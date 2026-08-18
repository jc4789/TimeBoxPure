package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import kotlin.math.roundToInt

/**
 * 9-band magic circle renderer (Rev 6 layout) with demoscene effects.
 *
 * Layers (outer to inner):
 *  1. Outer thin ring (white, static)
 *  2. Rune band (36 tangent mantra glyphs, drifting)
 *  3. Outer detail ticks (36 small, rotating slowly)
 *  4. 12-dot decoration ring (4 gold cardinals + 8 gray inter-cardinals, static)
 *  5. Outer timer beads (60, rotating slowly)
 *  6. 10-scripture ring (10 tangent hardcoded kanji, slower drift)
 *  7. Outer pentagram (5-pt double-line, primary + guide)
 *  8. 5 sector kanji (tangent, locked to pentagram angle)
 *  9. Octagram (2 squares +45° apart, independent CW/CCW)
 * 10. Inner timer beads (48, static, double-beat heartbeat)
 * 11. Yin-yang core (small, rotates fast)
 * 12. Yin-yang comet trail (6 dots, FABRIK-solved, fade alpha) — drawn AFTER core
 * 13. 5 inner cardinals (static, upright)
 * 14. Center timer text readout
 *
 * Rotation model: **continuous**, driven by `elapsedSeconds` (FrameClock.seconds(60f)).
 * Each layer has its own `ratePerSec` in degrees/second. The angle grows unbounded
 * over time but is reduced mod 360 at the draw site (inside FastMath). The user
 * sees non-repeating rotation as long as `elapsedSeconds` advances.
 *
 * Demoscene wiring:
 *  - 6 Wave oscillators + Perlin rune drift + FABRIK trail, all in [MagicCircleDemoscene]
 *  - Breathing effects (radius/scale modulations) are NOT applied per user feedback
 *  - Perlin rune drift IS applied (organic, subtle, no scale change)
 *  - FABRIK trail IS drawn (visible comet trail)
 *  - When `demoscene` is null OR `VisualsStateHolder.demosceneEffectsEnabled` is false,
 *    the magic circle renders as a static 13-layer layout (only the basic rotation).
 */
class NestedTimeboxInstrumentRenderer(private val renderer: ScaledProceduralRenderer) {
    companion object {
        // Authoritative graphics-local dimensions. The Android reference uses
        // block 3: 162 * 3 = 486px radius and 16 * 3 = 48px ornament glyphs.
        private const val GRAPHICS_SOURCE_CELL = 16f
        private const val GRAPHICS_SOURCE_RADIUS = 162f
        private const val GRAPHICS_REFERENCE_RADIUS = GRAPHICS_SOURCE_RADIUS * 3f
        private const val GRAPHICS_BOUNDARY_PAD = 2f
        private const val READOUT_MAX_WIDTH_CELLS = 16
        private const val GUIDE_ALPHA = 0x66
        private const val MECHANICAL_ALPHA = 0xAA
        private const val SCRIPTURE_ALPHA = 0x88
        private const val SOLID_ALPHA = 0xFF

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
        private val SECTOR_KANJI = charArrayOf('龍', '雀', '麟', '虎', '武')
        private val INNER_CARDINAL_KANJI = charArrayOf('龍', '雀', '麟', '虎', '武')

        // 12-dot decoration ring: 4 gold cardinals + 8 gray inter-cardinals.
        private val DECORATION_ANGLES: FloatArray = floatArrayOf(
            -90f, -60f, -30f, 0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f
        )
        private val DECORATION_IS_CARDINAL: BooleanArray = booleanArrayOf(
            true, false, false, true, false, false, true, false, false, true, false, false
        )

        // Continuous rotation rates in degrees per second. Tuned for "noticeable
        // but not frantic" — the core does one full turn every ~9s, the pentagram
        // every ~30s, the squares every 8-12s in opposite directions.
        private const val SCRIPTURE_DEG_PER_SEC = 4f
        private const val OUTER_RING_DEG_PER_SEC = 3f
        private const val PENTAGRAM_DEG_PER_SEC = 12f
        private const val SQUARE1_DEG_PER_SEC = -15f
        private const val SQUARE2_DEG_PER_SEC = 20f
        private const val CORE_DEG_PER_SEC = 40f
        // Yin-yang core radius — 3 graphics cells, snapped to the graphics grid.
        // The core occupies the 3-cell radius inside the 4-cell inner timer band.
        // 0.5f kept as the comet-trail lerp step (one step per ring rotation)
        // and as a glyph offset.
        // Comet tail: 4 dots in a line trailing the core's rotation angle.
        // Each dot's alpha is `trailBaseAlpha * (1 - linkIdx * 0.22)` where
        // `trailBaseAlpha` tracks the small timer's progress (1.0 → 0xCC,
        // 0.0 → ~0x14). The dots are at radii [coreR*1.05, coreR*1.5, coreR*1.95,
        // coreR*2.4] at angles [coreAngle, coreAngle-12°, coreAngle-24°,
        // coreAngle-36°] — a short arc trailing the core in the direction
        // opposite its rotation.
        private const val TRAIL_LINKS = 4
    }

