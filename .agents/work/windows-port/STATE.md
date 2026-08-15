# Objective
Create a Windows dummy-terminal build equivalent to the existing Android wrappers, without turning Win32 into an app framework.

# Constraints
- Platform wrappers are dumb terminals only (display, input, audio, timing, DPI, lifecycle).
- No Compose / SwiftUI / Electron / other UI frameworks.
- No `java.*` in `commonMain`.
- C interop stays in platform source sets.
- Existing Android app must keep working.
- If commonMain must change, stop and report.

# Plan
- [x] Inventory this Windows 11 PC against Kotlin/Native `mingwX64("win")` needs
- [x] Confirm `:shared-engine:compileKotlinWin` actually compiles current sources
- [x] Design Win32 dummy terminal (HWND, framebuffer present, input, waitable timer, waveOut)
- [x] Implement `winMain` wrappers + `main` entry
- [x] Link debug executable and smoke-launch

# Confirmed
- `commonMain` compiled for `mingwX64` with no source changes (`compileKotlinWin` success).
- Linked `shared-engine/build/bin/win/debugExecutable/shared-engine.exe` (linkDebugExecutableWin success).
- Process stayed alive for 3s after Start-Process (pid 30752), then was stopped.
- Alarm path: `CreateWaitableTimerExW` + `CREATE_WAITABLE_TIMER_HIGH_RESOLUTION` + `SetWaitableTimer` + `WM_APP_ALARM`.
- Display: `StretchDIBits` of a 32-bit DIB; scale from `DisplayScalePolicy`.
- Audio: PCM16 mono 48 kHz `waveOut` streaming of `CompiledOpnaPlayer`.
- Power: `SetThreadExecutionState`.
- Time: `GetSystemTimeAsFileTime` and `QueryPerformanceCounter`.

# Rejected
- Changing `commonMain` for this port: not required.
- Compose Desktop / JavaFX / Electron.
- Vendoring miniaudio.

# Unverified
- Human click/type through every scene.
- Alarm fire after a real countdown, including deep sleep/lid-close.
- waveOut on every audio device.
- Android assemble after this change (only `winMain` + linker opts).
- EXE contents (binary law: not inspected).

# Next
Wait for user runtime feedback. Later slices: WASAPI, Task Scheduler for process-dead alarms, persistence, PowerCreateRequest if KN bindings expose the enum.
