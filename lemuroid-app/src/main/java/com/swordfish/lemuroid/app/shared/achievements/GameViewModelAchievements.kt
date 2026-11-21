package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsConsoleMap
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsOfflineQueue
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsSettingsState
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.libretrodroid.AchievementEvent
import com.swordfish.libretrodroid.AchievementsBridge
import com.swordfish.libretrodroid.AchievementsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class GameViewModelAchievements(
    private val appContext: Context,
    private val system: GameSystem,
    private val systemCoreConfig: SystemCoreConfig,
    private val retroGameView: GameViewModelRetroGameView,
    private val scope: CoroutineScope,
    private val onExternalSdkResetRequested: () -> Unit = {},
) : DefaultLifecycleObserver {
    private val bannerChannel = Channel<HudBannerUi>(capacity = 32)
    private val nextBannerKey = AtomicLong(1L)
    private val soundPlayer = RetroAchievementsSoundPlayer(appContext, scope)

    @Volatile
    private var settingsSnapshot: RetroAchievementsSettingsState =
        RetroAchievementsPreferences.readStateSync(appContext)

    val isSystemSupported: Boolean = RetroAchievementsConsoleMap.isSupported(system.id)

    val isCoreHardcoreCompliant: Boolean =
        RetroAchievementsConsoleMap.isCoreHardcoreCompliant(systemCoreConfig.coreID, system.id)

    val coreUnsupportedReason: String =
        RetroAchievementsConsoleMap.hardcoreUnsupportedReason(systemCoreConfig.coreID, system.id).orEmpty()

    /**
     * Authoritative runtime flag consulted by save-state loading, cheats, rewind, and pause
     * cooldown boundaries. True whenever the running session is in Hardcore Mode OR a Hardcore
     * enable transition is actively in progress.
     */
    val isHardcoreEnforced: Boolean
        get() {
            val session = RetroAchievementsSessionHolder.state.value
            if (session.hardcoreTransitionInProgress) return true
            if (!isSystemSupported || !isCoreHardcoreCompliant) return false
            return session.hardcoreActive
        }

    init {
        resetSessionFromPreferences()
        startBannerPresenterLoop()
    }

    fun buildNativeConfig(
        requestedLoadSave: Boolean,
        explicitQuickResume: Boolean,
        hasAutoSaveSnapshot: Boolean,
    ): Pair<AchievementsConfig?, HardcorePolicy.LaunchDecision> {
        val current = RetroAchievementsPreferences.readStateSync(appContext)
        settingsSnapshot = current

        val consoleId = RetroAchievementsConsoleMap.getConsoleId(system.id)
        val account = current.account
        val raEnabledAndSignedIn =
            current.enabled &&
                account != null &&
                consoleId != RetroAchievementsConsoleMap.RC_CONSOLE_UNKNOWN

        val decision =
            HardcorePolicy.resolveLaunchDecision(
                raEnabledAndSignedIn = raEnabledAndSignedIn,
                hardcoreApproved = RetroAchievementsPreferences.isHardcoreApproved(),
                hardcorePreferenceEnabled = current.hardcoreEnabled,
                coreId = systemCoreConfig.coreID,
                systemId = system.id,
                requestedLoadSave = requestedLoadSave,
                explicitQuickResume = explicitQuickResume,
                hasAutoSaveSnapshot = hasAutoSaveSnapshot,
            )

        resetSessionFromPreferences(
            prefs = current,
            effectiveHardcore = decision.effectiveHardcore,
            switchedToCasualForQuickResume = decision.switchedToCasualForQuickResume,
        )

        if (decision.switchedToCasualForQuickResume && current.notificationsEnabled) {
            enqueueBanner(
                HudBannerUi.StatusNotice(
                    key = nextBannerKey.getAndIncrement(),
                    title = appContext.getString(R.string.ra_quick_resume_casual_title),
                    subtitle = appContext.getString(R.string.ra_quick_resume_casual_subtitle),
                    isError = false,
                ),
            )
        }

        if (!raEnabledAndSignedIn || account == null) {
            return null to decision
        }

        AchievementsBridge.appVersion = BuildConfig.VERSION_NAME

        val config =
            AchievementsConfig(
                enabled = true,
                username = account.username,
                token = account.token,
                consoleId = consoleId,
                hardcoreEnabled = decision.effectiveHardcore,
                encoreEnabled = current.encoreEnabled,
                unofficialEnabled = current.unofficialEnabled,
                serverHost = BuildConfig.RA_SERVER_HOST,
                appVersion = BuildConfig.VERSION_NAME,
                osRelease = Build.VERSION.RELEASE ?: "Android",
                coreHardcoreAllowed = decision.coreHardcoreSupported,
                coreUnsupportedReason = decision.coreUnsupportedReason,
            )
        return config to decision
    }

    fun setTransitionInProgress(inProgress: Boolean) {
        RetroAchievementsSessionHolder.updateState {
            it.copy(hardcoreTransitionInProgress = inProgress)
        }
    }

    fun showStatusBanner(
        title: String,
        subtitle: String,
        isError: Boolean,
    ) {
        enqueueBanner(
            HudBannerUi.StatusNotice(
                key = nextBannerKey.getAndIncrement(),
                title = title,
                subtitle = subtitle,
                isError = isError,
            ),
        )
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        scope.launch {
            RetroAchievementsPreferences.observeState(appContext).collect { latest ->
                settingsSnapshot = latest
                val pendingCount =
                    latest.account?.let {
                        RetroAchievementsOfflineQueue.pendingCountForAccount(appContext, it.username)
                    } ?: 0
                RetroAchievementsSessionHolder.updateState { prev ->
                    prev.copy(
                        hardcorePreference = latest.hardcoreEnabled,
                        challengeIndicatorsEnabled = latest.challengeIndicatorsEnabled,
                        progressIndicatorsEnabled = latest.progressIndicatorsEnabled,
                        leaderboardPopupsEnabled = latest.leaderboardPopupsEnabled,
                        isOfflinePendingSync = prev.isOfflinePendingSync || pendingCount > 0,
                    )
                }
            }
        }

        // Flush any durable offline submissions belonging exclusively to the signed-in account.
        scope.launch(Dispatchers.IO) {
            flushOfflineQueueForActiveAccount()
        }

        scope.launch(Dispatchers.Default) {
            val view = retroGameView.retroGameViewFlow()
            view.getAchievementEvents().collect { event ->
                handleNativeEvent(event)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        soundPlayer.release()
        RetroAchievementsSessionHolder.reset()
        super.onDestroy(owner)
    }

    private suspend fun flushOfflineQueueForActiveAccount() {
        val account = settingsSnapshot.account ?: return
        val summary = RetroAchievementsOfflineQueue.flushPending(appContext, account)
        RetroAchievementsSessionHolder.updateState {
            it.copy(isOfflinePendingSync = summary.remainingForAccount > 0)
        }
    }

    private fun resetSessionFromPreferences(
        prefs: RetroAchievementsSettingsState = settingsSnapshot,
        effectiveHardcore: Boolean = isSystemSupported && isCoreHardcoreCompliant && prefs.isHardcoreActive,
        switchedToCasualForQuickResume: Boolean = false,
    ) {
        val initialStatus =
            when {
                prefs.account == null -> AchievementSessionStatus.NOT_SIGNED_IN
                !prefs.enabled -> AchievementSessionStatus.DISABLED
                !isSystemSupported -> AchievementSessionStatus.UNSUPPORTED_SYSTEM
                else -> AchievementSessionStatus.SIGNING_IN
            }

        RetroAchievementsSessionHolder.reset(
            GameAchievementsUiState(
                status = initialStatus,
                username = prefs.account?.displayName.orEmpty(),
                hardcorePreference = prefs.hardcoreEnabled,
                hardcoreActive = effectiveHardcore,
                switchedToCasualForQuickResume = switchedToCasualForQuickResume,
                coreHardcoreSupported = isCoreHardcoreCompliant,
                coreUnsupportedReason = coreUnsupportedReason,
                challengeIndicatorsEnabled = prefs.challengeIndicatorsEnabled,
                progressIndicatorsEnabled = prefs.progressIndicatorsEnabled,
                leaderboardPopupsEnabled = prefs.leaderboardPopupsEnabled,
            ),
        )
    }

    private suspend fun handleNativeEvent(event: AchievementEvent) {
        val prefs = settingsSnapshot
        when (event.type) {
            AchievementEvent.TYPE_LOGIN_SUCCEEDED -> {
                RetroAchievementsPreferences.updateUserStats(
                    context = appContext,
                    displayName = event.title,
                    avatarUrl = event.badgeUrl,
                    score = event.points,
                    softcoreScore = event.totalPoints,
                )
                flushOfflineQueueForActiveAccount()
            }

            AchievementEvent.TYPE_LOGIN_FAILED -> {
                RetroAchievementsSessionHolder.updateState {
                    it.copy(
                        status = AchievementSessionStatus.LOGIN_FAILED,
                        statusMessage = event.description,
                        hardcoreActive = false,
                    )
                }
                enqueueBanner(
                    HudBannerUi.StatusNotice(
                        key = nextBannerKey.getAndIncrement(),
                        title = "RetroAchievements Sign-In Failed",
                        subtitle = event.description.ifBlank { "Open Settings to sign in again" },
                        isError = true,
                    ),
                )
            }

            AchievementEvent.TYPE_GAME_IDENTIFYING -> {
                RetroAchievementsSessionHolder.updateState {
                    it.copy(status = AchievementSessionStatus.IDENTIFYING)
                }
            }

            AchievementEvent.TYPE_GAME_LOADED -> {
                if (event.description == RetroAchievementsSessionHolder.HARDCORE_UNREGISTERED_MARKER) {
                    RetroAchievementsSessionHolder.updateState {
                        it.copy(hardcoreActive = false, hardcoreDowngraded = true)
                    }
                }
                RetroAchievementsSessionHolder.refreshFromNative()
                if (prefs.notificationsEnabled && (event.totalCount > 0 || event.title.isNotBlank())) {
                    enqueueBanner(
                        HudBannerUi.GameLoaded(
                            key = nextBannerKey.getAndIncrement(),
                            gameTitle = event.title,
                            badgeUrl = event.badgeUrl,
                            unlockedCount = event.unlockedCount,
                            totalCount = event.totalCount,
                            unlockedPoints = event.points,
                            totalPoints = event.totalPoints,
                            hardcore = event.hardcore,
                        ),
                    )
                }
            }

            AchievementEvent.TYPE_GAME_UNSUPPORTED -> {
                RetroAchievementsSessionHolder.updateState {
                    it.copy(
                        status = AchievementSessionStatus.UNSUPPORTED_GAME,
                        statusMessage = event.description,
                    )
                }
            }

            AchievementEvent.TYPE_ACHIEVEMENT_TRIGGERED -> {
                RetroAchievementsSessionHolder.refreshFromNative()
                if (prefs.soundEffectsEnabled) {
                    soundPlayer.playUnlockCelebration(
                        fanfare = event.hardcore,
                        playAudio = retroGameView.retroGameView?.audioEnabled != false,
                    )
                }
                if (prefs.notificationsEnabled) {
                    enqueueBanner(
                        HudBannerUi.AchievementUnlocked(
                            key = nextBannerKey.getAndIncrement(),
                            id = event.id,
                            title = event.title,
                            description = event.description,
                            badgeUrl = event.badgeUrl,
                            points = event.points,
                            hardcore = event.hardcore,
                            rarityPercent = event.rarity,
                        ),
                    )
                }
            }

            AchievementEvent.TYPE_PROGRESS_SHOW -> {
                if (prefs.progressIndicatorsEnabled) {
                    RetroAchievementsSessionHolder.updateState {
                        it.copy(
                            progressIndicator =
                                ProgressIndicatorUi(
                                    id = event.id,
                                    title = event.title,
                                    badgeUrl = event.badgeUrl,
                                    measuredProgress = event.measuredProgress,
                                    measuredPercent = event.measuredPercent.coerceIn(0f, 100f),
                                ),
                        )
                    }
                }
            }

            AchievementEvent.TYPE_PROGRESS_HIDE -> {
                RetroAchievementsSessionHolder.updateState {
                    it.copy(progressIndicator = null)
                }
            }

            AchievementEvent.TYPE_CHALLENGE_SHOW -> {
                RetroAchievementsSessionHolder.updateState { prev ->
                    val existing = prev.activeChallenges.filterNot { it.id == event.id }
                    prev.copy(
                        activeChallenges =
                            existing +
                                ChallengeIndicatorUi(
                                    id = event.id,
                                    title = event.title,
                                    badgeUrl = event.badgeUrl,
                                ),
                    )
                }
            }

            AchievementEvent.TYPE_CHALLENGE_HIDE -> {
                RetroAchievementsSessionHolder.updateState { prev ->
                    prev.copy(
                        activeChallenges = prev.activeChallenges.filterNot { it.id == event.id },
                    )
                }
            }

            AchievementEvent.TYPE_GAME_COMPLETED -> {
                RetroAchievementsSessionHolder.refreshFromNative()
                if (prefs.soundEffectsEnabled) {
                    soundPlayer.playUnlockCelebration(
                        fanfare = true,
                        playAudio = retroGameView.retroGameView?.audioEnabled != false,
                    )
                }
                if (prefs.notificationsEnabled) {
                    enqueueBanner(
                        HudBannerUi.GameCompleted(
                            key = nextBannerKey.getAndIncrement(),
                            gameTitle = event.title,
                            badgeUrl = event.badgeUrl,
                            hardcore = event.hardcore,
                        ),
                    )
                }
            }

            AchievementEvent.TYPE_DISCONNECTED -> {
                RetroAchievementsSessionHolder.updateState {
                    it.copy(isOfflinePendingSync = true)
                }
            }

            AchievementEvent.TYPE_RECONNECTED -> {
                flushOfflineQueueForActiveAccount()
            }

            AchievementEvent.TYPE_LEADERBOARD_STARTED -> {
                // Leaderboard evaluation and state refresh ALWAYS execute regardless of popup preference.
                RetroAchievementsSessionHolder.refreshFromNative()
                if (prefs.leaderboardPopupsEnabled && prefs.notificationsEnabled) {
                    enqueueBanner(
                        HudBannerUi.LeaderboardNotice(
                            key = nextBannerKey.getAndIncrement(),
                            id = event.id,
                            title = event.title,
                            subtitle = event.description,
                            formattedScore = event.measuredProgress,
                        ),
                    )
                }
            }

            AchievementEvent.TYPE_LEADERBOARD_FAILED -> {
                RetroAchievementsSessionHolder.refreshFromNative()
                if (prefs.leaderboardPopupsEnabled && prefs.notificationsEnabled) {
                    enqueueBanner(
                        HudBannerUi.LeaderboardNotice(
                            key = nextBannerKey.getAndIncrement(),
                            id = event.id,
                            title = event.title,
                            subtitle = "Attempt canceled",
                            formattedScore = "",
                            isFailed = true,
                        ),
                    )
                }
            }

            AchievementEvent.TYPE_LEADERBOARD_SUBMITTED,
            AchievementEvent.TYPE_LEADERBOARD_SCOREBOARD,
            -> {
                RetroAchievementsSessionHolder.refreshFromNative()
                if (prefs.leaderboardPopupsEnabled && prefs.notificationsEnabled) {
                    enqueueBanner(
                        HudBannerUi.LeaderboardNotice(
                            key = nextBannerKey.getAndIncrement(),
                            id = event.id,
                            title = event.title,
                            subtitle = event.description,
                            formattedScore = event.measuredProgress,
                            rankInfo = event.badgeUrl,
                            isSubmitted = true,
                        ),
                    )
                }
            }

            AchievementEvent.TYPE_LEADERBOARD_TRACKER_SHOW,
            AchievementEvent.TYPE_LEADERBOARD_TRACKER_HIDE,
            -> {
                // Keep active leaderboard tracker data synced; RetroAchievementsOverlay gates presentation on leaderboardPopupsEnabled.
                RetroAchievementsSessionHolder.refreshFromNative()
            }

            AchievementEvent.TYPE_HARDCORE_MODE_CHANGED -> {
                RetroAchievementsSessionHolder.updateState { prev ->
                    prev.copy(
                        hardcoreActive = event.hardcore,
                        activeChallenges = if (event.hardcore) emptyList() else prev.activeChallenges,
                        progressIndicator = if (event.hardcore) null else prev.progressIndicator,
                    )
                }
                RetroAchievementsSessionHolder.refreshFromNative()
            }

            AchievementEvent.TYPE_RESET_REQUIRED -> {
                onExternalSdkResetRequested()
            }
        }
    }

    private fun enqueueBanner(banner: HudBannerUi) {
        bannerChannel.trySend(banner)
    }

    private fun startBannerPresenterLoop() {
        scope.launch {
            for (banner in bannerChannel) {
                RetroAchievementsSessionHolder.setActiveBanner(banner)
                val holdDurationMs =
                    when (banner) {
                        is HudBannerUi.AchievementUnlocked -> 4200L
                        is HudBannerUi.GameCompleted -> 4600L
                        is HudBannerUi.GameLoaded -> 3400L
                        is HudBannerUi.LeaderboardNotice -> 3600L
                        is HudBannerUi.StatusNotice -> 3800L
                    }
                delay(holdDurationMs)
                RetroAchievementsSessionHolder.setActiveBanner(null)
                delay(240L)
            }
        }
    }
}
