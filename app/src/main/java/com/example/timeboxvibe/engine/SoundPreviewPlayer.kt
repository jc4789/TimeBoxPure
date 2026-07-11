package com.example.timeboxvibe.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.SongPlayback
import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import com.example.timeboxvibe.engine.audio.opna.OpnaSequencer
import kotlin.math.exp
import kotlin.math.sin

object SoundPreviewPlayer {
    private const val TAG = "SoundPreviewPlayer"

    val availableSongs get() = SongCatalog.all

    @Volatile
    private var isStreaming = false
    @Volatile
    private var streamThread: Thread? = null

    @Volatile
    private var activeTrack: AudioTrack? = null
    private var tickTrack: AudioTrack? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun playPreview(context: Context, soundKey: String, volume: Float) {
        stop()
        val song = SongCatalog.byId(soundKey) ?: return
        when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> playArrangementStreaming(
                context,
                playback.lanes,
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
        when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> playArrangementStreaming(context, playback.lanes, shouldLoop = false, stopAfterMs = 5000L)
            null -> return
        }
    }

    fun playAlarm(context: Context, soundKey: String, volume: Float) {
        stop()
        val song = SongCatalog.byId(soundKey) ?: return
        when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> playArrangementStreaming(context, playback.lanes, shouldLoop = true)
            null -> return
        }
    }

    private fun playArrangementStreaming(context: Context, arrangement: ArrangementLanes, shouldLoop: Boolean, stopAfterMs: Long = -1L) {
        if (arrangement.routing != ArrangementRouting.MML_LOGICAL_TRACKS) {
            Log.w(TAG, "Rejected retired legacy arrangement routing")
            return
        }
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

            val sampleRate = 48000 
            val chunkSize = 1024

            val synth = OpnaLikeSynthesizer(sampleRate)
            val sequencer = OpnaSequencer(sampleRate, arrangement.tempoBpm, arrangement.beatsPerBar)

            synth.enableOutputFilter = true
            synth.enableStereoResonator = true
            synth.configureMasterEq(arrangement.eqBands)
            var i = 0
            while (i < synth.fm.size) {
                synth.fm[i].enableOversampling = true
                i++
            }

            MmlArrangementScheduler.schedule(arrangement, synth, sequencer, sampleRate)
            sequencer.sortEvents()

            val compiled = requireNotNull(arrangement.compiledOpnaSong) {
                "Catalog playback requires the unified MML event program"
            }
            val maxDurationMs = compiled.durationMilliseconds()

            if (maxDurationMs <= 0L) return@Runnable

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val audioTrackBufferSize = maxOf(minBufferSize, chunkSize * 8 * 2)

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
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
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
            val shortBuffer = ShortArray(chunkSize * 2)
            var currentSampleOffset = 0L
            val songLenSamples = sequencer.loopLengthSamples()
            var lastUnderrunCount = 0

            try {
                while (Thread.currentThread() == streamThread && !Thread.currentThread().isInterrupted) {
                    val elapsedGlobalMs = (currentSampleOffset * 1000L) / sampleRate
                    if (stopAfterMs > 0L && elapsedGlobalMs >= stopAfterMs) {
                        break
                    }
                    if (!shouldLoop && elapsedGlobalMs >= maxDurationMs) {
                        break
                    }

                    val looping = shouldLoop && songLenSamples > 0L
                    var renderOffset = if (looping) currentSampleOffset % songLenSamples else currentSampleOffset
                    var framesFilled = 0
                    val totalSamples = chunkSize
                    while (framesFilled < totalSamples) {
                        val framesRemaining = totalSamples - framesFilled
                        val framesToRender = if (looping) {
                            minOf(framesRemaining.toLong(), songLenSamples - renderOffset).toInt()
                        } else {
                            framesRemaining
                        }

                        synth.renderStereo(floatBuffer, framesToRender, sequencer, renderOffset)

                        var k = 0
                        while (k < framesToRender) {
                            val source = k * 2
                            val destination = (framesFilled + k) * 2
                            val left = floatBuffer[source]
                            val right = floatBuffer[source + 1]
                            shortBuffer[destination] = (left * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                            shortBuffer[destination + 1] = (right * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                            k++
                        }

                        framesFilled += framesToRender
                        renderOffset += framesToRender
                        if (looping && renderOffset == songLenSamples) {
                            sequencer.resetPlaybackCursor()
                            synth.allNotesOff()
                            renderOffset = 0L
                        }
                    }

                    val written = audioTrack.write(shortBuffer, 0, totalSamples * 2, AudioTrack.WRITE_BLOCKING)
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
