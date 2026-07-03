package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.Lane
import com.example.timeboxvibe.engine.TimbreRef
import com.example.timeboxvibe.engine.audio.opna.LlsPatches
import com.example.timeboxvibe.engine.audio.opna.OpnaAudioConstants
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import kotlin.math.log2
import kotlin.math.roundToInt

object MmlArrangementScheduler {
    const val MIX_GAIN: Float = 0.75f
    const val FM_ATTACK_SECONDS: Float = 0.008f
    const val FM_RELEASE_MILLISECONDS: Int = 8
    const val FM_RELEASE_SECONDS: Float = 0.008f
    const val SSG_ATTACK_SECONDS: Float = 0.004f
    const val SSG_RELEASE_MILLISECONDS: Int = 4
    const val SSG_RELEASE_SECONDS: Float = 0.004f

    private const val LANE_NONE = 0
    private const val LANE_FM = 1
    private const val LANE_SSG = 2

    fun schedule(
        arrangement: ArrangementLanes,
        synth: OpnaLikeSynthesizer,
        sequencer: OpnaSequencer,
        sampleRate: Int
    ) {
        val compiled = arrangement.compiledOpnaSong
        if (compiled != null) {
            scheduleCompiled(compiled, synth, sequencer, sampleRate)
            return
        }
        synth.lfo.enabled = false
        var fmChannel = 0
        var ssgChannel = 0

        var scheduledType = scheduleLane(
            arrangement.lead,
            OpnaAudioConstants.LANE_GAIN_LEAD,
            synth,
            sequencer,
            sampleRate,
            fmChannel,
            ssgChannel
        )
        if (scheduledType == LANE_FM) fmChannel++ else if (scheduledType == LANE_SSG) ssgChannel++

        scheduledType = scheduleLane(
            arrangement.harmony,
            OpnaAudioConstants.LANE_GAIN_HARMONY,
            synth,
            sequencer,
            sampleRate,
            fmChannel,
            ssgChannel
        )
        if (scheduledType == LANE_FM) fmChannel++ else if (scheduledType == LANE_SSG) ssgChannel++

        scheduledType = scheduleLane(
            arrangement.bass,
            OpnaAudioConstants.LANE_GAIN_BASS,
            synth,
            sequencer,
            sampleRate,
            fmChannel,
            ssgChannel
        )
        if (scheduledType == LANE_FM) fmChannel++ else if (scheduledType == LANE_SSG) ssgChannel++

        scheduledType = scheduleLane(
            arrangement.auxiliary,
            OpnaAudioConstants.LANE_GAIN_HARMONY,
            synth,
            sequencer,
            sampleRate,
            fmChannel,
            ssgChannel
        )
        if (scheduledType == LANE_FM) fmChannel++ else if (scheduledType == LANE_SSG) ssgChannel++

        scheduleLane(
            arrangement.additional,
            OpnaAudioConstants.LANE_GAIN_HARMONY,
            synth,
            sequencer,
            sampleRate,
            fmChannel,
            ssgChannel
        )

        var drumIndex = 0
        while (drumIndex < arrangement.percussion.notes.size) {
            val note = arrangement.percussion.notes[drumIndex]
            val kind = if (note.freq == -1f) {
                ProceduralDrums.DrumKind.KICK
            } else if (note.freq >= 5000f) {
                ProceduralDrums.DrumKind.HAT
            } else {
                ProceduralDrums.DrumKind.SNARE
            }
            sequencer.noteDrumRaw(
                kind,
                millisecondsToSamples(note.startMs, sampleRate),
                note.volume * OpnaAudioConstants.LANE_GAIN_PERCUSSION * MIX_GAIN
            )
            drumIndex++
        }
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
            val velocity = song.velocity[i].coerceIn(0, 127) / 127f * MIX_GAIN
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

    private fun scheduleLane(
        lane: Lane?,
        laneGain: Float,
        synth: OpnaLikeSynthesizer,
        sequencer: OpnaSequencer,
        sampleRate: Int,
        fmChannel: Int,
        ssgChannel: Int
    ): Int {
        if (lane == null || lane.notes.isEmpty()) return LANE_NONE
        val patch = when (lane.timbre) {
            TimbreRef.FM_LLS_AT54 -> LlsPatches.At54
            TimbreRef.FM_LLS_AT74 -> LlsPatches.At74
            TimbreRef.FM_LLS_AT99 -> LlsPatches.At99
            TimbreRef.FM_LLS_AT181 -> LlsPatches.At181
            else -> null
        }
        var noteIndex = 0
        if (patch != null) {
            synth.fm[fmChannel].applyPatch(patch)
            while (noteIndex < lane.notes.size) {
                val note = lane.notes[noteIndex]
                if (note.freq > 10f) {
                    val midi = (12f * log2(note.freq / 440f) + 69f).roundToInt()
                    val gateMs = maxOf(0, note.durationMs - FM_RELEASE_MILLISECONDS)
                    sequencer.noteFmRaw(
                        fmChannel,
                        midi,
                        millisecondsToSamples(note.startMs, sampleRate),
                        millisecondsToSamples(gateMs, sampleRate),
                        note.volume * laneGain * MIX_GAIN,
                        FM_ATTACK_SECONDS,
                        null,
                        null,
                        FM_RELEASE_SECONDS
                    )
                }
                noteIndex++
            }
            return LANE_FM
        }

        while (noteIndex < lane.notes.size) {
            val note = lane.notes[noteIndex]
            if (note.freq > 10f) {
                val midi = (12f * log2(note.freq / 440f) + 69f).roundToInt()
                val gateMs = maxOf(0, note.durationMs - SSG_RELEASE_MILLISECONDS)
                sequencer.noteSsgRaw(
                    ssgChannel,
                    midi,
                    millisecondsToSamples(note.startMs, sampleRate),
                    millisecondsToSamples(gateMs, sampleRate),
                    note.volume * laneGain * MIX_GAIN,
                    dutyFor(note.type),
                    SSG_ATTACK_SECONDS,
                    -1f,
                    -1f,
                    SSG_RELEASE_SECONDS
                )
            }
            noteIndex++
        }
        return LANE_SSG
    }

    private fun millisecondsToSamples(milliseconds: Int, sampleRate: Int): Long =
        milliseconds.toLong() * sampleRate / 1000L

    private fun dutyFor(type: String): Float {
        return when (type) {
            "pulse25" -> 0.25f
            "pulse12" -> 0.125f
            else -> 0.5f
        }
    }
}
