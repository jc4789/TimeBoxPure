# Objective
Replace the regressed magic-circle rendering path with one coherent graphics-local raster pipeline. Output resolution is presentation capacity, not the authority for the graphic's text size or alias character.

# Constraints
- No feature duplication or fractured ownership.
- No band-aid per-call scaling rules.
- Carmack-but-ZUN; C++-style primitive Kotlin.
- commonMain owns graphics behavior; platform remains untouched.
- Integer-snapped, palette-indexed final raster; no hot-path allocation.
- Preserve restored palette, accepted radius/layout, and six independent relative rotation speeds.
- Tests may be added when they prove the renderer contract.

# Plan
- [x] Establish the pre-regression and current complete render paths.
- [x] Define one graphics-local coordinate/raster authority and deletion list.
- [x] Replace the fractured line/circle path without a fixed raster surface.
- [x] Add focused indexed-framebuffer contract tests.
- [ ] Build common/Win32/Android and inspect Google Pixel 10 output.
- [ ] Audit every objective and invariant before completion.

# Confirmed
- Current outer circle is rasterized directly at physical framebuffer coordinates after beginGraphics().
- Current magic glyph block is derived independently from graphicsRadius, creating a second visual-density rule.
- The just-added optional rasterBlock parameter is explicitly rejected as a band-aid and must not remain.
- Commit 1061ddc introduced output-space rerasterization and the mutable UI/graphics coordinate-mode switch.
- Commit 709e267 made tangent glyph sampling depend on final destination scale.
- The accepted Pixel 10 image is consistent with a radius-162 source circle replicated three times, not a radius-486 circle rerasterized on the output grid.
- Output resolution is presentation capacity only; it is not the source mask/stroke/glyph-density authority.
- AliasedVectorLayer now normalizes geometry by its existing stroke width, rasterizes once in that self-relative integer grid, and emits the result at the same stroke width.
- A 3x coordinate/radius/stroke circle produces the exact 3x aliased mask without fixed framebuffer dimensions.
- SoftwareGraphics no longer owns duplicate line or circle rasterizers.
- ScaledProceduralRenderer line, circle, star, polygon, arc, and tick paths all reach AliasedVectorLayer.
- Android unit tests, Win32 tests, Android compilation, Win32 link, and Android APK assembly pass.
- Win32 visual inspection confirms the outer ring is visibly aliased again.

# Rejected
- Physical display resolution as the design authority for magic-circle aliasing or text size.
- Reverting the renderer wholesale to old code.
- Adding a second circle rasterizer or per-feature presentation scale.
- A whole-engine fixed 360x800-style framebuffer: no authoritative fixed app framebuffer dimensions exist, and graphics ownership must remain separate from UI ownership.
- Reusing UiRasterGrid as the magic-circle source authority.
- Any fixed graphics-surface width, height, radius, or presentation block introduced to repair scaling.

# Unverified
- Exact visual equivalence with the accepted Google Pixel 10 reference screenshot.
- Google Pixel 10 appearance and motion; no ADB device was connected during this pass.

# Next
Inspect the built APK on the Google Pixel 10 when the device is connected. Do not add a fixed resolution or a second scale rule in response to any remaining visual difference.
