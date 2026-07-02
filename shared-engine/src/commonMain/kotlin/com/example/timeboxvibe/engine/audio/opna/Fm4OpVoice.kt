package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq

class Fm4OpVoice(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {

    private val opState: Array<OperatorState> = Array(AudioLaws.FM_OPERATORS) { OperatorState() }
    private var patch: FmPatch? = null
    private var baseFrequency: Float = 0f
    private var op0Feedback1: Int = 0
    private var op0Feedback2: Int = 0
    var noteGain: Float = 1f

    var channelAms: Int = 0
    var channelPms: Int = 0

    var enableOversampling: Boolean = false
        set(value) {
            field = value
            updateEnvelopeSampleRates()
            if (baseFrequency > 0f && patch != null) {
                recalcPhaseSteps(patch!!)
            }
        }
    private val oversampleBuffer = FloatArray(4096)
    private val oversampleRate = sampleRate * 2

    fun getPan(): Int = patch?.pan ?: 0

    private var lowPassPrev = 0f

    fun applyPatch(p: FmPatch) {
        patch = p
        setupOpState(0, p.op0)
        setupOpState(1, p.op1)
        setupOpState(2, p.op2)
        setupOpState(3, p.op3)
        if (baseFrequency > 0f) {
            recalcPhaseSteps(p)
        }
        updateEnvelopeSampleRates()
    }

    private fun updateEnvelopeSampleRates() {
        val effectiveRate = if (enableOversampling) oversampleRate else sampleRate
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].opnEnvelope.setSampleRate(effectiveRate)
            i++
        }
    }

    private fun setupOpState(opIdx: Int, spec: OperatorSpec) {
        val state = opState[opIdx]
        state.tl = spec.tl
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

    private fun setupOpStateWithAdsrOverride(
        opIdx: Int,
        spec: OperatorSpec,
        attackOverride: Float?,
        decayOverride: Float?,
        sustainOverride: Float?,
        releaseOverride: Float?
    ) {
        val state = opState[opIdx]
        
        state.tl = spec.tl
        state.outputLevel = AudioLaws.tlToAmplitude(spec.tl)
        state.egMode = spec.egMode
        state.opnEnvelope.attackRate = spec.ar
        state.opnEnvelope.decayRate = spec.dr
        state.opnEnvelope.sustainLevel = spec.sl
        state.opnEnvelope.releaseRate = spec.rr
        
        state.envelope.attack = attackOverride ?: spec.attack
        state.envelope.decay = decayOverride ?: spec.decay
        state.envelope.sustain = sustainOverride ?: spec.sustain
        state.envelope.release = releaseOverride ?: spec.release
    }

    fun getOperatorEnvelope(opIdx: Int): Envelope {
        return opState[opIdx].envelope
    }

    private fun recalcPhaseSteps(p: FmPatch) {
        opState[0].phaseStep = calcPhaseStep(p.op0)
        opState[1].phaseStep = calcPhaseStep(p.op1)
        opState[2].phaseStep = calcPhaseStep(p.op2)
        opState[3].phaseStep = calcPhaseStep(p.op3)
    }

    private fun calcPhaseStep(spec: OperatorSpec): Int {
        val mul = if (spec.mul == 0) 0.5 else spec.mul.toDouble()
        val freq = baseFrequency * mul
        val effectiveSampleRate = if (enableOversampling) oversampleRate else sampleRate
        // VHDL phase generator uses a 29-bit cycle (bits 28..19 for 10-bit sine index)
        return ((freq / effectiveSampleRate) * 536870912.0 * AudioLaws.detunePhaseMultiplierD(spec.detune)).toLong().toInt()
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
        val p = patch
        var isActiveRetrigger = false
        if (p != null) {
            var opIdx = 0
            while (opIdx < AudioLaws.FM_OPERATORS) {
                if (isCarrier(opIdx, p.algorithm)) {
                    val op = opState[opIdx]
                    val levelIsActive = if (op.egMode == EgMode.OPN_RATE) op.opnEnvelope.level > 0f else op.envelope.level > 0f
                    if (!isOpOff(opIdx) && levelIsActive) {
                        isActiveRetrigger = true
                    }
                }
                opIdx++
            }
        }

        baseFrequency = midiToFreq(midi)
        if (p != null) {
            recalcPhaseSteps(p)
            val alg = p.algorithm
            setupOpStateWithAdsrOverride(0, p.op0, if (isCarrier(0, alg)) attack else null, if (isCarrier(0, alg)) decay else null, if (isCarrier(0, alg)) sustain else null, if (isCarrier(0, alg)) release else null)
            setupOpStateWithAdsrOverride(1, p.op1, if (isCarrier(1, alg)) attack else null, if (isCarrier(1, alg)) decay else null, if (isCarrier(1, alg)) sustain else null, if (isCarrier(1, alg)) release else null)
            setupOpStateWithAdsrOverride(2, p.op2, if (isCarrier(2, alg)) attack else null, if (isCarrier(2, alg)) decay else null, if (isCarrier(2, alg)) sustain else null, if (isCarrier(2, alg)) release else null)
            setupOpStateWithAdsrOverride(3, p.op3, if (isCarrier(3, alg)) attack else null, if (isCarrier(3, alg)) decay else null, if (isCarrier(3, alg)) sustain else null, if (isCarrier(3, alg)) release else null)
        }
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            val op = opState[i]
            if (isActiveRetrigger) {
                op.envelope.stage = Envelope.ATTACK
                op.opnEnvelope.noteOn()
            } else {
                op.phase = 0
                op.prevOutput = 0
                op.envelope.noteOn()
                op.opnEnvelope.noteOn()
            }
            i++
        }
        if (!isActiveRetrigger) {
            op0Feedback1 = 0
            op0Feedback2 = 0
            lowPassPrev = 0f
        }
    }

    private fun isCarrier(opIdx: Int, algorithm: Int): Boolean {
        return when (algorithm) {
            0, 1, 2, 3 -> opIdx == 3
            4 -> opIdx == 1 || opIdx == 3
            5, 6 -> opIdx >= 1
            7 -> true
            else -> false
        }
    }

    fun noteOff() {
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].envelope.noteOff()
            opState[i].opnEnvelope.noteOff()
            i++
        }
    }

    internal fun clearActiveNote() {
        baseFrequency = 0f
        op0Feedback1 = 0
        op0Feedback2 = 0
        lowPassPrev = 0f
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].phase = 0
            opState[i].phaseStep = 0
            opState[i].prevOutput = 0
            opState[i].envelope.reset()
            opState[i].opnEnvelope.reset()
            i++
        }
    }

    fun reset() {
        baseFrequency = 0f
        op0Feedback1 = 0
        op0Feedback2 = 0
        lowPassPrev = 0f
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

        val combinedGain = gainScale * noteGain

        if (enableOversampling) {
            val osFrames = frames * 2
            if (oversampleBuffer.size < osFrames) return
            oversampleBuffer.fill(0f, 0, osFrames)
            var i = 0
            while (i < osFrames) {
                oversampleBuffer[i] = renderOne() * combinedGain
                i++
            }
            applyLowPassAndDownsample(oversampleBuffer, osFrames, buffer, startFrame, frames)
        } else {
            var i = 0
            while (i < frames) {
                val sample = renderOne()
                buffer[startFrame + i] += sample * combinedGain
                i++
            }
        }
    }

    private fun applyLowPassAndDownsample(input: FloatArray, inFrames: Int, output: FloatArray, startFrame: Int, outFrames: Int) {
        val alpha = 0.35f
        var prev = lowPassPrev
        var i = 0
        while (i < outFrames) {
            val idx0 = i * 2
            val idx1 = idx0 + 1
            val s0 = input[idx0]
            val s1 = if (idx1 < inFrames) input[idx1] else s0
            val filtered0 = (1f - alpha) * s0 + alpha * prev
            val filtered1 = (1f - alpha) * s1 + alpha * filtered0
            prev = filtered1
            output[startFrame + i] += (filtered0 + filtered1) * 0.5f
            i++
        }
        lowPassPrev = prev
    }

    private fun isOpOff(opIdx: Int): Boolean {
        val op = opState[opIdx]
        val legacyOff = op.envelope.stage == Envelope.OFF
        val opnOff = op.opnEnvelope.stage == OpnRateEnvelope.OFF
        return if (op.egMode == EgMode.OPN_RATE) opnOff else legacyOff
    }

    private fun envNextRaw(op: OperatorState): Int {
        return if (op.egMode == EgMode.OPN_RATE) {
            val envFloat = op.opnEnvelope.next()
            val eglevel = ((1f - envFloat) * 1023f).toInt().coerceIn(0, 1023)
            AudioEnvLut.cltab[eglevel] * AudioEnvLut.gaintab[op.tl.coerceIn(0, 127)]
        } else {
            val effectiveSampleRate = if (enableOversampling) oversampleRate else sampleRate
            val envFloat = op.envelope.next(1f / effectiveSampleRate)
            (envFloat * op.outputLevel * 65535f).toInt().coerceIn(0, 65535)
        }
    }

    private fun renderOne(): Float {
        val p = patch ?: return 0f
        val ops = opState
        val feedback = p.feedback.coerceIn(0, 7)

        val sum = when (p.algorithm) {
            0 -> {
                val s0 = computeOp0(ops, feedback)
                val s1 = advanceOp(1, s0)
                val s2 = advanceOp(2, s1)
                advanceOp(3, s2)
            }
            1 -> {
                val s0 = computeOp0(ops, feedback)
                val s1 = computeOpFree(1)
                val s2 = advanceOp(2, s0 + s1)
                advanceOp(3, s2)
            }
            2 -> {
                val s0 = computeOp0(ops, feedback)
                val s1 = computeOpFree(1)
                val s2 = advanceOp(2, s1)
                advanceOp(3, s0 + s2)
            }
            3 -> {
                val s0 = computeOp0(ops, feedback)
                val s1 = advanceOp(1, s0)
                val s2 = computeOpFree(2)
                advanceOp(3, s1 + s2)
            }
            4 -> {
                val s0 = computeOp0(ops, feedback)
                val s1 = advanceOp(1, s0)
                val s2 = computeOpFree(2)
                val s3 = advanceOp(3, s2)
                s1 + s3
            }
            5 -> {
                val s0 = computeOp0(ops, feedback)
                val s1 = advanceOp(1, s0)
                val s2 = advanceOp(2, s0)
                val s3 = advanceOp(3, s0)
                s1 + s2 + s3
            }
            6 -> {
                val s0 = computeOp0(ops, feedback)
                val s1 = advanceOp(1, s0)
                val s2 = computeOpFree(2)
                val s3 = computeOpFree(3)
                s1 + s2 + s3
            }
            7 -> {
                val s0 = computeOp0(ops, feedback)
                val s1 = computeOpFree(1)
                val s2 = computeOpFree(2)
                val s3 = computeOpFree(3)
                s0 + s1 + s2 + s3
            }
            else -> 0
        }
        return (sum.toFloat() * p.totalLevel) / 131072f
    }

    private val fbtab = intArrayOf(31, 7, 6, 5, 4, 3, 2, 1)

    private fun computeOp0(ops: Array<OperatorState>, feedback: Int): Int {
        val op = ops[0]
        val modulation = if (feedback > 0) {
            val fbOut = op0Feedback1 + op0Feedback2
            val inputS = fbOut shl 13
            inputS shr (fbtab[feedback] - 1)
        } else {
            0
        }
        val phaseAddr = ((op.phase + modulation) ushr 19) and 1023
        op.phase += op.phaseStep

        val envRaw = envNextRaw(op)
        val sineRaw = AudioSinLut.sample10BitInt(phaseAddr)
        val out = (sineRaw * envRaw) shr 8

        op.prevOutput = out
        op0Feedback2 = op0Feedback1
        op0Feedback1 = out
        return out
    }

    private fun computeOpFree(opIdx: Int): Int {
        val op = opState[opIdx]
        val phaseAddr = (op.phase ushr 19) and 1023
        op.phase += op.phaseStep

        val envRaw = envNextRaw(op)
        val sineRaw = AudioSinLut.sample10BitInt(phaseAddr)
        val out = (sineRaw * envRaw) shr 8

        op.prevOutput = out
        return out
    }

    private fun advanceOp(opIdx: Int, phaseMod: Int): Int {
        val op = opState[opIdx]
        val modulation = phaseMod shl 15
        val phaseAddr = ((op.phase + modulation) ushr 19) and 1023
        op.phase += op.phaseStep

        val envRaw = envNextRaw(op)
        val sineRaw = AudioSinLut.sample10BitInt(phaseAddr)
        val out = (sineRaw * envRaw) shr 8

        op.prevOutput = out
        return out
    }

    private companion object {
    }
}
