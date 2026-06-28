package com.example.timeboxvibe.engine.audio.opna

object Patches {
    val ZunLead1 = FmPatch(
        algorithm = 0, feedback = 3,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 127, modulationIndex = 2.0f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 20,  modulationIndex = 1.5f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 24,  modulationIndex = 1.0f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 12,  modulationIndex = 0f),
        totalLevel = 0.6f
    )

    val ZunBell1 = FmPatch(
        algorithm = 0, feedback = 4,
        op0 = OperatorSpec(mul = 2, detune = 0, tl = 127, modulationIndex = 2.0f),
        op1 = OperatorSpec(mul = 2, detune = 0, tl = 8,   modulationIndex = 1.5f),
        op2 = OperatorSpec(mul = 2, detune = 0, tl = 16,  modulationIndex = 1.0f),
        op3 = OperatorSpec(mul = 2, detune = 0, tl = 0,   modulationIndex = 0f),
        totalLevel = 0.5f
    )

    val ZunBass1 = FmPatch(
        algorithm = 1, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 0,  modulationIndex = 2.0f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 24, modulationIndex = 2.0f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 8,  modulationIndex = 0f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 16, modulationIndex = 0f),
        totalLevel = 0.7f
    )

    val ZunPad1 = FmPatch(
        algorithm = 1, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 12, modulationIndex = 2.0f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 8,  modulationIndex = 2.0f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 8,  modulationIndex = 0f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 8,  modulationIndex = 0f),
        totalLevel = 0.4f
    )
}
