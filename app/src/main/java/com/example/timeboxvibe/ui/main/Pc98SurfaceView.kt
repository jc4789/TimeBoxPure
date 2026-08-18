package com.example.timeboxvibe.ui.main

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.timeboxvibe.engine.core.EngineInputCodes
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_CANCEL
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_DOWN
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_MOVE
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_UP
import com.example.timeboxvibe.engine.core.PresentationTransform
import com.example.timeboxvibe.engine.core.PrimitiveDisplayProfile
import com.example.timeboxvibe.engine.core.ScaledProceduralRenderer
import com.example.timeboxvibe.engine.core.SceneManager
import com.example.timeboxvibe.engine.core.SoftwareEngineCanvas
import com.example.timeboxvibe.platform.android.AndroidFramebufferPresenter

/** Android terminal for the common indexed framebuffer. */
class Pc98SurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var renderThread: RenderThread? = null
    private val presentationTransform = PresentationTransform()
    @Volatile private var primitiveWidth = 1
    @Volatile private var primitiveHeight = 1
    private val queueLock = Any()
    private val touchQueue = IntArray(TOUCH_QUEUE_CAPACITY * TOUCH_EVENT_SLOT_COUNT)
    private var touchCount = 0
    @Volatile private var framesRendered = 0L
    @Volatile private var updatesCalled = 0L
    @Volatile private var lastDt = 0f
    @Volatile private var localTouchCountThisFrame = 0
    @Volatile private var touchesQueued = 0L
    @Volatile private var touchesDropped = 0L
    @Volatile private var touchesDrained = 0L
    
    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        renderThread?.stopThread()

        primitiveWidth = PrimitiveDisplayProfile.primitiveWidth(width, height)
        primitiveHeight = PrimitiveDisplayProfile.primitiveHeight(width, height)
        presentationTransform.configure(primitiveWidth, primitiveHeight, width, height)
        val engineCanvas = SoftwareEngineCanvas(primitiveWidth, primitiveHeight)
        val renderer = ScaledProceduralRenderer(engineCanvas)
        val presenter = AndroidFramebufferPresenter(primitiveWidth, primitiveHeight)

        renderThread = RenderThread(
            holder,
            renderer,
            engineCanvas,
            presenter,
            presentationTransform,
            this
        ).apply {
            startThread()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderThread?.stopThread()
        renderThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionCode = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> ENGINE_TOUCH_DOWN
            MotionEvent.ACTION_MOVE -> ENGINE_TOUCH_MOVE
            MotionEvent.ACTION_UP -> ENGINE_TOUCH_UP
            MotionEvent.ACTION_CANCEL -> ENGINE_TOUCH_CANCEL
            else -> return true
        }
        
        val rawX = event.x.toInt()
        val rawY = event.y.toInt()
        val logicalX = presentationTransform.primitiveX(rawX)
        val logicalY = presentationTransform.primitiveY(rawY)
        if (logicalX == PresentationTransform.OUTSIDE || logicalY == PresentationTransform.OUTSIDE) return true
        
        if (enqueueTouchInput(logicalX, logicalY, rawX, rawY, actionCode)) {
            touchesQueued++
        } else {
            touchesDropped++
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Map common control keys
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                SceneManager.enqueueInput(EngineInputCodes.CMD_BACKSPACE)
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                SceneManager.enqueueInput(EngineInputCodes.CMD_ENTER)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                SceneManager.enqueueInput(EngineInputCodes.CMD_LEFT)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                SceneManager.enqueueInput(EngineInputCodes.CMD_RIGHT)
                return true
            }
        }

        val unicode = event.unicodeChar
        if (unicode > 0) {
            SceneManager.enqueueInput(unicode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private class RenderThread(
        private val surfaceHolder: SurfaceHolder,
        private val renderer: ScaledProceduralRenderer,
        private val engineCanvas: SoftwareEngineCanvas,
        private val presenter: AndroidFramebufferPresenter,
        private val presentationTransform: PresentationTransform,
        private val viewRef: Pc98SurfaceView
    ) : Thread("Pc98RenderThread") {

        @Volatile
        private var running = false

        private val localTouchQueue = IntArray(TOUCH_QUEUE_CAPACITY * TOUCH_EVENT_SLOT_COUNT)
        private var localTouchCount = 0
        private var lastStatsLogNanos = 0L

        fun startThread() {
            running = true
            start()
        }

        fun stopThread() {
            running = false
            interrupt()
            try {
                join(RENDER_THREAD_JOIN_MS)
                if (isAlive) {
                    android.util.Log.e("Pc98SurfaceView", "SEVERE WARNING: RenderThread did not terminate cleanly!")
                }
            } catch (e: InterruptedException) { }
        }

        override fun run() {
            var lastNanos = System.nanoTime()

            while (running) {
                if (!surfaceHolder.surface.isValid) {
                    sleepFrame()
                    lastNanos = System.nanoTime()
                    continue
                }

                val frameStartNanos = System.nanoTime()
                val elapsedNanos = frameStartNanos - lastNanos
                lastNanos = frameStartNanos
                val dt = (elapsedNanos / NANOS_PER_SECOND).coerceAtMost(MAX_DELTA_SECONDS)

                try {
                    drawFrame(dt)
                    viewRef.framesRendered++
                } catch (e: Throwable) {
                    android.util.Log.e(
                        LOG_TAG,
                        "FRAME FAILURE scene=${SceneManager.currentSceneName()} touchCount=$localTouchCount",
                        e
                    )
                    localTouchCount = 0
                    viewRef.localTouchCountThisFrame = 0
                }
                logStatsIfDue(frameStartNanos)

                // TEMP_FRAME_CLOCK: temporary paced loop until a Looper-backed frame clock exists here.
                val loopNanos = System.nanoTime() - frameStartNanos
                val sleepTime = FRAME_NANOS - loopNanos
                if (sleepTime > 0L) {
                    try {
                        sleep(sleepTime / NANOS_PER_MILLI, (sleepTime % NANOS_PER_MILLI).toInt())
                    } catch (e: InterruptedException) { }
                } else {
                    Thread.yield()
                }
            }
        }

        private fun drawFrame(dt: Float) {
            val logicalWidth = engineCanvas.width
            val logicalHeight = engineCanvas.height

            drainTouchInputFastCopy()
            viewRef.localTouchCountThisFrame = localTouchCount
            try {
                SceneManager.setLogicalBounds(logicalWidth, logicalHeight)
                SceneManager.update(dt, localTouchQueue, localTouchCount)
                viewRef.updatesCalled++
                viewRef.lastDt = dt
            } catch (e: Throwable) {
                android.util.Log.e(
                    LOG_TAG,
                    "SceneManager.update failure scene=${SceneManager.currentSceneName()} touchCount=$localTouchCount",
                    e
                )
                localTouchCount = 0
                viewRef.localTouchCountThisFrame = 0
                return
            }

            SceneManager.render(renderer, logicalWidth, logicalHeight)
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas == null) return
                presenter.present(canvas, engineCanvas.framebuffer, presentationTransform)
            } catch (e: Throwable) {
                android.util.Log.e("Pc98SurfaceView", "Render thread failure", e)
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        android.util.Log.e("Pc98SurfaceView", "Canvas post failure", e)
                    }
                }
            }
        }

        private fun sleepFrame() {
            try {
                sleep(FRAME_SLEEP_MS)
            } catch (e: InterruptedException) { }
        }

        private fun drainTouchInputFastCopy() {
            localTouchCount = 0
            synchronized(viewRef.queueLock) {
                localTouchCount = viewRef.touchCount
                if (localTouchCount > 0) {
                    System.arraycopy(
                        viewRef.touchQueue,
                        0,
                        localTouchQueue,
                        0,
                        localTouchCount * TOUCH_EVENT_SLOT_COUNT
                    )
                }
                viewRef.touchCount = 0
            }
            if (localTouchCount > 0) {
                viewRef.touchesDrained += localTouchCount.toLong()
            }
        }

        private fun logStatsIfDue(nowNanos: Long) {
            if (lastStatsLogNanos == 0L) {
                lastStatsLogNanos = nowNanos
                return
            }
            if (nowNanos - lastStatsLogNanos < STATS_LOG_INTERVAL_NANOS) return

            lastStatsLogNanos = nowNanos
            android.util.Log.d(
                LOG_TAG,
                "framesRendered=${viewRef.framesRendered} updatesCalled=${viewRef.updatesCalled} physicalWidth=${viewRef.width} physicalHeight=${viewRef.height} primitiveWidth=${viewRef.primitiveWidth} primitiveHeight=${viewRef.primitiveHeight} currentSceneName=${SceneManager.currentSceneName()} lastDt=${viewRef.lastDt} localTouchCountThisFrame=${viewRef.localTouchCountThisFrame} totalTouchesQueued=${viewRef.touchesQueued} totalTouchesDrained=${viewRef.touchesDrained} totalTouchesDropped=${viewRef.touchesDropped}"
            )
        }
    }

    private fun enqueueTouchInput(logicalX: Int, logicalY: Int, rawX: Int, rawY: Int, actionCode: Int): Boolean {
        synchronized(queueLock) {
            if (touchCount >= TOUCH_QUEUE_CAPACITY) return false

            val offset = touchCount * TOUCH_EVENT_SLOT_COUNT
            touchQueue[offset + TOUCH_SLOT_LOGICAL_X] = logicalX
            touchQueue[offset + TOUCH_SLOT_LOGICAL_Y] = logicalY
            touchQueue[offset + TOUCH_SLOT_RAW_X] = rawX
            touchQueue[offset + TOUCH_SLOT_RAW_Y] = rawY
            touchQueue[offset + TOUCH_SLOT_ACTION] = actionCode
            touchCount++
            return true
        }
    }

    companion object {
        private const val FRAME_SLEEP_MS = 16L
        private const val FRAME_NANOS = FRAME_SLEEP_MS * 1_000_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000f
        private const val RENDER_THREAD_JOIN_MS = 100L
        private const val MAX_DELTA_SECONDS = 0.05f
        private const val STATS_LOG_INTERVAL_NANOS = 1_000_000_000L
        private const val TOUCH_QUEUE_CAPACITY = 128
        private const val TOUCH_EVENT_SLOT_COUNT = 5
        private const val TOUCH_SLOT_LOGICAL_X = 0
        private const val TOUCH_SLOT_LOGICAL_Y = 1
        private const val TOUCH_SLOT_RAW_X = 2
        private const val TOUCH_SLOT_RAW_Y = 3
        private const val TOUCH_SLOT_ACTION = 4
        private const val LOG_TAG = "Pc98SurfaceView"
    }
}
