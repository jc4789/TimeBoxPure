package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.math.sqrt
import kotlin.math.abs

class OpnaAcceptanceChecklistTest {
    @Test
    fun testFullEnsemble() {
        val synth = OpnaLikeSynthesizer(44100)
        val seq = OpnaSequencer(44100, 138)
        OpnaPatterns.focusMotif(seq)

        val samples = 176400 // 4 seconds at 44.1 kHz
        val buffer = FloatArray(1024)

        var sumSquares = 0.0
        var totalSamples = 0
        var maxVal = 0f
        var hasNaN = false

        var offset = 0L
        while (offset < samples) {
            synth.render(buffer, 1024, seq, offset)

            var i = 0
            while (i < 1024) {
                val v = buffer[i]
                if (v.isNaN()) hasNaN = true
                val absV = abs(v)
                if (absV > maxVal) maxVal = absV
                sumSquares += v * v
                i++
            }
            totalSamples += 1024
            offset += 1024
        }

        assertTrue(!hasNaN, "Output contained NaN values")
        assertTrue(maxVal <= 1f, "Output exceeded bounds [-1, 1]: $maxVal")

        val rms = sqrt(sumSquares / totalSamples)
        assertTrue(rms > 0.05, "RMS was too quiet: $rms")
    }
}
