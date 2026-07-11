package com.example.timeboxvibe.engine.audio.opna

internal class OperatorState {
    var phase: UInt = 0u
    var phaseStep: UInt = 0u
    var tl: Int = 127
    var prevOutput: Int = 0
    var amEnabled: Boolean = false
    val opnEnvelope: OpnRateEnvelope = OpnRateEnvelope()

    fun reset() {
        phase = 0u
        phaseStep = 0u
        prevOutput = 0
        amEnabled = false
        opnEnvelope.reset()
    }
}
