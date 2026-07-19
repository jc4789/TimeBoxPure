package com.example.timeboxvibe.engine.audio.opna

/** Immutable tick-domain program. Each semantic shape owns one exact primitive payload table. */
class CompiledOpnaSong internal constructor(
    val bpm: Float,
    internal val bpmMilli: Int,
    val beatsPerBar: Int,
    internal val pmdClocksPerQuarter: Int,
    val lfoRate: Int,
    val fm3Extended: Boolean,
    internal val instrumentBank: CompiledInstrumentBank,
    internal val tempoChangeCount: Int,
    private val tempoTicks: LongArray,
    private val tempoBpms: FloatArray,
    private val tempoBpmMillis: IntArray,
    val durationTicks: Long,
    internal val notes: NotePayloadTable,
    internal val states: StatePayloadTables,
    internal val modulations: ModulationPayloadTables,
    internal val rhythms: RhythmPayloadTables,
    private val authoredKinds: IntArray,
    private val authoredPayloadIndices: IntArray,
    private val authoredSourceOrders: IntArray,
    private val authoredSourceLines: IntArray,
    private val authoredSourceColumns: IntArray
) {
    internal val eventCount: Int get() = authoredKinds.size

    internal fun tempoTick(index: Int): Long = tempoTicks[index]
    internal fun tempoBpm(index: Int): Float = tempoBpms[index]
    internal fun tempoBpmMilli(index: Int): Int = tempoBpmMillis[index]
    internal fun authoredPayloadIndex(index: Int): Int = authoredPayloadIndices[index]
    internal fun sourceOrder(index: Int): Int = authoredSourceOrders[index]
    internal fun sourceLine(index: Int): Int = authoredSourceLines[index]
    internal fun sourceColumn(index: Int): Int = authoredSourceColumns[index]
    internal fun authoredKind(index: Int): Int = authoredKinds[index]

    internal fun authoredStartTick(index: Int): Long {
        val kind = authoredKinds[index]
        val payload = authoredPayloadIndices[index]
        return when {
            isNoteKind(kind) -> notes.startTick(payload)
            isStateKind(kind) -> states.startTick(kind, payload)
            isModulationKind(kind) -> modulations.startTick(kind, payload)
            isRhythmKind(kind) -> rhythms.startTick(kind, payload)
            else -> error("Unknown authored event kind $kind")
        }
    }

    internal fun eventChannel(index: Int): Int {
        val kind = authoredKinds[index]
        val payload = authoredPayloadIndices[index]
        return when {
            isNoteKind(kind) -> notes.channel(payload)
            isStateKind(kind) -> states.channel(kind, payload)
            isModulationKind(kind) -> modulations.channel(kind, payload)
            isRhythmKind(kind) -> rhythms.voice(kind, payload)
            else -> -1
        }
    }

    internal fun eventLogicalPart(index: Int): Int {
        val kind = authoredKinds[index]
        val payload = authoredPayloadIndices[index]
        return when {
            isNoteKind(kind) -> notes.logicalPart(payload)
            isModulationKind(kind) -> modulations.logicalPart(kind, payload)
            else -> LOGICAL_PART_NONE
        }
    }

    internal fun eventSlotMask(index: Int): Int {
        val kind = authoredKinds[index]
        val payload = authoredPayloadIndices[index]
        return when (kind) {
            FM_NOTE, SSG_NOTE, FM3_OPERATOR_NOTE -> notes.slotMask(payload)
            FM3_PATCH -> modulations.fm3Patches.slotMask(payload)
            FM_PART_SLOT_MASK -> modulations.fmPartSlotMasks.slotMask(payload)
            FM_SLOT_DETUNE_ABSOLUTE, FM_SLOT_DETUNE_RELATIVE -> modulations.fmSlotDetunes.slotMask(payload)
            FM_TL_ABSOLUTE, FM_TL_RELATIVE -> modulations.fmTotalLevels.slotMask(payload)
            FM_SLOT_KEY_ON_DELAY -> modulations.fmKeyOnDelays.slotMask(payload)
            SOFTWARE_LFO_TL_MASK -> modulations.softwareLfoTlMasks.slotMask(payload)
            else -> 0
        }
    }

    internal fun eventDurationTick(index: Int): Int =
        if (isNoteKind(authoredKinds[index])) notes.durationTick(authoredPayloadIndices[index]) else 0

    internal fun eventGateTick(index: Int): Int =
        if (isNoteKind(authoredKinds[index])) notes.gateTick(authoredPayloadIndices[index]) else 0

    internal fun eventMidi(index: Int): Int =
        if (isNoteKind(authoredKinds[index])) notes.midi(authoredPayloadIndices[index]) else 0

    internal fun eventPercussionKind(index: Int): Int =
        if (authoredKinds[index] == RHYTHM_SHOT || authoredKinds[index] == SSG_DRUM_SHOT)
            rhythms.percussionShots.drumKind(authoredPayloadIndices[index]) else -1

    internal fun eventTargetMidi(index: Int): Int =
        if (isNoteKind(authoredKinds[index])) notes.targetMidi(authoredPayloadIndices[index]) else -1

    internal fun eventPercussionVelocity(index: Int): Int = when (authoredKinds[index]) {
        RHYTHM_SHOT, SSG_DRUM_SHOT -> rhythms.percussionShots.velocity(authoredPayloadIndices[index])
        else -> 0
    }

    internal fun eventPatchId(index: Int): Int = when (authoredKinds[index]) {
        FM_NOTE, SSG_NOTE, FM3_OPERATOR_NOTE -> notes.patchId(authoredPayloadIndices[index])
        FM3_PATCH -> modulations.fm3Patches.patchId(authoredPayloadIndices[index])
        else -> -1
    }

    internal fun hasExactAuthoredStorage(): Boolean = authoredPayloadIndices.size == eventCount &&
        authoredSourceOrders.size == eventCount && authoredSourceLines.size == eventCount &&
        authoredSourceColumns.size == eventCount

    fun durationMilliseconds(): Long = PmdSampleClock.samplesAt(this, durationTicks, 1_000)

    internal companion object {
        const val TICKS_PER_QUARTER = 480
        const val MAX_AUTHORED_EVENTS = 262_144

        const val FM_NOTE = 0
        const val SSG_NOTE = 1
        const val RHYTHM_SHOT = 2
        const val FM3_OPERATOR_NOTE = 3
        const val SSG_ENVELOPE_DEFINE = 5
        const val SSG_ENVELOPE_MODE = 6
        const val SOFTWARE_LFO_DEFINE = 7
        const val SOFTWARE_LFO_SWITCH = 8
        const val SOFTWARE_LFO_WAVE = 9
        const val SOFTWARE_LFO_CLOCK = 10
        const val SOFTWARE_LFO_TL_MASK = 11
        const val SOFTWARE_LFO_DEPTH = 12
        const val FM_SLOT_DETUNE_ABSOLUTE = 13
        const val FM_SLOT_DETUNE_RELATIVE = 14
        const val FM_TL_ABSOLUTE = 15
        const val FM_TL_RELATIVE = 16
        const val FM_FEEDBACK_ABSOLUTE = 17
        const val FM_FEEDBACK_RELATIVE = 18
        const val FM_SLOT_KEY_ON_DELAY = 19
        const val FM3_PATCH = 20
        const val RHYTHM_CONTROL_SHOT = 21
        const val RHYTHM_CONTROL_DUMP = 22
        const val RHYTHM_MASTER_ABSOLUTE = 23
        const val RHYTHM_MASTER_RELATIVE = 24
        const val RHYTHM_VOICE_LEVEL_ABSOLUTE = 25
        const val RHYTHM_VOICE_LEVEL_RELATIVE = 26
        const val RHYTHM_VOICE_PAN = 27
        const val SSG_DRUM_SHOT = 28
        const val FM_PART_VOLUME = 29
        const val FM_PART_SLOT_MASK = 30
        const val SSG_TONE_ENABLE = 31
        const val SSG_NOISE_ENABLE = 32
        const val SSG_NOISE_PERIOD = 33
        const val SSG_HARDWARE_ENVELOPE_PERIOD = 34
        const val SSG_HARDWARE_ENVELOPE_SHAPE = 35
        const val HW_LFO_ENABLE = 36
        const val HW_LFO_RATE = 37
        const val HW_LFO_PMS = 38
        const val HW_LFO_AMS = 39
        const val HW_LFO_DELAY = 40
        const val SSG_PART_VOLUME = 41
        const val PART_DETUNE_ABSOLUTE = 42
        const val PART_DETUNE_RELATIVE = 43
        const val MASTER_DETUNE_ABSOLUTE = 44

        const val HW_LFO_DELAY_NONE = 0
        const val HW_LFO_DELAY_RAW_CLOCKS = 1
        const val HW_LFO_DELAY_NOTE_LENGTH = 2
        const val SOFTWARE_LFO_TARGET_FM = 0
        const val SOFTWARE_LFO_TARGET_SSG = 1
        const val DETUNE_TARGET_FM = 0
        const val DETUNE_TARGET_SSG = 1
        const val LOGICAL_PART_NONE = -1
        const val FM3_PART_BASE = 6
        const val FM3_PART_COUNT = 4

        fun isNoteKind(kind: Int): Boolean = kind == FM_NOTE || kind == SSG_NOTE || kind == FM3_OPERATOR_NOTE
        fun isStateKind(kind: Int): Boolean = kind == SSG_ENVELOPE_DEFINE || kind == SSG_ENVELOPE_MODE ||
            kind in SSG_TONE_ENABLE..SSG_HARDWARE_ENVELOPE_SHAPE
        fun isRhythmKind(kind: Int): Boolean = kind == RHYTHM_SHOT || kind in RHYTHM_CONTROL_SHOT..SSG_DRUM_SHOT
        fun isModulationKind(kind: Int): Boolean = !isNoteKind(kind) && !isStateKind(kind) && !isRhythmKind(kind)
    }
}

