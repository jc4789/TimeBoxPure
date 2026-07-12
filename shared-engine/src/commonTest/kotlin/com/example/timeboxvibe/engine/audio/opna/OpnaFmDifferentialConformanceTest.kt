package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpnaFmDifferentialConformanceTest {
    @Test
    fun lowRateFractionOneUsesTheDocumentedFivePulseSubcycle() {
        val expected = intArrayOf(0, 1, 0, 1, 1, 1, 0, 1)
        var ordinal = 0
        while (ordinal < expected.size) {
            assertEquals(
                expected[ordinal],
                OpnRateEnvelope.proceduralIncrement(21, (ordinal + 1L) * 64L),
                "ordinal=$ordinal"
            )
            ordinal++
        }
    }

    @Test
    fun envelopeRegisterBoundariesFollowTheManualRateEquation() {
        var keyScaleValue = 0
        while (keyScaleValue <= 31) {
            var rate = 0
            while (rate <= 31) {
                val expected = if (rate == 0) 0 else (rate * 2 + keyScaleValue).coerceAtMost(63)
                assertEquals(expected, OpnRateEnvelope.effectiveRate(rate, keyScaleValue, release = false))
                rate++
            }
            var release = 0
            while (release <= 15) {
                val expected = (release * 4 + 2 + keyScaleValue).coerceAtMost(63)
                assertEquals(expected, OpnRateEnvelope.effectiveRate(release, keyScaleValue, release = true))
                release++
            }
            keyScaleValue++
        }
        var keyCode = 0
        while (keyCode <= 31) {
            var keyScale = 0
            while (keyScale <= 3) {
                assertEquals(keyCode ushr (3 - keyScale), OpnRateEnvelope.keyScaleValue(keyCode, keyScale))
                keyScale++
            }
            keyCode++
        }
        var sustainLevel = 0
        while (sustainLevel < 15) {
            assertEquals(sustainLevel * 32, OpnRateEnvelope.sustainAttenuation(sustainLevel))
            sustainLevel++
        }
        assertEquals(992, OpnRateEnvelope.sustainAttenuation(15))
    }

    @Test
    fun decaySustainAndReleaseCheckpointsUseLinearEightStepRate62() {
        val envelope = OpnRateEnvelope()
        envelope.setSampleRate(18_518)
        envelope.attackRate = 31
        envelope.decayRate = 31
        envelope.sustainRate = 31
        envelope.sustainLevel = 4
        envelope.releaseRate = 15
        envelope.setKeyScale(0, 0)
        envelope.noteOn()
        assertEquals(OpnRateEnvelope.DECAY, envelope.stage)
        var tick = 1
        while (tick <= 16) {
            assertEquals(tick * 8, envelope.nextAttenuation(), "decay tick=$tick")
            tick++
        }
        assertEquals(OpnRateEnvelope.SUSTAIN, envelope.stage)
        assertEquals(136, envelope.nextAttenuation())
        envelope.noteOff()
        assertEquals(144, envelope.nextAttenuation())
    }

    @Test
    fun everyAlgorithmMatchesAnIndependentRegisterStepTrace() {
        var algorithm = 0
        while (algorithm < 8) {
            compareTrace(algorithm, feedback = 0, frames = 24)
            algorithm++
        }
    }

    @Test
    fun everyFeedbackSettingMatchesIndependentHistoryAndSamples() {
        var feedback = 0
        while (feedback < 8) {
            compareTrace(algorithm = 7, feedback = feedback, frames = 32)
            feedback++
        }
    }

    @Test
    fun mulZeroThroughFifteenAndSignedDetuneMatchRegisterPhaseLaw() {
        val packed = (4 shl 11) or 1_038
        var multiple = 0
        while (multiple <= 15) {
            val spec = REGISTER_OPERATORS[0].copy(mul = multiple, detune = 0)
            assertEquals(referencePhaseStep(packed, spec), OpnPitch.phaseStep29(packed, spec, SAMPLE_RATE))
            multiple++
        }
        var detune = 0
        while (detune <= 7) {
            val spec = REGISTER_OPERATORS[0].copy(mul = 1, detune = detune)
            assertEquals(referencePhaseStep(packed, spec), OpnPitch.phaseStep29(packed, spec, SAMPLE_RATE))
            detune++
        }
    }

    @Test
    fun keyCodeAndSelectedDetuneRowsMatchTheManualTables() {
        val expectedLow = intArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3)
        var nibble = 0
        while (nibble < expectedLow.size) {
            assertEquals(12 + expectedLow[nibble], OpnPitch.keyCode(3, nibble shl 7), "nibble=$nibble")
            nibble++
        }
        assertDetuneRow(0, 0, 1, 2)
        assertDetuneRow(16, 2, 5, 8)
        assertDetuneRow(23, 5, 10, 14)
        assertDetuneRow(27, 7, 14, 20)
        assertDetuneRow(31, 8, 16, 22)
    }

    @Test
    fun fm3SpecialModeKeepsFourIndependentRegisterPitches() {
        val voice = Fm4OpVoice(SAMPLE_RATE)
        voice.applyPatch(registerPatch(7, 0))
        val midi = intArrayOf(48, 55, 60, 67)
        var operator = 0
        while (operator < 4) {
            voice.noteOnOperator(operator, midi[operator])
            val packed = OpnPitch.nearestBlockFnumForMidi(midi[operator])
            assertEquals(
                referencePhaseStep(packed, REGISTER_OPERATORS[operator]),
                voice.operatorPhaseStepSnapshot(operator)
            )
            operator++
        }
        voice.render(FloatArray(64), 64, SAMPLE_RATE, 1f)
        assertTrue(voice.operatorPhaseSnapshot(0) != voice.operatorPhaseSnapshot(1))
        assertTrue(voice.operatorPhaseSnapshot(1) != voice.operatorPhaseSnapshot(2))
        assertTrue(voice.operatorPhaseSnapshot(2) != voice.operatorPhaseSnapshot(3))
    }

    @Test
    fun hardwarePmAndOperatorAmMatchCoreStateCheckpoints() {
        val operators = arrayOf(
            REGISTER_OPERATORS[0].copy(ams = 0),
            REGISTER_OPERATORS[1].copy(ams = 0),
            REGISTER_OPERATORS[2].copy(ams = 0),
            REGISTER_OPERATORS[3].copy(ams = 1)
        )
        val patch = FmPatch(7, 0, operators[0], operators[1], operators[2], operators[3], TOTAL_LEVEL, 7, 3)
        val voice = Fm4OpVoice(SAMPLE_RATE)
        voice.applyPatch(patch)
        voice.noteOn(69)
        val lfo = Lfo(SAMPLE_RATE)
        lfo.rate = 7
        lfo.enabled = true
        lfo.prepare(166)
        lfo.prepare(1)
        val pmQ20 = (lfo.pmAt(0) * OpnaLfoLaws.pmsDepthQ20(7)) shr 10
        val amAttenuation = (lfo.amAt(0) * OpnaLfoLaws.amsDepthAttenuation(3)) shr 10
        voice.render(FloatArray(1), 1, SAMPLE_RATE, 1f, lfo = lfo)
        var operator = 0
        while (operator < 4) {
            val base = referencePhaseStep((4 shl 11) or 1_038, operators[operator]).toLong()
            val expectedPhase = (base + ((base * pmQ20.toLong()) shr 20)).toUInt()
            assertEquals(expectedPhase, voice.operatorPhaseSnapshot(operator), "op=$operator phase")
            operator++
        }
        assertEquals(referenceOperatorOutput(0, operators[0].tl), voice.operatorOutputSnapshot(0))
        assertEquals(
            referenceOperatorOutput(0, operators[3].tl, amAttenuation),
            voice.operatorOutputSnapshot(3)
        )
    }

    @Test
    fun centerPanRoutesTheFullCoreSampleToBothYmOutputBuses() {
        val monoSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val stereoSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
        monoSynth.enableOutputFilter = false
        stereoSynth.enableOutputFilter = false
        val patch = registerPatch(7, 0).copy(pan = 0)
        monoSynth.fm[0].applyPatch(patch)
        stereoSynth.fm[0].applyPatch(patch)
        monoSynth.fm[0].noteOn(69)
        stereoSynth.fm[0].noteOn(69)
        val mono = FloatArray(512)
        val stereo = FloatArray(1_024)
        monoSynth.render(mono, mono.size)
        stereoSynth.renderStereo(stereo, mono.size)
        var frame = 0
        while (frame < mono.size) {
            assertEquals(mono[frame], stereo[frame * 2], "left frame=$frame")
            assertEquals(mono[frame], stereo[frame * 2 + 1], "right frame=$frame")
            frame++
        }
    }

    @Test
    fun everySsgEgShapeEntersOrdinaryReleaseWithoutALevelJump() {
        var shape = 8
        while (shape <= 15) {
            val envelope = OpnRateEnvelope()
            envelope.setSampleRate(18_518)
            envelope.attackRate = 31
            envelope.decayRate = 0
            envelope.sustainRate = 31
            envelope.sustainLevel = 0
            envelope.releaseRate = 8
            envelope.configureSsgEg(shape)
            envelope.noteOn()
            var tick = 0
            while (tick < 23) {
                envelope.nextAttenuation()
                tick++
            }
            val visibleBefore = envelope.currentAttenuation()
            envelope.noteOff()
            assertEquals(visibleBefore, envelope.currentAttenuation(), "shape=$shape")
            assertEquals(OpnRateEnvelope.RELEASE, envelope.stage)
            shape++
        }
    }

    @Test
    fun allEightSsgEgBoundaryShapesMatchTheManualBitMeanings() {
        val expected = intArrayOf(0, 1_022, 1_022, 0, 1_022, 0, 0, 1_022)
        var shape = 8
        while (shape <= 15) {
            val envelope = OpnRateEnvelope()
            envelope.setSampleRate(18_518)
            envelope.attackRate = 31
            envelope.decayRate = 0
            envelope.sustainRate = 31
            envelope.sustainLevel = 0
            envelope.configureSsgEg(shape)
            envelope.noteOn()
            var tick = 0
            while (tick < 64) {
                envelope.nextAttenuation()
                tick++
            }
            assertEquals(expected[shape - 8], envelope.currentAttenuation(), "shape=$shape")
            shape++
        }
    }

    private fun compareTrace(algorithm: Int, feedback: Int, frames: Int) {
        val patch = registerPatch(algorithm, feedback)
        val voice = Fm4OpVoice(SAMPLE_RATE)
        voice.applyPatch(patch)
        voice.noteOn(69)
        val reference = ReferenceChannel(algorithm, feedback)
        val sample = FloatArray(1)
        var frame = 0
        while (frame < frames) {
            sample[0] = 0f
            voice.render(sample, 1, SAMPLE_RATE, 1f)
            val expected = reference.render()
            assertEquals(expected, sample[0], "algorithm=$algorithm feedback=$feedback frame=$frame mix")
            var operator = 0
            while (operator < 4) {
                assertEquals(
                    reference.output[operator],
                    voice.operatorOutputSnapshot(operator),
                    "algorithm=$algorithm feedback=$feedback frame=$frame op=$operator output"
                )
                assertEquals(
                    reference.phase[operator].toUInt(),
                    voice.operatorPhaseSnapshot(operator),
                    "algorithm=$algorithm feedback=$feedback frame=$frame op=$operator phase"
                )
                assertEquals(0, voice.operatorAttenuationSnapshot(operator))
                operator++
            }
            assertEquals(reference.feedbackHistory(), voice.feedbackHistorySnapshot())
            frame++
        }
    }

    private fun assertDetuneRow(keyCode: Int, first: Int, second: Int, third: Int) {
        val expected = intArrayOf(0, first, second, third)
        var magnitude = 0
        while (magnitude <= 3) {
            assertEquals(expected[magnitude], OpnPitch.detuneAdjustment(magnitude, keyCode))
            assertEquals(-expected[magnitude], OpnPitch.detuneAdjustment(magnitude + 4, keyCode))
            magnitude++
        }
    }

    private class ReferenceChannel(private val algorithm: Int, private val feedback: Int) {
        val phase = LongArray(4)
        val output = IntArray(4)
        private val step = LongArray(4) { index ->
            referencePhaseStep((4 shl 11) or 1_038, REGISTER_OPERATORS[index]).toLong()
        }
        private var feedback1 = 0
        private var feedback2 = 0

        fun render(): Float {
            val sum = when (algorithm) {
                0 -> op(3, op(2, op(1, op0())))
                1 -> op(3, op(2, op0() + free(1)))
                2 -> op(3, op0() + op(2, free(1)))
                3 -> op(3, op(1, op0()) + free(2))
                4 -> op(1, op0()) + op(3, free(2))
                5 -> {
                    val first = op0()
                    op(1, first) + op(2, first) + op(3, first)
                }
                6 -> op(1, op0()) + free(2) + free(3)
                else -> op0() + free(1) + free(2) + free(3)
            }
            return sum.toFloat() * TOTAL_LEVEL / 16_384f
        }

        fun feedbackHistory(): Long =
            (feedback1.toLong() shl 32) or (feedback2.toLong() and 0xffffffffL)

        private fun op0(): Int {
            val modulation = if (feedback > 0) {
                (feedback1 + feedback2) shr (10 - feedback)
            } else {
                0
            }
            val result = operator(0, modulation)
            feedback2 = feedback1
            feedback1 = result
            return result
        }

        private fun free(index: Int): Int = operator(index, 0)
        private fun op(index: Int, modulation: Int): Int = operator(index, modulation shr 1)

        private fun operator(index: Int, modulation: Int): Int {
            val phaseAddress = (((phase[index] ushr 19).toInt() + modulation) and 1_023)
            phase[index] = (phase[index] + step[index]) and 0xffffffffL
            val result = referenceOperatorOutput(phaseAddress, REGISTER_OPERATORS[index].tl)
            output[index] = result
            return result
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val TOTAL_LEVEL = 0.25f
        val REGISTER_OPERATORS = arrayOf(
            OperatorSpec(1, 0, 8, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0, egMode = EgMode.OPN_RATE),
            OperatorSpec(2, 0, 16, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0, egMode = EgMode.OPN_RATE),
            OperatorSpec(3, 0, 24, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0, egMode = EgMode.OPN_RATE),
            OperatorSpec(4, 0, 32, ar = 31, dr = 0, sr = 0, sl = 0, rr = 0, egMode = EgMode.OPN_RATE)
        )

        fun registerPatch(algorithm: Int, feedback: Int): FmPatch = FmPatch(
            algorithm, feedback,
            REGISTER_OPERATORS[0], REGISTER_OPERATORS[1], REGISTER_OPERATORS[2], REGISTER_OPERATORS[3],
            totalLevel = TOTAL_LEVEL
        )

        fun referencePhaseStep(packed: Int, spec: OperatorSpec): UInt {
            val block = (packed ushr 11) and 7
            val fnum = packed and 2_047
            val nibble = (fnum ushr 7) and 15
            val keyLow = when {
                nibble <= 6 -> 0
                nibble == 7 -> 1
                nibble == 8 -> 2
                else -> 3
            }
            val keyCode = block * 4 + keyLow
            var chipStep = (((fnum shl 1) shl block) ushr 2)
            val dt = spec.detune.coerceIn(0, 7)
            val detuneMagnitude = if ((dt and 3) == 0) {
                0
            } else if (keyCode == 18) {
                when (dt and 3) {
                    1 -> 3
                    2 -> 6
                    else -> 9
                }
            } else {
                check(dt == 0) { "Reference vector only names the nonzero A4 detune row" }
                0
            }
            val detune = if ((dt and 4) != 0) -detuneMagnitude else detuneMagnitude
            chipStep = (chipStep + detune) and 0x1ffff
            val multipleX2 = if (spec.mul == 0) 1 else spec.mul.coerceIn(1, 15) * 2
            chipStep = (chipStep * multipleX2) ushr 1
            val step = chipStep.toDouble() * (8_000_000.0 / 144.0) * 512.0 / SAMPLE_RATE
            return (step + 0.5).toLong().toUInt()
        }

        fun referenceOperatorOutput(phase: Int, totalLevel: Int, envelopeAttenuation: Int = 0): Int {
            val wrapped = phase and 1_023
            var quarter = wrapped and 255
            if ((wrapped and 256) != 0) quarter = 255 - quarter
            val angle = (quarter.toDouble() + 0.5) * kotlin.math.PI / 512.0
            val logSine = (-ln(sin(angle)) / ln(2.0) * 256.0 + 0.5).toInt()
            val envelopeAndTl = envelopeAttenuation.coerceIn(0, 1_023) + totalLevel.coerceIn(0, 127) * 8
            val totalAttenuation = logSine + (envelopeAndTl shl 2)
            val shift = totalAttenuation ushr 8
            if (shift >= 13) return 0
            val fraction = totalAttenuation and 255
            val magnitude = (8_191.0 * exp(-ln(2.0) * fraction / 256.0) + 0.5).toInt() ushr shift
            return if ((wrapped and 512) != 0) -magnitude else magnitude
        }
    }
}
