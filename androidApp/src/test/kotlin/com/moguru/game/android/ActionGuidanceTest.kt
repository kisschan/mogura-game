package com.moguru.game.android

import com.moguru.game.engine.TurnPhase
import com.moguru.game.model.FoodCard
import com.moguru.game.model.FoodType
import com.moguru.game.model.HoleTile
import com.moguru.game.model.Position
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.presenter.ActionAvailability
import com.moguru.game.presenter.MoguraGameController
import com.moguru.game.util.FixedDiceRoller
import com.moguru.game.util.FixedShuffler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionGuidanceTest {
    @Test
    fun `skip labels identify the action being omitted in every phase`() {
        val expected = mapOf(
            TurnPhase.DIG to "移動へ進む",
            TurnPhase.MOVE to "移動しない",
            TurnPhase.CAPTURE to "捕獲しない",
            TurnPhase.DECIDE to "タベずに終了",
            TurnPhase.END to "手番終了",
        )

        expected.forEach { (phase, label) ->
            assertEquals(label, AndroidVisibleAction.SKIP.displayLabel(phase), "phase=$phase")
        }
        assertEquals("手番終了", AndroidVisibleAction.END_TURN.displayLabel())
    }

    @Test
    fun `end turn accessibility explains skipping remaining actions before the end phase`() {
        listOf(TurnPhase.MOVE, TurnPhase.CAPTURE).forEach { phase ->
            val label = AndroidVisibleAction.END_TURN.accessibilityLabel(phase)
            assertTrue(label.contains("残りの行動"), label)
            assertTrue(label.contains("終了"), label)
        }
        val endLabel = AndroidVisibleAction.END_TURN.accessibilityLabel(TurnPhase.END)
        assertTrue(endLabel.contains("手番終了"), endLabel)
        assertFalse(endLabel.contains("残りの行動"), endLabel)
        assertTrue(AndroidVisibleAction.SKIP.accessibilityLabel(TurnPhase.MOVE).contains("移動しない"))
        assertTrue(AndroidVisibleAction.SKIP.accessibilityLabel(TurnPhase.CAPTURE).contains("捕獲しない"))
    }

    @Test
    fun `end phase exposes one end action even when the core permits both skip and end`() {
        val (controller, viewModel) = fixture()
        repeat(3) { controller.engine!!.advancePhase() }
        viewModel.selectStartPlayer(0)

        val state = viewModel.uiState.value
        assertEquals(TurnPhase.END, state.playState.actionAvailability.activePhase)
        assertTrue(state.playState.actionAvailability.canSkip)
        assertTrue(state.playState.actionAvailability.canEndTurn)
        assertEquals(listOf(AndroidVisibleAction.END_TURN), state.visibleActions)
    }

    @Test
    fun `own nest food decision offers eating and a single end action`() {
        val (controller, viewModel) = fixture()
        val player = controller.currentPlayer!!
        player.carryFood(food(FoodType.MOLE_CRICKET))
        player.storeFood()
        player.moveTo(Position(1, 1))
        controller.engine!!.boardState.placeTile(Position(1, 1), horizontalTile())
        controller.engine!!.advancePhase()

        viewModel.onCellClicked(player.nestPosition)

        val state = viewModel.uiState.value
        assertEquals(TurnPhase.DECIDE, state.playState.actionAvailability.activePhase)
        assertTrue(state.playState.actionAvailability.canSkip)
        assertTrue(state.playState.actionAvailability.canEndTurn)
        assertEquals(listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.END_TURN), state.visibleActions)
        val instruction = actionBarInstruction(state)
        assertTrue(instruction.contains("タベる"), instruction)
        assertTrue(instruction.contains("終了"), instruction)
        assertFalse(instruction.contains("レンコウ"), instruction)
        assertFalse(instruction.contains("強奪"), instruction)
    }

    @Test
    fun `move and capture retain distinct skip and end actions when both are legal`() {
        val (controller, viewModel) = fixture()
        viewModel.onCellClicked(Position(1, 1))
        viewModel.selectRotation(Rotation.DEG_90)
        viewModel.confirmDigPlacement()
        assertEquals(TurnPhase.MOVE, controller.engine!!.currentPhase)
        assertEquals(
            listOf(AndroidVisibleAction.SKIP, AndroidVisibleAction.END_TURN),
            viewModel.uiState.value.visibleActions,
        )

        val (_, captureViewModel) = captureFixture()
        assertEquals(
            listOf(AndroidVisibleAction.CAPTURE, AndroidVisibleAction.SKIP, AndroidVisibleAction.END_TURN),
            captureViewModel.uiState.value.visibleActions,
        )
    }

    @Test
    fun `dig guidance retains drawn tile shape despite a later message or log`() {
        val state = fixture().second.uiState.value
        val instruction = actionBarInstruction(state)
        assertTrue(instruction.contains("山札"), instruction)
        assertTrue(instruction.contains("L字"), instruction)
        assertTrue(instruction.contains("掘る"), instruction)
        assertTrue(instruction.contains("選"), instruction)
        assertEquals(
            instruction,
            actionBarInstruction(state.copy(lastMessage = "直前の操作結果", logs = listOf("古い手番の記録"))),
        )
    }

    @Test
    fun `pending dig guidance explains choosing a tile and direction then placing it`() {
        val (_, viewModel) = fixture()
        viewModel.onCellClicked(Position(1, 1))
        assertTrue(viewModel.uiState.value.showDigControls)

        val instruction = actionBarInstruction(viewModel.uiState.value)

        assertTrue(instruction.contains("タイル"), instruction)
        assertTrue(instruction.contains("向き"), instruction)
        assertTrue(instruction.contains("置く"), instruction)
        assertFalse(instruction.contains("掘る場所"), instruction)
    }

    @Test
    fun `move guidance describes selecting a destination and the explicit confirm button`() {
        val (_, viewModel) = fixture()
        viewModel.onCellClicked(Position(1, 1))
        viewModel.selectRotation(Rotation.DEG_90)
        viewModel.confirmDigPlacement()
        val state = viewModel.uiState.value

        val instruction = actionBarInstruction(state)

        assertTrue(instruction.contains("移動先"), instruction)
        assertTrue(instruction.contains("選"), instruction)
        assertTrue(instruction.contains("このマスへ移動"), instruction)
        assertEquals(
            instruction,
            actionBarInstruction(state.copy(lastMessage = "タイルを置きました。", logs = listOf("次は捕獲の古い記録"))),
        )
    }

    @Test
    fun `capture guidance identifies capture while a captured decision offers only eat or carry`() {
        val (_, viewModel) = captureFixture()
        assertTrue(actionBarInstruction(viewModel.uiState.value).contains("捕獲"))
        viewModel.capture()
        viewModel.finishDiceRoulette()
        viewModel.finishCaptureAnimation(requireNotNull(viewModel.uiState.value.captureAnimation).event.id)

        val state = viewModel.uiState.value
        assertEquals(listOf(AndroidVisibleAction.EAT, AndroidVisibleAction.CARRY), state.visibleActions)
        val instruction = actionBarInstruction(state)
        assertTrue(instruction.contains("タベる"), instruction)
        assertTrue(instruction.contains("レンコウ"), instruction)
        assertFalse(instruction.contains("強奪"), instruction)
        assertFalse(instruction.contains("手番終了"), instruction)
    }

    @Test
    fun `robbery decision guidance names the available robbery action`() {
        val state = decisionState(listOf(AndroidVisibleAction.ROB))

        val instruction = actionBarInstruction(state)

        assertTrue(instruction.contains("強奪"), instruction)
        assertFalse(instruction.contains("タベる"), instruction)
        assertFalse(instruction.contains("レンコウ"), instruction)
        assertFalse(instruction.contains("手番終了"), instruction)
    }

    @Test
    fun `roulette and capture playback never prompt actions blocked by the animation`() {
        val (_, viewModel) = captureFixture()
        viewModel.capture()
        assertTrue(viewModel.uiState.value.playState.diceRouletteActive)
        assertProcessingInstruction(actionBarInstruction(viewModel.uiState.value))

        viewModel.finishDiceRoulette()
        requireNotNull(viewModel.uiState.value.captureAnimation)
        assertProcessingInstruction(actionBarInstruction(viewModel.uiState.value))
    }

    @Test
    fun `eat and turn consumption playback never repeat a completed decision prompt`() {
        val (_, viewModel) = captureFixture()
        viewModel.capture()
        viewModel.finishDiceRoulette()
        viewModel.finishCaptureAnimation(requireNotNull(viewModel.uiState.value.captureAnimation).event.id)
        viewModel.eat()
        val eat = requireNotNull(viewModel.uiState.value.eatAnimation)
        assertProcessingInstruction(actionBarInstruction(viewModel.uiState.value))

        viewModel.finishEatAnimation(eat.id)
        requireNotNull(viewModel.uiState.value.turnConsumptionAnimation)
        assertProcessingInstruction(actionBarInstruction(viewModel.uiState.value))
    }

    @Test
    fun `completed game guidance offers a rematch instead of another turn action`() {
        val state = fixture().second.uiState.value.copy(
            gameResult = AndroidGameResultUiState(0, "モグオ", emptyList()),
        )

        val instruction = actionBarInstruction(state)

        assertTrue(instruction.contains("再戦"), instruction)
        assertFalse(instruction.contains("手番終了"), instruction)
        assertFalse(instruction.contains("掘る"), instruction)
    }

    private fun assertProcessingInstruction(instruction: String) {
        assertTrue(listOf("中", "結果", "待").any(instruction::contains), instruction)
        listOf("選んで", "選択して", "このマスへ移動", "タベるか", "レンコウするか", "手番を終了してください").forEach { unavailableAction ->
            assertFalse(instruction.contains(unavailableAction), instruction)
        }
    }

    private fun decisionState(actions: List<AndroidVisibleAction>): AndroidGameUiState {
        val state = fixture().second.uiState.value
        return state.copy(
            visibleActions = actions,
            playState = state.playState.copy(
                actionAvailability = ActionAvailability(
                    canCapture = false,
                    canEat = AndroidVisibleAction.EAT in actions,
                    canCarry = AndroidVisibleAction.CARRY in actions,
                    canRob = AndroidVisibleAction.ROB in actions,
                    canSkip = AndroidVisibleAction.SKIP in actions,
                    canEndTurn = AndroidVisibleAction.END_TURN in actions,
                    activePhase = TurnPhase.DECIDE,
                ),
            ),
        )
    }

    private fun captureFixture(): Pair<MoguraGameController, AndroidGameViewModel> {
        val (controller, viewModel) = fixture()
        val source = Position(2, 2)
        controller.currentPlayer!!.moveTo(source)
        val engine = controller.engine!!
        while (engine.foodsAt(source).isNotEmpty()) engine.removeFoodAt(source, 0)
        engine.placeFoodAt(source, food(FoodType.BEETLE_LARVA))
        repeat(2) { engine.advancePhase() }
        viewModel.selectStartPlayer(0)
        return controller to viewModel
    }

    private fun fixture(): Pair<MoguraGameController, AndroidGameViewModel> {
        val controller = MoguraGameController(FixedDiceRoller(listOf(6)), FixedShuffler())
        val viewModel = AndroidGameViewModel(controller)
        viewModel.startNewGame(2)
        return controller to viewModel
    }

    private fun food(type: FoodType): FoodCard = FoodCard.createDummyCards(type).first()

    private fun horizontalTile(): HoleTile = HoleTile(TileShape.STRAIGHT).rotate(Rotation.DEG_90).flip()
}
