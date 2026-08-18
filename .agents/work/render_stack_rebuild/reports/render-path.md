# Scope

- Read-only audit of the current render, presentation, and pointer-coordinate paths. Production sources were not edited.
- Center: `shared-engine/src/commonMain/.../EngineCanvas.kt` and `ScaledProceduralRenderer.kt`; Android and Win32 were inspected only to establish their current terminal contracts.
- Relevant project law: common code owns rendering and palette-index meaning; platform code may expose a surface, input facts, and native color conversion, but must not own layout/render policy.

# Confirmed

## Current commonMain boundary

- `EngineCanvas` is not a framebuffer. It is a platform drawing-command interface containing surface bounds, `density`, `presentationScale`, logical primitives, and a second `drawPhysicalRect` coordinate space (`EngineCanvas.kt:93-113`). The two implementations are Android and Win32 only; no test or iOS implementation exists (`AndroidEngineCanvas.kt:19-25`, `Win32EngineCanvas.kt:14-21`).
- `ScaledProceduralRenderer` owns procedural graphics, font rasterization, UI button drawing, and platform-scale compensation in one class (`ScaledProceduralRenderer.kt:12-51`, `67-200`, `763-824`). Scene APIs directly expose that concrete type (`Scenes.kt:18-22`), and `SceneManager.render` also requires it (`SceneManager.kt:159-179`). This is a load-bearing compatibility boundary even though it is architecturally tangled.
- `SceneManager` stores the dimensions received from the host, derives HUD/play-area geometry, clears, and calls the active scene (`SceneManager.kt:159-188`). Pointer dispatch uses the already-transformed logical coordinates for both HUD and scene hit-testing (`SceneManager.kt:255-275`).

## Complete Android path

1. `surfaceChanged` reads physical surface size and Android display density, runs `DisplayScalePolicy.deriveScale`, then defines `dynamicLogicalWidth/Height = physical / scale` (`Pc98SurfaceView.kt:63-77`).
2. It constructs `AndroidEngineCanvas(logical bounds, density=1, presentationScale=scale)` and `ScaledProceduralRenderer` (`Pc98SurfaceView.kt:82-98`). Renderer construction mutates the process-global `TextRasterScale` from the canvas presentation scale (`ScaledProceduralRenderer.kt:45-47`).
3. Every frame, Android sets `SceneManager` logical bounds and updates input, then applies `Canvas.scale(scale, scale)` before `SceneManager.render` (`Pc98SurfaceView.kt:256-293`). Ordinary primitives therefore go common logical float -> Android rounded logical coordinate -> outer Canvas scale -> physical surface (`AndroidEngineCanvas.kt:81-108`).
4. Text takes a separate route: common code multiplies positions by `presentationScale`, caps source-pixel enlargement at 2, and calls `drawPhysicalRect` (`ScaledProceduralRenderer.kt:67-80`, `130-175`). Android then divides that rectangle by `presentationScale` so the outer Canvas transform multiplies it again (`AndroidEngineCanvas.kt:27`, `110-120`). Thus text size/layout is intentionally decoupled from other geometry.
5. Touch input performs the inverse integer division `event / currentScaleFactor`, stores logical and raw values, then the render thread forwards the five-slot events to `SceneManager.update` (`Pc98SurfaceView.kt:110-133`, `360-372`, `256-268`). Raw X/Y are stored but never read by commonMain (`SceneManager.kt:255-275`, `325-330`).
6. There is no completed indexed frame. Android resolves each palette index to ARGB and draws each primitive immediately (`AndroidEngineCanvas.kt:34-65`, `76-165`). Scanlines are a second platform-native overlay after common rendering (`Pc98SurfaceView.kt:295-305`).

## Complete Win32 path

1. `WM_SIZE` and `WM_DPICHANGED` call `applyClientSize`, which converts DPI to density, runs the same `DisplayScalePolicy`, and defines logical bounds as physical/scale (`Win32Host.kt:142-164`, `337-347`).
2. The host constructs/reconstructs `ScaledProceduralRenderer`; construction reconfigures the same global text-scale state (`Win32Host.kt:154-164`, `ScaledProceduralRenderer.kt:45-47`).
3. `Win32EngineCanvas` rasterizes each logical primitive itself into a physical-sized `IntArray`; it multiplies coordinates and thickness by `presentationScale` (`Win32EngineCanvas.kt:37-54`, `70-175`, `206-233`). This is a platform-owned software rasterizer, and the buffer contains native BGRA/alpha dwords, not palette indices (`Win32EngineCanvas.kt:178-203`).
4. The frame loop calls update/render, then `present` sends that physical native buffer through `StretchDIBits` (`Win32Host.kt:291-301`, `167-201`). The stretch source and destination sizes are currently identical, so this is not a final one-time logical framebuffer scaling boundary.
5. Mouse input performs integer division by `scaleFactor` before the same five-slot queue reaches `SceneManager` (`Win32Host.kt:114-139`, `291-299`, `357-369`). Wheel synthesis additionally multiplies its logical-cell delta by `scaleFactor` before re-entering the same inverse mapping (`Win32Host.kt:404-419`).

