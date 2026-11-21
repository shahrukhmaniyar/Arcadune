package com.swordfish.lemuroid.lib.storage.scanner

import com.swordfish.lemuroid.common.files.ByteArrayChdAccess
import com.swordfish.lemuroid.common.files.ChdRandomAccess
import com.swordfish.lemuroid.common.files.ChdReader
import com.swordfish.lemuroid.common.files.FileUtils
import com.swordfish.lemuroid.common.kotlin.indexOf
import com.swordfish.lemuroid.common.kotlin.kiloBytes
import com.swordfish.lemuroid.common.kotlin.megaBytes
import com.swordfish.lemuroid.common.kotlin.startsWithAny
import com.swordfish.lemuroid.lib.library.SystemID
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

object SerialScanner {
    private val READ_BUFFER_SIZE = 64.kiloBytes()
    private val MAX_STREAM_CHD_BYTES = 32.megaBytes()
    private const val MAX_DSK_PROBE_BYTES = 1_000_000L
    private val PCE_CD_SIGNATURE = "PC Engine CD-ROM SYSTEM".toByteArray(Charsets.US_ASCII)
    private val CSO_SIGNATURE = "CISO".toByteArray(Charsets.US_ASCII)
    private val CPC_MV_SIGNATURE = "MV - CPC".toByteArray(Charsets.US_ASCII)
    private val CPC_EXT_SIGNATURE = "EXTENDED CPC DSK".toByteArray(Charsets.US_ASCII)
    private val PRODOS_SIGNATURE = "PRODOS".toByteArray(Charsets.US_ASCII)

    data class DiskInfo(val serial: String?, val systemID: SystemID?)

    @ExperimentalUnsignedTypes
    private val MAGIC_NUMBERS =
        listOf(
            MagicNumber(
                0x0000,
                "SEGADISCSYSTEM".toByteArray(Charsets.US_ASCII),
                SystemID.SEGACD,
            ),
            MagicNumber(
                0x0010,
                ubyteArrayOf(
                    0x53U,
                    0x45U,
                    0x47U,
                    0x41U,
                    0x44U,
                    0x49U,
                    0x53U,
                    0x43U,
                    0x53U,
                    0x59U,
                    0x53U,
                    0x54U,
                    0x45U,
                    0x4dU,
                ).toByteArray(),
                SystemID.SEGACD,
            ),
            MagicNumber(
                0x8008,
                ubyteArrayOf(
                    0x50U,
                    0x4cU,
                    0x41U,
                    0x59U,
                    0x53U,
                    0x54U,
                    0x41U,
                    0x54U,
                    0x49U,
                    0x4fU,
                    0x4eU,
                ).toByteArray(),
                SystemID.PSX,
            ),
            MagicNumber(
                0x9320,
                ubyteArrayOf(
                    0x50U,
                    0x4cU,
                    0x41U,
                    0x59U,
                    0x53U,
                    0x54U,
                    0x41U,
                    0x54U,
                    0x49U,
                    0x4fU,
                    0x4eU,
                ).toByteArray(),
                SystemID.PSX,
            ),
            MagicNumber(
                0x8008,
                ubyteArrayOf(0x50U, 0x53U, 0x50U, 0x20U, 0x47U, 0x41U, 0x4dU, 0x45U).toByteArray(),
                SystemID.PSP,
            ),
            MagicNumber(
                0x0000,
                "SEGA SEGAKATANA".toByteArray(Charsets.US_ASCII),
                SystemID.DREAMCAST,
            ),
            MagicNumber(
                0x0010,
                "SEGA SEGAKATANA".toByteArray(Charsets.US_ASCII),
                SystemID.DREAMCAST,
            ),
            MagicNumber(
                0x0000,
                "SEGA SEGASATURN".toByteArray(Charsets.US_ASCII),
                SystemID.SATURN,
            ),
            MagicNumber(
                0x0010,
                "SEGA SEGASATURN".toByteArray(Charsets.US_ASCII),
                SystemID.SATURN,
            ),
            MagicNumber(
                0x0000,
                ubyteArrayOf(0x01U, 0x5aU, 0x5aU, 0x5aU, 0x5aU, 0x5aU, 0x01U).toByteArray(),
                SystemID.THREEDO,
            ),
            MagicNumber(
                0x0010,
                ubyteArrayOf(0x01U, 0x5aU, 0x5aU, 0x5aU, 0x5aU, 0x5aU, 0x01U).toByteArray(),
                SystemID.THREEDO,
            ),
            MagicNumber(
                0x0100,
                "SEGA 32X".toByteArray(Charsets.US_ASCII),
                SystemID.SEGA32X,
            ),
            MagicNumber(
                0x0100,
                "SEGA MEGA DRIVE".toByteArray(Charsets.US_ASCII),
                SystemID.GENESIS,
            ),
            MagicNumber(
                0x0100,
                "SEGA GENESIS".toByteArray(Charsets.US_ASCII),
                SystemID.GENESIS,
            ),
            MagicNumber(
                0x0001,
                "ATARI7800".toByteArray(Charsets.US_ASCII),
                SystemID.ATARI7800,
            ),
            MagicNumber(
                0x0000,
                "LYNX".toByteArray(Charsets.US_ASCII),
                SystemID.LYNX,
            ),
        )

