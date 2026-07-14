package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertEquals

/** Independent literal register vectors decoded from the four source-owned LLS voices. */
class LlsPatchRegisterTest {
    @Test
    fun everySourceDerivedVoiceIsProtectedAtEveryOwnedRegisterField() {
        assertPatch(LlsPatches.At54, 4, 4, arrayOf(
            op(7, 8, 17, 0, 31, 0, 0, 0, 0, 0),
            op(7, 4, 0, 0, 31, 1, 16, 0, 2, 8),
            op(3, 4, 20, 0, 31, 0, 0, 0, 0, 0),
            op(3, 2, 0, 0, 31, 1, 16, 0, 2, 8)
        ))
        assertPatch(LlsPatches.At74, 0, 4, arrayOf(
            op(6, 6, 28, 3, 31, 0, 7, 7, 2, 9),
            op(6, 5, 58, 3, 31, 0, 6, 6, 1, 9),
            op(6, 0, 22, 2, 31, 0, 9, 6, 1, 9),
            op(6, 1, 0, 2, 31, 0, 6, 8, 15, 9)
        ))
        assertPatch(LlsPatches.At99, 5, 7, arrayOf(
            op(0, 1, 29, 0, 31, 0, 0, 0, 0, 0),
            op(0, 1, 0, 0, 31, 0, 14, 3, 3, 15),
            op(0, 1, 0, 0, 31, 0, 14, 3, 3, 15),
            op(0, 1, 0, 0, 31, 0, 14, 3, 3, 15)
        ))
        assertPatch(LlsPatches.At181, 4, 7, arrayOf(
            op(7, 8, 27, 0, 31, 0, 0, 0, 0, 0),
            op(7, 4, 0, 0, 31, 0, 16, 0, 2, 8),
            op(3, 8, 30, 0, 31, 0, 0, 0, 0, 0),
            op(3, 4, 0, 0, 31, 0, 16, 0, 2, 8)
        ))
    }

    private fun assertPatch(patch: FmPatch, algorithm: Int, feedback: Int, expected: Array<IntArray>) {
        assertEquals(algorithm, patch.algorithm, "ALG")
        assertEquals(feedback, patch.feedback, "FB")
        assertEquals(0.38f, patch.totalLevel, "channel scale")
        assertEquals(0, patch.pms, "patch PMS")
        assertEquals(0, patch.ams, "patch AMS")
        assertEquals(0, patch.pan, "patch pan")
        val operators = arrayOf(patch.op0, patch.op1, patch.op2, patch.op3)
        var operator = 0
        while (operator < operators.size) {
            val actual = operators[operator]
            val values = expected[operator]
            assertEquals(values[0], actual.detune, "op$operator DT")
            assertEquals(values[1], actual.mul, "op$operator MUL")
            assertEquals(values[2], actual.tl, "op$operator TL")
            assertEquals(values[3], actual.ks, "op$operator KS")
            assertEquals(values[4], actual.ar, "op$operator AR")
            assertEquals(values[5], actual.ams, "op$operator AM")
            assertEquals(values[6], actual.dr, "op$operator DR")
            assertEquals(values[7], actual.sr, "op$operator SR")
            assertEquals(values[8], actual.sl, "op$operator SL")
            assertEquals(values[9], actual.rr, "op$operator RR")
            assertEquals(0, actual.ssgEg, "op$operator SSG-EG")
            assertEquals(EgMode.OPN_RATE, actual.egMode, "op$operator envelope mode")
            operator++
        }
    }

    private fun op(
        dt: Int, mul: Int, tl: Int, ks: Int, ar: Int,
        am: Int, dr: Int, sr: Int, sl: Int, rr: Int
    ): IntArray = intArrayOf(dt, mul, tl, ks, ar, am, dr, sr, sl, rr)
}
