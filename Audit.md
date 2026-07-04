# FULL AUDIT: Graphics Pipeline, Demoscene Systems & Engine Architecture

**Project:** TimeBox (AJATT Productivity Engine)  
**Target:** Kotlin Multiplatform (`commonMain`) — "Carmack but ZUN"  
**Audit Date:** July 4, 2026 (Updated with 64k Demoscene & Vector Direction)  
**Status:** COMPLETE (No code modified — Audit Phase Only)

---

## 1. Executive Summary

A comprehensive line-by-line audit of the graphics pipeline, demoscene effects, procedural math, and UI architecture was conducted across `shared-engine` (`commonMain`) and the Android platform surface view (`Pc98SurfaceView`).

Per project directives, **the target aesthetic is 64k demoscene math** (procedural art, dithered textures, efficient rasterization, compact math-driven animation) combined with classic PC-98 / 東方 (Touhou) aesthetics. Cheesy late-90s sine wave wobbles, spring chains, and lagging particle tails do NOT belong in this engine.

Key findings:
1. **Background Nebula is broken**: It snaps the entire play area to solid flat background colors rather than rendering a procedural dithered spatial nebula/plasma.
2. **Yin-Yang Core is overcomplicated & has unwanted tail**: It renders a 4-dot hardcoded "comet tail" and runs a 9,000-iteration per-pixel CPU loop every frame instead of being a clean, static shape rendered via `AliasedVectorLayer`.
3. **`Wave` Oscillators & `IkChain2D` are unwanted bloat**: They represent cheesy modulation systems that clutter `commonMain` and should be purged.
4. **Hot-loop allocations violate engine laws**: `ProceduralMath.kt` allocates `arrayOf(Pair(...))` inside Bresenham circle loops.
5. **Graphics math is fragmented**: Trigonometry and circle rasterization are duplicated across 3 separate files.

---

## 2. Architecture Map of Current Graphics & UI Components

| Component | File Path | Primary Responsibility | Target Action / Audit Verdict |
| :--- | :--- | :--- | :--- |
| **`EngineCanvas`** | [EngineCanvas.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/EngineCanvas.kt) | Platform-agnostic drawing interface (palette index boundary). | **Keep & Maintain**: Clean interface boundary. |
| **`AndroidEngineCanvas`** | `platform/android/AndroidEngineCanvas.kt` | Maps engine palette indices (`0..15`) to Android native `Canvas` paint calls. | **Keep & Maintain**: Dumb terminal wrapper. |
| **`ScaledProceduralRenderer`** | [ScaledProceduralRenderer.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ScaledProceduralRenderer.kt) | High-level renderer wrapping vector operations, typography, and IMGUI buttons. | **Refactor**: Remove allocating wrappers, delegate all vector math directly to `AliasedVectorLayer`. |
| **`AliasedVectorLayer`** | [AliasedVectorLayer.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/AliasedVectorLayer.kt) | Integer-snapped aliased line, circle, arc, and De Casteljau Bezier rasterizer. | **Primary Engine Core**: Use exclusively for static Yin-Yang, geometric shapes, and PC-98 linework. |
| **`NestedTimeboxInstrumentRenderer`** | [NestedTimeboxInstrumentRenderer.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/NestedTimeboxInstrumentRenderer.kt) | 14-layer ZUN-style magic circle, octagram, pentagram, and timer readout. | **Refactor**: Remove comet tail drawing code, simplify Yin-Yang to static vector calls, remove oscillator references. |
| **`MagicCircleDemoscene`** | [MagicCircleDemoscene.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/MagicCircleDemoscene.kt) | Legacy state manager holding `Wave` oscillators, `IkChain2D`, and Perlin noise. | **Simplify/Purge**: Strip `Wave` and `IkChain2D` bloat; keep only pure Perlin noise / nebula state. |
| **`IkChain2D`** | [IkChain2D.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/IkChain2D.kt) | 2D FABRIK constraint solver. | **PURGE**: Unwanted cheesy effect. Delete file and references. |
| **`Wave`** | [Wave.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Wave.kt) | Sine wave oscillator class. | **PURGE**: Unwanted cheesy effect. Delete file and references. |
| **`PerlinNoise`** | [PerlinNoise.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/PerlinNoise.kt) | 1D/2D Perlin noise + 2-octave fbm with generated 256-entry permutation LUT. | **Keep**: Essential for 64k-style dithered nebula/plasma textures and organic rune drift. |
| **`ProceduralMath` & `FastMath`** | [ProceduralMath.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ProceduralMath.kt) | Icon pixel maps, 1024-entry `FastMath` LUT, vertex/tick point generators. | **Fix Law Violation**: Eliminate `arrayOf(Pair)` allocations inside `drawBresenhamCircle`. |
| **`RetroHudComponent`** | [RetroHudComponent.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/RetroHudComponent.kt) | Navigation bar & touch hit-test dispatcher for PC-98 icons. | **Keep & Maintain**: Solid IMGUI component. |

---

## 3. Critical Bugs & Misimplemented Features

