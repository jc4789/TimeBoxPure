package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaStereoTest {

    @Test
    fun testStereoPanning() {
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)

        // Apply ZunLead1, but modify the pan to 1 (left)
        val leftPanPatch = Patches.ZunLead1.copy(pan = 1)
        synth.fm[0].applyPatch(leftPanPatch)
        synth.fm[0].noteOn(69) // A4

        // Render stereo block (4096 samples = 8192 float entries)
        val stereoBuffer = FloatArray(4096 * 2)
        synth.renderStereo(stereoBuffer, 4096)

        // Sum amplitude on left and right channels
        var leftSum = 0f
        var rightSum = 0f
        var i = 0
        while (i < 4096) {
            leftSum += abs(stereoBuffer[i * 2])
            rightSum += abs(stereoBuffer[i * 2 + 1])
            i++
        }

        assertTrue(leftSum > 10.0f, "Left channel should have active output signal ($leftSum)")
        assertTrue(rightSum < 0.001f, "Right channel should be silent ($rightSum) for hard left pan")

        // Now test right pan (pan = 2)
        val rightPanPatch = Patches.ZunLead1.copy(pan = 2)
        val synth2 = OpnaLikeSynthesizer(sampleRate)
        synth2.fm[0].applyPatch(rightPanPatch)
        synth2.fm[0].noteOn(69)

        val stereoBuffer2 = FloatArray(4096 * 2)
        synth2.renderStereo(stereoBuffer2, 4096)

        leftSum = 0f
        rightSum = 0f
        i = 0
        while (i < 4096) {
            leftSum += abs(stereoBuffer2[i * 2])
            rightSum += abs(stereoBuffer2[i * 2 + 1])
            i++
        }

        assertTrue(rightSum > 10.0f, "Right channel should have active output signal ($rightSum)")
        assertTrue(leftSum < 0.001f, "Left channel should be silent ($leftSum) for hard right pan")
    }
}
