package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.exp

class ProceduralDrums(private val configuredSampleRate: Int = 48_000) {
    companion object {
        const val IDLE = 0
        const val ATTACK = 1
        const val DECAY = 2
        const val OFF = 3
        const val KICK_GAIN = 0.7f
        const val SNARE_GAIN = 0.6f
        const val SNARE_NOISE_GAIN = 0.5f
        const val SNARE_PARTIAL_GAIN = 0.3f
        const val HAT_PREVIOUS_NOISE_MIX = 0.6f
        const val HAT_GAIN = 0.4f
        const val TOM_GAIN = 1.0f
        const val CYMBAL_PREVIOUS_NOISE_MIX = 0.78f
        const val CYMBAL_GAIN = 0.28f
        const val RIM_FREQUENCY_HZ = 1_850f
        const val RIM_SINE_MIX = 0.65f
        const val RIM_NOISE_MIX = 0.35f
        const val RIM_GAIN = 0.35f
    }

    enum class DrumKind {
        KICK, SNARE, HAT, TOM, CYMBAL, RIMSHOT
    }

    private val kickNoise = LfsrNoise(0xBEEF xor 1)
    private val snareNoise = LfsrNoise(0xBEEF xor 2)
    private val hatNoise = LfsrNoise(0xFACE xor 3)
    private val cymbalNoise = LfsrNoise(0xC1A0 xor 4)
    private val rimNoise = LfsrNoise(0x71A5 xor 5)

    private val kickFrequency = FloatArray(configuredSampleRate / 4 + 1) { age ->
        val ageMs = age * 1000f / configuredSampleRate
        50f + 70f * exp(-ageMs / 12f)
    }
    private val kickEnvelope = FloatArray(configuredSampleRate / 4 + 1) { age ->
        exp(-(age * 1000f / configuredSampleRate) / 70f)
    }
    private val snareEnvelope = FloatArray(configuredSampleRate * 3 / 20 + 1) { age ->
        exp(-(age * 1000f / configuredSampleRate) / 25f)
    }
    private val hatEnvelope = FloatArray(configuredSampleRate * 2 / 25 + 1) { age ->
        exp(-(age * 1000f / configuredSampleRate) / 10f)
    }
    private val tomEnvelope = FloatArray(configuredSampleRate / 5 + 1) { age ->
        exp(-(age * 1000f / configuredSampleRate) / 50f)
    }
    private val tomPitchEnvelope = FloatArray(configuredSampleRate / 5 + 1) { age ->
        exp(-(age * 1000f / configuredSampleRate) / 30f)
    }
    private val cymbalEnvelope = FloatArray(configuredSampleRate / 2 + 1) { age ->
        exp(-(age * 1000f / configuredSampleRate) / 90f)
    }
    private val rimEnvelope = FloatArray(configuredSampleRate / 20 + 1) { age ->
        exp(-(age * 1000f / configuredSampleRate) / 8f)
    }

    private var kickState: Int = IDLE
    private var kickLevel: Float = 0f
    internal var kickGain: Float = 1f
    private var kickPan: Int = 0
    private var kickAgeSamples: Int = 0
    private var kickPhase01: Float = 0f

    private var snareState: Int = IDLE
    private var snareLevel: Float = 0f
    internal var snareGain: Float = 1f
    private var snarePan: Int = 0
    private var snareAgeSamples: Int = 0
    private var snarePhase01_1: Float = 0f
    private var snarePhase01_2: Float = 0f

    private var hatState: Int = IDLE
    private var hatLevel: Float = 0f
    internal var hatGain: Float = 1f
    private var hatPan: Int = 0
    private var hatAgeSamples: Int = 0
    private var hatLastNoise: Float = 0f

    private var tomState: Int = IDLE
    private var tomLevel: Float = 0f
    internal var tomGain: Float = 1f
    private var tomPan: Int = 0
    private var tomAgeSamples: Int = 0
    private var tomPhase01: Float = 0f
    private var tomStartFreq: Float = 150f

