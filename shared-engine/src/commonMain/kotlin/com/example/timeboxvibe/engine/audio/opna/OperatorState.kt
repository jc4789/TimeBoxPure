package com.example.timeboxvibe.engine.audio.opna

internal class OperatorState {
    var phase: Double = 0.0
    var phaseStep: Double = 0.0
    var outputLevel: Float = 1f
    var modulationIndex: Float = 0f
    var prevOutput: Float = 0f
    val envelope: Envelope = Envelope()
    val opnEnvelope: OpnRateEnvelope = OpnRateEnvelope()
    var egMode: EgMode = EgMode.LEGACY_ADSR

    fun reset() {
        phase = 0.0
        phaseStep = 0.0
        outputLevel = 1f
        modulationIndex = 0f
        prevOutput = 0f
        envelope.reset()
        opnEnvelope.reset()
        egMode = EgMode.LEGACY_ADSR
    }
}
