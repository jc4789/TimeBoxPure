# Sound Software / Hardware Boundary Audit

## Scope

This is a read-only architecture audit of the `commonMain` audio code. It focuses on the intended boundary between:

- PMD/MML software: source language, authored music data, compilation, scheduling, and driver behavior;
- YM2608/OPNA hardware emulation: register-shaped state and procedural chip/DSP behavior; and
- output/board policy: mixing, headroom, stereo placement, mastering, and PC-9801-86-inspired calibration.

No production code or tests were changed. This report does not propose a framework rewrite or a replacement audio engine.

## Executive conclusion

The boundary is not currently maintained consistently.

The largest crossing is not the location of the three patch files. It is that `OpnaLikeSynthesizer` and the FM/SSG voices know about PMD performance state, software envelopes and LFOs, gates, portamento, logical parts, and compiled timeline events. Those are software-driver concepts. A YM2608-facing core should instead receive resolved, absolute hardware-domain controls.

The three questioned files are **not duplicates**:

- `LlsPatches.kt` is a small catalog decoded from the LLS/PMD source used by Bad Apple.
- `Patches.kt` is a legacy generic catalog using the older float-ADSR representation.
- `OpnaPatchBank.kt` is an aggregate registry and source-name lookup that delegates to those catalogs and also defines additional FM and SSG presets.

Deleting two of them as duplicates would therefore be incorrect. However, all three contain authored catalog or compiler-lookup policy, so they should not be treated as YM2608 hardware implementation. The eventual clean boundary is to keep authored catalogs and names on the MML/music side while passing a small immutable, register-shaped patch value into OPNA.

## Reference-derived ownership line

| PMD/MML software owns | Narrow runtime seam | YM2608/OPNA owns | Output/board layer owns |
|---|---|---|---|
| Parsing and source names | Absolute chip-domain controls or preallocated neutral control frames | FM FNUM/block and key state | Bus gains and headroom |
| Tone definitions and `@` selection | Register-shaped operator/channel patch values | DT/MUL, TL, AR/DR/SR/SL/RR/KS | SSG stereo placement |
| Tempo, timing, gates, ties, portamento | Key-on/off and parameter updates | Algorithms, feedback, four-operator routing | EQ/resonator/soft clipping |
| Logical parts and FM3 ownership | No source names, MIDI, tempo, or PMD event types | Hardware sine LFO and PMS/AMS/AM-enable | Song mastering |
| Software envelopes and software LFOs | One-way dependency from driver to chip contract | SSG tone/noise/mixer/envelope registers | PC-9801-86 calibration hypotheses |
| Relative commands and PMD effect selection | | Rhythm key/level/pan register behavior | Presentation/output conversion |

This division follows the manuals rather than the current package names:

- `PMDMML.MAN` sections 3-1, 6-1, and 6-2 place FM tone definitions, tone selection, SSG envelope presets, slot masks, and command interpretation in MML/compiler behavior.
- `PMDDATA.DOC` describes PMD per-part driver state containing notes, gates, portamento, two LFOs, software envelopes, slot masks, and related state.
- `ym2608 datasheet.txt` describes the chip's FM, SSG, rhythm-ROM, ADPCM, and hardware-LFO facilities. Its software-LFO discussion describes software periodically writing hardware parameters; it does not make the software algorithm chip state.
- `NEC PC-9801-86 User's Manual.txt` distinguishes six stereo FM channels, six stereo rhythm voices, and three mono SSG channels, along with board-level register access and output facilities.

The two text references contained enough information to establish ownership. The PDFs were not needed. They would become useful for a future attempt to reproduce detailed board wiring or analog transfer behavior, where the OCR text may be insufficient.

## Findings

Severity below describes architectural boundary impact, not a claim that the current app is audibly broken.

### 1. High: the synthesizer is also the PMD driver and player

`OpnaLikeSynthesizer.kt` owns `PmdPerformanceState`, prepares PMD modulation frames during rendering, dispatches compiled timeline events, and handles tempo, gate, software-envelope, software-LFO, relative-control, and rhythm semantics. It also owns mixer/mastering objects and accepts both `OpnaSequencer` and `CompiledOpnaPlayer`.

