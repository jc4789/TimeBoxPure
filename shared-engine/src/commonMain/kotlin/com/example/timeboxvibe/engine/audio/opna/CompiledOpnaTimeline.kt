package com.example.timeboxvibe.engine.audio.opna

/** Exact-size, canonically ordered primitive program consumed without allocation. */
class CompiledOpnaTimeline internal constructor(
    val eventCount: Int,
    val loopLengthSamples: Long,
    internal val pmdClocksPerQuarter: Int,
    internal val instrumentBank: CompiledInstrumentBank,
    internal val eventType: IntArray,
    internal val sampleTime: LongArray,
    internal val channel: IntArray,
    internal val logicalPart: IntArray,
    internal val operator: IntArray,
    internal val slotMask: IntArray,
    internal val midi: IntArray,
    internal val velocity: FloatArray,
    internal val noteId: IntArray,
    internal val patchId: IntArray,
    internal val pan: IntArray,
    internal val detuneCents: IntArray,
    internal val targetMidi: IntArray,
    internal val slideFrames: IntArray,
    internal val controlValues: IntArray,
    internal val sourceOrder: IntArray,
    internal val sourceLine: IntArray,
    internal val sourceColumn: IntArray
) {
    companion object {
        internal const val FM_ON = 0
        internal const val FM_OFF = 1
        internal const val SSG_ON = 2
        internal const val SSG_OFF = 3
        internal const val DRUM_SHOT = 4
        internal const val FM3_OPERATOR_ON = 5
        internal const val FM3_OPERATOR_OFF = 6
        internal const val FM_POLY_ON = 7
        internal const val FM_POLY_OFF = 8
        internal const val TEMPO = 9
        internal const val SSG_ENVELOPE_DEFINE = 10
        internal const val SSG_ENVELOPE_MODE = 11
        internal const val SOFTWARE_LFO_DEFINE = 12
        internal const val SOFTWARE_LFO_SWITCH = 13
        internal const val SOFTWARE_LFO_WAVE = 14
        internal const val SOFTWARE_LFO_CLOCK = 15
        internal const val SOFTWARE_LFO_TL_MASK = 16
        internal const val SOFTWARE_LFO_DEPTH = 17
        internal const val FM_SLOT_DETUNE_ABSOLUTE = 18
        internal const val FM_SLOT_DETUNE_RELATIVE = 19
        internal const val FM_TL_ABSOLUTE = 20
        internal const val FM_TL_RELATIVE = 21
        internal const val FM_FEEDBACK_ABSOLUTE = 22
        internal const val FM_FEEDBACK_RELATIVE = 23
        internal const val FM_SLOT_KEY_ON_DELAY = 24
        internal const val FM3_PATCH = 25
        internal const val RHYTHM_CONTROL_SHOT = 26
        internal const val RHYTHM_CONTROL_DUMP = 27
        internal const val RHYTHM_MASTER_ABSOLUTE = 28
        internal const val RHYTHM_MASTER_RELATIVE = 29
        internal const val RHYTHM_VOICE_LEVEL_ABSOLUTE = 30
        internal const val RHYTHM_VOICE_LEVEL_RELATIVE = 31
        internal const val RHYTHM_VOICE_PAN = 32
        internal const val SSG_DRUM_SHOT = 33
        internal const val FM_PART_VOLUME = 34
        internal const val FM_PART_SLOT_MASK = 35
        internal const val SSG_TONE_ENABLE = 36
        internal const val SSG_NOISE_ENABLE = 37
        internal const val SSG_NOISE_PERIOD = 38
        internal const val SSG_HARDWARE_ENVELOPE_PERIOD = 39
        internal const val SSG_HARDWARE_ENVELOPE_SHAPE = 40
        internal const val HW_LFO_ENABLE = 41
        internal const val HW_LFO_RATE = 42
        internal const val HW_LFO_PMS = 43
        internal const val HW_LFO_AMS = 44
        internal const val HW_LFO_DELAY = 45

        internal const val CONTROL_STRIDE = 8
    }
}

private const val ORDER_GLOBAL = 0
private const val ORDER_STATE = 1
private const val ORDER_KEY_OFF = 2
private const val ORDER_KEY_ON = 3
private const val ORDER_ZERO_GATE_OFF = 4

