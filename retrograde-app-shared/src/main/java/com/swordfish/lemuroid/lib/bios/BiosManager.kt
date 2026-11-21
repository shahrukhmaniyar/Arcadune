package com.swordfish.lemuroid.lib.bios

import com.swordfish.lemuroid.common.files.safeDelete
import com.swordfish.lemuroid.common.kotlin.associateByNotNull
import com.swordfish.lemuroid.common.kotlin.writeToFile
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.StorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.Locale

class BiosManager(private val directoriesManager: DirectoriesManager) {
    private val crcLookup = SUPPORTED_BIOS.associateByNotNull { it.externalCRC32?.uppercase(Locale.US) }
    private val nameLookup: Map<String, Bios> =
        buildMap {
            SUPPORTED_BIOS.forEach { bios ->
                val canonicalBase = bios.libretroFileName.substringAfterLast('/').lowercase(Locale.US)
                putIfAbsent(canonicalBase, bios)
                bios.externalName?.lowercase(Locale.US)?.let { extName ->
                    put(extName, bios)
                }
            }
        }

    fun getMissingBiosFiles(
        coreConfig: SystemCoreConfig,
        game: Game,
    ): List<String> {
        val regionalBiosFiles = coreConfig.regionalBIOSFiles

        val gameLabels =
            Regex("\\([A-Za-z]+\\)")
                .findAll(game.title)
                .map { it.value.drop(1).dropLast(1) }
                .filter { it.isNotBlank() }
                .toSet()

        Timber.d("Found game labels: $gameLabels")

        val requiredRegionalFiles =
            gameLabels.intersect(regionalBiosFiles.keys)
                .ifEmpty { regionalBiosFiles.keys }
                .mapNotNull { regionalBiosFiles[it] }
                .distinct()

        Timber.d("Required regional files for game: $requiredRegionalFiles")

        val systemDir = directoriesManager.getSystemDirectory()
        val missingRequired =
            (coreConfig.requiredBIOSFiles + requiredRegionalFiles)
                .distinct()
                .filter { !File(systemDir, it).exists() }
        val missingAnyOf =
            if (coreConfig.anyOfBIOSFiles.isNotEmpty() &&
                coreConfig.anyOfBIOSFiles.none { File(systemDir, it).exists() }
            ) {
                listOf("Any of: ${coreConfig.anyOfBIOSFiles.joinToString(", ")}")
            } else {
                emptyList()
            }

        return missingRequired + missingAnyOf
    }

    @Volatile
    var cachedBiosInfo: BiosInfo? = null
        private set

    fun deleteBiosBefore(timestampMs: Long) {
        Timber.i("Pruning old bios files")
        val systemDir = directoriesManager.getSystemDirectory()
        SUPPORTED_BIOS
            .flatMap { bios ->
                (listOf(bios.libretroFileName) + bios.mirrorFileNames).map { File(systemDir, it) }
            }
            .filter { it.lastModified() < normalizeTimestamp(timestampMs) }
            .forEach {
                Timber.d("Pruning old bios file: ${it.path}")
                it.safeDelete()
            }
        cachedBiosInfo = null
    }

    fun getBiosInfo(): BiosInfo {
        val bios =
            SUPPORTED_BIOS.groupBy {
                File(directoriesManager.getSystemDirectory(), it.libretroFileName).exists()
            }.withDefault { listOf() }

        return BiosInfo(bios.getValue(true), bios.getValue(false)).also { cachedBiosInfo = it }
    }

    suspend fun getBiosInfoAsync(): BiosInfo =
        withContext(Dispatchers.IO) {
            getBiosInfo()
        }

    fun tryAddBiosAfter(
        storageFile: StorageFile,
        inputStream: InputStream,
        timestampMs: Long,
    ): Boolean {
        val bios = findByCRC(storageFile) ?: findByName(storageFile) ?: return false

        Timber.i("Importing bios file: $bios")

        val systemDir = directoriesManager.getSystemDirectory()
        val biosFile = File(systemDir, bios.libretroFileName)
        val mirrorFiles = bios.mirrorFileNames.map { File(systemDir, it) }
        val normalizedTs = normalizeTimestamp(timestampMs)
        val allTargets = listOf(biosFile) + mirrorFiles

        if (allTargets.all { it.exists() && it.setLastModified(normalizedTs) }) {
            Timber.d("Bios file already present. Updated last modification date.")
        } else {
            Timber.d("Bios file not available. Copying new file.")
            biosFile.parentFile?.mkdirs()
            inputStream.writeToFile(biosFile)
            biosFile.setLastModified(normalizedTs)
            mirrorFiles.forEach { mirror ->
                mirror.parentFile?.mkdirs()
                biosFile.inputStream().writeToFile(mirror)
                mirror.setLastModified(normalizedTs)
            }
        }
        cachedBiosInfo = null
        return true
    }

