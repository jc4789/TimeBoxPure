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
    private val playbackFactory: () -> SongPlayback?
) {
    fun buildPlayback(): SongPlayback? = playbackFactory()
}

object SongCatalog {
    const val DEFAULT_FOCUS_ID = MmlSongBank.BAD_APPLE_LLS_KEY
    const val DEFAULT_RELAX_ID = MmlSongBank.BAD_APPLE_LLS_KEY

    private const val DEFAULT_PREVIEW_MS = 7000L

    val all: Array<SongDefinition> = arrayOf(
        SongDefinition(MmlSongBank.BAD_APPLE_LLS_KEY, "BAD APPLE!! / LOTUS LAND STORY", SongKind.MML, DEFAULT_PREVIEW_MS) {
            val arrangement = MmlSongBank.getArrangement(MmlSongBank.BAD_APPLE_LLS_KEY)
            if (arrangement == null) null else SongPlayback.Arrangement(arrangement)
        }
    )

    fun byId(id: String): SongDefinition? {
        val canonicalId = canonicalId(id)
        var i = 0
        while (i < all.size) {
            val song = all[i]
            if (song.id == canonicalId) return song
            i++
        }
        return null
    }

    fun indexOf(id: String): Int {
        val canonicalId = canonicalId(id)
        var i = 0
        while (i < all.size) {
            if (all[i].id == canonicalId) return i
            i++
        }
        return 0
    }

    private fun canonicalId(id: String): String = when (id) {
        MmlSongBank.SENBONZAKURA_DEMO_KEY -> MmlSongBank.BAD_APPLE_LLS_KEY
        else -> id
    }
}
