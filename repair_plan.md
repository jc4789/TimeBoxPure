# YM2608/PMD Architecture Repair Plan

Date: 2026-07-13

## Objective

Repair the early YM2608/PMD implementation so the engine can accept additional songs without accumulating Bad Apple-specific behavior, hidden driver state, or unsupported PMD semantics.

The repaired system must remain:

- clean-room and independently derived;
- procedural and assetless at runtime;
- Kotlin Multiplatform in `commonMain`;
- allocation-free in audio callbacks;
- driven by embedded, human-readable MML and compact instrument definitions;
- deterministic across render chunk sizes and loop resets;
- explicit about what is exact, approximate, unsupported, or awaiting listening approval.

This is a repair of the architecture in `IMPLEMENTATION_PLAN.md` lines 94–220. It is not a request for a PMD binary interpreter, an emulator transplant, a sample-based rhythm system, or a new application framework.

## Current Disposition

The audit found that the foundation is usable but not ready for unrestricted catalog expansion.

| Area | Disposition | Main reason |
|---|---|---|
| Phase 0 corpus/oracle | Partial | Capability reporting can claim support that the runtime cannot represent; the four traces are semantic part-A traces rather than independent register/state traces. |
| Phase 1 exact timeline | Pass | Exact-size arrays, canonical ordering, cursor playback, deterministic reset, and production routing are structurally sound. |
| Phase 2 timing/gate/envelope | Pass with evidence gaps | Generalized timing, gates, randomization, and software envelopes exist, but independent PMD vectors remain limited. |
| Phase 3 SSG core | Partial and blocking | Hardware-envelope amplitude is reduced from 32 steps to 16, and shared SSG register controls are not explicit timeline events. |
| Phase 4 LFO | Fail and blocking | Software-LFO timing defects exist; hardware LFO remains approximate and is not an ordered runtime program state. |
| Phase 5 FM conformance | Partial | Useful core differential tests exist, but coverage, raw pan/output separation, patch-field proof, and human A/B acceptance remain incomplete. |

No new production catalog song should be accepted until the blocking Phase 3 and Phase 4 repairs below are complete. A test-only song fixture may be used during implementation only when every required command is explicitly classified and unsupported behavior fails closed.

## Governing Architecture

### Three representations

Keep the three representations separate:

1. **Offline PMD observation model**
   - Reads legally supplied `.M86`/`.M26` archives through tooling only.
   - Records hashes, commands, normalized semantic traces, and independent state/register checkpoints.
   - May emit ignored reports or compact test-only semantic oracles.
   - Never becomes a runtime dependency.

2. **Authored MML/control model**
   - Embedded, human-readable MML remains the source of truth.
   - Compact Kotlin instrument definitions remain auditable.
   - Unsupported source behavior must be rejected or explicitly documented; it must not silently disappear.

3. **Runtime primitive program**
   - Exact-size, canonically ordered structure-of-arrays data only.
   - Contains all state required to reproduce playback and loop reset.
   - Does not retain parser objects, catalog objects, file paths, JSON, archive data, or PMD binaries.

### Runtime ownership

Enforce four distinct ownership domains:

- `OpnaChipState`: physical FM/SSG/hardware-LFO/rhythm generator and register state.
- `PmdPerformanceState`: logical-part driver state, including volume, detune, gates, slot masks, software envelopes, and two software LFOs per applicable part.
- `OpnaOutputProfile`: named board/chip bus ratios and optional board-response hypotheses.
- `SongMastering`: playback gain, song EQ, product filtering, resonator state, and soft clipping.

`OpnaChipState` must not own `PmdPerformanceState`. Physical voices must not become the lifetime owner of PMD part state. The synthesizer may coordinate the four domains, but their reset and mutation boundaries must remain explicit.

### Event ordering

Every runtime state change must have an explicit type and same-time precedence:

1. global clock and global chip controls;
2. driver/chip state and register-like controls;
3. key-offs and rhythm dumps;
4. key-ons and rhythm shots;
5. zero-gate key-offs.

Source order resolves events within the same precedence class. Playback must never depend on incidental insertion-sort stability.

## Repair Phase R0 — Make Capability Reporting Truthful

### Purpose

Prevent the ingestion checklist from approving a song whose commands cannot be represented by authored MML and the runtime primitive program.

### Work

- Replace the broad hand-maintained `CURRENT_IMPORT_PRESERVED` assumption with a capability table that distinguishes:
  - `EXACT`: decoded, authorable, compiled, dispatched, reset, and independently checked;
  - `PARTIAL`: decoded but missing one or more authored/runtime/conformance stages;
  - `OBSERVED_ONLY`: retained in an offline trace but not representable;
  - `UNSUPPORTED`: rejected for ingestion.
