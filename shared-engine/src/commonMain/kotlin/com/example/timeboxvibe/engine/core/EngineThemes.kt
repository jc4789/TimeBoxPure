package com.example.timeboxvibe.engine.core

object EngineThemes {
    /*
     * Source records. Engine colors are nearest 12-bit RGB, expanded by nibble repeat.
     *
     * 櫻色 / さくらいろ / source #FEEEED / RGB 254,238,237 / Munsell 10RP 9/2.5 / 12-bit #FEE / expanded #FFEEEE
     * 藍色 / あいいろ / source #165E83 / RGB 22,94,131 / Munsell - / 12-bit #168 / expanded #116688
     * 紺色 / こんいろ / source #192F60 / RGB 25,47,96 / Munsell - / 12-bit #236 / expanded #223366
     * 緋色 / ひいろ / source #D3381C / RGB 211,56,28 / Munsell - / 12-bit #C32 / expanded #CC3322
     * 深緋 / こきひ / source #C9171E / RGB 201,23,30 / Munsell - / 12-bit #C12 / expanded #CC1122
     * 山吹色 / やまぶきいろ / source #F8B500 / RGB 248,181,0 / Munsell - / 12-bit #FB0 / expanded #FFBB00
     * 月白 / げっぱく / source #EAF4FC / RGB 234,244,252 / Munsell - / 12-bit #EEF / expanded #EEEEFF
     * 藍鼠 / あいねず / source #6C848D / RGB 108,132,141 / Munsell - / 12-bit #688 / expanded #668888
     * 墨色 / すみいろ / source #1C1C1C / RGB 28,28,28 / Munsell - / 12-bit #222 / expanded #222222
     * 胡粉色 / ごふんいろ / source #FFFFFC / RGB 255,255,252 / Munsell - / 12-bit #FFF / expanded #FFFFFF
     * 翡翠色 / ひすいいろ / source #38B48B / RGB 56,180,139 / Munsell - / 12-bit #3B8 / expanded #33BB88
     * 古代紫 / こだいむらさき / source #895B8A / RGB 137,91,138 / Munsell - / 12-bit #858 / expanded #885588
     * 真紅 / しんく / source #A22041 / RGB 162,32,65 / Munsell - / 12-bit #A24 / expanded #AA2244
     * 紅 / くれない / source #D7003A / RGB 215,0,58 / Munsell - / 12-bit #D03 / expanded #DD0033
     * 紅緋 / べにひ / source #E83929 / RGB 232,57,41 / Munsell - / 12-bit #E32 / expanded #EE3322
     * 黒橡 / くろつるばみ / source #544A47 / RGB 84,74,71 / Munsell - / 12-bit #544 / expanded #554444
     * 藍鉄 / あいてつ / source #393F4C / RGB 57,63,76 / Munsell - / 12-bit #344 / expanded #334444
     * 褐返 / かちかえし / source #203744 / RGB 32,55,68 / Munsell - / 12-bit #234 / expanded #223344
     * 紫鳶 / むらさきとび / source #5F414B / RGB 95,65,75 / Munsell - / 12-bit #644 / expanded #664444
     * 煤色 / すすいろ / source #887F7A / RGB 136,127,122 / Munsell - / 12-bit #877 / expanded #887777
     * 海松茶 / みるちゃ / source #5A544B / RGB 90,84,75 / Munsell - / 12-bit #554 / expanded #555544
     * 藤鼠 / ふじねず / source #A6A5C4 / RGB 166,165,196 / Munsell - / 12-bit #AAC / expanded #AAAACC
     * 薄紅 / うすべに / source #E8A1A8 / RGB 232,161,168 / Munsell - / 12-bit #EAB / expanded #EE88BB
     *      (added for inner timer beads — a clear pink, distinct from the near-white
     *      櫻色, and from the red HIGHLIGHT used for the outer beads)
     */
    private object WaColor12 {
        const val 櫻色: Short = 0x0FEE
        const val 藍色: Short = 0x0168
        const val 紺色: Short = 0x0236
        const val 緋色: Short = 0x0C32
        const val 深緋: Short = 0x0C12
        const val 山吹色: Short = 0x0FB0
        const val 月白: Short = 0x0EEF
        const val 藍鼠: Short = 0x0688
        const val 墨色: Short = 0x0222
        const val 胡粉色: Short = 0x0FFF
        const val 翡翠色: Short = 0x03B8
        const val 古代紫: Short = 0x0858
        const val 真紅: Short = 0x0A24
        const val 紅: Short = 0x0D03
        const val 紅緋: Short = 0x0E32
        const val 黒橡: Short = 0x0544
        const val 藍鉄: Short = 0x0344
        const val 褐返: Short = 0x0234
        const val 紫鳶: Short = 0x0644
        const val 煤色: Short = 0x0877
        const val 海松茶: Short = 0x0554
        const val 藤鼠: Short = 0x0AAC
        const val 薄紅: Short = 0x0EAB
    }

