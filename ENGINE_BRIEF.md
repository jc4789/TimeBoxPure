# ENGINE_BRIEF

## Current Engine Laws
- Canonical Unit: `U = glyphWidth = glyphHeight`; layout derives from `U`.
- Color: core engine uses palette indices `0..15` only.
- Display: layout derives from `logicalWidth` and `logicalHeight`; no orientation assumptions.
- Asset: no runtime PNG/JPG/SVG/TTF/WAV/JSON/XML assets.
- Platform Firewall: platform code provides surfaces/input/audio only; core owns logic and rendering.

## Hot Path Files
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/Scenes.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/RetroHudComponent.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ScaledProceduralRenderer.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/TimerEngine.kt`
- `shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core/ChiptuneSynthesizer.kt`

## Platform Wrappers
- Android: `app/src/main/java/com/example/timeboxvibe/...`
- iOS: KMP platform source set
- Win32: KMP platform source set

## Known Failure Modes
- HUD must be explicitly delegated by each scene; no global HUD hooks in `SceneManager`.
- Template forge/list split can break HUD cards-tab routing if forge is treated as a no-op tab.
- IMGUI rows overlap when label/control layout does not derive from `U` and measured label width.
- Compose-style assumptions drift into engine code as hardcoded breakpoints or orientation hacks.
- Render/input helper paths in scenes can still allocate strings in hot paths and need periodic audits.

## Current Constants
- `U = 16f`
- `PALETTE_SIZE = 16`
- `MIN_SAFE_LOGICAL_WIDTH = 320`
- `MAX_SAFE_LOGICAL_WIDTH = 1200`

## Current Task Focus
- Stabilize HUD routing after the template list/forge split.
- Keep explicit scene-owned HUD render/input delegation intact.
- Continue reducing layout drift and hot-path allocations in `Scenes.kt`.
