package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.mml.MmlCompileResult
import com.example.timeboxvibe.engine.audio.mml.MmlCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Fm3SlotSemanticsTest {
    @Test
    fun twoByTwoSplitCompilesAsDirectFourBitMasks() {
        val program = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                #FM3EXTEND ON
                C @effect
                C1 s3 @brass o4 l1 c |
                C2 s12 @piano o4 l1 e |
            """.trimIndent()
        )

        val notes = IntArray(2)
        var count = 0
        var index = 0
        while (index < program.eventCount) {
            if (program.eventType[index] == CompiledOpnaSong.FM3_OPERATOR_NOTE) notes[count++] = index
            index++
        }
        assertEquals(2, count)
        assertEquals(3, program.slotMask[notes[0]])
        assertEquals(12, program.slotMask[notes[1]])
    }

    @Test
    fun compilerRejectsOverlappingExtendedOwnership() {
        val result = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile(
                """
                    #MML 2
                    #BPM 120
                    #BAR 4/4
                    #FM3EXTEND ON
                    C @effect
                    C1 s3 o4 l1 c |
                    C2 s6 o4 l1 e |
                """.trimIndent()
            )
        )

        assertTrue(result.diagnostics.any { it.reason.contains("slot ownership overlaps") })
    }

    @Test
    fun sequentialExtendedPartsMayReuseTheSamePhysicalSlots() {
        val program = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                #FM3EXTEND ON
                C @effect
                C1 s3 o4 l2 c r2 |
                C2 s3 o4 l2 r2 e |
            """.trimIndent()
        )

        assertEquals(2, count(program, CompiledOpnaSong.FM3_OPERATOR_NOTE))
    }

    @Test
    fun normalAndExtendedChannelCRemainMutuallyExclusive() {
        val result = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile(
                """
                    #MML 2
                    #BPM 120
                    #BAR 4/4
                    #FM3EXTEND ON
                    C @effect o4 l1 c |
                    C1 o4 l1 e |
                """.trimIndent()
            )
        )

        assertTrue(result.diagnostics.any { it.reason.contains("supplies FM3 patch/control data") })
    }

    @Test
    fun selectedPatchWritesDoNotCorruptTheOtherSlotGroup() {
        val first = OpnaPatchBank.Pc98Brass
        val second = OpnaPatchBank.Pc98Piano
        val voice = Fm4OpVoice(8_000)

        voice.applyPatchToSlots(first, 3)
        voice.applyPatchToSlots(second, 12)

        assertEquals(first.op0.tl, voice.operatorTlSnapshot(0))
        assertEquals(first.op1.tl, voice.operatorTlSnapshot(1))
        assertEquals(second.op2.tl, voice.operatorTlSnapshot(2))
        assertEquals(second.op3.tl, voice.operatorTlSnapshot(3))
        assertEquals(second.algorithm, voice.algorithmSnapshot())
        assertEquals(first.feedback, voice.feedbackSnapshot())
    }

    @Test
    fun liveSlotControlsAreOrderedBeforeSameTickKeyOn() {
        val compiled = MmlCompiler.compile(
                """
                    #MML 2
                    #BPM 120
                    #PMDCLOCK 24
                    #BAR 4/4
                    #FM3EXTEND ON
                    C @effect
                    C1 s3 sd3,4 sk2,1 MM3 @brass O3,10 o4 l1 c |
                    C2 s12 @piano o4 l1 e |
                """.trimIndent()
            )
        assertTrue(
            compiled is MmlCompileResult.Success,
            (compiled as? MmlCompileResult.Failure)?.diagnostics?.joinToString { it.reason } ?: "compile failed"
        )
        val arrangement = (compiled as MmlCompileResult.Success).arrangement
        val synth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 8_000)

        synth.render(FloatArray(1), 1, player, 0L)

        assertEquals(4, synth.fm[2].slotDetuneSnapshot(0))
        assertEquals(4, synth.fm[2].slotDetuneSnapshot(1))
        assertEquals(10, synth.fm[2].operatorTlSnapshot(0))
        assertEquals(10, synth.fm[2].operatorTlSnapshot(1))
        assertEquals(166, synth.fm[2].slotKeyOnDelaySnapshot(1))
        assertEquals(3, synth.fm3PartLfoTlMaskSnapshot(0, 0))
        assertEquals(OpnaPatchBank.Pc98Piano.algorithm, synth.fm[2].algorithmSnapshot())
        assertEquals(OpnaPatchBank.Pc98Brass.feedback, synth.fm[2].feedbackSnapshot())

    }

    @Test
    fun channelCControlsCompileAsLiveOrderedEvents() {
        val program = compile(
            """
                #MML 2
                #BPM 120
                #PMDCLOCK 24
                #BAR 4/4
                #FM3EXTEND ON
                C @effect s3 @brass O3,11 FB5 r2 s12 @piano O12,12 r2 |
                C1 s3 o4 l1 c |
                C2 s12 o4 l1 e |
            """.trimIndent()
        )

        assertEquals(3, count(program, CompiledOpnaSong.FM3_PATCH))
        assertEquals(2, count(program, CompiledOpnaSong.FM_TL_ABSOLUTE))
        assertEquals(1, count(program, CompiledOpnaSong.FM_FEEDBACK_ABSOLUTE))
    }

    @Test
    fun channelCRejectsPartLocalControlsInsteadOfSilentlyDiscardingThem() {
        val result = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile(
                """
                    #MML 2
                    #BPM 120
                    #BAR 4/4
                    #FM3EXTEND ON
                    C @effect V64 p1 H2,1 M1,1,2,3
                    C1 o4 l1 c |
                """.trimIndent()
            )
        )

        assertTrue(result.diagnostics.any { it.reason.contains("part-local") })
    }

    @Test
    fun fm3PartsKeepIndependentVolumeAndLfoState() {
        val compiled = MmlCompiler.compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                #FM3EXTEND ON
                C @effect
                C1 s3 V31 M1,1,2,3 MM3 *3 o4 l1 c |
                C2 s12 V96 M2,2,4,5 MM12 *3 o4 l1 e |
            """.trimIndent()
        )
        assertTrue(
            compiled is MmlCompileResult.Success,
            (compiled as? MmlCompileResult.Failure)?.diagnostics?.joinToString { it.reason } ?: "compile failed"
        )
        val arrangement = (compiled as MmlCompileResult.Success).arrangement
        val synth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 8_000)

        synth.render(FloatArray(1), 1, player, 0L)

        assertEquals(31, synth.fm3PartVolumeSnapshot(0))
        assertEquals(96, synth.fm3PartVolumeSnapshot(1))
        assertEquals(3, synth.fm3PartLfoTlMaskSnapshot(0, 0))
        assertEquals(12, synth.fm3PartLfoTlMaskSnapshot(1, 0))
    }

    @Test
    fun partialPatchWritesPreserveNonPatchChannelControls() {
        val voice = Fm4OpVoice(8_000)
        voice.applyPatch(OpnaPatchBank.Pc98Effect)
        val level = voice.channelTotalLevelSnapshot()
        val pms = voice.channelPms
        val ams = voice.channelAms
        val pan = voice.getPan()

        voice.applyPatchToSlots(OpnaPatchBank.Pc98Piano, 12)

        assertEquals(level, voice.channelTotalLevelSnapshot())
        assertEquals(pms, voice.channelPms)
        assertEquals(ams, voice.channelAms)
        assertEquals(pan, voice.getPan())
        assertEquals(OpnaPatchBank.Pc98Piano.algorithm, voice.algorithmSnapshot())
        assertEquals(OpnaPatchBank.Pc98Effect.feedback, voice.feedbackSnapshot())
    }

    @Test
    fun slotKeyOnDelayUsesExactFrameBoundaryAndCancels() {
        val voice = Fm4OpVoice(8_000)
        voice.applyPatchToSlots(OpnaPatchBank.Pc98Effect, 1)

        voice.setSlotKeyOnDelay(1, 0)
        voice.noteOnSlots(1, 60)
        assertTrue(!voice.slotPendingKeyOnSnapshot(0))

        voice.noteOffSlots(1)
        voice.setSlotKeyOnDelay(1, 1)
        voice.noteOnSlots(1, 60)
        voice.render(FloatArray(1), 1, 8_000, 1f)
        assertTrue(voice.slotPendingKeyOnSnapshot(0))
        voice.render(FloatArray(1), 1, 8_000, 1f)
        assertTrue(!voice.slotPendingKeyOnSnapshot(0))

        voice.noteOffSlots(1)
        voice.setSlotKeyOnDelay(1, 7)
        voice.noteOnSlots(1, 60)
        voice.render(FloatArray(7), 7, 8_000, 1f)
        assertTrue(voice.slotPendingKeyOnSnapshot(0))
        voice.noteOffSlots(1)
        assertTrue(!voice.slotPendingKeyOnSnapshot(0))
    }

    @Test
    fun absoluteAndRelativeLiveControlsClampAtHardwareRanges() {
        val voice = Fm4OpVoice(8_000)
        voice.applyPatch(OpnaPatchBank.Pc98Effect)

        voice.setSlotDetune(5, 20, relative = false)
        voice.setSlotDetune(1, -3, relative = true)
        voice.setOperatorTl(5, 120, relative = false)
        voice.setOperatorTl(1, 20, relative = true)
        voice.setFeedback(7, relative = false)
        voice.setFeedback(3, relative = true)

        assertEquals(17, voice.slotDetuneSnapshot(0))
        assertEquals(20, voice.slotDetuneSnapshot(2))
        assertEquals(127, voice.operatorTlSnapshot(0))
        assertEquals(120, voice.operatorTlSnapshot(2))
        assertEquals(7, voice.feedbackSnapshot())
    }

    private fun compile(source: String): CompiledOpnaSong =
        requireNotNull(assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement.compiledOpnaSong)

    private fun count(program: CompiledOpnaSong, type: Int): Int {
        var count = 0
        var index = 0
        while (index < program.eventCount) {
            if (program.eventType[index] == type) count++
            index++
        }
        return count
    }
}