internal class NotePayloadTable(
    private val startTicks: LongArray, private val durationTicks: IntArray, private val gateTicks: IntArray,
    private val channels: IntArray, private val logicalParts: IntArray, private val slotMasks: IntArray,
    private val midiNotes: IntArray, private val targetMidiNotes: IntArray,
    private val patchIds: IntArray, private val pans: IntArray, private val glideStartOffsetTicks: IntArray,
    private val gateValues: IntArray, private val gateScales: IntArray,
    private val gateTailClocks: IntArray, private val gateMinimumClocks: IntArray
) {
    val size: Int get() = startTicks.size
    fun startTick(i: Int) = startTicks[i]; fun durationTick(i: Int) = durationTicks[i]
    fun gateTick(i: Int) = gateTicks[i]; fun channel(i: Int) = channels[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun slotMask(i: Int) = slotMasks[i]
    fun midi(i: Int) = midiNotes[i]; fun targetMidi(i: Int) = targetMidiNotes[i]
    fun patchId(i: Int) = patchIds[i]
    fun pan(i: Int) = pans[i]; fun glideStartOffsetTick(i: Int) = glideStartOffsetTicks[i]
    fun gateValue(i: Int) = gateValues[i]; fun gateScale(i: Int) = gateScales[i]
    fun gateTailClocks(i: Int) = gateTailClocks[i]; fun gateMinimumClocks(i: Int) = gateMinimumClocks[i]
}

internal class SsgEnvelopeDefinitionTable(
    private val ticks: LongArray, private val channels: IntArray, private val formats: IntArray,
    private val attacks: IntArray, private val decays: IntArray, private val sustains: IntArray,
    private val releases: IntArray, private val sustainLevels: IntArray, private val attackLevels: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]
    fun format(i: Int) = formats[i]; fun attack(i: Int) = attacks[i]; fun decay(i: Int) = decays[i]
    fun sustain(i: Int) = sustains[i]; fun release(i: Int) = releases[i]
    fun sustainLevel(i: Int) = sustainLevels[i]; fun attackLevel(i: Int) = attackLevels[i]
}

internal class SsgEnvelopeModeTable(private val ticks: LongArray, private val channels: IntArray, private val modes: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]; fun mode(i: Int) = modes[i]
}

internal class SsgMixerEnableTable(private val ticks: LongArray, private val channels: IntArray, private val enabled: BooleanArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]; fun enabled(i: Int) = enabled[i]
}

internal class SsgPeriodTable(private val ticks: LongArray, private val channels: IntArray, private val periods: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]; fun period(i: Int) = periods[i]
}

internal class SsgEnvelopeShapeTable(private val ticks: LongArray, private val channels: IntArray, private val shapes: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]; fun shape(i: Int) = shapes[i]
}

internal class StatePayloadTables(
    val envelopeDefinitions: SsgEnvelopeDefinitionTable,
    val envelopeModes: SsgEnvelopeModeTable,
    val mixerEnables: SsgMixerEnableTable,
    val periods: SsgPeriodTable,
    val envelopeShapes: SsgEnvelopeShapeTable
) {
    val size get() = envelopeDefinitions.size + envelopeModes.size + mixerEnables.size + periods.size + envelopeShapes.size
    fun startTick(kind: Int, i: Int): Long = when (kind) {
        CompiledOpnaSong.SSG_ENVELOPE_DEFINE -> envelopeDefinitions.tick(i)
        CompiledOpnaSong.SSG_ENVELOPE_MODE -> envelopeModes.tick(i)
        CompiledOpnaSong.SSG_TONE_ENABLE, CompiledOpnaSong.SSG_NOISE_ENABLE -> mixerEnables.tick(i)
        CompiledOpnaSong.SSG_NOISE_PERIOD, CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_PERIOD -> periods.tick(i)
        CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE -> envelopeShapes.tick(i)
        else -> error("Unknown state payload kind $kind")
    }
    fun channel(kind: Int, i: Int): Int = when (kind) {
        CompiledOpnaSong.SSG_ENVELOPE_DEFINE -> envelopeDefinitions.channel(i)
        CompiledOpnaSong.SSG_ENVELOPE_MODE -> envelopeModes.channel(i)
        CompiledOpnaSong.SSG_TONE_ENABLE, CompiledOpnaSong.SSG_NOISE_ENABLE -> mixerEnables.channel(i)
        CompiledOpnaSong.SSG_NOISE_PERIOD, CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_PERIOD -> periods.channel(i)
        CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE -> envelopeShapes.channel(i)
        else -> -1
    }
}

internal class HardwareLfoEnableTable(private val ticks: LongArray, private val enabled: BooleanArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun enabled(i: Int) = enabled[i]
}

internal class HardwareLfoRateTable(private val ticks: LongArray, private val rates: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun rate(i: Int) = rates[i]
}

internal class HardwareLfoDepthTable(
    private val ticks: LongArray, private val channels: IntArray,
    private val logicalParts: IntArray, private val selectors: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun selector(i: Int) = selectors[i]
}

internal class HardwareLfoDelayTable(
    private val ticks: LongArray, private val channels: IntArray, private val logicalParts: IntArray,
    private val delayKinds: IntArray, private val delayValues: IntArray, private val dotted: BooleanArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun delayKind(i: Int) = delayKinds[i]
    fun delayValue(i: Int) = delayValues[i]; fun dotted(i: Int) = dotted[i]
}

internal class Fm3PatchTable(
    private val ticks: LongArray, private val logicalParts: IntArray,
    private val slotMasks: IntArray, private val patchIds: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun logicalPart(i: Int) = logicalParts[i]
    fun slotMask(i: Int) = slotMasks[i]; fun patchId(i: Int) = patchIds[i]
}

internal class PartVolumeTable(private val ticks: LongArray, private val parts: IntArray, private val volumes: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]
    fun part(i: Int) = parts[i]; fun volume(i: Int) = volumes[i]
}

internal class FmPartSlotMaskTable(private val ticks: LongArray, private val logicalParts: IntArray, private val slotMasks: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun slotMask(i: Int) = slotMasks[i]
}

internal class PartDetuneTable(
    private val ticks: LongArray, private val targetDomains: IntArray,
    private val parts: IntArray, private val values: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]
    fun targetDomain(i: Int) = targetDomains[i]; fun part(i: Int) = parts[i]; fun value(i: Int) = values[i]
}

internal class FmSlotDetuneTable(
    private val ticks: LongArray, private val channels: IntArray, private val logicalParts: IntArray,
    private val slotMasks: IntArray, private val rawDeltas: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun slotMask(i: Int) = slotMasks[i]; fun rawDelta(i: Int) = rawDeltas[i]
}

internal class FmTotalLevelTable(
    private val ticks: LongArray, private val channels: IntArray, private val logicalParts: IntArray,
    private val slotMasks: IntArray, private val totalLevels: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun slotMask(i: Int) = slotMasks[i]
    fun totalLevel(i: Int) = totalLevels[i]
}

internal class FmKeyOnDelayTable(
    private val ticks: LongArray, private val channels: IntArray, private val logicalParts: IntArray,
    private val slotMasks: IntArray, private val delayTicks: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun slotMask(i: Int) = slotMasks[i]
    fun delayTicks(i: Int) = delayTicks[i]
}

internal class FmFeedbackTable(
    private val ticks: LongArray, private val channels: IntArray,
    private val logicalParts: IntArray, private val feedback: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun feedback(i: Int) = feedback[i]
}

