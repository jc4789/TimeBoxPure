package com.example.timeboxvibe.engine.audio.opna

/** Six-voice YM2608 rhythm-register state with a clean-room procedural generator. */
internal class Ym2608RhythmUnit(sampleRate: Int) {
    private val generator = ProceduralDrums(sampleRate)
    private var masterLevel = DEFAULT_MASTER_LEVEL
    private val voiceLevel = IntArray(VOICE_COUNT) { DEFAULT_VOICE_LEVEL }
    private val voicePan = IntArray(VOICE_COUNT)

    init {
        refreshAllVoices()
    }

    fun shot(mask: Int) {
        var voice = 0
        while (voice < VOICE_COUNT) {
            if ((mask and (1 shl voice)) != 0) {
                refreshVoice(voice)
                trigger(generator, drumKind(voice))
            }
            voice++
        }
    }

    fun dump(mask: Int) {
        var voice = 0
        while (voice < VOICE_COUNT) {
            if ((mask and (1 shl voice)) != 0) generator.dump(drumKind(voice))
            voice++
        }
    }

    fun setMasterLevel(value: Int) {
        masterLevel = value.coerceIn(0, MASTER_LEVEL_MAX)
        refreshAllVoices()
    }

    fun setVoiceLevel(voice: Int, value: Int) {
        if (voice < 0 || voice >= VOICE_COUNT) return
        voiceLevel[voice] = value.coerceIn(0, VOICE_LEVEL_MAX)
        refreshVoice(voice)
    }

    fun setVoicePan(voice: Int, pan: Int) {
        if (voice < 0 || voice >= VOICE_COUNT) return
        voicePan[voice] = pan.coerceIn(PAN_MIN, PAN_MAX)
        generator.setPan(drumKind(voice), voicePan[voice])
    }

    fun silence() {
        generator.silence()
    }

    fun reset() {
        generator.reset()
        masterLevel = DEFAULT_MASTER_LEVEL
        var voice = 0
        while (voice < VOICE_COUNT) {
            voiceLevel[voice] = DEFAULT_VOICE_LEVEL
            voicePan[voice] = PAN_CENTER
            voice++
        }
        refreshAllVoices()
    }

    fun renderMono(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float, startFrame: Int = 0) {
        generator.render(buffer, frames, sampleRate, gainScale, startFrame)
    }

    fun renderStereo(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float, startFrame: Int = 0) {
        generator.renderStereo(buffer, frames, sampleRate, gainScale, startFrame)
    }

    internal fun masterLevelSnapshot(): Int = masterLevel

    internal fun voiceLevelSnapshot(voice: Int): Int =
        voiceLevel[voice.coerceIn(0, VOICE_COUNT - 1)]

    internal fun voicePanSnapshot(voice: Int): Int =
        voicePan[voice.coerceIn(0, VOICE_COUNT - 1)]

    internal fun generatorStateSnapshot(voice: Int): Int =
        generator.stateSnapshot(drumKind(voice.coerceIn(0, VOICE_COUNT - 1)))

    internal fun generatorGainSnapshot(voice: Int): Float =
        generator.gainSnapshot(drumKind(voice.coerceIn(0, VOICE_COUNT - 1)))

    private fun refreshAllVoices() {
        var voice = 0
        while (voice < VOICE_COUNT) {
            refreshVoice(voice)
            voice++
        }
    }

    private fun refreshVoice(voice: Int) {
        val gain = masterLevel.toFloat() / MASTER_LEVEL_MAX.toFloat() *
            voiceLevel[voice].toFloat() / VOICE_LEVEL_MAX.toFloat()
        val kind = drumKind(voice)
        generator.setGain(kind, gain)
        generator.setPan(kind, voicePan[voice])
    }

    private fun drumKind(voice: Int): ProceduralDrums.DrumKind = when (voice) {
        0 -> ProceduralDrums.DrumKind.KICK
        1 -> ProceduralDrums.DrumKind.SNARE
        2 -> ProceduralDrums.DrumKind.CYMBAL
        3 -> ProceduralDrums.DrumKind.HAT
        4 -> ProceduralDrums.DrumKind.TOM
        else -> ProceduralDrums.DrumKind.RIMSHOT
    }

    private fun trigger(drums: ProceduralDrums, kind: ProceduralDrums.DrumKind) {
        when (kind) {
            ProceduralDrums.DrumKind.KICK -> drums.triggerKick()
            ProceduralDrums.DrumKind.SNARE -> drums.triggerSnare()
            ProceduralDrums.DrumKind.HAT -> drums.triggerHat()
            ProceduralDrums.DrumKind.TOM -> drums.triggerTom(TOM_FREQUENCY_HZ)
            ProceduralDrums.DrumKind.CYMBAL -> drums.triggerCymbal()
            ProceduralDrums.DrumKind.RIMSHOT -> drums.triggerRimshot()
        }
    }

    private companion object {
        const val VOICE_COUNT = 6
        const val DEFAULT_MASTER_LEVEL = 48
        const val DEFAULT_VOICE_LEVEL = 31
        const val MASTER_LEVEL_MAX = 63
        const val VOICE_LEVEL_MAX = 31
        const val PAN_CENTER = 0
        const val PAN_MIN = 0
        const val PAN_MAX = 2
        const val TOM_FREQUENCY_HZ = 150f
    }
}
