@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.core.EngineUiState
import com.example.timeboxvibe.engine.core.PlatformInputTrigger
import com.example.timeboxvibe.engine.core.SessionMacroDisplay
import com.example.timeboxvibe.engine.core.TimerActions
import com.example.timeboxvibe.engine.core.TimerEngine
import com.example.timeboxvibe.engine.core.TimerPreset
import com.example.timeboxvibe.engine.getDefaultPresets
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import platform.windows.FLASHWINFO
import platform.windows.FlashWindowEx
import platform.windows.HWND
import platform.windows.LARGE_INTEGER
import platform.windows.MessageBeep
import platform.windows.QueryPerformanceCounter
import platform.windows.QueryPerformanceFrequency
import kotlin.concurrent.Volatile

/**
 * In-process TimerActions for the Win32 terminal.
 * Scene logic stays in commonMain; this only owns OS-side scheduling and audio.
 */
class Win32TimerActions(
    private val alarmScheduler: Win32AlarmScheduler,
    private val audio: Win32Audio,
    private val power: Win32Power
) : TimerActions, PlatformInputTrigger {

    private var language = "en"
    private var appTheme = "reimu"
    private var currentTask = ""
    private var strictMode = false
    private var tickEnabled = false
    private var vibeIntensity = 0.8f
    private var volume = 0.5f
    private var selectedFocusSound = SongCatalog.DEFAULT_FOCUS_ID
    private var selectedRelaxSound = SongCatalog.DEFAULT_RELAX_ID
    private var customPresets = emptyList<TimerPreset>()
    private var activePresetId = "dual_box"
    private var engine: TimerEngine? = null
    @Volatile
    var hwnd: HWND? = null

    @Volatile
    private var lastTickQpc: Long = 0L
    private var qpcFrequency: Long = 1L
    private val settingsStore = Win32SettingsStore()

    init {
        qpcFrequency = queryPerformanceFrequency()
        applyPersisted(settingsStore.load())
        rebuildIdleEngine()
    }

    fun attachWindow(target: HWND?) {
        hwnd = target
        alarmScheduler.targetHwnd = target
    }

    fun pump(nowQpc: Long) {
        val eng = engine ?: return
        if (eng.hasSyncInterruption) {
            val event = eng.processSyncInterruption()
            handleTickEvent(event, eng)
            lastTickQpc = nowQpc
            return
        }
        if (!eng.isActive || eng.isRinging) return
        if (lastTickQpc == 0L) {
            lastTickQpc = nowQpc
            return
        }
        val elapsedTicks = nowQpc - lastTickQpc
        val elapsedSeconds = (elapsedTicks / qpcFrequency).toInt()
        if (elapsedSeconds <= 0) return
        lastTickQpc += elapsedSeconds.toLong() * qpcFrequency
        var i = 0
        while (i < elapsedSeconds) {
            if (!eng.isActive || eng.isRinging) break
            val event = eng.tick()
            handleTickEvent(event, eng)
            i++
        }
    }

    fun onOsAlarm() {
        val eng = engine ?: return
        if (!eng.isActive || eng.isRinging) return
        eng.hasSyncInterruption = true
        pump(queryPerformanceCounter())
    }

    override fun startTimer() {
        audio.stop()
        val preset = presetById(activePresetId) ?: return
        val existing = engine
        if (existing != null && existing.preset.id == preset.id && !existing.isRinging) {
            lastTickQpc = queryPerformanceCounter()
            existing.start()
            power.acquireSession()
            return
        }
        val next = TimerEngine(preset)
        next.alarmScheduler = alarmScheduler
        engine = next
        lastTickQpc = queryPerformanceCounter()
        next.start()
        power.acquireSession()
    }

    override fun stopTimer() {
        audio.stop()
        engine?.pause()
        power.releaseAll()
    }

    override fun resetTimer() {
        audio.stop()
        engine?.reset()
        lastTickQpc = 0L
        power.releaseAll()
    }

    override fun skipTimer() {
        engine?.skip()
    }

    override fun dismissAlarm() {
        audio.stop()
        val eng = engine ?: return
        eng.dismissAlarm()
        if (eng.isActive) {
            lastTickQpc = queryPerformanceCounter()
            power.acquireSession()
        } else {
            power.releaseAll()
        }
    }

    override fun updateTask(task: String) {
        currentTask = task
        persistSettings()
    }

    override fun updateSettings(strictMode: Boolean, tickEnabled: Boolean, sound: String, vibeIntensity: Float) {
        this.strictMode = strictMode
        this.tickEnabled = tickEnabled
        this.vibeIntensity = vibeIntensity
        selectedFocusSound = catalogOrDefault(sound, SongCatalog.DEFAULT_FOCUS_ID)
        persistSettings()
    }

    override fun updateFocusSound(sound: String) {
        selectedFocusSound = catalogOrDefault(sound, SongCatalog.DEFAULT_FOCUS_ID)
        persistSettings()
    }

    override fun updateRelaxSound(sound: String) {
        selectedRelaxSound = catalogOrDefault(sound, SongCatalog.DEFAULT_RELAX_ID)
        persistSettings()
    }

    override fun updateLanguage(code: String) {
        language = code
        rebuildIdleEngine(keepRunning = true)
        persistSettings()
    }

    override fun updateTheme(themeName: String) {
        appTheme = themeName
        persistSettings()
    }

    override fun selectPreset(id: String) {
        audio.stop()
        engine?.pause()
        power.releaseAll()
        activePresetId = id
        rebuildIdleEngine()
        persistSettings()
    }

    override fun addCustomPreset(preset: TimerPreset) {
        upsertCustomPreset(preset)
    }

    override fun upsertCustomPreset(preset: TimerPreset) {
        val normalized = preset.normalized(logFailures = true)
        val next = ArrayList<TimerPreset>(customPresets.size + 1)
        var replaced = false
        var i = 0
        while (i < customPresets.size) {
            if (customPresets[i].id == normalized.id) {
                next.add(normalized)
                replaced = true
            } else {
                next.add(customPresets[i])
            }
            i++
        }
        if (!replaced) next.add(normalized)
        customPresets = next
        activePresetId = normalized.id
        if (engine?.isActive != true) {
            rebuildIdleEngine()
        }
        persistSettings()
    }

    override fun deletePreset(id: String) {
        customPresets = customPresets.filter { it.id != id }
        if (activePresetId == id) {
            activePresetId = "dual_box"
            if (engine?.isActive != true) {
                rebuildIdleEngine()
            }
        }
        persistSettings()
    }

    override fun previewSound(key: String) {
        audio.playPreview(key, volume)
    }

    override fun requestExactAlarmPermission() {
        // Waitable timers do not require a capability prompt on desktop Windows.
    }

    override fun updateVolume(vol: Float) {
        volume = vol
        persistSettings()
    }

    override fun getUiState(): EngineUiState {
        val presets = currentPresets()
        val eng = engine
        if (eng == null) {
            return EngineUiState(
                language = language,
                appTheme = appTheme,
                currentTask = currentTask,
                strictMode = strictMode,
                tickEnabled = tickEnabled,
                vibeIntensity = vibeIntensity,
                volume = volume,
                selectedFocusSound = selectedFocusSound,
                selectedRelaxSound = selectedRelaxSound,
                activePresetId = activePresetId,
                isExactAlarmPermitted = true,
                presets = presets
            )
        }
        val time = eng.timeRemaining.coerceAtLeast(0)
        val (macroRem, macroTot) = SessionMacroDisplay.resolveMacro(
            mode = eng.mode,
            sequence = eng.preset.sequence,
            currentIndex = eng.currentIndex,
            timeRemaining = time,
            engineBigRemaining = eng.bigTimeRemaining,
            engineBigTotal = eng.bigTotalDuration
        )
        return EngineUiState(
            timeRemaining = time,
            totalDuration = eng.totalDuration,
            midTimeRemaining = eng.midTimeRemaining.coerceAtLeast(0),
            midTotalDuration = eng.midTotalDuration,
            bigTimeRemaining = macroRem,
            bigTotalDuration = macroTot,
            currentIndex = eng.currentIndex,
            sequenceLength = eng.preset.stageCount(),
            currentStageLabel = eng.currentStageLabel,
            currentStageType = eng.currentStageType,
            isRunning = eng.isActive,
            isRinging = eng.isRinging,
            isBreak = if (eng.mode == "calendar") eng.isBreak else false,
            activePresetId = eng.preset.id,
            activeMode = eng.mode,
            isDual = eng.isDual,
            currentTask = currentTask,
            strictMode = strictMode,
            tickEnabled = tickEnabled,
            vibeIntensity = vibeIntensity,
            volume = volume,
            selectedFocusSound = selectedFocusSound,
            selectedRelaxSound = selectedRelaxSound,
            appTheme = appTheme,
            language = language,
            isExactAlarmPermitted = true,
            presets = presets
        )
    }

    override fun triggerKeyboard() {
        // Hardware keyboard is already routed through WM_CHAR / WM_KEYDOWN.
    }

    override fun performHapticFeedback(type: Int) {
        if (type == com.example.timeboxvibe.engine.core.EngineHaptics.IMPACT) {
            MessageBeep(0u)
        }
    }

    fun shutdown() {
        audio.stop()
        engine?.pause()
        power.releaseAll()
    }

    private fun handleTickEvent(event: TimerEngine.TickEvent, eng: TimerEngine) {
        when (event) {
            is TimerEngine.TickEvent.IntervalComplete,
            is TimerEngine.TickEvent.SequenceComplete -> {
                alarmScheduler.cancelAlarm()
                if (eng.isRinging) {
                    startPersistentAlarm(eng)
                } else if (event !is TimerEngine.TickEvent.None) {
                    val key = alarmSoundKey(eng)
                    audio.playGentleReminder(key, volume)
                    if (!eng.isActive) {
                        power.releaseAll()
                    }
                }
            }
            else -> {}
        }
    }

    private fun startPersistentAlarm(eng: TimerEngine) {
        audio.playAlarm(alarmSoundKey(eng), volume)
        power.acquireAlarmDisplay()
        flashWindow()
    }

    private fun alarmSoundKey(eng: TimerEngine): String {
        return if (eng.mode == "calendar" && eng.isBreak) selectedRelaxSound else selectedFocusSound
    }

    private fun flashWindow() {
        val target = hwnd ?: return
        memScopedFlash(target)
    }

    private fun rebuildIdleEngine(keepRunning: Boolean = false) {
        val running = engine?.isActive == true && keepRunning
        if (running) return
        val preset = presetById(activePresetId) ?: currentPresets().firstOrNull() ?: return
        val next = TimerEngine(preset)
        next.alarmScheduler = alarmScheduler
        engine = next
        lastTickQpc = 0L
    }

    private fun currentPresets(): List<TimerPreset> {
        val defaults = getDefaultPresets(language).map { it.normalized(logFailures = true) }
        val emergency = TimerPreset(
            id = "emergency",
            name = if (language == "zh") "紧急专注" else if (language == "ja") "緊急セッション" else "Emergency Session",
            mode = "classic",
            sequence = intArrayOf(25 * 60),
            alarmBehavior = "alarm",
            description = "SYS.OVERRIDE // EMERGENCY"
        ).normalized(logFailures = true)
        return defaults + customPresets + emergency
    }

    private fun presetById(id: String): TimerPreset? {
        val presets = currentPresets()
        var i = 0
        while (i < presets.size) {
            if (presets[i].id == id) return presets[i]
            i++
        }
        return presets.firstOrNull()
    }

    private fun catalogOrDefault(id: String, defaultId: String): String {
        return if (SongCatalog.byId(id) != null) id else defaultId
    }

    private fun applyPersisted(saved: PersistedWin32Settings) {
        language = saved.language
        appTheme = saved.appTheme
        currentTask = saved.currentTask
        strictMode = saved.strictMode
        tickEnabled = saved.tickEnabled
        vibeIntensity = saved.vibeIntensity
        volume = saved.volume
        selectedFocusSound = catalogOrDefault(saved.selectedFocusSound, SongCatalog.DEFAULT_FOCUS_ID)
        selectedRelaxSound = catalogOrDefault(saved.selectedRelaxSound, SongCatalog.DEFAULT_RELAX_ID)
        customPresets = saved.customPresets
        activePresetId = saved.activePresetId
    }

    private fun persistSettings() {
        settingsStore.save(
            PersistedWin32Settings(
                language = language,
                appTheme = appTheme,
                currentTask = currentTask,
                strictMode = strictMode,
                tickEnabled = tickEnabled,
                vibeIntensity = vibeIntensity,
                volume = volume,
                selectedFocusSound = selectedFocusSound,
                selectedRelaxSound = selectedRelaxSound,
                activePresetId = activePresetId,
                customPresets = customPresets
            )
        )
    }
}

private fun memScopedFlash(hwnd: HWND) {
    memScoped {
        val info = alloc<FLASHWINFO>()
        info.cbSize = sizeOf<FLASHWINFO>().toUInt()
        info.hwnd = hwnd
        info.dwFlags = FLASHW_ALL or FLASHW_TIMERNOFG
        info.uCount = 5u
        info.dwTimeout = 0u
        FlashWindowEx(info.ptr)
    }
}

internal fun queryPerformanceFrequency(): Long {
    memScoped {
        val freq = alloc<LARGE_INTEGER>()
        QueryPerformanceFrequency(freq.ptr)
        return freq.QuadPart
    }
}

internal fun queryPerformanceCounter(): Long {
    memScoped {
        val now = alloc<LARGE_INTEGER>()
        QueryPerformanceCounter(now.ptr)
        return now.QuadPart
    }
}
