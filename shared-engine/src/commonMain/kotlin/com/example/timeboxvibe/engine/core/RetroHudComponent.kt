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
    private const val U = CANONICAL_UI_UNIT
    private val BUTTON_BORDER = (U / 8).toFloat()
    private val ICON_SIZE = (U * 2).toFloat()
    private const val ICON_SCALE = 1
    private const val HUD_RATIO_NUM = 3f
    private const val HUD_RATIO_DEN = 10f
    private const val PLAY_AREA_RATIO_NUM = 17f
    private const val PLAY_AREA_RATIO_DEN = 20f
    private const val NAV_TABS = 4
    private const val LAYOUT_BOTTOM = 0
    private const val LAYOUT_LEFT = 1

    private var pendingSceneCommand: SceneCommand = SceneCommand.None

    fun consumeSceneCommand(): SceneCommand {
        val cmd = pendingSceneCommand
        pendingSceneCommand = SceneCommand.None
        return cmd
    }

    fun usesLeftHud(logicalWidth: Float, logicalHeight: Float): Boolean {
        return layoutMode(logicalWidth, logicalHeight) == LAYOUT_LEFT
    }

    fun playAreaStartX(logicalWidth: Float, logicalHeight: Float): Float {
        return if (usesLeftHud(logicalWidth, logicalHeight)) leftHudWidth(logicalWidth) else 0f
    }

    fun playAreaWidth(logicalWidth: Float, logicalHeight: Float): Float {
        val startX = playAreaStartX(logicalWidth, logicalHeight)
        return logicalWidth - startX
    }

    fun playAreaHeight(logicalWidth: Float, logicalHeight: Float): Float {
        return if (usesLeftHud(logicalWidth, logicalHeight)) logicalHeight else bottomPlayAreaHeight(logicalHeight)
    }

    fun hudStartY(logicalWidth: Float, logicalHeight: Float): Float {
        return if (usesLeftHud(logicalWidth, logicalHeight)) 0f else bottomPlayAreaHeight(logicalHeight)
    }

    fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val usesBottomHud = !usesLeftHud(logicalWidth, logicalHeight)
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

        if (usesBottomHud) {
            val hudStartY = (playY + playH).toFloat()
            val hudHeight = logicalHeight - hudStartY
            val btnHeight = bottomButtonHeight(hudHeight)
            val btnWidth = bottomButtonWidth(playW.toFloat())

            // Fill background of the HUD with black to mask the play area
            renderer.fillRectDither(0f, hudStartY, playW.toFloat(), logicalHeight, PaletteIndices.BLACK, PaletteIndices.BLACK, SoftDitherPattern.SOLID)
            renderer.drawLine(0f, hudStartY, playW.toFloat(), hudStartY, primaryColorIndex, 2f)

            var index = 0
            while (index < NAV_TABS) {
                drawHudButton(
                    renderer,
                    bottomButtonX(index, btnWidth),
                    bottomButtonY(hudStartY, hudHeight, btnHeight),
                    activeTab == index,
                    btnHeight,
                    btnWidth,
                    accentColorIndex,
                    iconName(index)
                )
                index++
            }
        } else {
            val hudWidth = leftHudW
            renderer.fillRectDither(0f, 0f, hudWidth, logicalHeight, PaletteIndices.BLACK, PaletteIndices.BLACK, SoftDitherPattern.SOLID)
            renderer.drawLine(hudWidth, 0f, hudWidth, logicalHeight, primaryColorIndex, 2f)

            val btnHeight = leftButtonHeight(logicalHeight)
            val btnWidth = leftButtonWidth(hudWidth)
            var index = 0
            while (index < NAV_TABS) {
                drawHudButton(
                    renderer,
                    leftButtonX(hudWidth, btnWidth),
                    leftButtonY(index, logicalHeight, btnHeight),
                    activeTab == index,
                    btnHeight,
                    btnWidth,
                    accentColorIndex,
                    iconName(index)
                )
                index++
            }
        }
    }

    fun onTouchEvent(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int): Boolean {
        val fx = x.toFloat()
        val fy = y.toFloat()
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val usesBottomHud = !usesLeftHud(logicalWidth, logicalHeight)
        val leftHudW = playX.toFloat()
        val hudStartY = (playY + playH).toFloat()

        if (usesBottomHud) {
            if (fy < hudStartY) return false
        } else {
            if (fx >= leftHudW) return false
        }

        val hudAct = onTouch(x, y, playX, playY, playW, playH)
        if (hudAct == HudAction.NONE) return false
        if (action == TouchAction.CANCEL) return true
        if (action != TouchAction.UP) return true

        when (hudAct) {
            HudAction.SELECT_TAB_TIMER -> {
                if (SceneManager.activeScene !is ActiveTimerScene) {
                    SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                    pendingSceneCommand = SceneCommand.GoTo(SceneId.ACTIVE_TIMER)
                }
            }
            HudAction.SELECT_TAB_CARDS -> {
                if (SceneManager.activeScene is TemplateForgeScene) {
                    SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                    pendingSceneCommand = SceneCommand.GoTo(SceneId.TEMPLATES)
                } else if (SceneManager.activeScene !is TemplateCustomizerScene) {
                    SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                    pendingSceneCommand = SceneCommand.GoTo(SceneId.TEMPLATES)
                }
            }
            HudAction.SELECT_TAB_BOMB -> {
                if (SceneManager.activeScene !is EntropyScene) {
                    SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                    pendingSceneCommand = SceneCommand.GoTo(SceneId.ENTROPY)
                }
            }
            HudAction.SELECT_TAB_SYSTEM -> {
                if (SceneManager.activeScene !is SettingsScene) {
                    SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                    pendingSceneCommand = SceneCommand.GoTo(SceneId.SETTINGS)
                }
            }
            else -> {}
        }
        return true
    }

    fun actionName(action: HudAction): String {
        return when (action) {
            HudAction.NONE -> "NONE"
            HudAction.SELECT_TAB_TIMER -> "SELECT_TAB_TIMER"
            HudAction.SELECT_TAB_CARDS -> "SELECT_TAB_CARDS"
            HudAction.SELECT_TAB_BOMB -> "SELECT_TAB_BOMB"
            HudAction.SELECT_TAB_SYSTEM -> "SELECT_TAB_SYSTEM"
            HudAction.TIMER_START_STOP -> "TIMER_START_STOP"
            HudAction.TIMER_RESET -> "TIMER_RESET"
            HudAction.TIMER_SKIP -> "TIMER_SKIP"
            HudAction.TOGGLE_TICKS -> "TOGGLE_TICKS"
            HudAction.TOGGLE_VIBE -> "TOGGLE_VIBE"
            HudAction.FOCUS_INPUT -> "FOCUS_INPUT"
        }
    }

    fun onTouch(x: Int, y: Int, playX: Int, playY: Int, playW: Int, playH: Int): HudAction {
        val fx = x.toFloat()
        val fy = y.toFloat()
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val usesBottomHud = !usesLeftHud(logicalWidth, logicalHeight)
        val leftHudW = playX.toFloat()
        if (usesBottomHud) {
            val hudStartY = (playY + playH).toFloat()
            val hudHeight = logicalHeight - hudStartY
            val btnHeight = bottomButtonHeight(hudHeight)
            val btnWidth = bottomButtonWidth(playW.toFloat())
            val btnY = bottomButtonY(hudStartY, hudHeight, btnHeight)
            var index = 0
            while (index < NAV_TABS) {
                if (TouchColliderManager.checkAABB(fx, fy, bottomButtonX(index, btnWidth), btnY, btnWidth, btnHeight)) {
                    return actionForIndex(index)
                }
                index++
            }
        } else {
            val btnHeight = leftButtonHeight(logicalHeight)
            val btnWidth = leftButtonWidth(leftHudW)
            val btnX = leftButtonX(leftHudW, btnWidth)
            var index = 0
            while (index < NAV_TABS) {
                if (TouchColliderManager.checkAABB(fx, fy, btnX, leftButtonY(index, logicalHeight, btnHeight), btnWidth, btnHeight)) {
                    return actionForIndex(index)
                }
                index++
            }
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

    private fun layoutMode(logicalWidth: Float, logicalHeight: Float): Int {
        val leftWidth = leftHudWidth(logicalWidth)
        val minimumButtonSpan = ICON_SIZE + U / 2f
        val leftValid = leftWidth >= ICON_SIZE + U &&
            leftButtonWidth(leftWidth) >= minimumButtonSpan &&
            leftFittedButtonHeight(logicalHeight) >= minimumButtonSpan
        val bottomHudHeight = logicalHeight - bottomPlayAreaHeight(logicalHeight)
        val bottomValid = bottomHudHeight >= ICON_SIZE + U && bottomButtonWidth(logicalWidth) >= minimumButtonSpan

        if (!leftValid) return LAYOUT_BOTTOM
        if (!bottomValid) return LAYOUT_LEFT

        val leftScore = minOf((logicalWidth - leftWidth) / U, logicalHeight / U)
        val bottomScore = minOf(logicalWidth / U, bottomPlayAreaHeight(logicalHeight) / U)
        return if (leftScore > bottomScore) LAYOUT_LEFT else LAYOUT_BOTTOM
    }

    private fun bottomPlayAreaHeight(logicalHeight: Float): Float {
        return logicalHeight * PLAY_AREA_RATIO_NUM / PLAY_AREA_RATIO_DEN
    }

    private fun bottomButtonWidth(playWidth: Float): Float {
        val gap = (U / 2).toFloat()
        val sidePad = (U / 2).toFloat()
        return maxOf(0f, (playWidth - sidePad * 2f - gap * (NAV_TABS - 1)) / NAV_TABS)
    }

    private fun bottomButtonHeight(hudHeight: Float): Float {
        return maxOf(0f, minOf(maxOf((U * 2).toFloat(), hudHeight - U.toFloat()), hudHeight))
    }

    private fun bottomButtonX(index: Int, buttonWidth: Float): Float {
        val gap = (U / 2).toFloat()
        val sidePad = (U / 2).toFloat()
        return sidePad + (buttonWidth + gap) * index
    }

    private fun bottomButtonY(hudStartY: Float, hudHeight: Float, buttonHeight: Float): Float {
        return hudStartY + (hudHeight - buttonHeight) / 2f
    }

    private fun leftFittedButtonHeight(logicalHeight: Float): Float {
        val gap = (U / 2).toFloat()
        val topPad = U.toFloat()
        return (logicalHeight - topPad * 2f - gap * (NAV_TABS - 1)) / NAV_TABS
    }

    private fun leftButtonHeight(logicalHeight: Float): Float {
        val preferredButtonHeight = (U * 5 / 2).toFloat()
        val fittedButtonHeight = leftFittedButtonHeight(logicalHeight)
        return maxOf(0f, minOf(preferredButtonHeight, fittedButtonHeight))
    }

    private fun leftButtonWidth(hudWidth: Float): Float {
        return hudWidth * 4f / 5f
    }

    private fun leftButtonX(hudWidth: Float, buttonWidth: Float): Float {
        return (hudWidth - buttonWidth) / 2f
    }

    private fun leftButtonY(index: Int, logicalHeight: Float, buttonHeight: Float): Float {
        val gap = (U / 2).toFloat()
        val stackHeight = buttonHeight * NAV_TABS + gap * (NAV_TABS - 1)
        val startY = maxOf(U.toFloat(), (logicalHeight - stackHeight) / 2f)
        return startY + (buttonHeight + gap) * index
    }

    private fun actionForIndex(index: Int): HudAction {
        return when (index) {
            0 -> HudAction.SELECT_TAB_TIMER
            1 -> HudAction.SELECT_TAB_CARDS
            2 -> HudAction.SELECT_TAB_BOMB
            else -> HudAction.SELECT_TAB_SYSTEM
        }
    }

    private fun iconName(index: Int): String {
        return when (index) {
            0 -> "watch"
            1 -> "ofuda"
            2 -> "hakkero"
            else -> "gohei"
        }
    }
}
