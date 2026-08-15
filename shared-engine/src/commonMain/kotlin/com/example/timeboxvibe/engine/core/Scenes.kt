package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.getStrings
import kotlin.math.roundToInt
import kotlin.math.abs

private const val U = CANONICAL_UI_UNIT

object TouchAction {
    const val DOWN = 0
    const val MOVE = 1
    const val UP   = 2
    const val CANCEL = 3
}

interface Scene {
    fun onEnter(payload: Any? = null)
    fun onExit()
    fun update(dt: Float)
    fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int)
    fun onInput(inputCode: Int)
    fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int)
    fun onInput(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {}
}

// ════════════════════════════════════════════════════════════════════
//  MAIN MENU SCENE
// ════════════════════════════════════════════════════════════════════
object MainMenuScene : Scene {
    override fun onEnter(payload: Any?) {}

    override fun onExit() {}
    override fun update(dt: Float) {}
    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {}
    override fun onInput(inputCode: Int) {}
    override fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {}
}

// ════════════════════════════════════════════════════════════════════
//  ACTIVE TIMER SCENE
// ════════════════════════════════════════════════════════════════════
object ActiveTimerScene : Scene {
    private const val TASK_INPUT_SIDE_PAD = U + (U / 4)
    private const val TASK_INPUT_HEIGHT = (U * 2) + (U / 4)
    private const val TASK_INPUT_INNER_PAD = U / 2
    private const val PRESET_BADGE_SIZE = (U * 2) + (U / 4)
    private const val PRESET_BADGE_GAP = U / 2
    private const val CONTROL_SLOT_COUNT = 3
    private const val CONTROL_BUTTON_HEIGHT = (U * 2) + (U / 2) + (U / 8)
    private const val CONTROL_GAP_PORTRAIT = (U / 2) + (U / 8)
    private const val CONTROL_GAP_LANDSCAPE = U
    private const val CONTROL_ICON_SIZE = U * 2
    private const val CONTROL_ICON_SCALE = 1
    private const val TIMER_RADIUS_WIDTH_NUM = 9f
    private const val TIMER_RADIUS_WIDTH_DEN = 20f
    private const val TIMER_RADIUS_HEIGHT_NUM = 9f
    private const val TIMER_RADIUS_HEIGHT_DEN = 20f
    private const val OUTER_TO_INNER_RING_CELLS = 2
    private const val INNER_TO_QUIET_ZONE_CELLS = 3
    private const val MIN_TIMER_RADIUS_CELLS = 8
    private const val TIMER_TOP_GAP_CELLS = 1
    private const val TIMER_CONTROL_GAP_CELLS = 1
    private const val TIMER_BOTTOM_PAD_CELLS = 1
    private const val CALENDAR_PANEL_HEIGHT_CELLS = 5
    private const val CALENDAR_PANEL_GAP_CELLS = 1
    var isTaskFocused = false
    private val inputContainer = FixedInputContainer(64)
    private val taskCursor = EngineCursorRenderer()
    private var scrollY = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasDragged = false
    private var alarmMarqueeX = 0f
    // Demoscene manager: 6 Wave oscillators + 1 IkChain2D + Perlin rune drift.
    // Allocated on first render so we don't run the constructor at class init.
    private var demoscene: MagicCircleDemoscene? = null

    override fun onEnter(payload: Any?) {
        isTaskFocused = false
        scrollY = 0f
        isDragging = false
        hasDragged = false
        taskCursor.reset()
        // Clear input container
        while (inputContainer.length > 0) {
            inputContainer.processPayload(EngineInputCodes.CMD_BACKSPACE)
        }
        // Seed current task text
        val currentTask = SceneManager.timerActions?.getUiState()?.currentTask ?: ""
        for (i in 0 until currentTask.length) {
            inputContainer.processPayload(currentTask[i].code)
        }
        // Allocate the demoscene manager on entry. Reset to deterministic state.
        val dm = demoscene ?: MagicCircleDemoscene().also { demoscene = it }
        dm.reset()
    }

    override fun onExit() {
        isTaskFocused = false
        isDragging = false
    }

    override fun update(dt: Float) {
        val state = SceneManager.timerActions?.getUiState() ?: return
        if (state.isRinging) {
            alarmMarqueeX -= (U * 8).toFloat() * dt
            if (alarmMarqueeX <= -500f) alarmMarqueeX += 500f
        } else {
            alarmMarqueeX = 0f
        }
        // Tick the 6 demoscene Wave oscillators. No-op when
        // `VisualsStateHolder.demosceneEffectsEnabled` is false.
        demoscene?.update(dt)
        taskCursor.update(dt)
    }

    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val state = SceneManager.timerActions?.getUiState() ?: return
        // This will setup the palette in Pc98GraphicsHardware dynamically
        EngineThemes.getColors(state.appTheme, state.isBreak)
        val strings = getStrings(state.language)
        
        // 0. Global Screen Clear to prevent transparent frame smearing
        renderer.drawRect(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG)
        
        val isPortrait = playX <= 0
        
        val cx: Float
        val baseCy: Float
        val radius: Float
        val playAreaStartX: Float
        val playAreaW: Float
        val playAreaH: Float
        val inputBaseY = timerInputY(logicalHeight)
        val taskInputH = taskInputHeight(state, strings, playW.toFloat())
        
        // Continuous time source for the magic circle. The renderer uses
        // `elapsedSeconds * rateDegPerSec` for each layer's rotation, so the
        // motion is non-cyclic and visibly continues across any time window.
        val elapsedSeconds = FrameClock.seconds(60f)

        if (isPortrait) {
            playAreaStartX = playX.toFloat()
            playAreaW = playW.toFloat()
            playAreaH = playH.toFloat()
            cx = logicalWidth / 2f
            radius = timerRadius(playAreaW, playAreaH)
            baseCy = timerCenterY(playAreaH, inputBaseY, radius, taskInputH)

            // 1. Draw Play Area background (top 85%). When the user enables
            //    the "Background Nebula" setting, the clear color slowly
            //    cycles through BG / BG_ALT / PANEL via a 2-octave Perlin
            //    fbm sampled at the play-area center and the center-offset.
            val nebulaColor = nebulaColorIndex(
                cx, baseCy, playAreaW, playAreaH, elapsedSeconds
            )
            renderer.fillRectDither(0f, 0f, logicalWidth, playAreaH, nebulaColor, nebulaColor, SoftDitherPattern.SOLID)
        } else {
            playAreaStartX = playX.toFloat()
            playAreaW = playW.toFloat()
            playAreaH = playH.toFloat()
            cx = playAreaStartX + (playAreaW / 2f)
            radius = timerRadius(playAreaW, playAreaH)
            baseCy = timerCenterY(playAreaH, inputBaseY, radius, taskInputH)

            // 1. Draw Play Area background (right 70%) with optional nebula.
            val nebulaColor = nebulaColorIndex(
                cx, baseCy, playAreaW, playAreaH, elapsedSeconds
            )
            renderer.fillRectDither(playAreaStartX, 0f, logicalWidth, logicalHeight, nebulaColor, nebulaColor, SoftDitherPattern.SOLID)
        }
        scrollY = scrollY.coerceIn(timerMinScroll(state, playAreaH, logicalHeight, radius, inputBaseY, taskInputH, playAreaW), 0f)
        val cy = baseCy + scrollY
        val inputY = inputBaseY + scrollY
        val btnY = timerControlRowY(baseCy, radius) + scrollY

        // Outer beads = current stage / micro timer. Inner beads = session macro
        // (dual big box, calendar remaining, or sequence set remaining e.g. 60m pomodoro).
        val activePreset = state.presets.firstOrNull { it.id == state.activePresetId }
        val (macroRem, macroTot) = SessionMacroDisplay.resolveMacro(
            mode = state.activeMode,
            sequence = activePreset?.sequence ?: IntArray(0),
            currentIndex = state.currentIndex,
            timeRemaining = state.timeRemaining,
            engineBigRemaining = state.bigTimeRemaining,
            engineBigTotal = state.bigTotalDuration
        )
        val outerProgress = if (state.totalDuration > 0) state.timeRemaining.toFloat() / state.totalDuration.toFloat() else 0f
        val innerProgress = if (state.activeMode == "dual.5") {
            if (state.midTotalDuration > 0) state.midTimeRemaining.toFloat() / state.midTotalDuration.toFloat() else 0f
        } else if (state.isDual || macroTot > 0) {
            if (macroTot > 0) macroRem.toFloat() / macroTot.toFloat() else 0f
        } else {
            0f
        }

        // 3. Draw the nested timebox instrument. `elapsedSeconds` (from
        //    FrameClock) drives each layer's continuous rotation. Demoscene
        //    manager provides Perlin rune drift + FABRIK comet trail; null
        //    when demoscene is disabled in settings.
        val innerRadius = radius - (U * OUTER_TO_INNER_RING_CELLS).toFloat()
        val quietRadius = innerRadius - (U * INNER_TO_QUIET_ZONE_CELLS).toFloat()
        renderer.nestedTimeboxRenderer.render(
            centerX = cx,
            centerY = cy,
            baseRadius = radius,
            quietRadius = quietRadius,
            outerProgress = outerProgress,
            innerProgress = innerProgress,
            elapsedSeconds = elapsedSeconds,
            isDual = state.isDual,
            outerActiveColorIndex = PaletteIndices.HIGHLIGHT,
            innerActiveColorIndex = PaletteIndices.TEXT_SECONDARY,
            magicPrimaryColorIndex = PaletteIndices.ACCENT_PRIMARY,
            magicSecondaryColorIndex = PaletteIndices.ACCENT_DANGER,
            textFrameColorIndex = PaletteIndices.HIGHLIGHT,
            timeRemaining = state.timeRemaining,
            stageLabel = state.currentStageLabel,
            midTimeRemaining = state.midTimeRemaining,
            bigTimeRemaining = macroRem,
            bigTotalDuration = macroTot,
            activeMode = state.activeMode,
            isBreak = state.isBreak,
            sequenceLength = state.sequenceLength,
            strings = strings,
            playAreaW = playAreaW,
            demoscene = demoscene
        )

        // 4. Draw Task Input Box
        val taskText = state.currentTask
        val displayText = taskInputDisplayText(taskText, strings)
        val inputH = taskInputH
        val inputX = playAreaStartX + TASK_INPUT_SIDE_PAD
        val showPresetBadge = playAreaW >= (U * 14) - (U / 4)
        val presetBadgeX = inputX + playAreaW - TASK_INPUT_SIDE_PAD * 2f - PRESET_BADGE_SIZE
        val inputW = if (showPresetBadge) {
            playAreaW - TASK_INPUT_SIDE_PAD * 2f - PRESET_BADGE_SIZE - PRESET_BADGE_GAP
        } else {
            playAreaW - TASK_INPUT_SIDE_PAD * 2f
        }
        renderer.drawRect(inputX, inputY, inputW, inputH, if (isTaskFocused) PaletteIndices.HIGHLIGHT else PaletteIndices.BORDER)

        if (showPresetBadge) {
            val presetBadgeY = inputY + (inputH - PRESET_BADGE_SIZE) / 2f
            renderer.drawRect(presetBadgeX, presetBadgeY, PRESET_BADGE_SIZE.toFloat(), PRESET_BADGE_SIZE.toFloat(), PaletteIndices.BORDER)
            ProceduralIconRenderer.draw(
                renderer,
                activePresetIcon(state.activeMode),
                presetBadgeX + (PRESET_BADGE_SIZE - CONTROL_ICON_SIZE) / 2f,
                presetBadgeY + (PRESET_BADGE_SIZE - CONTROL_ICON_SIZE) / 2f,
                scale = CONTROL_ICON_SCALE,
                primaryColor = PaletteIndices.TEXT_PRIMARY,
                onBackgroundColor = PaletteIndices.BORDER,
                surfaceColor = PaletteIndices.PANEL
            )
        }

        val textAreaWidth = maxOf(U.toFloat(), inputW - TASK_INPUT_INNER_PAD * 2f)
        val textHeight = ProceduralTextRenderer.measureWrappedHeight(displayText, textAreaWidth)
        val textY = inputY + (inputH - textHeight) / 2f
        val textColor = if (taskText.isEmpty()) PaletteIndices.TEXT_DISABLED else PaletteIndices.TEXT_PRIMARY
        ProceduralTextRenderer.drawWrapped(
            renderer = renderer,
            text = displayText,
            x = inputX + TASK_INPUT_INNER_PAD,
            y = textY,
            maxWidth = textAreaWidth,
            color = textColor,
            uppercase = true
        )

        // Task caret: blink owned by EngineCursorRenderer; placement from field + Int U cells.
        if (isTaskFocused) {
            val packedCursor = ProceduralTextRenderer.locateWrappedCursor(
                inputContainer.codePoints,
                0,
                inputContainer.length,
                inputContainer.cursor,
                textAreaWidth
            )
            val caretCellSize = ScaledProceduralRenderer.measureTextHeight()
            val caretX = inputX + TASK_INPUT_INNER_PAD + ProceduralTextRenderer.cursorColumn(packedCursor) * caretCellSize
            val caretY = textY + ProceduralTextRenderer.cursorLine(packedCursor) * caretCellSize
            taskCursor.draw(
                renderer = renderer,
                isFocused = true,
                x = caretX,
                y = caretY,
                colorIndex = PaletteIndices.HIGHLIGHT
            )
        }

        // 8. Draw Timer Control Buttons
        val btnW = timerControlWidth(playAreaW, isPortrait)
        var controlIndex = 0
        while (controlIndex < CONTROL_SLOT_COUNT) {
            val bx = timerControlX(playAreaStartX, playAreaW, isPortrait, controlIndex, btnW)
            drawTimerControlButton(renderer, state, controlIndex, bx, btnY, btnW, CONTROL_BUTTON_HEIGHT.toFloat())
            controlIndex++
        }

        drawActiveCalendarPanel(renderer, state, strings, playAreaStartX, playAreaW, btnY + CONTROL_BUTTON_HEIGHT + (U * CALENDAR_PANEL_GAP_CELLS).toFloat())

        RetroHudComponent.render(renderer, playX, playY, playW, playH)

        if (state.isRinging) {
            val now = getEpochMillis()
            val flashOn = (now % 300L) < 150L
            val bgColor = if (flashOn) PaletteIndices.HIGHLIGHT else PaletteIndices.BG
            renderer.fillRectDither(0f, 0f, logicalWidth, logicalHeight, bgColor, bgColor, SoftDitherPattern.SOLID)

            // Top warning bar — no local function, no allocations
            val marqueeH = (U * 2).toFloat()
            val msg = "ＡＬＡＲＭ　ＲＩＮＧＩＮＧ"
            // draw top marquee strip
            renderer.fillRectDither(0f, 0f, logicalWidth, marqueeH, PaletteIndices.HIGHLIGHT, PaletteIndices.HIGHLIGHT, SoftDitherPattern.SOLID)
            // draw text
            val marqueeTopY = U.toFloat()
            val marqueeTextStr = "　$msg　"
            val marqueeTextLen = marqueeTextStr.length
            val marqueeCharH = ScaledProceduralRenderer.measureTextHeight(ScaledProceduralRenderer.TEXT_SCALE_IDENTITY)
            val marqueeMaxChars = (logicalWidth / marqueeCharH).toInt() + 2
            var marqueeIdx = 0
            while (marqueeIdx < marqueeMaxChars) {
                val gx = alarmMarqueeX + marqueeIdx * marqueeCharH
                if (gx + marqueeCharH > U / 2f && gx < logicalWidth - U / 2f) {
                    renderer.drawGlyph(marqueeTextStr[marqueeIdx % marqueeTextLen], gx, marqueeTopY + (marqueeH - marqueeCharH) / 2f, PaletteIndices.TEXT_PRIMARY, scale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY, startX = 0f, startY = 0f, clipWidth = logicalWidth.toInt(), clipHeight = logicalHeight.toInt())
                }
                marqueeIdx++
            }

            val contentX = U.toFloat()
            val contentW = maxOf(U.toFloat(), logicalWidth - (U * 2).toFloat())
            val alarmCx = logicalWidth / 2f
            val titleText = "ＳＰＥＬＬ　ＣＡＲＤ　ＡＣＴＩＶＥ"
            val titleH = ProceduralTextRenderer.measureHeadingHeight(titleText, contentW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
            val subText = "ＡＤＨＤ　ＢＬＯＣＫＡＤＥ　ＩＮＩＴＩＡＴＥＤ"
            val subH = ProceduralTextRenderer.measureWrappedHeight(subText, contentW)
            val bombText = "ＢＯＭＢ　／　ＤＩＳＭＩＳＳ"
            val bombW = minOf(logicalWidth - (U * 2).toFloat(), maxOf((U * 12).toFloat(), ScaledProceduralRenderer.measureTextWidth(bombText) + (U * 4).toFloat()))
            val bombH = ScaledProceduralRenderer.measureButtonHeight(bombText, bombW, (U * 3).toFloat(), allowTextStacking = true)
            val bombX = (logicalWidth - bombW) / 2f
            val bombY = logicalHeight - bombH - (U / 2).toFloat()
            val marqueeBotY = bombY - marqueeH - (U / 2).toFloat()
            val titleY = marqueeH + U.toFloat()
            ProceduralTextRenderer.drawHeading(
                renderer,
                titleText,
                contentX,
                titleY,
                contentW,
                PaletteIndices.ACCENT_PRIMARY,
                ScaledProceduralRenderer.TEXT_SCALE_HEADER,
                ProceduralTextRenderer.ALIGN_CENTER,
                PaletteIndices.PANEL_DARK
            )

            // Vector-drawn alarm ornament (replaces ProceduralIconRenderer yinyang)
            val ornamentSpace = marqueeBotY - titleY - titleH - subH - (U * 3).toFloat()
            val ornamentR = minOf((U * 4).toFloat(), maxOf((U + U / 2).toFloat(), ornamentSpace / 2f))
            val ornamentCy = titleY + titleH + (U / 2).toFloat() + ornamentR
            val arcProgress = ((now % 4000L).toFloat() / 4000f)
            val tickRotation = ((now % 8000L).toFloat() / 8000f) * 360f
            renderer.drawAliasedProgressArc(alarmCx, ornamentCy, ornamentR - (U / 4).toFloat(), -90f, 360f, arcProgress, PaletteIndices.HIGHLIGHT, 2f)
            renderer.drawAliasedCircle(alarmCx, ornamentCy, ornamentR, PaletteIndices.BORDER, 2f, dashed = true)
            renderer.drawAliasedCircle(alarmCx, ornamentCy, ornamentR - U.toFloat(), PaletteIndices.ACCENT_PRIMARY, 1f)
            renderer.drawRadialTickMarks(alarmCx, ornamentCy, ornamentR + (U / 4).toFloat(), ornamentR + U.toFloat(), 8, tickRotation, PaletteIndices.TEXT_PRIMARY, 1f, majorEvery = 4, majorExtraLength = (U / 2).toFloat())
            renderer.fillRectDither(alarmCx - (U / 2).toFloat(), ornamentCy - (U / 2).toFloat(), alarmCx + (U / 2).toFloat(), ornamentCy + (U / 2).toFloat(), PaletteIndices.HIGHLIGHT, PaletteIndices.HIGHLIGHT, SoftDitherPattern.SOLID)

            val subY = ornamentCy + ornamentR + (U / 2).toFloat()
            ProceduralTextRenderer.drawWrapped(renderer, subText, contentX, subY, contentW, PaletteIndices.TEXT_PRIMARY, alignment = ProceduralTextRenderer.ALIGN_CENTER)

            // Bottom warning bar — inline second time
            var marqueeIdx2 = 0
            renderer.fillRectDither(0f, marqueeBotY, logicalWidth, marqueeBotY + marqueeH, PaletteIndices.HIGHLIGHT, PaletteIndices.HIGHLIGHT, SoftDitherPattern.SOLID)
            while (marqueeIdx2 < marqueeMaxChars) {
                val gx = alarmMarqueeX + marqueeIdx2 * marqueeCharH
                if (gx + marqueeCharH > (U / 2).toFloat() && gx < logicalWidth - (U / 2).toFloat()) {
                    renderer.drawGlyph(marqueeTextStr[marqueeIdx2 % marqueeTextLen], gx, marqueeBotY + (marqueeH - marqueeCharH) / 2f, PaletteIndices.TEXT_PRIMARY, scale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY, startX = 0f, startY = 0f, clipWidth = logicalWidth.toInt(), clipHeight = logicalHeight.toInt())
                }
                marqueeIdx2++
            }

            val bombFrame = PaletteIndices.HIGHLIGHT
            renderer.fillRectDither(bombX, bombY, bombX + bombW, bombY + bombH, bombFrame, bombFrame, SoftDitherPattern.SOLID)
            renderer.fillRectDither(bombX + (U / 8).toFloat(), bombY + (U / 8).toFloat(), bombX + bombW - (U / 8).toFloat(), bombY + bombH - (U / 8).toFloat(), PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)
            val bombTextW = maxOf(U.toFloat(), bombW - U.toFloat())
            val bombTextH = ProceduralTextRenderer.measureWrappedHeight(bombText, bombTextW)
            ProceduralTextRenderer.drawWrapped(renderer, bombText, bombX + (U / 2).toFloat(), bombY + (bombH - bombTextH) / 2f, bombTextW, bombFrame, alignment = ProceduralTextRenderer.ALIGN_CENTER)
        }

    }

