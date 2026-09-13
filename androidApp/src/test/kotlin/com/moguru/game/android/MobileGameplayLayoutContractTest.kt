package com.moguru.game.android

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MobileGameplayLayoutContractTest {
    @Test
    fun `board fits a stable space reservation on compact mobile viewports`() {
        listOf(
            360.dp to 740.dp,
            375.dp to 812.dp,
            390.dp to 844.dp,
            412.dp to 915.dp,
            430.dp to 932.dp,
        ).forEach { (width, height) ->
            // Reservations cover different font metrics, never the current event's measured height.
            listOf(120.dp, 220.dp, 340.dp).forEach { reservedActionHeight ->
                val availableHeight = height - MOBILE_PLAY_VERTICAL_PADDING * 2 -
                    MOBILE_PLAY_HUD_HEIGHT - MOBILE_PLAY_GAP - reservedActionHeight
                val boardWidth = fittedBoardWidth(width, availableHeight)
                val boardHeight = boardWidth / BOARD_ASPECT_RATIO

                assertTrue(boardWidth > 0.dp, "the board must remain visible at $width x $height")
                assertTrue(boardWidth <= width, "the board must fit horizontally")
                assertTrue(boardHeight <= availableHeight, "the board must fit below the HUD and above the full controls")
                assertTrue(boardWidth <= 420.dp, "the board must stay within its maximum width")
            }
        }
    }

    @Test
    fun `board adapts to shorter viewports while keeping its aspect ratio`() {
        val tallViewportBoardWidth = fittedBoardWidth(360.dp, availableHeight = 520.dp)
        val shortViewportBoardWidth = fittedBoardWidth(360.dp, availableHeight = 320.dp)

        assertEquals(360.dp, tallViewportBoardWidth)
        assertEquals(240.dp, shortViewportBoardWidth)
        assertTrue(shortViewportBoardWidth < tallViewportBoardWidth)
    }

    @Test
    fun `active gameplay does not use vertical scroll policy`() {
        assertFalse(ACTIVE_GAMEPLAY_USES_VERTICAL_SCROLL)
    }

    @Test
    fun `board fitting handles empty space without creating negative bounds`() {
        assertEquals(0.dp, fittedBoardWidth(360.dp, availableHeight = 0.dp))
        assertEquals(0.dp, fittedBoardWidth(360.dp, availableHeight = (-20).dp))
        assertEquals(0.dp, fittedBoardWidth((-10).dp, availableHeight = 500.dp))
    }

    @Test
    fun `board fitting respects both aspect ratio and maximum width`() {
        assertEquals(300.dp, fittedBoardWidth(390.dp, availableHeight = 400.dp))
        assertEquals(390.dp, fittedBoardWidth(390.dp, availableHeight = 600.dp))
        assertEquals(420.dp, fittedBoardWidth(800.dp, availableHeight = 1000.dp))
    }

    @Test
    fun `action buttons preserve readable height when accessibility font size increases`() {
        listOf(1f, 1.2f, 1.5f).forEach { fontScale ->
            val ordinaryButtonHeight = compactActionButtonHeight(hasEndTurnHint = false, fontScale)
            val hintedButtonHeight = compactActionButtonHeight(hasEndTurnHint = true, fontScale)
            assertTrue(ordinaryButtonHeight >= 44.dp * fontScale)
            assertTrue(hintedButtonHeight >= (15.dp * 2 + 12.dp * 2) * fontScale)
            assertTrue(hintedButtonHeight >= ordinaryButtonHeight)
        }
    }

    @Test
    fun `mobile action controls are single row only`() {
        assertEquals(1, COMPACT_ACTION_CONTROL_MAX_ROWS)
    }

    @Test
    fun `multi target actions stay capped to target cycler and primary action`() {
        assertEquals(1, compactTargetActionSlotCount(1))
        assertEquals(2, compactTargetActionSlotCount(2))
        assertEquals(2, compactTargetActionSlotCount(5))
        assertEquals("次対象 2/5: ミミズ", compactTargetCyclerLabel("ミミズ", selectedIndex = 1, total = 5))
    }

    @Test
    fun `log history is collapsed by default`() {
        assertTrue(LOG_HISTORY_COLLAPSED_BY_DEFAULT)
    }

    @Test
    fun `audio settings button keeps a full touch target`() {
        assertTrue(AUDIO_SETTINGS_BUTTON_SIZE >= 44.dp)
        assertTrue(AUDIO_SETTINGS_BUTTON_SIZE <= MOBILE_PLAY_HUD_HEIGHT)
    }

    @Test
    fun `game menu keeps a full touch target inside the hud`() {
        assertTrue(GAME_MENU_BUTTON_SIZE >= 44.dp)
        assertTrue(GAME_MENU_BUTTON_SIZE <= MOBILE_PLAY_HUD_HEIGHT)
    }

    @Test
    fun `hud score and direct controls keep their reserved widths`() {
        assertTrue(HUD_SCORE_MIN_WIDTH >= 40.dp)
        assertEquals(44.dp, GAME_MENU_BUTTON_SIZE)
        assertEquals(44.dp, BOARD_PIECE_VISIBILITY_TOGGLE_SIZE)
    }

    @Test
    fun `rules navigation keeps a full touch target`() {
        assertTrue(RULES_MIN_TOUCH_TARGET >= 44.dp)
    }

    @Test
    fun `board piece visibility toggle keeps a full touch target inside the hud`() {
        assertTrue(BOARD_PIECE_VISIBILITY_TOGGLE_SIZE >= 44.dp)
        assertTrue(BOARD_PIECE_VISIBILITY_TOGGLE_SIZE <= MOBILE_PLAY_HUD_HEIGHT)
    }

    @Test
    fun `log drawer has a bounded overlay height`() {
        assertEquals(420.dp, LOG_HISTORY_POPUP_MAX_HEIGHT)
        assertEquals(420.dp, logHistoryPopupHeightLimit(600.dp))
        assertEquals(296.dp, logHistoryPopupHeightLimit(300.dp))
        assertEquals(196.dp, logHistoryPopupHeightLimit(200.dp))
    }

    @Test
    fun `log drawer position stays above the action bar and inside safe drawing bounds`() {
        val safeLeft = 12
        val safeTop = 24
        val safeRight = 16
        val gap = 4
        val windowSize = IntSize(width = 360, height = 740)
        val actionBarBounds = IntRect(left = 16, top = 500, right = 344, bottom = 700)
        val popupSize = IntSize(width = 328, height = 220)
        val position = AboveAnchorPopupPositionProvider(
            safeLeftInsetPx = safeLeft,
            safeTopInsetPx = safeTop,
            safeRightInsetPx = safeRight,
            verticalGapPx = gap,
        ).calculatePosition(
            anchorBounds = actionBarBounds,
            windowSize = windowSize,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popupSize,
        )

        assertTrue(position.x >= safeLeft)
        assertTrue(position.x + popupSize.width <= windowSize.width - safeRight)
        assertTrue(position.y >= safeTop)
        assertTrue(position.y + popupSize.height + gap <= actionBarBounds.top)
    }

    @Test
    fun `dice roulette overlay blocks input to the back layer`() {
        assertTrue(DICE_ROULETTE_OVERLAY_CONSUMES_BACK_LAYER_INPUT)
    }

    private companion object {
        const val BOARD_ASPECT_RATIO = 3f / 4f
    }
}
