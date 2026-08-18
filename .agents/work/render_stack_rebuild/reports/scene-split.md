# Scope

- Read-only audit target: `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt`, `SceneManager.kt`, `RetroHudComponent.kt`, `TimerActions.kt`, and `TimerEngine.kt`.
- Goal: provide a compile-safe decomposition map separating business state/actions, input routing, immediate-mode UI layout, and graphics without changing reachable behavior.
- Constraint: HUD placement is a user setting (`LEFT` or `BOTTOM`). It must never be inferred from platform, device, orientation, resolution, or aspect ratio.
- Production code was not edited. This report is the only output.

# Confirmed

## Current ownership and reachable call path

1. `SceneManager` owns scene lifecycle, queued keyboard input, touch-buffer draining, logical bounds, play-area derivation, platform effects, and HUD command consumption in one singleton (`SceneManager.kt:17-324`).
2. The frame path is `SceneManager.update(dt, touchBuffer, touchCount)` -> `drainInputQueue()` -> `drainTouchBuffer()` -> `RetroHudComponent.onTouch(...)` -> `dispatchTouch(...)` -> `Scene.onTouch(...)`; after draining it consumes `RetroHudComponent.consumeSceneCommand()`, switches scenes, then calls `activeScene.update(dt)` (`SceneManager.kt:115-158`, `SceneManager.kt:225-275`).
3. The render path is `SceneManager.render(...)` -> `setLogicalBounds(...)` -> `Scene.render(renderer, playArea...)` (`SceneManager.kt:160-188`). Every real scene then calls `RetroHudComponent.render(...)` itself: Active Timer (`Scenes.kt:311`), Templates (`Scenes.kt:1038`), Forge (`Scenes.kt:1413`), Settings (`Scenes.kt:2249`), and Entropy (`Scenes.kt:2893`). Thus scene content owns shell/chrome rendering today.
4. HUD input is also called from every real scene through `RetroHudComponent.onTouchEvent(...)`: Active Timer (`Scenes.kt:475-476`), Templates (`Scenes.kt:1095-1096`), Forge (`Scenes.kt:1476-1477`), Settings (`Scenes.kt:2306-2307`), and Entropy (`Scenes.kt:2941-2942`). In normal `TOUCH_MODE_FULL`, `SceneManager.drainTouchBuffer()` first calls the pure HUD hit-test `onTouch(...)` and ignores its result, then dispatches to the scene, which calls `onTouchEvent(...)` and performs navigation (`SceneManager.kt:254-269`, `SceneManager.kt:317-320`).
5. `SceneManager.setLogicalBounds()` asks `RetroHudComponent` to derive content geometry (`SceneManager.kt:180-188`), while `RetroHudComponent` reads `SceneManager.logicalWidth`, `logicalHeight`, and `activeScene`, calls `SceneManager.performHapticFeedback`, and stores `SceneCommand` for `SceneManager` to consume (`RetroHudComponent.kt:30-74`, `RetroHudComponent.kt:125-174`). This is a concrete bidirectional dependency: `SceneManager <-> RetroHudComponent`.
6. `RetroHudComponent.layoutMode(width,height)` currently selects left or bottom from available geometry and scores (`RetroHudComponent.kt:272-287`). `usesLeftHud()` exposes that decision (`RetroHudComponent.kt:38-39`). This is current aspect/size policy, not a setting.
7. Scenes infer a pseudo-orientation from HUD geometry (`playX <= 0`) rather than receiving an explicit layout choice: Active Timer (`Scenes.kt:132`, `425`, `487`), Templates (`Scenes.kt:1045`), Forge (`Scenes.kt:1325`, `1430`, `1483`), Settings (`Scenes.kt:2472-2477`), and Entropy (`Scenes.kt:2761`, `2947`). Consequently HUD placement changes content behavior and spacing indirectly.
8. `Scenes.kt` is 3,435 lines and contains the `Scene` contract plus seven singleton scenes: `MainMenuScene` (`31`), `ActiveTimerScene` (`44`), `TemplateCustomizerScene` (`903`), `TemplateForgeScene` (`1221`), `SettingsScene` (`2193`), `EntropyScene` (`2677`), and `BlockOverlayScene` (`3320`), plus the calendar drawing helper at `3385`. These objects combine mutable UI state, input gesture state, hit testing, TimerActions calls, layout math, and raster commands.

## Business boundary that already exists

