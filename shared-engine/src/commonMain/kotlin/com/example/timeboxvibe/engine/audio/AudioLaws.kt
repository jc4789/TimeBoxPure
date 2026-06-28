package com.example.timeboxvibe.engine.audio

import kotlin.math.pow

internal object AudioLaws {
    const val FM_CHANNELS: Int = 6
    const val SSG_CHANNELS: Int = 3
    const val FM_OPERATORS: Int = 4
    const val SAMPLE_RATE: Int = 44100
    const val SSG_GAIN_DB: Float = -18f

    fun tlToAmplitude(tl: Int): Float =
        10f.pow(-tl.coerceIn(0, 127) * 0.75f / 20f)

    fun feedbackShift(feedback: Int): Float =
        if (feedback == 0) 0f else 0.03f * feedback.coerceIn(1, 7)

    fun detunePhaseMultiplier(detune: Int): Float =
        1.0f + detune.coerceIn(0, 7) * 0.0001f

    fun fnumBlockToFreq(block: Int, fnum: Int): Float =
        440f * 2f.pow(block.coerceIn(0, 7) + fnum.coerceIn(0, 2047) / 1024f - 9f)
}
