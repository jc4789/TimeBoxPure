package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws

/** Preallocated state owned by the procedural OPNA-compatible chip core. */
internal class OpnaChipState(sampleRate: Int, enableFmOversampling: Boolean) {
    val ssgShared = SsgSharedState(sampleRate)
    val ssg: Array<SsgVoice> = Array(AudioLaws.SSG_CHANNELS) { SsgVoice(it, ssgShared, sampleRate) }
    val fm: Array<Fm4OpVoice> = Array(AudioLaws.FM_CHANNELS) {
        Fm4OpVoice(sampleRate, enableFmOversampling)
    }
    val percussion = PercussionRouter(sampleRate)
    val lfo = Lfo(sampleRate)
}
