package com.swordfish.lemuroid.lib.saves.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes backups of saves and save states as a single zip file.
 *
 * A backup holds one top-level folder per root (for example "saves", "states"), plus a small
 * [INFO_ENTRY] file that marks the zip as an Arcadune backup. Restoring only writes inside the
 * given root directories and rejects entries that try to escape them.
 *
 * This class only uses plain Java I/O so it can be unit tested without Android.
 */
object SavesArchive {
    const val INFO_ENTRY = "backup-info.json"
    private const val FORMAT = "arcadune-saves-backup"
    private const val MAX_ENTRIES = 200_000
    private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

    class InvalidBackupException(message: String) : IOException(message)

    /** Writes every file below [roots] (folder name to directory) into [output]. Returns the file count. */
    fun write(
        output: OutputStream,
        roots: Map<String, File>,
        createdAtMillis: Long,
        appVersion: String,
    ): Int {
        var count = 0
        ZipOutputStream(output.buffered()).use { zip ->
            val info = """{"format":"$FORMAT","version":1,"createdAt":$createdAtMillis,"appVersion":"$appVersion"}"""
            zip.putNextEntry(ZipEntry(INFO_ENTRY))
            zip.write(info.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for ((folder, directory) in roots) {
                if (!directory.isDirectory) continue
                directory.walkTopDown()
                    .filter { it.isFile }
                    .sortedBy { it.path }
                    .forEach { file ->
                        val relative = file.relativeTo(directory).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry("$folder/$relative"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
            }
        }
        return count
    }

    /**
     * Restores the files of a backup into [roots]. Files with the same name are replaced, other
     * files are kept. Returns the number of files restored.
     */
    fun read(
        input: InputStream,
        roots: Map<String, File>,
    ): Int {
        val zip = ZipInputStream(input.buffered())
        val first = zip.nextEntry ?: throw InvalidBackupException("The file is empty")
        if (first.name != INFO_ENTRY || !String(zip.readBytes(), Charsets.UTF_8).contains(FORMAT)) {
            throw InvalidBackupException("This is not an Arcadune backup")
        }

        val canonicalRoots = roots.mapValues { it.value.canonicalFile }
        var entries = 0
        var restored = 0
        var totalBytes = 0L

        while (true) {
            val entry = zip.nextEntry ?: break
            if (++entries > MAX_ENTRIES) throw InvalidBackupException("The backup has too many files")
            if (entry.isDirectory) continue

            val target = resolveTarget(entry.name, canonicalRoots) ?: continue
            target.parentFile?.mkdirs()

            // Write to a temporary file first, so a broken backup never leaves a half-written save.
            val temporary = File(target.parentFile, target.name + ".restoring")
            try {
                temporary.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_TOTAL_BYTES) throw InvalidBackupException("The backup is too large")
                        out.write(buffer, 0, read)
                    }
                }
                temporary.copyTo(target, overwrite = true)
                restored++
            } finally {
                temporary.delete()
            }
        }

        return restored
    }

    /** Maps a zip entry name to a file inside one of the roots, or null if the entry should be ignored. */
    private fun resolveTarget(
        entryName: String,
        canonicalRoots: Map<String, File>,
    ): File? {
        val parts = entryName.split('/')
        // A colon is legal in Android file names (game names often have one), so it is allowed; the
        // canonical path check below catches any trick that would leave the root folder.
        if (parts.any { it == ".." || it == "." } || entryName.startsWith("/") || entryName.contains('\\')) {
            throw InvalidBackupException("The backup contains an unsafe file name")
        }
        if (parts.size < 2 || parts.last().isEmpty()) return null

        val root = canonicalRoots[parts.first()] ?: return null
        val target = File(root, parts.drop(1).joinToString("/")).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) {
            throw InvalidBackupException("The backup contains an unsafe file name")
        }
        return target
    }
}
