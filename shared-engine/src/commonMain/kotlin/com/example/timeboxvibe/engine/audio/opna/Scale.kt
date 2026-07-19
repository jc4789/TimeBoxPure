package com.example.timeboxvibe.engine.audio.opna

internal interface Scale {
    fun degreeToMidi(degree: Int, octave: Int): Int
}

internal abstract class AbstractScale(val rootMidi: Int, val intervals: IntArray) : Scale {
    override fun degreeToMidi(degree: Int, octave: Int): Int {
        val size = intervals.size
        val deg = ((degree % size) + size) % size
        val octShift = if (degree < 0) {
            (degree - size + 1) / size
        } else {
            degree / size
        }
        return rootMidi + (octave + octShift) * 12 + intervals[deg]
    }
}

internal class PhrygianDominantScale(rootMidi: Int) : AbstractScale(rootMidi, intArrayOf(0, 1, 4, 5, 7, 8, 10))

internal class PentatonicMinorScale(rootMidi: Int) : AbstractScale(rootMidi, intArrayOf(0, 3, 5, 7, 10))

internal class DorianScale(rootMidi: Int) : AbstractScale(rootMidi, intArrayOf(0, 2, 3, 5, 7, 9, 10))

internal class MinorScale(rootMidi: Int) : AbstractScale(rootMidi, intArrayOf(0, 2, 3, 5, 7, 8, 10))
