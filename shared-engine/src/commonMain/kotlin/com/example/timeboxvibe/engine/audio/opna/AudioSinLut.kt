package com.example.timeboxvibe.engine.audio.opna

internal object AudioSinLut {
    private const val TWO_PI = 6.283185307179586
    const val SIZE = 8192

    private val table = FloatArray(SIZE + 1) { i ->
        kotlin.math.sin((i.toDouble() / SIZE.toDouble()) * TWO_PI).toFloat()
    }

    fun sin01(phaseCycles: Double): Float {
        var wrapped = phaseCycles - kotlin.math.floor(phaseCycles)
        if (wrapped >= 1.0) wrapped = 0.0
        if (wrapped < 0.0) wrapped = 0.0
        
        val x = wrapped * SIZE.toDouble()
        val i = x.toInt().coerceIn(0, SIZE - 1)
        val frac = (x - i.toDouble()).toFloat()

        val a = table[i]
        val b = table[i + 1]

        return a + (b - a) * frac
    }
}