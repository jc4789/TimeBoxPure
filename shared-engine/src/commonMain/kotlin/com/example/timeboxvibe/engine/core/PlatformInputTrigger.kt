package com.example.timeboxvibe.engine.core

interface PlatformInputTrigger {
    fun triggerKeyboard()
    fun performHapticFeedback(type: Int)
}
