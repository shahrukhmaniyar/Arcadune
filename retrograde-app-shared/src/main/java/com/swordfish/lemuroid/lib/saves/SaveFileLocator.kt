package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.io.File

/**
 * Decides which file holds a game's battery save (.srm).
 *
 * Saves normally sit in one folder, named after the ROM without its extension, like RetroArch
 * does ("Sonic.md" -> "saves/Sonic.srm"). Two games with the same name on different consoles
 * ("Sonic.md" and "Sonic.gg") would then share one file and overwrite each other. When the
 * library has such a pair, each game gets its own file in `saves/by-console/<console>/`.
 *
 * The old shared file is still read by the game that wrote it last: the colliding game played
 * most recently that has no file of its own yet. Play time is only recorded when a session ends,
 * so that is exactly the game that last wrote the shared file.
 */
object SaveFileLocator {
    const val BY_CONSOLE_FOLDER = "by-console"

    data class Location(
        /** The file to read the save from, or null when the game has no save yet. */
        val read: File?,
        /** The file the next save is written to. */
        val write: File,
    )

    fun stemOf(fileName: String): String = fileName.substringBeforeLast(".")

    /** The other games of [candidates] whose save would have the same name as [game]'s. */
    fun collidingGames(
        game: Game,
        candidates: List<Game>,
    ): List<Game> {
        val stem = stemOf(game.fileName)
        return candidates.filter { it.id != game.id && stemOf(it.fileName).equals(stem, ignoreCase = true) }
    }

    fun locate(
        savesDirectory: File,
        game: Game,
        collidingGames: List<Game>,
    ): Location {
        val stem = stemOf(game.fileName)
        val legacy = File(savesDirectory, "$stem.srm")
        val group = collidingGames + game

        fun consoleFile(owner: Game): File {
            val sameConsolePeer = group.any { it.id != owner.id && it.systemId == owner.systemId }
            val name = if (sameConsolePeer) "$stem [${owner.id}].srm" else "$stem.srm"
            return File(File(File(savesDirectory, BY_CONSOLE_FOLDER), owner.systemId), name)
        }

        val own = consoleFile(game)
        if (own.hasData()) return Location(read = own, write = own)
        if (collidingGames.isEmpty()) return Location(read = legacy.takeIf { it.hasData() }, write = legacy)

        val ownerOfShared =
            group
                .filter { !consoleFile(it).hasData() }
                .maxWithOrNull(compareBy<Game>({ it.lastPlayedAt ?: Long.MIN_VALUE }, { -it.id }))
        val read = if (ownerOfShared?.id == game.id && legacy.hasData()) legacy else null
        return Location(read = read, write = own)
    }

    /** Escapes [text] for a SQL LIKE pattern that uses a backslash as escape character. */
    fun likePrefix(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    private fun File.hasData() = isFile && length() > 0
}
