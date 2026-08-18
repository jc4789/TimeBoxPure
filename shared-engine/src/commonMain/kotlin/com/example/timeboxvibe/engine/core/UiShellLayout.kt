package com.example.timeboxvibe.engine.core

enum class HudPlacement {
    LEFT,
    BOTTOM
}

object CommonUiSettings {
    var hudPlacement: HudPlacement = HudPlacement.BOTTOM
}

object UiShellLayout {
    private const val U = CANONICAL_UI_UNIT
    private const val NAV_TAB_COUNT = 4
    private const val HUD_RATIO_NUM = 3f
    private const val HUD_RATIO_DEN = 10f
    private const val PLAY_AREA_RATIO_NUM = 17f
    private const val PLAY_AREA_RATIO_DEN = 20f

    var placement: HudPlacement = HudPlacement.BOTTOM
        private set
    var logicalWidth = 0f
        private set
    var logicalHeight = 0f
        private set
    var isTallDisplay = false
        private set
    var contentX = 0f
        private set
    var contentY = 0f
        private set
    var contentWidth = 0f
        private set
    var contentHeight = 0f
        private set
    var hudX = 0f
        private set
    var hudY = 0f
        private set
    var hudWidth = 0f
        private set
    var hudHeight = 0f
        private set

    private val navX = FloatArray(NAV_TAB_COUNT)
    private val navY = FloatArray(NAV_TAB_COUNT)
    private val navWidth = FloatArray(NAV_TAB_COUNT)
    private val navHeight = FloatArray(NAV_TAB_COUNT)
    private val navigationUi = ImmediateUi(NAV_TAB_COUNT)

    fun resolve(width: Float, height: Float) {
        logicalWidth = width
        logicalHeight = height
        isTallDisplay = height > width
        placement = CommonUiSettings.hudPlacement

        if (placement == HudPlacement.LEFT) {
            hudX = 0f
            hudY = 0f
            hudWidth = (width * HUD_RATIO_NUM / HUD_RATIO_DEN).toInt().toFloat()
            hudHeight = height
            contentX = hudWidth
            contentY = 0f
            contentWidth = (width - contentX).toInt().toFloat()
            contentHeight = height.toInt().toFloat()
            resolveLeftNavigation()
        } else {
            contentX = 0f
            contentY = 0f
            contentWidth = width.toInt().toFloat()
            contentHeight = (height * PLAY_AREA_RATIO_NUM / PLAY_AREA_RATIO_DEN).toInt().toFloat()
            hudX = 0f
            hudY = contentHeight
            hudWidth = contentWidth
            hudHeight = height - hudY
            resolveBottomNavigation()
        }
    }

    fun navX(index: Int): Float = navX[index]

    fun navY(index: Int): Float = navY[index]

    fun navWidth(index: Int): Float = navWidth[index]

    fun navHeight(index: Int): Float = navHeight[index]

    fun containsHud(x: Int, y: Int): Boolean {
        return x >= hudX.toInt() &&
            y >= hudY.toInt() &&
            x < (hudX + hudWidth).toInt() &&
            y < (hudY + hudHeight).toInt()
    }

    fun navIndexAt(x: Float, y: Float): Int {
        navigationUi.beginFrame(x.toInt(), y.toInt(), UiPointerAction.NONE)
        var index = 0
        while (index < NAV_TAB_COUNT) {
            navigationUi.submit(
                index,
                navX[index].toInt(),
                navY[index].toInt(),
                navWidth[index].toInt(),
                navHeight[index].toInt()
            )
            index++
        }
        navigationUi.endFrame()
        return navigationUi.hotId
    }

    private fun resolveBottomNavigation() {
        val gap = (U / 2).toFloat()
        val sidePad = (U / 2).toFloat()
        val buttonWidth = maxOf(
            0,
            ((contentWidth - sidePad * 2f - gap * (NAV_TAB_COUNT - 1)) / NAV_TAB_COUNT).toInt()
        ).toFloat()
        val buttonHeight = maxOf(0f, minOf(maxOf((U * 2).toFloat(), hudHeight - U.toFloat()), hudHeight))
        val buttonY = (hudY + (hudHeight - buttonHeight) / 2f).toInt().toFloat()

        var index = 0
        while (index < NAV_TAB_COUNT) {
            navX[index] = sidePad + (buttonWidth + gap) * index
            navY[index] = buttonY
            navWidth[index] = buttonWidth
            navHeight[index] = buttonHeight
            index++
        }
    }

    private fun resolveLeftNavigation() {
        val gap = (U / 2).toFloat()
        val topPad = U.toFloat()
        val fittedButtonHeight = (logicalHeight - topPad * 2f - gap * (NAV_TAB_COUNT - 1)) / NAV_TAB_COUNT
        val buttonHeight = maxOf(0, minOf(U * 5 / 2, fittedButtonHeight.toInt())).toFloat()
        val buttonWidth = (hudWidth * 4f / 5f).toInt().toFloat()
        val buttonX = ((hudWidth - buttonWidth) / 2f).toInt().toFloat()
        val stackHeight = buttonHeight * NAV_TAB_COUNT + gap * (NAV_TAB_COUNT - 1)
        val startY = maxOf(U.toFloat(), ((logicalHeight - stackHeight) / 2f).toInt().toFloat())

        var index = 0
        while (index < NAV_TAB_COUNT) {
            navX[index] = buttonX
            navY[index] = startY + (buttonHeight + gap) * index
            navWidth[index] = buttonWidth
            navHeight[index] = buttonHeight
            index++
        }
    }
}
