package com.example.timeboxvibe.engine.core

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
    fun renderOverlay(renderer: ScaledProceduralRenderer, playX: Int, playY: Int, playW: Int, playH: Int) {}
    fun onInput(inputCode: Int)
    fun onTouch(x: Float, y: Float, action: Int, playX: Int, playY: Int, playW: Int, playH: Int)
    fun onInput(x: Int, y: Int, action: Int, playX: Int, playY: Int, playW: Int, playH: Int) {}
}
