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
