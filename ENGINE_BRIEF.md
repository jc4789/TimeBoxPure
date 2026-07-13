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
- Production uses `ArrangementRouting.MML_LOGICAL_TRACKS`, `MmlArrangementScheduler`, `CompiledOpnaTimeline`, and `CompiledOpnaPlayer`. `LEGACY` playback is rejected.
- Android music playback is true mono: `OpnaLikeSynthesizer.render(...)`, one PCM sample per frame, PCM16 streaming, and `AudioFormat.CHANNEL_OUT_MONO`.
- Stereo rendering remains an optional engine path. `ProceduralStereoResonator` defaults off and is not used by production playback.
- `USAGE_ALARM` is intentional for this productivity/alarm application.
- No `MediaPlayer`, sample asset, or runtime music-file path is part of production playback.

## Repaired Runtime Ownership

- Catalog MML has one runtime path: `CompiledOpnaSong` -> exact `CompiledOpnaTimeline` -> `CompiledOpnaPlayer`. The compiled-song-to-`OpnaSequencer` compatibility translator was removed; `OpnaSequencer` remains only for direct procedural motifs/tests.
- `OpnaChipState` owns preallocated chip voices/shared state, `PmdPerformanceState` owns FM3 part-local driver state, and `SongMastering` owns EQ/filter/resonator/clipping state.
- Compiled songs carry exact used-only `CompiledInstrumentBank` instances. Shared built-ins are compile-time sources; LOGO patch 79 is song-local and no longer extends the global built-in ID namespace.
- FM3 C1-C4 carry explicit logical-part IDs, independent volume/two-LFO state, and slot masks. Channel C is an explicit register-control lane; unsupported part-local controls fail compilation instead of disappearing.
- FM3 slot ownership is time-aware: simultaneous cross-part overlap fails, while sequential reuse is legal. ALG remains channel-global and patch FB changes only when slot 1 participates.
- Legacy authored drums, YM2608 rhythm-register controls, and PMD K/R SSG effects use separate procedural generators and reset domains. Timeline precedence is global -> state -> off/dump -> on/shot -> zero-gate off.

## Procedural OPN Sound Engine

- Main files:
  - `audio/opna/Fm4OpVoice.kt`
  - `audio/opna/OpnPitch.kt`
  - `audio/opna/OpnRateEnvelope.kt`
  - `audio/opna/AudioSinLut.kt`
  - `audio/opna/OpnaLikeSynthesizer.kt`
  - `audio/opna/CompiledOpnaTimeline.kt`
  - `audio/opna/CompiledOpnaPlayer.kt`
  - `audio/opna/OpnaSequencer.kt`
  - `audio/opna/LlsPatches.kt`
- Topology: 6 logical FM channels, 16 preallocated FM render voices, 4 operators per voice, all 8 OPN algorithms, 3 SSG channels, shared SSG noise/envelope state, and procedural drums.
- Phase state and step are `UInt`. The accumulator wraps at 32 bits; the intentional 29-bit waveform coordinate reads bits `28..19` for the 10-bit operator phase.
- Pitch selects the nearest legal 8 MHz OPN block/FNUM pair. MUL=0 means one-half; MUL=1..15 is direct. Signed detune uses the YM2608 keycode table.
- Generated log-sine and power tables are warmed before playback. Rendering contains no trigonometric calls or table initialization.
- Operator output is signed 14-bit. Attenuation combines log-sine, envelope, and TL in the integer logarithmic domain.
- Feedback uses the two previous operator outputs. All algorithms share the same clean-room phase-modulation core.
- FM envelope state is integer attenuation `0..1023` with OFF, ATTACK, DECAY, SUSTAIN, and RELEASE states.
- AR/DR/SR/RR/SL/KS, zero-rate holds, key scaling, retriggering, AM, SSG-EG, hardware LFO PM/AM, two PMD software LFOs per FM/SSG part, CH3 operator pitch, detune, pan, and portamento are implemented.
- Optional 2x FM oversampling advances phase twice per output frame while clocking the envelope once.
- SSG tone uses legal 12-bit periods derived from the standard 8 MHz `φM/(64*TP)` law. Its three fixed-duty tone counters, register-7 mixer bits, register-6 noise period, 17-bit noise LFSR, and hardware envelope registers are explicit shared chip state.
- SSG fixed levels select generated odd steps of the logarithmic 5-bit DAC law. Hardware-envelope period writes preserve phase; shape-register writes restart deterministically.
- `OpnaOutputProfile.TIMEBOX_LEGACY` remains the product default. `PC9801_86_REFERENCE` is an explicit selectable 25% SSG/FM balance hypothesis and is never selected from song identity.
- Master peak EQ is configured before playback and processed allocation-free.
- The core is independently derived and Yamaha-style; it is not copied MAME/ymfm code and is not a bit-perfect YM2608 emulator.
- Unimplemented register-level features include CSM, timers, raw register writes, and a PMD/S98/VGM register-stream frontend.

## LLS PMD Patches

