package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.EqType
import com.example.timeboxvibe.engine.SongEqBand
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MasterPeakEqTest {
    @Test
    fun emptyEqIsTransparentAndPeakEqIsDeterministic() {
        val input = FloatArray(256)
        input[0] = 0.5f

        val transparent = input.copyOf()
        MasterPeakEq(48000).processMono(transparent, transparent.size)
        assertTrue(input.contentEquals(transparent))

        val bands = listOf(SongEqBand(EqType.PEAK, 4700f, -2f, 0.8f))
        val outputA = input.copyOf()
        val outputB = input.copyOf()
        val eqA = MasterPeakEq(48000)
        val eqB = MasterPeakEq(48000)
        eqA.configure(bands)
        eqB.configure(bands)
        eqA.processMono(outputA, outputA.size)
        eqB.processMono(outputB, outputB.size)

        assertFalse(input.contentEquals(outputA))
        assertTrue(outputA.contentEquals(outputB))
    }
}
