package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimeline
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaOutputProfile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompiledOpnaPlayerTest {
    @Test
    fun productionProgramExpandsToExactBoundaryCount() {
        val arrangement = assertIs<MmlCompileResult.Success>(
            MmlSongBank.senbonzakuraDemoResult
        ).arrangement
        val player = MmlArrangementScheduler.createPlayer(
            arrangement,
            OpnaLikeSynthesizer(SAMPLE_RATE),
            SAMPLE_RATE
        )

        assertEquals(7_328, player.eventCount)
        assertEquals(player.eventCount, player.timeline.eventType.size)
        assertEquals(player.eventCount, player.timeline.sampleTime.size)
    }

    @Test
    fun timelineArraysAreExactAndBoundariesUseCanonicalOrder() {
        val arrangement = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                A @54 Q8 o4 l4 c d r2 |
                R @drum l1 k |
            """.trimIndent()
        )
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        val timeline = player.timeline

        assertEquals(6, timeline.eventCount)
        assertEquals(timeline.eventCount, timeline.eventType.size)
        assertEquals(timeline.eventCount, timeline.sampleTime.size)
        assertEquals(timeline.eventCount, timeline.velocity.size)

        val boundarySample = SAMPLE_RATE / 2L
        var firstAtBoundary = -1
        var secondAtBoundary = -1
        var i = 0
        while (i < timeline.eventCount) {
            if (timeline.sampleTime[i] == boundarySample) {
                if (firstAtBoundary < 0) firstAtBoundary = timeline.eventType[i]
                else if (secondAtBoundary < 0) secondAtBoundary = timeline.eventType[i]
            }
            i++
        }
        assertEquals(CompiledOpnaTimeline.FM_OFF, firstAtBoundary)
        assertEquals(CompiledOpnaTimeline.FM_ON, secondAtBoundary)
    }

    @Test
    fun qZeroPreservesTheFullAuthoredNoteLength() {
        val arrangement = compile(
            """
                #MML 2
                #BPM 120
                #BAR 4/4
                A @54 Q0 o4 l1 c |
            """.trimIndent()
        )
        val player = MmlArrangementScheduler.createPlayer(
            arrangement,
            OpnaLikeSynthesizer(SAMPLE_RATE),
            SAMPLE_RATE
        )

        val on = player.timeline.eventType.indexOf(CompiledOpnaTimeline.FM_ON)
        val off = player.timeline.eventType.indexOf(CompiledOpnaTimeline.FM_OFF)
        assertEquals(0L, player.timeline.sampleTime[on])
        assertEquals(player.loopLengthSamples, player.timeline.sampleTime[off])
    }

    @Test
    fun awkwardRenderChunksProduceIdenticalPcm() {
        val arrangement = testArrangement()
        val whole = renderInChunks(arrangement, intArrayOf(Int.MAX_VALUE))
        val chunked = renderInChunks(arrangement, intArrayOf(127, 509, 31, 1_021, 64))

        assertContentEquals(whole, chunked)
    }

    @Test
    fun consecutiveLoopRendersAreIdenticalAfterFullReset() {
        val arrangement = testArrangement()
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        synth.enableOutputFilter = true
        var voice = 0
        while (voice < synth.fm.size) {
            synth.fm[voice].enableOversampling = true
            voice++
        }
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        val frames = player.loopLengthSamples.toInt()
        val first = FloatArray(frames)
        val second = FloatArray(frames)

        synth.render(first, frames, player, 0L)
        player.reset(synth)
        synth.render(second, frames, player, 0L)

        assertContentEquals(first, second)
    }

    @Test
    fun tempoChangeUpdatesNormalEnvelopeAtTheExactTimelineBoundary() {
        val changed = compile(envelopeTempoSource(120))
        val steady = compile(envelopeTempoSource(60))
        val changedSynth = OpnaLikeSynthesizer(48_000)
        val steadySynth = OpnaLikeSynthesizer(48_000)
        val changedPlayer = MmlArrangementScheduler.createPlayer(changed, changedSynth, 48_000)
        val steadyPlayer = MmlArrangementScheduler.createPlayer(steady, steadySynth, 48_000)

        changedSynth.render(FloatArray(48_000), 48_000, changedPlayer, 0L)
        steadySynth.render(FloatArray(48_000), 48_000, steadyPlayer, 0L)
        assertEquals(
            steadySynth.ssg[0].softwareEnvelopeLevelOffsetSnapshot(),
            changedSynth.ssg[0].softwareEnvelopeLevelOffsetSnapshot()
        )

        changedSynth.render(FloatArray(24_000), 24_000, changedPlayer, 48_000L)
        steadySynth.render(FloatArray(24_000), 24_000, steadyPlayer, 48_000L)
        assertTrue(
            changedSynth.ssg[0].softwareEnvelopeLevelOffsetSnapshot() <
                steadySynth.ssg[0].softwareEnvelopeLevelOffsetSnapshot()
        )
    }

    @Test
    fun badAppleRendersDeterministicallyUnderBothListeningProfiles() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult).arrangement
        val legacy = renderProfilePrefix(arrangement, OpnaOutputProfile.TIMEBOX_LEGACY)
        val reference = renderProfilePrefix(arrangement, OpnaOutputProfile.PC9801_86_REFERENCE)

        assertTrue(legacy.any { it != 0f })
        assertTrue(reference.any { it != 0f })
        assertTrue(legacy.all { it.isFinite() })
        assertTrue(reference.all { it.isFinite() })
        assertTrue(!legacy.contentEquals(reference))
        assertContentEquals(legacy, renderProfilePrefix(arrangement, OpnaOutputProfile.TIMEBOX_LEGACY))
        assertContentEquals(reference, renderProfilePrefix(arrangement, OpnaOutputProfile.PC9801_86_REFERENCE))
    }

    private fun renderProfilePrefix(
        arrangement: ArrangementLanes,
        profile: OpnaOutputProfile
    ): FloatArray {
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        synth.outputProfile = profile
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        val output = FloatArray(SAMPLE_RATE * 2)
        synth.render(output, output.size, player, 0L)
        return output
    }

    private fun renderInChunks(arrangement: ArrangementLanes, chunks: IntArray): FloatArray {
        val synth = OpnaLikeSynthesizer(SAMPLE_RATE)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, SAMPLE_RATE)
        val output = FloatArray(player.loopLengthSamples.toInt())
        val scratch = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        var offset = 0
        var chunkIndex = 0
        while (offset < output.size) {
            val requested = chunks[chunkIndex % chunks.size]
            val frames = minOf(requested, scratch.size, output.size - offset)
            synth.render(scratch, frames, player, offset.toLong())
            var i = 0
            while (i < frames) {
                output[offset + i] = scratch[i]
                i++
            }
            offset += frames
            chunkIndex++
        }
        return output
    }

    private fun testArrangement(): ArrangementLanes = compile(
        """
            #MML 2
            #BPM 120
            #BAR 4/4
            #LFO 3
            A @54 V90 Q7 H2,1,l8 o4 l8 c d e f g a b >c |
            G @square V50 Q6 o5 l8 c r e r g r >c r |
            R @drum V80 l8 k h s h k h s h |
        """.trimIndent()
    )

    private fun envelopeTempoSource(changedTempo: Int): String = """
        #MML 2
        #BPM 60
        #PMDCLOCK 24
        #BAR 4/4
        A @54 r4 T$changedTempo r2. |
        G @square EX0 E0,0,8,1 o4 c1 |
    """.trimIndent()

    private fun compile(source: String): ArrangementLanes =
        assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement

    private companion object {
        const val SAMPLE_RATE = 8_000
    }
}
