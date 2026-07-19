# TimeBox Sound Architecture Stabilization Plan

Status: planning document only. This document proposes future work; it does not authorize or contain source-code changes.

## 1. Goal

Move the sound system from a development/research architecture to one stable, efficient product architecture for composing and playing TimeBox MML songs. The sound engine is a procedural, clean-room YM2608-based implementation. The MML layer is a separate clean-room PMD-based language and performance model, with LLS-era PMD 4.8 behavior used as the historical parity profile.

The intended result is not a PMD emulator. It is:

- one documented TimeBox MML language;
- one compiler backend;
- one immutable song program;
- one allocation-free playback scheduler;
- one procedural OPNA render core;
- one explicit owner for shared chip state and render configuration;
- a corpus-driven definition of the PMD musical behavior that TimeBox must express;
- a simple path for adding songs without changing engine code.

The FM/SSG synthesis core is not the rewrite target. The current core already contains the valuable work: four-operator FM, OPN rates and pitch, six FM channels, three SSG channels, shared SSG state, hardware/software modulation, procedural rhythm, primitive buffers, and deterministic rendering. The main work is to remove duplicate orchestration and make the remaining semantic boundaries explicit.

## 2. Clean-room boundary

### In scope

- Musical behavior needed by the 23 unique LLS `.M86` programs.
- Hardware-shaped YM2608 behavior useful to clean TimeBox compositions.
- Six FM parts, three SSG parts, six rhythm voices, and FM3 special-mode ownership.
- Notes, rests, durations, gates, ties/slurs, tempo, transpose, detune, portamento, patches, volume, loops, SSG envelopes, LFOs, pan flags, rhythm patterns, and typed shared-chip controls.
- A procedural replacement for YM2608 rhythm sounds and PMD SSG effects.
- Offline corpus tooling used as evidence and migration support.
- Better architecture, diagnostics, deterministic reset, performance, and listening quality.

### Explicitly out of scope

- Runtime loading or interpretation of `.M`, `.M86`, `.M26`, S98, or VGM data.
- PMD's compiled binary layout, work-memory layout, driver API, DOS interrupts, PMDWin DLL/COM API, or file-search behavior.
- PMD source syntax compatibility. TimeBox may use clearer syntax for the same musical semantics.
- PMD bugs, integer overflow, undefined behavior, timing busy-waits, and historical parser limits.
- PCM, ADPCM, P86, PPS, PPZ, rhythm-ROM, WAV, or other sample playback.
- Raw register-write compatibility. If an observed song needs a behavior, add a typed semantic command instead.
- Bit-accurate YM2608 emulation.

These boundaries follow `PMDMML.MAN`, `PMDDATA.DOC`, `PPS.DOC`, `P86DRV.DOC`, `DLLInfop.txt`, `PMDWin.txt`, `PSGEDATA.DOC`, and the relevant YM2608 hardware-manual pages. The manuals are behavioral references only; PMD/PMDWin internal source layouts must not be copied.

### Evidence classification

- `PMDMML.MAN` and `PSGEDATA.DOC`, plus narrowly relevant behavioral statements in `PMDDATA.DOC`, describe observable musical behavior. They do not authorize copying packed records, work areas, offsets, or driver control flow.
- `PMDWin.txt` is failure-history evidence. A listed bug suggests a regression test; it does not by itself specify the correct behavior.
- `PPS.DOC` documents a sample-based SSG PCM system and reinforces the PPS/PCM non-goal. `P86DRV.DOC` is likewise about PCM files, memory, interrupts, and registration, none of which creates a TimeBox runtime requirement.
- `DLLInfop.txt` supports independent playback instances and caller-owned output buffers as useful design properties. Its DLL ABI, file search, thread model, WAV rhythm loading, and public work structures are irrelevant to TimeBox.
- Driver waits, service numbers, file formats, packed effect layouts, raw pointers, and PMD work-memory fields must never become TimeBox architecture or checked-in runtime data.
- Hardware manuals define the YM2608-shaped ownership and numeric domains. Historical song archives and dumps show occurrence and musical use; they are not source code or runtime assets.

## 3. Evidence used

### Current product code

The live Android path is:

```text
SoundPreviewPlayer
  -> SongCatalog.byId()
  -> SongDefinition.buildPlayback(volume)
  -> MmlSongBank.getArrangement()
  -> MmlArrangementScheduler.createPlayer()
  -> CompiledOpnaSong (tick domain, produced by MmlCompiler)
  -> CompiledOpnaTimelineFactory (sample-rate lowering)
  -> CompiledOpnaPlayer
  -> OpnaLikeSynthesizer
  -> OpnaMixer + SongMastering
  -> PCM16 conversion and blocking AudioTrack writes
  -> mono PCM16 AudioTrack
```

Primary evidence:

- `shared-engine/src/commonMain/.../SongCatalog.kt:24`
- `shared-engine/src/commonMain/.../audio/mml/MmlSongBank.kt:326`
- `shared-engine/src/commonMain/.../audio/mml/MmlCompiler.kt:32`
- `shared-engine/src/commonMain/.../audio/opna/CompiledOpnaSong.kt:9`
- `shared-engine/src/commonMain/.../audio/opna/CompiledOpnaTimeline.kt:5`
- `shared-engine/src/commonMain/.../audio/opna/CompiledOpnaPlayer.kt:4`
- `shared-engine/src/commonMain/.../audio/opna/OpnaLikeSynthesizer.kt:286`
- `app/src/main/java/.../engine/SoundPreviewPlayer.kt:151`

The old `IMPLEMENTATION_PLAN.md` and `repair_plan.md` were deliberately not used. The current code and current references were understandable without treating old plans as present truth.

### Corpus evidence

`tools/pmd_corpus_audit.py` was read completely and run read-only against the two requested TH04 archives using their actual Unicode names:

- `幻想郷ED.DAT`, SHA-256 `ca787b8ff66f7b3f10c97b3ecc77cd466772767e3e9e8cf5a0c71dd612b1c8d7`
- `東方幻想.郷`, SHA-256 `4d975850a66b4ec2ca5fd4fba7f3166dd9293d220ba8ab6c1be87ed98b1399be`

Results:

- 80 archive entries;
- 23 unique M86 payloads and 23 unique M26 payloads;
- 45 unique payloads overall because `LOGO.M26` and `LOGO.M86` are identical;
- zero scan errors;
- 161 pitched active M86 parts;
- 26,697 unexpanded M86 notes;
- 76,562 counted-loop-expanded first-pass M86 notes;
- 527 K-part pattern selections and 302 rhythm patterns across 22 of 23 M86 songs.

The corpus scanner is evidence of occurrence and normalized observations, not proof of exact PMD semantics. Its own capability records are `OBSERVED_ONLY`, and its independent exact-evidence count is currently `0/4` (`tools/pmd_corpus_audit.py:53`, `:1238`).

