package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.common.files.ChdRandomAccess
import com.swordfish.lemuroid.common.files.ChdReader
import com.swordfish.lemuroid.lib.storage.local.DocumentFileParser
import com.swordfish.lemuroid.lib.storage.scanner.SerialScanner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class GameSystemsAndScannerTest {
    @Test
    fun `all 35 SystemIDs are registered in GameSystem and MetaSystemID with unique extensions`() {
        val allSystemIds = SystemID.values().toSet()
        assertEquals(35, allSystemIds.size)

        val gameSystems = GameSystem.all()
        assertEquals(35, gameSystems.size)
        assertEquals(allSystemIds, gameSystems.map { it.id }.toSet())

        val seenUniqueExtensions = mutableSetOf<String>()
        for (system in gameSystems) {
            assertEquals(system, GameSystem.findById(system.id.dbname))
            assertNotNull(MetaSystemID.fromSystemID(system.id))
            assertTrue("Expected at least one core config for ${system.id}", system.systemCoreConfigs.isNotEmpty())

            for (ext in system.uniqueExtensions) {
                assertTrue(
                    "Duplicate unique extension '$ext' on ${system.id}",
                    seenUniqueExtensions.add(ext.lowercase()),
                )
                assertEquals(system, GameSystem.findByUniqueFileExtension(ext))
            }
        }
    }

    @Test
    fun `3DS Citra config disables autoSave, keeps manual states, and exposes hardware shader cache`() {
        val system3ds = GameSystem.findById(SystemID.NINTENDO_3DS.dbname)
        assertTrue(system3ds.uniqueExtensions.containsAll(listOf("3ds", "cci", "cxi")))

        val citraConfig = system3ds.systemCoreConfigs.first { it.coreID == CoreID.CITRA }
        assertTrue(citraConfig.statesSupported)
        assertFalse(citraConfig.autoSaveSupported)

        val hwShaderDefault =
            citraConfig.defaultSettings.firstOrNull { it.key == "citra_use_hw_shader_cache" }
        assertNotNull(hwShaderDefault)
        assertEquals("disabled", hwShaderDefault!!.value)
        assertTrue(citraConfig.exposedAdvancedSettings.any { it.key == "citra_use_hw_shader_cache" })
    }

    @Test
    fun `SerialScanner detects Dreamcast Saturn 3DO Sega32X headers and handles short bin safely`() {
        fun streamWithMagicAt(
            offset: Int,
            magic: ByteArray,
            totalSize: Int = 512,
        ): ByteArrayInputStream {
            val buf = ByteArray(totalSize)
            System.arraycopy(magic, 0, buf, offset, magic.size)
            return ByteArrayInputStream(buf)
        }

        val dcIso =
            SerialScanner.extractInfo(
                "game.iso",
                streamWithMagicAt(0x0000, "SEGA SEGAKATANA".toByteArray(Charsets.US_ASCII)),
            )
        assertEquals(SystemID.DREAMCAST, dcIso.systemID)

        val dcRawBin =
            SerialScanner.extractInfo(
                "track03.bin",
                streamWithMagicAt(0x0010, "SEGA SEGAKATANA".toByteArray(Charsets.US_ASCII)),
            )
        assertEquals(SystemID.DREAMCAST, dcRawBin.systemID)

        val saturnIso =
            SerialScanner.extractInfo(
                "panzer.iso",
                streamWithMagicAt(0x0000, "SEGA SEGASATURN".toByteArray(Charsets.US_ASCII)),
            )
        assertEquals(SystemID.SATURN, saturnIso.systemID)

        val threeDoMagic = byteArrayOf(0x01, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x01)
        val threeDoBin =
            SerialScanner.extractInfo(
                "roadrash.bin",
                streamWithMagicAt(0x0010, threeDoMagic),
            )
        assertEquals(SystemID.THREEDO, threeDoBin.systemID)

        val sega32xBin =
            SerialScanner.extractInfo(
                "knuckles.bin",
                streamWithMagicAt(0x0100, "SEGA 32X".toByteArray(Charsets.US_ASCII)),
            )
        assertEquals(SystemID.SEGA32X, sega32xBin.systemID)

        // Regression test: tiny <38 KB .bin (e.g. Atari 2600 4 KB ROM) must not throw IndexOutOfBoundsException
        val shortBin = SerialScanner.extractInfo("combat.bin", ByteArrayInputStream(ByteArray(1024)))
        assertNull(shortBin.systemID)
        assertNull(shortBin.serial)

        // Empty 0-byte file must not throw NegativeArraySizeException
        val emptyBin = SerialScanner.extractInfo("empty.bin", ByteArrayInputStream(ByteArray(0)))
        assertNull(emptyBin.systemID)
        assertNull(emptyBin.serial)
    }

    @Test
    fun `SerialScanner detects Apple II vs MSX dsk images, PSX img Mode2 raw, PSP cso, and chunked streams`() {
        // Apple II 140 KB DOS 3.3 floppy (.dsk = 35 * 16 * 256 = 143,360 bytes)
        val apple2Bytes = ByteArray(143_360)
        apple2Bytes[0] = 0x01
        val apple2Info =
            SerialScanner.extractInfo(
                "Donkey Kong (1983)(Atari).dsk",
                ByteArrayInputStream(apple2Bytes),
                fileSize = apple2Bytes.size.toLong(),
            )
        assertEquals(SystemID.APPLEII, apple2Info.systemID)

        // MSX 720 KB FAT12 floppy (.dsk = 737,280 bytes, 512 bytes/sector, media F9)
        val msxBytes = ByteArray(737_280)
        msxBytes[0] = 0xEB.toByte()
        msxBytes[0x0B] = 0x00
        msxBytes[0x0C] = 0x02
        msxBytes[0x15] = 0xF9.toByte()
        val msxInfo =
            SerialScanner.extractInfo(
                "Metal Gear.dsk",
                ByteArrayInputStream(msxBytes),
                fileSize = msxBytes.size.toLong(),
            )
        assertEquals(SystemID.MSX, msxInfo.systemID)

        // Amstrad CPC .dsk must not false-positive as Apple II or MSX
        val cpcBytes = ByteArray(143_360)
        val cpcSig = "EXTENDED CPC DSK File".toByteArray(Charsets.US_ASCII)
        System.arraycopy(cpcSig, 0, cpcBytes, 0, cpcSig.size)
        val cpcInfo =
            SerialScanner.extractInfo(
                "Dizzy.dsk",
                ByteArrayInputStream(cpcBytes),
                fileSize = cpcBytes.size.toLong(),
            )
        assertNull(cpcInfo.systemID)

        // PSX CloneCD .img (Mode 2 2352-byte sectors -> PLAYSTATION at 0x9320) over a 512-byte chunked stream
        val psxImg = ByteArray(64 * 1024)
        val psxMagic = "PLAYSTATION".toByteArray(Charsets.US_ASCII)
        System.arraycopy(psxMagic, 0, psxImg, 0x9320, psxMagic.size)
        val psxBoot = "BOOT = cdrom:\\SLUS_010.53;1".toByteArray(Charsets.US_ASCII)
        System.arraycopy(psxBoot, 0, psxImg, 0x9400, psxBoot.size)
        val chunkedStream =
            object : InputStream() {
                private var pos = 0

                override fun read(): Int = if (pos < psxImg.size) psxImg[pos++].toInt() and 0xFF else -1

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int {
                    if (pos >= psxImg.size) return -1
                    val n = minOf(len, 512, psxImg.size - pos)
                    System.arraycopy(psxImg, pos, b, off, n)
                    pos += n
                    return n
                }
            }
        val psxImgInfo = SerialScanner.extractInfo("Road Rash Jailbreak.img", chunkedStream)
        assertEquals(SystemID.PSX, psxImgInfo.systemID)
        assertEquals("SLUS-01053", psxImgInfo.serial)

        // PSP .cso ("CISO" magic) and uniqueExtension
        val csoBytes = "CISO\u0000\u0000\u0000\u0018".toByteArray(Charsets.US_ASCII)
        val csoInfo = SerialScanner.extractInfo("God of War.cso", ByteArrayInputStream(csoBytes))
        assertEquals(SystemID.PSP, csoInfo.systemID)
        assertEquals(SystemID.PSP, GameSystem.findByUniqueFileExtension("cso")?.id)

        // Genesis .bin ("SEGA MEGA DRIVE" at 0x0100)
        val genesisBin = ByteArray(512)
        val mdSig = "SEGA MEGA DRIVE ".toByteArray(Charsets.US_ASCII)
        System.arraycopy(mdSig, 0, genesisBin, 0x0100, mdSig.size)
        assertEquals(
            SystemID.GENESIS,
            SerialScanner.extractInfo("Sonic.bin", ByteArrayInputStream(genesisBin)).systemID,
        )
    }

    @Test
    fun `DocumentFileParser and StorageFilesMerger handle Arcade zips cue variants and m3u comments`() {
        fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zos ->
                for ((name, bytes) in entries) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            return out.toByteArray()
        }

        // Neo Geo multi-chip arcade zip (093-p1.p1, 093-c1.c1) must NOT be treated as single-ROM console zip
        val neoGeoZip =
            buildZip(
                listOf(
                    "093-p1.p1" to ByteArray(256),
                    "093-c1.c1" to ByteArray(256),
                ),
            )
        ZipInputStream(ByteArrayInputStream(neoGeoZip)).use { zis ->
            assertNull(DocumentFileParser.findGameEntry(zis, neoGeoZip.size.toLong()))
        }

        // Single-ROM console zip with sidecar README.txt must return the ROM entry
        val gbaZip =
            buildZip(
                listOf(
                    "READ ME.txt" to "hello".toByteArray(),
                    "Advance Wars.GBA" to ByteArray(128),
                ),
            )
        ZipInputStream(ByteArrayInputStream(gbaZip)).use { zis ->
            val found = DocumentFileParser.findGameEntry(zis, gbaZip.size.toLong())
            assertNotNull(found)
            assertEquals("Advance Wars.GBA", found!!.name)
        }

        // Cue parser handles double quotes, single quotes, and unquoted FILE lines
        val cueRefs =
            StorageFilesMerger.parseCueFileReferences(
                listOf(
                    "FILE \"Road Rash Jailbreak.img\" BINARY",
                    "  TRACK 01 MODE2/2352",
                    "file 'Track 02.bin' BINARY",
                    "FILE track03.bin BINARY",
                ),
            )
        assertEquals(listOf("Road Rash Jailbreak.img", "Track 02.bin", "track03.bin"), cueRefs)

        // M3U parser ignores comments, blank lines, and relative directory prefixes
        val m3uEntries =
            StorageFilesMerger.parseM3uEntries(
                listOf(
                    "#EXTM3U",
                    "  ./Metal Gear Solid (Disc 1).chd  ",
                    "",
                    "Metal Gear Solid (Disc 2).CHD",
                    "",
                ),
            )
        assertEquals(
            listOf("metal gear solid (disc 1).chd", "metal gear solid (disc 2).chd"),
            m3uEntries,
        )

        // DOS system supports .zip, .exe, .iso, .chd in dos/ folders
        val dosSystem = GameSystem.findById(SystemID.DOS.dbname)
        assertTrue(dosSystem.supportedExtensions.containsAll(listOf("dosz", "zip", "exe", "iso", "chd")))

        // Apple II AppleWin defaults to Autostart-capable NMOS 6502 "Apple //e (original)"
        val apple2Config =
            GameSystem.findById(SystemID.APPLEII.dbname)
                .systemCoreConfigs
                .first { it.coreID == CoreID.APPLEWIN }
        val machineDefault = apple2Config.defaultSettings.firstOrNull { it.key == "applewin_machine" }
        assertNotNull(machineDefault)
        assertEquals("Apple //e (original)", machineDefault!!.value)
    }

    @Test
    fun `3DO Opera config defaults opera_active_devices to 1 and exposes it in advanced settings`() {
        val system3do = GameSystem.findById(SystemID.THREEDO.dbname)
        val operaConfig = system3do.systemCoreConfigs.first { it.coreID == CoreID.OPERA }
        val activeDevicesDefault =
            operaConfig.defaultSettings.firstOrNull { it.key == "opera_active_devices" }
        assertNotNull(activeDevicesDefault)
        assertEquals("1", activeDevicesDefault!!.value)
        assertTrue(operaConfig.exposedAdvancedSettings.any { it.key == "opera_active_devices" })
    }

    @Test
    fun `SerialScanner and ChdReader detect 3DO and PSX from CHD v5 cdzl images and strip cooked headers`() {
        val threeDoMagic = byteArrayOf(0x01, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x01)
        val rawSector0 = ByteArray(2352)
        // MODE1_RAW 16-byte sync + header
        val sync =
            byteArrayOf(
                0x00,
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0x00,
                0x00,
                0x02,
                0x00,
                0x01,
            )
        System.arraycopy(sync, 0, rawSector0, 0, sync.size)
        System.arraycopy(threeDoMagic, 0, rawSector0, 16, threeDoMagic.size)

        val chdBytes = buildTestChdV5Cdzl(listOf(0 to rawSector0), totalFrames = 32)
        val info = SerialScanner.extractInfo("Road Rash (USA).chd", ByteArrayInputStream(chdBytes))
        assertEquals(SystemID.THREEDO, info.systemID)

        // Verify RetroAchievements cooked 2048-byte sector reader strips the 16-byte MODE1_RAW header
        val cooked =
            ChdReader.openTrack(
                com.swordfish.lemuroid.common.files.ByteArrayChdAccess(chdBytes),
                trackSelector = 1,
            )?.use { handle ->
                assertEquals(0, handle.firstTrackSector)
                handle.readCookedSectors(0, 132)
            }
        assertNotNull(cooked)
        assertEquals(132, cooked!!.size)
        assertArrayEquals(threeDoMagic, cooked.copyOfRange(0, 7))

        // Verify PSX CHD v5 detection + serial extraction across hunk boundaries (sector 16 = hunk 2)
        val psxSector16 = ByteArray(2352)
        System.arraycopy(sync, 0, psxSector16, 0, 12)
        psxSector16[15] = 0x02
        val psxId = "PLAYSTATION".toByteArray(Charsets.US_ASCII)
        System.arraycopy(psxId, 0, psxSector16, 0x20, psxId.size)
        val psxSerial = "BOOT = cdrom:\\SLUS_005.84;1".toByteArray(Charsets.US_ASCII)
        System.arraycopy(psxSerial, 0, psxSector16, 0x80, psxSerial.size)

        val psxChdBytes =
            buildTestChdV5Cdzl(
                sectors = listOf(16 to psxSector16),
                totalFrames = 32,
                trackType = "MODE2_RAW",
            )
        val psxInfo = SerialScanner.extractInfo("Street Fighter.chd", ByteArrayInputStream(psxChdBytes))
        assertEquals(SystemID.PSX, psxInfo.systemID)
        assertEquals("SLUS-00584", psxInfo.serial)
    }

    @Test
    fun `ChdReader decodes real 3DO CHD v5 cdlz LZMA slice when scratch sample is present`() {
        val sliceFile =
            File(
                "C:/Users/shahr/.gemini/antigravity/brain/" +
                    "41135c10-0ac0-4fa4-98ac-7732e27a8d28/scratch/dragons_lair_slice.txt",
            )
        if (!sliceFile.isFile) return

        val rawText = sliceFile.readText()
        val parts = rawText.split("---MAP---")
        if (parts.size != 2) return
        val headBytes = Base64.getMimeDecoder().decode(parts[0].trim())
        val mapBytes = Base64.getMimeDecoder().decode(parts[1].trim())
        val mapOffset = 126603118L
        val totalFileSize = mapOffset + mapBytes.size

        val sparseAccess =
            object : ChdRandomAccess {
                override val size: Long = totalFileSize

                override fun readAt(
                    offset: Long,
                    dest: ByteArray,
                    destOffset: Int,
                    length: Int,
                ): Int {
                    return when {
                        offset >= 0L && offset + length <= headBytes.size -> {
                            System.arraycopy(headBytes, offset.toInt(), dest, destOffset, length)
                            length
                        }
                        offset >= mapOffset && offset + length <= mapOffset + mapBytes.size -> {
                            System.arraycopy(mapBytes, (offset - mapOffset).toInt(), dest, destOffset, length)
                            length
                        }
                        else -> -1
                    }
                }

                override fun close() = Unit
            }

        val diskInfo = SerialScanner.extractInfoForChd(sparseAccess)
        assertEquals(SystemID.THREEDO, diskInfo.systemID)

        val cookedHeader =
            ChdReader.openTrack(sparseAccess, trackSelector = 1, ownSource = false)?.use { handle ->
                handle.readCookedSectors(0, 132)
            }
        assertNotNull(cookedHeader)
        assertArrayEquals(
            byteArrayOf(0x01, 0x5A, 0x5A, 0x5A, 0x5A, 0x5A, 0x01),
            cookedHeader!!.copyOfRange(0, 7),
        )
    }

    private fun buildTestChdV5Cdzl(
        sectors: List<Pair<Int, ByteArray>>,
        totalFrames: Int,
        trackType: String = "MODE1_RAW",
    ): ByteArray {
        val framesPerHunk = 8
        val unitBytes = 2448
        val hunkBytes = framesPerHunk * unitBytes // 19584
        val hunkCount = (totalFrames + framesPerHunk - 1) / framesPerHunk
        val sectorMap = sectors.toMap()

        val metaStr =
            "TRACK:1 TYPE:$trackType SUBTYPE:NONE FRAMES:$totalFrames PREGAP:0 PGTYPE:MODE1 PGSUB:NONE POSTGAP:0\u0000"
        val metaPayload = metaStr.toByteArray(Charsets.US_ASCII)
        val metaOffset = 124L
        val dataStartOffset = metaOffset + 16L + metaPayload.size

        val compressedHunks = ArrayList<ByteArray>(hunkCount)
        for (h in 0 until hunkCount) {
            val rawSectorsConcat = ByteArray(framesPerHunk * 2352)
            var eccMask = 0
            for (f in 0 until framesPerHunk) {
                val secIdx = h * framesPerHunk + f
                val secData = sectorMap[secIdx]
                if (secData != null) {
                    val copyBytes = secData.copyOf(2352)
                    // Strip sync bytes in compressed payload to exercise sync reconstitution
                    for (z in 0 until 12) copyBytes[z] = 0
                    System.arraycopy(copyBytes, 0, rawSectorsConcat, f * 2352, 2352)
                    eccMask = eccMask or (1 shl f)
                }
            }
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            val deflatedBase =
                try {
                    deflater.setInput(rawSectorsConcat)
                    deflater.finish()
                    val buf = ByteArray(rawSectorsConcat.size + 128)
                    val len = deflater.deflate(buf)
                    buf.copyOf(len)
                } finally {
                    deflater.end()
                }
            val hunkStream = ByteArrayOutputStream()
            hunkStream.write(eccMask) // 1 byte ecc bitmap for 8 frames
            hunkStream.write((deflatedBase.size ushr 8) and 0xFF)
            hunkStream.write(deflatedBase.size and 0xFF)
            hunkStream.write(deflatedBase)
            hunkStream.write(byteArrayOf(0x03, 0x00)) // minimal subcode placeholder
            compressedHunks.add(hunkStream.toByteArray())
        }

        // Build compressed v5 map: Huffman RLE tree with symbol 0 = 1 bit, symbol 1 = 1 bit (canonical valid tree),
        // followed by hunkCount '0' bits (all hunks use compressor 0 = cdzl), then (16-bit length + 16-bit crc=0) per hunk.
        val mapBits = BitWriter()
        // RLE tree for 16 codes (4-bit values):
        // code 0 -> 2 (escaped as 2 -> wait: 1 is escape! To emit code 0 = len 1, we write escape 1, 1 -> single 1!
        // code 1 -> len 1: write 1, 1!
        // codes 2..15 (14 codes) -> len 0: write 1, 0, (14 - 3 = 11)!
        mapBits.write(1, 4)
        mapBits.write(1, 4) // symbol 0 has numbits = 1 (canonical code 0)
        mapBits.write(1, 4)
        mapBits.write(1, 4) // symbol 1 has numbits = 1 (canonical code 1)
        mapBits.write(1, 4)
        mapBits.write(0, 4)
        mapBits.write(14 - 3, 4) // symbols 2..15 (14 symbols) have numbits = 0

        repeat(hunkCount) {
            mapBits.write(0, 1) // symbol 0 -> V5_COMP_TYPE_0
        }
        for (hBytes in compressedHunks) {
            mapBits.write(hBytes.size, 16) // lengthBits = 16
            mapBits.write(0, 16) // crc16 = 0
        }
        val compressedMapBytes = mapBits.toByteArray()

        val totalDataBytes = compressedHunks.sumOf { it.size }
        val mapOffset = dataStartOffset + totalDataBytes

        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(124)
        header.put("MComprHD".toByteArray(Charsets.US_ASCII))
        header.putInt(124)
        header.putInt(5)
        header.putInt(0x63647A6C) // 'cdzl'
        header.putInt(0)
        header.putInt(0)
        header.putInt(0)
        header.putLong(hunkCount.toLong() * hunkBytes.toLong())
        header.putLong(mapOffset)
        header.putLong(metaOffset)
        header.putInt(hunkBytes)
        header.putInt(unitBytes)
        header.position(124)
        out.write(header.array())

        val metaHdr = ByteBuffer.allocate(16)
        metaHdr.putInt(0x43485432) // 'CHT2'
        metaHdr.putInt((0x01 shl 24) or metaPayload.size)
        metaHdr.putLong(0L)
        out.write(metaHdr.array())
        out.write(metaPayload)

        for (hBytes in compressedHunks) {
            out.write(hBytes)
        }

        val mapHdr = ByteBuffer.allocate(16)
        mapHdr.putInt(compressedMapBytes.size)
        mapHdr.putShort(((dataStartOffset ushr 32) and 0xFFFFL).toShort())
        mapHdr.putInt((dataStartOffset and 0xFFFFFFFFL).toInt())
        mapHdr.putShort(0) // map crc
        mapHdr.put(16.toByte()) // lengthBits
        mapHdr.put(8.toByte()) // selfBits
        mapHdr.put(8.toByte()) // parentBits
        mapHdr.put(0.toByte())
        out.write(mapHdr.array())
        out.write(compressedMapBytes)

        return out.toByteArray()
    }

    private class BitWriter {
        private val bytes = ByteArrayOutputStream()
        private var curByte = 0
        private var bitsUsed = 0

        fun write(
            value: Int,
            numBits: Int,
        ) {
            for (b in (numBits - 1) downTo 0) {
                val bit = (value ushr b) and 1
                curByte = (curByte shl 1) or bit
                bitsUsed++
                if (bitsUsed == 8) {
                    bytes.write(curByte)
                    curByte = 0
                    bitsUsed = 0
                }
            }
        }

        fun toByteArray(): ByteArray {
            if (bitsUsed > 0) {
                bytes.write(curByte shl (8 - bitsUsed))
                curByte = 0
                bitsUsed = 0
            }
            return bytes.toByteArray()
        }
    }
}
