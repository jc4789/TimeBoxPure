package com.example.timeboxvibe.engine

import com.example.timeboxvibe.engine.audio.opna.NoteLength
import com.example.timeboxvibe.engine.audio.opna.NoteSpec
import com.example.timeboxvibe.engine.audio.opna.SongSpec
import com.example.timeboxvibe.engine.audio.opna.compileNotes
import com.example.timeboxvibe.engine.audio.opna.compileLlsNotes
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong

enum class LaneMode {
    MONO_RETRIGGER,
    POLY_ROUND_ROBIN,
    CHORD_ALLOCATED,
    SSG_MONO,
    DRUM
}

enum class TimbreRef {
    FM_LEAD_ZUN1,
    FM_BASS_ZUN1,
    FM_BELL_ZUN1,
    FM_PAD_ZUN1,
    FM_LLS_AT54,
    FM_LLS_AT74,
    FM_LLS_AT99,
    FM_LLS_AT181,
    SSG_HARMONY_SQUARE,
    SSG_BASS_SQUARE,
    SSG_PAD_SQUARE,
    SSG_ARP_PULSE,
    DRUM_KICK,
    DRUM_SNARE,
    DRUM_HAT
}

data class Lane(val notes: List<ToneSpec>, val timbre: TimbreRef, val mode: LaneMode = LaneMode.MONO_RETRIGGER)

enum class ArrangementRouting {
    LEGACY,
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

data class ArrangementLanes(
    val lead: Lane,
    val harmony: Lane,
    val bass: Lane,
    val percussion: Lane,
    val tempoBpm: Float,
    val keyRootMidi: Int,
    val auxiliary: Lane? = null,
    val routing: ArrangementRouting = ArrangementRouting.LEGACY,
    val beatsPerBar: Int = 4,
    val eqBands: List<SongEqBand> = emptyList(),
    val additional: Lane? = null,
    val compiledOpnaSong: CompiledOpnaSong? = null
)

object SoundMelodies {
    private const val LEGACY_SYNTH_CHIME_ID = "synth-chime"
    private const val LEGACY_SYNTH_VICTORY_ID = "synth-victory"
    private const val LEGACY_SYNTH_BAD_APPLE_ID = "synth-bad-apple"
    private const val LEGACY_SYNTH_SENBONZAKURA_ID = "synth-senbonzakura"