- `LlsPatches.At54`, `At74`, `At99`, and `At181` are OPN register voices decoded offline from ZUN's compiled PMD `.M` files using KAJA's published PMD layout.
- PMD serialized operator order is converted to logical slots.
- Algorithm, feedback, MUL, DT, TL, AR, DR, SR, SL, RR, KS, and AM fields are preserved.
- Each patch uses `CHIP_CHANNEL_SCALE = 0.38f` to reserve shared-bus headroom for multi-carrier voices.
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
- Headerless MML is v1. `#MML 2` compiles into an exact-size `CompiledOpnaSong` independent of parser/catalog objects; setup expands it into one exact-size, canonically ordered sample-domain timeline.
- Catalog playback advances a primitive cursor through `CompiledOpnaPlayer`; it does not allocate event objects or sort in the callback. `OpnaSequencer` remains only for direct procedural motifs/tests, not catalog translation or playback.
- V2 channel layout is A-F FM, G-I SSG, and R rhythm, with optional C1-C4 operator parts under `#FM3EXTEND ON`.
- V2 supports dots, ties/slurs, PMD `Q0..8`/`Q%0..255` plus source-clock `q` random/minimum rules, `V0..127`, relative accents, pan, signed-cent detune, portamento, hardware LFO, PMD `M/MA/MB`, `MW`, `*`, `MM`, `MX`, and `MD` software-LFO controls, chords, authored polyphony, macros, nested loops, and channel-A tempo changes.
- V2 supports mid-track named instrument changes and records the active patch on every primitive note event. V1 deliberately rejects instrument changes.
- Timing uses 480 ticks per quarter. Exact milli-BPM and `#PMDCLOCK` state convert to samples with integer rational arithmetic and a carried remainder across tempo changes.
- SSG parts accept ordered legacy/extended `E` software-envelope definitions and Normal/Extend `EX0`/`EX1` clock controls. Normal follows tempo; Extend is fixed at approximately 56 Hz.
- FM and SSG parts each preallocate two independent PMD software LFOs. The seven documented waveforms, delay/speed/depth/repetition, pitch/volume targets, key-on sync/free-run, fixed/tempo clocks, FM TL masks, depth evolution, and explicit deterministic random reset are ordered part state. PMD software LFO is rejected with the engine-only `P1` polyphonic mode because dynamically pooled voices are not PMD parts.
- Compiled programs carry a lightweight playback-gain scalar; event arrays are shared when catalog volume changes.
- Playback-gain copies also share the immutable exact song-local instrument bank; runtime patch lookup never consults the authored/global name registry.
- MML2 is PMD-inspired, not a general PMD binary interpreter. Raw register commands, historical grace syntax, timers, CSM, and direct PMD binary playback remain unsupported.

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
- G contains 712 source-derived SSG1 notes and H contains 710 source-derived SSG2 notes on clean-room `lls_square` voices.
- PMD FM channel volume is mapped to 64% and the two summed SSG lanes to 37% before `MmlArrangementScheduler.MIX_GAIN = 0.75f`.
- R remains the existing 405-shot procedural rhythm approximation.
- Both active SSG lanes author the decoded PMD legacy software envelope `[AL=2, DD=-1, SR=24, RR=1]` as ordered part controls, clocked at 24 PMD clocks per quarter note.
- The original cut is still ambiguous: the source has a 192-clock/two-bar leading opening, while the current duration edit removes 288 clocks/three bars (about 4.48 seconds). Change this only as an explicit musical decision.
- `BAD_APPLE_LLS_MIGRATION_FIXTURE_MML` is an old headerless parser/migration fixture, not production song truth.
- Current authored size is 3868 primitive events: 2037 FM notes, 1422 SSG notes, 405 rhythm shots, and four SSG envelope/mode controls. The exact runtime timeline contains 7328 ordered tempo, control, note-on, key-off, and rhythm events.
- This is a source-coordinated reconstruction, not a complete or bit-authentic PMD claim. Exact decoded note timing, ties, mid-track patches, volume changes, PMD gate tails, and the LLS SSG software envelope are preserved for the included lanes. ST02 contains no active software-LFO definition/switch in the preserved source window, so Phase 4 intentionally leaves Bad Apple unchanged. Original rhythm semantics and the final cut decision remain unresolved.

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
- `PmdSoftwareLfo.advanceSample`
- `SsgSharedState.prepare`
- `CompiledOpnaPlayer` primitive cursor dispatch during catalog rendering
- `OpnaSequencer` dispatch for retained procedural motifs/tests
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
- `OpnaSequencer.MAX_EVENTS = 8192` applies only to retained procedural motif/test usage.
- `CompiledOpnaSong.MAX_AUTHORED_EVENTS = 262144` and tempo-change limit `4096` are malformed-input safety guards; built song and runtime timeline arrays are exact-size.
- `MmlCompiler.TICKS_PER_QUARTER = 480`
- Palette size `16`; canonical UI unit `U = 16`

## Verification