    override fun onInput(inputCode: Int) {
        if (isTaskFocused) {
            inputContainer.processPayload(inputCode)
            val builder = StringBuilder()
            for (i in 0 until inputContainer.length) {
                val cp = inputContainer.codePoints[i]
                if (cp <= 0xFFFF) {
                    builder.append(cp.toChar())
                } else {
                    val offset = cp - 0x10000
                    builder.append(((offset shr 10) or 0xD800).toChar())
                    builder.append(((offset and 0x3FF) or 0xDC00).toChar())
                }
            }
            val newTaskStr = builder.toString()
            SceneManager.timerActions?.updateTask(newTaskStr)

            if (inputCode == EngineInputCodes.CMD_ENTER) {
                isTaskFocused = false
            }
        }
    }

    override fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        val isPortrait = playX <= 0
        val inPlayArea = if (isPortrait) y < playH else x >= playX

        if (!isDragging && !inPlayArea) {
            onInput(x, y, action, playX, playY, playW, playH)
            return
        }

        when (action) {
            TouchAction.DOWN -> {
                if (!inPlayArea) return
                isDragging = true
                lastTouchY = y.toFloat()
                initialTouchX = x.toFloat()
                initialTouchY = y.toFloat()
                hasDragged = false
            }
            TouchAction.MOVE -> {
                if (isDragging) {
                    val deltaY = y - lastTouchY
                    if (abs(deltaY) > (U / 4).toFloat()) {
                        hasDragged = true
                    }
                    scrollY += deltaY
                    lastTouchY = y.toFloat()
                    val state = SceneManager.timerActions?.getUiState() ?: return
                    val logicalHeight = SceneManager.logicalHeight
                    val radius = timerRadius(playW.toFloat(), playH.toFloat())
                    val strings = getStrings(state.language)
                    val inputHeight = taskInputHeight(state, strings, playW.toFloat())
                    scrollY = scrollY.coerceIn(timerMinScroll(state, playH.toFloat(), logicalHeight, radius, timerInputY(logicalHeight), inputHeight, playW.toFloat()), 0f)
                }
            }
            TouchAction.UP -> {
                if (isDragging) {
                    isDragging = false
                    val deltaX = x - initialTouchX
                    val deltaY = y - initialTouchY
                    if (inPlayArea && abs(deltaX) < (U / 2).toFloat() && abs(deltaY) < (U / 2).toFloat() && !hasDragged) {
                        onInput(x, y, TouchAction.UP, playX, playY, playW, playH)
                    }
                }
            }
            TouchAction.CANCEL -> {
                isDragging = false
                hasDragged = false
            }
        }
    }

    override fun onInput(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        if (RetroHudComponent.onTouchEvent(x, y, action, playX, playY, playW, playH)) return
        if (action == TouchAction.CANCEL) {
            isTaskFocused = false
            return
        }
        val isUp = action == TouchAction.UP
        if (!isUp) return

        val state = SceneManager.timerActions?.getUiState() ?: return
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val isPortrait = playX <= 0

        val fx = x.toFloat()
        val fy = y.toFloat()

        val playAreaStartX = playX.toFloat()
        val playAreaW = playW.toFloat()
        val playAreaH = playH.toFloat()
        val radius = timerRadius(playAreaW, playAreaH)
        val inputBaseY = timerInputY(logicalHeight)
        val strings = getStrings(state.language)
        val inputH = taskInputHeight(state, strings, playAreaW)
        scrollY = scrollY.coerceIn(timerMinScroll(state, playAreaH, logicalHeight, radius, inputBaseY, inputH, playAreaW), 0f)

        if (state.isRinging) {
            if (SceneManager.timerActionsFromTouchEnabled()) {
                SceneManager.timerActions?.dismissAlarm()
            }
            isTaskFocused = false
            return
        }

        // 1. Task Input Click
        val inputY = inputBaseY + scrollY
        val inputX = playAreaStartX + TASK_INPUT_SIDE_PAD
        val inputW = if (playAreaW >= (U * 14) - (U / 4)) {
            playAreaW - TASK_INPUT_SIDE_PAD * 2f - PRESET_BADGE_SIZE - PRESET_BADGE_GAP
        } else {
            playAreaW - TASK_INPUT_SIDE_PAD * 2f
        }

        if (fy >= inputY && fy <= inputY + inputH && fx >= inputX && fx <= inputX + inputW) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            isTaskFocused = true
            SceneManager.triggerKeyboard()
            return
        }

        isTaskFocused = false

        // 2. Button clicks
        val btnY = timerControlRowY(timerCenterY(playAreaH, inputBaseY, radius, inputH), radius) + scrollY
        val btnW = timerControlWidth(playAreaW, isPortrait)

        if (fy >= btnY && fy <= btnY + CONTROL_BUTTON_HEIGHT) {
            var controlIndex = 0
            while (controlIndex < CONTROL_SLOT_COUNT) {
                val bx = timerControlX(playAreaStartX, playAreaW, isPortrait, controlIndex, btnW)
                if (fx >= bx && fx <= bx + btnW) {
                    when (controlIndex) {
                        0 -> {
                            SceneManager.performHapticFeedback(EngineHaptics.TICK)
                            if (SceneManager.timerActionsFromTouchEnabled()) {
                                SceneManager.timerActions?.resetTimer()
                            }
                        }
                        1 -> {
                            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                            if (SceneManager.timerActionsFromTouchEnabled()) {
                                if (state.isRunning) {
                                    SceneManager.timerActions?.stopTimer()
                                } else {
                                    SceneManager.timerActions?.startTimer()
                                }
                            }
                        }
                        2 -> {
                            if (skipVisible(state.activeMode)) {
                                SceneManager.performHapticFeedback(EngineHaptics.TICK)
                                if (SceneManager.timerActionsFromTouchEnabled()) {
                                    SceneManager.timerActions?.skipTimer()
                                }
                            }
                        }
                    }
                    return
                }
                controlIndex++
            }
        }
    }

    private fun timerControlRowY(timerCenterY: Float, radius: Float): Float {
        return timerCenterY + radius + U * TIMER_CONTROL_GAP_CELLS
    }

    private fun timerControlGap(isPortrait: Boolean): Float {
        return if (isPortrait) CONTROL_GAP_PORTRAIT.toFloat() else CONTROL_GAP_LANDSCAPE.toFloat()
    }

    private fun timerControlWidth(playAreaW: Float, isPortrait: Boolean): Float {
        val gap = timerControlGap(isPortrait)
        return (playAreaW - TASK_INPUT_SIDE_PAD * 2f - gap * (CONTROL_SLOT_COUNT - 1)) / CONTROL_SLOT_COUNT
    }

    private fun timerControlX(playAreaStartX: Float, playAreaW: Float, isPortrait: Boolean, index: Int, buttonWidth: Float): Float {
        val gap = timerControlGap(isPortrait)
        val startX = playAreaStartX + TASK_INPUT_SIDE_PAD
        return startX + index * (buttonWidth + gap)
    }

    private fun timerRadius(playAreaW: Float, playAreaH: Float): Float {
        val widthBound = playAreaW * TIMER_RADIUS_WIDTH_NUM / TIMER_RADIUS_WIDTH_DEN
        val heightBound = playAreaH * TIMER_RADIUS_HEIGHT_NUM / TIMER_RADIUS_HEIGHT_DEN
        return maxOf((U * MIN_TIMER_RADIUS_CELLS).toFloat(), minOf(widthBound, heightBound))
    }

    private fun timerInputY(logicalHeight: Float): Float {
        return maxOf(logicalHeight / 12f, U * 2f)
    }

    private fun timerCenterY(playAreaH: Float, inputY: Float, radius: Float, inputHeight: Float): Float {
        val clearTop = inputY + inputHeight + U * TIMER_TOP_GAP_CELLS + radius
        return maxOf(clearTop, playAreaH / 2f)
    }

    private fun timerMinScroll(state: EngineUiState, playAreaH: Float, logicalHeight: Float, radius: Float, inputY: Float, inputHeight: Float, playAreaW: Float): Float {
        val centerY = timerCenterY(playAreaH, inputY, radius, inputHeight)
        var contentBottom = timerControlRowY(centerY, radius) + CONTROL_BUTTON_HEIGHT + U * TIMER_BOTTOM_PAD_CELLS
        if (state.activeMode == "calendar") {
            contentBottom += U * CALENDAR_PANEL_GAP_CELLS + calendarPanelHeight(state, playAreaW)
        }
        return (playAreaH - contentBottom).coerceAtMost(0f)
    }

    private fun taskInputDisplayText(taskText: String, strings: AppStrings): String {
        return if (taskText.isEmpty()) {
            if (isTaskFocused) "＿" else strings.enterTaskPrompt
        } else {
            taskText
        }
    }

    private fun taskInputWidth(playAreaW: Float): Float {
        return if (playAreaW >= (U * 14) - (U / 4)) {
            playAreaW - TASK_INPUT_SIDE_PAD * 2f - PRESET_BADGE_SIZE - PRESET_BADGE_GAP
        } else {
            playAreaW - TASK_INPUT_SIDE_PAD * 2f
        }
    }

    private fun taskInputHeight(state: EngineUiState, strings: AppStrings, playAreaW: Float): Float {
        val text = taskInputDisplayText(state.currentTask, strings)
        val textAreaWidth = maxOf(U.toFloat(), taskInputWidth(playAreaW) - TASK_INPUT_INNER_PAD * 2f)
        return maxOf(TASK_INPUT_HEIGHT.toFloat(), ProceduralTextRenderer.measureWrappedHeight(text, textAreaWidth) + U.toFloat())
    }

    private fun calendarPanelHeight(state: EngineUiState, playAreaW: Float): Float {
        val preset = activePreset(state) ?: return (U * CALENDAR_PANEL_HEIGHT_CELLS).toFloat()
        val panelWidth = maxOf(U.toFloat(), playAreaW - TASK_INPUT_SIDE_PAD * 2f - U.toFloat())
        val title = if (state.currentStageLabel.isNotEmpty()) state.currentStageLabel else preset.name
        val titleHeight = maxOf(U.toFloat(), ProceduralTextRenderer.measureWrappedHeight(title, panelWidth))
        val typeHeight = maxOf(U.toFloat(), ProceduralTextRenderer.measureWrappedHeight(state.currentStageType, panelWidth))
        return (U / 2).toFloat() + titleHeight + U.toFloat() + typeHeight + U.toFloat() + (U / 2).toFloat()
    }

    private fun drawActiveCalendarPanel(
        renderer: ScaledProceduralRenderer,
        state: EngineUiState,
        strings: AppStrings,
        playAreaStartX: Float,
        playAreaW: Float,
        y: Float
    ) {
        if (state.activeMode != "calendar") return
        val preset = activePreset(state) ?: return
        val panelX = playAreaStartX + TASK_INPUT_SIDE_PAD
        val panelW = playAreaW - TASK_INPUT_SIDE_PAD * 2f
        val panelH = calendarPanelHeight(state, playAreaW)
        if (panelW <= U.toFloat() || panelH <= U.toFloat()) return
        renderer.drawRect(panelX, y, panelW, panelH, PaletteIndices.BORDER)
        val titleY = y + (U / 2).toFloat()
        val titleW = panelW - U.toFloat()
        val title = if (state.currentStageLabel.isNotEmpty()) state.currentStageLabel else preset.name
        val titleHeight = maxOf(U.toFloat(), ProceduralTextRenderer.measureWrappedHeight(title, titleW))
        ProceduralTextRenderer.drawWrapped(
            renderer = renderer,
            text = title,
            x = panelX + (U / 2).toFloat(),
            y = titleY,
            maxWidth = titleW,
            color = PaletteIndices.TEXT_PRIMARY,
            uppercase = true
        )
        val countY = titleY + titleHeight + U / 2f
        drawStepCentered(renderer, panelX + panelW / 2f, state.currentIndex, state.sequenceLength, countY, 1, PaletteIndices.TEXT_SECONDARY)
        val typeY = titleY + titleHeight + U.toFloat()
        val typeHeight = maxOf(U.toFloat(), ProceduralTextRenderer.measureWrappedHeight(state.currentStageType, titleW))
        if (state.currentStageType.isNotEmpty()) {
            ProceduralTextRenderer.drawWrapped(
                renderer = renderer,
                text = state.currentStageType,
                x = panelX + (U / 2).toFloat(),
                y = typeY,
                maxWidth = titleW,
                color = PaletteIndices.TEXT_SECONDARY,
                uppercase = true
            )
        }
        drawCalendarTimeline(renderer, preset, panelX + (U / 2).toFloat(), typeY + typeHeight, panelW - U.toFloat(), U.toFloat(), true)
    }

    private fun activePreset(state: EngineUiState): TimerPreset? {
        var i = 0
        while (i < state.presets.size) {
            val preset = state.presets[i]
            if (preset.id == state.activePresetId) return preset
            i++
        }
        return null
    }

    private fun drawTimerControlButton(
        renderer: ScaledProceduralRenderer,
        state: EngineUiState,
        index: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        when (index) {
            0 -> drawIconButton(renderer, "reset_yinyang", x, y, width, height, false, true)
            1 -> drawIconButton(renderer, if (state.isRunning) "pause_ofuda" else "play_danmaku", x, y, width, height, state.isRunning, true)
            2 -> {
                if (skipVisible(state.activeMode)) {
                    drawIconButton(renderer, "skip_double_danmaku", x, y, width, height, false, true)
                } else {
                    drawIconButton(renderer, "", x, y, width, height, false, false)
                }
            }
        }
    }

    private fun drawIconButton(
        renderer: ScaledProceduralRenderer,
        iconName: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        isClicked: Boolean,
        drawIcon: Boolean
    ) {
        val frameColor = PaletteIndices.BORDER_BRIGHT
        val fillColor = if (isClicked) PaletteIndices.BORDER_BRIGHT else PaletteIndices.PANEL_DARK
        renderer.fillRectDither(x, y, x + width, y + height, frameColor, frameColor, SoftDitherPattern.SOLID)
        renderer.fillRectDither(x + (U / 8), y + (U / 8), x + width - (U / 8), y + height - (U / 8), fillColor, fillColor, SoftDitherPattern.SOLID)
        if (!drawIcon) return

        val contentColor = if (isClicked) PaletteIndices.PANEL_DARK else PaletteIndices.TEXT_PRIMARY
        val surfaceColor = if (isClicked) PaletteIndices.ACCENT_PRIMARY else PaletteIndices.PANEL_DARK
        val iconX = x + (width - CONTROL_ICON_SIZE) / 2f
        val iconY = y + (U / 8)
        ProceduralIconRenderer.draw(
            renderer,
            iconName,
            iconX,
            iconY,
            scale = CONTROL_ICON_SCALE,
            primaryColor = contentColor,
            onBackgroundColor = PaletteIndices.ACCENT_SECONDARY,
            surfaceColor = surfaceColor
        )
    }

    private fun skipVisible(activeMode: String): Boolean {
        return activeMode == "sequence" || activeMode == "dual-sequence" || activeMode == "calendar"
    }

    private fun activePresetIcon(activeMode: String): String {
        return when (activeMode) {
            "classic" -> "watch"
            "dual" -> "yinyang"
            "dual.5" -> "hakkero"
            "sequence" -> "ribbon"
            "dual-sequence" -> "skip_double_danmaku"
            "calendar" -> "ofuda"
            else -> "gohei"
        }
    }

    private fun drawTimeCentered(renderer: ScaledProceduralRenderer, cx: Float, secs: Int, centerY: Float, scale: Int, colorIndex: Int) {
        val s = if (secs < 0) 0 else secs
        val m = s / 60
        val sec = s % 60
        
        val m1 = m / 10
        val m2 = m % 10
        val s1 = sec / 10
        val s2 = sec % 10
        
        val textWidth = 5 * ScaledProceduralRenderer.measureTextHeight(scale)
        val startX = cx - textWidth / 2f
        val startY = centerY - ScaledProceduralRenderer.measureTextHeight(scale) / 2f
        val charW = ScaledProceduralRenderer.measureTextHeight(scale)
        
        renderer.drawGlyph(FULLWIDTH_DIGITS[m1], startX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale)
        renderer.drawGlyph(FULLWIDTH_DIGITS[m2], startX + charW, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale)
        renderer.drawGlyph('：', startX + charW * 2f, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale)
        renderer.drawGlyph(FULLWIDTH_DIGITS[s1], startX + charW * 3f, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale)
        renderer.drawGlyph(FULLWIDTH_DIGITS[s2], startX + charW * 4f, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale)
    }

    private fun drawStepCentered(renderer: ScaledProceduralRenderer, cx: Float, current: Int, total: Int, centerY: Float, scale: Int, colorIndex: Int) {
        val xVal = current + 1
        val yVal = total
        
        val x1 = if (xVal >= 10) (xVal / 10) else -1
        val x2 = xVal % 10
        val y1 = if (yVal >= 10) (yVal / 10) else -1
        val y2 = yVal % 10
        
        val len = 5 + (if (x1 >= 0) 2 else 1) + 1 + (if (y1 >= 0) 2 else 1)
        val textWidth = len * ScaledProceduralRenderer.measureTextHeight(scale)
        var curX = cx - textWidth / 2f
        val startY = centerY - ScaledProceduralRenderer.measureTextHeight(scale) / 2f
        val charW = ScaledProceduralRenderer.measureTextHeight(scale)
        
        renderer.drawGlyph('Ｓ', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph('Ｔ', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph('Ｅ', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph('Ｐ', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph('　', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        
        if (x1 >= 0) {
            renderer.drawGlyph(FULLWIDTH_DIGITS[x1], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        }
        renderer.drawGlyph(FULLWIDTH_DIGITS[x2], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        
        renderer.drawGlyph('／', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        
        if (y1 >= 0) {
            renderer.drawGlyph(FULLWIDTH_DIGITS[y1], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        }
        renderer.drawGlyph(FULLWIDTH_DIGITS[y2], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
    }

    private fun drawAlarmTimeCentered(renderer: ScaledProceduralRenderer, cx: Float, secs: Int, centerY: Float, scale: Int, colorIndex: Int) {
        val s = if (secs < 0) 0 else secs
        val m = s / 60
        val sec = s % 60
        
        val m1 = m / 10
        val m2 = m % 10
        val s1 = sec / 10
        val s2 = sec % 10
        
        val textWidth = 16 * ScaledProceduralRenderer.measureTextHeight(scale)
        var curX = cx - textWidth / 2f
        val startY = centerY - ScaledProceduralRenderer.measureTextHeight(scale) / 2f
        val charW = ScaledProceduralRenderer.measureTextHeight(scale)
        
        val prefix = "［　ＡＬＡＲＭ：　"
        var i = 0
        while (i < prefix.length) {
            renderer.drawGlyph(prefix[i], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale)
            curX += charW
            i++
        }
        
        renderer.drawGlyph(FULLWIDTH_DIGITS[m1], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph(FULLWIDTH_DIGITS[m2], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph('：', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph(FULLWIDTH_DIGITS[s1], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph(FULLWIDTH_DIGITS[s2], curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        
        renderer.drawGlyph('　', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale); curX += charW
        renderer.drawGlyph('］', curX, startY, colorIndex, shadowColorIndex = PaletteIndices.PANEL_DARK, scale = scale)
    }

    /**
     * Sample the Perlin nebula at two points in the play area and pick a
     * palette index for the play-area background.
     *
     * When `backgroundNebulaEnabled` is false, returns the standard `BG`
     * (no modulation). When true, samples Perlin fbm at the play-area
     * center and at the play-area midpoint-offset, averages the two, and
     * picks one of three dark colors: `BG` (near-black void), `BG_ALT`
     * (dark blue-gray mid), or `PANEL` (dark warm brown peak). The time
     * scale gives a noticeable change every ~12 seconds.
     */
    private fun nebulaColorIndex(
        centerX: Float,
        centerY: Float,
        playAreaW: Float,
        playAreaH: Float,
        elapsedSeconds: Float
    ): Int {
        if (!VisualsStateHolder.backgroundNebulaEnabled) return PaletteIndices.BG
        val t = elapsedSeconds * NEBULA_TIME_SCALE
        val n1 = PerlinNoise.fbm(
            centerX * NEBULA_SPATIAL_SCALE + t,
            centerY * NEBULA_SPATIAL_SCALE,
            octaves = 2
        )
        val n2 = PerlinNoise.fbm(
            (centerX + playAreaW * 0.5f) * NEBULA_SPATIAL_SCALE + t,
            (centerY + playAreaH * 0.5f) * NEBULA_SPATIAL_SCALE,
            octaves = 2
        )
        val avg = (n1 + n2) * 0.5f
        return when {
            avg >  0.2f -> PaletteIndices.PANEL
            avg > -0.1f -> PaletteIndices.BG_ALT
            else        -> PaletteIndices.BG
        }
    }

    private const val NEBULA_SPATIAL_SCALE = 0.005f
    private const val NEBULA_TIME_SCALE = 0.08f
}

// ════════════════════════════════════════════════════════════════════
//  TEMPLATE CUSTOMIZER SCENE
// ════════════════════════════════════════════════════════════════════
object TemplateCustomizerScene : Scene {
    private var scrollY = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasDragged = false

    override fun onEnter(payload: Any?) {
        scrollY = 0f
        isDragging = false
        hasDragged = false
    }

    override fun onExit() {
        isDragging = false
    }
    
    override fun update(dt: Float) {}

    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val state = SceneManager.timerActions?.getUiState() ?: return
        val strings = getStrings(state.language)
        SceneManager.logStringsAfterLanguageChange("TemplateCustomizerScene", state.language)
        // This will setup the palette in Pc98GraphicsHardware dynamically
        EngineThemes.getColors(state.appTheme, state.isBreak)
        
        // 0. Global Screen Clear to prevent transparent frame smearing
        renderer.drawRect(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG)
        
        val playAreaStartX = playX.toFloat()
        val playAreaW = playW.toFloat()
        val playAreaH = playH.toFloat()

        renderer.fillRectDither(playAreaStartX, 0f, playAreaStartX + playAreaW, playAreaH, PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)

        val baseCardH = maxOf(playAreaH * 3f / 20f, ((U * 4) - (U / 4)).toFloat())
        val cardSpacing = maxOf(playAreaH * 3f / 100f, ((U / 4) + (U / 8)).toFloat())
        val safeTop = maxOf(logicalHeight * 0.08f, ((U * 2) - (U / 8)).toFloat())
        val forgeBtnW = maxOf(((U * 6) - (U / 4)).toFloat(), playAreaW * 0.24f)
        val forgeBtnH = (U + (U / 2) + (U / 8)).toFloat()
        val forgeBtnX = playAreaStartX + playAreaW - forgeBtnW - U - (U / 4)
        val maxHeaderW = maxOf(U.toFloat(), forgeBtnX - playAreaStartX - ((U * 2) - (U / 4)))
        val headerTextH = ProceduralTextRenderer.measureHeadingHeight(strings.presetsTitle, maxHeaderW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val headerRowH = maxOf(forgeBtnH, headerTextH)
        val headerCoverH = safeTop + headerRowH + (U / 2).toFloat()
        scrollY = scrollY.coerceIn(templateMinScroll(state, playAreaW, playAreaH, logicalHeight), 0f)

        // Draw cards with layout cursor starting strictly at headerCoverH + scrollY
        var currentY = headerCoverH + scrollY
        var idx = 0
        while (idx < state.presets.size) {
            val preset = state.presets[idx]
            if (preset.id == "emergency") {
                idx++
                continue
            }
            val isActive = preset.id == state.activePresetId
            val cardH = templateCardHeight(preset, playAreaStartX, playAreaW, baseCardH)
            
            val frameColorIndex = if (isActive) PaletteIndices.PRIMARY else PaletteIndices.SECONDARY
            val cardX = playAreaStartX + U + (U / 4)
            val cardW = playAreaW - (U * 2) - (U / 2)
            
            if (isActive) {
                renderer.fillRectDither(cardX, currentY, cardX + cardW, currentY + cardH, frameColorIndex, frameColorIndex, SoftDitherPattern.SOLID)
            } else {
                renderer.drawRect(cardX, currentY, cardW, cardH, frameColorIndex)
            }

            val textLeftX = cardX + (U / 2 + U / 8).toFloat()
            val hasDelete = preset.id.startsWith("custom_")
            val delW = (U * 4) - (U / 4)
            val delH = U + (U / 2) + (U / 8)
            val delX = playAreaStartX + playAreaW - (U * 5) - (U / 2) - (U / 8)
            val editX = delX - delW - (U / 2).toFloat()
            val delY = currentY + (cardH - delH) / 2f
            
            val textRightLimit = if (hasDelete) editX - (U / 2).toFloat() else cardX + cardW - (U / 2 + U / 8).toFloat()
            val maxTextW = maxOf(U.toFloat(), textRightLimit - textLeftX)

            val nameScale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
            val textColor = if (isActive) PaletteIndices.BLACK else PaletteIndices.PRIMARY
            val textTop = currentY + (U / 4).toFloat()
            val nameH = ProceduralTextRenderer.measureWrappedHeight(preset.name, maxTextW, nameScale)
            ProceduralTextRenderer.drawWrapped(renderer, preset.name, textLeftX, textTop, maxTextW, textColor, nameScale, uppercase = true)

            if (preset.mode == "calendar") {
                val idColor = if (isActive) PaletteIndices.BLACK else PaletteIndices.SECONDARY
                val idScale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
                val idY = textTop + nameH
                val idH = ProceduralTextRenderer.measurePresetIdHeight(preset.id, maxTextW, idScale)
                ProceduralTextRenderer.drawPresetIdWrapped(renderer, preset.id, textLeftX, idY, maxTextW, idColor, idScale)
                val timelineY = idY + idH
                val timelineH = U.toFloat()
                drawCalendarTimeline(renderer, preset, textLeftX, timelineY, maxTextW, timelineH, isActive)
            } else {
                val modeW = maxTextW * 2f / 5f
                val modeScale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
                val modeColor = if (isActive) PaletteIndices.PRIMARY else PaletteIndices.SECONDARY
                val detailY = textTop + nameH
                ProceduralTextRenderer.drawWrapped(renderer, preset.mode, textLeftX, detailY, modeW, modeColor, modeScale, uppercase = true)

                val idW = maxTextW / 2f
                val idScale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
                val idColor = if (isActive) PaletteIndices.BLACK else PaletteIndices.SECONDARY
                ProceduralTextRenderer.drawPresetIdWrapped(renderer, preset.id, textLeftX + maxTextW * 0.45f, detailY, idW, idColor, idScale)
            }

            if (preset.id.startsWith("custom_")) {
                val delW = ((U * 4) - (U / 4)).toFloat()
                val delH = (U + (U / 2) + (U / 8)).toFloat()
                val delX = playAreaStartX + playAreaW - (U * 5) - (U / 2) - (U / 8)
                val editX = delX - delW - (U / 2).toFloat()
                val delY = currentY + (cardH - delH) / 2f
                renderer.drawButton("ＥＤＩＴ", editX, delY, delW, delH, isClicked = false)
                renderer.drawButton("ＤＥＬ", delX, delY, delW, delH, isClicked = false)
            }
            
            currentY += cardH + cardSpacing
            idx++
        }

        // Draw solid background header cover to cover any scrolled cards at the top
        renderer.fillRectDither(playAreaStartX, 0f, playAreaStartX + playAreaW, headerCoverH, PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)

        // Draw header over the cover
        val headerText = strings.presetsTitle
        val headerY = safeTop + (headerRowH - headerTextH) / 2f
        val forgeBtnY = safeTop + (headerRowH - forgeBtnH) / 2f
        ProceduralTextRenderer.drawHeading(renderer, headerText, playAreaStartX + U + (U / 4), headerY, maxHeaderW, PaletteIndices.PRIMARY, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        renderer.drawButton("ＦＯＲＧＥ", forgeBtnX, forgeBtnY, forgeBtnW, forgeBtnH, isClicked = false, allowTextStacking = false)
        renderer.drawLine(playAreaStartX + (U / 2 + U / 8).toFloat(), headerCoverH - (U / 8).toFloat(), playAreaStartX + playAreaW - (U / 2 + U / 8).toFloat(), headerCoverH - (U / 8).toFloat(), PaletteIndices.SECONDARY, 1f)
        RetroHudComponent.render(renderer, playX, playY, playW, playH)

    }

    override fun onInput(inputCode: Int) {}

    override fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        val isPortrait = playX <= 0
        val playAreaStartX = playX.toFloat()
        val playAreaH = playH.toFloat()
        val logicalHeight = SceneManager.logicalHeight
        val inPlayArea = if (isPortrait) y < playAreaH else x >= playAreaStartX

        if (!isDragging && !inPlayArea) {
            onInput(x, y, action, playX, playY, playW, playH)
            return
        }

        when (action) {
            TouchAction.DOWN -> {
                if (!inPlayArea) return
                isDragging = true
                lastTouchY = y.toFloat()
                initialTouchX = x.toFloat()
                initialTouchY = y.toFloat()
                hasDragged = false
            }
            TouchAction.MOVE -> {
                if (isDragging) {
                    val deltaY = y - lastTouchY
                    if (abs(deltaY) > (U / 4).toFloat()) {
                        hasDragged = true
                    }
                    scrollY += deltaY
                    lastTouchY = y.toFloat()

                    val state = SceneManager.timerActions?.getUiState() ?: return
                    scrollY = scrollY.coerceIn(templateMinScroll(state, playW.toFloat(), playAreaH, logicalHeight), 0f)
                }
            }
            TouchAction.UP -> {
                if (isDragging) {
                    isDragging = false
                    val deltaX = x - initialTouchX
                    val deltaY = y - initialTouchY
                    if (inPlayArea && abs(deltaX) < (U / 2).toFloat() && abs(deltaY) < (U / 2).toFloat() && !hasDragged) {
                        onInput(x, y, TouchAction.UP, playX, playY, playW, playH)
                    }
                }
            }
            TouchAction.CANCEL -> {
                isDragging = false
                hasDragged = false
            }
        }
    }

    override fun onInput(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        if (RetroHudComponent.onTouchEvent(x, y, action, playX, playY, playW, playH)) return
        val isUp = action == TouchAction.UP
        if (!isUp) return

        val state = SceneManager.timerActions?.getUiState() ?: return
        val playAreaStartX = playX.toFloat()
        val playAreaW = playW.toFloat()
        val playAreaH = playH.toFloat()
        val logicalHeight = SceneManager.logicalHeight

        val safeTop = maxOf(logicalHeight * 0.08f, ((U * 2) - (U / 8)).toFloat())
        val baseCardH = maxOf(playAreaH * 3f / 20f, ((U * 4) - (U / 4)).toFloat())
        val cardSpacing = maxOf(playAreaH * 3f / 100f, ((U / 4) + (U / 8)).toFloat())
        val forgeBtnW = maxOf(((U * 6) - (U / 4)).toFloat(), playAreaW * 0.24f)
        val forgeBtnH = (U + (U / 2) + (U / 8)).toFloat()
        val forgeBtnX = playAreaStartX + playAreaW - forgeBtnW - U - (U / 4)
        val maxHeaderW = maxOf(U.toFloat(), forgeBtnX - playAreaStartX - ((U * 2) - (U / 4)))
        val headerTextH = ProceduralTextRenderer.measureHeadingHeight(getStrings(state.language).presetsTitle, maxHeaderW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val headerRowH = maxOf(forgeBtnH, headerTextH)
        val headerCoverH = safeTop + headerRowH + (U / 2).toFloat()
        val forgeBtnY = safeTop + (headerRowH - forgeBtnH) / 2f
        var currentY = headerCoverH + scrollY

        val fx = x.toFloat()
        val fy = y.toFloat()

        if (fx >= forgeBtnX && fx <= forgeBtnX + forgeBtnW && fy >= forgeBtnY && fy <= forgeBtnY + forgeBtnH) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            SceneManager.switchScene(TemplateForgeScene)
            return
        }

        var idx = 0
        while (idx < state.presets.size) {
            val preset = state.presets[idx]
            if (preset.id == "emergency") {
                idx++
                continue
            }
            val cardH = templateCardHeight(preset, playAreaStartX, playAreaW, baseCardH)
            val cardY = currentY
            currentY += cardH + cardSpacing

            if (fy >= cardY && fy <= cardY + cardH) {
                if (fx >= playAreaStartX + U + (U / 4) && fx <= playAreaStartX + playAreaW - U - (U / 4)) {
                    val delW = (U * 4) - (U / 4)
                    val delH = U + (U / 2) + (U / 8)
                    val delX = playAreaStartX + playAreaW - (U * 5) - (U / 2) - (U / 8)
                    val editX = delX - delW - (U / 2).toFloat()
                    val delY = cardY + (cardH - delH) / 2f
                    if (preset.id.startsWith("custom_") && fy >= delY && fy <= delY + delH) {
                        if (fx >= editX && fx <= editX + delW) {
                            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                            SceneManager.switchScene(TemplateForgeScene, preset)
                        } else if (fx >= delX && fx <= delX + delW) {
                            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                            SceneManager.timerActions?.deletePreset(preset.id)
                        } else {
                            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                            SceneManager.timerActions?.selectPreset(preset.id)
                            SceneManager.switchScene(ActiveTimerScene)
                        }
                    } else {
                        SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                        SceneManager.timerActions?.selectPreset(preset.id)
                        SceneManager.switchScene(ActiveTimerScene)
                    }
                }
                return
            }
            idx++
        }
    }

    private fun templateMinScroll(state: EngineUiState, playAreaW: Float, playAreaH: Float, logicalHeight: Float): Float {
        val baseCardH = maxOf(playAreaH * 3f / 20f, ((U * 4) - (U / 4)).toFloat())
        val cardSpacing = maxOf(playAreaH * 3f / 100f, ((U / 4) + (U / 8)).toFloat())
        val safeTop = maxOf(logicalHeight * 0.08f, ((U * 2) - (U / 8)).toFloat())
        val forgeBtnW = maxOf(((U * 6) - (U / 4)).toFloat(), playAreaW * 0.24f)
        val forgeBtnH = (U + (U / 2) + (U / 8)).toFloat()
        val forgeBtnX = playAreaW - forgeBtnW - U - (U / 4)
        val maxHeaderW = maxOf(U.toFloat(), forgeBtnX - ((U * 2) - (U / 4)))
        val headerTextH = ProceduralTextRenderer.measureHeadingHeight(getStrings(state.language).presetsTitle, maxHeaderW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val headerCoverH = safeTop + maxOf(forgeBtnH, headerTextH) + (U / 2).toFloat()
        var contentBottom = headerCoverH
        var visibleCount = 0
        var i = 0
        while (i < state.presets.size) {
            val preset = state.presets[i]
            if (preset.id != "emergency") {
                if (visibleCount > 0) contentBottom += cardSpacing
                contentBottom += templateCardHeight(preset, 0f, playAreaW, baseCardH)
                visibleCount++
            }
            i++
        }
        if (visibleCount <= 0) return 0f
        contentBottom += U.toFloat()
        return (playAreaH - contentBottom).coerceAtMost(0f)
    }

    private fun templateCardHeight(preset: TimerPreset, playAreaStartX: Float, playAreaW: Float, minimumHeight: Float): Float {
        val cardX = playAreaStartX + U + (U / 4)
        val cardW = playAreaW - (U * 2) - (U / 2)
        val textLeftX = cardX + (U / 2 + U / 8).toFloat()
        val hasDelete = preset.id.startsWith("custom_")
        val delW = ((U * 4) - (U / 4)).toFloat()
        val delX = playAreaStartX + playAreaW - (U * 5) - (U / 2) - (U / 8)
        val editX = delX - delW - (U / 2).toFloat()
        val textRightLimit = if (hasDelete) editX - (U / 2).toFloat() else cardX + cardW - (U / 2 + U / 8).toFloat()
        val textW = maxOf(U.toFloat(), textRightLimit - textLeftX)
        val nameH = ProceduralTextRenderer.measureWrappedHeight(preset.name, textW)
        val detailH = if (preset.mode == "calendar") {
            ProceduralTextRenderer.measurePresetIdHeight(preset.id, textW) + U.toFloat()
        } else {
            val modeH = ProceduralTextRenderer.measureWrappedHeight(preset.mode, textW * 2f / 5f)
            val idH = ProceduralTextRenderer.measurePresetIdHeight(preset.id, textW / 2f)
            maxOf(modeH, idH)
        }
        return maxOf(minimumHeight, nameH + detailH + (U / 2).toFloat())
    }

}

// ════════════════════════════════════════════════════════════════════
object TemplateForgeScene : Scene {
    private const val FOCUS_NONE = 0
    private const val FOCUS_NAME = 1
    private const val FOCUS_SEQUENCE = 2
    private const val FOCUS_CALENDAR_LABEL = 3
    private const val MAX_CALENDAR_BLOCKS = 8
    private const val LABEL_COLUMN_RATIO_NUM = 2f
    private const val LABEL_COLUMN_RATIO_DEN = 5f
    private const val SAFE_TOP_RATIO_DEN = U - (U / 4)
    private const val CONTENT_PAD_RATIO_DEN = U + (U / 4)
    private const val CONTROL_HEIGHT_CELLS = 2
    private const val HEADER_BUTTON_WIDTH_CELLS = 5
    private const val TITLE_ROW_HEIGHT_CELLS = 2
    private const val TITLE_GAP_CELLS = 1
    private const val SAVE_GAP_CELLS = 1
    private const val BUTTON_INNER_PAD_CELLS = 1

    private var scrollY = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasDragged = false
    private var focusedInput = FOCUS_NONE
    private var selectedCalendarBlock = 0
    private var calendarBlockCount = 2
    private var editingPresetId: String? = null

    private val presetNameInput = FixedInputContainer(32)
    private val sequenceInput = FixedInputContainer(64)
    private val calendarLabelInputs = Array(MAX_CALENDAR_BLOCKS) { FixedInputContainer(24) }
    private val calendarDurationsMinutes = IntArray(MAX_CALENDAR_BLOCKS)
    private val calendarRelaxFlags = BooleanArray(MAX_CALENDAR_BLOCKS)

    private val modeKeys = arrayOf("classic", "dual", "dual.5", "sequence", "dual-sequence", "calendar")
    private val modeLabels = arrayOf("ＣＬＡＳＳＩＣ", "ＤＵＡＬ", "ＤＵＡＬ．５", "ＳＰＩＲＡＬ", "ＳＰＩＲＡＬ＋", "ＣＡＬＥＮＤＡＲ")
    private val behaviorKeys = arrayOf("alarm", "auto")
    private val behaviorLabels = arrayOf("ＲＥＱＵＩＲＥ", "ＡＵＴＯ")
    private val sequenceUnitLabels = arrayOf("ＭＩＮＵＴＥＳ", "ＳＥＣＯＮＤＳ")

    private var modeIndex = 0
    private var behaviorIndex = 0
    private var sequenceUnitIndex = 0
    private var classicDurationMinutes = 25
    private var dualBigMinutes = 60
    private var dualSmallSeconds = 90
    private var dual5BigMinutes = 60
    private var dual5MidMinutes = 15
    private var dual5SmallSeconds = 300
    private var dualSequenceSmallSeconds = 60

    override fun onEnter(payload: Any?) {
        scrollY = 0f
        isDragging = false
        hasDragged = false
        focusedInput = FOCUS_NONE
        selectedCalendarBlock = 0
        calendarBlockCount = 2
        modeIndex = 0
        behaviorIndex = 0
        sequenceUnitIndex = 0
        classicDurationMinutes = 25
        dualBigMinutes = 60
        dualSmallSeconds = 90
        dual5BigMinutes = 60
        dual5MidMinutes = 15
        dual5SmallSeconds = 300
        dualSequenceSmallSeconds = 60
        editingPresetId = null
        clearInput(presetNameInput)
        clearInput(sequenceInput)
        var i = 0
        while (i < MAX_CALENDAR_BLOCKS) {
            clearInput(calendarLabelInputs[i])
            calendarDurationsMinutes[i] = 25
            calendarRelaxFlags[i] = false
            i++
        }
        val language = SceneManager.timerActions?.getUiState()?.language ?: "en"
        val strings = getStrings(language)
        setInput(calendarLabelInputs[0], strings.focusLabel)
        setInput(calendarLabelInputs[1], strings.breakLabel)
        calendarDurationsMinutes[1] = 5
        calendarRelaxFlags[1] = true
        if (payload is TimerPreset) {
            loadPreset(payload.normalized(logFailures = true))
        }
    }

    override fun onExit() {
        focusedInput = FOCUS_NONE
    }

    override fun update(dt: Float) {}

    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val state = SceneManager.timerActions?.getUiState() ?: return
        val strings = getStrings(state.language)
        SceneManager.logStringsAfterLanguageChange("TemplateForgeScene", state.language)
        EngineThemes.getColors(state.appTheme, state.isBreak)
        renderer.drawRect(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG)

        val isPortrait = playX <= 0
        val playAreaStartX = playX.toFloat()
        val playAreaW = playW.toFloat()
        val playAreaH = playH.toFloat()
        val padding = maxOf(U.toFloat(), playAreaW / CONTENT_PAD_RATIO_DEN)
        val contentX = playAreaStartX + padding
        val contentW = playAreaW - padding * 2f
        val safeTop = maxOf(logicalHeight / SAFE_TOP_RATIO_DEN, (U * TITLE_ROW_HEIGHT_CELLS).toFloat())
        val rowH = (U * CONTROL_HEIGHT_CELLS).toFloat()
        val gap = (U / 2).toFloat()
        val buttonW = maxOf((U * HEADER_BUTTON_WIDTH_CELLS).toFloat(), contentW * LABEL_COLUMN_RATIO_NUM / (LABEL_COLUMN_RATIO_DEN * 2f))
        val buttonX = playAreaStartX + playAreaW - padding - buttonW
        val titleW = maxOf(U.toFloat(), buttonX - contentX - gap)
        val titleH = ProceduralTextRenderer.measureHeadingHeight(strings.forgeTitle, titleW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val cancelH = ScaledProceduralRenderer.measureButtonHeight(strings.cancel, buttonW, rowH, allowTextStacking = true)
        val headerRowH = maxOf(rowH, titleH, cancelH)
        val titleY = safeTop + (headerRowH - titleH) / 2f
        val cancelY = safeTop + (headerRowH - cancelH) / 2f

        renderer.fillRectDither(playAreaStartX, 0f, playAreaStartX + playAreaW, playAreaH, PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)

        scrollY = scrollY.coerceIn(contentMinScroll(playAreaH, logicalHeight), 0f)
        val headerCoverH = safeTop + headerRowH + gap
        var y = headerCoverH + gap + scrollY

        y = drawInputRow(renderer, strings.presetNameLabel, inputToString(presetNameInput), strings.presetNamePlaceholder, contentX, contentW, y, rowH, focusedInput == FOCUS_NAME)
        y = drawStepperRow(renderer, strings.engineStyleLabel, modeLabels[modeIndex], contentX, contentW, y, rowH)
        val behaviorLabel = if (modeKeys[modeIndex] == "calendar") behaviorLabels[0] else behaviorLabels[behaviorIndex]
        y = drawStepperRow(renderer, strings.completionBehaviorLabel, behaviorLabel, contentX, contentW, y, rowH)
        val modeDescriptionH = modeDescriptionHeight(strings, contentW, rowH)
        ProceduralTextRenderer.drawWrapped(renderer, currentModeDescription(strings), contentX, y, contentW, PaletteIndices.SECONDARY)
        y += modeDescriptionH + gap

        when (modeKeys[modeIndex]) {
            "classic" -> {
                y = drawStepperRow(renderer, strings.durationLabel, "$classicDurationMinutes ${strings.minutes}", contentX, contentW, y, rowH)
            }
            "dual" -> {
                y = drawStepperRow(renderer, strings.bigBoxLabel, "$dualBigMinutes ${strings.minutes}", contentX, contentW, y, rowH)
                y = drawStepperRow(renderer, strings.smallLoopLabel, "$dualSmallSeconds ${strings.seconds}", contentX, contentW, y, rowH)
            }
            "dual.5" -> {
                y = drawStepperRow(renderer, strings.macroBlockLabel, "$dual5BigMinutes ${strings.minutes}", contentX, contentW, y, rowH)
                y = drawStepperRow(renderer, strings.mediumLoopLabel, "$dual5MidMinutes ${strings.minutes}", contentX, contentW, y, rowH)
                y = drawStepperRow(renderer, strings.microLoopLabel, "$dual5SmallSeconds ${strings.seconds}", contentX, contentW, y, rowH)
            }
            "sequence" -> {
                y = drawInputRow(renderer, strings.sequenceLabel, inputToString(sequenceInput), strings.sequencePlaceholder, contentX, contentW, y, rowH, focusedInput == FOCUS_SEQUENCE)
                y = drawStepperRow(renderer, strings.unitLabel, sequenceUnitLabels[sequenceUnitIndex], contentX, contentW, y, rowH)
            }
            "dual-sequence" -> {
                y = drawInputRow(renderer, strings.sequenceLabel, inputToString(sequenceInput), strings.sequencePlaceholder, contentX, contentW, y, rowH, focusedInput == FOCUS_SEQUENCE)
                y = drawStepperRow(renderer, strings.unitLabel, sequenceUnitLabels[sequenceUnitIndex], contentX, contentW, y, rowH)
                y = drawStepperRow(renderer, strings.smallLoopLabel, "$dualSequenceSmallSeconds ${strings.seconds}", contentX, contentW, y, rowH)
            }
            "calendar" -> {
                var blockIndex = 0
                while (blockIndex < calendarBlockCount) {
                    y = drawCalendarBlockRows(renderer, strings, blockIndex, contentX, contentW, y, rowH)
                    blockIndex++
                }
                val halfW = (contentW - gap) / 2f
                val calendarButtonsH = maxOf(
                    ScaledProceduralRenderer.measureButtonHeight(strings.addBlockLabel, halfW, rowH, allowTextStacking = true),
                    ScaledProceduralRenderer.measureButtonHeight(strings.deleteBlockLabel, halfW, rowH, allowTextStacking = true)
                )
                renderer.drawButton(strings.addBlockLabel, contentX, y, halfW, calendarButtonsH, isClicked = false, allowTextStacking = true)
                renderer.drawButton(strings.deleteBlockLabel, contentX + halfW + gap, y, halfW, calendarButtonsH, isClicked = calendarBlockCount > 1, allowTextStacking = true)
                y += calendarButtonsH + gap
            }
        }

        renderer.fillRectDither(playAreaStartX, 0f, playAreaStartX + playAreaW, headerCoverH, PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)
        ProceduralTextRenderer.drawHeading(renderer, strings.forgeTitle, contentX, titleY, titleW, PaletteIndices.PRIMARY, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        renderer.drawButton(strings.cancel, buttonX, cancelY, buttonW, cancelH, isClicked = false, allowTextStacking = true)
        renderer.drawLine(playAreaStartX + (U / 2).toFloat(), headerCoverH, playAreaStartX + playAreaW - (U / 2).toFloat(), headerCoverH, PaletteIndices.SECONDARY, 1f)

        val saveH = ScaledProceduralRenderer.measureButtonHeight(strings.saveTemplate, contentW, rowH, allowTextStacking = true)
        val saveY = playAreaH - saveH - (U * SAVE_GAP_CELLS).toFloat()
        if (isForgeValid()) {
            renderer.drawButton(strings.saveTemplate, contentX, saveY, contentW, saveH, isClicked = false, allowTextStacking = true)
        } else {
            renderer.drawRect(contentX, saveY, contentW, saveH, PaletteIndices.SECONDARY)
            val saveTextW = maxOf(U.toFloat(), contentW - U.toFloat())
            val saveTextH = ProceduralTextRenderer.measureWrappedHeight(strings.saveTemplate, saveTextW)
            ProceduralTextRenderer.drawWrapped(renderer, strings.saveTemplate, contentX + (U / 2).toFloat(), saveY + (saveH - saveTextH) / 2f, saveTextW, PaletteIndices.SECONDARY, alignment = ProceduralTextRenderer.ALIGN_CENTER)
        }

        RetroHudComponent.render(renderer, playX, playY, playW, playH)
    }

    override fun onInput(inputCode: Int) {
        val target = when (focusedInput) {
            FOCUS_NAME -> presetNameInput
            FOCUS_SEQUENCE -> sequenceInput
            FOCUS_CALENDAR_LABEL -> calendarLabelInputs[selectedCalendarBlock]
            else -> null
        } ?: return
        target.processPayload(inputCode)
        if (inputCode == EngineInputCodes.CMD_ENTER) {
            focusedInput = FOCUS_NONE
        }
    }

    override fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        val isPortrait = playX <= 0
        val playAreaStartX = playX.toFloat()
        val playAreaH = playH.toFloat()
        val logicalHeight = SceneManager.logicalHeight
        val inPlayArea = if (isPortrait) y < playAreaH else x >= playAreaStartX

        if (!isDragging && !inPlayArea) {
            onInput(x, y, action, playX, playY, playW, playH)
            return
        }

        when (action) {
            TouchAction.DOWN -> {
                if (!inPlayArea) return
                isDragging = true
                lastTouchY = y.toFloat()
                initialTouchX = x.toFloat()
                initialTouchY = y.toFloat()
                hasDragged = false
            }
            TouchAction.MOVE -> {
                if (isDragging) {
                    val deltaY = y - lastTouchY
                    if (abs(deltaY) > (U / 8).toFloat()) hasDragged = true
                    scrollY += deltaY
                    lastTouchY = y.toFloat()
                    scrollY = scrollY.coerceIn(contentMinScroll(playAreaH, logicalHeight), 0f)
                }
            }
            TouchAction.UP -> {
                if (isDragging) {
                    isDragging = false
                    val deltaX = x - initialTouchX
                    val deltaY = y - initialTouchY
                    if (inPlayArea && abs(deltaX) < (U / 2).toFloat() && abs(deltaY) < (U / 2).toFloat() && !hasDragged) {
                        onInput(x, y, TouchAction.UP, playX, playY, playW, playH)
                    }
                }
            }
            TouchAction.CANCEL -> {
                isDragging = false
                hasDragged = false
            }
        }
    }

    override fun onInput(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        if (RetroHudComponent.onTouchEvent(x, y, action, playX, playY, playW, playH)) return
        val isUp = action == TouchAction.UP
        if (!isUp) return
        val state = SceneManager.timerActions?.getUiState() ?: return
        val strings = getStrings(state.language)

        val isPortrait = playX <= 0
        val playAreaStartX = playX.toFloat()
        val playAreaW = playW.toFloat()
        val playAreaH = playH.toFloat()
        val padding = maxOf(U.toFloat(), playAreaW / CONTENT_PAD_RATIO_DEN)
        val contentX = playAreaStartX + padding
        val contentW = playAreaW - padding * 2f
        val logicalHeight = SceneManager.logicalHeight
        val safeTop = maxOf(logicalHeight / SAFE_TOP_RATIO_DEN, (U * TITLE_ROW_HEIGHT_CELLS).toFloat())
        val rowH = (U * CONTROL_HEIGHT_CELLS).toFloat()
        val gap = (U / 2).toFloat()
        val buttonW = maxOf((U * HEADER_BUTTON_WIDTH_CELLS).toFloat(), contentW * LABEL_COLUMN_RATIO_NUM / (LABEL_COLUMN_RATIO_DEN * 2f))
        val buttonX = playAreaStartX + playAreaW - padding - buttonW
        val titleW = maxOf(U.toFloat(), buttonX - contentX - gap)
        val titleH = ProceduralTextRenderer.measureHeadingHeight(strings.forgeTitle, titleW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val cancelH = ScaledProceduralRenderer.measureButtonHeight(strings.cancel, buttonW, rowH, allowTextStacking = true)
        val headerRowH = maxOf(rowH, titleH, cancelH)
        val cancelY = safeTop + (headerRowH - cancelH) / 2f
        val fx = x.toFloat()
        val rawFy = y.toFloat()
        val fy = rawFy - scrollY

        if (fx >= buttonX && fx <= buttonX + buttonW && rawFy >= cancelY && rawFy <= cancelY + cancelH) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            SceneManager.switchScene(TemplateCustomizerScene)
            return
        }

        val contentTopY = safeTop + headerRowH + gap + gap * TITLE_GAP_CELLS
        var y = contentTopY
        val presetNameValue = inputToString(presetNameInput)
        if (hitInputRow(fx, fy, strings.presetNameLabel, presetNameValue, strings.presetNamePlaceholder, y, contentX, contentW, rowH)) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                focusedInput = FOCUS_NAME
                SceneManager.triggerKeyboard()
                return
            }
            y = nextInputRowY(strings.presetNameLabel, presetNameValue, strings.presetNamePlaceholder, y, contentW, rowH)
            if (handleStepperTap(fx, fy, strings.engineStyleLabel, modeLabels[modeIndex], y, contentX, contentW, rowH, {
                    modeIndex = (modeIndex - 1 + modeKeys.size) % modeKeys.size
                    focusedInput = FOCUS_NONE
                }, {
                    modeIndex = (modeIndex + 1) % modeKeys.size
                    focusedInput = FOCUS_NONE
                })) return
            y = nextStepperRowY(strings.engineStyleLabel, modeLabels[modeIndex], y, contentW, rowH)
            val behaviorLabel = if (modeKeys[modeIndex] == "calendar") behaviorLabels[0] else behaviorLabels[behaviorIndex]
            if (modeKeys[modeIndex] != "calendar" && handleStepperTap(fx, fy, strings.completionBehaviorLabel, behaviorLabel, y, contentX, contentW, rowH, {
                    behaviorIndex = 0
                }, {
                    behaviorIndex = 1
                })) return
            y = nextStepperRowY(strings.completionBehaviorLabel, behaviorLabel, y, contentW, rowH)
            y += modeDescriptionHeight(strings, contentW, rowH) + gap

        when (modeKeys[modeIndex]) {
                "classic" -> {
                    val value = "$classicDurationMinutes ${strings.minutes}"
                    if (handleStepperTap(fx, fy, strings.durationLabel, value, y, contentX, contentW, rowH, {
                            classicDurationMinutes = (classicDurationMinutes - 1).coerceAtLeast(1)
                        }, {
                            classicDurationMinutes = (classicDurationMinutes + 1).coerceAtMost(120)
                        })) return
                }
                "dual" -> {
                    val bigValue = "$dualBigMinutes ${strings.minutes}"
                    if (handleStepperTap(fx, fy, strings.bigBoxLabel, bigValue, y, contentX, contentW, rowH, {
                            dualBigMinutes = (dualBigMinutes - 5).coerceAtLeast(5)
                        }, {
                            dualBigMinutes = (dualBigMinutes + 5).coerceAtMost(180)
                        })) return
                    y = nextStepperRowY(strings.bigBoxLabel, bigValue, y, contentW, rowH)
                    val smallValue = "$dualSmallSeconds ${strings.seconds}"
                    if (handleStepperTap(fx, fy, strings.smallLoopLabel, smallValue, y, contentX, contentW, rowH, {
                            dualSmallSeconds = (dualSmallSeconds - 10).coerceAtLeast(10)
                        }, {
                            dualSmallSeconds = (dualSmallSeconds + 10).coerceAtMost(300)
                        })) return
                }
                "dual.5" -> {
                    val bigValue = "$dual5BigMinutes ${strings.minutes}"
                    if (handleStepperTap(fx, fy, strings.macroBlockLabel, bigValue, y, contentX, contentW, rowH, {
                            dual5BigMinutes = (dual5BigMinutes - 5).coerceAtLeast(15)
                        }, {
                            dual5BigMinutes = (dual5BigMinutes + 5).coerceAtMost(180)
                        })) return
                    y = nextStepperRowY(strings.macroBlockLabel, bigValue, y, contentW, rowH)
                    val midValue = "$dual5MidMinutes ${strings.minutes}"
                    if (handleStepperTap(fx, fy, strings.mediumLoopLabel, midValue, y, contentX, contentW, rowH, {
                            dual5MidMinutes = (dual5MidMinutes - 1).coerceAtLeast(1)
                        }, {
                            dual5MidMinutes = (dual5MidMinutes + 1).coerceAtMost(60)
                        })) return
                    y = nextStepperRowY(strings.mediumLoopLabel, midValue, y, contentW, rowH)
                    val smallValue = "$dual5SmallSeconds ${strings.seconds}"
                    if (handleStepperTap(fx, fy, strings.microLoopLabel, smallValue, y, contentX, contentW, rowH, {
                            dual5SmallSeconds = (dual5SmallSeconds - 10).coerceAtLeast(10)
                        }, {
                            dual5SmallSeconds = (dual5SmallSeconds + 10).coerceAtMost(300)
                        })) return
                }
                "sequence" -> {
                    val sequenceValue = inputToString(sequenceInput)
                    if (hitInputRow(fx, fy, strings.sequenceLabel, sequenceValue, strings.sequencePlaceholder, y, contentX, contentW, rowH)) {
                        SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                        focusedInput = FOCUS_SEQUENCE
                        SceneManager.triggerKeyboard()
                        return
                    }
                    y = nextInputRowY(strings.sequenceLabel, sequenceValue, strings.sequencePlaceholder, y, contentW, rowH)
                    if (handleStepperTap(fx, fy, strings.unitLabel, sequenceUnitLabels[sequenceUnitIndex], y, contentX, contentW, rowH, {
                            sequenceUnitIndex = 0
                        }, {
                            sequenceUnitIndex = 1
                        })) return
                }
                "dual-sequence" -> {
                    val sequenceValue = inputToString(sequenceInput)
                    if (hitInputRow(fx, fy, strings.sequenceLabel, sequenceValue, strings.sequencePlaceholder, y, contentX, contentW, rowH)) {
                        SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                        focusedInput = FOCUS_SEQUENCE
                        SceneManager.triggerKeyboard()
                        return
                    }
                    y = nextInputRowY(strings.sequenceLabel, sequenceValue, strings.sequencePlaceholder, y, contentW, rowH)
                    if (handleStepperTap(fx, fy, strings.unitLabel, sequenceUnitLabels[sequenceUnitIndex], y, contentX, contentW, rowH, {
                            sequenceUnitIndex = 0
                        }, {
                            sequenceUnitIndex = 1
                        })) return
                    y = nextStepperRowY(strings.unitLabel, sequenceUnitLabels[sequenceUnitIndex], y, contentW, rowH)
                    val smallValue = "$dualSequenceSmallSeconds ${strings.seconds}"
                    if (handleStepperTap(fx, fy, strings.smallLoopLabel, smallValue, y, contentX, contentW, rowH, {
                            dualSequenceSmallSeconds = (dualSequenceSmallSeconds - 10).coerceAtLeast(10)
                        }, {
                            dualSequenceSmallSeconds = (dualSequenceSmallSeconds + 10).coerceAtMost(300)
                        })) return
                }
                "calendar" -> {
                    var blockIndex = 0
                    while (blockIndex < calendarBlockCount) {
                        if (handleCalendarBlockTap(fx, fy, strings, blockIndex, y, contentX, contentW, rowH)) return
                        y = calendarBlockRowsEndY(strings, blockIndex, y, contentW, rowH)
                        blockIndex++
                    }
                    val halfW = (contentW - gap) / 2f
                    val calendarButtonsH = maxOf(
                        ScaledProceduralRenderer.measureButtonHeight(strings.addBlockLabel, halfW, rowH, allowTextStacking = true),
                        ScaledProceduralRenderer.measureButtonHeight(strings.deleteBlockLabel, halfW, rowH, allowTextStacking = true)
                    )
                    if (fy >= y && fy <= y + calendarButtonsH) {
                        if (fx >= contentX && fx <= contentX + halfW && calendarBlockCount < MAX_CALENDAR_BLOCKS) {
                            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                            addCalendarBlock()
                            return
                        }
                        if (fx >= contentX + halfW + gap && fx <= contentX + contentW && calendarBlockCount > 1) {
                            SceneManager.performHapticFeedback(EngineHaptics.TICK)
                            deleteCalendarBlock(selectedCalendarBlock)
                            return
                        }
                    }
                }
            }

        val saveH = ScaledProceduralRenderer.measureButtonHeight(strings.saveTemplate, contentW, rowH, allowTextStacking = true)
        val saveY = playAreaH - saveH - (U * SAVE_GAP_CELLS).toFloat()
        if (fx >= contentX && fx <= contentX + contentW && rawFy >= saveY && rawFy <= saveY + saveH && isForgeValid()) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            SceneManager.timerActions?.upsertCustomPreset(buildPreset(state))
            SceneManager.switchScene(TemplateCustomizerScene)
            return
        }

        focusedInput = FOCUS_NONE
    }

    private fun contentMinScroll(playAreaH: Float, logicalHeight: Float): Float {
        val contentBottom = contentBottomY(logicalHeight)
        return (playAreaH - contentBottom).coerceAtMost(0f)
    }

    private fun contentBottomY(logicalHeight: Float): Float {
        val safeTop = maxOf(logicalHeight / SAFE_TOP_RATIO_DEN, (U * TITLE_ROW_HEIGHT_CELLS).toFloat())
        val rowH = (U * CONTROL_HEIGHT_CELLS).toFloat()
        val gap = (U / 2).toFloat()
        val playAreaW = cachedPlayAreaWidth()
        val padding = maxOf(U.toFloat(), playAreaW / CONTENT_PAD_RATIO_DEN)
        val contentW = playAreaW - padding * 2f
        val buttonW = maxOf((U * HEADER_BUTTON_WIDTH_CELLS).toFloat(), contentW * LABEL_COLUMN_RATIO_NUM / (LABEL_COLUMN_RATIO_DEN * 2f))
        val titleW = maxOf(U.toFloat(), contentW - buttonW - gap)
        val strings = getStrings(SceneManager.timerActions?.getUiState()?.language ?: "en")
        val headerRowH = maxOf(
            rowH,
            ProceduralTextRenderer.measureHeadingHeight(strings.forgeTitle, titleW, ScaledProceduralRenderer.TEXT_SCALE_HEADER),
            ScaledProceduralRenderer.measureButtonHeight(strings.cancel, buttonW, rowH, allowTextStacking = true)
        )
        val headerH = safeTop + headerRowH + gap
        var y = headerH + gap

        val presetNameValue = inputToString(presetNameInput)
        y = nextInputRowY(strings.presetNameLabel, presetNameValue, strings.presetNamePlaceholder, y, contentW, rowH)
        y = nextStepperRowY(strings.engineStyleLabel, modeLabels[modeIndex], y, contentW, rowH)
        val behaviorLabel = if (modeKeys[modeIndex] == "calendar") behaviorLabels[0] else behaviorLabels[behaviorIndex]
        y = nextStepperRowY(strings.completionBehaviorLabel, behaviorLabel, y, contentW, rowH)
        y += modeDescriptionHeight(strings, contentW, rowH) + gap

        when (modeKeys[modeIndex]) {
            "classic" -> {
                y = nextStepperRowY(strings.durationLabel, "$classicDurationMinutes ${strings.minutes}", y, contentW, rowH)
            }
            "dual" -> {
                y = nextStepperRowY(strings.bigBoxLabel, "$dualBigMinutes ${strings.minutes}", y, contentW, rowH)
                y = nextStepperRowY(strings.smallLoopLabel, "$dualSmallSeconds ${strings.seconds}", y, contentW, rowH)
            }
            "dual.5" -> {
                y = nextStepperRowY(strings.macroBlockLabel, "$dual5BigMinutes ${strings.minutes}", y, contentW, rowH)
                y = nextStepperRowY(strings.mediumLoopLabel, "$dual5MidMinutes ${strings.minutes}", y, contentW, rowH)
                y = nextStepperRowY(strings.microLoopLabel, "$dual5SmallSeconds ${strings.seconds}", y, contentW, rowH)
            }
            "sequence" -> {
                val sequenceValue = inputToString(sequenceInput)
                y = nextInputRowY(strings.sequenceLabel, sequenceValue, strings.sequencePlaceholder, y, contentW, rowH)
                y = nextStepperRowY(strings.unitLabel, sequenceUnitLabels[sequenceUnitIndex], y, contentW, rowH)
            }
            "dual-sequence" -> {
                val sequenceValue = inputToString(sequenceInput)
                y = nextInputRowY(strings.sequenceLabel, sequenceValue, strings.sequencePlaceholder, y, contentW, rowH)
                y = nextStepperRowY(strings.unitLabel, sequenceUnitLabels[sequenceUnitIndex], y, contentW, rowH)
                y = nextStepperRowY(strings.smallLoopLabel, "$dualSequenceSmallSeconds ${strings.seconds}", y, contentW, rowH)
            }
            "calendar" -> {
                var blockIndex = 0
                while (blockIndex < calendarBlockCount) {
                    y = calendarBlockRowsEndY(strings, blockIndex, y, contentW, rowH)
                    blockIndex++
                }
                val halfW = (contentW - gap) / 2f
                y += maxOf(
                    ScaledProceduralRenderer.measureButtonHeight(strings.addBlockLabel, halfW, rowH, allowTextStacking = true),
                    ScaledProceduralRenderer.measureButtonHeight(strings.deleteBlockLabel, halfW, rowH, allowTextStacking = true)
                ) + gap
            }
        }
        return y + (U * SAVE_GAP_CELLS).toFloat() + ScaledProceduralRenderer.measureButtonHeight(strings.saveTemplate, contentW, rowH, allowTextStacking = true)
    }

    private fun cachedPlayAreaWidth(): Float {
        return RetroHudComponent.playAreaWidth(SceneManager.logicalWidth, SceneManager.logicalHeight)
    }

    private fun currentModeDescription(strings: AppStrings): String {
        return when (modeKeys[modeIndex]) {
            "classic" -> strings.classicBehaviorDesc
            "dual" -> strings.dualBehaviorDesc
            "dual.5" -> strings.dual5BehaviorDesc
            "sequence" -> strings.sequenceBehaviorDesc
            "dual-sequence" -> strings.dualSequenceBehaviorDesc
            else -> strings.calendarBehaviorDesc
        }
    }

    private fun modeDescriptionHeight(strings: AppStrings, width: Float, minimumRowHeight: Float): Float {
        return maxOf(minimumRowHeight * 2f, ProceduralTextRenderer.measureWrappedHeight(currentModeDescription(strings), width))
    }

    private fun isForgeValid(): Boolean {
        if (inputToString(presetNameInput).trim().isEmpty()) return false
        return when (modeKeys[modeIndex]) {
            "sequence", "dual-sequence" -> parseSequenceValues().isNotEmpty()
            "calendar" -> calendarBlockCount > 0
            else -> true
        }
    }

    private fun buildPreset(state: EngineUiState): TimerPreset {
        val id = editingPresetId ?: nextCustomId(state)
        val name = inputToString(presetNameInput).trim()
        return when (modeKeys[modeIndex]) {
            "classic" -> TimerPreset(id = id, name = name, mode = "classic", sequence = intArrayOf(classicDurationMinutes * 60), alarmBehavior = behaviorKeys[behaviorIndex], description = "ＳＹＳ．ＣＬＡＳＳＩＣ　／／　ＣＵＳＴＯＭ")
            "dual" -> TimerPreset(id = id, name = name, mode = "dual", dualBigDuration = dualBigMinutes * 60, dualSmallDuration = dualSmallSeconds, alarmBehavior = behaviorKeys[behaviorIndex], description = "ＳＹＳ．ＤＵＡＬ　／／　ＣＵＳＴＯＭ")
            "dual.5" -> TimerPreset(id = id, name = name, mode = "dual.5", dualBigDuration = dual5BigMinutes * 60, dualMidDuration = dual5MidMinutes * 60, dualSmallDuration = dual5SmallSeconds, alarmBehavior = behaviorKeys[behaviorIndex], description = "ＳＹＳ．ＤＵＡＬ．５　／／　ＣＵＳＴＯＭ")
            "sequence" -> TimerPreset(id = id, name = name, mode = "sequence", sequence = parseSequenceValues(), alarmBehavior = behaviorKeys[behaviorIndex], description = "ＳＹＳ．ＳＥＱＵＥＮＣＥ　／／　ＣＵＳＴＯＭ")
            "dual-sequence" -> TimerPreset(id = id, name = name, mode = "dual-sequence", sequence = parseSequenceValues(), dualSmallDuration = dualSequenceSmallSeconds, alarmBehavior = behaviorKeys[behaviorIndex], description = "ＳＹＳ．ＤＵＡＬ‐ＳＥＱＵＥＮＣＥ　／／　ＣＵＳＴＯＭ")
            else -> {
                val strings = getStrings(state.language)
                val seq = IntArray(calendarBlockCount)
                val types = Array(calendarBlockCount) { "" }
                val labels = Array(calendarBlockCount) { "" }
                var i = 0
                while (i < calendarBlockCount) {
                    seq[i] = calendarDurationsMinutes[i] * 60
                    types[i] = if (calendarRelaxFlags[i]) "relax" else "focus"
                    val rawLabel = inputToString(calendarLabelInputs[i]).trim()
                    labels[i] = if (rawLabel.isEmpty()) {
                        if (calendarRelaxFlags[i]) strings.breakLabel else strings.focusLabel
                    } else {
                        rawLabel
                    }
                    i++
                }
                TimerPreset(id = id, name = name, mode = "calendar", sequence = seq, alarmBehavior = "alarm", description = "ＳＹＳ．ＣＡＬＥＮＤＡＲ　／／　ＣＵＳＴＯＭ　ＴＩＭＥＬＩＮＥ", sequenceTypes = types, sequenceLabels = labels)
            }
        }.normalized(logFailures = true)
    }

    private fun loadPreset(preset: TimerPreset) {
        editingPresetId = preset.id
        setInput(presetNameInput, preset.name)
        var modeSlot = 0
        while (modeSlot < modeKeys.size) {
            if (modeKeys[modeSlot] == preset.mode) {
                modeIndex = modeSlot
                break
            }
            modeSlot++
        }
        behaviorIndex = if (preset.alarmBehavior == "auto") 1 else 0
        when (preset.mode) {
            "classic" -> {
                classicDurationMinutes = ((preset.sequence.firstOrNull() ?: 1500) / 60).coerceAtLeast(1)
            }
            "dual" -> {
                dualBigMinutes = (preset.dualBigDuration / 60).coerceAtLeast(1)
                dualSmallSeconds = preset.dualSmallDuration.coerceAtLeast(1)
            }
            "dual.5" -> {
                dual5BigMinutes = (preset.dualBigDuration / 60).coerceAtLeast(1)
                dual5MidMinutes = (preset.dualMidDuration / 60).coerceAtLeast(1)
                dual5SmallSeconds = preset.dualSmallDuration.coerceAtLeast(1)
            }
            "sequence" -> setSequenceInputFromSeconds(preset.sequence)
            "dual-sequence" -> {
                setSequenceInputFromSeconds(preset.sequence)
                dualSequenceSmallSeconds = preset.dualSmallDuration.coerceAtLeast(1)
            }
            "calendar" -> {
                calendarBlockCount = preset.sequence.size.coerceIn(1, MAX_CALENDAR_BLOCKS)
                selectedCalendarBlock = 0
                var i = 0
                while (i < calendarBlockCount) {
                    val seconds = preset.sequence[i]
                    calendarDurationsMinutes[i] = (seconds / 60).coerceAtLeast(1)
                    calendarRelaxFlags[i] = preset.stageType(i) == "relax"
                    setInput(calendarLabelInputs[i], preset.stageLabel(i))
                    i++
                }
            }
        }
    }

    private fun setSequenceInputFromSeconds(sequence: IntArray) {
        var allMinutes = sequence.isNotEmpty()
        var i = 0
        while (i < sequence.size) {
            if (sequence[i] <= 0 || sequence[i] % 60 != 0) {
                allMinutes = false
                break
            }
            i++
        }
        sequenceUnitIndex = if (allMinutes) 0 else 1
        val builder = StringBuilder()
        i = 0
        while (i < sequence.size) {
            if (i > 0) builder.append(',')
            builder.append(if (sequenceUnitIndex == 0) sequence[i] / 60 else sequence[i])
            i++
        }
        setInput(sequenceInput, builder.toString())
    }

    private fun drawCalendarBlockRows(
        renderer: ScaledProceduralRenderer,
        strings: AppStrings,
        blockIndex: Int,
        x: Float,
        width: Float,
        y: Float,
        rowH: Float
    ): Float {
        val blockLabel = "${strings.calendarBlockLabel}　${FULLWIDTH_DIGITS[blockIndex + 1]}"
        var currentY = drawStepperRow(
            renderer,
            blockLabel,
            if (calendarRelaxFlags[blockIndex]) strings.calendarRelaxValue else strings.calendarFocusValue,
            x,
            width,
            y,
            rowH
        )
        currentY = drawInputRow(
            renderer,
            strings.calendarBlockNameLabel,
            inputToString(calendarLabelInputs[blockIndex]),
            if (calendarRelaxFlags[blockIndex]) strings.calendarRelaxThemePlaceholder else strings.calendarFocusThemePlaceholder,
            x,
            width,
            currentY,
            rowH,
            focusedInput == FOCUS_CALENDAR_LABEL && selectedCalendarBlock == blockIndex
        )
        return drawStepperRow(
            renderer,
            strings.durationLabel,
            "${calendarDurationsMinutes[blockIndex]} ${strings.minutes}",
            x,
            width,
            currentY,
            rowH
        )
    }

    private fun handleCalendarBlockTap(
        fx: Float,
        fy: Float,
        strings: AppStrings,
        blockIndex: Int,
        y: Float,
        x: Float,
        width: Float,
        rowH: Float
    ): Boolean {
        val blockLabel = "${strings.calendarBlockLabel}　${FULLWIDTH_DIGITS[blockIndex + 1]}"
        val blockValue = if (calendarRelaxFlags[blockIndex]) strings.calendarRelaxValue else strings.calendarFocusValue
        var currentY = y
        if (handleStepperTap(fx, fy, blockLabel, blockValue, currentY, x, width, rowH, {
                selectedCalendarBlock = blockIndex
                calendarRelaxFlags[blockIndex] = false
                focusedInput = FOCUS_NONE
            }, {
                selectedCalendarBlock = blockIndex
                calendarRelaxFlags[blockIndex] = true
                focusedInput = FOCUS_NONE
            })) return true
        currentY = nextStepperRowY(blockLabel, blockValue, currentY, width, rowH)
        val labelValue = inputToString(calendarLabelInputs[blockIndex])
        val labelPlaceholder = if (calendarRelaxFlags[blockIndex]) strings.calendarRelaxThemePlaceholder else strings.calendarFocusThemePlaceholder
        if (hitInputRow(fx, fy, strings.calendarBlockNameLabel, labelValue, labelPlaceholder, currentY, x, width, rowH)) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            selectedCalendarBlock = blockIndex
            focusedInput = FOCUS_CALENDAR_LABEL
            SceneManager.triggerKeyboard()
            return true
        }
        currentY = nextInputRowY(strings.calendarBlockNameLabel, labelValue, labelPlaceholder, currentY, width, rowH)
        val durationValue = "${calendarDurationsMinutes[blockIndex]} ${strings.minutes}"
        return handleStepperTap(fx, fy, strings.durationLabel, durationValue, currentY, x, width, rowH, {
            selectedCalendarBlock = blockIndex
            calendarDurationsMinutes[blockIndex] = (calendarDurationsMinutes[blockIndex] - 1).coerceAtLeast(1)
            focusedInput = FOCUS_NONE
        }, {
            selectedCalendarBlock = blockIndex
            calendarDurationsMinutes[blockIndex] = (calendarDurationsMinutes[blockIndex] + 1).coerceAtMost(120)
            focusedInput = FOCUS_NONE
        })
    }

    private fun calendarBlockRowsEndY(strings: AppStrings, blockIndex: Int, y: Float, width: Float, rowH: Float): Float {
        val blockLabel = "${strings.calendarBlockLabel}　${FULLWIDTH_DIGITS[blockIndex + 1]}"
        val blockValue = if (calendarRelaxFlags[blockIndex]) strings.calendarRelaxValue else strings.calendarFocusValue
        var currentY = nextStepperRowY(blockLabel, blockValue, y, width, rowH)
        val labelValue = inputToString(calendarLabelInputs[blockIndex])
        val labelPlaceholder = if (calendarRelaxFlags[blockIndex]) strings.calendarRelaxThemePlaceholder else strings.calendarFocusThemePlaceholder
        currentY = nextInputRowY(strings.calendarBlockNameLabel, labelValue, labelPlaceholder, currentY, width, rowH)
        return nextStepperRowY(strings.durationLabel, "${calendarDurationsMinutes[blockIndex]} ${strings.minutes}", currentY, width, rowH)
    }

    private fun nextCustomId(state: EngineUiState): String {
        var best = 0
        var i = 0
        while (i < state.presets.size) {
            val id = state.presets[i].id
            if (id.startsWith("custom_")) {
                val value = id.substring(7).toIntOrNull()
                if (value != null && value > best) best = value
            }
            i++
        }
        return "custom_${best + 1}"
    }

    private fun parseSequenceValues(): IntArray {
        val raw = inputToString(sequenceInput)
        val parts = raw.split(',')
        val temp = IntArray(parts.size)
        var count = 0
        var i = 0
        while (i < parts.size) {
            val value = parts[i].trim().toIntOrNull()
            if (value != null && value > 0) {
                temp[count] = if (sequenceUnitIndex == 0) value * 60 else value
                count++
            }
            i++
        }
        return temp.copyOf(count)
    }

    private fun addCalendarBlock() {
        if (calendarBlockCount >= MAX_CALENDAR_BLOCKS) return
        val idx = calendarBlockCount
        clearInput(calendarLabelInputs[idx])
        val language = SceneManager.timerActions?.getUiState()?.language ?: "en"
        setInput(calendarLabelInputs[idx], getStrings(language).focusLabel)
        calendarDurationsMinutes[idx] = 25
        calendarRelaxFlags[idx] = false
        calendarBlockCount++
        selectedCalendarBlock = idx
    }

    private fun deleteCalendarBlock(index: Int) {
        if (calendarBlockCount <= 1) return
        var i = index
        while (i < calendarBlockCount - 1) {
            clearInput(calendarLabelInputs[i])
            setInput(calendarLabelInputs[i], inputToString(calendarLabelInputs[i + 1]))
            calendarDurationsMinutes[i] = calendarDurationsMinutes[i + 1]
            calendarRelaxFlags[i] = calendarRelaxFlags[i + 1]
            i++
        }
        calendarBlockCount--
        clearInput(calendarLabelInputs[calendarBlockCount])
        calendarDurationsMinutes[calendarBlockCount] = 25
        calendarRelaxFlags[calendarBlockCount] = false
        if (selectedCalendarBlock >= calendarBlockCount) {
            selectedCalendarBlock = calendarBlockCount - 1
        }
    }

    private fun drawInputRow(
        renderer: ScaledProceduralRenderer,
        label: String,
        value: String,
        placeholder: String,
        x: Float,
        width: Float,
        y: Float,
        rowH: Float,
        isFocused: Boolean
    ): Float {
        val display = inputDisplay(value, placeholder)
        val controlX = controlXForLabel(label, x, width)
        val controlW = controlWidthForLabel(label, width)
        val fieldY = controlYForLabel(label, y, width, rowH)
        val fieldH = inputFieldHeightForDisplay(label, display, width, rowH)
        val labelW = labelTextWidth(label, width)
        val labelH = ProceduralTextRenderer.measureWrappedHeight(label, labelW)
        val labelY = labelYForRow(label, y, fieldY, fieldH, labelH, width)
        ProceduralTextRenderer.drawWrapped(renderer, label, x, labelY, labelW, PaletteIndices.PRIMARY)
        renderer.drawRect(controlX, fieldY, controlW, fieldH, if (isFocused) PaletteIndices.PRIMARY else PaletteIndices.SECONDARY)
        ProceduralTextRenderer.drawWrapped(renderer, display, controlX + (U / 2).toFloat(), fieldY + (U / 2).toFloat(), maxOf(U.toFloat(), controlW - U.toFloat()), if (value.isEmpty()) PaletteIndices.SECONDARY else PaletteIndices.PRIMARY)
        return fieldY + fieldH + (U / 2).toFloat()
    }

    private fun drawStepperRow(
        renderer: ScaledProceduralRenderer,
        label: String,
        value: String,
        x: Float,
        width: Float,
        y: Float,
        rowH: Float
    ): Float {
        val controlX = controlXForLabel(label, x, width)
        val controlW = controlWidthForLabel(label, width)
        val fieldY = controlYForLabel(label, y, width, rowH)
        val fieldH = stepperFieldHeight(label, value, width, rowH)
        val labelW = labelTextWidth(label, width)
        val labelH = ProceduralTextRenderer.measureWrappedHeight(label, labelW)
        val labelY = labelYForRow(label, y, fieldY, fieldH, labelH, width)
        ProceduralTextRenderer.drawWrapped(renderer, label, x, labelY, labelW, PaletteIndices.PRIMARY)
        renderer.drawButton("＜", controlX, fieldY, rowH, fieldH, isClicked = false)
        renderer.drawButton("＞", controlX + controlW - rowH, fieldY, rowH, fieldH, isClicked = false)
        ProceduralTextRenderer.drawWrapped(renderer, value, controlX + rowH + (U / 2).toFloat(), fieldY + (U / 2).toFloat(), stepperTextWidth(controlW, rowH), PaletteIndices.PRIMARY)
        return fieldY + fieldH + (U / 2).toFloat()
    }

    private fun hitInputRow(fx: Float, fy: Float, label: String, value: String, placeholder: String, y: Float, x: Float, width: Float, rowH: Float): Boolean {
        val controlX = controlXForLabel(label, x, width)
        val controlW = controlWidthForLabel(label, width)
        val fieldY = controlYForLabel(label, y, width, rowH)
        val fieldH = inputFieldHeight(label, value, placeholder, width, rowH)
        return fx >= controlX && fx <= controlX + controlW && fy >= fieldY && fy <= fieldY + fieldH
    }

    private fun handleStepperTap(
        fx: Float,
        fy: Float,
        label: String,
        value: String,
        y: Float,
        x: Float,
        width: Float,
        rowH: Float,
        onLeft: () -> Unit,
        onRight: () -> Unit
    ): Boolean {
        val fieldY = controlYForLabel(label, y, width, rowH)
        val controlX = controlXForLabel(label, x, width)
        val controlW = controlWidthForLabel(label, width)
        val fieldH = stepperFieldHeight(label, value, width, rowH)
        if (fy < fieldY || fy > fieldY + fieldH) return false
        if (fx >= controlX && fx <= controlX + rowH) {
            SceneManager.performHapticFeedback(EngineHaptics.TICK)
            onLeft()
            return true
        }
        if (fx >= controlX + controlW - rowH && fx <= controlX + controlW) {
            SceneManager.performHapticFeedback(EngineHaptics.TICK)
            onRight()
            return true
        }
        return false
    }

    private fun nextInputRowY(label: String, value: String, placeholder: String, y: Float, width: Float, rowH: Float): Float {
        val fieldY = controlYForLabel(label, y, width, rowH)
        return fieldY + inputFieldHeight(label, value, placeholder, width, rowH) + (U / 2).toFloat()
    }

    private fun nextStepperRowY(label: String, value: String, y: Float, width: Float, rowH: Float): Float {
        val fieldY = controlYForLabel(label, y, width, rowH)
        return fieldY + stepperFieldHeight(label, value, width, rowH) + (U / 2).toFloat()
    }

    private fun controlYForLabel(label: String, y: Float, width: Float, rowH: Float): Float {
        return if (labelNeedsStack(label, width)) y + ProceduralTextRenderer.measureWrappedHeight(label, width) + (U / 2).toFloat() else y
    }

    private fun controlXForLabel(label: String, x: Float, width: Float): Float {
        return if (labelNeedsStack(label, width)) x else x + labelColumnWidth(width) + (U / 2).toFloat()
    }

    private fun controlWidthForLabel(label: String, width: Float): Float {
        return if (labelNeedsStack(label, width)) width else width - labelColumnWidth(width) - (U / 2).toFloat()
    }

    private fun labelYForRow(label: String, y: Float, fieldY: Float, fieldH: Float, labelH: Float, width: Float): Float {
        return if (labelNeedsStack(label, width)) y else fieldY + (fieldH - labelH) / 2f
    }

    private fun labelNeedsStack(label: String, width: Float): Boolean {
        return ScaledProceduralRenderer.measureTextWidth(label) > labelColumnWidth(width)
    }

    private fun labelColumnWidth(width: Float): Float {
        return width * LABEL_COLUMN_RATIO_NUM / LABEL_COLUMN_RATIO_DEN
    }

    private fun labelTextWidth(label: String, width: Float): Float {
        return if (labelNeedsStack(label, width)) width else labelColumnWidth(width)
    }

    private fun inputDisplay(value: String, placeholder: String): String {
        return if (value.isEmpty()) "＞　$placeholder　＿" else value
    }

    private fun inputFieldHeight(label: String, value: String, placeholder: String, width: Float, rowH: Float): Float {
        return inputFieldHeightForDisplay(label, inputDisplay(value, placeholder), width, rowH)
    }

    private fun inputFieldHeightForDisplay(label: String, display: String, width: Float, rowH: Float): Float {
        val controlW = controlWidthForLabel(label, width)
        val textH = ProceduralTextRenderer.measureWrappedHeight(display, maxOf(U.toFloat(), controlW - U.toFloat()))
        return maxOf(rowH, textH + U.toFloat())
    }

    private fun stepperFieldHeight(label: String, value: String, width: Float, rowH: Float): Float {
        val controlW = controlWidthForLabel(label, width)
        val textH = ProceduralTextRenderer.measureWrappedHeight(value, stepperTextWidth(controlW, rowH))
        return maxOf(rowH, textH + U.toFloat())
    }

    private fun stepperTextWidth(controlW: Float, arrowW: Float): Float {
        return maxOf(U.toFloat(), controlW - arrowW * 2f - U.toFloat())
    }

    private fun clearInput(input: FixedInputContainer) {
        while (input.length > 0) {
            input.processPayload(EngineInputCodes.CMD_BACKSPACE)
        }
    }

    private fun setInput(input: FixedInputContainer, text: String) {
        clearInput(input)
        var i = 0
        while (i < text.length) {
            input.processPayload(text[i].code)
            i++
        }
    }

    private fun inputToString(input: FixedInputContainer): String {
        val builder = StringBuilder()
        var i = 0
        while (i < input.length) {
            val cp = input.codePoints[i]
            if (cp <= 0xFFFF) {
                builder.append(cp.toChar())
            }
            i++
        }
        return builder.toString()
    }

}

//  SETTINGS SCENE
// ════════════════════════════════════════════════════════════════════
object SettingsScene : Scene {
    private const val LABEL_RATIO_NUM = 2f
    private const val LABEL_RATIO_DEN = 5f
    private const val AUDIO_STEPS = 10

    private val languages = arrayOf("en", "zh", "ja")

    private val themes = arrayOf("reimu", "marisa", "alice", "kaguya")

    private var playAreaStartX = 0f
    private var playAreaW = 0f
    private var playAreaH = 0f
    private var safeTop = 0f
    private var currentY = 0f
    private var rowH = 0f
    private var spacing = 0f
    private var usableWidth = 0f
    private var labelX = 0f
    private var arrowW = 0f
    private var ctrlX = 0f
    private var ctrlY = 0f
    private var ctrlW = 0f
    private var scrollY = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasDragged = false

    override fun onEnter(payload: Any?) {
        scrollY = 0f
        isDragging = false
        hasDragged = false
    }

    override fun onExit() {
        isDragging = false
    }
    override fun update(dt: Float) {}

    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val state = SceneManager.timerActions?.getUiState() ?: return
        val strings = getStrings(state.language)
        SceneManager.logStringsAfterLanguageChange("SettingsScene", state.language)
        EngineThemes.getColors(state.appTheme, state.isBreak)

        renderer.drawRect(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG)
        beginSettingsLayout(logicalWidth, logicalHeight)
        clampSettingsScroll(state, strings)
        renderer.fillRectDither(playAreaStartX, 0f, playAreaStartX + playAreaW, playAreaH, PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)

        currentY = safeTop + scrollY
        drawSettingsRows(renderer, state, strings)

        RetroHudComponent.render(renderer, playX, playY, playW, playH)

    }

    override fun onInput(inputCode: Int) {}

    override fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        val isPortrait = playX <= 0
        val inPlayArea = if (isPortrait) y < playH else x >= playX

        if (!isDragging && !inPlayArea) {
            onInput(x, y, action, playX, playY, playW, playH)
            return
        }

        when (action) {
            TouchAction.DOWN -> {
                if (!inPlayArea) return
                isDragging = true
                lastTouchY = y.toFloat()
                initialTouchX = x.toFloat()
                initialTouchY = y.toFloat()
                hasDragged = false
            }
            TouchAction.MOVE -> {
                if (isDragging) {
                    val deltaY = y - lastTouchY
                    if (abs(deltaY) > (U / 4).toFloat()) {
                        hasDragged = true
                    }
                    scrollY += deltaY
                    lastTouchY = y.toFloat()
                    val state = SceneManager.timerActions?.getUiState() ?: return
                    val strings = getStrings(state.language)
                    val logicalWidth = SceneManager.logicalWidth
                    val logicalHeight = SceneManager.logicalHeight
                    beginSettingsLayout(logicalWidth, logicalHeight)
                    clampSettingsScroll(state, strings)
                }
            }
            TouchAction.UP -> {
                if (isDragging) {
                    isDragging = false
                    val deltaX = x - initialTouchX
                    val deltaY = y - initialTouchY
                    if (inPlayArea && abs(deltaX) < (U / 2).toFloat() && abs(deltaY) < (U / 2).toFloat() && !hasDragged) {
                        onInput(x, y, TouchAction.UP, playX, playY, playW, playH)
                    }
                }
            }
            TouchAction.CANCEL -> {
                isDragging = false
                hasDragged = false
            }
        }
    }

    override fun onInput(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        if (RetroHudComponent.onTouchEvent(x, y, action, playX, playY, playW, playH)) return
        val isUp = action == TouchAction.UP
        if (!isUp) return

        val state = SceneManager.timerActions?.getUiState() ?: return
        val strings = getStrings(state.language)
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        beginSettingsLayout(logicalWidth, logicalHeight)
        clampSettingsScroll(state, strings)
        currentY = safeTop + scrollY
        val fx = x.toFloat()
        val fy = y.toFloat()

        layoutRow(null, strings.languageLabel)
        if (fy >= ctrlY && fy <= ctrlY + rowH) {
            val idx = indexOf(languages, state.language)
            if (fx >= ctrlX && fx <= ctrlX + arrowW) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                val prev = (idx - 1 + languages.size) % languages.size
                SceneManager.timerActions?.updateLanguage(languages[prev])
                SceneManager.markLanguageChanged()
                return
            } else if (fx >= ctrlX + ctrlW - arrowW && fx <= ctrlX + ctrlW) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                val next = (idx + 1) % languages.size
                SceneManager.timerActions?.updateLanguage(languages[next])
                SceneManager.markLanguageChanged()
                return
            }
        }

        layoutRow(null, strings.volumeLabel)
        if (fy >= ctrlY && fy <= ctrlY + rowH) {
            val currentVol = state.volume
            if (fx >= ctrlX && fx <= ctrlX + arrowW) {
                SceneManager.performHapticFeedback(EngineHaptics.TICK)
                SceneManager.timerActions?.updateVolume((currentVol - (1f / AUDIO_STEPS)).coerceAtLeast(0f))
                return
            } else if (fx >= ctrlX + ctrlW - arrowW && fx <= ctrlX + ctrlW) {
                SceneManager.performHapticFeedback(EngineHaptics.TICK)
                SceneManager.timerActions?.updateVolume((currentVol + (1f / AUDIO_STEPS)).coerceAtMost(1f))
                return
            }
        }

        layoutRow(null, strings.focusToneLabel)
        if (fy >= ctrlY && fy <= ctrlY + rowH) {
            val songs = SongCatalog.all
            val idx = SongCatalog.indexOf(state.selectedFocusSound)
            if (fx >= ctrlX && fx <= ctrlX + arrowW) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                val prev = (idx - 1 + songs.size) % songs.size
                SceneManager.timerActions?.updateFocusSound(songs[prev].id)
                return
            } else if (fx >= ctrlX + ctrlW - arrowW && fx <= ctrlX + ctrlW) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                val next = (idx + 1) % songs.size
                SceneManager.timerActions?.updateFocusSound(songs[next].id)
                return
            }
        }

        layoutRow(null, strings.relaxToneLabel)
        if (fy >= ctrlY && fy <= ctrlY + rowH) {
            val songs = SongCatalog.all
            val idx = SongCatalog.indexOf(state.selectedRelaxSound)
            if (fx >= ctrlX && fx <= ctrlX + arrowW) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                val prev = (idx - 1 + songs.size) % songs.size
                SceneManager.timerActions?.updateRelaxSound(songs[prev].id)
                return
            } else if (fx >= ctrlX + ctrlW - arrowW && fx <= ctrlX + ctrlW) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                val next = (idx + 1) % songs.size
                SceneManager.timerActions?.updateRelaxSound(songs[next].id)
                return
            }
        }

        layoutRow(null, strings.testFocusLabel)
        if (fx >= ctrlX && fx <= ctrlX + ctrlW && fy >= ctrlY && fy <= ctrlY + rowH) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            SceneManager.timerActions?.previewSound(state.selectedFocusSound)
            return
        }

        layoutRow(null, strings.testRelaxLabel)
        if (fx >= ctrlX && fx <= ctrlX + ctrlW && fy >= ctrlY && fy <= ctrlY + rowH) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            SceneManager.timerActions?.previewSound(state.selectedRelaxSound)
            return
        }

        layoutRow(null, strings.strictLabel)
        if (fx >= ctrlX && fx <= ctrlX + ctrlW && fy >= ctrlY && fy <= ctrlY + rowH) {
            SceneManager.performHapticFeedback(EngineHaptics.TICK)
            SceneManager.timerActions?.updateSettings(!state.strictMode, state.tickEnabled, state.selectedFocusSound, state.vibeIntensity)
            return
        }

        layoutRow(null, strings.ticks)
        if (fx >= ctrlX && fx <= ctrlX + ctrlW && fy >= ctrlY && fy <= ctrlY + rowH) {
            SceneManager.performHapticFeedback(EngineHaptics.TICK)
            SceneManager.timerActions?.updateSettings(state.strictMode, !state.tickEnabled, state.selectedFocusSound, state.vibeIntensity)
            return
        }

        layoutRow(null, strings.vibe)
        if (fy >= ctrlY && fy <= ctrlY + rowH) {
            if (fx >= ctrlX && fx <= ctrlX + arrowW) {
                SceneManager.performHapticFeedback(EngineHaptics.TICK)
                val nextVibe = (state.vibeIntensity - (1f / AUDIO_STEPS)).coerceAtLeast(0f)
                SceneManager.timerActions?.updateSettings(state.strictMode, state.tickEnabled, state.selectedFocusSound, nextVibe)
                return
            } else if (fx >= ctrlX + ctrlW - arrowW && fx <= ctrlX + ctrlW) {
                SceneManager.performHapticFeedback(EngineHaptics.TICK)
                val nextVibe = (state.vibeIntensity + (1f / AUDIO_STEPS)).coerceAtMost(1f)
                SceneManager.timerActions?.updateSettings(state.strictMode, state.tickEnabled, state.selectedFocusSound, nextVibe)
                return
            }
        }

        layoutRow(null, strings.themeLabel)
        if (fy >= ctrlY && fy <= ctrlY + rowH) {
            val idx = indexOf(themes, state.appTheme)
            if (fx >= ctrlX && fx <= ctrlX + arrowW) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                val prev = (idx - 1 + themes.size) % themes.size
                SceneManager.timerActions?.updateTheme(themes[prev])
                return
            } else if (fx >= ctrlX + ctrlW - arrowW && fx <= ctrlX + ctrlW) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                val next = (idx + 1) % themes.size
                SceneManager.timerActions?.updateTheme(themes[next])
                return
            }
        }

        layoutRow(null, strings.precisionLabel)
        if (!state.isExactAlarmPermitted && fx >= ctrlX && fx <= ctrlX + ctrlW && fy >= ctrlY && fy <= ctrlY + rowH) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            SceneManager.timerActions?.requestExactAlarmPermission()
        }

        // Visuals section toggles. Header row is display-only.
        layoutRow(null, strings.visualsHeader)
        // No-op for the header row.

        layoutRow(null, strings.demosceneLabel)
        if (fx >= ctrlX && fx <= ctrlX + ctrlW && fy >= ctrlY && fy <= ctrlY + rowH) {
            SceneManager.performHapticFeedback(EngineHaptics.TICK)
            VisualsStateHolder.demosceneEffectsEnabled = !VisualsStateHolder.demosceneEffectsEnabled
            return
        }

        layoutRow(null, strings.nebulaLabel)
        if (fx >= ctrlX && fx <= ctrlX + ctrlW && fy >= ctrlY && fy <= ctrlY + rowH) {
            SceneManager.performHapticFeedback(EngineHaptics.TICK)
            VisualsStateHolder.backgroundNebulaEnabled = !VisualsStateHolder.backgroundNebulaEnabled
            return
        }
    }

    private fun beginSettingsLayout(logicalWidth: Float, logicalHeight: Float) {
        val leftHudW = RetroHudComponent.playAreaStartX(logicalWidth, logicalHeight)
        val useLeftHud = RetroHudComponent.usesLeftHud(logicalWidth, logicalHeight)
        val isPortrait = !useLeftHud
        playAreaStartX = if (isPortrait) 0f else leftHudW
        playAreaW = if (isPortrait) logicalWidth else logicalWidth - playAreaStartX
        playAreaH = RetroHudComponent.playAreaHeight(logicalWidth, logicalHeight)

        val padding = maxOf(U.toFloat(), playAreaW / (U + (U / 4)))
        usableWidth = playAreaW - padding * 2f
        labelX = playAreaStartX + padding
        safeTop = maxOf(logicalHeight / (U - (U / 4)), (U * 2).toFloat())
        rowH = maxOf(playAreaH * 3f / 25f, (U * 2).toFloat())
        spacing = (U / 4).toFloat()
        currentY = safeTop
        arrowW = minOf(rowH, (U * 2).toFloat())
    }

    private fun languageName(strings: AppStrings, index: Int): String {
        return when (index) {
            1 -> strings.languageChinese
            2 -> strings.languageJapanese
            else -> strings.languageEnglish
        }
    }

    private fun themeName(strings: AppStrings, index: Int): String {
        return when (index) {
            1 -> strings.themeMarisa
            2 -> strings.themeAlice
            3 -> strings.themeKaguya
            else -> strings.themeReimu
        }
    }

    private fun layoutRow(renderer: ScaledProceduralRenderer?, labelText: String) {
        val requiredLabelWidth = ScaledProceduralRenderer.measureTextWidth(labelText)
        val labelColumnW = usableWidth * LABEL_RATIO_NUM / LABEL_RATIO_DEN
        val sideBySide = requiredLabelWidth <= labelColumnW
        val labelControlPadding = (U / 2).toFloat()
        val maxRowHeight: Float

        if (sideBySide) {
            ctrlX = labelX + labelColumnW
            ctrlW = usableWidth - labelColumnW
            val labelTextH = ProceduralTextRenderer.measureWrappedHeight(labelText, labelColumnW)
            maxRowHeight = maxOf(labelTextH, rowH)
            ctrlY = currentY + (maxRowHeight - rowH) / 2f
            if (renderer != null) {
                val labelY = currentY + (maxRowHeight - labelTextH) / 2f
                ProceduralTextRenderer.drawWrapped(renderer, labelText, labelX, labelY, labelColumnW, PaletteIndices.PRIMARY)
            }
        } else {
            ctrlX = labelX
            ctrlW = usableWidth
            val labelTextH = ProceduralTextRenderer.measureWrappedHeight(labelText, usableWidth)
            ctrlY = currentY + labelTextH + labelControlPadding
            maxRowHeight = labelTextH + labelControlPadding + rowH
            if (renderer != null) {
                ProceduralTextRenderer.drawWrapped(renderer, labelText, labelX, currentY, usableWidth, PaletteIndices.PRIMARY)
            }
        }
        currentY += maxRowHeight + spacing
    }

    private fun drawSettingsRows(renderer: ScaledProceduralRenderer, state: EngineUiState, strings: AppStrings) {
        layoutRow(renderer, strings.languageLabel)
        drawStepper(renderer, languageName(strings, indexOf(languages, state.language)), ctrlX, ctrlY, rowH, ctrlW, PaletteIndices.PRIMARY, PaletteIndices.SECONDARY)

        layoutRow(renderer, strings.volumeLabel)
        drawBarStepper(renderer, (state.volume * AUDIO_STEPS).toInt().coerceIn(0, AUDIO_STEPS), AUDIO_STEPS, ctrlX, ctrlY, rowH, ctrlW, PaletteIndices.PRIMARY, PaletteIndices.SECONDARY)

        layoutRow(renderer, strings.focusToneLabel)
        drawStepper(renderer, SongCatalog.all[SongCatalog.indexOf(state.selectedFocusSound)].displayTitle, ctrlX, ctrlY, rowH, ctrlW, PaletteIndices.PRIMARY, PaletteIndices.SECONDARY, toneText = true)

        layoutRow(renderer, strings.relaxToneLabel)
        drawStepper(renderer, SongCatalog.all[SongCatalog.indexOf(state.selectedRelaxSound)].displayTitle, ctrlX, ctrlY, rowH, ctrlW, PaletteIndices.PRIMARY, PaletteIndices.SECONDARY, toneText = true)

        layoutRow(renderer, strings.testFocusLabel)
        renderer.drawButton(strings.testFocusLabel, ctrlX, ctrlY, ctrlW, rowH, isClicked = false)

        layoutRow(renderer, strings.testRelaxLabel)
        renderer.drawButton(strings.testRelaxLabel, ctrlX, ctrlY, ctrlW, rowH, isClicked = false)

        layoutRow(renderer, strings.strictLabel)
        renderer.drawButton(if (state.strictMode) strings.on else strings.off, ctrlX, ctrlY, ctrlW, rowH, isClicked = state.strictMode)

        layoutRow(renderer, strings.ticks)
        renderer.drawButton(if (state.tickEnabled) strings.on else strings.off, ctrlX, ctrlY, ctrlW, rowH, isClicked = state.tickEnabled)

        layoutRow(renderer, strings.vibe)
        drawBarStepper(renderer, (state.vibeIntensity * AUDIO_STEPS).toInt().coerceIn(0, AUDIO_STEPS), AUDIO_STEPS, ctrlX, ctrlY, rowH, ctrlW, PaletteIndices.PRIMARY, PaletteIndices.SECONDARY)

        layoutRow(renderer, strings.themeLabel)
        drawStepper(renderer, themeName(strings, indexOf(themes, state.appTheme)), ctrlX, ctrlY, rowH, ctrlW, PaletteIndices.PRIMARY, PaletteIndices.SECONDARY)

        layoutRow(renderer, strings.precisionLabel)
        if (state.isExactAlarmPermitted) {
            val txt = strings.secureLabel
            val textW = maxOf(U.toFloat(), ctrlW - U.toFloat())
            val textH = ProceduralTextRenderer.measureWrappedHeight(txt, textW)
            ProceduralTextRenderer.drawWrapped(renderer, txt, ctrlX + (U / 2).toFloat(), ctrlY + (rowH - textH) / 2f, textW, PaletteIndices.PRIMARY)
        } else {
            renderer.drawButton(strings.authorizeLabel, ctrlX, ctrlY, ctrlW, rowH, isClicked = false)
        }

        // Visuals section (demoscene + nebula toggles). Toggles write to
        // VisualsStateHolder, which the magic circle renderer reads each frame.
        // No platform persistence for now — toggles are session-scoped.
        layoutRow(renderer, strings.visualsHeader)
        val visualsHeaderH = ProceduralTextRenderer.measureWrappedHeight(strings.visualsHeader, usableWidth)
        ProceduralTextRenderer.drawWrapped(renderer, strings.visualsHeader, labelX, ctrlY + (rowH - visualsHeaderH) / 2f, usableWidth, PaletteIndices.SECONDARY)

        layoutRow(renderer, strings.demosceneLabel)
        renderer.drawButton(
            if (VisualsStateHolder.demosceneEffectsEnabled) strings.on else strings.off,
            ctrlX, ctrlY, ctrlW, rowH,
            isClicked = VisualsStateHolder.demosceneEffectsEnabled
        )

        layoutRow(renderer, strings.nebulaLabel)
        renderer.drawButton(
            if (VisualsStateHolder.backgroundNebulaEnabled) strings.on else strings.off,
            ctrlX, ctrlY, ctrlW, rowH,
            isClicked = VisualsStateHolder.backgroundNebulaEnabled
        )
    }

    private fun clampSettingsScroll(state: EngineUiState, strings: AppStrings) {
        currentY = safeTop
        measureSettingsRows(state, strings)
        val contentBottom = currentY + U.toFloat()
        val minScroll = (playAreaH - contentBottom).coerceAtMost(0f)
        scrollY = scrollY.coerceIn(minScroll, 0f)
    }

    private fun measureSettingsRows(state: EngineUiState, strings: AppStrings) {
        layoutRow(null, strings.languageLabel)
        layoutRow(null, strings.volumeLabel)
        layoutRow(null, strings.focusToneLabel)
        layoutRow(null, strings.relaxToneLabel)
        layoutRow(null, strings.testFocusLabel)
        layoutRow(null, strings.testRelaxLabel)
        layoutRow(null, strings.strictLabel)
        layoutRow(null, strings.ticks)
        layoutRow(null, strings.vibe)
        layoutRow(null, strings.themeLabel)
        layoutRow(null, strings.precisionLabel)
        // Visuals section: 1 header + 2 toggle rows. Same height as a
        // side-by-side row, so `layoutRow(null, label)` advances `currentY` once.
        layoutRow(null, strings.visualsHeader)
        layoutRow(null, strings.demosceneLabel)
        layoutRow(null, strings.nebulaLabel)
    }

    private fun indexOf(values: Array<String>, value: String): Int {
        var i = 0
        while (i < values.size) {
            if (values[i] == value) return i
            i++
        }
        return 0
    }

    private fun drawStepper(renderer: ScaledProceduralRenderer, valueText: String, x: Float, y: Float, h: Float, width: Float, primaryColorIndex: Int, accentColorIndex: Int, toneText: Boolean = false) {
        val localArrowW = minOf(h, (U * 2).toFloat())
        renderer.drawButton("<", x, y, localArrowW, h, isClicked = false)
        renderer.drawButton(">", x + width - localArrowW, y, localArrowW, h, isClicked = false)

        val spaceW = maxOf(U.toFloat(), width - localArrowW * 2f)
        val textPad = if (toneText) 0f else (U / 4).toFloat()
        val availableTextW = maxOf(U.toFloat(), spaceW - textPad * 2f)
        val textH = ProceduralTextRenderer.measureWrappedHeight(valueText, availableTextW)
        val startY = y + (h - textH) / 2f
        ProceduralTextRenderer.drawWrapped(renderer, valueText, x + localArrowW + textPad, startY, availableTextW, primaryColorIndex, alignment = ProceduralTextRenderer.ALIGN_CENTER)
    }

    private fun drawBarStepper(renderer: ScaledProceduralRenderer, percent: Int, maxBlocks: Int, x: Float, y: Float, h: Float, width: Float, primaryColorIndex: Int, accentColorIndex: Int) {
        val localArrowW = minOf(h, (U * 2).toFloat())
        renderer.drawButton("<", x, y, localArrowW, h, isClicked = false)
        renderer.drawButton(">", x + width - localArrowW, y, localArrowW, h, isClicked = false)

        val barPad = (U / 2).toFloat()
        val gap = (U / 8).toFloat()
        val startX = x + localArrowW + barPad
        val spaceW = width - localArrowW * 2f - barPad * 2f
        if (spaceW <= U.toFloat() || maxBlocks <= 0) return
        val blockW = maxOf((U / 16).toFloat(), spaceW / maxBlocks - gap)

        var i = 0
        while (i < maxBlocks) {
            val bx = startX + i * (blockW + gap)
            if (i < percent) {
                renderer.fillRectDither(bx, y + (U / 4).toFloat(), bx + blockW, y + h - (U / 4).toFloat(), primaryColorIndex, primaryColorIndex, SoftDitherPattern.SOLID)
            } else {
                renderer.drawRect(bx, y + (U / 4).toFloat(), blockW, h - (U / 2).toFloat(), accentColorIndex)
            }
            i++
        }
    }

}

