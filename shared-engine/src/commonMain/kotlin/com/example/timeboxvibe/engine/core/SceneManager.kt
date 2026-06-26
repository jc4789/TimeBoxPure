package com.example.timeboxvibe.engine.core

import kotlin.concurrent.Volatile

const val ENGINE_TOUCH_DOWN = 1
const val ENGINE_TOUCH_UP = 2
const val ENGINE_TOUCH_CANCEL = 3
const val ENGINE_TOUCH_MOVE = 4

enum class SceneId { ACTIVE_TIMER, TEMPLATES, TEMPLATE_EDITOR, SETTINGS, ENTROPY }

sealed class SceneCommand {
    data object None : SceneCommand()
    data class GoTo(val sceneId: SceneId) : SceneCommand()
}

object SceneManager {
    @Volatile
    var activeScene: Scene? = null
        private set

    private val inputQueue = ConcurrentIntegerQueue(1024)
    private var pendingScene: Scene? = null
    private var pendingPayload: Any? = null
    private var isDrainingInput = false
    private var logicalWidth = 0f
    private var logicalHeight = 0f
    private var inputDrainOverflowLogCooldownSeconds = 0f
    private var debugLogUpdateThisFrame = false
    private var debugRenderAfterSwitch = false
    private var debugLanguageChangedRecently = false
    private var debugNextTouchDispatch = false
    private var debugNextTouchUpdate = false
    private var debugStringsAfterLanguage = false

    var timerActions: TimerActions? = null
    var inputTrigger: PlatformInputTrigger? = null

    private val sceneRegistry = mapOf(
        SceneId.ACTIVE_TIMER to ActiveTimerScene,
        SceneId.TEMPLATES to TemplateCustomizerScene,
        SceneId.TEMPLATE_EDITOR to TemplateForgeScene,
        SceneId.SETTINGS to SettingsScene,
        SceneId.ENTROPY to EntropyScene
    )

    fun executeCommand(cmd: SceneCommand) {
        when (cmd) {
            is SceneCommand.GoTo -> {
                val scene = sceneRegistry[cmd.sceneId] ?: return
                performSceneSwitch(scene)
            }
            SceneCommand.None -> {}
        }
    }

    fun init(actions: TimerActions, trigger: PlatformInputTrigger) {
        this.timerActions = actions
        this.inputTrigger = trigger
    }

    fun switchScene(newScene: Scene, payload: Any? = null) {
        if (isDrainingInput) {
            pendingScene = newScene
            pendingPayload = payload
            return
        }
        performSceneSwitch(newScene, payload)
    }

    private fun performSceneSwitch(newScene: Scene, payload: Any? = null) {
        val old = activeScene
        println("SWITCH_BEGIN old=${sceneName(old)} new=${sceneName(newScene)}")
        try {
            println("SWITCH_BEFORE_ON_EXIT")
            old?.onExit()
            println("SWITCH_AFTER_ON_EXIT")

            activeScene = newScene
            debugRenderAfterSwitch = true
            println("SWITCH_SET_ACTIVE")

            println("SWITCH_BEFORE_ON_ENTER")
            newScene.onEnter(payload)
            println("SWITCH_AFTER_ON_ENTER")
        } catch (t: Throwable) {
            println("SWITCH_THROW new=${sceneName(newScene)} error=${t::class.simpleName}:${t.message}")
            activeScene = SettingsScene
            debugRenderAfterSwitch = true
        }
        println("SWITCH_END active=${currentSceneName()}")
    }

    fun update(dt: Float) {
        update(dt, emptyTouchBuffer, 0)
    }

    fun update(dt: Float, touchBuffer: IntArray, touchCount: Int) {
        val logThisFrame = touchCount > 0
        debugLogUpdateThisFrame = logThisFrame
        if (inputDrainOverflowLogCooldownSeconds > 0f) {
            inputDrainOverflowLogCooldownSeconds -= dt
        }
        isDrainingInput = true
        try {
            drainInputQueue()
            drainTouchBuffer(touchBuffer, touchCount)
        } finally {
            isDrainingInput = false
        }
        val hudCmd = RetroHudComponent.consumeSceneCommand()
        if (hudCmd !is SceneCommand.None) {
            executeCommand(hudCmd)
        }
        if (debugLogUpdateThisFrame) {
            println("BEFORE APPLY_PENDING_SCENE")
        }
        applyPendingSceneSwitch()
        if (debugLogUpdateThisFrame) {
            println("AFTER APPLY_PENDING_SCENE")
            println("BEFORE ACTIVE_UPDATE scene=${currentSceneName()}")
        }
        if (debugNextTouchUpdate) {
            println("NEXT_TOUCH_BEFORE_UPDATE scene=${currentSceneName()}")
        }
        activeScene?.update(dt)
        if (debugNextTouchUpdate) {
            println("NEXT_TOUCH_AFTER_UPDATE scene=${currentSceneName()}")
            debugNextTouchUpdate = false
            debugLanguageChangedRecently = false
        }
        if (debugLogUpdateThisFrame) {
            println("AFTER ACTIVE_UPDATE scene=${currentSceneName()}")
            debugLogUpdateThisFrame = false
        }
    }

