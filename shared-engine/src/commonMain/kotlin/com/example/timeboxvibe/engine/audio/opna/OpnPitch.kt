package com.example.timeboxvibe.engine.audio.opna

import kotlin.math.abs
import kotlin.math.pow

/**
 * Yamaha-style OPN pitch laws expressed in the engine's existing 29-bit phase coordinate.
 * The F-number equation and detune values come from the YM2608 application manual.
 */
internal object OpnPitch {
    const val MASTER_CLOCK_HZ: Int = 8_000_000
    const val FM_CLOCK_DIVIDER: Int = 144
    const val EG_CLOCK_DIVIDER: Int = 3

    private const val FNUM_BITS = 11
    private const val FNUM_MASK = (1 shl FNUM_BITS) - 1
    private const val PHASE_COORDINATE_SCALE = 512.0 // 2^29 / 2^20
    private const val PHASE_STEP_MASK = 0x1ffff
    private const val CENT_LIMIT = 1_200
    private const val CENT_SCALE_BITS = 20
    private const val CENT_SCALE_ONE = 1 shl CENT_SCALE_BITS

    // Yamaha YM2608 Application Manual, detune table, represented as OPN phase-step units.
    // Columns are DT magnitudes 0..3; DT values 4..7 apply the negative sign.
    private val detuneByKeyCode = intArrayOf(
        0, 0, 1, 2,   0, 0, 1, 2,   0, 0, 1, 2,   0, 0, 1, 2,
        0, 1, 2, 2,   0, 1, 2, 3,   0, 1, 2, 3,   0, 1, 2, 3,
        0, 1, 2, 4,   0, 1, 3, 4,   0, 1, 3, 4,   0, 1, 3, 5,
        0, 2, 4, 5,   0, 2, 4, 6,   0, 2, 4, 6,   0, 2, 5, 7,
        0, 2, 5, 8,   0, 3, 6, 8,   0, 3, 6, 9,   0, 3, 7, 10,
        0, 4, 8, 11,  0, 4, 8, 12,  0, 4, 9, 13,  0, 5, 10, 14,
        0, 5, 11, 16, 0, 6, 12, 17, 0, 6, 13, 19, 0, 7, 14, 20,
        0, 8, 16, 22, 0, 8, 16, 22, 0, 8, 16, 22, 0, 8, 16, 22
    )

    private val midiPitch = IntArray(128) { midi ->
        val frequencyHz = 440.0 * 2.0.pow((midi - 69).toDouble() / 12.0)
        nearestBlockFnum(frequencyHz)
    }

    private val centsScale = IntArray(CENT_LIMIT * 2 + 1) { index ->
        val cents = index - CENT_LIMIT
        (2.0.pow(cents.toDouble() / 1200.0) * CENT_SCALE_ONE.toDouble() + 0.5).toInt()
    }

    fun nearestBlockFnum(frequencyHz: Double): Int {
        var bestBlock = 0
        var bestFnum = 0
        var bestError = Double.MAX_VALUE
        var block = 0
        while (block <= 7) {
            val blockScale = if (block == 0) 0.5 else (1 shl (block - 1)).toDouble()
            val rawFnum = (FM_CLOCK_DIVIDER.toDouble() * frequencyHz * (1 shl 20).toDouble()) /
                (MASTER_CLOCK_HZ.toDouble() * blockScale)
            val fnum = (rawFnum + 0.5).toInt().coerceIn(0, FNUM_MASK)
            val actual = frequencyFor(block, fnum)
            val error = abs(actual - frequencyHz) / frequencyHz
            if (error < bestError) {
                bestError = error
                bestBlock = block
                bestFnum = fnum
            }
            block++
        }
        return (bestBlock shl FNUM_BITS) or bestFnum
    }

    fun nearestBlockFnumForMidi(midi: Int): Int {
        return midiPitch[midi.coerceIn(0, midiPitch.lastIndex)]
    }

    fun block(packed: Int): Int = (packed ushr FNUM_BITS) and 7

    fun fnum(packed: Int): Int = packed and FNUM_MASK

    fun frequencyFor(block: Int, fnum: Int): Double {
        val blockScale = if (block == 0) 0.5 else (1 shl (block - 1)).toDouble()
        return (fnum.coerceIn(0, FNUM_MASK).toDouble() * MASTER_CLOCK_HZ.toDouble() * blockScale) /
            (FM_CLOCK_DIVIDER.toDouble() * (1 shl 20).toDouble())
    }

    fun keyCode(block: Int, fnum: Int): Int {
        val f = fnum and FNUM_MASK
        val f11 = (f ushr 10) and 1
        val f10 = (f ushr 9) and 1
        val f9 = (f ushr 8) and 1
        val f8 = (f ushr 7) and 1
        val low = (f11 and (f10 or f9 or f8)) or ((f11 xor 1) and f10 and f9 and f8)
        return ((block.coerceIn(0, 7) shl 2) or (f11 shl 1) or low).coerceIn(0, 31)
    }

    fun detuneAdjustment(detune: Int, keyCode: Int): Int {
        val dt = detune.coerceIn(0, 7)
        val magnitude = detuneByKeyCode[keyCode.coerceIn(0, 31) * 4 + (dt and 3)]
        return if ((dt and 4) != 0) -magnitude else magnitude
    }

    fun applyCents(frequencyHz: Float, cents: Int): Float {
        val scale = centsScale[cents.coerceIn(-CENT_LIMIT, CENT_LIMIT) + CENT_LIMIT]
        return frequencyHz * scale.toFloat() / CENT_SCALE_ONE.toFloat()
    }

    fun phaseStep29(
        packedPitch: Int,
        spec: OperatorSpec,
        renderRate: Int,
        cents: Int = 0,
        fnumOffset: Int = 0
    ): UInt {
        val block = block(packedPitch)
        val fnum = (fnum(packedPitch) + fnumOffset).coerceIn(0, FNUM_MASK)
        val keyCode = keyCode(block, fnum)
        var opnStep = (((fnum shl 1) shl block) ushr 2)
        opnStep = (opnStep + detuneAdjustment(spec.detune, keyCode)) and PHASE_STEP_MASK
        val multipleX2 = if (spec.mul.coerceIn(0, 15) == 0) 1 else spec.mul.coerceIn(1, 15) * 2
        opnStep = (opnStep * multipleX2) ushr 1

        val phaseStep = opnStep.toDouble() *
            (MASTER_CLOCK_HZ.toDouble() / FM_CLOCK_DIVIDER.toDouble()) *
            PHASE_COORDINATE_SCALE / renderRate.coerceAtLeast(1).toDouble()
        val rounded = (phaseStep + 0.5).toLong()
        val scale = centsScale[cents.coerceIn(-CENT_LIMIT, CENT_LIMIT) + CENT_LIMIT].toLong()
        return ((rounded * scale + (CENT_SCALE_ONE / 2)) shr CENT_SCALE_BITS).toUInt()
    }
}
