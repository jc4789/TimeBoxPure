package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.core.FastMath
import kotlin.math.exp

class ProceduralDrums {
    companion object {
        const val IDLE = 0
        const val ATTACK = 1
        const val DECAY = 2
        const val OFF = 3
    }

    // Drum kind enum representation (indices)
    enum class DrumKind {
        KICK, SNARE, HAT, TOM
    }

    private val kickNoise = LfsrNoise(0xBEEF xor 1)
    private val snareNoise = LfsrNoise(0xBEEF xor 2)
    private val hatNoise = LfsrNoise(0xFACE xor 3)

    // Kick state
    private var kickState: Int = IDLE
    private var kickLevel: Float = 0f
    private var kickAgeSamples: Int = 0
    private var kickPhase01: Float = 0f

    // Snare state
    private var snareState: Int = IDLE
    private var snareLevel: Float = 0f
    private var snareAgeSamples: Int = 0
    private var snarePhase01_1: Float = 0f
    private var snarePhase01_2: Float = 0f

    // Hat state
    private var hatState: Int = IDLE
    private var hatLevel: Float = 0f
    private var hatAgeSamples: Int = 0
    private var hatLastNoise: Float = 0f

    // Tom state
    private var tomState: Int = IDLE
    private var tomLevel: Float = 0f
    private var tomAgeSamples: Int = 0
    private var tomPhase01: Float = 0f
    private var tomStartFreq: Float = 150f

    fun triggerKick() {
        kickState = DECAY
        kickLevel = 1f
        kickAgeSamples = 0
        kickPhase01 = 0f
    }

    fun triggerSnare() {
        snareState = DECAY
        snareLevel = 1f
        snareAgeSamples = 0
        snarePhase01_1 = 0f
        snarePhase01_2 = 0f
    }

    fun triggerHat() {
        hatState = DECAY
        hatLevel = 1f
        hatAgeSamples = 0
        hatLastNoise = 0f
    }

    fun triggerTom(freq: Float) {
        tomState = DECAY
        tomLevel = 1f
        tomAgeSamples = 0
        tomPhase01 = 0f
        tomStartFreq = freq
    }

    fun stopAll() {
        reset()
    }

    fun reset() {
        kickState = IDLE
        kickLevel = 0f
        kickAgeSamples = 0
        kickPhase01 = 0f

        snareState = IDLE
        snareLevel = 0f
        snareAgeSamples = 0
        snarePhase01_1 = 0f
        snarePhase01_2 = 0f

        hatState = IDLE
        hatLevel = 0f
        hatAgeSamples = 0
        hatLastNoise = 0f

        tomState = IDLE
        tomLevel = 0f
        tomAgeSamples = 0
        tomPhase01 = 0f
        tomStartFreq = 150f

        kickNoise.reset(0xBEEF xor 1)
        snareNoise.reset(0xBEEF xor 2)
        hatNoise.reset(0xFACE xor 3)
    }

    fun render(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float) {
        var i = 0
        while (i < frames) {
            var mixedSample = 0f

            // Kick: pitch-dropping sine, 120 Hz -> 50 Hz over 50 ms
            if (kickState == DECAY) {
                val ageMs = (kickAgeSamples * 1000f) / sampleRate
                val freq = 50f + 70f * exp(-ageMs / 12f)
                kickPhase01 += freq / sampleRate
                if (kickPhase01 >= 1f) kickPhase01 -= 1f

                val sineValue = FastMath.fastSin((kickPhase01 * 1024).toInt() and 1023)
                val env = exp(-ageMs / 70f)
                mixedSample += sineValue * env * kickLevel

                kickAgeSamples++
                if (ageMs > 250f || env < 0.001f) {
                    kickState = IDLE
                    kickLevel = 0f
                }
            }

            // Snare: LFSR noise + sum of two sine partials (180 Hz + 330 Hz)
            if (snareState == DECAY) {
                val ageMs = (snareAgeSamples * 1000f) / sampleRate
                snarePhase01_1 += 180f / sampleRate
                if (snarePhase01_1 >= 1f) snarePhase01_1 -= 1f
                snarePhase01_2 += 330f / sampleRate
                if (snarePhase01_2 >= 1f) snarePhase01_2 -= 1f

                val partials = 0.3f * FastMath.fastSin((snarePhase01_1 * 1024).toInt() and 1023) +
                               0.3f * FastMath.fastSin((snarePhase01_2 * 1024).toInt() and 1023)
                val noiseSig = snareNoise.next()
                val signal = noiseSig * 0.5f + partials
                val env = exp(-ageMs / 25f)
                mixedSample += signal * env * snareLevel

                snareAgeSamples++
                if (ageMs > 150f || env < 0.001f) {
                    snareState = IDLE
                    snareLevel = 0f
                }
            }

            // Hat: high-pass filtered noise, decay 30 ms
            if (hatState == DECAY) {
                val ageMs = (hatAgeSamples * 1000f) / sampleRate
                val currentNoise = hatNoise.next()
                val signal = currentNoise - 0.6f * hatLastNoise
                hatLastNoise = currentNoise

                val env = exp(-ageMs / 10f)
                mixedSample += signal * env * hatLevel * 0.4f

                hatAgeSamples++
                if (ageMs > 80f || env < 0.001f) {
                    hatState = IDLE
                    hatLevel = 0f
                }
            }

            // Tom: pitch-dropping sine
            if (tomState == DECAY) {
                val ageMs = (tomAgeSamples * 1000f) / sampleRate
                val freq = (tomStartFreq * 0.4f) + (tomStartFreq * 0.6f) * exp(-ageMs / 30f)
                tomPhase01 += freq / sampleRate
                if (tomPhase01 >= 1f) tomPhase01 -= 1f

                val sineValue = FastMath.fastSin((tomPhase01 * 1024).toInt() and 1023)
                val env = exp(-ageMs / 50f)
                mixedSample += sineValue * env * tomLevel

                tomAgeSamples++
                if (ageMs > 200f || env < 0.001f) {
                    tomState = IDLE
                    tomLevel = 0f
                }
            }

            buffer[i] += mixedSample * gainScale
            i++
        }
    }
}
