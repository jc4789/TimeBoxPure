# Scope
Current production scaling and layout call paths relevant to the requested guide.

# Confirmed
- `CANONICAL_UI_UNIT = 16`: `shared-engine/src/commonMain/.../core/EngineCanvas.kt:5`.
- Density/width-derived `DisplayScalePolicy`: `EngineCanvas.kt:7-50`.
- Independent capped `TextRasterScale`: `EngineCanvas.kt:53-87`.
- Text measurements and raster writes depend on that cap: `ScaledProceduralRenderer.kt:25-30, 45-46, 104-199` and `ProceduralUiPrimitives.kt:80-329`.
- Android scale path: `Pc98SurfaceView.surfaceChanged`, `onTouchEvent`, and `RenderThread.drawFrame`.
- Win32 scale path: `Win32Host.applyClientSize`, `enqueueTouch`, and `Win32EngineCanvas` primitive scaling.
- Shared layout authority: `SceneManager.setLogicalBounds` and `RetroHudComponent.layoutMode`.

# Rejected
- Palette conversion as a scaling root cause: native conversion is downstream of layout and scale selection.
- HUD placement as a platform bug: shared layout intentionally selects the left HUD when that cell allocation scores better.

# Unknown
- None that block writing the guide.

# Recommendation
Specify a common cell-grid result derived from physical W/H and a single integer S; remove all text-only scale behavior; require both platform adapters to consume the same result mechanically.
