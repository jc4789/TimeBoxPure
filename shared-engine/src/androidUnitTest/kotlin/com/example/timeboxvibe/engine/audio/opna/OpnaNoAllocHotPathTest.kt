package com.example.timeboxvibe.engine.audio.opna

import org.junit.Test
import kotlin.test.assertTrue

class OpnaNoAllocHotPathTest {
    @Test
    fun testNoAllocationsInHotPath() {
        val synth = OpnaLikeSynthesizer(44100)
        val seq = OpnaSequencer(44100, 120)
        OpnaPatterns.focusMotif(seq)

        val buffer = FloatArray(1024)

        // Warmup to let JIT compile
        var i = 0
        while (i < 500) {
            synth.render(buffer, 1024, seq, i * 1024L)
            i++
        }

        System.gc()
        Thread.sleep(100)
        System.gc()

        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        i = 0
        while (i < 1000) {
            synth.render(buffer, 1024, seq, i * 1024L)
            i++
        }

        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val diff = memAfter - memBefore

        assertTrue(diff <= 1024, "Hot path allocated memory: $diff bytes")
    }
}
