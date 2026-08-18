package com.example.timeboxvibe.engine.core

/** Keeps the indexed framebuffer identical to the terminal's real pixel dimensions. */
object PrimitiveDisplayProfile {
    const val MAX_PRIMITIVE_PIXELS = Int.MAX_VALUE

    fun primitiveWidth(outputWidth: Int, outputHeight: Int): Int =
        if (outputHeight <= 0) 1 else outputWidth.coerceAtLeast(1)

    fun primitiveHeight(outputWidth: Int, outputHeight: Int): Int =
        if (outputWidth <= 0) 1 else outputHeight.coerceAtLeast(1)
}
