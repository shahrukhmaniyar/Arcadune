package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.achievements.RaGoldColor
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.shared.achievements.ChallengeIndicatorUi
import com.swordfish.lemuroid.app.shared.achievements.HudBannerUi
import com.swordfish.lemuroid.app.shared.achievements.ProgressIndicatorUi
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsSessionHolder

private val HudGlassFill = Color(0xED14121E)
private val HudErrorTint = Color(0xFFFF6B6B)

/**
 * Non-interactive in-game RetroAchievements HUD overlay.
 * Uses zero pointer-input modifiers so 100% of touches pass directly to the controller skin and game screen.
 */
@Composable
fun RetroAchievementsOverlay(modifier: Modifier = Modifier) {
    val activeBanner by RetroAchievementsSessionHolder.activeBanner.collectAsState()
    val sessionState by RetroAchievementsSessionHolder.state.collectAsState()

    var retainedBanner by remember { mutableStateOf<HudBannerUi?>(null) }
    if (activeBanner != null) {
        retainedBanner = activeBanner
    }

    val safeTopInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(safeTopInsets)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (sessionState.challengeIndicatorsEnabled && sessionState.activeChallenges.isNotEmpty()) {
            ActiveChallengesCluster(
                challenges = sessionState.activeChallenges,
                hardcore = sessionState.hardcoreActive,
                modifier = Alignment.TopEnd.let { Modifier.align(it) },
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = activeBanner != null,
                enter =
                    slideInVertically(
                        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
                        initialOffsetY = { -it - 32 },
                    ) +
                        fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(
                            animationSpec = spring(dampingRatio = 0.80f, stiffness = 460f),
                            initialScale = 0.92f,
                        ),
                exit =
                    slideOutVertically(
                        animationSpec = spring(dampingRatio = 1f, stiffness = 600f),
                        targetOffsetY = { -it - 24 },
                    ) +
                        fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleOut(targetScale = 0.95f),
            ) {
                retainedBanner?.let { banner ->
                    HudBannerCapsule(banner = banner)
                }
            }

            val progress = sessionState.progressIndicator
            AnimatedVisibility(
                visible = activeBanner == null && sessionState.progressIndicatorsEnabled && progress != null,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) + scaleIn(initialScale = 0.94f),
                exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.94f),
            ) {
                if (progress != null) {
                    ProgressTrackerCapsule(
                        progress = progress,
                        hardcore = sessionState.hardcoreActive,
                    )
                }
            }
        }
    }
}

@Composable
private fun HudBannerCapsule(banner: HudBannerUi) {
    when (banner) {
        is HudBannerUi.AchievementUnlocked -> AchievementUnlockedCapsule(banner)
        is HudBannerUi.GameLoaded -> GameLoadedCapsule(banner)
        is HudBannerUi.GameCompleted -> GameCompletedCapsule(banner)
        is HudBannerUi.StatusNotice -> StatusNoticeCapsule(banner)
    }
}

