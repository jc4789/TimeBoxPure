package com.example.timeboxvibe.engine.audio.opna

class OpnaSequencer(val sampleRate: Int, val bpm: Int, val beatsPerBar: Int = 4) {
    companion object {
        const val MAX_EVENTS_PER_CHANNEL = 1024
    }

    internal val fmChannelIdx    = IntArray(MAX_EVENTS_PER_CHANNEL)
    internal val fmMidi          = IntArray(MAX_EVENTS_PER_CHANNEL)
    internal val fmStartSample   = LongArray(MAX_EVENTS_PER_CHANNEL)
    internal val fmDurationSamp  = LongArray(MAX_EVENTS_PER_CHANNEL)
    internal val fmVelocity      = FloatArray(MAX_EVENTS_PER_CHANNEL)
    internal val fmAttack        = FloatArray(MAX_EVENTS_PER_CHANNEL)
    internal val fmDecay         = FloatArray(MAX_EVENTS_PER_CHANNEL)
    internal val fmSustain       = FloatArray(MAX_EVENTS_PER_CHANNEL)
    internal val fmRelease       = FloatArray(MAX_EVENTS_PER_CHANNEL)
    internal var fmEventCount    = 0

    internal val ssgChannelIdx   = IntArray(MAX_EVENTS_PER_CHANNEL)
    internal val ssgMidi         = IntArray(MAX_EVENTS_PER_CHANNEL)
    internal val ssgStartSample  = LongArray(MAX_EVENTS_PER_CHANNEL)
    internal val ssgDurSamp      = LongArray(MAX_EVENTS_PER_CHANNEL)
    internal val ssgVelocity     = FloatArray(MAX_EVENTS_PER_CHANNEL)
    internal var ssgEventCount   = 0

    internal val drumKind        = IntArray(MAX_EVENTS_PER_CHANNEL)
    internal val drumStartSample = LongArray(MAX_EVENTS_PER_CHANNEL)
    internal val drumVelocity    = FloatArray(MAX_EVENTS_PER_CHANNEL)
    internal var drumEventCount  = 0

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
        val idx = fmEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        fmChannelIdx[idx]    = channel
        fmMidi[idx]          = midi
        fmStartSample[idx]   = startSample
        fmDurationSamp[idx]  = durationSamples
        fmVelocity[idx]      = velocity
        fmAttack[idx]        = attack ?: 0f
        fmDecay[idx]         = decay ?: 0f
        fmSustain[idx]       = sustain ?: 0f
        fmRelease[idx]       = release ?: 0f
        fmEventCount = idx + 1
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
        val idx = ssgEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        ssgChannelIdx[idx]   = channel
        ssgMidi[idx]         = midi
        ssgStartSample[idx]  = startSample
        ssgDurSamp[idx]      = durationSamples
        ssgVelocity[idx]     = velocity
        ssgEventCount = idx + 1
    }

    fun noteDrum(kind: ProceduralDrums.DrumKind, atBeat: Float) {
        noteDrumRaw(kind, beatToSample(atBeat))
    }

    fun noteDrumRaw(kind: ProceduralDrums.DrumKind, startSample: Long) {
        noteDrumRaw(kind, startSample, 1f)
    }

    fun noteDrumRaw(kind: ProceduralDrums.DrumKind, startSample: Long, velocity: Float) {
        val idx = drumEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        drumKind[idx]        = kind.ordinal
        drumStartSample[idx] = startSample
        drumVelocity[idx]    = velocity
        drumEventCount = idx + 1
    }

    fun clear() {
        fmEventCount = 0
        ssgEventCount = 0
        drumEventCount = 0
        customLoopLength = 0L
    }

    fun loopLengthSamples(): Long {
        if (customLoopLength > 0L) return customLoopLength
        return (4L * beatsPerBar * sampleRate * 60) / bpm
    }
}
