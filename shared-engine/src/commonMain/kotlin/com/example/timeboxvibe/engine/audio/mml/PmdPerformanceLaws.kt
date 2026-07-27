package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.OpnPitch
import com.example.timeboxvibe.engine.audio.opna.OpnaTimerBLaws

/** Named PMD driver units shared by MML setup and allocation-free playback. */
object PmdPerformanceLaws {
    const val BPM_MILLI_SCALE = 1_000
    const val DEFAULT_CLOCKS_PER_QUARTER = 24
    const val DEFAULT_WHOLE_NOTE_CLOCKS = 96
    const val WHOLE_NOTE_CLOCKS_MIN = 1
    const val WHOLE_NOTE_CLOCKS_MAX = 255
    const val MUSICAL_TEMPO_MIN = 18
    const val MUSICAL_TEMPO_MAX = 255
    const val TIMER_B_MIN = 0
    const val TIMER_B_MAX = 255
    const val PORTAMENTO_MAX_CLOCKS = 255
    const val TIMING_ABSOLUTE = 0
    const val TIMING_RELATIVE = 1
    const val TIMING_MUSICAL_TEMPO = 0
    const val TIMING_TIMER_B = 1
    const val TIMING_WHOLE_NOTE_CLOCKS = 2
    const val ENVELOPE_CLOCK_NORMAL = 0
    const val ENVELOPE_CLOCK_EXTENDED = 1
    const val ENVELOPE_DISABLED = 0
    const val ENVELOPE_LEGACY = 1
    const val ENVELOPE_EXTENDED = 2

    // PMD's manual specifies EX1 as approximately 56 Hz.
    const val EXTENDED_ENVELOPE_CLOCK_MILLIHERTZ = 56_000L
    const val LFO_CLOCK_NORMAL = 0
    const val LFO_CLOCK_FIXED = 1
    const val FIXED_LFO_CLOCK_MILLIHERTZ = 56_000L
    const val SOFTWARE_LFO_RANDOM_SEED = 0x13579BDF
    const val SSG_DETUNE_NORMAL = 0
    const val SSG_DETUNE_EXTENDED = 1

    // Stable compile-time gate randomization seed; loop playback reuses the
    // resulting primitive gate clocks exactly.
    const val GATE_RANDOM_SEED = 0x6D2B79F5

    /**
     * PMD software conversion from a half-note tempo to the nearest legal
     * physical Timer B preset. Returns -1 when the requested period cannot fit
     * the eight-bit hardware counter.
     */
    internal fun timerBForMusicalTempo(wholeNoteClocks: Int, musicalTempo: Int): Int {
        if (wholeNoteClocks !in WHOLE_NOTE_CLOCKS_MIN..WHOLE_NOTE_CLOCKS_MAX ||
            musicalTempo !in MUSICAL_TEMPO_MIN..MUSICAL_TEMPO_MAX
        ) return -1
        val denominator = TEMPO_DIVISOR.toLong() * wholeNoteClocks.toLong() * musicalTempo.toLong()
        val numerator = TEMPO_MASTER_CLOCK_MULTIPLIER.toLong() * OpnPitch.MASTER_CLOCK_HZ.toLong()
        val timerSteps = (numerator + denominator / 2L) / denominator
        if (timerSteps !in 1L..OpnaTimerBLaws.COUNTER_MODULUS.toLong()) return -1
        return OpnaTimerBLaws.COUNTER_MODULUS - timerSteps.toInt()
    }

    /** Actual quarter-note tempo produced by a physical preset and PMD grid. */
    internal fun quarterBpmMilliForTimerB(timerB: Int, clocksPerQuarter: Int): Int {
        if (timerB !in TIMER_B_MIN..TIMER_B_MAX || clocksPerQuarter <= 0) return 0
        val denominator = OpnaTimerBLaws.periodMasterClocks(timerB) * clocksPerQuarter.toLong()
        val numerator = SECONDS_PER_MINUTE.toLong() * OpnPitch.MASTER_CLOCK_HZ.toLong() * BPM_MILLI_SCALE
        return ((numerator + denominator / 2L) / denominator).toInt()
    }

    /**
     * PMD DX1 scales SSG detune/LFO period offsets down once per authored
     * octave. A non-zero source value always retains at least one period step.
     */
    internal fun extendedSsgPitchOffset(value: Int, midi: Int): Int {
        if (value == 0) return 0
        val octave = (midi / NOTES_PER_OCTAVE - MIDI_OCTAVE_BIAS).coerceIn(
            MIN_PMD_OCTAVE,
            MAX_PMD_OCTAVE
        )
        val magnitude = if (value < 0) -value.toLong() else value.toLong()
        val corrected = (magnitude shr octave).coerceAtLeast(MIN_NONZERO_SSG_PITCH_OFFSET)
        return if (value < 0) -corrected.toInt() else corrected.toInt()
    }

    private const val TEMPO_MASTER_CLOCK_MULTIPLIER = 5
    private const val TEMPO_DIVISOR = 48
    private const val SECONDS_PER_MINUTE = 60
    private const val NOTES_PER_OCTAVE = 12
    private const val MIDI_OCTAVE_BIAS = 1
    private const val MIN_PMD_OCTAVE = 0
    private const val MAX_PMD_OCTAVE = 8
    private const val MIN_NONZERO_SSG_PITCH_OFFSET = 1L
}
