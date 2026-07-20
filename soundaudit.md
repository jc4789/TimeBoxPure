# Sound Software / Hardware Boundary Audit

## Scope and decision rule

This is a read-only ownership audit of the production `commonMain` sound path. It separates three domains:

- **PMD/MML software:** source syntax, authored instrument selection, compilation, PMD clocks, logical parts, gates, ties, portamento, software envelopes/LFOs, relative commands, FM3 logical ownership, and K/R pattern selection;
- **YM2608/OPNA hardware model:** physical channel/register-equivalent state, FM operators and routing, FNUM/block, operator EG, hardware LFO, SSG counters/noise/hardware envelope, rhythm-register behavior, and procedural generation used behind those physical facilities; and
- **output/product policy:** bus balance, headroom, stereo presentation, EQ, filtering, resonance, clipping, and mastering.

The audit uses these classifications:

- **Proven boundary violation:** the references assign behavior to one side and the current dependency/state owner is on the other side.
- **Semantic divergence:** current MML behavior differs from documented PMD behavior. It may be retained only as an explicitly named TimeBox extension.
- **Supporting fact:** package placement or a broad data shape exposes debt but is not independently proof of a violation.
- **Intentional approximation:** nonliteral clean-room behavior that remains on the correct ownership side.

A coordinating entry point is acceptable only when it forwards already-owned work. It becomes a boundary violation when it owns another layer's state or interprets that layer's semantics. Under this exact rule, the current `OpnaLikeSynthesizer` is not merely a coordinator: it owns and executes PMD, chip, and output responsibilities.

No production code or tests were changed for this audit. Every `audio/...` code path below is relative to `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/`; a later basename-only citation refers to the same uniquely named file already introduced with that path. Reference-document citations use the files under `D:/Programes/ym2608-info/Docs/` or, for the PC-9801-86 text, `D:/Programes/ym2608-info/zun_music/`. `PMDDATA.DOC` and `PMDMML.MAN` were decoded as CP932 before assigning line numbers.

## Reference-established ownership

| Owner | Reference evidence | Consequence for TimeBox |
|---|---|---|
| PMD per-part driver | `PMDDATA.DOC:749-839` places note length, gate time, pitch, detune, two LFOs, portamento total/step/remainder, volume, software-envelope state, slot masks, q/Q, hardware-LFO delay, and slot delay in each part's work. | These states and their clocks belong to the PMD performance/player side. |
| PMD compiler/player | `PMDMML.MAN` §4-3 (`1324-1348`) defines portamento; §6-1/§6-2 (`1991-2045`, `2161-2213`) defines tone selection, SSG envelope expansion, and FM3 slot semantics; §8 (`2591-2705`) defines SSG/PCM software envelopes; §9 (`2727-3350`) defines software and hardware LFO commands. | Source commands are interpreted and advanced by PMD before they become chip-facing controls. |
| YM2608 FM hardware | `ym2608 datasheet.txt:641-730`, `1305-1508`, `1706-2079` defines algorithms/feedback, DT/MUL, TL, FNUM/block, operator EG, the sine hardware LFO, PMS/AMS, and per-operator AM enable. | The chip side owns current physical register-equivalent values and the DSP/counters driven by them. |
| YM2608 SSG/rhythm hardware | `ym2608 datasheet.txt:2193-2505` defines SSG tone/noise/envelope registers and the six-voice rhythm register block. | The chip side owns shared SSG registers/counters and rhythm key/dump/level/pan state. |
| Software LFO | `ym2608 datasheet.txt:2010-2012` describes software/timer code supplying changing parameter values when the built-in sine LFO is insufficient. | The software algorithm is not chip state merely because its results affect chip parameters. |
| PC-9801-86 board/output | `NEC PC-9801-86 User's Manual.txt:1043-1080`, `1239-1274` identifies six stereo FM voices, six stereo rhythm voices, and three mono SSG voices. | Per-SSG-voice stereo placement and product mastering are output extensions, not literal YM2608/board state. |

The required dependency direction is therefore:

```text
MML source/catalog
        -> PMD compiler + logical-part performance/player
        -> resolved chip/register-equivalent controls
        -> YM2608-like physical state + procedural DSP
        -> output/profile/mastering
```

