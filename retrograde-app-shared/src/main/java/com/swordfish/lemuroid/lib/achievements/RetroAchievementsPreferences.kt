package com.swordfish.lemuroid.lib.achievements

import android.content.Context
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class RetroAchievementsAccount(
    val username: String,
    val displayName: String,
    val token: String,
    val avatarUrl: String,
    val score: Int,
    val softcoreScore: Int,
) {
    val totalPoints: Int
        get() = score + softcoreScore
}

data class RetroAchievementsSettingsState(
    val account: RetroAchievementsAccount? = null,
    val enabled: Boolean = true,
    val hardcoreEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
    val progressIndicatorsEnabled: Boolean = true,
    val challengeIndicatorsEnabled: Boolean = true,
    val encoreEnabled: Boolean = false,
    val unofficialEnabled: Boolean = false,
) {
    val isSignedIn: Boolean
        get() = account != null && account.username.isNotBlank() && account.token.isNotBlank()

    val isActive: Boolean
        get() = isSignedIn && enabled

    val isHardcoreActive: Boolean
        get() = isHardcoreActive(RetroAchievementsPreferences.HARDCORE_SUPPORT_APPROVED)

    fun isHardcoreActive(hardcoreApproved: Boolean): Boolean = hardcoreApproved && isActive && hardcoreEnabled
}

object RetroAchievementsPreferences {
    /**
     * Flip to `true` once Arcadune's `User-Agent` is officially registered on
     * RetroAchievements.org's approved Hardcore emulator allowlist.
     */
    const val HARDCORE_SUPPORT_APPROVED = false

    private const val KEY_ENABLED = "ra_enabled"
    private const val KEY_USERNAME = "ra_username"
    private const val KEY_DISPLAY_NAME = "ra_display_name"
    private const val KEY_TOKEN = "ra_token"
    private const val KEY_AVATAR_URL = "ra_avatar_url"
    private const val KEY_SCORE = "ra_score"
    private const val KEY_SOFTCORE_SCORE = "ra_softcore_score"
    private const val KEY_HARDCORE = "ra_hardcore"
    private const val KEY_NOTIFICATIONS = "ra_notifications"
    private const val KEY_SOUND_EFFECTS = "ra_sound_effects"
    private const val KEY_PROGRESS_INDICATORS = "ra_progress_indicators"
    private const val KEY_CHALLENGE_INDICATORS = "ra_challenge_indicators"
    private const val KEY_ENCORE = "ra_encore"
    private const val KEY_UNOFFICIAL = "ra_unofficial"

