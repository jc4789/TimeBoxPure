package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.ArrangementRouting
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSongBuilder
import com.example.timeboxvibe.engine.audio.opna.FallbackSourceInstrumentLookup
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
import com.example.timeboxvibe.engine.audio.opna.PmdPerformanceLaws
import com.example.timeboxvibe.engine.audio.opna.SourceInstrumentLookup
import com.example.timeboxvibe.engine.audio.opna.SsgPatch
import com.example.timeboxvibe.engine.audio.AudioLaws

sealed class MmlCompileResult {
    data class Success(
        val arrangement: ArrangementLanes,
        val warnings: List<MmlDiagnostic> = emptyList()
    ) : MmlCompileResult()
    data class Failure(val diagnostics: List<MmlDiagnostic>) : MmlCompileResult()
}

object MmlCompiler {
    const val TICKS_PER_QUARTER = 480
    private const val WHOLE_NOTE_TICKS = TICKS_PER_QUARTER * 4
    private const val DEFAULT_OCTAVE = 4
    private const val DEFAULT_LENGTH = 4
    private const val DEFAULT_KEY_ROOT_MIDI = 60
    private const val SSG_SOURCE_ORDER_STRIDE = 8

    fun compile(source: String): MmlCompileResult {
        return compileSource(source, OpnaPatchBank)
    }

    internal fun compile(source: String, localInstruments: SourceInstrumentLookup): MmlCompileResult {
        return compileSource(source, FallbackSourceInstrumentLookup(localInstruments, OpnaPatchBank))
    }

    private fun compileSource(source: String, instruments: SourceInstrumentLookup): MmlCompileResult {
        return when (val parsed = MmlParser.parse(source)) {
            is MmlParseResult.Failure -> MmlCompileResult.Failure(parsed.diagnostics)
            is MmlParseResult.Success -> compile(parsed.document, instruments)
        }
    }

    fun compile(document: MmlDocument): MmlCompileResult {
        return compile(document, OpnaPatchBank)
    }

    private fun compile(document: MmlDocument, instruments: SourceInstrumentLookup): MmlCompileResult =
        compileV2(document, instruments)

    private fun compileV2(document: MmlDocument, instruments: SourceInstrumentLookup): MmlCompileResult {
        val diagnostics = mutableListOf<MmlDiagnostic>()
        val scaledBarTicks = document.barNumerator.toLong() * WHOLE_NOTE_TICKS
        if (scaledBarTicks % document.barDenominator != 0L || scaledBarTicks > Int.MAX_VALUE) {
            return MmlCompileResult.Failure(listOf(MmlDiagnostic(1, 1, "#BAR cannot be represented by the compiler tick grid")))
        }
        var eqIndex = 0
        while (eqIndex < document.eqBands.size) {
            val directive = document.eqBands[eqIndex]
            if (directive.band.frequencyHz >= AudioLaws.SAMPLE_RATE * 0.5f) {
                diagnostics.add(MmlDiagnostic(directive.line, directive.column, "#eq frequencyHz must be below the Nyquist frequency"))
            }
            eqIndex++
        }

        val barTicks = (scaledBarTicks / document.barDenominator).toInt()
        val builder = CompiledOpnaSongBuilder(
            bpm = document.bpm,
            bpmMilli = document.bpmMilli,
            beatsPerBar = document.barNumerator,
            pmdClocksPerQuarter = document.pmdClocksPerQuarter,
            lfoRate = document.lfoRate,
            fm3Extended = document.fm3Extended,
            sourceInstruments = instruments
        )
        addInitialHardwareLfoState(document, builder, diagnostics)
        val channelC = document.tracks[MmlChannelId.C.ordinal]
        val channelCPatch = firstFmPatch(channelC, instruments, builder)
        compileRhythmSequence(document, barTicks, builder, diagnostics)
        var trackIndex = 0
        while (trackIndex < document.tracks.size) {
            val track = document.tracks[trackIndex]
            val isFm3Operator = track.channel in MmlChannelId.C1..MmlChannelId.C4
            if (track.channel == MmlChannelId.K) {
                // K expands the separately compiled R0..R255 definitions above.
            } else if (isFm3Operator && !document.fm3Extended && track.commands.isNotEmpty()) {
                val first = track.commands[0]
                diagnostics.add(MmlDiagnostic(first.line, first.column, "${track.channel} requires #FM3EXTEND ON"))
            } else if (track.channel == MmlChannelId.C && document.fm3Extended) {
                if (containsSoundEvent(track)) {
                    val first = track.commands.first { it is MmlCommand.Note || it is MmlCommand.Chord || it is MmlCommand.Portamento || it is MmlCommand.Drum }
                    diagnostics.add(MmlDiagnostic(first.line, first.column, "Channel C supplies FM3 patch/control data while #FM3EXTEND is ON; write notes on C1-C4"))
                }
                compileV2Track(track, document, barTicks, channelCPatch, instruments, builder, diagnostics, fm3ControlLane = true)
            } else {
                compileV2Track(track, document, barTicks, channelCPatch, instruments, builder, diagnostics)
            }
            trackIndex++
        }

        if (document.fm3Extended) {
            var hasFm3Notes = false
            var index = MmlChannelId.C1.ordinal
            while (index <= MmlChannelId.C4.ordinal) {
                if (document.tracks[index].commands.isNotEmpty()) hasFm3Notes = true
                index++
            }
            if (hasFm3Notes && channelCPatch < 0) {
                diagnostics.add(MmlDiagnostic(1, 1, "#FM3EXTEND requires a named FM instrument on channel C"))
            }
        }
        if (diagnostics.isNotEmpty()) return MmlCompileResult.Failure(diagnostics)

        val program = builder.build()
        if (document.fm3Extended) validateFm3SlotOwnership(program, diagnostics)
        if (diagnostics.isNotEmpty()) return MmlCompileResult.Failure(diagnostics)
        val requiredFmVoices = maximumConcurrentFmVoices(program)
        if (requiredFmVoices > AudioLaws.FM_RENDER_VOICES) {
            return MmlCompileResult.Failure(
                listOf(
                    MmlDiagnostic(
                        1,
                        1,
                        "Song requires $requiredFmVoices simultaneous FM voices; engine capacity is ${AudioLaws.FM_RENDER_VOICES}"
                    )
                )
            )
        }
        return MmlCompileResult.Success(
            ArrangementLanes(
                tempoBpm = document.bpm,
                keyRootMidi = DEFAULT_KEY_ROOT_MIDI,
                routing = ArrangementRouting.MML_LOGICAL_TRACKS,
                beatsPerBar = document.barNumerator,
                eqBands = document.eqBands.map { it.band },
                compiledOpnaSong = program
            ),
            sharedSsgWarnings(program)
        )
    }

