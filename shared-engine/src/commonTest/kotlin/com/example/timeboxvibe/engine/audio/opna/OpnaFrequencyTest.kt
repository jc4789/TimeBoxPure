package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaFrequencyTest {

    private fun renderFmOneSecond(midi: Int): FloatArray {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        val simpleTestPatch = FmPatch(
            algorithm = 3,
            feedback = 0,
            op0 = OperatorSpec(mul = 1, detune = 0, tl = 127, modulationIndex = 0f, attack = 0.001f, decay = 0.01f, sustain = 1.0f, release = 0.01f),
            op1 = OperatorSpec(mul = 1, detune = 0, tl = 127, modulationIndex = 0f, attack = 0.001f, decay = 0.01f, sustain = 1.0f, release = 0.01f),
            op2 = OperatorSpec(mul = 1, detune = 0, tl = 127, modulationIndex = 0f, attack = 0.001f, decay = 0.01f, sustain = 1.0f, release = 0.01f),
            op3 = OperatorSpec(mul = 1, detune = 0, tl = 0,   modulationIndex = 0f, attack = 0.001f, decay = 0.01f, sustain = 1.0f, release = 0.01f),
            totalLevel = 1.0f,
            pms = 0, pan = 0
        )
        synth.fm[0].applyPatch(simpleTestPatch)
        val buffer = FloatArray(sampleRate)
        synth.fm[0].noteOn(midi, 0.05f, 0.05f, 0.7f, 0.1f)
        synth.render(buffer, sampleRate)
        return buffer
    }

    private fun renderSsgOneSecond(midi: Int): FloatArray {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        val buffer = FloatArray(sampleRate)
        synth.noteOnSsg(0, midi)
        synth.render(buffer, sampleRate)
        return buffer
    }

    private fun countZeroCrossings(buffer: FloatArray, threshold: Float = 0.01f): Int {
        var crossings = 0
        var lastAbove = false
        var primed = false
        var i = 0
        while (i < buffer.size) {
            val v = buffer[i]
            if (kotlin.math.abs(v) > threshold) {
                val above = v > 0f
                if (primed && above != lastAbove) {
                    crossings++
                }
                lastAbove = above
                primed = true
            }
            i++
        }
        return crossings
    }

    private fun expectedCrossingsHz(buffer: FloatArray, sampleRate: Int): Double {
        val durationSec = buffer.size.toDouble() / sampleRate
        val crossings = countZeroCrossings(buffer)
        return crossings / durationSec / 2.0
    }

    @Test
    fun fmA4Produces440Hz() {
        val buffer = renderFmOneSecond(midi = 69)
        val measured = expectedCrossingsHz(buffer, AudioLaws.SAMPLE_RATE)
        assertTrue(
            kotlin.math.abs(measured - 440.0) < 5.0,
            "FM A4 frequency out of range: measured=$measured Hz, expected 440 Hz"
        )
    }

    @Test
    fun fmA5Produces880Hz() {
        val buffer = renderFmOneSecond(midi = 81)
        val measured = expectedCrossingsHz(buffer, AudioLaws.SAMPLE_RATE)
        assertTrue(
            kotlin.math.abs(measured - 880.0) < 8.0,
            "FM A5 frequency out of range: measured=$measured Hz, expected 880 Hz"
        )
    }

    @Test
    fun fmA3Produces220Hz() {
        val buffer = renderFmOneSecond(midi = 57)
        val measured = expectedCrossingsHz(buffer, AudioLaws.SAMPLE_RATE)
        assertTrue(
            kotlin.math.abs(measured - 220.0) < 4.0,
            "FM A3 frequency out of range: measured=$measured Hz, expected 220 Hz"
        )
    }

    @Test
    fun ssgA4Produces440Hz() {
        val buffer = renderSsgOneSecond(midi = 69)
        val measured = expectedCrossingsHz(buffer, AudioLaws.SAMPLE_RATE)
        assertTrue(
            kotlin.math.abs(measured - 440.0) < 5.0,
            "SSG A4 frequency out of range: measured=$measured Hz, expected 440 Hz"
        )
    }

    @Test
    fun ssgBb4Produces466Hz() {
        val buffer = renderSsgOneSecond(midi = 70)
        val measured = expectedCrossingsHz(buffer, AudioLaws.SAMPLE_RATE)
        assertTrue(
            kotlin.math.abs(measured - 466.16) < 6.0,
            "SSG Bb4 frequency out of range: measured=$measured Hz, expected 466.16 Hz"
        )
    }

    @Test
    fun fmIsDeterministic() {
        val a = renderFmOneSecond(midi = 69)
        val b = renderFmOneSecond(midi = 69)
        assertTrue(a.contentEquals(b), "FM render must be deterministic across instances")
    }

    @Test
    fun ssgIsDeterministic() {
        val a = renderSsgOneSecond(midi = 69)
        val b = renderSsgOneSecond(midi = 69)
        assertTrue(a.contentEquals(b), "SSG render must be deterministic across instances")
    }
}
