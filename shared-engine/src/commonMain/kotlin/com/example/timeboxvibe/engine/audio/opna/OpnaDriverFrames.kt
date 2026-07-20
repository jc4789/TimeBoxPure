package com.example.timeboxvibe.engine.audio.opna

/** Resolved per-frame driver values consumed by one physical FM voice. */
internal class PmdModulationFrame {
    val pitch1Q20 = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val pitch2Q20 = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val volume1 = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val volume2 = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    var pitchTarget1 = false
    var pitchTarget2 = false
    var volumeTarget1 = false
    var volumeTarget2 = false
    var tlMask1 = 0
    var tlMask2 = 0
    var baseAttenuation = 0

    fun clear() {
        pitch1Q20.fill(0)
        pitch2Q20.fill(0)
        volume1.fill(0)
        volume2.fill(0)
        pitchTarget1 = false
        pitchTarget2 = false
        volumeTarget1 = false
        volumeTarget2 = false
        tlMask1 = 0
        tlMask2 = 0
        baseAttenuation = 0
    }
}

/** Resolved per-frame driver values consumed by one physical SSG voice. */
internal class PmdSsgFrame {
    val tonePeriodOffset = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val volumeOffset = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val softwareEnvelopeLevel = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val releaseFinished = BooleanArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)

    fun clear(baseLevel: Int) {
        tonePeriodOffset.fill(0)
        volumeOffset.fill(0)
        softwareEnvelopeLevel.fill(baseLevel.coerceIn(0, 15))
        releaseFinished.fill(false)
    }
}
