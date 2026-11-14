package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsConsoleMap
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsSettingsState
import com.swordfish.lemuroid.lib.library.GameSystem
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
    private val retroGameView: GameViewModelRetroGameView,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val bannerChannel = Channel<HudBannerUi>(capacity = 32)
    private val nextBannerKey = AtomicLong(1L)
    private val soundPlayer = RetroAchievementsSoundPlayer(appContext, scope)

    @Volatile
    private var settingsSnapshot: RetroAchievementsSettingsState =
        RetroAchievementsPreferences.readStateSync(appContext)

    val isSystemSupported: Boolean = RetroAchievementsConsoleMap.isSupported(system.id)

    val isHardcoreEnforced: Boolean
        get() {
            if (!isSystemSupported || !settingsSnapshot.isHardcoreActive) return false
            val session = RetroAchievementsSessionHolder.state.value
            return if (session.status == AchievementSessionStatus.LOADED || session.hardcoreDowngraded) {
                session.hardcoreActive
            } else {
                true
            }
        }

    init {
        resetSessionFromPreferences()
        startBannerPresenterLoop()
    }

    fun buildNativeConfig(): AchievementsConfig? {
        val current = RetroAchievementsPreferences.readStateSync(appContext)
        settingsSnapshot = current
        resetSessionFromPreferences(current)

        val consoleId = RetroAchievementsConsoleMap.getConsoleId(system.id)
        val account = current.account
        if (!current.enabled || account == null || consoleId == RetroAchievementsConsoleMap.RC_CONSOLE_UNKNOWN) {
            return null
        }

        AchievementsBridge.appVersion = BuildConfig.VERSION_NAME

        return AchievementsConfig(
            enabled = true,
            username = account.username,
            token = account.token,
            consoleId = consoleId,
            hardcoreEnabled = current.isHardcoreActive,
            encoreEnabled = current.encoreEnabled,
            unofficialEnabled = current.unofficialEnabled,
        )
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        scope.launch {
            RetroAchievementsPreferences.observeState(appContext).collect { latest ->
                settingsSnapshot = latest
                RetroAchievementsSessionHolder.updateState { prev ->
                    prev.copy(
                        challengeIndicatorsEnabled = latest.challengeIndicatorsEnabled,
                        progressIndicatorsEnabled = latest.progressIndicatorsEnabled,
                    )
                }
            }
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

    private fun resetSessionFromPreferences(prefs: RetroAchievementsSettingsState = settingsSnapshot) {
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
                hardcoreActive = isSystemSupported && prefs.isHardcoreActive,
                challengeIndicatorsEnabled = prefs.challengeIndicatorsEnabled,
                progressIndicatorsEnabled = prefs.progressIndicatorsEnabled,
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
            }

            AchievementEvent.TYPE_LOGIN_FAILED -> {
                RetroAchievementsSessionHolder.updateState {
                    it.copy(
                        status = AchievementSessionStatus.LOGIN_FAILED,
                        statusMessage = event.description,
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
                if (prefs.notificationsEnabled && event.totalCount > 0) {
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
                RetroAchievementsSessionHolder.updateState {
                    it.copy(isOfflinePendingSync = false)
                }
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
                        is HudBannerUi.StatusNotice -> 3800L
                    }
                delay(holdDurationMs)
                RetroAchievementsSessionHolder.setActiveBanner(null)
                delay(240L)
            }
        }
    }
}
