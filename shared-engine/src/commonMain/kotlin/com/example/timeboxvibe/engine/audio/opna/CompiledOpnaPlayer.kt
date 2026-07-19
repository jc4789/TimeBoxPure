package com.example.timeboxvibe.engine.audio.opna

/** Allocation-free cursor over one immutable [CompiledOpnaTimeline]. */
class CompiledOpnaPlayer internal constructor(
    internal val timeline: CompiledOpnaTimeline
) {
    private var renderedSampleOffset: Long = 0L
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
        activeSynthesizer = synthesizer
        synthesizer.reset()
    }

    /** Sequential product/offline cursor. Seeking belongs to [OfflineOpnaRenderer]. */
    internal fun render(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        channelCount: Int,
        outputStage: Int
    ) {
        if (activeSynthesizer == null) activeSynthesizer = synthesizer
        require(activeSynthesizer === synthesizer) {
            "A compiled cursor belongs to one synthesizer; use an independent offline renderer"
        }
        renderContiguous(
            synthesizer, buffer, startFrame, frames, renderedSampleOffset,
            channelCount, outputStage
        )
        renderedSampleOffset += frames
    }

    internal fun renderAtSequentialOffset(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        currentSampleOffset: Long,
        channelCount: Int,
        outputStage: Int
    ) {
        require(currentSampleOffset >= 0L) { "currentSampleOffset must be non-negative" }
        if (currentSampleOffset == 0L && renderedSampleOffset != 0L) {
            nextEventIndex = 0
            renderedSampleOffset = 0L
            activeSynthesizer = synthesizer
            synthesizer.resetTimelineDomains()
        }
        require(currentSampleOffset == renderedSampleOffset) {
            "Arbitrary interval replay belongs to OfflineOpnaRenderer"
        }
        render(synthesizer, buffer, startFrame, frames, channelCount, outputStage)
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
            if (eventIndex >= timeline.eventCount || timeline.sampleTime(eventIndex) >= chunkEnd) {
                synthesizer.renderTimelineSegment(
                    buffer,
                    startFrame + renderPosition,
                    frames - renderPosition,
                    channelCount,
                    outputStage
                )
                break
            }
            val eventOffset = (timeline.sampleTime(eventIndex) - currentSampleOffset).toInt()
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