    private fun findByCRC(storageFile: StorageFile): Bios? {
        return storageFile.crc?.uppercase(Locale.US)?.let { crcLookup[it] }
    }

    private fun findByName(storageFile: StorageFile): Bios? {
        val baseName = storageFile.name.substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.US)
        return nameLookup[baseName]
    }

    private fun normalizeTimestamp(timestamp: Long) = (timestamp / 1000) * 1000

    data class BiosInfo(val detected: List<Bios>, val notDetected: List<Bios>)

    companion object {
        private val SUPPORTED_BIOS =
            listOf(
                Bios(
                    "scph101.bin",
                    "6E3735FF4C7DC899EE98981385F6F3D0",
                    "PS One 4.5 NTSC-U/C (Optional on PCSX)",
                    SystemID.PSX,
                    "171BDCEC",
                    "scph101.bin",
                ),
                Bios(
                    "scph7001.bin",
                    "1E68C231D0896B7EADCAD1D7D8E76129",
                    "PS Original 4.1 NTSC-U/C (Optional on PCSX)",
                    SystemID.PSX,
                    "502224B6",
                    "scph7001.bin",
                ),
                Bios(
                    "scph5501.bin",
                    "490F666E1AFB15B7362B406ED1CEA246",
                    "PS Original 3.0 NTSC-U/C (Required for Beetle PSX US)",
                    SystemID.PSX,
                    "8D8CB7E4",
                    "scph5501.bin",
                ),
                Bios(
                    "scph5500.bin",
                    "8DD7D5296A650FAC7319BCE665A6A53C",
                    "PS Original 3.0 NTSC-J (Required for Beetle PSX JP)",
                    SystemID.PSX,
                    "FF3EEB8C",
                    "scph5500.bin",
                ),
                Bios(
                    "scph5502.bin",
                    "32736F17079D0B2B7024407C39BD3050",
                    "PS Original 3.0 PAL-E (Required for Beetle PSX EU)",
                    SystemID.PSX,
                    "D786F0B9",
                    "scph5502.bin",
                ),
                Bios(
                    "scph1001.bin",
                    "924E392ED05558FFDB115408C263DCCF",
                    "PS Original 2.2 NTSC-U/C (Optional on PCSX)",
                    SystemID.PSX,
                    "37157331",
                    "scph1001.bin",
                ),
                Bios(
                    "lynxboot.img",
                    "FCD403DB69F54290B51035D82F835E7B",
                    "Atari Lynx Boot Image (Optional)",
                    SystemID.LYNX,
                    "0D973C9D",
                    "lynxboot.img",
                ),
                Bios(
                    "bios_CD_E.bin",
                    "E66FA1DC5820D254611FDCDBA0662372",
                    "Sega CD EU BIOS",
                    SystemID.SEGACD,
                    "529AC15A",
                    "bios_CD_E.bin",
                ),
                Bios(
                    "bios_CD_J.bin",
                    "278A9397D192149E84E820AC621A8EDD",
                    "Sega CD JP BIOS",
                    SystemID.SEGACD,
                    "9D2DA8F2",
                    "bios_CD_J.bin",
                ),
                Bios(
                    "bios_CD_U.bin",
                    "2EFD74E3232FF260E371B99F84024F7F",
                    "Sega CD US BIOS",
                    SystemID.SEGACD,
                    "C6D10268",
                    "bios_CD_U.bin",
                ),
                Bios(
                    "bios7.bin",
                    "DF692A80A5B1BC90728BC3DFC76CD948",
                    "Nintendo DS ARM7 (Optional)",
                    SystemID.NDS,
                    "1280F0D5",
                    "bios7.bin",
                ),
                Bios(
                    "bios9.bin",
                    "A392174EB3E572FED6447E956BDE4B25",
                    "Nintendo DS ARM9 (Optional)",
                    SystemID.NDS,
                    "2AB23573",
                    "bios9.bin",
                ),
                Bios(
                    "firmware.bin",
                    "E45033D9B0FA6B0DE071292BBA7C9D13",
                    "Nintendo DS Firmware (Optional)",
                    SystemID.NDS,
                    "945F9DC9",
                    "nds_firmware.bin",
                ),
                Bios(
                    "syscard3.pce",
                    "FF1A674273FE3540CCCEF576376407D1",
                    "PC Engine CD System Card 3.0",
                    SystemID.PC_ENGINE,
                    "38179DF8",
                    "syscard3.pce",
                ),
                Bios(
                    "colecovision.rom",
                    "2C66F5911E5B42B8EBE113403548EEE7",
                    "ColecoVision BIOS",
                    SystemID.COLECOVISION,
                    "3AA93EF3",
                    "colecovision.rom",
                ),
                Bios(
                    "MSX.ROM",
                    "AA95AEAEBDACBC2BBAC7FB7DEEE79F53",
                    "MSX BIOS",
                    SystemID.MSX,
                    "94EE12F3",
                    "MSX.ROM",
                ),
                Bios(
                    "MSX2.ROM",
                    "EC3A01C91F24FBDDCA20EDAD00ED8AA2",
                    "MSX2 BIOS",
                    SystemID.MSX,
                    "6AEBE16B",
                    "MSX2.ROM",
                ),
                Bios(
                    "MSX2EXT.ROM",
                    "2183C2AFF17CF4297BDB496DE78C2E8A",
                    "MSX2 SubROM",
                    SystemID.MSX,
                    "AFEFAEE3",
                    "MSX2EXT.ROM",
                ),
                Bios(
                    "panafz1.bin",
                    "F47264DD47FE30F73AB3C010015C155B",
                    "Panasonic FZ-1 3DO BIOS (Optional)",
                    SystemID.THREEDO,
                    "51F2F43D",
                    "panafz1.bin",
                ),
                Bios(
                    "saturn_bios.bin",
                    "AF5828FDFF51384F99B3C4926BE27762",
                    "Sega Saturn BIOS (Optional on YabaSanshiro)",
                    SystemID.SATURN,
                    "F90F0089",
                    "saturn_bios.bin",
                ),
                Bios(
                    "dc/dc_boot.bin",
                    "E10C53C2F8B90BAB96EAD2D368858623",
                    "Dreamcast Boot ROM (Optional)",
                    SystemID.DREAMCAST,
                    "89F2B1A1",
                    "dc_boot.bin",
                ),
                Bios(
                    "dc/dc_flash.bin",
                    "0A93F7940C455905BEA6E392DFDE92A4",
                    "Dreamcast Flash ROM (Optional)",
                    SystemID.DREAMCAST,
                    null,
                    "dc_flash.bin",
                ),
                Bios(
                    "5200.rom",
                    "281F20EA4320404EC820FB7EC0693B38",
                    "Atari 5200 BIOS (Optional)",
                    SystemID.ATARI5200,
                    "4248D3E3",
                    "5200.rom",
                ),
                Bios(
                    "ATARIXL.ROM",
                    "06DAAC977823773A3EEA3422FD26A703",
                    "Atari XL/XE OS ROM (Optional)",
                    SystemID.ATARI800,
                    "1F9CD270",
                    "ATARIXL.ROM",
                ),
                Bios(
                    "ATARIOSB.ROM",
                    "A3E8D617C95D08031FE1B20D541434B2",
                    "Atari 400/800 OS-B ROM (Optional)",
                    SystemID.ATARI800,
                    "0E86D61D",
                    "ATARIOSB.ROM",
                ),
                Bios(
                    "ATARIBAS.ROM",
                    "0BAC0C6A50104045D902DF4503A4C30B",
                    "Atari BASIC ROM (Optional)",
                    SystemID.ATARI800,
                    "7D684184",
                    "ATARIBAS.ROM",
                ),
                Bios(
                    "gba_bios.bin",
                    "A860E8C0B6D573D191E4EC7DB1B1E4F6",
                    "Game Boy Advance BIOS (Required for Beetle GBA)",
                    SystemID.GBA,
                    "81977335",
                    "gba_bios.bin",
                ),
                Bios(
                    "sega_101.bin",
                    "85EC9CA47D8F6807718151CBCCA8B964",
                    "Sega Saturn JP BIOS (Required for Beetle Saturn JP)",
                    SystemID.SATURN,
                    "F90F0089",
                    "sega_101.bin",
                ),
                Bios(
                    "mpr-17933.bin",
                    "3240872C70984B6CBFDA1586CAB68DBE",
                    "Sega Saturn US/EU BIOS (Required for Beetle Saturn US/EU)",
                    SystemID.SATURN,
                    "4BE1D5AB",
                    "mpr-17933.bin",
                ),
                Bios(
                    "disksys.rom",
                    "CA30B50F880EB660A320674ED365EF7A",
                    "Famicom Disk System BIOS (Required for .fds games)",
                    SystemID.NES,
                    "5E607DCF",
                    "disksys.rom",
                ),
                Bios(
                    "neogeo.zip",
                    "",
                    "Neo Geo AES/MVS BIOS (Required for Neo Geo arcade games)",
                    SystemID.FBNEO,
                    null,
                    "neogeo.zip",
                    listOf("fbneo/neogeo.zip", "mame2003-plus/neogeo.zip"),
                ),
            )
    }
}
