package com.example.timeboxvibe.engine.audio.opna

/** YM2608 hardware-LFO constants in the engine's integer render units. */
internal object OpnaLfoLaws {
    const val WAVE_SCALE = 1_024
    const val PHASE_CYCLE = 4_294_967_296L
    const val MILLIHERTZ_PER_HERTZ = 1_000L

    private val rateMilliHertz = intArrayOf(
        3_980, 5_560, 6_020, 6_370, 6_880, 9_630, 48_100, 72_200
    )

    // Manual peaks 0, 3.4, 6.7, 10, 14, 20, 40, and 80 cents in Q20 ratios.
    private val pmsDepthQ20 = intArrayOf(
        0, 2_061, 4_066, 6_074, 8_514, 12_184, 24_509, 49_591
    )

    // Manual peaks 0, 1.4, 5.9, and 11.8 dB in 0.09375 dB envelope units.
    private val amsDepthAttenuation = intArrayOf(0, 15, 63, 126)

    fun rateMilliHertz(rate: Int): Int = rateMilliHertz[rate.coerceIn(0, 7)]
    fun pmsDepthQ20(pms: Int): Int = pmsDepthQ20[pms.coerceIn(0, 7)]
    fun amsDepthAttenuation(ams: Int): Int = amsDepthAttenuation[ams.coerceIn(0, 3)]
}
