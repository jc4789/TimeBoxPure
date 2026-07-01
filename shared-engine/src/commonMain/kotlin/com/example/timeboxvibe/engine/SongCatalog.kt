package com.example.timeboxvibe.engine

import com.example.timeboxvibe.engine.audio.mml.MmlSongBank

enum class SongKind {
    PROCEDURAL,
    MML,
    PLATFORM_ASSET
}

enum class PlatformSongAsset {
    ORIENTAL_ALARM
}

sealed class SongPlayback {
    class Arrangement(val lanes: ArrangementLanes) : SongPlayback()
    class PlatformAsset(val asset: PlatformSongAsset) : SongPlayback()
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
    const val SYNTH_CHIME_ID = "synth-chime"
    const val SYNTH_VICTORY_ID = "synth-victory"
    const val ORIENTAL_ID = "oriental"
    const val SYNTH_BAD_APPLE_ID = "synth-bad-apple"
    const val SYNTH_SENBONZAKURA_ID = "synth-senbonzakura"
    const val BAD_APPLE_LLS_ID = "synth-bad-apple-LotusLandStory"

    const val DEFAULT_FOCUS_ID = SYNTH_CHIME_ID
    const val DEFAULT_RELAX_ID = ORIENTAL_ID

    private const val DEFAULT_PREVIEW_MS = 7000L
    private const val ORIENTAL_PREVIEW_MS = 3500L
    private const val LONG_PREVIEW_MS = 25000L

    val all: Array<SongDefinition> = arrayOf(
        SongDefinition(SYNTH_CHIME_ID, "ZEN CHIME", SongKind.PROCEDURAL, DEFAULT_PREVIEW_MS) { volume ->
            SongPlayback.Arrangement(SoundMelodies.buildChimeArrangement(volume))
        },
        SongDefinition(SYNTH_VICTORY_ID, "VICTORY", SongKind.PROCEDURAL, DEFAULT_PREVIEW_MS) { volume ->
            SongPlayback.Arrangement(SoundMelodies.buildVictoryArrangement(volume))
        },
        SongDefinition(ORIENTAL_ID, "ORIENTAL", SongKind.PLATFORM_ASSET, ORIENTAL_PREVIEW_MS) {
            SongPlayback.PlatformAsset(PlatformSongAsset.ORIENTAL_ALARM)
        },
        SongDefinition(SYNTH_BAD_APPLE_ID, "BAD APPLE", SongKind.PROCEDURAL, DEFAULT_PREVIEW_MS) { volume ->
            SongPlayback.Arrangement(SoundMelodies.buildBadAppleArrangementLanes(volume))
        },
        SongDefinition(SYNTH_SENBONZAKURA_ID, "SENBONZAKURA", SongKind.PROCEDURAL, DEFAULT_PREVIEW_MS) { volume ->
            SongPlayback.Arrangement(SoundMelodies.buildSenbonzakuraArrangementLanes(volume))
        },
        SongDefinition(BAD_APPLE_LLS_ID, "Bad Apple!!(東方幻想郷)", SongKind.PROCEDURAL, LONG_PREVIEW_MS) { volume ->
            SongPlayback.Arrangement(SoundMelodies.buildBadAppleLotusLandStory(volume))
        },
        SongDefinition(MmlSongBank.SENBONZAKURA_DEMO_KEY, "SENBON MML DEMO", SongKind.MML, DEFAULT_PREVIEW_MS) { volume ->
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
