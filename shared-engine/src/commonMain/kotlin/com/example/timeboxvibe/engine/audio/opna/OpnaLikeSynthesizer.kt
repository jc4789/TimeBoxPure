package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import com.example.timeboxvibe.engine.SongEqBand

class OpnaLikeSynthesizer(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {
    internal val mixer = OpnaMixer(sampleRate)
    val ssg: Array<SsgVoice> = Array(AudioLaws.SSG_CHANNELS) { SsgVoice(it) }
    val fm: Array<Fm4OpVoice> = Array(AudioLaws.FM_CHANNELS) { Fm4OpVoice(sampleRate) }
    val drums = ProceduralDrums()
    val lfo = Lfo(sampleRate)

    var preClampPeak: Float = 0f
    var preClampKneeCrossings: Int = 0
        private set

    private val fmActiveNoteId = IntArray(AudioLaws.FM_CHANNELS) { -1 }
    private val ssgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { -1 }
    private val tempMonoBuffer = FloatArray(sampleRate)

    private var filterStateL: Float = 0f
    private var filterStateR: Float = 0f
    private val masterEq = MasterPeakEq(sampleRate)
    var filterAlpha: Float = 0.50f
    var enableOutputFilter: Boolean = true

    init {
        OpnLogTables.warmUp()
        DrumSinLut.warmUp()
        OpnEnvelopeCompatibility.warmUp()
    }

    fun noteOnSsg(channel: Int, midi: Int) {
        if (channel in ssg.indices) {
            ssg[channel].noteOn(midiToFreq(midi))
        }
    }

    fun noteOffSsg(channel: Int) {
        if (channel in ssg.indices) {
            ssg[channel].noteOff()
        }
    }

    fun noteOnFm(channel: Int, midi: Int) {
        noteOnFm(channel, midi, null, null, null, null)
    }

    fun noteOnFm(
        channel: Int,
        midi: Int,
        attack: Float?,
        decay: Float?,
        sustain: Float?,
        release: Float?
    ) {
        if (channel in fm.indices) {
            fm[channel].noteOn(midi, attack, decay, sustain, release)
        }
    }

    fun noteOnFm(channel: Int, midi: Int, patch: FmPatch) {
        if (channel in fm.indices) {
            fm[channel].applyPatch(patch)
            fm[channel].noteOn(midi)
        }
    }

    fun noteOffFm(channel: Int) {
        if (channel in fm.indices) {
            fm[channel].noteOff()
        }
    }

    fun triggerDrum(kind: Int, velocity: Float = 1f) {
        when (kind) {
            0, ProceduralDrums.DrumKind.KICK.ordinal -> {
                drums.kickGain = velocity
                drums.triggerKick()
            }
            1, ProceduralDrums.DrumKind.SNARE.ordinal -> {
                drums.snareGain = velocity
                drums.triggerSnare()
            }
            2, ProceduralDrums.DrumKind.HAT.ordinal -> {
                drums.hatGain = velocity
                drums.triggerHat()
            }
            3, ProceduralDrums.DrumKind.TOM.ordinal -> {
                drums.tomGain = velocity
                drums.triggerTom(150f)
            }
        }
    }

    fun allNotesOff() {
        var i = 0
        while (i < ssg.size) {
            ssg[i].reset()
            ssgActiveNoteId[i] = -1
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].clearActiveNote()
            fmActiveNoteId[i] = -1
            i++
        }
        drums.stopAll()
    }

    fun reset() {
        var i = 0
        while (i < ssg.size) {
            ssg[i].reset()
            ssgActiveNoteId[i] = -1
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].reset()
            fmActiveNoteId[i] = -1
            i++
        }
        drums.reset()
        lfo.reset()
        filterStateL = 0f
        filterStateR = 0f
        masterEq.reset()
    }

    fun configureMasterEq(bands: List<SongEqBand>) {
        masterEq.configure(bands)
    }

    fun render(buffer: FloatArray, frames: Int) {
        buffer.fill(0f)
        var offset = 0
        var remaining = frames
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            renderSegment(buffer, offset, chunkFrames)
            offset += chunkFrames
            remaining -= chunkFrames
        }
        applyGainAndClamp(buffer, frames)
        masterEq.processMono(buffer, frames)
    }

    fun render(buffer: FloatArray, frames: Int, sequencer: OpnaSequencer, currentSampleOffset: Long) {
        if (!sequencer.isSorted) {
            sequencer.sortEvents()
        }
        buffer.fill(0f)
        var offset = 0
        var remaining = frames
        var sampleOffset = currentSampleOffset
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            renderWithSequencer(buffer, offset, chunkFrames, sequencer, sampleOffset)
            offset += chunkFrames
            remaining -= chunkFrames
            sampleOffset += chunkFrames
        }
        applyGainAndClamp(buffer, frames)
        masterEq.processMono(buffer, frames)
    }

    fun renderStereo(stereoBuffer: FloatArray, frames: Int) {
        stereoBuffer.fill(0f)
        var offset = 0
        var remaining = frames
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            renderStereoSegment(stereoBuffer, offset, chunkFrames)
            offset += chunkFrames
            remaining -= chunkFrames
        }
        applyGainAndClampStereo(stereoBuffer, frames)
        masterEq.processStereo(stereoBuffer, frames)
    }

    fun renderStereo(stereoBuffer: FloatArray, frames: Int, sequencer: OpnaSequencer, currentSampleOffset: Long) {
        if (!sequencer.isSorted) {
            sequencer.sortEvents()
        }
        stereoBuffer.fill(0f)
        var offset = 0
        var remaining = frames
        var sampleOffset = currentSampleOffset
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            renderStereoWithSequencer(stereoBuffer, offset, chunkFrames, sequencer, sampleOffset)
            offset += chunkFrames
            remaining -= chunkFrames
            sampleOffset += chunkFrames
        }
        applyGainAndClampStereo(stereoBuffer, frames)
        masterEq.processStereo(stereoBuffer, frames)
    }

    private fun renderSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        if (frames <= 0) return
        var i = 0
        while (i < ssg.size) {
            ssg[i].render(buffer, frames, sampleRate, mixer.ssgGain, startFrame)
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].render(buffer, frames, sampleRate, mixer.fmGain, startFrame)
            i++
        }
        drums.render(buffer, frames, sampleRate, mixer.rhythmGain, startFrame)
    }

    private fun renderStereoSegment(stereoBuffer: FloatArray, startFrame: Int, frames: Int) {
        if (frames <= 0) return

        var i = 0
        while (i < ssg.size) {
            ensureTempBuffer(frames)
            tempMonoBuffer.fill(0f, 0, frames)
            ssg[i].render(tempMonoBuffer, frames, sampleRate, mixer.ssgGain)
            panMonoToStereo(tempMonoBuffer, stereoBuffer, frames, startFrame, 0)
            i++
        }

        i = 0
        while (i < fm.size) {
            ensureTempBuffer(frames)
            tempMonoBuffer.fill(0f, 0, frames)
            fm[i].render(tempMonoBuffer, frames, sampleRate, mixer.fmGain)
            val pan = fm[i].getPan()
            panMonoToStereo(tempMonoBuffer, stereoBuffer, frames, startFrame, pan)
            i++
        }

        ensureTempBuffer(frames)
        tempMonoBuffer.fill(0f, 0, frames)
        drums.render(tempMonoBuffer, frames, sampleRate, mixer.rhythmGain)
        panMonoToStereo(tempMonoBuffer, stereoBuffer, frames, startFrame, 0)
    }

    private fun ensureTempBuffer(frames: Int) {
        if (tempMonoBuffer.size < frames) {
        }
    }

    private fun panMonoToStereo(
        mono: FloatArray,
        stereo: FloatArray,
        frames: Int,
        startFrame: Int,
        pan: Int
    ) {
        val leftGain: Float
        val rightGain: Float
        when (pan) {
            1 -> {
                leftGain = 1f
                rightGain = 0f
            }
            2 -> {
                leftGain = 0f
                rightGain = 1f
            }
            else -> {
                leftGain = 0.707f
                rightGain = 0.707f
            }
        }
        var i = 0
        while (i < frames) {
            val sample = mono[i]
            val stereoIdx = (startFrame + i) * 2
            stereo[stereoIdx] += sample * leftGain
            stereo[stereoIdx + 1] += sample * rightGain
            i++
        }
    }

    private fun renderWithSequencer(
        buffer: FloatArray,
        startFrameOffset: Int,
        frames: Int,
        sequencer: OpnaSequencer,
        currentSampleOffset: Long
    ) {
        val chunkEnd = currentSampleOffset + frames
        var renderPos = 0

        while (sequencer.nextEventIdx < sequencer.eventCount &&
               sequencer.events[sequencer.nextEventIdx].sampleTime < currentSampleOffset) {
            sequencer.nextEventIdx++
        }

        while (renderPos < frames) {
            var nextEvent: SequencerEvent? = null
            if (sequencer.nextEventIdx < sequencer.eventCount) {
                val ev = sequencer.events[sequencer.nextEventIdx]
                if (ev.sampleTime >= currentSampleOffset && ev.sampleTime < chunkEnd) {
                    nextEvent = ev
                }
            }

            if (nextEvent == null) {
                renderSegment(buffer, startFrameOffset + renderPos, frames - renderPos)
                break
            }

            val eventOffset = (nextEvent.sampleTime - currentSampleOffset).toInt()
            if (eventOffset > renderPos) {
                renderSegment(buffer, startFrameOffset + renderPos, eventOffset - renderPos)
                renderPos = eventOffset
            }

            when (nextEvent.type) {
                SequencerEvent.FM_ON -> {
                    val ch = nextEvent.channel
                    if (ch >= 0 && ch < fm.size) {
                        fm[ch].noteOnScheduled(
                            nextEvent.midi,
                            nextEvent.attack,
                            nextEvent.decay,
                            nextEvent.sustain,
                            nextEvent.release
                        )
                        fm[ch].noteGain = nextEvent.velocity
                        fmActiveNoteId[ch] = nextEvent.noteId
                    }
                }
                SequencerEvent.FM_OFF -> {
                    val ch = nextEvent.channel
                    if (ch >= 0 && ch < fm.size && fmActiveNoteId[ch] == nextEvent.noteId) {
                        fm[ch].noteOff()
                        fmActiveNoteId[ch] = -1
                    }
                }
                SequencerEvent.SSG_ON -> {
                    val ch = nextEvent.channel
                    if (ch >= 0 && ch < ssg.size) {
                        ssg[ch].duty = nextEvent.duty
                        if (nextEvent.attack >= 0f) ssg[ch].env.attack = nextEvent.attack
                        if (nextEvent.decay >= 0f) ssg[ch].env.decay = nextEvent.decay
                        if (nextEvent.sustain >= 0f) ssg[ch].env.sustain = nextEvent.sustain
                        if (nextEvent.release >= 0f) ssg[ch].env.release = nextEvent.release
                        ssg[ch].noteOn(midiToFreq(nextEvent.midi))
                        ssg[ch].noteGain = nextEvent.velocity
                        ssgActiveNoteId[ch] = nextEvent.noteId
                    }
                }
                SequencerEvent.SSG_OFF -> {
                    val ch = nextEvent.channel
                    if (ch >= 0 && ch < ssg.size && ssgActiveNoteId[ch] == nextEvent.noteId) {
                        ssg[ch].noteOff()
                        ssgActiveNoteId[ch] = -1
                    }
                }
                SequencerEvent.DRUM -> {
                    triggerDrum(nextEvent.midi, nextEvent.velocity)
                }
            }

            sequencer.nextEventIdx++
        }
    }

    private fun renderStereoWithSequencer(
        stereoBuffer: FloatArray,
        startFrameOffset: Int,
        frames: Int,
        sequencer: OpnaSequencer,
        currentSampleOffset: Long
    ) {
        val chunkEnd = currentSampleOffset + frames
        var renderPos = 0

        while (sequencer.nextEventIdx < sequencer.eventCount &&
               sequencer.events[sequencer.nextEventIdx].sampleTime < currentSampleOffset) {
            sequencer.nextEventIdx++
        }

        while (renderPos < frames) {
            var nextEvent: SequencerEvent? = null
            if (sequencer.nextEventIdx < sequencer.eventCount) {
                val ev = sequencer.events[sequencer.nextEventIdx]
                if (ev.sampleTime >= currentSampleOffset && ev.sampleTime < chunkEnd) {
                    nextEvent = ev
                }
            }

            if (nextEvent == null) {
                renderStereoSegment(stereoBuffer, startFrameOffset + renderPos, frames - renderPos)
                break
            }

            val eventOffset = (nextEvent.sampleTime - currentSampleOffset).toInt()
            if (eventOffset > renderPos) {
                renderStereoSegment(stereoBuffer, startFrameOffset + renderPos, eventOffset - renderPos)
                renderPos = eventOffset
            }

            when (nextEvent.type) {
                SequencerEvent.FM_ON -> {
                    val ch = nextEvent.channel
                    if (ch >= 0 && ch < fm.size) {
                        fm[ch].noteOnScheduled(
                            nextEvent.midi,
                            nextEvent.attack,
                            nextEvent.decay,
                            nextEvent.sustain,
                            nextEvent.release
                        )
                        fm[ch].noteGain = nextEvent.velocity
                        fmActiveNoteId[ch] = nextEvent.noteId
                    }
                }
                SequencerEvent.FM_OFF -> {
                    val ch = nextEvent.channel
                    if (ch >= 0 && ch < fm.size && fmActiveNoteId[ch] == nextEvent.noteId) {
                        fm[ch].noteOff()
                        fmActiveNoteId[ch] = -1
                    }
                }
                SequencerEvent.SSG_ON -> {
                    val ch = nextEvent.channel
                    if (ch >= 0 && ch < ssg.size) {
                        ssg[ch].duty = nextEvent.duty
                        ssg[ch].noteOn(midiToFreq(nextEvent.midi))
                        ssg[ch].noteGain = nextEvent.velocity
                        ssgActiveNoteId[ch] = nextEvent.noteId
                    }
                }
                SequencerEvent.SSG_OFF -> {
                    val ch = nextEvent.channel
                    if (ch >= 0 && ch < ssg.size && ssgActiveNoteId[ch] == nextEvent.noteId) {
                        ssg[ch].noteOff()
                        ssgActiveNoteId[ch] = -1
                    }
                }
                SequencerEvent.DRUM -> {
                    triggerDrum(nextEvent.midi, nextEvent.velocity)
                }
            }

            sequencer.nextEventIdx++
        }
    }

    private fun softClip(x: Float): Float {
        val limit = SOFT_CLIP_KNEE
        val excessScale = 1.0f - limit
        return if (x > limit) {
            val diff = x - limit
            limit + excessScale * (diff / (diff + excessScale))
        } else if (x < -limit) {
            val diff = -x - limit
            -(limit + excessScale * (diff / (diff + excessScale)))
        } else {
            x
        }
    }

    private fun applyGainAndClamp(buffer: FloatArray, frames: Int) {
        val outputGain = AudioLaws.OPNA_OUTPUT_GAIN * AudioLaws.CHIP_MIX_HEADROOM *
            OpnaAudioConstants.MASTER_GAIN
        var peak = 0f
        var kneeCrossings = 0
        var i = 0
        while (i < frames) {
            val x = buffer[i] * outputGain
            val filtered = if (enableOutputFilter) {
                val f = (1f - filterAlpha) * x + filterAlpha * filterStateL
                filterStateL = f
                f
            } else {
                x
            }
            val absX = if (filtered < 0f) -filtered else filtered
            if (absX > peak) peak = absX
            if (absX > SOFT_CLIP_KNEE) kneeCrossings++
            buffer[i] = softClip(filtered)
            i++
        }
        preClampPeak = peak
        preClampKneeCrossings = kneeCrossings
    }

    private fun applyGainAndClampStereo(stereoBuffer: FloatArray, frames: Int) {
        val outputGain = AudioLaws.OPNA_OUTPUT_GAIN * AudioLaws.CHIP_MIX_HEADROOM *
            OpnaAudioConstants.MASTER_GAIN
        val totalSamples = frames * 2
        var peak = 0f
        var kneeCrossings = 0
        var i = 0
        while (i < totalSamples) {
            val x = stereoBuffer[i] * outputGain
            val isLeft = (i % 2) == 0
            val filtered = if (enableOutputFilter) {
                if (isLeft) {
                    val f = (1f - filterAlpha) * x + filterAlpha * filterStateL
                    filterStateL = f
                    f
                } else {
                    val f = (1f - filterAlpha) * x + filterAlpha * filterStateR
                    filterStateR = f
                    f
                }
            } else {
                x
            }
            val absX = if (filtered < 0f) -filtered else filtered
            if (absX > peak) peak = absX
            if (absX > SOFT_CLIP_KNEE) kneeCrossings++
            stereoBuffer[i] = softClip(filtered)
            i++
        }
        preClampPeak = peak
        preClampKneeCrossings = kneeCrossings
    }

    companion object {
        const val MAX_FRAMES_PER_CHUNK = 1024
        const val SOFT_CLIP_KNEE = 0.70f
    }
}
