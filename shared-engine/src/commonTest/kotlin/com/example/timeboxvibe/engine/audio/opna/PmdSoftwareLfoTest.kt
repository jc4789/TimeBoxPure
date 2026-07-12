package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PmdSoftwareLfoTest {
    @Test
    fun th04OneShotModeUsesFixedClockAndHoldsAfterItsRepetitions() {
        val lfo = PmdSoftwareLfo(48_000)
        lfo.configure(0, 2, 1, 30)
        lfo.setWaveform(6)
        lfo.setClockMode(PmdPerformanceLaws.LFO_CLOCK_FIXED)
        lfo.setSwitch(6)

        advance(lfo, 48_000)
        assertEquals(28, lfo.valueSnapshot())
        advance(lfo, 48_000)
        assertEquals(30, lfo.volumeValue())
        advance(lfo, 48_000)
        assertEquals(30, lfo.valueSnapshot())
    }

    @Test
    fun twoPreallocatedStatesEvolveIndependently() {
        val first = PmdSoftwareLfo(48_000)
        val second = PmdSoftwareLfo(48_000)
        first.configure(0, 1, 2, 4)
        second.configure(0, 4, -3, 5)
        first.setWaveform(6)
        second.setWaveform(6)
        first.setSwitch(1)
        second.setSwitch(2)
        advance(first, 4_000)
        advance(second, 4_000)
        assertNotEquals(first.valueSnapshot(), second.valueSnapshot())
        assertTrue(first.pitchValue() != 0)
        assertTrue(second.volumeValue() != 0)
    }

    @Test
    fun keyOnSyncRestartsWhileFreeRunKeepsItsPosition() {
        val sync = PmdSoftwareLfo(48_000)
        sync.configure(0, 1, 1, 12)
        sync.setWaveform(6)
        sync.setSwitch(2)
        advance(sync, 4_000)
        assertTrue(sync.valueSnapshot() > 0)
        sync.noteOn()
        assertEquals(0, sync.valueSnapshot())

        val free = PmdSoftwareLfo(48_000)
        free.configure(0, 1, 1, 12)
        free.setWaveform(6)
        free.setSwitch(6)
        advance(free, 4_000)
        val before = free.valueSnapshot()
        free.noteOn()
        assertEquals(before, free.valueSnapshot())
    }

    @Test
    fun randomWaveHasAnExplicitRepeatableResetSeed() {
        val lfo = PmdSoftwareLfo(48_000)
        lfo.configure(0, 1, 3, 12)
        lfo.setWaveform(3)
        lfo.setSwitch(1)
        advance(lfo, 2_000)
        val firstValue = lfo.valueSnapshot()
        val firstSeed = lfo.randomSnapshot()
        lfo.configure(0, 1, 3, 12)
        advance(lfo, 2_000)
        assertEquals(firstValue, lfo.valueSnapshot())
        assertEquals(firstSeed, lfo.randomSnapshot())
    }

    @Test
    fun normalClockTracksTempoWhileFixedClockDoesNot() {
        val slow = PmdSoftwareLfo(48_000)
        val fast = PmdSoftwareLfo(48_000)
        slow.configure(0, 1, 1, 255)
        fast.configure(0, 1, 1, 255)
        slow.setWaveform(6)
        fast.setWaveform(6)
        slow.setSwitch(1)
        fast.setSwitch(1)
        slow.setTempo(60_000, 24)
        fast.setTempo(120_000, 24)
        advance(slow, 48_000)
        advance(fast, 48_000)
        assertEquals(24, slow.valueSnapshot())
        assertEquals(48, fast.valueSnapshot())
    }

    @Test
    fun delayAndDepthEvolutionUseDocumentedLfoClocksAndCycles() {
        val delayed = PmdSoftwareLfo(48_000)
        delayed.configure(10, 1, 2, 8)
        delayed.setWaveform(6)
        delayed.setClockMode(PmdPerformanceLaws.LFO_CLOCK_FIXED)
        delayed.setSwitch(1)
        advance(delayed, 8_000)
        assertEquals(0, delayed.valueSnapshot())
        advance(delayed, 8_000)
        assertTrue(delayed.valueSnapshot() > 0)

        val evolving = PmdSoftwareLfo(48_000)
        evolving.configure(0, 1, 1, 1)
        evolving.setWaveform(4)
        evolving.setDepthEvolution(2, 1, 3)
        evolving.setClockMode(PmdPerformanceLaws.LFO_CLOCK_FIXED)
        evolving.setSwitch(1)
        advance(evolving, 48_000)
        assertEquals(4, evolving.depthSnapshot())
    }

    @Test
    fun squareSpeed255AndAllSevenWaveSelectorsAreDeterministic() {
        val square = PmdSoftwareLfo(48_000)
        square.configure(0, 255, 2, 3)
        square.setWaveform(2)
        square.setClockMode(PmdPerformanceLaws.LFO_CLOCK_FIXED)
        square.setSwitch(1)
        advance(square, 48_000 * 5)
        assertEquals(6, square.valueSnapshot())

        var waveform = 0
        while (waveform <= 6) {
            val first = PmdSoftwareLfo(48_000)
            val second = PmdSoftwareLfo(48_000)
            first.configure(0, 1, 2, 4)
            second.configure(0, 1, 2, 4)
            first.setWaveform(waveform)
            second.setWaveform(waveform)
            first.setSwitch(1)
            second.setSwitch(1)
            advance(first, 12_345)
            advance(second, 12_345)
            assertEquals(first.valueSnapshot(), second.valueSnapshot(), "waveform=$waveform")
            waveform++
        }
    }

    private fun advance(lfo: PmdSoftwareLfo, samples: Int) {
        var i = 0
        while (i < samples) {
            lfo.advanceSample()
            i++
        }
    }
}
