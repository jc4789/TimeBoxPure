package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import kotlin.math.abs

class OpnaLikeSynthesizer(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {
    internal val mixer = OpnaMixer(sampleRate)
    val ssg: Array<SsgVoice> = Array(AudioLaws.SSG_CHANNELS) { SsgVoice(it) }
    val fm: Array<Fm4OpVoice> = Array(AudioLaws.FM_CHANNELS) { Fm4OpVoice() }
    val drums = ProceduralDrums()
    val lfo = Lfo(sampleRate)

    private val fmActiveNoteId = IntArray(AudioLaws.FM_CHANNELS) { -1 }
    private val ssgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { -1 }
    private val tempMonoBuffer = FloatArray(sampleRate)

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
            ssg[i].noteOff()
            ssgActiveNoteId[i] = -1
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].noteOff()
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
        softClipAndGain(buffer, frames)
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
        softClipAndGain(buffer, frames)
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
        softClipAndGainStereo(stereoBuffer, frames)
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
        softClipAndGainStereo(stereoBuffer, frames)
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
        val (leftGain, rightGain) = AudioLaws.panToGains(pan)
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
                    if (ch in fm.indices) {
                        val a = if (nextEvent.attack >= 0f) nextEvent.attack else null
                        val d = if (nextEvent.decay >= 0f) nextEvent.decay else null
                        val s = if (nextEvent.sustain >= 0f) nextEvent.sustain else null
                        val r = if (nextEvent.release >= 0f) nextEvent.release else null
                        fm[ch].noteOn(nextEvent.midi, a, d, s, r)
                        fm[ch].noteGain = nextEvent.velocity
                        fmActiveNoteId[ch] = nextEvent.noteId
                    }
                }
                SequencerEvent.FM_OFF -> {
                    val ch = nextEvent.channel
                    if (ch in fm.indices && fmActiveNoteId[ch] == nextEvent.noteId) {
                        fm[ch].noteOff()
                        fmActiveNoteId[ch] = -1
                    }
                }
                SequencerEvent.SSG_ON -> {
                    val ch = nextEvent.channel
                    if (ch in ssg.indices) {
                        ssg[ch].noteOn(midiToFreq(nextEvent.midi))
                        ssg[ch].noteGain = nextEvent.velocity
                        ssgActiveNoteId[ch] = nextEvent.noteId
                    }
                }
                SequencerEvent.SSG_OFF -> {
                    val ch = nextEvent.channel
                    if (ch in ssg.indices && ssgActiveNoteId[ch] == nextEvent.noteId) {
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
                    if (ch in fm.indices) {
                        val a = if (nextEvent.attack >= 0f) nextEvent.attack else null
                        val d = if (nextEvent.decay >= 0f) nextEvent.decay else null
                        val s = if (nextEvent.sustain >= 0f) nextEvent.sustain else null
                        val r = if (nextEvent.release >= 0f) nextEvent.release else null
                        fm[ch].noteOn(nextEvent.midi, a, d, s, r)
                        fm[ch].noteGain = nextEvent.velocity
                        fmActiveNoteId[ch] = nextEvent.noteId
                    }
                }
                SequencerEvent.FM_OFF -> {
                    val ch = nextEvent.channel
                    if (ch in fm.indices && fmActiveNoteId[ch] == nextEvent.noteId) {
                        fm[ch].noteOff()
                        fmActiveNoteId[ch] = -1
                    }
                }
                SequencerEvent.SSG_ON -> {
                    val ch = nextEvent.channel
                    if (ch in ssg.indices) {
                        ssg[ch].noteOn(midiToFreq(nextEvent.midi))
                        ssg[ch].noteGain = nextEvent.velocity
                        ssgActiveNoteId[ch] = nextEvent.noteId
                    }
                }
                SequencerEvent.SSG_OFF -> {
                    val ch = nextEvent.channel
                    if (ch in ssg.indices && ssgActiveNoteId[ch] == nextEvent.noteId) {
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

    private fun softClipAndGain(buffer: FloatArray, frames: Int) {
        val outputGain = AudioLaws.OPNA_OUTPUT_GAIN * OpnaAudioConstants.MASTER_GAIN
        var i = 0
        while (i < frames) {
            val x = buffer[i] * outputGain
            buffer[i] = (x / (1f + abs(x))).coerceIn(-1f, 1f)
            i++
        }
    }

    private fun softClipAndGainStereo(stereoBuffer: FloatArray, frames: Int) {
        val outputGain = AudioLaws.OPNA_OUTPUT_GAIN * OpnaAudioConstants.MASTER_GAIN
        val totalSamples = frames * 2
        var i = 0
        while (i < totalSamples) {
            val x = stereoBuffer[i] * outputGain
            stereoBuffer[i] = (x / (1f + abs(x))).coerceIn(-1f, 1f)
            i++
        }
    }

    companion object {
        const val MAX_FRAMES_PER_CHUNK = 1024
    }
}
