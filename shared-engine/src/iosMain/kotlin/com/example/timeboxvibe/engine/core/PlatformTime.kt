@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.core

import platform.posix.time

actual fun getEpochMillis(): Long = time(null) * 1000L
