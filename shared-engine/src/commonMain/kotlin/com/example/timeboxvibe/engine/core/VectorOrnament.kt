package com.example.timeboxvibe.engine.core

/** Which vector vocabulary to instance on a logical rectangle. */
object VectorFrameKind {
    /** Single stroke + corner hooks. Buttons, steppers, fields. */
    const val SMALL = 0
    /** Nested stroke + corner scrolls + sparse edge arches. Cards and sheets. */
    const val PANEL = 1
}

/**
 * Unit-space aliased vector ornaments.
 * Coordinates are fractions of the instance size. Placement is a 2x2 in logical pixels.
 */
object VectorOrnament {
    private const val U = CANONICAL_UI_UNIT
    private const val INNER_FRAME_DIVISOR = 6
    private const val INNER_PETAL_SCALE = 0.38f
    private const val HALF = 0.5f
    private const val MIN_CORNER_PIXELS = 3f
    private const val SMALL_CORNER_CELLS_DEN = 3
    private const val PANEL_CORNER_CELLS_DEN = 2
    private const val EDGE_ARCH_MIN_SPAN_CELLS = 3
    private const val EDGE_ARCH_TRIPLE_SPAN_CELLS = 8
    private const val EDGE_LEFT = 0
    private const val EDGE_TOP = 1
    private const val EDGE_RIGHT = 2
    private const val EDGE_BOTTOM = 3

    private val cornerOps = intArrayOf(
        VectorPathOp.MOVE,
        VectorPathOp.LINE,
        VectorPathOp.CUBIC,
        VectorPathOp.CUBIC,
        VectorPathOp.CUBIC,
        VectorPathOp.CUBIC,
        VectorPathOp.LINE,
        VectorPathOp.MOVE,
        VectorPathOp.CUBIC,
        VectorPathOp.CUBIC,
        VectorPathOp.CLOSE
    )
    private val cornerCoords = floatArrayOf(
        0.00f, 1.00f,
        0.00f, 0.70f,
        0.00f, 0.38f, 0.04f, 0.14f, 0.26f, 0.10f,
        0.52f, 0.06f, 0.68f, 0.26f, 0.52f, 0.46f,
        0.34f, 0.66f, 0.08f, 0.58f, 0.10f, 0.30f,
        0.12f, 0.08f, 0.40f, 0.02f, 0.74f, 0.00f,
        1.00f, 0.00f,
        0.22f, 0.22f,
        0.32f, 0.08f, 0.52f, 0.10f, 0.44f, 0.26f,
        0.36f, 0.36f, 0.22f, 0.32f, 0.22f, 0.22f
    )

    private val hookOps = intArrayOf(
        VectorPathOp.MOVE,
        VectorPathOp.CUBIC,
        VectorPathOp.LINE
    )
    private val hookCoords = floatArrayOf(
        0.00f, 0.90f,
        0.00f, 0.28f, 0.28f, 0.00f, 0.90f, 0.00f,
        1.00f, 0.00f
    )

    private val archOps = intArrayOf(
        VectorPathOp.MOVE,
        VectorPathOp.CUBIC,
        VectorPathOp.CUBIC
    )
    private val archCoords = floatArrayOf(
        0.00f, 0.00f,
        0.22f, 0.00f, 0.28f, 0.55f, 0.50f, 0.62f,
        0.72f, 0.55f, 0.78f, 0.00f, 1.00f, 0.00f
    )

    private val petalOps = intArrayOf(
        VectorPathOp.MOVE,
        VectorPathOp.CUBIC,
        VectorPathOp.CUBIC,
        VectorPathOp.CLOSE
    )
    private val petalCoords = floatArrayOf(
        0.00f, -0.12f,
        0.46f, -0.30f, 0.36f, -0.78f, 0.00f, -1.00f,
        -0.36f, -0.78f, -0.46f, -0.30f, 0.00f, -0.12f
    )

