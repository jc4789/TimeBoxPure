package com.example.timeboxvibe.engine.core


data class EngineUiState(
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
    val selectedFocusSound: String = "synth-chime",
    val selectedRelaxSound: String = "oriental",
    val appTheme: String = "reimu",
    val language: String = "en",
    val isExactAlarmPermitted: Boolean = true,
    val presets: List<TimerPreset> = emptyList()
)

interface TimerActions {
    fun startTimer()
    fun stopTimer()
    fun resetTimer()
    fun skipTimer()
    fun dismissAlarm()
    fun updateTask(task: String)
    fun updateSettings(strictMode: Boolean, tickEnabled: Boolean, sound: String, vibeIntensity: Float)
    fun updateFocusSound(sound: String)
    fun updateRelaxSound(sound: String)
    fun updateLanguage(code: String)
    fun updateTheme(themeName: String)
    fun selectPreset(id: String)
    fun addCustomPreset(preset: TimerPreset)
    fun deletePreset(id: String)
    fun previewSound(key: String)
    fun requestExactAlarmPermission()
    fun updateVolume(vol: Float)
    fun getUiState(): EngineUiState
}
