package com.example.timeboxvibe.engine

enum class TimbreRef {
    FM_LEAD_ZUN1,
    FM_BASS_ZUN1,
    FM_BELL_ZUN1,
    FM_PAD_ZUN1,
    SSG_HARMONY_SQUARE,
    SSG_BASS_SQUARE,
    SSG_PAD_SQUARE,
    SSG_ARP_PULSE,
    DRUM_KICK,
    DRUM_SNARE,
    DRUM_HAT
}

data class Lane(val notes: List<ToneSpec>, val timbre: TimbreRef)

data class ArrangementLanes(
    val lead: Lane,
    val harmony: Lane,
    val bass: Lane,
    val percussion: Lane,
    val tempoBpm: Int,
    val keyRootMidi: Int
)

object SoundMelodies {
    val supportedKeys = listOf(
        "synth-chime",
        "synth-victory",
        "oriental",
        "synth-bad-apple",
        "synth-senbonzakura"
    )

    fun getMelody(key: String, volume: Float, isBass: Boolean): List<ToneSpec> {
        return when (key) {
            "synth-chime" -> {
                if (isBass) {
                    listOf(ToneSpec(233f, 0, 800, 0.25f * volume, "triangle"))
                } else {
                    listOf(ToneSpec(466f, 0, 800, 0.22f * volume, "square"))
                }
            }
            "synth-victory" -> {
                if (isBass) {
                    emptyList()
                } else {
                    listOf(
                        ToneSpec(523.25f, 0, 1800, 0.15f * volume, "square"),
                        ToneSpec(659.25f, 120, 1800, 0.15f * volume, "square"),
                        ToneSpec(783.99f, 240, 1800, 0.15f * volume, "square"),
                        ToneSpec(1046.5f, 360, 1800, 0.15f * volume, "square")
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
                ArrangementLanes(
                    lead = Lane(listOf(ToneSpec(466f, 0, 800, 0.7f * volume, "square")), TimbreRef.SSG_HARMONY_SQUARE),
                    harmony = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE),
                    bass = Lane(listOf(ToneSpec(233f, 0, 800, 0.5f * volume, "triangle")), TimbreRef.SSG_BASS_SQUARE),
                    percussion = Lane(emptyList(), TimbreRef.DRUM_HAT),
                    tempoBpm = 90,
                    keyRootMidi = 70
                )
            }
            "synth-victory" -> {
                ArrangementLanes(
                    lead = Lane(
                        listOf(
                            ToneSpec(523.25f, 0, 1800, 0.5f * volume, "square"),
                            ToneSpec(659.25f, 120, 1800, 0.5f * volume, "square"),
                            ToneSpec(783.99f, 240, 1800, 0.5f * volume, "square"),
                            ToneSpec(1046.5f, 360, 1800, 0.5f * volume, "square")
                        ),
                        TimbreRef.FM_LEAD_ZUN1
                    ),
                    harmony = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE),
                    bass = Lane(emptyList(), TimbreRef.FM_BASS_ZUN1),
                    percussion = Lane(emptyList(), TimbreRef.DRUM_HAT),
                    tempoBpm = 120,
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
                    tempoBpm = 138,
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
                    tempoBpm = 154,
                    keyRootMidi = 62
                )
            }
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
    private const val Eb2 = 77.78f
    private const val F2  = 87.31f
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
            result.add(base + 2)
            result.add(base + 7)
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
            result.add(base + 2)
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
            TimbreRef.FM_BELL_ZUN1
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
            TimbreRef.SSG_HARMONY_SQUARE
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
            buildChannel(notes, 0.5f * vol, "triangle",
                attackMs = 5, decayMs = 15, sustainLevel = 0.85f, releaseMs = 25),
            TimbreRef.FM_BASS_ZUN1
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
}
