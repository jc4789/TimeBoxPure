package com.example.timeboxvibe.engine.audio.opna

import org.junit.Test
import kotlin.test.assertTrue

class OpnaNoAllocHotPathTest {
    private val testBpm = 120f

    @Test
    fun testNoAllocationsInHotPath() {
        val synth = OpnaLikeSynthesizer(44100)
        val seq = OpnaSequencer(44100, testBpm)
        OpnaPatterns.focusMotif(seq)
        seq.noteFmPolyControlledRaw(2, 60, 0L, 512L, 0.5f, Patches.ZunPad1, 0, 0, 0, 0, 0)
        seq.noteFmPolyControlledRaw(2, 64, 0L, 512L, 0.5f, Patches.ZunPad1, 0, 0, 0, 0, 0)
        seq.noteFmPolyControlledRaw(2, 67, 0L, 512L, 0.5f, Patches.ZunPad1, 0, 0, 0, 0, 0)
        synth.fm[0].applyPatch(Patches.ZunLead1)
        synth.fm[1].applyPatch(Patches.ZunBass1)

        val buffer = FloatArray(1024)

        // Warmup to let JIT compile
        var i = 0
        while (i < 500) {
            seq.resetPlaybackCursor()
            synth.render(buffer, 1024, seq, 0L)
            i++
        }

        System.gc()
        Thread.sleep(100)
        System.gc()

        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        i = 0
        while (i < 1000) {
            seq.resetPlaybackCursor()
            synth.render(buffer, 1024, seq, 0L)
            i++
        }

        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val diff = memAfter - memBefore

        assertTrue(diff <= 1024, "Hot path allocated memory: $diff bytes")
    }
}
