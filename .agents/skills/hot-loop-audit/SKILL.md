---
name: hot-loop-audit
description: Audit allocation-free Kotlin hot paths. Use for update, render, onDraw, renderAudioBlock, input dispatch, pixel loops, rasterizer loops, and any frame/audio-critical code.
---

# Hot Loop Audit

## Purpose

Keep game-loop, render-loop, rasterizer, and audio paths allocation-free and deterministic.

The goal is not idiomatic Kotlin. The goal is predictable C-style execution in Kotlin Multiplatform.

## Trigger When

Use this skill when code touches:

- `update`
- `render`
- `onDraw`
- `renderAudioBlock`
- rasterization loops
- glyph drawing loops
- pixel filling loops
- audio callbacks
- input dispatch inside frame processing
- scene transition code that might run during gameplay

## Hot Path Law

Inside hot paths:

- no heap allocation
- no lambdas
- no collection operators
- no iterator allocation
- no temporary arrays
- no string building
- no boxing through generic helpers
- no coroutine scheduling
- no platform calls unless already approved as platform wrapper code

## Required Workflow

1. Locate the hot function bodies.
2. Inspect only relevant paths first.
3. Flag allocations and hidden allocations.
4. Replace elegant Kotlin with explicit primitive loops.
5. Prefer `while` loops over `forEach`, `map`, `filter`, or iteration over collections.
6. Prefer primitive arrays and preallocated buffers.
7. Preserve behavior exactly.

## Safe Patterns

Preferred loop style:

```kotlin
var i = 0
while (i < count) {
    // work
    i++
}
```

Preferred storage:

```kotlin
val x = IntArray(capacity)
val y = IntArray(capacity)
val phase = FloatArray(channelCount)
```

Preferred scene reset:

```kotlin
override fun onEnter(payload: ScenePayload) {
    cursor = 0
    selectedIndex = 0
    dirty = true
}
```

## Forbidden Patterns

Reject in hot paths:

```kotlin
items.forEach { ... }
items.map { ... }
items.filter { ... }
for (item in list) { ... }
val temp = FloatArray(n)
val temp = mutableListOf<T>()
val s = "x=$x y=$y"
launch { ... }
```

## Audit Notes

Kotlin `for` over primitive array may be acceptable only if confirmed not to allocate in the target backend, but default to `while` in engine code.

Creating value classes is allowed only when it wraps primitives without heap allocation and does not introduce generics or boxing in hot paths.

## Output Format

```text
HOT LOOP AUDIT:
Function:
Status: PASS / FAIL
Violations:
- line / pattern / reason
Required rewrite:
```
