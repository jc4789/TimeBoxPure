# UI Scale Overhaul — Implementation Guide and Work Ledger

Status: **design locked; production implementation not started**

This document is the authoritative implementation specification for the UI scale overhaul. If an implementation choice conflicts with this document, stop and update this document with the project owner before changing the design. Do not silently reinterpret the rules.

This specification translates the project owner's latest decisions and `D:\Programes\TB screenshots\Referance\How to scale.txt` into repository-specific work. The text file supplies the integer-grid method; this document is authoritative where it makes that method project-specific, including one global `S`, a top-left grid, `24×22` minimum capacity, and fullwidth-only text. Current production code is evidence of what must change, not authority to preserve conflicting behavior.

## 1. Objective

Replace the current density/width-derived scaling and the recent text-only raster cap with one display-derived, integer, fullwidth-cell grid shared by Android, Windows, text, UI geometry, hitboxes, and input conversion.

The application must use the active display or Win32 client area's real physical pixel dimensions. It must not create or target an invented design resolution such as 360×800, 640×400, 1920×1080, or any other fixed framebuffer size.

The implementation is complete only when:

- the 1080×2400 Google Pixel 10 Android phone reference device selects `S = 2`, exposes 33 complete columns and 75 complete rows, renders both a normal glyph and one `U` UI cell as 32×32 physical pixels, and places the HUD at the bottom because its physical aspect ratio is portrait;
- the current Windows reference window selects `S = 2`, exposes the complete cells available in its current client area, and places the HUD at the left because its physical client-area aspect ratio is landscape;
- all four primary scenes use the same cell/grid result without platform-specific layout branches;
- text, geometry, hitboxes, clipping, scrolling, and input inversion all use the same `S`;
- fullwidth-only text behavior remains unchanged;
- no antialiasing, bilinear filtering, fractional presentation scale, or text-only presentation scale remains.

## 2. Non-negotiable laws

### 2.1 One canonical unit

```text
U = 16 source pixels
U = glyph width = glyph height = sole UI layout unit
```

There is no separate `G`, font unit, density unit, dp unit, or platform UI unit.

A normal fullwidth glyph occupies exactly `U × U` source-grid pixels. At presentation scale `S`, it occupies exactly `(U × S) × (U × S)` physical pixels. A UI cell occupies the same physical area.

Replace the ambiguous text `scale` names with these semantic cell-span names:

```kotlin
const val TEXT_CELL_SPAN_NORMAL = 1
const val TEXT_CELL_SPAN_HEADER = 2
```

Header span `2` means a header glyph occupies `2U × 2U` in the same source grid before the same global `S` is applied. It is not an independent display/font scale. Rename text renderer parameters from `scale` to `cellSpan`; unrelated procedural-art scale parameters are outside this rename.

### 2.2 Fullwidth-only text

- One Kotlin `Char` in UI text consumes one full `U` cell at normal cell span.
- Preserve `toFullwidthDisplayChar` and the intentional ASCII-to-fullwidth conversion.
- Preserve fullwidth spaces and punctuation.
- Do not add 8×16 glyphs, halfwidth advances, proportional metrics, Unicode East Asian Width logic, or mixed-width wrapping.
- `measureTextCells(text)` remains `text.length`.

### 2.3 Physical display authority

The platform supplies only real facts:

```text
physicalWidthPx
physicalHeightPx
DPI/density metadata, retained only by existing non-layout platform effects
```

Physical width and height select the grid. DPI/density must not select `S`, change cell dimensions, or change layout. Preserve Android density only as an input to the existing physical scanline overlay; it is not a layout input.

### 2.4 One integer presentation scale

`S` is a positive integer. The only legal presentation scales are `1, 2, 3, ...`.

Every rendering path uses the same `S`. The following are forbidden:

- fractional `S`;
- scaling text with a capped value while geometry uses `S`;
- scaling only selected primitives;
- platform-specific scale overrides;
- per-scene `S`;
- changing `S` when the active scene or language changes.

### 2.5 HUD position is an aspect-ratio law

HUD position has no relationship to Android, Windows, or any other platform. Shared commonMain code chooses it from the active physical display/client-area aspect ratio only:

```text
physicalWidthPx > physicalHeightPx  -> landscape -> HUD at left
physicalWidthPx <= physicalHeightPx -> portrait or 1:1 tie -> HUD at bottom
```

The 1:1 rule is the deterministic tie behavior. Do not inspect OS, device model, DPI, named resolution, or scene. Recompute this shared HUD mode from the newly published `DisplayGrid` after a surface/client resize. The platform adapter supplies physical dimensions but does not select the HUD mode.

### 2.6 Macro layout grid

UI margins, padding, panel geometry, button geometry, list spacing, layout cursors, and hitboxes must be multiples of:

```text
U
U / 2
U / 4
```

`U / 8` remains legal only for existing micro-detail roles: bevel/border depth, one-pixel-source shadows, cursors, and internal bar/stepper detailing. It is forbidden for macro spacing, margins, row heights, control heights, drag thresholds, or layout cursor movement.

Procedural artwork is not UI macro geometry. The timer circle, magic circle, nebula, particles, animation phases, waveform math, and similar visual content continue to use continuous display-derived math. Their surrounding controls, labels, panels, and hitboxes must obey the cell grid.

