package com.example.timeboxvibe.engine.win

import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.core.EngineUiState
import com.example.timeboxvibe.engine.core.SessionMacroDisplay
import com.example.timeboxvibe.engine.core.TimerActions
import com.example.timeboxvibe.engine.core.TimerEngine
import com.example.timeboxvibe.engine.core.TimerPreset
import com.example.timeboxvibe.engine.getDefaultPresets

/**
 * Windows application coordinator. Native calls stay in the injected dummy
 * terminals; this class only coordinates the shared timer contract.
 */
internal class Win32TimerController(
    private val alarmScheduler: Win32AlarmScheduler,
    private val audio: Win32Audio,
    private val power: Win32Power,
    private val feedback: Win32Feedback,
    private val settingsStore: Win32SettingsStore,
    private val qpcFrequency: Long
) : TimerActions {
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
    private var presets = emptyList<TimerPreset>()
    private var activePresetId = "dual_box"
    private var engine: TimerEngine? = null
    private var lastTickQpc = 0L
    private var lastObservedQpc = 0L

    init {
        applyPersisted(settingsStore.load())
        rebuildPresetCache()
        rebuildIdleEngine()
    }

    fun pump(nowQpc: Long) {
        lastObservedQpc = nowQpc
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

    fun onOsAlarm(nowQpc: Long) {
        val eng = engine ?: return
        if (!eng.isActive || eng.isRinging) return
        eng.hasSyncInterruption = true
        pump(nowQpc)
    }

    override fun startTimer() {
        audio.stop()
        val preset = presetById(activePresetId) ?: return
        val existing = engine
        if (existing != null && existing.preset.id == preset.id && !existing.isRinging) {
            lastTickQpc = lastObservedQpc
            existing.start()
            power.acquireSession()
            return
        }
        val next = TimerEngine(preset)
        next.alarmScheduler = alarmScheduler
        engine = next
        lastTickQpc = lastObservedQpc
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
        val eng = engine ?: return
        if (eng.isRinging) {
            audio.stop()
            eng.dismissAlarm()
        } else {
            eng.skip()
        }
        synchronizeRuntimeState(eng)
    }

    override fun dismissAlarm() {
        audio.stop()
        val eng = engine ?: return
        eng.dismissAlarm()
        synchronizeRuntimeState(eng)
    }

    override fun updateTask(task: String) {
        currentTask = task
        requestSettingsSave()
    }

    override fun updateSettings(strictMode: Boolean, tickEnabled: Boolean, sound: String, vibeIntensity: Float) {
        this.strictMode = strictMode
        this.tickEnabled = tickEnabled
        this.vibeIntensity = vibeIntensity
        selectedFocusSound = catalogOrDefault(sound, SongCatalog.DEFAULT_FOCUS_ID)
        requestSettingsSave()
    }

    override fun updateFocusSound(sound: String) {
        selectedFocusSound = catalogOrDefault(sound, SongCatalog.DEFAULT_FOCUS_ID)
        requestSettingsSave()
    }

    override fun updateRelaxSound(sound: String) {
        selectedRelaxSound = catalogOrDefault(sound, SongCatalog.DEFAULT_RELAX_ID)
        requestSettingsSave()
    }

    override fun updateLanguage(code: String) {
        language = code
        rebuildPresetCache()
        rebuildIdleEngine(keepRunning = true)
        requestSettingsSave()
    }

    override fun updateTheme(themeName: String) {
        appTheme = themeName
        requestSettingsSave()
    }

    override fun selectPreset(id: String) {
        audio.stop()
        engine?.pause()
        power.releaseAll()
        activePresetId = id
        rebuildIdleEngine()
        requestSettingsSave()
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
        rebuildPresetCache()
        if (engine?.isActive != true) {
            rebuildIdleEngine()
        }
        requestSettingsSave()
    }

    override fun deletePreset(id: String) {
        customPresets = customPresets.filter { it.id != id }
        rebuildPresetCache()
        if (activePresetId == id) {
            activePresetId = "dual_box"
            if (engine?.isActive != true) {
                rebuildIdleEngine()
            }
        }
        requestSettingsSave()
    }

    override fun previewSound(key: String) {
        if (!audio.playPreview(key, volume)) {
            feedback.reportAudioFailure(isAlarm = false)
        }
    }

    override fun requestExactAlarmPermission() {
        // Waitable timers do not require a capability prompt on desktop Windows.
    }

    override fun updateVolume(vol: Float) {
        volume = vol
        requestSettingsSave()
    }

    override fun getUiState(): EngineUiState {
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

    fun shutdown() {
        audio.stop()
        engine?.pause()
        power.releaseAll()
    }

    private fun handleTickEvent(event: TimerEngine.TickEvent, eng: TimerEngine) {
        when (event) {
            is TimerEngine.TickEvent.IntervalComplete,
            is TimerEngine.TickEvent.SequenceComplete -> {
                if (eng.isRinging) {
                    startPersistentAlarm(eng)
                } else {
                    val played = audio.playGentleReminder(alarmSoundKey(eng), volume)
                    if (!played) feedback.reportAudioFailure(isAlarm = false)
                    if (!eng.isActive) power.releaseAll()
                }
            }
            else -> {}
        }
    }

    private fun startPersistentAlarm(eng: TimerEngine) {
        val played = audio.playAlarm(alarmSoundKey(eng), volume)
        if (!played) feedback.reportAudioFailure(isAlarm = true)
        power.acquireAlarmDisplay()
        feedback.flashAlarm()
    }

    private fun synchronizeRuntimeState(eng: TimerEngine) {
        if (eng.isRinging) {
            startPersistentAlarm(eng)
        } else if (eng.isActive) {
            lastTickQpc = lastObservedQpc
            power.acquireSession()
        } else {
            power.releaseAll()
        }
    }

    private fun alarmSoundKey(eng: TimerEngine): String {
        return if (eng.mode == "calendar" && eng.isBreak) selectedRelaxSound else selectedFocusSound
    }

    private fun rebuildIdleEngine(keepRunning: Boolean = false) {
        val running = engine?.isActive == true && keepRunning
        if (running) return
        val preset = presetById(activePresetId) ?: presets.firstOrNull() ?: return
        val next = TimerEngine(preset)
        next.alarmScheduler = alarmScheduler
        engine = next
        lastTickQpc = 0L
    }

    private fun rebuildPresetCache() {
        val defaults = getDefaultPresets(language)
        val next = ArrayList<TimerPreset>(defaults.size + customPresets.size + 1)
        var i = 0
        while (i < defaults.size) {
            next.add(defaults[i].normalized(logFailures = true))
            i++
        }
        i = 0
        while (i < customPresets.size) {
            next.add(customPresets[i])
            i++
        }
        next.add(
            TimerPreset(
                id = "emergency",
                name = if (language == "zh") "紧急专注" else if (language == "ja") "緊急セッション" else "Emergency Session",
                mode = "classic",
                sequence = intArrayOf(25 * 60),
                alarmBehavior = "alarm",
                description = "SYS.OVERRIDE // EMERGENCY"
            ).normalized(logFailures = true)
        )
        presets = next
    }

    private fun presetById(id: String): TimerPreset? {
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

    private fun requestSettingsSave() {
        settingsStore.requestSave(
            language,
            appTheme,
            currentTask,
            strictMode,
            tickEnabled,
            vibeIntensity,
            volume,
            selectedFocusSound,
            selectedRelaxSound,
            activePresetId,
            customPresets
        )
    }
}