The current Bad Apple audit passes only the fixed source window `[288, 5280)`, with the current x20 tick conversion, for recovered pitched lanes A-E, G, and H. It compares only tick, duration, MIDI pitch, and patch (`tools/pmd_corpus_audit.py:933`). It does not compare the K/R rhythm data, control-only parts, gate, volume, envelope, detune, tie/retrigger behavior, shared state, register-equivalent state, or rendered audio. This is a useful migration fixture, not whole-song parity evidence.

The Bad Apple dump/corpus scan confirms that the selected source window also exercises 10 rhythm patterns and 14 selections, four portamentos, four ties, 29 Q-gate changes, 28 volume changes, 17 detunes, loops, and SSG envelopes. It contains LFO clock-mode setup but no active software-LFO definition/wave/switch; those active cases belong to LOGO, ST03, and STAFF. These observations define missing verifier coverage, not automatic proof that the current lowering is correct.

The verifier also has structural limitations that Phase 2 must remove: it drops parts with no pitched notes, separates notes from controls so same-clock order is lost, mutates some state such as relative volume without emitting a semantic event, does not model note key-off/retrigger lifecycle, and hashes K/R pattern bytes without producing timed rhythm semantics. Its compact repeated-note representation is lossy when non-pitch fields differ. Full decoded traces must remain ephemeral local outputs; they must not become checked-in reconstructions of the source songs.

`MUSIC.TXT` supports `_86.M` as the published six-FM/three-SSG/rhythm form and `_26.M` as the standard-FM form, but it is a partial bonus-data notice rather than proof of every archive entry. `PC-98 Eternal Shrine Maiden.txt` identifies itself as a website-version arrangement by pedipanol; it may be used as secondary syntax/stress evidence, never as original ZUN or LLS corpus truth.

Names such as `PMD86_YM2608_86PCM` identify source provenance/profile only. They do not weaken TimeBox's explicit exclusion of PCM, ADPCM, P86, PPS, or sample playback.

### Manual evidence

Key PMD reference sections include:

- topology: `PMDMML.MAN:94-110`;
- patch fields: `PMDMML.MAN:1033-1129`;
- tie versus slur: `PMDMML.MAN:1577-1621`;
- gate and deterministic random gate: `PMDMML.MAN:1661-1752`;
- FM3 ownership: `PMDMML.MAN:2161-2231`;
- shared SSG tone/noise: `PMDMML.MAN:2292-2347`;
- two SSG software-envelope forms and clocks: `PMDMML.MAN:2599-2708`;
- two software LFOs and seven observed waveform values: `PMDMML.MAN:2727-3227`;
- hardware LFO/PMS/AMS: `PMDMML.MAN:3229-3350`;
- loop and song-loop behavior: `PMDMML.MAN:3366-3471`;
- slot key-on delay: `PMDMML.MAN:3673-3705`;
- pan/output flags: `PMDMML.MAN:3717-3778`;
- six-voice rhythm controls: `PMDMML.MAN:3781-3925`;
- procedural SSG-effect step fields: `PSGEDATA.DOC:4-58`.

The supplied UTF-8 extraction `D:\Programes\ym2608-info\zun_music\pc 88 va 12.txt` was read completely. The matching PDF was retained only for page/layout cross-checks; no OCR is required. This manual is a hardware and host-behavior reference, not a source of runtime data, BASIC syntax, or raw-register compatibility.

Relevant evidence in that extraction includes:

- physical topology and routing: lines 40-44, 1093-1126, and 7256-7288;
- the PC-88VA BASIC sound-mode surface: lines 1159-1199 and 5421-5456;
- mono summing versus stereo-capable outputs: lines 944-960;
- the global eight-rate hardware LFO selector: lines 3025-3072;
- per-FM-channel PMS, AMS, and independent L/R enables: lines 3156-3286;
- six rhythm shot/dump bits, shared attenuation, per-voice attenuation, and L/R enables: lines 3289-3435;
- host rhythm-pattern K/C/D transitions on a quarter-note/12 grid: lines 6482-6590.

The manual's statement that combined FM+SSG use is limited to three voices of each describes the PC-88VA BASIC sound-mode interface. Its mode 5 instead exposes six FM voices plus rhythm and disables SSG. TimeBox does not target that BASIC `PLAY` interface: the clean PMD/M86 semantic profile and physical chip ownership remain six FM parts, three SSG parts, and six rhythm voices. This distinction must stay explicit so a host-language policy is not mistaken for a YM2608 engine limit.

The supplied UTF-8 extraction `D:\Programes\ym2608-info\zun_music\NEC PC-9801-86 User's Manual.txt` was also read completely. The matching image-only PDF was not OCR'd; it remains only a visual page/layout cross-check. Useful additional evidence includes:

- nine simultaneous pitched voices—six stereo FM plus three mono SSG—and six separate stereo rhythm voices: lines 302-324, 1047-1080, and 1233-1248;
- explicit per-FM and per-rhythm-voice left/right routing: lines 1057-1071 and 1632-1646;
- slot-local FM amplitude-modulation enable, distinct from channel AMS and the chip-wide hardware LFO: lines 1575-1736;
- event-style rhythm key-on/dump masks, where only set voice bits act: lines 1744-1901;
- shared SSG noise, mixer, and hardware-envelope ownership: lines 2583-2690;
- the warning that encoded OPN patch fields can run in the opposite direction from human-facing timbre parameters: lines 2765-2779.

This later board manual resolves the earlier host-surface ambiguity directly: the PC-9801-86 advertises simultaneous six-FM/three-SSG performance. Its BASIC ROM exposes only YM2203-equivalent behavior (lines 2250-2260), which again demonstrates that a host command set may expose less than the physical sound engine. Neither limitation belongs in the TimeBox core.

## 4. Audit conclusion: the user is substantially right

The live compiled-song path is coherent and appropriately allocation-conscious. The bloat is concentrated around that path rather than inside the FM/SSG oscillator core.

### Confirmed duplicate or stale architecture

