package com.example.timeboxvibe.engine.core

enum class HudAction {
    NONE,
    SELECT_TAB_TIMER,
    SELECT_TAB_CARDS,
    SELECT_TAB_BOMB,
    SELECT_TAB_SYSTEM,
    TIMER_START_STOP,
    TIMER_RESET,
    TIMER_SKIP,
    TOGGLE_TICKS,
    TOGGLE_VIBE,
    FOCUS_INPUT
}

object RetroHudComponent {
    private const val U = 16f
    private const val BUTTON_BORDER = U / 8f
    private const val ICON_SIZE = U * 2f
    private const val ICON_SCALE = 1
    private const val HUD_RATIO_NUM = 3f
    private const val HUD_RATIO_DEN = 10f
    private const val PLAY_AREA_RATIO_NUM = 17f
    private const val PLAY_AREA_RATIO_DEN = 20f
    private const val MIN_LEFT_HUD_CELLS = 12
    private const val NAV_TABS = 4

    fun usesLeftHud(logicalWidth: Float): Boolean {
        return leftHudWidth(logicalWidth) >= MIN_LEFT_HUD_CELLS * U
    }

    fun playAreaStartX(logicalWidth: Float): Float {
        return if (usesLeftHud(logicalWidth)) leftHudWidth(logicalWidth) else 0f
    }

    fun playAreaWidth(logicalWidth: Float): Float {
        val startX = playAreaStartX(logicalWidth)
        return logicalWidth - startX
    }

    fun playAreaHeight(logicalWidth: Float, logicalHeight: Float): Float {
        return if (usesLeftHud(logicalWidth)) logicalHeight else logicalHeight * PLAY_AREA_RATIO_NUM / PLAY_AREA_RATIO_DEN
    }

    fun hudStartY(logicalHeight: Float): Float {
        return logicalHeight * PLAY_AREA_RATIO_NUM / PLAY_AREA_RATIO_DEN
    }

    fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = renderer.canvas.width
        val logicalHeight = renderer.canvas.height
        val isPortrait = playX <= 0
        val leftHudW = playX.toFloat()

        val activeTab = when (SceneManager.activeScene) {
            is ActiveTimerScene -> 0
            is TemplateCustomizerScene -> 1
            is TemplateForgeScene -> 1
            is EntropyScene -> 2
            is SettingsScene -> 3
            else -> 0
        }

        val primaryColorIndex = PaletteIndices.PRIMARY
        val accentColorIndex = PaletteIndices.SECONDARY

