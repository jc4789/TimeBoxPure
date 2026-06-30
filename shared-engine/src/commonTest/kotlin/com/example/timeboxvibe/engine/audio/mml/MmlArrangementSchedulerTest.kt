package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.Lane
import com.example.timeboxvibe.engine.audio.opna.OpnaAudioConstants
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.SequencerEvent
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MmlArrangementSchedulerTest {
    @Test
    fun productionScheduleAppliesMmlGainAndArticulationPolicy() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)

        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)

        val firstFm = findFirstEvent(sequencer, SequencerEvent.FM_ON)
        val expectedLeadGain = (13f / 15f) * OpnaAudioConstants.LANE_GAIN_LEAD * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedLeadGain, firstFm.velocity, 0.0001f)
        assertEquals(MmlArrangementScheduler.FM_ATTACK_SECONDS, firstFm.attack)
        assertEquals(MmlArrangementScheduler.FM_RELEASE_SECONDS, firstFm.release)

        val firstSsg = findFirstEvent(sequencer, SequencerEvent.SSG_ON)
        val expectedSsgGain = (8f / 15f) * OpnaAudioConstants.LANE_GAIN_HARMONY * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedSsgGain, firstSsg.velocity, 0.0001f)
        assertEquals(MmlArrangementScheduler.SSG_ATTACK_SECONDS, firstSsg.attack)
        assertEquals(MmlArrangementScheduler.SSG_RELEASE_SECONDS, firstSsg.release)

        val firstDrum = findFirstEvent(sequencer, SequencerEvent.DRUM)
        val expectedDrumGain = (12f / 15f) * OpnaAudioConstants.LANE_GAIN_PERCUSSION * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedDrumGain, firstDrum.velocity, 0.0001f)
    }

    @Test
    fun completeDemoStaysBelowSoftClipKnee() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.enableOutputFilter = true
        var voiceIndex = 0
        while (voiceIndex < synth.fm.size) {
            synth.fm[voiceIndex].enableOversampling = true
            voiceIndex++
        }
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)

        val totalSamples = maximumEndMs(arrangement).toLong() * sampleRate / 1000L
        val buffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        var sampleOffset = 0L
        var maximumPreClipPeak = 0f
        var maximumOutputPeak = 0f
        var sumSquares = 0.0
        var renderedSamples = 0L
        while (sampleOffset < totalSamples) {
            val frames = minOf(buffer.size.toLong(), totalSamples - sampleOffset).toInt()
            synth.render(buffer, frames, sequencer, sampleOffset)
            if (synth.preClampPeak > maximumPreClipPeak) maximumPreClipPeak = synth.preClampPeak
            var i = 0
            while (i < frames) {
                val sample = buffer[i]
                val magnitude = abs(sample)
                if (magnitude > maximumOutputPeak) maximumOutputPeak = magnitude
                sumSquares += sample * sample
                i++
            }
            renderedSamples += frames
            sampleOffset += frames
        }

        val rms = sqrt(sumSquares / renderedSamples).toFloat()
        assertTrue(maximumPreClipPeak in 0.10f..0.70f, "MML pre-clip peak=$maximumPreClipPeak")
        assertTrue(maximumOutputPeak <= 0.70f, "MML output peak=$maximumOutputPeak entered soft clipping")
        assertTrue(rms > 0.05f, "MML mix became too quiet: rms=$rms")
    }

    private fun requireDemoArrangement(): ArrangementLanes {
        val result = MmlSongBank.senbonzakuraDemoResult
        assertTrue(result is MmlCompileResult.Success)
        return result.arrangement
    }

    private fun findFirstEvent(sequencer: OpnaSequencer, type: Int): SequencerEvent {
        var i = 0
        while (i < sequencer.eventCount) {
            val event = sequencer.events[i]
            if (event.type == type) return event
            i++
        }
        error("No sequencer event of type $type")
    }

    private fun maximumEndMs(arrangement: ArrangementLanes): Int {
        var maximum = maximumEndMs(arrangement.lead)
        maximum = maxOf(maximum, maximumEndMs(arrangement.harmony))
        maximum = maxOf(maximum, maximumEndMs(arrangement.bass))
        maximum = maxOf(maximum, maximumEndMs(arrangement.percussion))
        val auxiliary = arrangement.auxiliary
        if (auxiliary != null) maximum = maxOf(maximum, maximumEndMs(auxiliary))
        return maximum
    }

    private fun maximumEndMs(lane: Lane): Int {
        var maximum = 0
        var i = 0
        while (i < lane.notes.size) {
            val note = lane.notes[i]
            maximum = maxOf(maximum, note.startMs + note.durationMs)
            i++
        }
        return maximum
    }
}
