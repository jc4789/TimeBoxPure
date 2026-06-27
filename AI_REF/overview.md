# TimeboxVibe: Codebase Audit & Architectural Overview

## 1. Executive Summary
This document provides an audited architectural overview of **TimeboxVibe**. The codebase adheres to "Hardware Constraints as Feature" ("Carmack but ZUN") and "cpp but Kotlin", using a custom low-level procedural rendering system for early 90s PC-98 Japanese game aesthetics.

The core visual rendering (the nested timeboxing instrument, timer progress tracks, and danmaku bullet animations) and domain logic run entirely inside a pure Kotlin Multiplatform module (`:shared-engine`), isolated from Java/JVM or Android dependencies. The rendering loop operates on a background thread inside an Android `SurfaceView` (`Pc98SurfaceView`) in the `:app` module, using a 16-color index-based drawing pipeline.

---

## 2. Directory & Module Breakdown

### `:shared-engine` — Pure KMP Core (`commonMain/kotlin/`)

| File | Role |
|---|---|
| `TimerEngine.kt` | State machine managing countdowns, sequence progression, mode changes. Not to be modified. |
| `ScaledProceduralRenderer.kt` | High-level vector plotter. Exposes 8 `AliasedVectorLayer` delegation methods (`drawAliasedCircle`, `drawAliasedArc`, `drawAliasedProgressArc`, `drawAliasedLine`, `drawRadialTickMarks`, `drawRadialProgressTickMarks`, `drawCubicBezierDeCasteljau`, `drawQuadraticBezierDeCasteljau`). Default `drawGlyph`/`drawText` clip origin is `(0f, 0f)` — canvas-space clipping. |
| `AliasedVectorLayer.kt` | Bresenham/De Casteljau vector primitives. Already owned by `ScaledProceduralRenderer` as `private val vector`. |
| `ProceduralMath.kt` | `FastMath` LUT (1024-element `sin`/`cos`), `drawBresenhamCircle`, danmaku helpers. |
| `ProceduralIconRenderer.kt` | Pre-computed pixel art icons drawn via `EngineCanvas.setPixel`. |
| `Pc98GraphicsHardware.kt` | 16-slot 12-bit palette. Volatile `paletteRevision` counter. |
| `EngineCanvas.kt` | Platform-agnostic drawing interface (palette-indexed only). |
| `Scenes.kt` | 6 scene objects: `ActiveTimerScene`, `TemplateCustomizerScene`, `TemplateForgeScene`, `SettingsScene`, `EntropyScene`, `BlockOverlayScene`. |
| `SceneManager.kt` | Owns `activeScene`, `sceneRegistry`, `executeCommand()`. `SceneId` enum + `SceneCommand` sealed class. |
| `RetroHudComponent.kt` | HUD tab bar. Queues `SceneCommand` for `SceneManager` to execute. No direct scene switching. |
| `ShinonomeFont.kt` | 16x16 ROM glyph data. `glyphFor()` always returns non-null (falls back to `missingGlyph`). |
| `Strings.kt` | `AppStrings` data class, `getStrings(lang)`. EN/ZH/JA. |
| `FixedInputContainer.kt` | Allocation-free character buffer for text input. |

### `:app` — Android Platform Host (`src/main/java/`)

| File | Role |
|---|---|
| `Pc98SurfaceView.kt` | `SurfaceView` with background `RenderThread`. Drains touch queue, calls `SceneManager.update()` + `render()`. `drainTouchBuffer` loop wrapped in try-catch, no re-throw. `lockCanvas()` null-safe. |
| `AndroidEngineCanvas.kt` | `EngineCanvas` implementation. RAMDAC cache (16-slot `IntArray`). `fillRectDither` uses `SOLID` fast path. |
| `FocusService.kt` | Foreground service. Owns `TimerEngine` instance. `DISMISS_ALARM` handler: `dismissAlarm()` → `broadcastState()` → if active: `startTicker()` + `updateNotification("running")` / else: `stopAlarmAndService()`. |
| `MainScreenViewModel.kt` | `TimerActions` implementation. Bridges engine state to UI via `TimerStateHolder` StateFlow. |
| `TimerStateHolder.kt` | `MutableStateFlow<TimerServiceState?>`. `update()`/`clear()`. |
| `AndroidAlarmScheduler.kt` | `PlatformAlarmScheduler` impl. Catches `SecurityException` only — other runtime exceptions propagate. |
| `SoundPreviewPlayer.kt` | Audio for ticks, alarms, reminders. `playTick()` has try-catch. |

---

## 3. Rendering Pipeline

