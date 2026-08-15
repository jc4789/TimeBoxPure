@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.ArrangementRouting
import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.SongPlayback
import com.example.timeboxvibe.engine.audio.mml.CompiledOpnaPlayer
import com.example.timeboxvibe.engine.audio.mml.MmlArrangementScheduler
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import platform.windows.CloseHandle
import platform.windows.CreateEventW
import platform.windows.CreateThread
import platform.windows.DWORD_PTR
import platform.windows.FALSE
import platform.windows.HANDLE
import platform.windows.HANDLEVar
import platform.windows.HWAVEOUT__
import platform.windows.SetEvent
import platform.windows.TRUE
import platform.windows.WAIT_OBJECT_0
import platform.windows.WAVEFORMATEX
import platform.windows.WAVEHDR
import platform.windows.WaitForSingleObject
import platform.windows.waveOutClose
import platform.windows.waveOutOpen
import platform.windows.waveOutPrepareHeader
import platform.windows.waveOutReset
import platform.windows.waveOutUnprepareHeader
import platform.windows.waveOutWrite
import kotlin.concurrent.Volatile

/**
 * Dummy audio output: PCM16 waveOut streaming.
 * Synthesis stays in the shared OPNA player.
 */
class Win32Audio {
    @Volatile
    private var running = false
    private var thread: HANDLE? = null
    private var stopEvent: HANDLE? = null
    private var stableSelf: StableRef<Win32Audio>? = null
    private val floatBuffer = FloatArray(AUDIO_CHUNK_FRAMES)
    private val shortBuffer = ShortArray(AUDIO_CHUNK_FRAMES)

    @Volatile
    private var pendingArrangement: ArrangementLanes? = null
    @Volatile
    private var pendingLoop = false
    @Volatile
    private var pendingStopAfterMs = -1L

