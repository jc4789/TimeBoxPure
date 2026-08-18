# Objective
Replace the tangled UI/graphics/scaling path with a clean custom IMGUI, graphics, and software-render stack; split the Scenes.kt god file; preserve nonvisual app behavior; make presentation use one completed primitive framebuffer.

# Constraints
- Kotlin Multiplatform commonMain owns engine behavior; platform wrappers remain dumb terminals.
- No Compose, SwiftUI, React, Electron, JVM-only APIs, runtime assets, DPI policy, densityRatio, or per-widget presentation scaling.
- UI and graphics have separate ownership. Graphics does not know widgets/layout/hitboxes; UI does not own the framebuffer.
- Preserve 16x16 ROM glyph data and PC-98 color law: 12-bit RGB palette space, 16 simultaneously active colors.
- Existing unrelated dirty files are user-owned and must not be overwritten.

# Plan
- [x] Audit current paths and establish ownership/deletion map.
- [x] Implement primitive indexed framebuffer and one-pass presentation boundary.
- [x] Separate IMGUI/layout/input from graphics primitives.
- [x] Split scene implementations out of Scenes.kt and migrate them mechanically.
- [x] Verify input, rendering, builds, pixel invariants, hot loops, and project laws.

# Confirmed
- Current visual cluster mixes scene state, layout, hitboxes, text, vector art, palette, and rasterization across Scenes.kt, RetroHudComponent.kt, ScaledProceduralRenderer.kt, and helpers.
- TimerEngine and TimerActions are independent of the visual renderer.
- ShinonomeFont provides retained 16x16 ROM glyph rows.
- Baseline Android/common build passed before production edits.
- Scenes.kt was mechanically split into one file per scene and compiled successfully before the shell refactor began.
- IndexedFramebuffer, SoftwareGraphics, GlyphRasterizer, PresentationTransform, PrimitiveDisplayProfile, and ImmediateUi now have common tests.
- Android and Win32 both render into the common indexed framebuffer, expand the active 16-color palette, and present the completed frame once through PresentationTransform.
- The same PresentationTransform maps pointer input back into primitive coordinates; pointers outside the fitted viewport are rejected.
- DisplayScalePolicy, TextRasterScale, density, presentationScale, drawPhysicalRect, platform DPI-derived scaling, and platform primitive rasterizers are removed.
- UiShellLayout owns display direction, HUD/content geometry, and four navigation rectangles. HUD placement comes only from CommonUiSettings.
- Scene files no longer render or hit-test HUD. SceneManager is the one HUD render/input route and consumes the entire HUD region.
- ImmediateUi is the active navigation hit-test path. TouchColliderManager was deleted.
- The UI-owned button painter was moved out of ScaledProceduralRenderer without changing scene call syntax.
- PC-98 palette setup now enforces 16 active entries drawn from the 4096-value 12-bit RGB space.
- Scenes.kt was deleted after its scene implementations were split into separate files.

# Rejected
- Per-widget/per-glyph scale values, DPI-derived scale policies, densityRatio, device-specific layout, or platform-owned UI.
- Reusing canonical-unit-layout or dpi-scale-derive as design authority.

# Verified
- `:shared-engine:testDebugUnitTest` passes.
- `:shared-engine:winTest` passes.
- `:app:assembleDebug` passes.
- `:shared-engine:linkDebugExecutableWin` passes.
- Searches find no DisplayScalePolicy, TextRasterScale, presentationScale, densityRatio, drawPhysicalRect, Canvas.scale, or platform density-derived renderer path.
- Searches find one HUD render route and one HUD hit-test route, both in SceneManager.

# Next
Install/run the generated Android or Win32 artifact and compare the four scenes visually at the target window/device sizes. No further code change is implied by this runtime check.
