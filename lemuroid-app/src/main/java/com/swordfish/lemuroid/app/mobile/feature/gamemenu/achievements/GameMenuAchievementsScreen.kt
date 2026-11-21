package com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.achievements.RaGoldColor
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.arcadunePressable
import com.swordfish.lemuroid.app.shared.achievements.AchievementKind
import com.swordfish.lemuroid.app.shared.achievements.AchievementSessionStatus
import com.swordfish.lemuroid.app.shared.achievements.AchievementUiItem
import com.swordfish.lemuroid.app.shared.achievements.GameAchievementsUiState
import com.swordfish.lemuroid.app.shared.achievements.LeaderboardUiItem
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsSessionHolder
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class AchievementFilter {
    ALL,
    LOCKED,
    UNLOCKED,
    ACTIVE,
    LEADERBOARDS,
}

private val MissableAmber = Color(0xFFFFB454)
private val ProgressionTeal = Color(0xFF5FD4C4)
private val CardFill = Color(0xE61C1A27)

@Composable
fun GameMenuAchievementsScreen(
    selectedAchievementId: Int? = null,
    onSelectAchievement: (Int?) -> Unit = {},
    onSwitchHardcoreMode: ((Boolean) -> Unit)? = null,
) {
    val state by RetroAchievementsSessionHolder.state.collectAsState()

    LaunchedEffect(Unit) {
        RetroAchievementsSessionHolder.refreshFromNative()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (state.status) {
            AchievementSessionStatus.SIGNING_IN ->
                LoadingStatusCard(message = stringResource(R.string.ra_menu_signing_in))

            AchievementSessionStatus.IDENTIFYING ->
                LoadingStatusCard(message = stringResource(R.string.ra_menu_identifying))

            AchievementSessionStatus.NOT_SIGNED_IN ->
                EmptyStatusCard(
                    icon = Icons.Filled.EmojiEvents,
                    iconTint = RaGoldColor,
                    title = stringResource(R.string.ra_empty_not_signed_in_title),
                    body = stringResource(R.string.ra_empty_not_signed_in_body),
                )

            AchievementSessionStatus.DISABLED ->
                EmptyStatusCard(
                    icon = Icons.Filled.EmojiEvents,
                    iconTint = ArcaduneColors.OnSurfaceDim,
                    title = stringResource(R.string.ra_empty_disabled_title),
                    body = stringResource(R.string.ra_empty_disabled_body),
                )

            AchievementSessionStatus.UNSUPPORTED_SYSTEM ->
                EmptyStatusCard(
                    icon = Icons.Filled.EmojiEvents,
                    iconTint = ArcaduneColors.OnSurfaceDim,
                    title = stringResource(R.string.ra_empty_unsupported_system_title),
                    body =
                        state.coreUnsupportedReason.ifBlank {
                            state.statusMessage.ifBlank {
                                stringResource(R.string.ra_empty_unsupported_system_body)
                            }
                        },
                )

            AchievementSessionStatus.UNSUPPORTED_GAME ->
                EmptyStatusCard(
                    icon = Icons.Filled.EmojiEvents,
                    iconTint = ArcaduneColors.Purple,
                    title = stringResource(R.string.ra_empty_unsupported_game_title),
                    body =
                        state.statusMessage.ifBlank {
                            stringResource(R.string.ra_empty_unsupported_game_body)
                        },
                )

            AchievementSessionStatus.LOGIN_FAILED ->
                EmptyStatusCard(
                    icon = Icons.Filled.ErrorOutline,
                    iconTint = Color(0xFFFF6B6B),
                    title = stringResource(R.string.ra_empty_login_failed_title),
                    body =
                        state.statusMessage.ifBlank {
                            stringResource(R.string.ra_empty_not_signed_in_body)
                        },
                )

            AchievementSessionStatus.ERROR ->
                EmptyStatusCard(
                    icon = Icons.Filled.ErrorOutline,
                    iconTint = Color(0xFFFF6B6B),
                    title = stringResource(R.string.ra_empty_error_title),
                    body = state.statusMessage,
                )

            AchievementSessionStatus.LOADED ->
                if (state.achievements.isEmpty() && state.leaderboards.isEmpty()) {
                    EmptyStatusCard(
                        icon = Icons.Filled.EmojiEvents,
                        iconTint = ArcaduneColors.Purple,
                        title = stringResource(R.string.ra_empty_unsupported_game_title),
                        body = stringResource(R.string.ra_empty_unsupported_game_body),
                    )
                } else {
                    LoadedAchievementsContent(
                        state = state,
                        selectedAchievementId = selectedAchievementId,
                        onSelectAchievement = onSelectAchievement,
                        onSwitchHardcoreMode = onSwitchHardcoreMode,
                    )
                }
        }
    }
}

