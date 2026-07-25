package com.example.timeboxvibe.engine.audio.opna

/**
 * Shared OPNA hardware LFO clock.
 *
 * The waveform table is generated from a sine rule during initialization. The
 * render path advances one unsigned phase and writes into preallocated buffers.
 */
class Lfo(private val sampleRate: Int = 48_000) {
    companion object {
        private const val TABLE_BITS = 10
        private const val TABLE_SIZE = 1 shl TABLE_BITS
        private const val TABLE_SHIFT = 32 - TABLE_BITS
        private val SINE_Q10 = IntArray(TABLE_SIZE) { i ->
            val angle = i.toDouble() * kotlin.math.PI * 2.0 / TABLE_SIZE.toDouble()
            (kotlin.math.sin(angle) * OpnaLfoLaws.WAVE_SCALE.toDouble()).toInt()
        }
    }

    private val pmBuffer = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private val amBuffer = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private var phase: UInt = 0u
    private var phaseStep: UInt = 0u
    private var phaseRemainderStep: Long = 0L
    private var phaseRemainder: Long = 0L
    private val phaseDenominator = sampleRate.coerceAtLeast(1).toLong() *
        OpnaLfoLaws.MILLIHERTZ_PER_HERTZ

    var enabled: Boolean = false
        set(value) {
            if (field != value) resetPhase()
            field = value
        }
    var rate: Int = 0
        set(value) {
            field = value.coerceIn(0, 7)
            val numerator = OpnaLfoLaws.rateMilliHertz(field).toLong() * OpnaLfoLaws.PHASE_CYCLE
            phaseStep = (numerator / phaseDenominator).toUInt()
            phaseRemainderStep = numerator % phaseDenominator
        }

    init {
        rate = 0
    }

    fun prepare(frames: Int) {
        val count = frames.coerceAtMost(pmBuffer.size)
        var i = 0
        while (i < count) {
            if (enabled) {
                val index = (phase shr TABLE_SHIFT).toInt()
                val signed = SINE_Q10[index]
                pmBuffer[i] = signed
                amBuffer[i] = (signed + OpnaLfoLaws.WAVE_SCALE) shr 1
                phase += phaseStep
                phaseRemainder += phaseRemainderStep
                if (phaseRemainder >= phaseDenominator) {
                    phase++
                    phaseRemainder -= phaseDenominator
                }
            } else {
                pmBuffer[i] = 0
                amBuffer[i] = 0
            }
            i++
        }
    }

    internal fun pmAt(frame: Int): Int = pmBuffer[frame]
    internal fun amAt(frame: Int): Int = amBuffer[frame]
    internal fun phaseSnapshot(): UInt = phase
    internal fun phaseRemainderSnapshot(): Long = phaseRemainder

    fun reset() {
        enabled = false
        rate = 0
        resetPhase()
        var i = 0
        while (i < pmBuffer.size) {
            pmBuffer[i] = 0
            amBuffer[i] = 0
            i++
        }
    }

    private fun resetPhase() {
        phase = 0u
        phaseRemainder = 0L
    }
}

/**
 * Physical YM2608 Timer B clock.
 *
 * The preset is the reload latch. [loadAndStart] loads it into the active
 * period, and each overflow automatically reloads the latest preset while
 * carrying the sub-sample master-clock remainder forward.
 */
internal class OpnaTimerB(private val sampleRate: Int) {
    companion object {
        private const val PRESET_MIN = 0
        private const val PRESET_MAX = 255
        private const val COUNTER_MODULUS = 256
        private const val MASTER_CLOCKS_PER_STEP = 1152
    }

    private val overflowCount = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private var preset = PRESET_MIN
    private var activePeriodThreshold = thresholdFor(preset)
    private var masterClockAccumulator = 0L
    private var running = false
    private var notificationsEnabled = false
    private var preparedFrames = 0

    init {
        require(sampleRate > 0) { "Timer B sample rate must be positive" }
    }

    fun setPreset(value: Int) {
        require(value >= PRESET_MIN && value <= PRESET_MAX) { "Timer B preset must be in 0..255" }
        preset = value
    }

    fun loadAndStart() {
        activePeriodThreshold = thresholdFor(preset)
        masterClockAccumulator = 0L
        running = true
    }

    fun stop() {
        running = false
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled = enabled
    }

    fun prepare(frames: Int) {
        require(frames >= 0 && frames <= overflowCount.size) {
            "Timer B frame count exceeds the prepared interval"
        }
        overflowCount.fill(0, 0, frames)
        preparedFrames = frames
        if (!running || frames == 0) return

        var frame = 0
        while (frame < frames) {
            masterClockAccumulator += OpnPitch.MASTER_CLOCK_HZ.toLong()
            var overflows = 0
            while (masterClockAccumulator >= activePeriodThreshold) {
                masterClockAccumulator -= activePeriodThreshold
                activePeriodThreshold = thresholdFor(preset)
                overflows++
            }
            if (notificationsEnabled) overflowCount[frame] = overflows
            frame++
        }
    }

    fun consumeOverflowCount(frame: Int): Int {
        require(frame >= 0 && frame < preparedFrames) {
            "Timer B notification frame is outside the prepared interval"
        }
        val count = overflowCount[frame]
        overflowCount[frame] = 0
        return count
    }

    fun reset() {
        preset = PRESET_MIN
        activePeriodThreshold = thresholdFor(preset)
        masterClockAccumulator = 0L
        running = false
        notificationsEnabled = false
        preparedFrames = 0
        overflowCount.fill(0)
    }

    private fun thresholdFor(value: Int): Long {
        val periodMasterClocks = MASTER_CLOCKS_PER_STEP.toLong() * (COUNTER_MODULUS - value).toLong()
        return sampleRate.toLong() * periodMasterClocks
    }
}
