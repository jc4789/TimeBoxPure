# TimeBox Sound Repair Plan

Status: implementation ledger. R1-R4 and the executable portions of R6 are implemented; R5 and musical admission remain gated by human listening.

Date: 2026-07-18

## 1. Repair objective

Restore the lost body, richness, resonance, and natural note behavior within the implemented Phase 1 architecture.

The repair must preserve:

- one `#MML 2` parser/compiler backend;
- one immutable compiled song representation;
- one sample-domain playback plan;
- one dispatcher and `OpnaPlaybackSession`;
- one procedural OPNA core;
- one `OpnRateEnvelope` FM envelope implementation;
- fixed six-FM/three-SSG hardware ownership;
- the current register-native voice definitions;
- allocation-free rendering after warm-up;
- procedural, clean-room behavior with no runtime assets.

The repair must not restore the deleted sequencer, v1 MML, float ADSR, generic event unions, pooled FM voices, retired songs, or any other old path. It must fix the current code in place.

Patch/voice timbre definitions are an explicit invariant. This is a performance-semantics and output-boundary repair, not a revoicing task.

## 2. Evidence discipline

PMD and YM2608 evidence were investigated separately because they answer different questions:

- PMD references define software language and performance semantics: volume, gates, ties, detune, portamento, envelopes, software LFOs, loops, and logical-part ownership.
- Yamaha references define hardware-shaped behavior: Block/FNUM, operator TL, ALG/FB, AR/DR/SR/SL/RR/KS, hardware LFO ownership, and L/R output enables.
- Current TimeBox code shows where those two domains are currently joined or conflated.

No PMD driver layout, work-memory structure, packed binary format, raw-register stream, emulator code, or sample data is an implementation source.

Evidence classification:

| Reference | Use in this repair |
|---|---|
| `PMDMML.MAN` | Normative clean-room evidence for observable PMD MML/performance behavior |
| `TH4 Bad Apple!! MML Dump.txt` | Primary evidence for what the source song exercises |
| `pmd_corpus_audit.py` | Structural discovery only; its current oracle is not semantic parity evidence |
| `PMDDATA.DOC` | Narrow public lifecycle/master-volume evidence; internal layouts excluded |
| `PMDWin.txt` | Failure-history prompts only; never semantic truth |
| `PC-98 Eternal Shrine Maiden.txt` | Secondary stress/syntax evidence; not ZUN corpus truth |
| Yamaha YM2608 application manual and text extraction | Normative hardware-shaped equations and register domains |
| `PPS.DOC`, `P86DRV.DOC`, `PMDPPZ8.DOC` | Out of scope PCM/sample systems |
| `PSGEDATA.DOC` | Separate procedural SSG-effect domain; not evidence for this FM voice repair |
| `DLLInfop.txt` | DLL/file/ABI material excluded; only weak corroboration that output gain is separate from part semantics |

The legacy PMD documents were read with CP932/Shift-JIS decoding. The UTF-8 secondary arrangement was read as UTF-8.

## 3. Executive diagnosis

The main defect is not patch timbre, FM ADSR removal, or an active Bad Apple LFO. It is a software-to-hardware boundary collapse:

1. Original PMD part volumes were rewritten as generic linear note gain to create mixer headroom.
2. Normal FM and FM3 then received incompatible runtime volume implementations and ambiguous ownership at their shared boundary.
3. Mixer/headroom/mastering policy remained active after the source had already been attenuated.
4. Important source performance behavior—17 detune changes and four portamentos—was omitted from the production transcription, and the tied-glide form cannot currently be represented correctly.
5. Product mono has no temporal resonator processing, but this predates Phase 1 and must not be used to conceal the raw semantic defects.

Phase 1 did not restore an old scheduler or old envelope. It preserved an older, already-damaged volume/transcription policy and then froze it in tests and hashes. The audible problem is real, but the evidence does not support blaming the new single-path architecture itself.

## 4. Current product path and active policy

Cached construction path:

```text
Bad Apple TimeBox MML -> MmlCompiler -> immutable CompiledOpnaSong
```

Active product/runtime path:

```text
SoundPreviewPlayer.playPreview
  -> SongCatalog / SongDefinition.buildPlayback
  -> MmlSongBank.getArrangement
  -> playArrangementStreaming
  -> OpnaPlaybackSession.createProduct
     -> OpnaRenderProfile.createPlayer
     -> CompiledOpnaTimelineFactory.build
  -> session.render
  -> OpnaLikeSynthesizer.renderProductSequential
     (CompiledOpnaPlayer supplies ordered boundaries and cursor state)
  -> PmdPerformanceState + chip domains
  -> Fm4OpVoice + SSG + percussion
  -> OpnaMixer
  -> SongMastering
  -> mono PCM16 AudioTrack
```

