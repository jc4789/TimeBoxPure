package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimeline
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimelineFactory
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.PmdPerformanceLaws
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
        assertEquals(16, program.eventCount)
        assertEquals(CompiledOpnaSong.TICKS_PER_QUARTER * 4L, program.durationTicks)
        val notes = eventIndices(program, CompiledOpnaSong.FM_NOTE)
        assertEquals(5, program.detuneCents[notes[0]])
        val pms = eventIndices(program, CompiledOpnaSong.HW_LFO_PMS)
        val ams = eventIndices(program, CompiledOpnaSong.HW_LFO_AMS)
        assertEquals(3, program.stateValue[pms.last()])
        assertEquals(1, program.stateValue[ams.last()])
        assertEquals(960, program.durationTick[notes[0]])
        assertEquals(840, program.gateTick[notes[0]])
        assertEquals(67, program.targetMidi[notes[1]])
    }

    @Test
    fun sourceClockGateTailPreservesPmdStyleAbsoluteKeyOffTiming() {
        val source = "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\nA @54 Q8 q2 o4 c4 d4 e2 |"
        val program = assertNotNull(
            assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement.compiledOpnaSong
        )

        val notes = eventIndices(program, CompiledOpnaSong.FM_NOTE)
        assertEquals(480, program.durationTick[notes[0]])
        assertEquals(440, program.gateTick[notes[0]])
        assertEquals(440, program.gateTick[notes[1]])
        assertEquals(920, program.gateTick[notes[2]])
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
        val notes = eventIndices(first, CompiledOpnaSong.FM_NOTE)
        var i = 0
        while (i < notes.size) {
            assertTrue(first.gateTick[notes[i]] in 60..100)
            assertTrue(first.gateMinimumClocks[notes[i]] == 2)
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
        assertEquals(8, program.eventCount)
        val notes = eventIndices(program, CompiledOpnaSong.FM_NOTE)
        assertEquals(480, program.durationTick[notes[0]])
        assertEquals(240, program.gateTick[notes[0]])
        assertEquals(480, program.gateTick[notes[1]])
        assertEquals(240, program.gateTick[notes[2]])
        assertEquals(240, program.gateTick[notes[3]])

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
        assertEquals(10, program.eventCount)
        assertContentEquals(
            intArrayOf(
                CompiledOpnaSong.HW_LFO_RATE,
                CompiledOpnaSong.HW_LFO_ENABLE,
                CompiledOpnaSong.SSG_TONE_ENABLE,
                CompiledOpnaSong.SSG_NOISE_ENABLE,
                CompiledOpnaSong.SSG_ENVELOPE_DEFINE,
                CompiledOpnaSong.SSG_ENVELOPE_MODE,
                CompiledOpnaSong.SSG_NOTE,
                CompiledOpnaSong.SSG_ENVELOPE_MODE,
                CompiledOpnaSong.SSG_ENVELOPE_DEFINE,
                CompiledOpnaSong.SSG_NOTE
            ),
            program.eventType
        )
        assertEquals(PmdPerformanceLaws.ENVELOPE_LEGACY, program.envelopeFormat[4])
        assertEquals(PmdPerformanceLaws.ENVELOPE_EXTENDED, program.envelopeFormat[8])
        assertEquals(7, program.envelopeSustainLevel[8])
        assertEquals(3, program.envelopeAttackLevel[8])
    }

    @Test
    fun v2PlayerRendersDeterministically() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(expressiveSource)).arrangement
        val sampleRate = 48_000
        val synthA = OpnaLikeSynthesizer(sampleRate)
        val synthB = OpnaLikeSynthesizer(sampleRate)
        val playerA = MmlArrangementScheduler.createPlayer(arrangement, synthA, sampleRate)
        val playerB = MmlArrangementScheduler.createPlayer(arrangement, synthB, sampleRate)

        assertTrue(!synthA.lfo.enabled, "Timeline state must not be applied during player construction")
        assertEquals(20, playerA.eventCount)
        assertEquals(2, count(playerA.timeline, CompiledOpnaTimeline.FM_ON))
        assertEquals(1, count(playerA.timeline, CompiledOpnaTimeline.SSG_ON))
        assertEquals(4, count(playerA.timeline, CompiledOpnaTimeline.DRUM_SHOT))

        val a = FloatArray(4096)
        val b = FloatArray(4096)
        synthA.render(a, a.size, playerA, 0L)
        synthB.render(b, b.size, playerB, 0L)
        assertTrue(synthA.lfo.enabled)
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
    fun fm3ExtendedPartsReachIndependentTimelineOperators() {
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
        assertEquals(7, program.eventCount)
        val fm3Notes = program.eventType.indices.filter { program.eventType[it] == CompiledOpnaSong.FM3_OPERATOR_NOTE }
        assertEquals(CompiledOpnaSong.FM3_PART_BASE, program.logicalPart[fm3Notes[0]])
        assertEquals(CompiledOpnaSong.FM3_PART_BASE + 1, program.logicalPart[fm3Notes[1]])

        val synth = OpnaLikeSynthesizer(48_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 48_000)
        assertEquals(2, count(player.timeline, CompiledOpnaTimeline.FM3_OPERATOR_ON))
        val output = FloatArray(4096)
        synth.render(output, output.size, player, 0L)
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
        assertEquals(8, program.eventCount)
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
        assertEquals(11, program.eventCount)
        val notes = eventIndices(program, CompiledOpnaSong.FM_POLY_NOTE)
        assertEquals(7, notes.size)
        var eventIndex = 0
        while (eventIndex < notes.size) {
            assertEquals(0L, program.startTick[notes[eventIndex]])
            eventIndex++
        }

        val synth = OpnaLikeSynthesizer(48_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 48_000)
        assertEquals(7, count(player.timeline, CompiledOpnaTimeline.FM_POLY_ON))
        val output = FloatArray(4_096)
        synth.render(output, output.size, player, 0L)
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
        val notes = eventIndices(program, CompiledOpnaSong.FM_POLY_NOTE)
        assertEquals(8, notes.size)
        val synth = OpnaLikeSynthesizer(48_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 48_000)
        synth.render(FloatArray(13_000), 13_000, player, 0L)
        assertEquals(1, synth.activeFmVoiceCount())
        assertTrue(synth.occupiedFmVoiceCount() >= 2, "The first arpeggio note's release tail was stolen")
    }

    @Test
    fun tempoChangesUseOneGlobalIntegerTimeline() {
        val source = "#MML 2\n#BPM 120\n#BAR 4/4\nA @lead o4 T120 c2 T240 d2 |"
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
        val program = assertNotNull(arrangement.compiledOpnaSong)
        assertEquals(1_500L, program.durationMilliseconds())
        val player = MmlArrangementScheduler.createPlayer(arrangement, OpnaLikeSynthesizer(48_000), 48_000)
        var secondOn = -1
        var event = 0
        while (event < player.eventCount) {
            if (player.timeline.eventType[event] == CompiledOpnaTimeline.FM_ON &&
                player.timeline.sampleTime[event] > 0L
            ) {
                secondOn = event
                break
            }
            event++
        }
        assertTrue(secondOn >= 0)
        assertEquals(48_000L, player.timeline.sampleTime[secondOn])
        assertEquals(72_000L, player.loopLengthSamples)
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

    @Test
    fun hardwareLfoCommandsBecomeExactOrderedSemanticState() {
        val source = """
            #MML 2
            #BPM 120
            #PMDCLOCK 24
            #BAR 4/4
            #LFO 5
            A @54 #1,6 H2,1,24 o4 c4 H3 d4 H4,2,l8. e4 #0 f4 |
        """.trimIndent()
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
        val song = assertNotNull(arrangement.compiledOpnaSong)

        assertEquals(19, song.eventCount)
        assertEquals(2, count(song, CompiledOpnaSong.HW_LFO_RATE))
        assertEquals(3, count(song, CompiledOpnaSong.HW_LFO_ENABLE))
        assertEquals(4, count(song, CompiledOpnaSong.HW_LFO_PMS))
        assertEquals(4, count(song, CompiledOpnaSong.HW_LFO_AMS))
        assertEquals(2, count(song, CompiledOpnaSong.HW_LFO_DELAY))

        val delayEvents = indices(song, CompiledOpnaSong.HW_LFO_DELAY)
        assertEquals(CompiledOpnaSong.HW_LFO_DELAY_RAW_CLOCKS, song.hardwareLfoDelayKind[delayEvents[0]])
        assertEquals(24, song.hardwareLfoDelayValue[delayEvents[0]])
        assertEquals(false, song.hardwareLfoDelayDotted[delayEvents[0]])
        assertEquals(CompiledOpnaSong.HW_LFO_DELAY_NOTE_LENGTH, song.hardwareLfoDelayKind[delayEvents[1]])
        assertEquals(8, song.hardwareLfoDelayValue[delayEvents[1]])
        assertEquals(true, song.hardwareLfoDelayDotted[delayEvents[1]])
        assertEquals(0, countAtTick(song, CompiledOpnaSong.HW_LFO_DELAY, 480L))

        val amsAtSecondNote = indexAtTick(song, CompiledOpnaSong.HW_LFO_AMS, 480L)
        assertEquals(0, song.stateValue[amsAtSecondNote])

        val timeline = CompiledOpnaTimelineFactory.build(song, 8_000, 1f)
        assertEquals(24, timeline.eventCount)
        assertEquals(timeline.eventCount, timeline.eventType.size)
        assertEquals(timeline.eventCount, timeline.sampleTime.size)
        val firstOn = timeline.eventType.indexOf(CompiledOpnaTimeline.FM_ON)
        assertContentEquals(
            intArrayOf(
                CompiledOpnaTimeline.HW_LFO_RATE,
                CompiledOpnaTimeline.HW_LFO_ENABLE,
                CompiledOpnaTimeline.HW_LFO_RATE,
                CompiledOpnaTimeline.HW_LFO_ENABLE,
                CompiledOpnaTimeline.TEMPO,
                CompiledOpnaTimeline.HW_LFO_PMS,
                CompiledOpnaTimeline.HW_LFO_AMS,
                CompiledOpnaTimeline.HW_LFO_PMS,
                CompiledOpnaTimeline.HW_LFO_AMS,
                CompiledOpnaTimeline.HW_LFO_DELAY
            ),
            timeline.eventType.copyOfRange(0, firstOn)
        )
        var ordered = 1
        while (ordered < firstOn) {
            if (timeline.eventType[ordered] != CompiledOpnaTimeline.TEMPO) {
                assertTrue(timeline.sourceOrder[ordered - 1] <= timeline.sourceOrder[ordered] ||
                    timeline.eventType[ordered - 1] == CompiledOpnaTimeline.TEMPO)
            }
            ordered++
        }
        val rawDelay = timeline.eventType.indexOf(CompiledOpnaTimeline.HW_LFO_DELAY)
        val rawBase = rawDelay * CompiledOpnaTimeline.CONTROL_STRIDE
        assertContentEquals(
            intArrayOf(CompiledOpnaSong.HW_LFO_DELAY_RAW_CLOCKS, 24, 0),
            timeline.controlValues.copyOfRange(rawBase, rawBase + 3)
        )
    }

    @Test
    fun fm3HardwareLfoStateMustUseThePhysicalChannelControlLane() {
        val rejected = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 120\n#BAR 4/4\n#FM3EXTEND ON\n" +
                    "C @effect\nC1 H2,1 o4 l1 c |"
            )
        )
        assertTrue(rejected.diagnostics.any { it.reason.contains("shared physical-channel state") })

        val accepted = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 120\n#BAR 4/4\n#FM3EXTEND ON\n" +
                    "C @effect H2,1\nC1 o4 l1 c |"
            )
        )
        val song = assertNotNull(accepted.arrangement.compiledOpnaSong)
        val pms = song.eventType.indexOf(CompiledOpnaSong.HW_LFO_PMS)
        assertEquals(2, song.channel[pms])
        assertEquals(CompiledOpnaSong.LOGICAL_PART_NONE, song.logicalPart[pms])
    }

    private fun count(timeline: CompiledOpnaTimeline, type: Int): Int {
        var result = 0
        var i = 0
        while (i < timeline.eventCount) {
            if (timeline.eventType[i] == type) result++
            i++
        }
        return result
    }

    private fun count(song: CompiledOpnaSong, type: Int): Int = indices(song, type).size

    private fun countAtTick(song: CompiledOpnaSong, type: Int, tick: Long): Int {
        var result = 0
        var i = 0
        while (i < song.eventCount) {
            if (song.eventType[i] == type && song.startTick[i] == tick) result++
            i++
        }
        return result
    }

    private fun indexAtTick(song: CompiledOpnaSong, type: Int, tick: Long): Int {
        var i = 0
        while (i < song.eventCount) {
            if (song.eventType[i] == type && song.startTick[i] == tick) return i
            i++
        }
        error("No event type $type at tick $tick")
    }

    private fun indices(song: CompiledOpnaSong, type: Int): IntArray {
        val result = IntArray(song.eventType.count { it == type })
        var output = 0
        var i = 0
        while (i < song.eventCount) {
            if (song.eventType[i] == type) result[output++] = i
            i++
        }
        return result
    }

    private fun eventIndices(song: CompiledOpnaSong, type: Int): IntArray = indices(song, type)
}
