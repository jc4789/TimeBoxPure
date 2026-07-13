# YM2608/PMD Generalization Implementation Plan

## Objective

Improve the clean-room procedural YM2608/OPNA engine and embedded MML pipeline as a reusable music system, not as a Bad Apple-specific renderer.

The governing test is:

> Bad Apple専用ではない音源全体の改善点を見つけろ  
> Find improvements to the entire sound source that are not specific to Bad Apple.

Bad Apple must continue to play as intended throughout the work. New behavior should be justified by YM2608/PMD rules, a multi-song corpus, and independent tests rather than by tuning one mix until that song sounds better.

## Non-goals and constraints

- Keep Kotlin Multiplatform `commonMain`; do not add `java.*`, Android APIs, or a UI/audio framework to the shared engine.
- Keep runtime music embedded and procedural. Do not add `.M`, `.M86`, WAV rhythm ROM dumps, or other external assets to the application.
- Offline tools may read legally supplied archives and produce reports or compact, human-auditable Kotlin/MML source.
- Keep audio callbacks allocation-free. Parsing, source conversion, table generation, and validation happen before streaming.
- Do not copy an emulator core or PMD assembly into Kotlin. Use manuals, independently observed traces, mathematical derivation, and black-box comparisons.
- Do not claim bit-perfect YM2608 or complete PMD compatibility unless a later conformance suite proves it.
- Preserve production mono unless a separate listening-driven product decision changes it. Stereo remains useful for offline chip-conformance tests because YM2608 FM/rhythm pan is stereo.

## Evidence reviewed

### Current project

- `.agents/AGENTS.md` and all project skills.
- `ENGINE_BRIEF.md`.
- The complete current FM, SSG, sequencer, compiled-song, MML parser/compiler/scheduler, patch-bank, catalog, and Android playback paths relevant to this plan.
- Relevant current tests for frequency, envelopes, algorithms, advanced modes, determinism, scheduling, and acceptance.
- `scratch/pmd_bad_apple_compare.py`, `scratch/patch_analysis.py`, and `scratch/full_vhdl_audit.py`.

### New local resources

