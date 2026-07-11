package com.example.timeboxvibe.engine.core

/**
 * Display-only helpers for nested session/macro remaining time.
 *
 * Sequence modes (including Classic Pomodoro) keep only the current stage in
 * the live engine micro timer. The 魔法陣 still needs a session-limit / macro
 * readout for the full set (e.g. 25+5+25+5 = 60m). That total is derived here
 * from the preset sequence — not by changing TimerEngine.
 */
object SessionMacroDisplay {

    /** Full sequence length in seconds (positive stages only). */
    fun sequenceTotalSeconds(sequence: IntArray): Int {
        var total = 0
        var i = 0
        while (i < sequence.size) {
            val d = sequence[i]
            if (d > 0) total += d
            i++
        }
        return total
    }

    /**
     * Remaining session seconds: current stage remainder + all later stages.
     */
    fun sequenceRemainingSeconds(
        sequence: IntArray,
        currentIndex: Int,
        timeRemaining: Int
    ): Int {
        var total = timeRemaining.coerceAtLeast(0)
        var i = currentIndex + 1
        while (i < sequence.size) {
            val d = sequence[i]
            if (d > 0) total += d
            i++
        }
        return total
    }

    /**
     * When [mode] is sequence and the preset is known, returns display macro
     * (remaining, total). Otherwise returns the provided engine/UI values.
     */
    fun resolveMacro(
        mode: String,
        sequence: IntArray,
        currentIndex: Int,
        timeRemaining: Int,
        engineBigRemaining: Int,
        engineBigTotal: Int
    ): Pair<Int, Int> {
        if (mode != "sequence" || sequence.isEmpty()) {
            return engineBigRemaining.coerceAtLeast(0) to engineBigTotal.coerceAtLeast(0)
        }
        val total = sequenceTotalSeconds(sequence)
        if (total <= 0) {
            return engineBigRemaining.coerceAtLeast(0) to engineBigTotal.coerceAtLeast(0)
        }
        val remaining = sequenceRemainingSeconds(sequence, currentIndex, timeRemaining)
        return remaining to total
    }
}