internal class SoftwareLfoIdentityTable(
    private val ticks: LongArray, private val channels: IntArray, private val logicalParts: IntArray,
    private val targetDomains: IntArray, private val lfoIndices: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun channel(i: Int) = channels[i]
    fun logicalPart(i: Int) = logicalParts[i]; fun targetDomain(i: Int) = targetDomains[i]
    fun lfoIndex(i: Int) = lfoIndices[i]
}

internal class SoftwareLfoDefinitionTable(
    val identity: SoftwareLfoIdentityTable, private val delayClocks: IntArray,
    private val speedClocks: IntArray, private val depthSteps: IntArray, private val depthCounts: IntArray
) {
    val size get() = identity.size; fun delayClocks(i: Int) = delayClocks[i]; fun speedClocks(i: Int) = speedClocks[i]
    fun depthStep(i: Int) = depthSteps[i]; fun depthCount(i: Int) = depthCounts[i]
}

internal class SoftwareLfoSwitchTable(val identity: SoftwareLfoIdentityTable, private val switchModes: IntArray) {
    val size get() = identity.size; fun switchMode(i: Int) = switchModes[i]
}

internal class SoftwareLfoWaveformTable(val identity: SoftwareLfoIdentityTable, private val waveforms: IntArray) {
    val size get() = identity.size; fun waveform(i: Int) = waveforms[i]
}

internal class SoftwareLfoClockModeTable(val identity: SoftwareLfoIdentityTable, private val clockModes: IntArray) {
    val size get() = identity.size; fun clockMode(i: Int) = clockModes[i]
}

internal class SoftwareLfoTlMaskTable(val identity: SoftwareLfoIdentityTable, private val slotMasks: IntArray) {
    val size get() = identity.size; fun slotMask(i: Int) = slotMasks[i]
}

internal class SoftwareLfoDepthTable(
    val identity: SoftwareLfoIdentityTable, private val speedClocks: IntArray,
    private val depthSteps: IntArray, private val evolutionCounts: IntArray
) {
    val size get() = identity.size; fun speedClocks(i: Int) = speedClocks[i]
    fun depthStep(i: Int) = depthSteps[i]; fun evolutionCount(i: Int) = evolutionCounts[i]
}

internal class ModulationPayloadTables(
    val hardwareLfoEnables: HardwareLfoEnableTable, val hardwareLfoRates: HardwareLfoRateTable,
    val hardwareLfoDepths: HardwareLfoDepthTable, val hardwareLfoDelays: HardwareLfoDelayTable,
    val fm3Patches: Fm3PatchTable, val partVolumes: PartVolumeTable, val partDetunes: PartDetuneTable,
    val fmPartSlotMasks: FmPartSlotMaskTable, val fmSlotDetunes: FmSlotDetuneTable,
    val fmTotalLevels: FmTotalLevelTable, val fmFeedback: FmFeedbackTable,
    val fmKeyOnDelays: FmKeyOnDelayTable, val softwareLfoDefinitions: SoftwareLfoDefinitionTable,
    val softwareLfoSwitches: SoftwareLfoSwitchTable, val softwareLfoWaveforms: SoftwareLfoWaveformTable,
    val softwareLfoClockModes: SoftwareLfoClockModeTable, val softwareLfoTlMasks: SoftwareLfoTlMaskTable,
    val softwareLfoDepths: SoftwareLfoDepthTable
) {
    val size get() = hardwareLfoEnables.size + hardwareLfoRates.size + hardwareLfoDepths.size +
        hardwareLfoDelays.size + fm3Patches.size + partVolumes.size + partDetunes.size + fmPartSlotMasks.size +
        fmSlotDetunes.size + fmTotalLevels.size + fmFeedback.size + fmKeyOnDelays.size +
        softwareLfoDefinitions.size + softwareLfoSwitches.size + softwareLfoWaveforms.size +
        softwareLfoClockModes.size + softwareLfoTlMasks.size + softwareLfoDepths.size

    fun startTick(kind: Int, i: Int): Long = when (kind) {
        CompiledOpnaSong.HW_LFO_ENABLE -> hardwareLfoEnables.tick(i)
        CompiledOpnaSong.HW_LFO_RATE -> hardwareLfoRates.tick(i)
        CompiledOpnaSong.HW_LFO_PMS, CompiledOpnaSong.HW_LFO_AMS -> hardwareLfoDepths.tick(i)
        CompiledOpnaSong.HW_LFO_DELAY -> hardwareLfoDelays.tick(i)
        CompiledOpnaSong.FM3_PATCH -> fm3Patches.tick(i)
        CompiledOpnaSong.FM_PART_VOLUME, CompiledOpnaSong.SSG_PART_VOLUME -> partVolumes.tick(i)
        CompiledOpnaSong.PART_DETUNE_ABSOLUTE, CompiledOpnaSong.PART_DETUNE_RELATIVE,
        CompiledOpnaSong.MASTER_DETUNE_ABSOLUTE -> partDetunes.tick(i)
        CompiledOpnaSong.FM_PART_SLOT_MASK -> fmPartSlotMasks.tick(i)
        CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE, CompiledOpnaSong.FM_SLOT_DETUNE_RELATIVE -> fmSlotDetunes.tick(i)
        CompiledOpnaSong.FM_TL_ABSOLUTE, CompiledOpnaSong.FM_TL_RELATIVE -> fmTotalLevels.tick(i)
        CompiledOpnaSong.FM_FEEDBACK_ABSOLUTE, CompiledOpnaSong.FM_FEEDBACK_RELATIVE -> fmFeedback.tick(i)
        CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY -> fmKeyOnDelays.tick(i)
        CompiledOpnaSong.SOFTWARE_LFO_DEFINE -> softwareLfoDefinitions.identity.tick(i)
        CompiledOpnaSong.SOFTWARE_LFO_SWITCH -> softwareLfoSwitches.identity.tick(i)
        CompiledOpnaSong.SOFTWARE_LFO_WAVE -> softwareLfoWaveforms.identity.tick(i)
        CompiledOpnaSong.SOFTWARE_LFO_CLOCK -> softwareLfoClockModes.identity.tick(i)
        CompiledOpnaSong.SOFTWARE_LFO_TL_MASK -> softwareLfoTlMasks.identity.tick(i)
        CompiledOpnaSong.SOFTWARE_LFO_DEPTH -> softwareLfoDepths.identity.tick(i)
        else -> error("Unknown modulation payload kind $kind")
    }

    fun channel(kind: Int, i: Int): Int = when (kind) {
        CompiledOpnaSong.HW_LFO_PMS, CompiledOpnaSong.HW_LFO_AMS -> hardwareLfoDepths.channel(i)
        CompiledOpnaSong.HW_LFO_DELAY -> hardwareLfoDelays.channel(i)
        CompiledOpnaSong.FM3_PATCH -> 2
        CompiledOpnaSong.FM_PART_VOLUME -> {
            val part = partVolumes.part(i)
            if (part in 0..5) part else 2
        }
        CompiledOpnaSong.SSG_PART_VOLUME -> partVolumes.part(i)
        CompiledOpnaSong.PART_DETUNE_ABSOLUTE, CompiledOpnaSong.PART_DETUNE_RELATIVE,
        CompiledOpnaSong.MASTER_DETUNE_ABSOLUTE -> {
            val part = partDetunes.part(i)
            if (partDetunes.targetDomain(i) == CompiledOpnaSong.DETUNE_TARGET_SSG) part
            else if (part < CompiledOpnaSong.FM3_PART_BASE) part else 2
        }
        CompiledOpnaSong.FM_PART_SLOT_MASK -> 2
        CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE, CompiledOpnaSong.FM_SLOT_DETUNE_RELATIVE -> fmSlotDetunes.channel(i)
        CompiledOpnaSong.FM_TL_ABSOLUTE, CompiledOpnaSong.FM_TL_RELATIVE -> fmTotalLevels.channel(i)
        CompiledOpnaSong.FM_FEEDBACK_ABSOLUTE, CompiledOpnaSong.FM_FEEDBACK_RELATIVE -> fmFeedback.channel(i)
        CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY -> fmKeyOnDelays.channel(i)
        CompiledOpnaSong.SOFTWARE_LFO_DEFINE -> softwareLfoDefinitions.identity.channel(i)
        CompiledOpnaSong.SOFTWARE_LFO_SWITCH -> softwareLfoSwitches.identity.channel(i)
        CompiledOpnaSong.SOFTWARE_LFO_WAVE -> softwareLfoWaveforms.identity.channel(i)
        CompiledOpnaSong.SOFTWARE_LFO_CLOCK -> softwareLfoClockModes.identity.channel(i)
        CompiledOpnaSong.SOFTWARE_LFO_TL_MASK -> softwareLfoTlMasks.identity.channel(i)
        CompiledOpnaSong.SOFTWARE_LFO_DEPTH -> softwareLfoDepths.identity.channel(i)
        else -> -1
    }

    fun logicalPart(kind: Int, i: Int): Int = when (kind) {
        CompiledOpnaSong.HW_LFO_PMS, CompiledOpnaSong.HW_LFO_AMS -> hardwareLfoDepths.logicalPart(i)
        CompiledOpnaSong.HW_LFO_DELAY -> hardwareLfoDelays.logicalPart(i)
        CompiledOpnaSong.FM3_PATCH -> fm3Patches.logicalPart(i)
        CompiledOpnaSong.FM_PART_VOLUME -> {
            val part = partVolumes.part(i)
            if (part >= CompiledOpnaSong.FM3_PART_BASE) part else CompiledOpnaSong.LOGICAL_PART_NONE
        }
        CompiledOpnaSong.FM_PART_SLOT_MASK -> fmPartSlotMasks.logicalPart(i)
        CompiledOpnaSong.PART_DETUNE_ABSOLUTE, CompiledOpnaSong.PART_DETUNE_RELATIVE,
        CompiledOpnaSong.MASTER_DETUNE_ABSOLUTE -> partDetunes.part(i)
        CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE, CompiledOpnaSong.FM_SLOT_DETUNE_RELATIVE -> fmSlotDetunes.logicalPart(i)
        CompiledOpnaSong.FM_TL_ABSOLUTE, CompiledOpnaSong.FM_TL_RELATIVE -> fmTotalLevels.logicalPart(i)
        CompiledOpnaSong.FM_FEEDBACK_ABSOLUTE, CompiledOpnaSong.FM_FEEDBACK_RELATIVE -> fmFeedback.logicalPart(i)
        CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY -> fmKeyOnDelays.logicalPart(i)
        CompiledOpnaSong.SOFTWARE_LFO_DEFINE -> softwareLfoDefinitions.identity.logicalPart(i)
        CompiledOpnaSong.SOFTWARE_LFO_SWITCH -> softwareLfoSwitches.identity.logicalPart(i)
        CompiledOpnaSong.SOFTWARE_LFO_WAVE -> softwareLfoWaveforms.identity.logicalPart(i)
        CompiledOpnaSong.SOFTWARE_LFO_CLOCK -> softwareLfoClockModes.identity.logicalPart(i)
        CompiledOpnaSong.SOFTWARE_LFO_TL_MASK -> softwareLfoTlMasks.identity.logicalPart(i)
        CompiledOpnaSong.SOFTWARE_LFO_DEPTH -> softwareLfoDepths.identity.logicalPart(i)
        else -> CompiledOpnaSong.LOGICAL_PART_NONE
    }
}

