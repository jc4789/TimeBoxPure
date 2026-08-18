# Scope

- Read-only audit of the currently reachable magic-circle path. Production code was not edited.
- Hot path traced end to end:
  - Android loop: `app/src/main/java/com/example/timeboxvibe/ui/main/Pc98SurfaceView.kt:163-180`
  - engine dispatch: `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/SceneManager.kt:109-133,145-162`
  - scene update/render: `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ActiveTimerScene.kt:56-67,70-178`
  - graphics state: `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/MagicCircleDemoscene.kt:95-167`
  - rasterizer: `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/NestedTimeboxInstrumentRenderer.kt:98-453`
- Laws applied: commonMain owns logic; indexed output is palette indices `0..15`; hot update/render paths must avoid allocation; final raster is integer-snapped; no new verification infrastructure.

# Confirmed

## Reachability and ownership

- Android computes `dt` from monotonic nanoseconds and clamps it to `0.05f` (`Pc98SurfaceView.kt:163-176,304-309`), then calls update and render exactly once in the same `drawFrame` (`Pc98SurfaceView.kt:205-227`).
- `SceneManager.update` calls the current scene once (`SceneManager.kt:109-133`). `ActiveTimerScene.update` calls `demoscene?.update(dt)` once (`ActiveTimerScene.kt:56-67`).
- `SceneManager.render` calls the current scene once (`SceneManager.kt:145-162`). `ActiveTimerScene.render` passes the same nullable `demoscene` reference to `NestedTimeboxInstrumentRenderer.render` (`ActiveTimerScene.kt:151-178`).
- Rotation mutation occurs only in `MagicCircleDemoscene.update` (`MagicCircleDemoscene.kt:95-103`). `NestedTimeboxInstrumentRenderer.render` only reads the six angles (`NestedTimeboxInstrumentRenderer.kt:155-160`). Therefore extra render calls without update calls do not advance phase.
- The current model is still explicitly time-derived: six angles, rune drift, and nebula drift advance by `rate * dt` (`MagicCircleDemoscene.kt:95-103,163-183`). Removal of `FrameClock` does **not** satisfy a contract that forbids time-derived motion.
- The Android clamp makes the present phase model lose elapsed motion below 20 updates/second: any frame interval above `0.05s` is reduced to `0.05s` (`Pc98SurfaceView.kt:173-176,308`). Thus broad “independent of render FPS” is false for sufficiently slow frames even though render itself is read-only.

## Hidden no-op and fractured gimmicks

- Six `Wave` objects are constructed and updated (`MagicCircleDemoscene.kt:34-71,105-111`), but repository-wide reference search finds no read of `runeSway`, `pentaBreath`, `outerHeartbeat`, `innerHeartbeat`, `coreWobble`, or `sectorSwing` outside their declaration/update/reset. They produce no pixels.
  - Evidence command: `rg -n --glob '*.kt' "runeSway|pentaBreath|outerHeartbeat|innerHeartbeat|coreWobble|sectorSwing" shared-engine/src`
- The 6-link `IkChain2D`, `trailAlpha`, and `trailSize` are constructed/reset and exposed (`MagicCircleDemoscene.kt:73-87,120-136`), but `solveTrail`, `trail.points`, `trailAlpha`, and `trailSize` have no call/read outside that file. This FABRIK feature produces no pixels.
  - Evidence command: `rg -n --glob '*.kt' "solveTrail|trailAlpha|trailSize|\\.trail\\b" shared-engine/src`