```
RenderThread.drawFrame()
  → drainTouchInputFastCopy()
  → SceneManager.update(dt, touchQueue, touchCount)
      → drainInputQueue()
      → drainTouchBuffer()  [try-catch wrapped, no re-throw]
      → RetroHudComponent.consumeSceneCommand()
      → executeCommand(hudCmd)
      → applyPendingSceneSwitch()
      → activeScene.update(dt)
  → surfaceHolder.lockCanvas()  [null-safe]
  → canvas.scale(scaleFactor)
  → engineCanvas.bind(canvas)
  → SceneManager.render(renderer, logicalW, logicalH)
      → renderer.drawRect(0,0,w,h, BG)  [full clear]
      → scene.render(renderer, playX, playY, playW, playH)
  → canvas.restore()
  → [scanlines]
  → surfaceHolder.unlockCanvasAndPost(canvas)  [try-catch in finally]
```

---



## 7. Vector Rendering Layer

### Public API (on `ScaledProceduralRenderer`)
- `drawAliasedCircle(cx, cy, r, colorIdx, strokeWidth=1f, dashed=false)`
- `drawAliasedArc(cx, cy, r, startDeg, sweepDeg, colorIdx, strokeWidth=1f)`
- `drawAliasedProgressArc(cx, cy, r, startDeg, sweepDeg, progress, colorIdx, strokeWidth=1f)`
- `drawAliasedLine(x0, y0, x1, y1, colorIdx, strokeWidth=1f)`
- `drawRadialTickMarks(cx, cy, innerR, outerR, count, startDeg, colorIdx, strokeWidth=1f, majorEvery=0, majorExtra=0f)`
- `drawRadialProgressTickMarks(cx, cy, innerR, outerR, count, activeCount, startDeg, colorIdx, strokeWidth=1f)`
- `drawCubicBezierDeCasteljau(x0,y0,x1,y1,x2,y2,x3,y3,colorIdx,strokeWidth=1f)`
- `drawQuadraticBezierDeCasteljau(x0,y0,x1,y2,y2,y2,colorIdx,strokeWidth=1f)`
- `drawPolarGlyph(char, cx, cy, radius, angleDeg, colorIdx, shadow, scale, tangent)` — places a 16x16 Shinonome glyph at a polar coordinate. When `tangent=true`, the bitmap is rotated by `angleDeg + 90` so its "up" points radially outward (used for rune bands and scripture rings). When `tangent=false`, the glyph is placed upright (tops point up) and centered on the polar point. Reuses a class-level `rotatedGlyphBuffer: IntArray(256)` to avoid per-frame allocation; per-pixel canvas-bounds clipping for the rotated path.
- `drawPolarDot(cx, cy, radius, angleDeg, size, colorIdx)` — places a small filled disc at a polar coordinate. Used for the 12-dot decoration ring.

All delegate to the existing `private val vector = AliasedVectorLayer(canvas)`. Final output is Bresenham-snapped integer pixels via `EngineCanvas.setPixel`/`drawRect`. All loops are bounded (`while (i <= segmentCount)`, `while (true) { if (x0 == x1 && y0 == y1) break }`).

### Demoscene Engine Upgrades (added this pass)

Five new small files in `shared-engine/.../engine/core/` provide procedural animation primitives. All are pure Kotlin, allocation-free in hot paths, deterministic.

| File | Role | LOC |
|---|---|---|
| `FrameClock.kt` | Monotonic frame counter. Replaces wall-clock `getEpochMillis() % N` for ornament phase math. Provides `phase(periodFrames)`, `rotation(degPerFrame)`, `seconds(frameRate)`. Ticked once per frame from `SceneManager.update()`. | ~50 |
| `Wave.kt` | Single-channel sine oscillator. Pre-allocated per layer; `update(dt)` from scene `update()`, `value()` / `valueNorm()` from renderer. Uses `kotlin.math.sin` directly. | ~50 |
| `PerlinNoise.kt` | Deterministic 1D / 2D / fbm Perlin noise. Permutation table generated by `tools/math_oracles/gen_lut.py --kind perm --size 256` (seed=42), emitted to `engine/generated/GeneratedPermLut.kt`. | ~90 |
| `IkChain2D.kt` | 2D FABRIK constraint solver. `solve(start, end, iterations)` resolves a chain of N points with fixed segment lengths. Used for the yin-yang comet trail. | ~80 |
| `Point2D` (in `IkChain2D.kt`) | Value class for 2D coordinates. | (data class) |
| `MagicCircleDemoscene.kt` | Owner of the 6 Wave instances + 1 IkChain2D + Perlin rune drift helper. Created once per `ActiveTimerScene.onEnter()`, ticked each frame. | ~110 |
| `VisualsStateHolder.kt` | Pure-Kotlin in-memory holder for the 2 user-toggleable visuals settings (demoscene effects, background nebula). Read by the magic circle renderer and `MagicCircleDemoscene`. Mutated by the Settings scene UI. | ~25 |
| `engine/generated/GeneratedPermLut.kt` | Generated by `gen_lut.py` — 516-entry Perlin permutation table (256 doubled + 4 padding for 12-aligned output). | (auto) |