// ════════════════════════════════════════════════════════════════════
//  ENTROPY SCENE
// ════════════════════════════════════════════════════════════════════
object EntropyScene : Scene {
    private var activePage = 0
    private var taskCount = 0
    private var seededLanguage = ""
    private var userEditedTasks = false
    private const val MAX_TASKS = 16
    private const val TASK_CAPACITY = 64
    private const val MIN_TASK_ROWS = 1
    private const val INPUT_HEIGHT_DEN = U - (U / 8)
    private const val TASK_ROW_HEIGHT_DEN = U - (U / 8)
    private const val DETONATOR_HEIGHT_DEN = U - (U / 4)
    private const val MIN_TASK_ROW_CELLS_NUM = 3
    private const val MIN_TASK_ROW_CELLS_DEN = 2
    private const val POPUP_CLOSE_CELLS = 2
    private const val DELETE_LABEL_CELLS = 3
    private var isInputFocused = false
    private val inputContainer = FixedInputContainer(64)
    private val taskCodePoints = IntArray(MAX_TASKS * TASK_CAPACITY)
    private val taskLengths = IntArray(MAX_TASKS)

    private var isSpinning = false
    private var spinTimer = 0f
    private var spinDelay = 0.04f
    private var spinCount = 0
    private const val MAX_SPINS = 25
    private var animationIndex = -1
    private var selectedIndex = -1
    private var rngState = 0x4D595DF4

