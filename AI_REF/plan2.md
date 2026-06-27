# PLAN 2: LAYOUT MATH & INTEGER GRID REFACTORING

## Objective
Refactor all layout math and UI positioning to use discrete integer grid variables derived from the canonical unit `U` (where `U = 16`, an `Int` constant) rather than floating-point multipliers, complying with the Canonical Unit and Discrete Integer Grid laws.

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

---

## Technical Refactoring Details

### 1. Core Renderers & Components
- **NestedTimeboxInstrumentRenderer.kt**: Converted all float radii math (`U * 1.5f`, `U * 2.5f`, etc.) to integer products (e.g., `U + U / 2`, `U * 2 + U / 2`) and only cast to `Float` when drawing on the canvas.
- **ScaledProceduralRenderer.kt**: Changed button border size to `U / 8` (2 pixels) micro-detail, and cleaned up text/glyph drawing overloads. Removed duplicate/conflicting float-scale overloads to resolve overload resolution ambiguity on `drawText` and `drawGlyph` calls.
- **RetroHudComponent.kt**: Updated button borders to `U / 8` and cleaned up layout bounds calculations.

### 2. Scene Components (Scenes.kt)
- **ActiveTimerScene**: Resolved mismatched brace bug inside `render()` (which previously closed the object prematurely and broke downstream references). Replaced float multipliers with integer-grid variables.
- **TemplateCustomizerScene**: Updated touch scrolling threshold to `U / 4` and layout spacing.
- **TemplateForgeScene**: Updated step layout stacking logic and margins.
- **SettingsScene**: Refactored row layout spacing to `U / 4` (tight list rows) and bar stepper gap to `U / 8` (micro-detailing internal to the bar component).
- **EntropyScene**: Changed vertical slots spacing to `U / 4` and task delete buttons layout to integer grid math.

---

## Verification & Build
- Checked compilation and assembled debug target successfully:
  ```powershell
  $env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :app:assembleDebug
  ```