@Composable
private fun AchievementUnlockedCapsule(banner: HudBannerUi.AchievementUnlocked) {
    val accent = if (banner.hardcore) RaGoldColor else ArcaduneColors.Purple
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier =
            Modifier
                .widthIn(max = 380.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(HudGlassFill)
                .background(
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                accent.copy(alpha = 0.20f),
                                Color.Transparent,
                            ),
                    ),
                )
                .border(1.25.dp, accent.copy(alpha = 0.65f), shape)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HudBadgeArtwork(
            url = banner.badgeUrl,
            size = 46.dp,
            borderColor = accent.copy(alpha = 0.70f),
        )

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            if (banner.hardcore) {
                                R.string.ra_hud_hardcore_unlocked_header
                            } else {
                                R.string.ra_hud_unlocked_header
                            },
                        ),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            fontSize = 10.sp,
                        ),
                    color = accent,
                    maxLines = 1,
                )
                if (banner.points > 0) {
                    HudMiniPill(
                        text = stringResource(R.string.ra_hud_points_plus, banner.points),
                        color = accent,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = banner.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = ArcaduneColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (banner.description.isNotBlank()) {
                Text(
                    text = banner.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcaduneColors.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GameLoadedCapsule(banner: HudBannerUi.GameLoaded) {
    val accent = if (banner.hardcore) RaGoldColor else ArcaduneColors.Purple
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier =
            Modifier
                .widthIn(max = 360.dp)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(HudGlassFill)
                .border(1.dp, accent.copy(alpha = 0.45f), shape)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        HudBadgeArtwork(
            url = banner.badgeUrl,
            size = 40.dp,
            borderColor = accent.copy(alpha = 0.50f),
        )

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = banner.gameTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ArcaduneColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (banner.hardcore) {
                    HudMiniPill(
                        text = stringResource(R.string.ra_hud_hardcore_badge),
                        color = RaGoldColor,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    stringResource(
                        R.string.ra_hud_game_loaded_stats,
                        banner.unlockedCount,
                        banner.totalCount,
                        banner.unlockedPoints,
                        banner.totalPoints,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = ArcaduneColors.OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GameCompletedCapsule(banner: HudBannerUi.GameCompleted) {
    val accent = if (banner.hardcore) RaGoldColor else ArcaduneColors.Purple
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier =
            Modifier
                .widthIn(max = 360.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(HudGlassFill)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                    ),
                )
                .border(1.5.dp, accent.copy(alpha = 0.75f), shape)
                .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HudBadgeArtwork(
            url = banner.badgeUrl,
            size = 44.dp,
            borderColor = accent,
        )

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text =
                    stringResource(
                        if (banner.hardcore) {
                            R.string.ra_hud_mastered_header
                        } else {
                            R.string.ra_hud_completed_header
                        },
                    ),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.7.sp,
                    ),
                color = accent,
                maxLines = 1,
            )
            Text(
                text = banner.gameTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = ArcaduneColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusNoticeCapsule(banner: HudBannerUi.StatusNotice) {
    val accent = if (banner.isError) HudErrorTint else ArcaduneColors.Purple
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier =
            Modifier
                .widthIn(max = 340.dp)
                .shadow(10.dp, shape)
                .clip(shape)
                .background(HudGlassFill)
                .border(1.dp, accent.copy(alpha = 0.50f), shape)
                .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (banner.isError) Icons.Filled.ErrorOutline else Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = banner.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ArcaduneColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (banner.subtitle.isNotBlank()) {
                Text(
                    text = banner.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcaduneColors.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProgressTrackerCapsule(
    progress: ProgressIndicatorUi,
    hardcore: Boolean,
) {
    val accent = if (hardcore) RaGoldColor else ArcaduneColors.Purple
    val shape = RoundedCornerShape(18.dp)
    val fraction = (progress.measuredPercent / 100f).coerceIn(0f, 1f)

    Row(
        modifier =
            Modifier
                .widthIn(min = 190.dp, max = 300.dp)
                .shadow(10.dp, shape)
                .clip(shape)
                .background(HudGlassFill)
                .border(1.dp, ArcaduneColors.GlassEdge, shape)
                .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HudBadgeArtwork(
            url = progress.badgeUrl,
            size = 30.dp,
            borderColor = accent.copy(alpha = 0.50f),
        )

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = progress.title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = ArcaduneColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = progress.measuredProgress,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(accent),
                )
            }
        }
    }
}

@Composable
private fun ActiveChallengesCluster(
    challenges: List<ChallengeIndicatorUi>,
    hardcore: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (hardcore) RaGoldColor else ArcaduneColors.Purple
    val visibleChallenges = challenges.take(4)
    val extraCount = challenges.size - visibleChallenges.size

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visibleChallenges.forEach { challenge ->
            HudBadgeArtwork(
                url = challenge.badgeUrl,
                size = 28.dp,
                borderColor = accent.copy(alpha = 0.72f),
            )
        }
        if (extraCount > 0) {
            Box(
                modifier =
                    Modifier
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(HudGlassFill)
                        .border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$extraCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun HudBadgeArtwork(
    url: String,
    size: androidx.compose.ui.unit.Dp,
    borderColor: Color,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape((size.value * 0.26f).dp)
    val request =
        remember(context, url) {
            ImageRequest.Builder(context)
                .data(url.takeIf { it.isNotBlank() })
                .crossfade(true)
                .build()
        }

    Box(
        modifier =
            Modifier
                .size(size)
                .clip(shape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = RaGoldColor.copy(alpha = 0.70f),
            modifier = Modifier.size(size * 0.52f),
        )
        if (url.isNotBlank()) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HudMiniPill(
    text: String,
    color: Color,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(color.copy(alpha = 0.18f))
                .border(1.dp, color.copy(alpha = 0.50f), shape)
                .padding(horizontal = 6.dp, vertical = 1.5.dp),
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                ),
            color = color,
            maxLines = 1,
        )
    }
}
