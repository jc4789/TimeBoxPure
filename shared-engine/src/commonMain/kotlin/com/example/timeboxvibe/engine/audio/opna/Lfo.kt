package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.core.FastMath

class Lfo(val sampleRate: Int = 48000) {
    companion object {
        private val AMS_DEPTH_TABLE = floatArrayOf(0f, 0.016f, 0.031f, 0.063f)
        private val PMS_DEPTH_TABLE = floatArrayOf(0f, 0.0005f, 0.001f, 0.002f, 0.004f, 0.008f, 0.016f, 0.032f)

        private val LFO_FREQ_TABLE = floatArrayOf(3.98f, 5.56f, 6.02f, 6.37f, 6.88f, 9.63f, 48.1f, 72.2f)
    }

    private var phase: Float = 0f
    private var phaseStep: Float = 0f
    private var triangleValue: Float = 0f

    var rate: Int = 0
        set(value) {
            field = value.coerceIn(0, 7)
            phaseStep = LFO_FREQ_TABLE[field] / sampleRate
        }

    fun update() {
        phase += phaseStep
        if (phase >= 1f) phase -= 1f
        val p4 = phase * 4f
        triangleValue = if (p4 < 1f) {
            p4
        } else if (p4 < 2f) {
            2f - p4
        } else if (p4 < 3f) {
            p4 - 2f
        } else {
            4f - p4
        }
    }

    fun amsModulation(ams: Int): Float {
        if (ams == 0) return 1f
        val depth = AMS_DEPTH_TABLE[ams.coerceIn(0, 3)]
        return 1f - depth * (1f - triangleValue)
    }

    fun pmsModulation(pms: Int): Float {
        if (pms == 0) return 0f
        val depth = PMS_DEPTH_TABLE[pms.coerceIn(0, 7)]
        return depth * triangleValue
    }

    fun reset() {
        phase = 0f
        triangleValue = 0f
    }
}
