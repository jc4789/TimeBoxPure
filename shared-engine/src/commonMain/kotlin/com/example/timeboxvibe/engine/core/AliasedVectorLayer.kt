package com.example.timeboxvibe.engine.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Small palette-indexed aliased vector layer for PC-98-style procedural linework.
 * Final raster output is snapped to integer pixels and emitted through EngineCanvas.
 *
 * Ownership contract:
 * - Sole integer Bresenham circle/line/arc/Bezier rasterizer (no second circle path).
 * - Stroke width is the graphic's own raster step, so proportional geometry keeps the same alias mask.
 * - Colors are palette indices 0..15 (4-bit on-screen); 12-bit RAMDAC is Pc98GraphicsHardware.
 * - Graphics trig uses FastMath only.
 */
class AliasedVectorLayer(private val canvas: EngineCanvas) {
    companion object {
        private const val FULL_TURN_DEGREES = 360f
        private const val SAMPLE_INTERVAL_PIXELS = 4f
        private const val MIN_ARC_SEGMENTS = 6
        private const val MAX_ARC_SEGMENTS = 144
        private const val MIN_BEZIER_SEGMENTS = 4
        private const val MAX_BEZIER_SEGMENTS = 96
    }

    fun drawAliasedLine(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        val rasterStep = strokeWidth.coerceAtLeast(1f)
        drawAliasedLineInt(
            (x0 / rasterStep).roundToInt(),
            (y0 / rasterStep).roundToInt(),
            (x1 / rasterStep).roundToInt(),
            (y1 / rasterStep).roundToInt(),
            colorIndex,
            rasterStep
        )
    }

    fun drawAliasedPolyline(points: FloatArray, pointCount: Int, colorIndex: Int, strokeWidth: Float = 1f) {
        var i = 1
        while (i < pointCount) {
            val prev = (i - 1) * 2
            val curr = i * 2
            drawAliasedLine(points[prev], points[prev + 1], points[curr], points[curr + 1], colorIndex, strokeWidth)
            i++
        }
    }

