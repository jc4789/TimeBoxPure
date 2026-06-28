package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.SoundMelodies
import com.example.timeboxvibe.engine.TimbreRef
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Diagnostic test to verify FM synthesis is producing audible output
 * for the synth-victory arrangement.
 */
class OpnaDiagnosticTest {

    private fun rms(buffer: FloatArray): Float {
        var sumSq = 0.0
        var i = 0
        while (i < buffer.size) {
            val v = buffer[i].toDouble()
            sumSq += v * v
            i++
        }
        return sqrt(sumSq / buffer.size).toFloat()
    }

    private fun peakAbs(buffer: FloatArray): Float {
        var peak = 0f
        var i = 0
        while (i < buffer.size) {
            val a = abs(buffer[i])
            if (a > peak) peak = a
            i++
        }
        return peak
    }

    @Test
    fun synthVictoryProducesAudibleOutput() {
        val arrangement = SoundMelodies.getArrangement("synth-victory", 1f)!!
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm)

        // Route lead lane (FM)
        val leadPatch = when (arrangement.lead.timbre) {
            TimbreRef.FM_LEAD_ZUN1 -> Patches.ZunLead1
            TimbreRef.FM_BELL_ZUN1 -> Patches.ZunBell1
            TimbreRef.FM_PAD_ZUN1 -> Patches.ZunPad1
            else -> null
        }

        assertTrue(leadPatch != null, "synth-victory lead should use FM patch")

        synth.fm[0].applyPatch(leadPatch!!)
        for (note in arrangement.lead.notes) {
            if (note.freq <= 10f) continue
            val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).toInt()
            val velocity = note.volume * OpnaAudioConstants.LANE_GAIN_LEAD
            sequencer.noteFmRaw(
                0, midi,
                (note.startMs.toLong() * sampleRate) / 1000L,
                (note.durationMs.toLong() * sampleRate) / 1000L,
                velocity, null, null, null, null
            )
        }

        // Render 2 seconds
        val buffer = FloatArray(sampleRate * 2)
        synth.render(buffer, buffer.size, sequencer, 0L)

        val r = rms(buffer)
        val p = peakAbs(buffer)

        println("synth-victory FM lead: RMS=$r, peak=$p")
        println("Number of lead notes: ${arrangement.lead.notes.size}")
        arrangement.lead.notes.forEachIndexed { i, note ->
            println("  Note $i: freq=${note.freq}Hz, start=${note.startMs}ms, dur=${note.durationMs}ms, vol=${note.volume}")
        }

        assertTrue(r > 0.01f, "synth-victory FM lead RMS=$r is too low")
        assertTrue(p > 0.05f, "synth-victory FM lead peak=$p is too low")
    }

    @Test
    fun synthChimeSsgProducesAudibleOutput() {
        val arrangement = SoundMelodies.getArrangement("synth-chime", 1f)!!
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm)

        // Route lead lane (SSG)
        for (note in arrangement.lead.notes) {
            if (note.freq <= 10f) continue
            val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).toInt()
            sequencer.noteSsgRaw(
                0, midi,
                (note.startMs.toLong() * sampleRate) / 1000L,
                (note.durationMs.toLong() * sampleRate) / 1000L,
                note.volume * OpnaAudioConstants.LANE_GAIN_LEAD
            )
        }

        // Render 1 second
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, buffer.size, sequencer, 0L)

        val r = rms(buffer)
        val p = peakAbs(buffer)

        println("synth-chime SSG lead: RMS=$r, peak=$p")
        println("Number of lead notes: ${arrangement.lead.notes.size}")

        assertTrue(r > 0.01f, "synth-chime SSG lead RMS=$r is too low")
        assertTrue(p > 0.05f, "synth-chime SSG lead peak=$p is too low")
    }
}