internal object CompiledOpnaTimelineFactory {
    fun build(song: CompiledOpnaSong, sampleRate: Int, mixGain: Float): CompiledOpnaTimeline {
        require(sampleRate > 0) { "Timeline sample rate must be positive" }
        var boundaryCount = 1 + song.tempoChangeCount
        var sourceIndex = 0
        while (sourceIndex < song.eventCount) {
            boundaryCount += when (song.eventType[sourceIndex]) {
                CompiledOpnaSong.RHYTHM_SHOT,
                CompiledOpnaSong.SSG_ENVELOPE_DEFINE,
                CompiledOpnaSong.SSG_ENVELOPE_MODE,
                CompiledOpnaSong.FM_PART_VOLUME,
                CompiledOpnaSong.FM_PART_SLOT_MASK,
                in CompiledOpnaSong.SSG_TONE_ENABLE..CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE,
                in CompiledOpnaSong.HW_LFO_ENABLE..CompiledOpnaSong.HW_LFO_DELAY,
                in CompiledOpnaSong.SOFTWARE_LFO_DEFINE..CompiledOpnaSong.SSG_DRUM_SHOT -> 1
                else -> 2
            }
            sourceIndex++
        }

        val draft = TimelineDraft(boundaryCount)
        draft.addControl(CompiledOpnaTimeline.TEMPO, 0L, ORDER_GLOBAL, -1, song.bpmMilli)
        var tempoIndex = 0
        while (tempoIndex < song.tempoChangeCount) {
            draft.addControl(
                CompiledOpnaTimeline.TEMPO,
                song.tempoTick[tempoIndex],
                ORDER_GLOBAL,
                -1,
                song.tempoBpmMilli[tempoIndex]
            )
            tempoIndex++
        }

        sourceIndex = 0
        while (sourceIndex < song.eventCount) {
            draft.beginSource(
                song.sourceOrder[sourceIndex],
                song.sourceLine[sourceIndex],
                song.sourceColumn[sourceIndex]
            )
            addSourceEvent(draft, song, sourceIndex, mixGain)
            sourceIndex++
        }
        return draft.build(song, sampleRate)
    }

