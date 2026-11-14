package com.swordfish.lemuroid.app.mobile.feature.gamemenu.states

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import kotlin.math.roundToInt

/**
 * Save State and Load State, like Delta: a grid of cards, each with the screenshot taken when the
 * state was saved, its slot and its date. Empty slots can be saved into but not loaded.
 */
@Composable
fun GameMenuStatesScreen(
    viewModel: GameMenuStatesViewModel,
    title: String,
    onStateClicked: (Int) -> Unit,
    onDeleteState: ((Int) -> Unit)? = null,
) {
    val state by viewModel.uiStates.collectAsState(initial = GameMenuStatesViewModel.State())
    var menuSlotIndex by rememberSaveable { mutableIntStateOf(-1) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    val thumbnailCenters = remember { mutableStateMapOf<Int, Offset>() }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerOrigin = it.positionInWindow() },
    ) {
        val columns = if (maxWidth > maxHeight) 4 else 2
        // Centre the title and slots together, like the speed picker. On short screens or with
        // large text, this area scrolls while the submenu's Back button remains reachable.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = if (columns == 4) 760.dp else 400.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    state.entries.chunked(columns).forEachIndexed { row, entries ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            entries.forEachIndexed { column, entry ->
                                val slotIndex = row * columns + column
                                val canDelete = onDeleteState != null && entry.hasState
                                StateCard(
                                    entry = entry,
                                    modifier = Modifier.weight(1f),
                                    isMenuOpen = canDelete && menuSlotIndex == slotIndex,
                                    onThumbnailCenterChanged = { center ->
                                        thumbnailCenters[slotIndex] = center
                                    },
                                    onClick = { onStateClicked(slotIndex) },
                                    onLongClick =
                                        if (canDelete) {
                                            { menuSlotIndex = slotIndex }
                                        } else {
                                            null
                                        },
                                )
                            }
                            repeat(columns - entries.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Draw the Remove pill inside this window so Android does not unhide the system navigation bar.
        val activeEntry = state.entries.getOrNull(menuSlotIndex)
        if (menuSlotIndex >= 0 && activeEntry?.hasState == true && onDeleteState != null) {
            BackHandler { menuSlotIndex = -1 }
            val center = thumbnailCenters[menuSlotIndex]
            if (center != null) {
                val relX = (center.x - containerOrigin.x).roundToInt()
                val relY = (center.y - containerOrigin.y).roundToInt()
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(menuSlotIndex) { focusRequester.requestFocus() }
                Layout(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { menuSlotIndex = -1 },
                            ),
                    content = {
                        RemoveStatePill(
                            label = stringResource(R.string.game_menu_state_remove),
                            modifier = Modifier.focusRequester(focusRequester),
                            onClick = {
                                val slotToDelete = menuSlotIndex
                                menuSlotIndex = -1
                                onDeleteState(slotToDelete)
                            },
                        )
                    },
                ) { measurables, constraints ->
                    val placeable =
                        measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val x =
                            (relX - placeable.width / 2)
                                .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                        val y =
                            (relY - placeable.height / 2)
                                .coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
                        placeable.placeRelative(x, y)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveStatePill(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val pillShape = RoundedCornerShape(999.dp)
    val borderColor = if (focused) ArcaduneColors.Purple else ArcaduneColors.GlassEdge
    val borderWidth = if (focused) 2.dp else 1.dp

    Row(
        modifier =
            modifier
                .shadow(12.dp, pillShape)
                .clip(pillShape)
                .background(Color(0xF2181624))
                .border(borderWidth, borderColor, pillShape)
                .clickable(
                    interactionSource = interactions,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = ArcaduneColors.OnSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = ArcaduneColors.OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StateCard(
    entry: GameMenuStatesViewModel.StateEntry,
    modifier: Modifier = Modifier,
    isMenuOpen: Boolean = false,
    onThumbnailCenterChanged: (Offset) -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, ArcaduneMotion.press(), label = "press")
    val shape = RoundedCornerShape(14.dp)
    val contentAlpha = if (entry.enabled) 1f else 0.55f
    val cardColor =
        if (entry.hasState) {
            if (entry.preview != null) Color(0xFF0E0D14) else Color.White.copy(alpha = 0.06f)
        } else {
            Color.Black.copy(alpha = 0.34f)
        }
    val highlighted = focused || isMenuOpen
    val borderColor = if (highlighted) ArcaduneColors.Purple else ArcaduneColors.GlassEdge
    val borderWidth = if (highlighted) 2.dp else 1.dp
    val removeLabel = stringResource(R.string.game_menu_state_remove)

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .combinedClickable(
                    interactionSource = interactions,
                    indication = null,
                    enabled = entry.enabled,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClickLabel = if (onLongClick != null) removeLabel else null,
                    onLongClick =
                        if (onLongClick != null) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick()
                            }
                        } else {
                            null
                        },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .onGloballyPositioned { coords ->
                        onThumbnailCenterChanged(coords.boundsInWindow().center)
                    }
                    .clip(shape)
                    .background(cardColor)
                    .border(borderWidth, borderColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            val preview = entry.preview
            if (preview != null) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = remember(preview) { preview.asImageBitmap() },
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                )
            } else {
                Icon(
                    imageVector = if (entry.hasState) Icons.Outlined.HideImage else Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = ArcaduneColors.OnSurfaceDim.copy(alpha = contentAlpha),
                    modifier = Modifier.size(28.dp),
                )
            }
            if (isMenuOpen) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.52f)),
                )
            }
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = entry.title,
            color = ArcaduneColors.OnSurface.copy(alpha = contentAlpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = entry.description.ifEmpty { stringResource(R.string.game_menu_state_empty) },
            color = ArcaduneColors.OnSurfaceDim.copy(alpha = contentAlpha),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