    override fun onEnter(payload: Any?) {
        val language = SceneManager.timerActions?.getUiState()?.language ?: "en"
        isInputFocused = false
        isSpinning = false
        selectedIndex = -1
        animationIndex = -1
        activePage = 0

        if (taskCount == 0 || (!userEditedTasks && seededLanguage != language)) {
            seedDefaultTasks(getStrings(language))
            seededLanguage = language
        }

        clearInput()
    }

    override fun onExit() {
        isInputFocused = false
        isSpinning = false
    }

    override fun update(dt: Float) {
        if (isSpinning && taskCount > 0) {
            spinTimer += dt
            if (spinTimer >= spinDelay) {
                spinTimer = 0f
                animationIndex = (animationIndex + 1) % taskCount
                SceneManager.performHapticFeedback(EngineHaptics.TICK)
                
                spinDelay *= 1.15f
                spinCount++
                
                if (spinCount >= MAX_SPINS) {
                    isSpinning = false
                    val finalIdx = nextRandomIndex(taskCount)
                    animationIndex = finalIdx
                    selectedIndex = finalIdx
                    SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                }
            }
        }
    }

    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val state = SceneManager.timerActions?.getUiState() ?: return
        val strings = getStrings(state.language)
        SceneManager.logStringsAfterLanguageChange("EntropyScene", state.language)
        // This will setup the palette in Pc98GraphicsHardware dynamically
        EngineThemes.getColors(state.appTheme, state.isBreak)
        
