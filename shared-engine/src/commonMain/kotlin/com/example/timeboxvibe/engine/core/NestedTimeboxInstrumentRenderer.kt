package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import kotlin.math.roundToInt

/**
 * Deterministic 14-layer magic-circle instrument renderer.
 *
 * Layers (outer to inner):
 *  1. Outer thin ring (white, static)
 *  2. Rune band (36 tangent mantra glyphs)
 *  3. Outer detail ticks (36 small ticks)
 *  4. 12-dot decoration ring (4 gold cardinals + 8 gray inter-cardinals, static)
 *  5. Outer timer beads (60)
 *  6. 10-scripture ring (10 tangent hardcoded kanji)
 *  7. Outer pentagram (5-point star)
 *  8. 5 sector kanji (tangent, locked to pentagram angle)
 *  9. Octagram (2 squares 45° apart)
 * 10. Inner timer beads (48)
 * 11. Yin-yang core
 * 12. Four-dot core ornament
 * 13. 5 inner cardinals (static, upright)
 * 14. Center timer text readout
 * All geometry, rotation phase, and rasterization are owned here. There is no
 * external clock, frame counter, alpha simulation, or effect manager.
 */
class NestedTimeboxInstrumentRenderer {
    companion object {
        // Authoritative graphics-local dimensions. The Android reference uses
        // block 3: 162 * 3 = 486px radius and 16 * 3 = 48px ornament glyphs.
        private const val GRAPHICS_SOURCE_CELL = 16f
        private const val GRAPHICS_SOURCE_RADIUS = 162f
        private const val GRAPHICS_REFERENCE_RADIUS = GRAPHICS_SOURCE_RADIUS * 3f
        private const val GRAPHICS_BOUNDARY_PAD = 2f
        private const val READOUT_MAX_WIDTH_CELLS = 16
        private const val SOLID_ALPHA = 0xFF

        private const val FULL_ROTATION_DEGREES = 360f
        private const val SCRIPTURE_DEGREES_PER_SECOND = 4f
        private const val OUTER_DETAIL_DEGREES_PER_SECOND = 3f
        private const val PENTAGRAM_DEGREES_PER_SECOND = 12f
        private const val SQUARE_ONE_DEGREES_PER_SECOND = -15f
        private const val SQUARE_TWO_DEGREES_PER_SECOND = 20f
        private const val CORE_DEGREES_PER_SECOND = 40f

        // Counts
        private const val OUTER_BEAD_COUNT = 60
        private const val INNER_BEAD_COUNT = 48
        private const val RUNE_BAND_COUNT = 36
        private const val SCRIPTURE_RING_COUNT = 10
        private const val SECTOR_KANJI_COUNT = 5
        private const val INNER_CARDINAL_COUNT = 5
        private const val OUTER_DETAIL_COUNT = 36
        private const val DECORATION_DOT_COUNT = 12
        private const val SQUARE_VERTEX_COUNT = 4
        private const val PENTAGRAM_VERTEX_COUNT = 5
        private const val PENTAGRAM_LINK_STEP = 2

        // 14 hardcoded kanji from ShinonomeFont.GLYPHS, used for the rune band mantra
        // and the scripture ring. Loop the string to fill the requested count.
        private const val MANTRA_STRING = "時分秒東雲霊魔音弾幕撃程郷"
        private const val SCRIPTURE_KANJI_SEQUENCE = "時分秒東雲霊魔音弾幕"

        // 五方 sector kanji: N=龍, E=雀, SE=麟, SW=虎, W=武.
        // The engine falls back to '?' if a glyph has not been added to ShinonomeFont yet.
        private val CARDINAL_KANJI = charArrayOf('龍', '雀', '麟', '虎', '武')

        // 12-dot decoration ring: 4 gold cardinals + 8 gray inter-cardinals.
        private val DECORATION_ANGLES: FloatArray = floatArrayOf(
            -90f, -60f, -30f, 0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f
        )
        private val DECORATION_IS_CARDINAL: BooleanArray = booleanArrayOf(
            true, false, false, true, false, false, true, false, false, true, false, false
        )

        private const val TRAIL_LINKS = 4
    }