## 3. Locked display-grid contract

### 3.1 Named constants

Add these commonMain constants next to the display-grid policy:

```kotlin
const val CANONICAL_UI_UNIT = 16

object DisplayGridPolicy {
    const val MIN_LAYOUT_COLUMNS = 24
    const val MIN_LAYOUT_ROWS = 22
    const val MIN_PRESENTATION_SCALE = 1
}
```

These values are content-capacity laws, not a target resolution:

- `24` columns is the smallest multiple-of-four column contract above the rejected 22-column result on the Google Pixel 10 Android phone at `S = 3`. It forces that reference device's 1080-physical-pixel display width to use `S = 2`, removing the rejected 48×48 base glyph without reintroducing a text-only scale.
- `22` rows is the current template screen's structural requirement: `2U` safe top + `2U` header + `U/2` header gap + four `15U/4` minimum cards + three `U/2` card gaps + `U` bottom breathing room = `22U`.

Do not change either constant merely to make one screenshot look better. A change requires a documented UI capacity change in this file.

### 3.2 Grid derivation

Introduce one immutable commonMain value object. Creation occurs on resize/surface change, never per frame.

```kotlin
data class DisplayGrid(
    val physicalWidthPx: Int,
    val physicalHeightPx: Int,
    val presentationScale: Int,
    val columns: Int,
    val rows: Int,
    val layoutWidth: Int,
    val layoutHeight: Int,
    val usedPhysicalWidthPx: Int,
    val usedPhysicalHeightPx: Int
) {
    val isRenderable: Boolean
        get() = columns > 0 && rows > 0

    fun containsPhysical(x: Int, y: Int): Boolean {
        return x >= 0 && y >= 0 &&
            x < usedPhysicalWidthPx && y < usedPhysicalHeightPx
    }
}

val EMPTY_DISPLAY_GRID = DisplayGrid(
    physicalWidthPx = 0,
    physicalHeightPx = 0,
    presentationScale = DisplayGridPolicy.MIN_PRESENTATION_SCALE,
    columns = 0,
    rows = 0,
    layoutWidth = 0,
    layoutHeight = 0,
    usedPhysicalWidthPx = 0,
    usedPhysicalHeightPx = 0
)
```

Place `derive` inside `DisplayGridPolicy`. The policy must use integer division only:

```kotlin
fun derive(physicalWidthPx: Int, physicalHeightPx: Int): DisplayGrid {
    if (physicalWidthPx <= 0 || physicalHeightPx <= 0) {
        return EMPTY_DISPLAY_GRID
    }

    val widthScale = physicalWidthPx / (MIN_LAYOUT_COLUMNS * CANONICAL_UI_UNIT)
    val heightScale = physicalHeightPx / (MIN_LAYOUT_ROWS * CANONICAL_UI_UNIT)
    val scale = maxOf(
        MIN_PRESENTATION_SCALE,
        minOf(widthScale, heightScale)
    )

    val columns = physicalWidthPx / (CANONICAL_UI_UNIT * scale)
    val rows = physicalHeightPx / (CANONICAL_UI_UNIT * scale)
    val layoutWidth = columns * CANONICAL_UI_UNIT
    val layoutHeight = rows * CANONICAL_UI_UNIT

    return DisplayGrid(
        physicalWidthPx = physicalWidthPx,
        physicalHeightPx = physicalHeightPx,
        presentationScale = scale,
        columns = columns,
        rows = rows,
        layoutWidth = layoutWidth,
        layoutHeight = layoutHeight,
        usedPhysicalWidthPx = layoutWidth * scale,
        usedPhysicalHeightPx = layoutHeight * scale
    )
}
```

If a nonzero surface is smaller than one `U` at `S = 1`, a derived column or row count of zero is valid. Clear the physical target and skip scene rendering/input until both counts are at least one.

### 3.3 Remainder pixels

The grid is top-left anchored at physical `(0, 0)`. Do not center it and do not add a letterbox offset.

```text
remainderWidthPx  = physicalWidthPx  - usedPhysicalWidthPx
remainderHeightPx = physicalHeightPx - usedPhysicalHeightPx
```

Clear the entire physical target to `PaletteIndices.BLACK` every frame before scene drawing. `SceneManager` owns this palette meaning; platform code must not choose a native black value. Render scene backgrounds and content only inside `usedPhysicalWidthPx × usedPhysicalHeightPx`. Right/bottom remainder pixels stay palette black. Input in a remainder strip is ignored.

### 3.4 Locked reference examples

Google Pixel 10 Android phone reference device:

```text
physical = 1080 × 2400
S = min(floor(1080 / (24×16)), floor(2400 / (22×16)))
S = min(2, 6) = 2
columns = floor(1080 / 32) = 33
rows = floor(2400 / 32) = 75
layout = 528 × 1200 source-grid pixels
used physical = 1056 × 2400
remainder = 24 × 0 physical pixels
normal glyph = UI cell = 32 × 32 physical pixels
```

Windows worked example using a 1900×983 client area:

```text
S = min(floor(1900 / (24×16)), floor(983 / (22×16)))
S = min(4, 2) = 2
columns = floor(1900 / 32) = 59
rows = floor(983 / 32) = 30
layout = 944 × 480 source-grid pixels
used physical = 1888 × 960
remainder = 12 × 23 physical pixels
normal glyph = UI cell = 32 × 32 physical pixels
```

The runtime must use `GetClientRect`; the screenshot's outer-window dimensions are not an input.

## 4. Required API and ownership changes

### 4.1 CommonMain owns grid selection

Replace `DisplayScalePolicy` with `DisplayGridPolicy`. `derive` accepts only integer physical width and height. Remove these policy concepts entirely:

- `MIN_SAFE_LOGICAL_WIDTH`;
- `MAX_SAFE_LOGICAL_WIDTH`;
- trusted/fake density ranges;
- `PHYSICAL_SCALE_PER_DENSITY`;
- `FALLBACK_MIN_SPAN_CELLS`;
- density-based initial scale;
- width-only correction loops.

DPI remains a platform fact but has no authority over UI scale.

### 4.2 Remove ambiguous continuous bounds

Do not calculate layout authority as:

```kotlin
val logicalWidth = physicalWidth / scale
val logicalHeight = physicalHeight / scale
```

Those expressions admit partial cells. Use `DisplayGrid.layoutWidth` and `DisplayGrid.layoutHeight`, which are exact multiples of `U`.

Rename shared state to prevent the old interpretation from returning:

- `SceneManager.logicalWidth` -> `SceneManager.layoutWidth`;
- `SceneManager.logicalHeight` -> `SceneManager.layoutHeight`;
- `SceneManager.setLogicalBounds(width, height)` -> `SceneManager.setDisplayGrid(grid)`;
- platform `dynamicLogicalWidth/Height` -> one immutable `displayGrid` snapshot;
- `EngineCanvas.width/height` -> `EngineCanvas.layoutWidth/layoutHeight`.

The scene render signature continues receiving integer play-area source-grid coordinates. Do not pass physical pixels into scenes.

The shared render entry has this exact ordering:

```kotlin
fun render(renderer: ScaledProceduralRenderer, grid: DisplayGrid) {
    setDisplayGrid(grid)
    renderer.clear(PaletteIndices.BLACK)
    if (!grid.isRenderable) return
    renderer.drawRect(
        0f,
        0f,
        grid.layoutWidth.toFloat(),
        grid.layoutHeight.toFloat(),
        PaletteIndices.BG
    )
    activeScene?.render(
        renderer,
        playAreaX,
        playAreaY,
        playAreaWidth,
        playAreaHeight
    )
}
```

Preserve every existing scene call to `EngineThemes.getColors` and every existing scene-owned bounded background draw. Do not move palette selection to platform code. The first palette-black clear exists specifically to clear the complete physical target and remainder strips; it does not replace the themed scene background inside the grid.

### 4.3 EngineCanvas boundary

Remove `drawPhysicalRect` from `EngineCanvas` and both platform implementations. CommonMain must not emit final physical coordinates.

All core draw methods accept palette indices and source-grid coordinates. Platform implementations alone apply `presentationScale` mechanically:

- Android: one integer `Canvas.scale(S, S)` around grid rendering;
- Win32: multiply source-grid primitive coordinates and sizes by `S` when writing the physical framebuffer.

These mechanisms are equivalent. No common renderer path may compensate for or bypass them.

### 4.4 Atomic resize state

Android must publish one immutable `DisplayGrid` through a volatile field. The render frame and touch event each read one local snapshot. Do not keep scale, width, and height in separate volatile fields that can describe different resize events.

Win32 keeps one `DisplayGrid` on `Win32Host`, replaced by `applyClientSize`. The message loop, framebuffer resize, render, mouse conversion, and wheel conversion use that same value.

## 5. Current bad code and mandatory replacements

The examples below quote current production patterns. The replacement shapes are requirements, not suggestions.

### 5.1 Bad: density plus width-only scale selection

Current `EngineCanvas.kt`:

```kotlin
var scale = if (isTrustedDensity(platformDensity)) {
    (platformDensity * PHYSICAL_SCALE_PER_DENSITY).toInt().coerceAtLeast(MIN_SCALE)
} else {
    val shortSpan = minOf(physicalWidth, physicalHeight)
    val fallbackLogicalSpan = (CANONICAL_UI_UNIT * FALLBACK_MIN_SPAN_CELLS).toFloat()
    (shortSpan / fallbackLogicalSpan).toInt().coerceAtLeast(MIN_SCALE)
}

while (scale > MIN_SCALE && physicalWidth / scale < MIN_SAFE_LOGICAL_WIDTH) {
    scale--
}
while (physicalWidth / scale > MAX_SAFE_LOGICAL_WIDTH) {
    scale++
}
```

Why it is wrong:

- density can select UI scale;
- only width is bounded after initial selection;
- landscape Windows can retain `S = 3` with only about 20 complete rows;
- the result is not expressed as complete fullwidth cells.

Mandatory replacement: `DisplayGridPolicy.derive(Int, Int)` from section 3.2.

### 5.2 Bad: text-only raster scale

Current `EngineCanvas.kt`:

