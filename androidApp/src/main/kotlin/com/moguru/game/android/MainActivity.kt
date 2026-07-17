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
    private val audioSettingsRepository by lazy {
        defaultAndroidAudioSettingsRepository(this)
    }
    private val initialAudioSettings by lazy {
        audioSettingsRepository.load()
    }
    private val backgroundMusic by lazy {
        AndroidBackgroundMusicController(
            player = defaultAndroidBackgroundMusicPlayer(this),
            initialSettings = initialAudioSettings,
        )
    }
    private val soundEffects by lazy {
        defaultAndroidSoundEffectPlayer(
            context = this,
            initialVolume = initialAudioSettings.normalizedSoundEffectVolume,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var audioSettings by remember { mutableStateOf(initialAudioSettings) }
            LaunchedEffect(audioSettings) {
                backgroundMusic.onAudioSettingsChanged(audioSettings)
                soundEffects.setVolume(audioSettings.normalizedSoundEffectVolume)
            }
            MoguraGameScreen(
                soundEffects = soundEffects,
                onGameStartedChanged = backgroundMusic::onGameStartedChanged,
                audioSettings = audioSettings,
                onAudioSettingsChanged = { updatedSettings ->
                    audioSettings = audioSettingsRepository.save(updatedSettings)
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