## Exact scale/density/text mechanisms to remove

- Entire `DisplayScalePolicy`, including trusted/fake density gates, geometry fallback, logical-width loops, and `CANONICAL_UI_UNIT`-based scale derivation (`EngineCanvas.kt:3-51`). No `densityRatio` symbol exists in the audited Kotlin tree.
- Entire mutable `TextRasterScale`, including its independent physical-pixel cap and logical-size conversion (`EngineCanvas.kt:53-87`). It affects renderer text measurement/rasterization (`ScaledProceduralRenderer.kt:25-30`, `45-47`, `67-80`, `121-193`) and all wrapped-text measurement/layout (`ProceduralUiPrimitives.kt:90`, `120`, `149`, `164`, `209`, `259`, `301`, `329`).
- `EngineCanvas.density`, `EngineCanvas.presentationScale`, and `drawPhysicalRect` (`EngineCanvas.kt:98-113`), plus the two platform implementations of those properties/methods (`AndroidEngineCanvas.kt:21-27`, `110-120`; `Win32EngineCanvas.kt:15-20`, `54`, `110-113`).
- Geometry dependence on `canvas.density` in procedural art (`ScaledProceduralRenderer.kt:411`, `418-440`, `602-636`) and the nested instrument (`NestedTimeboxInstrumentRenderer.kt:131`).
- Android `currentScaleFactor`, dynamic logical bounds, density sanitization, outer `Canvas.scale`, inverse touch division, and density-driven scanline spacing (`Pc98SurfaceView.kt:32-36`, `63-77`, `110-126`, `256-305`, `375-410`).
- Win32 `scaleFactor`, `presentationDensity`, DPI-derived logical bounds, per-primitive multiplication, inverse pointer division, and wheel compensation (`Win32Host.kt:102-107`, `114-164`, `404-419`; `Win32EngineCanvas.kt:37-54`, `70-175`, `206-209`).

## Color/presentation facts

- The retained common color contract already models 16 active entries backed by 12-bit RGB values: `Color12Bit = Short`, palette indices `0..15`, `PALETTE_SIZE = 16`, and a 16-entry active palette (`Pc98GraphicsHardware.kt:3-35`).
- Current common drawing APIs accept indices, but alpha is resolved in native colors rather than an indexed framebuffer. `setDrawAlpha` is exposed by `EngineCanvas` and is used repeatedly by `NestedTimeboxInstrumentRenderer` (`EngineCanvas.kt:104-105`; `NestedTimeboxInstrumentRenderer.kt:163-337`). A new strictly indexed framebuffer cannot preserve arbitrary alpha without an explicit indexed replacement rule.
- Current Android and Win32 palette caches/conversion are valid terminal responsibilities, but rasterization is not consistently common-owned (`AndroidEngineCanvas.kt:34-65`; `Win32EngineCanvas.kt:178-203`).

## Audit gates

```text
PLATFORM FIREWALL CHECK:
Platform: Android + Win32
Allowed responsibility: surface/input/native palette expansion/whole-frame present
Core responsibility preserved: scene and hitbox policy remains in commonMain
Leakage found: presentation-scale policy is host-driven; Android draws primitives directly; Win32 owns primitive rasterization
Result: FAIL (current path)
```

```text
COLOR LAW CHECK:
Core color representation: draw arguments are normally palette indices 0..15
Native color conversion location: AndroidEngineCanvas / Win32EngineCanvas
Palette cache: revisioned 16-entry cache on both platforms
Platform leakage: no common indexed framebuffer; alpha/native pixels are produced during individual primitive draws
Result: FAIL (current framebuffer/presentation path)
```

```text
LAW VIOLATION:
File: app/.../AndroidEngineCanvas.kt and shared-engine/src/winMain/.../Win32EngineCanvas.kt
Line: Android 76-165; Win32 70-175
Rule: platform wrappers are dumb terminals
Why this breaks the engine: platforms execute/rasterize individual graphics primitives instead of presenting one completed common frame
Replacement strategy: common indexed framebuffer + common software rasterizer; platforms only expand palette and present the whole frame
```

# Rejected

- Reusing `DisplayScalePolicy`, DPI, density, density-derived logical bounds, `presentationScale`, or the text-only scale cap in the rebuilt design.
- A framebuffer per widget/scene, direct platform primitive drawing, or platform-owned rasterization/layout policy.
- Keeping `drawPhysicalRect` as a second coordinate domain. All graphics and text must target the same primitive pixel coordinates.
- Treating Win32's existing physical ARGB `IntArray` as the required framebuffer; it is native-color output, not the common 4-bit indexed source.
- Treating Android's post-render scanline overlay as part of the core frame. If scanlines remain visual behavior, they must be drawn into the common indexed frame or explicitly removed.

# Unknown

