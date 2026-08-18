# UI Scale Overhaul — Minimal Math-Fix Implementation Guide

Status: **scope locked; reverted production code is the starting point; implementation not started**

This document replaces the previous UI scale overhaul guide. The previous guide is rejected because it expanded a scale-math correction into platform work, API renames, new state structures, renderer changes, and broad scene-layout rewrites. The supplied post-overhaul screenshots show that those changes damaged portrait layouts while leaving the primary landscape problem substantially unresolved.

The implementation described here is intentionally small. It corrects two existing commonMain UI calculations and preserves the rest of the application.

## 1. Authoritative evidence

The current production code has been reverted and is the implementation starting point.

Portrait layout baseline that must be preserved:

```text
D:\Programes\TB screenshots\Android before scaling overhaul\Screenshot_20260816-151707.png  Active Timer
D:\Programes\TB screenshots\Android before scaling overhaul\Screenshot_20260816-151712.png  Template
D:\Programes\TB screenshots\Android before scaling overhaul\Screenshot_20260816-151720.png  Entropy
D:\Programes\TB screenshots\Android before scaling overhaul\Screenshot_20260816-151725.png  Settings
```

Rejected previous-overhaul results:

```text
D:\Programes\TB screenshots\Android After scaling overhaul\Screenshot_20260817-184122.png  Active Timer
D:\Programes\TB screenshots\Android After scaling overhaul\Screenshot_20260817-184125.png  Template
D:\Programes\TB screenshots\Android After scaling overhaul\Screenshot_20260817-184128.png  Entropy
D:\Programes\TB screenshots\Android After scaling overhaul\Screenshot_20260817-184132.png  Settings
```

Primary landscape failure:

```text
D:\Programes\TB screenshots\Landscape.png
```

The rejected screenshots are evidence of what not to reproduce. They are not a new visual target.

## 2. Exact production scope

Only these existing commonMain locations are editable:

1. `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/EngineCanvas.kt`
   - constants used by `DisplayScalePolicy.deriveScale`;
   - the body of `DisplayScalePolicy.deriveScale`.
2. `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/RetroHudComponent.kt`
   - the body of `layoutMode` only.
No other production file or symbol is part of this overhaul.

### 2.1 Forbidden scope expansion

Do not:

- edit any platform source set or platform wrapper;
- add a platform implementation phase;
- rename any API, type, object, field, function, parameter, or constant;
- add `DisplayGrid` or any replacement display/state structure;
- rename `logicalWidth`, `logicalHeight`, `setLogicalBounds`, `EngineCanvas.width`, or `EngineCanvas.height`;
- change `SceneManager`;
- change the `EngineCanvas` interface;
- delete or replace `drawPhysicalRect`;
- delete, rename, or redesign `TextRasterScale`;
- change `ScaledProceduralRenderer`, `ProceduralUiPrimitives`, glyph emission, clipping, or input conversion;
- rewrite Template, Template Forge, Entropy, Settings, or Block Overlay layout;
- change `ActiveTimerScene`, `timerRadius`, the timer instrument, or any other procedural artwork;
- replace existing scene percentages with fixed-cell geometry;
- perform a general `U / 8` cleanup;
- add assets, dependencies, diagnostics, snapshot infrastructure, or new tests;
- perform unrelated cleanup because a touched file contains old code.

If an implementation proposes any item above, it is not implementing this guide.

## 3. Existing behavior that must remain

Preserve all of the following:

- `CANONICAL_UI_UNIT = 16` and fullwidth-only glyph behavior;
- the existing `DisplayScalePolicy` object name and `deriveScale(physicalWidth, physicalHeight, platformDensity)` signature;
- the existing logical-width/logical-height flow and current callers;
- the existing outer rendering and input transforms;
- the current `TextRasterScale` behavior;
- the current HUD width, height, button-size, padding, gap, drawing, and hitbox helpers;
- the current Template card sizing and spacing;
- the current Entropy row sizing and pagination;
- the current Settings row sizing and responsive stacking;
- all `ActiveTimerScene` code and all procedural artwork;
- current navigation, state, timer, sound, palette, input, and scrolling behavior.

