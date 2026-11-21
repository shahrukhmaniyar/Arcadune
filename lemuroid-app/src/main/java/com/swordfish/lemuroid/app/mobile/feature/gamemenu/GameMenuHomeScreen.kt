package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.achievements.RaGoldColor
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.achievements.AchievementSessionStatus
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsSessionHolder
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
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
    val context = LocalContext.current
    val achState by RetroAchievementsSessionHolder.state.collectAsState()
    var showEnableHardcoreDialog by rememberSaveable { mutableStateOf(false) }
    val hardcoreApproved = RetroAchievementsPreferences.isHardcoreApproved()
    val raActiveSession =
        achState.status != AchievementSessionStatus.DISABLED &&
            achState.status != AchievementSessionStatus.NOT_SIGNED_IN &&
            achState.status != AchievementSessionStatus.UNSUPPORTED_SYSTEM
    val achievementsBadge =
        when {
            achState.status == AchievementSessionStatus.LOADED && achState.totalCount > 0 ->
                "${achState.unlockedCount}/${achState.totalCount}"
            achState.status == AchievementSessionStatus.SIGNING_IN ||
                achState.status == AchievementSessionStatus.IDENTIFYING ->
                stringResource(R.string.ra_menu_loading)
            else -> null
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (achState.hardcoreActive) {
                        Modifier.background(Color(0xD90A0812))
                    } else {
                        Modifier
                    },
                )
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (raActiveSession) {
            val modeAccent = if (achState.hardcoreActive) RaGoldColor else ArcaduneColors.Purple
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xE61C1A27))
                            .border(1.dp, modeAccent.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (achState.hardcoreActive) {
                                    R.string.ra_hud_hardcore_badge
                                } else {
                                    R.string.ra_hud_casual_badge
                                },
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = modeAccent,
                    )
                }

                if (achState.hardcoreActive) {
                    ArcadunePillButton(
                        label = stringResource(R.string.ra_mode_switch_casual_action),
                        height = 32.dp,
                        onClick = {
                            onResult { putExtra(GameMenuContract.RESULT_SET_HARDCORE, false) }
                        },
                    )
                } else if (hardcoreApproved) {
                    ArcadunePillButton(
                        label = stringResource(R.string.ra_mode_enable_hardcore_action),
                        icon = Icons.Filled.Whatshot,
                        height = 32.dp,
                        onClick = {
                            if (achState.coreHardcoreSupported) {
                                showEnableHardcoreDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    achState.coreUnsupportedReason.ifBlank {
                                        context.getString(R.string.ra_empty_unsupported_system_body)
                                    },
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
        // Held upright the tiles go three to a row, sideways five (so nine tiles are 5 and 4); they
        // grow to fill a wider screen, and shrink on a short one so every row stays in view.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val sideways = maxWidth > maxHeight
            val columns = if (sideways) 5 else 3
            val tile = tileSize(maxWidth, maxHeight, columns, rows = if (sideways) 2 else 3)
            CompositionLocalProvider(LocalTileSize provides tile) {
                Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    FlowRow(
                        modifier = Modifier.padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(TILE_GAP, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(TILE_GAP),
                        maxItemsInEachRow = columns,
                    ) {
                        if (gameMenuRequest.coreConfig.statesSupported) {
                            MenuTile(
                                stringResource(R.string.game_menu_save),
                                painterResource(R.drawable.ic_menu_save),
                            ) {
                                navController.navigateToRoute(GameMenuRoute.SAVE)
                            }
                            MenuTile(
                                text = stringResource(R.string.game_menu_load),
                                icon = painterResource(R.drawable.ic_menu_load),
                                supportingText =
                                    if (achState.hardcoreActive) {
                                        stringResource(R.string.game_menu_hardcore_badge)
                                    } else {
                                        null
                                    },
                            ) {
                                if (achState.hardcoreActive) {
                                    Toast.makeText(
                                        context,
                                        R.string.ra_hardcore_load_disabled_toast,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    navController.navigateToRoute(GameMenuRoute.LOAD)
                                }
                            }
                        }
                        MenuTile(
                            text = stringResource(R.string.game_menu_achievements),
                            icon = rememberVectorPainter(Icons.Filled.EmojiEvents),
                            supportingText = achievementsBadge,
                        ) {
                            navController.navigateToRoute(GameMenuRoute.ACHIEVEMENTS)
                        }
                        MenuTile(
                            text = stringResource(R.string.game_menu_cheats),
                            icon = rememberVectorPainter(Icons.Filled.Star),
                            supportingText =
                                if (achState.hardcoreActive) {
                                    stringResource(R.string.game_menu_hardcore_badge)
                                } else {
                                    null
                                },
                        ) {
                            if (achState.hardcoreActive) {
                                Toast.makeText(
                                    context,
                                    R.string.ra_hardcore_cheats_disabled_toast,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                navController.navigateToRoute(GameMenuRoute.CHEATS)
                            }
                        }
                        if (gameMenuRequest.fastForwardSupported) {
                            MenuTile(
                                if (gameMenuRequest.frameSpeed > 1) {
                                    stringResource(R.string.game_menu_fast_forward_speed, gameMenuRequest.frameSpeed)
                                } else {
                                    stringResource(R.string.game_menu_fast_forward)
                                },
                                painterResource(R.drawable.ic_menu_fast_forward),
                                selected = gameMenuRequest.frameSpeed > 1,
                            ) {
                                navController.navigateToRoute(GameMenuRoute.FAST_FORWARD)
                            }
                        }
                        MenuTile(
                            stringResource(R.string.game_menu_mute_audio),
                            painterResource(R.drawable.ic_menu_mute),
                            selected = !gameMenuRequest.audioEnabled,
                        ) {
                            onResult { putExtra(GameMenuContract.RESULT_ENABLE_AUDIO, !gameMenuRequest.audioEnabled) }
                        }
                        MenuTile(
                            stringResource(R.string.game_menu_restart),
                            painterResource(R.drawable.ic_menu_restart),
                        ) {
                            onResult { putExtra(GameMenuContract.RESULT_RESET, true) }
                        }
                        if (gameMenuRequest.numDisks > 1) {
                            MenuTile(
                                stringResource(R.string.game_menu_change_disk_button),
                                painterResource(R.drawable.ic_menu_disk),
                            ) {
                                navController.navigateToRoute(GameMenuRoute.DISKS)
                            }
                        }
                        if (gameMenuRequest.allTiltConfigurations.isNotEmpty()) {
                            MenuTile(
                                stringResource(R.string.game_menu_tilt_sensor),
                                rememberVectorPainter(Icons.Filled.Sensors),
                            ) {
                                navController.navigateToRoute(GameMenuRoute.TILT)
                            }
                        }
                        MenuTile(
                            stringResource(R.string.game_menu_edit_touch_controls),
                            painterResource(R.drawable.ic_menu_controls),
                        ) {
                            onResult { putExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, true) }
                        }
                        MenuTile(
                            stringResource(R.string.game_menu_settings),
                            painterResource(R.drawable.ic_menu_settings),
                        ) {
                            navController.navigateToRoute(GameMenuRoute.OPTIONS)
                        }
                    }
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

    if (showEnableHardcoreDialog) {
        ArcaduneDialog(
            onDismissRequest = { showEnableHardcoreDialog = false },
            icon = Icons.Filled.Whatshot,
            title = { Text(text = stringResource(R.string.ra_hardcore_confirm_enable_title)) },
            dismissButton = {
                ArcadunePillButton(
                    label = stringResource(R.string.cancel),
                    onClick = { showEnableHardcoreDialog = false },
                )
            },
            confirmButton = {
                ArcadunePillButton(
                    label = stringResource(R.string.ra_hardcore_confirm_enable_confirm),
                    highlighted = true,
                    onClick = {
                        showEnableHardcoreDialog = false
                        onResult { putExtra(GameMenuContract.RESULT_SET_HARDCORE, true) }
                    },
                )
            },
        ) {
            Text(
                text = stringResource(R.string.ra_hardcore_confirm_enable_message),
                style = MaterialTheme.typography.bodyMedium,
                color = ArcaduneColors.OnSurfaceDim,
            )
        }
    }
}

/** Size of one menu tile, and the gap between tiles. */
internal data class TileSize(val width: Dp, val height: Dp)

internal val TILE_GAP = 12.dp

internal val LocalTileSize = compositionLocalOf { TileSize(100.dp, 92.dp) }

/**
 * A tile that fills its share of the room ([columns] across, [rows] down), but never gets huge on
 * a tablet nor too short to hold an icon and two lines of text.
 */
internal fun tileSize(
    maxWidth: Dp,
    maxHeight: Dp,
    columns: Int,
    rows: Int,
): TileSize {
    // A dp of slack, so rounding never pushes the last tile of a row onto the next one.
    if (columns < 5) {
        val cols = columns.coerceAtLeast(1)
        val width = ((maxWidth - TILE_GAP * (cols - 1)) / cols - 1.dp).coerceIn(84.dp, 106.dp)
        val roomForRows = (maxHeight - 32.dp - TILE_GAP * (rows.coerceAtLeast(1) - 1)) / rows.coerceAtLeast(1)
        val height = minOf(width * 0.92f, roomForRows).coerceAtLeast(76.dp)
        return TileSize(width, height)
    }
    val width = ((maxWidth - TILE_GAP * (columns - 1)) / columns - 1.dp).coerceIn(72.dp, 132.dp)
    val roomForRows = (maxHeight - 32.dp - TILE_GAP * (rows - 1)) / rows
    val height = minOf(width * 0.92f, roomForRows).coerceAtLeast(72.dp)
    return TileSize(width, height)
}

@Composable
internal fun PillButton(
    text: String,
    icon: Painter,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val borderWidth = if (focused) 2.dp else 1.dp
    val borderColor =
        when {
            focused -> ArcaduneColors.OnSurface
            highlighted -> Color.Transparent
            else -> ArcaduneColors.GlassEdge
        }
    Row(
        modifier =
            Modifier
                .semantics {
                    role = Role.Button
                    if (highlighted) selected = true
                }
                .pressableClick(onClick, interactions)
                .clip(shape)
                .background(if (highlighted) ArcaduneColors.Purple else Color(0xE61C1A27))
                .border(borderWidth, borderColor, shape)
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
internal fun MenuTile(
    text: String,
    icon: Painter,
    selected: Boolean = false,
    supportingText: String? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val tile = LocalTileSize.current
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val borderWidth = if (focused) 2.dp else 1.dp
    val borderColor = if (focused) ArcaduneColors.OnSurface else ArcaduneColors.GlassEdge
    Column(
        modifier =
            Modifier
                .size(width = tile.width, height = tile.height)
                .semantics {
                    role = Role.Button
                    if (selected) this.selected = true
                }
                .pressableClick(onClick, interactions)
                .clip(shape)
                .background(if (selected) ArcaduneColors.Purple else Color(0xE61C1A27))
                .border(borderWidth, borderColor, shape)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurface
        Icon(painter = icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(8.dp))
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fontBoost = density.fontScale.coerceIn(1f, 1.15f)
        val labelSize = with(density) { (11.dp * fontBoost).toSp() }
        val labelLine = with(density) { (13.dp * fontBoost).toSp() }
        Text(
            text = text,
            color = color,
            fontSize = labelSize,
            lineHeight = labelLine,
            textAlign = TextAlign.Center,
            maxLines = if (supportingText == null || tile.height >= 104.dp) 2 else 1,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
        )
        if (supportingText != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = supportingText,
                color = color.copy(alpha = 0.78f),
                fontSize = with(density) { (9.5f.dp * fontBoost).toSp() },
                lineHeight = with(density) { (11.5f.dp * fontBoost).toSp() },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A click that also shrinks the element a little while pressed, so every touch is felt. */
@Composable
private fun Modifier.pressableClick(
    onClick: () -> Unit,
    interactions: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, ArcaduneMotion.press(), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interactions, indication = null, onClick = onClick)
}
