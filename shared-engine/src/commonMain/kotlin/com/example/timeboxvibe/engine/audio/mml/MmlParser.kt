package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.PmdPerformanceLaws
import com.example.timeboxvibe.engine.EqType
import com.example.timeboxvibe.engine.SongEqBand

data class MmlDiagnostic(val line: Int, val column: Int, val reason: String)

enum class MmlChannelId { A, B, C, D, E, F, G, H, I, C1, C2, C3, C4, K, R }

sealed class MmlCommand(open val line: Int, open val column: Int) {
    /** Canonical authored order assigned after all logical-part sources are parsed. */
    internal var sourceOrder: Int = -1
    /** Occurrence order after loop expansion within this logical source. */
    internal var expansionOccurrence: Int = -1

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
    data class Gate(val value: Int, val scale: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class GateTail(
        val fromClocks: Int?,
        val toClocks: Int?,
        val minimumClocks: Int?,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class SoftwareEnvelope(
        val format: Int,
        val attack: Int,
        val decay: Int,
        val sustain: Int,
        val release: Int,
        val sustainLevel: Int,
        val attackLevel: Int,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class EnvelopeClockMode(val mode: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Pan(val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Polyphony(val enabled: Boolean, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Detune(val cents: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class FmSlotMask(val mask: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class FmSlotDetune(
        val mask: Int,
        val value: Int,
        val relative: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class FmOperatorTl(
        val mask: Int,
        val value: Int,
        val relative: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class FmFeedback(
        val value: Int,
        val relative: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class FmSlotKeyOnDelay(
        val mask: Int,
        val delay: Int,
        val lengthDenominator: Int?,
        val dotted: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class RhythmShot(val voiceMask: Int, val dump: Boolean, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class RhythmMasterLevel(
        val value: Int,
        val relative: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class RhythmVoiceLevel(
        val voice: Int,
        val value: Int,
        val relative: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class RhythmVoicePan(val voice: Int, val pan: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class RhythmPatternSelect(val pattern: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class Tempo(val bpm: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class HardwareLfo(
        val pms: Int,
        val ams: Int,
        val delayKind: Int,
        val delayValue: Int,
        val delayDotted: Boolean,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class HardwareLfoGlobal(
        val enabled: Boolean,
        val rate: Int?,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class SsgToneEnable(val enabled: Boolean, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SsgNoiseEnable(val enabled: Boolean, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SsgNoisePeriod(val period: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SsgHardwareEnvelopePeriod(val period: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SsgHardwareEnvelopeShape(val shape: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SoftwareLfoDefine(
        val index: Int,
        val delay: Int,
        val speed: Int,
        val depthA: Int,
        val depthB: Int,
        override val line: Int,
        override val column: Int
    ) : MmlCommand(line, column)
    data class SoftwareLfoSwitch(val index: Int, val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SoftwareLfoWaveform(val index: Int, val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SoftwareLfoClockMode(val index: Int, val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SoftwareLfoTlMask(val index: Int, val value: Int, override val line: Int, override val column: Int) : MmlCommand(line, column)
    data class SoftwareLfoDepthEvolution(
        val index: Int,
        val speed: Int,
        val depth: Int,
        val time: Int,
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
        const val HARDWARE_LFO_DELAY_NONE = 0
        const val HARDWARE_LFO_DELAY_RAW_CLOCKS = 1
        const val HARDWARE_LFO_DELAY_NOTE_LENGTH = 2
    }
}

data class MmlTrack(val channel: MmlChannelId, val commands: List<MmlCommand>)
data class MmlRhythmPattern(val id: Int, val commands: List<MmlCommand>)

data class MmlEqDirective(val band: SongEqBand, val line: Int, val column: Int)

data class MmlDocument(
    val bpm: Float,
    val barNumerator: Int,
    val barDenominator: Int,
    val tracks: List<MmlTrack>,
    val eqBands: List<MmlEqDirective> = emptyList(),
    val lfoRate: Int = -1,
    val fm3Extended: Boolean = false,
    val bpmMilli: Int = (bpm * PmdPerformanceLaws.BPM_MILLI_SCALE + 0.5f).toInt(),
    val pmdClocksPerQuarter: Int = PmdPerformanceLaws.DEFAULT_CLOCKS_PER_QUARTER,
    val envelopeClockMode: Int = PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL,
    val rhythmPatterns: List<MmlRhythmPattern> = emptyList()
)

sealed class MmlParseResult {
    data class Success(val document: MmlDocument) : MmlParseResult()
    data class Failure(val diagnostics: List<MmlDiagnostic>) : MmlParseResult()
}

object MmlParser {
    private const val MAX_EXPANDED_COMMANDS = CompiledOpnaSong.MAX_AUTHORED_EVENTS * 2

    private class ChannelSourceBuilder {
        private val text = StringBuilder()
        private val lines = mutableListOf<Int>()
        private val columns = mutableListOf<Int>()

        fun append(value: ExpandedLine, line: Int) {
            var i = 0
            while (i < value.text.length) {
                text.append(value.text[i])
                lines.add(line)
                columns.add(value.columns[i])
                i++
            }
            text.append(' ')
            lines.add(line)
            columns.add(if (value.columns.isEmpty()) 1 else value.columns[value.columns.lastIndex] + 1)
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

    private data class ExpandedLine(val text: String, val columns: IntArray)

    private data class ChannelSource(val text: String, val lines: IntArray, val columns: IntArray) {
        fun lineAt(index: Int): Int = lines[index.coerceIn(0, lines.lastIndex)]
        fun columnAt(index: Int): Int = columns[index.coerceIn(0, columns.lastIndex)]
    }

    fun parse(source: String): MmlParseResult {
        val diagnostics = mutableListOf<MmlDiagnostic>()
        val sources = Array(MmlChannelId.entries.size) { ChannelSourceBuilder() }
        val rhythmPatternSources = arrayOfNulls<ChannelSourceBuilder>(256)
        var bpm: Float? = null
        var barNumerator: Int? = null
        var barDenominator: Int? = null
        var currentChannel: MmlChannelId? = null
        var currentRhythmPattern = -1
        var sawMmlV2Directive = false
        var lfoRate = -1
        var fm3Extended = false
        var bpmMilli: Int? = null
        var pmdClocksPerQuarter = PmdPerformanceLaws.DEFAULT_CLOCKS_PER_QUARTER
        var envelopeClockMode = PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL
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
                        if (value != 2) diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#MML requires version 2"))
                        else sawMmlV2Directive = true
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
                        val sourceValue = directive.substring(4).trim()
                        val exactMilli = parseBpmMilli(sourceValue)
                        val value = sourceValue.toFloatOrNull()
                        if (value == null || !value.isFinite() || value <= 0f || exactMilli == null) {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#BPM requires a positive number with at most three decimals"))
                        } else if (bpm != null) {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#BPM may only be declared once"))
                        } else {
                            bpm = value
                            bpmMilli = exactMilli
                        }
                    } else if (directive.startsWith("#PMDCLOCK", ignoreCase = true)) {
                        val value = directive.substring(9).trim().toIntOrNull()
                        if (value == null || value <= 0 || CompiledOpnaSong.TICKS_PER_QUARTER % value != 0) {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#PMDCLOCK must be a positive divisor of ${CompiledOpnaSong.TICKS_PER_QUARTER}"))
                        } else {
                            pmdClocksPerQuarter = value
                        }
                    } else if (directive.startsWith("#ENVELOPESPEED", ignoreCase = true)) {
                        val value = directive.substring(14).trim()
                        if (value.equals("Normal", ignoreCase = true)) {
                            envelopeClockMode = PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL
                        } else if (value.equals("Extend", ignoreCase = true)) {
                            envelopeClockMode = PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED
                        } else {
                            diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "#EnvelopeSpeed requires Normal or Extend"))
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
                    val label = line.substring(first, labelEnd)
                    val patternId = if (label.length > 1 && label[0] == 'R') label.substring(1).toIntOrNull() else null
                    val channel = channelForLabel(label)
                    if (patternId != null && patternId in 0..255) {
                        currentChannel = null
                        currentRhythmPattern = patternId
                        val builder = rhythmPatternSources[patternId] ?: ChannelSourceBuilder().also { rhythmPatternSources[patternId] = it }
                        val expanded = expandMacros(
                            line.substring(labelEnd), labelEnd, macroNames, macroBodies,
                            lineIndex + 1, diagnostics
                        )
                        builder.append(expanded, lineIndex + 1)
                    } else if (channel != null) {
                        currentChannel = channel
                        currentRhythmPattern = -1
                        val expanded = expandMacros(
                            line.substring(labelEnd), labelEnd, macroNames, macroBodies,
                            lineIndex + 1, diagnostics
                        )
                        sources[channel.ordinal].append(expanded, lineIndex + 1)
                    } else if (currentRhythmPattern >= 0) {
                        val expanded = expandMacros(
                            line.substring(first), first, macroNames, macroBodies,
                            lineIndex + 1, diagnostics
                        )
                        rhythmPatternSources[currentRhythmPattern]!!.append(expanded, lineIndex + 1)
                    } else if (currentChannel != null) {
                        val expanded = expandMacros(
                            line.substring(first), first, macroNames, macroBodies,
                            lineIndex + 1, diagnostics
                        )
                        sources[currentChannel.ordinal].append(expanded, lineIndex + 1)
                    } else {
                        diagnostics.add(MmlDiagnostic(lineIndex + 1, first + 1, "Music data requires a preceding channel A, B, C, D, E, or R"))
                    }
                }
            }
            lineIndex++
        }
        if (!sawMmlV2Directive) diagnostics.add(MmlDiagnostic(1, 1, "Missing #MML 2 directive"))
        if (bpm == null) diagnostics.add(MmlDiagnostic(1, 1, "Missing #BPM directive"))
        if (barNumerator == null || barDenominator == null) diagnostics.add(MmlDiagnostic(1, 1, "Missing #BAR directive"))
        if (diagnostics.isNotEmpty()) return MmlParseResult.Failure(diagnostics)
        val resultTracks = mutableListOf<MmlTrack>()
        var i = 0
        while (i < sources.size) {
            val channelSource = sources[i].build()
            val commands = mutableListOf<MmlCommand>()
            if (channelSource.text.isNotBlank()) {
                parseCommands(
                    channelSource, 0, channelSource.text.length,
                    MmlChannelId.entries[i], 0, commands, diagnostics
                )
            }
            assignExpansionOccurrences(commands)
            resultTracks.add(MmlTrack(MmlChannelId.entries[i], commands))
            i++
        }
        val rhythmPatterns = mutableListOf<MmlRhythmPattern>()
        i = 0
        while (i < rhythmPatternSources.size) {
            val builder = rhythmPatternSources[i]
            if (builder != null) {
                val patternSource = builder.build()
                val commands = mutableListOf<MmlCommand>()
                parseCommands(patternSource, 0, patternSource.text.length, MmlChannelId.R, 0, commands, diagnostics)
                assignExpansionOccurrences(commands)
                rhythmPatterns.add(MmlRhythmPattern(i, commands))
            }
            i++
        }
        if (diagnostics.isNotEmpty()) return MmlParseResult.Failure(diagnostics)
        assignSourceOrder(resultTracks, rhythmPatterns)
        return MmlParseResult.Success(
            MmlDocument(
                bpm!!,
                barNumerator!!,
                barDenominator!!,
                resultTracks,
                eqBands,
                lfoRate,
                fm3Extended,
                bpmMilli!!,
                pmdClocksPerQuarter,
                envelopeClockMode,
                rhythmPatterns
            )
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
        channel: MmlChannelId,
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
                parseCommands(source, i + 1, close, channel, loopDepth + 1, loopCommands, diagnostics)
                val expandedSize = output.size.toLong() + loopCommands.size.toLong() * repeatCount.toLong()
                if (expandedSize > MAX_EXPANDED_COMMANDS) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Expanded loop exceeds parser command capacity"))
                    return
                }
                output.addAll(loopCommands)
                var repeatIndex = 1
                while (repeatIndex < repeatCount) {
                    parseCommands(
                        source,
                        i + 1,
                        close,
                        channel,
                        loopDepth + 1,
                        output,
                        diagnostics
                    )
                    repeatIndex++
                }
                i = repeatEnd
            } else if (c == ']') {
                diagnostics.add(MmlDiagnostic(source.lineAt(i), source.columnAt(i), "Unexpected loop close"))
                return
            } else if (c == '\\') {
                val tokenStart = i
                i++
                if (i >= end) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Rhythm control requires a command after backslash"))
                } else if (text[i] == 'V') {
                    i++
                    val relative = i < end && (text[i] == '+' || text[i] == '-')
                    var sign = 1
                    if (relative) {
                        if (text[i] == '-') sign = -1
                        i++
                    }
                    val value = parseUnsignedInteger(text, i, end)
                    i = value.second
                    if (value.first == null) diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "\\V requires a level"))
                    else output.add(MmlCommand.RhythmMasterLevel(value.first!! * sign, relative, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else if (text[i] == 'v') {
                    i++
                    val voice = if (i < end) rhythmVoiceFor(text[i]) else -1
                    if (voice >= 0) i++
                    val relative = i < end && (text[i] == '+' || text[i] == '-')
                    var sign = 1
                    if (relative) {
                        if (text[i] == '-') sign = -1
                        i++
                    }
                    val value = parseUnsignedInteger(text, i, end)
                    i = value.second
                    if (voice < 0 || value.first == null) diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "\\v requires voice b/s/c/h/t/i and level"))
                    else output.add(MmlCommand.RhythmVoiceLevel(voice, value.first!! * sign, relative, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else if (text[i] in "lmr") {
                    val pan = when (text[i]) { 'l' -> 1; 'r' -> 2; else -> 0 }
                    i++
                    val voice = if (i < end) rhythmVoiceFor(text[i]) else -1
                    if (voice >= 0) i++
                    if (voice < 0) diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Rhythm pan requires voice b/s/c/h/t/i"))
                    else output.add(MmlCommand.RhythmVoicePan(voice, pan, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else {
                    val voice = rhythmVoiceFor(text[i])
                    if (voice < 0) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Unknown rhythm control"))
                        i++
                    } else {
                        i++
                        val dump = i < end && text[i] == 'p'
                        if (dump) i++
                        output.add(MmlCommand.RhythmShot(1 shl voice, dump, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                    }
                }
            } else if (c == '@') {
                val tokenStart = i
                i++
                val valueStart = i
                if (i < end && text[i].isDigit()) {
                    while (i < end && text[i].isDigit()) i++
                } else {
                    while (i < end && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                }
                if (valueStart == i) diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "Missing instrument after @"))
                else output.add(MmlCommand.Instrument(text.substring(valueStart, i).lowercase(), source.lineAt(tokenStart), source.columnAt(tokenStart)))
            } else if (raw == 'V' || c == 'v' || (c == 'o' && raw != 'O') || c == 'l') {
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
            } else if (raw == 'q') {
                val tokenStart = i
                i++
                val fromStart = i
                while (i < end && text[i].isDigit()) i++
                val from = if (fromStart == i) null else text.substring(fromStart, i).toIntOrNull()
                var to: Int? = null
                var missingRangeEnd = false
                if (i < end && text[i] == '-') {
                    i++
                    val toStart = i
                    while (i < end && text[i].isDigit()) i++
                    to = if (toStart == i) null else text.substring(toStart, i).toIntOrNull()
                    missingRangeEnd = to == null
                }
                var minimum: Int? = null
                var missingMinimum = false
                if (i < end && text[i] == ',') {
                    i++
                    val minimumStart = i
                    while (i < end && text[i].isDigit()) i++
                    minimum = if (minimumStart == i) null else text.substring(minimumStart, i).toIntOrNull()
                    missingMinimum = minimum == null
                }
                if (missingRangeEnd || missingMinimum) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "q range and minimum separators require a following clock value"))
                } else if (from == null && to == null && minimum == null) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "q requires a tail range and/or minimum clock value"))
                } else if (to != null && from == null) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "q random range requires its first value"))
                } else {
                    output.add(MmlCommand.GateTail(from, to, minimum, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                }
            } else if (raw == 'R' && channel == MmlChannelId.K) {
                val tokenStart = i
                i++
                val value = parseUnsignedInteger(text, i, end)
                i = value.second
                if (value.first == null) diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "K-part R requires pattern 0..255"))
                else output.add(MmlCommand.RhythmPatternSelect(value.first!!, source.lineAt(tokenStart), source.columnAt(tokenStart)))
            } else if (raw == 'F' && i + 1 < end && text[i + 1] == 'B') {
                val tokenStart = i
                i += 2
                val relative = i < end && (text[i] == '+' || text[i] == '-')
                var sign = 1
                if (relative) {
                    if (text[i] == '-') sign = -1
                    i++
                }
                val parsed = parseUnsignedInteger(text, i, end)
                i = parsed.second
                if (parsed.first == null) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "FB requires an integer"))
                } else {
                    output.add(MmlCommand.FmFeedback(parsed.first!! * sign, relative, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                }
            } else if (raw == 'O') {
                val tokenStart = i
                i++
                val mask = parseUnsignedInteger(text, i, end)
                i = mask.second
                if (i < end && text[i] == ',') i++ else {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "O requires slotMask,value"))
                }
                val relative = i < end && (text[i] == '+' || text[i] == '-')
                var sign = 1
                if (relative) {
                    if (text[i] == '-') sign = -1
                    i++
                }
                val value = parseUnsignedInteger(text, i, end)
                i = value.second
                if (mask.first == null || value.first == null) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "O requires slotMask,value"))
                } else {
                    output.add(MmlCommand.FmOperatorTl(mask.first!!, value.first!! * sign, relative, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                }
            } else if (raw == 'Q' || c == 'p' || raw == 'D' || raw == 'T') {
                val tokenStart = i
                i++
                var gateScale = 8
                if (raw == 'Q' && i < end && text[i] == '%') {
                    gateScale = 256
                    i++
                }
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
                    output.add(MmlCommand.Gate(value * sign, gateScale, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else if (raw == 'D') {
                    output.add(MmlCommand.Detune(value * sign, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else if (raw == 'T') {
                    output.add(MmlCommand.Tempo(value * sign, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else {
                    output.add(MmlCommand.Pan(value * sign, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                }
            } else if (raw == 'M') {
                val tokenStart = i
                i++
                var kind = 'M'
                var lfoIndex = 0
                if (i < end && text[i] == 'M') {
                    kind = 'L'
                    i++
                } else if (i < end && text[i] in "WXD") {
                    kind = text[i]
                    i++
                }
                if (i < end && (text[i] == 'A' || text[i] == 'B')) {
                    lfoIndex = if (text[i] == 'B') 1 else 0
                    i++
                }
                val maximum = if (kind == 'M' || kind == 'D') 4 else 1
                val parsed = parseIntegerList(text, i, end, maximum)
                i = parsed.nextIndex
                val values = parsed.values
                val locationLine = source.lineAt(tokenStart)
                val locationColumn = source.columnAt(tokenStart)
                when (kind) {
                    'M' -> if (values.size == 4) {
                        output.add(MmlCommand.SoftwareLfoDefine(lfoIndex, values[0], values[1], values[2], values[3], locationLine, locationColumn))
                    } else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "M/MA/MB requires delay,speed,depthA,depthB"))
                    'W' -> if (values.size == 1) output.add(MmlCommand.SoftwareLfoWaveform(lfoIndex, values[0], locationLine, locationColumn))
                    else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "MW/MWA/MWB requires one waveform"))
                    'X' -> if (values.size == 1) output.add(MmlCommand.SoftwareLfoClockMode(lfoIndex, values[0], locationLine, locationColumn))
                    else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "MX/MXA/MXB requires one clock mode"))
                    'L' -> if (values.size == 1) output.add(MmlCommand.SoftwareLfoTlMask(lfoIndex, values[0], locationLine, locationColumn))
                    else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "MM/MMA/MMB requires one TL slot mask"))
                    'D' -> if (values.size in 1..3) output.add(
                        MmlCommand.SoftwareLfoDepthEvolution(
                            lfoIndex,
                            values[0],
                            if (values.size >= 2) values[1] else 0,
                            if (values.size >= 3) values[2] else 0,
                            locationLine,
                            locationColumn
                        )
                    ) else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "MD/MDA/MDB requires speed[,depth[,time]]"))
                }
            } else if (raw == '*') {
                val tokenStart = i
                i++
                var lfoIndex = 0
                if (i < end && (text[i] == 'A' || text[i] == 'B')) {
                    lfoIndex = if (text[i] == 'B') 1 else 0
                    i++
                }
                val parsed = parseIntegerList(text, i, end, 1)
                i = parsed.nextIndex
                if (parsed.values.size == 1) {
                    output.add(MmlCommand.SoftwareLfoSwitch(lfoIndex, parsed.values[0], source.lineAt(tokenStart), source.columnAt(tokenStart)))
                } else {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "*/ *A/ *B requires one switch value"))
                }
            } else if (raw == '#') {
                val tokenStart = i
                i++
                while (i < end && text[i].isWhitespace()) i++
                val switchValue = parseUnsignedInteger(text, i, end)
                i = switchValue.second
                var rate: Int? = null
                if (i < end && text[i] == ',') {
                    i++
                    while (i < end && text[i].isWhitespace()) i++
                    val parsedRate = parseUnsignedInteger(text, i, end)
                    rate = parsedRate.first
                    i = parsedRate.second
                }
                val selectedSwitch = switchValue.first
                if (selectedSwitch == null || selectedSwitch !in 0..1 || (selectedSwitch == 1 && rate == null)) {
                    diagnostics.add(
                        MmlDiagnostic(
                            source.lineAt(tokenStart), source.columnAt(tokenStart),
                            "OPNA hardware LFO requires #0[,rate] or #1,rate"
                        )
                    )
                } else {
                    output.add(
                        MmlCommand.HardwareLfoGlobal(
                            selectedSwitch == 1,
                            rate,
                            source.lineAt(tokenStart),
                            source.columnAt(tokenStart)
                        )
                    )
                }
            } else if (raw == 'H') {
                val tokenStart = i
                i++
                val pmsStart = i
                while (i < end && text[i].isDigit()) i++
                val pms = text.substring(pmsStart, i).toIntOrNull()
                var ams = 0
                var delayKind = MmlCommand.HARDWARE_LFO_DELAY_NONE
                var delayValue = 0
                var delayDotted = false
                var valid = pms != null
                if (i < end && text[i] == ',') {
                    i++
                    val amsStart = i
                    while (i < end && text[i].isDigit()) i++
                    val parsedAms = text.substring(amsStart, i).toIntOrNull()
                    if (parsedAms == null) valid = false else ams = parsedAms
                    if (i < end && text[i] == ',') {
                        i++
                        if (i < end && text[i].lowercaseChar() == 'l') {
                            delayKind = MmlCommand.HARDWARE_LFO_DELAY_NOTE_LENGTH
                            i++
                        } else {
                            delayKind = MmlCommand.HARDWARE_LFO_DELAY_RAW_CLOCKS
                        }
                        val delayStart = i
                        while (i < end && text[i].isDigit()) i++
                        val parsedDelay = text.substring(delayStart, i).toIntOrNull()
                        if (parsedDelay == null) valid = false else delayValue = parsedDelay
                        if (delayKind == MmlCommand.HARDWARE_LFO_DELAY_NOTE_LENGTH && i < end && text[i] == '.') {
                            delayDotted = true
                            i++
                        }
                    }
                }
                if (!valid) {
                    diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "H requires H<pms>[,<ams>][,<clocks>|,l<length>[.]]"))
                } else {
                    output.add(
                        MmlCommand.HardwareLfo(
                            pms!!, ams, delayKind, delayValue, delayDotted,
                            source.lineAt(tokenStart), source.columnAt(tokenStart)
                        )
                    )
                }
            } else if (raw == 'S') {
                val tokenStart = i
                i++
                val kindStart = i
                while (i < end && text[i].isLetter()) i++
                val kind = text.substring(kindStart, i).uppercase()
                val parsed = parseUnsignedInteger(text, i, end)
                i = parsed.second
                val value = parsed.first
                val locationLine = source.lineAt(tokenStart)
                val locationColumn = source.columnAt(tokenStart)
                when (kind) {
                    "T" -> if (value == 0 || value == 1) {
                        output.add(MmlCommand.SsgToneEnable(value == 1, locationLine, locationColumn))
                    } else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "ST requires ST0 or ST1"))
                    "N" -> if (value == 0 || value == 1) {
                        output.add(MmlCommand.SsgNoiseEnable(value == 1, locationLine, locationColumn))
                    } else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "SN requires SN0 or SN1"))
                    "NP" -> if (value != null) {
                        output.add(MmlCommand.SsgNoisePeriod(value, locationLine, locationColumn))
                    } else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "SNP requires a noise period 1..31"))
                    "EP" -> if (value != null) {
                        output.add(MmlCommand.SsgHardwareEnvelopePeriod(value, locationLine, locationColumn))
                    } else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "SEP requires an envelope period 1..65535"))
                    "ES" -> if (value != null) {
                        output.add(MmlCommand.SsgHardwareEnvelopeShape(value, locationLine, locationColumn))
                    } else diagnostics.add(MmlDiagnostic(locationLine, locationColumn, "SES requires a shape 0..15 and explicitly restarts the envelope"))
                    else -> diagnostics.add(
                        MmlDiagnostic(
                            locationLine,
                            locationColumn,
                            "SSG hardware commands are ST, SN, SNP, SEP, and SES"
                        )
                    )
                }
            } else if (raw == 'E') {
                val tokenStart = i
                i++
                if (i < end && text[i].lowercaseChar() == 'x') {
                    i++
                    val valueStart = i
                    while (i < end && text[i].isDigit()) i++
                    val value = text.substring(valueStart, i).toIntOrNull()
                    if (value == null || value !in 0..1) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "EX requires EX0 or EX1"))
                    } else {
                        output.add(MmlCommand.EnvelopeClockMode(value, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                    }
                } else {
                    val values = IntArray(6)
                    var count = 0
                    var valid = true
                    while (count < values.size && valid) {
                        var sign = 1
                        if (i < end && (text[i] == '+' || text[i] == '-')) {
                            if (text[i] == '-') sign = -1
                            i++
                        }
                        val valueStart = i
                        while (i < end && text[i].isDigit()) i++
                        val value = text.substring(valueStart, i).toIntOrNull()
                        if (value == null) {
                            valid = false
                        } else {
                            values[count] = value * sign
                            count++
                            if (i < end && text[i] == ',') i++ else break
                        }
                    }
                    if (!valid || count !in 4..6 || (i > tokenStart && text[i - 1] == ',')) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "E requires four legacy or five/six extended parameters"))
                    } else {
                        val format = if (count == 4) PmdPerformanceLaws.ENVELOPE_LEGACY else PmdPerformanceLaws.ENVELOPE_EXTENDED
                        output.add(
                            MmlCommand.SoftwareEnvelope(
                                format,
                                values[0],
                                values[1],
                                values[2],
                                values[3],
                                if (count >= 5) values[4] else 0,
                                if (count >= 6) values[5] else 0,
                                source.lineAt(tokenStart),
                                source.columnAt(tokenStart)
                            )
                        )
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
            } else if (c == 's' && channel != MmlChannelId.R) {
                val tokenStart = i
                i++
                if (i < end && text[i] == 'd') {
                    i++
                    var relative = false
                    if (i < end && text[i] == 'd') {
                        relative = true
                        i++
                    }
                    val mask = parseUnsignedInteger(text, i, end)
                    i = mask.second
                    if (i < end && text[i] == ',') i++ else {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "sd/sdd requires slotMask,value"))
                    }
                    var sign = 1
                    if (i < end && (text[i] == '+' || text[i] == '-')) {
                        if (text[i] == '-') sign = -1
                        i++
                    }
                    val value = parseUnsignedInteger(text, i, end)
                    i = value.second
                    if (mask.first == null || value.first == null) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "sd/sdd requires slotMask,value"))
                    } else {
                        output.add(MmlCommand.FmSlotDetune(mask.first!!, value.first!! * sign, relative, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                    }
                } else if (i < end && text[i] == 'k') {
                    i++
                    val mask = parseUnsignedInteger(text, i, end)
                    i = mask.second
                    var delay = 0
                    var denominator: Int? = null
                    var dotted = false
                    if (i < end && text[i] == ',') {
                        i++
                        if (i < end && text[i].lowercaseChar() == 'l') {
                            i++
                            val length = parseUnsignedInteger(text, i, end)
                            denominator = length.first
                            i = length.second
                            if (i < end && text[i] == '.') {
                                dotted = true
                                i++
                            }
                        } else {
                            val clocks = parseUnsignedInteger(text, i, end)
                            delay = clocks.first ?: -1
                            i = clocks.second
                        }
                    }
                    if (mask.first == null || (mask.first != 0 && delay < 0 && denominator == null)) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "sk requires slotMask[,clocks|,lLength]"))
                    } else {
                        output.add(MmlCommand.FmSlotKeyOnDelay(mask.first!!, delay.coerceAtLeast(0), denominator, dotted, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                    }
                } else {
                    val mask = parseUnsignedInteger(text, i, end)
                    i = mask.second
                    if (mask.first == null) {
                        diagnostics.add(MmlDiagnostic(source.lineAt(tokenStart), source.columnAt(tokenStart), "s requires a slot mask 0..15"))
                    } else {
                        output.add(MmlCommand.FmSlotMask(mask.first!!, source.lineAt(tokenStart), source.columnAt(tokenStart)))
                    }
                }
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

    private data class ParsedIntegerList(val values: List<Int>, val nextIndex: Int)

    private fun parseUnsignedInteger(text: String, start: Int, end: Int): Pair<Int?, Int> {
        var index = start
        var radix = 10
        if (index < end && text[index] == '$') {
            radix = 16
            index++
        }
        val valueStart = index
        while (index < end && if (radix == 16) text[index].isDigit() || text[index].lowercaseChar() in 'a'..'f' else text[index].isDigit()) index++
        return Pair(if (valueStart == index) null else text.substring(valueStart, index).toIntOrNull(radix), index)
    }

    private fun rhythmVoiceFor(value: Char): Int = when (value.lowercaseChar()) {
        'b' -> 0
        's' -> 1
        'c' -> 2
        'h' -> 3
        't' -> 4
        'i' -> 5
        else -> -1
    }

    private fun parseIntegerList(text: String, start: Int, end: Int, maximum: Int): ParsedIntegerList {
        val values = ArrayList<Int>(maximum)
        var index = start
        while (index < end && values.size < maximum) {
            var sign = 1
            if (text[index] == '+' || text[index] == '-') {
                if (text[index] == '-') sign = -1
                index++
            }
            val valueStart = index
            while (index < end && text[index].isDigit()) index++
            if (valueStart == index) break
            values.add(text.substring(valueStart, index).toInt() * sign)
            if (index >= end || text[index] != ',') break
            index++
        }
        return ParsedIntegerList(values, index)
    }

    private fun assignSourceOrder(tracks: List<MmlTrack>, rhythmPatterns: List<MmlRhythmPattern>) {
        val commands = ArrayList<MmlCommand>()
        var trackIndex = 0
        while (trackIndex < tracks.size) {
            commands.addAll(tracks[trackIndex].commands)
            trackIndex++
        }
        var patternIndex = 0
        while (patternIndex < rhythmPatterns.size) {
            commands.addAll(rhythmPatterns[patternIndex].commands)
            patternIndex++
        }
        commands.sortWith(
            compareBy<MmlCommand> { it.line }
                .thenBy { it.expansionOccurrence }
                .thenBy { it.column }
        )
        var sourceOrder = 0
        var commandIndex = 0
        while (commandIndex < commands.size) {
            val command = commands[commandIndex]
            if (command.sourceOrder < 0) {
                command.sourceOrder = sourceOrder
                sourceOrder++
            }
            commandIndex++
        }
    }

    private fun assignExpansionOccurrences(commands: List<MmlCommand>) {
        var occurrence = 0
        while (occurrence < commands.size) {
            commands[occurrence].expansionOccurrence = occurrence
            occurrence++
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

    private fun parseBpmMilli(value: String): Int? {
        if (value.isEmpty() || value[0] == '-') return null
        val dot = value.indexOf('.')
        if (dot >= 0 && value.indexOf('.', dot + 1) >= 0) return null
        val wholeText = if (dot >= 0) value.substring(0, dot) else value
        val fractionText = if (dot >= 0) value.substring(dot + 1) else ""
        if (wholeText.isEmpty() || !wholeText.all { it.isDigit() }) return null
        if (fractionText.length > 3 || !fractionText.all { it.isDigit() }) return null
        val whole = wholeText.toLongOrNull() ?: return null
        var fraction = if (fractionText.isEmpty()) 0 else fractionText.toInt()
        if (fractionText.length == 1) fraction *= 100
        else if (fractionText.length == 2) fraction *= 10
        val scaled = whole * PmdPerformanceLaws.BPM_MILLI_SCALE + fraction
        return if (scaled in 1..Int.MAX_VALUE.toLong()) scaled.toInt() else null
    }

    private fun expandMacros(
        source: String,
        columnOffset: Int,
        names: List<String>,
        bodies: List<String>,
        line: Int,
        diagnostics: MutableList<MmlDiagnostic>
    ): ExpandedLine {
        var current = source
        var currentColumns = IntArray(source.length) { columnOffset + it + 1 }
        var depth = 0
        while (current.indexOf('$') >= 0 && depth < MAX_MACRO_DEPTH) {
            val output = StringBuilder()
            val outputColumns = mutableListOf<Int>()
            var i = 0
            var changed = false
            while (i < current.length) {
                if (current[i] != '$') {
                    output.append(current[i])
                    outputColumns.add(currentColumns[i])
                    i++
                } else {
                    val tokenStart = i
                    i++
                    val nameStart = i
                    while (i < current.length && (current[i].isLetterOrDigit() || current[i] == '_')) i++
                    val name = current.substring(nameStart, i).lowercase()
                    val macroIndex = names.indexOf(name)
                    if (name.isEmpty() || macroIndex < 0) {
                        diagnostics.add(MmlDiagnostic(line, currentColumns[tokenStart], "Unknown MML macro '$name'"))
                        return ExpandedLine(current, currentColumns)
                    }
                    val body = bodies[macroIndex]
                    output.append(body)
                    var bodyIndex = 0
                    while (bodyIndex < body.length) {
                        outputColumns.add(currentColumns[tokenStart])
                        bodyIndex++
                    }
                    changed = true
                }
                if (output.length > MAX_MACRO_CHARACTERS) {
                    diagnostics.add(MmlDiagnostic(line, currentColumns[0], "Expanded macros exceed parser capacity"))
                    return ExpandedLine(current, currentColumns)
                }
            }
            current = output.toString()
            currentColumns = IntArray(outputColumns.size)
            var columnIndex = 0
            while (columnIndex < outputColumns.size) {
                currentColumns[columnIndex] = outputColumns[columnIndex]
                columnIndex++
            }
            if (!changed) break
            depth++
        }
        if (current.indexOf('$') >= 0) {
            val unresolved = current.indexOf('$')
            diagnostics.add(MmlDiagnostic(line, currentColumns[unresolved], "Macro expansion exceeds depth $MAX_MACRO_DEPTH"))
        }
        return ExpandedLine(current, currentColumns)
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
            "K" -> MmlChannelId.K
            "R" -> MmlChannelId.R
            else -> null
        }
    }
}
