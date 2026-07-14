package com.example.timeboxvibe.engine.audio.opna

internal class OpnaMixer {
    private var selectedProfile = OpnaOutputProfile.TIMEBOX_LEGACY

    val ssgGain: Float get() = selectedProfile.ssgGain
    val fmGain: Float get() = selectedProfile.fmGain
    val rhythmGain: Float get() = selectedProfile.rhythmGain

    fun applyProfile(profile: OpnaOutputProfile) {
        selectedProfile = profile
    }

    fun resetTo(profile: OpnaOutputProfile) {
        selectedProfile = profile
    }

    fun mixRawCoreMono(
        fmBus: FloatArray,
        ssgBus: FloatArray,
        rhythmBus: FloatArray,
        output: FloatArray,
        startFrame: Int,
        frames: Int
    ) {
        mixMono(fmBus, ssgBus, rhythmBus, output, startFrame, frames, 1f, 1f, 1f)
    }

    fun mixProfiledPreMasterMono(
        fmBus: FloatArray,
        ssgBus: FloatArray,
        rhythmBus: FloatArray,
        output: FloatArray,
        startFrame: Int,
        frames: Int
    ) {
        mixMono(fmBus, ssgBus, rhythmBus, output, startFrame, frames, fmGain, ssgGain, rhythmGain)
    }

    fun mixRawCoreStereo(
        fmBus: FloatArray,
        ssgBus: FloatArray,
        rhythmBus: FloatArray,
        output: FloatArray,
        startFrame: Int,
        frames: Int
    ) {
        mixStereo(fmBus, ssgBus, rhythmBus, output, startFrame, frames, 1f, 1f, 1f)
    }

    fun mixProfiledPreMasterStereo(
        fmBus: FloatArray,
        ssgBus: FloatArray,
        rhythmBus: FloatArray,
        output: FloatArray,
        startFrame: Int,
        frames: Int
    ) {
        mixStereo(fmBus, ssgBus, rhythmBus, output, startFrame, frames, fmGain, ssgGain, rhythmGain)
    }

    private fun mixMono(
        fmBus: FloatArray,
        ssgBus: FloatArray,
        rhythmBus: FloatArray,
        output: FloatArray,
        startFrame: Int,
        frames: Int,
        fmBusGain: Float,
        ssgBusGain: Float,
        rhythmBusGain: Float
    ) {
        var frame = 0
        while (frame < frames) {
            output[startFrame + frame] =
                fmBus[frame] * fmBusGain +
                    ssgBus[frame] * ssgBusGain +
                    rhythmBus[frame] * rhythmBusGain
            frame++
        }
    }

    private fun mixStereo(
        fmBus: FloatArray,
        ssgBus: FloatArray,
        rhythmBus: FloatArray,
        output: FloatArray,
        startFrame: Int,
        frames: Int,
        fmBusGain: Float,
        ssgBusGain: Float,
        rhythmBusGain: Float
    ) {
        val samples = frames * 2
        val outputStart = startFrame * 2
        var sample = 0
        while (sample < samples) {
            output[outputStart + sample] =
                fmBus[sample] * fmBusGain +
                    ssgBus[sample] * ssgBusGain +
                    rhythmBus[sample] * rhythmBusGain
            sample++
        }
    }
}
