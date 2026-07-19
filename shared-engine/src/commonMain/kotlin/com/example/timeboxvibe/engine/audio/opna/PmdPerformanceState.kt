package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws

/** Primitive per-frame PMD modulation values passed to a physical voice for one render call. */
internal class PmdModulationFrame {
    val pitch1Q20 = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val pitch2Q20 = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val volume1 = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val volume2 = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    var pitchTarget1 = false
    var pitchTarget2 = false
    var volumeTarget1 = false
    var volumeTarget2 = false
    var tlMask1 = 0
    var tlMask2 = 0
    var baseAttenuation = 0
    var hardwarePms = 0
    var hardwareAms = 0

    fun clear() {
        pitch1Q20.fill(0)
        pitch2Q20.fill(0)
        volume1.fill(0)
        volume2.fill(0)
        pitchTarget1 = false
        pitchTarget2 = false
        volumeTarget1 = false
        volumeTarget2 = false
        tlMask1 = 0
        tlMask2 = 0
        baseAttenuation = 0
        hardwarePms = 0
        hardwareAms = 0
    }
}

/** Primitive SSG driver output; the physical voice owns no PMD clock or envelope lifetime. */
internal class PmdSsgFrame {
    val tonePeriodOffset = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val volumeOffset = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val softwareEnvelopeLevel = IntArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
    val releaseFinished = BooleanArray(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)

    fun clear(baseLevel: Int) {
        tonePeriodOffset.fill(0)
        volumeOffset.fill(0)
        softwareEnvelopeLevel.fill(baseLevel.coerceIn(0, 15))
        releaseFinished.fill(false)
    }
}

/** Allocation-free PMD logical-part state, independent of physical YM2608 voice assignment. */
internal class PmdPerformanceState(sampleRate: Int) {
    private val fmParts = Array(AudioLaws.FM_CHANNELS) { part ->
        PmdLogicalPartState(sampleRate, seedFor(FM_SEED_FAMILY, part), withEnvelope = false)
    }
    private val ssgParts = Array(AudioLaws.SSG_CHANNELS) { part ->
        PmdLogicalPartState(sampleRate, seedFor(SSG_SEED_FAMILY, part), withEnvelope = true)
    }
    private val fm3Parts = Array(CompiledOpnaSong.FM3_PART_COUNT) { part ->
        PmdLogicalPartState(sampleRate, seedFor(FM3_SEED_FAMILY, part), withEnvelope = false)
    }

    init {
        reset()
    }

    fun reset() {
        resetParts(fmParts, hasSlotMasks = false)
        resetParts(ssgParts, hasSlotMasks = false)
        resetParts(fm3Parts, hasSlotMasks = true)
    }

    fun setTempo(bpmMilli: Int, clocksPerQuarter: Int) {
        setTempo(fmParts, bpmMilli, clocksPerQuarter)
        setTempo(ssgParts, bpmMilli, clocksPerQuarter)
        setTempo(fm3Parts, bpmMilli, clocksPerQuarter)
    }

    fun prepare(frames: Int) {
        val count = frames.coerceIn(0, OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK)
        prepareParts(fmParts, count)
        prepareParts(ssgParts, count)
        prepareParts(fm3Parts, count)
    }

    fun fmFrame(part: Int): PmdModulationFrame? =
        if (part in fmParts.indices) fmParts[part].modulation else null

    fun ssgFrame(part: Int): PmdSsgFrame? =
        if (part in ssgParts.indices) ssgParts[part].ssgFrame else null

    fun fm3Frame(part: Int): PmdModulationFrame? =
        if (part in fm3Parts.indices) fm3Parts[part].modulation else null

    fun setSsgBaseLevel(part: Int, level: Int) {
        if (part in ssgParts.indices) ssgParts[part].ssgBaseLevel = level.coerceIn(0, 15)
    }

    fun configureSsgEnvelope(
        part: Int,
        format: Int,
        attack: Int,
        decay: Int,
        sustain: Int,
        release: Int,
        sustainLevel: Int,
        attackLevel: Int
    ) {
        selectedEnvelope(part)?.configure(format, attack, decay, sustain, release, sustainLevel, attackLevel)
    }

    fun setSsgEnvelopeClockMode(part: Int, mode: Int) {
        selectedEnvelope(part)?.setClockMode(mode)
    }

    fun setHardwareLfoPms(part: Int, value: Int) {
        selectedPart(fmParts, part)?.hardwarePms = value.coerceIn(0, 7)
    }

    fun setHardwareLfoAms(part: Int, value: Int) {
        selectedPart(fmParts, part)?.hardwareAms = value.coerceIn(0, 3)
    }