This makes one class simultaneously:

- a PMD driver;
- a timeline player;
- a YM2608-like chip renderer; and
- an output/mastering facade.

Keeping a public integration facade is reasonable, but its internals should delegate across explicit layers. PMD should advance logical state and emit resolved controls; OPNA should consume them and render; output code should mix the rendered buses.

### 2. High: hardware voices consume PMD-specific performance frames

`Fm4OpVoice.kt` accepts `PmdModulationFrame`/FM3 PMD state and contains MIDI targets, slide timing, relative control handling, key-on delay, driver attenuation, and sample-linear portamento ramps.

`SsgVoice.kt` accepts `PmdSsgFrame` and combines PMD volume, software-envelope, release, and slide state. `SsgSharedState.kt` contains software period offsets and tone ramps.

The hardware model legitimately owns phase, operators, algorithms, feedback, hardware envelopes, FNUM/block, SSG counters, noise, and hardware envelope state. It should not know MIDI, PMD gates, PMD release completion, relative commands, or software modulation algorithms. Those should be resolved before crossing the seam.

### 3. High: the MML directory/package migration is incomplete

These files physically reside under `audio/mml` but declare `com.example.timeboxvibe.engine.audio.opna`:

- `CompiledInstrumentBank.kt`
- `CompiledOpnaPlayer.kt`
- `CompiledOpnaSong.kt`
- `CompiledOpnaTimeline.kt`
- `PmdPerformanceLaws.kt`
- `PmdPerformanceState.kt`
- `PmdSampleClock.kt`
- `PmdSoftwareEnvelope.kt`
- `PmdSoftwareLfo.kt`

Most of their contents are plainly compiler, scheduler, or PMD driver behavior. The package declaration makes them available to hardware code as if they were native OPNA concepts and hides upward dependencies.

A package-only move would improve honesty, but it will expose compile-time dependency cycles. That is useful evidence, not a reason to preserve the false package boundary. It should be done in controlled steps after defining the seam.

### 4. High: compiled song and timeline types mix semantic and hardware events

`CompiledOpnaSong.kt` and `CompiledOpnaTimeline.kt` combine PMD-level events—tempo, gate, logical parts, envelopes, LFOs, relative changes—with chip-facing actions. `CompiledOpnaPlayer` calls the synthesizer directly, while the synthesizer also accepts and interprets the player/timeline.

The result is a bidirectional relationship between player and renderer. A clearer flow is:

```text
MML source/catalog
        |
        v
PMD compiler + driver/player
        |
        v
absolute OPNA controls / neutral preallocated frames
        |
        v
YM2608-like chip/DSP
        |
        v
output / board-profile mixing
```

This need not require allocation in the audio callback. The seam can use fixed arrays, primitive commands, or reusable frames.

### 5. High: patch models mix authored, chip, runtime, and output data

`FmPatch.kt` combines several ownership categories:

- algorithm, feedback, and operator register fields are chip-shaped;
- `pms` and `ams` are hardware-LFO channel state rather than PMD tone-definition fields;
- `pan` is runtime channel routing rather than intrinsic operator tone data; and
- `totalLevel: Float` is product headroom/output policy, not the YM2608's per-operator TL register.

`MmlCompiler` currently makes FM patch selection also emit the patch's PMS/AMS defaults. PMD's documented tone-definition fields do not include PMS/AMS; hardware-LFO commands own that behavior. Selecting a tone should not silently reset unrelated runtime modulation state unless TimeBox explicitly defines that as an extension.

`OperatorSpec.kt` likewise contains both OPN rate fields and a legacy float-seconds ADSR mode. The compatibility conversion is useful, but it should finish on the software/lowering side so the hardware voice receives only a register-domain operator specification.

`SsgPatch.kt` is mostly hardware-shaped, but per-voice pan is a TimeBox output extension: the chip exposes the SSG as a mono mixed output. PMD's historical SSG `@0..9` behavior is also a compiler macro for software envelopes, not a hardware patch bank. Named SSG presets can remain a deliberate TimeBox extension, but should be owned and documented as source/compiler policy.

### 6. Medium-high: authored catalogs and source lookup live under `opna`

