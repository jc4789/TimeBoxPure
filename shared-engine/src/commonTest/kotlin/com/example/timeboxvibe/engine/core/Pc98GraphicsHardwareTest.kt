package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Pc98GraphicsHardwareTest {
    @Test
    fun exposes4096ColorSpaceWith16ActiveEntries() {
        assertEquals(4096, Pc98GraphicsHardware.COLOR_SPACE_SIZE)
        assertEquals(16, Pc98GraphicsHardware.PALETTE_SIZE)
    }

    @Test
    fun rejectsColorsOutside12BitRgbSpace() {
        val invalid = ShortArray(Pc98GraphicsHardware.PALETTE_SIZE)
        invalid[0] = 0x1000

        assertFailsWith<IllegalArgumentException> {
            Pc98GraphicsHardware.setupPalette(invalid)
        }
    }
}