internal class PercussionShotTable(
    private val ticks: LongArray, private val drumKinds: IntArray,
    private val velocities: IntArray, private val pans: IntArray
) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun drumKind(i: Int) = drumKinds[i]
    fun velocity(i: Int) = velocities[i]; fun pan(i: Int) = pans[i]
}

internal class RhythmGateTable(private val ticks: LongArray, private val voiceMasks: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun voiceMask(i: Int) = voiceMasks[i]
}

internal class RhythmMasterLevelTable(private val ticks: LongArray, private val levels: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun level(i: Int) = levels[i]
}

internal class RhythmVoiceLevelTable(private val ticks: LongArray, private val voices: IntArray, private val levels: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun voice(i: Int) = voices[i]; fun level(i: Int) = levels[i]
}

internal class RhythmVoicePanTable(private val ticks: LongArray, private val voices: IntArray, private val pans: IntArray) {
    val size get() = ticks.size; fun tick(i: Int) = ticks[i]; fun voice(i: Int) = voices[i]; fun pan(i: Int) = pans[i]
}

internal class RhythmPayloadTables(
    val percussionShots: PercussionShotTable, val gates: RhythmGateTable,
    val masterLevels: RhythmMasterLevelTable, val voiceLevels: RhythmVoiceLevelTable,
    val voicePans: RhythmVoicePanTable
) {
    val size get() = percussionShots.size + gates.size + masterLevels.size + voiceLevels.size + voicePans.size
    fun startTick(kind: Int, i: Int): Long = when (kind) {
        CompiledOpnaSong.RHYTHM_SHOT, CompiledOpnaSong.SSG_DRUM_SHOT -> percussionShots.tick(i)
        CompiledOpnaSong.RHYTHM_CONTROL_SHOT, CompiledOpnaSong.RHYTHM_CONTROL_DUMP -> gates.tick(i)
        CompiledOpnaSong.RHYTHM_MASTER_ABSOLUTE, CompiledOpnaSong.RHYTHM_MASTER_RELATIVE -> masterLevels.tick(i)
        CompiledOpnaSong.RHYTHM_VOICE_LEVEL_ABSOLUTE, CompiledOpnaSong.RHYTHM_VOICE_LEVEL_RELATIVE -> voiceLevels.tick(i)
        CompiledOpnaSong.RHYTHM_VOICE_PAN -> voicePans.tick(i)
        else -> error("Unknown rhythm payload kind $kind")
    }
    fun voice(kind: Int, i: Int): Int = when (kind) {
        CompiledOpnaSong.RHYTHM_VOICE_LEVEL_ABSOLUTE, CompiledOpnaSong.RHYTHM_VOICE_LEVEL_RELATIVE -> voiceLevels.voice(i)
        CompiledOpnaSong.RHYTHM_VOICE_PAN -> voicePans.voice(i)
        else -> -1
    }
}