This is an ownership rule for the existing production route, not authorization to add a parallel pipeline, tracing system, or verification architecture.

## Verified production call path

The current catalog route is:

1. `MmlCompiler` creates `CompiledOpnaSongBuilder`, compiles source/PMD commands, and returns the compiled program (`audio/mml/MmlCompiler.kt:53-141`).
2. `MmlArrangementScheduler` expands the program into a timeline and constructs `CompiledOpnaPlayer` (`audio/mml/MmlArrangementScheduler.kt:17-26`).
3. `CompiledOpnaPlayer` owns the timeline cursor but calls `OpnaLikeSynthesizer.renderTimelineSegment()` and `handleTimelineEvent()` (`audio/mml/CompiledOpnaPlayer.kt:83-119`).
4. `OpnaLikeSynthesizer` interprets the timeline, advances PMD state, drives physical voices, mixes buses, and applies product mastering (`audio/opna/OpnaLikeSynthesizer.kt:273-307`, `406-477`, `806-1122`).

The boundary problems below are on this live route. The retained `OpnaSequencer` overload is not the catalog player.

## Findings

### 1. Critical — `OpnaLikeSynthesizer` owns PMD, chip, and output state

**Current code path**

- The class constructs `OpnaChipState`, `PmdPerformanceState`, `OpnaMixer`, and `SongMastering` together (`audio/opna/OpnaLikeSynthesizer.kt:7-25`).
- It advances PMD performance frames in the render path and feeds them to SSG/FM voices (`OpnaLikeSynthesizer.kt:440-477`, `491-543`).
- It interprets tempo, PMD SSG software envelopes, PMD software LFOs, FM3 logical-part controls, hardware-LFO commands, relative FM controls, rhythm controls, and note lifecycle (`OpnaLikeSynthesizer.kt:806-1068`, `1071-1122`).
- It also selects raw/profiled mixing stages and invokes mastering (`OpnaLikeSynthesizer.kt:273-335`, `430-488`).

**Reference-owned behavior**

PMD part state and software modulation are driver work (`PMDDATA.DOC:749-839`). YM2608 operators/registers/counters are hardware state (`ym2608 datasheet.txt:641-730`, `1066-1076`). EQ, filters, resonator, clipping, and product gains are neither PMD nor YM2608 registers.

**Classification**

Proven boundary violation. One class is the owner and interpreter of all three domains, not merely their call coordinator.

**Repair boundary**

Keep PMD event interpretation and logical-part advancement on the PMD/player side. The OPNA side should receive resolved physical controls and render chip state. Output processing may remain in the product path but must not be described or owned as YM2608 hardware.

### 2. Critical — the player and synthesizer execute through each other

**Current code path**

- `CompiledOpnaPlayer` stores synth-facing seek buffers and an active synthesizer, resets the synthesizer, calls its segment renderer, and calls its event dispatcher (`audio/mml/CompiledOpnaPlayer.kt:4-12`, `23-31`, `83-119`).
- `OpnaLikeSynthesizer` accepts a `CompiledOpnaPlayer` and calls `player.render(this, ...)` (`audio/opna/OpnaLikeSynthesizer.kt:304-335`, `368-428`).

**Reference-owned behavior**

PMD owns program/part advancement; YM2608 owns physical state affected by the resulting controls. The manuals do not require a Kotlin API shape, but they do establish a one-way driver-to-device responsibility.

**Classification**

Proven live dependency cycle. The problem is not that a compiled program contains different event categories; it is that the player calls into the synth for PMD dispatch while the synth calls back into the player for execution.

**Repair boundary**

The PMD/player owner must advance program state and issue physical controls in one direction. The chip renderer must not accept a compiled PMD player or interpret PMD event identifiers.

### 3. Critical — physical voices consume PMD-specific runtime objects

**Current code path**

