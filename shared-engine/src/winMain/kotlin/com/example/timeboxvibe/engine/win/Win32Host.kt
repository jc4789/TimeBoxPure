@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.core.ActiveTimerScene
import com.example.timeboxvibe.engine.core.CANONICAL_UI_UNIT
import com.example.timeboxvibe.engine.core.DisplayScalePolicy
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_CANCEL
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_DOWN
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_MOVE
import com.example.timeboxvibe.engine.core.ENGINE_TOUCH_UP
import com.example.timeboxvibe.engine.core.EngineInputCodes
import com.example.timeboxvibe.engine.core.ScaledProceduralRenderer
import com.example.timeboxvibe.engine.core.SceneManager
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.pointed
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.wcstr
import platform.windows.BI_RGB
import platform.windows.BITMAPINFO
import platform.windows.CS_HREDRAW
import platform.windows.CS_OWNDC
import platform.windows.CS_VREDRAW
import platform.windows.CreateSolidBrush
import platform.windows.CreateWaitableTimerExW
import platform.windows.CreateWindowExW
import platform.windows.DIB_RGB_COLORS
import platform.windows.DefWindowProcW
import platform.windows.DispatchMessageW
import platform.windows.FALSE
import platform.windows.GetCapture
import platform.windows.GetClientRect
import platform.windows.GetDC
import platform.windows.GetModuleHandleW
import platform.windows.HANDLE
import platform.windows.HWND
import platform.windows.IDC_ARROW
import platform.windows.INFINITE
import platform.windows.LARGE_INTEGER
import platform.windows.LPARAM
import platform.windows.LRESULT
import platform.windows.LoadCursorW
import platform.windows.MSG
import platform.windows.MsgWaitForMultipleObjects
import platform.windows.PeekMessageW
import platform.windows.PM_REMOVE
import platform.windows.POINT
import platform.windows.PostQuitMessage
import platform.windows.QS_ALLINPUT
import platform.windows.RECT
import platform.windows.RegisterClassExW
import platform.windows.ReleaseDC
import platform.windows.ReleaseCapture
import platform.windows.SRCCOPY
import platform.windows.SIZE_MINIMIZED
import platform.windows.SW_SHOW
import platform.windows.ScreenToClient
import platform.windows.SetStretchBltMode
import platform.windows.SetCapture
import platform.windows.SetWaitableTimer
import platform.windows.SetWindowPos
import platform.windows.ShowWindow
import platform.windows.StretchDIBits
import platform.windows.TranslateMessage
import platform.windows.UINT
import platform.windows.UpdateWindow
import platform.windows.VK_BACK
import platform.windows.VK_LEFT
import platform.windows.VK_RETURN
import platform.windows.VK_RIGHT
import platform.windows.WM_CHAR
import platform.windows.WM_CANCELMODE
import platform.windows.WM_CAPTURECHANGED
import platform.windows.WM_DESTROY
import platform.windows.WM_KEYDOWN
import platform.windows.WM_LBUTTONDOWN
import platform.windows.WM_LBUTTONUP
import platform.windows.WM_MOUSEMOVE
import platform.windows.WM_PAINT
import platform.windows.WM_QUIT
import platform.windows.WM_SIZE
import platform.windows.WNDCLASSEXW
import platform.windows.WPARAM
import platform.windows.WS_OVERLAPPEDWINDOW
import platform.windows.WS_VISIBLE
import platform.windows.SWP_NOACTIVATE
import platform.windows.SWP_NOZORDER
import platform.windows.WAIT_OBJECT_0
import kotlin.concurrent.Volatile

private const val WINDOW_CLASS_NAME = "TimeBoxWin32Terminal"

internal class Win32Host {
    val alarm = Win32AlarmScheduler()
    val audio = Win32Audio()
    val power = Win32Power()
    val feedback = Win32Feedback()
    val settings = Win32SettingsStore()
    val qpcFrequency = queryPerformanceFrequency()
    val actions = Win32TimerController(alarm, audio, power, feedback, settings, qpcFrequency)
    val frameTimer: HANDLE? = CreateWaitableTimerExW(
        null,
        null,
        CREATE_WAITABLE_TIMER_HIGH_RESOLUTION,
        TIMER_ALL_ACCESS
    ) ?: CreateWaitableTimerExW(null, null, 0u, TIMER_ALL_ACCESS)