    fun setHardwareLfoDelay(part: Int, kind: Int, value: Int, dotted: Boolean) {
        val state = selectedPart(fmParts, part) ?: return
        state.hardwareDelayKind = when (kind) {
            CompiledOpnaSong.HW_LFO_DELAY_RAW_CLOCKS,
            CompiledOpnaSong.HW_LFO_DELAY_NOTE_LENGTH -> kind
            else -> CompiledOpnaSong.HW_LFO_DELAY_NONE
        }
        state.hardwareDelayValue = value.coerceAtLeast(0)
        state.hardwareDelayDotted = dotted && state.hardwareDelayKind == CompiledOpnaSong.HW_LFO_DELAY_NOTE_LENGTH
    }

    /** Resolves the preserved PMD delay unit against the tempo in force at key-on. */
    fun hardwareLfoDelayFrames(part: Int): Int =
        selectedPart(fmParts, part)?.hardwareLfoDelayFrames() ?: 0

    fun noteOnFm(part: Int) {
        selectedPart(fmParts, part)?.noteOn()
    }

    fun noteOnSsg(part: Int) {
        selectedPart(ssgParts, part)?.noteOn()
    }

    fun noteOnFm3(part: Int) {
        selectedPart(fm3Parts, part)?.noteOn()
    }

    /** Returns true while a configured software-envelope release must keep the chip voice alive. */
    fun noteOffSsg(part: Int): Boolean {
        val envelope = selectedEnvelope(part) ?: return false
        if (!envelope.enabled) return false
        envelope.noteOff()
        return true
    }

    fun configureFmLfo(part: Int, index: Int, delay: Int, speed: Int, depthA: Int, depthB: Int) {
        selectedLfo(fmParts, part, index)?.configure(delay, speed, depthA, depthB)
    }

    fun configureSsgLfo(part: Int, index: Int, delay: Int, speed: Int, depthA: Int, depthB: Int) {
        selectedLfo(ssgParts, part, index)?.configure(delay, speed, depthA, depthB)
    }

    fun configureFm3Lfo(part: Int, index: Int, delay: Int, speed: Int, depthA: Int, depthB: Int) {
        selectedLfo(fm3Parts, part, index)?.configure(delay, speed, depthA, depthB)
    }

    fun setFmLfoSwitch(part: Int, index: Int, value: Int) =
        selectedLfo(fmParts, part, index)?.setSwitch(value) ?: Unit

    fun setSsgLfoSwitch(part: Int, index: Int, value: Int) =
        selectedLfo(ssgParts, part, index)?.setSwitch(value) ?: Unit

    fun setFm3LfoSwitch(part: Int, index: Int, value: Int) =
        selectedLfo(fm3Parts, part, index)?.setSwitch(value) ?: Unit

    fun setFmLfoWaveform(part: Int, index: Int, value: Int) =
        selectedLfo(fmParts, part, index)?.setWaveform(value) ?: Unit

    fun setSsgLfoWaveform(part: Int, index: Int, value: Int) =
        selectedLfo(ssgParts, part, index)?.setWaveform(value) ?: Unit

    fun setFm3LfoWaveform(part: Int, index: Int, value: Int) =
        selectedLfo(fm3Parts, part, index)?.setWaveform(value) ?: Unit

    fun setFmLfoClockMode(part: Int, index: Int, value: Int) =
        selectedLfo(fmParts, part, index)?.setClockMode(value) ?: Unit

    fun setSsgLfoClockMode(part: Int, index: Int, value: Int) =
        selectedLfo(ssgParts, part, index)?.setClockMode(value) ?: Unit

    fun setFm3LfoClockMode(part: Int, index: Int, value: Int) =
        selectedLfo(fm3Parts, part, index)?.setClockMode(value) ?: Unit

    fun setFmLfoTlMask(part: Int, index: Int, value: Int) =
        selectedLfo(fmParts, part, index)?.setTlMask(value) ?: Unit

    fun setFm3LfoTlMask(part: Int, index: Int, value: Int) =
        selectedLfo(fm3Parts, part, index)?.setTlMask(value) ?: Unit

    fun setFmLfoDepthEvolution(part: Int, index: Int, speed: Int, depth: Int, time: Int) =
        selectedLfo(fmParts, part, index)?.setDepthEvolution(speed, depth, time) ?: Unit

    fun setSsgLfoDepthEvolution(part: Int, index: Int, speed: Int, depth: Int, time: Int) =
        selectedLfo(ssgParts, part, index)?.setDepthEvolution(speed, depth, time) ?: Unit

