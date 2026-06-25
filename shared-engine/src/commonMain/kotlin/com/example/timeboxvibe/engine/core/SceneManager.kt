package com.example.timeboxvibe.engine.core

import kotlin.concurrent.Volatile

const val ENGINE_TOUCH_DOWN = 1
const val ENGINE_TOUCH_UP = 2
const val ENGINE_TOUCH_CANCEL = 3

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
    private var debugLogRenderThisFrame = false

    var timerActions: TimerActions? = null
    var inputTrigger: PlatformInputTrigger? = null

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
        old?.onExit()
        activeScene = newScene
        newScene.onEnter(payload)
    }

    fun update(dt: Float) {
        update(dt, emptyTouchBuffer, 0)
    }

    fun update(dt: Float, touchBuffer: IntArray, touchCount: Int) {
        val logThisFrame = touchCount > 0
        debugLogUpdateThisFrame = logThisFrame
        if (logThisFrame) {
            debugLogRenderThisFrame = true
        }
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
        applyPendingSceneSwitch()
        if (debugLogUpdateThisFrame) {
            println("update BEFORE scene=${currentSceneName()}")
        }
        activeScene?.update(dt)
        if (debugLogUpdateThisFrame) {
            println("update AFTER scene=${currentSceneName()}")
            debugLogUpdateThisFrame = false
        }
    }

    fun render(renderer: ScaledProceduralRenderer, logicalWidth: Float, logicalHeight: Float) {
        val logThisFrame = debugLogRenderThisFrame
        if (logThisFrame) {
            println("render BEFORE scene=${currentSceneName()}")
        }
        val scene = activeScene
        if (scene == null) {
            if (logThisFrame) {
                println("render AFTER scene=null")
                debugLogRenderThisFrame = false
            }
            return
        }
        val playX = RetroHudComponent.playAreaStartX(logicalWidth).toInt()
        val playY = 0
        val playW = RetroHudComponent.playAreaWidth(logicalWidth).toInt()
        val playH = RetroHudComponent.playAreaHeight(logicalWidth, logicalHeight).toInt()
        scene.render(renderer, playX, playY, playW, playH)
        if (logThisFrame) {
            println("render AFTER scene=${currentSceneName()}")
            debugLogRenderThisFrame = false
        }
    }

    fun setLogicalBounds(width: Float, height: Float) {
        logicalWidth = width
        logicalHeight = height
    }

    fun currentSceneName(): String {
        return sceneName(activeScene)
    }

    fun triggerKeyboard() {
        if (DEBUG_DISABLE_PLATFORM_EFFECTS) return
        inputTrigger?.triggerKeyboard()
    }

    fun performHapticFeedback(type: Int) {
        if (DEBUG_DISABLE_PLATFORM_EFFECTS) return
        inputTrigger?.performHapticFeedback(type)
    }

    private fun dispatchTouch(x: Int, y: Int, actionCode: Int) {
        val scene = activeScene ?: return
        val sceneAction = when (actionCode) {
            ENGINE_TOUCH_DOWN -> TouchAction.DOWN
            ENGINE_TOUCH_UP, ENGINE_TOUCH_CANCEL -> TouchAction.UP
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
            val logicalX = touchBuffer[offset + TOUCH_SLOT_LOGICAL_X]
            val logicalY = touchBuffer[offset + TOUCH_SLOT_LOGICAL_Y]
            val rawX = touchBuffer[offset + TOUCH_SLOT_RAW_X]
            val rawY = touchBuffer[offset + TOUCH_SLOT_RAW_Y]
            val actionCode = touchBuffer[offset + TOUCH_SLOT_ACTION]
            println("touchDrain scene=${currentSceneName()} action=$actionCode rawX=$rawX rawY=$rawY logicalX=$logicalX logicalY=$logicalY dispatch=${!DEBUG_DISABLE_SCENE_TOUCH_DISPATCH}")
            if (!DEBUG_DISABLE_SCENE_TOUCH_DISPATCH) {
                val beforeName = currentSceneName()
                println("sceneTouch BEFORE scene=$beforeName action=$actionCode x=$logicalX y=$logicalY")
                dispatchTouch(
                    logicalX,
                    logicalY,
                    actionCode
                )
                val afterName = if (pendingScene != null) sceneName(pendingScene) else currentSceneName()
                println("sceneTouch AFTER scene=$afterName")
            }
            offset += TOUCH_EVENT_SLOT_COUNT
            index++
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
    private const val DEBUG_DISABLE_PLATFORM_EFFECTS = true
    private const val DEBUG_DISABLE_SCENE_TOUCH_DISPATCH = true
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