    private fun addSourceEvent(draft: TimelineDraft, song: CompiledOpnaSong, sourceIndex: Int, mixGain: Float) {
        val sourceType = song.eventType[sourceIndex]
        val eventTick = song.startTick[sourceIndex]
        if (sourceType == CompiledOpnaSong.SSG_ENVELOPE_DEFINE) {
            draft.addEnvelope(song, sourceIndex)
            return
        }
        if (sourceType == CompiledOpnaSong.SSG_ENVELOPE_MODE) {
            draft.addControl(
                CompiledOpnaTimeline.SSG_ENVELOPE_MODE,
                eventTick,
                ORDER_STATE,
                song.channel[sourceIndex],
                song.envelopeClockMode[sourceIndex]
            )
            return
        }
        if (sourceType in CompiledOpnaSong.SSG_TONE_ENABLE..CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE) {
            draft.addSsgHardwareState(song, sourceIndex)
            return
        }
        if (sourceType in CompiledOpnaSong.HW_LFO_ENABLE..CompiledOpnaSong.HW_LFO_DELAY) {
            draft.addHardwareLfoState(song, sourceIndex)
            return
        }
        if (sourceType in CompiledOpnaSong.SOFTWARE_LFO_DEFINE..CompiledOpnaSong.SOFTWARE_LFO_DEPTH) {
            draft.addSoftwareLfo(song, sourceIndex)
            return
        }
        if (sourceType in CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE..CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY) {
            draft.addFmControl(song, sourceIndex)
            return
        }
        if (sourceType == CompiledOpnaSong.FM3_PATCH) {
            draft.addFm3Patch(song, sourceIndex)
            return
        }
        if (sourceType == CompiledOpnaSong.FM_PART_VOLUME ||
            sourceType == CompiledOpnaSong.FM_PART_SLOT_MASK
        ) {
            draft.addFmPartControl(song, sourceIndex)
            return
        }
        if (sourceType in CompiledOpnaSong.RHYTHM_CONTROL_SHOT..CompiledOpnaSong.RHYTHM_VOICE_PAN) {
            draft.addRhythmControl(song, sourceIndex)
            return
        }
        if (sourceType == CompiledOpnaSong.SSG_DRUM_SHOT) {
            val shotVelocity = song.velocity[sourceIndex].coerceIn(0, 127) / 127f * mixGain * song.playbackGain
            draft.addNote(
                CompiledOpnaTimeline.SSG_DRUM_SHOT, eventTick, ORDER_KEY_ON,
                0, -1, 0, song.midi[sourceIndex], shotVelocity, 0, -1, song.pan[sourceIndex],
                0, -1, 0L
            )
            return
        }
        val velocity = song.velocity[sourceIndex].coerceIn(0, 127) / 127f * mixGain * song.playbackGain
        if (sourceType == CompiledOpnaSong.RHYTHM_SHOT) {
            draft.addNote(
                CompiledOpnaTimeline.DRUM_SHOT, eventTick, ORDER_KEY_ON,
                0, -1, 0, song.midi[sourceIndex], velocity, 0, -1, song.pan[sourceIndex],
                0, -1, 0L
            )
            return
        }

        val onType: Int
        val offType: Int
        when (sourceType) {
            CompiledOpnaSong.FM_NOTE -> {
                onType = CompiledOpnaTimeline.FM_ON
                offType = CompiledOpnaTimeline.FM_OFF
            }
            CompiledOpnaSong.SSG_NOTE -> {
                onType = CompiledOpnaTimeline.SSG_ON
                offType = CompiledOpnaTimeline.SSG_OFF
            }
            CompiledOpnaSong.FM3_OPERATOR_NOTE -> {
                onType = CompiledOpnaTimeline.FM3_OPERATOR_ON
                offType = CompiledOpnaTimeline.FM3_OPERATOR_OFF
            }
            CompiledOpnaSong.FM_POLY_NOTE -> {
                onType = CompiledOpnaTimeline.FM_POLY_ON
                offType = CompiledOpnaTimeline.FM_POLY_OFF
            }
            else -> error("Unknown compiled OPNA source event $sourceType")
        }
        if (sourceType == CompiledOpnaSong.SSG_NOTE) {
            requireNotNull(song.instrumentBank.ssgPatch(song.patchId[sourceIndex]))
        } else {
            requireNotNull(song.instrumentBank.fmPatch(song.patchId[sourceIndex]))
        }
        val id = sourceIndex + 1
        draft.addNote(
            onType, eventTick, ORDER_KEY_ON, song.channel[sourceIndex], song.operator[sourceIndex], song.slotMask[sourceIndex],
            song.midi[sourceIndex], velocity, id, song.patchId[sourceIndex], song.pan[sourceIndex],
            song.detuneCents[sourceIndex], song.targetMidi[sourceIndex],
            eventTick + song.durationTick[sourceIndex], song.logicalPart[sourceIndex]
        )
        val gateEnd = eventTick + song.gateTick[sourceIndex]
        draft.addNote(
            offType, gateEnd, if (gateEnd == eventTick) ORDER_ZERO_GATE_OFF else ORDER_KEY_OFF,
            song.channel[sourceIndex], song.operator[sourceIndex], song.slotMask[sourceIndex], song.midi[sourceIndex],
            0f, id, -1, 0, 0, -1, gateEnd, song.logicalPart[sourceIndex]
        )
    }

}

private class TimelineDraft(private val capacity: Int) {
    private var size = 0
    private var currentSourceOrder = Int.MAX_VALUE
    private var currentSourceLine = 0
    private var currentSourceColumn = 0
    private val eventType = IntArray(capacity)
    private val tickTime = LongArray(capacity)
    private val eventOrder = IntArray(capacity)
    private val channel = IntArray(capacity)
    private val logicalPart = IntArray(capacity) { CompiledOpnaSong.LOGICAL_PART_NONE }
    private val operator = IntArray(capacity)
    private val slotMask = IntArray(capacity)
    private val midi = IntArray(capacity)
    private val velocity = FloatArray(capacity)
    private val noteId = IntArray(capacity)
    private val patchId = IntArray(capacity)
    private val pan = IntArray(capacity)
    private val detuneCents = IntArray(capacity)
    private val targetMidi = IntArray(capacity)
    private val slideEndTick = LongArray(capacity)
    private val controlValues = IntArray(capacity * CompiledOpnaTimeline.CONTROL_STRIDE)
    private val sourceOrder = IntArray(capacity) { Int.MAX_VALUE }
    private val sourceLine = IntArray(capacity)
    private val sourceColumn = IntArray(capacity)

