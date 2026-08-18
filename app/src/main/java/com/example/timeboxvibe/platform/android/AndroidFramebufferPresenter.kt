package com.example.timeboxvibe.platform.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.timeboxvibe.engine.core.IndexedFramebuffer
import com.example.timeboxvibe.engine.core.Pc98GraphicsHardware
import com.example.timeboxvibe.engine.core.PresentationTransform

/** Expands one completed indexed frame and presents it with nearest-neighbor sampling. */
class AndroidFramebufferPresenter(initialWidth: Int, initialHeight: Int) {
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val sourceRect = Rect()
    private val destinationRect = Rect()
    private val nativePalette = IntArray(Pc98GraphicsHardware.PALETTE_SIZE)
    private var paletteRevision = -1
    private var bitmap = Bitmap.createBitmap(initialWidth, initialHeight, Bitmap.Config.ARGB_8888)
    private var nativePixels = IntArray(initialWidth * initialHeight)

    fun present(canvas: Canvas, framebuffer: IndexedFramebuffer, transform: PresentationTransform) {
        ensureSize(framebuffer.width, framebuffer.height)
        syncPalette()
        expandIndices(framebuffer)
        bitmap.setPixels(nativePixels, 0, framebuffer.width, 0, 0, framebuffer.width, framebuffer.height)

        sourceRect.set(0, 0, framebuffer.width, framebuffer.height)
        destinationRect.set(
            transform.viewportX,
            transform.viewportY,
            transform.viewportX + transform.viewportWidth,
            transform.viewportY + transform.viewportHeight
        )
        canvas.drawBitmap(bitmap, sourceRect, destinationRect, paint)
    }

    private fun ensureSize(width: Int, height: Int) {
        if (bitmap.width == width && bitmap.height == height) return
        bitmap.recycle()
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        nativePixels = IntArray(width * height)
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
            nativePalette[index] = 0xFF000000.toInt() or (red8 shl 16) or (green8 shl 8) or blue8
            index++
        }
        paletteRevision = revision
    }

    private fun expandIndices(framebuffer: IndexedFramebuffer) {
        val indices = framebuffer.indices
        val output = nativePixels
        var offset = 0
        while (offset < indices.size) {
            output[offset] = nativePalette[indices[offset].toInt() and IndexedFramebuffer.PALETTE_INDEX_MASK]
            offset++
        }
    }
}
