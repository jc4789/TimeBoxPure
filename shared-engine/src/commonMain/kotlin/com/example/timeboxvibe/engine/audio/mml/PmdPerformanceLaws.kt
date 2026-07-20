package com.example.timeboxvibe.engine.audio.mml

/** Named PMD driver units shared by MML setup and allocation-free playback. */
object PmdPerformanceLaws {
    const val BPM_MILLI_SCALE = 1_000
    const val DEFAULT_CLOCKS_PER_QUARTER = 24
    const val ENVELOPE_CLOCK_NORMAL = 0
    const val ENVELOPE_CLOCK_EXTENDED = 1
    const val ENVELOPE_DISABLED = 0
    const val ENVELOPE_LEGACY = 1
    const val ENVELOPE_EXTENDED = 2

    // PMD's manual specifies EX1 as approximately 56 Hz.
    const val EXTENDED_ENVELOPE_CLOCK_MILLIHERTZ = 56_000L
    const val LFO_CLOCK_NORMAL = 0
    const val LFO_CLOCK_FIXED = 1
    const val FIXED_LFO_CLOCK_MILLIHERTZ = 56_000L
    const val SOFTWARE_LFO_RANDOM_SEED = 0x13579BDF

    // Stable compile-time gate randomization seed; loop playback reuses the
    // resulting primitive gate clocks exactly.
    const val GATE_RANDOM_SEED = 0x6D2B79F5
}
