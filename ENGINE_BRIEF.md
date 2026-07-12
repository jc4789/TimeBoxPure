# ENGINE_BRIEF

## Current Engine Laws

- Shared engine code is Kotlin Multiplatform `commonMain`: no `java.*`, Android APIs, runtime content I/O, or hidden JVM assumptions.
- Audio, visuals, fonts, and UI geometry are procedural. Do not add sample files or runtime-loaded assets.
- Audio/render hot paths use caller-provided buffers, preallocated state, primitive loops, and no heap allocation or collection operators.
- Platform wrappers only present pixels/audio and forward primitive input/lifecycle events.
- Core colors are palette indices `0..15`; final rasterization is integer-snapped.
- Canonical UI unit is `U = 16`, the ROM glyph cell.

## Production Audio Contract

- `SongCatalog` is MML-only. It currently exposes `BAD APPLE!! / LOTUS LAND STORY` and `RIN TO SHITE`.
- Focus and relax defaults both use `MmlSongBank.SENBONZAKURA_DEMO_KEY`. This is a persisted legacy ID that now points to Bad Apple; do not infer content from its name or rename it casually.
- Production uses `ArrangementRouting.MML_LOGICAL_TRACKS`, `MmlArrangementScheduler`, and the unified primitive event program. `LEGACY` playback is rejected.
- Android music playback is true mono: `OpnaLikeSynthesizer.render(...)`, one PCM sample per frame, PCM16 streaming, and `AudioFormat.CHANNEL_OUT_MONO`.
- Stereo rendering remains an optional engine path. `ProceduralStereoResonator` defaults off and is not used by production playback.
- `USAGE_ALARM` is intentional for this productivity/alarm application.
- No `MediaPlayer`, sample asset, or runtime music-file path is part of production playback.

## Procedural OPN Sound Engine

- Main files:
  - `audio/opna/Fm4OpVoice.kt`
  - `audio/opna/OpnPitch.kt`
  - `audio/opna/OpnRateEnvelope.kt`
  - `audio/opna/AudioSinLut.kt`
  - `audio/opna/OpnaLikeSynthesizer.kt`
  - `audio/opna/OpnaSequencer.kt`
  - `audio/opna/LlsPatches.kt`
- Topology: 6 logical FM channels, 16 preallocated FM render voices, 4 operators per voice, all 8 OPN algorithms, 3 SSG channels, shared SSG noise/envelope state, and procedural drums.
- Phase state and step are `UInt`. The accumulator wraps at 32 bits; the intentional 29-bit waveform coordinate reads bits `28..19` for the 10-bit operator phase.
- Pitch selects the nearest legal 8 MHz OPN block/FNUM pair. MUL=0 means one-half; MUL=1..15 is direct. Signed detune uses the YM2608 keycode table.
- Generated log-sine and power tables are warmed before playback. Rendering contains no trigonometric calls or table initialization.
- Operator output is signed 14-bit. Attenuation combines log-sine, envelope, and TL in the integer logarithmic domain.
- Feedback uses the two previous operator outputs. All algorithms share the same clean-room phase-modulation core.
- FM envelope state is integer attenuation `0..1023` with OFF, ATTACK, DECAY, SUSTAIN, and RELEASE states.
- AR/DR/SR/RR/SL/KS, zero-rate holds, key scaling, retriggering, AM, SSG-EG, hardware LFO PM/AM, CH3 operator pitch, detune, pan, and portamento are implemented.
- Optional 2x FM oversampling advances phase twice per output frame while clocking the envelope once.
- Master peak EQ is configured before playback and processed allocation-free.
- The core is independently derived and Yamaha-style; it is not copied MAME/ymfm code and is not a bit-perfect YM2608 emulator.
- Unimplemented register-level features include CSM, timers, raw register writes, and a PMD/S98/VGM register-stream frontend.

## LLS PMD Patches

