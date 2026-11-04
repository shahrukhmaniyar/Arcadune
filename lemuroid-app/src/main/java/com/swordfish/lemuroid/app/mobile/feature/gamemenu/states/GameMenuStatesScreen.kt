package com.swordfish.lemuroid.app.mobile.feature.gamemenu.states

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion

/**
 * Save State and Load State, like Delta: a grid of cards, each with the screenshot taken when the
 * state was saved, its slot and its date. Empty slots can be saved into but not loaded.
 */
@Composable
fun GameMenuStatesScreen(
    viewModel: GameMenuStatesViewModel,
    onStateClicked: (Int) -> Unit,
) {
    val state by viewModel.uiStates.collectAsState(initial = GameMenuStatesViewModel.State())

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(state.entries) { index, entry ->
            StateCard(entry = entry, onClick = { onStateClicked(index) })
        }
    }
}

@Composable
private fun StateCard(
    entry: GameMenuStatesViewModel.StateEntry,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, ArcaduneMotion.press(), label = "press")
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .alpha(if (entry.enabled) 1f else 0.4f)
                .clickable(interactionSource = interactions, indication = null, enabled = entry.enabled, onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, ArcaduneColors.GlassEdge, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.preview != null) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = remember(entry.preview) { entry.preview.asImageBitmap() },
                    contentScale = ContentScale.Crop,
                    contentDescription = entry.title,
                )
            } else {
                Icon(
                    imageVector = if (entry.enabled) Icons.Outlined.AddPhotoAlternate else Icons.Outlined.HideImage,
                    contentDescription = null,
                    tint = ArcaduneColors.OnSurfaceDim,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = entry.title,
            color = ArcaduneColors.OnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = entry.description.ifEmpty { stringResource(R.string.game_menu_state_empty) },
            color = ArcaduneColors.OnSurfaceDim,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
