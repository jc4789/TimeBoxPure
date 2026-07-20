# Objective
Surgically make the production call direction `CompiledOpnaPlayer -> OpnaLikeSynthesizer/physical core` one-way, using the existing player and synth only.

# Constraints
- User scope overrides `soundaudit.md`; the audit is evidence, not blanket authorization.
- No alternate player, adapter backend, parallel pipeline, verification architecture, tests, or unrelated cleanup.
- Preserve sound semantics, timing, output policy, and the accepted baseline.
- Read every relevant production file and caller completely before architectural or behavioral claims.
- Define a production-file allowlist before editing.

# Plan
- [x] Read current production path and implicated files completely.
- [x] Record exact call direction, state owners, gates, handlers, and active legacy/debug paths.
- [x] Define the smallest production-file allowlist and splice.
- [x] Implement the one-way production dependency.
- [x] Inspect diff and perform permitted non-test verification.

# Confirmed
- `soundaudit.md` was read completely; its historical line claims must be checked against the current tree.
- `.agents/AGENTS.md` forbids tests without explicit user permission and requires smallest production-only change.
- Working tree initially contains only untracked `.agents/work/` task ledgers.
- Production is gated by `ArrangementRouting.MML_LOGICAL_TRACKS` in `SoundPreviewPlayer`, then calls `MmlArrangementScheduler.createPlayer` -> `CompiledOpnaPlayer.render` -> `OpnaLikeSynthesizer.renderTimelineSegment`.
- No current synth method accepts or calls `CompiledOpnaPlayer`; the historical object-call cycle is already removed.
- Current physical PMS/AMS is still copied from `PmdPerformanceState` into `PmdModulationFrame` and consumed in `Fm4OpVoice.setLfoFrame`.
- Absolute FM TL/detune/feedback, shared SSG registers, and rhythm level/pan already live in physical objects.
- Retained `OpnaSequencer`, raw/stereo player stages, and snapshot helpers remain active code but are not the Android catalog route.
- Production-file allowlist: `CompiledOpnaPlayer.kt`, `PmdPerformanceState.kt`, `OpnaDriverFrames.kt`, `OpnaLikeSynthesizer.kt`, `Fm4OpVoice.kt`.
- `Fm4OpVoice` now owns current channel PMS/AMS; `PmdModulationFrame` no longer carries those physical values.
- Player `HW_LFO_PMS/AMS` dispatch retains PMD mirrors and writes every currently bound physical FM voice through the existing synthesizer.
- Normal, pooled-polyphonic, FM3, direct synth, and retained direct-sequencer note paths resynchronize the physical sensitivity without adding state or an alternate pipeline.
- Compilation succeeded for common metadata, Android shared engine, and app Kotlin using the required JBR.
- The existing OPNA hot-path audit passed as a build dependency; no tests were created or run.
- Final search found no `CompiledOpnaPlayer` reference under the OPNA package, no PMS/AMS fields in `OpnaDriverFrames`, and no forbidden commonMain symbols in changed files.
- Final diff contains exactly the five allowlisted production files; each has fewer than 1024 added lines and no binary/debug/generated file is present.

# Rejected
- Treating all audit repair suggestions as authorized: user explicitly narrowed scope.
- Adding a replacement player/backend/adapter/pipeline: the existing player and synth already provide the required route.
- Moving SSG/rhythm/TL/detune/feedback state in this splice: current code already owns those values physically.

# Unverified
- Acoustic equivalence; no human listening is available in this task.

# Next
Report the completed ownership splice and the remaining human-listening limitation.
