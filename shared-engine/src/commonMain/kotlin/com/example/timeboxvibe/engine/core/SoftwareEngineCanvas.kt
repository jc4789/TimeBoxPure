package com.example.timeboxvibe.engine.core

import kotlin.math.roundToInt

/** Routes procedural renderer commands through one indexed software raster path. */
class SoftwareEngineCanvas(
    primitiveWidth: Int,
    primitiveHeight: Int
) : EngineCanvas {
    val framebuffer = IndexedFramebuffer(primitiveWidth, primitiveHeight)
    val graphics = SoftwareGraphics(framebuffer)
    private val aliased = AliasedVectorLayer(this)

    override val width: Float
        get() = framebuffer.width.toFloat()

    override val height: Float
        get() = framebuffer.height.toFloat()

    private var drawEnabled = true

    fun resize(primitiveWidth: Int, primitiveHeight: Int) {
        framebuffer.resize(primitiveWidth, primitiveHeight)
    }

    override fun setDrawAlpha(alphaByte: Int) {
        drawEnabled = alphaByte > 0
    }

    override fun clear(colorIndex: Int) {
        graphics.clear(colorIndex)
    }

    override fun setPixel(x: Float, y: Float, colorIndex: Int) {
        if (!drawEnabled) return
        graphics.pixel(x.roundToInt(), y.roundToInt(), colorIndex)
    }

    override fun drawLine(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colorIndex: Int,
        strokeWidth: Float
    ) {
        if (!drawEnabled) return
        aliased.drawAliasedLine(x0, y0, x1, y1, colorIndex, strokeWidth)
    }

    override fun drawRect(x: Float, y: Float, w: Float, h: Float, colorIndex: Int) {
        if (!drawEnabled) return
        graphics.fillRect(x.roundToInt(), y.roundToInt(), w.roundToInt(), h.roundToInt(), colorIndex)
    }

    override fun drawCircle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colorIndex: Int,
        strokeWidth: Float,
        dashed: Boolean
    ) {
        if (!drawEnabled) return
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
        if (!drawEnabled) return
        graphics.ditherRect(
            x0.roundToInt(),
            y0.roundToInt(),
            x1.roundToInt(),
            y1.roundToInt(),
            primaryIndex,
            secondaryIndex,
            pattern
        )
    }
}
