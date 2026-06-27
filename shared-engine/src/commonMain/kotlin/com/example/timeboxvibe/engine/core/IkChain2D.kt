package com.example.timeboxvibe.engine.core

import kotlin.math.sqrt

/**
 * 2D FABRIK (Forward-And-Backward Reaching IK) chain. A chain of N points
 * connected by N-1 segments of equal length. `solve()` iterates the constraint
 * solver to make the chain reach from `start` to `end` while preserving segment
 * lengths. Deterministic, allocation-free, ~10 ops per segment per iteration.
 *
 * Uses the engine's existing [Point2D] (defined in `ProceduralMath.kt`) for
 * chain point storage — no duplicate type.
 *
 * Use case in the engine: the yin-yang comet trail. The chain's "end" is
 * driven by the rotating core; the other 5 points lag behind, forming a
 * fading dot trail.
 */
class IkChain2D(val segmentLength: Float, val pointCount: Int) {
    val points: Array<Point2D> = Array(pointCount) { Point2D(0f, 0f) }

    init {
        require(pointCount >= 2) { "IkChain2D needs at least 2 points" }
        require(segmentLength > 0f) { "segmentLength must be > 0" }
    }

    /**
     * Solve the chain so point 0 sits at (startX, startY) and point N-1 sits at
     * (endX, endY), with all segment lengths preserved. `iterations` is the
     * number of forward+backward passes (6 is enough for sub-pixel stability).
     */
    fun solve(startX: Float, startY: Float, endX: Float, endY: Float, iterations: Int = 6) {
        val n = pointCount
        if (n < 2) return

        // Seed: place points along the straight line from start to end.
        placeLinearly(startX, startY, endX, endY)

        var iter = 0
        while (iter < iterations) {
            // Backward pass: pin end, walk back toward start, preserving segment length.
            points[n - 1] = Point2D(endX, endY)
            var i = n - 1
            while (i > 0) {
                val cur = points[i]
                val prev = points[i - 1]
                val dx = prev.x - cur.x
                val dy = prev.y - cur.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 0f) {
                    val ratio = segmentLength / dist
                    points[i - 1] = Point2D(
                        cur.x + dx * ratio,
                        cur.y + dy * ratio
                    )
                }
                i--
            }

            // Forward pass: pin start, walk forward toward end, preserving segment length.
            points[0] = Point2D(startX, startY)
            var j = 0
            while (j < n - 1) {
                val cur = points[j]
                val next = points[j + 1]
                val dx = next.x - cur.x
                val dy = next.y - cur.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 0f) {
                    val ratio = segmentLength / dist
                    points[j + 1] = Point2D(
                        cur.x + dx * ratio,
                        cur.y + dy * ratio
                    )
                }
                j++
            }
            iter++
        }
    }

    /**
     * Reset to all points at the origin. Call from the scene's `onEnter()` to
     * make the chain deterministic across scene switches.
     */
    fun reset() {
        var i = 0
        while (i < pointCount) {
            points[i] = Point2D(0f, 0f)
            i++
        }
    }

    private fun placeLinearly(startX: Float, startY: Float, endX: Float, endY: Float) {
        if (pointCount == 1) {
            points[0] = Point2D(startX, startY)
            return
        }
        val steps = (pointCount - 1).toFloat()
        var i = 0
        while (i < pointCount) {
            val t = i / steps
            points[i] = Point2D(
                startX + (endX - startX) * t,
                startY + (endY - startY) * t
            )
            i++
        }
    }
}
