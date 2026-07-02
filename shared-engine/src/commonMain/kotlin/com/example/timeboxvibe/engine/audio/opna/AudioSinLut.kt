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
        
        var v = if (scaled >= 0f) (scaled + 0.5f).toInt() else (scaled - 0.5f).toInt()
        
        if (i == 511) v = 0     // Zero-crossing endpoint anomaly
        if (i == 1023) v = -1   // Wrap-around endpoint anomaly
        
        if (v < 0 && v % 2 == 0) {
            v -= 2 // NEGATIVE_EVEN_ROM_BIAS
        }
        v
    }

    fun sample10BitInt(phaseIndex: Int): Int = tableInt[phaseIndex and INDEX_MASK]
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
