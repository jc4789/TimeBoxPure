package com.example.timeboxvibe.engine.audio.opna

internal class OperatorState {
    var phase: Int = 0
    var phaseStep: Int = 0
    var outputLevel: Float = 1f
    var prevOutput: Int = 0
    val envelope: Envelope = Envelope()
    val opnEnvelope: OpnRateEnvelope = OpnRateEnvelope()
    var egMode: EgMode = EgMode.LEGACY_ADSR

    fun reset() {
        phase = 0
        phaseStep = 0
        outputLevel = 1f
        prevOutput = 0
        envelope.reset()
        opnEnvelope.reset()
        egMode = EgMode.LEGACY_ADSR
    }
}