```kotlin
internal object TextRasterScale {
    const val MAX_BASE_PHYSICAL_PIXEL_SCALE = 2

    fun configure(displayScale: Int) {
        presentationScale = displayScale.coerceAtLeast(DisplayScalePolicy.MIN_SCALE)
        basePhysicalPixelScale = minOf(presentationScale, MAX_BASE_PHYSICAL_PIXEL_SCALE)
    }

    fun logicalCellSize(semanticScale: Int): Float {
        return CANONICAL_UI_UNIT * logicalPixelSize(semanticScale)
    }
}
```

Why it is wrong: at `S = 3`, UI `U` becomes 48 physical pixels while a normal glyph becomes 32 physical pixels. This directly violates `U = glyphWidth = glyphHeight`.

Mandatory replacement:

- delete `TextRasterScale` completely;
- delete its configuration call;
- make normal text metrics depend only on `U`;
- let the single platform presentation transform apply `S` to text exactly as it applies `S` to UI geometry.
- rename `TEXT_SCALE_IDENTITY/HEADER` and text-function `scale` parameters to the `TEXT_CELL_SPAN_*`/`cellSpan` names from section 2.1.

```kotlin
fun measureTextWidth(text: String, cellSpan: Int = TEXT_CELL_SPAN_NORMAL): Float {
    return text.length * CANONICAL_UI_UNIT * cellSpan.toFloat()
}

fun measureTextHeight(cellSpan: Int = TEXT_CELL_SPAN_NORMAL): Float {
    return CANONICAL_UI_UNIT * cellSpan.toFloat()
}
```

The corresponding draw/wrap rules are exact:

```kotlin
val charWidth = CANONICAL_UI_UNIT * cellSpan.toFloat()
val charHeight = CANONICAL_UI_UNIT * cellSpan.toFloat()
val sourcePixelSize = cellSpan.toFloat()
val shadowOffset = sourcePixelSize
val spacing = charSpacing * cellSpan
val cellsPerLine = maxOf(1, (maxWidth / charWidth).toInt())
```

`drawTextRasterRect(x, y, sourceWidth, sourceHeight, ..., cellSpan)` becomes one ordinary source-grid rectangle at `(x, y)` with width `sourceWidth * cellSpan` and height `sourceHeight * cellSpan`. It must not read `presentationScale`.

### 5.3 Bad: physical glyph bypass

Current `ScaledProceduralRenderer.drawGlyphRaw` converts logical origins to physical coordinates, chooses a separately capped physical pixel size, and calls `drawPhysicalRect`.

Mandatory replacement: rasterize the 16×16 bitmap in source-grid coordinates through ordinary `drawRect`:

```kotlin
private fun drawGlyphRaw(
    glyph: IntArray,
    destX: Float,
    destY: Float,
    colorIndex: Int,
    cellSpan: Int,
    startX: Float,
    startY: Float,
    clipWidth: Int,
    clipHeight: Int
) {
    val sourcePixelSize = cellSpan.coerceAtLeast(TEXT_CELL_SPAN_NORMAL).toFloat()
    val clipLeft = startX
    val clipTop = startY
    val clipRight = startX + clipWidth
    val clipBottom = startY + clipHeight
    var y = 0
    while (y < CANONICAL_UI_UNIT) {
        val rowBits = glyph[y]
        var x = 0
        while (x < CANONICAL_UI_UNIT) {
            if ((rowBits and (0x8000 ushr x)) != 0) {
                val drawX = destX + x * sourcePixelSize
                val drawY = destY + y * sourcePixelSize
                val clippedLeft = maxOf(drawX, clipLeft)
                val clippedTop = maxOf(drawY, clipTop)
                val clippedRight = minOf(drawX + sourcePixelSize, clipRight)
                val clippedBottom = minOf(drawY + sourcePixelSize, clipBottom)
                if (clippedLeft < clippedRight && clippedTop < clippedBottom) {
                    canvas.drawRect(
                        clippedLeft,
                        clippedTop,
                        clippedRight - clippedLeft,
                        clippedBottom - clippedTop,
                        colorIndex
                    )
                }
            }
            x++
        }
        y++
    }
}
```

The source-grid intersection above is the required clipping behavior. Do not convert its coordinates to physical pixels in commonMain.

Use this same source-grid bitmap emission for ordinary, wrapped, polar, and ornamental glyph paths. Do not retain two implementations with different presentation behavior.

### 5.4 Bad: Android continuous `physical / scale` layout

Current `Pc98SurfaceView.surfaceChanged`:

```kotlin
val scale = DisplayScalePolicy.deriveScale(physicalWidth, physicalHeight, platformDensity)
val logW = physicalWidth / scale
val logH = physicalHeight / scale

currentScaleFactor = scale
dynamicLogicalWidth = logW
dynamicLogicalHeight = logH
```

Mandatory replacement:

```kotlin
val nextGrid = DisplayGridPolicy.derive(width, height)
displayGrid = nextGrid
```

The renderer receives `nextGrid.layoutWidth` and `nextGrid.layoutHeight`. Density is not passed to the grid policy.

At frame start:

1. Read one `grid` snapshot.
2. Save the Canvas.
3. Scale by the same integer `grid.presentationScale` on both axes.
4. Call the shared render path; its first operation clears the entire physical target to `PaletteIndices.BLACK` through `renderer.clear`, then it draws only within `grid.layoutWidth × grid.layoutHeight`.
5. If `!grid.isRenderable`, perform only the shared palette-black clear.
6. Restore the Canvas.
7. Draw the existing physical scanline overlay afterward.

Do not add a platform clip or native-color clear. `AndroidEngineCanvas.clear` uses `Canvas.drawColor`, which is unaffected by the scale matrix when no clip has been installed. Shared scene/layout code is responsible for keeping all non-clear draw bounds inside the derived layout extent.

### 5.5 Bad: Win32 continuous `physical / scale` layout

Current `Win32Host.applyClientSize`:

```kotlin
val density = platformDensityFromDpi(dpi)
presentationDensity = if (density.isFinite() && density > 0.1f && density < 10f) density else 1f
val scale = DisplayScalePolicy.deriveScale(width.toFloat(), height.toFloat(), presentationDensity)
val logW = width.toFloat() / scale
val logH = height.toFloat() / scale
```

Mandatory replacement:

```kotlin
val nextGrid = DisplayGridPolicy.derive(width, height)
displayGrid = nextGrid
canvas.resizeFramebuffer(nextGrid)
```

Delete `platformDensityFromDpi`, `presentationDensity`, and the `dpi` parameter from `applyClientSize`; they have no remaining layout role. Keep Windows per-monitor DPI awareness and the existing `WM_DPICHANGED` branch, but make that branch re-read `GetClientRect` through the same no-DPI `applyResizeFromHwnd` path. Remove `windowDpi` and its constants if this leaves them without a non-layout caller. `Win32EngineCanvas` keeps a physical framebuffer exactly equal to the client width/height and applies `nextGrid.presentationScale` to every source-grid primitive.

### 5.6 Bad: percentage-sized HUD

Current `RetroHudComponent`:

```kotlin
private const val HUD_RATIO_NUM = 3f
private const val HUD_RATIO_DEN = 10f
private const val PLAY_AREA_RATIO_NUM = 17f
private const val PLAY_AREA_RATIO_DEN = 20f

private fun leftHudWidth(logicalWidth: Float): Float {
    return logicalWidth * HUD_RATIO_NUM / HUD_RATIO_DEN
}

private fun bottomPlayAreaHeight(logicalHeight: Float): Float {
    return logicalHeight * PLAY_AREA_RATIO_NUM / PLAY_AREA_RATIO_DEN
}
```

Why it is wrong: the same button/icon row grows with the screen rather than occupying a stable number of canonical cells.

Mandatory replacement constants:

```kotlin
private const val LEFT_HUD_WIDTH = U * 12
private const val LEFT_BUTTON_WIDTH = U * 10
private const val HUD_BUTTON_HEIGHT = U * 3
private const val BOTTOM_HUD_HEIGHT = U * 4
private const val HUD_GAP = U / 2
private const val HUD_SIDE_PADDING = U / 2
private const val HUD_TOP_PADDING = U
```

- Left buttons are `10U × 3U`, centered inside the `12U` HUD.
- Four left buttons use `U/2` gaps. Let `stackHeight = 4 * HUD_BUTTON_HEIGHT + 3 * HUD_GAP` and `stackTop = maxOf(U, snapDownToQuarter((layoutHeight - stackHeight) / 2f))`. Do not use button-stack fit to change HUD mode; HUD side is determined only by section 2.5.
- Bottom buttons are `3U` high inside a `4U` HUD.
- Divide the available bottom width after side padding and gaps into four equal widths, snapping each width down to `U/4`. Leave any division remainder at the right as background.
- Keep `BUTTON_BORDER = U/8`; this is an authorized bevel/detail use.
- Delete the current candidate-validity and candidate-score comparison in `layoutMode`. Use this exact shared aspect-ratio rule instead:

```kotlin
private fun layoutMode(grid: DisplayGrid): Int {
    return if (grid.physicalWidthPx > grid.physicalHeightPx) {
        LAYOUT_LEFT
    } else {
        LAYOUT_BOTTOM
    }
}
```

- Pass the immutable `DisplayGrid` snapshot into the shared HUD layout calculation. Do not derive HUD mode from platform identity or duplicate the comparison in Android/Win32 code.

Expected choices for the locked reference grids:

- 1080×2400 portrait Google Pixel 10 Android phone reference device: bottom HUD;
- 1900×983 landscape Win32 client-area example: left HUD.

These are aspect-ratio examples, not platform rules. A landscape Android display uses the left HUD. A portrait Windows client area uses the bottom HUD.

### 5.7 Bad: percentage-inflated template rows

Current `TemplateCustomizerScene`:

```kotlin
val baseCardH = maxOf(playAreaH * 3f / 20f, ((U * 4) - (U / 4)).toFloat())
val cardSpacing = maxOf(playAreaH * 3f / 100f, ((U / 4) + (U / 8)).toFloat())
val safeTop = maxOf(logicalHeight * 0.08f, ((U * 2) - (U / 8)).toFloat())
val forgeBtnW = maxOf(((U * 6) - (U / 4)).toFloat(), playAreaW * 0.24f)
val forgeBtnH = (U + (U / 2) + (U / 8)).toFloat()
```