- `EngineUiState` is the read model consumed by scenes; `TimerActions` is the mutation port (`TimerActions.kt:5-55`). Scenes do not directly mutate `TimerEngine`.
- `TimerEngine` owns timer/preset rules and timer state transitions: preset validation/normalization (`TimerEngine.kt:12-223`), initialization (`282-324`), start/pause/reset/preset replacement (`326-364`), ticks and completion rules (`367-503`), alarm dismissal (`505-530`), expired-block advancement (`532-646`), skip (`648-666`), and explicit stage advancement (`668-690`). This is business logic and should remain outside UI, IMGUI, graphics, and scene routing.
- The exact scene-to-business calls are:
  - Active Timer: `getUiState`, `updateTask`, `dismissAlarm`, `resetTimer`, `stopTimer`, `startTimer`, `skipTimer` (`Scenes.kt:93`, `108`, `124`, `416`, `450`, `484`, `503`, `540`, `547-557`).
  - Templates: `getUiState`, `deletePreset`, `selectPreset` (`Scenes.kt:926`, `1074`, `1100`, `1152-1160`).
  - Forge: `getUiState`, `upsertCustomPreset` (`Scenes.kt:1299`, `1319`, `1480`, `1652`, `1674`, `1986`).
  - Settings: `getUiState`, language/volume/sound/settings/theme/permission mutations (`Scenes.kt:2236`, `2281`, `2311`, `2327-2449`).
  - Entropy: `getUiState`, then the emergency launch sequence `selectPreset("emergency")`, `updateTask(...)`, `startTimer()` (`Scenes.kt:2707`, `2752`, `2946`, `2979-2981`).

## Per-scene tangles that must be separated without changing behavior

- `ActiveTimerScene`
  - UI/input state: focus, fixed text buffer, scroll and drag fields (`Scenes.kt:65-78`); text mutation and commit (`401-421`); gesture recognition (`424-473`); hit testing/action dispatch (`475-567`).
  - Layout: timer/input/control/calendar geometry (`569-768`) plus substantial inline geometry in `render` (`121-310`).
  - Graphics state/rendering: `EngineCursorRenderer`, `MagicCircleDemoscene`, nested timer artwork and alarm overlay (`Scenes.kt:68`, `78`, `96-100`, `107-399`).
  - Business dependency: TimerActions calls listed above.
- `TemplateCustomizerScene`
  - UI/input state: scroll/drag (`Scenes.kt:904-909`, `1044-1093`).
  - Layout and graphics are interleaved in `render` (`923-1040`) and repeated in hit testing (`1095-1168`); layout helpers are `templateMinScroll` and `templateCardHeight` (`1170-1220`).
  - Business dependency: select/delete preset; navigation to Forge (`Scenes.kt:1100-1160`).
- `TemplateForgeScene`
  - Form/controller state begins at `Scenes.kt:1222`; text input routing is `1416-1427`; gesture routing is `1429-1474`; action hit testing occupies `1476-1658`.
  - Rendering/layout is `1316-1414` plus row geometry helpers `2013-2159`.
  - Form-domain conversion is already identifiable: validation/build/load/parse and calendar block editing (`isForgeValid`, `buildPreset`, `loadPreset`, `parseSequenceValues`, `addCalendarBlock`, `deleteCalendarBlock`: `Scenes.kt:1749-2012`). These are not raster graphics.
- `SettingsScene`
  - Gesture and action dispatch are `2255-2469`; render is `2233-2251`; layout is statefully duplicated through `beginSettingsLayout`, `layoutRow`, `drawSettingsRows`, and `measureSettingsRows` (`2471-2625`).
  - `beginSettingsLayout()` directly re-queries HUD placement and derives “portrait” from it (`Scenes.kt:2471-2477`), proving shell policy leaked into scene layout.
- `EntropyScene`
  - Scene-local behavior/input buffers/random/countdown state begins at `Scenes.kt:2678`; update behavior is `2727-2747`; render is `2749-2895`; input/action dispatch is `2927-3091`; task-buffer and pagination helpers are `3093-3309`.
  - Emergency timer business action is a three-call transaction in input handling (`2979-2981`); visuals must not own it.
- `BlockOverlayScene`
  - Overlay render and return hit-test are combined with direct scene switching (`Scenes.kt:3320-3383`).

# Rejected

