package com.swordfish.lemuroid.lib.saves.backup

import com.swordfish.lemuroid.lib.library.SystemID
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Inspects on-device save/state folders and formats user-visible storage paths and export lists.
 * Uses plain Java I/O so every helper is unit tested on the JVM.
 */
object SavesStorageInspector {
    const val MAX_SINGLE_SAVE_BYTES = 64L * 1024 * 1024
    private const val TEMP_SUFFIX = ".restoring"
    private const val METADATA_SUFFIX = ".metadata"
    private val STORAGE_UUID_REGEX = Regex("^/storage/([^/]+)/(.+)$")
    private val FOLDER_SAVE_SYSTEMS =
        setOf(
            SystemID.PSP.dbname,
            SystemID.NINTENDO_3DS.dbname,
            SystemID.DOS.dbname,
        )

    data class SavesStorageStats(
        val savesCount: Int,
        val savesBytes: Long,
        val statesCount: Int,
        val statesBytes: Long,
        val absolutePath: String,
        val displayPath: String,
    )

    fun inspect(
        savesDirectory: File,
        statesDirectory: File,
    ): SavesStorageStats {
        val saveFiles = listExportableSaveFiles(savesDirectory)
        val savesCount = saveFiles.size
        val savesBytes = saveFiles.sumOf { it.second.length() }

        var statesCount = 0
        var statesBytes = 0L
        if (statesDirectory.isDirectory) {
            statesDirectory.walkTopDown()
                .filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }
                .forEach { file ->
                    statesBytes += file.length()
                    if (!file.name.endsWith(METADATA_SUFFIX)) {
                        statesCount++
                    }
                }
        }

        val absolutePath = savesDirectory.absolutePath
        return SavesStorageStats(
            savesCount = savesCount,
            savesBytes = savesBytes,
            statesCount = statesCount,
            statesBytes = statesBytes,
            absolutePath = absolutePath,
            displayPath = formatDisplayPath(absolutePath),
        )
    }

    fun listExportableSaveFiles(savesDirectory: File): List<Pair<String, File>> {
        if (!savesDirectory.isDirectory) return emptyList()
        return savesDirectory.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) && it.length() > 0L }
            .map { file -> file.relativeTo(savesDirectory).invariantSeparatorsPath to file }
            .sortedBy { it.first }
            .toList()
    }

    fun formatDisplayPath(absolutePath: String): String {
        val normalized = absolutePath.replace('\\', '/')
        return when {
            normalized.startsWith("/storage/emulated/0/") ->
                "Internal storage/${normalized.removePrefix("/storage/emulated/0/")}"
            normalized == "/storage/emulated/0" -> "Internal storage"
            normalized.startsWith("/sdcard/") ->
                "Internal storage/${normalized.removePrefix("/sdcard/")}"
            else -> {
                val match = STORAGE_UUID_REGEX.matchEntire(normalized)
                if (match != null && match.groupValues[1] != "self") {
                    "SD card (${match.groupValues[1]})/${match.groupValues[2]}"
                } else {
                    absolutePath
                }
            }
        }
    }

    fun supportsSingleFileSave(systemId: String): Boolean = systemId !in FOLDER_SAVE_SYSTEMS

    fun readBoundedSaveBytes(
        input: InputStream,
        maxBytes: Long = MAX_SINGLE_SAVE_BYTES,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw SavesArchive.InvalidBackupException("The selected save file is too large")
            }
            out.write(buffer, 0, read)
        }
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) {
            throw SavesArchive.InvalidBackupException("The selected save file is empty")
        }
        return bytes
    }
}
