package com.example.timeboxvibe.engine.core

object EngineInputCodes {
    const val COMMAND_FLAG = 0x10000000
    const val CMD_BACKSPACE = COMMAND_FLAG or 1
    const val CMD_LEFT      = COMMAND_FLAG or 2
    const val CMD_RIGHT     = COMMAND_FLAG or 3
    const val CMD_ENTER     = COMMAND_FLAG or 4
}

/**
 * Engine-side haptic feedback type constants.
 * The platform adapter (Android/iOS/Win32) maps these to native haptic APIs.
 * Zero allocation: const val Int is inlined at compile time.
 */
object EngineHaptics {
    const val TICK  = 0
    const val CLICK = 1
    const val IMPACT = 2
}


