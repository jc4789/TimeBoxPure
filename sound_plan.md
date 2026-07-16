# Sound Engine Productization Plan

Status: proposed plan only; no runtime changes are part of this document.

## Decision

The concern is valid, with one important qualification: the FM/SSG DSP core is not the part that needs a rewrite. The core already has the right shape for a clean-room, good-sounding OPNA-like synthesizer. The bloat is mainly in the layers around it:

- two playback/sequencing architectures;
- two MML compiler paths;
- a PMD-compatibility-sized command surface before the composer-facing language was frozen;
- three percussion domains built on similar procedural generators;
- experimental modulation, FM3, output, and mastering systems living in the product path;
- eager compilation of unused entries, repeated timeline construction, and product policy selected by Android code.

The recommended work is therefore a controlled productization and deletion pass, not a new audio engine. Preserve the proven FM/SSG core, choose one authored-song path, define a small stable MML profile, normalize the existing songs onto it, and then remove or quarantine everything outside that profile.

The finish line is practical: adding a song should be ordinary content work. It should not require a new event type, synthesizer branch, platform change, compatibility flag, or special mastering subsystem.

## Product goal

Ship one stable, efficient, clean-room procedural music platform with:

- six normal four-operator FM channels;
- three SSG channels with tone/noise and one predictable envelope model;
- one clean procedural six-voice OPNA-like rhythm lane;
- one compiled, allocation-free playback path;
- one common-code product audio profile;
- one documented MML dialect that supports complete songs;
- repeatable start, stop, restart, preview, and long looping playback;
- sound quality judged by listening and operational stability, not chip bit accuracy.

This is OPNA-like rather than an emulator. It should preserve the musical identity of YM2608-era FM, SSG, and rhythm without reproducing register buses, timer IRQs, driver bugs, ROM samples, or every PMD command.

## Non-goals

- No Compose, SwiftUI, React, Electron, or other UI/framework rewrite.
- No replacement of the FM/SSG core simply to make it look newer.
- No bit-exact YM2608, PMD, MUCOM, FMGEN, or ymfm compatibility.
- No PMD binary playback, raw register API, timer/status emulation, or hardware wait emulation.
- No PCM, ADPCM, P86, PPS, PPZ, PVI, PPC, or external sample pipeline.
- No attempt to accept every historical MML dialect.
- No new external audio assets or newly copied reference-song/voice data. Existing transcriptions have a separate provenance/rights decision; they are not evidence for clean-room implementation.
- No large test-preservation project for features intentionally removed.

## Evidence used

### Current TimeBox implementation

The production path is currently:

```text
SongCatalog
    -> MmlSongBank / MmlCompiler
    -> ArrangementLanes
    -> MmlArrangementScheduler
    -> CompiledOpnaTimelineFactory
    -> CompiledOpnaPlayer
    -> OpnaLikeSynthesizer
    -> mono float frames
    -> Android SoundPreviewPlayer / AudioTrack
```

This chain is visible in:

- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/SongCatalog.kt:13`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/mml/MmlSongBank.kt:326`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/mml/MmlArrangementScheduler.kt:17`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/CompiledOpnaPlayer.kt:33`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/opna/OpnaLikeSynthesizer.kt:304`
- `app/src/main/java/com/example/timeboxvibe/engine/SoundPreviewPlayer.kt:167`

The valuable architectural split already exists: MML compilation may allocate during setup, while the player consumes primitive arrays and the callback renders through preallocated state. That split should remain.

### Clean-room references

The supplied references were used as behavioral and workload evidence, not as code or data sources:

- `MUCOM88_REF.txt` identifies the high-value composing center: notes, timing, voices, volume, gate, detune, pan, ties, loops, macros, FM patches, and SSG controls.
- `PMDMML.MAN` shows why full PMD compatibility expands so quickly: it combines authoring conveniences, multiple chips and sample engines, hardware controls, compiler preprocessing, driver state, effects, and compatibility modes in one language.
- `PMDWin.txt` and `DLLInfop.txt` support a small producer-style lifecycle: initialize/load, start, render caller-owned PCM buffers, stop/reset, and query bounded status.
- `PSGEDATA.DOC` is useful for SSG tone/noise/envelope behavior, but describes an effect format rather than a required general song runtime.
- `pc 88 va 12.txt` confirms the audible OPNA shape: six FM channels, three SSG channels, six rhythm voices, hardware LFO, PMS/AMS, and pan. Its timer, bus, ADPCM, and status sections are not product requirements.
- The Bad Apple and Eternal Shrine Maiden dumps show actual authoring pressure: lots of notes, octave/length changes, voices, repeats, alternate endings, and rhythm-pattern reuse; very little portamento or exotic operator automation.

The references disagree only superficially about rhythm. PMD compatibility would require two historical rhythm systems and hidden SSG3 ownership rules; the real songs nevertheless benefit strongly from a percussion lane. The clean answer is one original procedural rhythm model, not emulation of PMD K/R or YM2608 ROM/sample behavior.

## What is already strong

These systems should be preserved and stabilized rather than replaced:

- `Fm4OpVoice`, `OpnRateEnvelope`, and `OpnPitch`: four-operator algorithms, feedback, OPN-like rate envelopes, and preallocated voice state.
- `SsgVoice`, `SsgSharedState`, and `SsgHardwareLaws`: shared-chip tone/noise behavior and procedural SSG rendering.
- `CompiledInstrumentBank`: renderer-facing instrument lookup separated from the mutable authoring layer.
- `CompiledOpnaSong`, `CompiledOpnaTimeline`, and `CompiledOpnaPlayer`: compile-before-play primitive data and allocation-free sequential playback.
- `ProceduralDrums`: a clean-room way to achieve rhythm character without shipping samples.
- the common-code synth ownership model: the platform receives frames rather than owning music logic.

The callback is already substantially allocation-free. Setup-time array construction is worth simplifying later, but it is not the first performance emergency.

## Where the complexity is coming from

### 1. Two sequencer architectures

The catalog uses compiled MML timelines, but `OpnaSequencer` and `OpnaPatterns` remain as a second mutable sample-domain scheduler. `OpnaLikeSynthesizer` still contains dispatcher and render overloads for both worlds.

Why this hurts:

- every new synth event can require two implementations;
- the public facade looks less stable than the product actually is;
- tests can keep dead architecture alive;
- mono/stereo/raw/legacy overloads obscure the one callback contract the app needs.

Decision: compiled authored-song playback is the only product architecture. Retire the mutable sequencer after any genuinely useful listening fixtures are converted.

### 2. Two MML generations

`MmlCompiler.kt` contains a V1 path and a much larger MML2 path. All catalog songs use `#MML 2`; the V1 Bad Apple text in `MmlSongBank.kt:12` is explicitly a migration fixture.

Why this hurts:

- fixes and diagnostics can diverge;
- old syntax influences the data model even when no product song uses it;
- a migration fixture is being carried as production content.

Decision: MML2 becomes the sole product language. Archive or rewrite V1 fixtures, then remove the V1 production path.

### 3. The event vocabulary grew before the product profile froze

The current authored program has 41 event kinds (`CompiledOpnaSong.kt:124`), and the sample timeline has 46 boundary kinds (`CompiledOpnaTimeline.kt:28`). They cover normal notes alongside FM3 parts, pooled polyphony, dual software LFO state, per-operator automation, two additional rhythm models, hardware-envelope controls, and hardware-LFO delay state.

Why this hurts:

- each event adds parser, compiler, storage, conversion, dispatch, reset, and test obligations;
- parallel arrays repeat fields that most songs never use;
- simplifying the IR is unsafe until the intended language is explicit.

Decision: freeze the composer profile first. Remove unused commands and event kinds second. Compact the IR only after its smaller vocabulary is proven.

### 4. Three percussion domains

The synth owns legacy direct drums, `Ym2608RhythmUnit`, and `PmdSsgEffectUnit`; each ultimately uses procedural drum generation, and all are rendered every block. Different MML syntax selects different domains.

Why this hurts:

- duplicated state and rendering cost;
- unclear volume, pan, choke, and reset behavior;
- composers cannot tell which drum model is the supported one;
- PMD K/R behavior introduces historical SSG3 channel-stealing semantics that do not improve this app.

Decision: retain one procedural six-voice rhythm unit and one rhythm event family. Compile the stable `R` lane and bounded rhythm patterns to that unit. Remove legacy direct-drum and K/R compatibility domains once songs are normalized.

### 5. Modulation and FM3 are paid for even when dormant