        // 0. Global Screen Clear to prevent transparent frame smearing
        renderer.drawRect(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG)
        
        val isPortrait = playX <= 0
        val playAreaStartX = playX.toFloat()
        val playAreaW = playW.toFloat()
        val playAreaH = playH.toFloat()
        
        renderer.fillRectDither(playAreaStartX, 0f, playAreaStartX + playAreaW, playAreaH, PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)

        val padding = maxOf(U.toFloat(), playAreaW / (U + (U / 2)))
        val safeTop = maxOf(playAreaH / (U - (U / 4)), (U * 2).toFloat())
        val headerY = safeTop
        val contentW = playAreaW - padding * 2f
        val headerH = ProceduralTextRenderer.measureHeadingHeight(strings.entropyBomb, contentW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        ProceduralTextRenderer.drawHeading(renderer, strings.entropyBomb, playAreaStartX + padding, headerY, contentW, PaletteIndices.PRIMARY, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val dividerY = headerY + headerH + (U / 2).toFloat()
        renderer.drawLine(playAreaStartX + padding / 2f, dividerY, playAreaStartX + playAreaW - padding / 2f, dividerY, PaletteIndices.SECONDARY, 1f)

        val descY = headerY + headerH + U.toFloat()
        val descH = maxOf((U * 2).toFloat(), ProceduralTextRenderer.measureWrappedHeight(strings.entropyDesc, contentW))
        ProceduralTextRenderer.drawWrapped(renderer, strings.entropyDesc, playAreaStartX + padding, descY, contentW, PaletteIndices.SECONDARY)

        val inputY = descY + descH
        val loadW = minOf((U * 6).toFloat(), playAreaW / (U / 4))
        val gap = (U / 2).toFloat()
        val inputW = playAreaW - padding * 2f - loadW - gap
        val inputX = playAreaStartX + padding
        val inputTextW = maxOf(U.toFloat(), inputW - U.toFloat())
        val inputTextH = if (inputContainer.length == 0) {
            ProceduralTextRenderer.measureWrappedHeight(strings.addTaskPlaceholder, inputTextW)
        } else {
            ProceduralTextRenderer.measureWrappedHeight(inputContainer.codePoints, 0, inputContainer.length, inputTextW)
        }
        val inputH = maxOf(
            maxOf((U * 2).toFloat(), playAreaH / INPUT_HEIGHT_DEN),
            inputTextH + U.toFloat(),
            ScaledProceduralRenderer.measureButtonHeight(strings.addButton, loadW, (U * 2).toFloat(), allowTextStacking = true)
        )
        renderer.drawRect(inputX, inputY, inputW, inputH, if (isInputFocused) PaletteIndices.PRIMARY else PaletteIndices.SECONDARY)

        val inputTextY = inputY + (inputH - inputTextH) / 2f
        if (inputContainer.length == 0) {
            ProceduralTextRenderer.drawWrapped(renderer, strings.addTaskPlaceholder, inputX + (U / 2).toFloat(), inputTextY, inputTextW, PaletteIndices.SECONDARY)
        } else {
            ProceduralTextRenderer.drawWrapped(renderer, inputContainer.codePoints, 0, inputContainer.length, inputX + (U / 2).toFloat(), inputTextY, inputTextW, PaletteIndices.PRIMARY)
        }
        
        val loadX = inputX + inputW + gap
        renderer.drawButton(strings.addButton, loadX, inputY, loadW, inputH, isClicked = false, allowTextStacking = true)

        val slotsStartY = inputY + inputH + (U / 2).toFloat()
        val slotSpacing = (U / 4).toFloat()
        val detW = playAreaW - padding * 2f
        val minimumDetH = maxOf((U * 2).toFloat(), playAreaH / DETONATOR_HEIGHT_DEN)
        val detH = maxOf(
            ScaledProceduralRenderer.measureButtonHeight(strings.explodeButton, detW, minimumDetH, allowTextStacking = true),
            ScaledProceduralRenderer.measureButtonHeight(strings.detonatingButton, detW, minimumDetH, allowTextStacking = true)
        )
        val detY = playAreaH - detH - U.toFloat()
        val switcherBtnSize = maxOf((U + U / 2).toFloat(), playAreaH / (U + (U / 8)))
        val rowRight = playAreaStartX + playAreaW - padding
        val deleteW = (U * DELETE_LABEL_CELLS).toFloat()
        val deleteX = rowRight - deleteW
        val taskMaxW = maxOf((U * 6).toFloat(), deleteX - inputX - U.toFloat())
        val availableNoPager = detY - slotsStartY - (U / 2).toFloat()
        val rowsNeedPager = taskRowsHeight(0, taskCount, playAreaH, taskMaxW, slotSpacing) > availableNoPager
        val pageRowsHeight = if (rowsNeedPager) availableNoPager - switcherBtnSize - (U / 2).toFloat() else availableNoPager
        val totalPages = if (rowsNeedPager) taskPageCount(playAreaH, taskMaxW, slotSpacing, pageRowsHeight) else 1
        if (activePage >= totalPages) activePage = totalPages - 1
        val startIdx = if (rowsNeedPager) taskPageStart(activePage, playAreaH, taskMaxW, slotSpacing, pageRowsHeight) else 0
        val endIdx = if (rowsNeedPager) taskPageEnd(startIdx, playAreaH, taskMaxW, slotSpacing, pageRowsHeight) else taskCount

        var slotY = slotsStartY
        var idx = startIdx
        while (idx < endIdx) {

            val isHighlighted = animationIndex == idx
            val slotH = taskRowHeight(idx, playAreaH, taskMaxW)
            val frameColorIndex = if (isHighlighted) PaletteIndices.PRIMARY else PaletteIndices.SECONDARY
            val slotTxtScale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
            val taskTextH = taskTextHeight(idx, taskMaxW)
            val slotTxtY = slotY + (slotH - taskTextH) / 2f
            val deleteY = slotY + (slotH - U.toFloat()) / 2f

            if (isHighlighted) {
                renderer.fillRectDither(inputX, slotY, inputX + playAreaW - padding * 2f, slotY + slotH, frameColorIndex, frameColorIndex, SoftDitherPattern.SOLID)
                drawTaskRow(renderer, idx, inputX + (U / 2).toFloat(), slotTxtY, PaletteIndices.BLACK, slotTxtScale, taskMaxW)
                if (!isSpinning) {
                    ProceduralTextRenderer.drawRaw(renderer, "［Ｘ］", deleteX, deleteY, PaletteIndices.BLACK, slotTxtScale)
                }
            } else {
                renderer.drawRect(inputX, slotY, playAreaW - padding * 2f, slotH, frameColorIndex)
                drawTaskRow(renderer, idx, inputX + (U / 2).toFloat(), slotTxtY, PaletteIndices.PRIMARY, slotTxtScale, taskMaxW)
                if (!isSpinning) {
                    ProceduralTextRenderer.drawRaw(renderer, "［Ｘ］", deleteX, deleteY, PaletteIndices.SECONDARY, slotTxtScale)
                }
            }
            slotY += slotH + slotSpacing
            idx++
        }

        if (totalPages > 1) {
            val switcherY = slotsStartY + taskRowsHeight(startIdx, endIdx, playAreaH, taskMaxW, slotSpacing) + (U / 2).toFloat()
            
            // draw < button
            renderer.drawButton("＜", inputX, switcherY, switcherBtnSize, switcherBtnSize, isClicked = false)
            
            val pageTextScale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
            val pageTextY = switcherY + (switcherBtnSize - (U * pageTextScale).toFloat()) / 2f
            drawPageIndicator(renderer, activePage + 1, totalPages, inputX + switcherBtnSize + (U / 2).toFloat(), pageTextY, PaletteIndices.PRIMARY, pageTextScale)

            // draw > button
            renderer.drawButton("＞", inputX + switcherBtnSize + (U * 4).toFloat(), switcherY, switcherBtnSize, switcherBtnSize, isClicked = false)
        }
        
        if (isSpinning) {
            renderer.fillRectDither(inputX, detY, inputX + detW, detY + detH, PaletteIndices.SECONDARY, PaletteIndices.SECONDARY, SoftDitherPattern.SOLID)
            drawCenteredText(renderer, strings.detonatingButton, inputX, detY, detW, detH, PaletteIndices.BLACK)
        } else {
            val canSpin = taskCount > 0
            val btnText = strings.explodeButton
            if (canSpin) {
                renderer.drawButton(btnText, inputX, detY, detW, detH, isClicked = false, allowTextStacking = true)
            } else {
                // disabled style
                renderer.drawRect(inputX, detY, detW, detH, PaletteIndices.SECONDARY)
                drawCenteredText(renderer, btnText, inputX, detY, detW, detH, PaletteIndices.SECONDARY)
            }
        }

        if (selectedIndex >= 0 && selectedIndex < taskCount) {
            drawDirectivePopup(renderer, playAreaStartX, playAreaW, playAreaH, strings)
        }

        RetroHudComponent.render(renderer, playX, playY, playW, playH)

    }

    private fun drawDirectivePopup(renderer: ScaledProceduralRenderer, playAreaStartX: Float, playAreaW: Float, playAreaH: Float, strings: AppStrings) {
        val popupX = playAreaStartX + playAreaW * 0.1f
        val popupW = playAreaW * 0.8f
        val popupH = directivePopupHeight(playAreaW, playAreaH, strings)
        val popupY = maxOf((U / 2).toFloat(), (playAreaH - popupH) / 2f)
        
        renderer.fillRectDither(popupX, popupY, popupX + popupW, popupY + popupH, PaletteIndices.BLACK, PaletteIndices.BLACK, SoftDitherPattern.SOLID)
        renderer.drawRect(popupX, popupY, popupW, popupH, PaletteIndices.ERROR)
        
        // Full-width close button using drawButton.
        val closeSize = (U * POPUP_CLOSE_CELLS).toFloat()
        val closePad = (U / 2).toFloat()
        renderer.drawButton("Ｘ", popupX + popupW - closeSize - closePad, popupY + closePad, closeSize, closeSize, isClicked = false)

        val titleW = directiveTitleWidth(popupW)
        val titleY = popupY + closePad
        val titleH = ProceduralTextRenderer.measureHeadingHeight(strings.missionLabel, titleW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        ProceduralTextRenderer.drawHeading(renderer, strings.missionLabel, popupX + U.toFloat(), titleY, titleW, PaletteIndices.ERROR, ScaledProceduralRenderer.TEXT_SCALE_HEADER)

        val taskW = maxOf(U.toFloat(), popupW - (U * 2).toFloat())
        val taskY = titleY + maxOf(titleH, closeSize) + (U / 2).toFloat()
        drawTaskBuffer(renderer, selectedIndex, popupX + U.toFloat(), taskY, PaletteIndices.PRIMARY, ScaledProceduralRenderer.TEXT_SCALE_IDENTITY, taskW)

        val cBtnW = popupW * 0.75f
        val cBtnH = ScaledProceduralRenderer.measureButtonHeight(strings.launchEmergency, cBtnW, (U * 2).toFloat(), allowTextStacking = true)
        val cBtnX = popupX + (popupW - cBtnW) / 2f
        val cBtnY = popupY + popupH - cBtnH - U.toFloat()
        renderer.drawButton(strings.launchEmergency, cBtnX, cBtnY, cBtnW, cBtnH, isClicked = false, allowTextStacking = true)
    }

    override fun onInput(inputCode: Int) {
        if (isInputFocused) {
            inputContainer.processPayload(inputCode)
            if (inputCode == EngineInputCodes.CMD_ENTER) {
                isInputFocused = false
                addInputTask()
            }
        }
    }

    override fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        onInput(x, y, action, playX, playY, playW, playH)
    }

    override fun onInput(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        if (RetroHudComponent.onTouchEvent(x, y, action, playX, playY, playW, playH)) return
        val isDown = action == TouchAction.DOWN
        if (!isDown) return

        val state = SceneManager.timerActions?.getUiState() ?: return
        val isPortrait = playX <= 0
        val playAreaStartX = playX.toFloat()
        val playAreaW = playW.toFloat()
        val playAreaH = playH.toFloat()
        val fx = x.toFloat()
        val fy = y.toFloat()

        if (selectedIndex >= 0 && selectedIndex < taskCount) {
            val popupX = playAreaStartX + playAreaW * 0.1f
            val popupW = playAreaW * 0.8f
            val strings = getStrings(state.language)
            val popupH = directivePopupHeight(playAreaW, playAreaH, strings)
            val popupY = maxOf((U / 2).toFloat(), (playAreaH - popupH) / 2f)
            
            // X close button
            val closeSize = (U * POPUP_CLOSE_CELLS).toFloat()
            val closePad = (U / 2).toFloat()
            val closeX = popupX + popupW - closeSize - closePad
            val closeY = popupY + closePad
            if (fx >= closeX && fx <= closeX + closeSize && fy >= closeY && fy <= closeY + closeSize) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                selectedIndex = -1
                return
            }
            // Commence button
            val cBtnW = popupW * 0.75f
            val cBtnH = ScaledProceduralRenderer.measureButtonHeight(strings.launchEmergency, cBtnW, (U * 2).toFloat(), allowTextStacking = true)
            val cBtnX = popupX + (popupW - cBtnW) / 2f
            val cBtnY = popupY + popupH - cBtnH - U.toFloat()
            if (fx >= cBtnX && fx <= cBtnX + cBtnW && fy >= cBtnY && fy <= cBtnY + cBtnH) {
                SceneManager.performHapticFeedback(EngineHaptics.CLICK)
                if (SceneManager.timerActionsFromTouchEnabled()) {
                    SceneManager.timerActions?.selectPreset("emergency")
                    SceneManager.timerActions?.updateTask(taskToString(selectedIndex))
                    SceneManager.timerActions?.startTimer()
                }
                selectedIndex = -1
                SceneManager.switchScene(SceneId.ACTIVE_TIMER)
            }
            return
        }

        val padding = maxOf(U.toFloat(), playAreaW / (U + (U / 2)))
        val safeTop = maxOf(SceneManager.logicalHeight / (U - (U / 4)), (U * 2).toFloat())
        val headerY = safeTop
        val strings = getStrings(state.language)
        val contentW = playAreaW - padding * 2f
        val headerH = ProceduralTextRenderer.measureHeadingHeight(strings.entropyBomb, contentW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val descY = headerY + headerH + U.toFloat()
        val descH = maxOf((U * 2).toFloat(), ProceduralTextRenderer.measureWrappedHeight(strings.entropyDesc, contentW))
        val inputY = descY + descH
        val loadW = minOf((U * 6).toFloat(), playAreaW / (U / 4))
        val gap = (U / 2).toFloat()
        val inputW = playAreaW - padding * 2f - loadW - gap
        val inputX = playAreaStartX + padding
        val inputTextW = maxOf(U.toFloat(), inputW - U.toFloat())
        val inputTextH = if (inputContainer.length == 0) {
            ProceduralTextRenderer.measureWrappedHeight(strings.addTaskPlaceholder, inputTextW)
        } else {
            ProceduralTextRenderer.measureWrappedHeight(inputContainer.codePoints, 0, inputContainer.length, inputTextW)
        }
        val inputH = maxOf(
            maxOf((U * 2).toFloat(), playAreaH / INPUT_HEIGHT_DEN),
            inputTextH + U.toFloat(),
            ScaledProceduralRenderer.measureButtonHeight(strings.addButton, loadW, (U * 2).toFloat(), allowTextStacking = true)
        )
        
        if (fx >= inputX && fx <= inputX + inputW && fy >= inputY && fy <= inputY + inputH && !isSpinning) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            isInputFocused = true
            SceneManager.triggerKeyboard()
            return
        }
        
        val loadX = inputX + inputW + gap
        if (fx >= loadX && fx <= loadX + loadW && fy >= inputY && fy <= inputY + inputH && !isSpinning) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            isInputFocused = false
            addInputTask()
            return
        }

