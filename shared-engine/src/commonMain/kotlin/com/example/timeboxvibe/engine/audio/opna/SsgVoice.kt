package com.example.timeboxvibe.engine.audio.opna

class SsgVoice(channelIndex: Int = 0) {
    var enabled: Boolean = false
    var frequency: Float = 0f
    var duty: Float = 0.5f
    var phase01: Float = 0f
    var useNoise: Boolean = false
    var noteGain: Float = 1f

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
        noteGain = 1f
        env.reset()
        noise.reset(0xACE1)
    }

    fun render(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float, startFrame: Int = 0) {
        if (!enabled && env.stage == Envelope.OFF) return

        val dt = 1f / sampleRate
        val step = frequency / sampleRate
        val combinedGain = gainScale * noteGain
        var i = 0
        while (i < frames) {
            val envVal = env.next(dt)
            var signal = if (useNoise) {
                noise.next()
            } else {
                var s = if (phase01 < duty) 1f else -1f
                
                if (phase01 < step) {
                    s += polyBlep(phase01 / step, step)
                } else if (phase01 >= duty && phase01 - step < duty) {
                    s -= polyBlep((phase01 - duty) / step, step)
                }
                s
            }

            buffer[startFrame + i] += signal * envVal * combinedGain

            phase01 += step
            if (phase01 >= 1f) {
                phase01 -= 1f
            }
            i++
        }
    }

    private fun polyBlep(t: Float, dt: Float): Float {
        if (dt <= 0f) return 0f
        return when {
            t < dt -> {
                val t2 = t / dt
                2f * t2 - t2 * t2 - 1f
            }
            t > 1f - dt -> {
                val t2 = (t - 1f) / dt
                t2 * t2 + 2f * t2 + 1f
            }
            else -> 0f
        }
    }
}
