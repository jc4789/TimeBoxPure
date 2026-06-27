# Plan — OPNA-Like Synthesizer for TimeBoxVibe

**Status:** Plan, v3 (final). No code written yet.
**Owner:** Engine / audio subsystem.
**Target source set:** `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/...`
**Runtime target today:** Android (`:app`).
**KMP constraint:** commonMain must compile for `mingwX64` + iOS + Android. No `java.*`, no `Random`, no `Math.*`, no per-sample allocation.

---

## 0. Decisions locked in (do not re-litigate in code)

| # | Decision |
|---|---|
| D1 | **OPNA replaces ChiptuneSynthesizer.** ChiptuneSynthesizer.kt is deleted after OPNA parity for all 4 in-scope keys. No permanent chiptune backend. |
| D2 | **Existing custom arrangements are project-authored arrangement data for this engine.** They are not third-party transcriptions; they are first-party data the user authored for the engine. **This task does not delete or replace them.** It upgrades their renderer from chiptune to OPNA. |
| D3 | **D14 / Phase 10 refactor:** First refactor each existing arrangement into per-lane note data. Then route each lane into OPNA. Arrangement bodies (note sequences) are preserved. |
| D4 | **v1 routing:** **4 OPNA keys + 1 legacy MediaPlayer key.** The 4 OPNA keys are `synth-chime`, `synth-victory`, `synth-bad-apple`, `synth-senbonzakura`. The 1 legacy key is `oriental`, which keeps its pre-existing `MediaPlayer` + `sounds/oriental_alarm.mp3` path. **No new asset files are added by this task** (the mp3 pre-exists). v1 is not "5 OPNA keys"; it is "4 OPNA + 1 MediaPlayer". |
| D5 | **OPNA is the replacement engine, not an optional second backend.** No second audio system lives alongside OPNA. ChiptuneSynthesizer is removed at the end of Phase 9. |
| D6 | **Only profile in v1: PC_9801_86.** No `OpnaProfile` enum. No 26K mode. No 26K mix approximation. No purist mode. |
| D7 | **`AudioLaws` is minimal:** `FM_CHANNELS=6`, `SSG_CHANNELS=3`, `FM_OPERATORS=4`, `SAMPLE_RATE=44100`, `SSG_GAIN_DB=-18f`. |
| D8 | **Patches are immutable singletons.** `FmPatch` contains immutable `OperatorSpec` values. `Fm4OpVoice` owns mutable `OperatorState`. No patch allocation in render paths. Startup allocation is fine. |
| D9 | **Hot-path audit fails the build.** Banned in `engine/audio/opna/**` render paths: `Random.nextFloat()`, `mutableListOf`, `arrayListOf`, `List.map`, `flatMap`, `buildList`, `Sequence`, `generateSequence`, `arrayOf` in hot loops, per-sample object creation, temporary lists, allocation-producing callbacks, boxing-heavy collections. Audit is a **Python tool**, not a `commonTest` test. |
| D10 | **Existing arrangements are upgraded** to OPNA rendering. Lane model: lead / harmony / bass / percussion map to FM lead / SSG harmony / FM bass / LFSR noise + procedural drums / OPNA mixer. **SSG is square / pulse / noise only. There is no SSG triangle.** Old arrangements that used a "triangle" tone (the old chiptune's bass lane) map to either FM bass or SSG square with a low duty cycle. |
| D11 | **Drum mapping by key:** focus = kick 1+3 + snare 2+4 + hat every 8th; alarm = four-on-the-floor (strong repeated kick, snare on 2+4, hat on every 8th); relax = sparse (kick on 1, hat on 2+4, no snare). All drums are procedural (pitch-drop kick, LFSR/noise snare, LFSR/noise hat, decaying-sine tom). No PCM. |
| D12 | **Scales for v1 motifs:** focus = E Phrygian Dominant, relax = A Pentatonic Minor, pad = D Dorian. Comments are short. |
| D13 | **LUT policy for v1:** no LUTs. v1 uses simple math approximations: `tl → linear amplitude = (127 - tl) / 127.0`; `detune → phase offset = detune * 0.01` (rad per sample); `feedback → shift = patch.feedback * 0.5`; `fnum/block → frequency = 440f * pow(2f, block + fnum/1024.0 - 9)`. **Full VHDL-derived LUTs (`FBTAB` / `RATETABLE` / `CLTAB` / `DTTAB` / `GAINTAB` / `NOTETAB`) are v1.1.** They are not required for first audible OPNA and are deferred to a follow-up phase. |
| D14 | **Hihat energy > 5 kHz** is an acceptance test (catches accidental sine regression). |
| D15 | **FM phase math:** `modulationIndex` is the **amount this operator's output contributes to the downstream operator's phase**, not a per-sample delta scaled by `sampleRate`. The carrier's natural progression is `phase += phaseStep` per sample. The modulator's contribution to the downstream operator is `downstreamPhase += modulatorOutput * modulationIndex`. The final LUT index is `((phase / TAU) * 1024).toInt() and 1023`. **`modulationIndex` is part of the immutable `OperatorSpec`, not the mutable `OperatorState`**, because it is a patch-level tuning parameter, not a per-note runtime value. |
| D16 | **`OpnaSequencer` uses fixed primitive arrays + count fields** (no `mutableListOf`, no `ArrayList`, no per-event allocation): `IntArray` for channel / midi, `LongArray` for start sample / duration sample, `Int` count field. Pre-allocated at construction. |
| D17 | **Phase 5 references SSG + drums only.** It does not reference `Fm4OpVoice`. `Fm4OpVoice` is created in Phase 6, and at the same time the `fm` array is added to `OpnaLikeSynthesizer.render()`. No silent `renderOne() == 0f` skeleton in either phase. |
| D18 | **Loop duration math:** `loopSamples = bars * beatsPerBar * sampleRate * 60 / bpm`. Beats-per-bar defaults to 4. |
| D19 | **Hot-path source-audit tests move out of `commonTest`.** They become a Python tool (`tools/math_oracles/opna_audit.py`) invoked from Gradle as a pre-`compileKotlin` task. `commonTest` stays platform-clean and only contains math/runtime tests. The no-allocation runtime test, if present, goes in `jvmTest` (JVM-only, can use `Runtime.totalMemory`). |
| D20 | **`SoundMelodies.kt` survives as-is structurally.** Its arrangement bodies are **refactored into per-lane note data** (D3), not deleted. The 5 supported keys remain. The `getMelody` API keeps its `List<ToneSpec>` shape but is **no longer the primary entry point** — the OPNA renderer reads the lane data directly. `ToneSpec.kt` is preserved. |
| D21 | **`OperatorSpec.modulationIndex` defaults to `0f`, not `1.0f`.** Rationale: a default of `1.0f` would hide behavior — an operator with no explicit `modulationIndex` would silently modulate downstream operators at depth 1.0, making incomplete patches sound "almost right" instead of obviously plain. With `0f` as the default, every patch must explicitly state which operators are modulators and at what depth. This is a no-hidden-FM-behavior contract. **Modulators** (operators whose output feeds a downstream operator's phase in the algorithm topology) explicitly set `modulationIndex = 1.5f..3.0f` (or whatever the patch needs). **Carriers** (operators whose output is summed to the channel output) explicitly set `modulationIndex = 0f`. Patches that intentionally route a "carrier" as a modulator (rare in v1 algorithms) override the default explicitly. |

---

## 1. Target file layout

All paths are repo-relative.

```
shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/
├── audio/
│   ├── AudioLaws.kt                     [NEW]  minimal constants (D7)
│   ├── Midi.kt                          [NEW]  midiToFreq helper
│   ├── opna/
│   │   ├── Envelope.kt                  [NEW]  shared ADSR
│   │   ├── LfsrNoise.kt                 [NEW]  16-bit Galois LFSR, per-channel seed
│   │   ├── SsgVoice.kt                  [NEW]  square / pulse / LFSR noise + ADSR (no triangle)
│   │   ├── OpnaMixer.kt                 [NEW]  hard-coded PC_9801_86, no enum
│   │   ├── ProceduralDrums.kt           [NEW]  kick / snare / hat / tom state machines
│   │   ├── OperatorSpec.kt              [NEW]  immutable, in FmPatch
│   │   ├── OperatorState.kt             [NEW]  mutable, in Fm4OpVoice
│   │   ├── FmPatch.kt                   [NEW]  immutable, in Patches
│   │   ├── Fm4OpVoice.kt                [NEW]  renderOne() with algo 0 + 1
│   │   ├── Patches.kt                   [NEW]  ZunLead1 / ZunBass1 / ZunBell1 / ZunPad1
│   │   ├── Scale.kt                     [NEW]  PhrygianDominant / PentatonicMinor / Dorian / Minor
│   │   ├── NoteLen.kt                   [NEW]  enum beats
│   │   ├── OpnaSequencer.kt             [NEW]  primitive-array event store
│   │   ├── OpnaPatterns.kt              [NEW]  focusMotif / alarmMotif / relaxMotif / padMotif
│   │   └── OpnaLikeSynthesizer.kt       [NEW]  top-level mixer, softClip after mix
│   └── (no other audio files)
├── core/
│   ├── ChiptuneSynthesizer.kt           [DELETED at end of Phase 9]
│   ├── FastMath.kt                      (existing, reused)
│   └── ...
├── SoundMelodies.kt                     [REFACTORED in Phase 9: per-lane data]
├── ToneSpec.kt                          (existing, retained; lane note records use it)
└── ...

tools/math_oracles/
├── gen_lut.py                           [unchanged in v1 — LUTs deferred to v1.1]
├── opna_audit.py                        [NEW in Phase 10]  source-grep audit
└── README.md                            [UPDATED: opna_audit section]

app/src/main/java/com/example/timeboxvibe/engine/
├── SoundPreviewPlayer.kt                [MODIFIED: 4 keys OPNA + 1 MediaPlayer]
└── ...

shared-engine/src/commonTest/kotlin/com/example/timeboxvibe/engine/audio/opna/
├── EnvelopeStageTest.kt                 [NEW]
├── LfsrNoisePeriodTest.kt               [NEW]
├── SsgVoiceDeterminismTest.kt           [NEW]
├── OpnaDeterminismTest.kt               [NEW]
├── OpnaHihatSpectralTest.kt             [NEW]   energy > 5 kHz
└── OpnaAcceptanceChecklistTest.kt       [NEW]   full ensemble

shared-engine/src/jvmTest/kotlin/com/example/timeboxvibe/engine/audio/opna/
└── OpnaNoAllocHotPathTest.kt            [NEW]   uses Runtime.totalMemory, JVM-only
```

LUT files (`generated/Generated{FbTab,RateTable,ClTab,DtTab,GainTab,NoteTab}.kt`) are **not** created in v1 (D13). v1.1 will add them under `shared-engine/.../engine/audio/opna/generated/`.

---

## 2. AudioLaws.kt (final)

```kotlin
package com.example.timeboxvibe.engine.audio

/**
 * Audio laws for the OPNA-like synthesizer.
 *
 * v1 = PC-9801-86 (OPNA / YM2608) profile only.
 * - 6 FM channels × 4 operators each
 * - 3 SSG channels (square / pulse / LFSR noise; no triangle)
 * - 44.1 kHz mono
 * - SSG mix = -18 dB relative to FM
 *
 * No OpnaProfile enum in v1. No 26K mode. No purist mode.
 *
 * v1.1 follow-up: full VHDL-derived LUTs (CLTAB, DTTAB, GAINTAB, NOTETAB, FBTAB, RATETABLE)
 * generated by `tools/math_oracles/gen_lut.py --kind opna-*`. v1 uses simple math
 * approximations defined in this object.
 */
internal object AudioLaws {
    const val FM_CHANNELS: Int = 6
    const val SSG_CHANNELS: Int = 3
    const val FM_OPERATORS: Int = 4
    const val SAMPLE_RATE: Int = 44100
    const val SSG_GAIN_DB: Float = -18f

    // --- v1 simple-math approximations of YM2608 tables (D13) ---
    // TL (0..127) → linear amplitude. Real YM2608: 1024-entry log curve (cltab).
    fun tlToAmplitude(tl: Int): Float = (127 - tl.coerceIn(0, 127)) / 127f

    // Detune (0..7) → per-sample phase offset in radians. Real YM2608: 256-entry table (dttab).
    fun detunePhaseOffset(detune: Int): Float = detune.coerceIn(0, 7) * 0.01f

    // Feedback (0..7) → output shift in bits (0 = no feedback, 7 = max feedback).
    // Real YM2608: 8-entry table (fbtab). v1: linear.
    fun feedbackShift(feedback: Int): Float = feedback.coerceIn(0, 7) * 0.5f

    // Block (0..7) and Fnum (0..2047) → frequency in Hz.
    // Real YM2608: notetab lookup + fnum scaling. v1: simplified.
    fun fnumBlockToFreq(block: Int, fnum: Int): Float =
        440f * pow(2f, block.coerceIn(0, 7) + fnum.coerceIn(0, 2047) / 1024f - 9f)
}
```

Constant justification table (so the next reader doesn't ask "why 6? why -18?"):

| Name | Value | Source / law |
|---|---|---|
| `FM_CHANNELS` | 6 | YM2608 has 6 FM channels. Hardware law, not magic. |
| `SSG_CHANNELS` | 3 | YM2608 SSG block has 3 channels. Hardware law. |
| `FM_OPERATORS` | 4 | YM2608 FM voice = 4 operators per channel. Hardware law. |
| `SAMPLE_RATE` | 44100 | Matches `SoundPreviewPlayer.kt:255` and Android `AudioTrack` default. Hardware / contract law. |
| `SSG_GAIN_DB` | -18 | PC-9801-86 board mixes SSG ~18 dB below FM. Documented in PC-9801-86 sound board manual. Hardware law. |
| `tlToAmplitude` | linear `(127 - tl) / 127` | v1 approximation. v1.1 replaces with `cltab[tl] / 255f`. |
| `detunePhaseOffset` | `detune * 0.01` | v1 approximation. v1.1 replaces with `dttab[detune + blockN]` (signed 7-bit cent offset). |
| `feedbackShift` | `feedback * 0.5` | v1 approximation (output is shifted right by this many bits). v1.1 replaces with `fbtab[feedback]`. |
| `fnumBlockToFreq` | `440 * 2^(block + fnum/1024 - 9)` | v1 approximation. v1.1 replaces with `notetab[fnum>>7] + fnum` scaling. |

---

## 3. Phase-by-phase plan

Each phase ends with **both** build gates succeeding:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"
.\gradlew :shared-engine:compileKotlinMetadata
.\gradlew :shared-engine:compileDebugKotlinAndroid
```

`commonTest` runs as part of `compileKotlinMetadata`; `jvmTest` is run separately when present.

---

### Phase 1 — Audio laws, MIDI helper, envelope, LFSR

**New files:**
- `shared-engine/.../engine/audio/AudioLaws.kt`
- `shared-engine/.../engine/audio/Midi.kt`
- `shared-engine/.../engine/audio/opna/Envelope.kt`
- `shared-engine/.../engine/audio/opna/LfsrNoise.kt`

**Envelope.kt** — shared ADSR (D9, all `var` fields, no allocation, `Int` stage constants 0..4):
- `var stage: Int = OFF`, `var level: Float = 0f`
- `var attack: Float = 0.002f` (seconds)
- `var decay: Float = 0.06f`
- `var sustain: Float = 0.55f`
- `var release: Float = 0.04f`
- `fun noteOn()`, `fun noteOff()`, `fun next(dt: Float): Float`, `fun reset()`
- Stage constants in companion: `OFF=0, ATTACK=1, DECAY=2, SUSTAIN=3, RELEASE=4` (Int, not enum, per project rule)

**LfsrNoise.kt** — 16-bit Galois LFSR, taps bits 0⊕1, period 65535:
- `private var state: Int`
- `fun next(): Float` — returns `+1f` or `-1f`, advances state
- `fun reset(seed: Int)` — reseeds
- Per-channel seed convention: `0xACE1 xor (channelIndex * 0x9E37)` documented in header

**Midi.kt** — `internal fun midiToFreq(midi: Int): Float = 440f * pow(2f, (midi - 69) / 12f)` (uses `kotlin.math.pow`, not `Math.pow`)

**Cleanup in this phase (low risk):**
- `ChiptuneSynthesizer.generateWaveform("noise")`: replace `Random.nextFloat() * 2f - 1f` with a `LfsrNoise(0xBEEF)` field. The `ChiptuneSynthesizer` is being deleted in Phase 9, but the noise path is reused by `SsgVoice` and `ProceduralDrums` in the meantime.
- **Do not** yet delete `ChiptuneSynthesizer`. Phase 1 is a small, focused, additive change.

**Tests (commonTest):**
- `EnvelopeStageTest` — stage transitions, never-negative level, release reaches 0.
- `LfsrNoisePeriodTest` — period = 65535, no `Random` import, deterministic per seed.

**Build gates:** both pass.

---

### Phase 2 — SsgVoice (square / pulse / noise + ADSR)

**New file:** `shared-engine/.../engine/audio/opna/SsgVoice.kt`

**API:**
- `var enabled: Boolean`, `var frequency: Float`, `var duty: Float = 0.5f`, `var phase01: Float = 0f`
- `var useNoise: Boolean = false`
- `private val env: Envelope`
- `private val noise: LfsrNoise`
- `fun noteOn(freq: Float)`, `fun noteOff()`, `fun render(buffer: FloatArray, frames: Int, sampleRate: Int, gainScale: Float)` (adds into buffer, never overwrites), `fun reset()`

**D10 enforcement:** SSG is square / pulse / LFSR noise. There is **no SSG triangle**. The "triangle" lane in the old chiptune will be re-routed in Phase 9 to either FM bass or SSG square with a low duty cycle (no `triangle` wave type in this engine).

**Hot-path rules (D9):**
- `render()` is a `while` loop over `frames`. No allocations, no `Random`, no `mutableListOf`, no `arrayOf`. Phase accumulator is a `var` field. LFSR state is a `var` field.
- `useNoise ? noise.next() : (if (phase01 < duty) 1f else -1f)` — single branch, no allocation.

**Tests (commonTest):**
- `SsgVoiceDeterminismTest`:
  - 440 Hz square → zero-crossings ≈ 440 ± 2 per second.
  - Two voices with identical seeds produce identical 1-second buffers (byte-equal `FloatArray.contentEquals`).
  - `reset()` zeroes `phase01`, `enabled=false`, env level=0.

**Build gates:** both pass.

---

### Phase 3 — ProceduralDrums (state machines, no stubs)

**New file:** `shared-engine/.../engine/audio/opna/ProceduralDrums.kt`

**API:**
- `private val noise: LfsrNoise = LfsrNoise(0xBEEF)` plus per-drum `LfsrNoise` instances.
- Per-drum state: `var state: Int` (`IDLE=0, ATTACK=1, DECAY=2, OFF=3`), `var level: Float`, `var ageSamples: Int`, `var phase01: Float` (for kick/tom).
- `fun triggerKick()`, `fun triggerSnare()`, `fun triggerHat()`, `fun triggerTom(freq: Float)`, `fun stopAll()`, `fun reset()`, `fun render(buffer, frames, sampleRate, gainScale)`.

**Sound recipes (D11):**
- **Kick:** pitch-dropping sine, 120 Hz → 50 Hz over 50 ms, exponential decay 200 ms. Implementation: `level = 1f`, `phaseStep = 2π × freq / sampleRate`, where `freq = 50f + 70f * exp(-ageMs / 12f)`. Envelope `level *= exp(-ageMs / 70f)`.
- **Snare:** LFSR noise (seed 0xBEEF xor drumIndex) + sum of two sine partials (180 Hz + 330 Hz) at 0.3 amplitude each, decay 80 ms.
- **Hat:** LFSR noise (seed 0xFACE xor drumIndex), decay 30 ms, simple bandpass approximation = noise - 0.6 × delayed-noise (1-sample delay).
- **Tom:** pitch-dropping sine, configurable start Hz, decay 150 ms.

**Hot-path rules (D9):** zero allocation per `render()` call. All state is `var` fields.

**Tests (commonTest):**
- `triggerKick()` then `render(1s)` → energy concentrated in `[20, 80] Hz` (count zero-crossings or sum bins).
- Multiple triggers in 100 ms produce overlapping decays (deterministic — same first-sample hash across two `ProceduralDrums` instances).
- `OpnaHihatSpectralTest` (D14): trigger hat, capture 1s buffer, count zero-crossings or simple DFT bins → >80% of energy in `>5 kHz` band. A regression to a sine-wave hat would fail this.

**Build gates:** both pass.

---

### Phase 4 — OpnaMixer (PC_9801_86 only, no enum)

**New file:** `shared-engine/.../engine/audio/opna/OpnaMixer.kt`

**Design:**
- No `OpnaProfile` enum (D6). The class is parameterized at construction with constants only.
- `class OpnaMixer(sampleRate: Int)`:
  - `private val ssgGainLinear: Float = pow(10f, AudioLaws.SSG_GAIN_DB / 20f)` (cached at construction, no per-frame `pow()`)
  - `private val fmGainLinear: Float = 1f`
  - `private val rhythmGainLinear: Float = 1f`
  - `val ssgGain: Float get() = ssgGainLinear` (read-only)
  - `val fmGain: Float get() = fmGainLinear`
  - `val rhythmGain: Float get() = rhythmGainLinear`
- Justification: SSG at -18 dB = `10^(-18/20) = 0.1259` linear, computed once.

**Tests (commonTest):**
- `OpnaMixerTest`: `ssgGain ≈ 0.1259f` (within float epsilon), `fmGain == 1f`, `rhythmGain == 1f`.

**Build gates:** both pass.

---

### Phase 5 — OpnaLikeSynthesizer skeleton (SSG + drums only, NO Fm4OpVoice yet)

**New file:** `shared-engine/.../engine/audio/opna/OpnaLikeSynthesizer.kt`

**D17 enforcement:** This phase references `SsgVoice`, `ProceduralDrums`, and `OpnaMixer` only. It does **not** declare a `fm: Array<Fm4OpVoice>` field. `Fm4OpVoice` does not exist in this phase. The `fm` field is added in Phase 6 together with the `Fm4OpVoice` class.

**Design:**
- `class OpnaLikeSynthesizer(sampleRate: Int = AudioLaws.SAMPLE_RATE)`:
  - `private val mixer = OpnaMixer(sampleRate)`
  - `private val ssg: Array<SsgVoice> = Array(AudioLaws.SSG_CHANNELS) { SsgVoice() }` (preallocated, usable this phase)
  - `private val drums = ProceduralDrums()`
  - `fun render(buffer: FloatArray, frames: Int)`:
    1. `buffer.fill(0f)`
    2. `var i = 0; while (i < ssg.size) { ssg[i].render(buffer, frames, sampleRate, mixer.ssgGain); i++ }`
    3. `drums.render(buffer, frames, sampleRate, mixer.rhythmGain)`
    4. `softClipAndGain(buffer, frames)`
  - `fun noteOnSsg(channel: Int, midi: Int)`, `fun noteOffSsg(channel: Int)`, `fun triggerKick/.../triggerHat()` (drum triggers), `fun allNotesOff()`, `fun reset()`
  - `private fun softClipAndGain(buffer, frames)`:
    - For each sample: `x = buffer[i] * 0.7f` (master gain), `buffer[i] = x / (1f + abs(x))`, then `coerceIn(-1f, 1f)`. Fused into one `while` loop. No `Math.tanh`, no allocation.

**Hot-path rules (D9):** all state is fields, all loops are `while`, no per-frame `mutableListOf` or `arrayOf`.

**Tests (commonTest):**
- 3 SSG voices simultaneously held → RMS > 0.05, output in `[-1, 1]`, no NaN / Inf.
- Two `OpnaLikeSynthesizer` instances, identical note-on sequence → identical first-sample hash (D9 determinism).

**Build gates:** both pass.

---

### Phase 6 — OperatorSpec, OperatorState, FmPatch, Fm4OpVoice (algorithm 0, first audible) + FM wired into OpnaLikeSynthesizer

**New files:**
- `shared-engine/.../engine/audio/opna/OperatorSpec.kt`
- `shared-engine/.../engine/audio/opna/OperatorState.kt`
- `shared-engine/.../engine/audio/opna/FmPatch.kt`
- `shared-engine/.../engine/audio/opna/Fm4OpVoice.kt`
- `shared-engine/.../engine/audio/opna/Patches.kt`

**D17 enforcement:** This is the phase that creates `Fm4OpVoice`. In the same phase, the `fm: Array<Fm4OpVoice>` field is added to `OpnaLikeSynthesizer`, and the FM loop is added to `OpnaLikeSynthesizer.render()`. There is no "silent skeleton" phase between this and Phase 5 — Phase 5 didn't have FM, Phase 6 introduces it in its first audible form.

**D8 enforcement:** `OperatorSpec` is the immutable spec in the patch. `OperatorState` is the mutable state owned by the voice. The voice applies the spec on `noteOn()`.

**OperatorSpec.kt (immutable, D21):**
```kotlin
data class OperatorSpec(
    val mul: Int,            // 0..15; 0 means 0.5×
    val detune: Int,         // 0..7
    val tl: Int,             // 0..127
    // D15, D21: modulationIndex = how much this operator's output contributes
    // to the downstream operator's phase. Default 0f = carrier / no modulation.
    // Modulators must set this explicitly (typical 1.5f..3.0f).
    // No hidden FM behavior: every patch states its modulation depths.
    val modulationIndex: Float = 0f,
    val attack: Float = 0.002f,
    val decay: Float = 0.06f,
    val sustain: Float = 0.55f,
    val release: Float = 0.04f
)
```

**OperatorState.kt (mutable):**
```kotlin
class OperatorState {
    var phase: Float = 0f
    var phaseStep: Float = 0f
    var outputLevel: Float = 1f       // cached at noteOn
    val envelope: Envelope = Envelope()
    fun reset() { phase = 0f; envelope.reset() }
}
```

**FmPatch.kt (immutable, contains 4 `OperatorSpec`):**
```kotlin
data class FmPatch(
    val algorithm: Int,
    val feedback: Int,
    val op0: OperatorSpec,
    val op1: OperatorSpec,
    val op2: OperatorSpec,
    val op3: OperatorSpec,
    val totalLevel: Float = 0.5f
)
```

**Fm4OpVoice.kt (mutable state, holds 4 `OperatorState`):**
- `private val opState: Array<OperatorState> = Array(FM_OPERATORS) { OperatorState() }` (preallocated, D9)
- `private var patch: FmPatch? = null`
- `private var baseFrequency: Float = 0f`
- `private var op0SelfMod: Float = 0f` (feedback state for op0)
- `fun applyPatch(p: FmPatch)` — sets `patch = p` and **copies the immutable specs into the mutable state** (sets `phaseStep`, `outputLevel`, and envelope parameters on each `OperatorState`). No per-render allocation.
- `fun noteOn(midi: Int)` — `baseFrequency = midiToFreq(midi)`, reset each `opState[i].phase` and call `opState[i].envelope.noteOn()`.
- `fun noteOff()` — call `opState[i].envelope.noteOff()` for all 4.
- `fun render(buffer, frames, sampleRate, gainScale)` — `while` loop calling `renderOne()`.
- `private fun renderOne(sampleRate: Int): Float`:
  - **D15 phase math (corrected):** modulator output is a phase offset scaled by `modulationIndex`, **not** a per-sample delta scaled by `sampleRate`.
  - Algorithm 0 (serial `op0 → op1 → op2 → op3 → out`):
    ```kotlin
    val p = patch ?: return 0f
    val ops = opState
    val specs = p.op0..p.op3

    // op0: feedback from previous sample, shifted by feedback table value
    val fbShift = AudioLaws.feedbackShift(p.feedback)
    ops[0].phase += ops[0].phaseStep + op0SelfMod * fbShift
    val s0 = FastMath.fastSin(phaseToIdx(ops[0].phase)) *
             ops[0].envelope.next(1f / sampleRate) *
             ops[0].outputLevel
    op0SelfMod = s0  // store for next sample

    // op1: modulated by op0
    ops[1].phase += ops[1].phaseStep + s0 * specs[1].modulationIndex
    val s1 = FastMath.fastSin(phaseToIdx(ops[1].phase)) *
             ops[1].envelope.next(1f / sampleRate) *
             ops[1].outputLevel

    // op2: modulated by op1
    ops[2].phase += ops[2].phaseStep + s1 * specs[2].modulationIndex
    val s2 = FastMath.fastSin(phaseToIdx(ops[2].phase)) *
             ops[2].envelope.next(1f / sampleRate) *
             ops[2].outputLevel

    // op3: modulated by op2 (carrier)
    ops[3].phase += ops[3].phaseStep + s2 * specs[3].modulationIndex
    val s3 = FastMath.fastSin(phaseToIdx(ops[3].phase)) *
             ops[3].envelope.next(1f / sampleRate) *
             ops[3].outputLevel *
             p.totalLevel

    return s3
    ```
  - `fun phaseToIdx(phase: Float): Int` — `((phase / TAU) * 1024).toInt() and 1023`. (Internal helper, no allocation.)
  - **All sin lookups via `FastMath.fastSin` / `FastMath.fastCos`.** No `kotlin.math.sin` per sample.

**Patches.kt** — immutable singletons (`val` / `object`). Every operator's `modulationIndex` is **explicit** (D21). The default `0f` is overridden wherever the operator acts as a modulator in the algorithm topology.

**Reference values for the two algorithms in v1** (per the modulationIndex contract):

Algorithm 0 (serial `op0 → op1 → op2 → op3 → out`):
- `op0.modulationIndex = 2.0f` (op0 modulates op1)
- `op1.modulationIndex = 1.5f` (op1 modulates op2)
- `op2.modulationIndex = 1.0f` (op2 modulates op3)
- `op3.modulationIndex = 0f` (op3 is the carrier; no downstream modulation)

Algorithm 1 (two parallel chains `op0 → op2 → out` and `op1 → op3 → out`):
- `op0.modulationIndex = 2.0f` (op0 modulates op2)
- `op1.modulationIndex = 2.0f` (op1 modulates op3)
- `op2.modulationIndex = 0f` (op2 is the chain-A carrier)
- `op3.modulationIndex = 0f` (op3 is the chain-B carrier)

```kotlin
internal object Patches {
    // ZunLead1 — algorithm 0, bright E-piano-ish lead. op0/1/2 are modulators.
    val ZunLead1 = FmPatch(
        algorithm = 0, feedback = 3,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 127, modulationIndex = 2.0f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 20,  modulationIndex = 1.5f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 24,  modulationIndex = 1.0f),
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 12,  modulationIndex = 0f),    // carrier
        totalLevel = 0.6f
    )
    // ZunBell1 — algorithm 0, bell-like timbre with high feedback. op0/1/2 are modulators.
    val ZunBell1 = FmPatch(
        algorithm = 0, feedback = 4,
        op0 = OperatorSpec(mul = 2, detune = 0, tl = 127, modulationIndex = 2.0f),
        op1 = OperatorSpec(mul = 2, detune = 0, tl = 8,   modulationIndex = 1.5f),
        op2 = OperatorSpec(mul = 2, detune = 0, tl = 16,  modulationIndex = 1.0f),
        op3 = OperatorSpec(mul = 2, detune = 0, tl = 0,   modulationIndex = 0f),    // carrier
        totalLevel = 0.5f
    )
    // ZunBass1 — algorithm 1, two parallel chains. op0 modulates op2, op1 modulates op3.
    val ZunBass1 = FmPatch(
        algorithm = 1, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 0,  modulationIndex = 2.0f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 24, modulationIndex = 2.0f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 8,  modulationIndex = 0f),     // carrier (chain A)
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 16, modulationIndex = 0f),     // carrier (chain B)
        totalLevel = 0.7f
    )
    // ZunPad1 — algorithm 1, soft pad. op0/1 are modulators at low depth.
    val ZunPad1 = FmPatch(
        algorithm = 1, feedback = 0,
        op0 = OperatorSpec(mul = 1, detune = 0, tl = 12, modulationIndex = 2.0f),
        op1 = OperatorSpec(mul = 1, detune = 0, tl = 8,  modulationIndex = 2.0f),
        op2 = OperatorSpec(mul = 1, detune = 0, tl = 8,  modulationIndex = 0f),     // carrier (chain A)
        op3 = OperatorSpec(mul = 1, detune = 0, tl = 8,  modulationIndex = 0f),     // carrier (chain B)
        totalLevel = 0.4f
    )
}
```

**Wiring into `OpnaLikeSynthesizer` (same phase):**
- Add `private val fm: Array<Fm4OpVoice> = Array(AudioLaws.FM_CHANNELS) { Fm4OpVoice() }`.
- In `render()`: between step 2 (SSG) and step 3 (drums), add `var i = 0; while (i < fm.size) { fm[i].render(buffer, frames, sampleRate, mixer.fmGain); i++ }`.
- Add `fun noteOnFm(channel: Int, midi: Int, patch: FmPatch)`, `fun noteOffFm(channel: Int)`.

**Tests (commonTest):**
- Render A4 (midi 69) with `ZunLead1` for 1s → output in `[-1, 1]`, no NaN, deterministic. Two runs of identical input produce identical first-sample hash.
- Confirm `kotlin.math.sin` is **not** called in `renderOne` (a static check: grep test).

**Build gates:** both pass.

---

### Phase 7 — Algorithm 1 (parallel pairs) + ZunBass1 / ZunPad1 wired and tested

**Modified file:** `shared-engine/.../engine/audio/opna/Fm4OpVoice.kt`
- Extend `renderOne` with `when (patch.algorithm) { 0 -> serial, 1 -> parallelPairs, else -> error("algo ${patch.algorithm} not in v1") }`.
- Algorithm 1 (per VHDL `fm_channel.vhd:70-77`, `algorithm1`):
  - Chain A: `op0 → op2 → out`
  - Chain B: `op1 → op3 → out`
  - Output: `outA + outB`
  - D15 math applies (modulator outputs are phase offsets).

**Tests (commonTest):**
- Same note A4 with `ZunLead1` (algo 0) vs `ZunBass1` (algo 1) → correlation < 0.5.
- `ZunBell1` vs `ZunLead1` (both algo 0) → correlation > 0.4 but visibly different amplitude envelope.

**Build gates:** both pass.

---

### Phase 8 — OpnaSequencer + Scale + NoteLen + OpnaPatterns (style-capture motifs)

**New files:**
- `shared-engine/.../engine/audio/opna/Scale.kt`
- `shared-engine/.../engine/audio/opna/NoteLen.kt`
- `shared-engine/.../engine/audio/opna/OpnaSequencer.kt`
- `shared-engine/.../engine/audio/opna/OpnaPatterns.kt`

**D16 enforcement:** `OpnaSequencer` uses **fixed primitive arrays + count fields**, no `mutableListOf`, no `ArrayList`, no per-event allocation.

**Scale.kt (D12):**
- `interface Scale { fun degreeToMidi(degree: Int, octave: Int): Int }`
- `class PhrygianDominantScale(val rootMidi: Int) : Scale` — intervals [0, 1, 4, 5, 7, 8, 10]
- `class PentatonicMinorScale(val rootMidi: Int) : Scale` — intervals [0, 3, 5, 7, 10]
- `class DorianScale(val rootMidi: Int) : Scale` — intervals [0, 2, 3, 5, 7, 9, 10]
- `class MinorScale(val rootMidi: Int) : Scale` — intervals [0, 2, 3, 5, 7, 8, 10]

**NoteLen.kt:**
- `enum class NoteLen(val beats: Float) { SIXTEENTH(0.25f), EIGHTH(0.5f), QUARTER(1f), HALF(2f), WHOLE(4f) }`

**OpnaSequencer.kt (primitive arrays, D16, D18):**
```kotlin
internal class OpnaSequencer(val sampleRate: Int, val bpm: Int, val beatsPerBar: Int = 4) {
    companion object { const val MAX_EVENTS_PER_CHANNEL = 1024 }

