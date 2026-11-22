package com.swordfish.lemuroid.lib.saves.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backs up, restores, inspects, and exports the user's saves and save states via the system
 * file picker, so saves stay portable and easy to reach even when Android hides Android/data/.
 */
class SavesBackupManager(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
    private val savesManager: SavesManager,
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

    fun initialOrCachedStats(): SavesStorageInspector.SavesStorageStats {
        cachedStats?.let { return it }
        val absolutePath = directoriesManager.getSavesDirectory().absolutePath
        return SavesStorageInspector.SavesStorageStats(
            savesCount = 0,
            savesBytes = 0L,
            statesCount = 0,
            statesBytes = 0L,
            absolutePath = absolutePath,
            displayPath = SavesStorageInspector.formatDisplayPath(absolutePath),
        )
    }

    suspend fun inspectStorage(): SavesStorageInspector.SavesStorageStats =
        withContext(Dispatchers.IO) {
            SavesStorageInspector.inspect(
                savesDirectory = directoriesManager.getSavesDirectory(),
                statesDirectory = directoriesManager.getStatesDirectory(),
            ).also { cachedStats = it }
        }

    /** Writes a backup to [destination]. Returns the number of files in it. */
    suspend fun backup(destination: Uri): Int =
        withContext(Dispatchers.IO) {
            val version =
                runCatching {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                }.getOrNull()
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

    /** Copies every raw in-game save file into [destinationTree], preserving subfolders. */
    suspend fun exportSavesToFolder(destinationTree: Uri): Int =
        withContext(Dispatchers.IO) {
            val rootDoc =
                DocumentFile.fromTreeUri(appContext, destinationTree)
                    ?: throw IOException("Cannot open the chosen folder")
            if (!rootDoc.isDirectory || !rootDoc.canWrite()) {
                throw IOException("Cannot write to the chosen folder")
            }

            val files = SavesStorageInspector.listExportableSaveFiles(directoriesManager.getSavesDirectory())
            if (files.isEmpty()) return@withContext 0

            val folderCache = HashMap<String, CachedFolder>()
            folderCache[""] = CachedFolder(rootDoc)

            var exported = 0
            for ((relativePath, sourceFile) in files) {
                val segments = relativePath.split('/')
                val fileName = segments.last()
                val targetFolder = resolveTargetFolder(segments.dropLast(1), folderCache)
                openDocumentOutputStream(targetFolder, fileName).use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                exported++
            }
            exported
        }

    /** Copies a single game's `.srm` save file to [destination]. */
    suspend fun exportGameSave(
        game: Game,
        destination: Uri,
    ) = withContext(Dispatchers.IO) {
        val saveFile =
            savesManager.saveRAMFileForRead(game)
                ?: throw IOException("No in-game save found")
        val out =
            openOutputStream(destination)
                ?: throw IOException("Cannot write to the chosen file")
        out.use { output ->
            saveFile.inputStream().use { input -> input.copyTo(output) }
        }
    }

    /** Imports a single `.srm` save file from [source] into [game]'s save slot. Returns byte count. */
    suspend fun importGameSave(
        game: Game,
        source: Uri,
    ): Int {
        val bytes =
            withContext(Dispatchers.IO) {
                val input =
                    appContext.contentResolver.openInputStream(source)
                        ?: throw IOException("Cannot read the chosen file")
                input.use { SavesStorageInspector.readBoundedSaveBytes(it) }
            }
        savesManager.writeSaveRAMOrThrow(game, bytes)
        return bytes.size
    }

    private fun resolveTargetFolder(
        dirSegments: List<String>,
        cache: MutableMap<String, CachedFolder>,
    ): CachedFolder {
        var currentKey = ""
        var current = cache.getValue("")
        for (segment in dirSegments) {
            currentKey = if (currentKey.isEmpty()) segment else "$currentKey/$segment"
            current =
                cache.getOrPut(currentKey) {
                    val existingDir = current.findChild(segment) { it.isDirectory }
                    val dir =
                        existingDir
                            ?: current.doc.createDirectory(segment)
                            ?: throw IOException("Cannot create folder $segment")
                    if (existingDir == null) current.putChild(dir)
                    CachedFolder(dir)
                }
        }
        return current
    }

    private fun openDocumentOutputStream(
        folder: CachedFolder,
        fileName: String,
    ): OutputStream {
        val existing = folder.findChild(fileName) { it.isFile }
        if (existing != null) {
            openOutputStream(existing.uri)?.let { return it }
            existing.delete()
            folder.removeChild(fileName)
        }
        val created =
            folder.doc.createFile(RAW_SAVE_MIME_TYPE, fileName)
                ?: throw IOException("Cannot create $fileName")
        folder.putChild(created)
        return openOutputStream(created.uri)
            ?: throw IOException("Cannot write to $fileName")
    }

    private fun openOutputStream(uri: Uri): OutputStream? =
        runCatching { appContext.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: runCatching { appContext.contentResolver.openOutputStream(uri) }.getOrNull()

    private class CachedFolder(val doc: DocumentFile) {
        private var indexed: MutableMap<String, DocumentFile>? = null

        private fun ensureIndexed(): MutableMap<String, DocumentFile> =
            indexed ?: doc.listFiles()
                .mapNotNull { child -> child.name?.let { it to child } }
                .toMap(LinkedHashMap())
                .also { indexed = it }

        fun findChild(
            name: String,
            predicate: (DocumentFile) -> Boolean,
        ): DocumentFile? = ensureIndexed()[name]?.takeIf(predicate)

        fun putChild(child: DocumentFile) {
            val name = child.name ?: return
            ensureIndexed()[name] = child
        }

        fun removeChild(name: String) {
            indexed?.remove(name)
        }
    }

    companion object {
        // Custom subtype avoids ExternalStorageProvider appending ".bin" when creating ".srm" files.
        const val RAW_SAVE_MIME_TYPE = "application/x-arcadune-save"

        @Volatile
        var cachedStats: SavesStorageInspector.SavesStorageStats? = null
            private set
    }
}
