package com.example.timeboxvibe.engine.core

typealias Color12Bit = Short

object PaletteIndices {
    const val BG = 0
    const val PRIMARY = 1
    const val SECONDARY = 2
    const val SURFACE = 3
    const val SHADOW = 4
    const val ERROR = 5
    const val WHITE = 6
    const val BLACK = 7
}

object Pc98GraphicsHardware {
    val onScreenPalette = ShortArray(16)
    var paletteRevision = 0

    init {
        // Initial defaults (monochromatic/black)
        for (i in 0 until 16) {
            onScreenPalette[i] = 0x000.toShort()
        }
    }

    fun setupPalette(
        bg: Short,
        primary: Short,
        secondary: Short,
        surface: Short,
        shadow: Short,
        error: Short = 0xF03.toShort()
    ) {
        onScreenPalette[PaletteIndices.BG] = bg
        onScreenPalette[PaletteIndices.PRIMARY] = primary
        onScreenPalette[PaletteIndices.SECONDARY] = secondary
        onScreenPalette[PaletteIndices.SURFACE] = surface
        onScreenPalette[PaletteIndices.SHADOW] = shadow
        onScreenPalette[PaletteIndices.ERROR] = error
        onScreenPalette[PaletteIndices.WHITE] = 0xFFF.toShort()
        onScreenPalette[PaletteIndices.BLACK] = 0x000.toShort()

        // Fill remaining slots
        for (i in 8 until 16) {
            onScreenPalette[i] = 0xFFF.toShort()
        }

        paletteRevision++
    }
}
