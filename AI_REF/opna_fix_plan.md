# OPNA Engine Fix Plan

**Status:** Planning phase - no code written yet
**Date:** 2026-01-28
**Scope:** Fix polyphony, rewrite arrangements, tune FM patches

---

## 1. Current Issues

### 1.1 synth-chime and synth-victory Play Single Flat Note

**Symptom:** Both sounds play only one sustained note instead of a melody.

**Root Cause Analysis:**

The arrangement data has overlapping notes designed for the old `ChiptuneSynthesizer` which played all notes simultaneously as a chord. The OPNA sequencer processes events sequentially with a single cursor per channel.

**synth-chime arrangement data:**
```kotlin
lead = Lane(listOf(ToneSpec(466f, 0, 800, 0.7f * volume, "square")), ...)
bass = Lane(listOf(ToneSpec(233f, 0, 800, 0.5f * volume, "triangle")), ...)
```
- Only 1 note in lead lane, 1 note in bass lane
- Designed for old engine's "play all at once" behavior
- With OPNA, it's just 2 sustained SSG waves

**synth-victory arrangement data:**
```kotlin
lead = Lane(listOf(
    ToneSpec(523.25f, 0, 1800, ...),    // starts at 0ms, lasts 1800ms
    ToneSpec(659.25f, 120, 1800, ...),  // starts at 120ms, lasts 1800ms
    ToneSpec(783.99f, 240, 1800, ...),  // starts at 240ms, lasts 1800ms
    ToneSpec(1046.5f, 360, 1800, ...)   // starts at 360ms, lasts 1800ms
), ...)
```
- 4 notes with overlapping durations (each lasts 1800ms)
- All routed to FM channel 0
- Sequencer processes note 0: noteOn at 0ms, noteOff at 1800ms
- When sequencer reaches 1800ms to do noteOff for note 0, notes 1-3 have already passed their start times (120ms, 240ms, 360ms)
- **Sequencer skips notes 1-3** because their start times are in the past

**Sequencer limitation:**
The sequencer uses a single cursor (`fmIdx`) with a phase variable (0=waiting for noteOn, 1=waiting for noteOff). It can only track one note at a time per channel. When notes overlap, subsequent notes are skipped.

### 1.2 Bad Apple, Senbonzakura, LLS Don't Sound Right

**Symptom:** These arrangements have proper sequential notes but the timbre sounds wrong.

**Root Cause Analysis:**

The `Fm4OpVoice.noteOn()` method overrides per-operator envelope settings from the patch:

```kotlin
fun noteOn(midi: Int, attack: Float?, decay: Float?, sustain: Float?, release: Float?) {
    baseFrequency = midiToFreq(midi)
    val p = patch
    if (p != null) {
        recalcPhaseSteps(p)
        val a = attack ?: p.op3.attack
        val d = decay ?: p.op3.decay
        val s = sustain ?: p.op3.sustain
        val r = release ?: p.op3.release
        for (i in 0 until AudioLaws.FM_OPERATORS) {
            opState[i].envelope.attack = a  // <-- Overrides all operators with same values
            opState[i].envelope.decay = d
            opState[i].envelope.sustain = s
            opState[i].envelope.release = r
        }
    }
    // ...
}
```

**Problem:** All 4 operators get the same A/D/S/R values, losing the per-operator envelope design from the patch. This makes all patches sound similar and loses timbral character.

**Additional issue:** The `OperatorSpec` now has per-operator envelope fields (`ar`, `dr`, `sl`, `rr`), but they're not being used when `egMode == LEGACY_ADSR`.

### 1.3 Other Potential Issues

**Stereo rendering:** The `renderStereoSegment` method uses a shared `tempMonoBuffer` that's only 44100 samples. If rendering more than 1 second at a time, this will cause issues. Currently not a problem since chunks are 1024 samples, but should be noted.

**FM gain staging:** The gain path is:
- `outputLevel` (from TL) applied per operator
- `totalLevel` applied at channel output
- `noteGain` applied per voice
- `mixer.fmGain` (1.0) applied in mixer
- `OPNA_OUTPUT_GAIN` (2.0) applied in softClip

This should be correct, but may need tuning.

---

## 2. Solution Architecture

### 2.1 Polyphonic Sequencer Design

**Current design:** Single cursor per track (fmIdx, ssgIdx, drumIdx) with phase variable.

