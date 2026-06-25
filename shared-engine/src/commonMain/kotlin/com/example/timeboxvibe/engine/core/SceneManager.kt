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
        isDrainingInput = true
        try {
            drainInputQueue()
            drainTouchBuffer(touchBuffer, touchCount)
        } finally {
            isDrainingInput = false
        }
        applyPendingSceneSwitch()
        activeScene?.update(dt)
    }

    fun render(renderer: ScaledProceduralRenderer, logicalWidth: Float, logicalHeight: Float) {
        val scene = activeScene ?: return
        val playX = RetroHudComponent.playAreaStartX(logicalWidth).toInt()
        val playY = 0
        val playW = RetroHudComponent.playAreaWidth(logicalWidth).toInt()
        val playH = RetroHudComponent.playAreaHeight(logicalWidth, logicalHeight).toInt()
        scene.render(renderer, playX, playY, playW, playH)
    }

    fun setLogicalBounds(width: Float, height: Float) {
        logicalWidth = width
        logicalHeight = height
    }

    fun currentSceneName(): String {
        return sceneName(activeScene)
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
        var inputCode = inputQueue.pop()
        while (inputCode != EMPTY_INPUT_SENTINEL) {
            onInput(inputCode)
            inputCode = inputQueue.pop()
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
            val logTouch = actionCode == ENGINE_TOUCH_DOWN || actionCode == ENGINE_TOUCH_UP
            val beforeName = if (logTouch) currentSceneName() else ""
            dispatchTouch(
                logicalX,
                logicalY,
                actionCode
            )
            if (logTouch) {
                val afterName = if (pendingScene != null) sceneName(pendingScene) else currentSceneName()
                println("touchInput rawX=$rawX rawY=$rawY logicalX=$logicalX logicalY=$logicalY engineAction=$actionCode currentSceneNameBefore=$beforeName currentSceneNameAfter=$afterName")
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
    private const val EMPTY_INPUT_SENTINEL = -1
    private const val TOUCH_EVENT_SLOT_COUNT = 5
    private const val TOUCH_SLOT_LOGICAL_X = 0
    private const val TOUCH_SLOT_LOGICAL_Y = 1
    private const val TOUCH_SLOT_RAW_X = 2
    private const val TOUCH_SLOT_RAW_Y = 3
    private const val TOUCH_SLOT_ACTION = 4
}
