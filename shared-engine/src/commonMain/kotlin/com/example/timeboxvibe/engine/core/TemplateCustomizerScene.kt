package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.getStrings
import kotlin.math.abs

private const val U = CANONICAL_UI_UNIT

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

        renderer.paintPlayfield(playAreaStartX, 0f, playAreaStartX + playAreaW, playAreaH)

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
            val fillColor = if (isActive) PaletteIndices.PANEL else PaletteIndices.PANEL_DARK
            renderer.fillRectDither(cardX, currentY, cardX + cardW, currentY + cardH, fillColor, fillColor, SoftDitherPattern.SOLID)
            renderer.strokeRectFrame(cardX, currentY, cardW, cardH, frameColorIndex)

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
            val textColor = if (isActive) PaletteIndices.TEXT_PRIMARY else PaletteIndices.PRIMARY
            val textTop = currentY + (U / 4).toFloat()
            val nameH = ProceduralTextRenderer.measureWrappedHeight(preset.name, maxTextW, nameScale)
            ProceduralTextRenderer.drawWrapped(renderer, preset.name, textLeftX, textTop, maxTextW, textColor, nameScale, uppercase = true)

            if (preset.mode == "calendar") {
                val idColor = if (isActive) PaletteIndices.TEXT_PRIMARY else PaletteIndices.SECONDARY
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
                val idColor = if (isActive) PaletteIndices.TEXT_PRIMARY else PaletteIndices.SECONDARY
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
        renderer.paintPlayfield(playAreaStartX, 0f, playAreaStartX + playAreaW, headerCoverH)

        // Draw header over the cover
        val headerText = strings.presetsTitle
        val headerY = safeTop + (headerRowH - headerTextH) / 2f
        val forgeBtnY = safeTop + (headerRowH - forgeBtnH) / 2f
        ProceduralTextRenderer.drawHeading(renderer, headerText, playAreaStartX + U + (U / 4), headerY, maxHeaderW, PaletteIndices.PRIMARY, ScaledProceduralRenderer.TEXT_SCALE_HEADER)
        renderer.drawButton("ＦＯＲＧＥ", forgeBtnX, forgeBtnY, forgeBtnW, forgeBtnH, isClicked = false, allowTextStacking = false)
        renderer.drawLine(playAreaStartX + (U / 2 + U / 8).toFloat(), headerCoverH - (U / 8).toFloat(), playAreaStartX + playAreaW - (U / 2 + U / 8).toFloat(), headerCoverH - (U / 8).toFloat(), PaletteIndices.SECONDARY, 1f)
    }

    override fun onInput(inputCode: Int) {}

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
    val priColor = if (isActive) PaletteIndices.HIGHLIGHT else PaletteIndices.PRIMARY
    val secColor = PaletteIndices.SECONDARY

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
