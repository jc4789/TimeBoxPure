package com.example.timeboxvibe.engine.audio.opna

/** Exact rational tick-to-sample mapping with carried division error. */
internal object PmdSampleClock {
    fun samplesAt(song: CompiledOpnaSong, targetTick: Long, sampleRate: Int): Long {
        require(targetTick >= 0L) { "Target tick must not be negative" }
        var samples = 0L
        var remainder = 0L
        var previousTick = 0L
        var bpmMilli = song.bpmMilli
        var tempoIndex = 0
        while (tempoIndex < song.tempoChangeCount && song.tempoTick(tempoIndex) < targetTick) {
            val changeTick = song.tempoTick(tempoIndex).coerceAtLeast(previousTick)
            val denominator = bpmMilli.toLong() * CompiledOpnaSong.TICKS_PER_QUARTER
            val numerator = (changeTick - previousTick) * sampleRate.toLong() * 60_000L + remainder
            samples += numerator / denominator
            remainder = numerator % denominator
            previousTick = changeTick
            val nextBpm = song.tempoBpmMilli(tempoIndex)
            remainder = rescaleRemainder(remainder, bpmMilli, nextBpm)
            bpmMilli = nextBpm
            tempoIndex++
        }
        val denominator = bpmMilli.toLong() * CompiledOpnaSong.TICKS_PER_QUARTER
        val numerator = (targetTick - previousTick) * sampleRate.toLong() * 60_000L + remainder
        return samples + numerator / denominator
    }

    private fun rescaleRemainder(remainder: Long, oldBpmMilli: Int, newBpmMilli: Int): Long {
        if (remainder == 0L || oldBpmMilli == newBpmMilli) return remainder
        val oldDenominator = oldBpmMilli.toLong() * CompiledOpnaSong.TICKS_PER_QUARTER
        val newDenominator = newBpmMilli.toLong() * CompiledOpnaSong.TICKS_PER_QUARTER
        return remainder * newDenominator / oldDenominator
    }
}
