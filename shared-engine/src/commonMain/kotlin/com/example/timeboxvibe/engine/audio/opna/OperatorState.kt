package com.example.timeboxvibe.engine.audio.opna

internal class OperatorState {
    var phase: Float = 0f
    var phaseStep: Float = 0f
    var outputLevel: Float = 1f
    var prevOutput: Float = 0f
    val envelope: Envelope = Envelope()
    val opnEnvelope: OpnRateEnvelope = OpnRateEnvelope()
    var egMode: EgMode = EgMode.LEGACY_ADSR

    fun reset() {
        phase = 0f
        phaseStep = 0f
        outputLevel = 1f
        prevOutput = 0f
        envelope.reset()
        opnEnvelope.reset()
        egMode = EgMode.LEGACY_ADSR
    }
}