| Current surface | Evidence | Decision |
|---|---|---|
| `OpnaSequencer` object-event scheduler | Separate fixed 8,192-event store and separate dispatch in `OpnaLikeSynthesizer.kt:623-803`; no product caller | Port valuable tests, then remove from `commonMain` |
| `OpnaPatterns` and `Scale` | Used only by old sequencer tests | Replace with compiled TimeBox MML fixtures, then remove |
| `Envelope` float ADSR | Used only by `EnvelopeStageTest`; product FM uses `OpnRateEnvelope` | Remove with its obsolete test |
| Dual FM envelope description | Every `OperatorSpec` carries float ADSR and OPN register fields; `EgMode` selects a model | Convert remaining patches once to explicit OPN fields, then remove float ADSR and `OpnEnvelopeCompatibility` |
| MML v1 and v2 compiler backends | `MmlCompiler.kt:32-54`, v1 at `:56-123`, v2 at `:275-365` | Migrate all product/test sources to the stable language, then keep one backend |
| Tick-event and sample-event schemas | Parallel type tables in `CompiledOpnaSong.kt:117-173` and `CompiledOpnaTimeline.kt:28-77` | Keep two timing stages but one payload schema |
| Generic payload reuse | LFO/FM/rhythm controls reuse envelope arrays in `CompiledOpnaSong.kt:439-494` | Replace with named family-specific primitive payload tables |
| Unused synth factory parameter | `MmlArrangementScheduler.kt:11-15` | Remove after caller migration |
| One-value routing enum/check | `ArrangementLanes.kt:5-7`; Android rejects every value except the only possible value | Remove the false abstraction |
| Hidden research fixture in product bank | LOGO is catalog-hidden but eagerly compiled in `MmlSongBank.kt:326-328` | Move to test/research scope |
| Unused migration song | `BAD_APPLE_LLS_MIGRATION_FIXTURE_MML` has no caller | Delete after confirming no external tooling imports it |
| Misnamed persisted Bad Apple ID | `SENBONZAKURA_DEMO_KEY` names different content | Preserve persisted alias, expose a correct canonical ID |
| Split render policy | Android, scheduler, song bank, synth, mixer, and mastering each decide part of configuration | Introduce one immutable common-code session profile |

### Parallel systems that are not automatically duplicates

- `CompiledOpnaSong` and a sample-domain playback plan have different legitimate jobs. Musical time must remain independent of sample rate. The fix is shared typed payloads and a narrow boundary index, not collapsing musical ticks into renderer time.
- FM part state and shared chip state must remain separate.
- YM2608 rhythm control and PMD SSG effects are different devices/ownership domains even if both are procedurally synthesized.
- Mono is the product output, but stereo core rendering is still useful for correct L/R flag conformance and offline checks. The optional stereo resonator should remain disabled unless a listening decision explicitly retains it.
- Raw-core and mastered render checkpoints are verification surfaces over the same dispatcher, not separate song architectures.

## 5. Target architecture

```text
TimeBox MML source
       |
       v
One parser + normalizer
  - source locations
  - macros / bounded loops / rhythm patterns
  - syntax only
       |
       v
One semantic compiler
  - typed musical units
  - per-part state
  - shared-state arbitration
  - deterministic gate/LFO randomization
       |
       v
Immutable OpnaSongProgram (tick domain)
  - note payload table
  - FM control payload table
  - SSG control payload table
  - modulation payload table
  - rhythm/effect payload table
  - tempo and loop metadata
       |
       v
PlaybackPlanBuilder(sampleRate)
  - exact sample boundaries
  - canonical ordering
  - boundary kind + payload-family index only
       |
       v
OpnaPlaybackSession
  - one cursor
  - one dispatcher
  - OpnaChipState
  - PmdPerformanceState
  - PercussionRouter
  - OpnaMixer
  - SongMastering
       |
       v
caller-provided mono/stereo buffer
       |
       v
thin platform output
```

### Why this helps

- A new musical feature is defined once in a typed payload family, not copied through two giant property bags and two event-number tables.
- Tick semantics remain sample-rate independent.
- Runtime boundaries remain exact-size primitive arrays and allocation-free.
- Shared SSG, hardware LFO, FM3, and rhythm ownership are visible instead of implied by handler order.
- Android stops mutating core sound policy.
- Offline audit, preview, alarm, and test rendering instantiate independent sessions from immutable song data.

### Migration map from current code

The target names above name responsibilities, not mandatory renames or permission to build a parallel engine. Prefer evolving the closest current type in place:

| Target responsibility | Closest current implementation |
|---|---|
| `OpnaSongProgram` | `CompiledOpnaSong` |
| `PlaybackPlanBuilder` and immutable playback plan | `CompiledOpnaTimelineFactory` and `CompiledOpnaTimeline` |
| Session cursor and dispatcher | `CompiledOpnaPlayer` |
| Procedural chip renderer | `OpnaLikeSynthesizer`, `OpnaChipState`, and `PmdPerformanceState` |
| Immutable render profile | Policy currently split across `AudioLaws`, `OpnaOutputProfile`, `OpnaAudioConstants`, `MmlArrangementScheduler.MIX_GAIN`, song EQ, and Android setup |
| Percussion routing | Three current generator paths embedded in `OpnaLikeSynthesizer` |

Do not add a second `PartState` beside `PmdPerformanceState` or a second shared-chip owner beside `OpnaChipState`/`SsgSharedState`. A wrapper is justified only when it removes split ownership or exposes the single product session API; one class per diagram box is not a goal.

## 6. Target state ownership

| Owner | Owns | Must not own |
|---|---|---|
| `OpnaSongProgram` | Immutable patches, notes, typed controls, patterns, source map, loop metadata | Runtime cursors or mutable chip state |
| `PmdPerformanceState` | Six FM parts, three SSG parts, four FM3 logical owners; volume, patch, gate, transpose, two software LFOs, software envelope | Global SSG registers, global hardware LFO, rhythm master state |
| `OpnaChipState` | Six physical FM voices, three SSG voices, shared SSG mixer/noise/envelope, hardware-LFO and FM3 physical state, YM rhythm register-equivalent state, LUT-backed synthesis state | PMD syntax, timing conversion, or platform APIs |
| `PercussionRouter` | YM2608 rhythm domain and PMD SSG-effect domain, priority/restore rules | A third anonymous legacy drum bus |
| `OpnaPlaybackSession` | Player cursor, tick/sample conversion remainders, reset/replay, immutable render profile, synth/mixer/mastering instances | Android thread or `AudioTrack` lifecycle |
| Android wrapper | Audio thread, `AudioTrack`, mono PCM16 conversion, wake lock, stop/start | Oversampling policy, mix gain, EQ policy, song semantics |

All runtime state remains preallocated. No collections, lambdas, temporary arrays, parser work, sorting, or trigonometric initialization may enter rendering.

### Shared-state projection and lifecycle

Keep desired logical music state separate from the physically applied shared-chip state. An SSG effect may temporarily own channel 3 and shared noise, but music events must continue advancing the desired state. When the effect releases, project the current desired music state back onto the chip instead of restoring a stale acquisition-time snapshot.

A shared-noise write received during an effect remains pending. PMD documents applying it at the first subsequent music key-on after the effect ends (`PMDMML.MAN:2336-2342`); it must not be applied because an arbitrary audio chunk ended. Hardware-LFO enable/rate remains chip-wide, PMS/AMS/delay channel-local, and AM enable slot-local. FM3 remains one physical ALG, slot-1-owned FB, four physical slot detunes/key delays, and logical slot masks; overlapping slot ownership is a compile error, while incompatible same-clock ALG writes are shared-state collisions.