    private var scriptureAngleDegrees = -90f
    private var outerDetailAngleDegrees = -90f
    private var pentagramAngleDegrees = -90f
    private var squareOneAngleDegrees = -90f
    private var squareTwoAngleDegrees = -45f
    private var coreAngleDegrees = 0f

    fun update(dt: Float) {
        scriptureAngleDegrees = advanceAngle(
            scriptureAngleDegrees,
            SCRIPTURE_DEGREES_PER_SECOND * dt
        )
        outerDetailAngleDegrees = advanceAngle(
            outerDetailAngleDegrees,
            OUTER_DETAIL_DEGREES_PER_SECOND * dt
        )
        pentagramAngleDegrees = advanceAngle(
            pentagramAngleDegrees,
            PENTAGRAM_DEGREES_PER_SECOND * dt
        )
        squareOneAngleDegrees = advanceAngle(
            squareOneAngleDegrees,
            SQUARE_ONE_DEGREES_PER_SECOND * dt
        )
        squareTwoAngleDegrees = advanceAngle(
            squareTwoAngleDegrees,
            SQUARE_TWO_DEGREES_PER_SECOND * dt
        )
        coreAngleDegrees = advanceAngle(
            coreAngleDegrees,
            CORE_DEGREES_PER_SECOND * dt
        )
    }