- Do not put HUD placement in a platform adapter, device-profile table, DPI rule, width/height threshold, orientation check, or aspect-ratio heuristic. The current `RetroHudComponent.layoutMode(width,height)` behavior is explicitly rejected for the target architecture (`RetroHudComponent.kt:272-287`).
- Do not let every scene render or hit-test the global HUD. That preserves the current circular dependency and duplicated routing (`Scenes.kt:311`, `476`, `1038`, `1096`, `1413`, `1477`, `2249`, `2307`, `2893`, `2942`).
- Do not move timer state transitions into scene controllers or IMGUI widgets. `TimerEngine` and platform `TimerActions` implementations remain the authority; the UI only emits existing action calls.
- Do not make `ScaledProceduralRenderer` aware of scenes, HUD tabs, `TimerActions`, navigation, touch, settings, or play-area policy. It is the graphics boundary, not the application coordinator.
- Do not introduce a general UI framework, DI container, ECS, retained widget tree, reflective registry, or collection-based per-frame command graph. The required split can be direct Kotlin objects/data holders with explicit calls.
- Do not combine the first physical file split with behavior changes. Moving declarations first gives a mechanically reviewable and compile-safe checkpoint.

# Unknown

- There is no HUD-placement field or mutation in the audited `EngineUiState`/`TimerActions` contract (`TimerActions.kt:5-55`). The authoritative setting name, default (`LEFT` or `BOTTOM`), and persistence owner are therefore not present in this scope.
- It is not established whether HUD placement belongs in the same persisted settings store used by both Android and Win32 implementations. Both `TimerActions` implementations must be updated consistently once that owner is chosen.
- `Scene.onInput(x,y,...)` is a default no-op overload distinct from `onTouch(...)` (`Scenes.kt:18-25`), but scenes use it as “tap/action handling.” The public name can be cleaned only after all call sites are migrated; renaming it during the initial split adds unnecessary risk.
- The target primitive framebuffer dimensions and presentation transform are outside these five files. Scene separation can remove scaling policy from scenes, but cannot alone prove final device scaling.

# Recommendation

## Target dependency direction

```text
TimerEngine <- platform TimerActions implementation <- Scene controller/action handler
                                                     ^
platform touch -> InputRouter -> UiShell -> scene input + navigation
                                  |
                                  v
                         shared UiLayout rectangles
                                  |
                                  v
                  scene view / HUD view -> Graphics API -> framebuffer
```

No arrow may point from Graphics or layout back to `TimerActions`, `SceneManager`, platform code, or touch state.

## Concrete ownership map

1. `SceneContracts.kt`
   - Own `TouchAction`, `Scene`, and a small explicit immutable/mutable frame context if needed.
   - Preserve the current lifecycle signatures during the first move. Do not redesign and split simultaneously.
2. One source file per existing scene object, retaining object names and package so `SceneManager.sceneRegistry` compiles unchanged:
   - `ActiveTimerScene.kt`
   - `TemplateCustomizerScene.kt`
   - `TemplateForgeScene.kt`
   - `SettingsScene.kt`
   - `EntropyScene.kt`
   - `BlockOverlayScene.kt`
   - `MainMenuScene.kt`
   - `drawCalendarTimeline` is shared by Active Timer and Templates (`Scenes.kt:651`, `1000`, `3385`). During a visibility-preserving mechanical split, keep an identical file-private copy beside each consumer; a shared non-private helper would be a later visibility/design change.
3. `UiShell`
   - Sole owner of HUD placement, content rectangle, HUD rectangle, HUD render order, and HUD-first input routing.
   - Input: logical framebuffer bounds + explicit `HudPlacement` setting. Output: one cached `UiShellLayout` containing content and HUD rectangles.
   - It renders `activeScene` content and then the HUD once. It hit-tests HUD once and either emits navigation or forwards the event to the active scene. Scenes never call HUD code.
4. `HudPlacement` / setting port
   - Define exactly two domain values, `LEFT` and `BOTTOM`.
   - Add placement to the settings read model and one mutation to the settings action port only after the persistence/default decision is supplied. `UiShellLayout.resolve(...)` must switch exclusively on that value; width/height may size the chosen region but may not choose placement.
5. `RetroHudView` (replacement boundary for `RetroHudComponent`)
   - Pure IMGUI shell component: `layout(bounds, placement, outLayout)`, `render(graphics, layout, activeSceneId)`, and `hitTest(layout, x, y): SceneCommand`.
   - It does not store `pendingSceneCommand`, read `SceneManager`, call haptics, or switch scenes. Navigation/haptics are handled by `UiShell`/scene coordinator after a returned command.
6. `SceneManager`
   - Own only scene registry, lifecycle/switching, current `SceneId`, queued scene changes, and calls to scene update/render.
   - Move input queue/draining and engine-touch translation into `InputRouter`; move logical bounds/play-area calculation into the framebuffer/UI shell; move haptic/keyboard calls behind an injected effects port already represented by `PlatformInputTrigger`.
   - Replace runtime type matching in `sceneName()` and HUD active-tab detection with the registry/current `SceneId` (`SceneManager.kt:296-310`, `RetroHudComponent.kt:65-73`).
