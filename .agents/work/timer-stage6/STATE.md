# Objective
Implement timerplan.md stage 6 only.

# Constraints
- Source hierarchy: current user instruction; gemini on overflow; refined Gemini explanation; PMD manual excerpt; timerplan.md.
- Accept the user-confirmed Gemini interpretation. Do not reverify it with outside information.
- One YM2608 Timer B overflow produces one PMD tick.
- Hardware overflow belongs to YM2608. PMD software LFO arithmetic overflow is a separate numeric condition.
- Add no ADPCM part.
- Add or run no tests and no diagnostic machinery.
- Preserve existing user changes in ENGINE_BRIEF.md and timerplan.md.
- Read every involved production file completely before behavioral claims or edits.

# Plan
- [x] Read timerplan.md completely.
- [x] Read all four supplied Shift_JIS references completely.
- [x] Read stage 6 production files and their call paths completely.
- [x] Fix the stage 6 allowlist.
- [x] Implement the smallest production-only change.
- [x] Inspect the diff without running tests or diagnostics.

# Confirmed
- #LFOSpeed Extend is equivalent to initial MXA1 and MXB1 for every existing FM and SSG part.
- #LFOSpeed Normal is equivalent to initial MXA0 and MXB0.
- Later part-local MX, MXA, or MXB commands override the global initial mode.
- #EnvelopeSpeed Extend is equivalent to initial EX1 for every existing SSG part.
- #EnvelopeSpeed Normal is equivalent to initial EX0.
- Later part-local EX commands override the global initial mode.
- Normal software LFO and envelope clocks are tempo dependent and advance by PMD internal ticks.
- Extend software LFO and envelope clocks are tempo independent at approximately 56 Hz.
- The production path is MmlParser -> MmlCompiler -> CompiledOpnaSongBuilder -> CompiledOpnaTimelineFactory -> CompiledOpnaPlayer -> PmdPerformanceState -> PmdSoftwareLfo/PmdSoftwareEnvelope.
- MmlDocument already carries the global envelope mode but does not carry a global software-LFO mode.
- MmlCompiler already emits part-local SOFTWARE_LFO_CLOCK events and SSG_ENVELOPE_MODE events.
- CompiledOpnaSong, CompiledOpnaTimeline, CompiledOpnaPlayer, PmdPerformanceState, PmdSoftwareLfo, and PmdSoftwareEnvelope already carry and apply the required runtime modes.
- The current global envelope event uses Int.MAX_VALUE source order, so an EX command at tick zero is applied before the header event and cannot override it.
- Unreferenced snapshot helpers remain in production source. They are outside stage 6 and will not be changed.
- MmlParser now accepts #LFOSpeed Normal/Extend before the distinct #LFO hardware directive and stores the global initial mode.
- MmlCompiler emits two tick-zero SOFTWARE_LFO_CLOCK events for each existing FM/SSG logical part when Extend is selected.
- FM3 control lane C is excluded; its existing C1-C4 logical parts receive their own initial modes.
- Initial software-LFO and envelope mode events use Int.MIN_VALUE source order, so authored MX/MXA/MXB and EX events at tick zero override them.

# Rejected
- The timerplan.md two-overflows-per-PMD-tick candidate is wrong.
- The unrefined Gemini file's reversed t/T interpretation is superseded by the refined explanation.

# Unverified
- No tests, builds, or diagnostics will be run.

# Allowlist
- shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/mml/MmlParser.kt
- shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/mml/MmlCompiler.kt

# Next
Report completion and the explicitly unrun verification work.
