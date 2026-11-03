package com.swordfish.lemuroid.app.mobile.feature.settings.advanced

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.lib.saves.backup.SavesArchive
import com.swordfish.lemuroid.lib.saves.backup.SavesBackupManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.cache.CacheCleaner
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class AdvancedSettingsViewModel(
    private val appContext: Context,
    private val settingsInteractor: SettingsInteractor,
) : ViewModel() {
    private val savesBackupManager = SavesBackupManager(appContext, DirectoriesManager(appContext))

    val backupFileName: String
        get() = savesBackupManager.suggestedFileName()

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

    val uiState =
        initializeState(appContext)
            .stateIn(viewModelScope, started = SharingStarted.Lazily, null)

    private fun initializeState(appContext: Context): Flow<State?> =
        flow {
            val supportedCacheValues = CacheCleaner.getSupportedCacheLimits()

            val default = CacheCleaner.getDefaultCacheLimit().toString()

            val displayNames =
                supportedCacheValues
                    .map { getSizeLabel(appContext, it) }

            val values =
                supportedCacheValues
                    .map { it.toString() }

            emit(State(CacheState(default, values, displayNames)))
        }

    private fun getSizeLabel(
        appContext: Context,
        size: Long,
    ): String {
        return Formatter.formatShortFileSize(appContext, size)
    }

    fun resetAllSettings() {
        settingsInteractor.resetAllSettings()
    }

    // Backup and restore must finish (and report) even if the player leaves this screen meanwhile,
    // so they do not run in viewModelScope, which would be cancelled and show a false error.
    @OptIn(DelicateCoroutinesApi::class)
    fun backupSaves(destination: Uri) {
        GlobalScope.launch(Dispatchers.Main) {
            val message =
                try {
                    appContext.getString(R.string.backup_saves_done, savesBackupManager.backup(destination))
                } catch (e: Exception) {
                    Timber.e(e, "Saves backup failed")
                    appContext.getString(R.string.backup_saves_failed, e.message ?: e.javaClass.simpleName)
                }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun restoreSaves(source: Uri) {
        GlobalScope.launch(Dispatchers.Main) {
            val message =
                try {
                    appContext.getString(R.string.restore_saves_done, savesBackupManager.restore(source))
                } catch (e: SavesArchive.InvalidBackupException) {
                    appContext.getString(R.string.restore_saves_invalid, e.message ?: "")
                } catch (e: Exception) {
                    Timber.e(e, "Saves restore failed")
                    appContext.getString(R.string.restore_saves_failed, e.message ?: e.javaClass.simpleName)
                }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
