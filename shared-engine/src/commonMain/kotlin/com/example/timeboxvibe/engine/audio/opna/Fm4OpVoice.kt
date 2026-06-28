package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import com.example.timeboxvibe.engine.core.FastMath

class Fm4OpVoice {
    private val opState: Array<OperatorState> = Array(AudioLaws.FM_OPERATORS) { OperatorState() }
    private var patch: FmPatch? = null
    private var baseFrequency: Float = 0f
    private var op0SelfMod: Float = 0f
    var noteGain: Float = 1f

    fun applyPatch(p: FmPatch) {
        patch = p
        setupOpState(0, p.op0)
        setupOpState(1, p.op1)
        setupOpState(2, p.op2)
        setupOpState(3, p.op3)
        if (baseFrequency > 0f) {
            recalcPhaseSteps(p)
        }
    }

    private fun setupOpState(opIdx: Int, spec: OperatorSpec) {
        val state = opState[opIdx]
        state.outputLevel = AudioLaws.tlToAmplitude(spec.tl)
        state.envelope.attack = spec.attack
        state.envelope.decay = spec.decay
        state.envelope.sustain = spec.sustain
        state.envelope.release = spec.release
    }

    private fun recalcPhaseSteps(p: FmPatch) {
        opState[0].phaseStep = calcPhaseStep(p.op0)
        opState[1].phaseStep = calcPhaseStep(p.op1)
        opState[2].phaseStep = calcPhaseStep(p.op2)
        opState[3].phaseStep = calcPhaseStep(p.op3)
    }

    private fun calcPhaseStep(spec: OperatorSpec): Float {
        val mul = if (spec.mul == 0) 0.5f else spec.mul.toFloat()
        val freq = baseFrequency * mul
        val radiansPerSample = freq * 2f * kotlin.math.PI.toFloat() / AudioLaws.SAMPLE_RATE
        return radiansPerSample * AudioLaws.detunePhaseMultiplier(spec.detune)
    }

    fun noteOn(midi: Int) {
        noteOn(midi, null, null, null, null)
    }

    fun noteOn(
        midi: Int,
        attack: Float?,
        decay: Float?,
        sustain: Float?,
        release: Float?
    ) {
        baseFrequency = midiToFreq(midi)
        val p = patch
        if (p != null) {
            recalcPhaseSteps(p)
            val a = attack ?: p.op3.attack
            val d = decay ?: p.op3.decay
            val s = sustain ?: p.op3.sustain
            val r = release ?: p.op3.release
            for (i in 0 until AudioLaws.FM_OPERATORS) {
                opState[i].envelope.attack = a
                opState[i].envelope.decay = d
                opState[i].envelope.sustain = s
                opState[i].envelope.release = r
            }
        }
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].phase = 0f
            opState[i].envelope.noteOn()
            i++
        }
        op0SelfMod = 0f
    }

    fun noteOff() {
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].envelope.noteOff()
            i++
        }
    }

    fun reset() {
        baseFrequency = 0f
        op0SelfMod = 0f
        noteGain = 1f
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].reset()
            i++
        }
        patch = null
    }

    fun render(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float) {
        val p = patch ?: return

        val isAlg0 = p.algorithm == 0
        val carriersOff = if (isAlg0) {
            opState[3].envelope.stage == Envelope.OFF
        } else {
            opState[2].envelope.stage == Envelope.OFF && opState[3].envelope.stage == Envelope.OFF
        }
        if (carriersOff) return

        val combinedGain = gainScale * noteGain
        var i = 0
        while (i < frames) {
            val sample = renderOne(sampleRate)
            buffer[i] += sample * combinedGain
            i++
        }
    }

    private fun renderOne(sampleRate: Int): Float {
        val p = patch ?: return 0f
        val ops = opState
        val dt = 1f / sampleRate

        val fbShift = AudioLaws.feedbackShift(p.feedback)

        if (p.algorithm == 0) {
            val op0Phase = ops[0].phase + op0SelfMod * fbShift
            ops[0].phase += ops[0].phaseStep
            val s0 = FastMath.fastSin(phaseToIdx(op0Phase)) *
                     ops[0].envelope.next(dt) *
                     ops[0].outputLevel
            op0SelfMod = s0

            val op1Phase = ops[1].phase + s0 * p.op1.modulationIndex
            ops[1].phase += ops[1].phaseStep
            val s1 = FastMath.fastSin(phaseToIdx(op1Phase)) *
                     ops[1].envelope.next(dt) *
                     ops[1].outputLevel

            val op2Phase = ops[2].phase + s1 * p.op2.modulationIndex
            ops[2].phase += ops[2].phaseStep
            val s2 = FastMath.fastSin(phaseToIdx(op2Phase)) *
                     ops[2].envelope.next(dt) *
                     ops[2].outputLevel

            val op3Phase = ops[3].phase + s2 * p.op3.modulationIndex
            ops[3].phase += ops[3].phaseStep
            val s3 = FastMath.fastSin(phaseToIdx(op3Phase)) *
                     ops[3].envelope.next(dt) *
                     ops[3].outputLevel *
                     p.totalLevel

            return s3
        } else {
            val op0Phase = ops[0].phase + op0SelfMod * fbShift
            ops[0].phase += ops[0].phaseStep
            val s0 = FastMath.fastSin(phaseToIdx(op0Phase)) *
                     ops[0].envelope.next(dt) *
                     ops[0].outputLevel
            op0SelfMod = s0

            val op1Phase = ops[1].phase
            ops[1].phase += ops[1].phaseStep
            val s1 = FastMath.fastSin(phaseToIdx(op1Phase)) *
                     ops[1].envelope.next(dt) *
                     ops[1].outputLevel

            val op2Phase = ops[2].phase + s0 * p.op2.modulationIndex
            ops[2].phase += ops[2].phaseStep
            val s2 = FastMath.fastSin(phaseToIdx(op2Phase)) *
                     ops[2].envelope.next(dt) *
                     ops[2].outputLevel

            val op3Phase = ops[3].phase + s1 * p.op3.modulationIndex
            ops[3].phase += ops[3].phaseStep
            val s3 = FastMath.fastSin(phaseToIdx(op3Phase)) *
                     ops[3].envelope.next(dt) *
                     ops[3].outputLevel

            return (s2 + s3) * p.totalLevel
        }
    }

    private fun phaseToIdx(phase: Float): Int {
        return (phase * 162.97466f).toInt() and 1023
    }
}