`PmdPerformanceState` prepares normal FM/SSG parts, four FM3 logical parts, and two software LFOs per part. The synth iterates 16 FM render voices, and Android currently enables 2x oversampling for all of them.

Why this hurts:

- dormant features consume callback time and expand reset/state rules;
- FM3 operator ownership and per-slot controls interact in difficult ways;
- the dual PMD LFO matrix is much larger than ordinary vibrato needs;
- unconditional oversampling has no measured low-end-device budget.

Decision: the stable profile has six physical FM channels, not pooled voices or FM3 subparts. Advanced modulation is removed from the default runtime state. Keep only a deliberately bounded LFO feature if an admitted song uses it.

### 6. Output policy is fragmented

Gain and tone can currently be influenced by MML volume, patch level, copied `playbackGain`, scheduler gain, mixer gains, per-song `#EQ`, output filtering, master gain, soft clipping, output profiles, and an optional stereo resonator. Android also chooses filtering, EQ, and oversampling policy.

Why this hurts:

- patches can be "fixed" at multiple layers instead of at the source;
- a song-volume change causes a new wrapper and timeline build;
- platform code is making engine decisions;
- disabled experiments remain lifetime obligations.

Decision: one immutable common-code product audio profile owns channel layout, sample-rate assumptions, quality mode, filtering, headroom, and mastering. Song volume is a runtime master scalar. Android only requests frames, converts them to the OS format, and manages lifecycle.

### 7. Song registration still looks like development scaffolding

- Bad Apple is persisted and aliased under a Senbonzakura identifier.
- Bad Apple, Rin, and a research-only logo song compile eagerly at object initialization.
- timeline conversion is repeated for playback/volume changes.
- `SongKind` and `ArrangementRouting` are one-value enums.
- a scheduler overload accepts an unused synthesizer.

Decision: make a song a declarative entry with stable ID, title, source, patch lookup, preview length, and cached compiled/timeline program. Preserve an old persisted ID only at the storage migration boundary, not in the new authoring API.

### 8. Streaming lifecycle is not yet product-grade

The Android preview player uses a 30-second wake-lock timeout even though songs can exceed it and alarms may loop. Natural completion and some early exits do not perform the same cleanup as explicit `stop()`. Timeline and synth setup are repeated for each playback.

Decision: start/stop/restart/natural-end must share one lifecycle and cleanup contract. Long playback is a release criterion, not a best-effort preview behavior.

## Stable Sound Profile v1

This is the contract later implementation work should freeze.

### Audio/rendering contract

- Common code produces caller-buffered PCM frames with no callback allocation.
- Initial shipping profile: 48 kHz, mono, because that is the current product path.
- The timeline/player is sequential. Restart is explicit; arbitrary seeking is not a product requirement.
- The output buffer size is owned by the platform adapter, within the synth's documented maximum chunk size.
- The authored mix uses one fixed mastering/soft-clip chain. User volume is a bounded scalar after that chain, followed only by a final PCM safety clamp, so UI volume does not change saturation character.
- One fixed output chain is selected through listening; optional resonator/reference profiles are not constructed in the default path.
- Every reset returns voices, envelopes, LFO, rhythm, mixer, filters, event cursor, and sample cursor to a deterministic state.
- A whole-song restart/loop boundary is sample-stable and resets all state; it does not depend on leakage from the previous pass.

Stereo and pan can be admitted later as one coherent profile change. A mono product should not pretend that a pan command is a stable audible feature.

### Synth contract

- Six independent four-operator FM voices.
- One canonical OPN-rate FM patch representation: algorithm, feedback, and explicit operator parameters.
- Three SSG voices sharing the chip noise/envelope state where the hardware model requires it.
- SSG tone, noise, or mixed mode; explicit noise period; direct level; one software-envelope representation with one documented timing basis.
- One procedural rhythm unit with six named OPNA-like roles: bass drum, snare, rim, high-hat, tom, and cymbal.
- One deterministic trigger/choke/reset rule for each rhythm voice.
- No hidden SSG channel theft by percussion.
- No ADPCM or sample-backed voices.

### Stable MML authoring surface

The exact spelling should preserve the current MML2 songs where reasonable, but the semantic surface should be deliberately small.

#### Source and routing

