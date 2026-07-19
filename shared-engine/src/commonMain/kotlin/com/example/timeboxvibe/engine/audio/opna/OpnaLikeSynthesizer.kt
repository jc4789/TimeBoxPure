package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq

class OpnaLikeSynthesizer private constructor(
    val sampleRate: Int,
    private val renderProfile: OpnaRenderProfile
) {
    constructor(sampleRate: Int = AudioLaws.SAMPLE_RATE) : this(
        sampleRate,
        OpnaRenderProfile.inspection(sampleRate)
    )

    private val chip = OpnaChipState(sampleRate, renderProfile.enableFmOversampling)
    private val performance = PmdPerformanceState(sampleRate)
    private val mixer = OpnaMixer(renderProfile.outputProfile)
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
    private val percussion: PercussionRouter get() = chip.percussion
    val lfo: Lfo get() = chip.lfo

    val preClampPeak: Float get() = mastering.preClampPeak
    val preClampKneeCrossings: Int get() = mastering.preClampKneeCrossings

    private val fmActiveNoteId = IntArray(AudioLaws.FM_CHANNELS) { FM_VOICE_FREE }
    private val ssgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { -1 }
    private val ssgHardwareStateConfigured = BooleanArray(AudioLaws.SSG_CHANNELS)
    private val fm3ActiveNoteId = IntArray(AudioLaws.FM_OPERATORS) { -1 }
    private val fm3DriverFrameByOperator = arrayOfNulls<PmdModulationFrame>(AudioLaws.FM_OPERATORS)
    internal val filterAlpha: Float get() = renderProfile.outputFilterAlpha
    internal val enableOutputFilter: Boolean get() = renderProfile.enableOutputFilter
    internal val enableStereoResonator: Boolean get() = renderProfile.enableStereoResonator
    internal val outputProfile: OpnaOutputProfile get() = renderProfile.outputProfile

    init {
        OpnLogTables.warmUp()
        DrumSinLut.warmUp()
        mastering.configure(renderProfile)
        resetFm3DriverMappings()
    }

    fun noteOnSsg(channel: Int, midi: Int) {
        if (channel in ssg.indices) {
            ensureDefaultSsgHardwareState(channel)
            performance.setDirectSsgBaseLevel(channel, ssg[channel].fixedLevelSnapshot())
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
        if (channel in 0 until AudioLaws.FM_CHANNELS) {
            performance.noteOnFm(channel)
            fm[channel].noteOn(midi)
        }
    }

    fun noteOnFm(channel: Int, midi: Int, patch: FmPatch) {
        if (channel in 0 until AudioLaws.FM_CHANNELS) {
            fm[channel].applyPatch(patch)
            // The direct preview API has no compiled timeline; seed its logical owner explicitly.
            performance.setHardwareLfoPms(channel, patch.pms)
            performance.setHardwareLfoAms(channel, patch.ams)
            performance.noteOnFm(channel)
            fm[channel].noteOn(midi)
        }
    }

    fun noteOffFm(channel: Int) {
        if (channel in 0 until AudioLaws.FM_CHANNELS) {
            fm[channel].noteOff()
        }
    }

    internal fun rhythmShot(mask: Int) {
        percussion.shotYm(mask)
    }

    internal fun rhythmDump(mask: Int) {
        percussion.dumpYm(mask)
    }

    internal fun setRhythmMasterLevel(value: Int, relative: Boolean) {
        percussion.setYmMasterLevel(value, relative)
    }

    internal fun setRhythmVoiceLevel(voice: Int, value: Int, relative: Boolean) {
        percussion.setYmVoiceLevel(voice, value, relative)
    }

    internal fun setRhythmVoicePan(voice: Int, pan: Int) {
        percussion.setYmVoicePan(voice, pan)
    }

    internal fun rhythmMasterLevelSnapshot(): Int = percussion.ymMasterLevelSnapshot()
    internal fun rhythmVoiceLevelSnapshot(voice: Int): Int = percussion.ymVoiceLevelSnapshot(voice)
    internal fun rhythmVoicePanSnapshot(voice: Int): Int = percussion.ymVoicePanSnapshot(voice)
    internal fun rhythmGeneratorStateSnapshot(voice: Int): Int = percussion.ymGeneratorStateSnapshot(voice)
    internal fun rhythmGeneratorGainSnapshot(voice: Int): Float = percussion.ymGeneratorGainSnapshot(voice)
    internal fun rhythmGeneratorPanSnapshot(voice: Int): Int = percussion.ymGeneratorPanSnapshot(voice)

    internal fun ssgDrumTriggerCountSnapshot(): Int = percussion.pmdTriggerCountSnapshot()
    internal fun pmdSsgEffectStateSnapshot(kind: ProceduralDrums.DrumKind): Int =
        percussion.pmdGeneratorStateSnapshot(kind)
    internal fun percussionActiveDomainMaskSnapshot(): Int = percussion.activeDomainMaskSnapshot()

    internal fun triggerPmdSsgEffect(kind: Int, velocity: Float) {
        percussion.triggerPmdSsgEffect(kind, velocity)
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
            i++
        }
        percussion.stop()
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
            i++
        }
        percussion.reset()
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

    private fun resetMasteringDomain() {
        mastering.reset()
    }

    /** Rebuilds timeline-owned state without erasing caller-owned mastering history. */
    internal fun resetTimelineDomains() {
        resetChipDomain()
        resetPerformanceDomain()
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

    internal fun render(buffer: FloatArray, frames: Int, player: CompiledOpnaPlayer, currentSampleOffset: Long) {
        renderProfiledPreMaster(buffer, frames, player, currentSampleOffset)
        mastering.processMono(buffer, frames)
    }

    internal fun renderProductSequential(
        buffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer
    ) {
        renderTimelineSequentialWholeBuffer(
            buffer, frames, player,
            CompiledOpnaPlayer.CHANNELS_MONO,
            CompiledOpnaPlayer.STAGE_PROFILED_PRE_MASTER
        )
        mastering.processMono(buffer, frames)
    }

    internal fun renderProductStereoSequential(
        buffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer
    ) {
        renderTimelineSequentialWholeBuffer(
            buffer, frames, player,
            CompiledOpnaPlayer.CHANNELS_STEREO,
            CompiledOpnaPlayer.STAGE_PROFILED_PRE_MASTER
        )
        mastering.processStereo(buffer, frames)
    }

    internal fun renderStageSequential(
        buffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        channelCount: Int,
        outputStage: Int
    ) {
        renderTimelineSequentialWholeBuffer(buffer, frames, player, channelCount, outputStage)
    }

    /** Timeline playback at unity chip-bus gain, before output profile and song mastering. */
    internal fun renderRawCore(
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
    internal fun renderProfiledPreMaster(
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

    internal fun renderStereo(
        stereoBuffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        currentSampleOffset: Long
    ) {
        renderProfiledPreMasterStereo(stereoBuffer, frames, player, currentSampleOffset)
        mastering.processStereo(stereoBuffer, frames)
    }

    /** Stereo timeline playback at unity chip-bus gain, before profile and mastering. */
    internal fun renderRawCoreStereo(
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
    internal fun renderProfiledPreMasterStereo(
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
            player.renderAtSequentialOffset(
                this, buffer, offset, chunkFrames, sampleOffset,
                channelCount, outputStage
            )
            offset += chunkFrames
            remaining -= chunkFrames
            sampleOffset += chunkFrames
        }
    }

    private fun renderTimelineSequentialWholeBuffer(
        buffer: FloatArray,
        frames: Int,
        player: CompiledOpnaPlayer,
        channelCount: Int,
        outputStage: Int
    ) {
        buffer.fill(0f)
        var offset = 0
        var remaining = frames
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
            player.render(this, buffer, offset, chunkFrames, channelCount, outputStage)
            offset += chunkFrames
            remaining -= chunkFrames
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
                performance.fmFrame(i),
                if (i == FM3_PHYSICAL_VOICE) fm3DriverFrameByOperator else null
            )
            i++
        }
        percussion.renderMono(rhythmMonoBus, ssgMonoBus, frames, sampleRate, 0)
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
                driverFrame = performance.fmFrame(i),
                fm3DriverFrames = if (i == FM3_PHYSICAL_VOICE) fm3DriverFrameByOperator else null
            )
            val pan = fm[i].getPan()
            panMonoToStereo(tempMonoBuffer, fmStereoBus, frames, 0, pan)
            i++
        }

        percussion.renderStereo(rhythmStereoBus, ssgStereoBus, frames, sampleRate, 0)
    }

    private fun preparePerformanceFrames(frames: Int) {
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

    internal fun handleTimelineEvent(timeline: CompiledOpnaTimeline, index: Int) {
        when (timeline.boundaryKind(index)) {
            CompiledOpnaTimeline.BOUNDARY_TEMPO -> {
                val payload = timeline.payloadIndex(index)
                val bpmMilli = if (payload == CompiledOpnaTimeline.INITIAL_TEMPO_PAYLOAD) {
                    timeline.song.bpmMilli
                } else timeline.song.tempoBpmMilli(payload)
                performance.setTempo(bpmMilli, timeline.pmdClocksPerQuarter)
            }
            CompiledOpnaTimeline.BOUNDARY_NOTE_ON,
            CompiledOpnaTimeline.BOUNDARY_NOTE_OFF -> handleNoteBoundary(timeline, index)
            CompiledOpnaTimeline.BOUNDARY_GLIDE_START -> handleGlideStart(timeline, index)
            CompiledOpnaTimeline.BOUNDARY_STATE -> handleStatePayload(timeline, index)
            CompiledOpnaTimeline.BOUNDARY_MODULATION -> handleModulationPayload(timeline, index)
            CompiledOpnaTimeline.BOUNDARY_RHYTHM -> handleRhythmPayload(timeline, index)
        }
    }

    private fun handleNoteBoundary(timeline: CompiledOpnaTimeline, boundary: Int) {
        val payload = timeline.payloadIndex(boundary)
        val notes = timeline.song.notes
        val channel = notes.channel(payload)
        val kind = timeline.semanticKind(boundary)
        val noteId = timeline.authoredEventIndex(boundary) + 1
        val isOn = timeline.boundaryKind(boundary) == CompiledOpnaTimeline.BOUNDARY_NOTE_ON
        if (!isOn) {
            when (kind) {
                CompiledOpnaSong.FM_NOTE -> if (channel in fm.indices && fmActiveNoteId[channel] == noteId) {
                    fm[channel].noteOff(); fmActiveNoteId[channel] = FM_VOICE_FREE
                }
                CompiledOpnaSong.SSG_NOTE -> if (channel in ssg.indices && ssgActiveNoteId[channel] == noteId) {
                    ssg[channel].noteOff(performance.noteOffSsg(channel)); ssgActiveNoteId[channel] = -1
                }
                CompiledOpnaSong.FM3_OPERATOR_NOTE -> {
                    val mask = notes.slotMask(payload) and 15
                    var releaseMask = 0
                    var operator = 0
                    while (operator < AudioLaws.FM_OPERATORS) {
                        if ((mask and (1 shl operator)) != 0 && fm3ActiveNoteId[operator] == noteId) {
                            releaseMask = releaseMask or (1 shl operator); fm3ActiveNoteId[operator] = -1
                        }
                        operator++
                    }
                    fm[2].noteOffSlots(releaseMask)
                }
            }
            return
        }
        val startTick = notes.startTick(payload)
        val glideOffset = notes.glideStartOffsetTick(payload)
        val slideFrames = (PmdSampleClock.samplesAt(
            timeline.song, startTick + notes.durationTick(payload), sampleRate
        ) - PmdSampleClock.samplesAt(timeline.song, startTick + glideOffset, sampleRate)).toInt()
        when (kind) {
            CompiledOpnaSong.FM_NOTE -> if (channel in fm.indices) {
                val voice = fm[channel]
                val patch = timeline.instrumentBank.fmPatch(notes.patchId(payload))
                if (patch != null) voice.applyPatch(patch)
                val detuneRaw = performance.fmDetuneRaw(channel)
                voice.setNoteControls(notes.pan(payload), detuneRaw,
                    performance.hardwareLfoDelayFrames(channel),
                    if (glideOffset == 0) notes.targetMidi(payload) else -1, slideFrames)
                performance.noteOnFm(channel)
                voice.noteOnScheduled(notes.midi(payload))
                fmActiveNoteId[channel] = noteId
            }
            CompiledOpnaSong.SSG_NOTE -> if (channel in ssg.indices) {
                val voice = ssg[channel]
                val patch = timeline.instrumentBank.ssgPatch(notes.patchId(payload))
                if (patch != null) voice.applyPatch(patch)
                voice.setPan(notes.pan(payload))
                val detuneRaw = performance.ssgDetuneRaw(channel)
                val basePeriod = OpnPitch.lowerSsgTonePeriod(
                    SsgHardwareLaws.nearestTonePeriod(midiToFreq(notes.midi(payload)).toDouble()), detuneRaw)
                val targetPeriod = if (glideOffset == 0 && notes.targetMidi(payload) >= 0)
                    OpnPitch.lowerSsgTonePeriod(SsgHardwareLaws.nearestTonePeriod(
                        midiToFreq(notes.targetMidi(payload)).toDouble()), detuneRaw) else -1
                performance.noteOnSsg(channel); voice.noteOnPeriod(basePeriod, targetPeriod, slideFrames)
                ssgActiveNoteId[channel] = noteId
            }
            CompiledOpnaSong.FM3_OPERATOR_NOTE -> {
                val mask = notes.slotMask(payload) and 15
                val part = fm3PartIndex(notes.logicalPart(payload))
                performance.setSlotMask(part, mask); bindFm3DriverFrames(part, mask); performance.noteOnFm3(part)
                val detuneRaw = performance.fm3DetuneRaw(part)
                fm[2].setNoteControls(notes.pan(payload), detuneRaw,
                    performance.hardwareLfoDelayFrames(FM3_PHYSICAL_VOICE), -1, 0)
                fm[2].noteOnSlots(mask, notes.midi(payload),
                    if (glideOffset == 0) notes.targetMidi(payload) else -1, slideFrames, detuneRaw)
                var operator = 0
                while (operator < AudioLaws.FM_OPERATORS) {
                    if ((mask and (1 shl operator)) != 0) fm3ActiveNoteId[operator] = noteId
                    operator++
                }
            }
        }
    }

    private fun handleGlideStart(timeline: CompiledOpnaTimeline, boundary: Int) {
        val payload = timeline.payloadIndex(boundary)
        val notes = timeline.song.notes
        val noteId = timeline.authoredEventIndex(boundary) + 1
        val targetMidi = notes.targetMidi(payload)
        if (targetMidi !in 0..127) return
        val start = notes.startTick(payload) + notes.glideStartOffsetTick(payload)
        val frames = (PmdSampleClock.samplesAt(timeline.song,
            notes.startTick(payload) + notes.durationTick(payload), sampleRate) -
            PmdSampleClock.samplesAt(timeline.song, start, sampleRate)).toInt()
        val channel = notes.channel(payload)
        when (timeline.semanticKind(boundary)) {
            CompiledOpnaSong.FM_NOTE -> if (channel in fm.indices && fmActiveNoteId[channel] == noteId)
                fm[channel].startHeldPitchRamp(targetMidi, performance.fmDetuneRaw(channel), frames)
            CompiledOpnaSong.SSG_NOTE -> if (channel in ssg.indices && ssgActiveNoteId[channel] == noteId) {
                val period = OpnPitch.lowerSsgTonePeriod(SsgHardwareLaws.nearestTonePeriod(
                    midiToFreq(targetMidi).toDouble()), performance.ssgDetuneRaw(channel))
                ssg[channel].startHeldPitchRampPeriod(period, frames)
            }
            CompiledOpnaSong.FM3_OPERATOR_NOTE -> {
                val mask = notes.slotMask(payload) and 15
                var activeMask = 0
                var operator = 0
                while (operator < AudioLaws.FM_OPERATORS) {
                    if ((mask and (1 shl operator)) != 0 && fm3ActiveNoteId[operator] == noteId)
                        activeMask = activeMask or (1 shl operator)
                    operator++
                }
                val part = fm3PartIndex(notes.logicalPart(payload))
                fm[2].startHeldSlotPitchRamp(activeMask, targetMidi, performance.fm3DetuneRaw(part), frames)
            }
        }
    }

    private fun handleStatePayload(timeline: CompiledOpnaTimeline, boundary: Int) {
        val states = timeline.song.states
        val payload = timeline.payloadIndex(boundary)
        val kind = timeline.semanticKind(boundary)
        val channel = timeline.channel(boundary)
        when (kind) {
            CompiledOpnaSong.SSG_ENVELOPE_DEFINE -> if (channel in ssg.indices) {
                val definitions = states.envelopeDefinitions
                performance.configureSsgEnvelope(channel, definitions.format(payload), definitions.attack(payload),
                    definitions.decay(payload), definitions.sustain(payload), definitions.release(payload),
                    definitions.sustainLevel(payload), definitions.attackLevel(payload))
            }
            CompiledOpnaSong.SSG_ENVELOPE_MODE -> if (channel in ssg.indices)
                performance.setSsgEnvelopeClockMode(channel, states.envelopeModes.mode(payload))
            CompiledOpnaSong.SSG_TONE_ENABLE -> if (channel in ssg.indices) {
                ssgShared.writeToneEnabled(channel, states.mixerEnables.enabled(payload)); ssgHardwareStateConfigured[channel] = true
            }
            CompiledOpnaSong.SSG_NOISE_ENABLE -> if (channel in ssg.indices) {
                ssgShared.writeNoiseEnabled(channel, states.mixerEnables.enabled(payload)); ssgHardwareStateConfigured[channel] = true
            }
            CompiledOpnaSong.SSG_NOISE_PERIOD -> if (channel in ssg.indices)
                ssgShared.writeNoisePeriod(states.periods.period(payload))
            CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_PERIOD -> if (channel in ssg.indices)
                ssgShared.writeEnvelopePeriod(states.periods.period(payload))
            CompiledOpnaSong.SSG_HARDWARE_ENVELOPE_SHAPE -> if (channel in ssg.indices)
                ssgShared.writeEnvelopeShape(states.envelopeShapes.shape(payload))
        }
    }

    private fun handleModulationPayload(timeline: CompiledOpnaTimeline, boundary: Int) {
        val controls = timeline.song.modulations
        val payload = timeline.payloadIndex(boundary)
        val kind = timeline.semanticKind(boundary)
        when (kind) {
            CompiledOpnaSong.HW_LFO_ENABLE -> lfo.enabled = controls.hardwareLfoEnables.enabled(payload)
            CompiledOpnaSong.HW_LFO_RATE -> lfo.rate = controls.hardwareLfoRates.rate(payload)
            CompiledOpnaSong.HW_LFO_PMS -> performance.setHardwareLfoPms(
                controls.hardwareLfoDepths.channel(payload), controls.hardwareLfoDepths.selector(payload))
            CompiledOpnaSong.HW_LFO_AMS -> performance.setHardwareLfoAms(
                controls.hardwareLfoDepths.channel(payload), controls.hardwareLfoDepths.selector(payload))
            CompiledOpnaSong.HW_LFO_DELAY -> performance.setHardwareLfoDelay(
                controls.hardwareLfoDelays.channel(payload), controls.hardwareLfoDelays.delayKind(payload),
                controls.hardwareLfoDelays.delayValue(payload), controls.hardwareLfoDelays.dotted(payload))
            CompiledOpnaSong.FM3_PATCH -> {
                val patches = controls.fm3Patches
                val patch = timeline.instrumentBank.fmPatch(patches.patchId(payload))
                if (patch != null) {
                    val mask = patches.slotMask(payload) and 15
                    if (patches.logicalPart(payload) == CompiledOpnaSong.LOGICAL_PART_NONE && mask == 15)
                        fm[2].applyPatch(patch) else fm[2].applyPatchToSlots(patch, mask)
                }
            }
            CompiledOpnaSong.FM_PART_VOLUME -> {
                val values = controls.partVolumes
                val part = values.part(payload)
                val fm3Part = fm3PartIndex(part)
                if (fm3Part >= 0) performance.setFm3Volume(fm3Part, values.volume(payload))
                else performance.setFmVolume(part, values.volume(payload))
            }
            CompiledOpnaSong.SSG_PART_VOLUME -> {
                val values = controls.partVolumes
                performance.setSsgVolume(values.part(payload), values.volume(payload))
            }
            CompiledOpnaSong.PART_DETUNE_ABSOLUTE, CompiledOpnaSong.PART_DETUNE_RELATIVE,
            CompiledOpnaSong.MASTER_DETUNE_ABSOLUTE -> {
                val values = controls.partDetunes
                val part = values.part(payload)
                if (values.targetDomain(payload) == CompiledOpnaSong.DETUNE_TARGET_SSG) {
                    performance.setSsgDetune(part, values.value(payload), kind)
                } else {
                    val fm3Part = fm3PartIndex(part)
                    if (fm3Part >= 0) performance.setFm3Detune(fm3Part, values.value(payload), kind)
                    else performance.setFmDetune(part, values.value(payload), kind)
                }
            }
            CompiledOpnaSong.FM_PART_SLOT_MASK -> {
                val values = controls.fmPartSlotMasks
                val part = fm3PartIndex(values.logicalPart(payload)); val mask = values.slotMask(payload)
                performance.setSlotMask(part, mask); bindFm3DriverFrames(part, mask)
            }
            CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE, CompiledOpnaSong.FM_SLOT_DETUNE_RELATIVE -> {
                val values = controls.fmSlotDetunes; val channel = values.channel(payload)
                if (channel in fm.indices) fm[channel].setSlotDetune(values.slotMask(payload) and 15,
                    values.rawDelta(payload), kind == CompiledOpnaSong.FM_SLOT_DETUNE_RELATIVE)
            }
            CompiledOpnaSong.FM_TL_ABSOLUTE, CompiledOpnaSong.FM_TL_RELATIVE -> {
                val values = controls.fmTotalLevels; val channel = values.channel(payload)
                if (channel in fm.indices) fm[channel].setOperatorTl(values.slotMask(payload) and 15,
                    values.totalLevel(payload), kind == CompiledOpnaSong.FM_TL_RELATIVE)
            }
            CompiledOpnaSong.FM_FEEDBACK_ABSOLUTE, CompiledOpnaSong.FM_FEEDBACK_RELATIVE -> {
                val values = controls.fmFeedback; val channel = values.channel(payload)
                if (channel in fm.indices) fm[channel].setFeedback(values.feedback(payload),
                    kind == CompiledOpnaSong.FM_FEEDBACK_RELATIVE)
            }
            CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY -> {
                val values = controls.fmKeyOnDelays; val channel = values.channel(payload)
                if (channel in fm.indices) {
                    val tick = values.tick(payload); val delayTicks = values.delayTicks(payload)
                    val frames = (PmdSampleClock.samplesAt(timeline.song, tick + delayTicks, sampleRate) -
                        PmdSampleClock.samplesAt(timeline.song, tick, sampleRate)).toInt()
                    fm[channel].setSlotKeyOnDelay(values.slotMask(payload) and 15, frames)
                }
            }
            CompiledOpnaSong.SOFTWARE_LFO_DEFINE -> handleSoftwareLfoDefinition(controls, payload)
            CompiledOpnaSong.SOFTWARE_LFO_SWITCH -> handleSoftwareLfoSwitch(controls, payload)
            CompiledOpnaSong.SOFTWARE_LFO_WAVE -> handleSoftwareLfoWaveform(controls, payload)
            CompiledOpnaSong.SOFTWARE_LFO_CLOCK -> handleSoftwareLfoClockMode(controls, payload)
            CompiledOpnaSong.SOFTWARE_LFO_TL_MASK -> handleSoftwareLfoTlMask(controls, payload)
            CompiledOpnaSong.SOFTWARE_LFO_DEPTH -> handleSoftwareLfoDepth(controls, payload)
        }
    }

    private fun handleSoftwareLfoDefinition(controls: ModulationPayloadTables, payload: Int) {
        val table = controls.softwareLfoDefinitions; val id = table.identity
        val channel = id.channel(payload); val part = fm3PartIndex(id.logicalPart(payload)); val lfoIndex = id.lfoIndex(payload)
        if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && part >= 0) {
            performance.configureFm3Lfo(part, lfoIndex, table.delayClocks(payload), table.speedClocks(payload),
                table.depthStep(payload), table.depthCount(payload))
        } else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && channel in fm.indices) {
            performance.configureFmLfo(channel, lfoIndex, table.delayClocks(payload), table.speedClocks(payload),
                table.depthStep(payload), table.depthCount(payload))
        } else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG && channel in ssg.indices) {
            performance.configureSsgLfo(channel, lfoIndex, table.delayClocks(payload), table.speedClocks(payload),
                table.depthStep(payload), table.depthCount(payload))
        }
    }

    private fun handleSoftwareLfoSwitch(controls: ModulationPayloadTables, payload: Int) {
        val table = controls.softwareLfoSwitches; val id = table.identity
        val channel = id.channel(payload); val part = fm3PartIndex(id.logicalPart(payload)); val lfoIndex = id.lfoIndex(payload)
        if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && part >= 0)
            performance.setFm3LfoSwitch(part, lfoIndex, table.switchMode(payload))
        else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && channel in fm.indices)
            performance.setFmLfoSwitch(channel, lfoIndex, table.switchMode(payload))
        else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG && channel in ssg.indices)
            performance.setSsgLfoSwitch(channel, lfoIndex, table.switchMode(payload))
    }

    private fun handleSoftwareLfoWaveform(controls: ModulationPayloadTables, payload: Int) {
        val table = controls.softwareLfoWaveforms; val id = table.identity
        val channel = id.channel(payload); val part = fm3PartIndex(id.logicalPart(payload)); val lfoIndex = id.lfoIndex(payload)
        if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && part >= 0)
            performance.setFm3LfoWaveform(part, lfoIndex, table.waveform(payload))
        else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && channel in fm.indices)
            performance.setFmLfoWaveform(channel, lfoIndex, table.waveform(payload))
        else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG && channel in ssg.indices)
            performance.setSsgLfoWaveform(channel, lfoIndex, table.waveform(payload))
    }

    private fun handleSoftwareLfoClockMode(controls: ModulationPayloadTables, payload: Int) {
        val table = controls.softwareLfoClockModes; val id = table.identity
        val channel = id.channel(payload); val part = fm3PartIndex(id.logicalPart(payload)); val lfoIndex = id.lfoIndex(payload)
        if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && part >= 0)
            performance.setFm3LfoClockMode(part, lfoIndex, table.clockMode(payload))
        else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && channel in fm.indices)
            performance.setFmLfoClockMode(channel, lfoIndex, table.clockMode(payload))
        else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG && channel in ssg.indices)
            performance.setSsgLfoClockMode(channel, lfoIndex, table.clockMode(payload))
    }

    private fun handleSoftwareLfoTlMask(controls: ModulationPayloadTables, payload: Int) {
        val table = controls.softwareLfoTlMasks; val id = table.identity
        val channel = id.channel(payload); val part = fm3PartIndex(id.logicalPart(payload)); val lfoIndex = id.lfoIndex(payload)
        if (part >= 0) performance.setFm3LfoTlMask(part, lfoIndex, table.slotMask(payload))
        else if (channel in fm.indices) performance.setFmLfoTlMask(channel, lfoIndex, table.slotMask(payload))
    }

    private fun handleSoftwareLfoDepth(controls: ModulationPayloadTables, payload: Int) {
        val table = controls.softwareLfoDepths; val id = table.identity
        val channel = id.channel(payload); val part = fm3PartIndex(id.logicalPart(payload)); val lfoIndex = id.lfoIndex(payload)
        if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && part >= 0) {
            performance.setFm3LfoDepthEvolution(part, lfoIndex, table.speedClocks(payload),
                table.depthStep(payload), table.evolutionCount(payload))
        } else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_FM && channel in fm.indices) {
            performance.setFmLfoDepthEvolution(channel, lfoIndex, table.speedClocks(payload),
                table.depthStep(payload), table.evolutionCount(payload))
        } else if (id.targetDomain(payload) == CompiledOpnaSong.SOFTWARE_LFO_TARGET_SSG && channel in ssg.indices) {
            performance.setSsgLfoDepthEvolution(channel, lfoIndex, table.speedClocks(payload),
                table.depthStep(payload), table.evolutionCount(payload))
        }
    }

    private fun handleRhythmPayload(timeline: CompiledOpnaTimeline, boundary: Int) {
        val rhythms = timeline.song.rhythms
        val payload = timeline.payloadIndex(boundary)
        when (timeline.semanticKind(boundary)) {
            CompiledOpnaSong.RHYTHM_SHOT -> percussion.shotAuthoredYm(
                rhythms.percussionShots.drumKind(payload), rhythms.percussionShots.velocity(payload).coerceIn(0, 127) / 127f,
                rhythms.percussionShots.pan(payload))
            CompiledOpnaSong.SSG_DRUM_SHOT -> percussion.triggerPmdSsgEffect(
                rhythms.percussionShots.drumKind(payload), rhythms.percussionShots.velocity(payload).coerceIn(0, 127) / 127f)
            CompiledOpnaSong.RHYTHM_CONTROL_SHOT -> rhythmShot(rhythms.gates.voiceMask(payload))
            CompiledOpnaSong.RHYTHM_CONTROL_DUMP -> rhythmDump(rhythms.gates.voiceMask(payload))
            CompiledOpnaSong.RHYTHM_MASTER_ABSOLUTE -> setRhythmMasterLevel(rhythms.masterLevels.level(payload), false)
            CompiledOpnaSong.RHYTHM_MASTER_RELATIVE -> setRhythmMasterLevel(rhythms.masterLevels.level(payload), true)
            CompiledOpnaSong.RHYTHM_VOICE_LEVEL_ABSOLUTE -> setRhythmVoiceLevel(
                rhythms.voiceLevels.voice(payload), rhythms.voiceLevels.level(payload), false)
            CompiledOpnaSong.RHYTHM_VOICE_LEVEL_RELATIVE -> setRhythmVoiceLevel(
                rhythms.voiceLevels.voice(payload), rhythms.voiceLevels.level(payload), true)
            CompiledOpnaSong.RHYTHM_VOICE_PAN -> setRhythmVoicePan(
                rhythms.voicePans.voice(payload), rhythms.voicePans.pan(payload))
        }
    }

    private fun ensureDefaultSsgHardwareState(channel: Int) {
        if (ssgHardwareStateConfigured[channel]) return
        ssgShared.writeMixerChannel(channel, toneEnabled = true, noiseEnabled = false)
        ssgHardwareStateConfigured[channel] = true
    }

    internal fun ssgNoisePeriodSnapshot(): Int = ssgShared.noisePeriodSnapshot()
    internal fun ssgEnvelopePeriodSnapshot(): Int = ssgShared.envelopePeriodSnapshot()
    internal fun ssgEnvelopeShapeSnapshot(): Int = ssgShared.envelopeShapeSnapshot()
    internal fun ssgEnvelopeRestartCountSnapshot(): Int = ssgShared.envelopeRestartCountSnapshot()

    private fun fm3PartIndex(logicalPart: Int): Int {
        val part = logicalPart - CompiledOpnaSong.FM3_PART_BASE
        return if (part in 0 until CompiledOpnaSong.FM3_PART_COUNT) part else -1
    }

    internal fun fmPartVolumeSnapshot(part: Int): Int = performance.fmVolumeSnapshot(part)
    internal fun fm3PartVolumeSnapshot(part: Int): Int = performance.fm3VolumeSnapshot(part)
    internal fun ssgPartVolumeSnapshot(part: Int): Int = performance.ssgVolumeSnapshot(part)
    internal fun fm3PartLfoTlMaskSnapshot(part: Int, lfo: Int): Int =
        performance.fm3LfoTlMaskSnapshot(part, lfo)

    internal fun fmSoftwareLfoValueSnapshot(part: Int, lfo: Int): Int =
        performance.fmLfoValueSnapshot(part, lfo)

    internal fun ssgSoftwareLfoValueSnapshot(part: Int, lfo: Int): Int =
        performance.ssgLfoValueSnapshot(part, lfo)

    internal fun ssgSoftwareEnvelopeLevelOffsetSnapshot(part: Int): Int =
        performance.ssgEnvelopeLevelOffsetSnapshot(part)

    internal fun activeFmNoteIdSnapshot(voice: Int): Int =
        if (voice in fmActiveNoteId.indices) fmActiveNoteId[voice] else FM_VOICE_FREE

    internal fun hardwareLfoPmsSnapshot(part: Int): Int = performance.hardwareLfoPmsSnapshot(part)
    internal fun hardwareLfoAmsSnapshot(part: Int): Int = performance.hardwareLfoAmsSnapshot(part)
    internal fun hardwareLfoDelayKindSnapshot(part: Int): Int = performance.hardwareLfoDelayKindSnapshot(part)
    internal fun hardwareLfoDelayValueSnapshot(part: Int): Int = performance.hardwareLfoDelayValueSnapshot(part)
    internal fun hardwareLfoDelayDottedSnapshot(part: Int): Boolean = performance.hardwareLfoDelayDottedSnapshot(part)
    internal fun hardwareLfoDelayFramesSnapshot(part: Int): Int = performance.hardwareLfoDelayFrames(part)

    companion object {
        internal fun create(profile: OpnaRenderProfile): OpnaLikeSynthesizer =
            OpnaLikeSynthesizer(profile.sampleRate, profile)

        private const val FM_VOICE_FREE = -1
        private const val FM3_PHYSICAL_VOICE = 2
        private const val STEREO_CHANNELS = 2
        const val MAX_FRAMES_PER_CHUNK = 1024
        const val SOFT_CLIP_KNEE = SongMastering.SOFT_CLIP_KNEE
    }
}
