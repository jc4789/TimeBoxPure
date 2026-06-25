package com.example.timeboxvibe.ui.main

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeboxvibe.AppLifecycleTracker
import com.example.timeboxvibe.FocusService
import com.example.timeboxvibe.TimerStateHolder
import com.example.timeboxvibe.engine.core.TimerEngine
import com.example.timeboxvibe.engine.core.TimerPreset
import com.example.timeboxvibe.engine.SoundMelodies
import com.example.timeboxvibe.engine.SoundPreviewPlayer
import com.example.timeboxvibe.engine.getDefaultPresets
import com.example.timeboxvibe.engine.DEFAULT_PRESETS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import com.example.timeboxvibe.engine.core.TimerActions
import com.example.timeboxvibe.engine.core.EngineUiState

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Full UI state for the entire app.
 * A pure, immutable representation of the Arcade Engine at any given frame.
 */
data class TimerUiState(
    val timeRemaining: Int = 25 * 60,
    val totalDuration: Int = 25 * 60,
    val midTimeRemaining: Int = 0,
    val midTotalDuration: Int = 0,
    val bigTimeRemaining: Int = 0,
    val bigTotalDuration: Int = 0,
    val currentIndex: Int = 0,
    val sequenceLength: Int = 0,

    val isRunning: Boolean = false,
    val isRinging: Boolean = false,
    val isBreak: Boolean = false,

    val activePresetId: String = "dual_box",
    val activeMode: String = "dual",
    val isDual: Boolean = true,

    val currentTask: String = "",
    val strictMode: Boolean = false,
    val tickEnabled: Boolean = false,
    val vibeIntensity: Float = 0.8f,
    val volume: Float = 0.5f,
    val selectedSound: String = "synth-chime",
    val selectedFocusSound: String = "synth-chime",
    val selectedRelaxSound: String = "oriental",
    val appTheme: String = "reimu",
    val language: String = "en",
    val activeTab: String = "timer",
    val presets: List<TimerPreset> = emptyList(),
    val isExactAlarmPermitted: Boolean = true
)

class MainScreenViewModel(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) : ViewModel(), TimerActions {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    init {
        checkExactAlarmPermission()
        // Collect state updates from background service reactively via TimerStateHolder Flow
        viewModelScope.launch {
            TimerStateHolder.state.collect { serviceState ->
                if (serviceState != null) {
                    _uiState.value = _uiState.value.copy(
                        timeRemaining = serviceState.timeRemaining,
                        totalDuration = serviceState.totalDuration,
                        midTimeRemaining = serviceState.midTimeRemaining,
                        midTotalDuration = serviceState.midTotalDuration,
                        bigTimeRemaining = serviceState.bigTimeRemaining,
                        bigTotalDuration = serviceState.bigTotalDuration,
                        currentIndex = serviceState.currentIndex,
                        isRunning = serviceState.isActive,
                        isRinging = serviceState.isRinging,
                        isBreak = serviceState.isBreak
                    )

                    // THE MECHANICAL HEARTBEAT:
                    // If the timer is ticking and the app is on screen, play the physical tick sound.
                    if (serviceState.isActive && !serviceState.isRinging && _uiState.value.tickEnabled && AppLifecycleTracker.isForeground) {
                        SoundPreviewPlayer.playTick(_uiState.value.volume)
                    }
                }
            }
        }

        // Load saved settings directly from the metal (DataStore)
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                val savedPresetId = prefs[stringPreferencesKey("activePresetId")] ?: "dual_box"
                val customPresetsJson = prefs[stringPreferencesKey("custom_presets_json")] ?: "[]"
                val lang = prefs[stringPreferencesKey("language")] ?: "en"
                val savedTaskName = prefs[stringPreferencesKey("currentTaskName")] ?: ""
                val focusSound = prefs[stringPreferencesKey("selectedFocusSound")] ?: prefs[stringPreferencesKey("selectedSound")] ?: "synth-chime"
                val relaxSound = prefs[stringPreferencesKey("selectedRelaxSound")] ?: "oriental"