7. Per scene, use a direct three-part split, not a framework:
   - `<Scene>Controller`: owns mutable form/gesture/scene-local behavior and invokes `TimerActions`/navigation/effects.
   - `<Scene>Layout`: computes named rectangles/positions once into a reusable layout holder. Both render and hit-test consume the same result; no duplicated coordinate formulas.
   - `<Scene>View`: raster-only code consuming `EngineUiState`, strings, layout, and explicit animation state. It may call procedural graphics helpers but cannot read `SceneManager` or invoke actions.
8. Graphics-specific extraction priorities:
   - Active Timer: move nested instrument, background nebula, calendar panel, control visuals, and alarm overlay into `ActiveTimerView`/small artwork helpers; keep demoscene/cursor animation state explicitly owned by the view/controller rather than global graphics.
   - Forge/Settings: layout rows return named hit rectangles; drawing and touch use those same rectangles.
   - Entropy: keep task-buffer/random/countdown logic in the controller; rendering only receives a snapshot plus cached layout.
   - HUD icons and all scene artwork call the same renderer/graphics primitives. Graphics never owns UI state or layout policy.

## Compile-preserving migration order

1. **Mechanical file split only.** Move `Scene`, `TouchAction`, every scene object, and `drawCalendarTimeline` out of `Scenes.kt` without changing signatures, visibility, or code. Add file-local `private const val U = CANONICAL_UI_UNIT` where required. Compile. This removes the god file while preserving all dependencies.
2. **Introduce explicit current scene identity.** Make the registry store/track `SceneId` alongside the existing `Scene`; retain existing overloads temporarily. Replace `sceneName()` type matching and HUD active-tab type checks with `currentSceneId`. Compile.
3. **Make HUD stateless before moving it.** Change HUD render/hit-test functions to accept logical bounds, active `SceneId`, and explicit placement; return `SceneCommand` directly. Remove `pendingSceneCommand` only after callers consume the direct result. Keep the current chosen placement temporarily supplied as one explicit constant/default so no aspect policy remains hidden. Compile.
4. **Add `UiShellLayout` and single shell ownership.** Compute content/HUD rectangles once per logical framebuffer size + placement. `SceneManager.render` (or a new `UiShell.render`) renders the scene then HUD once. Remove `RetroHudComponent.render` calls from scenes. Compile.
5. **Centralize HUD input.** Route HUD hit-test before scene dispatch in `InputRouter`/`UiShell`; if consumed, do not dispatch to the scene. Remove every scene call to `onTouchEvent` and the ignored preliminary `RetroHudComponent.onTouch` call. Preserve UP-only navigation and current haptic behavior (`RetroHudComponent.kt:140-174`). Compile.
6. **Make placement a real setting.** Add the chosen persistent setting field/mutation to `EngineUiState`/`TimerActions` and both platform implementations, then feed it to `UiShellLayout`. Remove `layoutMode`, `usesLeftHud`, and every `playX <= 0` pseudo-orientation branch. Branch on explicit placement where the two layouts genuinely differ. Compile on all supported targets.
7. **Extract shared per-scene layout holders, one scene at a time.** Start with Active Timer because its render and hit-test duplicate timer/input/button geometry (`Scenes.kt:121-310`, `484-567`). Next Templates, Forge, Settings, Entropy, then overlay. For each scene: calculate layout once, switch render to it, switch input to it, then delete old formulas. Compile after each scene.
8. **Extract scene controllers/views, one scene at a time.** Move TimerActions/navigation/effects into controllers and raster calls into views. Keep `TimerEngine` unchanged. Compile after each scene.
9. **Move queue translation out of `SceneManager`.** Once shell input owns consumption, extract `ConcurrentIntegerQueue` and touch-buffer decoding into `InputRouter`; leave `SceneManager` as lifecycle/navigation only. Compile.
10. **Final dependency audit.** Required searches should show: no `RetroHud` references in scene files; no `SceneManager` or `TimerActions` references in graphics/view files; no renderer calls in controllers/input; no width/height/orientation/aspect decision selecting HUD placement; no direct `TimerEngine` reference from UI.

This order keeps the project buildable at every checkpoint and attacks cycles before aesthetic rewrites. The load-bearing rule is that one explicit `UiShellLayout` is authoritative for both rendering and hit testing, while `TimerEngine` remains the timer authority and the graphics layer remains a one-way raster sink.
