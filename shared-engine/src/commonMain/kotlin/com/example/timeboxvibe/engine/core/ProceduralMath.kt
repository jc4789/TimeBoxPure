package com.example.timeboxvibe.engine.core

import kotlin.math.abs

// pure-Kotlin math separated from UI platforms
fun getPixelColor(
    iconName: String,
    x: Int,
    y: Int,
    primaryColor: Int,
    onBackgroundColor: Int,
    surfaceColor: Int
): Int {
    val dx = x - 15.5f
    val dy = y - 15.5f
    val rSq = dx * dx + dy * dy

    return when (iconName) {
        "yinyang", "reset_yinyang" -> {
            if (rSq > 225f) return 0
            if (rSq > 196f) return 0xFF000000.toInt()
            val dyTop = y - 8.5f
            val dyBottom = y - 22.5f
            val rTopSq = dx * dx + dyTop * dyTop
            val rBottomSq = dx * dx + dyBottom * dyBottom
            when {
                rTopSq <= 4f -> 0xFFFFFFFF.toInt()
                rTopSq <= 49f -> 0xFFCC0000.toInt()
                rBottomSq <= 4f -> 0xFFCC0000.toInt()
                rBottomSq <= 49f -> 0xFFFFFFFF.toInt()
                dx < 0f -> 0xFFFFFFFF.toInt()
                else -> 0xFFCC0000.toInt()
            }
        }
        "play_danmaku" -> {
            val cx = 11.5f
            val cy = 15.5f
            val dxPlay = x - cx
            val dyPlay = y - cy
            fun inFlameShape(r: Float, tail: Float): Boolean {
                if (dxPlay <= 0) {
                    return dxPlay * dxPlay + dyPlay * dyPlay <= r * r
                } else {
                    if (dxPlay > tail) return false
                    val thickness = r * (1f - dxPlay / tail)
                    return abs(dyPlay) <= thickness
                }
            }
            val isSpark = (x == 1 && (y == 15 || y == 16)) ||
                    (x == 4 && (y == 10 || y == 21))
            if (isSpark) return 0xFFFFCC00.toInt()
            if (inFlameShape(11f, 19f)) {
                when {
                    inFlameShape(4.5f, 8f) -> 0xFFFFFFFF.toInt()
                    inFlameShape(7f, 13f) -> 0xFFFFCC00.toInt()
                    inFlameShape(9.5f, 17f) -> 0xFFFF2200.toInt()
                    else -> 0xFF000000.toInt()
                }
            } else 0
        }
        "pause_ofuda" -> {
            val isPaper1 = x in 7..13 && y in 4..27
            val isPaper2 = x in 18..24 && y in 4..27
            if (isPaper1 || isPaper2) {
                val tx = if (isPaper1) x - 7 else x - 18
                val ty = y - 4
                if (tx == 0 || tx == 6 || ty == 0 || ty == 23) {
                    return 0xFFCC0000.toInt()
                }
                if (ty in 2..3 && tx in 2..4) return 0xFFFFCC00.toInt()
                val isRune = when (ty) {
                    6, 7, 9, 10, 12, 13, 15, 16, 18, 19 -> tx == 3
                    8, 11, 14, 17 -> tx in 2..4
                    20 -> tx in 1..5
                    else -> false
                }
                return if (isRune) 0xFFCC0000.toInt() else 0xFFFFFFFF.toInt()
            }
            val isOutline1 = x in 6..14 && y in 3..28
            val isOutline2 = x in 17..25 && y in 3..28
            if (isOutline1 || isOutline2) {
                return 0xFF000000.toInt()
            }
            0
        }
        "skip_double_danmaku" -> {
            fun getChevronColor(xTip: Int): Int {
                val along = xTip - x
                if (along < 0 || along > 10) return 0
                val armDistance = abs(abs(y * 2 - 31) - along * 2)
                return when {
                    armDistance <= 1 -> 0xFFFFFFFF.toInt()
                    armDistance <= 3 -> 0xFFFFCC00.toInt()
                    armDistance <= 5 -> 0xFFFF2200.toInt()
                    else -> 0
                }
            }
            val col2 = getChevronColor(27)
            if (col2 != 0) return col2
            val col1 = getChevronColor(16)
            if (col1 != 0) return col1
            0
        }
        "ribbon" -> {
            if (abs(dx) <= 1.5f && abs(dy) <= 1.5f) return 0xFFFFFFFF.toInt()
            if (abs(dx) <= 3f && abs(dy) <= 3f) return 0xFFFFCC00.toInt()
            val isLeftLoop = dx >= -12f && dx <= -2f && dy >= -7f && dy <= 3f &&
                    !(dx >= -9f && dx <= -4f && dy >= -5f && dy <= 1f) &&
                    (dy <= -0.5f * dx + 1f && dy >= 0.5f * dx - 3f)
            val isRightLoop = dx >= 2f && dx <= 12f && dy >= -7f && dy <= 3f &&
                    !(dx >= 4f && dx <= 9f && dy >= -5f && dy <= 1f) &&
                    (dy <= 0.5f * dx + 1f && dy >= -0.5f * dx - 3f)
            val isLeftTail = dy >= 2f && dy <= 13f && dx >= -9f && dx <= -1f &&
                    abs(dy + dx * 1.2f) <= 2.5f && !(dy > 10f && dx < -7f)
            val isRightTail = dy >= 2f && dy <= 13f && dx >= 1f && dx <= 9f &&
                    abs(dy - dx * 1.2f) <= 2.5f && !(dy > 10f && dx > 7f)
            val isHighlight = (dy >= -6f && dy <= -4f &&
                    ((dx >= -10f && dx <= -5f) || (dx >= 5f && dx <= 10f))) ||
                    (dy >= 5f && dy <= 7f &&
                    ((dx >= -6f && dx <= -4f) || (dx >= 4f && dx <= 6f)))
            if (isHighlight && (isLeftLoop || isRightLoop || isLeftTail || isRightTail)) {
                return 0xFFFFCC00.toInt()
            }
            if (isLeftLoop || isRightLoop || isLeftTail || isRightTail) return primaryColor
            0
        }
        "gohei" -> {
            val stickDist = abs(x + y - 31f)
            val isUpperPaper = (x in 16..22 && y in 5..8) ||
                    (x in 20..23 && y in 8..11) ||
                    (x in 18..22 && y in 11..14) ||
                    (x in 18..20 && y in 14..17) ||
                    (x in 18..23 && y in 16..18)
            val isLowerPaper = (x in 9..14 && y in 13..15) ||
                    (x in 11..13 && y in 15..19) ||
                    (x in 8..13 && y in 18..21) ||
                    (x in 8..10 && y in 21..24) ||
                    (x in 7..13 && y in 23..26)
            val isFold = (x in 20..22 && y in 8..9) ||
                    (x in 18..20 && y in 14..15) ||
                    (x in 11..13 && y in 15..16) ||
                    (x in 8..10 && y in 21..22)
            if (x in 14..17 && y in 14..17) return 0xFFCC0000.toInt()
            if (isFold) return 0xFFFFCC00.toInt()
            if (isUpperPaper || isLowerPaper) return 0xFFFFFFFF.toInt()
            if (stickDist <= 0.8f && x in 4..27 && y in 4..27) return 0xFF8B4513.toInt()
            if (stickDist <= 1.8f && x in 4..27 && y in 4..27) return 0xFF000000.toInt()
            0
        }
        "ofuda" -> {
            val inFrontPaper = x in 14..25 && y in 8..29
            if (inFrontPaper) {
                val frontHeader = y in 9..10 && x in 16..23
                val frontRune = (x == 19 || x == 20) && y in 12..26 ||
                        (y == 14 || y == 18 || y == 22) && x in 16..23 ||
                        y == 26 && x in 17..22 ||
                        ((x == 16 || x == 23) && (y == 16 || y == 20))
                val frontSeal = x in 18..21 && y in 23..27 &&
                        (x == 18 || x == 21 || y == 23 || y == 27)
                val frontShadow = (x == 25 || y == 29) && ((x + y) and 1) == 0
                return when {
                    frontHeader -> 0xFFFFCC00.toInt()
                    frontSeal || frontRune -> 0xFFCC0000.toInt()
                    frontShadow -> onBackgroundColor
                    else -> 0xFFFFFFFF.toInt()
                }
            }
            val inFrontOutline = x in 13..26 && y in 7..30 &&
                    !((x == 13 || x == 26) && (y == 7 || y == 30))
            if (inFrontOutline) return 0xFF000000.toInt()

            val inBackPaper = x in 6..15 && y in 3..24
            if (inBackPaper) {
                val backHeader = y in 4..5 && x in 8..13
                val backRune = (x == 10 || x == 11) && y in 7..21 ||
                        (y == 9 || y == 14 || y == 19) && x in 8..13 ||
                        ((x == 8 || x == 13) && (y == 11 || y == 16))
                val backShadow = (x == 15 || y == 24) && ((x + y) and 1) == 0
                return when {
                    backHeader -> 0xFFFFCC00.toInt()
                    backRune -> 0xFFCC0000.toInt()
                    backShadow -> onBackgroundColor
                    else -> 0xFFFFFFFF.toInt()
                }
            }
            val inBackOutline = x in 5..16 && y in 2..25 &&
                    !((x == 5 || x == 16) && (y == 2 || y == 25))
            if (inBackOutline) return 0xFF000000.toInt()

            0
        }
        "hakkero" -> {
            val absX = abs(dx)
            val absY = abs(dy)
            if (absX > 13.5f || absY > 13.5f || absX + absY > 20f) return 0
            if (absX > 11.5f || absY > 11.5f || absX + absY > 17.5f) {
                return 0xFFFFCC00.toInt()
            }
            if (absX > 10f || absY > 10f || absX + absY > 15.5f) {
                return 0xFF000000.toInt()
            }

            val coreDiamond = absX + absY
            if (coreDiamond <= 1.5f) return 0xFFFFFFFF.toInt()
            if (coreDiamond <= 3.5f) return 0xFFFFCC00.toInt()
            if (coreDiamond <= 5.5f) return 0xFFFF2200.toInt()

            val isCardinalMark = (y in 6..8 && x in 12..19) ||
                    (y in 23..25 && x in 12..19) ||
                    (x in 6..8 && y in 12..19) ||
                    (x in 23..25 && y in 12..19)
            val isDiagonalMark = (x in 8..10 && y in 8..10) ||
                    (x in 21..23 && y in 8..10) ||
                    (x in 8..10 && y in 21..23) ||
                    (x in 21..23 && y in 21..23)
            if (isCardinalMark) return 0xFFFFFFFF.toInt()
            if (isDiagonalMark) return 0xFFFFCC00.toInt()
            surfaceColor
        }
        "watch" -> {
            val cx = 15.5f
            val cy = 16.5f
            val dxWatch = x - cx
            val dyWatch = y - cy
            val rWatchSq = dxWatch * dxWatch + dyWatch * dyWatch

            val isHangerOutline = (y == 1 && x in 13..18) || (y == 2 && (x == 12 || x == 19)) ||
                    (y == 3 && (x == 12 || x == 19)) || (y == 4 && (x == 13 || x == 18))
            val isHangerFill = (y == 2 && x in 13..18) || (y == 3 && x in 13..18) || (y == 4 && x in 14..17)
            val isHangerHole = (y == 2 || y == 3) && x in 15..16
            
            if (isHangerOutline) return 0xFF000000.toInt()
            if (isHangerFill) {
                if (isHangerHole) return 0
                return 0xFFFFCC00.toInt()
            }

            if (rWatchSq <= 144f) {
                if (rWatchSq > 121f) return 0xFF000000.toInt()
                if (rWatchSq > 100f) {
                    if (dxWatch < -3f && dyWatch < -3f && ((x + y) and 1) == 0) {
                        return 0xFFFFFFFF.toInt()
                    }
                    return 0xFFFFCC00.toInt()
                }
                if (rWatchSq > 81f) return 0xFF000000.toInt()

                val isPivot = x in 15..16 && y in 16..17
                if (isPivot) return 0xFFFFCC00.toInt()

                val isHourHand = dxWatch > 0f && dyWatch < 0f && abs(dxWatch - (-dyWatch)) <= 0.8f && rWatchSq <= 16f
                val isMinHand = dxWatch < 0f && dyWatch < 0f && abs(dxWatch - dyWatch) <= 0.8f && rWatchSq <= 49f
                if (isHourHand || isMinHand) return 0xFF000000.toInt()

                val is12Tick = (x == 15 || x == 16) && y == 8
                val is6Tick = (x == 15 || x == 16) && y == 24
                val is3Tick = x == 23 && (y == 16 || y == 17)
                val is9Tick = x == 7 && (y == 16 || y == 17)
                val isDiagTick = (x == 11 && y == 10) || (x == 19 && y == 10) ||
                        (x == 11 && y == 22) || (x == 19 && y == 22) ||
                        (x == 8 && y == 12) || (x == 22 && y == 12) ||
                        (x == 8 && y == 20) || (x == 22 && y == 20)

                if (is12Tick || is6Tick || is3Tick || is9Tick) return 0xFFFFCC00.toInt()
                if (isDiagTick) return 0xFF000000.toInt()

                return 0xFFFFFFFF.toInt()
            }
            0
        }
        else -> 0
    }
}

