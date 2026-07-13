package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaPlayer
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimelineFactory

object MmlArrangementScheduler {
    const val MIX_GAIN: Float = 0.75f

    fun createPlayer(
        arrangement: ArrangementLanes,
        synth: OpnaLikeSynthesizer,
        sampleRate: Int
    ): CompiledOpnaPlayer {
        val compiled = requireNotNull(arrangement.compiledOpnaSong) {
            "MML playback requires the unified CompiledOpnaSong event program"
        }
        configureGlobalState(compiled, synth)
        return CompiledOpnaPlayer(
            CompiledOpnaTimelineFactory.build(compiled, sampleRate, MIX_GAIN)
        )
    }

    private fun configureGlobalState(song: CompiledOpnaSong, synth: OpnaLikeSynthesizer) {
        synth.lfo.enabled = song.lfoRate in 0..7
        if (song.lfoRate in 0..7) synth.lfo.rate = song.lfoRate
        var channel = 0
        while (channel < synth.ssg.size) {
            synth.ssg[channel].setSoftwareEnvelopeTempo(song.bpmMilli, song.pmdClocksPerQuarter)
            channel++
        }
    }

}