internal class CompiledOpnaSongBuilder(
    private val bpm: Float, private val bpmMilli: Int, private val beatsPerBar: Int,
    private val pmdClocksPerQuarter: Int, private val lfoRate: Int, private val fm3Extended: Boolean,
    sourceInstruments: SourceInstrumentLookup
) {
    private val instrumentBankBuilder = CompiledInstrumentBankBuilder(
        sourceInstruments, MAX_COMPILED_FM_PATCHES, MAX_COMPILED_SSG_PATCHES
    )
    private var tempoTick = LongArray(INITIAL_TEMPO_CAPACITY)
    private var tempoBpm = FloatArray(INITIAL_TEMPO_CAPACITY)
    private var tempoBpmMilli = IntArray(INITIAL_TEMPO_CAPACITY)
    private var tempoCount = 0

    private var noteStart = LongArray(INITIAL_EVENT_CAPACITY); private var noteDuration = IntArray(INITIAL_EVENT_CAPACITY)
    private var noteGate = IntArray(INITIAL_EVENT_CAPACITY); private var noteChannel = IntArray(INITIAL_EVENT_CAPACITY)
    private var noteLogicalPart = IntArray(INITIAL_EVENT_CAPACITY) { CompiledOpnaSong.LOGICAL_PART_NONE }
    private var noteSlotMask = IntArray(INITIAL_EVENT_CAPACITY); private var noteMidi = IntArray(INITIAL_EVENT_CAPACITY)
    private var noteTargetMidi = IntArray(INITIAL_EVENT_CAPACITY)
    private var notePatchId = IntArray(INITIAL_EVENT_CAPACITY); private var notePan = IntArray(INITIAL_EVENT_CAPACITY)
    private var noteGlideStartOffset = IntArray(INITIAL_EVENT_CAPACITY); private var noteGateValue = IntArray(INITIAL_EVENT_CAPACITY)
    private var noteGateScale = IntArray(INITIAL_EVENT_CAPACITY); private var noteGateTail = IntArray(INITIAL_EVENT_CAPACITY)
    private var noteGateMinimum = IntArray(INITIAL_EVENT_CAPACITY); private var noteCount = 0

    private val envelopeDefinitions = ArrayList<SsgEnvelopeDefinitionPayload>()
    private val envelopeModes = ArrayList<SsgEnvelopeModePayload>()
    private val mixerEnables = ArrayList<SsgMixerEnablePayload>()
    private val ssgPeriods = ArrayList<SsgPeriodPayload>()
    private val envelopeShapes = ArrayList<SsgEnvelopeShapePayload>()
    private val hardwareLfoEnables = ArrayList<HardwareLfoEnablePayload>()
    private val hardwareLfoRates = ArrayList<HardwareLfoRatePayload>()
    private val hardwareLfoDepths = ArrayList<HardwareLfoDepthPayload>()
    private val hardwareLfoDelays = ArrayList<HardwareLfoDelayPayload>()
    private val fm3Patches = ArrayList<Fm3PatchPayload>()
    private val partVolumes = ArrayList<PartVolumePayload>()
    private val partDetunes = ArrayList<PartDetunePayload>()
    private val fmPartSlotMasks = ArrayList<FmPartSlotMaskPayload>()
    private val fmSlotDetunes = ArrayList<FmSlotDetunePayload>()
    private val fmTotalLevels = ArrayList<FmTotalLevelPayload>()
    private val fmFeedback = ArrayList<FmFeedbackPayload>()
    private val fmKeyOnDelays = ArrayList<FmKeyOnDelayPayload>()
    private val softwareLfoDefinitions = ArrayList<SoftwareLfoDefinitionPayload>()
    private val softwareLfoSwitches = ArrayList<SoftwareLfoSwitchPayload>()
    private val softwareLfoWaveforms = ArrayList<SoftwareLfoWaveformPayload>()
    private val softwareLfoClockModes = ArrayList<SoftwareLfoClockModePayload>()
    private val softwareLfoTlMasks = ArrayList<SoftwareLfoTlMaskPayload>()
    private val softwareLfoDepths = ArrayList<SoftwareLfoDepthPayload>()
    private val percussionShots = ArrayList<PercussionShotPayload>()
    private val rhythmGates = ArrayList<RhythmGatePayload>()
    private val rhythmMasterLevels = ArrayList<RhythmMasterLevelPayload>()
    private val rhythmVoiceLevels = ArrayList<RhythmVoiceLevelPayload>()
    private val rhythmVoicePans = ArrayList<RhythmVoicePanPayload>()

    private var authoredKind = IntArray(INITIAL_EVENT_CAPACITY); private var authoredPayload = IntArray(INITIAL_EVENT_CAPACITY)
    private var authoredOrder = IntArray(INITIAL_EVENT_CAPACITY); private var authoredLine = IntArray(INITIAL_EVENT_CAPACITY)
    private var authoredColumn = IntArray(INITIAL_EVENT_CAPACITY)
    private var currentSourceOrder = -1; private var currentSourceLine = 0; private var currentSourceColumn = 0
    var size = 0; private set
    var durationTicks = 0L; private set

    fun internFmPatch(sourceId: Int) = instrumentBankBuilder.internFm(sourceId)
    fun internSsgPatch(sourceId: Int) = instrumentBankBuilder.internSsg(sourceId)
    fun beginSource(authoredOrder: Int, line: Int, column: Int) {
        currentSourceOrder = authoredOrder; currentSourceLine = line; currentSourceColumn = column
    }

    fun addTempo(atTick: Long, bpm: Float, milliBpm: Int): Boolean {
        var position = 0
        while (position < tempoCount && tempoTick[position] < atTick) position++
        if (position < tempoCount && tempoTick[position] == atTick) {
            tempoBpm[position] = bpm; tempoBpmMilli[position] = milliBpm; return true
        }
        if (tempoCount >= MAX_TEMPO_CHANGES) return false
        ensureTempoCapacity(tempoCount + 1)
        var move = tempoCount
        while (move > position) {
            tempoTick[move] = tempoTick[move - 1]; tempoBpm[move] = tempoBpm[move - 1]
            tempoBpmMilli[move] = tempoBpmMilli[move - 1]; move--
        }
        tempoTick[position] = atTick; tempoBpm[position] = bpm; tempoBpmMilli[position] = milliBpm; tempoCount++
        return true
    }

    fun addFmNote(
        atTick: Long, duration: Int, gate: Int, channel: Int, midiNote: Int, targetMidiNote: Int,
        patchId: Int, pan: Int, glideStartOffsetTicks: Int,
        gateValue: Int = 0, gateScale: Int = 8, gateTailClocks: Int = 0, gateMinimumClocks: Int = 0,
        sourceOrder: Int = SOURCE_ORDER_UNSET, sourceLine: Int = 0, sourceColumn: Int = 0
    ): Boolean = appendNote(
        CompiledOpnaSong.FM_NOTE, atTick, duration, gate, channel, midiNote, targetMidiNote,
        patchId, pan, glideStartOffsetTicks, gateValue, gateScale, gateTailClocks, gateMinimumClocks,
        0, CompiledOpnaSong.LOGICAL_PART_NONE, sourceOrder, sourceLine, sourceColumn
    )

    fun addSsgNote(
        atTick: Long, duration: Int, gate: Int, channel: Int, midiNote: Int, targetMidiNote: Int,
        patchId: Int, pan: Int, glideStartOffsetTicks: Int,
        gateValue: Int = 0, gateScale: Int = 8, gateTailClocks: Int = 0, gateMinimumClocks: Int = 0,
        sourceOrder: Int = SOURCE_ORDER_UNSET, sourceLine: Int = 0, sourceColumn: Int = 0
    ): Boolean = appendNote(
        CompiledOpnaSong.SSG_NOTE, atTick, duration, gate, channel, midiNote, targetMidiNote,
        patchId, pan, glideStartOffsetTicks, gateValue, gateScale, gateTailClocks, gateMinimumClocks,
        0, CompiledOpnaSong.LOGICAL_PART_NONE, sourceOrder, sourceLine, sourceColumn
    )

    fun addFm3OperatorNote(
        atTick: Long, duration: Int, gate: Int, midiNote: Int, targetMidiNote: Int,
        patchId: Int, pan: Int, glideStartOffsetTicks: Int, slotMask: Int, logicalPart: Int,
        gateValue: Int = 0, gateScale: Int = 8, gateTailClocks: Int = 0, gateMinimumClocks: Int = 0,
        sourceOrder: Int = SOURCE_ORDER_UNSET, sourceLine: Int = 0, sourceColumn: Int = 0
    ): Boolean = appendNote(
        CompiledOpnaSong.FM3_OPERATOR_NOTE, atTick, duration, gate, 2, midiNote, targetMidiNote,
        patchId, pan, glideStartOffsetTicks, gateValue, gateScale, gateTailClocks, gateMinimumClocks,
        slotMask, logicalPart, sourceOrder, sourceLine, sourceColumn
    )

    fun addRhythmShot(atTick: Long, duration: Int, drumKind: Int, velocity: Int, pan: Int,
        sourceOrder: Int = SOURCE_ORDER_UNSET, sourceLine: Int = 0, sourceColumn: Int = 0): Boolean =
        appendPercussionShot(CompiledOpnaSong.RHYTHM_SHOT, atTick, duration, drumKind, velocity, pan,
            sourceOrder, sourceLine, sourceColumn)

    fun addSsgDrumShot(atTick: Long, duration: Int, drumKind: Int, velocity: Int, pan: Int,
        sourceOrder: Int = SOURCE_ORDER_UNSET, sourceLine: Int = 0, sourceColumn: Int = 0): Boolean =
        appendPercussionShot(CompiledOpnaSong.SSG_DRUM_SHOT, atTick, duration, drumKind, velocity, pan,
            sourceOrder, sourceLine, sourceColumn)

    fun addSsgEnvelopeDefinition(atTick: Long, channel: Int, format: Int, attack: Int, decay: Int,
        sustain: Int, release: Int, sustainLevel: Int, attackLevel: Int): Boolean = append(
        CompiledOpnaSong.SSG_ENVELOPE_DEFINE, envelopeDefinitions,
        SsgEnvelopeDefinitionPayload(atTick, channel, format, attack, decay, sustain, release, sustainLevel, attackLevel)
    )
    fun addSsgEnvelopeMode(atTick: Long, channel: Int, mode: Int): Boolean = append(
        CompiledOpnaSong.SSG_ENVELOPE_MODE, envelopeModes, SsgEnvelopeModePayload(atTick, channel, mode)
    )
    fun addSsgToneEnable(tick: Long, channel: Int, enabled: Boolean,
        order: Int, line: Int, column: Int): Boolean = append(
        CompiledOpnaSong.SSG_TONE_ENABLE, mixerEnables,
        SsgMixerEnablePayload(tick, channel, enabled), order, line, column
    )
    fun addSsgNoiseEnable(tick: Long, channel: Int, enabled: Boolean,
        order: Int, line: Int, column: Int): Boolean = append(
        CompiledOpnaSong.SSG_NOISE_ENABLE, mixerEnables,
        SsgMixerEnablePayload(tick, channel, enabled), order, line, column
    )
    fun addSsgNoisePeriod(tick: Long, channel: Int, period: Int,
        order: Int, line: Int, column: Int): Boolean = append(
        CompiledOpnaSong.SSG_NOISE_PERIOD, ssgPeriods,
        SsgPeriodPayload(tick, channel, period), order, line, column
    )
    fun addSsgHardwareEnvelopePeriod(tick: Long, channel: Int, period: Int,
        order: Int, line: Int, column: Int): Boolean = append(
        CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_PERIOD, ssgPeriods,
        SsgPeriodPayload(tick, channel, period), order, line, column
    )
    fun addSsgEnvelopeShape(tick: Long, channel: Int, shape: Int,
        order: Int, line: Int, column: Int): Boolean = append(
        CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE, envelopeShapes,
        SsgEnvelopeShapePayload(tick, channel, shape), order, line, column
    )
    fun addHardwareLfoEnable(tick: Long, enabled: Boolean, order: Int, line: Int, column: Int): Boolean =
        append(CompiledOpnaSong.HW_LFO_ENABLE, hardwareLfoEnables, HardwareLfoEnablePayload(tick, enabled), order, line, column)
    fun addHardwareLfoRate(tick: Long, rate: Int, order: Int, line: Int, column: Int): Boolean =
        append(CompiledOpnaSong.HW_LFO_RATE, hardwareLfoRates, HardwareLfoRatePayload(tick, rate), order, line, column)
    fun addHardwareLfoPms(tick: Long, channel: Int, logicalPart: Int, selector: Int,
        order: Int, line: Int, column: Int): Boolean = append(
        CompiledOpnaSong.HW_LFO_PMS, hardwareLfoDepths,
        HardwareLfoDepthPayload(tick, channel, logicalPart, selector), order, line, column
    )
    fun addHardwareLfoAms(tick: Long, channel: Int, logicalPart: Int, selector: Int,
        order: Int, line: Int, column: Int): Boolean = append(
        CompiledOpnaSong.HW_LFO_AMS, hardwareLfoDepths,
        HardwareLfoDepthPayload(tick, channel, logicalPart, selector), order, line, column
    )
    fun addHardwareLfoDelay(tick: Long, channel: Int, logicalPart: Int, delayKind: Int, delayValue: Int,
        dotted: Boolean, order: Int, line: Int, column: Int): Boolean = append(
        CompiledOpnaSong.HW_LFO_DELAY, hardwareLfoDelays,
        HardwareLfoDelayPayload(tick, channel, logicalPart, delayKind, delayValue, dotted), order, line, column
    )
    fun addFm3Patch(tick: Long, mask: Int, patchId: Int,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        CompiledOpnaSong.FM3_PATCH, fm3Patches, Fm3PatchPayload(tick, logicalPart, mask, patchId)
    )
    fun addFmPartVolume(tick: Long, part: Int, volume: Int): Boolean = append(
        CompiledOpnaSong.FM_PART_VOLUME, partVolumes, PartVolumePayload(tick, part, volume)
    )
    fun addSsgPartVolume(tick: Long, part: Int, volume: Int): Boolean = append(
        CompiledOpnaSong.SSG_PART_VOLUME, partVolumes, PartVolumePayload(tick, part, volume)
    )
    fun addPartDetune(tick: Long, targetDomain: Int, part: Int, value: Int, kind: Int): Boolean = append(
        kind, partDetunes, PartDetunePayload(tick, targetDomain, part, value)
    )
    fun addFmPartSlotMask(tick: Long, logicalPart: Int, slotMask: Int): Boolean = append(
        CompiledOpnaSong.FM_PART_SLOT_MASK, fmPartSlotMasks, FmPartSlotMaskPayload(tick, logicalPart, slotMask)
    )
    fun addFmSlotDetune(tick: Long, channel: Int, slotMask: Int, rawDelta: Int, relative: Boolean,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        if (relative) CompiledOpnaSong.FM_SLOT_DETUNE_RELATIVE else CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE,
        fmSlotDetunes, FmSlotDetunePayload(tick, channel, logicalPart, slotMask, rawDelta)
    )
    fun addFmTotalLevel(tick: Long, channel: Int, slotMask: Int, totalLevel: Int, relative: Boolean,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        if (relative) CompiledOpnaSong.FM_TL_RELATIVE else CompiledOpnaSong.FM_TL_ABSOLUTE,
        fmTotalLevels, FmTotalLevelPayload(tick, channel, logicalPart, slotMask, totalLevel)
    )
    fun addFmFeedback(tick: Long, channel: Int, feedback: Int, relative: Boolean,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        if (relative) CompiledOpnaSong.FM_FEEDBACK_RELATIVE else CompiledOpnaSong.FM_FEEDBACK_ABSOLUTE,
        fmFeedback, FmFeedbackPayload(tick, channel, logicalPart, feedback)
    )
    fun addFmKeyOnDelay(tick: Long, channel: Int, slotMask: Int, delayTicks: Int,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY, fmKeyOnDelays,
        FmKeyOnDelayPayload(tick, channel, logicalPart, slotMask, delayTicks)
    )

    fun addSoftwareLfoDefinition(tick: Long, channel: Int, targetDomain: Int, lfoIndex: Int,
        delayClocks: Int, speedClocks: Int, depthStep: Int, depthCount: Int,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        CompiledOpnaSong.SOFTWARE_LFO_DEFINE, softwareLfoDefinitions,
        SoftwareLfoDefinitionPayload(tick, channel, logicalPart, targetDomain, lfoIndex,
            delayClocks, speedClocks, depthStep, depthCount)
    )
    fun addSoftwareLfoSwitch(tick: Long, channel: Int, targetDomain: Int, lfoIndex: Int, switchMode: Int,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        CompiledOpnaSong.SOFTWARE_LFO_SWITCH, softwareLfoSwitches,
        SoftwareLfoSwitchPayload(tick, channel, logicalPart, targetDomain, lfoIndex, switchMode)
    )
    fun addSoftwareLfoWaveform(tick: Long, channel: Int, targetDomain: Int, lfoIndex: Int, waveform: Int,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        CompiledOpnaSong.SOFTWARE_LFO_WAVE, softwareLfoWaveforms,
        SoftwareLfoWaveformPayload(tick, channel, logicalPart, targetDomain, lfoIndex, waveform)
    )
    fun addSoftwareLfoClockMode(tick: Long, channel: Int, targetDomain: Int, lfoIndex: Int, clockMode: Int,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        CompiledOpnaSong.SOFTWARE_LFO_CLOCK, softwareLfoClockModes,
        SoftwareLfoClockModePayload(tick, channel, logicalPart, targetDomain, lfoIndex, clockMode)
    )
    fun addSoftwareLfoTlMask(tick: Long, channel: Int, lfoIndex: Int, slotMask: Int,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        CompiledOpnaSong.SOFTWARE_LFO_TL_MASK, softwareLfoTlMasks,
        SoftwareLfoTlMaskPayload(tick, channel, logicalPart, CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM, lfoIndex, slotMask)
    )
    fun addSoftwareLfoDepth(tick: Long, channel: Int, targetDomain: Int, lfoIndex: Int,
        speedClocks: Int, depthStep: Int, evolutionCount: Int,
        logicalPart: Int = CompiledOpnaSong.LOGICAL_PART_NONE): Boolean = append(
        CompiledOpnaSong.SOFTWARE_LFO_DEPTH, softwareLfoDepths,
        SoftwareLfoDepthPayload(tick, channel, logicalPart, targetDomain, lfoIndex,
            speedClocks, depthStep, evolutionCount)
    )
    fun addRhythmGate(tick: Long, voiceMask: Int, dump: Boolean): Boolean = append(
        if (dump) CompiledOpnaSong.RHYTHM_CONTROL_DUMP else CompiledOpnaSong.RHYTHM_CONTROL_SHOT,
        rhythmGates, RhythmGatePayload(tick, voiceMask)
    )
    fun addRhythmMasterLevel(tick: Long, level: Int, relative: Boolean): Boolean = append(
        if (relative) CompiledOpnaSong.RHYTHM_MASTER_RELATIVE else CompiledOpnaSong.RHYTHM_MASTER_ABSOLUTE,
        rhythmMasterLevels, RhythmMasterLevelPayload(tick, level)
    )
    fun addRhythmVoiceLevel(tick: Long, voice: Int, level: Int, relative: Boolean): Boolean = append(
        if (relative) CompiledOpnaSong.RHYTHM_VOICE_LEVEL_RELATIVE else CompiledOpnaSong.RHYTHM_VOICE_LEVEL_ABSOLUTE,
        rhythmVoiceLevels, RhythmVoiceLevelPayload(tick, voice, level)
    )
    fun addRhythmVoicePan(tick: Long, voice: Int, pan: Int): Boolean = append(
        CompiledOpnaSong.RHYTHM_VOICE_PAN, rhythmVoicePans, RhythmVoicePanPayload(tick, voice, pan)
    )

    private fun appendNote(
        kind: Int, atTick: Long, duration: Int, gate: Int, channel: Int,
        midiNote: Int, targetMidiNote: Int, patchId: Int, pan: Int, glideStartOffsetTicks: Int,
        gateValue: Int, gateScale: Int, gateTailClocks: Int, gateMinimumClocks: Int,
        slotMask: Int, logicalPart: Int, sourceOrder: Int, sourceLine: Int, sourceColumn: Int
    ): Boolean {
        if (!hasEventCapacity()) return false
        ensureNoteCapacity(noteCount + 1)
        val index = noteCount++
        noteStart[index] = atTick; noteDuration[index] = duration; noteGate[index] = gate
        noteChannel[index] = channel; noteLogicalPart[index] = logicalPart; noteSlotMask[index] = slotMask
        noteMidi[index] = midiNote; noteTargetMidi[index] = targetMidiNote
        notePatchId[index] = patchId; notePan[index] = pan; noteGlideStartOffset[index] = glideStartOffsetTicks
        noteGateValue[index] = gateValue; noteGateScale[index] = gateScale
        noteGateTail[index] = gateTailClocks; noteGateMinimum[index] = gateMinimumClocks
        record(kind, index, sourceOrder, sourceLine, sourceColumn)
        updateDuration(atTick, duration)
        return true
    }

    private fun appendPercussionShot(
        kind: Int, atTick: Long, duration: Int, drumKind: Int, velocity: Int, pan: Int,
        sourceOrder: Int, sourceLine: Int, sourceColumn: Int
    ): Boolean {
        if (!hasEventCapacity()) return false
        val index = percussionShots.size
        percussionShots.add(PercussionShotPayload(atTick, drumKind, velocity, pan))
        record(kind, index, sourceOrder, sourceLine, sourceColumn)
        updateDuration(atTick, duration)
        return true
    }

    private fun <T> append(kind: Int, rows: MutableList<T>, payload: T,
        order: Int = SOURCE_ORDER_UNSET, line: Int = 0, column: Int = 0): Boolean {
        if (!hasEventCapacity()) return false
        val index = rows.size; rows.add(payload); record(kind, index, order, line, column); return true
    }

    private fun record(kind: Int, payload: Int, selectedOrder: Int, line: Int, column: Int) {
        ensureAuthoredCapacity(size + 1); authoredKind[size] = kind; authoredPayload[size] = payload
        authoredOrder[size] = if (selectedOrder != SOURCE_ORDER_UNSET) selectedOrder
            else if (currentSourceOrder >= 0) currentSourceOrder else size
        authoredLine[size] = if (line > 0) line else currentSourceLine
        authoredColumn[size] = if (column > 0) column else currentSourceColumn; size++
    }
    private fun hasEventCapacity() = size < CompiledOpnaSong.MAX_AUTHORED_EVENTS
    private fun updateDuration(start: Long, duration: Int) { val end = start + duration; if (end > durationTicks) durationTicks = end }

    fun build(): CompiledOpnaSong = CompiledOpnaSong(
        bpm, bpmMilli, beatsPerBar, pmdClocksPerQuarter, lfoRate, fm3Extended,
        instrumentBankBuilder.build(), tempoCount, tempoTick.copyOf(tempoCount),
        tempoBpm.copyOf(tempoCount), tempoBpmMilli.copyOf(tempoCount), durationTicks,
        NotePayloadTable(
            noteStart.copyOf(noteCount), noteDuration.copyOf(noteCount), noteGate.copyOf(noteCount),
            noteChannel.copyOf(noteCount), noteLogicalPart.copyOf(noteCount), noteSlotMask.copyOf(noteCount),
            noteMidi.copyOf(noteCount), noteTargetMidi.copyOf(noteCount),
            notePatchId.copyOf(noteCount), notePan.copyOf(noteCount), noteGlideStartOffset.copyOf(noteCount),
            noteGateValue.copyOf(noteCount), noteGateScale.copyOf(noteCount),
            noteGateTail.copyOf(noteCount), noteGateMinimum.copyOf(noteCount)
        ), buildStates(), buildModulations(), buildRhythms(),
        authoredKind.copyOf(size), authoredPayload.copyOf(size), authoredOrder.copyOf(size),
        authoredLine.copyOf(size), authoredColumn.copyOf(size)
    )

    private fun buildStates() = StatePayloadTables(
        SsgEnvelopeDefinitionTable(ticks(envelopeDefinitions), ints(envelopeDefinitions) { it.channel },
            ints(envelopeDefinitions) { it.format }, ints(envelopeDefinitions) { it.attack },
            ints(envelopeDefinitions) { it.decay }, ints(envelopeDefinitions) { it.sustain },
            ints(envelopeDefinitions) { it.release }, ints(envelopeDefinitions) { it.sustainLevel },
            ints(envelopeDefinitions) { it.attackLevel }),
        SsgEnvelopeModeTable(ticks(envelopeModes), ints(envelopeModes) { it.channel }, ints(envelopeModes) { it.mode }),
        SsgMixerEnableTable(ticks(mixerEnables), ints(mixerEnables) { it.channel }, booleans(mixerEnables) { it.enabled }),
        SsgPeriodTable(ticks(ssgPeriods), ints(ssgPeriods) { it.channel }, ints(ssgPeriods) { it.period }),
        SsgEnvelopeShapeTable(ticks(envelopeShapes), ints(envelopeShapes) { it.channel }, ints(envelopeShapes) { it.shape })
    )

    private fun buildModulations() = ModulationPayloadTables(
        HardwareLfoEnableTable(ticks(hardwareLfoEnables), booleans(hardwareLfoEnables) { it.enabled }),
        HardwareLfoRateTable(ticks(hardwareLfoRates), ints(hardwareLfoRates) { it.rate }),
        HardwareLfoDepthTable(ticks(hardwareLfoDepths), ints(hardwareLfoDepths) { it.channel },
            ints(hardwareLfoDepths) { it.logicalPart }, ints(hardwareLfoDepths) { it.selector }),
        HardwareLfoDelayTable(ticks(hardwareLfoDelays), ints(hardwareLfoDelays) { it.channel },
            ints(hardwareLfoDelays) { it.logicalPart }, ints(hardwareLfoDelays) { it.delayKind },
            ints(hardwareLfoDelays) { it.delayValue }, booleans(hardwareLfoDelays) { it.dotted }),
        Fm3PatchTable(ticks(fm3Patches), ints(fm3Patches) { it.logicalPart }, ints(fm3Patches) { it.slotMask }, ints(fm3Patches) { it.patchId }),
        PartVolumeTable(ticks(partVolumes), ints(partVolumes) { it.part }, ints(partVolumes) { it.volume }),
        PartDetuneTable(ticks(partDetunes), ints(partDetunes) { it.targetDomain },
            ints(partDetunes) { it.part }, ints(partDetunes) { it.value }),
        FmPartSlotMaskTable(ticks(fmPartSlotMasks), ints(fmPartSlotMasks) { it.logicalPart }, ints(fmPartSlotMasks) { it.slotMask }),
        FmSlotDetuneTable(ticks(fmSlotDetunes), ints(fmSlotDetunes) { it.channel }, ints(fmSlotDetunes) { it.logicalPart },
            ints(fmSlotDetunes) { it.slotMask }, ints(fmSlotDetunes) { it.rawDelta }),
        FmTotalLevelTable(ticks(fmTotalLevels), ints(fmTotalLevels) { it.channel }, ints(fmTotalLevels) { it.logicalPart },
            ints(fmTotalLevels) { it.slotMask }, ints(fmTotalLevels) { it.totalLevel }),
        FmFeedbackTable(ticks(fmFeedback), ints(fmFeedback) { it.channel }, ints(fmFeedback) { it.logicalPart }, ints(fmFeedback) { it.feedback }),
        FmKeyOnDelayTable(ticks(fmKeyOnDelays), ints(fmKeyOnDelays) { it.channel }, ints(fmKeyOnDelays) { it.logicalPart },
            ints(fmKeyOnDelays) { it.slotMask }, ints(fmKeyOnDelays) { it.delayTicks }),
        SoftwareLfoDefinitionTable(identity(softwareLfoDefinitions), ints(softwareLfoDefinitions) { it.delayClocks },
            ints(softwareLfoDefinitions) { it.speedClocks }, ints(softwareLfoDefinitions) { it.depthStep }, ints(softwareLfoDefinitions) { it.depthCount }),
        SoftwareLfoSwitchTable(identity(softwareLfoSwitches), ints(softwareLfoSwitches) { it.switchMode }),
        SoftwareLfoWaveformTable(identity(softwareLfoWaveforms), ints(softwareLfoWaveforms) { it.waveform }),
        SoftwareLfoClockModeTable(identity(softwareLfoClockModes), ints(softwareLfoClockModes) { it.clockMode }),
        SoftwareLfoTlMaskTable(identity(softwareLfoTlMasks), ints(softwareLfoTlMasks) { it.slotMask }),
        SoftwareLfoDepthTable(identity(softwareLfoDepths), ints(softwareLfoDepths) { it.speedClocks },
            ints(softwareLfoDepths) { it.depthStep }, ints(softwareLfoDepths) { it.evolutionCount })
    )

    private fun buildRhythms() = RhythmPayloadTables(
        PercussionShotTable(ticks(percussionShots), ints(percussionShots) { it.drumKind },
            ints(percussionShots) { it.velocity }, ints(percussionShots) { it.pan }),
        RhythmGateTable(ticks(rhythmGates), ints(rhythmGates) { it.voiceMask }),
        RhythmMasterLevelTable(ticks(rhythmMasterLevels), ints(rhythmMasterLevels) { it.level }),
        RhythmVoiceLevelTable(ticks(rhythmVoiceLevels), ints(rhythmVoiceLevels) { it.voice }, ints(rhythmVoiceLevels) { it.level }),
        RhythmVoicePanTable(ticks(rhythmVoicePans), ints(rhythmVoicePans) { it.voice }, ints(rhythmVoicePans) { it.pan })
    )

    private fun <T : TimedPayload> ticks(rows: List<T>) = LongArray(rows.size) { rows[it].tick }
    private fun <T> ints(rows: List<T>, read: (T) -> Int) = IntArray(rows.size) { read(rows[it]) }
    private fun <T> booleans(rows: List<T>, read: (T) -> Boolean) = BooleanArray(rows.size) { read(rows[it]) }
    private fun <T : SoftwareLfoPayload> identity(rows: List<T>) = SoftwareLfoIdentityTable(
        ticks(rows), ints(rows) { it.channel }, ints(rows) { it.logicalPart },
        ints(rows) { it.targetDomain }, ints(rows) { it.lfoIndex }
    )

    private fun ensureTempoCapacity(required: Int) {
        if (required <= tempoTick.size) return; val next = minOf(MAX_TEMPO_CHANGES, tempoTick.size * 2)
        tempoTick = tempoTick.copyOf(next); tempoBpm = tempoBpm.copyOf(next); tempoBpmMilli = tempoBpmMilli.copyOf(next)
    }
    private fun ensureAuthoredCapacity(required: Int) {
        if (required <= authoredKind.size) return; val next = nextCapacity(authoredKind.size)
        authoredKind = authoredKind.copyOf(next); authoredPayload = authoredPayload.copyOf(next)
        authoredOrder = authoredOrder.copyOf(next); authoredLine = authoredLine.copyOf(next); authoredColumn = authoredColumn.copyOf(next)
    }
    private fun ensureNoteCapacity(required: Int) {
        if (required <= noteStart.size) return; val old = noteStart.size; val next = nextCapacity(old)
        noteStart = noteStart.copyOf(next); noteDuration = noteDuration.copyOf(next); noteGate = noteGate.copyOf(next)
        noteChannel = noteChannel.copyOf(next); noteLogicalPart = noteLogicalPart.copyOf(next)
        noteLogicalPart.fill(CompiledOpnaSong.LOGICAL_PART_NONE, old, next); noteSlotMask = noteSlotMask.copyOf(next)
        noteMidi = noteMidi.copyOf(next); noteTargetMidi = noteTargetMidi.copyOf(next)
        notePatchId = notePatchId.copyOf(next); notePan = notePan.copyOf(next)
        noteGlideStartOffset = noteGlideStartOffset.copyOf(next)
        noteGateValue = noteGateValue.copyOf(next); noteGateScale = noteGateScale.copyOf(next)
        noteGateTail = noteGateTail.copyOf(next); noteGateMinimum = noteGateMinimum.copyOf(next)
    }
    private fun nextCapacity(current: Int) = minOf(CompiledOpnaSong.MAX_AUTHORED_EVENTS, current * 2)

    private companion object {
        const val INITIAL_EVENT_CAPACITY = 256; const val INITIAL_TEMPO_CAPACITY = 8
        const val MAX_TEMPO_CHANGES = 4_096; const val MAX_COMPILED_FM_PATCHES = 256
        const val MAX_COMPILED_SSG_PATCHES = 256; const val SOURCE_ORDER_UNSET = Int.MAX_VALUE
    }
}

