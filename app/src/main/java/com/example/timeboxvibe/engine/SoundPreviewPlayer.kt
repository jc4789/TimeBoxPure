package com.example.timeboxvibe.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

object SoundPreviewPlayer {
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
            "synth-bad-apple", "synth-senbonzakura" -> {
                val allNotes = SoundMelodies.getMelody(soundKey, volume, false)
                playSpecsStreaming(allNotes, shouldLoop = false, stopAfterMs = 3500L)
            }
            "synth-chime", "synth-victory" -> {
                val melody = SoundMelodies.getMelody(soundKey, volume, false)
                val bass = SoundMelodies.getMelody(soundKey, volume, true)
                playSpecsStreaming(melody + bass, shouldLoop = false, stopAfterMs = 3500L)
            }
        }
    }

    /**
     * The PC-98 "Thock"
     * Generates a heavy, mechanical UI click using a low-frequency square/triangle blend
     * with a rapid pitch-drop (FM synthesis) to simulate a physical switch.
     */
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
                    // Pitch drop: Starts at 300Hz, drops rapidly to 100Hz
                    val freq = 300.0 * exp(-50.0 * t)
                    val phase = 2.0 * Math.PI * freq * t

                    // Crunchy Square-like wave
                    var sample = if (sin(phase) > 0) 0.8f else -0.8f

                    val ageMs = i * 1000 / sampleRate
                    // Extremely sharp attack and decay for that mechanical "clack"
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
        
        try {
            activeTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) { e.printStackTrace() }
        activeTrack = null

        // WE DO NOT RELEASE tickTrack HERE ANYMORE!
        // We want the 'thock' to stay loaded in memory so it has zero latency.

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

    private fun computeEnvelope(ageMs: Int, spec: ToneSpec): Float {
        return com.example.timeboxvibe.engine.core.ChiptuneSynthesizer.computeEnvelope(ageMs, spec)
    }

    private fun generateWaveform(phase: Double, t: Float, spec: ToneSpec): Float {
        return com.example.timeboxvibe.engine.core.ChiptuneSynthesizer.generateWaveform(phase, t, spec)
    }

    private fun softClip(x: Float): Float {
        return com.example.timeboxvibe.engine.core.ChiptuneSynthesizer.softClip(x)
    }

    private fun playOrientalPreview(context: Context, volume: Float) {
        try {
            mediaPlayer = MediaPlayer().apply {
                // THE FIX: Route MediaPlayer through the ALARM channel so it doesn't get muted!
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
                afd.close() // Safer to close AFTER prepare
            }
            val runnable = Runnable { stop() }
            stopRunnable = runnable
            handler.postDelayed(runnable, 3500)
        } catch (e: Exception) { e.printStackTrace() }
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
                } catch (e: Exception) { e.printStackTrace() }
            }
            "synth-bad-apple", "synth-senbonzakura" -> {
                val allNotes = SoundMelodies.getMelody(soundKey, volume, false)
                playSpecsStreaming(allNotes, shouldLoop = false, stopAfterMs = 1500L)
            }
            "synth-chime", "synth-victory" -> {
                val melody = SoundMelodies.getMelody(soundKey, volume, false)
                val bass = SoundMelodies.getMelody(soundKey, volume, true)
                playSpecsStreaming(melody + bass, shouldLoop = false, stopAfterMs = 1500L)
            }
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
                } catch (e: Exception) { e.printStackTrace() }
            }
            "synth-bad-apple", "synth-senbonzakura" -> {
                val allNotes = SoundMelodies.getMelody(soundKey, volume, false)
                playSpecsStreaming(allNotes, shouldLoop = true)
            }
            "synth-chime", "synth-victory" -> {
                val melody = SoundMelodies.getMelody(soundKey, volume, false)
                val bass = SoundMelodies.getMelody(soundKey, volume, true)
                playSpecsStreaming(melody + bass, shouldLoop = true)
            }
        }
    }

    private fun playSpecsStreaming(specs: List<ToneSpec>, shouldLoop: Boolean, stopAfterMs: Long = -1L) {
        stop()
        if (specs.isEmpty()) return

        isStreaming = true

        val maxDurationMs = specs.maxOf { it.startMs + it.durationMs }.toLong()
        if (maxDurationMs <= 0L) return

        val thread = Thread(Runnable {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

            val sampleRate = 44100
            val chunkSize = 1024

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val audioTrackBufferSize = maxOf(minBufferSize, chunkSize * 4 * 4)

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
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
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

            val floatBuffer = FloatArray(chunkSize)

            var currentSampleOffset = 0L
            val maxDurationSamples = (maxDurationMs * sampleRate) / 1000L

            try {
                while (isStreaming) {
                    floatBuffer.fill(0f)

                    val chunkStartGlobal = currentSampleOffset
                    val chunkEndGlobal = currentSampleOffset + chunkSize

                    // If we have a non-looping limit in absolute playback terms
                    if (stopAfterMs > 0L) {
                        val elapsedGlobalMs = (chunkStartGlobal * 1000L) / sampleRate
                        if (elapsedGlobalMs >= stopAfterMs) {
                            break
                        }
                    } else if (!shouldLoop) {
                        val elapsedGlobalMs = (chunkStartGlobal * 1000L) / sampleRate
                        if (elapsedGlobalMs >= maxDurationMs) {
                            break
                        }
                    }

                    // Pre-calculate which specs overlap with this chunk
                    val activeSpecs = mutableListOf<ToneSpec>()

                    if (shouldLoop) {
                        val chunkStartMelody = chunkStartGlobal % maxDurationSamples
                        val chunkEndMelody = chunkStartMelody + chunkSize

                        if (chunkEndMelody <= maxDurationSamples) {
                            val cS = chunkStartMelody
                            val cE = chunkEndMelody
                            for (spec in specs) {
                                val specStart = (spec.startMs * sampleRate) / 1000L
                                val specEnd = specStart + (spec.durationMs * sampleRate) / 1000L
                                if (specStart < cE && specEnd > cS) {
                                    activeSpecs.add(spec)
                                }
                            }
                        } else {
                            val cS1 = chunkStartMelody
                            val cE1 = maxDurationSamples
                            val cS2 = 0L
                            val cE2 = chunkEndMelody % maxDurationSamples
                            for (spec in specs) {
                                val specStart = (spec.startMs * sampleRate) / 1000L
                                val specEnd = specStart + (spec.durationMs * sampleRate) / 1000L
                                val overlapsPortionA = specStart < cE1 && specEnd > cS1
                                val overlapsPortionB = specStart < cE2 && specEnd > cS2
                                if (overlapsPortionA || overlapsPortionB) {
                                    activeSpecs.add(spec)
                                }
                            }
                        }
                    } else {
                        val cS = chunkStartGlobal
                        val cE = chunkEndGlobal
                        for (spec in specs) {
                            val specStart = (spec.startMs * sampleRate) / 1000L
                            val specEnd = specStart + (spec.durationMs * sampleRate) / 1000L
                            if (specStart < cE && specEnd > cS) {
                                activeSpecs.add(spec)
                            }
                        }
                    }

                    // Render if we have active specs
                    if (activeSpecs.isNotEmpty()) {
                        com.example.timeboxvibe.engine.core.ChiptuneSynthesizer.renderChunk(
                            specs = activeSpecs,
                            shouldLoop = shouldLoop,
                            chunkStartGlobal = chunkStartGlobal,
                            chunkSize = chunkSize,
                            sampleRate = sampleRate,
                            maxDurationSamples = maxDurationSamples,
                            floatBuffer = floatBuffer
                        )
                    }

                    // Apply soft-clipping and write to AudioTrack
                    for (i in 0 until chunkSize) {
                        val mixed = floatBuffer[i] * 2.8f
                        floatBuffer[i] = softClip(mixed).coerceIn(-1.0f, 1.0f)
                    }

                    val written = audioTrack.write(floatBuffer, 0, chunkSize, AudioTrack.WRITE_BLOCKING)
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
}
