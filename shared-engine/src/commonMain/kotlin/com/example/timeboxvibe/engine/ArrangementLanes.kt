package com.example.timeboxvibe.engine

import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong

enum class ArrangementRouting {
    MML_LOGICAL_TRACKS
}

enum class EqType {
    PEAK
}

data class SongEqBand(
    val type: EqType,
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float
)

/** Metadata wrapper for one exact-size authored MML event program. */
data class ArrangementLanes(
    val tempoBpm: Float,
    val keyRootMidi: Int,
    val routing: ArrangementRouting = ArrangementRouting.MML_LOGICAL_TRACKS,
    val beatsPerBar: Int = 4,
    val eqBands: List<SongEqBand> = emptyList(),
    val compiledOpnaSong: CompiledOpnaSong
)
