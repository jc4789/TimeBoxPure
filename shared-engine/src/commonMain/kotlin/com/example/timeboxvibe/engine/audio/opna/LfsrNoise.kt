package com.example.timeboxvibe.engine.audio.opna

/**
 * 16-bit Galois LFSR for deterministic noise generation.
 * Has a maximal period of 65535.
 *
 * Per-channel seed convention:
 * 0xACE1 xor (channelIndex * 0x9E37)
 */
internal class LfsrNoise(initialSeed: Int = 0xACE1) {
    private var state: Int = if (initialSeed == 0) 0xACE1 else (initialSeed and 0xFFFF)

    fun reset(seed: Int) {
        state = if (seed == 0) 0xACE1 else (seed and 0xFFFF)
    }

    fun next(): Float {
        val out = if ((state and 1) != 0) 1f else -1f
        val lsb = state and 1
        state = state ushr 1
        if (lsb != 0) {
            state = state xor 0xB400 // Maximal-period polynomial for 16-bit LFSR
        }
        if (state == 0) {
            state = 0xACE1
        }
        return out
    }
}
