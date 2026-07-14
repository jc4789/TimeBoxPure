package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimeline
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogoSongIngestionTest {
    @Test
    fun sourceOwnedPatchMatchesDecodedAt79Registers() {
        val success = assertIs<MmlCompileResult.Success>(MmlSongBank.llsLogoResult)
        val program = assertNotNull(success.arrangement.compiledOpnaSong)
        val fmEvent = noteIndices(program, CompiledOpnaSong.FM_NOTE, 0)[0]
        val patch = assertNotNull(program.instrumentBank.fmPatch(program.patchId[fmEvent]))

        assertEquals(1, program.instrumentBank.fmPatchCount)
        assertEquals(1, program.instrumentBank.ssgPatchCount)
        assertEquals(-1, OpnaPatchBank.idForName("logo79"))
        assertEquals(null, OpnaPatchBank.fmPatch(13))
        assertEquals(4, patch.algorithm)
        assertEquals(7, patch.feedback)
        assertContentEquals(intArrayOf(2, 2, 4, 4), intArrayOf(patch.op0.mul, patch.op1.mul, patch.op2.mul, patch.op3.mul))
        assertContentEquals(intArrayOf(3, 7, 3, 7), intArrayOf(patch.op0.detune, patch.op1.detune, patch.op2.detune, patch.op3.detune))
        assertContentEquals(intArrayOf(30, 31, 0, 0), intArrayOf(patch.op0.tl, patch.op1.tl, patch.op2.tl, patch.op3.tl))
        assertContentEquals(intArrayOf(3, 3, 0, 0), intArrayOf(patch.op0.ks, patch.op1.ks, patch.op2.ks, patch.op3.ks))
        assertContentEquals(intArrayOf(15, 15, 13, 12), intArrayOf(patch.op0.ar, patch.op1.ar, patch.op2.ar, patch.op3.ar))
        assertContentEquals(intArrayOf(0, 0, 0, 0), intArrayOf(patch.op0.ams, patch.op1.ams, patch.op2.ams, patch.op3.ams))
        assertContentEquals(intArrayOf(0, 0, 2, 2), intArrayOf(patch.op0.dr, patch.op1.dr, patch.op2.dr, patch.op3.dr))
        assertContentEquals(intArrayOf(0, 0, 2, 6), intArrayOf(patch.op0.sr, patch.op1.sr, patch.op2.sr, patch.op3.sr))
        assertContentEquals(intArrayOf(0, 0, 13, 13), intArrayOf(patch.op0.sl, patch.op1.sl, patch.op2.sl, patch.op3.sl))
        assertContentEquals(intArrayOf(5, 3, 4, 5), intArrayOf(patch.op0.rr, patch.op1.rr, patch.op2.rr, patch.op3.rr))
    }

    @Test
    fun normalizedTimelineMatchesLogoM86NotesAndTempoState() {
        val success = assertIs<MmlCompileResult.Success>(MmlSongBank.llsLogoResult)
        val program = assertNotNull(success.arrangement.compiledOpnaSong)

        assertEquals(239, program.eventCount)
        assertEquals(3_960L, program.durationTicks)
        assertEquals(5, program.tempoChangeCount)
        assertContentEquals(longArrayOf(3_360L, 3_480L, 3_600L, 3_720L, 3_840L), program.tempoTick)
        assertContentEquals(intArrayOf(70_000, 60_000, 45_000, 30_000, 18_000), program.tempoBpmMilli)

        assertNoteTrace(
            program, CompiledOpnaSong.FM_NOTE, 0,
            longArrayOf(0L, 1_920L), intArrayOf(1_920, 1_920), intArrayOf(39, 40), intArrayOf(95, 106)
        )
        assertNoteTrace(
            program, CompiledOpnaSong.FM_NOTE, 1,
            longArrayOf(0L, 1_920L), intArrayOf(1_920, 1_920), intArrayOf(32, 44), intArrayOf(95, 106)
        )
        assertSsgTrace(program, 0, 0L, 25, 93)
        assertSsgTrace(program, 1, 0L, 25, 93)
        assertSsgTrace(program, 2, 120L, 17, 85)

        val player = MmlArrangementScheduler.createPlayer(success.arrangement, OpnaLikeSynthesizer(48_000), 48_000)
        assertEquals(441, player.eventCount)
        assertContentEquals(
            intArrayOf(
                CompiledOpnaTimeline.HW_LFO_RATE,
                CompiledOpnaTimeline.HW_LFO_ENABLE,
                CompiledOpnaTimeline.TEMPO
            ),
            player.timeline.eventType.copyOf(3)
        )
    }

    @Test
    fun researchFixtureStaysOutOfCatalogAndRendersDeterministically() {
        assertEquals(null, SongCatalog.byId(MmlSongBank.LLS_LOGO_KEY))
        assertDeterministicPrefix(48_000)
        assertDeterministicPrefix(55_466)
        assertUnmasteredPrefixIsFinite(48_000)
    }

    private fun assertSsgTrace(program: CompiledOpnaSong, channel: Int, firstTick: Long, firstVelocity: Int, secondVelocity: Int) {
        val indices = noteIndices(program, CompiledOpnaSong.SSG_NOTE, channel)
        assertEquals(64, indices.size)
        var i = 0
        while (i < indices.size) {
            val event = indices[i]
            assertEquals(firstTick + i * 60L, program.startTick[event])
            assertEquals(60, program.durationTick[event])
            val groupIndex = i and 7
            val octaveGroup = i / 32
            val expectedMidi = if (octaveGroup == 0) {
                intArrayOf(44, 46, 47, 51, 44, 46, 47, 44)[groupIndex]
            } else if (i < 56) {
                intArrayOf(51, 56, 58, 63, 59, 58, 56, 58)[groupIndex]
            } else {
                intArrayOf(63, 68, 70, 75, 71, 70, 68, 70)[groupIndex]
            }
            assertEquals(expectedMidi, program.midi[event])
            assertEquals(if (i < 32) firstVelocity else secondVelocity, program.velocity[event])
            i++
        }
    }

    private fun assertNoteTrace(
        program: CompiledOpnaSong,
        type: Int,
        channel: Int,
        starts: LongArray,
        durations: IntArray,
        pitches: IntArray,
        velocities: IntArray
    ) {
        val indices = noteIndices(program, type, channel)
        assertEquals(starts.size, indices.size)
        var i = 0
        while (i < indices.size) {
            val event = indices[i]
            assertEquals(starts[i], program.startTick[event])
            assertEquals(durations[i], program.durationTick[event])
            assertEquals(pitches[i], program.midi[event])
            assertEquals(velocities[i], program.velocity[event])
            assertEquals(0, program.patchId[event])
            i++
        }
    }

    private fun noteIndices(program: CompiledOpnaSong, type: Int, channel: Int): IntArray {
        var count = 0
        var i = 0
        while (i < program.eventCount) {
            if (program.eventType[i] == type && program.channel[i] == channel) count++
            i++
        }
        val result = IntArray(count)
        var output = 0
        i = 0
        while (i < program.eventCount) {
            if (program.eventType[i] == type && program.channel[i] == channel) result[output++] = i
            i++
        }
        return result
    }

    private fun assertDeterministicPrefix(sampleRate: Int) {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.llsLogoResult).arrangement
        val firstSynth = OpnaLikeSynthesizer(sampleRate)
        val secondSynth = OpnaLikeSynthesizer(sampleRate)
        val firstPlayer = MmlArrangementScheduler.createPlayer(arrangement, firstSynth, sampleRate)
        val secondPlayer = MmlArrangementScheduler.createPlayer(arrangement, secondSynth, sampleRate)
        val first = FloatArray(4_096)
        val second = FloatArray(4_096)
        firstSynth.render(first, first.size, firstPlayer, 0L)
        secondSynth.render(second, second.size, secondPlayer, 0L)
        assertContentEquals(first, second)
        assertTrue(first.any { it != 0f })
        assertTrue(first.all { it.isFinite() })
    }

    private fun assertUnmasteredPrefixIsFinite(sampleRate: Int) {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.llsLogoResult).arrangement
        val synth = OpnaLikeSynthesizer(sampleRate)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)
        val raw = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        synth.renderRawCore(raw, raw.size, player, 0L)
        assertTrue(raw.any { it != 0f })
        assertTrue(raw.all { it.isFinite() })
    }
}
