# Scope

- Read-only audit of the current commonMain magic-circle path and its introduction history.
- Constraints applied: no clock, elapsed-time service, frame counter, scene-local animation clock, angle-from-time, or angle-from-frame; preserve the accepted radius/layout; do not touch UI scaling, HUD placement, or platform wrappers.
- Relevant law: commonMain remains authoritative; update/render hot paths remain explicit, primitive, allocation-free Kotlin. Target source set is `shared-engine/src/commonMain`; no platform interop or external data is involved.
- Production code was not edited by this audit.

# Confirmed

## Current ownership is fractured

- `ActiveTimerScene` owns a nullable `MagicCircleDemoscene` instance, constructs/resets it in `onEnter`, advances it in `update(dt)`, and passes it into the renderer (`ActiveTimerScene.kt:30-32`, `47-49`, `56-67`, `151-177`).
- `ScaledProceduralRenderer` separately owns the actual `NestedTimeboxInstrumentRenderer` (`ScaledProceduralRenderer.kt:173`).
- `NestedTimeboxInstrumentRenderer.render` then reads six angle fields and rune drift through the scene-owned nullable object (`NestedTimeboxInstrumentRenderer.kt:98-123`, `155-160`, `186-203`).
- Complete current paths:
  - mutation: `SceneManager.update(dt, ...)` -> `activeScene?.update(dt)` (`SceneManager.kt:109-133`) -> `ActiveTimerScene.update(dt)` -> `MagicCircleDemoscene.update(dt)` (`ActiveTimerScene.kt:56-67` -> `MagicCircleDemoscene.kt:95-112`);
  - drawing: `SceneManager.render(...)` -> `scene.render(...)` (`SceneManager.kt:145-160`) -> `ActiveTimerScene.render(...)` -> `renderer.nestedTimeboxRenderer.render(...)` (`ActiveTimerScene.kt:70-177`) -> `NestedTimeboxInstrumentRenderer.render(...)` (`NestedTimeboxInstrumentRenderer.kt:98-380`).
- Therefore the current worktree does not have one magic-circle owner: scene lifecycle owns effect state, `ScaledProceduralRenderer` owns the renderer object, and `NestedTimeboxInstrumentRenderer` owns geometry/rasterization.

## The current worktree merely moved the rejected clock into another state object

- The uncommitted change adds six mutable angle accumulators plus rune/nebula phases to `MagicCircleDemoscene` (`MagicCircleDemoscene.kt:18-32`).
- Those are advanced by `degreesPerSecond * dt` and `rate * dt` (`MagicCircleDemoscene.kt:95-103`, `163-183`). This is still time-driven phase, only with a different owner/name.
- The renderer reads those accumulated phases (`NestedTimeboxInstrumentRenderer.kt:155-160`). This violates the stated no-time/no-frame requirement and should not be retained.
- Deleting `FrameClock.kt` and removing both `FrameClock.tick()` calls from `SceneManager` is directionally correct (current diff; former calls were in `SceneManager.update` overloads). It does not make the added `dt` phase accumulation valid.

## Most of `MagicCircleDemoscene` is unreachable scaffolding

- Six `Wave` instances are created and updated (`MagicCircleDemoscene.kt:34-71`, `105-111`), but repository-wide reference search shows no renderer read of `runeSway`, `pentaBreath`, `outerHeartbeat`, `innerHeartbeat`, `coreWobble`, or `sectorSwing`.
- `trail`, `trailAlpha`, and `trailSize` are created (`MagicCircleDemoscene.kt:73-87`), and `solveTrail` exists (`120-123`), but none is called or read outside that class. The visible four-dot trail is independently computed directly inside `NestedTimeboxInstrumentRenderer` (`NestedTimeboxInstrumentRenderer.kt:316-337`). The advertised FABRIK trail and the rendered trail are two fractured implementations.
- `IkChain2D.solve` is not allocation-free despite its comment: it repeatedly assigns newly constructed `Point2D` values inside both iterative passes (`IkChain2D.kt:31-78`, especially `41`, `51-54`, `60`, `70-73`). It is currently unreachable, so deletion is safer and smaller than repairing an unused gimmick.
- These files were all introduced together by commit `15a3c4f657f0b23b44a1c671d865e47128a99869`: `FrameClock.kt`, `MagicCircleDemoscene.kt`, `Wave.kt`, `IkChain2D.kt`, `PerlinNoise.kt`, `VisualsStateHolder.kt`, and `GeneratedPermLut.kt`. `git log --follow` shows no independent earlier history for `MagicCircleDemoscene.kt`.
- At introduction, only `runeDriftAngleOffset` was read by the renderer; repository search in the introduction commit finds no reads of the six Waves or FABRIK arrays/solver. The scaffolding was dead from its introduction (`15a3c4f`: `NestedTimeboxInstrumentRenderer.kt` references at its then-lines 110, 132-137, 170).

