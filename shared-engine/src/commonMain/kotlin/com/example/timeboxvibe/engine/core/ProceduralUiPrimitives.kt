package com.example.timeboxvibe.engine.core

object ProceduralIconRenderer {
    private const val ICON_SIZE = 32
    private const val WHITE_ARGB = 0xFFFFFFFF.toInt()
    private const val BLACK_ARGB = 0xFF000000.toInt()
    private const val ERROR_RED_ARGB = 0xFFCC0000.toInt()
    private const val FIRE_RED_ARGB = 0xFFFF2200.toInt()
    private const val GOLD_ARGB = 0xFFFFEE55.toInt()
    private const val YELLOW_ARGB = 0xFFFFCC00.toInt()
    private const val BLUE_ARGB = 0xFF00AAFF.toInt()
    private const val BROWN_ARGB = 0xFF8B4513.toInt()

    fun draw(
        renderer: ScaledProceduralRenderer,
        iconName: String,
        destX: Float,
        destY: Float,
        scale: Int,
        primaryColor: Int,
        onBackgroundColor: Int,
        surfaceColor: Int
    ) {
        var y = 0
        while (y < ICON_SIZE) {
            var x = 0
            while (x < ICON_SIZE) {
                val color = getPixelColor(iconName, x, y, primaryColor, onBackgroundColor, surfaceColor)
                if (color != 0) {
                    renderer.drawRect(
                        destX + x * scale,
                        destY + y * scale,
                        scale.toFloat(),
                        scale.toFloat(),
                        mapIconColor(color, primaryColor, onBackgroundColor, surfaceColor)
                    )
                }
                x++
            }
            y++
        }
    }

    private fun mapIconColor(color: Int, primaryColor: Int, onBackgroundColor: Int, surfaceColor: Int): Int {
        return when (color) {
            primaryColor -> primaryColor
            onBackgroundColor -> onBackgroundColor
            surfaceColor -> surfaceColor
            WHITE_ARGB -> PaletteIndices.WHITE
            BLACK_ARGB -> PaletteIndices.BLACK
            ERROR_RED_ARGB, FIRE_RED_ARGB -> PaletteIndices.ERROR
            GOLD_ARGB, YELLOW_ARGB -> PaletteIndices.SECONDARY
            BLUE_ARGB -> PaletteIndices.PRIMARY
            BROWN_ARGB -> PaletteIndices.SECONDARY
            else -> primaryColor
        }
    }
}

internal const val FULLWIDTH_DIGITS = "０１２３４５６７８９"
private const val FULLWIDTH_ASCII_OFFSET = 0xFEE0

internal fun toFullwidthDisplayChar(char: Char): Char {
    return when {
        char == ' ' -> '　'
        char == '"' -> '”'
        char == '\'' -> '’'
        char == '-' -> '‐'
        char == '~' -> '〜'
        char in '!'..'}' -> (char.code + FULLWIDTH_ASCII_OFFSET).toChar()
        else -> char
    }
}

object ProceduralTextRenderer {
    const val ALIGN_LEFT = 0
    const val ALIGN_CENTER = 1
    private const val CUSTOM_ID_SUFFIX_CHARS = 6
    private const val SYS_ID_PREFIX_CHARS = 8
    private const val SYS_ID_PREFIX = "ＳＹＳ＿ＩＤ：　"

    fun drawRaw(
        renderer: ScaledProceduralRenderer,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ) {
        val charW = ScaledProceduralRenderer.sourceCellSize(scale)
        var i = 0
        while (i < text.length) {
            renderer.drawGlyph(toFullwidthDisplayChar(text[i]), x + i * charW, y, color, scale = scale)
            i++
        }
    }

    fun measureWrappedLineCount(
        text: String,
        maxWidth: Float,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ): Int {
        if (text.isEmpty()) return 0
        val maxCells = cellsPerLine(maxWidth, scale)
        var lineCount = 0
        var start = 0
        while (start < text.length) {
            val end = stringLineEnd(text, start, maxCells)
            lineCount++
            start = stringNextLineStart(text, start, end)
        }
        return lineCount
    }

    fun measureWrappedHeight(
        text: String,
        maxWidth: Float,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ): Float {
        return measureWrappedLineCount(text, maxWidth, scale) * ScaledProceduralRenderer.sourceCellSize(scale)
    }

    fun measureHeadingHeight(text: String, maxWidth: Float, scale: Int): Float {
        return measureWrappedHeight(text, maxWidth, scale)
    }