- Define a capability only when all layers are linked:
  - PMD opcode/subcommand;
  - normalized observation field;
  - MML/control syntax;
  - `CompiledOpnaSong` event;
  - `CompiledOpnaTimeline` event;
  - runtime dispatcher and owner;
  - reset behavior;
  - independent oracle or explicitly documented approximation.
- Stop classifying hardware-LFO global/rate/delay commands as preserved until ordered runtime events exist.
- Generate genuine register/state checkpoint traces for at least four contrasting songs and relevant parts, not only part-A note lists.
- Keep archive identity, entry SHA-256, format, and driver profile attached to every oracle.
- Make strict ingestion fail when a used command is `PARTIAL`, `OBSERVED_ONLY`, or `UNSUPPORTED`, unless the candidate is deliberately marked as a non-catalog research fixture.

### Target files

- `tools/pmd_corpus_format.py`
- `tools/pmd_corpus_audit.py`
- `tools/pmd_corpus_audit_test.py`
- `tools/oracles/`
- `tools/README.md`

### Acceptance gate

- A report cannot call an opcode preserved when no corresponding authored and runtime event path exists.
- Four contrasting traces contain named independent state/register checkpoints with source hashes.
- Offline JSON/CSV remains tooling/test data and is never read by runtime code.

## Repair Phase R1 — Restore Chip/Driver Ownership

### Purpose

Finish the ownership repair that was applied to Phases 6–8 but not fully propagated back through Phases 2–5.

### Work

- Remove `PmdPerformanceState` ownership from `OpnaChipState`.
- Make the coordinating synthesizer own separate chip, performance, output-profile, and mastering state.
- Extend `PmdPerformanceState` from FM3-only state to explicit logical-part state for:
  - six normal FM parts;
  - three SSG parts;
  - four FM3 extended parts;
  - any separate PMD rhythm/effect driver state that is not a physical chip register.
- Give each applicable logical part two preallocated `PmdSoftwareLfo` states.
- Move PMD software-envelope lifetime and tempo-clock state out of physical `SsgVoice` ownership.
- Keep physical FM/SSG voices responsible only for chip/operator/counter/envelope generation required to render the current register state.
- Pass primitive modulation/attenuation/pitch values from driver state into chip rendering without allocations, generic containers, or callbacks.
- Define reset explicitly:
  - chip reset restores physical generator/register state;
  - performance reset restores authored driver defaults and deterministic seeds;
  - output-profile selection remains configured;
  - mastering reset clears filter/EQ/resonator history without discarding configuration.

### Target files

- `OpnaChipState.kt`
- `PmdPerformanceState.kt`
- `PmdSoftwareLfo.kt`
- `PmdSoftwareEnvelope.kt`
- `Fm4OpVoice.kt`
- `SsgVoice.kt`
- `OpnaLikeSynthesizer.kt`
- `CompiledOpnaPlayer.kt`

### Hot-path rules

- Preallocate all part state during synthesizer setup.
- Use primitive arrays, fixed state objects, and `while` loops.
- Do not introduce maps, lists, sealed event objects, lambdas, coroutines, or per-frame adapters.
- Do not make logical-part state depend on a dynamically selected physical voice.

### Acceptance gate

- `OpnaChipState` contains no PMD performance owner.
- Normal FM, SSG, and FM3 logical parts preserve independent two-LFO and envelope state.
- Loop reset restores both chip and driver domains deterministically.
- The raw chip render path can operate without `SongMastering` state.

## Repair Phase R2 — Correct the SSG Hardware Envelope

### Purpose

Restore the YM2608/AY-family distinction between four-bit fixed volume and the five-bit hardware-envelope level.

### Work

- Keep fixed volume as the documented 16-code path using the generated odd DAC steps.
- Add a separate generated 32-step hardware-envelope amplitude law.
- Preserve envelope level `0..31` end to end; remove the `ushr 1` collapse before rendering.
- Make `SsgVoice` select either:
  - fixed-volume amplitude from the 16-code table; or
  - hardware-envelope amplitude from the 32-step table.
- Keep envelope period writes phase-preserving and shape writes deterministically restarting.
- Derive both tables mathematically from named DAC laws; do not add captured samples or opaque blobs.

### Target files

- `SsgHardwareLaws.kt`
- `SsgSharedState.kt`
- `SsgVoice.kt`
- `SsgHardwareConformanceTest.kt`

### Acceptance gate

- All 32 hardware-envelope levels are observable and monotonically follow the named DAC law.
- Fixed-volume behavior remains a separate 16-code contract.
- Shape boundaries, holds, alternation, restart, and period-write behavior remain deterministic.

## Repair Phase R3 — Add Typed SSG Shared-Register Events

### Purpose

Allow SSG-heavy songs to express shared chip state without hiding register writes inside note-on patch application.

### Work

