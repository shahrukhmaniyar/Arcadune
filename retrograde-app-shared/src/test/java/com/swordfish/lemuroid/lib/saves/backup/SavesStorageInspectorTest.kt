package com.swordfish.lemuroid.lib.saves.backup

import com.swordfish.lemuroid.lib.library.SystemID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class SavesStorageInspectorTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun File.writeFile(
        relative: String,
        content: String,
    ): File {
        val file = File(this, relative)
        file.parentFile?.mkdirs()
        file.writeBytes(content.toByteArray())
        return file
    }

    @Test
    fun inspectCountsSavesAndStatesExcludingMetadataAndTempFiles() {
        val savesDir = temp.newFolder("saves")
        val statesDir = temp.newFolder("states")

        savesDir.writeFile("Castlevania.srm", "123456")
        savesDir.writeFile("PSP/SAVEDATA/DATA.BIN", "abcd")
        savesDir.writeFile("empty.srm", "")
        savesDir.writeFile("broken.srm.restoring", "temp")

        statesDir.writeFile("pcsx_rearmed/Castlevania.state", "statebytes")
        statesDir.writeFile("pcsx_rearmed/Castlevania.state.metadata", "{}")
        statesDir.writeFile("pcsx_rearmed/Castlevania.slot1", "slot")
        statesDir.writeFile("pcsx_rearmed/temp.state.restoring", "temp")

        val stats = SavesStorageInspector.inspect(savesDir, statesDir)

        assertEquals(2, stats.savesCount)
        assertEquals(10L, stats.savesBytes)
        assertEquals(2, stats.statesCount)
        assertEquals(16L, stats.statesBytes)
        assertEquals(savesDir.absolutePath, stats.absolutePath)
    }

    @Test
    fun listExportableSaveFilesReturnsSortedInvariantPaths() {
        val savesDir = temp.newFolder("saves_export")
        savesDir.writeFile("zeta.srm", "z")
        savesDir.writeFile("PSP/SAVEDATA/ULUS/SAVE.BIN", "psp")
        savesDir.writeFile("alpha.srm", "a")
        savesDir.writeFile("empty.srm", "")
        savesDir.writeFile("tmp.srm.restoring", "ignored")

        val paths = SavesStorageInspector.listExportableSaveFiles(savesDir).map { it.first }

        assertEquals(
            listOf(
                "PSP/SAVEDATA/ULUS/SAVE.BIN",
                "alpha.srm",
                "zeta.srm",
            ),
            paths,
        )
    }

    @Test
    fun formatDisplayPathConvertsAndroidInternalAndSdPathsCleanly() {
        assertEquals(
            "Internal storage/Android/data/io.github.shahrukhmaniyar.arcadune/files/saves",
            SavesStorageInspector.formatDisplayPath(
                "/storage/emulated/0/Android/data/io.github.shahrukhmaniyar.arcadune/files/saves",
            ),
        )
        assertEquals(
            "Internal storage/Android/data/pkg/files/saves",
            SavesStorageInspector.formatDisplayPath("/sdcard/Android/data/pkg/files/saves"),
        )
        assertEquals(
            "SD card (1234-ABCD)/Android/data/pkg/files/saves",
            SavesStorageInspector.formatDisplayPath("/storage/1234-ABCD/Android/data/pkg/files/saves"),
        )
    }

    @Test
    fun supportsSingleFileSaveExcludesFolderBasedSystems() {
        assertTrue(SavesStorageInspector.supportsSingleFileSave(SystemID.PSX.dbname))
        assertTrue(SavesStorageInspector.supportsSingleFileSave(SystemID.GBA.dbname))
        assertTrue(SavesStorageInspector.supportsSingleFileSave(SystemID.NDS.dbname))
        assertFalse(SavesStorageInspector.supportsSingleFileSave(SystemID.PSP.dbname))
        assertFalse(SavesStorageInspector.supportsSingleFileSave(SystemID.NINTENDO_3DS.dbname))
        assertFalse(SavesStorageInspector.supportsSingleFileSave(SystemID.DOS.dbname))
    }

    @Test
    fun readBoundedSaveBytesAcceptsValidSaveAndRejectsEmptyOrOversized() {
        val payload = "sram-data".toByteArray()
        assertArrayEquals(payload, SavesStorageInspector.readBoundedSaveBytes(ByteArrayInputStream(payload), 100L))

        val emptyFailure =
            runCatching {
                SavesStorageInspector.readBoundedSaveBytes(ByteArrayInputStream(ByteArray(0)), 100L)
            }
        assertTrue(emptyFailure.exceptionOrNull() is SavesArchive.InvalidBackupException)

        val largeFailure =
            runCatching {
                SavesStorageInspector.readBoundedSaveBytes(ByteArrayInputStream(ByteArray(101)), 100L)
            }
        assertTrue(largeFailure.exceptionOrNull() is SavesArchive.InvalidBackupException)
    }
}
