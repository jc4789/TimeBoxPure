package com.example.timeboxvibe.engine.audio.opna

class SsgVoice(
    private val channelIndex: Int = 0,
    private val shared: SsgSharedState = SsgSharedState(),
    private var configuredSampleRate: Int = 48_000
) {
    var enabled: Boolean = false
    var frequency: Float = 0f
        private set
    var noteGain: Float = 1f
    private var envelopeEnabled = false
    private var fixedLevel = 12
    private var pendingTargetFrequency = 0f
    private var pendingSlideFrames = 0
    private var pan = 0
    private val softwareEnvelope = PmdSoftwareEnvelope(configuredSampleRate)
    private val softwareLfo1 = PmdSoftwareLfo(configuredSampleRate, PmdPerformanceLaws.SOFTWARE_LFO_RANDOM_SEED)
    private val softwareLfo2 = PmdSoftwareLfo(configuredSampleRate, PmdPerformanceLaws.SOFTWARE_LFO_RANDOM_SEED xor 0x2468ACE0)
    private val softwareVolume = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)

    init {
        applyPatch(DEFAULT_PATCH)
    }

    fun noteOn(freq: Float) {
        val period = SsgHardwareLaws.nearestTonePeriod(freq.toDouble())
        shared.writeTonePeriod(channelIndex, period)
        if (pendingTargetFrequency > 0f) {
            shared.writeToneRamp(
                channelIndex,
                SsgHardwareLaws.nearestTonePeriod(pendingTargetFrequency.toDouble()),
                pendingSlideFrames
            )
        }
        frequency = SsgHardwareLaws.toneFrequency(period).toFloat()
        enabled = true
        pendingTargetFrequency = 0f
        pendingSlideFrames = 0
        softwareEnvelope.noteOn()
        softwareLfo1.noteOn()
        softwareLfo2.noteOn()
    }

    fun setPitchRamp(targetFrequency: Float, frames: Int) {
        pendingTargetFrequency = targetFrequency
        pendingSlideFrames = frames.coerceAtLeast(0)
    }

    fun noteOff() {
        if (softwareEnvelope.enabled && enabled) {
            softwareEnvelope.noteOff()
        } else {
            enabled = false
        }
    }

    fun applyPatch(patch: SsgPatch) {
        fixedLevel = patch.fixedLevel.coerceIn(0, 15)
        envelopeEnabled = patch.envelopeEnabled
        pan = patch.pan.coerceIn(0, 2)
        shared.writeMixerChannel(channelIndex, patch.toneEnabled, patch.noiseEnabled)
        if (patch.noiseEnabled) shared.writeNoisePeriod(patch.noisePeriod)
        if (patch.envelopeEnabled) {
            shared.writeEnvelopePeriod(patch.envelopePeriod)
            shared.writeEnvelopeShape(patch.envelopeShape)
        }
    }

    internal fun configureSoftwareEnvelope(
        format: Int,
        attack: Int,
        decay: Int,
        sustain: Int,
        release: Int,
        sustainLevel: Int,
        attackLevel: Int
    ) {
        softwareEnvelope.configure(format, attack, decay, sustain, release, sustainLevel, attackLevel)
    }

    internal fun setSoftwareEnvelopeClockMode(mode: Int) {
        softwareEnvelope.setClockMode(mode)
    }

    internal fun setSoftwareEnvelopeTempo(bpmMilli: Int, clocksPerQuarter: Int) {
        softwareEnvelope.setTempo(bpmMilli, clocksPerQuarter)
    }

    internal fun configureSoftwareLfo(index: Int, delay: Int, speed: Int, depthA: Int, depthB: Int) {
        softwareLfo(index).configure(delay, speed, depthA, depthB)
    }

    internal fun setSoftwareLfoSwitch(index: Int, value: Int) {
        softwareLfo(index).setSwitch(value)
        if (!softwareLfo1.enabled && !softwareLfo2.enabled) clearSoftwareLfoBuffers()
    }

    internal fun setSoftwareLfoWaveform(index: Int, value: Int) {
        softwareLfo(index).setWaveform(value)
    }

    internal fun setSoftwareLfoClockMode(index: Int, value: Int) {
        softwareLfo(index).setClockMode(value)
    }

    internal fun setSoftwareLfoDepthEvolution(index: Int, speed: Int, depth: Int, time: Int) {
        softwareLfo(index).setDepthEvolution(speed, depth, time)
    }

    internal fun setSoftwareLfoTempo(bpmMilli: Int, clocksPerQuarter: Int) {
        softwareLfo1.setTempo(bpmMilli, clocksPerQuarter)
        softwareLfo2.setTempo(bpmMilli, clocksPerQuarter)
    }

    fun getPan(): Int = pan

    fun setPan(value: Int) {
        pan = value.coerceIn(0, 2)
    }

    fun reset() {
        enabled = false
        frequency = 0f
        noteGain = 1f
        envelopeEnabled = false
        fixedLevel = 12
        pendingTargetFrequency = 0f
        pendingSlideFrames = 0
        pan = 0
        softwareEnvelope.reset()
        softwareLfo1.reset()
        softwareLfo2.reset()
        clearSoftwareLfoBuffers()
        applyPatch(DEFAULT_PATCH)
    }

    fun render(
        buffer: FloatArray,
        frames: Int,
        sampleRate: Int,
        gainScale: Float,
        startFrame: Int = 0,
        sharedPrepared: Boolean = false
    ) {
        if (configuredSampleRate != sampleRate) {
            configuredSampleRate = sampleRate
            shared.setSampleRate(sampleRate)
            softwareEnvelope.setSampleRate(sampleRate)
            softwareLfo1.setSampleRate(sampleRate)
            softwareLfo2.setSampleRate(sampleRate)
        }
        if (!sharedPrepared && frames > OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK) {
            var rendered = 0
            while (rendered < frames) {
                val count = minOf(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK, frames - rendered)
                prepareSoftwareLfo(count)
                shared.prepare(count)
                renderHardware(buffer, count, gainScale, startFrame + rendered)
                rendered += count
            }
            return
        }
        if (!sharedPrepared) {
            prepareSoftwareLfo(frames)
            shared.prepare(frames)
        }
        renderHardware(buffer, frames, gainScale, startFrame)
    }

    internal fun prepareSoftwareLfo(frames: Int) {
        if (!softwareLfo1.enabled && !softwareLfo2.enabled) return
        val count = frames.coerceAtMost(softwareVolume.size)
        var frame = 0
        while (frame < count) {
            shared.writeSoftwarePeriodOffset(
                channelIndex,
                frame,
                softwareLfo1.pitchValue() + softwareLfo2.pitchValue()
            )
            softwareVolume[frame] = softwareLfo1.volumeValue() + softwareLfo2.volumeValue()
            softwareLfo1.advanceSample()
            softwareLfo2.advanceSample()
            frame++
        }
    }

    private fun renderHardware(buffer: FloatArray, frames: Int, gainScale: Float, startFrame: Int) {
        if (!enabled) return
        val combinedGain = gainScale * noteGain
        val toneEnabled = shared.toneEnabled(channelIndex)
        val noiseEnabled = shared.noiseEnabled(channelIndex)
        var i = 0
        while (i < frames) {
            val tone = shared.toneAt(channelIndex, i)
            val noise = shared.noiseAt(i)
            val signal = when {
                toneEnabled && noiseEnabled -> if (tone > 0f && noise > 0f) 1f else -1f
                toneEnabled -> tone
                noiseEnabled -> noise
                else -> 0f
            }
            val baseLevel = if (envelopeEnabled) shared.envelopeAt(i) else softwareEnvelope.levelFor(fixedLevel)
            val level = (baseLevel + softwareVolume[i]).coerceIn(0, 15)
            buffer[startFrame + i] += signal * SsgLevelLaw.fixedAmplitude(level) * combinedGain
            softwareEnvelope.advanceSample()
            if (softwareEnvelope.finishedRelease()) {
                enabled = false
                return
            }
            i++
        }
    }

    internal fun softwareEnvelopeLevelOffsetSnapshot(): Int = softwareEnvelope.levelOffsetSnapshot(fixedLevel)
    internal fun softwareLfoValueSnapshot(index: Int): Int = softwareLfo(index).valueSnapshot()
    internal fun tonePeriodSnapshot(): Int = shared.tonePeriodSnapshot(channelIndex)
    internal fun mixerRegisterSnapshot(): Int = shared.mixerRegisterSnapshot()

    private companion object {
        val DEFAULT_PATCH = SsgPatch()
    }

    private fun softwareLfo(index: Int): PmdSoftwareLfo = if (index == 0) softwareLfo1 else softwareLfo2

    private fun clearSoftwareLfoBuffers() {
        softwareVolume.fill(0)
        shared.clearSoftwarePeriodOffset(channelIndex)
    }
}
