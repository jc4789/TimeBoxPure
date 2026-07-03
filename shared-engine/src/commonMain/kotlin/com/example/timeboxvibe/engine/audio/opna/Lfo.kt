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
        private const val WAVE_SCALE = 1024
        private const val PHASE_SCALE = 4_294_967_296.0

        // YM2608 hardware LFO rates, represented in millihertz.
        private val RATE_MILLIHERTZ = intArrayOf(3_980, 5_560, 6_020, 6_370, 6_880, 9_630, 48_100, 72_200)
        private val SINE_Q10 = IntArray(TABLE_SIZE) { i ->
            val angle = i.toDouble() * kotlin.math.PI * 2.0 / TABLE_SIZE.toDouble()
            (kotlin.math.sin(angle) * WAVE_SCALE.toDouble()).toInt()
        }
    }

    private val pmBuffer = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private val amBuffer = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private var phase: UInt = 0u
    private var phaseStep: UInt = 0u

    var enabled: Boolean = false
    var rate: Int = 0
        set(value) {
            field = value.coerceIn(0, 7)
            val hz = RATE_MILLIHERTZ[field].toDouble() / 1000.0
            phaseStep = (hz * PHASE_SCALE / sampleRate.coerceAtLeast(1).toDouble()).toLong().toUInt()
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
                amBuffer[i] = (signed + WAVE_SCALE) shr 1
                phase += phaseStep
            } else {
                pmBuffer[i] = 0
                amBuffer[i] = 0
            }
            i++
        }
    }

    internal fun pmAt(frame: Int): Int = pmBuffer[frame]
    internal fun amAt(frame: Int): Int = amBuffer[frame]

    fun reset() {
        phase = 0u
        var i = 0
        while (i < pmBuffer.size) {
            pmBuffer[i] = 0
            amBuffer[i] = 0
            i++
        }
    }
}
