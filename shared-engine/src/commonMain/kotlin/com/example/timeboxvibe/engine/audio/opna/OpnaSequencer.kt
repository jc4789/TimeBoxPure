package com.example.timeboxvibe.engine.audio.opna

class OpnaSequencer(val sampleRate: Int, val bpm: Int, val beatsPerBar: Int = 4) {
    companion object {
        const val MAX_EVENTS_PER_CHANNEL = 1024
    }

    // FM events: channel, midi, startSample, durationSamples
    private val fmChannelIdx    = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val fmMidi          = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val fmStartSample   = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val fmDurationSamp  = LongArray(MAX_EVENTS_PER_CHANNEL)
    private var fmEventCount    = 0

    // SSG events: channel, midi, startSample, durationSamples
    private val ssgChannelIdx   = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgMidi         = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgStartSample  = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgDurSamp      = LongArray(MAX_EVENTS_PER_CHANNEL)
    private var ssgEventCount   = 0

    // Drum events: kind, startSample
    private val drumKind        = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val drumStartSample = LongArray(MAX_EVENTS_PER_CHANNEL)
    private var drumEventCount  = 0

    var customLoopLength: Long = 0L

    fun beatToSample(beat: Float): Long = (beat * 60f * sampleRate / bpm).toLong()
    fun barToSample(bar: Int): Long = (bar * beatsPerBar * 60f * sampleRate / bpm).toLong()

    fun noteFm(channel: Int, midi: Int, atBeat: Float, durBeats: Float) {
        val idx = fmEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        fmChannelIdx[idx]    = channel
        fmMidi[idx]          = midi
        fmStartSample[idx]   = beatToSample(atBeat)
        fmDurationSamp[idx]  = beatToSample(durBeats)
        fmEventCount = idx + 1
    }

    fun noteFmRaw(channel: Int, midi: Int, startSample: Long, durationSamples: Long) {
        val idx = fmEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        fmChannelIdx[idx]    = channel
        fmMidi[idx]          = midi
        fmStartSample[idx]   = startSample
        fmDurationSamp[idx]  = durationSamples
        fmEventCount = idx + 1
    }

    fun noteSsg(channel: Int, midi: Int, atBeat: Float, durBeats: Float) {
        val idx = ssgEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        ssgChannelIdx[idx]   = channel
        ssgMidi[idx]         = midi
        ssgStartSample[idx]  = beatToSample(atBeat)
        ssgDurSamp[idx]      = beatToSample(durBeats)
        ssgEventCount = idx + 1
    }

    fun noteSsgRaw(channel: Int, midi: Int, startSample: Long, durationSamples: Long) {
        val idx = ssgEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        ssgChannelIdx[idx]   = channel
        ssgMidi[idx]         = midi
        ssgStartSample[idx]  = startSample
        ssgDurSamp[idx]      = durationSamples
        ssgEventCount = idx + 1
    }

    fun noteDrum(kind: ProceduralDrums.DrumKind, atBeat: Float) {
        val idx = drumEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        drumKind[idx]        = kind.ordinal
        drumStartSample[idx] = beatToSample(atBeat)
        drumEventCount = idx + 1
    }

    fun noteDrumRaw(kind: ProceduralDrums.DrumKind, startSample: Long) {
        val idx = drumEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        drumKind[idx]        = kind.ordinal
        drumStartSample[idx] = startSample
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
        // D18: loopLength = bars * beatsPerBar * sampleRate * 60 / bpm
        // Defaulting to 4 bars for typical motif loops
        return (4L * beatsPerBar * sampleRate * 60) / bpm
    }

    private fun isSampleInChunk(sample: Long, chunkStartGlobal: Long, chunkSize: Int): Boolean {
        val loopLen = loopLengthSamples()
        if (loopLen <= 0) return false
        val chunkStart = chunkStartGlobal % loopLen
        val chunkEnd = chunkStart + chunkSize
        return if (chunkEnd <= loopLen) {
            sample >= chunkStart && sample < chunkEnd
        } else {
            sample >= chunkStart || sample < (chunkEnd % loopLen)
        }
    }

    fun writeInto(synth: OpnaLikeSynthesizer, loopOffsetSample: Long, chunkSize: Int) {
        // FM events
        var i = 0
        while (i < fmEventCount) {
            val start = fmStartSample[i]
            val duration = fmDurationSamp[i]
            val end = start + duration

            if (isSampleInChunk(start, loopOffsetSample, chunkSize)) {
                synth.noteOnFm(fmChannelIdx[i], fmMidi[i])
            }
            if (isSampleInChunk(end, loopOffsetSample, chunkSize)) {
                synth.noteOffFm(fmChannelIdx[i])
            }
            i++
        }

        // SSG events
        i = 0
        while (i < ssgEventCount) {
            val start = ssgStartSample[i]
            val duration = ssgDurSamp[i]
            val end = start + duration

            if (isSampleInChunk(start, loopOffsetSample, chunkSize)) {
                synth.noteOnSsg(ssgChannelIdx[i], ssgMidi[i])
            }
            if (isSampleInChunk(end, loopOffsetSample, chunkSize)) {
                synth.noteOffSsg(ssgChannelIdx[i])
            }
            i++
        }

        // Drum events
        i = 0
        while (i < drumEventCount) {
            val start = drumStartSample[i]
            if (isSampleInChunk(start, loopOffsetSample, chunkSize)) {
                val kind = drumKind[i]
                synth.triggerDrum(kind)
            }
            i++
        }
    }
}
