package com.example.timeboxvibe.engine.audio.opna

/** One allocation-free PMD software-LFO state. Parts preallocate two instances. */
internal class PmdSoftwareLfo(
    private var sampleRate: Int,
    private val initialRandomSeed: Int = PmdPerformanceLaws.SOFTWARE_LFO_RANDOM_SEED
) {
    private var delay = 0
    private var speed = 1
    private var depthA = 0
    private var depthB = 0
    private var waveform = WAVE_TRIANGLE_1
    private var switchValue = 0
    private var tlMask = 0
    private var clockMode = PmdPerformanceLaws.LFO_CLOCK_NORMAL
    private var tempoMilliBpm = 120_000
    private var clocksPerQuarter = PmdPerformanceLaws.DEFAULT_CLOCKS_PER_QUARTER

    private var clockPhase = 0u
    private var clockStep = 0u
    private var clockRemainderStep = 0L
    private var clockRemainder = 0L
    private var clockDenominator = 1L

    private var delayRemaining = 0
    private var speedRemaining = 1
    private var repetitionRemaining = 0
    private var firstTurn = true
    private var started = false
    private var held = false
    private var step = 0
    private var value = 0
    private var randomState = initialRandomSeed

    private var depthChangeSpeed = 0
    private var depthChangeAmount = 0
    private var depthChangeTime = 0
    private var depthChangeClock = 0
    private var depthChangeRemaining = 0

    val enabled: Boolean
        get() = switchValue == 1 || switchValue == 2 || switchValue == 3 ||
            switchValue == 5 || switchValue == 6 || switchValue == 7
    val targetsPitch: Boolean
        get() = enabled && (switchValue and 1) != 0
    val targetsVolume: Boolean
        get() = enabled && (switchValue and 2) != 0
    val keyOnSynchronized: Boolean
        get() = switchValue in 1..3

    init {
        updateClockStep()
        resetState()
    }

    fun configure(selectedDelay: Int, selectedSpeed: Int, selectedDepthA: Int, selectedDepthB: Int) {
        delay = selectedDelay.coerceIn(0, 255)
        speed = selectedSpeed.coerceIn(0, 255)
        depthA = selectedDepthA.coerceIn(-128, 127)
        depthB = selectedDepthB.coerceIn(0, 255)
        resetState()
    }

    fun setSwitch(value: Int) {
        switchValue = value.coerceIn(0, 7)
    }

    fun setWaveform(value: Int) {
        waveform = value.coerceIn(WAVE_TRIANGLE_1, WAVE_ONE_SHOT)
    }

    fun setClockMode(value: Int) {
        clockMode = value.coerceIn(PmdPerformanceLaws.LFO_CLOCK_NORMAL, PmdPerformanceLaws.LFO_CLOCK_FIXED)
        updateClockStep()
    }

    fun setTlMask(value: Int) {
        tlMask = value.coerceIn(0, 15)
    }

    fun setDepthEvolution(selectedSpeed: Int, amount: Int, time: Int) {
        depthChangeSpeed = selectedSpeed.coerceIn(0, 255)
        depthChangeAmount = amount.coerceIn(-128, 127)
        depthChangeTime = time.coerceIn(0, 127)
        depthChangeClock = 0
        depthChangeRemaining = depthChangeTime
    }

    fun setTempo(bpmMilli: Int, sourceClocksPerQuarter: Int) {
        tempoMilliBpm = bpmMilli.coerceAtLeast(1)
        clocksPerQuarter = sourceClocksPerQuarter.coerceAtLeast(1)
        if (clockMode == PmdPerformanceLaws.LFO_CLOCK_NORMAL) updateClockStep()
    }

    fun setSampleRate(value: Int) {
        sampleRate = value.coerceAtLeast(1)
        updateClockStep()
    }

    fun noteOn() {
        if (enabled && keyOnSynchronized) resetState()
    }

    fun advanceSample() {
        val previous = clockPhase
        clockPhase += clockStep
        clockRemainder += clockRemainderStep
        if (clockRemainder >= clockDenominator) {
            clockPhase++
            clockRemainder -= clockDenominator
        }
        if (clockPhase < previous) clock()
    }

    fun pitchValue(): Int = if (targetsPitch) value else 0
    fun volumeValue(): Int = if (targetsVolume) value else 0
    fun tlMask(): Int = tlMask
    fun valueSnapshot(): Int = value
    fun depthSnapshot(): Int = depthA
    fun randomSnapshot(): Int = randomState

    fun reset() {
        delay = 0
        speed = 1
        depthA = 0
        depthB = 0
        waveform = WAVE_TRIANGLE_1
        switchValue = 0
        tlMask = 0
        clockMode = PmdPerformanceLaws.LFO_CLOCK_NORMAL
        tempoMilliBpm = 120_000
        clocksPerQuarter = PmdPerformanceLaws.DEFAULT_CLOCKS_PER_QUARTER
        depthChangeSpeed = 0
        depthChangeAmount = 0
        depthChangeTime = 0
        updateClockStep()
        resetState()
    }

    private fun resetState() {
        clockPhase = 0u
        clockRemainder = 0L
        delayRemaining = delay
        speedRemaining = interval()
        repetitionRemaining = depthB
        firstTurn = true
        started = false
        held = false
        step = waveformStep()
        value = 0
        randomState = initialRandomSeed
        depthChangeClock = 0
        depthChangeRemaining = depthChangeTime
    }

    private fun updateClockStep() {
        val milliHertz = if (clockMode == PmdPerformanceLaws.LFO_CLOCK_FIXED) {
            PmdPerformanceLaws.FIXED_LFO_CLOCK_MILLIHERTZ
        } else {
            tempoMilliBpm.toLong() * clocksPerQuarter.toLong() / 60L
        }
        clockDenominator = sampleRate.coerceAtLeast(1).toLong() * 1_000L
        val numerator = milliHertz * OpnaLfoLaws.PHASE_CYCLE
        clockStep = (numerator / clockDenominator).toUInt()
        clockRemainderStep = numerator % clockDenominator
    }

    private fun clock() {
        if (!enabled || held) return
        if (delayRemaining > 0) {
            delayRemaining--
            if (delayRemaining == 0) establishImmediateWaveValue()
            return
        }
        if (!started && establishImmediateWaveValue()) return
        speedRemaining--
        if (speedRemaining > 0) return
        speedRemaining = interval()
        when (waveform) {
            WAVE_SQUARE -> clockSquare()
            WAVE_RANDOM -> clockRandom()
            else -> clockStepped()
        }
    }

    private fun clockSquare() {
        value = signed16(-value)
        completedCycle()
    }

    private fun clockRandom() {
        assignRandomValue()
        completedCycle()
    }

    /** Square/random establish their first value at the delay edge, before the speed hold. */
    private fun establishImmediateWaveValue(): Boolean {
        when (waveform) {
            WAVE_SQUARE -> value = signed16(depthA * depthB)
            WAVE_RANDOM -> {
                assignRandomValue()
                completedCycle()
            }
            else -> return false
        }
        started = true
        speedRemaining = interval()
        if (speed == 255) held = true
        return true
    }

    private fun assignRandomValue() {
        var next = randomState
        next = next xor (next shl 13)
        next = next xor (next ushr 17)
        next = next xor (next shl 5)
        randomState = next
        val peak = kotlin.math.abs(depthA * depthB)
        value = if (peak == 0) 0 else (next.toUInt() % (peak * 2 + 1).toUInt()).toInt() - peak
    }

    private fun clockStepped() {
        value = signed16(value + step)
        if (depthB == 255) return
        repetitionRemaining--
        if (repetitionRemaining > 0) return
        when (waveform) {
            WAVE_TRIANGLE_1, WAVE_TRIANGLE_3 -> {
                step = -step
                repetitionRemaining = if (firstTurn) depthB * 2 else depthB
                firstTurn = false
                if (!firstTurn && value == 0) completedCycle()
            }
            WAVE_SAW -> {
                value = signed16(-value)
                repetitionRemaining = if (firstTurn) depthB * 2 else depthB
                firstTurn = false
                completedCycle()
            }
            WAVE_TRIANGLE_2 -> {
                step = -step
                repetitionRemaining = depthB
                if (value == 0) completedCycle()
            }
            WAVE_ONE_SHOT -> held = true
        }
        if (repetitionRemaining <= 0) repetitionRemaining = 1
    }

    private fun completedCycle() {
        if (depthChangeSpeed <= 0) return
        depthChangeClock++
        if (depthChangeClock < depthChangeSpeed) return
        depthChangeClock = 0
        val sign = if (depthA < 0) -1 else 1
        depthA = ((kotlin.math.abs(depthA) + depthChangeAmount).coerceIn(0, 128) * sign)
        step = waveformStep()
        if (depthChangeTime > 0) {
            depthChangeRemaining--
            if (depthChangeRemaining <= 0) depthChangeSpeed = 0
        }
    }

    private fun waveformStep(): Int = if (waveform == WAVE_TRIANGLE_3) {
        depthA * kotlin.math.abs(depthA)
    } else {
        depthA
    }

    private fun interval(): Int = speed.coerceAtLeast(1)
    private fun signed16(input: Int): Int = input.toShort().toInt()

    private companion object {
        const val WAVE_TRIANGLE_1 = 0
        const val WAVE_SAW = 1
        const val WAVE_SQUARE = 2
        const val WAVE_RANDOM = 3
        const val WAVE_TRIANGLE_2 = 4
        const val WAVE_TRIANGLE_3 = 5
        const val WAVE_ONE_SHOT = 6
    }
}
