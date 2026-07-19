package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvelopeStageTest {
    @Test
    fun testTransitions() {
        val env = Envelope()
        env.attack = 0.1f
        env.decay = 0.1f
        env.sustain = 0.5f
        env.release = 0.1f

        env.noteOn()
        assertEquals(Envelope.ATTACK, env.stage)
        assertEquals(0f, env.level)

        env.next(0.05f)
        assertEquals(Envelope.ATTACK, env.stage)
        assertTrue(env.level in 0.49f..0.51f)

        env.next(0.06f)
        assertEquals(Envelope.DECAY, env.stage)

        env.next(0.1f)
        assertEquals(Envelope.SUSTAIN, env.stage)
        assertEquals(0.5f, env.level)

        env.noteOff()
        assertEquals(Envelope.RELEASE, env.stage)
        env.next(0.05f)
        assertTrue(env.level in 0.1f..0.35f, "Expected exponential release, got ${env.level}")
        env.next(0.7f)
        assertEquals(Envelope.OFF, env.stage)
        assertEquals(0f, env.level)
    }
}
