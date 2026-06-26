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
            name = "三重奏 (Dual.5)",
            mode = "dual.5",
            dualBigDuration = 3600,
            dualMidDuration = 900,
            dualSmallDuration = 300,
            alarmBehavior = "alarm",
            description = "60分钟总览，15分钟闹钟循环，5分钟秒表节拍。"
        ),
        TimerPreset(
            id = "dual_box",
            name = "经典双重时间箱",
            mode = "dual",
            dualBigDuration = 3600,
            dualSmallDuration = 90,
            alarmBehavior = "auto",
            description = "60分钟宏观时间箱，内含90秒一轮的微观循环。"
        ),
        TimerPreset(
            id = "down_spiral",
            name = "递减收缩螺旋",
            mode = "sequence",
            sequence = intArrayOf(600, 540, 480, 420, 360, 300, 240, 180, 120, 60),
            alarmBehavior = "alarm",
            description = "从10分钟逐渐缩减到1分钟。对抗专注力递减。"
        ),
        TimerPreset(
            id = "up_climb",
            name = "递增起步攀登",
            mode = "sequence",
            sequence = intArrayOf(60, 120, 180, 300, 480),
            alarmBehavior = "alarm",
            description = "从1分钟起步逐渐拉长到8分钟。打破开始做事的惰性。"
        ),
        TimerPreset(
            id = "vibe_wave",
            name = "抛物线波浪律动",
            mode = "sequence",
            sequence = intArrayOf(60, 180, 300, 180, 60),
            alarmBehavior = "alarm",
            description = "起步预热、峰值聚焦、逐步放松。"
        ),
        TimerPreset(
            id = "spiral_dual",
            name = "双重收缩螺旋",
            mode = "dual-sequence",
            sequence = intArrayOf(600, 300, 180, 120, 60),
            dualSmallDuration = 60,
            alarmBehavior = "alarm",
            description = "不断缩短的大时间段内，包含重复60秒的行动循环。"
        ),
        TimerPreset(
            id = "classic_pom",
            name = "经典单时间箱",
            mode = "classic",
            sequence = intArrayOf(1500),
            alarmBehavior = "alarm",
            description = "传统25分钟的单一计时框。"
        ),
        TimerPreset(
            id = "default_calendar",
            name = "时空日程 (Calendar)",
            mode = "calendar",
            sequence = intArrayOf(1500, 300, 1500, 900),
            sequenceTypes = arrayOf("focus", "relax", "focus", "relax"),
            sequenceLabels = arrayOf("第一轮专注", "课间休整", "第二轮冲刺", "长假小憩"),
            alarmBehavior = "alarm",
            description = "25分钟专注、5分钟放松、25分钟专注、15分钟大休息。"
        )
    )
    "ja" -> listOf(
        TimerPreset(
            id = "dual_5",
            name = "デュアル.5",
            mode = "dual.5",
            dualBigDuration = 3600,
            dualMidDuration = 900,
            dualSmallDuration = 300,
            alarmBehavior = "alarm",
            description = "60分全体の制限時間に、15分のアラームと5分の秒針ループがネストされています。"
        ),
        TimerPreset(
            id = "dual_box",
            name = "定番デュアル・ボックス",
            mode = "dual",
            dualBigDuration = 3600,
            dualSmallDuration = 90,
            alarmBehavior = "auto",
            description = "60分の大きな枠の中で、90秒の短い行動を何度も繰り返します。"
        ),
        TimerPreset(
            id = "down_spiral",
            name = "デクリメンタル（減少螺旋）",
            mode = "sequence",
            sequence = intArrayOf(600, 540, 480, 420, 360, 300, 240, 180, 120, 60),
            alarmBehavior = "alarm",
            description = "10分から1分へと、タイマーが徐々に短くなります。"
        ),
        TimerPreset(
            id = "up_climb",
            name = "インクリメンタル（増加）",
            mode = "sequence",
            sequence = intArrayOf(60, 120, 180, 300, 480),
            alarmBehavior = "alarm",
            description = "1分から8分へ、徐々に長くして集中力をウォームアップさせます。"
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
            description = "縮小する時間ブロックの中で、60秒の行動ループを繰り返します。"
        ),
        TimerPreset(
            id = "classic_pom",
            name = "クラシック・ボックス",
            mode = "classic",
            sequence = intArrayOf(1500),
            alarmBehavior = "alarm",
            description = "お馴染みの25分間シングルカウントダウンタイマー。"
        ),
        TimerPreset(
            id = "default_calendar",
            name = "時空スケジュール (Calendar)",
            mode = "calendar",
            sequence = intArrayOf(1500, 300, 1500, 900),
            sequenceTypes = arrayOf("focus", "relax", "focus", "relax"),
            sequenceLabels = arrayOf("集中セッション 1", "ショート休憩", "集中セッション 2", "ロング休憩"),
            alarmBehavior = "alarm",
            description = "25分集中、5分休憩、25分集中、15分ロング休憩のスケジュール。"
        )
    )
    else -> listOf(
        TimerPreset(
            id = "dual_5",
            name = "Dual.5 Nested",
            mode = "dual.5",
            dualBigDuration = 3600,
            dualMidDuration = 900,
            dualSmallDuration = 300,
            alarmBehavior = "alarm",
            description = "3-Tier: 60m overall. 15m Alarm loop. 5m Stopwatch loop."
        ),
        TimerPreset(
            id = "dual_box",
            name = "Classic Dual Box",
            mode = "dual",
            dualBigDuration = 3600,
            dualSmallDuration = 90,
            alarmBehavior = "auto",
            description = "60m overall block containing looping 90s action ticks."
        ),
        TimerPreset(
            id = "down_spiral",
            name = "Downward Spiral",
            mode = "sequence",
            sequence = intArrayOf(600, 540, 480, 420, 360, 300, 240, 180, 120, 60),
            alarmBehavior = "alarm",
            description = "10m down to 1m sequences. Excellent for fading mental focus."
        ),
        TimerPreset(
            id = "up_climb",
            name = "Warmup Climb",
            mode = "sequence",
            sequence = intArrayOf(60, 120, 180, 300, 480),
            alarmBehavior = "alarm",
            description = "1m up to 8m warmup sequence. Helps overcome starting inertia."
        ),
        TimerPreset(
            id = "vibe_wave",
            name = "Parabolic Wave",
            mode = "sequence",
            sequence = intArrayOf(60, 180, 300, 180, 60),
            alarmBehavior = "alarm",
            description = "Parabolic curve: warm up, peak work, and ease down."
        ),
        TimerPreset(
            id = "spiral_dual",
            name = "Spiral Dual",
            mode = "dual-sequence",
            sequence = intArrayOf(600, 300, 180, 120, 60),
            dualSmallDuration = 60,
            alarmBehavior = "alarm",
            description = "Decremental big timer blocks containing looping 60s action tasks."
        ),
        TimerPreset(
            id = "classic_pom",
            name = "Classic Box",
            mode = "classic",
            sequence = intArrayOf(1500),
            alarmBehavior = "alarm",
            description = "A standard 25-minute singular countdown box."
        ),
        TimerPreset(
            id = "default_calendar",
            name = "Mixed Interval (Calendar)",
            mode = "calendar",
            sequence = intArrayOf(1500, 300, 1500, 900),
            sequenceTypes = arrayOf("focus", "relax", "focus", "relax"),
            sequenceLabels = arrayOf("Focus Session 1", "Short Break", "Focus Session 2", "Long Break"),
            alarmBehavior = "alarm",
            description = "25m focus, 5m relax, 25m focus, 15m long break."
        )
    )
}

// Fallback-only static value. Runtime selection should use getDefaultPresets(lang)
// plus user templates so localized/default presets never overwrite active choices.
val DEFAULT_PRESETS = getDefaultPresets("en")
