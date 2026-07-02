package com.example.timeboxvibe.engine.audio.opna

internal class OperatorState {
    var phase: UInt = 0u
    var phaseStep: UInt = 0u
    var tl: Int = 127
    var outputLevel: Float = 1f
    var prevOutput: Int = 0
    val envelope: Envelope = Envelope()
    val opnEnvelope: OpnRateEnvelope = OpnRateEnvelope()
    var egMode: EgMode = EgMode.LEGACY_ADSR

    fun reset() {
        phase = 0u
        phaseStep = 0u
        outputLevel = 1f
        prevOutput = 0
        envelope.reset()
        opnEnvelope.reset()
        egMode = EgMode.LEGACY_ADSR
    }
}
