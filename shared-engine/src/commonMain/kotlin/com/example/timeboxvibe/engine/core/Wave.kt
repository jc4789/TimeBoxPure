package com.example.timeboxvibe.engine.core

/**
 * Single-channel sine oscillator for procedural animation. Pre-allocated per layer;
 * `update(dt)` is called once per frame from `SceneManager.update()` and `value()`
 * is read during render.
 *
 * Each `Wave` is a single class instance — no per-frame allocation. `value()` uses
 * `kotlin.math.sin` directly (the wave's argument wraps internally to avoid the
 * per-call `sin` cost blowup). For animating polar positions, use `valueNorm()` to
 * get a [0, 1] normalized oscillation.
 */
class Wave {
    var phase: Float = 0f
    var amplitude: Float = 1f
    var frequency: Float = 1f
    var baseValue: Float = 0f
    var phaseOffset: Float = 0f

    /**
     * Advance the wave's internal phase by `dt * frequency` seconds.
     * Call once per frame from the scene's `update(dt)`.
     */
    fun update(dt: Float) {
        phase += dt * frequency
        if (phase > TAU) phase -= TAU
        if (phase < 0f) phase += TAU
    }

    /**
     * Sample the wave at its current phase. Returns `baseValue + amplitude * sin(2π·(phase+offset))`.
     * Range: `[baseValue - amplitude, baseValue + amplitude]`.
     */
    fun value(): Float {
        return baseValue + amplitude * sinTau(phase + phaseOffset)
    }

    /**
     * Sample the wave normalized to [0, 1]. Useful for alpha/size modulation.
     */
    fun valueNorm(): Float {
        return 0.5f + 0.5f * sinTau(phase + phaseOffset)
    }

    /**
     * Reset to a known initial state. Call from the scene's `onEnter()` to keep
     * the animation deterministic across scene switches.
     */
    fun reset() {
        phase = 0f
    }

    private fun sinTau(t: Float): Float {
        return kotlin.math.sin(t * TAU)
    }

    companion object {
        private const val TAU = 6.2831853f
    }
}
