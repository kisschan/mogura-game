package com.moguru.game.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private val audioSettingsRepository by lazy {
        defaultAndroidAudioSettingsRepository(this)
    }
    private val initialAudioSettings by lazy {
        audioSettingsRepository.load()
    }
    private val backgroundMusic by lazy {
        AndroidBackgroundMusicController(
            createPlayer = { track -> defaultAndroidBackgroundMusicPlayer(this, track) },
            initialSettings = initialAudioSettings,
        )
    }
    private val soundEffects by lazy {
        defaultAndroidSoundEffectPlayer(
            context = this,
            initialVolume = initialAudioSettings.normalizedSoundEffectVolume,
        )
    }
    // Held by the Activity as well, so onResume/onPause can reach it without a composition.
    private val gameViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == AndroidGameViewModel::class.java)
                return AndroidGameViewModel(
                    saveRepository = GameSaveDependencies.repositoryFactory(applicationContext),
                    saveIo = GameSaveDependencies.ioFactory(),
                    cpuScheduler = MainHandlerCpuStepScheduler(),
                    tutorialProgressRepository = TutorialProgressDependencies.repositoryFactory(applicationContext),
                ) as T
            }
        })[AndroidGameViewModel::class.java]
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
                viewModel = gameViewModel,
                soundEffects = soundEffects,
                onBackgroundMusicStateChanged = backgroundMusic::onGameStateChanged,
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

    override fun onResume() {
        super.onResume()
        gameViewModel.setCpuHostResumed(true)
    }

    override fun onPause() {
        // Told directly: composition may already be paused once the app leaves the foreground.
        gameViewModel.setCpuHostResumed(false)
        super.onPause()
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
