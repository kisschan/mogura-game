package com.moguru.game.android

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
        val spec = mobileGameplayLayoutSpec(
            viewportWidth = 360.dp,
            viewportHeight = 740.dp,
            actionBarHeight = MOBILE_PLAY_DIG_ACTION_BAR_HEIGHT,
        )

        assertTrue(spec.fitsWithoutScroll)
        assertTrue(spec.usedHeight <= 740.dp)
        assertTrue(spec.actionBarHeight <= MOBILE_PLAY_DIG_ACTION_BAR_HEIGHT)
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
            compactActionBarContentHeight(ActionBarContentMode.STANDARD, RESULT_EVENT_STRIP_HEIGHT) <=
                MOBILE_PLAY_RESULT_ACTION_BAR_HEIGHT,
            "result banners must not overflow the expanded action bar",
        )
    }

    @Test
    fun `result banner heights scale with accessibility font size`() {
        val scaledStripHeight = resultEventStripHeight(fontScale = 1.5f)
        val scaledActionBarHeight = compactActionBarHeight(
            mode = ActionBarContentMode.STANDARD,
            eventStripHeight = scaledStripHeight,
        )

        assertTrue(scaledStripHeight > RESULT_EVENT_STRIP_HEIGHT)
        assertTrue(scaledActionBarHeight > MOBILE_PLAY_RESULT_ACTION_BAR_HEIGHT)
        assertTrue(
            compactActionBarContentHeight(ActionBarContentMode.STANDARD, scaledStripHeight) <=
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
    fun `log drawer has a bounded overlay height`() {
        assertTrue(LOG_HISTORY_POPUP_MAX_HEIGHT <= 220.dp)
    }

    @Test
    fun `dice roulette overlay blocks input to the back layer`() {
        assertTrue(DICE_ROULETTE_OVERLAY_CONSUMES_BACK_LAYER_INPUT)
    }
}
