package com.example.timeboxvibe.engine.audio.opna

internal class PmdDetune private constructor(val raw: Int) {
    internal fun plusOrNull(other: PmdDetune): PmdDetune? {
        val sum = raw.toLong() + other.raw.toLong()
        return if (sum in MIN_RAW.toLong()..MAX_RAW.toLong()) PmdDetune(sum.toInt()) else null
    }

    companion object {
        const val MIN_RAW: Int = -32_768
        const val MAX_RAW: Int = 32_767
        val ZERO: PmdDetune = PmdDetune(0)

        fun fromRawOrNull(raw: Int): PmdDetune? =
            if (raw in MIN_RAW..MAX_RAW) PmdDetune(raw) else null
    }
}
