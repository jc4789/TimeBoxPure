package com.example.timeboxvibe.engine.core

/** Derives a client-shaped software framebuffer within a deterministic pixel budget. */
object PrimitiveDisplayProfile {
    const val MAX_PRIMITIVE_PIXELS = 1 shl 20
    // Pixel doubling presents one 16x16 ROM glyph cell as 32x32 terminal pixels.
    private const val MIN_TERMINAL_PIXEL_BLOCK = 2

    fun primitiveWidth(outputWidth: Int, outputHeight: Int): Int {
        val safeWidth = outputWidth.coerceAtLeast(1)
        val safeHeight = outputHeight.coerceAtLeast(1)
        val preferredWidth = ceilDiv(safeWidth, MIN_TERMINAL_PIXEL_BLOCK)
        val preferredHeight = ceilDiv(safeHeight, MIN_TERMINAL_PIXEL_BLOCK)
        if (fitsPixelBudget(preferredWidth, preferredHeight)) return preferredWidth

        val longAxis = boundedLongAxis(preferredWidth, preferredHeight)
        return if (preferredWidth >= preferredHeight) {
            longAxis
        } else {
            proportionalShortAxis(longAxis, preferredHeight, preferredWidth)
        }
    }

    fun primitiveHeight(outputWidth: Int, outputHeight: Int): Int {
        val safeWidth = outputWidth.coerceAtLeast(1)
        val safeHeight = outputHeight.coerceAtLeast(1)
        val preferredWidth = ceilDiv(safeWidth, MIN_TERMINAL_PIXEL_BLOCK)
        val preferredHeight = ceilDiv(safeHeight, MIN_TERMINAL_PIXEL_BLOCK)
        if (fitsPixelBudget(preferredWidth, preferredHeight)) return preferredHeight

        val longAxis = boundedLongAxis(preferredWidth, preferredHeight)
        return if (preferredHeight > preferredWidth) {
            longAxis
        } else {
            proportionalShortAxis(longAxis, preferredWidth, preferredHeight)
        }
    }

    private fun boundedLongAxis(width: Int, height: Int): Int {
        val preferredLongAxis = maxOf(width, height)
        val preferredShortAxis = minOf(width, height)
        var low = 1
        var high = preferredLongAxis
        var best = 1

        while (low <= high) {
            val candidateLongAxis = low + ((high - low) ushr 1)
            val candidateShortAxis = proportionalShortAxis(
                candidateLongAxis,
                preferredLongAxis,
                preferredShortAxis
            )
            if (fitsPixelBudget(candidateLongAxis, candidateShortAxis)) {
                best = candidateLongAxis
                low = candidateLongAxis + 1
            } else {
                high = candidateLongAxis - 1
            }
        }
        return best
    }

    private fun proportionalShortAxis(
        primitiveLongAxis: Int,
        preferredLongAxis: Int,
        preferredShortAxis: Int
    ): Int {
        return (
            (primitiveLongAxis.toLong() * preferredShortAxis + preferredLongAxis / 2L) /
                preferredLongAxis
            ).toInt().coerceAtLeast(1)
    }

    private fun fitsPixelBudget(width: Int, height: Int): Boolean {
        return width.toLong() * height.toLong() <= MAX_PRIMITIVE_PIXELS
    }

    private fun ceilDiv(value: Int, divisor: Int): Int {
        return ((value.toLong() + divisor - 1L) / divisor).toInt()
    }
}
