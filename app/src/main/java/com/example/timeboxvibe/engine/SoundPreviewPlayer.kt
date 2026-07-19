package com.example.timeboxvibe.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.timeboxvibe.engine.audio.ProceduralTimerTick
import com.example.timeboxvibe.engine.audio.opna.OpnaPlaybackSession

object SoundPreviewPlayer {
    private const val TAG = "SoundPreviewPlayer"

    val availableSongs get() = SongCatalog.all

    @Volatile
    private var isStreaming = false
    @Volatile
    private var streamThread: Thread? = null

    @Volatile
    private var activeTrack: AudioTrack? = null
    @Volatile
    private var activeSession: OpnaPlaybackSession? = null
    private var tickTrack: AudioTrack? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun playPreview(context: Context, soundKey: String, volume: Float) {
        stop()
        val song = SongCatalog.byId(soundKey) ?: return
        when (val playback = song.buildPlayback()) {
            is SongPlayback.Arrangement -> playArrangementStreaming(
                context,
                playback.lanes,
                userGain = volume,
                shouldLoop = false,
                stopAfterMs = song.previewDurationMs
            )
            null -> return
        }
    }

    fun playTick(volume: Float) {
        try {
            var track = tickTrack
            if (track == null) {
                val sampleRate = ProceduralTimerTick.SAMPLE_RATE
                val numSamples = sampleRate * ProceduralTimerTick.DURATION_MILLISECONDS / 1000
                val buffer = FloatArray(numSamples)
                ProceduralTimerTick.fill(buffer, volume)

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
        activeSession?.stop()
        activeSession = null
        val t = streamThread
        streamThread = null
        if (t != null) {
            try {
                t.interrupt()
                t.join(50)
            } catch (e: Exception) {}
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

        wakeLock?.let {
            if (it.isHeld) {
                try { it.release() } catch (e: Exception) { e.printStackTrace() }
            }
        }
        wakeLock = null
    }

    fun playGentleReminder(context: Context, soundKey: String, volume: Float) {
        stop()
        val song = SongCatalog.byId(soundKey) ?: return
        when (val playback = song.buildPlayback()) {
            is SongPlayback.Arrangement -> playArrangementStreaming(
                context, playback.lanes, volume, shouldLoop = false, stopAfterMs = 5000L
            )
            null -> return
        }
    }

    fun playAlarm(context: Context, soundKey: String, volume: Float) {
        stop()
        val song = SongCatalog.byId(soundKey) ?: return
        when (val playback = song.buildPlayback()) {
            is SongPlayback.Arrangement -> playArrangementStreaming(
                context, playback.lanes, volume, shouldLoop = true
            )
            null -> return
        }
    }

    private fun playArrangementStreaming(
        context: Context,
        arrangement: ArrangementLanes,
        userGain: Float,
        shouldLoop: Boolean,
        stopAfterMs: Long = -1L
    ) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimeBox::SoundPreviewWakeLock")
            wl.acquire(30 * 1000L)
            wakeLock = wl
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
        }

        isStreaming = true

        val thread = Thread(Runnable {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

            val session = OpnaPlaybackSession.createProduct(arrangement, userGain, shouldLoop)
            if (session.isComplete) {
                session.stop()
                return@Runnable
            }
            val sampleRate = session.sampleRate
            val chunkSize = session.maximumRenderChunk

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audioTrackBufferSize = maxOf(minBufferSize, chunkSize * 8)

            val audioTrack = try {
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(audioTrackBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                
                builder.build()
            } catch (e: Exception) {
                e.printStackTrace()
                session.stop()
                return@Runnable
            }

            synchronized(this@SoundPreviewPlayer) {
                activeTrack = audioTrack
                activeSession = session
            }

            try {
                audioTrack.play()
            } catch (e: Exception) {
                e.printStackTrace()
                session.stop()
                try { audioTrack.release() } catch (ex: Exception) {}
                synchronized(this@SoundPreviewPlayer) {
                    if (activeTrack == audioTrack) activeTrack = null
                    if (activeSession === session) activeSession = null
                }
                return@Runnable
            }

            val floatBuffer = FloatArray(chunkSize)
            val shortBuffer = ShortArray(chunkSize)
            var renderedFrames = 0L
            var lastUnderrunCount = 0

            try {
                while (Thread.currentThread() == streamThread && !Thread.currentThread().isInterrupted) {
                    val elapsedGlobalMs = (renderedFrames * 1000L) / sampleRate
                    if (stopAfterMs > 0L && elapsedGlobalMs >= stopAfterMs) {
                        break
                    }
                    if (session.isComplete) {
                        break
                    }

                    session.render(floatBuffer, chunkSize)
                    var k = 0
                    while (k < chunkSize) {
                        shortBuffer[k] = (floatBuffer[k] * 32767f)
                            .coerceIn(-32768f, 32767f).toInt().toShort()
                        k++
                    }

                    val written = audioTrack.write(shortBuffer, 0, chunkSize, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        break
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val underrunCount = audioTrack.underrunCount
                        if (underrunCount > lastUnderrunCount) {
                            Log.w(TAG, "AudioTrack underrun detected: $underrunCount total")
                            lastUnderrunCount = underrunCount
                        }
                    }

                    renderedFrames += chunkSize
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                session.stop()
                try { audioTrack.stop() } catch (e: Exception) {}
                try { audioTrack.release() } catch (e: Exception) {}
                synchronized(this@SoundPreviewPlayer) {
                    if (activeTrack == audioTrack) {
                        activeTrack = null
                    }
                    if (activeSession === session) activeSession = null
                }
            }
        }, "SoundStreamThread")

        streamThread = thread
        thread.start()
    }

}