- `#MML 2` as the sole accepted product version.
- Title, composer, and other catalog metadata live in the declarative song registry rather than expanding the MML grammar.
- `#BPM` and `#BAR`.
- A-F are the six FM tracks.
- G-I are the three SSG tracks.
- R is the one procedural rhythm track.
- `;` line comments.
- Clear compile errors for unknown parts or commands; never silently ignore them.

#### Common music commands

- notes `c d e f g a b`, accidentals, and rests;
- absolute and relative octave;
- default length, per-note length, dotted length, and exact tick/clock length;
- conventional BPM tempo changes;
- patch/envelope selection;
- one absolute volume scale plus one relative adjustment form;
- one deterministic gate model;
- small signed detune;
- tie with an exact no-key-off/no-retrigger contract;
- finite repeats;
- bounded named compile-time patterns/macros.

Exact lengths, tempo changes, repeats, and patterns stay because the reference songs use them heavily and they add no callback machinery when resolved during compilation. Alternate endings and in-song loop markers are useful in external references but are not implemented by the current product songs, so they remain deferred until an original composition demonstrates that source duplication or whole-song transport looping is inadequate.

#### FM-only surface

- patch selection from the canonical four-operator bank.

No LFO command is promised by stable v1 because no catalog song demonstrates an audible dependency. A later OPNA hardware-LFO feature must model global shared enable/rate plus per-channel PMS/AMS; turning it off affects A-F together. If independent key-synchronized vibrato is the actual musical need, add one triangle pitch LFO with only delay/rate/depth rather than pretending the hardware LFO is per-channel or restoring the PMD matrix. Hardware-LFO note delay, dual software LFOs, random waveforms, volume targets, operator masks, free-running modes, tempo-dependent clocks, and evolving depth remain outside v1.

#### SSG-only surface

- tone/noise/both selection;
- noise period;
- direct volume;
- named or numbered presets compiled to one software-envelope model and one clock basis.

The current Bad Apple content uses a PMD-style step envelope with tempo-clocked behavior. The preferred product target is fixed real-time envelope timing, but Phase 2 must first convert that song by ear and compare it with the baseline. If the conversion cannot preserve the intended articulation cleanly, retain the tempo-clocked basis instead. Only one basis survives productization. Hardware envelope period/shape should be admitted only if a real composition needs it and its shared-state behavior is documented.

#### Rhythm surface

- six named hit tokens;
- track volume;
- optional per-voice level only if listening shows the patch bank cannot provide a good fixed balance;
- rests, lengths, repeats, patterns, and song loop;
- compile-time named rhythm patterns using the same safe macro limits as pitched tracks.

Do not expose ROM-sample dumps, raw rhythm registers, hardware wait ordering, PMD K/R effect dispatch, or SSG3 ownership rules.

`R` is deliberately a TimeBox direct procedural-rhythm lane. This is an intentional semantic break from PMD, where R defines numbered SSG drum patterns and K sequences them. PMD K/R source is unsupported; importing it requires an offline translation into TimeBox rhythm patterns.

### Compiler obligations

- Parsing, macro expansion, repeat expansion/control flow, source validation, and diagnostics happen before playback.
- Macro recursion is detected directly and indirectly.
- Macro depth, expanded event count, repeat nesting, and pre-loop duration have explicit documented limits.
- Diagnostics retain original line/column locations through macro expansion.
- Illegal/dangling ties are rejected.
- Track lengths, repeat boundaries, and whole-song restart behavior are checked for deterministic behavior.
- Shared SSG noise-period automation conflicts produce a useful diagnostic or warning. The retained software envelope is per voice; shared-envelope diagnostics are needed only if chip hardware-envelope period/shape is admitted later.
- Unsupported compatibility syntax is rejected rather than partly emulated.
- The compiler reports a compact summary: duration, per-track event counts, patches used, peak simultaneous voices, and warnings.

### Transport/lifecycle contract

Use a deliberately small state machine:

```text
EMPTY -> READY -> PLAYING -> ENDED
            ^        |
            |        v
            +----- STOPPED
```

- load/prepare immutable compiled content;
- play from the beginning;
- render the next contiguous frames;
- stop and release platform resources;
- restart deterministically;
- report bounded status such as playing/ended and current sample position.

Pause and arbitrary seek are not part of v1. They should not complicate the product player until the app has a real UI/use case for them.

## Capability disposition

