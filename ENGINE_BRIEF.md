# ENGINE_BRIEF

## Current Engine Laws

- Core engine code is Kotlin Multiplatform `commonMain`; no `java.*`, Android APIs, runtime file I/O, or hidden JVM assumptions.
- Audio, visuals, fonts, and UI geometry are procedural. Do not add sample files or runtime-loaded assets.
- Audio/render hot paths use caller-provided buffers, preallocated state, primitive loops, and no collection operators or heap allocation.
- Platform wrappers present pixels/audio and forward primitive input/lifecycle events; engine behavior stays in shared code.
- Core colors are palette indices `0..15`. Final raster output is integer-snapped.
- Canonical UI unit is `U = 16` (the ROM glyph cell); layout derives from `U` and logical display bounds.

## Production Audio Contract

- `SongCatalog` is MML-only. Its sole production song and both focus/relax defaults use `MmlSongBank.SENBONZAKURA_DEMO_KEY`.
- The old Oriental MP3 entry, Zen Chime, Victory, procedural Bad Apple, procedural Senbonzakura, and Lotus Land Story arrangement are retired from the catalog.
- Persisted retired IDs automatically fall back because app preference restoration accepts only IDs returned by `SongCatalog.byId`.
- `SoundMelodies.kt` remains temporarily as quarantined legacy source/data. It is not a production playback contract and must not drive compatibility changes in the new FM core.
- Android playback rejects `ArrangementRouting.LEGACY`; production uses `MML_LOGICAL_TRACKS` and `MmlArrangementScheduler` only.
- `USAGE_ALARM` is intentional: this is an alarm/productivity application.
- No platform audio asset path or `MediaPlayer` playback remains in `SoundPreviewPlayer`.

## Procedural OPN Core

- Main files:
  - `audio/opna/Fm4OpVoice.kt`
  - `audio/opna/OpnPitch.kt`
  - `audio/opna/OpnRateEnvelope.kt`
  - `audio/opna/AudioSinLut.kt`
  - `audio/opna/OpnaLikeSynthesizer.kt`
  - `audio/opna/OpnaSequencer.kt`
- Topology: 6 FM channels, 4 operators each, all 8 OPN algorithms, 3 SSG channels, and procedural drums.
- V2 SSG uses integer tone phase, one shared 17-bit noise generator, one shared hardware envelope, and a generated logarithmic 16-level ladder. Shared configuration is authentic last-write-wins; conflicting overlaps produce compiler warnings.
- Procedural rhythm now supplies kick, snare, hi-hat, tom, cymbal, and rimshot with per-shot volume/pan and no render-time transcendental math.
- Phase state and step are `UInt`. The accumulator wraps at 32 bits; the intentional 29-bit waveform coordinate reads bits `28..19` for the 10-bit operator phase.
- Pitch selects the nearest legal 8 MHz OPN block/FNUM pair. MUL=0 means one-half; MUL=1..15 is direct. Signed detune comes from the YM2608 manual keycode table.
- Two 256-entry operator tables are generated once before playback:
  - log-sine: `round(-log2(sin((i + 0.5) * PI / 512)) * 256)`
  - power: `round(8191 * 2^(-i / 256))`
- Operator output is signed 14-bit. Attenuation is `logSine + ((EG + TL * 8) << 2)`.
- Normal modulation enters the next operator as `operatorOutput >> 1`. Feedback uses `(previous1 + previous2) >> (10 - feedback)`.
- The voice normalizes the 14-bit operator domain with denominator `16384`; application master/lane gains are separate and must not be used to repair timbre.
- Optional 2x oversampling advances phase twice per output frame, but the envelope is clocked once and held across both subsamples.
- `Lfo` is one shared integer 32-bit phase source with eight OPNA rates. It prepares fixed PM/AM buffers once per render segment; PMS scales phase steps and AMS adds attenuation only to AM-enabled operators.
- Scheduled FM state supports signed-cent detune, pan, delayed LFO, and linear phase-step portamento.

