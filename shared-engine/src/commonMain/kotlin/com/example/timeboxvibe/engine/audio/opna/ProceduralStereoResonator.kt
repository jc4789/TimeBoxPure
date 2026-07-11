package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.PI
import kotlin.math.exp

/**
 * Small procedural room resonator for dry FM/SSG mixes.
 *
 * Two unequal cross-fed delays avoid a metallic single echo while keeping the
 * implementation deterministic, allocation-free during rendering, and free of
 * impulse-response or sample assets.
 */
internal class ProceduralStereoResonator(sampleRate: Int) {
    private val earlyLeftDelay = FloatArray(delayFrames(sampleRate, EARLY_LEFT_MILLISECONDS))
    private val earlyRightDelay = FloatArray(delayFrames(sampleRate, EARLY_RIGHT_MILLISECONDS))
    private val leftDelay = FloatArray(delayFrames(sampleRate, LEFT_DELAY_MILLISECONDS))
    private val rightDelay = FloatArray(delayFrames(sampleRate, RIGHT_DELAY_MILLISECONDS))
    private var earlyLeftPosition = 0
    private var earlyRightPosition = 0
    private var leftPosition = 0
    private var rightPosition = 0
    private var dampedLeft = 0f
    private var dampedRight = 0f
    private var inputLowPassLeft = 0f
    private var inputLowPassRight = 0f
    private val inputHighPassCoefficient = (
        1.0 - exp(-2.0 * PI * INPUT_HIGH_PASS_HZ / sampleRate.coerceAtLeast(1).toDouble())
        ).toFloat()

    fun process(stereoBuffer: FloatArray, frames: Int) {
        var frame = 0
        while (frame < frames) {
            val sampleIndex = frame * STEREO_CHANNELS
            val dryLeft = stereoBuffer[sampleIndex]
            val dryRight = stereoBuffer[sampleIndex + 1]
            inputLowPassLeft += (dryLeft - inputLowPassLeft) * inputHighPassCoefficient
            inputLowPassRight += (dryRight - inputLowPassRight) * inputHighPassCoefficient
            val roomInputLeft = dryLeft - inputLowPassLeft
            val roomInputRight = dryRight - inputLowPassRight
            val earlyLeft = earlyLeftDelay[earlyLeftPosition]
            val earlyRight = earlyRightDelay[earlyRightPosition]
            val reflectedLeft = leftDelay[leftPosition]
            val reflectedRight = rightDelay[rightPosition]
            dampedLeft += (reflectedLeft - dampedLeft) * DAMPING_COEFFICIENT
            dampedRight += (reflectedRight - dampedRight) * DAMPING_COEFFICIENT

            earlyLeftDelay[earlyLeftPosition] = roomInputRight
            earlyRightDelay[earlyRightPosition] = roomInputLeft
            leftDelay[leftPosition] = roomInputLeft + earlyRight * EARLY_TO_LATE_GAIN + dampedRight * CROSS_FEEDBACK
            rightDelay[rightPosition] = roomInputRight + earlyLeft * EARLY_TO_LATE_GAIN + dampedLeft * CROSS_FEEDBACK
            stereoBuffer[sampleIndex] = dryLeft + earlyLeft * EARLY_WET_GAIN + dampedLeft * LATE_WET_GAIN
            stereoBuffer[sampleIndex + 1] = dryRight + earlyRight * EARLY_WET_GAIN + dampedRight * LATE_WET_GAIN

            earlyLeftPosition++
            if (earlyLeftPosition == earlyLeftDelay.size) earlyLeftPosition = 0
            earlyRightPosition++
            if (earlyRightPosition == earlyRightDelay.size) earlyRightPosition = 0
            leftPosition++
            if (leftPosition == leftDelay.size) leftPosition = 0
            rightPosition++
            if (rightPosition == rightDelay.size) rightPosition = 0
            frame++
        }
    }

    fun reset() {
        earlyLeftDelay.fill(0f)
        earlyRightDelay.fill(0f)
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        earlyLeftPosition = 0
        earlyRightPosition = 0
        leftPosition = 0
        rightPosition = 0
        dampedLeft = 0f
        dampedRight = 0f
        inputLowPassLeft = 0f
        inputLowPassRight = 0f
    }

    private companion object {
        const val STEREO_CHANNELS = 2
        const val EARLY_LEFT_MILLISECONDS = 7
        const val EARLY_RIGHT_MILLISECONDS = 13
        const val LEFT_DELAY_MILLISECONDS = 41
        const val RIGHT_DELAY_MILLISECONDS = 59
        const val INPUT_HIGH_PASS_HZ = 110.0
        const val EARLY_WET_GAIN = 0.12f
        const val EARLY_TO_LATE_GAIN = 0.18f
        const val CROSS_FEEDBACK = 0.45f
        const val LATE_WET_GAIN = 0.18f
        const val DAMPING_COEFFICIENT = 0.22f

        fun delayFrames(sampleRate: Int, milliseconds: Int): Int =
            (sampleRate.coerceAtLeast(1) * milliseconds / 1_000).coerceAtLeast(1)
    }
}
