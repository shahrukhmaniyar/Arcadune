package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.coversearch.CoverSearchActivity
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSmallGameImage
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGameContextActions(
    selectedGameState: MutableState<Game?>,
    shortcutSupported: Boolean,
    onGamePlay: (Game) -> Unit,
    onGameRestart: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onCreateShortcut: (Game) -> Unit,
    onSetCustomName: (Game, String) -> Unit,
    onClearCustomName: (Game) -> Unit,
    onSetCustomCoverUri: (Game, String) -> Unit,
    onClearCustomCoverUri: (Game) -> Unit,
) {
    val modalSheetState = rememberModalBottomSheetState(true)
    val selectedGame = selectedGameState.value

    LaunchedEffect(selectedGame) {
        if (selectedGame != null) {
            modalSheetState.show()
        } else {
            modalSheetState.hide()
        }
    }

    if (selectedGame != null) {
        ModalBottomSheet(
            sheetState = modalSheetState,
            onDismissRequest = { selectedGameState.value = null },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = ArcaduneColors.Surface,
            contentColor = ArcaduneColors.OnSurface,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                Box(
                    modifier =
                        Modifier
                            .padding(top = 12.dp, bottom = 6.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ArcaduneColors.GlassEdge),
                )
            },
        ) {
            ContextActionContent(
                selectedGame = selectedGame,
                onGamePlay = onGamePlay,
                selectedGameState = selectedGameState,
                onGameRestart = onGameRestart,
                onFavoriteToggle = onFavoriteToggle,
                shortcutSupported = shortcutSupported,
                onCreateShortcut = onCreateShortcut,
                onSetCustomName = onSetCustomName,
                onClearCustomName = onClearCustomName,
                onSetCustomCoverUri = onSetCustomCoverUri,
                onClearCustomCoverUri = onClearCustomCoverUri,
            )
        }
    }
}

@Composable
private fun ContextActionContent(
    selectedGame: Game,
    onGamePlay: (Game) -> Unit,
    selectedGameState: MutableState<Game?>,
    onGameRestart: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    shortcutSupported: Boolean,
    onCreateShortcut: (Game) -> Unit,
    onSetCustomName: (Game, String) -> Unit,
    onClearCustomName: (Game) -> Unit,
    onSetCustomCoverUri: (Game, String) -> Unit,
    onClearCustomCoverUri: (Game) -> Unit,
) {
    val context = LocalContext.current
    var showCustomNameDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (e: SecurityException) {
                    Timber.e(e, "Failed to persist read URI permission for custom cover")
                }
                onSetCustomCoverUri(selectedGame, uri.toString())
                selectedGameState.value = null
            }
        }

    val coverSearchLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val resultUri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && resultUri != null) {
                onSetCustomCoverUri(selectedGame, resultUri.toString())
                selectedGameState.value = null
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ContextActionHeader(game = selectedGame)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ContextPrimaryPill(
                modifier = Modifier.weight(1.35f),
                label = stringResource(id = R.string.game_context_menu_resume),
                icon = Icons.Default.PlayArrow,
                highlighted = true,
                onClick = {
                    onGamePlay(selectedGame)
                    selectedGameState.value = null
                },
            )
            ContextPrimaryPill(
                modifier = Modifier.weight(1f),
                label = stringResource(id = R.string.game_context_menu_restart),
                icon = Icons.Default.RestartAlt,
                highlighted = false,
                onClick = {
                    onGameRestart(selectedGame)
                    selectedGameState.value = null
                },
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ContextQuickTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label =
                    stringResource(
                        id =
                            if (selectedGame.isFavorite) {
                                R.string.game_context_menu_remove_from_favorites
                            } else {
                                R.string.game_context_menu_add_to_favorites
                            },
                    ),
                icon =
                    if (selectedGame.isFavorite) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                active = selectedGame.isFavorite,
                onClick = {
                    onFavoriteToggle(selectedGame, !selectedGame.isFavorite)
                    selectedGameState.value = null
                },
            )
            if (shortcutSupported) {
                ContextQuickTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = stringResource(id = R.string.game_context_menu_create_shortcut),
                    icon = Icons.Default.AppShortcut,
                    onClick = {
                        onCreateShortcut(selectedGame)
                        selectedGameState.value = null
                    },
                )
            }
            ContextQuickTile(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = stringResource(id = R.string.game_context_menu_add_custom_name),
                icon = Icons.Default.Edit,
                onClick = {
                    showCustomNameDialog = true
                },
            )
        }

        val cardShape = RoundedCornerShape(20.dp)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(ArcaduneColors.SurfaceHigh.copy(alpha = 0.65f))
                    .border(1.dp, ArcaduneColors.GlassEdge, cardShape),
        ) {
            if (selectedGame.customName != null) {
                ContextActionEntry(
                    label = stringResource(id = R.string.game_context_menu_remove_custom_name),
                    icon = Icons.Default.Clear,
                    onClick = {
                        onClearCustomName(selectedGame)
                        selectedGameState.value = null
                    },
                )
                GroupRowDivider()
            }

            if (selectedGame.customCoverUri != null) {
                ContextActionEntry(
                    label = stringResource(id = R.string.game_context_menu_remove_custom_artwork),
                    icon = Icons.Default.Clear,
                    onClick = {
                        onClearCustomCoverUri(selectedGame)
                        selectedGameState.value = null
                    },
                )
            } else {
                ContextActionEntry(
                    label = stringResource(id = R.string.game_context_menu_add_custom_artwork),
                    icon = Icons.Default.Image,
                    onClick = {
                        imagePickerLauncher.launch("image/*")
                    },
                )
            }

            GroupRowDivider()

            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_search_custom_artwork),
                icon = Icons.Default.Search,
                onClick = {
                    val systemName =
                        context.getString(GameSystem.findById(selectedGame.systemId).titleResId)
                    val intent =
                        Intent(context, CoverSearchActivity::class.java).apply {
                            putExtra(
                                CoverSearchActivity.EXTRA_QUERY,
                                "${cleanTitleForSearch(selectedGame.displayName())} $systemName box art",
                            )
                        }
                    coverSearchLauncher.launch(intent)
                },
            )
        }
    }

    if (showCustomNameDialog) {
        CustomNameDialog(
            initialName = selectedGame.displayName(),
            onDismiss = { showCustomNameDialog = false },
            onConfirm = { newName ->
                showCustomNameDialog = false
                onSetCustomName(selectedGame, newName)
                selectedGameState.value = null
            },
        )
    }
}

