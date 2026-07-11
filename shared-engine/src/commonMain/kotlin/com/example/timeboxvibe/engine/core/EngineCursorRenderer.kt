package com.example.timeboxvibe.engine.core

/**
 * Blinking text caret for IMGUI text fields.
 *
 * Ownership:
 * - This widget owns blink phase only.
 * - The scene owns placement from display-derived field rects and integer [U] glyph cells.
 * - Color is a palette index 0..15 (4-bit on-screen); 12-bit RGB lives in Pc98GraphicsHardware.
 *
 * Geometry uses Int [U] only (canonical glyph cell). No orientation or fixed-resolution assumptions.
 */
class EngineCursorRenderer {
    companion object {
        /** Canonical ROM glyph cell size (Int). */
        const val U = 16
        /** Thin PC-98 caret width — U/8 micro-detail law for UI carets. */
        const val CURSOR_WIDTH = U / 8
        /** Full glyph-cell height at text scale 1. */
        const val CURSOR_HEIGHT = U
        /** Half-period of blink cycle (seconds on, then seconds off). */
        private const val BLINK_HALF_PERIOD_SEC = 0.5f
    }

    private var blinkAccumulator = 0f

    fun reset() {
        blinkAccumulator = 0f
    }

    /**
     * Advance blink phase with real frame [dt] from Scene.update.
     * Never assume a fixed 1/60 frame time.
     */
    fun update(dt: Float) {
        val period = BLINK_HALF_PERIOD_SEC * 2f
        blinkAccumulator += dt
        while (blinkAccumulator >= period) {
            blinkAccumulator -= period
        }
    }

    /**
     * Draw a solid caret block when focused and in the visible blink phase.
     *
     * @param x logical pixel origin (scene-computed from field + visible char cells)
     * @param y logical pixel origin (typically the glyph row Y)
     * @param width integer U-derived width (default [CURSOR_WIDTH])
     * @param height integer U-derived height (default [CURSOR_HEIGHT])
     * @param colorIndex palette index 0..15
     */
    fun draw(
        renderer: ScaledProceduralRenderer,
        isFocused: Boolean,
        x: Float,
        y: Float,
        width: Int = CURSOR_WIDTH,
        height: Int = CURSOR_HEIGHT,
        colorIndex: Int = PaletteIndices.HIGHLIGHT
    ) {
        if (!isFocused) return
        if (blinkAccumulator >= BLINK_HALF_PERIOD_SEC) return

        val w = width.toFloat()
        val h = height.toFloat()
        renderer.fillRectDither(
            x0 = x,
            y0 = y,
            x1 = x + w,
            y1 = y + h,
            primaryIndex = colorIndex,
            secondaryIndex = colorIndex,
            pattern = SoftDitherPattern.SOLID
        )
    }
}
