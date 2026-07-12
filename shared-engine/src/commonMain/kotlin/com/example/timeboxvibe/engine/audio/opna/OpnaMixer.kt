package com.example.timeboxvibe.engine.audio.opna

internal class OpnaMixer {
    private var selectedProfile = OpnaOutputProfile.TIMEBOX_LEGACY

    val ssgGain: Float get() = selectedProfile.ssgGain
    val fmGain: Float get() = selectedProfile.fmGain
    val rhythmGain: Float get() = selectedProfile.rhythmGain

    fun applyProfile(profile: OpnaOutputProfile) {
        selectedProfile = profile
    }
}
