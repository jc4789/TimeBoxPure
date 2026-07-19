package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaLoudnessTest {

    private fun renderFmLead(midi: Int, durationSamples: Int = 22050): FloatArray {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunLead1)
        synth.fm[0].noteGain = 0.7f
        synth.fm[0].noteOn(midi, 0.01f, 0.05f, 0.7f, 0.08f)
        val buffer = FloatArray(durationSamples)
        synth.render(buffer, durationSamples)
        return buffer
    }

    private fun renderFmBell(midi: Int, durationSamples: Int = 22050): FloatArray {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunBell1)
        synth.fm[0].noteGain = 0.7f
        synth.fm[0].noteOn(midi, 0.01f, 0.05f, 0.7f, 0.08f)
        val buffer = FloatArray(durationSamples)
        synth.render(buffer, durationSamples)
        return buffer
    }

    private fun peakAbs(buffer: FloatArray): Float {
        var peak = 0f
        var i = 0
        while (i < buffer.size) {
            val a = kotlin.math.abs(buffer[i])
            if (a > peak) peak = a
            i++
        }
        return peak
    }

    private fun rms(buffer: FloatArray): Float {
        var sum = 0.0
        var i = 0
        while (i < buffer.size) {
            val v = buffer[i].toDouble()
            sum += v * v
            i++
        }
        return kotlin.math.sqrt(sum / buffer.size).toFloat()
    }

    @Test
    fun leadAtSustainProducesAudibleOutput() {
        val buffer = renderFmLead(midi = 70, durationSamples = AudioLaws.SAMPLE_RATE)
        val peak = peakAbs(buffer)
        val r = rms(buffer)
        assertTrue(
            peak in 0.05f..1.0f,
            "Lead peak out of audible range: peak=$peak, expected [0.05, 1.0]. " +
            "If this fails, the FM lead is too quiet (silent) or too loud (clipping)."
        )
        assertTrue(
            r > 0.02f,
            "Lead RMS too low: rms=$r, expected > 0.02. " +
            "If this fails, the FM lead is producing noise rather than a tone."
        )
    }

    @Test
    fun leadSustainsOverTime() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val buffer = renderFmLead(midi = 70, durationSamples = sampleRate)
        val quarter = buffer.size / 4
        val r1 = rms(buffer.copyOfRange(0, quarter))
        val r2 = rms(buffer.copyOfRange(quarter, quarter * 2))
        val r3 = rms(buffer.copyOfRange(quarter * 2, quarter * 3))
        val r4 = rms(buffer.copyOfRange(quarter * 3, buffer.size))
        assertTrue(
            r1 > 0.01f && r2 > 0.01f && r3 > 0.01f && r4 > 0.01f,
            "Lead RMS collapsed in one quarter of the buffer: " +
            "r1=$r1, r2=$r2, r3=$r3, r4=$r4. Expected all > 0.01."
        )
    }

    @Test
    fun bellAtSustainProducesAudibleOutput() {
        val buffer = renderFmBell(midi = 62, durationSamples = AudioLaws.SAMPLE_RATE)
        val peak = peakAbs(buffer)
        assertTrue(
            peak in 0.05f..1.0f,
            "Bell peak out of audible range: peak=$peak, expected [0.05, 1.0]. " +
            "If this fails, senbonzakura-style FM bell is too quiet."
        )
    }

    @Test
    fun ssgLeadAtSustainProducesAudibleOutput() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.ssg[0].noteGain = 0.7f
        synth.noteOnSsg(0, 70)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate)
        val peak = peakAbs(buffer)
        val r = rms(buffer)
        assertTrue(
            peak in 0.005f..1.0f,
            "SSG lead peak out of audible range: peak=$peak, expected [0.005, 1.0]. " +
            "SSG is at -18 dB hardware mix, so peak will be low but audible."
        )
        assertTrue(
            r > 0.002f,
            "SSG lead RMS too low: rms=$r, expected > 0.002."
        )
    }
}
