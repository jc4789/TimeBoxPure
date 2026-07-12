package com.example.timeboxvibe.engine.audio.opna

data class SsgPatch(
    val toneEnabled: Boolean = true,
    val noiseEnabled: Boolean = false,
    val fixedLevel: Int = 12,
    val envelopeEnabled: Boolean = false,
    val envelopeShape: Int = 9,
    val envelopePeriod: Int = 2048,
    val noisePeriod: Int = 16,
    val pan: Int = 0,
    val softwareEnvelopeClockHz: Float = 0f,
    val softwareEnvelopeAttackTicks: Int = 0,
    val softwareEnvelopeDecayLevel: Int = 0,
    val softwareEnvelopeSustainTicks: Int = 0,
    val softwareEnvelopeReleaseTicks: Int = 0
)
