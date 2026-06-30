# ENGINE_BRIEF

## Current Engine Laws
- Canonical Unit: `U = glyphWidth = glyphHeight`; layout derives from `U`.
- Color: core engine uses palette indices `0..15` only.
- Display: layout derives from `logicalWidth` and `logicalHeight`; no orientation assumptions.
- Asset: no runtime PNG/JPG/SVG/TTF/WAV/JSON/XML assets.
- Platform Firewall: platform code provides surfaces/input/audio only; core owns logic and rendering.
- Integer-Snap: final raster output is integer-snapped (PC-98 aesthetic).
- LUT Trigonometry: realtime trig in hot paths uses `FastMath` LUT (1024 entries, 0.35°/step).
- Determinism: animation phase math uses `FrameClock` (monotonic), not wall-clock.

## Hot Path Files
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/RetroHudComponent.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ScaledProceduralRenderer.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/NestedTimeboxInstrumentRenderer.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/MagicCircleDemoscene.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/TimerEngine.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ChiptuneSynthesizer.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/FrameClock.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Wave.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/PerlinNoise.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/IkChain2D.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/VisualsStateHolder.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/OpnaLikeSynthesizer.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/Fm4OpVoice.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/SsgVoice.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/ProceduralDrums.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/OpnaSequencer.kt`

## Procedural OPNA Sound Engine

- Core: pure `commonMain` Kotlin, 48 kHz, caller-provided PCM buffers, no sample assets or allocation in render loops.
- Voices: 6 FM channels, 3 SSG channels, 4 operators per FM voice, plus procedural kick/snare/hat/tom.
- FM: algorithms 0..7, feedback, multiplier/detune, legacy ADSR or OPN-rate envelopes, LUT sine, per-note gain, patch pan, optional 2x oversampling and low-pass downsampling.
- SSG: square/pulse duty, PolyBLEP edge smoothing, optional LFSR noise, envelope and per-note gain.
- Mixer/output: named lead/harmony/bass/percussion gains, output filter, peak tracking, soft clipping, mono and stereo render entry points.
- Sequencing: `OpnaSequencer` owns a preallocated 4096-event array; FM/SSG notes emit paired ON/OFF events and drums emit one event. Events use absolute sample positions and deterministic sorting/loop reset.
- Patches: native project patches plus Lotus Land Story `@54`, `@74`, `@99`, and `@181`. MML v1 exposes `@54`, `@74`, and `@99`.
- Arrangement bridge: `ToneSpec` remains the note/event payload. `ArrangementLanes` contains lead, harmony, bass, optional auxiliary D, percussion, tempo, meter, and `ArrangementRouting`.
- Routing: existing songs remain `LEGACY`. MML songs use `MML_LOGICAL_TRACKS`; A-D are assigned sequential free FM/SSG hardware channels by timbre and R schedules procedural drums.
- Android host: `SoundPreviewPlayer` creates `AudioTrack`, converts compiled lanes into sequencer events before streaming, then renders fixed-size buffers. It never parses MML in the audio loop.

## Minimal MML Authoring Layer

- Files: `audio/mml/MmlParser.kt`, `MmlCompiler.kt`, and `MmlSongBank.kt` in `commonMain`.
- Storage: songs are compact Kotlin raw strings; external `.mml` files and runtime file IO are forbidden.
- Lifecycle: `MmlSongBank` compiles each source once during object initialization to `ArrangementLanes`/`List<ToneSpec>`. Per-play volume scaling may copy compiled notes, but parsing and musical compilation never occur during rendering.
- Directives: `#BPM <positive decimal>` and `#BAR numerator/denominator`.
- Tracks: A, B, C, D tonal tracks and R rhythm. A channel header may be followed by multiline continuation data; loops may span lines.
- Instruments: `@54`, `@74`, `@99`, `@square`, and `@drum`. One timbre per track, declared before the first event; mid-track changes are rejected.
- State commands: `v0..v15`, `o0..o8`, `l1|2|4|8|16`, and per-note lengths such as `c4` or `r16`.
- Notes: `c d e f g a b`, accidentals `+`, `#`, `-`, rests `r`, and octave shifts `>`/`<`.
- Rhythm: R uses `k` kick, `s` snare, `h` hat, and `r` rest.
- Structure: one-level `[ ... ]N` loops, `|` bar validation, and `;` comments. Nested/unclosed loops and incomplete bars fail compilation.
- Diagnostics: typed failures carry line, column, and reason. Compiler also rejects invalid channel/instrument combinations, more than 3 SSG tracks, unrepresentable meter ticks, out-of-range MIDI notes, parser expansion overflow, and `OpnaSequencer.MAX_EVENTS` overflow.
- Timing: 480 ticks per quarter; milliseconds are derived from absolute ticks and decimal BPM, preventing cumulative per-note rounding drift.
- Current demo: `synth-mml-senbonzakura-demo`, an 8-bar 160.73 BPM A/B/C/D/R arrangement compiled once and exposed in Settings, preview, reminder, and looping alarm paths.
- Tests: `MmlCompilerTest` covers multiline parsing, directives, accidentals, loops, timing, volume, track/timbre mapping, drum sentinels, typed failures, SSG/event capacity, and deterministic non-silent PCM. Focused suite and OPNA hot-path audit pass.

