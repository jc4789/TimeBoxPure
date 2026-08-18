package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiShellLayoutTest {
    @Test
    fun bottomPlacementOwnsBottomHudAndFourImmediateButtons() {
        CommonUiSettings.hudPlacement = HudPlacement.BOTTOM
        UiShellLayout.resolve(400f, 640f)

        assertEquals(0f, UiShellLayout.contentX)
        assertEquals(0f, UiShellLayout.contentY)
        assertEquals(UiShellLayout.contentHeight, UiShellLayout.hudY)
        assertEquals(400f, UiShellLayout.hudWidth)
        assertTrue(UiShellLayout.isTallDisplay)
        assertTrue(UiShellLayout.containsHud(200, 639))
        assertEquals(0, centerHit(0))
        assertEquals(3, centerHit(3))
    }

    @Test
    fun leftPlacementOwnsLeftHudWithoutInspectingAspectRatio() {
        CommonUiSettings.hudPlacement = HudPlacement.LEFT
        UiShellLayout.resolve(400f, 640f)

        assertEquals(0f, UiShellLayout.hudX)
        assertEquals(0f, UiShellLayout.hudY)
        assertEquals(640f, UiShellLayout.hudHeight)
        assertEquals(UiShellLayout.hudWidth, UiShellLayout.contentX)
        assertTrue(UiShellLayout.isTallDisplay)
        assertTrue(UiShellLayout.containsHud(0, 320))
        assertEquals(0, centerHit(0))
        assertEquals(3, centerHit(3))
        assertTrue(UiShellLayout.navIndexAt(399f, 639f) < 0)

        CommonUiSettings.hudPlacement = HudPlacement.BOTTOM
    }

    private fun centerHit(index: Int): Int {
        val x = UiShellLayout.navX(index) + UiShellLayout.navWidth(index) / 2f
        val y = UiShellLayout.navY(index) + UiShellLayout.navHeight(index) / 2f
        return UiShellLayout.navIndexAt(x, y)
    }
}
