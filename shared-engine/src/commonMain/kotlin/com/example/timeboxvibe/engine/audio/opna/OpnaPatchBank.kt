package com.example.timeboxvibe.engine.audio.opna

/** Curated, named instruments for authored OPNA music. */
object OpnaPatchBank : SourceInstrumentLookup {
    const val FM_AT54 = 0
    const val FM_AT74 = 1
    const val FM_AT99 = 2
    const val FM_AT181 = 3
    const val FM_BRASS = 9
    const val FM_PIANO = 10
    const val FM_STRINGS = 11
    const val FM_EFFECT = 12

    const val SSG_SQUARE = 32
    const val SSG_LEAD = 33
    const val SSG_BASS = 34
    const val SSG_NOISE = 35
    const val SSG_ENVELOPE = 36
    const val SSG_LLS_SQUARE = 37
    const val SSG_NOISE_SLOW = 38
    const val SSG_ENVELOPE_ALT = 39

    val Pc98Brass = FmPatch(
        algorithm = 4, feedback = 4,
        op0 = OperatorSpec(1, 0, 30, ar = 27, dr = 11, sr = 2, sl = 4, rr = 9, ams = 1),
        op1 = OperatorSpec(1, 1, 10, ar = 25, dr = 9, sr = 1, sl = 5, rr = 8),
        op2 = OperatorSpec(2, 0, 38, ar = 26, dr = 12, sr = 3, sl = 5, rr = 9, ams = 1),
        op3 = OperatorSpec(1, 0, 13, ar = 24, dr = 8, sr = 1, sl = 6, rr = 8),
        totalLevel = 0.48f, pms = 2, ams = 1
    )

    val Pc98Piano = FmPatch(
        algorithm = 5, feedback = 2,
        op0 = OperatorSpec(1, 0, 34, ar = 31, dr = 19, sr = 10, sl = 3, rr = 10),
        op1 = OperatorSpec(1, 0, 10, ar = 31, dr = 16, sr = 8, sl = 4, rr = 9),
        op2 = OperatorSpec(2, 1, 26, ar = 31, dr = 20, sr = 12, sl = 2, rr = 10),
        op3 = OperatorSpec(1, 0, 15, ar = 31, dr = 15, sr = 7, sl = 5, rr = 9),
        totalLevel = 0.44f, pms = 1
    )

    val Pc98Strings = FmPatch(
        algorithm = 7, feedback = 1,
        op0 = OperatorSpec(1, 1, 20, ar = 16, dr = 7, sr = 1, sl = 5, rr = 7, ams = 1),
        op1 = OperatorSpec(1, 5, 24, ar = 15, dr = 6, sr = 1, sl = 6, rr = 7, ams = 1),
        op2 = OperatorSpec(2, 0, 45, ar = 17, dr = 8, sr = 2, sl = 5, rr = 8),
        op3 = OperatorSpec(1, 0, 18, ar = 14, dr = 5, sr = 1, sl = 7, rr = 6, ams = 1),
        totalLevel = 0.38f, pms = 3, ams = 1
    )

    val Pc98Effect = FmPatch(
        algorithm = 7, feedback = 6,
        op0 = OperatorSpec(1, 0, 18, ar = 31, dr = 18, sr = 16, sl = 2, rr = 12, ssgEg = 10),
        op1 = OperatorSpec(2, 1, 24, ar = 30, dr = 17, sr = 15, sl = 3, rr = 12, ssgEg = 12),
        op2 = OperatorSpec(3, 0, 30, ar = 29, dr = 16, sr = 14, sl = 4, rr = 11),
        op3 = OperatorSpec(4, 5, 34, ar = 28, dr = 15, sr = 13, sl = 5, rr = 10),
        totalLevel = 0.32f, pms = 4, ams = 1
    )

    private val Square = SsgPatch(fixedLevel = 12)
    private val Lead = SsgPatch(fixedLevel = 13)
    private val Bass = SsgPatch(fixedLevel = 12)
    private val Noise = SsgPatch(toneEnabled = false, noiseEnabled = true, fixedLevel = 10, noisePeriod = 8)
    private val Envelope = SsgPatch(fixedLevel = 15, envelopeEnabled = true, envelopeShape = 10, envelopePeriod = 1536)
    private val LlsSquare = SsgPatch(fixedLevel = 12)
    private val NoiseSlow = SsgPatch(toneEnabled = false, noiseEnabled = true, fixedLevel = 10, noisePeriod = 16)
    private val EnvelopeAlt = SsgPatch(fixedLevel = 15, envelopeEnabled = true, envelopeShape = 12, envelopePeriod = 1024)

    override fun sourceIdForName(name: String): Int = when (name.lowercase()) {
        "54" -> FM_AT54
        "74" -> FM_AT74
        "99" -> FM_AT99
        "181" -> FM_AT181
        "brass" -> FM_BRASS
        "piano" -> FM_PIANO
        "strings" -> FM_STRINGS
        "effect" -> FM_EFFECT
        "square" -> SSG_SQUARE
        "ssg_lead" -> SSG_LEAD
        "ssg_bass" -> SSG_BASS
        "ssg_noise" -> SSG_NOISE
        "ssg_envelope" -> SSG_ENVELOPE
        "lls_square" -> SSG_LLS_SQUARE
        "ssg_noise_slow" -> SSG_NOISE_SLOW
        "ssg_envelope_alt" -> SSG_ENVELOPE_ALT
        else -> -1
    }

    fun idForName(name: String): Int = sourceIdForName(name)

    fun isFm(id: Int): Boolean = id in FM_AT54..FM_AT181 || id in FM_BRASS..FM_EFFECT
    fun isSsg(id: Int): Boolean = id in SSG_SQUARE..SSG_ENVELOPE_ALT

    override fun fmPatch(sourceId: Int): FmPatch? = when (sourceId) {
        FM_AT54 -> LlsPatches.At54
        FM_AT74 -> LlsPatches.At74
        FM_AT99 -> LlsPatches.At99
        FM_AT181 -> LlsPatches.At181
        FM_BRASS -> Pc98Brass
        FM_PIANO -> Pc98Piano
        FM_STRINGS -> Pc98Strings
        FM_EFFECT -> Pc98Effect
        else -> null
    }

    override fun ssgPatch(sourceId: Int): SsgPatch? = when (sourceId) {
        SSG_SQUARE -> Square
        SSG_LEAD -> Lead
        SSG_BASS -> Bass
        SSG_NOISE -> Noise
        SSG_ENVELOPE -> Envelope
        SSG_LLS_SQUARE -> LlsSquare
        SSG_NOISE_SLOW -> NoiseSlow
        SSG_ENVELOPE_ALT -> EnvelopeAlt
        else -> null
    }
}
