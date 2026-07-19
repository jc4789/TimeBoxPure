package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.CompiledOpnaSong
import com.example.timeboxvibe.engine.audio.opna.OpnaLikeSynthesizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RinToShiteSongTest {
    @Test
    fun scoreArrangementCompilesAsAShortTenBarV2Finale() {
        val success = assertIs<MmlCompileResult.Success>(MmlSongBank.rinToShiteResult)
        val program = assertNotNull(success.arrangement.compiledOpnaSong)

        assertEquals(2, program.dialectVersion)
        assertEquals(-1, program.lfoRate)
        assertEquals(10L * 4L * CompiledOpnaSong.TICKS_PER_QUARTER, program.durationTicks)
        assertEquals(283, program.eventCount)
        assertTrue(program.durationMilliseconds() in 17_000L..18_000L)
        assertTrue(MmlSongBank.RIN_TO_SHITE_MML.contains("A @brass V72 Q8 p3 l8"))
        assertTrue(MmlSongBank.RIN_TO_SHITE_MML.contains("B @strings V62 Q8 p2 l2"))
        assertTrue(MmlSongBank.RIN_TO_SHITE_MML.contains("D @piano V64 Q8 P1 p1 l16"))
        assertTrue(MmlSongBank.RIN_TO_SHITE_MML.contains("E @strings V48 Q8 p2 l2"))
        assertTrue(MmlSongBank.RIN_TO_SHITE_MML.contains("F @strings V54 Q8 p1 l2"))
        assertTrue(!MmlSongBank.RIN_TO_SHITE_MML.contains("R @drum"))
        var event = 0
        while (event < program.eventCount) {
            assertTrue(program.eventType[event] != CompiledOpnaSong.RHYTHM_SHOT)
            event++
        }
    }

    @Test
    fun arrangementTimelineRendersDeterministically() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.rinToShiteResult).arrangement
        val sampleRate = 48_000
        val synthA = OpnaLikeSynthesizer(sampleRate)
        val synthB = OpnaLikeSynthesizer(sampleRate)
        val playerA = MmlArrangementScheduler.createPlayer(arrangement, synthA, sampleRate)
        val playerB = MmlArrangementScheduler.createPlayer(arrangement, synthB, sampleRate)

        assertEquals(555, playerA.eventCount)
        assertEquals(playerA.eventCount, playerB.eventCount)
        assertTrue(playerA.loopLengthSamples > 0L)

        val a = FloatArray(16_384)
        val b = FloatArray(a.size)
        synthA.render(a, a.size, playerA, 0L)
        synthB.render(b, b.size, playerB, 0L)
        assertTrue(a.contentEquals(b))
        assertTrue(a.any { abs(it) > 0.0001f })
        assertTrue(a.all { it.isFinite() })
    }

    @Test
    fun compiledSongHonorsTheExistingPlaybackVolumeArgument() {
        val arrangement = assertNotNull(MmlSongBank.getArrangement(MmlSongBank.RIN_TO_SHITE_KEY, 0.5f))
        val program = assertNotNull(arrangement.compiledOpnaSong)
        assertEquals(0.5f, program.playbackGain)
    }

    @Test
    fun completePolyphonicRenderRemainsFinite() {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlSongBank.rinToShiteResult).arrangement
        val sampleRate = 48_000
        val synth = OpnaLikeSynthesizer(sampleRate)
        var voice = 0
        while (voice < synth.fm.size) {
            synth.fm[voice].enableOversampling = true
            voice++
        }
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)
        val output = FloatArray(player.loopLengthSamples.toInt())
        synth.render(output, output.size, player, 0L)
        println("RIN_POLY_HEADROOM peak=${synth.preClampPeak} kneeCrossings=${synth.preClampKneeCrossings}")
        assertTrue(output.all { it.isFinite() })
    }

    @Test
    fun firstChordRetainsEveryIndependentFmPart() {
        val program = assertNotNull(
            assertIs<MmlCompileResult.Success>(MmlSongBank.rinToShiteResult)
                .arrangement.compiledOpnaSong
        )
        val fmChannels = intArrayOf(0, 1, 3, 4, 5)
        val expectedMidi = intArrayOf(81, 62, 50, 66, 69)
        var channel = 0
        while (channel < expectedMidi.size) {
            var found = false
            var event = 0
            while (event < program.eventCount) {
                if ((program.eventType[event] == CompiledOpnaSong.FM_NOTE ||
                        program.eventType[event] == CompiledOpnaSong.FM_POLY_NOTE) &&
                    program.channel[event] == fmChannels[channel] &&
                    program.startTick[event] == 0L &&
                    program.midi[event] == expectedMidi[channel]
                ) {
                    found = true
                    break
                }
                event++
            }
            assertTrue(found, "FM channel ${fmChannels[channel]} lost its first score note")
            channel++
        }

        val headers = arrayOf(
            "A @brass V72", "B @strings V62", "D @piano V64",
            "E @strings V48", "F @strings V54"
        )
        val partRmsValues = FloatArray(headers.size)
        val sampleRate = 48_000
        channel = 0
        while (channel < headers.size) {
            var isolated = MmlSongBank.RIN_TO_SHITE_MML
            var mute = 0
            while (mute < headers.size) {
                if (mute != channel) isolated = isolated.replace(headers[mute], headers[mute].substringBefore('V') + "V0")
                mute++
            }
            val rendered = renderSource(isolated)
            val start = sampleRate / 10
            val frames = sampleRate / 4
            val partRms = rms(rendered, start, frames)
            partRmsValues[channel] = partRms
            val lateRms = rms(rendered, sampleRate * 11 / 20, sampleRate / 4)
            val frequency = 440.0 * 2.0.pow((expectedMidi[channel] - 69) / 12.0)
            val fundamental = toneMagnitude(rendered, start, frames, frequency, sampleRate)
            println("RIN_FIRST_CHORD channel=${fmChannels[channel]} midi=${expectedMidi[channel]} earlyRms=$partRms lateRms=$lateRms fundamental=$fundamental")
            assertTrue(partRms > 0.0005f, "FM channel ${fmChannels[channel]} is effectively inaudible")
            channel++
        }
        val loudestChordTone = maxOf(partRmsValues[1], partRmsValues[3], partRmsValues[4])
        val quietestChordTone = minOf(partRmsValues[1], partRmsValues[3], partRmsValues[4])
        assertTrue(
            quietestChordTone >= loudestChordTone * 0.55f,
            "Root/third/fifth do not form an audible chord: ${partRmsValues[1]}, ${partRmsValues[3]}, ${partRmsValues[4]}"
        )
    }

    private fun renderSource(source: String): FloatArray {
        val arrangement = assertIs<MmlCompileResult.Success>(MmlCompiler.compile(source)).arrangement
        val sampleRate = 48_000
        val synth = OpnaLikeSynthesizer(sampleRate)
        synth.enableOutputFilter = true
        var channel = 0
        while (channel < synth.fm.size) {
            synth.fm[channel].enableOversampling = true
            channel++
        }
        val player = MmlArrangementScheduler.createPlayer(arrangement, synth, sampleRate)
        val output = FloatArray(player.loopLengthSamples.toInt())
        synth.render(output, output.size, player, 0L)
        return output
    }

    private fun rms(buffer: FloatArray, start: Int, frames: Int): Float {
        var energy = 0.0
        var i = start
        val end = minOf(buffer.size, start + frames)
        while (i < end) {
            val sample = buffer[i].toDouble()
            energy += sample * sample
            i++
        }
        return sqrt(energy / (end - start)).toFloat()
    }

    private fun toneMagnitude(buffer: FloatArray, start: Int, frames: Int, frequency: Double, sampleRate: Int): Double {
        val omega = 2.0 * PI * frequency / sampleRate
        val coefficient = 2.0 * cos(omega)
        var previous = 0.0
        var previous2 = 0.0
        var i = start
        val end = minOf(buffer.size, start + frames)
        while (i < end) {
            val current = buffer[i] + coefficient * previous - previous2
            previous2 = previous
            previous = current
            i++
        }
        val power = previous2 * previous2 + previous * previous - coefficient * previous * previous2
        return sqrt(power.coerceAtLeast(0.0)) * 2.0 / (end - start)
    }
}
