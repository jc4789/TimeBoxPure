@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.core

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.windows.FILETIME
import platform.windows.GetSystemTimeAsFileTime

private const val FILETIME_TICKS_PER_MILLISECOND = 10_000L
private const val MILLIS_BETWEEN_1601_AND_1970 = 11_644_473_600_000L

actual fun getEpochMillis(): Long {
    memScoped {
        val fileTime = alloc<FILETIME>()
        GetSystemTimeAsFileTime(fileTime.ptr)
        val ticks = (fileTime.dwHighDateTime.toUInt().toLong() shl 32) or
            fileTime.dwLowDateTime.toUInt().toLong()
        return ticks / FILETIME_TICKS_PER_MILLISECOND - MILLIS_BETWEEN_1601_AND_1970
    }
}
