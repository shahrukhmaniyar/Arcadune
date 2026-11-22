package com.swordfish.lemuroid.app.mobile.feature.settings.achievements

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsAuthClient
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsOfflineQueue
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsSettingsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RetroAchievementsSettingsViewModel(
    context: Context,
) : ViewModel() {
    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RetroAchievementsSettingsViewModel(context.applicationContext) as T
        }
    }

    data class UiState(
        val settings: RetroAchievementsSettingsState = RetroAchievementsSettingsState(),
        val usernameInput: String = "",
        val passwordInput: String = "",
        val isSigningIn: Boolean = false,
        val isRefreshing: Boolean = false,
        val isSyncingOffline: Boolean = false,
        val pendingOfflineCount: Int = 0,
        val errorMessage: String? = null,
    )

    private data class FormState(
        val usernameInput: String = "",
        val passwordInput: String = "",
        val isSigningIn: Boolean = false,
        val isRefreshing: Boolean = false,
        val isSyncingOffline: Boolean = false,
        val pendingOfflineNonce: Int = 0,
        val errorMessage: String? = null,
    )

    private val appContext = context.applicationContext
    private val formState = MutableStateFlow(FormState())
    private var hasAutoRefreshed = false

    val uiState: StateFlow<UiState> =
        combine(
            RetroAchievementsPreferences.observeState(appContext),
            formState,
        ) { settings, form ->
            val pendingCount =
                settings.account?.let {
                    RetroAchievementsOfflineQueue.pendingCountForAccount(appContext, it.username)
                } ?: 0
            UiState(
                settings = settings,
                usernameInput = form.usernameInput,
                passwordInput = form.passwordInput,
                isSigningIn = form.isSigningIn,
                isRefreshing = form.isRefreshing,
                isSyncingOffline = form.isSyncingOffline,
                pendingOfflineCount = pendingCount,
                errorMessage = form.errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState(settings = RetroAchievementsPreferences.readStateSync(appContext)),
        )

    fun onUsernameChanged(value: String) {
        formState.update { it.copy(usernameInput = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        formState.update { it.copy(passwordInput = value, errorMessage = null) }
    }

    fun signIn() {
        val currentForm = formState.value
        if (currentForm.isSigningIn) return

        val username = currentForm.usernameInput.trim()
        val password = currentForm.passwordInput
        if (username.isEmpty() || password.isEmpty()) return

        formState.update { it.copy(isSigningIn = true, errorMessage = null) }
        viewModelScope.launch {
            val result = RetroAchievementsAuthClient.loginWithPassword(username, password)
            result
                .onSuccess { account ->
                    val stored =
                        runCatching {
                            RetroAchievementsPreferences.saveAccount(
                                context = appContext,
                                username = account.username,
                                displayName = account.displayName,
                                token = account.token,
                                score = account.score,
                                softcoreScore = account.softcoreScore,
                                avatarUrl = account.avatarUrl,
                            )
                        }
                    if (stored.isFailure) {
                        formState.update {
                            it.copy(
                                isSigningIn = false,
                                errorMessage = appContext.getString(R.string.ra_sign_in_not_stored),
                            )
                        }
                        return@onSuccess
                    }
                    hasAutoRefreshed = true
                    RetroAchievementsOfflineQueue.flushPending(appContext, account)
                    formState.update {
                        it.copy(
                            passwordInput = "",
                            isSigningIn = false,
                            pendingOfflineNonce = it.pendingOfflineNonce + 1,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    formState.update {
                        it.copy(
                            isSigningIn = false,
                            errorMessage = error.message,
                        )
                    }
                }
        }
    }

    fun refreshAccountIfNeeded() {
        if (hasAutoRefreshed) return
        val account = uiState.value.settings.account ?: return
        if (account.token.isBlank()) return
        hasAutoRefreshed = true
        viewModelScope.launch {
            delay(300)
            refreshAccount(silent = true)
        }
    }

    fun refreshAccount(silent: Boolean = false) {
        if (formState.value.isRefreshing) return
        viewModelScope.launch {
            val current = RetroAchievementsPreferences.currentState(appContext)
            val account = current.account ?: return@launch
            formState.update {
                it.copy(
                    isRefreshing = true,
                    errorMessage = if (silent) it.errorMessage else null,
                )
            }
            RetroAchievementsAuthClient.verifyAndRefreshAccount(account.username, account.token)
                .onSuccess { refreshed ->
                    RetroAchievementsPreferences.updateUserStats(
                        context = appContext,
                        displayName = refreshed.displayName,
                        avatarUrl = refreshed.avatarUrl,
                        score = refreshed.score,
                        softcoreScore = refreshed.softcoreScore,
                    )
                    RetroAchievementsOfflineQueue.flushPending(appContext, refreshed)
                    formState.update {
                        it.copy(
                            isRefreshing = false,
                            pendingOfflineNonce = it.pendingOfflineNonce + 1,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    formState.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = if (silent) it.errorMessage else error.message,
                        )
                    }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            RetroAchievementsPreferences.signOut(appContext)
            hasAutoRefreshed = false
            formState.update {
                it.copy(
                    passwordInput = "",
                    isSigningIn = false,
                    isRefreshing = false,
                    errorMessage = null,
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setEnabled(appContext, enabled)
        }
    }

    fun setHardcoreEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setHardcoreEnabled(appContext, enabled)
        }
    }

    fun setEncoreEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setEncoreEnabled(appContext, enabled)
        }
    }

    fun setUnofficialEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setUnofficialEnabled(appContext, enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setNotificationsEnabled(appContext, enabled)
        }
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setSoundEffectsEnabled(appContext, enabled)
        }
    }

    fun setProgressIndicatorsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setProgressIndicatorsEnabled(appContext, enabled)
        }
    }

    fun setChallengeIndicatorsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setChallengeIndicatorsEnabled(appContext, enabled)
        }
    }

    fun setLeaderboardPopupsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            RetroAchievementsPreferences.setLeaderboardPopupsEnabled(appContext, enabled)
        }
    }

    fun syncPendingOfflineQueue() {
        if (formState.value.isSyncingOffline) return
        viewModelScope.launch {
            val account = RetroAchievementsPreferences.currentState(appContext).account ?: return@launch
            formState.update { it.copy(isSyncingOffline = true, errorMessage = null) }
            RetroAchievementsOfflineQueue.flushPending(appContext, account)
            formState.update {
                it.copy(
                    isSyncingOffline = false,
                    pendingOfflineNonce = it.pendingOfflineNonce + 1,
                )
            }
        }
    }
}
