# Objective
Fix Windows dummy-terminal runtime bugs without changing commonMain scale policy.

# Constraints
- Platform wrappers stay dumb terminals.
- Window client size is the display geometry. No fixed resolution.
- Do not change `DisplayScalePolicy` / commonMain until user reviews the overview.
- No tests. Japanese user-facing report.

# Plan
- [x] Diagnose scanlines, text, scale, scroll, settings
- [x] Remove opaque Win32 scanline overlay; present 1:1 from physical DIB
- [x] Physical framebuffer + integer presentation scale (match Android canvas.scale)
- [x] WM_MOUSEWHEEL as synthetic play-area drag
- [x] Persist settings to %APPDATA%\TimeBox
- [x] Report commonMain scale overview; do not edit it

# Confirmed
- `compileKotlinWin` + `linkDebugExecutableWin` succeeded after these winMain edits.
- Geometry source remains `GetClientRect` (window client), not a fixed desktop mode.

# Rejected
- Changing `DisplayScalePolicy` this turn.
- Inventing a fixed desktop resolution.

# Unverified
- Human click/wheel/alarm/settings-reload after rebuild.

# Next
User review of commonMain scale overview. Runtime check of EXE.