    fun render(
        renderer: ScaledProceduralRenderer,
        viewportLeft: Float,
        viewportTop: Float,
        viewportRight: Float,
        viewportBottom: Float,
        preferredCenterY: Float,
        outerProgress: Float,
        innerProgress: Float,
        isDual: Boolean,
        outerActiveColorIndex: Int,
        innerActiveColorIndex: Int,
        magicPrimaryColorIndex: Int,
        magicSecondaryColorIndex: Int,
        textFrameColorIndex: Int,
        timeRemaining: Int,
        stageLabel: String,
        midTimeRemaining: Int,
        bigTimeRemaining: Int,
        bigTotalDuration: Int = 0,
        activeMode: String,
        isBreak: Boolean,
        sequenceLength: Int,
        strings: AppStrings
    ): Float {
        // Nested readouts + inner bead ring whenever a macro tier is present
        // (dual modes, or calendar/classic pomodoro with remaining-block total).
        // Does not alter TimerEngine.isDual — display-only.
        val showNestedMacro = isDual || bigTotalDuration > 0
        val outputLeft = renderer.outputX(viewportLeft)
        val outputTop = renderer.outputY(viewportTop)
        val outputRight = renderer.outputX(viewportRight)
        val outputBottom = renderer.outputY(maxOf(viewportTop, viewportBottom))
        val outputPreferredCenterY = renderer.outputY(preferredCenterY)
        val outputShortAxis = minOf(
            maxOf(1f, outputRight - outputLeft),
            maxOf(1f, outputBottom - outputTop)
        )
        val graphicsRadius = minOf(
            GRAPHICS_REFERENCE_RADIUS,
            maxOf(1f, outputShortAxis * 0.5f - GRAPHICS_BOUNDARY_PAD)
        )
        val graphicsUnit = graphicsRadius / GRAPHICS_SOURCE_RADIUS
        val graphicsGlyphBlock = maxOf(1, graphicsUnit.roundToInt())
        val graphicsCell = GRAPHICS_SOURCE_CELL * graphicsUnit
        val thin = maxOf(1f, graphicsUnit)
        val centerX = (outputLeft + outputRight) * 0.5f
        val minimumCenterY = outputTop + graphicsRadius + GRAPHICS_BOUNDARY_PAD
        val maximumCenterY = outputBottom - graphicsRadius - GRAPHICS_BOUNDARY_PAD
        val centerY = if (maximumCenterY >= minimumCenterY) {
            outputPreferredCenterY.coerceIn(minimumCenterY, maximumCenterY)
        } else {
            (outputTop + outputBottom) * 0.5f
        }
        val baseRadius = graphicsRadius

        val coreR = graphicsCell * 3f

        // Radii are owned by the graphic's own integer pixel block, not the UI glyph cell.
        // The outer ring is the boundary; every other band is a whole or half
        // graphics cell inside the
        // boundary. Nothing extends past baseRadius — the 魔法陣 contains
        // everything.
        val outerR = baseRadius
        val decorationR = baseRadius - graphicsCell * 0.5f
        val runeBandR = baseRadius - graphicsCell
        val outerTimerR = baseRadius - graphicsCell * 2f
        val scriptureR = baseRadius - graphicsCell * 2.5f
        val pentagramR = baseRadius - graphicsCell * 2f
        val sectorKanjiR = baseRadius - graphicsCell * 2f
        val octagramR = baseRadius - graphicsCell * 3f
        val innerBeadR = graphicsCell * 4f
        val innerCardinalR = baseRadius - graphicsCell * 4f

        // Indexed output has no partial alpha. Enable drawing once for the
        // complete instrument and select all visual differences by palette index.
        renderer.beginGraphics()
        try {
        renderer.setDrawAlpha(SOLID_ALPHA)
        renderer.drawAliasedCircle(centerX, centerY, outerR, magicPrimaryColorIndex, thin)

        // 2. Rune band: 36 tangent mantra glyphs.
        var runeIdx = 0
        while (runeIdx < RUNE_BAND_COUNT) {
            val angle = scriptureAngleDegrees + runeIdx * (360f / RUNE_BAND_COUNT)
            renderer.drawPolarGlyph(
                MANTRA_STRING[runeIdx % MANTRA_STRING.length],
                centerX,
                centerY,
                runeBandR,
                angle,
                magicSecondaryColorIndex,
                scale = graphicsGlyphBlock,
                tangent = true
            )
            runeIdx++
        }

        // 3. Outer detail ticks. The ticks span
        //    the band between the outer ring and the decoration ring.
        renderer.drawActivePolarTickLoop(
            centerX, centerY, decorationR, outerR,
            OUTER_DETAIL_COUNT, OUTER_DETAIL_COUNT, outerDetailAngleDegrees,
            magicSecondaryColorIndex, thin
        )

        // 4. 12-dot decoration ring: 4 gold cardinals + 8 gray inter-cardinals.
        var dotIdx = 0
        while (dotIdx < DECORATION_DOT_COUNT) {
            val isCardinal = DECORATION_IS_CARDINAL[dotIdx]
            val color = if (isCardinal) PaletteIndices.ACCENT_SECONDARY else PaletteIndices.BORDER
            val size = if (isCardinal) 2.5f * graphicsUnit else 1.5f * graphicsUnit
            renderer.drawPolarDot(
                centerX, centerY, decorationR, DECORATION_ANGLES[dotIdx],
                size, color
            )
            dotIdx++
        }

        // 5. Outer timer beads. The ring sits two
        //    graphics cells inside the boundary, clear of the rune band.
        val outerActive = (outerProgress.coerceIn(0f, 1f) * OUTER_BEAD_COUNT).toInt()
        renderer.drawActivePolarBeadLoop(
            centerX, centerY, outerTimerR,
            OUTER_BEAD_COUNT, outerActive, -90f,
                2.5f * graphicsUnit, outerActiveColorIndex
        )

        // 6. 10-scripture ring.
        var scriptureIdx = 0
        while (scriptureIdx < SCRIPTURE_RING_COUNT) {
            val angle = scriptureAngleDegrees + scriptureIdx * (360f / SCRIPTURE_RING_COUNT) * 1.25f
            renderer.drawPolarGlyph(
                SCRIPTURE_KANJI_SEQUENCE[scriptureIdx % SCRIPTURE_KANJI_SEQUENCE.length],
                centerX,
                centerY,
                scriptureR,
                angle,
                magicPrimaryColorIndex,
                scale = graphicsGlyphBlock,
                tangent = true
            )
            scriptureIdx++
        }

        // 7. Outer pentagram: 5-point star, single line (the guide line was
        //    removed — it was reading as a "ghost" pentagram inside the main one).
        renderer.drawPolarStarLinks(
            centerX, centerY, pentagramR, PENTAGRAM_VERTEX_COUNT,
            PENTAGRAM_LINK_STEP, pentagramAngleDegrees, textFrameColorIndex, thin
        )

        // 8. 5 sector kanji (no sector swing — static radius, locked to pentagram).
        var sectorIdx = 0
        while (sectorIdx < SECTOR_KANJI_COUNT) {
            val angle = pentagramAngleDegrees + sectorIdx * (360f / SECTOR_KANJI_COUNT)
            renderer.drawPolarGlyph(
                CARDINAL_KANJI[sectorIdx],
                centerX,
                centerY,
                sectorKanjiR,
                angle,
                magicPrimaryColorIndex,
                scale = graphicsGlyphBlock,
                tangent = true
            )
            sectorIdx++
        }

        // 9. Octagram: two squares exactly 45 degrees apart.
        renderer.drawRotatingPolygon(
            centerX, centerY, octagramR, SQUARE_VERTEX_COUNT,
            squareOneAngleDegrees, magicPrimaryColorIndex, thin
        )
        renderer.drawRotatingPolygon(
            centerX, centerY, octagramR, SQUARE_VERTEX_COUNT,
            squareTwoAngleDegrees, magicPrimaryColorIndex, thin
        )

        // 10. Inner timer beads (48) — macro/session ring when nested.
        //     Slightly smaller than outer beads so the two rings stay distinct.
        if (showNestedMacro) {
            val innerActive = (innerProgress.coerceIn(0f, 1f) * INNER_BEAD_COUNT).toInt()
            renderer.drawActivePolarBeadLoop(
                centerX, centerY, innerBeadR,
                INNER_BEAD_COUNT, innerActive, -90f,
                2.0f * graphicsUnit, innerActiveColorIndex
            )
        }

        // 11. Yin-yang core.
        drawSolidYinYang(
            renderer, centerX, centerY, coreR, coreAngleDegrees,
            magicPrimaryColorIndex, magicSecondaryColorIndex, thin
        )

        // 12. Four-dot core ornament. Size, not nonexistent alpha blending,
        //     distinguishes the links in the indexed framebuffer.
        val trailR0 = coreR * 1.05f
        val trailRStep = coreR * 0.45f
        var trailLinkIdx = 0
        while (trailLinkIdx < TRAIL_LINKS) {
            val r = trailR0 + trailLinkIdx * trailRStep
            val trailAngle = coreAngleDegrees - trailLinkIdx * 12f
            val linkSize = (2.5f - trailLinkIdx * 0.4f).coerceAtLeast(0.8f) * graphicsUnit
            val linkX = renderer.getPolarX(centerX, r, trailAngle)
            val linkY = renderer.getPolarY(centerY, r, trailAngle)
            renderer.drawAliasedFilledCircle(linkX, linkY, linkSize, textFrameColorIndex)
            trailLinkIdx++
        }

        // 13. 5 inner cardinal kanji (static, upright, four graphics cells inward).
        var innerCardinalIdx = 0
        while (innerCardinalIdx < INNER_CARDINAL_COUNT) {
            val angle = innerCardinalIdx * (360f / INNER_CARDINAL_COUNT) + 36f - 90f
            renderer.drawPolarGlyph(
                CARDINAL_KANJI[innerCardinalIdx],
                centerX,
                centerY,
                innerCardinalR,
                angle,
                magicPrimaryColorIndex,
                scale = graphicsGlyphBlock,
                tangent = false
            )
            innerCardinalIdx++
        }

            // The center readout is part of the instrument graphic. It uses the
            // same graphics-local integer block as every ring and ornament.
            drawCenterReadout(
                renderer = renderer,
                centerX = centerX,
                centerY = centerY,
                graphicsCell = graphicsCell,
                graphicsGlyphBlock = graphicsGlyphBlock,
                showNestedMacro = showNestedMacro,
                timeRemaining = timeRemaining,
                stageLabel = stageLabel,
                midTimeRemaining = midTimeRemaining,
                bigTimeRemaining = bigTimeRemaining,
                activeMode = activeMode,
                isBreak = isBreak,
                sequenceLength = sequenceLength,
                strings = strings,
                viewportWidth = outputRight - outputLeft
            )
        } finally {
            renderer.endGraphics()
        }
        return UiRasterGrid.logicalY(centerY + graphicsRadius + thin * 0.5f)
    }