**New design:** Flat event stream sorted by sample time, with noteId guards for stale noteOff.

**Event structure:**
```kotlin
internal class SequencerEvent {
    var type: Int = 0  // 0=FM_ON, 1=FM_OFF, 2=SSG_ON, 3=SSG_OFF, 4=DRUM
    var sampleTime: Long = 0
    var channel: Int = 0
    var midi: Int = 0
    var velocity: Float = 0f
    var durationSamples: Long = 0
    var noteId: Int = 0  // Unique ID for noteOn/noteOff pairing
    var attack: Float = 0f
    var decay: Float = 0f
    var sustain: Float = 0f
    var release: Float = 0f
}
```

**Event processing:**
1. Sort all events by sampleTime at sequencer setup
2. Maintain a cursor (nextEventIdx) that advances through the sorted list
3. For each chunk, process all events within the chunk's time range
4. For FM_OFF/SSG_OFF events, check if the noteId matches the currently active note on that channel
5. If noteId doesn't match (stale noteOff), skip it

**NoteId generation:**
- Each noteOn gets a unique noteId (incrementing counter starting at 1)
- The corresponding noteOff uses the same noteId
- When processing noteOff, check if the channel's current noteId matches
- If not, the noteOff is stale (from a previous note that was interrupted)
- `-1` is reserved for "inactive" (no active note on that channel)
- `0` is not used
- When counter reaches `Int.MAX_VALUE`, wrap back to `1` (not `0`)
- Theoretical wrap collision after 2 billion notes is acceptable; simpler than using Long

**NoteId counter implementation:**
```kotlin
private var noteIdCounter: Int = 1

private fun nextNoteId(): Int {
    val id = noteIdCounter
    noteIdCounter = if (noteIdCounter == Int.MAX_VALUE) 1 else noteIdCounter + 1
    return id
}
```

**Active note tracking:**
```kotlin
private val fmActiveNoteId = IntArray(AudioLaws.FM_CHANNELS) { -1 }
private val ssgActiveNoteId = IntArray(AudioLaws.SSG_CHANNELS) { -1 }
```

### 2.2 Per-Operator Envelope Fix

**Current behavior:** `noteOn()` overrides all operators with the same A/D/S/R values.

**New behavior:**
- If `noteOn()` is called with explicit A/D/S/R values, use those for all operators (backwards compatible)
- If `noteOn()` is called with null values, use the patch's per-operator envelope settings
- For `egMode == LEGACY_ADSR`, use `OperatorSpec.attack/decay/sustain/release`
- For `egMode == OPN_RATE`, use `OperatorSpec.ar/dr/sl/rr`

**Implementation:**
```kotlin
fun noteOn(midi: Int, attack: Float?, decay: Float?, sustain: Float?, release: Float?) {
    baseFrequency = midiToFreq(midi)
    val p = patch ?: return
    recalcPhaseSteps(p)
    
    // Only override if explicitly provided
    if (attack != null || decay != null || sustain != null || release != null) {
        val a = attack ?: p.op3.attack
        val d = decay ?: p.op3.decay
        val s = sustain ?: p.op3.sustain
        val r = release ?: p.op3.release
        for (i in 0 until AudioLaws.FM_OPERATORS) {
            opState[i].envelope.attack = a
            opState[i].envelope.decay = d
            opState[i].envelope.sustain = s
            opState[i].envelope.release = r
        }
    } else {
        // Use patch's per-operator values
        for (i in 0 until AudioLaws.FM_OPERATORS) {
            val spec = when (i) {
                0 -> p.op0
                1 -> p.op1
                2 -> p.op2
                else -> p.op3
            }
            opState[i].envelope.attack = spec.attack
            opState[i].envelope.decay = spec.decay
            opState[i].envelope.sustain = spec.sustain
            opState[i].envelope.release = spec.release
        }
    }
    
    // Reset phases and trigger envelopes
    for (i in 0 until AudioLaws.FM_OPERATORS) {
        opState[i].phase = 0f
        opState[i].prevOutput = 0f
        opState[i].envelope.noteOn()
        opState[i].opnEnvelope.noteOn()
    }
    op0Feedback = 0f
}
```

### 2.3 Stereo Buffer Strategy

**Fixed-size buffers, no dynamic allocation:**

