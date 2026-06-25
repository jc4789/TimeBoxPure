---
name: platform-firewall-port
description: Keep Android, Win32, iOS, and other platform wrappers as dumb terminals. Use when adding or editing platform source sets, input, display, audio hooks, wake locks, canvas/framebuffer presentation, or OS lifecycle glue.
---

# Platform Firewall Port

## Purpose

Prevent platform wrappers from becoming app frameworks.

The platform is a terminal. The core engine is the universe.

## Trigger When

Use this skill when code touches:

- Android source set
- iOS source set
- mingwX64 / Win32 source set
- platform canvas
- framebuffer present
- audio output
- input events
- wake locks
- app lifecycle
- display metrics
- native callbacks

## Allowed Platform Responsibilities

Platform wrappers may provide:

- display surface
- framebuffer/canvas presentation
- input events
- timing source adapter
- audio output buffer
- wake-lock / power management
- platform DPI / display metadata
- native color byte-order conversion
- OS lifecycle forwarding

## Forbidden Platform Responsibilities

Platform wrappers must not own:

- game logic
- scene state
- navigation policy
- layout decisions
- rendering decisions
- color meaning
- audio synthesis
- palette meaning
- asset loading
- UI framework state

## Required Shape

Platform code should forward facts into core:

```kotlin
engine.onResize(width, height, dpiInfo)
engine.onInput(inputEvent)
engine.update(dt)
engine.render(canvas)
engine.renderAudioBlock(buffer, frames)
```

Platform code should not decide what UI looks like.

## Source Set Rule

- `commonMain`: engine logic and platform-agnostic math
- platform source sets: OS wrappers only
- C interop: platform source sets only

## Output Format

```text
PLATFORM FIREWALL CHECK:
Platform:
Allowed responsibility:
Core responsibility preserved:
Leakage found:
Result: PASS / FAIL
```
