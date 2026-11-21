package com.swordfish.lemuroid.app.shared.achievements

import com.swordfish.libretrodroid.AchievementDef
import com.swordfish.libretrodroid.AchievementsSnapshot
import com.swordfish.libretrodroid.LeaderboardDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class AchievementSessionStatus {
    DISABLED,
    NOT_SIGNED_IN,
    UNSUPPORTED_SYSTEM,
    SIGNING_IN,
    IDENTIFYING,
    LOADED,
    UNSUPPORTED_GAME,
    LOGIN_FAILED,
    ERROR,
}

enum class AchievementKind {
    STANDARD,
    MISSABLE,
    PROGRESSION,
    WIN,
}

data class AchievementUiItem(
    val id: Int,
    val title: String,
    val description: String,
    val badgeUrl: String,
    val badgeLockedUrl: String,
    val points: Int,
    val isUnlocked: Boolean,
    val isUnlockedHardcore: Boolean,
    val isActiveChallenge: Boolean,
    val isUnofficial: Boolean,
    val unlockTimeSeconds: Long,
    val measuredProgress: String,
    val measuredPercent: Float,
    val rarityPercent: Float,
    val rarityHardcorePercent: Float = 0f,
    val kind: AchievementKind,
)

data class LeaderboardUiItem(
    val id: Int,
    val title: String,
    val description: String,
    val trackerValue: String,
    val state: Int,
    val format: Int,
    val lowerIsBetter: Boolean,
) {
    val isAttempting: Boolean
        get() = state == LeaderboardDef.STATE_TRACKING

    val isPrimed: Boolean
        get() = state == LeaderboardDef.STATE_ACTIVE
}

data class LeaderboardTrackerUi(
    val id: Int,
    val display: String,
)

data class ChallengeIndicatorUi(
    val id: Int,
    val title: String,
    val badgeUrl: String,
)

data class ProgressIndicatorUi(
    val id: Int,
    val title: String,
    val badgeUrl: String,
    val measuredProgress: String,
    val measuredPercent: Float,
)

sealed interface HudBannerUi {
    val key: Long

    data class GameLoaded(
        override val key: Long,
        val gameTitle: String,
        val badgeUrl: String,
        val unlockedCount: Int,
        val totalCount: Int,
        val unlockedPoints: Int,
        val totalPoints: Int,
        val hardcore: Boolean,
    ) : HudBannerUi

    data class AchievementUnlocked(
        override val key: Long,
        val id: Int,
        val title: String,
        val description: String,
        val badgeUrl: String,
        val points: Int,
        val hardcore: Boolean,
        val rarityPercent: Float,
    ) : HudBannerUi

    data class GameCompleted(
        override val key: Long,
        val gameTitle: String,
        val badgeUrl: String,
        val hardcore: Boolean,
    ) : HudBannerUi

    data class LeaderboardNotice(
        override val key: Long,
        val id: Int,
        val title: String,
        val subtitle: String,
        val formattedScore: String,
        val rankInfo: String = "",
        val isSubmitted: Boolean = false,
        val isFailed: Boolean = false,
    ) : HudBannerUi

    data class StatusNotice(
        override val key: Long,
        val title: String,
        val subtitle: String,
        val isError: Boolean,
    ) : HudBannerUi
}

data class GameAchievementsUiState(
    val status: AchievementSessionStatus = AchievementSessionStatus.DISABLED,
    val statusMessage: String = "",
    val username: String = "",
    val hardcorePreference: Boolean = false,
    val hardcoreActive: Boolean = false,
    val hardcoreDowngraded: Boolean = false,
    val hardcoreTransitionInProgress: Boolean = false,
    val switchedToCasualForQuickResume: Boolean = false,
    val coreHardcoreSupported: Boolean = true,
    val coreUnsupportedReason: String = "",
    val challengeIndicatorsEnabled: Boolean = true,
    val progressIndicatorsEnabled: Boolean = true,
    val leaderboardPopupsEnabled: Boolean = true,
    val gameId: Int = 0,
    val gameTitle: String = "",
    val gameBadgeUrl: String = "",
    val gameHash: String = "",
    val userAgent: String = "",
    val richPresence: String = "",
    val totalCount: Int = 0,
    val unlockedCount: Int = 0,
    val totalPoints: Int = 0,
    val unlockedPoints: Int = 0,
    val achievements: List<AchievementUiItem> = emptyList(),
    val leaderboards: List<LeaderboardUiItem> = emptyList(),
    val activeLeaderboardTrackers: List<LeaderboardTrackerUi> = emptyList(),
    val activeChallenges: List<ChallengeIndicatorUi> = emptyList(),
    val progressIndicator: ProgressIndicatorUi? = null,
    val isOfflinePendingSync: Boolean = false,
)

/**
 * Process-local reactive state shared between `GameActivity` and `GameMenuActivity`
 * (both live in the `:game` process).
 */
object RetroAchievementsSessionHolder {
    const val HARDCORE_UNREGISTERED_MARKER = "HARDCORE_UNREGISTERED_EMULATOR"

    private val mutableState = MutableStateFlow(GameAchievementsUiState())
    val state: StateFlow<GameAchievementsUiState> = mutableState.asStateFlow()

