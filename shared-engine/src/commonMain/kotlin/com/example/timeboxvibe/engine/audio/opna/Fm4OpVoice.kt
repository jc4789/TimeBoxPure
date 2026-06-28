package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import com.example.timeboxvibe.engine.core.FastMath

class Fm4OpVoice {
    private val opState: Array<OperatorState> = Array(AudioLaws.FM_OPERATORS) { OperatorState() }
    private var patch: FmPatch? = null
    private var baseFrequency: Float = 0f
    private var op0Feedback: Float = 0f
    var noteGain: Float = 1f

    var channelAms: Int = 0
    var channelPms: Int = 0

    fun getPan(): Int = patch?.pan ?: 0

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
        state.egMode = spec.egMode
        state.envelope.attack = spec.attack
        state.envelope.decay = spec.decay
        state.envelope.sustain = spec.sustain
        state.envelope.release = spec.release
        state.opnEnvelope.attackRate = spec.ar
        state.opnEnvelope.decayRate = spec.dr
        state.opnEnvelope.sustainLevel = spec.sl
        state.opnEnvelope.releaseRate = spec.rr
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
            val hasOverrides = (attack != null || decay != null || sustain != null || release != null)
            if (hasOverrides) {
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
            } else {
                setupOpState(0, p.op0)
                setupOpState(1, p.op1)
                setupOpState(2, p.op2)
                setupOpState(3, p.op3)
            }
        }
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].phase = 0f
            opState[i].prevOutput = 0f
            opState[i].envelope.noteOn()
            opState[i].opnEnvelope.noteOn()
            i++
        }
        op0Feedback = 0f
    }

    fun noteOff() {
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].envelope.noteOff()
            opState[i].opnEnvelope.noteOff()
            i++
        }
    }

    fun reset() {
        baseFrequency = 0f
        op0Feedback = 0f
        noteGain = 1f
        channelAms = 0
        channelPms = 0
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].reset()
            i++
        }
        patch = null
    }

    fun render(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float, startFrame: Int = 0) {
        val p = patch ?: return

        val carriersOff = when (p.algorithm) {
            0 -> isOpOff(3)
            1 -> isOpOff(2) && isOpOff(3)
            2 -> isOpOff(2) && isOpOff(3)
            3 -> isOpOff(1) && isOpOff(3)
            4 -> isOpOff(1) && isOpOff(3)
            5 -> isOpOff(3)
            6 -> isOpOff(1) && isOpOff(2) && isOpOff(3)
            7 -> isOpOff(0) && isOpOff(1) && isOpOff(2) && isOpOff(3)
            else -> true
        }
        if (carriersOff) return

        val combinedGain = gainScale * noteGain
        var i = 0
        while (i < frames) {
            val sample = renderOne()
            buffer[startFrame + i] += sample * combinedGain
            i++
        }
    }

    private fun isOpOff(opIdx: Int): Boolean {
        val op = opState[opIdx]
        val legacyOff = op.envelope.stage == Envelope.OFF
        val opnOff = op.opnEnvelope.stage == OpnRateEnvelope.OFF
        return if (op.egMode == EgMode.OPN_RATE) opnOff else legacyOff
    }

    private fun envNext(op: OperatorState): Float {
        return if (op.egMode == EgMode.OPN_RATE) {
            op.opnEnvelope.next()
        } else {
            op.envelope.next(1f / AudioLaws.SAMPLE_RATE)
        }
    }

    private fun renderOne(): Float {
        val p = patch ?: return 0f
        val ops = opState
        val fbShift = AudioLaws.feedbackShift(p.feedback)

        return when (p.algorithm) {
            0 -> {
                val s0 = computeOp0(ops, fbShift)
                val s1 = advanceOp(1, s0 * p.op0.modulationIndex)
                val s2 = advanceOp(2, s1 * p.op1.modulationIndex)
                val s3 = advanceOp(3, s2 * p.op2.modulationIndex)
                s3 * p.totalLevel
            }
            1 -> {
                val s0 = computeOp0(ops, fbShift)
                val s1 = computeOpFree(1)
                val s2 = advanceOp(2, s0 * p.op0.modulationIndex)
                val s3 = advanceOp(3, s1 * p.op1.modulationIndex)
                (s2 + s3) * p.totalLevel
            }
            2 -> {
                val s0 = computeOp0(ops, fbShift)
                val s1 = advanceOp(1, s0 * p.op0.modulationIndex)
                val s2 = advanceOp(2, s1 * p.op1.modulationIndex)
                val s3 = computeOpFree(3)
                (s2 + s3) * p.totalLevel
            }
            3 -> {
                val s0 = computeOp0(ops, fbShift)
                val s1 = advanceOp(1, s0 * p.op0.modulationIndex)
                val s2 = computeOpFree(2)
                val s3 = advanceOp(3, s2 * p.op2.modulationIndex)
                (s1 + s3) * p.totalLevel
            }
            4 -> {
                val s0 = computeOp0(ops, fbShift)
                val s1 = advanceOp(1, s0 * p.op0.modulationIndex)
                val s2 = computeOpFree(2)
                val s3 = advanceOp(3, s2 * p.op2.modulationIndex)
                (s1 + s3) * p.totalLevel
            }
            5 -> {
                val s0 = computeOp0(ops, fbShift)
                val s1 = advanceOp(1, s0 * p.op0.modulationIndex)
                val s2 = advanceOp(2, s0 * p.op0.modulationIndex)
                val s3 = advanceOp(3, s0 * p.op0.modulationIndex)
                (s1 + s2 + s3) * p.totalLevel
            }
            6 -> {
                val s0 = computeOp0(ops, fbShift)
                val s1 = advanceOp(1, s0 * p.op0.modulationIndex)
                val s2 = computeOpFree(2)
                val s3 = computeOpFree(3)
                (s1 + s2 + s3) * p.totalLevel
            }
            7 -> {
                val s0 = computeOp0(ops, fbShift)
                val s1 = computeOpFree(1)
                val s2 = computeOpFree(2)
                val s3 = computeOpFree(3)
                (s0 + s1 + s2 + s3) * p.totalLevel
            }
            else -> 0f
        }
    }

    private fun sinLutInterpolated(phase: Float): Float {
        val idxFloat = phase * 162.97466f
        val idx = if (idxFloat >= 0f) idxFloat.toInt() else (idxFloat.toInt() - 1)
        val frac = idxFloat - idx
        val idx0 = idx and 1023
        val idx1 = (idx0 + 1) and 1023
        val y0 = FastMath.fastSin(idx0)
        val y1 = FastMath.fastSin(idx1)
        return y0 + frac * (y1 - y0)
    }

    private fun computeOp0(ops: Array<OperatorState>, fbShift: Float): Float {
        val p = patch!!
        val op0Phase = ops[0].phase + op0Feedback * fbShift
        ops[0].phase += ops[0].phaseStep
        if (ops[0].phase >= 2f * kotlin.math.PI.toFloat()) {
            ops[0].phase -= 2f * kotlin.math.PI.toFloat()
        }
        val raw = sinLutInterpolated(op0Phase)
        val out = raw * envNext(ops[0]) * ops[0].outputLevel
        ops[0].prevOutput = out
        op0Feedback = out
        return out
    }

    private fun computeOpFree(opIdx: Int): Float {
        val op = opState[opIdx]
        val raw = sinLutInterpolated(op.phase)
        op.phase += op.phaseStep
        if (op.phase >= 2f * kotlin.math.PI.toFloat()) {
            op.phase -= 2f * kotlin.math.PI.toFloat()
        }
        val out = raw * envNext(op) * op.outputLevel
        op.prevOutput = out
        return out
    }

    private fun advanceOp(opIdx: Int, phaseMod: Float): Float {
        val op = opState[opIdx]
        val phase = op.phase + phaseMod
        op.phase += op.phaseStep
        if (op.phase >= 2f * kotlin.math.PI.toFloat()) {
            op.phase -= 2f * kotlin.math.PI.toFloat()
        }
        val raw = sinLutInterpolated(phase)
        val out = raw * envNext(op) * op.outputLevel
        op.prevOutput = out
        return out
    }
}