        val slotsStartY = inputY + inputH + (U / 2).toFloat()
        val slotSpacing = (U / 4).toFloat()
        val detW = playAreaW - padding * 2f
        val minimumDetH = maxOf((U * 2).toFloat(), playAreaH / DETONATOR_HEIGHT_DEN)
        val detH = maxOf(
            ScaledProceduralRenderer.measureButtonHeight(strings.explodeButton, detW, minimumDetH, allowTextStacking = true),
            ScaledProceduralRenderer.measureButtonHeight(strings.detonatingButton, detW, minimumDetH, allowTextStacking = true)
        )
        val detY = playAreaH - detH - U.toFloat()
        val switcherBtnSize = maxOf((U + U / 2).toFloat(), playAreaH / (U + (U / 8)))
        val rowRight = playAreaStartX + playAreaW - padding
        val deleteW = (U * DELETE_LABEL_CELLS).toFloat()
        val taskMaxW = maxOf((U * 6).toFloat(), rowRight - deleteW - inputX - U.toFloat())
        val availableNoPager = detY - slotsStartY - (U / 2).toFloat()
        val rowsNeedPager = taskRowsHeight(0, taskCount, playAreaH, taskMaxW, slotSpacing) > availableNoPager
        val pageRowsHeight = if (rowsNeedPager) availableNoPager - switcherBtnSize - (U / 2).toFloat() else availableNoPager
        val totalPages = if (rowsNeedPager) taskPageCount(playAreaH, taskMaxW, slotSpacing, pageRowsHeight) else 1
        if (activePage >= totalPages) activePage = totalPages - 1
        val startIdx = if (rowsNeedPager) taskPageStart(activePage, playAreaH, taskMaxW, slotSpacing, pageRowsHeight) else 0
        val endIdx = if (rowsNeedPager) taskPageEnd(startIdx, playAreaH, taskMaxW, slotSpacing, pageRowsHeight) else taskCount
        val switcherY = slotsStartY + taskRowsHeight(startIdx, endIdx, playAreaH, taskMaxW, slotSpacing) + (U / 2).toFloat()
        if (totalPages > 1 && fy >= switcherY && fy <= switcherY + switcherBtnSize && !isSpinning) {
            if (fx >= inputX && fx <= inputX + switcherBtnSize) {
                SceneManager.performHapticFeedback(EngineHaptics.TICK)
                activePage = (activePage - 1 + totalPages) % totalPages
                return
            } else if (fx >= inputX + switcherBtnSize + (U * 4).toFloat() && fx <= inputX + switcherBtnSize + (U * 4).toFloat() + switcherBtnSize) {
                SceneManager.performHapticFeedback(EngineHaptics.TICK)
                activePage = (activePage + 1) % totalPages
                return
            }
        }

