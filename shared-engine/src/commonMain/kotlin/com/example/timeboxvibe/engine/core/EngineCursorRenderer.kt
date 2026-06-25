package com.example.timeboxvibe.engine.core

// Run inside your main frame draw loop
class EngineCursorRenderer {
    private var blinkAccumulator = 0.0f
    private val blinkRate = 0.5f // Blink phase flips every 500ms

    fun drawVirtualCursor(
        renderer: ScaledProceduralRenderer,
        deltaTime: Float,
        isFocused: Boolean,
        fieldGridX: Int,
        fieldGridY: Int,
        cursorIndex: Int,
        cursorWidth: Int = 10,
        cursorHeight: Int = 16,
        colorIndex: Int = PaletteIndices.PRIMARY
    ) {
        if (!isFocused) return

        // Step 1: Update time accumulator completely on the stack
        blinkAccumulator += deltaTime
        if (blinkAccumulator >= blinkRate * 2f) {
            blinkAccumulator -= blinkRate * 2f
        }

        // Step 2: Extract boolean blink visibility state mathematically
        val isVisible = blinkAccumulator < blinkRate

        if (isVisible) {
            // Step 3: Compute screen bounds using your 16x16 layout constraints
            val cursorPixelX = (fieldGridX + cursorIndex) * 16f
            val cursorPixelY = fieldGridY * 16f

            // Step 4: Blit a solid block into VRAM using dither fill with same colors
            renderer.fillRectDither(
                x0 = cursorPixelX,
                y0 = cursorPixelY,
                x1 = cursorPixelX + cursorWidth.toFloat(),
                y1 = cursorPixelY + cursorHeight.toFloat(),
                primaryIndex = colorIndex,
                secondaryIndex = colorIndex,
                pattern = SoftDitherPattern.SOLID // Solid block
            )
        }
    }
}