    fun strokeRectFrame(
        layer: AliasedVectorLayer,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f,
        kind: Int = VectorFrameKind.PANEL
    ) {
        if (width <= 0f || height <= 0f) return
        val right = x + width
        val bottom = y + height
        strokeRect(layer, x, y, width, height, colorIndex, strokeWidth)

        val shortest = minOf(width, height)
        if (kind == VectorFrameKind.SMALL) {
            val hookSize = minOf((U / SMALL_CORNER_CELLS_DEN).toFloat(), shortest * HALF)
            if (hookSize >= MIN_CORNER_PIXELS) {
                strokeCorners(layer, x, y, right, bottom, hookSize, hookOps, hookCoords, colorIndex, strokeWidth)
            }
            return
        }

        val cornerSize = minOf(U.toFloat(), shortest / PANEL_CORNER_CELLS_DEN)
        if (cornerSize < MIN_CORNER_PIXELS) return

        val inset = maxOf(2f, cornerSize / INNER_FRAME_DIVISOR)
        if (width > inset * 2f && height > inset * 2f) {
            strokeRect(layer, x + inset, y + inset, width - inset * 2f, height - inset * 2f, colorIndex, strokeWidth)
        }
        strokeCorners(layer, x, y, right, bottom, cornerSize, cornerOps, cornerCoords, colorIndex, strokeWidth)
        strokeEdgeArches(layer, x + cornerSize, y, width - cornerSize * 2f, cornerSize, EDGE_TOP, colorIndex, strokeWidth)
        strokeEdgeArches(layer, x + cornerSize, bottom, width - cornerSize * 2f, cornerSize, EDGE_BOTTOM, colorIndex, strokeWidth)
        strokeEdgeArches(layer, x, y + cornerSize, height - cornerSize * 2f, cornerSize, EDGE_LEFT, colorIndex, strokeWidth)
        strokeEdgeArches(layer, right, y + cornerSize, height - cornerSize * 2f, cornerSize, EDGE_RIGHT, colorIndex, strokeWidth)
    }

    fun strokeMedallion(
        layer: AliasedVectorLayer,
        centerX: Float,
        centerY: Float,
        radius: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        if (radius < MIN_CORNER_PIXELS) return
        strokePetals(layer, centerX, centerY, radius, colorIndex, strokeWidth)
        val inner = radius * INNER_PETAL_SCALE
        if (inner >= MIN_CORNER_PIXELS) {
            strokePetals(layer, centerX, centerY, inner, colorIndex, strokeWidth)
        }
    }

    fun strokeHorizontalRule(
        layer: AliasedVectorLayer,
        x0: Float,
        y: Float,
        x1: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        if (x1 <= x0) return
        layer.drawAliasedLine(x0, y, x1, y, colorIndex, strokeWidth)
        val span = x1 - x0
        val archWidth = minOf(U.toFloat() * 2f, span / 3f)
        if (archWidth < (U / 2).toFloat()) return
        val originX = x0 + (span - archWidth) * HALF
        layer.strokePath(
            archOps, archCoords,
            originX, y, archWidth, 0f, 0f, -archWidth * HALF,
            colorIndex, strokeWidth
        )
    }

    fun strokeVerticalRule(
        layer: AliasedVectorLayer,
        x: Float,
        y0: Float,
        y1: Float,
        colorIndex: Int,
        strokeWidth: Float = 1f
    ) {
        if (y1 <= y0) return
        layer.drawAliasedLine(x, y0, x, y1, colorIndex, strokeWidth)
        val span = y1 - y0
        val archWidth = minOf(U.toFloat() * 2f, span / 3f)
        if (archWidth < (U / 2).toFloat()) return
        val originY = y0 + (span - archWidth) * HALF
        layer.strokePath(
            archOps, archCoords,
            x, originY, 0f, archWidth * HALF, archWidth, 0f,
            colorIndex, strokeWidth
        )
    }

    fun drawLattice(
        renderer: ScaledProceduralRenderer,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colorIndex: Int
    ) {
        val step = U.toFloat()
        if (x1 <= x0 || y1 <= y0 || step <= 0f) return
        var gy = (y0 / step).toInt() * step
        if (gy < y0) gy += step
        while (gy < y1) {
            var gx = (x0 / step).toInt() * step
            if (gx < x0) gx += step
            while (gx < x1) {
                renderer.drawRect(gx, gy, 1f, 1f, colorIndex)
                gx += step
            }
            gy += step
        }
    }

