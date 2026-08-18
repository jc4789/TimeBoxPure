package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.ShinonomeFont
import kotlin.test.Test
import kotlin.test.assertEquals

class GlyphRasterizerTest {
    @Test
    fun romBitsMapOneToOneIntoPrimitivePixels() {
        val framebuffer = IndexedFramebuffer(16, 16)
        val glyph = ShinonomeFont.glyphFor('A')
        GlyphRasterizer(framebuffer).draw(glyph, 0, 0, 6)

        var y = 0
        while (y < 16) {
            var x = 0
            while (x < 16) {
                val expected = if ((glyph[y] and (0x8000 ushr x)) != 0) 6 else 0
                assertEquals(expected, framebuffer.colorIndexAt(x, y), "pixel $x,$y")
                x++
            }
            y++
        }
    }

    @Test
    fun clipPreventsWritesOutsideWidgetRect() {
        val framebuffer = IndexedFramebuffer(20, 20)
        val glyph = IntArray(16) { 0xFFFF }
        GlyphRasterizer(framebuffer).draw(glyph, 2, 2, 4, 5, 6, 3, 2)

        var y = 0
        while (y < 20) {
            var x = 0
            while (x < 20) {
                val expected = if (x >= 5 && x < 8 && y >= 6 && y < 8) 4 else 0
                assertEquals(expected, framebuffer.colorIndexAt(x, y), "pixel $x,$y")
                x++
            }
            y++
        }
    }
}
