package com.example.timeboxvibe.engine.core

const val CANONICAL_UI_UNIT = 16

/**
 * Maps the 16x16 UI source grid onto the real indexed framebuffer.
 *
 * This is UI-only. Procedural graphics can bypass it and address framebuffer
 * pixels directly. The integer pixel block follows `How to scale.txt`: choose
 * the largest whole-number block that preserves the minimum usable cell grid.
 */
object UiRasterGrid {
    private const val PORTRAIT_MIN_COLUMNS = 22
    private const val LANDSCAPE_MIN_COLUMNS = 40
    private const val MIN_ROWS = 40

    var pixelBlock = 1
        private set
    var logicalWidth = 1f
        private set
    var logicalHeight = 1f
        private set

    fun configure(outputWidth: Int, outputHeight: Int) {
        val safeWidth = outputWidth.coerceAtLeast(1)
        val safeHeight = outputHeight.coerceAtLeast(1)
        val minimumColumns = if (safeHeight > safeWidth) {
            PORTRAIT_MIN_COLUMNS
        } else {
            LANDSCAPE_MIN_COLUMNS
        }
        val widthBlock = safeWidth / (minimumColumns * CANONICAL_UI_UNIT)
        val heightBlock = safeHeight / (MIN_ROWS * CANONICAL_UI_UNIT)
        pixelBlock = maxOf(1, minOf(widthBlock, heightBlock))
        logicalWidth = (safeWidth / pixelBlock).toFloat()
        logicalHeight = (safeHeight / pixelBlock).toFloat()
    }

    fun outputX(logicalX: Float): Float = logicalX * pixelBlock

    fun outputY(logicalY: Float): Float = logicalY * pixelBlock

    fun logicalX(outputX: Int): Int = outputX / pixelBlock

    fun logicalY(outputY: Int): Int = outputY / pixelBlock

    fun logicalX(outputX: Float): Float = outputX / pixelBlock

    fun logicalY(outputY: Float): Float = outputY / pixelBlock
}

/**
 * Platform-agnostic interface for rendering geometric primitives and retro graphics.
 * This represents the "Disciplinary Purity" boundary; no Android imports are allowed here.
 */
interface EngineCanvas {
    companion object {
        const val COLOR_TRANSPARENT = -1
    }
    
    val width: Float
    val height: Float

    /** Indexed output has no alpha channel; zero disables drawing, nonzero enables it. */
    fun setDrawAlpha(alphaByte: Int) {}
    fun clear(colorIndex: Int)
    fun setPixel(x: Float, y: Float, colorIndex: Int)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, colorIndex: Int, strokeWidth: Float = 1f)
    fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int)
    fun drawCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float = 1f, dashed: Boolean = false)
    fun fillRectDither(x0: Float, y0: Float, x1: Float, y1: Float, primaryIndex: Int, secondaryIndex: Int, pattern: SoftDitherPattern)
}
