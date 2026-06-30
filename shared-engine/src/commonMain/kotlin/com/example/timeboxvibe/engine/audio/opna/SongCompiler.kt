package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.ToneSpec
import kotlin.math.pow

enum class NoteLength {
    WHOLE, HALF, QUARTER, EIGHTH, SIXTEENTH
}

data class SongSpec(
    val bpm: Float,
    val beatsPerBar: Int = 4,
    val ticksPerBeat: Int = 480
) {
    val msPerBeat: Float = 60000f / bpm
    val msPerBar: Float = msPerBeat * beatsPerBar
    val msPerTick: Float = msPerBeat / ticksPerBeat

    fun barToMs(bar: Int): Int = (bar * msPerBar).toInt()
    fun beatToMs(beat: Int): Int = (beat * msPerBeat).toInt()
    fun tickToMs(tick: Int): Int = (tick * msPerTick).toInt()

    fun noteLengthToMs(length: NoteLength): Int = when (length) {
        NoteLength.WHOLE -> (msPerBeat * beatsPerBar).toInt()
        NoteLength.HALF -> (msPerBeat * 2).toInt()
        NoteLength.QUARTER -> msPerBeat.toInt()
        NoteLength.EIGHTH -> (msPerBeat / 2).toInt()
        NoteLength.SIXTEENTH -> (msPerBeat / 4).toInt()
    }

    fun positionToMs(bar: Int, beat: Int, tick: Int = 0): Int =
        barToMs(bar) + beatToMs(beat) + tickToMs(tick)
}

data class NoteSpec(
    val bar: Int,
    val beat: Int,
    val tick: Int = 0,
    val midi: Int,
    val length: NoteLength,
    val gate: Float = 0.86f,
    val velocity: Float = 1f,
    val type: String = "pulse25",
    val attackMs: Int = 10,
    val decayMs: Int = 50,
    val sustainLevel: Float = 0.7f,
    val releaseMs: Int = 80
)

fun compileNotes(spec: SongSpec, notes: List<NoteSpec>): List<ToneSpec> {
    val result = mutableListOf<ToneSpec>()
    var i = 0
    while (i < notes.size) {
        val n = notes[i]
        val startMs = spec.positionToMs(n.bar, n.beat, n.tick)
        val durationMs = spec.noteLengthToMs(n.length)
        val gateDurationMs = (durationMs * n.gate).toInt()
        val freq = if (n.midi <= 0) 0f else midiNoteToFreq(n.midi)
        result.add(
            ToneSpec(
                freq = freq,
                startMs = startMs,
                durationMs = gateDurationMs,
                volume = n.velocity,
                type = n.type,
                useADSR = true,
                attackMs = n.attackMs,
                decayMs = n.decayMs,
                sustainLevel = n.sustainLevel,
                releaseMs = n.releaseMs
            )
        )
        i++
    }
    return result
}

fun midiNoteToFreq(midi: Int): Float {
    return 440f * 2.0.pow((midi - 69) / 12.0).toFloat()
}

fun compileLlsNotes(spec: SongSpec, notes: List<NoteSpec>, repeatCount: Int = 2): List<ToneSpec> {
    val result = mutableListOf<ToneSpec>()
    val originalDurationBeats = 40 * 4 
    val durationMs = (originalDurationBeats * spec.msPerBeat).toInt()

    var r = 0
    while (r < repeatCount) {
        val timeShiftMs = r * durationMs
        var i = 0
        while (i < notes.size) {
            val n = notes[i]
            val originalStartBeats = n.bar * 4.0 + n.beat + (n.tick / 480.0)
            val startMs = timeShiftMs + (originalStartBeats * spec.msPerBeat).toInt()
            
            val durationMs = spec.noteLengthToMs(n.length)
            val gateDurationMs = (durationMs * n.gate).toInt()
            
            val freq = if (n.midi <= 0) 0f else midiNoteToFreq(n.midi)
            result.add(
                ToneSpec(
                    freq = freq,
                    startMs = startMs,
                    durationMs = gateDurationMs,
                    volume = n.velocity,
                    type = n.type,
                    useADSR = true,
                    attackMs = n.attackMs,
                    decayMs = n.decayMs,
                    sustainLevel = n.sustainLevel,
                    releaseMs = n.releaseMs
                )
            )
            i++
        }
        r++
    }
    return result
}
