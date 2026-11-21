package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.libretrodroid.GLRetroView

/**
 * Pick how fast the game runs: Off, then 2X to 8X (as far as this system's emulator can go). The
 * speeds are the same tiles as the pause menu's, two to a row held upright and one row sideways;
 * the current one lights up, and the title sits right above them. One tap picks the speed and goes
 * back to the game.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameMenuFastForwardScreen(
    currentSpeed: Int,
    maxSpeed: Int,
    onPick: (Int) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { SharedPreferencesHelper.getSharedPreferences(context.applicationContext) }
    var audioMode by remember {
        mutableIntStateOf(
            preferences.getInt(
                GameMenuContract.PREF_FAST_FORWARD_AUDIO_MODE,
                GLRetroView.FAST_FORWARD_AUDIO_KEEP_PITCH,
            ),
        )
    }

    val speeds = GameMenuContract.speedsUpTo(maxSpeed)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        val sideways = maxWidth > maxHeight
        val columns = if (sideways) minOf(speeds.size.coerceAtLeast(2), 5) else 2
        val tile =
            if (sideways) {
                tileSize(maxWidth, maxHeight, columns = 5, rows = 2)
            } else {
                val width = ((maxWidth - TILE_GAP) / 2 - 1.dp).coerceIn(100.dp, 148.dp)
                TileSize(width, 92.dp)
            }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.game_menu_fast_forward),
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
                    speeds.forEach { speed ->
                        MenuTile(
                            text = if (speed == 1) stringResource(R.string.game_menu_fast_forward_off) else "${speed}X",
                            icon =
                                if (speed == 1) {
                                    rememberVectorPainter(Icons.Filled.PlayArrow)
                                } else {
                                    painterResource(R.drawable.ic_menu_fast_forward)
                                },
                            selected = speed == currentSpeed,
                        ) { onPick(speed) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.game_menu_ff_audio_label),
                style = MaterialTheme.typography.labelMedium,
                color = ArcaduneColors.OnSurfaceDim,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FastForwardAudioBar(
                selectedMode = audioMode,
                onModeSelected = { nextMode ->
                    audioMode = nextMode
                    preferences.edit {
                        putInt(GameMenuContract.PREF_FAST_FORWARD_AUDIO_MODE, nextMode)
                    }
                },
            )
        }
    }
}

@Composable
private fun FastForwardAudioBar(
    selectedMode: Int,
    onModeSelected: (Int) -> Unit,
) {
    val trackShape = RoundedCornerShape(999.dp)
    val options =
        listOf(
            GLRetroView.FAST_FORWARD_AUDIO_KEEP_PITCH to R.string.game_menu_ff_audio_keep_pitch,
            GLRetroView.FAST_FORWARD_AUDIO_CLASSIC to R.string.game_menu_ff_audio_classic,
            GLRetroView.FAST_FORWARD_AUDIO_MUTE to R.string.game_menu_ff_audio_mute,
        )
    Row(
        modifier =
            Modifier
                .height(40.dp)
                .clip(trackShape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(1.dp, ArcaduneColors.GlassEdge, trackShape)
                .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (mode, labelRes) ->
            val selected = mode == selectedMode
            Box(
                modifier =
                    Modifier
                        .height(34.dp)
                        .clip(trackShape)
                        .background(if (selected) ArcaduneColors.Purple else Color.Transparent)
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = if (selected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
