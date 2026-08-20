package com.example.timeboxvibe.engine.core

/** UI-owned button painter. Graphics remains unaware of widget identity and behavior. */
fun ScaledProceduralRenderer.drawButton(
    text: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    isClicked: Boolean,
    isHovered: Boolean = false,
    allowTextStacking: Boolean = false
) {
    val bgColor = if (isClicked || isHovered) PaletteIndices.PANEL else PaletteIndices.PANEL_DARK
    val textColor = if (isClicked || isHovered) PaletteIndices.TEXT_PRIMARY else PaletteIndices.SECONDARY
    val frameColor = if (isClicked || isHovered) PaletteIndices.BORDER_BRIGHT else PaletteIndices.SECONDARY

    val border = CANONICAL_UI_UNIT / BUTTON_BORDER_CELLS_DEN
    fillRectDither(
        x + border,
        y + border,
        x + w - border,
        y + h - border,
        bgColor,
        bgColor,
        SoftDitherPattern.SOLID
    )
    strokeRectFrame(x, y, w, h, frameColor, kind = VectorFrameKind.SMALL)

    val textScale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    if (isClicked || isHovered) {
        val indicatorHeight = ScaledProceduralRenderer.measureTextHeight(textScale)
        drawGlyph(
            ACTIVE_INDICATOR_GLYPH,
            x + CANONICAL_UI_UNIT / 2f,
            y + (h - indicatorHeight) / 2f,
            textColor,
            scale = textScale,
            startX = x,
            startY = y,
            clipWidth = w.toInt(),
            clipHeight = h.toInt()
        )
    }
    if (allowTextStacking) {
        val textAreaX = x + CANONICAL_UI_UNIT / 2f
        val textAreaWidth = maxOf(CANONICAL_UI_UNIT.toFloat(), w - CANONICAL_UI_UNIT.toFloat())
        val textHeight = ProceduralTextRenderer.measureWrappedHeight(text, textAreaWidth, textScale)
        val textY = y + (h - textHeight) / 2f
        ProceduralTextRenderer.drawWrapped(
            renderer = this,
            text = text,
            x = textAreaX,
            y = textY,
            maxWidth = textAreaWidth,
            color = textColor,
            scale = textScale,
            alignment = ProceduralTextRenderer.ALIGN_CENTER
        )
    } else {
        val textWidth = ScaledProceduralRenderer.measureTextWidth(text, textScale)
        val textHeight = ScaledProceduralRenderer.measureTextHeight(textScale)
        drawText(
            text = text,
            destX = x + (w - textWidth) / 2f,
            destY = y + (h - textHeight) / 2f,
            colorIndex = textColor,
            scale = textScale,
            startX = x,
            startY = y,
            clipWidth = w.toInt(),
            clipHeight = h.toInt()
        )
    }
}

private const val ACTIVE_INDICATOR_GLYPH = '＞'
private const val BUTTON_BORDER_CELLS_DEN = 8f
