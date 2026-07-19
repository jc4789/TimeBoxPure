package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HardwareLfoRuntimeTest {
    @Test
    fun schedulerIsPureAndSampleZeroEventsConfigureRuntimeState() {
        val arrangement = compile(
            "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\n#LFO 5\n" +
                "A @54 #1,6 H2,1,24 o4 l1 c |"
        )
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)

        assertEquals(false, synth.lfo.enabled)
        assertEquals(0, synth.lfo.rate)
        assertEquals(0, synth.hardwareLfoPmsSnapshot(0))

        synth.render(FloatArray(1), 1, player, 0L)

        assertEquals(true, synth.lfo.enabled)
        assertEquals(6, synth.lfo.rate)
        assertEquals(2, synth.hardwareLfoPmsSnapshot(0))
        assertEquals(1, synth.hardwareLfoAmsSnapshot(0))
        assertEquals(CompiledOpnaSong.HW_LFO_DELAY_RAW_CLOCKS, synth.hardwareLfoDelayKindSnapshot(0))
        assertEquals(24, synth.hardwareLfoDelayValueSnapshot(0))
        assertEquals(4_000, synth.hardwareLfoDelayFramesSnapshot(0))
        assertEquals(3_999, synth.fm[0].hardwareLfoDelayRemainingSnapshot())
    }

    @Test
    fun omittedDelayRetainsRawStateAndDottedLengthResolvesAtKeyOn() {
        val arrangement = compile(
            "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\n#LFO 7\n" +
                "A @54 H2,1,24 o4 l4 c H3 d H4,2,l8. e r4 |"
        )
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, SAMPLE_RATE)

        synth.render(FloatArray(4_001), 4_001, player, 0L)
        assertEquals(3, synth.hardwareLfoPmsSnapshot(0))
        assertEquals(CompiledOpnaSong.HW_LFO_DELAY_RAW_CLOCKS, synth.hardwareLfoDelayKindSnapshot(0))
        assertEquals(24, synth.hardwareLfoDelayValueSnapshot(0))
        assertEquals(3_999, synth.fm[0].hardwareLfoDelayRemainingSnapshot())

        synth.render(FloatArray(4_000), 4_000, player, 4_001L)
        assertEquals(CompiledOpnaSong.HW_LFO_DELAY_NOTE_LENGTH, synth.hardwareLfoDelayKindSnapshot(0))
        assertEquals(8, synth.hardwareLfoDelayValueSnapshot(0))
        assertTrue(synth.hardwareLfoDelayDottedSnapshot(0))
        assertEquals(3_000, synth.hardwareLfoDelayFramesSnapshot(0))
        assertEquals(2_999, synth.fm[0].hardwareLfoDelayRemainingSnapshot())
    }

    @Test
    fun rawDelayUsesTempoInForceAtEachKeyOn() {
        val arrangement = compile(
            "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\n#LFO 7\n" +
                "A @54 H2,1,24 o4 l4 c T240 d r2 |"
        )
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, SAMPLE_RATE)

        synth.render(FloatArray(4_001), 4_001, player, 0L)

        assertEquals(2_000, synth.hardwareLfoDelayFramesSnapshot(0))
        assertEquals(1_999, synth.fm[0].hardwareLfoDelayRemainingSnapshot())
    }

    @Test
    fun orderedGlobalDisableAndRateEnableEdgesResetThenRestartPhase() {
        val arrangement = compile(
            "#MML 2\n#BPM 120\n#BAR 4/4\n#LFO 5\n" +
                "A @54 #1,6 o4 l4 c #0 d #1,7 e r4 |"
        )
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, SAMPLE_RATE)

        synth.render(FloatArray(4_001), 4_001, player, 0L)
        assertEquals(false, synth.lfo.enabled)
        assertEquals(6, synth.lfo.rate)
        assertEquals(0u, synth.lfo.phaseSnapshot())

        synth.render(FloatArray(4_000), 4_000, player, 4_001L)
        assertEquals(true, synth.lfo.enabled)
        assertEquals(7, synth.lfo.rate)
        assertTrue(synth.lfo.phaseSnapshot() != 0u)
    }

    @Test
    fun sameTickPatchCannotOverwriteExplicitStateAndMidNoteChangeDoesNotRetrigger() {
        val arrangement = compile(
            "#MML 2\n#BPM 120\n#BAR 4/4\n#LFO 7\n" +
                "A H6,2 @54 o4 l4 c H7,3 d r2 |"
        )
        val song = requireNotNull(arrangement.compiledOpnaSong)
        var index = 0
        while (index < song.eventCount) {
            if ((song.eventType[index] == CompiledOpnaSong.HW_LFO_PMS && song.stateValue[index] == 7) ||
                (song.eventType[index] == CompiledOpnaSong.HW_LFO_AMS && song.stateValue[index] == 3)
            ) {
                song.startTick[index] = 240L
            }
            index++
        }

        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, SAMPLE_RATE)
        synth.render(FloatArray(2_000), 2_000, player, 0L)
        val noteId = synth.activeFmNoteIdSnapshot(0)
        assertTrue(noteId >= 0)
        assertEquals(6, synth.hardwareLfoPmsSnapshot(0))

        synth.render(FloatArray(1), 1, player, 2_000L)

        assertEquals(7, synth.hardwareLfoPmsSnapshot(0))
        assertEquals(3, synth.hardwareLfoAmsSnapshot(0))
        assertEquals(noteId, synth.activeFmNoteIdSnapshot(0))
    }

    @Test
    fun pooledVoicesAndFm3UseTheirPhysicalLogicalOwner() {
        val pooled = compile(
            "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\n#LFO 7\n" +
                "A @54 P1 H5,2,24 o4 {c,e,g}1 |"
        )
        val pooledSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val pooledPlayer = MmlArrangementScheduler.createPlayer(pooled, SAMPLE_RATE)
        pooledSynth.render(FloatArray(1), 1, pooledPlayer, 0L)
        assertEquals(3, pooledSynth.activeFmVoiceCount())
        var active = 0
        var voice = 0
        while (voice < pooledSynth.fm.size) {
            if (pooledSynth.activeFmNoteIdSnapshot(voice) >= 0) {
                assertEquals(0, pooledSynth.logicalFmPartForVoiceSnapshot(voice))
                assertEquals(3_999, pooledSynth.fm[voice].hardwareLfoDelayRemainingSnapshot())
                active++
            }
            voice++
        }
        assertEquals(3, active)

        val fm3 = compile(
            "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\n#LFO 7\n#FM3EXTEND ON\n" +
                "C @effect H5,2,24\nC1 o4 l1 c |"
        )
        val fm3Synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val fm3Player = MmlArrangementScheduler.createPlayer(fm3, SAMPLE_RATE)
        fm3Synth.render(FloatArray(1), 1, fm3Player, 0L)
        assertEquals(5, fm3Synth.hardwareLfoPmsSnapshot(2))
        assertEquals(2, fm3Synth.hardwareLfoAmsSnapshot(2))
        assertEquals(3_999, fm3Synth.fm[2].hardwareLfoDelayRemainingSnapshot())
    }

    @Test
    fun resetClearsDomainsAndTimelineReplaysTheSameInitialState() {
        val arrangement = compile(
            "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\n#LFO 5\n" +
                "A @54 H4,2,24 o4 l1 c |"
        )
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, SAMPLE_RATE)
        val first = FloatArray(1)
        synth.render(first, 1, player, 0L)

        player.reset(synth)
        assertEquals(false, synth.lfo.enabled)
        assertEquals(0, synth.lfo.rate)
        assertEquals(0, synth.hardwareLfoPmsSnapshot(0))
        assertEquals(CompiledOpnaSong.HW_LFO_DELAY_NONE, synth.hardwareLfoDelayKindSnapshot(0))

        val replay = FloatArray(1)
        synth.render(replay, 1, player, 0L)
        assertEquals(first[0], replay[0])
        assertEquals(true, synth.lfo.enabled)
        assertEquals(5, synth.lfo.rate)
        assertEquals(4, synth.hardwareLfoPmsSnapshot(0))
        assertEquals(3_999, synth.fm[0].hardwareLfoDelayRemainingSnapshot())
    }

    private fun compile(source: String): ArrangementLanes =
        assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement

    private companion object {
        const val SAMPLE_RATE = 8_000
    }
}
