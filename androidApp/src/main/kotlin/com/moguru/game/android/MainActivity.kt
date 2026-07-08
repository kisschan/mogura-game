package com.moguru.game.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private val backgroundMusic by lazy {
        AndroidBackgroundMusicController(defaultAndroidBackgroundMusicPlayer(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoguraGameScreen(
                onGameStartedChanged = backgroundMusic::onGameStartedChanged,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        backgroundMusic.onForegrounded()
    }

    override fun onStop() {
        backgroundMusic.onBackgrounded()
        super.onStop()
    }

    override fun onDestroy() {
        backgroundMusic.close()
        super.onDestroy()
    }
}