`OpnaPatchBank.kt` parses and resolves authored instrument names and controls lookup precedence. `LlsPatches.kt` preserves song/source provenance. `Patches.kt` is a legacy authored catalog. These are software/content concerns even though their final values target OPNA.

Recommended split:

- MML/music side: names, aliases, catalog membership, provenance, lookup precedence, and legacy/source representations;
- OPNA side: immutable `FmRegisterPatch`/`OperatorRegisterSpec`-style values containing only chip-shaped data;
- lowering step: resolve and convert once during compilation or initialization.

This preserves current values and lookup order without forcing hardware code to understand source names.

### 7. Medium-high: PMD drum selection knows the concrete procedural generator

`MmlCompiler` maps PMD rhythm sources directly to `ProceduralDrums.DrumKind.ordinal`. `PmdSsgEffectUnit.kt` also maps PMD K/R effect ordinals to procedural drum kinds, and `OpnaChipState` claims this PMD effect unit and legacy drums as chip-owned state.

The PMD command/effect mapping belongs in the driver. The procedural generator is an audio implementation detail. A stable neutral percussion/effect identifier or narrow trigger interface should separate them.

`Ym2608RhythmUnit.kt` is different: its six voices, key-on/dump, total/per-voice levels, and L/R routing form a reasonable hardware-facing rhythm-register facade. Its procedurally generated waveforms are an intentional clean-room substitute for the original fixed rhythm ROM, not a claim of bit-identical ROM emulation.

### 8. Medium: procedural sequencer and authored theory/content are under `opna`

`OpnaSequencer.kt`, `OpnaPatterns.kt`, `Scale.kt`, and `NoteLen.kt` contain scheduling, motifs, scales, and musical durations. They are not necessarily PMD MML, but they are also not YM2608 hardware. Current searches found no active call sites for this path.

These should be classified as a separate legacy/procedural music path and either moved to a music/sequencing package or retired after a dedicated call-site and compatibility decision. Their apparent lack of use is not authorization to delete them during a boundary move.

### 9. Medium: output policy is a third layer currently presented as OPNA

`OpnaMixer.kt`, `OpnaOutputProfile.kt`, `SongMastering.kt`, `MasterPeakEq.kt`, and `ProceduralStereoResonator.kt` implement bus balance, headroom, stereo behavior, EQ/resonance, clipping, and mastering. These are neither PMD language behavior nor literal YM2608 register state.

The `PC9801_86_REFERENCE` profile and the current SSG-to-FM ratio should remain labeled as calibration hypotheses unless supported by measurement or a more detailed board model. The board manual documents distinct paths and a five-path volume controller, but does not establish the current digital transfer functions.

`LlsPatches.CHIP_CHANNEL_SCALE = 0.38f` is a particularly visible crossing: a song/source-derived patch catalog embeds shared-bus headroom policy. Moving that file alone would not fix the ownership problem. Extracting or changing the value is sound-affecting and should be a separately measured change, not bundled with package cleanup.

### 10. Low-medium: hardware and product constants share one law surface

`AudioLaws.kt` places physical channel/operator counts beside render-pool sizes, gains, headroom, and output hypotheses. Splitting hardware invariants from engine-capacity and output-policy constants would make future audits and ports less ambiguous.

## File disposition

| File or group | Audit disposition | Reason |
|---|---|---|
| `LlsPatches.kt` | Move catalog ownership to MML/music; keep values | Active decoded PMD/source catalog, not a duplicate |
| `Patches.kt` | Move legacy catalog ownership; retain until compatibility decision | Distinct legacy ADSR voices; not active in the current song catalog but still reachable through generic lookup |
| `OpnaPatchBank.kt` | Split | Names/lookup/catalog are software; compiled register values can be OPNA inputs |
| Nine physical `audio/mml` files declaring `audio.opna` | Repackage in staged work | Compiler/player/PMD state is software-owned |
| `PmdPerformanceState` and PMD modulation types | Move to driver side; remove reverse dependencies | Logical-part and software-modulation state |
| `OpnaLikeSynthesizer` | Keep as optional facade, split internal ownership | Currently integrates driver, chip, and output responsibilities |
| `Fm4OpVoice`, `OpnRateEnvelope`, `OpnPitch`, hardware `Lfo` | Keep OPNA core; remove PMD inputs from voice | Hardware/DSP behavior |
| `SsgSharedState`/`SsgVoice` | Split software ramps from hardware counters/register behavior | Both layers currently coexist |
| `PmdSsgEffectUnit` | Split PMD mapping from procedural generator | Driver policy is stored as chip state |
| `Ym2608RhythmUnit` | Keep hardware-facing facade | Controls match the documented rhythm block |
| mixer/mastering/output profile | Move conceptually to `audio/output` or a clearly named board/profile layer | Product/output policy, not PMD or chip state |
| sequencer/pattern/scale/note-length legacy path | Move or retire only after a separate audit | Music logic, apparently unused, not hardware |
| generic `Envelope.kt` | Treat as legacy pending a dedicated API/dead-code check | Not part of the OPN rate-envelope core |

