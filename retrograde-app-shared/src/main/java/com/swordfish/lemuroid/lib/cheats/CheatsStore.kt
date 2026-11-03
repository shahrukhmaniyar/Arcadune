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

    /** Switches off all cheats of the game whose apply was interrupted. Returns that game's key, or null. */
    fun switchOffAfterCrash(): String? {
        val marker = markerFile()
        if (!marker.exists()) return null

        val key = runCatching { marker.readText() }.getOrNull()?.takeIf { it.isNotEmpty() }
        if (key != null) save(key, load(key).map { it.copy(enabled = false) })
        marker.delete()
        return key
    }

    private fun markerFile() = File(directory, APPLYING_MARKER)

    private fun fileFor(key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(directory, "$safe-${Integer.toHexString(key.hashCode())}.json")
    }

    companion object {
        private const val APPLYING_MARKER = "applying.marker"

        fun keyFor(
            systemId: String,
            fileName: String,
        ): String = "${systemId}__$fileName"
    }
}