- The visible trail is a separate, direct 4-dot polar calculation in the renderer (`NestedTimeboxInstrumentRenderer.kt:316-337`). It does not use the 6-link FABRIK state and is not gated by `demosceneEffectsEnabled`. This is concrete duplicate/fractured ownership.
- The visible trail's supposed fading does not exist in indexed output. `linkAlpha` varies (`NestedTimeboxInstrumentRenderer.kt:325-335`), but `SoftwareEngineCanvas.setDrawAlpha` only sets `drawEnabled = alphaByte > 0` (`SoftwareEngineCanvas.kt:19-27`); every clamped `linkAlpha` is at least `0x20`, so all four dots draw at the same palette color. The renderer's `GUIDE_ALPHA`, `MECHANICAL_ALPHA`, `SCRIPTURE_ALPHA`, and `SOLID_ALPHA` similarly collapse to the same nonzero behavior (`NestedTimeboxInstrumentRenderer.kt:45-48,180-184,208-221,287-310`).
- The outer timer beads are documented as rotating (`NestedTimeboxInstrumentRenderer.kt:14,229-230`) but are rendered at fixed `-90f` (`NestedTimeboxInstrumentRenderer.kt:232-237`). `outerRingAngle` only drives detail ticks (`NestedTimeboxInstrumentRenderer.kt:206-213`).
- `reset()` claims deterministic animation across scene switches (`MagicCircleDemoscene.kt:125-129`) but resets only Waves and the unused chain (`MagicCircleDemoscene.kt:129-137`). It does not reset the six angles, `runeDriftPhase`, or `nebulaPhase` declared at lines 18-32.
- `VisualsStateHolder.demosceneEffectsEnabled` is documented as controlling Waves, Perlin drift, and FABRIK trail (`VisualsStateHolder.kt:20-27`), but the current visible 4-dot trail is unconditional, all six Waves are invisible, and basic angle/rune/nebula phases advance before the early return (`MagicCircleDemoscene.kt:95-111`).

## Per-update/per-render allocations

- `MagicCircleDemoscene.update` itself uses only primitive fields and explicit calls; no per-call array, collection, lambda, string, or object construction appears at `MagicCircleDemoscene.kt:95-112`. HOT LOOP AUDIT for that method alone: **PASS for allocation**, while its dead Wave work remains redundant.
- `NestedTimeboxInstrumentRenderer.render` uses primitive `while` loops and a preallocated rotated-glyph buffer (`ScaledProceduralRenderer.kt:170-175,365-428`). No direct per-render array/list/lambda construction appears inside `NestedTimeboxInstrumentRenderer.kt:98-453`. HOT LOOP AUDIT for that method alone: **PASS for visible direct allocation**.
- The reachable scene path does allocate before entering the magic renderer:
  - Android `getUiState()` constructs a new `EngineUiState` on each call (`MainScreenViewModel.kt:618-648`). `ActiveTimerScene` calls it in both update and render (`ActiveTimerScene.kt:57,74`). This is at least one object per update and one per render on Android.
  - `state.presets.firstOrNull { ... }` traverses a `List` in the render path (`ActiveTimerScene.kt:133`; `TimerActions.kt:32`). Its `Iterable` iterator is a potential backend allocation and is not proven allocation-free.
  - `activePreset?.sequence ?: IntArray(0)` constructs an empty primitive array whenever no active preset is found (`ActiveTimerScene.kt:136`).
  - `SessionMacroDisplay.resolveMacro` returns `Pair<Int, Int>` through `to` on every render (`SessionMacroDisplay.kt:47-64`), and `ActiveTimerScene` destructures it (`ActiveTimerScene.kt:134-141`). This is a concrete generic object/boxing path.
- `IkChain2D` falsely documents itself as allocation-free (`IkChain2D.kt:5-16`): if its currently unreachable `solveTrail` were wired in, `solve`, `placeLinearly`, and `reset` repeatedly construct immutable `Point2D` data-class instances (`IkChain2D.kt:31-78,85-107`; `ProceduralMath.kt:10`). Do not revive this implementation in a hot path unchanged.

## Redundant procedural cost

