package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaPlayer
import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaTimelineFactory
import com.example.timeboxvibe.engine.audio.opna.OpnaPatchBank
import com.example.timeboxvibe.engine.audio.opna.PmdSampleClock

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
        configureGlobalState(song, synth)
        configureLegacyEnvelopeControls(song, synth)
        var i = 0
        while (i < song.eventCount) {
            val eventTick = song.startTick[i]
            val start = ticksToSamples(song, eventTick, sampleRate)
            val duration = ticksToSamples(song, eventTick + song.durationTick[i], sampleRate) - start
            val gate = ticksToSamples(song, eventTick + song.gateTick[i], sampleRate) - start
            val velocity = song.velocity[i].coerceIn(0, 127) / 127f * MIX_GAIN * song.playbackGain
            when (song.eventType[i]) {
                in CompiledOpnaSong.SOFTWARE_LFO_DEFINE..CompiledOpnaSong.SOFTWARE_LFO_DEPTH -> {
                    sequencer.softwareLfoControlRaw(
                        com.example.timeboxvibe.engine.audio.opna.SequencerEvent.SOFTWARE_LFO_DEFINE +
                            (song.eventType[i] - CompiledOpnaSong.SOFTWARE_LFO_DEFINE),
                        start, song.channel[i], song.operator[i], song.envelopeFormat[i],
                        song.envelopeAttack[i], song.envelopeDecay[i], song.envelopeSustain[i], song.envelopeRelease[i]
                    )
                }
                in CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE..CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY -> {
                    val sourceType = song.eventType[i]
                    val sequencerType = com.example.timeboxvibe.engine.audio.opna.SequencerEvent.FM_SLOT_DETUNE_ABSOLUTE +
                        (sourceType - CompiledOpnaSong.FM_SLOT_DETUNE_ABSOLUTE)
                    val value = if (sourceType == CompiledOpnaSong.FM_SLOT_KEY_ON_DELAY) {
                        (ticksToSamples(song, eventTick + song.envelopeAttack[i], sampleRate) - start).toInt()
                    } else song.envelopeAttack[i]
                    sequencer.fmControlRaw(sequencerType, start, song.channel[i], song.slotMask[i], value)
                }
                CompiledOpnaSong.FM3_PATCH -> {
                    sequencer.fmControlRaw(
                        com.example.timeboxvibe.engine.audio.opna.SequencerEvent.FM3_PATCH,
                        start, 2, song.slotMask[i], 0, OpnaPatchBank.fmPatch(song.patchId[i])
                    )
                }
                in CompiledOpnaSong.RHYTHM_CONTROL_SHOT..CompiledOpnaSong.RHYTHM_VOICE_PAN -> {
                    sequencer.rhythmControlRaw(
                        com.example.timeboxvibe.engine.audio.opna.SequencerEvent.RHYTHM_CONTROL_SHOT +
                            (song.eventType[i] - CompiledOpnaSong.RHYTHM_CONTROL_SHOT),
                        start, song.channel[i], song.midi[i], song.envelopeAttack[i]
                    )
                }
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
                CompiledOpnaSong.SSG_DRUM_SHOT -> {
                    val kind = ProceduralDrums.DrumKind.entries[song.midi[i].coerceIn(0, ProceduralDrums.DrumKind.entries.lastIndex)]
                    sequencer.noteSsgDrumRaw(kind, start, velocity)
                }
                CompiledOpnaSong.FM3_OPERATOR_NOTE -> {
                    val patch = OpnaPatchBank.fmPatch(song.patchId[i])
                    if (patch != null) {
                        val pms = if (song.pms[i] >= 0) song.pms[i] else patch.pms
                        val ams = if (song.ams[i] >= 0) song.ams[i] else patch.ams
                        sequencer.noteFm3SlotsRaw(
                            song.slotMask[i],
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
                            if (song.targetMidi[i] >= 0) duration.toInt() else 0,
                            applyPatch = song.operator[i] < 0
                        )
                    }
                }
            }
            i++
        }
        sequencer.customLoopLength = ticksToSamples(song, song.durationTicks, sampleRate)
    }

    private fun ticksToSamples(song: CompiledOpnaSong, ticks: Long, sampleRate: Int): Long {
        return PmdSampleClock.samplesAt(song, ticks, sampleRate)
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

    private fun configureLegacyEnvelopeControls(song: CompiledOpnaSong, synth: OpnaLikeSynthesizer) {
        var event = 0
        while (event < song.eventCount) {
            val selectedChannel = song.channel[event]
            if (selectedChannel >= 0 && selectedChannel < synth.ssg.size) {
                if (song.eventType[event] == CompiledOpnaSong.SSG_ENVELOPE_DEFINE) {
                    synth.ssg[selectedChannel].configureSoftwareEnvelope(
                        song.envelopeFormat[event], song.envelopeAttack[event], song.envelopeDecay[event],
                        song.envelopeSustain[event], song.envelopeRelease[event],
                        song.envelopeSustainLevel[event], song.envelopeAttackLevel[event]
                    )
                } else if (song.eventType[event] == CompiledOpnaSong.SSG_ENVELOPE_MODE) {
                    synth.ssg[selectedChannel].setSoftwareEnvelopeClockMode(song.envelopeClockMode[event])
                }
            }
            event++
        }
    }

}
