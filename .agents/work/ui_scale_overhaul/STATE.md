# Objective
Write a decision-complete implementation guide for the UI scale overhaul in `UIscaleOverhaul.md`.

# Constraints
- Physical display/client width and height are authoritative.
- `U = 16x16 fullwidth glyph cell = sole UI layout unit`.
- Fullwidth-only text remains; halfwidth support must not return.
- One integer presentation scale `S` applies to glyphs, geometry, hitboxes, and input inversion.
- No fixed design resolution, device/orientation branch, separate glyph unit, or platform-owned layout.
- Include exact current bad-code examples and exact replacement shapes.
- Do not implement production code or add tests/diagnostic infrastructure in this task.

# Plan
- [x] Re-read governing skills and scaling reference.
- [x] Confirm current scale, text-raster, Android, Win32, and shared-layout paths.
- [x] Write `UIscaleOverhaul.md` with invariants, formulas, examples, ordered implementation phases, and acceptance gates.
- [x] Inspect the document against the request, source evidence, locked arithmetic, and ambiguity gate.

# Confirmed
- `DisplayScalePolicy.deriveScale` currently mixes platform density with width-only logical bounds (`EngineCanvas.kt`).
- `TextRasterScale` caps text source pixels independently of presentation scale (`EngineCanvas.kt`, `ScaledProceduralRenderer.kt`).
- Android and Win32 both pass physical dimensions to the common policy, then expose `physical / scale` as continuous logical bounds.
- Android applies `Canvas.scale(S, S)`; Win32 scales primitives into its physical framebuffer.
- Shared `SceneManager` and `RetroHudComponent` own play-area and HUD layout.

# Rejected
- Preserve Android `S=3`: rejected because it makes canonical fullwidth glyphs 48x48 physical pixels, which the user explicitly rejected.
- Separate glyph unit `G`: rejected because `U` is the glyph cell and sole layout unit.
- Mixed halfwidth/fullwidth metrics: rejected because fullwidth-only is an intentional existing design.
- Fixed target framebuffer/design resolution: rejected because active physical display geometry is authoritative.

# Verified
- Pixel arithmetic: `1080x2400 -> S=2 -> 33x75 -> 24x0` physical-pixel remainder.
- Windows worked arithmetic: `1900x983 -> S=2 -> 59x30 -> 12x23` physical-pixel remainder.
- All referenced production files and the external scaling reference exist.
- Markdown fence count is balanced and the ambiguity-word scan has zero matches.
- No production source, test, dependency, asset, or user-owned modified file was changed.

# Next
Hand off `UIscaleOverhaul.md`; production implementation remains explicitly pending.
