package com.example.timeboxvibe.engine.audio.opna

/** Exact-size sample plan. Boundaries reference immutable tick-domain payload tables. */
class CompiledOpnaTimeline internal constructor(
    internal val song: CompiledOpnaSong,
    val eventCount: Int,
    val loopLengthSamples: Long,
    private val sampleTimes: LongArray,
    private val phases: IntArray,
    private val boundaryKinds: IntArray,
    private val payloadIndices: IntArray,
    private val authoredEventIndices: IntArray
) {
    internal val pmdClocksPerQuarter: Int get() = song.pmdClocksPerQuarter
    internal val instrumentBank: CompiledInstrumentBank get() = song.instrumentBank

    internal fun sampleTime(index: Int): Long = sampleTimes[index]
    internal fun phase(index: Int): Int = phases[index]
    internal fun boundaryKind(index: Int): Int = boundaryKinds[index]
    internal fun payloadIndex(index: Int): Int = payloadIndices[index]
    internal fun authoredEventIndex(index: Int): Int = authoredEventIndices[index]
    internal fun sourceOrder(index: Int): Int {
        val authored = authoredEventIndices[index]
        return if (authored >= 0) song.sourceOrder(authored) else Int.MAX_VALUE
    }
    internal fun sourceLine(index: Int): Int {
        val authored = authoredEventIndices[index]
        return if (authored >= 0) song.sourceLine(authored) else 0
    }
    internal fun sourceColumn(index: Int): Int {
        val authored = authoredEventIndices[index]
        return if (authored >= 0) song.sourceColumn(authored) else 0
    }
    internal fun semanticKind(index: Int): Int {
        val authored = authoredEventIndices[index]
        return if (authored >= 0) song.authoredKind(authored) else TEMPO_KIND
    }
    internal fun channel(index: Int): Int {
        val authored = authoredEventIndices[index]
        return if (authored >= 0) song.eventChannel(authored) else -1
    }
    internal fun logicalPart(index: Int): Int {
        val authored = authoredEventIndices[index]
        return if (authored >= 0) song.eventLogicalPart(authored) else CompiledOpnaSong.LOGICAL_PART_NONE
    }
    internal fun noteId(index: Int): Int = if (boundaryKinds[index] == BOUNDARY_NOTE_ON ||
        boundaryKinds[index] == BOUNDARY_NOTE_OFF || boundaryKinds[index] == BOUNDARY_GLIDE_START
    ) authoredEventIndices[index] + 1 else 0
    internal fun patchId(index: Int): Int {
        val authored = authoredEventIndices[index]
        return if (authored >= 0) song.eventPatchId(authored) else -1
    }
    internal fun hasExactStorage(): Boolean = sampleTimes.size == eventCount && phases.size == eventCount &&
        boundaryKinds.size == eventCount && payloadIndices.size == eventCount &&
        authoredEventIndices.size == eventCount

    internal companion object {
        const val BOUNDARY_TEMPO = 0
        const val BOUNDARY_NOTE_ON = 1
        const val BOUNDARY_NOTE_OFF = 2
        const val BOUNDARY_STATE = 3
        const val BOUNDARY_MODULATION = 4
        const val BOUNDARY_RHYTHM = 5
        const val BOUNDARY_GLIDE_START = 6

        const val PHASE_GLOBAL = 0
        const val PHASE_STATE = 1
        const val PHASE_KEY_OFF = 2
        const val PHASE_KEY_ON = 3
        const val PHASE_ZERO_GATE_OFF = 4
        const val TEMPO_KIND = -1
        const val INITIAL_TEMPO_PAYLOAD = -1
    }
}

internal object CompiledOpnaTimelineFactory {
    fun build(song: CompiledOpnaSong, sampleRate: Int): CompiledOpnaTimeline {
        require(sampleRate > 0) { "Timeline sample rate must be positive" }
        var delayedGlides = 0
        var note = 0
        while (note < song.notes.size) {
            if (song.notes.glideStartOffsetTick(note) > 0) delayedGlides++
            note++
        }
        val capacity = 1 + song.tempoChangeCount + song.eventCount + song.notes.size + delayedGlides
        val draft = TimelineDraft(song, capacity)
        draft.add(0L, CompiledOpnaTimeline.PHASE_GLOBAL, CompiledOpnaTimeline.BOUNDARY_TEMPO,
            CompiledOpnaTimeline.INITIAL_TEMPO_PAYLOAD, -1)
        var tempo = 0
        while (tempo < song.tempoChangeCount) {
            draft.add(song.tempoTick(tempo), CompiledOpnaTimeline.PHASE_GLOBAL,
                CompiledOpnaTimeline.BOUNDARY_TEMPO, tempo, -1)
            tempo++
        }

        var authored = 0
        while (authored < song.eventCount) {
            val payload = song.authoredPayloadIndex(authored)
            val kind = song.authoredKind(authored)
            when {
                CompiledOpnaSong.isNoteKind(kind) -> addNote(draft, song, payload, authored)
                CompiledOpnaSong.isStateKind(kind) -> draft.add(
                    song.authoredStartTick(authored), CompiledOpnaTimeline.PHASE_STATE,
                    CompiledOpnaTimeline.BOUNDARY_STATE, payload, authored
                )
                CompiledOpnaSong.isRhythmKind(kind) -> {
                    val phase = when (kind) {
                        CompiledOpnaSong.RHYTHM_CONTROL_DUMP -> CompiledOpnaTimeline.PHASE_KEY_OFF
                        CompiledOpnaSong.RHYTHM_CONTROL_SHOT, CompiledOpnaSong.RHYTHM_SHOT,
                        CompiledOpnaSong.SSG_DRUM_SHOT -> CompiledOpnaTimeline.PHASE_KEY_ON
                        else -> CompiledOpnaTimeline.PHASE_STATE
                    }
                    draft.add(song.authoredStartTick(authored), phase,
                        CompiledOpnaTimeline.BOUNDARY_RHYTHM, payload, authored)
                }
                else -> {
                    val phase = if (kind == CompiledOpnaSong.HW_LFO_ENABLE ||
                        kind == CompiledOpnaSong.HW_LFO_RATE
                    ) CompiledOpnaTimeline.PHASE_GLOBAL else CompiledOpnaTimeline.PHASE_STATE
                    draft.add(song.authoredStartTick(authored), phase,
                        CompiledOpnaTimeline.BOUNDARY_MODULATION, payload, authored)
                }
            }
            authored++
        }
        return draft.build(sampleRate)
    }