Current product policy is 48 kHz, FM oversampling enabled, timeline mix gain `0.75`, FM bus gain `0.86`, chip headroom `0.80`, master gain `1.5`, three song EQ bands, one-pole output filter alpha `0.50`, soft-clip knee `0.70`, mono output, and the stereo resonator disabled.

### Normal FM A-F today

```text
PMD-shaped V
  -> per-note field named velocity
  -> (V / 127) * timeline.mixGain
  -> Fm4OpVoice.noteGain
  -> linear multiplication after the complete four-operator voice
```

### FM3 today

```text
PMD-shaped V
  -> persistent FM_PART_VOLUME
  -> PmdPerformanceState.baseAttenuation
  -> attenuation added to every owned operator
  -> noteGain forced to unity
```

These are competing semantic implementations. The normal path treats PMD volume as generic post-synthesis velocity. The FM3 path uses logical-part driver attenuation selected per owned operator. That path is not a blanket attenuation of all four physical operators, and a modulator-owned FM3 part may legitimately control a modulator. Its exact PMD ownership must be preserved rather than forced into normal channel-volume semantics.

## 5. Confirmed findings

The findings in this section describe the pre-repair snapshot. Section 12 records their implemented disposition.

### F1 — Primary: PMD volume domains were converted into generic gain

PMD software evidence establishes:

- FM fine `V` is `0..127`.
- FM coarse `v` is `0..16` with the exact table:

```text
v:  0  1  2  3  4  5   6   7   8   9  10  11  12  13  14  15  16
V: 85 87 90 93 95 98 101 103 106 109 111 114 117 119 122 125 127
```

- SSG fine/coarse volume is `0..15`, not `0..127`.
- Ordinary non-percent FM volume steps change fine volume by four units.
- Part volume is persistent performance state; it is not a new note velocity on every note.

The source Bad Apple dump uses FM values `V111..V127` and SSG values `V13..V15`. The production MML explicitly says it retained FM at 64% and SSG at 37%, then rewrites those domains as FM `V71..V81` and SSG `V40..V46`.

This causes three concrete errors:

- legal FM `v16` is rejected because the compiler accepts only `v0..v15`;
- FM coarse volume is mapped linearly instead of using the PMD table;
- illegal SSG values above 15 are accepted because all pitched lanes share `V0..V127`.

It also replaces the source `111..127` domain with `71..81`, lowers absolute level, and then subjects the result to additional timeline, bus, chip, and mastering gain policy. The code proves source attenuation and the wrong transfer domain; loss of perceived body or richness remains a listening hypothesis that must be tested at matched loudness.

### F2 — Primary architecture defect: normal FM and FM3 disagree

Normal FM stores current volume in every note payload and applies it after synthesis. FM3 alone emits a persistent part-volume control and converts it to logical-owner operator attenuation.

The repair must provide one typed persistent PMD volume framework without erasing different owners: normal FM volume belongs to the physical channel/part, while FM3 volume belongs to its disjoint logical slot part. A TimeBox note-velocity extension, if retained, must be separately typed and default to unity.

The exact PMD `V -> operator TL` arithmetic is not fully specified by the manual and must be a named clean-room policy supported by independent fixtures and listening. It must not be copied from current behavior merely because tests are green.

For normal FM channel volume, the selected clean-room policy is carrier-only base projection because it changes loudness without altering modulator depth. This is an implementation policy pending independent fixtures/listening, not a direct manual fact. FM3 must retain its separately evidenced logical slot ownership, even when an owned slot is a modulator. Direct `O` commands independently change selected operator TL. `MM` masks independently select software-LFO targets only; they do not select ordinary base-volume operators.

### F3 — Missing performance behavior makes notes rigid

The source evidence contains:

- 28 pitched volume changes;
- 29 `q` gate changes;
- 17 signed detune changes;
- four portamentos;
- four ties;
- eight SSG software-envelope definitions;
- LFO clock-mode declarations but no active software-LFO definition/switch.

The production transcription omits the 17 detune commands and the four source glides. Its tie folding only joins the same pitched note, so the source tied-portamento form cannot currently produce one held attack with a continuous glide.

Their omission removes authored pitch motion and can plausibly explain rigid stepping in the affected passages without changing patch timbre. It does not, by itself, explain the whole-song character.

### F4 — Detune units and operations are conflated

PMD exposes signed `-32768..32767` raw pitch/detune operands: `D` sets absolute part detune, `DD` changes it relatively, and `DM` is a separate additive master detune. The current compiler/parser model names and compiles the `D` operand as cents with a `±1200` range, although production Bad Apple currently omits its `D` tokens. Elsewhere, a payload accessor named `cents` is stored as `slotFnumDetune` and added directly to the 11-bit FNUM field.

