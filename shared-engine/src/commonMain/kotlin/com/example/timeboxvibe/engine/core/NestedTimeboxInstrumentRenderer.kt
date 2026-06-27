package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
        private const val U = 16
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
        // Yin-yang core radius — 3 glyph cells, snapped to the 16x16 grid.
        // The core occupies the 3-cell radius inside the 4-cell inner timer band.
        // This is a clean U-multiplier; no arbitrary ratio.
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
        centerX: Float,
        centerY: Float,
        baseRadius: Float,
        quietRadius: Float,
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
        activeMode: String,
        isBreak: Boolean,
        sequenceLength: Int,
        strings: AppStrings,
        playAreaW: Float,
        demoscene: MagicCircleDemoscene? = null
    ) {
        val thin = renderer.canvas.density

        // Continuous rotation angles (degrees per second × elapsed seconds).
        // The angle grows unbounded; FastMath handles mod 360 internally.
        val scriptureAngle = elapsedSeconds * SCRIPTURE_DEG_PER_SEC - 90f
        val outerRingAngle = elapsedSeconds * OUTER_RING_DEG_PER_SEC - 90f
        val pentagramAngle = elapsedSeconds * PENTAGRAM_DEG_PER_SEC - 90f
        val square1Angle = elapsedSeconds * SQUARE1_DEG_PER_SEC - 90f
        val square2Angle = elapsedSeconds * SQUARE2_DEG_PER_SEC - 90f
        val coreAngle = elapsedSeconds * CORE_DEG_PER_SEC

        val coreR = U * 3f
        val coreX = centerX + coreR * cosDeg(coreAngle)
        val coreY = centerY + coreR * sinDeg(coreAngle)

        // Radii — all snapped to the 16x16 grid: baseRadius, baseRadius - U*N,
        // or U*N. The outer ring is the boundary (U*0 offset from baseRadius);
        // every other band is a whole or half number of U cells inside the
        // boundary. Nothing extends past baseRadius — the 魔法陣 contains
        // everything.
        val outerR = baseRadius
        val decorationR = baseRadius - U * 0.5f
        val runeBandR = baseRadius - U * 1f
        val outerTimerR = baseRadius - U * 2f
        val scriptureR = baseRadius - U * 2.5f
        val pentagramR = baseRadius - U * 2f
        val sectorKanjiR = baseRadius - U * 2f
        val octagramR = baseRadius - U * 3f
        val innerBeadR = U * 4f
        val innerRingR = baseRadius - U * 4f
        val innerCardinalR = baseRadius - U * 4f

        // 1. Outer thin ring (the "frame") — red, pulled in 0.25U, at SCRIPTURE
        //    alpha (0x88) so it's visible without dominating.
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
                tangent = true
            )
            runeIdx++
        }

        // 3. Outer detail ticks (36 small, rotating slowly). The ticks
        //    span the band between the outer ring and the decoration ring
        //    (both at U * 0.5f inside the boundary).
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
            val size = if (isCardinal) 2.5f else 1.5f
            renderer.setDrawAlpha(if (isCardinal) SOLID_ALPHA else SCRIPTURE_ALPHA)
            renderer.drawPolarDot(
                centerX, centerY, decorationR, DECORATION_ANGLES[dotIdx],
                size, color
            )
            dotIdx++
        }

        // 5. Outer timer beads (60, rotating slowly). The ring sits at
        //    baseRadius - U*2, a full cell inside the rune kanji band
        //    (so the kanji no longer cover the beads).
        renderer.setDrawAlpha(SOLID_ALPHA)
        val outerActive = (outerProgress.coerceIn(0f, 1f) * OUTER_BEAD_COUNT).toInt()
        renderer.drawActivePolarBeadLoop(
            centerX, centerY, outerTimerR,
            OUTER_BEAD_COUNT, outerActive, -90f,
            2.5f, outerActiveColorIndex
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

        // 10. Inner timer beads (48, dual mode only, static start). Size
        //     Size 2.0px (slightly smaller than the 2.5px outer beads) and
        //     a different color (ACCENT_SUCCESS = jade green, not the off-white
        //     TEXT_SECONDARY) so the two bead rings are clearly distinct.
        if (isDual) {
            renderer.setDrawAlpha(SOLID_ALPHA)
            val innerActive = (innerProgress.coerceIn(0f, 1f) * INNER_BEAD_COUNT).toInt()
            renderer.drawActivePolarBeadLoop(
                centerX, centerY, innerBeadR,
                INNER_BEAD_COUNT, innerActive, -90f,
                2.0f, innerActiveColorIndex
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
            val linkSize = (2.5f - trailLinkIdx * 0.4f).coerceAtLeast(0.8f)
            val linkX = centerX + r * cosDeg(trailAngle)
            val linkY = centerY + r * sinDeg(trailAngle)
            renderer.setDrawAlpha(linkAlpha)
            renderer.drawAliasedFilledCircle(linkX, linkY, linkSize, textFrameColorIndex)
            trailLinkIdx++
        }
        renderer.setDrawAlpha(SOLID_ALPHA)

        // 13. 5 inner cardinal kanji (static, upright, at innerRingR = 4 cells).
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
                tangent = false
            )
            innerCardinalIdx++
        }

        // 14. Center timer text readout (drawn last so it sits on top of everything).
        renderer.setDrawAlpha(SOLID_ALPHA)
        drawCenterReadout(
            centerX, centerY, quietRadius, isDual, timeRemaining, stageLabel,
            midTimeRemaining, bigTimeRemaining, activeMode, isBreak, sequenceLength,
            strings, playAreaW
        )
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
        quietRadius: Float,
        isDual: Boolean,
        timeRemaining: Int,
        stageLabel: String,
        midTimeRemaining: Int,
        bigTimeRemaining: Int,
        activeMode: String,
        isBreak: Boolean,
        sequenceLength: Int,
        strings: AppStrings,
        playAreaW: Float
    ) {
        val primary = PaletteIndices.TEXT_PRIMARY
        val secondary = PaletteIndices.TEXT_SECONDARY
        val maxTextWidth = minOf(playAreaW - U * 1.5f, quietRadius * 2f - U)

        if (isDual) {
            if (activeMode == "dual.5") {
                if (sequenceLength > 1) {
                    drawStageLabelCentered(centerX, stageLabel, centerY - U * 3.5f, secondary, maxTextWidth)
                }
                drawTimeCentered(centerX, timeRemaining, centerY - U * 1.5f, 2, primary)
                drawAlarmTimeCentered(centerX, midTimeRemaining, centerY + U * 0.5f, primary)
                drawTimeCentered(centerX, bigTimeRemaining, centerY + U * 1.75f, 1, primary)
                drawStaticTextCentered(centerX, strings.sessionLimitLabel, centerY + U * 3f, secondary)
            } else {
                if (sequenceLength > 1) {
                    drawStageLabelCentered(centerX, stageLabel, centerY - U * 2.5f, secondary, maxTextWidth)
                }
                drawTimeCentered(centerX, timeRemaining, centerY - U * 0.5f, 2, primary)
                drawTimeCentered(centerX, bigTimeRemaining, centerY + U * 1.5f, 1, primary)
                val label = if (activeMode == "dual-sequence") strings.blockLimitLabel else strings.sessionLimitLabel
                drawStaticTextCentered(centerX, label, centerY + U * 2.75f, secondary)
            }
            return
        }

        drawTimeCentered(centerX, timeRemaining, centerY - U * 0.5f, 2, primary)
        val isSequence = activeMode == "sequence" || activeMode == "calendar"
        if (isSequence && sequenceLength > 1) {
            drawStageLabelCentered(centerX, stageLabel, centerY + U * 1.5f, secondary, maxTextWidth)
        } else if (activeMode != "sequence") {
            val label = if (isBreak) strings.unwindingLabel else strings.focusingLabel
            drawStaticTextCentered(centerX, label, centerY + U * 1.5f, secondary)
        }
    }

    private fun drawStaticTextCentered(centerX: Float, text: String, centerY: Float, colorIndex: Int) {
        val startX = centerX - ScaledProceduralRenderer.measureTextWidth(text) / 2f
        val startY = centerY - U / 2f
        var index = 0
        while (index < text.length) {
            drawGlyph(text[index], startX + index * U, startY, colorIndex)
            index++
        }
    }

    private fun drawStageLabelCentered(
        centerX: Float,
        text: String,
        centerY: Float,
        colorIndex: Int,
        maxWidth: Float
    ) {
        if (text.isEmpty()) return
        val cellCount = minOf(text.length, maxOf(1, (maxWidth / U).toInt()))
        val startX = centerX - cellCount * U / 2f
        val startY = centerY - U / 2f
        var index = 0
        while (index < cellCount) {
            drawGlyph(text[index], startX + index * U, startY, colorIndex)
            index++
        }
    }

    private fun drawTimeCentered(centerX: Float, seconds: Int, centerY: Float, scale: Int, colorIndex: Int) {
        val safeSeconds = maxOf(0, seconds)
        val minutes = safeSeconds / 60
        val remainder = safeSeconds % 60
        val cellWidth = U * scale
        val startX = centerX - cellWidth * 2.5f
        val startY = centerY - cellWidth / 2f
        drawGlyph(((minutes / 10) % 10 + 48).toChar(), startX, startY, colorIndex, scale)
        drawGlyph((minutes % 10 + 48).toChar(), startX + cellWidth, startY, colorIndex, scale)
        drawGlyph(':', startX + cellWidth * 2f, startY, colorIndex, scale)
        drawGlyph((remainder / 10 + 48).toChar(), startX + cellWidth * 3f, startY, colorIndex, scale)
        drawGlyph((remainder % 10 + 48).toChar(), startX + cellWidth * 4f, startY, colorIndex, scale)
    }

    private fun drawAlarmTimeCentered(centerX: Float, seconds: Int, centerY: Float, colorIndex: Int) {
        val safeSeconds = maxOf(0, seconds)
        val minutes = safeSeconds / 60
        val remainder = safeSeconds % 60
        val prefix = "[ ALARM: "
        val totalCells = prefix.length + 7
        var drawX = centerX - totalCells * U / 2f
        val drawY = centerY - U / 2f
        var index = 0
        while (index < prefix.length) {
            drawGlyph(prefix[index], drawX, drawY, colorIndex)
            drawX += U
            index++
        }
        drawGlyph(((minutes / 10) % 10 + 48).toChar(), drawX, drawY, colorIndex); drawX += U
        drawGlyph((minutes % 10 + 48).toChar(), drawX, drawY, colorIndex); drawX += U
        drawGlyph(':', drawX, drawY, colorIndex); drawX += U
        drawGlyph((remainder / 10 + 48).toChar(), drawX, drawY, colorIndex); drawX += U
        drawGlyph((remainder % 10 + 48).toChar(), drawX, drawY, colorIndex); drawX += U
        drawGlyph(' ', drawX, drawY, colorIndex); drawX += U
        drawGlyph(']', drawX, drawY, colorIndex)
    }

    private fun drawGlyph(char: Char, x: Float, y: Float, colorIndex: Int, scale: Int = 1) {
        renderer.drawGlyph(char, x, y, colorIndex, scale = scale)
    }

    /** Degree → radians helper for polar positions. */
    private fun cosDeg(degrees: Float): Float = cos(degrees * 0.017453292519943295f)
    private fun sinDeg(degrees: Float): Float = sin(degrees * 0.017453292519943295f)
}
