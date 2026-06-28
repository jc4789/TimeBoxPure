package com.example.timeboxvibe.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.example.timeboxvibe.engine.audio.opna.LlsPatches
import com.example.timeboxvibe.engine.audio.opna.OpnaAudioConstants
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import com.example.timeboxvibe.engine.audio.opna.Patches
import com.example.timeboxvibe.engine.audio.opna.ProceduralDrums
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

object SoundPreviewPlayer {
    private const val GATE_RATIO = 0.85f

    @Volatile
    private var isStreaming = false
    private var streamThread: Thread? = null

    private var activeTrack: AudioTrack? = null
    private var tickTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    fun playPreview(context: Context, soundKey: String, volume: Float) {
        stop()
        when (soundKey) {
            "oriental" -> playOrientalPreview(context, volume)
            else -> playSpecsStreaming(soundKey, volume, shouldLoop = false, stopAfterMs = 7000L)
        }
    }

    fun playTick(volume: Float) {
        try {
            var track = tickTrack
            if (track == null) {
                val sampleRate = 44100
                val durationMs = 35
                val numSamples = sampleRate * durationMs / 1000
                val buffer = FloatArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val freq = 300.0 * exp(-50.0 * t)
                    val phase = 2.0 * Math.PI * freq * t

                    var sample = if (sin(phase) > 0) 0.8f else -0.8f

                    val ageMs = i * 1000 / sampleRate
                    val envelope = when {
                        ageMs < 2 -> ageMs / 2f
                        else -> {
                            val decayFactor = (ageMs - 2) / (durationMs - 2).toFloat()
                            exp(-8.0 * decayFactor).toFloat()
                        }
                    }
                    sample *= envelope * 0.15f * volume
                    buffer[i] = sample.coerceIn(-1.0f, 1.0f)
                }

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 4)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                tickTrack = track
            }

            track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isStreaming = false
        val t = streamThread
        if (t != null) {
            try {
                t.interrupt()
                t.join(1000)
            } catch (e: Exception) {}
            streamThread = null
        }