    fun setFm3LfoDepthEvolution(part: Int, index: Int, speed: Int, depth: Int, time: Int) =
        selectedLfo(fm3Parts, part, index)?.setDepthEvolution(speed, depth, time) ?: Unit

    fun setVolume(part: Int, value: Int) {
        if (part in fm3Parts.indices) fm3Parts[part].volume = value.coerceIn(0, 127)
    }

    fun setSlotMask(part: Int, value: Int) {
        if (part in fm3Parts.indices) fm3Parts[part].slotMask = value and 15
    }

    internal fun fmLfoValueSnapshot(part: Int, index: Int): Int =
        selectedLfo(fmParts, part, index)?.valueSnapshot() ?: 0

    internal fun ssgLfoValueSnapshot(part: Int, index: Int): Int =
        selectedLfo(ssgParts, part, index)?.valueSnapshot() ?: 0

    internal fun fm3LfoTlMaskSnapshot(part: Int, index: Int): Int =
        selectedLfo(fm3Parts, part, index)?.tlMask() ?: 0

    internal fun ssgEnvelopeLevelOffsetSnapshot(part: Int): Int {
        if (part !in ssgParts.indices) return 0
        val state = ssgParts[part]
        return state.envelope?.levelOffsetSnapshot(state.ssgBaseLevel) ?: 0
    }

    internal fun volumeSnapshot(part: Int): Int =
        if (part in fm3Parts.indices) fm3Parts[part].volume else 0

    internal fun hardwareLfoPmsSnapshot(part: Int): Int =
        selectedPart(fmParts, part)?.hardwarePms ?: 0

    internal fun hardwareLfoAmsSnapshot(part: Int): Int =
        selectedPart(fmParts, part)?.hardwareAms ?: 0

    internal fun hardwareLfoDelayKindSnapshot(part: Int): Int =
        selectedPart(fmParts, part)?.hardwareDelayKind ?: CompiledOpnaSong.HW_LFO_DELAY_NONE

    internal fun hardwareLfoDelayValueSnapshot(part: Int): Int =
        selectedPart(fmParts, part)?.hardwareDelayValue ?: 0

    internal fun hardwareLfoDelayDottedSnapshot(part: Int): Boolean =
        selectedPart(fmParts, part)?.hardwareDelayDotted ?: false

    private fun selectedEnvelope(part: Int): PmdSoftwareEnvelope? =
        if (part in ssgParts.indices) ssgParts[part].envelope else null

    private fun resetParts(parts: Array<PmdLogicalPartState>, hasSlotMasks: Boolean) {
        var part = 0
        while (part < parts.size) {
            parts[part].reset(if (hasSlotMasks) 1 shl part else 0)
            part++
        }
    }

    private fun setTempo(parts: Array<PmdLogicalPartState>, bpmMilli: Int, clocksPerQuarter: Int) {
        var part = 0
        while (part < parts.size) {
            parts[part].setTempo(bpmMilli, clocksPerQuarter)
            part++
        }
    }

    private fun prepareParts(parts: Array<PmdLogicalPartState>, frames: Int) {
        var part = 0
        while (part < parts.size) {
            parts[part].prepare(frames)
            part++
        }
    }

    private fun selectedPart(parts: Array<PmdLogicalPartState>, part: Int): PmdLogicalPartState? =
        if (part in parts.indices) parts[part] else null

    private fun selectedLfo(
        parts: Array<PmdLogicalPartState>,
        part: Int,
        index: Int
    ): PmdSoftwareLfo? {
        val state = selectedPart(parts, part) ?: return null
        return when (index) {
            0 -> state.lfo1
            1 -> state.lfo2
            else -> null
        }
    }