```kotlin
companion object {
    const val MAX_FRAMES_PER_CHUNK = 1024
}

private val leftScratch = FloatArray(MAX_FRAMES_PER_CHUNK)
private val rightScratch = FloatArray(MAX_FRAMES_PER_CHUNK)
```

**Large request handling:**
If the caller requests more than `MAX_FRAMES_PER_CHUNK` frames, split into multiple internal renders:

```kotlin
fun renderStereo(stereoBuffer: FloatArray, frames: Int) {
    var offset = 0
    var remaining = frames
    while (remaining > 0) {
        val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
        renderStereoChunk(stereoBuffer, offset, chunkFrames)
        offset += chunkFrames * 2  // stereo = 2 samples per frame
        remaining -= chunkFrames
    }
}
```

**Rationale:**
- Fixed-size buffers align with "CARMACK BUT ZUN" philosophy (no allocation in hot paths)
- 1024 frames per chunk is friendly to both OPNA rendering and AudioTrack
- Avoids creating `FloatArray(...)` inside render loops

### 2.4 Arrangement Rewrites

#### synth-chime (Bell-like Arpeggio)

**Target:** OPNA-style sparkling chime, 90 BPM, Bb major

**Design:**
- Lead: FM bell patch (ZunBell1), arpeggiated pattern
- Bass: FM bass patch (ZunBass1), root notes
- Percussion: Sparse hat hits

**Note pattern (approximate):**
```
Lead (FM bell):
  Bb4 (eighth) - D5 (eighth) - F5 (quarter) - D5 (eighth) - Bb4 (eighth) - F4 (quarter)
  [repeat with variation]

Bass (FM bass):
  Bb2 (quarter) - Bb2 (quarter) - F3 (quarter) - Bb2 (quarter)
  [repeat]

Percussion:
  Hat on beats 2 and 4
```

**Duration:** ~3-4 seconds (2-3 bars at 90 BPM)

#### synth-victory (Zelda Item Get Jingle)

**Target:** Short ascending arpeggio, 120 BPM, C major

**Design:**
- Lead: FM lead patch (ZunLead1), ascending fanfare
- Bass: FM bass patch (ZunBass1), root notes on beats 1 and 3
- Percussion: Kick on beat 1, snare on beat 3

**Note pattern (Zelda-style):**
```
Lead (FM lead):
  C5 (eighth) - E5 (eighth) - G5 (quarter) - C6 (half)
  [total: 2 bars = 4 beats = 2 seconds at 120 BPM]

Bass (FM bass):
  C3 (quarter) - C3 (quarter) - G3 (quarter) - C4 (quarter)
  [root-position arpeggio]

Percussion:
  Kick on beat 1, snare on beat 3
```

**Duration:** ~2-3 seconds

### 2.5 FM Patch Tuning

**Target sounds:**
- **ZUN lead:** Bright nasal FM lead / FM piano hybrid
  - Algorithm 0 (serial chain)
  - High modulation index on op0 (3.0-4.0)
  - Fast attack (5ms), medium decay (50ms), high sustain (0.7)
  - TL: op0=0, op1=8, op2=14, op3=10

- **Bell:** Sharp metallic FM bell
  - Algorithm 2 (op0→op1→op2 + op3 free)
  - High feedback (5-6)
  - Very fast attack (1ms), fast decay (30ms), low sustain (0.2)
  - TL: op0=0, op1=4, op2=10, op3=6
  - High multiplier on op0 (mul=3) for metallic harmonics

- **Bass:** Short punchy FM bass
  - Algorithm 1 (parallel chains)
  - Low feedback (0-1)
  - Fast attack (2ms), fast decay (20ms), low sustain (0.3)
  - TL: op0=0, op1=16, op2=6, op3=10

- **Pad:** Quiet support, not modern lush pad
  - Algorithm 4 (two 2-op chains)
  - Low feedback (0)
  - Slow attack (20ms), slow decay (100ms), medium sustain (0.5)
  - Low modulation index (1.5-2.0)
  - TL: op0=6, op1=10, op2=8, op3=10

**Tuning process:**
1. Start with the above values
2. Render each patch in isolation (unit test)
3. Listen to the output
4. Adjust modulation indices and TL values iteratively
5. Document final values

**No patch editor UI.** Manual tuning only. Document values in code comments.

---

## 3. Implementation Phases

### Phase 1: Fix Per-Operator Envelopes (Quick Win)

