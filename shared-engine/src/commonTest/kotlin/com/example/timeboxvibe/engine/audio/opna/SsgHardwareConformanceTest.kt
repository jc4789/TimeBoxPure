package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SsgHardwareConformanceTest {
    @Test
    fun legalTonePeriodsAreNearestToTheIndependentEightMegahertzOracle() {
        assertEquals(284, SsgHardwareLaws.nearestTonePeriod(440.0))
        assertEquals(478, SsgHardwareLaws.nearestTonePeriod(261.625565))
        assertEquals(4095, SsgHardwareLaws.nearestTonePeriod(8.175799))

        var midi = 24
        var maximumRelativeError = 0.0
        while (midi <= 127) {
            val expected = 440.0 * 2.0.pow((midi - 69).toDouble() / 12.0)
            val period = SsgHardwareLaws.nearestTonePeriod(expected)
            val actual = 8_000_000.0 / (64.0 * period)
            val selectedError = abs(actual - expected)
            if (period > 1) {
                val lowerPeriodError = abs(8_000_000.0 / (64.0 * (period - 1)) - expected)
                assertTrue(selectedError <= lowerPeriodError)
            }
            if (period < 4095) {
                val higherPeriodError = abs(8_000_000.0 / (64.0 * (period + 1)) - expected)
                assertTrue(selectedError <= higherPeriodError)
            }
            val relativeError = selectedError / expected
            if (relativeError > maximumRelativeError) maximumRelativeError = relativeError
            midi++
        }
        assertTrue(maximumRelativeError < 0.041)
    }

    @Test
    fun quantizedToneFrequencyIsStableAcrossRenderRates() {
        val expected = SsgHardwareLaws.toneFrequency(284)
        val rates = intArrayOf(44_100, 48_000, 55_466)
        var rateIndex = 0
        while (rateIndex < rates.size) {
            val sampleRate = rates[rateIndex]
            val shared = SsgSharedState(sampleRate)
            shared.writeToneEnabled(0, true)
            val voice = SsgVoice(0, shared, sampleRate)
            voice.noteOn(440f)
            val output = FloatArray(sampleRate)
            voice.render(output, output.size, sampleRate, 1f)
            var crossings = 0
            var i = 1
            while (i < output.size) {
                if ((output[i] >= 0f) != (output[i - 1] >= 0f)) crossings++
                i++
            }
            val measured = crossings / 2.0
            assertTrue(abs(measured - expected) < 1.0, "$sampleRate Hz render measured $measured")
            rateIndex++
        }
    }

    @Test
    fun registerSevenUsesActiveLowToneAndNoiseBitsPerChannel() {
        val state = SsgSharedState(48_000)
        assertEquals(0x3F, state.mixerRegisterSnapshot())

        state.writeMixerChannel(0, toneEnabled = true, noiseEnabled = false)
        assertEquals(0x3E, state.mixerRegisterSnapshot())
        state.writeMixerChannel(1, toneEnabled = false, noiseEnabled = true)
        assertEquals(0x2E, state.mixerRegisterSnapshot())
        state.writeMixerChannel(0, toneEnabled = true, noiseEnabled = true)
        assertEquals(0x26, state.mixerRegisterSnapshot())
    }

    @Test
    fun sharedNoiseMatchesTheSeventeenBitPolynomialTrace() {
        val state = SsgSharedState(125_000)
        state.writeNoisePeriod(1)
        val expected = intArrayOf(
            0x0FFFF, 0x07FFF, 0x03FFF, 0x01FFF, 0x00FFF, 0x007FF, 0x003FF,
            0x001FF, 0x000FF, 0x0007F, 0x0003F, 0x0001F, 0x0000F, 0x00007,
            0x10003, 0x18001, 0x1C000, 0x0E000, 0x07000, 0x03800
        )
        var i = 0
        while (i < expected.size) {
            state.prepare(1)
            assertEquals(expected[i], state.noiseLfsrSnapshot(), "17-bit trace step ${i + 1}")
            i++
        }
    }

    @Test
    fun envelopePeriodWriteDoesNotRestartButShapeWriteDoes() {
        val state = SsgSharedState(7_812)
        state.writeEnvelopePeriod(1)
        state.writeEnvelopeShape(12)
        assertEquals(1, state.envelopeRestartCountSnapshot())
        assertEquals(0, state.envelopeLevelSnapshot())
        state.prepare(1)
        assertEquals(1, state.envelopeLevelSnapshot())

        state.writeEnvelopePeriod(2)
        assertEquals(1, state.envelopeLevelSnapshot())
        assertEquals(1, state.envelopeRestartCountSnapshot())
        state.writeEnvelopeShape(13)
        assertEquals(0, state.envelopeLevelSnapshot())
        assertEquals(2, state.envelopeRestartCountSnapshot())
    }

    @Test
    fun voicePatchAndResetNeverWriteSharedRegisters() {
        val shared = SsgSharedState(48_000)
        shared.writeMixerChannel(0, toneEnabled = false, noiseEnabled = true)
        shared.writeNoisePeriod(27)
        shared.writeEnvelopePeriod(4_321)
        shared.writeEnvelopeShape(14)
        val voice = SsgVoice(0, shared, 48_000)

        voice.applyPatch(requireNotNull(OpnaPatchBank.ssgPatch(OpnaPatchBank.SSG_ENVELOPE_ALT)))
        voice.reset()

        assertEquals(0x37, shared.mixerRegisterSnapshot())
        assertEquals(27, shared.noisePeriodSnapshot())
        assertEquals(4_321, shared.envelopePeriodSnapshot())
        assertEquals(14, shared.envelopeShapeSnapshot())
        assertEquals(1, shared.envelopeRestartCountSnapshot())
    }

    @Test
    fun retainedSequencerAppliesSsgPatchRegistersAtTheEventBoundary() {
        val synth = OpnaLikeSynthesizer(8_000)
        val sequencer = OpnaSequencer(8_000, 120f)
        val noise = requireNotNull(OpnaPatchBank.ssgPatch(OpnaPatchBank.SSG_NOISE))
        sequencer.noteSsgControlledRaw(0, 60, 0L, 100L, 1f, patch = noise)

        val output = FloatArray(1)
        synth.render(output, output.size, sequencer, 0L)

        assertEquals(0x37, synth.ssg[0].mixerRegisterSnapshot())
        assertEquals(8, synth.ssgNoisePeriodSnapshot())
        assertTrue(output[0] != 0f)
    }

    @Test
    fun fixedLevelLawUsesOddFiveBitDacSteps() {
        assertEquals(0f, SsgLevelLaw.fixedAmplitude(0))
        assertEquals(1f, SsgLevelLaw.fixedAmplitude(15))
        val expectedStep = 10.0.pow(-1.5 / 20.0).toFloat()
        assertTrue(abs(SsgLevelLaw.fixedAmplitude(14) - expectedStep) < 0.000001f)
        assertTrue(
            abs(
                SsgLevelLaw.fixedAmplitude(13) / SsgLevelLaw.fixedAmplitude(14) - expectedStep
            ) < 0.000001f
        )
    }

    @Test
    fun hardwareEnvelopeKeepsAllThirtyTwoDacLevelsSeparateFromFixedVolume() {
        assertEquals(0f, SsgLevelLaw.envelopeAmplitude(0))
        assertEquals(1f, SsgLevelLaw.envelopeAmplitude(31))
        var level = 1
        var previous = SsgLevelLaw.envelopeAmplitude(0)
        while (level < 32) {
            val amplitude = SsgLevelLaw.envelopeAmplitude(level)
            assertTrue(amplitude > previous, "envelope DAC level $level must be distinct")
            val expected = 10.0.pow((level - 31) * 0.75 / 20.0).toFloat()
            assertTrue(abs(amplitude - expected) < 0.000001f)
            previous = amplitude
            level++
        }

        assertTrue(SsgLevelLaw.envelopeAmplitude(2) != SsgLevelLaw.fixedAmplitude(1))
        assertEquals(
            SsgLevelLaw.envelopeAmplitude(3),
            SsgLevelLaw.fixedAmplitude(1)
        )
    }

    @Test
    fun preparedHardwareEnvelopeExposesEveryFiveBitLevel() {
        val state = SsgSharedState(7_812)
        state.writeEnvelopePeriod(1)
        state.writeEnvelopeShape(12)
        assertEquals(0, state.envelopeLevelSnapshot())

        var expected = 1
        while (expected <= 31) {
            state.prepare(1)
            assertEquals(expected, state.envelopeLevelSnapshot())
            expected++
        }
    }

    @Test
    fun hardwareEnvelopeShapeBoundariesMatchContinueAlternateAndHoldBits() {
        assertEnvelopeBoundary(shape = 8, initial = 31, boundary = 0, transition = 31, afterTransition = 30)
        assertEnvelopeBoundary(shape = 9, initial = 31, boundary = 0, transition = 0, afterTransition = 0)
        assertEnvelopeBoundary(shape = 10, initial = 31, boundary = 0, transition = 0, afterTransition = 1)
        assertEnvelopeBoundary(shape = 11, initial = 31, boundary = 0, transition = 31, afterTransition = 31)
        assertEnvelopeBoundary(shape = 12, initial = 0, boundary = 31, transition = 0, afterTransition = 1)
        assertEnvelopeBoundary(shape = 13, initial = 0, boundary = 31, transition = 31, afterTransition = 31)
        assertEnvelopeBoundary(shape = 14, initial = 0, boundary = 31, transition = 31, afterTransition = 30)
        assertEnvelopeBoundary(shape = 15, initial = 0, boundary = 31, transition = 0, afterTransition = 0)
    }

    @Test
    fun hardwareEnvelopeStateIsInvariantToPreparationChunkSize() {
        val whole = SsgSharedState(7_812)
        val split = SsgSharedState(7_812)
        whole.writeEnvelopePeriod(1)
        split.writeEnvelopePeriod(1)
        whole.writeEnvelopeShape(14)
        split.writeEnvelopeShape(14)

        whole.prepare(64)
        var frame = 0
        while (frame < 64) {
            split.prepare(1)
            assertEquals(whole.envelopeAt(frame), split.envelopeAt(0), "frame $frame")
            frame++
        }
        assertEquals(whole.envelopeLevelSnapshot(), split.envelopeLevelSnapshot())
        assertEquals(whole.envelopeRestartCountSnapshot(), split.envelopeRestartCountSnapshot())
    }

    @Test
    fun outputProfilesKeepLegacyExactAndExposeReferenceBalance() {
        assertEquals(10f.pow(AudioLaws.SSG_GAIN_DB / 20f), OpnaOutputProfile.TIMEBOX_LEGACY.ssgGain)
        assertEquals(
            OpnaOutputProfile.TIMEBOX_LEGACY.fmGain,
            OpnaOutputProfile.PC9801_86_REFERENCE.fmGain
        )
        assertEquals(
            0.25f,
            OpnaOutputProfile.PC9801_86_REFERENCE.ssgGain /
                OpnaOutputProfile.PC9801_86_REFERENCE.fmGain
        )
        val defaultOutput = renderSsg(null)
        val explicitLegacy = renderSsg(OpnaOutputProfile.TIMEBOX_LEGACY)
        val reference = renderSsg(OpnaOutputProfile.PC9801_86_REFERENCE)
        assertContentEquals(defaultOutput, explicitLegacy)

        val expectedRatio = OpnaOutputProfile.PC9801_86_REFERENCE.ssgGain /
            OpnaOutputProfile.TIMEBOX_LEGACY.ssgGain
        var i = 0
        while (i < defaultOutput.size) {
            assertTrue(abs(reference[i] - defaultOutput[i] * expectedRatio) < 0.000001f)
            i++
        }
    }

    private fun renderSsg(profile: OpnaOutputProfile?): FloatArray {
        val synth = OpnaLikeSynthesizer(AudioLaws.SAMPLE_RATE)
        if (profile != null) synth.outputProfile = profile
        synth.enableOutputFilter = false
        synth.noteOnSsg(0, 69)
        val output = FloatArray(512)
        synth.render(output, output.size)
        return output
    }

    private fun assertEnvelopeBoundary(
        shape: Int,
        initial: Int,
        boundary: Int,
        transition: Int,
        afterTransition: Int
    ) {
        val state = SsgSharedState(7_812)
        state.writeEnvelopePeriod(1)
        state.writeEnvelopeShape(shape)
        assertEquals(initial, state.envelopeLevelSnapshot(), "shape $shape initial")
        state.prepare(31)
        assertEquals(boundary, state.envelopeLevelSnapshot(), "shape $shape boundary")
        state.prepare(1)
        assertEquals(transition, state.envelopeLevelSnapshot(), "shape $shape transition")
        state.prepare(1)
        assertEquals(afterTransition, state.envelopeLevelSnapshot(), "shape $shape after transition")
    }
}
