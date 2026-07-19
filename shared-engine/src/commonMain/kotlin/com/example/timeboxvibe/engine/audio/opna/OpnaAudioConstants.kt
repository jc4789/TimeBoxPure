package com.example.timeboxvibe.engine.audio.opna

object OpnaAudioConstants {
    // Do not change this to fix timbre or FM scaling; existing songs depend on this loudness.
    const val MASTER_GAIN: Float = 1.5f
    // LAW: Do not change MASTER_GAIN to fix FM timbre, feedback, modulation depth,
    // clipping, or perceived harshness. Existing songs depend on this loudness.
    // Fix those issues inside the FM synthesis core.
}