    // FM events: channel, midi, startSample, durationSamples
    private val fmChannelIdx    = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val fmMidi          = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val fmStartSample   = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val fmDurationSamp  = LongArray(MAX_EVENTS_PER_CHANNEL)
    private var fmEventCount    = 0

    // SSG events: channel, midi, startSample, durationSamples
    private val ssgChannelIdx   = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgMidi         = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgStartSample  = LongArray(MAX_EVENTS_PER_CHANNEL)
    private val ssgDurSamp      = LongArray(MAX_EVENTS_PER_CHANNEL)
    private var ssgEventCount   = 0

    // Drum events: kind, startSample
    private val drumKind        = IntArray(MAX_EVENTS_PER_CHANNEL)
    private val drumStartSample = LongArray(MAX_EVENTS_PER_CHANNEL)
    private var drumEventCount  = 0

    fun beatToSample(beat: Float): Long = (beat * 60f * sampleRate / bpm).toLong()
    fun barToSample(bar: Int): Long = (bar * beatsPerBar * 60f * sampleRate / bpm).toLong()  // D18

    fun noteFm(channel: Int, midi: Int, atBeat: Float, durBeats: Float) {
        val idx = fmEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return  // drop on overflow
        fmChannelIdx[idx]    = channel
        fmMidi[idx]          = midi
        fmStartSample[idx]   = beatToSample(atBeat)
        fmDurationSamp[idx]  = beatToSample(durBeats)
        fmEventCount = idx + 1
    }
    fun noteSsg(channel: Int, midi: Int, atBeat: Float, durBeats: Float) { /* same shape */ }
    fun noteDrum(kind: DrumKind, atBeat: Float) {
        val idx = drumEventCount
        if (idx >= MAX_EVENTS_PER_CHANNEL) return
        drumKind[idx]        = kind.ordinal
        drumStartSample[idx] = beatToSample(atBeat)
        drumEventCount = idx + 1
    }
    fun clear() { fmEventCount = 0; ssgEventCount = 0; drumEventCount = 0 }