| Capability | Disposition | Reason |
| --- | --- | --- |
| Four-operator FM core | Keep and freeze | Strong foundation and central musical identity. |
| OPN-rate envelope/pitch | Keep and freeze | Canonical FM behavior without bit-accuracy obligations. |
| SSG core and shared state | Keep and freeze | Required OPNA character and already procedural. |
| MML2 -> compiled primitive events | Keep and freeze | Correct allocation boundary. |
| One compiled timeline/player | Keep and freeze | Product path already uses it. |
| Procedural drums | Keep, consolidate | Needed by real arrangements; no sample assets. |
| Compile-time repeats/macros | Keep and bound | High authoring value, zero callback cost. |
| Exact tick lengths/tempo changes | Keep | Used by real reference workloads. |
| One SSG software envelope | Normalize then keep | Current songs need it; duplicate formats do not. |
| Canonical OPN patch format | Normalize then keep | Removes legacy ADSR and dialect-specific ambiguity. |
| Simple hardware LFO | Defer | Authentic but globally coupled; no catalog song proves an audible need. |
| Per-song EQ | Quarantine as mastering extension | Useful for experiments but can hide patch/mix defects. |
| Pan/stereo | Defer | Current product is mono; promise it only with a stereo product profile. |
| Portamento | Defer | Rare in reference songs and not required for stability. |
| FM polyphony/chords | Remove from stable profile | Six explicit lanes are enough; current product songs are effectively monophonic per lane. |
| FM3 split/operator parts | Experimental or remove | High ownership/state complexity; no admitted product song requires it. |
| Dual PMD software LFO matrix | Remove from stable runtime | Large callback/state cost for little current musical value. |
| Live TL/feedback/slot automation | Remove from stable profile | Patch-level design is clearer; real-song demand is absent. |
| Random gate/minimum-tail modes | Remove | Nondeterministic and unnecessary. |
| Hardware envelope raw controls | Defer | Shared-state complexity; no current product need established. |
| PMD K/R SSG effects | Remove from product | Hidden channel stealing and duplicate percussion model. |
| Raw YM rhythm controls | Remove from product | Register compatibility without product value. |
| MML V1 | Remove after fixture migration | No catalog content depends on it. |
| `OpnaSequencer`/`OpnaPatterns` | Remove after fixture migration | Second unused product architecture. |
| Stereo resonator/reference output profile | Research-only or remove | Disabled in mono production path. |
| Raw/register/timer/driver commands | Reject | Emulator/compatibility scope, not music-platform scope. |
| PCM/ADPCM/sample formats | Reject | Conflicts with procedural, asset-free direction. |

## Incremental implementation plan

This sequence is intentionally migration-first. Do not delete a capability until retained songs have stopped depending on it.

### Phase 0 - Record the audible baseline

Actions:

1. Record exact app settings, sample rate, channel layout, gains, filtering, oversampling, and mastering used by Bad Apple and Rin.
2. Select short listening passages that expose FM bass, FM lead, dense FM harmony, SSG tone, SSG envelope, noise, rhythm transients, and a loop boundary.
3. Record current callback timing and underrun behavior on the slowest supported Android device.
4. List every command/event actually used by the two catalog songs and the research logo separately.
5. Classify every test-only/research-only caller before deletion.

Why: cleanup needs a musical and operational reference. The goal is not sample equality; it is preventing accidental loss of the qualities already judged good.

Exit gate:

- a repeatable listening checklist exists;
- product-song feature usage is mechanically inventoried;
- callback timing and playback lifecycle failures have a baseline;
- research content is explicitly separated from shipping content.

### Phase 1 - Freeze the product contract

Actions:

1. Publish the Stable Sound Profile v1 command and rendering contract from this plan as the only supported target.
2. Mark all other MML commands experimental/deprecated; stop adding commands during productization.
3. Define one canonical FM patch structure and one SSG envelope representation; select its sole timing basis through the Phase 2 listening migration.
4. Define one common-code `ProductAudioProfile` concept for sample rate, mono/stereo, oversampling, filter, headroom, and mastering choices.
5. Define the sequential transport/reset/lifecycle contract.

Why: deletion is safe only when "supported" has a concrete meaning. This also gives future compositions a stable target.

Exit gate:

- every parser command and runtime event is labelled stable, migration-only, experimental, or delete;
- the two catalog songs can be expressed by the stable profile, or each exception has an explicit migration decision;
- no platform file owns synthesis policy in the intended design.

