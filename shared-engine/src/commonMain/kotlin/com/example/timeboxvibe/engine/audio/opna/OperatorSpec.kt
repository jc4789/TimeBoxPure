package com.example.timeboxvibe.engine.audio.opna

data class OperatorSpec(
    val mul: Int,
    val detune: Int,
    val tl: Int,
    val ar: Int,
    val dr: Int,
    val sr: Int,
    val sl: Int,
    val rr: Int,
    val ks: Int = 0,
    /** OPNA operator AM-enable bit: zero disables, non-zero enables. */
    val ams: Int = 0,
    /** OPNA SSG-EG shape 8..15; values below 8 disable SSG-EG. */
    val ssgEg: Int = 0
)