### 🐛 Critical Bug 1: Background Nebula is a Broken Full-Screen Flash
- **Location:** [Scenes.kt:L872-L897](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt#L872-L897).
- **Issue:** `nebulaColorIndex` samples 2-octave Perlin fbm at two screen coordinates and returns a single palette index (`BG`, `BG_ALT`, or `PANEL`). `ActiveTimerScene` uses this single index to call `fillRectDither` over the entire screen rectangle.
- **Impact:** Rather than rendering a 64k-style dithered plasma or spatial nebula texture, the screen violently jumps between solid dark colors every ~12 seconds.
- **Correct 64k Fix:** Render a true spatial dithered nebula field using a low-res grid of dither blocks (e.g. $16 \times 16$ or $32 \times 32$ pixel cells sampled via 2D Perlin noise and rendered using `SoftDitherPattern.CHECKERBOARD` / `SPARSE_DOTS`).

### 🐛 Misimplemented Feature 2: Yin-Yang Core has Unwanted Tail & Overcomplicated Pixel Loop
- **Location:** [NestedTimeboxInstrumentRenderer.kt:L291-L317 & L370-L403](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/NestedTimeboxInstrumentRenderer.kt#L291-L317).
- **Issues:**
  1. Lines 291–317 draw 4 hardcoded trailing dots ("comet tail") behind the core. The user confirmed this tail is unwanted and should not exist.
  2. `drawSolidYinYang` iterates over 9,216 pixels ($96 \times 96$ square) on the CPU every frame, running floating-point rotation math (`localX`, `localY`) and distance checks for every pixel to fill the core.
- **Correct Fix:** Remove the tail completely. Render a **static, crisp Yin-Yang core** using pure vector primitives from `AliasedVectorLayer` (`drawAliasedCircle`, `drawAliasedFilledCircle`, `drawRotatedBresenhamHalfCircle`), eliminating the per-pixel CPU loop.

### 🗑️ Misimplemented Feature 3: Cheesy `Wave` Oscillators & `IkChain2D` Bloat
- **Location:** [Wave.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Wave.kt), [IkChain2D.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/IkChain2D.kt), and [MagicCircleDemoscene.kt](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/MagicCircleDemoscene.kt).
- **Issue:** These classes add late-90s sine wave wobbles and spring chains. They are updated every frame in `ActiveTimerScene` but produce no visual value and clutter the engine.
- **Correct Fix:** Purge `Wave.kt` and `IkChain2D.kt`. Simplify `MagicCircleDemoscene` to focus strictly on Perlin noise generation for 64k dithered nebula textures and subtle rune drift.

---

## 4. Engine Law Violations & Performance Bottlenecks

### 🚫 Heap Allocations in Hot Paths ("No Hot Loop Allocations" Law)
1. **`ProceduralMath.drawBresenhamCircle`** ([ProceduralMath.kt:L332-L341](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ProceduralMath.kt#L332-L341)):
   Inside the inner octant plotting function `plotPoints(px, py)`, an array of `Pair` objects is allocated **on every octant iteration step**:
   ```kotlin
   val points = arrayOf(
       Pair(xc + px, yc + py),
       Pair(xc - px, yc + py),
       ...
   )
   ```
   This generates dozens of Garbage Collection allocations per circle draw call.

2. **`ProceduralMath.getStarVertices` & `getTickPoints`** ([ProceduralMath.kt:L272-L293](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ProceduralMath.kt#L272-L293)):
   Constructs `List<Point2D>` and `List<Pair<Point2D, Point2D>>` on heap builders.

---

## 5. Architectural Fragmentation & Duplication Analysis

1. **Duplicated Circle Rasterizer Code:**
   - `AliasedVectorLayer.drawAliasedCircle` (Allocation-free integer Bresenham).
   - `ProceduralMath.drawBresenhamCircle` (Allocates `arrayOf(Pair)` objects).
   - `ScaledProceduralRenderer.drawBresenhamCircle` (Delegates to `ProceduralMath`, causing allocations).

2. **Mixed Trigonometry APIs:**
   - `FastMath` provides a 1024-entry lookup table (`fastCos`, `fastSin`, `degreesToIdx`).
   - `NestedTimeboxInstrumentRenderer` defines separate private `cosDeg` / `sinDeg` wrappers around standard `kotlin.math.cos` / `sin`.
   - `AliasedVectorLayer` uses `FastMath`.

3. **Duplicated Cursor & Input UI Renderers:**
   - `EngineCursorRenderer.kt` was written as a standalone renderer component, but `ActiveTimerScene` in `Scenes.kt` manually implements cursor blinking and dither rectangle rendering.

---

## 6. Recommendations for PC-98 / 64k Demoscene / 東方 Architecture

### 🌌 1. Fix Background Nebula (True 64k Spatial Dithered Plasma)
- Replace full-screen single-color jumps with a **spatial grid of dithered blocks** ($16 \times 16$ or $32 \times 32$ pixel cells).
- Sample `PerlinNoise.fbm` at cell coordinates and map noise levels to PC-98 dither patterns (`SoftDitherPattern.CHECKERBOARD`, `SPARSE_DOTS`, `HORIZONTAL_STRIPES`) using palette indices `BG`, `BG_ALT`, and `PANEL`.
- Runs 100% allocation-free and produces an authentic 64k demoscene procedural background texture.

### ☯️ 2. Clean, Static Vector Yin-Yang (Zero Tail)
- **Remove comet tail drawing loop** ([NestedTimeboxInstrumentRenderer.kt:L291-L317](file:///d:/Programes/TimeBox/shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/NestedTimeboxInstrumentRenderer.kt#L291-L317)).
- Replace `drawSolidYinYang` per-pixel CPU loop with a static, crisp `AliasedVectorLayer` implementation using integer midpoint circle & half-circle rasterization.

### 🧹 3. Purge `Wave` & `IkChain2D` Bloat
- Delete `Wave.kt` and `IkChain2D.kt`.
- Clean up `MagicCircleDemoscene.kt` to retain only Perlin noise generation and time parameters.

### ⚡ 4. Eliminate Hot-Loop Allocations & Unify Math
- Remove `drawBresenhamCircle` from `ProceduralMath.kt`; route all circle drawing to `AliasedVectorLayer`.
- Replace all `kotlin.math.cos`/`sin` calls in rendering routines with `FastMath` 1024-entry LUT.

---
*Audit updated per user specifications. No code changes executed per prompt instructions.*
