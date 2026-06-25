package com.example.timeboxvibe.engine.core

interface PlatformAlarmScheduler {
    fun scheduleExactAlarm(epochMillis: Long)
    fun cancelAlarm()
}
