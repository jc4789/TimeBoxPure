package com.example.timeboxvibe.engine.core

import kotlin.math.abs
import kotlin.math.roundToInt

object VectorPathOp {
    const val MOVE = 0
    const val LINE = 1
    const val QUAD = 2
    const val CUBIC = 3
    const val CLOSE = 4
}

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
        /** Chord deviation, in logical pixels, that stops De Casteljau splits. */
        private const val BEZIER_FLATNESS_PIXELS = 1f
        /** Matches tools/math_oracles/de_casteljau_oracle.py max_depth. */
        private const val BEZIER_MAX_SUBDIVISION_DEPTH = 16
        private const val BEZIER_STACK_CAPACITY = BEZIER_MAX_SUBDIVISION_DEPTH + 1
        private const val DE_CASTELJAU_MIDPOINT = 0.5f
        private const val CUBIC_CONTROL_POINTS = 4
        private const val QUAD_CONTROL_POINTS = 3
    }

    private val cubicX = FloatArray(BEZIER_STACK_CAPACITY * CUBIC_CONTROL_POINTS)
    private val cubicY = FloatArray(BEZIER_STACK_CAPACITY * CUBIC_CONTROL_POINTS)
    private val cubicDepth = IntArray(BEZIER_STACK_CAPACITY)
    private val quadX = FloatArray(BEZIER_STACK_CAPACITY * QUAD_CONTROL_POINTS)
    private val quadY = FloatArray(BEZIER_STACK_CAPACITY * QUAD_CONTROL_POINTS)
    private val quadDepth = IntArray(BEZIER_STACK_CAPACITY)
    private var clipActive = false
    private var clipX0 = 0f
    private var clipY0 = 0f
    private var clipX1 = 0f
    private var clipY1 = 0f

    fun setClipRect(x0: Float, y0: Float, x1: Float, y1: Float) {
        clipActive = true
        clipX0 = minOf(x0, x1)
        clipY0 = minOf(y0, y1)
        clipX1 = maxOf(x0, x1)
        clipY1 = maxOf(y0, y1)
    }

    fun clearClipRect() {
        clipActive = false
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
        var stackCount = 0
        stackCount = pushQuad(stackCount, x0, y0, x1, y1, x2, y2, 0)
        var prevX = x0
        var prevY = y0
        var prevSnappedX = x0.roundToInt()
        var prevSnappedY = y0.roundToInt()
        while (stackCount > 0) {
            stackCount--
            val base = stackCount * QUAD_CONTROL_POINTS
            val qx0 = quadX[base]
            val qy0 = quadY[base]
            val qx1 = quadX[base + 1]
            val qy1 = quadY[base + 1]
            val qx2 = quadX[base + 2]
            val qy2 = quadY[base + 2]
            val depth = quadDepth[stackCount]
            if (depth >= BEZIER_MAX_SUBDIVISION_DEPTH || quadIsFlat(qx0, qy0, qx1, qy1, qx2, qy2)) {
                val snappedX = qx2.roundToInt()
                val snappedY = qy2.roundToInt()
                if (snappedX != prevSnappedX || snappedY != prevSnappedY) {
                    drawAliasedLine(prevX, prevY, qx2, qy2, colorIndex, strokeWidth)
                    prevX = qx2
                    prevY = qy2
                    prevSnappedX = snappedX
                    prevSnappedY = snappedY
                }
            } else {
                val ax = lerp(qx0, qx1, DE_CASTELJAU_MIDPOINT)
                val ay = lerp(qy0, qy1, DE_CASTELJAU_MIDPOINT)
                val bx = lerp(qx1, qx2, DE_CASTELJAU_MIDPOINT)
                val by = lerp(qy1, qy2, DE_CASTELJAU_MIDPOINT)
                val cx = lerp(ax, bx, DE_CASTELJAU_MIDPOINT)
                val cy = lerp(ay, by, DE_CASTELJAU_MIDPOINT)
                val nextDepth = depth + 1
                stackCount = pushQuad(stackCount, cx, cy, bx, by, qx2, qy2, nextDepth)
                stackCount = pushQuad(stackCount, qx0, qy0, ax, ay, cx, cy, nextDepth)
            }
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
        var stackCount = 0
        stackCount = pushCubic(stackCount, x0, y0, x1, y1, x2, y2, x3, y3, 0)
        var prevX = x0
        var prevY = y0
        var prevSnappedX = x0.roundToInt()
        var prevSnappedY = y0.roundToInt()
        while (stackCount > 0) {
            stackCount--
            val base = stackCount * CUBIC_CONTROL_POINTS
            val cx0 = cubicX[base]
            val cy0 = cubicY[base]
            val cx1 = cubicX[base + 1]
            val cy1 = cubicY[base + 1]
            val cx2 = cubicX[base + 2]
            val cy2 = cubicY[base + 2]
            val cx3 = cubicX[base + 3]
            val cy3 = cubicY[base + 3]
            val depth = cubicDepth[stackCount]
            if (depth >= BEZIER_MAX_SUBDIVISION_DEPTH || cubicIsFlat(cx0, cy0, cx1, cy1, cx2, cy2, cx3, cy3)) {
                val snappedX = cx3.roundToInt()
                val snappedY = cy3.roundToInt()
                if (snappedX != prevSnappedX || snappedY != prevSnappedY) {
                    drawAliasedLine(prevX, prevY, cx3, cy3, colorIndex, strokeWidth)
                    prevX = cx3
                    prevY = cy3
                    prevSnappedX = snappedX
                    prevSnappedY = snappedY
                }
            } else {
                val ax = lerp(cx0, cx1, DE_CASTELJAU_MIDPOINT)
                val ay = lerp(cy0, cy1, DE_CASTELJAU_MIDPOINT)
                val bx = lerp(cx1, cx2, DE_CASTELJAU_MIDPOINT)
                val by = lerp(cy1, cy2, DE_CASTELJAU_MIDPOINT)
                val cx = lerp(cx2, cx3, DE_CASTELJAU_MIDPOINT)
                val cy = lerp(cy2, cy3, DE_CASTELJAU_MIDPOINT)
                val dx = lerp(ax, bx, DE_CASTELJAU_MIDPOINT)
                val dy = lerp(ay, by, DE_CASTELJAU_MIDPOINT)
                val ex = lerp(bx, cx, DE_CASTELJAU_MIDPOINT)
                val ey = lerp(by, cy, DE_CASTELJAU_MIDPOINT)
                val fx = lerp(dx, ex, DE_CASTELJAU_MIDPOINT)
                val fy = lerp(dy, ey, DE_CASTELJAU_MIDPOINT)
                val nextDepth = depth + 1
                stackCount = pushCubic(stackCount, fx, fy, ex, ey, cx, cy, cx3, cy3, nextDepth)
                stackCount = pushCubic(stackCount, cx0, cy0, ax, ay, dx, dy, fx, fy, nextDepth)
            }
        }
    }

    /**
     * Stroke a hardcoded unit path after the 2x2 placement
     * `out = origin + [xx xy; yx yy] * unit`.
     */
    fun strokePath(
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
        var coordIndex = 0
        var opIndex = 0
        var hasPoint = false
        var startX = 0f
        var startY = 0f
        var curX = 0f
        var curY = 0f
        while (opIndex < ops.size) {
            val op = ops[opIndex]
            when (op) {
                VectorPathOp.MOVE -> {
                    if (coordIndex + 1 >= coords.size) return
                    val x = coords[coordIndex]
                    val y = coords[coordIndex + 1]
                    coordIndex += 2
                    curX = originX + xx * x + xy * y
                    curY = originY + yx * x + yy * y
                    startX = curX
                    startY = curY
                    hasPoint = true
                }
                VectorPathOp.LINE -> {
                    if (coordIndex + 1 >= coords.size) return
                    val x = coords[coordIndex]
                    val y = coords[coordIndex + 1]
                    coordIndex += 2
                    val nextX = originX + xx * x + xy * y
                    val nextY = originY + yx * x + yy * y
                    if (hasPoint) {
                        drawAliasedLine(curX, curY, nextX, nextY, colorIndex, strokeWidth)
                    }
                    curX = nextX
                    curY = nextY
                    if (!hasPoint) {
                        startX = curX
                        startY = curY
                        hasPoint = true
                    }
                }
                VectorPathOp.QUAD -> {
                    if (coordIndex + 3 >= coords.size) return
                    val x1 = coords[coordIndex]
                    val y1 = coords[coordIndex + 1]
                    val x2 = coords[coordIndex + 2]
                    val y2 = coords[coordIndex + 3]
                    coordIndex += 4
                    val c1x = originX + xx * x1 + xy * y1
                    val c1y = originY + yx * x1 + yy * y1
                    val nextX = originX + xx * x2 + xy * y2
                    val nextY = originY + yx * x2 + yy * y2
                    if (hasPoint) {
                        drawQuadraticBezierDeCasteljau(curX, curY, c1x, c1y, nextX, nextY, colorIndex, strokeWidth)
                    }
                    curX = nextX
                    curY = nextY
                    if (!hasPoint) {
                        startX = curX
                        startY = curY
                        hasPoint = true
                    }
                }
                VectorPathOp.CUBIC -> {
                    if (coordIndex + 5 >= coords.size) return
                    val x1 = coords[coordIndex]
                    val y1 = coords[coordIndex + 1]
                    val x2 = coords[coordIndex + 2]
                    val y2 = coords[coordIndex + 3]
                    val x3 = coords[coordIndex + 4]
                    val y3 = coords[coordIndex + 5]
                    coordIndex += 6
                    val c1x = originX + xx * x1 + xy * y1
                    val c1y = originY + yx * x1 + yy * y1
                    val c2x = originX + xx * x2 + xy * y2
                    val c2y = originY + yx * x2 + yy * y2
                    val nextX = originX + xx * x3 + xy * y3
                    val nextY = originY + yx * x3 + yy * y3
                    if (hasPoint) {
                        drawCubicBezierDeCasteljau(
                            curX, curY, c1x, c1y, c2x, c2y, nextX, nextY, colorIndex, strokeWidth
                        )
                    }
                    curX = nextX
                    curY = nextY
                    if (!hasPoint) {
                        startX = curX
                        startY = curY
                        hasPoint = true
                    }
                }
                VectorPathOp.CLOSE -> {
                    if (hasPoint) {
                        drawAliasedLine(curX, curY, startX, startY, colorIndex, strokeWidth)
                        curX = startX
                        curY = startY
                    }
                }
                else -> return
            }
            opIndex++
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
        if (clipActive &&
            (outputX < clipX0 || outputX >= clipX1 || outputY < clipY0 || outputY >= clipY1)
        ) {
            return
        }
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

    private fun pushCubic(
        stackCount: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        depth: Int
    ): Int {
        if (stackCount >= BEZIER_STACK_CAPACITY) return stackCount
        val base = stackCount * CUBIC_CONTROL_POINTS
        cubicX[base] = x0
        cubicY[base] = y0
        cubicX[base + 1] = x1
        cubicY[base + 1] = y1
        cubicX[base + 2] = x2
        cubicY[base + 2] = y2
        cubicX[base + 3] = x3
        cubicY[base + 3] = y3
        cubicDepth[stackCount] = depth
        return stackCount + 1
    }

    private fun pushQuad(
        stackCount: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        depth: Int
    ): Int {
        if (stackCount >= BEZIER_STACK_CAPACITY) return stackCount
        val base = stackCount * QUAD_CONTROL_POINTS
        quadX[base] = x0
        quadY[base] = y0
        quadX[base + 1] = x1
        quadY[base + 1] = y1
        quadX[base + 2] = x2
        quadY[base + 2] = y2
        quadDepth[stackCount] = depth
        return stackCount + 1
    }

    private fun cubicIsFlat(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float
    ): Boolean {
        return controlIsFlat(x0, y0, x3, y3, x1, y1) && controlIsFlat(x0, y0, x3, y3, x2, y2)
    }

    private fun quadIsFlat(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Boolean {
        return controlIsFlat(x0, y0, x2, y2, x1, y1)
    }

    private fun controlIsFlat(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        px: Float,
        py: Float
    ): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val chordSq = dx * dx + dy * dy
        val toleranceSq = BEZIER_FLATNESS_PIXELS * BEZIER_FLATNESS_PIXELS
        if (chordSq <= 0f) {
            val ox = px - ax
            val oy = py - ay
            return ox * ox + oy * oy <= toleranceSq
        }
        val cross = dy * px - dx * py + bx * ay - by * ax
        return cross * cross <= toleranceSq * chordSq
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
}
