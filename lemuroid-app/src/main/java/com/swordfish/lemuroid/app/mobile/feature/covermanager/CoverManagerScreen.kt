package com.swordfish.lemuroid.app.mobile.feature.covermanager

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneGlassCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneSystemBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSmallGameImage
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.arcadunePressable
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game

/** Lists the games without a working cover. Tapping one opens its menu, which has every way to fix a cover. */
@Composable
fun CoverManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: CoverManagerViewModel,
    onGameClick: (Game) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val allGood = state.done && state.missing.isEmpty()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp + navBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "status_card") {
                val summary =
                    when {
                        !state.done -> stringResource(R.string.cover_manager_checking, state.checked, state.total)
                        allGood -> stringResource(R.string.cover_manager_all_good)
                        else -> stringResource(R.string.cover_manager_missing, state.missing.size)
                    }
                val statusIcon =
                    when {
                        !state.done -> Icons.Filled.Search
                        allGood -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.Image
                    }

                ArcaduneGlassCard(cornerRadius = 22.dp) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ArcaduneIconBadge(
                                icon = statusIcon,
                                tint = ArcaduneColors.Purple,
                                containerColor = ArcaduneColors.Purple.copy(alpha = 0.16f),
                                borderColor = ArcaduneColors.Purple.copy(alpha = 0.45f),
                                size = 44.dp,
                                iconSize = 22.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = ArcaduneColors.OnSurface,
                                )
                                Text(
                                    text =
                                        if (state.missing.isNotEmpty()) {
                                            stringResource(R.string.cover_manager_hint)
                                        } else {
                                            stringResource(R.string.cover_manager_description)
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ArcaduneColors.OnSurfaceDim,
                                )
                            }
                        }

                        if (!state.done && state.total > 0) {
                            LinearProgressIndicator(
                                progress = { state.checked.toFloat() / state.total },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                                color = ArcaduneColors.Purple,
                                trackColor = ArcaduneColors.SurfaceHigh,
                            )
                        }
                    }
                }
            }

            items(state.missing, key = { it.id }) { game ->
                MissingCoverGameCard(game = game, onClick = { onGameClick(game) })
            }
        }
    }
}

@Composable
private fun MissingCoverGameCard(
    game: Game,
    onClick: () -> Unit,
) {
    val systemTitle = stringResource(id = GameSystem.findById(game.systemId).shortTitleResId)
    val developer = game.developer?.takeIf { it.isNotBlank() }
    val coverShape = RoundedCornerShape(12.dp)

    ArcaduneGlassCard(
        modifier = Modifier.arcadunePressable(scaleDown = 0.97f, onClick = onClick),
        cornerRadius = 18.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(coverShape),
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
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = ArcaduneColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ArcaduneSystemBadge(text = systemTitle)
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
            Spacer(modifier = Modifier.width(10.dp))
            ArcaduneIconBadge(
                icon = Icons.Filled.Search,
                tint = ArcaduneColors.OnSurface,
                containerColor = ArcaduneColors.SurfaceHigh,
            )
        }
    }
}
