package com.example.timeboxvibe.engine.audio.mml

/** Part-local PMD Q/q state resolved during compilation into primitive ticks. */
internal class PmdGateState(channelOrdinal: Int, private val ticksPerClock: Int) {
    var proportionalValue: Int = 0
        private set
    var proportionalScale: Int = 8
        private set
    var tailFromClocks: Int = 0
        private set
    var tailToClocks: Int = 0
        private set
    var minimumClocks: Int = 0
        private set
    var lastResolvedTailClocks: Int = 0
        private set

    private var randomState: UInt = (
        PmdPerformanceLaws.GATE_RANDOM_SEED xor
            ((channelOrdinal + 1) * CHANNEL_SEED_STRIDE)
        ).toUInt()

    fun setProportional(value: Int, scale: Int) {
        proportionalValue = value
        proportionalScale = scale
    }

    fun updateTail(from: Int?, to: Int?, minimum: Int?) {
        if (from != null) {
            tailFromClocks = from
            tailToClocks = to ?: from
        }
        if (minimum != null) minimumClocks = minimum
    }

    fun resolve(durationTicks: Int, slur: Boolean): Int {
        if (slur) {
            lastResolvedTailClocks = 0
            return durationTicks
        }
        val proportionalTicks = if (proportionalValue == 0) {
            durationTicks
        } else {
            durationTicks * proportionalValue / proportionalScale
        }
        val low = minOf(tailFromClocks, tailToClocks)
        val high = maxOf(tailFromClocks, tailToClocks)
        val selectedTail = if (high > low) low + nextBounded(high - low + 1) else low
        lastResolvedTailClocks = selectedTail
        val minimumTicks = maxOf(1, minimumClocks) * ticksPerClock
        return (proportionalTicks - selectedTail * ticksPerClock)
            .coerceAtLeast(minimumTicks)
            .coerceAtMost(durationTicks)
    }

    private fun nextBounded(bound: Int): Int {
        var value = randomState
        value = value xor (value shl 13)
        value = value xor (value shr 17)
        value = value xor (value shl 5)
        randomState = value
        return (value % bound.toUInt()).toInt()
    }

    private companion object {
        const val CHANNEL_SEED_STRIDE = 0x1F123BB5
    }
}