    private fun prefs(context: Context) = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    fun observeState(context: Context): Flow<RetroAchievementsSettingsState> {
        val flowPrefs = FlowSharedPreferences(prefs(context))

        val accountFlow =
            combine(
                flowPrefs.getString(KEY_USERNAME, "").asFlow(),
                flowPrefs.getString(KEY_DISPLAY_NAME, "").asFlow(),
                flowPrefs.getString(KEY_TOKEN, "").asFlow(),
                flowPrefs.getString(KEY_AVATAR_URL, "").asFlow(),
                combine(
                    flowPrefs.getInt(KEY_SCORE, 0).asFlow(),
                    flowPrefs.getInt(KEY_SOFTCORE_SCORE, 0).asFlow(),
                ) { score, softcore -> score to softcore },
            ) { username, displayName, token, avatarUrl, (score, softcore) ->
                if (username.isNotBlank() && token.isNotBlank()) {
                    RetroAchievementsAccount(
                        username = username,
                        displayName = displayName.ifBlank { username },
                        token = token,
                        avatarUrl =
                            avatarUrl.ifBlank {
                                defaultAvatarUrl(username)
                            },
                        score = score,
                        softcoreScore = softcore,
                    )
                } else {
                    null
                }
            }

        val togglesFlow =
            combine(
                flowPrefs.getBoolean(KEY_ENABLED, true).asFlow(),
                flowPrefs.getBoolean(KEY_HARDCORE, false).asFlow(),
                flowPrefs.getBoolean(KEY_NOTIFICATIONS, true).asFlow(),
                flowPrefs.getBoolean(KEY_SOUND_EFFECTS, true).asFlow(),
                combine(
                    flowPrefs.getBoolean(KEY_PROGRESS_INDICATORS, true).asFlow(),
                    flowPrefs.getBoolean(KEY_CHALLENGE_INDICATORS, true).asFlow(),
                    combine(
                        flowPrefs.getBoolean(KEY_ENCORE, false).asFlow(),
                        flowPrefs.getBoolean(KEY_UNOFFICIAL, false).asFlow(),
                    ) { encore, unofficial -> encore to unofficial },
                ) { progress, challenge, (encore, unofficial) ->
                    OverlayToggles(progress, challenge, encore, unofficial)
                },
            ) { enabled, hardcore, notifications, soundEffects, extra ->
                TogglesSnapshot(
                    enabled = enabled,
                    hardcore = hardcore,
                    notifications = notifications,
                    soundEffects = soundEffects,
                    progress = extra.progress,
                    challenge = extra.challenge,
                    encore = extra.encore,
                    unofficial = extra.unofficial,
                )
            }

        return combine(accountFlow, togglesFlow) { account, toggles ->
            RetroAchievementsSettingsState(
                account = account,
                enabled = toggles.enabled,
                hardcoreEnabled = toggles.hardcore,
                notificationsEnabled = toggles.notifications,
                soundEffectsEnabled = toggles.soundEffects,
                progressIndicatorsEnabled = toggles.progress,
                challengeIndicatorsEnabled = toggles.challenge,
                encoreEnabled = toggles.encore,
                unofficialEnabled = toggles.unofficial,
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun currentState(context: Context): RetroAchievementsSettingsState =
        withContext(Dispatchers.IO) {
            readStateSync(context)
        }

    fun readStateSync(context: Context): RetroAchievementsSettingsState {
        val sp = prefs(context)
        val username = sp.getString(KEY_USERNAME, "").orEmpty()
        val token = sp.getString(KEY_TOKEN, "").orEmpty()
        val displayName = sp.getString(KEY_DISPLAY_NAME, "").orEmpty()
        val avatarUrl = sp.getString(KEY_AVATAR_URL, "").orEmpty()

        val account =
            if (username.isNotBlank() && token.isNotBlank()) {
                RetroAchievementsAccount(
                    username = username,
                    displayName = displayName.ifBlank { username },
                    token = token,
                    avatarUrl = avatarUrl.ifBlank { defaultAvatarUrl(username) },
                    score = sp.getInt(KEY_SCORE, 0),
                    softcoreScore = sp.getInt(KEY_SOFTCORE_SCORE, 0),
                )
            } else {
                null
            }

        return RetroAchievementsSettingsState(
            account = account,
            enabled = sp.getBoolean(KEY_ENABLED, true),
            hardcoreEnabled = sp.getBoolean(KEY_HARDCORE, false),
            notificationsEnabled = sp.getBoolean(KEY_NOTIFICATIONS, true),
            soundEffectsEnabled = sp.getBoolean(KEY_SOUND_EFFECTS, true),
            progressIndicatorsEnabled = sp.getBoolean(KEY_PROGRESS_INDICATORS, true),
            challengeIndicatorsEnabled = sp.getBoolean(KEY_CHALLENGE_INDICATORS, true),
            encoreEnabled = sp.getBoolean(KEY_ENCORE, false),
            unofficialEnabled = sp.getBoolean(KEY_UNOFFICIAL, false),
        )
    }

    suspend fun saveAccount(
        context: Context,
        username: String,
        displayName: String,
        token: String,
        score: Int,
        softcoreScore: Int,
        avatarUrl: String? = null,
    ) = withContext(Dispatchers.IO) {
        prefs(context).edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_DISPLAY_NAME, displayName.ifBlank { username })
            .putString(KEY_TOKEN, token)
            .putString(KEY_AVATAR_URL, avatarUrl?.takeIf { it.isNotBlank() } ?: defaultAvatarUrl(username))
            .putInt(KEY_SCORE, score)
            .putInt(KEY_SOFTCORE_SCORE, softcoreScore)
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    suspend fun updateUserStats(
        context: Context,
        displayName: String?,
        avatarUrl: String?,
        score: Int,
        softcoreScore: Int,
    ) = withContext(Dispatchers.IO) {
        val editor =
            prefs(context).edit()
                .putInt(KEY_SCORE, score)
                .putInt(KEY_SOFTCORE_SCORE, softcoreScore)
        if (!displayName.isNullOrBlank()) {
            editor.putString(KEY_DISPLAY_NAME, displayName)
        }
        if (!avatarUrl.isNullOrBlank()) {
            editor.putString(KEY_AVATAR_URL, avatarUrl)
        }
        editor.apply()
    }

    suspend fun signOut(context: Context) =
        withContext(Dispatchers.IO) {
            prefs(context).edit()
                .remove(KEY_USERNAME)
                .remove(KEY_DISPLAY_NAME)
                .remove(KEY_TOKEN)
                .remove(KEY_AVATAR_URL)
                .remove(KEY_SCORE)
                .remove(KEY_SOFTCORE_SCORE)
                .apply()
        }

    suspend fun setEnabled(
        context: Context,
        value: Boolean,
    ) = setBool(context, KEY_ENABLED, value)

    suspend fun setHardcoreEnabled(
        context: Context,
        value: Boolean,
    ) = setBool(context, KEY_HARDCORE, value)

    suspend fun setNotificationsEnabled(
        context: Context,
        value: Boolean,
    ) = setBool(context, KEY_NOTIFICATIONS, value)

    suspend fun setSoundEffectsEnabled(
        context: Context,
        value: Boolean,
    ) = setBool(context, KEY_SOUND_EFFECTS, value)

    suspend fun setProgressIndicatorsEnabled(
        context: Context,
        value: Boolean,
    ) = setBool(context, KEY_PROGRESS_INDICATORS, value)

    suspend fun setChallengeIndicatorsEnabled(
        context: Context,
        value: Boolean,
    ) = setBool(context, KEY_CHALLENGE_INDICATORS, value)

    suspend fun setEncoreEnabled(
        context: Context,
        value: Boolean,
    ) = setBool(context, KEY_ENCORE, value)

    suspend fun setUnofficialEnabled(
        context: Context,
        value: Boolean,
    ) = setBool(context, KEY_UNOFFICIAL, value)

    private suspend fun setBool(
        context: Context,
        key: String,
        value: Boolean,
    ) = withContext(Dispatchers.IO) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun defaultAvatarUrl(username: String): String {
        return "https://media.retroachievements.org/UserPic/$username.png"
    }

    private data class OverlayToggles(
        val progress: Boolean,
        val challenge: Boolean,
        val encore: Boolean,
        val unofficial: Boolean,
    )

    private data class TogglesSnapshot(
        val enabled: Boolean,
        val hardcore: Boolean,
        val notifications: Boolean,
        val soundEffects: Boolean,
        val progress: Boolean,
        val challenge: Boolean,
        val encore: Boolean,
        val unofficial: Boolean,
    )
}
