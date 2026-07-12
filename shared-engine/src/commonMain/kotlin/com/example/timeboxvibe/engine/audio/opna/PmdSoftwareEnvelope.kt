package com.example.timeboxvibe.engine.audio.opna

/** Allocation-free PMD legacy/extended SSG software-envelope state. */
internal class PmdSoftwareEnvelope(private var sampleRate: Int) {
    private var format = PmdPerformanceLaws.ENVELOPE_DISABLED
    private var clockMode = PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL
    private var tempoMilliBpm = 120 * PmdPerformanceLaws.BPM_MILLI_SCALE
    private var clocksPerQuarter = PmdPerformanceLaws.DEFAULT_CLOCKS_PER_QUARTER
    private var clockPhase = 0u
    private var clockStep = 0u

    private var attack = 0
    private var decay = 0
    private var sustain = 0
    private var release = 0
    private var sustainLevel = 0
    private var attackLevel = 0

    private var stage = STAGE_OFF
    private var counter = 0
    private var legacyOffset = 0
    private var extendedLevel = 0
    private var releaseFinished = false

    val enabled: Boolean
        get() = format != PmdPerformanceLaws.ENVELOPE_DISABLED

    fun configure(
        selectedFormat: Int,
        attackValue: Int,
        decayValue: Int,
        sustainValue: Int,
        releaseValue: Int,
        selectedSustainLevel: Int,
        selectedAttackLevel: Int
    ) {
        format = selectedFormat
        attack = attackValue
        decay = decayValue
        sustain = sustainValue
        release = releaseValue
        sustainLevel = selectedSustainLevel
        attackLevel = selectedAttackLevel
    }

    fun setClockMode(mode: Int) {
        clockMode = mode
        updateClockStep()
    }

    fun setTempo(milliBpm: Int, sourceClocksPerQuarter: Int) {
        tempoMilliBpm = milliBpm.coerceAtLeast(1)
        clocksPerQuarter = sourceClocksPerQuarter.coerceAtLeast(1)
        if (clockMode == PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL) updateClockStep()
    }

    fun setSampleRate(value: Int) {
        sampleRate = value.coerceAtLeast(1)
        updateClockStep()
    }

    fun noteOn() {
        if (!enabled) return
        releaseFinished = false
        if (format == PmdPerformanceLaws.ENVELOPE_LEGACY) {
            legacyOffset = 0
            if (attack > 0) {
                stage = STAGE_ATTACK
                counter = attack
            } else {
                legacyOffset = decay
                stage = STAGE_SUSTAIN
                counter = sustain
            }
        } else {
            extendedLevel = attackLevel.coerceIn(0, MAX_LEVEL)
            stage = STAGE_ATTACK
            counter = initialExtendedCounter(attack, STAGE_ATTACK)
            clockExtended()
        }
    }

    fun noteOff() {
        if (!enabled) return
        stage = STAGE_RELEASE
        if (format == PmdPerformanceLaws.ENVELOPE_LEGACY) {
            counter = release
            if (release == 0) {
                legacyOffset = -MAX_LEVEL
                releaseFinished = true
            }
        } else {
            counter = initialExtendedCounter(release, STAGE_RELEASE)
        }
    }

    fun advanceSample() {
        if (!enabled || releaseFinished) return
        val previous = clockPhase
        clockPhase += clockStep
        if (clockPhase < previous) {
            if (format == PmdPerformanceLaws.ENVELOPE_LEGACY) clockLegacy() else clockExtended()
        }
    }

    fun levelFor(baseLevel: Int): Int {
        if (!enabled) return baseLevel
        return if (format == PmdPerformanceLaws.ENVELOPE_LEGACY) {
            (baseLevel + legacyOffset).coerceIn(0, MAX_LEVEL)
        } else {
            (baseLevel * (extendedLevel + 1) / (MAX_LEVEL + 1)).coerceIn(0, MAX_LEVEL)
        }
    }

    fun finishedRelease(): Boolean = releaseFinished

    fun levelOffsetSnapshot(baseLevel: Int): Int = levelFor(baseLevel) - baseLevel

    fun reset() {
        format = PmdPerformanceLaws.ENVELOPE_DISABLED
        clockMode = PmdPerformanceLaws.ENVELOPE_CLOCK_NORMAL
        tempoMilliBpm = 120 * PmdPerformanceLaws.BPM_MILLI_SCALE
        clocksPerQuarter = PmdPerformanceLaws.DEFAULT_CLOCKS_PER_QUARTER
        clockPhase = 0u
        clockStep = 0u
        attack = 0
        decay = 0
        sustain = 0
        release = 0
        sustainLevel = 0
        attackLevel = 0
        stage = STAGE_OFF
        counter = 0
        legacyOffset = 0
        extendedLevel = 0
        releaseFinished = false
    }

