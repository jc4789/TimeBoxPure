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
    val beatsPerBar: Int,
    val lfoRate: Int,
    val fm3Extended: Boolean,
    internal val tempoChangeCount: Int,
    internal val tempoTick: LongArray,
    internal val tempoBpm: FloatArray,
    val durationTicks: Long,
    internal val eventCount: Int,
    internal val eventType: IntArray,
    internal val startTick: LongArray,
    internal val durationTick: IntArray,
    internal val gateTick: IntArray,
    internal val channel: IntArray,
    internal val operator: IntArray,
    internal val midi: IntArray,
    internal val targetMidi: IntArray,
    internal val velocity: IntArray,
    internal val patchId: IntArray,
    internal val pan: IntArray,
    internal val detuneCents: IntArray,
    internal val pms: IntArray,
    internal val ams: IntArray,
    internal val lfoDelayTick: IntArray
) {
    fun durationMilliseconds(): Long {
        var milliseconds = 0.0
        var previousTick = 0L
        var currentBpm = bpm
        var i = 0
        while (i < tempoChangeCount && tempoTick[i] < durationTicks) {
            val changeTick = tempoTick[i].coerceAtLeast(previousTick)
            milliseconds += ticksToMilliseconds(changeTick - previousTick, currentBpm)
            previousTick = changeTick
            currentBpm = tempoBpm[i]
            i++
        }
        milliseconds += ticksToMilliseconds(durationTicks - previousTick, currentBpm)
        return milliseconds.toLong()
    }

    private fun ticksToMilliseconds(ticks: Long, tempo: Float): Double =
        ticks.toDouble() * 60_000.0 / (tempo.toDouble() * TICKS_PER_QUARTER.toDouble())

    companion object {
        const val TICKS_PER_QUARTER: Int = 480
        const val MAX_EVENTS: Int = 4096

        internal const val FM_NOTE: Int = 0
        internal const val SSG_NOTE: Int = 1
        internal const val RHYTHM_SHOT: Int = 2
        internal const val FM3_OPERATOR_NOTE: Int = 3
    }
}

internal class CompiledOpnaSongBuilder(
    private val dialectVersion: Int,
    private val bpm: Float,
    private val beatsPerBar: Int,
    private val lfoRate: Int,
    private val fm3Extended: Boolean
) {
    private val tempoTick = LongArray(MAX_TEMPO_CHANGES)
    private val tempoBpm = FloatArray(MAX_TEMPO_CHANGES)
    private var tempoChangeCount = 0
    private val eventType = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val startTick = LongArray(CompiledOpnaSong.MAX_EVENTS)
    private val durationTick = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val gateTick = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val channel = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val operator = IntArray(CompiledOpnaSong.MAX_EVENTS) { -1 }
    private val midi = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val targetMidi = IntArray(CompiledOpnaSong.MAX_EVENTS) { -1 }
    private val velocity = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val patchId = IntArray(CompiledOpnaSong.MAX_EVENTS) { -1 }
    private val pan = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val detuneCents = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val pms = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val ams = IntArray(CompiledOpnaSong.MAX_EVENTS)
    private val lfoDelayTick = IntArray(CompiledOpnaSong.MAX_EVENTS)

    var size: Int = 0
        private set
    var durationTicks: Long = 0L
        private set

    fun addTempo(atTick: Long, bpm: Float): Boolean {
        if (tempoChangeCount > 0 && tempoTick[tempoChangeCount - 1] == atTick) {
            tempoBpm[tempoChangeCount - 1] = bpm
            return true
        }
        if (tempoChangeCount >= MAX_TEMPO_CHANGES) return false
        tempoTick[tempoChangeCount] = atTick
        tempoBpm[tempoChangeCount] = bpm
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
        selectedPms: Int,
        selectedAms: Int,
        delayTick: Int
    ): Boolean {
        if (size >= CompiledOpnaSong.MAX_EVENTS) return false
        val i = size
        eventType[i] = type
        startTick[i] = atTick
        durationTick[i] = duration
        gateTick[i] = gate
        channel[i] = channelIndex
        operator[i] = operatorIndex
        midi[i] = midiNote
        targetMidi[i] = targetMidiNote
        velocity[i] = fineVelocity
        patchId[i] = selectedPatchId
        pan[i] = selectedPan
        detuneCents[i] = cents
        pms[i] = selectedPms
        ams[i] = selectedAms
        lfoDelayTick[i] = delayTick
        size++
        val end = atTick + duration.toLong()
        if (end > durationTicks) durationTicks = end
        return true
    }

    fun build(): CompiledOpnaSong = CompiledOpnaSong(
        dialectVersion = dialectVersion,
        bpm = bpm,
        beatsPerBar = beatsPerBar,
        lfoRate = lfoRate,
        fm3Extended = fm3Extended,
        tempoChangeCount = tempoChangeCount,
        tempoTick = tempoTick,
        tempoBpm = tempoBpm,
        durationTicks = durationTicks,
        eventCount = size,
        eventType = eventType,
        startTick = startTick,
        durationTick = durationTick,
        gateTick = gateTick,
        channel = channel,
        operator = operator,
        midi = midi,
        targetMidi = targetMidi,
        velocity = velocity,
        patchId = patchId,
        pan = pan,
        detuneCents = detuneCents,
        pms = pms,
        ams = ams,
        lfoDelayTick = lfoDelayTick
    )

    private companion object {
        const val MAX_TEMPO_CHANGES = 128
    }
}
