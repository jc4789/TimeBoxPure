package com.example.timeboxvibe.platform.android

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.example.timeboxvibe.engine.core.EngineInputCodes
import com.example.timeboxvibe.engine.core.SceneManager

class AndroidHeadlessInputBridge(
    context: Context,
    private val onInputQueued: () -> Unit
) {
    private val DUMMY_TEXT = " " // Keeps the field populated so backspace stays alive

    val platformProxyView = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        imeOptions = EditorInfo.IME_ACTION_DONE
        background = null
        
        // Seed the initial dummy character so the field is never empty
        setText(DUMMY_TEXT)
        setSelection(1) // Keep cursor at the end

        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEND) {
                enqueueInput(EngineInputCodes.CMD_ENTER)
                true
            } else {
                false
            }
        }

        setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        enqueueInput(EngineInputCodes.CMD_LEFT)
                        return@setOnKeyListener true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        enqueueInput(EngineInputCodes.CMD_RIGHT)
                        return@setOnKeyListener true
                    }
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        enqueueInput(EngineInputCodes.CMD_ENTER)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        addTextChangedListener(object : TextWatcher {
            private var isInternalReset = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isInternalReset || s == null) return

                // TRAP A: Backspace clicked (Length shrunk from our dummy spacer anchor)
                if (s.length < DUMMY_TEXT.length) {
                    enqueueInput(EngineInputCodes.CMD_BACKSPACE)
                    resetFieldState()
                    return
                }

                // TRAP B: Characters or strings added
                if (count > 0) {
                    var i = start
                    while (i < start + count) {
                        val codePoint = Character.codePointAt(s, i)
                        enqueueInput(codePoint)
                        i += Character.charCount(codePoint)
                    }
                    resetFieldState()
                }
            }

            override fun afterTextChanged(s: Editable?) {}

            private fun resetFieldState() {
                isInternalReset = true
                setText(DUMMY_TEXT)
                setSelection(1) // Snap cursor back to index 1
                isInternalReset = false
            }
        })
    }

    fun attachToWindow(rootViewGroup: ViewGroup) {
        // Add as a 1x1 pixel or invisible layout token purely so it can gain focus
        platformProxyView.layoutParams = ViewGroup.LayoutParams(1, 1)
        rootViewGroup.addView(platformProxyView)
    }

    fun triggerKeyboard() {
        platformProxyView.requestFocus()
        val imm = platformProxyView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm?.showSoftInput(platformProxyView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun enqueueInput(inputCode: Int) {
        SceneManager.enqueueInput(inputCode)
        onInputQueued()
    }
}
