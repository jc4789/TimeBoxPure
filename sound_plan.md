# Sound Engine Stabilization and PMD 4.8 Feature-Parity Plan

Status: revised product plan based on the current codebase, `IMPLEMENTATION_PLAN.md`, `repair_plan.md`, the supplied PMD/YM2608 documentation, the decompilable TH04 archives, and the known-good ZUN MML references.

This document plans later work. It does not authorize or contain engine-code changes.

## Corrected objective

The sound engine must become a stable, efficient platform for composing and adding songs, but it must not become feature-thin.

The target is:

> A clean TimeBox MML and procedural OPNA engine that can express the musical behavior available to PMD 4.8-era Touhou/LLS arrangements, without loading PMD binaries or copying PMD's implementation.

The language may be cleaner than PMD MML. The runtime may be organized differently from PMD. The synthesizer may be an original, good-sounding approximation rather than bit-exact YM2608 emulation. However, the musical result must remain expressible: timing, parts, patches, articulation, modulation, envelopes, rhythm patterns, loops, and shared-chip behavior cannot be discarded merely because they are advanced.

The previous phrase "deliberately limited MML2 language" was wrong for this objective. The correct phrase is:

> One deliberately bounded, documented, capability-complete TimeBox MML language.

"Bounded" means one syntax, explicit semantics, compile-time limits, and no compatibility ambiguity. It does not mean a small musical feature set.

## Relationship to the existing plans

- `IMPLEMENTATION_PLAN.md` records the broad YM2608/PMD generalization work, including the exact-size timeline, PMD envelopes and gates, SSG work, LFO, FM3, rhythm controls, song-local patch banks, and corpus tooling.
- `repair_plan.md` is the authoritative technical warning that some of that work still has ownership, evidence, and conformance problems. Its R0-R6 repair gates remain prerequisites.
- `Old_sound_plan.md` is discarded and has no authority.
- This `sound_plan.md` is the productization sequence: what must be retained, repaired, simplified, proven, and frozen before ordinary song composition resumes.

The correct synthesis of the plans is not "undo the PMD features." It is "repair the PMD-era musical feature set, remove duplicate architecture and compatibility scaffolding, then freeze one usable product surface."

## What feature parity means

### 1. Authoring parity

TimeBox MML must be able to author the same musical decisions as the target PMD 4.8 OPNA material. The spelling does not need to match PMD.

For a target song, the author must be able to represent:

- all active musical parts;
- pitch, accidentals, rests, note lengths, raw clock lengths, and tempo changes;
- instruments and full four-operator FM patch data;
- volume, fine/relative volume, gate, ties, slurs, detune, and transpose;
- finite loops, final-pass exits, per-part song loops, and bounded macros;
- software envelopes and their timing modes;
- software and hardware LFO behavior;
- portamento;
- SSG tone/noise/mixer and shared-register behavior;
- PMD K/R rhythm-pattern behavior;
- YM2608 rhythm-unit controls where the PMD-era feature surface requires them;
- FM3 slot behavior and live FM controls where required by PMD 4.8 feature parity;
- pan in a stereo conformance path, even while the current product output remains mono.

### 2. Semantic parity

Authored controls must survive every layer:

```text
MML syntax
    -> compiler state/control
    -> CompiledOpnaSong typed event
    -> CompiledOpnaTimeline ordered event
    -> player dispatch
    -> correct state owner
    -> deterministic reset/loop behavior
```

A parser token with no correct runtime owner is not supported. A runtime feature with no authoring syntax is not supported. A decoded PMD opcode that the engine silently drops is not supported.

### 3. Musical sound parity

The target is an original, good-sounding OPNA-like result, not sample equality.

Acceptable differences:

- oscillator and envelope math may be independently designed;
- procedural percussion may approximate ROM/SSG drum timbres;
- the mix may use a named TimeBox output profile;
- production may remain 48 kHz mono;
- output need not be bit-exact to YM2608, PMDWin, FMGEN, or real hardware.

Unacceptable differences:

- removing a modulation, envelope, rhythm, loop, or channel behavior needed to express the arrangement;
- replacing a stateful behavior with a no-op;
- silently collapsing two distinct PMD behaviors into one when songs depend on the distinction;
- relying on song-title-specific engine branches or corrective hacks.

