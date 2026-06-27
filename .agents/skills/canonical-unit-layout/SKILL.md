---
name: canonical-unit-layout
description: Enforce the 16x16 ROM glyph cell as the canonical layout unit. Use for IMGUI, buttons, text fields, menus, hitboxes, responsive stacking, DPI scaling, and any UI geometry.
---

# Canonical Unit Layout

## Purpose

Make all UI geometry derive from the ROM glyph cell and display variables.

No arbitrary pixels. No orientation hacks. No layout folklore.

## Canonical Unit

```text
U = glyphWidth = glyphHeight
```

The current engine uses a 16x16 ROM font.

The literal value `16` is an implementation detail. Code should use `U`, `glyphWidth`, `glyphHeight`, or a named engine constant.

## Trigger When

Use this skill when implementing or reviewing:

- buttons
- menus
- text fields
- HUD layout
- sidebars
- touch hitboxes
- vertical stacking
- responsive rows
- glyph rendering
- DPI scaling
- logical dimensions
- IMGUI layout cursor code

## Display Law

`logicalWidth` and `logicalHeight` come from the active display.

Do not assume:

- 640x400
- 1920x1080
- portrait
- landscape
- 16:9
- 4:3

All aspect ratios are valid, including `1:1`.

## Required Layout Rules


All layout values must derive from:

- `U`
- `logicalWidth`
- `logicalHeight`
- measured text size
- named engine constants
- explicit palette / glyph / hardware laws

## Layout Laws (Updates)

### 1. The Canonical Unit Law
- The fundamental layout measurement unit is the ROM glyph cell `U = 16`.
- Macro layout margins, spacing, sizes, and padding must derive strictly from integer multiples or divisions of `U`.

### 2. The Quarter-Block Law
- Authorized the use of `U / 4` (exactly 4 logical pixels) for layout margins, paddings, border insets, and sub-tile spacing.
- Used for dense vertical list row spacing (e.g., Settings, Template Customizer, Entropy Scene).

### 3. The U / 8 Detailing Law
- **Banned for Macro-Layouts**: `U / 8` must not be used for spacing between buttons, list rows, window margins, or layout cursors.
- **Authorized for Micro-Detailing ONLY**: `U / 8` (exactly 2 logical pixels) is allowed ONLY for:
  - *Drop Shadows*: Shadow text/box offsets.
  - *Button Bevels*: Drawing 2-pixel inner/outer borders to simulate 3D panel depth.
  - *UI Cursors*: define blink cursors or scrollbar thumbs.
  - *Component Internals*: Bar stepper slot spacing/gaps.


## Responsive Row Stacking

Use this exact decision shape for label-control rows:

```kotlin
val requiredLabelW = text.length * U
val labelColumnW = usableWidth * LABEL_COLUMN_RATIO
val padding = U / 2

if (requiredLabelW <= labelColumnW) {
    // side-by-side
    // label in label column
    // control in remaining column
    maxRowHeight = max(labelHeight, controlHeight)
} else {
    // vertical stacking
    // label on current row
    // control starts at currentY + labelHeight + padding
    maxRowHeight = labelHeight + padding + controlHeight
}

currentY += maxRowHeight + padding
```

Do not use diagonal stair-step layout.

Do not advance `currentY` separately for label and control without computing `maxRowHeight`.

## Forbidden Patterns

Reject:

```kotlin
padding = 8f
buttonHeight = 48f
margin = 10f
if (logicalHeight > logicalWidth) { ... }
if (width < 480f) { ... }
x = 640f
y = 400f
```

Unless the number is a named engine constant with a documented law.

## Acceptable Constants

Examples:

```kotlin
const val GLYPH_CELL_SIZE = 16
const val PALETTE_SIZE = 16
const val LABEL_COLUMN_RATIO_NUM = 2
const val LABEL_COLUMN_RATIO_DEN = 5
```

Prefer rational constants over unexplained floats when ratios are laws.

## Output Format

```text
LAYOUT LAW CHECK:
U source:
Display variables used:
Unexplained constants:
Stacking behavior:
Result: PASS / FAIL
```
