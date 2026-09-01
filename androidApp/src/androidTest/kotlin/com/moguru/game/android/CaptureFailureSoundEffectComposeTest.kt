package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.CaptureAnimationEvent
import com.moguru.game.presenter.CaptureOutcomeKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureFailureSoundEffectComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun escapedEventPlaysOnceAcrossRecompositionAndSameIdUpdates() {
        val player = RecordingSoundEffectPlayer()
        val activeEvent = mutableStateOf<CaptureAnimationEvent?>(event(31L, CaptureOutcomeKind.ESCAPED))
        val recomposition = mutableIntStateOf(0)
        composeRule.setContent {
            Box(Modifier.testTag("capture-sound-${recomposition.intValue}")) {
                CaptureFailureSoundEffect(activeEvent.value, player)
            }
        }

        composeRule.runOnIdle {
            assertEquals(listOf(AndroidSoundEffect.CAPTURE_FAILURE), player.played)
            recomposition.intValue += 1
        }
        composeRule.runOnIdle {
            assertEquals(listOf(AndroidSoundEffect.CAPTURE_FAILURE), player.played)
            activeEvent.value = activeEvent.value?.copy(foodType = FoodType.EARTHWORM)
        }
        composeRule.runOnIdle {
            assertEquals(listOf(AndroidSoundEffect.CAPTURE_FAILURE), player.played)
            activeEvent.value = event(32L, CaptureOutcomeKind.ESCAPED)
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(AndroidSoundEffect.CAPTURE_FAILURE, AndroidSoundEffect.CAPTURE_FAILURE),
                player.played,
            )
        }
    }

    @Test
    fun capturedEventsNeverPlayFailureSound() {
        val player = RecordingSoundEffectPlayer()
        val activeEvent = mutableStateOf<CaptureAnimationEvent?>(event(41L, CaptureOutcomeKind.CAPTURED))
        composeRule.setContent {
            CaptureFailureSoundEffect(activeEvent.value, player)
        }

        composeRule.runOnIdle {
            assertEquals(emptyList<AndroidSoundEffect>(), player.played)
            activeEvent.value = event(42L, CaptureOutcomeKind.CAPTURED)
        }
        composeRule.runOnIdle {
            assertEquals(emptyList<AndroidSoundEffect>(), player.played)
        }
    }

    private fun event(id: Long, kind: CaptureOutcomeKind): CaptureAnimationEvent =
        CaptureAnimationEvent(
            id = id,
            kind = kind,
            playerId = 0,
            foodType = FoodType.BEETLE_LARVA,
            source = Position(2, 2),
            foodIndex = 0,
            destination = if (kind == CaptureOutcomeKind.ESCAPED) Position(2, 1) else null,
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
