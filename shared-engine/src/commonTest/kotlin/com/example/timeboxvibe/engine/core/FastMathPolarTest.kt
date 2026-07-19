package com.example.timeboxvibe.engine.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards FastMath as the sole graphics-path polar trig source and Int-U caret constants.
 */
class FastMathPolarTest {

    @Test
    fun cardinalAxesMatchUnitCircleWithinLutTolerance() {
        val tol = 0.02f
        assertNear(1f, FastMath.fastCos(FastMath.degreesToIdx(0f)), tol)
        assertNear(0f, FastMath.fastSin(FastMath.degreesToIdx(0f)), tol)

        assertNear(0f, FastMath.fastCos(FastMath.degreesToIdx(90f)), tol)
        assertNear(1f, FastMath.fastSin(FastMath.degreesToIdx(90f)), tol)

        assertNear(-1f, FastMath.fastCos(FastMath.degreesToIdx(180f)), tol)
        assertNear(0f, FastMath.fastSin(FastMath.degreesToIdx(180f)), tol)

        assertNear(0f, FastMath.fastCos(FastMath.degreesToIdx(270f)), tol)
        assertNear(-1f, FastMath.fastSin(FastMath.degreesToIdx(270f)), tol)
    }

    @Test
    fun degreesToIdxIsStableUnderWrap() {
        assertEquals(FastMath.degreesToIdx(0f), FastMath.degreesToIdx(360f))
        assertEquals(FastMath.degreesToIdx(90f), FastMath.degreesToIdx(450f))
        assertEquals(FastMath.degreesToIdx(-90f), FastMath.degreesToIdx(270f))
    }

    @Test
    fun cursorGeometryIsIntegerUDerived() {
        assertEquals(16, EngineCursorRenderer.U)
        assertEquals(EngineCursorRenderer.U / 8, EngineCursorRenderer.CURSOR_WIDTH)
        assertEquals(EngineCursorRenderer.U, EngineCursorRenderer.CURSOR_HEIGHT)
        assertTrue(EngineCursorRenderer.CURSOR_WIDTH > 0)
    }

    private fun assertNear(expected: Float, actual: Float, tol: Float) {
        assertTrue(
            abs(expected - actual) <= tol,
            "expected $expected ± $tol but was $actual"
        )
    }
}
