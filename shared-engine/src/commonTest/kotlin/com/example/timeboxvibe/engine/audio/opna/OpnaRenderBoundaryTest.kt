package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.mml.MmlCompileResult
import com.example.timeboxvibe.engine.audio.mml.MmlCompiler
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpnaRenderBoundaryTest {
    @Test
    fun masteringMeasurementsAccumulateUntilExplicitReset() {
        val mastering = SongMastering(SAMPLE_RATE)
        mastering.enableOutputFilter = false
        mastering.processMono(floatArrayOf(100f), 1)
        val loudPeak = mastering.preClampPeak
        val loudCrossings = mastering.preClampKneeCrossings
        assertTrue(loudPeak > SongMastering.SOFT_CLIP_KNEE)
        assertTrue(loudCrossings > 0)

        mastering.processMono(floatArrayOf(0f), 1)
        assertEquals(loudPeak, mastering.preClampPeak)
        assertEquals(loudCrossings, mastering.preClampKneeCrossings)

        mastering.reset()
        assertEquals(0f, mastering.preClampPeak)
        assertEquals(0, mastering.preClampKneeCrossings)
    }

    @Test
    fun rawCoreBypassesOutputProfileAndAllSongMastering() {
        val arrangement = ssgOnlyArrangement()
        val legacy = renderPrefix(
            arrangement, Stage.RAW_MONO, OpnaOutputProfile.TIMEBOX_LEGACY,
            configureMastering = false
        )
        val referenceWithDifferentMastering = renderPrefix(
            arrangement, Stage.RAW_MONO, OpnaOutputProfile.PC9801_86_REFERENCE,
            configureMastering = true
        )

        assertContentEquals(legacy, referenceWithDifferentMastering)
        assertTrue(legacy.any { it != 0f })
    }

    @Test
    fun profiledPreMasterAppliesNamedBusRatioButNotSongMastering() {
        val arrangement = ssgOnlyArrangement()
        val raw = renderPrefix(arrangement, Stage.RAW_MONO, OpnaOutputProfile.PC9801_86_REFERENCE)
        val profiled = renderPrefix(
            arrangement, Stage.PROFILED_MONO, OpnaOutputProfile.PC9801_86_REFERENCE
        )
        val expectedGain = OpnaOutputProfile.PC9801_86_REFERENCE.ssgGain

        var i = 0
        while (i < raw.size) {
            assertEquals(raw[i] * expectedGain, profiled[i], 0.000001f)
            i++
        }

        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        synth.renderProfiledPreMaster(FloatArray(PREFIX_FRAMES), PREFIX_FRAMES, player, 0L)
        assertEquals(0f, synth.preClampPeak)
        assertEquals(0, synth.preClampKneeCrossings)
    }

    @Test
    fun productIsProfiledPreMasterFollowedBySongMastering() {
        val arrangement = mixedArrangement()
        val profiledSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
        profiledSynth.outputProfile = OpnaOutputProfile.PC9801_86_REFERENCE
        val profiledPlayer = MmlArrangementScheduler.createPlayer(arrangement, profiledSynth, SAMPLE_RATE)
        val profiled = FloatArray(PREFIX_FRAMES)
        profiledSynth.renderProfiledPreMaster(profiled, profiled.size, profiledPlayer, 0L)

        val expectedProduct = profiled.copyOf()
        SongMastering(SAMPLE_RATE).processMono(expectedProduct, expectedProduct.size)

        val productSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
        productSynth.outputProfile = OpnaOutputProfile.PC9801_86_REFERENCE
        val productPlayer = MmlArrangementScheduler.createPlayer(arrangement, productSynth, SAMPLE_RATE)
        val product = FloatArray(PREFIX_FRAMES)
        productSynth.render(product, product.size, productPlayer, 0L)

        assertContentEquals(expectedProduct, product)
        assertTrue(productSynth.preClampPeak > 0f)
    }

    @Test
    fun rawAndProfiledTimelineRenderingAreChunkInvariantInMonoAndStereo() {
        val arrangement = mixedArrangement()
        assertContentEquals(
            renderLoop(arrangement, Stage.RAW_MONO, intArrayOf(Int.MAX_VALUE)),
            renderLoop(arrangement, Stage.RAW_MONO, intArrayOf(127, 509, 31, 1_021, 64))
        )
        assertContentEquals(
            renderLoop(arrangement, Stage.PROFILED_MONO, intArrayOf(Int.MAX_VALUE)),
            renderLoop(arrangement, Stage.PROFILED_MONO, intArrayOf(127, 509, 31, 1_021, 64))
        )
        assertContentEquals(
            renderLoop(arrangement, Stage.RAW_STEREO, intArrayOf(Int.MAX_VALUE)),
            renderLoop(arrangement, Stage.RAW_STEREO, intArrayOf(127, 509, 31, 1_021, 64))
        )
        assertContentEquals(
            renderLoop(arrangement, Stage.PROFILED_STEREO, intArrayOf(Int.MAX_VALUE)),
            renderLoop(arrangement, Stage.PROFILED_STEREO, intArrayOf(127, 509, 31, 1_021, 64))
        )
    }

    @Test
    fun arbitraryRawAndProfiledIntervalsReplayAllPriorTimelineState() {
        val arrangement = mixedArrangement()
        val intervalOffset = 6_137
        val intervalFrames = 1_777
        for (stage in Stage.entries) {
            val referenceSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
            val referencePlayer = MmlArrangementScheduler.createPlayer(arrangement, referenceSynth, SAMPLE_RATE)
            val prefix = FloatArray((intervalOffset + intervalFrames) * stage.channels)
            renderStage(
                referenceSynth, referencePlayer, stage, prefix,
                intervalOffset + intervalFrames, 0L
            )
            val expected = prefix.copyOfRange(
                intervalOffset * stage.channels,
                (intervalOffset + intervalFrames) * stage.channels
            )

            val freshSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
            val freshPlayer = MmlArrangementScheduler.createPlayer(arrangement, freshSynth, SAMPLE_RATE)
            val fresh = FloatArray(intervalFrames * stage.channels)
            renderStage(freshSynth, freshPlayer, stage, fresh, intervalFrames, intervalOffset.toLong())
            assertContentEquals(expected, fresh, "fresh seek stage=$stage")

            val gapSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
            val gapPlayer = MmlArrangementScheduler.createPlayer(arrangement, gapSynth, SAMPLE_RATE)
            renderStage(gapSynth, gapPlayer, stage, FloatArray(113 * stage.channels), 113, 0L)
            val afterGap = FloatArray(intervalFrames * stage.channels)
            renderStage(gapSynth, gapPlayer, stage, afterGap, intervalFrames, intervalOffset.toLong())
            assertContentEquals(expected, afterGap, "forward gap stage=$stage")

            val backwardFrames = 509
            val backwardOffset = 1_031
            val backward = FloatArray(backwardFrames * stage.channels)
            renderStage(
                gapSynth, gapPlayer, stage, backward, backwardFrames, backwardOffset.toLong()
            )
            assertContentEquals(
                prefix.copyOfRange(
                    backwardOffset * stage.channels,
                    (backwardOffset + backwardFrames) * stage.channels
                ),
                backward,
                "backward seek stage=$stage"
            )
        }
    }

    @Test
    fun rawAndProfiledLoopsReplayFromTheSameResetEventProgram() {
        assertLoopReplay(Stage.RAW_MONO)
        assertLoopReplay(Stage.PROFILED_MONO)
        assertLoopReplay(Stage.RAW_STEREO)
        assertLoopReplay(Stage.PROFILED_STEREO)
    }

    @Test
    fun rawMonoAndStereoExposeDistinctButConsistentInspectionPoints() {
        val arrangement = fmOnlyArrangement()
        val mono = renderPrefix(arrangement, Stage.RAW_MONO, OpnaOutputProfile.TIMEBOX_LEGACY)
        val stereo = renderPrefix(arrangement, Stage.RAW_STEREO, OpnaOutputProfile.TIMEBOX_LEGACY)

        var frame = 0
        while (frame < mono.size) {
            assertEquals(mono[frame], stereo[frame * 2])
            assertEquals(mono[frame], stereo[frame * 2 + 1])
            frame++
        }
    }

    @Test
    fun rawPreMasterFmRoutingCoversAllFourYmOutputEnableCombinations() {
        val both = renderFmPan(0)
        val left = renderFmPan(1)
        val right = renderFmPan(2)
        val neither = renderFmPan(3)
        assertTrue(both.any { it != 0f })
        assertTrue(renderFmPanMono(0).any { it != 0f })
        assertTrue(renderFmPanMono(3).all { it == 0f })

        var frame = 0
        while (frame < both.size / 2) {
            val sample = both[frame * 2]
            assertEquals(sample, both[frame * 2 + 1], "both frame=$frame")
            assertEquals(sample, left[frame * 2], "left frame=$frame")
            assertEquals(0f, left[frame * 2 + 1], "left-disabled-right frame=$frame")
            assertEquals(0f, right[frame * 2], "right-disabled-left frame=$frame")
            assertEquals(sample, right[frame * 2 + 1], "right frame=$frame")
            assertEquals(0f, neither[frame * 2], "neither left frame=$frame")
            assertEquals(0f, neither[frame * 2 + 1], "neither right frame=$frame")
            frame++
        }
    }

    @Test
    fun playerReplaysStateWhenReusedWithAnotherSynthesizer() {
        val arrangement = mixedArrangement()
        val firstSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val sharedPlayer = MmlArrangementScheduler.createPlayer(arrangement, firstSynth, SAMPLE_RATE)
        val offset = 777
        val frames = 509
        firstSynth.renderRawCore(FloatArray(offset), offset, sharedPlayer, 0L)

        val secondSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val actual = FloatArray(frames)
        secondSynth.renderRawCore(actual, frames, sharedPlayer, offset.toLong())

        val referenceSynth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val referencePlayer = MmlArrangementScheduler.createPlayer(arrangement, referenceSynth, SAMPLE_RATE)
        val prefix = FloatArray(offset + frames)
        referenceSynth.renderRawCore(prefix, prefix.size, referencePlayer, 0L)
        assertContentEquals(prefix.copyOfRange(offset, offset + frames), actual)
    }

    @Test
    fun internalRawSeekDoesNotEraseMasteringMeasurements() {
        val arrangement = mixedArrangement()
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        synth.render(FloatArray(PREFIX_FRAMES), PREFIX_FRAMES, player, 0L)
        val peak = synth.preClampPeak
        val crossings = synth.preClampKneeCrossings
        assertTrue(peak > 0f)

        synth.renderRawCore(FloatArray(257), 257, player, 0L)
        assertEquals(peak, synth.preClampPeak)
        assertEquals(crossings, synth.preClampKneeCrossings)
    }

    private fun renderFmPan(pan: Int): FloatArray {
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        synth.fm[0].applyPatch(LlsPatches.At54.copy(pan = pan))
        synth.fm[0].noteOn(60)
        val output = FloatArray(PAN_TEST_FRAMES * 2)
        synth.renderTimelineSegment(
            output, 0, PAN_TEST_FRAMES,
            CompiledOpnaPlayer.CHANNELS_STEREO,
            CompiledOpnaPlayer.STAGE_RAW_CORE
        )
        return output
    }

    private fun renderFmPanMono(pan: Int): FloatArray {
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        synth.fm[0].applyPatch(LlsPatches.At54.copy(pan = pan))
        synth.fm[0].noteOn(60)
        val output = FloatArray(PAN_TEST_FRAMES)
        synth.renderTimelineSegment(
            output, 0, PAN_TEST_FRAMES,
            CompiledOpnaPlayer.CHANNELS_MONO,
            CompiledOpnaPlayer.STAGE_RAW_CORE
        )
        return output
    }

    private fun assertLoopReplay(stage: Stage) {
        val arrangement = mixedArrangement()
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        val frames = player.loopLengthSamples.toInt()
        val first = FloatArray(frames * stage.channels)
        val second = FloatArray(frames * stage.channels)

        renderStage(synth, player, stage, first, frames, 0L)
        player.reset(synth)
        renderStage(synth, player, stage, second, frames, 0L)

        assertContentEquals(first, second)
    }

    private fun renderLoop(arrangement: ArrangementLanes, stage: Stage, chunks: IntArray): FloatArray {
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        synth.outputProfile = OpnaOutputProfile.PC9801_86_REFERENCE
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        val frames = player.loopLengthSamples.toInt()
        val result = FloatArray(frames * stage.channels)
        var frameOffset = 0
        var chunkIndex = 0
        while (frameOffset < frames) {
            val requested = chunks[chunkIndex % chunks.size]
            val chunkFrames = minOf(requested, frames - frameOffset)
            val chunk = FloatArray(chunkFrames * stage.channels)
            renderStage(synth, player, stage, chunk, chunkFrames, frameOffset.toLong())
            chunk.copyInto(result, frameOffset * stage.channels)
            frameOffset += chunkFrames
            chunkIndex++
        }
        return result
    }

    private fun renderPrefix(
        arrangement: ArrangementLanes,
        stage: Stage,
        profile: OpnaOutputProfile,
        configureMastering: Boolean = false
    ): FloatArray {
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        synth.outputProfile = profile
        if (configureMastering) {
            synth.filterAlpha = 0.91f
            synth.enableOutputFilter = false
            synth.enableStereoResonator = true
        }
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        val output = FloatArray(PREFIX_FRAMES * stage.channels)
        renderStage(synth, player, stage, output, PREFIX_FRAMES, 0L)
        return output
    }

    private fun renderStage(
        synth: OpnaLikeSynthesizer,
        player: CompiledOpnaPlayer,
        stage: Stage,
        output: FloatArray,
        frames: Int,
        sampleOffset: Long
    ) {
        when (stage) {
            Stage.RAW_MONO -> synth.renderRawCore(output, frames, player, sampleOffset)
            Stage.PROFILED_MONO -> synth.renderProfiledPreMaster(output, frames, player, sampleOffset)
            Stage.RAW_STEREO -> synth.renderRawCoreStereo(output, frames, player, sampleOffset)
            Stage.PROFILED_STEREO -> synth.renderProfiledPreMasterStereo(output, frames, player, sampleOffset)
        }
    }

    private fun ssgOnlyArrangement(): ArrangementLanes = compile(
        """
            #MML 2
            #BPM 120
            #BAR 4/4
            G @square Q8 o4 l4 c e g c |
        """.trimIndent()
    )

    private fun fmOnlyArrangement(): ArrangementLanes = compile(
        """
            #MML 2
            #BPM 120
            #BAR 4/4
            A @54 Q8 o4 l4 c d e f |
        """.trimIndent()
    )

    private fun mixedArrangement(): ArrangementLanes = compile(
        """
            #MML 2
            #BPM 120
            #BAR 4/4
            A @54 Q8 o4 l4 c d e f |
            G @square Q8 o4 l4 c e g c |
            R @drum l4 k s h k |
        """.trimIndent()
    )

    private fun compile(source: String): ArrangementLanes {
        val result = MmlCompiler.compile(source)
        return (result as MmlCompileResult.Success).arrangement
    }

    private enum class Stage(val channels: Int) {
        RAW_MONO(1),
        PROFILED_MONO(1),
        RAW_STEREO(2),
        PROFILED_STEREO(2)
    }

    companion object {
        private const val SAMPLE_RATE = 8_000
        private const val PREFIX_FRAMES = 2_048
        private const val PAN_TEST_FRAMES = OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK
    }
}
