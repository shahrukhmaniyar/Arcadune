package com.swordfish.lemuroid.lib.cheats

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Keeps each game's cheats in a small JSON file inside [directory].
 *
 * The in-game menu and the running game live in the same app process group and both read these
 * files, so they always agree on which cheats exist and which are switched on.
 */
class CheatsStore(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(key: String): List<Cheat> {
        val file = fileFor(key)
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<Cheat>>(file.readText()) }.getOrDefault(emptyList())
    }

    fun save(
        key: String,
        cheats: List<Cheat>,
    ) {
        val file = fileFor(key)
        if (cheats.isEmpty()) {
            file.delete()
            return
        }

        directory.mkdirs()
        // Write to a temporary file first, so a crash never leaves a half-written cheat list.
        val temporary = File(directory, file.name + ".tmp")
        try {
            temporary.writeText(json.encodeToString(cheats))
            temporary.copyTo(file, overwrite = true)
        } finally {
            temporary.delete()
        }
    }

    /** The cheats that were switched on in the emulator at the last apply (needed to undo them later). */
    fun loadApplied(key: String): List<Cheat> {
        val file = appliedFileFor(key)
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<Cheat>>(file.readText()) }.getOrDefault(emptyList())
    }

    fun saveApplied(
        key: String,
        applied: List<Cheat>,
    ) {
        val file = appliedFileFor(key)
        if (applied.isEmpty()) {
            file.delete()
            return
        }
        directory.mkdirs()
        file.writeText(json.encodeToString(applied))
    }

    /**
     * Call before sending cheats to the emulator and [finishApplying] afterwards. If the game dies in
     * between (an emulator core can abort on a code it dislikes), the marker is still there at the
     * next start and [switchOffAfterCrash] turns the cheats of that game off.
     */
    fun startApplying(key: String) {
        directory.mkdirs()
        markerFile().writeText(key)
    }

    fun finishApplying() {
        markerFile().delete()
    }

    /**
     * Marks that the game is running with cheats switched on. A cheat can also bring the game down
     * some time after it was applied (a code written for another version of the game, for
     * example), so the marker stays until the game is closed normally with [endSession].
     */
    fun startSession(key: String) {
        directory.mkdirs()
        sessionFile().writeText(key)
    }

    fun endSession() {
        sessionFile().delete()
    }

    /**
     * Switches off all cheats of the game that was stopped while its cheats were being applied, or
     * that ended without being closed normally while cheats were on. Returns that game's key, or null.
     */
    fun switchOffAfterCrash(): String? {
        var switchedOff: String? = null
        for (marker in listOf(markerFile(), sessionFile())) {
            if (!marker.exists()) continue

            val key = runCatching { marker.readText() }.getOrNull()?.takeIf { it.isNotEmpty() }
            if (key != null) {
                save(key, load(key).map { it.copy(enabled = false) })
                switchedOff = key
            }
            marker.delete()
        }
        return switchedOff
    }

    private fun markerFile() = File(directory, APPLYING_MARKER)

    private fun sessionFile() = File(directory, SESSION_MARKER)

    private fun fileFor(key: String): File = File(directory, "${safeName(key)}.json")

    private fun appliedFileFor(key: String): File = File(directory, "${safeName(key)}.applied")

    private fun safeName(key: String): String {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "$safe-${Integer.toHexString(key.hashCode())}"
    }

    companion object {
        private const val APPLYING_MARKER = "applying.marker"
        private const val SESSION_MARKER = "session.marker"

        fun keyFor(
            systemId: String,
            fileName: String,
        ): String = "${systemId}__$fileName"
    }
}