**Goal:** Make patches use their per-operator envelope settings.

**Files to modify:**
- `Fm4OpVoice.kt` - Fix `noteOn()` to preserve per-operator envelopes

**Changes:**
1. Modify `noteOn()` to only override envelope values if explicitly provided
2. If all parameters are null, use patch's per-operator values
3. Keep backwards compatibility: if any parameter is provided, use current behavior

**Testing:**
- Render ZunLead1 with null parameters → should use patch's per-operator values
- Render ZunLead1 with explicit parameters → should override all operators
- Verify output sounds different (more timbral character)

**Risk:** Low. This is a minimal change that fixes a clear bug.

### Phase 2: Rewrite synth-chime and synth-victory Arrangements (Quick Win)

**Goal:** Create proper sequential melodies for these sounds.

**Files to modify:**
- `SoundMelodies.kt` - Rewrite `synth-chime` and `synth-victory` arrangements

**Changes:**
1. synth-chime: Bell-like arpeggio with FM bell patch
2. synth-victory: Zelda-style ascending fanfare with FM lead patch
3. Use sequential notes (no overlap) as a temporary workaround

**Testing:**
- Play synth-chime → should hear sparkling arpeggio
- Play synth-victory → should hear ascending fanfare
- Verify duration is 2-4 seconds

**Risk:** Low. This is just data changes.

### Phase 3: Tune FM Patches (Iterative)

**Goal:** Achieve OPNA-style timbres.

**Files to modify:**
- `Patches.kt` - Adjust patch parameters

**Changes:**
1. Update patch parameters based on target sounds (see 2.5)
2. Render each patch in isolation
3. Listen and adjust iteratively

**Testing:**
- Unit test: Render each patch for 1 second, verify output is in audible range
- Manual test: Listen to each patch, verify it matches target sound

**Risk:** Medium. Requires iterative listening and adjustment.

### Phase 4: Implement Polyphonic Sequencer (Complex)

**Goal:** Support overlapping notes on the same channel.

**Files to modify:**
- `OpnaSequencer.kt` - Add event sorting and noteId tracking
- `OpnaLikeSynthesizer.kt` - Rewrite `renderWithSequencer` and `renderStereoWithSequencer`

**Changes:**
1. Add `SequencerEvent` class with noteId field
2. Modify `OpnaSequencer` to store events in a flat list, sorted by sampleTime
3. Add noteId counter (starting at 1, wrapping at Int.MAX_VALUE) and active note tracking in `OpnaLikeSynthesizer`
4. Rewrite sequencer rendering to process events from sorted list
5. For noteOff events, check noteId to avoid stale noteOff
6. Use fixed-size stereo buffers (MAX_FRAMES_PER_CHUNK = 1024)
7. Split large render requests into multiple chunks