### 4. What parity does not mean

TimeBox does not need:

- runtime `.M`, `.M86`, or `.M26` loading;
- PMD binary compatibility;
- PMD driver APIs, resident-process behavior, DOS integration, or command-line options;
- PMD source syntax compatibility;
- timer IRQ, status-port, bus-wait, or register-stream emulation beyond musical scheduling;
- copied PMD assembly, PMDWin/FMGEN/ymfm code, or copied lookup tables;
- PCM/ADPCM/P86/PPS/PPZ sample systems unless a later explicit procedural/legal design expands the project scope;
- external rhythm ROM samples, WAV files, or copied sample bytes.

## Evidence base

### Decompilable TH04 corpus

`tools/pmd_corpus_audit.py` uses THTK only as an offline extractor. Extraction occurs in a temporary directory and the PMD payloads are not written into the repository or read by runtime code.

The audited archives are:

- `東方幻想.郷`, SHA-256 `4d975850a66b4ec2ca5fd4fba7f3166dd9293d220ba8ab6c1be87ed98b1399be`;
- `幻想郷ED.DAT`, SHA-256 `ca787b8ff66f7b3f10c97b3ecc77cd466772767e3e9e8cf5a0c71dd612b1c8d7`.

The 2026-07-16 audit found:

- 80 archive entries;
- 45 unique PMD payloads;
- 23 unique `.M86` payloads;
- 23 unique `.M26` payloads;
- zero scan or file-boundary errors.

`.M86` supplies the primary six-FM OPNA view. `.M26` supplies a three-FM OPN comparison for shared FM/SSG behavior. Archive hash and entry hash are required because filenames alone do not identify a unique arrangement.

### Human-readable musical references

- `TH4 Bad Apple!! MML Dump.txt` is the readable ST02/Bad Apple arrangement reference.
- `PC-98 Eternal Shrine Maiden.txt` is a second known-good ZUN PMD-style reference with extensive repeats, alternate exits, rhythm patterns, ties, detune, volume changes, and pan.
- `MUSIC.TXT` identifies the official `_86.M` material as FM6 + SSG3 + rhythm and the `_26.M` material as the standard FM configuration.
- `PMDMML.MAN`, `PMD.DOC`, `PMDWin.txt`, `DLLInfop.txt`, `PSGEDATA.DOC`, `MUCOM88_REF.txt`, and the supplied PC-88VA/YM2608 material define the historical behavior and hardware vocabulary.

These sources are behavioral references. They are not runtime inputs and do not license copying implementations or redistributing source payloads.

### Current Bad Apple proof and its limit

ST02 is anchored to SHA-256 `60e0e4e9742db3d97bd02238f2602ad7f671c71077479d71593e69710be8f130`.

The current offline comparison reports zero event-count/timing/pitch/patch mismatches for:

- A: 487 events;
- B: 205 events;
- C: 205 events;
- D: 570 events;
- E: 570 events;
- G: 712 events;
- H: 710 events.

That is valuable, but it is not a full PMD semantic proof. It does not by itself prove gate, volume, envelope, LFO, rhythm, shared-register, or final audio equivalence. The plan must not overstate this result.

## Corpus capability findings

The following behaviors are not speculative. They occur in the 45-song TH04 corpus and therefore belong to the mandatory LLS parity tier.

| Behavior | Unique songs | Occurrences/evidence | Decision |
| --- | ---: | ---: | --- |
| Finite loop start/end | 45 | 2,252 starts + 2,252 ends | Core language/runtime behavior. |
| Final-pass loop exit | 42 | 648 | Core; cannot be deferred. |
| Per-part song loop | 44 | 325 | Core deterministic transport state. |
| Tempo | 45 | 852 | Preserve PMD clock/rational timing. |
| Instrument selection | 45 | 942 | Core, with song-local banks. |
| Volume | 45 | 1,415 | Core part state. |
| Software envelope | 45 | 447 | Core SSG performance state. |
| Software-LFO clock setup | 45 | 900 clock-mode controls | Core state even when dormant. |
| Active software LFO | 5 | Definitions/waves/switches in LOGO, ST03, STAFF variants | Required and must be independently verified. |
| Transpose/master transpose | 43-44 | More than 1,500 combined controls | Core compiler/runtime state. |
| Detune | 37 | 483 | Core. |
| Gate Q | 35 | 637 | Core, including correct PMD clock meaning. |
| Portamento | 26 | 123 | Core; the earlier deferral was wrong. |
| Tie | 22 | 106 | Core no-retrigger behavior. |
| PMD K/R rhythm patterns | 44 | 603 decoded patterns | Core rhythm behavior. |
| Bar-length changes | 3 | 26 | Required by corpus. |
| Step/fine volume variants | 1-2 | 91+ controls | Required, preferably normalized to a clean authoring form. |

