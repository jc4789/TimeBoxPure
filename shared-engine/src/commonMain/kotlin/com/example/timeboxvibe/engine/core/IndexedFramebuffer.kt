package com.example.timeboxvibe.engine.core

/**
 * Engine-owned 4-bit indexed framebuffer.
 *
 * A pixel stores one of the sixteen simultaneously active palette entries.
 * The palette entry itself remains a 12-bit RGB value in [Pc98GraphicsHardware].
 * This class knows nothing about UI, glyph cells, platform pixels, or presentation.
 */
class IndexedFramebuffer(initialWidth: Int, initialHeight: Int) {
    var width: Int = positiveDimension(initialWidth)
        private set

    var height: Int = positiveDimension(initialHeight)
        private set

    var indices: ByteArray = ByteArray(width * height)
        private set

    fun resize(nextWidth: Int, nextHeight: Int) {
        val safeWidth = positiveDimension(nextWidth)
        val safeHeight = positiveDimension(nextHeight)
        if (safeWidth == width && safeHeight == height) return
        width = safeWidth
        height = safeHeight
        indices = ByteArray(width * height)
    }

    fun clear(colorIndex: Int) {
        requirePaletteIndex(colorIndex)
        val value = paletteByte(colorIndex)
        indices.fill(value)
    }

    fun colorIndexAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        return indices[y * width + x].toInt() and PALETTE_INDEX_MASK
    }

    fun setColorIndex(x: Int, y: Int, colorIndex: Int) {
        requirePaletteIndex(colorIndex)
        if (x < 0 || y < 0 || x >= width || y >= height) return
        indices[y * width + x] = paletteByte(colorIndex)
    }

    companion object {
        const val ACTIVE_COLOR_COUNT = 16
        const val PALETTE_INDEX_MASK = ACTIVE_COLOR_COUNT - 1

        private fun positiveDimension(value: Int): Int = value.coerceAtLeast(1)

        private fun paletteByte(colorIndex: Int): Byte {
            return colorIndex.toByte()
        }

        private fun requirePaletteIndex(colorIndex: Int) {
            if (colorIndex < 0 || colorIndex >= ACTIVE_COLOR_COUNT) {
                throw IllegalArgumentException("Palette index must be in 0..15")
            }
        }
    }
}