### Phase 2 - Normalize current songs and registration

Actions:

1. Convert Bad Apple and Rin to the final stable syntax without changing their intended arrangement.
2. Audibly migrate Bad Apple's tempo-clocked SSG envelope to the proposed fixed-time basis; retain tempo-clocked timing instead if the conversion loses the intended articulation.
3. Treat the logo song as research content; do not let it define product features.
4. Move the V1 migration fixture out of the runtime song bank or rewrite it as a small isolated compiler fixture.
5. Replace Senbonzakura-facing names with truthful Bad Apple names while preserving the old persisted ID through a narrow migration alias.
6. Replace eager compilation of every bank entry with one cached compiled result per selected song.
7. Finish parse, compile, validation, timeline construction, and error reporting before the audio thread starts; make cache initialization safe for simultaneous requests.
8. Cache immutable timelines by song, actual sample rate, and relevant product-profile fields; never cache a mutable player or synth, and do not rebuild for a volume change.
9. Apply user/song playback volume as the post-master runtime scalar defined by the profile.
10. Reduce song registration to one declarative record.

Why: retained content must stop anchoring compatibility scaffolding before that scaffolding is removed. Caching also makes preview/start behavior predictable.

Exit gate:

- adding a song requires source, patch lookup, metadata, and one registry entry only;
- changing volume performs no parse, compile, sort, or timeline reconstruction;
- research songs are not eagerly prepared by the shipping catalog;
- both catalog songs play and loop through the one path.

### Phase 3 - Make playback operationally robust

Actions:

1. Give natural completion, error exits, explicit stop, restart, and alarm cancellation one cleanup path.
2. Make thread state and "is playing" state converge on every exit.
3. Hold/release wake locks according to actual playback lifetime rather than a fixed 30-second preview timeout.
4. State that rendering is contiguous; replace accidental large-offset render-forward behavior with an explicit restart/seek decision.
5. Drain every requested PCM chunk completely: handle partial positive, zero, negative, interrupted, and shutdown `AudioTrack.write` results without dropping frames or advancing transport past unwritten samples.
6. Move oversampling, filter, EQ, and mastering selection from Android into the common product profile.
7. Keep Android as a PCM presenter and lifecycle adapter.

Why: a beautiful synth that leaks state, loses background reliability when a 30-second wake lock expires, drops partially written samples, or cannot restart reliably is not a usable alarm/music platform.

Exit gate:

- repeated start/stop/restart works without stale thread or synth state;
- natural end releases the same resources as stop;
- a 30+ minute loop has no wake-lock lapse, state leak, or underrun;
- platform code does not select synth quality or mastering behavior.

### Phase 4 - Remove the duplicate playback architecture

Actions:

1. Convert any valuable `OpnaPatterns` listening motifs into small MML2/original compiled fixtures.
2. Remove production calls and compatibility render overloads for `OpnaSequencer`.
3. Remove `OpnaSequencer`, `OpnaPatterns`, `Scale`, and `NoteLen` when no product caller remains.
4. Remove the ignored synth parameter from `MmlArrangementScheduler`.
5. Collapse redundant single-value routing/kind wrappers where they add no product meaning.
6. Reduce `OpnaLikeSynthesizer` to one product render entry point plus clearly separate offline diagnostics.

Why: one scheduler and one dispatch path halve the number of places where timing, note-off, reset, and new event behavior can diverge.

Exit gate:

- the app and catalog have exactly one route to rendered music;
- the synth has one documented callback entry point;
- no test is the sole reason a second architecture exists.

### Phase 5 - Remove the compatibility language/runtime

Actions:

1. Remove MML V1 after its last fixture is migrated or archived.
2. Reject deferred syntax clearly instead of silently supporting fragments.
3. Remove FM3 logical parts, pooled FM polyphony, random gate modes, live operator controls, and the dual software-LFO matrix from the stable parser/compiler/runtime.
4. Move hardware LFO out of the default parser/runtime state; admit the small globally coupled form later only through the feature-admission rule.
5. Remove inactive performance-state preparation and render only the six FM plus three SSG product parts.
6. After event removal, simplify `CompiledOpnaSong`, timeline conversion, and dispatch arrays.
7. Do not redesign the IR before this pruning; let the retained event vocabulary determine the smaller structure.

