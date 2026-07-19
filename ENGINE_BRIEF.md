# ENGINE_BRIEF

## Current Engine Laws

- Shared engine code is Kotlin Multiplatform `commonMain`: no `kotlin.jvm`, `java.*`, `javax.*`, Android APIs, runtime content I/O, or hidden JVM assumptions.
- Audio, visuals, fonts, and UI geometry are procedural. Do not add sample files or runtime-loaded assets.
- Audio/render hot paths use caller-provided buffers, preallocated state, primitive loops, and no heap allocation or collection operators.
- Platform wrappers only present pixels/audio and forward primitive input/lifecycle events.
- Core colors are palette indices `0..15`; final rasterization is integer-snapped.
- Canonical UI unit is `U = 16`, the ROM glyph cell.

## Production Audio Contract

- `SongCatalog` exposes exactly one product song: `BAD APPLE!! / LOTUS LAND STORY`.
- Its canonical ID is `synth-mml-bad-apple-lls`. The persisted legacy ID `synth-mml-senbonzakura-demo` resolves to that same catalog entry; do not add a duplicate entry.
- Stable source requires explicit `#MML 2`. There is one parser/compiler backend and one exact compiled representation.
- Production playback is `CompiledOpnaSong` -> exact `CompiledOpnaTimeline` -> `CompiledOpnaPlayer`, owned by `OpnaPlaybackSession` under one immutable `OpnaRenderProfile`.
- Android `SoundPreviewPlayer` is terminal glue only: thread/wake lock, `AudioTrack`, PCM16 conversion, buffers, and session calls.
- Production Android playback is mono. Stereo and arbitrary-interval inspection remain available through `OfflineOpnaRenderer` for engine work.
- `USAGE_ALARM` is intentional for this productivity/alarm application.
- No `MediaPlayer`, sample asset, runtime music-file path, `OpnaSequencer`, or scheduler-side song state is part of production playback.

## Runtime Ownership

- `CompiledOpnaSong` owns exact-size typed semantic payload tables with named fields. `CompiledOpnaTimeline` owns only compact, canonically ordered sample-boundary arrays and typed references into the song.
- `CompiledOpnaPlayer` is the single dispatcher. Stale note-off identity, sample-zero setup, canonical sub-order, reset, chunk, and loop behavior are explicit.
- `OpnaRenderProfile` is the single product render-policy owner: 48 kHz, maximum block size, oversampling, output profile, mix/headroom/master gains, filter/resonator policy, EQ, and final user gain.
- `OpnaPlaybackSession` owns synthesizer/player lifetime, sequential cursor, reset, stop, and loop restart. `OfflineOpnaRenderer` owns independent seek/replay state for inspection.
- `OpnaChipState` owns six FM voices, three SSG voices/shared state, FM3 overlay state, and one `PercussionRouter`.
- `PercussionRouter` has exactly two named domains: YM2608 rhythm and PMD SSG effect. Legacy authored drum kinds map explicitly into the YM2608 rhythm unit.
- `SongMastering` owns post-profile EQ/filter/resonator/clipping state and measurements. User gain is applied only at final mastering.
- `OperatorSpec` contains OPN register fields only. `OpnRateEnvelope` is the sole FM envelope implementation.
- `PmdPerformanceState` is the one persistent logical-part owner for normal FM, FM3, and SSG volume, detune, envelope, and software-LFO state. Runtime payloads are primitive; parser/compiler-only typed wrappers must remain common Kotlin.

## Procedural OPN Engine

- Main files:
  - `audio/opna/CompiledOpnaSong.kt`
  - `audio/opna/CompiledOpnaTimeline.kt`
  - `audio/opna/CompiledOpnaPlayer.kt`
  - `audio/opna/OpnaPlaybackSession.kt`
  - `audio/opna/OpnaRenderProfile.kt`
  - `audio/opna/OpnaLikeSynthesizer.kt`
  - `audio/opna/Fm4OpVoice.kt`
  - `audio/opna/OpnRateEnvelope.kt`
  - `audio/opna/PercussionRouter.kt`
- Topology: six fixed FM channels/voices, four operators per voice, all eight OPN algorithms, three SSG channels, shared SSG noise/envelope state, FM3 C1-C4 overlay, and two percussion domains.
- The fixed FM topology has no `P0`/`P1`, density selection, pooled ownership, reclaim logic, or pool snapshots. The retained `{cg}` chord form is a fixed two-note portamento gesture.
- Phase state and step are `UInt`; the accumulator wraps at 32 bits and the intentional 29-bit waveform coordinate reads bits `28..19`.
- Pitch selects the nearest legal 8 MHz OPN block/FNUM pair. Generated log-sine and power tables are warmed before playback.
- Operator output is signed 14-bit. Attenuation combines log-sine, envelope, and TL in the integer logarithmic domain.
- AR/DR/SR/RR/SL/KS, SSG-EG, hardware LFO, two PMD software LFOs per logical part, FM3 operator pitch, detune, pan, and portamento are supported.
- The sole FM envelope API is the integer `0..1023` OPN attenuation domain. Do not restore float ADSR or float compatibility accessors.
- Optional 2x FM oversampling advances phase twice per output frame while clocking the envelope once.
- SSG tone/noise/envelope state follows explicit 8 MHz register-domain laws. Fixed-volume and hardware-envelope DAC laws remain distinct.
- The clean-room core is Yamaha-style but is not copied emulator code and is not a bit-perfect YM2608 claim.
- CSM, timers, raw register writes, PMD binary playback, and S98/VGM playback remain outside the stable engine.