`reset()` and replay on an existing session must equal a fresh session made from the same immutable program and render profile. Reset must clear the player cursor and timing remainders; all voice phases, keys, envelopes, portamento and slot-delay state; both software LFOs and their deterministic RNG/depth state; hardware LFO and FM3 state; shared SSG mixer/noise/envelope and pending writes; effect ownership/priority/cursor; rhythm state; and mixer/mastering/filter/resonator history. Immutable policy and caller gain remain construction inputs, not mutable song state. Stop must release all owners and silence the session; starting again must behave like fresh playback.

## 7. Stable TimeBox MML contract

Do not create a third evolving dialect. Treat the current explicit `#MML 2` sources as the migration base for the stable TimeBox language, document the language as TimeBox MML, and remove headerless v1 after all owned sources are migrated.

PMD terminology may be used in documentation where it names a musical behavior, but TimeBox syntax does not need to reproduce PMD spelling.

### Required core language

- Simultaneously available fixed parts: FM A-F, SSG G-I, and one rhythm/pattern part.
- Notes, rests, octave, default/explicit lengths, percent lengths, dots, bars, and diagnostics with line/column.
- Tie as no key-off/no retrigger; slur as the separately documented retrigger behavior.
- Absolute and relative volume with explicit legal ranges.
- Absolute/relative transpose, master transpose, signed detune, portamento.
- One shared tempo map and an explicit clocks-per-quarter law.
- Deterministic Q/q gate behavior, including seeded random/minimum rules.
- Named instruments and mid-part instrument changes.
- Bounded loops, alternate endings, reusable macros, and an explicit song-loop point.
- Named rhythm patterns with ordered and simultaneous hits.

### Exact state-transition semantics

- A counted-loop iteration restores only syntax-local state: octave, default length, ornament, pseudo-echo, whole-note length, and key signature. Patch, volume, envelope, LFO, transpose, detune, and other performance state carry unless the song changes them.
- A final alternate-ending exit continues with state at the loop end. A song-loop point likewise restores the narrow syntax-local state at the marker while performance state carries from the previous pass. A song that needs identical passes must initialize its performance/shared state explicitly after the marker.
- Tie removes the intermediate key-off and key-on. Slur is the separate key-off/retrigger behavior.
- Gate lowering computes Q's proportional cut, subtracts q's fixed or seeded-random rear cut, then enforces q's minimum audible duration. A tie suppresses the resulting intermediate key-off.
- Defining a software LFO reinitializes that LFO immediately, including during a tie or when key-on sync is disabled; the next note observes its delay again. LFO1 and LFO2 use one implementation distinguished only by state index.
- Keep the approximately 56 Hz fixed software-envelope/LFO clock distinct from the approximately 60 Hz procedural SSG-effect step clock.
- The PMD manual's LFO range header conflicts with its seven-value behavior table. TimeBox retains seven explicitly named wave semantics `0..6`; it does not reproduce the header typo.

### Required FM semantics

- Four operators with MUL, DT, TL, AR, DR, SR, SL, RR, KS, AM, and SSG-EG.
- ALG and FB, with carrier mask derived once from ALG.
- Per-channel PMS, AMS, and hardware L/R enable flags.
- AM enable is per operator slot; AMS depth is per FM channel; hardware-LFO enable/rate is chip-wide. These ownership levels must not be collapsed into one part flag.
- One chip-wide hardware-LFO enable and one of eight discrete rate selectors. The hardware-reference mapping is `3.98`, `5.56`, `6.02`, `6.37`, `6.88`, `9.63`, `48.1`, and `72.2 Hz`; do not expose an invented continuous hardware-LFO rate.
- Discrete PMS selectors `0..7` correspond to `0`, `3.4`, `6.7`, `10`, `14`, `20`, `40`, and `80` cents peak deviation; AMS selectors `0..3` correspond to `0`, `1.4`, `5.9`, and `11.8 dB`. Keep selector values canonical and use the physical depths as conformance data.
- Keep runtime patches register-native. If composer-facing syntax presents TL or envelope rates as loudness/time rather than encoded OPN fields, lower that direction exactly once and test both endpoints; never let a friendly parameter silently reverse the stored hardware-shaped value.
- Two software LFOs per logical part with seven explicit wave values, delay, speed, depth, count, sync, pitch/volume targets, TL mask, and depth evolution.
- FM3 special mode with one physical channel, four slot masks, shared ALG, slot-1 FB behavior, per-slot detune/key delay, and compile-time collision diagnostics.

### Required SSG semantics

- Three tone periods and amplitudes.
- One shared noise generator/period.
- One shared mixer register represented as typed tone/noise enables.
- One shared hardware-envelope period/shape.
- Both PMD-style software-envelope forms and normal/fixed approximately 56 Hz clock modes.
- Two software LFOs per SSG part.
- Explicit arbitration when an SSG effect temporarily owns channel 3 or shared noise; music state must be restored afterward.

### Required rhythm/effect semantics

- Six fixed YM2608-like voices: bass drum, snare, cymbal, hi-hat, tom, rim.
- Shot and dump masks, master level, per-voice level, and L/R flags.
- Shot and dump are event masks: a set voice bit triggers that action and a clear bit leaves that voice unchanged. Define canonical ordering when shot and dump events share a sample boundary.
- Preserve the hardware-shaped attenuation domains: six-bit rhythm-total attenuation from `0` to `-47.25 dB` and five-bit per-voice attenuation from `0` to `-23.25 dB`, both in `0.75 dB` steps. Composer-facing loudness may use a friendlier direction, but its lowering to these domains must be explicit and tested.
- The event model must express key-on, continuation, and dump independently. The board manual's quarter-note/12 K/C/D pattern model is a useful semantic fixture, not syntax to copy or a file format to interpret.
- Procedural synthesis only; no ROM/sample data.
- Immutable SSG-effect programs described by duration, tone/noise periods, mixer, volume/envelope selection, and tone/noise sweeps, not by copied packed binary.
- Pattern selection, repeats, alternate endings, simultaneous shots, and deterministic event ordering.

### Output-routing law

- FM and rhythm routing is two independent enable bits: left, right, both, or neither. It is not a continuous pan control.
- SSG has no per-channel L/R selection in this hardware profile and is treated as centered.
- The product mono path must deterministically fold the enabled left and right buses together with documented gain/headroom. It must not discard one bus or erase routing semantics before the fold-down.
- Stereo rendering remains a conformance/offline surface unless product listening explicitly adopts stereo output.

### TimeBox extensions

Extensions must be visibly separate from the PMD-parity profile and must justify their maintenance cost.

- Song EQ and mastering are product features, not PMD semantics.
- General software FM polyphony/chord pooling is not YM2608 hardware behavior. Rin is the only admitted product source using `P1`, and it is to be removed rather than used to justify pooled voices. After that removal, rescan the admitted product set. If no retained composition exceeds six simultaneous FM voices, remove the extension; if a future original composition genuinely requires it, reintroduce it only as an explicit non-PMD owner with separate tests.
- Pseudo-echo and ornaments may exist as compiler conveniences only; lower them to ordinary typed notes and controls.

