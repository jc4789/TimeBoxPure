package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import com.example.timeboxvibe.engine.SongEqBand

class OpnaLikeSynthesizer(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {
    private val chip = OpnaChipState(sampleRate)
    private val mastering = SongMastering(sampleRate)
    internal val mixer: OpnaMixer get() = chip.mixer
    private val ssgShared: SsgSharedState get() = chip.ssgShared
    private val tempMonoBuffer: FloatArray get() = chip.tempMonoBuffer
    private val performance: PmdPerformanceState get() = chip.performance
    val ssg: Array<SsgVoice> get() = chip.ssg
    val fm: Array<Fm4OpVoice> get() = chip.fm
    val drums: ProceduralDrums get() = chip.legacyDrums
    private val ym2608Rhythm: Ym2608RhythmUnit get() = chip.ym2608Rhythm
    private val pmdSsgEffects: PmdSsgEffectUnit get() = chip.pmdSsgEffects
    val lfo: Lfo get() = chip.lfo

    val preClampPeak: Float get() = mastering.preClampPeak
    val preClampKneeCrossings: Int get() = mastering.preClampKneeCrossings

    private val fmActiveNoteId = IntArray(AudioLaws.FM_RENDER_VOICES) { FM_VOICE_FREE }
    private val ssgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { -1 }
    private val fm3ActiveNoteId = IntArray(AudioLaws.FM_OPERATORS) { -1 }
    var filterAlpha: Float
        get() = mastering.filterAlpha
        set(value) {
            mastering.filterAlpha = value
        }
    var enableOutputFilter: Boolean
        get() = mastering.enableOutputFilter
        set(value) {
            mastering.enableOutputFilter = value
        }
    var enableStereoResonator: Boolean
        get() = mastering.enableStereoResonator
        set(value) {
            mastering.enableStereoResonator = value
        }
    var outputProfile: OpnaOutputProfile = OpnaOutputProfile.TIMEBOX_LEGACY
        set(value) {
            field = value
            chip.mixer.applyProfile(value)
        }

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

    internal fun rhythmShot(mask: Int) {
        ym2608Rhythm.shot(mask)
    }

    internal fun rhythmDump(mask: Int) {
        ym2608Rhythm.dump(mask)
    }

    internal fun setRhythmMasterLevel(value: Int, relative: Boolean) {
        ym2608Rhythm.setMasterLevel(value, relative)
    }

    internal fun setRhythmVoiceLevel(voice: Int, value: Int, relative: Boolean) {
        ym2608Rhythm.setVoiceLevel(voice, value, relative)
    }

    internal fun setRhythmVoicePan(voice: Int, pan: Int) {
        ym2608Rhythm.setVoicePan(voice, pan)
    }

    internal fun rhythmMasterLevelSnapshot(): Int = ym2608Rhythm.masterLevelSnapshot()
    internal fun rhythmVoiceLevelSnapshot(voice: Int): Int = ym2608Rhythm.voiceLevelSnapshot(voice)
    internal fun rhythmVoicePanSnapshot(voice: Int): Int = ym2608Rhythm.voicePanSnapshot(voice)
    internal fun rhythmGeneratorStateSnapshot(voice: Int): Int = ym2608Rhythm.generatorStateSnapshot(voice)
    internal fun rhythmGeneratorGainSnapshot(voice: Int): Float = ym2608Rhythm.generatorGainSnapshot(voice)

    internal fun ssgDrumTriggerCountSnapshot(): Int = pmdSsgEffects.triggerCountSnapshot()
    internal fun pmdSsgEffectStateSnapshot(kind: ProceduralDrums.DrumKind): Int =
        pmdSsgEffects.generatorStateSnapshot(kind)

    internal fun triggerPmdSsgEffect(kind: Int, velocity: Float) {
        pmdSsgEffects.trigger(kind, velocity)
    }

    fun allNotesOff() {
        ssgShared.reset()
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
        drums.silence()
        ym2608Rhythm.silence()
        pmdSsgEffects.silence()
        i = 0
        while (i < fm3ActiveNoteId.size) {
            fm3ActiveNoteId[i] = -1
            i++
        }
    }

    fun reset() {
        ssgShared.reset()
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
        ym2608Rhythm.reset()
        pmdSsgEffects.reset()
        i = 0
        while (i < fm3ActiveNoteId.size) {
            fm3ActiveNoteId[i] = -1
            i++
        }
        lfo.reset()
        performance.reset()
        mastering.reset()
    }

    fun configureMasterEq(bands: List<SongEqBand>) {
        mastering.configureEq(bands)
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
        mastering.processMono(buffer, frames)
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
        mastering.processMono(buffer, frames)
    }

    fun render(buffer: FloatArray, frames: Int, player: CompiledOpnaPlayer, currentSampleOffset: Long) {
        buffer.fill(0f)
        var offset = 0
        var remaining = frames
        var sampleOffset = currentSampleOffset
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            player.renderMono(this, buffer, offset, chunkFrames, sampleOffset)
            offset += chunkFrames
            remaining -= chunkFrames
            sampleOffset += chunkFrames
        }
        mastering.processMono(buffer, frames)
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
        mastering.processStereo(stereoBuffer, frames)
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
        mastering.processStereo(stereoBuffer, frames)
    }

    fun renderStereo(
        stereoBuffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        currentSampleOffset: Long
    ) {
        stereoBuffer.fill(0f)
        var offset = 0
        var remaining = frames
        var sampleOffset = currentSampleOffset
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            player.renderStereo(this, stereoBuffer, offset, chunkFrames, sampleOffset)
            offset += chunkFrames
            remaining -= chunkFrames
            sampleOffset += chunkFrames
        }
        mastering.processStereo(stereoBuffer, frames)
    }

    private fun renderSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        if (frames <= 0) return
        lfo.prepare(frames)
        var i = 0
        while (i < ssg.size) {
            ssg[i].prepareSoftwareLfo(frames)
            i++
        }
        ssgShared.prepare(frames)
        i = 0
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
        ym2608Rhythm.renderMono(buffer, frames, sampleRate, mixer.rhythmGain, startFrame)
        pmdSsgEffects.renderMono(buffer, frames, sampleRate, mixer.ssgGain, startFrame)
    }

    private fun renderStereoSegment(stereoBuffer: FloatArray, startFrame: Int, frames: Int) {
        if (frames <= 0) return
        lfo.prepare(frames)
        var i = 0
        while (i < ssg.size) {
            ssg[i].prepareSoftwareLfo(frames)
            i++
        }
        ssgShared.prepare(frames)

        i = 0
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
        ym2608Rhythm.renderStereo(stereoBuffer, frames, sampleRate, mixer.rhythmGain, startFrame)
        pmdSsgEffects.renderStereo(stereoBuffer, frames, sampleRate, mixer.ssgGain, startFrame)
    }

    internal fun renderTimelineMonoSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        renderSegment(buffer, startFrame, frames)
    }

    internal fun renderTimelineStereoSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        renderStereoSegment(buffer, startFrame, frames)
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
                leftGain = 1f
                rightGain = 1f
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
        }
    }

    internal fun handleTimelineEvent(timeline: CompiledOpnaTimeline, index: Int) {
        when (timeline.eventType[index]) {
            CompiledOpnaTimeline.TEMPO -> {
                val bpmMilli = timeline.controlValues[index * CompiledOpnaTimeline.CONTROL_STRIDE]
                var channel = 0
                while (channel < ssg.size) {
                    ssg[channel].setSoftwareEnvelopeTempo(bpmMilli, timeline.pmdClocksPerQuarter)
                    ssg[channel].setSoftwareLfoTempo(bpmMilli, timeline.pmdClocksPerQuarter)
                    channel++
                }
                channel = 0
                while (channel < fm.size) {
                    fm[channel].setSoftwareLfoTempo(bpmMilli, timeline.pmdClocksPerQuarter)
                    channel++
                }
                performance.setTempo(bpmMilli, timeline.pmdClocksPerQuarter)
            }
            CompiledOpnaTimeline.SSG_ENVELOPE_DEFINE -> {
                val channel = timeline.channel[index]
                if (channel >= 0 && channel < ssg.size) {
                    val base = index * CompiledOpnaTimeline.CONTROL_STRIDE
                    ssg[channel].configureSoftwareEnvelope(
                        timeline.controlValues[base],
                        timeline.controlValues[base + 1],
                        timeline.controlValues[base + 2],
                        timeline.controlValues[base + 3],
                        timeline.controlValues[base + 4],
                        timeline.controlValues[base + 5],
                        timeline.controlValues[base + 6]
                    )
                }
            }
            CompiledOpnaTimeline.SSG_ENVELOPE_MODE -> {
                val channel = timeline.channel[index]
                if (channel >= 0 && channel < ssg.size) {
                    ssg[channel].setSoftwareEnvelopeClockMode(
                        timeline.controlValues[index * CompiledOpnaTimeline.CONTROL_STRIDE]
                    )
                }
            }
            in CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE..CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH -> {
                handleSoftwareLfoControl(timeline, index)
            }
            in CompiledOpnaTimeline.FM_SLOT_DETUNE_ABSOLUTE..CompiledOpnaTimeline.FM_SLOT_KEY_ON_DELAY -> {
                handleFmSlotControl(timeline, index)
            }
            CompiledOpnaTimeline.FM3_PATCH -> {
                val patch = timeline.instrumentBank.fmPatch(timeline.patchId[index])
                if (patch != null) {
                    val mask = timeline.slotMask[index] and 15
                    if (timeline.logicalPart[index] == CompiledOpnaSong.LOGICAL_PART_NONE && mask == 15) {
                        fm[2].applyPatch(patch)
                    } else {
                        fm[2].applyPatchToSlots(patch, mask)
                    }
                }
            }
            CompiledOpnaTimeline.FM_PART_VOLUME -> {
                performance.setVolume(fm3PartIndex(timeline.logicalPart[index]),
                    timeline.controlValues[index * CompiledOpnaTimeline.CONTROL_STRIDE])
            }
            CompiledOpnaTimeline.FM_PART_SLOT_MASK -> {
                performance.setSlotMask(fm3PartIndex(timeline.logicalPart[index]),
                    timeline.controlValues[index * CompiledOpnaTimeline.CONTROL_STRIDE])
            }
            in CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT..CompiledOpnaTimeline.RHYTHM_VOICE_PAN -> {
                handleRhythmControl(
                    timeline.eventType[index], timeline.channel[index], timeline.slotMask[index],
                    timeline.controlValues[index * CompiledOpnaTimeline.CONTROL_STRIDE]
                )
            }
            CompiledOpnaTimeline.FM_ON -> {
                val channel = timeline.channel[index]
                if (channel >= 0 && channel < fm.size) {
                    val voice = fm[channel]
                    val patch = timeline.instrumentBank.fmPatch(timeline.patchId[index])
                    if (patch != null) voice.applyPatch(patch)
                    voice.setPerformanceControls(
                        timeline.pan[index],
                        timeline.detuneCents[index],
                        timeline.pms[index],
                        timeline.ams[index],
                        timeline.lfoDelayFrames[index],
                        timeline.targetMidi[index],
                        timeline.slideFrames[index]
                    )
                    voice.noteOnScheduled(timeline.midi[index], -1f, -1f, -1f, -1f)
                    voice.noteGain = timeline.velocity[index]
                    fmActiveNoteId[channel] = timeline.noteId[index]
                }
            }
            CompiledOpnaTimeline.FM_OFF -> {
                val channel = timeline.channel[index]
                if (channel >= 0 && channel < fm.size &&
                    fmActiveNoteId[channel] == timeline.noteId[index]
                ) {
                    fm[channel].noteOff()
                    fmActiveNoteId[channel] = FM_VOICE_FREE
                }
            }
            CompiledOpnaTimeline.FM_POLY_ON -> {
                val voiceIndex = availablePolyVoice(timeline.channel[index])
                if (voiceIndex >= 0) {
                    val voice = fm[voiceIndex]
                    val patch = timeline.instrumentBank.fmPatch(timeline.patchId[index])
                    if (patch != null) voice.applyPatch(patch)
                    voice.setPerformanceControls(
                        timeline.pan[index],
                        timeline.detuneCents[index],
                        timeline.pms[index],
                        timeline.ams[index],
                        timeline.lfoDelayFrames[index],
                        -1,
                        0
                    )
                    voice.noteOnScheduled(timeline.midi[index], -1f, -1f, -1f, -1f)
                    voice.noteGain = timeline.velocity[index]
                    fmActiveNoteId[voiceIndex] = timeline.noteId[index]
                }
            }
            CompiledOpnaTimeline.FM_POLY_OFF -> {
                var voiceIndex = 0
                while (voiceIndex < fmActiveNoteId.size) {
                    if (fmActiveNoteId[voiceIndex] == timeline.noteId[index]) {
                        fm[voiceIndex].noteOff()
                        fmActiveNoteId[voiceIndex] = FM_VOICE_RELEASING
                        break
                    }
                    voiceIndex++
                }
            }
            CompiledOpnaTimeline.SSG_ON -> {
                val channel = timeline.channel[index]
                if (channel >= 0 && channel < ssg.size) {
                    val voice = ssg[channel]
                    val patch = timeline.instrumentBank.ssgPatch(timeline.patchId[index])
                    if (patch != null) voice.applyPatch(patch)
                    voice.setPan(timeline.pan[index])
                    val frequency = OpnPitch.applyCents(
                        midiToFreq(timeline.midi[index]),
                        timeline.detuneCents[index]
                    )
                    voice.setPitchRamp(
                        if (timeline.targetMidi[index] >= 0) {
                            OpnPitch.applyCents(
                                midiToFreq(timeline.targetMidi[index]),
                                timeline.detuneCents[index]
                            )
                        } else {
                            0f
                        },
                        timeline.slideFrames[index]
                    )
                    voice.noteOn(frequency)
                    voice.noteGain = timeline.velocity[index]
                    ssgActiveNoteId[channel] = timeline.noteId[index]
                }
            }
            CompiledOpnaTimeline.SSG_OFF -> {
                val channel = timeline.channel[index]
                if (channel >= 0 && channel < ssg.size &&
                    ssgActiveNoteId[channel] == timeline.noteId[index]
                ) {
                    ssg[channel].noteOff()
                    ssgActiveNoteId[channel] = -1
                }
            }
            CompiledOpnaTimeline.DRUM_SHOT -> triggerDrum(
                timeline.midi[index],
                timeline.velocity[index],
                timeline.pan[index]
            )
            CompiledOpnaTimeline.SSG_DRUM_SHOT -> {
                pmdSsgEffects.trigger(timeline.midi[index], timeline.velocity[index])
            }
            CompiledOpnaTimeline.FM3_OPERATOR_ON -> {
                val mask = timeline.slotMask[index] and 15
                val part = fm3PartIndex(timeline.logicalPart[index])
                performance.setSlotMask(part, mask)
                fm[2].setPerformanceControls(
                    timeline.pan[index],
                    timeline.detuneCents[index],
                    timeline.pms[index],
                    timeline.ams[index],
                    timeline.lfoDelayFrames[index],
                    -1,
                    0
                )
                fm[2].noteOnSlots(
                    mask,
                    timeline.midi[index],
                    timeline.targetMidi[index],
                    timeline.slideFrames[index],
                    timeline.detuneCents[index],
                    part
                )
                fm[2].noteGain = 1f
                var operator = 0
                while (operator < AudioLaws.FM_OPERATORS) {
                    if ((mask and (1 shl operator)) != 0) fm3ActiveNoteId[operator] = timeline.noteId[index]
                    operator++
                }
            }
            CompiledOpnaTimeline.FM3_OPERATOR_OFF -> {
                val mask = timeline.slotMask[index] and 15
                var releaseMask = 0
                var operator = 0
                while (operator < AudioLaws.FM_OPERATORS) {
                    if ((mask and (1 shl operator)) != 0 && fm3ActiveNoteId[operator] == timeline.noteId[index]) {
                        releaseMask = releaseMask or (1 shl operator)
                        fm3ActiveNoteId[operator] = -1
                    }
                    operator++
                }
                fm[2].noteOffSlots(releaseMask)
            }
        }
    }

    private fun handleSoftwareLfoControl(timeline: CompiledOpnaTimeline, eventIndex: Int) {
        val channel = timeline.channel[eventIndex]
        val family = timeline.operator[eventIndex]
        val base = eventIndex * CompiledOpnaTimeline.CONTROL_STRIDE
        val lfoIndex = timeline.controlValues[base]
        val fm3Part = fm3PartIndex(timeline.logicalPart[eventIndex])
        if (family == 0 && fm3Part >= 0) {
            when (timeline.eventType[eventIndex]) {
                CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE -> performance.configureLfo(
                    fm3Part, lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3], timeline.controlValues[base + 4]
                )
                CompiledOpnaTimeline.SOFTWARE_LFO_SWITCH -> performance.setLfoSwitch(fm3Part, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_WAVE -> performance.setLfoWaveform(fm3Part, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_CLOCK -> performance.setLfoClockMode(fm3Part, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_TL_MASK -> performance.setLfoTlMask(fm3Part, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH -> performance.setLfoDepthEvolution(
                    fm3Part, lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3]
                )
            }
        } else if (family == 0 && channel in fm.indices) {
            val voice = fm[channel]
            when (timeline.eventType[eventIndex]) {
                CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE -> voice.configureSoftwareLfo(
                    lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3], timeline.controlValues[base + 4]
                )
                CompiledOpnaTimeline.SOFTWARE_LFO_SWITCH -> voice.setSoftwareLfoSwitch(lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_WAVE -> voice.setSoftwareLfoWaveform(lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_CLOCK -> voice.setSoftwareLfoClockMode(lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_TL_MASK -> voice.setSoftwareLfoTlMask(lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH -> voice.setSoftwareLfoDepthEvolution(
                    lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3]
                )
            }
        } else if (family == 1 && channel in ssg.indices) {
            val voice = ssg[channel]
            when (timeline.eventType[eventIndex]) {
                CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE -> voice.configureSoftwareLfo(
                    lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3], timeline.controlValues[base + 4]
                )
                CompiledOpnaTimeline.SOFTWARE_LFO_SWITCH -> voice.setSoftwareLfoSwitch(lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_WAVE -> voice.setSoftwareLfoWaveform(lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_CLOCK -> voice.setSoftwareLfoClockMode(lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH -> voice.setSoftwareLfoDepthEvolution(
                    lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3]
                )
            }
        }
    }

    private fun handleFmSlotControl(timeline: CompiledOpnaTimeline, eventIndex: Int) {
        val channel = timeline.channel[eventIndex]
        if (channel !in fm.indices) return
        val voice = fm[channel]
        val mask = timeline.slotMask[eventIndex] and 15
        val value = timeline.controlValues[eventIndex * CompiledOpnaTimeline.CONTROL_STRIDE]
        when (timeline.eventType[eventIndex]) {
            CompiledOpnaTimeline.FM_SLOT_DETUNE_ABSOLUTE -> voice.setSlotDetune(mask, value, relative = false)
            CompiledOpnaTimeline.FM_SLOT_DETUNE_RELATIVE -> voice.setSlotDetune(mask, value, relative = true)
            CompiledOpnaTimeline.FM_TL_ABSOLUTE -> voice.setOperatorTl(mask, value, relative = false)
            CompiledOpnaTimeline.FM_TL_RELATIVE -> voice.setOperatorTl(mask, value, relative = true)
            CompiledOpnaTimeline.FM_FEEDBACK_ABSOLUTE -> voice.setFeedback(value, relative = false)
            CompiledOpnaTimeline.FM_FEEDBACK_RELATIVE -> voice.setFeedback(value, relative = true)
            CompiledOpnaTimeline.FM_SLOT_KEY_ON_DELAY -> voice.setSlotKeyOnDelay(mask, value)
        }
    }

    private fun fm3PartIndex(logicalPart: Int): Int {
        val part = logicalPart - CompiledOpnaSong.FM3_PART_BASE
        return if (part in 0 until CompiledOpnaSong.FM3_PART_COUNT) part else -1
    }

    internal fun fm3PartVolumeSnapshot(part: Int): Int = performance.volumeSnapshot(part)
    internal fun fm3PartLfoTlMaskSnapshot(part: Int, lfo: Int): Int = performance.lfoTlMaskSnapshot(part, lfo)

    private fun handleRhythmControl(type: Int, voice: Int, mask: Int, value: Int) {
        when (type) {
            CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT -> rhythmShot(mask)
            CompiledOpnaTimeline.RHYTHM_CONTROL_DUMP -> rhythmDump(mask)
            CompiledOpnaTimeline.RHYTHM_MASTER_ABSOLUTE -> setRhythmMasterLevel(value, false)
            CompiledOpnaTimeline.RHYTHM_MASTER_RELATIVE -> setRhythmMasterLevel(value, true)
            CompiledOpnaTimeline.RHYTHM_VOICE_LEVEL_ABSOLUTE -> setRhythmVoiceLevel(voice, value, false)
            CompiledOpnaTimeline.RHYTHM_VOICE_LEVEL_RELATIVE -> setRhythmVoiceLevel(voice, value, true)
            CompiledOpnaTimeline.RHYTHM_VOICE_PAN -> setRhythmVoicePan(voice, value)
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

    companion object {
        private const val FM_VOICE_FREE = -1
        private const val FM_VOICE_RELEASING = -2
        const val MAX_FRAMES_PER_CHUNK = 1024
        const val SOFT_CLIP_KNEE = SongMastering.SOFT_CLIP_KNEE
    }
}
