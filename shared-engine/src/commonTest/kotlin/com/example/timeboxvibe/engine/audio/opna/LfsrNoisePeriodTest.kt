package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LfsrNoisePeriodTest {
    @Test
    fun testNoisePeriodAndDeterminism() {
        val noise1 = LfsrNoise(0xBEEF)
        val noise2 = LfsrNoise(0xBEEF)

        for (i in 0 until 100) {
            assertEquals(noise1.next(), noise2.next())
        }

        val n1 = LfsrNoise(1)
        val n2 = LfsrNoise(1)
        // Advance n1 by exactly 65535 steps (its maximal period)
        for (i in 0 until 65535) {
            n1.next()
        }
        // Verify n1's state has looped back and is identical to n2
        for (i in 0 until 100) {
            assertEquals(n2.next(), n1.next())
        }
    }
}
