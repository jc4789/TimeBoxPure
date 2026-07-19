package com.example.timeboxvibe.engine.audio

import kotlin.math.pow

internal object AudioLaws {
    const val FM_CHANNELS: Int = 6
    const val SSG_CHANNELS: Int = 3
    const val FM_OPERATORS: Int = 4
    const val SAMPLE_RATE: Int = 48000
    // FM is the reference bus; SSG and rhythm are balanced against it.
    const val FM_BUS_GAIN: Float = 0.86f
    const val SSG_GAIN_DB: Float = -6f
    val SSG_LEGACY_BUS_GAIN: Float = 10f.pow(SSG_GAIN_DB / 20f)
    // Initial PC-9801-86 analog-balance hypothesis; selectable, never song-selected.
    const val PC9801_86_SSG_TO_FM_RATIO: Float = 0.25f
    // Keeps simultaneous kick/snare/hat below the chip output knee at legal velocity.
    const val RHYTHM_BUS_GAIN: Float = 0.40f
    // One common post-bus reserve. Includes the retired 0.75 timeline gain once.
    const val CHIP_MIX_HEADROOM: Float = 0.60f

    const val OPNA_OUTPUT_GAIN: Float = 1.0f

}
