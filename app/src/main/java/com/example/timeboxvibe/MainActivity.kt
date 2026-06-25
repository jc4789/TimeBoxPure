package com.example.timeboxvibe

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.timeboxvibe.ui.main.MainScreenViewModel
import com.example.timeboxvibe.ui.main.Pc98SurfaceView
import com.example.timeboxvibe.platform.android.AndroidHeadlessInputBridge
import com.example.timeboxvibe.engine.core.PlatformInputTrigger
import com.example.timeboxvibe.engine.core.SceneManager
import com.example.timeboxvibe.engine.core.ActiveTimerScene
import android.widget.FrameLayout
import android.view.ViewGroup
import android.view.View
import android.os.Build
import android.view.WindowManager
import com.example.timeboxvibe.ui.main.dataStore
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity(), PlatformInputTrigger {

    private lateinit var viewModel: MainScreenViewModel
    private var inputBridge: AndroidHeadlessInputBridge? = null
    private var surfaceView: Pc98SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime notification permission on startup (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Wake screen and show over keyguard when alarm triggers and activity is launched
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Instantiate viewModel at the activity level
        viewModel = MainScreenViewModel.Factory(applicationContext, applicationContext.dataStore)
            .create(MainScreenViewModel::class.java)

        val view = Pc98SurfaceView(this)
        surfaceView = view

        val bridge = AndroidHeadlessInputBridge(this) { }
        inputBridge = bridge

        val rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Add SurfaceView
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootFrame.addView(view)

        // Attach Headless Input Bridge EditText (adds to rootFrame)
        bridge.attachToWindow(rootFrame)

        setContentView(rootFrame)

        // Initialize SceneManager
        SceneManager.init(viewModel, this)
        SceneManager.switchScene(ActiveTimerScene)
    }

    override fun triggerKeyboard() {
        runOnUiThread {
            inputBridge?.triggerKeyboard()
        }
    }

    override fun performHapticFeedback(type: Int) {
        runOnUiThread {
            val androidType = when (type) {
                com.example.timeboxvibe.engine.core.EngineHaptics.TICK -> android.view.HapticFeedbackConstants.CLOCK_TICK
                com.example.timeboxvibe.engine.core.EngineHaptics.CLICK -> android.view.HapticFeedbackConstants.KEYBOARD_PRESS
                com.example.timeboxvibe.engine.core.EngineHaptics.IMPACT -> android.view.HapticFeedbackConstants.LONG_PRESS
                else -> android.view.HapticFeedbackConstants.CLOCK_TICK
            }
            surfaceView?.performHapticFeedback(androidType)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.checkExactAlarmPermission()
        }
    }
}