## 8. Corpus-required behavior

The 23 unique LLS M86 payloads actually exercise:

- counted loops and loop exits;
- part-loop boundaries;
- instrument changes;
- absolute, relative, step, and fine volume changes;
- two observed tempo forms;
- transpose and master transpose;
- Q gates and ties;
- detune and portamento;
- SSG software envelopes;
- LFO1/LFO2 clock-mode setup;
- software-LFO definition/wave/switch in `LOGO.M86`, `ST03.M86`, and `STAFF.M86`;
- bar-length changes;
- K/R pattern selection and rhythm pattern data.

The corpus currently shows zero M86 occurrences for raw-register writes, extended gate, FM3/live-FM controls, hardware LFO, pan, dedicated SSG-effect opcodes, and YM2608 rhythm-control opcodes. Zero occurrence means “not required by these 23 payloads,” not “safe to delete from the hardware-shaped TimeBox language.” Existing typed implementations should be retained if they remain single-path and maintainable.

Before claiming all LLS songs are supported, resolve the observed but semantically unidentified `C0/F9`, `C0/FD`, `C0/FF`, `CD`, `ED`, and `EE` command forms. Each must be classified as:

1. musical behavior implemented as a typed TimeBox semantic;
2. metadata/control with no audible state effect, documented with evidence; or
3. unsupported behavior that keeps the relevant song outside the parity claim.

Unknown commands may not be silently ignored.

Resolve the `C0` family first: `C0/F9` and `C0/FF` occur in 44 of the 45 unique M86/M26 payloads, while `C0/FD` occurs in 43 and affects every non-LOGO M86 song. `CD` is confined to ST05/ST06; `ED` and `EE` are confined to ST06. Occurrence and operand width do not establish meaning: each classification needs manual evidence, state-before/state-after observations from more than one context where possible, and an independent semantic check. ST06 part G is the concentrated rare-command fixture once the ubiquitous controls are understood.

## 9. Implementation phases

## Phase 1 — Remove duplicate product architecture without removing parity

This is the first implementation phase. Do not add new PMD features until it passes.

### 1.1 Freeze the current behavioral baseline

Before deleting paths, record:

- Bad Apple tick-domain program summaries;
- ordered sample-boundary summaries at 48 kHz;
- raw-core mono hashes across multiple chunk sizes;
- current product-output hashes;
- loop-reset hashes;
- patch/register snapshots;
- current mastering/profile/oversampling settings;
- a short human listening record for named excerpts.

Regenerate these baselines from the current code instead of copying counts from older plans. Current tests assert `3896` total `CompiledOpnaSong` events, `3864` musical events (`2037` FM, `1422` SSG, `405` rhythm), and `7356` lowered timeline boundaries. Freeze three separately named artifacts: tick-program semantics, the 48 kHz ordered playback plan, and raw/profiled/product audio hashes. Also record the effective policy currently assembled from `MmlArrangementScheduler.MIX_GAIN`, the 48 kHz/1,024-frame Android setup, FM oversampling, output filtering, song EQ, output profile, mixer headroom/gains, master gain, and resonator state.

Hashes are migration alarms, not permanent bit-accuracy requirements. They prove that cleanup did not accidentally change behavior. Intentional sound improvements may establish a new baseline only after a documented listening decision.

### 1.2 Port tests off `OpnaSequencer`

- Identify what each sequencer test actually protects: FM algorithms, polyphony, sub-chunk ordering, SSG shared state, reset, or allocation.
- Re-express valuable cases as tiny compiled TimeBox MML programs or direct typed `OpnaSongProgram` fixtures.
- Replace the weak legacy heap-delta test with the product player's allocation measurement.
- Remove redundant tests that only protect obsolete helper behavior.

The migration must account for the active legacy coverage rather than deleting it by filename: `OpnaSubChunkSchedulingTest` protects mid-buffer boundaries; `OpnaPolyphonyTest` protects ownership/retrigger/stale-off behavior; `OpnaDeterminismTest` and `OpnaAcceptanceChecklistTest` protect repeatability and dense rendering; sequencer cases in `OpnaFmCoreTest` protect release/gate/SSG behavior. Retain `SsgHardwareConformanceTest` for shared-register semantics. Use tiny `#MML 2` programs for normal musical cases and a test-only typed program builder only for deliberate overlaps or same-sample boundaries that authored sequential MML cannot express.

Why: deleting the old scheduler first would discard useful evidence; retaining it indefinitely forces every synth change through two dispatchers.

### 1.3 Delete the legacy scheduling/render path

After the ported tests pass:

- remove `OpnaSequencer`, `SequencerEvent`, and sequencer render overloads;
- remove legacy dispatch from `OpnaLikeSynthesizer`;
- remove `OpnaPatterns`, `Scale`, unused `NoteLen`, and sequencer-only constants;
- make `CompiledOpnaPlayer`/its successor the only product and test scheduler.

The deletion includes `OpnaLikeSynthesizer`'s sequencer render overloads, `renderWithSequencer`, `renderStereoWithSequencer`, and `handleSequencerEvent`, plus sequencer-only carrier ADSR overrides. There is no product caller. The surviving API should be session-owned rendering, not a public combination where callers coordinate player and synthesizer independently.

Gate: repository search must find no second scheduler, cursor, note-on/off expansion, or legacy drum dispatch.

### 1.4 Collapse the FM patch/envelope model

- Evaluate the current float-ADSR-to-register conversion once for every surviving legacy patch.
- Store the resulting legal OPN AR/DR/SR/SL/RR/KS values explicitly.
- Compare patch register snapshots and rendered baselines.
- Remove `Envelope`, `EgMode.LEGACY_ADSR`, float ADSR fields, and `OpnEnvelopeCompatibility`.
- Keep `OpnRateEnvelope` as the only FM envelope implementation.

Perform this in dependency order: snapshot every surviving converted patch; write its legal OPN fields explicitly; port/remove direct and sequencer APIs that accept ADSR overrides; make `OperatorSpec` register-only; then remove `EgMode`, `OpnEnvelopeCompatibility`, its warmups, and `Envelope`. `EnvelopeStageTest` becomes obsolete; any valuable legacy case in `OpnaFmCoreTest` becomes an explicit register snapshot/conformance case.

Why: one operator currently carries two competing truths. Register-native patches are smaller, auditable, and match the clean YM2608-shaped core.

### 1.5 Collapse MML dialects

- Convert every owned headerless-v1 source/test to stable TimeBox MML.
- Remove the unused Bad Apple migration fixture.
- Remove v1-only parser gates and the v1 compiler backend.
- Keep one parser normalization pipeline and one semantic compiler.
- Do not add a new dialect branch to preserve private test strings.

