package com.example.timeboxvibe.engine.audio.opna

internal class OperatorState {
    var phase: UInt = 0u
    var phaseStep: UInt = 0u
    var tl: Int = 127
    var prevOutput: Int = 0
    // Retained as the source-compatible ADSR configuration view; FM execution uses opnEnvelope only.
    val envelope: Envelope = Envelope()
    val opnEnvelope: OpnRateEnvelope = OpnRateEnvelope()

    fun reset() {
        phase = 0u
        phaseStep = 0u
        prevOutput = 0
        envelope.reset()
        opnEnvelope.reset()
    }
}
