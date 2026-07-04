# TimeBox Audio Engine and Song Audit

Date: 2026-07-04  
Scope: current working tree, with no production-code changes  
Primary subjects: the custom OPNA/YM2608-like engine, the mislabeled v1 Bad Apple!! / Lotus Land Story target, MML v2, and `凛として咲く花の如く`

## Executive verdict

The user's negative assessment is substantially correct.

`SENBONZAKURA_DEMO_MML` is misnamed. Repository provenance identifies it as the Bad Apple!! / Lotus Land Story target: it is in E-flat minor at the repository's exact Bad Apple tempo of 160.73 BPM, uses the four `LlsPatches`, and was introduced alongside the LLS material; the separate legacy Senbonzakura source is different D-minor material. This v1 MML is therefore the engine's primary real-world benchmark, not merely a “working Senbonzakura arrangement.” It currently sounds thin, is dominated by dry FM voices, gives its essential SSG layer very little effective level, and has its intended patch envelopes partially replaced by scheduler policy. If this target cannot play properly, the production engine contract is not healthy.

The current `凛として咲く花の如く` implementation is a failed production arrangement, but it is secondary evidence rather than the first repair target. It is a ten-bar, hand-authored extract described as measures 67–76; it has no in-repository score or human-auditable note map proving the transcription, no rhythm part, no SSG part, no dedicated low bass, no LFO, three synchronized instances of one invented “strings” patch, one invented “piano” patch, and panning that the Android playback path discards by rendering mono. Its failure combines weak authorship with the split backend; it should not be used to tune advanced FM features before the Bad Apple/LLS benchmark works through the unified path.

MML v2 is mechanically implemented but not production-proven. Its parser, compiler, primitive event program, scheduler, software voice pool, and several expressive commands work in isolation. Its first production song demonstrates that feature completion was mistaken for musical validation.

The custom audio core is correctly scoped as a **clean-room, OPNA-inspired deterministic software synthesizer**. It is not intended to be a register-level or bit-accurate emulator. Its six-channel/four-operator FM topology, FNUM/block pitch, eight algorithms, integer attenuation envelope, LFO, CH3-style operator control, three SSG voices, and six original procedural drum voices are the intended product. The absence of ADPCM sample playback, rhythm-ROM samples, raw registers, timers, CSM, status/bus behavior, and analog-output emulation is deliberate and is **not** an engine defect or missing-work list. The procedural drums are the correct solution under the zero-asset law.

The central architectural failure is fragmentation—not a lack of advanced timbres or chip peripherals. MML v2 was supposed to upgrade the existing MML system, but the implementation instead branches after a shared parser into a second compiled representation and a second scheduler. The Bad Apple/LLS benchmark was stranded on `ToneSpec`/`Lane`, legacy SSG, and scheduler envelope overrides, while Rin exercised `CompiledOpnaSong`, the newer SSG rules, polyphony, and expression. The same MML-facing engine now has different world laws depending on the song.

The immediate priority is therefore: make `CompiledOpnaSong` the internal representation for **all** MML, migrate the current Bad Apple/LLS v1 text unchanged only as a backend regression fixture, use one scheduler, stereo production, one SSG, and one FM envelope truth, and then replace the shortened fixture with a faithful full target structure. Rin and further specialist features come afterward.

## Correct project contract

This refined audit uses the following requirements as hard constraints:

1. `SongCatalog` is MML-only. Audio-file playback is not a present or future target.
2. All sound is generated procedurally. WAV/MP3/OGG files, copied rhythm-ROM data, sample playback, black-box PCM baselines, and embedded audio blobs are forbidden.
3. The synthesis core is an independently derived clean-room design. Documentation and mathematical understanding may inform it; copied emulator code, tables, ROM data, or opaque captures must not become implementation data.
4. YM2608/OPNA is a musical and mathematical design inspiration, not a requirement for register compatibility or complete peripheral emulation.
5. Procedural kick, snare, hi-hat, tom, cymbal, and rimshot synthesis is intentional and correct for this project.
6. Raw registers, status ports, bus waits, timers, CSM, ADPCM file playback, and binary music formats are out of scope unless the project direction is explicitly changed later.
7. MML v2 was intended to be an upgrade to the existing MML implementation. New syntax may require new internal fields, but old and new syntax should converge on one compiled event contract, one timing law, one scheduler, and one audible engine path.

## Confidence labels

- **Proven** means the behavior follows directly from the complete current source path or a current test/diagnostic run.
- **Strong inference** means the code and measurements make the explanation likely, but a human listening decision or explicit musical specification is still required.
- **Not verifiable here** means the repository lacks a human-auditable score/note map or named musical target required for a defensible conclusion. This does not imply that an audio asset or hardware capture should be added.

## Audit method and boundaries

The complete contents of the following relevant production areas were read line by line before repository searches were used:

- `SongCatalog.kt`, `ToneSpec.kt`, and all 890 lines of `SoundMelodies.kt`
- all files under `shared-engine/.../audio/mml`
- all files under `shared-engine/.../audio/opna`
- `SoundPreviewPlayer.kt`
- the audio call paths in `MainScreenViewModel.kt` and `FocusService.kt`
- all current MML and OPNA tests in `commonTest` and `androidUnitTest`
- the OPNA/hot-path audit scripts and `shared-engine/build.gradle.kts`

