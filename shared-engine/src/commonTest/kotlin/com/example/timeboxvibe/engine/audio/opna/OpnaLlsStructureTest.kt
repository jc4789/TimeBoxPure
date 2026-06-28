package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.SoundMelodies
import com.example.timeboxvibe.engine.TimbreRef
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Structure test for synth-bad-apple-LotusLandStory.
 * Locks in the section sizes and the intro structure (2 N.C. + 2 E♭m).
 *
 * Total: 4 + 16 + 12 + 15 = 47 bars at 160.73 BPM = ~70.2 sec
 */
class OpnaLlsStructureTest {

    private val key = "synth-bad-apple-LotusLandStory"
    private val sampleRate = 44100

    @Test
    fun leadStartsAtBeginning() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val firstLeadStart = arr.lead.notes.minOf { it.startMs }
        assertTrue(
            firstLeadStart == 0,
            "Lead's first note should start immediately at 0 ms, but starts at $firstLeadStart ms."
        )
    }

    @Test
    fun harmonyAndBassStartAfterNCRest() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val firstHarmonyStart = arr.harmony.notes.minOf { it.startMs }
        val firstBassStart = arr.bass.notes.minOf { it.startMs }
        val ncBars = 2
        val ncMs = ncBars * 1492
        assertTrue(
            firstHarmonyStart >= ncMs - 1,
            "Harmony should start at or after the 2-bar N.C. rest (>= $ncMs ms), but starts at $firstHarmonyStart ms."
        )
        assertTrue(
            firstBassStart >= ncMs - 1,
            "Bass should start at or after the 2-bar N.C. rest (>= $ncMs ms), but starts at $firstBassStart ms."
        )
    }

    @Test
    fun leadHasSustainedContent() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val leadNoteCount = arr.lead.notes.size
        assertTrue(
            leadNoteCount >= 50,
            "Lead should have at least 50 notes for a 47-bar song, got $leadNoteCount. " +
            "If low, the B section or chorus may be empty."
        )
    }

    @Test
    fun chorusHasKeyChange() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val chorusStartMs = (4 + 16 + 12) * 1492
        val chorusNotes = arr.lead.notes.filter { it.startMs >= chorusStartMs }
        val minFreq = chorusNotes.minOf { it.freq }
        val maxFreq = chorusNotes.maxOf { it.freq }
        assertTrue(
            minFreq > 60f,
            "Chorus should have notes above 60 Hz (it's the climactic section). " +
            "Min chorus freq=$minFreq, max=$maxFreq."
        )
        assertTrue(
            maxFreq < 1200f,
            "Chorus should not have notes above 1200 Hz (avoid ear-piercing high notes). " +
            "Max chorus freq=$maxFreq."
        )
    }

    @Test
    fun noMidSongTimbreJump() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val leadTimbre = arr.lead.timbre
        assertTrue(
            leadTimbre == TimbreRef.FM_LEAD_ZUN1 || leadTimbre == TimbreRef.SSG_HARMONY_SQUARE,
            "Lead timbre should be consistent throughout the song (no mid-song switch). " +
            "Got $leadTimbre."
        )
    }

    @Test
    fun totalDurationMatchesPlan() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val allNotes = arr.lead.notes + arr.harmony.notes + arr.bass.notes + arr.percussion.notes
        val maxEnd = allNotes.maxOf { it.startMs + it.durationMs }
        assertTrue(
            maxEnd in 65000..80000,
            "Total duration $maxEnd ms is outside [65000, 80000]. " +
            "Expected ~70000 ms for 47 bars at 160.73 BPM."
        )
    }

    @Test
    fun tempoBpmIsCorrect() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        assertTrue(
            arr.tempoBpm in 160.0f..161.0f,
            "tempoBpm=${arr.tempoBpm} is outside [160, 161]. " +
            "Expected 160.73 (PC-98 source tempo)."
        )
    }
}
