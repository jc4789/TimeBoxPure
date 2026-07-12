package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.OpnaAudioConstants
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.SequencerEvent
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MmlArrangementSchedulerTest {
    @Test
    fun productionScheduleUsesOneCompiledGainAndPatchEnvelopeContract() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)

        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)

        val firstFm = findFirstEvent(sequencer, SequencerEvent.FM_ON)
        val expectedLeadGain = 78f / 127f * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedLeadGain, firstFm.velocity, 0.0001f)
        assertEquals(-1f, firstFm.attack)
        assertEquals(-1f, firstFm.release)
        assertEquals(com.example.timeboxvibe.engine.audio.opna.LlsPatches.At74, firstFm.patch)

        val firstSsg = findFirstEvent(sequencer, SequencerEvent.SSG_ON)
        val expectedSsgGain = 40f / 127f * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedSsgGain, firstSsg.velocity, 0.0001f)
        assertEquals(-1f, firstSsg.attack)
        assertEquals(-1f, firstSsg.release)
        assertEquals(OpnaPatchBank.ssgPatch(OpnaPatchBank.SSG_LLS_SQUARE), firstSsg.ssgPatch)

        val firstDrum = findFirstEvent(sequencer, SequencerEvent.DRUM)
        val expectedDrumGain = ((11 * 127 + 7) / 15) / 127f * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedDrumGain, firstDrum.velocity, 0.0001f)
    }

    @Test
    fun eachMmlLaneUsesOnePhysicalChannel() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)

        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)

        assertEquals(487, countEvents(sequencer, SequencerEvent.FM_ON, 0))
        assertEquals(205, countEvents(sequencer, SequencerEvent.FM_ON, 1))
        assertEquals(205, countEvents(sequencer, SequencerEvent.FM_ON, 2))
        assertEquals(570, countEvents(sequencer, SequencerEvent.FM_ON, 3))
        assertEquals(570, countEvents(sequencer, SequencerEvent.FM_ON, 4))
        assertEquals(712, countEvents(sequencer, SequencerEvent.SSG_ON, 0))
        assertEquals(710, countEvents(sequencer, SequencerEvent.SSG_ON, 1))
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
        val program = requireNotNull(arrangement.compiledOpnaSong)
        val expectedFmGateSamples =
            ticksToSamples(program.startTick[0] + program.gateTick[0], program.bpm, sampleRate) -
                ticksToSamples(program.startTick[0], program.bpm, sampleRate)
        assertEquals(
            firstFmOn.sampleTime + expectedFmGateSamples,
            firstFmOff.sampleTime
        )

        val firstSsgOn = findFirstEvent(sequencer, SequencerEvent.SSG_ON)
        val firstSsgOff = findEvent(sequencer, SequencerEvent.SSG_OFF, firstSsgOn.channel, firstSsgOn.noteId)
        var ssgIndex = 0
        while (program.eventType[ssgIndex] != CompiledOpnaSong.SSG_NOTE) ssgIndex++
        val expectedSsgGateSamples =
            ticksToSamples(program.startTick[ssgIndex] + program.gateTick[ssgIndex], program.bpm, sampleRate) -
                ticksToSamples(program.startTick[ssgIndex], program.bpm, sampleRate)
        assertEquals(
            firstSsgOn.sampleTime + expectedSsgGateSamples,
            firstSsgOff.sampleTime
        )
    }

    @Test
    fun restContainsOnlyTheAuthenticPatchReleaseTailBeforeNextNote() {
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
        assertTrue(silentRestRms < 0.02f, "Authentic PMD patch release tail is excessive: rms=$silentRestRms")
        assertTrue(silentRestRms < nextNoteRms * 0.5f, "Release tail masks the next articulation: rest=$silentRestRms next=$nextNoteRms")
        assertTrue(nextNoteRms > 0.01f, "The note after the rest should retrigger: rms=$nextNoteRms")
    }

    @Test
    fun productionMonoBenchmarkIsAudibleFromTheFirstBlock() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48_000
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.enableOutputFilter = true
        synth.configureMasterEq(arrangement.eqBands)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)
        val frames = sampleRate / 10
        val mono = FloatArray(frames)
        synth.render(mono, frames, sequencer, 0L)

        var energy = 0.0
        var i = 0
        while (i < mono.size) {
            val sample = mono[i].toDouble()
            energy += sample * sample
            i++
        }
        val firstBlockRms = sqrt(energy / mono.size).toFloat()
        assertTrue(firstBlockRms > 0.005f, "Benchmark still has a silent opening: rms=$firstBlockRms")
    }

    @Test
    fun productionMonoMixKeepsBandsAndHeadroomControlled() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48_000
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.enableOutputFilter = true
        synth.configureMasterEq(arrangement.eqBands)
        var voice = 0
        while (voice < synth.fm.size) {
            synth.fm[voice].enableOversampling = true
            voice++
        }
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)
        val frames = sampleRate * 4
        val mono = FloatArray(frames)
        synth.render(mono, frames, sequencer, 0L)
        val highBandRatio = highBandEnergyRatio(mono, sampleRate)
        val midBandRatio = midBandEnergyRatio(mono, sampleRate)
        val lowBandRatio = lowBandEnergyRatio(mono, sampleRate)
        val presenceBandRatio = bandEnergyRatio(mono, sampleRate, 2_000.0, 3_200.0)
        assertTrue(lowBandRatio < 0.75, "Production mix is still low-band dominated: ratio=$lowBandRatio")
        assertTrue(midBandRatio > 0.25, "Production mix still lacks mid-band support: ratio=$midBandRatio")
        assertTrue(presenceBandRatio < 0.05, "2-3 kHz presence band is still concentrated: ratio=$presenceBandRatio")
        assertTrue(highBandRatio < 0.08, "High-band energy is still overpowering: ratio=$highBandRatio")
        assertTrue(synth.preClampPeak < OpnaLikeSynthesizer.SOFT_CLIP_KNEE, "Production mix reaches distortion knee: peak=${synth.preClampPeak}")
        assertEquals(0, synth.preClampKneeCrossings, "Production mix should not distort in the measured passage")
    }

    @Test
    fun completeDemoStaysBelowSoftClipKnee() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val deterministicSynth = OpnaLikeSynthesizer(sampleRate)
        synth.enableOutputFilter = true
        deterministicSynth.enableOutputFilter = true
        var voiceIndex = 0
        while (voiceIndex < synth.fm.size) {
            synth.fm[voiceIndex].enableOversampling = true
            deterministicSynth.fm[voiceIndex].enableOversampling = true
            voiceIndex++
        }
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        val deterministicSequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)
        MmlArrangementScheduler.schedule(arrangement, deterministicSynth, deterministicSequencer, sampleRate)

        val totalSamples = requireNotNull(arrangement.compiledOpnaSong).durationMilliseconds() * sampleRate / 1000L
        val buffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        val deterministicBuffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        var sampleOffset = 0L
        var maximumPreClipPeak = 0f
        var maximumOutputPeak = 0f
        var sumSquares = 0.0
        var renderedSamples = 0L
        var kneeCrossings = 0L
        while (sampleOffset < totalSamples) {
            val frames = minOf(buffer.size.toLong(), totalSamples - sampleOffset).toInt()
            synth.render(buffer, frames, sequencer, sampleOffset)
            deterministicSynth.render(deterministicBuffer, frames, deterministicSequencer, sampleOffset)
            if (synth.preClampPeak > maximumPreClipPeak) maximumPreClipPeak = synth.preClampPeak
            kneeCrossings += synth.preClampKneeCrossings
            var i = 0
            while (i < frames) {
                val sample = buffer[i]
                if (!sample.isFinite()) error("MML output became non-finite at sample ${sampleOffset + i}")
                if (sample != deterministicBuffer[i]) error("MML output is nondeterministic at sample ${sampleOffset + i}")
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
    fun llsPatchesReachSequencerWithoutCarrierEnvelopeOverrides() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)
        val expected = arrayOf(
            com.example.timeboxvibe.engine.audio.opna.LlsPatches.At74,
            com.example.timeboxvibe.engine.audio.opna.LlsPatches.At181,
            com.example.timeboxvibe.engine.audio.opna.LlsPatches.At181,
            com.example.timeboxvibe.engine.audio.opna.LlsPatches.At99,
            com.example.timeboxvibe.engine.audio.opna.LlsPatches.At99
        )
        var channel = 0
        while (channel < expected.size) {
            val event = findFirstEvent(sequencer, SequencerEvent.FM_ON, channel)
            assertEquals(expected[channel], event.patch)
            assertEquals(-1f, event.attack)
            assertEquals(-1f, event.release)
            channel++
        }
        val channel3At54 = findFirstPatchEvent(
            sequencer,
            channel = 3,
            patch = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At54,
            afterSample = -1L
        )
        findFirstPatchEvent(
            sequencer,
            channel = 3,
            patch = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At99,
            afterSample = channel3At54.sampleTime
        )
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

    private fun renderComparisonPrefix(arrangement: ArrangementLanes, sampleRate: Int, richPatches: Boolean): FloatArray {
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.enableOutputFilter = true
        var voiceIndex = 0
        while (voiceIndex < synth.fm.size) {
            synth.fm[voiceIndex].enableOversampling = true
            voiceIndex++
        }
        val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)
        MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)
        if (!richPatches) {
            synth.fm[0].applyPatch(com.example.timeboxvibe.engine.audio.opna.LlsPatches.At54.copy(
                algorithm = 0,
                feedback = 3,
                op0 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At54.op0.copy(mul = 1, tl = 24),
                op1 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At54.op1.copy(mul = 1, tl = 30),
                op2 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At54.op2.copy(tl = 36)
            ))
            synth.fm[1].applyPatch(com.example.timeboxvibe.engine.audio.opna.LlsPatches.At74.copy(
                algorithm = 2,
                feedback = 2,
                op0 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At74.op0.copy(tl = 26),
                op1 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At74.op1.copy(mul = 2, tl = 32),
                op2 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At74.op2.copy(tl = 38)
            ))
            synth.fm[2].applyPatch(com.example.timeboxvibe.engine.audio.opna.LlsPatches.At99.copy(
                feedback = 2,
                op0 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At99.op0.copy(tl = 24),
                op1 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At99.op1.copy(tl = 38),
                op2 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At99.op2.copy(tl = 34)
            ))
            synth.fm[3].applyPatch(com.example.timeboxvibe.engine.audio.opna.LlsPatches.At181.copy(
                feedback = 1,
                op0 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At181.op0.copy(tl = 32),
                op2 = com.example.timeboxvibe.engine.audio.opna.LlsPatches.At181.op2.copy(tl = 34)
            ))
        }
        val buffer = FloatArray(sampleRate * 2)
        synth.render(buffer, buffer.size, sequencer, 0L)
        return buffer
    }

    private fun highBandEnergyRatio(buffer: FloatArray, sampleRate: Int): Double {
        val decay = kotlin.math.exp(-2.0 * kotlin.math.PI * 5000.0 / sampleRate.toDouble())
        var lowPass = 0.0
        var highEnergy = 0.0
        var totalEnergy = 0.0
        var i = 0
        while (i < buffer.size) {
            val sample = buffer[i].toDouble()
            lowPass = (1.0 - decay) * sample + decay * lowPass
            val high = sample - lowPass
            highEnergy += high * high
            totalEnergy += sample * sample
            i++
        }
        return if (totalEnergy > 0.0) highEnergy / totalEnergy else 0.0
    }

    private fun lowBandEnergyRatio(buffer: FloatArray, sampleRate: Int): Double {
        val decay = kotlin.math.exp(-2.0 * kotlin.math.PI * 500.0 / sampleRate.toDouble())
        var lowPass = 0.0
        var lowEnergy = 0.0
        var totalEnergy = 0.0
        var i = 0
        while (i < buffer.size) {
            val sample = buffer[i].toDouble()
            lowPass = (1.0 - decay) * sample + decay * lowPass
            lowEnergy += lowPass * lowPass
            totalEnergy += sample * sample
            i++
        }
        return if (totalEnergy > 0.0) lowEnergy / totalEnergy else 0.0
    }

    private fun midBandEnergyRatio(buffer: FloatArray, sampleRate: Int): Double {
        val lowDecay = kotlin.math.exp(-2.0 * kotlin.math.PI * 250.0 / sampleRate.toDouble())
        val highDecay = kotlin.math.exp(-2.0 * kotlin.math.PI * 2_500.0 / sampleRate.toDouble())
        var lowPass = 0.0
        var highPassBoundary = 0.0
        var midEnergy = 0.0
        var totalEnergy = 0.0
        var i = 0
        while (i < buffer.size) {
            val sample = buffer[i].toDouble()
            lowPass = (1.0 - lowDecay) * sample + lowDecay * lowPass
            highPassBoundary = (1.0 - highDecay) * sample + highDecay * highPassBoundary
            val mid = highPassBoundary - lowPass
            midEnergy += mid * mid
            totalEnergy += sample * sample
            i++
        }
        return if (totalEnergy > 0.0) midEnergy / totalEnergy else 0.0
    }

    private fun bandEnergyRatio(buffer: FloatArray, sampleRate: Int, lowHz: Double, highHz: Double): Double {
        val lowDecay = kotlin.math.exp(-2.0 * kotlin.math.PI * lowHz / sampleRate.toDouble())
        val highDecay = kotlin.math.exp(-2.0 * kotlin.math.PI * highHz / sampleRate.toDouble())
        var belowLow = 0.0
        var belowHigh = 0.0
        var bandEnergy = 0.0
        var totalEnergy = 0.0
        var i = 0
        while (i < buffer.size) {
            val sample = buffer[i].toDouble()
            belowLow = (1.0 - lowDecay) * sample + lowDecay * belowLow
            belowHigh = (1.0 - highDecay) * sample + highDecay * belowHigh
            val band = belowHigh - belowLow
            bandEnergy += band * band
            totalEnergy += sample * sample
            i++
        }
        return if (totalEnergy > 0.0) bandEnergy / totalEnergy else 0.0
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

    private fun findFirstEvent(sequencer: OpnaSequencer, type: Int, channel: Int): SequencerEvent {
        var i = 0
        while (i < sequencer.eventCount) {
            val event = sequencer.events[i]
            if (event.type == type && event.channel == channel) return event
            i++
        }
        error("No sequencer event of type $type on channel $channel")
    }

    private fun findFirstPatchEvent(
        sequencer: OpnaSequencer,
        channel: Int,
        patch: com.example.timeboxvibe.engine.audio.opna.FmPatch,
        afterSample: Long
    ): SequencerEvent {
        var i = 0
        while (i < sequencer.eventCount) {
            val event = sequencer.events[i]
            if (event.type == SequencerEvent.FM_ON && event.channel == channel &&
                event.patch == patch && event.sampleTime > afterSample
            ) return event
            i++
        }
        error("No FM event for channel $channel with requested patch after $afterSample")
    }

    private fun ticksToSamples(ticks: Long, bpm: Float, sampleRate: Int): Long =
        (ticks.toDouble() * sampleRate.toDouble() * 60.0 /
            (bpm.toDouble() * CompiledOpnaSong.TICKS_PER_QUARTER.toDouble())).toLong()

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

}
