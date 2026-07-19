package com.example.timeboxvibe.engine.audio.opna

/** Allocation-free owner and bus router for the two Phase 1 percussion domains. */
internal class PercussionRouter(sampleRate: Int) {
    private val ym2608Rhythm = Ym2608RhythmUnit(sampleRate)
    private val pmdSsgEffect = PmdSsgEffectUnit(sampleRate)
    private var activeDomainMask = 0

    fun shotAuthoredYm(drumKindOrdinal: Int, velocity: Float, pan: Int) {
        if (ym2608Rhythm.shotAuthored(drumKindOrdinal, velocity, pan)) {
            activeDomainMask = activeDomainMask or DOMAIN_YM2608_RHYTHM
        }
    }

    fun shotYm(mask: Int) {
        if ((mask and YM_VOICE_MASK) == 0) return
        ym2608Rhythm.shot(mask)
        activeDomainMask = activeDomainMask or DOMAIN_YM2608_RHYTHM
    }

    fun dumpYm(mask: Int) {
        ym2608Rhythm.dump(mask)
        refreshYmActiveBit()
    }

    fun setYmMasterLevel(value: Int, relative: Boolean) {
        ym2608Rhythm.setMasterLevel(value, relative)
    }

    fun setYmVoiceLevel(voice: Int, value: Int, relative: Boolean) {
        ym2608Rhythm.setVoiceLevel(voice, value, relative)
    }

    fun setYmVoicePan(voice: Int, pan: Int) {
        ym2608Rhythm.setVoicePan(voice, pan)
    }

    fun triggerPmdSsgEffect(kindOrdinal: Int, velocity: Float) {
        pmdSsgEffect.trigger(kindOrdinal, velocity)
        if (kindOrdinal in 0 until YM_VOICE_COUNT) {
            activeDomainMask = activeDomainMask or DOMAIN_PMD_SSG_EFFECT
        }
    }

    fun triggerPmdSsgEffect(kind: ProceduralDrums.DrumKind, velocity: Float) {
        pmdSsgEffect.trigger(kind, velocity)
        activeDomainMask = activeDomainMask or DOMAIN_PMD_SSG_EFFECT
    }

    fun renderMono(
        rhythmBus: FloatArray,
        ssgBus: FloatArray,
        frames: Int,
        sampleRate: Int,
        startFrame: Int = 0
    ) {
        if ((activeDomainMask and DOMAIN_YM2608_RHYTHM) != 0) {
            ym2608Rhythm.renderMono(rhythmBus, frames, sampleRate, 1f, startFrame)
            refreshYmActiveBit()
        }
        if ((activeDomainMask and DOMAIN_PMD_SSG_EFFECT) != 0) {
            pmdSsgEffect.renderMono(ssgBus, frames, sampleRate, 1f, startFrame)
            refreshPmdActiveBit()
        }
    }

    fun renderStereo(
        rhythmBus: FloatArray,
        ssgBus: FloatArray,
        frames: Int,
        sampleRate: Int,
        startFrame: Int = 0
    ) {
        if ((activeDomainMask and DOMAIN_YM2608_RHYTHM) != 0) {
            ym2608Rhythm.renderStereo(rhythmBus, frames, sampleRate, 1f, startFrame)
            refreshYmActiveBit()
        }
        if ((activeDomainMask and DOMAIN_PMD_SSG_EFFECT) != 0) {
            pmdSsgEffect.renderStereo(ssgBus, frames, sampleRate, 1f, startFrame)
            refreshPmdActiveBit()
        }
    }

    /** Stops active sound while preserving YM registers and PMD trigger history. */
    fun stop() {
        ym2608Rhythm.silence()
        pmdSsgEffect.silence()
        activeDomainMask = 0
    }

    fun reset() {
        ym2608Rhythm.reset()
        pmdSsgEffect.reset()
        activeDomainMask = 0
    }

    internal fun ymMasterLevelSnapshot(): Int = ym2608Rhythm.masterLevelSnapshot()
    internal fun ymVoiceLevelSnapshot(voice: Int): Int = ym2608Rhythm.voiceLevelSnapshot(voice)
    internal fun ymVoicePanSnapshot(voice: Int): Int = ym2608Rhythm.voicePanSnapshot(voice)
    internal fun ymGeneratorStateSnapshot(voice: Int): Int = ym2608Rhythm.generatorStateSnapshot(voice)
    internal fun ymGeneratorGainSnapshot(voice: Int): Float = ym2608Rhythm.generatorGainSnapshot(voice)
    internal fun ymGeneratorPanSnapshot(voice: Int): Int = ym2608Rhythm.generatorPanSnapshot(voice)
    internal fun pmdTriggerCountSnapshot(): Int = pmdSsgEffect.triggerCountSnapshot()
    internal fun pmdGeneratorStateSnapshot(kind: ProceduralDrums.DrumKind): Int =
        pmdSsgEffect.generatorStateSnapshot(kind)
    internal fun pmdGeneratorGainSnapshot(kind: ProceduralDrums.DrumKind): Float =
        pmdSsgEffect.generatorGainSnapshot(kind)
    internal fun activeDomainMaskSnapshot(): Int = activeDomainMask

    private fun refreshYmActiveBit() {
        activeDomainMask = if (ym2608Rhythm.hasActiveVoices()) {
            activeDomainMask or DOMAIN_YM2608_RHYTHM
        } else {
            activeDomainMask and DOMAIN_YM2608_RHYTHM.inv()
        }
    }

    private fun refreshPmdActiveBit() {
        activeDomainMask = if (pmdSsgEffect.hasActiveVoices()) {
            activeDomainMask or DOMAIN_PMD_SSG_EFFECT
        } else {
            activeDomainMask and DOMAIN_PMD_SSG_EFFECT.inv()
        }
    }

    companion object {
        const val DOMAIN_YM2608_RHYTHM = 1
        const val DOMAIN_PMD_SSG_EFFECT = 2
        const val ALL_DOMAINS = DOMAIN_YM2608_RHYTHM or DOMAIN_PMD_SSG_EFFECT
        private const val YM_VOICE_COUNT = 6
        private const val YM_VOICE_MASK = (1 shl YM_VOICE_COUNT) - 1
    }
}