    fun drawHeading(
        renderer: ScaledProceduralRenderer,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        color: Int,
        scale: Int,
        alignment: Int = ALIGN_LEFT,
        shadowColor: Int = EngineCanvas.COLOR_TRANSPARENT
    ) {
        drawWrapped(renderer, text, x, y, maxWidth, color, scale, alignment, shadowColor = shadowColor)
    }

    fun measurePresetIdHeight(
        id: String,
        maxWidth: Float,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ): Float {
        val suffixLength = minOf(CUSTOM_ID_SUFFIX_CHARS, id.length)
        val maxCells = cellsPerLine(maxWidth, scale)
        val lineCount = (SYS_ID_PREFIX_CHARS + suffixLength + maxCells - 1) / maxCells
        return lineCount * ScaledProceduralRenderer.sourceCellSize(scale)
    }

    fun drawPresetIdWrapped(
        renderer: ScaledProceduralRenderer,
        id: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        color: Int,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ) {
        val suffixStart = if (id.length > CUSTOM_ID_SUFFIX_CHARS) id.length - CUSTOM_ID_SUFFIX_CHARS else 0
        val suffixLength = id.length - suffixStart
        val maxCells = cellsPerLine(maxWidth, scale)
        val charSize = ScaledProceduralRenderer.sourceCellSize(scale)
        val cellCount = SYS_ID_PREFIX_CHARS + suffixLength
        var cell = 0
        while (cell < cellCount) {
            val source = if (cell < SYS_ID_PREFIX_CHARS) {
                SYS_ID_PREFIX[cell]
            } else {
                toUpperCase(id[suffixStart + cell - SYS_ID_PREFIX_CHARS])
            }
            val line = cell / maxCells
            val column = cell - line * maxCells
            renderer.drawGlyph(toFullwidthDisplayChar(source), x + column * charSize, y + line * charSize, color, scale = scale)
            cell++
        }
    }

    fun measureWrappedLineCount(
        buffer: IntArray,
        offset: Int,
        length: Int,
        maxWidth: Float,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ): Int {
        if (length <= 0) return 0
        val safeLength = minOf(length, buffer.size - offset)
        if (offset < 0 || safeLength <= 0) return 0
        val endExclusive = offset + safeLength
        val maxCells = cellsPerLine(maxWidth, scale)
        var lineCount = 0
        var start = offset
        while (start < endExclusive) {
            val end = bufferLineEnd(buffer, start, endExclusive, maxCells)
            lineCount++
            start = bufferNextLineStart(buffer, start, end, endExclusive)
        }
        return lineCount
    }

    fun measureWrappedHeight(
        buffer: IntArray,
        offset: Int,
        length: Int,
        maxWidth: Float,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ): Float {
        return measureWrappedLineCount(buffer, offset, length, maxWidth, scale) * ScaledProceduralRenderer.sourceCellSize(scale)
    }

    fun locateWrappedCursor(
        buffer: IntArray,
        offset: Int,
        length: Int,
        cursor: Int,
        maxWidth: Float,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
    ): Long {
        if (offset < 0 || offset >= buffer.size || length <= 0) return 0L
        val endExclusive = minOf(buffer.size, offset + length)
        val target = (offset + cursor.coerceIn(0, endExclusive - offset)).coerceIn(offset, endExclusive)
        val maxCells = cellsPerLine(maxWidth, scale)
        var start = offset
        var line = 0
        while (start < endExclusive) {
            val end = bufferLineEnd(buffer, start, endExclusive, maxCells)
            if (target <= end) return packCursor(line, target - start)
            val next = bufferNextLineStart(buffer, start, end, endExclusive)
            if (target < next) return packCursor(line, end - start)
            start = next
            line++
        }
        return packCursor(line, 0)
    }

    fun cursorLine(packedCursor: Long): Int {
        return (packedCursor ushr 32).toInt()
    }

    fun cursorColumn(packedCursor: Long): Int {
        return packedCursor.toInt()
    }

    fun drawWrapped(
        renderer: ScaledProceduralRenderer,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        color: Int,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY,
        alignment: Int = ALIGN_LEFT,
        uppercase: Boolean = false,
        shadowColor: Int = EngineCanvas.COLOR_TRANSPARENT
    ) {
        if (text.isEmpty()) return
        val maxCells = cellsPerLine(maxWidth, scale)
        val charSize = ScaledProceduralRenderer.sourceCellSize(scale)
        var start = 0
        var line = 0
        while (start < text.length) {
            val end = stringLineEnd(text, start, maxCells)
            val lineWidth = (end - start) * charSize
            val lineX = if (alignment == ALIGN_CENTER) x + (maxWidth - lineWidth) / 2f else x
            var index = start
            while (index < end) {
                val source = if (uppercase) toUpperCase(text[index]) else text[index]
                renderer.drawGlyph(
                    toFullwidthDisplayChar(source),
                    lineX + (index - start) * charSize,
                    y + line * charSize,
                    color,
                    shadowColorIndex = shadowColor,
                    scale = scale
                )
                index++
            }
            line++
            start = stringNextLineStart(text, start, end)
        }
    }