- `PmdPerformanceState` produces `PmdModulationFrame` and `PmdSsgFrame`, including pitch/volume targets, TL masks, software-envelope levels, and release completion (`audio/mml/PmdPerformanceState.kt:5-51`, `299-369`).
- `Fm4OpVoice.renderDriven()` accepts `PmdModulationFrame` and an array of FM3 PMD frames (`audio/opna/Fm4OpVoice.kt:526-535`). It selects PMD frames by operator and interprets PMD pitch targets, volume targets, TL masks, carrier targeting, and base attenuation inside operator rendering (`Fm4OpVoice.kt:703-772`).
- `SsgVoice.renderDriven()` accepts `PmdSsgFrame`, installs its software period offsets, applies its software-envelope/volume values, and ends the physical voice from `releaseFinished` (`audio/opna/SsgVoice.kt:81-125`, `127-161`).

**Reference-owned behavior**

The PMD side owns the software algorithms and part lifetime. The chip side may consume resolved frequency/TL/level/key operations. PMD itself mirrors hardware-facing values in part work, including B4-B6 pan/AM-depth/PM-depth and SSG register-07 tone/noise/mix (`PMDDATA.DOC:792-793`); this supports driver-to-chip writes, not chip types depending on `Pmd*` objects.

**Classification**

Proven reverse dependency. Precomputed primitive arrays are not inherently wrong, but their current types and consumers require physical voices to understand PMD-specific target masks and release semantics.

**Repair boundary**

PMD retains software modulation/lifetime state. OPNA consumes narrowly defined physical values without importing PMD logical-part types or interpreting PMD mask/lifecycle rules.

### 4. High — PMD portamento and relative-command policy are completed inside hardware objects

**Current code path**

- The compiler preserves source/target pitch and duration (`audio/mml/MmlCompiler.kt:863-887`), and the timeline converts the duration to sample frames (`audio/mml/CompiledOpnaTimeline.kt:483-497`).
- `Fm4OpVoice` stores MIDI target and slide duration, constructs phase-step ramps, and advances them per sample (`audio/opna/Fm4OpVoice.kt:27-45`, `224-236`, `774-865`).
- `SsgVoice` stores the target frequency/slide duration while `SsgSharedState` stores and advances tone-ramp state beside hardware counters (`audio/opna/SsgVoice.kt:14-41`; `audio/opna/SsgSharedState.kt:17-20`, `53-67`, `195-205`).
- Relative detune, TL, feedback, and rhythm-level commands are compiled as relative operations (`audio/mml/MmlCompiler.kt:509-582`) and remain relative when dispatched into mutable hardware-facing objects (`audio/opna/OpnaLikeSynthesizer.kt:1148-1205`; `audio/opna/Fm4OpVoice.kt:238-276`).

**Reference-owned behavior**

PMD documents portamento total delta, per-update delta, and remainder as part work (`PMDDATA.DOC:760-762`) and defines portamento as an MML performance command (`PMDMML.MAN` §4-3, `1324-1348`). YM2608 exposes absolute register fields, not MIDI targets, PMD slide duration, or relative source commands.

**Classification**

Proven boundary violation. It is also a behavioral risk: a sample-linear phase/period ramp is not evidence of PMD's documented clocked quotient/remainder trajectory merely because it reaches the same endpoint.

**Repair boundary**

PMD must resolve relative operations and advance its portamento accumulator/remainder. OPNA should receive the resulting absolute physical values at the times chosen by PMD.

### 5. High — hardware-LFO register state is stored as PMD frame state, while patch selection silently changes it

**Current code path**

- `PmdPerformanceState` stores channel PMS, AMS, and PMD key-on delay together (`audio/mml/PmdPerformanceState.kt:118-139`, `312-316`, `342-344`).
- Timeline PMS/AMS events write that PMD state rather than physical channel state (`audio/opna/OpnaLikeSynthesizer.kt:866-885`).
- `Fm4OpVoice` reads PMS/AMS from `PmdModulationFrame` during hardware-LFO rendering (`audio/opna/Fm4OpVoice.kt:703-720`).
- Selecting an FM instrument implicitly emits PMS/AMS events from `FmPatch` unless an explicit hardware-LFO depth has already appeared (`audio/mml/MmlCompiler.kt:310-345`, `941-963`). Direct preview similarly copies patch PMS/AMS into `PmdPerformanceState` (`audio/opna/OpnaLikeSynthesizer.kt:100-108`).

