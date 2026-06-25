package com.example.timeboxvibe.engine

data class ToneSpec(
    val freq: Float,
    val startMs: Int,
    val durationMs: Int,
    val volume: Float,
    val type: String,
    val useADSR: Boolean = false,
    val attackMs: Int = 0,
    val decayMs: Int = 0,
    val sustainLevel: Float = 1.0f,
    val releaseMs: Int = 0
)