Mandatory replacement:

```kotlin
val baseCardH = (U * 4 - U / 4).toFloat() // 15U/4
val cardSpacing = (U / 2).toFloat()
val safeTop = (U * 2).toFloat()
val forgeBtnW = (U * 6).toFloat()
val forgeBtnH = (U * 2).toFloat()
```

`templateCardHeight` grows above `baseCardH` only when measured fullwidth text/timeline content requires it. It must not grow from a percentage of available height. Apply the same constants in render, touch hit-testing, and scroll-range calculation; do not maintain duplicated formulas with different values.

Use these exact card constants and formulas in render, touch hit-testing, and `templateCardHeight`:

```kotlin
val cardTextPadding = (U * 3 / 4).toFloat()
val actionButtonW = (U * 4 - U / 4).toFloat() // 15U/4
val actionButtonH = (U * 2).toFloat()
val actionGap = (U / 2).toFloat()
val actionRight = cardX + cardW - cardTextPadding
val delX = actionRight - actionButtonW
val editX = delX - actionGap - actionButtonW
val textLeftX = cardX + cardTextPadding
val textRightLimit = if (hasDelete) editX - actionGap else actionRight
```

For the header divider, use `3U/4` as both the left and right inset. Keep only its vertical `headerCoverH - U/8` offset as the authorized one-pixel-source line detail. No other template macro layout expression uses `U/8`.

### 5.8 Bad: percentage-inflated Settings rows

Current `SettingsScene.beginSettingsLayout`:

```kotlin
val padding = maxOf(U.toFloat(), playAreaW / (U + (U / 4)))
safeTop = maxOf(logicalHeight / (U - (U / 4)), (U * 2).toFloat())
rowH = maxOf(playAreaH * 3f / 25f, (U * 2).toFloat())
```

Mandatory replacement:

```kotlin
val padding = U.toFloat()
safeTop = (U * 2).toFloat()
rowH = (U * 2).toFloat()
spacing = (U / 4).toFloat()
```

Keep the existing responsive label/control stacking algorithm. Snap the named `2/5` label-column result down to `U/4` before using it. `maxRowHeight` remains the maximum of measured label height and control height, and the cursor advances once by `maxRowHeight + spacing`.

### 5.9 Bad: display-height-derived Entropy controls

Current examples include:

```kotlin
maxOf((U * 2).toFloat(), playAreaH / INPUT_HEIGHT_DEN)
maxOf((U * 2).toFloat(), playAreaH / DETONATOR_HEIGHT_DEN)
maxOf((U + U / 2).toFloat(), playAreaH / (U + (U / 8)))
```

Mandatory behavior:

- input height = `max(2U, measured wrapped input height + U)`;
- detonator height = `max(2U, measured wrapped button height + U)` through the existing button measurement helper;
- pager/switcher button = `2U × 2U`;
- task row height = `max(3U/2, measured task text height + U/2)`;
- task spacing remains `U/4`;
- outer horizontal padding = `U` and safe top = `2U`;
- derive pagination from the remaining source-grid height after these fixed/content-derived rows.

Use the named `4/5` width ratio for the directive popup and snap the result down to `U/4`. Use the named `3/4` popup-width ratio for its action button and snap that result down to `U/4`. Popup height is measured content plus canonical padding, capped to the available height; remove the `55%` minimum-height rule. Center popup coordinates and snap them down to `U/4`.

### 5.10 Active Timer limits

Replace macro UI values:

```kotlin
CONTROL_BUTTON_HEIGHT = U * 5 / 2
CONTROL_GAP = U / 2
```

Do not change timer-circle, yin-yang, magic-circle, nebula, star-link, or animation mathematics merely because they contain ratios or floating-point values. Those are procedural visual geometry, not UI layout. Their available bounds change automatically through the new grid.

## 6. Shared layout helpers

Add primitive, allocation-free commonMain helpers and use them consistently:

```kotlin
const val UI_QUARTER = CANONICAL_UI_UNIT / 4

fun snapDownToQuarter(value: Float): Float {
    return (value / UI_QUARTER).toInt() * UI_QUARTER.toFloat()
}

fun snapUpToQuarter(value: Float): Float {
    val units = (value / UI_QUARTER).toInt()
    val snapped = units * UI_QUARTER.toFloat()
    return if (snapped < value) snapped + UI_QUARTER else snapped
}
```

Rules:

- use `snapDownToQuarter` for widths/positions that must remain inside available bounds;
- use `snapUpToQuarter` for measured content containers that must not clip content;
- do not allocate wrapper objects in render/layout loops;
- do not use rounding to an arbitrary pixel;
- do not apply these helpers to procedural art coordinates.

## 7. Platform implementation details

### 7.1 Android

Target files: `Pc98SurfaceView.kt` and `AndroidEngineCanvas.kt`.