**Reference-owned behavior**

The YM2608 defines channel PMS/AMS as physical hardware-LFO sensitivity (`ym2608 datasheet.txt:1979-1992`). PMD's `H` command owns channel PMS/AMS and driver-side key-on delay (`PMDMML.MAN` §9-10, `3229-3259`). PMD part work may mirror B4-B6 state (`PMDDATA.DOC:792`), but the physical model must still own the current register-equivalent value.

**Classification**

Two separate issues:

- Proven boundary violation: the physical channel's current PMS/AMS exists only in PMD performance frames rather than chip/channel state.
- Proven semantic divergence: `@name` silently performs an `H`-like state change. This may be retained only as a documented TimeBox named-patch extension.

**Important terminology correction**

PMD FM tone records do contain a per-operator field labeled `AMS` with range `0..1` (`PMDMML.MAN` §3-1, `1033-1064`). In TimeBox that corresponds to `OperatorSpec.ams`, the per-operator AM-enable bit (`audio/opna/OperatorSpec.kt:17-18`; `audio/opna/Fm4OpVoice.kt:112-118`). It is not the same thing as `FmPatch.ams`, the channel AMS sensitivity. The audit must distinguish `operator AM enable` from `channel AMS sensitivity`.

**Repair boundary**

PMD owns `H` command retention and delay timing; the physical FM channel owns applied PMS/AMS. Tone selection must not reset channel LFO sensitivity unless an explicit TimeBox extension says that it expands to those controls.

### 6. High — PMD K/R SSG drums bypass the documented SSG3 path

**Current code path**

- `MmlCompiler` maps PMD K/R instrument numbers directly to `ProceduralDrums.DrumKind.ordinal` (`audio/mml/MmlCompiler.kt:1185-1255`).
- `PmdSsgEffectUnit` owns a separate `ProceduralDrums` instance and maps those ordinals to concrete generator triggers (`audio/opna/PmdSsgEffectUnit.kt:3-31`).
- `OpnaChipState` owns that PMD effect unit beside the actual SSG and rhythm units (`audio/opna/OpnaChipState.kt:5-13`).
- The synth mixes it directly into the SSG bus after rendering the three SSG voices (`audio/opna/OpnaLikeSynthesizer.kt:448-476`, `506-533`). It never arbitrates with physical SSG channel 3.

**Reference-owned behavior**

PMD says K/R normally plays PMD's built-in SSG drums, not the YM2608 rhythm part (`PMDMML.MAN` §1-2-1, `181-198`). Those effects use SSG channel 3, conflict with normal I-part playback, and win simultaneous key-on (`PMDMML.MAN` §1-2-3, `240-249`). The documented drum instrument IDs appear in §6-1-3 (`2050-2066`).

**Classification**

Proven boundary violation and behavioral divergence. The compiler depends on a concrete synthesis enum, and the implementation creates a parallel voice that cannot reproduce documented SSG3 contention.

**Repair boundary**

A PMD-faithful K/R path must remain PMD-scheduled and drive the physical SSG3 state with the documented conflict/priority. If the independent procedural effect is intentionally retained, label it as a TimeBox alternate backend rather than PMD K/R fidelity, and keep PMD semantic IDs separate from `ProceduralDrums.DrumKind`.

**Non-conflicting comparison**

`Ym2608RhythmUnit` is different. Its key/dump, total level, per-voice level, and pan controls are a reasonable register-shaped rhythm facade (`audio/opna/Ym2608RhythmUnit.kt:14-55`). Its procedural waveforms replace unavailable rhythm-ROM samples as a clean-room approximation; that is not by itself an ownership violation.

### 7. Medium-high — patch/catalog structures combine four ownership domains

**Current code path**

