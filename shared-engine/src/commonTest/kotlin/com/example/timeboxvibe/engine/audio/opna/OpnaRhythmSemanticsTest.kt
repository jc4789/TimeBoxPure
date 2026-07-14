package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.mml.MmlCompileResult
import com.example.timeboxvibe.engine.audio.mml.MmlCompiler
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpnaRhythmSemanticsTest {
    @Test
    fun orderedControlsShotAndDumpReachThePrimitiveTimeline() {
        val arrangement = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                R @drum \V40 \V+5 \vb10 \vb+3 \lb \b r2 \bp r2 |
            """.trimIndent()
        )
        val synth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 8_000)

        val expected = intArrayOf(
            CompiledOpnaTimeline.RHYTHM_MASTER_ABSOLUTE,
            CompiledOpnaTimeline.RHYTHM_MASTER_RELATIVE,
            CompiledOpnaTimeline.RHYTHM_VOICE_LEVEL_ABSOLUTE,
            CompiledOpnaTimeline.RHYTHM_VOICE_LEVEL_RELATIVE,
            CompiledOpnaTimeline.RHYTHM_VOICE_PAN,
            CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT
        )
        var expectedIndex = 0
        var event = 0
        while (event < player.eventCount && expectedIndex < expected.size) {
            if (player.timeline.sampleTime[event] == 0L &&
                player.timeline.eventType[event] in
                    CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT..CompiledOpnaTimeline.RHYTHM_VOICE_PAN
            ) {
                assertEquals(expected[expectedIndex++], player.timeline.eventType[event])
            }
            event++
        }
        assertEquals(expected.size, expectedIndex)

        synth.render(FloatArray(1), 1, player, 0L)
        assertEquals(45, synth.rhythmMasterLevelSnapshot())
        assertEquals(13, synth.rhythmVoiceLevelSnapshot(0))
        assertEquals(1, synth.rhythmVoicePanSnapshot(0))
        assertEquals(ProceduralDrums.DECAY, synth.rhythmGeneratorStateSnapshot(0))
        assertEquals(45f / 63f * 13f / 31f, synth.rhythmGeneratorGainSnapshot(0), 0.000001f)

        synth.render(FloatArray(1), 1, player, 8_000L)
        assertEquals(ProceduralDrums.IDLE, synth.rhythmGeneratorStateSnapshot(0))
    }

    @Test
    fun simultaneousHitsAndDumpMasksUseDocumentedPhysicalVoiceBits() {
        val arrangement = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                R @drum \b\s\c\h\t\i r1 |
            """.trimIndent()
        )
        val synth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 8_000)
        synth.render(FloatArray(1), 1, player, 0L)

        var kind = 0
        while (kind < ProceduralDrums.DrumKind.entries.size) {
            assertEquals(ProceduralDrums.DECAY, synth.rhythmGeneratorStateSnapshot(kind))
            kind++
        }
        synth.rhythmDump(0b100101)
        assertEquals(ProceduralDrums.IDLE, synth.rhythmGeneratorStateSnapshot(0))
        assertEquals(ProceduralDrums.IDLE, synth.rhythmGeneratorStateSnapshot(2))
        assertEquals(ProceduralDrums.IDLE, synth.rhythmGeneratorStateSnapshot(5))
        assertEquals(ProceduralDrums.DECAY, synth.rhythmGeneratorStateSnapshot(1))
    }

    @Test
    fun malformedRhythmRangesAreRejected() {
        val result = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile("#MML 2\n#BPM 120\n#BAR 4/4\nR @drum \\V64 \\vb32 r1 |")
        )
        assertTrue(result.diagnostics.any { it.reason.contains("master level") })
        assertTrue(result.diagnostics.any { it.reason.contains("voice level") })
    }

    @Test
    fun kPartExpandsRDefinitionsAsSeparateSsgDrumEvents() {
        val arrangement = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                K R0R1R0R1 |
                R0 l4 @1c @128c
                R1 l2 @2c
            """.trimIndent()
        )
        val program = requireNotNull(arrangement.compiledOpnaSong)
        var hits = 0
        var event = 0
        while (event < program.eventCount) {
            if (program.eventType[event] == CompiledOpnaSong.SSG_DRUM_SHOT) hits++
            event++
        }
        assertEquals(6, hits)

        val synth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 8_000)
        synth.render(FloatArray(1), 1, player, 0L)
        assertEquals(1, synth.ssgDrumTriggerCountSnapshot())
        assertEquals(48, synth.rhythmMasterLevelSnapshot())
        assertEquals(31, synth.rhythmVoiceLevelSnapshot(0))
    }

    @Test
    fun reverseAuthoredSameTickRhythmCommandsUseCanonicalOrder() {
        val arrangement = compile(
            "#MML 2\n#BPM 120\n#BAR 4/4\nR @drum \\b \\bp \\V40 \\vb10 \\lb r1 |"
        )
        val synth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 8_000)
        val expected = intArrayOf(
            CompiledOpnaTimeline.RHYTHM_MASTER_ABSOLUTE,
            CompiledOpnaTimeline.RHYTHM_VOICE_LEVEL_ABSOLUTE,
            CompiledOpnaTimeline.RHYTHM_VOICE_PAN,
            CompiledOpnaTimeline.RHYTHM_CONTROL_DUMP,
            CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT
        )
        var found = 0
        var event = 0
        while (event < player.eventCount) {
            if (player.timeline.sampleTime[event] == 0L &&
                player.timeline.eventType[event] in
                    CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT..CompiledOpnaTimeline.RHYTHM_VOICE_PAN
            ) {
                assertEquals(expected[found++], player.timeline.eventType[event])
            }
            event++
        }
        assertEquals(expected.size, found)

        synth.render(FloatArray(1), 1, player, 0L)
        assertEquals(ProceduralDrums.DECAY, synth.rhythmGeneratorStateSnapshot(0))
        assertEquals(40f / 63f * 10f / 31f, synth.rhythmGeneratorGainSnapshot(0), 0.000001f)
    }

    @Test
    fun pmdPatternAndYmRhythmGeneratorsCannotMutateEachOther() {
        val arrangement = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                K R0 |
                R @drum \V40 \vb10 \b r1 |
                R0 l1 @1c
            """.trimIndent()
        )
        val synth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 8_000)
        synth.render(FloatArray(1), 1, player, 0L)

        assertEquals(ProceduralDrums.DECAY, synth.rhythmGeneratorStateSnapshot(0))
        assertEquals(ProceduralDrums.DECAY, synth.pmdSsgEffectStateSnapshot(ProceduralDrums.DrumKind.KICK))
        assertEquals(40, synth.rhythmMasterLevelSnapshot())
        assertEquals(10, synth.rhythmVoiceLevelSnapshot(0))

        synth.rhythmDump(1)
        assertEquals(ProceduralDrums.IDLE, synth.rhythmGeneratorStateSnapshot(0))
        assertEquals(ProceduralDrums.DECAY, synth.pmdSsgEffectStateSnapshot(ProceduralDrums.DrumKind.KICK))
    }

    @Test
    fun ymControlsAreRejectedOnKAndInsidePatternDefinitions() {
        val patternResult = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 120\n#BAR 4/4\nK R0 |\nR0 l1 @1 \\V40 \\b c"
            )
        )
        assertTrue(patternResult.diagnostics.any { it.reason.contains("inside R0") })

        val kResult = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 120\n#BAR 4/4\nK \\V40 R0 |\nR0 l1 @1c"
            )
        )
        assertTrue(kResult.diagnostics.any { it.reason.contains("part K") })

        assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile("#MML 2\n#BPM 120\n#BAR 4/4\nR @drum \\V40 \\b r1 |")
        )
    }

    @Test
    fun allNotesOffPreservesYmRegistersAndFullResetRestoresEveryDomain() {
        val synth = OpnaLikeSynthesizer(8_000)
        synth.setRhythmMasterLevel(12, relative = false)
        synth.rhythmShot(1)
        synth.triggerPmdSsgEffect(ProceduralDrums.DrumKind.SNARE.ordinal, 0.5f)
        synth.triggerDrum(ProceduralDrums.DrumKind.HAT.ordinal, 0.5f)

        synth.allNotesOff()
        assertEquals(12, synth.rhythmMasterLevelSnapshot())
        assertEquals(ProceduralDrums.IDLE, synth.rhythmGeneratorStateSnapshot(0))
        assertEquals(ProceduralDrums.IDLE, synth.pmdSsgEffectStateSnapshot(ProceduralDrums.DrumKind.SNARE))
        assertEquals(ProceduralDrums.IDLE, synth.drums.stateSnapshot(ProceduralDrums.DrumKind.HAT))
        assertEquals(1, synth.ssgDrumTriggerCountSnapshot())

        synth.reset()
        assertEquals(48, synth.rhythmMasterLevelSnapshot())
        assertEquals(31, synth.rhythmVoiceLevelSnapshot(0))
        assertEquals(0, synth.ssgDrumTriggerCountSnapshot())
    }

    @Test
    fun pmdEffectsUseSsgGainWhileYmRhythmUsesRhythmGain() {
        val legacyPmd = OpnaLikeSynthesizer(8_000)
        val referencePmd = OpnaLikeSynthesizer(8_000)
        legacyPmd.enableOutputFilter = false
        referencePmd.enableOutputFilter = false
        referencePmd.outputProfile = OpnaOutputProfile.PC9801_86_REFERENCE
        legacyPmd.triggerPmdSsgEffect(ProceduralDrums.DrumKind.KICK.ordinal, 0.5f)
        referencePmd.triggerPmdSsgEffect(ProceduralDrums.DrumKind.KICK.ordinal, 0.5f)
        val legacyPmdOutput = FloatArray(128)
        val referencePmdOutput = FloatArray(128)
        legacyPmd.render(legacyPmdOutput, legacyPmdOutput.size)
        referencePmd.render(referencePmdOutput, referencePmdOutput.size)
        assertTrue(!legacyPmdOutput.contentEquals(referencePmdOutput))

        val legacyYm = OpnaLikeSynthesizer(8_000)
        val referenceYm = OpnaLikeSynthesizer(8_000)
        legacyYm.enableOutputFilter = false
        referenceYm.enableOutputFilter = false
        referenceYm.outputProfile = OpnaOutputProfile.PC9801_86_REFERENCE
        legacyYm.rhythmShot(1)
        referenceYm.rhythmShot(1)
        val legacyYmOutput = FloatArray(128)
        val referenceYmOutput = FloatArray(128)
        legacyYm.render(legacyYmOutput, legacyYmOutput.size)
        referenceYm.render(referenceYmOutput, referenceYmOutput.size)
        assertContentEquals(legacyYmOutput, referenceYmOutput)
    }

    private fun compile(source: String) =
        assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
}
