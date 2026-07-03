package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.ArrangementRouting
import com.example.timeboxvibe.engine.Lane
import com.example.timeboxvibe.engine.LaneMode
import com.example.timeboxvibe.engine.TimbreRef
import com.example.timeboxvibe.engine.ToneSpec
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSongBuilder
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.opna.midiNoteToFreq

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
        val lanes = arrayOfNulls<Lane>(MmlChannelId.R.ordinal)
        var percussion: Lane? = null
        var ssgTracks = 0
        var sequencerEvents = 0
        var trackIndex = 0
        while (trackIndex < document.tracks.size) {
            val track = document.tracks[trackIndex]
            if (track.channel != MmlChannelId.R && track.channel.ordinal > MmlChannelId.E.ordinal && track.commands.isNotEmpty()) {
                val first = track.commands[0]
                diagnostics.add(MmlDiagnostic(first.line, first.column, "Channels F-I and C1-C4 require #MML 2"))
                trackIndex++
                continue
            }
            val built = compileTrack(track, document.bpm, barTicks, diagnostics)
            if (built != null) {
                if (track.channel == MmlChannelId.R) {
                    percussion = built.lane
                    sequencerEvents += built.noteCount
                } else {
                    lanes[track.channel.ordinal] = built.lane
                    sequencerEvents += built.noteCount * 2
                    if (built.lane.timbre == TimbreRef.SSG_HARMONY_SQUARE) ssgTracks++
                }
            }
            trackIndex++
        }
        if (ssgTracks > 3) diagnostics.add(MmlDiagnostic(1, 1, "MML uses more than the three available SSG channels"))
        if (sequencerEvents > OpnaSequencer.MAX_EVENTS) {
            diagnostics.add(MmlDiagnostic(1, 1, "Expanded song exceeds OpnaSequencer.MAX_EVENTS"))
        }
        if (diagnostics.isNotEmpty()) return MmlCompileResult.Failure(diagnostics)

        val emptyFm = Lane(emptyList(), TimbreRef.FM_LLS_AT54, LaneMode.MONO_RETRIGGER)
        val emptySsg = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE, LaneMode.SSG_MONO)
        val emptyDrums = Lane(emptyList(), TimbreRef.DRUM_HAT, LaneMode.DRUM)
        return MmlCompileResult.Success(
            ArrangementLanes(
                lead = lanes[0] ?: emptyFm,
                harmony = lanes[1] ?: emptySsg,
                bass = lanes[2] ?: emptyFm,
                percussion = percussion ?: emptyDrums,
                tempoBpm = document.bpm,
                keyRootMidi = DEFAULT_KEY_ROOT_MIDI,
                auxiliary = lanes[3],
                routing = ArrangementRouting.MML_LOGICAL_TRACKS,
                beatsPerBar = document.barNumerator,
                eqBands = document.eqBands.map { it.band },
                additional = lanes[4]
            )
        )
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
        val builder = CompiledOpnaSongBuilder(
            dialectVersion = 2,
            bpm = document.bpm,
            beatsPerBar = document.barNumerator,
            lfoRate = document.lfoRate,
            fm3Extended = document.fm3Extended
        )
        val channelC = document.tracks[MmlChannelId.C.ordinal]
        val channelCPatch = firstFmPatch(channelC)
        var sequencerCost = 0
        var trackIndex = 0
        while (trackIndex < document.tracks.size) {
            val track = document.tracks[trackIndex]
            val isFm3Operator = track.channel in MmlChannelId.C1..MmlChannelId.C4
            if (isFm3Operator && !document.fm3Extended && track.commands.isNotEmpty()) {
                val first = track.commands[0]
                diagnostics.add(MmlDiagnostic(first.line, first.column, "${track.channel} requires #FM3EXTEND ON"))
            } else if (track.channel == MmlChannelId.C && document.fm3Extended) {
                if (containsSoundEvent(track)) {
                    val first = track.commands.first { it is MmlCommand.Note || it is MmlCommand.Portamento || it is MmlCommand.Drum }
                    diagnostics.add(MmlDiagnostic(first.line, first.column, "Channel C supplies FM3 patch/control data while #FM3EXTEND is ON; write notes on C1-C4"))
                }
            } else {
                sequencerCost += compileV2Track(track, document, barTicks, channelCPatch, builder, diagnostics)
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
        if (sequencerCost > OpnaSequencer.MAX_EVENTS) {
            diagnostics.add(MmlDiagnostic(1, 1, "Expanded song exceeds OpnaSequencer.MAX_EVENTS"))
        }
        if (diagnostics.isNotEmpty()) return MmlCompileResult.Failure(diagnostics)

        val program = builder.build()
        val emptyFm = Lane(emptyList(), TimbreRef.FM_LLS_AT54, LaneMode.MONO_RETRIGGER)
        val emptySsg = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE, LaneMode.SSG_MONO)
        val emptyDrums = Lane(emptyList(), TimbreRef.DRUM_HAT, LaneMode.DRUM)
        return MmlCompileResult.Success(
            ArrangementLanes(
                lead = emptyFm,
                harmony = emptyFm,
                bass = emptyFm,
                percussion = emptyDrums,
                tempoBpm = document.bpm,
                keyRootMidi = DEFAULT_KEY_ROOT_MIDI,
                auxiliary = emptySsg,
                routing = ArrangementRouting.MML_LOGICAL_TRACKS,
                beatsPerBar = document.barNumerator,
                eqBands = document.eqBands.map { it.band },
                additional = null,
                compiledOpnaSong = program
            ),
            sharedSsgWarnings(program)
        )
    }

    private fun sharedSsgWarnings(program: CompiledOpnaSong): List<MmlDiagnostic> {
        val warnings = mutableListOf<MmlDiagnostic>()
        var i = 0
        while (i < program.eventCount && warnings.isEmpty()) {
            if (program.eventType[i] == CompiledOpnaSong.SSG_NOTE) {
                val first = OpnaPatchBank.ssgPatch(program.patchId[i])
                if (first != null && first.envelopeEnabled) {
                    val firstEnd = program.startTick[i] + program.durationTick[i]
                    var j = i + 1
                    while (j < program.eventCount) {
                        if (program.eventType[j] == CompiledOpnaSong.SSG_NOTE) {
                            val second = OpnaPatchBank.ssgPatch(program.patchId[j])
                            val secondEnd = program.startTick[j] + program.durationTick[j]
                            val overlaps = program.startTick[i] < secondEnd && program.startTick[j] < firstEnd
                            if (second != null && second.envelopeEnabled && overlaps &&
                                (first.envelopeShape != second.envelopeShape || first.envelopePeriod != second.envelopePeriod)
                            ) {
                                warnings.add(MmlDiagnostic(1, 1, "Overlapping SSG parts request incompatible shared hardware envelopes; last note-on wins"))
                                break
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

    private fun compileV2Track(
        track: MmlTrack,
        document: MmlDocument,
        barTicks: Int,
        channelCPatch: Int,
        builder: CompiledOpnaSongBuilder,
        diagnostics: MutableList<MmlDiagnostic>
    ): Int {
        if (track.commands.isEmpty()) return 0
        val isRhythm = track.channel == MmlChannelId.R
        val isSsg = track.channel in MmlChannelId.G..MmlChannelId.I
        val isFm = track.channel in MmlChannelId.A..MmlChannelId.F
        val isFm3Operator = track.channel in MmlChannelId.C1..MmlChannelId.C4
        var octave = DEFAULT_OCTAVE
        var defaultLength = DEFAULT_LENGTH
        var fineVolume = 127
        var gateEighths = 8
        var patchId = if (isFm3Operator) channelCPatch else -1
        var pan = 0
        var detuneCents = 0
        var pms = -1
        var ams = -1
        var lfoDelayTicks = 0
        var tick = 0L
        var sequencerCost = 0
        var sawEvent = false
        var i = 0
        while (i < track.commands.size) {
            val command = track.commands[i]
            when (command) {
                is MmlCommand.Instrument -> {
                    if (isRhythm) {
                        if (command.value != "drum") diagnostics.add(MmlDiagnostic(command.line, command.column, "Channel R requires @drum"))
                    } else if (isFm3Operator) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "FM3 operator parts inherit their instrument from channel C"))
                    } else {
                        val id = OpnaPatchBank.idForName(command.value)
                        val valid = (isFm && OpnaPatchBank.isFm(id)) || (isSsg && OpnaPatchBank.isSsg(id))
                        if (!valid) diagnostics.add(MmlDiagnostic(command.line, command.column, "Instrument @${command.value} is invalid for channel ${track.channel}"))
                        else patchId = id
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
                    if (command.eighths !in 0..8) diagnostics.add(MmlDiagnostic(command.line, command.column, "Gate must be Q0..Q8"))
                    else gateEighths = command.eighths
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
                is MmlCommand.Detune -> {
                    if (command.cents !in -1_200..1_200) diagnostics.add(MmlDiagnostic(command.line, command.column, "Detune must be between -1200 and +1200 cents"))
                    else detuneCents = command.cents
                }
                is MmlCommand.Tempo -> {
                    if (track.channel != MmlChannelId.A) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Tempo changes are global and must be written on channel A"))
                    } else if (command.bpm !in 20..400) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Tempo must be T20..T400"))
                    } else if (!builder.addTempo(tick, command.bpm.toFloat())) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Tempo-change capacity exceeded"))
                    }
                }
                is MmlCommand.HardwareLfo -> {
                    if (!isFm && !isFm3Operator) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Hardware LFO is only valid on FM channels"))
                    } else if (command.pms !in 0..7 || command.ams !in 0..3) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "H requires PMS 0..7 and AMS 0..3"))
                    } else {
                        pms = command.pms
                        ams = command.ams
                        lfoDelayTicks = if (command.delayDenominator == null) 0 else durationTicksV2(command.delayDenominator, false, defaultLength, command, diagnostics)
                    }
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
                            val gate = if (link == MmlCommand.LINK_SLUR) totalDuration else totalDuration * gateEighths / 8
                            val type = when {
                                isFm3Operator -> CompiledOpnaSong.FM3_OPERATOR_NOTE
                                isSsg -> CompiledOpnaSong.SSG_NOTE
                                else -> CompiledOpnaSong.FM_NOTE
                            }
                            val channelIndex = when {
                                isFm3Operator -> 2
                                isSsg -> track.channel.ordinal - MmlChannelId.G.ordinal
                                else -> track.channel.ordinal
                            }
                            val operatorIndex = if (isFm3Operator) track.channel.ordinal - MmlChannelId.C1.ordinal else -1
                            if (!builder.add(type, tick, totalDuration, gate, channelIndex, operatorIndex, midi, -1, fineVolume, patchId, pan, detuneCents, pms, ams, lfoDelayTicks)) {
                                diagnostics.add(MmlDiagnostic(command.line, command.column, "Compiled OPNA event capacity exceeded"))
                            }
                            sequencerCost += 2
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
                        else if (duration > 0) {
                            val type = if (isSsg) CompiledOpnaSong.SSG_NOTE else if (isFm3Operator) CompiledOpnaSong.FM3_OPERATOR_NOTE else CompiledOpnaSong.FM_NOTE
                            val channelIndex = if (isSsg) track.channel.ordinal - MmlChannelId.G.ordinal else if (isFm3Operator) 2 else track.channel.ordinal
                            val operatorIndex = if (isFm3Operator) track.channel.ordinal - MmlChannelId.C1.ordinal else -1
                            builder.add(type, tick, duration, duration * gateEighths / 8, channelIndex, operatorIndex, fromMidi, toMidi, fineVolume, patchId, pan, detuneCents, pms, ams, lfoDelayTicks)
                            sequencerCost += 2
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
                            builder.add(CompiledOpnaSong.RHYTHM_SHOT, tick, duration, 0, 0, -1, kind.ordinal, -1, fineVolume, -1, pan, 0, 0, 0, 0)
                            sequencerCost++
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
        return sequencerCost
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
            if (command is MmlCommand.Note || command is MmlCommand.Portamento || command is MmlCommand.Drum) return true
            i++
        }
        return false
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

    private data class BuiltTrack(val lane: Lane, val noteCount: Int)

    private fun compileTrack(
        track: MmlTrack,
        bpm: Float,
        barTicks: Int,
        diagnostics: MutableList<MmlDiagnostic>
    ): BuiltTrack? {
        if (track.commands.isEmpty()) return null
        val notes = mutableListOf<ToneSpec>()
        var octave = DEFAULT_OCTAVE
        var defaultLength = DEFAULT_LENGTH
        var volume = DEFAULT_VOLUME
        var timbre: TimbreRef? = null
        var sawEvent = false
        var tick = 0L
        var noteCount = 0
        var i = 0
        while (i < track.commands.size) {
            val command = track.commands[i]
            when (command) {
                is MmlCommand.Instrument -> {
                    if (sawEvent) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Instrument must be set before the first event and cannot change"))
                    } else {
                        val mapped = mapTimbre(track.channel, command.value)
                        if (mapped == null) diagnostics.add(MmlDiagnostic(command.line, command.column, "Instrument @${command.value} is invalid for channel ${track.channel}"))
                        else if (timbre != null && timbre != mapped) diagnostics.add(MmlDiagnostic(command.line, command.column, "Only one instrument is allowed per channel"))
                        else timbre = mapped
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
                    if (track.channel == MmlChannelId.R) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Pitched notes are not allowed on channel R"))
                    } else {
                        val durationTicks = durationTicks(command.denominator, defaultLength, command.line, command.column, diagnostics)
                        if (durationTicks > 0) {
                            val midi = midiFor(command.letter, command.accidental, octave)
                            if (midi !in 0..127) diagnostics.add(MmlDiagnostic(command.line, command.column, "Note is outside MIDI range 0..127"))
                            else {
                                notes.add(toneFor(midiNoteToFreq(midi), tick, durationTicks, bpm, volume, toneType(timbre), false))
                                noteCount++
                            }
                            tick += durationTicks
                        }
                    }
                }
                is MmlCommand.Rest -> {
                    sawEvent = true
                    if (command.dotted) diagnostics.add(MmlDiagnostic(command.line, command.column, "Dotted rests require #MML 2"))
                    val durationTicks = durationTicks(command.denominator, defaultLength, command.line, command.column, diagnostics)
                    if (durationTicks > 0) tick += durationTicks
                }
                is MmlCommand.Drum -> {
                    sawEvent = true
                    if (command.dotted || command.kind == 't' || command.kind == 'y' || command.kind == 'i') {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Extended rhythm syntax requires #MML 2"))
                    }
                    if (track.channel != MmlChannelId.R) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Drum tokens are only allowed on channel R"))
                    } else {
                        val durationTicks = durationTicks(command.denominator, defaultLength, command.line, command.column, diagnostics)
                        if (durationTicks > 0) {
                            val freq = if (command.kind == 'k') -1f else if (command.kind == 'h') 8000f else 3000f
                            val type = if (command.kind == 'k') "kick" else if (command.kind == 'h') "hat" else "snare"
                            notes.add(toneFor(freq, tick, durationTicks, bpm, volume, type, false))
                            noteCount++
                            tick += durationTicks
                        }
                    }
                }
                is MmlCommand.Bar -> {
                    if (tick == 0L || tick % barTicks != 0L) {
                        diagnostics.add(MmlDiagnostic(command.line, command.column, "Bar line does not fall on a complete #BAR boundary"))
                    }
                }
                is MmlCommand.FineVolume,
                is MmlCommand.RelativeVolume,
                is MmlCommand.Gate,
                is MmlCommand.Pan,
                is MmlCommand.Detune,
                is MmlCommand.Tempo,
                is MmlCommand.HardwareLfo,
                is MmlCommand.Portamento -> {
                    diagnostics.add(MmlDiagnostic(command.line, command.column, "Command requires #MML 2"))
                }
            }
            i++
        }
        if (sawEvent && tick % barTicks != 0L) {
            val last = track.commands[track.commands.size - 1]
            diagnostics.add(MmlDiagnostic(last.line, last.column, "Final bar is incomplete"))
        }
        if (sawEvent && timbre == null) {
            val first = track.commands[0]
            diagnostics.add(MmlDiagnostic(first.line, first.column, "Channel ${track.channel} requires an instrument before its first event"))
        }
        val resolvedTimbre = timbre ?: if (track.channel == MmlChannelId.R) TimbreRef.DRUM_HAT else TimbreRef.FM_LLS_AT54
        val mode = if (track.channel == MmlChannelId.R) LaneMode.DRUM else if (resolvedTimbre == TimbreRef.SSG_HARMONY_SQUARE) LaneMode.SSG_MONO else LaneMode.MONO_RETRIGGER
        return BuiltTrack(Lane(notes, resolvedTimbre, mode), noteCount)
    }

    private fun toneFor(freq: Float, startTick: Long, durationTicks: Int, bpm: Float, volume: Int, type: String, adsr: Boolean): ToneSpec {
        val startMs = ticksToMs(startTick, bpm)
        val endMs = ticksToMs(startTick + durationTicks, bpm)
        return ToneSpec(freq, startMs, endMs - startMs, volume / 15f, type, adsr)
    }

    private fun ticksToMs(ticks: Long, bpm: Float): Int =
        (ticks.toDouble() * 60000.0 / (bpm.toDouble() * TICKS_PER_QUARTER)).toInt()

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

    private fun mapTimbre(channel: MmlChannelId, value: String): TimbreRef? {
        if (channel == MmlChannelId.R) return if (value == "drum") TimbreRef.DRUM_HAT else null
        return when (value) {
            "54" -> TimbreRef.FM_LLS_AT54
            "74" -> TimbreRef.FM_LLS_AT74
            "99" -> TimbreRef.FM_LLS_AT99
            "181" -> TimbreRef.FM_LLS_AT181
            "square" -> TimbreRef.SSG_HARMONY_SQUARE
            else -> null
        }
    }

    private fun toneType(timbre: TimbreRef?): String = if (timbre == TimbreRef.SSG_HARMONY_SQUARE) "square" else "fm"
}
