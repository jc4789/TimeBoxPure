# Sound Engine Stabilization Plan

## Decision

The concern is valid, but the clean-room FM/SSG core is not the main problem. The useful center already exists: compiled songs, deterministic chip state, allocation-free rendering, explicit mixing, and a mono platform stream. Complexity has accumulated around that center through a second sequencer, PMD research features, one-option wrappers, repeated setup work, and product/test render modes sharing one large facade.

Stabilize by narrowing the product, not by rewriting the synth.

## Stable target

```text
MML2 
            |
            v
immutable tick-domain song program
            |
            v
cached 48 kHz runtime timeline -> one cursor player
            |
            v
OPNA-like core -> mono mix/output stage -> platform PCM presenter
```

The product contract is:

- Six logical FM parts with four operators, eight algorithms, feedback, OPN-rate envelopes, detune/DT2, patch changes, level, gate, ties, and pitch slides.
- Three SSG parts with tone, shared noise, compact envelopes, and deterministic gating.
- Six named procedural rhythm voices with combined hits and explicit master/per-voice control.
- A fixed 48 kHz, mono, caller-buffer render path with no hot-loop allocation.
- One immutable compiled song format and one runtime dispatcher. MML is a frontend, not a runtime dependency.
- Song-local patches plus a small curated shared bank. Historical operator order is converted only at the import boundary.

## Scope decisions

| Area | Decision |
|---|---|
| FM phase, algorithms, envelopes, pitch | Keep and refine; musical correctness matters more than bit identity. |
| Chip, performance, mixer, mastering ownership | Keep the separation; expose it through a smaller product facade. |
| `CompiledOpnaSong` -> `CompiledOpnaTimeline` -> player | Keep the two domains, rename/clarify them, cache the runtime timeline, and strip authoring diagnostics from playback arrays. |
| `OpnaSequencer`, `OpnaPatterns`, legacy note-length/scale path | Remove from production. Custom arrangements must compile to the canonical song program. |
| MML dialects | Make MML2 the product contract. Isolate v1/import compatibility and fail clearly on unsupported commands. |
| Legacy float ADSR patches | Convert wanted patches to OPN-rate definitions, then retire the compatibility envelope path. |
| FM3 split mode, dual PMD LFOs, raw slot controls | Keep only when an admitted song demonstrates a musical need; otherwise move to research/import code. |
| Rhythm implementations | Present one product rhythm contract. Preserve separate generator state only where simultaneous semantics require it; remove unused domains after catalog verification. |
| Stereo, resonator, raw-core stages, alternate board profile | Move outside the product facade. Production remains intentional mono with one tuned balance. |
| Per-song EQ | Allow as light arrangement mastering, never as a repair for a bad patch, envelope, or mix law. |
| PMD binaries, ADPCM/PCM assets, timers, bus waits, ROM emulation | Out of scope. They do not improve the procedural product and conflict with the asset law. |

## Work plan

### R0 — Establish the listening baseline

- Use short, repeatable excerpts from Bad Apple, Rin to Shite, and one new reference arrangement covering lead, bass, dense FM, SSG, and rhythm.
- Record audible problems in plain language and capture peak level, clipping count, render cost, and underruns.
- Freeze the current output only as a comparison point. Existing tests and bit traces are diagnostic evidence, not the sound-quality target.

Exit: the team can identify whether a later change improved sound, reduced cost, or merely changed it.

### R1 — Make one production path

- Retire the parallel `OpnaSequencer` dispatcher and its procedural motif stack.
- Collapse `SongKind`, `SongPlayback`, `ArrangementRouting`, redundant arrangement metadata, and the scheduler overload that ignores its synth argument.
- Stop eagerly compiling excluded LOGO/migration fixtures in the production bank.
- Preserve persisted song IDs while removing misleading aliases from new authoring APIs.

Exit: every catalog song and custom arrangement reaches the same compiled player.

### R2 — Make setup cheap and runtime small

- Cache one canonical 48 kHz runtime timeline per song.
- Apply user volume as runtime pre-master gain instead of cloning the tick program and rebuilding/sorting the timeline.
- Keep source positions and rich diagnostics in compiler results; keep only dispatch fields in the runtime timeline.
- Give loop/reset one explicit operation that resets chip and performance state without accidentally erasing caller-owned monitoring state.

