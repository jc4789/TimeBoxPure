package com.example.timeboxvibe.engine.core

/**
 * Owns and ticks the 6 Wave oscillators + 1 IkChain2D + Perlin rune drift
 * for the magic circle. Created once per `ActiveTimerScene.onEnter()`,
 * `update(dt)` called once per frame from the scene's `update(dt)`.
 *
 * Reads `VisualsStateHolder.demosceneEffectsEnabled` to allow the user to
 * disable all demoscene effects via the Settings scene.
 *
 * This class is the "demoscene manager" — single-purpose, separated from the
 * geometry renderer (`NestedTimeboxInstrumentRenderer`) and the scene
 * orchestration (`ActiveTimerScene`). The renderer reads `Wave.value()` and
 * `IkChain2D.points[i]` at render time, but does not own them.
 */
class MagicCircleDemoscene {

    // ---- 6 Wave oscillators (per the locked plan; no dot pulse) ----

    /** Rune band kanji sway: each glyph y-offset = Wave.value() with per-idx phase stagger. */
    val runeSway = Wave().apply {
        frequency = 0.3f
        amplitude = 0.5f
    }

    /** Outer pentagram breath: effective radius = baseR * (1 + value() * 0.025). */
    val pentaBreath = Wave().apply {
        frequency = 0.5f
        amplitude = 0.025f
    }

    /** Outer 60-bead ring heartbeat: strokeWidth = baseSW * (1 + valueNorm() * 0.4). */
    val outerHeartbeat = Wave().apply {
        frequency = 0.7f
        amplitude = 0.4f
    }

    /** Inner 48-bead ring heartbeat (π offset for "double-beat" feel). */
    val innerHeartbeat = Wave().apply {
        frequency = 0.7f
        amplitude = 0.4f
        phaseOffset = 0.5f
    }

    /** Yin-yang core wobble: scale = 1 + value() * 0.04. */
    val coreWobble = Wave().apply {
        frequency = 1.2f
        amplitude = 0.04f
    }

    /** Sector kanji swing: each of 5 sector kanji radius = baseR * (1 + value() * 0.03). */
    val sectorSwing = Wave().apply {
        frequency = 0.6f
        amplitude = 0.03f
    }

    // ---- FABRIK chain (yin-yang comet trail) ----

    /** 6-link FABRIK chain. Extends from the core position outward in the
     *  direction opposite the rotation. Head anchor is 1.5x coreR (just
     *  outside the core's edge); tail anchor is 4x coreR, 60° behind. Chain
     *  length ~100px (5 segments of 20px). The chain is drawn AFTER the core
     *  so the trail is visible on top of the core. */
    val trail: IkChain2D = IkChain2D(segmentLength = 20f, pointCount = TRAIL_LINKS)

    /**
     * Per-link fade parameters: [alpha, size].
     * Index 0 is the head (just outside the core), last is the tail tip.
     */
    val trailAlpha: IntArray = intArrayOf(0xFF, 0xC0, 0x90, 0x60, 0x40, 0x20)
    val trailSize: FloatArray = floatArrayOf(3f, 2.5f, 2f, 1.5f, 1f, 1f)

    /**
     * Tick all 6 Waves by `dt` seconds. Called from `ActiveTimerScene.update(dt)`.
     * No-op when demoscene effects are disabled in settings (the Wave phases
     * freeze but the existing values remain — so the magic circle doesn't
     * snap when the user re-enables demoscene mid-session).
     */
    fun update(dt: Float) {
        if (!VisualsStateHolder.demosceneEffectsEnabled) return
        runeSway.update(dt)
        pentaBreath.update(dt)
        outerHeartbeat.update(dt)
        innerHeartbeat.update(dt)
        coreWobble.update(dt)
        sectorSwing.update(dt)
    }

    /**
     * Solve the FABRIK chain for the comet trail. The "start" is the lagged
     * position behind the yin-yang, and the "end" is the current core position.
     * Caller is responsible for providing these from the renderer's geometry
     * state (computed from `FrameClock.rotation`).
     */
    fun solveTrail(startX: Float, startY: Float, endX: Float, endY: Float) {
        if (!VisualsStateHolder.demosceneEffectsEnabled) return
        trail.solve(startX, startY, endX, endY, iterations = TRAIL_ITERATIONS)
    }

    /**
     * Reset all Waves and the FABRIK chain. Called from `ActiveTimerScene.onEnter()`
     * to keep the animation deterministic across scene switches.
     */
    fun reset() {
        runeSway.reset()
        pentaBreath.reset()
        outerHeartbeat.reset()
        innerHeartbeat.reset()
        coreWobble.reset()
        sectorSwing.reset()
        trail.reset()
    }

    /**
     * Per-glyph rune drift: Perlin-driven angular perturbation for the rune band.
     * Returns an angle offset in degrees in roughly [-0.5, 0.5]. Caller passes
     * the glyph index to stagger the noise.
     */
    fun runeDriftAngleOffset(glyphIndex: Int, timeSeconds: Float): Float {
        if (!VisualsStateHolder.demosceneEffectsEnabled) return 0f
        return PerlinNoise.noise1D(timeSeconds * 0.3f + glyphIndex * 0.1f) * 0.5f
    }

    /**
     * Background nebula sample: 2-octave fbm Perlin at the given screen point.
     * Returns roughly [-1, 1]. Caller maps this to a palette index between
     * `BG` and `BG_ALT`. When nebula is disabled, returns 0 (caller uses BG).
     */
    fun nebulaSample(x: Float, y: Float, timeSeconds: Float): Float {
        if (!VisualsStateHolder.backgroundNebulaEnabled) return 0f
        return PerlinNoise.fbm(x * 0.005f + timeSeconds * 0.02f, y * 0.005f, octaves = 2)
    }

    companion object {
        const val TRAIL_LINKS: Int = 6
        const val TRAIL_ITERATIONS: Int = 6
    }
}
