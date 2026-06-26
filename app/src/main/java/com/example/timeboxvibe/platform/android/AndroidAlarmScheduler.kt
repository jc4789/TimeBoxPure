package com.example.timeboxvibe.platform.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.timeboxvibe.FocusService
import com.example.timeboxvibe.engine.core.PlatformAlarmScheduler

class AndroidAlarmScheduler(private val context: Context) : PlatformAlarmScheduler {

    override fun scheduleExactAlarm(epochMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FocusService::class.java).apply {
            action = "ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate elapsed real time for the trigger
        val delayMillis = epochMillis - System.currentTimeMillis()
        if (delayMillis <= 0) return
        val triggerTime = SystemClock.elapsedRealtime() + delayMillis

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "scheduleExactAlarm exact alarm failed; falling back", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (ex: RuntimeException) {
                Log.w(TAG, "scheduleExactAlarm fallback failed", ex)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "scheduleExactAlarm failed", e)
        }
    }

    override fun cancelAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, FocusService::class.java).apply {
            action = "ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getService(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            } catch (e: RuntimeException) {
                Log.w(TAG, "cancelAlarm failed", e)
            }
        }
    }

    private companion object {
        private const val TAG = "AndroidAlarmScheduler"
    }
}
