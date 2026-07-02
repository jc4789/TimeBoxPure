package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.Lane
import com.example.timeboxvibe.engine.TimbreRef
import com.example.timeboxvibe.engine.audio.opna.LlsPatches
import com.example.timeboxvibe.engine.audio.opna.OpnaAudioConstants
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
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
