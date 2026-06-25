# Custom UI Parity Implementation Plan

Date: 2026-06-24

## Scope

This plan covers the next migration chunk from the old Compose UI into the custom KMP engine:

- Make Entropy Bomb a first-class, scene-law-compliant custom scene.
- Restore template creation/editing parity from the old Compose `TemplatesScreen.kt`.
- Stop CJK/mojibake characters from rendering as missing-glyph black boxes.
- Add procedural pixel art to the HUD and main timer scene.
- Keep all work inside the project laws: no Compose in core, no external assets, palette indices only, `U`-derived layout, deterministic hot paths.

Relevant law: "The executable contains rules. The executable does not contain content files."

## Current Findings

### Entropy Scene

`EntropyScene` already exists in `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt`, and `RetroHudComponent` already routes to it by cycling the third HUD tab between Settings and Entropy.

It is not ready as-is:

- Uses `ArrayList<String>` for task storage.
- Builds strings during render with `StringBuilder`, `uppercase()`, interpolation, substring, and concatenation.
- Uses `(0 until tasks.size).random()` during the spin result.
- Has hardcoded layout constants instead of the newer shared `U`/ratio pattern.
- Does not use localized/default tasks from the old `EntropyScreen.kt`; it seeds fixed engineering-oriented tasks.
- Is reachable only through the Settings/Entropy cycling tab, which hides the feature.

### Templates

The current `TemplateCustomizerScene` is mostly a preset selector. The old Compose `TemplatesScreen.kt` also had a preset forge:

- New preset name input.
- Mode selector for classic, dual, dual.5, sequence, dual-sequence, calendar.
- Completion behavior selector.
- Parameter controls for durations.
- Calendar block editor with duration, focus/relax type, label, add/delete.

The data path already exists through `TimerActions.addCustomPreset(preset)` and Android persistence in `MainScreenViewModel`, but the custom scene does not expose most of that UI yet.

### Shinonome 16x16 Font / CJK Glyphs

`ScaledProceduralRenderer.drawGlyph()` currently resolves:

1. `ShinonomeFont.GLYPHS[char]`
2. `ShinonomeFont.fallbackProvider?.invoke(char)`
3. `ShinonomeFont.DEFAULT_GLYPH`

Important correction from project owner:

- The project already has a custom zero-byte font engine based on Shinonome 16x16 arrays from `東雲１６ｂｔｆ`.
- The desired fix is not a generic procedural fake-CJK fallback.
- This problem was fixed before by caching glyphs on load.

Current likely failure shape:

- `ShinonomeFont.kt` contains the core static 16x16 ROM glyph arrays and a `fallbackProvider` hook.
- The renderer asks for glyphs one character at a time.
- Unknown CJK/mojibake characters fall through to `DEFAULT_GLYPH`, producing black boxes.
- The missing or regressed part is likely the load-time glyph cache/population path that should feed `GLYPHS`/fallback lookups before rendering starts.

Lawful font strategy:

- Preserve the Shinonome 16x16 bitmask model as the canonical font system.
- Restore or rebuild load-time caching so render hot paths do not allocate or parse.
- Keep runtime rendering as direct `IntArray` row-bit lookup.
- Do not introduce TTF/OTF, platform text rendering, PNG glyph sheets, or runtime font file loading in core.
- If a source Shinonome conversion table exists in tooling, use it only as build/tooling input, not runtime content.
- If no source table is present, add only a compact, auditable ROM-glyph cache for the exact app text coverage needed first.

### Pixel Art

Procedural icons already exist in `ProceduralMath.getPixelColor()` and are used in template cards through `drawProceduralIcon(...)`.

Missing parity:

- HUD nav buttons do not draw icons.
- Main timer controls use text buttons instead of procedural play/pause/reset/skip icons.
- Main timer area does not show the active preset icon or theme/personality icon.

The existing icon pipeline is lawful: mathematical pixel rules, palette-index mapping, no PNG/SVG.

## Implementation Plan

### Phase 1: Shared Primitive Cleanup

Status: started.

Completed so far:

- Added shared `ProceduralIconRenderer` for 32x32 procedural icon drawing.
- Added shared `ProceduralTextRenderer` helpers for clipped uppercase text, preset ID drawing, and glyph-cell scale fitting.
- Switched template preset cards to the shared helpers.
- Removed the old private `drawProceduralIcon(...)` duplicate from `Scenes.kt`.