@Composable
private fun ContextActionHeader(game: Game) {
    val systemTitle = stringResource(id = GameSystem.findById(game.systemId).shortTitleResId)
    val developer = game.developer?.takeIf { it.isNotBlank() }
    val cardShape = RoundedCornerShape(20.dp)
    val coverShape = RoundedCornerShape(14.dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(ArcaduneColors.SurfaceHigh.copy(alpha = 0.55f))
                .border(1.dp, ArcaduneColors.GlassEdge, cardShape)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(coverShape),
        ) {
            LemuroidSmallGameImage(
                modifier = Modifier.fillMaxSize(),
                game = game,
            )
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .border(1.dp, ArcaduneColors.GlassEdge, coverShape),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = game.displayName(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ArcaduneColors.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ArcaduneColors.Purple.copy(alpha = 0.16f))
                            .border(
                                1.dp,
                                ArcaduneColors.Purple.copy(alpha = 0.45f),
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = systemTitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = ArcaduneColors.Purple,
                        maxLines = 1,
                    )
                }
                if (developer != null) {
                    Text(
                        text = developer,
                        style = MaterialTheme.typography.bodySmall,
                        color = ArcaduneColors.OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextPrimaryPill(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val contentColor = if (highlighted) ArcaduneColors.OnAccent else ArcaduneColors.OnSurface
    Row(
        modifier =
            modifier
                .height(50.dp)
                .semantics { role = Role.Button }
                .pressableScale(onClick)
                .clip(shape)
                .background(if (highlighted) ArcaduneColors.Purple else ArcaduneColors.SurfaceHigh)
                .then(
                    if (highlighted) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, ArcaduneColors.GlassEdge, shape)
                    },
                )
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContextQuickTile(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val backgroundColor =
        if (active) ArcaduneColors.Purple.copy(alpha = 0.2f) else ArcaduneColors.SurfaceHigh.copy(alpha = 0.65f)
    val borderColor =
        if (active) ArcaduneColors.Purple.copy(alpha = 0.6f) else ArcaduneColors.GlassEdge
    val iconColor = if (active) ArcaduneColors.Purple else ArcaduneColors.OnSurface

    Column(
        modifier =
            modifier
                .heightIn(min = 82.dp)
                .semantics { role = Role.Button }
                .pressableScale(onClick)
                .clip(shape)
                .background(backgroundColor)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            lineHeight = 14.sp,
            color = ArcaduneColors.OnSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContextActionEntry(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val badgeShape = RoundedCornerShape(11.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(badgeShape)
                    .background(ArcaduneColors.Surface)
                    .border(1.dp, ArcaduneColors.GlassEdge, badgeShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ArcaduneColors.OnSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = ArcaduneColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GroupRowDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 64.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
    )
}

@Composable
private fun Modifier.pressableScale(
    onClick: () -> Unit,
    interactions: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, ArcaduneMotion.press(), label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interactions, indication = null, onClick = onClick)
}

private val SEARCH_TITLE_TAGS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")

/** Drops "(Hudson) (Japan)[tr en ...]" style tags so the artwork search starts from a plain title. */
private fun cleanTitleForSearch(title: String): String = title.replace(SEARCH_TITLE_TAGS, "").trim().ifEmpty { title }
