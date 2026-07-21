package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws

/** Primitive physical FM controls resolved once for one render segment. */
internal class FmRenderBinding {
    val baseAttenuation = IntArray(AudioLaws.FM_OPERATORS)
    val pitchStreams = arrayOfNulls<IntArray>(MAX_STREAMS)
    val pitchMasks = IntArray(MAX_STREAMS)
    val attenuationStreams = arrayOfNulls<IntArray>(MAX_STREAMS)
    val attenuationMasks = IntArray(MAX_STREAMS)
    var pitchStreamCount = 0
        private set
    var attenuationStreamCount = 0
        private set
    var mode = MODE_IDENTITY
        private set

    fun reset() {
        baseAttenuation.fill(0)
        var stream = 0
        while (stream < pitchStreamCount) {
            pitchStreams[stream] = null
            pitchMasks[stream] = 0
            stream++
        }
        stream = 0
        while (stream < attenuationStreamCount) {
            attenuationStreams[stream] = null
            attenuationMasks[stream] = 0
            stream++
        }
        pitchStreamCount = 0
        attenuationStreamCount = 0
        mode = MODE_IDENTITY
    }

    fun setBaseAttenuation(operatorMask: Int, value: Int) {
        var operator = 0
        while (operator < AudioLaws.FM_OPERATORS) {
            if ((operatorMask and (1 shl operator)) != 0) baseAttenuation[operator] = value
            operator++
        }
    }

    fun addPitchStream(stream: IntArray, operatorMask: Int) {
        if (operatorMask == 0 || pitchStreamCount >= MAX_STREAMS) return
        pitchStreams[pitchStreamCount] = stream
        pitchMasks[pitchStreamCount] = operatorMask
        pitchStreamCount++
    }

    fun addAttenuationStream(stream: IntArray, operatorMask: Int) {
        if (operatorMask == 0 || attenuationStreamCount >= MAX_STREAMS) return
        attenuationStreams[attenuationStreamCount] = stream
        attenuationMasks[attenuationStreamCount] = operatorMask
        attenuationStreamCount++
    }

    fun finish(): Boolean {
        if (pitchStreamCount > 0 || attenuationStreamCount > 0) {
            mode = MODE_STREAMED
            return true
        }
        var operator = 0
        while (operator < AudioLaws.FM_OPERATORS) {
            if (baseAttenuation[operator] != 0) {
                mode = MODE_SCALAR
                return true
            }
            operator++
        }
        mode = MODE_IDENTITY
        return false
    }

    companion object {
        const val MODE_IDENTITY = 0
        const val MODE_SCALAR = 1
        const val MODE_STREAMED = 2
        private const val SOFTWARE_STREAMS_PER_SOURCE = 2
        private const val MAX_STREAMS = AudioLaws.FM_OPERATORS * SOFTWARE_STREAMS_PER_SOURCE
    }
}

/** Existing driver-produced arrays exposed only as resolved physical SSG controls. */
internal class SsgRenderBinding {
    lateinit var tonePeriodOffset: IntArray
        private set
    lateinit var volumeOffset: IntArray
        private set
    lateinit var softwareLevel: IntArray
        private set

    fun bind(tonePeriodOffset: IntArray, volumeOffset: IntArray, softwareLevel: IntArray) {
        this.tonePeriodOffset = tonePeriodOffset
        this.volumeOffset = volumeOffset
        this.softwareLevel = softwareLevel
    }
}
