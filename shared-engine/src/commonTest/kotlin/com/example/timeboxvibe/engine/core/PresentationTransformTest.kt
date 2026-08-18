package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationTransformTest {
    @Test
    fun completedFrameCoversTheWholeTerminal() {
        val transform = PresentationTransform()
        transform.configure(1280, 684, 2560, 1368)

        assertEquals(0, transform.viewportX)
        assertEquals(0, transform.viewportY)
        assertEquals(2560, transform.viewportWidth)
        assertEquals(1368, transform.viewportHeight)
    }

    @Test
    fun mapsViewportEdgesIntoPrimitiveBounds() {
        val transform = PresentationTransform()
        transform.configure(1280, 684, 2560, 1368)

        assertEquals(0, transform.primitiveX(0))
        assertEquals(1279, transform.primitiveX(2559))
        assertEquals(0, transform.primitiveY(0))
        assertEquals(683, transform.primitiveY(1367))
    }

    @Test
    fun rejectsPointerOutsideViewport() {
        val transform = PresentationTransform()
        transform.configure(1280, 684, 2560, 1368)

        assertEquals(PresentationTransform.OUTSIDE, transform.primitiveX(-1))
        assertEquals(PresentationTransform.OUTSIDE, transform.primitiveX(2560))
        assertFalse(transform.containsTerminalPoint(0, 1368))
        assertTrue(transform.containsTerminalPoint(1280, 684))
    }
}
