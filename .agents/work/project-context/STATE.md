# Objective
Read the requested project references and confirm shared understanding with a compact summary.

# Constraints
- Read `.agents/AGENTS.md`, `ENGINE_BRIEF.md`, and every skill instruction under `.agents/skills` completely.
- Make no production-code changes.
- Do not run or create tests.

# Plan
- [x] Read all requested references without truncation.
- [x] Extract the governing architecture, workflow, and current-state constraints.
- [x] Return a concise alignment summary.

# Confirmed
- Context Drift Guard requires this compact uncommitted ledger for multi-step/context-heavy work.
- The engine is a deterministic, allocation-conscious Kotlin Multiplatform custom engine: procedural systems, palette-index rendering, canonical glyph-cell layout, and dumb platform terminals.
- Sound is split between a clean-room YM2608-inspired physical engine and a separate clean-room PMD-inspired MML/performance layer, with one-way player-to-synth ownership.
- The existing production baseline must be preserved unless an exact change is authorized and supported by concrete evidence.
- Production edits require a narrow file allowlist and complete line-by-line reading of every relevant file and caller path.
- Tests may not be created or run without explicit user permission; automated success cannot establish acoustic correctness.
- No production code was changed and no tests were run.

# Rejected

# Unverified
- None for the requested context intake.

# Next
Return the alignment summary, then await a concrete coding task.
