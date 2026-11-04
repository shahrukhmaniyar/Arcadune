package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.tilt.TiltConfigurationMenuEntry
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import com.swordfish.lemuroid.app.shared.GameMenuContract
import kotlin.reflect.KFunction1

/**
 * The pause menu, after Delta: "Main Menu" and "Resume" on top, and a grid of glass tiles for
 * everything else. Toggles (fast forward, sound) light up when on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameMenuHomeScreen(
    navController: NavController,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    onResult: KFunction1<Intent.() -> Unit, Unit>,
) {
    var showDisks by remember { mutableStateOf(false) }
    var showTilt by remember { mutableStateOf(false) }

    // The menu settles in with a small spring, like an iOS sheet appearing.
    val entrance = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        entrance.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f))
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entrance.value.coerceIn(0f, 1f)
                    val size = 0.94f + 0.06f * entrance.value
                    scaleX = size
                    scaleY = size
                }
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            FlowRow(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (gameMenuRequest.coreConfig.statesSupported) {
                    MenuTile(stringResource(R.string.game_menu_save), painterResource(R.drawable.ic_menu_save)) {
                        navController.navigateToRoute(GameMenuRoute.SAVE)
                    }
                    MenuTile(stringResource(R.string.game_menu_load), painterResource(R.drawable.ic_menu_load)) {
                        navController.navigateToRoute(GameMenuRoute.LOAD)
                    }
                }
                MenuTile(stringResource(R.string.game_menu_cheats), rememberVectorPainter(Icons.Filled.Star)) {
                    navController.navigateToRoute(GameMenuRoute.CHEATS)
                }
                if (gameMenuRequest.fastForwardSupported) {
                    MenuTile(
                        stringResource(R.string.game_menu_fast_forward),
                        painterResource(R.drawable.ic_menu_fast_forward),
                        selected = gameMenuRequest.fastForwardEnabled,
                    ) {
                        onResult {
                            putExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD, !gameMenuRequest.fastForwardEnabled)
                        }
                    }
                }
                MenuTile(
                    stringResource(R.string.game_menu_mute_audio),
                    painterResource(R.drawable.ic_menu_mute),
                    selected = !gameMenuRequest.audioEnabled,
                ) {
                    onResult { putExtra(GameMenuContract.RESULT_ENABLE_AUDIO, !gameMenuRequest.audioEnabled) }
                }
                MenuTile(stringResource(R.string.game_menu_restart), painterResource(R.drawable.ic_menu_restart)) {
                    onResult { putExtra(GameMenuContract.RESULT_RESET, true) }
                }
                if (gameMenuRequest.numDisks > 1) {
                    MenuTile(stringResource(R.string.game_menu_change_disk_button), painterResource(R.drawable.ic_menu_disk)) {
                        showDisks = true
                    }
                }
                if (gameMenuRequest.allTiltConfigurations.isNotEmpty()) {
                    MenuTile(stringResource(R.string.game_menu_tilt_sensor), rememberVectorPainter(Icons.Filled.Sensors)) {
                        showTilt = true
                    }
                }
                MenuTile(
                    stringResource(R.string.game_menu_edit_touch_controls),
                    painterResource(R.drawable.ic_menu_controls),
                ) {
                    onResult { putExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, true) }
                }
                MenuTile(stringResource(R.string.game_menu_settings), painterResource(R.drawable.ic_menu_settings)) {
                    navController.navigateToRoute(GameMenuRoute.OPTIONS)
                }
            }
        }

        // Main Menu and Resume sit at the bottom, where thumbs already are.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                text = stringResource(R.string.pause_main_menu),
                icon = rememberVectorPainter(Icons.Filled.Home),
                highlighted = false,
                onClick = { onResult { putExtra(GameMenuContract.RESULT_QUIT, true) } },
            )
            PillButton(
                text = stringResource(R.string.pause_resume),
                icon = rememberVectorPainter(Icons.Filled.PlayArrow),
                highlighted = true,
                onClick = { onResult { } },
            )
        }
    }

    if (showDisks) {
        ChoiceDialog(
            title = stringResource(R.string.game_menu_change_disk_button),
            choices = (1..gameMenuRequest.numDisks).map { stringResource(R.string.game_menu_change_disk_disk, it) },
            selected = gameMenuRequest.currentDisk,
            onDismiss = { showDisks = false },
            onChoice = { index -> onResult { putExtra(GameMenuContract.RESULT_CHANGE_DISK, index) } },
        )
    }

    if (showTilt) {
        val entries = gameMenuRequest.allTiltConfigurations.map { TiltConfigurationMenuEntry.fromTiltConfiguration(it) }
        ChoiceDialog(
            title = stringResource(R.string.game_menu_tilt_sensor),
            choices = entries.map { stringResource(it.descriptionId) },
            selected = gameMenuRequest.allTiltConfigurations.indexOf(gameMenuRequest.currentTiltConfiguration),
            onDismiss = { showTilt = false },
            onChoice = { index ->
                onResult { putExtra(GameMenuContract.RESULT_CHANGE_TILT_CONFIG, entries[index].configuration) }
            },
        )
    }
}

@Composable
private fun PillButton(
    text: String,
    icon: Painter,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            Modifier
                .pressableClick(onClick)
                .clip(shape)
                .background(if (highlighted) ArcaduneColors.Purple else Color(0xE61C1A27))
                .border(1.dp, if (highlighted) Color.Transparent else ArcaduneColors.GlassEdge, shape)
                .size(width = 84.dp, height = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Icon only; the name is still read out by TalkBack.
        val color = if (highlighted) ArcaduneColors.OnAccent else ArcaduneColors.OnSurface
        Icon(painter = icon, contentDescription = text, tint = color, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun MenuTile(
    text: String,
    icon: Painter,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier =
            Modifier
                .size(width = 100.dp, height = 92.dp)
                .pressableClick(onClick)
                .clip(shape)
                .background(if (selected) ArcaduneColors.Purple else Color(0xE61C1A27))
                .border(1.dp, ArcaduneColors.GlassEdge, shape)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurface
        Icon(painter = icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** A click that also shrinks the element a little while pressed, so every touch is felt. */
@Composable
private fun Modifier.pressableClick(onClick: () -> Unit): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, ArcaduneMotion.press(), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interactions, indication = null, onClick = onClick)
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onChoice: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEachIndexed { index, choice ->
                    Text(
                        text = choice,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onChoice(index) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        color = if (index == selected) ArcaduneColors.Purple else ArcaduneColors.OnSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