    fun render(renderer: ScaledProceduralRenderer, logicalWidth: Float, logicalHeight: Float) {
        renderer.drawRect(0f, 0f, logicalWidth, logicalHeight, PaletteIndices.BG)
        val logThisFrame = debugRenderAfterSwitch
        if (logThisFrame) {
            println("RENDER_BEFORE scene=${currentSceneName()}")
        }
        val scene = activeScene
        if (scene == null) {
            if (logThisFrame) {
                println("RENDER_AFTER scene=null")
                debugRenderAfterSwitch = false
            }
            return
        }
        val playX = RetroHudComponent.playAreaStartX(logicalWidth).toInt()
        val playY = 0
        val playW = RetroHudComponent.playAreaWidth(logicalWidth).toInt()
        val playH = RetroHudComponent.playAreaHeight(logicalWidth, logicalHeight).toInt()
        scene.render(renderer, playX, playY, playW, playH)
        if (logThisFrame) {
            println("RENDER_AFTER scene=${currentSceneName()}")
            debugRenderAfterSwitch = false
        }
    }

    fun setLogicalBounds(width: Float, height: Float) {
        logicalWidth = width
        logicalHeight = height
    }

    fun currentSceneName(): String {
        return sceneName(activeScene)
    }

    fun timerActionsFromTouchEnabled(): Boolean {
        return true
    }

    fun triggerKeyboard() {
        if (DEBUG_DISABLE_PLATFORM_EFFECTS) return
        inputTrigger?.triggerKeyboard()
    }

    fun performHapticFeedback(type: Int) {
        if (DEBUG_DISABLE_PLATFORM_EFFECTS) return
        inputTrigger?.performHapticFeedback(type)
    }

    fun markLanguageChanged() {
        debugLanguageChangedRecently = true
        debugStringsAfterLanguage = true
    }

    fun logStringsAfterLanguageChange(scene: String, language: String) {
        if (!debugStringsAfterLanguage) return
        println("STRINGS language=$language scene=$scene")
        debugStringsAfterLanguage = false
    }

    private fun dispatchTouch(x: Int, y: Int, actionCode: Int) {
        val scene = activeScene ?: return
        val sceneAction = when (actionCode) {
            ENGINE_TOUCH_DOWN -> TouchAction.DOWN
            ENGINE_TOUCH_MOVE -> TouchAction.MOVE
            ENGINE_TOUCH_UP -> TouchAction.UP
            ENGINE_TOUCH_CANCEL -> TouchAction.CANCEL
            else -> return
        }
        val playX = RetroHudComponent.playAreaStartX(logicalWidth).toInt()
        val playY = 0
        val playW = RetroHudComponent.playAreaWidth(logicalWidth).toInt()
        val playH = RetroHudComponent.playAreaHeight(logicalWidth, logicalHeight).toInt()
        scene.onTouch(x, y, sceneAction, playX, playY, playW, playH)
    }

    fun onInput(inputCode: Int) {
        activeScene?.onInput(inputCode)
    }

    fun enqueueInput(inputCode: Int) {
        inputQueue.push(inputCode)
    }

    private fun drainInputQueue() {
        var drained = 0
        var inputCode = inputQueue.pop()
        while (inputCode != EMPTY_INPUT_SENTINEL && drained < MAX_INPUT_DRAIN_PER_FRAME) {
            onInput(inputCode)
            drained++
            if (drained < MAX_INPUT_DRAIN_PER_FRAME) {
                inputCode = inputQueue.pop()
            }
        }
        if (drained >= MAX_INPUT_DRAIN_PER_FRAME && inputDrainOverflowLogCooldownSeconds <= 0f) {
            println("inputDrain capped maxPerFrame=$MAX_INPUT_DRAIN_PER_FRAME")
            inputDrainOverflowLogCooldownSeconds = INPUT_DRAIN_OVERFLOW_LOG_INTERVAL_SECONDS
        }
    }

