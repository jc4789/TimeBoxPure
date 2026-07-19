package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.mml.MmlCompileResult
import com.example.timeboxvibe.engine.audio.mml.MmlCompiler
import java.lang.management.ManagementFactory
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpnaNoAllocHotPathTest {
    @Test
    fun productSessionDoesNotAllocateInSteadyStateRenderPath() {
        val arrangement = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 1\n#PMDCLOCK 24\n#BAR 4/4\n" +
                    "A @54 Q7 o4 l4 c d e f |\n" +
                    "G @square EX0 E2,-1,8,1 Q8 o5 l4 c d e f |"
            )
        ).arrangement
        val session = OpnaPlaybackSession.createProduct(arrangement, 1f, looping = true)
        val buffer = FloatArray(1024)
        var i = 0
        while (i < 500) {
            session.render(buffer, buffer.size)
            i++
        }

        val allocationBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        allocationBean.isThreadAllocatedMemoryEnabled = true
        val threadId = Thread.currentThread().id
        // Enabling allocation instrumentation can trigger one final JBR bookkeeping allocation.
        // Consume that transition before opening the zero-allocation measurement window.
        i = 0
        while (i < 1000) {
            session.render(buffer, buffer.size)
            i++
        }
        val before = allocationBean.getThreadAllocatedBytes(threadId)

        i = 0
        while (i < 1000) {
            session.render(buffer, buffer.size)
            i++
        }

        val after = allocationBean.getThreadAllocatedBytes(threadId)
        val allocated = after - before
        // This local JBR reports a nondeterministic 56..312 byte observer/JIT floor even after
        // instrumentation warm-up. Keep the measured ceiling explicit and small; the static
        // hot-function audit is the platform-independent zero-allocation gate.
        assertTrue(
            allocated <= JBR_MEASUREMENT_NOISE_CEILING,
            "Product session steady-state window allocated $allocated bytes"
        )
    }

    private companion object {
        const val JBR_MEASUREMENT_NOISE_CEILING = 512L
    }
}
