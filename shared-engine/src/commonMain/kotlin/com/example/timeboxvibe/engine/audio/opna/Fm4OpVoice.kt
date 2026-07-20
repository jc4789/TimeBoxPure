package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws

/**
 * Four-operator OPN voice accuracy profile.
 * Implements logarithmic operators, 8 algorithms, feedback, FNUM/block/DT/MUL and the normal EG.
 * Includes shared hardware LFO AM/PM and operator SSG-EG. CSM and chip timers
 * remain intentionally out of scope.
 */
class Fm4OpVoice(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {

    private val opState: Array<OperatorState> = Array(AudioLaws.FM_OPERATORS) { OperatorState() }
    private val opSpec: Array<OperatorSpec?> = arrayOfNulls(AudioLaws.FM_OPERATORS)
    private var patch: FmPatch? = null
    private var configuredOperatorMask: Int = 0
    private var channelAlgorithm: Int = 0
    private var channelFeedback: Int = 0
    private var channelTotalLevel: Float = 1f
    private var channelHardwarePms: Int = 0
    private var channelHardwareAms: Int = 0
    private var hasPitch: Boolean = false
    private var keyDown: Boolean = false
    private var packedPitch: Int = 0
    private var op0Feedback1: Int = 0
    private var op0Feedback2: Int = 0
    var noteGain: Float = 1f

    private var panOverride: Int = -1
    private var detuneCents: Int = 0
    private var targetMidi: Int = -1
    private var requestedSlideFrames: Int = 0
    private var lfoDelayRemaining: Int = 0
    private var currentPmQ20: Int = 0
    private var currentAmAttenuation: Int = 0
    private val currentDriverPitchQ20 = IntArray(AudioLaws.FM_OPERATORS)
    private val currentDriverVolumeOffset = IntArray(AudioLaws.FM_OPERATORS)
    private val rampStartStep = LongArray(AudioLaws.FM_OPERATORS)
    private val rampTargetStep = LongArray(AudioLaws.FM_OPERATORS)
    private var rampFrames: Int = 0
    private var rampPosition: Int = 0
    private var specialMode: Boolean = false
    private val specialPackedPitch = IntArray(AudioLaws.FM_OPERATORS)
    private val specialRampStart = LongArray(AudioLaws.FM_OPERATORS)
    private val specialRampTarget = LongArray(AudioLaws.FM_OPERATORS)
    private val specialRampFrames = IntArray(AudioLaws.FM_OPERATORS)
    private val specialRampPosition = IntArray(AudioLaws.FM_OPERATORS)
    private val specialBaseCents = IntArray(AudioLaws.FM_OPERATORS)
    private val slotFnumDetune = IntArray(AudioLaws.FM_OPERATORS)
    private val slotKeyOnDelayFrames = IntArray(AudioLaws.FM_OPERATORS)
    private val pendingKeyOnFrames = IntArray(AudioLaws.FM_OPERATORS)
    private val pendingKeyOn = BooleanArray(AudioLaws.FM_OPERATORS)

    var enableOversampling: Boolean = false
        set(value) {
            field = value
            if (hasPitch && patch != null) {
                recalcPhaseSteps(patch!!)
            }
        }
    private val oversampleBuffer = FloatArray(4096)
    private val oversampleRate = sampleRate * 2

    fun getPan(): Int = if (panOverride >= 0) panOverride else patch?.pan ?: 0

    private var lowPassPrev = 0f

    fun applyPatch(p: FmPatch) {
        if (patch === p) return
        OpnLogTables.warmUp()
        OpnEnvelopeCompatibility.warmUp()
        patch = p
        configuredOperatorMask = 15
        channelAlgorithm = p.algorithm.coerceIn(0, 7)
        channelFeedback = p.feedback.coerceIn(0, 7)
        channelTotalLevel = p.totalLevel
        setupOpState(0, p.op0)
        setupOpState(1, p.op1)
        setupOpState(2, p.op2)
        setupOpState(3, p.op3)
        if (hasPitch) {
            recalcPhaseSteps(p)
        }
        updateEnvelopeSampleRates()
    }

    internal fun applyPatchToSlots(p: FmPatch, slotMask: Int) {
        OpnLogTables.warmUp()
        OpnEnvelopeCompatibility.warmUp()
        channelAlgorithm = p.algorithm.coerceIn(0, 7)
        if ((slotMask and 1) != 0) channelFeedback = p.feedback.coerceIn(0, 7)
        configuredOperatorMask = configuredOperatorMask or (slotMask and 15)
        var opIndex = 0
        while (opIndex < AudioLaws.FM_OPERATORS) {
            if ((slotMask and (1 shl opIndex)) != 0) {
                val spec = operatorSpec(p, opIndex)
                opSpec[opIndex] = spec
                setupOpState(opIndex, spec)
            }
            opIndex++
        }
        updateEnvelopeSampleRates()
    }

    private fun updateEnvelopeSampleRates() {
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            // The EG is clocked once per output frame and held across both oversampled subsamples.
            opState[i].opnEnvelope.setSampleRate(sampleRate)
            i++
        }
    }

    private fun setupOpState(opIdx: Int, spec: OperatorSpec) {
        opSpec[opIdx] = spec
        val state = opState[opIdx]
        state.tl = spec.tl
        state.amEnabled = spec.ams != 0
        state.opnEnvelope.configureSsgEg(spec.ssgEg)
    }

    private fun setupOpStateWithAdsrOverride(
        opIdx: Int,
        spec: OperatorSpec,
        attackOverride: Float,
        decayOverride: Float,
        sustainOverride: Float,
        releaseOverride: Float,
        updateTl: Boolean = true
    ) {
        val state = opState[opIdx]
        if (updateTl) state.tl = spec.tl
        OpnEnvelopeCompatibility.configure(
            state.opnEnvelope,
            spec,
            OpnPitch.block(packedPitch),
            OpnPitch.fnum(packedPitch),
            attackOverride,
            decayOverride,
            sustainOverride,
            releaseOverride
        )
    }

    private fun setupOperatorForNote(
        opIdx: Int,
        spec: OperatorSpec,
        algorithm: Int,
        attack: Float,
        decay: Float,
        sustain: Float,
        release: Float
    ) {
        val carrier = isCarrier(opIdx, algorithm)
        setupOpStateWithAdsrOverride(
            opIdx,
            spec,
            if (carrier) attack else NO_ADSR_OVERRIDE,
            if (carrier) decay else NO_ADSR_OVERRIDE,
            if (carrier) sustain else NO_ADSR_OVERRIDE,
            if (carrier) release else NO_ADSR_OVERRIDE
        )
    }

    internal fun operatorEnvelopeSnapshot(opIdx: Int): Int {
        val envelope = opState[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)].opnEnvelope
        return (envelope.stage shl 10) or envelope.attenuation
    }

    internal fun operatorPhaseSnapshot(opIdx: Int): UInt =
        opState[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)].phase

    internal fun operatorPhaseStepSnapshot(opIdx: Int): UInt =
        opState[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)].phaseStep

    internal fun operatorOutputSnapshot(opIdx: Int): Int =
        opState[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)].prevOutput

    internal fun operatorAttenuationSnapshot(opIdx: Int): Int =
        opState[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)].opnEnvelope.attenuation

    internal fun operatorTlSnapshot(opIdx: Int): Int =
        opState[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)].tl

    internal fun algorithmSnapshot(): Int = channelAlgorithm

    internal fun feedbackSnapshot(): Int = channelFeedback

    internal fun slotDetuneSnapshot(opIdx: Int): Int =
        slotFnumDetune[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)]

    internal fun slotKeyOnDelaySnapshot(opIdx: Int): Int =
        slotKeyOnDelayFrames[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)]

    internal fun slotPendingKeyOnSnapshot(opIdx: Int): Boolean =
        pendingKeyOn[opIdx.coerceIn(0, AudioLaws.FM_OPERATORS - 1)]

    internal fun hardwareLfoDelayRemainingSnapshot(): Int = lfoDelayRemaining

    internal fun channelTotalLevelSnapshot(): Float = channelTotalLevel

    internal fun feedbackHistorySnapshot(): Long =
        (op0Feedback1.toLong() shl 32) or (op0Feedback2.toLong() and 0xffffffffL)

    internal fun releaseFinished(): Boolean {
        var opIndex = 0
        while (opIndex < AudioLaws.FM_OPERATORS) {
            if (opState[opIndex].opnEnvelope.stage != OpnRateEnvelope.OFF) return false
            opIndex++
        }
        return true
    }

    private fun recalcPhaseSteps(p: FmPatch) {
        opState[0].phaseStep = calcPhaseStep(p.op0)
        opState[1].phaseStep = calcPhaseStep(p.op1)
        opState[2].phaseStep = calcPhaseStep(p.op2)
        opState[3].phaseStep = calcPhaseStep(p.op3)
    }

    private fun calcPhaseStep(spec: OperatorSpec): UInt {
        val effectiveSampleRate = if (enableOversampling) oversampleRate else sampleRate
        return OpnPitch.phaseStep29(packedPitch, spec, effectiveSampleRate, detuneCents)
    }

    internal fun setNoteControls(
        pan: Int,
        cents: Int,
        delayFrames: Int,
        slideTargetMidi: Int,
        slideFrames: Int
    ) {
        panOverride = pan.coerceIn(0, 3)
        detuneCents = cents.coerceIn(-1_200, 1_200)
        lfoDelayRemaining = delayFrames.coerceAtLeast(0)
        targetMidi = slideTargetMidi
        requestedSlideFrames = slideFrames.coerceAtLeast(0)
    }

    internal fun setSlotDetune(slotMask: Int, value: Int, relative: Boolean) {
        var index = 0
        while (index < AudioLaws.FM_OPERATORS) {
            if ((slotMask and (1 shl index)) != 0) {
                slotFnumDetune[index] = if (relative) {
                    (slotFnumDetune[index] + value).coerceIn(-32_768, 32_767)
                } else value.coerceIn(-32_768, 32_767)
                if (specialMode && hasPitch) recalcSpecialOperator(index)
            }
            index++
        }
    }

    internal fun setOperatorTl(slotMask: Int, value: Int, relative: Boolean) {
        var index = 0
        while (index < AudioLaws.FM_OPERATORS) {
            if ((slotMask and (1 shl index)) != 0) {
                opState[index].tl = if (relative) {
                    (opState[index].tl + value).coerceIn(0, 127)
                } else value.coerceIn(0, 127)
            }
            index++
        }
    }

    internal fun setFeedback(value: Int, relative: Boolean) {
        channelFeedback = if (relative) (channelFeedback + value).coerceIn(0, 7) else value.coerceIn(0, 7)
    }

    internal fun setHardwareLfoPms(value: Int) {
        channelHardwarePms = value.coerceIn(0, 7)
    }

    internal fun setHardwareLfoAms(value: Int) {
        channelHardwareAms = value.coerceIn(0, 3)
    }

    internal fun setSlotKeyOnDelay(slotMask: Int, frames: Int) {
        if (slotMask == 0) {
            slotKeyOnDelayFrames.fill(0)
            return
        }
        var index = 0
        while (index < AudioLaws.FM_OPERATORS) {
            if ((slotMask and (1 shl index)) != 0) slotKeyOnDelayFrames[index] = frames.coerceAtLeast(0)
            index++
        }
    }

    fun noteOn(midi: Int) {
        lfoDelayRemaining = 0
        noteOnInternal(midi, NO_ADSR_OVERRIDE, NO_ADSR_OVERRIDE, NO_ADSR_OVERRIDE, NO_ADSR_OVERRIDE)
    }

    fun noteOn(
        midi: Int,
        attack: Float?,
        decay: Float?,
        sustain: Float?,
        release: Float?
    ) {
        lfoDelayRemaining = 0
        noteOnInternal(
            midi,
            attack ?: NO_ADSR_OVERRIDE,
            decay ?: NO_ADSR_OVERRIDE,
            sustain ?: NO_ADSR_OVERRIDE,
            release ?: NO_ADSR_OVERRIDE
        )
    }

    internal fun noteOnScheduled(
        midi: Int,
        attack: Float,
        decay: Float,
        sustain: Float,
        release: Float
    ) {
        noteOnInternal(midi, attack, decay, sustain, release)
    }

    private fun noteOnInternal(
        midi: Int,
        attack: Float,
        decay: Float,
        sustain: Float,
        release: Float
    ) {
        specialMode = false
        val wasKeyDown = keyDown
        val p = patch
        var isActiveRetrigger = false
        if (p != null) {
            var opIdx = 0
            while (opIdx < AudioLaws.FM_OPERATORS) {
                if (isCarrier(opIdx, p.algorithm)) {
                    val op = opState[opIdx]
                    if (!isOpOff(opIdx) && op.opnEnvelope.attenuation < OpnRateEnvelope.MAX_ATTENUATION) {
                        isActiveRetrigger = true
                    }
                }
                opIdx++
            }
        }

        packedPitch = OpnPitch.nearestBlockFnumForMidi(midi)
        hasPitch = true
        if (p != null) {
            recalcPhaseSteps(p)
            val alg = p.algorithm
            setupOperatorForNote(0, p.op0, alg, attack, decay, sustain, release)
            setupOperatorForNote(1, p.op1, alg, attack, decay, sustain, release)
            setupOperatorForNote(2, p.op2, alg, attack, decay, sustain, release)
            setupOperatorForNote(3, p.op3, alg, attack, decay, sustain, release)
            configurePitchRamp(p)
            targetMidi = -1
            requestedSlideFrames = 0
        }
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            val op = opState[i]
            if (!wasKeyDown) {
                op.phase = 0u
                op.prevOutput = 0
                op.opnEnvelope.noteOn(retrigger = isActiveRetrigger)
            }
            i++
        }
        if (!wasKeyDown) {
            op0Feedback1 = 0
            op0Feedback2 = 0
            lowPassPrev = 0f
        }
        keyDown = true
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
        keyDown = false
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].opnEnvelope.noteOff()
            i++
        }
    }

    internal fun noteOnOperator(operator: Int, midi: Int, slideTargetMidi: Int = -1, slideFrames: Int = 0) {
        noteOnSlots(1 shl operator.coerceIn(0, AudioLaws.FM_OPERATORS - 1), midi, slideTargetMidi, slideFrames, detuneCents)
    }

    internal fun noteOnSlots(
        slotMask: Int,
        midi: Int,
        slideTargetMidi: Int = -1,
        slideFrames: Int = 0,
        cents: Int = 0
    ) {
        val pitch = OpnPitch.nearestBlockFnumForMidi(midi)
        specialMode = true
        hasPitch = true
        packedPitch = pitch
        var index = 0
        while (index < AudioLaws.FM_OPERATORS) {
            if ((slotMask and (1 shl index)) != 0) {
                val spec = opSpec[index]
                if (spec != null) {
                    specialPackedPitch[index] = pitch
                    specialBaseCents[index] = cents.coerceIn(-1_200, 1_200)
                    setupOpStateWithAdsrOverride(
                        index, spec, NO_ADSR_OVERRIDE, NO_ADSR_OVERRIDE,
                        NO_ADSR_OVERRIDE, NO_ADSR_OVERRIDE, updateTl = false
                    )
                    val rate = if (enableOversampling) oversampleRate else sampleRate
                    val op = opState[index]
                    op.phaseStep = OpnPitch.phaseStep29(pitch, spec, rate, specialBaseCents[index], slotFnumDetune[index])
                    specialRampStart[index] = op.phaseStep.toLong()
                    specialRampTarget[index] = if (slideTargetMidi in 0..127 && slideFrames > 0) {
                        OpnPitch.phaseStep29(
                            OpnPitch.nearestBlockFnumForMidi(slideTargetMidi), spec, rate,
                            specialBaseCents[index], slotFnumDetune[index]
                        ).toLong()
                    } else op.phaseStep.toLong()
                    specialRampFrames[index] = if (slideTargetMidi in 0..127) slideFrames.coerceAtLeast(0) else 0
                    specialRampPosition[index] = 0
                    val delay = slotKeyOnDelayFrames[index]
                    pendingKeyOnFrames[index] = delay
                    pendingKeyOn[index] = delay > 0
                    if (delay == 0) keyOnSpecialOperator(index)
                }
            }
            index++
        }
    }

    internal fun noteOffOperator(operator: Int) {
        noteOffSlots(1 shl operator.coerceIn(0, AudioLaws.FM_OPERATORS - 1))
    }

    internal fun noteOffSlots(slotMask: Int) {
        var operator = 0
        while (operator < AudioLaws.FM_OPERATORS) {
            if ((slotMask and (1 shl operator)) != 0) {
                pendingKeyOn[operator] = false
                pendingKeyOnFrames[operator] = 0
                opState[operator].opnEnvelope.noteOff()
            }
            operator++
        }
    }

    internal fun clearActiveNote() {
        hasPitch = false
        keyDown = false
        packedPitch = 0
        op0Feedback1 = 0
        op0Feedback2 = 0
        lowPassPrev = 0f
        specialMode = false
        currentDriverPitchQ20.fill(0)
        currentDriverVolumeOffset.fill(0)
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].phase = 0u
            opState[i].phaseStep = 0u
            opState[i].prevOutput = 0
            opState[i].opnEnvelope.reset()
            specialRampStart[i] = 0L
            specialRampTarget[i] = 0L
            specialRampFrames[i] = 0
            specialRampPosition[i] = 0
            pendingKeyOn[i] = false
            pendingKeyOnFrames[i] = 0
            i++
        }
    }

    fun reset() {
        hasPitch = false
        keyDown = false
        packedPitch = 0
        op0Feedback1 = 0
        op0Feedback2 = 0
        lowPassPrev = 0f
        noteGain = 1f
        panOverride = -1
        detuneCents = 0
        targetMidi = -1
        requestedSlideFrames = 0
        lfoDelayRemaining = 0
        rampFrames = 0
        rampPosition = 0
        specialMode = false
        currentDriverPitchQ20.fill(0)
        currentDriverVolumeOffset.fill(0)
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            opState[i].reset()
            specialRampStart[i] = 0L
            specialRampTarget[i] = 0L
            specialRampFrames[i] = 0
            specialRampPosition[i] = 0
            specialBaseCents[i] = 0
            slotFnumDetune[i] = 0
            slotKeyOnDelayFrames[i] = 0
            pendingKeyOnFrames[i] = 0
            pendingKeyOn[i] = false
            opSpec[i] = null
            i++
        }
        patch = null
        configuredOperatorMask = 0
        channelAlgorithm = 0
        channelFeedback = 0
        channelTotalLevel = 1f
        channelHardwarePms = 0
        channelHardwareAms = 0
    }

    fun render(
        buffer: FloatArray,
        frames: Int,
        sampleRate: Int,
        gainScale: Float,
        startFrame: Int = 0,
        lfo: Lfo? = null
    ) {
        renderDriven(buffer, frames, sampleRate, gainScale, startFrame, lfo, null, null)
    }

    internal fun renderDriven(
        buffer: FloatArray,
        frames: Int,
        sampleRate: Int,
        gainScale: Float,
        startFrame: Int,
        lfo: Lfo?,
        driverFrame: PmdModulationFrame?,
        fm3DriverFrames: Array<PmdModulationFrame?>?
    ) {
        if (configuredOperatorMask == 0) {
            return
        }

        val combinedGain = gainScale * noteGain

        if (enableOversampling) {
            val osFrames = frames * 2
            if (oversampleBuffer.size < osFrames) return
            oversampleBuffer.fill(0f, 0, osFrames)
            var frame = 0
            while (frame < frames) {
                val first = frame * 2
                setLfoFrame(lfo, frame, clockFrame = true, driverFrame, fm3DriverFrames)
                oversampleBuffer[first] = renderOne(clockEnvelope = true) * combinedGain
                setLfoFrame(lfo, frame, clockFrame = false, driverFrame, fm3DriverFrames)
                oversampleBuffer[first + 1] = renderOne(clockEnvelope = false) * combinedGain
                frame++
            }
            applyLowPassAndDownsample(oversampleBuffer, osFrames, buffer, startFrame, frames)
        } else {
            var i = 0
            while (i < frames) {
                setLfoFrame(lfo, i, clockFrame = true, driverFrame, fm3DriverFrames)
                val sample = renderOne(clockEnvelope = true)
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
        return opState[opIdx].opnEnvelope.stage == OpnRateEnvelope.OFF
    }

    private fun renderOne(clockEnvelope: Boolean): Float {
        if (configuredOperatorMask == 0) return 0f
        val ops = opState
        val feedback = channelFeedback

        if (clockEnvelope) {
            clockPendingKeyOns()
            if (specialMode) clockSpecialPitchRamps() else clockPitchRamp()
        }

        val sum = when (channelAlgorithm) {
            0 -> {
                val s0 = computeOp0(ops, feedback, clockEnvelope)
                val s1 = advanceOp(1, s0, clockEnvelope)
                val s2 = advanceOp(2, s1, clockEnvelope)
                advanceOp(3, s2, clockEnvelope)
            }
            1 -> {
                val s0 = computeOp0(ops, feedback, clockEnvelope)
                val s1 = computeOpFree(1, clockEnvelope)
                val s2 = advanceOp(2, s0 + s1, clockEnvelope)
                advanceOp(3, s2, clockEnvelope)
            }
            2 -> {
                val s0 = computeOp0(ops, feedback, clockEnvelope)
                val s1 = computeOpFree(1, clockEnvelope)
                val s2 = advanceOp(2, s1, clockEnvelope)
                advanceOp(3, s0 + s2, clockEnvelope)
            }
            3 -> {
                val s0 = computeOp0(ops, feedback, clockEnvelope)
                val s1 = advanceOp(1, s0, clockEnvelope)
                val s2 = computeOpFree(2, clockEnvelope)
                advanceOp(3, s1 + s2, clockEnvelope)
            }
            4 -> {
                val s0 = computeOp0(ops, feedback, clockEnvelope)
                val s1 = advanceOp(1, s0, clockEnvelope)
                val s2 = computeOpFree(2, clockEnvelope)
                val s3 = advanceOp(3, s2, clockEnvelope)
                s1 + s3
            }
            5 -> {
                val s0 = computeOp0(ops, feedback, clockEnvelope)
                val s1 = advanceOp(1, s0, clockEnvelope)
                val s2 = advanceOp(2, s0, clockEnvelope)
                val s3 = advanceOp(3, s0, clockEnvelope)
                s1 + s2 + s3
            }
            6 -> {
                val s0 = computeOp0(ops, feedback, clockEnvelope)
                val s1 = advanceOp(1, s0, clockEnvelope)
                val s2 = computeOpFree(2, clockEnvelope)
                val s3 = computeOpFree(3, clockEnvelope)
                s1 + s2 + s3
            }
            7 -> {
                val s0 = computeOp0(ops, feedback, clockEnvelope)
                val s1 = computeOpFree(1, clockEnvelope)
                val s2 = computeOpFree(2, clockEnvelope)
                val s3 = computeOpFree(3, clockEnvelope)
                s0 + s1 + s2 + s3
            }
            else -> 0
        }
        return (sum.toFloat() * channelTotalLevel) / 16384f
    }

    private fun computeOp0(ops: Array<OperatorState>, feedback: Int, clockEnvelope: Boolean): Int {
        val op = ops[0]
        val phaseMod = if (feedback > 0) {
            (op0Feedback1 + op0Feedback2) shr (10 - feedback)
        } else {
            0
        }
        val phaseAddr = (basePhaseAddress(op) + phaseMod) and PHASE_ADDRESS_MASK
        advancePhase(op, 0)

        val attenuation = operatorAttenuation(op, 0, clockEnvelope)
        val out = OpnLogTables.output(phaseAddr, attenuation, op.tl)

        op.prevOutput = out
        op0Feedback2 = op0Feedback1
        op0Feedback1 = out
        return out
    }

    private fun computeOpFree(opIdx: Int, clockEnvelope: Boolean): Int {
        val op = opState[opIdx]
        val phaseAddr = basePhaseAddress(op)
        advancePhase(op, opIdx)

        val attenuation = operatorAttenuation(op, opIdx, clockEnvelope)
        val out = OpnLogTables.output(phaseAddr, attenuation, op.tl)

        op.prevOutput = out
        return out
    }

    private fun advanceOp(opIdx: Int, phaseMod: Int, clockEnvelope: Boolean): Int {
        val op = opState[opIdx]
        val phaseAddr = (basePhaseAddress(op) + (phaseMod shr 1)) and PHASE_ADDRESS_MASK
        advancePhase(op, opIdx)

        val attenuation = operatorAttenuation(op, opIdx, clockEnvelope)
        val out = OpnLogTables.output(phaseAddr, attenuation, op.tl)

        op.prevOutput = out
        return out
    }

    private fun basePhaseAddress(op: OperatorState): Int =
        ((op.phase shr PHASE_ADDRESS_SHIFT) and PHASE_ADDRESS_MASK_UINT).toInt()

    private fun setLfoFrame(
        lfo: Lfo?,
        frame: Int,
        clockFrame: Boolean,
        driverFrame: PmdModulationFrame?,
        fm3DriverFrames: Array<PmdModulationFrame?>?
    ) {
        val delayed = lfoDelayRemaining > 0
        if (clockFrame && delayed) lfoDelayRemaining--
        if (lfo == null || delayed) {
            currentPmQ20 = 0
            currentAmAttenuation = 0
        } else {
            val pmDepth = OpnaLfoLaws.pmsDepthQ20(channelHardwarePms)
            currentPmQ20 = (lfo.pmAt(frame) * pmDepth) shr 10
            val amDepth = OpnaLfoLaws.amsDepthAttenuation(channelHardwareAms)
            currentAmAttenuation = (lfo.amAt(frame) * amDepth) shr 10
        }
        var operator = 0
        while (operator < AudioLaws.FM_OPERATORS) {
            val selected = if (specialMode) fm3DriverFrames?.get(operator) else driverFrame
            copyDriverFrame(selected, frame, operator)
            operator++
        }
    }

    private fun operatorAttenuation(op: OperatorState, opIndex: Int, clockEnvelope: Boolean): Int {
        if (pendingKeyOn[opIndex]) return OpnRateEnvelope.MAX_ATTENUATION
        val envelope = if (clockEnvelope) {
            op.opnEnvelope.nextAttenuation()
        } else {
            op.opnEnvelope.currentAttenuation()
        }
        var result = envelope + if (op.amEnabled) currentAmAttenuation else 0
        result += currentDriverVolumeOffset[opIndex]
        return result
    }

    private fun advancePhase(op: OperatorState, opIndex: Int) {
        val base = op.phaseStep.toLong()
        val softwarePitch = currentDriverPitchQ20[opIndex]
        val delta = (base * (currentPmQ20 + softwarePitch).toLong()) shr 20
        op.phase += (base + delta).coerceAtLeast(0L).toUInt()
    }

    private fun copyDriverFrame(frame: PmdModulationFrame?, frameIndex: Int, operator: Int) {
        if (frame == null) {
            currentDriverPitchQ20[operator] = 0
            currentDriverVolumeOffset[operator] = 0
            return
        }
        val bit = 1 shl operator
        var pitch = 0
        if (frame.pitchTarget1 && (frame.tlMask1 == 0 || (frame.tlMask1 and bit) != 0)) {
            pitch += frame.pitch1Q20[frameIndex]
        }
        if (frame.pitchTarget2 && (frame.tlMask2 == 0 || (frame.tlMask2 and bit) != 0)) {
            pitch += frame.pitch2Q20[frameIndex]
        }
        var attenuation = frame.baseAttenuation
        val carrier = isCarrier(operator, channelAlgorithm)
        if (frame.volumeTarget1 && (if (frame.tlMask1 == 0) carrier else (frame.tlMask1 and bit) != 0)) {
            attenuation -= frame.volume1[frameIndex] * 8
        }
        if (frame.volumeTarget2 && (if (frame.tlMask2 == 0) carrier else (frame.tlMask2 and bit) != 0)) {
            attenuation -= frame.volume2[frameIndex] * 8
        }
        currentDriverPitchQ20[operator] = pitch
        currentDriverVolumeOffset[operator] = attenuation
    }

    private fun configurePitchRamp(p: FmPatch) {
        rampPosition = 0
        rampFrames = if (targetMidi in 0..127) requestedSlideFrames else 0
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            rampStartStep[i] = opState[i].phaseStep.toLong()
            rampTargetStep[i] = if (rampFrames > 0) {
                val spec = when (i) {
                    0 -> p.op0
                    1 -> p.op1
                    2 -> p.op2
                    else -> p.op3
                }
                val rate = if (enableOversampling) oversampleRate else sampleRate
                OpnPitch.phaseStep29(OpnPitch.nearestBlockFnumForMidi(targetMidi), spec, rate, detuneCents).toLong()
            } else {
                opState[i].phaseStep.toLong()
            }
            i++
        }
    }

    private fun operatorSpec(p: FmPatch, index: Int): OperatorSpec = when (index) {
        0 -> p.op0
        1 -> p.op1
        2 -> p.op2
        else -> p.op3
    }

    private fun recalcSpecialOperator(index: Int) {
        val spec = opSpec[index] ?: return
        val rate = if (enableOversampling) oversampleRate else sampleRate
        val step = OpnPitch.phaseStep29(
            specialPackedPitch[index], spec, rate, specialBaseCents[index], slotFnumDetune[index]
        )
        opState[index].phaseStep = step
        specialRampStart[index] = step.toLong()
        if (specialRampFrames[index] <= 0) specialRampTarget[index] = step.toLong()
    }

    private fun keyOnSpecialOperator(index: Int) {
        val op = opState[index]
        pendingKeyOn[index] = false
        pendingKeyOnFrames[index] = 0
        op.phase = 0u
        op.prevOutput = 0
        op.opnEnvelope.noteOn(retrigger = op.opnEnvelope.stage != OpnRateEnvelope.OFF)
        if (index == 0) {
            op0Feedback1 = 0
            op0Feedback2 = 0
        }
    }

    private fun clockPendingKeyOns() {
        var index = 0
        while (index < AudioLaws.FM_OPERATORS) {
            if (pendingKeyOn[index]) {
                if (pendingKeyOnFrames[index] <= 0) keyOnSpecialOperator(index)
                else pendingKeyOnFrames[index]--
            }
            index++
        }
    }

    private fun clockPitchRamp() {
        if (rampFrames <= 0 || rampPosition >= rampFrames) return
        rampPosition++
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            val start = rampStartStep[i]
            val target = rampTargetStep[i]
            val interpolated = start + (target - start) * rampPosition.toLong() / rampFrames.toLong()
            opState[i].phaseStep = interpolated.toUInt()
            i++
        }
    }

    private fun clockSpecialPitchRamps() {
        var i = 0
        while (i < AudioLaws.FM_OPERATORS) {
            val frames = specialRampFrames[i]
            val position = specialRampPosition[i]
            if (frames > 0 && position < frames) {
                val next = position + 1
                specialRampPosition[i] = next
                opState[i].phaseStep = (
                    specialRampStart[i] +
                        (specialRampTarget[i] - specialRampStart[i]) * next.toLong() / frames.toLong()
                    ).toUInt()
            }
            i++
        }
    }

    private companion object {
        const val NO_ADSR_OVERRIDE = -1f
        const val PHASE_CYCLE_BITS = 29
        const val PHASE_ADDRESS_BITS = 10
        const val PHASE_ADDRESS_SHIFT = PHASE_CYCLE_BITS - PHASE_ADDRESS_BITS
        const val PHASE_ADDRESS_MASK = (1 shl PHASE_ADDRESS_BITS) - 1
        val PHASE_ADDRESS_MASK_UINT: UInt = PHASE_ADDRESS_MASK.toUInt()
    }
}
