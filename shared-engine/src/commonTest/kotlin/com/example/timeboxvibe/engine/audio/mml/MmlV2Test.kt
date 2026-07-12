package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimeline
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.PmdPerformanceLaws
import com.example.timeboxvibe.engine.audio.opna.SequencerEvent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class MmlV2Test {
    private val expressiveSource = """
        #MML 2
        #BPM 120
        #BAR 4/4
        #LFO 6
        A @lead V100 o4 l4 Q7 p3 D+5 H3,1,l8 c4&c4 {eg}2 |
        G @ssg_lead V90 o4 l1 c |
        R @drum V100 l4 k s h y |
    """.trimIndent()

    @Test
    fun v2CompilesExpressionIntoPrimitiveOpnaProgram() {
        val success = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(expressiveSource))
        val program = assertNotNull(success.arrangement.compiledOpnaSong)

        assertEquals(2, program.dialectVersion)
        assertEquals(6, program.lfoRate)
        assertEquals(7, program.eventCount)
        assertEquals(CompiledOpnaSong.TICKS_PER_QUARTER * 4L, program.durationTicks)
        assertEquals(5, program.detuneCents[0])
        assertEquals(3, program.pms[0])
        assertEquals(1, program.ams[0])
        assertEquals(960, program.durationTick[0])
        assertEquals(840, program.gateTick[0])
        assertEquals(67, program.targetMidi[1])
    }

    @Test
    fun sourceClockGateTailPreservesPmdStyleAbsoluteKeyOffTiming() {
        val source = "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\nA @54 Q8 q2 o4 c4 d4 e2 |"
        val program = assertNotNull(
            assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement.compiledOpnaSong
        )

        assertEquals(480, program.durationTick[0])
        assertEquals(440, program.gateTick[0])
        assertEquals(440, program.gateTick[1])
        assertEquals(920, program.gateTick[2])
    }

    @Test
    fun gateRandomRangeIsDeterministicInclusiveAndHonorsMinimum() {
        val source = """
            #MML 2
            #BPM 120
            #PMDCLOCK 24
            #BAR 4/4
            A @54 Q8 q1-3,2 o4 l16 c c c c c c c c c c c c c c c c |
        """.trimIndent()
        val first = assertNotNull(
            assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement.compiledOpnaSong
        )
        val second = assertNotNull(
            assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement.compiledOpnaSong
        )

        assertContentEquals(first.gateTick, second.gateTick)
        assertTrue(first.gateTailClocks.any { it == 1 })
        assertTrue(first.gateTailClocks.any { it == 3 })
        var i = 0
        while (i < first.eventCount) {
            assertTrue(first.gateTick[i] in 60..100)
            assertTrue(first.gateMinimumClocks[i] == 2)
            i++
        }
    }

    @Test
    fun qZeroPercentGateTiesAndSlursResolveBeforeTimelineOrdering() {
        val source = """
            #MML 2
            #BPM 120
            #PMDCLOCK 24
            #BAR 4/4
            A @54 Q4 q0 o4 c8&c8 d4&& e4 Q%128 f4 |
        """.trimIndent()
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
        val program = assertNotNull(arrangement.compiledOpnaSong)
        assertEquals(4, program.eventCount)
        assertEquals(480, program.durationTick[0])
        assertEquals(240, program.gateTick[0])
        assertEquals(480, program.gateTick[1])
        assertEquals(240, program.gateTick[2])
        assertEquals(240, program.gateTick[3])

        val player = MmlArrangementScheduler.createPlayer(arrangement, OpnaLikeSynthesizer(8_000), 8_000)
        val boundary = 8_000L
        var off = -1
        var on = -1
        var i = 0
        while (i < player.eventCount) {
            if (player.timeline.sampleTime[i] == boundary) {
                if (player.timeline.eventType[i] == CompiledOpnaTimeline.FM_OFF) off = i
                if (player.timeline.eventType[i] == CompiledOpnaTimeline.FM_ON) on = i
            }
            i++
        }
        assertTrue(off >= 0 && on > off)
    }

    @Test
    fun softwareEnvelopeDefinitionsRemainOrderedPartControls() {
        val source = """
            #MML 2
            #BPM 120
            #BAR 4/4
            G @square E2,-1,24,1 EX1 o4 c2 EX0 E31,20,10,5,7,3 d2 |
        """.trimIndent()
        val program = assertNotNull(
            assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement.compiledOpnaSong
        )
        assertEquals(6, program.eventCount)
        assertEquals(CompiledOpnaSong.SSG_ENVELOPE_DEFINE, program.eventType[0])
        assertEquals(CompiledOpnaSong.SSG_ENVELOPE_MODE, program.eventType[1])
        assertEquals(CompiledOpnaSong.SSG_NOTE, program.eventType[2])
        assertEquals(CompiledOpnaSong.SSG_ENVELOPE_MODE, program.eventType[3])
        assertEquals(CompiledOpnaSong.SSG_ENVELOPE_DEFINE, program.eventType[4])
        assertEquals(CompiledOpnaSong.SSG_NOTE, program.eventType[5])
        assertEquals(PmdPerformanceLaws.ENVELOPE_LEGACY, program.envelopeFormat[0])
        assertEquals(PmdPerformanceLaws.ENVELOPE_EXTENDED, program.envelopeFormat[4])
        assertEquals(7, program.envelopeSustainLevel[4])
        assertEquals(3, program.envelopeAttackLevel[4])
    }

    @Test
    fun v2SchedulesAndRendersDeterministically() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(expressiveSource)).arrangement
        val sampleRate = 48_000
        val synthA = OpnaLikeSynthesizer(sampleRate)
        val synthB = OpnaLikeSynthesizer(sampleRate)
        val seqA = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        val seqB = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synthA, seqA, sampleRate)
        MmlArrangementScheduler.schedule(arrangement, synthB, seqB, sampleRate)

        assertTrue(synthA.lfo.enabled)
        assertEquals(10, seqA.eventCount)
        assertEquals(2, count(seqA, SequencerEvent.FM_ON))
        assertEquals(1, count(seqA, SequencerEvent.SSG_ON))
        assertEquals(4, count(seqA, SequencerEvent.DRUM))

        val a = FloatArray(4096)
        val b = FloatArray(4096)
        synthA.render(a, a.size, seqA, 0L)
        synthB.render(b, b.size, seqB, 0L)
        assertTrue(a.contentEquals(b))
        assertTrue(a.any { abs(it) > 0.0001f })
        assertTrue(a.all { it.isFinite() })
    }

    @Test
    fun namedPatchChangesAreAllowedOnlyWithinChannelFamily() {
        val valid = MmlCompiler.compile(
            "#MML 2\n#BPM 120\n#BAR 4/4\nA @lead o4 l2 c @piano d |"
        )
        assertIs<MmlCompileResult.Success>(valid)

        val invalid = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile("#MML 2\n#BPM 120\n#BAR 4/4\nG @lead o4 l1 c |")
        )
        assertTrue(invalid.diagnostics.any { it.reason.contains("invalid for channel") })
    }

    @Test
    fun fm3ExtendedPartsScheduleIndependentOperators() {
        val source = """
            #MML 2
            #BPM 120
            #BAR 4/4
            #FM3EXTEND ON
            C @effect
            C1 o4 l1 c |
            C2 o4 l1 e |
        """.trimIndent()
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
        val program = assertNotNull(arrangement.compiledOpnaSong)
        assertTrue(program.fm3Extended)
        assertEquals(2, program.eventCount)

        val synth = OpnaLikeSynthesizer(48_000)
        val sequencer = OpnaSequencer(48_000, 120f)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, 48_000)
        assertEquals(2, count(sequencer, SequencerEvent.FM3_OPERATOR_ON))
        val output = FloatArray(4096)
        synth.render(output, output.size, sequencer, 0L)
        assertTrue(output.any { abs(it) > 0.0001f })
    }

    @Test
    fun v2SupportsNestedLoopsButV1StillRejectsThem() {
        val v2 = MmlCompiler.compile("#MML 2\n#BPM 120\n#BAR 4/4\nA @lead o4 l4 [[c]2]2 |")
        assertIs<MmlCompileResult.Success>(v2)
        val v1 = MmlCompiler.compile("#BPM 120\n#BAR 4/4\nA @54 o4 l4 [[c]2]2 |")
        assertIs<MmlCompileResult.Failure>(v1)
    }

    @Test
    fun v2ExpandsNamedAuthoringMacros() {
        val source = """
            #MML 2
            #BPM 120
            #BAR 4/4
            #MACRO phrase c4 d4 e4 f4
            A @lead o4 ${'$'}phrase |
        """.trimIndent()
        val program = assertNotNull(assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement.compiledOpnaSong)
        assertEquals(4, program.eventCount)
    }

    @Test
    fun chordSyntaxUsesThePreallocatedFmVoicePoolBeyondSixHardwareParts() {
        val source = """
            #MML 2
            #BPM 120
            #BAR 4/4
            A @strings V80 Q7 o4 {c,d,e,f,g,a,b}1 |
        """.trimIndent()
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
        val program = assertNotNull(arrangement.compiledOpnaSong)
        assertEquals(7, program.eventCount)
        var eventIndex = 0
        while (eventIndex < program.eventCount) {
            assertEquals(CompiledOpnaSong.FM_POLY_NOTE, program.eventType[eventIndex])
            assertEquals(0L, program.startTick[eventIndex])
            eventIndex++
        }

        val synth = OpnaLikeSynthesizer(48_000)
        val sequencer = OpnaSequencer(48_000, arrangement.tempoBpm)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, 48_000)
        assertEquals(7, count(sequencer, SequencerEvent.FM_POLY_ON))
        val output = FloatArray(4_096)
        synth.render(output, output.size, sequencer, 0L)
        assertEquals(7, synth.activeFmVoiceCount())
        assertTrue(output.any { abs(it) > 0.0001f })
    }

    @Test
    fun chordSyntaxIsFmV2Only() {
        assertIs<MmlCompileResult.Failure>(MmlCompiler.compile("#BPM 120\n#BAR 4/4\nA @54 o4 {c,e,g}1 |"))
        val ssg = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile("#MML 2\n#BPM 120\n#BAR 4/4\nG @square o4 {c,e,g}1 |")
        )
        assertTrue(ssg.diagnostics.any { it.reason.contains("only valid on FM") })
    }

    @Test
    fun compilerRejectsDensityInsteadOfSilentlyDroppingChordNotes() {
        val result = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 120\n#BAR 4/4\n" +
                    "A @strings o4 {c,d,e,f,g,a,b,c}1 |\n" +
                    "B @strings o4 {c,d,e,f,g,a,b,c}1 |\n" +
                    "C @strings o4 {c,d,e,f,g,a,b,c}1 |"
            )
        )
        assertTrue(result.diagnostics.any { it.reason.contains("simultaneous FM voices") })
    }

    @Test
    fun polyphonicPartModePreservesSequentialReleaseTails() {
        val arrangement = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile("#MML 2\n#BPM 120\n#BAR 4/4\nA @strings V80 Q8 P1 o4 l8 c d e f g a b >c |")
        ).arrangement
        val program = assertNotNull(arrangement.compiledOpnaSong)
        var eventIndex = 0
        while (eventIndex < program.eventCount) {
            assertEquals(CompiledOpnaSong.FM_POLY_NOTE, program.eventType[eventIndex])
            eventIndex++
        }
        val synth = OpnaLikeSynthesizer(48_000)
        val sequencer = OpnaSequencer(48_000, arrangement.tempoBpm)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, 48_000)
        synth.render(FloatArray(13_000), 13_000, sequencer, 0L)
        assertEquals(1, synth.activeFmVoiceCount())
        assertTrue(synth.occupiedFmVoiceCount() >= 2, "The first arpeggio note's release tail was stolen")
    }

    @Test
    fun tempoChangesUseOneGlobalIntegerTimeline() {
        val source = "#MML 2\n#BPM 120\n#BAR 4/4\nA @lead o4 T120 c2 T240 d2 |"
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
        val program = assertNotNull(arrangement.compiledOpnaSong)
        assertEquals(1_500L, program.durationMilliseconds())
        val sequencer = OpnaSequencer(48_000, arrangement.tempoBpm)
        MmlArrangementScheduler.schedule(arrangement, OpnaLikeSynthesizer(48_000), sequencer, 48_000)
        val secondOn = sequencer.events.first { it.type == SequencerEvent.FM_ON && it.sampleTime > 0L }
        assertEquals(48_000L, secondOn.sampleTime)
        assertEquals(72_000L, sequencer.customLoopLength)
    }

    @Test
    fun decimalTempoAndChangesUseExactRationalSampleBoundaries() {
        val source = """
            #MML 2
            #BPM 160.73
            #BAR 4/4
            A @lead o4 c4 T121 d4 T137 e4 f4 |
        """.trimIndent()
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
        val player = MmlArrangementScheduler.createPlayer(arrangement, OpnaLikeSynthesizer(48_000), 48_000)
        val onSamples = LongArray(4)
        var onCount = 0
        var i = 0
        while (i < player.eventCount) {
            if (player.timeline.eventType[i] == CompiledOpnaTimeline.FM_ON) {
                onSamples[onCount++] = player.timeline.sampleTime[i]
            }
            i++
        }
        assertEquals(4, onCount)
        assertContentEquals(longArrayOf(0L, 17_918L, 41_719L, 62_741L), onSamples)
        assertEquals(83_763L, player.loopLengthSamples)
    }

    private fun count(sequencer: OpnaSequencer, type: Int): Int {
        var result = 0
        var i = 0
        while (i < sequencer.eventCount) {
            if (sequencer.events[i].type == type) result++
            i++
        }
        return result
    }
}
