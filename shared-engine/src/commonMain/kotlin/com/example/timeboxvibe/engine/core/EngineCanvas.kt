package com.example.timeboxvibe.engine.core

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
    val density: Float // Screen density, 1.0 means 1 dp = 1 px

    /** Sets the opacity applied when the platform resolves palette indices to native colors. */
    fun setDrawAlpha(alphaByte: Int) {}
    fun clear(colorIndex: Int)
    fun setPixel(x: Float, y: Float, colorIndex: Int)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, colorIndex: Int, strokeWidth: Float = 1f)
    fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int)
    fun drawCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float = 1f, dashed: Boolean = false)
    fun fillRectDither(x0: Float, y0: Float, x1: Float, y1: Float, primaryIndex: Int, secondaryIndex: Int, pattern: SoftDitherPattern)
}
