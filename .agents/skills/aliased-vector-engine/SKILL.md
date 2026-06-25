---
name: aliased-vector-engine
description: Build or review the procedural aliased vector-to-pixel pipeline. Use for hardcoded Kotlin vector commands, Bezier curves, De Casteljau subdivision, PC-98 pixel art generation, rasterization, fills, strokes, and no-asset visual generation.
---

# Aliased Vector Engine

## Purpose

Convert symbolic vector intent into real constrained pixel art.

This engine uses hardcoded Kotlin vector commands, not runtime SVG/XML files.

The output is aliased, palette-indexed, integer-snapped pixel art.

## Trigger When

Use this skill when code touches:

- vector primitives
- Bezier curves
- De Casteljau subdivision
- line rasterization
- polygon fills
- stroke rendering
- pixel art generation
- Kotlin-SVG-like hardcoded commands
- PC-98 ornamental UI shapes
- icons / decorations / borders generated from math

## Asset Law

Forbidden:

- runtime SVG parsing
- XML parsing
- PNG fallback
- external vector files
- external image files
- TTF fonts
- anti-aliased raster output

Allowed:

- Kotlin-coded vector command arrays
- compact mathematical shape definitions
- procedural generation
- palette-index fills/strokes
- deterministic test artifacts

## Raster Law

Final output must be:

- integer-snapped
- aliased
- palette-indexed
- deterministic
- free of intermediate platform colors

No anti-aliasing.

No subpixel final draw calls.

## Bezier Law

For Bezier curves:

1. Use De Casteljau subdivision or another explicitly approved deterministic subdivision rule.
2. Subdivision tolerance must be named and derived from pixel/grid law.
3. Emit integer-snapped line segments.
4. Rasterize through the engine line/pixel primitives.

## Fill Law

Fill rules must be explicit:

- winding or even-odd, chosen per engine law
- integer scanline behavior documented
- no platform path fill API in core

## Output Format

```text
VECTOR ENGINE CHECK:
Input representation:
Subdivision rule:
Integer snap location:
Palette index output:
Asset leakage:
Result: PASS / FAIL
```
