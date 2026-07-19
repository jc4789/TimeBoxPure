package com.example.timeboxvibe.engine.audio.opna

internal object OpnaPatterns {

    fun focusMotif(seq: OpnaSequencer) {
        seq.clear()
        val scale = PhrygianDominantScale(64) // E4 root

        // Lead melody - FM Channel 0 (ZunLead1)
        // Bar 1
        seq.noteFm(0, scale.degreeToMidi(0, 0), 0.0f, 1.0f)
        seq.noteFm(0, scale.degreeToMidi(1, 0), 1.0f, 0.5f)
        seq.noteFm(0, scale.degreeToMidi(2, 0), 1.5f, 0.5f)
        seq.noteFm(0, scale.degreeToMidi(4, 0), 2.0f, 1.0f)
        // Bar 2
        seq.noteFm(0, scale.degreeToMidi(3, 0), 4.0f, 1.0f)
        seq.noteFm(0, scale.degreeToMidi(2, 0), 5.0f, 0.5f)
        seq.noteFm(0, scale.degreeToMidi(1, 0), 5.5f, 0.5f)
        seq.noteFm(0, scale.degreeToMidi(0, 0), 6.0f, 2.0f)
        // Bar 3
        seq.noteFm(0, scale.degreeToMidi(4, 0), 8.0f, 1.0f)
        seq.noteFm(0, scale.degreeToMidi(5, 0), 9.0f, 0.5f)
        seq.noteFm(0, scale.degreeToMidi(4, 0), 9.5f, 0.5f)
        seq.noteFm(0, scale.degreeToMidi(7, 0), 10.0f, 1.0f)
        // Bar 4
        seq.noteFm(0, scale.degreeToMidi(6, 0), 12.0f, 1.0f)
        seq.noteFm(0, scale.degreeToMidi(5, 0), 13.0f, 0.5f)
        seq.noteFm(0, scale.degreeToMidi(4, 0), 13.5f, 0.5f)
        seq.noteFm(0, scale.degreeToMidi(0, 0), 14.0f, 2.0f)

        // Bass - FM Channel 1 (ZunBass1)
        var bar = 0
        while (bar < 4) {
            val baseBeat = bar * 4f
            val degree = when (bar) {
                0 -> 0
                1 -> 1
                2 -> 4
                else -> 0
            }
            var b = 0
            while (b < 8) {
                seq.noteFm(1, scale.degreeToMidi(degree, -2), baseBeat + b * 0.5f, 0.4f)
                b++
            }
            bar++
        }

        // Arp - SSG Channel 0
        var beat = 0f
        while (beat < 16f) {
            val step = (beat * 2).toInt() % 4
            val degree = when (step) {
                0 -> 0
                1 -> 2
                2 -> 4
                else -> 7
            }
            seq.noteSsg(0, scale.degreeToMidi(degree, -1), beat, 0.4f)
            beat += 0.5f
        }

        // Drums: kick 1+3, snare 2+4, hat every 8th
        beat = 0f
        while (beat < 16f) {
            val barBeat = beat % 4f
            
            // Kick on 1 and 3 (beats 0 and 2)
            if (barBeat == 0.0f || barBeat == 2.0f) {
                seq.noteDrum(ProceduralDrums.DrumKind.KICK, beat)
            }
            // Snare on 2 and 4 (beats 1 and 3)
            if (barBeat == 1.0f || barBeat == 3.0f) {
                seq.noteDrum(ProceduralDrums.DrumKind.SNARE, beat)
            }
            // Hat every 8th note (every 0.5 beats)
            seq.noteDrum(ProceduralDrums.DrumKind.HAT, beat)
            
            beat += 0.5f
        }
    }

    fun alarmMotif(seq: OpnaSequencer) {
        seq.clear()
        val scale = PhrygianDominantScale(64) // E4 root

        // Lead melody - FM Channel 0 (ZunBell1)
        var beat = 0f
        while (beat < 8f) {
            val barBeat = beat % 4f
            val note = if (barBeat < 2f) scale.degreeToMidi(7, 0) else scale.degreeToMidi(8, 0)
            seq.noteFm(0, note, beat, 0.4f)
            beat += 0.5f
        }

        // Bass - FM Channel 1 (ZunBass1)
        beat = 0f
        while (beat < 8f) {
            seq.noteFm(1, scale.degreeToMidi(0, -2), beat, 0.4f)
            beat += 0.5f
        }

        // Drums: four-on-the-floor, snare 2+4, hat every 8th
        beat = 0f
        while (beat < 8f) {
            val barBeat = beat % 4f
            
            // Kick on every beat
            if (barBeat % 1.0f == 0.0f) {
                seq.noteDrum(ProceduralDrums.DrumKind.KICK, beat)
            }
            // Snare on 2 and 4 (beats 1 and 3)
            if (barBeat == 1.0f || barBeat == 3.0f) {
                seq.noteDrum(ProceduralDrums.DrumKind.SNARE, beat)
            }
            // Hat every 8th
            seq.noteDrum(ProceduralDrums.DrumKind.HAT, beat)
            
            beat += 0.5f
        }
    }

    fun relaxMotif(seq: OpnaSequencer) {
        seq.clear()
        val scale = PentatonicMinorScale(57) // A3 root

        // SSG Pad - sustained square on SSG Channel 0
        seq.noteSsg(0, scale.degreeToMidi(0, 0), 0.0f, 4.0f)
        seq.noteSsg(0, scale.degreeToMidi(2, 0), 4.0f, 4.0f)
        seq.noteSsg(0, scale.degreeToMidi(3, 0), 8.0f, 4.0f)
        seq.noteSsg(0, scale.degreeToMidi(4, 0), 12.0f, 4.0f)

        // Drums: kick on 1, hat on 2 and 4, no snare
        var beat = 0f
        while (beat < 16f) {
            val barBeat = beat % 4f
            
            // Kick on 1
            if (barBeat == 0.0f) {
                seq.noteDrum(ProceduralDrums.DrumKind.KICK, beat)
            }
            // Hat on 2 and 4
            if (barBeat == 1.0f || barBeat == 3.0f) {
                seq.noteDrum(ProceduralDrums.DrumKind.HAT, beat)
            }
            
            beat += 0.5f
        }
    }

    fun padMotif(seq: OpnaSequencer) {
        seq.clear()
        val scale = DorianScale(62) // D4 root

        // FM Pad - FM Channel 0 (ZunPad1)
        seq.noteFm(0, scale.degreeToMidi(0, 0), 0.0f, 4.0f)
        seq.noteFm(0, scale.degreeToMidi(2, 0), 4.0f, 4.0f)

        // SSG Arp - SSG Channel 0
        var beat = 0f
        while (beat < 8f) {
            val step = (beat * 2).toInt() % 4
            val degree = when (step) {
                0 -> 0
                1 -> 2
                2 -> 4
                else -> 2
            }
            seq.noteSsg(0, scale.degreeToMidi(degree, -1), beat, 0.4f)
            beat += 0.5f
        }
        
        // No drums for padMotif
    }
}
