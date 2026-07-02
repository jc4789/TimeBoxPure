package com.example.timeboxvibe.engine.audio.opna

object LlsPatches {
    val At54 = FmPatch(
        algorithm = 4, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 30, ar = 25, dr = 15, sr = 12, sl = 1, rr = 11, egMode = EgMode.OPN_RATE),
        op1 = OperatorSpec(mul = 0, detune = 1, tl = 24, ar = 24, dr = 13, sr = 0, sl = 1, rr = 10, egMode = EgMode.OPN_RATE),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 34, ar = 23, dr = 13, sr = 12, sl = 1, rr = 10, egMode = EgMode.OPN_RATE),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 8, ar = 22, dr = 13, sr = 0, sl = 1, rr = 10, egMode = EgMode.OPN_RATE),
        totalLevel = 0.70f,
        pms = 2, pan = 0
    )

    val At74 = FmPatch(
        algorithm = 4, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 32, ar = 27, dr = 24, sr = 1, sl = 15, rr = 11, egMode = EgMode.OPN_RATE),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 22, ar = 27, dr = 18, sr = 0, sl = 7, rr = 11, egMode = EgMode.OPN_RATE),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 34, ar = 27, dr = 18, sr = 1, sl = 15, rr = 9, egMode = EgMode.OPN_RATE),
        op3 = OperatorSpec(mul = 1, detune = 2, tl = 10, ar = 25, dr = 17, sr = 0, sl = 15, rr = 8, egMode = EgMode.OPN_RATE),
        totalLevel = 0.50f,
        pms = 1, pan = 0
    )

    val At99 = FmPatch(
        algorithm = 1, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 20, ar = 25, dr = 17, sr = 11, sl = 2, rr = 12, egMode = EgMode.OPN_RATE),
        op1 = OperatorSpec(mul = 1, detune = 3, tl = 34, ar = 25, dr = 17, sr = 11, sl = 3, rr = 12, egMode = EgMode.OPN_RATE),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 30, ar = 25, dr = 17, sr = 11, sl = 3, rr = 11, egMode = EgMode.OPN_RATE),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 16, ar = 25, dr = 13, sr = 0, sl = 1, rr = 10, egMode = EgMode.OPN_RATE),
        totalLevel = 0.45f,
        pms = 1, pan = 0
    )

    val At181 = FmPatch(
        algorithm = 4, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 28, ar = 19, dr = 14, sr = 12, sl = 2, rr = 10, egMode = EgMode.OPN_RATE),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 12, ar = 18, dr = 13, sr = 0, sl = 2, rr = 10, egMode = EgMode.OPN_RATE),
        op2 = OperatorSpec(mul = 1, detune = 3, tl = 30, ar = 19, dr = 14, sr = 12, sl = 2, rr = 10, egMode = EgMode.OPN_RATE),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 12, ar = 18, dr = 13, sr = 0, sl = 2, rr = 10, egMode = EgMode.OPN_RATE),
        totalLevel = 0.40f,
        pms = 3, pan = 0
    )
}
