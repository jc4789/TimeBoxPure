package com.example.timeboxvibe.engine.audio.opna

class OpnaSequencer(val sampleRate: Int, val bpm: Int, val beatsPerBar: Int = 4) {
    companion object {
        const val MAX_EVENTS_PER_CHANNEL = 1024
    }

    private val fmChannelIdx    = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val fmMidi          = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val fmStartSample   = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val fmDurationSamp  = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val fmVelocity      = FloatArray(MAX_EVENTS_PER_CHANNEL)
    private val fmAttack        = FloatArray(MAX_EVENTS_PER_CHANNEL)
    private val fmDecay         = FloatArray(MAX_EVENTS_PER_CHANNEL)
    private val fmSustain       = FloatArray(MAX_EVENTS_PER_CHANNEL)
    private val fmRelease       = FloatArray(MAX_EVENTS_PER_CHANNEL)
    private var fmEventCount    = 0

    private val ssgChannelIdx   = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgMidi         = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgStartSample  = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgDurSamp      = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgVelocity     = FloatArray(MAX_EVENTS_PER_CHANNEL)
    private var ssgEventCount   = 0

    private val drumKind        = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val drumStartSample = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val drumVelocity    = FloatArray(MAX_EVENTS_PER_CHANNEL)
    private var drumEventCount  = 0

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
        var i = 0
        while (i < fmEventCount) {
            val start = fmStartSample[i]
            val duration = fmDurationSamp[i]
            val end = start + duration

            if (isSampleInChunk(start, loopOffsetSample, chunkSize)) {
                val attack = if (fmAttack[i] > 0f) fmAttack[i] else null
                val decay = if (fmDecay[i] > 0f) fmDecay[i] else null
                val sustain = if (fmSustain[i] > 0f) fmSustain[i] else null
                val release = if (fmRelease[i] > 0f) fmRelease[i] else null
                synth.noteOnFm(
                    fmChannelIdx[i], fmMidi[i],
                    attack, decay, sustain, release
                )
                synth.fm[fmChannelIdx[i]].noteGain = fmVelocity[i]
            }
            if (isSampleInChunk(end, loopOffsetSample, chunkSize)) {
                synth.noteOffFm(fmChannelIdx[i])
            }
            i++
        }

        i = 0
        while (i < ssgEventCount) {
            val start = ssgStartSample[i]
            val duration = ssgDurSamp[i]
            val end = start + duration

            if (isSampleInChunk(start, loopOffsetSample, chunkSize)) {
                synth.noteOnSsg(ssgChannelIdx[i], ssgMidi[i])
                synth.ssg[ssgChannelIdx[i]].noteGain = ssgVelocity[i]
            }
            if (isSampleInChunk(end, loopOffsetSample, chunkSize)) {
                synth.noteOffSsg(ssgChannelIdx[i])
            }
            i++
        }

        i = 0
        while (i < drumEventCount) {
            val start = drumStartSample[i]
            if (isSampleInChunk(start, loopOffsetSample, chunkSize)) {
                val kind = drumKind[i]
                val velocity = drumVelocity[i]
                synth.triggerDrum(kind, velocity)
            }
            i++
        }
    }
}
