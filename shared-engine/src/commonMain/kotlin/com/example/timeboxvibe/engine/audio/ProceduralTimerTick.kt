package com.example.timeboxvibe.engine.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Platform-free timer sonification; the caller owns the one-shot output buffer. */
object ProceduralTimerTick {
    const val SAMPLE_RATE = 44_100
    const val DURATION_MILLISECONDS = 35

    fun fill(output: FloatArray, userGain: Float) {
        val expectedSamples = SAMPLE_RATE * DURATION_MILLISECONDS / 1_000
        require(output.size == expectedSamples) { "Timer tick buffer has the wrong duration" }
        var sampleIndex = 0
        while (sampleIndex < output.size) {
            val timeSeconds = sampleIndex.toFloat() / SAMPLE_RATE
            val frequency = START_FREQUENCY_HZ * exp(-FREQUENCY_DECAY * timeSeconds)
            val phase = 2.0 * PI * frequency * timeSeconds
            var sample = if (sin(phase) > 0.0) SQUARE_LEVEL else -SQUARE_LEVEL
            val ageMilliseconds = sampleIndex * 1_000 / SAMPLE_RATE
            val envelope = if (ageMilliseconds < ATTACK_MILLISECONDS) {
                ageMilliseconds / ATTACK_MILLISECONDS.toFloat()
            } else {
                val decayPosition = (ageMilliseconds - ATTACK_MILLISECONDS) /
                    (DURATION_MILLISECONDS - ATTACK_MILLISECONDS).toFloat()
                exp(-ENVELOPE_DECAY * decayPosition).toFloat()
            }
            sample *= envelope * OUTPUT_LEVEL * userGain.coerceAtLeast(0f)
            output[sampleIndex] = sample.coerceIn(-1f, 1f)
            sampleIndex++
        }
    }

    private const val START_FREQUENCY_HZ = 300.0
    private const val FREQUENCY_DECAY = 50.0
    private const val SQUARE_LEVEL = 0.8f
    private const val ATTACK_MILLISECONDS = 2
    private const val ENVELOPE_DECAY = 8.0
    private const val OUTPUT_LEVEL = 0.15f
}
