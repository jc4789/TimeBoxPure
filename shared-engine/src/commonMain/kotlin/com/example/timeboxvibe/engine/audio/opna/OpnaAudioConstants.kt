package com.example.timeboxvibe.engine.audio.opna

object OpnaAudioConstants {
    // Do not change this to fix timbre or FM scaling; existing songs depend on this loudness.
    const val MASTER_GAIN: Float = 1.5f
    // LAW: Do not change MASTER_GAIN to fix FM timbre, feedback, modulation depth,
    // clipping, or perceived harshness. Existing songs depend on this loudness.
    // Fix those issues inside the FM synthesis core.
    const val LANE_GAIN_LEAD: Float = 0.5f
    const val LANE_GAIN_HARMONY: Float = 0.45f
    const val LANE_GAIN_BASS: Float = 0.45f
    const val LANE_GAIN_PERCUSSION: Float = 0.55f
}
