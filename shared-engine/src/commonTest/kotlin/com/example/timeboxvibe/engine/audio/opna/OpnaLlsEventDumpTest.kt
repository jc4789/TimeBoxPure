package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.SoundMelodies
import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaLlsEventDumpTest {

    private val key = "synth-bad-apple-LotusLandStory"

    @Test
    fun dumpFirst16Bars() {
        val arr = SoundMelodies.getArrangement(key, 1f)!!
        val spec = SongSpec(bpm = arr.tempoBpm, beatsPerBar = 4)
        
        val first16BarsMs = spec.barToMs(16)
        
        println("=== LLS Event Dump: First 16 Bars ===")
        println("BPM: ${arr.tempoBpm}")
        println("Bar duration: ${spec.msPerBar.toInt()} ms")
        println("Total bars: 54")
        println()
        
        println("--- Lead Lane (FM_LLS_AT54, MONO_RETRIGGER) ---")
        val leadEvents = arr.lead.notes
            .filter { it.startMs < first16BarsMs && it.freq > 0f }
            .sortedBy { it.startMs }
        
        for (note in leadEvents) {
            val bar = (note.startMs / spec.msPerBar).toInt()
            val beat = ((note.startMs % spec.msPerBar) / spec.msPerBeat).toInt()
            val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).toInt()
            println("bar=$bar beat=$beat midi=$midi dur=${note.durationMs}ms vol=${note.volume}")
        }
        
        println()
        println("--- Harmony Lane (SSG_HARMONY_SQUARE, SSG_MONO) ---")
        val harmonyEvents = arr.harmony.notes
            .filter { it.startMs < first16BarsMs && it.freq > 0f }
            .sortedBy { it.startMs }
        
        for (note in harmonyEvents) {
            val bar = (note.startMs / spec.msPerBar).toInt()
            val beat = ((note.startMs % spec.msPerBar) / spec.msPerBeat).toInt()
            val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).toInt()
            println("bar=$bar beat=$beat midi=$midi dur=${note.durationMs}ms vol=${note.volume}")
        }
        
        println()
        println("--- Bass Lane (FM_LLS_AT99, MONO_RETRIGGER) ---")
        val bassEvents = arr.bass.notes
            .filter { it.startMs < first16BarsMs && it.freq > 0f }
            .sortedBy { it.startMs }
        
        for (note in bassEvents) {
            val bar = (note.startMs / spec.msPerBar).toInt()
            val beat = ((note.startMs % spec.msPerBar) / spec.msPerBeat).toInt()
            val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).toInt()
            println("bar=$bar beat=$beat midi=$midi dur=${note.durationMs}ms vol=${note.volume}")
        }
        
        println()
        println("--- Percussion Lane (DRUM) ---")
        val drumEvents = arr.percussion.notes
            .filter { it.startMs < first16BarsMs }
            .sortedBy { it.startMs }
        
        var drumCount = 0
        for (note in drumEvents) {
            val bar = (note.startMs / spec.msPerBar).toInt()
            val beat = ((note.startMs % spec.msPerBar) / spec.msPerBeat).toInt()
            val drumType = when {
                note.freq < 0f -> "KICK"
                note.freq > 5000f -> "HAT"
                else -> "SNARE"
            }
            if (drumCount < 32) {
                println("bar=$bar beat=$beat type=$drumType vol=${note.volume}")
            }
            drumCount++
        }
        if (drumCount > 32) {
            println("... and ${drumCount - 32} more drum events")
        }
        
        println()
        println("=== End of Dump ===")
        
        assertTrue(leadEvents.isNotEmpty(), "Lead should have events in first 16 bars")
        assertTrue(harmonyEvents.isNotEmpty(), "Harmony should have events in first 16 bars")
        assertTrue(bassEvents.isNotEmpty(), "Bass should have events in first 16 bars")
        assertTrue(drumEvents.isNotEmpty(), "Drums should have events in first 16 bars")
    }
}