    private fun strokeRect(
        layer: AliasedVectorLayer,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        val right = x + width
        val bottom = y + height
        layer.drawAliasedLine(x, y, right, y, colorIndex, strokeWidth)
        layer.drawAliasedLine(right, y, right, bottom, colorIndex, strokeWidth)
        layer.drawAliasedLine(right, bottom, x, bottom, colorIndex, strokeWidth)
        layer.drawAliasedLine(x, bottom, x, y, colorIndex, strokeWidth)
    }

    private fun strokeCorners(
        layer: AliasedVectorLayer,
        x: Float,
        y: Float,
        right: Float,
        bottom: Float,
        size: Float,
        ops: IntArray,
        coords: FloatArray,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        layer.strokePath(ops, coords, x, y, size, 0f, 0f, size, colorIndex, strokeWidth)
        layer.strokePath(ops, coords, right, y, -size, 0f, 0f, size, colorIndex, strokeWidth)
        layer.strokePath(ops, coords, right, bottom, -size, 0f, 0f, -size, colorIndex, strokeWidth)
        layer.strokePath(ops, coords, x, bottom, size, 0f, 0f, -size, colorIndex, strokeWidth)
    }

    private fun strokePetals(
        layer: AliasedVectorLayer,
        centerX: Float,
        centerY: Float,
        radius: Float,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        layer.strokePath(petalOps, petalCoords, centerX, centerY, radius, 0f, 0f, radius, colorIndex, strokeWidth)
        layer.strokePath(petalOps, petalCoords, centerX, centerY, 0f, -radius, radius, 0f, colorIndex, strokeWidth)
        layer.strokePath(petalOps, petalCoords, centerX, centerY, -radius, 0f, 0f, -radius, colorIndex, strokeWidth)
        layer.strokePath(petalOps, petalCoords, centerX, centerY, 0f, radius, -radius, 0f, colorIndex, strokeWidth)
    }

    private fun strokeEdgeArches(
        layer: AliasedVectorLayer,
        originX: Float,
        originY: Float,
        span: Float,
        size: Float,
        edge: Int,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        val minSpan = (U * EDGE_ARCH_MIN_SPAN_CELLS).toFloat()
        if (span < minSpan || size < MIN_CORNER_PIXELS) return
        val archWidth = minOf(size * 2f, span / 3f)
        val tripleSpan = (U * EDGE_ARCH_TRIPLE_SPAN_CELLS).toFloat()
        val count = if (span >= tripleSpan) 3 else 1
        val used = count * archWidth
        val gap = if (count > 1) (span - used) / (count + 1) else (span - used) * HALF
        var i = 0
        while (i < count) {
            val along = if (count > 1) gap * (i + 1) + archWidth * i else gap
            placeArch(layer, originX, originY, along, archWidth, size, edge, colorIndex, strokeWidth)
            i++
        }
    }

    private fun placeArch(
        layer: AliasedVectorLayer,
        originX: Float,
        originY: Float,
        along: Float,
        archWidth: Float,
        size: Float,
        edge: Int,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        val inward = size * HALF
        when (edge) {
            EDGE_TOP -> layer.strokePath(
                archOps, archCoords,
                originX + along, originY, archWidth, 0f, 0f, inward,
                colorIndex, strokeWidth
            )
            EDGE_BOTTOM -> layer.strokePath(
                archOps, archCoords,
                originX + along, originY, archWidth, 0f, 0f, -inward,
                colorIndex, strokeWidth
            )
            EDGE_LEFT -> layer.strokePath(
                archOps, archCoords,
                originX, originY + along, 0f, inward, archWidth, 0f,
                colorIndex, strokeWidth
            )
            else -> layer.strokePath(
                archOps, archCoords,
                originX, originY + along, 0f, -inward, archWidth, 0f,
                colorIndex, strokeWidth
            )
        }
    }
}