## The “nebula” is another cross-owned time gimmick, not a raster nebula

- `ActiveTimerScene.nebulaColorIndex` asks the magic-circle state object for only two Perlin samples, averages them, and selects one flat background palette index (`ActiveTimerScene.kt:746-775`). It does not render spatial nebula pixels.
- The current uncommitted version advances `nebulaPhase` from `dt` (`MagicCircleDemoscene.kt:102-103`) and samples it (`154-160`), so it is also disallowed time-driven phase.
- Its settings and state are spread across `SettingsScene` (`SettingsScene.kt:264-280`, `386-405`, `428-432`), `VisualsStateHolder` (`VisualsStateHolder.kt:12-27`), localized `AppStrings` fields (`Strings.kt:110-112` and language initializers), `ActiveTimerScene`, `MagicCircleDemoscene`, `PerlinNoise`, and `GeneratedPermLut`. Keeping a static or renamed remnant would preserve the cluster rather than simplify it.

## Accepted geometry can be preserved independently

- Accepted radius/layout is localized in `NestedTimeboxInstrumentRenderer.render`: `GRAPHICS_SOURCE_CELL`, `GRAPHICS_SOURCE_RADIUS`, `GRAPHICS_REFERENCE_RADIUS`, boundary pad, output-space radius selection, center clamp, and all derived radii (`NestedTimeboxInstrumentRenderer.kt:38-44`, `128-178`).
- None of those calculations requires `MagicCircleDemoscene`, a clock, `dt`, or a frame number.
- Timer bead counts/readouts are functional render inputs (`outerProgress`, `innerProgress`, remaining-time fields), not animation phase ownership. They can remain exactly where they are (`NestedTimeboxInstrumentRenderer.kt:104-121`, `229-237`, `297-307`, `358-376`). No rotation angle should be derived from them.

# Rejected

- Keep `FrameClock` with repaired internals: rejected; the API and the frame-derived model are explicitly invalid.
- Add `EngineTime`, a platform clock, an elapsed-time service, or a new interface: rejected; this adds another owner/API.
- Put an elapsed accumulator in `ActiveTimerScene`: rejected; this splits graphics state between scene and renderer.
- Put `dt`-advanced angles inside `MagicCircleDemoscene` or `NestedTimeboxInstrumentRenderer`: rejected; `angle += speed * dt` is time-based regardless of ownership.
- Advance a fixed angle per `update` or per `render`: rejected; that is frame-count animation with implicit counting and changes speed with update/render frequency.
- Derive rotation from `timeRemaining`, `outerProgress`, or `innerProgress`: rejected for angle phase; these are time-derived and update discretely, so they reintroduce time-based, visibly stepped rotation. Retain them only for the existing functional bead/readout display.
- Introduce a fixed-step simulation tick: rejected; it is still a time/frame progression subsystem and is larger than the feature.
- Keep `MagicCircleDemoscene` as an empty façade around static constants: rejected; it preserves a redundant owner and nullable parameter without behavior.
- Move magic-circle behavior into `ActiveTimerScene`, HUD, UI-scaling code, `EngineCanvas`, or platform wrappers: rejected; it violates the graphics ownership boundary and expands scope.
- Rename or replace `NestedTimeboxInstrumentRenderer` with a new public API merely for aesthetics: rejected; the smallest coherent refactor can retain the existing renderer and shrink its signature.

# Unknown

