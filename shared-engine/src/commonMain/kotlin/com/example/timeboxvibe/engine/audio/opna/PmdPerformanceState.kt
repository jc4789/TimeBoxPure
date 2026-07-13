package com.example.timeboxvibe.engine.audio.opna

/** Allocation-free PMD driver state which is not a physical YM2608 register. */
internal class PmdPerformanceState(sampleRate: Int) {
    private val fm3Parts = Array(CompiledOpnaSong.FM3_PART_COUNT) { part ->
        PmdFm3PartState(sampleRate, part)
    }

    fun reset() {
        var part = 0
        while (part < fm3Parts.size) {
            fm3Parts[part].reset(part)
            part++
        }
    }

    fun setTempo(bpmMilli: Int, clocksPerQuarter: Int) {
        var part = 0
        while (part < fm3Parts.size) {
            fm3Parts[part].setTempo(bpmMilli, clocksPerQuarter)
            part++
        }
    }

    fun setVolume(part: Int, value: Int) {
        if (part in fm3Parts.indices) fm3Parts[part].volume = value.coerceIn(0, 127)
    }

    fun setSlotMask(part: Int, value: Int) {
        if (part in fm3Parts.indices) fm3Parts[part].slotMask = value and 15
    }

    fun configureLfo(part: Int, index: Int, delay: Int, speed: Int, depthA: Int, depthB: Int) {
        selectedLfo(part, index)?.configure(delay, speed, depthA, depthB)
    }

    fun setLfoSwitch(part: Int, index: Int, value: Int) {
        selectedLfo(part, index)?.setSwitch(value)
    }

    fun setLfoWaveform(part: Int, index: Int, value: Int) {
        selectedLfo(part, index)?.setWaveform(value)
    }

    fun setLfoClockMode(part: Int, index: Int, value: Int) {
        selectedLfo(part, index)?.setClockMode(value)
    }

    fun setLfoTlMask(part: Int, index: Int, value: Int) {
        selectedLfo(part, index)?.setTlMask(value)
    }

    fun setLfoDepthEvolution(part: Int, index: Int, speed: Int, depth: Int, time: Int) {
        selectedLfo(part, index)?.setDepthEvolution(speed, depth, time)
    }

    fun noteOn(part: Int) {
        if (part !in fm3Parts.indices) return
        fm3Parts[part].lfo1.noteOn()
        fm3Parts[part].lfo2.noteOn()
    }

    fun prepareFrame(clockFrame: Boolean) {
        var part = 0
        while (part < fm3Parts.size) {
            val state = fm3Parts[part]
            state.pitch1Q20 = state.lfo1.pitchValue() shl 9
            state.pitch2Q20 = state.lfo2.pitchValue() shl 9
            state.volume1 = state.lfo1.volumeValue()
            state.volume2 = state.lfo2.volumeValue()
            if (clockFrame) {
                state.lfo1.advanceSample()
                state.lfo2.advanceSample()
            }
            part++
        }
    }

    fun partForOperator(operator: Int): Int {
        val bit = 1 shl operator
        var part = 0
        while (part < fm3Parts.size) {
            if ((fm3Parts[part].slotMask and bit) != 0) return part
            part++
        }
        return -1
    }

    fun pitchQ20(part: Int, operator: Int): Int {
        if (part !in fm3Parts.indices) return 0
        val state = fm3Parts[part]
        var result = 0
        if (targetsPitch(state.lfo1, state.slotMask, operator)) result += state.pitch1Q20
        if (targetsPitch(state.lfo2, state.slotMask, operator)) result += state.pitch2Q20
        return result
    }

    fun volumeOffset(part: Int, operator: Int, carrier: Boolean): Int {
        if (part !in fm3Parts.indices) return 0
        val state = fm3Parts[part]
        var result = (127 - state.volume) * OpnRateEnvelope.MAX_ATTENUATION / 127
        if (targetsVolume(state.lfo1, state.slotMask, operator, carrier)) result -= state.volume1 * 8
        if (targetsVolume(state.lfo2, state.slotMask, operator, carrier)) result -= state.volume2 * 8
        return result
    }

    internal fun lfoTlMaskSnapshot(part: Int, index: Int): Int = selectedLfo(part, index)?.tlMask() ?: 0
    internal fun volumeSnapshot(part: Int): Int = if (part in fm3Parts.indices) fm3Parts[part].volume else 0

    private fun targetsPitch(lfo: PmdSoftwareLfo, partMask: Int, operator: Int): Boolean {
        if (!lfo.targetsPitch || (partMask and (1 shl operator)) == 0) return false
        val mask = lfo.tlMask()
        return mask == 0 || (mask and (1 shl operator)) != 0
    }

    private fun targetsVolume(lfo: PmdSoftwareLfo, partMask: Int, operator: Int, carrier: Boolean): Boolean {
        if (!lfo.targetsVolume || (partMask and (1 shl operator)) == 0) return false
        val mask = lfo.tlMask()
        return if (mask == 0) carrier else (mask and (1 shl operator)) != 0
    }

    private fun selectedLfo(part: Int, index: Int): PmdSoftwareLfo? {
        if (part !in fm3Parts.indices) return null
        return if (index == 0) fm3Parts[part].lfo1 else fm3Parts[part].lfo2
    }
}

private class PmdFm3PartState(sampleRate: Int, part: Int) {
    val lfo1 = PmdSoftwareLfo(sampleRate, PmdPerformanceLaws.SOFTWARE_LFO_RANDOM_SEED xor (part * 0x10203))
    val lfo2 = PmdSoftwareLfo(sampleRate, (PmdPerformanceLaws.SOFTWARE_LFO_RANDOM_SEED xor 0x2468ACE0) xor (part * 0x10203))
    var volume = 127
    var slotMask = 1 shl part
    var pitch1Q20 = 0
    var pitch2Q20 = 0
    var volume1 = 0
    var volume2 = 0

    fun setTempo(bpmMilli: Int, clocksPerQuarter: Int) {
        lfo1.setTempo(bpmMilli, clocksPerQuarter)
        lfo2.setTempo(bpmMilli, clocksPerQuarter)
    }

    fun reset(part: Int) {
        lfo1.reset()
        lfo2.reset()
        volume = 127
        slotMask = 1 shl part
        pitch1Q20 = 0
        pitch2Q20 = 0
        volume1 = 0
        volume2 = 0
    }
}