    fun beginSource(authoredOrder: Int, line: Int, column: Int) {
        currentSourceOrder = authoredOrder
        currentSourceLine = line
        currentSourceColumn = column
    }

    fun addControl(type: Int, atTick: Long, order: Int, channelIndex: Int, value: Int) {
        val i = reserve(type, atTick, order, channelIndex)
        controlValues[i * CompiledOpnaTimeline.CONTROL_STRIDE] = value
    }

    fun addEnvelope(song: CompiledOpnaSong, source: Int) {
        val i = reserve(
            CompiledOpnaTimeline.SSG_ENVELOPE_DEFINE,
            song.startTick[source],
            ORDER_STATE,
            song.channel[source]
        )
        val base = i * CompiledOpnaTimeline.CONTROL_STRIDE
        controlValues[base] = song.envelopeFormat[source]
        controlValues[base + 1] = song.envelopeAttack[source]
        controlValues[base + 2] = song.envelopeDecay[source]
        controlValues[base + 3] = song.envelopeSustain[source]
        controlValues[base + 4] = song.envelopeRelease[source]
        controlValues[base + 5] = song.envelopeSustainLevel[source]
        controlValues[base + 6] = song.envelopeAttackLevel[source]
    }

    fun addSsgHardwareState(song: CompiledOpnaSong, source: Int) {
        val type = CompiledOpnaTimeline.SSG_TONE_ENABLE +
            (song.eventType[source] - CompiledOpnaSong.SSG_TONE_ENABLE)
        val i = reserve(
            type,
            song.startTick[source],
            ORDER_STATE,
            song.channel[source],
            song.sourceOrder[source],
            song.sourceLine[source],
            song.sourceColumn[source]
        )
        controlValues[i * CompiledOpnaTimeline.CONTROL_STRIDE] = song.stateValue[source]
    }

    fun addHardwareLfoState(song: CompiledOpnaSong, source: Int) {
        val type = CompiledOpnaTimeline.HW_LFO_ENABLE +
            (song.eventType[source] - CompiledOpnaSong.HW_LFO_ENABLE)
        val order = if (song.eventType[source] == CompiledOpnaSong.HW_LFO_ENABLE ||
            song.eventType[source] == CompiledOpnaSong.HW_LFO_RATE
        ) ORDER_GLOBAL else ORDER_STATE
        val i = reserve(
            type,
            song.startTick[source],
            order,
            song.channel[source],
            song.sourceOrder[source],
            song.sourceLine[source],
            song.sourceColumn[source]
        )
        logicalPart[i] = song.logicalPart[source]
        val base = i * CompiledOpnaTimeline.CONTROL_STRIDE
        if (song.eventType[source] == CompiledOpnaSong.HW_LFO_DELAY) {
            controlValues[base] = song.hardwareLfoDelayKind[source]
            controlValues[base + 1] = song.hardwareLfoDelayValue[source]
            controlValues[base + 2] = if (song.hardwareLfoDelayDotted[source]) 1 else 0
        } else {
            controlValues[base] = song.stateValue[source]
        }
    }

    fun addSoftwareLfo(song: CompiledOpnaSong, source: Int) {
        val timelineType = CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE +
            (song.eventType[source] - CompiledOpnaSong.SOFTWARE_LFO_DEFINE)
        val i = reserve(timelineType, song.startTick[source], ORDER_STATE, song.channel[source])
        operator[i] = song.operator[source]
        logicalPart[i] = song.logicalPart[source]
        val base = i * CompiledOpnaTimeline.CONTROL_STRIDE
        controlValues[base] = song.envelopeFormat[source]
        controlValues[base + 1] = song.envelopeAttack[source]
        controlValues[base + 2] = song.envelopeDecay[source]
        controlValues[base + 3] = song.envelopeSustain[source]
        controlValues[base + 4] = song.envelopeRelease[source]
    }

