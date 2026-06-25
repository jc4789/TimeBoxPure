package com.example.timeboxvibe.engine

/**
 * SoundMelodies — Chiptune melody data for alarm sounds.
 *
 * Contains faithful PC-98 style arrangements of:
 * - Bad Apple!! (東方幻想郷 Touhou 4: Lotus Land Story, Stage 3 BGM by ZUN)
 *   Key: Eb minor | Tempo: 138 BPM | Progression: Ebm–Cb–Db–Ebm (i–VI–VII–i)
 *
 * - 千本桜 Senbonzakura (黒うさP / Kurousa-P, Vocaloid)
 *   Key: D minor  | Tempo: 154 BPM | Progression: Dm–Bb–C–F (i–VI–VII–III)
 *
 * Each arrangement uses 4 channels with ADSR envelopes:
 *   CH1 Lead (pulse25) | CH2 Harmony (square) | CH3 Bass (triangle) | CH4 Percussion (noise-metallic)
 */
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
                // All 4 channels returned from isBass=false; isBass=true returns empty
                if (isBass) emptyList() else getBadAppleArrangement(volume)
            }
            "synth-senbonzakura" -> {
                if (isBass) emptyList() else getSenbonzakuraArrangement(volume)
            }
            else -> emptyList()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  NOTE FREQUENCY CONSTANTS (Hz, A4 = 440 Hz standard tuning)
    // ════════════════════════════════════════════════════════════════════

    // Octave 2
    private const val D2  = 73.42f
    private const val Eb2 = 77.78f
    private const val F2  = 87.31f
    private const val Bb2 = 116.54f
    private const val B2  = 123.47f   // enharmonic Cb3

    // Octave 3
    private const val C3  = 130.81f
    private const val Db3 = 138.59f
    private const val D3  = 146.83f
    private const val Eb3 = 155.56f
    private const val F3  = 174.61f
    private const val Gb3 = 185.00f
    private const val Ab3 = 207.65f
    private const val A3  = 220.00f
    private const val Bb3 = 233.08f
    private const val B3  = 246.94f   // enharmonic Cb4

    // Octave 4
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
    private const val B4  = 493.88f   // enharmonic Cb5

    // Octave 5
    private const val C5  = 523.25f
    private const val Db5 = 554.37f
    private const val D5  = 587.33f
    private const val Eb5 = 622.25f

    // ════════════════════════════════════════════════════════════════════
    //  HELPER: Build a channel from a list of (frequency, duration) pairs
    // ════════════════════════════════════════════════════════════════════

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

    /** Repeat a bar pattern [repeats] times. */
    private fun repeatBar(bar: List<Pair<Float, Int>>, repeats: Int): List<Pair<Float, Int>> {
        val result = mutableListOf<Pair<Float, Int>>()
        repeat(repeats) { result.addAll(bar) }
        return result
    }

    // ════════════════════════════════════════════════════════════════════
    //  BAD APPLE!! — 東方幻想郷 Touhou 4: Lotus Land Story, Stage 3 BGM
    //  Key: Eb minor | Tempo: 138 BPM | 16 bars
    //  Chord cycle: Ebm (1 bar) – Cb (1 bar) – Db (1 bar) – Ebm (1 bar)
    // ════════════════════════════════════════════════════════════════════

    private fun getBadAppleArrangement(vol: Float): List<ToneSpec> {
        val e = 217  // eighth note at 138 BPM
        val q = 434  // quarter note
        val s = 109  // sixteenth note

        val lead = buildBadAppleLead(vol, e, q, s)
        val harmony = buildBadAppleHarmony(vol, e, q)
        val bass = buildBadAppleBass(vol, e)
        val perc = buildBadApplePercussion(vol, e)

        return lead + harmony + bass + perc
    }

    /**
     * CH1 — Lead melody (pulse25 wave)
     * The iconic Bad Apple!! theme: descending-ascending Eb minor scale motif
     * Scale degrees: 5-5-♭6-5-4-♭3-2-1 / 1-2-♭3-4-5-4-♭3-2
     */
    private fun buildBadAppleLead(vol: Float, e: Int, q: Int, s: Int): List<ToneSpec> {
        val notes = listOf(
            // ── Bars 1–4: Main theme statement ──────────────────────
            // Bar 1 (Ebm): Descending from dominant
            Bb4 to e, Bb4 to e, B4 to e, Bb4 to e,
            Ab4 to e, Gb4 to e, F4 to e, Eb4 to e,
            // Bar 2 (Cb): Ascending response
            Eb4 to e, F4 to e, Gb4 to e, Ab4 to e,
            Bb4 to e, Ab4 to e, Gb4 to e, F4 to e,
            // Bar 3 (Db): Theme transposed down a third
            Gb4 to e, Gb4 to e, Ab4 to e, Gb4 to e,
            F4 to e, Eb4 to e, Db4 to e, Eb4 to e,
            // Bar 4 (Ebm): Ascending resolution
            F4 to e, Gb4 to e, Ab4 to e, Gb4 to e,
            F4 to e, Eb4 to e, Db4 to e, Eb4 to e,

            // ── Bars 5–8: Development (higher register) ─────────────
            // Bar 5 (Ebm): Climbing sequence
            Bb4 to e, B4 to e, Db5 to e, Eb5 to e,
            Db5 to e, B4 to e, Bb4 to e, Ab4 to e,
            // Bar 6 (Cb): Continuation
            Ab4 to e, Bb4 to e, B4 to e, Db5 to e,
            Eb5 to e, Db5 to e, B4 to e, Bb4 to e,
            // Bar 7 (Db): Descent begins
            Gb4 to e, Ab4 to e, Bb4 to e, B4 to e,
            Bb4 to e, Ab4 to e, Gb4 to e, F4 to e,
            // Bar 8 (Ebm): Return to tonic region
            Eb4 to e, F4 to e, Gb4 to e, Ab4 to e,
            Gb4 to e, F4 to e, Eb4 to e, Db4 to e,

            // ── Bars 9–12: Variation with embellishments ────────────
            // Bar 9 (Ebm): Ascending scalar run
            Eb4 to e, Eb4 to e, F4 to e, Gb4 to e,
            Ab4 to e, Bb4 to e, B4 to e, Db5 to e,
            // Bar 10 (Cb): Mirror descending run
            Db5 to e, B4 to e, Bb4 to e, Ab4 to e,
            Gb4 to e, F4 to e, Eb4 to e, Db4 to e,
            // Bar 11 (Db): Arpeggiated approach
            Eb4 to e, Gb4 to e, Bb4 to e, Eb5 to e,
            Db5 to e, B4 to e, Bb4 to e, Ab4 to e,
            // Bar 12 (Ebm): Turnaround figure
            Gb4 to e, F4 to e, Eb4 to e, Db4 to e,
            Eb4 to e, F4 to e, Gb4 to e, Ab4 to e,

            // ── Bars 13–16: Recap (loops back to bar 1) ─────────────
            // Bar 13 = Bar 1
            Bb4 to e, Bb4 to e, B4 to e, Bb4 to e,
            Ab4 to e, Gb4 to e, F4 to e, Eb4 to e,
            // Bar 14 = Bar 2
            Eb4 to e, F4 to e, Gb4 to e, Ab4 to e,
            Bb4 to e, Ab4 to e, Gb4 to e, F4 to e,
            // Bar 15 = Bar 3
            Gb4 to e, Gb4 to e, Ab4 to e, Gb4 to e,
            F4 to e, Eb4 to e, Db4 to e, Eb4 to e,
            // Bar 16 = Bar 4
            F4 to e, Gb4 to e, Ab4 to e, Gb4 to e,
            F4 to e, Eb4 to e, Db4 to e, Eb4 to e
        )
        return buildChannel(notes, 0.18f * vol, "pulse25",
            attackMs = 10, decayMs = 50, sustainLevel = 0.7f, releaseMs = 80)
    }

    /**
     * CH2 — Harmony / counter-melody (square wave)
     * Arpeggiated chord tones following the Ebm–Cb–Db–Ebm progression
     */
    private fun buildBadAppleHarmony(vol: Float, e: Int, q: Int): List<ToneSpec> {
        // One bar of arpeggiated chords per chord in the progression
        val ebmArp = listOf(Eb3 to q, Gb3 to q, Bb3 to q, Gb3 to q)  // Ebm arpeggio
        val cbArp  = listOf(B3 to q, Eb4 to q, Gb4 to q, Eb4 to q)   // Cb (= B) major arpeggio
        val dbArp  = listOf(Db4 to q, F4 to q, Ab4 to q, F4 to q)    // Db major arpeggio
        val ebmArp2 = listOf(Eb3 to q, Bb3 to q, Gb3 to q, Bb3 to q) // Ebm variation

        // 4 cycles × 4 bars = 16 bars
        val notes = repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
                    repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
                    repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
                    repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
                    repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
                    repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1) +
                    repeatBar(ebmArp, 1) + repeatBar(cbArp, 1) +
                    repeatBar(dbArp, 1) + repeatBar(ebmArp2, 1)

        return buildChannel(notes, 0.10f * vol, "square",
            attackMs = 15, decayMs = 30, sustainLevel = 0.5f, releaseMs = 60)
    }

    /**
     * CH3 — Bass line (triangle wave)
     * Driving eighth-note root motion: Eb–Cb–Db–Eb
     */
    private fun buildBadAppleBass(vol: Float, e: Int): List<ToneSpec> {
        val ebmBass = listOf(Eb2 to e, Eb2 to e, Eb2 to e, Eb2 to e,
                             Eb2 to e, Eb3 to e, Eb2 to e, Eb3 to e)
        val cbBass  = listOf(B2 to e, B2 to e, B2 to e, B2 to e,
                             B2 to e, B3 to e, B2 to e, B3 to e)
        val dbBass  = listOf(Db3 to e, Db3 to e, Db3 to e, Db3 to e,
                             Db3 to e, Db4 to e, Db3 to e, Db4 to e)
        val ebmBass2 = listOf(Eb2 to e, Eb3 to e, Eb2 to e, Eb2 to e,
                              Eb2 to e, Eb2 to e, Eb3 to e, Eb2 to e)

        val oneCycle = ebmBass + cbBass + dbBass + ebmBass2
        val notes = repeatBar(oneCycle, 4) // 4 cycles = 16 bars

        return buildChannel(notes, 0.20f * vol, "triangle",
            attackMs = 5, decayMs = 20, sustainLevel = 0.8f, releaseMs = 30)
    }

    /**
     * CH4 — Percussion (noise-metallic)
     * Hi-hat on every 8th note, accented on beats 2 and 4 (backbeat)
     */
    private fun buildBadApplePercussion(vol: Float, e: Int): List<ToneSpec> {
        // 8 hits per bar: hi-hat with accent on positions 2,6 (beats 2,4)
        val hiHat = 8000f    // bright metallic noise
        val snare = 3000f    // darker noise for accents

        val oneBar = listOf(
            hiHat to e, hiHat to e, snare to e, hiHat to e,
            hiHat to e, hiHat to e, snare to e, hiHat to e
        )
        val notes = repeatBar(oneBar, 16) // 16 bars

        return buildChannel(notes, 0.06f * vol, "noise-metallic",
            attackMs = 2, decayMs = 0, sustainLevel = 0.0f, releaseMs = 15)
    }

    // ════════════════════════════════════════════════════════════════════
    //  千本桜 SENBONZAKURA — 黒うさP / Kurousa-P (Vocaloid)
    //  Key: D minor | Tempo: 154 BPM | 16 bars (chorus section)
    //  Chord cycle: Dm (1 bar) – Bb (1 bar) – C (1 bar) – F (1 bar)
    // ════════════════════════════════════════════════════════════════════

    private fun getSenbonzakuraArrangement(vol: Float): List<ToneSpec> {
        val e = 195  // eighth note at 154 BPM
        val q = 390  // quarter note
        val s = 97   // sixteenth note

        val lead = buildSenbonzakuraLead(vol, e, q, s)
        val harmony = buildSenbonzakuraHarmony(vol, e, q)
        val bass = buildSenbonzakuraBass(vol, e)
        val perc = buildSenbonzakuraPercussion(vol, e)

        return lead + harmony + bass + perc
    }

    /**
     * CH1 — Chorus vocal melody (pulse25 wave)
     * 「千本桜 夜に紛れ 君の声も届かないよ」
     * Characteristic fast syllabic delivery with pentatonic influence
     */
    private fun buildSenbonzakuraLead(vol: Float, e: Int, q: Int, s: Int): List<ToneSpec> {
        val notes = listOf(
            // ── Bars 1–4: First phrase of chorus ────────────────────
            // Bar 1 (Bb to C): 「千本桜」
            D4 to e, F4 to e, G4 to e, A4 to e,
            D5 to e, C5 to e, Bb4 to e, A4 to e,
            // Bar 2 (Dm): 「夜に紛れ」
            G4 to e, F4 to e, G4 to e, A4 to q,
            A4 to q, 0f to e,
            // Bar 3 (Bb to C): 「君の声も」
            D4 to e, F4 to e, G4 to e, A4 to e,
            D5 to e, C5 to e, Bb4 to e, A4 to e,
            // Bar 4 (Dm): 「届かないよ」
            G4 to e, F4 to e, E4 to e, D4 to q,
            E4 to e, D4 to q,

            // ── Bars 5–8: Second phrase ─────────────────────────────
            // Bar 5 (Bb to C): 「此処は宴」
            D4 to e, F4 to e, G4 to e, A4 to e,
            D5 to e, C5 to e, Bb4 to e, A4 to e,
            // Bar 6 (F to Dm): 「鋼の檻」
            G4 to e, F4 to e, G4 to e, A4 to q,
            A4 to q, 0f to e,
            // Bar 7 (Bb to C): 「その断頭台で」
            D4 to e, F4 to e, G4 to e, A4 to e,
            D5 to e, C5 to e, Bb4 to e, A4 to e,
            // Bar 8 (Dm): 「见下ろして」
            G4 to e, F4 to e, E4 to e, D4 to q,
            D4 to q, 0f to e,

            // ── Bars 9–12: Third phrase (climactic) ─────────────────
            // Bar 9 (Bb to C): 「三千世界」
            D5 to e, D5 to e, C5 to e, Bb4 to e,
            A4 to e, G4 to e, A4 to e, Bb4 to e,
            // Bar 10 (Dm): 「常世の闇」
            C5 to e, C5 to e, Bb4 to e, A4 to e,
            G4 to e, F4 to e, G4 to e, A4 to e,
            // Bar 11 (Bb to C): 「歌も聞こえないよ」
            Bb4 to e, A4 to e, G4 to e, F4 to e,
            E4 to e, D4 to q, D4 to e,
            // Bar 12 (Dm): Instrumental synth fill (climbing run)
            D4 to q, E4 to q, F4 to q, A4 to q,

            // ── Bars 13–16: Final phrase (loop preparation) ─────────
            // Bar 13 (Bb to C): 「青嵐の空」
            D5 to e, D5 to e, C5 to e, Bb4 to e,
            A4 to e, G4 to e, A4 to e, Bb4 to e,
            // Bar 14 (F to Dm): 「遥か彼方」
            C5 to e, C5 to e, Bb4 to e, A4 to e,
            G4 to e, F4 to e, E4 to e, D4 to e,
            // Bar 15 (Bb to C): 「その光線銃で」
            Bb4 to e, Bb4 to e, C5 to e, D5 to e,
            C5 to e, Bb4 to e, A4 to e, G4 to e,
            // Bar 16 (Dm): 「打ち抜け」
            A4 to e, G4 to e, F4 to e, E4 to e,
            D4 to q, D4 to q
        )
        return buildChannel(notes, 0.18f * vol, "pulse25",
            attackMs = 8, decayMs = 40, sustainLevel = 0.75f, releaseMs = 60)
    }

    /**
     * CH2 — Chord harmony (square wave)
     * Arpeggiated Dm–Bb–C–F progression, fast rhythmic stabs
     */
    private fun buildSenbonzakuraHarmony(vol: Float, e: Int, q: Int): List<ToneSpec> {
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

        return buildChannel(notes, 0.10f * vol, "square",
            attackMs = 5, decayMs = 20, sustainLevel = 0.6f, releaseMs = 40)
    }

    /**
     * CH3 — Bass line (triangle wave)
     * Driving eighth-note root pulses: D–Bb–C–F with octave jumps
     */
    private fun buildSenbonzakuraBass(vol: Float, e: Int): List<ToneSpec> {
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

        return buildChannel(notes, 0.20f * vol, "triangle",
            attackMs = 5, decayMs = 15, sustainLevel = 0.85f, releaseMs = 25)
    }

    /**
     * CH4 — Percussion (noise-metallic)
     * Fast driving hi-hat with snare accents — characteristic Senbonzakura energy
     */
    private fun buildSenbonzakuraPercussion(vol: Float, e: Int): List<ToneSpec> {
        val hiHat = 9000f   // bright metallic hi-hat
        val snare = 3500f   // darker accent

        val oneBar = listOf(
            hiHat to e, hiHat to e, snare to e, hiHat to e,
            hiHat to e, hiHat to e, snare to e, hiHat to e
        )
        val notes = repeatBar(oneBar, 16) // 16 bars

        return buildChannel(notes, 0.06f * vol, "noise-metallic",
            attackMs = 2, decayMs = 0, sustainLevel = 0.0f, releaseMs = 12)
    }
}
