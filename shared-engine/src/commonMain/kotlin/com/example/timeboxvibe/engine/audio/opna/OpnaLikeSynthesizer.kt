package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import com.example.timeboxvibe.engine.SongEqBand

class OpnaLikeSynthesizer(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {
    private val chip = OpnaChipState(sampleRate)
    private val performance = PmdPerformanceState(sampleRate)
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

    private val fmActiveNoteId = IntArray(AudioLaws.FM_RENDER_VOICES) { FM_VOICE_FREE }
    private val fmLogicalPartByVoice = IntArray(AudioLaws.FM_RENDER_VOICES) { voice ->
        if (voice < AudioLaws.FM_CHANNELS) voice else LOGICAL_PART_NONE
    }
    private val ssgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { -1 }
    private val ssgHardwareStateConfigured = BooleanArray(AudioLaws.SSG_CHANNELS)
    private val fm3ActiveNoteId = IntArray(AudioLaws.FM_OPERATORS) { -1 }
    private val fm3DriverFrameByOperator = arrayOfNulls<PmdModulationFrame>(AudioLaws.FM_OPERATORS)
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
        resetFm3DriverMappings()
    }

    fun noteOnSsg(channel: Int, midi: Int) {
        if (channel in ssg.indices) {
            ensureDefaultSsgHardwareState(channel)
            performance.setSsgBaseLevel(channel, ssg[channel].fixedLevelSnapshot())
            performance.noteOnSsg(channel)
            ssg[channel].noteOn(midiToFreq(midi))
        }
    }

    fun noteOffSsg(channel: Int) {
        if (channel in ssg.indices) {
            ssg[channel].noteOff(performance.noteOffSsg(channel))
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
            fmLogicalPartByVoice[channel] = channel
            performance.noteOnFm(channel)
            fm[channel].noteOn(midi, attack, decay, sustain, release)
        }
    }

    fun noteOnFm(channel: Int, midi: Int, patch: FmPatch) {
        if (channel in 0 until AudioLaws.FM_CHANNELS) {
            fm[channel].applyPatch(patch)
            // The direct preview API has no compiled timeline; seed its logical owner explicitly.
            performance.setHardwareLfoPms(channel, patch.pms)
            performance.setHardwareLfoAms(channel, patch.ams)
            fmLogicalPartByVoice[channel] = channel
            performance.noteOnFm(channel)
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
        performance.reset()
        ssgShared.reset()
        var i = 0
        while (i < ssg.size) {
            ssg[i].reset()
            ssgActiveNoteId[i] = -1
            ssgHardwareStateConfigured[i] = false
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].clearActiveNote()
            fmActiveNoteId[i] = -1
            fmLogicalPartByVoice[i] = if (i < AudioLaws.FM_CHANNELS) i else LOGICAL_PART_NONE
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
        resetFm3DriverMappings()
    }

    fun reset() {
        resetChipDomain()
        resetPerformanceDomain()
        resetOutputDomain()
        resetMasteringDomain()
    }

    private fun resetChipDomain() {
        ssgShared.reset()
        var i = 0
        while (i < ssg.size) {
            ssg[i].reset()
            ssgActiveNoteId[i] = -1
            ssgHardwareStateConfigured[i] = false
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].reset()
            fmActiveNoteId[i] = -1
            fmLogicalPartByVoice[i] = if (i < AudioLaws.FM_CHANNELS) i else LOGICAL_PART_NONE
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
        resetFm3DriverMappings()
    }

    private fun resetPerformanceDomain() {
        performance.reset()
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
        resetPerformanceDomain()
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

    fun render(buffer: FloatArray, frames: Int, player: CompiledOpnaPlayer, currentSampleOffset: Long) {
        renderProfiledPreMaster(buffer, frames, player, currentSampleOffset)
        mastering.processMono(buffer, frames)
    }

    /** Timeline playback at unity chip-bus gain, before output profile and song mastering. */
    fun renderRawCore(
        buffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        currentSampleOffset: Long
    ) {
        renderTimelineWholeBuffer(
            buffer, frames, player, currentSampleOffset,
            CompiledOpnaPlayer.CHANNELS_MONO,
            CompiledOpnaPlayer.STAGE_RAW_CORE
        )
    }

    /** Timeline playback after the named output profile, but before [SongMastering]. */
    fun renderProfiledPreMaster(
        buffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        currentSampleOffset: Long
    ) {
        renderTimelineWholeBuffer(
            buffer, frames, player, currentSampleOffset,
            CompiledOpnaPlayer.CHANNELS_MONO,
            CompiledOpnaPlayer.STAGE_PROFILED_PRE_MASTER
        )
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

    fun renderStereo(
        stereoBuffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        currentSampleOffset: Long
    ) {
        renderProfiledPreMasterStereo(stereoBuffer, frames, player, currentSampleOffset)
        mastering.processStereo(stereoBuffer, frames)
    }

    /** Stereo timeline playback at unity chip-bus gain, before profile and mastering. */
    fun renderRawCoreStereo(
        stereoBuffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        currentSampleOffset: Long
    ) {
        renderTimelineWholeBuffer(
            stereoBuffer, frames, player, currentSampleOffset,
            CompiledOpnaPlayer.CHANNELS_STEREO,
            CompiledOpnaPlayer.STAGE_RAW_CORE
        )
    }

    /** Stereo timeline playback after the output profile, but before [SongMastering]. */
    fun renderProfiledPreMasterStereo(
        stereoBuffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        currentSampleOffset: Long
    ) {
        renderTimelineWholeBuffer(
            stereoBuffer, frames, player, currentSampleOffset,
            CompiledOpnaPlayer.CHANNELS_STEREO,
            CompiledOpnaPlayer.STAGE_PROFILED_PRE_MASTER
        )
    }

    private fun renderTimelineWholeBuffer(
        buffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        currentSampleOffset: Long,
        channelCount: Int,
        outputStage: Int
    ) {
        buffer.fill(0f)
        var offset = 0
        var remaining = frames
        var sampleOffset = currentSampleOffset
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            player.render(
                this, buffer, offset, chunkFrames, sampleOffset,
                channelCount, outputStage
            )
            offset += chunkFrames
            remaining -= chunkFrames
            sampleOffset += chunkFrames
        }
    }

    private fun renderRawCoreMonoSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        renderChipAndDriverCoreMonoToBuses(frames)
        mixer.mixRawCoreMono(fmMonoBus, ssgMonoBus, rhythmMonoBus, buffer, startFrame, frames)
    }

    private fun renderProfiledPreMasterMonoSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        renderChipAndDriverCoreMonoToBuses(frames)
        mixer.mixProfiledPreMasterMono(fmMonoBus, ssgMonoBus, rhythmMonoBus, buffer, startFrame, frames)
    }

    private fun renderChipAndDriverCoreMonoToBuses(frames: Int) {
        if (frames <= 0) return
        fmMonoBus.fill(0f, 0, frames)
        ssgMonoBus.fill(0f, 0, frames)
        rhythmMonoBus.fill(0f, 0, frames)
        preparePerformanceFrames(frames)
        lfo.prepare(frames)
        var i = 0
        while (i < ssg.size) {
            ssg[i].prepareDriverFrame(performance.ssgFrame(i), frames)
            i++
        }
        ssgShared.prepare(frames)
        i = 0
        while (i < ssg.size) {
            ssg[i].renderDriven(
                ssgMonoBus, frames, sampleRate, 1f, 0,
                sharedPrepared = true, driverFrame = performance.ssgFrame(i)
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
                performance.fmFrame(fmLogicalPartByVoice[i]),
                if (i == FM3_PHYSICAL_VOICE) fm3DriverFrameByOperator else null
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

    private fun renderChipAndDriverCoreStereoToBuses(frames: Int) {
        if (frames <= 0) return
        val stereoSamples = frames * STEREO_CHANNELS
        fmStereoBus.fill(0f, 0, stereoSamples)
        ssgStereoBus.fill(0f, 0, stereoSamples)
        rhythmStereoBus.fill(0f, 0, stereoSamples)
        preparePerformanceFrames(frames)
        lfo.prepare(frames)
        var i = 0
        while (i < ssg.size) {
            ssg[i].prepareDriverFrame(performance.ssgFrame(i), frames)
            i++
        }
        ssgShared.prepare(frames)

        i = 0
        while (i < ssg.size) {
            tempMonoBuffer.fill(0f, 0, frames)
            ssg[i].renderDriven(
                tempMonoBuffer, frames, sampleRate, 1f,
                startFrame = 0,
                sharedPrepared = true, driverFrame = performance.ssgFrame(i)
            )
            panMonoToStereo(tempMonoBuffer, ssgStereoBus, frames, 0, ssg[i].getPan())
            i++
        }

        i = 0
        while (i < fm.size) {
            tempMonoBuffer.fill(0f, 0, frames)
            fm[i].renderDriven(
                tempMonoBuffer, frames, sampleRate, 1f, startFrame = 0, lfo = lfo,
                driverFrame = performance.fmFrame(fmLogicalPartByVoice[i]),
                fm3DriverFrames = if (i == FM3_PHYSICAL_VOICE) fm3DriverFrameByOperator else null
            )
            val pan = fm[i].getPan()
            panMonoToStereo(tempMonoBuffer, fmStereoBus, frames, 0, pan)
            i++
        }

        drums.renderStereo(rhythmStereoBus, frames, sampleRate, 1f, 0)
        ym2608Rhythm.renderStereo(rhythmStereoBus, frames, sampleRate, 1f, 0)
        pmdSsgEffects.renderStereo(ssgStereoBus, frames, sampleRate, 1f, 0)
    }

    private fun preparePerformanceFrames(frames: Int) {
        var channel = 0
        while (channel < ssg.size) {
            performance.setSsgBaseLevel(channel, ssg[channel].fixedLevelSnapshot())
            channel++
        }
        performance.prepare(frames)
    }

    private fun resetFm3DriverMappings() {
        var operator = 0
        while (operator < fm3DriverFrameByOperator.size) {
            fm3DriverFrameByOperator[operator] = performance.fm3Frame(operator)
            operator++
        }
    }

    private fun bindFm3DriverFrames(part: Int, mask: Int) {
        val frame = performance.fm3Frame(part) ?: return
        var operator = 0
        while (operator < fm3DriverFrameByOperator.size) {
            if ((mask and (1 shl operator)) != 0) {
                fm3DriverFrameByOperator[operator] = frame
            }
            operator++
        }
    }

    internal fun renderTimelineSegment(
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        channelCount: Int,
        outputStage: Int
    ) {
        if (channelCount == CompiledOpnaPlayer.CHANNELS_STEREO) {
            if (outputStage == CompiledOpnaPlayer.STAGE_RAW_CORE) {
                renderRawCoreStereoSegment(buffer, startFrame, frames)
            } else {
                renderProfiledPreMasterStereoSegment(buffer, startFrame, frames)
            }
        } else {
            if (outputStage == CompiledOpnaPlayer.STAGE_RAW_CORE) {
                renderRawCoreMonoSegment(buffer, startFrame, frames)
            } else {
                renderProfiledPreMasterMonoSegment(buffer, startFrame, frames)
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
                    performance.setHardwareLfoPms(ch, event.pms)
                    performance.setHardwareLfoAms(ch, event.ams)
                    voice.setNoteControls(
                        event.pan,
                        event.detuneCents,
                        event.lfoDelayFrames,
                        event.targetMidi,
                        event.slideFrames
                    )
                    if (ch < AudioLaws.FM_CHANNELS) {
                        fmLogicalPartByVoice[ch] = ch
                        performance.noteOnFm(ch)
                    }
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
                    performance.setHardwareLfoPms(event.channel, event.pms)
                    performance.setHardwareLfoAms(event.channel, event.ams)
                    voice.setNoteControls(
                        event.pan,
                        event.detuneCents,
                        event.lfoDelayFrames,
                        -1,
                        0
                    )
                    fmLogicalPartByVoice[voiceIndex] = event.channel
                    performance.noteOnFm(event.channel)
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
                    performance.setSsgBaseLevel(ch, voice.fixedLevelSnapshot())
                    performance.noteOnSsg(ch)
                    voice.noteOn(frequency)
                    voice.noteGain = event.velocity
                    ssgActiveNoteId[ch] = event.noteId
                }
            }
            SequencerEvent.SSG_OFF -> {
                val ch = event.channel
                if (ch >= 0 && ch < ssg.size && ssgActiveNoteId[ch] == event.noteId) {
                    ssg[ch].noteOff(performance.noteOffSsg(ch))
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
                performance.setTempo(bpmMilli, timeline.pmdClocksPerQuarter)
            }
            CompiledOpnaTimeline.SSG_ENVELOPE_DEFINE -> {
                val channel = timeline.channel[index]
                if (channel >= 0 && channel < ssg.size) {
                    val base = index * CompiledOpnaTimeline.CONTROL_STRIDE
                    performance.configureSsgEnvelope(
                        channel,
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
                    performance.setSsgEnvelopeClockMode(
                        channel,
                        timeline.controlValues[index * CompiledOpnaTimeline.CONTROL_STRIDE]
                    )
                }
            }
            CompiledOpnaTimeline.SSG_TONE_ENABLE -> {
                val channel = timeline.channel[index]
                if (channel in ssg.indices) {
                    ssgShared.writeToneEnabled(channel, timeline.controlValue(index) != 0)
                    ssgHardwareStateConfigured[channel] = true
                }
            }
            CompiledOpnaTimeline.SSG_NOISE_ENABLE -> {
                val channel = timeline.channel[index]
                if (channel in ssg.indices) {
                    ssgShared.writeNoiseEnabled(channel, timeline.controlValue(index) != 0)
                    ssgHardwareStateConfigured[channel] = true
                }
            }
            CompiledOpnaTimeline.SSG_NOISE_PERIOD -> {
                if (timeline.channel[index] in ssg.indices) {
                    ssgShared.writeNoisePeriod(timeline.controlValue(index))
                }
            }
            CompiledOpnaTimeline.SSG_HARDWARE_ENVELOPE_PERIOD -> {
                if (timeline.channel[index] in ssg.indices) {
                    ssgShared.writeEnvelopePeriod(timeline.controlValue(index))
                }
            }
            CompiledOpnaTimeline.SSG_HARDWARE_ENVELOPE_SHAPE -> {
                if (timeline.channel[index] in ssg.indices) {
                    ssgShared.writeEnvelopeShape(timeline.controlValue(index))
                }
            }
            CompiledOpnaTimeline.HW_LFO_ENABLE -> {
                lfo.enabled = timeline.controlValue(index) != 0
            }
            CompiledOpnaTimeline.HW_LFO_RATE -> {
                lfo.rate = timeline.controlValue(index)
            }
            CompiledOpnaTimeline.HW_LFO_PMS -> {
                performance.setHardwareLfoPms(timeline.channel[index], timeline.controlValue(index))
            }
            CompiledOpnaTimeline.HW_LFO_AMS -> {
                performance.setHardwareLfoAms(timeline.channel[index], timeline.controlValue(index))
            }
            CompiledOpnaTimeline.HW_LFO_DELAY -> {
                val base = index * CompiledOpnaTimeline.CONTROL_STRIDE
                performance.setHardwareLfoDelay(
                    timeline.channel[index],
                    timeline.controlValues[base],
                    timeline.controlValues[base + 1],
                    timeline.controlValues[base + 2] != 0
                )
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
                val part = fm3PartIndex(timeline.logicalPart[index])
                val mask = timeline.controlValues[index * CompiledOpnaTimeline.CONTROL_STRIDE]
                performance.setSlotMask(part, mask)
                bindFm3DriverFrames(part, mask)
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
                    voice.setNoteControls(
                        timeline.pan[index],
                        timeline.detuneCents[index],
                        performance.hardwareLfoDelayFrames(channel),
                        timeline.targetMidi[index],
                        timeline.slideFrames[index]
                    )
                    if (channel < AudioLaws.FM_CHANNELS) {
                        fmLogicalPartByVoice[channel] = channel
                        performance.noteOnFm(channel)
                    }
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
                    voice.setNoteControls(
                        timeline.pan[index],
                        timeline.detuneCents[index],
                        performance.hardwareLfoDelayFrames(timeline.channel[index]),
                        -1,
                        0
                    )
                    fmLogicalPartByVoice[voiceIndex] = timeline.channel[index]
                    performance.noteOnFm(timeline.channel[index])
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
                    performance.setSsgBaseLevel(channel, voice.fixedLevelSnapshot())
                    performance.noteOnSsg(channel)
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
                    ssg[channel].noteOff(performance.noteOffSsg(channel))
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
                bindFm3DriverFrames(part, mask)
                performance.noteOnFm3(part)
                fm[2].setNoteControls(
                    timeline.pan[index],
                    timeline.detuneCents[index],
                    performance.hardwareLfoDelayFrames(FM3_PHYSICAL_VOICE),
                    -1,
                    0
                )
                fm[2].noteOnSlots(
                    mask,
                    timeline.midi[index],
                    timeline.targetMidi[index],
                    timeline.slideFrames[index],
                    timeline.detuneCents[index]
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
                CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE -> performance.configureFm3Lfo(
                    fm3Part, lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3], timeline.controlValues[base + 4]
                )
                CompiledOpnaTimeline.SOFTWARE_LFO_SWITCH -> performance.setFm3LfoSwitch(fm3Part, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_WAVE -> performance.setFm3LfoWaveform(fm3Part, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_CLOCK -> performance.setFm3LfoClockMode(fm3Part, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_TL_MASK -> performance.setFm3LfoTlMask(fm3Part, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH -> performance.setFm3LfoDepthEvolution(
                    fm3Part, lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3]
                )
            }
        } else if (family == 0 && channel in 0 until AudioLaws.FM_CHANNELS) {
            when (timeline.eventType[eventIndex]) {
                CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE -> performance.configureFmLfo(
                    channel, lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3], timeline.controlValues[base + 4]
                )
                CompiledOpnaTimeline.SOFTWARE_LFO_SWITCH -> performance.setFmLfoSwitch(channel, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_WAVE -> performance.setFmLfoWaveform(channel, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_CLOCK -> performance.setFmLfoClockMode(channel, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_TL_MASK -> performance.setFmLfoTlMask(channel, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH -> performance.setFmLfoDepthEvolution(
                    channel, lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3]
                )
            }
        } else if (family == 1 && channel in ssg.indices) {
            when (timeline.eventType[eventIndex]) {
                CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE -> performance.configureSsgLfo(
                    channel, lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3], timeline.controlValues[base + 4]
                )
                CompiledOpnaTimeline.SOFTWARE_LFO_SWITCH -> performance.setSsgLfoSwitch(channel, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_WAVE -> performance.setSsgLfoWaveform(channel, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_CLOCK -> performance.setSsgLfoClockMode(channel, lfoIndex, timeline.controlValues[base + 1])
                CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH -> performance.setSsgLfoDepthEvolution(
                    channel, lfoIndex, timeline.controlValues[base + 1], timeline.controlValues[base + 2],
                    timeline.controlValues[base + 3]
                )
            }
        }
    }

    private fun CompiledOpnaTimeline.controlValue(eventIndex: Int): Int =
        controlValues[eventIndex * CompiledOpnaTimeline.CONTROL_STRIDE]

    private fun ensureDefaultSsgHardwareState(channel: Int) {
        if (ssgHardwareStateConfigured[channel]) return
        ssgShared.writeMixerChannel(channel, toneEnabled = true, noiseEnabled = false)
        ssgHardwareStateConfigured[channel] = true
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
    internal fun fm3PartLfoTlMaskSnapshot(part: Int, lfo: Int): Int =
        performance.fm3LfoTlMaskSnapshot(part, lfo)

    internal fun fmSoftwareLfoValueSnapshot(part: Int, lfo: Int): Int =
        performance.fmLfoValueSnapshot(part, lfo)

    internal fun ssgSoftwareLfoValueSnapshot(part: Int, lfo: Int): Int =
        performance.ssgLfoValueSnapshot(part, lfo)

    internal fun ssgSoftwareEnvelopeLevelOffsetSnapshot(part: Int): Int =
        performance.ssgEnvelopeLevelOffsetSnapshot(part)

    internal fun logicalFmPartForVoiceSnapshot(voice: Int): Int =
        if (voice in fmLogicalPartByVoice.indices) fmLogicalPartByVoice[voice] else LOGICAL_PART_NONE

    internal fun activeFmNoteIdSnapshot(voice: Int): Int =
        if (voice in fmActiveNoteId.indices) fmActiveNoteId[voice] else FM_VOICE_FREE

    internal fun hardwareLfoPmsSnapshot(part: Int): Int = performance.hardwareLfoPmsSnapshot(part)
    internal fun hardwareLfoAmsSnapshot(part: Int): Int = performance.hardwareLfoAmsSnapshot(part)
    internal fun hardwareLfoDelayKindSnapshot(part: Int): Int = performance.hardwareLfoDelayKindSnapshot(part)
    internal fun hardwareLfoDelayValueSnapshot(part: Int): Int = performance.hardwareLfoDelayValueSnapshot(part)
    internal fun hardwareLfoDelayDottedSnapshot(part: Int): Boolean = performance.hardwareLfoDelayDottedSnapshot(part)
    internal fun hardwareLfoDelayFramesSnapshot(part: Int): Int = performance.hardwareLfoDelayFrames(part)

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
                fmLogicalPartByVoice[voiceIndex] =
                    if (voiceIndex < AudioLaws.FM_CHANNELS) voiceIndex else LOGICAL_PART_NONE
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
        private const val LOGICAL_PART_NONE = -1
        private const val FM3_PHYSICAL_VOICE = 2
        private const val STEREO_CHANNELS = 2
        const val MAX_FRAMES_PER_CHUNK = 1024
        const val SOFT_CLIP_KNEE = SongMastering.SOFT_CLIP_KNEE
    }
}