The before-overhaul portrait screenshots are the reference for these preserved layouts.

## 4. Why the previous plan failed

### 4.1 The minimum row count did not address landscape

The previous guide used 22 minimum rows. For a landscape client close to the supplied 2559×1439 screenshot, that still permits `S = 3`:

```text
floor(1439 / (22 × 16)) = 4
```

Using the actual client height instead of the complete screenshot height can reduce that result to 3, but it does not force 2. This is why the landscape presentation scale remained practically unchanged.

### 4.2 Broad scene rewrites were not scale fixes

The rejected portrait screenshots show the consequences:

- Template cards became much shorter and more cards were packed onto one screen;
- Entropy rows collapsed and left a large unused region;
- Settings controls became over-compressed and top content collided with the system status region.

Those regressions came from replacing working scene layout formulas. This implementation does not touch those formulas.

## 5. Fix 1 — change only `DisplayScalePolicy` math

Target: `EngineCanvas.kt`, inside the existing `DisplayScalePolicy` object.

### 5.1 Required minimum capacity

Use these existing-layout-derived minimums:

```text
minimum columns = 24
minimum rows    = 30
```

They are not a target framebuffer or invented design resolution. They are the minimum number of complete `U` cells required before choosing a larger integer presentation scale.

The 24-column minimum is derived from the current 17-cell reference task text plus the existing Active Timer input geometry:

```text
17U  task text
 1U  two U/2 inner text paddings
2.5U two 5U/4 outer side paddings
2.25U preset badge
0.5U badge gap
-----
23.25U -> 24 complete columns
```

The 30-row minimum comes directly from the rejected landscape UI scale. At `S = 3`, the complete 2559×1439 reference image has only 29 complete `U` rows:

```text
floor(1439 / (16 × 3)) = 29 complete UI rows
```

The supplied landscape result establishes that this 29-row UI presentation is too large. `30` is the smallest complete-row capacity that rejects `S = 3`; it is not a target resolution and does not change non-UI artwork.

### 5.2 Exact constant edit

Keep the existing constant name and add only the matching height constant:

```kotlin
const val MIN_SAFE_LOGICAL_WIDTH = CANONICAL_UI_UNIT * 24f
const val MIN_SAFE_LOGICAL_HEIGHT = CANONICAL_UI_UNIT * 30f
```

Do not rename or remove `MIN_SAFE_LOGICAL_WIDTH`, `MAX_SAFE_LOGICAL_WIDTH`, `MIN_SCALE`, or the `deriveScale` parameters.

### 5.3 Exact `deriveScale` replacement body

Keep the function declaration exactly as it is. Replace only its body with:

```kotlin
fun deriveScale(
    physicalWidth: Float,
    physicalHeight: Float,
    platformDensity: Float
): Int {
    if (!physicalWidth.isFinite() ||
        !physicalHeight.isFinite() ||
        physicalWidth <= 0f ||
        physicalHeight <= 0f
    ) {
        return MIN_SCALE
    }

    val widthScale = (physicalWidth / MIN_SAFE_LOGICAL_WIDTH).toInt()
    val heightScale = (physicalHeight / MIN_SAFE_LOGICAL_HEIGHT).toInt()
    return minOf(widthScale, heightScale).coerceAtLeast(MIN_SCALE)
}
```

`platformDensity` remains in the signature for compatibility with every current caller. It does not select UI scale after this change. Do not modify callers to remove it.

Do not delete or rename the now-unused private density-selection constants/helper in this task. Removing unrelated private declarations is cleanup outside the authorized diff.

### 5.4 Locked calculation examples

For a 1080×2400 engine surface on the Google Pixel 10 Android phone:

```text
widthScale  = floor(1080 / (24 × 16)) = 2
heightScale = floor(2400 / (30 × 16)) = 5
S = min(2, 5) = 2
```

