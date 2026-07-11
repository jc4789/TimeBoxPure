package com.example.timeboxvibe.engine.audio.opna

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpnaAdvancedModesTest {
    @Test
    fun sharedIntegerLfoChangesAmEnabledFmOutput() {
        val frames = 4096
        val dryVoice = Fm4OpVoice(48_000)
        val wetVoice = Fm4OpVoice(48_000)
        dryVoice.applyPatch(OpnaPatchBank.Pc98Brass)
        wetVoice.applyPatch(OpnaPatchBank.Pc98Brass)
        dryVoice.noteOn(69)
        wetVoice.noteOn(69)
        wetVoice.setPerformanceControls(0, 0, 7, 3, 0, -1, 0)
        val lfo = Lfo(48_000)
        lfo.enabled = true
        lfo.rate = 7
        lfo.prepare(frames.coerceAtMost(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK))

        val dry = FloatArray(frames)
        val wet = FloatArray(frames)
        dryVoice.render(dry, frames, 48_000, 1f)
        var offset = 0
        while (offset < frames) {
            val count = minOf(OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK, frames - offset)
            lfo.prepare(count)
            wetVoice.render(wet, count, 48_000, 1f, offset, lfo)
            offset += count
        }
        assertFalse(dry.contentEquals(wet))
        assertTrue(wet.all { it.isFinite() })
    }

    @Test
    fun everySsgEgShapeRemainsFiniteAndBounded() {
        var shape = 8
        while (shape <= 15) {
            val envelope = OpnRateEnvelope()
            envelope.setSampleRate(48_000)
            envelope.attackRate = 31
            envelope.decayRate = 31
            envelope.sustainRate = 31
            envelope.sustainLevel = 1
            envelope.releaseRate = 15
            envelope.configureSsgEg(shape)
            envelope.noteOn()
            var i = 0
            while (i < 100_000) {
                val attenuation = envelope.nextAttenuation()
                assertTrue(attenuation in 0..OpnRateEnvelope.MAX_ATTENUATION)
                i++
            }
            shape++
        }
    }

    @Test
    fun hardwareSsgPathIsDeterministic() {
        val sharedA = SsgSharedState(48_000)
        val sharedB = SsgSharedState(48_000)
        val a = SsgVoice(0, sharedA, 48_000)
        val b = SsgVoice(0, sharedB, 48_000)
        val patch = OpnaPatchBank.ssgPatch(OpnaPatchBank.SSG_ENVELOPE)!!
        a.applyPatch(patch)
        b.applyPatch(patch)
        a.noteOn(440f)
        b.noteOn(440f)
        val outA = FloatArray(2048)
        val outB = FloatArray(2048)
        a.render(outA, outA.size, 48_000, 1f)
        b.render(outB, outB.size, 48_000, 1f)
        assertTrue(outA.contentEquals(outB))
        assertTrue(outA.any { it != 0f })
    }

    @Test
    fun fixedLevelPatchDoesNotRestartSharedEnvelope() {
        val sharedA = SsgSharedState(48_000)
        val sharedB = SsgSharedState(48_000)
        val voiceA = SsgVoice(0, sharedA, 48_000)
        SsgVoice(0, sharedB, 48_000)
        sharedA.configureEnvelope(shape = 10, period = 1, restart = true)
        sharedB.configureEnvelope(shape = 10, period = 1, restart = true)
        sharedA.prepare(257)
        sharedB.prepare(257)

        voiceA.applyPatch(requireNotNull(OpnaPatchBank.ssgPatch(OpnaPatchBank.SSG_SQUARE)))
        sharedA.prepare(128)
        sharedB.prepare(128)

        var frame = 0
        while (frame < 128) {
            assertTrue(sharedA.envelopeAt(frame) == sharedB.envelopeAt(frame))
            frame++
        }
    }
}