    fun addFmControl(song: CompiledOpnaSong, source: Int) {
        val timelineType = CompiledOpnaTimeline.FM_SLOT_DETUNE_ABSOLUTE +
            (song.eventType[source] - CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE)
        val i = reserve(timelineType, song.startTick[source], ORDER_STATE, song.channel[source])
        logicalPart[i] = song.logicalPart[source]
        slotMask[i] = song.slotMask[source]
        controlValues[i * CompiledOpnaTimeline.CONTROL_STRIDE] = song.envelopeAttack[source]
    }

    fun addFm3Patch(song: CompiledOpnaSong, source: Int) {
        val i = reserve(CompiledOpnaTimeline.FM3_PATCH, song.startTick[source], ORDER_STATE, 2)
        logicalPart[i] = song.logicalPart[source]
        slotMask[i] = song.slotMask[source]
        patchId[i] = song.patchId[source]
    }

    fun addFmPartControl(song: CompiledOpnaSong, source: Int) {
        val type = if (song.eventType[source] == CompiledOpnaSong.FM_PART_VOLUME) {
            CompiledOpnaTimeline.FM_PART_VOLUME
        } else {
            CompiledOpnaTimeline.FM_PART_SLOT_MASK
        }
        val i = reserve(type, song.startTick[source], ORDER_STATE, song.channel[source])
        logicalPart[i] = song.logicalPart[source]
        controlValues[i * CompiledOpnaTimeline.CONTROL_STRIDE] = song.envelopeAttack[source]
    }

    fun addRhythmControl(song: CompiledOpnaSong, source: Int) {
        val type = CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT +
            (song.eventType[source] - CompiledOpnaSong.RHYTHM_CONTROL_SHOT)
        val order = when (song.eventType[source]) {
            CompiledOpnaSong.RHYTHM_CONTROL_DUMP -> ORDER_KEY_OFF
            CompiledOpnaSong.RHYTHM_CONTROL_SHOT -> ORDER_KEY_ON
            else -> ORDER_STATE
        }
        val i = reserve(type, song.startTick[source], order, song.channel[source])
        slotMask[i] = song.midi[source]
        controlValues[i * CompiledOpnaTimeline.CONTROL_STRIDE] = song.envelopeAttack[source]
    }

    fun addNote(
        type: Int,
        atTick: Long,
        order: Int,
        channelIndex: Int,
        operatorIndex: Int,
        selectedSlotMask: Int,
        midiNote: Int,
        eventVelocity: Float,
        eventNoteId: Int,
        selectedPatchId: Int,
        selectedPan: Int,
        cents: Int,
        targetMidiNote: Int,
        portamentoEndTick: Long,
        logicalPartId: Int = CompiledOpnaSong.LOGICAL_PART_NONE
    ) {
        val i = reserve(type, atTick, order, channelIndex)
        operator[i] = operatorIndex
        logicalPart[i] = logicalPartId
        slotMask[i] = selectedSlotMask
        midi[i] = midiNote
        velocity[i] = eventVelocity
        noteId[i] = eventNoteId
        patchId[i] = selectedPatchId
        pan[i] = selectedPan
        detuneCents[i] = cents
        targetMidi[i] = targetMidiNote
        slideEndTick[i] = portamentoEndTick
    }

    private fun reserve(
        type: Int,
        atTick: Long,
        order: Int,
        channelIndex: Int,
        authoredOrder: Int = currentSourceOrder,
        line: Int = currentSourceLine,
        column: Int = currentSourceColumn
    ): Int {
        check(size < capacity) { "Timeline boundary count changed between passes" }
        val i = size++
        eventType[i] = type
        tickTime[i] = atTick
        eventOrder[i] = order
        channel[i] = channelIndex
        sourceOrder[i] = authoredOrder
        sourceLine[i] = line
        sourceColumn[i] = column
        return i
    }

