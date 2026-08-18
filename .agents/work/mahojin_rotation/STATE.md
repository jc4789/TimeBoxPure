# Objective
Refactor the magic-circle graphics into one clean commonMain owner. Remove broken gimmicks while preserving the accepted radius/layout and the six previously-correct independent relative rotation speeds.

# Constraints
- No duplicate/fractured feature ownership or spaghetti.
- "Carmack but ZUN" and "C++ but Kotlin": explicit primitive state, allocation-free update/render, procedural indexed graphics.
- Magic-circle graphics are not UI; do not change UI scaling, HUD, platform wrappers, or radius/layout.
- No `FrameClock`, `EngineTime`, elapsed-time service, frame counter, or scene-local duplicate animation clock. The graphics owner advances its own six primitive phases from the existing scene `dt` update.

# Plan
- [x] Trace every reachable magic-circle feature and classify keep/delete/consolidate.
- [x] Implement one graphics-owned update/render path and remove dead gimmicks.
- [x] Audit the final diff for ownership, hot-loop, palette/raster, and forbidden global timing paths.
- [x] Run existing tests/builds and verify Android runtime behavior without adding infrastructure.

# Confirmed
- `ActiveTimerScene` owns exactly one `NestedTimeboxInstrumentRenderer`; its existing `update(dt)` and `render(...)` paths address that same object.
- The rejected `FrameClock` path derived seconds from `frameCount / 60` and slowed when exact-framebuffer rendering reduced FPS.
- Current Android radius/layout is accepted and must remain untouched.
- Worktree already contains redundant-clear/fill hot-path reductions; they improved Pixel 10 Active Timer from about 28 FPS to about 54 FPS after warmup.
- All six `Wave` values had zero readers. FABRIK solve/points/fade arrays had zero readers; the visible trail was a separate renderer-local four-dot ornament.
- Indexed alpha is binary draw enable, so every advertised partial-alpha/fade distinction was a no-op.
- Perlin was reachable only through rune drift and the two-sample flat background color gimmick.
- The retained renderer owns six independent primitive angle fields. Their previous relative speeds remain exactly `4 / 3 / 12 / -15 / 20 / 40` degrees per second.
- The accepted radius/center/ring math remains unchanged.
- The deleted subsystem comprises `FrameClock`, `MagicCircleDemoscene`, six unread `Wave` values, unused FABRIK/IK, Perlin drift/nebula, fake alpha fades, `VisualsStateHolder`, and the unused generated permutation LUT.
- Existing Android/JVM, Win32 tests/link, and Android APK assembly pass under the required JBR.
- Pixel 10 runtime reaches about 53 FPS after warmup with no Android runtime exception. Two screenshots separated by two seconds show distinct angular displacement for the six independently rotating layers.

# Rejected
- Global `EngineTime`: adds a second clock API.
- Scene-private elapsed accumulator: fractures graphics state into the scene.
- Angle derived from a global elapsed time or frame number: wrong model and creates cross-cutting timing ownership.
- Fixed/static orientations: rejected after direct device verification because the graphic must rotate.
- UI/layout/radius changes: outside the graphics-animation refactor and already visually accepted.

# Unverified
- None within the requested refactor scope.

# Next
Hand the verified refactor back to the user; do not alter the accepted geometry or relative rates.
