package com.example.timeboxvibe.engine.audio.opna

/** Allocation-free cursor over one immutable [CompiledOpnaTimeline]. */
class CompiledOpnaPlayer internal constructor(
    internal val timeline: CompiledOpnaTimeline
) {
    private val seekMonoBuffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    private val seekStereoBuffer = FloatArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK * 2)
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

    /** Restores every deterministic chip/output state used by a new loop. */
    fun reset(synthesizer: OpnaLikeSynthesizer) {
        nextEventIndex = 0
        renderedSampleOffset = 0L
        activeChannelCount = CHANNELS_MONO
        activeOutputStage = STAGE_PROFILED_PRE_MASTER
        activeSynthesizer = synthesizer
        synthesizer.reset()
    }

    /** One cursor and one dispatcher serve raw, profiled, mono, and stereo timeline playback. */
    internal fun render(
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
                synthesizer.renderTimelineSegment(
                    buffer,
                    startFrame + renderPosition,
                    frames - renderPosition,
                    channelCount,
                    outputStage
                )
                break
            }
            val eventOffset = (timeline.sampleTime[eventIndex] - currentSampleOffset).toInt()
            if (eventOffset > renderPosition) {
                synthesizer.renderTimelineSegment(
                    buffer,
                    startFrame + renderPosition,
                    eventOffset - renderPosition,
                    channelCount,
                    outputStage
                )
                renderPosition = eventOffset
            }
            synthesizer.handleTimelineEvent(timeline, eventIndex)
            nextEventIndex++
        }
    }

    internal companion object {
        const val CHANNELS_MONO = 1
        const val CHANNELS_STEREO = 2
        const val STAGE_RAW_CORE = 0
        const val STAGE_PROFILED_PRE_MASTER = 1
    }
}