- Add semantic primitive events for:
  - register-7 tone enable bits;
  - register-7 noise enable bits;
  - register-6 shared noise period;
  - hardware-envelope period;
  - hardware-envelope shape/restart;
  - any source attribution needed for conflict diagnostics.
- Add matching authored MML/control directives only for documented semantic behavior. Do not introduce a general raw-register frontend.
- Carry the controls through `CompiledOpnaSong`, exact timeline construction, canonical ordering, and runtime dispatch.
- Separate patch/timbre selection from live shared-register writes.
- Diagnose overlapping SSG parts that request incompatible shared state at the same time.
- Preserve source order when multiple legal writes occur at one timestamp.

### Target files

- `MmlParser.kt`
- `MmlCompiler.kt`
- `CompiledOpnaSong.kt`
- `CompiledOpnaTimeline.kt`
- `CompiledOpnaPlayer.kt`
- `OpnaLikeSynthesizer.kt`
- `SsgSharedState.kt`
- SSG compiler/timeline/conformance tests

### Acceptance gate

- Mid-note mixer, noise, envelope-period, and shape writes are representable.
- Shape restart ordering is explicit at identical timestamps.
- Runtime playback consumes only typed primitive events.
- No song title, patch name, or fixed BPM selects shared SSG behavior.

## Repair Phase R4 — Repair Hardware and PMD Software LFO Semantics

### Purpose

Complete Phase 4 with independently justified hardware behavior and correct PMD driver timing.

### Hardware LFO work

- Add ordered runtime events for global enable and rate changes.
- Ensure per-channel PMS/AMS and delay state belongs to the correct chip/driver domain and can change independently of note creation.
- Replace the current sine assumption only after deriving the actual waveform/cadence from primary documentation, independent traces, or optional real-chip capture.
- Store the final law as a compact mathematical rule or generated setup-time table.
- Do not call trigonometric functions in the audio callback.
- Make enable edges, rate changes, loop reset, and same-time note/control ordering explicit.

### PMD software-LFO work

- Correct square and random onset: the first value is established immediately after delay, then held for `speed` clocks.
- Count square-wave depth-evolution cycles at the documented sign transition, not at initial assignment.
- Preserve `H` delay when a later command omits the delay argument.
- Represent documented raw internal-clock and note-length delay forms without converting them into unrelated compiler ticks.
- Preserve key-on sync/free-run, fixed/tempo clocks, waveform, signed depth, repetition/hold, target, TL mask, and deterministic random seed/reset.
- Retain the explicit rejection of software LFO with dynamically pooled `P1` voices unless a real logical-part ownership model is introduced.

### Target files

- `Lfo.kt`
- `OpnaLfoLaws.kt`
- `PmdSoftwareLfo.kt`
- `PmdPerformanceState.kt`
- `MmlParser.kt`
- `MmlCompiler.kt`
- `CompiledOpnaSong.kt`
- `CompiledOpnaTimeline.kt`
- `OpnaLikeSynthesizer.kt`
- LFO conformance/integration tests

### Acceptance gate

- Hardware expectations do not call production `Lfo` or `OpnaLfoLaws` to construct their expected values.
- Hardware enable/rate changes are present in the exact timeline and replay after reset.
- Square/random onset and square `MD` evolution match independent PMD traces.
- `H` retention and both documented delay-unit forms are covered.
- LOGO validation renders far enough to exercise its real authored one-shot LFO transition and compares compiled controls/order to its normalized oracle.

## Repair Phase R5 — Strengthen the Raw FM Conformance Boundary

### Purpose

Turn Phase 5 from useful internal differential coverage into a defensible clean-room conformance layer without changing sound by guesswork.

### Work

- Add an explicitly named raw chip render path that bypasses:
  - song EQ;
  - output filter;
  - soft clipping;
  - stereo resonator;
  - discretionary mastering gain;
  - oversampling unless a particular conformance case explicitly requests it.
- Keep product rendering separate and continue applying named `OpnaOutputProfile` and `SongMastering` stages afterward.
- Expand independent differential vectors to cover:
  - the complete algorithm × feedback matrix;
  - integrated MUL and detune behavior across representative blocks/keycodes;
  - dynamic AR/DR/SR/RR/SL/KS cadence through actual voices;
  - complete SSG-EG boundaries, holds, alternation, retrigger, and key-off;
  - FM3 independent pitch/sample traces;
  - hardware-LFO PM/AM using independent vectors;
  - all four YM left/right enable-bit combinations before mastering.
- Assert every decoded register field for every source-derived patch:
  - ALG, FB, DT, MUL, TL, KS, AR, AM, DR, SR, SL, and RR;
  - plus any source-owned channel fields.
- Name every filter/decimator coefficient and its response target before changing it.
- Do not change patches, global gain, EQ, oversampling, filters, or clipping merely to make one song sound better.

### Target files

