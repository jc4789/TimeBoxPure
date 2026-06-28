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
        renderSegment(buffer, 0, frames)
        softClipAndGain(buffer, frames)
    }

    fun render(buffer: FloatArray, frames: Int, sequencer: OpnaSequencer, currentSampleOffset: Long) {
        buffer.fill(0f)
        renderWithSequencer(buffer, frames, sequencer, currentSampleOffset)
        softClipAndGain(buffer, frames)
    }

    private fun renderSegment(buffer: FloatArray, startFrame: Int, frames: Int) {
        if (frames <= 0) return
        var i = 0
        while (i < ssg.size) {
            ssg[i].render(buffer, frames, sampleRate, mixer.ssgGain, startFrame)
            i++
        }
        i = 0
        while (i < fm.size) {
            fm[i].render(buffer, frames, sampleRate, mixer.fmGain, startFrame)
            i++
        }
        drums.render(buffer, frames, sampleRate, mixer.rhythmGain, startFrame)
    }

    private fun renderWithSequencer(
        buffer: FloatArray,
        frames: Int,
        sequencer: OpnaSequencer,
        currentSampleOffset: Long
    ) {
        val chunkEnd = currentSampleOffset + frames
        var renderPos = 0

        var fmIdx = 0
        var ssgIdx = 0
        var drumIdx = 0
        var fmPhase = 0
        var ssgPhase = 0

        while (fmIdx < sequencer.fmEventCount) {
            val end = sequencer.fmStartSample[fmIdx] + sequencer.fmDurationSamp[fmIdx]
            if (end > currentSampleOffset) {
                if (sequencer.fmStartSample[fmIdx] < currentSampleOffset) {
                    fmPhase = 1
                }
                break
            }
            fmIdx++
        }
        while (ssgIdx < sequencer.ssgEventCount) {
            val end = sequencer.ssgStartSample[ssgIdx] + sequencer.ssgDurSamp[ssgIdx]
            if (end > currentSampleOffset) {
                if (sequencer.ssgStartSample[ssgIdx] < currentSampleOffset) {
                    ssgPhase = 1
                }
                break
            }
            ssgIdx++
        }
        while (drumIdx < sequencer.drumEventCount) {
            if (sequencer.drumStartSample[drumIdx] >= currentSampleOffset) break
            drumIdx++
        }

        while (renderPos < frames) {
            var nextTime = Long.MAX_VALUE
            var applyFmOn = false
            var applyFmOff = false
            var applySsgOn = false
            var applySsgOff = false
            var applyDrum = false

            if (fmIdx < sequencer.fmEventCount) {
                val evTime = if (fmPhase == 0) {
                    sequencer.fmStartSample[fmIdx]
                } else {
                    sequencer.fmStartSample[fmIdx] + sequencer.fmDurationSamp[fmIdx]
                }
                if (evTime >= currentSampleOffset && evTime < chunkEnd) {
                    if (evTime < nextTime) {
                        nextTime = evTime
                        applyFmOn = fmPhase == 0
                        applyFmOff = fmPhase == 1
                        applySsgOn = false
                        applySsgOff = false
                        applyDrum = false
                    } else if (evTime == nextTime) {
                        if (fmPhase == 0) applyFmOn = true else applyFmOff = true
                    }
                }
            }

            if (ssgIdx < sequencer.ssgEventCount) {
                val evTime = if (ssgPhase == 0) {
                    sequencer.ssgStartSample[ssgIdx]
                } else {
                    sequencer.ssgStartSample[ssgIdx] + sequencer.ssgDurSamp[ssgIdx]
                }
                if (evTime >= currentSampleOffset && evTime < chunkEnd) {
                    if (evTime < nextTime) {
                        nextTime = evTime
                        applyFmOn = false
                        applyFmOff = false
                        applySsgOn = ssgPhase == 0
                        applySsgOff = ssgPhase == 1
                        applyDrum = false
                    } else if (evTime == nextTime) {
                        if (ssgPhase == 0) applySsgOn = true else applySsgOff = true
                    }
                }
            }

            if (drumIdx < sequencer.drumEventCount) {
                val evTime = sequencer.drumStartSample[drumIdx]
                if (evTime >= currentSampleOffset && evTime < chunkEnd) {
                    if (evTime < nextTime) {
                        nextTime = evTime
                        applyFmOn = false
                        applyFmOff = false
                        applySsgOn = false
                        applySsgOff = false
                        applyDrum = true
                    } else if (evTime == nextTime) {
                        applyDrum = true
                    }
                }
            }

            if (nextTime == Long.MAX_VALUE) {
                renderSegment(buffer, renderPos, frames - renderPos)
                break
            }

            val eventOffset = (nextTime - currentSampleOffset).toInt()
            if (eventOffset > renderPos) {
                renderSegment(buffer, renderPos, eventOffset - renderPos)
            }
            renderPos = eventOffset

            if (applyFmOn) {
                val ch = sequencer.fmChannelIdx[fmIdx]
                val midi = sequencer.fmMidi[fmIdx]
                val a = if (sequencer.fmAttack[fmIdx] > 0f) sequencer.fmAttack[fmIdx] else null
                val d = if (sequencer.fmDecay[fmIdx] > 0f) sequencer.fmDecay[fmIdx] else null
                val s = if (sequencer.fmSustain[fmIdx] > 0f) sequencer.fmSustain[fmIdx] else null
                val r = if (sequencer.fmRelease[fmIdx] > 0f) sequencer.fmRelease[fmIdx] else null
                if (ch in fm.indices) {
                    fm[ch].noteOn(midi, a, d, s, r)
                    fm[ch].noteGain = sequencer.fmVelocity[fmIdx]
                }
                fmPhase = 1
            }
            if (applyFmOff) {
                val ch = sequencer.fmChannelIdx[fmIdx]
                if (ch in fm.indices) {
                    fm[ch].noteOff()
                }
                fmIdx++
                fmPhase = 0
            }
            if (applySsgOn) {
                val ch = sequencer.ssgChannelIdx[ssgIdx]
                if (ch in ssg.indices) {
                    ssg[ch].noteOn(midiToFreq(sequencer.ssgMidi[ssgIdx]))
                    ssg[ch].noteGain = sequencer.ssgVelocity[ssgIdx]
                }
                ssgPhase = 1
            }
            if (applySsgOff) {
                val ch = sequencer.ssgChannelIdx[ssgIdx]
                if (ch in ssg.indices) {
                    ssg[ch].noteOff()
                }
                ssgIdx++
                ssgPhase = 0
            }
            if (applyDrum) {
                triggerDrum(sequencer.drumKind[drumIdx], sequencer.drumVelocity[drumIdx])
                drumIdx++
            }
        }
    }

    private fun softClipAndGain(buffer: FloatArray, frames: Int) {
        val outputGain = AudioLaws.OPNA_OUTPUT_GAIN
        var i = 0
        while (i < frames) {
            val x = buffer[i] * outputGain
            val clipped = x / (1f + abs(x))
            buffer[i] = clipped.coerceIn(-1f, 1f)
            i++
        }
    }
}
