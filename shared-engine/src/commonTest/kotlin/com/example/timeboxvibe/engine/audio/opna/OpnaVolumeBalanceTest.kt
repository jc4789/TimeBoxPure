package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.SoundMelodies
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Volume balance test: ensures each arrangement has audible lead, harmony, bass, and percussion.
 * Catches the "bass is 5.6x quieter than lead" regression that caused "no bass" in
 * senbonzakura and "thin mix" in old synth-bad-apple.
 *
 * Renders each arrangement and asserts the overall RMS is in an audible range.
 * For per-lane isolation, use SoundPreviewPlayer.kt's lane-gain constants in
 * OpnaAudioConstants — those should be in [0.3, 0.9] for all four lanes.
 */
class OpnaVolumeBalanceTest {

    private fun rms(buffer: FloatArray): Float {
        var sumSq = 0.0
        var i = 0
        while (i < buffer.size) {
            val v = buffer[i].toDouble()
            sumSq += v * v
            i++
        }
        return kotlin.math.sqrt(sumSq / buffer.size).toFloat()
    }

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

    @Test
    fun laneGainsAreBalanced() {
        assertTrue(
            OpnaAudioConstants.LANE_GAIN_BASS in 0.3f..0.9f,
            "LANE_GAIN_BASS=${OpnaAudioConstants.LANE_GAIN_BASS} is too low. " +
            "Bass becomes inaudible below 0.3. Should be in [0.3, 0.9]."
        )
        assertTrue(
            OpnaAudioConstants.LANE_GAIN_LEAD in 0.3f..0.9f,
            "LANE_GAIN_LEAD=${OpnaAudioConstants.LANE_GAIN_LEAD} should be in [0.3, 0.9]."
        )
        assertTrue(
            OpnaAudioConstants.LANE_GAIN_HARMONY in 0.3f..0.9f,
            "LANE_GAIN_HARMONY=${OpnaAudioConstants.LANE_GAIN_HARMONY} should be in [0.3, 0.9]."
        )
        assertTrue(
            OpnaAudioConstants.LANE_GAIN_PERCUSSION in 0.3f..0.9f,
            "LANE_GAIN_PERCUSSION=${OpnaAudioConstants.LANE_GAIN_PERCUSSION} should be in [0.3, 0.9]."
        )
    }

    @Test
    fun laneGainRatioBassToLeadIsNotTooSmall() {
        val ratio = OpnaAudioConstants.LANE_GAIN_BASS / OpnaAudioConstants.LANE_GAIN_LEAD
        assertTrue(
            ratio >= 0.4f,
            "Bass/lead gain ratio is $ratio (should be >= 0.4). " +
            "If too low, bass is inaudible relative to lead."
        )
    }

    @Test
    fun oldBadAppleArrangementIsAudible() {
        val arr = SoundMelodies.getArrangement("synth-bad-apple", 1f)!!
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arr.tempoBpm)
        val leadPatch = Patches.ZunLead1
        synth.fm[0].applyPatch(leadPatch)
        for (note in arr.lead.notes) {
            if (note.freq <= 10f) continue
            val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).toInt()
            sequencer.noteFmRaw(0, midi, (note.startMs * sampleRate / 1000L),
                (note.durationMs * sampleRate / 1000L),
                note.volume * OpnaAudioConstants.LANE_GAIN_LEAD, null, null, null, null)
        }
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate, sequencer, 0L)
        val r = rms(buffer)
        val p = peakAbs(buffer)
        assertTrue(r > 0.01f, "synth-bad-apple RMS=$r is too low")
        assertTrue(p > 0.05f, "synth-bad-apple peak=$p is too low")
    }

    @Test
    fun senbonzakuraArrangementIsAudible() {
        val arr = SoundMelodies.getArrangement("synth-senbonzakura", 1f)!!
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arr.tempoBpm)
        synth.fm[0].applyPatch(Patches.ZunBell1)
        for (note in arr.lead.notes) {
            if (note.freq <= 10f) continue
            val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).toInt()
            sequencer.noteFmRaw(0, midi, (note.startMs * sampleRate / 1000L),
                (note.durationMs * sampleRate / 1000L),
                note.volume * OpnaAudioConstants.LANE_GAIN_LEAD, null, null, null, null)
        }
        val buffer = FloatArray(sampleRate)
        synth.render(buffer, sampleRate, sequencer, 0L)
        val r = rms(buffer)
        val p = peakAbs(buffer)
        assertTrue(r > 0.01f, "synth-senbonzakura RMS=$r is too low")
        assertTrue(p > 0.05f, "synth-senbonzakura peak=$p is too low")
    }

    @Test
    fun lotusLandStoryArrangementIsAudible() {
        val arr = SoundMelodies.getArrangement("synth-bad-apple-LotusLandStory", 1f)!!
        val sampleRate = 44100
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arr.tempoBpm)
        synth.fm[0].applyPatch(Patches.ZunLead1)
        for (note in arr.lead.notes) {
            if (note.freq <= 10f) continue
            val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).toInt()
            sequencer.noteFmRaw(0, midi, (note.startMs * sampleRate / 1000L),
                (note.durationMs * sampleRate / 1000L),
                note.volume * OpnaAudioConstants.LANE_GAIN_LEAD, null, null, null, null)
        }
        val chunk = FloatArray(1024)
        var pos = 0L
        var sumSq = 0.0
        var peak = 0f
        var totalSamples = 0
        val chunksToRender = 5 * 44100 / 1024
        var i = 0
        while (i < chunksToRender) {
            synth.render(chunk, 1024, sequencer, pos)
            pos += 1024
            var j = 0
            while (j < 1024) {
                val v = chunk[j]
                val a = abs(v)
                if (a > peak) peak = a
                sumSq += v * v
                j++
            }
            totalSamples += 1024
            i++
        }
        val rms = kotlin.math.sqrt(sumSq / totalSamples).toFloat()
        assertTrue(rms > 0.01f, "LotusLandStory RMS=$rms is too low (5-second render)")
        assertTrue(peak > 0.05f, "LotusLandStory peak=$peak is too low")
    }
}
