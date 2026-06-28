package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaSubChunkSchedulingTest {

    private fun rms(buffer: FloatArray, start: Int, end: Int): Float {
        if (end <= start) return 0f
        var sum = 0.0
        var i = start
        while (i < end) {
            val v = buffer[i].toDouble()
            sum += v * v
            i++
        }
        return kotlin.math.sqrt(sum / (end - start)).toFloat()
    }

    @Test
    fun fmNoteOnAtMidChunkProducesSoundFromThatOffset() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val chunkSize = 1024
        val noteOnSample = 500L

        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunLead1)

        val seq = OpnaSequencer(sampleRate, bpm = 120)
        seq.noteFmRaw(
            channel = 0,
            midi = 69,
            startSample = noteOnSample,
            durationSamples = 4096L,
            velocity = 0.7f,
            attack = 0.005f,
            decay = 0.05f,
            sustain = 0.8f,
            release = 0.05f
        )

        val buffer = FloatArray(chunkSize)
        synth.render(buffer, chunkSize, seq, currentSampleOffset = 0L)

        val rmsBefore = rms(buffer, 0, noteOnSample.toInt())
        val rmsAfter = rms(buffer, noteOnSample.toInt(), chunkSize)

        assertTrue(
            rmsAfter > 0.01f,
            "Note should be audible from sample $noteOnSample to end of chunk: rms=$rmsAfter"
        )
        assertTrue(
            rmsAfter > rmsBefore * 4f,
            "RMS after noteOn ($rmsAfter) should be at least 4x RMS before noteOn ($rmsBefore). " +
            "If rmsBefore is comparable, the noteOn fired at sample 0 instead of sample $noteOnSample " +
            "(sub-chunk scheduling bug)."
        )
    }

    @Test
    fun drumTriggerAtMidChunkFiresAtThatOffset() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val chunkSize = 1024
        val kickSample = 700L

        val synth = OpnaLikeSynthesizer(sampleRate)

        val seq = OpnaSequencer(sampleRate, bpm = 120)
        seq.noteDrumRaw(ProceduralDrums.DrumKind.KICK, kickSample, 0.7f)

        val buffer = FloatArray(chunkSize)
        synth.render(buffer, chunkSize, seq, currentSampleOffset = 0L)

        val rmsBefore = rms(buffer, 0, kickSample.toInt())
        val rmsAfter = rms(buffer, kickSample.toInt(), chunkSize)

        assertTrue(
            rmsAfter > 0.005f,
            "Kick should be audible from sample $kickSample: rms=$rmsAfter"
        )
        assertTrue(
            rmsAfter > rmsBefore * 3f,
            "RMS after kick ($rmsAfter) should exceed RMS before ($rmsBefore). " +
            "If rmsBefore is comparable, the kick fired at sample 0 instead of sample $kickSample."
        )
    }

    @Test
    fun noteOnAtChunkBoundaryStillWorks() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val chunkSize = 1024

        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunLead1)

        val seq = OpnaSequencer(sampleRate, bpm = 120)
        seq.noteFmRaw(
            channel = 0,
            midi = 69,
            startSample = 0L,
            durationSamples = 8192L,
            velocity = 0.7f,
            attack = 0.005f,
            decay = 0.05f,
            sustain = 0.8f,
            release = 0.05f
        )

        val buffer = FloatArray(chunkSize)
        synth.render(buffer, chunkSize, seq, currentSampleOffset = 0L)

        val r = rms(buffer, 0, chunkSize)
        assertTrue(r > 0.01f, "NoteOn at sample 0 should produce audible sound: rms=$r")
    }

    @Test
    fun secondChunkPicksUpActiveNotes() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val chunkSize = 1024
        val noteOnSample = 200L

        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunLead1)

        val seq = OpnaSequencer(sampleRate, bpm = 120)
        seq.noteFmRaw(
            channel = 0,
            midi = 69,
            startSample = noteOnSample,
            durationSamples = 5000L,
            velocity = 0.7f,
            attack = 0.005f,
            decay = 0.05f,
            sustain = 0.8f,
            release = 0.05f
        )

        val chunk0 = FloatArray(chunkSize)
        synth.render(chunk0, chunkSize, seq, currentSampleOffset = 0L)

        val chunk1 = FloatArray(chunkSize)
        synth.render(chunk1, chunkSize, seq, currentSampleOffset = chunkSize.toLong())

        val r1 = rms(chunk1, 0, chunkSize)
        assertTrue(
            r1 > 0.01f,
            "Chunk 1 (samples ${chunkSize}..${chunkSize * 2}) should still produce audio: rms=$r1. " +
            "The note started in chunk 0 and is sustained; sub-chunk scheduling must keep it active."
        )
    }

    @Test
    fun noteOffInSecondChunkStopsSound() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val chunkSize = 1024
        val noteOnSample = 100L
        val noteOffSample = 1500L

        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunLead1)

        val seq = OpnaSequencer(sampleRate, bpm = 120)
        seq.noteFmRaw(
            channel = 0,
            midi = 69,
            startSample = noteOnSample,
            durationSamples = (noteOffSample - noteOnSample),
            velocity = 0.7f,
            attack = 0.005f,
            decay = 0.05f,
            sustain = 0.8f,
            release = 0.02f
        )

        val chunk0 = FloatArray(chunkSize)
        synth.render(chunk0, chunkSize, seq, currentSampleOffset = 0L)

        val chunk1 = FloatArray(chunkSize)
        synth.render(chunk1, chunkSize, seq, currentSampleOffset = chunkSize.toLong())

        val r0End = rms(chunk1, 0, (noteOffSample - chunkSize).toInt())
        val r1After = rms(chunk1, (noteOffSample - chunkSize).toInt(), chunkSize)
        val r1 = abs(r0End - r1After)
        assertTrue(
            r1 > 0.001f || r1After < 0.01f,
            "NoteOff at sample $noteOffSample should reduce energy after that point. " +
            "r0End=$r0End, r1After=$r1After. Sub-chunk scheduling must apply noteOff at exact offset."
        )
    }

    @Test
    fun noEventsProducesSilentBuffer() {
        val sampleRate = AudioLaws.SAMPLE_RATE
        val chunkSize = 1024

        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.fm[0].applyPatch(Patches.ZunLead1)

        val seq = OpnaSequencer(sampleRate, bpm = 120)

        val buffer = FloatArray(chunkSize)
        synth.render(buffer, chunkSize, seq, currentSampleOffset = 0L)

        val r = rms(buffer, 0, chunkSize)
        assertTrue(r < 0.001f, "Empty sequencer should produce silence: rms=$r")
    }
}