- `LlsPatches.At54`, `At74`, `At99`, and `At181` are OPN register voices decoded offline from ZUN's compiled PMD `.M` files using KAJA's published PMD layout.
- PMD serialized operator order is converted to logical slots.
- Algorithm, feedback, MUL, DT, TL, AR, DR, SR, SL, RR, KS, and AM fields are preserved.
- Each patch uses `CHIP_CHANNEL_SCALE = 0.44f` to reserve shared-bus headroom for multi-carrier voices.
- Patch/register contract tests protect the decoded definitions.
- Do not repair a patch/timbre error by arbitrarily changing Android output gain. Diagnose operator topology, envelope behavior, decoded registers, and mix interaction first.

## MML Layer

- Main files:
  - `audio/mml/MmlParser.kt`
  - `audio/mml/MmlCompiler.kt`
  - `audio/mml/MmlSongBank.kt`
  - `audio/mml/RinToShiteSong.kt`
  - `audio/mml/MmlArrangementScheduler.kt`
- MML is embedded as Kotlin raw strings and compiled once before streaming. Parsing and compilation never occur in the audio callback.
- Headerless MML is v1. `#MML 2` compiles into `CompiledOpnaSong`, a fixed-capacity primitive-array program independent of parser/catalog objects.
- V2 channel layout is A-F FM, G-I SSG, and R rhythm, with optional C1-C4 operator parts under `#FM3EXTEND ON`.
- V2 supports dots, ties/slurs, `Q0..8`, `V0..127`, relative accents, pan, signed-cent detune, portamento, hardware LFO, chords, authored polyphony, macros, nested loops, and channel-A tempo changes.
- V2 supports mid-track named instrument changes and records the active patch on every primitive note event. V1 deliberately rejects instrument changes.
- Timing uses 480 ticks per quarter and absolute tick conversion.
- Compiled programs carry a lightweight playback-gain scalar; event arrays are shared when catalog volume changes.
- MML2 is PMD-inspired, not a general PMD binary interpreter. Raw register commands, historical grace syntax, timers, CSM, and PMD software-LFO compatibility remain unsupported.

## Current Bad Apple State

- Production source constant: `MmlSongBank.BAD_APPLE_LLS_MML`.
- Catalog title: `BAD APPLE!! / LOTUS LAND STORY`.
- Tempo: 160.73 BPM, 4/4.
- Current duration: 52 bars, approximately 77.64 seconds.
- Master EQ:
  - 180 Hz, -2.0 dB, Q 0.70
  - 850 Hz, +1.5 dB, Q 0.65
  - 2400 Hz, -4.0 dB, Q 0.85
- The TH04 archive and `ST02.M86` were decoded by offline tooling only. No archive bytes, PMD binary, extracted asset, or runtime file access was added to the project.
- Current source window is PMD clocks `288..<5280`, retaining the requested three-bar cut and 52-bar runtime.
- A-E are coordinated source-derived FM1-FM5:
  - A / FM1: 487 notes, `@74` harmonic bed.
  - B / FM2: 205 notes, upper melody/doubling; begins on `@181`, then changes to `@99`.
  - C / FM3: 205 notes, lower melody/doubling; begins on `@181`, then changes to `@99`.
  - D / FM4: 570 notes with source `@99 -> @54 -> @99` transitions.
  - E / FM5: 570 notes with source `@99 -> @54 -> @99` transitions.
- G contains 710 source-derived SSG2 notes on the clean-room square voice.
- PMD FM/SSG channel balance is uniformly mapped to 64% before `MmlArrangementScheduler.MIX_GAIN = 0.75f`.
- R remains the existing 405-shot procedural rhythm approximation.
- SSG1 is not included. Its PSG envelope/command stream has not yet been decoded safely.
- The original cut is still ambiguous: the source has a 192-clock/two-bar leading opening, while the current duration edit removes 288 clocks/three bars (about 4.48 seconds). Change this only as an explicit musical decision.
- `BAD_APPLE_LLS_MIGRATION_FIXTURE_MML` is an old headerless parser/migration fixture, not production song truth.
- Current compiled size is 3152 primitive events: 2037 FM notes, 710 SSG notes, and 405 rhythm shots.
- This is a source-coordinated reconstruction, not a complete or bit-authentic PMD claim. Exact decoded note timing, ties, mid-track patches, volume changes, and fixed PMD key-off tails are preserved for the included lanes. SSG1, original rhythm semantics, random/percentage Q, software LFO, and the final cut decision remain unresolved.