Yamaha hardware defines legal Block `0..7` and FNUM `0..2047`, but it does not define PMD's signed detune command or its overflow policy. Therefore:

- PMD evidence must define the software command and carry/clamp policy.
- The lowering boundary must produce an explicitly typed, legal Block/FNUM result.
- The hardware core must not silently interpret a cents-named value as FNUM units.
- Raw masking/wrapping is forbidden unless independently established as the chosen clean-room PMD behavior.

### F5 — FM3 ownership must not be flattened during the repair

`PmdPerformanceState` creates a base attenuation per FM3 logical part. `Fm4OpVoice` selects that part's driver frame per operator according to the bound slot mask.

Changing a modulator-owned part changes modulation index, but that may be the intended consequence of FM3 logical slot ownership. The confirmed defect is the incompatible and ambiguously named normal-FM/FM3 volume machinery, not proof that FM3 must be carrier-only. Bad Apple does not use FM3. The repair must share typed storage/lowering where appropriate while preserving FM3 slot ownership and testing each owner independently.

### F6 — Product mono has no procedural resonator

`SongMastering.processMono` performs EQ, gain/filtering, and soft clipping only. The existing procedural resonator is called only by the stereo path, and the product profile disables it.

This can explain a dry lack of temporal resonance, but it predates Phase 1. It is a secondary capability gap, not proof of the reported regression. A mono mode may be added to the one existing procedural resonator only after raw volume, detune, glide, and articulation semantics are correct.

### F7 — Mono erases hardware L/R semantics before fold-down

The current mono FM path treats left-only, right-only, and both-enabled voices as the same full-level mono signal; only neither is discarded. The stereo path correctly preserves the two enable bits.

The hardware-shaped repair is to preserve L and R first, then apply one documented fixed-headroom product fold-down. Yamaha specifies independent outputs; the mono equation is TimeBox product policy. Bad Apple is centered, so this is not its primary regression, but it is required by `sound_plan.md` before claiming correct product mono.

### F8 — Current tests encode implementation, not authenticity

A selected Android/JVM test run on 2026-07-18 passed, but that result is diagnostic only and does not validate the sound. Several tests explicitly freeze the defect:

- `MmlCompilerTest` expects the rewritten `V78` value.
- `MmlArrangementSchedulerTest` expects `V78 / 127 * 0.75` and `V40 / 127 * 0.75`.
- frozen raw/profile/product hashes preserve the current known-bad output.
- headroom/spectral thresholds can reward the source-level attenuation.
- `OpnaFmRichnessTest` only checks that a direct voice is not close to a pure sine by using peak/RMS; it does not exercise PMD volume, compiled playback, product mono, resonance, or authenticity.
- `OpnaLaneGainTest` checks stale/unused lane-gain constants rather than the active product gain path.
- differential tests that reproduce production equations are internal consistency checks, not independent hardware evidence.

Hashes remain useful migration alarms. They are not acceptance oracles and must not block an evidence-backed sound correction.

## 6. Findings ruled out as primary causes

### Patch/voice timbre

The active LLS register voices are unchanged except for removal of the obsolete envelope-mode selector. They are controls for the repair and must not be revoiced.

### Float ADSR removal

The active product patches already used the OPN-rate envelope. Pre-Phase-1 compiled note events supplied no float ADSR overrides. `OpnRateEnvelope` is unchanged. Restoring float ADSR would recreate duplicate truth without repairing the symptom.

### Bad Apple LFO

Bad Apple has no active software-LFO commands. The global hardware LFO may run, but the active LLS patches have PMS/AMS depth zero, so it is audibly inert. LFO correctness remains important for other songs but is not the Bad Apple repair target.

### Master clock as a richness fix

The supplied Yamaha manual specifies the standard 8.0 MHz profile and uses 8 MHz in its FNUM example. The supplied PC-9801-86 text does not establish 7.9872 MHz. Even if a board-specific 7.9872 MHz profile is later proven, the difference is about `-0.16%` or `-2.77` cents and a `0.16%` EG/LFO timing shift. From that quantified difference, the engineering inference is that it cannot plausibly explain a large loss of body or resonance and does not change ALG/FB topology.

Keep `8_000_000` unless independent board evidence deliberately introduces a named hardware profile. Do not change clock as a tone control.

### New Phase 1 scheduling regression

The production MML attenuation, normal-FM note-gain path, mix gain, EQ/filter, oversampling, mono resonator bypass, and FM3 logical-part base-attenuation machinery predate or survive Phase 1. Phase 1 exposed and retained the defect; it did not justify restoring old architecture.

