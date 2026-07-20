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
    }

    fun setPitchRamp(targetFrequency: Float, frames: Int) {
        pendingTargetFrequency = targetFrequency
        pendingSlideFrames = frames.coerceAtLeast(0)
    }

    fun noteOff(releaseContinues: Boolean = false) {
        if (!releaseContinues) enabled = false
    }

    fun applyPatch(patch: SsgPatch) {
        fixedLevel = patch.fixedLevel.coerceIn(0, 15)
        envelopeEnabled = patch.envelopeEnabled
        pan = patch.pan.coerceIn(0, 2)
    }

    fun getPan(): Int = pan

    fun setPan(value: Int) {
        pan = value.coerceIn(0, 2)
    }

    fun reset() {
        enabled = false
        frequency = 0f
        noteGain = 1f
        pendingTargetFrequency = 0f
        pendingSlideFrames = 0
        fixedLevel = DEFAULT_PATCH.fixedLevel
        envelopeEnabled = DEFAULT_PATCH.envelopeEnabled
        pan = DEFAULT_PATCH.pan
    }

    fun render(
        buffer: FloatArray,
        frames: Int,
        sampleRate: Int,
        gainScale: Float,
        startFrame: Int = 0,
        sharedPrepared: Boolean = false
    ) {
        renderDriven(buffer, frames, sampleRate, gainScale, startFrame, sharedPrepared, null, STOP_FRAME_NONE)
    }

    internal fun renderDriven(
        buffer: FloatArray,
        frames: Int,
        sampleRate: Int,
        gainScale: Float,
        startFrame: Int,
        sharedPrepared: Boolean,
        driverFrame: PmdSsgFrame?,
        stopAfterFrame: Int
    ) {
        if (configuredSampleRate != sampleRate) {
            configuredSampleRate = sampleRate
            shared.setSampleRate(sampleRate)
        }
        if (!sharedPrepared && frames > OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK) {
            var rendered = 0
            while (rendered < frames) {
                val count = minOf(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK, frames - rendered)
                prepareDriverFrame(driverFrame, count)
                shared.prepare(count)
                val chunkStopFrame = if (stopAfterFrame >= rendered && stopAfterFrame < rendered + count) {
                    stopAfterFrame - rendered
                } else {
                    STOP_FRAME_NONE
                }
                renderHardware(buffer, count, gainScale, startFrame + rendered, driverFrame, chunkStopFrame)
                rendered += count
            }
            return
        }
        if (!sharedPrepared) {
            prepareDriverFrame(driverFrame, frames)
            shared.prepare(frames)
        }
        renderHardware(buffer, frames, gainScale, startFrame, driverFrame, stopAfterFrame)
    }

    internal fun prepareDriverFrame(driverFrame: PmdSsgFrame?, frames: Int) {
        shared.clearSoftwarePeriodOffset(channelIndex)
        if (driverFrame == null) return
        val count = frames.coerceAtMost(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        var frame = 0
        while (frame < count) {
            shared.writeSoftwarePeriodOffset(
                channelIndex,
                frame,
                driverFrame.tonePeriodOffset[frame]
            )
            frame++
        }
    }

    private fun renderHardware(
        buffer: FloatArray,
        frames: Int,
        gainScale: Float,
        startFrame: Int,
        driverFrame: PmdSsgFrame?,
        stopAfterFrame: Int
    ) {
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
            val driverVolume = driverFrame?.volumeOffset?.get(i) ?: 0
            val amplitude = if (envelopeEnabled) {
                val level = (shared.envelopeAt(i) + driverVolume).coerceIn(0, 31)
                SsgLevelLaw.envelopeAmplitude(level)
            } else {
                val envelopeLevel = driverFrame?.softwareEnvelopeLevel?.get(i) ?: fixedLevel
                val level = (envelopeLevel + driverVolume).coerceIn(0, 15)
                SsgLevelLaw.fixedAmplitude(level)
            }
            buffer[startFrame + i] += signal * amplitude * combinedGain
            if (i == stopAfterFrame) {
                enabled = false
                return
            }
            i++
        }
    }

    internal fun fixedLevelSnapshot(): Int = fixedLevel
    internal fun tonePeriodSnapshot(): Int = shared.tonePeriodSnapshot(channelIndex)
    internal fun mixerRegisterSnapshot(): Int = shared.mixerRegisterSnapshot()

    private companion object {
        const val STOP_FRAME_NONE = -1
        val DEFAULT_PATCH = SsgPatch()
    }

}
