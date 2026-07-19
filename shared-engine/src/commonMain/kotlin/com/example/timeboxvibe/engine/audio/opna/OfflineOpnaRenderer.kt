package com.example.timeboxvibe.engine.audio.opna

/** Independent inspection renderer that deliberately owns replay/seek scratch state. */
class OfflineOpnaRenderer(
    program: CompiledOpnaSong,
    private val profile: OpnaRenderProfile
) {
    internal val synthesizer = OpnaLikeSynthesizer.create(profile)
    private val player = profile.createPlayer(program)
    private val seekMonoBuffer = FloatArray(profile.maximumRenderChunk)
    private val seekStereoBuffer = FloatArray(profile.maximumRenderChunk * CHANNELS_STEREO)
    private var renderedSampleOffset: Long = 0L
    private var activeMode: Int = MODE_NONE

    init {
        player.reset(synthesizer)
    }

    fun reset() {
        activeMode = MODE_NONE
        renderedSampleOffset = 0L
        player.reset(synthesizer)
    }

    fun renderRawCore(output: FloatArray, frames: Int, sampleOffset: Long) =
        renderInterval(output, frames, sampleOffset, MODE_RAW_MONO)

    fun renderProfiledPreMaster(output: FloatArray, frames: Int, sampleOffset: Long) =
        renderInterval(output, frames, sampleOffset, MODE_PROFILED_MONO)

    fun renderProduct(output: FloatArray, frames: Int, sampleOffset: Long) =
        renderInterval(output, frames, sampleOffset, MODE_PRODUCT_MONO)

    fun renderRawCoreStereo(output: FloatArray, frames: Int, sampleOffset: Long) =
        renderInterval(output, frames, sampleOffset, MODE_RAW_STEREO)

    fun renderProfiledPreMasterStereo(output: FloatArray, frames: Int, sampleOffset: Long) =
        renderInterval(output, frames, sampleOffset, MODE_PROFILED_STEREO)

    fun renderProductStereo(output: FloatArray, frames: Int, sampleOffset: Long) =
        renderInterval(output, frames, sampleOffset, MODE_PRODUCT_STEREO)

    private fun renderInterval(output: FloatArray, frames: Int, sampleOffset: Long, mode: Int) {
        require(frames >= 0 && sampleOffset >= 0L) { "Offline interval must be non-negative" }
        val channels = channels(mode)
        require(frames.toLong() * channels <= output.size.toLong()) {
            "Offline interval exceeds output buffer"
        }
        prepareInterval(mode, sampleOffset)
        renderNext(output, frames, mode)
        renderedSampleOffset += frames
    }

    private fun prepareInterval(mode: Int, sampleOffset: Long) {
        if (activeMode != mode || sampleOffset < renderedSampleOffset) {
            player.reset(synthesizer)
            renderedSampleOffset = 0L
            activeMode = mode
        }
        while (renderedSampleOffset < sampleOffset) {
            val chunkFrames = minOf(
                profile.maximumRenderChunk.toLong(),
                sampleOffset - renderedSampleOffset
            ).toInt()
            val scratch = if (channels(mode) == CHANNELS_STEREO) seekStereoBuffer else seekMonoBuffer
            renderNext(scratch, chunkFrames, mode)
            renderedSampleOffset += chunkFrames
        }
    }

    private fun renderNext(output: FloatArray, frames: Int, mode: Int) {
        when (mode) {
            MODE_RAW_MONO -> synthesizer.renderStageSequential(
                output, frames, player, CompiledOpnaPlayer.CHANNELS_MONO,
                CompiledOpnaPlayer.STAGE_RAW_CORE
            )
            MODE_PROFILED_MONO -> synthesizer.renderStageSequential(
                output, frames, player, CompiledOpnaPlayer.CHANNELS_MONO,
                CompiledOpnaPlayer.STAGE_PROFILED_PRE_MASTER
            )
            MODE_PRODUCT_MONO -> synthesizer.renderProductSequential(output, frames, player)
            MODE_RAW_STEREO -> synthesizer.renderStageSequential(
                output, frames, player, CompiledOpnaPlayer.CHANNELS_STEREO,
                CompiledOpnaPlayer.STAGE_RAW_CORE
            )
            MODE_PROFILED_STEREO -> synthesizer.renderStageSequential(
                output, frames, player, CompiledOpnaPlayer.CHANNELS_STEREO,
                CompiledOpnaPlayer.STAGE_PROFILED_PRE_MASTER
            )
            MODE_PRODUCT_STEREO -> synthesizer.renderProductStereoSequential(output, frames, player)
        }
    }

    private fun channels(mode: Int): Int = if (mode >= MODE_RAW_STEREO) CHANNELS_STEREO else 1

    private companion object {
        const val CHANNELS_STEREO = 2
        const val MODE_NONE = -1
        const val MODE_RAW_MONO = 0
        const val MODE_PROFILED_MONO = 1
        const val MODE_PRODUCT_MONO = 2
        const val MODE_RAW_STEREO = 3
        const val MODE_PROFILED_STEREO = 4
        const val MODE_PRODUCT_STEREO = 5
    }
}
