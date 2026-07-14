package com.example.timeboxvibe.engine.audio.opna

/** Shared YM2608 SSG registers, counters, noise LFSR, and hardware envelope. */
class SsgSharedState(private var sampleRate: Int = 48_000) {
    private val toneBuffer = FloatArray(
        SsgHardwareLaws.CHANNEL_COUNT * OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK
    )
    private val noiseBuffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private val envelopeBuffer = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private val softwarePeriodOffset = IntArray(
        SsgHardwareLaws.CHANNEL_COUNT * OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK
    )

    private val tonePeriod = IntArray(SsgHardwareLaws.CHANNEL_COUNT) { SsgHardwareLaws.MIN_TONE_PERIOD }
    private val toneClockAccumulator = LongArray(SsgHardwareLaws.CHANNEL_COUNT)
    private val toneOutput = IntArray(SsgHardwareLaws.CHANNEL_COUNT) { 1 }
    private val toneRampStart = IntArray(SsgHardwareLaws.CHANNEL_COUNT)
    private val toneRampTarget = IntArray(SsgHardwareLaws.CHANNEL_COUNT)
    private val toneRampFrames = IntArray(SsgHardwareLaws.CHANNEL_COUNT)
    private val toneRampPosition = IntArray(SsgHardwareLaws.CHANNEL_COUNT)

    private var mixerRegister = MIXER_ALL_DISABLED
    private var noisePeriod = SsgHardwareLaws.MIN_NOISE_PERIOD
    private var noiseClockAccumulator = 0L
    private var lfsr = NOISE_LFSR_RESET
    private var noiseOutput = 1

    private var envelopePeriod = SsgHardwareLaws.MIN_ENVELOPE_PERIOD
    private var envelopeShape = 0
    private var envelopeClockAccumulator = 0L
    private var envelopeLevel = ENVELOPE_MAX_LEVEL
    private var envelopeDirection = -1
    private var envelopeHolding = false
    private var envelopeRestartCount = 0

    fun setSampleRate(value: Int) {
        val selected = value.coerceAtLeast(1)
        if (selected == sampleRate) return
        sampleRate = selected
        clearClockAccumulators()
    }

    fun writeTonePeriod(channel: Int, period: Int) {
        if (channel !in 0 until SsgHardwareLaws.CHANNEL_COUNT) return
        tonePeriod[channel] = period.coerceIn(
            SsgHardwareLaws.MIN_TONE_PERIOD,
            SsgHardwareLaws.MAX_TONE_PERIOD
        )
        toneRampFrames[channel] = 0
        toneRampPosition[channel] = 0
    }

    fun writeToneRamp(channel: Int, targetPeriod: Int, frames: Int) {
        if (channel !in 0 until SsgHardwareLaws.CHANNEL_COUNT) return
        val duration = frames.coerceAtLeast(0)
        if (duration == 0) {
            writeTonePeriod(channel, targetPeriod)
            return
        }
        toneRampStart[channel] = tonePeriod[channel]
        toneRampTarget[channel] = targetPeriod.coerceIn(
            SsgHardwareLaws.MIN_TONE_PERIOD,
            SsgHardwareLaws.MAX_TONE_PERIOD
        )
        toneRampFrames[channel] = duration
        toneRampPosition[channel] = 0
    }

    internal fun writeSoftwarePeriodOffset(channel: Int, frame: Int, value: Int) {
        softwarePeriodOffset[channel * OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK + frame] = value
    }

