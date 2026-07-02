package com.example.timeboxvibe.engine.audio.opna

data class OperatorSpec(
    val mul: Int,
    val detune: Int,
    val tl: Int,
    val attack: Float = 0.002f,
    val decay: Float = 0.06f,
    val sustain: Float = 0.55f,
    val release: Float = 0.04f,
    val sl: Int = 10,
    val rr: Int = 12,
    val ar: Int = 12,
    val dr: Int = 10,
    val ks: Int = 0,
    val ams: Int = 0,
    val egMode: EgMode = EgMode.LEGACY_ADSR
)
