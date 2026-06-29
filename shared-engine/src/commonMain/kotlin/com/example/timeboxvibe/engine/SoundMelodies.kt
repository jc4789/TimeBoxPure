package com.example.timeboxvibe.engine

import com.example.timeboxvibe.engine.audio.opna.NoteLength
import com.example.timeboxvibe.engine.audio.opna.NoteSpec
import com.example.timeboxvibe.engine.audio.opna.SongSpec
import com.example.timeboxvibe.engine.audio.opna.compileNotes

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

data class ArrangementLanes(
    val lead: Lane,
    val harmony: Lane,
    val bass: Lane,
    val percussion: Lane,
    val tempoBpm: Float,
    val keyRootMidi: Int
)

object SoundMelodies {
    val supportedKeys = listOf(
        "synth-chime",
        "synth-victory",
        "oriental",
        "synth-bad-apple",
        "synth-senbonzakura",
        "synth-bad-apple-LotusLandStory"
    )

    fun getMelody(key: String, volume: Float, isBass: Boolean): List<ToneSpec> {
        return when (key) {
            "synth-chime" -> {
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
            "synth-victory" -> {
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
            "synth-bad-apple" -> {
                if (isBass) emptyList() else getBadAppleArrangement(volume)
            }
            "synth-senbonzakura" -> {
                if (isBass) emptyList() else getSenbonzakuraArrangement(volume)
            }
            else -> emptyList()
        }
    }

    fun getArrangement(key: String, volume: Float = 1f): ArrangementLanes? {
        return when (key) {
            "synth-chime" -> {
                val e = 333
                val q = 667
                ArrangementLanes(
                    lead = Lane(listOf(
                        ToneSpec(466f, 0, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(554f, e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(698f, 2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(554f, 2*e+q, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(466f, 2*e+q+e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),
                        ToneSpec(349f, 2*e+q+2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50)
                    ), TimbreRef.FM_BELL_ZUN1),
                    harmony = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE),
                    bass = Lane(listOf(
                        ToneSpec(116f, 0, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30),
                        ToneSpec(116f, q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30),
                        ToneSpec(174f, 2*q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30)
                    ), TimbreRef.FM_BASS_ZUN1),
                    percussion = Lane(listOf(
                        ToneSpec(8000f, q, e, 0.3f * volume, "hat", false),
                        ToneSpec(8000f, 2*q+q, e, 0.3f * volume, "hat", false)
                    ), TimbreRef.DRUM_HAT),
                    tempoBpm = 90f,
                    keyRootMidi = 70
                )
            }
            "synth-victory" -> {
                val e = 250
                val q = 500
                val h = 1000
                ArrangementLanes(
                    lead = Lane(
                        listOf(
                            ToneSpec(523f, 0, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),
                            ToneSpec(659f, e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),
                            ToneSpec(784f, 2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),
                            ToneSpec(1046f, 2*e+q, h, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 100)
                        ),
                        TimbreRef.FM_LEAD_ZUN1
                    ),
                    harmony = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE),
                    bass = Lane(listOf(
                        ToneSpec(130f, 0, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),
                        ToneSpec(130f, q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),
                        ToneSpec(196f, 2*q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),
                        ToneSpec(261f, 2*q+q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30)
                    ), TimbreRef.FM_BASS_ZUN1),
                    percussion = Lane(listOf(
                        ToneSpec(-1f, 0, q, 0.5f * volume, "kick", false),
                        ToneSpec(3000f, 2*q, q, 0.4f * volume, "snare", false)
                    ), TimbreRef.DRUM_HAT),
                    tempoBpm = 120f,
                    keyRootMidi = 60
                )
            }
            "synth-bad-apple" -> {
                val e = 217
                val q = 434
                val s = 109
                val kickBeats = badAppleKickBeats(16)
                val percussion = Lane(
                    notes = buildKickLane(kickBeats, e, 0.5f * volume) +
                             buildBadApplePercussion(volume, e).notes,
                    timbre = TimbreRef.DRUM_HAT
                )
                ArrangementLanes(
                    lead = buildBadAppleLead(volume, e, q, s),
                    harmony = buildBadAppleHarmony(volume, e, q),
                    bass = buildBadAppleBass(volume, e),
                    percussion = percussion,
                    tempoBpm = 138f,
                    keyRootMidi = 63
                )
            }
            "synth-senbonzakura" -> {
                val e = 195
                val q = 390
                val s = 97
                val kickBeats = senbonzakuraKickBeats(16)
                val percussion = Lane(
                    notes = buildKickLane(kickBeats, e, 0.5f * volume) +
                             buildSenbonzakuraPercussion(volume, e).notes,
                    timbre = TimbreRef.DRUM_HAT
                )
                ArrangementLanes(
                    lead = buildSenbonzakuraLead(volume, e, q, s),
                    harmony = buildSenbonzakuraHarmony(volume, e, q),
                    bass = buildSenbonzakuraBass(volume, e),
                    percussion = percussion,
                    tempoBpm = 154f,
                    keyRootMidi = 62
                )
            }
            "synth-bad-apple-LotusLandStory" -> buildBadAppleLotusLandStory(volume)
            else -> null
        }
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

    private fun buildBadAppleLead(vol: Float, e: Int, q: Int, s: Int): Lane {
        val notes = listOf(
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
        return Lane(
            buildChannel(notes, 0.7f * vol, "pulse25",
                attackMs = 10, decayMs = 50, sustainLevel = 0.7f, releaseMs = 80),
            TimbreRef.FM_LEAD_ZUN1
        )
    }

    private fun buildBadAppleHarmony(vol: Float, e: Int, q: Int): Lane {
        val ebmArp = listOf(Eb3 to q, Gb3 to q, Bb3 to q, Gb3 to q)
        val cbArp  = listOf(B3 to q, Eb4 to q, Gb4 to q, Eb4 to q)
        val dbArp  = listOf(Db4 to q, F4 to q, Ab4 to q, F4 to q)
        val ebmArp2 = listOf(Eb3 to q, Bb3 to q, Gb3 to q, Bb3 to q)

        val notes = repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
                    repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
                    repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
                    repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
                    repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
                    repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
                    repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
                    repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1)

        return Lane(
            buildChannel(notes, 0.35f * vol, "square",
                attackMs = 15, decayMs = 30, sustainLevel = 0.5f, releaseMs = 60),
            TimbreRef.SSG_HARMONY_SQUARE
        )
    }

    private fun buildBadAppleBass(vol: Float, e: Int): Lane {
        val ebmBass = listOf(Eb2 to e, Eb2 to e, Eb2 to e, Eb2 to e,
                             Eb2 to e, Eb3 to e, Eb2 to e, Eb3 to e)
        val cbBass  = listOf(B2 to e, B2 to e, B2 to e, B2 to e,
                             B2 to e, B3 to e, B2 to e, B3 to e)
        val dbBass  = listOf(Db3 to e, Db3 to e, Db3 to e, Db3 to e,
                             Db3 to e, Db4 to e, Db3 to e, Db4 to e)
        val ebmBass2 = listOf(Eb2 to e, Eb3 to e, Eb2 to e, Eb2 to e,
                               Eb2 to e, Eb2 to e, Eb3 to e, Eb2 to e)

        val oneCycle = ebmBass + cbBass + dbBass + ebmBass2
        val notes = repeatBar(oneCycle, 4)

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
    private const val BA_LLS_TOTAL_BARS = 40

    private fun buildBadAppleLotusLandStory(vol: Float): ArrangementLanes {
        val spec = SongSpec(bpm = BA_LLS_BPM, beatsPerBar = 4)
        
        val introBars = 0
        val aBars = 8
        val bBars = 16
        val chorusBars = 16
        
        val aStart = introBars
        val bStart = introBars + aBars
        val chorusStart = introBars + aBars + bBars

        val leadNotes = buildLlsLead(spec, vol, aStart, bStart, chorusStart)
        val harmonyNotes = buildLlsHarmony(spec, vol, aStart, bStart, chorusStart)
        val bassNotes = buildLlsBass(spec, vol, aStart, bStart, chorusStart)
        val drumNotes = buildLlsDrums(spec, vol)

        return ArrangementLanes(
            lead = Lane(leadNotes, TimbreRef.FM_LLS_AT54, LaneMode.MONO_RETRIGGER),
            harmony = Lane(harmonyNotes, TimbreRef.SSG_HARMONY_SQUARE, LaneMode.SSG_MONO),
            bass = Lane(bassNotes, TimbreRef.FM_LLS_AT99, LaneMode.MONO_RETRIGGER),
            percussion = Lane(drumNotes, TimbreRef.DRUM_HAT, LaneMode.DRUM),
            tempoBpm = BA_LLS_BPM,
            keyRootMidi = 63
        )
    }

    private fun buildLlsLead(spec: SongSpec, vol: Float, aStart: Int, bStart: Int, chorusStart: Int): List<ToneSpec> {
        val notes = mutableListOf<NoteSpec>()
        
        // A section (bars 0-7): E♭m melody
        // Bar 0-1: E♭m phrase
        notes.add(NoteSpec(aStart, 0, midi = 75, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25")) // Eb4
        notes.add(NoteSpec(aStart, 1, midi = 75, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart, 2, midi = 76, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25")) // B4 (not in scale, passing tone)
        notes.add(NoteSpec(aStart, 3, midi = 75, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        
        notes.add(NoteSpec(aStart + 1, 0, midi = 72, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25")) // Ab4
        notes.add(NoteSpec(aStart + 1, 1, midi = 70, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25")) // Gb4
        notes.add(NoteSpec(aStart + 1, 2, midi = 69, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25")) // F4
        notes.add(NoteSpec(aStart + 1, 3, midi = 67, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25")) // Eb4
        
        // Bar 2-3: C♭ → D♭ Ddim
        notes.add(NoteSpec(aStart + 2, 0, midi = 67, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 2, 1, midi = 69, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 2, 2, midi = 70, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 2, 3, midi = 72, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        
        notes.add(NoteSpec(aStart + 3, 0, midi = 70, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 3, 1, midi = 69, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 3, 2, midi = 67, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 3, 3, midi = 65, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25")) // F4
        
        // Bar 4-5: E♭m → E♭m-D♭
        notes.add(NoteSpec(aStart + 4, 0, midi = 70, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 4, 1, midi = 70, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 4, 2, midi = 72, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 4, 3, midi = 70, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        
        notes.add(NoteSpec(aStart + 5, 0, midi = 69, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 5, 1, midi = 67, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 5, 2, midi = 65, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 5, 3, midi = 63, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25")) // Eb4
        
        // Bar 6-7: C♭ → D♭ Ddim
        notes.add(NoteSpec(aStart + 6, 0, midi = 67, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 6, 1, midi = 69, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 6, 2, midi = 70, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 6, 3, midi = 72, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        
        notes.add(NoteSpec(aStart + 7, 0, midi = 70, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 7, 1, midi = 69, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 7, 2, midi = 67, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        notes.add(NoteSpec(aStart + 7, 3, midi = 65, length = NoteLength.EIGHTH, velocity = 0.7f * vol, type = "pulse25"))
        
        // B section (bars 8-23): Higher energy
        // Bar 8-11: C♭ | D♭ | E♭m | E♭m
        notes.add(NoteSpec(bStart, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25")) // Eb4
        notes.add(NoteSpec(bStart, 2, midi = 77, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25")) // Bb4
        notes.add(NoteSpec(bStart + 1, 0, midi = 74, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25")) // Db4
        notes.add(NoteSpec(bStart + 1, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25")) // Gb4
        notes.add(NoteSpec(bStart + 2, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 2, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 3, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 3, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        
        // Bar 12-15: C♭ | D♭ | E♭m | E♭m-D♭
        notes.add(NoteSpec(bStart + 4, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 4, 2, midi = 77, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 5, 0, midi = 74, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 5, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 6, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 6, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 7, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 7, 2, midi = 74, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25")) // Db4
        
        // Bar 16-19: repeat
        notes.add(NoteSpec(bStart + 8, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 8, 2, midi = 77, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 9, 0, midi = 74, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 9, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 10, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 10, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 11, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 11, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        
        // Bar 20-23: repeat with variation
        notes.add(NoteSpec(bStart + 12, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 12, 2, midi = 77, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 13, 0, midi = 74, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 13, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 14, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 14, 2, midi = 79, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 15, 0, midi = 75, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        notes.add(NoteSpec(bStart + 15, 2, midi = 74, length = NoteLength.QUARTER, velocity = 0.75f * vol, type = "pulse25"))
        
        // Chorus (bars 24-39): Key change to Gm, higher energy
        // "E♭ F | Gm - - F" pattern repeats 7 times, then "E♭ F | Gm"
        val chorusPattern = listOf(
            NoteSpec(0, 0, midi = 83, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"), // Eb5
            NoteSpec(0, 1, midi = 84, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"), // F5
            NoteSpec(0, 2, midi = 83, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"),
            NoteSpec(0, 3, midi = 82, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"), // Db5
            NoteSpec(1, 0, midi = 79, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"), // Gb4
            NoteSpec(1, 1, midi = 77, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"), // Bb4
            NoteSpec(1, 2, midi = 79, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"),
            NoteSpec(1, 3, midi = 84, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25")  // F5
        )
        
        var chorusBar = 0
        while (chorusBar < 14) {
            for (patternNote in chorusPattern) {
                notes.add(patternNote.copy(bar = chorusStart + chorusBar + patternNote.bar))
            }
            chorusBar += 2
        }
        
        // Final 2 bars: E♭ F | Gm
        notes.add(NoteSpec(chorusStart + 14, 0, midi = 83, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"))
        notes.add(NoteSpec(chorusStart + 14, 1, midi = 84, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"))
        notes.add(NoteSpec(chorusStart + 14, 2, midi = 83, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"))
        notes.add(NoteSpec(chorusStart + 14, 3, midi = 82, length = NoteLength.EIGHTH, velocity = 0.8f * vol, type = "pulse25"))
        notes.add(NoteSpec(chorusStart + 15, 0, midi = 79, length = NoteLength.QUARTER, velocity = 0.8f * vol, type = "pulse25"))
        notes.add(NoteSpec(chorusStart + 15, 2, midi = 77, length = NoteLength.QUARTER, velocity = 0.8f * vol, type = "pulse25"))
        
        return compileNotes(spec, notes)
    }

    private fun buildLlsHarmony(spec: SongSpec, vol: Float, aStart: Int, bStart: Int, chorusStart: Int): List<ToneSpec> {
        val notes = mutableListOf<NoteSpec>()
        
        // A section: SSG square arpeggios
        // Bar 0-1: E♭m arp
        notes.add(NoteSpec(aStart, 0, midi = 63, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // Eb3
        notes.add(NoteSpec(aStart, 1, midi = 67, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // Gb3
        notes.add(NoteSpec(aStart, 2, midi = 70, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // Bb3
        notes.add(NoteSpec(aStart, 3, midi = 75, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // Eb4
        
        notes.add(NoteSpec(aStart + 1, 0, midi = 63, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 1, 1, midi = 67, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 1, 2, midi = 70, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 1, 3, midi = 75, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        
        // Bar 2: C♭ arp
        notes.add(NoteSpec(aStart + 2, 0, midi = 71, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // B3
        notes.add(NoteSpec(aStart + 2, 1, midi = 74, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // D4
        notes.add(NoteSpec(aStart + 2, 2, midi = 77, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // F4
        notes.add(NoteSpec(aStart + 2, 3, midi = 83, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // B4
        
        // Bar 3: D♭ Ddim
        notes.add(NoteSpec(aStart + 3, 0, midi = 73, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // Db4
        notes.add(NoteSpec(aStart + 3, 1, midi = 77, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // F4
        notes.add(NoteSpec(aStart + 3, 2, midi = 80, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // Ab4
        notes.add(NoteSpec(aStart + 3, 3, midi = 85, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // Db5
        
        // Continue pattern for remaining A section bars
        notes.add(NoteSpec(aStart + 4, 0, midi = 63, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 4, 1, midi = 67, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 4, 2, midi = 70, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 4, 3, midi = 75, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        
        notes.add(NoteSpec(aStart + 5, 0, midi = 63, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 5, 1, midi = 67, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 5, 2, midi = 70, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 5, 3, midi = 73, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square")) // Db4
        
        notes.add(NoteSpec(aStart + 6, 0, midi = 71, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 6, 1, midi = 74, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 6, 2, midi = 77, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 6, 3, midi = 83, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        
        notes.add(NoteSpec(aStart + 7, 0, midi = 73, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 7, 1, midi = 77, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 7, 2, midi = 80, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        notes.add(NoteSpec(aStart + 7, 3, midi = 85, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
        
        // B section: Continue arpeggios
        var bBar = 0
        while (bBar < 16) {
            val barChord = bBar % 4
            val baseMidi = when (barChord) {
                0 -> 71 // C♭
                1 -> 73 // D♭
                2 -> 63 // E♭m
                else -> 63 // E♭m
            }
            notes.add(NoteSpec(bStart + bBar, 0, midi = baseMidi, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
            notes.add(NoteSpec(bStart + bBar, 1, midi = baseMidi + 4, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
            notes.add(NoteSpec(bStart + bBar, 2, midi = baseMidi + 7, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
            notes.add(NoteSpec(bStart + bBar, 3, midi = baseMidi + 12, length = NoteLength.EIGHTH, velocity = 0.35f * vol, type = "square"))
            bBar++
        }
        
        // Chorus: SSG harmony in Gm
        val chorusArp = listOf(
            NoteSpec(0, 0, midi = 67, length = NoteLength.EIGHTH, velocity = 0.4f * vol, type = "square"), // G3
            NoteSpec(0, 1, midi = 70, length = NoteLength.EIGHTH, velocity = 0.4f * vol, type = "square"), // Bb3
            NoteSpec(0, 2, midi = 74, length = NoteLength.EIGHTH, velocity = 0.4f * vol, type = "square"), // D4
            NoteSpec(0, 3, midi = 79, length = NoteLength.EIGHTH, velocity = 0.4f * vol, type = "square")  // Gb4
        )
        
        var chorusBar = 0
        while (chorusBar < 16) {
            for (patternNote in chorusArp) {
                notes.add(patternNote.copy(bar = chorusStart + chorusBar + patternNote.bar))
            }
            chorusBar++
        }
        
        return compileNotes(spec, notes)
    }

    private fun buildLlsBass(spec: SongSpec, vol: Float, aStart: Int, bStart: Int, chorusStart: Int): List<ToneSpec> {
        val notes = mutableListOf<NoteSpec>()
        
        // A section: FM bass
        // Bar 0-1: E♭m bass
        notes.add(NoteSpec(aStart, 0, midi = 51, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12")) // Eb2
        notes.add(NoteSpec(aStart, 1, midi = 51, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart, 2, midi = 58, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12")) // Bb2
        notes.add(NoteSpec(aStart, 3, midi = 58, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        
        notes.add(NoteSpec(aStart + 1, 0, midi = 51, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 1, 1, midi = 51, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 1, 2, midi = 58, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 1, 3, midi = 56, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12")) // Gb2
        
        // Bar 2: C♭ bass
        notes.add(NoteSpec(aStart + 2, 0, midi = 59, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12")) // B2
        notes.add(NoteSpec(aStart + 2, 1, midi = 59, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 2, 2, midi = 65, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12")) // F2
        notes.add(NoteSpec(aStart + 2, 3, midi = 65, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        
        // Bar 3: D♭ bass
        notes.add(NoteSpec(aStart + 3, 0, midi = 49, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12")) // Db2
        notes.add(NoteSpec(aStart + 3, 1, midi = 49, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 3, 2, midi = 56, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12")) // Ab2
        notes.add(NoteSpec(aStart + 3, 3, midi = 56, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        
        // Continue for remaining A section
        notes.add(NoteSpec(aStart + 4, 0, midi = 51, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 4, 1, midi = 51, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 4, 2, midi = 58, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 4, 3, midi = 58, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        
        notes.add(NoteSpec(aStart + 5, 0, midi = 51, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 5, 1, midi = 51, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 5, 2, midi = 58, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 5, 3, midi = 49, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        
        notes.add(NoteSpec(aStart + 6, 0, midi = 59, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 6, 1, midi = 59, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 6, 2, midi = 65, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 6, 3, midi = 65, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        
        notes.add(NoteSpec(aStart + 7, 0, midi = 49, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 7, 1, midi = 49, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 7, 2, midi = 56, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        notes.add(NoteSpec(aStart + 7, 3, midi = 56, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
        
        // B section
        var bBar = 0
        while (bBar < 16) {
            val barChord = bBar % 4
            val rootMidi = when (barChord) {
                0 -> 59 // C♭
                1 -> 49 // D♭
                2 -> 51 // E♭m
                else -> 51 // E♭m
            }
            notes.add(NoteSpec(bStart + bBar, 0, midi = rootMidi, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
            notes.add(NoteSpec(bStart + bBar, 1, midi = rootMidi, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
            notes.add(NoteSpec(bStart + bBar, 2, midi = rootMidi + 7, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
            notes.add(NoteSpec(bStart + bBar, 3, midi = rootMidi + 7, length = NoteLength.EIGHTH, velocity = 0.5f * vol, type = "pulse12"))
            bBar++
        }
        
        // Chorus: Gm bass
        val chorusBass = listOf(
            NoteSpec(0, 0, midi = 55, length = NoteLength.EIGHTH, velocity = 0.55f * vol, type = "pulse12"), // G2
            NoteSpec(0, 1, midi = 62, length = NoteLength.EIGHTH, velocity = 0.55f * vol, type = "pulse12"), // D3
            NoteSpec(0, 2, midi = 55, length = NoteLength.EIGHTH, velocity = 0.55f * vol, type = "pulse12"),
            NoteSpec(0, 3, midi = 62, length = NoteLength.EIGHTH, velocity = 0.55f * vol, type = "pulse12")
        )
        
        var chorusBar = 0
        while (chorusBar < 16) {
            for (patternNote in chorusBass) {
                notes.add(patternNote.copy(bar = chorusStart + chorusBar + patternNote.bar))
            }
            chorusBar++
        }
        
        return compileNotes(spec, notes)
    }

    private fun buildLlsDrums(spec: SongSpec, vol: Float): List<ToneSpec> {
        val notes = mutableListOf<NoteSpec>()
        
        var bar = 0
        while (bar < BA_LLS_TOTAL_BARS) {
            // Kick on beats 1 and 3
            notes.add(NoteSpec(bar, 0, midi = -1, length = NoteLength.QUARTER, velocity = 0.5f * vol, type = "kick"))
            notes.add(NoteSpec(bar, 2, midi = -1, length = NoteLength.QUARTER, velocity = 0.5f * vol, type = "kick"))
            
            // Snare on beats 2 and 4
            notes.add(NoteSpec(bar, 1, midi = 72, length = NoteLength.QUARTER, velocity = 0.4f * vol, type = "snare"))
            notes.add(NoteSpec(bar, 3, midi = 72, length = NoteLength.QUARTER, velocity = 0.4f * vol, type = "snare"))
            
            // Hi-hat on every eighth
            var beat = 0
            while (beat < 4) {
                notes.add(NoteSpec(bar, beat, midi = 8000, length = NoteLength.EIGHTH, velocity = 0.3f * vol, type = "hat"))
                notes.add(NoteSpec(bar, beat, tick = 240, midi = 8000, length = NoteLength.EIGHTH, velocity = 0.3f * vol, type = "hat"))
                beat++
            }
            
            bar++
        }
        
        return compileNotes(spec, notes)
    }
}
