package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaPlayer
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimelineFactory

object MmlArrangementScheduler {
    const val MIX_GAIN: Float = 0.75f

    fun createPlayer(
        arrangement: ArrangementLanes,
        @Suppress("UNUSED_PARAMETER") synth: OpnaLikeSynthesizer,
        sampleRate: Int
    ): CompiledOpnaPlayer = createPlayer(arrangement, sampleRate)

    fun createPlayer(
        arrangement: ArrangementLanes,
        sampleRate: Int
    ): CompiledOpnaPlayer {
        val compiled = requireNotNull(arrangement.compiledOpnaSong) {
            "MML playback requires the unified CompiledOpnaSong event program"
        }
        return CompiledOpnaPlayer(
            CompiledOpnaTimelineFactory.build(compiled, sampleRate, MIX_GAIN)
        )
    }

}