    fun getMelody(key: String, volume: Float, isBass: Boolean): List<ToneSpec> {
        return when (key) {
            LEGACY_SYNTH_CHIME_ID -> {
                val e = 333
                val q = 667
                if (isBass) {
                    listOf(
                        ToneSpec(116f, 0, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30),
                        ToneSpec(116f, q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30),
                        ToneSpec(174f, 2*q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30)
                    )
                } else {
                    listOf(
                        ToneSpec(466f, 0, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(554f, e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(698f, 2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(554f, 2*e+q, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(466f, 2*e+q+e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(349f, 2*e+q+2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50)
                    )
                }
            }
            LEGACY_SYNTH_VICTORY_ID -> {
                val e = 250
                val q = 500
                val h = 1000
                if (isBass) {
                    listOf(
                        ToneSpec(130f, 0, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),
                        ToneSpec(130f, q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),
                        ToneSpec(196f, 2*q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),
                        ToneSpec(261f, 2*q+q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30)
                    )
                } else {
                    listOf(
                        ToneSpec(523f, 0, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),
                        ToneSpec(659f, e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),
                        ToneSpec(784f, 2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),
                        ToneSpec(1046f, 2*e+q, h, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 100)
                    )
                }
            }
            LEGACY_SYNTH_BAD_APPLE_ID -> {
                if (isBass) emptyList() else getBadAppleArrangement(volume)
            }
            LEGACY_SYNTH_SENBONZAKURA_ID -> {
                if (isBass) emptyList() else getSenbonzakuraArrangement(volume)
            }
            else -> emptyList()
        }
    }

    fun getArrangement(key: String, volume: Float = 1f): ArrangementLanes? {
        val playback = SongCatalog.byId(key)?.buildPlayback(volume)
        return if (playback is SongPlayback.Arrangement) playback.lanes else null
    }

    internal fun buildChimeArrangement(volume: Float): ArrangementLanes {
                val e = 120
                val q = 240
                val h = 480
                return ArrangementLanes(
                    lead = Lane(listOf(
                        ToneSpec(523.25f, 0, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 80), // C5
                        ToneSpec(659.25f, e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 80), // E5
                        ToneSpec(783.99f, 2*e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 80), // G5
                        ToneSpec(987.77f, 3*e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 80), // B5
                        ToneSpec(1174.66f, 4*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 150), // D6
                        ToneSpec(987.77f, 4*e+q, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 80),
                        ToneSpec(783.99f, 4*e+q+e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 80),
                        ToneSpec(659.25f, 4*e+q+2*e, h, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 150)
                    ), TimbreRef.FM_BELL_ZUN1),
                    harmony = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE),
                    bass = Lane(listOf(
                        ToneSpec(130.81f, 0, 4*e+q+2*e+h, 0.6f * volume, "pulse12", true, 10, 40, 0.6f, 100) // C3 bass sustaining
                    ), TimbreRef.FM_BASS_ZUN1),
                    percussion = Lane(emptyList(), TimbreRef.DRUM_HAT),
                    tempoBpm = 120f,
                    keyRootMidi = 60
                )
    }

    internal fun buildVictoryArrangement(volume: Float): ArrangementLanes {
                val e = 150
                val q = 300
                val h = 600
                return ArrangementLanes(
                    lead = Lane(listOf(
                        ToneSpec(523.25f, 0, e, 0.8f * volume, "pulse25", true, 5, 30, 0.6f, 80), // C5
                        ToneSpec(523.25f, e, e, 0.8f * volume, "pulse25", true, 5, 30, 0.6f, 80), // C5
                        ToneSpec(523.25f, 2*e, e, 0.8f * volume, "pulse25", true, 5, 30, 0.6f, 80), // C5
                        ToneSpec(523.25f, 3*e, q, 0.8f * volume, "pulse25", true, 5, 30, 0.6f, 100), // C5
                        
                        ToneSpec(415.30f, 3*e+q, q, 0.8f * volume, "pulse25", true, 5, 30, 0.6f, 100), // Ab4
                        ToneSpec(466.16f, 3*e+2*q, q, 0.8f * volume, "pulse25", true, 5, 30, 0.6f, 100), // Bb4
                        
                        ToneSpec(523.25f, 3*e+3*q, h, 0.8f * volume, "pulse25", true, 5, 30, 0.6f, 150) // C5
                    ), TimbreRef.FM_LEAD_ZUN1),
                    harmony = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE),
                    bass = Lane(listOf(
                        ToneSpec(130.81f, 0, e, 0.6f * volume, "pulse12", true, 5, 20, 0.5f, 50), // C3
                        ToneSpec(130.81f, e, e, 0.6f * volume, "pulse12", true, 5, 20, 0.5f, 50),
                        ToneSpec(130.81f, 2*e, e, 0.6f * volume, "pulse12", true, 5, 20, 0.5f, 50),
                        ToneSpec(130.81f, 3*e, q, 0.6f * volume, "pulse12", true, 5, 20, 0.5f, 50),
                        
                        ToneSpec(103.83f, 3*e+q, q, 0.6f * volume, "pulse12", true, 5, 20, 0.5f, 50), // Ab2
                        ToneSpec(116.54f, 3*e+2*q, q, 0.6f * volume, "pulse12", true, 5, 20, 0.5f, 50), // Bb2
                        
                        ToneSpec(130.81f, 3*e+3*q, h, 0.6f * volume, "pulse12", true, 5, 20, 0.5f, 80) // C3
                    ), TimbreRef.FM_BASS_ZUN1),
                    percussion = Lane(listOf(
                        ToneSpec(-1f, 0, e, 0.5f * volume, "kick", false),
                        ToneSpec(-1f, 2*e, e, 0.5f * volume, "kick", false),
                        ToneSpec(3000f, 3*e, q, 0.4f * volume, "snare", false),
                        ToneSpec(-1f, 3*e+q, q, 0.5f * volume, "kick", false),
                        ToneSpec(3000f, 3*e+2*q, q, 0.4f * volume, "snare", false)
                    ), TimbreRef.DRUM_HAT),
                    tempoBpm = 120f,
                    keyRootMidi = 60
                )
    }

    internal fun buildBadAppleArrangementLanes(volume: Float): ArrangementLanes {
                val e = 217
                val q = 434
                val s = 109
                val kickBeats = badAppleKickBeats(16)
                val percussion = Lane(
                    notes = buildKickLane(kickBeats, e, 0.5f * volume) +
                             buildBadApplePercussion(volume, e).notes,
                    timbre = TimbreRef.DRUM_HAT
                )
                return ArrangementLanes(
                    lead = buildBadAppleLead(volume, e, q, s),
                    harmony = buildBadAppleHarmony(volume, e, q),
                    bass = buildBadAppleBass(volume, e),
                    percussion = percussion,
                    tempoBpm = 138f,
                    keyRootMidi = 63
                )
    }

    internal fun buildSenbonzakuraArrangementLanes(volume: Float): ArrangementLanes {
                val e = 195
                val q = 390
                val s = 97
                val kickBeats = senbonzakuraKickBeats(16)
                val percussion = Lane(
                    notes = buildKickLane(kickBeats, e, 0.5f * volume) +
                             buildSenbonzakuraPercussion(volume, e).notes,
                    timbre = TimbreRef.DRUM_HAT
                )
                return ArrangementLanes(
                    lead = buildSenbonzakuraLead(volume, e, q, s),
                    harmony = buildSenbonzakuraHarmony(volume, e, q),
                    bass = buildSenbonzakuraBass(volume, e),
                    percussion = percussion,
                    tempoBpm = 154f,
                    keyRootMidi = 62
                )
    }

    private fun getBadAppleArrangement(vol: Float): List<ToneSpec> {
        val e = 217
        val q = 434
        val s = 109
        return buildBadAppleLead(vol, e, q, s).notes +
                buildBadAppleHarmony(vol, e, q).notes +
                buildBadAppleBass(vol, e).notes +
                buildBadApplePercussion(vol, e).notes
    }

    private fun getSenbonzakuraArrangement(vol: Float): List<ToneSpec> {
        val e = 195
        val q = 390
        val s = 97
        return buildSenbonzakuraLead(vol, e, q, s).notes +
                buildSenbonzakuraHarmony(vol, e, q).notes +
                buildSenbonzakuraBass(vol, e).notes +
                buildSenbonzakuraPercussion(vol, e).notes
    }

    private const val D2  = 73.42f
    private const val Db2 = 69.30f
    private const val Eb2 = 77.78f
    private const val F2  = 87.31f
    private const val Gb2 = 92.50f
    private const val Ab2 = 103.83f
    private const val Bb2 = 116.54f
    private const val B2  = 123.47f

    private const val C3  = 130.81f
    private const val Db3 = 138.59f
    private const val D3  = 146.83f
    private const val Eb3 = 155.56f
    private const val F3  = 174.61f
    private const val Gb3 = 185.00f
    private const val Ab3 = 207.65f
    private const val A3  = 220.00f
    private const val Bb3 = 233.08f
    private const val B3  = 246.94f

    private const val C4  = 261.63f
    private const val Db4 = 277.18f
    private const val D4  = 293.66f
    private const val Eb4 = 311.13f
    private const val E4  = 329.63f
    private const val F4  = 349.23f
    private const val Gb4 = 369.99f
    private const val G4  = 392.00f
    private const val Ab4 = 415.30f
    private const val A4  = 440.00f
    private const val Bb4 = 466.16f
    private const val B4  = 493.88f

    private const val C5  = 523.25f
    private const val Db5 = 554.37f
    private const val D5  = 587.33f
    private const val Eb5 = 622.25f
    private const val E5  = 659.25f
    private const val F5  = 698.46f
    private const val G5  = 783.99f
    private const val A5  = 880.00f
    private const val B5  = 987.77f
    private const val G2  = 98.00f
    private const val G3  = 196.00f

    private fun buildChannel(
        notes: List<Pair<Float, Int>>,
        volume: Float,
        type: String,
        useADSR: Boolean = true,
        attackMs: Int = 10,
        decayMs: Int = 50,
        sustainLevel: Float = 0.7f,
        releaseMs: Int = 80
    ): List<ToneSpec> {
        var t = 0
        return notes.map { (freq, dur) ->
            val noteVol = if (freq <= 0f) 0f else volume
            val spec = ToneSpec(freq, t, dur, noteVol, type, useADSR, attackMs, decayMs, sustainLevel, releaseMs)
            t += dur
            spec
        }
    }

    private fun buildKickLane(
        kickBeats: List<Int>,
        e: Int,
        volume: Float
    ): List<ToneSpec> {
        return kickBeats.map { beat ->
            val startMs = beat * e
            ToneSpec(-1f, startMs, e, volume, "kick", false)
        }
    }

    private fun badAppleKickBeats(bars: Int): List<Int> {
        val result = mutableListOf<Int>()
        var bar = 0
        while (bar < bars) {
            val base = bar * 8
            result.add(base + 0)
            result.add(base + 4)
            if (bar % 4 == 3) result.add(base + 7)
            bar++
        }
        return result
    }

    private fun senbonzakuraKickBeats(bars: Int): List<Int> {
        val result = mutableListOf<Int>()
        var bar = 0
        while (bar < bars) {
            val base = bar * 8
            result.add(base + 0)
            result.add(base + 4)
            bar++
        }
        return result
    }

    private fun repeatBar(bar: List<Pair<Float, Int>>, repeats: Int): List<Pair<Float, Int>> {
        val result = mutableListOf<Pair<Float, Int>>()
        repeat(repeats) { result.addAll(bar) }
        return result
    }

    private fun getBadAppleLeadNotes(e: Int): List<Pair<Float, Int>> {
        return listOf(
            Bb4 to e, Bb4 to e, B4 to e, Bb4 to e,
            Ab4 to e, Gb4 to e, F4 to e, Eb4 to e,
            Eb4 to e, F4 to e, Gb4 to e, Ab4 to e,
            Bb4 to e, Ab4 to e, Gb4 to e, F4 to e,
            Gb4 to e, Gb4 to e, Ab4 to e, Gb4 to e,
            F4 to e, Eb4 to e, Db4 to e, Eb4 to e,
            F4 to e, Gb4 to e, Ab4 to e, Gb4 to e,
            F4 to e, Eb4 to e, Db4 to e, Eb4 to e,

            Bb4 to e, B4 to e, Db5 to e, Eb5 to e,
            Db5 to e, B4 to e, Bb4 to e, Ab4 to e,
            Ab4 to e, Bb4 to e, B4 to e, Db5 to e,
            Eb5 to e, Db5 to e, B4 to e, Bb4 to e,
            Gb4 to e, Ab4 to e, Bb4 to e, B4 to e,
            Bb4 to e, Ab4 to e, Gb4 to e, F4 to e,
            Eb4 to e, F4 to e, Gb4 to e, Ab4 to e,
            Gb4 to e, F4 to e, Eb4 to e, Db4 to e,

            Eb4 to e, Eb4 to e, F4 to e, Gb4 to e,
            Ab4 to e, Bb4 to e, B4 to e, Db5 to e,
            Db5 to e, B4 to e, Bb4 to e, Ab4 to e,
            Gb4 to e, F4 to e, Eb4 to e, Db4 to e,
            Eb4 to e, Gb4 to e, Bb4 to e, Eb5 to e,
            Db5 to e, B4 to e, Bb4 to e, Ab4 to e,
            Gb4 to e, F4 to e, Eb4 to e, Db4 to e,
            Eb4 to e, F4 to e, Gb4 to e, Ab4 to e,

            Bb4 to e, Bb4 to e, B4 to e, Bb4 to e,
            Ab4 to e, Gb4 to e, F4 to e, Eb4 to e,
            Eb4 to e, F4 to e, Gb4 to e, Ab4 to e,
            Bb4 to e, Ab4 to e, Gb4 to e, F4 to e,
            Gb4 to e, Gb4 to e, Ab4 to e, Gb4 to e,
            F4 to e, Eb4 to e, Db4 to e, Eb4 to e,
            F4 to e, Gb4 to e, Ab4 to e, Gb4 to e,
            F4 to e, Eb4 to e, Db4 to e, Eb4 to e
        )
    }

    private fun getBadAppleHarmonyNotes(q: Int): List<Pair<Float, Int>> {
        val ebmArp = listOf(Eb3 to q, Gb3 to q, Bb3 to q, Gb3 to q)
        val cbArp  = listOf(B3 to q, Eb4 to q, Gb4 to q, Eb4 to q)
        val dbArp  = listOf(Db4 to q, F4 to q, Ab4 to q, F4 to q)
        val ebmArp2 = listOf(Eb3 to q, Bb3 to q, Gb3 to q, Bb3 to q)

        return repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
               repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
               repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
               repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
               repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
               repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
               repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
               repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1)
    }

    private fun getBadAppleBassNotes(e: Int): List<Pair<Float, Int>> {
        val ebmBass = listOf(Eb2 to e, Eb2 to e, Eb2 to e, Eb2 to e,
                             Eb2 to e, Eb3 to e, Eb2 to e, Eb3 to e)
        val cbBass  = listOf(B2 to e, B2 to e, B2 to e, B2 to e,
                             B2 to e, B3 to e, B2 to e, B3 to e)
        val dbBass  = listOf(Db3 to e, Db3 to e, Db3 to e, Db3 to e,
                             Db3 to e, Db4 to e, Db3 to e, Db4 to e)
        val ebmBass2 = listOf(Eb2 to e, Eb3 to e, Eb2 to e, Eb2 to e,
                               Eb2 to e, Eb2 to e, Eb3 to e, Eb2 to e)

        val oneCycle = ebmBass + cbBass + dbBass + ebmBass2
        return repeatBar(oneCycle, 4)
    }

    private fun buildBadAppleLead(vol: Float, e: Int, q: Int, s: Int): Lane {
        val notes = getBadAppleLeadNotes(e)
        return Lane(
            buildChannel(notes, 0.7f * vol, "pulse25",
                attackMs = 10, decayMs = 50, sustainLevel = 0.7f, releaseMs = 80),
            TimbreRef.FM_LEAD_ZUN1
        )
    }

    private fun buildBadAppleHarmony(vol: Float, e: Int, q: Int): Lane {
        val notes = getBadAppleHarmonyNotes(q)
        return Lane(
            buildChannel(notes, 0.35f * vol, "square",
                attackMs = 15, decayMs = 30, sustainLevel = 0.5f, releaseMs = 60),
            TimbreRef.SSG_HARMONY_SQUARE
        )
    }

    private fun buildBadAppleBass(vol: Float, e: Int): Lane {
        val notes = getBadAppleBassNotes(e)
        return Lane(
            buildChannel(notes, 0.5f * vol, "triangle",
                attackMs = 5, decayMs = 20, sustainLevel = 0.8f, releaseMs = 30),
            TimbreRef.FM_BASS_ZUN1
        )
    }

    private fun buildBadApplePercussion(vol: Float, e: Int): Lane {
        val hiHat = 8000f
        val snare = 3000f

        val oneBar = listOf(
            hiHat to e, hiHat to e, snare to e, hiHat to e,
            hiHat to e, hiHat to e, snare to e, hiHat to e
        )
        val notes = repeatBar(oneBar, 16)

        return Lane(
            buildChannel(notes, 0.2f * vol, "noise-metallic",
                attackMs = 2, decayMs = 0, sustainLevel = 0.0f, releaseMs = 15),
            TimbreRef.DRUM_HAT
        )
    }

    private fun buildSenbonzakuraLead(vol: Float, e: Int, q: Int, s: Int): Lane {
        val notes = listOf(
            D4 to e, F4 to e, G4 to e, A4 to e,
            D5 to e, C5 to e, Bb4 to e, A4 to e,
            G4 to e, F4 to e, G4 to e, A4 to q,
            A4 to q, 0f to e,
            D4 to e, F4 to e, G4 to e, A4 to e,
            D5 to e, C5 to e, Bb4 to e, A4 to e,
            G4 to e, F4 to e, E4 to e, D4 to q,
            E4 to e, D4 to q,

            D4 to e, F4 to e, G4 to e, A4 to e,
            D5 to e, C5 to e, Bb4 to e, A4 to e,
            G4 to e, F4 to e, G4 to e, A4 to q,
            A4 to q, 0f to e,
            D4 to e, F4 to e, G4 to e, A4 to e,
            D5 to e, C5 to e, Bb4 to e, A4 to e,
            G4 to e, F4 to e, E4 to e, D4 to q,
            D4 to q, 0f to e,

            D5 to e, D5 to e, C5 to e, Bb4 to e,
            A4 to e, G4 to e, A4 to e, Bb4 to e,
            C5 to e, C5 to e, Bb4 to e, A4 to e,
            G4 to e, F4 to e, G4 to e, A4 to e,
            Bb4 to e, A4 to e, G4 to e, F4 to e,
            E4 to e, D4 to q, D4 to e,
            D4 to q, E4 to q, F4 to q, A4 to q,

            D5 to e, D5 to e, C5 to e, Bb4 to e,
            A4 to e, G4 to e, A4 to e, Bb4 to e,
            C5 to e, C5 to e, Bb4 to e, A4 to e,
            G4 to e, F4 to e, E4 to e, D4 to e,
            Bb4 to e, Bb4 to e, C5 to e, D5 to e,
            C5 to e, Bb4 to e, A4 to e, G4 to e,
            A4 to e, G4 to e, F4 to e, E4 to e,
            D4 to q, D4 to q
        )
        return Lane(
            buildChannel(notes, 0.7f * vol, "pulse25",
                attackMs = 8, decayMs = 40, sustainLevel = 0.75f, releaseMs = 60),
            TimbreRef.FM_LEAD_ZUN1,
            LaneMode.MONO_RETRIGGER
        )
    }

    private fun buildSenbonzakuraHarmony(vol: Float, e: Int, q: Int): Lane {
        val bbHalf = listOf(Bb3 to q, D4 to q)
        val cHalf = listOf(C4 to q, E4 to q)
        val dmFull = listOf(D4 to e, F4 to e, A4 to e, F4 to e, D4 to e, F4 to e, A4 to e, F4 to e)
        val fHalf = listOf(F3 to q, A3 to q)
        val dmHalf = listOf(D4 to q, F4 to q)

        val notes = (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + (fHalf + dmHalf) +
                    (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + (fHalf + dmHalf) +
                    (bbHalf + cHalf) + dmFull

        return Lane(
            buildChannel(notes, 0.35f * vol, "square",
                attackMs = 5, decayMs = 20, sustainLevel = 0.6f, releaseMs = 40),
            TimbreRef.SSG_HARMONY_SQUARE,
            LaneMode.SSG_MONO
        )
    }

    private fun buildSenbonzakuraBass(vol: Float, e: Int): Lane {
        val bbHalf = listOf(Bb2 to e, Bb2 to e, Bb2 to e, Bb2 to e)
        val cHalf = listOf(C3 to e, C3 to e, C3 to e, C3 to e)
        val dmFull = listOf(D2 to e, D2 to e, D2 to e, D2 to e, D2 to e, D3 to e, D2 to e, D3 to e)
        val fHalf = listOf(F2 to e, F2 to e, F2 to e, F2 to e)
        val dmHalf = listOf(D2 to e, D2 to e, D2 to e, D2 to e)

        val notes = (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + (fHalf + dmHalf) +
                    (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + dmFull +
                    (bbHalf + cHalf) + (fHalf + dmHalf) +
                    (bbHalf + cHalf) + dmFull

        return Lane(
            buildChannel(notes, 0.5f * vol, "square",
                attackMs = 5, decayMs = 15, sustainLevel = 0.85f, releaseMs = 25),
            TimbreRef.SSG_BASS_SQUARE,
            LaneMode.SSG_MONO
        )
    }

    private fun buildSenbonzakuraPercussion(vol: Float, e: Int): Lane {
        val hiHat = 9000f
        val snare = 3500f

        val oneBar = listOf(
            hiHat to e, hiHat to e, snare to e, hiHat to e,
            hiHat to e, hiHat to e, snare to e, hiHat to e
        )
        val notes = repeatBar(oneBar, 16)

        return Lane(
            buildChannel(notes, 0.2f * vol, "noise-metallic",
                attackMs = 2, decayMs = 0, sustainLevel = 0.0f, releaseMs = 12),
            TimbreRef.DRUM_HAT
        )
    }

    // ================================================================
    // Bad Apple!! (東方幻想郷) - synth-bad-apple-LotusLandStory
    // PC-98 / OPNA arrangement at 160.73 BPM in E♭m → Gm
    // 40 bars total (0 + 8 + 16 + 16) = ~60 sec
    // ================================================================

    private const val BA_LLS_BPM = 160.73f
    internal fun buildBadAppleLotusLandStory(vol: Float): ArrangementLanes {
        val e = 186
        val q = 373
        val s = 93

        val leadNotes16 = getBadAppleLeadNotes(e)
        val harmonyNotes16 = getBadAppleHarmonyNotes(q)
        val bassNotes16 = getBadAppleBassNotes(e)

        val leadNotes40 = leadNotes16 + leadNotes16 + leadNotes16.take(64)
        val harmonyNotes40 = harmonyNotes16 + harmonyNotes16 + harmonyNotes16.take(32)
        val bassNotes40 = bassNotes16 + bassNotes16 + bassNotes16.take(64)

        val kickBeats = badAppleKickBeats(40)
        val hiHat = 8000f
        val spec = SongSpec(bpm = BA_LLS_BPM, beatsPerBar = 4)
        val introBars = 0
        val aBars = 8
        val bBars = 16
        val chorusBars = 16

        val aStart = introBars
        val bStart = introBars + aBars
        val chorusStart = introBars + aBars + bBars

        val leadNotes = compileLlsNotes(spec, buildLlsLeadSpecs(vol, aStart, bStart, chorusStart))
        val harmonyNotes = compileLlsNotes(spec, buildLlsHarmonySpecs(vol, aStart, bStart, chorusStart))
        val bassNotes = compileLlsNotes(spec, buildLlsBassSpecs(vol, aStart, bStart, chorusStart))
        val drumNotes = compileLlsNotes(spec, buildLlsDrumsSpecs(vol))

        return ArrangementLanes(
            lead = Lane(leadNotes, TimbreRef.FM_LLS_AT54, LaneMode.MONO_RETRIGGER),
            harmony = Lane(harmonyNotes, TimbreRef.SSG_HARMONY_SQUARE, LaneMode.SSG_MONO),
            bass = Lane(bassNotes, TimbreRef.FM_LLS_AT99, LaneMode.MONO_RETRIGGER),
            percussion = Lane(drumNotes, TimbreRef.DRUM_HAT, LaneMode.DRUM),
            tempoBpm = BA_LLS_BPM,
            keyRootMidi = 63
        )
    }

    private const val mEb2 = 39
    private const val mG2 = 43
    private const val mBb2 = 46
    private const val mB2 = 47
    private const val mDb3 = 49
    private const val mD3 = 50
    private const val mEb3 = 51
    private const val mF3 = 53
    private const val mGb3 = 54
    private const val mG3 = 55
    private const val mAb3 = 56
    private const val mA3 = 57
    private const val mBb3 = 58
    private const val mB3 = 59
    private const val mC4 = 60
    private const val mDb4 = 61
    private const val mD4 = 62
    private const val mEb4 = 63
    private const val mF4 = 65
    private const val mGb4 = 66
    private const val mG4 = 67
    private const val mAb4 = 68
    private const val mA4 = 69
    private const val mBb4 = 70
    private const val mB4 = 71
    private const val mC5 = 72
    private const val mDb5 = 73
    private const val mD5 = 74
    private const val mEb5 = 75
    private const val mF5 = 77
    private const val mGb5 = 78
    private const val mG5 = 79
    private const val mAb5 = 80
    private const val mA5 = 81
    private const val mBb5 = 82
    private const val mB5 = 83
    private const val mC6 = 84
    private const val mDb6 = 85
    private const val mD6 = 86
    private const val mEb6 = 87

    private fun MutableList<NoteSpec>.addEighths(bar: Int, notes: List<Int>, vol: Float) {
        var i = 0
        while (i < notes.size && i < 8) {
            val beat = i / 2
            val tick = if (i % 2 == 0) 0 else 240
            val midi = notes[i]
            this.add(
                NoteSpec(
                    bar = bar, beat = beat, tick = tick, midi = midi,
                    length = NoteLength.EIGHTH, velocity = vol, releaseMs = 30
                )
            )
            i++
        }
    }

    private fun MutableList<NoteSpec>.addChord(bar: Int, beat: Int, notes: List<Int>, length: NoteLength, vol: Float) {
        var i = 0
        while (i < notes.size) {
            this.add(
                NoteSpec(
                    bar = bar, beat = beat, tick = 0, midi = notes[i],
                    length = length, velocity = vol, releaseMs = 40
                )
            )
            i++
        }
    }

    private fun MutableList<NoteSpec>.addNote(bar: Int, beat: Int, tick: Int, midi: Int, length: NoteLength, vol: Float) {
        this.add(
            NoteSpec(
                bar = bar, beat = beat, tick = tick, midi = midi,
                length = length, velocity = vol, releaseMs = 30
            )
        )
    }

    private fun buildLlsLeadSpecs(vol: Float, aStart: Int, bStart: Int, chorusStart: Int): List<NoteSpec> {
        val notes = mutableListOf<NoteSpec>()

        // A section (bars 0-7): E♭m melody (True Bad Apple Verse hook)
        var bar = 0
        while (bar < 8) {
            val start = aStart + bar
            if (bar % 4 == 0 || bar % 4 == 1) {
                notes.addEighths(start, listOf(mBb4, mBb4, mB4, mBb4, mAb4, mGb4, mF4, mEb4), 0.75f * vol)
            } else if (bar % 4 == 2) {
                notes.addEighths(start, listOf(mEb4, mF4, mGb4, mAb4, mBb4, mAb4, mGb4, mF4), 0.75f * vol)
            } else {
                notes.addEighths(start, listOf(mGb4, mGb4, mAb4, mGb4, mF4, mEb4, mDb4, mEb4), 0.75f * vol)
            }
            bar++
        }

        // B section (bars 8-23): Pre-chorus bridge
        var b = 0
        while (b < 16) {
            val start = bStart + b
            val phrase = b % 8
            if (phrase == 0 || phrase == 4) {
                notes.addNote(start, 0, 0, mEb5, NoteLength.HALF, 0.75f * vol)
                notes.addNote(start, 2, 0, mF5,  NoteLength.HALF, 0.75f * vol)
            } else if (phrase == 1 || phrase == 5) {
                notes.addNote(start, 0, 0, mGb5, NoteLength.HALF, 0.75f * vol)
                notes.addNote(start, 2, 0, mAb5, NoteLength.HALF, 0.75f * vol)
            } else if (phrase == 2 || phrase == 6) {
                notes.addNote(start, 0, 0, mBb5, NoteLength.WHOLE, 0.75f * vol)
            } else if (phrase == 3) {
                notes.addNote(start, 0, 0, mBb5, NoteLength.HALF, 0.75f * vol)
                notes.addNote(start, 2, 0, mAb5, NoteLength.QUARTER, 0.75f * vol)
                notes.addNote(start, 3, 0, mGb5, NoteLength.QUARTER, 0.75f * vol)
            } else {
                notes.addNote(start, 0, 0, mBb5, NoteLength.HALF, 0.75f * vol)
                notes.addNote(start, 2, 0, mB5,  NoteLength.HALF, 0.75f * vol)
            }
            b++
        }

        // Chorus section (bars 24-39): Gm hook
        var cBar = 0
        while (cBar < 16) {
            val start = chorusStart + cBar
            val phrase = cBar % 4
            if (phrase == 0 || phrase == 2) {
                notes.addEighths(start, listOf(mD5, mD5, mEb5, mD5, mC5, mBb4, mA4, mG4), 0.8f * vol)
            } else if (phrase == 1) {
                notes.addEighths(start, listOf(mG4, mA4, mBb4, mC5, mD5, mC5, mBb4, mA4), 0.8f * vol)
            } else {
                notes.addEighths(start, listOf(mBb4, mBb4, mC5, mBb4, mA4, mG4, mF4, mG4), 0.8f * vol)
            }
            cBar++
        }

        return notes
    }

    private fun buildLlsHarmonySpecs(vol: Float, aStart: Int, bStart: Int, chorusStart: Int): List<NoteSpec> {
        val notes = mutableListOf<NoteSpec>()

        // A section (bars 0-7)
        var bar = 0
        while (bar < 8) {
            val start = aStart + bar
            if (bar % 4 == 0 || bar % 4 == 1) {
                notes.addChord(start, 0, listOf(mEb3, mGb3, mBb3), NoteLength.HALF, 0.35f * vol)
                notes.addChord(start, 2, listOf(mEb3, mGb3, mBb3), NoteLength.HALF, 0.35f * vol)
            } else if (bar % 4 == 2) {
                notes.addChord(start, 0, listOf(mB2, mEb3, mGb3), NoteLength.HALF, 0.35f * vol)
                notes.addChord(start, 2, listOf(mB2, mEb3, mGb3), NoteLength.HALF, 0.35f * vol)
            } else {
                notes.addChord(start, 0, listOf(mDb3, mF3, mAb3), NoteLength.HALF, 0.35f * vol)
                notes.addChord(start, 2, listOf(mDb3, mF3, mAb3), NoteLength.HALF, 0.35f * vol)
            }
            bar++
        }

        // B section (bars 8-23)
        var b = 0
        while (b < 16) {
            val start = bStart + b
            val chord = b % 4
            val base1 = if (chord == 0) mB2 else if (chord == 1) mDb3 else mEb3
            val base2 = if (chord == 0) mEb3 else if (chord == 1) mF3 else mGb3
            val base3 = if (chord == 0) mGb3 else if (chord == 1) mAb3 else mBb3

            notes.addChord(start, 0, listOf(base1, base2, base3), NoteLength.HALF, 0.35f * vol)
            notes.addChord(start, 2, listOf(base1, base2, base3), NoteLength.HALF, 0.35f * vol)
            b++
        }

        // Chorus section (bars 24-39) in Gm
        var cBar = 0
        while (cBar < 16) {
            val start = chorusStart + cBar
            val phrase = cBar % 4
            if (phrase == 0 || phrase == 2) {
                notes.addChord(start, 0, listOf(mEb3, mG3, mBb3), NoteLength.HALF, 0.35f * vol)
                notes.addChord(start, 2, listOf(mF3, mA3, mC4), NoteLength.HALF, 0.35f * vol)
            } else {
                notes.addChord(start, 0, listOf(mG3, mBb3, mD4), NoteLength.HALF, 0.35f * vol)
                notes.addChord(start, 2, listOf(mF3, mA3, mC4), NoteLength.HALF, 0.35f * vol)
            }
            cBar++
        }

        return notes
    }

    private fun buildLlsBassSpecs(vol: Float, aStart: Int, bStart: Int, chorusStart: Int): List<NoteSpec> {
        val notes = mutableListOf<NoteSpec>()

        // A section (bars 0-7)
        var bar = 0
        while (bar < 8) {
            val start = aStart + bar
            val chord = bar % 4
            val midi = if (chord == 0 || chord == 1) mEb2 else if (chord == 2) mB2 else mDb3
            val fifth = if (chord == 0 || chord == 1) mBb2 else if (chord == 2) mGb3 else mAb3

            notes.addEighths(start, listOf(midi, midi, fifth, fifth, midi, midi, fifth, fifth), 0.55f * vol)
            bar++
        }

        // B section (bars 8-23)
        var b = 0
        while (b < 16) {
            val start = bStart + b
            val chord = b % 4
            val midi = if (chord == 0) mB2 else if (chord == 1) mDb3 else mEb2
            val fifth = if (chord == 0) mGb3 else if (chord == 1) mAb3 else mBb2

            notes.addEighths(start, listOf(midi, midi, fifth, fifth, midi, midi, fifth, fifth), 0.55f * vol)
            b++
        }

        // Chorus section (bars 24-39)
        var cBar = 0
        while (cBar < 16) {
            val start = chorusStart + cBar
            val chord = cBar % 4
            if (chord == 0 || chord == 2) {
                notes.addEighths(start, listOf(mEb2, mEb2, mF3, mF3, mEb2, mEb2, mF3, mF3), 0.55f * vol)
            } else {
                notes.addEighths(start, listOf(mG2, mG2, mD3, mD3, mG2, mG2, mD3, mD3), 0.55f * vol)
            }
            cBar++
        }

        return notes
    }

    private fun buildLlsDrumsSpecs(vol: Float): List<NoteSpec> {
        val notes = mutableListOf<NoteSpec>()

        var bar = 0
        while (bar < 40) {
            notes.add(NoteSpec(bar, 0, midi = -1, length = NoteLength.QUARTER, velocity = 0.5f * vol, type = "kick"))
            notes.add(NoteSpec(bar, 2, midi = -1, length = NoteLength.QUARTER, velocity = 0.5f * vol, type = "kick"))

            notes.add(NoteSpec(bar, 1, midi = 72, length = NoteLength.QUARTER, velocity = 0.4f * vol, type = "snare"))
            notes.add(NoteSpec(bar, 3, midi = 72, length = NoteLength.QUARTER, velocity = 0.4f * vol, type = "snare"))

            var beat = 0
            while (beat < 4) {
                notes.add(NoteSpec(bar, beat, midi = 8000, length = NoteLength.EIGHTH, velocity = 0.3f * vol, type = "hat"))
                notes.add(NoteSpec(bar, beat, tick = 240, midi = 8000, length = NoteLength.EIGHTH, velocity = 0.3f * vol, type = "hat"))
                beat++
            }

            bar++
        }

        return notes
    }
}
