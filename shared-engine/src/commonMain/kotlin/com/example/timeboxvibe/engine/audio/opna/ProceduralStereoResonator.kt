package com.example.timeboxvibe.engine.audio.opna

/**
 * Small procedural room resonator for dry FM/SSG mixes.
 *
 * Two unequal cross-fed delays avoid a metallic single echo while keeping the
 * implementation deterministic, allocation-free during rendering, and free of
 * impulse-response or sample assets.
 */
internal class ProceduralStereoResonator(sampleRate: Int) {
    private val leftDelay = FloatArray(delayFrames(sampleRate, LEFT_DELAY_MILLISECONDS))
    private val rightDelay = FloatArray(delayFrames(sampleRate, RIGHT_DELAY_MILLISECONDS))
    private var leftPosition = 0
    private var rightPosition = 0
    private var dampedLeft = 0f
    private var dampedRight = 0f

    fun process(stereoBuffer: FloatArray, frames: Int) {
        var frame = 0
        while (frame < frames) {
            val sampleIndex = frame * STEREO_CHANNELS
            val dryLeft = stereoBuffer[sampleIndex]
            val dryRight = stereoBuffer[sampleIndex + 1]
            val reflectedLeft = leftDelay[leftPosition]
            val reflectedRight = rightDelay[rightPosition]
            dampedLeft += (reflectedLeft - dampedLeft) * DAMPING_COEFFICIENT
            dampedRight += (reflectedRight - dampedRight) * DAMPING_COEFFICIENT

            leftDelay[leftPosition] = dryLeft + dampedRight * CROSS_FEEDBACK
            rightDelay[rightPosition] = dryRight + dampedLeft * CROSS_FEEDBACK
            stereoBuffer[sampleIndex] = dryLeft + dampedLeft * WET_GAIN
            stereoBuffer[sampleIndex + 1] = dryRight + dampedRight * WET_GAIN

            leftPosition++
            if (leftPosition == leftDelay.size) leftPosition = 0
            rightPosition++
            if (rightPosition == rightDelay.size) rightPosition = 0
            frame++
        }
    }

    fun reset() {
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        leftPosition = 0
        rightPosition = 0
        dampedLeft = 0f
        dampedRight = 0f
    }

    private companion object {
        const val STEREO_CHANNELS = 2
        const val LEFT_DELAY_MILLISECONDS = 23
        const val RIGHT_DELAY_MILLISECONDS = 31
        const val CROSS_FEEDBACK = 0.38f
        const val WET_GAIN = 0.30f
        const val DAMPING_COEFFICIENT = 0.35f

        fun delayFrames(sampleRate: Int, milliseconds: Int): Int =
            (sampleRate.coerceAtLeast(1) * milliseconds / 1_000).coerceAtLeast(1)
    }
}
