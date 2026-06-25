---
name: palette-index-rendering
description: Enforce the core color law. Use for renderer commands, palette code, platform canvas wrappers, RAMDAC caching, pixel buffers, glyph rendering, and rasterizer output.
---

# Palette Index Rendering

## Purpose

Keep the core renderer platform-agnostic and PC-98 constrained.

Core color is not ARGB. Core color is a palette index.

## Color Law

Core engine color:

```text
0..15
```

The core engine never manipulates native platform colors.

The platform wrapper resolves palette indices into native colors using a cached 16-entry native color table.

## Trigger When

Use this skill when code touches:

- draw commands
- renderer APIs
- pixel buffers
- palette definitions
- platform canvas adapters
- RAMDAC caching
- glyph rendering
- vector rasterization
- background clears
- UI borders
- hover / pressed states

## Required Rules

In `commonMain`:

- draw APIs accept palette indices
- pixel buffers store palette indices or packed palette-index data
- no `ARGB`
- no `RGBA`
- no Android `Color`
- no platform paint types
- no native color literals
- no anti-aliased intermediate colors

In platform wrappers:

- native color conversion is allowed
- conversion must use cached palette table
- no per-pixel color math during draw if a cached lookup can be used
- byte order is platform-specific and must not leak into core

## Safe Pattern

Core:

```kotlin
renderer.drawRect(x, y, w, h, PaletteIndices.BG)
```

Platform:

```kotlin
val nativeColor = nativePaletteCache[index and 0x0F]
```

## Forbidden Pattern

```kotlin
renderer.drawRect(x, y, w, h, 0xFF000000.toInt())
```

```kotlin
import android.graphics.Color
```

## Output Format

```text
COLOR LAW CHECK:
Core color representation:
Native color conversion location:
Palette cache:
Platform leakage:
Result: PASS / FAIL
```
