package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrimitiveDisplayProfileTest {
    @Test
    fun normalClientUsesPixelDoubledPrimitiveRaster() {
        assertEquals(400, PrimitiveDisplayProfile.primitiveWidth(800, 600))
        assertEquals(300, PrimitiveDisplayProfile.primitiveHeight(800, 600))
    }

    @Test
    fun suppliedWin32SizeUsesClientShapedHalfResolutionFramebuffer() {
        assertEquals(1280, PrimitiveDisplayProfile.primitiveWidth(2560, 1368))
        assertEquals(684, PrimitiveDisplayProfile.primitiveHeight(2560, 1368))
    }

    @Test
    fun suppliedPortraitSizesStayOnSameReadablePixelBlock() {
        assertEquals(428, PrimitiveDisplayProfile.primitiveWidth(855, 1226))
        assertEquals(613, PrimitiveDisplayProfile.primitiveHeight(855, 1226))
        assertEquals(429, PrimitiveDisplayProfile.primitiveWidth(857, 1225))
        assertEquals(613, PrimitiveDisplayProfile.primitiveHeight(857, 1225))
    }

    @Test
    fun oversizedTerminalFitsProportionallyWithinSoftwarePixelBudget() {
        val outputWidth = 3840
        val outputHeight = 2160
        val width = PrimitiveDisplayProfile.primitiveWidth(outputWidth, outputHeight)
        val height = PrimitiveDisplayProfile.primitiveHeight(outputWidth, outputHeight)
        val crossProductError = kotlin.math.abs(
            width.toLong() * outputHeight - height.toLong() * outputWidth
        )

        assertEquals(1365, width)
        assertEquals(768, height)
        assertTrue(width.toLong() * height.toLong() <= PrimitiveDisplayProfile.MAX_PRIMITIVE_PIXELS)
        assertTrue(crossProductError * 2L <= maxOf(outputWidth, outputHeight))
    }

    @Test
    fun invalidTerminalDimensionsClampToOnePrimitivePixel() {
        assertEquals(1, PrimitiveDisplayProfile.primitiveWidth(0, -20))
        assertEquals(1, PrimitiveDisplayProfile.primitiveHeight(0, -20))
    }
}
