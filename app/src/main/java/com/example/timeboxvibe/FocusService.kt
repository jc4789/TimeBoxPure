package com.example.timeboxvibe

import com.example.timeboxvibe.R
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.timeboxvibe.engine.SoundPreviewPlayer
import com.example.timeboxvibe.engine.core.TimerEngine
import com.example.timeboxvibe.engine.core.TimerPreset
import com.example.timeboxvibe.engine.core.PlatformAlarmScheduler
import com.example.timeboxvibe.platform.android.AndroidAlarmScheduler
import com.example.timeboxvibe.ui.main.dataStore
import kotlinx.coroutines.*

class FocusService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var tickerJob: Job? = null

    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var vibeIntensity = 0.8
    private var volume = 0.5
    private var isStrictMode = false
    private var tickEnabled = false
    private var tickTone: ToneGenerator? = null
    private var selectedSound = "synth-chime"
    private var selectedFocusSound = "synth-chime"
    private var selectedRelaxSound = "oriental"
    private var taskName = "Focus Time"
    private var engine: TimerEngine? = null
    private var lastTickTimestamp: Long = 0L

    private var appTheme = "reimu"
    private var isBreak = false

    private var lastBitmapIconName: String? = null
    private var lastBitmapTheme: String? = null
    private var lastBitmapIsBreak: Boolean? = null
    private var cachedIconBitmap: Bitmap? = null

    private inline fun guardedStep(label: String, block: () -> Unit) {
        Log.d(TAG, "$label: before")
        try {
            block()
            Log.d(TAG, "$label: after")
        } catch (t: Throwable) {
            Log.e(TAG, "$label: FAILED", t)
            throw t
        }
    }

    private fun logEngineSnapshot(label: String, eng: TimerEngine) {
        Log.d(
            TAG,
            "$label presetId=${eng.preset.id} mode=${eng.mode} " +
                "sequence=${eng.preset.sequence.joinToString(prefix = "[", postfix = "]")} " +
                "sequenceTypes=${eng.preset.sequenceTypes.joinToString(prefix = "[", postfix = "]")} " +
                "sequenceLabels=${eng.preset.sequenceLabels.joinToString(prefix = "[", postfix = "]")} " +
                "currentIndex=${eng.currentIndex} bigDuration=${eng.bigTimeRemaining}/${eng.bigTotalDuration} " +
                "smallDuration=${eng.timeRemaining}/${eng.totalDuration} timeRemaining=${eng.timeRemaining} " +
                "isRinging=${eng.isRinging} isActive=${eng.isActive}"
        )
    }

    private val BLOCKED_PACKAGES = listOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.google.android.youtube"
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
 
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
 
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimeBox::AlarmWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action

        // Extract/update settings first (passed in almost all intents)
        isBreak = intent.getBooleanExtra("isBreak", isBreak)
        val theme = intent.getStringExtra("appTheme")
        if (theme != null) appTheme = theme

        vibeIntensity = intent.getDoubleExtra("vibeIntensity", vibeIntensity)
        volume = intent.getDoubleExtra("volume", volume)
        isStrictMode = intent.getBooleanExtra("strictMode", isStrictMode)
        val newTickEnabled = intent.getBooleanExtra("tickEnabled", tickEnabled)
        if (newTickEnabled != tickEnabled) {
            tickEnabled = newTickEnabled
            if (tickEnabled) {
                if (tickTone == null) {
                    try {
                        tickTone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 30)
                    } catch (e: Exception) {
                    }
                }
            } else {
                tickTone?.release()
                tickTone = null
            }
        }
        val snd = intent.getStringExtra("selectedSound")
        if (snd != null) selectedSound = snd
        val fSnd = intent.getStringExtra("selectedFocusSound")
        if (fSnd != null) selectedFocusSound = fSnd
        val rSnd = intent.getStringExtra("selectedRelaxSound")
        if (rSnd != null) selectedRelaxSound = rSnd

        val task = intent.getStringExtra("taskName")
        if (task != null) taskName = task

        when (action) {
            "STOP_SERVICE" -> {
                clearSavedEngineState()
                stopAlarmAndService()
                return START_NOT_STICKY
            }
            "PAUSE_TIMER" -> {
                engine?.let {
                    guardedStep("PAUSE_TIMER engine.pause") { it.pause() }
                    guardedStep("PAUSE_TIMER stopTicker") { stopTicker() }
                    guardedStep("PAUSE_TIMER saveEngineState") { saveEngineState() }
                    guardedStep("PAUSE_TIMER stopAlarmAudioAndVibe") { stopAlarmAudioAndVibe() }
                    guardedStep("PAUSE_TIMER broadcastState") { broadcastState() }
                    guardedStep("PAUSE_TIMER updateNotification paused") { updateNotification("paused") }
                }
                return START_NOT_STICKY
            }
            "RESUME_TIMER" -> {
                engine?.let {
                    lastTickTimestamp = SystemClock.elapsedRealtime()
                    it.start()
                    startTicker()
                    broadcastState()
                    updateNotification("running")
                }
                return START_NOT_STICKY
            }
            "RESET_TIMER" -> {
                engine?.let {
                    it.reset()
                    stopTicker()
                    clearSavedEngineStateNow()
                    stopAlarmAudioAndVibe()
                    broadcastState()
                }
                return START_NOT_STICKY
            }
            "SKIP_TIMER" -> {
                engine?.let {
                    logEngineSnapshot("SKIP_TIMER before", it)
                    it.skip()
                    logEngineSnapshot("SKIP_TIMER afterEngine", it)
                    broadcastState()
                }
                return START_NOT_STICKY
            }
            "DISMISS_ALARM" -> {
                engine?.let {
                    logEngineSnapshot("DISMISS_ALARM before", it)
                    guardedStep("DISMISS_ALARM engine.dismissAlarm") { it.dismissAlarm() }
                    logEngineSnapshot("DISMISS_ALARM afterEngine", it)
                    lastTickTimestamp = SystemClock.elapsedRealtime()
                    guardedStep("DISMISS_ALARM stopAlarmAudioAndVibe") { stopAlarmAudioAndVibe() }
                    guardedStep("DISMISS_ALARM broadcastState") { broadcastState() }
                    logEngineSnapshot("DISMISS_ALARM afterBroadcast", it)
                    if (it.isActive) {
                        guardedStep("DISMISS_ALARM startTicker") { startTicker() }
                        guardedStep("DISMISS_ALARM updateNotification running") { updateNotification("running") }
                    } else {
                        Log.d(TAG, "DISMISS_ALARM session complete, stopping service")
                        guardedStep("DISMISS_ALARM stopAlarmAndService") { stopAlarmAndService() }
                    }
                    Log.d(TAG, "DISMISS_ALARM exit")
                }
                return START_NOT_STICKY
            }
            "ALARM_TRIGGER" -> {
                onAlarmTriggered()
                return START_NOT_STICKY
            }
            "UPDATE_SETTINGS" -> {
                val stateName = if (engine?.isRinging == true) "ringing" else if (engine?.isActive == true) "running" else "paused"
                updateNotification(stateName)
                return START_NOT_STICKY
            }
        }

        // Starting the timer
        val presetId = intent.getStringExtra("presetId")
        if (presetId != null) {
            stopTicker()
            cancelAlarm()
            engine?.pause()
            engine = null
            FocusService.engine = null
            TimerStateHolder.clear()

            val name = intent.getStringExtra("presetName") ?: ""
            val mode = intent.getStringExtra("presetMode") ?: "classic"
            val sequence = intent.getIntegerArrayListExtra("presetSequence") ?: ArrayList()
            val dualBigDuration = intent.getIntExtra("dualBigDuration", 3600)
            val dualMidDuration = intent.getIntExtra("dualMidDuration", 900)
            val dualSmallDuration = intent.getIntExtra("dualSmallDuration", 90)
            val alarmBehavior = intent.getStringExtra("alarmBehavior") ?: "alarm"
            val description = intent.getStringExtra("presetDescription") ?: ""
            val sequenceTypes = if (mode == "calendar") {
                intent.getStringArrayListExtra("presetSequenceTypes") ?: ArrayList()
            } else {
                ArrayList()
            }
            val sequenceLabels = if (mode == "calendar") {
                intent.getStringArrayListExtra("presetSequenceLabels") ?: ArrayList()
            } else {
                ArrayList()
            }

            val preset = TimerPreset(
                id = presetId,
                name = name,
                mode = mode,
                sequence = sequence.toIntArray(),
                dualBigDuration = dualBigDuration,
                dualMidDuration = dualMidDuration,
                dualSmallDuration = dualSmallDuration,
                alarmBehavior = alarmBehavior,
                description = description,
                sequenceTypes = sequenceTypes.toTypedArray(),
                sequenceLabels = sequenceLabels.toTypedArray()
            ).normalized(logFailures = true)
            engine = TimerEngine(preset).apply {
                alarmScheduler = AndroidAlarmScheduler(this@FocusService)
            }
            FocusService.engine = engine
            engine?.let { logEngineSnapshot("START_TIMER constructed", it) }
        }

        engine?.let {
            lastTickTimestamp = SystemClock.elapsedRealtime()
            it.start()
            logEngineSnapshot("START_TIMER afterStart", it)
            FocusService.engine = it

            // Start tick loop
            startTicker()

            // Schedule background exact alarm for deep sleep
            scheduleAlarm()

            // Broadcast initial state
            broadcastState()

            // Set foreground notification
            val notification = buildNotification("running")
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (true) {
                delay(1000)
                try {
                    tick()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun handleTickEvent(
        event: TimerEngine.TickEvent, 
        eng: TimerEngine, 
        onIntervalComplete: () -> Unit, 
        onSequenceComplete: () -> Unit
    ) {
        when (event) {
            is TimerEngine.TickEvent.IntervalComplete -> {
                cancelAlarm()
                if (eng.isRinging) {
                    triggerPersistentAlarm()
                } else {
                    triggerGentleReminder()
                    onIntervalComplete()
                }
            }
            is TimerEngine.TickEvent.SequenceComplete -> {
                cancelAlarm()
                if (eng.isRinging) {
                    triggerPersistentAlarm()
                } else {
                    clearSavedEngineStateNow()
                    stopAlarmAndService()
                    onSequenceComplete()
                }
            }
            else -> {}
        }
    }

    private fun tick() {
        val eng = engine ?: return

        var intervalCompleteHappened = false
        var isServiceStopped = false

        if (eng.hasSyncInterruption) {
            val event = eng.processSyncInterruption()
            broadcastState()
            handleTickEvent(event, eng, { intervalCompleteHappened = true }, { isServiceStopped = true })
        }

        if (eng.isActive && !eng.isRinging && !isServiceStopped) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = ((now - lastTickTimestamp + 500) / 1000).toInt()
            if (elapsed <= 0) return

            lastTickTimestamp += elapsed * 1000L

            for (i in 0 until elapsed) {
                if (!eng.isActive || eng.isRinging) break

                // Play native tick sound if enabled and app is in background
                if (tickEnabled && tickTone != null && !AppLifecycleTracker.isForeground) {
                    try {
                        tickTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 20) // 20ms beep
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // App Blocker logic
                if (isStrictMode) {
                    try {
                        checkForegroundApp()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val event = eng.tick()
                broadcastState()

                handleTickEvent(event, eng, { intervalCompleteHappened = true }, { isServiceStopped = true })
            }

            // Reschedule alarm and update notification at the end of the batch
            if (eng.isActive && !eng.isRinging && !isServiceStopped) {
                if (intervalCompleteHappened) {
                    scheduleAlarm()
                }
                updateNotification("running")
            }
        }
    }

    private fun checkForegroundApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

            val time = System.currentTimeMillis()
            val events = usm.queryEvents(time - 10000, time)
            val event = UsageEvents.Event()
            var currentApp: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentApp = event.packageName
                }
            }

            if (currentApp != null && BLOCKED_PACKAGES.contains(currentApp)) {
                // Trigger Block Overlay
                val blockIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(blockIntent)
            }
        }
    }

    private fun triggerPersistentAlarm() {
        cancelAlarm()

        updateNotification("ringing")

        // Start looping alarm sound via SoundPreviewPlayer
        try {
            val eng = engine
            val alarmSoundKey = if (eng != null) {
                val finishedBlockIsRelax = if (eng.mode == "calendar") {
                    eng.currentIndex < eng.preset.sequenceTypes.size && eng.preset.sequenceTypes[eng.currentIndex] == "relax"
                } else {
                    isBreak
                }
                if (finishedBlockIsRelax) selectedRelaxSound else selectedFocusSound
            } else {
                selectedFocusSound
            }
            SoundPreviewPlayer.playAlarm(this, alarmSoundKey, volume.toFloat())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start Vibration Loop
        try {
            val vib = vibrator
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = (255 * vibeIntensity).toInt().coerceIn(1, 255)
                    val timings = longArrayOf(0, 1000, 1000)
                    val amplitudes = intArrayOf(0, amplitude, 0)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, 0) // 0 means loop at index 0
                    vib.vibrate(effect)
                } else {
                    val pattern = longArrayOf(0, 1000, 1000)
                    @Suppress("DEPRECATION")
                    vib.vibrate(pattern, 0) // 0 means repeat
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "triggerPersistentAlarm vibration failed", e)
        }

        // Wake up the screen so the user can see the dismiss button
        try {
            if (wakeLock != null && wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "triggerPersistentAlarm wakeLock acquire failed", e)
        }

        Log.d(TAG, "triggerPersistentAlarm: notification/fullScreenIntent owns UI surfacing")

        // Update notification
        updateNotification("ringing")
    }

    private fun triggerGentleReminder() {
        cancelAlarm()

        // Play brief 1-second gentle reminder sound
        try {
            val eng = engine
            val alarmSoundKey = if (eng != null) {
                val finishedBlockIsRelax = if (eng.mode == "calendar") {
                    eng.currentIndex < eng.preset.sequenceTypes.size && eng.preset.sequenceTypes[eng.currentIndex] == "relax"
                } else {
                    isBreak
                }
                if (finishedBlockIsRelax) selectedRelaxSound else selectedFocusSound
            } else {
                selectedFocusSound
            }
            SoundPreviewPlayer.playGentleReminder(this, alarmSoundKey, volume.toFloat())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Vibrate for exactly 1 second
        try {
            val vib = vibrator
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    var amplitude = (255 * vibeIntensity).toInt()
                    if (amplitude < 1) amplitude = 1
                    val effect = VibrationEffect.createOneShot(1000, amplitude)
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(1000)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "triggerGentleReminder vibration failed", e)
        }
    }

    private fun stopAlarmAudioAndVibe() {
        try {
            vibrator?.cancel()
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopAlarmAudioAndVibe vibrator cancel failed", e)
        }
        try {
            SoundPreviewPlayer.stop()
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopAlarmAudioAndVibe audio stop failed", e)
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopAlarmAudioAndVibe wakeLock release failed", e)
        }
    }

    private fun stopAlarmAndService() {
        stopTicker()
        cancelAlarm()
        engine?.pause()
        engine = null
        FocusService.engine = null
        TimerStateHolder.clear()
        stopAlarmAudioAndVibe()
        try {
            tickTone?.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopAlarmAndService tickTone release failed", e)
        }
        tickTone = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "stopAlarmAndService stopForeground failed", e)
        }
        stopSelf()
    }

    private fun broadcastState() {
        val eng = engine ?: return
        if (eng.mode == "calendar") {
            isBreak = eng.isBreak
        }
        logEngineSnapshot("BROADCAST_STATE", eng)
        TimerStateHolder.update(
            presetId = eng.preset.id,
            presetName = eng.preset.name,
            mode = eng.mode,
            timeRemaining = eng.timeRemaining.coerceAtLeast(0),
            totalDuration = eng.totalDuration,
            midTimeRemaining = eng.midTimeRemaining.coerceAtLeast(0),
            midTotalDuration = eng.midTotalDuration,
            bigTimeRemaining = eng.bigTimeRemaining.coerceAtLeast(0),
            bigTotalDuration = eng.bigTotalDuration,
            currentIndex = eng.currentIndex,
            sequenceLength = eng.preset.stageCount(),
            currentStageLabel = eng.currentStageLabel,
            currentStageType = eng.currentStageType,
            isActive = eng.isActive,
            isRinging = eng.isRinging,
            isBreak = if (eng.mode == "calendar") eng.isBreak else isBreak
        )
    }

    private fun formatTime(secs: Int): String {
        if (secs < 0) return "00:00"
        val m = secs / 60
        val s = secs % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun updateNotification(stateType: String) {
        try {
            val notification = buildNotification(stateType)
            createNotificationChannel()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            Log.d(TAG, "updateNotification $stateType: before NotificationManager.notify")
            manager?.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "updateNotification $stateType: after NotificationManager.notify")
        } catch (e: RuntimeException) {
            Log.w(TAG, "updateNotification failed stateType=$stateType", e)
        }
    }

    private fun getThemeColors(themeName: String, isBreakMode: Boolean): Triple<Int, Int, Int> {
        return when (themeName) {
            "reimu" -> {
                val prim = android.graphics.Color.parseColor("#FF5555")
                val sec = android.graphics.Color.parseColor("#FFAAA6")
                val bg = android.graphics.Color.parseColor(if (isBreakMode) "#331111" else "#220011")
                Triple(prim, sec, bg)
            }
            "marisa" -> {
                val prim = android.graphics.Color.parseColor("#FFEE55")
                val sec = android.graphics.Color.parseColor("#AA88FF")
                val bg = android.graphics.Color.parseColor(if (isBreakMode) "#221133" else "#221100")
                Triple(prim, sec, bg)
            }
            "alice" -> {
                val prim = android.graphics.Color.parseColor("#55AAFF")
                val sec = android.graphics.Color.parseColor("#FF99BB")
                val bg = android.graphics.Color.parseColor(if (isBreakMode) "#110022" else "#001122")
                Triple(prim, sec, bg)
            }
            "kaguya" -> {
                val prim = android.graphics.Color.parseColor("#DDAAFF")
                val sec = android.graphics.Color.parseColor("#88CC66")
                val bg = android.graphics.Color.parseColor(if (isBreakMode) "#223322" else "#110022")
                Triple(prim, sec, bg)
            }
            else -> { // Default Reimu Focus
                val prim = android.graphics.Color.parseColor("#FF5555")
                val sec = android.graphics.Color.parseColor("#FFAAA6")
                val bg = android.graphics.Color.parseColor("#220011")
                Triple(prim, sec, bg)
            }
        }
    }

    private fun generatePixelArtIcon(iconName: String, themeName: String, isBreakMode: Boolean): Bitmap {
        val (primaryColor, secondaryColor, backgroundColor) = getThemeColors(themeName, isBreakMode)
        val gridWidth = 32
        val gridHeight = 32
        val bitmap = Bitmap.createBitmap(gridWidth, gridHeight, Bitmap.Config.ARGB_8888)

        for (x in 0 until gridWidth) {
            for (y in 0 until gridHeight) {
                // Check if it's the double-concentric border
                // Outer 1-px border: coordinates 0 and 31
                if (x == 0 || x == 31 || y == 0 || y == 31) {
                    bitmap.setPixel(x, y, primaryColor)
                    continue
                }
                // Dark spacer: coordinates 1 and 30
                if (x == 1 || x == 30 || y == 1 || y == 30) {
                    bitmap.setPixel(x, y, android.graphics.Color.BLACK)
                    continue
                }
                // Inner 1-px border: coordinates 2 and 29
                if (x == 2 || x == 29 || y == 2 || y == 29) {
                    bitmap.setPixel(x, y, secondaryColor)
                    continue
                }

                // Floating 2x2 accent orbs (sigil dots) inside corner junctions
                val isSigil = (x in 4..5 || x in 26..27) && (y in 4..5 || y in 26..27)
                if (isSigil) {
                    bitmap.setPixel(x, y, primaryColor)
                    continue
                }

                // Inside the border: draw the icon (scale down radius if needed so it fits)
                val dx = x - 15.5f
                val dy = y - 15.5f
                val rSq = dx * dx + dy * dy

                val color = when (iconName) {
                    "yinyang" -> {
                        // Outer boundary fits in radius 12
                        if (rSq > 144f) backgroundColor
                        else if (rSq > 121f) android.graphics.Color.BLACK
                        else {
                            val dxSub = dx
                            val dyTop = y - 8.5f
                            val dyBottom = y - 22.5f
                            val rTopSq = dxSub * dxSub + dyTop * dyTop
                            val rBottomSq = dxSub * dxSub + dyBottom * dyBottom
                            when {
                                rTopSq <= 16f -> if (rTopSq <= 2.25f) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                                rBottomSq <= 16f -> if (rBottomSq <= 2.25f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                                dx < 0f -> android.graphics.Color.WHITE
                                else -> android.graphics.Color.BLACK
                            }
                        }
                    }
                    "play_danmaku" -> {
                        val cx = 12f
                        val cy = 15.5f
                        val dxPlay = x - cx
                        val dyPlay = y - cy
                        fun inFlameShape(r: Float, tail: Float): Boolean {
                            if (dxPlay <= 0) {
                                return dxPlay * dxPlay + dyPlay * dyPlay <= r * r
                            } else {
                                if (dxPlay > tail) return false
                                val thickness = r * (1f - dxPlay / tail)
                                return kotlin.math.abs(dyPlay) <= thickness
                            }
                        }
                        if (inFlameShape(11f, 15f)) {
                            when {
                                inFlameShape(5f, 6f) -> android.graphics.Color.WHITE
                                inFlameShape(8f, 10f) -> android.graphics.Color.parseColor("#FFFFCC00") // yellow
                                inFlameShape(10f, 13f) -> android.graphics.Color.parseColor("#FFFF2200") // fire-red
                                else -> android.graphics.Color.parseColor("#FF880000") // dark red outline
                            }
                        } else backgroundColor
                    }
                    "pause_ofuda" -> {
                        val isTal1 = x in 7..13 && y in 4..27
                        val isTal2 = x in 18..24 && y in 4..27
                        if (!isTal1 && !isTal2) backgroundColor
                        else {
                            val tx = if (isTal1) x - 7 else x - 18
                            val ty = y - 4
                            if (tx == 0 || tx == 6 || ty == 0 || ty == 23) {
                                android.graphics.Color.parseColor("#FFCC0000")
                            } else {
                                val isRune = when (ty) {
                                    3, 4 -> tx == 3
                                    6 -> tx in 2..4
                                    8, 9 -> tx == 3
                                    11 -> tx in 2..4
                                    13, 14 -> tx == 3
                                    16 -> tx in 2..4
                                    18, 19 -> tx == 3
                                    21 -> tx in 1..5
                                    else -> false
                                }
                                if (isRune) android.graphics.Color.parseColor("#FFCC0000")
                                else android.graphics.Color.WHITE
                            }
                        }
                    }
                    "watch" -> {
                        val isHanger = (y in 3..4 && kotlin.math.abs(x - 15.5f) <= 2f && kotlin.math.abs(x - 15.5f) >= 1f) || (y == 3 && kotlin.math.abs(x - 15.5f) <= 1f)
                        if (isHanger) android.graphics.Color.parseColor("#FFCCCCCC")
                        else if (rSq > 121f) backgroundColor
                        else if (rSq > 100f) android.graphics.Color.parseColor("#FFCCCCCC")
                        else if (rSq > 81f) android.graphics.Color.BLACK
                        else {
                            val isTick = (x == 15 && y in 6..7) || (x == 15 && y in 24..25) || (y == 15 && x in 6..7) || (y == 15 && x in 24..25)
                            if (isTick) android.graphics.Color.BLACK
                            else {
                                val isHourHand = dx > 0f && dy < 0f && kotlin.math.abs(dx - (-dy)) <= 0.8f && rSq <= 16f
                                val isMinHand = kotlin.math.abs(dx) <= 0.6f && dy < 0f && rSq <= 49f
                                if (isHourHand || isMinHand) android.graphics.Color.BLACK
                                else android.graphics.Color.WHITE
                            }
                        }
                    }
                    "hakkero" -> {
                        val octVal = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) + 0.5f * (kotlin.math.abs(dx) + kotlin.math.abs(dy))
                        if (octVal > 13f) backgroundColor
                        else if (octVal > 11f) android.graphics.Color.parseColor("#FFD4AF37")
                        else if (octVal > 9f) android.graphics.Color.BLACK
                        else if (rSq <= 25f) {
                            if (rSq <= 6.25f) android.graphics.Color.parseColor("#FFFFCC00") else primaryColor
                        } else {
                            val isTrigram = (y == 8 && x in 13..18) || (y == 23 && x in 13..18) || (x == 8 && y in 13..18) || (x == 23 && y in 13..18)
                            if (isTrigram) android.graphics.Color.WHITE
                            else android.graphics.Color.parseColor("#FF333333")
                        }
                    }
                    else -> backgroundColor
                }

                bitmap.setPixel(x, y, color)
            }
        }

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, false)
        bitmap.recycle()
        return scaledBitmap
    }

    private fun getOrCreateIconBitmap(iconName: String, themeName: String, isBreakMode: Boolean): Bitmap {
        if (iconName == lastBitmapIconName && themeName == lastBitmapTheme && isBreakMode == lastBitmapIsBreak) {
            cachedIconBitmap?.let { return it }
        }
        try {
            cachedIconBitmap?.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "getOrCreateIconBitmap recycle failed", e)
            cachedIconBitmap = null
        }

        try {
            val newBitmap = generatePixelArtIcon(iconName, themeName, isBreakMode)
            cachedIconBitmap = newBitmap
            lastBitmapIconName = iconName
            lastBitmapTheme = themeName
            lastBitmapIsBreak = isBreakMode
            return newBitmap
        } catch (e: Exception) {
            Log.w(TAG, "generatePixelArtIcon failed icon=$iconName", e)
            // Return a minimal 1x1 bitmap as fallback to prevent notification crash
            val fallback = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            fallback.setPixel(0, 0, android.graphics.Color.BLACK)
            cachedIconBitmap = fallback
            lastBitmapIconName = iconName
            lastBitmapTheme = themeName
            lastBitmapIsBreak = isBreakMode
            return fallback
        }
    }

    private fun buildNotification(stateType: String): Notification {
        val eng = engine
        val timeStr = if (eng != null) formatTime(eng.timeRemaining.coerceAtLeast(0)) else "00:00"

        val title = when (stateType) {
            "ringing" -> "Time's Up!"
            "paused" -> "Timer Paused"
            else -> if (isBreak) "Break Session" else "Focus Session"
        }

        val content = when (stateType) {
            "ringing" -> "Task: $taskName · ${eng?.currentStageLabel ?: ""}"
            "paused" -> "${eng?.currentStageLabel ?: taskName} (Paused at $timeStr)"
            else -> "${eng?.currentStageLabel ?: taskName} ($timeStr)"
        }

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconName = when (stateType) {
            "ringing" -> "hakkero"
            "paused" -> "pause_ofuda"
            else -> if (isBreak) "yinyang" else "watch"
        }
        val iconBitmap = getOrCreateIconBitmap(iconName, appTheme, isBreak)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(iconBitmap)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        builder.setUsesChronometer(false)
        builder.setShowWhen(false)

        when (stateType) {
            "ringing" -> {
                val dismissPendingIntent = PendingIntent.getService(
                    this,
                    4,
                    Intent(this, FocusService::class.java).apply { action = "DISMISS_ALARM" },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_launcher_foreground),
                        "Dismiss Alarm",
                        dismissPendingIntent
                    ).build()
                )
                builder.setPriority(Notification.PRIORITY_MAX)
                builder.setFullScreenIntent(pendingIntent, true)
                builder.setCategory(Notification.CATEGORY_ALARM)
            }
            "paused" -> {
                val resumePendingIntent = PendingIntent.getService(
                    this,
                    3,
                    Intent(this, FocusService::class.java).apply { action = "RESUME_TIMER" },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val stopPendingIntent = PendingIntent.getService(
                    this,
                    1,
                    Intent(this, FocusService::class.java).apply { action = "STOP_SERVICE" },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_launcher_foreground),
                        "Resume",
                        resumePendingIntent
                    ).build()
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_launcher_foreground),
                        "Stop",
                        stopPendingIntent
                    ).build()
                )
                builder.setPriority(Notification.PRIORITY_HIGH)
            }
            else -> { // running
                val pausePendingIntent = PendingIntent.getService(
                    this,
                    2,
                    Intent(this, FocusService::class.java).apply { action = "PAUSE_TIMER" },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val stopPendingIntent = PendingIntent.getService(
                    this,
                    1,
                    Intent(this, FocusService::class.java).apply { action = "STOP_SERVICE" },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_launcher_foreground),
                        "Pause",
                        pausePendingIntent
                    ).build()
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_launcher_foreground),
                        "Stop",
                        stopPendingIntent
                    ).build()
                )
                builder.setPriority(Notification.PRIORITY_HIGH)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Focus Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    // --- ALARM MANAGER INTEGRATION (Android 16 / Deep Sleep Compatible) ---

    private fun scheduleAlarm() {
        val eng = engine ?: return
        if (!eng.isActive || eng.isRinging) return
        // Guard: if the small timer has just hit 0 and the engine hasn't reset it
        // yet (e.g., during a dual.5 auto-loop catch-up), don't schedule a 0-second
        // alarm. That would fire immediately via onAlarmTriggered() → tick() and
        // re-enter scheduleAlarm(), creating an infinite re-trigger loop. The user
        // would see the gentle reminder sound repeat and the first 1 second of
        // the countdown keep replaying.
        if (eng.timeRemaining <= 0) return

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, FocusService::class.java).apply {
            action = "ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = SystemClock.elapsedRealtime() + eng.timeRemaining * 1000L

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
            Log.w(TAG, "scheduleAlarm exact alarm failed; falling back", e)
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
                Log.w(TAG, "scheduleAlarm fallback failed", ex)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "scheduleAlarm failed", e)
        }
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, FocusService::class.java).apply {
            action = "ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getService(
            this,
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

    private fun onAlarmTriggered() {
        val eng = engine ?: return
        if (!eng.isActive || eng.isRinging) return
        eng.hasSyncInterruption = true
        tick()
    }

    // --- PERSISTENT STATE SAVING ---

    private fun saveEngineState() {
        val eng = engine ?: return
        val timeRemaining = eng.timeRemaining
        val totalDuration = eng.totalDuration
        val midTimeRemaining = eng.midTimeRemaining
        val midTotalDuration = eng.midTotalDuration
        val bigTimeRemaining = eng.bigTimeRemaining
        val bigTotalDuration = eng.bigTotalDuration
        val currentIndex = eng.currentIndex
        val isActive = eng.isActive
        val isRinging = eng.isRinging
        serviceScope.launch(Dispatchers.IO) {
            persistEngineState(
                timeRemaining,
                totalDuration,
                midTimeRemaining,
                midTotalDuration,
                bigTimeRemaining,
                bigTotalDuration,
                currentIndex,
                isActive,
                isRinging
            )
        }
    }

    private fun saveEngineStateNow() {
        val eng = engine ?: return
        val timeRemaining = eng.timeRemaining
        val totalDuration = eng.totalDuration
        val midTimeRemaining = eng.midTimeRemaining
        val midTotalDuration = eng.midTotalDuration
        val bigTimeRemaining = eng.bigTimeRemaining
        val bigTotalDuration = eng.bigTotalDuration
        val currentIndex = eng.currentIndex
        val isActive = eng.isActive
        val isRinging = eng.isRinging
        runBlocking(Dispatchers.IO) {
            persistEngineState(
                timeRemaining,
                totalDuration,
                midTimeRemaining,
                midTotalDuration,
                bigTimeRemaining,
                bigTotalDuration,
                currentIndex,
                isActive,
                isRinging
            )
        }
    }

    private suspend fun persistEngineState(
        timeRemaining: Int,
        totalDuration: Int,
        midTimeRemaining: Int,
        midTotalDuration: Int,
        bigTimeRemaining: Int,
        bigTotalDuration: Int,
        currentIndex: Int,
        isActive: Boolean,
        isRinging: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("has_saved_state")] = true
            prefs[intPreferencesKey("saved_time_remaining")] = timeRemaining.coerceAtLeast(0)
            prefs[intPreferencesKey("saved_total_duration")] = totalDuration
            prefs[intPreferencesKey("saved_mid_time_remaining")] = midTimeRemaining.coerceAtLeast(0)
            prefs[intPreferencesKey("saved_mid_total_duration")] = midTotalDuration
            prefs[intPreferencesKey("saved_big_time_remaining")] = bigTimeRemaining.coerceAtLeast(0)
            prefs[intPreferencesKey("saved_big_total_duration")] = bigTotalDuration
            prefs[intPreferencesKey("saved_current_index")] = currentIndex
            prefs[booleanPreferencesKey("saved_is_active")] = isActive
            prefs[booleanPreferencesKey("saved_is_ringing")] = isRinging
        }
    }

    private fun clearSavedEngineState() {
        serviceScope.launch(Dispatchers.IO) {
            persistClearedEngineState()
        }
    }

    private fun clearSavedEngineStateNow() {
        runBlocking(Dispatchers.IO) {
            persistClearedEngineState()
        }
    }

    private suspend fun persistClearedEngineState() {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("has_saved_state")] = false
        }
    }

    override fun onDestroy() {
        val eng = engine
        if (eng != null && (eng.isActive || eng.isRinging || eng.timeRemaining != eng.totalDuration)) {
            // Save state so we can resume if killed/recreated
            saveEngineStateNow()
        }
        stopTicker()
        stopAlarmAudioAndVibe()
        tickTone?.release()
        tickTone = null
        FocusService.engine = null
        cachedIconBitmap?.recycle()
        cachedIconBitmap = null
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val eng = engine
        if (eng != null && (eng.isActive || eng.isRinging)) {
            // Active session — save state to be safe, but keep service running in foreground
            saveEngineState()
        } else {
            // Idle or paused — clean up and stop service/dismiss notification
            clearSavedEngineStateNow()
            stopAlarmAndService()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "FocusService"
        @JvmStatic
        var engine: TimerEngine? = null
        private const val CHANNEL_ID = "FocusServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
}
