package com.example.timeboxvibe.engine.core

/**
 * Pure-Kotlin holder for user-toggleable visual settings. Read by the engine
 * (magic circle renderer, demoscene manager) and mutated by the Settings scene
 * UI. Lives in `commonMain` to keep the engine laws intact: no `kotlinx.coroutines`,
 * no platform dependencies, just a simple in-memory holder.
 *
 * Defaults: nebula OFF (productivity-focused), demoscene effects ON (the user
 * asked for them — that's the whole point of this pass).
 */
object VisualsStateHolder {
    /**
     * Perlin-driven background nebula on the play area.
     * When false: flat `BG` color. When true: 2-octave fbm modulates BG ↔ BG_ALT.
     * Default false — keep the timer UI calm unless the user opts in.
     */
    var backgroundNebulaEnabled: Boolean = false

    /**
     * Master switch for all 6 Wave oscillators + Perlin rune drift + FABRIK trail.
     * When false: the magic circle renders as the static 9-layer layout with
     * only the basic rotation (no breathing, no sway, no trail).
     * When true: all demoscene effects active.
     * Default true — the user asked for the demoscene flavor.
     */
    var demosceneEffectsEnabled: Boolean = true
}
