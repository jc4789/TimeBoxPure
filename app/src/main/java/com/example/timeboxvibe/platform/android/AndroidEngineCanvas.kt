package com.example.timeboxvibe.platform.android

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import com.example.timeboxvibe.engine.core.EngineCanvas
import com.example.timeboxvibe.engine.core.SoftDitherPattern
import com.example.timeboxvibe.engine.core.Pc98GraphicsHardware
import com.example.timeboxvibe.engine.core.Color12Bit
import kotlin.math.roundToInt
import java.util.concurrent.ConcurrentHashMap

/**
 * Android implementation of the cross-platform EngineCanvas.
 * Draws natively using hardware-accelerated android.graphics.Canvas.
 */
class AndroidEngineCanvas(
    private var nativeCanvas: Canvas,
    override var width: Float,
    override var height: Float,
    override var density: Float
) : EngineCanvas {

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }

    private val cachedNativePalette = IntArray(16)
    private var lastSyncedRevision = -1
    private var drawAlpha = 0xFF

    private fun checkSyncPalette() {
        val rev = Pc98GraphicsHardware.paletteRevision
        if (rev != lastSyncedRevision) {
            for (i in 0 until 16) {
                val color12 = Pc98GraphicsHardware.onScreenPalette[i]
                cachedNativePalette[i] = convert12BitTo32Bit(color12)
            }
            lastSyncedRevision = rev
        }
    }

    private fun convert12BitTo32Bit(color12: Color12Bit): Int {
        val c = color12.toInt()
        val r4 = (c shr 8) and 0x0F
        val g4 = (c shr 4) and 0x0F
        val b4 = c and 0x0F

        val r8 = (r4 shl 4) or r4
        val g8 = (g4 shl 4) or g4
        val b8 = (b4 shl 4) or b4

        // Android native format: 0xAARRGGBB
        return 0xFF000000.toInt() or (r8 shl 16) or (g8 shl 8) or b8
    }

    private fun getNativeColor(colorIndex: Int): Int {
        checkSyncPalette()
        return (drawAlpha shl 24) or (cachedNativePalette[colorIndex and 0x0F] and 0x00FFFFFF)
    }

    override fun setDrawAlpha(alphaByte: Int) {
        drawAlpha = alphaByte.coerceIn(0, 0xFF)
    }

    fun bind(canvas: Canvas) {
        this.nativeCanvas = canvas
    }

    override fun clear(colorIndex: Int) {
        checkSyncPalette()
        nativeCanvas.drawColor(cachedNativePalette[colorIndex and 0x0F])
    }

    override fun setPixel(x: Float, y: Float, colorIndex: Int) {
        paint.color = getNativeColor(colorIndex)
        paint.style = Paint.Style.FILL
        val rx = x.roundToInt().toFloat()
        val ry = y.roundToInt().toFloat()
        nativeCanvas.drawRect(rx, ry, rx + 1f, ry + 1f, paint)
    }

    override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, colorIndex: Int, strokeWidth: Float) {
        paint.color = getNativeColor(colorIndex)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        val rx0 = x0.roundToInt().toFloat()
        val ry0 = y0.roundToInt().toFloat()
        val rx1 = x1.roundToInt().toFloat()
        val ry1 = y1.roundToInt().toFloat()
        nativeCanvas.drawLine(rx0, ry0, rx1, ry1, paint)
    }

    override fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int) {
        paint.color = getNativeColor(colorIndex)
        paint.style = Paint.Style.FILL
        val rx = x.roundToInt().toFloat()
        val ry = y.roundToInt().toFloat()
        val rw = w.roundToInt().toFloat()
        val rh = h.roundToInt().toFloat()
        nativeCanvas.drawRect(rx, ry, rx + rw, ry + rh, paint)
    }

    override fun drawCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float, dashed: Boolean) {
        paint.color = getNativeColor(colorIndex)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        val rcx = centerX.roundToInt().toFloat()
        val rcy = centerY.roundToInt().toFloat()
        val rrad = radius.roundToInt().toFloat()
        if (dashed) {
            val dashLen = 8f * density
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(dashLen, dashLen), 0f)
        } else {
            paint.pathEffect = null
        }
        nativeCanvas.drawCircle(rcx, rcy, rrad, paint)
        paint.pathEffect = null
    }

    override fun fillRectDither(
        x0: Float, y0: Float, x1: Float, y1: Float,
        primaryIndex: Int, secondaryIndex: Int,
        pattern: SoftDitherPattern
    ) {
        val rx0 = x0.roundToInt().toFloat()
        val ry0 = y0.roundToInt().toFloat()
        val rx1 = x1.roundToInt().toFloat()
        val ry1 = y1.roundToInt().toFloat()
        
        val colorPrimary = getNativeColor(primaryIndex)
        val colorSecondary = getNativeColor(secondaryIndex)
        
        if (pattern == SoftDitherPattern.SOLID) {
            paint.color = colorPrimary
            paint.style = Paint.Style.FILL
            nativeCanvas.drawRect(rx0, ry0, rx1, ry1, paint)
            return
        }

        val shader = getOrCreatePattern(pattern, colorPrimary, colorSecondary)
        paint.shader = shader
        paint.style = Paint.Style.FILL
        nativeCanvas.drawRect(rx0, ry0, rx1, ry1, paint)
        paint.shader = null
    }

    companion object {
        private val patternCache = ConcurrentHashMap<String, BitmapShader>()

        private fun getOrCreatePattern(pattern: SoftDitherPattern, colorP: Int, colorS: Int): BitmapShader {
            val key = "${pattern.name}_${colorP}_${colorS}"
            return patternCache.computeIfAbsent(key) { _ ->
                val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                for (y in 0..3) {
                    for (x in 0..3) {
                        val shouldDrawPrimary = when (pattern) {
                            SoftDitherPattern.CHECKERBOARD -> (x + y) % 2 == 0
                            SoftDitherPattern.HORIZONTAL_STRIPES -> y % 2 == 0
                            SoftDitherPattern.VERTICAL_STRIPES -> x % 2 == 0
                            SoftDitherPattern.DIAGONAL_STRIPES -> (x + y) % 4 == 0
                            SoftDitherPattern.SPARSE_DOTS -> (x % 4 == 0) && (y % 4 == 0)
                            else -> true
                        }
                        bmp.setPixel(x, y, if (shouldDrawPrimary) colorP else colorS)
                    }
                }
                BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        }
    }
}
