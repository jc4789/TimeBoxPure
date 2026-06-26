package com.example.timeboxvibe.engine.core

import kotlin.concurrent.Volatile

import kotlinx.serialization.Serializable

/**
 * TimerPreset — Full data model matching the original React i18n.js presets.
 * Supports all 5 engine modes: classic, dual, dual.5, sequence, dual-sequence.
 */
@Serializable
data class TimerPreset(
    val id: String,
    val name: String,
    val mode: String,                    // "classic" | "dual" | "dual.5" | "sequence" | "dual-sequence" | "calendar"
    val sequence: IntArray = IntArray(0), // Array of seconds for sequence modes
    val dualBigDuration: Int = 3600,     // Macro timer total seconds
    val dualMidDuration: Int = 900,      // Medium timer loop seconds (Dual.5 only)
    val dualSmallDuration: Int = 90,     // Micro timer loop seconds
    val alarmBehavior: String = "alarm", // "alarm" (requires dismissal) | "auto" (auto-progresses)
    val description: String = "",
    val sequenceTypes: Array<String> = emptyArray(), // "focus" | "relax"
    val sequenceLabels: Array<String> = emptyArray() // custom text labels
) {
    fun stageCount(): Int {
        return when (mode) {
            "classic", "sequence", "calendar", "dual-sequence" -> if (sequence.isNotEmpty()) sequence.size else 1
            else -> 1
        }
    }

    fun stageDuration(index: Int): Int {
        val safeIndex = index.coerceIn(0, (sequence.size - 1).coerceAtLeast(0))
        return when (mode) {
            "classic", "sequence", "calendar", "dual-sequence" -> {
                if (sequence.isNotEmpty()) sequence[safeIndex] else dualSmallDuration
            }
            "dual.5" -> dualSmallDuration
            "dual" -> dualSmallDuration
            else -> if (sequence.isNotEmpty()) sequence[safeIndex] else dualSmallDuration
        }
    }

    fun stageLabel(index: Int): String {
        val count = stageCount().coerceAtLeast(1)
        val safeIndex = index.coerceIn(0, count - 1)
        if (safeIndex < sequenceLabels.size && sequenceLabels[safeIndex].isNotBlank()) {
            return sequenceLabels[safeIndex]
        }
        val duration = stageDuration(safeIndex)
        return "Stage ${safeIndex + 1}/$count · ${formatStageDuration(duration)}"
    }

    fun stageType(index: Int): String {
        val count = stageCount().coerceAtLeast(1)
        val safeIndex = index.coerceIn(0, count - 1)
        if (safeIndex < sequenceTypes.size && sequenceTypes[safeIndex].isNotBlank()) {
            return sequenceTypes[safeIndex]
        }
        return if (mode == "calendar") "focus" else ""
    }

    fun normalized(logFailures: Boolean = false): TimerPreset {
        val normalizedMode = if (isKnownMode(mode)) mode else "classic"
        val normalizedAlarm = if (alarmBehavior == "alarm" || alarmBehavior == "auto") alarmBehavior else "alarm"
        val copiedSequence = sequence.copyOf()
        var copiedTypes = sequenceTypes.copyOf()
        var copiedLabels = sequenceLabels.copyOf()

        if (normalizedMode == "calendar") {
            val count = copiedSequence.size
            if (copiedTypes.size != count) {
                copiedTypes = Array(count) { idx ->
                    if (idx < sequenceTypes.size && sequenceTypes[idx].isNotBlank()) sequenceTypes[idx] else "focus"
                }
            }
            if (copiedLabels.size != count) {
                copiedLabels = Array(count) { idx ->
                    val type = if (idx < copiedTypes.size) copiedTypes[idx] else "focus"
                    if (idx < sequenceLabels.size && sequenceLabels[idx].isNotBlank()) {
                        sequenceLabels[idx]
                    } else if (type == "relax") {
                        "Break ${idx + 1}"
                    } else {
                        "Focus Session ${idx + 1}"
                    }
                }
            }
        } else if (normalizedMode == "dual" || normalizedMode == "dual.5") {
            copiedTypes = emptyArray()
            copiedLabels = emptyArray()
        } else {
            if (copiedLabels.isNotEmpty() && copiedLabels.size != copiedSequence.size) copiedLabels = emptyArray()
            if (copiedTypes.isNotEmpty() && copiedTypes.size != copiedSequence.size) copiedTypes = emptyArray()
        }

        val normalized = copy(
            mode = normalizedMode,
            sequence = copiedSequence,
            alarmBehavior = normalizedAlarm,
            sequenceTypes = copiedTypes,
            sequenceLabels = copiedLabels
        )
        if (logFailures) normalized.validate(logFailures = true)
        return normalized
    }

    fun validate(logFailures: Boolean = false): Boolean {
        var ok = true
        fun fail(message: String) {
            ok = false
            if (logFailures) {
                println("TimerPreset validation failed id=$id mode=$mode reason=$message")
            }
        }

        if (id.isBlank()) fail("blank id")
        if (!isKnownMode(mode)) fail("unknown mode")
        if (sequenceLabels.isNotEmpty() && sequenceLabels.size != sequence.size) fail("sequenceLabels size mismatch")
        if (sequenceTypes.isNotEmpty() && sequenceTypes.size != sequence.size) fail("sequenceTypes size mismatch")

        when (mode) {
            "classic" -> {
                if (sequence.isEmpty() || sequence[0] <= 0) fail("classic duration missing or invalid")
            }
            "sequence", "calendar" -> {
                validateSequenceValues { fail(it) }
                if (mode == "calendar") {
                    if (sequenceLabels.size != sequence.size) fail("calendar labels missing")
                    if (sequenceTypes.size != sequence.size) fail("calendar types missing")
                }
            }
            "dual" -> {
                if (dualBigDuration <= 0) fail("dual big duration invalid")
                if (dualSmallDuration <= 0) fail("dual small duration invalid")
            }
            "dual.5" -> {
                if (dualBigDuration <= 0) fail("dual.5 big duration invalid")
                if (dualMidDuration <= 0) fail("dual.5 mid duration invalid")
                if (dualSmallDuration <= 0) fail("dual.5 small duration invalid")
            }
            "dual-sequence" -> {
                validateSequenceValues { fail(it) }
                if (dualSmallDuration <= 0) fail("dual-sequence small duration invalid")
            }
        }
        return ok
    }

    private fun validateSequenceValues(fail: (String) -> Unit) {
        if (sequence.isEmpty()) {
            fail("sequence missing")
            return
        }
        var i = 0
        while (i < sequence.size) {
            if (sequence[i] <= 0) fail("sequence[$i] invalid")
            i++
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TimerPreset) return false

        other as TimerPreset

        if (id != other.id) return false
        if (name != other.name) return false
        if (mode != other.mode) return false
        if (!sequence.contentEquals(other.sequence)) return false
        if (dualBigDuration != other.dualBigDuration) return false
        if (dualMidDuration != other.dualMidDuration) return false
        if (dualSmallDuration != other.dualSmallDuration) return false
        if (alarmBehavior != other.alarmBehavior) return false
        if (description != other.description) return false
        if (!sequenceTypes.contentEquals(other.sequenceTypes)) return false
        if (!sequenceLabels.contentEquals(other.sequenceLabels)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + sequence.contentHashCode()
        result = 31 * result + dualBigDuration
        result = 31 * result + dualMidDuration
        result = 31 * result + dualSmallDuration
        result = 31 * result + alarmBehavior.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + sequenceTypes.contentHashCode()
        result = 31 * result + sequenceLabels.contentHashCode()
        return result
    }

    companion object {
        fun isKnownMode(mode: String): Boolean {
            return mode == "classic" ||
                mode == "dual" ||
                mode == "dual.5" ||
                mode == "sequence" ||
                mode == "dual-sequence" ||
                mode == "calendar"
        }

        private fun formatStageDuration(seconds: Int): String {
            val safe = seconds.coerceAtLeast(0)
            val minutes = safe / 60
            val secs = safe % 60
            val secTens = secs / 10
            val secOnes = secs % 10
            return "$minutes:$secTens$secOnes"
        }
    }
}

/**
 * TimerEngine — Pure Kotlin port of useTimer.js.
 * Manages all 5 timer modes with multi-tier countdown logic.
 *
 * Call tick() every second. The engine updates its internal state
 * and returns events (interval complete, sequence complete, ringing).
 */
class TimerEngine(
    preset: TimerPreset
) {
    var preset: TimerPreset = preset.normalized(logFailures = true)
        private set
    var alarmScheduler: PlatformAlarmScheduler? = null
    
    // Primary (small/action) timer
    var timeRemaining: Int = 0; private set
    var totalDuration: Int = 0; private set

    // Medium timer (Dual.5 only)
    var midTimeRemaining: Int = 0; private set
    var midTotalDuration: Int = 0; private set

    // Big (macro) timer
    var bigTimeRemaining: Int = 0; private set
    var bigTotalDuration: Int = 0; private set

    // Sequence tracking
    var currentIndex: Int = 0; private set
    val sequenceLength: Int get() = preset.sequence.size
    val currentStageLabel: String get() = preset.stageLabel(currentIndex)
    val currentStageType: String get() = preset.stageType(currentIndex)

    // State
    var isActive: Boolean = false
        private set(value) {
            field = value
            state = if (value) ACTIVE else "INACTIVE"
        }
    var isRinging: Boolean = false; private set

    companion object {
        @kotlin.concurrent.Volatile
        var state: String = "INACTIVE"
        const val ACTIVE = "ACTIVE"
    }
    
    // Event Queue for cross-thread sync bridging
    @Volatile
    var hasSyncInterruption: Boolean = false

    @Volatile
    var isDirty: Boolean = true

    // Mode shortcut
    val mode: String get() = preset.mode
    val isDual: Boolean get() = mode == "dual" || mode == "dual-sequence" || mode == "dual.5"
    val isBreak: Boolean get() = (mode == "calendar" && currentIndex < preset.sequenceTypes.size && preset.sequenceTypes[currentIndex] == "relax")

    init {
        initialize()
    }

    /** Reset everything to the preset's initial state. */
    fun initialize() {
        isActive = false
        isRinging = false
        currentIndex = 0

        when (mode) {
            "classic" -> {
                val dur = preset.sequence.firstOrNull() ?: 1500
                timeRemaining = dur; totalDuration = dur
                midTimeRemaining = 0; midTotalDuration = 0
                bigTimeRemaining = 0; bigTotalDuration = 0
            }
            "dual" -> {
                timeRemaining = preset.dualSmallDuration; totalDuration = preset.dualSmallDuration
                midTimeRemaining = 0; midTotalDuration = 0
                bigTimeRemaining = preset.dualBigDuration; bigTotalDuration = preset.dualBigDuration
            }
            "dual.5" -> {
                timeRemaining = preset.dualSmallDuration; totalDuration = preset.dualSmallDuration
                midTimeRemaining = preset.dualMidDuration; midTotalDuration = preset.dualMidDuration
                bigTimeRemaining = preset.dualBigDuration; bigTotalDuration = preset.dualBigDuration
            }
            "sequence" -> {
                val first = preset.sequence.firstOrNull() ?: 60
                timeRemaining = first; totalDuration = first
                midTimeRemaining = 0; midTotalDuration = 0
                bigTimeRemaining = 0; bigTotalDuration = 0
            }
            "calendar" -> {
                val first = preset.sequence.firstOrNull() ?: 600
                timeRemaining = first; totalDuration = first
                midTimeRemaining = 0; midTotalDuration = 0
                bigTotalDuration = sumSequenceFrom(0)
                bigTimeRemaining = bigTotalDuration
            }
            "dual-sequence" -> {
                val firstBig = preset.sequence.firstOrNull() ?: 600
                timeRemaining = preset.dualSmallDuration; totalDuration = preset.dualSmallDuration
                midTimeRemaining = 0; midTotalDuration = 0
                bigTimeRemaining = firstBig; bigTotalDuration = firstBig
            }
        }
    }

    fun start() { 
        isActive = true; isRinging = false 
        scheduleNextAlarm()
    }

    fun pause() { 
        isActive = false 
        alarmScheduler?.cancelAlarm()
    }
    
    // Engine calculates target timestamp to instruct OS boundaries.
    private fun scheduleNextAlarm() {
        if (!isActive || isRinging) return
        alarmScheduler?.scheduleExactAlarm(getEpochMillis() + (timeRemaining * 1000L))
    }

    fun reset() { 
        initialize()
        alarmScheduler?.cancelAlarm() 
    }

    fun restoreState(
        timeRemaining: Int,
        midTimeRemaining: Int,
        bigTimeRemaining: Int,
        currentIndex: Int
    ) {
        val maxIndex = (preset.stageCount() - 1).coerceAtLeast(0)
        this.timeRemaining = timeRemaining
        this.midTimeRemaining = midTimeRemaining
        this.bigTimeRemaining = bigTimeRemaining
        this.currentIndex = currentIndex.coerceIn(0, maxIndex)
    }

    fun changePreset(newPreset: TimerPreset) {
        alarmScheduler?.cancelAlarm()
        preset = newPreset.normalized(logFailures = true)
        initialize()
    }
    
    // Snaps the mathematical state into reality following an OS interruption event.
    fun processSyncInterruption(): TickEvent {
        if (!hasSyncInterruption) return TickEvent.None
        hasSyncInterruption = false
        // Instantly force to zero and progress state
        if (timeRemaining > 0) {
            val elapsed = timeRemaining
            timeRemaining = 0
            if (mode == "dual.5") midTimeRemaining -= elapsed
            if (isDual || mode == "calendar") bigTimeRemaining -= elapsed
        }
        return tick() 
    }

    sealed interface TickEvent {
        data object None : TickEvent
        data object IntervalComplete : TickEvent
        data object SequenceComplete : TickEvent
    }

    /**
     * Advance the timer by 1 second. Returns a TickEvent indicating
     * what happened (nothing, interval ended, or full sequence ended).
     */
    fun tick(): TickEvent {
        if (!isActive || isRinging) return TickEvent.None
        isDirty = true

        // Decrement all active tiers
        timeRemaining--
        if (mode == "dual.5") midTimeRemaining--
        if (isDual || mode == "calendar") bigTimeRemaining--

        // === A. BIG TIMER COMPLETE (Dual and Calendar modes) ===
        if ((isDual || mode == "calendar") && bigTimeRemaining <= 0) {
            if (mode == "dual-sequence" && currentIndex < preset.sequence.size - 1) {
                // Move to next sequence block
                if (preset.alarmBehavior == "alarm") {
                    isActive = false; isRinging = true
                    alarmScheduler?.cancelAlarm()
                    return TickEvent.IntervalComplete
                } else {
                    return advanceExpiredBlock()
                }
            }
            // Session over
            isActive = false
            timeRemaining = 0; midTimeRemaining = 0; bigTimeRemaining = 0
            if (preset.alarmBehavior == "alarm") isRinging = true
            alarmScheduler?.cancelAlarm()
            return TickEvent.SequenceComplete
        }

        // === B. MID TIMER COMPLETE (Dual.5 only) ===
        if (mode == "dual.5" && midTimeRemaining <= 0) {
            if (preset.alarmBehavior == "alarm") {
                isActive = false; isRinging = true
                alarmScheduler?.cancelAlarm()
                return TickEvent.IntervalComplete
            } else {
                return advanceExpiredBlock()
            }
        }

        // === C. SMALL TIMER COMPLETE ===
        if (timeRemaining <= 0) {
            return when (mode) {
                "classic" -> {
                    isActive = false
                    if (preset.alarmBehavior == "alarm") isRinging = true
                    alarmScheduler?.cancelAlarm()
                    TickEvent.SequenceComplete
                }
                "dual" -> {
                    if (preset.alarmBehavior == "alarm") {
                        isActive = false; isRinging = true
                        alarmScheduler?.cancelAlarm()
                    } else {
                        return advanceExpiredBlock()
                    }
                    TickEvent.IntervalComplete
                }
                "dual.5" -> {
                    // Micro stopwatch always auto-loops
                    return advanceExpiredBlock()
                }
                "sequence" -> {
                    if (currentIndex < preset.sequence.size - 1) {
                        if (preset.alarmBehavior == "alarm") {
                            isActive = false; isRinging = true
                            alarmScheduler?.cancelAlarm()
                        } else {
                            return advanceExpiredBlock()
                        }
                        TickEvent.IntervalComplete
                    } else {
                        isActive = false
                        if (preset.alarmBehavior == "alarm") isRinging = true
                        alarmScheduler?.cancelAlarm()
                        TickEvent.SequenceComplete
                    }
                }
                "calendar" -> {
                    if (currentIndex < preset.sequence.size - 1) {
                        if (preset.alarmBehavior == "alarm") {
                            isActive = false
                            isRinging = true
                            alarmScheduler?.cancelAlarm()
                        } else {
                            return advanceExpiredBlock()
                        }
                        TickEvent.IntervalComplete
                    } else {
                        isActive = false
                        if (preset.alarmBehavior == "alarm") isRinging = true
                        alarmScheduler?.cancelAlarm()
                        TickEvent.SequenceComplete
                    }
                }
                "dual-sequence" -> {
                    if (preset.alarmBehavior == "alarm") {
                        isActive = false; isRinging = true
                        alarmScheduler?.cancelAlarm()
                    } else {
                        return advanceExpiredBlock()
                    }
                    TickEvent.IntervalComplete
                }
                else -> TickEvent.None
            }
        }

        return TickEvent.None
    }

    /**
     * Dismiss the alarm and resume or end the session.
     * Mirrors the dismissAlarm() logic from useTimer.js.
     */
    fun dismissAlarm() {
        val wasRinging = isRinging
        isRinging = false
        alarmScheduler?.cancelAlarm()

        if (!wasRinging) {
            return
        }

        advanceAfterAlarmAcknowledged()
    }

    private fun advanceAfterAlarmAcknowledged(): TickEvent {
        isDirty = true

        return when (mode) {
            "sequence", "calendar" -> advanceExplicitStage()
            "classic" -> {
                isActive = false
                timeRemaining = 0
                alarmScheduler?.cancelAlarm()
                TickEvent.SequenceComplete
            }
            else -> advanceExpiredBlock()
        }
    }

    private fun advanceExpiredBlock(): TickEvent {
        isDirty = true

        when (mode) {
            "dual.5" -> {
                if (bigTimeRemaining <= 0) {
                    isActive = false
                    timeRemaining = 0; midTimeRemaining = 0; bigTimeRemaining = 0
                    alarmScheduler?.cancelAlarm()
                    return TickEvent.SequenceComplete
                } else if (midTimeRemaining <= 0) {
                    val nextMid = minOf(preset.dualMidDuration, bigTimeRemaining)
                    midTimeRemaining = nextMid; midTotalDuration = nextMid
                    val nextSub = minOf(preset.dualSmallDuration, nextMid)
                    timeRemaining = nextSub; totalDuration = nextSub
                }
                isActive = true
                scheduleNextAlarm()
                return TickEvent.IntervalComplete
            }
            "dual" -> {
                if (bigTimeRemaining <= 0) {
                    isActive = false
                    timeRemaining = 0; bigTimeRemaining = 0
                    alarmScheduler?.cancelAlarm()
                    return TickEvent.SequenceComplete
                } else if (timeRemaining <= 0) {
                    val next = minOf(preset.dualSmallDuration, bigTimeRemaining)
                    timeRemaining = next; totalDuration = next
                }
                isActive = true
                scheduleNextAlarm()
                return TickEvent.IntervalComplete
            }
            "sequence" -> {
                if (timeRemaining <= 0 && currentIndex < preset.sequence.size - 1) {
                    val nextIdx = currentIndex + 1
                    currentIndex = nextIdx
                    timeRemaining = preset.sequence[nextIdx]
                    totalDuration = preset.sequence[nextIdx]
                    isActive = true
                    scheduleNextAlarm()
                    return TickEvent.IntervalComplete
                } else {
                    isActive = false
                    timeRemaining = 0
                    alarmScheduler?.cancelAlarm()
                    return TickEvent.SequenceComplete
                }
            }
            "calendar" -> {
                if (timeRemaining <= 0 && currentIndex < preset.sequence.size - 1) {
                    val nextIdx = currentIndex + 1
                    currentIndex = nextIdx
                    timeRemaining = preset.sequence[nextIdx]
                    totalDuration = preset.sequence[nextIdx]
                    bigTimeRemaining = sumSequenceFrom(nextIdx)
                    bigTotalDuration = bigTimeRemaining
                    isActive = true
                    scheduleNextAlarm()
                    return TickEvent.IntervalComplete
                } else {
                    isActive = false
                    timeRemaining = 0
                    if (currentIndex >= preset.sequence.size - 1) bigTimeRemaining = 0
                    alarmScheduler?.cancelAlarm()
                    return TickEvent.SequenceComplete
                }
            }
            "dual-sequence" -> {
                if (bigTimeRemaining <= 0) {
                    if (currentIndex < preset.sequence.size - 1) {
                        val nextIdx = currentIndex + 1
                        currentIndex = nextIdx
                        val nextBig = preset.sequence[nextIdx]
                        bigTimeRemaining = nextBig; bigTotalDuration = nextBig
                        timeRemaining = preset.dualSmallDuration; totalDuration = preset.dualSmallDuration
                        isActive = true
                        scheduleNextAlarm()
                        return TickEvent.IntervalComplete
                    } else {
                        isActive = false
                        timeRemaining = 0; bigTimeRemaining = 0
                        alarmScheduler?.cancelAlarm()
                        return TickEvent.SequenceComplete
                    }
                } else if (timeRemaining <= 0) {
                    val next = minOf(preset.dualSmallDuration, bigTimeRemaining)
                    timeRemaining = next; totalDuration = next
                    isActive = true
                    scheduleNextAlarm()
                    return TickEvent.IntervalComplete
                }
                return TickEvent.None
            }
            "classic" -> {
                // Classic session is done after alarm
                isActive = false
                timeRemaining = 0
                alarmScheduler?.cancelAlarm()
                return TickEvent.SequenceComplete
            }
            else -> return TickEvent.None
        }
    }

    /**
     * Skip to the next interval (for sequence and dual-sequence modes).
     */
    fun skip() {
        when (mode) {
            "sequence", "calendar" -> advanceExplicitStage()
            "dual-sequence" -> {
                if (currentIndex < preset.sequence.size - 1) {
                    val nextIdx = currentIndex + 1
                    currentIndex = nextIdx
                    bigTimeRemaining = preset.sequence[nextIdx]
                    bigTotalDuration = preset.sequence[nextIdx]
                    timeRemaining = preset.dualSmallDuration
                    totalDuration = preset.dualSmallDuration
                    scheduleNextAlarm()
                } else {
                    isActive = false; timeRemaining = 0; bigTimeRemaining = 0
                    alarmScheduler?.cancelAlarm()
                }
            }
        }
    }

    private fun advanceExplicitStage(): TickEvent {
        isDirty = true
        isRinging = false
        if (currentIndex < preset.sequence.size - 1) {
            val nextIdx = currentIndex + 1
            currentIndex = nextIdx
            timeRemaining = preset.sequence[nextIdx]
            totalDuration = preset.sequence[nextIdx]
            if (mode == "calendar") {
                bigTimeRemaining = sumSequenceFrom(nextIdx)
                bigTotalDuration = bigTimeRemaining
            }
            isActive = true
            scheduleNextAlarm()
            return TickEvent.IntervalComplete
        }

        isActive = false
        timeRemaining = 0
        if (mode == "calendar") bigTimeRemaining = 0
        alarmScheduler?.cancelAlarm()
        return TickEvent.SequenceComplete
    }

    private fun sumSequenceFrom(startIndex: Int): Int {
        if (startIndex >= preset.sequence.size) return 0
        var total = 0
        var i = if (startIndex < 0) 0 else startIndex
        while (i < preset.sequence.size) {
            val duration = preset.sequence[i]
            if (duration > 0) total += duration
            i++
        }
        return total
    }
}
