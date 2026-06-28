package com.example.timeboxvibe.engine.audio

import kotlin.math.pow

internal fun midiToFreq(midi: Int): Float = 440f * 2f.pow((midi - 69) / 12f)