## 7. Repair sequence

### R0 — Establish a known-bad diagnostic baseline

Status: automated diagnostic baseline complete; named level-matched human observations remain pending.

1. Keep current semantic/audio hashes labeled `KNOWN_BAD_PRE_REPAIR`; do not call them acceptance baselines.
2. Capture named Bad Apple excerpts at raw-core, profiled-pre-master, and product-mono stages.
3. Record level, RMS, peak, clipping, low-mid energy, spectral centroid, attack trajectory, and release trajectory.
4. Make all listening comparisons level matched so louder is not automatically judged richer.
5. Record human observations for body, attack, sustained resonance, glide continuity, rigid/electronic stepping, and loop/reset behavior.

Gate: the defect can be localized to raw semantics, output profile, or product mastering without using a single whole-output hash as truth.

### R1 — Restore correct authored volume domains

Status: implemented. The provisional FM projection still requires the listening admission named in this plan.

1. Implement and test part-aware volume grammar/state before changing the product transcription:
   - FM `V0..V127`;
   - FM `v0..v16` using the exact PMD table;
   - SSG `V/v0..15`;
   - rhythm/effect volume remains in its explicitly named domain.
2. Implement the distinct PMD operations without merging their state:
   - non-percent FM `)`/`(` changes fine `V` by `+4/-4`;
   - non-percent SSG relative volume changes by the requested amount, defaulting to one, within `0..15`;
   - percent mode performs fine relative changes;
   - `v+`/`v-` changes current and future volume in fine units until a later setting replaces it;
   - `v)`/`v(` changes only the conversion used by future coarse `v`, not current fine `V`.
3. Emit typed persistent part-volume controls for normal FM, FM3 logical parts, and SSG instead of copying PMD volume into every note. This is the chosen TimeBox representation of the observable persistence law, not a PMD storage-layout claim.
4. Add the runtime projections described in R2 and make the authored-value switch atomic with those projections.
5. Keep any TimeBox note velocity in a separate typed extension with unity default.
6. Remove timeline `mixGain` from FM-note, SSG-note, and rhythm-shot semantics and delete the duplicated velocity derivation. The timeline should contain timing/order/payload references, not mastering policy.
7. Preserve the active Bad Apple/current-product lane balance during migration by folding the existing `0.75` once into the profile-owned common chip-headroom stage after the FM/SSG/rhythm buses; for its normal FM, SSG, and rhythm lanes, the initial equivalent combined value with current `0.80` chip headroom is `0.60`. FM3 currently bypasses `mixGain`, so this common policy would intentionally lower FM3 product level by 25%; capture and approve that change with a separate profiled-level fixture and level-matched listening gate. Do not call it engine-wide equivalent or duplicate `0.75` in per-lane/per-note state.
8. Only after steps 1-7 pass, replace the production MML's baked 64% FM and 37% SSG translations with the source semantic values in the same gated migration.

Gate: source semantic traces show original FM `V111..127` and SSG `V13..15`; no authored value exists solely to create product headroom.

### R2 — Define volume projections without flattening ownership

Status: implemented. Normal FM carrier ownership, FM3 logical-slot ownership, SSG fixed-level ownership, and one profile headroom owner are distinct.

1. Extend the existing `PmdPerformanceState`; do not add a second part-state system.
2. Define one named clean-room projection for normal FM channel volume, using the selected carrier-only base policy pending independent fixtures/listening.
3. Preserve FM3's disjoint logical slot owners. Share typed storage and conversion helpers only where their evidenced units match; do not force normal-FM carrier ownership onto FM3 slot parts.
4. Keep direct `O` operator-TL commands independent from base part volume.
5. Keep `MM` independent from base volume and direct `O` TL. On normal FM, `MM` selects operator targets for software volume LFO only, while software pitch LFO continues to target all operators. On FM3, `MM` selects targets for both software pitch and volume LFO. `MM0` defaults volume LFO to carriers and pitch LFO to all slots; explicit masks persist across patch changes.
6. Add persistent SSG part volume and project legal `0..15` state into the existing SSG fixed-level path, with reset, loop, and chunk invariance.
7. Make `OpnaRenderProfile` the single policy owner. `OpnaMixer` legitimately applies FM/SSG/rhythm bus gains, and `SongMastering` applies one common chip-headroom/master/user-gain chain. The migrated former `mixGain` must affect all three buses once through that common headroom, including rhythm. Admit the newly unified FM3 product level only through its named profiled-level regression/listening gate; do not hide compensation in song, timeline, note, or FM3 slot state.
8. If restored source levels clip, adjust one named render-profile headroom policy only after raw semantics pass. Do not alter source volumes or patch registers to solve clipping.

