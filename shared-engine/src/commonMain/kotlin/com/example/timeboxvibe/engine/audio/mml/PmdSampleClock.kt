package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.OpnPitch
import com.example.timeboxvibe.engine.audio.opna.OpnaTimerBLaws

/**
 * Maps resolved PMD clocks to sample boundaries using the physical Timer B
 * period. One hardware overflow advances the PMD sequence by one clock.
 */
internal object PmdSampleClock {
    fun samplesAt(song: CompiledOpnaSong, targetClock: Long, sampleRate: Int): Long {
        require(targetClock >= 0L) { "Target PMD clock must not be negative" }
        require(sampleRate > 0) { "Sample rate must be positive" }
        var samples = 0L
        var remainder = 0L
        var previousClock = 0L
        var timerB = song.initialTimerB
        var changeIndex = 0
        while (changeIndex < song.timerBChangeCount &&
            song.timerBChangeClock[changeIndex] < targetClock
        ) {
            val changeClock = song.timerBChangeClock[changeIndex].coerceAtLeast(previousClock)
            val numerator = sampleNumerator(
                changeClock - previousClock,
                sampleRate,
                timerB,
                remainder
            )
            samples += numerator / OpnPitch.MASTER_CLOCK_HZ
            remainder = numerator % OpnPitch.MASTER_CLOCK_HZ
            previousClock = changeClock
            timerB = song.timerBValue[changeIndex]
            changeIndex++
        }
        val numerator = sampleNumerator(
            targetClock - previousClock,
            sampleRate,
            timerB,
            remainder
        )
        return samples + numerator / OpnPitch.MASTER_CLOCK_HZ
    }

    private fun sampleNumerator(
        clocks: Long,
        sampleRate: Int,
        timerB: Int,
        remainder: Long
    ): Long {
        return clocks * sampleRate.toLong() * OpnaTimerBLaws.periodMasterClocks(timerB) + remainder
    }
}
