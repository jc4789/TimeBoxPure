package com.example.timeboxvibe.engine.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A platform-independent vector coordinate representation.
 * Allows math pipelines to compile without any dependency on Jetpack Compose or Native Android types.
 */
data class Point2D(val x: Float, val y: Float)

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
            val dxSub = dx
            val dyTop = y - 8.5f
            val dyBottom = y - 22.5f
            val rTopSq = dxSub * dxSub + dyTop * dyTop
            val rBottomSq = dxSub * dxSub + dyBottom * dyBottom
            when {
                rTopSq <= 20.25f -> if (rTopSq <= 4f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                rBottomSq <= 20.25f -> if (rBottomSq <= 4f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                dx < 0f -> 0xFFFFFFFF.toInt()
                else -> 0xFF000000.toInt()
            }
        }
        "play_danmaku" -> {
            val cx = 12f
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
            if (inFlameShape(11f, 17f)) {
                when {
                    inFlameShape(5f, 7f) -> 0xFFFFFFFF.toInt()
                    inFlameShape(8f, 12f) -> 0xFFFFCC00.toInt() // yellow
                    inFlameShape(10f, 15f) -> 0xFFFF2200.toInt() // fire-red
                    else -> 0xFF000000.toInt() // high-contrast outline
                }
            } else 0
        }
        "pause_ofuda" -> {
            val isTal1 = x in 7..13 && y in 4..27
            val isTal2 = x in 18..24 && y in 4..27
            if (isTal1 || isTal2) {
                val tx = if (isTal1) x - 7 else x - 18
                val ty = y - 4
                if (tx == 0 || tx == 6 || ty == 0 || ty == 23) {
                    return 0xFFCC0000.toInt()
                }
                val isRune = when (ty) {
                    3, 4 -> tx == 3
                    6 -> tx in 2..4
                    8, 9 -> tx == 3
                    11 -> tx in 2..4
                    13, 14 -> tx == 3
                    16 -> tx in 2..4
                    18, 19 -> tx == 3
                    21 -> tx in 1..5
                    else -> false
                }
                return if (isRune) 0xFFCC0000.toInt() else 0xFFFFFFFF.toInt()
            }
            // 1-pixel high-contrast black outline surrounding the talismans
            val isOutline1 = x in 6..14 && y in 3..28
            val isOutline2 = x in 17..25 && y in 3..28
            if (isOutline1 || isOutline2) {
                return 0xFF000000.toInt()
            }
            0
        }
        "skip_double_danmaku" -> {
            fun getChevronColor(xTip: Float): Int {
                val dxVal = xTip - x
                if (dxVal < 0 || dxVal > 10f) return 0
                val dyVal = abs(y - 15.5f)
                val isOuter = dyVal <= dxVal && !(dxVal >= 4f && dyVal <= (dxVal - 4f))
                if (!isOuter) return 0
                val isCore = dyVal <= (dxVal - 1f) / 2f && dxVal >= 1f && !(dxVal >= 4f && dyVal <= (dxVal - 4f))
                if (isCore) return 0xFFFFFFFF.toInt()
                if (dyVal <= dxVal - 1f) {
                    return 0xFF00AAFF.toInt()
                }
                return 0xFF000000.toInt() // high-contrast outline
            }
            val col2 = getChevronColor(26f)
            if (col2 != 0) return col2
            val col1 = getChevronColor(15f)
            if (col1 != 0) return col1
            0
        }
        "ribbon" -> {
            if (abs(dx) <= 2.2f && abs(dy) <= 2.2f) return primaryColor
            val isLeftLoop = dx >= -12f && dx <= -2f && dy >= -7f && dy <= 3f &&
                    !(dx >= -9f && dx <= -4f && dy >= -5f && dy <= 1f) &&
                    (dy <= -0.5f * dx + 1f && dy >= 0.5f * dx - 3f)
            val isRightLoop = dx >= 2f && dx <= 12f && dy >= -7f && dy <= 3f &&
                    !(dx >= 4f && dx <= 9f && dy >= -5f && dy <= 1f) &&
                    (dy <= 0.5f * dx + 1f && dy >= -0.5f * dx - 3f)
            val isLeftTail = dy >= 2f && dy <= 13f && dx >= -9f && dx <= -1f && abs(dy - (-dx * 1.2f)) <= 2.5f
            val isRightTail = dy >= 2f && dy <= 13f && dx >= 1f && dx <= 9f && abs(dy - (dx * 1.2f)) <= 2.5f
            if (isLeftLoop || isRightLoop || isLeftTail || isRightTail) return primaryColor
            0
        }
        "gohei" -> {
            val stickDist = abs(x + y - 31f)
            if (stickDist <= 0.8f && x in 5..26 && y in 5..26) return 0xFF8B4513.toInt()
            val isRibbon1 = (x in 15..17 && y in 5..9) || (x in 17..20 && y in 9..13) || (x in 15..18 && y in 13..17)
            val isRibbon2 = (x in 10..12 && y in 10..14) || (x in 12..15 && y in 14..18) || (x in 10..13 && y in 18..22)
            if (isRibbon1 || isRibbon2) return 0xFFFFFFFF.toInt()
            0
        }
        "ofuda" -> {
            // --- FRONT TALISMAN LAYOUT (Offset down-right: x in 14..25, y in 8..29) ---
            val isFrontOutline = (x == 14 || x == 25) && y in 8..29 || (y == 8 || y == 29) && x in 14..25
            val isFrontPin = x in 19..20 && y in 9..10
            val isFrontPinOutline = (x == 18 && y in 9..10) || (x == 21 && y in 9..10) || (y == 11 && x in 19..20)
            val isFrontRune = (x == 19 || x == 20) && y in 12..27 ||
                    y == 14 && x in 16..23 ||
                    y == 18 && x in 16..23 ||
                    y == 22 && x in 16..23 ||
                    y == 25 && x in 17..22 ||
                    (x == 16 && y == 16) || (x == 23 && y == 16) ||
                    (x == 16 && y == 20) || (x == 23 && y == 20)

            if (isFrontOutline) return 0xFF000000.toInt()
            if (isFrontPinOutline) return 0xFF000000.toInt()
            if (isFrontPin) return 0xFFFFEE55.toInt() // Gold header pin
            if (isFrontRune) return 0xFFCC0000.toInt() // Crimson rune seal
            
            // Dithered shading along bottom/right edge of front paper
            val isFrontDither = (x == 24 && y in 15..28) || (y == 28 && x in 15..24)
            if (isFrontDither && (x + y) % 2 == 0) return 0xFFFFAAA6.toInt()
            
            if (x in 15..24 && y in 9..28) return 0xFFFFFFFF.toInt() // Front paper body

            // --- BACK TALISMAN LAYOUT (Offset up-left: x in 6..15, y in 3..24) ---
            val isBackOutline = (x == 6 || x == 15) && y in 3..24 || (y == 3 || y == 24) && x in 6..15
            val isBackRune = x == 10 && y in 6..22 ||
                    y == 8 && x in 8..13 ||
                    y == 13 && x in 8..13 ||
                    y == 18 && x in 7..14

            if (isBackOutline) return 0xFF000000.toInt()
            if (isBackRune) return 0xFFCC0000.toInt() // Crimson rune seal
            
            // Dithered shading along bottom/right edge of back paper
            val isBackDither = (x == 14 && y in 10..23) || (y == 23 && x in 7..14)
            if (isBackDither && (x + y) % 2 == 0) return 0xFFFFAAA6.toInt()
            
            if (x in 7..14 && y in 4..23) return 0xFFFFFFFF.toInt() // Back paper body

            0
        }
        "hakkero" -> {
            val octVal = maxOf(abs(dx), abs(dy)) + 0.5f * (abs(dx) + abs(dy))
            if (octVal > 16f) return 0
            if (octVal > 12f) return 0xFFD4AF37.toInt()
            if (octVal > 10f) return 0xFF000000.toInt()
            if (rSq <= 36f) return if (rSq <= 9f) 0xFFFFCC00.toInt() else primaryColor
            val isTrigram = (y == 7 && x in 13..18) || (y == 24 && x in 13..18) || (x == 7 && y in 13..18) || (x == 24 && y in 13..18)
            if (isTrigram) return 0xFFFFFFFF.toInt()
            0xFF333333.toInt()
        }
        "watch" -> {
            val cx = 15.5f
            val cy = 16.5f
            val dxWatch = x - cx
            val dyWatch = y - cy
            val rWatchSq = dxWatch * dxWatch + dyWatch * dyWatch

            // Hanger loop at top (y in 1..4, x in 12..19)
            val isHangerOutline = (y == 1 && x in 13..18) || (y == 2 && (x == 12 || x == 19)) ||
                    (y == 3 && (x == 12 || x == 19)) || (y == 4 && (x == 13 || x == 18))
            val isHangerFill = (y == 2 && x in 13..18) || (y == 3 && x in 13..18) || (y == 4 && x in 14..17)
            val isHangerHole = (y == 2 || y == 3) && x in 15..16
            
            if (isHangerOutline) return 0xFF000000.toInt()
            if (isHangerFill) {
                if (isHangerHole) return 0
                return 0xFFCCCCCC.toInt() // Silver hanger
            }

            // Pocket watch casing circle
            if (rWatchSq <= 144f) {
                if (rWatchSq > 121f) return 0xFF000000.toInt() // Casing outer outline
                if (rWatchSq > 100f) return 0xFFD4AF37.toInt() // Gold outer bezel
                if (rWatchSq > 81f) return 0xFF000000.toInt() // Casing inner bevel

                // Inside the watch face (radius <= 9, rWatchSq <= 81f)
                // Center pivot
                val isPivot = x in 15..16 && y in 16..17
                if (isPivot) return 0xFFD4AF37.toInt() // Gold pivot

                // Hands (Black)
                val isHourHand = dxWatch > 0f && dyWatch < 0f && abs(dxWatch - (-dyWatch)) <= 0.8f && rWatchSq <= 16f
                val isMinHand = dxWatch < 0f && dyWatch < 0f && abs(dxWatch - dyWatch) <= 0.8f && rWatchSq <= 49f
                if (isHourHand || isMinHand) return 0xFF000000.toInt()

                // Dial Ticks
                val is12Tick = (x == 15 || x == 16) && y == 8
                val is6Tick = (x == 15 || x == 16) && y == 24
                val is3Tick = x == 23 && (y == 16 || y == 17)
                val is9Tick = x == 7 && (y == 16 || y == 17)
                val isDiagTick = (x == 11 && y == 10) || (x == 19 && y == 10) ||
                        (x == 11 && y == 22) || (x == 19 && y == 22) ||
                        (x == 8 && y == 12) || (x == 22 && y == 12) ||
                        (x == 8 && y == 20) || (x == 22 && y == 20)

                if (is12Tick || is6Tick || is3Tick || is9Tick || isDiagTick) return 0xFF000000.toInt()

                return 0xFFFFFFFF.toInt()
            }
            0
        }
        else -> 0
    }
}