Do not mechanically prepend `#MML 2`: v1 dynamically assigns physical FM/SSG families, whereas v2 fixes A-F as FM and G-I as SSG. Rewrite each retained fixture by meaning, remove tests that protect only v1 assignment/rejection behavior, then remove headerless/`#MML 1` acceptance from `MmlParser`, `compileV1` and the compiler branch, and finally `dialectVersion` from document/program models.

Gate: every product and retained test song compiles through one backend; no `dialectVersion` branch reaches semantic compilation.

### 1.6 Separate product songs from research fixtures

- Keep only catalog songs in the production song bank.
- Move LOGO and its local patch bank to research/test scope until explicitly admitted.
- Compile/cache product songs once outside the audio callback.
- Preserve the old `synth-mml-senbonzakura-demo` value as a persistence alias while exposing a correctly named Bad Apple ID.

Remove `RinToShiteSong.kt`, its `RIN_TO_SHITE_*` definitions, catalog entry, and song test; update catalog admission tests accordingly. Retain a retired/fallback persisted Rin ID only if storage compatibility actually requires it, without compiling the song. Move `LogoSong.kt`, `LogoSongPatchBank`, the compiled LOGO result/key, and its ingestion fixture out of the product bank. The Bad Apple legacy ID becomes an alias resolving to one canonical entry, never a duplicate entry.

Why: hidden eager compilation makes test material part of product startup and obscures which songs define product requirements.

### 1.7 Replace the parallel event schemas

Keep tick-domain compilation and sample-domain lowering, but change their boundary:

- Store each semantic payload once in a named primitive family table.
- Make the sample-domain plan contain only sample time, canonical order, boundary kind, and payload-family index.
- Represent note-on and note-off boundaries as references to one note payload.
- Remove the duplicate event constant tables and broad payload-copy operation.
- Stop storing FM/rhythm/LFO values in arrays named after envelope stages.
- Keep source order and source location in compile/debug metadata without copying irrelevant fields into every render boundary.

Preserve `PmdSampleClock.samplesAt`, the current `TimelineDraft.comesBefore` guarantees, source order, note identity/stale-off suppression, and the current global -> state -> off/dump -> on/shot -> zero-gate precedence until the Phase 1 baseline proves an intentional replacement. Migrate payload families incrementally: tempo/notes, shared and part state, modulation/FM/SSG controls, then rhythm/effects. “Immutable program” also means callers cannot mutate exposed primitive arrays; replace tests such as `HardwareLfoRuntimeTest` mutating `startTick` with typed fixture construction.

Before changing same-boundary order, specify and baseline a TimeBox policy such as `(sampleTime, phase, sourceOrder)` with old-note termination/dump, effect release/projected restore, atomic typed controls/full-patch change, effect acquisition, then note-on/shot. A tie emits neither termination nor note-on. Same-phase shared writes preserve source order; conflicting writes to the same shared scalar are diagnosed with both source locations, and exclusive-owner collisions are compile errors. This ordering is proposed TimeBox policy, not a PMD-manual fact; any audible baseline change requires an explicit migration and listening decision.

Unsupported PPS/P86/PCM/PPZ/file directives and raw-register commands are compile errors, never ignored compatibility spelling. Unknown corpus commands remain admission failures. Source locations and diagnostic metadata are built offline; the render path neither allocates nor formats diagnostics.

Gate: adding a typed control requires one payload definition, one lowering rule, and one runtime handler—not synchronized edits to two property bags.

### 1.8 Consolidate session and render configuration

Create one immutable common-code render profile containing:

- sample rate;
- maximum render chunk;
- FM oversampling policy;
- output profile;
- mix gain/headroom;
- output filter state;
- resonator state;
- song EQ and user gain boundary.

Create one product setup API returning a fully configured `OpnaPlaybackSession`. Remove the unused synth parameter from the player factory and stop Android from mutating all FM voices.

Apply user volume at the final session/mastering boundary rather than copying the entire compiled-song wrapper and rebuilding a timeline for every volume.

Android should retain only thread lifecycle, `AudioTrack`, buffers, PCM16 conversion, wake lock, and blocking writes.

The product-facing shape should be one setup call followed by `session.render(out, frames)` and `session.reset()`. The session owns the sequential cursor; product callers do not pass `currentSampleOffset`. Move arbitrary interval replay and the seek buffers currently used by `CompiledOpnaPlayer.prepareInterval` into a clearly named offline/test renderer that creates an independent session. Preserve raw-core and profiled-pre-master checkpoints while applying caller volume only at the final mastering boundary.

### 1.9 Reduce percussion to explicit domains

- Route ordinary TimeBox/YM rhythm notes and typed rhythm controls through one `Ym2608RhythmUnit` domain.
- Reproduce current legacy-drum defaults explicitly so migration does not change Bad Apple accidentally.
- Keep PMD SSG effects independent because they have different ownership, priority, and reset behavior.
- Remove `legacyDrums` after no event targets it.
- Put both remaining domains behind an explicit `PercussionRouter` and active mask.

Today the three domains are `OpnaChipState.legacyDrums`, `Ym2608RhythmUnit`, and `PmdSsgEffectUnit` wrapping another generic `ProceduralDrums` approximation. First redirect ordinary product rhythm shots to `Ym2608RhythmUnit` while reproducing Bad Apple's current default gain/pan behavior. Delete `legacyDrums` only after every direct, compiled, and old-sequencer target is gone. Replacing the SSG-effect ordinal-to-drum approximation must remove that approximation, not add a fourth generator.

Why: two semantically real domains are maintainable; three anonymous generators rendered on every segment are not.

### 1.10 Decide the software-polyphony extension

- Scan product songs for actual simultaneous notes that exceed six FM physical voices.
- If no admitted song requires pooled voices, remove `FM_RENDER_VOICES = 16`, `P1`, and chord pooling.
- If a real TimeBox composition requires it, isolate it as a named non-PMD extension with its own voice owner and ensure PMD parts still map one-to-one to hardware voices.

After Rin is removed, delete the pool if the admitted set has no remaining need: `FM_RENDER_VOICES = 16`, `FM_POLY_NOTE`, pooled owner arrays, `P1`/chord-pooling compiler and runtime branches, and pool-only tests. Retain six-part and FM3 ownership coverage. A future software-polyphony feature would require a new explicit product decision; do not keep dormant hot-loop complexity as speculation.

Default decision: prefer the fixed six-FM hardware profile. Hidden polyphony increases every hot-loop and complicates PMD part/LFO ownership.

### Phase 1 exit gate

- Exactly one MML semantic compiler backend.
- Exactly one playback scheduler and dispatcher.
- Exactly one FM envelope model.
- Exactly one product song bank.
- Exactly one render/session configuration owner.
- Two named percussion domains at most: YM rhythm and PMD SSG effect.
- No product-loaded research fixtures.
- No semantically overloaded event payload fields.
- Bad Apple preserves the agreed musical behavior and loop/reset behavior.
- Playback remains allocation-free after warm-up.