- There is no user-provided desired fixed orientation for each layer. The renderer contract itself says the two octagram squares are 45 degrees apart (`NestedTimeboxInstrumentRenderer.kt:18`, `283-286`), while the current uncommitted initial values set both to `-90f` (`MagicCircleDemoscene.kt:24-27`) and therefore initially overlap. The contract supports `-90f` and `-45f`; a different art-directed pair would require user evidence.
- No runtime screenshot proves whether the four-dot direct trail is desired as ornament. It is not FABRIK and does not require a clock; retaining it preserves the accepted current composition better than deleting it.
- `visualsHeader` may be intended for future non-demoscene settings, but it currently heads only the two gimmick toggles (`SettingsScene.kt:386-405`). If both toggles are removed now, retaining an empty header has no reachable purpose.

# Recommendation

## Smallest coherent model

Make `NestedTimeboxInstrumentRenderer` the sole owner of the magic-circle composition. It remains a stateless, deterministic procedural graphics renderer. `ActiveTimerScene` supplies only viewport bounds and the timer snapshot already needed by beads/readouts. There is no magic-circle update path.

Final call paths:

- update: `SceneManager.update` -> `ActiveTimerScene.update`; only the scene's real responsibilities (alarm marquee and cursor) update. No magic-circle call.
- render: `SceneManager.render` -> `ActiveTimerScene.render` -> existing `ScaledProceduralRenderer.nestedTimeboxRenderer` -> `NestedTimeboxInstrumentRenderer.render` -> existing aliased palette-index primitives.

Rotation resolution: **phase does not advance**. With time, frame, fixed tick, and scene-local accumulator all prohibited, automatic smooth rotation has no legal progression source. Any alternative would hide one of those sources under another name. Render the composition at named fixed phase constants owned by `NestedTimeboxInstrumentRenderer`:

- scripture/rune phase: `-90f`;
- outer detail phase: `-90f`;
- pentagram/sector phase: `-90f`;
- octagram square phases: `-90f` and `-45f` to satisfy the file's explicit “+45 degrees apart” contract;
- yin-yang phase: `0f`.

These are orientation constants, not mutable state. Rune Perlin offset becomes `0f`. The existing direct four-dot trail may remain at the fixed yin-yang phase with its existing progress-derived alpha; it is already renderer-owned and preserves composition. All accepted radius/center/cell calculations at `NestedTimeboxInstrumentRenderer.kt:128-178` remain byte-for-byte unchanged.

## Exact file disposition

Delete completely (introduced for, and only reachable through, the rejected gimmick system):

- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/FrameClock.kt` (already deleted in worktree)
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/MagicCircleDemoscene.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Wave.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/IkChain2D.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/PerlinNoise.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/VisualsStateHolder.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/generated/GeneratedPermLut.kt`

Modify minimally:

- `ActiveTimerScene.kt`: remove `demoscene` field, allocation/reset, update call, renderer argument, time/nebula comments and `nebulaColorIndex`; retain all accepted viewport/radius/layout inputs and timer data.
- `NestedTimeboxInstrumentRenderer.kt`: remove nullable `demoscene` parameter and all reads; use the named fixed orientation constants above; remove stale demoscene/rotation claims; retain all geometry/radius math and direct procedural drawing.
- `SettingsScene.kt`: remove the now-dead visuals header/toggle rows from touch, render, and measurement paths.
- `Strings.kt`: remove `visualsHeader`, `demosceneLabel`, and `nebulaLabel` fields and their three language values because no caller remains.
- `tools/math_oracles/gen_lut.py`: remove only the `perm` mode/seed path added in `15a3c4f`; preserve all pre-existing waveform generation.
- `ProceduralMath.kt`: remove only the stale comment claiming it is kept for `IkChain2D`; do not alter its unrelated math/raster code.

Retain:

- `NestedTimeboxInstrumentRenderer.kt` as the single magic-circle owner.
- `ScaledProceduralRenderer.nestedTimeboxRenderer` and existing aliased/palette-index drawing primitives.
- `ActiveTimerScene` only as caller/orchestrator.
- `outerProgress`, `innerProgress`, and timer readout inputs for their existing functional display, with no use as angle phase.
- Current graphics radius, graphics cell, center clamp, viewport bounds, HUD/layout selection, platform wrappers, and UI scaling unchanged.
- The unrelated current `IndexedFramebuffer`/`SoftwareGraphics` fill optimizations should be reviewed separately; they are not part of this ownership recommendation.

This removes the mutation path, seven feature-only files, dead hot-loop scaffolding, two dead settings, the generated Perlin table/tool branch, and the nullable cross-owner renderer argument. It introduces no API, service, interface, allocation, platform dependency, or replacement framework.
