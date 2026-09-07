package com.moguru.game.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.moguru.game.model.FoodType
import com.moguru.game.presenter.displayName

/** Shows the carried card at the mole's hands while keeping its identifying ring opaque. */
@Composable
internal fun BoardPlayerToken(
    player: AndroidPlayerTokenUiState,
    pieceAlpha: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .border(2.dp, playerAccentColor(player.playerId), RoundedCornerShape(999.dp))
                .padding(2.dp),
        ) {
            BoardPlayerImage(
                playerId = player.playerId,
                contentDescription = player.accessibilityLabel,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = pieceAlpha },
            )
        }
        player.carriedFoodType?.let { foodType ->
            // Size in dp, not board-normalized units: the tile must stay square
            // even though the existing token bounds are taller than they are wide.
            val tileSize = maxWidth * 0.4f
            Image(
                painter = painterResource(foodRes(foodType)),
                contentDescription = carriedFoodContentDescription(foodType),
                modifier = Modifier
                    .offset(
                        x = (maxWidth - tileSize) / 2f,
                        y = maxHeight * 0.92f - tileSize,
                    )
                    .size(tileSize)
                    .testTag("carried-food-${player.playerId}")
                    .graphicsLayer { alpha = pieceAlpha },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

internal fun carriedFoodContentDescription(type: FoodType): String =
    "${type.displayName()}をレンコウ中"
