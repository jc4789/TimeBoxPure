package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.core.Color12Bit
import com.example.timeboxvibe.engine.core.EngineCanvas
import com.example.timeboxvibe.engine.core.Pc98GraphicsHardware
import com.example.timeboxvibe.engine.core.SoftDitherPattern
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Software framebuffer consumer. Palette indices stay engine-owned;
 * this wrapper only expands 12-bit entries into DIB BGRA dwords.
 */
class Win32EngineCanvas(
    override var width: Float,
    override var height: Float,
    override var density: Float,
    override var presentationScale: Int,
    physicalWidth: Int,
    physicalHeight: Int
) : EngineCanvas {

    private val cachedNativePalette = IntArray(Pc98GraphicsHardware.PALETTE_SIZE)
    private var lastSyncedRevision = -1
    private var drawAlpha = 0xFF
    var pixels: IntArray = IntArray(1)
        private set
    var pixelWidth: Int = 1
        private set
    var pixelHeight: Int = 1
        private set

    init {
        resizeFramebuffer(width, height, physicalWidth, physicalHeight)
    }

    fun resizeFramebuffer(
        logicalWidth: Float,
        logicalHeight: Float,
        physicalWidth: Int,
        physicalHeight: Int
    ) {
        width = logicalWidth
        height = logicalHeight
        val nextW = physicalWidth.coerceAtLeast(1)
        val nextH = physicalHeight.coerceAtLeast(1)
        if (nextW != pixelWidth || nextH != pixelHeight) {
            pixelWidth = nextW
            pixelHeight = nextH
            pixels = IntArray(pixelWidth * pixelHeight)
        }
    }

    private fun scale(): Int = presentationScale.coerceAtLeast(1)

    override fun setDrawAlpha(alphaByte: Int) {
        drawAlpha = alphaByte.coerceIn(0, 0xFF)
    }

    override fun clear(colorIndex: Int) {
        val color = nativeColor(colorIndex)
        var i = 0
        val buf = pixels
        while (i < buf.size) {
            buf[i] = color
            i++
        }
    }

    override fun setPixel(x: Float, y: Float, colorIndex: Int) {
        fillLogicalCell(x.roundToInt(), y.roundToInt(), nativeColor(colorIndex))
    }

    override fun drawLine(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        val color = nativeColor(colorIndex)
        val s = scale()
        val radius = ((strokeWidth * s - 1f) * 0.5f).roundToInt().coerceAtLeast(0)
        bresenham(
            x0.roundToInt() * s,
            y0.roundToInt() * s,
            x1.roundToInt() * s,
            y1.roundToInt() * s
        ) { px, py ->
            if (radius <= 0) {
                plot(px, py, color)
            } else {
                fillDisk(px, py, radius, color)
            }
        }
    }

    override fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int) {
        val s = scale()
        fillRectSnapped(
            x.roundToInt() * s,
            y.roundToInt() * s,
            w.roundToInt() * s,
            h.roundToInt() * s,
            nativeColor(colorIndex)
        )
    }

    override fun drawPhysicalRect(x: Int, y: Int, w: Int, h: Int, colorIndex: Int) {
        if (w <= 0 || h <= 0) return
        fillRectSnapped(x, y, w, h, nativeColor(colorIndex))
    }

    override fun drawCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colorIndex: Int,
        strokeWidth: Float,
        dashed: Boolean
    ) {
        val color = nativeColor(colorIndex)
        val s = scale()
        val cx = centerX.roundToInt() * s
        val cy = centerY.roundToInt() * s
        val r = (radius.roundToInt() * s).coerceAtLeast(0)
        val thickness = (strokeWidth.roundToInt() * s).coerceAtLeast(1)
        val dashLen = (8f * density * s).roundToInt().coerceAtLeast(1)
        midpointCircle(cx, cy, r, dashed, dashLen) { px, py ->
            if (thickness <= 1) {
                plot(px, py, color)
            } else {
                fillDisk(px, py, thickness / 2, color)
            }
        }
    }

    override fun fillRectDither(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        primaryIndex: Int,
        secondaryIndex: Int,
        pattern: SoftDitherPattern
    ) {
        val left = x0.roundToInt()
        val top = y0.roundToInt()
        val right = x1.roundToInt()
        val bottom = y1.roundToInt()
        val primary = nativeColor(primaryIndex)
        val secondary = nativeColor(secondaryIndex)
        if (pattern == SoftDitherPattern.SOLID) {
            val s = scale()
            fillRectSnapped(left * s, top * s, (right - left) * s, (bottom - top) * s, primary)
            return
        }
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val usePrimary = when (pattern) {
                    SoftDitherPattern.CHECKERBOARD -> ((x + y) and 1) == 0
                    SoftDitherPattern.HORIZONTAL_STRIPES -> (y and 1) == 0
                    SoftDitherPattern.VERTICAL_STRIPES -> (x and 1) == 0
                    SoftDitherPattern.DIAGONAL_STRIPES -> ((x + y) and 3) == 0
                    SoftDitherPattern.SPARSE_DOTS -> (x and 3) == 0 && (y and 3) == 0
                    SoftDitherPattern.SOLID -> true
                }
                fillLogicalCell(x, y, if (usePrimary) primary else secondary)
                x++
            }
            y++
        }
    }

    private fun syncPalette() {
        val rev = Pc98GraphicsHardware.paletteRevision
        if (rev == lastSyncedRevision) return
        var i = 0
        while (i < Pc98GraphicsHardware.PALETTE_SIZE) {
            cachedNativePalette[i] = convert12BitToDib(Pc98GraphicsHardware.onScreenPalette[i])
            i++
        }
        lastSyncedRevision = rev
    }

    private fun nativeColor(colorIndex: Int): Int {
        syncPalette()
        return cachedNativePalette[colorIndex and 0x0F] and 0x00FFFFFF
    }

    private fun convert12BitToDib(color12: Color12Bit): Int {
        val c = color12.toInt()
        val r4 = (c shr 8) and 0x0F
        val g4 = (c shr 4) and 0x0F
        val b4 = c and 0x0F
        val r8 = (r4 shl 4) or r4
        val g8 = (g4 shl 4) or g4
        val b8 = (b4 shl 4) or b4
        return b8 or (g8 shl 8) or (r8 shl 16)
    }

    private fun fillLogicalCell(logicalX: Int, logicalY: Int, color: Int) {
        val s = scale()
        fillRectSnapped(logicalX * s, logicalY * s, s, s, color)
    }

    private fun plot(x: Int, y: Int, color: Int) {
        if (x < 0 || y < 0 || x >= pixelWidth || y >= pixelHeight) return
        val index = y * pixelWidth + x
        val alpha = drawAlpha
        if (alpha >= 0xFF) {
            pixels[index] = color
        } else if (alpha > 0) {
            pixels[index] = blendRgb(color, pixels[index], alpha)
        }
    }

    private fun fillRectSnapped(x: Int, y: Int, w: Int, h: Int, color: Int) {
        if (w <= 0 || h <= 0) return
        val left = x.coerceAtLeast(0)
        val top = y.coerceAtLeast(0)
        val right = (x + w).coerceAtMost(pixelWidth)
        val bottom = (y + h).coerceAtMost(pixelHeight)
        if (left >= right || top >= bottom) return
        val alpha = drawAlpha
        if (alpha <= 0) return
        var row = top
        while (row < bottom) {
            var col = left
            val rowOffset = row * pixelWidth
            if (alpha >= 0xFF) {
                while (col < right) {
                    pixels[rowOffset + col] = color
                    col++
                }
            } else {
                while (col < right) {
                    val index = rowOffset + col
                    pixels[index] = blendRgb(color, pixels[index], alpha)
                    col++
                }
            }
            row++
        }
    }

    private fun blendRgb(source: Int, destination: Int, alpha: Int): Int {
        val inverse = 0xFF - alpha
        val blue = (((source and 0xFF) * alpha) + ((destination and 0xFF) * inverse) + 0x7F) / 0xFF
        val green = ((((source ushr 8) and 0xFF) * alpha) + (((destination ushr 8) and 0xFF) * inverse) + 0x7F) / 0xFF
        val red = ((((source ushr 16) and 0xFF) * alpha) + (((destination ushr 16) and 0xFF) * inverse) + 0x7F) / 0xFF
        return blue or (green shl 8) or (red shl 16)
    }

    private fun fillDisk(cx: Int, cy: Int, radius: Int, color: Int) {
        val r2 = radius * radius
        var y = -radius
        while (y <= radius) {
            var x = -radius
            while (x <= radius) {
                if (x * x + y * y <= r2) {
                    plot(cx + x, cy + y, color)
                }
                x++
            }
            y++
        }
    }

    private inline fun bresenham(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        plotPoint: (Int, Int) -> Unit
    ) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val sx = if (x0 < x1) 1 else -1
        val dy = -abs(y1 - y0)
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            plotPoint(x, y)
            if (x == x1 && y == y1) return
            val e2 = err shl 1
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
    }

    private inline fun midpointCircle(
        cx: Int,
        cy: Int,
        radius: Int,
        dashed: Boolean,
        dashLen: Int,
        plotPoint: (Int, Int) -> Unit
    ) {
        if (radius <= 0) {
            plotPoint(cx, cy)
            return
        }
        var x = radius
        var y = 0
        var err = 1 - radius
        var dashIndex = 0
        while (x >= y) {
            val draw = !dashed || ((dashIndex / dashLen) and 1) == 0
            if (draw) {
                plotPoint(cx + x, cy + y)
                plotPoint(cx + y, cy + x)
                plotPoint(cx - y, cy + x)
                plotPoint(cx - x, cy + y)
                plotPoint(cx - x, cy - y)
                plotPoint(cx - y, cy - x)
                plotPoint(cx + y, cy - x)
                plotPoint(cx + x, cy - y)
            }
            dashIndex++
            y++
            if (err < 0) {
                err += (y shl 1) + 1
            } else {
                x--
                err += ((y - x) shl 1) + 1
            }
        }
    }
}
