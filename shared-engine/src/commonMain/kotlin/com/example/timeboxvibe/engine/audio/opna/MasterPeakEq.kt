package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.EqType
import com.example.timeboxvibe.engine.SongEqBand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal class MasterPeakEq(private val sampleRate: Int) {
    private var bandCount = 0
    private var b0 = FloatArray(0)
    private var b1 = FloatArray(0)
    private var b2 = FloatArray(0)
    private var a1 = FloatArray(0)
    private var a2 = FloatArray(0)
    private var x1L = FloatArray(0)
    private var x2L = FloatArray(0)
    private var y1L = FloatArray(0)
    private var y2L = FloatArray(0)
    private var x1R = FloatArray(0)
    private var x2R = FloatArray(0)
    private var y1R = FloatArray(0)
    private var y2R = FloatArray(0)

    fun configure(bands: List<SongEqBand>) {
        bandCount = bands.size
        b0 = FloatArray(bandCount)
        b1 = FloatArray(bandCount)
        b2 = FloatArray(bandCount)
        a1 = FloatArray(bandCount)
        a2 = FloatArray(bandCount)
        x1L = FloatArray(bandCount)
        x2L = FloatArray(bandCount)
        y1L = FloatArray(bandCount)
        y2L = FloatArray(bandCount)
        x1R = FloatArray(bandCount)
        x2R = FloatArray(bandCount)
        y1R = FloatArray(bandCount)
        y2R = FloatArray(bandCount)

        var i = 0
        while (i < bandCount) {
            val band = bands[i]
            if (band.type == EqType.PEAK) configurePeak(i, band)
            i++
        }
    }

    private fun configurePeak(index: Int, band: SongEqBand) {
        val amplitude = 10.0.pow(band.gainDb.toDouble() / 40.0)
        val omega = 2.0 * PI * band.frequencyHz.toDouble() / sampleRate.toDouble()
        val alpha = sin(omega) / (2.0 * band.q.toDouble())
        val cosOmega = cos(omega)
        val a0 = 1.0 + alpha / amplitude
        b0[index] = ((1.0 + alpha * amplitude) / a0).toFloat()
        b1[index] = ((-2.0 * cosOmega) / a0).toFloat()
        b2[index] = ((1.0 - alpha * amplitude) / a0).toFloat()
        a1[index] = ((-2.0 * cosOmega) / a0).toFloat()
        a2[index] = ((1.0 - alpha / amplitude) / a0).toFloat()
    }

    fun processMono(buffer: FloatArray, frames: Int) {
        if (bandCount == 0) return
        var frame = 0
        while (frame < frames) {
            var sample = buffer[frame]
            var band = 0
            while (band < bandCount) {
                val output = b0[band] * sample + b1[band] * x1L[band] + b2[band] * x2L[band] -
                    a1[band] * y1L[band] - a2[band] * y2L[band]
                x2L[band] = x1L[band]
                x1L[band] = sample
                y2L[band] = y1L[band]
                y1L[band] = output
                sample = output
                band++
            }
            buffer[frame] = sample
            frame++
        }
    }

    fun processStereo(buffer: FloatArray, frames: Int) {
        if (bandCount == 0) return
        var frame = 0
        while (frame < frames) {
            val offset = frame * 2
            buffer[offset] = processLeft(buffer[offset])
            buffer[offset + 1] = processRight(buffer[offset + 1])
            frame++
        }
    }

    private fun processLeft(input: Float): Float {
        var sample = input
        var band = 0
        while (band < bandCount) {
            val output = b0[band] * sample + b1[band] * x1L[band] + b2[band] * x2L[band] -
                a1[band] * y1L[band] - a2[band] * y2L[band]
            x2L[band] = x1L[band]
            x1L[band] = sample
            y2L[band] = y1L[band]
            y1L[band] = output
            sample = output
            band++
        }
        return sample
    }

    private fun processRight(input: Float): Float {
        var sample = input
        var band = 0
        while (band < bandCount) {
            val output = b0[band] * sample + b1[band] * x1R[band] + b2[band] * x2R[band] -
                a1[band] * y1R[band] - a2[band] * y2R[band]
            x2R[band] = x1R[band]
            x1R[band] = sample
            y2R[band] = y1R[band]
            y1R[band] = output
            sample = output
            band++
        }
        return sample
    }

    fun reset() {
        x1L.fill(0f); x2L.fill(0f); y1L.fill(0f); y2L.fill(0f)
        x1R.fill(0f); x2R.fill(0f); y1R.fill(0f); y2R.fill(0f)
    }
}
