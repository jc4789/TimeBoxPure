package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.ArrangementRouting
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSongBuilder
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
import com.example.timeboxvibe.engine.audio.opna.PmdPerformanceLaws
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
    private const val DEFAULT_VOLUME = 15
    private const val DEFAULT_KEY_ROOT_MIDI = 60

    fun compile(source: String): MmlCompileResult {
        return when (val parsed = MmlParser.parse(source)) {
            is MmlParseResult.Failure -> MmlCompileResult.Failure(parsed.diagnostics)
            is MmlParseResult.Success -> compile(parsed.document)
        }
    }

    fun compile(document: MmlDocument): MmlCompileResult {
        if (document.dialectVersion == 2) return compileV2(document)
        return compileV1(document)
    }

    private fun compileV1(document: MmlDocument): MmlCompileResult {
        val diagnostics = mutableListOf<MmlDiagnostic>()
        var eqIndex = 0
        while (eqIndex < document.eqBands.size) {
            val directive = document.eqBands[eqIndex]
            if (directive.band.frequencyHz >= AudioLaws.SAMPLE_RATE * 0.5f) {
                diagnostics.add(MmlDiagnostic(directive.line, directive.column, "#eq frequencyHz must be below the Nyquist frequency"))
            }
            eqIndex++
        }
        val scaledBarTicks = document.barNumerator.toLong() * WHOLE_NOTE_TICKS
        if (scaledBarTicks % document.barDenominator != 0L || scaledBarTicks > Int.MAX_VALUE) {
            diagnostics.add(MmlDiagnostic(1, 1, "#BAR cannot be represented by the compiler tick grid"))
            return MmlCompileResult.Failure(diagnostics)
        }
        val barTicks = (scaledBarTicks / document.barDenominator).toInt()
        val builder = CompiledOpnaSongBuilder(
            dialectVersion = document.dialectVersion,
            bpm = document.bpm,
            bpmMilli = document.bpmMilli,
            beatsPerBar = document.barNumerator,
            pmdClocksPerQuarter = document.pmdClocksPerQuarter,
            lfoRate = document.lfoRate,
            fm3Extended = false
        )
        var fmChannel = 0
        var ssgChannel = 0
        var trackIndex = 0
        while (trackIndex < document.tracks.size) {
            val track = document.tracks[trackIndex]
            if (track.commands.isEmpty()) {
                trackIndex++
                continue
            }
            if (track.channel != MmlChannelId.R && track.channel.ordinal > MmlChannelId.E.ordinal && track.commands.isNotEmpty()) {
                val first = track.commands[0]
                diagnostics.add(MmlDiagnostic(first.line, first.column, "Channels F-I and C1-C4 require #MML 2"))
                trackIndex++
                continue
            }
            val patchId = firstPatch(track)
            val assignedChannel = when {
                track.channel == MmlChannelId.R -> 0
                OpnaPatchBank.isSsg(patchId) -> ssgChannel++
                else -> fmChannel++
            }
            compileV1Track(track, barTicks, patchId, assignedChannel, builder, diagnostics)
            trackIndex++
        }
        if (ssgChannel > AudioLaws.SSG_CHANNELS) diagnostics.add(MmlDiagnostic(1, 1, "MML uses more than the three available SSG channels"))
        if (fmChannel > AudioLaws.FM_CHANNELS) diagnostics.add(MmlDiagnostic(1, 1, "MML uses more than the six available FM channels"))
        if (diagnostics.isNotEmpty()) return MmlCompileResult.Failure(diagnostics)

        val program = builder.build()
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

    private fun compileV1Track(
        track: MmlTrack,
        barTicks: Int,
        initialPatchId: Int,
        channelIndex: Int,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        if (track.commands.isEmpty()) return
        val isRhythm = track.channel == MmlChannelId.R
        val isSsg = OpnaPatchBank.isSsg(initialPatchId)
        var octave = DEFAULT_OCTAVE
        var defaultLength = DEFAULT_LENGTH
        var volume = DEFAULT_VOLUME
        var patchId = -1
        var tick = 0L
        var sawEvent = false
        var i = 0
        while (i < track.commands.size) {
            val command = track.commands[i]
            when (command) {
                is MmlCommand.Instrument -> {
                    val selected = OpnaPatchBank.idForName(command.value)
                    val valid = if (isRhythm) command.value == "drum" else selected >= 0
                    if (!valid) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Instrument @${command.value} is invalid for channel ${track.channel}"))
                    } else if (patchId >= 0 && patchId != selected) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Instrument changes require #MML 2"))
                    } else if (!isRhythm && OpnaPatchBank.isSsg(selected) != isSsg) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Instrument family cannot change within a channel"))
                    } else {
                        patchId = selected
                    }
                }
                is MmlCommand.Volume -> {
                    if (command.value !in 0..15) diagnostics.add(MmlDiagnostic(command.line, command.column, "Volume must be v0..v15"))
                    else volume = command.value
                }
                is MmlCommand.Octave -> {
                    if (command.value !in 0..8) diagnostics.add(MmlDiagnostic(command.line, command.column, "Octave must be o0..o8"))
                    else octave = command.value
                }
                is MmlCommand.DefaultLength -> {
                    if (!isValidLength(command.denominator)) diagnostics.add(MmlDiagnostic(command.line, command.column, "Length must be 1, 2, 4, 8, or 16"))
                    else defaultLength = command.denominator
                }
                is MmlCommand.OctaveShift -> {
                    val shifted = octave + command.delta
                    if (shifted !in 0..8) diagnostics.add(MmlDiagnostic(command.line, command.column, "Octave shift leaves o0..o8"))
                    else octave = shifted
                }
                is MmlCommand.Note -> {
                    sawEvent = true
                    if (command.dotted || command.link != MmlCommand.LINK_NONE) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Dots, ties, and slurs require #MML 2"))
                    }
                    if (isRhythm) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Pitched notes are not allowed on channel R"))
                    } else {
                        val duration = durationTicks(command.denominator, defaultLength, command.line, command.column, diagnostics)
                        val midi = midiFor(command.letter, command.accidental, octave)
                        if (patchId < 0) diagnostics.add(MmlDiagnostic(command.line, command.column, "Channel ${track.channel} requires an instrument before its first event"))
                        else if (midi !in 0..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Note is outside MIDI range 0..127"))
                        else if (duration > 0) {
                            val type = if (isSsg) CompiledOpnaSong.SSG_NOTE else CompiledOpnaSong.FM_NOTE
                            if (!builder.add(type, tick, duration, duration, channelIndex, -1, midi, -1, (volume * 127 + 7) / 15, patchId, 0, 0, -1, -1, 0)) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                            }
                            tick += duration
                        }
                    }
                }
                is MmlCommand.Rest -> {
                    sawEvent = true
                    if (command.dotted) diagnostics.add(MmlDiagnostic(command.line, command.column, "Dotted rests require #MML 2"))
                    tick += durationTicks(command.denominator, defaultLength, command.line, command.column, diagnostics)
                }
                is MmlCommand.Drum -> {
                    sawEvent = true
                    if (command.dotted || command.kind == 't' || command.kind == 'y' || command.kind == 'i') {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Extended rhythm syntax requires #MML 2"))
                    }
                    if (!isRhythm) diagnostics.add(MmlDiagnostic(command.line, command.column, "Drum tokens are only allowed on channel R"))
                    else {
                        val duration = durationTicks(command.denominator, defaultLength, command.line, command.column, diagnostics)
                        val kind = drumKind(command.kind)
                        if (duration > 0 && kind != null) {
                            if (!builder.add(CompiledOpnaSong.RHYTHM_SHOT, tick, duration, 0, 0, -1, kind.ordinal, -1, (volume * 127 + 7) / 15, -1, 0, 0, 0, 0, 0)) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event safety limit exceeded"))
                            }
                            tick += duration
                        }
                    }
                }
                is MmlCommand.Bar -> if (tick == 0L || tick % barTicks != 0L) {
                    diagnostics.add(MmlDiagnostic(command.line, command.column, "Bar line does not fall on a complete #BAR boundary"))
                }
                else -> diagnostics.add(MmlDiagnostic(command.line, command.column, "Command requires #MML 2"))
            }
            i++
        }
        if (sawEvent && tick % barTicks != 0L) {
            val last = track.commands[track.commands.size - 1]
            diagnostics.add(MmlDiagnostic(last.line, last.column, "Final bar is incomplete"))
        }
    }

    private fun firstPatch(track: MmlTrack): Int {
        if (track.channel == MmlChannelId.R) return -1
        var i = 0
        while (i < track.commands.size) {
            val command = track.commands[i]
            if (command is MmlCommand.Instrument) return OpnaPatchBank.idForName(command.value)
            i++
        }
        return -1
    }

    private fun compileV2(document: MmlDocument): MmlCompileResult {
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
        if (document.fm3Extended) validateFm3SlotOwnership(document, diagnostics)
        val builder = CompiledOpnaSongBuilder(
            dialectVersion = 2,
            bpm = document.bpm,
            bpmMilli = document.bpmMilli,
            beatsPerBar = document.barNumerator,
            pmdClocksPerQuarter = document.pmdClocksPerQuarter,
            lfoRate = document.lfoRate,
            fm3Extended = document.fm3Extended
        )
        val channelC = document.tracks[MmlChannelId.C.ordinal]
        val channelCPatch = firstFmPatch(channelC)
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
            } else {
                compileV2Track(track, document, barTicks, channelCPatch, builder, diagnostics)
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
            if (program.eventType[i] == CompiledOpnaSong.SSG_NOTE) {
                val first = OpnaPatchBank.ssgPatch(program.patchId[i])
                if (first != null) {
                    val firstEnd = program.startTick[i] + program.durationTick[i]
                    var j = i + 1
                    while (j < program.eventCount && (!warnedEnvelope || !warnedNoise)) {
                        if (program.eventType[j] == CompiledOpnaSong.SSG_NOTE) {
                            val second = OpnaPatchBank.ssgPatch(program.patchId[j])
                            val secondEnd = program.startTick[j] + program.durationTick[j]
                            val overlaps = program.startTick[i] < secondEnd && program.startTick[j] < firstEnd
                            if (!warnedEnvelope && second != null && first.envelopeEnabled &&
                                second.envelopeEnabled && overlaps &&
                                (first.envelopeShape != second.envelopeShape || first.envelopePeriod != second.envelopePeriod)
                            ) {
                                warnings.add(MmlDiagnostic(1, 1, "Overlapping SSG parts request incompatible shared hardware envelopes; last note-on wins"))
                                warnedEnvelope = true
                            }
                            if (!warnedNoise && second != null && first.noiseEnabled &&
                                second.noiseEnabled && overlaps && first.noisePeriod != second.noisePeriod
                            ) {
                                warnings.add(MmlDiagnostic(1, 1, "Overlapping SSG parts request incompatible shared noise periods; last note-on wins"))
                                warnedNoise = true
                            }
                        }
                        j++
                    }
                }
            }
            i++
        }
        return warnings
    }

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
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        if (track.commands.isEmpty()) return
        val isRhythm = track.channel == MmlChannelId.R
        val isSsg = track.channel in MmlChannelId.G..MmlChannelId.I
        val isFm = track.channel in MmlChannelId.A..MmlChannelId.F
        val isFm3Operator = track.channel in MmlChannelId.C1..MmlChannelId.C4
        val isFmPart = isFm || isFm3Operator
        val fmChannelIndex = if (isFm3Operator) 2 else track.channel.ordinal
        var octave = DEFAULT_OCTAVE
        var defaultLength = DEFAULT_LENGTH
        var fineVolume = 127
        val ticksPerPmdClock = TICKS_PER_QUARTER / document.pmdClocksPerQuarter
        val gateState = PmdGateState(track.channel.ordinal, ticksPerPmdClock)
        var patchId = if (isFm3Operator) channelCPatch else -1
        var fmSlotMask = if (isFm3Operator) 1 shl (track.channel.ordinal - MmlChannelId.C1.ordinal) else 15
        var fm3PatchApplied = false
        var pan = 0
        var detuneCents = 0
        var pms = -1
        var ams = -1
        var polyphonicPart = false
        var sawSoftwareLfo = false
        var lfoDelayTicks = 0
        var tick = 0L
        var sawEvent = false
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
            when (command) {
                is MmlCommand.Instrument -> {
                    if (isRhythm) {
                        if (command.value != "drum") diagnostics.add(MmlDiagnostic(command.line, command.column, "Channel R requires @drum"))
                    } else {
                        val id = OpnaPatchBank.idForName(command.value)
                        val valid = (isFmPart && OpnaPatchBank.isFm(id)) || (isSsg && OpnaPatchBank.isSsg(id))
                        if (!valid) diagnostics.add(MmlDiagnostic(command.line, command.column, "Instrument @${command.value} is invalid for channel ${track.channel}"))
                        else {
                            patchId = id
                            if (isFm3Operator) {
                                if (!builder.addFm3Patch(tick, fmSlotMask, id)) {
                                    diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                                }
                                fm3PatchApplied = true
                            }
                        }
                    }
                }
                is MmlCommand.Volume -> {
                    if (command.value !in 0..15) diagnostics.add(MmlDiagnostic(command.line, command.column, "Volume must be v0..v15"))
                    else fineVolume = (command.value * 127 + 7) / 15
                }
                is MmlCommand.FineVolume -> {
                    if (command.value !in 0..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Fine volume must be V0..V127"))
                    else fineVolume = command.value
                }
                is MmlCommand.RelativeVolume -> fineVolume = (fineVolume + command.delta).coerceIn(0, 127)
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
                    }
                }
                is MmlCommand.FmSlotDetune -> {
                    if (!isFm3Operator || command.mask !in 1..15 || command.value !in -32_768..32_767) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "sd/sdd requires an FM3 extended part, slot mask 1..15, and value -32768..32767"))
                    } else if (!builder.addFmControl(
                            if (command.relative) CompiledOpnaSong.FM_SLOT_DETUNE_RELATIVE else CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE,
                            tick, 2, command.mask, command.value
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.FmOperatorTl -> {
                    val validValue = if (command.relative) command.value in -128..127 else command.value in 0..127
                    if (!isFmPart || command.mask !in 1..15 || !validValue) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "O requires an FM part, slot mask 1..15, and valid TL value"))
                    } else if (!builder.addFmControl(
                            if (command.relative) CompiledOpnaSong.FM_TL_RELATIVE else CompiledOpnaSong.FM_TL_ABSOLUTE,
                            tick, fmChannelIndex, command.mask, command.value
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                }
                is MmlCommand.FmFeedback -> {
                    val validValue = if (command.relative) command.value in -7..7 else command.value in 0..7
                    if (!isFmPart || !validValue) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "FB requires an FM part and feedback 0..7 or relative -7..+7"))
                    } else if (!builder.addFmControl(
                            if (command.relative) CompiledOpnaSong.FM_FEEDBACK_RELATIVE else CompiledOpnaSong.FM_FEEDBACK_ABSOLUTE,
                            tick, fmChannelIndex, 0, command.value
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
                            CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY, tick, fmChannelIndex, command.mask, delayTicks
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
                is MmlCommand.HardwareLfo -> {
                    if (!isFmPart) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Hardware LFO is only valid on FM channels"))
                    } else if (command.pms !in 0..7 || command.ams !in 0..3) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "H requires PMS 0..7 and AMS 0..3"))
                    } else {
                        pms = command.pms
                        ams = command.ams
                        lfoDelayTicks = if (command.delayDenominator == null) 0 else durationTicksV2(command.delayDenominator, false, defaultLength, command, diagnostics)
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
                            command.delay, command.speed, command.depthA, command.depthB
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
                            if (isSsg) 1 else 0, command.index, command.value
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
                            if (isSsg) 1 else 0, command.index, command.value
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
                            if (isSsg) 1 else 0, command.index, command.value
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
                            0, command.index, command.value
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
                            command.speed, command.depth, command.time
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
                            val patchAlreadyApplied = isFm3Operator && fm3PatchApplied
                            if (!builder.add(
                                    type, tick, totalDuration, gate, channelIndex, if (patchAlreadyApplied) 0 else -1, midi, -1,
                                    fineVolume, patchId, pan, detuneCents, pms, ams, lfoDelayTicks,
                                    gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks,
                                    if (isFm3Operator) fmSlotMask else 0
                                )
                            ) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                            }
                            if (isFm3Operator) fm3PatchApplied = true
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
                                        pms,
                                        ams,
                                        lfoDelayTicks,
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
                            val patchAlreadyApplied = isFm3Operator && fm3PatchApplied
                            if (!builder.add(
                                    type, tick, duration, gate, channelIndex, if (patchAlreadyApplied) 0 else -1, fromMidi, toMidi,
                                    fineVolume, patchId, pan, detuneCents, pms, ams, lfoDelayTicks,
                                    gateState.proportionalValue, gateState.proportionalScale,
                                    gateState.lastResolvedTailClocks, gateState.minimumClocks,
                                    if (isFm3Operator) fmSlotMask else 0
                                )
                            ) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event safety limit exceeded"))
                            }
                            if (isFm3Operator) fm3PatchApplied = true
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
                            if (!builder.add(CompiledOpnaSong.RHYTHM_SHOT, tick, duration, 0, 0, -1, kind.ordinal, -1, fineVolume, -1, pan, 0, 0, 0, 0)) {
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

    private fun firstFmPatch(track: MmlTrack): Int {
        var i = 0
        while (i < track.commands.size) {
            val command = track.commands[i]
            if (command is MmlCommand.Instrument) {
                val id = OpnaPatchBank.idForName(command.value)
                if (OpnaPatchBank.isFm(id)) return id
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

    private fun validateFm3SlotOwnership(document: MmlDocument, diagnostics: MutableList<MmlDiagnostic>) {
        var owned = 0
        var trackIndex = MmlChannelId.C1.ordinal
        while (trackIndex <= MmlChannelId.C4.ordinal) {
            val track = document.tracks[trackIndex]
            var mask = 1 shl (trackIndex - MmlChannelId.C1.ordinal)
            var used = 0
            var firstSound: MmlCommand? = null
            var commandIndex = 0
            while (commandIndex < track.commands.size) {
                val command = track.commands[commandIndex]
                if (command is MmlCommand.FmSlotMask) {
                    mask = command.mask
                } else if (command is MmlCommand.Note || command is MmlCommand.Portamento || command is MmlCommand.Chord) {
                    used = used or mask
                    if (firstSound == null) firstSound = command
                }
                commandIndex++
            }
            val overlap = owned and used
            if (overlap != 0 && firstSound != null) {
                diagnostics.add(
                    MmlDiagnostic(
                        firstSound.line,
                        firstSound.column,
                        "FM3 slot ownership overlaps an earlier extended part (mask $overlap)"
                    )
                )
            }
            owned = owned or used
            trackIndex++
        }
    }

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
                is MmlCommand.RhythmVoicePan -> addRhythmControlCommand(command, tick, builder, diagnostics)
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
                            2, -1, kind, -1, velocity, -1, 0, 0, 0, 0, 0
                        )
                    ) diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                    tick += duration
                }
                is MmlCommand.Rest -> tick += durationTicksV2(command.denominator, command.dotted, defaultLength, command, diagnostics)
                is MmlCommand.RhythmShot,
                is MmlCommand.RhythmMasterLevel,
                is MmlCommand.RhythmVoiceLevel,
                is MmlCommand.RhythmVoicePan -> addRhythmControlCommand(command, baseTick + tick, builder, diagnostics)
                is MmlCommand.Bar -> Unit
                else -> diagnostics.add(MmlDiagnostic(command.line, command.column, "Command is not valid in PMD rhythm definition R${pattern.id}"))
            }
            commandIndex++
        }
        return tick
    }

    private fun addRhythmControlCommand(
        command: MmlCommand,
        tick: Long,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        val added = when (command) {
            is MmlCommand.RhythmShot -> builder.addRhythmControl(
                if (command.dump) CompiledOpnaSong.RHYTHM_CONTROL_DUMP else CompiledOpnaSong.RHYTHM_CONTROL_SHOT,
                tick, -1, command.voiceMask, 0
            )
            is MmlCommand.RhythmMasterLevel -> {
                val valid = if (command.relative) command.value in -63..63 else command.value in 0..63
                valid && builder.addRhythmControl(
                    if (command.relative) CompiledOpnaSong.RHYTHM_MASTER_RELATIVE else CompiledOpnaSong.RHYTHM_MASTER_ABSOLUTE,
                    tick, -1, 0, command.value
                )
            }
            is MmlCommand.RhythmVoiceLevel -> {
                val valid = command.voice in 0..5 && if (command.relative) command.value in -31..31 else command.value in 0..31
                valid && builder.addRhythmControl(
                    if (command.relative) CompiledOpnaSong.RHYTHM_VOICE_LEVEL_RELATIVE else CompiledOpnaSong.RHYTHM_VOICE_LEVEL_ABSOLUTE,
                    tick, command.voice, 0, command.value
                )
            }
            is MmlCommand.RhythmVoicePan -> builder.addRhythmControl(
                CompiledOpnaSong.RHYTHM_VOICE_PAN, tick, command.voice, 0, command.pan
            )
            else -> true
        }
        if (!added) diagnostics.add(MmlDiagnostic(command.line, command.column, "Invalid rhythm control range or compiled event capacity exceeded"))
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

    private fun durationTicks(value: Int?, defaultValue: Int, line: Int, column: Int, diagnostics: MutableList<MmlDiagnostic>): Int {
        val denominator = value ?: defaultValue
        if (!isValidLength(denominator)) {
            diagnostics.add(MmlDiagnostic(line, column, "Length must be 1, 2, 4, 8, or 16"))
            return 0
        }
        return WHOLE_NOTE_TICKS / denominator
    }

    private fun isValidLength(value: Int): Boolean = value == 1 || value == 2 || value == 4 || value == 8 || value == 16

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
