package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class OpnaPolyphonyTest {

    @Test
    fun logicalPmdPartsOwnIndependentPreallocatedState() {
        val performance = PmdPerformanceState(48_000)

        performance.configureFmLfo(0, 0, 0, 1, 3, 4)
        performance.setFmLfoSwitch(0, 0, 3)
        performance.setFmLfoTlMask(0, 0, 1)
        performance.configureFmLfo(1, 1, 0, 1, 7, 4)
        performance.setFmLfoSwitch(1, 1, 3)
        performance.setFmLfoTlMask(1, 1, 8)

        performance.configureSsgEnvelope(
            0, PmdPerformanceLaws.ENVELOPE_EXTENDED, 31, 31, 0, 15, 8, 0
        )
        performance.setSsgEnvelopeClockMode(0, PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED)
        performance.noteOnSsg(0)
        performance.noteOnSsg(1)

        performance.setFm3LfoTlMask(0, 0, 3)
        performance.setFm3LfoTlMask(1, 1, 12)
        performance.noteOnFm(0)
        performance.noteOnFm(1)
        performance.prepare(1)

        val fm0 = requireNotNull(performance.fmFrame(0))
        val fm1 = requireNotNull(performance.fmFrame(1))
        assertNotSame(fm0, fm1)
        assertEquals(1, fm0.tlMask1)
        assertEquals(8, fm1.tlMask2)
        assertEquals(0, fm0.tlMask2)
        assertEquals(0, fm1.tlMask1)

        assertNotSame(performance.ssgFrame(0), performance.ssgFrame(1))
        assertEquals(0, performance.ssgEnvelopeLevelOffsetSnapshot(1))
        assertEquals(true, performance.noteOffSsg(0))
        assertEquals(false, performance.noteOffSsg(1))
        assertEquals(3, performance.fm3LfoTlMaskSnapshot(0, 0))
        assertEquals(12, performance.fm3LfoTlMaskSnapshot(1, 1))
    }

    @Test
    fun overlappingPolyVoicesKeepTheirAuthoredLogicalPart() {
        val synth = OpnaLikeSynthesizer(48_000)
        val sequencer = OpnaSequencer(48_000, 120f)
        sequencer.noteFmPolyControlledRaw(4, 60, 0L, 1_000L, 1f, Patches.ZunLead1, 0, 0, 0, 0, 0)
        sequencer.noteFmPolyControlledRaw(4, 64, 0L, 1_000L, 1f, Patches.ZunLead1, 0, 0, 0, 0, 0)
        sequencer.sortEvents()

        synth.render(FloatArray(1), 1, sequencer, 0L)

        assertEquals(4, synth.logicalFmPartForVoiceSnapshot(4))
        assertEquals(4, synth.logicalFmPartForVoiceSnapshot(6))
        assertEquals(2, synth.activeFmVoiceCount())
    }

    @Test
    fun fullResetPreservesOutputSettingsAndClearsMeasurementHistory() {
        val synth = OpnaLikeSynthesizer(48_000)
        synth.outputProfile = OpnaOutputProfile.PC9801_86_REFERENCE
        synth.enableOutputFilter = false
        synth.enableStereoResonator = true
        synth.noteOnFm(0, 60, Patches.ZunLead1)
        synth.render(FloatArray(1_024), 1_024)
        assertTrue(synth.preClampPeak > 0f)

        synth.reset()

        assertEquals(OpnaOutputProfile.PC9801_86_REFERENCE, synth.outputProfile)
        assertEquals(false, synth.enableOutputFilter)
        assertEquals(true, synth.enableStereoResonator)
        assertEquals(0f, synth.preClampPeak)
        assertEquals(0, synth.preClampKneeCrossings)
    }

    private fun rms(buffer: FloatArray): Float {
        var sumSq = 0.0
        var i = 0
        while (i < buffer.size) {
            val v = buffer[i].toDouble()
            sumSq += v * v
            i++
        }
        return sqrt(sumSq / buffer.size).toFloat()
    }

    private fun countZeroCrossings(buffer: FloatArray): Int {
        var crossings = 0
        var i = 1
        while (i < buffer.size) {
            if ((buffer[i - 1] < 0f && buffer[i] >= 0f) || (buffer[i - 1] > 0f && buffer[i] <= 0f)) {
                crossings++
            }
            i++
        }
        return crossings
    }

    @Test
    fun overlappingEventsRetriggerSingleVoice() {
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)
        val seq = OpnaSequencer(sampleRate, 120f)

        synth.fm[0].applyPatch(Patches.ZunLead1)

        // Queue overlapping event lifetimes on one physical channel. Each ON retriggers
        // the same voice; this is not four-voice polyphony.
        seq.noteFmRaw(0, 60, 0L, 44100L)
        seq.noteFmRaw(0, 64, 4410L, 44100L)
        seq.noteFmRaw(0, 67, 8820L, 44100L)
        seq.noteFmRaw(0, 72, 13230L, 44100L)

        seq.sortEvents()

        val buffer = FloatArray(sampleRate * 3 / 2)
        synth.render(buffer, buffer.size, seq, 0L)

        val r = rms(buffer)
        assertTrue(r > 0.05f, "RMS level ($r) should indicate active synthesis")

        val crossings = countZeroCrossings(buffer)
        assertTrue(crossings > 500, "Zero crossings ($crossings) should indicate complex waveform")
    }

    @Test
    fun olderNoteOffCannotStopNewerRetriggeredNote() {
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)
        val seq = OpnaSequencer(sampleRate, 120f)

        synth.fm[0].applyPatch(Patches.ZunLead1)

        seq.noteFmRaw(0, 60, 0L, 8820L)
        seq.noteFmRaw(0, 64, 4410L, 22050L)

        seq.sortEvents()

        val buffer = FloatArray(17640)
        synth.render(buffer, buffer.size, seq, 0L)

        // Copy range from 10000 to 17640
        val secondHalf = FloatArray(7640)
        var i = 0
        while (i < 7640) {
            secondHalf[i] = buffer[10000 + i]
            i++
        }

        val r2 = rms(secondHalf)
        assertTrue(r2 > 0.05f, "Second half RMS ($r2) should indicate E4 is still sounding (stale NoteOff ignored)")
    }
}
