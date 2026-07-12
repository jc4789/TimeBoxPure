package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.pow

class SsgVoice(
    channelIndex: Int = 0,
    private val shared: SsgSharedState = SsgSharedState(),
    private var configuredSampleRate: Int = 48_000
) {
    var enabled: Boolean = false
    var frequency: Float = 0f
    var duty: Float = 0.5f
    var useNoise: Boolean = false
    var noteGain: Float = 1f
    private var toneEnabled: Boolean = true
    private var envelopeEnabled: Boolean = false
    private var fixedLevel: Int = 12
    private var hardwarePhase: UInt = 0u
    private var hardwarePhaseStep: UInt = 0u
    private var hardwareRampStartStep: Long = 0L
    private var hardwareRampTargetStep: Long = 0L
    private var hardwareRampFrames: Int = 0
    private var hardwareRampPosition: Int = 0
    private var pendingTargetFrequency: Float = 0f
    private var pendingSlideFrames: Int = 0
    private var pan: Int = 0
    private var softwareEnvelopeEnabled: Boolean = false
    private var softwareEnvelopeClockHz: Float = 0f
    private var softwareEnvelopeClockStep: UInt = 0u
    private var softwareEnvelopeClockPhase: UInt = 0u
    private var softwareEnvelopeAttackTicks: Int = 0
    private var softwareEnvelopeDecayLevel: Int = 0
    private var softwareEnvelopeSustainTicks: Int = 0
    private var softwareEnvelopeReleaseTicks: Int = 0
    private var softwareEnvelopeStage: Int = SOFTWARE_ENVELOPE_OFF
    private var softwareEnvelopeTicksRemaining: Int = 0
    private var softwareEnvelopeLevelOffset: Int = 0

    init {
        applyPatch(SsgPatch())
    }

    fun noteOn(freq: Float) {
        frequency = freq
        enabled = true
        hardwarePhaseStep = phaseStep(freq, configuredSampleRate)
        hardwareRampStartStep = hardwarePhaseStep.toLong()
        hardwareRampTargetStep = if (pendingTargetFrequency > 0f) phaseStep(pendingTargetFrequency, configuredSampleRate).toLong() else hardwareRampStartStep
        hardwareRampFrames = if (pendingTargetFrequency > 0f) pendingSlideFrames else 0
        hardwareRampPosition = 0
        pendingTargetFrequency = 0f
        pendingSlideFrames = 0
        hardwarePhase = 0u
        if (softwareEnvelopeEnabled) restartSoftwareEnvelope()
    }

    fun setPitchRamp(targetFrequency: Float, frames: Int) {
        pendingTargetFrequency = targetFrequency
        pendingSlideFrames = frames.coerceAtLeast(0)
    }

    fun noteOff() {
        if (softwareEnvelopeEnabled && enabled) {
            softwareEnvelopeStage = SOFTWARE_ENVELOPE_RELEASE
            softwareEnvelopeTicksRemaining = softwareEnvelopeReleaseTicks
        } else {
            enabled = false
        }
    }

    fun applyPatch(patch: SsgPatch) {
        toneEnabled = patch.toneEnabled
        useNoise = patch.noiseEnabled
        fixedLevel = patch.fixedLevel.coerceIn(0, 15)
        envelopeEnabled = patch.envelopeEnabled
        pan = patch.pan.coerceIn(0, 2)
        if (patch.noiseEnabled) shared.configureNoise(patch.noisePeriod)
        if (patch.envelopeEnabled) {
            shared.configureEnvelope(patch.envelopeShape, patch.envelopePeriod, restart = true)
        }
        softwareEnvelopeClockHz = patch.softwareEnvelopeClockHz.coerceAtLeast(0f)
        softwareEnvelopeEnabled = softwareEnvelopeClockHz > 0f
        softwareEnvelopeClockStep = phaseStep(softwareEnvelopeClockHz, configuredSampleRate)
        softwareEnvelopeAttackTicks = patch.softwareEnvelopeAttackTicks.coerceAtLeast(0)
        softwareEnvelopeDecayLevel = patch.softwareEnvelopeDecayLevel.coerceIn(-15, 15)
        softwareEnvelopeSustainTicks = patch.softwareEnvelopeSustainTicks.coerceAtLeast(0)
        softwareEnvelopeReleaseTicks = patch.softwareEnvelopeReleaseTicks.coerceAtLeast(0)
        if (!softwareEnvelopeEnabled) {
            softwareEnvelopeClockPhase = 0u
            softwareEnvelopeStage = SOFTWARE_ENVELOPE_OFF
            softwareEnvelopeTicksRemaining = 0
            softwareEnvelopeLevelOffset = 0
        }
    }

    fun getPan(): Int = pan

    fun setPan(value: Int) {
        pan = value.coerceIn(0, 2)
    }

    fun reset() {
        enabled = false
        frequency = 0f
        duty = 0.5f
        useNoise = false
        noteGain = 1f
        toneEnabled = true
        envelopeEnabled = false
        fixedLevel = 12
        hardwarePhase = 0u
        hardwarePhaseStep = 0u
        hardwareRampStartStep = 0L
        hardwareRampTargetStep = 0L
        hardwareRampFrames = 0
        hardwareRampPosition = 0
        pendingTargetFrequency = 0f
        pendingSlideFrames = 0
        pan = 0
        softwareEnvelopeClockPhase = 0u
        softwareEnvelopeStage = SOFTWARE_ENVELOPE_OFF
        softwareEnvelopeTicksRemaining = 0
        softwareEnvelopeLevelOffset = 0
        applyPatch(SsgPatch())
    }

    fun render(
        buffer: FloatArray,
        frames: Int,
        sampleRate: Int,
        gainScale: Float,
        startFrame: Int = 0,
        sharedPrepared: Boolean = false
    ) {
        if (!enabled) return
        if (configuredSampleRate != sampleRate) {
            configuredSampleRate = sampleRate
            hardwarePhaseStep = phaseStep(frequency, sampleRate)
            if (softwareEnvelopeEnabled) {
                softwareEnvelopeClockStep = phaseStep(softwareEnvelopeClockHz, sampleRate)
            }
        }
        if (!sharedPrepared && frames > OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK) {
            var rendered = 0
            while (rendered < frames) {
                val count = minOf(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK, frames - rendered)
                shared.prepare(count)
                renderHardware(buffer, count, gainScale, startFrame + rendered)
                rendered += count
            }
            return
        }
        if (!sharedPrepared) shared.prepare(frames)
        renderHardware(buffer, frames, gainScale, startFrame)
    }

    private fun renderHardware(buffer: FloatArray, frames: Int, gainScale: Float, startFrame: Int) {
        if (!enabled) return
        val combinedGain = gainScale * noteGain
        var i = 0
        while (i < frames) {
            val tone = if ((hardwarePhase and 0x80000000u) == 0u) 1f else -1f
            val noise = shared.noiseAt(i)
            val signal = when {
                toneEnabled && useNoise -> tone * noise
                toneEnabled -> tone
                useNoise -> noise
                else -> 0f
            }
            val level = if (envelopeEnabled) shared.envelopeAt(i) else fixedLevel + softwareEnvelopeLevelOffset
            buffer[startFrame + i] += signal * LEVEL_TABLE[level.coerceIn(0, 15)] * combinedGain
            hardwarePhase += hardwarePhaseStep
            if (hardwareRampFrames > 0 && hardwareRampPosition < hardwareRampFrames) {
                hardwareRampPosition++
                hardwarePhaseStep = (
                    hardwareRampStartStep +
                        (hardwareRampTargetStep - hardwareRampStartStep) * hardwareRampPosition.toLong() / hardwareRampFrames.toLong()
                    ).toUInt()
            }
            if (softwareEnvelopeEnabled) clockSoftwareEnvelope()
            i++
        }
    }

    internal fun softwareEnvelopeLevelOffsetSnapshot(): Int = softwareEnvelopeLevelOffset

    private fun restartSoftwareEnvelope() {
        softwareEnvelopeClockPhase = 0u
        softwareEnvelopeLevelOffset = 0
        softwareEnvelopeStage = if (softwareEnvelopeAttackTicks > 0) {
            softwareEnvelopeTicksRemaining = softwareEnvelopeAttackTicks
            SOFTWARE_ENVELOPE_ATTACK
        } else {
            softwareEnvelopeLevelOffset = softwareEnvelopeDecayLevel
            softwareEnvelopeTicksRemaining = softwareEnvelopeSustainTicks
            SOFTWARE_ENVELOPE_DECAY
        }
    }

    private fun clockSoftwareEnvelope() {
        val previous = softwareEnvelopeClockPhase
        softwareEnvelopeClockPhase += softwareEnvelopeClockStep
        if (softwareEnvelopeClockPhase >= previous) return

        when (softwareEnvelopeStage) {
            SOFTWARE_ENVELOPE_ATTACK -> {
                softwareEnvelopeTicksRemaining--
                if (softwareEnvelopeTicksRemaining <= 0) {
                    softwareEnvelopeLevelOffset = softwareEnvelopeDecayLevel
                    softwareEnvelopeTicksRemaining = softwareEnvelopeSustainTicks
                    softwareEnvelopeStage = SOFTWARE_ENVELOPE_DECAY
                }
            }
            SOFTWARE_ENVELOPE_DECAY -> {
                if (softwareEnvelopeSustainTicks <= 0) return
                softwareEnvelopeTicksRemaining--
                if (softwareEnvelopeTicksRemaining <= 0) {
                    softwareEnvelopeLevelOffset = (softwareEnvelopeLevelOffset - 1).coerceAtLeast(-15)
                    softwareEnvelopeTicksRemaining = softwareEnvelopeSustainTicks
                }
            }
            SOFTWARE_ENVELOPE_RELEASE -> {
                if (softwareEnvelopeReleaseTicks <= 0) {
                    enabled = false
                    softwareEnvelopeStage = SOFTWARE_ENVELOPE_OFF
                    return
                }
                softwareEnvelopeTicksRemaining--
                if (softwareEnvelopeTicksRemaining <= 0) {
                    softwareEnvelopeLevelOffset--
                    if (fixedLevel + softwareEnvelopeLevelOffset <= 0) {
                        enabled = false
                        softwareEnvelopeStage = SOFTWARE_ENVELOPE_OFF
                    } else {
                        softwareEnvelopeTicksRemaining = softwareEnvelopeReleaseTicks
                    }
                }
            }
        }
    }

    private fun phaseStep(freq: Float, sampleRate: Int): UInt =
        (freq.toDouble() * UINT_CYCLE / sampleRate.coerceAtLeast(1).toDouble()).toLong().toUInt()

    private companion object {
        const val SOFTWARE_ENVELOPE_OFF = 0
        const val SOFTWARE_ENVELOPE_ATTACK = 1
        const val SOFTWARE_ENVELOPE_DECAY = 2
        const val SOFTWARE_ENVELOPE_RELEASE = 3
        const val UINT_CYCLE = 4_294_967_296.0
        val LEVEL_TABLE = FloatArray(16) { level ->
            if (level == 0) 0f else 10.0.pow(((level - 15) * 1.5) / 20.0).toFloat()
        }
    }
}
