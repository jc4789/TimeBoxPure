package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import com.example.timeboxvibe.engine.getStrings

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
    private const val CONTROL_BOTTOM_CLEARANCE_CELLS = 1
    private const val GRAPHICS_TOP_GAP_CELLS = 1
    private const val CALENDAR_PANEL_HEIGHT_CELLS = 5
    private const val CALENDAR_PANEL_GAP_CELLS = 1
    var isTaskFocused = false
    private val inputContainer = FixedInputContainer(64)
    private val taskCursor = EngineCursorRenderer()
    private var alarmMarqueeX = 0f
    private var renderedControlRowY = 0f
    private var lastRenderState: EngineUiState? = null
    private val nestedTimebox = NestedTimeboxInstrumentRenderer()
    override fun onEnter(payload: Any?) {
        isTaskFocused = false
        renderedControlRowY = 0f
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
    }

    override fun onExit() {
        isTaskFocused = false
    }

    override fun update(dt: Float) {
        val state = SceneManager.timerActions?.getUiState() ?: return
        if (state.isRinging) {
            alarmMarqueeX -= (U * 8).toFloat() * dt
            if (alarmMarqueeX <= -500f) alarmMarqueeX += 500f
        } else {
            alarmMarqueeX = 0f
        }
        nestedTimebox.update(dt)
        taskCursor.update(dt)
    }

    override fun render(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {
        val logicalHeight = SceneManager.logicalHeight
        lastRenderState = null
        val state = SceneManager.timerActions?.getUiState() ?: return
        lastRenderState = state
        // This will setup the palette in Pc98GraphicsHardware dynamically
        EngineThemes.getColors(state.appTheme, state.isBreak)
        val strings = getStrings(state.language)
        
        val isPortrait = UiShellLayout.isTallDisplay
        
        val preferredCy: Float
        val playAreaStartX: Float
        val playAreaW: Float
        val playAreaH: Float
        val inputBaseY = timerInputY(logicalHeight)
        val taskInputH = taskInputHeight(state, strings, playW.toFloat())
        
        if (isPortrait) {
            playAreaStartX = playX.toFloat()
            playAreaW = playW.toFloat()
            playAreaH = playH.toFloat()
            preferredCy = playAreaH / 2f
        } else {
            playAreaStartX = playX.toFloat()
            playAreaW = playW.toFloat()
            playAreaH = playH.toFloat()
            preferredCy = playAreaH / 2f
        }
        val inputY = inputBaseY
        val reservedBtnY = timerControlRowY(playAreaH)
        renderer.paintPlayfield(playAreaStartX, 0f, playAreaStartX + playAreaW, playAreaH)
        val calendarY = reservedBtnY - (U * CALENDAR_PANEL_GAP_CELLS).toFloat() - calendarPanelHeight(state, playAreaW)
        val graphicsBottomY = if (state.activeMode == "calendar") {
            calendarY - U.toFloat()
        } else {
            reservedBtnY - U.toFloat()
        }

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

        // 3. Draw the deterministic nested timebox instrument.
        val renderedGraphicsBottomY = nestedTimebox.render(
            renderer = renderer,
            viewportLeft = playAreaStartX,
            viewportTop = inputY + taskInputH + (U * GRAPHICS_TOP_GAP_CELLS).toFloat(),
            viewportRight = playAreaStartX + playAreaW,
            viewportBottom = graphicsBottomY,
            preferredCenterY = playY.toFloat() + playAreaH * 0.5f,
            outerProgress = outerProgress,
            innerProgress = innerProgress,
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
            strings = strings
        )
        val btnY = if (state.activeMode == "calendar") {
            reservedBtnY
        } else {
            minOf(reservedBtnY, renderedGraphicsBottomY + U.toFloat())
        }
        renderedControlRowY = btnY

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
        renderer.fillRectDither(inputX, inputY, inputX + inputW, inputY + inputH, PaletteIndices.PANEL_DARK, PaletteIndices.PANEL_DARK, SoftDitherPattern.SOLID)
        renderer.strokeRectFrame(inputX, inputY, inputW, inputH, if (isTaskFocused) PaletteIndices.HIGHLIGHT else PaletteIndices.SECONDARY, kind = VectorFrameKind.SMALL)

        if (showPresetBadge) {
            val presetBadgeY = inputY + (inputH - PRESET_BADGE_SIZE) / 2f
            renderer.fillRectDither(presetBadgeX, presetBadgeY, presetBadgeX + PRESET_BADGE_SIZE.toFloat(), presetBadgeY + PRESET_BADGE_SIZE.toFloat(), PaletteIndices.PANEL_DARK, PaletteIndices.PANEL_DARK, SoftDitherPattern.SOLID)
            renderer.strokeRectFrame(presetBadgeX, presetBadgeY, PRESET_BADGE_SIZE.toFloat(), PRESET_BADGE_SIZE.toFloat(), PaletteIndices.SECONDARY, kind = VectorFrameKind.SMALL)
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

        drawActiveCalendarPanel(renderer, state, strings, playAreaStartX, playAreaW, calendarY)

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

    override fun onTouch(x: Float, y: Float, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        val inPlayArea = x >= playX && y >= playY && x < playX + playW && y < playY + playH
        if (action == TouchAction.CANCEL) {
            onInput(x.toInt(), y.toInt(), action, playX, playY, playW, playH)
        } else if (action == TouchAction.UP && inPlayArea) {
            onInput(x.toInt(), y.toInt(), action, playX, playY, playW, playH)
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
        val logicalHeight = SceneManager.logicalHeight
        val isPortrait = UiShellLayout.isTallDisplay

        val fx = x.toFloat()
        val fy = y.toFloat()

        val playAreaStartX = playX.toFloat()
        val playAreaW = playW.toFloat()
        val playAreaH = playH.toFloat()
        val inputBaseY = timerInputY(logicalHeight)
        val strings = getStrings(state.language)
        val inputH = taskInputHeight(state, strings, playAreaW)

        if (state.isRinging) {
            if (SceneManager.timerActionsFromTouchEnabled()) {
                SceneManager.timerActions?.dismissAlarm()
            }
            isTaskFocused = false
            return
        }

        // 1. Task Input Click
        val inputY = inputBaseY
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
        val btnY = if (renderedControlRowY > 0f) renderedControlRowY else timerControlRowY(playAreaH)
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

    private fun timerControlRowY(playAreaH: Float): Float {
        val requestedY = playAreaH - CONTROL_BUTTON_HEIGHT - U * CONTROL_BOTTOM_CLEARANCE_CELLS
        return maxOf(U.toFloat(), requestedY)
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

    private fun timerInputY(logicalHeight: Float): Float {
        return maxOf(logicalHeight / 12f, U * 2f)
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
        renderer.fillRectDither(panelX, y, panelX + panelW, y + panelH, PaletteIndices.PANEL_DARK, PaletteIndices.PANEL_DARK, SoftDitherPattern.SOLID)
        renderer.strokeRectFrame(panelX, y, panelW, panelH, PaletteIndices.SECONDARY)
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
        val fillColor = if (isClicked) PaletteIndices.PANEL else PaletteIndices.PANEL_DARK
        renderer.fillRectDither(x, y, x + width, y + height, fillColor, fillColor, SoftDitherPattern.SOLID)
        renderer.strokeRectFrame(x, y, width, height, PaletteIndices.SECONDARY, kind = VectorFrameKind.SMALL)
        if (!drawIcon) return

        val contentColor = PaletteIndices.TEXT_PRIMARY
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