- `SourceInstrumentLookup` and fallback/name resolution are physically in `audio/mml/CompiledInstrumentBank.kt` but declared in the OPNA package (`CompiledInstrumentBank.kt:1-38`).
- `OpnaPatchBank` owns authored names, IDs, lookup precedence, FM catalogs, and SSG presets (`audio/opna/OpnaPatchBank.kt:3-27`, `64-130`). `MmlCompiler` consumes that source lookup (`audio/mml/MmlCompiler.kt:31-50`, `310-345`).
- `FmPatch` combines chip-shaped algorithm/feedback/operator data with floating product `totalLevel`, channel PMS/AMS defaults, and pan (`audio/opna/FmPatch.kt:3-15`).
- `LlsPatches` embeds shared-bus headroom in the source-derived catalog through `CHIP_CHANNEL_SCALE` (`audio/opna/LlsPatches.kt:3-15`).
- `SsgPatch` combines SSG register defaults with per-voice pan (`audio/opna/SsgPatch.kt:3-12`), even though the board exposes SSG as three mono voices (`NEC PC-9801-86 User's Manual.txt:1239-1247`).

**Reference-owned behavior**

PMD owns authored tone names/selection and tone records (`PMDMML.MAN` §3-1 and §6-1). Chip-shaped register values can be compiled from those records. Product headroom and per-SSG-voice stereo placement are output policy. PMD's historical SSG `@0..9` selection expands to software `E` envelopes (`PMDMML.MAN` §6-1-2, `2026-2045`), not to an intrinsic hardware patch bank.

**Classification**

Proven ownership mixing. Named SSG/FM presets may remain intentional TimeBox extensions, but their source naming, physical register data, compatibility data, and output defaults must not be presented as one YM2608 hardware concept.

**The three questioned files are not duplicates**

- `LlsPatches.kt` contains source-derived OPN-rate tones used by the LLS/Bad Apple material.
- `Patches.kt` contains distinct legacy seconds-based ADSR-authored presets.
- `OpnaPatchBank.kt` is the aggregate source-name/ID lookup and adds further FM/SSG presets.

Deleting any two as duplicates is unsupported. The ownership correction is to keep authored catalogs/names/provenance on the source/music side, keep register-ready tone data on the physical side, and keep gain/pan defaults in explicit product policy without changing current values during a structural move.

### 8. Medium — output/profile policy is packaged and owned as OPNA

**Current code path**

- `OpnaMixer` owns selectable FM/SSG/rhythm bus gains (`audio/opna/OpnaMixer.kt:3-16`, `18-104`).
- `OpnaOutputProfile` defines product hypotheses (`audio/opna/OpnaOutputProfile.kt:5-20`).
- `SongMastering`, `MasterPeakEq`, and `ProceduralStereoResonator` implement EQ, filter, resonance, gain, measurement counters, and clipping under the OPNA package (`audio/opna/SongMastering.kt:6-19`, `34-118`).
- `AudioLaws` mixes physical counts with a software voice-pool size, sample rate, bus gains, headroom, and product output gain (`audio/AudioLaws.kt:5-27`).

**Reference-owned behavior**

These are output/product responsibilities, not PMD commands or literal YM2608 register state.

**PC-9801-86 correction**

