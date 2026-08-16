@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.core.ActiveTimerScene
import com.example.timeboxvibe.engine.core.CANONICAL_UI_UNIT
import com.example.timeboxvibe.engine.core.DisplayScalePolicy
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
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
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
import platform.windows.SRCCOPY
import platform.windows.SW_SHOW
import platform.windows.ScreenToClient
import platform.windows.SetStretchBltMode
import platform.windows.SetWaitableTimer
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
import kotlin.concurrent.Volatile

private const val WINDOW_CLASS_NAME = "TimeBoxWin32Terminal"

internal class Win32Host {
    val alarm = Win32AlarmScheduler()
    val audio = Win32Audio()
    val power = Win32Power()
    val actions = Win32TimerActions(alarm, audio, power)
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
        host.actions.attachWindow(hwnd)
        ShowWindow(hwnd, SW_SHOW)
        UpdateWindow(hwnd)
        applyResizeFromHwnd(host, hwnd)

        SceneManager.init(host.actions, host.actions)
        SceneManager.switchScene(ActiveTimerScene)

        val msg = alloc<MSG>()
        var lastQpc = queryPerformanceCounter()
        val freq = queryPerformanceFrequency().coerceAtLeast(1L)
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
            val elapsed = frameStart - lastQpc
            lastQpc = frameStart
            val dt = (elapsed.toDouble() / freq.toDouble()).toFloat().coerceAtMost(MAX_DELTA_SECONDS)

            val canvas = host.canvas
            val renderer = host.renderer
            if (canvas != null && renderer != null && host.logicalWidth > 0f && host.logicalHeight > 0f) {
                val touches = host.drainTouches()
                host.actions.pump(frameStart)
                host.audio.pump()
                SceneManager.setLogicalBounds(host.logicalWidth, host.logicalHeight)
                SceneManager.update(dt, host.drainQueue, touches)
                SceneManager.render(renderer, host.logicalWidth, host.logicalHeight)
                host.present()
            }

            waitForNextFrame(host, frameStart, freq)
        }
    }
    host.shutdown()
    activeHost = null
}

private fun applyResizeFromHwnd(host: Win32Host, hwnd: HWND?) {
    if (hwnd == null) return
    memScoped {
        val rect = alloc<RECT>()
        GetClientRect(hwnd, rect.ptr)
        val width = (rect.right - rect.left)
        val height = (rect.bottom - rect.top)
        host.applyClientSize(width, height, windowDpi(hwnd))
    }
}

private fun waitForNextFrame(host: Win32Host, frameStartQpc: Long, freq: Long) {
    val timer = host.frameTimer
    val elapsedQpc = queryPerformanceCounter() - frameStartQpc
    val frameQpc = (FRAME_NANOS * freq) / 1_000_000_000L
    val remain = frameQpc - elapsedQpc
    if (remain <= 0L || timer == null) return
    memScoped {
        val due = alloc<LARGE_INTEGER>()
        due.QuadPart = -((remain * 1_000_000_000L) / freq) / 100L
        SetWaitableTimer(timer, due.ptr, 0, null, null, FALSE)
        val handles = allocArray<platform.windows.HANDLEVar>(1)
        handles[0] = timer
        MsgWaitForMultipleObjects(1u, handles, FALSE, INFINITE, QS_ALLINPUT.toUInt())
    }
}

private fun win32WndProc(hwnd: HWND?, message: UINT, wParam: WPARAM, lParam: LPARAM): LRESULT {
    val host = activeHost
    when (message.toInt()) {
        WM_SIZE -> {
            if (host != null) applyResizeFromHwnd(host, hwnd)
            return 0
        }
        WM_DPICHANGED_VALUE -> {
            if (host != null) applyResizeFromHwnd(host, hwnd)
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
            host?.enqueueTouch(lowWord(lParam), highWord(lParam), ENGINE_TOUCH_DOWN)
            return 0
        }
        WM_MOUSEMOVE -> {
            if ((wParam.toInt() and platform.windows.MK_LBUTTON.toInt()) != 0) {
                host?.enqueueTouch(lowWord(lParam), highWord(lParam), ENGINE_TOUCH_MOVE)
            }
            return 0
        }
        WM_LBUTTONUP -> {
            host?.enqueueTouch(lowWord(lParam), highWord(lParam), ENGINE_TOUCH_UP)
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
            host?.actions?.onOsAlarm()
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

private fun lowWord(value: LPARAM): Int = value.toInt() and 0xFFFF
private fun highWord(value: LPARAM): Int = (value.toInt() ushr 16) and 0xFFFF

private fun signedLowWord(value: Long): Int = value.toInt().toShort().toInt()
private fun signedHighWord(value: Long): Int = (value.toInt() shr 16).toShort().toInt()
