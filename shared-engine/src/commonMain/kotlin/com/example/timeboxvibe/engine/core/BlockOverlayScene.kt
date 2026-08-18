package com.example.timeboxvibe.engine.core

private const val U = CANONICAL_UI_UNIT

object BlockOverlayScene : Scene {
    var onReturnClicked: (() -> Unit)? = null

    override fun onEnter(payload: Any?) {}
    override fun onExit() {}
    override fun update(dt: Float) {}

    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val theme = "reimu"
        // This will setup the palette in Pc98GraphicsHardware dynamically
        EngineThemes.getColors(theme, false)
        
        renderer.fillRectDither(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)

        val contentX = U.toFloat()
        val contentW = maxOf(U.toFloat(), logicalWidth - (U * 2).toFloat())
        val title = "ＦＯＣＵＳ　ＭＯＤＥ　ＡＣＴＩＶＥ"
        val subtitle = "Ｇｅｔ　ｂａｃｋ　ｔｏ　ｗｏｒｋ．"
        val titleH = ProceduralTextRenderer.measureHeadingHeight(title, contentW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val titleY = logicalHeight * 0.25f
        ProceduralTextRenderer.drawHeading(renderer, title, contentX, titleY, contentW, PaletteIndices.PRIMARY, ScaledProceduralRenderer.TEXT_SCALE_HEADER, ProceduralTextRenderer.ALIGN_CENTER)
        val subtitleY = titleY + titleH + U.toFloat()
        ProceduralTextRenderer.drawWrapped(renderer, subtitle, contentX, subtitleY, contentW, PaletteIndices.SECONDARY, alignment = ProceduralTextRenderer.ALIGN_CENTER)

        val btnW = blockButtonWidth(logicalWidth)
        val btnH = blockButtonHeight(logicalWidth, logicalHeight)
        val btnX = (logicalWidth - btnW) / 2f
        val btnY = blockButtonY(logicalHeight, btnH)
        
        renderer.drawButton("ＲＥＴＵＲＮ　ＴＯ　ＴＩＭＥＢＯＸ", btnX, btnY, btnW, btnH, isClicked = false, allowTextStacking = true)
    }

    override fun onInput(inputCode: Int) {}

    override fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        if (action == TouchAction.DOWN) {
            val logicalWidth = SceneManager.logicalWidth
            val logicalHeight = SceneManager.logicalHeight
            val btnW = blockButtonWidth(logicalWidth)
            val btnH = blockButtonHeight(logicalWidth, logicalHeight)
            val btnX = (logicalWidth - btnW) / 2f
            val btnY = blockButtonY(logicalHeight, btnH)
            
            if (x >= btnX && x <= btnX + btnW && y >= btnY && y <= btnY + btnH) {
                onReturnClicked?.invoke()
            }
        }
    }

    private fun blockButtonWidth(logicalWidth: Float): Float {
        val textWidth = ScaledProceduralRenderer.measureTextWidth("ＲＥＴＵＲＮ　ＴＯ　ＴＩＭＥＢＯＸ") + U.toFloat()
        return minOf(logicalWidth - (U * 2).toFloat(), maxOf(logicalWidth * 0.4f, ((U * 12) + (U / 2)).toFloat(), textWidth))
    }

    private fun blockButtonHeight(logicalWidth: Float, logicalHeight: Float): Float {
        return ScaledProceduralRenderer.measureButtonHeight("ＲＥＴＵＲＮ　ＴＯ　ＴＩＭＥＢＯＸ", blockButtonWidth(logicalWidth), maxOf(logicalHeight * 0.1f, (U * 2).toFloat()), allowTextStacking = true)
    }

    private fun blockButtonY(logicalHeight: Float, buttonHeight: Float): Float {
        return minOf(logicalHeight * 0.7f, logicalHeight - buttonHeight - U.toFloat())
    }
}

