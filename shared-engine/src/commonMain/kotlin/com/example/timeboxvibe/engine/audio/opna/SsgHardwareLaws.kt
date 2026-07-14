package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.pow

/** YM2608 SSG register widths and clock dividers at the standard 8 MHz master clock. */
internal object SsgHardwareLaws {
    const val CHANNEL_COUNT = 3
    const val MIN_TONE_PERIOD = 1
    const val MAX_TONE_PERIOD = 0x0FFF
    const val MIN_NOISE_PERIOD = 1
    const val MAX_NOISE_PERIOD = 0x1F
    const val MIN_ENVELOPE_PERIOD = 1
    const val MAX_ENVELOPE_PERIOD = 0xFFFF

    const val TONE_FREQUENCY_DIVIDER = 64
    const val TONE_TOGGLE_DIVIDER = TONE_FREQUENCY_DIVIDER / 2
    const val NOISE_CLOCK_DIVIDER = 64
    const val ENVELOPE_STEP_DIVIDER = 1_024

    fun nearestTonePeriod(frequencyHz: Double): Int {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return MAX_TONE_PERIOD
        val raw = OpnPitch.MASTER_CLOCK_HZ.toDouble() /
            (TONE_FREQUENCY_DIVIDER.toDouble() * frequencyHz)
        return (raw + 0.5).toInt().coerceIn(MIN_TONE_PERIOD, MAX_TONE_PERIOD)
    }

    fun toneFrequency(period: Int): Double =
        OpnPitch.MASTER_CLOCK_HZ.toDouble() /
            (TONE_FREQUENCY_DIVIDER.toDouble() * period.coerceIn(MIN_TONE_PERIOD, MAX_TONE_PERIOD))
}

/** YM2608 fixed-volume codes select odd steps of its logarithmic 5-bit SSG DAC. */
internal object SsgLevelLaw {
    private const val DAC_MAX_LEVEL = 31
    private const val DAC_STEP_DB = 0.75
    private const val FIXED_LEVEL_COUNT = 16
    private const val ENVELOPE_LEVEL_COUNT = DAC_MAX_LEVEL + 1
    private const val FIXED_LEVEL_STRIDE = 2
    private const val FIXED_LEVEL_BIAS = 1

    private val fixedAmplitude = FloatArray(FIXED_LEVEL_COUNT) { level ->
        if (level == 0) {
            0f
        } else {
            val dacLevel = level * FIXED_LEVEL_STRIDE + FIXED_LEVEL_BIAS
            amplitudeForDacLevel(dacLevel)
        }
    }

    private val envelopeAmplitude = FloatArray(ENVELOPE_LEVEL_COUNT) { level ->
        if (level == 0) 0f else amplitudeForDacLevel(level)
    }

    fun fixedAmplitude(level: Int): Float = fixedAmplitude[level.coerceIn(0, FIXED_LEVEL_COUNT - 1)]

    fun envelopeAmplitude(level: Int): Float =
        envelopeAmplitude[level.coerceIn(0, ENVELOPE_LEVEL_COUNT - 1)]

    private fun amplitudeForDacLevel(level: Int): Float =
        10.0.pow((level - DAC_MAX_LEVEL) * DAC_STEP_DB / 20.0).toFloat()
}
