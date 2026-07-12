package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpnaLfoConformanceTest {
    @Test
    fun everyHardwareRateAdvancesByItsManualFrequency() {
        val sampleRate = 48_000
        var rate = 0
        while (rate < 8) {
            val lfo = Lfo(sampleRate)
            lfo.rate = rate
            lfo.enabled = true
            var remaining = sampleRate
            while (remaining > 0) {
                val count = minOf(remaining, OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
                lfo.prepare(count)
                remaining -= count
            }
            val numerator = OpnaLfoLaws.rateMilliHertz(rate).toLong() * OpnaLfoLaws.PHASE_CYCLE
            assertEquals((numerator / 1_000L).toUInt(), lfo.phaseSnapshot(), "rate=$rate")
            assertEquals(
                (numerator % 1_000L) * sampleRate.toLong(),
                lfo.phaseRemainderSnapshot(),
                "rate=$rate"
            )
            rate++
        }
    }

    @Test
    fun hardwareSineAndAmAreBipolarAndUnipolarViewsOfOnePhase() {
        val lfo = Lfo(48_000)
        lfo.rate = 7
        lfo.enabled = true
        var minimum = Int.MAX_VALUE
        var maximum = Int.MIN_VALUE
        var frames = 0
        while (frames < 48_000) {
            val count = minOf(1_024, 48_000 - frames)
            lfo.prepare(count)
            var i = 0
            while (i < count) {
                val pm = lfo.pmAt(i)
                assertEquals((pm + OpnaLfoLaws.WAVE_SCALE) shr 1, lfo.amAt(i))
                if (pm < minimum) minimum = pm
                if (pm > maximum) maximum = pm
                i++
            }
            frames += count
        }
        assertTrue(minimum <= -1_020)
        assertTrue(maximum >= 1_020)
    }

    @Test
    fun enableEdgesResetTheGlobalOscillator() {
        val lfo = Lfo(48_000)
        lfo.rate = 7
        lfo.enabled = true
        lfo.prepare(100)
        assertTrue(lfo.phaseSnapshot() != 0u)
        lfo.enabled = false
        assertEquals(0u, lfo.phaseSnapshot())
        lfo.enabled = true
        lfo.prepare(1)
        assertEquals(0, lfo.pmAt(0))
        assertEquals(OpnaLfoLaws.WAVE_SCALE / 2, lfo.amAt(0))
    }

    @Test
    fun pmsAndAmsMappingsMatchTheYm2608Manual() {
        val pms = intArrayOf(0, 2_061, 4_066, 6_074, 8_514, 12_184, 24_509, 49_591)
        var i = 0
        while (i < pms.size) {
            assertEquals(pms[i], OpnaLfoLaws.pmsDepthQ20(i))
            i++
        }
        val ams = intArrayOf(0, 15, 63, 126)
        i = 0
        while (i < ams.size) {
            assertEquals(ams[i], OpnaLfoLaws.amsDepthAttenuation(i))
            i++
        }
    }

    @Test
    fun operatorAmEnableIsRequiredForChannelAmsToAffectOutput() {
        val source = OpnaPatchBank.Pc98Brass
        val disabled = source.copy(
            pms = 0,
            ams = 3,
            op0 = source.op0.copy(ams = 0),
            op1 = source.op1.copy(ams = 0),
            op2 = source.op2.copy(ams = 0),
            op3 = source.op3.copy(ams = 0)
        )
        val enabled = disabled.copy(op3 = disabled.op3.copy(ams = 1))
        val dry = renderVoice(disabled, false)
        val disabledWet = renderVoice(disabled, true)
        val enabledWet = renderVoice(enabled, true)
        assertTrue(dry.contentEquals(disabledWet))
        assertTrue(!dry.contentEquals(enabledWet))
    }

    @Test
    fun hardwarePhaseIsIndependentOfPrepareChunking() {
        val whole = Lfo(48_000)
        val chunked = Lfo(48_000)
        whole.rate = 5
        chunked.rate = 5
        whole.enabled = true
        chunked.enabled = true
        whole.prepare(1_024)
        var remaining = 1_024
        while (remaining > 0) {
            val count = minOf(127, remaining)
            chunked.prepare(count)
            remaining -= count
        }
        assertEquals(whole.phaseSnapshot(), chunked.phaseSnapshot())
        assertEquals(whole.phaseRemainderSnapshot(), chunked.phaseRemainderSnapshot())
    }

    private fun renderVoice(patch: FmPatch, withLfo: Boolean): FloatArray {
        val voice = Fm4OpVoice(48_000)
        voice.applyPatch(patch)
        voice.noteOn(69)
        val lfo = Lfo(48_000)
        lfo.rate = 7
        lfo.enabled = withLfo
        val output = FloatArray(4_096)
        var offset = 0
        while (offset < output.size) {
            val count = minOf(1_024, output.size - offset)
            lfo.prepare(count)
            voice.render(output, count, 48_000, 1f, offset, lfo)
            offset += count
        }
        return output
    }
}