    private fun advanceAngle(angleDegrees: Float, deltaDegrees: Float): Float {
        var next = angleDegrees + deltaDegrees
        while (next >= FULL_ROTATION_DEGREES) next -= FULL_ROTATION_DEGREES
        while (next < 0f) next += FULL_ROTATION_DEGREES
        return next
    }

    private fun drawSolidYinYang(
        renderer: ScaledProceduralRenderer,
        centerX: Float,
        centerY: Float,
        coreR: Float,
        coreAngleDegrees: Float,
        primaryColorIndex: Int,
        secondaryColorIndex: Int,
        strokeWidth: Float
    ) {
        val coreAngleIndex = FastMath.degreesToIdx(coreAngleDegrees)
        val cosRotation = FastMath.fastCos(coreAngleIndex)
        val sinRotation = FastMath.fastSin(coreAngleIndex)
        val lobeR = coreR / 2f
        val dotR = coreR / 8f
        val coreRadiusInt = coreR.roundToInt()
        val centerXInt = centerX.roundToInt()
        val centerYInt = centerY.roundToInt()
        val coreRadiusSq = coreR * coreR
        val lobeRadiusSq = lobeR * lobeR
        val dotRadiusSq = dotR * dotR
        val canvas = renderer.canvas
        val canvasWidth = canvas.width.toInt()
        val canvasHeight = canvas.height.toInt()

        var dy = -coreRadiusInt
        while (dy <= coreRadiusInt) {
            val pixelY = centerYInt + dy
            if (pixelY >= 0 && pixelY < canvasHeight) {
                var dx = -coreRadiusInt
                while (dx <= coreRadiusInt) {
                    val pixelX = centerXInt + dx
                    if (pixelX >= 0 && pixelX < canvasWidth) {
                        val distanceSq = dx * dx + dy * dy
                        if (distanceSq.toFloat() <= coreRadiusSq) {
                            val localX = dx * cosRotation + dy * sinRotation
                            val localY = -dx * sinRotation + dy * cosRotation
                            val topDy = localY + lobeR
                            val bottomDy = localY - lobeR
                            val topDistanceSq = localX * localX + topDy * topDy
                            val bottomDistanceSq = localX * localX + bottomDy * bottomDy
                            val usePrimary = when {
                                topDistanceSq <= lobeRadiusSq -> topDistanceSq > dotRadiusSq
                                bottomDistanceSq <= lobeRadiusSq -> bottomDistanceSq <= dotRadiusSq
                                else -> localX < 0f
                            }
                            canvas.setPixel(
                                pixelX.toFloat(),
                                pixelY.toFloat(),
                                if (usePrimary) primaryColorIndex else secondaryColorIndex
                            )
                        }
                    }
                    dx++
                }
            }
            dy++
        }

        val topX = centerX + lobeR * sinRotation
        val topY = centerY - lobeR * cosRotation
        val bottomX = centerX - lobeR * sinRotation
        val bottomY = centerY + lobeR * cosRotation

        renderer.drawAliasedCircle(centerX, centerY, coreR, primaryColorIndex, strokeWidth)
        renderer.drawRotatedBresenhamHalfCircle(
            topX, topY, lobeR, coreAngleIndex, true, primaryColorIndex, strokeWidth
        )
        renderer.drawRotatedBresenhamHalfCircle(
            bottomX, bottomY, lobeR, coreAngleIndex, false, primaryColorIndex, strokeWidth
        )
    }

