package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.dao.GameDao
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.migrators.getSavesMigrator
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/** A save file exists but could not be read. The game must not start, or its empty save would replace the real one. */
class SaveReadException(file: File, cause: Throwable) : IOException("Cannot read the save ${file.name}", cause)

class SavesManager(
    private val directoriesManager: DirectoriesManager,
    private val gameDao: GameDao? = null,
) {
    /**
     * The game's battery save, or null when it has none yet.
     * Throws [SaveReadException] when a save exists but cannot be read.
     */
    suspend fun getSaveRAM(
        game: Game,
        systemCoreConfig: SystemCoreConfig,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = locate(game).read
            if (file != null) {
                return@withContext runCatchingWithRetry(FILE_ACCESS_RETRIES) { file.readBytes() }
                    .getOrElse { throw SaveReadException(file, it) }
            }
            // Saves of an older emulator for this console, if any (they live in other files).
            runCatching { systemCoreConfig.getSavesMigrator()?.loadPreviousSaveForGame(game, directoriesManager) }
                .onFailure { Timber.w(it, "Could not read an older emulator's save for ${game.fileName}") }
                .getOrNull()
        }

    /** Writes the battery save. Returns false when it could not be written. */
    suspend fun setSaveRAM(
        game: Game,
        data: ByteArray,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (data.isEmpty()) return@withContext true
            val target = locate(game).write
            runCatchingWithRetry(FILE_ACCESS_RETRIES) { writeKeepingPrevious(target, data) }
                .onFailure { Timber.e(it, "Could not write the save for ${game.fileName}") }
                .isSuccess
        }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo =
        withContext(Dispatchers.IO) {
            val file = locate(game).read
            SaveInfo(file != null, file?.lastModified() ?: 0L)
        }

    suspend fun hasSingleFileSave(game: Game): Boolean = saveRAMFileForRead(game) != null

    /** The file holding [game]'s battery save, or null when it has none. */
    suspend fun saveRAMFileForRead(game: Game): File? = withContext(Dispatchers.IO) { locate(game).read }

    /** Like [saveRAMFileForRead] for many games at once, using [games] to find shared names. */
    fun saveRAMFilesForRead(games: List<Game>): Map<Int, File?> {
        val savesDirectory = directoriesManager.getSavesDirectory()
        val byStem = games.groupBy { SaveFileLocator.stemOf(it.fileName).lowercase() }
        return games.associate { game ->
            val group = byStem[SaveFileLocator.stemOf(game.fileName).lowercase()].orEmpty()
            game.id to SaveFileLocator.locate(savesDirectory, game, SaveFileLocator.collidingGames(game, group)).read
        }
    }

    suspend fun writeSaveRAMOrThrow(
        game: Game,
        data: ByteArray,
    ) {
        require(data.isNotEmpty()) { "Save data must not be empty" }
        withContext(Dispatchers.IO) {
            val target = locate(game).write
            runCatchingWithRetry(FILE_ACCESS_RETRIES) { writeKeepingPrevious(target, data) }.getOrThrow()
        }
    }

    private suspend fun locate(game: Game): SaveFileLocator.Location {
        val savesDirectory = directoriesManager.getSavesDirectory()
        val candidates =
            gameDao?.let { dao ->
                runCatching {
                    dao.selectByFileNamePrefix(SaveFileLocator.likePrefix(SaveFileLocator.stemOf(game.fileName)))
                }.onFailure { Timber.w(it, "Could not look for games sharing a save name") }
                    .getOrNull()
            }.orEmpty()
        return SaveFileLocator.locate(savesDirectory, game, SaveFileLocator.collidingGames(game, candidates))
    }

    /**
     * Keeps the save being replaced in `saves-previous/` (same relative path), so one bad write can
     * always be undone by hand. The folder is outside `saves/`, so exports and RetroArch never see it.
     */
    private fun writeKeepingPrevious(
        target: File,
        data: ByteArray,
    ) {
        target.parentFile?.mkdirs()
        if (target.isFile && target.length() > 0) {
            runCatching {
                val savesDirectory = directoriesManager.getSavesDirectory()
                val relative = target.relativeTo(savesDirectory).path
                val previous = File(directoriesManager.getPreviousSavesDirectory(), relative)
                previous.parentFile?.mkdirs()
                target.copyTo(previous, overwrite = true)
            }.onFailure { Timber.w(it, "Could not keep the previous save of ${target.name}") }
        }
        target.writeBytesAtomic(data)
    }

    companion object {
        private const val FILE_ACCESS_RETRIES = 3

        /** The usual (RetroArch-compatible) save file name, used to suggest a name when exporting. */
        fun saveFileNameFor(game: Game) = "${SaveFileLocator.stemOf(game.fileName)}.srm"
    }
}