**6 Wave instances** (allocated in `MagicCircleDemoscene`, ticked at 60Hz):
- `runeSway` (0.3 Hz) — rune band kanji sway
- `pentaBreath` (0.5 Hz) — outer pentagram radius breath
- `outerHeartbeat` (0.7 Hz) — outer 60-bead ring stroke width pulse
- `innerHeartbeat` (0.7 Hz, π phase offset) — inner 48-bead ring double-beat
- `coreWobble` (1.2 Hz) — yin-yang core scale wobble
- `sectorSwing` (0.6 Hz) — 5 sector kanji radius swing

**All 6 are toggleable** via `VisualsStateHolder.demosceneEffectsEnabled` (default `true`). When off, the Waves freeze but the magic circle still renders as a static 9-layer layout.

### Magic Circle Layer Sequence (in `NestedTimeboxInstrumentRenderer.render`)

The timer instrument is composed of 14 layers drawn back-to-front with 9 explicit non-overlapping radial bands. Every layer has its own continuous rotation multiplier so the components spin independently.

| # | Layer | Radius (U offset) | Color | Alpha | Animation |
|---|---|---|---|---|---|
| 1 | Outer ring (single) | `r` | `MAGIC_PRIMARY` (red) | SOLID | static |
| 2 | Rune band (36 tangent mantra glyphs) | `r - U*0.5` | `MAGIC_SECONDARY` (red) | SCRIPTURE | 0.10x phase + per-idx Perlin drift |
| 3 | Outer detail ticks (36) | `r - U*0.5` to `r - U*0.75` | `BORDER` (gray) | GUIDE | 0.10x |
| 4 | 12-dot decoration ring (4 gold cardinals + 8 gray inter-cardinals) | `r - U*0.75` | `BORDER`/`ACCENT_SECONDARY` (gold) | SOLID/SCRIPTURE | static |
| 5 | Outer timer beads (60) | `r - U*0.75` | `OUTER_ACTIVE` (HIGHLIGHT) | SOLID | 0.10x + heartbeat pulse 0.4x |
| 6 | Scripture ring (10 tangent hardcoded kanji) | `r - U*1.0` | `MAGIC_PRIMARY` (red) | SCRIPTURE | 0.08x phase (× 1.25 divisor) |
| 7 | Outer pentagram (5-pt, double-line) | `r - U*1.0` (primary), `r - U*2.0` (guide) | `TEXT_FRAME` (crimson) | SOLID/GUIDE | 0.40x + breath ±2.5% |
| 8 | 5 sector kanji (龍雀麟虎武, in pentagram arms) | `r - U*1.5` (+sector swing ±3%) | `MAGIC_PRIMARY` | SCRIPTURE | locked to pentagram + sector swing |
| 9 | Octagram (2 squares +45° apart) | `r - U*2.5` | `MAGIC_SECONDARY` (sq1) / `MAGIC_PRIMARY` (sq2) | MECHANICAL | sq1: -0.75x CCW, sq2: +0.90x CW |
| 10 | Inner timer beads (48, dual mode only) | `r - U*2.5` | `INNER_ACTIVE` (ACCENT_SECONDARY) | SOLID | static start + heartbeat 0.4x |
| 11 | Small inner ring | `r - U*3.5` | `MAGIC_PRIMARY` | SOLID | static |
| 12 | Yin-yang core | `r * 0.16` | `MAGIC_PRIMARY`/`MAGIC_SECONDARY` | SOLID | 1.50x + wobble ±4% |
| 13 | Yin-yang comet trail (6 dots, FABRIK-solved) | along trail | `TEXT_FRAME` (crimson) | fade alpha (0xFF→0x30) | lagging core position by 30° |
| 14 | 5 inner cardinals (龍雀麟虎武, static, upright) | `r - U*4.0` | `MAGIC_PRIMARY` | SCRIPTURE | static |
| 15 | Center text readout | `cx, cy` | `TEXT_PRIMARY`/`TEXT_SECONDARY` | SOLID | static (digits stay crisp) |

