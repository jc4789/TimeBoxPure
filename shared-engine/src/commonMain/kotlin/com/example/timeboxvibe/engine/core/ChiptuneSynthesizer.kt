package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.ToneSpec
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random

object ChiptuneSynthesizer {

    fun computeEnvelope(ageMs: Int, spec: ToneSpec): Float {
        if (!spec.useADSR) {
            // Legacy envelope — identical to the original implementation
            return when {
                ageMs < 30 -> ageMs / 30f
                else -> {
                    val decayFactor = (ageMs - 30) / (spec.durationMs - 30).toFloat()
                    exp(-3.0 * decayFactor).toFloat()
                }
            }
        }
        // ADSR envelope
        val sustainEnd = (spec.durationMs - spec.releaseMs).coerceAtLeast(spec.attackMs + spec.decayMs)
        return when {
            ageMs < spec.attackMs -> {
                if (spec.attackMs > 0) ageMs.toFloat() / spec.attackMs else 1.0f
            }
            ageMs < spec.attackMs + spec.decayMs -> {
                val progress = (ageMs - spec.attackMs).toFloat() / spec.decayMs.coerceAtLeast(1)
                1.0f - (1.0f - spec.sustainLevel) * progress
            }
            ageMs < sustainEnd -> spec.sustainLevel
            else -> {
                val progress = (ageMs - sustainEnd).toFloat() / spec.releaseMs.coerceAtLeast(1)
                (spec.sustainLevel * (1.0f - progress)).coerceAtLeast(0f)
            }
        }
    }

    fun generateWaveform(phase: Double, t: Float, spec: ToneSpec): Float {
        return when (spec.type) {
            "square" -> if (phase < PI) 1.0f else -1.0f
            "pulse25" -> {
                // Pulse Width Modulation: sweeps duty cycle between 13% and 37% at 1.8Hz
                val dutyCycle = 0.25f + 0.12f * sin(2.0 * PI * 1.8 * t).toFloat()
                if (phase < 2.0 * PI * dutyCycle) 1.0f else -1.0f
            }
            "triangle" -> {
                val p = phase / (2.0 * PI)
                when {
                    p < 0.25 -> (4.0 * p).toFloat()
                    p < 0.75 -> (2.0 - 4.0 * p).toFloat()
                    else -> (4.0 * p - 4.0).toFloat()
                }
            }
            "noise" -> (Random.nextFloat().toFloat() * 2f - 1f)
            "noise-metallic" -> {
                // Sample-and-hold noise clocked at spec.freq → tonal metallic timbre
                val step = (spec.freq * t).toInt()
                val hash = (step * 1103515245 + 12345)
                (hash and 0x7FFF).toFloat() / 16384f - 1f
            }
            else -> sin(phase).toFloat()
        }
    }

    /** Soft-clip limiter to prevent harsh clipping with 4-channel mixes. */
    fun softClip(x: Float): Float = x / (1f + abs(x)) * 1.5f

    // Platform-independent audio chunk render function (allocation-free array updates)
    fun renderChunk(
        specs: List<ToneSpec>,
        shouldLoop: Boolean,
        chunkStartGlobal: Long,
        chunkSize: Int,
        sampleRate: Int,
        maxDurationSamples: Long,
        floatBuffer: FloatArray
    ) {
        floatBuffer.fill(0f)

        // Find active specs for this chunk without allocation inside sample loop
        for (idx in 0 until specs.size) {
            val s = specs[idx]
            val specStartSample = (s.startMs * sampleRate) / 1000L
            val specEndSample = specStartSample + (s.durationMs * sampleRate) / 1000L

            val hasVibrato = s.freq > 0f && (s.type == "pulse25" || s.type == "square" || s.type == "triangle")
            val freqFactor = 2.0 * PI * s.freq
            val vibratoFactor = if (hasVibrato) s.freq * (0.007 / 6.0) else 0.0
            val vibratoRateFactor = 2.0 * PI * 6.0

            for (i in 0 until chunkSize) {
                val globalSampleIdx = chunkStartGlobal + i
                val sampleInMelody = if (shouldLoop) {
                    globalSampleIdx % maxDurationSamples
                } else {
                    globalSampleIdx
                }

                // If looping, we also need to check boundary overlap wrap
                var overlaps = false
                var offsetInMelody = sampleInMelody

                if (shouldLoop) {
                    val chunkStartMelody = chunkStartGlobal % maxDurationSamples
                    val chunkEndMelody = chunkStartMelody + chunkSize
                    if (chunkEndMelody <= maxDurationSamples) {
                        overlaps = specStartSample < chunkEndMelody && specEndSample > chunkStartMelody
                    } else {
                        val overlapsPortionA = specStartSample < maxDurationSamples && specEndSample > chunkStartMelody
                        val overlapsPortionB = specStartSample < (chunkEndMelody % maxDurationSamples) && specEndSample > 0L
                        overlaps = overlapsPortionA || overlapsPortionB
                        // adjust offsetInMelody relative to the spec start
                        if (overlapsPortionB && sampleInMelody < (chunkEndMelody % maxDurationSamples)) {
                            // it wraps around, so it is in portion B
                            offsetInMelody = sampleInMelody
                        }
                    }
                } else {
                    overlaps = specStartSample < (chunkStartGlobal + chunkSize) && specEndSample > chunkStartGlobal
                }

                if (overlaps && offsetInMelody >= specStartSample && offsetInMelody < specEndSample) {
                    val relSample = (offsetInMelody - specStartSample).toInt()
                    val t = relSample.toFloat() / sampleRate
                    val ageMs = (relSample * 1000) / sampleRate

                    val phase = if (hasVibrato) {
                        freqFactor * t + vibratoFactor * (1.0 - cos(vibratoRateFactor * t))
                    } else {
                        freqFactor * t
                    }

                    val envelope = computeEnvelope(ageMs, s)
                    var sample = generateWaveform(phase % (2.0 * PI), t, s)
                    sample *= envelope * s.volume
                    floatBuffer[i] += sample
                }
            }
        }
    }
}
