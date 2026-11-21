package com.swordfish.lemuroid.common.kotlin

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Random

/**
 * Saving a game state used to take 20+ seconds on a phone because the atomic writer wrote the
 * file one byte at a time (one system call per byte). These tests keep that from coming back.
 */
class FileKtWriteSpeedTest {
    @Test
    fun `a large save is written quickly and reads back the same`() {
        val dir = Files.createTempDirectory("arcadune-write-speed").toFile()
        try {
            // Random bytes do not compress, so the file on disk is as big as the state (6 MB).
            val state = ByteArray(6 * 1024 * 1024).also { Random(42).nextBytes(it) }
            val target = java.io.File(dir, "game.state")

            val startedAt = System.nanoTime()
            target.writeBytesCompressedAtomic(state)
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            // Well under a second with a buffer; writing it a byte at a time takes many seconds.
            assertTrue("Writing 6 MB took $elapsedMs ms", elapsedMs < 4_000)
            assertArrayEquals(state, target.readBytesUncompressedAtomic())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `an uncompressed save is written quickly, atomically and leaves no temporary file`() {
        val dir = Files.createTempDirectory("arcadune-write-atomic").toFile()
        try {
            val data = ByteArray(4 * 1024 * 1024).also { Random(7).nextBytes(it) }
            val target = java.io.File(dir, "save.srm")

            val startedAt = System.nanoTime()
            target.writeBytesAtomic(data)
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            assertTrue("Writing 4 MB took $elapsedMs ms", elapsedMs < 4_000)
            assertArrayEquals(data, target.readBytes())
            assertFalse(java.io.File(dir, "save.srm.new").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