## OPN Envelope

- All FM audio executes through one integer attenuation envelope (`0..1023`) with states OFF, ATTACK, DECAY, SUSTAIN, RELEASE.
- EG time derives from the 8 MHz chip clock divided by `144 * 3`, then is distributed over the host sample rate.
- Attack uses `attenuation += (~attenuation * increment) >> 4`; decay, sustain, and release are linear in attenuation space.
- AR/DR/SR/RR/SL/KS, zero-rate holds, key scaling, retrigger, and the SL=15 quiet level are implemented.
- Rate cadence is generated procedurally from effective-rate groups and fractional pulse distribution; no emulator increment table is embedded.
- `EgMode.OPN_RATE` consumes explicit register-style fields. `LEGACY_ADSR` only converts old float inputs to legal OPN rates at A4; it is not a second rendering engine and is not a promise that retired songs retain their old timbre.
- Explicit target patches are `LlsPatches.At54`, `At74`, `At99`, and `At181`; their carriers keep SR=0 while intended modulators use nonzero SR shaping.
- `OperatorSpec.ssgEg` enables FM operator SSG-EG shapes `8..15`, including repeat, alternate/invert, and hold behavior.

## Accuracy Profile

- This is an independently derived Yamaha-style OPN core, not a copied MAME/ymfm implementation and not a bit-perfect compatibility claim.
- Implemented musical chip features include LFO AM/PM, FM operator SSG-EG, hardware-style SSG tone/noise/envelope operation, and CH3 per-operator pitch/key control.
- Intentionally unimplemented: CSM, timers, raw register writes, and register-level YM2608 emulation. These belong to a future register-stream frontend if S98/VGM or PMD binary compatibility becomes a requirement.
- The engine preserves the project patch/note APIs, but production compatibility is defined by current MML content, not by `SoundMelodies.kt` output.
- Remaining subjective issue: the production timbre retains a small amount of upper-mid/high-band bite, although the operator/envelope rewrite materially reduced the previous piercing/hollow character.

## MML Layer

- Files: `audio/mml/MmlParser.kt`, `MmlCompiler.kt`, `MmlSongBank.kt`, and `MmlArrangementScheduler.kt`.
- MML is embedded as Kotlin raw strings and compiled once; parsing and musical compilation never occur in the streaming render loop.
- Headerless MML remains v1: A-E are flexible tonal tracks, R is rhythm, and the production source remains byte-for-byte unchanged.
- `#MML 2` compiles to `CompiledOpnaSong`, a primitive fixed-capacity event program independent of parser/catalog types.
- V2 layout is A-F FM, G-I SSG, R rhythm, with optional C1-C4 operator parts under `#FM3EXTEND ON`.
- V2 expression includes dots, ties/slurs, `Q0..8`, `V0..127`, relative accents, `p1..p3`, signed-cent `D`, `{cg}4` portamento, `H<pms>,<ams>[,l<delay>]`, and shared `#LFO 0..7`.
- V2 authoring includes named FM/SSG patches, nested loops to depth 8, `#MACRO`/`$name`, and global channel-A `T20..T400` tempo changes.
- Named FM patches are `54`, `74`, `99`, `181`, `lead`, `bell`, `bass`, `pad`, `chime`, `brass`, `piano`, `strings`, and `effect`; named SSG patches are `square`, `ssg_lead`, `ssg_bass`, `ssg_noise`, and `ssg_envelope`.
- V2 rhythm tokens are kick `k`, snare `s`, hi-hat `h`, tom `t`, cymbal `y`, and rimshot `i`; volume and pan are captured per shot.
- Timing uses 480 ticks per quarter and absolute tick conversion to avoid cumulative note-rounding drift.
- The production source is a 32-bar A/B/C/D/E/R arrangement (a 16-bar body repeated twice) at 160.73 BPM. Its historical ID still contains `demo`; do not rename persisted IDs casually.
- The sequencer owns a preallocated 4096-event array. `SoundPreviewPlayer` schedules and sorts events before starting streamed rendering.

