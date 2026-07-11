package com.example.timeboxvibe.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionMacroDisplayTest {

    @Test
    fun classicPomodoroFullSetIsSixtyMinutes() {
        val seq = intArrayOf(1500, 300, 1500, 300)
        assertEquals(3600, SessionMacroDisplay.sequenceTotalSeconds(seq))
        assertEquals(3600, SessionMacroDisplay.sequenceRemainingSeconds(seq, 0, 1500))
    }

    @Test
    fun remainingDropsAfterFirstStageAdvances() {
        val seq = intArrayOf(1500, 300, 1500, 300)
        // Mid stage-2 break with 200s left: 200 + 1500 + 300 = 2000
        assertEquals(2000, SessionMacroDisplay.sequenceRemainingSeconds(seq, 1, 200))
    }

    @Test
    fun resolveMacroFillsSequenceWhenEngineBigIsZero() {
        val seq = intArrayOf(1500, 300, 1500, 300)
        val (rem, tot) = SessionMacroDisplay.resolveMacro(
            mode = "sequence",
            sequence = seq,
            currentIndex = 0,
            timeRemaining = 1500,
            engineBigRemaining = 0,
            engineBigTotal = 0
        )
        assertEquals(3600, rem)
        assertEquals(3600, tot)
    }

    @Test
    fun resolveMacroLeavesDualEngineValuesAlone() {
        val (rem, tot) = SessionMacroDisplay.resolveMacro(
            mode = "dual",
            sequence = IntArray(0),
            currentIndex = 0,
            timeRemaining = 90,
            engineBigRemaining = 3500,
            engineBigTotal = 3600
        )
        assertEquals(3500, rem)
        assertEquals(3600, tot)
    }
}
