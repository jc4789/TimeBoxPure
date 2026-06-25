---
name: engine-law-check
description: Audit Kotlin Multiplatform engine changes against the Carmack-but-ZUN constitution. Use before writing code, before finalizing diffs, or whenever code touches core rendering, layout, audio, platform wrappers, assets, timing, or build instructions.
---

# Engine Law Check

## Purpose

Prevent Codex from violating the project laws while editing the engine.

This skill is a review gate. It does not invent architecture. It checks whether the proposed work stays inside the existing engine universe.

## Trigger When

Use this skill when the task involves:

- `commonMain`
- renderer code
- IMGUI code
- layout code
- audio / DSP code
- scene transitions
- platform wrappers
- C interop
- build commands
- adding files
- refactoring engine primitives
- reviewing a diff

## Core Laws To Enforce

1. No UI frameworks in core rendering.
2. No `java.*` or hidden JVM assumptions in `commonMain`.
3. No hot-loop allocations.
4. No Kotlin collections or collection operators in hot paths.
5. No coroutines in core engine logic.
6. No external assets.
7. No unexplained constants.
8. Final rasterization must be integer-snapped.
9. Core color means palette index `0..15`.
10. Platform wrappers are dumb terminals.
11. `U = glyphWidth = glyphHeight`; layout derives from `U`, not literal `16`.

## Required Workflow

Before implementation:

1. Quote the single relevant project law.
2. Identify target source set:
   - `commonMain`
   - Android platform
   - iOS platform
   - mingwX64 / Win32 platform
   - tooling / scripts
3. State whether the change touches hot paths.
4. State whether the change touches platform interop.
5. State whether the change introduces data, files, or resources.

After implementation:

1. Review the diff against the Core Laws.
2. Search for forbidden symbols.
3. Confirm no new files violate Asset Law.
4. Confirm any new constants are named and justified.
5. Confirm no platform API leaked into `commonMain`.

## Forbidden Patterns

Reject or rewrite code containing these in `commonMain`:

```text
java.
System.currentTimeMillis
System.nanoTime
Runtime
Thread
android.
androidx.compose
@Composable
SwiftUI
HTML
DOM
XML UI
```

Reject or rewrite hot-path code containing:

```text
listOf
mutableListOf
mapOf
List<
MutableList
Map<
Set<
Sequence
.forEach
.map
.filter
.flatMap
.sorted
.groupBy
```

Reject or rewrite asset access containing:

```text
.png
.jpg
.jpeg
.svg
.ttf
.wav
.ogg
.mp3
.json
.xml
loadTexture
loadImage
loadFont
loadSound
readText
readBytes
```

## Output Format

When reporting a violation, use this format:

```text
LAW VIOLATION:
File:
Line:
Rule:
Why this breaks the engine:
Replacement strategy:
```

## Bias

Prefer small primitive changes.

Do not introduce frameworks, dependencies, service locators, ECS frameworks, dependency injection, serialization frameworks, or asset pipelines.

When unsure, ask which existing engine primitive should be used.
