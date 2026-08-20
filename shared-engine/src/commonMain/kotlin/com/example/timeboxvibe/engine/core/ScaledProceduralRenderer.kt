package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.ShinonomeFont
import kotlin.math.roundToInt

private class UiMappedEngineCanvas(private val output: EngineCanvas) : EngineCanvas {
    private var graphicsDepth = 0
    val aliased = AliasedVectorLayer(this)

    override val width: Float
        get() = if (graphicsDepth > 0) output.width else UiRasterGrid.logicalWidth

    override val height: Float
        get() = if (graphicsDepth > 0) output.height else UiRasterGrid.logicalHeight

    fun beginGraphics() {
        graphicsDepth++
    }

    fun endGraphics() {
        if (graphicsDepth > 0) graphicsDepth--
    }

    override fun setDrawAlpha(alphaByte: Int) {
        output.setDrawAlpha(alphaByte)
    }

    override fun clear(colorIndex: Int) {
        output.clear(colorIndex)
    }

    override fun setPixel(x: Float, y: Float, colorIndex: Int) {
        if (graphicsDepth > 0) {
            output.setPixel(x, y, colorIndex)
            return
        }
        val block = UiRasterGrid.pixelBlock.toFloat()
        output.drawRect(x * block, y * block, block, block, colorIndex)
    }

    override fun drawLine(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        aliased.drawAliasedLine(x0, y0, x1, y1, colorIndex, strokeWidth)
    }

    override fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int) {
        if (graphicsDepth > 0) {
            output.drawRect(x, y, w, h, colorIndex)
            return
        }
        val block = UiRasterGrid.pixelBlock.toFloat()
        output.drawRect(x * block, y * block, w * block, h * block, colorIndex)
    }

    override fun drawCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colorIndex: Int,
        strokeWidth: Float,
        dashed: Boolean
    ) {
        aliased.drawAliasedCircle(centerX, centerY, radius, colorIndex, strokeWidth, dashed)
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
        if (graphicsDepth > 0) {
            output.fillRectDither(x0, y0, x1, y1, primaryIndex, secondaryIndex, pattern)
            return
        }
        val block = UiRasterGrid.pixelBlock.toFloat()
        output.fillRectDither(
            x0 * block,
            y0 * block,
            x1 * block,
            y1 * block,
            primaryIndex,
            secondaryIndex,
            pattern
        )
    }
}

/**
 * High-Level cross-platform procedural drawing engine.
 * It takes primitive vector operations provided by [EngineCanvas] and uses math to
 * procedurally draw retro PC-98 visuals, Shinonome bitmapped typography, complex ZUN-style
 * magical circles, and bullet patterns.
 */
class ScaledProceduralRenderer(private val outputCanvas: EngineCanvas) {
    companion object {
        private const val U = CANONICAL_UI_UNIT
        private const val GLYPH_CELL_COUNT = 16
        private const val GLYPH_SOURCE_CENTER = (GLYPH_CELL_COUNT - 1) * 0.5f
        private const val GLYPH_SAMPLE_FRACTION_BITS = 12
        private const val GLYPH_SAMPLE_FRACTION_SCALE = 1 shl GLYPH_SAMPLE_FRACTION_BITS
        private const val GLYPH_SAMPLE_FRACTION_HALF = GLYPH_SAMPLE_FRACTION_SCALE / 2
        const val TEXT_SCALE_IDENTITY = 1
        const val TEXT_SCALE_HEADER = 2

        fun measureTextCells(text: String): Int {
            return text.length
        }

        fun measureTextWidth(text: String, scale: Int = TEXT_SCALE_IDENTITY): Float {
            return measureTextCells(text) * sourceCellSize(scale)
        }

        fun measureTextHeight(scale: Int = TEXT_SCALE_IDENTITY): Float {
            return sourceCellSize(scale)
        }

        fun sourcePixelSize(scale: Int): Int {
            return scale.coerceAtLeast(TEXT_SCALE_IDENTITY)
        }

        fun sourceCellSize(scale: Int): Float {
            return (CANONICAL_UI_UNIT * sourcePixelSize(scale)).toFloat()
        }

        fun measureButtonHeight(text: String, width: Float, minimumHeight: Float, allowTextStacking: Boolean): Float {
            val textAreaWidth = maxOf(U.toFloat(), width - U.toFloat())
            val textHeight = if (allowTextStacking) {
                ProceduralTextRenderer.measureWrappedHeight(text, textAreaWidth, TEXT_SCALE_IDENTITY)
            } else {
                measureTextHeight(TEXT_SCALE_IDENTITY)
            }
            return maxOf(minimumHeight, textHeight + U.toFloat())
        }

    }