    private val mutableBanner = MutableStateFlow<HudBannerUi?>(null)
    val activeBanner: StateFlow<HudBannerUi?> = mutableBanner.asStateFlow()

    fun updateState(transform: (GameAchievementsUiState) -> GameAchievementsUiState) {
        mutableState.value = transform(mutableState.value)
    }

    fun reset(initial: GameAchievementsUiState = GameAchievementsUiState()) {
        mutableState.value = initial
        mutableBanner.value = null
    }

    fun setActiveBanner(banner: HudBannerUi?) {
        mutableBanner.value = banner
    }

    suspend fun refreshFromNative() {
        val snap =
            withContext(Dispatchers.Default) {
                runCatching {
                    com.swordfish.libretrodroid.LibretroDroid.getAchievementsSnapshot()
                }.getOrNull()
            } ?: return

        applySnapshot(snap)
    }

    fun applySnapshot(snap: AchievementsSnapshot) {
        if (snap.sessionStatus == AchievementsSnapshot.STATUS_DISABLED) return
        val activeChallengeIds = mutableState.value.activeChallenges.map { it.id }.toSet()
        val mappedItems =
            snap.achievements.map { def ->
                val unlockedHardcore = def.isUnlockedHardcore
                val unlockedAny = if (snap.hardcoreActive) unlockedHardcore else def.isUnlocked
                AchievementUiItem(
                    id = def.id,
                    title = def.title,
                    description = def.description,
                    badgeUrl = def.badgeUrl,
                    badgeLockedUrl = def.badgeLockedUrl.ifBlank { def.badgeUrl },
                    points = def.points,
                    isUnlocked = unlockedAny,
                    isUnlockedHardcore = unlockedHardcore,
                    isActiveChallenge =
                        def.bucket == AchievementDef.BUCKET_ACTIVE_CHALLENGE || def.id in activeChallengeIds,
                    isUnofficial = (def.category and AchievementDef.CATEGORY_UNOFFICIAL) != 0,
                    unlockTimeSeconds = def.unlockTimeSeconds,
                    measuredProgress = def.measuredProgress,
                    measuredPercent = def.measuredPercent.coerceIn(0f, 100f),
                    rarityPercent = if (snap.hardcoreActive) def.rarityHardcore else def.rarity,
                    rarityHardcorePercent = def.rarityHardcore,
                    kind =
                        when (def.type) {
                            AchievementDef.TYPE_MISSABLE -> AchievementKind.MISSABLE
                            AchievementDef.TYPE_PROGRESSION -> AchievementKind.PROGRESSION
                            AchievementDef.TYPE_WIN -> AchievementKind.WIN
                            else -> AchievementKind.STANDARD
                        },
                )
            }

        val mappedLeaderboards =
            snap.leaderboards
                .map { lb ->
                    LeaderboardUiItem(
                        id = lb.id,
                        title = lb.title,
                        description = lb.description,
                        trackerValue = lb.trackerValue,
                        state = lb.state,
                        format = lb.format,
                        lowerIsBetter = lb.lowerIsBetter,
                    )
                }

        val mappedTrackers =
            snap.leaderboardTrackers.map { tracker ->
                LeaderboardTrackerUi(
                    id = tracker.id,
                    display = tracker.display,
                )
            }

        val isDowngraded =
            snap.statusMessage == HARDCORE_UNREGISTERED_MARKER || mutableState.value.hardcoreDowngraded
        val cleanStatusMsg =
            if (snap.statusMessage == HARDCORE_UNREGISTERED_MARKER) {
                ""
            } else {
                snap.statusMessage.ifBlank { mutableState.value.statusMessage }
            }

        mutableState.value =
            mutableState.value.copy(
                status =
                    when (snap.sessionStatus) {
                        AchievementsSnapshot.STATUS_SIGNING_IN -> AchievementSessionStatus.SIGNING_IN
                        AchievementsSnapshot.STATUS_IDENTIFYING -> AchievementSessionStatus.IDENTIFYING
                        AchievementsSnapshot.STATUS_LOADED -> AchievementSessionStatus.LOADED
                        AchievementsSnapshot.STATUS_UNSUPPORTED -> AchievementSessionStatus.UNSUPPORTED_GAME
                        AchievementsSnapshot.STATUS_LOGIN_FAILED -> AchievementSessionStatus.LOGIN_FAILED
                        AchievementsSnapshot.STATUS_LOAD_ERROR -> AchievementSessionStatus.ERROR
                        else -> mutableState.value.status
                    },
                statusMessage = cleanStatusMsg,
                hardcoreActive = snap.hardcoreActive,
                hardcoreDowngraded = isDowngraded,
                gameId = snap.gameId,
                gameTitle = snap.gameTitle,
                gameBadgeUrl = snap.gameBadgeUrl,
                gameHash = snap.gameHash,
                userAgent = snap.userAgent,
                richPresence = snap.richPresence,
                totalCount = snap.totalCount,
                unlockedCount = snap.unlockedCount,
                totalPoints = snap.totalPoints,
                unlockedPoints = snap.unlockedPoints,
                achievements = mappedItems,
                leaderboards = mappedLeaderboards,
                activeLeaderboardTrackers = mappedTrackers,
            )
    }
}
