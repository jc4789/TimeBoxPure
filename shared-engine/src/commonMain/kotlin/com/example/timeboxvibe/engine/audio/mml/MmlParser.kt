package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.EqType
import com.example.timeboxvibe.engine.SongEqBand

data class MmlDiagnostic(val line: Int, val column: Int, val reason: String)

enum class MmlChannelId { A, B, C, D, E, R }

sealed class MmlCommand(open val line: Int, open val column: Int) {
    data class Instrument(val value: String, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Volume(val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Octave(val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class DefaultLength(val denominator: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class OctaveShift(val delta: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Note(val letter: Char, val accidental: Int, val denominator: Int?, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Rest(val denominator: Int?, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Drum(val kind: Char, val denominator: Int?, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Bar(override val line: Int, override val column: Int) : MmlCommand(line, column)
}

data class MmlTrack(val channel: MmlChannelId, val commands: List<MmlCommand>)

data class MmlEqDirective(val band: SongEqBand, val line: Int, val column: Int)

data class MmlDocument(
    val bpm: Float,
    val barNumerator: Int,
    val barDenominator: Int,
    val tracks: List<MmlTrack>,
    val eqBands: List<MmlEqDirective> = emptyList()
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
                    if (directive.startsWith("#BPM", ignoreCase = true)) {
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
                    val channel = channelForLabel(line[first])
                    if (channel != null && (first + 1 == line.length || line[first + 1].isWhitespace())) {
                        currentChannel = channel
                        sources[channel.ordinal].append(line, first + 1, lineIndex + 1)
                    } else if (currentChannel != null) {
                        sources[currentChannel.ordinal].append(line, first, lineIndex + 1)
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
                parseCommands(channelSource, 0, channelSource.text.length, true, commands, diagnostics)
            }
            resultTracks.add(MmlTrack(MmlChannelId.entries[i], commands))
            i++
        }
        if (diagnostics.isNotEmpty()) return MmlParseResult.Failure(diagnostics)
        return MmlParseResult.Success(MmlDocument(bpm!!, barNumerator!!, barDenominator!!, resultTracks, eqBands))
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
        loopsAllowed: Boolean,
        output: MutableList<MmlCommand>,
        diagnostics: MutableList<MmlDiagnostic>
    ) {
        val text = source.text
        var i = start
        while (i < end) {
            val c = text[i].lowercaseChar()
            if (c.isWhitespace()) {
                i++
            } else if (c == '[') {
                if (!loopsAllowed) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Nested loops are not supported"))
                    return
                }
                val close = findLoopClose(text, i + 1, end)
                if (close == -2) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Nested loops are not supported"))
                    return
                }
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
                parseCommands(source, i + 1, close, false, loopCommands, diagnostics)
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
            } else if (c == 'v' || c == 'o' || c == 'l') {
                val tokenStart = i
                i++
                val valueStart = i
                while (i < end && text[i].isDigit()) i++
                val value = text.substring(valueStart, i).toIntOrNull()
                if (value == null) diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Command $c requires an integer"))
                else if (c == 'v') output.add(MmlCommand.Volume(value, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                else if (c == 'o') output.add(MmlCommand.Octave(value, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                else output.add(MmlCommand.DefaultLength(value, source.lineAt(tokenStart), source.columnAt(tokenStart)))
            } else if (c == '>' || c == '<') {
                output.add(MmlCommand.OctaveShift(if (c == '>') 1 else -1, source.lineAt(i), source.columnAt(i)))
                i++
            } else if (c == '|') {
                output.add(MmlCommand.Bar(source.lineAt(i), source.columnAt(i)))
                i++
            } else if (c in 'a'..'g') {
                val tokenStart = i
                i++
                var accidental = 0
                if (i < end && (text[i] == '+' || text[i] == '#')) { accidental = 1; i++ }
                else if (i < end && text[i] == '-') { accidental = -1; i++ }
                val lengthStart = i
                while (i < end && text[i].isDigit()) i++
                val denominator = if (lengthStart == i) null else text.substring(lengthStart, i).toIntOrNull()
                output.add(MmlCommand.Note(c, accidental, denominator, source.lineAt(tokenStart), source.columnAt(tokenStart)))
            } else if (c == 'r' || c == 'k' || c == 's' || c == 'h') {
                val tokenStart = i
                i++
                val lengthStart = i
                while (i < end && text[i].isDigit()) i++
                val denominator = if (lengthStart == i) null else text.substring(lengthStart, i).toIntOrNull()
                if (c == 'r') output.add(MmlCommand.Rest(denominator, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                else output.add(MmlCommand.Drum(c, denominator, source.lineAt(tokenStart), source.columnAt(tokenStart)))
            } else {
                diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Unknown token '${text[i]}'"))
                i++
            }
        }
    }

    private fun firstNonWhitespace(value: String): Int {
        var i = 0
        while (i < value.length) {
            if (!value[i].isWhitespace()) return i
            i++
        }
        return -1
    }

    private fun findLoopClose(value: String, start: Int, end: Int): Int {
        var i = start
        while (i < end) {
            if (value[i] == '[') return -2
            if (value[i] == ']') return i
            i++
        }
        return -1
    }

    private fun channelForLabel(value: Char): MmlChannelId? = when (value) {
        'A' -> MmlChannelId.A
        'B' -> MmlChannelId.B
        'C' -> MmlChannelId.C
        'D' -> MmlChannelId.D
        'E' -> MmlChannelId.E
        'R' -> MmlChannelId.R
        else -> null
    }
}
