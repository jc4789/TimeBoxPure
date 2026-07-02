package com.example.timeboxvibe.engine.audio.opna

internal object OpnLogTables {
    const val PHASE_SIZE: Int = 1024
    const val QUARTER_SIZE: Int = 256
    private const val PHASE_MASK = PHASE_SIZE - 1
    private const val FRACTION_MASK = QUARTER_SIZE - 1
    private const val LOG_UNITS_PER_OCTAVE = 256.0
    private const val MAX_OUTPUT_SHIFT = 13
    private const val LN_2 = 0.6931471805599453

    // Generated from the documented ideal OPN curves. These are rules, not copied ROM data.
    private val logSineQuarter = IntArray(QUARTER_SIZE) { i ->
        val angle = (i.toDouble() + 0.5) * kotlin.math.PI / 512.0
        val attenuation = -kotlin.math.ln(kotlin.math.sin(angle)) / LN_2 * LOG_UNITS_PER_OCTAVE
        (attenuation + 0.5).toInt()
    }

    private val powerFraction = IntArray(QUARTER_SIZE) { i ->
        val amplitude = 8191.0 * kotlin.math.exp((-LN_2 * i.toDouble()) / LOG_UNITS_PER_OCTAVE)
        (amplitude + 0.5).toInt()
    }

    fun logSineAttenuation(phaseIndex: Int): Int {
        val phase = phaseIndex and PHASE_MASK
        var quarterIndex = phase and FRACTION_MASK
        if ((phase and QUARTER_SIZE) != 0) quarterIndex = FRACTION_MASK - quarterIndex
        return logSineQuarter[quarterIndex]
    }

    fun output(phaseIndex: Int, envelopeAttenuation: Int, totalLevel: Int): Int {
        val phase = phaseIndex and PHASE_MASK
        val envelopeAndTl = envelopeAttenuation.coerceIn(0, 1023) + totalLevel.coerceIn(0, 127) * 8
        val totalAttenuation = logSineAttenuation(phase) + (envelopeAndTl shl 2)
        val wholeShift = totalAttenuation ushr 8
        if (wholeShift >= MAX_OUTPUT_SHIFT) return 0
        val amplitude = powerFraction[totalAttenuation and FRACTION_MASK] ushr wholeShift
        return if ((phase and 512) != 0) -amplitude else amplitude
    }

    internal fun quarterLogValue(index: Int): Int = logSineQuarter[index.coerceIn(0, FRACTION_MASK)]

    internal fun powerValue(index: Int): Int = powerFraction[index.coerceIn(0, FRACTION_MASK)]

    fun warmUp() = Unit
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

    fun warmUp() = Unit
}
