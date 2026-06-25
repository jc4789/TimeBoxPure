---
name: dpi-scale-derive
description: Validate DPI and derive hardware-agnostic scale from display geometry and named engine bounds. Use when implementing platform display adapters, affine scaling, logicalWidth/logicalHeight, or fallback DPI behavior.
---

# DPI Scale Derive

## Purpose

Prevent Codex from trusting garbage DPI metadata or inventing random scale breakpoints.

Platform DPI is advisory. The engine scale must be derived and validated.

## Trigger When

Use this skill when code touches:

- display metrics
- DPI
- density
- scale factor
- affine matrix
- logical width / height
- viewport fitting
- platform wrappers that report display geometry

## Required Constants

Use named constants, not raw numbers:

```kotlin
const val MIN_SAFE_LOGICAL_WIDTH = 320
const val MAX_SAFE_LOGICAL_WIDTH = 1200
const val MIN_SCALE = 1
```

If these values change, update the named constants and their justification. Do not scatter raw `320`, `1200`, or `1`.

## DPI Sanity Law

Reject DPI values that are clearly invalid:

- `0`
- `1`
- near-zero
- negative
- NaN
- infinite
- absurdly high
- values known to be fake platform metadata

## Fallback Scaling Rule

When DPI is invalid or untrustworthy:

1. Derive initial scale from display geometry and `U`.
2. Calculate `logicalWidth`.
3. If `logicalWidth < MIN_SAFE_LOGICAL_WIDTH`, decrement scale factor, never below `MIN_SCALE`.
4. If `logicalWidth > MAX_SAFE_LOGICAL_WIDTH`, increment scale factor.
5. Recompute `logicalWidth` and `logicalHeight`.
6. Build the affine transform from final scale and display dimensions.
7. Do not branch on device name, OS version, portrait, or landscape.

## Forbidden Patterns

Reject:

```kotlin
if (logicalWidth < 320) ...
if (logicalWidth > 1200) ...
if (height > width) ...
scale = 2.75f
dpi = platformDpi // blindly trusted
```

## Output Format

```text
DPI SCALE CHECK:
Platform DPI:
DPI accepted/rejected:
Fallback used:
Scale:
logicalWidth:
logicalHeight:
Constants used:
Result: PASS / FAIL
```
