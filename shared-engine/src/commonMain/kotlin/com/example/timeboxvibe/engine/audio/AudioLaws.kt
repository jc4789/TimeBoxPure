package com.example.timeboxvibe.engine.audio

internal object AudioLaws {
    const val FM_CHANNELS: Int = 6
    // Authored polyphonic chords retain the six OPNA control parts while using
    // a fixed software voice pool large enough for dense piano-score voicings.
    const val FM_RENDER_VOICES: Int = 16
    const val SSG_CHANNELS: Int = 3
    const val FM_OPERATORS: Int = 4
    const val SAMPLE_RATE: Int = 48000
    // FM is the reference bus; SSG and rhythm are balanced against it.
    const val FM_BUS_GAIN: Float = 1.0f
    const val SSG_GAIN_DB: Float = -18f
    // Keeps simultaneous kick/snare/hat below the chip output knee at legal velocity.
    const val RHYTHM_BUS_GAIN: Float = 0.35f
    // Fixed chip summing reserve before the unchanged application master gain.
    const val CHIP_MIX_HEADROOM: Float = 0.80f

    const val OPNA_OUTPUT_GAIN: Float = 1.0f

}
