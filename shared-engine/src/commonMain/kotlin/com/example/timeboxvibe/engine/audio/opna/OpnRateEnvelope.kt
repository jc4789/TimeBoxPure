package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.pow

class OpnRateEnvelope {
    companion object {
        const val OFF: Int = 0
        const val ATTACK: Int = 1
        const val DECAY: Int = 2
        const val SUSTAIN: Int = 3
        const val RELEASE: Int = 4

        private const val MAX_RATE = 63

        private val attackStepTable = FloatArray(MAX_RATE + 1)
        private val decayStepTable = FloatArray(MAX_RATE + 1)

        init {
            var i = 0
            while (i <= MAX_RATE) {
                val effectiveRate = i.coerceIn(0, MAX_RATE)
                if (effectiveRate < 4) {
                    attackStepTable[i] = 0f
                    decayStepTable[i] = 0f
                } else if (effectiveRate >= 60) {
                    attackStepTable[i] = 1.0f
                    decayStepTable[i] = 1.0f
                } else {
                    val p = effectiveRate shr 2
                    val q = effectiveRate and 3
                    val pow2 = 2.0.pow(p)
                    val base = (1.0 / 44100.0) * pow2
                    val qf = q.toDouble()
                    val attackInc = (base * (3.0 + 2.0 * qf) / 4.0).toFloat()
                    val decayInc = (base * (1.0 + 0.5 * qf)).toFloat()
                    attackStepTable[i] = attackInc.coerceAtMost(1.0f)
                    decayStepTable[i] = decayInc.coerceAtMost(1.0f)
                }
                i++
            }
        }

        fun keyCode(block: Int, fnum: Int): Int {
            val b = block.coerceIn(0, 7)
            val f = fnum.coerceIn(0, 2047)
            val fHigh = f shr 7
            return (b shl 2) or fHigh
        }
    }

    var stage: Int = OFF
    var level: Float = 0f

    var attackRate: Int = 12
    var decayRate: Int = 10
    var sustainLevel: Int = 10
    var releaseRate: Int = 12

    private var keyScaleValue: Int = 0

    fun setKeyScale(block: Int, fnum: Int) {
        keyScaleValue = keyCode(block, fnum)
    }

    fun noteOn() {
        stage = ATTACK
        level = 0f
    }

    fun noteOff() {
        if (stage != OFF) {
            stage = RELEASE
        }
    }

    fun reset() {
        stage = OFF
        level = 0f
        keyScaleValue = 0
    }

    fun next(): Float {
        when (stage) {
            OFF -> {
                level = 0f
            }
            ATTACK -> {
                val effectiveRate = (attackRate + keyScaleValue).coerceIn(0, MAX_RATE)
                val step = attackStepTable[effectiveRate]
                if (step <= 0f) {
                    if (attackRate + keyScaleValue < 4) return level
                }
                level += step * (1.0f - level)
                if (level >= 0.999f) {
                    level = 1.0f
                    stage = DECAY
                }
            }
            DECAY -> {
                val effectiveRate = (decayRate + keyScaleValue).coerceIn(0, MAX_RATE)
                val step = decayStepTable[effectiveRate]
                val sl = sustainLevel.toFloat() / 15.0f
                level -= step
                if (level <= sl) {
                    level = sl
                    stage = SUSTAIN
                }
            }
            SUSTAIN -> {
            }
            RELEASE -> {
                val effectiveRate = (releaseRate + keyScaleValue).coerceIn(0, MAX_RATE)
                val step = decayStepTable[effectiveRate]
                level -= step
                if (level <= 0f) {
                    level = 0f
                    stage = OFF
                }
            }
        }
        return level
    }
}
