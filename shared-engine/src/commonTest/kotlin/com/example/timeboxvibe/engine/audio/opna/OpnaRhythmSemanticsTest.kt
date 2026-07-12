package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.mml.MmlCompileResult
import com.example.timeboxvibe.engine.audio.mml.MmlCompiler
import kotlin.test.Test
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
            if (player.timeline.sampleTime[event] == 0L && player.timeline.eventType[event] != CompiledOpnaTimeline.TEMPO) {
                assertEquals(expected[expectedIndex++], player.timeline.eventType[event])
            }
            event++
        }
        assertEquals(expected.size, expectedIndex)

        synth.render(FloatArray(1), 1, player, 0L)
        assertEquals(45, synth.rhythmMasterLevelSnapshot())
        assertEquals(13, synth.rhythmVoiceLevelSnapshot(0))
        assertEquals(1, synth.rhythmVoicePanSnapshot(0))
        assertEquals(ProceduralDrums.DECAY, synth.drums.stateSnapshot(ProceduralDrums.DrumKind.KICK))
        assertEquals(45f / 63f * 13f / 31f, synth.drums.gainSnapshot(ProceduralDrums.DrumKind.KICK), 0.000001f)

        synth.render(FloatArray(1), 1, player, 8_000L)
        assertEquals(ProceduralDrums.IDLE, synth.drums.stateSnapshot(ProceduralDrums.DrumKind.KICK))
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
            assertEquals(ProceduralDrums.DECAY, synth.drums.stateSnapshot(ProceduralDrums.DrumKind.entries[kind]))
            kind++
        }
        synth.rhythmDump(0b100101)
        assertEquals(ProceduralDrums.IDLE, synth.drums.stateSnapshot(ProceduralDrums.DrumKind.KICK))
        assertEquals(ProceduralDrums.IDLE, synth.drums.stateSnapshot(ProceduralDrums.DrumKind.CYMBAL))
        assertEquals(ProceduralDrums.IDLE, synth.drums.stateSnapshot(ProceduralDrums.DrumKind.RIMSHOT))
        assertEquals(ProceduralDrums.DECAY, synth.drums.stateSnapshot(ProceduralDrums.DrumKind.SNARE))
    }

    @Test
    fun retainedSequencerMatchesRhythmControlState() {
        val arrangement = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                R @drum \V63 \vs7 \rh \s r1 |
            """.trimIndent()
        )
        val playerSynth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, playerSynth, 8_000)
        val retainedSynth = OpnaLikeSynthesizer(8_000)
        val sequencer = OpnaSequencer(8_000, arrangement.tempoBpm)
        MmlArrangementScheduler.schedule(arrangement, retainedSynth, sequencer, 8_000)

        playerSynth.render(FloatArray(1), 1, player, 0L)
        retainedSynth.render(FloatArray(1), 1, sequencer, 0L)

        assertEquals(playerSynth.rhythmMasterLevelSnapshot(), retainedSynth.rhythmMasterLevelSnapshot())
        assertEquals(playerSynth.rhythmVoiceLevelSnapshot(1), retainedSynth.rhythmVoiceLevelSnapshot(1))
        assertEquals(playerSynth.rhythmVoicePanSnapshot(3), retainedSynth.rhythmVoicePanSnapshot(3))
        assertEquals(
            playerSynth.drums.stateSnapshot(ProceduralDrums.DrumKind.SNARE),
            retainedSynth.drums.stateSnapshot(ProceduralDrums.DrumKind.SNARE)
        )
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

    private fun compile(source: String) =
        assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
}