The corpus contains no direct occurrences of:

- general direct-register writes;
- extended/random gate opcodes;
- FM3/live-FM control opcodes;
- hardware-LFO opcodes;
- pan opcodes;
- direct YM2608 rhythm-unit control opcodes;
- standalone SSG-effect trigger opcodes.

Zero corpus occurrence does not automatically make a feature removable. The user requested PMD 4.8-era feature parity, not only a TH04 bytecode clone. These zero-occurrence areas form the PMD 4.8 parity-completion tier and must be decided from the manual and known-good song references. Eternal Shrine Maiden, for example, provides readable pan use outside the TH04 archive corpus.

Three opcode labels in the current scanner remain deliberately unresolved/reserved in a few songs (`0xCD`, `0xED`, `0xEE`). They must be identified before claiming complete LLS semantic coverage. They may not be ignored because their occurrence count is small.

## Capability tiers

### Tier A - Mandatory LLS corpus parity

Everything observed in the 45 unique TH04 payloads is mandatory. Tier A is not subject to the future feature-admission rule.

This includes the full loop model, PMD timing, two software-LFO state slots and clock modes, the active LFO subset, both software-envelope forms/modes where observed, detune, gate, portamento, tie/slur behavior, transposition, K/R rhythm patterns, bar length, volume variants, and all resolved special-control subcommands.

### Tier B - PMD 4.8 OPNA musical feature parity

These are composer-visible PMD 4.8/YM2608 behaviors that may not occur in the TH04 corpus but belong to the era-level platform target:

- hardware LFO global enable/rate and per-channel PMS/AMS/delay;
- FM3 extended slot ownership, patch isolation, slot detune, TL, feedback, and key-on delay;
- pan and the stereo conformance route;
- extended gate behaviors;
- typed SSG mixer/noise/hardware-envelope controls;
- typed YM2608 rhythm shot/dump/master/voice-level/pan controls;
- both PMD software LFOs, documented waves, targets, sync modes, timing modes, TL masks, and depth evolution;
- the documented software-envelope variants and clock modes;
- typed musical equivalents for live controls that PMD exposes through register-oriented commands.

Tier B can be completed after the Tier A songs are stable, but the engine must not advertise "PMD 4.8 feature parity" until Tier B is connected and verified.

### Tier C - Compatibility and driver machinery outside the product goal

- PMD binary parsing at runtime;
- raw register-stream playback;
- generic raw-register authoring as a substitute for typed semantics;
- PMD process/status/fade/mask/integration commands;
- DOS resident-driver behavior;
- PCM, ADPCM-B, 86PCM, PPSDRV, PPZ8, or P86 file/sample systems;
- ROM-authentic rhythm samples;
- arbitrary hardware timer/IRQ/CSM emulation unrelated to authored musical scheduling.

Tier C stays out. If a Tier C mechanism produces a musically relevant result, add the smallest typed TimeBox semantic operation rather than importing the compatibility mechanism.

## What is actually bloated

The problem is not the existence of PMD-era musical capabilities. The problem is duplicated architecture, unclear ownership, unproven semantics, and product/research paths mixed together.

### Accidental complexity to remove

- MML V1 and MML2 both remaining callable.
- The compiled timeline and the old mutable `OpnaSequencer` both appearing to be product architectures.
- `OpnaLikeSynthesizer` exposing product, legacy, raw, stereo, preview, and research entry points through one large facade.
- Legacy drums, YM2608 rhythm controls, and PMD K/R effects each owning loosely coordinated generator/mix state.
- Android selecting oversampling, filtering, EQ, and other synthesis policy.
- Eager compilation of unused songs and timeline construction repeated for playback/volume changes.
- One-value routing/kind abstractions and ignored parameters.
- Research output profiles and resonator behavior living in the default lifetime without a named experiment.
- Setup and callback work for inactive parts/features when a fast inactive path would suffice.
- Capability reports that can call decoded behavior supported before authoring/runtime/reset/independent evidence exists.

