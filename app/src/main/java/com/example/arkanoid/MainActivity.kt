package com.example.arkanoid

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button

class MainActivity : Activity() {
    private lateinit var gameView: GameView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Keep controls clear of system bars on Android 15 with target SDK 35.
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        gameView = findViewById(R.id.gameView)
        val pause = findViewById<Button>(R.id.btnPause)
        findViewById<Button>(R.id.btnNewGame).setOnClickListener {
            gameView.resetGame(); pause.setText(R.string.pause)
        }
        pause.setOnClickListener {
            gameView.togglePause()
            pause.setText(if (gameView.isPaused) R.string.resume else R.string.pause)
        }
    }
    override fun onResume() { super.onResume(); gameView.resumeRendering() }
    override fun onPause() { gameView.pauseRendering(); super.onPause() }
}