    private fun drawCenterReadout(
        renderer: ScaledProceduralRenderer,
        centerX: Float,
        centerY: Float,
        graphicsCell: Float,
        graphicsGlyphBlock: Int,
        showNestedMacro: Boolean,
        timeRemaining: Int,
        stageLabel: String,
        midTimeRemaining: Int,
        bigTimeRemaining: Int,
        activeMode: String,
        isBreak: Boolean,
        sequenceLength: Int,
        strings: AppStrings,
        viewportWidth: Float
    ) {
        val primary = PaletteIndices.TEXT_PRIMARY
        val secondary = PaletteIndices.TEXT_SECONDARY
        val maxTextWidth = maxOf(
            graphicsCell,
            minOf(
                viewportWidth - graphicsCell * 1.5f,
                graphicsCell * READOUT_MAX_WIDTH_CELLS
            )
        )

        // Nested: micro stage (large) + macro block remaining (small) + labels.
        if (showNestedMacro) {
            if (activeMode == "dual.5") {
                if (sequenceLength > 1) {
                    drawStageLabelCentered(renderer, centerX, stageLabel, centerY - graphicsCell * 3.5f, secondary, maxTextWidth, true, graphicsGlyphBlock, graphicsCell)
                }
                drawTimeCentered(renderer, centerX, timeRemaining, centerY - graphicsCell * 1.5f, graphicsGlyphBlock * 2, primary)
                drawAlarmTimeCentered(renderer, centerX, midTimeRemaining, centerY + graphicsCell * 0.5f, primary, graphicsGlyphBlock)
                drawTimeCentered(renderer, centerX, bigTimeRemaining, centerY + graphicsCell * 2f, graphicsGlyphBlock, primary)
                drawStaticTextCenteredAtTop(renderer, centerX, strings.sessionLimitLabel, centerY + graphicsCell * 3f, secondary, maxTextWidth, graphicsGlyphBlock)
            } else {
                if (sequenceLength > 1 || activeMode == "calendar" || activeMode == "sequence") {
                    drawStageLabelCentered(renderer, centerX, stageLabel, centerY - graphicsCell * 2.5f, secondary, maxTextWidth, true, graphicsGlyphBlock, graphicsCell)
                }
                drawTimeCentered(renderer, centerX, timeRemaining, centerY - graphicsCell * 0.5f, graphicsGlyphBlock * 2, primary)
                drawTimeCentered(renderer, centerX, bigTimeRemaining, centerY + graphicsCell * 1.5f, graphicsGlyphBlock, primary)
                val label = if (activeMode == "dual-sequence") {
                    strings.blockLimitLabel
                } else {
                    strings.sessionLimitLabel
                }
                drawStaticTextCenteredAtTop(renderer, centerX, label, centerY + graphicsCell * 2.5f, secondary, maxTextWidth, graphicsGlyphBlock)
            }
            return
        }

        drawTimeCentered(renderer, centerX, timeRemaining, centerY - graphicsCell * 0.5f, graphicsGlyphBlock * 2, primary)
        val isSequence = activeMode == "sequence" || activeMode == "calendar"
        if (isSequence && sequenceLength > 1) {
            drawStageLabelCentered(renderer, centerX, stageLabel, centerY + graphicsCell * 1.5f, secondary, maxTextWidth, false, graphicsGlyphBlock, graphicsCell)
        } else if (activeMode != "sequence") {
            val label = if (isBreak) strings.unwindingLabel else strings.focusingLabel
            drawStaticTextCenteredAtTop(renderer, centerX, label, centerY + graphicsCell, secondary, maxTextWidth, graphicsGlyphBlock)
        }
    }