### Required complexity to organize, not delete

- PMD logical part state separated from physical FM/SSG voices;
- two software LFOs per applicable logical part;
- per-SSG-part software envelopes;
- shared SSG noise and hardware-envelope state;
- FM3 shared-channel/slot ownership;
- global hardware LFO with per-channel PMS/AMS;
- K/R rhythm-pattern semantics and typed YM2608 rhythm semantics;
- exact same-time event ordering;
- deterministic random gate/LFO behavior;
- tempo-clocked and fixed-clock driver state;
- separate raw-conformance and product-mastering inspection points.

The stabilization work should make this required state explicit, typed, preallocated, and dormant when unused. Deleting it would miss the clarified goal.

## Target architecture

### Three representations

```text
Offline PMD observation
    archives -> temporary extraction -> opcode/state reports/oracles

Authored TimeBox music
    readable embedded MML + auditable song-local patch definitions

Runtime product program
    exact-size ordered primitive timeline -> one player -> one synth core
```

No PMD/archive bytes cross from the observation layer into the authored or runtime layer.

### Four runtime owners

1. `OpnaChipState`
   - physical FM, SSG, hardware-LFO and procedural rhythm generation;
   - chip/register-equivalent state only.

2. `PmdPerformanceState`
   - logical-part volume, gate, detune, slot masks, software LFOs, software envelopes, clock modes, and deterministic driver state;
   - independent of physical voice allocation.

3. `OpnaOutputProfile` / `OpnaMixer`
   - named FM/SSG/rhythm bus relationships and optional board-response hypotheses.

4. `SongMastering`
   - product gain, optional song EQ, output filtering, resonator, clipping, and their history state.

`OpnaLikeSynthesizer` may coordinate these owners, but it must not blur their reset rules or make one owner a hidden field of another.

### One product playback route

```text
Song registry
    -> prepared MML compile result
    -> cached sample-rate/profile-specific immutable timeline
    -> fresh mutable player + synthesizer state
    -> contiguous caller-buffer render
    -> common product output profile/mastering
    -> platform PCM presenter
```

The old sequencer may remain only as an explicitly non-product procedural experiment while fixtures are migrated. It must not be a second catalog path or a second PMD event dispatcher.

## Stable TimeBox MML design

### Language policy

- One production language version.
- TimeBox semantics, not a mixture of PMD/MUCOM dialect spellings.
- Clear errors for unsupported or ambiguous commands.
- Every stable command documented with state ownership, units, reset behavior, and same-tick ordering.
- Compiler-side limits for macro depth, loop nesting, expanded events, and song duration.
- Metadata remains in the declarative song registry unless it has genuine authoring value.
- PMD imports are offline research/conversion work; the product parser never accepts `.M` bytes.

### Compiler-side features

Keep these out of the callback:

- source macros and named patterns;
- finite-loop expansion/control flow;
- final-pass exits;
- source-level length/dot/tick arithmetic;
- diagnostics and source mapping;
- patch/name resolution;
- capability validation;
- shared-state conflict analysis;
- event counting and exact-size allocation;
- canonical same-time ordering.

### Runtime semantic modules

Organize the language reference into modules without making them separate dialects:

1. Notes and clocks.
2. Part routing and patches.
3. Volume, gate, tie/slur, detune, transpose and portamento.
4. Tempo, bar length, local loops, loop exits and part loops.
5. SSG tone/noise/software envelope/hardware envelope.
6. Software LFO A/B and hardware LFO.
7. FM3 and live typed FM controls.
8. K/R procedural rhythm patterns and YM2608 rhythm controls.
9. Pan/stereo conformance behavior.

This makes the language learnable while preserving capability. A composer can ignore advanced modules until a song needs them.

## Rhythm decision

The previous proposal to collapse all percussion into one generic direct rhythm lane is incorrect.

The TH04 corpus uses PMD K/R patterns in 44 of 45 unique songs, with 603 decoded pattern definitions. It contains no direct YM2608 rhythm-control opcodes. Therefore:

