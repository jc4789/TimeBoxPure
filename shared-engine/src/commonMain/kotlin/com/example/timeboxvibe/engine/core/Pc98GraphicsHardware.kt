package com.example.timeboxvibe.engine.core

typealias Color12Bit = Short

object PaletteIndices {
    const val BG = 0
    const val BG_ALT = 1
    const val PANEL = 2
    const val PANEL_DARK = 3
    const val BORDER = 4
    const val BORDER_BRIGHT = 5
    const val TEXT_PRIMARY = 6
    const val TEXT_SECONDARY = 7
    const val TEXT_DISABLED = 8
    const val ACCENT_PRIMARY = 9
    const val ACCENT_SECONDARY = 10
    const val ACCENT_DANGER = 11
    const val ACCENT_SUCCESS = 12
    const val MAGIC_CIRCLE_PRIMARY = 13
    const val MAGIC_CIRCLE_SECONDARY = 14
    const val HIGHLIGHT = 15

    const val PRIMARY = ACCENT_PRIMARY
    const val SECONDARY = ACCENT_SECONDARY
    const val SURFACE = PANEL
    const val SHADOW = PANEL_DARK
    const val ERROR = ACCENT_DANGER
    const val WHITE = BORDER_BRIGHT
    const val BLACK = PANEL_DARK
}

object Pc98GraphicsHardware {
    const val PALETTE_SIZE = 16
    val onScreenPalette = ShortArray(16)
    var paletteRevision = 0

    init {
        for (i in 0 until PALETTE_SIZE) {
            onScreenPalette[i] = 0x000.toShort()
        }
    }

    fun setupPalette(palette: ShortArray) {
        var changed = false
        var i = 0
        while (i < PALETTE_SIZE) {
            val color = palette[i]
            if (onScreenPalette[i] != color) {
                onScreenPalette[i] = color
                changed = true
            }
            i++
        }
        if (changed) paletteRevision++
    }
}
