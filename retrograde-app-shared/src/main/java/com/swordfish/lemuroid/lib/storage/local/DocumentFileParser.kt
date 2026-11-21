package com.swordfish.lemuroid.lib.storage.local

import android.content.Context
import com.swordfish.lemuroid.common.files.FileChdAccess
import com.swordfish.lemuroid.common.files.ParcelFdChdAccess
import com.swordfish.lemuroid.common.kotlin.calculateCrc32
import com.swordfish.lemuroid.common.kotlin.toStringCRC32
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.lib.storage.scanner.SerialScanner
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DocumentFileParser {
    private const val MAX_CHECKED_ENTRIES = 12
    private const val MAX_SIZE_CRC32 = 1_000_000_000

    private val ARCADE_BIOS_ARCHIVES =
        setOf(
            "neogeo.zip",
            "pgm.zip",
            "skns.zip",
            "isgsm.zip",
            "ym2608.zip",
            "cpzn1.zip",
            "cpzn2.zip",
            "decocass.zip",
            "konamigx.zip",
            "nss.zip",
            "playch10.zip",
            "stvbios.zip",
        )

    private val NON_EXTRACTABLE_FOLDER_TOKENS =
        setOf(
            "arcade",
            "fbneo",
            "fba",
            "mame",
            "mame2003",
            "mame2003plus",
            "mame2003-plus",
            "neogeo",
            "dos",
            "pc",
        )

    private val IGNORED_SIDE_EXTENSIONS =
        setOf(
            "txt",
            "nfo",
            "diz",
            "htm",
            "html",
            "url",
            "jpg",
            "jpeg",
            "png",
            "gif",
            "bmp",
            "pdf",
            "xml",
            "sfv",
        )

    private val AMBIGUOUS_SINGLE_FILE_ROM_EXTENSIONS =
        setOf(
            "bin",
            "rom",
            "img",
            "dsk",
        )

    private val EXTRACTABLE_CONSOLE_ROM_EXTENSIONS =
        setOf(
            "nes",
            "fds",
            "unf",
            "unif",
            "sfc",
            "smc",
            "swc",
            "fig",
            "bs",
            "gba",
            "gb",
            "gbc",
            "cgb",
            "dmg",
            "md",
            "gen",
            "smd",
            "mdx",
            "68k",
            "sms",
            "gg",
            "sg",
            "32x",
            "pce",
            "sgx",
            "ngp",
            "ngc",
            "ngpc",
            "npc",
            "ws",
            "wsc",
            "pc2",
            "lnx",
            "lyx",
            "a26",
            "a52",
            "a78",
            "col",
            "cv",
            "nds",
            "dsi",
            "ids",
            "n64",
            "z64",
            "v64",
            "3ds",
            "3dsx",
            "cci",
            "cxi",
            "iso",
            "cso",
            "pbp",
            "cdi",
            "chd",
            "d64",
            "d71",
            "d80",
            "d81",
            "d82",
            "t64",
            "prg",
            "crt",
            "tap",
            "g64",
            "p00",
            "vsf",
            "mx1",
            "mx2",
            "fdi",
            "atr",
            "atx",
            "dcm",
            "car",
            "xex",
            "xfd",
            "do",
            "po",
            "nib",
            "woz",
            "2mg",
            "2img",
            "hdv",
        )

    fun parseDocumentFile(
        context: Context,
        baseStorageFile: BaseStorageFile,
    ): StorageFile {
        return if (baseStorageFile.extension.equals("zip", ignoreCase = true)) {
            Timber.d("Detected zip file. ${baseStorageFile.name}")
            parseZipFile(context, baseStorageFile)
        } else {
            Timber.d("Detected standard file. ${baseStorageFile.name}")
            parseStandardFile(context, baseStorageFile)
        }
    }

    private fun parseZipFile(
        context: Context,
        baseStorageFile: BaseStorageFile,
    ): StorageFile {
        if (baseStorageFile.name.lowercase(Locale.US) in ARCADE_BIOS_ARCHIVES ||
            isLikelyMultiRomOrArcadeFolder(baseStorageFile)
        ) {
            return parseStandardFile(context, baseStorageFile)
        }
        val inputStream =
            context.contentResolver.openInputStream(baseStorageFile.uri)
                ?: return parseStandardFile(context, baseStorageFile)
        return ZipInputStream(inputStream).use { zipStream ->
            val gameEntry = findGameEntry(zipStream, baseStorageFile.size)
            if (gameEntry != null) {
                parseCompressedGame(baseStorageFile, gameEntry, zipStream)
                    ?.also { Timber.d("Handling zip file as compressed game: ${baseStorageFile.name}") }
                    ?: parseStandardFile(context, baseStorageFile)
            } else {
                Timber.d("Handling zip file as standard: ${baseStorageFile.name}")
                parseStandardFile(context, baseStorageFile)
            }
        }
    }

    private fun isLikelyMultiRomOrArcadeFolder(baseStorageFile: BaseStorageFile): Boolean {
        val rawPath = (baseStorageFile.path ?: baseStorageFile.uri.path ?: "").lowercase(Locale.US)
        val segments = rawPath.split('/', '\\', ':').map { it.trim() }.filter { it.isNotEmpty() }
        return segments.dropLast(1).any { it in NON_EXTRACTABLE_FOLDER_TOKENS }
    }

    private fun parseCompressedGame(
        baseStorageFile: BaseStorageFile,
        entry: ZipEntry,
        zipInputStream: ZipInputStream,
    ): StorageFile? {
        Timber.d("Processing zipped entry: ${entry.name}")

        val entryExt = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
        val diskInfo =
            SerialScanner.extractInfo(
                entry.name,
                zipInputStream,
                fileSize = entry.size,
                closeStream = false,
            )
        runCatching { zipInputStream.closeEntry() }

        if (entryExt in AMBIGUOUS_SINGLE_FILE_ROM_EXTENSIONS && hasAnotherNonIgnoredEntry(zipInputStream)) {
            return null
        }

        val crcHex = if (entry.crc >= 0L) entry.crc.toStringCRC32() else null

        return StorageFile(
            entry.name,
            entry.size,
            crcHex,
            diskInfo.serial,
            baseStorageFile.uri,
            baseStorageFile.uri.path,
            diskInfo.systemID,
        )
    }

    private fun parseStandardFile(
        context: Context,
        baseStorageFile: BaseStorageFile,
    ): StorageFile {
        val isChd = baseStorageFile.extension.equals("chd", ignoreCase = true)
        val diskInfo =
            if (isChd) {
                extractChdDiskInfo(context, baseStorageFile)
            } else {
                context.contentResolver.openInputStream(baseStorageFile.uri)
                    ?.let { inputStream ->
                        SerialScanner.extractInfo(baseStorageFile.name, inputStream, baseStorageFile.size)
                    }
            }

        // CHD containers are never indexed by file CRC32 in LibretroDB or BiosManager, and large
        // optical disc images that already matched a known system header don't benefit from a
        // multi-hundred-MB CRC32 pass over SAF.
        val skipCrc =
            isChd ||
                (diskInfo?.systemID != null && baseStorageFile.size > 64_000_000L)

        val crc32 =
            if (!skipCrc && baseStorageFile.size < MAX_SIZE_CRC32 && diskInfo?.serial == null) {
                context.contentResolver.openInputStream(baseStorageFile.uri)?.calculateCrc32()
            } else {
                null
            }

        Timber.d("Parsed standard file: $baseStorageFile")

        return StorageFile(
            baseStorageFile.name,
            baseStorageFile.size,
            crc32,
            diskInfo?.serial,
            baseStorageFile.uri,
            baseStorageFile.uri.path,
            diskInfo?.systemID,
        )
    }

    private fun extractChdDiskInfo(
        context: Context,
        baseStorageFile: BaseStorageFile,
    ): SerialScanner.DiskInfo? {
        runCatching {
            context.contentResolver.openFileDescriptor(baseStorageFile.uri, "r")?.use { pfd ->
                val info = SerialScanner.extractInfoForChd(ParcelFdChdAccess(pfd))
                if (info.systemID != null || info.serial != null) {
                    return info
                }
            }
        }
        val localFile =
            baseStorageFile.uri.path
                ?.takeIf { baseStorageFile.uri.scheme == null || baseStorageFile.uri.scheme == "file" }
                ?.let { File(it) }
                ?.takeIf { it.isFile }
        if (localFile != null) {
            return runCatching {
                FileChdAccess(localFile).use { access ->
                    SerialScanner.extractInfoForChd(access)
                }
            }.getOrNull()
        }
        return null
    }

    /* Finds a zip entry which we assume is a single console game, even when Deflate Data Descriptor
       (bit 3) leaves entry.compressedSize == -1 in the Local File Header.Note:ZipInputStream must
       stay positioned at the returned entry so SerialScanner can read from it immediately! */
    fun findGameEntry(
        openedInputStream: ZipInputStream,
        fileSize: Long = -1,
    ): ZipEntry? {
        for (i in 0 until MAX_CHECKED_ENTRIES) {
            val entry = openedInputStream.nextEntry ?: break
            if (entry.isDirectory) continue
            val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext in IGNORED_SIDE_EXTENSIONS) continue
            return if (isGameEntry(ext)) entry else null
        }
        return null
    }

    private fun hasAnotherNonIgnoredEntry(openedInputStream: ZipInputStream): Boolean {
        for (i in 0 until MAX_CHECKED_ENTRIES) {
            val entry = openedInputStream.nextEntry ?: return false
            if (entry.isDirectory) continue
            val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext in IGNORED_SIDE_EXTENSIONS) continue
            return true
        }
        return false
    }

    private fun isGameEntry(ext: String): Boolean {
        return ext in EXTRACTABLE_CONSOLE_ROM_EXTENSIONS || ext in AMBIGUOUS_SINGLE_FILE_ROM_EXTENSIONS
    }
}
