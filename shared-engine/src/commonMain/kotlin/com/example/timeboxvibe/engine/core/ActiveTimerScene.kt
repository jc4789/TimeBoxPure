package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import com.example.timeboxvibe.engine.getStrings
import kotlin.math.abs

private const val U = CANONICAL_UI_UNIT

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
    private var lastRenderState: EngineUiState? = null
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
        lastRenderState = null
        val state = SceneManager.timerActions?.getUiState() ?: return
        lastRenderState = state
        // This will setup the palette in Pc98GraphicsHardware dynamically
        EngineThemes.getColors(state.appTheme, state.isBreak)
        val strings = getStrings(state.language)
        
        // 0. Global Screen Clear to prevent transparent frame smearing
        renderer.drawRect(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG)
        
        val isPortrait = UiShellLayout.isTallDisplay
        
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

    }

    override fun renderOverlay(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val state = lastRenderState ?: return
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
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
        val inPlayArea = x >= playX && y >= playY && x < playX + playW && y < playY + playH

        if (!isDragging && !inPlayArea) {
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
        if (action == TouchAction.CANCEL) {
            isTaskFocused = false
            return
        }
        val isUp = action == TouchAction.UP
        if (!isUp) return

        val state = SceneManager.timerActions?.getUiState() ?: return
        val logicalWidth = SceneManager.logicalWidth
        val logicalHeight = SceneManager.logicalHeight
        val isPortrait = UiShellLayout.isTallDisplay

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
