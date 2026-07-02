package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.SoundMelodies
import com.example.timeboxvibe.engine.SongCatalog
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpnaBadAppleLotusLandStoryTest {

    private val key = SongCatalog.BAD_APPLE_LLS_ID

    @Test
    fun keyIsInSongCatalog() {
        assertNotNull(SongCatalog.byId(key))
    }

    @Test
    fun getArrangementReturnsNonNull() {
        val arr = SoundMelodies.getArrangement(key, 1f)
        assertNotNull(arr, "getArrangement('$key') returned null")
    }

    @Test
    fun tempoBpmIs16073() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        assertTrue(
            arr.tempoBpm in 160.0f..161.0f,
            "tempoBpm out of range: got ${arr.tempoBpm}, expected 160.73 ± 0.27"
        )
    }

    @Test
    fun allLanesAreNonEmpty() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        assertTrue(arr.lead.notes.isNotEmpty(), "lead lane is empty")
        assertTrue(arr.harmony.notes.isNotEmpty(), "harmony lane is empty")
        assertTrue(arr.bass.notes.isNotEmpty(), "bass lane is empty")
        assertTrue(arr.percussion.notes.isNotEmpty(), "percussion lane is empty")
    }

    @Test
    fun leadContainsChorusSection() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val hasLateNote = arr.lead.notes.any { it.startMs >= 45000 }
        assertTrue(
            hasLateNote,
            "Lead lane should have at least one note with startMs >= 45000 (covers chorus / key change). " +
            "Max startMs in lead: ${arr.lead.notes.maxOfOrNull { it.startMs }}"
        )
    }

    @Test
    fun totalDurationIsInExpectedRange() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val allNotes = arr.lead.notes + arr.harmony.notes + arr.bass.notes + arr.percussion.notes
        val maxEnd = allNotes.maxOf { it.startMs + it.durationMs }
        assertTrue(
            maxEnd in 110000..125000,
            "Total arrangement duration $maxEnd ms is out of range [110000, 125000]. " +
            "Expected ~119000 ms for 40 bars * 2 loops at 160.73 BPM."
        )
    }

    @Test
    fun existingFiveKeysStillWork() {
        val existingKeys = arrayOf(
            SongCatalog.SYNTH_CHIME_ID,
            SongCatalog.SYNTH_VICTORY_ID,
            SongCatalog.SYNTH_BAD_APPLE_ID,
            SongCatalog.SYNTH_SENBONZAKURA_ID
        )
        for (k in existingKeys) {
            val arr = SoundMelodies.getArrangement(k, 1f)
            assertNotNull(arr, "getArrangement('$k') returned null — existing key broken")
            assertTrue(arr.lead.notes.isNotEmpty(), "Existing key '$k' has empty lead lane")
        }
    }

    @Test
    fun arrangementHasValidFrequencies() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val allNotes = arr.lead.notes + arr.harmony.notes + arr.bass.notes
        for (note in allNotes) {
            assertTrue(
                note.freq > 0f || note.freq == -1f,
                "Note has invalid frequency: ${note.freq} (startMs=${note.startMs}, durationMs=${note.durationMs})"
            )
        }
    }
}
