package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpnProceduralCoreTest {
    @Test
    fun logCurvesAreMonotonicAndPhaseFoldIsSigned() {
        var i = 1
        while (i < OpnLogTables.QUARTER_SIZE) {
            assertTrue(OpnLogTables.quarterLogValue(i) <= OpnLogTables.quarterLogValue(i - 1))
            assertTrue(OpnLogTables.powerValue(i) <= OpnLogTables.powerValue(i - 1))
            i++
        }

        i = 0
        while (i < 512) {
            val positive = OpnLogTables.output(i, 0, 0)
            val negative = OpnLogTables.output(i + 512, 0, 0)
            assertEquals(-positive, negative, "phase sign mismatch at $i")
            i++
        }
        assertEquals(8191, OpnLogTables.output(256, 0, 0))
        assertEquals(0, OpnLogTables.output(256, 1023, 127))
    }

    @Test
    fun a4UsesNearestLegalFnumAndPitch() {
        val packed = OpnPitch.nearestBlockFnum(440.0)
        assertEquals(4, OpnPitch.block(packed))
        assertEquals(1038, OpnPitch.fnum(packed))
        assertTrue(abs(OpnPitch.frequencyFor(4, 1038) - 440.0) < 0.05)

        var midi = 0
        while (midi <= 116) {
            val ideal = 440.0 * 2.0.pow((midi - 69).toDouble() / 12.0)
            val note = OpnPitch.nearestBlockFnum(ideal)
            val actual = OpnPitch.frequencyFor(OpnPitch.block(note), OpnPitch.fnum(note))
            val cents = 1200.0 * kotlin.math.log2(actual / ideal)
            assertTrue(abs(cents) <= 3.0, "midi=$midi cents=$cents")
            midi++
        }
    }

    @Test
    fun phaseStepTracksNearestLegalFnumWithinTwoCents() {
        val sampleRates = intArrayOf(44_100, 48_000, 96_000)
        val midiNotes = intArrayOf(36, 48, 60, 69, 84, 108)
        val operator = OperatorSpec(
            mul = 1,
            detune = 0,
            tl = 0,
            ar = 31,
            dr = 0,
            sr = 0,
            sl = 0,
            rr = 0,
            egMode = EgMode.OPN_RATE
        )
        var sampleRateIndex = 0
        while (sampleRateIndex < sampleRates.size) {
            val sampleRate = sampleRates[sampleRateIndex]
            var noteIndex = 0
            while (noteIndex < midiNotes.size) {
                val packed = OpnPitch.nearestBlockFnumForMidi(midiNotes[noteIndex])
                val legalHz = OpnPitch.frequencyFor(OpnPitch.block(packed), OpnPitch.fnum(packed))
                val step = OpnPitch.phaseStep29(packed, operator, sampleRate)
                val renderedHz = step.toDouble() * sampleRate.toDouble() / (1L shl 29).toDouble()
                val cents = 1200.0 * kotlin.math.log2(renderedHz / legalHz)
                assertTrue(abs(cents) <= 2.0, "midi=${midiNotes[noteIndex]} rate=$sampleRate cents=$cents")
                noteIndex++
            }
            sampleRateIndex++
        }
    }

    @Test
    fun documentedDetuneHasCorrectSignAndMagnitude() {
        var keyCode = 0
        while (keyCode < 32) {
            assertEquals(0, OpnPitch.detuneAdjustment(0, keyCode))
            assertEquals(0, OpnPitch.detuneAdjustment(4, keyCode))
            var magnitude = 1
            while (magnitude <= 3) {
                val positive = OpnPitch.detuneAdjustment(magnitude, keyCode)
                val negative = OpnPitch.detuneAdjustment(magnitude + 4, keyCode)
                assertEquals(-positive, negative)
                if (magnitude > 1) {
                    assertTrue(positive >= OpnPitch.detuneAdjustment(magnitude - 1, keyCode))
                }
                magnitude++
            }
            keyCode++
        }
    }

    @Test
    fun attackUsesInvertedAttenuationHardwareShape() {
        val envelope = OpnRateEnvelope()
        envelope.setSampleRate(18_518)
        envelope.attackRate = 30
        envelope.decayRate = 0
        envelope.sustainLevel = 0
        envelope.setKeyScale(0, 0)
        envelope.noteOn()

        assertEquals(511, envelope.nextAttenuation())
        assertEquals(255, envelope.nextAttenuation())
        assertEquals(127, envelope.nextAttenuation())
    }

    @Test
    fun envelopeTimeComesFromChipClockNotHostRate() {
        val at44100 = configuredEnvelope(44_100)
        val at48000 = configuredEnvelope(48_000)
        val at96000 = configuredEnvelope(96_000)

        var i = 0
        while (i < 44_100) {
            at44100.nextAttenuation()
            i++
        }
        i = 0
        while (i < 48_000) {
            at48000.nextAttenuation()
            i++
        }
        i = 0
        while (i < 96_000) {
            at96000.nextAttenuation()
            i++
        }

        assertEquals(at48000.stage, at44100.stage)
        assertEquals(at48000.stage, at96000.stage)
        assertTrue(abs(at48000.attenuation - at44100.attenuation) <= 8)
        assertTrue(abs(at48000.attenuation - at96000.attenuation) <= 8)
    }

    @Test
    fun envelopeRateBoundariesAndRetriggerAreStable() {
        val heldAttack = OpnRateEnvelope()
        heldAttack.setSampleRate(18_518)
        heldAttack.attackRate = 0
        heldAttack.noteOn()
        repeat(64) { heldAttack.nextAttenuation() }
        assertEquals(OpnRateEnvelope.ATTACK, heldAttack.stage)
        assertEquals(OpnRateEnvelope.MAX_ATTENUATION, heldAttack.attenuation)

        val heldDecay = OpnRateEnvelope()
        heldDecay.setSampleRate(18_518)
        heldDecay.attackRate = 31
        heldDecay.decayRate = 0
        heldDecay.sustainLevel = 4
        heldDecay.noteOn()
        repeat(64) { heldDecay.nextAttenuation() }
        assertEquals(OpnRateEnvelope.DECAY, heldDecay.stage)
        assertEquals(0, heldDecay.attenuation)

        val instantAttack = OpnRateEnvelope()
        instantAttack.attackRate = 31
        instantAttack.sustainLevel = 15
        instantAttack.noteOn()
        assertEquals(OpnRateEnvelope.DECAY, instantAttack.stage)
        assertEquals(0, instantAttack.attenuation)

        val heldSustain = OpnRateEnvelope()
        heldSustain.setSampleRate(18_518)
        heldSustain.attackRate = 31
        heldSustain.sustainRate = 0
        heldSustain.sustainLevel = 0
        heldSustain.noteOn()
        repeat(64) { heldSustain.nextAttenuation() }
        assertEquals(OpnRateEnvelope.SUSTAIN, heldSustain.stage)
        assertEquals(0, heldSustain.attenuation)

        val retriggered = configuredEnvelope(18_518)
        repeat(32) { retriggered.nextAttenuation() }
        val attenuationBeforeRetrigger = retriggered.attenuation
        retriggered.noteOn(retrigger = true)
        assertEquals(attenuationBeforeRetrigger, retriggered.attenuation)
        retriggered.noteOff()
        assertEquals(OpnRateEnvelope.RELEASE, retriggered.stage)

        assertEquals(992, OpnRateEnvelope.sustainAttenuation(15))
        assertTrue(OpnLogTables.output(256, 0, 0) > 0)
        assertEquals(0, OpnLogTables.output(256, 0, 127))
    }

    @Test
    fun oversamplingHoldsEnvelopeAcrossBothSubsamples() {
        val operator = OperatorSpec(
            mul = 1,
            detune = 0,
            tl = 0,
            ar = 20,
            dr = 14,
            sr = 7,
            sl = 4,
            rr = 8,
            egMode = EgMode.OPN_RATE
        )
        val patch = FmPatch(algorithm = 7, feedback = 0, op0 = operator, op1 = operator, op2 = operator, op3 = operator)
        val normal = Fm4OpVoice(48_000)
        val oversampled = Fm4OpVoice(48_000)
        oversampled.enableOversampling = true
        normal.applyPatch(patch)
        oversampled.applyPatch(patch)
        normal.noteOn(69)
        oversampled.noteOn(69)
        val normalSample = FloatArray(1)
        val oversampledSample = FloatArray(1)
        var frame = 0
        while (frame < 12_000) {
            if (frame == 6_000) {
                normal.noteOff()
                oversampled.noteOff()
            }
            normalSample[0] = 0f
            oversampledSample[0] = 0f
            normal.render(normalSample, 1, 48_000, 1f)
            oversampled.render(oversampledSample, 1, 48_000, 1f)
            var op = 0
            while (op < 4) {
                assertEquals(
                    normal.operatorEnvelopeSnapshot(op),
                    oversampled.operatorEnvelopeSnapshot(op),
                    "frame=$frame op=$op"
                )
                op++
            }
            frame++
        }
    }

    @Test
    fun decodedPmdPatchesUseOneOpnEnvelopeCore() {
        val patches = arrayOf(LlsPatches.At54, LlsPatches.At74, LlsPatches.At99, LlsPatches.At181)
        var patchIndex = 0
        while (patchIndex < patches.size) {
            val patch = patches[patchIndex]
            val specs = arrayOf(patch.op0, patch.op1, patch.op2, patch.op3)
            var op = 0
            while (op < specs.size) {
                assertEquals(EgMode.OPN_RATE, specs[op].egMode)
                op++
            }
            patchIndex++
        }
    }

    @Test
    fun sixChannelFortissimoUsesWideMixBus() {
        val synth = OpnaLikeSynthesizer(48_000)
        var channel = 0
        while (channel < 6) {
            synth.fm[channel].applyPatch(LlsPatches.At54)
            synth.fm[channel].noteOn(72 + channel)
            channel++
        }
        val buffer = FloatArray(4096)
        synth.render(buffer, buffer.size)
        var i = 0
        while (i < buffer.size) {
            assertTrue(buffer[i].isFinite())
            assertTrue(buffer[i] in -1f..1f)
            i++
        }
        assertTrue(synth.preClampPeak > 0f)
        assertTrue(synth.preClampPeak < 8f)
    }

    @Test
    fun everyAlgorithmAndFeedbackLevelProducesFiniteAudio() {
        var algorithm = 0
        while (algorithm < 8) {
            var feedback = 0
            while (feedback < 8) {
                val op = OperatorSpec(
                    mul = 1, detune = 0, tl = 18,
                    ar = 31, dr = 12, sr = 0, sl = 3, rr = 8,
                    egMode = EgMode.OPN_RATE
                )
                val patch = FmPatch(algorithm, feedback, op, op, op, op, totalLevel = 0.25f)
                val synth = OpnaLikeSynthesizer(48_000)
                synth.fm[0].applyPatch(patch)
                synth.fm[0].noteOn(69)
                val buffer = FloatArray(512)
                synth.render(buffer, buffer.size)
                var i = 0
                while (i < buffer.size) {
                    assertTrue(buffer[i].isFinite(), "algorithm=$algorithm feedback=$feedback sample=$i")
                    i++
                }
                feedback++
            }
            algorithm++
        }
    }

    private fun configuredEnvelope(sampleRate: Int): OpnRateEnvelope {
        val envelope = OpnRateEnvelope()
        envelope.setSampleRate(sampleRate)
        envelope.attackRate = 20
        envelope.decayRate = 12
        envelope.sustainRate = 5
        envelope.sustainLevel = 4
        envelope.releaseRate = 8
        envelope.setKeyScale(4, 1038)
        envelope.noteOn()
        return envelope
    }
}
