package com.example.timeboxvibe.engine.audio.opna

internal object AudioSinLut {
    private const val TWO_PI = 6.283185307179586
    const val SIZE = 8192

    private val table = FloatArray(SIZE + 1) { i ->
        kotlin.math.sin((i.toDouble() / SIZE.toDouble()) * TWO_PI).toFloat()
    }

    fun sin01(phaseCycles: Double): Float {
        val wrapped = phaseCycles - kotlin.math.floor(phaseCycles)
        val x = wrapped * SIZE.toDouble()
        val i = x.toInt()
        val frac = (x - i.toDouble()).toFloat()

        val a = table[i]
        val b = table[i + 1]

        return a + (b - a) * frac
    }
}