For the complete 2559×1439 landscape screenshot dimensions:

```text
widthScale  = floor(2559 / (24 × 16)) = 6
heightScale = floor(1439 / (30 × 16)) = 2
S = min(6, 2) = 2
```

Runtime calculation continues using the real dimensions already supplied to `deriveScale`; do not hardcode either reference resolution.

## 6. Fix 2 — make HUD side depend only on aspect ratio

Target: `RetroHudComponent.kt`, existing private `layoutMode` body only.

Replace the candidate-validity and candidate-score body with:

```kotlin
private fun layoutMode(logicalWidth: Float, logicalHeight: Float): Int {
    return if (logicalWidth > logicalHeight) {
        LAYOUT_LEFT
    } else {
        LAYOUT_BOTTOM
    }
}
```

This is shared aspect-ratio logic:

```text
landscape -> left HUD
portrait  -> bottom HUD
1:1 tie   -> bottom HUD
```

Do not change any other `RetroHudComponent` function or constant. In particular, preserve current HUD dimensions, button geometry, drawing, and hitboxes. Do not add platform checks.

## 7. Ordered implementation plan

### Phase 0 — scale policy math `[pending]`

- Edit only the constants and `deriveScale` body specified in section 5.
- Preserve its name, signature, callers, and surrounding APIs.
- Confirm the two locked calculations produce `S = 2` by direct substitution into the exact formula.

### Phase 1 — shared HUD orientation `[pending]`

- Replace only the existing `layoutMode` body with section 6.
- Do not change HUD geometry.

### Phase 2 — existing build and visual check `[pending]`

- Use the project's existing build/run workflow; do not add test or diagnostic infrastructure.
- Compare portrait scenes against the four before-overhaul screenshots, not the rejected after-overhaul screenshots.
- Confirm the landscape UI uses `S = 2` and the left HUD.
- If a failure requires code outside the exact scope in section 2, stop and report it instead of expanding the implementation.

## 8. Acceptance checklist

### Scope

- [ ] The production diff is limited to the two commonMain files and exact symbols listed in section 2.
- [ ] No platform source-set file is changed.
- [ ] No API/type/object/field/function/parameter/constant name is changed.
- [ ] No new display/state architecture is added.
- [ ] Renderer, glyph, text-raster, clipping, and input-transform code is unchanged.
- [ ] Template, Template Forge, Entropy, Settings, and Block Overlay code is unchanged.

### Scale math

- [ ] `deriveScale(1080f, 2400f, existingDensity)` returns `2`.
- [ ] `deriveScale(2559f, 1439f, existingDensity)` returns `2`.
- [ ] Both width and height participate in the same integer-scale result.
- [ ] No reference resolution is hardcoded.
- [ ] Existing callers and logical-bound flow are unchanged.

### Layout behavior

- [ ] Portrait uses the bottom HUD.
- [ ] Landscape uses the left HUD.
- [ ] HUD geometry and hitboxes otherwise retain current behavior.
- [ ] The portrait Template scene retains the before-overhaul card density and spacing.
- [ ] The portrait Entropy scene retains the before-overhaul row height and does not reproduce the rejected large empty region.
- [ ] The portrait Settings scene retains the before-overhaul row sizing and stacking.
- [ ] `ActiveTimerScene`, `timerRadius`, the timer instrument, and all other procedural artwork are unchanged.

## 9. Stop conditions

Stop and report the exact conflict without improvising if:

- the implementation requires editing a file or symbol outside section 2;
- an API rename appears necessary;
- a platform-wrapper change appears necessary;
- a proposed fix requires `DisplayGrid`, a new renderer path, or a new input path;
- a proposed fix requires changing Template, Entropy, or Settings layout;
- a proposed fix requires changing `ActiveTimerScene`, `timerRadius`, or procedural artwork;
- the reverted current code no longer matches the quoted functions;
- the landscape UI scale still fails after the exact width-and-height scale formula is applied.

Do not respond to a stop condition by restoring any part of the rejected previous guide.