The translated [YM2608 OPNA Application Manual](https://manualmachine.com/yamaha/ym2608/5410883-user-manual/) was consulted only as mathematical context for the OPN-style features the project intentionally implements, such as FNUM/block pitch and envelope fields. It is not used as a checklist requiring ADPCM, ROM rhythm samples, registers, timers, or peripheral compatibility.

No direct listening session was available. No human-auditable note-event transcription for `凛として咲く花の如く` exists in the repository. Therefore this audit can prove that the implementation lacks musical validation and identify concrete internal mismatches, but it cannot enumerate every incorrect authored pitch against the intended melody. The remedy is a reviewable MML/note specification and human listening—not checked-in reference audio.

## Authoritative production call path

### Selection and preview

1. `SongCatalog.kt:23-38` exposes exactly two songs.
2. `MainScreenViewModel.kt:396-398` calls `SoundPreviewPlayer.playPreview`.
3. `SoundPreviewPlayer.kt:33-44` asks the catalog factory to build an `ArrangementLanes` playback object.
4. `MmlSongBank.kt:125-126` has already compiled both embedded songs during object initialization.
5. `SoundPreviewPlayer.kt:152-188` creates one `OpnaLikeSynthesizer`, one `OpnaSequencer`, enables the output filter and 2x FM oversampling, schedules all events, and sorts them.
6. `SoundPreviewPlayer.kt:206-225` creates a **mono**, 48 kHz, signed 16-bit `AudioTrack` using alarm usage.
7. `SoundPreviewPlayer.kt:252-311` renders 1024-frame float blocks, converts them to shorts, and writes them synchronously.

### Alarm and reminder

- `FocusService.kt:435-456` selects the focus/relax song and calls looping `playAlarm`.
- `FocusService.kt:493-511` calls `playGentleReminder`.
- `SoundPreviewPlayer.kt:134-149` routes both through the identical arrangement/synth path.

There is no alternative production renderer, media-player branch, or sample path. The Android mono stream is the audible contract today.

## Detailed findings

The actual repair priority is:

1. unify v1/v2 behind `CompiledOpnaSong` and one scheduler;
2. use the Bad Apple/LLS v1 path as the engine benchmark, while recognizing its current MML is only a shortened migration fixture;
3. unify SSG and FM envelope truth so the benchmark reaches the intended synth;
4. enable the existing stereo capability in Android production;
5. reconstruct and validate the full Bad Apple/LLS target;
6. rebuild Rin only after those world laws are stable.

### P0 — The v1 production benchmark is Bad Apple!! / Lotus Land Story, but its current MML is mislabeled and structurally incomplete

**Status: Identity proven; transcription incompleteness proven.**

The supplied original-target specification is:

- tempo: 160.73 BPM (`Timer B = 202`);
- meter: 4/4;
- duration: 1:22;
- key plan: E-flat minor from about 0:04, changing once to G minor at about 0:58;
- instruments: square wave, `@54`, `@74`, `@99`, `@181`, and drums;
- form: an E-flat-minor intro, A and B sections, then a G-minor chorus.

The supplied harmonic map should be retained as the human-auditable reconstruction target:

| Section | Key | Progression |
|---|---|---|
| Intro | E-flat minor | `N.C.` ×2, `E♭m` ×4, then `E♭m | E♭m | C♭ | C♭ B♭m/D` ×2 |
| A | E-flat minor | `E♭m | E♭m | C♭ | D♭ Ddim | E♭m | E♭m - - D♭ | C♭ | D♭ Ddim` |
| B | E-flat minor | (`C♭ | D♭ | E♭m | 〃` then `C♭ | D♭ | E♭m | E♭m - - D♭`) ×2, then `C♭ | D♭ | Ddim | 〃` |
| Chorus | G minor | `E♭ F | Gm - - F` ×7, then `E♭ F | Gm` |

The production constant named `SENBONZAKURA_DEMO_MML` matches that identity in every distinctive setup parameter: `MmlSongBank.kt:13-14` uses 160.73 BPM and 4/4; lines 16, 34, 52, 70, 88, and 106 use exactly `@54`, `@74`, `@99`, square wave, `@181`, and drums; its pitch language begins in E-flat minor and later introduces G-minor-region natural notes. The four FM patches are defined in `LlsPatches`. Git history also shows that the MML appeared in the same change that reworked the repository's explicit Bad Apple!! / Lotus Land Story material. By contrast, the separate retired Senbonzakura builder in `SoundMelodies.kt:477-584` is different D-minor note material. `SongCatalog.kt:30` and `ENGINE_BRIEF.md:79` therefore carry the wrong musical name.

However, identity is not fidelity. Each current MML part contains one 16-bar block repeated twice. The result is 32 bars, or approximately `32 * 4 * 60 / 160.73 = 47.78` seconds—only about 58% of the 1:22 target. It has no four-second introduction. Its late natural-note material starts around 17.9 seconds, resets to the opening material at 23.9 seconds, and returns around 41.8 seconds; that is not the target's single E-flat-minor-to-G-minor transition around 0:58. The current MML is therefore a shortened, structurally incorrect reconstruction of the intended PC-98 target.

This changes the fault model:

- the original composition is not the problem;
- the current MML transcription is incomplete and misstructured;
- the engine path is independently broken for the target because it routes v1 through legacy SSG, buries that square-wave role, and overrides the LLS patches' carrier envelopes;
- a successful repair must correct both layers, but must stabilize the unified engine contract before judging a rewritten full transcription.

The persisted internal key may need to remain for settings compatibility, but future code work should correct the display title, comments, and documentation. This audit changes terminology immediately: “Bad Apple/LLS v1 target” means the production item currently stored under the Senbonzakura identifiers.

### P2 — The Rin arrangement was never validated as the song it claims to represent

**Status: Proven.**

`RinToShiteSong.kt:3-9` describes a “short PC-98 arrangement” using only the final A-major section, measures 67–76. Lines 10–54 contain only ten bars. This is an adaptation, not the complete song.

The repository contains:

- no original score;
- no note-event reference fixture;
- no assertion comparing the 271 compiled events with a source transcription;
- no recorded human listening acceptance.

`RinToShiteSongTest.kt:18-39` checks the version, duration, event count, selected headers, and absence of rhythm. `RinToShiteSongTest.kt:90-150` checks only five opening MIDI notes and broad early RMS. It does not validate the remaining pitches. Its “fundamental” window is not a pitch oracle: for the lead and 16th-note piano part, the 250 ms analysis window includes later notes after the first note has ended.

The assertion “score-derived” is therefore unsupported by repository evidence. The user's report that the notes differ from the intended song must be treated as the only available musical acceptance result, and it is a failure.

### P2 — Rin's production texture is intentionally sparse in exactly the regions the user hears as empty

**Status: Proven, with a distinction between preview and full-loop playback.**

The compiled program has 271 note events over about 17.65 seconds:

- lead A: 60 notes;
- sustained strings B: 17 notes;
- polyphonic piano D: 160 notes;
- sustained strings E: 17 notes;
- sustained strings F: 17 notes;
- drums: 0;
- SSG: 0.

The apparently high event count is dominated by the single 16th-note piano arpeggio. It does not imply a dense full-range arrangement.

Concrete omissions and valleys:

- `RinToShiteSong.kt:31-32` writes an explicit quarter-note rest in lead bars 7 and 8. At 136 BPM, each is about 441 ms.
- `RinToShiteSong.kt:36-39` explicitly says the drum track was removed.
- `RinToShiteSongTest.kt:33-38` locks in the absence of all rhythm events.
- Low-register material appears only inside the moving D-part piano arpeggio, which eventually reaches F#2 (MIDI 42, about 92.5 Hz). There is no dedicated bass channel sustaining or articulating the low end as an independent role.
- No G–I SSG part exists.
- No `#LFO` directive exists; `RinToShiteSongTest.kt:24` locks `lfoRate == -1`.
- The three harmony lines all use `@strings`; they differ mostly by pitch and volume rather than timbral role.

Important preview qualification: the catalog preview is only seven seconds (`SongCatalog.kt:27`), so it stops near the end of bar 4. The explicit rests in bars 7–8 cannot explain valleys heard during the normal seven-second preview. In the first four bars, all parts use `Q8`, and the compiled scheduler does not insert timing holes. Preview-time valleys must therefore come from the synthesized/mixed texture rather than authored rests.

### Diagnostic clarification — There is no sample-level scheduling gap in consecutive Rin v2 notes

**Status: Proven.**

This rules out one tempting but incorrect diagnosis.

- `RinToShiteSong.kt:24,38,42,49,52` selects `Q8` on every part.
- `MmlCompiler.kt:396-430` converts a note to absolute ticks and sets `gate = totalDuration * gateEighths / 8`; with Q8, gate equals duration.
- `MmlArrangementScheduler.kt:127-155` converts both start and end from the same absolute tick timeline.
- `OpnaSequencer.kt:112-148` places note-off at `start + duration`.
- `OpnaSequencer.kt:414-426` uses a stable insertion sort by sample time.
- Because each old OFF is appended before the following ON on the same track, equal-time boundaries process OFF then ON.
- `OpnaLikeSynthesizer.kt:313-351` handles all events at a sample boundary before rendering the next sample span.

There is no rendered silent sample between ordinary consecutive Q8 notes. Large perceived valleys are not caused by a compiler rounding hole.

This is also why changing gate math blindly would be dangerous: it would treat a mix/timbre/authorship problem as a timing bug.

### P1 — Stereo expression is implemented and tested but absent from production

**Status: Proven.**

Rin is authored around stereo separation:

- lead uses `p3` (center);
- B and E use `p2` (left);
- D and F use `p1` (right).

`MmlCompiler.kt:349-358` maps those PMD-style values to the engine's internal pan values. `OpnaLikeSynthesizer.kt:198-228` and `OpnaStereoTest.kt` implement/test stereo routing.

Production never calls it. `SoundPreviewPlayer.kt:206-223` opens a mono track, allocates mono buffers at lines 252–253, and calls mono `synth.render` at line 280. Repository search finds `renderStereo` only in engine code and tests.

Consequences:

- all authored pan commands are inaudible;
- the three similar string voices and piano are collapsed into one dry center sum;
- the arrangement loses separation that was evidently expected to create clarity and width;
- stereo tests give false confidence because they do not cover the Android playback contract.

The mono collapse is a strong contributor to the user's “notes sound wrong / no richness” report. It does not by itself create scheduler silence, but synchronized similar patches can produce a congested and periodically weak composite amplitude when collapsed.

### P2 — The “piano,” “strings,” and “brass” timbres are invented patches with no defined musical target

**Status: Proven.**

`OpnaPatchBank.kt:25-59` defines the three Rin patches directly in Kotlin. Creating them procedurally is correct, but no attack/body/release target, intended frequency role, or human listening result is documented for any of them.

Notable topology:

- `Pc98Brass` uses algorithm 4: two separate modulator-carrier stacks.
- `Pc98Piano` uses algorithm 5: one modulator feeds three carriers.
- `Pc98Strings` uses algorithm 7: all four operators are carriers, making it primarily an additive four-sine voice rather than a deeply modulated string timbre.

All three harmony voices reuse the same `Pc98Strings` object. That produces synchronized, spectrally similar layers instead of bass/mid/upper roles with complementary envelopes.

The focused test run reported that every opening part is nonzero, but nonzero is not resemblance. The test thresholds are only `RMS > 0.0005`. They do not evaluate transient shape, pitch salience, low/mid/high balance, note-to-note continuity, or a named musical role. Those measurements can all be computed transiently from procedural renders without storing PCM assets.

The core pitch path itself does not show a systematic tuning failure: `OpnPitch.kt:46-80` chooses a legal block/FNUM pair, and A4 resolves to block 4/FNUM 1038, matching the Yamaha manual example. This makes authored pitches and overtone-dominant timbres more likely explanations for “wrong notes” than a global oscillator-frequency bug. Exact pitch correctness of the arrangement remains unverified because the score is absent.

### P0 — MML v2 forked the existing MML pipeline instead of upgrading it

**Status: Proven.**

The parser itself is shared: `MmlParser.parse` produces one `MmlDocument` and records `dialectVersion`. The fork begins immediately afterward. `MmlCompiler.kt:40-41` dispatches version 2 to `compileV2`, while v1 continues through the old `Lane`/`ToneSpec` compilation. Version 2 then builds `CompiledOpnaSong`; version 1 never does. `MmlArrangementScheduler.kt:35-39` branches again on `compiledOpnaSong`, selecting a wholly different scheduling implementation.

This means v2 is not just additional syntax on the existing MML language. It is a second semantic backend hidden inside the same parser/compiler/scheduler class names:

| Concern | MML v1 / mislabeled Bad Apple target | MML v2 / Rin |
|---|---|---|
| Song representation | `List<ToneSpec>` inside `Lane` | primitive arrays in `CompiledOpnaSong` |
| Timeline | ticks → integer milliseconds → samples | ticks → absolute samples |
| Channel model | A–E flexibly become FM or SSG | A–F fixed FM, G–I fixed SSG |
| FM articulation | generic carrier ADSR overrides and 8 ms early key-off | patch-native OPN envelope fields, Q gate |
| SSG synthesis | float phase + PolyBLEP + float ADSR | integer OPN-inspired phase + shared noise/envelope |
| Polyphony | one physical voice per FM lane | 16-voice software pool for `P1`/chords |
| Volume scaling | copies all `ToneSpec` notes | wraps the primitive program with `playbackGain` |
| Pan/LFO/FM3 | unavailable | compiled, although pan is discarded in production |

As a result, the Bad Apple/LLS fixture producing sound does not validate MML v2. The two songs do not exercise the same timing, envelope, SSG, allocation, or expression contract.

The primitive fixed-capacity `CompiledOpnaSong` representation is not itself a mistake; it is appropriate for deterministic, allocation-free scheduling. The mistake was making only v2 lower into it while leaving v1 on the old `ToneSpec` backend. A genuine upgrade would parse both source versions, apply version-specific syntax/default validation, and then lower both into one internal event program consumed by one scheduler. During backend migration, the existing v1 text should remain byte-for-byte unchanged so engine differences are measurable; the separate full Bad Apple/LLS transcription rewrite follows only after that path is stable.

Why this fork was created cannot be proven from code alone, but the structure strongly suggests a risk-avoidance shortcut: preserve the only producing-sound v1 fixture by leaving its backend untouched, then add advanced commands and a hot-path-safe event program beside it. That reduced immediate migration risk, but it transferred the risk into permanent duplication. Every later fix now has to answer “v1 path, v2 path, or both?”, which is exactly how the requested upgrade became a parallel implementation.

`ENGINE_BRIEF.md:140` currently codifies that temporary split by instructing future work to keep the mislabeled benchmark on v1. That instruction is now stale relative to the clarified requirement: the explicit next task is the deliberate migration it postponed.

### P0 — The Bad Apple/LLS target's patch envelopes are partially overridden by scheduler policy

**Status: Proven.**

`MmlArrangementScheduler.kt:17-23` defines global FM/SSG attack and release values. For each v1 FM note, lines 273–284:

- subtract 8 ms from the written duration;
- pass an 8 ms attack override;
- pass an 8 ms release override.

`Fm4OpVoice.kt:120-137` applies overrides only to carrier operators. `OpnEnvelopeCompatibility.kt:46-82` then converts those float seconds back to legal OPN rates even for `OPN_RATE` patches.

Therefore the authored AR/RR fields in `LlsPatches` are not the full production envelope for v1. Modulators use the patch register rates while carriers use scheduler-selected compatibility rates. Every v1 FM instrument is partially homogenized.

This can make the patch's modulation envelope and carrier envelope evolve as an unintended hybrid, changing brightness and body over each note. It is a direct source of “timbre unlike expected.”

### P0 — The Bad Apple/LLS target's essential SSG harmony is buried by the production gain contract

**Status: Proven.**

The mislabeled Bad Apple/LLS MML has five tonal tracks in text, but only four are close to continuously active; the fifth is a punctuating line, and every part remains monophonic:

- A: mono FM At54 lead;
- B: mono FM At74 counterline;
- C: mono FM At99 bass;
- D: mono SSG arpeggio;
- E: mono FM At181 punctuating line;
- R: procedural rhythm.

There is no chord allocator in v1. Chordal information is implied by several independent monophonic lines and a rapid SSG arpeggio.

The SSG layer is especially weak in the actual gain chain:

1. MML D volume is `v5`, or 5/15.
2. The scheduler applies harmony lane gain 0.45 and mix gain 0.75.
3. The SSG bus applies -18 dB (`AudioLaws.kt:13`, about 0.126 linear).

Before master/headroom scaling, that path is roughly `5/15 * 0.45 * 0.75 * 0.126 = 0.0142`. The lead event velocity alone is `12/15 * 0.5 * 0.75 = 0.30` before patch/operator scaling. The layer intended to imply continuous harmony is therefore easy to bury.

The supplied target specification explicitly lists square wave alongside `@54/@74/@99/@181` and drums. SSG is therefore not optional decoration for this benchmark; it is one of the target's defining instrument roles. A production mix that makes it functionally disappear is an engine/mix-contract failure.

The existing scheduler test asserts only the pre-bus event velocity. It does not test the post-mix SSG-to-FM balance.

### P2 — No richness/resonance system exists

**Status: Proven.**

The active signal path contains:

- dry FM/SSG/procedural-drum summing;
- per-voice oversampling/downsample filtering;
- a global one-pole output filter;
- a soft-clip curve;
- optional static peak EQ.

It contains no reverb, delay, chorus, room response, resonant filter, send bus, or cross-voice ambience. LFO exists but Rin disables it. The Bad Apple/LLS v1 path disables shared LFO in `MmlArrangementScheduler.kt:40`.

“Resonance” and “richness” therefore have to come entirely from FM partials, overlapping release tails, and arrangement. The Bad Apple/LLS v1 path has no software polyphonic tail allocator; Rin uses it only for the piano part.

### P1 — Output coloration has too many independently tuned stages

**Status: Proven.**

Production enables two smoothing stages:

- every FM voice enables 2x oversampling (`SoundPreviewPlayer.kt:179-183`), with a custom one-pole downsampler in `Fm4OpVoice.kt:419-435`;
- the synth output filter is enabled (`SoundPreviewPlayer.kt:177`), using `filterAlpha = 0.50` in `OpnaLikeSynthesizer.kt:24-28,584-637`.

The mix then applies several independent gain controls:

- per-event volume;
- v1 lane gain or v2 direct fine volume;
- patch `totalLevel` (a non-register float);
- FM/SSG/rhythm bus gain;
- chip headroom;
- `MASTER_GAIN = 1.5`;
- soft clipping;
- optional song EQ;
- final float-to-16-bit clamp.

These product-specific DSP stages are allowed in the clean-room engine, but their overlapping responsibilities make it difficult to determine whether a tonal problem belongs to operator math, patch authoring, bus balance, or post-processing.

Current Rin is not being damaged by soft clipping: the focused full render reported a pre-clamp peak of `0.21523634` with zero knee crossings. The harsh/thin result is not overload distortion.

### P1 — The deliberate sixteen-voice extension has incomplete capacity semantics

**Status: Proven.**

`AudioLaws.kt:4-7` defines six FM channels but sixteen FM render voices. `OpnaLikeSynthesizer.kt:11` instantiates all sixteen as complete four-operator voices. `MmlCompiler.kt:234-277` estimates concurrent note gates, and `OpnaLikeSynthesizer.kt:512-548` allocates/steals software voices for MML v2 chords and `P1` tails.

This is a legitimate clean-room software-synth feature, not an accuracy violation. It does create a split model that must have one explicit product contract:

- six authored “control channels”;
- sixteen independent render voices;
- one shared LFO;
- patches copied per allocated voice;
- release-tail stealing not represented in the compiler's gate-only capacity calculation.

The compiler checks simultaneous gated voices, not the number of still-releasing voices. Runtime can steal a releasing voice when the pool fills. That means “compiles within capacity” is not a proof that every release tail survives.

### P0 — The FM envelope API is duplicated, and one test inspects the inactive copy

**Status: Proven.**

Every `OperatorState` owns both:

- `Envelope`, a float ADSR object;
- `OpnRateEnvelope`, the actual FM render envelope.

`OperatorState.kt:9-11` admits the first is retained as a source-compatible view. `Fm4OpVoice.kt:568-575` renders only `opnEnvelope`.

Nevertheless:

- `Fm4OpVoice.getOperatorEnvelope` exposes the inactive float object;
- `OpnaFmCoreTest.kt:204-235` verifies ADSR overrides by reading that inactive object;
- `EnvelopeStageTest` validates the float envelope independently;
- `OpnRateEnvelope` is separately tested.

The test can pass while the audible FM envelope is wrong. This is a concrete example of duplicate state producing false confidence.

### P0 — SSG has two synthesizers inside one class

**Status: Proven.**

`SsgVoice` contains:

1. a legacy float-frequency, float-phase, PolyBLEP square/noise voice with float ADSR;
2. a “hardware mode” integer phase voice with fixed levels and shared noise/envelope state.

MML v1 schedules SSG without an `SsgPatch`, so the Bad Apple/LLS benchmark uses the first path. MML v2 named SSG patches invoke the second path.

Additional semantic differences between the two internal SSG paths:

- v2 tone pitch uses a different integer-phase path from v1's direct float-phase path;
- applying any SSG patch reconfigures shared noise and restarts the shared envelope, even if the selected patch uses fixed level;
- legacy SSG uses a per-channel 16-bit LFSR, while v2 uses one shared 17-bit generator.

This is not one coherent SSG contract.

### Scope clarification — The clean-room YM2608-inspired engine is not missing emulator peripherals

**Status: Proven by `ENGINE_BRIEF.md` and the project laws.**

The earlier framing of omitted YM2608 features as engine deficiencies was wrong. This project is building a procedural musical synthesizer around selected OPN ideas, not a YM2608 device emulator and not a playback host for register streams, ROM samples, or audio files.

Current intended scope:

| Musical area | Current implementation | Audit assessment |
|---|---|---|
| FM topology | 6 authored channels, 16 preallocated render voices, 4 operators, 8 algorithms | Intended clean-room core; unify its song-facing semantics |
| Pitch | Procedurally derived block/FNUM, MUL, and detune | Strong, equation-tested foundation |
| Envelopes | Procedural integer attenuation with OPN-style parameters | Intended; remove duplicate compatibility truth |
| FM expression | LFO, AM/PM, SSG-EG, CH3-style per-operator control | Useful selected features; no raw-register frontend required |
| SSG | 3 procedural tone/noise/envelope voices | Intended; v1/v2 must use one SSG contract |
| Rhythm | 6 original procedural drum voices | Correct under the asset law; improve musically, never replace with ROM/sample data |
| Output | Custom gains, filtering, clipping, optional EQ | Allowed; simplify and document responsibilities |
| Android presentation | 48 kHz mono PCM | Current product contract; either author for it or deliberately change it |
| ADPCM/sample playback | Absent | Correctly out of scope |
| Raw registers/status/bus waits/timers/CSM | Absent | Correctly out of scope |
| S98/VGM/PMD binary compatibility | Absent | Correctly out of scope |

The name `OpnaLikeSynthesizer` accurately communicates inspiration without promising emulation. “Custom YM2608-inspired audio engine” is also a fair project description as long as it is not expanded into a bit-perfect compatibility claim. The audit should judge internal consistency, deterministic procedural behavior, musical quality, and MML integration—not peripheral completeness.

### P2 — Large dead and quarantined systems remain compiled beside production

**Status: Proven by complete read plus repository reference search.**

`SoundMelodies.kt` is 890 lines. Its arrangement model types are still active, but the `SoundMelodies` object itself has no caller. It retains:

- old chime/victory/bad-apple/senbonzakura factories;
- repeated floating frequency constants;
- legacy list-building song code;
- a retired Lotus Land Story arrangement;
- old `NoteSpec` compilation helpers.

Other inactive/test-only generations include:

- `SongCompiler.compileNotes`: no caller;
- `NoteLen`: no caller;
- `OpnaPatterns`: test-only;
- `Scale` implementations: used only by `OpnaPatterns`;
- stereo render: test-only in the product;
- EQ directives: supported and tested but unused by both catalog songs;
- multiple legacy patches used primarily by tests and optional v2 names.

The dead code does not directly execute in current playback, but it keeps obsolete abstractions compile-visible and makes future changes likely to target the wrong layer.

### P2 — The automated hot-path audits pass but do not audit architecture or sound

**Status: Proven.**

Both scripts passed:

- `python tools/math_oracles/opna_audit.py`
- `python tools/hotpath_audit.py ... --strict`

Their scope is token scanning. `opna_audit.py:17-59` uses a brace matcher and `BANNED_TOKENS`; it cannot detect duplicate envelope truth, dead playback features, incorrect gain structure, score errors, mono collapse, weak tests, or incorrect chip equations that happen not to contain a banned token.

The Gradle `opnaAudit` task therefore means “no obvious forbidden syntax in selected functions,” not “OPNA behavior audited.”

## Fragmentation and duplication map

| Domain | Generation A | Generation B | Current consequence |
|---|---|---|---|
| Song model | v1 `ToneSpec` + `Lane` inside `ArrangementLanes` | v2 `CompiledOpnaSong` primitive arrays, also wrapped in `ArrangementLanes` | version selects different semantic backends |
| Compiler | retired `SongCompiler`, MML v1 lane compiler | MML v2 primitive-program compiler | three timing semantics remain; requested upgrade became a fork |
| Timing | floating beat/ms conversion | v1 tick→ms flooring | v2 absolute tick→sample conversion |
| Note lengths | unused `NoteLen` | legacy `NoteLength` | MML denominators add a third model |
| Pitch helpers | `audio.Midi.midiToFreq` | `SongCompiler.midiNoteToFreq` | duplicate equations |
| FM envelope | float `Envelope` mirror | integer `OpnRateEnvelope` | tests can inspect wrong state |
| FM patch fields | float ADSR seconds/levels | OPN-style AR/DR/SR/SL/RR/KS | `EgMode` compatibility branch |
| Patch libraries | `Patches` | `LlsPatches` + `OpnaPatchBank` | mixed generations behind names |
| SSG voice | PolyBLEP/float ADSR | integer/shared OPN-inspired mode | v1 and v2 sound fundamentally different |
| Noise | per-voice 16-bit `LfsrNoise` | shared 17-bit SSG LFSR | two incompatible SSG noise laws |
| Rhythm | frequency/type sentinels in `ToneSpec` | enum rhythm events | same drums reached through two encodings |
| Polyphony | mono lane assignment | 16-voice allocator | v2 behavior cannot be inferred from v1 |
| Output | mono production renderer | stereo tested renderer | pan feature is dead in product |
| Tone shaping | patch/operator design | output LPF, soft clip, master EQ | multiple places can mask the same defect |

## Why this happened

### 1. The requested upgrade was implemented as a side-by-side backend

The oldest visible model is list-based `ToneSpec` playback with float ADSR and procedural patterns. MML v1 was adapted into that model. The integer OPN core was then placed underneath it through `OpnEnvelopeCompatibility`. When v2 added fixed channel families, polyphony, LFO, pan, chords, macros, and FM3-style control, those features were compiled into a separate primitive event program instead of extending a single shared semantic backend.

There is a reasonable engineering motive behind part of this: primitive arrays and a fixed-capacity program fit the no-allocation audio law much better than lists of `ToneSpec`. The correct migration, however, was to make that representation the common compiled form and lower existing v1 MML into it. Keeping the old v1 backend intact turned a good internal representation into a second MML implementation.

No consolidation step migrated v1 into the new program or removed the legacy song compiler, float FM envelope state, legacy SSG path, obsolete patterns, or retired arrangements.

### 2. Rapid cross-cutting changes did not receive a stabilization phase

The relevant git history shows repeated edits to the parser, compiler, FM voice, envelope, synth, old song store, and Android player between June 28 and July 4. `RinToShiteSong.kt` first appears in the July 4 commit that also changed the MML/compiler/FM areas. That is evidence that a new production song and engine behavior moved together rather than the song targeting a stable, reference-validated instrument.

### 3. The benchmark lost its identity

The Bad Apple/LLS-derived MML was named and displayed as Senbonzakura, and the incorrect name propagated into tests and `ENGINE_BRIEF.md`. Once the source stopped being described as a 1:22 E-flat-minor-to-G-minor PC-98 target with a defining square-wave part and four numbered FM patches, a 32-bar repeated fragment could be treated as “coherent” merely because it compiled. The wrong name did not change samples directly; it removed the musical specification needed to recognize that the implementation was incomplete.

### 4. Acceptance criteria were structural, not musical

The current tests heavily favor:

- compilation success;
- event counts;
- deterministic equality;
- finite output;
- broad non-silence;
- loose headroom;
- isolated feature existence.

Those are useful engineering properties, but none answers “does this rendition sound like the source?” or “does this patch retain body across the note?”

### 5. Relative regression tests were treated as absolute quality evidence

For example, `MmlArrangementSchedulerTest.richPatchesReduceHighBandAndRestoreLowBody` proves only that one patch set has less high-band and slightly more low-band energy than a deliberately hollow local alternative. It does not prove that either set is good, balanced, or musically correct.

### 6. The product path was not included in feature acceptance

Stereo passes engine tests while Android is mono. Pan syntax passes parser/compiler tests while it is inaudible. The newer SSG path passes isolated tests while the only production v1 benchmark uses the legacy path. This is the classic shape of local feature completion without end-to-end validation.

## Song-specific diagnosis

### Bad Apple!! / Lotus Land Story v1 target (misnamed Senbonzakura)

What is correctly identified:

- the source compiles consistently;
- its 160.73 BPM, 4/4 meter, E-flat-minor basis, square wave, four numbered LLS patches, and drums match the supplied target specification;
- its 32-bar timeline is internally deterministic, although it is not the full target form;
- it has lead, counterline, bass, SSG arpeggio, an additional FM line, and rhythm;
- it uses absolute tick conversion before v1's millisecond representation, avoiding cumulative note-duration drift;
- the scheduler reaches four FM voices, one SSG voice, and drums deterministically.

Why it is not currently a valid rendition or healthy engine benchmark:

1. The current 32 bars last about 47.78 seconds, not the target's 1:22.
2. One 16-bar block is repeated twice; the required intro/A/B/chorus form and single key change near 0:58 are absent.
3. The benchmark remains on the old `ToneSpec`/`Lane` backend instead of the forward primitive event program.
4. The defining SSG/square-wave role is mixed extremely low after the -18 dB SSG bus.
5. Global 8 ms carrier attack/release overrides partially replace `@54/@74/@99/@181` OPN-style envelope parameters.
6. V1 SSG is the legacy PolyBLEP/float-ADSR synth, not the canonical path intended for the forward engine.
7. Lead/harmony/bass gains and patch levels favor upper FM material, while all lanes remain monophonic and release tails are not preserved.
8. Shared LFO is forcibly disabled, and the active product output is mono and post-filtered.
9. Tests validate compilation and relative waveform statistics, not the supplied form, instrument-role audibility, or musical resemblance.

Conclusion: this item cannot be called the one “working song.” It is the correct **kind** of benchmark but an incomplete MML reconstruction played through a fragmented engine path. The source transcription must eventually be rebuilt, yet the engine is independently at fault because its v1 scheduler changes the FM envelopes and suppresses a defining SSG role. Backend unification must precede the full musical rewrite so engine changes and transcription changes can be evaluated separately.

### Rin / MML v2

What mechanically works:

- v2 parsing and compilation succeed;
- 271 events become 542 sequencer ON/OFF events;
- absolute tick-to-sample conversion is coherent;
- all five opening parts are scheduled;
- the 16-voice pool keeps the P1 arpeggio active;
- the render is deterministic, finite, and well below clipping.

Why the result fails musically:

1. It is only a ten-bar final-section adaptation, not the requested song as a whole.
2. There is no human-auditable score/note map proving the pitches.
3. It deliberately deletes rhythm.
4. It has no SSG color and no dedicated low bass.
5. Three harmony parts use the same four-carrier strings patch and synchronized half/whole-note rhythm.
6. The piano part is continuous but dry and made from one invented algorithm-5 patch.
7. LFO is disabled, removing the only implemented shared modulation source.
8. Its stereo plan is rendered mono.
9. Full-loop bars 7–8 contain explicit 441 ms lead rests.
10. The normal preview truncates the piece at seven seconds, near the end of bar 4.
11. Tests never check note identity beyond the opening event of each part or compare to the song.
12. Tests never measure per-beat/per-bar RMS valleys, pitch salience, spectral balance, or the Android mono result.

Conclusion: the parser/compiler did what the source asked. The source, patches, mix, and acceptance process asked for the wrong thing.

## Test-suite audit

### Tests that provide meaningful evidence

- `OpnProceduralCoreTest.a4UsesNearestLegalFnumAndPitch`: supports pitch-table correctness.
- `OpnProceduralCoreTest.phaseStepTracksNearestLegalFnumWithinTwoCents`: supports phase-step conversion.
- `OpnProceduralCoreTest.envelopeTimeComesFromChipClockNotHostRate`: supports host-rate independence.
- `OpnaSubChunkSchedulingTest`: supports event-boundary segmentation.
- `OpnaPolyphonyTest.olderNoteOffCannotStopNewerRetriggeredNote`: supports note-ID safety.
- deterministic chunking and finite-output tests: support reproducibility/stability.

### Tests that are too weak for their implied claim

- “richness” via peak/RMS > 1.5 only proves the waveform is not a pure sine.
- “headroom” limits such as pre-clamp peak < 20 or < 8 are far too loose to establish mix quality.
- `OpnaLaneGainTest` validates constants are inside arbitrary ranges, not that lanes are balanced after patches and buses.
- Rin opening-part RMS > 0.0005 proves existence, not audibility in the mix or correct timbre.
- the full Rin render asserts only finite samples.
- stereo tests never exercise `SoundPreviewPlayer`.
- `EnvelopeStageTest` tests the legacy float envelope, not FM's active envelope.
- `OpnaFmCoreTest.adsrOverrideAppliesToCarrierOperators` reads the inactive compatibility mirror.
- `OpnaPatterns` acceptance can pass without applying FM patches to all scheduled pattern channels; it is not a catalog-song test.

### Missing tests and asset-free acceptance evidence

- Bad Apple/LLS target-form validation: 160.73 BPM, 4/4, expected full duration/form, one late E-flat-minor-to-G-minor transition, and the six named instrument roles;
- catalog metadata that identifies the v1 benchmark correctly while preserving its persisted key;
- human-auditable expected note list for Rin;
- v1-to-unified-backend event equivalence for the current Bad Apple/LLS migration fixture;
- Android production-format stereo render regression;
- per-part and full-mix RMS envelopes over musical windows;
- maximum valley depth/duration for Q8 passages;
- post-bus FM/SSG/rhythm balance;
- pitch-salience measurements on stable single notes;
- patch transient/steady-state spectra;
- release-tail voice-pool pressure;
- preview cutoff behavior;
- comparison of v1 and v2 semantics for the same minimal score/patch;
- live human listening acceptance for each catalog song.

All waveform checks above can render into temporary in-memory test buffers and assert compact derived numbers. They do not require checked-in PCM, audio fixtures, sample files, emulator captures, or runtime file I/O.

## File-by-file disposition

### Active production core

- `AudioLaws.kt`: active; central bus/voice constants; SSG -18 dB balance is questionable.
- `Midi.kt`: active; one of two MIDI-frequency helpers.
- `MmlParser.kt`: active; feature-rich, allocation-heavy at compile time (acceptable), includes song EQ and two dialects.
- `MmlCompiler.kt`: active; contains two substantially different compilers in one object.
- `MmlArrangementScheduler.kt`: active; contains two substantially different schedulers and v1's global articulation policy.
- `MmlSongBank.kt`: active; contains the mislabeled, structurally incomplete Bad Apple/LLS v1 fixture, caches both compile results, and scales v1/v2 volume differently.
- `SongCatalog.kt`: active; incorrectly displays the v1 Bad Apple/LLS benchmark as `SENBONZAKURA`.
- `RinToShiteSong.kt`: active; failed ten-bar production arrangement.
- `CompiledOpnaSong.kt`: active v2 primitive program; suitable as the one unified post-parse event representation, but currently v2-only and nested inside legacy `ArrangementLanes`.
- `Fm4OpVoice.kt`: active FM renderer; coherent hot loop, but mixes compatibility state, an additional total-gain domain, and several independently tuned output behaviors.
- `OpnPitch.kt`: active; comparatively strong and manual-aligned.
- `OpnRateEnvelope.kt`: active FM EG; deterministic clean-room implementation with equation and behavior tests.
- `OpnEnvelopeCompatibility.kt`: active for legacy fields/overrides; major semantic bridge and source of dual truth.
- `AudioSinLut.kt`: active generated log/power tables; deterministic and equation-tested, correctly avoiding copied ROM tables.
- `Lfo.kt`: active but disabled by both current production arrangements.
- `LlsPatches.kt`: active v1 patches and v2 numeric names.
- `Patches.kt`: active through named v2 patches, otherwise heavily test-oriented legacy bank.
- `OpnaPatchBank.kt`: active registry plus Rin patches; combines multiple patch generations.
- `OpnaSequencer.kt`: active; preallocated and sample-sorted; direct APIs silently drop on capacity.
- `OpnaLikeSynthesizer.kt`: active top-level mix/dispatch; 16 FM voices, mono/stereo split, arbitrary output DSP.
- `SsgVoice.kt`: active; contains two incompatible engines.
- `SsgSharedState.kt`: active only for the newer v2 SSG path; one side of the v1/v2 split.
- `LfsrNoise.kt`: active for drums and legacy SSG noise; separate from the newer shared SSG LFSR.
- `ProceduralDrums.kt`: active; original procedural instruments, exactly the correct asset-free rhythm strategy.
- `MasterPeakEq.kt`: active infrastructure, unused by catalog songs; another tone-repair layer.
- `OpnaMixer.kt`: active bus gains; constructor sample rate is unused.
- `OpnaAudioConstants.kt`: active v1 lane/master gains; overlaps `AudioLaws` mix constants.

### Active legacy containers

- `ToneSpec.kt`: active only because v1 and `ArrangementLanes` retain the old representation.
- `SoundMelodies.kt:1-67`: active model definitions live in a file dominated by retired content.
- `SoundMelodies.kt:69-890`: no production caller; compiled legacy/quarantined material.

### Dead or test-only production-source features

- `SongCompiler.kt`: legacy compiler is referenced only by dead `SoundMelodies` builders; `compileNotes` has no caller.
- `NoteLen.kt`: no caller.
- `Scale.kt`: only test-pattern support.
- `OpnaPatterns.kt`: tests only.
- `Envelope.kt`: active for legacy SSG; FM copy is compatibility/test state only.
- stereo rendering: engine/test active, product inactive.
- EQ authoring: parser/player active, catalog content inactive.

### Android playback

- `SoundPreviewPlayer.kt`: sole production audio endpoint; procedural tick is a separate 44.1 kHz float/static engine, while songs use 48 kHz mono 16-bit streaming. This is another small audio subsystem, though not responsible for catalog-song timbre.
- `MainScreenViewModel.kt`: preview caller and persisted song selection.
- `FocusService.kt`: alarm/reminder caller; no alternative music engine.

## Implementation roadmap (no implementation in this audit)

### Milestone 1 — One MML world law

1. Keep one public MML parser. `#MML 2` may enable syntax and validation, but must not select a different audible backend.
2. Make `CompiledOpnaSong` (or a neutral rename) the single fixed-capacity post-parse representation for every MML source.
3. First lower the current v1 Bad Apple/LLS text unchanged into that representation. It is a backend-migration fixture, not a musical gold standard.
4. Replace the `compiledOpnaSong != null` scheduler fork with one scheduler and one timing/gain/voice contract.
5. Preserve the persisted ID during migration, but do not preserve `ToneSpec`/`Lane`, legacy SSG, or scheduler overrides as compatibility mechanisms.

Exit condition: the same authored events from the current v1 fixture reach the one event program deterministically, and v1/v2 no longer select different render semantics.

### Milestone 2 — Make production stereo

The best fit for this project is stereo production, not continued mono. Pan is already part of MML v2 and the engine renderer; sparse FM/SSG arrangements benefit materially from left/right separation without adding effects or assets. Android should present interleaved stereo PCM through `CHANNEL_OUT_STEREO`, while remaining a dumb terminal.

Exit condition: the actual preview/alarm path exercises `renderStereo`; authored pan is audible; mono-only tests no longer stand in for product behavior.

### Milestone 3 — One SSG

1. Make the newer integer/shared-noise/shared-envelope SSG path canonical.
2. Lower v1 square-wave syntax into that path with explicit legacy defaults.
3. Correct its patch-application semantics so a fixed-level patch does not unnecessarily restart shared envelope state.
4. Remove the legacy PolyBLEP/float-ADSR SSG renderer after migration.
5. Tune the post-bus balance against the Bad Apple/LLS square-wave role, which must be clearly audible beside `@54/@74/@99/@181` and drums.

Exit condition: the same SSG command means the same tone/noise/envelope behavior in every MML version, and the benchmark's defining SSG role is not buried.

### Milestone 4 — One FM envelope truth

1. Make `OpnRateEnvelope` the only audible FM envelope.
2. Retain float ADSR only as a one-way legacy import/conversion input if still needed.
3. Stop the scheduler from silently replacing carrier AR/RR with global 8 ms overrides.
4. Ensure `@54/@74/@99/@181` reach rendering with their intended operator envelopes intact.

Exit condition: patch authors can trust that a patch's OPN-style parameters determine its audible envelope, and tests inspect the state that actually renders.

### Milestone 5 — Unify practical MML expression

Bring the already implemented useful v2 expression—gate, ties, dots, dynamics, LFO, pan, and patch changes—through the one compiler/event contract. Mid-track timbre changes should become a deliberate supported operation rather than a v1 rejection. Do this before adding more specialist CH3-style features.

Exit condition: a composer can use the common expression vocabulary without choosing a different backend or changing the meaning of existing v1 text.

### Milestone 6 — Restore the benchmark, then rebuild Rin

Bad Apple/LLS comes first:

1. Replace the 32-bar repeated placeholder with a reviewable full structure matching the supplied target: 160.73 BPM, 4/4, approximately 1:22, E-flat-minor intro/A/B material, one late transition to the G-minor chorus, square wave, `@54/@74/@99/@181`, and procedural drums.
2. Correct the catalog display name, source comments, and documentation while retaining the persisted key if compatibility requires it.
3. Validate bass, SSG, each FM role, rhythm, and the full stereo mix by listening through the production path.

Only after that target works should Rin be rebuilt with a clear scope, verified notes, dedicated low role, SSG, procedural rhythm where musically intended, complementary patches, and useful stereo placement.

Dead-code cleanup follows the migration; it must not distract from getting the benchmark audible. Then move any still-needed model types out of `SoundMelodies.kt` and remove retired builders, `SongCompiler`, `NoteLen`, `OpnaPatterns`, unused scale code, and obsolete compatibility state.

## Lightweight acceptance policy

The previous twelve-item release checklist was too heavy for ordinary iteration. Daily development should remain direct: make one bounded change, render the affected passage, listen, and keep only the smallest deterministic measurement needed to prevent that exact regression.

The stricter gate applies only when a song enters or changes in `SongCatalog`:

1. Its identity, form, tempo, key plan, and instrument roles match a human-auditable target description.
2. It is heard through the exact Android stereo preview/alarm path, not an engine-only surrogate.
3. Each intended FM, SSG, bass, and rhythm role is audible; no unintended valley or voice drop is accepted.
4. Peak/clipping and allocation checks pass, with deeper spectral measurements used only when diagnosing a named problem.
5. A human listening pass accepts the result musically.
6. No WAV/MP3/OGG, copied samples, embedded PCM, runtime file I/O, or platform media playback is introduced.

Engine-wide invariants remain simple: one MML event/scheduler contract, one SSG path, one audible FM envelope, deterministic allocation-free rendering, derived procedural tables, procedural drums, and no out-of-scope emulator peripherals.

## Constraint compliance of this revised recommendation

```text
FM DSP CHECK:
Callback: no callback code changed; recommendations retain caller-provided procedural rendering
Allocations: unified compiled program remains primitive and fixed-capacity
State buffers: existing preallocated synth/sequencer/voice state remains the model
Sample asset leakage: none proposed; procedural drums remain procedural
Native handoff: Android remains a dumb PCM presenter
Result: PASS

ASSET LAW CHECK:
New files: AudioAudit.md only; no runtime content file proposed
Runtime file IO: none
Embedded data: no PCM, ROM dump, emulator table, or opaque binary proposed
Procedural representation: MML source, named equations, generated tables, and procedural synthesis
Result: PASS
```

## Verification performed

Current commands/results:

- v1 identity/provenance check across `MmlSongBank.kt`, `SoundMelodies.kt`, `SongCatalog.kt`, `LlsPatches.kt`, `ENGINE_BRIEF.md`, and git history: **mislabeled Bad Apple/LLS target confirmed**;
- current v1 form calculation: 32 bars at 160.73 BPM = `47.78199` seconds versus the supplied 82-second target: **structurally incomplete**;
- full `:shared-engine:testDebugUnitTest`: **PASS**;
- focused `RinToShiteSongTest`: **PASS**;
- focused full Rin render: pre-clamp peak `0.21523634`, knee crossings `0`;
- OPNA token audit: **PASS**;
- strict general hot-loop token audit on the audio tree: **PASS**.

These results prove build/test stability and support the no-clipping/no-obvious-hot-loop-allocation findings. They do not contradict the audit's musical failure verdict, because the suite does not encode target identity, full form, instrument-role audibility, or musical correctness.

## Final conclusion

This failure did not happen because one small MML v2 parser bug inserted silence, and it did not happen because the clean-room synth lacks raw registers, timers, ADPCM, ROM drums, or audio-file playback. Those features are intentionally irrelevant to this product.

It happened because an upgrade request was implemented as a backend fork: v2 received a new primitive program, timing path, scheduler branch, SSG semantics, polyphony model, patch behavior, and expression system while the existing v1 song stayed on the old backend. The primitive program was a sound hot-path design choice, but failing to migrate v1 into it created two MML products. An unverified arrangement and weak musical acceptance then exposed that split.

The mislabeled Bad Apple/LLS fixture produces a busier signal through accumulated compatibility, but it does not reproduce the supplied 1:22 form and the engine distorts two defining elements: the LLS patch envelopes and SSG role. Rin then fails differently because it is sparse in low/rhythm/color roles, uses unvalidated invented patches and an unverified ten-bar transcription, loses its stereo plan in production, and was accepted by mechanical tests that never asked whether it sounded like `凛として咲く花の如く`.

The correct next move is not to add another compensating EQ, gain constant, patch alias, compatibility branch, asset pipeline, sample player, register-emulation layer, or advanced FM showcase. It is to make both MML source versions lower into one asset-free procedural event/scheduler contract, enable the existing stereo product path, unify SSG and FM envelopes, and then reconstruct and listen to the full Bad Apple/LLS benchmark before returning to Rin.
