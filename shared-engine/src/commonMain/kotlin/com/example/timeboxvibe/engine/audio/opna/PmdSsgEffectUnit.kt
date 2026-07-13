package com.example.timeboxvibe.engine.audio.opna

/** Procedural approximation domain for PMD K/R SSG drum and effect patterns. */
internal class PmdSsgEffectUnit(sampleRate: Int) {
    private val generator = ProceduralDrums(sampleRate)
    private var triggerCount = 0

    fun trigger(kindOrdinal: Int, velocity: Float) {
        val kind = when (kindOrdinal) {
            0 -> ProceduralDrums.DrumKind.KICK
            1 -> ProceduralDrums.DrumKind.SNARE
            2 -> ProceduralDrums.DrumKind.HAT
            3 -> ProceduralDrums.DrumKind.TOM
            4 -> ProceduralDrums.DrumKind.CYMBAL
            5 -> ProceduralDrums.DrumKind.RIMSHOT
            else -> return
        }
        trigger(kind, velocity)
    }

    fun trigger(kind: ProceduralDrums.DrumKind, velocity: Float) {
        generator.setGain(kind, velocity.coerceAtLeast(0f))
        generator.setPan(kind, PAN_CENTER)
        when (kind) {
            ProceduralDrums.DrumKind.KICK -> generator.triggerKick()
            ProceduralDrums.DrumKind.SNARE -> generator.triggerSnare()
            ProceduralDrums.DrumKind.HAT -> generator.triggerHat()
            ProceduralDrums.DrumKind.TOM -> generator.triggerTom(TOM_FREQUENCY_HZ)
            ProceduralDrums.DrumKind.CYMBAL -> generator.triggerCymbal()
            ProceduralDrums.DrumKind.RIMSHOT -> generator.triggerRimshot()
        }
        triggerCount++
    }

    fun silence() {
        generator.silence()
    }

    fun reset() {
        generator.reset()
        triggerCount = 0
    }

    fun renderMono(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float, startFrame: Int = 0) {
        generator.render(buffer, frames, sampleRate, gainScale, startFrame)
    }

    fun renderStereo(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float, startFrame: Int = 0) {
        generator.renderStereo(buffer, frames, sampleRate, gainScale, startFrame)
    }

    internal fun triggerCountSnapshot(): Int = triggerCount

    internal fun generatorStateSnapshot(kind: ProceduralDrums.DrumKind): Int =
        generator.stateSnapshot(kind)

    internal fun generatorGainSnapshot(kind: ProceduralDrums.DrumKind): Float =
        generator.gainSnapshot(kind)

    private companion object {
        const val PAN_CENTER = 0
        const val TOM_FREQUENCY_HZ = 150f
    }
}
