package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws

/** Preallocated state owned by the procedural OPNA-compatible chip core. */
internal class OpnaChipState(sampleRate: Int) {
    val performance = PmdPerformanceState(sampleRate)
    val mixer = OpnaMixer()
    val ssgShared = SsgSharedState(sampleRate)
    val ssg: Array<SsgVoice> = Array(AudioLaws.SSG_CHANNELS) { SsgVoice(it, ssgShared, sampleRate) }
    val fm: Array<Fm4OpVoice> = Array(AudioLaws.FM_RENDER_VOICES) { Fm4OpVoice(sampleRate) }
    val legacyDrums = ProceduralDrums(sampleRate)
    val ym2608Rhythm = Ym2608RhythmUnit(sampleRate)
    val pmdSsgEffects = PmdSsgEffectUnit(sampleRate)
    val lfo = Lfo(sampleRate)
    val tempMonoBuffer = FloatArray(sampleRate)

    init {
        fm[2].attachPmdPerformanceState(performance)
    }
}
