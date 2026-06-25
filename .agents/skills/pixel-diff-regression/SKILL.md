---
name: pixel-diff-regression
description: Verify deterministic renderer output with indexed framebuffer tests and pixel diffs. Use after changing renderer, layout, glyphs, vector rasterization, palette code, UI, or scaling.
---

# Pixel Diff Regression

## Purpose

Make visual correctness measurable.

The renderer emits deterministic indexed pixels. Use that to catch drift.

## Trigger When

Use this skill after changes to:

- renderer
- layout
- glyph rendering
- vector rasterizer
- palette handling
- DPI scaling
- IMGUI widgets
- buttons / borders
- text fields
- platform presentation adapters

## Test Philosophy

Compare engine-level indexed buffers, not platform screenshots first.

Prefer:

- width
- height
- palette-index buffer
- hash
- changed pixel count
- unexpected color count
- off-grid draw count

Avoid relying only on visual screenshots.

## Required Checks

1. Render known test scenes.
2. Compare indexed framebuffer to expected result.
3. Check no pixel uses invalid palette index.
4. Check no final draw uses subpixel coordinates.
5. Check dimensions match expected logical output.
6. Check known UI anchors remain stable.
7. Produce compact summary.

## Suggested Report

```text
PIXEL DIFF:
Scene:
Expected hash:
Actual hash:
Changed pixels:
Invalid palette indices:
Max x drift:
Max y drift:
Off-grid draws:
Result: PASS / FAIL
```

## Artifact Rule

Generated PNGs are test artifacts only.

They are not engine assets.

Do not add generated screenshots to runtime resources.