- The post-audit repair adds interaction coverage for FM3 part isolation, exact key delay, sequential slot reuse, rhythm-domain isolation/ordering/reset/bus routing, compact song-local banks, and LOGO oracle provenance.
- Latest post-repair Android/JVM result: 205 tests, zero failures/errors/skips; common metadata, Android shared code, app compilation/assembly, and the OPNA hot-path audit pass. The full `winTest` aggregate exceeded the five-minute command limit without failure output; focused Windows native rhythm/ownership tests and native compilation pass.
- OPNA allocation/hot-path audit passes.
- Android `:app:assembleDebug` succeeds.
- Bad Apple regressions protect compiled event totals, per-lane counts, decoded opening pitches/patch, and the scheduled channel-D `@99 -> @54 -> @99` transition.
- Timeline regressions cover a 4112-note source/8225-event runtime, exact arrays, rational tempo boundaries, canonical same-time ordering, awkward chunk invariance, retained-sequencer PCM parity, allocation-free envelope/register dispatch/reset, and identical consecutive loops.
- SSG conformance covers legal period optimality, 44.1/48/55.466 kHz measured pitch, register-7 mixer bits, a hardcoded 17-bit LFSR trace, global envelope write ordering, logarithmic DAC amplitudes, conflict warnings, and deterministic Bad Apple renders under both output profiles.
- Hardware-LFO conformance covers all eight manual rates with carried integer phase, sine PM/AM views, all PMS/AMS mappings, operator AM enable, global enable reset, and chunk invariance. PMD software-LFO tests cover all seven waveforms, the TH04-authored fixed-clock one-shot mode, two-state independence, delay, targets, TL masks, sync/free-run, tempo response, depth evolution, deterministic random reset, chunk invariance, and identical full-loop reset.
- FM differential conformance compares phase, envelope attenuation, operator output, feedback history, and mixed core samples against an independent register-step oracle for all eight algorithms and feedback 0..7. It also covers MUL 0..15, manual keycode/detune rows, exhaustive AR/DR/SR/RR/SL/KS rate laws, all eight SSG-EG shapes, FM3 independent pitches, hardware PM/AM, and unity routing to both enabled L/R buses.
- Phase 5 corrected the effective-rate fraction-1 EG pulse order, materializes visible SSG-EG attenuation before ordinary key-off release, and routes center stereo at full level to each enabled hardware bus. No patch registers, mono product path, song EQ, soft clip, oversampling/decimator coefficient, or output-filter coefficient changed.
- Phase 6 carries explicit four-bit FM3 slot masks end-to-end. PMD-style `s`, `sd`/`sdd`, `O`, `FB`, `sk`, and `MM` controls are ordered primitive events; selected patch writes isolate operator groups, ALG stays channel-global, patch FB changes only with slot 1, and compile-time ownership diagnostics reject ambiguous splits. Primitive-player and retained-sequencer state traces agree.
- Phase 7 separates YM2608 rhythm-register controls from PMD K/R SSG rhythm definitions. Backslash shot/dump/master/voice-level/pan commands are ordered timeline state; K expands R0..R255 definitions into separately tagged SSG-drum approximation events. Both paths remain procedural and asset-free. Production rhythm timbres/defaults are unchanged pending the required multi-song human listening pass.
- The offline PMD corpus audit inventories 23 unique M86 and 23 unique M26 payloads with zero scan errors; the ST02 seven-lane oracle remains at zero mismatches.
- Phase 8's first complete ingestion is `LOGO.M86` (`1e572f...66f7`) from archive `ca787b...c8d7`; `LOGO.M26` is byte-identical. Its single source-owned @79 patch, two FM notes, three 64-note SSG lanes, fixed-clock one-shot LFOs, envelopes, volumes, detune, and cross-part tempo ramp have normalized trace tests. It adds the catalog key `synth-mml-lls-logo` without changing global audio laws.
- Required local build:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:testDebugUnitTest :shared-engine:compileCommonMainKotlinMetadata :shared-engine:compileKotlinMetadata :shared-engine:compileDebugKotlinAndroid :app:compileDebugKotlin :app:assembleDebug
```

## Current Task Focus

- Phases 0-8 of the YM2608/PMD generalization plan are implemented, including one checklist-driven archive ingestion. Phase 5 tonal A/B, Phase 7 three-song rhythm listening, and listening approval of the new LOGO transcription remain explicit manual acceptance gates; no global tonal law or production rhythm default was changed without them.
- The architectural repair after Phases 6-8 is implemented. Do not restore the retired compiled-song sequencer path, a global per-song patch namespace, shared YM/K-R drum state, or channel-wide FM3 part performance state.
- Listen to the coordinated FM1-FM5/SSG1-SSG2 Bad Apple build before making more tonal changes.
- Listen to both output profiles before considering any change away from `TIMEBOX_LEGACY`; automated tests do not establish musical balance.
- Replace approximate rhythm only with a procedural/legal reconstruction.
- Resolve the two-bar versus three-bar cut explicitly.
- Keep the clean-room OPN core plus embedded MML as the forward architecture.
- Add register emulation only if a concrete future project requires register-stream compatibility.