    private fun drawStaticTextCenteredAtTop(
        renderer: ScaledProceduralRenderer,
        centerX: Float,
        text: String,
        topY: Float,
        colorIndex: Int,
        maxWidth: Float,
        scale: Int
    ) {
        ProceduralTextRenderer.drawWrapped(
            renderer,
            text,
            centerX - maxWidth / 2f,
            topY,
            maxWidth,
            colorIndex,
            scale = scale,
            alignment = ProceduralTextRenderer.ALIGN_CENTER
        )
    }

    private fun drawStageLabelCentered(
        renderer: ScaledProceduralRenderer,
        centerX: Float,
        text: String,
        centerY: Float,
        colorIndex: Int,
        maxWidth: Float,
        placeAbove: Boolean,
        scale: Int,
        graphicsCell: Float
    ) {
        if (text.isEmpty()) return
        val textHeight = ProceduralTextRenderer.measureWrappedHeight(text, maxWidth, scale)
        val startY = if (placeAbove) {
            centerY + graphicsCell * 0.5f - textHeight
        } else {
            centerY - graphicsCell * 0.5f
        }
        ProceduralTextRenderer.drawWrapped(
            renderer,
            text,
            centerX - maxWidth / 2f,
            startY,
            maxWidth,
            colorIndex,
            scale = scale,
            alignment = ProceduralTextRenderer.ALIGN_CENTER
        )
    }

