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
                    tempoBpm = 90f,
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

    // ================================================================
    // Bad Apple!! (東方幻想郷) - synth-bad-apple-LotusLandStory
    // PC-98 / OPNA arrangement at 160.73 BPM in E♭m → Gm
    // 47 bars total (4 + 16 + 12 + 15) = ~70.2 sec
    // ================================================================

    private const val BA_LLS_BPM = 160.73f
    private const val BA_LLS_SIXTEENTH_MS = 93
    private const val BA_LLS_EIGHTH_MS = 187
    private const val BA_LLS_QUARTER_MS = 373
    private const val BA_LLS_HALF_MS = 746
    private const val BA_LLS_WHOLE_MS = 1492
    private const val BA_LLS_KICK_FREQ = -1f
    private const val BA_LLS_SNARE_FREQ = 3000f
    private const val BA_LLS_HAT_FREQ = 8000f
    private const val BA_LLS_TOTAL_BARS = 47

    private fun buildBallsPercussion(vol: Float): List<ToneSpec> {
        val e = BA_LLS_EIGHTH_MS
        val q = BA_LLS_QUARTER_MS
        val out = mutableListOf<ToneSpec>()
        var bar = 0
        while (bar < BA_LLS_TOTAL_BARS) {
            val baseMs = bar * BA_LLS_WHOLE_MS
            out.add(ToneSpec(BA_LLS_KICK_FREQ, baseMs, q, vol * 0.5f, "kick", false))
            out.add(ToneSpec(BA_LLS_KICK_FREQ, baseMs + q + q, q, vol * 0.5f, "kick", false))
            out.add(ToneSpec(BA_LLS_SNARE_FREQ, baseMs + q, q, vol * 0.4f, "snare", false))
            out.add(ToneSpec(BA_LLS_SNARE_FREQ, baseMs + q + q + q, q, vol * 0.4f, "snare", false))
            var beat = 0
            while (beat < 8) {
                out.add(ToneSpec(BA_LLS_HAT_FREQ, baseMs + beat * e, e, vol * 0.3f, "hat", false))
                beat++
            }
            bar++
        }
        return out
    }

    private fun buildBallsIntro(vol: Float): Lane {
        val notes = mutableListOf<Pair<Float, Int>>()
        notes.add(Eb3 to BA_LLS_QUARTER_MS)
        notes.add(Gb3 to BA_LLS_QUARTER_MS)
        notes.add(Bb3 to BA_LLS_QUARTER_MS)
        notes.add(Eb3 to BA_LLS_QUARTER_MS)
        notes.add(Eb3 to BA_LLS_QUARTER_MS)
        notes.add(Gb3 to BA_LLS_QUARTER_MS)
        notes.add(Bb3 to BA_LLS_QUARTER_MS)
        notes.add(Eb3 to BA_LLS_QUARTER_MS)
        return Lane(
            buildChannel(notes, 0.6f * vol, "pulse25",
                attackMs = 20, decayMs = 60, sustainLevel = 0.6f, releaseMs = 100),
            TimbreRef.FM_LEAD_ZUN1
        )
    }

    private fun buildBallsIntroBass(vol: Float): Lane {
        val notes = mutableListOf<Pair<Float, Int>>()
        notes.add(Eb2 to BA_LLS_QUARTER_MS)
        notes.add(Bb2 to BA_LLS_QUARTER_MS)
        notes.add(Eb2 to BA_LLS_QUARTER_MS)
        notes.add(Bb2 to BA_LLS_QUARTER_MS)
        notes.add(Eb2 to BA_LLS_QUARTER_MS)
        notes.add(Bb2 to BA_LLS_QUARTER_MS)
        notes.add(Eb2 to BA_LLS_QUARTER_MS)
        notes.add(Bb2 to BA_LLS_QUARTER_MS)
        return Lane(
            buildChannel(notes, 0.5f * vol, "pulse12",
                attackMs = 5, decayMs = 30, sustainLevel = 0.7f, releaseMs = 40),
            TimbreRef.FM_BASS_ZUN1
        )
    }

    private fun buildBallsAMelodyLead(vol: Float): Lane {
        val phrase1 = mutableListOf<Pair<Float, Int>>()
        phrase1.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(B4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Ab4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(F4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(F4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Ab4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Ab4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(F4 to BA_LLS_EIGHTH_MS)
        phrase1.add(F4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Ab4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(F4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Db4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(F4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Ab4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Ab4 to BA_LLS_EIGHTH_MS)
        phrase1.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase1.add(F4 to BA_LLS_EIGHTH_MS)
        val phrase2 = mutableListOf<Pair<Float, Int>>()
        phrase2.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Ab4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(F4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Db4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(F4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Ab4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Gb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(F4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Db4 to BA_LLS_EIGHTH_MS)
        phrase2.add(C4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Db4 to BA_LLS_EIGHTH_MS)
        phrase2.add(D4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase2.add(D4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Db4 to BA_LLS_EIGHTH_MS)
        phrase2.add(C4 to BA_LLS_EIGHTH_MS)
        phrase2.add(B3 to BA_LLS_EIGHTH_MS)
        phrase2.add(C4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Db4 to BA_LLS_EIGHTH_MS)
        phrase2.add(D4 to BA_LLS_EIGHTH_MS)
        phrase2.add(C4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Bb3 to BA_LLS_EIGHTH_MS)
        phrase2.add(Ab3 to BA_LLS_EIGHTH_MS)
        phrase2.add(Bb3 to BA_LLS_EIGHTH_MS)
        phrase2.add(C4 to BA_LLS_EIGHTH_MS)
        phrase2.add(Db4 to BA_LLS_EIGHTH_MS)
        val notes = phrase1 + phrase2 + phrase1 + phrase2
        return Lane(
            buildChannel(notes, 0.7f * vol, "pulse25",
                attackMs = 10, decayMs = 50, sustainLevel = 0.7f, releaseMs = 80),
            TimbreRef.FM_LEAD_ZUN1
        )
    }

    private fun buildBallsAMelodyHarmony(vol: Float): Lane {
        val bar1Eb = mutableListOf<Pair<Float, Int>>()
        bar1Eb.add(Eb3 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Gb3 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Bb3 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Eb4 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Bb3 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Gb3 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Eb3 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Bb3 to BA_LLS_EIGHTH_MS)
        val bar3Cb = mutableListOf<Pair<Float, Int>>()
        bar3Cb.add(B3 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(D4 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(F4 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(B4 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(F4 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(D4 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(B3 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(F4 to BA_LLS_EIGHTH_MS)
        val bar4DbDim = mutableListOf<Pair<Float, Int>>()
        bar4DbDim.add(Db3 to BA_LLS_EIGHTH_MS)
        bar4DbDim.add(F3 to BA_LLS_EIGHTH_MS)
        bar4DbDim.add(Ab3 to BA_LLS_EIGHTH_MS)
        bar4DbDim.add(Db4 to BA_LLS_EIGHTH_MS)
        bar4DbDim.add(F4 to BA_LLS_EIGHTH_MS)
        bar4DbDim.add(D4 to BA_LLS_EIGHTH_MS)
        bar4DbDim.add(Db3 to BA_LLS_EIGHTH_MS)
        bar4DbDim.add(D4 to BA_LLS_EIGHTH_MS)
        val bar6EbToDb = mutableListOf<Pair<Float, Int>>()
        bar6EbToDb.add(Eb3 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Gb3 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Bb3 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Eb4 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Bb3 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Gb3 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Db3 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Ab3 to BA_LLS_EIGHTH_MS)
        val aSection = bar1Eb + bar1Eb + bar3Cb + bar4DbDim + bar1Eb + bar6EbToDb + bar3Cb + bar4DbDim
        val notes = aSection + aSection
        return Lane(
            buildChannel(notes, 0.35f * vol, "square",
                attackMs = 15, decayMs = 30, sustainLevel = 0.5f, releaseMs = 60),
            TimbreRef.SSG_HARMONY_SQUARE
        )
    }

    private fun buildBallsAMelodyBass(vol: Float): Lane {
        val bar1Eb = mutableListOf<Pair<Float, Int>>()
        bar1Eb.add(Eb2 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Eb2 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Bb2 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Bb2 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Eb2 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Eb2 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Bb2 to BA_LLS_EIGHTH_MS)
        bar1Eb.add(Gb2 to BA_LLS_EIGHTH_MS)
        val bar3Cb = mutableListOf<Pair<Float, Int>>()
        bar3Cb.add(B2 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(B2 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(F2 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(F2 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(B2 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(B2 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(F2 to BA_LLS_EIGHTH_MS)
        bar3Cb.add(D2 to BA_LLS_EIGHTH_MS)
        val bar4Db = mutableListOf<Pair<Float, Int>>()
        bar4Db.add(Db2 to BA_LLS_EIGHTH_MS)
        bar4Db.add(Db2 to BA_LLS_EIGHTH_MS)
        bar4Db.add(Ab2 to BA_LLS_EIGHTH_MS)
        bar4Db.add(Ab2 to BA_LLS_EIGHTH_MS)
        bar4Db.add(Db2 to BA_LLS_EIGHTH_MS)
        bar4Db.add(Db2 to BA_LLS_EIGHTH_MS)
        bar4Db.add(Ab2 to BA_LLS_EIGHTH_MS)
        bar4Db.add(F2 to BA_LLS_EIGHTH_MS)
        val bar6EbToDb = mutableListOf<Pair<Float, Int>>()
        bar6EbToDb.add(Eb2 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Eb2 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Bb2 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Bb2 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Eb2 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Eb2 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Db2 to BA_LLS_EIGHTH_MS)
        bar6EbToDb.add(Ab2 to BA_LLS_EIGHTH_MS)
        val aSection = bar1Eb + bar1Eb + bar3Cb + bar4Db + bar1Eb + bar6EbToDb + bar3Cb + bar4Db
        val notes = aSection + aSection
        return Lane(
            buildChannel(notes, 0.5f * vol, "pulse12",
                attackMs = 5, decayMs = 20, sustainLevel = 0.8f, releaseMs = 30),
            TimbreRef.FM_BASS_ZUN1
        )
    }

    private fun buildBallsBMelodyLead(vol: Float): Lane {
        val phraseA = mutableListOf<Pair<Float, Int>>()
        phraseA.add(C5 to BA_LLS_EIGHTH_MS)
        phraseA.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Ab4 to BA_LLS_EIGHTH_MS)
        phraseA.add(G4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Ab4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(C5 to BA_LLS_EIGHTH_MS)
        phraseA.add(D5 to BA_LLS_EIGHTH_MS)
        phraseA.add(Eb5 to BA_LLS_EIGHTH_MS)
        phraseA.add(D5 to BA_LLS_EIGHTH_MS)
        phraseA.add(C5 to BA_LLS_EIGHTH_MS)
        phraseA.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Ab4 to BA_LLS_EIGHTH_MS)
        phraseA.add(G4 to BA_LLS_EIGHTH_MS)
        phraseA.add(F4 to BA_LLS_EIGHTH_MS)
        phraseA.add(G4 to BA_LLS_EIGHTH_MS)
        phraseA.add(F4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Eb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(D4 to BA_LLS_EIGHTH_MS)
        phraseA.add(C4 to BA_LLS_EIGHTH_MS)
        phraseA.add(D4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Eb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(F4 to BA_LLS_EIGHTH_MS)
        phraseA.add(G4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Eb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(G4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(G4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Eb4 to BA_LLS_EIGHTH_MS)
        phraseA.add(Bb3 to BA_LLS_EIGHTH_MS)
        val phraseAv = mutableListOf<Pair<Float, Int>>()
        phraseAv.add(C5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Ab4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(G4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Ab4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(C5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(D5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Eb5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(D5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(C5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(C5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(D5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Eb5 to BA_LLS_EIGHTH_MS)
        phraseAv.add(G4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(D4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(F4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(G4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(D4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb3 to BA_LLS_EIGHTH_MS)
        phraseAv.add(G4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Eb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(G4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Ab4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(Gb4 to BA_LLS_EIGHTH_MS)
        phraseAv.add(F4 to BA_LLS_EIGHTH_MS)
        val phraseB = mutableListOf<Pair<Float, Int>>()
        phraseB.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(C5 to BA_LLS_EIGHTH_MS)
        phraseB.add(D5 to BA_LLS_EIGHTH_MS)
        phraseB.add(Eb5 to BA_LLS_EIGHTH_MS)
        phraseB.add(F5 to BA_LLS_EIGHTH_MS)
        phraseB.add(Eb5 to BA_LLS_EIGHTH_MS)
        phraseB.add(D5 to BA_LLS_EIGHTH_MS)
        phraseB.add(C5 to BA_LLS_EIGHTH_MS)
        phraseB.add(D5 to BA_LLS_EIGHTH_MS)
        phraseB.add(C5 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Ab4 to BA_LLS_EIGHTH_MS)
        phraseB.add(G4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Db5 to BA_LLS_EIGHTH_MS)
        phraseB.add(F5 to BA_LLS_EIGHTH_MS)
        phraseB.add(Eb5 to BA_LLS_EIGHTH_MS)
        phraseB.add(Db5 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(G4 to BA_LLS_EIGHTH_MS)
        phraseB.add(G4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Eb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb3 to BA_LLS_EIGHTH_MS)
        phraseB.add(G4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Eb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb3 to BA_LLS_EIGHTH_MS)
        phraseB.add(Eb4 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb3 to BA_LLS_EIGHTH_MS)
        phraseB.add(G3 to BA_LLS_EIGHTH_MS)
        phraseB.add(Bb3 to BA_LLS_EIGHTH_MS)
        val notes = phraseA + phraseAv + phraseB
        return Lane(
            buildChannel(notes, 0.7f * vol, "pulse25",
                attackMs = 8, decayMs = 40, sustainLevel = 0.75f, releaseMs = 60),
            TimbreRef.FM_BELL_ZUN1
        )
    }

    private fun buildBallsBMelodyHarmony(vol: Float): Lane {
        val barCb = mutableListOf<Pair<Float, Int>>()
        barCb.add(B3 to BA_LLS_HALF_MS)
        barCb.add(F4 to BA_LLS_HALF_MS)
        val barDb = mutableListOf<Pair<Float, Int>>()
        barDb.add(Db3 to BA_LLS_HALF_MS)
        barDb.add(Ab3 to BA_LLS_HALF_MS)
        val barEb = mutableListOf<Pair<Float, Int>>()
        barEb.add(Eb3 to BA_LLS_HALF_MS)
        barEb.add(Bb3 to BA_LLS_HALF_MS)
        val phrase1 = barCb + barDb + barEb + barEb
        val barDdim = mutableListOf<Pair<Float, Int>>()
        barDdim.add(D3 to BA_LLS_HALF_MS)
        barDdim.add(Ab3 to BA_LLS_HALF_MS)
        val phrase2 = barCb + barDb + barEb + barDdim
        val notes = phrase1 + phrase2 + phrase1
        return Lane(
            buildChannel(notes, 0.35f * vol, "square",
                attackMs = 5, decayMs = 20, sustainLevel = 0.6f, releaseMs = 40),
            TimbreRef.SSG_HARMONY_SQUARE
        )
    }

    private fun buildBallsBMelodyBass(vol: Float): Lane {
        val barCb = mutableListOf<Pair<Float, Int>>()
        barCb.add(B2 to BA_LLS_EIGHTH_MS)
        barCb.add(B2 to BA_LLS_EIGHTH_MS)
        barCb.add(B2 to BA_LLS_EIGHTH_MS)
        barCb.add(B2 to BA_LLS_EIGHTH_MS)
        barCb.add(F2 to BA_LLS_EIGHTH_MS)
        barCb.add(F2 to BA_LLS_EIGHTH_MS)
        barCb.add(B2 to BA_LLS_EIGHTH_MS)
        barCb.add(D2 to BA_LLS_EIGHTH_MS)
        val barDb = mutableListOf<Pair<Float, Int>>()
        barDb.add(Db2 to BA_LLS_EIGHTH_MS)
        barDb.add(Db2 to BA_LLS_EIGHTH_MS)
        barDb.add(Db2 to BA_LLS_EIGHTH_MS)
        barDb.add(Db2 to BA_LLS_EIGHTH_MS)
        barDb.add(Ab2 to BA_LLS_EIGHTH_MS)
        barDb.add(Ab2 to BA_LLS_EIGHTH_MS)
        barDb.add(Db2 to BA_LLS_EIGHTH_MS)
        barDb.add(F2 to BA_LLS_EIGHTH_MS)
        val barEb = mutableListOf<Pair<Float, Int>>()
        barEb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEb.add(Bb2 to BA_LLS_EIGHTH_MS)
        barEb.add(Bb2 to BA_LLS_EIGHTH_MS)
        barEb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEb.add(Gb2 to BA_LLS_EIGHTH_MS)
        val barEbToDb = mutableListOf<Pair<Float, Int>>()
        barEbToDb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEbToDb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEbToDb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEbToDb.add(Eb2 to BA_LLS_EIGHTH_MS)
        barEbToDb.add(Bb2 to BA_LLS_EIGHTH_MS)
        barEbToDb.add(Bb2 to BA_LLS_EIGHTH_MS)
        barEbToDb.add(Db2 to BA_LLS_EIGHTH_MS)
        barEbToDb.add(Ab2 to BA_LLS_EIGHTH_MS)
        val barDdim = mutableListOf<Pair<Float, Int>>()
        barDdim.add(D2 to BA_LLS_EIGHTH_MS)
        barDdim.add(D2 to BA_LLS_EIGHTH_MS)
        barDdim.add(D2 to BA_LLS_EIGHTH_MS)
        barDdim.add(D2 to BA_LLS_EIGHTH_MS)
        barDdim.add(Ab2 to BA_LLS_EIGHTH_MS)
        barDdim.add(Ab2 to BA_LLS_EIGHTH_MS)
        barDdim.add(D2 to BA_LLS_EIGHTH_MS)
        barDdim.add(F2 to BA_LLS_EIGHTH_MS)
        val phrase1 = barCb + barDb + barEb + barEbToDb
        val phrase2 = barCb + barDb + barEb + barEbToDb
        val phrase3 = barCb + barDb + barDdim + barEbToDb
        val notes = phrase1 + phrase2 + phrase3
        return Lane(
            buildChannel(notes, 0.5f * vol, "pulse12",
                attackMs = 5, decayMs = 15, sustainLevel = 0.85f, releaseMs = 25),
            TimbreRef.FM_BASS_ZUN1
        )
    }

    private fun buildBallsChorusLead(vol: Float): Lane {
        val phrase2bar = mutableListOf<Pair<Float, Int>>()
        phrase2bar.add(D5 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D5 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(C5 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(A4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(A4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(F4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(A4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(C5 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(F4 to BA_LLS_EIGHTH_MS)
        val phrase2barV = mutableListOf<Pair<Float, Int>>()
        phrase2barV.add(D5 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(Eb5 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(D5 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(C5 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(A4 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(C5 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(G4 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(Bb4 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(D5 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(G4 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(F4 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(A4 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(C5 to BA_LLS_EIGHTH_MS)
        phrase2barV.add(F4 to BA_LLS_EIGHTH_MS)
        val notes = phrase2bar + phrase2barV + phrase2bar + phrase2barV +
                    phrase2bar + phrase2barV + phrase2bar
        val final = mutableListOf<Pair<Float, Int>>()
        final.add(Bb4 to BA_LLS_EIGHTH_MS)
        final.add(A4 to BA_LLS_EIGHTH_MS)
        final.add(G4 to BA_LLS_EIGHTH_MS)
        final.add(F4 to BA_LLS_EIGHTH_MS)
        final.add(Eb4 to BA_LLS_EIGHTH_MS)
        final.add(G4 to BA_LLS_QUARTER_MS * 2)
        return Lane(
            buildChannel(notes + final, 0.7f * vol, "pulse25",
                attackMs = 8, decayMs = 35, sustainLevel = 0.75f, releaseMs = 70),
            TimbreRef.FM_BELL_ZUN1
        )
    }

    private fun buildBallsChorusHarmony(vol: Float): Lane {
        val phrase2bar = mutableListOf<Pair<Float, Int>>()
        phrase2bar.add(Eb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Gb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Eb4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Gb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Eb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D4 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D4 to BA_LLS_EIGHTH_MS)
        val notes = phrase2bar + phrase2bar + phrase2bar + phrase2bar +
                    phrase2bar + phrase2bar + phrase2bar
        val final = mutableListOf<Pair<Float, Int>>()
        final.add(Eb3 to BA_LLS_EIGHTH_MS)
        final.add(Gb3 to BA_LLS_EIGHTH_MS)
        final.add(Bb3 to BA_LLS_EIGHTH_MS)
        final.add(Eb4 to BA_LLS_EIGHTH_MS)
        final.add(G3 to BA_LLS_QUARTER_MS * 2)
        return Lane(
            buildChannel(notes + final, 0.4f * vol, "square",
                attackMs = 10, decayMs = 25, sustainLevel = 0.6f, releaseMs = 50),
            TimbreRef.SSG_HARMONY_SQUARE
        )
    }

    private fun buildBallsChorusBass(vol: Float): Lane {
        val phrase2bar = mutableListOf<Pair<Float, Int>>()
        phrase2bar.add(Eb2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Eb2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Eb2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Eb2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(Bb2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D3 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(G2 to BA_LLS_EIGHTH_MS)
        phrase2bar.add(D3 to BA_LLS_EIGHTH_MS)
        val notes = phrase2bar + phrase2bar + phrase2bar + phrase2bar +
                    phrase2bar + phrase2bar + phrase2bar
        val final = mutableListOf<Pair<Float, Int>>()
        final.add(Eb2 to BA_LLS_EIGHTH_MS)
        final.add(Bb2 to BA_LLS_EIGHTH_MS)
        final.add(Eb2 to BA_LLS_EIGHTH_MS)
        final.add(Bb2 to BA_LLS_EIGHTH_MS)
        final.add(G2 to BA_LLS_QUARTER_MS * 2)
        return Lane(
            buildChannel(notes + final, 0.55f * vol, "pulse12",
                attackMs = 5, decayMs = 15, sustainLevel = 0.85f, releaseMs = 30),
            TimbreRef.FM_BASS_ZUN1
        )
    }

    private fun offsetNotes(notes: List<ToneSpec>, offsetMs: Int): List<ToneSpec> {
        if (offsetMs == 0) return notes
        val result = mutableListOf<ToneSpec>()
        var i = 0
        while (i < notes.size) {
            val n = notes[i]
            result.add(
                ToneSpec(n.freq, n.startMs + offsetMs, n.durationMs, n.volume,
                    n.type, n.useADSR, n.attackMs, n.decayMs, n.sustainLevel, n.releaseMs)
            )
            i++
        }
        return result
    }

    private fun buildBadAppleLotusLandStory(vol: Float): ArrangementLanes {
        val introBars = 4
        val aBars = 16
        val bBars = 12
        val chorusBars = 15

        val introMs = introBars * BA_LLS_WHOLE_MS
        val aMs = aBars * BA_LLS_WHOLE_MS
        val bMs = bBars * BA_LLS_WHOLE_MS

        val introOffset = 0
        val aOffset = introMs
        val bOffset = introMs + aMs
        val chorusOffset = introMs + aMs + bMs

        val introLead = offsetNotes(buildBallsIntro(vol).notes, introOffset)
        val aLead = offsetNotes(buildBallsAMelodyLead(vol).notes, aOffset)
        val bLead = offsetNotes(buildBallsBMelodyLead(vol).notes, bOffset)
        val chorusLead = offsetNotes(buildBallsChorusLead(vol).notes, chorusOffset)

        val introHarmony = offsetNotes(emptyList<ToneSpec>(), introOffset)
        val aHarmony = offsetNotes(buildBallsAMelodyHarmony(vol).notes, aOffset)
        val bHarmony = offsetNotes(buildBallsBMelodyHarmony(vol).notes, bOffset)
        val chorusHarmony = offsetNotes(buildBallsChorusHarmony(vol).notes, chorusOffset)

        val introBass = offsetNotes(buildBallsIntroBass(vol).notes, introOffset)
        val aBass = offsetNotes(buildBallsAMelodyBass(vol).notes, aOffset)
        val bBass = offsetNotes(buildBallsBMelodyBass(vol).notes, bOffset)
        val chorusBass = offsetNotes(buildBallsChorusBass(vol).notes, chorusOffset)

        return ArrangementLanes(
            lead = Lane(introLead + aLead + bLead + chorusLead, TimbreRef.FM_LEAD_ZUN1),
            harmony = Lane(introHarmony + aHarmony + bHarmony + chorusHarmony, TimbreRef.SSG_HARMONY_SQUARE),
            bass = Lane(introBass + aBass + bBass + chorusBass, TimbreRef.FM_BASS_ZUN1),
            percussion = Lane(buildBallsPercussion(vol), TimbreRef.DRUM_HAT),
            tempoBpm = BA_LLS_BPM,
            keyRootMidi = 67
        )
    }
}
