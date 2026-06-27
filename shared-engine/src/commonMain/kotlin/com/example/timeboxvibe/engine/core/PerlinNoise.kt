package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.generated.GeneratedPermLut
import kotlin.math.floor

/**
 * Deterministic 1D / 2D Perlin noise using a 256-entry permutation table.
 * The permutation is generated at build time by `tools/math_oracles/gen_lut.py`
 * with seed=42 — same build → same noise. Allocation-free, hot-loop safe.
 *
 * Convention: `noise1D` and `noise2D` return values in roughly [-1, 1].
 * `fbm` sums `octaves` octaves of `noise2D` at halved frequencies and halved
 * amplitudes, giving more natural-looking fractal noise in roughly [-1, 1].
 *
 * Use cases in the magic circle:
 *   - rune glyph angle perturbation (1D)
 *   - background nebula (2D fbm)
 *   - any organic, non-repeating drift
 */
object PerlinNoise {
    private val PERM: IntArray = GeneratedPermLut.DATA
    private const val PERM_SIZE: Int = 256

    /**
     * 1D Perlin noise. Returns roughly [-1, 1].
     * `x` is any real number; the function is smooth and continuous.
     */
    fun noise1D(x: Float): Float {
        val xi = floor(x.toDouble()).toInt() and (PERM_SIZE - 1)
        val xf = x - floor(x.toDouble()).toFloat()
        val u = fade(xf)

        val a = PERM[xi]
        val b = PERM[xi + 1]

        val g0 = grad1D(a)
        val g1 = grad1D(b)

        return lerp(g0 * xf, g1 * (xf - 1f), u)
    }

    /**
     * 2D Perlin noise. Returns roughly [-1, 1].
     */
    fun noise2D(x: Float, y: Float): Float {
        val xi = floor(x.toDouble()).toInt() and (PERM_SIZE - 1)
        val yi = floor(y.toDouble()).toInt() and (PERM_SIZE - 1)
        val xf = x - floor(x.toDouble()).toFloat()
        val yf = y - floor(y.toDouble()).toFloat()
        val u = fade(xf)
        val v = fade(yf)

        val aa = PERM[PERM[xi] + yi]
        val ab = PERM[PERM[xi] + yi + 1]
        val ba = PERM[PERM[xi + 1] + yi]
        val bb = PERM[PERM[xi + 1] + yi + 1]

        val x1 = lerp(grad2D(aa, xf, yf), grad2D(ba, xf - 1f, yf), u)
        val x2 = lerp(grad2D(ab, xf, yf - 1f), grad2D(bb, xf - 1f, yf - 1f), u)
        return lerp(x1, x2, v)
    }

    /**
     * Fractal Brownian motion: sum of `octaves` octaves of `noise2D` with halved
     * frequency and halved amplitude per octave. Result in roughly [-1, 1].
     */
    fun fbm(x: Float, y: Float, octaves: Int): Float {
        if (octaves <= 0) return 0f
        var sum = 0f
        var amp = 1f
        var freq = 1f
        var norm = 0f
        var i = 0
        while (i < octaves) {
            sum += amp * noise2D(x * freq, y * freq)
            norm += amp
            amp *= 0.5f
            freq *= 2f
            i++
        }
        if (norm <= 0f) return 0f
        return sum / norm
    }

    /**
     * 5th-order smoothstep — Perlin's classic ease curve.
     * Maps [0, 1] -> [0, 1] with zero 1st and 2nd derivative at the endpoints.
     */
    private fun fade(t: Float): Float {
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    /**
     * 1D gradient table — Perlin's choice of [−1, 1] mapped via (h & 1) ? -1 : 1.
     */
    private fun grad1D(hash: Int): Float {
        return if (hash and 1 != 0) -1f else 1f
    }

    /**
     * 2D gradient table — 8 directions, picked by lower 3 bits of hash.
     */
    private fun grad2D(hash: Int, x: Float, y: Float): Float {
        val h = hash and 7
        val u = if (h < 4) x else y
        val v = if (h < 4) y else x
        return (if (h and 1 != 0) -u else u) + (if (h and 2 != 0) -2f * v else 2f * v)
    }
}
