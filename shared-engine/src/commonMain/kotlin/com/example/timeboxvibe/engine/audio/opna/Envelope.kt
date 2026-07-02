package com.example.timeboxvibe.engine.audio.opna

class Envelope {
    companion object {
        const val OFF: Int = 0
        const val ATTACK: Int = 1
        const val DECAY: Int = 2
        const val SUSTAIN: Int = 3
        const val RELEASE: Int = 4
    }

    var stage: Int = OFF
    var level: Float = 0f
    var attack: Float = 0.002f    // in seconds
    var decay: Float = 0.06f      // in seconds
    var sustain: Float = 0.55f    // 0..1 level
    var release: Float = 0.04f    // in seconds

    private var releaseStartLevel: Float = 0f

    fun noteOn() {
        stage = ATTACK
        level = 0f
    }

    fun noteOff() {
        stage = RELEASE
        releaseStartLevel = level
    }

    fun reset() {
        stage = OFF
        level = 0f
        releaseStartLevel = 0f
    }

    fun next(dt: Float): Float {
        when (stage) {
            OFF -> {
                level = 0f
            }
            ATTACK -> {
                if (attack <= 0f) {
                    level = 1f
                    stage = DECAY
                } else {
                    level += dt / attack
                    if (level >= 1f) {
                        level = 1f
                        stage = DECAY
                    }
                }
            }
            DECAY -> {
                if (decay <= 0f) {
                    level = sustain
                    stage = SUSTAIN
                } else {
                    level -= (dt / decay) * (level - sustain).coerceAtLeast(0.001f)
                    if (level <= sustain + 0.001f) {
                        level = sustain
                        stage = SUSTAIN
                    }
                }
            }
            SUSTAIN -> {
                level = sustain
            }
            RELEASE -> {
                if (release <= 0f) {
                    level = 0f
                    stage = OFF
                } else {
                    level -= (dt / release) * level.coerceAtLeast(0.001f)
                    if (level <= 0.001f) {
                        level = 0f
                        stage = OFF
                    }
                }
            }
        }
        return level
    }
}
