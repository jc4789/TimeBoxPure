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
        const val TEXT_SCALE_IDENTITY = 1
        const val TEXT_SCALE_HEADER = 2
        const val ACTIVE_INDICATOR_GLYPH = '>'
        private const val BUTTON_BORDER_CELLS_DEN = 8f

        fun measureTextCells(text: String): Int {
            return text.length
        }

        fun measureTextWidth(text: String, scale: Int = TEXT_SCALE_IDENTITY): Float {
            return measureTextCells(text) * U * scale
        }

        fun measureTextHeight(scale: Int = TEXT_SCALE_IDENTITY): Float {
            return U * scale
        }
    }

    init {
        ShinonomeFont.initCache()
    }

    private val vector = AliasedVectorLayer(canvas)

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
        scale: Int = TEXT_SCALE_IDENTITY,
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
        sizeMultiplier: Float = TEXT_SCALE_IDENTITY.toFloat(),
        startX: Float = destX,
        startY: Float = destY,
        clipWidth: Int = canvas.width.toInt(),
        clipHeight: Int = canvas.height.toInt()
    ) {
        val scaleInt = kotlin.math.round(sizeMultiplier).toInt().coerceAtLeast(TEXT_SCALE_IDENTITY)
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
        scale: Int = TEXT_SCALE_IDENTITY,
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
        sizeMultiplier: Float = TEXT_SCALE_IDENTITY.toFloat(),
        startX: Float = destX,
        startY: Float = destY,
        clipWidth: Int = canvas.width.toInt(),
        clipHeight: Int = canvas.height.toInt()
    ) {
        val scaleInt = kotlin.math.round(sizeMultiplier).toInt().coerceAtLeast(TEXT_SCALE_IDENTITY)
        drawText(text, destX, destY, colorIndex, shadowColorIndex, charSpacing, scaleInt, startX, startY, clipWidth, clipHeight)
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

    fun drawNestedTimeboxInstrument(
        centerX: Float,
        centerY: Float,
        outerRadius: Float,
        innerRadius: Float,
        quietRadius: Float,
        outerProgress: Float,
        innerProgress: Float,
        rotationProgress: Float,
        isDual: Boolean,
        outerActiveColorIndex: Int,
        innerActiveColorIndex: Int,
        trackColorIndex: Int,
        magicPrimaryColorIndex: Int,
        magicSecondaryColorIndex: Int,
        textFrameColorIndex: Int
    ) {
        val density = canvas.density
        val thin = density
        val medium = density * 2f
        val outerSegments = 90
        val innerSegments = 60
        val outerActive = (outerProgress * outerSegments).toInt().coerceIn(0, outerSegments)
        val innerActive = (innerProgress * innerSegments).toInt().coerceIn(0, innerSegments)
        vector.drawAliasedCircle(centerX, centerY, outerRadius, magicPrimaryColorIndex, thin)
        vector.drawAliasedCircle(centerX, centerY, outerRadius - U / 2f, magicPrimaryColorIndex, thin)
        vector.drawAliasedCircle(centerX, centerY, innerRadius + U / 4f, magicSecondaryColorIndex, thin)
        vector.drawAliasedCircle(centerX, centerY, innerRadius - U / 4f, magicSecondaryColorIndex, thin)
        vector.drawAliasedCircle(centerX, centerY, quietRadius, textFrameColorIndex, thin)

        drawTimerArcRing(centerX, centerY, outerRadius - U / 4f, outerSegments, outerActive, outerProgress, outerActiveColorIndex, trackColorIndex, medium)
        if (isDual) {
            drawTimerArcRing(centerX, centerY, innerRadius, innerSegments, innerActive, innerProgress, innerActiveColorIndex, trackColorIndex, medium)
        }

        val sigilRadius = innerRadius + U / 2f
        val centerDiscRadius = quietRadius - U / 2f
        vector.drawAliasedCircle(centerX, centerY, sigilRadius, magicPrimaryColorIndex, thin)
        vector.drawAliasedCircle(centerX, centerY, quietRadius, textFrameColorIndex, thin)
        drawPentacleFrame(centerX, centerY, sigilRadius, rotationProgress, magicPrimaryColorIndex, thin)
        drawSigilFlourishCurves(centerX, centerY, quietRadius, sigilRadius, magicSecondaryColorIndex, thin)
        drawYinYangDisc(centerX, centerY, centerDiscRadius, textFrameColorIndex, magicSecondaryColorIndex, magicPrimaryColorIndex, trackColorIndex, thin)

        vector.drawRadialTickMarks(
            centerX,
            centerY,
            outerRadius - U - thin,
            outerRadius - U + thin,
            60,
            rotationProgress * 360f,
            magicSecondaryColorIndex,
            thin,
            majorEvery = 5,
            majorExtraLength = thin * 2f
        )

        val notchR = quietRadius + U / 2f
        var i = 0
        while (i < 12) {
            val angleIdx = FastMath.degreesToIdx(i * 30f)
            val nx = centerX + notchR * FastMath.fastCos(angleIdx)
            val ny = centerY + notchR * FastMath.fastSin(angleIdx)
            canvas.drawRect(nx - thin, ny - thin, thin * 2f, thin * 2f, textFrameColorIndex)
            i++
        }
    }

    private fun drawTimerArcRing(
        centerX: Float,
        centerY: Float,
        radius: Float,
        segmentCount: Int,
        activeCount: Int,
        progress: Float,
        activeColorIndex: Int,
        trackColorIndex: Int,
        strokeWidth: Float
    ) {
        vector.drawAliasedArc(centerX, centerY, radius, -90f, 360f, trackColorIndex, strokeWidth)
        vector.drawAliasedProgressArc(centerX, centerY, radius, -90f, 360f, progress, activeColorIndex, strokeWidth)
        vector.drawRadialTickMarks(centerX, centerY, radius - U / 4f, radius + U / 4f, segmentCount, -90f, trackColorIndex, 1f)
        vector.drawRadialProgressTickMarks(centerX, centerY, radius - U / 4f, radius + U / 4f, segmentCount, activeCount, -90f, activeColorIndex, strokeWidth)
    }

    private fun drawPentacleFrame(
        centerX: Float,
        centerY: Float,
        radius: Float,
        rotationProgress: Float,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        val rotIdx = FastMath.degreesToIdx(rotationProgress * 360f - 90f)
        var i = 0
        while (i < 5) {
            val idx1 = FastMath.degreesToIdx(i * 72f) + rotIdx
            val idx2 = FastMath.degreesToIdx(((i + 2) % 5) * 72f) + rotIdx
            vector.drawAliasedLine(
                centerX + radius * FastMath.fastCos(idx1),
                centerY + radius * FastMath.fastSin(idx1),
                centerX + radius * FastMath.fastCos(idx2),
                centerY + radius * FastMath.fastSin(idx2),
                colorIndex,
                strokeWidth
            )
            i++
        }
    }

    private fun drawSigilFlourishCurves(
        centerX: Float,
        centerY: Float,
        quietRadius: Float,
        sigilRadius: Float,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        var i = 0
        while (i < 5) {
            val startIdx = FastMath.degreesToIdx(i * 72f - 90f)
            val controlIdx = FastMath.degreesToIdx(i * 72f + 36f - 90f)
            val endIdx = FastMath.degreesToIdx(i * 72f + 72f - 90f)
            val startRadius = quietRadius + U / 2f
            val controlRadius = sigilRadius - U / 3f
            val endRadius = quietRadius + U / 2f
            vector.drawQuadraticBezierDeCasteljau(
                centerX + startRadius * FastMath.fastCos(startIdx),
                centerY + startRadius * FastMath.fastSin(startIdx),
                centerX + controlRadius * FastMath.fastCos(controlIdx),
                centerY + controlRadius * FastMath.fastSin(controlIdx),
                centerX + endRadius * FastMath.fastCos(endIdx),
                centerY + endRadius * FastMath.fastSin(endIdx),
                colorIndex,
                strokeWidth
            )
            vector.drawCubicBezierDeCasteljau(
                centerX + (sigilRadius - U / 2f) * FastMath.fastCos(startIdx),
                centerY + (sigilRadius - U / 2f) * FastMath.fastSin(startIdx),
                centerX + sigilRadius * FastMath.fastCos(startIdx),
                centerY + sigilRadius * FastMath.fastSin(startIdx),
                centerX + sigilRadius * FastMath.fastCos(endIdx),
                centerY + sigilRadius * FastMath.fastSin(endIdx),
                centerX + (sigilRadius - U / 2f) * FastMath.fastCos(endIdx),
                centerY + (sigilRadius - U / 2f) * FastMath.fastSin(endIdx),
                colorIndex,
                strokeWidth
            )
            i++
        }
    }

    private fun drawYinYangDisc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        lightColorIndex: Int,
        darkColorIndex: Int,
        outlineColorIndex: Int,
        dotColorIndex: Int,
        cellSize: Float
    ) {
        val rSq = radius * radius
        val halfR = radius / 2f
        var y = -radius.toInt()
        while (y <= radius.toInt()) {
            var x = -radius.toInt()
            while (x <= radius.toInt()) {
                val fx = x.toFloat()
                val fy = y.toFloat()
                if (fx * fx + fy * fy <= rSq) {
                    val upperDx = fx
                    val upperDy = fy + halfR
                    val lowerDx = fx
                    val lowerDy = fy - halfR
                    val colorIndex = when {
                        upperDx * upperDx + upperDy * upperDy <= halfR * halfR -> lightColorIndex
                        lowerDx * lowerDx + lowerDy * lowerDy <= halfR * halfR -> darkColorIndex
                        fx >= 0f -> lightColorIndex
                        else -> darkColorIndex
                    }
                    canvas.drawRect(centerX + fx, centerY + fy, cellSize, cellSize, colorIndex)
                }
                x++
            }
            y++
        }
        vector.drawAliasedCircle(centerX, centerY, radius, outlineColorIndex, cellSize)
        vector.drawAliasedCircle(centerX, centerY - halfR, radius / 8f, darkColorIndex, cellSize)
        vector.drawAliasedCircle(centerX, centerY + halfR, radius / 8f, lightColorIndex, cellSize)
        canvas.drawRect(centerX - cellSize, centerY - halfR - cellSize, cellSize * 2f, cellSize * 2f, dotColorIndex)
        canvas.drawRect(centerX - cellSize, centerY + halfR - cellSize, cellSize * 2f, cellSize * 2f, dotColorIndex)
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

        val textScale = TEXT_SCALE_IDENTITY
        val textWidth = measureTextWidth(text, textScale)
        val textHeight = measureTextHeight(textScale)
        val textX = x + (w - textWidth) / 2f
        val textY = y + (h - textHeight) / 2f
        if (isClicked || isHovered) {
            drawGlyph(
                ACTIVE_INDICATOR_GLYPH,
                x + U / 2f,
                y + (h - U) / 2f,
                textColor,
                scale = TEXT_SCALE_IDENTITY,
                startX = x,
                startY = y,
                clipWidth = w.toInt(),
                clipHeight = h.toInt()
            )
        }
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
