package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import com.example.timeboxvibe.engine.SongEqBand

class OpnaLikeSynthesizer(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {
    private val chip = OpnaChipState(sampleRate)
    internal val mixer = OpnaMixer()
    private val mastering = SongMastering(sampleRate)
    private val ssgShared: SsgSharedState get() = chip.ssgShared
    private val tempMonoBuffer = FloatArray(MAX_FRAMES_PER_CHUNK)
    private val fmMonoBus = FloatArray(MAX_FRAMES_PER_CHUNK)
    private val ssgMonoBus = FloatArray(MAX_FRAMES_PER_CHUNK)
    private val rhythmMonoBus = FloatArray(MAX_FRAMES_PER_CHUNK)
    private val fmStereoBus = FloatArray(MAX_FRAMES_PER_CHUNK * STEREO_CHANNELS)
    private val ssgStereoBus = FloatArray(MAX_FRAMES_PER_CHUNK * STEREO_CHANNELS)
    private val rhythmStereoBus = FloatArray(MAX_FRAMES_PER_CHUNK * STEREO_CHANNELS)
    val ssg: Array<SsgVoice> get() = chip.ssg
    val fm: Array<Fm4OpVoice> get() = chip.fm
    val drums: ProceduralDrums get() = chip.legacyDrums
    private val ym2608Rhythm: Ym2608RhythmUnit get() = chip.ym2608Rhythm
    private val pmdSsgEffects: PmdSsgEffectUnit get() = chip.pmdSsgEffects
    val lfo: Lfo get() = chip.lfo

    val preClampPeak: Float get() = mastering.preClampPeak
    val preClampKneeCrossings: Int get() = mastering.preClampKneeCrossings

    private val directFmPartFrames = Array(AudioLaws.FM_CHANNELS) { PmdModulationFrame() }
    private val directFmDriverFrameByVoice = arrayOfNulls<PmdModulationFrame>(AudioLaws.FM_RENDER_VOICES)
    private val directFmActiveNoteId = IntArray(AudioLaws.FM_RENDER_VOICES) { FM_VOICE_FREE }
    private val directSsgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { -1 }
    private val ssgHardwareStateConfigured = BooleanArray(AudioLaws.SSG_CHANNELS)
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
            mixer.applyProfile(value)
        }

    init {
        OpnLogTables.warmUp()
        DrumSinLut.warmUp()
        OpnEnvelopeCompatibility.warmUp()
        resetDirectDriverState()
    }

    fun noteOnSsg(channel: Int, midi: Int) {
        if (channel in ssg.indices) {
            ensureDefaultSsgHardwareState(channel)
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
            directFmDriverFrameByVoice[channel] = directFmPartFrames[channel]
            fm[channel].noteOn(midi, attack, decay, sustain, release)
        }
    }

    fun noteOnFm(channel: Int, midi: Int, patch: FmPatch) {
        if (channel in 0 until AudioLaws.FM_CHANNELS) {
            fm[channel].applyPatch(patch)
            directFmDriverFrameByVoice[channel] = directFmPartFrames[channel]
            setDirectFmHardwareLfo(channel, patch.pms, patch.ams)
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
            directSsgActiveNoteId[i] = -1
            ssgHardwareStateConfigured[i] = false
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].clearActiveNote()
            directFmActiveNoteId[i] = FM_VOICE_FREE
            i++
        }
        drums.silence()
        ym2608Rhythm.silence()
        pmdSsgEffects.silence()
        resetDirectDriverState()
    }

    fun reset() {
        resetChipDomain()
        resetOutputDomain()
        resetMasteringDomain()
    }

    private fun resetChipDomain() {
        ssgShared.reset()
        var i = 0
        while (i < ssg.size) {
            ssg[i].reset()
            directSsgActiveNoteId[i] = -1
            ssgHardwareStateConfigured[i] = false
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].reset()
            directFmActiveNoteId[i] = FM_VOICE_FREE
            i++
        }
        drums.reset()
        ym2608Rhythm.reset()
        pmdSsgEffects.reset()
        lfo.reset()
        resetDirectDriverState()
    }

    private fun resetOutputDomain() {
        mixer.resetTo(outputProfile)
    }

    private fun resetMasteringDomain() {
        mastering.reset()
    }

    /** Rebuilds timeline-owned state without erasing caller-owned mastering history. */
    internal fun resetTimelineDomains() {
        resetChipDomain()
        resetOutputDomain()
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
            renderProfiledPreMasterMonoSegment(buffer, offset, chunkFrames)
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

    fun renderStereo(stereoBuffer: FloatArray, frames: Int) {
        stereoBuffer.fill(0f)
        var offset = 0
        var remaining = frames
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            renderProfiledPreMasterStereoSegment(stereoBuffer, offset, chunkFrames)
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

    internal fun processTimelineMasteringMono(buffer: FloatArray, frames: Int) {
        mastering.processMono(buffer, frames)
    }

    internal fun processTimelineMasteringStereo(buffer: FloatArray, frames: Int) {
        mastering.processStereo(buffer, frames)
    }

    private fun renderRawCoreMonoSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        renderChipAndDriverCoreMonoToBuses(frames)
        mixer.mixRawCoreMono(fmMonoBus, ssgMonoBus, rhythmMonoBus, buffer, startFrame, frames)
    }

    private fun renderProfiledPreMasterMonoSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        renderChipAndDriverCoreMonoToBuses(frames)
        mixer.mixProfiledPreMasterMono(fmMonoBus, ssgMonoBus, rhythmMonoBus, buffer, startFrame, frames)
    }

    private fun renderChipAndDriverCoreMonoToBuses(
        frames: Int,
        fmDriverFrames: Array<PmdModulationFrame?>? = directFmDriverFrameByVoice,
        ssgDriverFrames: Array<PmdSsgFrame?>? = null,
        fm3DriverFrames: Array<PmdModulationFrame?>? = null,
        ssgStopAfterFrame: IntArray? = null
    ) {
        if (frames <= 0) return
        fmMonoBus.fill(0f, 0, frames)
        ssgMonoBus.fill(0f, 0, frames)
        rhythmMonoBus.fill(0f, 0, frames)
        lfo.prepare(frames)
        var i = 0
        while (i < ssg.size) {
            ssg[i].prepareDriverFrame(ssgDriverFrames?.get(i), frames)
            i++
        }
        ssgShared.prepare(frames)
        i = 0
        while (i < ssg.size) {
            ssg[i].renderDriven(
                ssgMonoBus, frames, sampleRate, 1f, 0,
                sharedPrepared = true,
                driverFrame = ssgDriverFrames?.get(i),
                stopAfterFrame = ssgStopAfterFrame?.get(i) ?: STOP_FRAME_NONE
            )
            i++
        }
        i = 0
        while (i < fm.size) {
            val destination = if (fm[i].getPan() == 3) {
                tempMonoBuffer.fill(0f, 0, frames)
                tempMonoBuffer
            } else fmMonoBus
            fm[i].renderDriven(
                destination, frames, sampleRate, 1f, 0, lfo,
                fmDriverFrames?.get(i),
                if (i == FM3_PHYSICAL_VOICE) fm3DriverFrames else null
            )
            i++
        }
        drums.render(rhythmMonoBus, frames, sampleRate, 1f, 0)
        ym2608Rhythm.renderMono(rhythmMonoBus, frames, sampleRate, 1f, 0)
        pmdSsgEffects.renderMono(ssgMonoBus, frames, sampleRate, 1f, 0)
    }

    private fun renderRawCoreStereoSegment(stereoBuffer: FloatArray, startFrame: Int, frames: Int) {
        renderChipAndDriverCoreStereoToBuses(frames)
        mixer.mixRawCoreStereo(fmStereoBus, ssgStereoBus, rhythmStereoBus, stereoBuffer, startFrame, frames)
    }

    private fun renderProfiledPreMasterStereoSegment(stereoBuffer: FloatArray, startFrame: Int, frames: Int) {
        renderChipAndDriverCoreStereoToBuses(frames)
        mixer.mixProfiledPreMasterStereo(
            fmStereoBus, ssgStereoBus, rhythmStereoBus, stereoBuffer, startFrame, frames
        )
    }

    private fun renderChipAndDriverCoreStereoToBuses(
        frames: Int,
        fmDriverFrames: Array<PmdModulationFrame?>? = directFmDriverFrameByVoice,
        ssgDriverFrames: Array<PmdSsgFrame?>? = null,
        fm3DriverFrames: Array<PmdModulationFrame?>? = null,
        ssgStopAfterFrame: IntArray? = null
    ) {
        if (frames <= 0) return
        val stereoSamples = frames * STEREO_CHANNELS
        fmStereoBus.fill(0f, 0, stereoSamples)
        ssgStereoBus.fill(0f, 0, stereoSamples)
        rhythmStereoBus.fill(0f, 0, stereoSamples)
        lfo.prepare(frames)
        var i = 0
        while (i < ssg.size) {
            ssg[i].prepareDriverFrame(ssgDriverFrames?.get(i), frames)
            i++
        }
        ssgShared.prepare(frames)

        i = 0
        while (i < ssg.size) {
            tempMonoBuffer.fill(0f, 0, frames)
            ssg[i].renderDriven(
                tempMonoBuffer, frames, sampleRate, 1f,
                startFrame = 0,
                sharedPrepared = true,
                driverFrame = ssgDriverFrames?.get(i),
                stopAfterFrame = ssgStopAfterFrame?.get(i) ?: STOP_FRAME_NONE
            )
            panMonoToStereo(tempMonoBuffer, ssgStereoBus, frames, 0, ssg[i].getPan())
            i++
        }

        i = 0
        while (i < fm.size) {
            tempMonoBuffer.fill(0f, 0, frames)
            fm[i].renderDriven(
                tempMonoBuffer, frames, sampleRate, 1f, startFrame = 0, lfo = lfo,
                driverFrame = fmDriverFrames?.get(i),
                fm3DriverFrames = if (i == FM3_PHYSICAL_VOICE) fm3DriverFrames else null
            )
            val pan = fm[i].getPan()
            panMonoToStereo(tempMonoBuffer, fmStereoBus, frames, 0, pan)
            i++
        }

        drums.renderStereo(rhythmStereoBus, frames, sampleRate, 1f, 0)
        ym2608Rhythm.renderStereo(rhythmStereoBus, frames, sampleRate, 1f, 0)
        pmdSsgEffects.renderStereo(ssgStereoBus, frames, sampleRate, 1f, 0)
    }

    internal fun renderTimelineSegment(
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        stereo: Boolean,
        rawCore: Boolean,
        fmDriverFrames: Array<PmdModulationFrame?>,
        ssgDriverFrames: Array<PmdSsgFrame?>,
        fm3DriverFrames: Array<PmdModulationFrame?>,
        ssgStopAfterFrame: IntArray
    ) {
        if (stereo) {
            renderChipAndDriverCoreStereoToBuses(
                frames, fmDriverFrames, ssgDriverFrames, fm3DriverFrames, ssgStopAfterFrame
            )
            if (rawCore) {
                mixer.mixRawCoreStereo(fmStereoBus, ssgStereoBus, rhythmStereoBus, buffer, startFrame, frames)
            } else {
                mixer.mixProfiledPreMasterStereo(
                    fmStereoBus, ssgStereoBus, rhythmStereoBus, buffer, startFrame, frames
                )
            }
        } else {
            renderChipAndDriverCoreMonoToBuses(
                frames, fmDriverFrames, ssgDriverFrames, fm3DriverFrames, ssgStopAfterFrame
            )
            if (rawCore) {
                mixer.mixRawCoreMono(fmMonoBus, ssgMonoBus, rhythmMonoBus, buffer, startFrame, frames)
            } else {
                mixer.mixProfiledPreMasterMono(fmMonoBus, ssgMonoBus, rhythmMonoBus, buffer, startFrame, frames)
            }
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
            3 -> {
                leftGain = 0f
                rightGain = 0f
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
                renderProfiledPreMasterMonoSegment(buffer, startFrameOffset + renderPos, frames - renderPos)
                break
            }

            val eventOffset = (nextEvent.sampleTime - currentSampleOffset).toInt()
            if (eventOffset > renderPos) {
                renderProfiledPreMasterMonoSegment(buffer, startFrameOffset + renderPos, eventOffset - renderPos)
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
                renderProfiledPreMasterStereoSegment(stereoBuffer, startFrameOffset + renderPos, frames - renderPos)
                break
            }

            val eventOffset = (nextEvent.sampleTime - currentSampleOffset).toInt()
            if (eventOffset > renderPos) {
                renderProfiledPreMasterStereoSegment(stereoBuffer, startFrameOffset + renderPos, eventOffset - renderPos)
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
                    if (ch < AudioLaws.FM_CHANNELS) {
                        directFmDriverFrameByVoice[ch] = directFmPartFrames[ch]
                        setDirectFmHardwareLfo(ch, event.pms, event.ams)
                    }
                    voice.setNoteControls(
                        event.pan,
                        event.detuneCents,
                        event.lfoDelayFrames,
                        event.targetMidi,
                        event.slideFrames
                    )
                    voice.noteOnScheduled(event.midi, event.attack, event.decay, event.sustain, event.release)
                    voice.noteGain = event.velocity
                    directFmActiveNoteId[ch] = event.noteId
                }
            }
            SequencerEvent.FM_OFF -> {
                val ch = event.channel
                if (ch >= 0 && ch < fm.size && directFmActiveNoteId[ch] == event.noteId) {
                    fm[ch].noteOff()
                    directFmActiveNoteId[ch] = FM_VOICE_FREE
                }
            }
            SequencerEvent.FM_POLY_ON -> {
                val voiceIndex = availablePolyVoice(event.channel)
                if (voiceIndex >= 0) {
                    val voice = fm[voiceIndex]
                    val selectedPatch = event.patch
                    if (selectedPatch != null) voice.applyPatch(selectedPatch)
                    directFmDriverFrameByVoice[voiceIndex] =
                        if (event.channel in directFmPartFrames.indices) directFmPartFrames[event.channel] else null
                    if (event.channel in directFmPartFrames.indices) {
                        setDirectFmHardwareLfo(event.channel, event.pms, event.ams)
                    } else {
                        voice.setHardwareLfoPms(0)
                        voice.setHardwareLfoAms(0)
                    }
                    voice.setNoteControls(
                        event.pan,
                        event.detuneCents,
                        event.lfoDelayFrames,
                        -1,
                        0
                    )
                    voice.noteOnScheduled(event.midi, -1f, -1f, -1f, -1f)
                    voice.noteGain = event.velocity
                    directFmActiveNoteId[voiceIndex] = event.noteId
                }
            }
            SequencerEvent.FM_POLY_OFF -> {
                var voiceIndex = 0
                while (voiceIndex < directFmActiveNoteId.size) {
                    if (directFmActiveNoteId[voiceIndex] == event.noteId) {
                        fm[voiceIndex].noteOff()
                        directFmActiveNoteId[voiceIndex] = FM_VOICE_RELEASING
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
                    if (selectedPatch != null) {
                        writeSsgPatchState(ch, selectedPatch)
                        voice.applyPatch(selectedPatch)
                    } else {
                        ensureDefaultSsgHardwareState(ch)
                    }
                    voice.setPan(event.pan)
                    val frequency = OpnPitch.applyCents(midiToFreq(event.midi), event.detuneCents)
                    voice.setPitchRamp(
                        if (event.targetMidi >= 0) OpnPitch.applyCents(midiToFreq(event.targetMidi), event.detuneCents) else 0f,
                        event.slideFrames
                    )
                    voice.noteOn(frequency)
                    voice.noteGain = event.velocity
                    directSsgActiveNoteId[ch] = event.noteId
                }
            }
            SequencerEvent.SSG_OFF -> {
                val ch = event.channel
                if (ch >= 0 && ch < ssg.size && directSsgActiveNoteId[ch] == event.noteId) {
                    ssg[ch].noteOff()
                    directSsgActiveNoteId[ch] = -1
                }
            }
            SequencerEvent.DRUM -> triggerDrum(event.midi, event.velocity, event.pan)
        }
    }

    private fun ensureDefaultSsgHardwareState(channel: Int) {
        if (ssgHardwareStateConfigured[channel]) return
        ssgShared.writeMixerChannel(channel, toneEnabled = true, noiseEnabled = false)
        ssgHardwareStateConfigured[channel] = true
    }

    internal fun setTimelineSsgToneEnabled(channel: Int, enabled: Boolean) {
        if (channel !in ssg.indices) return
        ssgShared.writeToneEnabled(channel, enabled)
        ssgHardwareStateConfigured[channel] = true
    }

    internal fun setTimelineSsgNoiseEnabled(channel: Int, enabled: Boolean) {
        if (channel !in ssg.indices) return
        ssgShared.writeNoiseEnabled(channel, enabled)
        ssgHardwareStateConfigured[channel] = true
    }

    internal fun setTimelineSsgNoisePeriod(channel: Int, period: Int) {
        if (channel in ssg.indices) ssgShared.writeNoisePeriod(period)
    }

    internal fun setTimelineSsgEnvelopePeriod(channel: Int, period: Int) {
        if (channel in ssg.indices) ssgShared.writeEnvelopePeriod(period)
    }

    internal fun setTimelineSsgEnvelopeShape(channel: Int, shape: Int) {
        if (channel in ssg.indices) ssgShared.writeEnvelopeShape(shape)
    }

    internal fun setFmHardwareLfoPms(channel: Int, value: Int) {
        if (channel in fm.indices) fm[channel].setHardwareLfoPms(value)
    }

    internal fun setFmHardwareLfoAms(channel: Int, value: Int) {
        if (channel in fm.indices) fm[channel].setHardwareLfoAms(value)
    }

    private fun writeSsgPatchState(channel: Int, patch: SsgPatch) {
        ssgShared.writeMixerChannel(channel, patch.toneEnabled, patch.noiseEnabled)
        if (patch.noiseEnabled) ssgShared.writeNoisePeriod(patch.noisePeriod)
        if (patch.envelopeEnabled) {
            ssgShared.writeEnvelopePeriod(patch.envelopePeriod)
            ssgShared.writeEnvelopeShape(patch.envelopeShape)
        }
        ssgHardwareStateConfigured[channel] = true
    }

    internal fun ssgNoisePeriodSnapshot(): Int = ssgShared.noisePeriodSnapshot()
    internal fun ssgEnvelopePeriodSnapshot(): Int = ssgShared.envelopePeriodSnapshot()
    internal fun ssgEnvelopeShapeSnapshot(): Int = ssgShared.envelopeShapeSnapshot()
    internal fun ssgEnvelopeRestartCountSnapshot(): Int = ssgShared.envelopeRestartCountSnapshot()

    private fun setDirectFmHardwareLfo(part: Int, pms: Int, ams: Int) {
        if (part !in directFmPartFrames.indices) return
        val frame = directFmPartFrames[part]
        var voice = 0
        while (voice < directFmDriverFrameByVoice.size) {
            if (directFmDriverFrameByVoice[voice] === frame) {
                fm[voice].setHardwareLfoPms(pms)
                fm[voice].setHardwareLfoAms(ams)
            }
            voice++
        }
    }

    private fun resetDirectDriverState() {
        var part = 0
        while (part < directFmPartFrames.size) {
            directFmPartFrames[part].clear()
            part++
        }
        var voice = 0
        while (voice < directFmDriverFrameByVoice.size) {
            directFmActiveNoteId[voice] = FM_VOICE_FREE
            fm[voice].setHardwareLfoPms(0)
            fm[voice].setHardwareLfoAms(0)
            directFmDriverFrameByVoice[voice] =
                if (voice < directFmPartFrames.size) directFmPartFrames[voice] else null
            voice++
        }
        var channel = 0
        while (channel < directSsgActiveNoteId.size) {
            directSsgActiveNoteId[channel] = -1
            channel++
        }
    }

    private fun availablePolyVoice(preferredChannel: Int): Int {
        reclaimFinishedPolyVoices()
        if (preferredChannel in 0 until AudioLaws.FM_CHANNELS && directFmActiveNoteId[preferredChannel] == FM_VOICE_FREE) {
            return preferredChannel
        }
        var voiceIndex = AudioLaws.FM_CHANNELS
        while (voiceIndex < directFmActiveNoteId.size) {
            if (directFmActiveNoteId[voiceIndex] == FM_VOICE_FREE) return voiceIndex
            voiceIndex++
        }
        voiceIndex = 0
        while (voiceIndex < AudioLaws.FM_CHANNELS) {
            if (directFmActiveNoteId[voiceIndex] == FM_VOICE_FREE) return voiceIndex
            voiceIndex++
        }
        voiceIndex = AudioLaws.FM_CHANNELS
        while (voiceIndex < directFmActiveNoteId.size) {
            if (directFmActiveNoteId[voiceIndex] == FM_VOICE_RELEASING) return voiceIndex
            voiceIndex++
        }
        voiceIndex = 0
        while (voiceIndex < AudioLaws.FM_CHANNELS) {
            if (directFmActiveNoteId[voiceIndex] == FM_VOICE_RELEASING) return voiceIndex
            voiceIndex++
        }
        return -1
    }

    private fun reclaimFinishedPolyVoices() {
        var voiceIndex = 0
        while (voiceIndex < directFmActiveNoteId.size) {
            if (directFmActiveNoteId[voiceIndex] == FM_VOICE_RELEASING && fm[voiceIndex].releaseFinished()) {
                directFmActiveNoteId[voiceIndex] = FM_VOICE_FREE
                directFmDriverFrameByVoice[voiceIndex] =
                    if (voiceIndex < directFmPartFrames.size) directFmPartFrames[voiceIndex] else null
            }
            voiceIndex++
        }
    }

    internal fun activeFmVoiceCount(): Int {
        var active = 0
        var voiceIndex = 0
        while (voiceIndex < directFmActiveNoteId.size) {
            if (directFmActiveNoteId[voiceIndex] >= 0) active++
            voiceIndex++
        }
        return active
    }

    internal fun occupiedFmVoiceCount(): Int {
        var occupied = 0
        var voiceIndex = 0
        while (voiceIndex < directFmActiveNoteId.size) {
            if (directFmActiveNoteId[voiceIndex] != FM_VOICE_FREE) occupied++
            voiceIndex++
        }
        return occupied
    }

    companion object {
        private const val FM_VOICE_FREE = -1
        private const val FM_VOICE_RELEASING = -2
        private const val FM3_PHYSICAL_VOICE = 2
        private const val STOP_FRAME_NONE = -1
        private const val STEREO_CHANNELS = 2
        const val MAX_FRAMES_PER_CHUNK = 1024
        const val SOFT_CLIP_KNEE = SongMastering.SOFT_CLIP_KNEE
    }
}
