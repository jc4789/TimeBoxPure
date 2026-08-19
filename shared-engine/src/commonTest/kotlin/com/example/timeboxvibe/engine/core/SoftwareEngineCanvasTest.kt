package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SoftwareEngineCanvasTest {
    @Test
    fun aliasedCircleScalesRelativeToItsOwnStroke() {
        val source = SoftwareEngineCanvas(32, 32)
        val scaled = SoftwareEngineCanvas(96, 96)

        source.drawCircle(16f, 16f, 10f, 7, 1f)
        scaled.drawCircle(48f, 48f, 30f, 7, 3f)

        var y = 0
        while (y < scaled.framebuffer.height) {
            var x = 0
            while (x < scaled.framebuffer.width) {
                val sourceX = (x + 1) / 3
                val sourceY = (y + 1) / 3
                val expected = if (sourceX < source.framebuffer.width && sourceY < source.framebuffer.height) {
                    source.framebuffer.colorIndexAt(sourceX, sourceY)
                } else {
                    0
                }
                assertEquals(expected, scaled.framebuffer.colorIndexAt(x, y), "pixel ($x, $y)")
                x++
            }
            y++
        }
    }

    @Test
    fun clearAndRectWritePaletteIndices() {
        val canvas = SoftwareEngineCanvas(8, 6)
        canvas.clear(3)
        canvas.drawRect(2f, 1f, 3f, 2f, 14)

        assertEquals(3, canvas.framebuffer.colorIndexAt(0, 0))
        assertEquals(14, canvas.framebuffer.colorIndexAt(2, 1))
        assertEquals(14, canvas.framebuffer.colorIndexAt(4, 2))
        assertEquals(3, canvas.framebuffer.colorIndexAt(5, 2))
    }

    @Test
    fun invalidPaletteIndicesAreRejected() {
        val canvas = SoftwareEngineCanvas(2, 1)
        assertFailsWith<IllegalArgumentException> { canvas.setPixel(0f, 0f, 16) }
        assertFailsWith<IllegalArgumentException> { canvas.drawRect(0f, 0f, 1f, 1f, -1) }
        assertEquals(0, canvas.framebuffer.colorIndexAt(0, 0))
    }

    @Test
    fun ditherIsDeterministic() {
        val canvas = SoftwareEngineCanvas(4, 2)
        canvas.fillRectDither(0f, 0f, 4f, 2f, 1, 2, SoftDitherPattern.CHECKERBOARD)

        assertEquals(1, canvas.framebuffer.colorIndexAt(0, 0))
        assertEquals(2, canvas.framebuffer.colorIndexAt(1, 0))
        assertEquals(2, canvas.framebuffer.colorIndexAt(0, 1))
        assertEquals(1, canvas.framebuffer.colorIndexAt(1, 1))
    }
}
