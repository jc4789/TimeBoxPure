package com.example.timeboxvibe.engine.core

/**
 * Monotonic frame counter that replaces wall-clock `getEpochMillis() % N` for
 * visual ornament phase math. The render thread ticks this once per frame; every
 * rotating layer reads `phase(periodFrames)` or `rotation(degreesPerFrame)` from it.
 *
 * Determinism: `frameCount` increases monotonically. Phase wraps are bounded to
 * the caller's period (so the rune band's 60s cycle and the yin-yang's 4s cycle
 * never sync up). The counter survives scene switches but resets on app cold start.
 */
object FrameClock {
    private var frameCount: Long = 0L

    fun tick() {
        frameCount++
    }

    fun reset() {
        frameCount = 0L
    }

    fun frame(): Long = frameCount

    /**
     * Phase in [0, 1) for a given cycle period (in frames).
     * Example: `phase(3600)` returns a value in [0, 1) that completes one full
     * cycle every 3600 frames (60 seconds at 60Hz).
     */
    fun phase(periodFrames: Long): Float {
        if (periodFrames <= 0L) return 0f
        return ((frameCount % periodFrames).toFloat() / periodFrames.toFloat())
    }

    /**
     * Continuous rotation in [0, 360) at a fixed rate of degrees per frame.
     * Frame-rate independent when paired with a stable frame rate.
     */
    fun rotation(degreesPerFrame: Float): Float {
        val raw = (frameCount.toDouble() * degreesPerFrame) % 360.0
        return raw.toFloat()
    }

    /**
     * Seconds since the clock started, at the given assumed frame rate.
     * Used for time-driven effects (sin/cos via `Wave`).
     */
    fun seconds(frameRate: Float): Float {
        if (frameRate <= 0f) return 0f
        return frameCount.toFloat() / frameRate
    }

    /**
     * Total accumulated time delta (in seconds) at the given frame rate, since
     * the last call. Useful for one-shot triggers ("do X every N seconds").
     */
    fun elapsed(frameRate: Float): Float = seconds(frameRate)
}