    private val SEGA_CD_REGEX = Regex("([A-Z]+)?-?([0-9]+) ?-?([0-9]*)")

    private val PS_SERIAL_REGEX = Regex("^([A-Z]+)-?([0-9]+)")
    private val PS_SERIAL_REGEX2 = Regex("^([A-Z]+)_?([0-9]{3})\\.([0-9]{2})")

    private const val PS_SERIAL_MAX_SIZE = 12

    private val PSX_BASE_SERIALS =
        listOf(
            "CPCS",
            "SCES",
            "SIPS",
            "SLKA",
            "SLPS",
            "SLUS",
            "ESPM",
            "SLED",
            "SCPS",
            "SCAJ",
            "PAPX",
            "SLES",
            "HPS",
            "LSP",
            "SLPM",
            "SCUS",
            "SCED",
        )

    private val PSP_BASE_SERIALS =
        listOf(
            "ULES",
            "ULUS",
            "ULJS",
            "ULEM",
            "ULUM",
            "ULJM",
            "ULKS",
            "ULAS",
            "UCES",
            "UCUS",
            "UCJS",
            "UCAS",
            "NPEH",
            "NPUH",
            "NPJH",
            "NPEG",
            "NPEX",
            "NPUG",
            "NPJG",
            "NPJJ",
            "NPHG",
            "NPEZ",
            "NPUZ",
            "NPJZ",
            "NPUF",
            "NPUZ",
            "NPUG",
            "NPUX",
        )

    fun extractInfo(
        fileName: String,
        inputStream: InputStream,
        fileSize: Long = -1L,
        closeStream: Boolean = true,
    ): DiskInfo {
        Timber.d("Extracting disk info for $fileName")
        val baseStream =
            if (closeStream) {
                inputStream
            } else {
                object : FilterInputStream(inputStream) {
                    override fun close() = Unit
                }
            }
        baseStream.buffered(READ_BUFFER_SIZE).use {
            return when (FileUtils.extractExtension(fileName)) {
                "pbp" -> extractInfoForPBP(it)
                "iso", "bin", "img", "mdf" -> standardExtractInfo(it)
                "chd" -> extractInfoForChdStream(it)
                "cso" -> extractInfoForCSO(it)
                "dsk" -> extractInfoForDSK(it, fileSize)
                "3ds", "cci" -> extractInfoFor3DS(it)
                else -> DiskInfo(null, null)
            }
        }
    }

