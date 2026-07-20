# Sound Software / Hardware Boundary Audit

## Scope

This is a read-only architecture audit of the `commonMain` audio code. It focuses on the intended boundary between:

- PMD/MML software: source language, authored music data, compilation, scheduling, and driver behavior;
- YM2608/OPNA hardware emulation: register-shaped state and procedural chip/DSP behavior; and
- output/board policy: mixing, headroom, stereo placement, mastering, and PC-9801-86-inspired calibration.

No production code or tests were changed. This report does not propose a framework rewrite or a replacement audio engine.

## Executive conclusion

The boundary is not currently maintained consistently.

The largest crossing is not the location of the three patch files. It is that `OpnaLikeSynthesizer` and the FM/SSG voices know about PMD performance state, PMD SSG software envelopes and software LFOs, gates, portamento, logical parts, and compiled timeline events. Those are software-driver concepts. Correcting ownership means organizing the responsibilities, dependencies, and file/package placement of the existing correct production playback path. It does not require a new seam or verification architecture.

The three questioned files are **not duplicates**:

- `LlsPatches.kt` is a small catalog decoded from the LLS/PMD source used by Bad Apple.
- `Patches.kt` is a generic catalog authored for the software-ADSR runtime path.
- `OpnaPatchBank.kt` is an aggregate registry and source-name lookup that delegates to those catalogs and also defines additional FM and SSG presets.

Deleting two of them as duplicates would therefore be incorrect. However, all three contain authored catalog or compiler-lookup policy, so they should not be treated as YM2608 hardware implementation. The eventual clean boundary is to keep authored catalogs and names on the MML/music side while preserving the existing runtime choice between a software-ADSR operator program and a YM2608 operator-EG program.

### Envelope non-conversion invariant

Software ADSR and the YM2608 operator EG are distinct supported runtime paths. Neither should be converted into, replaced by, approximated through, or lowered into the other.

The current implementation does not fully honor that intended distinction: `EgMode.LEGACY_ADSR` is routed through `OpnEnvelopeCompatibility`, which converts its seconds/level values to legal OPN rates, and `Fm4OpVoice` then runs `OpnRateEnvelope`. The standalone software `Envelope` implementation has no `commonMain` or `commonTest` call site in the current tree. This is an existing implementation mismatch to investigate, not the desired target and not a migration technique recommended by this audit.

There are also three different concepts whose similar names should not be conflated:

1. FM software ADSR, expressed in seconds and a linear sustain level;
2. the YM2608 FM operator EG, expressed as AR/DR/SR/SL/RR/KS and optional SSG-EG state; and
3. PMD's SSG software envelope, which is part of the PMD driver/performance model.

## Reference-derived ownership line

| PMD/MML software owns | YM2608/OPNA owns | Output/board layer owns |
|---|---|---|
| Parsing, source names, tone selection, tempo, timing, gates, ties, and portamento | FM FNUM/block, key state, DT/MUL, TL, algorithms, feedback, and four-operator routing | Bus gains and headroom |
| Logical parts, FM3 ownership, PMD SSG software envelopes, software LFOs, relative commands, and PMD effect selection | The OPN-EG path's AR/DR/SR/SL/RR/KS, hardware sine LFO, SSG registers, and rhythm-register behavior | SSG stereo placement, EQ/resonator/soft clipping, mastering, and presentation |

This table classifies the existing playback route; it does not propose a new runtime seam. The correction is to put the current objects and behavior under their proper owners and dependency direction without adding verification machinery.

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

The hardware model legitimately owns phase, operators, algorithms, feedback, hardware envelopes, FNUM/block, SSG counters, noise, and hardware envelope state. It should not know MIDI, PMD gates, PMD release completion, relative commands, or software modulation algorithms. Those responsibilities should be organized under PMD-owned code within the existing playback route.

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

A package-only move would improve honesty, but it will expose compile-time dependency cycles. That is useful evidence, not a reason to preserve the false package boundary. The work should organize the existing types and dependencies according to their real production responsibilities rather than introducing a new mediation layer.

### 4. High: compiled song and timeline types mix semantic and hardware events

`CompiledOpnaSong.kt` and `CompiledOpnaTimeline.kt` combine PMD-level events—tempo, gate, logical parts, envelopes, LFOs, relative changes—with chip-facing actions. `CompiledOpnaPlayer` calls the synthesizer directly, while the synthesizer also accepts and interprets the player/timeline.

