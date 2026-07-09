package com.moguru.game.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val musicSettingsRepository by lazy {
        defaultAndroidMusicSettingsRepository(this)
    }
    private val backgroundMusic by lazy {
        AndroidBackgroundMusicController(
            player = defaultAndroidBackgroundMusicPlayer(this),
            initialSettings = musicSettingsRepository.load(),
        )
    }
    private val soundEffects by lazy {
        defaultAndroidSoundEffectPlayer(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var musicSettings by remember { mutableStateOf(musicSettingsRepository.load()) }
            LaunchedEffect(musicSettings) {
                backgroundMusic.onMusicSettingsChanged(musicSettings)
            }
            MoguraGameScreen(
                soundEffects = soundEffects,
                onGameStartedChanged = backgroundMusic::onGameStartedChanged,
                musicSettings = musicSettings,
                onMusicSettingsChanged = { updatedSettings ->
                    musicSettings = musicSettingsRepository.save(updatedSettings)
                },
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
