package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.SoundMelodies
import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaLlsNoteValidationTest {

    private val key = "synth-bad-apple-LotusLandStory"

    @Test
    fun leadNotesHaveValidFrequencies() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val leadNotes = arr.lead.notes.filter { it.freq > 0f }
        
        assertTrue(leadNotes.isNotEmpty(), "Lead should have notes with valid frequencies")
        
        val minFreq = leadNotes.minOf { it.freq }
        val maxFreq = leadNotes.maxOf { it.freq }
        
        assertTrue(minFreq > 50f, "Min lead freq=$minFreq should be > 50 Hz")
        assertTrue(maxFreq < 2000f, "Max lead freq=$maxFreq should be < 2000 Hz")
        
        println("Lead: ${leadNotes.size} notes, freq range: $minFreq - $maxFreq Hz")
    }

    @Test
    fun bassNotesHaveValidFrequencies() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val bassNotes = arr.bass.notes.filter { it.freq > 0f }
        
        assertTrue(bassNotes.isNotEmpty(), "Bass should have notes with valid frequencies")
        
        val minFreq = bassNotes.minOf { it.freq }
        val maxFreq = bassNotes.maxOf { it.freq }
        
        assertTrue(minFreq > 30f, "Min bass freq=$minFreq should be > 30 Hz")
        assertTrue(maxFreq < 500f, "Max bass freq=$maxFreq should be < 500 Hz")
        
        println("Bass: ${bassNotes.size} notes, freq range: $minFreq - $maxFreq Hz")
    }

    @Test
    fun harmonyNotesHaveValidFrequencies() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val harmonyNotes = arr.harmony.notes.filter { it.freq > 0f }
        
        assertTrue(harmonyNotes.isNotEmpty(), "Harmony should have notes with valid frequencies")
        
        val minFreq = harmonyNotes.minOf { it.freq }
        val maxFreq = harmonyNotes.maxOf { it.freq }
        
        assertTrue(minFreq > 100f, "Min harmony freq=$minFreq should be > 100 Hz")
        assertTrue(maxFreq < 1500f, "Max harmony freq=$maxFreq should be < 1500 Hz")
        
        println("Harmony: ${harmonyNotes.size} notes, freq range: $minFreq - $maxFreq Hz")
    }

    @Test
    fun notesStartAtExpectedBars() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val spec = SongSpec(bpm = arr.tempoBpm, beatsPerBar = 4)
        
        val firstLeadBar = (arr.lead.notes.minOf { it.startMs } / spec.msPerBar).toInt()
        val firstBassBar = (arr.bass.notes.minOf { it.startMs } / spec.msPerBar).toInt()
        val firstHarmonyBar = (arr.harmony.notes.minOf { it.startMs } / spec.msPerBar).toInt()
        
        println("First lead note at bar: $firstLeadBar")
        println("First bass note at bar: $firstBassBar")
        println("First harmony note at bar: $firstHarmonyBar")
        
        assertTrue(firstLeadBar in 0..2, "Lead should start around bar 0, got bar $firstLeadBar")
        assertTrue(firstBassBar in 0..2, "Bass should start around bar 0, got bar $firstBassBar")
        assertTrue(firstHarmonyBar in 0..2, "Harmony should start around bar 0, got bar $firstHarmonyBar")
    }
}