- The fixed primitive framebuffer width and height are not specified by current code or the task ledger. They must be chosen once before layout and input mapping can become stable.
- The final mapping from primitive aspect ratio to an arbitrary surface (stretch, fit/letterbox, or crop) is not specified. That choice determines the inverse input mapping; it must be one common presentation contract, not separate Android/Win32 policy.
- Arbitrary alpha behavior has no defined 4-bit indexed equivalent. Preserve/remove/replace-with-dither is a visual design decision, not something the platform adapter can decide.
- Whether scanlines remain is not specified.

# Recommendation

## Minimal one-primitive-framebuffer architecture

```text
Scene/UI (layout + hitboxes)
        -> Graphics API (palette-index primitives only)
        -> commonMain SoftwareRasterizer
        -> one fixed-size IndexedFramebuffer (one byte/index per pixel, 0..15)
        -> platform Presenter (active 12-bit palette -> cached native pixels)
        -> one whole-frame present to the surface

physical pointer fact
        -> common presentation-coordinate mapper
        -> primitive framebuffer coordinate
        -> UI/scene input
```

- Make `IndexedFramebuffer(width, height, pixels)` the only raster target. The simplest storage is a reusable `ByteArray(width * height)` with each byte constrained to `0..15`; packing two pixels per byte can wait because it adds complexity without fixing ownership.
- Put `setPixel`, clipped integer rectangles, lines, circles, dither, and 16x16 ROM glyph emission in one commonMain software rasterizer. The graphics layer knows only pixels/primitives/palette indices; it does not know widgets, layouts, hitboxes, scenes, DPI, or the display.
- UI/IMGUI owns widget state/layout/hitboxes and calls the graphics API; it receives primitive framebuffer bounds but does not own or expose the buffer.
- Platform wrappers receive physical surface dimensions/events, convert the completed indexed frame with the active 16-entry 12-bit palette, and present it once. They do not receive individual scene draw commands. The inverse pointer mapping must be the exact inverse of the single final frame mapping.
- Preserve the five-slot touch queue initially (even though raw X/Y are unused) and preserve `SceneManager.update`, scene switching, timer actions, and keyboard contracts. Removing queue slots during the renderer migration creates unrelated breakage.

## Compatibility and likely compile breakpoints

1. **Renderer type:** Renaming/removing `ScaledProceduralRenderer` immediately breaks `Scene.render`, every scene implementation/helper in `Scenes.kt`, `SceneManager.render`, `RetroHudComponent`, `ProceduralUiPrimitives`, `NestedTimeboxInstrumentRenderer`, `EngineCursorRenderer`, and both hosts. Either migrate these atomically or temporarily keep the type name as a facade over the new common rasterizer (`Scenes.kt:18-22`; `SceneManager.kt:159`; `RetroHudComponent.kt:59`; `ProceduralUiPrimitives.kt:15`; `NestedTimeboxInstrumentRenderer.kt:38`; `EngineCursorRenderer.kt:53`).
2. **Static text metrics:** Removing `TextRasterScale` breaks all static measurement calls in scenes and `ProceduralTextRenderer`. Replace them with deterministic primitive metrics (`16 * semantic glyph scale`) before deleting the object (`ScaledProceduralRenderer.kt:25-40`; `ProceduralUiPrimitives.kt:90-329`; numerous call sites in `Scenes.kt`, beginning at `Scenes.kt:288`).
3. **Canvas interface:** Removing `density`, `presentationScale`, or `drawPhysicalRect` breaks both platform adapters, renderer initialization/text, and density-dependent graphics. Migrate common graphics off all three first, then replace adapters with presenters (`EngineCanvas.kt:98-113`).
4. **Alpha:** Removing `setDrawAlpha` breaks the nested instrument call sites. This needs an explicit indexed behavior or deletion of that visual system, not a native-alpha compatibility shim (`NestedTimeboxInstrumentRenderer.kt:163-337`).
5. **Host construction/presentation:** Android currently hands a locked `Canvas` to every draw and Win32 exposes its raster buffer on `Win32EngineCanvas`. Both constructors and frame loops must change together with the new presenter contract (`Pc98SurfaceView.kt:82-98`, `281-305`; `Win32Host.kt:154-201`, `291-301`).
6. **Input:** Deleting host scale division before adding the common inverse presentation mapping makes hitboxes receive physical instead of primitive coordinates. Frame presentation and pointer mapping must switch in the same commit (`Pc98SurfaceView.kt:110-128`; `Win32Host.kt:114-124`).
7. **Palette setup:** Keep `Pc98GraphicsHardware`/palette indices available while replacing the raster path; both presenters depend on its revisioned active palette, and scene/theme code already supplies indices (`Pc98GraphicsHardware.kt:3-55`).

Safest migration order: (1) choose framebuffer dimensions and final aspect mapping; (2) add common indexed framebuffer+rasterizer while retaining a compatibility renderer facade; (3) make both hosts present that completed buffer and switch input mapping simultaneously; (4) replace text metrics/raster with primitive-only metrics and delete all scale/density paths; (5) separate IMGUI ownership and then migrate/split scenes; (6) delete the compatibility facade after all concrete-type call sites are gone.
