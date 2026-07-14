package com.example.timeboxvibe.engine.audio.opna

data class FmPatch(
    val algorithm: Int,
    val feedback: Int,
    val op0: OperatorSpec,
    val op1: OperatorSpec,
    val op2: OperatorSpec,
    val op3: OperatorSpec,
    val totalLevel: Float = 0.5f,
    val pms: Int = 0,
    val ams: Int = 0,
    /** YM output-enable combination: 0=both, 1=left, 2=right, 3=neither. */
    val pan: Int = 0
)