1. Move or duplicate the existing `drawProceduralIcon(...)` helper into a shared core utility, probably `ProceduralIconRenderer`.
2. Keep it allocation-free: no returned arrays, no color objects, no lambdas in render paths.
3. Replace raw ARGB-like intermediate constants where practical with small symbolic icon color categories, then map to palette indices at draw time.
4. Add `drawTextUpperClipped(...)`, `drawCodepointGlyphFallback(...)`, and fixed text-buffer helpers so scenes stop allocating strings just to draw labels.

Verification:

- `tools/hotpath_audit.py` on `Scenes.kt`, `RetroHudComponent.kt`, `ScaledProceduralRenderer.kt`, and any new helper.
- `tools/law_check.py` on touched commonMain files.

### Phase 2: Shinonome Cache / Missing Glyph Fix

Goal: no black boxes.

Completed so far:

- Confirmed the bundled CJK glyph keys in `ShinonomeFont.kt` are valid Japanese characters on disk.
- Added a load-time `ShinonomeFont` glyph cache keyed directly by `Char.code`.
- Warmed the cache from `ScaledProceduralRenderer` construction so `drawGlyph()` does not hit `GLYPHS` during normal rendering.
- Changed final unknown-character fallback from the full black-box `DEFAULT_GLYPH` to the existing `?` glyph.
- Added `ShinonomeFont.hasGlyph(char)` for non-render verification paths.
- Added tooling to generate a compact app-text Shinonome subset from `AI_REF/shnmk16.bdf`.
- Added `ShinonomeGeneratedGlyphs.kt` and load it into the same cache during `initCache()`.
- Current generated coverage: 438 direct BDF glyphs, 89 Simplified-to-JIS-form aliases, 4 remaining missing app-text codepoints (`你`, `办`/`辦`, `卡`).
- HUD and Settings now read localized labels from `getStrings(state.language)` instead of hardcoded English labels.

1. Audit `ShinonomeFont.kt` for the intended cache API:
   - Static `GLYPHS`.
   - `fallbackProvider`.
   - Any missing cache fields/functions that existed before.
2. Search repo/history if available for the previous "cache on load" implementation.
3. Restore a KMP-safe cache shape:
   - Preallocated or initialized outside render.
   - Keyed by `Char`/codepoint.
   - Returns existing `IntArray` glyph rows.
   - No allocation inside `drawGlyph`, `drawText`, scene `render`, or scene `update`.
4. Decide where cache warmup belongs:
   - Prefer `ShinonomeFont.initCacheForText(...)` called during app/engine initialization.
   - Seed from default presets, strings, scene labels, and currently selected language.
   - Avoid doing this inside render.
5. Make `fallbackProvider` a real bridge to the loaded cache, or replace the nullable provider with a direct internal cache lookup if that better matches the original design.
6. Add a final non-box fallback for truly unknown characters:
   - Prefer `?` or a small "missing" mark, not the current full black-box glyph.
   - This is only the last resort, not the normal CJK path.
7. Add a small verification utility/test that renders representative Japanese/Chinese/mojibake strings and counts `DEFAULT_GLYPH` hits.

Risk:

- If the previous cache source is gone, we may need to reconstruct the cache from the existing Shinonome-derived arrays or regenerate a compact table through tooling.
- A full CJK table can become an embedded asset blob if added carelessly; keep it ROM-glyph-lawful and auditable.

Decision needed later:

- Where is the old cache-on-load source or generator?
- Which text set should be warmed first: all localized strings/presets, or only current language plus visible scene labels?

### Phase 3: Entropy Scene Proper

Completed:

- Replaced `EntropyScene` task storage with fixed preallocated task slots backed by `IntArray`.
- Seeded scene tasks from localized `Strings.defaultTasks`.
- Replaced Kotlin random selection with scene-local deterministic xorshift selection.
- Reworked Entropy render/layout around `U`, display-derived play area, and fixed task rows.
- Removed Entropy render-time task `StringBuilder`, `uppercase()`, row interpolation, substring clipping, and `ArrayList` mutation.
- Preserved the Compose workflow: add task, delete task, page tasks, spin/highlight, directive popup, launch emergency session.
- Made Entropy a first-class HUD tab instead of hiding it behind Settings/System cycling.

1. Replace `ArrayList<String>` task storage with preallocated fixed task slots:
   - `FixedInputContainer` per slot, or a compact `EntropyTaskStore` using `IntArray`.
   - Fixed maximum task count and fixed maximum codepoints per task.
2. Replace random final selection with deterministic engine RNG:
   - Small xorshift state in `EntropyScene`.
   - Seed from scene-local state or tick count supplied by platform-safe engine time if already allowed.