The result is a bidirectional relationship between player and renderer. A clearer flow is:

```text
existing production playback route:

MML source/catalog
        |
        v
PMD compiler + driver/player
        |
        v
YM2608-like chip/DSP
        |
        v
output / board-profile mixing
```

The diagram describes how responsibilities in the current route should be organized. It is not an additional runtime layer or a new seam.

### 5. High: patch models mix authored, chip, runtime, and output data

`FmPatch.kt` combines several ownership categories:

- algorithm, feedback, and operator register fields are chip-shaped;
- `pms` and `ams` are hardware-LFO channel state rather than PMD tone-definition fields;
- `pan` is runtime channel routing rather than intrinsic operator tone data; and
- `totalLevel: Float` is product headroom/output policy, not the YM2608's per-operator TL register.

`MmlCompiler` currently makes FM patch selection also emit the patch's PMS/AMS defaults. PMD's documented tone-definition fields do not include PMS/AMS; hardware-LFO commands own that behavior. Selecting a tone should not silently reset unrelated runtime modulation state unless TimeBox explicitly defines that as an extension.

`OperatorSpec.kt` contains two native envelope parameter sets: float seconds/level for software ADSR and OPN register rates for the YM2608 operator EG. `EgMode` should select which runtime engine evaluates its own native parameters.

Today `OpnEnvelopeCompatibility.configure` converts the software-ADSR fields to nearest OPN rates and both modes are then rendered by `OpnRateEnvelope`. That collapses the two intended runtime paths and can change envelope timing and shape. The audit therefore rejects its earlier suggestion to move or finish this conversion during lowering. The boundary should instead preserve a discriminated software-ADSR specification or OPN-EG specification all the way to the appropriate runtime evaluator.

`SsgPatch.kt` is mostly hardware-shaped, but per-voice pan is a TimeBox output extension: the chip exposes the SSG as a mono mixed output. PMD's historical SSG `@0..9` behavior is also a compiler macro for software envelopes, not a hardware patch bank. Named SSG presets can remain a deliberate TimeBox extension, but should be owned and documented as source/compiler policy.

### 6. Medium-high: authored catalogs and source lookup live under `opna`

`OpnaPatchBank.kt` parses and resolves authored instrument names and controls lookup precedence. `LlsPatches.kt` preserves song/source provenance. `Patches.kt` is an authored software-ADSR catalog. These are software/content concerns even though their oscillators ultimately target the FM synthesizer.

Recommended split:

- MML/music side: names, aliases, catalog membership, provenance, and lookup precedence;
- existing runtime selection: preserve `EgMode`'s choice and each mode's native parameters without adding an intermediate payload type;
- OPNA behavior: the YM2608 operator-EG evaluator consumes OPN register fields, while the distinct software-ADSR evaluator consumes seconds/level fields.

This preserves current values and lookup order without forcing hardware code to understand source names, approximate one envelope model with the other, or route playback through new architecture.

### 7. Medium-high: PMD drum selection knows the concrete procedural generator

`MmlCompiler` maps PMD rhythm sources directly to `ProceduralDrums.DrumKind.ordinal`. `PmdSsgEffectUnit.kt` also maps PMD K/R effect ordinals to procedural drum kinds, and `OpnaChipState` claims this PMD effect unit and legacy drums as chip-owned state.

The PMD command/effect mapping belongs in the driver. The procedural generator is an audio implementation detail. Their existing production code should be reorganized so each responsibility has the correct owner while preserving current triggering behavior; this does not require a new translation interface or layer.

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
| `Patches.kt` | Move catalog ownership; preserve the software-ADSR mode and values | Distinct software-ADSR voices; not active in the current song catalog but still reachable through generic lookup |
| `OpnaPatchBank.kt` | Split | Names/lookup/catalog are software; compiled patches must retain their selected ADSR or OPN-EG runtime path |
| Nine physical `audio/mml` files declaring `audio.opna` | Repackage in staged work | Compiler/player/PMD state is software-owned |
| `PmdPerformanceState` and PMD modulation types | Move to driver side; remove reverse dependencies | Logical-part and software-modulation state |
| `OpnaLikeSynthesizer` | Keep as optional facade, split internal ownership | Currently integrates driver, chip, and output responsibilities |
| `Fm4OpVoice`, `OpnRateEnvelope`, `OpnPitch`, hardware `Lfo` | Keep OPNA core; remove PMD inputs and stop routing software ADSR through `OpnRateEnvelope` | Hardware/DSP behavior, with a separate software-ADSR evaluator at the synthesis boundary |
| `SsgSharedState`/`SsgVoice` | Split software ramps from hardware counters/register behavior | Both layers currently coexist |
| `PmdSsgEffectUnit` | Split PMD mapping from procedural generator | Driver policy is stored as chip state |
| `Ym2608RhythmUnit` | Keep hardware-facing facade | Controls match the documented rhythm block |
| mixer/mastering/output profile | Move conceptually to `audio/output` or a clearly named board/profile layer | Product/output policy, not PMD or chip state |
| sequencer/pattern/scale/note-length legacy path | Move or retire only after a separate audit | Music logic, apparently unused, not hardware |
| software `Envelope.kt` | Preserve pending a dedicated software-ADSR runtime-path audit | The standalone evaluator is currently unreferenced; do not delete or convert it until the canonical software-ADSR evaluator is identified |

