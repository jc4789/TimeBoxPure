package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.math.pow

internal class OpnaMixer(sampleRate: Int) {
    private val ssgGainLinear: Float = 10f.pow(AudioLaws.SSG_GAIN_DB / 20f)
    private val fmGainLinear: Float = 1f
    private val rhythmGainLinear: Float = 1f

    val ssgGain: Float get() = ssgGainLinear
    val fmGain: Float get() = fmGainLinear
    val rhythmGain: Float get() = rhythmGainLinear
}
