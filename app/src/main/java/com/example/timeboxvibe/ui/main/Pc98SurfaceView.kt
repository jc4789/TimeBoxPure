package com.example.timeboxvibe.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.timeboxvibe.engine.core.EngineInputCodes
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_CANCEL
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_DOWN
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_UP
import com.example.timeboxvibe.engine.core.ScaledProceduralRenderer
import com.example.timeboxvibe.engine.core.SceneManager
import com.example.timeboxvibe.platform.android.AndroidEngineCanvas

/**
 * A highly optimized, 100% crash-proof SurfaceView utilizing direct background Math
 * rendering and scene routing.
 */
class Pc98SurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var renderThread: RenderThread? = null
    @Volatile var currentScaleFactor: Int = MIN_SCALE

    // THE FIX: No Magic Numbers. These must be dynamically calculated in surfaceChanged.
    @Volatile private var dynamicLogicalWidth: Float = 0f
    @Volatile private var dynamicLogicalHeight: Float = 0f
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
    private var scanlinesEnabled: Boolean = true
    private var scanlineIntensity: Float = DEFAULT_SCANLINE_INTENSITY
    
    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        // Keep transparent to allow Compose UI to sandwich if needed, 
        // BUT remember the engine must draw a solid rect to clear the frame!
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // THE FIX: Do NOT initialize the Renderer here. 
        // We do not know the screen dimensions yet. Wait for surfaceChanged.
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        val physicalWidth = width.toFloat()
        val physicalHeight = height.toFloat()
        val osDensity = safeDensity(context.resources.displayMetrics.density)
        val scale = deriveScale(physicalWidth, osDensity)
        val logW = physicalWidth / scale
        val logH = physicalHeight / scale

        // Lock in the mathematically correct dimensions
        currentScaleFactor = scale
        dynamicLogicalWidth = logW
        dynamicLogicalHeight = logH

        // 5. Safely Boot the Engine with the correct dynamic bounds
        renderThread?.stopThread()
        
        // Pass a dummy canvas for initialization; RenderThread will pass the locked hardware canvas during draw
        val dummyCanvas = Canvas()
        val engineCanvas = AndroidEngineCanvas(dummyCanvas, dynamicLogicalWidth, dynamicLogicalHeight, currentScaleFactor.toFloat())
        val renderer = ScaledProceduralRenderer(engineCanvas)

        renderThread = RenderThread(
            holder,
            renderer,
            engineCanvas,
            osDensity,
            this
        ).apply {
            setScanlineSettings(scanlinesEnabled, scanlineIntensity)
            startThread()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderThread?.stopThread()
        renderThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE) return true

        val actionCode = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> ENGINE_TOUCH_DOWN
            MotionEvent.ACTION_UP -> ENGINE_TOUCH_UP
            MotionEvent.ACTION_CANCEL -> ENGINE_TOUCH_CANCEL
            else -> return true
        }
        
        // Safety check: Don't process touches before the matrix is built
        if (currentScaleFactor <= 0) return true

        // THE FIX: Reverse the exact scale factor used by the renderer
        val rawX = event.x.toInt()
        val rawY = event.y.toInt()
        val logicalX = (event.x / currentScaleFactor).toInt()
        val logicalY = (event.y / currentScaleFactor).toInt()
        
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

    fun updateScanlines(enabled: Boolean, intensity: Float) {
        this.scanlinesEnabled = enabled
        this.scanlineIntensity = intensity
        renderThread?.setScanlineSettings(enabled, intensity)
    }

    private class RenderThread(
        private val surfaceHolder: SurfaceHolder,
        private val renderer: ScaledProceduralRenderer,
        private val engineCanvas: AndroidEngineCanvas,
        private val displayDensity: Float,
        private val viewRef: Pc98SurfaceView
    ) : Thread("Pc98RenderThread") {

        @Volatile
        private var running = false

        private val scanlinePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = SCANLINE_STROKE_WIDTH
        }

        private var scanlinesEnabled = true
        private var scanlineIntensity = DEFAULT_SCANLINE_INTENSITY
        private val localTouchQueue = IntArray(TOUCH_QUEUE_CAPACITY * TOUCH_EVENT_SLOT_COUNT)
        private var localTouchCount = 0
        private var lastStatsLogNanos = 0L

        fun setScanlineSettings(enabled: Boolean, intensity: Float) {
            this.scanlinesEnabled = enabled
            this.scanlineIntensity = intensity
        }

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

                drawFrame(dt)
                viewRef.framesRendered++
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
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas == null) return

                val viewWidth = canvas.width.toFloat()
                val viewHeight = canvas.height.toFloat()
                val scaleFactor = deriveScale(viewWidth, displayDensity)
                val logicalWidth = viewWidth / scaleFactor
                val logicalHeight = viewHeight / scaleFactor

                viewRef.currentScaleFactor = scaleFactor
                viewRef.dynamicLogicalWidth = logicalWidth
                viewRef.dynamicLogicalHeight = logicalHeight

                canvas.save()
                canvas.scale(scaleFactor.toFloat(), scaleFactor.toFloat())
                engineCanvas.bind(canvas)
                engineCanvas.width = logicalWidth
                engineCanvas.height = logicalHeight
                engineCanvas.density = MIN_SCALE.toFloat()
                SceneManager.setLogicalBounds(logicalWidth, logicalHeight)
                drainTouchInputFastCopy()
                viewRef.localTouchCountThisFrame = localTouchCount
                SceneManager.update(dt, localTouchQueue, localTouchCount)
                viewRef.updatesCalled++
                viewRef.lastDt = dt
                SceneManager.render(renderer, logicalWidth, logicalHeight)
                canvas.restore()

                if (scanlinesEnabled && scanlineIntensity > 0f) {
                    val alpha = (scanlineIntensity * SCANLINE_ALPHA_MULTIPLIER).toInt().coerceIn(0, MAX_ALPHA)
                    scanlinePaint.color = Color.argb(alpha, 0, 0, 0)

                    val lineStep = maxOf(MIN_SCANLINE_STEP, displayDensity)
                    var y = 0f
                    while (y < viewHeight) {
                        canvas.drawLine(0f, y, viewWidth, y, scanlinePaint)
                        y += lineStep
                    }
                }
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
                "framesRendered=${viewRef.framesRendered} updatesCalled=${viewRef.updatesCalled} currentSceneName=${SceneManager.currentSceneName()} lastDt=${viewRef.lastDt} localTouchCountThisFrame=${viewRef.localTouchCountThisFrame} totalTouchesQueued=${viewRef.touchesQueued} totalTouchesDrained=${viewRef.touchesDrained} totalTouchesDropped=${viewRef.touchesDropped}"
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
        private const val MIN_SAFE_LOGICAL_WIDTH = 320f
        private const val MAX_SAFE_LOGICAL_WIDTH = 1200f
        private const val MIN_SCALE = 1
        private const val MIN_VALID_DENSITY = 0.1f
        private const val MAX_VALID_DENSITY = 10.0f
        private const val DEFAULT_DENSITY = 1.0f
        private const val DENSITY_SCALE_MULTIPLIER = 2.0
        private const val DEFAULT_SCANLINE_INTENSITY = 0.3f
        private const val SCANLINE_STROKE_WIDTH = 1.0f
        private const val SCANLINE_ALPHA_MULTIPLIER = 180f
        private const val MAX_ALPHA = 255
        private const val MIN_SCANLINE_STEP = 2f
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
        private fun safeDensity(displayDensity: Float): Float {
            return if (
                displayDensity.isFinite() &&
                displayDensity > MIN_VALID_DENSITY &&
                displayDensity < MAX_VALID_DENSITY
            ) {
                displayDensity
            } else {
                DEFAULT_DENSITY
            }
        }

        private fun deriveScale(physicalWidth: Float, displayDensity: Float): Int {
            var scale = Math.floor(displayDensity * DENSITY_SCALE_MULTIPLIER).toInt().coerceAtLeast(MIN_SCALE)
            while (scale > MIN_SCALE && (physicalWidth / scale) < MIN_SAFE_LOGICAL_WIDTH) {
                scale--
            }
            while ((physicalWidth / scale) > MAX_SAFE_LOGICAL_WIDTH) {
                scale++
            }
            return scale
        }
    }
}