## Hot Paths

- `OpnaLikeSynthesizer.render*`
- `Fm4OpVoice.render` / `renderOne`
- `OpnRateEnvelope.nextAttenuation`
- `SsgVoice.render`
- `ProceduralDrums.render`
- `Lfo.prepare`
- `SsgSharedState.prepare`
- `OpnaSequencer` event dispatch inside synth rendering
- `ScaledProceduralRenderer`, scene `update/render`, and framebuffer rasterization

## Known Risks / Failure Modes

- Do not retune timbre with `MASTER_GAIN`, lane gains, MML volumes, or Android `AudioTrack`; fix operator/envelope/patch parameters.
- Do not reintroduce platform assets or a `MediaPlayer` branch for alarms.
- Do not make retired `LEGACY` arrangements pass by weakening OPN math.
- Do not lazily initialize operator tables on the first note; synth/patch setup explicitly warms procedural tables and compatibility curves.
- Procedural drum exponential and pitch curves are generated once during initialization; rendering only indexes those tables. The six voices are original approximations, not copied YM2608 rhythm-ROM samples.
- No checked-in black-box ymfm capture exists. Current accuracy evidence comes from documented equations, manual detune values, behavioral tests, and rendered output, not an external bit-for-bit oracle.
- No pre-rewrite PCM baseline was preserved, so the old proposed historical 2 dB spectral A/B gate is not reproducible. Current full-song spectral/body regressions compare deterministic in-tree renders.
- MML v1 intentionally rejects mid-track timbre changes and songs exceeding channel/event capacity.
- MML v2 is PMD-inspired, not PMD-compatible. Raw registers, CSM, timer effects, historical grace-note syntax, and PMD software-LFO compatibility remain unsupported.
- `MmlSongBank.getArrangement(volume != 1)` allocates scaled note copies before playback; rendering itself remains allocation-free.

## Key Constants

- `AudioLaws.SAMPLE_RATE = 48000`
- `AudioLaws.FM_CHANNELS = 6`
- `AudioLaws.FM_OPERATORS = 4`
- `AudioLaws.SSG_CHANNELS = 3`
- `OpnPitch.MASTER_CLOCK_HZ = 8_000_000`
- FM clock divider `144`; EG divider `3`
- Phase coordinate `2^29`; operator lookup shift `19`; lookup size `1024`
- Envelope attenuation `0..1023`; TL `0..127`; signed operator peak `8191`
- `OpnaSequencer.MAX_EVENTS = 4096`
- `CompiledOpnaSong.MAX_EVENTS = 4096`; tempo-change capacity is `128`
- `MmlCompiler.TICKS_PER_QUARTER = 480`
- Palette size `16`; canonical UI unit `U = 16`

## Verification

- Math/hot-path audit: `python tools/math_oracles/opna_audit.py`
- Required local build:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:testDebugUnitTest :shared-engine:compileCommonMainKotlinMetadata :shared-engine:compileKotlinMetadata :shared-engine:compileDebugKotlinAndroid :app:compileDebugKotlin :app:assembleDebug
```

- Core coverage includes procedural tables, pitch/FNUM, detune, EG timing/rates, every algorithm/feedback level, phase wraps, oversampling, LFO AM/PM, SSG-EG shapes, hardware SSG determinism, CH3 scheduling, MML v1/v2, macro/tempo timelines, deterministic chunking, headroom, and allocation auditing.

## Current Task Focus

- Treat the procedural OPN engine plus embedded MML as the forward architecture.
- Compose new songs in `#MML 2` with named patches and authentic A-F/G-I/R limits; keep production Senbonzakura on v1 until a deliberate musical migration is requested.
- Add register emulation only if a concrete future project requires register-stream compatibility.
- Keep `SoundMelodies.kt` only until its shared arrangement model types are moved to a neutral file; then delete the retired builders separately.