    var hwnd: HWND? = null
    var canvas: Win32EngineCanvas? = null
    var renderer: ScaledProceduralRenderer? = null
    var scaleFactor: Int = DisplayScalePolicy.MIN_SCALE
    var logicalWidth = 0f
    var logicalHeight = 0f
    var physicalWidth = 0
    var physicalHeight = 0
    var presentationDensity = 1f
    private val touchQueue = IntArray(TOUCH_QUEUE_CAPACITY * TOUCH_EVENT_SLOT_COUNT)
    val drainQueue = IntArray(TOUCH_QUEUE_CAPACITY * TOUCH_EVENT_SLOT_COUNT)
    private var touchCount = 0
    private var pointerDown = false
    private var lastPointerX = 0
    private var lastPointerY = 0
    var minimized = false
    @Volatile
    var running = true

    fun enqueueTouch(rawX: Int, rawY: Int, action: Int) {
        if (scaleFactor <= 0) return
        val logicalX = rawX / scaleFactor
        val logicalY = rawY / scaleFactor
        if (touchCount >= TOUCH_QUEUE_CAPACITY) return
        val offset = touchCount * TOUCH_EVENT_SLOT_COUNT
        touchQueue[offset + TOUCH_SLOT_LOGICAL_X] = logicalX
        touchQueue[offset + TOUCH_SLOT_LOGICAL_Y] = logicalY
        touchQueue[offset + TOUCH_SLOT_RAW_X] = rawX
        touchQueue[offset + TOUCH_SLOT_RAW_Y] = rawY
        touchQueue[offset + TOUCH_SLOT_ACTION] = action
        touchCount++
    }

    fun drainTouches(): Int {
        val count = touchCount
        if (count > 0) {
            var i = 0
            val n = count * TOUCH_EVENT_SLOT_COUNT
            while (i < n) {
                drainQueue[i] = touchQueue[i]
                i++
            }
            touchCount = 0
        }
        return count
    }

    fun beginPointer(rawX: Int, rawY: Int) {
        pointerDown = true
        lastPointerX = rawX
        lastPointerY = rawY
        enqueueTouch(rawX, rawY, ENGINE_TOUCH_DOWN)
    }

    fun movePointer(rawX: Int, rawY: Int) {
        if (!pointerDown) return
        lastPointerX = rawX
        lastPointerY = rawY
        enqueueTouch(rawX, rawY, ENGINE_TOUCH_MOVE)
    }

    fun endPointer(rawX: Int, rawY: Int) {
        if (!pointerDown) return
        pointerDown = false
        lastPointerX = rawX
        lastPointerY = rawY
        enqueueTouch(rawX, rawY, ENGINE_TOUCH_UP)
    }

    fun cancelPointer() {
        if (!pointerDown) return
        pointerDown = false
        enqueueTouch(lastPointerX, lastPointerY, ENGINE_TOUCH_CANCEL)
    }

    fun applyClientSize(width: Int, height: Int, dpi: Int) {
        if (width <= 0 || height <= 0) return
        physicalWidth = width
        physicalHeight = height
        val density = platformDensityFromDpi(dpi)
        presentationDensity = if (density.isFinite() && density > 0.1f && density < 10f) density else 1f
        val scale = DisplayScalePolicy.deriveScale(width.toFloat(), height.toFloat(), presentationDensity)
        val logW = width.toFloat() / scale
        val logH = height.toFloat() / scale
        scaleFactor = scale
        logicalWidth = logW
        logicalHeight = logH
        val existing = canvas
        if (existing == null) {
            val next = Win32EngineCanvas(logW, logH, LOGICAL_ENGINE_DENSITY, scale, width, height)
            canvas = next
            renderer = ScaledProceduralRenderer(next)
        } else {
            existing.presentationScale = scale
            existing.density = LOGICAL_ENGINE_DENSITY
            existing.resizeFramebuffer(logW, logH, width, height)
            renderer = ScaledProceduralRenderer(existing)
        }
    }