## Current Listening Risks

- User reports across recent passes included excessive 2-3 kHz bite, electronic/sharp timbre, muddy or overpowering bass, weak low/high cohesion, missing mids, intermittent resonance, and distortion.
- Mathematical, spectral, and unit tests cannot establish musical acceptance; listening remains the final gate.
- Never paste one recovered PMD lane over handwritten accompaniment and call it authentic. The coordinated source channel set and mid-track patch transitions must remain aligned.
- Do not apply a headphone Harman target literally as a synth-voice design target. Master EQ may control playback balance, but FM topology/envelopes and arrangement errors must be corrected at their source.
- Production is mono intentionally. Do not reintroduce stereo widening/resonance without an explicit listening-driven decision.
- Procedural drums are original approximations, not copied YM2608 rhythm-ROM samples.

## Other Production Song

- `RIN TO SHITE` is a ten-bar v2 PC-98-style finale at 136 BPM, adapted from score measures 67..76.
- Five FM parts divide lead, polyphonic piano motion, and sustained block harmony.
- It intentionally has no rhythm part or duplicate bass ostinato.

## Hot Paths

- `OpnaLikeSynthesizer.render*`
- `Fm4OpVoice.render` / `renderOne`
- `OpnRateEnvelope.nextAttenuation`
- `SsgVoice.render`
- `ProceduralDrums.render`
- `Lfo.prepare`
- `SsgSharedState.prepare`
- `OpnaSequencer` dispatch during rendering
- Renderer, rasterizer, and scene `update/render` loops

## Key Constants

- `AudioLaws.SAMPLE_RATE = 48000`
- `AudioLaws.FM_CHANNELS = 6`
- `AudioLaws.FM_RENDER_VOICES = 16`
- `AudioLaws.FM_OPERATORS = 4`
- `AudioLaws.SSG_CHANNELS = 3`
- `OpnPitch.MASTER_CLOCK_HZ = 8_000_000`
- FM clock divider `144`; EG divider `3`
- Phase coordinate `2^29`; lookup shift `19`; lookup size `1024`
- Envelope attenuation `0..1023`; TL `0..127`; signed operator peak `8191`
- `OpnaSequencer.MAX_EVENTS = 8192`
- `CompiledOpnaSong.MAX_EVENTS = 4096`; tempo-change capacity `128`
- `MmlCompiler.TICKS_PER_QUARTER = 480`
- Palette size `16`; canonical UI unit `U = 16`

## Verification

- Latest audio state: all 116 shared-engine tests pass.
- OPNA allocation/hot-path audit passes.
- Android `:app:assembleDebug` succeeds.
- Bad Apple regressions protect compiled event totals, per-lane counts, decoded opening pitches/patch, and the scheduled channel-D `@99 -> @54 -> @99` transition.
- Required local build:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:testDebugUnitTest :shared-engine:compileCommonMainKotlinMetadata :shared-engine:compileKotlinMetadata :shared-engine:compileDebugKotlinAndroid :app:compileDebugKotlin :app:assembleDebug
```

## Current Task Focus

- Listen to the coordinated FM1-FM5/SSG2 Bad Apple build before making more tonal changes.
- Decode SSG1 PSG-envelope semantics correctly.
- Replace approximate rhythm only with a procedural/legal reconstruction.
- Resolve the two-bar versus three-bar cut explicitly.
- Keep the clean-room OPN core plus embedded MML as the forward architecture.
- Add register emulation only if a concrete future project requires register-stream compatibility.
