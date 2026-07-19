package com.example.timeboxvibe.engine

import com.example.timeboxvibe.engine.audio.mml.MmlSongBank

enum class SongKind {
    MML
}

sealed class SongPlayback {
    class Arrangement(val lanes: ArrangementLanes) : SongPlayback()
}

class SongDefinition(
    val id: String,
    val displayTitle: String,
    val kind: SongKind,
    val previewDurationMs: Long,
    private val playbackFactory: (Float) -> SongPlayback?
) {
    fun buildPlayback(volume: Float): SongPlayback? = playbackFactory(volume)
}

object SongCatalog {
    const val DEFAULT_FOCUS_ID = MmlSongBank.SENBONZAKURA_DEMO_KEY
    const val DEFAULT_RELAX_ID = MmlSongBank.SENBONZAKURA_DEMO_KEY

    private const val DEFAULT_PREVIEW_MS = 7000L

    val all: Array<SongDefinition> = arrayOf(
        SongDefinition(MmlSongBank.SENBONZAKURA_DEMO_KEY, "BAD APPLE!! / LOTUS LAND STORY", SongKind.MML, DEFAULT_PREVIEW_MS) { volume ->
            val arrangement = MmlSongBank.getArrangement(MmlSongBank.SENBONZAKURA_DEMO_KEY, volume)
            if (arrangement == null) null else SongPlayback.Arrangement(arrangement)
        }
    )

    fun byId(id: String): SongDefinition? {
        var i = 0
        while (i < all.size) {
            val song = all[i]
            if (song.id == id) return song
            i++
        }
        return null
    }

    fun indexOf(id: String): Int {
        var i = 0
        while (i < all.size) {
            if (all[i].id == id) return i
            i++
        }
        return 0
    }
}
