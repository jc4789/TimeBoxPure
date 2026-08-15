# Objective
Replace choppy Win32 waveOut playback with miniaudio WASAPI output. Keep synthesis in commonMain.

# Constraints
- Platform wrapper is a dummy audio terminal only.
- Do not change commonMain audio/MML/OPNA.
- No C interop in commonMain.
- ma_device / userdata follow c-interop ownership laws.
- No tests.

# Plan
- [x] Thin C wrapper around provided miniaudio.h (WASAPI only)
- [x] Gradle compile + cinterop + link
- [x] Rewrite Win32Audio to callback render
- [x] Link debug executable

# Confirmed
- `compileMiniaudioWin` + `compileKotlinWin` + `linkDebugExecutableWin` succeeded.
- waveOut 2x1024 + timeout skip was the previous underrun path.

# Rejected
- cinterop of the full miniaudio.h
- Changing OPNA/MML

# Unverified
- Human listen of preview/alarm after rebuild

# Next
User listen check of preview and alarm.