        try {
            activeTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) { e.printStackTrace() }
        activeTrack = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) { e.printStackTrace() }
        mediaPlayer = null

        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
    }

    private fun playOrientalPreview(context: Context, volume: Float) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val afd = context.assets.openFd("sounds/oriental_alarm.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setVolume(volume, volume)
                prepare()
                start()
                afd.close()
            }
            val runnable = Runnable { stop() }
            stopRunnable = runnable
            handler.postDelayed(runnable, 3500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playGentleReminder(context: Context, soundKey: String, volume: Float) {
        stop()
        when (soundKey) {
            "oriental" -> {
                try {
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        val afd = context.assets.openFd("sounds/oriental_alarm.mp3")
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        setVolume(volume, volume)
                        prepare()
                        start()
                        afd.close()
                    }
                    val runnable = Runnable { stop() }
                    stopRunnable = runnable
                    handler.postDelayed(runnable, 1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            else -> playSpecsStreaming(soundKey, volume, shouldLoop = false, stopAfterMs = 5000L)
        }
    }

    fun playAlarm(context: Context, soundKey: String, volume: Float) {
        stop()
        when (soundKey) {
            "oriental" -> {
                try {
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        val afd = context.assets.openFd("sounds/oriental_alarm.mp3")
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        setVolume(volume, volume)
                        isLooping = true
                        prepare()
                        start()
                        afd.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            else -> playSpecsStreaming(soundKey, volume, shouldLoop = true)
        }
    }

    private fun playSpecsStreaming(soundKey: String, volume: Float, shouldLoop: Boolean, stopAfterMs: Long = -1L) {
        val arrangement = SoundMelodies.getArrangement(soundKey, volume) ?: return

        isStreaming = true

        val thread = Thread(Runnable {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

            val sampleRate = 44100
            val chunkSize = 1024

            val synth = OpnaLikeSynthesizer(sampleRate)
            val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm)

            fun msToSamples(ms: Int): Long = (ms.toLong() * sampleRate) / 1000L

            val leadGain = OpnaAudioConstants.LANE_GAIN_LEAD
            val harmonyGain = OpnaAudioConstants.LANE_GAIN_HARMONY
            val bassGain = OpnaAudioConstants.LANE_GAIN_BASS
            val percussionGain = OpnaAudioConstants.LANE_GAIN_PERCUSSION

            // 1. Lead lane - monophonic on single channel
            val leadTimbre = arrangement.lead.timbre
            val leadPatch = when (leadTimbre) {
                TimbreRef.FM_LEAD_ZUN1 -> Patches.ZunLead1
                TimbreRef.FM_BELL_ZUN1 -> Patches.ZunBell1
                TimbreRef.FM_PAD_ZUN1 -> Patches.ZunPad1
                TimbreRef.FM_LLS_AT54 -> LlsPatches.At54
                TimbreRef.FM_LLS_AT74 -> LlsPatches.At74
                TimbreRef.FM_LLS_AT99 -> LlsPatches.At99
                TimbreRef.FM_LLS_AT181 -> LlsPatches.At181
                else -> null
            }
            val leadChannel = 0
            if (leadPatch != null) {
                synth.fm[leadChannel].applyPatch(leadPatch)
                for (note in arrangement.lead.notes) {
                    if (note.freq <= 10f) continue
                    val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).roundToInt()
                    val velocity = note.volume * leadGain
                    val (a, d, s, r) = adsrFromNote(note)
                    val gateSamples = msToSamples((note.durationMs * GATE_RATIO).toInt())
                    sequencer.noteFmRaw(
                        leadChannel, midi,
                        msToSamples(note.startMs),
                        gateSamples,
                        velocity, a, d, s, r
                    )
                }
            } else {
                for (note in arrangement.lead.notes) {
                    if (note.freq <= 10f) continue
                    val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).roundToInt()
                    val duty = parseDutyCycle(note.type)
                    val gateSamples = msToSamples((note.durationMs * GATE_RATIO).toInt())
                    sequencer.noteSsgRaw(
                        0, midi,
                        msToSamples(note.startMs),
                        gateSamples,
                        note.volume * leadGain,
                        duty
                    )
                }
            }

            // 2. Harmony lane
            for (note in arrangement.harmony.notes) {
                if (note.freq <= 10f) continue
                val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).roundToInt()
                val duty = parseDutyCycle(note.type)
                val gateSamples = msToSamples((note.durationMs * GATE_RATIO).toInt())
                sequencer.noteSsgRaw(
                    1, midi,
                    msToSamples(note.startMs),
                    gateSamples,
                    note.volume * harmonyGain,
                    duty
                )
            }

            // 3. Bass lane - monophonic on single channel
            val bassTimbre = arrangement.bass.timbre
            val bassChannel = 1
            if (bassTimbre == TimbreRef.FM_BASS_ZUN1) {
                synth.fm[bassChannel].applyPatch(Patches.ZunBass1)
                for (note in arrangement.bass.notes) {
                    if (note.freq <= 10f) continue
                    val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).roundToInt()
                    val velocity = note.volume * bassGain
                    val (a, d, s, r) = adsrFromNote(note)
                    val gateSamples = msToSamples((note.durationMs * GATE_RATIO).toInt())
                    sequencer.noteFmRaw(
                        bassChannel, midi,
                        msToSamples(note.startMs),
                        gateSamples,
                        velocity, a, d, s, r
                    )
                }
            } else if (bassTimbre == TimbreRef.SSG_BASS_SQUARE) {
                for (note in arrangement.bass.notes) {
                    if (note.freq <= 10f) continue
                    val midi = (12f * kotlin.math.log2(note.freq / 440f) + 69f).roundToInt()
                    val duty = parseDutyCycle(note.type)
                    val gateSamples = msToSamples((note.durationMs * GATE_RATIO).toInt())
                    sequencer.noteSsgRaw(
                        2, midi,
                        msToSamples(note.startMs),
                        gateSamples,
                        note.volume * bassGain,
                        duty
                    )
                }
            }

            // 4. Percussion lane
            for (note in arrangement.percussion.notes) {
                val kind: ProceduralDrums.DrumKind?
                val velocity: Float
                if (note.freq == -1f) {
                    kind = ProceduralDrums.DrumKind.KICK
                    velocity = note.volume * percussionGain
                } else if (note.freq >= 5000f) {
                    kind = ProceduralDrums.DrumKind.HAT
                    velocity = note.volume * percussionGain
                } else if (note.freq > 0f) {
                    kind = ProceduralDrums.DrumKind.SNARE
                    velocity = note.volume * percussionGain
                } else {
                    continue
                }
                sequencer.noteDrumRaw(
                    kind,
                    msToSamples(note.startMs),
                    velocity
                )
            }

            val maxDurationMs = listOf(
                arrangement.lead.notes.maxOfOrNull { it.startMs + it.durationMs } ?: 0,
                arrangement.harmony.notes.maxOfOrNull { it.startMs + it.durationMs } ?: 0,
                arrangement.bass.notes.maxOfOrNull { it.startMs + it.durationMs } ?: 0,
                arrangement.percussion.notes.maxOfOrNull { it.startMs + it.durationMs } ?: 0
            ).maxOrNull()?.toLong() ?: 0L

            if (maxDurationMs <= 0L) return@Runnable

            sequencer.customLoopLength = msToSamples(maxDurationMs.toInt())

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            val audioTrackBufferSize = maxOf(minBufferSize, chunkSize * 4 * 4 * 2)

            val audioTrack = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(audioTrackBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                e.printStackTrace()
                return@Runnable
            }

            synchronized(this@SoundPreviewPlayer) {
                activeTrack = audioTrack
            }

            try {
                audioTrack.play()
            } catch (e: Exception) {
                e.printStackTrace()
                try { audioTrack.release() } catch (ex: Exception) {}
                synchronized(this@SoundPreviewPlayer) {
                    if (activeTrack == audioTrack) activeTrack = null
                }
                return@Runnable
            }

            val floatBuffer = FloatArray(chunkSize * 2)
            var currentSampleOffset = 0L
            val songLenSamples = sequencer.loopLengthSamples()
            var lastWrappedOffset = 0L

            try {
                while (isStreaming && !Thread.currentThread().isInterrupted) {
                    if (stopAfterMs > 0L) {
                        val elapsedGlobalMs = (currentSampleOffset * 1000L) / sampleRate
                        if (elapsedGlobalMs >= stopAfterMs) {
                            break
                        }
                    } else if (!shouldLoop) {
                        val elapsedGlobalMs = (currentSampleOffset * 1000L) / sampleRate
                        if (elapsedGlobalMs >= maxDurationMs) {
                            break
                        }
                    }

                    var renderOffset = currentSampleOffset
                    if (shouldLoop && songLenSamples > 0L) {
                        renderOffset = currentSampleOffset % songLenSamples
                        if (renderOffset < lastWrappedOffset) {
                            sequencer.nextEventIdx = 0
                            synth.allNotesOff()
                        }
                        lastWrappedOffset = renderOffset
                    }

                    synth.renderStereo(floatBuffer, chunkSize, sequencer, renderOffset)

                    val written = audioTrack.write(floatBuffer, 0, chunkSize * 2, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        break
                    }

                    currentSampleOffset += chunkSize
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { audioTrack.stop() } catch (e: Exception) {}
                try { audioTrack.release() } catch (e: Exception) {}
                synchronized(this@SoundPreviewPlayer) {
                    if (activeTrack == audioTrack) {
                        activeTrack = null
                    }
                }
            }
        }, "SoundStreamThread")

        streamThread = thread
        thread.start()
    }

    private fun parseDutyCycle(type: String): Float {
        return when (type) {
            "square" -> 0.5f
            "pulse25" -> 0.25f
            "pulse12" -> 0.125f
            else -> 0.5f
        }
    }

    private fun adsrFromNote(note: ToneSpec): Quadruple<Float?, Float?, Float?, Float?> {
        if (!note.useADSR) {
            return Quadruple(null, null, null, null)
        }
        return Quadruple(
            note.attackMs / 1000f,
            note.decayMs / 1000f,
            note.sustainLevel,
            note.releaseMs / 1000f
        )
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
