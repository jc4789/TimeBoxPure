package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import com.example.timeboxvibe.engine.SongEqBand

class OpnaLikeSynthesizer(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {
    internal val mixer = OpnaMixer(sampleRate)
    private val ssgShared = SsgSharedState(sampleRate)
    val ssg: Array<SsgVoice> = Array(AudioLaws.SSG_CHANNELS) { SsgVoice(it, ssgShared, sampleRate) }
    val fm: Array<Fm4OpVoice> = Array(AudioLaws.FM_RENDER_VOICES) { Fm4OpVoice(sampleRate) }
    val drums = ProceduralDrums(sampleRate)
    val lfo = Lfo(sampleRate)

    var preClampPeak: Float = 0f
    var preClampKneeCrossings: Int = 0
        private set

    private val fmActiveNoteId = IntArray(AudioLaws.FM_RENDER_VOICES) { FM_VOICE_FREE }
    private val ssgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { -1 }
    private val fm3ActiveNoteId = IntArray(AudioLaws.FM_OPERATORS) { -1 }
    private val tempMonoBuffer = FloatArray(sampleRate)

    private var filterStateL: Float = 0f
    private var filterStateR: Float = 0f
    private val masterEq = MasterPeakEq(sampleRate)
    private val stereoResonator = ProceduralStereoResonator(sampleRate)
    var filterAlpha: Float = 0.50f
    var enableOutputFilter: Boolean = true
    var enableStereoResonator: Boolean = false

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
        if (channel in 0 until AudioLaws.FM_CHANNELS) {
            fm[channel].noteOn(midi, attack, decay, sustain, release)
        }
    }

    fun noteOnFm(channel: Int, midi: Int, patch: FmPatch) {
        if (channel in 0 until AudioLaws.FM_CHANNELS) {
            fm[channel].applyPatch(patch)
            fm[channel].noteOn(midi)
        }
    }

    fun noteOffFm(channel: Int) {
        if (channel in 0 until AudioLaws.FM_CHANNELS) {
            fm[channel].noteOff()
        }
    }

    fun triggerDrum(kind: Int, velocity: Float = 1f, pan: Int = 0) {
        when (kind) {
            0, ProceduralDrums.DrumKind.KICK.ordinal -> {
                drums.kickGain = velocity
                drums.setPan(ProceduralDrums.DrumKind.KICK, pan)
                drums.triggerKick()
            }
            1, ProceduralDrums.DrumKind.SNARE.ordinal -> {
                drums.snareGain = velocity
                drums.setPan(ProceduralDrums.DrumKind.SNARE, pan)
                drums.triggerSnare()
            }
            2, ProceduralDrums.DrumKind.HAT.ordinal -> {
                drums.hatGain = velocity
                drums.setPan(ProceduralDrums.DrumKind.HAT, pan)
                drums.triggerHat()
            }
            3, ProceduralDrums.DrumKind.TOM.ordinal -> {
                drums.tomGain = velocity
                drums.setPan(ProceduralDrums.DrumKind.TOM, pan)
                drums.triggerTom(150f)
            }
            ProceduralDrums.DrumKind.CYMBAL.ordinal -> {
                drums.cymbalGain = velocity
                drums.setPan(ProceduralDrums.DrumKind.CYMBAL, pan)
                drums.triggerCymbal()
            }
            ProceduralDrums.DrumKind.RIMSHOT.ordinal -> {
                drums.rimGain = velocity
                drums.setPan(ProceduralDrums.DrumKind.RIMSHOT, pan)
                drums.triggerRimshot()
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
        ssgShared.reset()
        i = 0
        while (i < fm3ActiveNoteId.size) {
            fm3ActiveNoteId[i] = -1
            i++
        }
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
        ssgShared.reset()
        i = 0
        while (i < fm3ActiveNoteId.size) {
            fm3ActiveNoteId[i] = -1
            i++
        }
        lfo.reset()
        filterStateL = 0f
        filterStateR = 0f
        masterEq.reset()
        stereoResonator.reset()
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
        masterEq.processMono(buffer, frames)
        applyGainAndClamp(buffer, frames)
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
        masterEq.processMono(buffer, frames)
        applyGainAndClamp(buffer, frames)
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
        if (enableStereoResonator) stereoResonator.process(stereoBuffer, frames)
        masterEq.processStereo(stereoBuffer, frames)
        applyGainAndClampStereo(stereoBuffer, frames)
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
        if (enableStereoResonator) stereoResonator.process(stereoBuffer, frames)
        masterEq.processStereo(stereoBuffer, frames)
        applyGainAndClampStereo(stereoBuffer, frames)
    }

    private fun renderSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        if (frames <= 0) return
        lfo.prepare(frames)
        ssgShared.prepare(frames)
        var i = 0
        while (i < ssg.size) {
            ssg[i].render(buffer, frames, sampleRate, mixer.ssgGain, startFrame, sharedPrepared = true)
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].render(buffer, frames, sampleRate, mixer.fmGain, startFrame, lfo)
            i++
        }
        drums.render(buffer, frames, sampleRate, mixer.rhythmGain, startFrame)
    }

    private fun renderStereoSegment(stereoBuffer: FloatArray, startFrame: Int, frames: Int) {
        if (frames <= 0) return
        lfo.prepare(frames)
        ssgShared.prepare(frames)

        var i = 0
        while (i < ssg.size) {
            ensureTempBuffer(frames)
            tempMonoBuffer.fill(0f, 0, frames)
            ssg[i].render(tempMonoBuffer, frames, sampleRate, mixer.ssgGain, sharedPrepared = true)
            panMonoToStereo(tempMonoBuffer, stereoBuffer, frames, startFrame, ssg[i].getPan())
            i++
        }

        i = 0
        while (i < fm.size) {
            ensureTempBuffer(frames)
            tempMonoBuffer.fill(0f, 0, frames)
            fm[i].render(tempMonoBuffer, frames, sampleRate, mixer.fmGain, lfo = lfo)
            val pan = fm[i].getPan()
            panMonoToStereo(tempMonoBuffer, stereoBuffer, frames, startFrame, pan)
            i++
        }

        drums.renderStereo(stereoBuffer, frames, sampleRate, mixer.rhythmGain, startFrame)
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

            handleSequencerEvent(nextEvent)

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

            handleSequencerEvent(nextEvent)

            sequencer.nextEventIdx++
        }
    }

    private fun handleSequencerEvent(event: SequencerEvent) {
        when (event.type) {
            SequencerEvent.FM_ON -> {
                val ch = event.channel
                if (ch >= 0 && ch < fm.size) {
                    val voice = fm[ch]
                    val selectedPatch = event.patch
                    if (selectedPatch != null) voice.applyPatch(selectedPatch)
                    voice.setPerformanceControls(
                        event.pan,
                        event.detuneCents,
                        event.pms,
                        event.ams,
                        event.lfoDelayFrames,
                        event.targetMidi,
                        event.slideFrames
                    )
                    voice.noteOnScheduled(event.midi, event.attack, event.decay, event.sustain, event.release)
                    voice.noteGain = event.velocity
                    fmActiveNoteId[ch] = event.noteId
                }
            }
            SequencerEvent.FM_OFF -> {
                val ch = event.channel
                if (ch >= 0 && ch < fm.size && fmActiveNoteId[ch] == event.noteId) {
                    fm[ch].noteOff()
                    fmActiveNoteId[ch] = -1
                }
            }
            SequencerEvent.FM_POLY_ON -> {
                val voiceIndex = availablePolyVoice(event.channel)
                if (voiceIndex >= 0) {
                    val voice = fm[voiceIndex]
                    val selectedPatch = event.patch
                    if (selectedPatch != null) voice.applyPatch(selectedPatch)
                    voice.setPerformanceControls(
                        event.pan,
                        event.detuneCents,
                        event.pms,
                        event.ams,
                        event.lfoDelayFrames,
                        -1,
                        0
                    )
                    voice.noteOnScheduled(event.midi, -1f, -1f, -1f, -1f)
                    voice.noteGain = event.velocity
                    fmActiveNoteId[voiceIndex] = event.noteId
                }
            }
            SequencerEvent.FM_POLY_OFF -> {
                var voiceIndex = 0
                while (voiceIndex < fmActiveNoteId.size) {
                    if (fmActiveNoteId[voiceIndex] == event.noteId) {
                        fm[voiceIndex].noteOff()
                        fmActiveNoteId[voiceIndex] = FM_VOICE_RELEASING
                        break
                    }
                    voiceIndex++
                }
            }
            SequencerEvent.SSG_ON -> {
                val ch = event.channel
                if (ch >= 0 && ch < ssg.size) {
                    val voice = ssg[ch]
                    val selectedPatch = event.ssgPatch
                    if (selectedPatch != null) voice.applyPatch(selectedPatch)
                    voice.setPan(event.pan)
                    voice.duty = event.duty
                    val frequency = OpnPitch.applyCents(midiToFreq(event.midi), event.detuneCents)
                    voice.setPitchRamp(
                        if (event.targetMidi >= 0) OpnPitch.applyCents(midiToFreq(event.targetMidi), event.detuneCents) else 0f,
                        event.slideFrames
                    )
                    voice.noteOn(frequency)
                    voice.noteGain = event.velocity
                    ssgActiveNoteId[ch] = event.noteId
                }
            }
            SequencerEvent.SSG_OFF -> {
                val ch = event.channel
                if (ch >= 0 && ch < ssg.size && ssgActiveNoteId[ch] == event.noteId) {
                    ssg[ch].noteOff()
                    ssgActiveNoteId[ch] = -1
                }
            }
            SequencerEvent.DRUM -> triggerDrum(event.midi, event.velocity, event.pan)
            SequencerEvent.FM3_OPERATOR_ON -> {
                val op = event.operator.coerceIn(0, AudioLaws.FM_OPERATORS - 1)
                val selectedPatch = event.patch
                if (selectedPatch != null) fm[2].applyPatch(selectedPatch)
                fm[2].setPerformanceControls(
                    event.pan,
                    event.detuneCents,
                    event.pms,
                    event.ams,
                    event.lfoDelayFrames,
                    -1,
                    0
                )
                fm[2].noteOnOperator(op, event.midi, event.targetMidi, event.slideFrames)
                fm[2].noteGain = event.velocity
                fm3ActiveNoteId[op] = event.noteId
            }
            SequencerEvent.FM3_OPERATOR_OFF -> {
                val op = event.operator.coerceIn(0, AudioLaws.FM_OPERATORS - 1)
                if (fm3ActiveNoteId[op] == event.noteId) {
                    fm[2].noteOffOperator(op)
                    fm3ActiveNoteId[op] = -1
                }
            }
        }
    }

    private fun availablePolyVoice(preferredChannel: Int): Int {
        reclaimFinishedPolyVoices()
        if (preferredChannel in 0 until AudioLaws.FM_CHANNELS && fmActiveNoteId[preferredChannel] == FM_VOICE_FREE) {
            return preferredChannel
        }
        var voiceIndex = AudioLaws.FM_CHANNELS
        while (voiceIndex < fmActiveNoteId.size) {
            if (fmActiveNoteId[voiceIndex] == FM_VOICE_FREE) return voiceIndex
            voiceIndex++
        }
        voiceIndex = 0
        while (voiceIndex < AudioLaws.FM_CHANNELS) {
            if (fmActiveNoteId[voiceIndex] == FM_VOICE_FREE) return voiceIndex
            voiceIndex++
        }
        voiceIndex = AudioLaws.FM_CHANNELS
        while (voiceIndex < fmActiveNoteId.size) {
            if (fmActiveNoteId[voiceIndex] == FM_VOICE_RELEASING) return voiceIndex
            voiceIndex++
        }
        voiceIndex = 0
        while (voiceIndex < AudioLaws.FM_CHANNELS) {
            if (fmActiveNoteId[voiceIndex] == FM_VOICE_RELEASING) return voiceIndex
            voiceIndex++
        }
        return -1
    }

    private fun reclaimFinishedPolyVoices() {
        var voiceIndex = 0
        while (voiceIndex < fmActiveNoteId.size) {
            if (fmActiveNoteId[voiceIndex] == FM_VOICE_RELEASING && fm[voiceIndex].releaseFinished()) {
                fmActiveNoteId[voiceIndex] = FM_VOICE_FREE
            }
            voiceIndex++
        }
    }

    internal fun activeFmVoiceCount(): Int {
        var active = 0
        var voiceIndex = 0
        while (voiceIndex < fmActiveNoteId.size) {
            if (fmActiveNoteId[voiceIndex] >= 0) active++
            voiceIndex++
        }
        return active
    }

    internal fun occupiedFmVoiceCount(): Int {
        var occupied = 0
        var voiceIndex = 0
        while (voiceIndex < fmActiveNoteId.size) {
            if (fmActiveNoteId[voiceIndex] != FM_VOICE_FREE) occupied++
            voiceIndex++
        }
        return occupied
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
        private const val FM_VOICE_FREE = -1
        private const val FM_VOICE_RELEASING = -2
        const val MAX_FRAMES_PER_CHUNK = 1024
        const val SOFT_CLIP_KNEE = 0.70f
    }
}
