# Objective
Rewrite `UIscaleOverhaul.md` as a minimal math/layout-logic correction plan based on the reverted current code and the supplied before/after/landscape screenshots.

# Constraints
- Physical display/client width and height are authoritative.
- `U = 16x16 fullwidth glyph cell = sole UI layout unit`.
- Fullwidth-only text remains; halfwidth support must not return.
- Limit production edits to existing commonMain scaling math and the minimum shared layout logic required by the screenshots.
- Do not edit or include Android, Win32, or other platform wrappers in the implementation scope.
- Do not rename APIs, fields, parameters, constants, or types.
- Do not introduce `DisplayGrid`, replace ownership/state structures, or rewrite rendering/input paths.
- Landscape scale correction is the primary failure that the revised plan must address.
- Include exact current bad-code examples and exact replacement shapes.
- Do not implement production code or add tests/diagnostic infrastructure in this task.

# Plan
- [x] Record the user's corrected scope and rejected old-plan mechanisms.
- [x] Compare the supplied before/after Android screenshots and landscape screenshot.
- [x] Re-open only the reverted commonMain scaling/layout code needed to explain those results.
- [x] Replace `UIscaleOverhaul.md` with a minimal, scope-locked implementation guide.
- [x] Inspect the final document for forbidden platform/API/rewrite instructions.

# Confirmed
- The prior implementation was reverted; current production code matches the starting state used when the old guide was written.
- The old guide expanded scope into platform wrappers, API renames, new grid/state structures, and renderer rewrites.
- The post-overhaul landscape result remained practically unchanged even though landscape overscaling is the primary issue.
- The rejected portrait result collapsed Template, Entropy, and Settings layout; those scenes are now explicitly preserved.
- The revised scope excludes `ActiveTimerScene`, `timerRadius`, and all procedural artwork because they are not UI.
- The revised UI scale contract uses a 24-column minimum and 30-row minimum; both supplied reference dimensions select `S=2`.

# Rejected
- Any platform-wrapper edit or platform implementation phase.
- Any API/type/field/parameter rename.
- `DisplayGrid` or replacement resize/state architecture.
- Renderer/input-path rewrite or `drawPhysicalRect` API removal merely to perform this fix.
- Re-deriving or redesigning unrelated scene geometry.

# Unverified
- Post-implementation screenshots; this task rewrites the guide only.

# Next
Hand off the revised guide. Production implementation remains pending and must stay within its two-symbol-group commonMain scope.
