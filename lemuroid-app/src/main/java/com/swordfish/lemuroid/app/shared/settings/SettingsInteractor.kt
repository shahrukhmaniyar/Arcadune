package com.swordfish.lemuroid.app.shared.settings

import android.content.Context
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.storage.cache.CacheCleanerWork
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager

class SettingsInteractor(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    fun changeLocalStorageFolder() {
        StorageFrameworkPickerLauncher.pickFolder(context)
    }

    fun resetAllSettings() {
        // The ROMs folder is kept: clearing it made the next scan see no files at all, and the game
        // crash screen sends players here, so a reset must never empty their library.
        val legacy = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val folderKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
        val folder = legacy.getString(folderKey, null)
        val editor = legacy.edit().clear()
        if (folder != null) editor.putString(folderKey, folder)
        editor.apply()
        SharedPreferencesHelper.getSharedPreferences(context).edit().clear().apply()
        LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
        CacheCleanerWork.enqueueCleanCacheAll(context.applicationContext)
        if (!GameProcessLock.isHeldByAnotherProcess(context.applicationContext)) {
            deleteDownloadedCores()
        }
    }

    private fun deleteDownloadedCores() {
        directoriesManager.getCoresDirectory()
            .listFiles()
            ?.forEach { entry ->
                runCatching {
                    entry.walkBottomUp().forEach { child ->
                        runCatching { child.setWritable(true, true) }
                    }
                    entry.deleteRecursively()
                }
            }
    }
}
