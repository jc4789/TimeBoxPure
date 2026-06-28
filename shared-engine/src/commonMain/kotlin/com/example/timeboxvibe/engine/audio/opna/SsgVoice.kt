package com.example.timeboxvibe.engine.audio.opna

internal class SsgVoice(channelIndex: Int = 0) {
    var enabled: Boolean = false
    var frequency: Float = 0f
    var duty: Float = 0.5f
    var phase01: Float = 0f
    var useNoise: Boolean = false

    val env: Envelope = Envelope()
    private val noise: LfsrNoise = LfsrNoise(0xACE1 xor (channelIndex * 0x9E37))

    fun noteOn(freq: Float) {
        frequency = freq
        enabled = true
        env.noteOn()
    }

    fun noteOff() {
        env.noteOff()
    }

    fun reset() {
        enabled = false
        frequency = 0f
        duty = 0.5f
        phase01 = 0f
        useNoise = false
        env.reset()
        noise.reset(0xACE1)
    }

    fun render(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float) {
        if (!enabled && env.stage == Envelope.OFF) return
        
        val dt = 1f / sampleRate
        val step = frequency / sampleRate
        var i = 0
        while (i < frames) {
            val envVal = env.next(dt)
            val signal = if (useNoise) {
                noise.next()
            } else {
                if (phase01 < duty) 1f else -1f
            }
            
            buffer[i] += signal * envVal * gainScale
            
            phase01 += step
            if (phase01 >= 1f) {
                phase01 -= 1f
            }
            i++
        }
    }
}
