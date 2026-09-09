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
    fun `active gameplay fits compact mobile viewports without scroll`() {
        listOf(
            360.dp to 740.dp,
            375.dp to 812.dp,
            390.dp to 844.dp,
            412.dp to 915.dp,
            430.dp to 932.dp,
        ).forEach { (width, height) ->
            val spec = mobileGameplayLayoutSpec(width, height)

            assertTrue(spec.fitsWithoutScroll, "gameplay should fit $width x $height")
            assertTrue(spec.usedHeight <= height, "used height ${spec.usedHeight} should fit $height")
            assertTrue(spec.boardHeight <= spec.boardViewportHeight)
            assertTrue(spec.hudHeight <= MOBILE_PLAY_HUD_HEIGHT)
            assertTrue(spec.actionBarHeight <= MOBILE_PLAY_ACTION_BAR_HEIGHT)
        }
    }

    @Test
    fun `dig placement still fits the smallest supported viewport`() {
        listOf(1f, 1.5f).forEach { fontScale ->
            val stripHeight = actionGuidanceStripHeight(hasCaptureOutcome = false, fontScale)
            val buttonHeight = compactActionButtonHeight(hasEndTurnHint = false, fontScale)
            val actionBarHeight = compactActionBarHeight(ActionBarContentMode.DIG_PLACEMENT, stripHeight, buttonHeight)
            val spec = mobileGameplayLayoutSpec(
                viewportWidth = 360.dp,
                viewportHeight = 740.dp,
                actionBarHeight = actionBarHeight,
            )

            assertTrue(spec.fitsWithoutScroll)
            assertTrue(spec.usedHeight <= 740.dp)
            assertTrue(spec.actionBarHeight >= ACTION_BAR_VERTICAL_PADDING * 2 + stripHeight + ACTION_BAR_CONTENT_GAP + buttonHeight)
            if (fontScale == 1f) assertEquals(MOBILE_PLAY_DIG_ACTION_BAR_HEIGHT, spec.actionBarHeight)
        }
    }

    @Test
    fun `active gameplay does not use vertical scroll policy`() {
        assertFalse(ACTIVE_GAMEPLAY_USES_VERTICAL_SCROLL)
    }

    @Test
    fun `action bar content fits its fixed mobile height`() {
        assertTrue(
            compactActionBarContentHeight(ActionBarContentMode.STANDARD) <= MOBILE_PLAY_ACTION_BAR_HEIGHT,
            "standard actions must not overflow the fixed action bar",
        )
        assertTrue(
            compactActionBarContentHeight(ActionBarContentMode.DIG_PLACEMENT) <= MOBILE_PLAY_DIG_ACTION_BAR_HEIGHT,
            "dig placement controls must not overflow the fixed action bar",
        )
    }

    @Test
    fun `result banners get enough strip and action bar height for four lines`() {
        assertTrue(RESULT_EVENT_STRIP_HEIGHT > EVENT_STRIP_HEIGHT)
        assertTrue(RESULT_EVENT_STRIP_HEIGHT >= 44.dp)
        assertTrue(
            compactActionBarContentHeight(ActionBarContentMode.STANDARD, actionGuidanceStripHeight(true)) <=
                MOBILE_PLAY_RESULT_ACTION_BAR_HEIGHT,
            "result banners must not overflow the expanded action bar",
        )
    }

    @Test
    fun `result banner heights scale with accessibility font size`() {
        val scaledStripHeight = resultEventStripHeight(fontScale = 1.5f)
        val scaledActionBarHeight = compactActionBarHeight(
            mode = ActionBarContentMode.STANDARD,
            eventStripHeight = actionGuidanceStripHeight(true, fontScale = 1.5f),
        )

        assertTrue(scaledStripHeight > RESULT_EVENT_STRIP_HEIGHT)
        assertTrue(scaledActionBarHeight > MOBILE_PLAY_RESULT_ACTION_BAR_HEIGHT)
        assertTrue(
            compactActionBarContentHeight(ActionBarContentMode.STANDARD, actionGuidanceStripHeight(true, 1.5f)) <=
                scaledActionBarHeight,
        )
    }

    @Test
    fun `result banner action bar still fits the smallest supported viewport`() {
        val spec = mobileGameplayLayoutSpec(
            viewportWidth = 360.dp,
            viewportHeight = 740.dp,
            actionBarHeight = MOBILE_PLAY_RESULT_ACTION_BAR_HEIGHT,
        )

        assertTrue(spec.fitsWithoutScroll)
        assertTrue(spec.usedHeight <= 740.dp)
    }

    @Test
    fun `independent instruction and event fit together including larger fonts`() {
        listOf(1f, 1.2f, 1.5f).forEach { fontScale ->
            val resultHeight = resultEventStripHeight(fontScale)
            val stripHeight = actionGuidanceStripHeight(hasCaptureOutcome = true, fontScale = fontScale)
            assertTrue(stripHeight >= 32.dp * fontScale + 2.dp + resultHeight)
            val buttonHeight = compactActionButtonHeight(hasEndTurnHint = true, fontScale = fontScale)
            assertTrue(buttonHeight >= (15.dp * 2 + 12.dp * 2) * fontScale)
            val actionBarHeight = compactActionBarHeight(ActionBarContentMode.STANDARD, stripHeight, buttonHeight)
            listOf(360.dp to 740.dp, 390.dp to 844.dp).forEach { (width, height) ->
                val spec = mobileGameplayLayoutSpec(width, height, actionBarHeight)
                assertTrue(spec.fitsWithoutScroll)
                assertTrue(spec.boardHeight > 0.dp)
            }
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
    fun `event strip keeps a usable history tap target`() {
        assertTrue(EVENT_STRIP_HEIGHT >= 40.dp)
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
        assertTrue(LOG_HISTORY_POPUP_MAX_HEIGHT <= 220.dp)
        assertEquals(220.dp, logHistoryPopupHeightLimit(300.dp))
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
}