// Fast integer-indexed Sine/Cosine Lookup Table (LUT) for zero-allocation procedural geometry
object FastMath {
    private const val TABLE_SIZE = 1024
    private const val MASK = TABLE_SIZE - 1

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
        return sinTable[angleIndex and MASK]
    }

    fun fastCos(angleIndex: Int): Float {
        return cosTable[angleIndex and MASK]
    }

    fun degreesToIdx(degrees: Float): Int {
        val normalized = ((degrees % 360f) + 360f) % 360f
        return (normalized * (TABLE_SIZE / 360f)).toInt() and MASK
    }
}

fun getStarVertices(centerX: Float, centerY: Float, starRadius: Float, points: Int): List<Point2D> {
    return List(points) { idx ->
        val deg = idx * (360f / points)
        val aIdx = FastMath.degreesToIdx(deg)
        Point2D(
            centerX + starRadius * FastMath.fastCos(aIdx),
            centerY + starRadius * FastMath.fastSin(aIdx)
        )
    }
}

fun getTickPoints(centerX: Float, centerY: Float, radiusOuter: Float, radiusInner: Float, count: Int): List<Pair<Point2D, Point2D>> {
    return List(count) { idx ->
        val deg = idx * (360f / count)
        val aIdx = FastMath.degreesToIdx(deg)
        val cosVal = FastMath.fastCos(aIdx)
        val sinVal = FastMath.fastSin(aIdx)
        val p1 = Point2D(centerX + radiusOuter * cosVal, centerY + radiusOuter * sinVal)
        val p2 = Point2D(centerX + radiusInner * cosVal, centerY + radiusInner * sinVal)
        Pair(p1, p2)
    }
}

