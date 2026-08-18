package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImmediateUiTest {
    @Test
    fun clickRequiresDownAndUpInsideSameWidget() {
        val ui = ImmediateUi(4)

        ui.beginFrame(12, 12, UiPointerAction.DOWN)
        ui.submit(7, 10, 10, 20, 10)
        ui.endFrame()
        assertTrue(ui.isActive(7))

        ui.beginFrame(12, 12, UiPointerAction.UP)
        ui.submit(7, 10, 10, 20, 10)
        ui.endFrame()
        assertTrue(ui.wasClicked(7))
        assertFalse(ui.isActive(7))
    }

    @Test
    fun rightAndBottomEdgesAreExclusive() {
        val ui = ImmediateUi(2)
        ui.beginFrame(30, 20, UiPointerAction.DOWN)
        ui.submit(1, 10, 10, 20, 10)
        ui.endFrame()
        assertEquals(ImmediateUi.NO_WIDGET, ui.hotId)
    }

    @Test
    fun lastSubmittedWidgetOwnsOverlap() {
        val ui = ImmediateUi(2)
        ui.beginFrame(5, 5, UiPointerAction.DOWN)
        ui.submit(1, 0, 0, 10, 10)
        ui.submit(2, 0, 0, 10, 10)
        ui.endFrame()
        assertEquals(2, ui.hotId)
        assertEquals(2, ui.activeId)
    }

    @Test
    fun submittedRectIsAvailableToTheView() {
        val ui = ImmediateUi(1)
        ui.beginFrame(0, 0, UiPointerAction.NONE)
        val slot = ui.submit(9, 3, 4, 20, 12)
        ui.endFrame()

        assertEquals(3, ui.xAt(slot))
        assertEquals(4, ui.yAt(slot))
        assertEquals(20, ui.widthAt(slot))
        assertEquals(12, ui.heightAt(slot))
    }
}
