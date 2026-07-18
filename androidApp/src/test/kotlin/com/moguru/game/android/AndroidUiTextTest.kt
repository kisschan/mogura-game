package com.moguru.game.android

import androidx.compose.ui.unit.dp
import com.moguru.game.model.FoodType
import com.moguru.game.model.Position
import com.moguru.game.model.CellType
import com.moguru.game.model.Rotation
import com.moguru.game.model.TileShape
import com.moguru.game.engine.TurnPhase
import com.moguru.game.presenter.CaptureTargetDisplay
import com.moguru.game.presenter.CaptureOutcomeDisplay
import com.moguru.game.presenter.CaptureOutcomeKind
import com.moguru.game.presenter.DigCandidateDisplay
import com.moguru.game.presenter.DigTileChoice
import com.moguru.game.presenter.RobberyTargetDisplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidUiTextTest {
    @Test
    fun `nest labels include board location`() {
        val label = nestDisplayLabel(Position(0, 1))

        assertEquals("巣A", label.name)
        assertEquals("左上", label.location)
    }

    @Test
    fun `used setup choices name the player that already owns them`() {
        assertEquals("P2使用中", setupUsedByLabel(seatIndex = 1))
    }

    @Test
    fun `used nest labels keep name location and owner visible together`() {
        val text = nestChoiceVisualLines(Position(0, 1), usedByLabel = "P2使用中")

        assertEquals(listOf("巣A", "左上", "P2使用中"), text)
    }

    @Test
    fun `setup choice state matches selectable swap behavior`() {
        assertEquals("選択中", setupChoiceStateDescription(selected = true, usedByLabel = null))
        assertEquals("未選択", setupChoiceStateDescription(selected = false, usedByLabel = null))
        assertEquals(
            "P2使用中。選ぶと担当を入れ替えます",
            setupChoiceStateDescription(selected = false, usedByLabel = "P2使用中"),
        )
    }

    @Test
    fun `setup selected palette is shared and not danger red`() {
        val colors = setupSelectedChoiceColors()

        assertEquals(0xFF158A45.toInt(), colors.borderArgb)
        assertEquals(0xFF35BC67.toInt(), colors.containerArgb)
        assertEquals(0xFF102F1B.toInt(), colors.contentArgb)
        assertFalse(colors.borderArgb == 0xFFE64B3F.toInt())
        assertFalse(colors.containerArgb == 0xFFFFD9D3.toInt())
    }

    @Test
    fun `start player semantics include seat name and state`() {
        assertEquals(
            "P1 モグタを先手にする、選択中",
            startPlayerSemanticsLabel(seatIndex = 0, name = "モグタ", selected = true),
        )
    }

    @Test
    fun `only highlighted cells are exposed as board primary actions`() {
        val inactiveCell = AndroidBoardCellUiState(
            position = Position(5, 4),
            cellType = CellType.UNDERGROUND,
            tile = AndroidTileUiState(TileShape.STRAIGHT, Rotation.DEG_0, isFaceDown = false),
            foods = emptyList(),
            players = emptyList(),
            highlight = null,
        )
        val highlightedCell = inactiveCell.copy(highlight = AndroidHighlightTone.DIG)

        assertFalse(isBoardPrimaryActionCell(inactiveCell, TurnPhase.DIG))
        assertEquals(true, isBoardPrimaryActionCell(highlightedCell, TurnPhase.DIG))
        assertFalse(isBoardPrimaryActionCell(highlightedCell, TurnPhase.DECIDE))
    }

    @Test
    fun `compact play screens keep the board small enough for controls`() {
        assertEquals(300.dp, playBoardMaxWidthForHeight(640.dp))
        assertEquals(420.dp, playBoardMaxWidthForHeight(900.dp))
    }

    @Test
    fun `action labels use plain player-facing Japanese`() {
        assertEquals("タベる", AndroidVisibleAction.EAT.displayLabel())
        assertEquals("レンコウ", AndroidVisibleAction.CARRY.displayLabel())
        assertEquals("タベる（食べる）", AndroidVisibleAction.EAT.accessibilityLabel())
        assertEquals("レンコウ（巣へ持ち帰る）", AndroidVisibleAction.CARRY.accessibilityLabel())
    }

    @Test
    fun `audio settings labels expose both volume levels and mute state`() {
        assertEquals("35%", audioVolumePercentLabel(AndroidAudioSettings().bgmVolume))
        assertEquals("45%", audioVolumePercentLabel(AndroidAudioSettings().soundEffectVolume))
        assertEquals(
            "音量設定: BGM 35%、効果音 45%",
            audioSettingsButtonContentDescription(AndroidAudioSettings()),
        )
        assertEquals(
            "音量設定: BGM ミュート、効果音 80%",
            audioSettingsButtonContentDescription(
                AndroidAudioSettings(bgmVolume = 0f, soundEffectVolume = 0.8f),
            ),
        )
    }

    @Test
    fun `board player token keeps accessibility name without visible board label`() {
        assertNull(boardPlayerVisibleLabel("モグタ"))
        assertEquals("モグタの駒", boardPlayerContentDescription("モグタ", isCurrent = false))
        assertEquals("モグタの駒、現在の手番", boardPlayerContentDescription("モグタ", isCurrent = true))
    }

    @Test
    fun `single board target becomes one primary action`() {
        val target = AndroidBoardCellUiState(
            position = Position(1, 1),
            cellType = CellType.UNDERGROUND,
            tile = null,
            foods = emptyList(),
            players = emptyList(),
            highlight = AndroidHighlightTone.DIG,
        )

        assertEquals(
            MobilePrimaryBoardAction(label = "このタイルを掘る", position = Position(1, 1)),
            singlePrimaryBoardAction(listOf(target), TurnPhase.DIG),
        )
        assertNull(
            singlePrimaryBoardAction(
                listOf(target, target.copy(position = Position(2, 1))),
                TurnPhase.DIG,
            ),
        )
    }

    @Test
    fun `single board action is preferred over generic visible actions`() {
        val action = MobilePrimaryBoardAction(label = "このマスへ移動", position = Position(1, 1))

        assertTrue(preferSingleBoardAction(action, listOf(AndroidVisibleAction.SKIP)))
        assertFalse(preferSingleBoardAction(null, listOf(AndroidVisibleAction.SKIP)))
    }

    @Test
    fun `single board capture keeps optional end turn without duplicate capture action`() {
        assertEquals(
            listOf(AndroidVisibleAction.END_TURN),
            visibleActionsAfterSingleBoardAction(
                TurnPhase.CAPTURE,
                listOf(AndroidVisibleAction.CAPTURE, AndroidVisibleAction.END_TURN),
            ),
        )
        assertEquals(
            listOf(AndroidVisibleAction.SKIP, AndroidVisibleAction.END_TURN),
            visibleActionsAfterSingleBoardAction(
                TurnPhase.MOVE,
                listOf(AndroidVisibleAction.SKIP, AndroidVisibleAction.END_TURN),
            ),
        )
    }

    @Test
    fun `latest event strip is intentionally one line`() {
        assertEquals(1, EVENT_STRIP_MAX_LINES)
    }

    @Test
    fun `face down capture targets include their order`() {
        val target = CaptureTargetDisplay(
            index = 1,
            type = FoodType.EARTHWORM,
            isFaceDown = true,
            selected = true,
            enabled = true,
        )

        assertEquals("選択中: 裏向き 2/3", captureTargetLabel(target, total = 3))
        assertEquals(
            "捕獲対象: 選択中: 裏向き 2/3",
            captureTargetSummary(
                listOf(
                    target.copy(index = 0, selected = false),
                    target,
                    target.copy(index = 2, selected = false),
                ),
            ),
        )
        assertNull(captureTargetSummary(emptyList()))
    }

    @Test
    fun `dig candidate accessibility labels describe action and state`() {
        val candidate = DigCandidateDisplay(
            choice = DigTileChoice.DRAWN,
            label = "山札",
            shape = TileShape.T_SHAPE,
            selected = false,
            enabled = true,
        )

        assertEquals("山札、T字タイル、未選択", digCandidateSemanticLabel(candidate))
        assertEquals("山札を選ぶ", digCandidateActionLabel(candidate))
    }

    @Test
    fun `dig candidate visual tile resource follows candidate shape`() {
        val candidate = DigCandidateDisplay(
            choice = DigTileChoice.DRAWN,
            label = "山札",
            shape = TileShape.T_SHAPE,
            selected = false,
            enabled = true,
        )

        assertEquals(R.drawable.tile_t_shape, digCandidateTileRes(candidate))
        assertNull(digCandidateTileRes(candidate.copy(shape = null)))
    }

    @Test
    fun `capture target accessibility labels describe action and target`() {
        val target = CaptureTargetDisplay(
            index = 1,
            type = FoodType.EARTHWORM,
            isFaceDown = false,
            selected = true,
            enabled = true,
        )

        assertEquals("捕獲対象、選択中: ミミズ 2/3、選択できます", captureTargetSemanticLabel(target, total = 3))
        assertEquals("捕獲対象を選ぶ: 選択中: ミミズ 2/3", captureTargetActionLabel(target, total = 3))
    }

    @Test
    fun `robbery target labels include owner name`() {
        val target = RobberyTargetDisplay(
            index = 0,
            ownerPlayerId = 1,
            ownerName = "モグタ",
            type = FoodType.MOLE_CRICKET,
            selected = true,
            enabled = true,
        )

        assertEquals("モグタの巣", robberyOwnerLabel(target))
        assertEquals("選択中: ケラ 1/1", robberyTargetLabel(target, total = 1))
        assertEquals("強奪対象: モグタの巣 / 選択中: ケラ 1/1", robberyTargetSummary(listOf(target)))
        assertEquals("強奪対象、モグタの巣、選択中: ケラ 1/1、選択できます", robberyTargetSemanticLabel(target, total = 1))
        assertEquals("強奪対象を選ぶ: モグタの巣 / 選択中: ケラ 1/1", robberyTargetActionLabel(target, total = 1))
    }

    @Test
    fun `result banner keeps dice result and next action visible`() {
        val colors = resultBannerColors(CaptureOutcomeKind.CAPTURED)
        val text = resultBannerText(
            CaptureOutcomeDisplay(
                kind = CaptureOutcomeKind.CAPTURED,
                diceRoll = 6,
                message = "モグオ が ミミズ を捕獲しました。タベるか、レンコウするか選んでください。",
            ),
        )

        assertEquals(0xFFE8FFF0.toInt(), colors.containerArgb)
        assertTrue(text!!.contains("ダイス: 6"))
        assertTrue(text.contains("レンコウ"))
    }

    @Test
    fun `result banner does not cap lines because outcome and next action are critical`() {
        assertEquals(4, RESULT_BANNER_MAX_LINES)
    }

    @Test
    fun `event strip presentation uses result banner styling for capture outcomes`() {
        val outcome = CaptureOutcomeDisplay(
            kind = CaptureOutcomeKind.CAPTURED,
            diceRoll = 6,
            message = "モグオ が ミミズ を捕獲しました。",
        )
        val colors = resultBannerColors(CaptureOutcomeKind.CAPTURED)
        val presentation = eventStripPresentation(outcome)

        assertEquals(colors.containerArgb, presentation.containerArgb)
        assertEquals(colors.borderArgb, presentation.borderArgb)
        assertEquals(colors.contentArgb, presentation.contentArgb)
        assertEquals(RESULT_BANNER_MAX_LINES, presentation.maxLines)
        assertEquals(RESULT_EVENT_STRIP_HEIGHT, presentation.stripHeight)
    }

    @Test
    fun `event strip presentation keeps normal events compact`() {
        val presentation = eventStripPresentation(null)

        assertNull(presentation.containerArgb)
        assertNull(presentation.borderArgb)
        assertEquals(0xFF4B3826.toInt(), presentation.contentArgb)
        assertEquals(EVENT_STRIP_MAX_LINES, presentation.maxLines)
        assertEquals(EVENT_STRIP_HEIGHT, presentation.stripHeight)
    }

    @Test
    fun `result banner supports no escape captures`() {
        val text = resultBannerText(
            CaptureOutcomeDisplay(
                kind = CaptureOutcomeKind.CAPTURED,
                diceRoll = null,
                message = "モグオ が カブトムシの幼虫 を捕獲しました。タベるか、レンコウするか選んでください。",
            ),
        )

        assertEquals("逃走なし　モグオ が カブトムシの幼虫 を捕獲しました。タベるか、レンコウするか選んでください。", text)
    }

    @Test
    fun `game result copy names winner and avoids impossible turn action`() {
        val result = AndroidGameResultUiState(
            winnerPlayerId = 0,
            winnerName = "モグオ",
            players = listOf(
                AndroidGameResultPlayerUiState(
                    playerId = 0,
                    name = "モグオ",
                    score = 4,
                    health = 12,
                    isEliminated = false,
                    isWinner = true,
                ),
                AndroidGameResultPlayerUiState(
                    playerId = 1,
                    name = "モグタ",
                    score = 0,
                    health = 0,
                    isEliminated = true,
                    isWinner = false,
                ),
            ),
        )

        assertEquals("モグオ の勝利", gameResultTitle(result))
        assertEquals("モグオ の勝利。全員の成績を確認してください。", gameResultEventText(result))
        assertEquals("ゲーム終了。新規から再戦できます。", gameResultActionInstruction())
        assertEquals("勝者", gameResultPlayerStatus(result.players[0]))
        assertEquals("脱落", gameResultPlayerStatus(result.players[1]))
        assertFalse(gameResultActionInstruction().contains("ターン終了"))
    }

    @Test
    fun `result banner keeps capture result even when later logs arrive`() {
        val colors = resultBannerColors(CaptureOutcomeKind.ESCAPED)
        val text = resultBannerText(
            CaptureOutcomeDisplay(
                kind = CaptureOutcomeKind.ESCAPED,
                diceRoll = 1,
                message = "ミミズ はダイス 1 で 上に逃げました。",
            ),
        )

        assertEquals(0xFFFFF1CF.toInt(), colors.containerArgb)
        assertEquals("ダイス: 1　ミミズ はダイス 1 で 上に逃げました。", text)
    }

    @Test
    fun `roulette copy uses unified capture vocabulary`() {
        assertEquals("逃走なし", rouletteRevealStatus(emptyList()))
        assertEquals("捕獲する", roulettePrimaryActionLabel(emptyList()))
        assertEquals("ダイスで逃走判定", rouletteRevealStatus(listOf(1, 2)))
        assertEquals("ダイスを振る", roulettePrimaryActionLabel(listOf(1, 2)))
        assertEquals("ダイスを止める", rouletteStopActionLabel())
    }
}