        if (isPortrait) {
            val hudStartY = (playY + playH).toFloat()
            val hudHeight = logicalHeight - hudStartY
            val btnHeight = maxOf(U * 2f, hudHeight - U)
            val gap = U / 2f
            val sidePad = U / 2f
            val colW3 = (playW.toFloat() - sidePad * 2f - gap * (NAV_TABS - 1)) / NAV_TABS

            // Fill background of the HUD with black to mask the play area
            renderer.fillRectDither(0f, hudStartY, playW.toFloat(), logicalHeight, PaletteIndices.BLACK, PaletteIndices.BLACK, SoftDitherPattern.SOLID)
            renderer.drawLine(0f, hudStartY, playW.toFloat(), hudStartY, primaryColorIndex, 2f)

            // Row 0: Centered single row of navigation buttons
            val btnY = hudStartY + (hudHeight - btnHeight) / 2f

            drawHudButton(renderer, sidePad, btnY, activeTab == 0, btnHeight, colW3, accentColorIndex, "watch")
            drawHudButton(renderer, sidePad + colW3 + gap, btnY, activeTab == 1, btnHeight, colW3, accentColorIndex, "ofuda")
            drawHudButton(renderer, sidePad + (colW3 + gap) * 2f, btnY, activeTab == 2, btnHeight, colW3, accentColorIndex, "hakkero")
            drawHudButton(renderer, sidePad + (colW3 + gap) * 3f, btnY, activeTab == 3, btnHeight, colW3, accentColorIndex, "gohei")
        } else {
            val hudWidth = leftHudW
            renderer.fillRectDither(0f, 0f, hudWidth, logicalHeight, PaletteIndices.BLACK, PaletteIndices.BLACK, SoftDitherPattern.SOLID)
            renderer.drawLine(hudWidth, 0f, hudWidth, logicalHeight, primaryColorIndex, 2f)

            val gap = U / 2f
            val topPad = U
            val availableH = logicalHeight - topPad * 2f - gap * (NAV_TABS - 1)
            val preferredBtnH = U * 5f / 2f
            val minBtnH = U * 3f / 2f
            val fittedBtnH = availableH / NAV_TABS
            val btnHeight = when {
                fittedBtnH >= preferredBtnH -> preferredBtnH
                fittedBtnH >= minBtnH -> fittedBtnH
                else -> minBtnH
            }
            val btnWidth = hudWidth * 4f / 5f
            val btnX = (hudWidth - btnWidth) / 2f
            val stackHeight = btnHeight * NAV_TABS + gap * (NAV_TABS - 1)
            val startY = maxOf(topPad, (logicalHeight - stackHeight) / 2f)
            val spacing = btnHeight + gap

            drawHudButton(renderer, btnX, startY, activeTab == 0, btnHeight, btnWidth, accentColorIndex, "watch")
            drawHudButton(renderer, btnX, startY + spacing, activeTab == 1, btnHeight, btnWidth, accentColorIndex, "ofuda")
            drawHudButton(renderer, btnX, startY + 2 * spacing, activeTab == 2, btnHeight, btnWidth, accentColorIndex, "hakkero")
            drawHudButton(renderer, btnX, startY + 3 * spacing, activeTab == 3, btnHeight, btnWidth, accentColorIndex, "gohei")
        }
    }

    fun onInput(x: Int, y: Int, isDown: Boolean, playX: Int, playY: Int, playW: Int, playH: Int): Boolean {
        if (!isDown) return false
        val fx = x.toFloat()
        val fy = y.toFloat()
        val logicalWidth = (playX + playW).toFloat()
        val logicalHeight = logicalHeightFromPlay(playX, playH)
        val isPortrait = playX <= 0
        val leftHudW = playX.toFloat()
        val hudStartY = (playY + playH).toFloat()

        if (isPortrait) {
            if (fy < hudStartY) return false
        } else {
            if (fx >= leftHudW) return false
        }

        val hudAct = onTouch(x, y, playX, playY, playW, playH)
        if (hudAct == HudAction.NONE) return false

        ActiveTimerScene.isTaskFocused = false

        when (hudAct) {
            HudAction.SELECT_TAB_TIMER -> {
                if (SceneManager.activeScene !is ActiveTimerScene) {
                    SceneManager.inputTrigger?.performHapticFeedback(EngineHaptics.CLICK)
                    SceneManager.switchScene(ActiveTimerScene)
                }
            }
            HudAction.SELECT_TAB_CARDS -> {
                if (SceneManager.activeScene is TemplateForgeScene) {
                    SceneManager.inputTrigger?.performHapticFeedback(EngineHaptics.CLICK)
                    SceneManager.switchScene(TemplateCustomizerScene)
                } else if (SceneManager.activeScene !is TemplateCustomizerScene) {
                    SceneManager.inputTrigger?.performHapticFeedback(EngineHaptics.CLICK)
                    SceneManager.switchScene(TemplateCustomizerScene)
                }
            }
            HudAction.SELECT_TAB_BOMB -> {
                if (SceneManager.activeScene !is EntropyScene) {
                    SceneManager.inputTrigger?.performHapticFeedback(EngineHaptics.CLICK)
                    SceneManager.switchScene(EntropyScene)
                }
            }
            HudAction.SELECT_TAB_SYSTEM -> {
                if (SceneManager.activeScene !is SettingsScene) {
                    SceneManager.inputTrigger?.performHapticFeedback(EngineHaptics.CLICK)
                    SceneManager.switchScene(SettingsScene)
                }
            }
            else -> {}
        }
        return true
    }

    fun onTouch(x: Int, y: Int, playX: Int, playY: Int, playW: Int, playH: Int): HudAction {
        val fx = x.toFloat()
        val fy = y.toFloat()
        val logicalHeight = logicalHeightFromPlay(playX, playH)
        val isPortrait = playX <= 0
        val leftHudW = playX.toFloat()
        if (isPortrait) {
            val hudStartY = (playY + playH).toFloat()
            val hudHeight = logicalHeight - hudStartY
            val btnHeight = maxOf(U * 2f, hudHeight - U)
            val gap = U / 2f
            val sidePad = U / 2f
            val colW3 = (playW.toFloat() - sidePad * 2f - gap * (NAV_TABS - 1)) / NAV_TABS
            val btnY = hudStartY + (hudHeight - btnHeight) / 2f

            if (TouchColliderManager.checkAABB(fx, fy, sidePad, btnY, colW3, btnHeight)) return HudAction.SELECT_TAB_TIMER
            if (TouchColliderManager.checkAABB(fx, fy, sidePad + colW3 + gap, btnY, colW3, btnHeight)) return HudAction.SELECT_TAB_CARDS
            if (TouchColliderManager.checkAABB(fx, fy, sidePad + (colW3 + gap) * 2f, btnY, colW3, btnHeight)) return HudAction.SELECT_TAB_BOMB
            if (TouchColliderManager.checkAABB(fx, fy, sidePad + (colW3 + gap) * 3f, btnY, colW3, btnHeight)) return HudAction.SELECT_TAB_SYSTEM
        } else {
            val gap = U / 2f
            val topPad = U
            val availableH = logicalHeight - topPad * 2f - gap * (NAV_TABS - 1)
            val preferredBtnH = U * 5f / 2f
            val minBtnH = U * 3f / 2f
            val fittedBtnH = availableH / NAV_TABS
            val btnHeight = when {
                fittedBtnH >= preferredBtnH -> preferredBtnH
                fittedBtnH >= minBtnH -> fittedBtnH
                else -> minBtnH
            }
            val btnWidth = leftHudW * 4f / 5f
            val btnX = (leftHudW - btnWidth) / 2f
            val stackHeight = btnHeight * NAV_TABS + gap * (NAV_TABS - 1)
            val startY = maxOf(topPad, (logicalHeight - stackHeight) / 2f)
            val spacing = btnHeight + gap

            if (TouchColliderManager.checkAABB(fx, fy, btnX, startY, btnWidth, btnHeight)) return HudAction.SELECT_TAB_TIMER
            if (TouchColliderManager.checkAABB(fx, fy, btnX, startY + spacing, btnWidth, btnHeight)) return HudAction.SELECT_TAB_CARDS
            if (TouchColliderManager.checkAABB(fx, fy, btnX, startY + 2 * spacing, btnWidth, btnHeight)) return HudAction.SELECT_TAB_BOMB
            if (TouchColliderManager.checkAABB(fx, fy, btnX, startY + 3 * spacing, btnWidth, btnHeight)) return HudAction.SELECT_TAB_SYSTEM
        }
        return HudAction.NONE
    }

    private fun drawHudButton(
        renderer: ScaledProceduralRenderer,
        x: Float,
        y: Float,
        isActive: Boolean,
        height: Float,
        width: Float,
        accentColorIndex: Int,
        iconName: String
    ) {
        val frameColor = PaletteIndices.WHITE
        val fillColor = if (isActive) PaletteIndices.WHITE else PaletteIndices.BLACK
        val contentColor = if (isActive) PaletteIndices.BLACK else PaletteIndices.WHITE
        val surfaceColor = if (isActive) PaletteIndices.PRIMARY else PaletteIndices.BLACK
        renderer.fillRectDither(x, y, x + width, y + height, frameColor, frameColor, SoftDitherPattern.SOLID)
        renderer.fillRectDither(
            x + BUTTON_BORDER,
            y + BUTTON_BORDER,
            x + width - BUTTON_BORDER,
            y + height - BUTTON_BORDER,
            fillColor,
            fillColor,
            SoftDitherPattern.SOLID
        )

        val iconX = x + (width - ICON_SIZE) / 2f
        val iconY = y + (height - ICON_SIZE) / 2f
        ProceduralIconRenderer.draw(
            renderer,
            iconName,
            iconX,
            iconY,
            scale = ICON_SCALE,
            primaryColor = contentColor,
            onBackgroundColor = accentColorIndex,
            surfaceColor = surfaceColor
        )
    }

    private fun leftHudWidth(logicalWidth: Float): Float {
        return logicalWidth * HUD_RATIO_NUM / HUD_RATIO_DEN
    }

    private fun logicalHeightFromPlay(playX: Int, playH: Int): Float {
        return if (playX > 0) {
            playH.toFloat()
        } else {
            playH * PLAY_AREA_RATIO_DEN / PLAY_AREA_RATIO_NUM
        }
    }
}