    fun extractInfoForChd(source: ChdRandomAccess): DiskInfo {
        val candidates =
            runCatching {
                ChdReader.extractCandidateDataTrackHeaders(source, READ_BUFFER_SIZE)
            }.getOrDefault(emptyList())

        for (headerBytes in candidates) {
            val info =
                ByteArrayInputStream(headerBytes).buffered(READ_BUFFER_SIZE).use { stream ->
                    standardExtractInfo(stream)
                }
            if (info.systemID != null || info.serial != null) {
                return info
            }
        }
        return DiskInfo(null, null)
    }

    private fun extractInfoForChdStream(openedStream: InputStream): DiskInfo {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16.kiloBytes())
        var total = 0
        while (true) {
            val read = openedStream.read(buf)
            if (read <= 0) break
            total += read
            if (total > MAX_STREAM_CHD_BYTES) {
                return DiskInfo(null, null)
            }
            out.write(buf, 0, read)
        }
        if (total == 0) return DiskInfo(null, null)
        return ByteArrayChdAccess(out.toByteArray()).use { access ->
            extractInfoForChd(access)
        }
    }

    private fun standardExtractInfo(openedStream: InputStream): DiskInfo {
        openedStream.mark(READ_BUFFER_SIZE)
        val header = readByteArray(openedStream, ByteArray(READ_BUFFER_SIZE))

        val matchedMagic =
            MAGIC_NUMBERS
                .firstOrNull {
                    header.size >= it.offset + it.numbers.size &&
                        header.copyOfRange(it.offset, it.offset + it.numbers.size).contentEquals(it.numbers)
                }
        val detectedSystem =
            matchedMagic?.systemID
                ?: if (header.indexOf(PCE_CD_SIGNATURE) >= 0) SystemID.PC_ENGINE else null

        Timber.d("SystemID detected via magic numbers: $detectedSystem")

        openedStream.reset()

        return when (detectedSystem) {
            SystemID.SEGACD -> {
                val sectorHeaderBias = if (matchedMagic?.offset == 0x0000) 0L else 0x10L
                runCatching { extractInfoForSegaCD(openedStream, sectorHeaderBias) }
                    .getOrDefault(DiskInfo(null, SystemID.SEGACD))
            }

            SystemID.PSX ->
                runCatching { extractInfoForPSX(openedStream) }
                    .getOrDefault(DiskInfo(null, SystemID.PSX))

            SystemID.PSP ->
                runCatching { extractInfoForPSP(openedStream) }
                    .getOrDefault(DiskInfo(null, SystemID.PSP))

            SystemID.DREAMCAST,
            SystemID.SATURN,
            SystemID.THREEDO,
            SystemID.SEGA32X,
            SystemID.GENESIS,
            SystemID.ATARI7800,
            SystemID.LYNX,
            SystemID.PC_ENGINE,
            -> DiskInfo(null, detectedSystem)

            else -> DiskInfo(null, null)
        }
    }

    private fun extractInfoForCSO(openedStream: InputStream): DiskInfo {
        val magic = readByteArray(openedStream, ByteArray(4))
        return if (magic.contentEquals(CSO_SIGNATURE)) {
            DiskInfo(null, SystemID.PSP)
        } else {
            DiskInfo(null, null)
        }
    }

    private fun extractInfoForDSK(
        openedStream: InputStream,
        fileSize: Long,
    ): DiskInfo {
        val header = readByteArray(openedStream, ByteArray(1024))
        if (header.size < 16) {
            return DiskInfo(null, null)
        }
        if (startsWithBytes(header, CPC_MV_SIGNATURE) || startsWithBytes(header, CPC_EXT_SIGNATURE)) {
            return DiskInfo(null, null)
        }

        val effectiveSize =
            if (fileSize > 0L) {
                fileSize
            } else {
                var total = header.size.toLong()
                val scratch = ByteArray(8.kiloBytes())
                while (total <= MAX_DSK_PROBE_BYTES) {
                    val read = openedStream.read(scratch)
                    if (read <= 0) break
                    total += read
                }
                total
            }

        val isMsxGeometry = effectiveSize == 368_640L || effectiveSize == 737_280L
        val hasFat12SectorSize =
            header.size >= 0x16 &&
                header[0x0B] == 0x00.toByte() &&
                header[0x0C] == 0x02.toByte() &&
                (header[0x15].toInt() and 0xFF) in 0xF8..0xFF
        if (isMsxGeometry && hasFat12SectorSize) {
            return DiskInfo(null, SystemID.MSX)
        }

        val isApple2Floppy =
            effectiveSize == 143_360L ||
                effectiveSize == 143_488L ||
                effectiveSize == 116_480L ||
                (effectiveSize == 819_200L && header.indexOf(PRODOS_SIGNATURE) >= 0)
        if (isApple2Floppy) {
            return DiskInfo(null, SystemID.APPLEII)
        }

        return DiskInfo(null, null)
    }

    private fun startsWithBytes(
        data: ByteArray,
        prefix: ByteArray,
    ): Boolean {
        return data.size >= prefix.size && data.copyOfRange(0, prefix.size).contentEquals(prefix)
    }

    private fun extractInfoFor3DS(openedStream: InputStream): DiskInfo {
        Timber.d("Parsing 3DS game")
        openedStream.mark(0x2000)
        val skipped = skipFully(openedStream, 0x1150)

        val rawSerial =
            if (skipped == 0x1150L) {
                String(readByteArray(openedStream, ByteArray(10)), Charsets.US_ASCII)
                    .trim { it <= ' ' || it == '\u0000' }
                    .takeIf { it.isNotEmpty() }
            } else {
                null
            }

        openedStream.reset()

        Timber.d("Found 3DS serial: $rawSerial")
        return DiskInfo(rawSerial, SystemID.NINTENDO_3DS)
    }

    private fun extractInfoForSegaCD(
        openedStream: InputStream,
        sectorHeaderBias: Long = 0x10L,
    ): DiskInfo {
        Timber.d("Parsing SegaCD game")
        openedStream.mark(20000)
        skipFully(openedStream, 0x183L + sectorHeaderBias)

        val rawSerial = String(readByteArray(openedStream, ByteArray(16)), Charsets.US_ASCII)

        Timber.d("Detected SegaCD raw serial read: $rawSerial")

        openedStream.reset()
        skipFully(openedStream, 0x1F0L + sectorHeaderBias)

        val regionID = String(readByteArray(openedStream, ByteArray(1)), Charsets.US_ASCII)

        Timber.d("Detected SegaCD region: $regionID")

        val groups = SEGA_CD_REGEX.find(rawSerial)?.groupValues

        // The following rules come from here: https://github.com/libretro/RetroArch/pull/11719/files
        // and some guess work. They are by no means complete.
        val prefix = groups?.get(1)
        val num = groups?.get(2)
        var postfix = groups?.get(3)

        if (regionID == "E") {
            postfix = "50"
        }

        if (postfix == "00") {
            postfix = null
        }

        val finalSerial =
            sequenceOf(prefix, num, postfix)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString("-") { it.trim() }
                .takeIf { it.isNotBlank() }

        Timber.i("SegaCD final serial: $finalSerial")
        return DiskInfo(finalSerial, SystemID.SEGACD)
    }

    private fun extractInfoForPSX(openedStream: InputStream): DiskInfo {
        val headerSize = 64.kiloBytes()
        return textSearch(PSX_BASE_SERIALS, openedStream, PS_SERIAL_MAX_SIZE, headerSize)
            .mapNotNull { serial -> parsePSXSerial(serial) }
            .map { serial -> DiskInfo(serial, SystemID.PSX) }
            .firstOrNull() ?: DiskInfo(null, SystemID.PSX)
    }

    private fun extractInfoForPSP(openedStream: InputStream): DiskInfo {
        val headerSize = 64.kiloBytes()
        return textSearch(PSP_BASE_SERIALS, openedStream, PS_SERIAL_MAX_SIZE, headerSize)
            .mapNotNull { serial -> parsePSXSerial(serial) }
            .map { serial -> DiskInfo(serial, SystemID.PSP) }
            .firstOrNull() ?: DiskInfo(null, SystemID.PSP)
    }

    private fun extractInfoForPBP(openedStream: InputStream): DiskInfo {
        val headerSize = 2.megaBytes()
        val queries = (PSP_BASE_SERIALS + PSX_BASE_SERIALS)

        return textSearch(queries, openedStream, PS_SERIAL_MAX_SIZE, headerSize)
            .mapNotNull { serial -> parsePSXSerial(serial) }
            .map { serial ->
                when {
                    serial.startsWithAny(PSX_BASE_SERIALS) -> DiskInfo(serial, SystemID.PSX)
                    serial.startsWithAny(PSP_BASE_SERIALS) -> DiskInfo(serial, SystemID.PSP)
                    else -> DiskInfo(serial, null)
                }
            }
            .firstOrNull() ?: DiskInfo(null, null)
    }

    private fun parsePSXSerial(serial: String): String? {
        return sequenceOf(
            PS_SERIAL_REGEX.find(serial)?.groupValues?.let { "${it[1]}-${it[2]}" },
            PS_SERIAL_REGEX2.find(serial)?.groupValues?.let { "${it[1]}-${it[2]}${it[3]}" },
        ).filter { it != null }
            .firstOrNull()
    }

    private fun textSearch(
        queries: List<String>,
        openedStream: InputStream,
        resultSize: Int,
        streamSize: Int,
        windowSize: Int = 8.kiloBytes(),
        skipSize: Int = windowSize - resultSize,
        charset: Charset = Charsets.US_ASCII,
    ): Sequence<String> {
        val byteQueries = queries.map { it.toByteArray(charset) }
        return movingWidnowSequence(openedStream, windowSize, (skipSize).toLong())
            .take(ceil(streamSize.toDouble() / skipSize.toDouble()).roundToInt())
            .flatMap { serial ->
                byteQueries.asSequence()
                    .map { serial.indexOf(it) }
                    .filter { it >= 0 }
                    .map { serial to it }
            }
            .map { (bytes, index) ->
                val endIndex = min(index + resultSize, bytes.size)
                val serialBytes = bytes.copyOfRange(index, endIndex)
                String(serialBytes, charset)
            }
            .filterNotNull()
    }

    private fun movingWidnowSequence(
        inputStream: InputStream,
        windowSize: Int,
        windowSkip: Long,
    ) = sequence {
        while (true) {
            inputStream.mark(windowSize)
            val chunk = readByteArray(inputStream, ByteArray(windowSize))
            if (chunk.isEmpty()) break
            yield(chunk)
            if (chunk.size < windowSize) break
            inputStream.reset()
            if (skipFully(inputStream, windowSkip) <= 0L) break
        }
    }

    private fun skipFully(
        inputStream: InputStream,
        bytesToSkip: Long,
    ): Long {
        var remaining = bytesToSkip
        val scratch = ByteArray(4096)
        while (remaining > 0L) {
            val skipped = inputStream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            val read = inputStream.read(scratch, 0, min(remaining, scratch.size.toLong()).toInt())
            if (read <= 0) break
            remaining -= read
        }
        return bytesToSkip - remaining
    }

    private fun readByteArray(
        inputStream: InputStream,
        byteArray: ByteArray,
    ): ByteArray {
        var totalRead = 0
        while (totalRead < byteArray.size) {
            val read = inputStream.read(byteArray, totalRead, byteArray.size - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        return if (totalRead < byteArray.size) {
            byteArray.copyOf(totalRead)
        } else {
            byteArray
        }
    }
}