## Phase 2 — Prove and fill LLS musical parity

### 2.1 Upgrade the corpus verifier

Extend the offline normalized trace to compare, per source part and ordered clock:

- tempo and bar length;
- note start, duration, gate end, pitch, tie/retrigger state;
- patch and all operator register fields;
- absolute/relative volume;
- transpose/master transpose and detune;
- portamento target/duration;
- SSG envelope definition/mode;
- software-LFO definition, wave, switch, clock, targets, masks, and depth evolution;
- rhythm pattern selection and ordered/simultaneous shots;
- part-loop and song-loop boundaries;
- shared SSG and other typed state changes.

Do not compare PMD binary layout or raw addresses. Compare clean semantic traces.

Implement the verifier as two explicitly separate products:

1. A structural inventory may report archive hashes, offsets, operand bytes, scan widths, and occurrence counts for local diagnosis. It proves only that the scanner can walk the supplied material.
2. A lossless semantic trace contains one ordered event stream across all pitched and control-only parts. Each row carries semantic clock, canonical same-clock order, part/owner, event family, typed payload, and source provenance sufficient for a diagnostic—not raw driver layout.

The semantic trace must emit every state transition, including relative volume and tempo forms, loop/alternate/part-loop boundaries, bar length, shared controls, and K/R pattern expansion. Notes must carry onset, written duration, computed gate/key-off clock, key-on/retrigger/tie/slur state, patch, effective volume, transpose, detune, and portamento target/duration. Merge per-part traces deterministically to expose shared tempo, SSG mixer/noise/envelope, hardware LFO, FM3, and rhythm ownership. Remove or replace the lossy compact repeated-note oracle; exact comparison hashes canonical lossless semantic rows.

Fail ambiguity closed. A repeated entry name requires archive/hash disambiguation rather than silently choosing the first payload. Strict parity runs must explicitly require candidate-specific independent checkpoints; `--strict` alone may not succeed merely because the global checkpoint set remains `0/4`. Checkpoints require a versioned clock/schema, derivation identity, typed/range-checked pre/post state, strict order, covered capability keys, and semantic diversity. A useful diverse set is ST02 part B for gate/tie/portamento/detune/loop, ST03 part A for active FM software LFO, ST06 part G for SSG envelope and rare controls, and STAFF part K for rhythm.

Keep full traces and decoded song oracles ephemeral. Check in only hashes and aggregate coverage, small independently authored synthetic fixtures, minimal non-expressive checkpoints, owned TimeBox program baselines, and capability evidence/results. Never check in full decoded note, patch, or rhythm-pattern reconstructions.

### 2.2 Resolve unknown corpus commands

Triangulate the six unresolved command families using the manual, multiple songs, normalized before/after state, and independent observations. Record a short clean-room evidence note for every conclusion. Classify ubiquitous `C0/F9`, `C0/FD`, and `C0/FF` before broad song admission, then use ST05/ST06 for `CD` and ST06 for `ED`/`EE`.

Do not infer semantics from a single byte width or handler label.

### 2.3 Close gaps in corpus priority order

Implement only through the canonical typed path:

1. timing, gates, ties, transpose, detune, portamento, volume;
2. SSG software-envelope behavior;
3. K/R rhythm pattern behavior;
4. LFO behavior for LOGO/ST03/STAFF;
5. bar/loop/part-loop semantics;
6. resolved special controls.

Every added behavior must include a corpus-derived semantic fixture, a small independent unit fixture, reset/replay coverage, and a listening checkpoint where audible.

### 2.4 Implement the real procedural SSG-effect model

Replace generic drum-kind substitution for PMD SSG effects with a compact, immutable procedural effect program derived from the behavior described in `PSGEDATA.DOC`. A named effect step contains duration in effect-control ticks, tone and noise periods, tone/noise enables, amplitude or hardware-envelope selection, hardware-envelope period/shape, signed tone-period delta per step tick, and signed noise-period delta plus its update divider.

Run it with a preallocated cursor and rational/fixed-point approximately 60 Hz effect-clock accumulator independent of callback chunk size. Do not reproduce the historical packed record, terminator, offsets, or nibbles. Higher numeric priority wins and lower priority is ignored without disturbing the active effect; documented SSG drum effects use priority zero. Equal-priority replacement and a zero noise-sweep divider are not fully specified by the references, so choose, name, and test TimeBox policies rather than presenting guesses as PMD parity.

Add one arbiter for SSG channel 3 and shared noise:

- simultaneous effect key-on wins according to the documented priority;
- effect ownership is released and the current desired music state is reprojected explicitly;
- deferred shared-noise changes are applied deterministically;
- reset and song loop cannot leave the effect owner latched.

Music desired state continues while the effect owns physical output. Channel-3 music and an effect trigger at the same boundary resolve with the effect physically winning, while the music event still advances desired state. On release, project current music state and retain the documented pending-noise-until-next-key-on behavior.

This is procedural behavior, not reproduction of PSGEDATA's packed format.

### 2.5 Retain hardware-shaped typed features

FM3, hardware LFO, pan flags, and detailed rhythm controls are not exercised by the scanned LLS M86 corpus, but they are legitimate YM2608/PMD behaviors already represented in the engine. Retain them if they pass the single-path architecture test. Remove only compatibility spelling, duplicate state, or raw machinery—not the typed capability.

Add small independent conformance fixtures for the eight hardware-LFO rates, slot-local AM versus channel-local AMS, all PMS/AMS selector depths, patch-parameter direction, both rhythm attenuation domains, set-bit/clear-bit rhythm event behavior, L/R/both/neither routing, SSG centering, and mono fold-down. These fixtures validate typed semantics and generated numeric tables; they do not replay register streams or rhythm-ROM data.

### Phase 2 exit gate

- Every command occurring in all 23 unique LLS M86 songs is semantically classified.
- All 23 songs are assessed individually against the candidate-specific capability and checkpoint gate; no aggregate scan substitutes for per-song admission.
- No exercised command is silently skipped.
- The semantic verifier covers more than note tuples and passes all admitted songs.
- Bad Apple's gate, volume, envelope, detune, tie, rhythm, and shared-state behavior are verified, not merely pitch/duration/patch.
- LOGO/ST03/STAFF LFO semantics are verified independently.
- K/R rhythm behavior is represented procedurally.
- A human listening record covers representative FM, SSG, LFO, rhythm, and reset/loop passages.

## Phase 3 — Efficiency and deterministic lifecycle

Only optimize after Phase 1 baselines and Phase 2 semantics exist.

