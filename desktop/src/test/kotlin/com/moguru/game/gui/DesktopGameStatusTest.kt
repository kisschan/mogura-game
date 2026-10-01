package com.moguru.game.gui

import com.moguru.game.engine.GameState
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.presenter.MoguraGameController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopGameStatusTest {
    @Test
    fun `elimination result names the winner instead of requesting another turn`() {
        val controller = MoguraGameController().apply { startNewGame(2) }
        repeat(3) { controller.engine!!.advancePhase() }
        repeat(12) { controller.currentPlayer!!.reduceHealth(false) }
        assertTrue(controller.finishTurn().success)
        assertEquals(GameState.FINISHED, controller.engine!!.gameState)
        assertFinishedWinner(controller)
    }

    @Test
    fun `score result names the winner and removes the active turn title`() {
        val controller = MoguraGameController().apply { startNewGame(2) }
        controller.currentPlayer!!.apply {
            carryFood(FoodCard(FoodType.FROG, emptyMap(), isFaceDown = false))
            storeFood()
        }
        assertEquals(GameState.FINISHED, controller.engine!!.checkGameOver())
        assertFinishedWinner(controller)
    }

    @Test
    fun `all eliminated result shows draw instead of a player turn`() {
        val controller = MoguraGameController().apply { startNewGame(2) }
        controller.engine!!.players.forEach { player -> repeat(13) { player.reduceHealth(false) } }
        assertEquals(GameState.FINISHED, controller.engine!!.checkGameOver())
        assertTrue(desktopGameStatusText(controller).contains("ドロー"))
        assertEquals("ゲーム終了", desktopCurrentPlayerDisplay(controller).titleText)
        assertEquals(null, desktopCurrentPlayerDisplay(controller).playerId)
    }

    private fun assertFinishedWinner(controller: MoguraGameController) {
        val winner = controller.engine!!.checkWinCondition()!!
        assertTrue(desktopGameStatusText(controller).contains("${winner.name} の勝利"))
        assertFalse(desktopGameStatusText(controller).contains("ターンを終了"))
        val display = desktopCurrentPlayerDisplay(controller)
        assertEquals(winner.id, display.playerId)
        assertEquals("${winner.name} の勝利", display.titleText)
        assertEquals("ゲーム終了", display.phaseText)
    }
}
