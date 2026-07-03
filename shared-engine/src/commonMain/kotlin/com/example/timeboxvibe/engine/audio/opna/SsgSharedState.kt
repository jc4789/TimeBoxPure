package com.example.timeboxvibe.engine.audio.opna

/** Shared noise and hardware-envelope generators used by all three SSG voices. */
class SsgSharedState(private val sampleRate: Int = 48_000) {
    private val noiseBuffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private val envelopeBuffer = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private var noisePhase: UInt = 0u
    private var noiseStep: UInt = 0u
    private var envelopePhase: UInt = 0u
    private var envelopeStep: UInt = 0u
    private var lfsr: Int = 0x1ffff
    private var noiseOutput: Float = 1f
    private var envelopeShape: Int = 9
    private var envelopeLevel: Int = 31
    private var envelopeDirection: Int = -1
    private var envelopeHolding: Boolean = false

    fun configureNoise(period: Int) {
        val divider = period.coerceIn(1, 31)
        noiseStep = frequencyStep(OpnPitch.MASTER_CLOCK_HZ.toDouble() / (64.0 * divider.toDouble()))
    }

    fun configureEnvelope(shape: Int, period: Int, restart: Boolean) {
        envelopeShape = shape and 15
        val divider = period.coerceIn(1, 65_535)
        envelopeStep = frequencyStep(OpnPitch.MASTER_CLOCK_HZ.toDouble() / (256.0 * divider.toDouble()))
        if (restart) restartEnvelope()
    }

    fun prepare(frames: Int) {
        val count = frames.coerceAtMost(noiseBuffer.size)
        var i = 0
        while (i < count) {
            val oldNoise = noisePhase
            noisePhase += noiseStep
            if (noisePhase < oldNoise) clockNoise()
            noiseBuffer[i] = noiseOutput

            val oldEnvelope = envelopePhase
            envelopePhase += envelopeStep
            if (envelopePhase < oldEnvelope) clockEnvelopeStep()
            envelopeBuffer[i] = envelopeLevel.coerceIn(0, 31) ushr 1
            i++
        }
    }

    internal fun noiseAt(frame: Int): Float = noiseBuffer[frame]
    internal fun envelopeAt(frame: Int): Int = envelopeBuffer[frame]

    fun reset() {
        noisePhase = 0u
        envelopePhase = 0u
        lfsr = 0x1ffff
        noiseOutput = 1f
        restartEnvelope()
    }

    private fun frequencyStep(frequencyHz: Double): UInt =
        (frequencyHz * UINT_CYCLE / sampleRate.coerceAtLeast(1).toDouble()).toLong().toUInt()

    private fun clockNoise() {
        val feedback = (lfsr xor (lfsr ushr 3)) and 1
        lfsr = (lfsr ushr 1) or (feedback shl 16)
        noiseOutput = if ((lfsr and 1) == 0) -1f else 1f
    }

    private fun restartEnvelope() {
        envelopeHolding = false
        val attack = (envelopeShape and 4) != 0
        envelopeLevel = if (attack) 0 else 31
        envelopeDirection = if (attack) 1 else -1
    }

    private fun clockEnvelopeStep() {
        if (envelopeHolding) return
        envelopeLevel += envelopeDirection
        if (envelopeLevel in 0..31) return

        val continues = (envelopeShape and 8) != 0
        val alternate = (envelopeShape and 2) != 0
        val hold = (envelopeShape and 1) != 0
        if (!continues) {
            envelopeLevel = 0
            envelopeHolding = true
            return
        }
        if (alternate) envelopeDirection = -envelopeDirection
        envelopeLevel = if (envelopeDirection > 0) 0 else 31
        if (hold) envelopeHolding = true
    }

    private companion object {
        const val UINT_CYCLE = 4_294_967_296.0
    }
}
