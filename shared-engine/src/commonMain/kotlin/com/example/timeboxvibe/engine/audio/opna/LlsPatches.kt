package com.example.timeboxvibe.engine.audio.opna

object LlsPatches {
    val At54 = FmPatch(
        algorithm = 4, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 30, attack = 0.002f, decay = 0.030f, sustain = 0.65f, release = 0.06f),
        op1 = OperatorSpec(mul = 0, detune = 1, tl = 24, attack = 0.003f, decay = 0.050f, sustain = 0.70f, release = 0.08f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 34, attack = 0.004f, decay = 0.060f, sustain = 0.70f, release = 0.10f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 8,  attack = 0.005f, decay = 0.060f, sustain = 0.70f, release = 0.12f),
        totalLevel = 0.70f,
        pms = 2, pan = 0
    )

    val At74 = FmPatch(
        algorithm = 4, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 32, attack = 0.001f, decay = 0.040f, sustain = 0.0f, release = 0.040f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 22, attack = 0.001f, decay = 0.060f, sustain = 0.1f, release = 0.060f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 34, attack = 0.001f, decay = 0.300f, sustain = 0.0f, release = 0.300f),
        op3 = OperatorSpec(mul = 1, detune = 2, tl = 10, attack = 0.002f, decay = 0.400f, sustain = 0.0f, release = 0.400f),
        totalLevel = 0.50f,
        pms = 1, pan = 0
    )

    val At99 = FmPatch(
        algorithm = 1, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 20, attack = 0.002f, decay = 0.025f, sustain = 0.5f, release = 0.020f),
        op1 = OperatorSpec(mul = 1, detune = 3, tl = 34, attack = 0.002f, decay = 0.035f, sustain = 0.4f, release = 0.025f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 30, attack = 0.002f, decay = 0.045f, sustain = 0.4f, release = 0.040f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 16, attack = 0.002f, decay = 0.055f, sustain = 0.75f, release = 0.080f),
        totalLevel = 0.45f,
        pms = 1, pan = 0
    )

    val At181 = FmPatch(
        algorithm = 4, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 28, attack = 0.015f, decay = 0.080f, sustain = 0.55f, release = 0.080f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 12, attack = 0.020f, decay = 0.120f, sustain = 0.55f, release = 0.120f),
        op2 = OperatorSpec(mul = 1, detune = 3, tl = 30, attack = 0.015f, decay = 0.080f, sustain = 0.55f, release = 0.080f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 12, attack = 0.020f, decay = 0.120f, sustain = 0.55f, release = 0.120f),
        totalLevel = 0.40f,
        pms = 3, pan = 0
    )
}
