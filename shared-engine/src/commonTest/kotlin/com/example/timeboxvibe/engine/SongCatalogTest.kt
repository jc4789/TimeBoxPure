package com.example.timeboxvibe.engine

import com.example.timeboxvibe.engine.audio.mml.MmlCompileResult
import com.example.timeboxvibe.engine.audio.mml.MmlSongBank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongCatalogTest {
    @Test
    fun catalogMetadataIsCompleteAndIdsAreUnique() {
        val ids = mutableSetOf<String>()
        var i = 0
        while (i < SongCatalog.all.size) {
            val song = SongCatalog.all[i]
            assertTrue(song.id.isNotBlank())
            assertTrue(song.displayTitle.isNotBlank())
            assertTrue(song.previewDurationMs > 0L)
            assertTrue(ids.add(song.id), "Duplicate song id: ${song.id}")
            assertSame(song, SongCatalog.byId(song.id))
            assertEquals(i, SongCatalog.indexOf(song.id))
            i++
        }
        assertNull(SongCatalog.byId("missing-song"))
    }

    @Test
    fun everyCatalogEntryBuildsItsDeclaredPlaybackKind() {
        var i = 0
        while (i < SongCatalog.all.size) {
            val song = SongCatalog.all[i]
            val playback = assertNotNull(song.buildPlayback(1f), "Factory failed for ${song.id}")
            assertEquals(SongKind.MML, song.kind)
            assertIs<SongPlayback.Arrangement>(playback)
            i++
        }
    }

    @Test
    fun catalogIsMmlOnlyAndRetiredIdsFallBack() {
        assertEquals(1, SongCatalog.all.size)
        assertEquals(MmlSongBank.SENBONZAKURA_DEMO_KEY, SongCatalog.DEFAULT_FOCUS_ID)
        assertEquals(MmlSongBank.SENBONZAKURA_DEMO_KEY, SongCatalog.DEFAULT_RELAX_ID)
        assertNull(SongCatalog.byId("oriental"))
        assertNull(SongCatalog.byId("synth-chime"))
        assertNull(SongCatalog.byId("synth-victory"))
        assertNull(SongCatalog.byId("synth-bad-apple"))
        assertNull(SongCatalog.byId("synth-senbonzakura"))
        assertNull(SongCatalog.byId("synth-bad-apple-LotusLandStory"))
    }

    @Test
    fun mmlFactoryUsesTheBankCachedCompilation() {
        val compiled = assertIs<MmlCompileResult.Success>(MmlSongBank.senbonzakuraDemoResult)
        val song = assertNotNull(SongCatalog.byId(MmlSongBank.SENBONZAKURA_DEMO_KEY))
        assertEquals(SongKind.MML, song.kind)
        val playback = assertIs<SongPlayback.Arrangement>(song.buildPlayback(1f))
        assertSame(compiled.arrangement, playback.lanes)
    }
}