    private val reimuFocus = shortArrayOf(
        WaColor12.墨色, WaColor12.藍鉄, WaColor12.黒橡, WaColor12.褐返,
        WaColor12.藍鼠, WaColor12.胡粉色, WaColor12.月白, WaColor12.櫻色,
        WaColor12.煤色, WaColor12.深緋, WaColor12.山吹色, WaColor12.緋色,
        WaColor12.翡翠色, WaColor12.藍色, WaColor12.古代紫, WaColor12.真紅
    )

    private val reimuBreak = shortArrayOf(
        WaColor12.墨色, WaColor12.紫鳶, WaColor12.黒橡, WaColor12.褐返,
        WaColor12.藍鼠, WaColor12.胡粉色, WaColor12.月白, WaColor12.櫻色,
        WaColor12.煤色, WaColor12.紅, WaColor12.山吹色, WaColor12.深緋,
        WaColor12.翡翠色, WaColor12.藍色, WaColor12.古代紫, WaColor12.紅緋
    )

    private val marisaFocus = shortArrayOf(
        WaColor12.褐返, WaColor12.墨色, WaColor12.海松茶, WaColor12.藍鉄,
        WaColor12.黒橡, WaColor12.胡粉色, WaColor12.月白, WaColor12.櫻色,
        WaColor12.煤色, WaColor12.山吹色, WaColor12.古代紫, WaColor12.緋色,
        WaColor12.翡翠色, WaColor12.藍鼠, WaColor12.藍色, WaColor12.紅
    )

    private val marisaBreak = shortArrayOf(
        WaColor12.墨色, WaColor12.海松茶, WaColor12.黒橡, WaColor12.褐返,
        WaColor12.藍鉄, WaColor12.胡粉色, WaColor12.月白, WaColor12.櫻色,
        WaColor12.煤色, WaColor12.山吹色, WaColor12.藤鼠, WaColor12.深緋,
        WaColor12.翡翠色, WaColor12.藍鼠, WaColor12.古代紫, WaColor12.紅緋
    )

    private val aliceFocus = shortArrayOf(
        WaColor12.紺色, WaColor12.藍鉄, WaColor12.古代紫, WaColor12.墨色,
        WaColor12.藍鼠, WaColor12.胡粉色, WaColor12.月白, WaColor12.櫻色,
        WaColor12.煤色, WaColor12.藤鼠, WaColor12.翡翠色, WaColor12.深緋,
        WaColor12.山吹色, WaColor12.藍色, WaColor12.紫鳶, WaColor12.紅緋
    )

    private val aliceBreak = shortArrayOf(
        WaColor12.藍鉄, WaColor12.紺色, WaColor12.紫鳶, WaColor12.墨色,
        WaColor12.藍鼠, WaColor12.胡粉色, WaColor12.月白, WaColor12.藤鼠,
        WaColor12.煤色, WaColor12.古代紫, WaColor12.翡翠色, WaColor12.緋色,
        WaColor12.山吹色, WaColor12.藍色, WaColor12.櫻色, WaColor12.真紅
    )

    private val kaguyaFocus = shortArrayOf(
        WaColor12.墨色, WaColor12.海松茶, WaColor12.黒橡, WaColor12.褐返,
        WaColor12.藍鼠, WaColor12.胡粉色, WaColor12.月白, WaColor12.櫻色,
        WaColor12.煤色, WaColor12.山吹色, WaColor12.古代紫, WaColor12.深緋,
        WaColor12.翡翠色, WaColor12.藍色, WaColor12.藤鼠, WaColor12.真紅
    )

    private val kaguyaBreak = shortArrayOf(
        WaColor12.褐返, WaColor12.海松茶, WaColor12.黒橡, WaColor12.藍鉄,
        WaColor12.藍鼠, WaColor12.胡粉色, WaColor12.月白, WaColor12.櫻色,
        WaColor12.煤色, WaColor12.翡翠色, WaColor12.山吹色, WaColor12.緋色,
        WaColor12.古代紫, WaColor12.藍色, WaColor12.紫鳶, WaColor12.紅
    )

    fun getColors(themeName: String, isBreak: Boolean) {
        val palette = when (themeName) {
            "marisa" -> if (isBreak) marisaBreak else marisaFocus
            "alice" -> if (isBreak) aliceBreak else aliceFocus
            "kaguya" -> if (isBreak) kaguyaBreak else kaguyaFocus
            else -> if (isBreak) reimuBreak else reimuFocus
        }
        Pc98GraphicsHardware.setupPalette(palette)
    }
}