    init {
        ShinonomeFont.initCache()
    }

    private val mappedCanvas = UiMappedEngineCanvas(outputCanvas)
    val canvas: EngineCanvas = mappedCanvas
    private val aliased = mappedCanvas.aliased

    fun beginGraphics() {
        mappedCanvas.beginGraphics()
    }

    fun endGraphics() {
        mappedCanvas.endGraphics()
    }

    fun outputX(logicalX: Float): Float = UiRasterGrid.outputX(logicalX)

    fun outputY(logicalY: Float): Float = UiRasterGrid.outputY(logicalY)

    fun clear(colorIndex: Int) {
        canvas.clear(colorIndex)
    }

    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, colorIndex: Int, strokeWidth: Float = 1f) {
        aliased.drawAliasedLine(x0, y0, x1, y1, colorIndex, strokeWidth)
    }

    fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int) {
        canvas.drawRect(x, y, w, h, colorIndex)
    }

    fun drawTextRasterRect(
        x: Float,
        y: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        colorIndex: Int,
        scale: Int = TEXT_SCALE_IDENTITY
    ) {
        val sourcePixelSize = sourcePixelSize(scale).toFloat()
        canvas.drawRect(
            x,
            y,
            sourceWidth * sourcePixelSize,
            sourceHeight * sourcePixelSize,
            colorIndex
        )
    }

    fun drawCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float = 1f, dashed: Boolean = false) {
        aliased.drawAliasedCircle(centerX, centerY, radius, colorIndex, strokeWidth, dashed)
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
     * A ROM bit is one primitive pixel at identity size.
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
        
        val sourcePixelSize = sourcePixelSize(scale).toFloat()

        // Render retro shadow first if specified (offset one source pixel).
        if (shadowColorIndex != EngineCanvas.COLOR_TRANSPARENT) {
            drawGlyphRaw(glyph, destX + sourcePixelSize, destY + sourcePixelSize, shadowColorIndex, scale, startX, startY, clipWidth, clipHeight)
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
        val sourcePixelSize = sourcePixelSize(scale)
        val originX = destX.roundToInt()
        val originY = destY.roundToInt()
        val clipLeft = startX.roundToInt()
        val clipTop = startY.roundToInt()
        val clipRight = (startX + clipWidth).roundToInt()
        val clipBottom = (startY + clipHeight).roundToInt()
        var y = 0
        while (y < U.toInt()) {
            val rowBits = glyph[y]
            var x = 0
            while (x < U.toInt()) {
                val bitMask = 0x8000 ushr x
                if ((rowBits and bitMask) != 0) {
                    val drawX = originX + x * sourcePixelSize
                    val drawY = originY + y * sourcePixelSize
                    val clippedLeft = maxOf(drawX, clipLeft)
                    val clippedTop = maxOf(drawY, clipTop)
                    val clippedRight = minOf(drawX + sourcePixelSize, clipRight)
                    val clippedBottom = minOf(drawY + sourcePixelSize, clipBottom)
                    if (clippedLeft < clippedRight && clippedTop < clippedBottom) {
                        canvas.drawRect(
                            clippedLeft.toFloat(),
                            clippedTop.toFloat(),
                            (clippedRight - clippedLeft).toFloat(),
                            (clippedBottom - clippedTop).toFloat(),
                            colorIndex
                        )
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
        val charWidth = sourceCellSize(scale)
        val spacing = charSpacing * sourcePixelSize(scale)
        var i = 0
        while (i < text.length) {
            drawGlyph(toFullwidthDisplayChar(text[i]), currentX, destY, colorIndex, shadowColorIndex, scale, startX, startY, clipWidth, clipHeight)
            currentX += charWidth + spacing
            i++
        }
    }

    /**
     * Places a Shinonome glyph at a polar coordinate relative to (centerX, centerY).
     * When [tangent] is true, the glyph bitmap is rotated by (angleDegrees + 90) so its
     * "up" points radially outward — used for rune bands and scripture rings.
     * When [tangent] is false, the glyph is rendered upright (tops point up) and centered
     * on the polar coordinate.
     * Tangent glyphs are inverse-sampled directly into the final scaled pixel grid,
     * without per-call allocation. Final raster output is integer-snapped and bounded
     * against the canvas.
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
            drawCanonicalGlyph(
                glyph,
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
        var inkPixelCount = 0
        var inkXTotal = 0
        var inkYTotal = 0
        var glyphY = 0
        while (glyphY < GLYPH_CELL_COUNT) {
            val rowBits = glyph[glyphY]
            var glyphX = 0
            while (glyphX < GLYPH_CELL_COUNT) {
                if ((rowBits and (0x8000 ushr glyphX)) != 0) {
                    inkPixelCount++
                    inkXTotal += glyphX
                    inkYTotal += glyphY
                }
                glyphX++
            }
            glyphY++
        }
        val sourceCenterX = if (inkPixelCount > 0) {
            inkXTotal.toFloat() / inkPixelCount
        } else {
            GLYPH_SOURCE_CENTER
        }
        val sourceCenterY = if (inkPixelCount > 0) {
            inkYTotal.toFloat() / inkPixelCount
        } else {
            GLYPH_SOURCE_CENTER
        }
        emitRotatedGlyph(
            glyph,
            polarX,
            polarY,
            cosR,
            sinR,
            sourceCenterX,
            sourceCenterY,
            colorIndex,
            shadowColorIndex,
            scale
        )
    }

    /** Keeps ornamental polar glyphs in the canonical UI transform. */
    private fun drawCanonicalGlyph(
        glyph: IntArray,
        destX: Float,
        destY: Float,
        colorIndex: Int,
        shadowColorIndex: Int,
        scale: Int
    ) {
        val fScale = scale.toFloat()
        if (shadowColorIndex != EngineCanvas.COLOR_TRANSPARENT) {
            drawCanonicalGlyphRaw(glyph, destX + fScale, destY + fScale, shadowColorIndex, scale)
        }
        drawCanonicalGlyphRaw(glyph, destX, destY, colorIndex, scale)
    }

    private fun drawCanonicalGlyphRaw(
        glyph: IntArray,
        destX: Float,
        destY: Float,
        colorIndex: Int,
        scale: Int
    ) {
        val fScale = scale.toFloat()
        val clipRight = canvas.width
        val clipBottom = canvas.height
        var y = 0
        while (y < GLYPH_CELL_COUNT) {
            val rowBits = glyph[y]
            var x = 0
            while (x < GLYPH_CELL_COUNT) {
                val bitMask = 0x8000 ushr x
                if ((rowBits and bitMask) != 0) {
                    val drawX = destX + x * scale
                    val drawY = destY + y * scale
                    if (drawX >= 0f && drawY >= 0f && drawX < clipRight && drawY < clipBottom) {
                        canvas.drawRect(drawX, drawY, fScale, fScale, colorIndex)
                    }
                }
                x++
            }
            y++
        }
    }

    private fun emitRotatedGlyph(
        glyph: IntArray,
        centerX: Float,
        centerY: Float,
        cosRotation: Float,
        sinRotation: Float,
        sourceCenterX: Float,
        sourceCenterY: Float,
        colorIndex: Int,
        shadowColorIndex: Int,
        scale: Int
    ) {
        val fScale = scale.toFloat()
        val inverseScale = 1f / fScale
        val rasterSize = GLYPH_CELL_COUNT * scale
        val rasterHalf = rasterSize * 0.5f
        val unshiftedBaseX = centerX - rasterHalf
        val unshiftedBaseY = centerY - rasterHalf
        val clipRight = canvas.width.toInt()
        val clipBottom = canvas.height.toInt()
        val firstLocalCoordinate = 0.5f * inverseScale - GLYPH_CELL_COUNT * 0.5f
        val sampleFixedScale = GLYPH_SAMPLE_FRACTION_SCALE.toFloat()
        val firstSourceX = (
            firstLocalCoordinate * cosRotation +
                firstLocalCoordinate * sinRotation +
                sourceCenterX
        )
        val firstSourceY = (
            -firstLocalCoordinate * sinRotation +
                firstLocalCoordinate * cosRotation +
                sourceCenterY
        )
        val sourceXStep = (inverseScale * cosRotation * sampleFixedScale).roundToInt()
        val sourceYStep = (-inverseScale * sinRotation * sampleFixedScale).roundToInt()
        val rowSourceXStep = (inverseScale * sinRotation * sampleFixedScale).roundToInt()
        val rowSourceYStep = (inverseScale * cosRotation * sampleFixedScale).roundToInt()
        val firstSourceXFixed = (firstSourceX * sampleFixedScale).roundToInt()
        val firstSourceYFixed = (firstSourceY * sampleFixedScale).roundToInt()
        var pass = if (shadowColorIndex == EngineCanvas.COLOR_TRANSPARENT) 1 else 0
        while (pass < 2) {
            val passOffset = if (pass == 0) scale else 0
            val passColor = if (pass == 0) shadowColorIndex else colorIndex
            val baseX = unshiftedBaseX.roundToInt() + passOffset
            val baseY = unshiftedBaseY.roundToInt() + passOffset
            var rowSourceX = firstSourceXFixed
            var rowSourceY = firstSourceYFixed
            var rasterY = 0
            while (rasterY < rasterSize) {
                var sampledSourceX = rowSourceX
                var sampledSourceY = rowSourceY
                var rasterX = 0
                while (rasterX < rasterSize) {
                    val sourceX = (sampledSourceX + GLYPH_SAMPLE_FRACTION_HALF) shr
                        GLYPH_SAMPLE_FRACTION_BITS
                    val sourceY = (sampledSourceY + GLYPH_SAMPLE_FRACTION_HALF) shr
                        GLYPH_SAMPLE_FRACTION_BITS
                    if (
                        sourceX >= 0 && sourceX < GLYPH_CELL_COUNT &&
                        sourceY >= 0 && sourceY < GLYPH_CELL_COUNT
                    ) {
                        val bitMask = 0x8000 ushr sourceX
                        if ((glyph[sourceY] and bitMask) != 0) {
                            val pixelX = baseX + rasterX
                            val pixelY = baseY + rasterY
                            if (pixelX >= 0 && pixelY >= 0 && pixelX < clipRight && pixelY < clipBottom) {
                                canvas.setPixel(pixelX.toFloat(), pixelY.toFloat(), passColor)
                            }
                        }
                    }
                    sampledSourceX += sourceXStep
                    sampledSourceY += sourceYStep
                    rasterX++
                }
                rowSourceX += rowSourceXStep
                rowSourceY += rowSourceYStep
                rasterY++
            }
            pass++
        }
    }

    fun drawProgressTracks(
        centerX: Float, centerY: Float, radius: Float,
        outerProgress: Float, innerProgress: Float,
        primaryColorIndex: Int, secondaryColorIndex: Int,
        isDual: Boolean
    ) {
        val sw = 2f
        val outerSegments = 60
        val activeOuter = (outerProgress * outerSegments).toInt()
        
        // Outer track: Draw clean segment ticks projecting outwards
        for (i in 0 until outerSegments) {
            val aIdx = FastMath.degreesToIdx(i * (360f / outerSegments) - 90f)
            val r1 = radius - 4f
            val r2 = radius + 4f
            val cosVal = FastMath.fastCos(aIdx)
            val sinVal = FastMath.fastSin(aIdx)
            val x1 = centerX + r1 * cosVal
            val y1 = centerY + r1 * sinVal
            val x2 = centerX + r2 * cosVal
            val y2 = centerY + r2 * sinVal
            if (i <= activeOuter) {
                aliased.drawAliasedLine(x1, y1, x2, y2, primaryColorIndex, sw * 1.5f)
            }
        }

        if (isDual) {
            val innerSegments = 60
            val activeInner = (innerProgress * innerSegments).toInt()
            val rCenter = radius - 24f
            for (i in 0 until innerSegments) {
                val aIdx = FastMath.degreesToIdx(i * (360f / innerSegments) - 90f)
                val isFifth = i % 5 == 0
                val tickLen = if (isFifth) 4f else 2f
                val r1 = rCenter - tickLen
                val r2 = rCenter + tickLen
                
                val cosVal = FastMath.fastCos(aIdx)
                val sinVal = FastMath.fastSin(aIdx)
                val x1 = centerX + r1 * cosVal
                val y1 = centerY + r1 * sinVal
                val x2 = centerX + r2 * cosVal
                val y2 = centerY + r2 * sinVal
                
                if (i <= activeInner) {
                    aliased.drawAliasedLine(x1, y1, x2, y2, secondaryColorIndex, sw * 1.5f)
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
        aliased.drawAliasedCircle(centerX, centerY, radius, colorIndex, strokeWidth)
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
            aliased.drawAliasedLine(prevX, prevY, x, y, colorIndex, strokeWidth)
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
        aliased.drawAliasedFilledCircle(bx, by, half, colorIndex)
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
            aliased.drawAliasedLine(x1, y1, x2, y2, colorIndex, strokeWidth)
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
            aliased.drawAliasedLine(x1, y1, x2, y2, colorIndex, strokeWidth)
            i++
        }
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
        val scale = bulletSizeMultiplier

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
        aliased.drawAliasedCircle(centerX, centerY, radius, colorIndex, strokeWidth, dashed)
    }

    fun drawAliasedFilledCircle(centerX: Float, centerY: Float, radius: Float, colorIndex: Int) {
        aliased.drawAliasedFilledCircle(centerX, centerY, radius, colorIndex)
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
        aliased.drawRotatedBresenhamHalfCircle(
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
        aliased.drawAliasedArc(centerX, centerY, radius, startDegrees, sweepDegrees, colorIndex, strokeWidth)
    }

    fun drawAliasedProgressArc(
        centerX: Float, centerY: Float, radius: Float,
        startDegrees: Float, fullSweepDegrees: Float,
        progress: Float, colorIndex: Int, strokeWidth: Float = 1f
    ) {
        aliased.drawAliasedProgressArc(centerX, centerY, radius, startDegrees, fullSweepDegrees, progress, colorIndex, strokeWidth)
    }

    fun drawAliasedLine(
        x0: Float, y0: Float, x1: Float, y1: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        aliased.drawAliasedLine(x0, y0, x1, y1, colorIndex, strokeWidth)
    }

    fun drawRadialTickMarks(
        centerX: Float, centerY: Float,
        innerRadius: Float, outerRadius: Float,
        tickCount: Int, startDegrees: Float,
        colorIndex: Int, strokeWidth: Float = 1f,
        majorEvery: Int = 0, majorExtraLength: Float = 0f
    ) {
        aliased.drawRadialTickMarks(centerX, centerY, innerRadius, outerRadius, tickCount, startDegrees, colorIndex, strokeWidth, majorEvery, majorExtraLength)
    }

    fun drawRadialProgressTickMarks(
        centerX: Float, centerY: Float,
        innerRadius: Float, outerRadius: Float,
        tickCount: Int, activeCount: Int, startDegrees: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        aliased.drawRadialProgressTickMarks(centerX, centerY, innerRadius, outerRadius, tickCount, activeCount, startDegrees, colorIndex, strokeWidth)
    }

    fun drawCubicBezierDeCasteljau(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        aliased.drawCubicBezierDeCasteljau(x0, y0, x1, y1, x2, y2, x3, y3, colorIndex, strokeWidth)
    }

    fun drawQuadraticBezierDeCasteljau(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float,
        colorIndex: Int, strokeWidth: Float = 1f
    ) {
        aliased.drawQuadraticBezierDeCasteljau(x0, y0, x1, y1, x2, y2, colorIndex, strokeWidth)
    }

    fun strokeVectorPath(
        ops: IntArray,
        coords: FloatArray,
        originX: Float,
        originY: Float,
        xx: Float,
        xy: Float,
        yx: Float,
        yy: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        aliased.strokePath(ops, coords, originX, originY, xx, xy, yx, yy, colorIndex, strokeWidth)
    }

    fun strokeRectFrame(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f,
        kind: Int = VectorFrameKind.PANEL
    ) {
        VectorOrnament.strokeRectFrame(aliased, x, y, width, height, colorIndex, strokeWidth, kind)
    }

    fun strokeMedallion(centerX: Float, centerY: Float, radius: Float, colorIndex: Int, strokeWidth: Float = 1f) {
        VectorOrnament.strokeMedallion(aliased, centerX, centerY, radius, colorIndex, strokeWidth)
    }

    fun strokeHorizontalRule(x0: Float, y: Float, x1: Float, colorIndex: Int, strokeWidth: Float = 1f) {
        VectorOrnament.strokeHorizontalRule(aliased, x0, y, x1, colorIndex, strokeWidth)
    }

    fun strokeVerticalRule(x: Float, y0: Float, y1: Float, colorIndex: Int, strokeWidth: Float = 1f) {
        VectorOrnament.strokeVerticalRule(aliased, x, y0, y1, colorIndex, strokeWidth)
    }

    fun drawLattice(x0: Float, y0: Float, x1: Float, y1: Float, colorIndex: Int) {
        VectorOrnament.drawLattice(this, x0, y0, x1, y1, colorIndex)
    }

}
