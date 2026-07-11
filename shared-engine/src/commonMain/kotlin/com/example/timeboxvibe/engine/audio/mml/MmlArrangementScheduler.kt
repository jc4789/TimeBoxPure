package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank

object MmlArrangementScheduler {
    const val MIX_GAIN: Float = 0.75f

    fun schedule(
        arrangement: ArrangementLanes,
        synth: OpnaLikeSynthesizer,
        sequencer: OpnaSequencer,
        sampleRate: Int
    ) {
        val compiled = requireNotNull(arrangement.compiledOpnaSong) {
            "MML playback requires the unified CompiledOpnaSong event program"
        }
        scheduleCompiled(compiled, synth, sequencer, sampleRate)
    }

    private fun scheduleCompiled(
        song: CompiledOpnaSong,
        synth: OpnaLikeSynthesizer,
        sequencer: OpnaSequencer,
        sampleRate: Int
    ) {
        synth.lfo.enabled = song.lfoRate in 0..7
        if (song.lfoRate in 0..7) synth.lfo.rate = song.lfoRate
        var i = 0
        while (i < song.eventCount) {
            val eventTick = song.startTick[i]
            val start = ticksToSamples(song, eventTick, sampleRate)
            val duration = ticksToSamples(song, eventTick + song.durationTick[i], sampleRate) - start
            val gate = ticksToSamples(song, eventTick + song.gateTick[i], sampleRate) - start
            val velocity = song.velocity[i].coerceIn(0, 127) / 127f * MIX_GAIN * song.playbackGain
            when (song.eventType[i]) {
                CompiledOpnaSong.FM_NOTE -> {
                    val patch = OpnaPatchBank.fmPatch(song.patchId[i])
                    if (patch != null) {
                        val pms = if (song.pms[i] >= 0) song.pms[i] else patch.pms
                        val ams = if (song.ams[i] >= 0) song.ams[i] else patch.ams
                        sequencer.noteFmControlledRaw(
                            song.channel[i],
                            song.midi[i],
                            start,
                            gate,
                            velocity,
                            null,
                            null,
                            null,
                            null,
                            patch,
                            song.pan[i],
                            song.detuneCents[i],
                            pms,
                            ams,
                            (ticksToSamples(song, eventTick + song.lfoDelayTick[i], sampleRate) - start).toInt(),
                            song.targetMidi[i],
                            if (song.targetMidi[i] >= 0) duration.toInt() else 0
                        )
                    }
                }
                CompiledOpnaSong.FM_POLY_NOTE -> {
                    val patch = OpnaPatchBank.fmPatch(song.patchId[i])
                    if (patch != null) {
                        val pms = if (song.pms[i] >= 0) song.pms[i] else patch.pms
                        val ams = if (song.ams[i] >= 0) song.ams[i] else patch.ams
                        sequencer.noteFmPolyControlledRaw(
                            song.channel[i],
                            song.midi[i],
                            start,
                            gate,
                            velocity,
                            patch,
                            song.pan[i],
                            song.detuneCents[i],
                            pms,
                            ams,
                            (ticksToSamples(song, eventTick + song.lfoDelayTick[i], sampleRate) - start).toInt()
                        )
                    }
                }
                CompiledOpnaSong.SSG_NOTE -> {
                    val patch = OpnaPatchBank.ssgPatch(song.patchId[i])
                    if (patch != null) {
                        sequencer.noteSsgControlledRaw(
                            song.channel[i],
                            song.midi[i],
                            start,
                            gate,
                            velocity,
                            0.5f,
                            patch = patch,
                            pan = song.pan[i],
                            detuneCents = song.detuneCents[i],
                            targetMidi = song.targetMidi[i],
                            slideFrames = if (song.targetMidi[i] >= 0) duration.toInt() else 0
                        )
                    }
                }
                CompiledOpnaSong.RHYTHM_SHOT -> {
                    val kind = ProceduralDrums.DrumKind.entries[song.midi[i].coerceIn(0, ProceduralDrums.DrumKind.entries.lastIndex)]
                    sequencer.noteDrumControlledRaw(kind, start, velocity, song.pan[i])
                }
                CompiledOpnaSong.FM3_OPERATOR_NOTE -> {
                    val patch = OpnaPatchBank.fmPatch(song.patchId[i])
                    if (patch != null) {
                        val pms = if (song.pms[i] >= 0) song.pms[i] else patch.pms
                        val ams = if (song.ams[i] >= 0) song.ams[i] else patch.ams
                        sequencer.noteFm3OperatorRaw(
                            song.operator[i],
                            song.midi[i],
                            start,
                            gate,
                            velocity,
                            patch,
                            song.pan[i],
                            song.detuneCents[i],
                            pms,
                            ams,
                            (ticksToSamples(song, eventTick + song.lfoDelayTick[i], sampleRate) - start).toInt(),
                            song.targetMidi[i],
                            if (song.targetMidi[i] >= 0) duration.toInt() else 0
                        )
                    }
                }
            }
            i++
        }
        sequencer.customLoopLength = ticksToSamples(song, song.durationTicks, sampleRate)
    }

    private fun ticksToSamples(song: CompiledOpnaSong, ticks: Long, sampleRate: Int): Long {
        var samples = 0.0
        var previousTick = 0L
        var currentBpm = song.bpm
        var i = 0
        while (i < song.tempoChangeCount && song.tempoTick[i] < ticks) {
            val changeTick = song.tempoTick[i].coerceAtLeast(previousTick)
            samples += tickSpanToSamples(changeTick - previousTick, currentBpm, sampleRate)
            previousTick = changeTick
            currentBpm = song.tempoBpm[i]
            i++
        }
        samples += tickSpanToSamples(ticks - previousTick, currentBpm, sampleRate)
        return samples.toLong()
    }

    private fun tickSpanToSamples(ticks: Long, bpm: Float, sampleRate: Int): Double =
        ticks.toDouble() * sampleRate.toDouble() * 60.0 /
            (bpm.toDouble() * CompiledOpnaSong.TICKS_PER_QUARTER.toDouble())

}
