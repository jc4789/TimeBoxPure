package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for Fm4OpVoice modulationIndex routing.
 *
 * Catches the "destination uses destination's modulationIndex" bug
 * (which produces dial-tone / unmodulated carrier).
 *
 * Math:
 *   - Pure sine wave: peak/rms = sqrt(2) ~= 1.414
 *   - FM-modulated signal: peak/rms > 1.5 (richer harmonic content)
 *
 * If this test fails, the modulation index is being applied to the
 * wrong operator, the carrier is unmodulated, or the patch has
 * a tl / outputLevel that silences it.
 */
class OpnaFmRichnessTest {

    private fun renderFmLead(patch: FmPatch, midi: Int, durationSamples: Int): FloatArray {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(patch)
        synth.fm[0].noteGain = 0.05f
        synth.fm[0].noteOn(midi, 0.005f, 0.05f, 0.7f, 0.1f)
        val buffer = FloatArray(durationSamples)
        synth.render(buffer, durationSamples)
        return buffer
    }

    private fun peakRmsRatio(buffer: FloatArray): Float {
        var peak = 0f
        var sumSq = 0.0
        var i = 0
        while (i < buffer.size) {
            val v = buffer[i]
            val a = abs(v)
            if (a > peak) peak = a
            sumSq += v.toDouble() * v.toDouble()
            i++
        }
        val rms = sqrt(sumSq / buffer.size).toFloat()
        if (rms <= 0f) return 0f
        return peak / rms
    }

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

    @Test
    fun zunLead1ProducesFmRichOutput() {
        val buffer = renderFmLead(Patches.ZunLead1, midi = 69, durationSamples = AudioLaws.SAMPLE_RATE)
        val ratio = peakRmsRatio(buffer)
        val r = rms(buffer)
        assertTrue(
            ratio > 1.5f,
            "ZunLead1 peak/rms=$ratio is at the sine threshold (~1.41). " +
            "FM modulation is missing — the carrier is unmodulated. " +
            "Check that op1/op2/op3 phases use p.op0/p.op1/p.op2 modulationIndex (source op, not destination)."
        )
        assertTrue(
            r > 0.005f,
            "ZunLead1 RMS=$r is too low — carrier is silent or noteOn didn't fire."
        )
    }

    @Test
    fun zunBell1ProducesFmRichOutput() {
        val buffer = renderFmLead(Patches.ZunBell1, midi = 69, durationSamples = AudioLaws.SAMPLE_RATE)
        val ratio = peakRmsRatio(buffer)
        val r = rms(buffer)
        assertTrue(
            ratio > 1.5f,
            "ZunBell1 peak/rms=$ratio is at the sine threshold. FM modulation missing."
        )
        assertTrue(
            r > 0.003f,
            "ZunBell1 RMS=$r is too low."
        )
    }

    @Test
    fun zunBass1ProducesFmRichOutput() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunBass1)
        synth.fm[0].noteGain = 0.05f
        synth.fm[0].noteOn(45, 0.005f, 0.05f, 0.7f, 0.1f)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate)
        val ratio = peakRmsRatio(buffer)
        val r = rms(buffer)
        assertTrue(
            ratio > 1.5f,
            "ZunBass1 peak/rms=$ratio is at the sine threshold. " +
            "Alg 1 chains (op0->op2, op1->op3) not modulated. " +
            "Check that alg 1 op2/op3 use p.op0/p.op1 modulationIndex."
        )
        assertTrue(
            r > 0.001f,
            "ZunBass1 RMS=$r is too low."
        )
    }

    @Test
    fun zunPad1ProducesFmRichOutput() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunPad1)
        synth.fm[0].noteGain = 0.05f
        synth.fm[0].noteOn(57, 0.005f, 0.05f, 0.7f, 0.1f)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate)
        val ratio = peakRmsRatio(buffer)
        val r = rms(buffer)
        assertTrue(
            ratio > 1.5f,
            "ZunPad1 peak/rms=$ratio is at the sine threshold. FM modulation missing."
        )
        assertTrue(
            r > 0.003f,
            "ZunPad1 RMS=$r is too low."
        )
    }
}