- `surfaceChanged` derives and publishes one `DisplayGrid`.
- Recreate/rebind the canvas/renderer only as currently required by the surface lifecycle; do not derive the grid per frame.
- The physical Canvas remains the real surface resolution.
- The shared render entry calls `renderer.clear(PaletteIndices.BLACK)` before any bounded scene draw; `Canvas.drawColor` clears the complete physical target despite the scale matrix.
- Use `canvas.scale(S.toFloat(), S.toFloat())` exactly once for scene rendering.
- Do not install a platform clip and do not clear with `android.graphics.Color`; commonMain owns the palette-black remainder rule.
- Keep `Paint.isAntiAlias = false` and `Paint.isFilterBitmap = false`.
- Remove the inverse-scale implementation of `drawPhysicalRect` together with that API.
- Input reads one grid snapshot, rejects remainder-strip coordinates, then computes `layoutX = rawX / S` and `layoutY = rawY / S`.
- Do not use Android dp, Compose, View layout metrics, or density for engine geometry.

### 7.2 Win32

Target files: `Win32Host.kt` and `Win32EngineCanvas.kt`.

- `GetClientRect` width/height remain the physical authority.
- `applyClientSize` derives one `DisplayGrid` and resizes the physical framebuffer to the exact client dimensions.
- Source-grid primitives are integer-snapped and multiplied by `S` inside `Win32EngineCanvas`.
- The shared palette-black clear fills the entire physical framebuffer each frame so right/bottom remainder pixels never retain old content.
- `StretchDIBits` continues copying the already-physical framebuffer without an additional layout scale.
- Mouse input rejects remainder-strip coordinates and divides accepted coordinates by `S`.
- Wheel delta remains `WHEEL_NOTCH_CELLS * U * S` physical pixels before the ordinary input inverse transform.
- `WM_DPICHANGED` triggers a client-size re-read through `applyResizeFromHwnd`; its reported DPI does not choose `S` or layout.

### 7.3 Shared firewall

Android and Win32 must not decide:

- minimum columns/rows;
- HUD side/bottom selection;
- scene padding, control size, wrapping, or stacking;
- palette meaning;
- glyph metrics.

Those decisions remain in commonMain. Platform code supplies dimensions, applies the common grid mechanically, presents pixels, and converts input coordinates.

## 8. Ordered implementation plan

This section is also the work ledger. Implement one phase at a time and update its status and evidence before moving to the next phase. The ledger records implementation progress only; it does not authorize re-measuring, re-deriving, or replacing the locked design rules and reference results in sections 2–7.

### Phase 0 — Common display grid `[pending]`

- Add `DisplayGrid` and `DisplayGridPolicy` with the exact constants/formula in section 3.
- Remove density and width-only scale selection.
- Replace shared logical-bound state with the grid snapshot and renamed layout extents.
- Do not edit scene layout yet beyond compilation-required renames.
- Completion evidence: commonMain compiles; the Google Pixel 10 Android phone and Win32 client-area worked calculations match section 3.4 by direct inspection.

### Phase 1 — One glyph/UI raster path `[pending]`

- Delete `TextRasterScale` and `drawPhysicalRect`.
- Replace text measurement, wrapping, cursor, shadow, glyph, and text-raster-rect calculations with `U × cellSpan` source-grid math.
- Consolidate ordinary and ornamental glyph emission so both use source-grid rectangles.
- Preserve fullwidth conversion and all glyph data.
- Completion evidence: no `TextRasterScale`, `drawPhysicalRect`, or capped base physical pixel scale remains; commonMain and both platform source sets compile.

### Phase 2 — Android and Win32 adapters `[pending]`

- Migrate both platforms to the immutable grid snapshot.
- Apply the same `S` to all primitives and inverse input mapping.
- Clear full physical targets and reject remainder input.
- Preserve platform firewall boundaries.
- Completion evidence: the Google Pixel 10 Android phone uses 33×75 at `S=2`; the current Win32 reference client area uses `S=2`; resize changes complete columns/rows without fractional cells.

### Phase 3 — HUD canonicalization `[pending]`

- Replace percentage HUD dimensions with the locked cell constants.
- Replace candidate scoring with the exact shared physical-aspect-ratio comparison in section 2.5.
- Keep render geometry and hitboxes sourced from the same helper calculations.
- Completion evidence: portrait selects bottom HUD and landscape selects left HUD on either platform; the 1:1 tie selects bottom; every visible button and its hitbox coincide.

### Phase 4 — Four primary scenes `[pending]`

Apply the exact rules in section 5 to:

1. `ActiveTimerScene`;
2. `TemplateCustomizerScene`;
3. `EntropyScene`;
4. `SettingsScene`.

`TemplateForgeScene` receives only the mechanical shared-metric and canonical row fixes needed to remain reachable and consistent with the template tab. Do not redesign `BlockOverlayScene` in this overhaul; adapt it only where renderer/API changes require compilation and preserve its current behavior.

For each primary scene, update render, hit-testing, scroll range, and pagination together. A visual change without the matching input/scroll calculation is incomplete.

### Phase 5 — Verification and ledger closeout `[pending]`

- Run the compile/build commands in section 9.
- Capture the four primary scenes on the Google Pixel 10 Android phone and the Win32 reference client area at the same application state/language where possible.  
- Complete every acceptance item in section 10.
- Search for every forbidden/removal symbol in section 9.
- Record deviations, unverified behavior, and final screenshot paths.

