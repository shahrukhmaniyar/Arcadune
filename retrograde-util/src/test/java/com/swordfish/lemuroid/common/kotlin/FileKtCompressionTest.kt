package com.swordfish.lemuroid.common.kotlin

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileKtCompressionTest {
    @Test
    fun `isZstdCompressed detects Citra ZSTD save states and rejects non-ZSTD payloads`() {
        val zstdPayload =
            byteArrayOf(
                0x28.toByte(),
                0xB5.toByte(),
                0x2F.toByte(),
                0xFD.toByte(),
                0x01,
                0x02,
                0x03,
            )
        assertTrue(zstdPayload.isZstdCompressed())

        val gzipPayload = byteArrayOf(0x1F.toByte(), 0x8B.toByte(), 0x08, 0x00)
        assertFalse(gzipPayload.isZstdCompressed())

        val shortPayload = byteArrayOf(0x28.toByte(), 0xB5.toByte(), 0x2F.toByte())
        assertFalse(shortPayload.isZstdCompressed())
        assertFalse(ByteArray(0).isZstdCompressed())
    }

    @Test
    fun `uncompressedInputStream decompresses GZIP and passes ZSTD and empty streams untouched`() {
        val plainBytes = "Arcadune save payload".encodeToByteArray()
        val gzipBytes =
            ByteArrayOutputStream().use { out ->
                GZIPOutputStream(out).use { gzip -> gzip.write(plainBytes) }
                out.toByteArray()
            }

        val decodedGzip = readBytesUncompressedStream(ByteArrayInputStream(gzipBytes).uncompressedInputStream())
        assertArrayEquals(plainBytes, decodedGzip)

        val zstdBytes =
            byteArrayOf(
                0x28.toByte(),
                0xB5.toByte(),
                0x2F.toByte(),
                0xFD.toByte(),
                0x41,
                0x42,
                0x43,
                0x44,
            )
        val passthroughZstd = readBytesUncompressedStream(ByteArrayInputStream(zstdBytes).uncompressedInputStream())
        assertArrayEquals(zstdBytes, passthroughZstd)

        val emptyDecoded = readBytesUncompressedStream(ByteArrayInputStream(ByteArray(0)).uncompressedInputStream())
        assertEquals(0, emptyDecoded.size)
    }

    @Test
    fun `writeToFile writes atomically and cleans temporary file when stream fails mid-copy`() {
        val tempDir = Files.createTempDirectory("arcadune-atomic-write").toFile()
        try {
            val target = File(tempDir, "Pokemon Y.3ds")
            val failingStream =
                object : InputStream() {
                    private var bytesRead = 0

                    override fun read(): Int {
                        if (bytesRead >= 128) {
                            throw IOException("Simulated interrupted ROM copy")
                        }
                        bytesRead++
                        return 0x5A
                    }
                }

            assertThrows(IOException::class.java) {
                failingStream.writeToFile(target)
            }
            assertFalse("Truncated target file must not exist after failed write", target.exists())
            assertFalse("Temporary sibling file must be cleaned up", File(tempDir, "${target.name}.tmp").exists())

            val expectedPayload = "Complete 3DS ROM payload".encodeToByteArray()
            ByteArrayInputStream(expectedPayload).writeToFile(target)
            assertTrue(target.exists())
            assertArrayEquals(expectedPayload, target.readBytes())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `extractEntryToFile extracts matching entry atomically and throws on missing entry`() {
        val tempDir = Files.createTempDirectory("arcadune-zip-extract").toFile()
        try {
            val expectedRom = "Extracted ROM image".encodeToByteArray()
            val zipBytes =
                ByteArrayOutputStream().use { out ->
                    ZipOutputStream(out).use { zip ->
                        zip.putNextEntry(ZipEntry("readme.txt"))
                        zip.write("ignore".encodeToByteArray())
                        zip.closeEntry()

                        val romEntry = ZipEntry("game.3ds")
                        romEntry.size = expectedRom.size.toLong()
                        zip.putNextEntry(romEntry)
                        zip.write(expectedRom)
                        zip.closeEntry()
                    }
                    out.toByteArray()
                }

            val target = File(tempDir, "game.3ds")
            ZipInputStream(ByteArrayInputStream(zipBytes)).extractEntryToFile("game.3ds", target)
            assertTrue(target.exists())
            assertArrayEquals(expectedRom, target.readBytes())

            val missingTarget = File(tempDir, "missing.3ds")
            assertThrows(IOException::class.java) {
                ZipInputStream(ByteArrayInputStream(zipBytes)).extractEntryToFile("missing.3ds", missingTarget)
            }
            assertFalse(missingTarget.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `atomic save helpers sync before close and round-trip SRAM and GZIP save states cleanly`() {
        val tempDir = Files.createTempDirectory("arcadune-atomic-sram").toFile()
        try {
            val sramFile = File(tempDir, "Chrono Trigger.srm")
            val firstSram = ByteArray(8192) { (it and 0xFF).toByte() }
            val secondSram = ByteArray(8192) { ((it * 3) and 0xFF).toByte() }

            sramFile.writeBytesAtomic(firstSram)
            assertTrue(sramFile.exists())
            assertFalse(File(tempDir, "Chrono Trigger.srm.new").exists())
            assertArrayEquals(firstSram, sramFile.readBytes())

            sramFile.writeBytesAtomic(secondSram)
            assertArrayEquals(secondSram, sramFile.readBytes())

            val stateFile = File(tempDir, "Chrono Trigger.state1")
            val rawState = "SaveStatePayload-1234567890".repeat(64).encodeToByteArray()
            stateFile.writeBytesCompressedAtomic(rawState)
            assertTrue(stateFile.exists())
            assertFalse(File(tempDir, "Chrono Trigger.state1.new").exists())
            assertArrayEquals(rawState, stateFile.readBytesUncompressedAtomic())

            val metadataFile = File(tempDir, "Chrono Trigger.state1.metadata")
            metadataFile.writeTextAtomic("""{"version":2}""")
            assertEquals("""{"version":2}""", metadataFile.readText())

            assertTrue(File(tempDir, "SUPER_MARIO.ZIP").isZipped())
            assertTrue(File(tempDir, "archive.Zip").isZipped())
            assertFalse(File(tempDir, "Super Mario.sfc").isZipped())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