Why: this is the largest maintainability and callback-state reduction. Doing it after song normalization avoids designing a "clean" representation for unwanted features.

Exit gate:

- the stable compiler has one dialect and one command meaning for each operation;
- runtime event kinds correspond to audible product behavior, not historical compatibility;
- dormant FM3/LFO parts do no callback work;
- compiled playback remains allocation-free.

### Phase 6 - Consolidate rhythm

Actions:

1. Choose `Ym2608RhythmUnit` or a renamed equivalent as the single rhythm owner.
2. Route the stable R-lane hits and compile-time rhythm patterns to it.
3. Define master level, per-voice balance, choke, retrigger, and reset once.
4. Remove legacy direct drum state and `PmdSsgEffectUnit` from production.
5. Tune the six procedural voices as an ensemble, not as isolated effects.

Why: the reference songs demonstrate that rhythm-pattern reuse matters, while neither ROM/sample emulation nor PMD channel stealing does. One route provides the musical benefit with predictable cost.

Exit gate:

- exactly one drum generator/mixer domain renders per block;
- existing product rhythm passages retain impact and timing;
- no percussion command steals an SSG music channel;
- a rhythm pattern expands entirely before playback.

### Phase 7 - Canonicalize patches and output sound

Actions:

1. Convert retained legacy float ADSR patches to canonical OPN-rate definitions.
2. Curate a small bank for useful roles: basses, leads, bells/plucks, pads/brass, metallic color, and SSG presets.
3. Normalize patch loudness so ordinary arrangements do not need corrective per-song EQ.
4. Select oversampling per quality profile from measured CPU/listening results; do not enable 2x on 16 dormant voices.
5. Choose one default filter/headroom/soft-clip chain through blind level-matched listening.
6. Remove `#EQ` from core MML. Retain it only as a named optional mastering extension if it proves musically necessary after patch normalization.
7. Remove disabled output experiments unless a named research task owns them.

Why: "good sounding" comes from predictable patches, gain staging, alias control, and transient behavior more than from compatibility breadth.

Exit gate:

- no retained patch needs the legacy ADSR compatibility path;
- representative arrangements remain clear without mandatory song-specific EQ;
- dense playback has safe headroom without obvious pumping or harsh clipping;
- the slowest target keeps at least 50% render-time headroom at the chosen buffer size.

### Phase 8 - Prove composition is now ordinary

Actions:

1. Write one short original conformance song that touches every stable command.
2. Write one complete original song using only the stable profile.
3. Add a compact MML reference with examples and exact tie/gate/loop semantics.
4. Add compiler output useful to a composer: track lengths, loop alignment, events, patches, and diagnostics.
5. Fix only authoring friction revealed by these songs; do not pre-emptively restore PMD features.

Why: a platform is stable when it supports composing, not merely when its internal tests pass. Original material also protects the clean-room boundary.

Exit gate:

- the complete song requires no engine change or experimental command;
- source errors point to useful line/column locations;
- preview/restart/edit/listen is fast enough to use routinely;
- adding the song is a registry/content change, not an architecture change.

### Phase 9 - Declare the surface stable

Actions:

1. Version the stable MML contract and reject undocumented behavior.
2. Keep experimental features outside the default parser/runtime path.
3. Document the feature-admission rule below.
4. Remove obsolete tests with removed features; keep a small contract suite.
5. Record final CPU, memory, lifecycle, and listening results in the engine brief.

Exit gate:

- the app can add songs without sound-engine modification;
- the stable command reference matches the implementation;
- long playback and repeated lifecycle use are reliable;
- the default runtime contains no unowned experimental subsystem.

## Listening and operational acceptance

Bit-exact output is explicitly not an acceptance criterion. The release review should use level-matched listening plus runtime measurements.

### Listening passages

- FM bass: pitch stability, envelope punch, no low-end mud.
- FM lead: smooth sustained tone, controlled high-frequency aliasing, useful vibrato.
- FM bell/pluck: clear transient and natural decay without zipper noise.
- Dense six-FM section: separation, headroom, no harsh buildup.
- SSG square melody: stable pitch and non-fatiguing level.
- SSG noise/mixed voice: shared noise behavior remains intentional.
- SSG envelope: attack/decay/release are repeatable across tempo changes.
- Rhythm solo: each role is identifiable and transients do not collapse the master.
- Full mix: melody remains readable over rhythm and SSG.
- Tie and loop boundaries: no unintended click, retrigger, gap, or state jump.

