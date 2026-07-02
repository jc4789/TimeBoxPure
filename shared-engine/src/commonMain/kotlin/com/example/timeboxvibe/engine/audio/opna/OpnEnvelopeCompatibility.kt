package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.abs
import kotlin.math.log10

/** Converts the existing seconds/level patch API into legal OPN register parameters. */
internal object OpnEnvelopeCompatibility {
    private val EG_TICKS_PER_SECOND =
        OpnPitch.MASTER_CLOCK_HZ.toDouble() /
            (OpnPitch.FM_CLOCK_DIVIDER.toDouble() * OpnPitch.EG_CLOCK_DIVIDER.toDouble())
    private const val MIN_LEVEL = 0.00001
    private val A4_PITCH = OpnPitch.nearestBlockFnumForMidi(69)
    private val A4_KEY_CODE = OpnPitch.keyCode(OpnPitch.block(A4_PITCH), OpnPitch.fnum(A4_PITCH))

    private val attackDurationByEffectiveRate = DoubleArray(64) { effectiveRate ->
        if (effectiveRate < 2) {
            Double.POSITIVE_INFINITY
        } else if (effectiveRate >= 62) {
            0.0
        } else {
            var attenuation = OpnRateEnvelope.MAX_ATTENUATION
            var egTicks = 0L
            while (attenuation > 0) {
                egTicks++
                val increment = OpnRateEnvelope.proceduralIncrement(effectiveRate, egTicks)
                if (increment > 0) attenuation += (attenuation.inv() * increment) shr 4
            }
            egTicks.toDouble() / EG_TICKS_PER_SECOND
        }
    }

    fun warmUp() = Unit

    fun configure(
        envelope: OpnRateEnvelope,
        spec: OperatorSpec,
        block: Int,
        fnum: Int,
        attackOverride: Float,
        decayOverride: Float,
        sustainOverride: Float,
        releaseOverride: Float
    ) {
        envelope.keyScale = spec.ks.coerceIn(0, 3)
        envelope.setKeyScale(block, fnum)
        val useLegacy = spec.egMode == EgMode.LEGACY_ADSR

        val sustain = if (sustainOverride >= 0f) sustainOverride else spec.sustain
        envelope.sustainLevel = if (useLegacy || sustainOverride >= 0f) {
            nearestSustainLevel(sustain)
        } else {
            spec.sl.coerceIn(0, 15)
        }
        envelope.attackRate = if (useLegacy || attackOverride >= 0f) {
            nearestAttackRate((if (attackOverride >= 0f) attackOverride else spec.attack).toDouble(), A4_KEY_CODE, spec.ks)
        } else {
            spec.ar.coerceIn(0, 31)
        }
        envelope.decayRate = if (useLegacy || decayOverride >= 0f) {
            val target = OpnRateEnvelope.sustainAttenuation(envelope.sustainLevel)
            nearestLinearRate(
                (if (decayOverride >= 0f) decayOverride else spec.decay).toDouble(),
                target,
                A4_KEY_CODE,
                spec.ks,
                release = false
            )
        } else {
            spec.dr.coerceIn(0, 31)
        }
        envelope.sustainRate = spec.sr.coerceIn(0, 31)
        envelope.releaseRate = if (useLegacy || releaseOverride >= 0f) {
            nearestLinearRate(
                (if (releaseOverride >= 0f) releaseOverride else spec.release).toDouble(),
                OpnRateEnvelope.MAX_ATTENUATION,
                A4_KEY_CODE,
                spec.ks,
                release = true
            )
        } else {
            spec.rr.coerceIn(0, 15)
        }
    }

    internal fun nearestSustainLevel(linearLevel: Float): Int {
        if (linearLevel <= MIN_LEVEL) return 15
        val wantedAttenuation = -20.0 * log10(linearLevel.coerceIn(MIN_LEVEL.toFloat(), 1f).toDouble()) /
            0.09375
        var best = 0
        var bestError = Double.MAX_VALUE
        var sl = 0
        while (sl <= 15) {
            val error = abs(OpnRateEnvelope.sustainAttenuation(sl).toDouble() - wantedAttenuation)
            if (error < bestError) {
                bestError = error
                best = sl
            }
            sl++
        }
        return best
    }

    internal fun nearestAttackRate(seconds: Double, keyCode: Int, keyScale: Int): Int {
        if (seconds <= 0.0) return 31
        var best = 1
        var bestError = Double.MAX_VALUE
        var rate = 1
        while (rate <= 31) {
            val ksr = OpnRateEnvelope.keyScaleValue(keyCode, keyScale)
            val effective = OpnRateEnvelope.effectiveRate(rate, ksr, release = false)
            val duration = attackDurationByEffectiveRate[effective]
            val error = abs(duration - seconds)
            if (error < bestError) {
                bestError = error
                best = rate
            }
            rate++
        }
        return best
    }

    internal fun nearestLinearRate(
        seconds: Double,
        attenuationDistance: Int,
        keyCode: Int,
        keyScale: Int,
        release: Boolean
    ): Int {
        if (attenuationDistance <= 0) return 0
        if (seconds <= 0.0) return if (release) 15 else 31
        val maxRate = if (release) 15 else 31
        var best = if (release) 0 else 1
        var bestError = Double.MAX_VALUE
        var rate = if (release) 0 else 1
        while (rate <= maxRate) {
            val ksr = OpnRateEnvelope.keyScaleValue(keyCode, keyScale)
            val effective = OpnRateEnvelope.effectiveRate(rate, ksr, release)
            val average = OpnRateEnvelope.averageIncrementPerEgTick(effective)
            if (average > 0.0) {
                val duration = attenuationDistance.toDouble() / (average * EG_TICKS_PER_SECOND)
                val error = abs(duration - seconds)
                if (error < bestError) {
                    bestError = error
                    best = rate
                }
            }
            rate++
        }
        return best
    }
}
