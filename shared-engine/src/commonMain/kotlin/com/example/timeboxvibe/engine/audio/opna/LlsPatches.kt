package com.example.timeboxvibe.engine.audio.opna

/** OPN register voices decoded from ZUN's PMD .M files in the LLS music archive. */
object LlsPatches {
    // The operator core peaks at 0.5 per carrier; reserve room for PMD voices
    // with two or three simultaneous carriers before the shared chip mix bus.
    private const val CHIP_CHANNEL_SCALE = 0.38f

    val At54 = FmPatch(
        algorithm = 4, feedback = 4,
        op0 = op(mul = 8, detune = 7, tl = 17, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0),
        op1 = op(mul = 4, detune = 7, tl = 0, ar = 31, dr = 16, sr = 0, sl = 2, rr = 8, am = 1),
        op2 = op(mul = 4, detune = 3, tl = 20, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0),
        op3 = op(mul = 2, detune = 3, tl = 0, ar = 31, dr = 16, sr = 0, sl = 2, rr = 8, am = 1),
        totalLevel = CHIP_CHANNEL_SCALE
    )

    val At74 = FmPatch(
        algorithm = 0, feedback = 4,
        op0 = op(mul = 6, detune = 6, tl = 28, ar = 31, dr = 7, sr = 7, sl = 2, rr = 9, ks = 3),
        op1 = op(mul = 5, detune = 6, tl = 58, ar = 31, dr = 6, sr = 6, sl = 1, rr = 9, ks = 3),
        op2 = op(mul = 0, detune = 6, tl = 22, ar = 31, dr = 9, sr = 6, sl = 1, rr = 9, ks = 2),
        op3 = op(mul = 1, detune = 6, tl = 0, ar = 31, dr = 6, sr = 8, sl = 15, rr = 9, ks = 2),
        totalLevel = CHIP_CHANNEL_SCALE
    )

    val At99 = FmPatch(
        algorithm = 5, feedback = 7,
        op0 = op(mul = 1, detune = 0, tl = 29, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0),
        op1 = op(mul = 1, detune = 0, tl = 0, ar = 31, dr = 14, sr = 3, sl = 3, rr = 15),
        op2 = op(mul = 1, detune = 0, tl = 0, ar = 31, dr = 14, sr = 3, sl = 3, rr = 15),
        op3 = op(mul = 1, detune = 0, tl = 0, ar = 31, dr = 14, sr = 3, sl = 3, rr = 15),
        totalLevel = CHIP_CHANNEL_SCALE
    )

    val At181 = FmPatch(
        algorithm = 4, feedback = 7,
        op0 = op(mul = 8, detune = 7, tl = 27, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0),
        op1 = op(mul = 4, detune = 7, tl = 0, ar = 31, dr = 16, sr = 0, sl = 2, rr = 8),
        op2 = op(mul = 8, detune = 3, tl = 30, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0),
        op3 = op(mul = 4, detune = 3, tl = 0, ar = 31, dr = 16, sr = 0, sl = 2, rr = 8),
        totalLevel = CHIP_CHANNEL_SCALE
    )

    private fun op(
        mul: Int,
        detune: Int,
        tl: Int,
        ar: Int,
        dr: Int,
        sr: Int,
        sl: Int,
        rr: Int,
        ks: Int = 0,
        am: Int = 0
    ) = OperatorSpec(
        mul = mul,
        detune = detune,
        tl = tl,
        ar = ar,
        dr = dr,
        sr = sr,
        sl = sl,
        rr = rr,
        ks = ks,
        ams = am,
        egMode = EgMode.OPN_RATE
    )
}
