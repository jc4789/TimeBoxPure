package com.example.timeboxvibe.engine

import com.example.timeboxvibe.engine.core.TimerPreset

 /**
 * Get default presets matching the original i18n.js.
 * All 8 presets with their full localized configurations.
 */
fun getDefaultPresets(lang: String): List<TimerPreset> = when (lang) {
    "zh" -> listOf(
        TimerPreset(
            id = "dual_5",
            name = "三重奏　（Ｄｕａｌ．５）",
            mode = "dual.5",
            dualBigDuration = 3600,
            dualMidDuration = 900,
            dualSmallDuration = 300,
            alarmBehavior = "alarm",
            description = "６０分鐘総覧，１５分鐘鬧鐘循環，５分鐘秒表節拍。"
        ),
        TimerPreset(
            id = "dual_box",
            name = "経典双重時間箱",
            mode = "dual",
            dualBigDuration = 3600,
            dualSmallDuration = 90,
            alarmBehavior = "auto",
            description = "６０分鐘宏観時間箱，内含９０秒一輪的微観循環。"
        ),
        TimerPreset(
            id = "down_spiral",
            name = "逓減收縮螺旋",
            mode = "sequence",
            sequence = intArrayOf(600, 540, 480, 420, 360, 300, 240, 180, 120, 60),
            alarmBehavior = "alarm",
            description = "从１０分鐘逐漸縮減到１分鐘。対抗専注力逓減。"
        ),
        TimerPreset(
            id = "up_climb",
            name = "逓増起歩攀登",
            mode = "sequence",
            sequence = intArrayOf(60, 120, 180, 300, 480),
            alarmBehavior = "alarm",
            description = "从１分鐘起歩逐漸拉長到８分鐘。打破開始做事的惰性。"
        ),
        TimerPreset(
            id = "vibe_wave",
            name = "抛物線波浪律動",
            mode = "sequence",
            sequence = intArrayOf(60, 180, 300, 180, 60),
            alarmBehavior = "alarm",
            description = "起歩預熱、峰値聚焦、逐歩放松。"
        ),
        TimerPreset(
            id = "spiral_dual",
            name = "双重收縮螺旋",
            mode = "dual-sequence",
            sequence = intArrayOf(600, 300, 180, 120, 60),
            dualSmallDuration = 60,
            alarmBehavior = "alarm",
            description = "不断縮短的大時間段内，包含重復６０秒的行動循環。"
        ),
        TimerPreset(
            id = "classic_pom",
            name = "経典番茄箱",
            mode = "sequence",
            sequence = intArrayOf(1500, 300, 1500, 300),
            sequenceLabels = arrayOf("専注　１", "休息　１", "専注　２", "休息　２"),
            alarmBehavior = "alarm",
            description = "番茄鐘：２５分工作、５分休息、２５分工作、５分休息（６０分一輪）。"
        ),
        TimerPreset(
            id = "default_calendar",
            name = "時空日程　（Ｃａｌｅｎｄａｒ）",
            mode = "calendar",
            sequence = intArrayOf(1500, 300, 1500, 900),
            sequenceTypes = arrayOf("focus", "relax", "focus", "relax"),
            sequenceLabels = arrayOf("第一輪専注", "課間休整", "第二輪冲刺", "長假小憩"),
            alarmBehavior = "alarm",
            description = "２５分鐘専注、５分鐘放松、２５分鐘専注、１５分鐘大休息。"
        )
    )
    "ja" -> listOf(
        TimerPreset(
            id = "dual_5",
            name = "デュアル．５",
            mode = "dual.5",
            dualBigDuration = 3600,
            dualMidDuration = 900,
            dualSmallDuration = 300,
            alarmBehavior = "alarm",
            description = "６０分全体の制限時間に、１５分のアラームと５分の秒針ループがネストされています。"
        ),
        TimerPreset(
            id = "dual_box",
            name = "定番デュアル・ボックス",
            mode = "dual",
            dualBigDuration = 3600,
            dualSmallDuration = 90,
            alarmBehavior = "auto",
            description = "６０分の大きな枠の中で、９０秒の短い行動を何度も繰り返します。"
        ),
        TimerPreset(
            id = "down_spiral",
            name = "デクリメンタル（減少螺旋）",
            mode = "sequence",
            sequence = intArrayOf(600, 540, 480, 420, 360, 300, 240, 180, 120, 60),
            alarmBehavior = "alarm",
            description = "１０分から１分へと、タイマーが徐々に短くなります。"
        ),
        TimerPreset(
            id = "up_climb",
            name = "インクリメンタル（増加）",
            mode = "sequence",
            sequence = intArrayOf(60, 120, 180, 300, 480),
            alarmBehavior = "alarm",
            description = "１分から８分へ、徐々に長くして集中力をウォームアップさせます。"
        ),
        TimerPreset(
            id = "vibe_wave",
            name = "パラボリック（波形）",
            mode = "sequence",
            sequence = intArrayOf(60, 180, 300, 180, 60),
            alarmBehavior = "alarm",
            description = "ウォームアップ、ピーク時の集中、クールダウンの波形サイクル。"
        ),
        TimerPreset(
            id = "spiral_dual",
            name = "スパイラル・デュアル",
            mode = "dual-sequence",
            sequence = intArrayOf(600, 300, 180, 120, 60),
            dualSmallDuration = 60,
            alarmBehavior = "alarm",
            description = "縮小する時間ブロックの中で、６０秒の行動ループを繰り返します。"
        ),
        TimerPreset(
            id = "classic_pom",
            name = "クラシック・ポモドーロ",
            mode = "sequence",
            sequence = intArrayOf(1500, 300, 1500, 300),
            sequenceLabels = arrayOf("集中　１", "休憩　１", "集中　２", "休憩　２"),
            alarmBehavior = "alarm",
            description = "ポモドーロ：２５分作業、５分休憩、２５分作業、５分休憩（６０分１セット）。"
        ),
        TimerPreset(
            id = "default_calendar",
            name = "時空スケジュール　（Ｃａｌｅｎｄａｒ）",
            mode = "calendar",
            sequence = intArrayOf(1500, 300, 1500, 900),
            sequenceTypes = arrayOf("focus", "relax", "focus", "relax"),
            sequenceLabels = arrayOf("集中セッション　１", "ショート休憩", "集中セッション　２", "ロング休憩"),
            alarmBehavior = "alarm",
            description = "２５分集中、５分休憩、２５分集中、１５分ロング休憩のスケジュール。"
        )
    )
    else -> listOf(
        TimerPreset(
            id = "dual_5",
            name = "Ｄｕａｌ．５　Ｎｅｓｔｅｄ",
            mode = "dual.5",
            dualBigDuration = 3600,
            dualMidDuration = 900,
            dualSmallDuration = 300,
            alarmBehavior = "alarm",
            description = "３‐Ｔｉｅｒ：　６０ｍ　ｏｖｅｒａｌｌ．　１５ｍ　Ａｌａｒｍ　ｌｏｏｐ．　５ｍ　Ｓｔｏｐｗａｔｃｈ　ｌｏｏｐ．"
        ),
        TimerPreset(
            id = "dual_box",
            name = "Ｃｌａｓｓｉｃ　Ｄｕａｌ　Ｂｏｘ",
            mode = "dual",
            dualBigDuration = 3600,
            dualSmallDuration = 90,
            alarmBehavior = "auto",
            description = "６０ｍ　ｏｖｅｒａｌｌ　ｂｌｏｃｋ　ｃｏｎｔａｉｎｉｎｇ　ｌｏｏｐｉｎｇ　９０ｓ　ａｃｔｉｏｎ　ｔｉｃｋｓ．"
        ),
        TimerPreset(
            id = "down_spiral",
            name = "Ｄｏｗｎｗａｒｄ　Ｓｐｉｒａｌ",
            mode = "sequence",
            sequence = intArrayOf(600, 540, 480, 420, 360, 300, 240, 180, 120, 60),
            alarmBehavior = "alarm",
            description = "１０ｍ　ｄｏｗｎ　ｔｏ　１ｍ　ｓｅｑｕｅｎｃｅｓ．　Ｅｘｃｅｌｌｅｎｔ　ｆｏｒ　ｆａｄｉｎｇ　ｍｅｎｔａｌ　ｆｏｃｕｓ．"
        ),
        TimerPreset(
            id = "up_climb",
            name = "Ｗａｒｍｕｐ　Ｃｌｉｍｂ",
            mode = "sequence",
            sequence = intArrayOf(60, 120, 180, 300, 480),
            alarmBehavior = "alarm",
            description = "１ｍ　ｕｐ　ｔｏ　８ｍ　ｗａｒｍｕｐ　ｓｅｑｕｅｎｃｅ．　Ｈｅｌｐｓ　ｏｖｅｒｃｏｍｅ　ｓｔａｒｔｉｎｇ　ｉｎｅｒｔｉａ．"
        ),
        TimerPreset(
            id = "vibe_wave",
            name = "Ｐａｒａｂｏｌｉｃ　Ｗａｖｅ",
            mode = "sequence",
            sequence = intArrayOf(60, 180, 300, 180, 60),
            alarmBehavior = "alarm",
            description = "Ｐａｒａｂｏｌｉｃ　ｃｕｒｖｅ：　ｗａｒｍ　ｕｐ，　ｐｅａｋ　ｗｏｒｋ，　ａｎｄ　ｅａｓｅ　ｄｏｗｎ．"
        ),
        TimerPreset(
            id = "spiral_dual",
            name = "Ｓｐｉｒａｌ　Ｄｕａｌ",
            mode = "dual-sequence",
            sequence = intArrayOf(600, 300, 180, 120, 60),
            dualSmallDuration = 60,
            alarmBehavior = "alarm",
            description = "Ｄｅｃｒｅｍｅｎｔａｌ　ｂｉｇ　ｔｉｍｅｒ　ｂｌｏｃｋｓ　ｃｏｎｔａｉｎｉｎｇ　ｌｏｏｐｉｎｇ　６０ｓ　ａｃｔｉｏｎ　ｔａｓｋｓ．"
        ),
        TimerPreset(
            id = "classic_pom",
            name = "Ｃｌａｓｓｉｃ　Ｐｏｍｏｄｏｒｏ",
            mode = "sequence",
            sequence = intArrayOf(1500, 300, 1500, 300),
            sequenceLabels = arrayOf("Ｆｏｃｕｓ　１", "Ｂｒｅａｋ　１", "Ｆｏｃｕｓ　２", "Ｂｒｅａｋ　２"),
            alarmBehavior = "alarm",
            description = "Ｐｏｍｏｄｏｒｏ：　２５ｍ　ｗｏｒｋ，　５ｍ　ｒｅｌａｘ，　２５ｍ　ｗｏｒｋ，　５ｍ　ｒｅｌａｘ　（６０ｍ　ｓｅｔ）．"
        ),
        TimerPreset(
            id = "default_calendar",
            name = "Ｍｉｘｅｄ　Ｉｎｔｅｒｖａｌ　（Ｃａｌｅｎｄａｒ）",
            mode = "calendar",
            sequence = intArrayOf(1500, 300, 1500, 900),
            sequenceTypes = arrayOf("focus", "relax", "focus", "relax"),
            sequenceLabels = arrayOf("Ｆｏｃｕｓ　Ｓｅｓｓｉｏｎ　１", "Ｓｈｏｒｔ　Ｂｒｅａｋ", "Ｆｏｃｕｓ　Ｓｅｓｓｉｏｎ　２", "Ｌｏｎｇ　Ｂｒｅａｋ"),
            alarmBehavior = "alarm",
            description = "２５ｍ　ｆｏｃｕｓ，　５ｍ　ｒｅｌａｘ，　２５ｍ　ｆｏｃｕｓ，　１５ｍ　ｌｏｎｇ　ｂｒｅａｋ．"
        )
    )
}

// Fallback-only static value. Runtime selection should use getDefaultPresets(lang)
// plus user templates so localized/default presets never overwrite active choices.
val DEFAULT_PRESETS = getDefaultPresets("en")