Gate: normal FM volume changes loudness without unintentionally changing modulator state; FM3 changes only the slots owned by its logical part; SSG uses legal part/fixed-level values; rhythm retains its intended relative balance; direct TL and software-LFO masks remain distinct; no timeline payload carries mastering gain.

### R3 — Restore detune, portamento, and tied-glide semantics

Status: implemented for the checked clean-room validity policy recorded below.

1. Introduce one explicit signed `-32768..32767` PMD detune unit at the compiler/performance boundary; do not call it cents if it is an FNUM-domain delta.
2. Represent `D` absolute part detune, `DD` relative part detune, and the separate per-logical-part additive `DM` master-detune component as distinct state operations.
3. Determine PMD add/carry/clamp behavior from PMD evidence and independent fixtures; Yamaha hardware evidence only supplies the legal final Block/FNUM range.
4. Lower the combined result exactly once into legal Block `0..7` and FNUM `0..2047` state.
5. Restore all 17 Bad Apple detune changes in the current MML transcription.
6. Restore all four source portamentos, including their targets and durations.
7. Support tie-to-portamento as one held key state with no intermediate key-off/retrigger and a continuous ramp.
8. Keep ordinary slur/retrigger behavior separate.

Gate: the lossless Bad Apple semantic trace contains 17 detunes, four target-bearing portamentos, and four ties with correct key lifecycle.

### R4 — Validate envelope and modulation execution without changing timbre

Status: implemented after R1-R3. Exact undocumented silicon cadence remains an explicitly unclaimed hypothesis.

1. Retain the current OPN-rate envelope and register-native patch fields.
2. Add manual-derived checks for AR/DR/SR/RR/SL/KS domains, stage behavior, rate ordering/effective-rate relationships, endpoints, and per-slot key state. Exact increment pulses/cadence remain measurement-required TimeBox policy unless independently established.
3. Validate Bad Apple's legacy SSG `E2,-1,24,1` behavior: wait AL clocks, apply signed DD, decay by SR, decay after key-off by RR, sustain at SR0, immediately zero at RR0, and preserve EX0/EX1 clock policy across chunk/reset/key-off.
4. Preserve Bad Apple's zero active software-LFO state as an explicit fixture.
5. Validate both software LFOs: configure resets immediately even during a tie; waveform selectors `0..6`; switch `0` off, `1/5` pitch, `2/6` volume, and `3/7` both, with `1..3` key-on synchronized and `5..7` asynchronous/free-running; fixed-clock tempo independence; positive volume LFO lowers TL/raises loudness; the normal-FM versus FM3 `MM` ownership rules from R2; explicit `MM` persistence across patch changes; depth evolution clamped to legal `+127` rather than the current possible `+128` edge.
6. Test hardware LFO through rendered pitch/amplitude measurements rather than state-array snapshots alone.
7. Keep deeper EG cadence, PMS symmetry, feedback-history reset, and ideal-log-table rounding as measurement-required hypotheses. Change none of them without an independently observed mismatch.

Gate: the OPN-rate envelope remains register-native; the legacy SSG `E` behavior and both LFO ownership models pass independent fixtures; 

### R5 — Repair product mono and evaluate temporal resonance

Status: intentionally pending. The required raw-semantic human listening record does not yet exist.

1. Preserve hardware L/R enable semantics to the final bus.
2. Define one fixed-headroom L+R-to-mono equation as explicit TimeBox product policy.
3. Verify left/right/both/neither, centered SSG, rhythm routing, and stereo-to-mono equivalence.
4. Add `processMono` behavior to the existing procedural resonator rather than creating a second effect.
5. Compare resonator off/on at matched loudness after volume and articulation repairs.
6. Enable it in the product profile only if named listening excerpts show improved natural body without metallic ringing, masking, or loop/reset tails.
7. Re-evaluate the current EQ and output filter one stage at a time. Do not use EQ/resonance to hide raw-core defects.

Gate: product resonance is a deliberate, reset-safe policy with one owner, not an accidental stereo-only path or a compensating effect.

### R6 — Replace invalid tests and upgrade the semantic verifier

Status: executable semantic, lifecycle, and hot-path work implemented alongside R1-R4. Timed K/R rhythm expansion and R5-specific mono fixtures remain open.

Replace current bad oracles with independently derived fixtures:

#### PMD volume