    fun present() {
        val window = hwnd ?: return
        val fb = canvas ?: return
        val dc = GetDC(window) ?: return
        try {
            memScoped {
                val info = alloc<BITMAPINFO>()
                info.bmiHeader.biSize = sizeOf<platform.windows.BITMAPINFOHEADER>().toUInt()
                info.bmiHeader.biWidth = fb.pixelWidth
                info.bmiHeader.biHeight = -fb.pixelHeight
                info.bmiHeader.biPlanes = 1u
                info.bmiHeader.biBitCount = 32u
                info.bmiHeader.biCompression = BI_RGB.toUInt()
                SetStretchBltMode(dc, COLORONCOLOR_MODE)
                fb.pixels.usePinned { pinned ->
                    StretchDIBits(
                        dc,
                        0,
                        0,
                        fb.pixelWidth,
                        fb.pixelHeight,
                        0,
                        0,
                        fb.pixelWidth,
                        fb.pixelHeight,
                        pinned.addressOf(0),
                        info.ptr,
                        DIB_RGB_COLORS.toUInt(),
                        SRCCOPY
                    )
                }
            }
        } finally {
            ReleaseDC(window, dc)
        }
    }

    fun shutdown() {
        running = false
        actions.shutdown()
        settings.shutdown()
        audio.shutdown()
        alarm.shutdown()
        power.shutdown()
        frameTimer?.let { platform.windows.CloseHandle(it) }
    }
}

internal var activeHost: Win32Host? = null

internal fun runWin32Terminal() {
    enablePerMonitorDpi()
    val host = Win32Host()
    activeHost = host
    host.alarm.startWaiter()

    memScoped {
        val module = GetModuleHandleW(null)
        val className = WINDOW_CLASS_NAME.wcstr
        val wc = alloc<WNDCLASSEXW>()
        wc.cbSize = sizeOf<WNDCLASSEXW>().toUInt()
        wc.style = (CS_OWNDC.toInt() or CS_HREDRAW.toInt() or CS_VREDRAW.toInt()).toUInt()
        wc.lpfnWndProc = staticCFunction(::win32WndProc)
        wc.cbClsExtra = 0
        wc.cbWndExtra = 0
        wc.hInstance = module
        wc.hIcon = null
        wc.hCursor = LoadCursorW(null, IDC_ARROW)
        wc.hbrBackground = CreateSolidBrush(rgbColor(0, 0, 0))
        wc.lpszMenuName = null
        wc.lpszClassName = className.ptr
        wc.hIconSm = null
        if (RegisterClassExW(wc.ptr).toInt() == 0) {
            println("RegisterClassExW failed")
            host.shutdown()
            return
        }

        val hwnd = CreateWindowExW(
            0u,
            WINDOW_CLASS_NAME,
            "TimeBox",
            (WS_OVERLAPPEDWINDOW.toInt() or WS_VISIBLE.toInt()).toUInt(),
            platform.windows.CW_USEDEFAULT,
            platform.windows.CW_USEDEFAULT,
            platform.windows.CW_USEDEFAULT,
            platform.windows.CW_USEDEFAULT,
            null,
            null,
            module,
            null
        )
        if (hwnd == null) {
            println("CreateWindowExW failed")
            host.shutdown()
            return
        }
        host.hwnd = hwnd
        host.alarm.targetHwnd = hwnd
        host.feedback.attachWindow(hwnd)
        ShowWindow(hwnd, SW_SHOW)
        UpdateWindow(hwnd)
        applyResizeFromHwnd(host, hwnd)

        SceneManager.init(host.actions, host.feedback)
        SceneManager.switchScene(ActiveTimerScene)

        val msg = alloc<MSG>()
        var lastQpc = queryPerformanceCounter()
        val freq = host.qpcFrequency
        val frameQpc = ((FRAME_NANOS * freq) / NANOSECONDS_PER_SECOND).coerceAtLeast(1L)
        var nextFrameQpc = lastQpc
        while (host.running) {
            while (PeekMessageW(msg.ptr, null, 0u, 0u, PM_REMOVE.toUInt()) != 0) {
                if (msg.message.toInt() == WM_QUIT) {
                    host.running = false
                    break
                }
                TranslateMessage(msg.ptr)
                DispatchMessageW(msg.ptr)
            }
            if (!host.running) break

            val frameStart = queryPerformanceCounter()
            if (frameStart < nextFrameQpc) {
                waitUntilFrameOrMessage(host, nextFrameQpc, freq)
                continue
            }
            val elapsed = frameStart - lastQpc
            lastQpc = frameStart
            val dt = (elapsed.toDouble() / freq.toDouble()).toFloat().coerceAtMost(MAX_DELTA_SECONDS)
            nextFrameQpc += frameQpc
            if (nextFrameQpc <= frameStart) {
                nextFrameQpc = frameStart + frameQpc
            }

            host.actions.pump(frameStart)
            host.audio.pump()

            val canvas = host.canvas
            val renderer = host.renderer
            val touches = host.drainTouches()
            if (!host.minimized && canvas != null && renderer != null && host.logicalWidth > 0f && host.logicalHeight > 0f) {
                SceneManager.setLogicalBounds(host.logicalWidth, host.logicalHeight)
                SceneManager.update(dt, host.drainQueue, touches)
                SceneManager.render(renderer, host.logicalWidth, host.logicalHeight)
                host.present()
            } else if (touches > 0) {
                SceneManager.update(0f, host.drainQueue, touches)
            }
        }
    }
    host.shutdown()
    activeHost = null
}

