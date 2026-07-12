package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SsgVoiceDeterminismTest {
    @Test
    fun testDeterminismAndSquare() {
        val ssg1 = SsgVoice(0)
        val ssg2 = SsgVoice(0)

        ssg1.noteOn(440f)
        ssg2.noteOn(440f)

        val buf1 = FloatArray(1024)
        val buf2 = FloatArray(1024)

        ssg1.render(buf1, 1024, 44100, 1.0f)
        ssg2.render(buf2, 1024, 44100, 1.0f)

        assertTrue(buf1.contentEquals(buf2))

        val bufOneSec = FloatArray(44100)
        val ssg = SsgVoice(0)
        ssg.noteOn(440f)
        ssg.render(bufOneSec, 44100, 44100, 1.0f)

        var crossings = 0
        var i = 1
        while (i < 44100) {
            if ((bufOneSec[i] >= 0f && bufOneSec[i - 1] < 0f) || (bufOneSec[i] < 0f && bufOneSec[i - 1] >= 0f)) {
                crossings++
            }
            i++
        }

        val diff = kotlin.math.abs(crossings - 880)
        assertTrue(diff <= 5, "Crossings was $crossings, expected ~880")
    }

    @Test
    fun llsLegacySoftwareEnvelopeFollowsPmdTickStages() {
        val voice = SsgVoice(0)
        val patch = requireNotNull(OpnaPatchBank.ssgPatch(OpnaPatchBank.SSG_LLS_SQUARE))
        voice.applyPatch(patch)
        voice.noteOn(440f)

        voice.render(FloatArray(1_600), 1_600, 48_000, 1f)
        assertEquals(-1, voice.softwareEnvelopeLevelOffsetSnapshot())

        voice.render(FloatArray(18_000), 18_000, 48_000, 1f)
        assertEquals(-2, voice.softwareEnvelopeLevelOffsetSnapshot())

        val beforeRelease = voice.softwareEnvelopeLevelOffsetSnapshot()
        voice.noteOff()
        voice.render(FloatArray(800), 800, 48_000, 1f)
        assertTrue(voice.softwareEnvelopeLevelOffsetSnapshot() < beforeRelease)
    }
}
