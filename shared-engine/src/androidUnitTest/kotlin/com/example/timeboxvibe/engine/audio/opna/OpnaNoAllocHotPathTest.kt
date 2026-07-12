package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.mml.MmlCompileResult
import com.example.timeboxvibe.engine.audio.mml.MmlCompiler
import java.lang.management.ManagementFactory
import org.junit.Test
import kotlin.test.assertIs
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

    @Test
    fun compiledPlayerDoesNotAllocateInHotPath() {
        val arrangement = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 120\n#PMDCLOCK 24\n#BAR 4/4\n" +
                    "A @54 Q7 o4 l4 c d e f |\n" +
                    "G @square EX0 E2,-1,8,1 Q8 o5 l4 c d e f |"
            )
        ).arrangement
        val synth = OpnaLikeSynthesizer(44_100)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 44_100)
        val buffer = FloatArray(1024)
        var i = 0
        while (i < 500) {
            player.reset(synth)
            synth.render(buffer, buffer.size, player, 0L)
            i++
        }

        val allocationBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        allocationBean.isThreadAllocatedMemoryEnabled = true
        val threadId = Thread.currentThread().id
        val before = allocationBean.getThreadAllocatedBytes(threadId)

        i = 0
        while (i < 1000) {
            player.reset(synth)
            synth.render(buffer, buffer.size, player, 0L)
            i++
        }

        val after = allocationBean.getThreadAllocatedBytes(threadId)
        assertTrue(after - before <= 1024, "Compiled player hot path allocated ${after - before} bytes")
    }
}
