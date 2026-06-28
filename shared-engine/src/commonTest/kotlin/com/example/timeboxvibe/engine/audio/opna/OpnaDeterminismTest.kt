package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaDeterminismTest {
    private val testBpm = 120f

    @Test
    fun testSynthDeterminism() {
        val synth1 = OpnaLikeSynthesizer(44100)
        val synth2 = OpnaLikeSynthesizer(44100)

        val seq1 = OpnaSequencer(44100, testBpm)
        val seq2 = OpnaSequencer(44100, testBpm)

        OpnaPatterns.focusMotif(seq1)
        OpnaPatterns.focusMotif(seq2)

        val buf1 = FloatArray(4096)
        val buf2 = FloatArray(4096)

        synth1.render(buf1, 4096, seq1, 0L)
        synth2.render(buf2, 4096, seq2, 0L)

        assertTrue(buf1.contentEquals(buf2))
    }
}
