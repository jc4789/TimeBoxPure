@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.core.EngineHaptics
import com.example.timeboxvibe.engine.core.PlatformInputTrigger
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import platform.windows.FLASHWINFO
import platform.windows.FlashWindowEx
import platform.windows.HWND
import platform.windows.MessageBeep
import kotlin.concurrent.Volatile

/** Native feedback terminal. It contains no timer or scene policy. */
internal class Win32Feedback : PlatformInputTrigger {
    @Volatile
    private var hwnd: HWND? = null

    fun attachWindow(target: HWND?) {
        hwnd = target
    }

    fun flashAlarm() {
        val target = hwnd ?: return
        memScoped {
            val info = alloc<FLASHWINFO>()
            info.cbSize = sizeOf<FLASHWINFO>().toUInt()
            info.hwnd = target
            info.dwFlags = FLASHW_ALL or FLASHW_TIMERNOFG
            info.uCount = 5u
            info.dwTimeout = 0u
            FlashWindowEx(info.ptr)
        }
    }

    fun reportAudioFailure(isAlarm: Boolean) {
        println("Win32 audio output is unavailable")
        if (isAlarm) {
            MessageBeep(MESSAGE_BEEP_WARNING)
        }
    }

    override fun triggerKeyboard() {
        // The physical keyboard already enters through WM_CHAR / WM_KEYDOWN.
    }

    override fun performHapticFeedback(type: Int) {
        if (type == EngineHaptics.IMPACT) {
            MessageBeep(0u)
        }
    }
}
