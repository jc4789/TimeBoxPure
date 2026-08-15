@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import platform.windows.GetModuleHandleW
import platform.windows.GetProcAddress
import platform.windows.HWND
import platform.windows.SetProcessDPIAware
import platform.windows.UINT
import platform.windows.WINBOOL

internal const val CREATE_WAITABLE_TIMER_HIGH_RESOLUTION: UInt = 0x00000002u
internal const val TIMER_ALL_ACCESS: UInt = 0x001F0003u
internal const val ES_SYSTEM_REQUIRED: UInt = 0x00000001u
internal const val ES_DISPLAY_REQUIRED: UInt = 0x00000002u
internal const val ES_CONTINUOUS: UInt = 0x80000000u
internal const val WM_APP_VALUE: Int = 0x8000
internal const val WM_APP_ALARM_VALUE: Int = 0x8001
internal const val WM_DPICHANGED_VALUE: Int = 0x02E0
internal const val USER_DEFAULT_SCREEN_DPI: Int = 96
internal const val FLASHW_ALL: UInt = 0x00000003u
internal const val FLASHW_TIMERNOFG: UInt = 0x0000000Cu
internal const val CALLBACK_EVENT: UInt = 0x00050000u
internal const val WAVE_MAPPER: UInt = 0xFFFFFFFFu
internal const val WAVE_FORMAT_PCM: UShort = 1u
internal const val WHDR_DONE: UInt = 0x00000001u
internal const val MMSYSERR_NOERROR: UInt = 0u
internal const val POWER_REQUEST_CONTEXT_VERSION_VALUE: UInt = 0u
internal const val POWER_REQUEST_CONTEXT_SIMPLE_STRING_VALUE: UInt = 1u

internal const val FRAME_NANOS = 16_000_000L
internal const val MAX_DELTA_SECONDS = 0.05f
internal const val LOGICAL_ENGINE_DENSITY = 1f
internal const val TOUCH_QUEUE_CAPACITY = 128
internal const val TOUCH_EVENT_SLOT_COUNT = 5
internal const val TOUCH_SLOT_LOGICAL_X = 0
internal const val TOUCH_SLOT_LOGICAL_Y = 1
internal const val TOUCH_SLOT_RAW_X = 2
internal const val TOUCH_SLOT_RAW_Y = 3
internal const val TOUCH_SLOT_ACTION = 4
internal const val MIN_SCANLINE_STEP = 2
internal const val AUDIO_SAMPLE_RATE = 48000
internal const val AUDIO_CHUNK_FRAMES = 1024
internal const val AUDIO_HEADER_COUNT = 2
internal const val GENTLE_REMINDER_MS = 5000L
internal const val FILETIME_TICKS_PER_MILLISECOND = 10_000L

internal fun rgbColor(r: Int, g: Int, b: Int): UInt {
    return (r or (g shl 8) or (b shl 16)).toUInt()
}

internal fun enablePerMonitorDpi(): Boolean {
    val user32 = GetModuleHandleW("user32")
    if (user32 != null) {
        val setContext = GetProcAddress(user32, "SetProcessDpiAwarenessContext")
        if (setContext != null) {
            val fn = setContext.reinterpret<CFunction<(COpaquePointer?) -> WINBOOL>>()
            val context = (-4L).toCPointer<CPointed>()
            if (fn(context) != 0) return true
        }
    }
    return SetProcessDPIAware() != 0
}

internal fun windowDpi(hwnd: HWND?): Int {
    val user32 = GetModuleHandleW("user32") ?: return USER_DEFAULT_SCREEN_DPI
    val getDpi = GetProcAddress(user32, "GetDpiForWindow")
    if (getDpi != null && hwnd != null) {
        val fn = getDpi.reinterpret<CFunction<(HWND?) -> UINT>>()
        val dpi = fn(hwnd).toInt()
        if (dpi > 0) return dpi
    }
    return USER_DEFAULT_SCREEN_DPI
}

internal fun platformDensityFromDpi(dpi: Int): Float {
    if (dpi <= 0) return 1f
    return dpi.toFloat() / USER_DEFAULT_SCREEN_DPI.toFloat()
}
