package com.moguru.game.android

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.presenter.EatAnimationEvent
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EatRecoverySoundEffectComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun eventPlaysOnceAcrossRecompositionAndSameIdUpdates() {
        val player = RecordingSoundEffectPlayer()
        val viewModel = AndroidGameViewModel()
        val activeEvent = mutableStateOf<EatAnimationEvent?>(event(31L))
        val recomposition = mutableIntStateOf(0)
        composeRule.setContent {
            Box(Modifier.testTag("eat-sound-${recomposition.intValue}")) {
                EatRecoverySoundEffect(
                    activeEvent.value,
                    player,
                    viewModel::eatRecoverySoundEffectFor,
                )
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

    @Test
    fun capturedFoodRecoverySoundDoesNotReplayWhenEffectIsRecreatedForTheSameViewModel() {
        val player = RecordingSoundEffectPlayer()
        val viewModel = eatingViewModel()
        val activeEvent = requireNotNull(viewModel.uiState.value.eatAnimation)
        val showEffect = mutableStateOf(true)
        composeRule.setContent {
            if (showEffect.value) {
                Box(Modifier.testTag("active-eat-sound-effect")) {
                    EatRecoverySoundEffect(
                        activeEvent,
                        player,
                        viewModel::eatRecoverySoundEffectFor,
                    )
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(listOf(AndroidSoundEffect.EAT_RECOVERY), player.played)
            assertEquals(activeEvent, viewModel.uiState.value.eatAnimation)
            showEffect.value = false
        }
        composeRule.onNodeWithTag("active-eat-sound-effect").assertDoesNotExist()
        composeRule.runOnIdle { showEffect.value = true }
        composeRule.onNodeWithTag("active-eat-sound-effect").assertExists()
        composeRule.runOnIdle {
            assertEquals(activeEvent, viewModel.uiState.value.eatAnimation)
            assertEquals(listOf(AndroidSoundEffect.EAT_RECOVERY), player.played)
        }
    }

    private fun eatingViewModel(): AndroidGameViewModel {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        val currentPlayer = controller.currentPlayer!!
        repeat(4) { currentPlayer.reduceHealth(isOnSurface = false) }
        val source = Position(2, 2)
        currentPlayer.moveTo(source)
        val engine = controller.engine!!
        while (engine.foodsAt(source).isNotEmpty()) engine.removeFoodAt(source, 0)
        engine.placeFoodAt(source, FoodCard.createDummyCards(FoodType.MOLE_CRICKET).first())
        engine.advancePhase()
        engine.advancePhase()
        check(controller.captureCurrentPositionImmediately().success)
        check(engine.currentPhase == TurnPhase.DECIDE)
        viewModel.eat()
        return viewModel
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
