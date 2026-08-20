package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertTrue

class EngineThemesTest {
    @Test
    fun eachThemeIsSixteen12BitColorsWithReadableContrast() {
        val names = arrayOf("reimu", "marisa", "alice", "kaguya")
        var nameIndex = 0
        while (nameIndex < names.size) {
            var breakMode = 0
            while (breakMode <= 1) {
                EngineThemes.getColors(names[nameIndex], breakMode == 1)
                assertPaletteReadable(names[nameIndex], breakMode == 1)
                breakMode++
            }
            nameIndex++
        }
    }

    private fun assertPaletteReadable(name: String, isBreak: Boolean) {
        val palette = Pc98GraphicsHardware.onScreenPalette
        var i = 0
        while (i < Pc98GraphicsHardware.PALETTE_SIZE) {
            val value = palette[i].toInt() and 0xFFFF
            assertTrue(value <= 0x0FFF, "$name break=$isBreak slot $i is 12-bit ($value)")
            i++
        }
        val bg = luma(palette[PaletteIndices.BG])
        val text = luma(palette[PaletteIndices.TEXT_PRIMARY])
        val panel = luma(palette[PaletteIndices.PANEL_DARK])
        val chrome = luma(palette[PaletteIndices.SECONDARY])
        assertTrue(text > bg + 4, "$name break=$isBreak text must out-luma the field ($text vs $bg)")
        assertTrue(palette[PaletteIndices.BG] != palette[PaletteIndices.PANEL_DARK], "$name break=$isBreak panel is not the field")
        assertTrue(palette[PaletteIndices.SECONDARY] != palette[PaletteIndices.PANEL_DARK], "$name break=$isBreak chrome is not the panel")
        assertTrue(kotlin.math.abs(chrome - panel) >= 2, "$name break=$isBreak chrome luma $chrome vs panel $panel")
    }

    private fun luma(color: Short): Int {
        val value = color.toInt()
        val red = (value ushr 8) and 0x0F
        val green = (value ushr 4) and 0x0F
        val blue = value and 0x0F
        return red * 3 + green * 6 + blue
    }
}