    private class PmdLogicalPartState(
        private val sampleRate: Int,
        randomSeed: Int,
        withEnvelope: Boolean
    ) {
        val lfo1 = PmdSoftwareLfo(sampleRate, randomSeed)
        val lfo2 = PmdSoftwareLfo(sampleRate, randomSeed xor SECOND_LFO_SEED_XOR)
        val envelope = if (withEnvelope) PmdSoftwareEnvelope(sampleRate) else null
        val modulation = PmdModulationFrame()
        val ssgFrame = if (withEnvelope) PmdSsgFrame() else null
        var volume = 127
        var slotMask = 0
        var ssgBaseLevel = DEFAULT_SSG_LEVEL
        var hardwarePms = 0
        var hardwareAms = 0
        var hardwareDelayKind = CompiledOpnaSong.HW_LFO_DELAY_NONE
        var hardwareDelayValue = 0
        var hardwareDelayDotted = false
        private var tempoMilliBpm = 120_000
        private var clocksPerQuarter = PmdPerformanceLaws.DEFAULT_CLOCKS_PER_QUARTER

        fun setTempo(bpmMilli: Int, clocksPerQuarter: Int) {
            tempoMilliBpm = bpmMilli.coerceAtLeast(1)
            this.clocksPerQuarter = clocksPerQuarter.coerceAtLeast(1)
            lfo1.setTempo(tempoMilliBpm, this.clocksPerQuarter)
            lfo2.setTempo(tempoMilliBpm, this.clocksPerQuarter)
            envelope?.setTempo(tempoMilliBpm, this.clocksPerQuarter)
        }

        fun noteOn() {
            lfo1.noteOn()
            lfo2.noteOn()
            envelope?.noteOn()
        }

        fun prepare(frames: Int) {
            val output = modulation
            output.pitchTarget1 = lfo1.targetsPitch
            output.pitchTarget2 = lfo2.targetsPitch
            output.volumeTarget1 = lfo1.targetsVolume
            output.volumeTarget2 = lfo2.targetsVolume
            output.tlMask1 = lfo1.tlMask()
            output.tlMask2 = lfo2.tlMask()
            output.baseAttenuation = (127 - volume) * OpnRateEnvelope.MAX_ATTENUATION / 127
            output.hardwarePms = hardwarePms
            output.hardwareAms = hardwareAms
            val selectedSsg = ssgFrame
            var frame = 0
            while (frame < frames) {
                val pitch1 = lfo1.pitchValue()
                val pitch2 = lfo2.pitchValue()
                val volume1 = lfo1.volumeValue()
                val volume2 = lfo2.volumeValue()
                output.pitch1Q20[frame] = pitch1 shl 9
                output.pitch2Q20[frame] = pitch2 shl 9
                output.volume1[frame] = volume1
                output.volume2[frame] = volume2
                if (selectedSsg != null) {
                    val selectedEnvelope = envelope
                    selectedSsg.tonePeriodOffset[frame] = pitch1 + pitch2
                    selectedSsg.volumeOffset[frame] = volume1 + volume2
                    selectedSsg.softwareEnvelopeLevel[frame] =
                        selectedEnvelope?.levelFor(ssgBaseLevel) ?: ssgBaseLevel
                    selectedEnvelope?.advanceSample()
                    selectedSsg.releaseFinished[frame] = selectedEnvelope?.finishedRelease() == true
                }
                lfo1.advanceSample()
                lfo2.advanceSample()
                frame++
            }
        }

        fun reset(initialSlotMask: Int) {
            lfo1.reset()
            lfo2.reset()
            envelope?.reset()
            volume = 127
            slotMask = initialSlotMask
            ssgBaseLevel = DEFAULT_SSG_LEVEL
            hardwarePms = 0
            hardwareAms = 0
            hardwareDelayKind = CompiledOpnaSong.HW_LFO_DELAY_NONE
            hardwareDelayValue = 0
            hardwareDelayDotted = false
            tempoMilliBpm = 120_000
            clocksPerQuarter = PmdPerformanceLaws.DEFAULT_CLOCKS_PER_QUARTER
            modulation.clear()
            ssgFrame?.clear(ssgBaseLevel)
        }

        fun hardwareLfoDelayFrames(): Int {
            if (hardwareDelayValue <= 0) return 0
            val numerator: Long
            val denominator: Long
            if (hardwareDelayKind == CompiledOpnaSong.HW_LFO_DELAY_RAW_CLOCKS) {
                numerator = sampleRate.toLong() * 60_000L * hardwareDelayValue.toLong()
                denominator = tempoMilliBpm.toLong() * clocksPerQuarter.toLong()
            } else if (hardwareDelayKind == CompiledOpnaSong.HW_LFO_DELAY_NOTE_LENGTH) {
                var top = sampleRate.toLong() * 240_000L
                var bottom = tempoMilliBpm.toLong() * hardwareDelayValue.toLong()
                if (hardwareDelayDotted) {
                    top *= 3L
                    bottom *= 2L
                }
                numerator = top
                denominator = bottom
            } else {
                return 0
            }
            if (denominator <= 0L) return 0
            return (numerator / denominator).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private companion object {
        const val FM_SEED_FAMILY = 0x13579BDF
        const val SSG_SEED_FAMILY = 0x2468ACE
        const val FM3_SEED_FAMILY = 0x10203040
        const val SECOND_LFO_SEED_XOR = 0x2468ACE0
        const val PART_SEED_STEP = 0x10203
        const val DEFAULT_SSG_LEVEL = 12

        fun seedFor(family: Int, part: Int): Int =
            PmdPerformanceLaws.SOFTWARE_LFO_RANDOM_SEED xor family xor (part * PART_SEED_STEP)
    }
}
