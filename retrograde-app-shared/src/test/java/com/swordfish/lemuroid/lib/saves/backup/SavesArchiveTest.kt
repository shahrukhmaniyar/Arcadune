package com.swordfish.lemuroid.lib.saves.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SavesArchiveTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun roots(name: String): Map<String, File> =
        linkedMapOf(
            "saves" to temp.newFolder(name, "saves"),
            "states" to temp.newFolder(name, "states"),
        )

    private fun File.write(
        relative: String,
        content: String,
    ): File {
        val file = File(this, relative)
        file.parentFile.mkdirs()
        file.writeBytes(content.toByteArray())
        return file
    }

    private fun backupOf(roots: Map<String, File>): ByteArray {
        val out = ByteArrayOutputStream()
        SavesArchive.write(out, roots, 1L, "1.0.0")
        return out.toByteArray()
    }

    /** Builds a zip by hand, with or without the info entry, to simulate other and hostile files. */
    private fun zipOf(
        withInfo: Boolean,
        vararg entries: Pair<String, String>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (withInfo) {
                zip.putNextEntry(ZipEntry(SavesArchive.INFO_ENTRY))
                zip.write("""{"format":"arcadune-saves-backup","version":1}""".toByteArray())
                zip.closeEntry()
            }
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun backupThenRestoreGivesBackTheSameFiles() {
        val source = roots("source")
        source.getValue("saves").write("Wario Land 4.srm", "save data")
        source.getValue("states").write("fceumm/Contra.state", "state data")

        val target = roots("target")
        val restored = SavesArchive.read(ByteArrayInputStream(backupOf(source)), target)

        assertEquals(2, restored)
        assertArrayEquals("save data".toByteArray(), File(target.getValue("saves"), "Wario Land 4.srm").readBytes())
        assertArrayEquals("state data".toByteArray(), File(target.getValue("states"), "fceumm/Contra.state").readBytes())
    }

    @Test
    fun backupOfEmptyFoldersRestoresNothing() {
        val source = roots("source")
        assertEquals(0, SavesArchive.read(ByteArrayInputStream(backupOf(source)), roots("target")))
    }

    @Test
    fun restoreReplacesFilesWithTheSameNameAndKeepsOthers() {
        val source = roots("source")
        source.getValue("saves").write("a.srm", "from backup")

        val target = roots("target")
        target.getValue("saves").write("a.srm", "old")
        target.getValue("saves").write("b.srm", "untouched")

        SavesArchive.read(ByteArrayInputStream(backupOf(source)), target)

        assertEquals("from backup", File(target.getValue("saves"), "a.srm").readText())
        assertEquals("untouched", File(target.getValue("saves"), "b.srm").readText())
        assertFalse(File(target.getValue("saves"), "a.srm.restoring").exists())
    }

    @Test
    fun fileThatIsNotABackupIsRejected() {
        val target = roots("target")
        val notABackup = zipOf(withInfo = false, "saves/a.srm" to "data")

        try {
            SavesArchive.read(ByteArrayInputStream(notABackup), target)
            fail("a zip without the backup marker must be rejected")
        } catch (expected: SavesArchive.InvalidBackupException) {
            assertFalse(File(target.getValue("saves"), "a.srm").exists())
        }
    }

    @Test
    fun randomBytesAreRejected() {
        try {
            SavesArchive.read(ByteArrayInputStream(ByteArray(2000) { it.toByte() }), roots("target"))
            fail("random bytes must be rejected")
        } catch (expected: java.io.IOException) {
            // Any I/O style rejection is fine, nothing may be written.
        }
    }

    @Test
    fun entryThatEscapesTheSavesFolderIsRejected() {
        val target = roots("target")
        val outside = File(temp.root, "target/evil.txt")
        val hostile = zipOf(withInfo = true, "saves/../evil.txt" to "pwned")

        try {
            SavesArchive.read(ByteArrayInputStream(hostile), target)
            fail("a path that leaves the folder must be rejected")
        } catch (expected: SavesArchive.InvalidBackupException) {
            assertFalse(outside.exists())
        }
    }

    @Test
    fun absoluteAndBackslashPathsAreRejected() {
        for (name in listOf("/etc/evil.txt", "saves\\..\\evil.txt")) {
            try {
                SavesArchive.read(ByteArrayInputStream(zipOf(withInfo = true, name to "x")), roots("t" + name.hashCode()))
                fail("unsafe name should be rejected: $name")
            } catch (expected: SavesArchive.InvalidBackupException) {
                // expected
            }
        }
    }

    @Test
    fun unknownTopLevelFoldersAreIgnored() {
        val target = roots("target")
        val zip = zipOf(withInfo = true, "other/x.txt" to "x", "saves/ok.srm" to "ok")

        val restored = SavesArchive.read(ByteArrayInputStream(zip), target)

        assertEquals(1, restored)
        assertTrue(File(target.getValue("saves"), "ok.srm").exists())
        assertFalse(File(temp.root, "target/other").exists())
    }
}