Exit: starting or changing volume does not recompile or rebuild a song, and replay is click-free and deterministic.

### R3 — Shrink the synth facade

- Separate the current large synthesizer into a chip core, compiled-event dispatcher, mono product renderer, and optional offline diagnostics facade.
- Keep Android responsible only for thread/lifecycle, wake lock, PCM16 streaming, and underrun reporting.
- Move stereo, raw checkpoints, snapshots, and research profiles out of the product-facing API.

Exit: the product renderer has a small lifecycle: create, reset, render, stop.

### R4 — Remove dormant hot-loop work

- Track active/sounding FM voices and skip voices whose envelopes and modulation are truly dormant.
- Prepare PMD/LFO state only for active or explicitly free-running parts.
- Keep the fixed polyphony pool for authored chords, but isolate its allocator from the six logical OPNA parts.
- Measure before and after; do not trade envelope tails, free-running LFO phase, or FM3 ownership for a benchmark win.

Exit: dense songs sound unchanged or better while silence and sparse passages cost materially less.

### R5 — Tune one coherent instrument

- Tune FM operator scaling, feedback, envelopes, patch levels, SSG/FM/rhythm balance, headroom, and soft protection in that order.
- Choose one product oversampling policy from listening and CPU evidence; do not enable 2x blindly on every pooled voice.
- Keep mastering small: stable bus gains, optional authored peak EQ, one output filter if it audibly helps, and transparent overload protection.
- Judge changes across several patches and arrangements so one song cannot bend the entire engine around itself.

Exit: no recurring harsh 2–3 kHz edge, muddy bass, missing midrange, intermittent resonance, clicks, or routine limiter dependence in the listening set.

### R6 — Make adding music ordinary

- Define a compact song package: metadata, MML2 or Kotlin arrangement source, song-local patches, mix spec, and loop point.
- Add a setup-time Kotlin arrangement builder for non-MML transcriptions; it emits the same tick-domain program and adds no second runtime.
- Keep authoring conveniences such as named patches, sections, grouped parts, macros, loops, alternate endings, and named drum patterns in the compiler.
- Require unsupported historical commands to fail with a useful source location instead of silently disappearing.

Exit: adding a normal song changes only its song package and catalog entry—not the synth, player, or platform streamer.

## Ready definition

The engine leaves development mode when:

- Bad Apple and Rin to Shite still play correctly, and a third independently authored arrangement can be added without engine changes.
- A non-MML transcription can use the Kotlin builder and the same player.
- The audio callback allocates nothing, sparse playback skips dormant work, and Android streams without routine underruns.
- Replay/loop boundaries do not click; ties do not retrigger; patch, LFO, noise, pan, and envelope state reset predictably.
- Normal arrangements stay inside mix headroom without using master EQ or gain changes to hide synthesis errors.
- Listening approval across multiple speakers/headphones is recorded. Tests protect these contracts but do not substitute for listening or demand bit accuracy.

## Clean-room evidence basis

- `MUCOM88_REF.txt`: compact musical command set, four-operator patch fields, compile-time loops/macros, SSG controls, and the separation of driver from output.
- `pc 88 va 12.txt`: OPNA-shaped limits and behavioral register laws; used as specification, not implementation.
- `TH4 Bad Apple!! MML Dump.txt` and `PC-98 Eternal Shrine Maiden.txt`: real demand is custom patches, five FM parts, two SSG parts, rhythm, articulation, dense patterns, sections, and loops—not full hardware emulation.
- `DLLInfop.txt` and `PMDWin.txt`: a small caller-buffer lifecycle is sufficient; their broad DLL, file, PCM, compatibility, and timing surfaces are not product requirements. Their bug history makes reset, ties, modulation bounds, and gain staging important.
- `PSGEDATA.DOC`: timed tone/noise/envelope steps are a useful procedural effect model; its binary container is not needed.

All implementation must remain independently derived. References define observable behavior and useful scope only; no emulator or driver code is to be copied.
