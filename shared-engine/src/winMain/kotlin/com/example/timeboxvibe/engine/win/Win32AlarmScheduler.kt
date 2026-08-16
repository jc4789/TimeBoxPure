@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.core.PlatformAlarmScheduler
import com.example.timeboxvibe.engine.core.getEpochMillis
import kotlinx.cinterop.COpaquePointer
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
import platform.windows.CreateMutexW
import platform.windows.CreateThread
import platform.windows.CreateWaitableTimerExW
import platform.windows.FALSE
import platform.windows.HANDLE
import platform.windows.HANDLEVar
import platform.windows.HWND
import platform.windows.INFINITE
import platform.windows.LARGE_INTEGER
import platform.windows.PostMessageW
import platform.windows.ReleaseMutex
import platform.windows.SetEvent
import platform.windows.SetWaitableTimer
import platform.windows.TRUE
import platform.windows.WAIT_OBJECT_0
import platform.windows.WAIT_ABANDONED
import platform.windows.WaitForMultipleObjects
import platform.windows.WaitForSingleObject
import kotlin.concurrent.Volatile

/**
 * Process-local waitable-timer terminal.
 *
 * The UI thread publishes schedule/cancel commands. Only the waiter thread
 * arms the native timer, so the generation attached to a signal cannot be
 * confused with a newer reservation.
 */
class Win32AlarmScheduler : PlatformAlarmScheduler {
    private val timer: HANDLE? = CreateWaitableTimerExW(
        null,
        null,
        CREATE_WAITABLE_TIMER_HIGH_RESOLUTION,
        TIMER_ALL_ACCESS
    ) ?: CreateWaitableTimerExW(null, null, 0u, TIMER_ALL_ACCESS)
    private val commandEvent: HANDLE? = CreateEventW(null, FALSE, FALSE, null)
    private val stopEvent: HANDLE? = CreateEventW(null, TRUE, FALSE, null)
    private val stateMutex: HANDLE? = CreateMutexW(null, FALSE, null)

    private var waiterThread: HANDLE? = null
    private var stableSelf: StableRef<Win32AlarmScheduler>? = null
    private var pendingDeadlineMillis = 0L
    private var pendingGeneration = 0
    private var pendingArmed = false
    private var closed = false

    @Volatile
    var targetHwnd: HWND? = null

    @Volatile
    private var running = true

    @Volatile
    private var currentGeneration = 0

    fun startWaiter(): Boolean {
        if (waiterThread != null) return true
        if (timer == null || commandEvent == null || stopEvent == null || stateMutex == null) {
            println("Win32 alarm initialization failed")
            return false
        }
        val ref = StableRef.create(this)
        val thread = CreateThread(
            null,
            0u,
            staticCFunction(::win32AlarmWaiterThunk),
            ref.asCPointer(),
            0u,
            null
        )
        if (thread == null) {
            ref.dispose()
            println("CreateThread failed for Win32 alarm waiter")
            return false
        }
        stableSelf = ref
        waiterThread = thread
        return true
    }

    override fun scheduleExactAlarm(epochMillis: Long) {
        if (!running || closed) return
        val generation = nextGeneration()
        if (!withStateLock {
            pendingDeadlineMillis = epochMillis
            pendingGeneration = generation
            pendingArmed = true
        }) {
            println("Win32 alarm state lock failed while scheduling")
            return
        }
        if (commandEvent?.let { SetEvent(it) } == 0) {
            println("SetEvent failed while scheduling Win32 alarm")
        }
    }

    override fun cancelAlarm() {
        if (closed) return
        val generation = nextGeneration()
        if (!withStateLock {
            pendingDeadlineMillis = 0L
            pendingGeneration = generation
            pendingArmed = false
        }) {
            println("Win32 alarm state lock failed while cancelling")
            return
        }
        if (running && commandEvent?.let { SetEvent(it) } == 0) {
            println("SetEvent failed while cancelling Win32 alarm")
        }
    }

    fun isCurrentNotification(generation: Int): Boolean {
        return running && generation != 0 && generation == currentGeneration
    }

