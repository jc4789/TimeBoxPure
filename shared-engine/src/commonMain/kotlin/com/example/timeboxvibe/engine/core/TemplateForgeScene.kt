package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import com.example.timeboxvibe.engine.getStrings
import kotlin.math.abs

private const val U = CANONICAL_UI_UNIT

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

        val isPortrait = UiShellLayout.isTallDisplay
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

        renderer.paintPlayfield(playAreaStartX, 0f, playAreaStartX + playAreaW, playAreaH)

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

        renderer.paintPlayfield(playAreaStartX, 0f, playAreaStartX + playAreaW, headerCoverH)
        ProceduralTextRenderer.drawHeading(renderer, strings.forgeTitle, contentX, titleY, titleW, PaletteIndices.PRIMARY, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        renderer.drawButton(strings.cancel, buttonX, cancelY, buttonW, cancelH, isClicked = false, allowTextStacking = true)
        renderer.drawLine(playAreaStartX + (U / 2).toFloat(), headerCoverH, playAreaStartX + playAreaW - (U / 2).toFloat(), headerCoverH, PaletteIndices.SECONDARY, 1f)

        val saveH = ScaledProceduralRenderer.measureButtonHeight(strings.saveTemplate, contentW, rowH, allowTextStacking = true)
        val saveY = playAreaH - saveH - (U * SAVE_GAP_CELLS).toFloat()
        if (isForgeValid()) {
            renderer.drawButton(strings.saveTemplate, contentX, saveY, contentW, saveH, isClicked = false, allowTextStacking = true)
        } else {
            renderer.paintRectFrame(contentX, saveY, contentW, saveH, PaletteIndices.PANEL_DARK, PaletteIndices.SECONDARY)
            val saveTextW = maxOf(U.toFloat(), contentW - U.toFloat())
            val saveTextH = ProceduralTextRenderer.measureWrappedHeight(strings.saveTemplate, saveTextW)
            ProceduralTextRenderer.drawWrapped(renderer, strings.saveTemplate, contentX + (U / 2).toFloat(), saveY + (saveH - saveTextH) / 2f, saveTextW, PaletteIndices.SECONDARY, alignment = ProceduralTextRenderer.ALIGN_CENTER)
        }

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

    override fun onTouch(x: Float, y: Float, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
        val playAreaH = playH.toFloat()
        val logicalHeight = SceneManager.logicalHeight
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
                        onInput(x.toInt(), y.toInt(), TouchAction.UP, playX, playY, playW, playH)
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
        val isUp = action == TouchAction.UP
        if (!isUp) return
        val state = SceneManager.timerActions?.getUiState() ?: return
        val strings = getStrings(state.language)

        val isPortrait = UiShellLayout.isTallDisplay
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
        return UiShellLayout.contentWidth
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
        renderer.paintRectFrame(controlX, fieldY, controlW, fieldH, PaletteIndices.PANEL_DARK, if (isFocused) PaletteIndices.PRIMARY else PaletteIndices.SECONDARY, VectorFrameKind.SMALL)
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
