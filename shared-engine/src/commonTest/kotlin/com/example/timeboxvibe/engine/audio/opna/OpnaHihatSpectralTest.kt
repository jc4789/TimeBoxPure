package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaHihatSpectralTest {
    @Test
    fun testHihatSpectralEnergy() {
        val drums = ProceduralDrums()
        drums.triggerHat()

        val sampleRate = 44100
        val buffer = FloatArray(44100)
        drums.render(buffer, 44100, sampleRate, 1.0f)

        // Calculate energy using a 5 kHz high-pass filter
        // y[n] = alpha * (y[n-1] + x[n] - x[n-1])
        // alpha = 1 / (1 + 2 * PI * fc / fs)
        // For fc = 5000, fs = 44100 -> alpha = 1 / (1 + 0.7123) = 0.584
        val alpha = 0.584f
        var prevX = 0f
        var prevY = 0f
        var hpEnergy = 0f
        var totalEnergy = 0f

        var i = 0
        while (i < 44100) {
            val x = buffer[i]
            val y = alpha * (prevY + x - prevX)
            prevX = x
            prevY = y

            hpEnergy += y * y
            totalEnergy += x * x
            i++
        }

        assertTrue(totalEnergy > 0f, "Hihat did not generate any sound")
        val ratio = hpEnergy / totalEnergy
        // Assert that at least 45% of the energy survives the 5kHz high-pass filter
        assertTrue(ratio > 0.45f, "Hihat energy ratio below 5kHz was too high: $ratio")
    }
}
