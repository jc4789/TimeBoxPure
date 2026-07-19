package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.ArrangementLanes

/** One fully configured, strictly sequential product-output playback cursor. */
class OpnaPlaybackSession private constructor(
    private val profile: OpnaRenderProfile,
    private val looping: Boolean,
    internal val synthesizer: OpnaLikeSynthesizer,
    internal val player: CompiledOpnaPlayer
) {
    private val renderBuffer = FloatArray(profile.maximumRenderChunk)
    private var sampleOffset: Long = 0L
    private var stopped: Boolean = false

    var isComplete: Boolean = player.loopLengthSamples <= 0L
        private set

    val sampleRate: Int get() = profile.sampleRate
    val maximumRenderChunk: Int get() = profile.maximumRenderChunk
    val loopLengthSamples: Long get() = player.loopLengthSamples

    /** Renders the next sequential product frames. No caller-owned timeline offset exists. */
    fun render(output: FloatArray, frames: Int) {
        require(frames in 0..output.size) { "Session render frames exceed output buffer" }
        var outputOffset = 0
        while (outputOffset < frames) {
            if (stopped || isComplete) {
                output.fill(0f, outputOffset, frames)
                return
            }
            val untilLoopEnd = player.loopLengthSamples - sampleOffset
            if (untilLoopEnd <= 0L) {
                finishOrLoop()
                continue
            }
            val chunkFrames = minOf(
                (frames - outputOffset).toLong(),
                profile.maximumRenderChunk.toLong(),
                untilLoopEnd
            ).toInt()
            synthesizer.renderProductSequential(renderBuffer, chunkFrames, player)
            renderBuffer.copyInto(output, outputOffset, 0, chunkFrames)
            outputOffset += chunkFrames
            sampleOffset += chunkFrames
            if (sampleOffset == player.loopLengthSamples) finishOrLoop()
        }
    }

    fun reset() {
        sampleOffset = 0L
        stopped = false
        player.reset(synthesizer)
        isComplete = player.loopLengthSamples <= 0L
    }

    fun stop() {
        stopped = true
        isComplete = true
        synthesizer.allNotesOff()
    }

    private fun finishOrLoop() {
        if (looping && player.loopLengthSamples > 0L) {
            sampleOffset = 0L
            player.reset(synthesizer)
        } else {
            isComplete = true
        }
    }

    companion object {
        fun createProduct(
            arrangement: ArrangementLanes,
            userGain: Float,
            looping: Boolean
        ): OpnaPlaybackSession = create(
            arrangement.compiledOpnaSong,
            OpnaRenderProfile.product(arrangement, userGain),
            looping
        )

        fun create(
            program: CompiledOpnaSong,
            profile: OpnaRenderProfile,
            looping: Boolean
        ): OpnaPlaybackSession {
            val synthesizer = OpnaLikeSynthesizer.create(profile)
            val player = profile.createPlayer(program)
            player.reset(synthesizer)
            return OpnaPlaybackSession(profile, looping, synthesizer, player)
        }
    }
}
