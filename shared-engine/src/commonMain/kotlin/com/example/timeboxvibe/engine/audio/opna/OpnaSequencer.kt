package com.example.timeboxvibe.engine.audio.opna

internal class SequencerEvent {
    companion object {
        const val FM_ON = 0
        const val FM_OFF = 1
        const val SSG_ON = 2
        const val SSG_OFF = 3
        const val DRUM = 4
    }

    var type: Int = 0
    var sampleTime: Long = 0L
    var channel: Int = 0
    var midi: Int = 0
    var velocity: Float = 0f
    var noteId: Int = 0
    var attack: Float = -1f
    var decay: Float = -1f
    var sustain: Float = -1f
    var release: Float = -1f
}

class OpnaSequencer(val sampleRate: Int, val bpm: Float, val beatsPerBar: Int = 4) {
    companion object {
        const val MAX_EVENTS = 4096
    }

    internal val events = Array(MAX_EVENTS) { SequencerEvent() }
    internal var eventCount = 0
    var nextEventIdx = 0
    var isSorted = false

    private var noteIdCounter = 1

    private fun nextNoteId(): Int {
        val id = noteIdCounter
        noteIdCounter = if (noteIdCounter == Int.MAX_VALUE) 1 else noteIdCounter + 1
        return id
    }

    var customLoopLength: Long = 0L

    fun beatToSample(beat: Float): Long = (beat * 60f * sampleRate / bpm).toLong()
    fun barToSample(bar: Int): Long = (bar * beatsPerBar * 60f * sampleRate / bpm).toLong()

    fun noteFm(channel: Int, midi: Int, atBeat: Float, durBeats: Float) {
        noteFmRaw(channel, midi, beatToSample(atBeat), beatToSample(durBeats))
    }

    fun noteFmRaw(channel: Int, midi: Int, startSample: Long, durationSamples: Long) {
        noteFmRaw(channel, midi, startSample, durationSamples, 1f, null, null, null, null)
    }

    fun noteFmRaw(
        channel: Int,
        midi: Int,
        startSample: Long,
        durationSamples: Long,
        velocity: Float,
        attack: Float?,
        decay: Float?,
        sustain: Float?,
        release: Float?
    ) {
        if (eventCount + 2 > MAX_EVENTS) return
        val noteId = nextNoteId()
        isSorted = false

        // 1. FM_ON Event
        val onEv = events[eventCount++]
        onEv.type = SequencerEvent.FM_ON
        onEv.sampleTime = startSample
        onEv.channel = channel
        onEv.midi = midi
        onEv.velocity = velocity
        onEv.noteId = noteId
        onEv.attack = attack ?: -1f
        onEv.decay = decay ?: -1f
        onEv.sustain = sustain ?: -1f
        onEv.release = release ?: -1f

        // 2. FM_OFF Event
        val offEv = events[eventCount++]
        offEv.type = SequencerEvent.FM_OFF
        offEv.sampleTime = startSample + durationSamples
        offEv.channel = channel
        offEv.midi = midi
        offEv.velocity = 0f
        offEv.noteId = noteId
        offEv.attack = -1f
        offEv.decay = -1f
        offEv.sustain = -1f
        offEv.release = -1f
    }

    fun noteSsg(channel: Int, midi: Int, atBeat: Float, durBeats: Float) {
        noteSsgRaw(channel, midi, beatToSample(atBeat), beatToSample(durBeats))
    }

    fun noteSsgRaw(channel: Int, midi: Int, startSample: Long, durationSamples: Long) {
        noteSsgRaw(channel, midi, startSample, durationSamples, 1f)
    }

    fun noteSsgRaw(
        channel: Int,
        midi: Int,
        startSample: Long,
        durationSamples: Long,
        velocity: Float
    ) {
        if (eventCount + 2 > MAX_EVENTS) return
        val noteId = nextNoteId()
        isSorted = false

        // 1. SSG_ON Event
        val onEv = events[eventCount++]
        onEv.type = SequencerEvent.SSG_ON
        onEv.sampleTime = startSample
        onEv.channel = channel
        onEv.midi = midi
        onEv.velocity = velocity
        onEv.noteId = noteId
        onEv.attack = -1f
        onEv.decay = -1f
        onEv.sustain = -1f
        onEv.release = -1f

        // 2. SSG_OFF Event
        val offEv = events[eventCount++]
        offEv.type = SequencerEvent.SSG_OFF
        offEv.sampleTime = startSample + durationSamples
        offEv.channel = channel
        offEv.midi = midi
        offEv.velocity = 0f
        offEv.noteId = noteId
        offEv.attack = -1f
        offEv.decay = -1f
        offEv.sustain = -1f
        offEv.release = -1f
    }

    fun noteDrum(kind: ProceduralDrums.DrumKind, atBeat: Float) {
        noteDrumRaw(kind, beatToSample(atBeat))
    }

    fun noteDrumRaw(kind: ProceduralDrums.DrumKind, startSample: Long) {
        noteDrumRaw(kind, startSample, 1f)
    }

    fun noteDrumRaw(kind: ProceduralDrums.DrumKind, startSample: Long, velocity: Float) {
        if (eventCount + 1 > MAX_EVENTS) return
        isSorted = false

        val ev = events[eventCount++]
        ev.type = SequencerEvent.DRUM
        ev.sampleTime = startSample
        ev.channel = 0
        ev.midi = kind.ordinal
        ev.velocity = velocity
        ev.noteId = 0
        ev.attack = -1f
        ev.decay = -1f
        ev.sustain = -1f
        ev.release = -1f
    }

    fun clear() {
        eventCount = 0
        nextEventIdx = 0
        noteIdCounter = 1
        customLoopLength = 0L
        isSorted = false
    }

    fun loopLengthSamples(): Long {
        if (customLoopLength > 0L) return customLoopLength
        return (4L * beatsPerBar * sampleRate * 60 / bpm).toLong()
    }

    fun sortEvents() {
        var i = 1
        while (i < eventCount) {
            val key = events[i]
            var j = i - 1
            while (j >= 0 && events[j].sampleTime > key.sampleTime) {
                events[j + 1] = events[j]
                j--
            }
            events[j + 1] = key
            i++
        }
        isSorted = true
    }
}