    private fun sharedSsgWarnings(program: CompiledOpnaSong): List<MmlDiagnostic> {
        val warnings = mutableListOf<MmlDiagnostic>()
        var warnedEnvelope = false
        var warnedNoise = false
        var i = 0
        while (i < program.eventCount && (!warnedEnvelope || !warnedNoise)) {
            val type = program.eventType[i]
            val sharedNoise = type == CompiledOpnaSong.SSG_NOISE_PERIOD
            val sharedEnvelope = type == CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_PERIOD ||
                type == CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE
            if ((sharedNoise && !warnedNoise) || (sharedEnvelope && !warnedEnvelope)) {
                var j = i + 1
                while (j < program.eventCount) {
                    if (program.eventType[j] == type && program.startTick[j] == program.startTick[i] &&
                        program.channel[j] != program.channel[i] && program.stateValue[j] != program.stateValue[i]
                    ) {
                        val earlier = if (program.sourceOrder[i] <= program.sourceOrder[j]) i else j
                        val later = if (earlier == i) j else i
                        val firstPart = ssgPartName(program.channel[earlier])
                        val secondPart = ssgPartName(program.channel[later])
                        val locations = "$firstPart at ${program.sourceLine[earlier]}:${program.sourceColumn[earlier]} and " +
                            "$secondPart at ${program.sourceLine[later]}:${program.sourceColumn[later]}"
                        if (sharedNoise) {
                            warnings.add(
                                MmlDiagnostic(
                                    program.sourceLine[later],
                                    program.sourceColumn[later],
                                    "Shared SSG noise periods conflict between $locations; authored source order applies $secondPart last"
                                )
                            )
                            warnedNoise = true
                        } else {
                            warnings.add(
                                MmlDiagnostic(
                                    program.sourceLine[later],
                                    program.sourceColumn[later],
                                    "Shared SSG hardware envelopes conflict between $locations; authored source order applies $secondPart last"
                                )
                            )
                            warnedEnvelope = true
                        }
                        break
                    }
                    j++
                }
            }
            i++
        }
        return warnings
    }

    private fun ssgPartName(channel: Int): String =
        if (channel in 0..2) "part ${('G'.code + channel).toChar()}" else "SSG part $channel"

    private fun maximumConcurrentFmVoices(program: CompiledOpnaSong): Int {
        val normalChannelActive = BooleanArray(AudioLaws.FM_CHANNELS)
        var maximum = 0
        var boundaryIndex = 0
        while (boundaryIndex < program.eventCount) {
            val boundaryType = program.eventType[boundaryIndex]
            if (boundaryType == CompiledOpnaSong.FM_NOTE ||
                boundaryType == CompiledOpnaSong.FM_POLY_NOTE ||
                boundaryType == CompiledOpnaSong.FM3_OPERATOR_NOTE
            ) {
                var channel = 0
                while (channel < normalChannelActive.size) {
                    normalChannelActive[channel] = false
                    channel++
                }
                val tick = program.startTick[boundaryIndex]
                var polyphonic = 0
                var eventIndex = 0
                while (eventIndex < program.eventCount) {
                    val type = program.eventType[eventIndex]
                    val end = program.startTick[eventIndex] + program.gateTick[eventIndex].toLong()
                    if (program.startTick[eventIndex] <= tick && tick < end) {
                        if (type == CompiledOpnaSong.FM_POLY_NOTE) {
                            polyphonic++
                        } else if (type == CompiledOpnaSong.FM_NOTE) {
                            val eventChannel = program.channel[eventIndex]
                            if (eventChannel in normalChannelActive.indices) normalChannelActive[eventChannel] = true
                        } else if (type == CompiledOpnaSong.FM3_OPERATOR_NOTE) {
                            normalChannelActive[2] = true
                        }
                    }
                    eventIndex++
                }
                var active = polyphonic
                channel = 0
                while (channel < normalChannelActive.size) {
                    if (normalChannelActive[channel]) active++
                    channel++
                }
                if (active > maximum) maximum = active
            }
            boundaryIndex++
        }
        return maximum
    }

