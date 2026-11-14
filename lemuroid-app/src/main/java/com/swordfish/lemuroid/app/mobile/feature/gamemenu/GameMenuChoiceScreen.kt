package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.tilt.TiltConfigurationMenuEntry

/** A real pause-menu page for short choices: no dialog card, and Back belongs to [GameMenuActivity.SubMenu]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameMenuChoiceScreen(
    title: String,
    choices: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    onChoice: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        val sideways = maxWidth > maxHeight
        val columns =
            if (sideways) {
                if (choices.size == 5) 5 else minOf(choices.size.coerceAtLeast(2), 4)
            } else {
                2
            }
        val tileWidth = ((maxWidth - TILE_GAP * (columns - 1)) / columns - 1.dp).coerceIn(92.dp, 176.dp)
        val tileHeight = if (columns == 5) minOf(tileWidth * 0.92f, 96.dp).coerceAtLeast(76.dp) else 96.dp
        val tile = TileSize(tileWidth, tileHeight)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            CompositionLocalProvider(LocalTileSize provides tile) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TILE_GAP, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(TILE_GAP),
                    maxItemsInEachRow = columns,
                ) {
                    choices.forEachIndexed { index, choice ->
                        MenuTile(
                            text = choice,
                            icon = rememberVectorPainter(icons[index]),
                            selected = index == selected,
                        ) { onChoice(index) }
                    }
                }
            }
        }
    }
}

internal fun tiltChoiceIcon(entry: TiltConfigurationMenuEntry): ImageVector =
    when (entry) {
        TiltConfigurationMenuEntry.TILT_CONFIGURATION_DISABLED -> Icons.Filled.Block
        TiltConfigurationMenuEntry.TILT_CONFIGURATION_CROSS -> Icons.Filled.Gamepad
        TiltConfigurationMenuEntry.TILT_CONFIGURATION_ANALOG_LEFT,
        TiltConfigurationMenuEntry.TILT_CONFIGURATION_ANALOG_RIGHT,
        -> Icons.Filled.SportsEsports
        TiltConfigurationMenuEntry.TILT_CONFIGURATION_L_R,
        TiltConfigurationMenuEntry.TILT_CONFIGURATION_L1_R1,
        TiltConfigurationMenuEntry.TILT_CONFIGURATION_L2_R2,
        -> Icons.Filled.SwapHoriz
    }