- K/R pattern authoring and playback is Tier A core behavior.
- K/R must remain semantically distinct from direct YM2608 rhythm-unit control.
- A TimeBox `R` lane may use cleaner syntax, but it must be capable of expressing the K/R pattern result.
- The compiler must preserve pattern timing, instrument selection, repeats, and SSG3/part-ownership implications.
- The current corpus has essentially no simultaneous melodic I-part plus K/R use; the compiler should still define and diagnose ownership rather than relying on this accident.
- Direct YM2608 rhythm shot/dump/level/pan remains a Tier B typed feature.
- Procedural generation remains mandatory; no rhythm-ROM samples are imported.

Implementation may share low-level oscillator/noise/envelope primitives, but K/R SSG percussion, YM2608 rhythm control, and any simple TimeBox drum facade may not silently become the same state machine.

## LFO decision

LFO is not optional compatibility trivia.

- Both software-LFO clock-mode controls occur throughout the corpus.
- Active software-LFO definition/wave/switch behavior occurs in five unique songs, especially LOGO, ST03 and STAFF material.
- The observed active TH04 mode includes fixed-clock waveform 6/one-shot behavior.
- PMD 4.8 parity requires the broader documented two-LFO state model.
- Hardware-LFO opcodes do not occur in this TH04 corpus, but hardware LFO remains a Tier B OPNA/PMD-era capability.

The optimization is an explicit inactive fast path, not removing LFO state. Every active logical part receives deterministic preallocated state, while inactive states should cost only a small branch and no per-sample modulation work.

## FM3 decision

The TH04 corpus contains no FM3/live-FM opcodes, so FM3 is not a Tier A release blocker. It is still a Tier B PMD 4.8 capability.

Retain and repair:

- explicit physical slot masks;
- shared algorithm state;
- slot-selective patch application;
- feedback ownership rules;
- per-slot detune;
- TL/feedback live controls;
- slot key-on delay;
- time-aware ownership and overlap diagnostics;
- software-LFO TL masks.

Do not pay the full FM3 render cost when extended mode is inactive.

## Ordered implementation plan

### Phase S0 - Freeze the parity charter and listening baseline

Actions:

1. Adopt the parity definition in this document.
2. Record current production settings, output profile, gains, filters, oversampling, EQ, and lifecycle behavior.
3. Select listening passages from Bad Apple, LOGO, a portamento-heavy song, an SSG/rhythm-heavy song, STAFF, and Eternal Shrine Maiden.
4. Keep the two archive hashes and entry hashes with every oracle/report.
5. Separate automated semantic evidence from human tonal approval.

Why: cleanup cannot be judged against a vague goal. The parity charter prevents both compatibility creep and destructive feature pruning.

Exit gate:

- Tier A, Tier B, and Tier C are accepted as the scope boundary;
- the listening suite and operational baseline are recorded;
- no feature is removed solely because the current two-song catalog does not use it.

### Phase S1 - Complete repair R0: make capability reporting truthful

Actions:

1. Keep every decoded opcode/subcommand `OBSERVED_ONLY`, `PARTIAL`, `EXACT`, or `UNSUPPORTED` based on connected evidence, never manual optimism.
2. Require evidence across observation, authoring, compiled event, timeline event, runtime dispatch, reset, and independent verification.
3. Produce at least four genuinely independent register/state checkpoint traces; normalized note lists alone are insufficient.
4. Resolve `0xCD`, `0xED`, and `0xEE` before declaring Tier A complete.
5. Keep generated reports outside runtime and extracted files outside the repository.

Why: a broad engine is usable only when its support claims fail closed.

Exit gate:

- the audit cannot label an unconnected capability `EXACT`;
- four independent, diverse checkpoint traces exist;
- the strict 45-song scan has zero boundary errors and no unexplained observed opcode.

### Phase S2 - Complete repair R1-R4: fix state ownership and PMD timing

Actions:

1. Separate `OpnaChipState`, `PmdPerformanceState`, mixer/profile, and mastering ownership exactly as `repair_plan.md` specifies.
2. Preserve the 32-level SSG hardware-envelope path separately from the 16-code fixed-volume path.
3. Convert SSG mixer, shared noise period, hardware-envelope period, shape and restart into ordered typed events.
4. Remove shared-register side effects from `SsgVoice.applyPatch`; expand legacy patch defaults into explicit compile-time events during migration.
5. Repair hardware-LFO global/per-channel state ordering.
6. Repair PMD software-LFO start timing, waveform timing, clock modes, sync/free-run behavior, targets, depth evolution and deterministic random reset.
7. Keep logical-part performance state independent of dynamically selected physical voices.