private interface TimedPayload { val tick: Long }
private data class SsgEnvelopeDefinitionPayload(override val tick: Long, val channel: Int, val format: Int,
    val attack: Int, val decay: Int, val sustain: Int, val release: Int, val sustainLevel: Int,
    val attackLevel: Int) : TimedPayload
private data class SsgEnvelopeModePayload(override val tick: Long, val channel: Int, val mode: Int) : TimedPayload
private data class SsgMixerEnablePayload(override val tick: Long, val channel: Int, val enabled: Boolean) : TimedPayload
private data class SsgPeriodPayload(override val tick: Long, val channel: Int, val period: Int) : TimedPayload
private data class SsgEnvelopeShapePayload(override val tick: Long, val channel: Int, val shape: Int) : TimedPayload
private data class HardwareLfoEnablePayload(override val tick: Long, val enabled: Boolean) : TimedPayload
private data class HardwareLfoRatePayload(override val tick: Long, val rate: Int) : TimedPayload
private data class HardwareLfoDepthPayload(override val tick: Long, val channel: Int, val logicalPart: Int,
    val selector: Int) : TimedPayload
private data class HardwareLfoDelayPayload(override val tick: Long, val channel: Int, val logicalPart: Int,
    val delayKind: Int, val delayValue: Int, val dotted: Boolean) : TimedPayload
