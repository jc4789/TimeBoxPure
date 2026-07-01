package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementRouting
import com.example.timeboxvibe.engine.EqType
import com.example.timeboxvibe.engine.LaneMode
import com.example.timeboxvibe.engine.TimbreRef
import com.example.timeboxvibe.engine.audio.opna.LlsPatches
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MmlCompilerTest {
    @Test
    fun parserStoresPeakEqMetadata() {
        val source = """
            #BPM 160
            #BAR 4/4
            #eq peak 4700 -2.0 0.8
            A @54 o4 l8 c d e f g a b >c |
        """.trimIndent()

        val document = assertIs<MmlParseResult.Success>(MmlParser.parse(source)).document
        assertEquals(1, document.eqBands.size)
        val band = document.eqBands[0].band
        assertEquals(EqType.PEAK, band.type)
        assertEquals(4700f, band.frequencyHz)
        assertEquals(-2f, band.gainDb)
        assertEquals(0.8f, band.q)
    }

    @Test
    fun parserRejectsInvalidEqMetadataWithLocations() {
        val directives = arrayOf(
            "#eq shelf 4700 -2.0 0.8",
            "#eq peak 4700 -2.0",
            "#eq peak NaN -2.0 0.8",
            "#eq peak Infinity -2.0 0.8",
            "#eq peak 0 -2.0 0.8",
            "#eq peak 4700 -2.0 0"
        )
        var i = 0
        while (i < directives.size) {
            val result = MmlParser.parse("#BPM 120\n#BAR 4/4\n${directives[i]}\nA @54 o4 l1 c")
            val failure = assertIs<MmlParseResult.Failure>(result)
            assertTrue(failure.diagnostics.any { it.line == 3 && it.column == 1 })
            i++
        }
    }

    @Test
    fun compilerPropagatesEqAndRejectsNyquistFrequency() {
        val compiled = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile("#BPM 120\n#BAR 4/4\n#eq peak 4700 -2.0 0.8\nA @54 o4 l1 c")
        )
        assertEquals(4700f, compiled.arrangement.eqBands.single().frequencyHz)

        val rejected = assertIs<MmlCompileResult.Failure>(
            MmlCompiler.compile("#BPM 120\n#BAR 4/4\n#eq peak 24000 -2.0 0.8\nA @54 o4 l1 c")
        )
        assertTrue(rejected.diagnostics.any { it.line == 3 && it.reason.contains("Nyquist") })
    }

    @Test
    fun parserSupportsDirectivesCommentsLoopsAndRepeatedChannelLines() {
        val source = """
            #BPM 160.73
            #BAR 4/4
            A @54 v13 o5 l8 [e- e+ g# a |]2 ; comment
            A >c4 <r4 | d1 |
            R @drum v12 l8 [k h s h |]2
            R r2 r2 |
        """.trimIndent()

        val result = assertIs<MmlParseResult.Success>(MmlParser.parse(source))
        assertEquals(160.73f, result.document.bpm)
        assertEquals(4, result.document.barNumerator)
        val trackA = result.document.tracks[MmlChannelId.A.ordinal]
        assertTrue(trackA.commands.any { it is MmlCommand.Note && it.accidental == -1 })
        assertTrue(trackA.commands.any { it is MmlCommand.Note && it.accidental == 1 })
        assertTrue(trackA.commands.any { it is MmlCommand.OctaveShift })
        assertTrue(trackA.commands.any { it is MmlCommand.Rest })
        val drums = result.document.tracks[MmlChannelId.R.ordinal].commands.count { it is MmlCommand.Drum }
        assertEquals(8, drums)
    }

    @Test
    fun multilineDemoCompilesToFiveTracksAtAbsoluteTickTiming() {
        val result = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult)
        val arrangement = result.arrangement
        assertEquals(ArrangementRouting.MML_LOGICAL_TRACKS, arrangement.routing)
        assertEquals(TimbreRef.FM_LLS_AT54, arrangement.lead.timbre)
        assertEquals(TimbreRef.FM_LLS_AT74, arrangement.harmony.timbre)
        assertEquals(TimbreRef.FM_LLS_AT99, arrangement.bass.timbre)
        assertEquals(TimbreRef.SSG_HARMONY_SQUARE, arrangement.auxiliary?.timbre)
        assertEquals(64, arrangement.lead.notes.size)
        assertEquals(64, arrangement.harmony.notes.size)
        assertEquals(30, arrangement.bass.notes.size)
        assertEquals(120, arrangement.auxiliary?.notes?.size)
        assertEquals(62, arrangement.percussion.notes.size)
        assertEquals(0, arrangement.lead.notes[0].startMs)
        assertEquals(186, arrangement.lead.notes[1].startMs)
        assertEquals(373, arrangement.lead.notes[2].startMs)
        assertEquals(13f / 15f, arrangement.lead.notes[0].volume)
        val expectedEndMs = (32.0 * 60000.0 / 160.73).toInt()
        val last = arrangement.lead.notes.last()
        assertEquals(expectedEndMs, last.startMs + last.durationMs)
    }

    @Test
    fun tonalMmlLanesCompileToMonophonicModes() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult).arrangement

        assertEquals(LaneMode.MONO_RETRIGGER, arrangement.lead.mode)
        assertEquals(LaneMode.MONO_RETRIGGER, arrangement.harmony.mode)
        assertEquals(LaneMode.MONO_RETRIGGER, arrangement.bass.mode)
        assertEquals(LaneMode.SSG_MONO, arrangement.auxiliary?.mode)
    }

    @Test
    fun restAdvancesTrackTimeWithoutExtendingPreviousNote() {
        val result = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                "#BPM 120\n#BAR 4/4\nA @54 v15 o4 l4 c r d2 |"
            )
        )
        val notes = result.arrangement.lead.notes

        assertEquals(2, notes.size)
        assertEquals(0, notes[0].startMs)
        assertEquals(500, notes[0].durationMs)
        assertEquals(1000, notes[1].startMs)
        assertEquals(1000, notes[1].durationMs)
        assertTrue(notes[0].startMs + notes[0].durationMs <= notes[1].startMs)
    }

    @Test
    fun drumsUseExistingToneSpecSentinels() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult).arrangement
        val notes = arrangement.percussion.notes
        assertEquals(-1f, notes[0].freq)
        assertEquals("kick", notes[0].type)
        assertEquals(8000f, notes[1].freq)
        assertEquals("hat", notes[1].type)
        assertEquals(3000f, notes[2].freq)
        assertEquals("snare", notes[2].type)
    }

    @Test
    fun malformedInputReturnsLocatedDiagnostics() {
        val cases = arrayOf(
            "#BPM 120\n#BAR 4/4\nA @54 o4 l8 [[c]2]2",
            "#BPM 120\n#BAR 4/4\nA @54 o4 l8 [c d",
            "#BPM 120\n#BAR 4/4\nA @54 o4 l8 c d e f |",
            "#BPM 120\n#BAR 4/4\nA @drum o4 l1 c",
            "#BPM 120\n#BAR 4/4\nR @drum l1 c"
        )
        var i = 0
        while (i < cases.size) {
            val result = MmlCompiler.compile(cases[i])
            val failure = assertIs<MmlCompileResult.Failure>(result)
            assertTrue(failure.diagnostics.isNotEmpty())
            assertTrue(failure.diagnostics[0].line > 0)
            assertTrue(failure.diagnostics[0].column > 0)
            i++
        }
    }

    @Test
    fun instrumentChangesAndFourSsgTracksAreRejected() {
        val changed = MmlCompiler.compile("#BPM 120\n#BAR 4/4\nA @54 o4 l1 c @74")
        assertIs<MmlCompileResult.Failure>(changed)

        val exhausted = MmlCompiler.compile(
            "#BPM 120\n#BAR 4/4\n" +
                "A @square o4 l1 c\nB @square o4 l1 c\n" +
                "C @square o4 l1 c\nD @square o4 l1 c"
        )
        val failure = assertIs<MmlCompileResult.Failure>(exhausted)
        assertTrue(failure.diagnostics.any { it.reason.contains("three available SSG") })
    }

    @Test
    fun expandedSongCannotSilentlyOverflowSequencerCapacity() {
        val result = MmlCompiler.compile(
            "#BPM 120\n#BAR 4/4\n" +
                "A @54 o4 l16 [c c c c c c c c c c c c c c c c |]129"
        )
        val failure = assertIs<MmlCompileResult.Failure>(result)
        assertTrue(failure.diagnostics.any { it.reason.contains("MAX_EVENTS") })
    }

    @Test
    fun compiledLeadRendersDeterministicNonSilentPcm() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult).arrangement
        val sampleRate = 48000
        val seqA = OpnaSequencer(sampleRate, arrangement.tempoBpm)
        val seqB = OpnaSequencer(sampleRate, arrangement.tempoBpm)
        scheduleLead(arrangement.lead.notes, seqA, sampleRate)
        scheduleLead(arrangement.lead.notes, seqB, sampleRate)
        val synthA = OpnaLikeSynthesizer(sampleRate)
        val synthB = OpnaLikeSynthesizer(sampleRate)
        synthA.fm[0].applyPatch(LlsPatches.At54)
        synthB.fm[0].applyPatch(LlsPatches.At54)
        val outA = FloatArray(4096)
        val outB = FloatArray(4096)
        synthA.render(outA, outA.size, seqA, 0L)
        synthB.render(outB, outB.size, seqB, 0L)
        assertTrue(outA.any { it != 0f })
        assertTrue(outA.contentEquals(outB))
    }

    private fun scheduleLead(notes: List<com.example.timeboxvibe.engine.ToneSpec>, seq: OpnaSequencer, sampleRate: Int) {
        var i = 0
        while (i < notes.size) {
            val note = notes[i]
            val midi = (12f * log2(note.freq / 440f) + 69f).roundToInt()
            seq.noteFmRaw(
                0,
                midi,
                note.startMs.toLong() * sampleRate / 1000L,
                note.durationMs.toLong() * sampleRate / 1000L
            )
            i++
        }
    }
}
