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
import kotlin.test.assertIs
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
        val expectedLeadGain = (12f / 15f) * OpnaAudioConstants.LANE_GAIN_LEAD * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedLeadGain, firstFm.velocity, 0.0001f)
        assertEquals(MmlArrangementScheduler.FM_ATTACK_SECONDS, firstFm.attack)
        assertEquals(MmlArrangementScheduler.FM_RELEASE_SECONDS, firstFm.release)

        val firstSsg = findFirstEvent(sequencer, SequencerEvent.SSG_ON)
        val expectedSsgGain = (5f / 15f) * OpnaAudioConstants.LANE_GAIN_HARMONY * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedSsgGain, firstSsg.velocity, 0.0001f)
        assertEquals(MmlArrangementScheduler.SSG_ATTACK_SECONDS, firstSsg.attack)
        assertEquals(MmlArrangementScheduler.SSG_RELEASE_SECONDS, firstSsg.release)

        val firstDrum = findFirstEvent(sequencer, SequencerEvent.DRUM)
        val expectedDrumGain = (11f / 15f) * OpnaAudioConstants.LANE_GAIN_PERCUSSION * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedDrumGain, firstDrum.velocity, 0.0001f)
    }

    @Test
    fun eachMmlLaneUsesOnePhysicalChannel() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)

        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)

        assertEquals(arrangement.lead.notes.size, countEvents(sequencer, SequencerEvent.FM_ON, 0))
        assertEquals(arrangement.harmony.notes.size, countEvents(sequencer, SequencerEvent.FM_ON, 1))
        assertEquals(arrangement.bass.notes.size, countEvents(sequencer, SequencerEvent.FM_ON, 2))
        assertEquals(arrangement.auxiliary?.notes?.size, countEvents(sequencer, SequencerEvent.SSG_ON, 0))
        assertEquals(0, countEvents(sequencer, SequencerEvent.SSG_ON, 1))
        assertEquals(0, countEvents(sequencer, SequencerEvent.SSG_ON, 2))
    }

    @Test
    fun fifthMelodicLaneUsesNextAvailableFmChannel() {
        val arrangement = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                """
                    #BPM 120
                    #BAR 4/4
                    A @54 o4 l1 c |
                    B @74 o4 l1 d |
                    C @99 o4 l1 e |
                    D @square o4 l1 f |
                    E @181 o3 l1 g |
                """.trimIndent()
            )
        ).arrangement
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)

        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)

        assertEquals(1, countEvents(sequencer, SequencerEvent.FM_ON, 3))
        assertEquals(1, countEvents(sequencer, SequencerEvent.SSG_ON, 0))
    }

    @Test
    fun releaseEndsAtCompiledNoteBoundary() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)

        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)

        val firstFmOn = findFirstEvent(sequencer, SequencerEvent.FM_ON)
        val firstFmOff = findEvent(sequencer, SequencerEvent.FM_OFF, firstFmOn.channel, firstFmOn.noteId)
        val firstFmNote = arrangement.lead.notes[0]
        val expectedFmGateMs = maxOf(0, firstFmNote.durationMs - MmlArrangementScheduler.FM_RELEASE_MILLISECONDS)
        assertEquals(
            firstFmOn.sampleTime + expectedFmGateMs.toLong() * sampleRate / 1000L,
            firstFmOff.sampleTime
        )

        val firstSsgOn = findFirstEvent(sequencer, SequencerEvent.SSG_ON)
        val firstSsgOff = findEvent(sequencer, SequencerEvent.SSG_OFF, firstSsgOn.channel, firstSsgOn.noteId)
        val firstSsgNote = requireNotNull(arrangement.auxiliary).notes[0]
        val expectedSsgGateMs = maxOf(0, firstSsgNote.durationMs - MmlArrangementScheduler.SSG_RELEASE_MILLISECONDS)
        assertEquals(
            firstSsgOn.sampleTime + expectedSsgGateMs.toLong() * sampleRate / 1000L,
            firstSsgOff.sampleTime
        )
    }

    @Test
    fun restIsSilentAfterExpectedReleaseTail() {
        val arrangement = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                "#BPM 120\n#BAR 4/4\nA @54 v15 o4 l4 c r d2 |"
            )
        ).arrangement
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)

        val buffer = FloatArray(sampleRate * 3 / 2)
        synth.render(buffer, buffer.size, sequencer, 0L)

        val releaseTailRms = rms(buffer, sampleRate * 492 / 1000, sampleRate * 500 / 1000)
        val silentRestRms = rms(buffer, sampleRate * 520 / 1000, sampleRate * 980 / 1000)
        val nextNoteRms = rms(buffer, sampleRate * 1020 / 1000, sampleRate * 1200 / 1000)
        assertTrue(releaseTailRms > silentRestRms, "Release tail should decay before the rest: tail=$releaseTailRms rest=$silentRestRms")
        assertTrue(silentRestRms < 0.005f, "Rest should be silent after release: rms=$silentRestRms")
        assertTrue(nextNoteRms > 0.01f, "The note after the rest should retrigger: rms=$nextNoteRms")
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
        var kneeCrossings = 0L
        while (sampleOffset < totalSamples) {
            val frames = minOf(buffer.size.toLong(), totalSamples - sampleOffset).toInt()
            synth.render(buffer, frames, sequencer, sampleOffset)
            if (synth.preClampPeak > maximumPreClipPeak) maximumPreClipPeak = synth.preClampPeak
            kneeCrossings += synth.preClampKneeCrossings
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
        val kneeCrossingRatio = kneeCrossings.toDouble() / renderedSamples
        assertTrue(maximumPreClipPeak >= 0.10f, "MML pre-clip peak=$maximumPreClipPeak")
        assertTrue(kneeCrossingRatio < 0.001, "MML soft-clip crossing ratio=$kneeCrossingRatio")
        assertTrue(maximumOutputPeak <= 1.0f, "MML output peak=$maximumOutputPeak")
        assertTrue(rms > 0.025f, "MML mix became too quiet: rms=$rms")
    }

    @Test
    fun normalRhythmOverlapStaysBelowSoftClipKnee() {
        val synth = OpnaLikeSynthesizer(48000)
        val normalVelocity = OpnaAudioConstants.LANE_GAIN_PERCUSSION * MmlArrangementScheduler.MIX_GAIN
        synth.triggerDrum(0, normalVelocity)
        synth.triggerDrum(1, normalVelocity)
        synth.triggerDrum(2, normalVelocity)

        synth.render(FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK), OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)

        assertTrue(synth.preClampPeak < OpnaLikeSynthesizer.SOFT_CLIP_KNEE)
        assertEquals(0, synth.preClampKneeCrossings)
    }

    @Test
    fun sameDemoPositionHasStableHighBandEnergy() {
        val first = renderDemoPrefix()
        val second = renderDemoPrefix()
        var firstDifferenceEnergy = 0.0
        var secondDifferenceEnergy = 0.0
        var i = 1
        while (i < first.size) {
            val firstDifference = first[i] - first[i - 1]
            val secondDifference = second[i] - second[i - 1]
            firstDifferenceEnergy += firstDifference * firstDifference
            secondDifferenceEnergy += secondDifference * secondDifference
            i++
        }

        assertEquals(firstDifferenceEnergy, secondDifferenceEnergy, 0.0000001)
    }

    private fun renderDemoPrefix(): FloatArray {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)
        val buffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        synth.render(buffer, buffer.size, sequencer, 0L)
        return buffer
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

    private fun findEvent(sequencer: OpnaSequencer, type: Int, channel: Int, noteId: Int): SequencerEvent {
        var i = 0
        while (i < sequencer.eventCount) {
            val event = sequencer.events[i]
            if (event.type == type && event.channel == channel && event.noteId == noteId) return event
            i++
        }
        error("No sequencer event of type $type for channel $channel and note $noteId")
    }

    private fun countEvents(sequencer: OpnaSequencer, type: Int, channel: Int): Int {
        var count = 0
        var i = 0
        while (i < sequencer.eventCount) {
            val event = sequencer.events[i]
            if (event.type == type && event.channel == channel) count++
            i++
        }
        return count
    }

    private fun rms(buffer: FloatArray, start: Int, end: Int): Float {
        var sumSquares = 0.0
        var i = start
        while (i < end) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
            i++
        }
        return sqrt(sumSquares / (end - start)).toFloat()
    }

    private fun maximumEndMs(arrangement: ArrangementLanes): Int {
        var maximum = maximumEndMs(arrangement.lead)
        maximum = maxOf(maximum, maximumEndMs(arrangement.harmony))
        maximum = maxOf(maximum, maximumEndMs(arrangement.bass))
        maximum = maxOf(maximum, maximumEndMs(arrangement.percussion))
        val auxiliary = arrangement.auxiliary
        if (auxiliary != null) maximum = maxOf(maximum, maximumEndMs(auxiliary))
        val additional = arrangement.additional
        if (additional != null) maximum = maxOf(maximum, maximumEndMs(additional))
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
