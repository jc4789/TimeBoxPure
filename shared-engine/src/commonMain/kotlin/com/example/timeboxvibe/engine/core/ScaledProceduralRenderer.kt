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
        private const val U = 16
        private const val GLYPH_CELL_COUNT = 16
        const val TEXT_SCALE_IDENTITY = 1
        const val TEXT_SCALE_HEADER = 2
        const val ACTIVE_INDICATOR_GLYPH = '>'
        private const val BUTTON_BORDER_CELLS_DEN = 8f

        fun measureTextCells(text: String): Int {
            return text.length
        }

        fun measureTextWidth(text: String, scale: Int = TEXT_SCALE_IDENTITY): Float {
            return (measureTextCells(text) * U * scale).toFloat()
        }

        fun measureTextHeight(scale: Int = TEXT_SCALE_IDENTITY): Float {
            return (U * scale).toFloat()
        }

    }

    init {
        ShinonomeFont.initCache()
    }

    private val vector = AliasedVectorLayer(canvas)
    val nestedTimeboxRenderer = NestedTimeboxInstrumentRenderer(this)

    private val rotatedGlyphBuffer = IntArray(GLYPH_CELL_COUNT * GLYPH_CELL_COUNT)

    fun clear(colorIndex: Int) {
        canvas.clear(colorIndex)
    }

    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, colorIndex: Int, strokeWidth: Float = 1f) {
        canvas.drawLine(x0, y0, x1, y1, colorIndex, strokeWidth)
    }

    fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int) {
        canvas.drawRect(x, y, w, h, colorIndex)
    }

    /**
     * Platform canvas circle. Non-authoritative for PC-98 ornaments —
     * use [drawAliasedCircle] for integer-snapped palette-index rings.
     */
    fun drawCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float = 1f, dashed: Boolean = false) {
        canvas.drawCircle(centerX, centerY, radius, colorIndex, strokeWidth, dashed)
    }

    /**
     * Renders a dither-filled rectangle directly via the engine canvas.
     * Colors are palette indices 0..15 (4-bit on-screen), not host ARGB.
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
        startX: Float = 0f,
        startY: Float = 0f,
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
        startX: Float = 0f,
        startY: Float = 0f,
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

    /**
     * Places a Shinonome glyph at a polar coordinate relative to (centerX, centerY).
     * When [tangent] is true, the glyph bitmap is rotated by (angleDegrees + 90) so its
     * "up" points radially outward — used for rune bands and scripture rings.
     * When [tangent] is false, the glyph is rendered upright (tops point up) and centered
     * on the polar coordinate.
     * Reuses [rotatedGlyphBuffer] to avoid per-call allocation. Final raster output is
     * integer-snapped and bounded against the canvas.
     */
    /**
     * Places a small filled disc at a polar coordinate. Used for the magic
     * circle's outer decoration dot ring (12 fixed dots) and any other
     * polar-positioned marker that should be circular.
     */
    fun drawPolarDot(
        centerX: Float,
        centerY: Float,
        radius: Float,
        angleDegrees: Float,
        size: Float,
        colorIndex: Int
    ) {
        val aIdx = FastMath.degreesToIdx(angleDegrees)
        val x = centerX + radius * FastMath.fastCos(aIdx)
        val y = centerY + radius * FastMath.fastSin(aIdx)
        drawAliasedFilledCircle(x, y, size, colorIndex)
    }

    fun drawPolarGlyph(
        char: Char,
        centerX: Float,
        centerY: Float,
        radius: Float,
        angleDegrees: Float,
        colorIndex: Int,
        shadowColorIndex: Int = EngineCanvas.COLOR_TRANSPARENT,
        scale: Int = TEXT_SCALE_IDENTITY,
        tangent: Boolean = false
    ) {
        val glyph = ShinonomeFont.glyphFor(char)
        val aIdx = FastMath.degreesToIdx(angleDegrees)
        val polarX = centerX + radius * FastMath.fastCos(aIdx)
        val polarY = centerY + radius * FastMath.fastSin(aIdx)

        if (!tangent) {
            val fScale = scale.toFloat()
            drawGlyph(
                char,
                polarX - U * 0.5f * fScale,
                polarY - U * 0.5f * fScale,
                colorIndex,
                shadowColorIndex,
                scale
            )
            return
        }

        // Tangent rotation: glyph "up" maps to the radial outward direction.
        // At polar angle theta, outward = (cos theta, sin theta). Glyph natural up = (0, -1).
        // So we rotate the bitmap by theta + 90 degrees.
        val rotIdx = FastMath.degreesToIdx(angleDegrees + 90f)
        val cosR = FastMath.fastCos(rotIdx)
        val sinR = FastMath.fastSin(rotIdx)
        val cxLocal = 7.5f
        val cyLocal = 7.5f

        var i = 0
        while (i < GLYPH_CELL_COUNT * GLYPH_CELL_COUNT) {
            rotatedGlyphBuffer[i] = 0
            i++
        }

        var y = 0
        while (y < GLYPH_CELL_COUNT) {
            val rowBits = glyph[y]
            var x = 0
            while (x < GLYPH_CELL_COUNT) {
                val bitMask = 0x8000 ushr x
                if ((rowBits and bitMask) != 0) {
                    val localX = (x - cxLocal).toFloat()
                    val localY = (y - cyLocal).toFloat()
                    val rx = (localX * cosR - localY * sinR).roundToInt() + 8
                    val ry = (localX * sinR + localY * cosR).roundToInt() + 8
                    if (rx in 0..15 && ry in 0..15) {
                        rotatedGlyphBuffer[ry * GLYPH_CELL_COUNT + rx] = 1
                    }
                }
                x++
            }
            y++
        }
        emitRotatedGlyph(polarX, polarY, colorIndex, shadowColorIndex, scale)
    }

    private fun emitRotatedGlyph(
        centerX: Float,
        centerY: Float,
        colorIndex: Int,
        shadowColorIndex: Int,
        scale: Int
    ) {
        val fScale = scale.toFloat()
        val baseX = centerX - U * 0.5f * fScale
        val baseY = centerY - U * 0.5f * fScale
        val w = canvas.width.toInt()
        val h = canvas.height.toInt()

        if (shadowColorIndex != EngineCanvas.COLOR_TRANSPARENT) {
            var y = 0
            while (y < GLYPH_CELL_COUNT) {
                var x = 0
                while (x < GLYPH_CELL_COUNT) {
                    if (rotatedGlyphBuffer[y * GLYPH_CELL_COUNT + x] != 0) {
                        var sy = 0
                        while (sy < scale) {
                            var sx = 0
                            while (sx < scale) {
                                val px = baseX + (x * scale + sx) + fScale
                                val py = baseY + (y * scale + sy) + fScale
                                if (px >= 0 && px < w && py >= 0 && py < h) {
                                    canvas.setPixel(px, py, shadowColorIndex)
                                }
                                sx++
                            }
                            sy++
                        }
                    }
                    x++
                }
                y++
            }
        }

        var y = 0
        while (y < GLYPH_CELL_COUNT) {
            var x = 0
            while (x < GLYPH_CELL_COUNT) {
                if (rotatedGlyphBuffer[y * GLYPH_CELL_COUNT + x] != 0) {
                    var sy = 0
                    while (sy < scale) {
                        var sx = 0
                        while (sx < scale) {
                            val px = baseX + (x * scale + sx)
                            val py = baseY + (y * scale + sy)
                            if (px >= 0 && px < w && py >= 0 && py < h) {
                                canvas.setPixel(px, py, colorIndex)
                            }
                            sx++
                        }
                        sy++
                    }
                }
                x++
            }
            y++
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

    fun getPolarX(centerX: Float, radius: Float, angleDegrees: Float): Float {
        val aIdx = FastMath.degreesToIdx(angleDegrees)
        return centerX + radius * FastMath.fastCos(aIdx)
    }

    fun getPolarY(centerY: Float, radius: Float, angleDegrees: Float): Float {
        val aIdx = FastMath.degreesToIdx(angleDegrees)
        return centerY + radius * FastMath.fastSin(aIdx)
    }

    fun drawCircleStroke(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float = 1f) {
        canvas.drawCircle(centerX, centerY, radius, colorIndex, strokeWidth, false)
    }

    fun setDrawAlpha(alphaByte: Int) {
        canvas.setDrawAlpha(alphaByte)
    }

    fun drawSegmentedArc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        startDegrees: Float,
        sweepDegrees: Float,
        segmentCount: Int,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        if (segmentCount <= 0) return
        val sweepStep = sweepDegrees / segmentCount
        var prevX = getPolarX(centerX, radius, startDegrees)
        var prevY = getPolarY(centerY, radius, startDegrees)
        var i = 1
        while (i <= segmentCount) {
            val angle = startDegrees + sweepStep * i
            val x = getPolarX(centerX, radius, angle)
            val y = getPolarY(centerY, radius, angle)
            canvas.drawLine(prevX, prevY, x, y, colorIndex, strokeWidth)
            prevX = x
            prevY = y
            i++
        }
    }

    fun drawPolarBead(
        centerX: Float,
        centerY: Float,
        radius: Float,
        angleDegrees: Float,
        size: Float,
        colorIndex: Int
    ) {
        val bx = getPolarX(centerX, radius, angleDegrees)
        val by = getPolarY(centerY, radius, angleDegrees)
        val half = size / 2f
        vector.drawAliasedFilledCircle(bx, by, half, colorIndex)
    }

    fun drawPolarStarLinks(
        centerX: Float,
        centerY: Float,
        radius: Float,
        vertexCount: Int,
        step: Int,
        phaseDegrees: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        if (vertexCount <= 2) return
        var i = 0
        while (i < vertexCount) {
            val angle1 = phaseDegrees + i * (360f / vertexCount)
            val angle2 = phaseDegrees + ((i + step) % vertexCount) * (360f / vertexCount)
            val x1 = getPolarX(centerX, radius, angle1)
            val y1 = getPolarY(centerY, radius, angle1)
            val x2 = getPolarX(centerX, radius, angle2)
            val y2 = getPolarY(centerY, radius, angle2)
            canvas.drawLine(x1, y1, x2, y2, colorIndex, strokeWidth)
            i++
        }
    }

    fun drawRotatingPolygon(
        centerX: Float,
        centerY: Float,
        radius: Float,
        vertexCount: Int,
        phaseDegrees: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        drawPolarStarLinks(centerX, centerY, radius, vertexCount, 1, phaseDegrees, colorIndex, strokeWidth)
    }

    fun drawActivePolarBeadLoop(
        centerX: Float,
        centerY: Float,
        radius: Float,
        totalCount: Int,
        activeCount: Int,
        startAngleDegrees: Float,
        beadSize: Float,
        colorIndex: Int
    ) {
        if (totalCount <= 0 || activeCount <= 0) return
        val count = activeCount.coerceAtMost(totalCount)
        val step = 360f / totalCount
        val half = beadSize / 2f
        var i = 0
        while (i < count) {
            val angle = startAngleDegrees + i * step
            val aIdx = FastMath.degreesToIdx(angle)
            val x = centerX + radius * FastMath.fastCos(aIdx) - half
            val y = centerY + radius * FastMath.fastSin(aIdx) - half
            canvas.drawRect(x, y, beadSize, beadSize, colorIndex)
            i++
        }
    }

    fun drawActivePolarTickLoop(
        centerX: Float,
        centerY: Float,
        innerRadius: Float,
        outerRadius: Float,
        totalCount: Int,
        activeCount: Int,
        startAngleDegrees: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        if (totalCount <= 0 || activeCount <= 0) return
        val count = activeCount.coerceAtMost(totalCount)
        val step = 360f / totalCount
        var i = 0
        while (i < count) {
            val angle = startAngleDegrees + i * step
            val x1 = getPolarX(centerX, innerRadius, angle)
            val y1 = getPolarY(centerY, innerRadius, angle)
            val x2 = getPolarX(centerX, outerRadius, angle)
            val y2 = getPolarY(centerY, outerRadius, angle)
            canvas.drawLine(x1, y1, x2, y2, colorIndex, strokeWidth)
            i++
        }
    }

    fun drawProceduralPolarDemo(centerX: Float, centerY: Float, baseRadius: Float, timeMs: Long) {
        val density = canvas.density
        // 1. Draw independent rotating circle stroke
        drawCircleStroke(centerX, centerY, baseRadius, PaletteIndices.HIGHLIGHT, 1f * density)

        // 2. Draw rotating star links (5-pointed star rotating counter-clockwise at 8s period)
        val phaseStar = (timeMs % 8000L).toFloat() / 8000f * -360f
        drawPolarStarLinks(centerX, centerY, baseRadius - U, 5, 2, phaseStar, PaletteIndices.ACCENT_SECONDARY, 1f * density)

        // 3. Draw rotating octagon (rotating clockwise at 12s period)
        val phaseOct = (timeMs % 12000L).toFloat() / 12000f * 360f
        drawRotatingPolygon(centerX, centerY, baseRadius - U * 2f, 8, phaseOct, PaletteIndices.MAGIC_CIRCLE_PRIMARY, 1f * density)

        // 4. Draw active-only bead loop (30 beads, 15 active, rotating slowly)
        val phaseBeads = (timeMs % 20000L).toFloat() / 20000f * 360f
        drawActivePolarBeadLoop(centerX, centerY, baseRadius + U, 30, 15, phaseBeads, 4f * density, PaletteIndices.BORDER)

        // 5. Draw active-only ticks (24 ticks, 12 active, rotating CCW)
        val phaseTicks = (timeMs % 15000L).toFloat() / 15000f * -360f
        drawActivePolarTickLoop(centerX, centerY, baseRadius - U * 3f, baseRadius - U * 2.5f, 24, 12, phaseTicks, PaletteIndices.HIGHLIGHT, 1f * density)
    }

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

        // 1. Draw trailing spark offset by -8 degrees (small single-pixel trail)
        val sparkAngleDegrees = angleDegrees - 8f
        val saIdx = FastMath.degreesToIdx(sparkAngleDegrees)
        val sx = centerX + radius * FastMath.fastCos(saIdx)
        val sy = centerY + radius * FastMath.fastSin(saIdx)
        canvas.drawRect(sx, sy, scale, scale, sparkColorIndex)

        // 2. Real-time custom vector-rendered glowing Danmaku bullet
        var dy = -4
        while (dy <= 4) {
            var dx = -4
            while (dx <= 4) {
                val distSq = dx * dx + dy * dy
                val px = bx + (dx * scale)
                val py = by + (dy * scale)
                when {
                    distSq <= 2 -> canvas.drawRect(px, py, scale, scale, PaletteIndices.WHITE) // White core
                    distSq <= 8 -> canvas.drawRect(px, py, scale, scale, bulletColorIndex) // High intensity primary
                    distSq <= 16 -> canvas.drawRect(px, py, scale, scale, sparkColorIndex) // Color aura
                }
                dx++
            }
            dy++
        }
    }
    /**
     * Canonical circle stroke for ornaments/UI — integer Bresenham via [AliasedVectorLayer].
     * [colorIndex] is a palette index 0..15, not host ARGB.
     */
    fun drawAliasedCircle(
        centerX: Float, centerY: Float, radius: Float,
        colorIndex: Int, strokeWidth: Float = 1f, dashed: Boolean = false
    ) {
        vector.drawAliasedCircle(centerX, centerY, radius, colorIndex, strokeWidth, dashed)
    }

    fun drawAliasedFilledCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int) {
        vector.drawAliasedFilledCircle(centerX, centerY, radius, colorIndex)
    }

    fun drawRotatedBresenhamHalfCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        rotationAngleIndex: Int,
        drawPositiveX: Boolean,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        vector.drawRotatedBresenhamHalfCircle(
            centerX,
            centerY,
            radius,
            rotationAngleIndex,
            drawPositiveX,
            colorIndex,
            strokeWidth
        )
    }

    fun drawAliasedArc(
        centerX: Float, centerY: Float, radius: Float,
        startDegrees: Float, sweepDegrees: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        vector.drawAliasedArc(centerX, centerY, radius, startDegrees, sweepDegrees, colorIndex, strokeWidth)
    }

    fun drawAliasedProgressArc(
        centerX: Float, centerY: Float, radius: Float,
        startDegrees: Float, fullSweepDegrees: Float,
        progress: Float, colorIndex: Int, strokeWidth: Float = 1f
    ) {
        vector.drawAliasedProgressArc(centerX, centerY, radius, startDegrees, fullSweepDegrees, progress, colorIndex, strokeWidth)
    }

    fun drawAliasedLine(
        x0: Float, y0: Float, x1: Float, y1: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        vector.drawAliasedLine(x0, y0, x1, y1, colorIndex, strokeWidth)
    }

    fun drawRadialTickMarks(
        centerX: Float, centerY: Float,
        innerRadius: Float, outerRadius: Float,
        tickCount: Int, startDegrees: Float,
        colorIndex: Int, strokeWidth: Float = 1f,
        majorEvery: Int = 0, majorExtraLength: Float = 0f
    ) {
        vector.drawRadialTickMarks(centerX, centerY, innerRadius, outerRadius, tickCount, startDegrees, colorIndex, strokeWidth, majorEvery, majorExtraLength)
    }

    fun drawRadialProgressTickMarks(
        centerX: Float, centerY: Float,
        innerRadius: Float, outerRadius: Float,
        tickCount: Int, activeCount: Int, startDegrees: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        vector.drawRadialProgressTickMarks(centerX, centerY, innerRadius, outerRadius, tickCount, activeCount, startDegrees, colorIndex, strokeWidth)
    }

    fun drawCubicBezierDeCasteljau(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        vector.drawCubicBezierDeCasteljau(x0, y0, x1, y1, x2, y2, x3, y3, colorIndex, strokeWidth)
    }

    fun drawQuadraticBezierDeCasteljau(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        vector.drawQuadraticBezierDeCasteljau(x0, y0, x1, y1, x2, y2, colorIndex, strokeWidth)
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