    fun drawWrapped(
        renderer: ScaledProceduralRenderer,
        buffer: IntArray,
        offset: Int,
        length: Int,
        x: Float,
        y: Float,
        maxWidth: Float,
        color: Int,
        scale: Int = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY,
        alignment: Int = ALIGN_LEFT,
        uppercase: Boolean = false,
        shadowColor: Int = EngineCanvas.COLOR_TRANSPARENT
    ) {
        if (offset < 0 || length <= 0 || offset >= buffer.size) return
        val endExclusive = minOf(buffer.size, offset + length)
        val maxCells = cellsPerLine(maxWidth, scale)
        val charSize = ScaledProceduralRenderer.sourceCellSize(scale)
        var start = offset
        var line = 0
        while (start < endExclusive) {
            val end = bufferLineEnd(buffer, start, endExclusive, maxCells)
            val lineWidth = (end - start) * charSize
            val lineX = if (alignment == ALIGN_CENTER) x + (maxWidth - lineWidth) / 2f else x
            var index = start
            while (index < end) {
                val rawChar = codePointDisplayChar(buffer[index])
                val source = if (uppercase) toUpperCase(rawChar) else rawChar
                renderer.drawGlyph(
                    toFullwidthDisplayChar(source),
                    lineX + (index - start) * charSize,
                    y + line * charSize,
                    color,
                    shadowColorIndex = shadowColor,
                    scale = scale
                )
                index++
            }
            line++
            start = bufferNextLineStart(buffer, start, end, endExclusive)
        }
    }

    private fun cellsPerLine(maxWidth: Float, scale: Int): Int {
        val safeScale = maxOf(ScaledProceduralRenderer.TEXT_SCALE_IDENTITY, scale)
        return maxOf(1, (maxWidth / ScaledProceduralRenderer.sourceCellSize(safeScale)).toInt())
    }

    private fun stringLineEnd(text: String, start: Int, maxCells: Int): Int {
        val hardEnd = minOf(text.length, start + maxCells)
        var index = start
        while (index < hardEnd) {
            if (text[index] == '\n') return index
            index++
        }
        if (hardEnd >= text.length || text[hardEnd] == '\n') return hardEnd

        index = hardEnd - 1
        while (index > start) {
            if (isDisplaySpace(text[index])) return index
            index--
        }
        return hardEnd
    }

    private fun stringNextLineStart(text: String, start: Int, end: Int): Int {
        var next = if (end > start) end else start + 1
        if (next < text.length && text[next] == '\n') next++
        while (next < text.length && isDisplaySpace(text[next])) next++
        return next
    }

    private fun bufferLineEnd(buffer: IntArray, start: Int, endExclusive: Int, maxCells: Int): Int {
        val hardEnd = minOf(endExclusive, start + maxCells)
        var index = start
        while (index < hardEnd) {
            if (buffer[index] == '\n'.code) return index
            index++
        }
        if (hardEnd >= endExclusive || buffer[hardEnd] == '\n'.code) return hardEnd

        index = hardEnd - 1
        while (index > start) {
            if (isDisplaySpace(buffer[index])) return index
            index--
        }
        return hardEnd
    }

    private fun bufferNextLineStart(buffer: IntArray, start: Int, end: Int, endExclusive: Int): Int {
        var next = if (end > start) end else start + 1
        if (next < endExclusive && buffer[next] == '\n'.code) next++
        while (next < endExclusive && isDisplaySpace(buffer[next])) next++
        return next
    }

    private fun isDisplaySpace(char: Char): Boolean {
        return char == '　' || char == ' '
    }

    private fun isDisplaySpace(codePoint: Int): Boolean {
        return codePoint == '　'.code || codePoint == ' '.code
    }

    private fun codePointDisplayChar(codePoint: Int): Char {
        return if (codePoint in 0..0xFFFF) codePoint.toChar() else '？'
    }

    private fun packCursor(line: Int, column: Int): Long {
        return (line.toLong() shl 32) or (column.toLong() and 0xFFFFFFFFL)
    }

    private fun toUpperCase(c: Char): Char {
        if (c in 'a'..'z') return (c.code - 32).toChar()
        if (c in '\uFF41'..'\uFF5A') return (c.code - 32).toChar()
        return c
    }
}