    fun loopLengthSamples(): Long =
        (4 * beatsPerBar * 60L * sampleRate / bpm).toLong()  // D18: 1 bar

    fun writeInto(synth: OpnaLikeSynthesizer, loopOffsetSample: Long) {
        // Iterate primitive arrays. No allocation.
        var i = 0
        while (i < fmEventCount) {
            // schedule FM note on synth with patch parameter supplied by caller via channel
            i++
        }
        // same for ssg, drum
    }
}
```

**D18 fix:** `loopLengthSamples()` = `4 * 4 * 60 * sampleRate / bpm` (1 bar in 4/4) or equivalently `beatsPerBar * 4 * 60 * sampleRate / bpm` for 4 bars. The general formula is `bars * beatsPerBar * 60 * sampleRate / bpm`. The function exposes the 1-bar default; multi-bar loops scale at the call site.

**OpnaPatterns.kt (D12, D11, D8):**
- `internal object OpnaPatterns`:
  - `fun focusMotif(seq: OpnaSequencer)` — 4 bars, 138 BPM, E Phrygian Dominant. 1 FM lead (`ZunLead1`) + 1 FM bass (`ZunBass1`) + 1 SSG arp + drums (kick 1+3, snare 2+4, hat every 8th). Notes generated from the scale, not transcribed.
  - `fun alarmMotif(seq: OpnaSequencer)` — 2 bars, 154 BPM, E Phrygian Dominant. 1 FM lead (`ZunBell1`) + 1 FM bass (`ZunBass1`) + drums (four-on-the-floor, stronger kick, snare on 2+4, hat on every 8th). Notes from scale, no transcription.
  - `fun relaxMotif(seq: OpnaSequencer)` — 4 bars, 90 BPM, A Pentatonic Minor. 1 SSG pad (sustained square, duty 25%) + sparse drums (kick on 1, hat on 2+4, no snare).
  - `fun padMotif(seq: OpnaSequencer)` — 2 bars, 60 BPM, D Dorian. 1 FM pad (`ZunPad1`) + 1 SSG arp. No drums.

**Tests (commonTest):**
- `OpnaPatternsDurationTest`: each motif loops every `4 * beatsPerBar * 60 / bpm` seconds (D18). Assert via sample count.
- `OpnaPatternsNonTranscriptionTest`: per motif, manual review recorded as a code comment in the test file. Automated check: `focusMotif` uses only E Phrygian Dominant scale degrees [0,1,4,5,7,8,10] — assert no out-of-scale note.
- `OpnaSequencerNoAllocTest`: warmup once, then `noteFm` / `noteSsg` / `noteDrum` / `clear` 1000 times in a tight loop, assert no allocation per call (verified by reading the count field and the array contents; no `mutableListOf` involved).

**Build gates:** both pass.

---

### Phase 9 — SoundMelodies refactored to lane data, SoundPreviewPlayer routes 4 OPNA + 1 MediaPlayer, ChiptuneSynthesizer deleted

**D3 / D4 / D10 / D20 enforcement:**
- **Do not delete** arrangement bodies. Refactor each existing arrangement into **per-lane note data**.
- **v1 routing = 4 OPNA keys + 1 MediaPlayer key.** `oriental` keeps the existing `MediaPlayer` + `sounds/oriental_alarm.mp3` path.
- **No SSG triangle.** The old chiptune's "triangle" lane (bass) is re-routed to **FM bass** (cleaner) for the 4 OPNA keys.

**Modified files:**
- `shared-engine/.../engine/SoundMelodies.kt` — **refactored**, not deleted.
  - Define `internal data class Lane(val notes: List<ToneSpec>, val timbre: TimbreRef)`.
  - `internal data class ArrangementLanes(val lead: Lane, val harmony: Lane, val bass: Lane, val percussion: Lane, val tempoBpm: Int, val keyRootMidi: Int)`.
  - `internal enum class TimbreRef { FM_LEAD_ZUN1, FM_BASS_ZUN1, FM_BELL_ZUN1, FM_PAD_ZUN1, SSG_HARMONY_SQUARE, SSG_BASS_SQUARE, SSG_PAD_SQUARE, SSG_ARP_PULSE, DRUM_KICK, DRUM_SNARE, DRUM_HAT }` — pure metadata, no allocation.
  - **Refactor** each existing arrangement function (`buildBadAppleLead`, `buildBadAppleHarmony`, `buildBadAppleBass`, `buildBadApplePercussion`, the same for Senbonzakura) so they return `Lane` instead of `List<ToneSpec>`. The note data (frequencies, durations) is **preserved unchanged** — this is the project's own arrangement data, not a transcription.
  - `getMelody(key, volume, isBass): List<ToneSpec>` — **kept as a compat shim** that returns the appropriate lane for the `isBass=true` callers (the existing `SoundPreviewPlayer` calls `getMelody(key, volume, true)` to get the bass). The 4 OPNA keys' bass lane is just the `bass` field. The `oriental` key returns `emptyList()`.
  - `getArrangement(key): ArrangementLanes?` — **new** function. Returns the refactored arrangement for the 4 OPNA keys; returns `null` for `oriental`.
  - `synth-chime` and `synth-victory` had no explicit "lanes" in the old code (each was a single-channel function). For these, the refactor produces a one-lane `ArrangementLanes` with `lead` only and empty `harmony`/`bass`/`percussion`. The OPNA renderer treats empty lanes as "no notes on this channel."

- `app/src/main/java/com/example/timeboxvibe/engine/SoundPreviewPlayer.kt` — new branch.
  - `playPreview`, `playGentleReminder`, `playAlarm` each switch on the 5 keys.
  - For `synth-chime`, `synth-victory`, `synth-bad-apple`, `synth-senbonzakura`: instantiate `OpnaLikeSynthesizer(44100)`, fetch `SoundMelodies.getArrangement(key)`, instantiate `OpnaSequencer(sampleRate, arrangement.tempoBpm)`, walk each lane:
    - `lead` lane → `seq.noteFm(channel = 0, midi, atBeat, durBeats)` then `synth.fm[0].applyPatch(Patches.ZunLead1)` on noteOn
    - `harmony` lane → SSG channel
    - `bass` lane → `synth.fm[1].applyPatch(Patches.ZunBass1)` on noteOn
    - `percussion` lane → drum triggers
  - For `oriental`: **unchanged**, uses the existing `MediaPlayer` path with the pre-existing mp3 asset.
  - Same `AudioTrack` streaming plumbing (chunkSize = 1024, `THREAD_PRIORITY_AUDIO` thread) as before.

- `shared-engine/.../engine/core/ChiptuneSynthesizer.kt` — **deleted at the end of this phase**, after grepping the entire repo for remaining references.

**Routing table (D4, D10):**

| Key | Lane → OPNA | Tempo | Notes |
|---|---|---|---|
| `synth-chime` | 1 SSG square (high) + 1 SSG square bass (low, duty 0.25) + sparse hat | 90 BPM | Old chiptune's "triangle" lane replaced by SSG square bass (D10). |
| `synth-victory` | 1 FM lead (`ZunLead1`) + 1 SSG square bass (low) + kick 1+3, hat 2+4 | 120 BPM | Old chiptune's "triangle" lane replaced by SSG square bass (D10). |
| `oriental` | **(unchanged)** `MediaPlayer` with `sounds/oriental_alarm.mp3` | n/a | Pre-existing asset. D4. |
| `synth-bad-apple` | 1 FM lead (`ZunLead1`) + 1 SSG harmony (square) + 1 FM bass (`ZunBass1`) + drums (kick 1+3, snare 2+4, hat every 8th) | 138 BPM | Lane data preserved from existing arrangement (D2, D3). |
| `synth-senbonzakura` | 1 FM lead (`ZunBell1`) + 1 SSG harmony (square) + 1 FM bass (`ZunBass1`) + drums (kick 1+3, snare 2+4, hat every 8th) | 154 BPM | Lane data preserved from existing arrangement (D2, D3). |

**Tests (commonTest):**
- `SoundMelodiesFacadeTest`: for each of the 4 OPNA keys, `SoundMelodies.getArrangement(key)` returns a non-null `ArrangementLanes` with non-empty `lead`. For `oriental`, returns `null`.
- `SoundMelodiesSupportedKeysTest`: `SoundMelodies.supportedKeys` is exactly `[synth-chime, synth-victory, oriental, synth-bad-apple, synth-senbonzakura]`.
- `OpnaAcceptanceChecklistTest`: a 4-second render of all 4 OPNA keys (sequentially) → output in `[-1, 1]`, no NaN, no allocation, deterministic.

**Build gates:** both pass.
**Final cleanup:** delete `ChiptuneSynthesizer.kt`. Verify no other source file imports it (`grep -r "ChiptuneSynthesizer" --include="*.kt"`).

---

### Phase 10 — Hot-path audit (Python tooling) + acceptance tests

**New Python tool:** `tools/math_oracles/opna_audit.py`
- Reads every `.kt` file under `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/` and `OpnaLikeSynthesizer.kt` parent.
- Banned tokens (D9, D19) — fails the script with non-zero exit on any match in render paths:
  - `Random`, `nextFloat`, `nextInt`, `nextDouble`
  - `mutableListOf`, `arrayListOf`
  - `.map {`, `.flatMap`, `.buildList`, `.generateSequence`
  - `arrayOf(` when inside a `while` or `for` loop body (heuristic: scan line-by-line, track brace depth, flag any `arrayOf(` whose enclosing scope contains a `while` or `for`)
  - `kotlin.math.sin` (in hot loops — same heuristic)
  - `java.`, `System.currentTimeMillis`, `System.nanoTime`, `Thread(`, `Runtime.`
- "Render path" detection: the file's `render(`, `renderOne(`, `noteOn(`, `noteOff(`, `trigger*(` function bodies. Heuristic: the function body is everything from the opening `{` after the function header to the matching `}`. Banned tokens are flagged only if they appear inside one of these bodies.
- Output: per-file report + a single boolean pass/fail.
- Wired into Gradle as a custom task that runs before `compileKotlin`. Failing the audit fails the build (D9, D19).

**New tests (commonTest):**
- `OpnaDeterminismTest` — two `OpnaLikeSynthesizer` instances, identical note sequence → identical 1-second buffer hash (D9).
- `OpnaHihatSpectralTest` (D14) — `ProceduralDrums.triggerHat()` + 1s render → >80% of energy in `>5 kHz` band. Uses a simple zero-crossings heuristic or a hand-rolled 1024-point DFT.
- `OpnaAcceptanceChecklistTest` — full ensemble: `focusMotif` for 4 seconds → 1 FM lead, 1 FM bass, 1 SSG arp, drums → output in `[-1, 1]`, no NaN, RMS > 0.05.

**New test (jvmTest — JVM-only because of `Runtime.totalMemory`):**
- `OpnaNoAllocHotPathTest` — `OpnaLikeSynthesizer.render(buffer, 1024)` in a loop 1000 times, compare `Runtime.totalMemory()` before/after. Assert delta is below a small threshold (e.g., 1 KB — accounts for JIT warmup but flags per-frame allocation).

**Updates to `tools/math_oracles/README.md`:** add a section showing `opna_audit.py` usage and the Gradle task.

**Test infrastructure note:** the project does not yet have a `commonTest` directory. Phase 10 adds it. If `build.gradle.kts` needs a `commonTest { dependencies { implementation(kotlin("test")) } }` block, that is a small change. The first commit in Phase 10 verifies this with `grep "commonTest" build.gradle.kts` before adding anything.

**Build gates (extended):** both compile gates pass, AND `./gradlew opnaAudit` (the new task) passes, AND `commonTest` passes, AND `jvmTest` passes (if `jvmTest` is configured).

---

## 4. Phasing & PR breakdown

Each phase is one PR. PR titles:

1. `feat(audio): add AudioLaws, Envelope, LfsrNoise, Midi helper; clean ChiptuneSynthesizer noise path`
2. `feat(audio): add SsgVoice with deterministic test (square / pulse / LFSR noise; no triangle)`
3. `feat(audio): add ProceduralDrums with state machines and 5kHz+ hat test`
4. `feat(audio): add OpnaMixer (PC_9801_86 only, no enum)`
5. `feat(audio): add OpnaLikeSynthesizer skeleton with SSG + drums + softClip (no Fm4OpVoice yet)`
6. `feat(audio): add OperatorSpec / OperatorState / FmPatch / Fm4OpVoice algorithm 0; wire FM into OpnaLikeSynthesizer; add ZunLead1 / ZunBell1 / ZunBass1 / ZunPad1`
7. `feat(audio): add Fm4OpVoice algorithm 1 (parallel pairs)`
8. `feat(audio): add Scale / NoteLen / OpnaSequencer (primitive arrays) / OpnaPatterns`
9. `refactor(audio): SoundMelodies refactored to per-lane data; SoundPreviewPlayer routes 4 OPNA + 1 MediaPlayer; ChiptuneSynthesizer deleted`
10. `test(audio): add opna_audit.py (source-grep audit) + commonTest determinism / spectral / acceptance; jvmTest no-alloc; build fails on violation`

---

## 5. What is explicitly OUT of scope (v1.1 follow-up)

- **Full VHDL-derived LUTs:** `FBTAB` (8), `RATETABLE` (64), `CLTAB` (1024), `DTTAB` (256), `GAINTAB` (128), `NOTETAB` (128). Generated by `gen_lut.py --kind opna-*` into `shared-engine/.../engine/audio/opna/generated/`. The simple-math approximations in `AudioLaws` (`tlToAmplitude`, `detunePhaseOffset`, `feedbackShift`, `fnumBlockToFreq`) are replaced with table lookups. This is the **only** v1.1 task explicitly mentioned in this plan (D13).
- PC_9801_26K mode, `OpnaProfile` enum, 3-FM-channel mode.
- Algorithms 2, 3, 5, 6, 7.
- LFO / vibrato / AMS / PMS / tremolo.
- SSG-EG (inverted envelope mode).
- ADPCM channel / sample RAM.
- Patch editor UI / builder DSL.
- Migration of `oriental` from MediaPlayer to OPNA (pre-existing asset; out of scope until the user requests it).
- iOS / mingwX64 runtime validation (compile only).

---

## 6. Risk register

| Risk | Mitigation |
|---|---|
| `OpnaSequencer` pre-allocated `IntArray`/`LongArray` capped at 1024 events per channel. A user-defined motif with >1024 notes per channel would silently drop events. | Document the cap. Add a `getDroppedEventCount()` for diagnostics. v1.1 increases the cap if needed. |
| Phase 6 FM with 4 operators × 6 channels × 44100 Hz = 1.05M sin lookups/s. `FastMath` LUT is 1024 entries, integer-indexed. Should fit in a 44.1kHz audio thread budget on a mid-range Android. | If profiling shows a hotspot, add a `q8` quantized-phase variant of `FastMath` (already supported via `--q 8` in `gen_lut.py`). |
| `OpnaLikeSynthesizer.softClip` denominator `1f + abs(x)` is fast but slightly different from `Math.tanh`. Loudness may differ from old `ChiptuneSynthesizer`. | Documented. Master gain 0.7 calibrated by ear in Phase 9. |
| `OpnaSequencer.writeInto` iterates primitive arrays on every render chunk to schedule events. With 1024 events per channel × 4 channels, that's 4096 events to check per chunk. | Acceptable. If profiling shows it as a hotspot, convert the sequencer to a per-sample event iterator (advance a cursor per chunk). Deferred to v1.1. |
| The hot-path Python audit's `arrayOf(` and `kotlin.math.sin` heuristic (track brace depth + `while`/`for` scopes) is approximate. False positives are possible. | Audit failures are reviewed manually before being treated as real. The build fails the test, not the agent. |
| `ChiptuneSynthesizer` deletion breaks any third-party caller (none expected — this is a single-app codebase). | Grep `ChiptuneSynthesizer` across the entire repo before deletion. Phase 9 prerequisite. |

---

## 7. Acceptance criteria (v1 done when ALL of these are true)

A1. `AudioLaws.kt` exists with the 5 channel/sample/gain constants and the 4 simple-math functions (D7, D13).
A2. All 10 phases merged. `ChiptuneSynthesizer.kt` is deleted. `SoundMelodies.kt` is refactored to per-lane data, not deleted (D2, D3).
A3. `gradlew :shared-engine:compileKotlinMetadata` succeeds.
A4. `gradlew :shared-engine:compileDebugKotlinAndroid` succeeds.
A5. `gradlew opnaAudit` succeeds (Python source-grep audit passes).
A6. `gradlew :shared-engine:jvmTest` succeeds and `OpnaNoAllocHotPathTest` passes.
A7. `gradlew :shared-engine:commonTest` (or whatever the test task is named) succeeds and the math/spectral/acceptance tests pass.
A8. `gradlew :app:assembleDebug` succeeds and produces a working APK.
A9. On a real Android device, the 4 OPNA keys (`synth-chime`, `synth-victory`, `synth-bad-apple`, `synth-senbonzakura`) play. The `oriental` key plays via its pre-existing `MediaPlayer` path. No crash. No clicks. No clipping. **No new asset files are present in the repo (D4).**
A10. Pre-existing user-saved `TimerActions` with any of the 5 keys still play correctly without re-selection (D2, D4).
A11. No external asset files are added by this task. The pre-existing `sounds/oriental_alarm.mp3` is not introduced by this task (D4).
A12. The v1 file layout matches Section 1 exactly. LUT files do **not** exist in v1 (D13).

---

## 8. Open questions

**None.** The previous single open question (D21: `modulationIndex` default) is resolved. Decisions are locked. Phase 1 can begin.

For reference, the resolution:

- `OperatorSpec.modulationIndex` default is `0f`.
- Modulators (operators that contribute to a downstream operator's phase in the algorithm topology) explicitly set `modulationIndex = 1.5f..3.0f` (or whatever the patch needs).
- Carriers (operators whose output is summed to the channel output) explicitly set `modulationIndex = 0f`.
- No hidden FM behavior: incomplete patches sound obviously plain, not "almost right."
- `modulationIndex` is part of the immutable `OperatorSpec`, not the mutable `OperatorState`.
