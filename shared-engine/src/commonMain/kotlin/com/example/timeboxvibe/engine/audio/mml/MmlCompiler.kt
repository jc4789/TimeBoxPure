package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSongBuilder
import com.example.timeboxvibe.engine.audio.opna.FallbackSourceInstrumentLookup
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import com.example.timeboxvibe.engine.audio.opna.OpnPitch
import com.example.timeboxvibe.engine.audio.opna.PmdDetune
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
import com.example.timeboxvibe.engine.audio.opna.PmdPerformanceLaws
import com.example.timeboxvibe.engine.audio.opna.SourceInstrumentLookup
import com.example.timeboxvibe.engine.audio.opna.SsgPatch
import com.example.timeboxvibe.engine.audio.opna.SsgHardwareLaws
import com.example.timeboxvibe.engine.audio.AudioLaws
import kotlin.math.pow

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
    private const val AUTHORED_COMMAND_SUBORDER_COUNT = 8
    private const val SUBORDER_PRIMARY = 0
    private const val SUBORDER_SECONDARY = 1
    private const val SUBORDER_TERTIARY = 2
    private const val SUBORDER_FALLBACK = AUTHORED_COMMAND_SUBORDER_COUNT - 1
    private val FM_COARSE_VOLUME = intArrayOf(
        85, 87, 90, 93, 95, 98, 101, 103, 106,
        109, 111, 114, 117, 119, 122, 125, 127
    )

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
        compileStable(document, instruments)

    private fun compileStable(document: MmlDocument, instruments: SourceInstrumentLookup): MmlCompileResult {
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
                    val first = track.commands.first { it is MmlCommand.Note || it is MmlCommand.Portamento || it is MmlCommand.Drum }
                    diagnostics.add(MmlDiagnostic(first.line, first.column, "Channel C supplies FM3 patch/control data while #FM3EXTEND is ON; write notes on C1-C4"))
                }
                compileStableTrack(track, document, barTicks, channelCPatch, instruments, builder, diagnostics, fm3ControlLane = true)
            } else {
                compileStableTrack(track, document, barTicks, channelCPatch, instruments, builder, diagnostics)
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
        return MmlCompileResult.Success(
            ArrangementLanes(
                tempoBpm = document.bpm,
                keyRootMidi = DEFAULT_KEY_ROOT_MIDI,
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
            val type = program.authoredKind(i)
            val sharedNoise = type == CompiledOpnaSong.SSG_NOISE_PERIOD
            val sharedEnvelope = type == CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_PERIOD ||
                type == CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE
            if ((sharedNoise && !warnedNoise) || (sharedEnvelope && !warnedEnvelope)) {
                val firstPayload = program.authoredPayloadIndex(i)
                var j = i + 1
                while (j < program.eventCount) {
                    if (program.authoredKind(j) == type &&
                        program.authoredStartTick(j) == program.authoredStartTick(i) &&
                        program.eventChannel(j) != program.eventChannel(i)
                    ) {
                        val secondPayload = program.authoredPayloadIndex(j)
                        val sameValue = if (type == CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE) {
                            program.states.envelopeShapes.shape(secondPayload) ==
                                program.states.envelopeShapes.shape(firstPayload)
                        } else {
                            program.states.periods.period(secondPayload) == program.states.periods.period(firstPayload)
                        }
                        if (sameValue) {
                            j++
                            continue
                        }
                        val earlier = if (program.sourceOrder(i) <= program.sourceOrder(j)) i else j
                        val later = if (earlier == i) j else i
                        val firstPart = ssgPartName(program.eventChannel(earlier))
                        val secondPart = ssgPartName(program.eventChannel(later))
                        val locations = "$firstPart at ${program.sourceLine(earlier)}:${program.sourceColumn(earlier)} and " +
                            "$secondPart at ${program.sourceLine(later)}:${program.sourceColumn(later)}"
                        if (sharedNoise) {
                            warnings.add(
                                MmlDiagnostic(
                                    program.sourceLine(later),
                                    program.sourceColumn(later),
                                    "Shared SSG noise periods conflict between $locations; authored source order applies $secondPart last"
                                )
                            )
                            warnedNoise = true
                        } else {
                            warnings.add(
                                MmlDiagnostic(
                                    program.sourceLine(later),
                                    program.sourceColumn(later),
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

    private fun compileStableTrack(
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
        var fineVolume = if (isSsg) 15 else 127
        var coarseVolumeOffset = 0
        val ticksPerPmdClock = TICKS_PER_QUARTER / document.pmdClocksPerQuarter
        val gateState = PmdGateState(track.channel.ordinal, ticksPerPmdClock)
        var patchId = if (isFm3Operator) channelCPatch else -1
        var fmSlotMask = if (isFm3Operator) 1 shl (track.channel.ordinal - MmlChannelId.C1.ordinal) else 15
        var pan = 0
        var partDetune = PmdDetune.ZERO
        var masterDetune = PmdDetune.ZERO
        var hasExplicitHardwareLfoDepth = false
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
                authoredCommandOrder(command.sourceOrder, SUBORDER_FALLBACK),
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
                    val maximum = if (isFmPart) 16 else 15
                    if (command.value !in 0..maximum) {
                        val domain = if (isFmPart) "FM v0..v16" else "v0..v15"
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Volume must be $domain"))
                    } else {
                        fineVolume = when {
                            isFmPart -> (FM_COARSE_VOLUME[command.value] + coarseVolumeOffset).coerceIn(0, 127)
                            isSsg -> (command.value + coarseVolumeOffset).coerceIn(0, 15)
                            else -> (command.value * 127 + 7) / 15
                        }
                        addPersistentPartVolume(
                            isFmPart, isFm3Operator, isSsg, fmChannelIndex, logicalPart,
                            track.channel, tick, fineVolume, command, builder, diagnostics
                        )
                    }
                }
                is MmlCommand.FineVolume -> {
                    val maximum = if (isSsg) 15 else 127
                    if (command.value !in 0..maximum) diagnostics.add(
                        MmlDiagnostic(command.line, command.column, "Fine volume must be V0..V$maximum")
                    )
                    else {
                        fineVolume = command.value
                        addPersistentPartVolume(
                            isFmPart, isFm3Operator, isSsg, fmChannelIndex, logicalPart,
                            track.channel, tick, fineVolume, command, builder, diagnostics
                        )
                    }
                }
                is MmlCommand.RelativeVolume -> {
                    val step = when {
                        command.fineMode -> command.amount
                        isFmPart -> 4
                        else -> command.amount
                    }
                    val delta = if (command.increase) step else -step
                    val maximum = if (isSsg) 15 else 127
                    fineVolume = (fineVolume + delta).coerceIn(0, maximum)
                    addPersistentPartVolume(
                        isFmPart, isFm3Operator, isSsg, fmChannelIndex, logicalPart,
                        track.channel, tick, fineVolume, command, builder, diagnostics
                    )
                }
                is MmlCommand.FineVolumeOffset -> {
                    val maximum = if (isSsg) 15 else 127
                    fineVolume = (fineVolume + command.delta).coerceIn(0, maximum)
                    addPersistentPartVolume(
                        isFmPart, isFm3Operator, isSsg, fmChannelIndex, logicalPart,
                        track.channel, tick, fineVolume, command, builder, diagnostics
                    )
                }
                is MmlCommand.CoarseVolumeOffset -> {
                    val maximum = if (isSsg) 15 else 127
                    coarseVolumeOffset = (coarseVolumeOffset + command.delta).coerceIn(-maximum, maximum)
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
                is MmlCommand.SsgToneEnable -> addSsgToneEnableCommand(
                    isSsg, command, tick, track, builder, diagnostics)
                is MmlCommand.SsgNoiseEnable -> addSsgNoiseEnableCommand(
                    isSsg, command, tick, track, builder, diagnostics)
                is MmlCommand.SsgNoisePeriod -> addSsgNoisePeriodCommand(
                    isSsg, command, tick, track, builder, diagnostics)
                is MmlCommand.SsgHardwareEnvelopePeriod -> addSsgHardwareEnvelopePeriodCommand(
                    isSsg, command, tick, track, builder, diagnostics)
                is MmlCommand.SsgHardwareEnvelopeShape -> addSsgEnvelopeShapeCommand(
                    isSsg, command, tick, track, builder, diagnostics)
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
                is MmlCommand.PartDetuneAbsolute,
                is MmlCommand.PartDetuneRelative,
                is MmlCommand.MasterDetuneAbsolute -> {
                    val operand = when (command) {
                        is MmlCommand.PartDetuneAbsolute -> command.value
                        is MmlCommand.PartDetuneRelative -> command.value
                        is MmlCommand.MasterDetuneAbsolute -> command.value
                        else -> PmdDetune.ZERO
                    }
                    val nextPart = when (command) {
                        is MmlCommand.PartDetuneAbsolute -> operand
                        is MmlCommand.PartDetuneRelative -> partDetune.plusOrNull(operand)
                        else -> partDetune
                    }
                    val nextMaster = if (command is MmlCommand.MasterDetuneAbsolute) operand else masterDetune
                    val effective = nextPart?.plusOrNull(nextMaster)
                    if (!isFmPart && !isSsg) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "D, DD, and DM require an FM or SSG part"))
                    } else if (nextPart == null || effective == null) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Detune arithmetic exceeds the signed 16-bit PMD range"))
                    } else {
                        partDetune = nextPart
                        masterDetune = nextMaster
                        val kind = when (command) {
                            is MmlCommand.PartDetuneAbsolute -> CompiledOpnaSong.PART_DETUNE_ABSOLUTE
                            is MmlCommand.PartDetuneRelative -> CompiledOpnaSong.PART_DETUNE_RELATIVE
                            else -> CompiledOpnaSong.MASTER_DETUNE_ABSOLUTE
                        }
                        val targetDomain = if (isSsg) CompiledOpnaSong.DETUNE_TARGET_SSG else CompiledOpnaSong.DETUNE_TARGET_FM
                        val part = when {
                            isFm3Operator -> logicalPart
                            isSsg -> track.channel.ordinal - MmlChannelId.G.ordinal
                            else -> fmChannelIndex
                        }
                        if (!builder.addPartDetune(tick, targetDomain, part, operand.raw, kind))
                            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    }
                }
                is MmlCommand.FmSlotMask -> {
                    if (!isFmPart || command.mask !in 0..15) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "s requires an FM part and slot mask 0..15"))
                    } else {
                        fmSlotMask = command.mask
                        if (isFm3Operator && !builder.addFmPartSlotMask(tick, logicalPart, fmSlotMask))
                            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    }
                }
                is MmlCommand.FmSlotDetune -> {
                    if ((!isFm3Operator && !fm3ControlLane) || command.mask !in 1..15 || command.value !in -32_768..32_767) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "sd/sdd requires an FM3 extended part, slot mask 1..15, and value -32768..32767"))
                    } else if (!builder.addFmSlotDetune(
                            tick, 2, command.mask, command.value, command.relative, logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.FmOperatorTl -> {
                    val validValue = if (command.relative) command.value in -128..127 else command.value in 0..127
                    if (!isFmPart || command.mask !in 1..15 || !validValue) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "O requires an FM part, slot mask 1..15, and valid TL value"))
                    } else if (!builder.addFmTotalLevel(
                            tick, fmChannelIndex, command.mask, command.value, command.relative, logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.FmFeedback -> {
                    val validValue = if (command.relative) command.value in -7..7 else command.value in 0..7
                    if (!isFmPart || !validValue) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "FB requires an FM part and feedback 0..7 or relative -7..+7"))
                    } else if (!builder.addFmFeedback(
                            tick, fmChannelIndex, command.value, command.relative, logicalPart
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
                    } else if (!builder.addFmKeyOnDelay(
                            tick, fmChannelIndex, command.mask, delayTicks, logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmShot -> {
                    if (!builder.addRhythmGate(tick, command.voiceMask, command.dump)
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmMasterLevel -> {
                    val valid = if (command.relative) command.value in -63..63 else command.value in 0..63
                    if (!valid) diagnostics.add(MmlDiagnostic(command.line, command.column, "\\V rhythm master level must be 0..63 or a relative value within that range"))
                    else if (!builder.addRhythmMasterLevel(tick, command.value, command.relative)
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmVoiceLevel -> {
                    val valid = command.voice in 0..5 && if (command.relative) command.value in -31..31 else command.value in 0..31
                    if (!valid) diagnostics.add(MmlDiagnostic(command.line, command.column, "Rhythm voice level must select b/s/c/h/t/i and level 0..31"))
                    else if (!builder.addRhythmVoiceLevel(tick, command.voice, command.value, command.relative)
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.RhythmVoicePan -> {
                    if (command.voice !in 0..5 || command.pan !in 0..2) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Rhythm pan must select a valid voice and left/center/right"))
                    } else if (!builder.addRhythmVoicePan(tick, command.voice, command.pan)
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
                        val sourceBase = authoredCommandOrder(command.sourceOrder, SUBORDER_PRIMARY)
                        if (selectedRate != null && !builder.addHardwareLfoRate(
                                tick, selectedRate,
                                sourceBase,
                                command.line,
                                command.column
                            )
                        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        if (!builder.addHardwareLfoEnable(
                                tick, command.enabled,
                                sourceBase + SUBORDER_SECONDARY,
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
                        val sourceBase = authoredCommandOrder(command.sourceOrder, SUBORDER_PRIMARY)
                        if (!builder.addHardwareLfoPms(
                                tick, fmChannelIndex, logicalPart, command.pms,
                                sourceBase, command.line, command.column
                            )
                        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        if (!builder.addHardwareLfoAms(
                                tick, fmChannelIndex, logicalPart, command.ams,
                                sourceBase + SUBORDER_SECONDARY, command.line, command.column
                            )
                        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        if (command.delayKind != MmlCommand.HARDWARE_LFO_DELAY_NONE) {
                            val delayKind = if (command.delayKind == MmlCommand.HARDWARE_LFO_DELAY_RAW_CLOCKS) {
                                CompiledOpnaSong.HW_LFO_DELAY_RAW_CLOCKS
                            } else {
                                CompiledOpnaSong.HW_LFO_DELAY_NOTE_LENGTH
                            }
                            if (!builder.addHardwareLfoDelay(
                                    tick, fmChannelIndex, logicalPart, delayKind, command.delayValue, command.delayDotted,
                                    sourceBase + SUBORDER_TERTIARY, command.line, command.column
                                )
                            ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                        }
                    }
                }
                is MmlCommand.SoftwareLfoDefine -> {
                    if (!isFmPart && !isSsg) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Software LFO is only valid on FM/SSG parts"))
                    } else if (command.delay !in 0..255 || command.speed !in 0..255 ||
                        command.depthA !in -128..127 || command.depthB !in 0..255
                    ) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "M requires delay/speed/depthB 0..255 and depthA -128..127"))
                    } else if (!builder.addSoftwareLfoDefinition(
                            tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG else CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM,
                            command.index,
                            command.delay, command.speed, command.depthA, command.depthB,
                            logicalPart = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoSwitch -> {
                    if ((!isFmPart && !isSsg) || command.value !in 0..7) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "* requires an FM/SSG part and switch 0..7"))
                    } else if (!builder.addSoftwareLfoSwitch(
                            tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG else CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM,
                            command.index, command.value,
                            logicalPart = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoWaveform -> {
                    if ((!isFmPart && !isSsg) || command.value !in 0..6) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "MW requires an FM/SSG part and waveform 0..6"))
                    } else if (!builder.addSoftwareLfoWaveform(
                            tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG else CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM,
                            command.index, command.value,
                            logicalPart = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoClockMode -> {
                    if ((!isFmPart && !isSsg) || command.value !in 0..1) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "MX requires an FM/SSG part and clock mode 0..1"))
                    } else if (!builder.addSoftwareLfoClockMode(
                            tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG else CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM,
                            command.index, command.value,
                            logicalPart = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoTlMask -> {
                    if (!isFmPart || command.value !in 0..15) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "MM requires an FM part and TL slot mask 0..15"))
                    } else if (!builder.addSoftwareLfoTlMask(
                            tick, fmChannelIndex, command.index, command.value,
                            logicalPart = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.SoftwareLfoDepthEvolution -> {
                    if ((!isFmPart && !isSsg) || command.speed !in 0..255 ||
                        command.depth !in -128..127 || command.time !in 0..127
                    ) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "MD requires speed 0..255, depth -128..127, and time 0..127"))
                    } else if (!builder.addSoftwareLfoDepth(
                            tick,
                            if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else fmChannelIndex,
                            if (isSsg) CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG else CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM,
                            command.index,
                            command.speed, command.depth, command.time,
                            logicalPart = logicalPart
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.Note -> {
                    sawEvent = true
                    if (isRhythm) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Pitched notes are not allowed on channel R"))
                    } else {
                        val firstDuration = durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                        var totalDuration = firstDuration
                        val midi = midiFor(command.letter, command.accidental, octave)
                        var link = command.link
                        var finalIndex = i
                        var targetMidi = -1
                        var glideStartOffsetTicks = 0
                        var gate = gateState.resolve(firstDuration, link == MmlCommand.LINK_SLUR)
                        while (link == MmlCommand.LINK_TIE && finalIndex + 1 < track.commands.size) {
                            when (val next = track.commands[finalIndex + 1]) {
                                is MmlCommand.Note -> {
                                    if (midiFor(next.letter, next.accidental, octave) != midi) {
                                        diagnostics.add(MmlDiagnostic(command.line, command.column, "A tie must immediately join the same pitch or a matching portamento"))
                                        break
                                    }
                                    val nextDuration = durationTicksV2(next.denominator, next.dotted, defaultLength, next, diagnostics)
                                    val nextGate = gateState.resolve(nextDuration, next.link == MmlCommand.LINK_SLUR)
                                    gate = totalDuration + nextGate
                                    totalDuration += nextDuration
                                    link = next.link
                                    finalIndex++
                                }
                                is MmlCommand.Portamento -> {
                                    val fromMidi = midiFor(next.fromLetter, next.fromAccidental, octave)
                                    if (fromMidi != midi) {
                                        diagnostics.add(MmlDiagnostic(next.line, next.column, "A tied portamento must start at the held pitch"))
                                        break
                                    }
                                    val glideDuration = durationTicksV2(next.denominator, next.dotted, defaultLength, next, diagnostics)
                                    val glideGate = gateState.resolve(glideDuration, false)
                                    glideStartOffsetTicks = totalDuration
                                    targetMidi = midiFor(next.toLetter, next.toAccidental, octave)
                                    gate = totalDuration + glideGate
                                    totalDuration += glideDuration
                                    link = MmlCommand.LINK_NONE
                                    finalIndex++
                                }
                                else -> {
                                    diagnostics.add(MmlDiagnostic(command.line, command.column, "A tie must immediately join the same pitch or a matching portamento"))
                                    break
                                }
                            }
                        }
                        if (midi !in 0..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Note is outside MIDI range 0..127"))
                        else if (targetMidi !in -1..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Portamento target is outside MIDI range 0..127"))
                        else if (patchId < 0) diagnostics.add(MmlDiagnostic(command.line, command.column, "Channel ${track.channel} requires a named instrument before its first note"))
                        else if (!pitchIsLowerable(isSsg, midi, partDetune, masterDetune) ||
                            (targetMidi >= 0 && !pitchIsLowerable(isSsg, targetMidi, partDetune, masterDetune))) {
                            diagnostics.add(MmlDiagnostic(command.line, command.column, "Detune cannot be lowered at this note without crossing the hardware pitch field"))
                        }
                        else if (totalDuration > 0) {
                            val added = when {
                                isFm3Operator -> builder.addFm3OperatorNote(
                                    tick, totalDuration, gate, midi, targetMidi, patchId, pan, glideStartOffsetTicks,
                                    fmSlotMask, logicalPart, gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks
                                )
                                isSsg -> builder.addSsgNote(
                                    tick, totalDuration, gate, track.channel.ordinal - MmlChannelId.G.ordinal,
                                    midi, targetMidi, patchId, pan, glideStartOffsetTicks,
                                    gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks
                                )
                                else -> builder.addFmNote(
                                    tick, totalDuration, gate, track.channel.ordinal, midi, targetMidi,
                                    patchId, pan, glideStartOffsetTicks,
                                    gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks
                                )
                            }
                            if (!added) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                            }
                            tick += totalDuration
                        }
                        i = finalIndex
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
                        else if (fromMidi !in 0..127 || toMidi !in 0..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Portamento is outside MIDI range 0..127"))
                        else if (!pitchIsLowerable(isSsg, fromMidi, partDetune, masterDetune) ||
                            !pitchIsLowerable(isSsg, toMidi, partDetune, masterDetune)) {
                            diagnostics.add(MmlDiagnostic(command.line, command.column, "Detune cannot be lowered at this portamento without crossing the hardware pitch field"))
                        }
                        else if (duration > 0) {
                            val gate = gateState.resolve(duration, false)
                            val added = when {
                                isFm3Operator -> builder.addFm3OperatorNote(
                                    tick, duration, gate, fromMidi, toMidi, patchId, pan, 0,
                                    fmSlotMask, logicalPart, gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks
                                )
                                isSsg -> builder.addSsgNote(
                                    tick, duration, gate, track.channel.ordinal - MmlChannelId.G.ordinal,
                                    fromMidi, toMidi, patchId, pan, 0,
                                    gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks
                                )
                                else -> builder.addFmNote(
                                    tick, duration, gate, track.channel.ordinal, fromMidi, toMidi,
                                    patchId, pan, 0,
                                    gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks
                                )
                            }
                            if (!added) {
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
                            if (!builder.addRhythmShot(tick, duration, kind.ordinal, fineVolume, pan)) {
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
        if (!builder.addHardwareLfoRate(
                0L, rate,
                Int.MIN_VALUE, 1, 1
            )
        ) diagnostics.add(MmlDiagnostic(1, 1, "Compiled OPNA event capacity exceeded"))
        if (!builder.addHardwareLfoEnable(
                0L, document.lfoRate in 0..7,
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
        val sourceBase = authoredCommandOrder(command.sourceOrder, SUBORDER_PRIMARY)
        if (!builder.addHardwareLfoPms(
                tick, channel, logicalPart, pms.coerceIn(0, 7),
                sourceBase, command.line, command.column
            )
        ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        if (!builder.addHardwareLfoAms(
                tick, channel, logicalPart, ams.coerceIn(0, 3),
                sourceBase + SUBORDER_SECONDARY, command.line, command.column
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
        fun nextOrder(): Int {
            val order = authoredCommandOrder(command.sourceOrder, subOrder)
            subOrder++
            return order
        }
        fun reportCapacity(added: Boolean) {
            if (!added) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        }
        reportCapacity(builder.addSsgToneEnable(tick, channel,
            patch.toneEnabled, nextOrder(), command.line, command.column))
        reportCapacity(builder.addSsgNoiseEnable(tick, channel,
            patch.noiseEnabled, nextOrder(), command.line, command.column))
        if (patch.noiseEnabled) reportCapacity(builder.addSsgNoisePeriod(
            tick, channel, patch.noisePeriod, nextOrder(), command.line, command.column))
        if (patch.envelopeEnabled) {
            reportCapacity(builder.addSsgHardwareEnvelopePeriod(
                tick, channel, patch.envelopePeriod, nextOrder(), command.line, command.column))
            reportCapacity(builder.addSsgEnvelopeShape(tick, channel, patch.envelopeShape,
                nextOrder(), command.line, command.column))
        }
    }

    private fun addSsgToneEnableCommand(isSsg: Boolean, command: MmlCommand.SsgToneEnable,
        tick: Long, track: MmlTrack, builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>) {
        if (!isSsg) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG hardware state commands are only valid on channels G-I"))
        } else if (!builder.addSsgToneEnable(tick, track.channel.ordinal - MmlChannelId.G.ordinal, command.enabled,
                authoredCommandOrder(command.sourceOrder, SUBORDER_PRIMARY),
                command.line, command.column)) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        }
    }

    private fun addSsgNoiseEnableCommand(isSsg: Boolean, command: MmlCommand.SsgNoiseEnable,
        tick: Long, track: MmlTrack, builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>) {
        if (!isSsg) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG hardware state commands are only valid on channels G-I"))
        } else if (!builder.addSsgNoiseEnable(tick, track.channel.ordinal - MmlChannelId.G.ordinal, command.enabled,
                authoredCommandOrder(command.sourceOrder, SUBORDER_PRIMARY), command.line, command.column)) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        }
    }

    private fun addSsgNoisePeriodCommand(isSsg: Boolean, command: MmlCommand.SsgNoisePeriod,
        tick: Long, track: MmlTrack, builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>) {
        if (!isSsg) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG hardware state commands are only valid on channels G-I"))
        } else if (command.period !in 1..31) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG period must be 1..31"))
        } else if (!builder.addSsgNoisePeriod(tick, track.channel.ordinal - MmlChannelId.G.ordinal, command.period,
                authoredCommandOrder(command.sourceOrder, SUBORDER_PRIMARY), command.line, command.column)) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        }
    }

    private fun addSsgHardwareEnvelopePeriodCommand(
        isSsg: Boolean,
        command: MmlCommand.SsgHardwareEnvelopePeriod,
        tick: Long,
        track: MmlTrack,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        if (!isSsg) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG hardware state commands are only valid on channels G-I"))
        } else if (command.period !in 1..65_535) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG period must be 1..65535"))
        } else if (!builder.addSsgHardwareEnvelopePeriod(
                tick,
                track.channel.ordinal - MmlChannelId.G.ordinal,
                command.period,
                authoredCommandOrder(command.sourceOrder, SUBORDER_PRIMARY),
                command.line,
                command.column
            )
        ) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        }
    }

    private fun addSsgEnvelopeShapeCommand(isSsg: Boolean, command: MmlCommand.SsgHardwareEnvelopeShape,
        tick: Long, track: MmlTrack, builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>) {
        if (!isSsg) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "SSG hardware state commands are only valid on channels G-I"))
        } else if (!builder.addSsgEnvelopeShape(tick, track.channel.ordinal - MmlChannelId.G.ordinal, command.shape,
                authoredCommandOrder(command.sourceOrder, SUBORDER_PRIMARY), command.line, command.column)) {
            diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
        }
    }

    private fun authoredCommandOrder(sourceOrder: Int, suborder: Int): Int =
        sourceOrder * AUTHORED_COMMAND_SUBORDER_COUNT + suborder

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
            if (command is MmlCommand.Note || command is MmlCommand.Portamento || command is MmlCommand.Drum) return true
            i++
        }
        return false
    }

    private fun pitchIsLowerable(
        isSsg: Boolean,
        midi: Int,
        partDetune: PmdDetune,
        masterDetune: PmdDetune
    ): Boolean {
        if (midi !in 0..127) return false
        val effective = partDetune.plusOrNull(masterDetune) ?: return false
        return if (isSsg) {
            val period = SsgHardwareLaws.nearestTonePeriod(midiToFrequency(midi))
            OpnPitch.lowerSsgTonePeriod(period, effective.raw) != OpnPitch.INVALID_PACKED_PITCH
        } else {
            OpnPitch.lowerPmdDetune(
                OpnPitch.nearestBlockFnumForMidi(midi), effective.raw
            ) != OpnPitch.INVALID_PACKED_PITCH
        }
    }

    private fun midiToFrequency(midi: Int): Double =
        440.0 * 2.0.pow((midi - 69).toDouble() / 12.0)

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
            val firstPayload = program.authoredPayloadIndex(first)
            val secondPayload = program.authoredPayloadIndex(second)
            val firstStart = program.notes.startTick(firstPayload)
            val secondStart = program.notes.startTick(secondPayload)
            val firstEnd = firstStart + program.notes.durationTick(firstPayload)
            val secondEnd = secondStart + program.notes.durationTick(secondPayload)
            if (firstStart < secondEnd && secondStart < firstEnd &&
                (program.notes.slotMask(firstPayload) and program.notes.slotMask(secondPayload)) != 0
            ) return true
            if (firstEnd <= secondEnd) first = nextFm3Note(program, first + 1, firstLogicalPart)
            if (secondEnd <= firstEnd) second = nextFm3Note(program, second + 1, secondLogicalPart)
        }
        return false
    }

    private fun nextFm3Note(program: CompiledOpnaSong, start: Int, logicalPart: Int): Int {
        var index = start
        while (index < program.eventCount) {
            if (program.authoredKind(index) == CompiledOpnaSong.FM3_OPERATOR_NOTE &&
                program.notes.logicalPart(program.authoredPayloadIndex(index)) == logicalPart
            ) return index
            index++
        }
        return -1
    }

    private fun isUnsupportedFm3ControlLaneCommand(command: MmlCommand): Boolean =
        command is MmlCommand.Volume || command is MmlCommand.FineVolume ||
            command is MmlCommand.RelativeVolume || command is MmlCommand.FineVolumeOffset ||
            command is MmlCommand.CoarseVolumeOffset || command is MmlCommand.Octave ||
            command is MmlCommand.OctaveShift || command is MmlCommand.Gate ||
            command is MmlCommand.GateTail || command is MmlCommand.Pan ||
            command is MmlCommand.PartDetuneAbsolute || command is MmlCommand.PartDetuneRelative ||
            command is MmlCommand.MasterDetuneAbsolute ||
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
                authoredCommandOrder(command.sourceOrder, SUBORDER_FALLBACK),
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
                authoredCommandOrder(command.sourceOrder, SUBORDER_FALLBACK),
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
                is MmlCommand.RelativeVolume -> {
                    val delta = if (command.increase) command.amount else -command.amount
                    velocity = (velocity + delta).coerceIn(0, 127)
                }
                is MmlCommand.FineVolumeOffset -> velocity = (velocity + command.delta).coerceIn(0, 127)
                is MmlCommand.CoarseVolumeOffset -> Unit
                is MmlCommand.Note -> {
                    val duration = durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                    val kind = ssgDrumKind(instrument)
                    if (kind < 0) diagnostics.add(MmlDiagnostic(command.line, command.column, "R${pattern.id} requires a supported SSG drum @ instrument before a hit"))
                    else if (!builder.addSsgDrumShot(baseTick + tick, duration, kind, velocity, 0)
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

    private fun addPersistentPartVolume(
        isFmPart: Boolean,
        isFm3Operator: Boolean,
        isSsg: Boolean,
        fmChannel: Int,
        logicalPart: Int,
        channel: MmlChannelId,
        tick: Long,
        volume: Int,
        command: MmlCommand,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        val added = when {
            isFm3Operator -> builder.addFmPartVolume(tick, logicalPart, volume)
            isFmPart -> builder.addFmPartVolume(tick, fmChannel, volume)
            isSsg -> builder.addSsgPartVolume(tick, channel.ordinal - MmlChannelId.G.ordinal, volume)
            else -> true
        }
        if (!added) diagnostics.add(
            MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded")
        )
    }

}