    private fun compileV2Track(
        track: MmlTrack,
        document: MmlDocument,
        barTicks: Int,
        channelCPatch: Int,
        instruments: SourceInstrumentLookup,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>,
        fm3ControlLane: Boolean = false
    ) {
        if (track.commands.isEmpty()) return
        val isRhythm = track.channel == MmlChannelId.R
        val isSsg = track.channel in MmlChannelId.G..MmlChannelId.I
        val isFm = track.channel in MmlChannelId.A..MmlChannelId.F
        val isFm3Operator = track.channel in MmlChannelId.C1..MmlChannelId.C4
        val isFmPart = isFm || isFm3Operator
        val logicalPart = if (isFm3Operator) {
            CompiledOpnaSong.FM3_PART_BASE + track.channel.ordinal - MmlChannelId.C1.ordinal
        } else CompiledOpnaSong.LOGICAL_PART_NONE
        val fmChannelIndex = if (isFm3Operator) 2 else track.channel.ordinal
        var octave = DEFAULT_OCTAVE
        var defaultLength = DEFAULT_LENGTH
        var fineVolume = 127
        val ticksPerPmdClock = TICKS_PER_QUARTER / document.pmdClocksPerQuarter
        val gateState = PmdGateState(track.channel.ordinal, ticksPerPmdClock)
        var patchId = if (isFm3Operator) channelCPatch else -1
        var fmSlotMask = if (isFm3Operator) 1 shl (track.channel.ordinal - MmlChannelId.C1.ordinal) else 15
        var pan = 0
        var detuneCents = 0
        var hasExplicitHardwareLfoDepth = false
        var polyphonicPart = false
        var sawSoftwareLfo = false
        var tick = 0L
        var sawEvent = false
        if (isSsg && document.envelopeClockMode != PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL) {
            builder.beginSource(Int.MAX_VALUE, 1, 1)
        }
        if (isSsg && document.envelopeClockMode != PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL &&
            !builder.addSsgEnvelopeMode(
                0L,
                track.channel.ordinal - MmlChannelId.G.ordinal,
                document.envelopeClockMode
            )
        ) {
            diagnostics.add(MmlDiagnostic(1, 1, "Compiled OPNA event capacity exceeded"))
        }
        var i = 0
        while (i < track.commands.size) {
            val command = track.commands[i]
            builder.beginSource(
                command.sourceOrder * SSG_SOURCE_ORDER_STRIDE + SSG_SOURCE_ORDER_STRIDE - 1,
                command.line,
                command.column
            )
            if (fm3ControlLane && isUnsupportedFm3ControlLaneCommand(command)) {
                diagnostics.add(
                    MmlDiagnostic(
                        command.line,
                        command.column,
                        "Command is part-local and cannot be authored on FM3 control lane C; use C1-C4"
                    )
                )
                i++
                continue
            }
            when (command) {
                is MmlCommand.Instrument -> {
                    if (isRhythm) {
                        if (command.value != "drum") diagnostics.add(MmlDiagnostic(command.line, command.column, "Channel R requires @drum"))
                    } else {
                        val sourceId = instruments.sourceIdForName(command.value)
                        val id = when {
                            isFmPart -> builder.internFmPatch(sourceId)
                            isSsg -> builder.internSsgPatch(sourceId)
                            else -> -1
                        }
                        val valid = id >= 0
                        if (!valid) diagnostics.add(MmlDiagnostic(command.line, command.column, "Instrument @${command.value} is invalid for channel ${track.channel}"))
                        else {
                            patchId = id
                            if (isFmPart && !isFm3Operator && !hasExplicitHardwareLfoDepth) {
                                val fmPatch = instruments.fmPatch(sourceId)
                                if (fmPatch != null) {
                                    addPatchHardwareLfoDefaults(
                                        fmPatch.pms, fmPatch.ams, tick, fmChannelIndex, logicalPart,
                                        command, builder, diagnostics
                                    )
                                }
                            }
                            if (isSsg) {
                                val patch = instruments.ssgPatch(sourceId)
                                if (patch != null) {
                                    expandSsgPatchState(
                                        patch,
                                        tick,
                                        track.channel.ordinal - MmlChannelId.G.ordinal,
                                        command,
                                        builder,
                                        diagnostics
                                    )
                                }
                            }
                            if (isFm3Operator) {
                                if (!builder.addFm3Patch(tick, fmSlotMask, id, logicalPart)) {
                                    diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                                }
                            } else if (fm3ControlLane) {
                                if (!builder.addFm3Patch(tick, fmSlotMask, id)) {
                                    diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                                }
                            }
                        }
                    }
                }
                is MmlCommand.Volume -> {
                    if (command.value !in 0..15) diagnostics.add(MmlDiagnostic(command.line, command.column, "Volume must be v0..v15"))
                    else {
                        fineVolume = (command.value * 127 + 7) / 15
                        if (isFm3Operator && !builder.addFmPartControl(CompiledOpnaSong.FM_PART_VOLUME, tick, logicalPart, fineVolume)) {
                            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        }
                    }
                }
                is MmlCommand.FineVolume -> {
                    if (command.value !in 0..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Fine volume must be V0..V127"))
                    else {
                        fineVolume = command.value
                        if (isFm3Operator && !builder.addFmPartControl(CompiledOpnaSong.FM_PART_VOLUME, tick, logicalPart, fineVolume)) {
                            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        }
                    }
                }
                is MmlCommand.RelativeVolume -> {
                    fineVolume = (fineVolume + command.delta).coerceIn(0, 127)
                    if (isFm3Operator && !builder.addFmPartControl(CompiledOpnaSong.FM_PART_VOLUME, tick, logicalPart, fineVolume)) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    }
                }
                is MmlCommand.Octave -> {
                    if (command.value !in 0..8) diagnostics.add(MmlDiagnostic(command.line, command.column, "Octave must be o0..o8"))
                    else octave = command.value
                }
                is MmlCommand.DefaultLength -> {
                    if (!isValidV2Length(command.denominator)) diagnostics.add(MmlDiagnostic(command.line, command.column, "Length must divide the 1/480-quarter tick grid"))
                    else defaultLength = command.denominator
                }
                is MmlCommand.OctaveShift -> {
                    val shifted = octave + command.delta
                    if (shifted !in 0..8) diagnostics.add(MmlDiagnostic(command.line, command.column, "Octave shift leaves o0..o8"))
                    else octave = shifted
                }
                is MmlCommand.Gate -> {
                    val maximum = command.scale - 1 + if (command.scale == 8) 1 else 0
                    if (command.value !in 0..maximum) {
                        val syntax = if (command.scale == 8) "Q0..Q8" else "Q%0..Q%255"
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Gate must be $syntax"))
                    } else {
                        gateState.setProportional(command.value, command.scale)
                    }
                }
                is MmlCommand.GateTail -> {
                    val from = command.fromClocks
                    val to = command.toClocks
                    val minimum = command.minimumClocks
                    if ((from != null && from !in 0..255) ||
                        (to != null && to !in 0..255) ||
                        (minimum != null && minimum !in 0..255) ||
                        (from != null && to != null && kotlin.math.abs(from - to) > 127)
                    ) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "q clocks must be 0..255 with a random range no wider than 127"))
                    } else {
                        gateState.updateTail(from, to, minimum)
                    }
                }
                is MmlCommand.SoftwareEnvelope -> {
                    if (!isSsg) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "E software envelopes are only valid on SSG channels G-I"))
                    } else if (!isValidSoftwareEnvelope(command)) {
                        val reason = if (command.format == 1) {
                            "Legacy E requires AL 0..255, DD -15..15, SR 0..255, and RR 0..255"
                        } else {
                            "Extended E requires AR/DR/SR 0..31 and RR/SL/attack level 0..15"
                        }
                        diagnostics.add(MmlDiagnostic(command.line, command.column, reason))
                    } else if (!builder.addSsgEnvelopeDefinition(
                            tick,
                            track.channel.ordinal - MmlChannelId.G.ordinal,
                            command.format,
                            command.attack,
                            command.decay,
                            command.sustain,
                            command.release,
                            command.sustainLevel,
                            command.attackLevel
                        )
                    ) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    }
                }
                is MmlCommand.EnvelopeClockMode -> {
                    if (!isSsg) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "EX0/EX1 envelope modes are only valid on SSG channels G-I"))
                    } else if (!builder.addSsgEnvelopeMode(
                            tick,
                            track.channel.ordinal - MmlChannelId.G.ordinal,
                            command.mode
                        )
                    ) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    }
                }
                is MmlCommand.SsgToneEnable -> addSsgSemanticState(
                    isSsg, command, CompiledOpnaSong.SSG_TONE_ENABLE,
                    if (command.enabled) 1 else 0, 0..1, tick, track, builder, diagnostics
                )
                is MmlCommand.SsgNoiseEnable -> addSsgSemanticState(
                    isSsg, command, CompiledOpnaSong.SSG_NOISE_ENABLE,
                    if (command.enabled) 1 else 0, 0..1, tick, track, builder, diagnostics
                )
                is MmlCommand.SsgNoisePeriod -> addSsgSemanticState(
                    isSsg, command, CompiledOpnaSong.SSG_NOISE_PERIOD,
                    command.period, 1..31, tick, track, builder, diagnostics
                )
                is MmlCommand.SsgHardwareEnvelopePeriod -> addSsgSemanticState(
                    isSsg, command, CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_PERIOD,
                    command.period, 1..65_535, tick, track, builder, diagnostics
                )
                is MmlCommand.SsgHardwareEnvelopeShape -> addSsgSemanticState(
                    isSsg, command, CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE,
                    command.shape, 0..15, tick, track, builder, diagnostics
                )
                is MmlCommand.Pan -> {
                    pan = when (command.value) {
                        1 -> 2 // PMD/OPNA: right
                        2 -> 1 // PMD/OPNA: left
                        3 -> 0 // center
                        else -> {
                            diagnostics.add(MmlDiagnostic(command.line, command.column, "Pan must be p1, p2, or p3"))
                            pan
                        }
                    }
                }
                is MmlCommand.Polyphony -> {
                    if (!isFm) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "P0/P1 polyphony is only valid on FM channels A-F"))
                    } else if (command.enabled && sawSoftwareLfo) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "P1 cannot be combined with PMD software LFO controls"))
                    } else {
                        polyphonicPart = command.enabled
                    }
                }
                is MmlCommand.Detune -> {
                    if (command.cents !in -1_200..1_200) diagnostics.add(MmlDiagnostic(command.line, command.column, "Detune must be between -1200 and +1200 cents"))
                    else detuneCents = command.cents
                }
                is MmlCommand.FmSlotMask -> {
                    if (!isFmPart || command.mask !in 0..15) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "s requires an FM part and slot mask 0..15"))
                    } else {
                        fmSlotMask = command.mask
                        if (isFm3Operator && !builder.addFmPartControl(
                                CompiledOpnaSong.FM_PART_SLOT_MASK, tick, logicalPart, fmSlotMask
                            )) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    }
                }
                is MmlCommand.FmSlotDetune -> {
                    if ((!isFm3Operator && !fm3ControlLane) || command.mask !in 1..15 || command.value !in -32_768..32_767) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "sd/sdd requires an FM3 extended part, slot mask 1..15, and value -32768..32767"))
                    } else if (!builder.addFmControl(
                            if (command.relative) CompiledOpnaSong.FM_SLOT_DETUNE_RELATIVE else CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE,
                            tick, 2, command.mask, command.value, logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.FmOperatorTl -> {
                    val validValue = if (command.relative) command.value in -128..127 else command.value in 0..127
                    if (!isFmPart || command.mask !in 1..15 || !validValue) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "O requires an FM part, slot mask 1..15, and valid TL value"))
                    } else if (!builder.addFmControl(
                            if (command.relative) CompiledOpnaSong.FM_TL_RELATIVE else CompiledOpnaSong.FM_TL_ABSOLUTE,
                            tick, fmChannelIndex, command.mask, command.value, logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.FmFeedback -> {
                    val validValue = if (command.relative) command.value in -7..7 else command.value in 0..7
                    if (!isFmPart || !validValue) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "FB requires an FM part and feedback 0..7 or relative -7..+7"))
                    } else if (!builder.addFmControl(
                            if (command.relative) CompiledOpnaSong.FM_FEEDBACK_RELATIVE else CompiledOpnaSong.FM_FEEDBACK_ABSOLUTE,
                            tick, fmChannelIndex, 0, command.value, logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.FmSlotKeyOnDelay -> {
                    val delayTicks = if (command.lengthDenominator != null) {
                        durationTicksV2(command.lengthDenominator, command.dotted, defaultLength, command, diagnostics)
                    } else {
                        command.delay * ticksPerPmdClock
                    }
                    if (!isFmPart || command.mask !in 0..15 || command.delay !in 0..255) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "sk requires an FM part, slot mask 0..15, and delay 0..255"))
                    } else if (!builder.addFmControl(
                            CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY, tick, fmChannelIndex, command.mask, delayTicks, logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmShot -> {
                    if (!builder.addRhythmControl(
                            if (command.dump) CompiledOpnaSong.RHYTHM_CONTROL_DUMP else CompiledOpnaSong.RHYTHM_CONTROL_SHOT,
                            tick, -1, command.voiceMask, 0
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmMasterLevel -> {
                    val valid = if (command.relative) command.value in -63..63 else command.value in 0..63
                    if (!valid) diagnostics.add(MmlDiagnostic(command.line, command.column, "\\V rhythm master level must be 0..63 or a relative value within that range"))
                    else if (!builder.addRhythmControl(
                            if (command.relative) CompiledOpnaSong.RHYTHM_MASTER_RELATIVE else CompiledOpnaSong.RHYTHM_MASTER_ABSOLUTE,
                            tick, -1, 0, command.value
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmVoiceLevel -> {
                    val valid = command.voice in 0..5 && if (command.relative) command.value in -31..31 else command.value in 0..31
                    if (!valid) diagnostics.add(MmlDiagnostic(command.line, command.column, "Rhythm voice level must select b/s/c/h/t/i and level 0..31"))
                    else if (!builder.addRhythmControl(
                            if (command.relative) CompiledOpnaSong.RHYTHM_VOICE_LEVEL_RELATIVE else CompiledOpnaSong.RHYTHM_VOICE_LEVEL_ABSOLUTE,
                            tick, command.voice, 0, command.value
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmVoicePan -> {
                    if (command.voice !in 0..5 || command.pan !in 0..2) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Rhythm pan must select a valid voice and left/center/right"))
                    } else if (!builder.addRhythmControl(
                            CompiledOpnaSong.RHYTHM_VOICE_PAN, tick, command.voice, 0, command.pan
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmPatternSelect -> {
                    diagnostics.add(MmlDiagnostic(command.line, command.column, "R pattern selection is only valid on K"))
                }
                is MmlCommand.Tempo -> {
                    if (command.bpm !in 18..400) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Tempo must be T18..T400"))
                    } else if (!builder.addTempo(tick, command.bpm.toFloat(), command.bpm * 1_000)) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Tempo-change capacity exceeded"))
                    }
                }
                is MmlCommand.HardwareLfoGlobal -> {
                    val selectedRate = command.rate
                    if (!isFm || isFm3Operator) {
                        diagnostics.add(
                            MmlDiagnostic(
                                command.line,
                                command.column,
                                "OPNA # hardware LFO control must be authored on physical FM channel A-F"
                            )
                        )
                    } else if (selectedRate != null && selectedRate !in 0..7) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "OPNA hardware LFO rate must be 0..7"))
                    } else {
                        val sourceBase = command.sourceOrder * SSG_SOURCE_ORDER_STRIDE
                        if (selectedRate != null && !builder.addHardwareLfoGlobalControl(
                                CompiledOpnaSong.HW_LFO_RATE,
                                tick,
                                selectedRate,
                                sourceBase,
                                command.line,
                                command.column
                            )
                        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        if (!builder.addHardwareLfoGlobalControl(
                                CompiledOpnaSong.HW_LFO_ENABLE,
                                tick,
                                if (command.enabled) 1 else 0,
                                sourceBase + 1,
                                command.line,
                                command.column
                            )
                        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    }
                }
                is MmlCommand.HardwareLfo -> {
                    if (!isFmPart) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Hardware LFO is only valid on FM channels"))
                    } else if (isFm3Operator) {
                        diagnostics.add(
                            MmlDiagnostic(
                                command.line,
                                command.column,
                                "FM3 PMS/AMS/delay are shared physical-channel state; author H on channel C, not C1-C4"
                            )
                        )
                    } else if (command.pms !in 0..7 || command.ams !in 0..3) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "H requires PMS 0..7 and AMS 0..3"))
                    } else if (!isValidHardwareLfoDelay(command)) {
                        diagnostics.add(
                            MmlDiagnostic(
                                command.line,
                                command.column,
                                "H delay requires raw clocks 0..255 or lLength 1..255 on the compiler tick grid"
                            )
                        )
                    } else {
                        hasExplicitHardwareLfoDepth = true
                        val sourceBase = command.sourceOrder * SSG_SOURCE_ORDER_STRIDE
                        if (!builder.addHardwareLfoPartControl(
                                CompiledOpnaSong.HW_LFO_PMS, tick, fmChannelIndex, logicalPart,
                                command.pms, CompiledOpnaSong.HW_LFO_DELAY_NONE, 0, false,
                                sourceBase, command.line, command.column
                            )
                        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        if (!builder.addHardwareLfoPartControl(
                                CompiledOpnaSong.HW_LFO_AMS, tick, fmChannelIndex, logicalPart,
                                command.ams, CompiledOpnaSong.HW_LFO_DELAY_NONE, 0, false,
                                sourceBase + 1, command.line, command.column
                            )
                        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        if (command.delayKind != MmlCommand.HARDWARE_LFO_DELAY_NONE) {
                            val delayKind = if (command.delayKind == MmlCommand.HARDWARE_LFO_DELAY_RAW_CLOCKS) {
                                CompiledOpnaSong.HW_LFO_DELAY_RAW_CLOCKS
                            } else {
                                CompiledOpnaSong.HW_LFO_DELAY_NOTE_LENGTH
                            }
                            if (!builder.addHardwareLfoPartControl(
                                    CompiledOpnaSong.HW_LFO_DELAY, tick, fmChannelIndex, logicalPart,
                                    0, delayKind, command.delayValue, command.delayDotted,
                                    sourceBase + 2, command.line, command.column
                                )
                            ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        }
                    }
                }
                is MmlCommand.SoftwareLfoDefine -> {
                    sawSoftwareLfo = true
                    if (polyphonicPart) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "PMD software LFO controls cannot follow P1"))
                    } else if (!isFmPart && !isSsg) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Software LFO is only valid on FM/SSG parts"))
                    } else if (command.delay !in 0..255 || command.speed !in 0..255 ||
                        command.depthA !in -128..127 || command.depthB !in 0..255
                    ) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "M requires delay/speed/depthB 0..255 and depthA -128..127"))
                    } else if (!builder.addSoftwareLfoControl(
                            CompiledOpnaSong.SOFTWARE_LFO_DEFINE, tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) 1 else 0, command.index,
                            command.delay, command.speed, command.depthA, command.depthB,
                            logicalPartId = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoSwitch -> {
                    sawSoftwareLfo = true
                    if (polyphonicPart) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "PMD software LFO controls cannot follow P1"))
                    } else if ((!isFmPart && !isSsg) || command.value !in 0..7) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "* requires an FM/SSG part and switch 0..7"))
                    } else if (!builder.addSoftwareLfoControl(
                            CompiledOpnaSong.SOFTWARE_LFO_SWITCH, tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) 1 else 0, command.index, command.value,
                            logicalPartId = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoWaveform -> {
                    sawSoftwareLfo = true
                    if (polyphonicPart) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "PMD software LFO controls cannot follow P1"))
                    } else if ((!isFmPart && !isSsg) || command.value !in 0..6) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "MW requires an FM/SSG part and waveform 0..6"))
                    } else if (!builder.addSoftwareLfoControl(
                            CompiledOpnaSong.SOFTWARE_LFO_WAVE, tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) 1 else 0, command.index, command.value,
                            logicalPartId = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoClockMode -> {
                    sawSoftwareLfo = true
                    if (polyphonicPart) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "PMD software LFO controls cannot follow P1"))
                    } else if ((!isFmPart && !isSsg) || command.value !in 0..1) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "MX requires an FM/SSG part and clock mode 0..1"))
                    } else if (!builder.addSoftwareLfoControl(
                            CompiledOpnaSong.SOFTWARE_LFO_CLOCK, tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) 1 else 0, command.index, command.value,
                            logicalPartId = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoTlMask -> {
                    sawSoftwareLfo = true
                    if (polyphonicPart) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "PMD software LFO controls cannot follow P1"))
                    } else if (!isFmPart || command.value !in 0..15) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "MM requires an FM part and TL slot mask 0..15"))
                    } else if (!builder.addSoftwareLfoControl(
                            CompiledOpnaSong.SOFTWARE_LFO_TL_MASK, tick, fmChannelIndex,
                            0, command.index, command.value,
                            logicalPartId = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoDepthEvolution -> {
                    sawSoftwareLfo = true
                    if (polyphonicPart) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "PMD software LFO controls cannot follow P1"))
                    } else if ((!isFmPart && !isSsg) || command.speed !in 0..255 ||
                        command.depth !in -128..127 || command.time !in 0..127
                    ) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "MD requires speed 0..255, depth -128..127, and time 0..127"))
                    } else if (!builder.addSoftwareLfoControl(
                            CompiledOpnaSong.SOFTWARE_LFO_DEPTH, tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) 1 else 0, command.index,
                            command.speed, command.depth, command.time,
                            logicalPartId = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.Note -> {
                    sawEvent = true
                    if (isRhythm) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Pitched notes are not allowed on channel R"))
                    } else {
                        var totalDuration = durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                        val midi = midiFor(command.letter, command.accidental, octave)
                        var link = command.link
                        var finalIndex = i
                        while (link == MmlCommand.LINK_TIE && finalIndex + 1 < track.commands.size) {
                            val next = track.commands[finalIndex + 1] as? MmlCommand.Note
                            if (next == null || midiFor(next.letter, next.accidental, octave) != midi) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "A tie must immediately join the same pitch"))
                                break
                            }
                            totalDuration += durationTicksV2(next.denominator, next.dotted, defaultLength, next, diagnostics)
                            link = next.link
                            finalIndex++
                        }
                        if (midi !in 0..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Note is outside MIDI range 0..127"))
                        else if (patchId < 0) diagnostics.add(MmlDiagnostic(command.line, command.column, "Channel ${track.channel} requires a named instrument before its first note"))
                        else if (totalDuration > 0) {
                            val gate = gateState.resolve(totalDuration, link == MmlCommand.LINK_SLUR)
                            val type = when {
                                isFm3Operator -> CompiledOpnaSong.FM3_OPERATOR_NOTE
                                isSsg -> CompiledOpnaSong.SSG_NOTE
                                polyphonicPart -> CompiledOpnaSong.FM_POLY_NOTE
                                else -> CompiledOpnaSong.FM_NOTE
                            }
                            val channelIndex = when {
                                isFm3Operator -> 2
                                isSsg -> track.channel.ordinal - MmlChannelId.G.ordinal
                                else -> track.channel.ordinal
                            }
                            if (!builder.add(
                                    type, tick, totalDuration, gate, channelIndex, if (isFm3Operator) 0 else -1, midi, -1,
                                    fineVolume, patchId, pan, detuneCents,
                                    gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks,
                                    if (isFm3Operator) fmSlotMask else 0,
                                    logicalPart
                                )
                            ) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                            }
                            tick += totalDuration
                        }
                        i = finalIndex
                    }
                }
                is MmlCommand.Chord -> {
                    sawEvent = true
                    if (!isFm) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Polyphonic chords are only valid on FM channels A-F"))
                    } else {
                        val duration = durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                        if (patchId < 0) {
                            diagnostics.add(MmlDiagnostic(command.line, command.column, "A chord requires a named FM instrument"))
                        } else if (duration > 0) {
                            val gate = gateState.resolve(duration, false)
                            var pitchIndex = 0
                            while (pitchIndex < command.pitches.size) {
                                val pitch = command.pitches[pitchIndex]
                                val midi = midiFor(pitch.first, pitch.second, octave)
                                if (midi !in 0..127) {
                                    diagnostics.add(MmlDiagnostic(command.line, command.column, "Chord note is outside MIDI range 0..127"))
                                } else if (!builder.add(
                                        CompiledOpnaSong.FM_POLY_NOTE,
                                        tick,
                                        duration,
                                        gate,
                                        track.channel.ordinal,
                                        -1,
                                        midi,
                                        -1,
                                        fineVolume,
                                        patchId,
                                        pan,
                                        detuneCents,
                                        gateState.proportionalValue,
                                        gateState.proportionalScale,
                                        gateState.lastResolvedTailClocks,
                                        gateState.minimumClocks
                                    )
                                ) {
                                    diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                                }
                                pitchIndex++
                            }
                            tick += duration
                        }
                    }
                }
                is MmlCommand.Portamento -> {
                    sawEvent = true
                    if (isRhythm) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Portamento is not valid on channel R"))
                    } else {
                        val duration = durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                        val fromMidi = midiFor(command.fromLetter, command.fromAccidental, octave)
                        val toMidi = midiFor(command.toLetter, command.toAccidental, octave)
                        if (patchId < 0) diagnostics.add(MmlDiagnostic(command.line, command.column, "Portamento requires a named instrument"))
                        else if (duration > 0) {
                            val type = if (isSsg) CompiledOpnaSong.SSG_NOTE else if (isFm3Operator) CompiledOpnaSong.FM3_OPERATOR_NOTE else CompiledOpnaSong.FM_NOTE
                            val channelIndex = if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else if (isFm3Operator) 2 else track.channel.ordinal
                            val gate = gateState.resolve(duration, false)
                            if (!builder.add(
                                    type, tick, duration, gate, channelIndex, if (isFm3Operator) 0 else -1, fromMidi, toMidi,
                                    fineVolume, patchId, pan, detuneCents,
                                    gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks,
                                    if (isFm3Operator) fmSlotMask else 0,
                                    logicalPart
                                )
                            ) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event safety limit exceeded"))
                            }
                            tick += duration
                        }
                    }
                }
                is MmlCommand.Rest -> {
                    sawEvent = true
                    tick += durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                }
                is MmlCommand.Drum -> {
                    sawEvent = true
                    if (!isRhythm) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Drum tokens are only allowed on channel R"))
                    } else {
                        val duration = durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                        val kind = drumKind(command.kind)
                        if (duration > 0 && kind != null) {
                            if (!builder.add(CompiledOpnaSong.RHYTHM_SHOT, tick, duration, 0, 0, -1, kind.ordinal, -1, fineVolume, -1, pan, 0)) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event safety limit exceeded"))
                            }
                            tick += duration
                        }
                    }
                }
                is MmlCommand.Bar -> {
                    if (tick == 0L || tick % barTicks != 0L) diagnostics.add(MmlDiagnostic(command.line, command.column, "Bar line does not fall on a complete #BAR boundary"))
                }
            }
            i++
        }
        if (sawEvent && tick % barTicks != 0L) {
            val last = track.commands[track.commands.size - 1]
            diagnostics.add(MmlDiagnostic(last.line, last.column, "Final bar is incomplete"))
        }
    }

    private fun addInitialHardwareLfoState(
        document: MmlDocument,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        val rate = if (document.lfoRate in 0..7) document.lfoRate else 0
        if (!builder.addHardwareLfoGlobalControl(
                CompiledOpnaSong.HW_LFO_RATE, 0L, rate,
                Int.MIN_VALUE, 1, 1
            )
        ) diagnostics.add(MmlDiagnostic(1, 1, "Compiled OPNA event capacity exceeded"))
        if (!builder.addHardwareLfoGlobalControl(
                CompiledOpnaSong.HW_LFO_ENABLE, 0L,
                if (document.lfoRate in 0..7) 1 else 0,
                Int.MIN_VALUE + 1, 1, 1
            )
        ) diagnostics.add(MmlDiagnostic(1, 1, "Compiled OPNA event capacity exceeded"))
    }

    private fun addPatchHardwareLfoDefaults(
        pms: Int,
        ams: Int,
        tick: Long,
        channel: Int,
        logicalPart: Int,
        command: MmlCommand.Instrument,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        val sourceBase = command.sourceOrder * SSG_SOURCE_ORDER_STRIDE
        if (!builder.addHardwareLfoPartControl(
                CompiledOpnaSong.HW_LFO_PMS, tick, channel, logicalPart,
                pms.coerceIn(0, 7), CompiledOpnaSong.HW_LFO_DELAY_NONE, 0, false,
                sourceBase, command.line, command.column
            )
        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        if (!builder.addHardwareLfoPartControl(
                CompiledOpnaSong.HW_LFO_AMS, tick, channel, logicalPart,
                ams.coerceIn(0, 3), CompiledOpnaSong.HW_LFO_DELAY_NONE, 0, false,
                sourceBase + 1, command.line, command.column
            )
        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
    }

    private fun isValidHardwareLfoDelay(command: MmlCommand.HardwareLfo): Boolean = when (command.delayKind) {
        MmlCommand.HARDWARE_LFO_DELAY_NONE -> command.delayValue == 0 && !command.delayDotted
        MmlCommand.HARDWARE_LFO_DELAY_RAW_CLOCKS -> command.delayValue in 0..255 && !command.delayDotted
        MmlCommand.HARDWARE_LFO_DELAY_NOTE_LENGTH ->
            command.delayValue in 1..255 && WHOLE_NOTE_TICKS % command.delayValue == 0
        else -> false
    }

    private fun isValidSoftwareEnvelope(command: MmlCommand.SoftwareEnvelope): Boolean {
        return if (command.format == 1) {
            command.attack in 0..255 && command.decay in -15..15 &&
                command.sustain in 0..255 && command.release in 0..255
        } else {
            command.format == 2 && command.attack in 0..31 && command.decay in 0..31 &&
                command.sustain in 0..31 && command.release in 0..15 &&
                command.sustainLevel in 0..15 && command.attackLevel in 0..15
        }
    }

    private fun expandSsgPatchState(
        patch: SsgPatch,
        tick: Long,
        channel: Int,
        command: MmlCommand.Instrument,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        var subOrder = 0
        fun add(type: Int, value: Int) {
            if (!builder.addSsgHardwareState(
                    type, tick, channel, value, command.sourceOrder * SSG_SOURCE_ORDER_STRIDE + subOrder,
                    command.line, command.column
                )
            ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
            subOrder++
        }
        add(CompiledOpnaSong.SSG_TONE_ENABLE, if (patch.toneEnabled) 1 else 0)
        add(CompiledOpnaSong.SSG_NOISE_ENABLE, if (patch.noiseEnabled) 1 else 0)
        if (patch.noiseEnabled) add(CompiledOpnaSong.SSG_NOISE_PERIOD, patch.noisePeriod)
        if (patch.envelopeEnabled) {
            add(CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_PERIOD, patch.envelopePeriod)
            add(CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE, patch.envelopeShape)
        }
    }

    private fun addSsgSemanticState(
        isSsg: Boolean,
        command: MmlCommand,
        type: Int,
        value: Int,
        validRange: IntRange,
        tick: Long,
        track: MmlTrack,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        if (!isSsg) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG hardware state commands are only valid on channels G-I"))
        } else if (value !in validRange) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG hardware state value must be ${validRange.first}..${validRange.last}"))
        } else if (!builder.addSsgHardwareState(
                type,
                tick,
                track.channel.ordinal - MmlChannelId.G.ordinal,
                value,
                command.sourceOrder * SSG_SOURCE_ORDER_STRIDE,
                command.line,
                command.column
            )
        ) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        }
    }

    private fun firstFmPatch(
        track: MmlTrack,
        instruments: SourceInstrumentLookup,
        builder: CompiledOpnaSongBuilder
    ): Int {
        var i = 0
        while (i < track.commands.size) {
            val command = track.commands[i]
            if (command is MmlCommand.Instrument) {
                val sourceId = instruments.sourceIdForName(command.value)
                if (instruments.fmPatch(sourceId) != null) return builder.internFmPatch(sourceId)
            }
            i++
        }
        return -1
    }

    private fun containsSoundEvent(track: MmlTrack): Boolean {
        var i = 0
        while (i < track.commands.size) {
            val command = track.commands[i]
            if (command is MmlCommand.Note || command is MmlCommand.Chord || command is MmlCommand.Portamento || command is MmlCommand.Drum) return true
            i++
        }
        return false
    }

    private fun validateFm3SlotOwnership(program: CompiledOpnaSong, diagnostics: MutableList<MmlDiagnostic>) {
        var firstPart = 0
        while (firstPart < CompiledOpnaSong.FM3_PART_COUNT) {
            var secondPart = firstPart + 1
            while (secondPart < CompiledOpnaSong.FM3_PART_COUNT) {
                if (partsOverlap(program, firstPart, secondPart)) {
                    diagnostics.add(
                        MmlDiagnostic(
                            1,
                            1,
                            "FM3 slot ownership overlaps another active extended part"
                        )
                    )
                    return
                }
                secondPart++
            }
            firstPart++
        }
    }

    private fun partsOverlap(program: CompiledOpnaSong, firstPart: Int, secondPart: Int): Boolean {
        val firstLogicalPart = CompiledOpnaSong.FM3_PART_BASE + firstPart
        val secondLogicalPart = CompiledOpnaSong.FM3_PART_BASE + secondPart
        var first = nextFm3Note(program, 0, firstLogicalPart)
        var second = nextFm3Note(program, 0, secondLogicalPart)
        while (first >= 0 && second >= 0) {
            val firstStart = program.startTick[first]
            val secondStart = program.startTick[second]
            val firstEnd = firstStart + program.durationTick[first].toLong()
            val secondEnd = secondStart + program.durationTick[second].toLong()
            if (firstStart < secondEnd && secondStart < firstEnd &&
                (program.slotMask[first] and program.slotMask[second]) != 0
            ) return true
            if (firstEnd <= secondEnd) first = nextFm3Note(program, first + 1, firstLogicalPart)
            if (secondEnd <= firstEnd) second = nextFm3Note(program, second + 1, secondLogicalPart)
        }
        return false
    }

    private fun nextFm3Note(program: CompiledOpnaSong, start: Int, logicalPart: Int): Int {
        var index = start
        while (index < program.eventCount) {
            if (program.eventType[index] == CompiledOpnaSong.FM3_OPERATOR_NOTE &&
                program.logicalPart[index] == logicalPart
            ) return index
            index++
        }
        return -1
    }

    private fun isUnsupportedFm3ControlLaneCommand(command: MmlCommand): Boolean =
        command is MmlCommand.Volume || command is MmlCommand.FineVolume ||
            command is MmlCommand.RelativeVolume || command is MmlCommand.Octave ||
            command is MmlCommand.OctaveShift || command is MmlCommand.Gate ||
            command is MmlCommand.GateTail || command is MmlCommand.Pan ||
            command is MmlCommand.Polyphony || command is MmlCommand.Detune ||
            command is MmlCommand.SoftwareLfoDefine ||
            command is MmlCommand.SoftwareLfoSwitch || command is MmlCommand.SoftwareLfoWaveform ||
            command is MmlCommand.SoftwareLfoClockMode || command is MmlCommand.SoftwareLfoTlMask ||
            command is MmlCommand.SoftwareLfoDepthEvolution

    private fun compileRhythmSequence(
        document: MmlDocument,
        barTicks: Int,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        val track = document.tracks[MmlChannelId.K.ordinal]
        if (track.commands.isEmpty()) return
        var tick = 0L
        var defaultLength = DEFAULT_LENGTH
        var sawEvent = false
        var commandIndex = 0
        while (commandIndex < track.commands.size) {
            val command = track.commands[commandIndex]
            builder.beginSource(
                command.sourceOrder * SSG_SOURCE_ORDER_STRIDE + SSG_SOURCE_ORDER_STRIDE - 1,
                command.line,
                command.column
            )
            when (command) {
                is MmlCommand.RhythmPatternSelect -> {
                    val pattern = document.rhythmPatterns.firstOrNull { it.id == command.pattern }
                    if (pattern == null) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "K part selects undefined rhythm pattern R${command.pattern}"))
                    } else {
                        tick += compileRhythmPattern(pattern, tick, defaultLength, builder, diagnostics)
                        sawEvent = true
                    }
                }
                is MmlCommand.DefaultLength -> {
                    if (!isValidV2Length(command.denominator)) diagnostics.add(MmlDiagnostic(command.line, command.column, "Length must divide the 1/480-quarter tick grid"))
                    else defaultLength = command.denominator
                }
                is MmlCommand.Rest -> {
                    tick += durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                    sawEvent = true
                }
                is MmlCommand.Bar -> if (tick == 0L || tick % barTicks != 0L) {
                    diagnostics.add(MmlDiagnostic(command.line, command.column, "K-part bar line does not fall on a complete #BAR boundary"))
                }
                is MmlCommand.RhythmShot,
                is MmlCommand.RhythmMasterLevel,
                is MmlCommand.RhythmVoiceLevel,
                is MmlCommand.RhythmVoicePan -> diagnostics.add(
                    MmlDiagnostic(command.line, command.column, "YM2608 rhythm controls are not valid on PMD rhythm-selection part K; author them on bare R")
                )
                else -> diagnostics.add(MmlDiagnostic(command.line, command.column, "Command is not valid on PMD rhythm-selection part K"))
            }
            commandIndex++
        }
        if (sawEvent && tick % barTicks != 0L) {
            val last = track.commands[track.commands.lastIndex]
            diagnostics.add(MmlDiagnostic(last.line, last.column, "Final K-part bar is incomplete"))
        }
    }

    private fun compileRhythmPattern(
        pattern: MmlRhythmPattern,
        baseTick: Long,
        inheritedLength: Int,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ): Long {
        var tick = 0L
        var defaultLength = inheritedLength
        var velocity = 127
        var instrument = -1
        var commandIndex = 0
        while (commandIndex < pattern.commands.size) {
            val command = pattern.commands[commandIndex]
            builder.beginSource(
                command.sourceOrder * SSG_SOURCE_ORDER_STRIDE + SSG_SOURCE_ORDER_STRIDE - 1,
                command.line,
                command.column
            )
            when (command) {
                is MmlCommand.Instrument -> {
                    instrument = command.value.toIntOrNull() ?: -1
                    if (ssgDrumKind(instrument) < 0) diagnostics.add(MmlDiagnostic(command.line, command.column, "R${pattern.id} uses unsupported SSG drum instrument @${command.value}"))
                }
                is MmlCommand.DefaultLength -> {
                    if (!isValidV2Length(command.denominator)) diagnostics.add(MmlDiagnostic(command.line, command.column, "Length must divide the 1/480-quarter tick grid"))
                    else defaultLength = command.denominator
                }
                is MmlCommand.Volume -> {
                    if (command.value !in 0..15) diagnostics.add(MmlDiagnostic(command.line, command.column, "Volume must be v0..v15"))
                    else velocity = (command.value * 127 + 7) / 15
                }
                is MmlCommand.FineVolume -> {
                    if (command.value !in 0..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Fine volume must be V0..V127"))
                    else velocity = command.value
                }
                is MmlCommand.RelativeVolume -> velocity = (velocity + command.delta).coerceIn(0, 127)
                is MmlCommand.Note -> {
                    val duration = durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                    val kind = ssgDrumKind(instrument)
                    if (kind < 0) diagnostics.add(MmlDiagnostic(command.line, command.column, "R${pattern.id} requires a supported SSG drum @ instrument before a hit"))
                    else if (!builder.add(
                            CompiledOpnaSong.SSG_DRUM_SHOT, baseTick + tick, duration, 0,
                            2, -1, kind, -1, velocity, -1, 0, 0
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    tick += duration
                }
                is MmlCommand.Rest -> tick += durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                is MmlCommand.RhythmShot,
                is MmlCommand.RhythmMasterLevel,
                is MmlCommand.RhythmVoiceLevel,
                is MmlCommand.RhythmVoicePan -> diagnostics.add(
                    MmlDiagnostic(command.line, command.column, "YM2608 rhythm controls are not valid inside R${pattern.id}; author them on bare R")
                )
                is MmlCommand.Bar -> Unit
                else -> diagnostics.add(MmlDiagnostic(command.line, command.column, "Command is not valid in PMD rhythm definition R${pattern.id}"))
            }
            commandIndex++
        }
        return tick
    }

    private fun ssgDrumKind(instrument: Int): Int = when (instrument) {
        1 -> ProceduralDrums.DrumKind.KICK.ordinal
        2, 64 -> ProceduralDrums.DrumKind.SNARE.ordinal
        4, 8, 16 -> ProceduralDrums.DrumKind.TOM.ordinal
        32 -> ProceduralDrums.DrumKind.RIMSHOT.ordinal
        128, 256 -> ProceduralDrums.DrumKind.HAT.ordinal
        512, 1024 -> ProceduralDrums.DrumKind.CYMBAL.ordinal
        else -> -1
    }

    private fun durationTicksV2(
        value: Int?,
        dotted: Boolean,
        defaultValue: Int,
        command: MmlCommand,
        diagnostics: MutableList<MmlDiagnostic>
    ): Int {
        val denominator = value ?: defaultValue
        if (!isValidV2Length(denominator)) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "Length must divide the 1/480-quarter tick grid"))
            return 0
        }
        val base = WHOLE_NOTE_TICKS / denominator
        return if (dotted) base + base / 2 else base
    }

    private fun isValidV2Length(value: Int): Boolean = value > 0 && WHOLE_NOTE_TICKS % value == 0

    private fun drumKind(kind: Char): ProceduralDrums.DrumKind? = when (kind) {
        'k' -> ProceduralDrums.DrumKind.KICK
        's' -> ProceduralDrums.DrumKind.SNARE
        'h' -> ProceduralDrums.DrumKind.HAT
        't' -> ProceduralDrums.DrumKind.TOM
        'y' -> ProceduralDrums.DrumKind.CYMBAL
        'i' -> ProceduralDrums.DrumKind.RIMSHOT
        else -> null
    }

    private fun midiFor(letter: Char, accidental: Int, octave: Int): Int {
        val semitone = when (letter) {
            'c' -> 0
            'd' -> 2
            'e' -> 4
            'f' -> 5
            'g' -> 7
            'a' -> 9
            else -> 11
        }
        return (octave + 1) * 12 + semitone + accidental
    }

}
