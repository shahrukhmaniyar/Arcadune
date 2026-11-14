package com.swordfish.lemuroid.app.mobile.feature.settings.advanced

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.lib.storage.cache.CacheCleaner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AdvancedSettingsViewModel(
    appContext: Context,
    private val settingsInteractor: SettingsInteractor,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val settingsInteractor: SettingsInteractor,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdvancedSettingsViewModel(appContext, settingsInteractor) as T
        }
    }

    data class CacheState(
        val default: String,
        val values: List<String>,
        val displayNames: List<String>,
    )

    data class State(val cache: CacheState)

    val uiState: StateFlow<State> = MutableStateFlow(buildState(appContext)).asStateFlow()

    private fun buildState(appContext: Context): State {
        val supportedCacheValues = CacheCleaner.getSupportedCacheLimits()
        val default = CacheCleaner.getDefaultCacheLimit().toString()
        val displayNames = supportedCacheValues.map { Formatter.formatShortFileSize(appContext, it) }
        val values = supportedCacheValues.map { it.toString() }
        return State(CacheState(default, values, displayNames))
    }

    fun resetAllSettings() {
        settingsInteractor.resetAllSettings()
    }
}
