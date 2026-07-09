package com.moguru.game.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private val backgroundMusic by lazy {
        AndroidBackgroundMusicController(defaultAndroidBackgroundMusicPlayer(this))
    }
    private val soundEffects by lazy {
        defaultAndroidSoundEffectPlayer(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoguraGameScreen(
                soundEffects = soundEffects,
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
        soundEffects.close()
        backgroundMusic.close()
        super.onDestroy()
    }
}