    fun build(song: CompiledOpnaSong, sampleRate: Int): CompiledOpnaTimeline {
        check(size == capacity) { "Timeline boundary count changed between passes" }
        val order = sortedIndices()
        val sortedType = IntArray(size)
        val sortedTime = LongArray(size)
        val sortedChannel = IntArray(size)
        val sortedLogicalPart = IntArray(size)
        val sortedOperator = IntArray(size)
        val sortedSlotMask = IntArray(size)
        val sortedMidi = IntArray(size)
        val sortedVelocity = FloatArray(size)
        val sortedNoteId = IntArray(size)
        val sortedPatchId = IntArray(size)
        val sortedPan = IntArray(size)
        val sortedDetune = IntArray(size)
        val sortedTarget = IntArray(size)
        val sortedSlide = IntArray(size)
        val sortedControls = IntArray(size * CompiledOpnaTimeline.CONTROL_STRIDE)
        val sortedSourceOrder = IntArray(size)
        val sortedSourceLine = IntArray(size)
        val sortedSourceColumn = IntArray(size)
        var i = 0
        while (i < size) {
            val source = order[i]
            val startSample = PmdSampleClock.samplesAt(song, tickTime[source], sampleRate)
            sortedType[i] = eventType[source]
            sortedTime[i] = startSample
            sortedChannel[i] = channel[source]
            sortedLogicalPart[i] = logicalPart[source]
            sortedOperator[i] = operator[source]
            sortedSlotMask[i] = slotMask[source]
            sortedMidi[i] = midi[source]
            sortedVelocity[i] = velocity[source]
            sortedNoteId[i] = noteId[source]
            sortedPatchId[i] = patchId[source]
            sortedPan[i] = pan[source]
            sortedDetune[i] = detuneCents[source]
            sortedTarget[i] = targetMidi[source]
            sortedSourceOrder[i] = sourceOrder[source]
            sortedSourceLine[i] = sourceLine[source]
            sortedSourceColumn[i] = sourceColumn[source]
            sortedSlide[i] = if (isNoteOn(eventType[source])) {
                (PmdSampleClock.samplesAt(song, slideEndTick[source], sampleRate) - startSample).toInt()
            } else {
                0
            }
            var control = 0
            while (control < CompiledOpnaTimeline.CONTROL_STRIDE) {
                val raw = controlValues[source * CompiledOpnaTimeline.CONTROL_STRIDE + control]
                sortedControls[i * CompiledOpnaTimeline.CONTROL_STRIDE + control] =
                    if (control == 0 && eventType[source] == CompiledOpnaTimeline.FM_SLOT_KEY_ON_DELAY) {
                        (
                            PmdSampleClock.samplesAt(song, tickTime[source] + raw.toLong(), sampleRate) -
                                PmdSampleClock.samplesAt(song, tickTime[source], sampleRate)
                            ).toInt()
                    } else raw
                control++
            }
            i++
        }
        return CompiledOpnaTimeline(
            size, PmdSampleClock.samplesAt(song, song.durationTicks, sampleRate), song.pmdClocksPerQuarter, song.instrumentBank,
            sortedType, sortedTime, sortedChannel, sortedLogicalPart, sortedOperator, sortedSlotMask, sortedMidi, sortedVelocity,
            sortedNoteId, sortedPatchId, sortedPan, sortedDetune,
            sortedTarget, sortedSlide, sortedControls,
            sortedSourceOrder, sortedSourceLine, sortedSourceColumn
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

    private fun isNoteOn(type: Int): Boolean =
        type == CompiledOpnaTimeline.FM_ON || type == CompiledOpnaTimeline.SSG_ON ||
            type == CompiledOpnaTimeline.FM3_OPERATOR_ON || type == CompiledOpnaTimeline.FM_POLY_ON

    private fun comesBefore(first: Int, second: Int): Boolean {
        if (tickTime[first] != tickTime[second]) return tickTime[first] < tickTime[second]
        if (eventOrder[first] != eventOrder[second]) return eventOrder[first] < eventOrder[second]
        if (sourceOrder[first] != sourceOrder[second]) return sourceOrder[first] < sourceOrder[second]
        return first < second
    }
}