                val customPresets = parseCustomPresets(customPresetsJson)
                val defaultPresets = getDefaultPresets(lang)
                val emergencyPreset = TimerPreset(
                    id = "emergency",
                    name = if (lang == "zh") "紧急专注" else if (lang == "ja") "緊急セッション" else "Emergency Session",
                    mode = "classic",
                    sequence = intArrayOf(25 * 60),
                    alarmBehavior = "alarm",
                    description = "SYS.OVERRIDE // EMERGENCY"
                )
                val combined = defaultPresets + customPresets + emergencyPreset

                val preset = combined.firstOrNull { it.id == savedPresetId } ?: defaultPresets.firstOrNull() ?: return@collect

                _uiState.value = _uiState.value.copy(
                    strictMode = prefs[booleanPreferencesKey("strictMode")] ?: false,
                    tickEnabled = prefs[booleanPreferencesKey("tickEnabled")] ?: false,
                    selectedSound = prefs[stringPreferencesKey("selectedSound")] ?: "synth-chime",
                    selectedFocusSound = focusSound,
                    selectedRelaxSound = relaxSound,
                    vibeIntensity = prefs[floatPreferencesKey("vibeIntensity")] ?: 0.8f,
                    volume = prefs[floatPreferencesKey("volume")] ?: 0.5f,
                    appTheme = prefs[stringPreferencesKey("appTheme")] ?: "reimu",
                    language = lang,
                    activePresetId = savedPresetId,
                    presets = combined,
                    currentTask = savedTaskName
                )