    private var cymbalState: Int = IDLE
    private var cymbalAgeSamples: Int = 0
    internal var cymbalGain: Float = 1f
    private var cymbalPan: Int = 0
    private var cymbalLastNoise: Float = 0f

    private var rimState: Int = IDLE
    private var rimAgeSamples: Int = 0
    internal var rimGain: Float = 1f
    private var rimPan: Int = 0
    private var rimPhase01: Float = 0f

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

    fun triggerCymbal() {
        cymbalState = DECAY
        cymbalAgeSamples = 0
        cymbalLastNoise = 0f
    }

    fun triggerRimshot() {
        rimState = DECAY
        rimAgeSamples = 0
        rimPhase01 = 0f
    }

    fun setPan(kind: DrumKind, pan: Int) {
        val value = pan.coerceIn(0, 2)
        when (kind) {
            DrumKind.KICK -> kickPan = value
            DrumKind.SNARE -> snarePan = value
            DrumKind.HAT -> hatPan = value
            DrumKind.TOM -> tomPan = value
            DrumKind.CYMBAL -> cymbalPan = value
            DrumKind.RIMSHOT -> rimPan = value
        }
    }

    fun setGain(kind: DrumKind, gain: Float) {
        val value = gain.coerceAtLeast(0f)
        when (kind) {
            DrumKind.KICK -> kickGain = value
            DrumKind.SNARE -> snareGain = value
            DrumKind.HAT -> hatGain = value
            DrumKind.TOM -> tomGain = value
            DrumKind.CYMBAL -> cymbalGain = value
            DrumKind.RIMSHOT -> rimGain = value
        }
    }

    fun dump(kind: DrumKind) {
        when (kind) {
            DrumKind.KICK -> kickState = IDLE
            DrumKind.SNARE -> snareState = IDLE
            DrumKind.HAT -> hatState = IDLE
            DrumKind.TOM -> tomState = IDLE
            DrumKind.CYMBAL -> cymbalState = IDLE
            DrumKind.RIMSHOT -> rimState = IDLE
        }
    }

    internal fun stateSnapshot(kind: DrumKind): Int = when (kind) {
        DrumKind.KICK -> kickState
        DrumKind.SNARE -> snareState
        DrumKind.HAT -> hatState
        DrumKind.TOM -> tomState
        DrumKind.CYMBAL -> cymbalState
        DrumKind.RIMSHOT -> rimState
    }

    internal fun gainSnapshot(kind: DrumKind): Float = when (kind) {
        DrumKind.KICK -> kickGain
        DrumKind.SNARE -> snareGain
        DrumKind.HAT -> hatGain
        DrumKind.TOM -> tomGain
        DrumKind.CYMBAL -> cymbalGain
        DrumKind.RIMSHOT -> rimGain
    }

    internal fun panSnapshot(kind: DrumKind): Int = when (kind) {
        DrumKind.KICK -> kickPan
        DrumKind.SNARE -> snarePan
        DrumKind.HAT -> hatPan
        DrumKind.TOM -> tomPan
        DrumKind.CYMBAL -> cymbalPan
        DrumKind.RIMSHOT -> rimPan
    }

    internal fun hasActiveVoices(): Boolean =
        kickState != IDLE || snareState != IDLE || hatState != IDLE ||
            tomState != IDLE || cymbalState != IDLE || rimState != IDLE

    fun stopAll() {
        silence()
    }

    /** Stops active voices without changing authored gain/pan state or reseeding noise. */
    fun silence() {
        kickState = IDLE
        kickLevel = 0f
        snareState = IDLE
        snareLevel = 0f
        hatState = IDLE
        hatLevel = 0f
        tomState = IDLE
        tomLevel = 0f
        cymbalState = IDLE
        rimState = IDLE
    }

