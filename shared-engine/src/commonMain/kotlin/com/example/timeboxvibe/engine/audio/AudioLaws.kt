package com.example.timeboxvibe.engine.audio

import kotlin.math.pow

internal object AudioLaws {
    const val FM_CHANNELS: Int = 6
    const val SSG_CHANNELS: Int = 3
    const val FM_OPERATORS: Int = 4
    const val SAMPLE_RATE: Int = 44100
    const val SSG_GAIN_DB: Float = -18f

    fun tlToAmplitude(tl: Int): Float = (127 - tl.coerceIn(0, 127)) / 127f

    fun detunePhaseOffset(detune: Int): Float = detune.coerceIn(0, 7) * 0.01f

    fun feedbackShift(feedback: Int): Float = feedback.coerceIn(0, 7) * 0.5f

    fun fnumBlockToFreq(block: Int, fnum: Int): Float =
        440f * 2f.pow(block.coerceIn(0, 7) + fnum.coerceIn(0, 2047) / 1024f - 9f)
}
