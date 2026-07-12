package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws

/** Explicit board/migration hypotheses; song identity never selects a profile. */
enum class OpnaOutputProfile(
    internal val fmGain: Float,
    internal val ssgGain: Float,
    internal val rhythmGain: Float
) {
    TIMEBOX_LEGACY(
        AudioLaws.FM_BUS_GAIN,
        AudioLaws.SSG_LEGACY_BUS_GAIN,
        AudioLaws.RHYTHM_BUS_GAIN
    ),
    PC9801_86_REFERENCE(
        AudioLaws.FM_BUS_GAIN,
        AudioLaws.FM_BUS_GAIN * AudioLaws.PC9801_86_SSG_TO_FM_RATIO,
        AudioLaws.RHYTHM_BUS_GAIN
    )
}