- The most expensive visible primitive is the Yin-yang core: every render scans the full `(2*coreRadius+1)^2` bounding square, performs rotated/lobe tests, and writes all circle pixels (`NestedTimeboxInstrumentRenderer.kt:383-439`). At the documented reference geometry, `graphicsUnit=3`, `graphicsCell=48`, and `coreR=144` (`NestedTimeboxInstrumentRenderer.kt:38-43,141-143,162`), so the scan is `289*289 = 83,521` candidate pixels per frame before outline work.
- Every tangent glyph clears the same 256-entry buffer and scans all 256 glyph source bits (`ScaledProceduralRenderer.kt:365-428`). The magic circle calls it for 36 rune glyphs and 10 scripture glyphs each frame (`NestedTimeboxInstrumentRenderer.kt:186-204,239-255`): 46 buffer clears and 46 full 16x16 scans per render, before scaled pixel emission.
- Rune drift adds 36 Perlin samples per render (`NestedTimeboxInstrumentRenderer.kt:189-203`; `MagicCircleDemoscene.kt:144-147`). `PerlinNoise.noise1D` calls `floor(x.toDouble())` twice for each sample (`PerlinNoise.kt:28-31`). This is deterministic but avoidable work if the drift gimmick is removed.
- Static geometry is necessarily redrawn after the framebuffer clear (`SceneManager.kt:145-160`); no existing retained layer/cache exists. This audit does not recommend inventing one because that would add a new subsystem and is not required to remove the confirmed dead/fractured work.

## Geometry and palette evidence

- Accepted radius/layout math is unchanged relative to `HEAD`. `git diff --unified=0 -- shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/NestedTimeboxInstrumentRenderer.kt` shows only documentation, removal of the `elapsedSeconds` argument/constants, angle-source substitutions at lines 155-160, and the rune-drift call signature. It contains no change to `GRAPHICS_SOURCE_CELL`, `GRAPHICS_SOURCE_RADIUS`, `GRAPHICS_REFERENCE_RADIUS`, viewport conversion, center constraints, `graphicsRadius`, `graphicsCell`, or radii at current lines 38-44 and 128-178.
- Palette constants are exactly `0..15` (`Pc98GraphicsHardware.kt:5-21`). Every software primitive rejects values outside `0..15` (`IndexedFramebuffer.kt:40-43,46-59`; `SoftwareGraphics.kt:220-223`). The current magic caller passes only `PaletteIndices` values from `ActiveTimerScene.kt:162-166` and renderer-local `PaletteIndices` at `NestedTimeboxInstrumentRenderer.kt:219,471-472`.
- Existing palette/raster tests pass on Android/JVM and Win32/Kotlin-Native:
  - Command: `$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:testDebugUnitTest :shared-engine:winTest`
  - Result on 2026-08-18: `BUILD SUCCESSFUL in 32s`; `22 actionable tasks: 10 executed, 12 up-to-date`.
  - Coverage: `SoftwareEngineCanvasTest.kt:9-36` checks palette writes, invalid-index rejection, and deterministic dither; `GlyphRasterizerTest.kt:9-41` checks ROM-bit pixel mapping and clipping.
- `git diff --check` exits successfully; its only output is Git's existing LF/CRLF conversion warnings.

## Existing runtime evidence

- The connected device is identified read-only as `Pixel_10`, device `frankel`, serial `58150DLCR000UW` by:
  - `C:\Users\cesta\AppData\Local\Android\Sdk\platform-tools\adb.exe devices -l`
- Android system logs show the TimeBox activity at a `1080x2424` app bound, but the app was not foreground during this read-only audit.
- The root task ledger records a previous warm Pixel 10 observation improving Active Timer from about 28 FPS to about 54 FPS after the framebuffer clear/fill reductions (`.agents/work/mahojin_rotation/STATE.md:20-21`). The corresponding source changes are visible at `IndexedFramebuffer.kt:29-33`, `SoftwareGraphics.kt:121-155,171-187`, and `SceneManager.kt:145-160`.

# Rejected

- **Rejected: six Waves are active visual effects.** No renderer reads their values; update/reset is dead work.
- **Rejected: the renderer uses the FABRIK trail.** It draws a separate four-dot polar trail and never calls `solveTrail` or reads `trail.points`.
- **Rejected: alpha provides fade in the 4-bit indexed framebuffer.** Nonzero alpha only enables drawing; it does not blend or choose another palette index.
- **Rejected: `demosceneEffectsEnabled=false` disables the visible trail.** The renderer's direct trail is unconditional.
- **Rejected: current magic animation has no time/frame dependency.** `FrameClock` is deleted, but `MagicCircleDemoscene.update(dt)` remains a time-derived phase integrator.
- **Rejected: current phase behavior is unconditionally render-FPS independent.** It is unaffected by extra render-only calls, but Android couples update and render and clamps `dt` to `0.05f`; motion loses time below 20 FPS.
- **Rejected: existing tests prove accepted magic-circle pixels/geometry.** No `MagicCircleDemoscene`, `NestedTimeboxInstrumentRenderer`, `ActiveTimerScene`, framebuffer hash, or magic-scene golden test exists under `shared-engine/src/commonTest`.
- **Rejected: a passing shared-engine build proves Android smoothness.** It proves compilation/tests, not delivered frame cadence on the device.