- `Fm4OpVoice.kt`
- `OperatorState.kt`
- `OpnRateEnvelope.kt`
- `OpnaLikeSynthesizer.kt`
- `SongMastering.kt`
- `LlsPatches.kt`
- `LogoSong.kt`
- FM differential/core/stereo/patch tests

### Acceptance gate

- Expected conformance values are independent of production implementations.
- Raw mono/stereo checkpoints never pass through mastering.
- Every PMD-derived patch is exhaustively protected.
- Automated evidence is described narrowly; it does not claim human musical approval.

## Repair Phase R6 — Reopen Song Ingestion Deliberately

### Purpose

Use additional songs as validation pressure without allowing song-specific exceptions to become engine laws.

### Candidate gate

For each candidate:

1. Record archive and entry SHA-256.
2. Produce the capability report and reject every non-`EXACT` used command for a production entry.
3. Prefer `.M86`; use `.M26` only as a shared FM/SSG comparison.
4. Decode only used patches into an exact song-local immutable bank.
5. Author readable MML and semantic controls; never embed `.M` bytes.
6. Compare normalized timing, pitch, gate, patch, volume, detune, LFO, envelope, and shared-register state.
7. Record authored and runtime event totals.
8. Render the raw conformance path and the named product path separately.
9. Perform human listening before accepting any global tonal/mix change.
10. Register the song in `MmlSongBank` and `SongCatalog` only after all gates pass.

### Validation set

Build a small contrasting suite rather than several songs resembling Bad Apple:

- an FM-dominant song with different algorithms and patches;
- an SSG-heavy song exercising the repaired 32-step envelope and shared events;
- a software-LFO song that exercises more than the existing short LOGO prefix;
- an FM3/rhythm song only after its complete capability report is `EXACT`.

The current manual catalog array/`when` registration is acceptable for a small catalog. Do not introduce serialization, resource files, reflection, dependency injection, or a data-driven runtime asset catalog merely to reduce a few manual entries.

### Human listening gate

Maintain timestamped notes for:

- 2–3 kHz harshness;
- bass masking;
- midrange loss;
- FM/SSG balance under both named output profiles;
- software/hardware LFO depth and cadence;
- SSG envelope articulation;
- rhythm transients and balance;
- loop-boundary clicks or state discontinuities.

No global gain, filter, EQ, envelope, modulation, or mix law may be approved from Bad Apple alone.

## Verification Policy

### Local platform policy

- Do not run `winTest`, `mingwX64Test`, or other Windows-native test tasks on this system.
- Use Android/JVM tests, common metadata compilation, Android shared compilation, app compilation, and Android assembly locally.
- Treat historical Windows-native results as unverified evidence, not a current gate.
- If non-JVM runtime verification is required later, run it only in an explicitly supported environment and record that environment separately.

### Required checks after each future repair phase

- Read every involved file and caller completely before editing or making behavioral claims.
- Review the diff against `AGENTS.md` and the relevant project skills.
- Confirm no `java.*` or platform API entered `commonMain`.
- Confirm audio rendering remains allocation-free with preallocated primitive state and `while` loops.
- Confirm no runtime assets, archive bytes, sample data, JSON, or opaque embedded blobs were added.
- Confirm all constants have named hardware, mathematical, or engine derivations.
- Confirm the Android wrapper remains a mono PCM presenter and does not gain synthesis policy.
- Re-run the strict corpus/capability audit after event-model changes.
- Record automated results separately from human listening acceptance.

## Explicit Non-Goals

Do not add or implement as part of this repair:

- runtime PMD binary playback;
- a raw YM2608 register-stream frontend;
- copied emulator or PMD assembly code;
- CSM/timer emulation beyond required musical scheduling;
- ADPCM-B, 86PCM, PPSDRV, or PPZ8 without a separately approved procedural/legal design;
- rhythm-ROM WAV files, copied sample bytes, or claims of ROM-authentic drums;
- external MML, JSON, XML, audio, font, or image assets;
- Compose, SwiftUI, React, Electron, or another UI/audio framework;
- production stereo migration;
- Bad Apple cut, EQ, or mix changes before multi-song listening evidence.

## Completion Definition

The repair is complete when:

- capability reporting cannot overstate runtime support;
- chip, driver, output-profile, and mastering state have separate owners;
- the SSG hardware envelope preserves all 32 levels;
- shared SSG controls and hardware-LFO controls are ordered primitive events;
- PMD software-LFO onset, delay, and depth evolution match independent traces;
- the raw FM/SSG conformance path is isolated from product mastering;
- decoded patches are exhaustively protected;
- Android/JVM and hot-path gates pass without Windows-native tasks;
- at least one contrasting multi-song listening suite is recorded;
- new catalog songs require no title-specific synth branch, hidden asset, global patch collision, or restoration of the retired sequencer catalog path.

Only after these conditions are met should unrestricted Phase 8 catalog expansion resume.