Listen on at least neutral headphones, ordinary earbuds, and a phone speaker. A problem that appears only after matching loudness is still a problem; louder is not automatically better.

### Operational passages

- start, stop, immediate restart, and rapid repeated preview;
- natural song end;
- loop for at least 30 minutes;
- foreground/background lifecycle transitions appropriate to the app;
- volume changes without parse, compile, or timeline rebuild;
- malformed source and unsupported command diagnostics;
- slowest supported device under the chosen buffer and quality profile.

### Runtime budgets

- zero allocation in the render callback after preparation;
- no locks, parsing, collection growth, sorting, file I/O, or logging in the callback;
- no underruns during the long-loop scenario;
- p95 render time below 50% of the output buffer duration on the slowest supported target;
- deterministic reset and loop state;
- memory proportional to compiled song events, not historical maximum feature counts.

## Testing policy

The user has correctly deprioritized passing every existing test and bit accuracy. Tests should serve the stable decisions, not prevent cleanup.

Keep a small suite for:

- parser diagnostics and compiler limits;
- note/rest/length/tempo conversion;
- tie, gate, finite-repeat, whole-song restart, and external loop semantics;
- deterministic reset/restart;
- allocation-free render smoke checks;
- shared SSG state rules;
- rhythm trigger/choke/reset behavior;
- pitch/envelope sanity ranges;
- song registry compilation.

Delete or rewrite tests whose only purpose is preserving MML V1, the old sequencer, FM3 compatibility, dual-LFO edge cases, raw rhythm control, or other intentionally removed behavior. Listening remains the authority for timbre and mix quality.

## Clean-room rules

1. Use the supplied manuals and dumps only to identify observable behavior, musical workload, and useful terminology for the engine.
2. Do not copy PMDWin/FMGEN/ymfm implementation code, lookup tables, algorithms expressed as code, binary formats, or API structure merely for compatibility.
3. Do not add the supplied external song dumps or their voice tables to the product. Existing transcriptions are a separate content-provenance and rights decision: retain them only if authorized, otherwise replace them with original material. Their presence never permits copying implementation code.
4. Create original micro-fixtures and an original full song for acceptance.
5. Keep procedural rhythm/noise generation original and asset-free.
6. Document independent design decisions in ordinary musical terms: pitch, envelope, feedback, modulation, gating, mixing, and lifecycle.
7. Treat DLL/driver manuals as boundary evidence, not as a mandate to reproduce their public API.

## Feature-admission rule after stabilization

A new sound or MML feature is admitted only when all of the following are true:

1. a real original composition has a specific musical need;
2. the need cannot be expressed cleanly with the stable profile;
3. the smallest proposed feature has bounded parser, compiler, runtime, reset, and CPU behavior;
4. it does not require a second playback architecture or platform-owned music logic;
5. it includes an original listening fixture and documentation;
6. its long-term owner is clear;
7. its value is greater than its permanent event/state/test surface.

"PMD/MUCOM supports it" is evidence that an idea exists, not sufficient reason to add it.

## Expected end state

```text
Original MML2 source
    -> one bounded parser/compiler
    -> immutable authored event program
    -> cached sample-rate/profile-specific playback timeline
    -> one sequential player
    -> six FM + three SSG + one procedural rhythm unit
    -> one common product mastering profile
    -> caller-owned mono PCM buffer
    -> dumb platform presenter
```

At that point the sound system is no longer a collection of compatibility experiments. It is a small product platform with a clear musical identity. New songs can use the stable vocabulary freely; experiments can still happen, but they must earn entry into the product path rather than accumulating there by default.

## Recommended first implementation slice

When code changes begin, the safest first slice is Phases 0-3 only:

1. establish listening/CPU/lifecycle baselines;
2. freeze and document the stable command set;
3. normalize song registration and cache compiled timelines;
4. make Android streaming lifecycle reliable and move audio policy into common code.

That slice improves usability and clarifies the boundary without yet deleting major DSP/compiler systems. Once both catalog songs play through that frozen boundary, the duplicate sequencer, V1 compiler, extra percussion routes, and advanced PMD state can be removed in evidence-backed steps.
