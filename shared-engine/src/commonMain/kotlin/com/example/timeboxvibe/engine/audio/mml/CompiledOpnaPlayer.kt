package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimeline
import com.example.timeboxvibe.engine.audio.opna.FmRenderBinding
import com.example.timeboxvibe.engine.audio.opna.OpnPitch
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.SsgRenderBinding

/** Allocation-free PMD owner and cursor over one immutable [CompiledOpnaTimeline]. */
class CompiledOpnaPlayer internal constructor(
    internal val timeline: CompiledOpnaTimeline,
    sampleRate: Int
) {
    private val performance = PmdPerformanceState(sampleRate)
    private val seekMonoBuffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private val seekStereoBuffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK * CHANNELS_STEREO)
    private val fmActiveNoteId = IntArray(AudioLaws.FM_RENDER_VOICES) { FM_VOICE_FREE }
    private val fmLogicalPartByVoice = IntArray(AudioLaws.FM_RENDER_VOICES)
    private val fmRenderBindingStorage = Array(AudioLaws.FM_RENDER_VOICES) { FmRenderBinding() }
    private val fmRenderBindingByVoice = arrayOfNulls<FmRenderBinding>(AudioLaws.FM_RENDER_VOICES)
    private val ssgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { NOTE_ID_NONE }
    private val ssgPerformanceFrameByChannel = arrayOfNulls<PmdSsgFrame>(AudioLaws.SSG_CHANNELS)
    private val ssgRenderBindingStorage = Array(AudioLaws.SSG_CHANNELS) { SsgRenderBinding() }
    private val ssgRenderBindingByChannel = arrayOfNulls<SsgRenderBinding>(AudioLaws.SSG_CHANNELS)
    private val ssgReleasePending = BooleanArray(AudioLaws.SSG_CHANNELS)
    private val ssgDisableAfterFrame = IntArray(AudioLaws.SSG_CHANNELS) { DISABLE_FRAME_NONE }
    private val fm3ActiveNoteId = IntArray(AudioLaws.FM_OPERATORS) { NOTE_ID_NONE }
    private val fm3PerformanceFrameByOperator = arrayOfNulls<PmdFmFrame>(AudioLaws.FM_OPERATORS)
    private val fm3GroupedSource = arrayOfNulls<PmdFmFrame>(AudioLaws.FM_OPERATORS)
    private val fm3GroupedOperatorMask = IntArray(AudioLaws.FM_OPERATORS)
    private var fm3UsesLogicalParts = false
    private var renderedSampleOffset: Long = 0L
    private var activeChannelCount: Int = CHANNELS_MONO
    private var activeOutputStage: Int = STAGE_PROFILED_PRE_MASTER
    private var activeSynthesizer: OpnaLikeSynthesizer? = null

    var nextEventIndex: Int = 0
        private set

    val loopLengthSamples: Long
        get() = timeline.loopLengthSamples

    val eventCount: Int
        get() = timeline.eventCount

    init {
        resetDriverOwnership()
    }

    /** Restores every deterministic player/chip/output state used by a new loop. */
    fun reset(synthesizer: OpnaLikeSynthesizer) {
        nextEventIndex = 0
        renderedSampleOffset = 0L
        activeChannelCount = CHANNELS_MONO
        activeOutputStage = STAGE_PROFILED_PRE_MASTER
        activeSynthesizer = synthesizer
        resetDriverOwnership()
        synthesizer.reset()
    }

    fun render(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        frames: Int,
        currentSampleOffset: Long
    ) {
        renderProfiledPreMaster(synthesizer, buffer, frames, currentSampleOffset)
        synthesizer.processTimelineMasteringMono(buffer, frames)
    }

    /** Timeline playback at unity chip-bus gain, before output profile and song mastering. */
    fun renderRawCore(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        frames: Int,
        currentSampleOffset: Long
    ) {
        renderTimelineWholeBuffer(
            synthesizer, buffer, frames, currentSampleOffset,
            CHANNELS_MONO, STAGE_RAW_CORE
        )
    }

    /** Timeline playback after the named output profile, but before song mastering. */
    fun renderProfiledPreMaster(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        frames: Int,
        currentSampleOffset: Long
    ) {
        renderTimelineWholeBuffer(
            synthesizer, buffer, frames, currentSampleOffset,
            CHANNELS_MONO, STAGE_PROFILED_PRE_MASTER
        )
    }

    fun renderStereo(
        synthesizer: OpnaLikeSynthesizer,
        stereoBuffer: FloatArray,
        frames: Int,
        currentSampleOffset: Long
    ) {
        renderProfiledPreMasterStereo(synthesizer, stereoBuffer, frames, currentSampleOffset)
        synthesizer.processTimelineMasteringStereo(stereoBuffer, frames)
    }

    /** Stereo timeline playback at unity chip-bus gain, before profile and mastering. */
    fun renderRawCoreStereo(
        synthesizer: OpnaLikeSynthesizer,
        stereoBuffer: FloatArray,
        frames: Int,
        currentSampleOffset: Long
    ) {
        renderTimelineWholeBuffer(
            synthesizer, stereoBuffer, frames, currentSampleOffset,
            CHANNELS_STEREO, STAGE_RAW_CORE
        )
    }

    /** Stereo timeline playback after the output profile, but before song mastering. */
    fun renderProfiledPreMasterStereo(
        synthesizer: OpnaLikeSynthesizer,
        stereoBuffer: FloatArray,
        frames: Int,
        currentSampleOffset: Long
    ) {
        renderTimelineWholeBuffer(
            synthesizer, stereoBuffer, frames, currentSampleOffset,
            CHANNELS_STEREO, STAGE_PROFILED_PRE_MASTER
        )
    }

    private fun renderTimelineWholeBuffer(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        frames: Int,
        currentSampleOffset: Long,
        channelCount: Int,
        outputStage: Int
    ) {
        buffer.fill(0f)
        var offset = 0
        var remaining = frames
        var sampleOffset = currentSampleOffset
        while (remaining > 0) {
            val chunkFrames = minOf(remaining, OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
            renderInterval(
                synthesizer, buffer, offset, chunkFrames, sampleOffset,
                channelCount, outputStage
            )
            offset += chunkFrames
            remaining -= chunkFrames
            sampleOffset += chunkFrames
        }
    }

    /** One cursor and one dispatcher serve raw, profiled, mono, and stereo timeline playback. */
    private fun renderInterval(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        currentSampleOffset: Long,
        channelCount: Int,
        outputStage: Int
    ) {
        require(currentSampleOffset >= 0L) { "currentSampleOffset must be non-negative" }
        prepareInterval(synthesizer, currentSampleOffset, channelCount, outputStage)
        renderContiguous(
            synthesizer, buffer, startFrame, frames, currentSampleOffset,
            channelCount, outputStage
        )
        renderedSampleOffset = currentSampleOffset + frames
    }

    private fun prepareInterval(
        synthesizer: OpnaLikeSynthesizer,
        currentSampleOffset: Long,
        channelCount: Int,
        outputStage: Int
    ) {
        if (activeSynthesizer !== synthesizer || activeChannelCount != channelCount ||
            activeOutputStage != outputStage ||
            currentSampleOffset < renderedSampleOffset
        ) {
            nextEventIndex = 0
            renderedSampleOffset = 0L
            activeChannelCount = channelCount
            activeOutputStage = outputStage
            activeSynthesizer = synthesizer
            resetDriverOwnership()
            synthesizer.resetTimelineDomains()
        }
        val seekBuffer = if (channelCount == CHANNELS_STEREO) seekStereoBuffer else seekMonoBuffer
        while (renderedSampleOffset < currentSampleOffset) {
            val frames = minOf(
                OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK.toLong(),
                currentSampleOffset - renderedSampleOffset
            ).toInt()
            renderContiguous(
                synthesizer, seekBuffer, 0, frames, renderedSampleOffset,
                channelCount, outputStage
            )
            renderedSampleOffset += frames
        }
    }

    private fun renderContiguous(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        currentSampleOffset: Long,
        channelCount: Int,
        outputStage: Int
    ) {
        val chunkEnd = currentSampleOffset + frames
        var renderPosition = 0
        while (renderPosition < frames) {
            val eventIndex = nextEventIndex
            if (eventIndex >= timeline.eventCount || timeline.sampleTime[eventIndex] >= chunkEnd) {
                renderDriverSegment(
                    synthesizer, buffer, startFrame + renderPosition, frames - renderPosition,
                    channelCount, outputStage
                )
                break
            }
            val eventOffset = (timeline.sampleTime[eventIndex] - currentSampleOffset).toInt()
            if (eventOffset > renderPosition) {
                renderDriverSegment(
                    synthesizer, buffer, startFrame + renderPosition, eventOffset - renderPosition,
                    channelCount, outputStage
                )
                renderPosition = eventOffset
            }
            handleTimelineEvent(synthesizer, eventIndex)
            nextEventIndex++
        }
    }

    private fun renderDriverSegment(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        channelCount: Int,
        outputStage: Int
    ) {
        preparePerformanceFrames(synthesizer, frames)
        synthesizer.renderTimelineSegment(
            buffer,
            startFrame,
            frames,
            stereo = channelCount == CHANNELS_STEREO,
            rawCore = outputStage == STAGE_RAW_CORE,
            fmRenderBindings = fmRenderBindingByVoice,
            ssgRenderBindings = ssgRenderBindingByChannel,
            ssgDisableAfterFrame = ssgDisableAfterFrame
        )
    }

    private fun preparePerformanceFrames(synthesizer: OpnaLikeSynthesizer, frames: Int) {
        var channel = 0
        while (channel < AudioLaws.SSG_CHANNELS) {
            performance.setSsgBaseLevel(channel, synthesizer.ssg[channel].fixedLevelSnapshot())
            ssgDisableAfterFrame[channel] = DISABLE_FRAME_NONE
            channel++
        }
        performance.prepare(frames)
        channel = 0
        while (channel < AudioLaws.SSG_CHANNELS) {
            if (ssgReleasePending[channel]) {
                val frame = ssgPerformanceFrameByChannel[channel]
                var sample = 0
                while (frame != null && sample < frames && !frame.releaseFinished[sample]) sample++
                if (sample < frames) {
                    ssgDisableAfterFrame[channel] = sample
                    ssgReleasePending[channel] = false
                }
            }
            channel++
        }
        resolveRenderBindings(synthesizer)
    }

    private fun handleTimelineEvent(synthesizer: OpnaLikeSynthesizer, index: Int) {
        when (timeline.eventType[index]) {
            CompiledOpnaTimeline.TEMPO -> {
                val bpmMilli = timeline.controlValues[index * CompiledOpnaTimeline.CONTROL_STRIDE]
                performance.setTempo(bpmMilli, timeline.pmdClocksPerQuarter)
            }
            CompiledOpnaTimeline.SSG_ENVELOPE_DEFINE -> {
                val channel = timeline.channel[index]
                if (channel in 0 until AudioLaws.SSG_CHANNELS) {
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
                if (channel in 0 until AudioLaws.SSG_CHANNELS) {
                    performance.setSsgEnvelopeClockMode(channel, timeline.controlValue(index))
                }
            }
            CompiledOpnaTimeline.SSG_TONE_ENABLE ->
                synthesizer.setTimelineSsgToneEnabled(timeline.channel[index], timeline.controlValue(index) != 0)
            CompiledOpnaTimeline.SSG_NOISE_ENABLE ->
                synthesizer.setTimelineSsgNoiseEnabled(timeline.channel[index], timeline.controlValue(index) != 0)
            CompiledOpnaTimeline.SSG_NOISE_PERIOD ->
                synthesizer.setTimelineSsgNoisePeriod(timeline.channel[index], timeline.controlValue(index))
            CompiledOpnaTimeline.SSG_HARDWARE_ENVELOPE_PERIOD ->
                synthesizer.setTimelineSsgEnvelopePeriod(timeline.channel[index], timeline.controlValue(index))
            CompiledOpnaTimeline.SSG_HARDWARE_ENVELOPE_SHAPE ->
                synthesizer.setTimelineSsgEnvelopeShape(timeline.channel[index], timeline.controlValue(index))
            CompiledOpnaTimeline.HW_LFO_ENABLE -> synthesizer.lfo.enabled = timeline.controlValue(index) != 0
            CompiledOpnaTimeline.HW_LFO_RATE -> synthesizer.lfo.rate = timeline.controlValue(index)
            CompiledOpnaTimeline.HW_LFO_PMS ->
                setHardwareLfoPms(synthesizer, timeline.channel[index], timeline.controlValue(index))
            CompiledOpnaTimeline.HW_LFO_AMS ->
                setHardwareLfoAms(synthesizer, timeline.channel[index], timeline.controlValue(index))
            CompiledOpnaTimeline.HW_LFO_DELAY -> {
                val base = index * CompiledOpnaTimeline.CONTROL_STRIDE
                performance.setHardwareLfoDelay(
                    timeline.channel[index],
                    timeline.controlValues[base],
                    timeline.controlValues[base + 1],
                    timeline.controlValues[base + 2] != 0
                )
            }
            in CompiledOpnaTimeline.SOFTWARE_LFO_DEFINE..CompiledOpnaTimeline.SOFTWARE_LFO_DEPTH ->
                handleSoftwareLfoControl(index)
            in CompiledOpnaTimeline.FM_SLOT_DETUNE_ABSOLUTE..CompiledOpnaTimeline.FM_SLOT_KEY_ON_DELAY ->
                handleFmSlotControl(synthesizer, index)
            CompiledOpnaTimeline.FM3_PATCH -> {
                val patch = timeline.instrumentBank.fmPatch(timeline.patchId[index])
                if (patch != null) {
                    val mask = timeline.slotMask[index] and SLOT_MASK_ALL
                    if (timeline.logicalPart[index] == CompiledOpnaSong.LOGICAL_PART_NONE && mask == SLOT_MASK_ALL) {
                        synthesizer.fm[FM3_PHYSICAL_VOICE].applyPatch(patch)
                    } else {
                        synthesizer.fm[FM3_PHYSICAL_VOICE].applyPatchToSlots(patch, mask)
                    }
                }
            }
            CompiledOpnaTimeline.FM_PART_VOLUME -> performance.setVolume(
                fm3PartIndex(timeline.logicalPart[index]), timeline.controlValue(index)
            )
            CompiledOpnaTimeline.FM_PART_SLOT_MASK -> {
                val part = fm3PartIndex(timeline.logicalPart[index])
                val mask = timeline.controlValue(index)
                performance.setSlotMask(part, mask)
                bindFm3PerformanceFrames(part, mask)
            }
            in CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT..CompiledOpnaTimeline.RHYTHM_VOICE_PAN ->
                handleRhythmControl(
                    synthesizer,
                    timeline.eventType[index],
                    timeline.channel[index],
                    timeline.slotMask[index],
                    timeline.controlValue(index)
                )
            CompiledOpnaTimeline.FM_ON -> noteOnFm(synthesizer, index)
            CompiledOpnaTimeline.FM_OFF -> noteOffFm(synthesizer, index)
            CompiledOpnaTimeline.FM_POLY_ON -> noteOnFmPoly(synthesizer, index)
            CompiledOpnaTimeline.FM_POLY_OFF -> noteOffFmPoly(synthesizer, index)
            CompiledOpnaTimeline.SSG_ON -> noteOnSsg(synthesizer, index)
            CompiledOpnaTimeline.SSG_OFF -> noteOffSsg(synthesizer, index)
            CompiledOpnaTimeline.DRUM_SHOT -> synthesizer.triggerDrum(
                timeline.midi[index], timeline.velocity[index], timeline.pan[index]
            )
            CompiledOpnaTimeline.SSG_DRUM_SHOT ->
                synthesizer.triggerPmdSsgEffect(timeline.midi[index], timeline.velocity[index])
            CompiledOpnaTimeline.FM3_OPERATOR_ON -> noteOnFm3(synthesizer, index)
            CompiledOpnaTimeline.FM3_OPERATOR_OFF -> noteOffFm3(synthesizer, index)
        }
    }

    private fun noteOnFm(synthesizer: OpnaLikeSynthesizer, index: Int) {
        val channel = timeline.channel[index]
        if (channel !in synthesizer.fm.indices) return
        val voice = synthesizer.fm[channel]
        val patch = timeline.instrumentBank.fmPatch(timeline.patchId[index])
        if (patch != null) voice.applyPatch(patch)
        synthesizer.setFmHardwareLfoPms(channel, performance.hardwareLfoPms(channel))
        synthesizer.setFmHardwareLfoAms(channel, performance.hardwareLfoAms(channel))
        voice.setNoteControls(
            timeline.pan[index],
            timeline.detuneCents[index],
            performance.hardwareLfoDelayFrames(channel),
            timeline.targetMidi[index],
            timeline.slideFrames[index]
        )
        if (channel < AudioLaws.FM_CHANNELS) {
            bindFmVoiceToPart(channel, channel)
            performance.noteOnFm(channel)
        }
        if (channel == FM3_PHYSICAL_VOICE) fm3UsesLogicalParts = false
        voice.noteOnScheduled(timeline.midi[index], -1f, -1f, -1f, -1f)
        voice.noteGain = timeline.velocity[index]
        fmActiveNoteId[channel] = timeline.noteId[index]
    }

    private fun noteOffFm(synthesizer: OpnaLikeSynthesizer, index: Int) {
        val channel = timeline.channel[index]
        if (channel in synthesizer.fm.indices && fmActiveNoteId[channel] == timeline.noteId[index]) {
            synthesizer.fm[channel].noteOff()
            fmActiveNoteId[channel] = FM_VOICE_FREE
        }
    }

    private fun noteOnFmPoly(synthesizer: OpnaLikeSynthesizer, index: Int) {
        val part = timeline.channel[index]
        val voiceIndex = availablePolyVoice(synthesizer, part)
        if (voiceIndex < 0) return
        val voice = synthesizer.fm[voiceIndex]
        val patch = timeline.instrumentBank.fmPatch(timeline.patchId[index])
        if (patch != null) voice.applyPatch(patch)
        synthesizer.setFmHardwareLfoPms(voiceIndex, performance.hardwareLfoPms(part))
        synthesizer.setFmHardwareLfoAms(voiceIndex, performance.hardwareLfoAms(part))
        voice.setNoteControls(
            timeline.pan[index],
            timeline.detuneCents[index],
            performance.hardwareLfoDelayFrames(part),
            CompiledOpnaSong.LOGICAL_PART_NONE,
            0
        )
        bindFmVoiceToPart(voiceIndex, part)
        performance.noteOnFm(part)
        if (voiceIndex == FM3_PHYSICAL_VOICE) fm3UsesLogicalParts = false
        voice.noteOnScheduled(timeline.midi[index], -1f, -1f, -1f, -1f)
        voice.noteGain = timeline.velocity[index]
        fmActiveNoteId[voiceIndex] = timeline.noteId[index]
    }

    private fun noteOffFmPoly(synthesizer: OpnaLikeSynthesizer, index: Int) {
        var voiceIndex = 0
        while (voiceIndex < fmActiveNoteId.size) {
            if (fmActiveNoteId[voiceIndex] == timeline.noteId[index]) {
                synthesizer.fm[voiceIndex].noteOff()
                fmActiveNoteId[voiceIndex] = FM_VOICE_RELEASING
                break
            }
            voiceIndex++
        }
    }

    private fun noteOnSsg(synthesizer: OpnaLikeSynthesizer, index: Int) {
        val channel = timeline.channel[index]
        if (channel !in synthesizer.ssg.indices) return
        val voice = synthesizer.ssg[channel]
        val patch = timeline.instrumentBank.ssgPatch(timeline.patchId[index])
        if (patch != null) voice.applyPatch(patch)
        voice.setPan(timeline.pan[index])
        val frequency = OpnPitch.applyCents(midiToFreq(timeline.midi[index]), timeline.detuneCents[index])
        voice.setPitchRamp(
            if (timeline.targetMidi[index] >= 0) {
                OpnPitch.applyCents(midiToFreq(timeline.targetMidi[index]), timeline.detuneCents[index])
            } else {
                0f
            },
            timeline.slideFrames[index]
        )
        performance.setSsgBaseLevel(channel, voice.fixedLevelSnapshot())
        performance.noteOnSsg(channel)
        ssgReleasePending[channel] = false
        voice.noteOn(frequency)
        voice.noteGain = timeline.velocity[index]
        ssgActiveNoteId[channel] = timeline.noteId[index]
    }

    private fun noteOffSsg(synthesizer: OpnaLikeSynthesizer, index: Int) {
        val channel = timeline.channel[index]
        if (channel !in synthesizer.ssg.indices || ssgActiveNoteId[channel] != timeline.noteId[index]) return
        val releaseContinues = performance.noteOffSsg(channel)
        if (releaseContinues) {
            ssgReleasePending[channel] = true
        } else {
            synthesizer.ssg[channel].noteOff()
        }
        ssgActiveNoteId[channel] = NOTE_ID_NONE
    }

    private fun noteOnFm3(synthesizer: OpnaLikeSynthesizer, index: Int) {
        val mask = timeline.slotMask[index] and SLOT_MASK_ALL
        val part = fm3PartIndex(timeline.logicalPart[index])
        performance.setSlotMask(part, mask)
        bindFm3PerformanceFrames(part, mask)
        performance.noteOnFm3(part)
        fm3UsesLogicalParts = true
        val voice = synthesizer.fm[FM3_PHYSICAL_VOICE]
        synthesizer.setFmHardwareLfoPms(
            FM3_PHYSICAL_VOICE,
            performance.hardwareLfoPms(FM3_PHYSICAL_VOICE)
        )
        synthesizer.setFmHardwareLfoAms(
            FM3_PHYSICAL_VOICE,
            performance.hardwareLfoAms(FM3_PHYSICAL_VOICE)
        )
        voice.setNoteControls(
            timeline.pan[index],
            timeline.detuneCents[index],
            performance.hardwareLfoDelayFrames(FM3_PHYSICAL_VOICE),
            CompiledOpnaSong.LOGICAL_PART_NONE,
            0
        )
        voice.noteOnSlots(
            mask,
            timeline.midi[index],
            timeline.targetMidi[index],
            timeline.slideFrames[index],
            timeline.detuneCents[index]
        )
        voice.noteGain = 1f
        var operator = 0
        while (operator < AudioLaws.FM_OPERATORS) {
            if ((mask and (1 shl operator)) != 0) fm3ActiveNoteId[operator] = timeline.noteId[index]
            operator++
        }
    }

    private fun noteOffFm3(synthesizer: OpnaLikeSynthesizer, index: Int) {
        val mask = timeline.slotMask[index] and SLOT_MASK_ALL
        var releaseMask = 0
        var operator = 0
        while (operator < AudioLaws.FM_OPERATORS) {
            if ((mask and (1 shl operator)) != 0 && fm3ActiveNoteId[operator] == timeline.noteId[index]) {
                releaseMask = releaseMask or (1 shl operator)
                fm3ActiveNoteId[operator] = NOTE_ID_NONE
            }
            operator++
        }
        synthesizer.fm[FM3_PHYSICAL_VOICE].noteOffSlots(releaseMask)
    }

    private fun setHardwareLfoPms(synthesizer: OpnaLikeSynthesizer, part: Int, value: Int) {
        performance.setHardwareLfoPms(part, value)
        var voice = 0
        while (voice < fmLogicalPartByVoice.size) {
            if (fmLogicalPartByVoice[voice] == part) synthesizer.setFmHardwareLfoPms(voice, value)
            voice++
        }
    }

    private fun setHardwareLfoAms(synthesizer: OpnaLikeSynthesizer, part: Int, value: Int) {
        performance.setHardwareLfoAms(part, value)
        var voice = 0
        while (voice < fmLogicalPartByVoice.size) {
            if (fmLogicalPartByVoice[voice] == part) synthesizer.setFmHardwareLfoAms(voice, value)
            voice++
        }
    }

    private fun handleSoftwareLfoControl(eventIndex: Int) {
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
        } else if (family == 1 && channel in 0 until AudioLaws.SSG_CHANNELS) {
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

    private fun handleFmSlotControl(synthesizer: OpnaLikeSynthesizer, eventIndex: Int) {
        val channel = timeline.channel[eventIndex]
        if (channel !in synthesizer.fm.indices) return
        val voice = synthesizer.fm[channel]
        val mask = timeline.slotMask[eventIndex] and SLOT_MASK_ALL
        val value = timeline.controlValue(eventIndex)
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

    private fun handleRhythmControl(
        synthesizer: OpnaLikeSynthesizer,
        type: Int,
        voice: Int,
        mask: Int,
        value: Int
    ) {
        when (type) {
            CompiledOpnaTimeline.RHYTHM_CONTROL_SHOT -> synthesizer.rhythmShot(mask)
            CompiledOpnaTimeline.RHYTHM_CONTROL_DUMP -> synthesizer.rhythmDump(mask)
            CompiledOpnaTimeline.RHYTHM_MASTER_ABSOLUTE -> synthesizer.setRhythmMasterLevel(value, false)
            CompiledOpnaTimeline.RHYTHM_MASTER_RELATIVE -> synthesizer.setRhythmMasterLevel(value, true)
            CompiledOpnaTimeline.RHYTHM_VOICE_LEVEL_ABSOLUTE -> synthesizer.setRhythmVoiceLevel(voice, value, false)
            CompiledOpnaTimeline.RHYTHM_VOICE_LEVEL_RELATIVE -> synthesizer.setRhythmVoiceLevel(voice, value, true)
            CompiledOpnaTimeline.RHYTHM_VOICE_PAN -> synthesizer.setRhythmVoicePan(voice, value)
        }
    }

    private fun availablePolyVoice(synthesizer: OpnaLikeSynthesizer, preferredChannel: Int): Int {
        reclaimFinishedPolyVoices(synthesizer)
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

    private fun reclaimFinishedPolyVoices(synthesizer: OpnaLikeSynthesizer) {
        var voiceIndex = 0
        while (voiceIndex < fmActiveNoteId.size) {
            if (fmActiveNoteId[voiceIndex] == FM_VOICE_RELEASING && synthesizer.fm[voiceIndex].releaseFinished()) {
                fmActiveNoteId[voiceIndex] = FM_VOICE_FREE
                bindFmVoiceToPart(
                    voiceIndex,
                    if (voiceIndex < AudioLaws.FM_CHANNELS) voiceIndex else CompiledOpnaSong.LOGICAL_PART_NONE
                )
            }
            voiceIndex++
        }
    }

    private fun bindFmVoiceToPart(voice: Int, part: Int) {
        fmLogicalPartByVoice[voice] = part
    }

    private fun bindFm3PerformanceFrames(part: Int, mask: Int) {
        val frame = performance.fm3Frame(part) ?: return
        var operator = 0
        while (operator < fm3PerformanceFrameByOperator.size) {
            if ((mask and (1 shl operator)) != 0) fm3PerformanceFrameByOperator[operator] = frame
            operator++
        }
    }

    private fun resolveRenderBindings(synthesizer: OpnaLikeSynthesizer) {
        resolveFmRenderBindings(synthesizer)
        var channel = 0
        while (channel < ssgRenderBindingByChannel.size) {
            val source = ssgPerformanceFrameByChannel[channel]
            if (source == null) {
                ssgRenderBindingByChannel[channel] = null
            } else {
                val binding = ssgRenderBindingStorage[channel]
                binding.bind(source.tonePeriodOffset, source.volumeOffset, source.softwareEnvelopeLevel)
                ssgRenderBindingByChannel[channel] = binding
            }
            channel++
        }
    }

    private fun resolveFmRenderBindings(synthesizer: OpnaLikeSynthesizer) {
        var voice = 0
        while (voice < fmRenderBindingByVoice.size) {
            val binding = fmRenderBindingStorage[voice]
            binding.reset()
            val algorithm = synthesizer.fm[voice].algorithmSnapshot()
            if (voice == FM3_PHYSICAL_VOICE && fm3UsesLogicalParts) {
                resolveFm3Sources(binding, algorithm)
            } else {
                bindFmSource(
                    binding,
                    performance.fmFrame(fmLogicalPartByVoice[voice]),
                    SLOT_MASK_ALL,
                    algorithm
                )
            }
            fmRenderBindingByVoice[voice] = if (binding.finish()) binding else null
            voice++
        }
    }

    private fun resolveFm3Sources(binding: FmRenderBinding, algorithm: Int) {
        var groupCount = 0
        var operator = 0
        while (operator < AudioLaws.FM_OPERATORS) {
            val source = fm3PerformanceFrameByOperator[operator]
            if (source != null) {
                var group = 0
                while (group < groupCount && fm3GroupedSource[group] !== source) group++
                if (group == groupCount) {
                    fm3GroupedSource[group] = source
                    fm3GroupedOperatorMask[group] = 0
                    groupCount++
                }
                fm3GroupedOperatorMask[group] = fm3GroupedOperatorMask[group] or (1 shl operator)
            }
            operator++
        }
        var group = 0
        while (group < groupCount) {
            bindFmSource(binding, fm3GroupedSource[group], fm3GroupedOperatorMask[group], algorithm)
            fm3GroupedSource[group] = null
            fm3GroupedOperatorMask[group] = 0
            group++
        }
    }

    private fun bindFmSource(
        binding: FmRenderBinding,
        source: PmdFmFrame?,
        physicalOperatorMask: Int,
        algorithm: Int
    ) {
        if (source == null || physicalOperatorMask == 0) return
        binding.setBaseAttenuation(physicalOperatorMask, source.baseAttenuation)
        val carrierMask = fmCarrierMask(algorithm)
        val pitchMask1 = physicalOperatorMask and
            (if (source.tlMask1 == 0) SLOT_MASK_ALL else source.tlMask1)
        val pitchMask2 = physicalOperatorMask and
            (if (source.tlMask2 == 0) SLOT_MASK_ALL else source.tlMask2)
        if (source.pitchTarget1) binding.addPitchStream(source.pitch1Q20, pitchMask1)
        if (source.pitchTarget2) binding.addPitchStream(source.pitch2Q20, pitchMask2)
        val attenuationMask1 = physicalOperatorMask and
            (if (source.tlMask1 == 0) carrierMask else source.tlMask1)
        val attenuationMask2 = physicalOperatorMask and
            (if (source.tlMask2 == 0) carrierMask else source.tlMask2)
        if (source.volumeTarget1) binding.addAttenuationStream(source.attenuation1, attenuationMask1)
        if (source.volumeTarget2) binding.addAttenuationStream(source.attenuation2, attenuationMask2)
    }

    private fun fmCarrierMask(algorithm: Int): Int = when (algorithm) {
        0, 1, 2, 3 -> 1 shl 3
        4 -> (1 shl 1) or (1 shl 3)
        5, 6 -> (1 shl 1) or (1 shl 2) or (1 shl 3)
        7 -> SLOT_MASK_ALL
        else -> 0
    }

    private fun resetDriverOwnership() {
        performance.reset()
        var voice = 0
        while (voice < fmActiveNoteId.size) {
            fmActiveNoteId[voice] = FM_VOICE_FREE
            fmRenderBindingStorage[voice].reset()
            fmRenderBindingByVoice[voice] = null
            bindFmVoiceToPart(
                voice,
                if (voice < AudioLaws.FM_CHANNELS) voice else CompiledOpnaSong.LOGICAL_PART_NONE
            )
            voice++
        }
        var channel = 0
        while (channel < ssgActiveNoteId.size) {
            ssgActiveNoteId[channel] = NOTE_ID_NONE
            ssgPerformanceFrameByChannel[channel] = performance.ssgFrame(channel)
            ssgRenderBindingByChannel[channel] = null
            ssgReleasePending[channel] = false
            ssgDisableAfterFrame[channel] = DISABLE_FRAME_NONE
            channel++
        }
        var operator = 0
        while (operator < fm3ActiveNoteId.size) {
            fm3ActiveNoteId[operator] = NOTE_ID_NONE
            fm3PerformanceFrameByOperator[operator] = performance.fm3Frame(operator)
            fm3GroupedSource[operator] = null
            fm3GroupedOperatorMask[operator] = 0
            operator++
        }
        fm3UsesLogicalParts = false
    }

    private fun fm3PartIndex(logicalPart: Int): Int {
        val part = logicalPart - CompiledOpnaSong.FM3_PART_BASE
        return if (part in 0 until CompiledOpnaSong.FM3_PART_COUNT) part else -1
    }

    private fun CompiledOpnaTimeline.controlValue(eventIndex: Int): Int =
        controlValues[eventIndex * CompiledOpnaTimeline.CONTROL_STRIDE]

    internal companion object {
        const val CHANNELS_MONO = 1
        const val CHANNELS_STEREO = 2
        const val STAGE_RAW_CORE = 0
        const val STAGE_PROFILED_PRE_MASTER = 1
        private const val FM_VOICE_FREE = -1
        private const val FM_VOICE_RELEASING = -2
        private const val NOTE_ID_NONE = -1
        private const val DISABLE_FRAME_NONE = -1
        private const val FM3_PHYSICAL_VOICE = 2
        private const val SLOT_MASK_ALL = 15
    }
}