    fun render(
        viewportLeft: Float,
        viewportTop: Float,
        viewportRight: Float,
        viewportBottom: Float,
        preferredCenterY: Float,
        outerProgress: Float,
        innerProgress: Float,
        elapsedSeconds: Float,
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
        strings: AppStrings,
        playAreaW: Float,
        demoscene: MagicCircleDemoscene? = null
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

        // Continuous rotation angles (degrees per second × elapsed seconds).
        // The angle grows unbounded; FastMath handles mod 360 internally.
        val scriptureAngle = elapsedSeconds * SCRIPTURE_DEG_PER_SEC - 90f
        val outerRingAngle = elapsedSeconds * OUTER_RING_DEG_PER_SEC - 90f
        val pentagramAngle = elapsedSeconds * PENTAGRAM_DEG_PER_SEC - 90f
        val square1Angle = elapsedSeconds * SQUARE1_DEG_PER_SEC - 90f
        val square2Angle = elapsedSeconds * SQUARE2_DEG_PER_SEC - 90f
        val coreAngle = elapsedSeconds * CORE_DEG_PER_SEC

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

        // 1. Outer thin ring (the "frame") at SCRIPTURE alpha (0x88).
        renderer.beginGraphics()
        try {
        renderer.setDrawAlpha(SCRIPTURE_ALPHA)
        renderer.drawAliasedCircle(centerX, centerY, outerR, magicPrimaryColorIndex, thin)

        // 2. Rune band: 36 tangent mantra glyphs. Each glyph drifts via Perlin
        //    (no breathing — just angle perturbation).
        renderer.setDrawAlpha(SCRIPTURE_ALPHA)
        var runeIdx = 0
        while (runeIdx < RUNE_BAND_COUNT) {
            val perlinOffset = demoscene?.runeDriftAngleOffset(runeIdx, elapsedSeconds) ?: 0f
            val angle = scriptureAngle + runeIdx * (360f / RUNE_BAND_COUNT) + perlinOffset
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

        // 3. Outer detail ticks (36 small, rotating slowly). The ticks span
        //    the band between the outer ring and the decoration ring.
        renderer.setDrawAlpha(GUIDE_ALPHA)
        renderer.drawActivePolarTickLoop(
            centerX, centerY, decorationR, outerR,
            OUTER_DETAIL_COUNT, OUTER_DETAIL_COUNT, outerRingAngle,
            magicSecondaryColorIndex, thin
        )

        // 4. 12-dot decoration ring: 4 gold cardinals + 8 gray inter-cardinals.
        var dotIdx = 0
        while (dotIdx < DECORATION_DOT_COUNT) {
            val isCardinal = DECORATION_IS_CARDINAL[dotIdx]
            val color = if (isCardinal) PaletteIndices.ACCENT_SECONDARY else PaletteIndices.BORDER
            val size = if (isCardinal) 2.5f * graphicsUnit else 1.5f * graphicsUnit
            renderer.setDrawAlpha(if (isCardinal) SOLID_ALPHA else SCRIPTURE_ALPHA)
            renderer.drawPolarDot(
                centerX, centerY, decorationR, DECORATION_ANGLES[dotIdx],
                size, color
            )
            dotIdx++
        }

        // 5. Outer timer beads (60, rotating slowly). The ring sits two
        //    graphics cells inside the boundary, clear of the rune band.
        renderer.setDrawAlpha(SOLID_ALPHA)
        val outerActive = (outerProgress.coerceIn(0f, 1f) * OUTER_BEAD_COUNT).toInt()
        renderer.drawActivePolarBeadLoop(
            centerX, centerY, outerTimerR,
            OUTER_BEAD_COUNT, outerActive, -90f,
                2.5f * graphicsUnit, outerActiveColorIndex
        )

        // 6. 10-scripture ring (10 hardcoded kanji, slightly faster than rune band).
        renderer.setDrawAlpha(SCRIPTURE_ALPHA)
        var scriptureIdx = 0
        while (scriptureIdx < SCRIPTURE_RING_COUNT) {
            val angle = scriptureAngle + scriptureIdx * (360f / SCRIPTURE_RING_COUNT) * 1.25f
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
        renderer.setDrawAlpha(SOLID_ALPHA)
        renderer.drawPolarStarLinks(
            centerX, centerY, pentagramR, PENTAGRAM_VERTEX_COUNT,
            PENTAGRAM_LINK_STEP, pentagramAngle, textFrameColorIndex, thin
        )

        // 8. 5 sector kanji (no sector swing — static radius, locked to pentagram).
        renderer.setDrawAlpha(SCRIPTURE_ALPHA)
        var sectorIdx = 0
        while (sectorIdx < SECTOR_KANJI_COUNT) {
            val angle = pentagramAngle + sectorIdx * (360f / SECTOR_KANJI_COUNT)
            renderer.drawPolarGlyph(
                SECTOR_KANJI[sectorIdx],
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

        // 9. Octagram: 2 squares +45° apart, rotating independently (CW + CCW).
        //     Both squares share the same color so the 8-pointed star reads
        //     as a single unified shape (octagon) rather than two overlapping
        //     squares.
        renderer.setDrawAlpha(MECHANICAL_ALPHA)
        renderer.drawRotatingPolygon(
            centerX, centerY, octagramR, SQUARE_VERTEX_COUNT,
            square1Angle, magicPrimaryColorIndex, thin
        )
        renderer.drawRotatingPolygon(
            centerX, centerY, octagramR, SQUARE_VERTEX_COUNT,
            square2Angle, magicPrimaryColorIndex, thin
        )

        // 10. Inner timer beads (48) — macro/session ring when nested.
        //     Slightly smaller than outer beads so the two rings stay distinct.
        if (showNestedMacro) {
            renderer.setDrawAlpha(SOLID_ALPHA)
            val innerActive = (innerProgress.coerceIn(0f, 1f) * INNER_BEAD_COUNT).toInt()
            renderer.drawActivePolarBeadLoop(
                centerX, centerY, innerBeadR,
                INNER_BEAD_COUNT, innerActive, -90f,
                2.0f * graphicsUnit, innerActiveColorIndex
            )
        }

        // 11. Yin-yang core (small, rotates fast, no wobble — static radius).
        renderer.setDrawAlpha(SOLID_ALPHA)
        drawSolidYinYang(
            centerX, centerY, coreR, coreAngle,
            magicPrimaryColorIndex, magicSecondaryColorIndex, thin
        )

        // 12. Comet tail — 4 dots in a line from the core's surface outward,
        //     trailing the core's current rotation angle. The dots fade from
        //     head to tail, and the overall brightness tracks the small
        //     timer's progress (bright when timer is full, dim when timer
        //     is almost expired). The tail "follows" the core as it rotates
        //     and "fades" as the timer counts down — the visual link between
        //     the nested timer and the magic circle.
        val trailR0 = coreR * 1.05f
        val trailRStep = coreR * 0.45f
        val trailBaseAlpha = (outerProgress.coerceIn(0f, 1f) * 220f).toInt()
        var trailLinkIdx = 0
        while (trailLinkIdx < TRAIL_LINKS) {
            val r = trailR0 + trailLinkIdx * trailRStep
            val trailAngle = coreAngle - trailLinkIdx * 12f
            val linkAlpha = (trailBaseAlpha * (1f - trailLinkIdx * 0.22f)).toInt().coerceIn(0x20, 0xFF)
            val linkSize = (2.5f - trailLinkIdx * 0.4f).coerceAtLeast(0.8f) * graphicsUnit
            val linkX = renderer.getPolarX(centerX, r, trailAngle)
            val linkY = renderer.getPolarY(centerY, r, trailAngle)
            renderer.setDrawAlpha(linkAlpha)
            renderer.drawAliasedFilledCircle(linkX, linkY, linkSize, textFrameColorIndex)
            trailLinkIdx++
        }
        renderer.setDrawAlpha(SOLID_ALPHA)

        // 13. 5 inner cardinal kanji (static, upright, four graphics cells inward).
        renderer.setDrawAlpha(SCRIPTURE_ALPHA)
        var innerCardinalIdx = 0
        while (innerCardinalIdx < INNER_CARDINAL_COUNT) {
            val angle = innerCardinalIdx * (360f / INNER_CARDINAL_COUNT) + 36f - 90f
            renderer.drawPolarGlyph(
                INNER_CARDINAL_KANJI[innerCardinalIdx],
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
            renderer.setDrawAlpha(SOLID_ALPHA)
            drawCenterReadout(
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

    private fun drawSolidYinYang(
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
                    drawStageLabelCentered(centerX, stageLabel, centerY - graphicsCell * 3.5f, secondary, maxTextWidth, true, graphicsGlyphBlock, graphicsCell)
                }
                drawTimeCentered(centerX, timeRemaining, centerY - graphicsCell * 1.5f, graphicsGlyphBlock * 2, primary)
                drawAlarmTimeCentered(centerX, midTimeRemaining, centerY + graphicsCell * 0.5f, primary, graphicsGlyphBlock)
                drawTimeCentered(centerX, bigTimeRemaining, centerY + graphicsCell * 2f, graphicsGlyphBlock, primary)
                drawStaticTextCenteredAtTop(centerX, strings.sessionLimitLabel, centerY + graphicsCell * 3f, secondary, maxTextWidth, graphicsGlyphBlock)
            } else {
                if (sequenceLength > 1 || activeMode == "calendar" || activeMode == "sequence") {
                    drawStageLabelCentered(centerX, stageLabel, centerY - graphicsCell * 2.5f, secondary, maxTextWidth, true, graphicsGlyphBlock, graphicsCell)
                }
                drawTimeCentered(centerX, timeRemaining, centerY - graphicsCell * 0.5f, graphicsGlyphBlock * 2, primary)
                drawTimeCentered(centerX, bigTimeRemaining, centerY + graphicsCell * 1.5f, graphicsGlyphBlock, primary)
                val label = if (activeMode == "dual-sequence") {
                    strings.blockLimitLabel
                } else {
                    strings.sessionLimitLabel
                }
                drawStaticTextCenteredAtTop(centerX, label, centerY + graphicsCell * 2.5f, secondary, maxTextWidth, graphicsGlyphBlock)
            }
            return
        }

        drawTimeCentered(centerX, timeRemaining, centerY - graphicsCell * 0.5f, graphicsGlyphBlock * 2, primary)
        val isSequence = activeMode == "sequence" || activeMode == "calendar"
        if (isSequence && sequenceLength > 1) {
            drawStageLabelCentered(centerX, stageLabel, centerY + graphicsCell * 1.5f, secondary, maxTextWidth, false, graphicsGlyphBlock, graphicsCell)
        } else if (activeMode != "sequence") {
            val label = if (isBreak) strings.unwindingLabel else strings.focusingLabel
            drawStaticTextCenteredAtTop(centerX, label, centerY + graphicsCell, secondary, maxTextWidth, graphicsGlyphBlock)
        }
    }

    private fun drawStaticTextCenteredAtTop(
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

    private fun drawTimeCentered(centerX: Float, seconds: Int, centerY: Float, scale: Int, colorIndex: Int) {
        val safeSeconds = maxOf(0, seconds)
        val minutes = safeSeconds / 60
        val remainder = safeSeconds % 60
        val cellWidth = ScaledProceduralRenderer.measureTextHeight(scale)
        val startX = centerX - cellWidth * 2.5f
        val startY = centerY - cellWidth / 2f
        drawGlyph(FULLWIDTH_DIGITS[(minutes / 10) % 10], startX, startY, colorIndex, scale)
        drawGlyph(FULLWIDTH_DIGITS[minutes % 10], startX + cellWidth, startY, colorIndex, scale)
        drawGlyph('：', startX + cellWidth * 2f, startY, colorIndex, scale)
        drawGlyph(FULLWIDTH_DIGITS[remainder / 10], startX + cellWidth * 3f, startY, colorIndex, scale)
        drawGlyph(FULLWIDTH_DIGITS[remainder % 10], startX + cellWidth * 4f, startY, colorIndex, scale)
    }

    private fun drawAlarmTimeCentered(centerX: Float, seconds: Int, centerY: Float, colorIndex: Int, scale: Int) {
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
            drawGlyph(prefix[index], drawX, drawY, colorIndex, scale)
            drawX += cellWidth
            index++
        }
        drawGlyph(FULLWIDTH_DIGITS[(minutes / 10) % 10], drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(FULLWIDTH_DIGITS[minutes % 10], drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph('：', drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(FULLWIDTH_DIGITS[remainder / 10], drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph(FULLWIDTH_DIGITS[remainder % 10], drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph('　', drawX, drawY, colorIndex, scale); drawX += cellWidth
        drawGlyph('］', drawX, drawY, colorIndex, scale)
    }

    private fun drawGlyph(char: Char, x: Float, y: Float, colorIndex: Int, scale: Int = 1) {
        renderer.drawGlyph(char, x, y, colorIndex, scale = scale)
    }
}