    internal fun clearSoftwarePeriodOffset(channel: Int) {
        val start = channel * OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK
        softwarePeriodOffset.fill(0, start, start + OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    }

    fun writeMixerChannel(channel: Int, toneEnabled: Boolean, noiseEnabled: Boolean) {
        writeToneEnabled(channel, toneEnabled)
        writeNoiseEnabled(channel, noiseEnabled)
    }

    fun writeToneEnabled(channel: Int, enabled: Boolean) {
        if (channel !in 0 until SsgHardwareLaws.CHANNEL_COUNT) return
        val toneMask = 1 shl channel
        mixerRegister = if (enabled) mixerRegister and toneMask.inv() else mixerRegister or toneMask
        mixerRegister = mixerRegister and MIXER_REGISTER_MASK
    }

    fun writeNoiseEnabled(channel: Int, enabled: Boolean) {
        if (channel !in 0 until SsgHardwareLaws.CHANNEL_COUNT) return
        val noiseMask = 1 shl (channel + NOISE_ENABLE_BIT_OFFSET)
        mixerRegister = if (enabled) mixerRegister and noiseMask.inv() else mixerRegister or noiseMask
        mixerRegister = mixerRegister and MIXER_REGISTER_MASK
    }

    fun writeNoisePeriod(period: Int) {
        noisePeriod = period.coerceIn(
            SsgHardwareLaws.MIN_NOISE_PERIOD,
            SsgHardwareLaws.MAX_NOISE_PERIOD
        )
    }

    fun writeEnvelopePeriod(period: Int) {
        envelopePeriod = period.coerceIn(
            SsgHardwareLaws.MIN_ENVELOPE_PERIOD,
            SsgHardwareLaws.MAX_ENVELOPE_PERIOD
        )
    }

    /** A YM2608 register-$0D write always restarts the shared envelope. */
    fun writeEnvelopeShape(shape: Int) {
        envelopeShape = shape and ENVELOPE_SHAPE_MASK
        envelopeRestartCount++
        restartEnvelope()
    }

    fun prepare(frames: Int) {
        val count = frames.coerceAtMost(noiseBuffer.size)
        var i = 0
        while (i < count) {
            prepareToneFrame(i)
            prepareNoiseFrame(i)
            prepareEnvelopeFrame(i)
            i++
        }
    }

    internal fun toneAt(channel: Int, frame: Int): Float =
        toneBuffer[channel * OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK + frame]

    internal fun noiseAt(frame: Int): Float = noiseBuffer[frame]
    internal fun envelopeAt(frame: Int): Int = envelopeBuffer[frame]

    internal fun toneEnabled(channel: Int): Boolean = (mixerRegister and (1 shl channel)) == 0

    internal fun noiseEnabled(channel: Int): Boolean =
        (mixerRegister and (1 shl (channel + NOISE_ENABLE_BIT_OFFSET))) == 0

    internal fun tonePeriodSnapshot(channel: Int): Int = tonePeriod[channel]
    internal fun noisePeriodSnapshot(): Int = noisePeriod
    internal fun mixerRegisterSnapshot(): Int = mixerRegister
    internal fun noiseLfsrSnapshot(): Int = lfsr
    internal fun envelopePeriodSnapshot(): Int = envelopePeriod
    internal fun envelopeShapeSnapshot(): Int = envelopeShape
    internal fun envelopeLevelSnapshot(): Int = envelopeLevel
    internal fun envelopeRestartCountSnapshot(): Int = envelopeRestartCount

    fun reset() {
        var channel = 0
        while (channel < SsgHardwareLaws.CHANNEL_COUNT) {
            tonePeriod[channel] = SsgHardwareLaws.MIN_TONE_PERIOD
            toneOutput[channel] = 1
            toneRampStart[channel] = 0
            toneRampTarget[channel] = 0
            toneRampFrames[channel] = 0
            toneRampPosition[channel] = 0
            channel++
        }
        mixerRegister = MIXER_ALL_DISABLED
        noisePeriod = SsgHardwareLaws.MIN_NOISE_PERIOD
        lfsr = NOISE_LFSR_RESET
        noiseOutput = 1
        envelopePeriod = SsgHardwareLaws.MIN_ENVELOPE_PERIOD
        envelopeShape = 0
        envelopeRestartCount = 0
        clearClockAccumulators()
        restartEnvelope()
    }

    private fun prepareToneFrame(frame: Int) {
        var channel = 0
        while (channel < SsgHardwareLaws.CHANNEL_COUNT) {
            advanceToneRamp(channel)
            val effectivePeriod = (
                tonePeriod[channel] +
                    softwarePeriodOffset[channel * OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK + frame]
                ).coerceIn(SsgHardwareLaws.MIN_TONE_PERIOD, SsgHardwareLaws.MAX_TONE_PERIOD)
            val threshold = sampleRate.toLong() * SsgHardwareLaws.TONE_TOGGLE_DIVIDER *
                effectivePeriod.toLong()
            var accumulator = toneClockAccumulator[channel] + OpnPitch.MASTER_CLOCK_HZ.toLong()
            var output = toneOutput[channel]
            while (accumulator >= threshold) {
                accumulator -= threshold
                output = output xor 1
            }
            toneClockAccumulator[channel] = accumulator
            toneOutput[channel] = output
            toneBuffer[channel * OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK + frame] =
                if (output == 0) -1f else 1f
            channel++
        }
    }

    private fun advanceToneRamp(channel: Int) {
        val frames = toneRampFrames[channel]
        var position = toneRampPosition[channel]
        if (frames <= 0 || position >= frames) return
        position++
        toneRampPosition[channel] = position
        tonePeriod[channel] = (
            toneRampStart[channel].toLong() +
                (toneRampTarget[channel] - toneRampStart[channel]).toLong() * position / frames
            ).toInt()
    }

    private fun prepareNoiseFrame(frame: Int) {
        val threshold = sampleRate.toLong() * SsgHardwareLaws.NOISE_CLOCK_DIVIDER * noisePeriod
        noiseClockAccumulator += OpnPitch.MASTER_CLOCK_HZ.toLong()
        while (noiseClockAccumulator >= threshold) {
            noiseClockAccumulator -= threshold
            clockNoise()
        }
        noiseBuffer[frame] = if (noiseOutput == 0) -1f else 1f
    }

    private fun prepareEnvelopeFrame(frame: Int) {
        val threshold = sampleRate.toLong() * SsgHardwareLaws.ENVELOPE_STEP_DIVIDER * envelopePeriod
        envelopeClockAccumulator += OpnPitch.MASTER_CLOCK_HZ.toLong()
        while (envelopeClockAccumulator >= threshold) {
            envelopeClockAccumulator -= threshold
            clockEnvelopeStep()
        }
        envelopeBuffer[frame] = envelopeLevel.coerceIn(0, ENVELOPE_MAX_LEVEL)
    }

    private fun clockNoise() {
        val feedback = (lfsr xor (lfsr ushr NOISE_FEEDBACK_TAP)) and 1
        lfsr = (lfsr ushr 1) or (feedback shl NOISE_TOP_BIT)
        noiseOutput = lfsr and 1
    }

    private fun clearClockAccumulators() {
        var channel = 0
        while (channel < toneClockAccumulator.size) {
            toneClockAccumulator[channel] = 0L
            channel++
        }
        noiseClockAccumulator = 0L
        envelopeClockAccumulator = 0L
    }

    private fun restartEnvelope() {
        envelopeHolding = false
        val attack = (envelopeShape and ENVELOPE_ATTACK_BIT) != 0
        envelopeLevel = if (attack) 0 else ENVELOPE_MAX_LEVEL
        envelopeDirection = if (attack) 1 else -1
    }

    private fun clockEnvelopeStep() {
        if (envelopeHolding) return
        envelopeLevel += envelopeDirection
        if (envelopeLevel in 0..ENVELOPE_MAX_LEVEL) return

        val continues = (envelopeShape and ENVELOPE_CONTINUE_BIT) != 0
        val alternate = (envelopeShape and ENVELOPE_ALTERNATE_BIT) != 0
        val hold = (envelopeShape and ENVELOPE_HOLD_BIT) != 0
        if (!continues) {
            envelopeLevel = 0
            envelopeHolding = true
            return
        }
        if (hold) {
            if (alternate) envelopeDirection = -envelopeDirection
            envelopeLevel = if (envelopeDirection > 0) ENVELOPE_MAX_LEVEL else 0
            envelopeHolding = true
            return
        }
        if (alternate) envelopeDirection = -envelopeDirection
        envelopeLevel = if (envelopeDirection > 0) 0 else ENVELOPE_MAX_LEVEL
    }

    private companion object {
        const val MIXER_ALL_DISABLED = 0x3F
        const val MIXER_REGISTER_MASK = 0x3F
        const val NOISE_ENABLE_BIT_OFFSET = 3
        const val NOISE_LFSR_RESET = 0x1FFFF
        const val NOISE_FEEDBACK_TAP = 3
        const val NOISE_TOP_BIT = 16
        const val ENVELOPE_MAX_LEVEL = 31
        const val ENVELOPE_SHAPE_MASK = 0x0F
        const val ENVELOPE_CONTINUE_BIT = 0x08
        const val ENVELOPE_ATTACK_BIT = 0x04
        const val ENVELOPE_ALTERNATE_BIT = 0x02
        const val ENVELOPE_HOLD_BIT = 0x01
    }
}
