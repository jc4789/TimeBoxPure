package com.example.timeboxvibe.engine.audio.opna

/**
 * Allocation-free OPN envelope in the chip's 10-bit attenuation domain.
 * Rate cadence is generated procedurally; no emulator increment table is embedded.
 */
class OpnRateEnvelope {
    companion object {
        const val OFF: Int = 0
        const val ATTACK: Int = 1
        const val DECAY: Int = 2
        const val SUSTAIN: Int = 3
        const val RELEASE: Int = 4
        const val MAX_ATTENUATION: Int = 1023
        private const val MAX_EFFECTIVE_RATE = 63

        fun keyCode(block: Int, fnum: Int): Int = OpnPitch.keyCode(block, fnum)

        internal fun sustainAttenuation(level: Int): Int {
            val sl = level.coerceIn(0, 15)
            return if (sl == 15) 31 shl 5 else sl shl 5
        }

        internal fun keyScaleValue(keyCode: Int, keyScale: Int): Int =
            keyCode.coerceIn(0, 31) ushr (keyScale.coerceIn(0, 3) xor 3)

        internal fun effectiveRate(registerRate: Int, keyScaleValue: Int, release: Boolean): Int {
            val base = if (release) {
                registerRate.coerceIn(0, 15) * 4 + 2
            } else {
                val rate = registerRate.coerceIn(0, 31)
                if (rate == 0) return 0
                rate * 2
            }
            return (base + keyScaleValue).coerceIn(0, MAX_EFFECTIVE_RATE)
        }

        internal fun averageIncrementPerEgTick(effectiveRate: Int): Double {
            val rate = effectiveRate.coerceIn(0, MAX_EFFECTIVE_RATE)
            if (rate < 2) return 0.0
            if (rate >= 60) return 8.0
            val group = rate ushr 2
            val fraction = if (rate < 4) 0 else rate and 3
            return if (group < 12) {
                (4.0 + fraction.toDouble()) / 8.0 / (1 shl (11 - group)).toDouble()
            } else {
                (1 shl (group - 12)).toDouble() * (1.0 + fraction.toDouble() / 4.0)
            }
        }

        internal fun proceduralIncrement(effectiveRate: Int, counter: Long): Int {
            val rate = effectiveRate.coerceIn(0, MAX_EFFECTIVE_RATE)
            if (rate < 2) return 0
            if (rate >= 60) return 8

            val group = rate ushr 2
            val fraction = if (rate < 4) 0 else rate and 3
            if (group < 12) {
                val interval = 1L shl (11 - group)
                if ((counter and (interval - 1L)) != 0L) return 0
                val ordinal = ((counter / interval) - 1L).toInt() and 7
                return if (distributedPulse(ordinal, 4 + fraction)) 1 else 0
            }

            val base = 1 shl (group - 12)
            if (fraction == 0) return base
            val ordinal = (counter - 1L).toInt() and 7
            return base + if (distributedPulse(ordinal, fraction * 2)) base else 0
        }

        private fun distributedPulse(ordinal: Int, pulsesPerEight: Int): Boolean {
            return (ordinal + 1) * pulsesPerEight / 8 != ordinal * pulsesPerEight / 8
        }
    }

    var stage: Int = OFF
        private set
    var attenuation: Int = MAX_ATTENUATION
        private set
    val level: Float
        get() = (MAX_ATTENUATION - attenuation).toFloat() / MAX_ATTENUATION.toFloat()

    var attackRate: Int = 0
    var decayRate: Int = 0
    var sustainRate: Int = 0
    var sustainLevel: Int = 15
    var releaseRate: Int = 0
    var keyScale: Int = 0

    private var keyCodeValue: Int = 0
    private var egClockAccumulator: Long = 0L
    private var rateCounter: Long = 0L
    private var egClockDenominator: Long =
        48_000L * OpnPitch.FM_CLOCK_DIVIDER.toLong() * OpnPitch.EG_CLOCK_DIVIDER.toLong()

    fun setSampleRate(sampleRate: Int) {
        egClockDenominator = sampleRate.coerceAtLeast(1).toLong() *
            OpnPitch.FM_CLOCK_DIVIDER.toLong() * OpnPitch.EG_CLOCK_DIVIDER.toLong()
        if (egClockAccumulator >= egClockDenominator) egClockAccumulator %= egClockDenominator
    }

    fun setKeyScale(block: Int, fnum: Int) {
        keyCodeValue = keyCode(block, fnum)
    }

    fun noteOn(retrigger: Boolean = false) {
        if (!retrigger || stage == OFF) attenuation = MAX_ATTENUATION
        stage = ATTACK
        if (currentEffectiveRate() >= 62) finishAttack()
    }

    fun noteOff() {
        if (stage != OFF) stage = RELEASE
    }

    fun reset() {
        stage = OFF
        attenuation = MAX_ATTENUATION
        keyCodeValue = 0
        egClockAccumulator = 0L
        rateCounter = 0L
    }

    fun nextAttenuation(): Int {
        egClockAccumulator += OpnPitch.MASTER_CLOCK_HZ.toLong()
        while (egClockAccumulator >= egClockDenominator) {
            egClockAccumulator -= egClockDenominator
            clockEnvelope()
        }
        return attenuation
    }

    fun next(): Float {
        nextAttenuation()
        return level
    }

    private fun finishAttack() {
        attenuation = 0
        stage = if (sustainAttenuation(sustainLevel) == 0) SUSTAIN else DECAY
    }

    private fun clockEnvelope() {
        rateCounter++
        if (stage == OFF) return

        if (stage == DECAY && attenuation >= sustainAttenuation(sustainLevel)) stage = SUSTAIN

        val rate = currentEffectiveRate()
        if (stage == ATTACK && rate >= 62) {
            finishAttack()
            return
        }
        val increment = proceduralIncrement(rate, rateCounter)
        if (increment == 0) return

        when (stage) {
            ATTACK -> {
                attenuation += (attenuation.inv() * increment) shr 4
                if (attenuation <= 0) finishAttack()
            }
            DECAY -> {
                attenuation += increment
                val target = sustainAttenuation(sustainLevel)
                if (attenuation >= target) {
                    attenuation = target.coerceAtMost(MAX_ATTENUATION)
                    stage = SUSTAIN
                }
            }
            SUSTAIN, RELEASE -> {
                attenuation += increment
                if (attenuation >= MAX_ATTENUATION) {
                    attenuation = MAX_ATTENUATION
                    stage = OFF
                }
            }
        }
    }

    private fun currentEffectiveRate(): Int {
        val ksr = keyScaleValue(keyCodeValue, keyScale)
        return when (stage) {
            ATTACK -> effectiveRate(attackRate, ksr, release = false)
            DECAY -> effectiveRate(decayRate, ksr, release = false)
            SUSTAIN -> effectiveRate(sustainRate, ksr, release = false)
            RELEASE -> effectiveRate(releaseRate, ksr, release = true)
            else -> 0
        }
    }

}
