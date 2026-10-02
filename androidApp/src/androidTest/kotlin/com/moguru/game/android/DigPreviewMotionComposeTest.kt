package com.moguru.game.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.DigTileChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DigPreviewMotionComposeTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 1f
    })

    @Test
    fun initialRotationHasNoEntranceAnimationAndWrapMovesClockwise() {
        var rotation by mutableStateOf(Rotation.DEG_270)
        var displayed = 0f
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            displayed = rememberDigPreviewRotation(Position(2, 2), TileShape.L_SHAPE,
                DigTileChoice.REVEALED, 1, rotation).value
        }
        composeRule.runOnIdle {
            assertEquals(270f, displayed, 0.01f)
            rotation = Rotation.DEG_0
        }
        composeRule.mainClock.advanceTimeBy(64)
        composeRule.runOnIdle { assertTrue(displayed > 270f && displayed < 360f) }
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.runOnIdle { assertEquals(360f, displayed, 0.01f) }
    }

    @Test
    fun rapidChangesFollowTheLatestTargetWithoutReversing() {
        var rotation by mutableStateOf(Rotation.DEG_0)
        var displayed = 0f
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            displayed = rememberDigPreviewRotation(Position(2, 2), TileShape.L_SHAPE,
                DigTileChoice.REVEALED, 1, rotation).value
        }
        var previous = 0f
        listOf(Rotation.DEG_90, Rotation.DEG_180, Rotation.DEG_270, Rotation.DEG_0).forEach {
            composeRule.runOnIdle { rotation = it }
            composeRule.mainClock.advanceTimeBy(48)
            composeRule.runOnIdle {
                assertTrue(displayed >= previous)
                previous = displayed
            }
        }
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.runOnIdle { assertEquals(360f, displayed, 0.01f) }
    }

    @Test
    fun changingCandidatePositionShapeOrGameResetsToItsOwnAngle() {
        var position by mutableStateOf(Position(2, 2))
        var choice by mutableStateOf(DigTileChoice.REVEALED)
        var shape by mutableStateOf(TileShape.L_SHAPE)
        var generation by mutableStateOf(1L)
        var rotation by mutableStateOf(Rotation.DEG_270)
        var displayed = 0f
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            displayed = rememberDigPreviewRotation(position, shape, choice, generation, rotation).value
        }
        composeRule.runOnIdle { rotation = Rotation.DEG_0 }
        composeRule.mainClock.advanceTimeBy(48)
        composeRule.runOnIdle { choice = DigTileChoice.DRAWN; rotation = Rotation.DEG_90 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(90f, displayed, 0.01f) }
        composeRule.runOnIdle { position = Position(3, 2); rotation = Rotation.DEG_180 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(180f, displayed, 0.01f) }
        composeRule.runOnIdle { shape = TileShape.T_SHAPE; rotation = Rotation.DEG_270 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(270f, displayed, 0.01f) }
        composeRule.runOnIdle { generation++; rotation = Rotation.DEG_0 }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(0f, displayed, 0.01f) }
    }
}