private data class Fm3PatchPayload(override val tick: Long, val logicalPart: Int, val slotMask: Int,
    val patchId: Int) : TimedPayload
private data class PartVolumePayload(override val tick: Long, val part: Int, val volume: Int) : TimedPayload
private data class PartDetunePayload(override val tick: Long, val targetDomain: Int, val part: Int,
    val value: Int) : TimedPayload
private data class FmPartSlotMaskPayload(override val tick: Long, val logicalPart: Int, val slotMask: Int) : TimedPayload
private data class FmSlotDetunePayload(override val tick: Long, val channel: Int, val logicalPart: Int,
    val slotMask: Int, val rawDelta: Int) : TimedPayload
private data class FmTotalLevelPayload(override val tick: Long, val channel: Int, val logicalPart: Int,
    val slotMask: Int, val totalLevel: Int) : TimedPayload
private data class FmFeedbackPayload(override val tick: Long, val channel: Int, val logicalPart: Int,
    val feedback: Int) : TimedPayload
private data class FmKeyOnDelayPayload(override val tick: Long, val channel: Int, val logicalPart: Int,
    val slotMask: Int, val delayTicks: Int) : TimedPayload
private interface SoftwareLfoPayload : TimedPayload {
    val channel: Int; val logicalPart: Int; val targetDomain: Int; val lfoIndex: Int
}
private data class SoftwareLfoDefinitionPayload(override val tick: Long, override val channel: Int,
    override val logicalPart: Int, override val targetDomain: Int, override val lfoIndex: Int,
    val delayClocks: Int, val speedClocks: Int, val depthStep: Int, val depthCount: Int) : SoftwareLfoPayload
