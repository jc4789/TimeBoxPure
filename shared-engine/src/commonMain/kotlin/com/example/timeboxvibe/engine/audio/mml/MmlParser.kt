package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.EqType
import com.example.timeboxvibe.engine.SongEqBand

data class MmlDiagnostic(val line: Int, val column: Int, val reason: String)

enum class MmlChannelId { A, B, C, D, E, F, G, H, I, C1, C2, C3, C4, R }

sealed class MmlCommand(open val line: Int, open val column: Int) {
    data class Instrument(val value: String, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Volume(val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class FineVolume(val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class RelativeVolume(val delta: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Octave(val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class DefaultLength(val denominator: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class OctaveShift(val delta: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Note(
        val letter: Char,
        val accidental: Int,
        val denominator: Int?,
        override val line: Int,
        override val column: Int,
        val dotted: Boolean = false,
        val link: Int = LINK_NONE
    ) : MmlCommand(line, column)
    data class Rest(val denominator: Int?, override val line: Int, override val column: Int, val dotted: Boolean = false) : MmlCommand(line, column)
    data class Drum(val kind: Char, val denominator: Int?, override val line: Int, override val column: Int, val dotted: Boolean = false) : MmlCommand(line, column)
    data class Gate(val eighths: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class FixedGateTail(val ticks: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Pan(val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Polyphony(val enabled: Boolean, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Detune(val cents: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Tempo(val bpm: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class HardwareLfo(
        val pms: Int,
        val ams: Int,
        val delayDenominator: Int?,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class Portamento(
        val fromLetter: Char,
        val fromAccidental: Int,
        val toLetter: Char,
        val toAccidental: Int,
        val denominator: Int?,
        val dotted: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class Chord(
        val pitches: List<Pair<Char, Int>>,
        val denominator: Int?,
        val dotted: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class Bar(override val line: Int, override val column: Int) : MmlCommand(line, column)

    companion object {
        const val LINK_NONE = 0
        const val LINK_TIE = 1
        const val LINK_SLUR = 2
    }
}

data class MmlTrack(val channel: MmlChannelId, val commands: List<MmlCommand>)

data class MmlEqDirective(val band: SongEqBand, val line: Int, val column: Int)

data class MmlDocument(
    val bpm: Float,
    val barNumerator: Int,
    val barDenominator: Int,
    val tracks: List<MmlTrack>,
    val eqBands: List<MmlEqDirective> = emptyList(),
    val dialectVersion: Int = 1,
    val lfoRate: Int = -1,
    val fm3Extended: Boolean = false
)

sealed class MmlParseResult {
    data class Success(val document: MmlDocument) : MmlParseResult()
    data class Failure(val diagnostics: List<MmlDiagnostic>) : MmlParseResult()
}

object MmlParser {
    private const val MAX_EXPANDED_COMMANDS = OpnaSequencer.MAX_EVENTS * 2

    private class ChannelSourceBuilder {
        private val text = StringBuilder()
        private val lines = mutableListOf<Int>()
        private val columns = mutableListOf<Int>()

        fun append(value: String, start: Int, line: Int) {
            var i = start
            while (i < value.length) {
                text.append(value[i])
                lines.add(line)
                columns.add(i + 1)
                i++
            }
            text.append(' ')
            lines.add(line)
            columns.add(value.length + 1)
        }

        fun build(): ChannelSource {
            val lineMap = IntArray(lines.size)
            val columnMap = IntArray(columns.size)
            var i = 0
            while (i < lines.size) {
                lineMap[i] = lines[i]
                columnMap[i] = columns[i]
                i++
            }
            return ChannelSource(text.toString(), lineMap, columnMap)
        }
    }

    private data class ChannelSource(val text: String, val lines: IntArray, val columns: IntArray) {
        fun lineAt(index: Int): Int = lines[index.coerceIn(0, lines.lastIndex)]
        fun columnAt(index: Int): Int = columns[index.coerceIn(0, columns.lastIndex)]
    }

    fun parse(source: String): MmlParseResult {
        val diagnostics = mutableListOf<MmlDiagnostic>()
        val sources = Array(MmlChannelId.entries.size) { ChannelSourceBuilder() }
        var bpm: Float? = null
        var barNumerator: Int? = null
        var barDenominator: Int? = null
        var currentChannel: MmlChannelId? = null
        var dialectVersion = 1
        var lfoRate = -1
        var fm3Extended = false
        val macroNames = mutableListOf<String>()
        val macroBodies = mutableListOf<String>()
        val eqBands = mutableListOf<MmlEqDirective>()
        val lines = source.lines()
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val rawLine = lines[lineIndex]
            val commentAt = rawLine.indexOf(';')
            val line = if (commentAt >= 0) rawLine.substring(0, commentAt) else rawLine
            val first = firstNonWhitespace(line)
            if (first >= 0) {
                if (line[first] == '#') {
                    val directive = line.substring(first).trim()
                    if (directive.startsWith("#MACRO", ignoreCase = true)) {
                        val definition = directive.substring(6).trim()
                        val split = definition.indexOfFirst { it.isWhitespace() }
                        if (split <= 0 || split >= definition.lastIndex) {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#MACRO requires a name and MML body"))
                        } else {
                            val name = definition.substring(0, split).lowercase()
                            val body = definition.substring(split).trim()
                            if (!name.all { it.isLetterOrDigit() || it == '_' }) {
                                diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#MACRO name must use letters, digits, or underscore"))
                            } else if (macroNames.contains(name)) {
                                diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#MACRO '$name' is already defined"))
                            } else {
                                macroNames.add(name)
                                macroBodies.add(body)
                            }
                        }
                    } else if (directive.startsWith("#MML", ignoreCase = true)) {
                        val value = directive.substring(4).trim().toIntOrNull()
                        if (value == null || value !in 1..2) diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#MML requires version 1 or 2"))
                        else dialectVersion = value
                    } else if (directive.startsWith("#LFO", ignoreCase = true)) {
                        val value = directive.substring(4).trim().toIntOrNull()
                        if (value == null || value !in 0..7) diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#LFO requires rate 0..7"))
                        else lfoRate = value
                    } else if (directive.startsWith("#FM3EXTEND", ignoreCase = true)) {
                        val value = directive.substring(10).trim()
                        if (value.equals("ON", ignoreCase = true)) fm3Extended = true
                        else if (value.equals("OFF", ignoreCase = true)) fm3Extended = false
                        else diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#FM3EXTEND requires ON or OFF"))
                    } else if (directive.startsWith("#BPM", ignoreCase = true)) {
                        val value = directive.substring(4).trim().toFloatOrNull()
                        if (value == null || !value.isFinite() || value <= 0f) {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#BPM requires a positive finite number"))
                        } else if (bpm != null) {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#BPM may only be declared once"))
                        } else {
                            bpm = value
                        }
                    } else if (directive.startsWith("#BAR", ignoreCase = true)) {
                        val value = directive.substring(4).trim()
                        val slash = value.indexOf('/')
                        val numerator = if (slash > 0) value.substring(0, slash).trim().toIntOrNull() else null
                        val denominator = if (slash > 0) value.substring(slash + 1).trim().toIntOrNull() else null
                        if (numerator == null || denominator == null || numerator <= 0 || denominator <= 0) {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#BAR requires a positive fraction such as 4/4"))
                        } else if (barNumerator != null) {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#BAR may only be declared once"))
                        } else {
                            barNumerator = numerator
                            barDenominator = denominator
                        }
                    } else if (directive.startsWith("#eq", ignoreCase = true)) {
                        parseEqDirective(directive, lineIndex + 1, first + 1, eqBands, diagnostics)
                    } else {
                        diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "Unknown directive"))
                    }
                } else {
                    var labelEnd = first
                    while (labelEnd < line.length && !line[labelEnd].isWhitespace()) labelEnd++
                    val channel = channelForLabel(line.substring(first, labelEnd))
                    if (channel != null) {
                        currentChannel = channel
                        val expanded = expandMacros(line.substring(labelEnd), macroNames, macroBodies, lineIndex + 1, diagnostics)
                        sources[channel.ordinal].append(expanded, 0, lineIndex + 1)
                    } else if (currentChannel != null) {
                        val expanded = expandMacros(line.substring(first), macroNames, macroBodies, lineIndex + 1, diagnostics)
                        sources[currentChannel.ordinal].append(expanded, 0, lineIndex + 1)
                    } else {
                        diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "Music data requires a preceding channel A, B, C, D, E, or R"))
                    }
                }
            }
            lineIndex++
        }
        if (bpm == null) diagnostics.add(MmlDiagnostic(1, 1, "Missing #BPM directive"))
        if (barNumerator == null || barDenominator == null) diagnostics.add(MmlDiagnostic(1, 1, "Missing #BAR directive"))
        if (diagnostics.isNotEmpty()) return MmlParseResult.Failure(diagnostics)
        val resultTracks = mutableListOf<MmlTrack>()
        var i = 0
        while (i < sources.size) {
            val channelSource = sources[i].build()
            val commands = mutableListOf<MmlCommand>()
            if (channelSource.text.isNotBlank()) {
                parseCommands(channelSource, 0, channelSource.text.length, dialectVersion, 0, commands, diagnostics)
            }
            resultTracks.add(MmlTrack(MmlChannelId.entries[i], commands))
            i++
        }
        if (diagnostics.isNotEmpty()) return MmlParseResult.Failure(diagnostics)
        if (dialectVersion == 1 && (lfoRate >= 0 || fm3Extended)) {
            diagnostics.add(MmlDiagnostic(1, 1, "#LFO and #FM3EXTEND require #MML 2"))
        }
        if (dialectVersion == 1 && macroNames.isNotEmpty()) diagnostics.add(MmlDiagnostic(1, 1, "#MACRO requires #MML 2"))
        if (diagnostics.isNotEmpty()) return MmlParseResult.Failure(diagnostics)
        return MmlParseResult.Success(
            MmlDocument(bpm!!, barNumerator!!, barDenominator!!, resultTracks, eqBands, dialectVersion, lfoRate, fm3Extended)
        )
    }

    private fun parseEqDirective(
        directive: String,
        line: Int,
        column: Int,
        output: MutableList<MmlEqDirective>,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        val tokens = directive.split(Regex("\\s+"))
        if (tokens.size != 5) {
            diagnostics.add(MmlDiagnostic(line, column, "#eq requires: #eq peak <frequencyHz> <gainDb> <q>"))
            return
        }
        val type = when (tokens[1].lowercase()) {
            "peak" -> EqType.PEAK
            else -> {
                diagnostics.add(MmlDiagnostic(line, column, "Unknown EQ type '${tokens[1]}'"))
                return
            }
        }
        val frequencyHz = tokens[2].toFloatOrNull()
        val gainDb = tokens[3].toFloatOrNull()
        val q = tokens[4].toFloatOrNull()
        if (frequencyHz == null || gainDb == null || q == null ||
            !frequencyHz.isFinite() || !gainDb.isFinite() || !q.isFinite()
        ) {
            diagnostics.add(MmlDiagnostic(line, column, "#eq values must be finite numbers"))
            return
        }
        if (frequencyHz <= 0f) {
            diagnostics.add(MmlDiagnostic(line, column, "#eq frequencyHz must be greater than zero"))
            return
        }
        if (q <= 0f) {
            diagnostics.add(MmlDiagnostic(line, column, "#eq q must be greater than zero"))
            return
        }
        output.add(MmlEqDirective(SongEqBand(type, frequencyHz, gainDb, q), line, column))
    }

    private fun parseCommands(
        source: ChannelSource,
        start: Int,
        end: Int,
        dialectVersion: Int,
        loopDepth: Int,
        output: MutableList<MmlCommand>,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        val text = source.text
        var i = start
        while (i < end) {
            val raw = text[i]
            val c = raw.lowercaseChar()
            if (c.isWhitespace()) {
                i++
            } else if (c == '[') {
                if (dialectVersion == 1 && loopDepth > 0) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Nested loops are not supported"))
                    return
                }
                if (loopDepth >= MAX_LOOP_DEPTH) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Loop nesting exceeds $MAX_LOOP_DEPTH"))
                    return
                }
                val close = findLoopClose(text, i + 1, end)
                if (close < 0) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Unclosed loop"))
                    return
                }
                var repeatEnd = close + 1
                while (repeatEnd < end && text[repeatEnd].isDigit()) repeatEnd++
                val repeatCount = text.substring(close + 1, repeatEnd).toIntOrNull()
                if (repeatCount == null || repeatCount <= 0) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(close), source.columnAt(close), "Loop requires a positive repeat count"))
                    return
                }
                val loopCommands = mutableListOf<MmlCommand>()
                parseCommands(source, i + 1, close, dialectVersion, loopDepth + 1, loopCommands, diagnostics)
                val expandedSize = output.size.toLong() + loopCommands.size.toLong() * repeatCount.toLong()
                if (expandedSize > MAX_EXPANDED_COMMANDS) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Expanded loop exceeds parser command capacity"))
                    return
                }
                var repeatIndex = 0
                while (repeatIndex < repeatCount) {
                    output.addAll(loopCommands)
                    repeatIndex++
                }
                i = repeatEnd
            } else if (c == ']') {
                diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Unexpected loop close"))
                return
            } else if (c == '@') {
                val tokenStart = i
                i++
                val valueStart = i
                while (i < end && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                if (valueStart == i) diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Missing instrument after @"))
                else output.add(MmlCommand.Instrument(text.substring(valueStart, i).lowercase(), source.lineAt(tokenStart), source.columnAt(tokenStart)))
            } else if (raw == 'V' || c == 'v' || c == 'o' || c == 'l') {
                val tokenStart = i
                i++
                val valueStart = i
                while (i < end && text[i].isDigit()) i++
                val value = text.substring(valueStart, i).toIntOrNull()
                if (value == null) diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Command $c requires an integer"))
                else if (raw == 'V') output.add(MmlCommand.FineVolume(value, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                else if (c == 'v') output.add(MmlCommand.Volume(value, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                else if (c == 'o') output.add(MmlCommand.Octave(value, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                else output.add(MmlCommand.DefaultLength(value, source.lineAt(tokenStart), source.columnAt(tokenStart)))
            } else if (raw == 'P') {
                val tokenStart = i
                i++
                if (i >= end || (text[i] != '0' && text[i] != '1')) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "P requires P0 or P1"))
                } else {
                    output.add(MmlCommand.Polyphony(text[i] == '1', source.lineAt(tokenStart), source.columnAt(tokenStart)))
                    i++
                }
            } else if (raw == 'Q' || raw == 'q' || c == 'p' || raw == 'D' || raw == 'T') {
                val tokenStart = i
                i++
                var sign = 1
                if (i < end && (text[i] == '+' || text[i] == '-')) {
                    if (text[i] == '-') sign = -1
                    i++
                }
                val valueStart = i
                while (i < end && text[i].isDigit()) i++
                val value = text.substring(valueStart, i).toIntOrNull()
                if (value == null) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Command $raw requires an integer"))
                } else if (raw == 'Q') {
                    output.add(MmlCommand.Gate(value * sign, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else if (raw == 'q') {
                    output.add(MmlCommand.FixedGateTail(value * sign, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else if (raw == 'D') {
                    output.add(MmlCommand.Detune(value * sign, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else if (raw == 'T') {
                    output.add(MmlCommand.Tempo(value * sign, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else {
                    output.add(MmlCommand.Pan(value * sign, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                }
            } else if (raw == 'H') {
                val tokenStart = i
                i++
                val pmsStart = i
                while (i < end && text[i].isDigit()) i++
                val pms = text.substring(pmsStart, i).toIntOrNull()
                if (i >= end || text[i] != ',') {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "H requires H<pms>,<ams>[,l<delay>]"))
                } else {
                    i++
                    val amsStart = i
                    while (i < end && text[i].isDigit()) i++
                    val ams = text.substring(amsStart, i).toIntOrNull()
                    var delay: Int? = null
                    if (i < end && text[i] == ',') {
                        i++
                        if (i < end && text[i].lowercaseChar() == 'l') i++
                        val delayStart = i
                        while (i < end && text[i].isDigit()) i++
                        delay = text.substring(delayStart, i).toIntOrNull()
                    }
                    if (pms == null || ams == null || (i > tokenStart && delay == null && text[i - 1] == ',')) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "H requires H<pms>,<ams>[,l<delay>]"))
                    } else {
                        output.add(MmlCommand.HardwareLfo(pms, ams, delay, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                    }
                }
            } else if (c == ')' || c == '(') {
                val tokenStart = i
                i++
                val valueStart = i
                while (i < end && text[i].isDigit()) i++
                val amount = if (valueStart == i) 4 else text.substring(valueStart, i).toIntOrNull() ?: 4
                output.add(MmlCommand.RelativeVolume(if (c == ')') amount else -amount, source.lineAt(tokenStart), source.columnAt(tokenStart)))
            } else if (c == '>' || c == '<') {
                output.add(MmlCommand.OctaveShift(if (c == '>') 1 else -1, source.lineAt(i), source.columnAt(i)))
                i++
            } else if (c == '|') {
                output.add(MmlCommand.Bar(source.lineAt(i), source.columnAt(i)))
                i++
            } else if (c == '{') {
                val tokenStart = i
                val close = text.indexOf('}', i + 1)
                if (close < 0 || close >= end) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Unclosed portamento"))
                    return
                }
                val content = text.substring(i + 1, close).filterNot { it.isWhitespace() }
                i = close + 1
                val lengthStart = i
                while (i < end && text[i].isDigit()) i++
                val denominator = if (lengthStart == i) null else text.substring(lengthStart, i).toIntOrNull()
                var dotted = false
                if (i < end && text[i] == '.') { dotted = true; i++ }
                if (content.indexOf(',') >= 0) {
                    val tokens = content.split(',')
                    val pitches = ArrayList<Pair<Char, Int>>(tokens.size)
                    var tokenIndex = 0
                    var valid = tokens.size in 2..MAX_CHORD_NOTES
                    while (tokenIndex < tokens.size && valid) {
                        val note = parseInlineNote(tokens[tokenIndex], 0)
                        if (note == null || note.third != tokens[tokenIndex].length) valid = false
                        else pitches.add(note.first)
                        tokenIndex++
                    }
                    if (!valid) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Chord requires 2..$MAX_CHORD_NOTES comma-separated notes, such as {c,e,g}4"))
                    } else {
                        output.add(MmlCommand.Chord(pitches, denominator, dotted, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                    }
                } else {
                    val firstNote = parseInlineNote(content, 0)
                    val secondNote = if (firstNote != null) parseInlineNote(content, firstNote.third) else null
                    if (firstNote == null || secondNote == null || secondNote.third != content.length) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Portamento requires exactly two notes, such as {cg}4"))
                    } else {
                        output.add(
                            MmlCommand.Portamento(
                                firstNote.first.first,
                                firstNote.first.second,
                                secondNote.first.first,
                                secondNote.first.second,
                                denominator,
                                dotted,
                                source.lineAt(tokenStart),
                                source.columnAt(tokenStart)
                            )
                        )
                    }
                }
            } else if (c in 'a'..'g') {
                val tokenStart = i
                i++
                var accidental = 0
                if (i < end && (text[i] == '+' || text[i] == '#')) { accidental = 1; i++ }
                else if (i < end && text[i] == '-') { accidental = -1; i++ }
                val lengthStart = i
                while (i < end && text[i].isDigit()) i++
                val denominator = if (lengthStart == i) null else text.substring(lengthStart, i).toIntOrNull()
                var dotted = false
                if (i < end && text[i] == '.') { dotted = true; i++ }
                var link = MmlCommand.LINK_NONE
                if (i < end && text[i] == '&') {
                    i++
                    link = if (i < end && text[i] == '&') {
                        i++
                        MmlCommand.LINK_SLUR
                    } else {
                        MmlCommand.LINK_TIE
                    }
                }
                output.add(MmlCommand.Note(c, accidental, denominator, source.lineAt(tokenStart), source.columnAt(tokenStart), dotted, link))
            } else if (c == 'r' || c == 'k' || c == 's' || c == 'h' || c == 't' || c == 'y' || c == 'i') {
                val tokenStart = i
                i++
                val lengthStart = i
                while (i < end && text[i].isDigit()) i++
                val denominator = if (lengthStart == i) null else text.substring(lengthStart, i).toIntOrNull()
                var dotted = false
                if (i < end && text[i] == '.') { dotted = true; i++ }
                if (c == 'r') output.add(MmlCommand.Rest(denominator, source.lineAt(tokenStart), source.columnAt(tokenStart), dotted))
                else output.add(MmlCommand.Drum(c, denominator, source.lineAt(tokenStart), source.columnAt(tokenStart), dotted))
            } else {
                diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Unknown token '${text[i]}'"))
                i++
            }
        }
    }

    private fun parseInlineNote(value: String, start: Int): Triple<Pair<Char, Int>, Int, Int>? {
        if (start >= value.length) return null
        val letter = value[start].lowercaseChar()
        if (letter !in 'a'..'g') return null
        var index = start + 1
        var accidental = 0
        if (index < value.length && (value[index] == '+' || value[index] == '#')) {
            accidental = 1
            index++
        } else if (index < value.length && value[index] == '-') {
            accidental = -1
            index++
        }
        return Triple(Pair(letter, accidental), start, index)
    }

    private fun firstNonWhitespace(value: String): Int {
        var i = 0
        while (i < value.length) {
            if (!value[i].isWhitespace()) return i
            i++
        }
        return -1
    }

    private fun expandMacros(
        source: String,
        names: List<String>,
        bodies: List<String>,
        line: Int,
        diagnostics: MutableList<MmlDiagnostic>
    ): String {
        var current = source
        var depth = 0
        while (current.indexOf('$') >= 0 && depth < MAX_MACRO_DEPTH) {
            val output = StringBuilder()
            var i = 0
            var changed = false
            while (i < current.length) {
                if (current[i] != '$') {
                    output.append(current[i])
                    i++
                } else {
                    val tokenStart = i
                    i++
                    val nameStart = i
                    while (i < current.length && (current[i].isLetterOrDigit() || current[i] == '_')) i++
                    val name = current.substring(nameStart, i).lowercase()
                    val macroIndex = names.indexOf(name)
                    if (name.isEmpty() || macroIndex < 0) {
                        diagnostics.add(MmlDiagnostic(line, tokenStart + 1, "Unknown MML macro '$name'"))
                        return current
                    }
                    output.append(bodies[macroIndex])
                    changed = true
                }
                if (output.length > MAX_MACRO_CHARACTERS) {
                    diagnostics.add(MmlDiagnostic(line, 1, "Expanded macros exceed parser capacity"))
                    return current
                }
            }
            current = output.toString()
            if (!changed) break
            depth++
        }
        if (current.indexOf('$') >= 0) diagnostics.add(MmlDiagnostic(line, 1, "Macro expansion exceeds depth $MAX_MACRO_DEPTH"))
        return current
    }

    private fun findLoopClose(value: String, start: Int, end: Int): Int {
        var depth = 0
        var i = start
        while (i < end) {
            if (value[i] == '[') depth++
            if (value[i] == ']') {
                if (depth == 0) return i
                depth--
            }
            i++
        }
        return -1
    }

    private const val MAX_LOOP_DEPTH = 8
    private const val MAX_CHORD_NOTES = 8
    private const val MAX_MACRO_DEPTH = 8
    private const val MAX_MACRO_CHARACTERS = 65_536

    private fun channelForLabel(value: String): MmlChannelId? {
        if (value != value.uppercase()) return null
        return when (value) {
            "A" -> MmlChannelId.A
            "B" -> MmlChannelId.B
            "C" -> MmlChannelId.C
            "D" -> MmlChannelId.D
            "E" -> MmlChannelId.E
            "F" -> MmlChannelId.F
            "G" -> MmlChannelId.G
            "H" -> MmlChannelId.H
            "I" -> MmlChannelId.I
            "C1" -> MmlChannelId.C1
            "C2" -> MmlChannelId.C2
            "C3" -> MmlChannelId.C3
            "C4" -> MmlChannelId.C4
            "R" -> MmlChannelId.R
            else -> null
        }
    }
}
