/*
 * FileKt.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.common.kotlin

import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

private const val CRC32_BYTE_ARRAY_SIZE = 16 * 1024
private const val GZIP_INPUT_STREAM_BUFFER_SIZE = 64 * 1024
private const val ATOMIC_WRITE_BUFFER_SIZE = 64 * 1024

fun InputStream.calculateCrc32(): String =
    this.use { fileStream ->
        val buffer = ByteArray(CRC32_BYTE_ARRAY_SIZE)
        return CheckedInputStream(fileStream, CRC32()).use { crcStream ->
            while (crcStream.read(buffer) != -1) {
                // Read file in completely
            }
            crcStream.checksum.value.toStringCRC32()
        }
    }

fun File.calculateMd5(): String {
    val bytes =
        MessageDigest
            .getInstance("MD5")
            .digest(this.readBytes())
    return bytes.toHexString()
}

fun InputStream.writeToFile(file: File) {
    file.parentFile?.mkdirs()
    val tempFile = File(file.parentFile, "${file.name}.tmp")
    try {
        this.use { inputStream ->
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.fd.sync()
            }
        }
        if (file.exists() && !file.delete()) {
            throw IOException("Failed to replace existing file: ${file.absolutePath}")
        }
        if (!tempFile.renameTo(file)) {
            throw IOException("Failed to move temporary file to: ${file.absolutePath}")
        }
    } catch (e: Throwable) {
        tempFile.delete()
        throw e
    }
}

fun ZipInputStream.extractEntryToFile(
    entryName: String,
    gameFile: File,
) {
    this.use { inputStream ->
        var matchedEntrySize = -1L
        while (true) {
            val entry =
                inputStream.nextEntry
                    ?: throw IOException("Zip entry '$entryName' not found for ${gameFile.name}")
            if (entry.name == entryName) {
                matchedEntrySize = entry.size
                break
            }
        }
        inputStream.writeToFile(gameFile)
        if (matchedEntrySize > 0L && gameFile.length() != matchedEntrySize) {
            val actualSize = gameFile.length()
            gameFile.delete()
            throw IOException(
                "Zip entry size mismatch for '$entryName': expected $matchedEntrySize, got $actualSize",
            )
        }
    }
}

fun File.isZipped() = extension.equals("zip", ignoreCase = true)

fun DocumentFile.isZipped(): Boolean {
    val mime = type?.lowercase()
    if (mime == "application/zip" || mime == "application/x-zip-compressed") {
        return true
    }
    return name?.substringAfterLast('.', "")?.equals("zip", ignoreCase = true) == true
}

/** Returns the uncompressed input stream if gzip compressed. */
internal fun InputStream.uncompressedInputStream(): InputStream {
    val pb = PushbackInputStream(this, 2)
    val signature = ByteArray(2)
    val len = pb.read(signature)
    if (len > 0) {
        pb.unread(signature, 0, len)
    }
    return if (len == 2 && signature[0] == 0x1f.toByte() && signature[1] == 0x8b.toByte()) {
        GZIPInputStream(pb, GZIP_INPUT_STREAM_BUFFER_SIZE)
    } else {
        pb
    }
}

/** Read bytes from input stream. If the stream is compressed with GZIP the uncompressed data is returned.*/
internal fun readBytesUncompressedStream(inputStream: InputStream): ByteArray =
    inputStream.use { input ->
        val b = ByteArray(GZIP_INPUT_STREAM_BUFFER_SIZE)
        val os = ByteArrayOutputStream()
        os.use { usedOutputStream ->
            var c: Int
            while (input.read(b).also { c = it } != -1) {
                usedOutputStream.write(b, 0, c)
            }
        }
        return os.toByteArray()
    }

private fun File.writeStreamAtomic(writer: (OutputStream) -> Unit) {
    parentFile?.mkdirs()
    val newFile = File(parentFile, "$name.new")
    val legacyBackupFile = File(parentFile, "$name.bak")
    if (legacyBackupFile.exists()) {
        if (!exists()) {
            legacyBackupFile.renameTo(this)
        } else {
            legacyBackupFile.delete()
        }
    }
    try {
        FileOutputStream(newFile).use { fos ->
            // Buffered, and passing whole arrays through: a plain FilterOutputStream writes an
            // array one byte at a time (one system call per byte), which made saving a game state
            // take 20+ seconds when leaving a game.
            val buffered = BufferedOutputStream(fos, ATOMIC_WRITE_BUFFER_SIZE)
            val nonClosingOut =
                object : FilterOutputStream(buffered) {
                    override fun write(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ) {
                        out.write(b, off, len)
                    }

                    override fun close() {
                        flush()
                    }
                }
            writer(nonClosingOut)
            buffered.flush()
            fos.flush()
            runCatching { fos.fd.sync() }
        }
        if (!newFile.renameTo(this)) {
            if (exists() && !delete()) {
                throw IOException("Failed to replace existing file: $absolutePath")
            }
            if (!newFile.renameTo(this)) {
                throw IOException("Failed to atomically commit $newFile to $absolutePath")
            }
        }
    } catch (e: Throwable) {
        newFile.delete()
        throw e
    }
}

private fun File.openAtomicInputStream(): InputStream {
    val legacyBackupFile = File(parentFile, "$name.bak")
    if (legacyBackupFile.exists()) {
        delete()
        legacyBackupFile.renameTo(this)
    }
    val incompleteNewFile = File(parentFile, "$name.new")
    if (incompleteNewFile.exists()) {
        incompleteNewFile.delete()
    }
    return FileInputStream(this)
}

/** Write bytes to file atomically (via `.new` + `fd.sync()` + rename) to prevent corruption. */
fun File.writeBytesAtomic(data: ByteArray) {
    writeStreamAtomic { out -> out.write(data) }
}

/** Read text from file atomically (recovering any legacy `.bak` file if needed). */
fun File.readTextAtomic(): String {
    return openAtomicInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
}

/** Write text to file atomically. */
fun File.writeTextAtomic(text: String) {
    writeBytesAtomic(text.toByteArray(Charsets.UTF_8))
}

internal fun ByteArray.isZstdCompressed(): Boolean {
    return size >= 4 &&
        this[0] == 0x28.toByte() &&
        this[1] == 0xB5.toByte() &&
        this[2] == 0x2F.toByte() &&
        this[3] == 0xFD.toByte()
}

/** Write bytes to file using GZIP compression atomically (skips GZIP when payload is already ZSTD-compressed). */
fun File.writeBytesCompressedAtomic(array: ByteArray) {
    if (array.isZstdCompressed()) {
        writeBytesAtomic(array)
        return
    }
    writeStreamAtomic { out ->
        ByteArrayInputStream(array).use { input ->
            GZIPOutputStream(out).use { gzipOutput ->
                input.copyTo(gzipOutput)
            }
        }
    }
}

/** Read bytes atomically. If the file is compressed with GZIP the uncompressed data is returned. */
fun File.readBytesUncompressedAtomic(): ByteArray {
    return openAtomicInputStream().use { input ->
        readBytesUncompressedStream(input.uncompressedInputStream())
    }
}
