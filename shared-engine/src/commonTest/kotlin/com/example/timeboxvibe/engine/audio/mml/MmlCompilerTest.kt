package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementRouting
import com.example.timeboxvibe.engine.EqType
import com.example.timeboxvibe.engine.audio.opna.EgMode
import com.example.timeboxvibe.engine.audio.opna.LlsPatches
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
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
    fun mmlPatchesMatchDecodedPmdOpnRegisterVoices() {
        assertPatch(LlsPatches.At54, 4, 17, 0, 20, 0)
        assertPatch(LlsPatches.At74, 4, 28, 58, 22, 0)
        assertPatch(LlsPatches.At99, 7, 29, 0, 0, 0)
        assertPatch(LlsPatches.At181, 7, 27, 0, 30, 0)
        assertEquals(4, LlsPatches.At54.algorithm)
        assertEquals(listOf(8, 4, 4, 2), listOf(LlsPatches.At54.op0.mul, LlsPatches.At54.op1.mul, LlsPatches.At54.op2.mul, LlsPatches.At54.op3.mul))
        assertEquals(listOf(7, 7, 3, 3), listOf(LlsPatches.At54.op0.detune, LlsPatches.At54.op1.detune, LlsPatches.At54.op2.detune, LlsPatches.At54.op3.detune))
        assertEquals(0, LlsPatches.At74.algorithm)
        assertEquals(listOf(6, 5, 0, 1), listOf(LlsPatches.At74.op0.mul, LlsPatches.At74.op1.mul, LlsPatches.At74.op2.mul, LlsPatches.At74.op3.mul))
        assertEquals(5, LlsPatches.At99.algorithm)
        assertEquals(4, LlsPatches.At181.algorithm)
        assertEquals(listOf(8, 4, 8, 4), listOf(LlsPatches.At181.op0.mul, LlsPatches.At181.op1.mul, LlsPatches.At181.op2.mul, LlsPatches.At181.op3.mul))
        assertEquals(EgMode.OPN_RATE, LlsPatches.At54.op0.egMode)
        assertEquals(EgMode.OPN_RATE, LlsPatches.At74.op0.egMode)
        assertEquals(EgMode.OPN_RATE, LlsPatches.At99.op0.egMode)
        assertEquals(EgMode.OPN_RATE, LlsPatches.At181.op0.egMode)
        assertEquals(listOf(0, 1, 0, 1), listOf(LlsPatches.At54.op0.ams, LlsPatches.At54.op1.ams, LlsPatches.At54.op2.ams, LlsPatches.At54.op3.ams))
    }

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
    fun fifthMelodicChannelCompilesAt181() {
        val source = """
            #BPM 120
            #BAR 4/4
            A @54 o4 l1 c |
            B @74 o4 l1 d |
            C @99 o4 l1 e |
            D @square o4 l1 f |
            E @181 o3 l1 g |
        """.trimIndent()

        val parsed = assertIs<MmlParseResult.Success>(MmlParser.parse(source)).document
        assertTrue(parsed.tracks[MmlChannelId.E.ordinal].commands.isNotEmpty())

        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(parsed)).arrangement
        val program = requireNotNull(arrangement.compiledOpnaSong)
        assertEquals(5, program.eventCount)
        assertEquals(OpnaPatchBank.FM_AT181, program.patchId[4])
        assertEquals(3, program.channel[4])
    }

    @Test
    fun multilineDemoCompilesToOnePrimitiveProgramAtAbsoluteTickTiming() {
        val compiled = MmlSongBank.senbonzakuraDemoResult
        assertTrue(compiled is MmlCompileResult.Success, compiled.toString())
        val result = compiled as MmlCompileResult.Success
        val arrangement = result.arrangement
        assertEquals(ArrangementRouting.MML_LOGICAL_TRACKS, arrangement.routing)
        val program = requireNotNull(arrangement.compiledOpnaSong)
        assertEquals(2, program.dialectVersion)
        assertEquals(3_868, program.eventCount)
        assertEquals(3_864, countMusicalEvents(program))
        assertEquals(2_037, countEvents(program, CompiledOpnaSong.FM_NOTE))
        assertEquals(1_422, countEvents(program, CompiledOpnaSong.SSG_NOTE))
        assertEquals(405, countEvents(program, CompiledOpnaSong.RHYTHM_SHOT))
        assertEquals(0L, program.startTick[0])
        assertEquals(240L, program.startTick[1])
        assertEquals(480L, program.startTick[2])
        assertEquals(51, program.midi[0])
        assertEquals(58, program.midi[1])
        assertEquals(56, program.midi[2])
        assertEquals(OpnaPatchBank.FM_AT74, program.patchId[0])
        assertEquals(78, program.velocity[0])
        assertEquals(3, arrangement.eqBands.size)
        assertEquals(180f, arrangement.eqBands[0].frequencyHz)
        assertEquals(850f, arrangement.eqBands[1].frequencyHz)
        assertEquals(2_400f, arrangement.eqBands[2].frequencyHz)
        assertEquals(52L * 4L * MmlCompiler.TICKS_PER_QUARTER, program.durationTicks)
        assertTrue(program.durationMilliseconds() in 77_500L..77_800L)
    }

    @Test
    fun badAppleDecodedPmdCheckpointsStayAlignedPastFormerCorruptionPoints() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult).arrangement
        val program = requireNotNull(arrangement.compiledOpnaSong)

        assertEvent(program, 134, 32_160L, 240, 49, OpnaPatchBank.FM_AT74, 240)
        assertEvent(program, 645, 81_600L, 480, 81, OpnaPatchBank.FM_AT99, 480)
        assertEvent(program, 919, 12_720L, 240, 74, OpnaPatchBank.FM_AT99, 200)
        assertEvent(program, 2_035, 96_000L, 1_440, 82, OpnaPatchBank.FM_AT99, 1_440)
        assertEvent(program, 2_036, 97_440L, 480, 82, OpnaPatchBank.FM_AT99, 480)
        assertEvent(program, 2_173, 20_880L, 120, 66, OpnaPatchBank.SSG_LLS_SQUARE, 120)
        assertEvent(program, 2_174, 21_000L, 120, 65, OpnaPatchBank.SSG_LLS_SQUARE, 120)
        assertEvent(program, 2_887, 21_060L, 60, 66, OpnaPatchBank.SSG_LLS_SQUARE, 60)
        assertEvent(program, 2_888, 21_480L, 240, 63, OpnaPatchBank.SSG_LLS_SQUARE, 240)
    }

    @Test
    fun restAdvancesTrackTimeWithoutExtendingPreviousNote() {
        val result = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                "#BPM 120\n#BAR 4/4\nA @54 v15 o4 l4 c r d2 |"
            )
        )
        val program = requireNotNull(result.arrangement.compiledOpnaSong)
        assertEquals(2, program.eventCount)
        assertEquals(0L, program.startTick[0])
        assertEquals(480, program.durationTick[0])
        assertEquals(960L, program.startTick[1])
        assertEquals(960, program.durationTick[1])
    }

    @Test
    fun drumsUseTypedPrimitiveEvents() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult).arrangement
        val program = requireNotNull(arrangement.compiledOpnaSong)
        var i = 0
        while (i < program.eventCount && program.eventType[i] != CompiledOpnaSong.RHYTHM_SHOT) i++
        assertTrue(i + 2 < program.eventCount)
        assertEquals(ProceduralDrums.DrumKind.KICK.ordinal, program.midi[i])
        assertEquals(ProceduralDrums.DrumKind.HAT.ordinal, program.midi[i + 1])
        assertEquals(ProceduralDrums.DrumKind.SNARE.ordinal, program.midi[i + 2])
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
    fun overlappingSharedSsgRegisterRequestsProduceSpecificWarnings() {
        val noise = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 120\n#BAR 4/4\n" +
                    "G @ssg_noise o4 l1 c |\nH @ssg_noise_slow o4 l1 e |"
            )
        )
        assertTrue(noise.warnings.any { it.reason.contains("noise periods") })

        val envelope = assertIs<MmlCompileResult.Success>(
            MmlCompiler.compile(
                "#MML 2\n#BPM 120\n#BAR 4/4\n" +
                    "G @ssg_envelope o4 l1 c |\nH @ssg_envelope_alt o4 l1 e |"
            )
        )
        assertTrue(envelope.warnings.any { it.reason.contains("hardware envelopes") })
    }

    @Test
    fun songAboveFormer4096LimitUsesExactAuthoredAndRuntimeArrays() {
        val result = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(
            "#BPM 120\n#BAR 4/4\n" +
                "A @54 o4 l16 [c c c c c c c c c c c c c c c c |]257"
        ))
        val program = result.arrangement.compiledOpnaSong
        assertEquals(4_112, program.eventCount)
        assertEquals(program.eventCount, program.eventType.size)
        assertEquals(program.eventCount, program.startTick.size)
        assertEquals(program.eventCount, program.patchId.size)

        val synth = OpnaLikeSynthesizer(8_000)
        val player = MmlArrangementScheduler.createPlayer(result.arrangement, synth, 8_000)
        assertEquals(8_225, player.eventCount)
        assertTrue(player.eventCount > OpnaSequencer.MAX_EVENTS)
        val prefix = FloatArray(1_024)
        synth.render(prefix, prefix.size, player, 0L)
        assertTrue(prefix.any { it != 0f })
    }

    @Test
    fun unifiedBenchmarkProgramRendersDeterministicNonSilentPcm() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult).arrangement
        val sampleRate = 48000
        val seqA = OpnaSequencer(sampleRate, arrangement.tempoBpm)
        val seqB = OpnaSequencer(sampleRate, arrangement.tempoBpm)
        val synthA = OpnaLikeSynthesizer(sampleRate)
        val synthB = OpnaLikeSynthesizer(sampleRate)
        MmlArrangementScheduler.schedule(arrangement, synthA, seqA, sampleRate)
        MmlArrangementScheduler.schedule(arrangement, synthB, seqB, sampleRate)
        val outA = FloatArray(sampleRate * 4)
        val outB = FloatArray(sampleRate * 4)
        synthA.render(outA, outA.size, seqA, 0L)
        synthB.render(outB, outB.size, seqB, 0L)
        assertTrue(outA.any { it != 0f })
        assertTrue(outA.contentEquals(outB))
    }

    private fun countEvents(program: CompiledOpnaSong, type: Int): Int {
        var count = 0
        var i = 0
        while (i < program.eventCount) {
            if (program.eventType[i] == type) count++
            i++
        }
        return count
    }

    private fun countMusicalEvents(program: CompiledOpnaSong): Int {
        var count = 0
        var i = 0
        while (i < program.eventCount) {
            if (program.eventType[i] <= CompiledOpnaSong.FM_POLY_NOTE) count++
            i++
        }
        return count
    }

    private fun assertPatch(
        patch: com.example.timeboxvibe.engine.audio.opna.FmPatch,
        feedback: Int,
        op0Tl: Int,
        op1Tl: Int,
        op2Tl: Int,
        op3Tl: Int
    ) {
        assertEquals(feedback, patch.feedback)
        assertEquals(op0Tl, patch.op0.tl)
        assertEquals(op1Tl, patch.op1.tl)
        assertEquals(op2Tl, patch.op2.tl)
        assertEquals(op3Tl, patch.op3.tl)
    }

    private fun assertEvent(
        program: CompiledOpnaSong,
        index: Int,
        start: Long,
        duration: Int,
        midi: Int,
        patchId: Int,
        gate: Int
    ) {
        assertEquals(start, program.startTick[index])
        assertEquals(duration, program.durationTick[index])
        assertEquals(midi, program.midi[index])
        assertEquals(patchId, program.patchId[index])
        assertEquals(gate, program.gateTick[index])
    }
}