- `D:\Programes\ym2608-info\pmd48s\PMDMML.MAN` (KAJA's PMD 4.8 MML command manual).
- `D:\Programes\ym2608-info\PMD48O\PMDDATA.DOC` (PMD control/work-state documentation).
- The readable YM2608 hardware utility sources in `2608testtool`, `2608play`, and `2608fm_datedit`.
- THTK documentation and read-only archive listings for the two user-supplied TH04 archives.
- Executables, DLLs, LIBs, PDBs, and other forbidden binary/debug metadata were not inspected or summarized.

### Web research

- [Touhou Soundfont Wiki: FM sound sources](https://w.atwiki.jp/touhousoundfont/pages/18.html), retrieved through a text proxy because the live page presented a browser challenge.
- The page is a useful secondary source, not a chip specification. Its hardware ratios and listening recommendations must be treated as calibration hypotheses.

## Current production call path

The active catalog path is:

`SongCatalog` -> `MmlSongBank.getArrangement` -> `MmlCompiler`/cached `CompiledOpnaSong` -> `MmlArrangementScheduler` -> exact `CompiledOpnaTimeline`/`CompiledOpnaPlayer` -> `OpnaLikeSynthesizer.render` -> mono PCM16 `AudioTrack`.

Production currently contains two catalog songs: Bad Apple and Rin to Shite. Both use `MML_LOGICAL_TRACKS`; the legacy routing is rejected by Android playback.

Bad Apple is MML v2 and uses coordinated source-derived FM1-FM5 and SSG1-SSG2 lanes plus a handwritten procedural rhythm lane. The offline comparison currently reports zero pitch/start/duration/patch mismatches for the seven included PMD lanes:

- A: 487 events
- B: 205 events
- C: 205 events
- D: 570 events
- E: 570 events
- G: 712 events
- H: 710 events

The active old/test material is:

- MML v1 parsing/compilation remains callable, but no catalog song uses the migration fixture.
- `BAD_APPLE_LLS_MIGRATION_FIXTURE_MML` is data only and is not production truth.
- `OpnaPatterns` and several direct note APIs are exercised by tests/experiments, not the catalog playback path.
- Files under `scratch/` are offline investigations and are not runtime code. Some describe older engine implementations and must not be treated as current truth.

## Important documentation drift

The former `ENGINE_BRIEF.md` drift for `LlsPatches.CHIP_CHANNEL_SCALE` was corrected from `0.44f` to the source-authoritative `0.38f` when Phase 0 landed.

The brief also records the last known test count. The implementation work must report the actual count from the current build rather than copying that number forward.

Baseline verification performed for this plan: 120 tests passed with zero failures, errors, or skips; the OPNA hot-path audit passed; common metadata/Android compilation and `:app:assembleDebug` completed successfully.

## Resource findings

### TH04 is a real multi-song validation corpus

THTK can list both supplied version-4 archives without extracting them into the repository:

- `幻想郷ED.DAT`: 23 `.M86` and 23 `.M26` entries, including ending, logo, name, stage, and staff-roll songs.
- `東方幻想.郷`: 17 `.M86` and 17 `.M26` entries used by the main game archive.

This provides three useful views:

1. `.M86` is the primary six-FM-channel PMD/OPNA corpus.
2. `.M26` is a three-FM-channel OPN-compatible comparison corpus for shared FM/SSG behavior.
3. Differences between arrangements help distinguish driver/chip constraints from song-specific choices.

The wiki also notes that trial and release Bad Apple arrangements differ, especially in 26K SSG parts. Therefore archive identity and SHA-256 must be recorded for every oracle; filenames alone are insufficient.

### PMD is a state machine, not merely a note list

The PMD manual documents behavior that the current note-only event program cannot fully express:

- PMD's K/R rhythm pattern system normally drives SSG drum/effect behavior. YM2608 rhythm-unit commands are separate commands that can appear on any part.
- `Q` and `q` interact. PMD supports proportional gate, fixed clock subtraction, a random gate range, and a minimum sounding length.
- There are two independent software LFOs per part with triangle, saw, square, random, and one-shot behavior; they can affect pitch, volume, or selected FM operator TL values and may be key-on synchronous or free-running.
- SSG/PCM software envelopes have two formats and either tempo-dependent or approximately 56 Hz clocking.
- FM3 extended mode is slot-mask based. Patch changes update selected slots while algorithm remains channel-global and feedback depends on slot 1 participation.
- PMD can change FM TL, feedback, slot mask, detune, envelope, SSG tone/noise, and shared noise frequency during a part.
- YM2608 rhythm control includes shot and dump, global level, per-instrument level, and per-instrument pan.
- Timer-B is the musical scheduler. Exact source timing is better represented as clock/rational data than rounded integer BPM alone.

### The supplied wiki changes how mixing should be framed

The page describes YM2608 as six 4-operator FM channels, three SSG channels, one ADPCM channel, and six built-in rhythm voices. It also explains that SSG is analog and board-dependent while FM/rhythm are on the digital side of the output path.

For PC-98 YM2608 boards it gives an approximate SSG level of 25% relative to FM; other boards differ materially. The current engine converts `SSG_GAIN_DB = -6 dB` to about 0.501 while its FM bus is 0.86, so the global SSG/FM ratio is about 58% before authored song scaling. Neither ratio should be silently declared universally correct.

The engine needs named output profiles and an objective calibration procedure. `PC9801_86` can begin with 25% as a hypothesis, while the present mix remains a `TIMEBOX_LEGACY` profile until listening and regression work approve a migration.

The page's 55,466 Hz/no-LPF recommendation is useful for offline comparisons with historical players. It is not a reason to change Android's 48 kHz output blindly. A reference renderer can operate at 55,466 Hz and resample/output separately while production remains 48 kHz.

## Gap analysis

| Area | Current behavior | General-purpose gap | Priority |
|---|---|---|---|
| Corpus/oracle | One specialized ST02 comparison script | No reusable `.M86` command inventory or multi-song trace oracle | P0 |
| Program capacity | 4,096 compiled events; expanded to at most 8,192 sequencer events | Bad Apple already uses 3,864 compiled events; longer songs will fail | P0 |
| Event model | Note, patch ID, volume, pan, detune, hardware-LFO fields | Cannot express PMD state changes or rhythm/SSG shared controls | P0 |
| SSG software envelope | `lls_square` hardcodes Bad Apple BPM and 24 PMD clocks/quarter | Wrong for other tempos and tempo changes; explicitly song-specific | P0 |
| Gate semantics | Engine-specific `Q0..8` and fixed `q` ticks | PMD `Q0`, random `q`, minimum gate, and clock units are not preserved | P1 |
| Tempo | Float BPM plus integer mid-track `T` | Cannot losslessly retain Timer-B/PMD clock timing and fractional changes | P1 |
| SSG tone | Direct floating note frequency | No 12-bit period quantization; `duty` is stored but hardware rendering is fixed 50% | P1 |
| SSG shared state | Shared noise/envelope exists | Noise-period conflicts are not diagnosed; register-write ordering is implicit in note-on | P1 |
| Mix/output | Global gains, one-pole filter, soft clip, song EQ | Board behavior, chip mix, and application mastering are conflated | P1 |
| Hardware LFO | One shared sine-derived PM/AM buffer | Needs independent waveform/depth/quantization conformance evidence | P1 |
| Software LFO | Unsupported | Important PMD articulation is discarded in recovered songs | P1/P2 by corpus frequency |
| FM3 extended | Four operator note parts on one voice | No arbitrary slot masks/live slot patch semantics or exact shared-channel state | P2 |
| Rhythm | Original procedural approximations | No PMD K/R SSG rhythm semantics; no exact shot/dump/global/individual control model | P2 |
| Patch bank | Closed global numeric IDs and `when` mappings | Every recovered song increases global coupling and collision risk | P2 |
| FM core conformance | Deterministic idealized logarithmic core and self-tests | Few independent register/trace comparisons; oversampling/filter constants color output | P1/P2 |
| ADPCM-B/86PCM | Not implemented | Some PMD songs may require PCM semantics, but external sample assets violate runtime law | Deferred until corpus proves need |
| Timers/CSM/raw registers | Not implemented | Needed for register-stream emulation, not for current embedded MML goal | Deferred |

## Recommended architecture

### 1. Keep three separate representations

Do not turn the runtime into a PMD binary interpreter.

1. **Offline PMD observation model**  
   Tool-only state used to decode supplied `.M86`/`.M26` files, enumerate commands, and produce normalized traces.

2. **Authored MML/OPNA control model**  
   Human-readable embedded MML plus compact instrument definitions. This remains the project source of truth.

3. **Runtime primitive program**  
   Exact-size primitive arrays containing ordered note and control events. Playback consumes only this representation and never parser objects or files.

This preserves the clean-room architecture while allowing PMD-derived behavior to inform the authored program.

### 2. Replace the note-only program with ordered typed events

Evolve `CompiledOpnaSong` into a primitive program that can carry, at minimum:

- FM/SSG note-on and key-off.
- Per-part volume and detune.
- Instrument selection.
- Tempo/clock changes.
- Hardware LFO global enable/rate plus per-channel PMS/AMS/delay.
- Software-envelope definition and clock mode.
- Software-LFO definition, target, sync mode, and enable state.
- SSG mixer bits, noise period, envelope period/shape/restart.
- FM slot mask, operator TL change, feedback change, and FM3 operator frequency.
- Rhythm shot/dump, master level, instrument level, and pan.

Use parallel primitive arrays or a small structure-of-arrays group. No sealed event objects in the audio callback.

Event ordering at the same clock must be explicit and tested. Suggested order:

1. global clock/tempo controls;
2. patch/register-like controls;
3. key-offs/dumps;
4. key-ons/shots.

This avoids depending on insertion-sort stability for retrigger behavior.

### 3. Remove the double-capacity scheduling expansion

Do not solve the 4,096/8,192 limit only by doubling constants.

Implement an exact-size `CompiledOpnaTimeline` during compilation/setup:

- First pass validates and counts note-on, key-off, and control boundaries.
- Second pass allocates exact primitive arrays outside the callback.
- Sort once during compilation or emit in canonical order; do not insertion-sort thousands of events when playback starts.
- The renderer advances a cursor through this already ordered timeline.
- Loop reset restores a preallocated initial chip/driver state snapshot and resets LFO/SSG/rhythm clocks deterministically.

Retain named hard safety ceilings for malicious/broken MML, but make them materially larger than a normal full song and validate them before allocation.

### 4. Separate chip state, driver state, and output profile

Introduce clear ownership:

- `OpnaChipState`: FM, SSG, hardware LFO, rhythm state.
- `PmdPerformanceState`: gate rules, two software LFOs, tempo-clocked envelope state, part-local volume/detune/slot masks.
- `OpnaOutputProfile`: FM/SSG/rhythm ratios and optional board/output filtering.
- `SongMastering`: playback gain and optional song EQ.

The conformance render path should bypass song EQ, soft clipping, resonator, and discretionary output filtering. The product render path may apply named mastering after the chip/output-profile render.

### 5. Make instrument banks song-scalable

Replace the single global numeric `when` bank with compact immutable banks:

- A shared built-in bank for authored generic instruments.
- A per-song PMD-derived bank containing only the voices the song uses.
- Stable local IDs stored in the compiled program.
- Compact Kotlin `OperatorSpec` records remain human-auditable and legal under the asset law.

Do not embed compiled `.M` bytes or opaque patch blobs.

## Phased implementation

### Phase 0 — Freeze baselines and build the corpus oracle

**Purpose:** make multi-song evidence available before changing sound.

Implementation:

- Move the reusable parts of `scratch/pmd_bad_apple_compare.py` into a documented tool under `tools/`.
- Invoke THTK only as an offline extractor/listing tool. Extract to a temporary or ignored research directory, never runtime resources.
- Record archive SHA-256, entry name, format (`M86`/`M26`), size, and decoded driver profile.
- Generalize the decoder enough to walk every part and emit:
  - opcode/command frequency;
  - part/channel usage;
  - instrument IDs and patch transitions;
  - tempo/timer changes;
  - gate/Q/q usage;
  - software/hardware LFO usage;
  - SSG envelope/noise/mixer changes;
  - FM3 slot usage;
  - rhythm shot/dump/volume/pan usage;
  - unsupported opcode locations.
- Produce a normalized JSON/CSV report only as a tooling artifact; do not ship it.
- Preserve the existing ST02 SHA-256 and zero-mismatch lane audit.
- Generate short register/state traces for selected notes from at least four contrasting songs.

Acceptance gates:

- All 23 ending-archive `.M86` entries are inventoried without an unknown file boundary.
- The same is done for `.M26`, or unsupported format differences are explicitly listed.
- ST02's current seven-lane comparison remains zero-mismatch.
- Unsupported commands are ranked by number of songs and occurrences. This ranking finalizes P1 versus P2 ordering.

**Implemented and verified (2026-07-12):**

- `tools/pmd_corpus_audit.py` and `tools/pmd_corpus_format.py` provide the reusable offline scanner; `tools/pmd_corpus_audit_test.py` covers its boundary grammar and ranking rules.
- THTK extraction is restricted to a temporary directory. Reports are generated under ignored `build/reports/pmd-corpus/` and are not runtime inputs.
- The ending archive hash is `ca787b8ff66f7b3f10c97b3ecc77cd466772767e3e9e8cf5a0c71dd612b1c8d7`; the main archive hash is `4d975850a66b4ec2ca5fd4fba7f3166dd9293d220ba8ab6c1be87ed98b1399be`.
- The strict scan inventoried 80 entries with 45 unique payloads, 23 unique M86 payloads, 23 unique M26 payloads, and zero scan/boundary errors.
- The four normalized traces are `ST00.M86`, `ST02.M86`, `ST03.M86`, and `STAFF.M86`.
- ST02 remains anchored to SHA-256 `60e0e4e9742db3d97bd02238f2602ad7f671c71077479d71593e69710be8f130`; all seven production lanes have zero mismatches.
- The highest-frequency unpreserved behavior is tempo (45 songs/852 occurrences), software-LFO clock/state commands (45 songs), software envelopes (45 songs/447 occurrences), detune (37 songs/483 occurrences), and portamento (26 songs/123 occurrences). PMD K/R SSG rhythm patterns occur in 44 songs with 603 decoded patterns; no YM2608 rhythm-unit control opcodes occur in this corpus.

### Phase 1 — Runtime timeline and deterministic reset

**Purpose:** allow full songs and ordered PMD-like controls without allocations in rendering.

Implementation targets:

- `CompiledOpnaSong.kt`: two-pass exact-size primitive timeline and typed integer event codes.
- `MmlCompiler.kt`: emit canonical event order and preserve rational/clock timing.
- New `CompiledOpnaPlayer.kt`: cursor-based dispatch directly from the compiled timeline.
- `MmlArrangementScheduler.kt`: become setup/translation only, then retire the expanded `OpnaSequencer` catalog path.
- `SoundPreviewPlayer.kt`: use the new player; preserve mono PCM16 streaming.
- Reset all timeline cursors, FM/SSG envelopes, shared noise/envelope, hardware LFO phase, software-LFO state, rhythm state, and output filter state at a loop boundary.

Acceptance gates:

- A synthetic song above 4,096 note records compiles and plays without raising arbitrary constants in the old scheduler.
- Audio callback allocation audit passes.
- Rendering the same song in different chunk sizes yields identical or explicitly bounded output.
- Two consecutive loop iterations hash identically in the conformance path.
- Bad Apple event counts, source timing, patch transitions, and audible production path remain intact.

**Implemented and verified (2026-07-12):**

- `CompiledOpnaSong` setup storage grows only during compilation, has a named malformed-input guard, and is trimmed to exact authored-array sizes.
- `CompiledOpnaTimelineFactory` first counts boundaries, then emits exact primitive arrays and performs a setup-only stable merge ordering by sample, explicit precedence, and source order.
- `CompiledOpnaPlayer` advances one primitive cursor. Android catalog playback uses it directly; `OpnaSequencer` remains active only for direct procedural motifs and compatibility tests.
- Same-time ordering is prior key-offs, key-ons/shots, then zero-gate key-offs. This preserves current `Q0` behavior until Phase 2 implements PMD's special semantics.
- Loop reset clears the cursor, FM/SSG voices and envelopes, shared noise/envelope phases, deterministic drum noise, hardware-LFO phase, output filters, EQ history, and optional resonator history without changing configured oversampling, EQ coefficients, or LFO rate/enable.
- A 4112-note synthetic song compiles to exact authored arrays and 8224 exact runtime boundaries, exceeding both retired catalog-path limits without increasing `OpnaSequencer.MAX_EVENTS`.
- Regression tests prove exact array sizes, canonical ordering, PCM parity with the retained sequencer, awkward external chunk invariance, and bit-identical consecutive loop renders.
- The thread-local allocation probe found and removed a 168-byte loop-reset allocation caused by three default `SsgPatch` constructions; compiled-player render/reset now passes the allocation threshold.
- Bad Apple remains 3864 authored events and 7323 runtime boundaries; the independent seven-lane ST02 oracle remains zero-mismatch.
- All 127 shared-engine tests pass; common metadata, Android compilation, `:app:assembleDebug`, and the OPNA hot-path audit pass.

### Phase 2 — Remove Bad Apple-specific driver state

**Purpose:** make envelopes, gate, and timing valid for any PMD-style song.

Implementation:

- Remove `LLS_PMD_BPM` and the fixed envelope clock from the global `lls_square` patch.
- Add a tempo-clocked PMD software-envelope state per SSG part.
- Support both PMD envelope formats:
  - legacy `AL, DD, SR, RR`;
  - extended `AR, DR, SR, RR, SL[, attack level]`.
- Support PMD Normal/EX0 tempo-dependent clocking and Extend/EX1 fixed approximately 56 Hz clocking.
- Carry envelope settings as ordered part controls rather than instrument-global constants.
- Define gate in source clock units with:
  - proportional `Q`;
  - fixed `q` subtraction;
  - deterministic random range using a named seeded PRNG;
  - minimum sounding length.
- Correctly define PMD `Q0` special behavior instead of mapping it to immediate key-off.
- Store tempo as rational clock duration or Timer-B-derived fixed point. Convert to samples with an error accumulator so long songs do not drift.

Acceptance gates:

- Existing Bad Apple SSG articulation hashes remain available as a legacy comparison, but the new implementation derives its clock from song tempo.
- The same envelope produces proportionally different timing under PMD Normal mode at two tempos and identical timing under Extend mode.
- Tempo changes affect Normal envelopes at the exact event boundary.
- Gate tests cover Q/q interaction, deterministic randomization, minimum duration, ties, slurs, and same-timestamp retriggers.

**Implemented and verified (2026-07-12):**

- `lls_square` is once again a timbre-only patch. Its Bad Apple BPM constant and patch-owned software-envelope fields were removed.
- MML2 now retains exact milli-BPM and a declared `#PMDCLOCK`; `PmdSampleClock` converts authored ticks to samples with integer rational arithmetic and a carried remainder across tempo changes.
- `E` accepts legacy `AL,DD,SR,RR` and extended `AR,DR,SR,RR,SL[,attack level]` definitions. `EX0`/Normal follows the tempo clock, while `EX1`/Extend uses a named fixed 56 Hz law. Definitions and mode changes remain ordered SSG-part controls in the compiled program and runtime timeline.
- Each `SsgVoice` owns one preallocated `PmdSoftwareEnvelope`; patch changes no longer overwrite performance state, and render/reset remain allocation-free.
- `Q0` now means full authored duration. `Q0..8`, `Q%0..255`, fixed/random `q` subtraction, inclusive deterministic random ranges, and minimum sounding clocks resolve during compilation and retain their source metadata.
- Bad Apple declares 24 PMD clocks per quarter, authors the decoded legacy envelope on G/H, and migrates its former `q20/q40` compiler-tick values to source-clock `q1/q2`. It retains 3864 musical events plus four ordered controls and expands to 7328 runtime events including the initial tempo control.
- Tests cover legacy/extended envelope stages, two-tempo Normal behavior, tempo-independent Extend behavior, exact-boundary live tempo changes, rational decimal-tempo boundaries, Q/q interaction, random endpoints/determinism, minimum duration, ties, slurs, and retrigger order.
- All 134 shared-engine tests, the compiled-player allocation probe, common/Android compilation, `:app:assembleDebug`, and the OPNA audit pass. The 45-song corpus audit reports zero scan errors, and the seven-lane ST02 oracle remains zero-mismatch.

### Phase 3 — SSG register-faithful core and output profiles

**Purpose:** improve every song's SSG behavior without song-specific EQ.

Implementation:

- Replace direct floating-frequency SSG tone with a 12-bit period derived from the 8 MHz master clock and documented divider.
- Quantize pitch to the legal period and test the measured error across the note range.
- Remove `duty` from the hardware SSG contract or rename the existing oscillator as a clearly non-hardware procedural mode. YM2608 SSG tone is fixed duty; the current `duty` property is not used by rendering.
- Model register 7 tone/noise enables and register 6 shared noise period explicitly.
- Model the shared hardware envelope as global state with deterministic write/restart ordering.
- Extend compiler diagnostics to cover incompatible simultaneous noise-period requests as well as envelope conflicts.
- Verify the 17-bit noise LFSR polynomial, output tap, and clock against an independent trace.
- Replace the implicit level curve with a documented, generated law and independent amplitude tests.
- Add `OpnaOutputProfile` with at least:
  - `TIMEBOX_LEGACY` (bit-for-bit migration path);
  - `PC9801_86_REFERENCE` (initial SSG/FM hypothesis around 25%);
  - optional `PC9801_26K_REFERENCE` for `.M26` comparison.

Acceptance gates:

- Legal SSG periods and frequencies match the independent oracle.
- Shared noise/envelope write-order tests pass.
- No SSG state is derived from a song title or fixed song BPM.
- Bad Apple is listening-tested under legacy and reference output profiles before changing its default.

**Implemented and verified (2026-07-12):**

- `SsgHardwareLaws` derives legal 12-bit tone periods from the standard 8 MHz clock and Yamaha's `f = φM/(64*TP)` law. Tone counters and period-domain portamento run in shared preallocated chip state and can advance multiple hardware toggles per output sample.
- The unused duty-cycle field and sequencer parameter were removed. YM2608 SSG tone is now one fixed 1:1 rectangular-wave contract in both the retained sequencer and compiled player.
- `SsgSharedState` explicitly owns register-7 active-low tone/noise bits, register-6 noise period, three continuously clocked tone counters, the shared 17-bit noise LFSR, and global envelope period/shape/restart state. Register-$0D shape writes restart; period writes do not.
- The fixed-volume table is generated from the documented odd steps of the logarithmic 5-bit DAC: 0.75 dB per DAC step and two DAC steps per fixed-level code. Level zero remains digital silence.
- `MmlCompiler` warns separately when overlapping SSG parts request incompatible shared noise periods or incompatible hardware envelopes; deterministic last-note-on register ordering remains explicit.
- `OpnaOutputProfile.TIMEBOX_LEGACY` preserves the established bus gains and remains the default. `PC9801_86_REFERENCE` exposes the initial 25% SSG/FM hypothesis without any song-title selection or global default change.
- Independent tests cover nearest legal periods over MIDI 24..127, measured tone frequency at 44.1/48/55.466 kHz, register-7 bits, a hardcoded 20-step 17-bit LFSR trace, envelope write/restart order, shared-register source order, the generated DAC law, conflict warnings, and both output profiles.
- Bad Apple renders deterministically and finitely under both profiles, while the legacy profile stays selected pending human listening. Its authored/timeline counts and seven-lane source oracle remain unchanged with zero mismatches.
- All 144 shared-engine tests, the compiled-player allocation probe, common/Android compilation, `:app:assembleDebug`, and the OPNA hot-path audit pass. The 45-song corpus audit reports zero scan errors.

### Phase 4 — Hardware and PMD software LFO conformance

**Purpose:** replace the current approximate shared sine modulation with reusable PMD articulation.

Implementation:

- Build independent tests for all eight YM2608 hardware-LFO rates.
- Verify actual PM and AM waveform shapes, phase cadence, PMS mapping, AMS attenuation, operator AM enable, and global enable/rate semantics before changing code.
- Keep the hardware LFO global while PMS/AMS remain per FM channel.
- Implement two preallocated PMD software-LFO states per part.
- Support waveform, delay, speed, depth, repetition, key-on sync/free-run, pitch/volume target, FM TL slot mask, fixed/tempo-dependent clock mode, and depth evolution.
- Implement deterministic random LFO with an explicit seed/reset contract.
- Do not compute trig in the callback.

Acceptance gates:

- Hardware-LFO traces match the independent oracle for every rate/PMS/AMS combination selected by the corpus.
- Chunk-size and loop-reset determinism pass.
- Software-LFO tests cover all waveforms actually present in the TH04 corpus before optional rare modes are added.
- Bad Apple remains unchanged unless its source actually contains a newly preserved LFO command.

**Implemented and verified (2026-07-12):**

- `OpnaLfoLaws` names the Yamaha-manual hardware rates (3.98 through 72.2 Hz), PMS peaks (0 through 80 cents), and AMS peaks (0 through 11.8 dB) in engine integer units. `Lfo` carries the rational phase remainder instead of truncating it every sample, remains one global oscillator, and resets deterministically on global enable edges.
- Hardware tests cover one-second phase cadence at all eight rates, the shared sine PM/bias-AM relationship, every PMS/AMS table entry, operator AM-enable isolation, global enable/reset behavior, and prepare-chunk invariance. Trigonometry remains setup-only in the generated sine table.
- Every FM and SSG part owns two preallocated `PmdSoftwareLfo` states. Ordered MML2 controls implement `M/MA/MB`, `MW/MWA/MWB`, `*/*A/*B`, `MM/MMA/MMB`, `MX/MXA/MXB`, and `MD/MDA/MDB` without callback allocation.
- The seven documented PMD waveforms implement delay, speed, signed depth, repetition/hold rules, pitch and volume targets, key-on synchronization or free-run, Normal tempo clocks or the documented approximately 56 Hz fixed clock, FM TL slot masks with PMD's sign reversal, and depth evolution. Random modulation uses an explicit per-state seed and reset contract.
- SSG pitch modulation is applied as a per-sample legal-period offset before the shared tone counter advances; SSG volume modulation remains in the generated level domain. FM software pitch combines with hardware PM, while software volume adjusts selected TL slots. Inactive states have an explicit fast path.
- TH04 corpus inspection shows the active authored mode is fixed-clock waveform 6 (one-shot), including definitions such as `[delay=0,speed=2,depthA=1,depthB=30]`; that exact mode has an integration trace. Dormant per-part clock setup is widespread, while the corpus contains no hardware-LFO commands.
- Chunked PCM and full player-reset PCM are identical with active FM and SSG LFOs. All 158 common/native tests and both JVM allocation tests pass; the common/Android/app build matrix and OPNA hot-path audit pass.
- The 45-song corpus scan still has zero errors. ST02/Bad Apple still has 487/205/205/570/570/712/710 decoded lane events and zero mismatches; because its production source has no active preserved LFO definition/switch, its authored and runtime event totals remain unchanged.

### Phase 5 — FM core differential conformance

**Purpose:** improve chip behavior by evidence, not tonal guesswork.

Implementation:

- Create small register-derived test vectors covering:
  - all eight algorithms;
  - feedback 0..7;
  - MUL=0 and 1..15;
  - positive/negative detune across keycodes;
  - AR/DR/SR/RR/SL/KS boundaries;
  - SSG-EG shapes;
  - retrigger and key-off behavior;
  - FM3 special mode;
  - hardware LFO AM/PM.
- Compare phase, envelope attenuation, operator output, and mixed sample checkpoints—not only final RMS/zero crossings.
- Audit modulation scaling, feedback history/shift, log-sine/power quantization, envelope cadence, carrier summing, and pan.
- Separate the chip-conformance path from:
  - 2x oversampling and its current fixed one-pole decimator;
  - the output one-pole filter;
  - soft clipping;
  - song EQ.
- Replace unexplained filter coefficients only after impulse/frequency-response targets are named.
- Use the hardware utility's direct-register interface as an optional real-chip capture oracle if matching hardware becomes available; do not make hardware access a build dependency.

Acceptance gates:

- Independent state/sample vectors pass on JVM tests and at least one non-JVM compilation target.
- Allocation/hot-loop audit passes.
- Existing source-derived patches retain their decoded registers exactly.
- Any audible change is A/B tested across multiple songs, including a sparse FM song, a dense multi-carrier song, and a mixed FM/SSG song.

**Implemented and automated gates verified (2026-07-12):**

- `OpnaFmDifferentialConformanceTest` uses a test-only register-step oracle built independently from direct phase, logarithmic sine/power, feedback, and algorithm equations. It compares every frame's phase, raw envelope attenuation, operator output, feedback history, and mixed core sample for all eight algorithms and feedback settings 0..7.
- Register vectors cover MUL 0 and 1..15, positive/negative manual detune rows across representative keycodes, the complete keycode nibble mapping, exhaustive normal/release rate equations for every register and key-scale offset, all SL values, and exact decay/sustain/release checkpoints.
- FM3 special mode retains four independent phase steps. Hardware PM and per-operator AM have direct phase/output checkpoints. All eight SSG-EG boundary shapes and key-off transitions are state-tested, and retrigger/phase reset remains covered by the existing core suite.
- The low-rate effective-rate-fraction-1 EG subcycle was corrected from a generic distribution to the five-pulse `0,1,0,1,1,1,0,1` cadence. Other rate fractions and the high-rate cadence remain unchanged.
- SSG-EG key-off now materializes the currently visible doubled/inverted attenuation before entering ordinary RR release, preventing a discontinuous level jump.
- When both YM2608 L/R output bits are represented as enabled, stereo now routes the full core sample to each bus instead of applying a constant-power 0.707 center gain. Production mono is unchanged.
- Direct `Fm4OpVoice` traces remain outside oversampling, the output one-pole filter, soft clipping, EQ, and stereo presentation. No unexplained filter/decimator coefficient was changed, and decoded PMD patch register tests remain exact.
- All 170 common/native tests and both JVM allocation tests pass. Windows Kotlin/Native supplies the non-JVM state/sample target; common metadata, Android shared code, app compilation, and `:app:assembleDebug` pass. The OPNA hot-loop audit passes.
- The 45-song corpus scan retains zero errors and the seven-lane ST02 oracle retains zero mismatches. Automated sparse/dense/mixed render regressions pass, but human multi-song listening/A-B acceptance has not been performed and remains an explicit manual gate before phases 6-8.

### Phase 6 — FM3 slot semantics and live controls

**Purpose:** preserve PMD songs that use channel 3 as multiple operator parts.

Implementation:

- Represent the 4-bit slot mask directly instead of assuming one logical part per operator.
- Keep algorithm channel-global.
- Apply patch changes only to selected slots; update feedback only when slot 1 participates, matching PMD's documented rule.
- Support per-slot detune, key-on delay, TL changes, and software-LFO TL masks as corpus evidence requires.
- Diagnose overlapping/contradictory slot ownership at compile time.

Acceptance gates:

- Two-slot/two-slot and other corpus-used splits match normalized PMD traces.
- Patch changes on one slot group do not corrupt the other group.
- Normal channel C and extended C-part behavior cannot be active ambiguously.

**Implemented and automated gates verified (2026-07-12):**

- FM3 notes now carry an explicit four-bit physical slot mask through the compiled song, exact-size timeline, retained sequencer, and synthesizer. `C1`-`C4` retain one-bit defaults for existing songs, while PMD-style `s0..15` (including `$` hexadecimal masks) supports grouped splits such as `s3`/`s12`.
- Extended-part `@` writes are ordered controls. ALG remains channel-global; operator definitions and TL are written only to selected slots; FB is imported from a patch only when slot 1 participates. Later `O` and `FB` absolute/relative writes preserve authored same-tick order.
- `sd`/`sdd` use shared physical per-slot FNUM offsets, `sk` stores per-slot key-on delays in exact timeline frames and cancels a pending key-on when the note ends first, and `MM` masks both pitch and volume software-LFO targets in FM3 mode. Normal FM behavior keeps its existing all-slot pitch law.
- Compile-time ownership analysis rejects overlapping physical masks across active extended parts. Channel C sound events remain forbidden whenever extended mode is enabled, so normal and extended channel-3 playback cannot coexist ambiguously.
- `Fm3SlotSemanticsTest` covers two-slot/two-slot normalized masks, overlapping ownership diagnostics, normal/extended exclusivity, selected patch isolation, ALG/FB rules, absolute/relative live controls, same-tick `@ -> O -> key-on` ordering, and state parity between the primitive player and retained sequencer.
- All 176 common/native tests and both JVM allocation sentinels pass. Common metadata, Android shared code, app compilation, `:app:assembleDebug`, and the OPNA hot-path audit pass. The strict 45-song corpus scan has zero errors and confirms that the current TH04 corpus has no FM3/live-FM command occurrences; the synthetic PMD-derived traces therefore provide this phase's positive coverage. The seven-lane ST02 oracle remains zero-mismatch.

### Phase 7 — Rhythm semantics with procedural sound generation

**Purpose:** separate PMD rhythm control accuracy from the legal/procedural choice of drum timbre.

Implementation:

- Add ordered YM2608 rhythm control events for shot, dump, master level, per-voice level, and per-voice pan.
- Add PMD K/R pattern definitions and selection if the corpus report shows they are required.
- Distinguish PMD's SSG drum/effect patterns from the YM2608 six-voice rhythm unit.
- Preserve register/control behavior even when the timbre generator is a clean-room procedural approximation.
- Improve each procedural drum from mathematical envelopes/noise/oscillators and multi-song listening tests. Never import rhythm-ROM WAV files or copied sample bytes.
- Document that procedural timbres are behaviorally compatible approximations, not ROM-authentic samples.

Acceptance gates:

- Shot/dump and simultaneous-hit ordering match PMD traces.
- Level and pan controls are independently testable.
- No runtime/sample assets are added.
- At least three rhythmically distinct songs are listening-tested before replacing the current Bad Apple rhythm default.

**Implemented and automated gates verified (2026-07-12):**

- PMD `\\b/\\s/\\c/\\h/\\t/\\i` shot and `p` dump commands compile as ordered six-bit YM2608 rhythm controls. `\\V`, per-voice `\\v*`, and `\\l*/\\m*/\\r*` pan writes support documented absolute/relative ranges and remain ordered at identical ticks.
- The primitive timeline and retained sequencer dispatch the same shot, dump, master-level, per-voice-level, and per-voice-pan state. Levels clamp to the documented 0..63 and 0..31 domains; the procedural generator applies them as normalized control gains without importing hardware ROM data.
- PMD K/R authoring is separate from the YM2608 rhythm unit: `K R0...` expands `R0`..`R255` definitions into tagged `SSG_DRUM_SHOT` events. The documented SSG drum instrument numbers are mapped to clean-room procedural approximations, and these events do not mutate YM2608 rhythm-register state.
- `OpnaRhythmSemanticsTest` covers same-tick control ordering, all six physical shot bits, masked dump behavior, independent level/pan state, retained-sequencer parity, malformed ranges, and K/R pattern expansion.
- No rhythm-ROM WAV, copied sample bytes, runtime files, or other assets were added. Existing procedural drum equations and the production Bad Apple rhythm lane were intentionally left unchanged because the required three-song human listening comparison has not been performed.

### Phase 8 — Add songs through a repeatable ingestion checklist

For each candidate song:

1. Record archive and entry SHA-256.
2. Run the corpus decoder and list every used/unsupported command.
3. Select `.M86` as primary; use `.M26` as a shared FM/SSG comparison, not as a blind substitute.
4. Decode only required instruments into a per-song compact patch bank.
5. Generate or hand-author readable MML/control directives.
6. Compare normalized timing, pitches, patches, volume, gate, and state changes.
7. Compile to the primitive timeline and record event/control totals.
8. Render conformance audio without mastering, then product audio with the selected output profile.
9. Listen on multiple songs before changing any global synthesis/mix law.
10. Add the song to `MmlSongBank` and `SongCatalog` only after tests pass.

Good first additions should exercise different engine areas rather than selecting the closest song to Bad Apple:

- one FM-dominant song using a different algorithm/patch set;
- one SSG-heavy song;
- one song using hardware or software LFO;
- one song using FM3 extended mode or meaningful rhythm controls, if present in the corpus.

**First checklist ingestion implemented and automated gates verified (2026-07-12):**

- Candidate: `LOGO.M86`, selected because it is short, SSG-heavy, and actively exercises PMD software-LFO waveform 6. The primary entry SHA-256 is `1e572f2677129bdc16bc79323c2e8369ca1c958d9c2685e3c48e21e74c2e66f7`; its archive SHA-256 is `ca787b8ff66f7b3f10c97b3ecc77cd466772767e3e9e8cf5a0c71dd612b1c8d7`. `LOGO.M26` is byte-identical and has the same entry hash, so it is recorded as corroboration rather than substituted blindly.
- The strict decoder reports no scan errors. Used bytecode commands are instrument, volume/step-down, tempo, detune, loop start/end, master transpose, bar length, both software-LFO clock modes, software-LFO definition/wave/switch, and software envelope; no used command remains unrepresented after normalization.
- Only embedded voice 79 is required. `LogoSongPatchBank.At79` contains its decoded DT/MUL, TL, KS/AR, AM/DR, SR, SL/RR, ALG, and FB register fields; tests compare every stored field.
- `LOGO_M86_MML_SOURCE` preserves the two FM notes, 64 notes on each of three SSG lanes, exact 3-clock SSG durations, 6-clock I-part delay, volume transitions, one-shot fixed-clock LFO controls, software envelopes, detune, and the ordered 80 -> 70 -> 60 -> 45 -> 30 -> 18 tempo ramp. Global tempo insertion is now sorted by tick even when PMD authors changes on different parts.
- The compiled result contains 227 authored note/control events, five sorted tempo changes, a 3,960-tick one-shot duration, and 429 runtime boundaries. `LogoSongIngestionTest` checks the full normalized pitch/start/duration/volume traces, patch registers, tempo state, deterministic product prefixes at 48,000 and 55,466 Hz, and a finite non-empty unmastered prefix.
- The cached song is registered as `synth-mml-lls-logo` in `MmlSongBank` and `SongCatalog`. No global synthesis, mix, EQ, or rhythm law changed during ingestion; listening remains required before any such change.

## Post-implementation architecture repair (2026-07-12)

The first Phase 6-8 implementation passed its automated tests but violated several ownership and evidence requirements above. The repair is now part of the architecture:

- The compiled-song-to-`OpnaSequencer` compatibility translator and its duplicate PMD/FM3/rhythm dispatch catalog were removed. Direct procedural `OpnaSequencer` motifs remain supported.
- `OpnaChipState`, `PmdPerformanceState`, and `SongMastering` now own chip, driver-part, and product-output state respectively.
- FM3 C1-C4 preserve logical-part identity, independent volume and two-LFO state, explicit patch/control ordering, selected-slot patch isolation, exact key-delay boundaries, and time-aware slot ownership. Unsupported Channel C part-local commands are rejected.
- Legacy drums, YM2608 rhythm controls, and PMD K/R SSG effects have separate procedural generators, buses, silence/reset contracts, and canonical same-time precedence.
- Compiled programs carry exact used-only, song-local instrument banks. `LOGO.M86` patch 79 no longer occupies the global built-in ID/name map.
- `tools/oracles/logo_m86_normalized.json` is a compact derived semantic oracle covering the five active parts, 196 notes, represented controls, source hash, and decoded patch registers. It contains no archive/audio bytes and is never a runtime input.
- Interaction tests replace compatibility-parity assertions. Passing tests do not waive the outstanding human tonal/rhythm/listening gates.

## Testing strategy

### Structural tests

- Parser diagnostics and command-range tests.
- Exact primitive event/control counts.
- Same-timestamp ordering.
- Capacity and malformed-input rejection.
- Per-song instrument-bank isolation.

### Chip/driver state tests

- Phase/FNUM/period checkpoints.
- Envelope and LFO state checkpoints.
- Shared SSG noise/envelope state.
- FM3 slot ownership and patch application.
- Rhythm shot/dump/level/pan state.
- Loop-reset state equivalence.

### Render tests

- Deterministic hashes at 48,000 Hz and offline 55,466 Hz.
- Chunk-size invariance across awkward sub-chunks and event boundaries.
- Mono production and stereo conformance paths.
- Invalid/NaN/Inf/clip counters.
- Frequency, RMS, peak, spectrum, and DC checks as supporting evidence only.

### Musical acceptance

Tests cannot establish musical correctness. Maintain a short multi-song listening suite with timestamped notes for:

- harsh 2-3 kHz energy;
- bass masking;
- midrange loss;
- FM/SSG balance;
- envelope articulation;
- LFO depth/speed;
- rhythm balance and transients;
- loop-boundary clicks or state discontinuities.

No global gain, filter, EQ, modulation, or envelope change should be accepted based only on Bad Apple.

## Required law gates for every implementation phase

- Quote the relevant engine law before editing.
- Identify source set and whether the change touches the audio hot path.
- Read every involved file and caller completely before behavioral claims.
- Use primitive arrays/preallocated state and `while` loops in callbacks.
- Run the hot-path and engine-law audits.
- Confirm no runtime assets or platform APIs entered `commonMain`.
- Review all constants for a named derivation.
- Preserve the Android platform wrapper as a PCM presenter, not a music engine.

Required local verification command:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:testDebugUnitTest :shared-engine:compileCommonMainKotlinMetadata :shared-engine:compileKotlinMetadata :shared-engine:compileDebugKotlinAndroid :app:compileDebugKotlin :app:assembleDebug
```

## Decisions to defer until corpus evidence exists

- Full PMD binary playback in runtime.
- Raw YM2608 register-stream frontend.
- CSM and timer emulation beyond musical scheduling.
- ADPCM-B sample playback, 86PCM, PPSDRV, or PPZ8.
- Bit-perfect rhythm-ROM reproduction, which conflicts with the procedural asset law.
- Changing production from mono to stereo.
- Changing Bad Apple's two-bar versus three-bar cut.
- Replacing current Bad Apple EQ or mix settings before a multi-song A/B pass.

## Recommended immediate work order

1. Phase 0 corpus inventory and normalized trace tool.
2. Phase 1 exact-size ordered runtime timeline.
3. Phase 2 tempo/gate/software-envelope generalization, removing `LLS_PMD_BPM`.
4. Phase 3 SSG period/shared-register/output-profile work.
5. Re-rank Phases 4, 6, and 7 using actual TH04 command frequency.
6. Phase 5 FM-core differential corrections in small independently proven changes.
7. Add the first contrasting songs through Phase 8.

This order creates reusable evidence and removes the clearest Bad Apple-specific state before making broad tonal changes.
