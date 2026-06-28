package com.example.timeboxvibe.engine.audio.opna

data class FmPatch(
    val algorithm: Int,
    val feedback: Int,
    val op0: OperatorSpec,
    val op1: OperatorSpec,
    val op2: OperatorSpec,
    val op3: OperatorSpec,
    val totalLevel: Float = 0.5f
)