private data class SoftwareLfoSwitchPayload(override val tick: Long, override val channel: Int,
    override val logicalPart: Int, override val targetDomain: Int, override val lfoIndex: Int,
    val switchMode: Int) : SoftwareLfoPayload
private data class SoftwareLfoWaveformPayload(override val tick: Long, override val channel: Int,
    override val logicalPart: Int, override val targetDomain: Int, override val lfoIndex: Int,
    val waveform: Int) : SoftwareLfoPayload
private data class SoftwareLfoClockModePayload(override val tick: Long, override val channel: Int,
    override val logicalPart: Int, override val targetDomain: Int, override val lfoIndex: Int,
    val clockMode: Int) : SoftwareLfoPayload
private data class SoftwareLfoTlMaskPayload(override val tick: Long, override val channel: Int,
    override val logicalPart: Int, override val targetDomain: Int, override val lfoIndex: Int,
    val slotMask: Int) : SoftwareLfoPayload
private data class SoftwareLfoDepthPayload(override val tick: Long, override val channel: Int,
    override val logicalPart: Int, override val targetDomain: Int, override val lfoIndex: Int,
    val speedClocks: Int, val depthStep: Int, val evolutionCount: Int) : SoftwareLfoPayload
private data class PercussionShotPayload(override val tick: Long, val drumKind: Int,
    val velocity: Int, val pan: Int) : TimedPayload
private data class RhythmGatePayload(override val tick: Long, val voiceMask: Int) : TimedPayload
private data class RhythmMasterLevelPayload(override val tick: Long, val level: Int) : TimedPayload
private data class RhythmVoiceLevelPayload(override val tick: Long, val voice: Int, val level: Int) : TimedPayload
private data class RhythmVoicePanPayload(override val tick: Long, val voice: Int, val pan: Int) : TimedPayload