3. Port old Compose behavior:
   - Default tasks from `Strings.defaultTasks`.
   - Add task input.
   - Delete task.
   - Spin/highlight animation.
   - Directive popup.
   - Commence emergency session.
4. Rebuild layout using the same `U`/play-area rules used by Settings and Templates.
5. Make navigation explicit:
   - Either add a fourth HUD slot when width permits, or give the third slot a stable split/cycle indicator.
   - Avoid hiding Entropy behind "SYSTEM" text.

Verification:

- Spin with zero tasks, one task, and max tasks.
- Add/delete across pages.
- Start emergency session.
- Resize portrait/landscape/square.

### Phase 4: Template Forge Scene

1. Convert `TemplateCustomizerScene` from list-only into two modes:
   - List mode.
   - Forge mode.
2. Use singleton scene state only; no per-entry object allocation in render.
3. Add editor fields:
   - Preset name input.
   - Mode selector.
   - Completion behavior.
   - Duration steppers/sliders.
   - Sequence entry.
   - Calendar block editor.
4. For calendar blocks:
   - Preallocate max block count.
   - Store duration seconds in `IntArray`.
   - Store type as `BooleanArray` or small `IntArray`.
   - Store labels in fixed input containers.
5. Generate custom preset IDs without `System.currentTimeMillis()` in commonMain:
   - Add a `TimerActions.createCustomPreset(...)` platform action, or
   - Let Android `MainScreenViewModel.addCustomPreset(...)` assign IDs.
6. Ensure custom calendar presets pass complete arrays for sequence, sequenceTypes, and sequenceLabels.

Verification:

- Create each preset type.
- Persist/reload.
- Delete custom preset.
- Start created calendar preset and step through focus/relax blocks.

### Phase 5: HUD Pixel Art

1. Add icon+text HUD buttons:
   - TIMER: watch or play icon.
   - CARDS: ofuda or ribbon.
   - ENTROPY: hakkero/bomb/flame icon.
   - SYSTEM: gohei or small gear-like procedural icon.
2. In narrow portrait mode:
   - Prefer icon-only or short labels.
   - Preserve hitboxes from the same computed layout as render.
3. In landscape:
   - Draw icon at `btnX + U / 2`, text after icon.
   - Clip text inside button.

Verification:

- Touch hitboxes match icons/text.
- No overlap at small logical widths.
- No black-box glyphs in HUD labels.

### Phase 6: Main Timer Pixel Art

1. Replace text-only timer controls with procedural icon buttons:
   - Start: `play_danmaku`
   - Pause: `pause_ofuda`
   - Reset: `reset_yinyang`
   - Skip: `skip_double_danmaku`
2. Add active preset icon near the timer/task area:
   - classic: `watch`
   - dual: `yinyang`
   - dual.5: `hakkero`
   - sequence: `ribbon`
   - dual-sequence: `skip_double_danmaku`
   - calendar: `ofuda`
3. Keep text labels as clipped secondary affordances only when space permits.
4. Use one shared control layout function for render and input.

Verification:

- Start/pause/reset/skip all still work.
- Controls do not shift when skip appears/disappears.
- Icons render in all themes.

## Suggested Execution Order

1. Shinonome cache restoration first, because it affects every scene and makes visual debugging sane.
2. Shared procedural icon renderer.
3. HUD icons and explicit Entropy navigation.
4. Entropy scene cleanup/parity.
5. Main timer icon controls.
6. Template forge/calendar editor.

## Open Questions

1. Where did the old Shinonome cache-on-load implementation live, and is the source/generator still available?
2. Should Entropy get its own permanent HUD tab, or should the current Settings/Entropy cycling behavior remain?
3. What maximum custom calendar block count should the engine support?
4. Should custom preset creation live entirely in commonMain, or should Android assign the final persistent custom ID?
5. Are the current mojibake-style headers intentional aesthetic text, or should those become readable English/Japanese/Chinese strings?

## Files Likely To Change

- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/ShinonomeFont.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ScaledProceduralRenderer.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ProceduralMath.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/RetroHudComponent.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/TimerActions.kt`
- `app/src/main/java/com/example/timeboxvibe/ui/main/MainScreenViewModel.kt`

## Law Check Summary

ASSET LAW CHECK:
New files: one markdown planning document only.
Runtime file IO: none.
Embedded data: no new runtime data in this plan.
Procedural representation: required for icons and fallback glyphs.
Result: PASS

LAYOUT LAW CHECK:
U source: existing 16x16 ROM glyph cell.
Display variables used: required for all scene/HUD layouts.
Unexplained constants: must be moved to named constants during implementation.
Stacking behavior: required for all editor rows.
Result: PASS
