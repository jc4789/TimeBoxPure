package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaFmCoreTest {

    private fun peakAbs(buffer: FloatArray): Float {
        var peak = 0f
        var i = 0
        while (i < buffer.size) {
            val a = abs(buffer[i])
            if (a > peak) peak = a
            i++
        }
        return peak
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

    private fun hasNaNOrInf(buffer: FloatArray): Boolean {
        var i = 0
        while (i < buffer.size) {
            val v = buffer[i]
            if (v.isNaN() || v.isInfinite()) return true
            i++
        }
        return false
    }

    private fun countZeroCrossings(buffer: FloatArray, threshold: Float = 0.01f): Int {
        var crossings = 0
        var lastAbove = false
        var primed = false
        var i = 0
        while (i < buffer.size) {
            val v = buffer[i]
            if (abs(v) > threshold) {
                val above = v > 0f
                if (primed && above != lastAbove) {
                    crossings++
                }
                lastAbove = above
                primed = true
            }
            i++
        }
        return crossings
    }

    private fun measuredHz(buffer: FloatArray, sampleRate: Int): Double {
        val durationSec = buffer.size.toDouble() / sampleRate
        val crossings = countZeroCrossings(buffer)
        return crossings / durationSec / 2.0
    }

    private fun renderPureSineA4(sampleRate: Int): FloatArray {
        val synth = OpnaLikeSynthesizer(sampleRate)
        val pureSinePatch = FmPatch(
            algorithm = 0,
            feedback = 0,
            op0 = OperatorSpec(mul = 1, detune = 0, tl = 127, attack = 0.001f, decay = 0.01f, sustain = 1.0f, release = 0.01f),
            op1 = OperatorSpec(mul = 1, detune = 0, tl = 127, attack = 0.001f, decay = 0.01f, sustain = 1.0f, release = 0.01f),
            op2 = OperatorSpec(mul = 1, detune = 0, tl = 127, attack = 0.001f, decay = 0.01f, sustain = 1.0f, release = 0.01f),
            op3 = OperatorSpec(mul = 1, detune = 0, tl = 0,   attack = 0.001f, decay = 0.01f, sustain = 1.0f, release = 0.01f),
            totalLevel = 1.0f,
            pms = 0, pan = 0
        )
        synth.fm[0].applyPatch(pureSinePatch)
        synth.fm[0].noteOn(69, 0.05f, 0.05f, 0.7f, 0.1f)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate)
        return buffer
    }

    private fun renderPatchOneSecond(patch: FmPatch, midi: Int = 69): FloatArray {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(patch)
        synth.fm[0].noteGain = 0.7f
        synth.fm[0].noteOn(midi)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate)
        return buffer
    }

    @Test
    fun noNaNOrInfInAnyPatch() {
        val patches = listOf(
            Patches.ZunLead1 to 69,
            Patches.ZunBell1 to 69,
            Patches.ZunBass1 to 45,
            Patches.ZunPad1 to 57,
            Patches.ZunChime1 to 72
        )
        for ((patch, midi) in patches) {
            val buffer = renderPatchOneSecond(patch, midi)
            assertTrue(!hasNaNOrInf(buffer), "Patch produced NaN or Inf: algorithm=${patch.algorithm}")
        }
    }

    @Test
    fun outputMaxAbsWithinBounds() {
        val patches = listOf(
            Patches.ZunLead1 to 69,
            Patches.ZunBell1 to 69,
            Patches.ZunBass1 to 45,
            Patches.ZunPad1 to 57,
            Patches.ZunChime1 to 72
        )
        for ((patch, midi) in patches) {
            val buffer = renderPatchOneSecond(patch, midi)
            val peak = peakAbs(buffer)
            assertTrue(peak <= 1.0f, "Patch output exceeded [-1,1]: peak=$peak, algorithm=${patch.algorithm}")
        }
    }

    @Test
    fun a4Produces440HzAt48000() {
        val buffer = renderPureSineA4(48000)
        val measured = measuredHz(buffer, 48000)
        assertTrue(
            abs(measured - 440.0) < 5.0,
            "A4 at 48kHz: measured=$measured Hz, expected ~440 Hz"
        )
    }

    @Test
    fun pitchIndependentOfSampleRate() {
        val buf44100 = renderPureSineA4(44100)
        val buf48000 = renderPureSineA4(48000)
        val hz44100 = measuredHz(buf44100, 44100)
        val hz48000 = measuredHz(buf48000, 48000)
        val diff = abs(hz44100 - hz48000)
        assertTrue(
            diff < 2.0,
            "Pitch differs between 44100 and 48000: 44100=$hz44100 Hz, 48000=$hz48000 Hz, diff=$diff Hz"
        )
    }

    @Test
    fun noteOffCausesDecay() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        val seq = OpnaSequencer(sampleRate, 120f)
        synth.fm[0].applyPatch(Patches.ZunLead1)
        seq.noteFmRaw(0, 69, 0L, sampleRate.toLong() / 4, 0.7f, null, null, null, null)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate, seq, 0L)

        val firstQuarter = buffer.copyOfRange(0, sampleRate / 4)
        val lastQuarter = buffer.copyOfRange(sampleRate * 3 / 4, sampleRate)
        val rmsFirst = rms(firstQuarter)
        val rmsLast = rms(lastQuarter)
        assertTrue(
            rmsLast < rmsFirst * 0.5f,
            "NoteOff did not cause decay: rmsFirst=$rmsFirst, rmsLast=$rmsLast"
        )
    }

    @Test
    fun algorithm7Headroom() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunChime1)
        synth.fm[0].noteGain = 0.7f
        synth.fm[0].noteOn(72)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate)
        val peak = synth.preClampPeak
        assertTrue(
            peak < 20.0f,
            "Algorithm 7 pre-clamp peak=$peak is too high. Carrier sum normalization may be broken."
        )
    }

    @Test
    fun preClampPeakIsMeasured() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunLead1)
        synth.fm[0].noteGain = 0.7f
        synth.fm[0].noteOn(69)
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate)
        assertTrue(
            synth.preClampPeak > 0f,
            "preClampPeak should be > 0 after rendering, got ${synth.preClampPeak}"
        )
    }

    @Test
    fun legacyAdsrInputChangesOnlyTheAudibleCarrierRateEnvelope() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val native = Fm4OpVoice(sampleRate)
        val imported = Fm4OpVoice(sampleRate)
        val patch = Patches.ZunLead1
        native.applyPatch(patch)
        imported.applyPatch(patch)
        native.noteOn(69)
        imported.noteOn(69, 0.200f, 0.100f, 0.60f, 0.200f)

        native.render(FloatArray(2_400), 2_400, sampleRate, 1f)
        imported.render(FloatArray(2_400), 2_400, sampleRate, 1f)

        assertTrue(
            native.operatorEnvelopeSnapshot(3) != imported.operatorEnvelopeSnapshot(3),
            "Legacy ADSR import must alter the audible carrier OpnRateEnvelope"
        )
        assertTrue(
            native.operatorEnvelopeSnapshot(0) == imported.operatorEnvelopeSnapshot(0),
            "Legacy ADSR import must not replace modulator envelope parameters"
        )
    }

    @Test
    fun ssgDutyCycleAppliedAtEventTime() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        val seq = OpnaSequencer(sampleRate, 120f)

        // Schedule two SSG notes with different duty cycles
        seq.noteSsgRaw(0, 69, 0L, sampleRate.toLong() / 2, 0.7f, 0.25f)
        seq.noteSsgRaw(0, 72, sampleRate.toLong() / 2, sampleRate.toLong() / 2, 0.7f, 0.5f)

        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate, seq, 0L)

        // Verify the SSG voice duty was updated during playback
        // The last note should have set duty to 0.5f
        assertTrue(
            abs(synth.ssg[0].duty - 0.5f) < 0.001f,
            "SSG duty should be 0.5f after playback, got ${synth.ssg[0].duty}"
        )
    }

    @Test
    fun gateRatioCausesEarlyNoteOff() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val synth = OpnaLikeSynthesizer(sampleRate)
        val seq = OpnaSequencer(sampleRate, 120f)

        // Schedule a note with short duration (simulating gate ratio effect)
        val noteDurationSamples = sampleRate / 4
        seq.noteFmRaw(0, 69, 0L, noteDurationSamples.toLong(), 0.7f, null, null, null, null)

        val buffer = FloatArray(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunLead1)
        synth.render(buffer, sampleRate, seq, 0L)

        // Verify the note is in release phase after the duration
        // Compare RMS of first half (note playing) vs second half (release/silence)
        val firstHalf = buffer.copyOfRange(0, sampleRate / 2)
        val secondHalf = buffer.copyOfRange(sampleRate / 2, sampleRate)
        val rmsFirst = rms(firstHalf)
        val rmsSecond = rms(secondHalf)

        // The second half should be quieter than the first half
        assertTrue(rmsSecond < rmsFirst, "Note should be decaying after noteOff: first=$rmsFirst, second=$rmsSecond")
    }
}
