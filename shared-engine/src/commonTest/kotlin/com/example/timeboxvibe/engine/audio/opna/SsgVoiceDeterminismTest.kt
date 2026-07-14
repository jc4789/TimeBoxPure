package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SsgVoiceDeterminismTest {
    @Test
    fun testDeterminismAndSquare() {
        val shared1 = SsgSharedState()
        val shared2 = SsgSharedState()
        shared1.writeToneEnabled(0, true)
        shared2.writeToneEnabled(0, true)
        val ssg1 = SsgVoice(0, shared1)
        val ssg2 = SsgVoice(0, shared2)

        ssg1.noteOn(440f)
        ssg2.noteOn(440f)

        val buf1 = FloatArray(1024)
        val buf2 = FloatArray(1024)

        ssg1.render(buf1, 1024, 44100, 1.0f)
        ssg2.render(buf2, 1024, 44100, 1.0f)

        assertTrue(buf1.contentEquals(buf2))

        val bufOneSec = FloatArray(44100)
        val shared = SsgSharedState(44_100)
        shared.writeToneEnabled(0, true)
        val ssg = SsgVoice(0, shared, 44_100)
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
        val harness = EnvelopeHarness()
        val voice = harness.voice
        val patch = requireNotNull(OpnaPatchBank.ssgPatch(OpnaPatchBank.SSG_LLS_SQUARE))
        voice.applyPatch(patch)
        harness.configure(PmdPerformanceLaws.ENVELOPE_LEGACY, 2, -1, 24, 1, 0, 0)
        harness.setClockAndTempo(PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL, 180_000)
        harness.noteOn()

        harness.render(1_400)
        assertEquals(-1, harness.level())

        harness.render(16_000)
        assertEquals(-2, harness.level())

        val beforeRelease = harness.level()
        harness.noteOff()
        harness.render(700)
        assertTrue(harness.level() < beforeRelease)
    }

    @Test
    fun normalEnvelopeClockTracksTempoButExtendedClockDoesNot() {
        val slow = envelopeVoice(PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL, 60_000)
        val fast = envelopeVoice(PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL, 120_000)
        slow.render(5_000)
        fast.render(5_000)
        assertTrue(fast.level() < slow.level())

        val extendedSlow = envelopeVoice(PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED, 60_000)
        val extendedFast = envelopeVoice(PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED, 240_000)
        extendedSlow.render(5_000)
        extendedFast.render(5_000)
        assertEquals(
            extendedSlow.level(),
            extendedFast.level()
        )
    }

    @Test
    fun extendedEnvelopeRunsAttackDecayAndReleaseAtExClock() {
        val harness = EnvelopeHarness()
        harness.configure(PmdPerformanceLaws.ENVELOPE_EXTENDED, 31, 31, 0, 15, 8, 0)
        harness.setClockAndTempo(PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED, 60_000)
        harness.noteOn()
        assertEquals(0, harness.level())

        harness.render(900)
        assertTrue(harness.level() < 0)
        harness.noteOff()
        harness.render(900)
        assertTrue(!harness.voice.enabled)
    }

    private fun envelopeVoice(clockMode: Int, tempoMilli: Int): EnvelopeHarness {
        val harness = EnvelopeHarness()
        harness.configure(PmdPerformanceLaws.ENVELOPE_LEGACY, 0, 0, 1, 1, 0, 0)
        harness.setClockAndTempo(clockMode, tempoMilli)
        harness.noteOn()
        return harness
    }

    private class EnvelopeHarness {
        val voice = SsgVoice(0)
        private val performance = PmdPerformanceState(SAMPLE_RATE)

        fun configure(type: Int, al: Int, dd: Int, sr: Int, rr: Int, sl: Int, ar: Int) {
            performance.configureSsgEnvelope(0, type, al, dd, sr, rr, sl, ar)
        }

        fun setClockAndTempo(clockMode: Int, tempoMilli: Int) {
            performance.setSsgEnvelopeClockMode(0, clockMode)
            performance.setTempo(tempoMilli, 24)
        }

        fun noteOn() {
            performance.setSsgBaseLevel(0, voice.fixedLevelSnapshot())
            performance.noteOnSsg(0)
            voice.noteOn(440f)
        }

        fun noteOff() {
            voice.noteOff(performance.noteOffSsg(0))
        }

        fun level(): Int = performance.ssgEnvelopeLevelOffsetSnapshot(0)

        fun render(frames: Int) {
            val output = FloatArray(frames)
            var rendered = 0
            while (rendered < frames) {
                val count = minOf(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK, frames - rendered)
                performance.setSsgBaseLevel(0, voice.fixedLevelSnapshot())
                performance.prepare(count)
                voice.renderDriven(
                    output, count, SAMPLE_RATE, 1f, rendered,
                    sharedPrepared = false, driverFrame = performance.ssgFrame(0)
                )
                rendered += count
            }
        }

        companion object {
            private const val SAMPLE_RATE = 48_000
        }
    }
}