@Composable
private fun LoadedAchievementsContent(
    state: GameAchievementsUiState,
    selectedAchievementId: Int?,
    onSelectAchievement: (Int?) -> Unit,
    onSwitchHardcoreMode: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf(AchievementFilter.ALL) }
    var showEnableHardcoreDialog by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val selectedItem =
        remember(state.achievements, selectedAchievementId) {
            selectedAchievementId?.let { id -> state.achievements.firstOrNull { it.id == id } }
        }

    LaunchedEffect(selectedAchievementId, selectedItem) {
        if (selectedAchievementId != null && selectedItem == null) {
            onSelectAchievement(null)
        }
    }

    val unlockedCount = remember(state.achievements) { state.achievements.count { it.isUnlocked } }
    val lockedCount = state.achievements.size - unlockedCount
    val activeCount = remember(state.achievements) { state.achievements.count { it.isActiveChallenge } }
    val leaderboardCount = state.leaderboards.size

    LaunchedEffect(activeCount, leaderboardCount) {
        if (selectedFilter == AchievementFilter.ACTIVE && activeCount == 0) {
            selectedFilter = AchievementFilter.ALL
        } else if (selectedFilter == AchievementFilter.LEADERBOARDS && leaderboardCount == 0) {
            selectedFilter = AchievementFilter.ALL
        }
    }

    val filteredItems =
        remember(state.achievements, selectedFilter) {
            when (selectedFilter) {
                AchievementFilter.ALL ->
                    state.achievements.sortedWith(
                        compareByDescending<AchievementUiItem> { it.isActiveChallenge },
                    )
                AchievementFilter.LOCKED -> state.achievements.filter { !it.isUnlocked }
                AchievementFilter.UNLOCKED -> state.achievements.filter { it.isUnlocked }
                AchievementFilter.ACTIVE -> state.achievements.filter { it.isActiveChallenge }
                AchievementFilter.LEADERBOARDS -> emptyList()
            }
        }

    AnimatedContent(
        targetState = selectedItem,
        transitionSpec = { ArcaduneMotion.appear togetherWith ArcaduneMotion.disappear },
        contentKey = { it?.id },
        label = "raDetailTransition",
    ) { detailItem ->
        if (detailItem != null) {
            AchievementDetailPage(
                item = detailItem,
                hardcoreMode = state.hardcoreActive,
            )
        } else {
            AchievementsListColumn(
                state = state,
                listState = listState,
                selectedFilter = selectedFilter,
                unlockedCount = unlockedCount,
                lockedCount = lockedCount,
                activeCount = activeCount,
                leaderboardCount = leaderboardCount,
                filteredItems = filteredItems,
                onSelectFilter = { selectedFilter = it },
                onSelectAchievement = { onSelectAchievement(it) },
                onRequestModeChange =
                    onSwitchHardcoreMode?.let { callback ->
                        { enable ->
                            if (enable) {
                                if (state.coreHardcoreSupported) {
                                    showEnableHardcoreDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        state.coreUnsupportedReason.ifBlank {
                                            context.getString(R.string.ra_empty_unsupported_system_body)
                                        },
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            } else {
                                callback(false)
                            }
                        }
                    },
            )
        }
    }

    if (showEnableHardcoreDialog && onSwitchHardcoreMode != null) {
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
                        onSwitchHardcoreMode(true)
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

@Composable
private fun AchievementsListColumn(
    state: GameAchievementsUiState,
    listState: LazyListState,
    selectedFilter: AchievementFilter,
    unlockedCount: Int,
    lockedCount: Int,
    activeCount: Int,
    leaderboardCount: Int,
    filteredItems: List<AchievementUiItem>,
    onSelectFilter: (AchievementFilter) -> Unit,
    onSelectAchievement: (Int) -> Unit,
    onRequestModeChange: ((Boolean) -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            GameAchievementsHeaderCard(
                state = state,
                onRequestModeChange = onRequestModeChange,
            )
        }

        item(key = "filters") {
            FilterChipsRow(
                selected = selectedFilter,
                totalCount = state.achievements.size,
                lockedCount = lockedCount,
                unlockedCount = unlockedCount,
                activeCount = activeCount,
                leaderboardCount = leaderboardCount,
                hardcore = state.hardcoreActive,
                onSelect = onSelectFilter,
            )
        }

        if (selectedFilter == AchievementFilter.LEADERBOARDS) {
            if (state.leaderboards.isEmpty()) {
                item(key = "leaderboards_empty") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(CardFill)
                                .border(1.dp, ArcaduneColors.GlassEdge, RoundedCornerShape(18.dp))
                                .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.ra_leaderboards_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ArcaduneColors.OnSurfaceDim,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(
                    items = state.leaderboards,
                    key = { "lb_${it.id}" },
                ) { lb ->
                    LeaderboardRowCard(item = lb, hardcoreMode = state.hardcoreActive)
                }
            }
        } else if (filteredItems.isEmpty()) {
            item(key = "filter_empty") {
                val msg =
                    when (selectedFilter) {
                        AchievementFilter.UNLOCKED -> stringResource(R.string.ra_filter_empty_unlocked)
                        AchievementFilter.LOCKED -> stringResource(R.string.ra_filter_empty_locked)
                        else -> stringResource(R.string.ra_empty_unsupported_game_body)
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardFill)
                            .border(1.dp, ArcaduneColors.GlassEdge, RoundedCornerShape(18.dp))
                            .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArcaduneColors.OnSurfaceDim,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(
                items = filteredItems,
                key = { it.id },
            ) { item ->
                AchievementRowCard(
                    item = item,
                    hardcoreMode = state.hardcoreActive,
                    onClick = { onSelectAchievement(item.id) },
                )
            }
        }
    }
}

@Composable
private fun GameAchievementsHeaderCard(
    state: GameAchievementsUiState,
    onRequestModeChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val accent = if (state.hardcoreActive) RaGoldColor else ArcaduneColors.Purple
    val hardcoreApproved = RetroAchievementsPreferences.isHardcoreApproved()
    val cardShape = RoundedCornerShape(22.dp)
    val completionTarget =
        if (state.totalCount > 0) {
            (state.unlockedCount.toFloat() / state.totalCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val animatedCompletion by animateFloatAsState(
        targetValue = completionTarget,
        animationSpec = ArcaduneMotion.quick(),
        label = "raCompletionBar",
    )

    val gameBadgeRequest =
        remember(context, state.gameBadgeUrl) {
            ImageRequest.Builder(context)
                .data(state.gameBadgeUrl.takeIf { it.isNotBlank() })
                .crossfade(true)
                .build()
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(CardFill)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                    ),
                )
                .border(1.dp, accent.copy(alpha = 0.42f), cardShape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val badgeShape = RoundedCornerShape(14.dp)
            Box(
                modifier =
                    Modifier
                        .size(54.dp)
                        .clip(badgeShape)
                        .background(ArcaduneColors.SurfaceHigh)
                        .border(1.dp, accent.copy(alpha = 0.50f), badgeShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.70f),
                    modifier = Modifier.size(26.dp),
                )
                if (state.gameBadgeUrl.isNotBlank()) {
                    AsyncImage(
                        model = gameBadgeRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.gameTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ArcaduneColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TagBadge(
                        text =
                            stringResource(
                                if (state.hardcoreActive) {
                                    R.string.ra_hud_hardcore_badge
                                } else {
                                    R.string.ra_hud_casual_badge
                                },
                            ),
                        color = accent,
                    )
                }

                if (state.richPresence.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = state.richPresence,
                        style = MaterialTheme.typography.bodySmall,
                        color = ArcaduneColors.OnSurfaceDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (state.isOfflinePendingSync) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MissableAmber,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.ra_menu_offline_sync_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MissableAmber,
                )
            }
        }

        if (!state.coreHardcoreSupported && state.coreUnsupportedReason.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = RaGoldColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = state.coreUnsupportedReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
            }
        } else if (RetroAchievementsPreferences.isHardcoreApproved() && state.hardcoreDowngraded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = RaGoldColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.ra_menu_hardcore_fallback_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
            }
        }

        val showModeButton =
            onRequestModeChange != null &&
                (state.hardcoreActive || (hardcoreApproved && state.coreHardcoreSupported))
        if (showModeButton && onRequestModeChange != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (state.hardcoreActive) {
                    ArcadunePillButton(
                        label = stringResource(R.string.ra_mode_switch_casual_action),
                        height = 34.dp,
                        onClick = { onRequestModeChange(false) },
                    )
                } else {
                    ArcadunePillButton(
                        label = stringResource(R.string.ra_mode_enable_hardcore_action),
                        icon = Icons.Filled.Whatshot,
                        height = 34.dp,
                        onClick = { onRequestModeChange(true) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.ra_menu_progress_counts,
                            state.unlockedCount,
                            state.totalCount,
                        ),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ArcaduneColors.OnSurface,
                )
                Text(
                    text =
                        stringResource(
                            R.string.ra_menu_progress_points,
                            state.unlockedPoints,
                            state.totalPoints,
                        ),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(animatedCompletion)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(accent),
                )
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: AchievementFilter,
    totalCount: Int,
    lockedCount: Int,
    unlockedCount: Int,
    activeCount: Int,
    leaderboardCount: Int,
    hardcore: Boolean,
    onSelect: (AchievementFilter) -> Unit,
) {
    val accent = if (hardcore) RaGoldColor else ArcaduneColors.Purple

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChipPill(
            label = stringResource(R.string.ra_filter_all, totalCount),
            isSelected = selected == AchievementFilter.ALL,
            accent = accent,
            onClick = { onSelect(AchievementFilter.ALL) },
        )
        FilterChipPill(
            label = stringResource(R.string.ra_filter_locked, lockedCount),
            isSelected = selected == AchievementFilter.LOCKED,
            accent = accent,
            onClick = { onSelect(AchievementFilter.LOCKED) },
        )
        FilterChipPill(
            label = stringResource(R.string.ra_filter_unlocked, unlockedCount),
            isSelected = selected == AchievementFilter.UNLOCKED,
            accent = accent,
            onClick = { onSelect(AchievementFilter.UNLOCKED) },
        )
        if (activeCount > 0) {
            FilterChipPill(
                label = stringResource(R.string.ra_filter_active, activeCount),
                isSelected = selected == AchievementFilter.ACTIVE,
                accent = accent,
                onClick = { onSelect(AchievementFilter.ACTIVE) },
            )
        }
        if (leaderboardCount > 0) {
            FilterChipPill(
                label = stringResource(R.string.ra_filter_leaderboards, leaderboardCount),
                isSelected = selected == AchievementFilter.LEADERBOARDS,
                accent = accent,
                onClick = { onSelect(AchievementFilter.LEADERBOARDS) },
            )
        }
    }
}

@Composable
private fun LeaderboardRowCard(
    item: LeaderboardUiItem,
    hardcoreMode: Boolean,
) {
    val accent = if (hardcoreMode) RaGoldColor else ArcaduneColors.Purple
    val shape = RoundedCornerShape(18.dp)
    val stateLabel =
        when {
            item.isAttempting -> stringResource(R.string.ra_leaderboard_state_active)
            item.isPrimed -> stringResource(R.string.ra_leaderboard_state_primed)
            else -> stringResource(R.string.ra_leaderboard_state_inactive)
        }
    val stateColor =
        when {
            item.isAttempting -> accent
            item.isPrimed -> ProgressionTeal
            else -> ArcaduneColors.OnSurfaceDim
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CardFill)
                .background(if (item.isAttempting) accent.copy(alpha = 0.10f) else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (item.isAttempting) accent.copy(alpha = 0.45f) else ArcaduneColors.GlassEdge,
                    shape = shape,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = ArcaduneColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TagBadge(text = stateLabel, color = stateColor)
            }
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcaduneColors.OnSurfaceDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.trackerValue.isNotBlank()) {
            TagBadge(text = item.trackerValue, color = accent)
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val bgColor = if (isSelected) accent else CardFill
    val textColor =
        if (isSelected) {
            if (accent == RaGoldColor) Color(0xFF241700) else ArcaduneColors.OnAccent
        } else {
            ArcaduneColors.OnSurface
        }

    Box(
        modifier =
            Modifier
                .height(36.dp)
                .semantics {
                    role = Role.Tab
                    selected = isSelected
                }
                .arcadunePressable(onClick = onClick)
                .clip(shape)
                .background(bgColor)
                .then(
                    if (isSelected) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, ArcaduneColors.GlassEdge, shape)
                    },
                )
                .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
            color = textColor,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementRowCard(
    item: AchievementUiItem,
    hardcoreMode: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val unlockAccent = if (item.isUnlockedHardcore || hardcoreMode) RaGoldColor else ArcaduneColors.Purple
    val shape = RoundedCornerShape(18.dp)
    val imageUrl = if (item.isUnlocked) item.badgeUrl else item.badgeLockedUrl
    val imageRequest =
        remember(context, imageUrl) {
            ImageRequest.Builder(context)
                .data(imageUrl.takeIf { it.isNotBlank() })
                .crossfade(true)
                .build()
        }

    val containerBg =
        when {
            item.isUnlocked -> unlockAccent.copy(alpha = 0.11f)
            item.isActiveChallenge -> unlockAccent.copy(alpha = 0.08f)
            else -> CardFill
        }
    val borderColor =
        when {
            item.isUnlocked -> unlockAccent.copy(alpha = 0.48f)
            item.isActiveChallenge -> unlockAccent.copy(alpha = 0.40f)
            else -> ArcaduneColors.GlassEdge
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { role = Role.Button }
                .arcadunePressable(onClick = onClick, scaleDown = 0.98f)
                .clip(shape)
                .background(CardFill)
                .background(containerBg)
                .border(1.dp, borderColor, shape)
                .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        val badgeShape = RoundedCornerShape(13.dp)
        Box(
            modifier =
                Modifier
                    .size(54.dp)
                    .clip(badgeShape)
                    .background(ArcaduneColors.SurfaceHigh)
                    .border(
                        width = 1.dp,
                        color = if (item.isUnlocked) unlockAccent.copy(alpha = 0.65f) else ArcaduneColors.GlassEdge,
                        shape = badgeShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.isUnlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (item.isUnlocked) unlockAccent else ArcaduneColors.OnSurfaceDim.copy(alpha = 0.55f),
                modifier = Modifier.size(24.dp),
            )
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .alpha(if (item.isUnlocked) 1f else 0.72f),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = ArcaduneColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (item.isUnlocked) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = unlockAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }

                TagBadge(
                    text = stringResource(R.string.ra_points_label, item.points),
                    color = if (item.isUnlocked) unlockAccent else ArcaduneColors.OnSurfaceDim,
                )
            }

            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcaduneColors.OnSurfaceDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val hasMetaRow =
                item.kind != AchievementKind.STANDARD ||
                    item.isActiveChallenge ||
                    item.isUnofficial ||
                    item.rarityPercent > 0f

            if (hasMetaRow) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (item.isActiveChallenge) {
                        TagBadge(
                            text = stringResource(R.string.ra_badge_active_challenge),
                            color = unlockAccent,
                        )
                    }
                    when (item.kind) {
                        AchievementKind.MISSABLE ->
                            TagBadge(
                                text = stringResource(R.string.ra_kind_missable),
                                color = MissableAmber,
                            )
                        AchievementKind.PROGRESSION ->
                            TagBadge(
                                text = stringResource(R.string.ra_kind_progression),
                                color = ProgressionTeal,
                            )
                        AchievementKind.WIN ->
                            TagBadge(
                                text = stringResource(R.string.ra_kind_win),
                                color = RaGoldColor,
                            )
                        AchievementKind.STANDARD -> Unit
                    }
                    if (item.isUnofficial) {
                        TagBadge(
                            text = stringResource(R.string.ra_badge_unofficial),
                            color = ArcaduneColors.OnSurfaceDim,
                        )
                    }
                    if (item.rarityPercent > 0f) {
                        Text(
                            text = stringResource(R.string.ra_rarity_label, item.rarityPercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = ArcaduneColors.OnSurfaceDim.copy(alpha = 0.80f),
                            maxLines = 1,
                        )
                    }
                }
            }

            if (!item.isUnlocked && item.measuredProgress.isNotBlank()) {
                val fraction = (item.measuredPercent / 100f).coerceIn(0f, 1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(unlockAccent),
                        )
                    }
                    Text(
                        text = item.measuredProgress,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = unlockAccent,
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ArcaduneColors.OnSurfaceDim.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AchievementDetailPage(
    item: AchievementUiItem,
    hardcoreMode: Boolean,
) {
    val context = LocalContext.current
    val accent = if (item.isUnlockedHardcore || hardcoreMode) RaGoldColor else ArcaduneColors.Purple
    val imageUrl = if (item.isUnlocked) item.badgeUrl else item.badgeLockedUrl
    val imageRequest =
        remember(context, imageUrl) {
            ImageRequest.Builder(context)
                .data(imageUrl.takeIf { it.isNotBlank() })
                .crossfade(true)
                .build()
        }

    val formattedUnlockDate =
        remember(item.unlockTimeSeconds) {
            if (item.unlockTimeSeconds > 0L) {
                runCatching {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(item.unlockTimeSeconds * 1000L))
                }.getOrNull()
            } else {
                null
            }
        }

    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(CardFill)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    if (item.isUnlocked) {
                                        accent.copy(alpha = 0.18f)
                                    } else {
                                        accent.copy(alpha = 0.08f)
                                    },
                                    Color.Transparent,
                                ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = if (item.isUnlocked) accent.copy(alpha = 0.48f) else ArcaduneColors.GlassEdge,
                        shape = cardShape,
                    )
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val badgeShape = RoundedCornerShape(20.dp)
                Box(
                    modifier =
                        Modifier
                            .size(84.dp)
                            .clip(badgeShape)
                            .background(ArcaduneColors.SurfaceHigh)
                            .border(
                                width = 1.5.dp,
                                color = if (item.isUnlocked) accent.copy(alpha = 0.70f) else ArcaduneColors.GlassEdge,
                                shape = badgeShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (item.isUnlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (item.isUnlocked) accent else ArcaduneColors.OnSurfaceDim.copy(alpha = 0.55f),
                        modifier = Modifier.size(34.dp),
                    )
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .alpha(if (item.isUnlocked) 1f else 0.78f),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val (statusLabel, statusColor) =
                            when {
                                !RetroAchievementsPreferences.isHardcoreApproved() && item.isUnlocked ->
                                    stringResource(R.string.ra_detail_status_unlocked) to
                                        if (item.isUnlockedHardcore) RaGoldColor else ArcaduneColors.Purple
                                item.isUnlockedHardcore ->
                                    stringResource(R.string.ra_detail_status_hardcore) to RaGoldColor
                                item.isUnlocked ->
                                    stringResource(R.string.ra_detail_status_softcore) to ArcaduneColors.Purple
                                else ->
                                    stringResource(R.string.ra_detail_status_locked) to ArcaduneColors.OnSurfaceDim
                            }
                        TagBadge(text = statusLabel, color = statusColor)

                        if (item.isActiveChallenge) {
                            TagBadge(
                                text = stringResource(R.string.ra_badge_active_challenge),
                                color = accent,
                            )
                        }
                        if (item.isUnofficial) {
                            TagBadge(
                                text = stringResource(R.string.ra_badge_unofficial),
                                color = ArcaduneColors.OnSurfaceDim,
                            )
                        }
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ArcaduneColors.OnSurface,
                    )

                    if (item.isUnlocked && !formattedUnlockDate.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.ra_detail_unlocked_on, formattedUnlockDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = ArcaduneColors.OnSurfaceDim,
                            )
                        }
                    }
                }
            }

            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArcaduneColors.OnSurface.copy(alpha = 0.90f),
                )
            }

            val (kindLabel, kindColor) =
                when (item.kind) {
                    AchievementKind.MISSABLE -> stringResource(R.string.ra_kind_missable) to MissableAmber
                    AchievementKind.PROGRESSION -> stringResource(R.string.ra_kind_progression) to ProgressionTeal
                    AchievementKind.WIN -> stringResource(R.string.ra_kind_win) to RaGoldColor
                    AchievementKind.STANDARD -> stringResource(R.string.ra_kind_standard) to ArcaduneColors.OnSurface
                }

            val rarityText =
                if (item.rarityPercent > 0f) {
                    String.format(Locale.US, "%.1f%%", item.rarityPercent)
                } else {
                    "\u2014"
                }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailStatTile(
                    label = stringResource(R.string.ra_detail_stat_points),
                    value = stringResource(R.string.ra_hud_points_plus, item.points),
                    valueColor = accent,
                    modifier = Modifier.weight(1f),
                )
                DetailStatTile(
                    label = stringResource(R.string.ra_detail_stat_rarity),
                    value = rarityText,
                    valueColor = ArcaduneColors.OnSurface,
                    modifier = Modifier.weight(1f),
                )
                DetailStatTile(
                    label = stringResource(R.string.ra_detail_stat_type),
                    value = kindLabel,
                    valueColor = kindColor,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!item.isUnlocked && item.measuredProgress.isNotBlank()) {
                val fraction = (item.measuredPercent / 100f).coerceIn(0f, 1f)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ArcaduneColors.SurfaceHigh)
                            .border(1.dp, ArcaduneColors.GlassEdge, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.ra_detail_progress_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = ArcaduneColors.OnSurfaceDim,
                        )
                        Text(
                            text = item.measuredProgress,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ArcadunePillButton(
                    label = stringResource(R.string.ra_detail_open_web),
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    height = 42.dp,
                    onClick = {
                        runCatching {
                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://retroachievements.org/achievement/${item.id}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailStatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(1.dp, ArcaduneColors.GlassEdge, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ArcaduneColors.OnSurfaceDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TagBadge(
    text: String,
    color: Color,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.44f), shape)
                .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                ),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun LoadingStatusCard(message: String) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .clip(shape)
                    .background(CardFill)
                    .border(1.dp, ArcaduneColors.GlassEdge, shape)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = RaGoldColor,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = ArcaduneColors.OnSurface,
            )
        }
    }
}

@Composable
private fun EmptyStatusCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    body: String,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CardFill)
                    .border(1.dp, ArcaduneColors.GlassEdge, shape)
                    .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArcaduneIconBadge(
                icon = icon,
                tint = iconTint,
                containerColor = iconTint.copy(alpha = 0.16f),
                borderColor = iconTint.copy(alpha = 0.45f),
                size = 50.dp,
                iconSize = 26.dp,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ArcaduneColors.OnSurface,
                textAlign = TextAlign.Center,
            )
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArcaduneColors.OnSurfaceDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
