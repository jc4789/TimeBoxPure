package com.example.timeboxvibe.engine.core

import com.example.timeboxvibe.engine.AppStrings
import com.example.timeboxvibe.engine.SongCatalog
import com.example.timeboxvibe.engine.getStrings
import kotlin.math.abs

private const val U = CANONICAL_UI_UNIT

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
        renderer.paintPlayfield(playAreaStartX, 0f, playAreaStartX + playAreaW, playAreaH)

        currentY = safeTop + scrollY
        drawSettingsRows(renderer, state, strings)

    }

    override fun onInput(inputCode: Int) {}

    override fun onTouch(x: Float, y: Float, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {
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

    }

    private fun beginSettingsLayout(logicalWidth: Float, logicalHeight: Float) {
        playAreaStartX = UiShellLayout.contentX
        playAreaW = UiShellLayout.contentWidth
        playAreaH = UiShellLayout.contentHeight

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