## Demoscene Engine Upgrades (this pass)

5 new pure-Kotlin files in `commonMain` provide procedural animation primitives. All allocation-free in hot paths, deterministic.

- **FrameClock** — monotonic frame counter, replaces `getEpochMillis() % N` for ornament phase. Ticked once per frame from `SceneManager.update()`.
- **Wave** — single-channel sine oscillator. 6 instances allocated in `MagicCircleDemoscene` for rune sway, pentagram breath, bead heartbeat (×2, π offset), core wobble, sector swing.
- **PerlinNoise** — 1D/2D/FBM Perlin with 256-entry permutation table generated at build time by `gen_lut.py --kind perm --size 256 --seed 42`. Permutation table emitted to `engine/generated/GeneratedPermLut.kt`.
- **IkChain2D** (FABRIK) — 2D constraint solver. Used for the 6-link yin-yang comet trail.
- **Point2D** (data class in `IkChain2D.kt`) — 2D coordinate value class.
- **MagicCircleDemoscene** — owner of the 6 Waves + 1 IkChain2D + Perlin rune drift helper. Created once per `ActiveTimerScene.onEnter()`.
- **VisualsStateHolder** — pure-Kotlin in-memory holder for the 2 user-toggleable visuals settings (demoscene effects, background nebula). Read by the magic circle renderer and `MagicCircleDemoscene`. Mutated by the Settings scene UI.

**New primitive on `ScaledProceduralRenderer`:** `drawPolarDot(cx, cy, radius, angle, size, colorIdx)` — small filled disc at a polar coordinate. Used for the 12-dot decoration ring.

**Tooling:** `tools/math_oracles/gen_lut.py` now has a `--kind perm` flag for deterministic Perlin permutation table generation.

**All 3 demoscene techniques (sin/cos, Perlin, FABRIK) implemented.** All hot-loop safe.

## Magic Circle (9 explicit non-overlapping bands)

Replaces the prior 16-layer overlapping layout. Rendered by `NestedTimeboxInstrumentRenderer.render()`. 14 draw calls.

| # | Layer | Radius | Color | Animation |
|---|---|---|---|---|
| 1 | Outer ring | `r` | `MAGIC_PRIMARY` (red) | static |
| 2 | Rune band (36 mantra glyphs) | `r - U*0.5` | `MAGIC_SECONDARY` | drift + Perlin |
| 3 | Outer detail ticks (36) | `r - U*0.5` to `r - U*0.75` | `BORDER` | 0.10x |
| 4 | 12-dot decoration ring | `r - U*0.75` | gold (cardinal) + gray | static |
| 5 | Outer timer beads (60) | `r - U*0.75` | `OUTER_ACTIVE` | 0.10x + heartbeat |
| 6 | Scripture ring (10 kanji) | `r - U*1.0` | `MAGIC_PRIMARY` | 0.08x |
| 7 | Outer pentagram (double-line) | `r - U*1.0` / `r - U*2.0` | `TEXT_FRAME` | 0.40x + breath |
| 8 | 5 sector kanji (龍雀麟虎武) | `r - U*1.5` | `MAGIC_PRIMARY` | locked to penta + swing |
| 9 | Octagram (2 squares) | `r - U*2.5` | sec/pri reds | -0.75x / +0.90x |
| 10 | Inner timer beads (48) | `r - U*2.5` | `INNER_ACTIVE` | static + heartbeat |
| 11 | Small inner ring | `r - U*3.5` | `MAGIC_PRIMARY` | static |
| 12 | Yin-yang core | `r * 0.16` | pri/sec | 1.50x + wobble |
| 13 | Comet trail (6 dots) | along trail | `TEXT_FRAME` | FABRIK (lagging 30°) |
| 14 | 5 inner cardinals | `r - U*4.0` | `MAGIC_PRIMARY` | static, upright |
| 15 | Center text readout | `cx, cy` | `TEXT_PRIMARY`/`TEXT_SECONDARY` | static (crisp) |

**Color remap:** uses `ACCENT_PRIMARY` (深緋), `ACCENT_DANGER` (緋色), `HIGHLIGHT` (真紅) for the red target aesthetic.

## Platform Wrappers
- Android: `app/src/main/java/com/example/timeboxvibe/...`
- iOS: KMP platform source set
- Win32: KMP platform source set