        var slotY = slotsStartY
        var idx = startIdx
        while (idx < endIdx) {
            val slotH = taskRowHeight(idx, playAreaH, taskMaxW)
            if (fy >= slotY && fy <= slotY + slotH && !isSpinning) {
                if (fx >= rowRight - deleteW && fx <= rowRight) {
                    SceneManager.performHapticFeedback(EngineHaptics.TICK)
                    deleteTask(idx)
                    animationIndex = -1
                    selectedIndex = -1
                }
                return
            }
            slotY += slotH + slotSpacing
            idx++
        }

        val canSpin = taskCount > 0 && !isSpinning
        if (canSpin && fx >= inputX && fx <= inputX + detW && fy >= detY && fy <= detY + detH) {
            SceneManager.performHapticFeedback(EngineHaptics.CLICK)
            isSpinning = true
            spinTimer = 0f
            spinDelay = 0.04f
            spinCount = 0
            animationIndex = -1
            selectedIndex = -1
        }
        
        isInputFocused = false
    }

    private fun seedDefaultTasks(strings: AppStrings) {
        taskCount = 0
        val defaults = strings.defaultTasks
        var i = 0
        while (i < defaults.size && i < MAX_TASKS) {
            copyStringToSlot(taskCount, defaults[i])
            taskCount++
            i++
        }
        userEditedTasks = false
    }

    private fun copyStringToSlot(index: Int, text: String) {
        val base = index * TASK_CAPACITY
        var i = 0
        while (i < text.length && i < TASK_CAPACITY) {
            taskCodePoints[base + i] = text[i].code
            i++
        }
        taskLengths[index] = i
    }

    private fun clearInput() {
        while (inputContainer.length > 0) {
            inputContainer.processPayload(EngineInputCodes.CMD_BACKSPACE)
        }
    }

    private fun addInputTask() {
        if (taskCount >= MAX_TASKS || inputContainer.length == 0) return
        var hasContent = false
        var i = 0
        while (i < inputContainer.length) {
            if (inputContainer.codePoints[i] > 32) {
                hasContent = true
            }
            i++
        }
        if (!hasContent) {
            clearInput()
            return
        }

        val base = taskCount * TASK_CAPACITY
        var copy = 0
        while (copy < inputContainer.length && copy < TASK_CAPACITY) {
            taskCodePoints[base + copy] = inputContainer.codePoints[copy]
            copy++
        }
        taskLengths[taskCount] = copy
        taskCount++
        userEditedTasks = true
        clearInput()
    }

    private fun deleteTask(index: Int) {
        if (index < 0 || index >= taskCount) return
        var i = index
        while (i < taskCount - 1) {
            taskLengths[i] = taskLengths[i + 1]
            val dst = i * TASK_CAPACITY
            val src = (i + 1) * TASK_CAPACITY
            var j = 0
            while (j < TASK_CAPACITY) {
                taskCodePoints[dst + j] = taskCodePoints[src + j]
                j++
            }
            i++
        }
        taskCount--
        taskLengths[taskCount] = 0
        userEditedTasks = true
        if (taskCount == 0) activePage = 0
    }

    private fun taskPageCount(playAreaH: Float, taskMaxW: Float, spacing: Float, availableHeight: Float): Int {
        if (taskCount <= 0) return 1
        var count = 0
        var start = 0
        while (start < taskCount) {
            start = taskPageEnd(start, playAreaH, taskMaxW, spacing, availableHeight)
            count++
        }
        return count
    }

    private fun taskPageStart(page: Int, playAreaH: Float, taskMaxW: Float, spacing: Float, availableHeight: Float): Int {
        var start = 0
        var currentPage = 0
        while (currentPage < page && start < taskCount) {
            start = taskPageEnd(start, playAreaH, taskMaxW, spacing, availableHeight)
            currentPage++
        }
        return start
    }

    private fun taskPageEnd(start: Int, playAreaH: Float, taskMaxW: Float, spacing: Float, availableHeight: Float): Int {
        if (start >= taskCount) return taskCount
        var used = 0f
        var end = start
        while (end < taskCount) {
            val rowH = taskRowHeight(end, playAreaH, taskMaxW)
            val nextUsed = used + if (end == start) rowH else spacing + rowH
            if (end > start && nextUsed > availableHeight) break
            used = nextUsed
            end++
            if (used >= availableHeight) break
        }
        return maxOf(start + MIN_TASK_ROWS, end).coerceAtMost(taskCount)
    }

    private fun taskRowsHeight(start: Int, end: Int, playAreaH: Float, taskMaxW: Float, spacing: Float): Float {
        var height = 0f
        var index = start.coerceAtLeast(0)
        val safeEnd = end.coerceAtMost(taskCount)
        while (index < safeEnd) {
            if (index > start) height += spacing
            height += taskRowHeight(index, playAreaH, taskMaxW)
            index++
        }
        return height
    }

    private fun taskTextHeight(index: Int, taskMaxW: Float): Float {
        val textW = maxOf(U.toFloat(), taskMaxW - (U * 5).toFloat())
        return taskBufferHeight(index, textW)
    }

    private fun taskBufferHeight(index: Int, maxWidth: Float): Float {
        if (index < 0 || index >= taskCount) return U.toFloat()
        return maxOf(U.toFloat(), ProceduralTextRenderer.measureWrappedHeight(taskCodePoints, index * TASK_CAPACITY, taskLengths[index], maxWidth))
    }

    private fun taskRowHeight(index: Int, playAreaH: Float, taskMaxW: Float): Float {
        val minimumHeight = maxOf((U * MIN_TASK_ROW_CELLS_NUM / MIN_TASK_ROW_CELLS_DEN).toFloat(), playAreaH / TASK_ROW_HEIGHT_DEN)
        return maxOf(minimumHeight, taskTextHeight(index, taskMaxW) + (U / 2).toFloat())
    }

    private fun directiveTitleWidth(popupW: Float): Float {
        val closeSize = (U * POPUP_CLOSE_CELLS).toFloat()
        val closePad = (U / 2).toFloat()
        return maxOf(U.toFloat(), popupW - U.toFloat() - closeSize - closePad * 2f)
    }

    private fun directivePopupHeight(playAreaW: Float, playAreaH: Float, strings: AppStrings): Float {
        val popupW = playAreaW * 0.8f
        val closeSize = (U * POPUP_CLOSE_CELLS).toFloat()
        val closePad = (U / 2).toFloat()
        val titleH = ProceduralTextRenderer.measureHeadingHeight(strings.missionLabel, directiveTitleWidth(popupW), ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val taskW = maxOf(U.toFloat(), popupW - (U * 2).toFloat())
        val taskH = taskBufferHeight(selectedIndex, taskW)
        val buttonW = popupW * 0.75f
        val buttonH = ScaledProceduralRenderer.measureButtonHeight(strings.launchEmergency, buttonW, (U * 2).toFloat(), allowTextStacking = true)
        val contentH = closePad + maxOf(closeSize, titleH) + (U / 2).toFloat() + taskH + (U / 2).toFloat() + buttonH + U.toFloat()
        return maxOf(playAreaH * 0.55f, contentH)
    }

    private fun nextRandomIndex(bound: Int): Int {
        var x = rngState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        if (x == 0) x = 0x13579BDF
        rngState = x
        return if (bound <= 0) 0 else (x and 0x7FFFFFFF) % bound
    }

    private fun drawTaskRow(renderer: ScaledProceduralRenderer, index: Int, x: Float, y: Float, color: Int, scale: Int, maxWidth: Float) {
        var curX = x
        val charWidth = ScaledProceduralRenderer.measureTextHeight(scale)
        renderer.drawGlyph('［', curX, y, color, scale = scale); curX += charWidth
        drawTwoDigits(renderer, index + 1, curX, y, color, scale); curX += charWidth * 2f
        renderer.drawGlyph('］', curX, y, color, scale = scale); curX += charWidth
        renderer.drawGlyph('　', curX, y, color, scale = scale); curX += charWidth
        drawTaskBuffer(renderer, index, curX, y, color, scale, maxWidth - charWidth * 5f)
    }

    private fun drawTaskBuffer(renderer: ScaledProceduralRenderer, index: Int, x: Float, y: Float, color: Int, scale: Int, maxWidth: Float) {
        val base = index * TASK_CAPACITY
        drawCodePointBuffer(renderer, taskCodePoints, base, taskLengths[index], x, y, color, scale, maxWidth)
    }

    private fun drawCodePointBuffer(renderer: ScaledProceduralRenderer, buffer: IntArray, offset: Int, length: Int, x: Float, y: Float, color: Int, scale: Int, maxWidth: Float) {
        ProceduralTextRenderer.drawWrapped(renderer, buffer, offset, length, x, y, maxWidth, color, scale)
    }

    private fun drawTwoDigits(renderer: ScaledProceduralRenderer, value: Int, x: Float, y: Float, color: Int, scale: Int) {
        val clamped = value.coerceIn(0, 99)
        val tens = clamped / 10
        val ones = clamped % 10
        val charWidth = ScaledProceduralRenderer.measureTextHeight(scale)
        renderer.drawGlyph(FULLWIDTH_DIGITS[tens], x, y, color, scale = scale)
        renderer.drawGlyph(FULLWIDTH_DIGITS[ones], x + charWidth, y, color, scale = scale)
    }

    private fun drawPageIndicator(renderer: ScaledProceduralRenderer, page: Int, total: Int, x: Float, y: Float, color: Int, scale: Int) {
        val charWidth = ScaledProceduralRenderer.measureTextHeight(scale)
        drawTwoDigits(renderer, page, x, y, color, scale)
        renderer.drawGlyph('／', x + charWidth * 2f, y, color, scale = scale)
        drawTwoDigits(renderer, total, x + charWidth * 3f, y, color, scale)
    }

    private fun drawCenteredText(renderer: ScaledProceduralRenderer, text: String, x: Float, y: Float, w: Float, h: Float, color: Int) {
        val scale = ScaledProceduralRenderer.TEXT_SCALE_IDENTITY
        val textAreaW = maxOf(U.toFloat(), w - U.toFloat())
        val textH = ProceduralTextRenderer.measureWrappedHeight(text, textAreaW, scale)
        ProceduralTextRenderer.drawWrapped(renderer, text, x + (U / 2).toFloat(), y + (h - textH) / 2f, textAreaW, color, scale, ProceduralTextRenderer.ALIGN_CENTER)
    }

    private fun taskToString(index: Int): String {
        val builder = kotlin.text.StringBuilder()
        val base = index * TASK_CAPACITY
        var i = 0
        while (i < taskLengths[index]) {
            val cp = taskCodePoints[base + i]
            if (cp <= 0xFFFF) {
                builder.append(cp.toChar())
            }
            i++
        }
        return builder.toString()
    }
}