/**
 * Graphics-path sine/cosine LUT (allocation-free).
 * Sole ornament/render trig source — do not call kotlin.math.sin/cos in hot draw paths.
 * Circle rasterization lives only in [AliasedVectorLayer.drawAliasedCircle].
 */
object FastMath {
    private const val TABLE_SIZE = 1024
    private const val MASK = TABLE_SIZE - 1
    // The high bits select a LUT entry; the low bits interpolate to the next entry.
    // This keeps a 486px-radius orbit below one final pixel per angular quantization step.
    private const val INDEX_FRACTION_BITS = 8
    private const val INDEX_FRACTION_SCALE = 1 shl INDEX_FRACTION_BITS
    private const val INDEX_FRACTION_MASK = INDEX_FRACTION_SCALE - 1
    private const val INDEX_FRACTION_TO_FLOAT = 1f / INDEX_FRACTION_SCALE
    private const val DEGREES_TO_FIXED_INDEX =
        TABLE_SIZE * INDEX_FRACTION_SCALE / 360f

    private val sinTable = FloatArray(TABLE_SIZE)
    private val cosTable = FloatArray(TABLE_SIZE)

    init {
        for (i in 0 until TABLE_SIZE) {
            val radians = i * 2.0 * kotlin.math.PI / TABLE_SIZE
            sinTable[i] = kotlin.math.sin(radians).toFloat()
            cosTable[i] = kotlin.math.cos(radians).toFloat()
        }
    }

    fun fastSin(angleIndex: Int): Float {
        val baseIndex = angleIndex ushr INDEX_FRACTION_BITS
        val fraction = (angleIndex and INDEX_FRACTION_MASK) * INDEX_FRACTION_TO_FLOAT
        val first = sinTable[baseIndex and MASK]
        val second = sinTable[(baseIndex + 1) and MASK]
        return first + (second - first) * fraction
    }

    fun fastCos(angleIndex: Int): Float {
        val baseIndex = angleIndex ushr INDEX_FRACTION_BITS
        val fraction = (angleIndex and INDEX_FRACTION_MASK) * INDEX_FRACTION_TO_FLOAT
        val first = cosTable[baseIndex and MASK]
        val second = cosTable[(baseIndex + 1) and MASK]
        return first + (second - first) * fraction
    }

    fun degreesToIdx(degrees: Float): Int {
        val normalized = ((degrees % 360f) + 360f) % 360f
        return (normalized * DEGREES_TO_FIXED_INDEX).toInt()
    }
}
