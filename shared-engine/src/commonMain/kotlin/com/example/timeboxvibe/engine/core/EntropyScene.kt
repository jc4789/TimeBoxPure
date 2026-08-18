package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import com.example.timeboxvibe.engine.getStrings

private const val U = CANONICAL_UI_UNIT

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
        
        val isPortrait = UiShellLayout.isTallDisplay
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
        val isDown = action == TouchAction.DOWN
        if (!isDown) return

        val state = SceneManager.timerActions?.getUiState() ?: return
        val isPortrait = UiShellLayout.isTallDisplay
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