fun getDanmakuBulletOffset(centerX: Float, centerY: Float, radius: Float, progress: Float): Point2D {
    val angleDegrees = -90f + 360f * progress
    val aIdx = FastMath.degreesToIdx(angleDegrees)
    return Point2D(
        centerX + radius * FastMath.fastCos(aIdx),
        centerY + radius * FastMath.fastSin(aIdx)
    )
}

fun getDanmakuSparkOffset(centerX: Float, centerY: Float, radius: Float, progress: Float): Point2D {
    val angleDegrees = -90f + 360f * progress - 5f
    val aIdx = FastMath.degreesToIdx(angleDegrees)
    return Point2D(
        centerX + radius * FastMath.fastCos(aIdx),
        centerY + radius * FastMath.fastSin(aIdx)
    )
}

fun drawBresenhamCircle(
    canvas: EngineCanvas,
    centerX: Float,
    centerY: Float,
    radius: Float,
    colorIndex: Int,
    strokeWidth: Float = 1f,
    dashed: Boolean = false
) {
    val xc = centerX.roundToInt()
    val yc = centerY.roundToInt()
    val r = radius.roundToInt()
    val sw = (strokeWidth * canvas.density).roundToInt().coerceAtLeast(1)

    var x = 0
    var y = r
    var d = 3 - 2 * r

    fun plotPoints(px: Int, py: Int) {
        val points = arrayOf(
            Pair(xc + px, yc + py),
            Pair(xc - px, yc + py),
            Pair(xc + px, yc - py),
            Pair(xc - px, yc - py),
            Pair(xc + py, yc + px),
            Pair(xc - py, yc + px),
            Pair(xc + py, yc - px),
            Pair(xc - py, yc - px)
        )
        for (p in points) {
            val skip = dashed && (px / 4) % 2 != 0
            if (!skip) {
                val xVal = p.first
                val yVal = p.second
                if (xVal >= 0 && xVal < canvas.width.toInt() && yVal >= 0 && yVal < canvas.height.toInt()) {
                    if (sw <= 1) {
                        canvas.setPixel(xVal.toFloat(), yVal.toFloat(), colorIndex)
                    } else {
                        canvas.drawRect(
                            xVal.toFloat() - sw / 2f,
                            yVal.toFloat() - sw / 2f,
                            sw.toFloat(),
                            sw.toFloat(),
                            colorIndex
                        )
                    }
                }
            }
        }
    }

    if (r > 0) {
        plotPoints(x, y)
        while (x <= y) {
            x++
            if (d > 0) {
                y--
                d += 4 * (x - y) + 10
            } else {
                d += 4 * x + 6
            }
            plotPoints(x, y)
        }
    }
}


