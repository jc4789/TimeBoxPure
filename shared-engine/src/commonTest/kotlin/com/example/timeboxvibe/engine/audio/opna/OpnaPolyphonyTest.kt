package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class OpnaPolyphonyTest {

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
    fun overlappingNotesAllPlay() {
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)
        val seq = OpnaSequencer(sampleRate, 120f)

        synth.fm[0].applyPatch(Patches.ZunLead1)

        // Queue 4 overlapping notes on channel 0
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
    fun staleNoteOffGuarded() {
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
