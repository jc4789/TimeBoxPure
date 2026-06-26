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

## 4. Scene Navigation Architecture (Phase 1 — COMPLETE)

### Command Pattern
```
SceneId enum     → ACTIVE_TIMER, TEMPLATES, TEMPLATE_EDITOR, SETTINGS, ENTROPY
SceneCommand     → None | GoTo(sceneId)
SceneManager     → executeCommand(cmd)  [single call site for all transitions]
RetroHudComponent → queues pendingSceneCommand instead of calling switchScene directly
```

### Transition sources
- **HUD** (5 tabs): queues `SceneCommand.GoTo(...)`, SceneManager executes via `consumeSceneCommand()` after drain.
- **In-scene** (5 calls): `TemplateCustomizerScene` → `TemplateForgeScene` (FORGE), → `ActiveTimerScene` (card tap); `TemplateForgeScene` → `TemplateCustomizerScene` (back/save); `EntropyScene` → `ActiveTimerScene` (emergency launch).

### Debug flags cleaned
- `DEBUG_DISABLE_HUD_SCENE_SWITCH` — **removed entirely**
- `DEBUG_DISABLE_PLATFORM_EFFECTS` — `true` → **`false`** (keyboard + haptics work now)
- `DEBUG_DISABLE_SCENE_TOUCH_DISPATCH` — unchanged (`false`, correct)

---

## 5. Template Forge Scroll Refactor (Phase 2 — COMPLETE)

### Before
`TemplateForgeScene` used `PAGE_BASICS`/`PAGE_PARAMS` fake pages with `<`/`>` navigation buttons. No scrolling.

### After
- `PAGE_BASICS`/`PAGE_PARAMS` constants, `page` variable, and page nav buttons **removed**
- Real vertical scrolling: `scrollY`, `isDragging`, `lastTouchY`, `initialTouchX`, `initialTouchY`, `hasDragged`
- `onTouch` 5-arg handles DOWN/MOVE/UP/CANCEL drag (same pattern as `TemplateCustomizerScene`)
- `onInput` 4-arg: `isDown` → `isUp` (taps on UP); `fy` offset by `scrollY`
- All form fields rendered sequentially in one scrollable column
- `contentMinScroll()` calculates scroll bounds from row count + mode params
- Header cover rect prevents scroll content from showing above header
- Save button remains fixed footer; cancel button remains fixed header

---

## 6. Glyph Clipping Hardening (Complete)

### Before
`drawGlyph` and `drawText` defaulted `startX = destX`, `startY = destY`. Negative destination positions (e.g., marquee scrolling at `x = -500`) shifted the clip rectangle negative, allowing pixel writes outside canvas bounds.

### After
Both `drawGlyph` overloads and both `drawText` overloads default `startX = 0f`, `startY = 0f`. Canvas-space clipping is the default. Callers needing text-box clipping pass explicit bounds. The marquee alarm overlay calls include explicit `startX = 0f, startY = 0f, clipWidth = logicalWidth.toInt(), clipHeight = logicalHeight.toInt()` as self-documenting intent.

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
- `drawQuadraticBezierDeCasteljau(x0,y0,x1,y1,x2,y2,colorIdx,strokeWidth=1f)`

All delegate to the existing `private val vector = AliasedVectorLayer(canvas)`. Final output is Bresenham-snapped integer pixels via `EngineCanvas.setPixel`/`drawRect`. All loops are bounded (`while (i <= segmentCount)`, `while (true) { if (x0 == x1 && y0 == y1) break }`).

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
