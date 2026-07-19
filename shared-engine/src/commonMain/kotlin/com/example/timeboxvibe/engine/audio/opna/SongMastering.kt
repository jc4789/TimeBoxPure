package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws

/** Product-output processing kept separate from chip and driver state. */
internal class SongMastering(sampleRate: Int) {
    private var filterStateL: Float = 0f
    private var filterStateR: Float = 0f
    private val masterEq = MasterPeakEq(sampleRate)
    private val stereoResonator = ProceduralStereoResonator(sampleRate)
    private var chipMixHeadroom: Float = AudioLaws.CHIP_MIX_HEADROOM
    private var outputGain: Float = AudioLaws.OPNA_OUTPUT_GAIN
    private var masterGain: Float = OpnaAudioConstants.MASTER_GAIN
    private var userGain: Float = 1f

    private var filterAlpha: Float = 0.50f
    private var enableOutputFilter: Boolean = true
    private var enableStereoResonator: Boolean = false
    var preClampPeak: Float = 0f
        private set
    var preClampKneeCrossings: Int = 0
        private set

    fun configure(profile: OpnaRenderProfile) {
        filterAlpha = profile.outputFilterAlpha
        enableOutputFilter = profile.enableOutputFilter
        enableStereoResonator = profile.enableStereoResonator
        chipMixHeadroom = profile.chipMixHeadroom
        outputGain = profile.outputGain
        masterGain = profile.masterGain
        userGain = profile.userGain
        masterEq.configure(profile)
    }

    fun reset() {
        filterStateL = 0f
        filterStateR = 0f
        preClampPeak = 0f
        preClampKneeCrossings = 0
        masterEq.reset()
        stereoResonator.reset()
    }

    fun processMono(buffer: FloatArray, frames: Int) {
        masterEq.processMono(buffer, frames)
        applyGainAndClampMono(buffer, frames)
    }

    fun processStereo(buffer: FloatArray, frames: Int) {
        if (enableStereoResonator) stereoResonator.process(buffer, frames)
        masterEq.processStereo(buffer, frames)
        applyGainAndClampStereo(buffer, frames)
    }

    private fun softClip(x: Float): Float {
        val limit = SOFT_CLIP_KNEE
        val excessScale = 1.0f - limit
        return if (x > limit) {
            val diff = x - limit
            limit + excessScale * (diff / (diff + excessScale))
        } else if (x < -limit) {
            val diff = -x - limit
            -(limit + excessScale * (diff / (diff + excessScale)))
        } else {
            x
        }
    }

    private fun applyGainAndClampMono(buffer: FloatArray, frames: Int) {
        val finalGain = outputGain * chipMixHeadroom * masterGain * userGain
        var peak = 0f
        var kneeCrossings = 0
        var i = 0
        while (i < frames) {
            val x = buffer[i] * finalGain
            val filtered = if (enableOutputFilter) {
                val f = (1f - filterAlpha) * x + filterAlpha * filterStateL
                filterStateL = f
                f
            } else {
                x
            }
            val absX = if (filtered < 0f) -filtered else filtered
            if (absX > peak) peak = absX
            if (absX > SOFT_CLIP_KNEE) kneeCrossings++
            buffer[i] = softClip(filtered)
            i++
        }
        if (peak > preClampPeak) preClampPeak = peak
        preClampKneeCrossings += kneeCrossings
    }

    private fun applyGainAndClampStereo(buffer: FloatArray, frames: Int) {
        val finalGain = outputGain * chipMixHeadroom * masterGain * userGain
        val totalSamples = frames * 2
        var peak = 0f
        var kneeCrossings = 0
        var i = 0
        while (i < totalSamples) {
            val x = buffer[i] * finalGain
            val filtered = if (enableOutputFilter) {
                if ((i % 2) == 0) {
                    val f = (1f - filterAlpha) * x + filterAlpha * filterStateL
                    filterStateL = f
                    f
                } else {
                    val f = (1f - filterAlpha) * x + filterAlpha * filterStateR
                    filterStateR = f
                    f
                }
            } else {
                x
            }
            val absX = if (filtered < 0f) -filtered else filtered
            if (absX > peak) peak = absX
            if (absX > SOFT_CLIP_KNEE) kneeCrossings++
            buffer[i] = softClip(filtered)
            i++
        }
        if (peak > preClampPeak) preClampPeak = peak
        preClampKneeCrossings += kneeCrossings
    }

    companion object {
        const val SOFT_CLIP_KNEE = 0.70f
    }
}
