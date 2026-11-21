package com.swordfish.lemuroid.app.mobile.feature.settings.saves

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.achievements.HardcorePolicy
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.backup.SavesArchive
import com.swordfish.lemuroid.lib.saves.backup.SavesBackupManager
import com.swordfish.lemuroid.lib.saves.backup.SavesStorageInspector
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SavesSettingsViewModel(
    private val appContext: Context,
    directoriesManager: DirectoriesManager,
    private val savesManager: SavesManager,
    private val retrogradeDb: RetrogradeDatabase,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val directoriesManager: DirectoriesManager,
        private val savesManager: SavesManager,
        private val retrogradeDb: RetrogradeDatabase,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SavesSettingsViewModel(appContext, directoriesManager, savesManager, retrogradeDb) as T
        }
    }

    data class GameSaveOption(
        val game: Game,
        val hasSave: Boolean,
        val saveSizeBytes: Long,
    )

    data class State(
        val stats: SavesStorageInspector.SavesStorageStats,
        val singleGameOptions: List<GameSaveOption> = emptyList(),
        val isBusy: Boolean = false,
    )

    private val savesBackupManager = SavesBackupManager(appContext, directoriesManager)
    private val _uiState =
        MutableStateFlow(
            State(
                stats = savesBackupManager.initialOrCachedStats(),
                singleGameOptions = cachedSingleGameOptions.orEmpty(),
            ),
        )
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    val backupFileName: String
        get() = savesBackupManager.suggestedFileName()

    init {
        refresh(deferForTransition = SavesBackupManager.cachedStats != null)
    }

    fun suggestedGameSaveFileName(game: Game): String = SavesManager.saveFileNameFor(game)

    fun refresh(deferForTransition: Boolean = false) {
        viewModelScope.launch {
            if (deferForTransition) {
                delay(260)
            }
            val stats =
                runCatching { savesBackupManager.inspectStorage() }
                    .onFailure { Timber.e(it, "Failed to inspect save storage") }
                    .getOrNull()
            val options =
                runCatching { warmSingleGameOptions(retrogradeDb, savesManager) }
                    .onFailure { Timber.e(it, "Failed to load games for single-save picker") }
                    .getOrNull()
            _uiState.update { current ->
                current.copy(
                    stats = stats ?: current.stats,
                    singleGameOptions = options ?: current.singleGameOptions,
                )
            }
        }
    }

    companion object {
        @Volatile
        var cachedSingleGameOptions: List<GameSaveOption>? = null
            private set

        suspend fun warmSingleGameOptions(
            retrogradeDb: RetrogradeDatabase,
            savesManager: SavesManager,
        ): List<GameSaveOption> {
            val games =
                withContext(Dispatchers.IO) {
                    retrogradeDb.gameDao().selectAllFlow().first()
                }
            return withContext(Dispatchers.IO) {
                games
                    .asSequence()
                    .filter { SavesStorageInspector.supportsSingleFileSave(it.systemId) }
                    .map { game ->
                        val saveFile = savesManager.getSaveRAMFile(game)
                        val size = if (saveFile.exists() && saveFile.length() > 0L) saveFile.length() else 0L
                        GameSaveOption(game = game, hasSave = size > 0L, saveSizeBytes = size)
                    }
                    .sortedBy { it.game.displayName().lowercase() }
                    .toList()
                    .also { cachedSingleGameOptions = it }
            }
        }
    }

    // Export, backup, restore, and single-save transfers must complete and notify even if the user navigates away.
    @OptIn(DelicateCoroutinesApi::class)
    private fun runSaveOperation(
        logTag: String,
        failureStringRes: Int,
        invalidMessage: ((SavesArchive.InvalidBackupException) -> String)? = null,
        action: suspend () -> String,
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(isBusy = true) }
            val message =
                try {
                    action()
                } catch (e: SavesArchive.InvalidBackupException) {
                    invalidMessage?.invoke(e)
                        ?: appContext.getString(failureStringRes, e.message ?: e.javaClass.simpleName)
                } catch (e: Exception) {
                    Timber.e(e, logTag)
                    appContext.getString(failureStringRes, e.message ?: e.javaClass.simpleName)
                } finally {
                    _uiState.update { it.copy(isBusy = false) }
                }
            refresh()
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    fun exportSavesFolder(destinationTree: Uri) {
        runSaveOperation("Folder export failed", R.string.saves_export_folder_failed) {
            val count = savesBackupManager.exportSavesToFolder(destinationTree)
            if (count == 0) {
                appContext.getString(R.string.saves_export_folder_none)
            } else {
                appContext.getString(R.string.saves_export_folder_done, count)
            }
        }
    }

    fun exportGameSave(
        game: Game,
        destination: Uri,
    ) {
        runSaveOperation("Failed to export save for ${game.id}", R.string.export_single_save_failed) {
            savesBackupManager.exportGameSave(game, destination)
            appContext.getString(R.string.export_single_save_done, game.displayName())
        }
    }

    fun importGameSave(
        game: Game,
        source: Uri,
    ) {
        if (!HardcorePolicy.canMutateSaveFilesExternally(GameProcessLock.isHeldByAnotherProcess(appContext))) {
            Toast.makeText(appContext, R.string.game_process_another_game_running, Toast.LENGTH_LONG).show()
            return
        }
        runSaveOperation(
            logTag = "Failed to import save for ${game.id}",
            failureStringRes = R.string.import_single_save_failed,
            invalidMessage = { e -> appContext.getString(R.string.import_single_save_invalid, e.message ?: "") },
        ) {
            savesBackupManager.importGameSave(game, source)
            appContext.getString(R.string.import_single_save_done, game.displayName())
        }
    }

    fun backupSaves(destination: Uri) {
        runSaveOperation("Saves backup failed", R.string.backup_saves_failed) {
            appContext.getString(R.string.backup_saves_done, savesBackupManager.backup(destination))
        }
    }

    fun restoreSaves(source: Uri) {
        if (!HardcorePolicy.canMutateSaveFilesExternally(GameProcessLock.isHeldByAnotherProcess(appContext))) {
            Toast.makeText(appContext, R.string.game_process_another_game_running, Toast.LENGTH_LONG).show()
            return
        }
        runSaveOperation(
            logTag = "Saves restore failed",
            failureStringRes = R.string.restore_saves_failed,
            invalidMessage = { e -> appContext.getString(R.string.restore_saves_invalid, e.message ?: "") },
        ) {
            appContext.getString(R.string.restore_saves_done, savesBackupManager.restore(source))
        }
    }

    fun formatStorageSummary(stats: SavesStorageInspector.SavesStorageStats): String = stats.formatSummary(appContext)
}

internal fun SavesStorageInspector.SavesStorageStats.formatSummary(context: Context): String {
    if (savesCount == 0 && statesCount == 0) {
        return context.getString(R.string.settings_saves_summary_empty)
    }
    val savesPart = context.resources.getQuantityString(R.plurals.saves_count_label, savesCount, savesCount)
    val statesPart = context.resources.getQuantityString(R.plurals.states_count_label, statesCount, statesCount)
    return context.getString(
        R.string.settings_saves_summary,
        savesPart,
        Formatter.formatShortFileSize(context, savesBytes),
        statesPart,
        Formatter.formatShortFileSize(context, statesBytes),
    )
}
