package com.example.timeboxvibe.engine.audio.opna

/** Allocation-free cursor over one immutable [CompiledOpnaTimeline]. */
class CompiledOpnaPlayer internal constructor(
    internal val timeline: CompiledOpnaTimeline
) {
    var nextEventIndex: Int = 0
        private set

    val loopLengthSamples: Long
        get() = timeline.loopLengthSamples

    val eventCount: Int
        get() = timeline.eventCount

    /** Restores every deterministic chip/output state used by a new loop. */
    fun reset(synthesizer: OpnaLikeSynthesizer) {
        nextEventIndex = 0
        synthesizer.reset()
    }

    internal fun renderMono(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        currentSampleOffset: Long
    ) {
        val chunkEnd = currentSampleOffset + frames
        var renderPosition = 0
        skipEventsBefore(currentSampleOffset)
        while (renderPosition < frames) {
            val eventIndex = nextEventIndex
            if (eventIndex >= timeline.eventCount || timeline.sampleTime[eventIndex] >= chunkEnd) {
                synthesizer.renderTimelineMonoSegment(
                    buffer,
                    startFrame + renderPosition,
                    frames - renderPosition
                )
                break
            }
            val eventOffset = (timeline.sampleTime[eventIndex] - currentSampleOffset).toInt()
            if (eventOffset > renderPosition) {
                synthesizer.renderTimelineMonoSegment(
                    buffer,
                    startFrame + renderPosition,
                    eventOffset - renderPosition
                )
                renderPosition = eventOffset
            }
            synthesizer.handleTimelineEvent(timeline, eventIndex)
            nextEventIndex++
        }
    }

    internal fun renderStereo(
        synthesizer: OpnaLikeSynthesizer,
        buffer: FloatArray,
        startFrame: Int,
        frames: Int,
        currentSampleOffset: Long
    ) {
        val chunkEnd = currentSampleOffset + frames
        var renderPosition = 0
        skipEventsBefore(currentSampleOffset)
        while (renderPosition < frames) {
            val eventIndex = nextEventIndex
            if (eventIndex >= timeline.eventCount || timeline.sampleTime[eventIndex] >= chunkEnd) {
                synthesizer.renderTimelineStereoSegment(
                    buffer,
                    startFrame + renderPosition,
                    frames - renderPosition
                )
                break
            }
            val eventOffset = (timeline.sampleTime[eventIndex] - currentSampleOffset).toInt()
            if (eventOffset > renderPosition) {
                synthesizer.renderTimelineStereoSegment(
                    buffer,
                    startFrame + renderPosition,
                    eventOffset - renderPosition
                )
                renderPosition = eventOffset
            }
            synthesizer.handleTimelineEvent(timeline, eventIndex)
            nextEventIndex++
        }
    }

    private fun skipEventsBefore(sampleOffset: Long) {
        while (nextEventIndex < timeline.eventCount &&
            timeline.sampleTime[nextEventIndex] < sampleOffset
        ) {
            nextEventIndex++
        }
    }
}
