package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.moguru.game.model.FoodType
import com.moguru.game.presenter.EatAnimationEvent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EatRecoverySoundEffectComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun eventPlaysOnceAcrossRecompositionAndSameIdUpdates() {
        val player = RecordingSoundEffectPlayer()
        val activeEvent = mutableStateOf<EatAnimationEvent?>(event(31L))
        val recomposition = mutableIntStateOf(0)
        composeRule.setContent {
            Box(Modifier.testTag("eat-sound-${recomposition.intValue}")) {
                EatRecoverySoundEffect(activeEvent.value, player)
            }
        }

        composeRule.runOnIdle {
            assertEquals(listOf(AndroidSoundEffect.EAT_RECOVERY), player.played)
            recomposition.intValue += 1
        }
        composeRule.runOnIdle {
            assertEquals(listOf(AndroidSoundEffect.EAT_RECOVERY), player.played)
            activeEvent.value = activeEvent.value?.copy(foodType = FoodType.FROG)
        }
        composeRule.runOnIdle {
            assertEquals(listOf(AndroidSoundEffect.EAT_RECOVERY), player.played)
            activeEvent.value = event(32L)
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(AndroidSoundEffect.EAT_RECOVERY, AndroidSoundEffect.EAT_RECOVERY),
                player.played,
            )
        }
    }

    private fun event(id: Long): EatAnimationEvent =
        EatAnimationEvent(
            id = id,
            playerId = 0,
            foodType = FoodType.MOLE_CRICKET,
            requestedRecovery = 3,
            actualRecovery = 3,
            healthBefore = 8,
            healthAfter = 11,
        )

    private class RecordingSoundEffectPlayer : AndroidSoundEffectPlayer {
        val played = mutableListOf<AndroidSoundEffect>()

        override fun play(effect: AndroidSoundEffect) {
            played += effect
        }

        override fun setVolume(volume: Float) = Unit

        override fun close() = Unit
    }
}
