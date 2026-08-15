@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.core.PlatformAlarmScheduler
import com.example.timeboxvibe.engine.core.getEpochMillis
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import platform.windows.CancelWaitableTimer
import platform.windows.CloseHandle
import platform.windows.CreateEventW
import platform.windows.CreateThread
import platform.windows.CreateWaitableTimerExW
import platform.windows.DWORD
import platform.windows.FALSE
import platform.windows.HANDLE
import platform.windows.HANDLEVar
import platform.windows.HWND
import platform.windows.INFINITE
import platform.windows.LARGE_INTEGER
import platform.windows.PostMessageW
import platform.windows.SetEvent
import platform.windows.SetWaitableTimer
import platform.windows.TRUE
import platform.windows.WAIT_OBJECT_0
import platform.windows.WaitForMultipleObjects
import platform.windows.WaitForSingleObject
import kotlin.concurrent.Volatile

/**
 * Process-local exact alarm using a high-resolution waitable timer.
 * Signals the UI thread with WM_APP_ALARM; does not own timer logic.
 */
class Win32AlarmScheduler : PlatformAlarmScheduler {
    private val timer: HANDLE? = CreateWaitableTimerExW(
        null,
        null,
        CREATE_WAITABLE_TIMER_HIGH_RESOLUTION,
        TIMER_ALL_ACCESS
    ) ?: CreateWaitableTimerExW(null, null, 0u, TIMER_ALL_ACCESS)

    private val stopEvent: HANDLE? = CreateEventW(null, TRUE, FALSE, null)
    private var waiterThread: HANDLE? = null
    private var stableSelf: StableRef<Win32AlarmScheduler>? = null

    @Volatile
    var targetHwnd: HWND? = null

    @Volatile
    private var running = true

    fun startWaiter() {
        if (timer == null || stopEvent == null || waiterThread != null) return
        val ref = StableRef.create(this)
        stableSelf = ref
        waiterThread = CreateThread(
            null,
            0u,
            staticCFunction { param ->
                val self = param!!.asStableRef<Win32AlarmScheduler>().get()
                self.waiterLoop()
                0u
            },
            ref.asCPointer(),
            0u,
            null
        )
    }

    override fun scheduleExactAlarm(epochMillis: Long) {
        val handle = timer ?: return
        val delayMs = epochMillis - getEpochMillis()
        if (delayMs <= 0L) return
        memScoped {
            val due = alloc<LARGE_INTEGER>()
            due.QuadPart = -delayMs * FILETIME_TICKS_PER_MILLISECOND
            SetWaitableTimer(handle, due.ptr, 0, null, null, FALSE)
        }
    }

    override fun cancelAlarm() {
        val handle = timer ?: return
        CancelWaitableTimer(handle)
    }

    fun shutdown() {
        running = false
        stopEvent?.let { SetEvent(it) }
        cancelAlarm()
        val thread = waiterThread
        if (thread != null) {
            WaitForSingleObject(thread, 1000u)
            CloseHandle(thread)
            waiterThread = null
        }
        timer?.let { CloseHandle(it) }
        stopEvent?.let { CloseHandle(it) }
        stableSelf?.dispose()
        stableSelf = null
    }

    private fun waiterLoop() {
        val alarm = timer ?: return
        val stop = stopEvent ?: return
        memScoped {
            val handles = allocArray<HANDLEVar>(2)
            handles[0] = alarm
            handles[1] = stop
            while (running) {
                val signaled = WaitForMultipleObjects(2u, handles, FALSE, INFINITE)
                if (!running) return
                if (signaled == WAIT_OBJECT_0) {
                    val hwnd = targetHwnd
                    if (hwnd != null) {
                        PostMessageW(hwnd, WM_APP_ALARM_VALUE.toUInt(), 0u, 0)
                    }
                } else {
                    return
                }
            }
        }
    }
}