                // Hard-sync with the engine or fallback to saved state
                val activeEngine = FocusService.engine
                val hasSavedState = prefs[booleanPreferencesKey("has_saved_state")] ?: false
                if (activeEngine != null) {
                    syncStateToActiveEngine(activeEngine)
                } else if (hasSavedState) {
                    syncStateToSavedState(prefs, preset)
                } else {
                    syncStateToIdlePreset(preset)
                }
            }
        }
    }

    // --- ENGINE IGNITION ---

    override fun startTimer() {
        SoundPreviewPlayer.stop()
        val state = _uiState.value
        val preset = state.presets.firstOrNull { it.id == state.activePresetId } ?: return

        // Fire up the unkillable foreground service
        val intent = Intent(context, FocusService::class.java).apply {
            putExtra("presetId", preset.id)
            putExtra("presetName", preset.name)
            putExtra("presetMode", preset.mode)
            
            putExtra("dualBigDuration", preset.dualBigDuration)
            putExtra("dualMidDuration", preset.dualMidDuration)
            putExtra("dualSmallDuration", preset.dualSmallDuration)
            putExtra("alarmBehavior", preset.alarmBehavior)
            putExtra("presetDescription", preset.description)

            putExtra("taskName", state.currentTask.ifEmpty { "FOCUS SESSION" })
            putExtra("vibeIntensity", state.vibeIntensity.toDouble())
            putExtra("volume", state.volume.toDouble())
            putExtra("strictMode", state.strictMode)
            putExtra("tickEnabled", state.tickEnabled)
            putExtra("selectedSound", state.selectedSound)
            putExtra("selectedFocusSound", state.selectedFocusSound)
            putExtra("selectedRelaxSound", state.selectedRelaxSound)
            putExtra("appTheme", state.appTheme)
            putExtra("isBreak", state.isBreak)

            // Calendar sequence types and labels
            putIntegerArrayListExtra("presetSequence", ArrayList(preset.sequence.toList()))
            putStringArrayListExtra("presetSequenceTypes", ArrayList(preset.sequenceTypes.toList()))
            putStringArrayListExtra("presetSequenceLabels", ArrayList(preset.sequenceLabels.toList()))

            // Resume state values
            putExtra("resumeTimeRemaining", state.timeRemaining)
            putExtra("resumeMidTimeRemaining", state.midTimeRemaining)
            putExtra("resumeBigTimeRemaining", state.bigTimeRemaining)
            putExtra("resumeCurrentIndex", state.currentIndex)
        }

        ContextCompat.startForegroundService(context, intent)
    }

    override fun stopTimer() {
        SoundPreviewPlayer.stop()
        sendEngineCommand("PAUSE_TIMER")
    }

    override fun resetTimer() {
        SoundPreviewPlayer.stop()
        sendEngineCommand("RESET_TIMER")
    }

    override fun skipTimer() {
        sendEngineCommand("SKIP_TIMER")
    }

    override fun dismissAlarm() {
        sendEngineCommand("DISMISS_ALARM")
    }

    private fun sendEngineCommand(actionCommand: String) {
        val intent = Intent(context, FocusService::class.java).apply {
            action = actionCommand
        }
        context.startService(intent)
    }

    // --- CARTRIDGE SWAPPING (Presets) ---

    override fun selectPreset(id: String) {
        SoundPreviewPlayer.stop()
        val preset = _uiState.value.presets.firstOrNull { it.id == id } ?: return

        // Hard stop the current engine before slotting the new preset in
        stopBackgroundService()

        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("activePresetId")] = id
                prefs[booleanPreferencesKey("has_saved_state")] = false
            }
        }

        syncStateToIdlePreset(preset)
        _uiState.value = _uiState.value.copy(activePresetId = id, activeTab = "timer")
    }

    fun toggleBreak() {
        val nextBreak = !_uiState.value.isBreak
        _uiState.value = _uiState.value.copy(isBreak = nextBreak)
        if (_uiState.value.isRunning) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = "UPDATE_SETTINGS"
                putExtra("isBreak", nextBreak)
                putExtra("appTheme", _uiState.value.appTheme)
            }
            context.startService(intent)
        }
    }

    override fun updateTask(task: String) {
        _uiState.value = _uiState.value.copy(currentTask = task)
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("currentTaskName")] = task
            }
        }
    }

    fun navigateTo(tab: String) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun applyPreset(durationSeconds: Int, isBreak: Boolean) {
        stopBackgroundService()
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("activePresetId")] = "emergency"
                prefs[booleanPreferencesKey("has_saved_state")] = false
            }
        }
        _uiState.value = _uiState.value.copy(
            isBreak = isBreak,
            activePresetId = "emergency",
            activeMode = "classic",
            isDual = false,
            timeRemaining = durationSeconds,
            totalDuration = durationSeconds,
            midTimeRemaining = 0, midTotalDuration = 0,
            bigTimeRemaining = 0, bigTotalDuration = 0,
            currentIndex = 0, isRunning = false, isRinging = false
        )
    }

    // --- HARDWARE SETTINGS ---

    override fun updateSettings(strictMode: Boolean, tickEnabled: Boolean, sound: String, vibeIntensity: Float) {
        _uiState.value = _uiState.value.copy(
            strictMode = strictMode, tickEnabled = tickEnabled,
            selectedSound = sound, vibeIntensity = vibeIntensity
        )

        if (_uiState.value.isRunning) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = "UPDATE_SETTINGS"
                putExtra("strictMode", strictMode)
                putExtra("tickEnabled", tickEnabled)
                putExtra("selectedSound", sound)
                putExtra("selectedFocusSound", _uiState.value.selectedFocusSound)
                putExtra("selectedRelaxSound", _uiState.value.selectedRelaxSound)
                putExtra("vibeIntensity", vibeIntensity.toDouble())
                putExtra("volume", _uiState.value.volume.toDouble())
            }
            context.startService(intent)
        }

        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("strictMode")] = strictMode
                prefs[booleanPreferencesKey("tickEnabled")] = tickEnabled
                prefs[stringPreferencesKey("selectedSound")] = sound
                prefs[floatPreferencesKey("vibeIntensity")] = vibeIntensity
            }
        }
    }

    override fun updateFocusSound(sound: String) {
        _uiState.value = _uiState.value.copy(selectedFocusSound = sound)
        if (_uiState.value.isRunning) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = "UPDATE_SETTINGS"
                putExtra("selectedFocusSound", sound)
            }
            context.startService(intent)
        }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("selectedFocusSound")] = sound
            }
        }
    }

    override fun updateRelaxSound(sound: String) {
        _uiState.value = _uiState.value.copy(selectedRelaxSound = sound)
        if (_uiState.value.isRunning) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = "UPDATE_SETTINGS"
                putExtra("selectedRelaxSound", sound)
            }
            context.startService(intent)
        }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("selectedRelaxSound")] = sound
            }
        }
    }

    override fun previewSound(key: String) {
        SoundPreviewPlayer.playPreview(context, key, _uiState.value.volume)
    }

    override fun updateVolume(vol: Float) {
        _uiState.value = _uiState.value.copy(volume = vol)
        if (_uiState.value.isRunning) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = "UPDATE_SETTINGS"
                putExtra("volume", vol.toDouble())
            }
            context.startService(intent)
        }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[floatPreferencesKey("volume")] = vol
            }
        }
    }

    override fun updateTheme(themeName: String) {
        _uiState.value = _uiState.value.copy(appTheme = themeName)
        if (_uiState.value.isRunning) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = "UPDATE_SETTINGS"
                putExtra("appTheme", themeName)
                putExtra("isBreak", _uiState.value.isBreak)
            }
            context.startService(intent)
        }
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[stringPreferencesKey("appTheme")] = themeName }
        }
    }

    override fun updateLanguage(code: String) {
        _uiState.value = _uiState.value.copy(language = code)
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[stringPreferencesKey("language")] = code }
        }
    }

    private fun stopBackgroundService() {
        val intent = Intent(context, FocusService::class.java)
        context.stopService(intent)
    }

    // --- STATE SYNC HELPERS ---

    private fun syncStateToIdlePreset(preset: TimerPreset) {
        val (smallRemaining, smallTotal) = when (preset.mode) {
            "classic", "sequence", "calendar" -> (preset.sequence.firstOrNull() ?: 1500) to (preset.sequence.firstOrNull() ?: 1500)
            else -> preset.dualSmallDuration to preset.dualSmallDuration
        }
        val (bigRemaining, bigTotal) = when (preset.mode) {
            "dual", "dual.5" -> preset.dualBigDuration to preset.dualBigDuration
            "dual-sequence" -> (preset.sequence.firstOrNull() ?: 600) to (preset.sequence.firstOrNull() ?: 600)
            "calendar" -> preset.sequence.sum() to preset.sequence.sum()
            else -> 0 to 0
        }
        val (midRemaining, midTotal) = if (preset.mode == "dual.5") preset.dualMidDuration to preset.dualMidDuration else 0 to 0

        _uiState.value = _uiState.value.copy(
            timeRemaining = smallRemaining, totalDuration = smallTotal,
            midTimeRemaining = midRemaining, midTotalDuration = midTotal,
            bigTimeRemaining = bigRemaining, bigTotalDuration = bigTotal,
            currentIndex = 0, isRunning = false, isRinging = false,
            activeMode = preset.mode,
            isDual = preset.mode in listOf("dual", "dual.5", "dual-sequence", "calendar"),
            isBreak = if (preset.mode == "calendar") (preset.sequenceTypes.firstOrNull() == "relax") else _uiState.value.isBreak
        )
    }

    private fun syncStateToActiveEngine(activeEngine: TimerEngine) {
        _uiState.value = _uiState.value.copy(
            timeRemaining = activeEngine.timeRemaining, totalDuration = activeEngine.totalDuration,
            midTimeRemaining = activeEngine.midTimeRemaining, midTotalDuration = activeEngine.midTotalDuration,
            bigTimeRemaining = activeEngine.bigTimeRemaining, bigTotalDuration = activeEngine.bigTotalDuration,
            currentIndex = activeEngine.currentIndex, isRunning = activeEngine.isActive,
            isRinging = activeEngine.isRinging, activeMode = activeEngine.mode, isDual = activeEngine.isDual
        )
    }

    private fun syncStateToSavedState(prefs: Preferences, preset: TimerPreset) {
        val savedTime = prefs[intPreferencesKey("saved_time_remaining")] ?: preset.dualSmallDuration
        val savedTotal = prefs[intPreferencesKey("saved_total_duration")] ?: preset.dualSmallDuration
        val savedMid = prefs[intPreferencesKey("saved_mid_time_remaining")] ?: 0
        val savedMidTotal = prefs[intPreferencesKey("saved_mid_total_duration")] ?: 0
        val savedBig = prefs[intPreferencesKey("saved_big_time_remaining")] ?: 0
        val savedBigTotal = prefs[intPreferencesKey("saved_big_total_duration")] ?: 0
        val savedIndex = prefs[intPreferencesKey("saved_current_index")] ?: 0

        _uiState.value = _uiState.value.copy(
            timeRemaining = savedTime,
            totalDuration = savedTotal,
            midTimeRemaining = savedMid,
            midTotalDuration = savedMidTotal,
            bigTimeRemaining = savedBig,
            bigTotalDuration = savedBigTotal,
            currentIndex = savedIndex,
            isRunning = false,
            isRinging = false,
            activeMode = preset.mode,
            isDual = preset.mode in listOf("dual", "dual.5", "dual-sequence", "calendar"),
            isBreak = if (preset.mode == "calendar") (savedIndex < preset.sequenceTypes.size && preset.sequenceTypes[savedIndex] == "relax") else _uiState.value.isBreak
        )
    }

    // --- JSON PARSING WITH KOTLINX.SERIALIZATION ---

    private fun parseCustomPresets(jsonStr: String): List<TimerPreset> {
        return try {
            Json.decodeFromString<List<TimerPreset>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun addCustomPreset(preset: TimerPreset) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val customPresetsJson = prefs[stringPreferencesKey("custom_presets_json")] ?: "[]"
                val customPresets = parseCustomPresets(customPresetsJson).toMutableList()
                customPresets.add(preset)

                prefs[stringPreferencesKey("custom_presets_json")] = Json.encodeToString(customPresets)
                prefs[stringPreferencesKey("activePresetId")] = preset.id
            }
        }
    }

    override fun deletePreset(id: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val customPresetsJson = prefs[stringPreferencesKey("custom_presets_json")] ?: "[]"
                val customPresets = parseCustomPresets(customPresetsJson).filter { it.id != id }

                prefs[stringPreferencesKey("custom_presets_json")] = Json.encodeToString(customPresets)

                if (prefs[stringPreferencesKey("activePresetId")] == id) {
                    prefs[stringPreferencesKey("activePresetId")] = "dual_box"
                }
            }
        }
    }

    override fun getUiState(): EngineUiState {
        val s = _uiState.value
        val currentPreset = s.presets.firstOrNull { it.id == s.activePresetId }
        val seqLen = currentPreset?.sequence?.size ?: 0
        return EngineUiState(
            timeRemaining = s.timeRemaining,
            totalDuration = s.totalDuration,
            midTimeRemaining = s.midTimeRemaining,
            midTotalDuration = s.midTotalDuration,
            bigTimeRemaining = s.bigTimeRemaining,
            bigTotalDuration = s.bigTotalDuration,
            currentIndex = s.currentIndex,
            sequenceLength = seqLen,
            isRunning = s.isRunning,
            isRinging = s.isRinging,
            isBreak = s.isBreak,
            activePresetId = s.activePresetId,
            activeMode = s.activeMode,
            isDual = s.isDual,
            currentTask = s.currentTask,
            strictMode = s.strictMode,
            tickEnabled = s.tickEnabled,
            vibeIntensity = s.vibeIntensity,
            volume = s.volume,
            selectedFocusSound = s.selectedFocusSound,
            selectedRelaxSound = s.selectedRelaxSound,
            appTheme = s.appTheme,
            language = s.language,
            isExactAlarmPermitted = s.isExactAlarmPermitted,
            presets = s.presets
        )
    }

    fun checkExactAlarmPermission() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val isPermitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        _uiState.value = _uiState.value.copy(isExactAlarmPermitted = isPermitted)
    }

    override fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class Factory(
        private val context: Context,
        private val dataStore: DataStore<Preferences>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainScreenViewModel(context, dataStore) as T
        }
    }
}