**Testing:**
- Unit test: Render synth-victory with overlapping notes → all notes should play
- Unit test: Render Bad Apple with sequential notes → should still work
- Unit test: Verify noteOff guards work (interrupted notes don't cause issues)

**Risk:** High. This is a significant rewrite of the sequencer logic.

### Phase 5: Add Diagnostic Tests

**Goal:** Verify fixes and prevent regressions.

**Files to add:**
- `OpnaPolyphonyTest.kt` - Test overlapping notes
- `OpnaStereoTest.kt` - Test stereo panning
- `OpnaPerOperatorEnvelopeTest.kt` - Test per-operator envelopes

**Tests:**
1. Polyphony test: Render 4 overlapping notes on same channel → verify all 4 are audible
2. Stereo test: Render with pan=1 (left) → verify left channel has signal, right is silent
3. Per-operator envelope test: Render with different per-operator A/D/S/R → verify envelope stages are independent

**Testing approach:**
- Light frequency/signal tests (zero-crossing count, RMS, peak)
- No FFT-based musical quality tests
- Focus on signal presence and basic correctness

---

## 4. Detailed Technical Specifications

### 4.1 Sequencer Event Structure

```kotlin
internal class SequencerEvent {
    companion object {
        const val FM_ON = 0
        const val FM_OFF = 1
        const val SSG_ON = 2
        const val SSG_OFF = 3
        const val DRUM = 4
    }
    
    var type: Int = 0
    var sampleTime: Long = 0
    var channel: Int = 0
    var midi: Int = 0
    var velocity: Float = 0f
    var durationSamples: Long = 0
    var noteId: Int = 0
    var attack: Float = 0f
    var decay: Float = 0f
    var sustain: Float = 0f
    var release: Float = 0f
}
```

### 4.2 Sequencer Event List

```kotlin
class OpnaSequencer(val sampleRate: Int, val bpm: Float, val beatsPerBar: Int = 4) {
    companion object {
        const val MAX_EVENTS = 4096  // Increased from 1024 to support more events
    }
    
    private val events = Array<SequencerEvent?>(MAX_EVENTS) { null }
    private var eventCount = 0
    
    // NoteId counter: starts at 1, wraps at Int.MAX_VALUE back to 1
    // -1 is reserved for "inactive", 0 is not used
    private var noteIdCounter: Int = 1
    
    private fun nextNoteId(): Int {
        val id = noteIdCounter
        noteIdCounter = if (noteIdCounter == Int.MAX_VALUE) 1 else noteIdCounter + 1
        return id
    }
    
    fun noteFmRaw(channel: Int, midi: Int, startSample: Long, durationSamples: Long, 
                  velocity: Float, attack: Float?, decay: Float?, sustain: Float?, release: Float?) {
        if (eventCount + 2 >= MAX_EVENTS) return
        
        val noteId = nextNoteId()
        
        // Add FM_ON event
        val onEvent = SequencerEvent()
        onEvent.type = SequencerEvent.FM_ON
        onEvent.sampleTime = startSample
        onEvent.channel = channel
        onEvent.midi = midi
        onEvent.velocity = velocity
        onEvent.durationSamples = durationSamples
        onEvent.noteId = noteId
        onEvent.attack = attack ?: 0f
        onEvent.decay = decay ?: 0f
        onEvent.sustain = sustain ?: 0f
        onEvent.release = release ?: 0f
        events[eventCount++] = onEvent
        
        // Add FM_OFF event
        val offEvent = SequencerEvent()
        offEvent.type = SequencerEvent.FM_OFF
        offEvent.sampleTime = startSample + durationSamples
        offEvent.channel = channel
        offEvent.noteId = noteId
        events[eventCount++] = offEvent
    }
    
    // Similar methods for SSG and drums...
    
    fun sortEvents() {
        // Sort events by sampleTime
        // Use insertion sort (stable, good for nearly-sorted data)
        var i = 1
        while (i < eventCount) {
            val key = events[i]
            var j = i - 1
            while (j >= 0 && events[j]!!.sampleTime > key!!.sampleTime) {
                events[j + 1] = events[j]
                j--
            }
            events[j + 1] = key
            i++
        }
    }
}
```

### 4.3 Polyphonic Sequencer Rendering

```kotlin
private fun renderWithSequencer(
    buffer: FloatArray,
    frames: Int,
    sequencer: OpnaSequencer,
    currentSampleOffset: Long
) {
    val chunkEnd = currentSampleOffset + frames
    var renderPos = 0
    
    // Advance event cursor to current position
    while (sequencer.nextEventIdx < sequencer.eventCount &&
           sequencer.events[sequencer.nextEventIdx]!!.sampleTime < currentSampleOffset) {
        sequencer.nextEventIdx++
    }
    
    while (renderPos < frames) {
        // Find next event within this chunk
        var nextEvent: SequencerEvent? = null
        if (sequencer.nextEventIdx < sequencer.eventCount) {
            val ev = sequencer.events[sequencer.nextEventIdx]!!
            if (ev.sampleTime >= currentSampleOffset && ev.sampleTime < chunkEnd) {
                nextEvent = ev
            }
        }
        
        if (nextEvent == null) {
            // No more events in this chunk, render remaining frames
            renderSegment(buffer, renderPos, frames - renderPos)
            break
        }
        
        // Render up to event time
        val eventOffset = (nextEvent.sampleTime - currentSampleOffset).toInt()
        if (eventOffset > renderPos) {
            renderSegment(buffer, renderPos, eventOffset - renderPos)
            renderPos = eventOffset
        }
        
        // Process event
        when (nextEvent.type) {
            SequencerEvent.FM_ON -> {
                val ch = nextEvent.channel
                if (ch in fm.indices) {
                    fm[ch].noteOn(nextEvent.midi, nextEvent.attack, nextEvent.decay, 
                                  nextEvent.sustain, nextEvent.release)
                    fm[ch].noteGain = nextEvent.velocity
                    fmActiveNoteId[ch] = nextEvent.noteId
                }
            }
            SequencerEvent.FM_OFF -> {
                val ch = nextEvent.channel
                if (ch in fm.indices && fmActiveNoteId[ch] == nextEvent.noteId) {
                    fm[ch].noteOff()
                    fmActiveNoteId[ch] = -1
                }
            }
            // Similar for SSG_ON, SSG_OFF, DRUM...
        }
        
        sequencer.nextEventIdx++
    }
}
```

### 4.4 Stereo Buffer Implementation

```kotlin
companion object {
    const val MAX_FRAMES_PER_CHUNK = 1024
}

private val leftScratch = FloatArray(MAX_FRAMES_PER_CHUNK)
private val rightScratch = FloatArray(MAX_FRAMES_PER_CHUNK)

fun renderStereo(stereoBuffer: FloatArray, frames: Int) {
    var offset = 0
    var remaining = frames
    while (remaining > 0) {
        val chunkFrames = minOf(remaining, MAX_FRAMES_PER_CHUNK)
        renderStereoChunk(stereoBuffer, offset, chunkFrames)
        offset += chunkFrames * 2
        remaining -= chunkFrames
    }
}

private fun renderStereoChunk(stereoBuffer: FloatArray, startFrame: Int, frames: Int) {
    leftScratch.fill(0f)
    rightScratch.fill(0f)
    
    // Render each voice into left/right scratch buffers
    var i = 0
    while (i < fm.size) {
        val pan = fm[i].getPan()
        val (leftGain, rightGain) = AudioLaws.panToGains(pan)
        
        // Render to mono temp, then pan to left/right
        tempMonoBuffer.fill(0f)
        fm[i].render(tempMonoBuffer, frames, sampleRate, mixer.fmGain)
        
        var j = 0
        while (j < frames) {
            leftScratch[j] += tempMonoBuffer[j] * leftGain
            rightScratch[j] += tempMonoBuffer[j] * rightGain
            j++
        }
        i++
    }
    
    // Similar for SSG and drums...
    
    // Interleave to stereo buffer
    var j = 0
    while (j < frames) {
        val stereoIdx = (startFrame + j) * 2
        stereoBuffer[stereoIdx] = leftScratch[j]
        stereoBuffer[stereoIdx + 1] = rightScratch[j]
        j++
    }
}
```

### 4.5 Arrangement Data Format

**synth-chime (bell arpeggio):**
```kotlin
"synth-chime" -> {
    val e = 333  // eighth note at 90 BPM = 333ms
    val q = 667  // quarter note
    ArrangementLanes(
        lead = Lane(listOf(
            ToneSpec(466f, 0, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),      // Bb4
            ToneSpec(554f, e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),      // Db5
            ToneSpec(698f, 2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),    // F5
            ToneSpec(554f, 2*e+q, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),  // Db5
            ToneSpec(466f, 2*e+q+e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50),// Bb4
            ToneSpec(349f, 2*e+q+2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.3f, 50) // F4
        ), TimbreRef.FM_BELL_ZUN1),
        harmony = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE),
        bass = Lane(listOf(
            ToneSpec(116f, 0, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30),      // Bb2
            ToneSpec(116f, q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30),      // Bb2
            ToneSpec(174f, 2*q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.3f, 30)     // F3
        ), TimbreRef.FM_BASS_ZUN1),
        percussion = Lane(listOf(
            ToneSpec(8000f, q, e, 0.3f * volume, "hat", false),
            ToneSpec(8000f, 2*q+q, e, 0.3f * volume, "hat", false)
        ), TimbreRef.DRUM_HAT),
        tempoBpm = 90f,
        keyRootMidi = 70
    )
}
```

**synth-victory (Zelda item get):**
```kotlin
"synth-victory" -> {
    val e = 250  // eighth note at 120 BPM = 250ms
    val q = 500  // quarter note
    val h = 1000 // half note
    ArrangementLanes(
        lead = Lane(listOf(
            ToneSpec(523f, 0, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),     // C5
            ToneSpec(659f, e, e, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),     // E5
            ToneSpec(784f, 2*e, q, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 50),   // G5
            ToneSpec(1046f, 2*e+q, h, 0.7f * volume, "pulse25", true, 5, 30, 0.5f, 100) // C6
        ), TimbreRef.FM_LEAD_ZUN1),
        harmony = Lane(emptyList(), TimbreRef.SSG_HARMONY_SQUARE),
        bass = Lane(listOf(
            ToneSpec(130f, 0, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),     // C3
            ToneSpec(130f, q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),     // C3
            ToneSpec(196f, 2*q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30),   // G3
            ToneSpec(261f, 2*q+q, q, 0.5f * volume, "pulse12", true, 5, 20, 0.5f, 30)  // C4
        ), TimbreRef.FM_BASS_ZUN1),
        percussion = Lane(listOf(
            ToneSpec(-1f, 0, q, 0.5f * volume, "kick", false),
            ToneSpec(3000f, 2*q, q, 0.4f * volume, "snare", false)
        ), TimbreRef.DRUM_HAT),
        tempoBpm = 120f,
        keyRootMidi = 60
    )
}
```

---

## 5. Testing Strategy

### 5.1 Unit Tests

**Per-operator envelope test:**
```kotlin
@Test
fun perOperatorEnvelopesAreUsed() {
    val synth = OpnaLikeSynthesizer(44100)
    synth.fm[0].applyPatch(Patches.ZunLead1)
    synth.fm[0].noteOn(69)  // A4, null parameters
    
    // Render and verify output has timbral character
    val buffer = FloatArray(44100)
    synth.render(buffer, 44100)
    
    // Light signal test: RMS should be in audible range
    val rms = calculateRms(buffer)
    assertTrue(rms > 0.05f, "Output should be audible")
    
    // Peak/rms ratio should indicate FM richness (not pure sine)
    val peak = calculatePeak(buffer)
    val ratio = peak / rms
    assertTrue(ratio > 1.5f, "FM modulation should produce rich harmonics")
}
```

**Polyphony test:**
```kotlin
@Test
fun overlappingNotesAllPlay() {
    val synth = OpnaLikeSynthesizer(44100)
    val seq = OpnaSequencer(44100, 120f)
    
    // Add 4 overlapping notes
    seq.noteFmRaw(0, 60, 0, 44100, 0.7f, null, null, null, null)      // C4, 0-1s
    seq.noteFmRaw(0, 64, 5000, 44100, 0.7f, null, null, null, null)   // E4, 0.11s-1.11s
    seq.noteFmRaw(0, 67, 10000, 44100, 0.7f, null, null, null, null)  // G4, 0.23s-1.23s
    seq.noteFmRaw(0, 72, 15000, 44100, 0.7f, null, null, null, null)  // C5, 0.34s-1.34s
    
    seq.sortEvents()
    synth.fm[0].applyPatch(Patches.ZunLead1)
    
    val buffer = FloatArray(44100 * 2)
    synth.render(buffer, buffer.size, seq, 0L)
    
    // Light signal test: RMS should be higher than single note
    val rms = calculateRms(buffer)
    assertTrue(rms > 0.1f, "Multiple overlapping notes should produce louder output")
    
    // Zero-crossing count should be higher than single note (more harmonic content)
    val crossings = countZeroCrossings(buffer)
    assertTrue(crossings > 1000, "Overlapping notes should produce complex waveform")
}
```

**Stereo test:**
```kotlin
@Test
fun stereoPanningWorks() {
    val synth = OpnaLikeSynthesizer(44100)
    synth.fm[0].applyPatch(Patches.ZunLead1.copy(pan = 1))  // pan left
    synth.fm[0].noteOn(69)
    
    val buffer = FloatArray(44100 * 2)  // stereo
    synth.renderStereo(buffer, 44100)
    
    // Light signal test: left channel has signal, right channel is silent
    var leftSum = 0.0
    var rightSum = 0.0
    for (i in 0 until 44100) {
        leftSum += abs(buffer[i * 2])
        rightSum += abs(buffer[i * 2 + 1])
    }
    
    assertTrue(leftSum > 100.0, "Left channel should have signal")
    assertTrue(rightSum < 1.0, "Right channel should be silent")
}
```

**Testing approach:**
- Light frequency/signal tests: RMS, peak, zero-crossing count, peak/rms ratio
- No FFT-based musical quality tests
- Focus on signal presence and basic correctness
- Manual listening tests for subjective quality

### 5.2 Manual Testing

**Build and deploy:**
```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :app:assembleDebug
```

**Test each sound:**
1. synth-chime → should hear sparkling bell arpeggio
2. synth-victory → should hear ascending fanfare (C-E-G-C)
3. synth-bad-apple → should hear full arrangement with proper timbre
4. synth-senbonzakura → should hear full arrangement with proper timbre
5. synth-bad-apple-LotusLandStory → should hear full 70-second arrangement

**Verify:**
- All notes play (no skipped notes)
- Timbre matches target sound
- Stereo panning works (if using headphones)
- No clicks or pops
- Volume is reasonable

---

## 6. Success Criteria

### 6.1 Functional Requirements

1. **Polyphony:** Overlapping notes on the same channel all play
2. **Per-operator envelopes:** Patches use their per-operator envelope settings
3. **Arrangements:** All 6 sound keys play proper melodies
4. **Stereo:** Stereo panning works correctly

### 6.2 Quality Requirements

1. **Timbre:** FM patches sound like OPNA-style instruments
2. **Volume:** Output is audible but not clipping
3. **No artifacts:** No clicks, pops, or glitches
4. **Determinism:** Same input produces same output

### 6.3 Test Requirements

1. All existing tests pass
2. New polyphony test passes
3. New per-operator envelope test passes
4. New stereo test passes
5. Build succeeds: `compileKotlinMetadata` + `compileDebugKotlinAndroid` + `assembleDebug`

---

## 7. Risk Assessment

### 7.1 High Risk

**Polyphonic sequencer rewrite:**
- Risk: Breaking existing arrangements that work
- Mitigation: Keep backwards compatibility, test thoroughly
- Fallback: Keep old sequencer as fallback, add new one as option

### 7.2 Medium Risk

**FM patch tuning:**
- Risk: Patches may not sound good, require extensive iteration
- Mitigation: Start with known-good values from OPNA references
- Fallback: Keep current patches, tune later

### 7.3 Low Risk

**Per-operator envelope fix:**
- Risk: Breaking existing behavior
- Mitigation: Keep backwards compatibility (override if any parameter provided)

**Arrangement rewrites:**
- Risk: New arrangements may not sound good
- Mitigation: Use simple patterns, test manually
- Fallback: Keep old arrangements, mark as "legacy"

---

## 8. Resolved Questions

1. **Sequencer event limit:** Increased to 4096 events to support more complex arrangements.

2. **NoteId overflow:** Wrap from `Int.MAX_VALUE` back to `1`. `-1` is reserved for inactive, `0` is not used. Theoretical wrap collision after 2 billion notes is acceptable; simpler than using Long.

3. **Stereo buffer size:** Fixed-size buffers (`MAX_FRAMES_PER_CHUNK = 1024`). No dynamic allocation in render loops. Large requests are split into multiple chunks. This aligns with "CARMACK BUT ZUN" philosophy.

4. **Patch tuning:** No patch editor UI. Manual tuning only. Document values in code comments.

5. **Testing:** Light frequency/signal tests (RMS, peak, zero-crossing, peak/rms ratio). No FFT-based musical quality tests. Focus on signal presence and basic correctness.

---

## 9. Implementation Order

1. **Phase 1:** Fix per-operator envelopes (quick win, low risk)
2. **Phase 2:** Rewrite synth-chime and synth-victory arrangements (quick win, low risk)
3. **Phase 3:** Tune FM patches (iterative, medium risk)
4. **Phase 4:** Implement polyphonic sequencer (complex, high risk)
5. **Phase 5:** Add diagnostic tests (verification)

**Estimated time:**
- Phase 1: 30 minutes
- Phase 2: 1 hour
- Phase 3: 2-3 hours (iterative)
- Phase 4: 3-4 hours
- Phase 5: 1 hour

**Total:** 7-10 hours

---

## 10. Notes

- This plan does not address SSG enhancements (YM2149-style). That's a separate task.
- This plan does not address LFO/AMS/PMS integration. The infrastructure is in place, but not wired up.
- This plan does not address rate-based EG (OPN_RATE mode). The infrastructure is in place, but patches use LEGACY_ADSR.
- The polyphonic sequencer is needed for overlapping notes, but most arrangements use sequential notes. Phase 2 is a temporary workaround.

---

**Next steps:**
1. Review this plan
2. Begin Phase 1 implementation
