package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.OpnaAudioConstants
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaPlayer
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimeline
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.FmPatch
import com.example.timeboxvibe.engine.audio.opna.LlsPatches
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import com.example.timeboxvibe.engine.audio.opna.PmdSampleClock
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MmlArrangementSchedulerTest {
    @Test
    fun productionPlayerUsesOneCompiledGainAndPatchContract() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)

        val firstFm = findFirstEvent(player, CompiledOpnaTimeline.FM_ON)
        val expectedLeadGain = 78f / 127f * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedLeadGain, player.timeline.velocity[firstFm], 0.0001f)
        assertSame(LlsPatches.At74, player.timeline.instrumentBank.fmPatch(player.timeline.patchId[firstFm]))

        val firstSsg = findFirstEvent(player, CompiledOpnaTimeline.SSG_ON)
        val expectedSsgGain = 40f / 127f * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedSsgGain, player.timeline.velocity[firstSsg], 0.0001f)
        assertSame(
            OpnaPatchBank.ssgPatch(OpnaPatchBank.SSG_LLS_SQUARE),
            player.timeline.instrumentBank.ssgPatch(player.timeline.patchId[firstSsg])
        )

        val firstDrum = findFirstEvent(player, CompiledOpnaTimeline.DRUM_SHOT)
        val expectedDrumGain = ((11 * 127 + 7) / 15) / 127f * MmlArrangementScheduler.MIX_GAIN
        assertEquals(expectedDrumGain, player.timeline.velocity[firstDrum], 0.0001f)
    }

    @Test
    fun eachMmlLaneUsesOnePhysicalChannel() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)

        assertEquals(487, countEvents(player, CompiledOpnaTimeline.FM_ON, 0))
        assertEquals(205, countEvents(player, CompiledOpnaTimeline.FM_ON, 1))
        assertEquals(205, countEvents(player, CompiledOpnaTimeline.FM_ON, 2))
        assertEquals(570, countEvents(player, CompiledOpnaTimeline.FM_ON, 3))
        assertEquals(570, countEvents(player, CompiledOpnaTimeline.FM_ON, 4))
        assertEquals(712, countEvents(player, CompiledOpnaTimeline.SSG_ON, 0))
        assertEquals(710, countEvents(player, CompiledOpnaTimeline.SSG_ON, 1))
        assertEquals(0, countEvents(player, CompiledOpnaTimeline.SSG_ON, 2))
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
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)

        assertEquals(1, countEvents(player, CompiledOpnaTimeline.FM_ON, 3))
        assertEquals(1, countEvents(player, CompiledOpnaTimeline.SSG_ON, 0))
    }

    @Test
    fun releaseEndsAtCompiledNoteBoundary() {
        val arrangement = requireDemoArrangement()
        val sampleRate = 48000
        val synth = OpnaLikeSynthesizer(sampleRate)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)

        val firstFmOn = findFirstEvent(player, CompiledOpnaTimeline.FM_ON)
        val firstFmOff = findEvent(
            player, CompiledOpnaTimeline.FM_OFF, player.timeline.channel[firstFmOn], player.timeline.noteId[firstFmOn]
        )
        val program = requireNotNull(arrangement.compiledOpnaSong)
        val expectedFmGateSamples =
            PmdSampleClock.samplesAt(program, program.startTick[0] + program.gateTick[0], sampleRate) -
                PmdSampleClock.samplesAt(program, program.startTick[0], sampleRate)
        assertEquals(
            player.timeline.sampleTime[firstFmOn] + expectedFmGateSamples,
            player.timeline.sampleTime[firstFmOff]
        )

        val firstSsgOn = findFirstEvent(player, CompiledOpnaTimeline.SSG_ON)
        val firstSsgOff = findEvent(
            player, CompiledOpnaTimeline.SSG_OFF, player.timeline.channel[firstSsgOn], player.timeline.noteId[firstSsgOn]
        )
        var ssgIndex = 0
        while (program.eventType[ssgIndex] != CompiledOpnaSong.SSG_NOTE) ssgIndex++
        val expectedSsgGateSamples =
            PmdSampleClock.samplesAt(program, program.startTick[ssgIndex] + program.gateTick[ssgIndex], sampleRate) -
                PmdSampleClock.samplesAt(program, program.startTick[ssgIndex], sampleRate)
        assertEquals(
            player.timeline.sampleTime[firstSsgOn] + expectedSsgGateSamples,
            player.timeline.sampleTime[firstSsgOff]
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
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)

        val buffer = FloatArray(sampleRate * 3 / 2)
        synth.render(buffer, buffer.size, player, 0L)

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
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)
        val frames = sampleRate / 10
        val mono = FloatArray(frames)
        synth.render(mono, frames, player, 0L)

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
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)
        val frames = sampleRate * 4
        val mono = FloatArray(frames)
        synth.render(mono, frames, player, 0L)
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
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)
        val deterministicPlayer = MmlArrangementScheduler.createPlayer(arrangement, deterministicSynth, sampleRate)

        val totalSamples = player.loopLengthSamples
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
            synth.render(buffer, frames, player, sampleOffset)
            deterministicSynth.render(deterministicBuffer, frames, deterministicPlayer, sampleOffset)
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
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)
        val expected = arrayOf(
            LlsPatches.At74,
            LlsPatches.At181,
            LlsPatches.At181,
            LlsPatches.At99,
            LlsPatches.At99
        )
        var channel = 0
        while (channel < expected.size) {
            val event = findFirstEvent(player, CompiledOpnaTimeline.FM_ON, channel)
            assertSame(expected[channel], player.timeline.instrumentBank.fmPatch(player.timeline.patchId[event]))
            channel++
        }
        val channel3At54 = findFirstPatchEvent(
            player,
            channel = 3,
            patch = LlsPatches.At54,
            afterSample = -1L
        )
        findFirstPatchEvent(
            player,
            channel = 3,
            patch = LlsPatches.At99,
            afterSample = player.timeline.sampleTime[channel3At54]
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
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)
        val buffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        synth.render(buffer, buffer.size, player, 0L)
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

    private fun findFirstEvent(player: CompiledOpnaPlayer, type: Int): Int {
        var i = 0
        while (i < player.eventCount) {
            if (player.timeline.eventType[i] == type) return i
            i++
        }
        error("No timeline event of type $type")
    }

    private fun findFirstEvent(player: CompiledOpnaPlayer, type: Int, channel: Int): Int {
        var i = 0
        while (i < player.eventCount) {
            if (player.timeline.eventType[i] == type && player.timeline.channel[i] == channel) return i
            i++
        }
        error("No timeline event of type $type on channel $channel")
    }

    private fun findFirstPatchEvent(
        player: CompiledOpnaPlayer,
        channel: Int,
        patch: FmPatch,
        afterSample: Long
    ): Int {
        var i = 0
        while (i < player.eventCount) {
            if (player.timeline.eventType[i] == CompiledOpnaTimeline.FM_ON &&
                player.timeline.channel[i] == channel && player.timeline.instrumentBank.fmPatch(player.timeline.patchId[i]) === patch &&
                player.timeline.sampleTime[i] > afterSample
            ) return i
            i++
        }
        error("No FM event for channel $channel with requested patch after $afterSample")
    }

    private fun findEvent(player: CompiledOpnaPlayer, type: Int, channel: Int, noteId: Int): Int {
        var i = 0
        while (i < player.eventCount) {
            if (player.timeline.eventType[i] == type && player.timeline.channel[i] == channel &&
                player.timeline.noteId[i] == noteId
            ) return i
            i++
        }
        error("No timeline event of type $type for channel $channel and note $noteId")
    }

    private fun countEvents(player: CompiledOpnaPlayer, type: Int, channel: Int): Int {
        var count = 0
        var i = 0
        while (i < player.eventCount) {
            if (player.timeline.eventType[i] == type && player.timeline.channel[i] == channel) count++
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