private fun applyResizeFromHwnd(host: Win32Host, hwnd: HWND?, dpi: Int = windowDpi(hwnd)) {
    if (hwnd == null) return
    memScoped {
        val rect = alloc<RECT>()
        GetClientRect(hwnd, rect.ptr)
        val width = (rect.right - rect.left)
        val height = (rect.bottom - rect.top)
        host.applyClientSize(width, height, dpi)
    }
}

private fun waitUntilFrameOrMessage(host: Win32Host, deadlineQpc: Long, freq: Long) {
    val timer = host.frameTimer
    val remain = deadlineQpc - queryPerformanceCounter()
    if (remain <= 0L) return
    if (timer != null) {
        val waitResult = memScoped {
            val due = alloc<LARGE_INTEGER>()
            val remainNanos = (remain * NANOSECONDS_PER_SECOND) / freq
            due.QuadPart = -(remainNanos / HUNDRED_NANOSECOND_NANOS).coerceAtLeast(1L)
            if (SetWaitableTimer(timer, due.ptr, 0, null, null, FALSE) == 0) {
                return@memScoped null
            }
            val handles = allocArray<platform.windows.HANDLEVar>(1)
            handles[0] = timer
            MsgWaitForMultipleObjects(1u, handles, FALSE, INFINITE, QS_ALLINPUT.toUInt())
        }
        if (waitResult == WAIT_OBJECT_0 || waitResult == WAIT_OBJECT_0 + 1u) return
    }
    val timeoutMillis = ((remain * MILLISECONDS_PER_SECOND + freq - 1L) / freq)
        .coerceIn(1L, UInt.MAX_VALUE.toLong())
        .toUInt()
    MsgWaitForMultipleObjects(0u, null, FALSE, timeoutMillis, QS_ALLINPUT.toUInt())
}

