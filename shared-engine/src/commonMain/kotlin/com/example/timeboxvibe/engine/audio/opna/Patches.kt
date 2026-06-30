package com.example.timeboxvibe.engine.audio.opna

object Patches {
    val ZunLead1 = FmPatch(
        algorithm = 0, feedback = 3,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 0,  modulationIndex = 2.5f, attack = 0.001f, decay = 0.02f, sustain = 0.6f, release = 0.08f),
        op1 = OperatorSpec(mul = 1, detune = 1, tl = 8,  modulationIndex = 1.8f, attack = 0.003f, decay = 0.04f, sustain = 0.7f, release = 0.1f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 14, modulationIndex = 1.2f, attack = 0.004f, decay = 0.05f, sustain = 0.7f, release = 0.12f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 4,  modulationIndex = 0f,   attack = 0.005f, decay = 0.05f, sustain = 0.7f, release = 0.15f),
        totalLevel = 0.75f,
        pms = 2, pan = 0
    )

    val ZunBell1 = FmPatch(
        algorithm = 2, feedback = 1,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 0,  modulationIndex = 2.5f, attack = 0.001f, decay = 0.030f, sustain = 0.0f, release = 0.030f),
        op1 = OperatorSpec(mul = 2, detune = 0, tl = 4,  modulationIndex = 1.8f, attack = 0.001f, decay = 0.050f, sustain = 0.1f, release = 0.050f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 12, modulationIndex = 1.2f,  attack = 0.001f, decay = 0.250f, sustain = 0.0f, release = 0.250f),
        op3 = OperatorSpec(mul = 1, detune = 3, tl = 6,  modulationIndex = 0f,   attack = 0.002f, decay = 0.350f, sustain = 0.0f, release = 0.350f),
        totalLevel = 0.55f,
        pms = 1, pan = 0
    )

    val ZunBass1 = FmPatch(
        algorithm = 1, feedback = 1,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 0,  modulationIndex = 2.0f, attack = 0.002f, decay = 0.030f, sustain = 0.5f, release = 0.020f),
        op1 = OperatorSpec(mul = 1, detune = 4, tl = 16, modulationIndex = 1.5f, attack = 0.002f, decay = 0.040f, sustain = 0.4f, release = 0.030f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 12, modulationIndex = 1.2f,  attack = 0.002f, decay = 0.050f, sustain = 0.4f, release = 0.050f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 18, modulationIndex = 0f,   attack = 0.002f, decay = 0.060f, sustain = 0.0f, release = 0.050f),
        totalLevel = 0.45f,
        pms = 1, pan = 0
    )

    val ZunPad1 = FmPatch(
        algorithm = 4, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 6,  modulationIndex = 1.8f, attack = 0.020f, decay = 0.100f, sustain = 0.5f, release = 0.100f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 10, modulationIndex = 0f,   attack = 0.025f, decay = 0.150f, sustain = 0.5f, release = 0.150f),
        op2 = OperatorSpec(mul = 1, detune = 4, tl = 8,  modulationIndex = 1.8f, attack = 0.020f, decay = 0.100f, sustain = 0.5f, release = 0.100f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 10, modulationIndex = 0f,   attack = 0.025f, decay = 0.150f, sustain = 0.5f, release = 0.150f),
        totalLevel = 0.45f,
        pms = 3, pan = 0
    )

    val ZunChime1 = FmPatch(
        algorithm = 7, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 6,  modulationIndex = 0f, attack = 0.001f, decay = 0.150f, sustain = 0.0f, release = 0.150f),
        op1 = OperatorSpec(mul = 2, detune = 3, tl = 8,  modulationIndex = 0f, attack = 0.001f, decay = 0.200f, sustain = 0.0f, release = 0.200f),
        op2 = OperatorSpec(mul = 3, detune = 0, tl = 10, modulationIndex = 0f, attack = 0.002f, decay = 0.250f, sustain = 0.0f, release = 0.250f),
        op3 = OperatorSpec(mul = 4, detune = 0, tl = 12, modulationIndex = 0f, attack = 0.002f, decay = 0.300f, sustain = 0.0f, release = 0.300f),
        totalLevel = 0.55f,
        pms = 0, pan = 0
    )
}