    private fun updateClockStep() {
        val milliHertz = if (clockMode == PmdPerformanceLaws.ENVELOPE_CLOCK_EXTENDED) {
            PmdPerformanceLaws.EXTENDED_ENVELOPE_CLOCK_MILLIHERTZ
        } else {
            tempoMilliBpm.toLong() * clocksPerQuarter.toLong() / SECONDS_PER_MINUTE
        }
        clockStep = (
            milliHertz * UINT_CYCLE /
                (sampleRate.coerceAtLeast(1).toLong() * MILLIHERTZ_PER_HERTZ)
            ).toUInt()
    }

    private fun clockLegacy() {
        when (stage) {
            STAGE_ATTACK -> {
                counter--
                if (counter <= 0) {
                    legacyOffset = (legacyOffset + decay).coerceIn(-MAX_LEVEL, MAX_LEVEL)
                    stage = STAGE_SUSTAIN
                    counter = sustain
                }
            }
            STAGE_SUSTAIN -> {
                if (sustain > 0) {
                    counter--
                    if (counter <= 0) {
                        legacyOffset = (legacyOffset - 1).coerceAtLeast(-MAX_LEVEL)
                        counter = sustain
                    }
                }
            }
            STAGE_RELEASE -> {
                if (release == 0) {
                    legacyOffset = -MAX_LEVEL
                    releaseFinished = true
                } else {
                    counter--
                    if (counter <= 0) {
                        legacyOffset--
                        counter = release
                        if (legacyOffset <= -MAX_LEVEL) {
                            legacyOffset = -MAX_LEVEL
                            releaseFinished = true
                        }
                    }
                }
            }
        }
    }

    private fun clockExtended() {
        when (stage) {
            STAGE_ATTACK -> {
                val amount = extendedAmount(attack)
                if (amount > 0) {
                    extendedLevel += amount
                    counter = initialExtendedCounter(attack, STAGE_ATTACK)
                    if (extendedLevel >= MAX_LEVEL) {
                        extendedLevel = MAX_LEVEL
                        val target = MAX_LEVEL - sustainLevel.coerceIn(0, MAX_LEVEL)
                        stage = if (target == MAX_LEVEL) STAGE_SUSTAIN else STAGE_DECAY
                        counter = initialExtendedCounter(
                            if (stage == STAGE_DECAY) decay else sustain,
                            stage
                        )
                    }
                }
            }
            STAGE_DECAY -> {
                val amount = extendedAmount(decay)
                if (amount > 0) {
                    extendedLevel -= amount
                    counter = initialExtendedCounter(decay, STAGE_DECAY)
                    val target = MAX_LEVEL - sustainLevel.coerceIn(0, MAX_LEVEL)
                    if (extendedLevel <= target) {
                        extendedLevel = target
                        stage = STAGE_SUSTAIN
                        counter = initialExtendedCounter(sustain, STAGE_SUSTAIN)
                    }
                }
            }
            STAGE_SUSTAIN -> {
                val amount = extendedAmount(sustain)
                if (amount > 0) {
                    extendedLevel = (extendedLevel - amount).coerceAtLeast(0)
                    counter = initialExtendedCounter(sustain, STAGE_SUSTAIN)
                }
            }
            STAGE_RELEASE -> {
                val amount = extendedAmount(release)
                if (amount > 0) {
                    extendedLevel = (extendedLevel - amount).coerceAtLeast(0)
                    counter = initialExtendedCounter(release, STAGE_RELEASE)
                    if (extendedLevel == 0) releaseFinished = true
                }
            }
        }
    }

    private fun extendedAmount(rate: Int): Int {
        if (rate == 0) return 0
        if (counter <= 0) {
            counter++
            return 0
        }
        return counter
    }

    private fun initialExtendedCounter(rate: Int, selectedStage: Int): Int = when (selectedStage) {
        STAGE_ATTACK -> rate - RATE_CENTER
        STAGE_RELEASE -> rate * 2 - RATE_CENTER
        else -> {
            val centered = rate - RATE_CENTER
            if (centered < 0) centered * 2 else centered
        }
    }

    private companion object {
        const val STAGE_OFF = 0
        const val STAGE_ATTACK = 1
        const val STAGE_DECAY = 2
        const val STAGE_SUSTAIN = 3
        const val STAGE_RELEASE = 4
        const val MAX_LEVEL = 15
        const val RATE_CENTER = 16
        const val SECONDS_PER_MINUTE = 60L
        const val MILLIHERTZ_PER_HERTZ = 1_000L
        const val UINT_CYCLE = 4_294_967_296L
    }
}
