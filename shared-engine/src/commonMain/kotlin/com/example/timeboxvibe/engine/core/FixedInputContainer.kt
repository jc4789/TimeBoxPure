package com.example.timeboxvibe.engine.core

class FixedInputContainer(val maxCapacity: Int = 128) {
    // Array holds full 32-bit CodePoints instead of 16-bit Chars
    val codePoints = IntArray(maxCapacity)
    var length = 0
        private set
    var cursor = 0
        private set

    fun processPayload(payload: Int) {
        if ((payload and EngineInputCodes.COMMAND_FLAG) != 0) {
            handleCommand(payload)
        } else {
            insertCodePoint(payload) // Directly takes the 32-bit int
        }
    }

    private fun insertCodePoint(codePoint: Int) {
        if (length >= maxCapacity) return
        if (cursor < length) {
            codePoints.copyInto(codePoints, cursor + 1, cursor, length)
        }
        codePoints[cursor] = codePoint
        cursor++
        length++
    }

    private fun handleCommand(command: Int) {
        when (command) {
            EngineInputCodes.CMD_BACKSPACE -> {
                if (cursor > 0) {
                    if (cursor < length) {
                        codePoints.copyInto(codePoints, cursor - 1, cursor, length)
                    }
                    cursor--
                    length--
                }
            }
            EngineInputCodes.CMD_LEFT -> if (cursor > 0) cursor--
            EngineInputCodes.CMD_RIGHT -> if (cursor < length) cursor++
            EngineInputCodes.CMD_ENTER -> { /* Handle submit later or externally */ }
        }
    }
}
