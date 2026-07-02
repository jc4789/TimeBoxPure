package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpnaFrequencyTest {

    private fun renderFmOneSecond(midi: Int): FloatArray {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        val simpleTestPatch = FmPatch(
            algorithm = 3,
            feedback = 0,
            op0 = OperatorSpec(mul = 1, detune = 0, tl = 127, ar = 31, dr = 0, sr = 0, sl = 0, egMode = EgMode.OPN_RATE),
            op1 = OperatorSpec(mul = 1, detune = 0, tl = 127, ar = 31, dr = 0, sr = 0, sl = 0, egMode = EgMode.OPN_RATE),
            op2 = OperatorSpec(mul = 1, detune = 0, tl = 127, ar = 31, dr = 0, sr = 0, sl = 0, egMode = EgMode.OPN_RATE),
            op3 = OperatorSpec(mul = 1, detune = 0, tl = 0, ar = 31, dr = 0, sr = 0, sl = 0, egMode = EgMode.OPN_RATE),
            totalLevel = 1.0f,
            pms = 0, pan = 0
        )
        synth.fm[0].applyPatch(simpleTestPatch)
        val buffer = FloatArray(sampleRate)
        synth.fm[0].noteOn(midi)
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

    private fun renderSingleFmOperatorOneSecond(opIndex: Int): FloatArray {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val audible = OperatorSpec(
            mul = 1, detune = 0, tl = 0,
            ar = 31, dr = 0, sr = 0, sl = 0, egMode = EgMode.OPN_RATE
        )
        val muted = audible.copy(tl = 127)
        val patch = FmPatch(
            algorithm = 7,
            feedback = 0,
            op0 = if (opIndex == 0) audible else muted,
            op1 = if (opIndex == 1) audible else muted,
            op2 = if (opIndex == 2) audible else muted,
            op3 = if (opIndex == 3) audible else muted,
            totalLevel = 1f
        )
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(patch)
        synth.fm[0].noteOn(69)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, buffer.size)
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
    fun everyOperatorPathKeepsA4PitchAcrossUIntWraps() {
        var opIndex = 0
        while (opIndex < 4) {
            val buffer = renderSingleFmOperatorOneSecond(opIndex)
            val measured = expectedCrossingsHz(buffer, AudioLaws.SAMPLE_RATE)
            assertTrue(
                kotlin.math.abs(measured - 440.0) < 5.0,
                "FM operator $opIndex measured=$measured Hz after repeated UInt phase wraps"
            )
            opIndex++
        }
    }

    @Test
    fun proceduralLogTablesMatchTheirDocumentedEquations() {
        var index = 0
        while (index < OpnLogTables.QUARTER_SIZE) {
            val angle = (index.toDouble() + 0.5) * kotlin.math.PI / 512.0
            val expectedLog = (-kotlin.math.log2(kotlin.math.sin(angle)) * 256.0 + 0.5).toInt()
            val expectedPower = (8191.0 * 2.0.pow(-index.toDouble() / 256.0) + 0.5).toInt()
            assertEquals(expectedLog, OpnLogTables.quarterLogValue(index), "log-sine mismatch at $index")
            assertEquals(expectedPower, OpnLogTables.powerValue(index), "power mismatch at $index")
            index++
        }
        assertTrue(OpnLogTables.quarterLogValue(0) > OpnLogTables.quarterLogValue(255))
        assertTrue(OpnLogTables.powerValue(0) > OpnLogTables.powerValue(255))
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
