@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import platform.windows.SetThreadExecutionState

/**
 * Dummy power terminal. SetThreadExecutionState is the documented
 * process-local stay-awake API for a desktop session.
 */
class Win32Power {
    fun acquireSession() {
        SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED)
    }

    fun acquireAlarmDisplay() {
        SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED)
    }

    fun releaseAll() {
        SetThreadExecutionState(ES_CONTINUOUS)
    }

    fun shutdown() {
        releaseAll()
    }
}