Why: these are current architecture blockers. Adding more songs before fixing them would turn song-specific coincidences into permanent engine law.

Exit gate:

- all four owners have independent reset contracts;
- shared SSG state is visible in the primitive timeline;
- active software LFOs match independent state traces;
- callback allocation and chunk/reset determinism remain clean.

### Phase S3 - Complete repair R5: establish one conformance/product render core

Actions:

1. Define one internal raw chip/driver render core that does not advance a timeline or apply mastering.
2. Define one timeline-aware raw render path using the same `CompiledOpnaPlayer` and dispatcher as production.
3. Apply output profile, mixer and `SongMastering` only after shared raw rendering.
4. Remove any duplicate raw/product event dispatcher.
5. Maintain inspection points before output profile, after output profile, and after mastering.
6. Use independent FM/SSG/LFO/rhythm state vectors rather than production code to generate expectations.

Why: sound defects cannot be localized while chip generation, driver modulation, board mix and product mastering are inseparable.

Exit gate:

- raw and product rendering share the same event and synth core;
- conformance rendering bypasses discretionary mastering;
- chunk size and loop reset produce deterministic raw output.

### Phase S4 - Finish Tier A LLS semantic parity

Actions:

1. Map every observed TH04 capability to a documented TimeBox MML operation.
2. Preserve PMD clock units and state transitions rather than approximating them early as float BPM or unrelated compiler ticks.
3. Complete finite loops, loop exits and part-loop state restoration/re-entry rules.
4. Complete the observed software-envelope and software-LFO behaviors.
5. Complete gate, portamento, tie/slur, detune, transpose, volume variants and bar-length behavior.
6. Make K/R patterns fully authorable, ordered, resettable and procedurally rendered.
7. Convert several contrasting archive songs into readable TimeBox MML without adding per-song engine branches.

Why: Tier A is the concrete proof that the platform can support LLS music rather than only its current hand-picked subset.

Exit gate:

- every used opcode/subcommand in all 45 unique songs has an `EXACT` typed semantic mapping or an explicitly reviewed typed approximation;
- at least four contrasting full songs can be manually represented without an engine change during transcription;
- Bad Apple includes verified rhythm/envelope/gate/state behavior beyond the current seven-lane note comparison.

### Phase S5 - Finish Tier B PMD 4.8 OPNA feature parity

Actions:

1. Complete hardware LFO, FM3/live FM controls, typed extended gates, pan/stereo conformance, hardware-envelope controls and direct YM2608 rhythm semantics.
2. Use PMD 4.8 manuals and known-good readable songs to exercise features absent from TH04.
3. Replace any need for generic raw-register authoring with the smallest typed musical control.
4. Document unsupported driver/sample features as Tier C rather than implying full PMD compatibility.

Why: passing the TH04 corpus proves LLS parity; Tier B completes the requested reusable PMD 4.8-era OPNA composing platform.

Exit gate:

- the documented composer-visible PMD 4.8 OPNA feature matrix is connected end to end;
- no claim of file, driver, PCM, or bit-level compatibility is made;
- an original conformance score exercises every stable Tier B module.

### Phase S6 - Remove duplicate product architecture without removing parity

Actions:

1. Freeze MML2/TimeBox MML as the sole product language and archive/remove MML V1 production paths.
2. Migrate useful `OpnaPatterns`/direct-note listening fixtures to authored timelines.
3. Remove the old sequencer as a catalog/PMD path and remove duplicate dispatch overloads.
4. Reduce `OpnaLikeSynthesizer` to one product entry point plus clearly named internal conformance inspection points.
5. Remove ignored parameters, redundant one-value wrappers and misleading Senbonzakura names.
6. Keep advanced PMD state in the one timeline/player rather than creating compatibility translators.

Why: one expressive engine is maintainable; two smaller engines are not.

Exit gate:

- the catalog has exactly one route to audio;
- PMD-era controls have one runtime dispatcher;
- no test is the sole owner of a second architecture.