The board manual's five 16-level volume paths are FM direct, FM PCM-recording, line/mic direct, line/mic PCM-recording, and PCM playback (`NEC PC-9801-86 User's Manual.txt:2105-2127`). They are not independent FM/SSG/rhythm digital bus gains. The current `PC9801_86_SSG_TO_FM_RATIO = 0.25f` is correctly labeled a hypothesis in `AudioLaws.kt:19-20`; the manual does not establish that ratio.

**Classification**

Proven third-layer ownership debt. This does not prove the current values are wrong and does not authorize recalibration.

**Repair boundary**

Keep chip-bus generation in the audio core, but name and own bus profiles/mastering as output or board/product policy. A structural move must preserve all sound-affecting values and remain separate from later listening-driven calibration.

### 9. Supporting evidence — the physical MML directory still declares OPNA packages

These nine files live under `audio/mml` but declare `package com.example.timeboxvibe.engine.audio.opna` on line 1:

- `CompiledInstrumentBank.kt`
- `CompiledOpnaPlayer.kt`
- `CompiledOpnaSong.kt`
- `CompiledOpnaTimeline.kt`
- `PmdPerformanceLaws.kt`
- `PmdPerformanceState.kt`
- `PmdSampleClock.kt`
- `PmdSoftwareEnvelope.kt`
- `PmdSoftwareLfo.kt`

This is exact current-tree evidence, but it is not nine independent violations. A package-only rename would leave real reverse dependencies:

- `PmdGateState.kt:3` imports OPNA-packaged `PmdPerformanceLaws`;
- `PmdSoftwareLfo.kt:163` depends on `OpnaLfoLaws.PHASE_CYCLE`;
- `PmdPerformanceState.kt:342` uses `OpnRateEnvelope.MAX_ATTENUATION`; and
- physical voices still accept the PMD frame types described in finding 3.

The ownership/dependency changes must precede or accompany package correction. Otherwise the move is cosmetic.　user note ＊＊＊ package整理を後回しにして、先にarchitectureを直す口実になりうる。むしろ一fileずつpackage/importだけ変え、露出したcompile dependencyを一個ずつ処理する方が安全。＊＊＊

## Corrected non-findings

### Mixed compiled event tables are not automatically a violation

`CompiledOpnaSong` and `CompiledOpnaTimeline` contain PMD semantics and target-hardware controls (`audio/mml/CompiledOpnaSong.kt:122-170`; `audio/mml/CompiledOpnaTimeline.kt:28-76`). A PMD driver program may legitimately carry both while it is being lowered. The violation is that `OpnaLikeSynthesizer` interprets PMD events and owns PMD state, not the mere coexistence of event categories.

### Compiler emission of chip controls is not automatically a violation

Compiling explicit MML hardware commands into chip-facing values is correct driver behavior. Examples include the explicit hardware-LFO path (`audio/mml/MmlCompiler.kt:594-677`) and expansion of explicit SSG register state (`MmlCompiler.kt:985-1037`). The audit targets hidden semantic changes, unresolved relative commands, and reverse dependencies—not every compiler-to-chip mapping.

### Legacy seconds-based ADSR is not itself a PMD boundary violation

`OperatorSpec` carries both float ADSR and OPN EG fields (`audio/opna/OperatorSpec.kt:3-22`). `EgMode.LEGACY_ADSR` is converted to OPN rates by `OpnEnvelopeCompatibility` (`audio/opna/OpnEnvelopeCompatibility.kt:34-83`), and every `OperatorState` renders through `OpnRateEnvelope` (`audio/opna/OperatorState.kt:3-16`). The standalone `Envelope.kt` is not the active operator evaluator.

　user note ＊＊＊ software ADSR evaluator has to be retained as part of the mml/pmd functionality　＊＊＊

### Procedural YM2608 rhythm is not automatically misplaced

The physical YM2608 rhythm block used ROM-backed voices; TimeBox intentionally substitutes procedural clean-room generation. So long as the register-shaped owner retains key/dump/level/pan behavior and does not claim bit-identical ROM reproduction, this is an approximation within the hardware model. It must not be conflated with the PMD K/R bypass in finding 6.

### Missing hardware features are not boundary violations

Missing ADPCM, CSM, timers, or a raw-register frontend are coverage questions. They are defects only if the project claims those features. They do not justify moving PMD state into hardware or replacing the clean-room engine.

### The retained direct sequencer is not the production MML route

`OpnaSequencer`, `OpnaPatterns`, `Scale`, and `NoteLen` contain music/sequencing concepts under `audio/opna`, but current production catalog playback uses the compiled song/timeline/player route. Their namespace is low-priority ownership debt, not evidence that production has two active PMD players. Relocation or removal requires a separate explicit decision.

## File disposition for future repair work

| File/group | Evidence-based disposition | Constraint |
|---|---|---|
| `PmdPerformanceState`, `PmdSoftwareEnvelope`, `PmdSoftwareLfo`, `PmdSampleClock`, PMD laws | PMD/player ownership | Remove dependencies on synth/OPNA implementation constants before package correction. |
| `CompiledOpnaPlayer` | PMD/player ownership | Break the two-way player/synth execution described in finding 2. |
| `CompiledOpnaSong` / `CompiledOpnaTimeline` | Reclassify fields/events during staged repair | Mixed data is not itself wrong; decide which stage consumes PMD semantics and which values are physical controls. |
| `Fm4OpVoice` | Physical OPNA ownership | Remove PMD types, MIDI targets, unresolved relative operations, and PMD portamento policy. Preserve operator/algorithm/EG/DSP behavior. |
| `SsgVoice` / `SsgSharedState` | Physical SSG ownership after split | Keep registers/counters/noise/hardware envelope; move PMD release, software offsets, and portamento policy out. |
| `OpnaLikeSynthesizer` | Physical render owner or thin product entry point after responsibilities move | It must not own or interpret PMD state/events. Output ownership must be explicit. |
| `LlsPatches`, `Patches`, `OpnaPatchBank`, `CompiledInstrumentBank` | Split source catalogs/lookup from register-ready tone data and product defaults | Do not delete catalogs as duplicates or change values during structural repair. |
| `PmdSsgEffectUnit` and K/R mapping | PMD schedules semantic K/R effects; physical SSG owns resulting SSG3 state | Current independent generator is not PMD-faithful SSG3 behavior. |
| `Ym2608RhythmUnit` | Keep as physical register-shaped approximation | Do not claim procedural voices reproduce the original ROM exactly. |
| `OpnaMixer`, profiles, mastering, EQ/resonator, product gains | Output/product ownership | Moving ownership does not authorize gain, EQ, filter, clipping, or stereo changes. |
| direct sequencer/pattern/theory files | Separate later decision | Not on the active catalog route; do not bundle with the PMD/chip repair. |

## Repair order authorized by this audit

This section orders ownership work only. It does not authorize production changes by itself.

1. **Separate PMD execution from chip rendering.** Move tempo, logical parts, gates/key-off decisions, software envelopes/LFOs, FM3 logical ownership, relative commands, portamento, hardware-LFO delay, and K/R scheduling under the PMD/player owner.
2. **Make the live dependency one-way.** PMD/player execution supplies resolved physical controls; OPNA renders current physical state. Remove `Pmd*Frame` parameters and PMD event dispatch from hardware classes without adding a parallel playback path.
3. **Put current physical state in the chip domain.** This includes channel PMS/AMS and absolute register-equivalent TL/detune/feedback/SSG/rhythm values. PMD may retain its documented mirrors and command history.
4. **Correct package declarations after the dependencies are honest.** Moving the nine files first would preserve the cycle under new names.
5. **Split authored lookup from physical tone data and output defaults.** Preserve all catalog entries, lookup precedence, values, and the current compatibility conversion until each has a separately approved behavior decision.
6. **Choose the K/R contract explicitly.** Either reproduce PMD's SSG3 ownership/contention or retain the current parallel procedural effect as a clearly named TimeBox divergence. Do not call both the same behavior.
7. **Move output policy without recalibration.** Package/ownership correction must not change bus gains, headroom, EQ, filters, resonator, clipping, channel routing, or production mono playback.
8. **Handle the inactive direct sequencer last and separately.** Its location does not block repairing the live PMD/OPNA boundary.

Every sound-affecting change remains subject to the project's one-change/one-hypothesis and listening laws. Structural compilation cannot establish acoustic acceptance, and no tests may be created or run unless the user explicitly requests them.

## Reference limits

The text references were sufficient for every ownership conclusion above, so the PDFs were not needed. The PDFs may be necessary later for detailed register diagrams, analog board topology, or transfer-function questions that the OCR text cannot answer.

This audit does not establish cycle accuracy, analog accuracy, PMD numerical equivalence, or acoustic equivalence. It identifies where current state, dependencies, and semantic execution sit on the wrong side of the intended boundary.

## Bottom line

The primary violation is concrete and live:

```text
CompiledOpnaPlayer
    <-> OpnaLikeSynthesizer
          owns PmdPerformanceState
          interprets PMD events
          passes Pmd*Frame into physical FM/SSG voices
          renders chip state
          mixes and masters output
```

The first repair target is therefore not a folder rename and not deletion of patch files. It is the execution direction: PMD must own and finish PMD semantics, then drive an OPNA model that owns only physical state and procedural hardware behavior. Output policy remains a third, explicitly named responsibility.
