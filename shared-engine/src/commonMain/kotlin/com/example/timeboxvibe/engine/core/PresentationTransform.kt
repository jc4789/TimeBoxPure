package com.example.timeboxvibe.engine.core

/**
 * Integer source-frame to output-surface mapping shared by every platform terminal.
 *
 * The primitive framebuffer is derived from the same terminal dimensions, then
 * presented across the complete client area. Pointer input uses this exact inverse.
 */
class PresentationTransform {
    var sourceWidth: Int = 1
        private set
    var sourceHeight: Int = 1
        private set
    var outputWidth: Int = 1
        private set
    var outputHeight: Int = 1
        private set
    var viewportX: Int = 0
        private set
    var viewportY: Int = 0
        private set
    var viewportWidth: Int = 1
        private set
    var viewportHeight: Int = 1
        private set

    fun configure(
        primitiveWidth: Int,
        primitiveHeight: Int,
        terminalWidth: Int,
        terminalHeight: Int
    ) {
        sourceWidth = primitiveWidth.coerceAtLeast(1)
        sourceHeight = primitiveHeight.coerceAtLeast(1)
        outputWidth = terminalWidth.coerceAtLeast(1)
        outputHeight = terminalHeight.coerceAtLeast(1)

        viewportX = 0
        viewportY = 0
        viewportWidth = outputWidth
        viewportHeight = outputHeight
    }

    fun primitiveX(terminalX: Int): Int {
        if (terminalX < viewportX || terminalX >= viewportX + viewportWidth) return OUTSIDE
        return (((terminalX - viewportX).toLong() * sourceWidth) / viewportWidth)
            .toInt()
            .coerceAtMost(sourceWidth - 1)
    }

    fun primitiveY(terminalY: Int): Int {
        if (terminalY < viewportY || terminalY >= viewportY + viewportHeight) return OUTSIDE
        return (((terminalY - viewportY).toLong() * sourceHeight) / viewportHeight)
            .toInt()
            .coerceAtMost(sourceHeight - 1)
    }

    fun containsTerminalPoint(terminalX: Int, terminalY: Int): Boolean {
        return primitiveX(terminalX) != OUTSIDE && primitiveY(terminalY) != OUTSIDE
    }

    companion object {
        const val OUTSIDE = -1
    }
}
