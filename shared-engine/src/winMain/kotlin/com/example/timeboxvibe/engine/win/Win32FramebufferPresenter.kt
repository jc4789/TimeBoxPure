package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.core.IndexedFramebuffer
import com.example.timeboxvibe.engine.core.Pc98GraphicsHardware

/** Expands one completed indexed frame into the DIB byte order used by Win32 presentation. */
internal class Win32FramebufferPresenter(initialWidth: Int, initialHeight: Int) {
    private val nativePalette = IntArray(Pc98GraphicsHardware.PALETTE_SIZE)
    private var paletteRevision = -1

    var pixels = IntArray(initialWidth * initialHeight)
        private set
    var pixelWidth = initialWidth
        private set
    var pixelHeight = initialHeight
        private set

    fun expand(framebuffer: IndexedFramebuffer) {
        ensureSize(framebuffer.width, framebuffer.height)
        syncPalette()
        val source = framebuffer.indices
        val destination = pixels
        var offset = 0
        while (offset < source.size) {
            destination[offset] = nativePalette[source[offset].toInt() and IndexedFramebuffer.PALETTE_INDEX_MASK]
            offset++
        }
    }

    private fun ensureSize(width: Int, height: Int) {
        if (width == pixelWidth && height == pixelHeight) return
        pixelWidth = width
        pixelHeight = height
        pixels = IntArray(width * height)
    }

    private fun syncPalette() {
        val revision = Pc98GraphicsHardware.paletteRevision
        if (revision == paletteRevision) return
        var index = 0
        while (index < Pc98GraphicsHardware.PALETTE_SIZE) {
            val color = Pc98GraphicsHardware.onScreenPalette[index].toInt()
            val red4 = (color shr 8) and 0x0F
            val green4 = (color shr 4) and 0x0F
            val blue4 = color and 0x0F
            val red8 = (red4 shl 4) or red4
            val green8 = (green4 shl 4) or green4
            val blue8 = (blue4 shl 4) or blue4
            nativePalette[index] = blue8 or (green8 shl 8) or (red8 shl 16)
            index++
        }
        paletteRevision = revision
    }
}
