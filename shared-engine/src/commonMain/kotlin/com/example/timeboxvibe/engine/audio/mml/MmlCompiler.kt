package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.ArrangementRouting
import com.example.timeboxvibe.engine.Lane
import com.example.timeboxvibe.engine.LaneMode
import com.example.timeboxvibe.engine.TimbreRef
import com.example.timeboxvibe.engine.ToneSpec
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.midiNoteToFreq

sealed class MmlCompileResult {
    data class Success(val arrangement: ArrangementLanes) : MmlCompileResult()
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
        val diagnostics = mutableListOf<MmlDiagnostic>()
        val scaledBarTicks = document.barNumerator.toLong() * WHOLE_NOTE_TICKS
        if (scaledBarTicks % document.barDenominator != 0L || scaledBarTicks > Int.MAX_VALUE) {
            diagnostics.add(MmlDiagnostic(1, 1, "#BAR cannot be represented by the compiler tick grid"))
            return MmlCompileResult.Failure(diagnostics)
        }
        val barTicks = (scaledBarTicks / document.barDenominator).toInt()
        val lanes = arrayOfNulls<Lane>(4)
        var percussion: Lane? = null
        var ssgTracks = 0
        var sequencerEvents = 0
        var trackIndex = 0
        while (trackIndex < document.tracks.size) {
            val track = document.tracks[trackIndex]
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
                beatsPerBar = document.barNumerator
            )
        )
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
                    val durationTicks = durationTicks(command.denominator, defaultLength, command.line, command.column, diagnostics)
                    if (durationTicks > 0) tick += durationTicks
                }
                is MmlCommand.Drum -> {
                    sawEvent = true
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
            "square" -> TimbreRef.SSG_HARMONY_SQUARE
            else -> null
        }
    }

    private fun toneType(timbre: TimbreRef?): String = if (timbre == TimbreRef.SSG_HARMONY_SQUARE) "square" else "fm"
}
