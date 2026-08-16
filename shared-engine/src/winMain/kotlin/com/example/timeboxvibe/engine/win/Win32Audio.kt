@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.ArrangementRouting
import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.SongPlayback
import com.example.timeboxvibe.engine.audio.mml.CompiledOpnaPlayer
import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import miniaudio.tb_audio_device_init
import miniaudio.tb_audio_device_start
import miniaudio.tb_audio_device_stop
import miniaudio.tb_audio_device_uninit
import kotlin.concurrent.Volatile

/**
 * Dummy WASAPI output via miniaudio.
 * Synthesis stays in CompiledOpnaPlayer / OpnaLikeSynthesizer.
 */
class Win32Audio {
    private val floatBuffer = FloatArray(AUDIO_CALLBACK_MAX_FRAMES)

    @Volatile
    private var running = false
    @Volatile
    private var currentSampleOffset = 0L
    @Volatile
    private var shouldLoop = false
    @Volatile
    private var stopAfterMs = -1L
    @Volatile
    private var maxDurationMs = 0L
    @Volatile
    private var songLenSamples = 0L

    private var player: CompiledOpnaPlayer? = null
    private var synth: OpnaLikeSynthesizer? = null
    /* NATIVE_OWNED by timebox_miniaudio.c; freed in tb_audio_device_uninit. */
    private var device: COpaquePointer? = null
    /* STABLE_REF: callback userdata. Dispose only after uninit. */
    private var stableSelf: StableRef<Win32Audio>? = null

    fun playPreview(soundKey: String, volume: Float): Boolean {
        val song = SongCatalog.byId(soundKey) ?: return false
        return when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> start(playback.lanes, shouldLoop = false, stopAfterMs = song.previewDurationMs)
            null -> false
        }
    }

    fun playAlarm(soundKey: String, volume: Float): Boolean {
        val song = SongCatalog.byId(soundKey) ?: return false
        return when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> start(playback.lanes, shouldLoop = true, stopAfterMs = -1L)
            null -> false
        }
    }

    fun playGentleReminder(soundKey: String, volume: Float): Boolean {
        val song = SongCatalog.byId(soundKey) ?: return false
        return when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> start(playback.lanes, shouldLoop = false, stopAfterMs = GENTLE_REMINDER_MS)
            null -> false
        }
    }

    fun pump() {
        if (device != null && !running) {
            teardownDevice()
        }
    }

    fun stop() {
        running = false
        teardownDevice()
    }

    fun shutdown() {
        stop()
    }

    internal fun onRender(output: CPointer<FloatVar>, frameCount: Int) {
        if (frameCount <= 0) return
        if (!running) {
            zeroOutput(output, frameCount)
            return
        }
        val activePlayer = player
        val activeSynth = synth
        if (activePlayer == null || activeSynth == null) {
            running = false
            zeroOutput(output, frameCount)
            return
        }

        val elapsedMs = (currentSampleOffset * 1000L) / AUDIO_SAMPLE_RATE
        if ((stopAfterMs > 0L && elapsedMs >= stopAfterMs) ||
            (!shouldLoop && elapsedMs >= maxDurationMs)
        ) {
            running = false
            zeroOutput(output, frameCount)
            return
        }

        val looping = shouldLoop && songLenSamples > 0L
        var written = 0
        var sampleOffset = currentSampleOffset
        while (written < frameCount) {
            var renderOffset = if (looping) sampleOffset % songLenSamples else sampleOffset
            var framesToRender = frameCount - written
            if (framesToRender > AUDIO_CALLBACK_MAX_FRAMES) {
                framesToRender = AUDIO_CALLBACK_MAX_FRAMES
            }
            if (looping) {
                val untilLoop = (songLenSamples - renderOffset).toInt()
                if (untilLoop < framesToRender) {
                    framesToRender = untilLoop
                }
            }
            if (framesToRender <= 0) break
            activePlayer.render(activeSynth, floatBuffer, framesToRender, renderOffset)
            var i = 0
            while (i < framesToRender) {
                output[written + i] = floatBuffer[i]
                i++
            }
            written += framesToRender
            sampleOffset += framesToRender.toLong()
            renderOffset += framesToRender.toLong()
            if (looping && renderOffset == songLenSamples) {
                activePlayer.reset(activeSynth)
            }
        }
        currentSampleOffset = sampleOffset
    }

    private fun start(arrangement: ArrangementLanes, shouldLoop: Boolean, stopAfterMs: Long): Boolean {
        stop()
        if (arrangement.routing != ArrangementRouting.MML_LOGICAL_TRACKS) return false
        val compiled = arrangement.compiledOpnaSong
        val durationMs = compiled.durationMilliseconds()
        if (durationMs <= 0L) return false

        val nextSynth = OpnaLikeSynthesizer(AUDIO_SAMPLE_RATE)
        nextSynth.enableOutputFilter = true
        nextSynth.configureMasterEq(arrangement.eqBands)
        var voice = 0
        while (voice < nextSynth.fm.size) {
            nextSynth.fm[voice].enableOversampling = true
            voice++
        }
        val nextPlayer = MmlArrangementScheduler.createPlayer(arrangement, AUDIO_SAMPLE_RATE)

        this.player = nextPlayer
        this.synth = nextSynth
        this.shouldLoop = shouldLoop
        this.stopAfterMs = stopAfterMs
        this.maxDurationMs = durationMs
        this.songLenSamples = nextPlayer.loopLengthSamples
        this.currentSampleOffset = 0L

        val ref = StableRef.create(this)
        stableSelf = ref
        var initResult = 0
        val opened = memScoped {
            val slot = alloc<COpaquePointerVar>()
            val rc = tb_audio_device_init(
                slot.ptr,
                AUDIO_SAMPLE_RATE.toUInt(),
                AUDIO_PERIOD_FRAMES.toUInt(),
                AUDIO_PERIOD_COUNT.toUInt(),
                staticCFunction(::win32AudioRenderThunk),
                ref.asCPointer()
            )
            initResult = rc
            if (rc != 0) {
                null
            } else {
                slot.value
            }
        }
        if (opened == null) {
            ref.dispose()
            stableSelf = null
            this.player = null
            this.synth = null
            println("Win32 audio device initialization failed: $initResult")
            return false
        }
        device = opened
        running = true
        val startResult = tb_audio_device_start(opened)
        if (startResult != 0) {
            running = false
            teardownDevice()
            println("Win32 audio device start failed: $startResult")
            return false
        }
        return true
    }

    private fun teardownDevice() {
        val opened = device
        device = null
        if (opened != null) {
            tb_audio_device_stop(opened)
            tb_audio_device_uninit(opened)
        }
        stableSelf?.dispose()
        stableSelf = null
        player = null
        synth = null
    }

    private fun zeroOutput(output: CPointer<FloatVar>, frameCount: Int) {
        var i = 0
        while (i < frameCount) {
            output[i] = 0f
            i++
        }
    }
}

private fun win32AudioRenderThunk(
    userData: COpaquePointer?,
    output: CPointer<FloatVar>?,
    frameCount: UInt
) {
    if (userData == null || output == null || frameCount == 0u) return
    val self = userData.asStableRef<Win32Audio>().get()
    self.onRender(output, frameCount.toInt())
}