## MML Layer

- Main files: `audio/mml/MmlParser.kt`, `audio/mml/MmlCompiler.kt`, and `audio/mml/MmlSongBank.kt`.
- MML is embedded as Kotlin raw strings and compiled before streaming. Parsing and compilation never occur in the audio callback.
- Stable MML requires `#MML 2`; the old headerless/v1 dialect and compiler backend are removed.
- Channel layout is A-F FM, G-I SSG, R rhythm, with optional C1-C4 operator parts under `#FM3EXTEND ON`.
- The compiler emits semantic builder calls into typed payload tables; it does not expose a generic event/value union API.
- PMD volumes are persistent typed state: FM `V0..127` and exact coarse `v0..16`; SSG `V/v0..15`. Normal FM volume projects to carriers, while FM3 preserves disjoint logical-slot ownership. Timeline events carry no mix/mastering gain.
- `D`, `DD`, and per-logical-part `DM` are signed raw PMD detune operations, not cents. Checked lowering rejects overflow or state that cannot become legal FM Block/FNUM or SSG period values; no wrap/saturation parity is claimed.
- Timing uses 480 ticks per quarter. Exact milli-BPM and `#PMDCLOCK` convert to samples with integer rational arithmetic and a carried remainder.
- LOGO remains a test-only research fixture. Rin and its production/test source were removed. Neither is product-loaded.
- MML2 is PMD-inspired, not a general PMD binary interpreter.

## Current Bad Apple State

- Production source: `MmlSongBank.BAD_APPLE_LLS_MML`; title: `BAD APPLE!! / LOTUS LAND STORY`.
- Tempo is 160.73 BPM in 4/4; the 52-bar source window is approximately 77.64 seconds.
- A-E are coordinated source-derived FM lanes, G-H are source-derived SSG lanes, and R is the retained procedural rhythm approximation.
- The exact runtime timeline contains 7356 events.
- Source-domain performance state is restored: the complete source has 28 volume, 29 gate, and 17 detune declarations; the corresponding `[288, 5280)` source window contains 25 volume, 22 gate, 10 detune declarations, all four tied portamentos, and all eight SSG envelope definitions. The owned embedded MML comparison normalizes redundant declarations to effective typed state and currently has zero pitched-lane mismatches.
- Whole-output hashes are migration diagnostics only and are not musical or authenticity oracles.
- The TH04 archive and `ST02.M86` were decoded by offline tooling only. No archive bytes, PMD binary, extracted asset, or runtime file access is in the app.
- Musical acceptance still requires human listening; hashes and tests establish deterministic behavior, not taste.

## Hot Paths

- `OpnaPlaybackSession.render`
- `CompiledOpnaPlayer` primitive cursor dispatch
- `OpnaLikeSynthesizer.render*`
- `Fm4OpVoice.render` / `renderOne`
- `OpnRateEnvelope.nextAttenuation`
- SSG, PMD LFO, percussion-router, rhythm-unit, and mastering sample loops
- Renderer, rasterizer, and scene `update/render` loops

## Key Constants

- `AudioLaws.SAMPLE_RATE = 48000`
- `OpnaLikeSynthesizer.MAX_FRAMES_PER_CHUNK = 1024`
- `AudioLaws.FM_CHANNELS = 6`
- `AudioLaws.FM_OPERATORS = 4`
- `AudioLaws.SSG_CHANNELS = 3`
- `MmlCompiler.TICKS_PER_QUARTER = 480`
- `CompiledOpnaSong.MAX_AUTHORED_EVENTS = 262144`; built semantic tables and timelines are exact-size.
- Palette size `16`; canonical UI unit `U = 16`.

## Verification

- The repair adds semantic volume/detune/glide, envelope/LFO, product-session lifecycle, and hot-loop verification. Invalid gain/hash/richness tests were removed instead of made green around known-bad behavior.
- Final Android/JVM verification on 2026-07-18 passes 243 tests with zero failures, errors, or skips; shared Android compilation, app debug assembly, and `opnaAudit` pass.
- The final `OpnaPlaybackSession.render` path is statically audited for allocation hazards. The local JBR proxy permits at most 512 bytes of measurement noise over 1,024,000 frames after warm-up; it is not evidence of exact Android-device allocation count.
- Reset, loop, chunk, stale-ownership, stop/restart, typed-storage, and independent-session behavior are explicit fixtures.
- Windows and iOS builds/tests are intentionally not run until their local toolchains are configured.
- Required local verification:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:testDebugUnitTest :shared-engine:compileDebugKotlinAndroid :app:compileDebugKotlin :app:assembleDebug --rerun-tasks
```

## Current Task Focus

- `sound_plan.md` Phase 1 is the implemented architecture. Repair R1-R4 and executable R6 work are implemented; Phase 2 has not started.
- R5 product mono fold-down and resonator evaluation remain blocked on the plan's raw-semantic, level-matched human listening gate. Do not tune resonator/EQ before that record exists.
- Do not restore the retired sequencer, headerless/v1 MML, generic event unions, mutable synth policy, pooled FM voices, Rin, or a third percussion generator.
- Keep LOGO test-only until the separate admission evidence and listening gates are deliberately satisfied.
- Keep the clean-room OPN core plus embedded MML; do not add copied emulators, sample assets, PMD binary runtime, or a UI-framework rewrite.
