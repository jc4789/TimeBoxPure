package com.example.timeboxvibe.engine.audio.opna

/**
 * Renderer-facing, allocation-free song program.
 *
 * MML is only one possible producer. Playback consumes primitive arrays so the
 * synthesizer remains independent from parser and catalog types.
 */
class CompiledOpnaSong internal constructor(
    val dialectVersion: Int,
    val bpm: Float,
    internal val bpmMilli: Int,
    val beatsPerBar: Int,
    internal val pmdClocksPerQuarter: Int,
    val lfoRate: Int,
    val fm3Extended: Boolean,
    internal val instrumentBank: CompiledInstrumentBank,
    internal val tempoChangeCount: Int,
    internal val tempoTick: LongArray,
    internal val tempoBpm: FloatArray,
    internal val tempoBpmMilli: IntArray,
    val durationTicks: Long,
    internal val eventCount: Int,
    internal val eventType: IntArray,
    internal val startTick: LongArray,
    internal val durationTick: IntArray,
    internal val gateTick: IntArray,
    internal val channel: IntArray,
    internal val logicalPart: IntArray,
    internal val operator: IntArray,
    internal val slotMask: IntArray,
    internal val midi: IntArray,
    internal val targetMidi: IntArray,
    internal val velocity: IntArray,
    internal val patchId: IntArray,
    internal val pan: IntArray,
    internal val detuneCents: IntArray,
    internal val gateValue: IntArray,
    internal val gateScale: IntArray,
    internal val gateTailClocks: IntArray,
    internal val gateMinimumClocks: IntArray,
    internal val envelopeFormat: IntArray,
    internal val envelopeAttack: IntArray,
    internal val envelopeDecay: IntArray,
    internal val envelopeSustain: IntArray,
    internal val envelopeRelease: IntArray,
    internal val envelopeSustainLevel: IntArray,
    internal val envelopeAttackLevel: IntArray,
    internal val envelopeClockMode: IntArray,
    internal val stateValue: IntArray,
    internal val hardwareLfoDelayKind: IntArray,
    internal val hardwareLfoDelayValue: IntArray,
    internal val hardwareLfoDelayDotted: BooleanArray,
    internal val sourceOrder: IntArray,
    internal val sourceLine: IntArray,
    internal val sourceColumn: IntArray,
    internal val playbackGain: Float = 1f
) {
    internal fun withPlaybackGain(gain: Float): CompiledOpnaSong {
        if (gain == playbackGain) return this
        return CompiledOpnaSong(
            dialectVersion = dialectVersion,
            bpm = bpm,
            bpmMilli = bpmMilli,
            beatsPerBar = beatsPerBar,
            pmdClocksPerQuarter = pmdClocksPerQuarter,
            lfoRate = lfoRate,
            fm3Extended = fm3Extended,
            instrumentBank = instrumentBank,
            tempoChangeCount = tempoChangeCount,
            tempoTick = tempoTick,
            tempoBpm = tempoBpm,
            tempoBpmMilli = tempoBpmMilli,
            durationTicks = durationTicks,
            eventCount = eventCount,
            eventType = eventType,
            startTick = startTick,
            durationTick = durationTick,
            gateTick = gateTick,
            channel = channel,
            logicalPart = logicalPart,
            operator = operator,
            slotMask = slotMask,
            midi = midi,
            targetMidi = targetMidi,
            velocity = velocity,
            patchId = patchId,
            pan = pan,
            detuneCents = detuneCents,
            gateValue = gateValue,
            gateScale = gateScale,
            gateTailClocks = gateTailClocks,
            gateMinimumClocks = gateMinimumClocks,
            envelopeFormat = envelopeFormat,
            envelopeAttack = envelopeAttack,
            envelopeDecay = envelopeDecay,
            envelopeSustain = envelopeSustain,
            envelopeRelease = envelopeRelease,
            envelopeSustainLevel = envelopeSustainLevel,
            envelopeAttackLevel = envelopeAttackLevel,
            envelopeClockMode = envelopeClockMode,
            stateValue = stateValue,
            hardwareLfoDelayKind = hardwareLfoDelayKind,
            hardwareLfoDelayValue = hardwareLfoDelayValue,
            hardwareLfoDelayDotted = hardwareLfoDelayDotted,
            sourceOrder = sourceOrder,
            sourceLine = sourceLine,
            sourceColumn = sourceColumn,
            playbackGain = gain.coerceAtLeast(0f)
        )
    }

    fun durationMilliseconds(): Long {
        return PmdSampleClock.samplesAt(this, durationTicks, 1_000)
    }

    companion object {
        const val TICKS_PER_QUARTER: Int = 480

        // Abuse guard only. Storage grows during setup and is trimmed exactly
        // at build time; this is not a playback or normal-song capacity.
        const val MAX_AUTHORED_EVENTS: Int = 262_144

        internal const val FM_NOTE: Int = 0
        internal const val SSG_NOTE: Int = 1
        internal const val RHYTHM_SHOT: Int = 2
        internal const val FM3_OPERATOR_NOTE: Int = 3
        internal const val FM_POLY_NOTE: Int = 4
        internal const val SSG_ENVELOPE_DEFINE: Int = 5
        internal const val SSG_ENVELOPE_MODE: Int = 6
        internal const val SOFTWARE_LFO_DEFINE: Int = 7
        internal const val SOFTWARE_LFO_SWITCH: Int = 8
        internal const val SOFTWARE_LFO_WAVE: Int = 9
        internal const val SOFTWARE_LFO_CLOCK: Int = 10
        internal const val SOFTWARE_LFO_TL_MASK: Int = 11
        internal const val SOFTWARE_LFO_DEPTH: Int = 12
        internal const val FM_SLOT_DETUNE_ABSOLUTE: Int = 13
        internal const val FM_SLOT_DETUNE_RELATIVE: Int = 14
        internal const val FM_TL_ABSOLUTE: Int = 15
        internal const val FM_TL_RELATIVE: Int = 16
        internal const val FM_FEEDBACK_ABSOLUTE: Int = 17
        internal const val FM_FEEDBACK_RELATIVE: Int = 18
        internal const val FM_SLOT_KEY_ON_DELAY: Int = 19
        internal const val FM3_PATCH: Int = 20
        internal const val RHYTHM_CONTROL_SHOT: Int = 21
        internal const val RHYTHM_CONTROL_DUMP: Int = 22
        internal const val RHYTHM_MASTER_ABSOLUTE: Int = 23
        internal const val RHYTHM_MASTER_RELATIVE: Int = 24
        internal const val RHYTHM_VOICE_LEVEL_ABSOLUTE: Int = 25
        internal const val RHYTHM_VOICE_LEVEL_RELATIVE: Int = 26
        internal const val RHYTHM_VOICE_PAN: Int = 27
        internal const val SSG_DRUM_SHOT: Int = 28
        internal const val FM_PART_VOLUME: Int = 29
        internal const val FM_PART_SLOT_MASK: Int = 30
        internal const val SSG_TONE_ENABLE: Int = 31
        internal const val SSG_NOISE_ENABLE: Int = 32
        internal const val SSG_NOISE_PERIOD: Int = 33
        internal const val SSG_HARDWARE_ENVELOPE_PERIOD: Int = 34
        internal const val SSG_HARDWARE_ENVELOPE_SHAPE: Int = 35
        internal const val HW_LFO_ENABLE: Int = 36
        internal const val HW_LFO_RATE: Int = 37
        internal const val HW_LFO_PMS: Int = 38
        internal const val HW_LFO_AMS: Int = 39
        internal const val HW_LFO_DELAY: Int = 40

        internal const val HW_LFO_DELAY_NONE: Int = 0
        internal const val HW_LFO_DELAY_RAW_CLOCKS: Int = 1
        internal const val HW_LFO_DELAY_NOTE_LENGTH: Int = 2

        internal const val LOGICAL_PART_NONE: Int = -1
        internal const val FM3_PART_BASE: Int = 6
        internal const val FM3_PART_COUNT: Int = 4
    }
}

