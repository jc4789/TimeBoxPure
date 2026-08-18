package com.example.timeboxvibe.engine.core

/** Pointer facts consumed by [ImmediateUi]. */
object UiPointerAction {
    const val NONE = 0
    const val DOWN = 1
    const val MOVE = 2
    const val UP = 3
    const val CANCEL = 4
}

/**
 * Allocation-free immediate UI interaction state.
 *
 * This object owns widget identity, submitted rectangles, and pointer capture only.
 * It does not own graphics, a framebuffer, layout policy, strings, scenes, or actions.
 * Views draw from the exact rectangle stored in the returned slot, so hit testing and
 * rasterization cannot silently use different geometry.
 */
class ImmediateUi(maxWidgets: Int = DEFAULT_WIDGET_CAPACITY) {
    private val capacity = maxWidgets.coerceAtLeast(1)
    private val ids = IntArray(capacity)
    private val left = IntArray(capacity)
    private val top = IntArray(capacity)
    private val width = IntArray(capacity)
    private val height = IntArray(capacity)

    var widgetCount: Int = 0
        private set

    var hotId: Int = NO_WIDGET
        private set

    var activeId: Int = NO_WIDGET
        private set

    var clickedId: Int = NO_WIDGET
        private set

    private var pointerX = PresentationTransform.OUTSIDE
    private var pointerY = PresentationTransform.OUTSIDE
    private var pointerAction = UiPointerAction.NONE

    fun beginFrame(x: Int, y: Int, action: Int) {
        widgetCount = 0
        hotId = NO_WIDGET
        clickedId = NO_WIDGET
        pointerX = x
        pointerY = y
        pointerAction = action
    }

    fun submit(id: Int, x: Int, y: Int, w: Int, h: Int): Int {
        if (widgetCount >= capacity) return NO_SLOT
        val slot = widgetCount
        ids[slot] = id
        left[slot] = x
        top[slot] = y
        width[slot] = w.coerceAtLeast(0)
        height[slot] = h.coerceAtLeast(0)
        widgetCount++
        if (contains(slot, pointerX, pointerY)) {
            // Later submissions are topmost for overlapping widgets.
            hotId = id
        }
        return slot
    }

    fun endFrame() {
        when (pointerAction) {
            UiPointerAction.DOWN -> activeId = hotId
            UiPointerAction.UP -> {
                if (activeId != NO_WIDGET && activeId == hotId) {
                    clickedId = activeId
                }
                activeId = NO_WIDGET
            }
            UiPointerAction.CANCEL -> activeId = NO_WIDGET
        }
    }

    fun idAt(slot: Int): Int = if (validSlot(slot)) ids[slot] else NO_WIDGET
    fun xAt(slot: Int): Int = if (validSlot(slot)) left[slot] else 0
    fun yAt(slot: Int): Int = if (validSlot(slot)) top[slot] else 0
    fun widthAt(slot: Int): Int = if (validSlot(slot)) width[slot] else 0
    fun heightAt(slot: Int): Int = if (validSlot(slot)) height[slot] else 0
    fun isHot(id: Int): Boolean = hotId == id
    fun isActive(id: Int): Boolean = activeId == id
    fun wasClicked(id: Int): Boolean = clickedId == id

    private fun contains(slot: Int, x: Int, y: Int): Boolean {
        if (x == PresentationTransform.OUTSIDE || y == PresentationTransform.OUTSIDE) return false
        val w = width[slot]
        val h = height[slot]
        if (w <= 0 || h <= 0) return false
        return x >= left[slot] &&
            y >= top[slot] &&
            x < left[slot] + w &&
            y < top[slot] + h
    }

    private fun validSlot(slot: Int): Boolean = slot >= 0 && slot < widgetCount

    companion object {
        const val NO_WIDGET = -1
        const val NO_SLOT = -1
        private const val DEFAULT_WIDGET_CAPACITY = 128
    }
}

