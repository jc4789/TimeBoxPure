package com.example.timeboxvibe.engine.audio

import kotlin.math.pow

internal object AudioLaws {
    const val FM_CHANNELS: Int = 6
    const val SSG_CHANNELS: Int = 3
    const val FM_OPERATORS: Int = 4
    const val SAMPLE_RATE: Int = 48000
    const val SSG_GAIN_DB: Float = -18f

    const val OPNA_OUTPUT_GAIN: Float = 1.0f

    fun tlToAmplitude(tl: Int): Float =
        10f.pow(-tl.coerceIn(0, 127) * 0.75f / 20f)

    fun feedbackShift(feedback: Int): Int {
        val fb = feedback.coerceIn(0, 7)
        val fbtab = intArrayOf(31, 7, 6, 5, 4, 3, 2, 1)
        return fbtab[fb]
    }

    fun detunePhaseMultiplier(detune: Int): Float {
        val dt = detune.coerceIn(0, 7)
        val dttab = floatArrayOf(1.0f, 1.0004f, 1.0008f, 1.0015f, 0.9995f, 0.999f, 0.998f, 0.997f)
        return dttab[dt]
    }

    fun detunePhaseMultiplierD(detune: Int): Double {
        val dt = detune.coerceIn(0, 7)
        val dttab = doubleArrayOf(1.0, 1.0004, 1.0008, 1.0015, 0.9995, 0.999, 0.998, 0.997)
        return dttab[dt]
    }

    fun fnumBlockToFreq(block: Int, fnum: Int): Float =
        440f * 2f.pow(block.coerceIn(0, 7) + fnum.coerceIn(0, 2047) / 1024f - 9f)

    fun panToGains(pan: Int): Pair<Float, Float> {
        return when (pan) {
            1 -> Pair(1f, 0f)
            2 -> Pair(0f, 1f)
            else -> Pair(0.707f, 0.707f)
        }
    }
}
