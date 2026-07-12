package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimeline
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PmdSoftwareLfoIntegrationTest {
    @Test
    fun pmdControlsRemainOrderedBeforeSameTickKeyOn() {
        val arrangement = compile(LFO_SOURCE)
        val song = assertNotNull(arrangement.compiledOpnaSong)
        assertEquals(CompiledOpnaSong.SOFTWARE_LFO_CLOCK, song.eventType[0])
        assertEquals(CompiledOpnaSong.SOFTWARE_LFO_DEFINE, song.eventType[1])
        assertEquals(CompiledOpnaSong.SOFTWARE_LFO_WAVE, song.eventType[2])
        assertEquals(CompiledOpnaSong.SOFTWARE_LFO_SWITCH, song.eventType[3])
        assertEquals(CompiledOpnaSong.SOFTWARE_LFO_TL_MASK, song.eventType[4])

        val player = MmlArrangementScheduler.createPlayer(arrangement, OpnaLikeSynthesizer(48_000), 48_000)
        var lastControl = -1
        var firstOn = -1
        var i = 0
        while (i < player.eventCount) {
            val type = player.timeline.eventType[i]
            if (type in CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE..CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH) {
                lastControl = i
            } else if (type == CompiledOpnaTimeline.FM_ON && firstOn < 0) {
                firstOn = i
            }
            i++
        }
        assertTrue(lastControl >= 0 && firstOn > lastControl)
    }

    @Test
    fun authoredFmAndSsgLfoTargetsChangePcmAndExposeIndependentState() {
        val wetArrangement = compile(LFO_SOURCE)
        val dryArrangement = compile(DRY_SOURCE)
        val wetSynth = OpnaLikeSynthesizer(48_000)
        val drySynth = OpnaLikeSynthesizer(48_000)
        val wetPlayer = MmlArrangementScheduler.createPlayer(wetArrangement, wetSynth, 48_000)
        val dryPlayer = MmlArrangementScheduler.createPlayer(dryArrangement, drySynth, 48_000)
        val wet = FloatArray(48_000)
        val dry = FloatArray(48_000)
        wetSynth.render(wet, wet.size, wetPlayer, 0L)
        drySynth.render(dry, dry.size, dryPlayer, 0L)

        assertNotEquals(dry.contentHashCode(), wet.contentHashCode())
        assertEquals(28, wetSynth.fm[0].softwareLfoValueSnapshot(0))
        assertEquals(12, wetSynth.ssg[0].softwareLfoValueSnapshot(1))
    }

    @Test
    fun softwareLfoPcmIsChunkAndFullResetDeterministic() {
        val arrangement = compile(LFO_SOURCE)
        val whole = render(arrangement, intArrayOf(1_024))
        val awkward = render(arrangement, intArrayOf(127, 509, 31, 1_021, 64))
        assertContentEquals(whole, awkward)

        val synth = OpnaLikeSynthesizer(48_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 48_000)
        val first = FloatArray(player.loopLengthSamples.toInt())
        val second = FloatArray(first.size)
        synth.render(first, first.size, player, 0L)
        player.reset(synth)
        synth.render(second, second.size, player, 0L)
        assertContentEquals(first, second)
    }

    private fun render(arrangement: ArrangementLanes, chunks: IntArray): FloatArray {
        val synth = OpnaLikeSynthesizer(48_000)
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, 48_000)
        val output = FloatArray(player.loopLengthSamples.toInt())
        val scratch = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        var offset = 0
        var chunk = 0
        while (offset < output.size) {
            val frames = minOf(chunks[chunk % chunks.size], scratch.size, output.size - offset)
            synth.render(scratch, frames, player, offset.toLong())
            var i = 0
            while (i < frames) {
                output[offset + i] = scratch[i]
                i++
            }
            offset += frames
            chunk++
        }
        return output
    }

    private fun compile(source: String): ArrangementLanes =
        assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement

    private companion object {
        val LFO_SOURCE = """
            #MML 2
            #BPM 120
            #BAR 4/4
            A @54 MX1 M0,2,1,30 MW6 *6 MM8 o4 c1 |
            G @square MXB1 MB0,1,1,12 MWB6 *B5 o4 c1 |
        """.trimIndent()
        val DRY_SOURCE = """
            #MML 2
            #BPM 120
            #BAR 4/4
            A @54 o4 c1 |
            G @square o4 c1 |
        """.trimIndent()
    }
}