    fun drawAliasedCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f,
        dashed: Boolean = false
    ) {
        val rasterStep = strokeWidth.coerceAtLeast(1f)
        val xc = (centerX / rasterStep).roundToInt()
        val yc = (centerY / rasterStep).roundToInt()
        val r = (radius / rasterStep).roundToInt()
        var x = 0
        var y = r
        var d = 3 - 2 * r

        if (r <= 0) return
        plotCircleOctants(xc, yc, x, y, colorIndex, rasterStep, dashed)
        while (x <= y) {
            x++
            if (d > 0) {
                y--
                d += 4 * (x - y) + 10
            } else {
                d += 4 * x + 6
            }
            plotCircleOctants(xc, yc, x, y, colorIndex, rasterStep, dashed)
        }
    }

    private fun drawHorizontalLine(x0: Int, x1: Int, yVal: Int, colorIndex: Int) {
        val h = canvas.height.toInt()
        val w = canvas.width.toInt()
        if (yVal in 0 until h) {
            val startX = x0.coerceAtLeast(0)
            val endX = x1.coerceAtMost(w - 1)
            var px = startX
            while (px <= endX) {
                canvas.setPixel(px.toFloat(), yVal.toFloat(), colorIndex)
                px++
            }
        }
    }

    fun drawAliasedFilledCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colorIndex: Int
    ) {
        val xc = centerX.roundToInt()
        val yc = centerY.roundToInt()
        val r = radius.roundToInt()
        if (r <= 0) return

        var x = 0
        var y = r
        var d = 3 - 2 * r

        drawHorizontalLine(xc - x, xc + x, yc + y, colorIndex)
        drawHorizontalLine(xc - x, xc + x, yc - y, colorIndex)
        drawHorizontalLine(xc - y, xc + y, yc + x, colorIndex)
        drawHorizontalLine(xc - y, xc + y, yc - x, colorIndex)

        while (x <= y) {
            x++
            if (d > 0) {
                y--
                d += 4 * (x - y) + 10
            } else {
                d += 4 * x + 6
            }
            drawHorizontalLine(xc - x, xc + x, yc + y, colorIndex)
            drawHorizontalLine(xc - x, xc + x, yc - y, colorIndex)
            drawHorizontalLine(xc - y, xc + y, yc + x, colorIndex)
            drawHorizontalLine(xc - y, xc + y, yc - x, colorIndex)
        }
    }

    /**
     * Draws one local left/right half of a midpoint circle, then rotates each integer point.
     * This keeps the yin-yang S-divider aliased without allocating a path or point buffer.
     */
    fun drawRotatedBresenhamHalfCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        rotationAngleIndex: Int,
        drawPositiveX: Boolean,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        val rasterStep = strokeWidth.coerceAtLeast(1f)
        val xc = (centerX / rasterStep).roundToInt()
        val yc = (centerY / rasterStep).roundToInt()
        val r = (radius / rasterStep).roundToInt()
        if (r <= 0) return

        val cosRotation = FastMath.fastCos(rotationAngleIndex)
        val sinRotation = FastMath.fastSin(rotationAngleIndex)
        var x = 0
        var y = r
        var decision = 3 - 2 * r

        plotRotatedHalfCircleOctants(xc, yc, x, y, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        while (x <= y) {
            x++
            if (decision > 0) {
                y--
                decision += 4 * (x - y) + 10
            } else {
                decision += 4 * x + 6
            }
            plotRotatedHalfCircleOctants(xc, yc, x, y, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        }
    }

    private fun plotRotatedHalfCircleOctants(
        centerX: Int,
        centerY: Int,
        x: Int,
        y: Int,
        cosRotation: Float,
        sinRotation: Float,
        drawPositiveX: Boolean,
        colorIndex: Int,
        rasterStep: Float
    ) {
        plotRotatedHalfCirclePoint(centerX, centerY, x, y, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        plotRotatedHalfCirclePoint(centerX, centerY, y, x, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        plotRotatedHalfCirclePoint(centerX, centerY, -x, y, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        plotRotatedHalfCirclePoint(centerX, centerY, -y, x, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        plotRotatedHalfCirclePoint(centerX, centerY, x, -y, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        plotRotatedHalfCirclePoint(centerX, centerY, y, -x, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        plotRotatedHalfCirclePoint(centerX, centerY, -x, -y, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
        plotRotatedHalfCirclePoint(centerX, centerY, -y, -x, cosRotation, sinRotation, drawPositiveX, colorIndex, rasterStep)
    }

    private fun plotRotatedHalfCirclePoint(
        centerX: Int,
        centerY: Int,
        localX: Int,
        localY: Int,
        cosRotation: Float,
        sinRotation: Float,
        drawPositiveX: Boolean,
        colorIndex: Int,
        rasterStep: Float
    ) {
        if ((localX >= 0) != drawPositiveX) return
        val rotatedX = (localX * cosRotation - localY * sinRotation).roundToInt()
        val rotatedY = (localX * sinRotation + localY * cosRotation).roundToInt()
        plotStrokePixel(centerX + rotatedX, centerY + rotatedY, colorIndex, rasterStep)
    }

    fun drawAliasedArc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        startDegrees: Float,
        sweepDegrees: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        val segmentCount = estimateArcSegments(radius / strokeWidth.coerceAtLeast(1f), abs(sweepDegrees))
        drawSampledArc(centerX, centerY, radius, startDegrees, sweepDegrees, segmentCount, colorIndex, strokeWidth)
    }

    fun drawAliasedProgressArc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        startDegrees: Float,
        fullSweepDegrees: Float,
        progress: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress <= 0f) return
        drawAliasedArc(centerX, centerY, radius, startDegrees, fullSweepDegrees * clampedProgress, colorIndex, strokeWidth)
    }

    fun drawRadialTickMarks(
        centerX: Float,
        centerY: Float,
        innerRadius: Float,
        outerRadius: Float,
        tickCount: Int,
        startDegrees: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f,
        majorEvery: Int = 0,
        majorExtraLength: Float = 0f
    ) {
        if (tickCount <= 0) return
        var i = 0
        while (i < tickCount) {
            val isMajor = majorEvery > 0 && i % majorEvery == 0
            val outer = if (isMajor) outerRadius + majorExtraLength else outerRadius
            val angleIdx = FastMath.degreesToIdx(startDegrees + i * (FULL_TURN_DEGREES / tickCount))
            val cosVal = FastMath.fastCos(angleIdx)
            val sinVal = FastMath.fastSin(angleIdx)
            drawAliasedLine(
                centerX + innerRadius * cosVal,
                centerY + innerRadius * sinVal,
                centerX + outer * cosVal,
                centerY + outer * sinVal,
                colorIndex,
                strokeWidth
            )
            i++
        }
    }

    fun drawRadialProgressTickMarks(
        centerX: Float,
        centerY: Float,
        innerRadius: Float,
        outerRadius: Float,
        tickCount: Int,
        activeCount: Int,
        startDegrees: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f,
        majorEvery: Int = 0,
        majorExtraLength: Float = 0f
    ) {
        if (tickCount <= 0 || activeCount <= 0) return
        val clampedActive = activeCount.coerceAtMost(tickCount)
        var i = 0
        while (i < clampedActive) {
            val isMajor = majorEvery > 0 && i % majorEvery == 0
            val outer = if (isMajor) outerRadius + majorExtraLength else outerRadius
            val angleIdx = FastMath.degreesToIdx(startDegrees + i * (FULL_TURN_DEGREES / tickCount))
            val cosVal = FastMath.fastCos(angleIdx)
            val sinVal = FastMath.fastSin(angleIdx)
            drawAliasedLine(
                centerX + innerRadius * cosVal,
                centerY + innerRadius * sinVal,
                centerX + outer * cosVal,
                centerY + outer * sinVal,
                colorIndex,
                strokeWidth
            )
            i++
        }
    }

    fun drawQuadraticBezierDeCasteljau(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        val rasterStep = strokeWidth.coerceAtLeast(1f)
        val segmentCount = estimateBezierSegments(
            (distanceEstimate(x0, y0, x1, y1) + distanceEstimate(x1, y1, x2, y2)) / rasterStep
        )
        var prevX = x0
        var prevY = y0
        var prevSnappedX = x0.roundToInt()
        var prevSnappedY = y0.roundToInt()
        var i = 1
        while (i <= segmentCount) {
            val t = i.toFloat() / segmentCount
            val ax = lerp(x0, x1, t)
            val ay = lerp(y0, y1, t)
            val bx = lerp(x1, x2, t)
            val by = lerp(y1, y2, t)
            val cx = lerp(ax, bx, t)
            val cy = lerp(ay, by, t)
            val snappedX = cx.roundToInt()
            val snappedY = cy.roundToInt()
            if (snappedX != prevSnappedX || snappedY != prevSnappedY) {
                drawAliasedLine(prevX, prevY, cx, cy, colorIndex, strokeWidth)
                prevX = cx
                prevY = cy
                prevSnappedX = snappedX
                prevSnappedY = snappedY
            }
            i++
        }
    }

    fun drawCubicBezierDeCasteljau(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        val rasterStep = strokeWidth.coerceAtLeast(1f)
        val segmentCount = estimateBezierSegments(
            (distanceEstimate(x0, y0, x1, y1) +
                distanceEstimate(x1, y1, x2, y2) +
                distanceEstimate(x2, y2, x3, y3)) / rasterStep
        )
        var prevX = x0
        var prevY = y0
        var prevSnappedX = x0.roundToInt()
        var prevSnappedY = y0.roundToInt()
        var i = 1
        while (i <= segmentCount) {
            val t = i.toFloat() / segmentCount
            val ax = lerp(x0, x1, t)
            val ay = lerp(y0, y1, t)
            val bx = lerp(x1, x2, t)
            val by = lerp(y1, y2, t)
            val cx = lerp(x2, x3, t)
            val cy = lerp(y2, y3, t)
            val dx = lerp(ax, bx, t)
            val dy = lerp(ay, by, t)
            val ex = lerp(bx, cx, t)
            val ey = lerp(by, cy, t)
            val fx = lerp(dx, ex, t)
            val fy = lerp(dy, ey, t)
            val snappedX = fx.roundToInt()
            val snappedY = fy.roundToInt()
            if (snappedX != prevSnappedX || snappedY != prevSnappedY) {
                drawAliasedLine(prevX, prevY, fx, fy, colorIndex, strokeWidth)
                prevX = fx
                prevY = fy
                prevSnappedX = snappedX
                prevSnappedY = snappedY
            }
            i++
        }
    }

    private fun drawSampledArc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        startDegrees: Float,
        sweepDegrees: Float,
        segmentCount: Int,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        val startIdx = FastMath.degreesToIdx(startDegrees)
        var prevX = centerX + radius * FastMath.fastCos(startIdx)
        var prevY = centerY + radius * FastMath.fastSin(startIdx)
        var i = 1
        while (i <= segmentCount) {
            val angleIdx = FastMath.degreesToIdx(startDegrees + sweepDegrees * (i.toFloat() / segmentCount))
            val x = centerX + radius * FastMath.fastCos(angleIdx)
            val y = centerY + radius * FastMath.fastSin(angleIdx)
            drawAliasedLine(prevX, prevY, x, y, colorIndex, strokeWidth)
            prevX = x
            prevY = y
            i++
        }
    }

    private fun drawAliasedLineInt(x0In: Int, y0In: Int, x1: Int, y1: Int, colorIndex: Int, rasterStep: Float) {
        var x0 = x0In
        var y0 = y0In
        val dx = abs(x1 - x0)
        val sx = if (x0 < x1) 1 else -1
        val dy = -abs(y1 - y0)
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            plotStrokePixel(x0, y0, colorIndex, rasterStep)
            if (x0 == x1 && y0 == y1) break
            val e2 = err * 2
            if (e2 >= dy) {
                err += dy
                x0 += sx
            }
            if (e2 <= dx) {
                err += dx
                y0 += sy
            }
        }
    }

    private fun plotCircleOctants(xc: Int, yc: Int, x: Int, y: Int, colorIndex: Int, rasterStep: Float, dashed: Boolean) {
        if (dashed && ((x + y) / 4) % 2 != 0) return
        plotStrokePixel(xc + x, yc + y, colorIndex, rasterStep)
        plotStrokePixel(xc - x, yc + y, colorIndex, rasterStep)
        plotStrokePixel(xc + x, yc - y, colorIndex, rasterStep)
        plotStrokePixel(xc - x, yc - y, colorIndex, rasterStep)
        plotStrokePixel(xc + y, yc + x, colorIndex, rasterStep)
        plotStrokePixel(xc - y, yc + x, colorIndex, rasterStep)
        plotStrokePixel(xc + y, yc - x, colorIndex, rasterStep)
        plotStrokePixel(xc - y, yc - x, colorIndex, rasterStep)
    }

    private fun plotStrokePixel(x: Int, y: Int, colorIndex: Int, rasterStep: Float) {
        val outputX = x * rasterStep
        val outputY = y * rasterStep
        if (outputX < 0f || outputX >= canvas.width || outputY < 0f || outputY >= canvas.height) return
        if (rasterStep <= 1f) {
            canvas.setPixel(outputX, outputY, colorIndex)
        } else {
            val half = rasterStep / 2f
            canvas.drawRect(outputX - half, outputY - half, rasterStep, rasterStep, colorIndex)
        }
    }

    private fun estimateArcSegments(radius: Float, sweepDegrees: Float): Int {
        val arcLengthEstimate = radius * sweepDegrees / FULL_TURN_DEGREES * 6f
        return (arcLengthEstimate / SAMPLE_INTERVAL_PIXELS).roundToInt()
            .coerceIn(MIN_ARC_SEGMENTS, MAX_ARC_SEGMENTS)
    }

    private fun estimateBezierSegments(lengthEstimate: Float): Int {
        return (lengthEstimate / SAMPLE_INTERVAL_PIXELS).roundToInt()
            .coerceIn(MIN_BEZIER_SEGMENTS, MAX_BEZIER_SEGMENTS)
    }

    private fun distanceEstimate(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        return abs(x1 - x0) + abs(y1 - y0)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
}
