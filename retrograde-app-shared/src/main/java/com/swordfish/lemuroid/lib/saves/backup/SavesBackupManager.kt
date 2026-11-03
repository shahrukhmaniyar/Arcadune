package com.swordfish.lemuroid.lib.saves.backup

import android.content.Context
import android.net.Uri
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backs up and restores the user's saves and save states to a zip file chosen through the system
 * file picker, so the file can be kept anywhere (for example Google Drive) or moved to another phone.
 */
class SavesBackupManager(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
) {
    private fun roots() =
        linkedMapOf(
            "saves" to directoriesManager.getSavesDirectory(),
            "states" to directoriesManager.getStatesDirectory(),
            "state-previews" to directoriesManager.getStatesPreviewDirectory(),
        )

    fun suggestedFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "arcadune-backup-$date.zip"
    }

    /** Writes a backup to [destination]. Returns the number of files in it. */
    suspend fun backup(destination: Uri): Int =
        withContext(Dispatchers.IO) {
            val version = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull()
            val stream =
                appContext.contentResolver.openOutputStream(destination, "wt")
                    ?: throw IOException("Cannot write to the chosen location")
            stream.use { SavesArchive.write(it, roots(), System.currentTimeMillis(), version ?: "unknown") }
        }

    /** Restores the backup at [source]. Returns the number of files restored. */
    suspend fun restore(source: Uri): Int =
        withContext(Dispatchers.IO) {
            val stream =
                appContext.contentResolver.openInputStream(source)
                    ?: throw IOException("Cannot read the chosen file")
            stream.use { SavesArchive.read(it, roots()) }
        }
}
