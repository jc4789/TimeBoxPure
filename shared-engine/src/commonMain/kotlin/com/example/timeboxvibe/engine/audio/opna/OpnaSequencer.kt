package com.example.timeboxvibe.engine.audio.opna

internal class SequencerEvent {
    companion object {
        const val FM_ON = 0
        const val FM_OFF = 1
        const val SSG_ON = 2
        const val SSG_OFF = 3
        const val DRUM = 4
        const val FM_POLY_ON = 7
        const val FM_POLY_OFF = 8
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
    var patch: FmPatch? = null
    var ssgPatch: SsgPatch? = null
    var pan: Int = 0
    var detuneCents: Int = 0
    var pms: Int = 0
    var ams: Int = 0
    var lfoDelayFrames: Int = 0
    var targetMidi: Int = -1
    var slideFrames: Int = 0
}

class OpnaSequencer(val sampleRate: Int, val bpm: Float, val beatsPerBar: Int = 4) {
    companion object {
        // A compiled tonal note expands to ON and OFF events. This capacity
        // covers the 55-bar benchmark's dense SSG arpeggio without runtime growth.
        const val MAX_EVENTS = 8192
    }

    internal val events = Array(MAX_EVENTS) { SequencerEvent() }
    internal var eventCount = 0
    var nextEventIdx = 0
        internal set
    var isSorted = false

    private var noteIdCounter = 1

    private fun nextNoteId(): Int {
        val id = noteIdCounter
        noteIdCounter = if (noteIdCounter == Int.MAX_VALUE) 1 else noteIdCounter + 1
        return id
    }

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
        noteFmControlledRaw(
            channel, midi, startSample, durationSamples, velocity,
            attack, decay, sustain, release,
            null, 0, 0, 0, 0, 0, -1, 0
        )
    }

    fun noteFmControlledRaw(
        channel: Int,
        midi: Int,
        startSample: Long,
        durationSamples: Long,
        velocity: Float,
        attack: Float?,
        decay: Float?,
        sustain: Float?,
        release: Float?,
        patch: FmPatch?,
        pan: Int,
        detuneCents: Int,
        pms: Int,
        ams: Int,
        lfoDelayFrames: Int,
        targetMidi: Int,
        slideFrames: Int
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
        onEv.patch = patch
        onEv.ssgPatch = null
        onEv.pan = pan
        onEv.detuneCents = detuneCents
        onEv.pms = pms
        onEv.ams = ams
        onEv.lfoDelayFrames = lfoDelayFrames
        onEv.targetMidi = targetMidi
        onEv.slideFrames = slideFrames

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
        clearControls(offEv)
    }

    fun noteFmPolyControlledRaw(
        channel: Int,
        midi: Int,
        startSample: Long,
        durationSamples: Long,
        velocity: Float,
        patch: FmPatch,
        pan: Int,
        detuneCents: Int,
        pms: Int,
        ams: Int,
        lfoDelayFrames: Int
    ) {
        if (eventCount + 2 > MAX_EVENTS) return
        val noteId = nextNoteId()
        isSorted = false

        val onEv = events[eventCount++]
        onEv.type = SequencerEvent.FM_POLY_ON
        onEv.sampleTime = startSample
        onEv.channel = channel
        onEv.midi = midi
        onEv.velocity = velocity
        onEv.noteId = noteId
        onEv.attack = -1f
        onEv.decay = -1f
        onEv.sustain = -1f
        onEv.release = -1f
        onEv.patch = patch
        onEv.ssgPatch = null
        onEv.pan = pan
        onEv.detuneCents = detuneCents
        onEv.pms = pms
        onEv.ams = ams
        onEv.lfoDelayFrames = lfoDelayFrames
        onEv.targetMidi = -1
        onEv.slideFrames = 0

        val offEv = events[eventCount++]
        offEv.type = SequencerEvent.FM_POLY_OFF
        offEv.sampleTime = startSample + durationSamples
        offEv.channel = channel
        offEv.midi = midi
        offEv.velocity = 0f
        offEv.noteId = noteId
        offEv.attack = -1f
        offEv.decay = -1f
        offEv.sustain = -1f
        offEv.release = -1f
        clearControls(offEv)
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
        noteSsgControlledRaw(channel, midi, startSample, durationSamples, velocity, patch = null)
    }

    fun noteSsgControlledRaw(
        channel: Int,
        midi: Int,
        startSample: Long,
        durationSamples: Long,
        velocity: Float,
        attack: Float = -1f,
        decay: Float = -1f,
        sustain: Float = -1f,
        release: Float = -1f,
        patch: SsgPatch?,
        pan: Int = patch?.pan ?: 0,
        detuneCents: Int = 0,
        targetMidi: Int = -1,
        slideFrames: Int = 0
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
        onEv.attack = attack
        onEv.decay = decay
        onEv.sustain = sustain
        onEv.release = release
        onEv.patch = null
        onEv.ssgPatch = patch
        onEv.pan = pan
        onEv.detuneCents = detuneCents
        onEv.pms = 0
        onEv.ams = 0
        onEv.lfoDelayFrames = 0
        onEv.targetMidi = targetMidi
        onEv.slideFrames = slideFrames

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
        clearControls(offEv)
    }

    fun noteDrum(kind: ProceduralDrums.DrumKind, atBeat: Float) {
        noteDrumRaw(kind, beatToSample(atBeat))
    }

    fun noteDrumRaw(kind: ProceduralDrums.DrumKind, startSample: Long) {
        noteDrumRaw(kind, startSample, 1f)
    }

    fun noteDrumRaw(kind: ProceduralDrums.DrumKind, startSample: Long, velocity: Float) {
        noteDrumControlledRaw(kind, startSample, velocity, 0)
    }

    fun noteDrumControlledRaw(kind: ProceduralDrums.DrumKind, startSample: Long, velocity: Float, pan: Int) {
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
        clearControls(ev)
        ev.pan = pan.coerceIn(0, 2)
    }

    private fun clearControls(event: SequencerEvent) {
        event.patch = null
        event.ssgPatch = null
        event.pan = 0
        event.detuneCents = 0
        event.pms = 0
        event.ams = 0
        event.lfoDelayFrames = 0
        event.targetMidi = -1
        event.slideFrames = 0
    }

    fun clear() {
        eventCount = 0
        resetPlaybackCursor()
        noteIdCounter = 1
        isSorted = false
    }

    fun resetPlaybackCursor() {
        nextEventIdx = 0
    }

    fun loopLengthSamples(): Long {
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
