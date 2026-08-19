package com.example.timeboxvibe.engine.core

/** UI-independent integer graphics commands targeting one indexed framebuffer. */
interface IndexedGraphics {
    val framebuffer: IndexedFramebuffer

    fun clear(colorIndex: Int)
    fun pixel(x: Int, y: Int, colorIndex: Int)
    fun fillRect(x: Int, y: Int, width: Int, height: Int, colorIndex: Int)
    fun ditherRect(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        primaryIndex: Int,
        secondaryIndex: Int,
        pattern: SoftDitherPattern
    )
    fun glyph(
        glyphRows: IntArray,
        x: Int,
        y: Int,
        colorIndex: Int,
        clipX: Int = 0,
        clipY: Int = 0,
        clipWidth: Int = framebuffer.width,
        clipHeight: Int = framebuffer.height
    )
}

/** Deterministic allocation-free software rasterizer. */
class SoftwareGraphics(
    override val framebuffer: IndexedFramebuffer
) : IndexedGraphics {
    private val glyphRasterizer = GlyphRasterizer(framebuffer)

    override fun clear(colorIndex: Int) {
        framebuffer.clear(colorIndex)
    }

    override fun pixel(x: Int, y: Int, colorIndex: Int) {
        framebuffer.setColorIndex(x, y, colorIndex)
    }

    override fun fillRect(x: Int, y: Int, width: Int, height: Int, colorIndex: Int) {
        requirePaletteIndex(colorIndex)
        fillRectUnchecked(x, y, width, height, colorIndex)
    }

    override fun ditherRect(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        primaryIndex: Int,
        secondaryIndex: Int,
        pattern: SoftDitherPattern
    ) {
        requirePaletteIndex(primaryIndex)
        requirePaletteIndex(secondaryIndex)
        val left = minOf(x0, x1).coerceAtLeast(0)
        val top = minOf(y0, y1).coerceAtLeast(0)
        val right = maxOf(x0, x1).coerceAtMost(framebuffer.width)
        val bottom = maxOf(y0, y1).coerceAtMost(framebuffer.height)
        if (left >= right || top >= bottom) return
        if (primaryIndex == secondaryIndex || pattern == SoftDitherPattern.SOLID) {
            fillRectUnchecked(left, top, right - left, bottom - top, primaryIndex)
            return
        }
        val pixels = framebuffer.indices
        val stride = framebuffer.width
        val primary = primaryIndex.toByte()
        val secondary = secondaryIndex.toByte()
        var y = top
        while (y < bottom) {
            var x = left
            var offset = y * stride + left
            while (x < right) {
                pixels[offset] = if (usesPrimary(pattern, x, y)) primary else secondary
                x++
                offset++
            }
            y++
        }
    }

    override fun glyph(
        glyphRows: IntArray,
        x: Int,
        y: Int,
        colorIndex: Int,
        clipX: Int,
        clipY: Int,
        clipWidth: Int,
        clipHeight: Int
    ) {
        glyphRasterizer.draw(glyphRows, x, y, colorIndex, clipX, clipY, clipWidth, clipHeight)
    }

    private fun fillRectUnchecked(x: Int, y: Int, width: Int, height: Int, colorIndex: Int) {
        if (width <= 0 || height <= 0) return
        val left = x.coerceAtLeast(0)
        val top = y.coerceAtLeast(0)
        val right = (x + width).coerceAtMost(framebuffer.width)
        val bottom = (y + height).coerceAtMost(framebuffer.height)
        if (left >= right || top >= bottom) return
        val pixels = framebuffer.indices
        val color = colorIndex.toByte()
        val stride = framebuffer.width
        var row = top
        while (row < bottom) {
            val rowStart = row * stride + left
            val rowEnd = row * stride + right
            pixels.fill(color, rowStart, rowEnd)
            row++
        }
    }

    private fun usesPrimary(pattern: SoftDitherPattern, x: Int, y: Int): Boolean {
        return when (pattern) {
            SoftDitherPattern.SOLID -> true
            SoftDitherPattern.CHECKERBOARD -> ((x + y) and 1) == 0
            SoftDitherPattern.HORIZONTAL_STRIPES -> (y and 1) == 0
            SoftDitherPattern.VERTICAL_STRIPES -> (x and 1) == 0
            SoftDitherPattern.DIAGONAL_STRIPES -> ((x + y) and 3) == 0
            SoftDitherPattern.SPARSE_DOTS -> (x and 3) == 0 && (y and 3) == 0
        }
    }

    private fun requirePaletteIndex(colorIndex: Int) {
        if (colorIndex < 0 || colorIndex >= IndexedFramebuffer.ACTIVE_COLOR_COUNT) {
            throw IllegalArgumentException("Palette index must be in 0..15")
        }
    }
}