- Build active FM, SSG, logical-part/LFO, and percussion masks from the immutable program.
- Do not prepare or render software LFOs or parts that immutable program analysis proves are never referenced.
- Do not render inactive percussion domains.
- Render only the required six FM voices in the hardware profile.
- Cache the 48 kHz playback plan for catalog songs; create only mutable session state per playback.
- Keep one sequential product render API. Put arbitrary seek/replay simulation in a clearly named offline/test API using an independent session.
- Warm all LUTs and tables before audio starts.
- Keep exact-size primitive arrays and caller-owned output buffers.
- Benchmark representative dense sections, not silence.

Capability masks are immutable facts about what the program can use, not merely what is audible in the current segment. Do not skip a temporarily disabled free-running LFO if later enabling depends on its elapsed clock history. Eliminate state only when the program never references it or when an explicit control transition defines the dormant behavior.

Cache one immutable 48 kHz playback plan per catalog program, independent of user volume; create only session/chip/player state per playback. Warm `OpnLogTables`, `DrumSinLut`, and every remaining generated table before the audio thread starts. Benchmark a named dense Bad Apple interval against the 21.33 ms deadline for 1,024 frames at 48 kHz.

Performance gates:

- zero steady-state callback allocations after warm-up;
- deterministic output across chunk sizes and resets;
- no parser, sort, timeline build, patch conversion, or collection work on the audio thread;
- measured callback time comfortably below the 1,024-frame deadline at 48 kHz on the target Android device;
- no regression hidden by increasing `AudioTrack` buffering.

## Phase 4 — Sound quality and composer-ready workflow

### 4.1 Separate core correctness from mastering

- Diagnose pitch, algorithm, operator routing, envelope, patch, modulation, and mix-bus issues before EQ.
- Keep raw-core and profiled-pre-master render checkpoints.
- Keep product mono intentional and implement it as a documented L+R fold-down with fixed headroom.
- Retain stereo rendering only for hardware L/R semantics and offline use unless listening explicitly chooses stereo product output.
- Do not use the optional resonator to conceal arrangement or timbre errors.
- Version any intentional output-profile change and record why it sounds better.

The current mono renderer is not yet a true fold of the stereo buses: FM treats any enabled side as full mono, SSG renders directly to mono, and procedural drums do not preserve all stereo routing in the mono path. Therefore L+R fold-down is an intentional migration, not cleanup-only refactoring. Define its equation and headroom, test FM left/right/both/neither, centered SSG, rhythm routing, and stereo-to-mono equivalence, then establish a new product baseline only after listening approval. Preserve the existing raw-core, profiled-pre-master, and stereo verification surfaces through the session refactor.

### 4.2 Listening protocol

For each admitted song, record dated observations for:

- FM lead attack and high-frequency bite;
- bass definition and low-mid masking;
- SSG/FM balance;
- rhythm transient level and clipping;
- midrange continuity;
- modulation stability;
- loop/reset clicks or changed timbre;
- mono phone speaker and headphones.

Automated spectra and hashes locate changes; they do not approve music.

### 4.3 Composer workflow

Adding a normal song should require only:

1. one TimeBox MML source constant/file represented under the procedural asset law;
2. named register-native patches, preferably song-local when recovered or unique;
3. catalog metadata and persistence aliases if needed;
4. compile diagnostics, semantic summary, and a listening record.

It should not require editing the parser, event schema, synthesizer, Android player, or mixer.

Create a compact language reference covering every stable command, legal ranges, state ownership, examples, and whether each feature is PMD-shaped or a TimeBox extension.

### Song admission must be two-tiered

- A new original TimeBox song may be admitted when it compiles, stays within the stable language, renders safely, and passes listening. It does not need four historical PMD traces.
- A song advertised as an LLS/PMD reconstruction needs the stronger corpus semantic evidence for the behavior it claims.

This replaces the current situation where an empty `0/4` independent-trace research gate can block ordinary composing while still failing to prove Bad Apple's full musical semantics.

## 10. Failure-history checks that must become permanent

`PMDWin.txt` is valuable because it records recurring failure classes. Convert the relevant history into named typed-semantic fixtures first and audio/hash checks second:

| Fixture | Required assertion |
|---|---|
| LFO1/LFO2 symmetry | Identical definitions produce identical traces; LFO2 delay and slot masks work |
| Fixed software clock | Tempo changes do not change extended-clock envelope/LFO step counts |
| Signed modulation | Negative depth, FM volume-LFO direction, detune, TL changes, and depth evolution retain sign and use explicit bounds |
| Relative tempo/detune | Relative tempo mutates shared tempo once in the right direction; extended SSG detune is not doubled |
| Gate/tie/slur/random Q | Tie has one attack and no middle key-off; slur retriggers; seeded Q replays exactly and respects range/minimum |
| Atomic patch switch | Operator fields plus ALG/FB change as one typed control before key-on |
| Hardware-LFO disable | Chip-wide disable removes modulation without stale per-channel state becoming audible |
| Shared-noise replay | Fresh and reset sessions start from identical noise state and hashes |
| Effect arbitration | Channel 3 is masked, desired music continues, current state returns, and pending noise applies at the documented key-on point |
| Effect priority | Higher wins, lower is ignored, and the chosen equal-priority TimeBox rule is stable |
| FM3 ownership | Slot masks/detunes/delays, LFO/TL masks, shared ALG, and slot-1 FB remain correctly owned |
| Rhythm boundary order | Same-boundary dump/shot follows the declared TimeBox order |
| Cross-rate semantics | Supported sample rates produce the same semantic trace; only sample boundaries/audio differ |
| Chunk invariance | Same-rate output is identical across callback partitions |
| Session isolation | Interleaved sessions equal separate rendering; resetting one cannot change the other |
| Fresh-session reset | Reset/replay equals construction from the same program/profile and leaves no old voice, filter, rhythm, or effect tail |

Do not import PMDWin's P86/PPS/PPZ/ADPCM file, interpolation, malformed-binary, DLL ABI, WAV, or corresponding memory-management tests. Those subsystems are intentionally absent.

## 11. Completion definition

The sound architecture is stable and composer-ready only when all of the following are true:

- The Phase 1 single-path exit gate passes.
- The six-FM/three-SSG/six-rhythm hardware profile is explicit.
- Product playback is allocation-free and platform-independent in `commonMain`.
- Android is a dumb mono PCM terminal.
- All 23 unique LLS M86 programs have no unexplained effect-bearing command.
- The semantic corpus audit covers the actual behavior it claims.
- Bad Apple and representative LFO/rhythm songs pass semantic and listening checks.
- The SSG effect model is procedural and arbitrates shared state correctly.
- No runtime binary, driver, PCM/sample, or raw-register compatibility path exists.
- A new original song can be added without modifying engine architecture.
- The stable TimeBox MML language and its PMD-shaped versus TimeBox-extension boundaries are documented.

At that point, further work should primarily be composing songs, adding register-native patches, and making listening-driven sound improvements—not adding another scheduler, dialect, event model, or compatibility layer.