### Phase S7 - Consolidate rhythm implementation carefully

Actions:

1. Define one top-level procedural rhythm subsystem and mixer ownership boundary.
2. Preserve separate typed semantics for K/R SSG percussion and YM2608 rhythm controls.
3. Share mathematical oscillator/noise/envelope primitives where behavior permits.
4. Remove the legacy direct-drum route after its content is translated.
5. Define choke, retrigger, shot/dump, levels, pan, reset and same-tick ordering explicitly.
6. Ensure inactive rhythm modes do no generator work.

Why: three unrelated render domains are bloated, but flattening K/R and YM rhythm into one command model would destroy required behavior.

Exit gate:

- one subsystem owns rhythm buffers/mixing/reset;
- K/R and YM rhythm remain semantically distinguishable;
- no samples/assets are introduced;
- at least three rhythmically different songs pass listening review.

### Phase S8 - Make setup and callbacks efficient

Actions:

1. Cache immutable compiled songs and timelines by song, actual sample rate and relevant output-profile fields.
2. Never cache mutable players or synth state.
3. Apply user volume after the fixed authored mastering chain and before only a safety clamp.
4. Add explicit inactive fast paths for unused LFO, FM3, SSG-envelope and rhythm states.
5. Render only active physical voices/modes while preserving preallocated logical state.
6. Choose oversampling from listening plus measured target-device cost rather than enabling it blindly for all 16 FM render voices.
7. Simplify parallel event arrays only after the final Tier A/B vocabulary is frozen.

Why: performance comes from avoiding dormant work and repeated setup, not from deleting musical capability.

Exit gate:

- zero callback allocation;
- no callback parsing, sorting, locks, I/O, collection growth or logging;
- p95 render time stays below 50% of buffer duration on the slowest supported target;
- volume changes perform no parse, compile, sort or timeline reconstruction.

### Phase S9 - Make platform playback operationally robust

Actions:

1. Give natural end, explicit stop, error exit, alarm cancellation and restart one cleanup path.
2. Keep thread and playing state consistent on every exit.
3. Hold/release wake locks for the actual playback lifetime.
4. Drain partial `AudioTrack.write` results correctly and handle zero, negative, interrupted and shutdown results without skipping song frames.
5. State that normal rendering is contiguous; make restart/seek explicit.
6. Move oversampling/filter/profile/mastering policy into common code.
7. Leave Android as a PCM presenter and lifecycle adapter.

Why: a feature-complete music engine is not usable if long alarms, natural completion or restart are unreliable.

Exit gate:

- repeated start/stop/restart is clean;
- natural end releases the same resources as stop;
- a 30+ minute loop has no wake-lock lapse, underrun or stale state;
- platform code owns no synthesis policy.

### Phase S10 - Curate sound and resume composition

Actions:

1. Normalize retained FM patches to canonical OPN register/rate definitions.
2. Keep song-local immutable banks for recovered/reference-derived voices.
3. Tune patches, procedural percussion and bus ratios across the multi-song listening suite.
4. Keep `TIMEBOX_LEGACY` and a named PC-98 reference hypothesis distinct until listening approves a default.
5. Remove per-song EQ from core MML; retain it only as an explicit mastering extension if genuinely needed.
6. Write one original conformance score covering the stable language.
7. Write one complete original song without changing the engine.
8. Resume adding songs through the R6 ingestion checklist.

Why: composing is the final product test. Once songs can be added without engine edits, the system has left development mode.

Exit gate:

- adding a song requires readable MML, a compact patch bank, metadata and one registry entry;
- it requires no song-name branch, new event type, platform change or hidden asset;
- multiple songs sound balanced without global tuning for only Bad Apple;
- the stable language reference matches actual behavior.

## Acceptance suite

### Semantic corpus acceptance

- both archives scan with zero boundary errors;
- archive and entry identities are hashed;
- all Tier A opcodes/subcommands are resolved;
- four independent state/register checkpoint traces exist;
- selected full-song normalized traces cover timing, pitch, gate, patch, volume, detune, envelope, LFO, SSG shared state, rhythm and loop state;
- `.M86` is primary and `.M26` is a comparison, not a blind replacement;
- no `.M` bytes enter runtime or authored source.

