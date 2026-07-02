package com.example.timeboxvibe.engine.audio.opna

internal object AudioSinLut {
    private const val TWO_PI = 6.283185307179586
    private const val PHASE_BITS = 10
    const val SIZE = 1 shl PHASE_BITS
    private const val INDEX_MASK = SIZE - 1
    private const val SINE_AMPLITUDE_STEPS = 256f

    // Procedurally reproduces every signed 10-bit entry in the reference sinetab.coe.
    // Its negative half has an even-value encoding asymmetry and two endpoint entries.
    private val tableInt = IntArray(SIZE) { i ->
        val phase = (i.toDouble() + 0.5) / SIZE.toDouble()
        val scaled = kotlin.math.sin(phase * TWO_PI).toFloat() * SINE_AMPLITUDE_STEPS
        val rounded = if (scaled >= 0f) (scaled + 0.5f).toInt() else (scaled - 0.5f).toInt()
        when {
            i == POSITIVE_ZERO_CROSSING_ROM_ADDRESS -> POSITIVE_ZERO_CROSSING_ROM_SAMPLE
            i == NEGATIVE_ZERO_CROSSING_ROM_ADDRESS -> NEGATIVE_ZERO_CROSSING_ROM_SAMPLE
            rounded < 0 && (rounded and 1) == 0 -> rounded - NEGATIVE_EVEN_ROM_BIAS
            else -> rounded
        }
    }

    fun sample10BitInt(phaseIndex: Int): Int = tableInt[phaseIndex and INDEX_MASK]

    private const val NEGATIVE_EVEN_ROM_BIAS = 2
    private const val POSITIVE_ZERO_CROSSING_ROM_ADDRESS = 508
    private const val POSITIVE_ZERO_CROSSING_ROM_SAMPLE = 6
    private const val NEGATIVE_ZERO_CROSSING_ROM_ADDRESS = 1020
    private const val NEGATIVE_ZERO_CROSSING_ROM_SAMPLE = -8
}

internal object DrumSinLut {
    private const val TWO_PI = 6.283185307179586
    private const val SIZE = 8192

    private val table = FloatArray(SIZE + 1) { i ->
        kotlin.math.sin((i.toDouble() / SIZE.toDouble()) * TWO_PI).toFloat()
    }

    fun sin01(phaseCycles: Double): Float {
        var wrapped = phaseCycles - kotlin.math.floor(phaseCycles)
        if (wrapped >= 1.0) wrapped = 0.0
        if (wrapped < 0.0) wrapped = 0.0

        val x = wrapped * SIZE.toDouble()
        val i = x.toInt().coerceIn(0, SIZE - 1)
        val fraction = (x - i.toDouble()).toFloat()
        val first = table[i]
        return first + (table[i + 1] - first) * fraction
    }
}
