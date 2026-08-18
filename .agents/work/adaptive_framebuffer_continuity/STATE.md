# Objective
Make the two supplied Win32 sizes consistently human-readable while preserving the common indexed framebuffer, full-client presentation, and flicker fix.

# Constraints
- Scaling authority remains in commonMain; platform wrappers only report surface/client dimensions and present/forward input.
- No DPI, densityRatio, per-widget scaling, UI framework, external asset, or scene-specific workaround.
- Preserve `U = glyphWidth = glyphHeight` and all existing scene/HUD geometry.
- Do not change Active Timer non-UI graphics.
- Preserve the 12-bit palette / 16 active indexed framebuffer path.
- No allocation in per-frame render/pixel loops; resize-time arithmetic is allowed.

# Plan
- [x] Quantify the screenshot discontinuity and confirm the exact profile boundary.
- [x] Use a minimum 2x2 terminal pixel block per primitive pixel, then proportionally fit only when its framebuffer exceeds the budget.
- [x] Add deterministic readability/continuity/aspect/budget tests without screenshot infrastructure.
- [x] Pass common, Android, Win32, hot-loop, color-law, and runtime resize gates.

# Confirmed
- The old `PrimitiveDisplayProfile` incremented an integer divisor until the primitive area was within `1 shl 20` pixels.
- The supplied portrait screenshots are almost the same physical size but show approximately 1x versus 2x primitive magnification.
- A client near 856x1224 is just below the current budget; a few extra pixels push the divisor from 1 to 2.
- The source images are 859x1262 and 866x1261 including their non-client areas.
- Old profile: 856x1224 -> 856x1224, but 857x1224 -> 429x612.
- The rejected near-1x experiment produced 856x1224 -> 856x1224 and 857x1224 -> 856x1223.
- Final profile uses integer arithmetic and resize-time binary search only above the pixel-doubled baseline; render/pixel loops are unchanged.
- 855x1226 -> 428x613 and 857x1225 -> 429x613, so both supplied portrait sizes use the readable approximately 2x presentation.
- 2560x1368 -> 1280x684, preserving the previously accepted Win32 density.
- 3840x2160 -> 1365x768, using continuous budget fitting instead of a 2-to-3 integer-divisor cliff.
- common Android/JVM tests, Win32 native tests, Android APK, and Win32 executable builds pass.
- Runtime Win32 inspection at 1906x1016 shows the restored readable pixel-doubled UI, full-client rendering, and no observed black frame.
- Runtime maximized Win32 inspection at 2560x1392 also shows full-client rendering and no observed black frame.
- The supplied readable portrait side and the final profile both use 429x613 at the 857x1225 client size; the adjacent 855x1226 client now uses 428x613 instead of switching to near-1x.
- User runtime feedback: the proportional near-1x result is visually worse; the readable side of the supplied pair is the approximately 2x result.

# Rejected
- Scene/HUD-specific size edits: they would hide the framebuffer discontinuity in only selected views.
- Increasing or decreasing the pixel-budget constant alone: it merely moves the same discontinuity to another window size.
- Fitting every over-budget client as close to 1x as possible: mathematically continuous, but it makes the 16x16 ROM glyph and HUD too small from a human point of view.

# Unverified
- None within the supplied two image sizes and requested Win32/Android build scope.

# Next
Complete; retain the latest executable for user inspection.