    fun playPreview(soundKey: String, volume: Float) {
        val song = SongCatalog.byId(soundKey) ?: return
        when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> start(playback.lanes, shouldLoop = false, stopAfterMs = song.previewDurationMs)
            null -> return
        }
    }

    fun playAlarm(soundKey: String, volume: Float) {
        val song = SongCatalog.byId(soundKey) ?: return
        when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> start(playback.lanes, shouldLoop = true, stopAfterMs = -1L)
            null -> return
        }
    }

    fun playGentleReminder(soundKey: String, volume: Float) {
        val song = SongCatalog.byId(soundKey) ?: return
        when (val playback = song.buildPlayback(volume)) {
            is SongPlayback.Arrangement -> start(playback.lanes, shouldLoop = false, stopAfterMs = GENTLE_REMINDER_MS)
            null -> return
        }
    }

    fun stop() {
        running = false
        stopEvent?.let { SetEvent(it) }
        val existing = thread
        if (existing != null) {
            WaitForSingleObject(existing, 2000u)
            CloseHandle(existing)
            thread = null
        }
        stopEvent?.let { CloseHandle(it) }
        stopEvent = null
        stableSelf?.dispose()
        stableSelf = null
        pendingArrangement = null
    }

    fun shutdown() {
        stop()
    }

    private fun start(arrangement: ArrangementLanes, shouldLoop: Boolean, stopAfterMs: Long) {
        stop()
        if (arrangement.routing != ArrangementRouting.MML_LOGICAL_TRACKS) return
        pendingArrangement = arrangement
        pendingLoop = shouldLoop
        pendingStopAfterMs = stopAfterMs
        running = true
        stopEvent = CreateEventW(null, TRUE, FALSE, null)
        val ref = StableRef.create(this)
        stableSelf = ref
        thread = CreateThread(
            null,
            0u,
            staticCFunction { param ->
                val self = param!!.asStableRef<Win32Audio>().get()
                self.renderLoop()
                0u
            },
            ref.asCPointer(),
            0u,
            null
        )
    }

    private fun renderLoop() {
        val arrangement = pendingArrangement ?: return
        val shouldLoop = pendingLoop
        val stopAfterMs = pendingStopAfterMs
        val compiled = arrangement.compiledOpnaSong
        val maxDurationMs = compiled.durationMilliseconds()
        if (maxDurationMs <= 0L) return

        val synth = OpnaLikeSynthesizer(AUDIO_SAMPLE_RATE)
        synth.enableOutputFilter = true
        synth.configureMasterEq(arrangement.eqBands)
        var voice = 0
        while (voice < synth.fm.size) {
            synth.fm[voice].enableOversampling = true
            voice++
        }
        val player = MmlArrangementScheduler.createPlayer(arrangement, AUDIO_SAMPLE_RATE)
        val songLenSamples = player.loopLengthSamples
        val headerSize = sizeOf<WAVEHDR>().toUInt()

        memScoped {
            val format = alloc<WAVEFORMATEX>()
            format.wFormatTag = WAVE_FORMAT_PCM
            format.nChannels = 1u
            format.nSamplesPerSec = AUDIO_SAMPLE_RATE.toUInt()
            format.wBitsPerSample = 16u
            format.nBlockAlign = 2u
            format.nAvgBytesPerSec = (AUDIO_SAMPLE_RATE * 2).toUInt()
            format.cbSize = 0u

            val waveOutVar = allocArray<HANDLEVar>(1)
            val doneEvent = CreateEventW(null, FALSE, FALSE, null) ?: return
            val callback = doneEvent.rawValue.toLong().toULong()
            val openResult = waveOutOpen(
                waveOutVar.reinterpret(),
                WAVE_MAPPER,
                format.ptr,
                callback,
                0u,
                CALLBACK_EVENT
            )
            if (openResult != MMSYSERR_NOERROR) {
                CloseHandle(doneEvent)
                return
            }
            val waveOut = waveOutVar[0]?.reinterpret<HWAVEOUT__>()
            val headers = Array(AUDIO_HEADER_COUNT) { nativeHeap.alloc<WAVEHDR>() }
            val nativeBuffers = Array<CPointer<ShortVar>>(AUDIO_HEADER_COUNT) {
                nativeHeap.allocArray(AUDIO_CHUNK_FRAMES)
            }
            var prepared = 0
            try {
                var headerIndex = 0
                while (headerIndex < AUDIO_HEADER_COUNT) {
                    val header = headers[headerIndex]
                    header.lpData = nativeBuffers[headerIndex].reinterpret()
                    header.dwBufferLength = (AUDIO_CHUNK_FRAMES * 2).toUInt()
                    header.dwFlags = 0u
                    header.dwLoops = 0u
                    waveOutPrepareHeader(waveOut, header.ptr, headerSize)
                    headerIndex++
                    prepared++
                }

                var currentSampleOffset = 0L
                var primed = 0
                while (primed < AUDIO_HEADER_COUNT && running) {
                    renderPcm16(
                        player,
                        synth,
                        floatBuffer,
                        shortBuffer,
                        AUDIO_CHUNK_FRAMES,
                        currentSampleOffset,
                        songLenSamples,
                        shouldLoop
                    )
                    copyToNative(nativeBuffers[primed], shortBuffer, AUDIO_CHUNK_FRAMES)
                    if (waveOutWrite(waveOut, headers[primed].ptr, headerSize) != MMSYSERR_NOERROR) {
                        return
                    }
                    currentSampleOffset += AUDIO_CHUNK_FRAMES.toLong()
                    primed++
                }

                while (running) {
                    val elapsedMs = (currentSampleOffset * 1000L) / AUDIO_SAMPLE_RATE
                    if (stopAfterMs > 0L && elapsedMs >= stopAfterMs) break
                    if (!shouldLoop && elapsedMs >= maxDurationMs) break

                    val wait = WaitForSingleObject(doneEvent, 100u)
                    if (!running) break
                    if (wait != WAIT_OBJECT_0) continue

                    var hdr = 0
                    while (hdr < AUDIO_HEADER_COUNT) {
                        val header = headers[hdr]
                        if ((header.dwFlags and WHDR_DONE) != 0u) {
                            renderPcm16(
                                player,
                                synth,
                                floatBuffer,
                                shortBuffer,
                                AUDIO_CHUNK_FRAMES,
                                currentSampleOffset,
                                songLenSamples,
                                shouldLoop
                            )
                            copyToNative(nativeBuffers[hdr], shortBuffer, AUDIO_CHUNK_FRAMES)
                            header.dwFlags = header.dwFlags and WHDR_DONE.inv()
                            if (waveOutWrite(waveOut, header.ptr, headerSize) != MMSYSERR_NOERROR) {
                                return
                            }
                            currentSampleOffset += AUDIO_CHUNK_FRAMES.toLong()
                        }
                        hdr++
                    }
                }
            } finally {
                waveOutReset(waveOut)
                var hdr = 0
                while (hdr < prepared) {
                    waveOutUnprepareHeader(waveOut, headers[hdr].ptr, headerSize)
                    nativeHeap.free(headers[hdr].rawPtr)
                    nativeHeap.free(nativeBuffers[hdr].rawValue)
                    hdr++
                }
                waveOutClose(waveOut)
                CloseHandle(doneEvent)
            }
        }
    }

    private fun copyToNative(dest: CPointer<ShortVar>, src: ShortArray, frames: Int) {
        var i = 0
        while (i < frames) {
            dest[i] = src[i]
            i++
        }
    }

    private fun renderPcm16(
        player: CompiledOpnaPlayer,
        synth: OpnaLikeSynthesizer,
        floatBuf: FloatArray,
        shortBuf: ShortArray,
        frames: Int,
        currentSampleOffset: Long,
        songLenSamples: Long,
        shouldLoop: Boolean
    ) {
        val looping = shouldLoop && songLenSamples > 0L
        var renderOffset = if (looping) currentSampleOffset % songLenSamples else currentSampleOffset
        var framesFilled = 0
        while (framesFilled < frames) {
            val framesRemaining = frames - framesFilled
            val framesToRender = if (looping) {
                minOf(framesRemaining.toLong(), songLenSamples - renderOffset).toInt()
            } else {
                framesRemaining
            }
            player.render(synth, floatBuf, framesToRender, renderOffset)
            var frame = 0
            while (frame < framesToRender) {
                val sample = floatBuf[frame]
                shortBuf[framesFilled + frame] =
                    (sample * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                frame++
            }
            framesFilled += framesToRender
            renderOffset += framesToRender
            if (looping && renderOffset == songLenSamples) {
                player.reset(synth)
                renderOffset = 0L
            }
        }
    }
}
