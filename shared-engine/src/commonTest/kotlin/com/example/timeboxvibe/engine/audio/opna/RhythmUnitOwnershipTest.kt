package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RhythmUnitOwnershipTest {
    @Test
    fun ymAndPmdUnitsOwnIndependentGeneratorState() {
        val ym = Ym2608RhythmUnit(SAMPLE_RATE)
        val pmd = PmdSsgEffectUnit(SAMPLE_RATE)

        ym.setMasterLevel(63, relative = false)
        ym.setVoiceLevel(0, 7, relative = false)
        ym.setVoicePan(0, 1)
        ym.shot(1)

        assertEquals(ProceduralDrums.DECAY, ym.generatorStateSnapshot(0))
        assertEquals(ProceduralDrums.IDLE, pmd.generatorStateSnapshot(ProceduralDrums.DrumKind.KICK))
        assertEquals(0, pmd.triggerCountSnapshot())

        pmd.trigger(ProceduralDrums.DrumKind.KICK, 0.25f)

        assertEquals(ProceduralDrums.DECAY, ym.generatorStateSnapshot(0))
        assertEquals(7, ym.voiceLevelSnapshot(0))
        assertEquals(1, ym.voicePanSnapshot(0))
        assertEquals(ProceduralDrums.DECAY, pmd.generatorStateSnapshot(ProceduralDrums.DrumKind.KICK))
        assertEquals(0.25f, pmd.generatorGainSnapshot(ProceduralDrums.DrumKind.KICK))
        assertEquals(1, pmd.triggerCountSnapshot())

        ym.dump(1)
        assertEquals(ProceduralDrums.IDLE, ym.generatorStateSnapshot(0))
        assertEquals(ProceduralDrums.DECAY, pmd.generatorStateSnapshot(ProceduralDrums.DrumKind.KICK))
    }

    @Test
    fun silencePreservesControlsWhileResetRestoresDefaults() {
        val ym = Ym2608RhythmUnit(SAMPLE_RATE)
        val pmd = PmdSsgEffectUnit(SAMPLE_RATE)

        ym.setMasterLevel(12, relative = false)
        ym.setVoiceLevel(1, 5, relative = false)
        ym.setVoicePan(1, 2)
        ym.shot(1 shl 1)
        pmd.trigger(ProceduralDrums.DrumKind.SNARE, 0.4f)

        ym.silence()
        pmd.silence()

        assertEquals(ProceduralDrums.IDLE, ym.generatorStateSnapshot(1))
        assertEquals(12, ym.masterLevelSnapshot())
        assertEquals(5, ym.voiceLevelSnapshot(1))
        assertEquals(2, ym.voicePanSnapshot(1))
        assertEquals(ProceduralDrums.IDLE, pmd.generatorStateSnapshot(ProceduralDrums.DrumKind.SNARE))
        assertEquals(1, pmd.triggerCountSnapshot())

        ym.reset()
        pmd.reset()

        assertEquals(48, ym.masterLevelSnapshot())
        assertEquals(31, ym.voiceLevelSnapshot(1))
        assertEquals(0, ym.voicePanSnapshot(1))
        assertEquals(48f / 63f, ym.generatorGainSnapshot(1), 0.000001f)
        assertEquals(0, pmd.triggerCountSnapshot())
    }

    @Test
    fun fullResetRestoresDeterministicProceduralOutput() {
        val ym = Ym2608RhythmUnit(SAMPLE_RATE)
        val pmd = PmdSsgEffectUnit(SAMPLE_RATE)
        val first = FloatArray(FRAMES)
        val second = FloatArray(FRAMES)

        triggerBoth(ym, pmd)
        ym.renderMono(first, first.size, SAMPLE_RATE, 1f)
        pmd.renderMono(first, first.size, SAMPLE_RATE, 1f)

        ym.reset()
        pmd.reset()
        triggerBoth(ym, pmd)
        ym.renderMono(second, second.size, SAMPLE_RATE, 1f)
        pmd.renderMono(second, second.size, SAMPLE_RATE, 1f)

        assertContentEquals(first, second)
    }

    private fun triggerBoth(ym: Ym2608RhythmUnit, pmd: PmdSsgEffectUnit) {
        ym.shot((1 shl 0) or (1 shl 2))
        pmd.trigger(ProceduralDrums.DrumKind.SNARE, 0.5f)
    }

    private companion object {
        const val SAMPLE_RATE = 8_000
        const val FRAMES = 256
    }
}