## 9. Verification commands

Do not add new test projects, snapshot frameworks, diagnostic overlays, or validation infrastructure as part of this overhaul.

Android/common compilation:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:compileCommonMainKotlinMetadata :shared-engine:compileDebugKotlinAndroid :app:compileDebugKotlin
```

Android debug APK when device verification begins:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :app:assembleDebug
```

Windows executable link:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :shared-engine:linkDebugExecutableWin
```

Required source searches after implementation:

```powershell
rg -n "TextRasterScale|drawPhysicalRect|MIN_SAFE_LOGICAL_WIDTH|MAX_SAFE_LOGICAL_WIDTH|PHYSICAL_SCALE_PER_DENSITY|FALLBACK_MIN_SPAN_CELLS" app shared-engine
```

Expected result: no production matches.

```powershell
rg -n "dynamicLogicalWidth|dynamicLogicalHeight|setLogicalBounds|SceneManager\.logicalWidth|SceneManager\.logicalHeight" app shared-engine
```

Expected result: no production matches.

Review remaining macro-layout `U / 8` occurrences manually; detail-only occurrences are allowed:

```powershell
rg -n "U / 8" shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/core
```

## 10. Acceptance checklist and evidence

### Grid and raster

- [ ] The Google Pixel 10 Android phone physical target remains 1080×2400; no 528×1200 or other derived target framebuffer is created.
- [ ] The Google Pixel 10 Android phone grid is 33×75 at `S=2`, with a 24-physical-pixel right remainder.
- [ ] Right/bottom remainder pixels are `PaletteIndices.BLACK`, not a platform-native hardcoded color.
- [ ] Current Windows client grid uses `S=2` and its actual `GetClientRect` dimensions.
- [ ] Body glyph source cell is exactly `U×U`; body glyph and one UI cell have identical physical extents.
- [ ] Header glyphs use `2U×2U` source cells and the same global `S`.
- [ ] Every glyph source bit becomes an integer `S×S` physical block for body text.
- [ ] No smoothing, filtering, or fractional presentation coordinate appears.

### Layout

- [ ] Every portrait display/client area uses the bottom HUD regardless of platform.
- [ ] Every landscape display/client area uses the left HUD regardless of platform.
- [ ] A 1:1 physical aspect-ratio tie uses the bottom HUD.
- [ ] HUD placement is chosen once in commonMain from `DisplayGrid.physicalWidthPx > DisplayGrid.physicalHeightPx`; platform identity is not inspected.
- [ ] Template card heights are content/cell-derived rather than percentages of display height.
- [ ] Settings controls use fixed/content-derived row height and existing responsive stacking.
- [ ] Entropy input, task, pager, and detonator controls use fixed/content-derived cells.
- [ ] Active Timer UI controls use canonical cells; procedural art remains display-responsive.
- [ ] No primary scene clips required fullwidth text at its reference grid.
- [ ] Scroll/pagination reaches all content on both references.

### Input

- [ ] Android touch and Win32 mouse use the same grid snapshot as rendering.
- [ ] Hitboxes match rendered controls after resize.
- [ ] Right/bottom remainder strips do not activate UI.
- [ ] Win32 wheel movement remains an integer number of canonical cells.

### Architecture

- [ ] commonMain contains no Android, Win32, JVM, Compose, SwiftUI, React, or other UI-framework dependency.
- [ ] Platform wrappers do not own layout or HUD policy.
- [ ] Core draw APIs continue accepting palette indices, not native colors.
- [ ] No external font/image asset or new dependency is added.
- [ ] No per-frame allocation is introduced in render, glyph, layout, or input hot paths.

### Evidence record

Fill this during implementation:

```text
Android physical dimensions:
Android derived grid:
Android screenshot directory:
Windows client dimensions:
Windows derived grid:
Windows screenshot directory:
Compilation commands/results:
Remaining unverified items:
Approved deviations from this document:
```

## 11. Explicit non-goals

Do not use this overhaul to:

- introduce halfwidth or proportional text;
- replace the Shinonome 16×16 font;
- change application strings or localization;
- redesign procedural artwork;
- move layout into Android or Win32;
- introduce Compose, SwiftUI, React, Electron, or any UI framework;
- change palette meaning or native color conversion;
- add assets, dependencies, test frameworks, debug overlays, or diagnostics;
- change audio, timer, alarm, navigation, persistence, or scene-state behavior;
- preserve the recent text cap under another name.

## 12. Stop conditions

Stop implementation and report the exact conflict instead of improvising if:

- the Google Pixel 10 Android phone reference device does not derive `S=2`, 33 columns, and 75 rows from the locked formula;
- the current Windows client does not derive `S=2`;
- a required primary-scene layout cannot fit or scroll correctly at the locked grid;
- removing the physical text path would require antialiasing or fractional rasterization;
- a platform requires owning a layout decision;
- a proposed fix requires halfwidth text, a fixed design resolution, or a second glyph/UI unit;
- existing user changes overlap a target line and cannot be preserved mechanically.

Do not solve a stop condition by introducing a platform/device/named-resolution exception, a HUD breakpoint other than the exact physical-width-versus-height rule, a density breakpoint, a text-only scale, or an unexplained constant.