- all 17 FM coarse `v` mappings;
- FM `v16` accepted and SSG `v16` rejected;
- FM `V0/V127` and SSG `V0/V15` endpoints;
- coarse/fine/relative operations, including FM's non-percent four-unit step and SSG's requested/default-one relative step;
- persistence across notes, patch changes, local loops, and song loops;
- normal-FM persistent channel volume under the named carrier-base projection;
- FM3 disjoint logical-part ownership, including independently owned carrier and modulator slots;
- direct `O` operator-TL edits kept separate from base part volume;
- normal-FM `MM0`/explicit masks applied only to software volume LFO while pitch LFO targets all operators;
- FM3 `MM0`/explicit masks applied to both software pitch and volume LFO with the documented pitch-all/volume-carrier defaults.

#### Detune, gate, and glide

- signed PMD detune endpoints `-32768..32767` and final legal Block/FNUM boundaries;
- `D` absolute part detune, `DD` relative part detune, and separate additive `DM` master detune;
- portamento source/target/duration;
- tied glide with one attack and no middle key-off;
- slur as separate retrigger behavior;
- gate/tail-cut behavior independent of ties.

#### Envelope and LFO

- legacy SSG envelope `E2,-1,24,1` under normal and fixed clock modes;
- two independent software LFOs;
- signed depth and `+127` bound;
- normal-FM versus FM3 software-LFO target ownership, including `MM0` defaults and explicit masks;
- switch targets `0`, `1/5`, `2/6`, and `3/7` with synchronized versus asynchronous behavior;
- fixed-clock tempo independence;
- zero active Bad Apple software LFO.

#### Hardware-shaped execution

- legal Block/FNUM endpoints;
- single-carrier TL ratio at 0.75 dB per step;
- all eight ALG topologies and their carrier/operator routing;
- level-matched carrier-versus-modulator metamorphic tests;
- feedback selector `0` off and selectors `1..7` following the manual equation/map from `π/16` through `4π`; post-attack spectra are diagnostic only;
- one chip-wide hardware-LFO oscillator with shared phase and cross-channel phase coherence, plus per-channel PMS/AMS, per-slot AM enable, and rendered pitch/amplitude excursions; exact enable/reset phase behavior remains measurement-required;
- true L/R/both/neither routing and mono fold.

#### Lifecycle and hot path

- chunk invariance;
- fresh-session/reset equivalence;
- loop-state persistence;
- stale note-off ownership;
- exact typed payload storage;
- zero steady-state render allocations.

Upgrade `pmd_corpus_audit.py` to emit one lossless, clock-ordered semantic stream containing notes and control-only parts. The Bad Apple acceptance trace must include volume, gate, tie/retrigger, detune, portamento target/duration, envelope, LFO state, rhythm, shared state, and source provenance.

The complete-source inventory gate is exact: 28 pitched-part volume declarations at source-domain values, 29 `q` declarations, 17 detunes, four target-bearing portamentos, four ties without an intermediate retrigger, eight `E` definitions, 20 `MX` declarations, and zero active software-LFO commands. The selected production window `[288, 5280)` contains 25 volume declarations, 22 `q` declarations, 10 detunes, all four portamentos and ties, and all eight `E` definitions. Production parity compares normalized effective typed state, because the embedded source intentionally omits redundant declarations (`q`, `E`, and inert `MX`) while preserving their effective behavior.

Full decoded traces remain ephemeral local outputs. Check in only non-expressive checkpoints, small independently authored fixtures, aggregate coverage, and owned TimeBox baselines.

Gate: no test asserts `V78 / 127 * MIX_GAIN`, `V40 / 127 * MIX_GAIN`, or a current audio hash as proof of authenticity.

## 8. Expected implementation surfaces

Likely Phase R1-R4 files:

- `audio/mml/MmlSongBank.kt`
- `audio/mml/MmlCompiler.kt`
- typed payload tables in `audio/opna/CompiledOpnaSong.kt`
- the narrow timing boundary in `audio/opna/CompiledOpnaTimeline.kt`
- `audio/opna/CompiledOpnaPlayer.kt` only if dispatch references change
- `audio/opna/PmdPerformanceLaws.kt`
- `audio/opna/PmdPerformanceState.kt`
- `audio/opna/OpnaLikeSynthesizer.kt`
- `audio/opna/Fm4OpVoice.kt`
- `audio/opna/SsgVoice.kt`
- `audio/opna/OpnaPlaybackSession.kt`
- `audio/opna/OpnaOutputProfile.kt`
- `audio/opna/OpnaRenderProfile.kt`
- `audio/opna/OpnaMixer.kt` and `audio/opna/SongMastering.kt` for the R1/R2 common-headroom migration
- `audio/opna/OpnPitch.kt` only for the explicitly typed detune lowering seam
- `tools/pmd_corpus_audit.py`
- focused common tests and Android allocation tests

Later Phase R5 files:

- `audio/opna/OpnaLikeSynthesizer.kt`
- `audio/opna/OpnaMixer.kt`
- `audio/opna/SongMastering.kt`
- the existing procedural resonator
- `audio/opna/OpnaRenderProfile.kt`

Explicit non-targets:

- LLS patch/operator register definitions;
- a new FM envelope implementation;
- Android playback architecture;
- deleted scheduler/dialect/envelope files;
- sample, PCM, PMD binary, S98/VGM, or raw-register support;
- UI or platform frameworks.

## 9. Implementation order and stop rules

Work on one repair unit at a time:

1. R1 typed volume grammar/state.
2. R2 distinct normal-FM channel and FM3 logical-slot volume projections on shared typed state.
3. R3 detune and tied portamento.
4. R4 focused envelope/modulation validation.
5. R5 mono/output resonance.
6. Final semantic verifier and listening admission.

After each unit:

- update this ledger with completed files and decisions;
- review the diff against the engine laws;
- verify no new parallel owner or event path appeared;
- verify no allocations entered render/audio loops;
- record which old hashes intentionally changed;
- stop if a required PMD behavior, overflow policy, or listening choice remains unproven.

Do not proceed to resonator/EQ tuning while raw volume, detune, gate, or portamento semantics are still wrong.

## 10. Verification commands and gates

