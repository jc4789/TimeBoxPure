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
        assertEquals(3, synth.fm[2].softwareLfoTlMaskSnapshot(0))
        assertEquals(OpnaPatchBank.Pc98Piano.algorithm, synth.fm[2].algorithmSnapshot())
        assertEquals(OpnaPatchBank.Pc98Brass.feedback, synth.fm[2].feedbackSnapshot())

        val retainedSynth = OpnaLikeSynthesizer(8_000)
        val sequencer = OpnaSequencer(8_000, arrangement.tempoBpm)
        MmlArrangementScheduler.schedule(arrangement, retainedSynth, sequencer, 8_000)
        retainedSynth.render(FloatArray(1), 1, sequencer, 0L)
        assertEquals(synth.fm[2].operatorTlSnapshot(0), retainedSynth.fm[2].operatorTlSnapshot(0))
        assertEquals(synth.fm[2].slotDetuneSnapshot(1), retainedSynth.fm[2].slotDetuneSnapshot(1))
        assertEquals(synth.fm[2].slotKeyOnDelaySnapshot(1), retainedSynth.fm[2].slotKeyOnDelaySnapshot(1))
        assertEquals(synth.fm[2].softwareLfoTlMaskSnapshot(0), retainedSynth.fm[2].softwareLfoTlMaskSnapshot(0))
        assertEquals(synth.fm[2].algorithmSnapshot(), retainedSynth.fm[2].algorithmSnapshot())
        assertEquals(synth.fm[2].feedbackSnapshot(), retainedSynth.fm[2].feedbackSnapshot())
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
}