**Removed (from prior 16-layer version):** inner pentagram (worst ghosting culprit), inner seal kanji, oversized rune band.

**Mantra** (rune band, 36 glyphs): `"時分秒東雲霊魔音弾幕撃程郷"` (14 hardcoded kanji from `ShinonomeFont.GLYPHS`, looped 2.57x).

**Scripture ring** (10 kanji): first 10 of the 14, evenly spaced 36° apart, tangent to the circle.

**Sector kanji** (5 in pentagram arms): `[龍, 雀, 麟, 虎, 武]` at the pentagram vertex angles (-90°, -18°, 54°, 126°, 198°). Tops point radially outward. Locked to pentagram angle.

**Inner cardinal kanji** (5 around small inner ring): same 5 kanji at the inner-pentagram vertex angles (-54°, 18°, 90°, 162°, 234°). Static and upright.

**Color remap** (in `Scenes.kt`): magic circle is rendered with `ACCENT_PRIMARY` (深緋 deep crimson), `ACCENT_DANGER` (緋色 scarlet), and `HIGHLIGHT` (真紅 crimson) for the pentagram frame, matching the red target aesthetic.

**Per-frame workflow** (called from `SceneManager.update()` → `ActiveTimerScene.update()`):
1. `FrameClock.tick()` — increments the frame counter
2. `demoscene.update(dt)` — ticks all 6 Wave oscillators
3. `phase = FrameClock.phase(3600)` — 60s ornament cycle
4. `nestedTimeboxRenderer.render(..., phase, demoscene)` — draws all 14 layers

