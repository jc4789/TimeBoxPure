package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.SequencerEvent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
