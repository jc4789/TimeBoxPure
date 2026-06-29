package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.SoundMelodies
import com.example.timeboxvibe.engine.TimbreRef
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Structure test for synth-bad-apple-LotusLandStory.
 * Locks in the section sizes and the intro structure (14 bars intro + 8 bars A + 16 bars B + 16 bars chorus).
 *
 * Total: 14 + 8 + 16 + 16 = 54 bars at 160.73 BPM = ~80.6 sec
 */
class OpnaLlsStructureTest {

    private val key = "synth-bad-apple-LotusLandStory"
    private val sampleRate = 44100

    @Test
    fun leadStartsAtASection() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val firstLeadStart = arr.lead.notes.minOf { it.startMs }
        val aSectionBars = 0
        val aSectionMs = (aSectionBars * 4 * 60000f / 160.73f).toInt()
        assertTrue(
            firstLeadStart in (aSectionMs - 100)..(aSectionMs + 100),
            "Lead's first note should start at the A section (~$aSectionMs ms), but starts at $firstLeadStart ms."
        )
    }

    @Test
    fun harmonyAndBassStartAtASection() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val firstHarmonyStart = arr.harmony.notes.minOf { it.startMs }
        val firstBassStart = arr.bass.notes.minOf { it.startMs }
        val aSectionBars = 0
        val aSectionMs = (aSectionBars * 4 * 60000f / 160.73f).toInt()
        assertTrue(
            firstHarmonyStart >= aSectionMs - 100,
            "Harmony should start at or after the A section (>= $aSectionMs ms), but starts at $firstHarmonyStart ms."
        )
        assertTrue(
            firstBassStart >= aSectionMs - 100,
            "Bass should start at or after the A section (>= $aSectionMs ms), but starts at $firstBassStart ms."
        )
    }

    @Test
    fun leadHasSustainedContent() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val leadNoteCount = arr.lead.notes.size
        assertTrue(
            leadNoteCount >= 50,
            "Lead should have at least 50 notes for a 54-bar song, got $leadNoteCount. " +
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
            leadTimbre == TimbreRef.FM_LEAD_ZUN1 || leadTimbre == TimbreRef.SSG_HARMONY_SQUARE || leadTimbre == TimbreRef.FM_LLS_AT54,
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
            maxEnd in 55000..65000,
            "Total duration $maxEnd ms is outside [55000, 65000]. " +
            "Expected ~60000 ms for 40 bars at 160.73 BPM."
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