## What should not change merely to tidy the boundary

- Do not delete two of the three patch files as duplicates.
- Do not convert, replace, approximate, or lower software ADSR into YM2608 operator-EG rates, or vice versa.
- Do not move oscillator, phase, algorithm, feedback, OPN rate-envelope, hardware-LFO, SSG counter/noise, or rhythm-register behavior into MML.
- Do not classify PMD software LFO as chip state merely because it produces timed register changes.
- Do not claim the clean-room procedural rhythm waveforms reproduce the original YM2608 rhythm ROM exactly.
- Do not call EQ, resonator, soft clipping, per-SSG-voice stereo pan, or the current FM/SSG gain ratio literal YM2608 behavior.
- Do not combine package moves with gain, envelope, modulation, or timing changes. Those require separate listening/render baselines.

## Recommended repair sequence

Each stage should preserve the current audible result and be reviewed independently.

1. **Correct pure PMD ownership within the current route.** Repackage the PMD clock, software envelope/LFO, logical performance state, compiled program, timeline, and player types. Organize their existing production dependencies under the correct owners; do not introduce a new seam.
2. **Correct catalog ownership.** Move source lookup, catalog names, aliases, and provenance to MML/music while preserving the current lookup behavior, values, precedence, `EgMode`, and native envelope parameters.
3. **Put PMD calculations under PMD ownership.** Organize MIDI/note interpretation, gates, slides, relative controls, PMD software envelopes/LFOs, and FM3 logical ownership within the existing playback route. Preserve the correct production behavior and do not add a parallel representation.
4. **Untangle player and renderer ownership.** Organize the current player/timeline behavior under MML and chip/DSP behavior under OPNA while preserving the established product playback route. This audit does not prescribe a new call architecture.
5. **Correct effect ownership.** Organize PMD effect selection under the driver and procedural generation under audio synthesis while preserving the current triggering behavior. Do not introduce a translation or inspection layer.
6. **Extract output policy only as production behavior.** Move mixer/mastering/profile ownership into an output or board-profile package only if those objects remain the actual product playback implementation. Do not add comparison, measurement, recording, or validation machinery.
7. **Audit unrelated legacy paths separately.** Decide whether to retain, relocate, or remove the procedural sequencer/pattern types only after full public/API and sound-baseline review. Software ADSR is excluded from removal because it is a supported runtime path. This review does not authorize permanent tests or verification infrastructure.

## Non-findings and limits

- Missing ADPCM, CSM, timers, or a raw-register front end are feature-coverage questions, not PMD/OPNA boundary violations. They are defects only if the project claims complete YM2608 coverage.
- This audit establishes ownership from code and reference semantics. It does not establish cycle accuracy, analog accuracy, or acoustic equivalence to a physical PC-9801-86/YM2608.
- No recommendation here requires replacing the custom Kotlin Multiplatform architecture or introducing a modern UI/audio framework.

## Bottom line on the original three files

Your instinct that their current `opna` ownership is questionable is correct. The part to revise is the duplication hypothesis: the files serve different roles and should all remain until a deliberate compatibility decision is made.

The clean target is not simply “move all three files into `mml`.” It is:

1. put authored catalogs, source names, and lookup on the MML/music side;
2. preserve two explicit compiled envelope variants—software ADSR and YM2608 operator EG—and execute each with its own runtime behavior without conversion; and
3. keep mixer/headroom and runtime channel controls out of both the authored tone definition and the existing production patch representation while organizing the current playback route rather than adding a new seam.