# Unknown

- Exact current installed-APK FPS is not proven. Existing `Pc98SurfaceView.logStatsIfDue` can report cumulative `framesRendered`, scene, primitive dimensions, and `lastDt` (`Pc98SurfaceView.kt:272-283`), but no `Pc98SurfaceView:D` stats lines remained in logcat during this audit. The historical ~54 FPS ledger entry is useful but not final-runtime proof.
- Accepted geometry is supported by zero geometry-math diff plus the user's accepted screenshots; there is no existing engine-level pixel hash/golden to quantify changed pixels.
- Whether the currently visible direct 4-dot trail is aesthetically accepted cannot be inferred from code. Technically, its advertised fade/progress behavior is absent.
- Kotlin backend escape analysis may eliminate some platform/state/iterator/Pair allocations, but source-level allocation-free compliance is not proven and Kotlin/Native should not be assumed to erase them.

# Recommendation

1. Treat `NestedTimeboxInstrumentRenderer`'s accepted radius/center/ring constants and draw geometry (`NestedTimeboxInstrumentRenderer.kt:38-44,128-178`) as the protected baseline. Do not touch them during the gimmick cleanup.
2. Remove rather than relocate the confirmed dead/fractured pieces: six unread Waves, unused 6-link FABRIK state, unused trail arrays/solver, false alpha/fade machinery, and comments/settings claims for effects that do not render. Do not replace them with another service, interface, clock, accumulator, or cache.
3. Because the user explicitly rejected time- and frame-derived motion, do not keep `MagicCircleDemoscene.update(dt)` under another name. The clean no-duplication endpoint is one renderer-owned static phase definition for retained layers, or deletion of rejected moving layers; choosing fixed accepted phase values preserves geometry without introducing timing ownership.
4. Keep background nebula separate from magic-circle ownership. It is an Active Timer background feature (`ActiveTimerScene.kt:97-120,758-777`), not circle geometry. If retained, it needs its own explicitly accepted behavior; it is not a reason to preserve the `MagicCircleDemoscene` grab-bag.
5. Minimal **existing** verification set after the cleanup, without new infrastructure:
   - Geometry: `git diff --unified=0 -- shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/NestedTimeboxInstrumentRenderer.kt` and confirm no changes to current lines 38-44 and 128-178; then compare the four existing Android screenshots, especially the accepted Active Timer image. Existing tests cannot replace this visual check.
   - Palette/raster: run `$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:testDebugUnitTest :shared-engine:winTest`; retain runtime `0..15` guards.
   - Forbidden timing paths: `rg -n "FrameClock|EngineTime|elapsedSeconds|frameCount|timeMs|getEpochMillis|degreesPerSecond|DEG_PER_SEC|update\(dt" shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/MagicCircleDemoscene.kt shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/NestedTimeboxInstrumentRenderer.kt`; expected final result is no magic-circle timing owner. If `MagicCircleDemoscene.kt` is deleted, search the retained owner(s).
   - Phase/render-FPS behavior: for a static-phase endpoint, verify two consecutive existing render calls produce identical indexed framebuffer content except timer/readout state. There is no existing automated magic renderer test, so do not claim pixel equality from the current test suite.
   - Android smoothness: build/install normally, foreground Active Timer, collect two or more existing `Pc98SurfaceView:D` one-second stats lines, calculate frame delta/time delta, and inspect `lastDt`; no diagnostic code is needed. Final smoothness remains unverified until this is done on the final APK.

