package com.example.timeboxvibe

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.example.timeboxvibe.ui.main.Pc98SurfaceView
import com.example.timeboxvibe.engine.core.SceneManager
import com.example.timeboxvibe.engine.core.BlockOverlayScene
import com.example.timeboxvibe.engine.core.PlatformInputTrigger
import android.widget.FrameLayout
import android.view.ViewGroup

class BlockOverlayActivity : ComponentActivity(), PlatformInputTrigger {

    private var surfaceView: Pc98SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure it shows over lock screen and keeps screen on
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

        val view = Pc98SurfaceView(this)
        surfaceView = view

        val rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootFrame.addView(view)
        setContentView(rootFrame)

        BlockOverlayScene.onReturnClicked = {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()
        }

        // Setup SceneManager to run the block overlay scene
        val activeActions = SceneManager.timerActions
        if (activeActions != null) {
            SceneManager.init(activeActions, this)
        }
        SceneManager.switchScene(BlockOverlayScene)
    }

    override fun triggerKeyboard() {}
    override fun performHapticFeedback(type: Int) {
        runOnUiThread {
            surfaceView?.performHapticFeedback(type)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Intercept back button
    }
}
