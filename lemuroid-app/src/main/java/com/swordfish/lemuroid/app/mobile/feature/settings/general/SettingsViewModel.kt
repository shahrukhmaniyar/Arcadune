package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.saves.SavesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.saves.formatSummary
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsSettingsState
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.backup.SavesBackupManager
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val settingsInteractor: SettingsInteractor,
    saveSyncManager: SaveSyncManager,
    sharedPreferences: FlowSharedPreferences,
    private val biosManager: BiosManager,
    directoriesManager: DirectoriesManager,
    private val savesManager: SavesManager,
    private val retrogradeDb: RetrogradeDatabase,
) : ViewModel() {
    class Factory(
        private val context: Context,
        private val settingsInteractor: SettingsInteractor,
        private val saveSyncManager: SaveSyncManager,
        private val sharedPreferences: FlowSharedPreferences,
        private val biosManager: BiosManager,
        private val directoriesManager: DirectoriesManager,
        private val savesManager: SavesManager,
        private val retrogradeDb: RetrogradeDatabase,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                context,
                settingsInteractor,
                saveSyncManager,
                sharedPreferences,
                biosManager,
                directoriesManager,
                savesManager,
                retrogradeDb,
            ) as T
        }
    }

    data class State(
        val currentDirectory: String = "",
        val currentDirectoryName: String = "",
        val isSaveSyncSupported: Boolean = false,
        val savesSummary: String = "",
        val achievementsState: RetroAchievementsSettingsState = RetroAchievementsSettingsState(),
    )

    private val externalFolderPrefKey =
        context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
    private val initialDirectory =
        SharedPreferencesHelper.getLegacySharedPreferences(context)
            .getString(externalFolderPrefKey, "")
            .orEmpty()
    private val initialDirectoryName =
        cachedDirectoryNames[initialDirectory] ?: context.getString(R.string.none)

    private val savesBackupManager = SavesBackupManager(context, directoriesManager, savesManager)
    private val savesSummary =
        MutableStateFlow(
            SavesBackupManager.cachedStats?.formatSummary(context)
                ?: context.getString(R.string.settings_description_saves),
        )

    val indexingInProgress = PendingOperationsMonitor(context).anyLibraryOperationInProgress()

    val directoryScanInProgress = PendingOperationsMonitor(context).isDirectoryScanInProgress()

    private val directoryFlow =
        sharedPreferences.getString(externalFolderPrefKey)
            .asFlow()
            .map { uriString ->
                val resolvedName =
                    if (uriString.isBlank()) {
                        context.getString(R.string.none)
                    } else {
                        cachedDirectoryNames[uriString]
                            ?: runCatching {
                                DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
                            }.getOrNull()
                                ?.also { cachedDirectoryNames[uriString] = it }
                            ?: context.getString(R.string.none)
                    }
                uriString to resolvedName
            }
            .flowOn(Dispatchers.IO)

    val uiState: StateFlow<State> =
        combine(
            directoryFlow,
            savesSummary,
            RetroAchievementsPreferences.observeState(context),
        ) { (directory, directoryName), summary, achievements ->
            State(
                currentDirectory = directory,
                currentDirectoryName = directoryName,
                isSaveSyncSupported = saveSyncManager.isSupported(),
                savesSummary = summary,
                achievementsState = achievements,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                State(
                    currentDirectory = initialDirectory,
                    currentDirectoryName = initialDirectoryName,
                    isSaveSyncSupported = saveSyncManager.isSupported(),
                    savesSummary = savesSummary.value,
                    achievementsState = RetroAchievementsPreferences.readStateSync(context),
                ),
        )

    init {
        refreshSavesSummary()
        warmSubpageCaches()
    }

    fun refreshSavesSummary() {
        viewModelScope.launch {
            runCatching { savesBackupManager.inspectStorage().formatSummary(context) }
                .onSuccess { savesSummary.value = it }
        }
    }

    private fun warmSubpageCaches() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { biosManager.getBiosInfoAsync() }
            runCatching { SavesSettingsViewModel.warmSingleGameOptions(retrogradeDb, savesManager) }
        }
    }

    fun changeLocalStorageFolder() {
        settingsInteractor.changeLocalStorageFolder()
    }

    companion object {
        private val cachedDirectoryNames = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}