private fun win32WndProc(hwnd: HWND?, message: UINT, wParam: WPARAM, lParam: LPARAM): LRESULT {
    val host = activeHost
    when (message.toInt()) {
        WM_SIZE -> {
            if (host != null) {
                host.minimized = wParam.toInt() == SIZE_MINIMIZED
                if (host.minimized) {
                    host.cancelPointer()
                    releaseMouseCapture(hwnd)
                } else {
                    applyResizeFromHwnd(host, hwnd)
                }
            }
            return 0
        }
        WM_DPICHANGED_VALUE -> {
            if (host != null) applyDpiSuggestedRect(host, hwnd, wParam, lParam)
            return 0
        }
        WM_PAINT -> {
            host?.present()
            memScoped {
                val ps = alloc<platform.windows.PAINTSTRUCT>()
                platform.windows.BeginPaint(hwnd, ps.ptr)
                platform.windows.EndPaint(hwnd, ps.ptr)
            }
            return 0
        }
        WM_LBUTTONDOWN -> {
            if (host != null) {
                SetCapture(hwnd)
                host.beginPointer(signedLowWord(lParam.toLong()), signedHighWord(lParam.toLong()))
            }
            return 0
        }
        WM_MOUSEMOVE -> {
            if ((wParam.toInt() and platform.windows.MK_LBUTTON.toInt()) != 0) {
                host?.movePointer(signedLowWord(lParam.toLong()), signedHighWord(lParam.toLong()))
            }
            return 0
        }
        WM_LBUTTONUP -> {
            host?.endPointer(signedLowWord(lParam.toLong()), signedHighWord(lParam.toLong()))
            releaseMouseCapture(hwnd)
            return 0
        }
        WM_CAPTURECHANGED, WM_CANCELMODE -> {
            host?.cancelPointer()
            releaseMouseCapture(hwnd)
            return 0
        }
        WM_MOUSEWHEEL_VALUE -> {
            if (host != null) enqueueWheelScroll(host, hwnd, wParam, lParam)
            return 0
        }
        WM_KEYDOWN -> {
            when (wParam.toInt()) {
                VK_BACK.toInt() -> SceneManager.enqueueInput(EngineInputCodes.CMD_BACKSPACE)
                VK_RETURN.toInt() -> SceneManager.enqueueInput(EngineInputCodes.CMD_ENTER)
                VK_LEFT.toInt() -> SceneManager.enqueueInput(EngineInputCodes.CMD_LEFT)
                VK_RIGHT.toInt() -> SceneManager.enqueueInput(EngineInputCodes.CMD_RIGHT)
            }
            return 0
        }
        WM_CHAR -> {
            val code = wParam.toInt() and 0xFFFF
            if (code >= 32) {
                SceneManager.enqueueInput(code)
            }
            return 0
        }
        WM_APP_ALARM_VALUE -> {
            if (host != null && host.alarm.isCurrentNotification(wParam.toInt())) {
                host.actions.onOsAlarm(queryPerformanceCounter())
            }
            return 0
        }
        WM_DESTROY -> {
            host?.running = false
            PostQuitMessage(0)
            return 0
        }
    }
    return DefWindowProcW(hwnd, message, wParam, lParam)
}

private fun applyDpiSuggestedRect(host: Win32Host, hwnd: HWND?, wParam: WPARAM, lParam: LPARAM) {
    if (hwnd == null) return
    val suggested = lParam.toLong().toCPointer<RECT>() ?: return
    val left = suggested.pointed.left
    val top = suggested.pointed.top
    val width = suggested.pointed.right - left
    val height = suggested.pointed.bottom - top
    if (width <= 0 || height <= 0) return
    SetWindowPos(
        hwnd,
        null,
        left,
        top,
        width,
        height,
        (SWP_NOZORDER.toInt() or SWP_NOACTIVATE.toInt()).toUInt()
    )
    applyResizeFromHwnd(host, hwnd, wParam.toInt() and 0xFFFF)
}

private fun releaseMouseCapture(hwnd: HWND?) {
    if (hwnd != null && GetCapture() == hwnd) {
        ReleaseCapture()
    }
}

private fun enqueueWheelScroll(host: Win32Host, hwnd: HWND?, wParam: WPARAM, lParam: LPARAM) {
    if ((wParam.toInt() and MK_LBUTTON_FLAG) != 0) return
    val notches = signedHighWord(wParam.toLong()) / WHEEL_DELTA_STANDARD
    if (notches == 0) return
    val physicalDelta = notches * WHEEL_NOTCH_CELLS * CANONICAL_UI_UNIT * host.scaleFactor
    if (physicalDelta == 0) return
    memScoped {
        val point = alloc<POINT>()
        point.x = signedLowWord(lParam)
        point.y = signedHighWord(lParam)
        ScreenToClient(hwnd, point.ptr)
        val x = point.x
        val y = point.y
        host.enqueueTouch(x, y, ENGINE_TOUCH_DOWN)
        host.enqueueTouch(x, y + physicalDelta, ENGINE_TOUCH_MOVE)
        host.enqueueTouch(x, y + physicalDelta, ENGINE_TOUCH_UP)
    }
}

private fun signedLowWord(value: Long): Int = value.toInt().toShort().toInt()
private fun signedHighWord(value: Long): Int = (value.toInt() shr 16).toShort().toInt()
