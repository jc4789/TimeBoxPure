package com.example.timeboxvibe.engine.core

object EngineThemes {
    fun getColors(themeName: String, isBreak: Boolean) {
        var bg: Short = 0x201.toShort()
        var pri: Short = 0xF55.toShort()
        var sec: Short = 0xFAA.toShort()
        var sur: Short = 0x412.toShort()
        var shd: Short = 0x000.toShort()

        when (themeName) {
            "reimu" -> {
                if (isBreak) {
                    bg = 0x311.toShort()
                    pri = 0xF55.toShort()
                    sec = 0xFAA.toShort()
                    sur = 0x522.toShort()
                    shd = 0x000.toShort()
                } else {
                    bg = 0x201.toShort()
                    pri = 0xF55.toShort()
                    sec = 0xFAA.toShort()
                    sur = 0x412.toShort()
                    shd = 0x000.toShort()
                }
            }
            "marisa" -> {
                if (isBreak) {
                    bg = 0x213.toShort()
                    pri = 0xFE5.toShort()
                    sec = 0xFFF.toShort()
                    sur = 0x425.toShort()
                    shd = 0x000.toShort()
                } else {
                    bg = 0x210.toShort()
                    pri = 0xFE5.toShort()
                    sec = 0xA8F.toShort()
                    sur = 0x431.toShort()
                    shd = 0x000.toShort()
                }
            }
            "alice" -> {
                if (isBreak) {
                    bg = 0x102.toShort()
                    pri = 0x5AF.toShort()
                    sec = 0xF9B.toShort()
                    sur = 0x314.toShort()
                    shd = 0x000.toShort()
                } else {
                    bg = 0x012.toShort()
                    pri = 0x5AF.toShort()
                    sec = 0xF9B.toShort()
                    sur = 0x124.toShort()
                    shd = 0x000.toShort()
                }
            }
            "kaguya" -> {
                if (isBreak) {
                    bg = 0x232.toShort()
                    pri = 0xDAF.toShort()
                    sec = 0x8C6.toShort()
                    sur = 0x343.toShort()
                    shd = 0x000.toShort()
                } else {
                    bg = 0x102.toShort()
                    pri = 0xDAF.toShort()
                    sec = 0x8C6.toShort()
                    sur = 0x314.toShort()
                    shd = 0x000.toShort()
                }
            }
            else -> { // Default Reimu Red
                bg = 0x201.toShort()
                pri = 0xF55.toShort()
                sec = 0xFAA.toShort()
                sur = 0x412.toShort()
                shd = 0x000.toShort()
            }
        }

        // Initialize Pc98GraphicsHardware palette slots
        Pc98GraphicsHardware.setupPalette(bg, pri, sec, sur, shd)
    }
}