### Musical listening acceptance

Listen on neutral headphones, ordinary earbuds and a phone speaker.

- sparse FM lead/bass: pitch, envelope, feedback and alias character;
- dense six-FM passage: separation and headroom;
- SSG melody: pitch quantization, level and fatigue;
- SSG envelope passage: articulation under tempo and fixed clock modes;
- active software LFO: start time, waveform, depth, target and reset;
- portamento-heavy passage: start/end pitch and duration;
- K/R rhythm: pattern timing, transient identity and SSG balance;
- YM rhythm: shot/dump/level/pan in conformance stereo;
- full mix: FM/SSG/rhythm balance and clipping;
- loop boundary: no click, retrigger, missing state or drift.

Automated hashes and spectra support this review but do not replace it.

### Operational acceptance

- prepare failures are reported before the audio thread starts;
- cache initialization is safe under simultaneous requests;
- start, stop, natural end and restart share cleanup;
- long looping playback remains active and interruption-free;
- partial platform writes do not lose frames;
- output remains finite with no NaN/Inf;
- callback allocation and hot-loop audits pass.

## Testing policy

The goal is not preserving every historical test or achieving a fashionable test count.

Keep tests/oracles that protect:

- capability truthfulness;
- parser diagnostics and limits;
- PMD clock/gate/loop semantics;
- typed same-time ordering;
- deterministic reset and random state;
- FM/SSG/LFO/rhythm state laws;
- allocation-free callback behavior;
- raw/product render boundaries;
- song-local patch isolation;
- platform streaming lifecycle.

Delete or rewrite tests whose only purpose is retaining MML V1, a second catalog sequencer, duplicate dispatch, misleading compatibility behavior, or deliberately removed Tier C machinery.

## Clean-room and asset rules

1. The archive auditor remains offline tooling.
2. THTK extraction stays temporary or ignored and never becomes a build/runtime dependency.
3. Runtime code never reads archives, `.M` data, JSON reports, or external MML.
4. Derived oracles contain only compact, human-auditable semantic facts with source hashes; no archive/audio payloads.
5. No PMD assembly, emulator implementation, ROM data, sample bytes or opaque generated tables are copied.
6. FM, SSG and percussion remain mathematically/procedurally generated.
7. Existing transcriptions have a separate provenance/rights decision; technical clean-room status does not decide redistribution rights.
8. Original conformance fixtures and original songs are used for product acceptance.

## Feature-admission rule after PMD 4.8 parity

Tier A and Tier B features are the agreed baseline; they do not need to re-justify their existence through a new TimeBox song.

A feature beyond that baseline is admitted only when:

1. a real composition has a specific need;
2. the stable PMD-era surface cannot express it;
3. the smallest typed operation has bounded authoring, runtime, reset and CPU behavior;
4. it does not introduce another playback architecture or platform-owned music logic;
5. it has an original fixture and documentation;
6. it has a clear long-term owner.

## Completion definition

The sound engine is stable when all of the following are true:

- Tier A LLS corpus parity is connected and independently evidenced.
- Tier B PMD 4.8 OPNA musical parity is documented and connected.
- Tier C compatibility/sample/driver machinery remains outside runtime.
- `repair_plan.md` R0-R5 ownership and evidence blockers are resolved.
- one authored language, one timeline, one player, one dispatcher and one product render route remain.
- advanced PMD state is preallocated, deterministic and cheap when inactive.
- K/R and YM rhythm semantics are preserved without sample assets.
- platform playback is reliable for long alarms and repeated lifecycle use.
- multiple contrasting songs pass human listening review.
- a new song can be added without changing the engine.

At that point, the engine is no longer an experiment that accumulates commands. It is a clean, broad, frozen PMD 4.8-era composing platform whose complexity corresponds to real musical capability rather than duplicated implementation.

## Recommended first implementation slice

When code work begins, do only S0-S3 first:

1. freeze the parity charter and listening set;
2. finish truthful capability evidence and independent checkpoints;
3. repair runtime ownership, SSG shared events and LFO semantics;
4. establish one raw/product rendering core.

Do not delete PMD-era features or add more catalog songs during this slice. Once the repair blockers are resolved, complete Tier A against the 45-song corpus, then Tier B, then remove duplicate architecture and resume composition.
