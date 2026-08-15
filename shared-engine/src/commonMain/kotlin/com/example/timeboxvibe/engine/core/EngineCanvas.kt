package com.example.timeboxvibe.engine.core

import kotlin.math.abs

const val CANONICAL_UI_UNIT = 16

object DisplayScalePolicy {
    const val MIN_SAFE_LOGICAL_WIDTH = 320f
    const val MAX_SAFE_LOGICAL_WIDTH = 1200f
    const val MIN_SCALE = 1

    private const val MIN_TRUSTED_DENSITY = 0.5f
    private const val MAX_TRUSTED_DENSITY = 8f
    private const val FAKE_DENSITY = 1f
    private const val FAKE_DENSITY_EPSILON = 0.01f
    private const val PHYSICAL_SCALE_PER_DENSITY = 2f
    private const val FALLBACK_MIN_SPAN_CELLS = 20

    fun deriveScale(
        physicalWidth: Float,
        physicalHeight: Float,
        platformDensity: Float
    ): Int {
        if (!physicalWidth.isFinite() || !physicalHeight.isFinite() || physicalWidth <= 0f || physicalHeight <= 0f) {
            return MIN_SCALE
        }

        var scale = if (isTrustedDensity(platformDensity)) {
            (platformDensity * PHYSICAL_SCALE_PER_DENSITY).toInt().coerceAtLeast(MIN_SCALE)
        } else {
            val shortSpan = minOf(physicalWidth, physicalHeight)
            val fallbackLogicalSpan = (CANONICAL_UI_UNIT * FALLBACK_MIN_SPAN_CELLS).toFloat()
            (shortSpan / fallbackLogicalSpan).toInt().coerceAtLeast(MIN_SCALE)
        }

        while (scale > MIN_SCALE && physicalWidth / scale < MIN_SAFE_LOGICAL_WIDTH) {
            scale--
        }
        while (physicalWidth / scale > MAX_SAFE_LOGICAL_WIDTH) {
            scale++
        }
        return scale
    }

    private fun isTrustedDensity(platformDensity: Float): Boolean {
        return platformDensity.isFinite() &&
            platformDensity > MIN_TRUSTED_DENSITY &&
            platformDensity < MAX_TRUSTED_DENSITY &&
            abs(platformDensity - FAKE_DENSITY) > FAKE_DENSITY_EPSILON
    }
}

/**
 * Text-local raster transform. [CANONICAL_UI_UNIT] remains the 16x16 source cell;
 * only its final presentation is capped so high display scales do not enlarge each
 * source bit beyond [MAX_BASE_PHYSICAL_PIXEL_SCALE] physical pixels.
 */
internal object TextRasterScale {
    private const val MIN_SEMANTIC_SCALE = 1
    const val MAX_BASE_PHYSICAL_PIXEL_SCALE = 2

    var presentationScale: Int = DisplayScalePolicy.MIN_SCALE
        private set
    var basePhysicalPixelScale: Int = DisplayScalePolicy.MIN_SCALE
        private set

    fun configure(displayScale: Int) {
        presentationScale = displayScale.coerceAtLeast(DisplayScalePolicy.MIN_SCALE)
        basePhysicalPixelScale = minOf(presentationScale, MAX_BASE_PHYSICAL_PIXEL_SCALE)
    }

    fun physicalPixelSize(semanticScale: Int): Int {
        return basePhysicalPixelScale * semanticScale.coerceAtLeast(MIN_SEMANTIC_SCALE)
    }

    fun logicalPixelSize(semanticScale: Int): Float {
        return physicalPixelSize(semanticScale).toFloat() / presentationScale
    }

    fun logicalCellSize(semanticScale: Int): Float {
        return CANONICAL_UI_UNIT * logicalPixelSize(semanticScale)
    }

    fun physicalSourceSize(sourcePixels: Int, semanticScale: Int = MIN_SEMANTIC_SCALE): Int {
        return sourcePixels * physicalPixelSize(semanticScale)
    }
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
    val density: Float // Screen density, 1.0 means 1 dp = 1 px
    /** Validated positive integer used by the platform's outer presentation transform. */
    val presentationScale: Int

    /** Sets the opacity applied when the platform resolves palette indices to native colors. */
    fun setDrawAlpha(alphaByte: Int) {}
    fun clear(colorIndex: Int)
    fun setPixel(x: Float, y: Float, colorIndex: Int)
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, colorIndex: Int, strokeWidth: Float = 1f)
    fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int)
    /** Draws an already integer-snapped rectangle in final presentation pixels. */
    fun drawPhysicalRect(x: Int, y: Int, w: Int, h: Int, colorIndex: Int)
    fun drawCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float = 1f, dashed: Boolean = false)
    fun fillRectDither(x0: Float, y0: Float, x1: Float, y1: Float, primaryIndex: Int, secondaryIndex: Int, pattern: SoftDitherPattern)
}