    private fun drainTouchBuffer(touchBuffer: IntArray, touchCount: Int) {
        var index = 0
        var offset = 0
        while (index < touchCount) {
            try {
                val logicalX = touchBuffer[offset + TOUCH_SLOT_LOGICAL_X]
                val logicalY = touchBuffer[offset + TOUCH_SLOT_LOGICAL_Y]
                val actionCode = touchBuffer[offset + TOUCH_SLOT_ACTION]
                val sceneBefore = currentSceneName()
                val playX = RetroHudComponent.playAreaStartX(logicalWidth).toInt()
                val playY = 0
                val playW = RetroHudComponent.playAreaWidth(logicalWidth).toInt()
                val playH = RetroHudComponent.playAreaHeight(logicalWidth, logicalHeight).toInt()
                RetroHudComponent.onTouch(logicalX, logicalY, playX, playY, playW, playH)
                if (DEBUG_TOUCH_MODE == TOUCH_MODE_HUD_ONLY) {
                    RetroHudComponent.onTouchEvent(logicalX, logicalY, engineTouchAction(actionCode), playX, playY, playW, playH)
                } else if (!DEBUG_DISABLE_SCENE_TOUCH_DISPATCH && (DEBUG_TOUCH_MODE == TOUCH_MODE_SCENE_NO_TIMER_ACTIONS || DEBUG_TOUCH_MODE == TOUCH_MODE_FULL)) {
                    dispatchTouch(logicalX, logicalY, actionCode)
                }
            } catch (e: Throwable) {
                println("TOUCH_DRAIN_THROW scene=${currentSceneName()} action=${engineTouchAction(touchBuffer[offset + TOUCH_SLOT_ACTION])} index=$index error=${e::class.simpleName}:${e.message}")
            }
            offset += TOUCH_EVENT_SLOT_COUNT
            index++
        }
    }

    private fun engineTouchAction(actionCode: Int): Int {
        return when (actionCode) {
            ENGINE_TOUCH_DOWN -> TouchAction.DOWN
            ENGINE_TOUCH_MOVE -> TouchAction.MOVE
            ENGINE_TOUCH_UP -> TouchAction.UP
            ENGINE_TOUCH_CANCEL -> TouchAction.CANCEL
            else -> actionCode
        }
    }

    private fun applyPendingSceneSwitch() {
        val scene = pendingScene ?: return
        val payload = pendingPayload
        pendingScene = null
        pendingPayload = null
        performSceneSwitch(scene, payload)
    }

    private fun sceneName(scene: Scene?): String {
        return when (scene) {
            MainMenuScene -> "MainMenuScene"
            ActiveTimerScene -> "ActiveTimerScene"
            TemplateCustomizerScene -> "TemplateCustomizerScene"
            TemplateForgeScene -> "TemplateForgeScene"
            SettingsScene -> "SettingsScene"
            EntropyScene -> "EntropyScene"
            BlockOverlayScene -> "BlockOverlayScene"
            null -> "null"
            else -> "UnknownScene"
        }
    }
    
    fun requiresContinuousRendering(): Boolean {
        return TimerEngine.state == TimerEngine.ACTIVE
    }

    private val emptyTouchBuffer = IntArray(0)
    private const val TOUCH_MODE_LOG_ONLY = 0
    private const val TOUCH_MODE_HUD_ONLY = 1
    private const val TOUCH_MODE_SCENE_NO_TIMER_ACTIONS = 2
    private const val TOUCH_MODE_FULL = 3
    private const val DEBUG_TOUCH_MODE = TOUCH_MODE_FULL
    private const val DEBUG_DISABLE_PLATFORM_EFFECTS = false
    private const val DEBUG_DISABLE_SCENE_TOUCH_DISPATCH = false
    private const val MAX_INPUT_DRAIN_PER_FRAME = 64
    private const val INPUT_DRAIN_OVERFLOW_LOG_INTERVAL_SECONDS = 1f
    private const val EMPTY_INPUT_SENTINEL = -1
    private const val TOUCH_EVENT_SLOT_COUNT = 5
    private const val TOUCH_SLOT_LOGICAL_X = 0
    private const val TOUCH_SLOT_LOGICAL_Y = 1
    private const val TOUCH_SLOT_RAW_X = 2
    private const val TOUCH_SLOT_RAW_Y = 3
    private const val TOUCH_SLOT_ACTION = 4
}
