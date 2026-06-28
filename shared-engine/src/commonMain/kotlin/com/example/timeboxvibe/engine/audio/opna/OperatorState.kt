package com.example.timeboxvibe.engine.audio.opna

internal class OperatorState {
    var phase: Float = 0f
    var phaseStep: Float = 0f
    var outputLevel: Float = 1f
    val envelope: Envelope = Envelope()

    fun reset() {
        phase = 0f
        phaseStep = 0f
        outputLevel = 1f
        envelope.reset()
    }
}
