package com.example.timeboxvibe

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimerServiceState(
    val timeRemaining: Int = 0,
    val totalDuration: Int = 0,
    val midTimeRemaining: Int = 0,
    val midTotalDuration: Int = 0,
    val bigTimeRemaining: Int = 0,
    val bigTotalDuration: Int = 0,
    val currentIndex: Int = 0,
    val isActive: Boolean = false,
    val isRinging: Boolean = false,
    val isBreak: Boolean = false
)

object TimerStateHolder {
    private val _state = MutableStateFlow<TimerServiceState?>(null)
    val state: StateFlow<TimerServiceState?> = _state.asStateFlow()

    fun update(
        timeRemaining: Int,
        totalDuration: Int,
        midTimeRemaining: Int,
        midTotalDuration: Int,
        bigTimeRemaining: Int,
        bigTotalDuration: Int,
        currentIndex: Int,
        isActive: Boolean,
        isRinging: Boolean,
        isBreak: Boolean
    ) {
        _state.value = TimerServiceState(
            timeRemaining = timeRemaining,
            totalDuration = totalDuration,
            midTimeRemaining = midTimeRemaining,
            midTotalDuration = midTotalDuration,
            bigTimeRemaining = bigTimeRemaining,
            bigTotalDuration = bigTotalDuration,
            currentIndex = currentIndex,
            isActive = isActive,
            isRinging = isRinging,
            isBreak = isBreak
        )
    }

    fun clear() {
        _state.value = null
    }
}

object AppLifecycleTracker {
    val isForeground: Boolean
        get() = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
}