## Known Failure Modes
- HUD must be explicitly delegated by each scene; no global HUD hooks in `SceneManager`.
- Template forge/list split can break HUD cards-tab routing if forge is treated as a no-op tab.
- IMGUI rows overlap when label/control layout does not derive from `U` and measured label width.
- Compose-style assumptions drift into engine code as hardcoded breakpoints or orientation hacks.
- Render/input helper paths in scenes can still allocate strings in hot paths and need periodic audits.
- Never call `MmlParser` or `MmlCompiler` from `render`, audio callbacks, or per-buffer streaming code.
- `ArrangementLanes` cannot represent a mid-track timbre change; MML v1 intentionally rejects it.
- Four `@square` tonal tracks exceed the 3-channel SSG hardware model and must fail compilation.
- The legacy `oriental` sound still loads `sounds/oriental_alarm.mp3`; it is outside the procedural OPNA/MML path and conflicts with the current zero-asset law.
- Full shared-engine tests currently have two unrelated Lotus Land Story duration failures; focused MML tests pass.

## Current Constants
- `U = 16` (Int constant, layout refactored to discrete integer grid)
- `PALETTE_SIZE = 16`
- `MIN_SAFE_LOGICAL_WIDTH = 320`
- `MAX_SAFE_LOGICAL_WIDTH = 1200`
- `FastMath.TABLE_SIZE = 1024` (LUT for sin/cos)
- `PerlinNoise.PERM_SIZE = 256` (permutation table, doubled to 512 in `GeneratedPermLut`)
- `ORNAMENT_PHASE_PERIOD_FRAMES = 3600L` (60s at 60Hz)
- `MagicCircleDemoscene.TRAIL_LINKS = 6` (FABRIK chain length)
- `AudioLaws.SAMPLE_RATE = 48000`
- `AudioLaws.FM_CHANNELS = 6`; `AudioLaws.SSG_CHANNELS = 3`; `AudioLaws.FM_OPERATORS = 4`
- `OpnaSequencer.MAX_EVENTS = 4096`
- `MmlCompiler.TICKS_PER_QUARTER = 480`

## Current Task Focus
- Procedural OPNA engine is operational and unchanged by the MML work. Minimal MML authoring now compiles embedded multiline A/B/C/D/R songs into the existing arrangement path before playback.
- Current MML demo is the revised 8-bar `synth-mml-senbonzakura-demo`; next music work should improve composition/content without altering DSP math.
- Demoscene engine upgrades implemented (FrameClock, Wave, PerlinNoise, IkChain2D, MagicCircleDemoscene, VisualsStateHolder). Magic circle redesigned as 9-band layout with independent rotation, demoscene effects, FABRIK trail.
- Visuals settings section in SettingsScene (Demoscene Effects, Background Nebula toggles).
- Next: run oracle validation (`palette_verify.py`, `gen_lut.py --kind perm`, `affine_matrix_verify.py --cases`), then regression test the magic circle on device.

## File Change Manifest (this pass)

**New files (5 hand-written + 1 generated):**
- `shared-engine/.../engine/core/FrameClock.kt` (~50 LOC)
- `shared-engine/.../engine/core/Wave.kt` (~50 LOC)
- `shared-engine/.../engine/core/PerlinNoise.kt` (~90 LOC)
- `shared-engine/.../engine/core/IkChain2D.kt` (~80 LOC, includes Point2D)
- `shared-engine/.../engine/core/VisualsStateHolder.kt` (~25 LOC)
- `shared-engine/.../engine/core/MagicCircleDemoscene.kt` (~110 LOC)
- `shared-engine/.../engine/generated/GeneratedPermLut.kt` (auto-generated by `gen_lut.py`)

**Modified files (5):**
- `shared-engine/.../engine/core/ScaledProceduralRenderer.kt` — added `drawPolarDot`
- `shared-engine/.../engine/core/NestedTimeboxInstrumentRenderer.kt` — 9-band rewrite of `render()`, accepts `phase` + `demoscene` params
- `shared-engine/.../engine/core/Scenes.kt` — `ActiveTimerScene` now owns a `MagicCircleDemoscene` instance, uses `FrameClock.phase(3600)`; `SettingsScene` got the Visuals section with 2 toggles
- `shared-engine/.../engine/core/SceneManager.kt` — `FrameClock.tick()` at top of `update()`
- `shared-engine/.../engine/Strings.kt` — added 3 new strings (`visualsHeader`, `demosceneLabel`, `nebulaLabel`) in EN/ZH/JA

**Tooling:**
- `tools/math_oracles/gen_lut.py` — added `--kind perm` flag for Perlin permutation table generation

**Docs:**
- `AI_REF/overview.md` — section 7 updated with new primitives + 14-layer magic circle table
- `AI_REF/plan.md` — full plan document

**Total LOC:** ~600 new code lines + ~150 modified lines = ~750 net delta.

## Settings Additions

`SettingsScene` got a new "Visuals" section after the existing rows:

- **Demoscene Effects** (default ON) — toggles all 6 Wave oscillators + Perlin rune drift + FABRIK trail. When OFF, the magic circle renders as a static 14-layer layout.
- **Background Nebula** (default OFF) — toggles Perlin-driven 2-octave fbm modulation of BG ↔ BG_ALT. When OFF, flat `BG` color.

Toggles write to `VisualsStateHolder` (session-scoped, no platform persistence yet).
