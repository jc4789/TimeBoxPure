package com.example.timeboxvibe.engine.audio.opna

internal object AudioSinLut {
    private const val TWO_PI = 6.283185307179586
    const val SIZE = 8192
    const val MASK = SIZE - 1

    private val table = FloatArray(SIZE + 1) { i ->
        kotlin.math.sin((i.toDouble() / SIZE.toDouble()) * TWO_PI).toFloat()
    }

    fun sin01(phaseCycles: Float): Float {
        val wrapped = phaseCycles - kotlin.math.floor(phaseCycles)
        val x = wrapped * SIZE.toFloat()
        val i0 = x.toInt() and MASK
        val frac = x - x.toInt()
        val a = table[i0]
        val b = table[i0 + 1]
        return a + (b - a) * frac
    }
}