Use the required local JBR for every Gradle command:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:testDebugUnitTest :shared-engine:compileDebugKotlinAndroid :app:compileDebugKotlin :app:assembleDebug --rerun-tasks
```

Required gates:

- the complete Phase 1 single-path architecture remains intact;
- the PMD semantic fixtures pass independently of current product hashes;
- the YM2608 hardware-domain fixtures use manual-derived units and equations;
- raw/profile/product rendering is deterministic across chunk sizes and reset;
- the allocation audit and final render hot path pass;
- no external/runtime asset or binary compatibility path appears;
- current known-bad hashes change only for documented semantic/output reasons;
- named level-matched listening excerpts improve body, resonance, glide continuity, and natural note behavior;
- no patch/voice definition is changed to manufacture a pass.

## 11. Open decisions that must not be guessed

1. Exact clean-room `FM V -> carrier TL` arithmetic: the manual defines the user volume scale but not the internal formula. Establish a named policy with independent fixtures and listening.
2. PMD signed detune overflow/carry behavior: resolve from PMD evidence, not Yamaha register documentation or the current cents/FNUM mix-up.
3. Product mono resonator admission: decide only after raw semantics are corrected and level-matched listening is recorded.
4. Any future 7.9872 MHz board profile: require independent board evidence and introduce it as a named profile, not a replacement justified by perceived richness.
5. Deeper EG/LFO/feedback/log-table refinements: retain as hypotheses until a manual-derived or measured failure exists.

## 12. Implementation ledger — 2026-07-18

- R0: the pre-repair Android/JVM build passed 222 tests and supplied a diagnostic-only known-bad baseline. Stale whole-output hashes, linear-gain expectations, and spectral/richness thresholds were removed rather than promoted to acceptance truth. Human listening was not fabricated.
- R1/R2: FM `V0..127`, exact `v0..16`, SSG `V/v0..15`, and relative/coarse operations now compile into persistent typed part state. Normal FM projects volume to carriers, FM3 retains disjoint logical-slot ownership, SSG projects to fixed level, direct operator TL and `MM` remain independent, timeline `mixGain`/note gain duplication is gone, and product chip headroom is owned once by `OpnaRenderProfile` at `0.60`.
- R3: `D`, `DD`, and per-part `DM` use signed raw PMD units; runtime payloads remain primitive integers. Long arithmetic rejects an overflowing signed component, overflowing effective sum, or result that cannot lower to legal FM Block/FNUM or SSG period state. This is an explicit checked TimeBox subset, not an unsupported PMD wrap/saturation claim. Bad Apple restores 17 detune declarations and four delayed tied glides with one key-on, one continuous pitch ramp, and one key-off.
- R4: the sole FM envelope remains `OpnRateEnvelope`; no float ADSR returned. Manual-domain envelope/LFO fixtures were expanded, software-LFO depth now stops at `+127`, and FM3 key scaling uses each operator's actually detuned packed pitch. The unused float compatibility accessors were removed from the register-native envelope API. Procedural EG pulse cadence remains a clean-room hypothesis rather than a silicon-exact claim.
- R5: not started because this plan requires raw-semantic human listening first. Product mono fold-down, resonator admission, and EQ/filter re-evaluation must not proceed until that record exists.
- R6: invalid gain/hash/richness oracles were removed; product-session lifecycle fixtures cover awkward chunks, looping, reset, stop/restart, stale ownership, exact storage, and independent sessions. Static hot-loop auditing was expanded. The local JBR allocation probe uses a documented 512-byte measurement-noise ceiling over 1,024,000 frames; it is a JVM proxy, not a false claim of exact Android-device zero allocation. The corpus tool emits clock-ordered typed semantics, rejects duplicate archive names before overwrite, uses candidate-specific evidence, keeps full traces ephemeral, and compares normalized effective Bad Apple state. Timed K/R rhythm-shot expansion remains open and is not claimed.
- Final automated verification: 243 Android/JVM shared-engine tests pass with zero failures/errors/skips; shared Android compilation, app debug assembly, and `opnaAudit` pass. The corpus verifier's 32 tests and strict two-archive run pass with 80 entries, 45 unique payloads, zero scan errors, and zero pitched-lane tuple/effective-state mismatches for Bad Apple A-E/G/H.
- Architecture: no deleted scheduler, v1 dialect, pooled voice owner, generic event union, float ADSR, or old song path was restored. `commonMain` contains no `kotlin.jvm`, `java.*`, `javax.*`, Android, or AndroidX dependency.

## 13. Completion definition

This repair is complete only when:

- original PMD FM and SSG volume domains are preserved in authored semantics;
- normal FM, FM3, and SSG share one typed persistent-volume framework while preserving normal-channel, FM3 logical-slot, and SSG part/fixed-level ownership;
- mixer/headroom/user gain have one render-policy owner;
- Bad Apple's complete-source semantic inventory contains 28 pitched-part volume declarations at source values, 29 `q` declarations, 17 detunes, four target-bearing portamentos, four ties without retrigger, eight `E` definitions, and 20 `MX` declarations; the selected product window matches normalized effective typed state;
- Bad Apple remains explicitly free of active software LFO;
- detune units are explicit and the hardware core receives legal Block/FNUM state;
- product mono preserves L/R semantics before a documented fold-down;
- any admitted resonator behavior uses the one existing procedural effect owner;
- invalid gain/hash/richness oracles have been replaced;
- hot-path allocation, determinism, reset, and build gates pass;
- human listening confirms improved body, resonance, and natural note behavior without patch revoicing;
- no retired code or duplicated architecture has returned.



## files that were used as clean-room referances: 

"D:\Programes\TimeBox\tools\pmd_corpus_audit.py"  for parity requments to play pmd zun曲 /   "D:\Shit Games\PC-98 Games\Touhou 4 - Lotus Land Story\hdi\MUSIC\MUSIC.TXT"   "D:\Shit Games\PC-98 Games\The Touhou98 Experience v3.0.0\(TH04) Touhou Gensoukyou ~ Lotus Land Story\disks\main\th04plus\幻想郷ED.DAT"   "D:\Shit Games\PC-98 Games\The Touhou98 Experience v3.0.0\(TH04) Touhou Gensoukyou ~ Lotus Land Story\disks\main\th04plus\東方幻想.郷"

 "D:\Programes\ym2608-info\Docs\PMDMML.MAN" pmd manuel 

D:\Programes\ym2608-info\Docs\PPS.DOC" PMD SSGPCM環境 "PPS"	Document

"D:\Programes\ym2608-info\Docs\PMDDATA.DOC"  	PMD(PC98,88VA)をプログラムで制御する為のテクニカル情報


 "D:\Programes\ym2608-info\Docs\P86DRV.DOC"
 
"D:\Programes\ym2608-info\zun_music\TH4 Bad Apple!! MML Dump.txt" what real song actually exercises

 "D:\Programes\ym2608-info\zun_music\PC-98 Eternal Shrine Maiden.txt"  
 
 "D:\Programes\ym2608-info\Docs\DLLInfop.txt" has usefull info read.             
 
 "D:\Programes\ym2608-info\Docs\PMDWin.txt"       most valuable for its failure history     the file is shift-jis and not utf-8 this can cause 文字化け or missing charaters.
 
 
 
 "D:\Programes\ym2608-info\Docs\PSGEDATA.DOC" describes a compact procedural SSG effect model.


"D:\Programes\ym2608-info\Docs\PMDPPZ8.DOC" a doucumnt about what PMDPPZ does. PMDPPZは、PPZ8という、PC-9801-86および A-MATE音源にて、８重再生 ＰＣＭドライバを利用して、PCM８声再生を可能にしたＰＭＤです。     

 
 "D:\Programes\ym2608-info\Docs\UPDATE.DOC"　the update history of PMD,

 "D:\Programes\ym2608-info\Docs\Yamaha YM2608 OPNA Application Manual (Japanese).pdf" / "D:\Programes\ym2608-info\Docs\ym2608 datasheet.txt"    
