package com.example.timeboxvibe.engine.core

object ProceduralIconRenderer {
    private const val ICON_SIZE = 32
    private const val WHITE_ARGB = 0xFFFFFFFF.toInt()
    private const val BLACK_ARGB = 0xFF000000.toInt()
    private const val ERROR_RED_ARGB = 0xFFCC0000.toInt()
    private const val FIRE_RED_ARGB = 0xFFFF2200.toInt()
    private const val GOLD_ARGB = 0xFFFFEE55.toInt()
    private const val YELLOW_ARGB = 0xFFFFCC00.toInt()
    private const val BLUE_ARGB = 0xFF00AAFF.toInt()
    private const val BROWN_ARGB = 0xFF8B4513.toInt()

    fun draw(
        renderer: ScaledProceduralRenderer,
        iconName: String,
        destX: Float,
        destY: Float,
        scale: Int,
        primaryColor: Int,
        onBackgroundColor: Int,
        surfaceColor: Int
    ) {
        var y = 0
        while (y < ICON_SIZE) {
            var x = 0
            while (x < ICON_SIZE) {
                val color = getPixelColor(iconName, x, y, primaryColor, onBackgroundColor, surfaceColor)
                if (color != 0) {
                    renderer.drawRect(
                        destX + x * scale,
                        destY + y * scale,
                        scale.toFloat(),
                        scale.toFloat(),
                        mapIconColor(color, primaryColor, onBackgroundColor, surfaceColor)
                    )
                }
                x++
            }
            y++
        }
    }

    private fun mapIconColor(color: Int, primaryColor: Int, onBackgroundColor: Int, surfaceColor: Int): Int {
        return when (color) {
            primaryColor -> primaryColor
            onBackgroundColor -> onBackgroundColor
            surfaceColor -> surfaceColor
            WHITE_ARGB -> PaletteIndices.WHITE
            BLACK_ARGB -> PaletteIndices.BLACK
            ERROR_RED_ARGB, FIRE_RED_ARGB -> PaletteIndices.ERROR
            GOLD_ARGB, YELLOW_ARGB -> PaletteIndices.SECONDARY
            BLUE_ARGB -> PaletteIndices.PRIMARY
            BROWN_ARGB -> PaletteIndices.SECONDARY
            else -> primaryColor
        }
    }
}

object ProceduralTextRenderer {
    private const val U = 16f
    private const val CUSTOM_ID_SUFFIX_CHARS = 6
    private const val SYS_ID_PREFIX_CHARS = 8

    fun drawRaw(
        renderer: ScaledProceduralRenderer,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ) {
        val charW = U * scale
        var i = 0
        while (i < text.length) {
            renderer.drawGlyph(text[i], x + i * charW, y, color, scale = scale)
            i++
        }
    }

    fun drawUpperClipped(
        renderer: ScaledProceduralRenderer,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Int,
        clipX: Float,
        clipY: Float,
        clipW: Float,
        clipH: Float
    ) {
        val charW = U * scale
        var i = 0
        while (i < text.length) {
            val drawX = x + i * charW
            if (drawX + charW > clipX + clipW) return
            val c = text[i]
            renderer.drawGlyph(
                upperAscii(c),
                drawX,
                y,
                color,
                scale = scale,
                startX = clipX,
                startY = clipY,
                clipWidth = clipW.toInt(),
                clipHeight = clipH.toInt()
            )
            i++
        }
    }

    fun drawPresetId(
        renderer: ScaledProceduralRenderer,
        id: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Int,
        clipX: Float,
        clipY: Float,
        clipW: Float,
        clipH: Float
    ) {
        val charW = U * scale
        renderer.drawText("SYS_ID: ", x, y, color, scale = scale, startX = clipX, startY = clipY, clipWidth = clipW.toInt(), clipHeight = clipH.toInt())
        val suffixStart = if (id.length > CUSTOM_ID_SUFFIX_CHARS) id.length - CUSTOM_ID_SUFFIX_CHARS else 0
        var sourceIdx = suffixStart
        var outIdx = SYS_ID_PREFIX_CHARS
        while (sourceIdx < id.length) {
            val drawX = x + outIdx * charW
            if (drawX + charW > clipX + clipW) return
            renderer.drawGlyph(
                upperAscii(id[sourceIdx]),
                drawX,
                y,
                color,
                scale = scale,
                startX = clipX,
                startY = clipY,
                clipWidth = clipW.toInt(),
                clipHeight = clipH.toInt()
            )
            sourceIdx++
            outIdx++
        }
    }

    private fun upperAscii(c: Char): Char {
        return if (c in 'a'..'z') (c.code - 32).toChar() else c
    }
}