    private fun addNote(
        draft: TimelineDraft,
        song: CompiledOpnaSong,
        payload: Int,
        authored: Int
    ) {
        val start = song.notes.startTick(payload)
        val gateEnd = start + song.notes.gateTick(payload)
        draft.add(start, CompiledOpnaTimeline.PHASE_KEY_ON,
            CompiledOpnaTimeline.BOUNDARY_NOTE_ON, payload, authored)
        val glideOffset = song.notes.glideStartOffsetTick(payload)
        if (glideOffset > 0) draft.add(
            start + glideOffset, CompiledOpnaTimeline.PHASE_STATE,
            CompiledOpnaTimeline.BOUNDARY_GLIDE_START, payload, authored
        )
        draft.add(gateEnd,
            if (gateEnd == start) CompiledOpnaTimeline.PHASE_ZERO_GATE_OFF
            else CompiledOpnaTimeline.PHASE_KEY_OFF,
            CompiledOpnaTimeline.BOUNDARY_NOTE_OFF, payload, authored)
    }
}

private class TimelineDraft(
    private val song: CompiledOpnaSong,
    private val capacity: Int
) {
    private var size = 0
    private val ticks = LongArray(capacity)
    private val phases = IntArray(capacity)
    private val boundaryKinds = IntArray(capacity)
    private val payloadIndices = IntArray(capacity)
    private val authoredEventIndices = IntArray(capacity)

    fun add(tick: Long, phase: Int, boundaryKind: Int, payloadIndex: Int, authoredEventIndex: Int) {
        check(size < capacity) { "Timeline boundary count changed between passes" }
        ticks[size] = tick
        phases[size] = phase
        boundaryKinds[size] = boundaryKind
        payloadIndices[size] = payloadIndex
        authoredEventIndices[size] = authoredEventIndex
        size++
    }

    fun build(sampleRate: Int): CompiledOpnaTimeline {
        check(size == capacity) { "Timeline boundary count changed between passes" }
        val order = sortedIndices()
        val sortedSamples = LongArray(size)
        val sortedPhases = IntArray(size)
        val sortedKinds = IntArray(size)
        val sortedPayloads = IntArray(size)
        val sortedAuthored = IntArray(size)
        var i = 0
        while (i < size) {
            val source = order[i]
            sortedSamples[i] = PmdSampleClock.samplesAt(song, ticks[source], sampleRate)
            sortedPhases[i] = phases[source]
            sortedKinds[i] = boundaryKinds[source]
            sortedPayloads[i] = payloadIndices[source]
            sortedAuthored[i] = authoredEventIndices[source]
            i++
        }
        return CompiledOpnaTimeline(
            song, size, PmdSampleClock.samplesAt(song, song.durationTicks, sampleRate),
            sortedSamples, sortedPhases, sortedKinds, sortedPayloads, sortedAuthored
        )
    }

    private fun sortedIndices(): IntArray {
        var order = IntArray(size) { it }
        var scratch = IntArray(size)
        var width = 1
        while (width < size) {
            var left = 0
            while (left < size) {
                val middle = minOf(left + width, size)
                val end = minOf(left + width * 2, size)
                var first = left
                var second = middle
                var output = left
                while (output < end) {
                    val takeFirst = second >= end ||
                        (first < middle && comesBefore(order[first], order[second]))
                    scratch[output] = if (takeFirst) order[first++] else order[second++]
                    output++
                }
                left += width * 2
            }
            val swap = order
            order = scratch
            scratch = swap
            width *= 2
        }
        return order
    }

    private fun comesBefore(first: Int, second: Int): Boolean {
        if (ticks[first] != ticks[second]) return ticks[first] < ticks[second]
        if (phases[first] != phases[second]) return phases[first] < phases[second]
        val firstOrder = sourceOrder(first)
        val secondOrder = sourceOrder(second)
        if (firstOrder != secondOrder) return firstOrder < secondOrder
        return first < second
    }

    private fun sourceOrder(boundary: Int): Int {
        val authored = authoredEventIndices[boundary]
        return if (authored >= 0) song.sourceOrder(authored) else Int.MAX_VALUE
    }
}