## What should not change merely to tidy the boundary

- Do not delete two of the three patch files as duplicates.
- Do not move oscillator, phase, algorithm, feedback, OPN rate-envelope, hardware-LFO, SSG counter/noise, or rhythm-register behavior into MML.
- Do not classify PMD software LFO as chip state merely because it produces timed register changes.
- Do not claim the clean-room procedural rhythm waveforms reproduce the original YM2608 rhythm ROM exactly.
- Do not call EQ, resonator, soft clipping, per-SSG-voice stereo pan, or the current FM/SSG gain ratio literal YM2608 behavior.
- Do not combine package moves with gain, envelope, modulation, or timing changes. Those require separate listening/render baselines.

## Recommended repair sequence

Each stage should preserve the current audible result and be reviewed independently.

1. **Name the seam.** Introduce or document the minimal chip-control contract: absolute register-domain values, key actions, and reusable control frames. Do not change synthesis.
2. **Correct pure PMD ownership.** Repackage the PMD clock, software envelope/LFO, logical performance state, compiled program, timeline, and player types. Resolve the dependency errors this exposes without adding adapter logic to the chip core.
3. **Split instrument compilation from chip values.** Move source lookup, catalog names, aliases, provenance, and legacy conversion to MML/music. Preserve all current values and lookup precedence. Pass a pure compiled register patch across the seam.
4. **Move performance advancement out of voices.** Have the PMD driver resolve MIDI/note interpretation, gates, slides, relative controls, software envelopes/LFOs, and FM3 logical ownership into absolute OPNA controls. Keep frames fixed-size and allocation-free.
5. **Separate the player from the renderer.** Make timeline dispatch one-way: player/driver emits controls; chip consumes controls. Retain `OpnaLikeSynthesizer` only as a thin integration facade if that remains convenient.
6. **Split effect selection from generation.** Remove `ProceduralDrums.DrumKind` and PMD ordinals from the compiler/chip seam while preserving the current procedural audio implementation.
7. **Extract output policy.** Move mixer/mastering/profile ownership into an output or board-profile layer. Treat gain changes as calibration work with rendered comparisons, not architecture cleanup.
8. **Audit legacy paths separately.** Decide whether to retain, relocate, or remove `Patches`, the procedural sequencer/pattern types, and generic ADSR code only after full public/API and sound-baseline checks.

## Non-findings and limits

- Missing ADPCM, CSM, timers, or a raw-register front end are feature-coverage questions, not PMD/OPNA boundary violations. They are defects only if the project claims complete YM2608 coverage.
- This audit establishes ownership from code and reference semantics. It does not establish cycle accuracy, analog accuracy, or acoustic equivalence to a physical PC-9801-86/YM2608.
- No recommendation here requires replacing the custom Kotlin Multiplatform architecture or introducing a modern UI/audio framework.

## Bottom line on the original three files

Your instinct that their current `opna` ownership is questionable is correct. The part to revise is the duplication hypothesis: the files serve different roles and should all remain until a deliberate compatibility decision is made.

The clean target is not simply “move all three files into `mml`.” It is:

1. put authored catalogs, source names, lookup, and legacy conversion on the MML/music side;
2. keep one small immutable register-shaped patch representation at the YM2608 seam; and
3. keep mixer/headroom and runtime channel controls out of both the authored tone definition and the chip patch DTO.