    fun reset() {
        kickState = IDLE
        kickLevel = 0f
        kickGain = 1f
        kickPan = 0
        kickAgeSamples = 0
        kickPhase01 = 0f

        snareState = IDLE
        snareLevel = 0f
        snareGain = 1f
        snarePan = 0
        snareAgeSamples = 0
        snarePhase01_1 = 0f
        snarePhase01_2 = 0f

        hatState = IDLE
        hatLevel = 0f
        hatGain = 1f
        hatPan = 0
        hatAgeSamples = 0
        hatLastNoise = 0f

        tomState = IDLE
        tomLevel = 0f
        tomGain = 1f
        tomPan = 0
        tomAgeSamples = 0
        tomPhase01 = 0f
        tomStartFreq = 150f

        cymbalState = IDLE
        cymbalAgeSamples = 0
        cymbalGain = 1f
        cymbalPan = 0
        cymbalLastNoise = 0f

        rimState = IDLE
        rimAgeSamples = 0
        rimGain = 1f
        rimPan = 0
        rimPhase01 = 0f

        kickNoise.reset(0xBEEF xor 1)
        snareNoise.reset(0xBEEF xor 2)
        hatNoise.reset(0xFACE xor 3)
        cymbalNoise.reset(0xC1A0 xor 4)
        rimNoise.reset(0x71A5 xor 5)
    }

    fun render(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float, startFrame: Int = 0) {
        renderInternal(buffer, null, frames, sampleRate, gainScale, startFrame)
    }

    fun renderStereo(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float, startFrame: Int = 0) {
        renderInternal(null, buffer, frames, sampleRate, gainScale, startFrame)
    }

