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
        voice.configureSoftwareEnvelope(PmdPerformanceLaws.ENVELOPE_LEGACY, 2, -1, 24, 1, 0, 0)
        voice.setSoftwareEnvelopeClockMode(PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL)
        voice.setSoftwareEnvelopeTempo(180_000, 24)
        voice.noteOn(440f)

        voice.render(FloatArray(1_400), 1_400, 48_000, 1f)
        assertEquals(-1, voice.softwareEnvelopeLevelOffsetSnapshot())

        voice.render(FloatArray(16_000), 16_000, 48_000, 1f)
        assertEquals(-2, voice.softwareEnvelopeLevelOffsetSnapshot())

        val beforeRelease = voice.softwareEnvelopeLevelOffsetSnapshot()
        voice.noteOff()
        voice.render(FloatArray(700), 700, 48_000, 1f)
        assertTrue(voice.softwareEnvelopeLevelOffsetSnapshot() < beforeRelease)
    }

    @Test
    fun normalEnvelopeClockTracksTempoButExtendedClockDoesNot() {
        val slow = envelopeVoice(PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL, 60_000)
        val fast = envelopeVoice(PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL, 120_000)
        slow.render(FloatArray(5_000), 5_000, 48_000, 1f)
        fast.render(FloatArray(5_000), 5_000, 48_000, 1f)
        assertTrue(fast.softwareEnvelopeLevelOffsetSnapshot() < slow.softwareEnvelopeLevelOffsetSnapshot())

        val extendedSlow = envelopeVoice(PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED, 60_000)
        val extendedFast = envelopeVoice(PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED, 240_000)
        extendedSlow.render(FloatArray(5_000), 5_000, 48_000, 1f)
        extendedFast.render(FloatArray(5_000), 5_000, 48_000, 1f)
        assertEquals(
            extendedSlow.softwareEnvelopeLevelOffsetSnapshot(),
            extendedFast.softwareEnvelopeLevelOffsetSnapshot()
        )
    }

    @Test
    fun extendedEnvelopeRunsAttackDecayAndReleaseAtExClock() {
        val voice = SsgVoice(0)
        voice.configureSoftwareEnvelope(PmdPerformanceLaws.ENVELOPE_EXTENDED, 31, 31, 0, 15, 8, 0)
        voice.setSoftwareEnvelopeClockMode(PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED)
        voice.setSoftwareEnvelopeTempo(60_000, 24)
        voice.noteOn(440f)
        assertEquals(0, voice.softwareEnvelopeLevelOffsetSnapshot())

        voice.render(FloatArray(900), 900, 48_000, 1f)
        assertTrue(voice.softwareEnvelopeLevelOffsetSnapshot() < 0)
        voice.noteOff()
        voice.render(FloatArray(900), 900, 48_000, 1f)
        assertTrue(!voice.enabled)
    }

    private fun envelopeVoice(clockMode: Int, tempoMilli: Int): SsgVoice {
        val voice = SsgVoice(0)
        voice.configureSoftwareEnvelope(PmdPerformanceLaws.ENVELOPE_LEGACY, 0, 0, 1, 1, 0, 0)
        voice.setSoftwareEnvelopeClockMode(clockMode)
        voice.setSoftwareEnvelopeTempo(tempoMilli, 24)
        voice.noteOn(440f)
        return voice
    }
}
