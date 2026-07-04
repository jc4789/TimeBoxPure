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
    var phase01: Float = 0f
    var useNoise: Boolean = false
    var noteGain: Float = 1f
    private var hardwareMode: Boolean = false
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

    val env: Envelope = Envelope()
    private val noise: LfsrNoise = LfsrNoise(0xACE1 xor (channelIndex * 0x9E37))

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
        if (hardwareMode) hardwarePhase = 0u
        env.noteOn()
    }

    fun setPitchRamp(targetFrequency: Float, frames: Int) {
        pendingTargetFrequency = targetFrequency
        pendingSlideFrames = frames.coerceAtLeast(0)
    }

    fun noteOff() {
        if (hardwareMode) {
            enabled = false
        } else {
            env.noteOff()
        }
    }

    fun applyPatch(patch: SsgPatch) {
        hardwareMode = true
        toneEnabled = patch.toneEnabled
        useNoise = patch.noiseEnabled
        fixedLevel = patch.fixedLevel.coerceIn(0, 15)
        envelopeEnabled = patch.envelopeEnabled
        pan = patch.pan.coerceIn(0, 2)
        shared.configureNoise(patch.noisePeriod)
        shared.configureEnvelope(patch.envelopeShape, patch.envelopePeriod, restart = true)
    }

    fun getPan(): Int = pan

    fun setPan(value: Int) {
        pan = value.coerceIn(0, 2)
    }

    fun reset() {
        enabled = false
        frequency = 0f
        duty = 0.5f
        phase01 = 0f
        useNoise = false
        noteGain = 1f
        hardwareMode = false
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
        env.reset()
        noise.reset(0xACE1)
    }

    fun render(
        buffer: FloatArray,
        frames: Int,
        sampleRate: Int,
        gainScale: Float,
        startFrame: Int = 0,
        sharedPrepared: Boolean = false
    ) {
        if (!enabled && env.stage == Envelope.OFF) return

        if (hardwareMode) {
            if (configuredSampleRate != sampleRate) {
                configuredSampleRate = sampleRate
                hardwarePhaseStep = phaseStep(frequency, sampleRate)
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
            return
        }

        val dt = 1f / sampleRate
        val step = frequency / sampleRate
        val combinedGain = gainScale * noteGain
        var i = 0
        while (i < frames) {
            val envVal = env.next(dt)
            var signal = if (useNoise) {
                noise.next()
            } else {
                var s = if (phase01 < duty) 1f else -1f
                
                // PolyBLEP at 0.0 transition (step from -1 to +1)
                s += polyBlep(phase01, step)
                
                // PolyBLEP at duty transition (step from +1 to -1)
                var tDuty = phase01 - duty
                if (tDuty < 0f) tDuty += 1f
                s -= polyBlep(tDuty, step)
                s
            }

            buffer[startFrame + i] += signal * envVal * combinedGain

            phase01 += step
            if (phase01 >= 1f) {
                phase01 -= 1f
            }
            i++
        }
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
            val level = if (envelopeEnabled) shared.envelopeAt(i) else fixedLevel
            buffer[startFrame + i] += signal * LEVEL_TABLE[level.coerceIn(0, 15)] * combinedGain
            hardwarePhase += hardwarePhaseStep
            if (hardwareRampFrames > 0 && hardwareRampPosition < hardwareRampFrames) {
                hardwareRampPosition++
                hardwarePhaseStep = (
                    hardwareRampStartStep +
                        (hardwareRampTargetStep - hardwareRampStartStep) * hardwareRampPosition.toLong() / hardwareRampFrames.toLong()
                    ).toUInt()
            }
            i++
        }
    }

    private fun phaseStep(freq: Float, sampleRate: Int): UInt =
        (freq.toDouble() * UINT_CYCLE / sampleRate.coerceAtLeast(1).toDouble()).toLong().toUInt()

    private fun polyBlep(t: Float, dt: Float): Float {
        if (dt <= 0f) return 0f
        return when {
            t < dt -> {
                val t2 = t / dt
                2f * t2 - t2 * t2 - 1f
            }
            t > 1f - dt -> {
                val t2 = (t - 1f) / dt
                t2 * t2 + 2f * t2 + 1f
            }
            else -> 0f
        }
    }

    private companion object {
        const val UINT_CYCLE = 4_294_967_296.0
        val LEVEL_TABLE = FloatArray(16) { level ->
            if (level == 0) 0f else 10.0.pow(((level - 15) * 1.5) / 20.0).toFloat()
        }
    }
}