    fun shutdown() {
        if (closed) return
        running = false
        nextGeneration()
        val stop = stopEvent
        if (stop != null && SetEvent(stop) == 0) {
            println("SetEvent failed while stopping Win32 alarm waiter")
        }

        val thread = waiterThread
        if (thread != null) {
            val waitResult = WaitForSingleObject(thread, INFINITE)
            if (waitResult != WAIT_OBJECT_0) {
                println("Win32 alarm waiter did not terminate; native handles were retained")
                return
            }
            CloseHandle(thread)
            waiterThread = null
        } else {
            timer?.let { CancelWaitableTimer(it) }
        }

        stableSelf?.dispose()
        stableSelf = null
        timer?.let { CloseHandle(it) }
        commandEvent?.let { CloseHandle(it) }
        stopEvent?.let { CloseHandle(it) }
        stateMutex?.let { CloseHandle(it) }
        closed = true
    }

    internal fun waiterLoop() {
        val alarm = timer ?: return
        val command = commandEvent ?: return
        val stop = stopEvent ?: return
        var armedGeneration = 0
        memScoped {
            val handles = allocArray<HANDLEVar>(3)
            handles[0] = stop
            handles[1] = command
            handles[2] = alarm
            while (running) {
                when (WaitForMultipleObjects(3u, handles, FALSE, INFINITE)) {
                    WAIT_OBJECT_0 -> {
                        CancelWaitableTimer(alarm)
                        return
                    }
                    WAIT_OBJECT_0 + 1u -> {
                        CancelWaitableTimer(alarm)
                        armedGeneration = applyPendingCommand(alarm)
                    }
                    WAIT_OBJECT_0 + 2u -> {
                        val generation = armedGeneration
                        armedGeneration = 0
                        if (isCurrentNotification(generation)) {
                            postAlarmNotification(generation)
                        }
                    }
                    else -> {
                        println("WaitForMultipleObjects failed for Win32 alarm waiter")
                        return
                    }
                }
            }
        }
    }

    private fun applyPendingCommand(alarm: HANDLE): Int {
        var deadlineMillis = 0L
        var generation = 0
        var armed = false
        if (!withStateLock {
            deadlineMillis = pendingDeadlineMillis
            generation = pendingGeneration
            armed = pendingArmed
        }) {
            println("Win32 alarm state lock failed in waiter")
            return 0
        }
        if (!armed || !isCurrentNotification(generation)) return 0

        val delayMillis = deadlineMillis - getEpochMillis()
        if (delayMillis <= 0L) {
            postAlarmNotification(generation)
            return 0
        }
        val armedOk = memScoped {
            val due = alloc<LARGE_INTEGER>()
            due.QuadPart = -delayMillis * FILETIME_TICKS_PER_MILLISECOND
            SetWaitableTimer(alarm, due.ptr, 0, null, null, FALSE) != 0
        }
        if (!armedOk) {
            println("SetWaitableTimer failed for Win32 alarm")
            return 0
        }
        return generation
    }

    private fun postAlarmNotification(generation: Int) {
        val hwnd = targetHwnd
        if (hwnd == null || PostMessageW(
                hwnd,
                WM_APP_ALARM_VALUE.toUInt(),
                generation.toULong(),
                0
            ) == 0
        ) {
            println("PostMessageW failed for Win32 alarm notification")
        }
    }

    private fun nextGeneration(): Int {
        val next = if (currentGeneration == Int.MAX_VALUE) 1 else currentGeneration + 1
        currentGeneration = next
        return next
    }

    private inline fun withStateLock(block: () -> Unit): Boolean {
        val mutex = stateMutex ?: return false
        val waitResult = WaitForSingleObject(mutex, INFINITE)
        if (waitResult != WAIT_OBJECT_0 && waitResult != WAIT_ABANDONED) return false
        try {
            block()
            return true
        } finally {
            ReleaseMutex(mutex)
        }
    }
}

private fun win32AlarmWaiterThunk(userData: COpaquePointer?): UInt {
    if (userData == null) return 0u
    try {
        val self = userData.asStableRef<Win32AlarmScheduler>().get()
        self.waiterLoop()
    } catch (failure: Throwable) {
        println("Win32 alarm waiter terminated: ${failure.message}")
    }
    return 0u
}