// ════════════════════════════════════════════════════════════════════
//  BLOCK OVERLAY SCENE
// ════════════════════════════════════════════════════════════════════
object BlockOverlayScene : Scene {
    var onReturnClicked: (() -> Unit)? = null

    override fun onEnter(payload: Any?) {}
    override fun onExit() {}
    override fun update(dt: Float) {}

    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val theme = "reimu"
        // This will setup the palette in Pc98GraphicsHardware dynamically
        EngineThemes.getColors(theme, false)
        
        renderer.fillRectDither(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG, PaletteIndices.BG, SoftDitherPattern.SOLID)

        val contentX = U.toFloat()
        val contentW = maxOf(U.toFloat(), logicalWidth - (U * 2).toFloat())
        val title = "ＦＯＣＵＳ　ＭＯＤＥ　ＡＣＴＩＶＥ"
        val subtitle = "Ｇｅｔ　ｂａｃｋ　ｔｏ　ｗｏｒｋ．"
        val titleH = ProceduralTextRenderer.measureHeadingHeight(title, contentW, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        val titleY = logicalHeight * 0.25f
        ProceduralTextRenderer.drawHeading(renderer, title, contentX, titleY, contentW, PaletteIndices.PRIMARY, ScaledProceduralRenderer.TEXT_SCALE_HEADER, ProceduralTextRenderer.ALIGN_CENTER)
        val subtitleY = titleY + titleH + U.toFloat()
        ProceduralTextRenderer.drawWrapped(renderer, subtitle, contentX, subtitleY, contentW, PaletteIndices.SECONDARY, alignment = ProceduralTextRenderer.ALIGN_CENTER)

        val btnW = blockButtonWidth(logicalWidth)
        val btnH = blockButtonHeight(logicalWidth, logicalHeight)
        val btnX = (logicalWidth - btnW) / 2f
        val btnY = blockButtonY(logicalHeight, btnH)
        
        renderer.drawButton("ＲＥＴＵＲＮ　ＴＯ　ＴＩＭＥＢＯＸ", btnX, btnY, btnW, btnH, isClicked = false, allowTextStacking = true)
    }

    override fun onInput(inputCode: Int) {}

    override fun onTouch(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        if (action == TouchAction.DOWN) {
            val logicalWidth = SceneManager.logicalWidth
            val logicalHeight = SceneManager.logicalHeight
            val btnW = blockButtonWidth(logicalWidth)
            val btnH = blockButtonHeight(logicalWidth, logicalHeight)
            val btnX = (logicalWidth - btnW) / 2f
            val btnY = blockButtonY(logicalHeight, btnH)
            
            if (x >= btnX && x <= btnX + btnW && y >= btnY && y <= btnY + btnH) {
                onReturnClicked?.invoke()
            }
        }
    }

    private fun blockButtonWidth(logicalWidth: Float): Float {
        val textWidth = ScaledProceduralRenderer.measureTextWidth("ＲＥＴＵＲＮ　ＴＯ　ＴＩＭＥＢＯＸ") + U.toFloat()
        return minOf(logicalWidth - (U * 2).toFloat(), maxOf(logicalWidth * 0.4f, ((U * 12) + (U / 2)).toFloat(), textWidth))
    }

    private fun blockButtonHeight(logicalWidth: Float, logicalHeight: Float): Float {
        return ScaledProceduralRenderer.measureButtonHeight("ＲＥＴＵＲＮ　ＴＯ　ＴＩＭＥＢＯＸ", blockButtonWidth(logicalWidth), maxOf(logicalHeight * 0.1f, (U * 2).toFloat()), allowTextStacking = true)
    }

    private fun blockButtonY(logicalHeight: Float, buttonHeight: Float): Float {
        return minOf(logicalHeight * 0.7f, logicalHeight - buttonHeight - U.toFloat())
    }
}

private fun drawCalendarTimeline(
    renderer: ScaledProceduralRenderer,
    preset: TimerPreset,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    isActive: Boolean
) {
    val seq = preset.sequence
    if (seq.isEmpty()) return
    val types = preset.sequenceTypes
    var total = 0
    var totalIdx = 0
    while (totalIdx < seq.size) {
        total += seq[totalIdx]
        totalIdx++
    }
    val totalSecs = total.toFloat()
    if (totalSecs <= 0f) return

    val numBlocks = seq.size
    val gap = (U / 8).toFloat()
    val totalGapSpace = (numBlocks - 1) * gap
    val availableW = width - totalGapSpace
    if (availableW <= 0f || height <= 0f) return

    var currentX = x
    val priColor = if (isActive) PaletteIndices.BLACK else PaletteIndices.PRIMARY
    val secColor = if (isActive) PaletteIndices.PRIMARY else PaletteIndices.SECONDARY

    var i = 0
    while (i < numBlocks) {
        val duration = seq[i]
        val blockType = if (i < types.size) types[i] else "focus"
        val blockW = (duration.toFloat() / totalSecs) * availableW
        if (blockW > 0f) {
            val isRelax = blockType == "relax"
            val blockH = if (isRelax) height / 2f else height
            val blockY = y + (height - blockH)

            val color = if (isRelax) secColor else priColor
            renderer.fillRectDither(
                currentX, blockY, currentX + blockW, blockY + blockH,
                color, color, SoftDitherPattern.SOLID
            )
        }
        currentX += blockW + gap
        i++
    }
}