    private fun drawTimeCentered(renderer: ScaledProceduralRenderer, centerX: Float, seconds: Int, centerY: Float, scale: Int, colorIndex: Int) {
        val safeSeconds = maxOf(0, seconds)
        val minutes = safeSeconds / 60
        val remainder = safeSeconds % 60
        val cellWidth = ScaledProceduralRenderer.measureTextHeight(scale)
        val startX = centerX - cellWidth * 2.5f
        val startY = centerY - cellWidth / 2f
        drawGlyph(renderer, FULLWIDTH_DIGITS[(minutes / 10) % 10], startX, startY, colorIndex, scale)
        drawGlyph(renderer, FULLWIDTH_DIGITS[minutes % 10], startX + cellWidth, startY, colorIndex, scale)
        drawGlyph(renderer, '：', startX + cellWidth * 2f, startY, colorIndex, scale)
        drawGlyph(renderer, FULLWIDTH_DIGITS[remainder / 10], startX + cellWidth * 3f, startY, colorIndex, scale)
        drawGlyph(renderer, FULLWIDTH_DIGITS[remainder % 10], startX + cellWidth * 4f, startY, colorIndex, scale)
    }

    private fun drawAlarmTimeCentered(renderer: ScaledProceduralRenderer, centerX: Float, seconds: Int, centerY: Float, colorIndex: Int, scale: Int) {
        val safeSeconds = maxOf(0, seconds)
        val minutes = safeSeconds / 60
        val remainder = safeSeconds % 60
        val prefix = "［　ＡＬＡＲＭ：　"
        val totalCells = prefix.length + 7
        val cellWidth = ScaledProceduralRenderer.measureTextHeight(scale)
        var drawX = centerX - totalCells * cellWidth / 2f
        val drawY = centerY - cellWidth / 2f
        var index = 0
        while (index < prefix.length) {
            drawGlyph(renderer, prefix[index], drawX, drawY, colorIndex, scale)
            drawX += cellWidth
            index++
        }
        drawGlyph(renderer, FULLWIDTH_DIGITS[(minutes / 10) % 10], drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(renderer, FULLWIDTH_DIGITS[minutes % 10], drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(renderer, '：', drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(renderer, FULLWIDTH_DIGITS[remainder / 10], drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(renderer, FULLWIDTH_DIGITS[remainder % 10], drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(renderer, '　', drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(renderer, '］', drawX, drawY, colorIndex, scale)
    }

    private fun drawGlyph(renderer: ScaledProceduralRenderer, char: Char, x: Float, y: Float, colorIndex: Int, scale: Int = 1) {
        renderer.drawGlyph(char, x, y, colorIndex, scale = scale)
    }
}