When `VisualsStateHolder.demosceneEffectsEnabled == false`, the demoscene is a no-op (Waves freeze, Perlin returns 0, FABRIK chain isn't solved). The 14-layer layout still renders, just without the breathing/swaying/trail effects.

---

## 8. Alarm Dismiss Crash — Full Investigation

**Symptom:** App crashes consistently when user taps to dismiss a ringing alarm (both in-app touch and notification action).

**Dismiss flow traced** (every function in the chain read line-by-line):

```
Touch → ActiveTimerScene.onTouch(5-arg) → onInput(5-arg) → onInput(4-arg)
  → SceneManager.timerActions?.dismissAlarm()
  → MainScreenViewModel.dismissAlarm()
  → sendEngineCommand("DISMISS_ALARM") → context.startService(intent)
  → [Android posts to main thread]
  → FocusService.onStartCommand() [MAIN THREAD]
    → engine?.let {
        it.dismissAlarm()           // TimerEngine.dismissAlarm()
        lastTickTimestamp = now
        stopAlarmAudioAndVibe()     // try-catch protected
        broadcastState()            // StateFlow set — safe
        if (active) {
            startTicker()           // coroutine launch — safe
            updateNotification("running")  // builds notification — possible crash vector
        } else {
            stopAlarmAndService()   // full teardown
        }
      }
```

### Hypothesis A — AliasedVectorLayer Bresenham overflow — RULED OUT
- `drawAliasedLineInt` uses standard Bresenham; loop exits on `x0 == x1 && y0 == y1`
- `drawSampledArc` bounded by `segmentCount` (6–144)
- All coordinates derived from `logicalWidth/2` and `logicalHeight * fraction + U*constant` — always positive and within canvas
- `plotStrokePixel` → `AndroidEngineCanvas.setPixel`/`drawRect` — Android Canvas clips
- `FastMath.fastCos`/`fastSin` indexed with modulo-bounded LUT access

### Hypothesis B — SoundPreviewPlayer.playTick() audio crash — RULED OUT
- `playTick()` wrapped in `try { ... } catch (e: Exception)`
- AudioTrack created once, reused; `stop()`/`play()` on released track caught
- Called from StateFlow collector on main thread — sequential, no concurrency

### Hypothesis C — AndroidAlarmScheduler uncaught exception — PLAUSIBLE
- `scheduleExactAlarm()` only catches `SecurityException`; other runtime exceptions propagate
- Call chain: `scheduleExactAlarm()` → `scheduleNextAlarm()` → `dismissAlarm()` → `onStartCommand(DISMISS_ALARM)` — **no try-catch**
- If AlarmManager binder call throws `RuntimeException`/`DeadObjectException`, crashes main thread
- **BUT**: same call succeeds during `TimerEngine.start()` — no crash on timer start
- **Mitigating factor**: `dismissAlarm()` also calls `alarmScheduler.cancelAlarm()` before scheduling; two binder calls in rapid succession

### Hypothesis D — PendingIntent race — RULED OUT
- After Phase 1 fix, `FocusService.scheduleAlarm()` is NOT called from DISMISS_ALARM
- Only `TimerEngine.scheduleNextAlarm()` → `alarmScheduler.scheduleExactAlarm()` runs
- Both use same PendingIntent (request code 0, action ALARM_TRIGGER) — `FLAG_UPDATE_CURRENT` handles duplicates
- Next tick cycle: `cancelAlarm()` + `scheduleAlarm()` properly sync the FocusService alarm

### Hypothesis E — Stale isRinging frame — RULED OUT
- After dismiss, one frame renders with `isRinging = true` (StateFlow async gap)
- Marquee glyphs have explicit `startX=0f` clip bounds + default canvas clipping
- Vector ornament coordinates are all canvas-bounded (see Hypothesis A)

### Remaining suspects (needs logcat to confirm)

| # | Suspect | Location | Mechanism |
|---|---|---|---|
| F | `updateNotification("running")` throws | `FocusService.kt:183` | `buildNotification` → `getOrCreateIconBitmap` → `generatePixelArtIcon` → `Bitmap.createScaledBitmap` OOM or invalid state. Uncaught in `onStartCommand` → main thread crash. |
| G | `stopAlarmAudioAndVibe()` → `SoundPreviewPlayer.stop()` | `FocusService.kt:501` | `stop()` is NOT try-catch protected inside (only the outer call has try-catch on `SoundPreviewPlayer.stop()`, but `stop()` accesses `activeTrack`/`mediaPlayer` which may be in bad state from concurrent alarm playback thread). |
| H | `AppLifecycleTracker.isForeground` called from wrong thread | `TimerStateHolder.kt:57` | `ProcessLifecycleOwner.get()` from non-main thread throws. But called from StateFlow collector on main thread — should be safe. |
| I | `val elapsed = ((now - lastTickTimestamp + 500) / 1000).toInt()` overflow | `FocusService.kt:331` | If `lastTickTimestamp` is stale (e.g., set before a long suspend), `elapsed` could be huge. The `for (i in 0 until elapsed)` loop would iterate hundreds of times, calling `tick()` each time. Not a crash but could trigger cascading alarm events. |

### Critical observation
The `tick()` function at `FocusService.kt:336` uses a `for` loop over `elapsed` seconds:
```kotlin
for (i in 0 until elapsed) {
    if (!eng.isActive || eng.isRinging) break
    ...
    val event = eng.tick()
    broadcastState()
    handleTickEvent(...)
}
```
After dismiss, `lastTickTimestamp` is reset to current time, so `elapsed` is 0 — tick returns early. But if there's a time gap (e.g., the service was suspended), `elapsed` could be large. Each iteration calls `tick()`, `broadcastState()`, and `handleTickEvent()`. If `elapsed = 90` (90 seconds of catch-up), `tick()` decrements the timer 90 times and triggers an `IntervalComplete` event. This would re-trigger the alarm immediately after dismiss — **causing a rapid alarm→dismiss→alarm→dismiss loop that could crash via resource exhaustion**.

**This is the most likely crash mechanism.** The dismiss resets the timer and starts the ticker, but the ticker's first iteration processes a batch of `elapsed` seconds. If `elapsed` is ≥ 1, the for loop runs. If the accumulated elapsed time exceeds the newly-set `timeRemaining` (90s for dual mode), `tick()` would immediately trigger another `IntervalComplete` — the alarm fires again before the user even sees the running timer.

---

## 9. Known Issues (Unrelated to Crash)

| # | File | Issue |
|---|---|---|
| 1 | `SceneManager.kt:142,158` | Conditional `println()` in hot path (gated by debug flags) |
| 2 | `Scenes.kt` onInput (4-arg, 3 scenes) | `val isDown = action == TouchAction.UP` — misleading variable name |
| 3 | `MainScreenViewModel.kt:221` | `dismissAlarm()` calls `startService()` from render thread — dispatches unnecessary intent to already-running service |
| 4 | `AndroidEngineCanvas.kt:146` | `patternCache` uses `ConcurrentHashMap` but `computeIfAbsent` called from render thread only — no concurrency benefit |
| 5 | `Pc98SurfaceView.kt` | `surfaceChanged` creates new renderer even if dimensions unchanged — potential for rapid surface callbacks to leak render threads |
| 6 | `template/forge` | `buildPreset` calls `getStrings(state.language)` at line 1557 — localized inside `onInput` hot path via `strings` parameter, but `buildPreset` re-fetches |

---

## 10. Build Commands

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:compileDebugKotlinAndroid
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :app:assembleDebug
```