internal class CompiledOpnaSongBuilder(
    private val dialectVersion: Int,
    private val bpm: Float,
    private val bpmMilli: Int,
    private val beatsPerBar: Int,
    private val pmdClocksPerQuarter: Int,
    private val lfoRate: Int,
    private val fm3Extended: Boolean,
    sourceInstruments: SourceInstrumentLookup
) {
    private val instrumentBankBuilder = CompiledInstrumentBankBuilder(
        source = sourceInstruments,
        maxFmPatches = MAX_COMPILED_FM_PATCHES,
        maxSsgPatches = MAX_COMPILED_SSG_PATCHES
    )
    private var tempoTick = LongArray(INITIAL_TEMPO_CAPACITY)
    private var tempoBpm = FloatArray(INITIAL_TEMPO_CAPACITY)
    private var tempoBpmMilli = IntArray(INITIAL_TEMPO_CAPACITY)
    private var tempoChangeCount = 0
    private var eventType = IntArray(INITIAL_EVENT_CAPACITY)
    private var startTick = LongArray(INITIAL_EVENT_CAPACITY)
    private var durationTick = IntArray(INITIAL_EVENT_CAPACITY)
    private var gateTick = IntArray(INITIAL_EVENT_CAPACITY)
    private var channel = IntArray(INITIAL_EVENT_CAPACITY)
    private var logicalPart = IntArray(INITIAL_EVENT_CAPACITY) { CompiledOpnaSong.LOGICAL_PART_NONE }
    private var operator = IntArray(INITIAL_EVENT_CAPACITY)
    private var slotMask = IntArray(INITIAL_EVENT_CAPACITY)
    private var midi = IntArray(INITIAL_EVENT_CAPACITY)
    private var targetMidi = IntArray(INITIAL_EVENT_CAPACITY)
    private var velocity = IntArray(INITIAL_EVENT_CAPACITY)
    private var patchId = IntArray(INITIAL_EVENT_CAPACITY)
    private var pan = IntArray(INITIAL_EVENT_CAPACITY)
    private var detuneCents = IntArray(INITIAL_EVENT_CAPACITY)
    private var gateValue = IntArray(INITIAL_EVENT_CAPACITY)
    private var gateScale = IntArray(INITIAL_EVENT_CAPACITY)
    private var gateTailClocks = IntArray(INITIAL_EVENT_CAPACITY)
    private var gateMinimumClocks = IntArray(INITIAL_EVENT_CAPACITY)
    private var envelopeFormat = IntArray(INITIAL_EVENT_CAPACITY)
    private var envelopeAttack = IntArray(INITIAL_EVENT_CAPACITY)
    private var envelopeDecay = IntArray(INITIAL_EVENT_CAPACITY)
    private var envelopeSustain = IntArray(INITIAL_EVENT_CAPACITY)
    private var envelopeRelease = IntArray(INITIAL_EVENT_CAPACITY)
    private var envelopeSustainLevel = IntArray(INITIAL_EVENT_CAPACITY)
    private var envelopeAttackLevel = IntArray(INITIAL_EVENT_CAPACITY)
    private var envelopeClockMode = IntArray(INITIAL_EVENT_CAPACITY)
    private var stateValue = IntArray(INITIAL_EVENT_CAPACITY)
    private var hardwareLfoDelayKind = IntArray(INITIAL_EVENT_CAPACITY)
    private var hardwareLfoDelayValue = IntArray(INITIAL_EVENT_CAPACITY)
    private var hardwareLfoDelayDotted = BooleanArray(INITIAL_EVENT_CAPACITY)
    private var sourceOrder = IntArray(INITIAL_EVENT_CAPACITY)
    private var sourceLine = IntArray(INITIAL_EVENT_CAPACITY)
    private var sourceColumn = IntArray(INITIAL_EVENT_CAPACITY)
    private var currentSourceOrder = -1
    private var currentSourceLine = 0
    private var currentSourceColumn = 0

    var size: Int = 0
        private set
    var durationTicks: Long = 0L
        private set

    fun internFmPatch(sourceId: Int): Int = instrumentBankBuilder.internFm(sourceId)

    fun internSsgPatch(sourceId: Int): Int = instrumentBankBuilder.internSsg(sourceId)

    fun beginSource(authoredOrder: Int, line: Int, column: Int) {
        currentSourceOrder = authoredOrder
        currentSourceLine = line
        currentSourceColumn = column
    }

    fun addTempo(atTick: Long, bpm: Float, milliBpm: Int): Boolean {
        var position = 0
        while (position < tempoChangeCount && tempoTick[position] < atTick) position++
        if (position < tempoChangeCount && tempoTick[position] == atTick) {
            tempoBpm[position] = bpm
            tempoBpmMilli[position] = milliBpm
            return true
        }
        if (tempoChangeCount >= MAX_TEMPO_CHANGES) return false
        ensureTempoCapacity(tempoChangeCount + 1)
        var move = tempoChangeCount
        while (move > position) {
            tempoTick[move] = tempoTick[move - 1]
            tempoBpm[move] = tempoBpm[move - 1]
            tempoBpmMilli[move] = tempoBpmMilli[move - 1]
            move--
        }
        tempoTick[position] = atTick
        tempoBpm[position] = bpm
        tempoBpmMilli[position] = milliBpm
        tempoChangeCount++
        return true
    }

    fun add(
        type: Int,
        atTick: Long,
        duration: Int,
        gate: Int,
        channelIndex: Int,
        operatorIndex: Int,
        midiNote: Int,
        targetMidiNote: Int,
        fineVelocity: Int,
        selectedPatchId: Int,
        selectedPan: Int,
        cents: Int,
        selectedGateValue: Int = 0,
        selectedGateScale: Int = 8,
        selectedGateTailClocks: Int = 0,
        selectedGateMinimumClocks: Int = 0,
        selectedSlotMask: Int = 0,
        selectedLogicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE,
        selectedSourceOrder: Int = SOURCE_ORDER_UNSET,
        selectedSourceLine: Int = 0,
        selectedSourceColumn: Int = 0
    ): Boolean {
        if (size >= CompiledOpnaSong.MAX_AUTHORED_EVENTS) return false
        ensureEventCapacity(size + 1)
        val i = size
        eventType[i] = type
        startTick[i] = atTick
        durationTick[i] = duration
        gateTick[i] = gate
        channel[i] = channelIndex
        logicalPart[i] = selectedLogicalPart
        operator[i] = operatorIndex
        slotMask[i] = selectedSlotMask
        midi[i] = midiNote
        targetMidi[i] = targetMidiNote
        velocity[i] = fineVelocity
        patchId[i] = selectedPatchId
        pan[i] = selectedPan
        detuneCents[i] = cents
        gateValue[i] = selectedGateValue
        gateScale[i] = selectedGateScale
        gateTailClocks[i] = selectedGateTailClocks
        gateMinimumClocks[i] = selectedGateMinimumClocks
        sourceOrder[i] = if (selectedSourceOrder != SOURCE_ORDER_UNSET) {
            selectedSourceOrder
        } else if (currentSourceOrder >= 0) {
            currentSourceOrder
        } else {
            i
        }
        sourceLine[i] = if (selectedSourceLine > 0) selectedSourceLine else currentSourceLine
        sourceColumn[i] = if (selectedSourceColumn > 0) selectedSourceColumn else currentSourceColumn
        size++
        val end = atTick + duration.toLong()
        if (end > durationTicks) durationTicks = end
        return true
    }

    fun addSsgEnvelopeDefinition(
        atTick: Long,
        channelIndex: Int,
        format: Int,
        attack: Int,
        decay: Int,
        sustain: Int,
        release: Int,
        sustainLevel: Int,
        attackLevel: Int
    ): Boolean {
        if (!add(CompiledOpnaSong.SSG_ENVELOPE_DEFINE, atTick, 0, 0, channelIndex, -1, 0, -1, 0, -1, 0, 0)) return false
        val i = size - 1
        envelopeFormat[i] = format
        envelopeAttack[i] = attack
        envelopeDecay[i] = decay
        envelopeSustain[i] = sustain
        envelopeRelease[i] = release
        envelopeSustainLevel[i] = sustainLevel
        envelopeAttackLevel[i] = attackLevel
        return true
    }

    fun addSsgEnvelopeMode(atTick: Long, channelIndex: Int, mode: Int): Boolean {
        if (!add(CompiledOpnaSong.SSG_ENVELOPE_MODE, atTick, 0, 0, channelIndex, -1, 0, -1, 0, -1, 0, 0)) return false
        envelopeClockMode[size - 1] = mode
        return true
    }

    fun addSsgHardwareState(
        type: Int,
        atTick: Long,
        channelIndex: Int,
        value: Int,
        authoredOrder: Int,
        line: Int,
        column: Int
    ): Boolean {
        if (!add(
                type, atTick, 0, 0, channelIndex, -1, 0, -1, 0, -1, 0, 0,
                selectedSourceOrder = authoredOrder,
                selectedSourceLine = line,
                selectedSourceColumn = column
            )
        ) return false
        stateValue[size - 1] = value
        return true
    }

    fun addHardwareLfoGlobalControl(
        type: Int,
        atTick: Long,
        value: Int,
        authoredOrder: Int,
        line: Int,
        column: Int
    ): Boolean = addHardwareLfoControl(
        type, atTick, -1, CompiledOpnaSong.LOGICAL_PART_NONE, value,
        CompiledOpnaSong.HW_LFO_DELAY_NONE, 0, false,
        authoredOrder, line, column
    )

    fun addHardwareLfoPartControl(
        type: Int,
        atTick: Long,
        channelIndex: Int,
        logicalPartId: Int,
        value: Int,
        delayKind: Int,
        delayValue: Int,
        delayDotted: Boolean,
        authoredOrder: Int,
        line: Int,
        column: Int
    ): Boolean = addHardwareLfoControl(
        type, atTick, channelIndex, logicalPartId, value,
        delayKind, delayValue, delayDotted,
        authoredOrder, line, column
    )

    private fun addHardwareLfoControl(
        type: Int,
        atTick: Long,
        channelIndex: Int,
        logicalPartId: Int,
        value: Int,
        delayKind: Int,
        delayValue: Int,
        delayDotted: Boolean,
        authoredOrder: Int,
        line: Int,
        column: Int
    ): Boolean {
        if (!add(
                type, atTick, 0, 0, channelIndex, -1, 0, -1, 0, -1, 0, 0,
                selectedLogicalPart = logicalPartId,
                selectedSourceOrder = authoredOrder,
                selectedSourceLine = line,
                selectedSourceColumn = column
            )
        ) return false
        val i = size - 1
        stateValue[i] = value
        hardwareLfoDelayKind[i] = delayKind
        hardwareLfoDelayValue[i] = delayValue
        hardwareLfoDelayDotted[i] = delayDotted
        return true
    }

    fun addSoftwareLfoControl(
        type: Int,
        atTick: Long,
        channelIndex: Int,
        partFamily: Int,
        lfoIndex: Int,
        value1: Int,
        value2: Int = 0,
        value3: Int = 0,
        value4: Int = 0,
        logicalPartId: Int = CompiledOpnaSong.LOGICAL_PART_NONE
    ): Boolean {
        if (!add(type, atTick, 0, 0, channelIndex, partFamily, 0, -1, 0, -1, 0, 0,
                selectedLogicalPart = logicalPartId)) return false
        val i = size - 1
        envelopeFormat[i] = lfoIndex
        envelopeAttack[i] = value1
        envelopeDecay[i] = value2
        envelopeSustain[i] = value3
        envelopeRelease[i] = value4
        return true
    }

    fun addFmControl(
        type: Int, atTick: Long, channelIndex: Int, mask: Int, value: Int,
        logicalPartId: Int = CompiledOpnaSong.LOGICAL_PART_NONE
    ): Boolean {
        if (!add(type, atTick, 0, 0, channelIndex, -1, 0, -1, 0, -1, 0, 0,
                selectedLogicalPart = logicalPartId)) return false
        val i = size - 1
        slotMask[i] = mask
        envelopeAttack[i] = value
        return true
    }

    fun addFm3Patch(
        atTick: Long, mask: Int, selectedPatchId: Int,
        logicalPartId: Int = CompiledOpnaSong.LOGICAL_PART_NONE
    ): Boolean {
        if (!add(CompiledOpnaSong.FM3_PATCH, atTick, 0, 0, 2, -1, 0, -1, 0, selectedPatchId, 0, 0,
                selectedLogicalPart = logicalPartId)) return false
        slotMask[size - 1] = mask
        return true
    }

    fun addFmPartControl(type: Int, atTick: Long, logicalPartId: Int, value: Int): Boolean {
        if (!add(type, atTick, 0, 0, 2, -1, 0, -1, 0, -1, 0, 0,
                selectedLogicalPart = logicalPartId)) return false
        envelopeAttack[size - 1] = value
        return true
    }

    fun addRhythmControl(type: Int, atTick: Long, voice: Int, mask: Int, value: Int): Boolean {
        if (!add(type, atTick, 0, 0, voice, -1, mask, -1, 0, -1, 0, 0)) return false
        envelopeAttack[size - 1] = value
        return true
    }

    fun build(): CompiledOpnaSong = CompiledOpnaSong(
        dialectVersion = dialectVersion,
        bpm = bpm,
        bpmMilli = bpmMilli,
        beatsPerBar = beatsPerBar,
        pmdClocksPerQuarter = pmdClocksPerQuarter,
        lfoRate = lfoRate,
        fm3Extended = fm3Extended,
        instrumentBank = instrumentBankBuilder.build(),
        tempoChangeCount = tempoChangeCount,
        tempoTick = tempoTick.copyOf(tempoChangeCount),
        tempoBpm = tempoBpm.copyOf(tempoChangeCount),
        tempoBpmMilli = tempoBpmMilli.copyOf(tempoChangeCount),
        durationTicks = durationTicks,
        eventCount = size,
        eventType = eventType.copyOf(size),
        startTick = startTick.copyOf(size),
        durationTick = durationTick.copyOf(size),
        gateTick = gateTick.copyOf(size),
        channel = channel.copyOf(size),
        logicalPart = logicalPart.copyOf(size),
        operator = operator.copyOf(size),
        slotMask = slotMask.copyOf(size),
        midi = midi.copyOf(size),
        targetMidi = targetMidi.copyOf(size),
        velocity = velocity.copyOf(size),
        patchId = patchId.copyOf(size),
        pan = pan.copyOf(size),
        detuneCents = detuneCents.copyOf(size),
        gateValue = gateValue.copyOf(size),
        gateScale = gateScale.copyOf(size),
        gateTailClocks = gateTailClocks.copyOf(size),
        gateMinimumClocks = gateMinimumClocks.copyOf(size),
        envelopeFormat = envelopeFormat.copyOf(size),
        envelopeAttack = envelopeAttack.copyOf(size),
        envelopeDecay = envelopeDecay.copyOf(size),
        envelopeSustain = envelopeSustain.copyOf(size),
        envelopeRelease = envelopeRelease.copyOf(size),
        envelopeSustainLevel = envelopeSustainLevel.copyOf(size),
        envelopeAttackLevel = envelopeAttackLevel.copyOf(size),
        envelopeClockMode = envelopeClockMode.copyOf(size),
        stateValue = stateValue.copyOf(size),
        hardwareLfoDelayKind = hardwareLfoDelayKind.copyOf(size),
        hardwareLfoDelayValue = hardwareLfoDelayValue.copyOf(size),
        hardwareLfoDelayDotted = hardwareLfoDelayDotted.copyOf(size),
        sourceOrder = sourceOrder.copyOf(size),
        sourceLine = sourceLine.copyOf(size),
        sourceColumn = sourceColumn.copyOf(size),
        playbackGain = 1f
    )

    private fun ensureTempoCapacity(required: Int) {
        if (required <= tempoTick.size) return
        val next = minOf(MAX_TEMPO_CHANGES, tempoTick.size * 2)
        tempoTick = tempoTick.copyOf(next)
        tempoBpm = tempoBpm.copyOf(next)
        tempoBpmMilli = tempoBpmMilli.copyOf(next)
    }

    private fun ensureEventCapacity(required: Int) {
        if (required <= eventType.size) return
        val next = minOf(CompiledOpnaSong.MAX_AUTHORED_EVENTS, eventType.size * 2)
        eventType = eventType.copyOf(next)
        startTick = startTick.copyOf(next)
        durationTick = durationTick.copyOf(next)
        gateTick = gateTick.copyOf(next)
        channel = channel.copyOf(next)
        val oldLogicalSize = logicalPart.size
        logicalPart = logicalPart.copyOf(next)
        logicalPart.fill(CompiledOpnaSong.LOGICAL_PART_NONE, oldLogicalSize, next)
        operator = operator.copyOf(next)
        slotMask = slotMask.copyOf(next)
        midi = midi.copyOf(next)
        targetMidi = targetMidi.copyOf(next)
        velocity = velocity.copyOf(next)
        patchId = patchId.copyOf(next)
        pan = pan.copyOf(next)
        detuneCents = detuneCents.copyOf(next)
        gateValue = gateValue.copyOf(next)
        gateScale = gateScale.copyOf(next)
        gateTailClocks = gateTailClocks.copyOf(next)
        gateMinimumClocks = gateMinimumClocks.copyOf(next)
        envelopeFormat = envelopeFormat.copyOf(next)
        envelopeAttack = envelopeAttack.copyOf(next)
        envelopeDecay = envelopeDecay.copyOf(next)
        envelopeSustain = envelopeSustain.copyOf(next)
        envelopeRelease = envelopeRelease.copyOf(next)
        envelopeSustainLevel = envelopeSustainLevel.copyOf(next)
        envelopeAttackLevel = envelopeAttackLevel.copyOf(next)
        envelopeClockMode = envelopeClockMode.copyOf(next)
        stateValue = stateValue.copyOf(next)
        hardwareLfoDelayKind = hardwareLfoDelayKind.copyOf(next)
        hardwareLfoDelayValue = hardwareLfoDelayValue.copyOf(next)
        hardwareLfoDelayDotted = hardwareLfoDelayDotted.copyOf(next)
        sourceOrder = sourceOrder.copyOf(next)
        sourceLine = sourceLine.copyOf(next)
        sourceColumn = sourceColumn.copyOf(next)
    }

    private companion object {
        const val INITIAL_EVENT_CAPACITY = 256
        const val INITIAL_TEMPO_CAPACITY = 8
        const val MAX_TEMPO_CHANGES = 4_096
        // PMD instrument selectors are one-byte domains; final banks are trimmed
        // to the exact used counts, while these setup arrays bound malformed input.
        const val MAX_COMPILED_FM_PATCHES = 256
        const val MAX_COMPILED_SSG_PATCHES = 256
        const val SOURCE_ORDER_UNSET = Int.MAX_VALUE
    }
}
