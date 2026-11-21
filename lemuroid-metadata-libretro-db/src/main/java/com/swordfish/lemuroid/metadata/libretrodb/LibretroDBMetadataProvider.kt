package com.swordfish.lemuroid.metadata.libretrodb

import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDBManager
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDatabase
import com.swordfish.lemuroid.metadata.libretrodb.db.entity.LibretroRom
import timber.log.Timber
import java.util.Locale

class LibretroDBMetadataProvider(private val ovgdbManager: LibretroDBManager) :
    GameMetadataProvider {
    companion object {
        private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")
        private val PATH_SEGMENT_SPLIT = Regex("[/\\\\:]+")
        private val SEGMENT_SEPARATORS = Regex("[\\s_\\-]+")
        private val ARCADE_BIOS_ZIPS =
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
        private val FOLDER_SEGMENT_ALIASES: Map<String, Set<String>> =
            mapOf(
                SystemID.GENESIS.dbname to setOf("md", "genesis", "megadrive", "segamegadrive", "segagenesis"),
                SystemID.SEGACD.dbname to setOf("scd", "segacd", "megacd", "segamegacd"),
                SystemID.PSX.dbname to setOf("psx", "ps1", "psone", "playstation", "sonyplaystation"),
                SystemID.PSP.dbname to setOf("psp", "playstationportable", "sonypsp"),
                SystemID.FBNEO.dbname to setOf("fbneo", "fba", "arcade", "neogeo", "cps1", "cps2", "cps3"),
                SystemID.MAME2003PLUS.dbname to setOf("mame2003plus", "mame2003", "mame"),
                SystemID.DOS.dbname to setOf("dos", "msdos", "dosbox"),
                SystemID.PC_ENGINE.dbname to setOf("pce", "pcengine", "tg16", "turbografx", "turbografx16", "pcecd"),
                SystemID.DREAMCAST.dbname to setOf("dc", "dreamcast", "segadreamcast"),
                SystemID.THREEDO.dbname to setOf("3do", "threedo", "panasonic3do", "the3docompany3do"),
                SystemID.SATURN.dbname to setOf("saturn", "segasaturn"),
                SystemID.SEGA32X.dbname to setOf("32x", "sega32x"),
                SystemID.ATARI5200.dbname to setOf("a5200", "atari5200"),
                SystemID.ATARI7800.dbname to setOf("a7800", "atari7800"),
                SystemID.ATARI800.dbname to setOf("a800", "atari800", "atarixl"),
                SystemID.COLECOVISION.dbname to setOf("coleco", "colecovision"),
                SystemID.C64.dbname to setOf("c64", "commodore64"),
                SystemID.APPLEII.dbname to setOf("apple2", "appleii"),
                SystemID.MSX.dbname to setOf("msx", "msx1", "msx2"),
            )
    }

    private val sortedSystemIds: List<String> by lazy {
        SystemID.values()
            .map { it.dbname }
            .sortedByDescending { it.length }
    }

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        val db = ovgdbManager.dbInstance

        Timber.d("Looking metadata for file: $storageFile")

        val metadata =
            runCatching {
                findByCRC(storageFile, db)
                    ?: findBySerial(storageFile, db)
                    ?: findByFilename(db, storageFile)
                    ?: findByPathAndFilename(db, storageFile)
                    ?: findByArcadeZipFilename(db, storageFile)
                    ?: findByUniqueExtension(storageFile)
                    ?: findByKnownSystem(storageFile)
                    ?: findByPathAndSupportedExtension(storageFile)
            }.getOrElse {
                Timber.e("Error in retrieving $storageFile metadata: $it... Skipping.")
                null
            }

        metadata?.let { Timber.d("Metadata retrieved for item: $it") }

        return metadata
    }

    private fun convertToGameMetadata(rom: LibretroRom): GameMetadata {
        val system = GameSystem.findById(rom.system!!)
        return GameMetadata(
            name = rom.name,
            romName = rom.romName,
            thumbnail = computeCoverUrl(system, rom.name),
            system = rom.system,
            developer = rom.developer,
        )
    }

    private suspend fun findByFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findAllByFileName(file.name)
            .firstOrNull { rom ->
                rom.system != null &&
                    runCatching { extractGameSystem(rom).scanOptions.scanByFilename }.getOrDefault(false)
            }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findByPathAndFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findAllByFileName(file.name)
            .firstOrNull { rom ->
                val system = rom.system?.let { runCatching { GameSystem.findById(it) }.getOrNull() }
                system != null &&
                    system.scanOptions.scanByPathAndFilename &&
                    parentContainsSystem(file.path, system.id.dbname)
            }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findByArcadeZipFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        if (file.extension != "zip") return null
        val normalizedName = file.name.lowercase(Locale.US)
        if (normalizedName in ARCADE_BIOS_ZIPS) return null

        val matches = db.gameDao().findAllByFileName(file.name)
        if (matches.isEmpty()) return null

        val preferred =
            matches.firstOrNull { it.system == SystemID.FBNEO.dbname }
                ?: matches.firstOrNull { it.system == SystemID.MAME2003PLUS.dbname }
        return preferred?.let { convertToGameMetadata(it) }
    }

    private fun findByPathAndSupportedExtension(file: StorageFile): GameMetadata? {
        val system =
            sortedSystemIds
                .filter { parentContainsSystem(file.path, it) }
                .map { GameSystem.findById(it) }
                .filter { it.scanOptions.scanByPathAndSupportedExtensions }
                .firstOrNull { it.supportedExtensions.contains(file.extension) }

        return system?.let {
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = computeCoverUrl(it, file.extensionlessName),
                system = it.id.dbname,
                developer = null,
            )
        }
    }

    private fun parentContainsSystem(
        parent: String?,
        dbname: String,
    ): Boolean {
        val lower = parent?.lowercase(Locale.US) ?: return false
        if (lower.contains(dbname)) {
            return true
        }
        val aliases = FOLDER_SEGMENT_ALIASES[dbname] ?: return false
        val normalized = lower.replace("%2f", "/").replace("%3a", ":").replace("%20", " ")
        return normalized.split(PATH_SEGMENT_SPLIT).any { segment ->
            val compact = segment.trim().replace(SEGMENT_SEPARATORS, "")
            segment in aliases || compact in aliases
        }
    }

    private suspend fun findByCRC(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.crc == null || file.crc == "0") return null
        return file.crc?.let { crc32 -> db.gameDao().findByCRC(crc32) }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findBySerial(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.serial == null) return null
        return db.gameDao().findBySerial(file.serial!!)
            ?.let { convertToGameMetadata(it) }
    }

    private fun findByKnownSystem(file: StorageFile): GameMetadata? {
        val knownSystemId = file.systemID ?: return null
        val gameSystem = runCatching { GameSystem.findById(knownSystemId.dbname) }.getOrNull()

        return GameMetadata(
            name = file.extensionlessName,
            romName = file.name,
            thumbnail = gameSystem?.let { computeCoverUrl(it, file.extensionlessName) },
            system = knownSystemId.dbname,
            developer = null,
        )
    }

    private fun findByUniqueExtension(file: StorageFile): GameMetadata? {
        val system = GameSystem.findByUniqueFileExtension(file.extension)

        if (system?.scanOptions?.scanByUniqueExtension == false) {
            return null
        }

        val result =
            system?.let {
                GameMetadata(
                    name = file.extensionlessName,
                    romName = file.name,
                    thumbnail = computeCoverUrl(it, file.extensionlessName),
                    system = it.id.dbname,
                    developer = null,
                )
            }

        return result
    }

    private fun extractGameSystem(rom: LibretroRom): GameSystem {
        return GameSystem.findById(rom.system!!)
    }

    private fun computeCoverUrl(
        system: GameSystem,
        name: String?,
    ): String? {
        var systemName = system.libretroFullName

        // Specific mame version don't have any thumbnails in Libretro database
        if (system.id == SystemID.MAME2003PLUS) {
            systemName = "MAME"
        }

        if (name == null) {
            return null
        }

        val imageType = "Named_Boxarts"

        val thumbGameName = name.replace(THUMB_REPLACE, "_")

        return "http://thumbnails.libretro.com/$systemName/$imageType/$thumbGameName.png"
    }
}
