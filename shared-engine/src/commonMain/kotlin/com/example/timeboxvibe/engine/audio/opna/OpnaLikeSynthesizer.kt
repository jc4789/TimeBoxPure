package com.example.timeboxvibe.engine.audio.opna

import com.example.timeboxvibe.engine.audio.AudioLaws
import com.example.timeboxvibe.engine.audio.midiToFreq
import kotlin.math.abs

class OpnaLikeSynthesizer(val sampleRate: Int = AudioLaws.SAMPLE_RATE) {
    internal val mixer = OpnaMixer(sampleRate)
    internal val ssg: Array<SsgVoice> = Array(AudioLaws.SSG_CHANNELS) { SsgVoice(it) }
    val fm: Array<Fm4OpVoice> = Array(AudioLaws.FM_CHANNELS) { Fm4OpVoice() }
    val drums = ProceduralDrums()

    fun noteOnSsg(channel: Int, midi: Int) {
        if (channel in ssg.indices) {
            ssg[channel].noteOn(midiToFreq(midi))
        }
    }

    fun noteOffSsg(channel: Int) {
        if (channel in ssg.indices) {
            ssg[channel].noteOff()
        }
    }

    fun noteOnFm(channel: Int, midi: Int) {
        noteOnFm(channel, midi, null, null, null, null)
    }

    fun noteOnFm(
        channel: Int,
        midi: Int,
        attack: Float?,
        decay: Float?,
        sustain: Float?,
        release: Float?
    ) {
        if (channel in fm.indices) {
            fm[channel].noteOn(midi, attack, decay, sustain, release)
        }
    }

    fun noteOnFm(channel: Int, midi: Int, patch: FmPatch) {
        if (channel in fm.indices) {
            fm[channel].applyPatch(patch)
            fm[channel].noteOn(midi)
        }
    }

    fun noteOffFm(channel: Int) {
        if (channel in fm.indices) {
            fm[channel].noteOff()
        }
    }

    fun triggerDrum(kind: Int, velocity: Float = 1f) {
        when (kind) {
            0, ProceduralDrums.DrumKind.KICK.ordinal -> {
                drums.kickGain = velocity
                drums.triggerKick()
            }
            1, ProceduralDrums.DrumKind.SNARE.ordinal -> {
                drums.snareGain = velocity
                drums.triggerSnare()
            }
            2, ProceduralDrums.DrumKind.HAT.ordinal -> {
                drums.hatGain = velocity
                drums.triggerHat()
            }
            3, ProceduralDrums.DrumKind.TOM.ordinal -> {
                drums.tomGain = velocity
                drums.triggerTom(150f)
            }
        }
    }

    fun allNotesOff() {
        var i = 0
        while (i < ssg.size) {
            ssg[i].noteOff()
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].noteOff()
            i++
        }
        drums.stopAll()
    }

    fun reset() {
        var i = 0
        while (i < ssg.size) {
            ssg[i].reset()
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].reset()
            i++
        }
        drums.reset()
    }

    fun render(buffer: FloatArray, frames: Int) {
        buffer.fill(0f)

        var i = 0
        while (i < ssg.size) {
            ssg[i].render(buffer, frames, sampleRate, mixer.ssgGain)
            i++
        }

        i = 0
        while (i < fm.size) {
            fm[i].render(buffer, frames, sampleRate, mixer.fmGain)
            i++
        }

        drums.render(buffer, frames, sampleRate, mixer.rhythmGain)

        softClipAndGain(buffer, frames)
    }

    fun render(buffer: FloatArray, frames: Int, sequencer: OpnaSequencer, currentSampleOffset: Long) {
        sequencer.writeInto(this, currentSampleOffset, frames)
        render(buffer, frames)
    }

    private fun softClipAndGain(buffer: FloatArray, frames: Int) {
        val masterGain = OpnaAudioConstants.MASTER_GAIN
        var i = 0
        while (i < frames) {
            val x = buffer[i] * masterGain
            val clipped = x / (1f + abs(x))
            buffer[i] = clipped.coerceIn(-1f, 1f)
            i++
        }
    }
}
