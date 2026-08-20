package com.example.timeboxvibe.engine.core

enum class HudAction {
    NONE,
    SELECT_TAB_TIMER,
    SELECT_TAB_CARDS,
    SELECT_TAB_BOMB,
    SELECT_TAB_SYSTEM
}

object RetroHudComponent {
    private const val U = CANONICAL_UI_UNIT
    private val BUTTON_BORDER = (U / 8).toFloat()
    private val ICON_SIZE = (U * 2).toFloat()
    private const val ICON_SCALE = 1
    private const val NAV_TABS = 4

    fun render(renderer: ScaledProceduralRenderer, activeScene: Scene?) {
        val activeTab = when (activeScene) {
            is ActiveTimerScene -> 0
            is TemplateCustomizerScene -> 1
            is TemplateForgeScene -> 1
            is EntropyScene -> 2
            is SettingsScene -> 3
            else -> 0
        }

        renderer.fillRectDither(
            UiShellLayout.hudX,
            UiShellLayout.hudY,
            UiShellLayout.hudX + UiShellLayout.hudWidth,
            UiShellLayout.hudY + UiShellLayout.hudHeight,
            PaletteIndices.PANEL_DARK,
            PaletteIndices.PANEL_DARK,
            SoftDitherPattern.SOLID
        )
        renderer.drawLattice(
            UiShellLayout.hudX,
            UiShellLayout.hudY,
            UiShellLayout.hudX + UiShellLayout.hudWidth,
            UiShellLayout.hudY + UiShellLayout.hudHeight,
            PaletteIndices.BG_ALT
        )
        if (UiShellLayout.placement == HudPlacement.LEFT) {
            renderer.strokeVerticalRule(
                UiShellLayout.hudX + UiShellLayout.hudWidth,
                UiShellLayout.hudY,
                UiShellLayout.hudY + UiShellLayout.hudHeight,
                PaletteIndices.SECONDARY
            )
        } else {
            renderer.strokeHorizontalRule(
                UiShellLayout.hudX,
                UiShellLayout.hudY,
                UiShellLayout.hudX + UiShellLayout.hudWidth,
                PaletteIndices.SECONDARY
            )
        }

        var index = 0
        while (index < NAV_TABS) {
            drawHudButton(
                renderer,
                UiShellLayout.navX(index),
                UiShellLayout.navY(index),
                activeTab == index,
                UiShellLayout.navHeight(index),
                UiShellLayout.navWidth(index),
                PaletteIndices.SECONDARY,
                iconName(index)
            )
            index++
        }
    }

    fun hitTest(x: Int, y: Int): HudAction {
        return actionForIndex(UiShellLayout.navIndexAt(x.toFloat(), y.toFloat()))
    }

    fun commandFor(action: HudAction, activeScene: Scene?): SceneCommand {
        return when (action) {
            HudAction.SELECT_TAB_TIMER -> {
                if (activeScene !is ActiveTimerScene) SceneCommand.GoTo(SceneId.ACTIVE_TIMER) else SceneCommand.None
            }
            HudAction.SELECT_TAB_CARDS -> {
                if (activeScene !is TemplateCustomizerScene) {
                    SceneCommand.GoTo(SceneId.TEMPLATES)
                } else {
                    SceneCommand.None
                }
            }
            HudAction.SELECT_TAB_BOMB -> {
                if (activeScene !is EntropyScene) SceneCommand.GoTo(SceneId.ENTROPY) else SceneCommand.None
            }
            HudAction.SELECT_TAB_SYSTEM -> {
                if (activeScene !is SettingsScene) SceneCommand.GoTo(SceneId.SETTINGS) else SceneCommand.None
            }
            else -> SceneCommand.None
        }
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
        val contentColor = if (isActive) PaletteIndices.TEXT_PRIMARY else PaletteIndices.BORDER_BRIGHT
        val surfaceColor = if (isActive) PaletteIndices.ACCENT_PRIMARY else PaletteIndices.PANEL_DARK
        if (isActive) {
            renderer.fillRectDither(
                x + BUTTON_BORDER,
                y + BUTTON_BORDER,
                x + width - BUTTON_BORDER,
                y + height - BUTTON_BORDER,
                PaletteIndices.PANEL,
                PaletteIndices.PANEL,
                SoftDitherPattern.SOLID
            )
            renderer.strokeRectFrame(x, y, width, height, accentColorIndex, kind = VectorFrameKind.SMALL)
            val radius = minOf(width, height) / 3f
            renderer.strokeMedallion(x + width / 2f, y + height / 2f, radius, accentColorIndex)
        }

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

    private fun actionForIndex(index: Int): HudAction {
        return when (index) {
            0 -> HudAction.SELECT_TAB_TIMER
            1 -> HudAction.SELECT_TAB_CARDS
            2 -> HudAction.SELECT_TAB_BOMB
            3 -> HudAction.SELECT_TAB_SYSTEM
            else -> HudAction.NONE
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
