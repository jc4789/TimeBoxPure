package com.example.timeboxvibe.engine.core

/** Draws 16x16 ROM glyph bits into an indexed framebuffer at primitive size. */
class GlyphRasterizer(private val framebuffer: IndexedFramebuffer) {
    fun draw(
        glyphRows: IntArray,
        x: Int,
        y: Int,
        colorIndex: Int,
        clipX: Int = 0,
        clipY: Int = 0,
        clipWidth: Int = framebuffer.width,
        clipHeight: Int = framebuffer.height
    ) {
        if (colorIndex < 0 || colorIndex >= IndexedFramebuffer.ACTIVE_COLOR_COUNT) {
            throw IllegalArgumentException("Palette index must be in 0..15")
        }
        val clipRight = (clipX + clipWidth).coerceAtMost(framebuffer.width)
        val clipBottom = (clipY + clipHeight).coerceAtMost(framebuffer.height)
        val pixels = framebuffer.indices
        val stride = framebuffer.width
        val color = colorIndex.toByte()
        var row = 0
        val rowCount = minOf(GLYPH_HEIGHT, glyphRows.size)
        while (row < rowCount) {
            val targetY = y + row
            if (targetY >= clipY && targetY < clipBottom && targetY >= 0) {
                val bits = glyphRows[row]
                var column = 0
                while (column < GLYPH_WIDTH) {
                    val targetX = x + column
                    if (targetX >= clipX && targetX < clipRight && targetX >= 0) {
                        if ((bits and (GLYPH_LEFT_BIT ushr column)) != 0) {
                            pixels[targetY * stride + targetX] = color
                        }
                    }
                    column++
                }
            }
            row++
        }
    }

    companion object {
        const val GLYPH_WIDTH = 16
        const val GLYPH_HEIGHT = 16
        private const val GLYPH_LEFT_BIT = 0x8000
    }
}

