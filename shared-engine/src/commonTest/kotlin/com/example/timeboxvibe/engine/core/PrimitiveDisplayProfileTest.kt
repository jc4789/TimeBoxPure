package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PrimitiveDisplayProfileTest {
    @Test
    fun normalClientUsesExactTerminalRaster() {
        assertEquals(800, PrimitiveDisplayProfile.primitiveWidth(800, 600))
        assertEquals(600, PrimitiveDisplayProfile.primitiveHeight(800, 600))
    }

    @Test
    fun suppliedWin32SizeUsesExactClientFramebuffer() {
        assertEquals(2560, PrimitiveDisplayProfile.primitiveWidth(2560, 1368))
        assertEquals(1368, PrimitiveDisplayProfile.primitiveHeight(2560, 1368))
    }

    @Test
    fun suppliedPortraitSizesRemainExact() {
        assertEquals(855, PrimitiveDisplayProfile.primitiveWidth(855, 1226))
        assertEquals(1226, PrimitiveDisplayProfile.primitiveHeight(855, 1226))
        assertEquals(857, PrimitiveDisplayProfile.primitiveWidth(857, 1225))
        assertEquals(1225, PrimitiveDisplayProfile.primitiveHeight(857, 1225))
    }

    @Test
    fun largeTerminalRemainsExact() {
        val outputWidth = 3840
        val outputHeight = 2160
        val width = PrimitiveDisplayProfile.primitiveWidth(outputWidth, outputHeight)
        val height = PrimitiveDisplayProfile.primitiveHeight(outputWidth, outputHeight)

        assertEquals(outputWidth, width)
        assertEquals(outputHeight, height)
    }

    @Test
    fun invalidTerminalDimensionsClampToOnePrimitivePixel() {
        assertEquals(1, PrimitiveDisplayProfile.primitiveWidth(0, -20))
        assertEquals(1, PrimitiveDisplayProfile.primitiveHeight(0, -20))
    }
}
