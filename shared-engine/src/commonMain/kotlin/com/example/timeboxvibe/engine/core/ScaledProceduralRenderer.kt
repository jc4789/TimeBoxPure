package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.ShinonomeFont
import kotlin.math.roundToInt

/**
 * High-Level cross-platform procedural drawing engine.
 * It takes primitive vector operations provided by [EngineCanvas] and uses math to
 * procedurally draw retro PC-98 visuals, Shinonome bitmapped typography, complex ZUN-style
 * magical circles, and bullet patterns.
 */
class ScaledProceduralRenderer(val canvas: EngineCanvas) {
    companion object {
        private const val U = 16f
        private const val BUTTON_TEXT_WIDTH_NUM = 9f
        private const val BUTTON_TEXT_WIDTH_DEN = 10f
        private const val BUTTON_TEXT_HEIGHT_NUM = 3f
        private const val BUTTON_TEXT_HEIGHT_DEN = 5f
        private const val BUTTON_BORDER_CELLS_DEN = 8f
    }

    init {
        ShinonomeFont.initCache()
    }

    fun clear(colorIndex: Int) {
        canvas.clear(colorIndex)
    }

    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, colorIndex: Int, strokeWidth: Float = 1f) {
        canvas.drawLine(x0, y0, x1, y1, colorIndex, strokeWidth)
    }

    fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int) {
        canvas.drawRect(x, y, w, h, colorIndex)
    }

    fun drawCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float = 1f, dashed: Boolean = false) {
        canvas.drawCircle(centerX, centerY, radius, colorIndex, strokeWidth, dashed)
    }

    /**
     * Renders a pixel-perfect circle using Bresenham's/Midpoint Circle Algorithm.
     * Operates purely using fast integer arithmetic, avoiding floating-point computations or trig.
     */
    fun drawBresenhamCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f,
        dashed: Boolean = false
    ) {
        drawBresenhamCircle(canvas, centerX, centerY, radius, colorIndex, strokeWidth, dashed)
    }

    /**
     * Renders a dither-filled rectangle directly via the engine canvas.
     */
    fun fillRectDither(
        x0: Float, y0: Float, x1: Float, y1: Float,
        primaryIndex: Int, secondaryIndex: Int,
        pattern: SoftDitherPattern
    ) {
        canvas.fillRectDither(x0, y0, x1, y1, primaryIndex, secondaryIndex, pattern)
    }

    /**
     * Plots a single 16x16 Shinonome Font Glyph.
     * Upscales automatically according to canvas density.
     */
    fun drawGlyph(
        char: Char,
        destX: Float,
        destY: Float,
        colorIndex: Int,
        shadowColorIndex: Int = EngineCanvas.COLOR_TRANSPARENT,
        scale: Int = 1,
        startX: Float = destX,
        startY: Float = destY,
        clipWidth: Int = canvas.width.toInt(),
        clipHeight: Int = canvas.height.toInt()
    ) {
        val glyph = ShinonomeFont.glyphFor(char)
        
        val fScale = scale.toFloat()

        // Render retro shadow first if specified (offset 1 logical pixel)
        if (shadowColorIndex != EngineCanvas.COLOR_TRANSPARENT) {
            drawGlyphRaw(glyph, destX + (1f * fScale), destY + (1f * fScale), shadowColorIndex, scale, startX, startY, clipWidth, clipHeight)
        }
        drawGlyphRaw(glyph, destX, destY, colorIndex, scale, startX, startY, clipWidth, clipHeight)
    }

    fun drawGlyph(
        char: Char,
        destX: Float,
        destY: Float,
        colorIndex: Int,
        shadowColorIndex: Int = EngineCanvas.COLOR_TRANSPARENT,
        sizeMultiplier: Float = 1f,
        startX: Float = destX,
        startY: Float = destY,
        clipWidth: Int = canvas.width.toInt(),
        clipHeight: Int = canvas.height.toInt()
    ) {
        val scaleInt = kotlin.math.round(sizeMultiplier).toInt().coerceAtLeast(1)
        drawGlyph(char, destX, destY, colorIndex, shadowColorIndex, scaleInt, startX, startY, clipWidth, clipHeight)
    }

    private fun drawGlyphRaw(
        glyph: IntArray,
        destX: Float,
        destY: Float,
        colorIndex: Int,
        scale: Int,
        startX: Float,
        startY: Float,
        clipWidth: Int,
        clipHeight: Int
    ) {
        val fScale = scale.toFloat()
        val clipRight = startX + clipWidth
        val clipBottom = startY + clipHeight
        var y = 0
        while (y < U.toInt()) {
            val rowBits = glyph[y]
            var x = 0
            while (x < U.toInt()) {
                val bitMask = 0x8000 ushr x
                if ((rowBits and bitMask) != 0) {
                    val drawX = destX + (x * scale)
                    val drawY = destY + (y * scale)
                    if (drawX >= startX && drawY >= startY && drawX < clipRight && drawY < clipBottom) {
                        canvas.drawRect(drawX, drawY, fScale, fScale, colorIndex)
                    }
                }
                x++
            }
            y++
        }
    }

    fun drawText(
        text: String,
        destX: Float,
        destY: Float,
        colorIndex: Int,
        shadowColorIndex: Int = EngineCanvas.COLOR_TRANSPARENT,
        charSpacing: Float = 0f,
        scale: Int = 1,
        startX: Float = destX,
        startY: Float = destY,
        clipWidth: Int = canvas.width.toInt(),
        clipHeight: Int = canvas.height.toInt()
    ) {
        var currentX = destX
        val fScale = scale.toFloat()
        var i = 0
        while (i < text.length) {
            drawGlyph(text[i], currentX, destY, colorIndex, shadowColorIndex, scale, startX, startY, clipWidth, clipHeight)
            currentX += (U * fScale) + (charSpacing * fScale)
            i++
        }
    }

    fun drawText(
        text: String,
        destX: Float,
        destY: Float,
        colorIndex: Int,
        shadowColorIndex: Int = EngineCanvas.COLOR_TRANSPARENT,
        charSpacing: Float = 0f,
        sizeMultiplier: Float = 1f,
        startX: Float = destX,
        startY: Float = destY,
        clipWidth: Int = canvas.width.toInt(),
        clipHeight: Int = canvas.height.toInt()
    ) {
        val scaleInt = kotlin.math.round(sizeMultiplier).toInt().coerceAtLeast(1)
        drawText(text, destX, destY, colorIndex, shadowColorIndex, charSpacing, scaleInt, startX, startY, clipWidth, clipHeight)
    }

    fun drawOctagram(centerX: Float, centerY: Float, radius: Float, rotAngle: Float, colorIndex: Int, sw: Float) {
        val rotIdx = FastMath.degreesToIdx(rotAngle)
        for (i in 0 until 4) {
            val idx1 = (i * 256) - 256 + rotIdx
            val idx2 = ((i + 1) * 256) - 256 + rotIdx
            val x1 = centerX + radius * FastMath.fastCos(idx1)
            val y1 = centerY + radius * FastMath.fastSin(idx1)
            val x2 = centerX + radius * FastMath.fastCos(idx2)
            val y2 = centerY + radius * FastMath.fastSin(idx2)
            canvas.drawLine(x1, y1, x2, y2, colorIndex, sw)

            val idx1b = idx1 + 128
            val idx2b = idx2 + 128
            val x1b = centerX + radius * FastMath.fastCos(idx1b)
            val y1b = centerY + radius * FastMath.fastSin(idx1b)
            val x2b = centerX + radius * FastMath.fastCos(idx2b)
            val y2b = centerY + radius * FastMath.fastSin(idx2b)
            canvas.drawLine(x1b, y1b, x2b, y2b, colorIndex, sw)
        }
    }

    fun drawPentagram(centerX: Float, centerY: Float, radius: Float, rotAngle: Float, colorIndex: Int, sw: Float) {
        val pPoints = 5
        val rotIdx = FastMath.degreesToIdx(rotAngle)
        for (i in 0 until pPoints) {
            val deg1 = i * 72f - 90f
            val idx1 = FastMath.degreesToIdx(deg1) + rotIdx
            val x1 = centerX + radius * FastMath.fastCos(idx1)
            val y1 = centerY + radius * FastMath.fastSin(idx1)

            val deg2 = ((i + 2) % pPoints) * 72f - 90f
            val idx2 = FastMath.degreesToIdx(deg2) + rotIdx
            val x2 = centerX + radius * FastMath.fastCos(idx2)
            val y2 = centerY + radius * FastMath.fastSin(idx2)

            canvas.drawLine(x1, y1, x2, y2, colorIndex, sw)
            // Talisman ends
            drawBresenhamCircle(x1, y1, sw * 2f, colorIndex, sw, false)
        }
    }

    fun drawProgressTracks(
        centerX: Float, centerY: Float, radius: Float,
        outerProgress: Float, innerProgress: Float,
        primaryColorIndex: Int, secondaryColorIndex: Int,
        isDual: Boolean
    ) {
        val sw = 2f * canvas.density
        val outerSegments = 60
        val activeOuter = (outerProgress * outerSegments).toInt()
        
        // Outer track: Draw clean segment ticks projecting outwards
        for (i in 0 until outerSegments) {
            val aIdx = FastMath.degreesToIdx(i * (360f / outerSegments) - 90f)
            val r1 = radius - (4f * canvas.density)
            val r2 = radius + (4f * canvas.density)
            val cosVal = FastMath.fastCos(aIdx)
            val sinVal = FastMath.fastSin(aIdx)
            val x1 = centerX + r1 * cosVal
            val y1 = centerY + r1 * sinVal
            val x2 = centerX + r2 * cosVal
            val y2 = centerY + r2 * sinVal
            if (i <= activeOuter) {
                canvas.drawLine(x1, y1, x2, y2, primaryColorIndex, sw * 1.5f)
            }
        }

        if (isDual) {
            val innerSegments = 60
            val activeInner = (innerProgress * innerSegments).toInt()
            val rCenter = radius - (24f * canvas.density)
            for (i in 0 until innerSegments) {
                val aIdx = FastMath.degreesToIdx(i * (360f / innerSegments) - 90f)
                val isFifth = i % 5 == 0
                val tickLen = if (isFifth) 4f else 2f
                val r1 = rCenter - (tickLen * canvas.density)
                val r2 = rCenter + (tickLen * canvas.density)
                
                val cosVal = FastMath.fastCos(aIdx)
                val sinVal = FastMath.fastSin(aIdx)
                val x1 = centerX + r1 * cosVal
                val y1 = centerY + r1 * sinVal
                val x2 = centerX + r2 * cosVal
                val y2 = centerY + r2 * sinVal
                
                if (i <= activeInner) {
                    canvas.drawLine(x1, y1, x2, y2, secondaryColorIndex, sw * 1.5f)
                }
            }
        }
    }

    /**
     * High-performance ZUN-Vibe procedural spellcard/danmaku circle generator.
     * Renders magic runes, rotating teeth, and concentric stars purely via vector canvas math.
     */
    fun drawZunMagicCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        progress: Float,
        primaryColorIndex: Int,
        accentColorIndex: Int,
        strokeWidth: Float = 2f
    ) {
        val sw = strokeWidth * canvas.density

        val innerRadius = radius - (32f * canvas.density)
        val tRadius = radius + (14f * canvas.density)

        // Outer runic rim placeholders (small squares rotating geometry)
        val numRunes = 24
        val rot0 = progress * 360f * -0.5f 
        val rotIdx = FastMath.degreesToIdx(rot0)
        for (i in 0 until numRunes) {
             val deg = i * (360f / numRunes)
             val aIdx = FastMath.degreesToIdx(deg) + rotIdx
             val px = centerX + tRadius * FastMath.fastCos(aIdx)
             val py = centerY + tRadius * FastMath.fastSin(aIdx)
             canvas.drawRect(px - sw, py - sw, sw * 2f, sw * 2f, accentColorIndex)
             if (i % 3 == 0) {
                 canvas.drawLine(centerX, centerY, px, py, primaryColorIndex, sw * 0.2f)
             }
        }

        // 1. Concentric rings
        drawBresenhamCircle(centerX, centerY, radius + (12f * canvas.density), primaryColorIndex, sw * 0.5f, dashed = false)
        drawBresenhamCircle(centerX, centerY, radius + (8f * canvas.density), primaryColorIndex, sw, dashed = false)
        
        // 2. Rotating Octagrams (Hakke layer)
        val rotAngle1 = progress * 360f * 0.8f
        drawOctagram(centerX, centerY, innerRadius + (12f * canvas.density), rotAngle1, primaryColorIndex, sw)
        drawOctagram(centerX, centerY, innerRadius + (6f * canvas.density), -rotAngle1, accentColorIndex, sw * 0.5f)

        // 3. Counter-rotating Hexagram core (Sei)
        val rotAngle2 = -progress * 360f * 1.2f
        val rotIdx2 = FastMath.degreesToIdx(rotAngle2)
        val hRad = innerRadius - (12f * canvas.density)
        for (i in 0 until 6) {
             val idx1 = FastMath.degreesToIdx(i * 60f - 90f) + rotIdx2
             val idx2 = FastMath.degreesToIdx((i + 2) * 60f - 90f) + rotIdx2
             val x1 = centerX + hRad * FastMath.fastCos(idx1)
             val y1 = centerY + hRad * FastMath.fastSin(idx1)
             val x2 = centerX + hRad * FastMath.fastCos(idx2)
             val y2 = centerY + hRad * FastMath.fastSin(idx2)
             canvas.drawLine(x1, y1, x2, y2, accentColorIndex, sw)
             // Inner connective web
             canvas.drawLine(x1, y1, centerX, centerY, accentColorIndex, sw * 0.3f)
        }
        drawBresenhamCircle(centerX, centerY, hRad, accentColorIndex, sw, dashed = false)

        // 4. Central nucleus
        val innerBound = hRad * 0.4f
        drawBresenhamCircle(centerX, centerY, innerBound, primaryColorIndex, sw, dashed = false)
        drawBresenhamCircle(centerX, centerY, innerBound * 0.5f, accentColorIndex, sw * 0.5f, dashed = true)
    }

    /**
     * Draws procedural glowing bullets flying around using scalable rectangles!
     */
    fun drawBulletPattern(
        centerX: Float,
        centerY: Float,
        radius: Float,
        bulletProgress: Float,
        bulletColorIndex: Int,
        sparkColorIndex: Int,
        bulletSizeMultiplier: Float = 1f
    ) {
        val angleDegrees = -90f + 360f * bulletProgress
        val aIdx = FastMath.degreesToIdx(angleDegrees)
        val bx = centerX + radius * FastMath.fastCos(aIdx)
        val by = centerY + radius * FastMath.fastSin(aIdx)

        val scale = canvas.density * bulletSizeMultiplier

        // Real-time custom vector-rendered glowing Danmaku bullet
        for (dy in -4..4) {
            for (dx in -4..4) {
                val distSq = dx * dx + dy * dy
                val px = bx + (dx * scale)
                val py = by + (dy * scale)
                when {
                    distSq <= 2 -> canvas.drawRect(px, py, scale, scale, PaletteIndices.WHITE) // White core
                    distSq <= 8 -> canvas.drawRect(px, py, scale, scale, bulletColorIndex) // High intensity primary
                    distSq <= 16 -> canvas.drawRect(px, py, scale, scale, sparkColorIndex) // Color aura
                }
            }
        }
    }

    /**
     * Draws a unified retro PC-98 style button with procedural double-border and high contrast styling.
     */
    fun drawButton(
        text: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        isClicked: Boolean,
        isHovered: Boolean = false
    ) {
        val pc98White = PaletteIndices.WHITE
        val pc98Black = PaletteIndices.BLACK
        val bgColor = if (isClicked || isHovered) pc98White else pc98Black
        val textColor = if (isClicked || isHovered) pc98Black else pc98White

        // Procedural Border:
        // First, fillRect(x, y, w, h, PC98_WHITE) (outer border)
        fillRectDither(x, y, x + w, y + h, pc98White, pc98White, SoftDitherPattern.SOLID)
        val border = U / BUTTON_BORDER_CELLS_DEN
        fillRectDither(x + border, y + border, x + w - border, y + h - border, bgColor, bgColor, SoftDitherPattern.SOLID)

        // Scaled Text centered inside the button with clipping mask
        val maxScaleY = (h * BUTTON_TEXT_HEIGHT_NUM / BUTTON_TEXT_HEIGHT_DEN) / U
        val maxScaleX = (w * BUTTON_TEXT_WIDTH_NUM / BUTTON_TEXT_WIDTH_DEN) / (text.length * U)
        val textScale = kotlin.math.floor(kotlin.math.min(maxScaleX, maxScaleY).toDouble()).toInt().coerceAtLeast(1)

        val textWidth = text.length * U * textScale
        val textX = x + (w - textWidth) / 2f
        val textY = y + (h - (U * textScale)) / 2f
        drawText(
            text = text,
            destX = textX,
            destY = textY,
            colorIndex = textColor,
            scale = textScale,
            startX = x,
            startY = y,
            clipWidth = w.toInt(),
            clipHeight = h.toInt()
        )
    }
}