    private fun renderInternal(
        monoBuffer: FloatArray?,
        stereoBuffer: FloatArray?,
        frames: Int,
        sampleRate: Int,
        gainScale: Float,
        startFrame: Int
    ) {
        var i = 0
        while (i < frames) {
            var mixedSample = 0f
            var leftSample = 0f
            var rightSample = 0f

            if (kickState == DECAY) {
                val ageMs = (kickAgeSamples * 1000f) / sampleRate
                val index = kickAgeSamples.coerceAtMost(kickFrequency.lastIndex)
                val freq = kickFrequency[index]
                kickPhase01 += freq / sampleRate
                if (kickPhase01 >= 1f) kickPhase01 -= 1f

                val sineValue = DrumSinLut.sin01(kickPhase01.toDouble())
                val env = kickEnvelope[index]
                val contribution = sineValue * env * kickLevel * KICK_GAIN * kickGain
                mixedSample += contribution
                leftSample += contribution * leftPanGain(kickPan)
                rightSample += contribution * rightPanGain(kickPan)

                kickAgeSamples++
                if (ageMs > 250f || env < 0.001f) {
                    kickState = IDLE
                    kickLevel = 0f
                }
            }

            if (snareState == DECAY) {
                val ageMs = (snareAgeSamples * 1000f) / sampleRate
                snarePhase01_1 += 180f / sampleRate
                if (snarePhase01_1 >= 1f) snarePhase01_1 -= 1f
                snarePhase01_2 += 330f / sampleRate
                if (snarePhase01_2 >= 1f) snarePhase01_2 -= 1f

                val partials = SNARE_PARTIAL_GAIN * DrumSinLut.sin01(snarePhase01_1.toDouble()) +
                               SNARE_PARTIAL_GAIN * DrumSinLut.sin01(snarePhase01_2.toDouble())
                val noiseSig = snareNoise.next()
                val signal = noiseSig * SNARE_NOISE_GAIN + partials
                val env = snareEnvelope[snareAgeSamples.coerceAtMost(snareEnvelope.lastIndex)]
                val contribution = signal * env * snareLevel * SNARE_GAIN * snareGain
                mixedSample += contribution
                leftSample += contribution * leftPanGain(snarePan)
                rightSample += contribution * rightPanGain(snarePan)

                snareAgeSamples++
                if (ageMs > 150f || env < 0.001f) {
                    snareState = IDLE
                    snareLevel = 0f
                }
            }

            if (hatState == DECAY) {
                val ageMs = (hatAgeSamples * 1000f) / sampleRate
                val currentNoise = hatNoise.next()
                val signal = currentNoise - HAT_PREVIOUS_NOISE_MIX * hatLastNoise
                hatLastNoise = currentNoise

                val env = hatEnvelope[hatAgeSamples.coerceAtMost(hatEnvelope.lastIndex)]
                val contribution = signal * env * hatLevel * HAT_GAIN * hatGain
                mixedSample += contribution
                leftSample += contribution * leftPanGain(hatPan)
                rightSample += contribution * rightPanGain(hatPan)

                hatAgeSamples++
                if (ageMs > 80f || env < 0.001f) {
                    hatState = IDLE
                    hatLevel = 0f
                }
            }

            if (tomState == DECAY) {
                val ageMs = (tomAgeSamples * 1000f) / sampleRate
                val index = tomAgeSamples.coerceAtMost(tomEnvelope.lastIndex)
                val freq = (tomStartFreq * 0.4f) + (tomStartFreq * 0.6f) * tomPitchEnvelope[index]
                tomPhase01 += freq / sampleRate
                if (tomPhase01 >= 1f) tomPhase01 -= 1f

                val sineValue = DrumSinLut.sin01(tomPhase01.toDouble())
                val env = tomEnvelope[index]
                val contribution = sineValue * env * tomLevel * TOM_GAIN * tomGain
                mixedSample += contribution
                leftSample += contribution * leftPanGain(tomPan)
                rightSample += contribution * rightPanGain(tomPan)

                tomAgeSamples++
                if (ageMs > 200f || env < 0.001f) {
                    tomState = IDLE
                    tomLevel = 0f
                }
            }

            if (cymbalState == DECAY) {
                val currentNoise = cymbalNoise.next()
                val brightNoise = currentNoise - CYMBAL_PREVIOUS_NOISE_MIX * cymbalLastNoise
                cymbalLastNoise = currentNoise
                val env = cymbalEnvelope[cymbalAgeSamples.coerceAtMost(cymbalEnvelope.lastIndex)]
                val contribution = brightNoise * env * CYMBAL_GAIN * cymbalGain
                mixedSample += contribution
                leftSample += contribution * leftPanGain(cymbalPan)
                rightSample += contribution * rightPanGain(cymbalPan)
                cymbalAgeSamples++
                if (cymbalAgeSamples >= cymbalEnvelope.size - 1 || env < 0.001f) cymbalState = IDLE
            }

            if (rimState == DECAY) {
                rimPhase01 += RIM_FREQUENCY_HZ / sampleRate
                if (rimPhase01 >= 1f) rimPhase01 -= 1f
                val click = DrumSinLut.sin01(rimPhase01.toDouble()) * RIM_SINE_MIX + rimNoise.next() * RIM_NOISE_MIX
                val env = rimEnvelope[rimAgeSamples.coerceAtMost(rimEnvelope.lastIndex)]
                val contribution = click * env * RIM_GAIN * rimGain
                mixedSample += contribution
                leftSample += contribution * leftPanGain(rimPan)
                rightSample += contribution * rightPanGain(rimPan)
                rimAgeSamples++
                if (rimAgeSamples >= rimEnvelope.size - 1 || env < 0.001f) rimState = IDLE
            }

            if (stereoBuffer != null) {
                val stereoIndex = (startFrame + i) * 2
                stereoBuffer[stereoIndex] += leftSample * gainScale
                stereoBuffer[stereoIndex + 1] += rightSample * gainScale
            } else if (monoBuffer != null) {
                monoBuffer[startFrame + i] += mixedSample * gainScale
            }
            i++
        }
    }

    private fun leftPanGain(pan: Int): Float = when (pan) {
        2 -> 0f
        1 -> 1f
        else -> 0.707f
    }

    private fun rightPanGain(pan: Int): Float = when (pan) {
        1 -> 0f
        2 -> 1f
        else -> 0.707f
    }
}
