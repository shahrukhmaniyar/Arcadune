package com.swordfish.lemuroid.common.files

import android.os.ParcelFileDescriptor
import android.system.Os
import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.zip.Inflater

interface ChdRandomAccess : Closeable {
    val size: Long

    fun readAt(
        offset: Long,
        dest: ByteArray,
        destOffset: Int = 0,
        length: Int = dest.size - destOffset,
    ): Int
}

class ParcelFdChdAccess(
    private val pfd: ParcelFileDescriptor,
    private val closeDescriptor: Boolean = false,
) : ChdRandomAccess {
    override val size: Long =
        runCatching { Os.fstat(pfd.fileDescriptor).st_size }
            .getOrElse { pfd.statSize }

    override fun readAt(
        offset: Long,
        dest: ByteArray,
        destOffset: Int,
        length: Int,
    ): Int {
        if (length <= 0 || offset < 0L) return 0
        var total = 0
        while (total < length) {
            val read =
                runCatching {
                    Os.pread(
                        pfd.fileDescriptor,
                        dest,
                        destOffset + total,
                        length - total,
                        offset + total,
                    )
                }.getOrElse { return if (total > 0) total else -1 }
            if (read <= 0) break
            total += read
        }
        return total
    }

    override fun close() {
        if (closeDescriptor) {
            runCatching { pfd.close() }
        }
    }
}

class FileChdAccess(file: File) : ChdRandomAccess {
    private val raf = RandomAccessFile(file, "r")
    override val size: Long = raf.length()

    @Synchronized
    override fun readAt(
        offset: Long,
        dest: ByteArray,
        destOffset: Int,
        length: Int,
    ): Int {
        if (length <= 0 || offset < 0L || offset >= size) return 0
        raf.seek(offset)
        var total = 0
        while (total < length) {
            val read = raf.read(dest, destOffset + total, length - total)
            if (read <= 0) break
            total += read
        }
        return total
    }

    override fun close() {
        runCatching { raf.close() }
    }
}

class ByteArrayChdAccess(private val data: ByteArray) : ChdRandomAccess {
    override val size: Long = data.size.toLong()

    override fun readAt(
        offset: Long,
        dest: ByteArray,
        destOffset: Int,
        length: Int,
    ): Int {
        if (length <= 0 || offset < 0L || offset >= data.size) return 0
        val available = minOf(length, (data.size - offset).toInt())
        System.arraycopy(data, offset.toInt(), dest, destOffset, available)
        return available
    }

    override fun close() = Unit
}

/**
 * Pure-Kotlin reader for MAME CHD v3/v4/v5 disc images (CD-ROM, GD-ROM, DVD/raw).
 * Supports `cdlz` (CD LZMA), `cdzl` (CD Deflate), `zlib`, `lzma`, uncompressed, self-referenced,
 * and mini-hunk blocks for both fast ROM header scanning and RetroAchievements sector hashing.
 */
object ChdReader {
    const val TRACK_FIRST_DATA = -1
    const val TRACK_LAST = -2
    const val TRACK_LARGEST = -3
    const val TRACK_SECOND_SESSION = -4

    private const val CHD_V3_HEADER_SIZE = 120
    private const val CHD_V4_HEADER_SIZE = 108
    private const val CHD_V5_HEADER_SIZE = 124
    private const val MAX_HUNK_BYTES = 1024 * 1024
    private const val MAX_TOTAL_HUNKS = 4_000_000
    private const val MAX_MAP_BYTES = 16 * 1024 * 1024
    private const val MAX_METADATA_NODES = 256
    private const val MAX_SELF_RECURSION = 8

    private const val CD_MAX_SECTOR_DATA = 2352
    private const val CD_MAX_SUBCODE_DATA = 96
    private const val CD_FRAME_SIZE = CD_MAX_SECTOR_DATA + CD_MAX_SUBCODE_DATA
    private const val COOKED_SECTOR_BYTES = 2048
    private const val TRACK_PAD = 4
    private const val LZMA_PROPS_BYTE: Byte = 0x5D

    private val MAGIC_TAG = "MComprHD".toByteArray(Charsets.US_ASCII)
    private val CD_SYNC_HEADER =
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
        )

    private const val TAG_ZLIB = 0x7A6C6962 // 'zlib'
    private const val TAG_LZMA = 0x6C7A6D61 // 'lzma'
    private const val TAG_CDZL = 0x63647A6C // 'cdzl'
    private const val TAG_CDLZ = 0x63646C7A // 'cdlz'
    private const val TAG_CDFL = 0x6364666C // 'cdfl'

    private const val TAG_CHTR = 0x43485452 // 'CHTR'
    private const val TAG_CHT2 = 0x43485432 // 'CHT2'
    private const val TAG_CHGD = 0x43484744 // 'CHGD'
    private const val TAG_CHCD = 0x43484344 // 'CHCD'
    private const val TAG_CHGT = 0x43484754 // 'CHGT'

    private const val V5_COMP_TYPE_0 = 0
    private const val V5_COMP_TYPE_1 = 1
    private const val V5_COMP_TYPE_2 = 2
    private const val V5_COMP_TYPE_3 = 3
    private const val V5_COMP_NONE = 4
    private const val V5_COMP_SELF = 5
    private const val V5_COMP_PARENT = 6
    private const val V5_COMP_RLE_SMALL = 7
    private const val V5_COMP_RLE_LARGE = 8
    private const val V5_COMP_SELF_0 = 9
    private const val V5_COMP_SELF_1 = 10
    private const val V5_COMP_PARENT_SELF = 11
    private const val V5_COMP_PARENT_0 = 12
    private const val V5_COMP_PARENT_1 = 13

    private const val V34_MAP_COMPRESSED = 1
    private const val V34_MAP_UNCOMPRESSED = 2
    private const val V34_MAP_MINI = 3
    private const val V34_MAP_SELF_HUNK = 4
    private const val V34_MAP_2ND_COMPRESSED = 6

    data class TrackMetadata(
        val trackNumber: Int,
        val type: String,
        val frames: Int,
        val pregap: Int,
        val pgType: String,
        val extraFrames: Int,
        val chdFrameOffset: Int,
        val firstTrackSector: Int,
        val isGdRom: Boolean,
    ) {
        val isAudio: Boolean
            get() = type.equals("AUDIO", ignoreCase = true)

        fun rawSectorSize(defaultUnitBytes: Int): Int =
            when (type.uppercase(Locale.US)) {
                "MODE1_RAW", "MODE2_RAW", "AUDIO" -> CD_MAX_SECTOR_DATA
                "MODE1", "MODE2_FORM1" -> COOKED_SECTOR_BYTES
                "MODE2", "MODE2_FORM_MIX" -> 2336
                "MODE2_FORM2" -> 2324
                else -> minOf(defaultUnitBytes, CD_MAX_SECTOR_DATA)
            }
    }

    internal class ParsedChdHeader(
        val version: Int,
        val headerLength: Int,
        val compressors: IntArray,
        val logicalBytes: Long,
        val mapOffset: Long,
        val metaOffset: Long,
        val hunkBytes: Int,
        val unitBytes: Int,
        val totalHunks: Int,
    )

    internal class HunkMap(
        val compTypes: ByteArray,
        val lengths: IntArray,
        val offsets: LongArray,
    ) {
        val decodedCount: Int
            get() = lengths.size
    }

    class TrackHandle internal constructor(
        private val source: ChdRandomAccess,
        private val ownSource: Boolean,
        private val header: ParsedChdHeader,
        private val map: HunkMap,
        val track: TrackMetadata,
    ) : Closeable {
        private val framesPerHunk = (header.hunkBytes / header.unitBytes).coerceAtLeast(1)
        private val rawSectorBytes = track.rawSectorSize(header.unitBytes)
        private val inFilePregapFrames =
            if (track.pgType.startsWith("V", ignoreCase = true)) 0 else track.pregap.coerceAtLeast(0)

        private var cachedHunkIndex = -1
        private val cachedHunkData = ByteArray(header.hunkBytes)
        private val lzmaScratch = ByteArray(header.hunkBytes)

        val firstTrackSector: Int
            get() = track.firstTrackSector

        /**
         * Reads a contiguous stream of raw track sector bytes starting at [trackSectorOffset] frames
         * into the track. Used by `SerialScanner` to inspect magic numbers and game serials.
         */
        @Synchronized
        fun readRawTrackBytes(
            maxBytes: Int,
            skipStoredPregap: Boolean = true,
        ): ByteArray? {
            if (maxBytes <= 0 || track.frames <= 0) return null
            val startFrameInTrack = if (skipStoredPregap && inFilePregapFrames < track.frames) inFilePregapFrames else 0
            val availableFrames = (track.frames - startFrameInTrack).coerceAtLeast(0)
            val totalAvailableBytes = availableFrames.toLong() * rawSectorBytes.toLong()
            val targetBytes = minOf(maxBytes.toLong(), totalAvailableBytes).toInt()
            if (targetBytes <= 0) return null

            val out = ByteArray(targetBytes)
            var written = 0
            var frameIdx = startFrameInTrack
            while (written < targetBytes && frameIdx < track.frames) {
                val chdFrame = track.chdFrameOffset + frameIdx
                val hunk = chdFrame / framesPerHunk
                val unitOffset = (chdFrame % framesPerHunk) * header.unitBytes
                if (!ensureHunkLoaded(hunk)) {
                    return if (written > 0) out.copyOf(written) else null
                }
                val toCopy = minOf(rawSectorBytes, targetBytes - written, header.hunkBytes - unitOffset)
                if (toCopy <= 0) break
                System.arraycopy(cachedHunkData, unitOffset, out, written, toCopy)
                written += toCopy
                frameIdx++
            }
            return out
        }

        /**
         * Reads cooked 2048-byte user data starting at absolute LBA [sector], matching `rcheevos`'s
         * `cdreader_read_sector` contract across sector boundaries.
         */
        @Synchronized
        fun readCookedSectors(
            sector: Int,
            requestedBytes: Int,
        ): ByteArray? {
            if (requestedBytes <= 0 || sector < track.firstTrackSector) return null
            var relSector = sector - track.firstTrackSector + inFilePregapFrames
            if (relSector < 0 || relSector >= track.frames) return null

            val out = ByteArray(requestedBytes)
            var written = 0
            while (written < requestedBytes && relSector < track.frames) {
                val chdFrame = track.chdFrameOffset + relSector
                val hunk = chdFrame / framesPerHunk
                val unitOffset = (chdFrame % framesPerHunk) * header.unitBytes
                if (!ensureHunkLoaded(hunk)) {
                    break
                }
                val headerSkip = computeCookedHeaderOffset(unitOffset)
                val availableInUnit =
                    minOf(
                        COOKED_SECTOR_BYTES,
                        (header.unitBytes - headerSkip).coerceAtLeast(0),
                    )
                val toCopy = minOf(availableInUnit, requestedBytes - written)
                if (toCopy <= 0) break
                System.arraycopy(cachedHunkData, unitOffset + headerSkip, out, written, toCopy)
                written += toCopy
                relSector++
            }
            return when {
                written == 0 -> null
                written == out.size -> out
                else -> out.copyOf(written)
            }
        }

        private fun computeCookedHeaderOffset(unitOffset: Int): Int {
            return when (track.type.uppercase(Locale.US)) {
                "MODE1", "MODE2_FORM1", "RAW" -> 0
                "MODE2", "MODE2_FORM_MIX", "MODE2_FORM2" -> 8
                "MODE1_RAW" -> 16
                "MODE2_RAW" -> {
                    val modeByte =
                        if (unitOffset + 15 < cachedHunkData.size) {
                            cachedHunkData[unitOffset + 15].toInt() and 0xFF
                        } else {
                            2
                        }
                    if (modeByte == 1) 16 else 24
                }
                else -> {
                    if (unitOffset + 12 <= cachedHunkData.size && matchesSyncHeader(cachedHunkData, unitOffset)) {
                        val modeByte = cachedHunkData[unitOffset + 15].toInt() and 0xFF
                        if (modeByte == 2) 24 else 16
                    } else {
                        0
                    }
                }
            }
        }

        private fun ensureHunkLoaded(hunkIndex: Int): Boolean {
            if (hunkIndex == cachedHunkIndex) return true
            if (!readHunkInto(hunkIndex, cachedHunkData, 0)) return false
            cachedHunkIndex = hunkIndex
            return true
        }

        private fun readHunkInto(
            hunkIndex: Int,
            dest: ByteArray,
            depth: Int,
        ): Boolean {
            if (depth > MAX_SELF_RECURSION) return false
            if (hunkIndex < 0 || hunkIndex >= map.decodedCount) return false

            val type = map.compTypes[hunkIndex].toInt() and 0xFF
            val blockLen = map.lengths[hunkIndex]
            val blockOffs = map.offsets[hunkIndex]

            if (header.version < 5) {
                return when (type) {
                    V34_MAP_UNCOMPRESSED ->
                        source.readAt(blockOffs, dest, 0, header.hunkBytes) == header.hunkBytes
                    V34_MAP_COMPRESSED, V34_MAP_2ND_COMPRESSED -> {
                        if (blockLen <= 0 || blockLen > header.hunkBytes * 2) return false
                        val comp = ByteArray(blockLen)
                        if (source.readAt(blockOffs, comp, 0, blockLen) != blockLen) return false
                        decompressZlibRaw(comp, 0, blockLen, dest, 0, header.hunkBytes)
                    }
                    V34_MAP_MINI -> {
                        for (b in 0 until minOf(8, header.hunkBytes)) {
                            dest[b] = ((blockOffs ushr ((7 - b) * 8)) and 0xFFL).toByte()
                        }
                        for (b in 8 until header.hunkBytes) {
                            dest[b] = dest[b - 8]
                        }
                        true
                    }
                    V34_MAP_SELF_HUNK -> readHunkInto(blockOffs.toInt(), dest, depth + 1)
                    else -> false
                }
            }

            return when (type) {
                V5_COMP_TYPE_0, V5_COMP_TYPE_1, V5_COMP_TYPE_2, V5_COMP_TYPE_3 -> {
                    val codecTag = header.compressors[type]
                    if (blockLen <= 0 || blockLen > header.hunkBytes * 2) return false
                    val comp = ByteArray(blockLen)
                    if (source.readAt(blockOffs, comp, 0, blockLen) != blockLen) return false
                    decompressV5Codec(codecTag, comp, blockLen, dest)
                }
                V5_COMP_NONE -> {
                    if (blockOffs == 0L && header.compressors[0] == 0) {
                        dest.fill(0)
                        true
                    } else {
                        source.readAt(blockOffs, dest, 0, header.hunkBytes) == header.hunkBytes
                    }
                }
                V5_COMP_SELF -> readHunkInto(blockOffs.toInt(), dest, depth + 1)
                else -> false
            }
        }

        private fun decompressV5Codec(
            codecTag: Int,
            src: ByteArray,
            compLen: Int,
            dest: ByteArray,
        ): Boolean {
            return when (codecTag) {
                TAG_CDLZ -> decompressCdWrapped(src, compLen, dest, useLzma = true)
                TAG_CDZL -> decompressCdWrapped(src, compLen, dest, useLzma = false)
                TAG_ZLIB -> decompressZlibRaw(src, 0, compLen, dest, 0, header.hunkBytes)
                TAG_LZMA -> decompressLzmaRaw(src, 0, compLen, dest, header.hunkBytes)
                TAG_CDFL -> false
                else -> false
            }
        }

        private fun decompressCdWrapped(
            src: ByteArray,
            compLen: Int,
            dest: ByteArray,
            useLzma: Boolean,
        ): Boolean {
            if (header.hunkBytes % CD_FRAME_SIZE != 0) return false
            val frames = header.hunkBytes / CD_FRAME_SIZE
            val compLenBytes = if (header.hunkBytes < 65536) 2 else 3
            val eccBytes = (frames + 7) / 8
            val headerBytes = eccBytes + compLenBytes
            if (compLen <= headerBytes) return false

            var compLenBase =
                ((src[eccBytes].toInt() and 0xFF) shl 8) or
                    (src[eccBytes + 1].toInt() and 0xFF)
            if (compLenBytes > 2) {
                compLenBase = (compLenBase shl 8) or (src[eccBytes + 2].toInt() and 0xFF)
            }
            if (compLenBase <= 0 || headerBytes + compLenBase > compLen) return false

            val sectorTotalBytes = frames * CD_MAX_SECTOR_DATA
            val ok =
                if (useLzma) {
                    decompressLzmaRaw(src, headerBytes, compLenBase, lzmaScratch, sectorTotalBytes)
                } else {
                    decompressZlibRaw(src, headerBytes, compLenBase, lzmaScratch, 0, sectorTotalBytes)
                }
            if (!ok) return false

            dest.fill(0)
            for (frameNum in 0 until frames) {
                val srcPos = frameNum * CD_MAX_SECTOR_DATA
                val dstPos = frameNum * CD_FRAME_SIZE
                System.arraycopy(lzmaScratch, srcPos, dest, dstPos, CD_MAX_SECTOR_DATA)
                val hasSync = (src[frameNum / 8].toInt() and (1 shl (frameNum % 8))) != 0
                if (hasSync) {
                    System.arraycopy(CD_SYNC_HEADER, 0, dest, dstPos, CD_SYNC_HEADER.size)
                }
            }
            return true
        }

        override fun close() {
            if (ownSource) {
                source.close()
            }
        }
    }

    /**
     * Fast scanner entry point: extracts up to [maxBytes] from the first data track (and if
     * [includeLargestDataFallback] is true and a distinct larger data track exists — such as
     * Dreamcast GD-ROM Track 3 — yields it as a second candidate).
     */
    fun extractCandidateDataTrackHeaders(
        source: ChdRandomAccess,
        maxBytes: Int = 64 * 1024,
    ): List<ByteArray> {
        val header = parseHeader(source) ?: return emptyList()
        val tracks = parseTracks(source, header)
        if (tracks.isEmpty()) return emptyList()

        val firstData = selectTrack(tracks, TRACK_FIRST_DATA) ?: return emptyList()
        val largestData =
            tracks.filter { !it.isAudio }
                .maxByOrNull { it.frames }
                ?.takeIf { it.trackNumber != firstData.trackNumber }

        val candidateTracks = listOfNotNull(firstData, largestData)
        val framesPerHunk = (header.hunkBytes / header.unitBytes).coerceAtLeast(1)
        val maxNeededHunk =
            candidateTracks.maxOf { track ->
                val rawSectorSize = track.rawSectorSize(header.unitBytes).coerceAtLeast(1)
                val neededFrames = (maxBytes + rawSectorSize - 1) / rawSectorSize + track.pregap + 2
                val lastFrame = (track.chdFrameOffset + neededFrames).coerceAtMost(track.chdFrameOffset + track.frames)
                (lastFrame / framesPerHunk) + 1
            }.coerceAtMost(header.totalHunks)

        val map = decodeMap(source, header, maxNeededHunk) ?: return emptyList()

        val results = ArrayList<ByteArray>(candidateTracks.size)
        for (track in candidateTracks) {
            val handle = TrackHandle(source, ownSource = false, header = header, map = map, track = track)
            handle.readRawTrackBytes(maxBytes)?.let { results.add(it) }
        }
        return results
    }

    fun openTrack(
        source: ChdRandomAccess,
        trackSelector: Int = TRACK_FIRST_DATA,
        ownSource: Boolean = true,
    ): TrackHandle? {
        val header =
            parseHeader(source) ?: run {
                if (ownSource) source.close()
                return null
            }
        val tracks = parseTracks(source, header)
        val selected =
            selectTrack(tracks, trackSelector) ?: run {
                if (ownSource) source.close()
                return null
            }
        val map =
            decodeMap(source, header, header.totalHunks) ?: run {
                if (ownSource) source.close()
                return null
            }
        return TrackHandle(source, ownSource = ownSource, header = header, map = map, track = selected)
    }

    private fun selectTrack(
        tracks: List<TrackMetadata>,
        selector: Int,
    ): TrackMetadata? {
        if (tracks.isEmpty()) return null
        return when {
            selector > 0 -> tracks.firstOrNull { it.trackNumber == selector }
            selector == 0 || selector == TRACK_LARGEST ->
                tracks.filter { !it.isAudio }.maxByOrNull { it.frames }
                    ?: tracks.maxByOrNull { it.frames }
            selector == TRACK_FIRST_DATA -> tracks.firstOrNull { !it.isAudio }
            selector == TRACK_LAST -> tracks.lastOrNull()
            selector == TRACK_SECOND_SESSION ->
                tracks.firstOrNull { it.isGdRom && it.trackNumber == 3 && !it.isAudio }
                    ?: tracks.drop(1).firstOrNull { !it.isAudio }
            else -> null
        }
    }

    private fun parseHeader(source: ChdRandomAccess): ParsedChdHeader? {
        val raw = ByteArray(CHD_V5_HEADER_SIZE)
        val read = source.readAt(0L, raw, 0, raw.size)
        if (read < CHD_V4_HEADER_SIZE) return null
        for (i in MAGIC_TAG.indices) {
            if (raw[i] != MAGIC_TAG[i]) return null
        }

        val length = u32be(raw, 8)
        val version = u32be(raw, 12)
        if (read < length) return null

        return when (version) {
            3 -> {
                if (length != CHD_V3_HEADER_SIZE) return null
                val flags = u32be(raw, 16)
                if ((flags and 0x1) != 0) return null
                val comp = u32be(raw, 20)
                val totalHunks = u32be(raw, 24)
                val logicalBytes = u64be(raw, 28)
                val metaOffset = u64be(raw, 36)
                val hunkBytes = u32be(raw, 76)
                if (!validateBounds(hunkBytes, totalHunks)) return null
                val unitBytes = guessLegacyUnitBytes(source, metaOffset, hunkBytes)
                ParsedChdHeader(
                    version = 3,
                    headerLength = length,
                    compressors = intArrayOf(comp, 0, 0, 0),
                    logicalBytes = logicalBytes,
                    mapOffset = length.toLong(),
                    metaOffset = metaOffset,
                    hunkBytes = hunkBytes,
                    unitBytes = unitBytes,
                    totalHunks = totalHunks,
                )
            }
            4 -> {
                if (length != CHD_V4_HEADER_SIZE) return null
                val flags = u32be(raw, 16)
                if ((flags and 0x1) != 0) return null
                val comp = u32be(raw, 20)
                val totalHunks = u32be(raw, 24)
                val logicalBytes = u64be(raw, 28)
                val metaOffset = u64be(raw, 36)
                val hunkBytes = u32be(raw, 44)
                if (!validateBounds(hunkBytes, totalHunks)) return null
                val unitBytes = guessLegacyUnitBytes(source, metaOffset, hunkBytes)
                ParsedChdHeader(
                    version = 4,
                    headerLength = length,
                    compressors = intArrayOf(comp, 0, 0, 0),
                    logicalBytes = logicalBytes,
                    mapOffset = length.toLong(),
                    metaOffset = metaOffset,
                    hunkBytes = hunkBytes,
                    unitBytes = unitBytes,
                    totalHunks = totalHunks,
                )
            }
            5 -> {
                if (length != CHD_V5_HEADER_SIZE) return null
                for (i in 104 until 124) {
                    if (raw[i].toInt() != 0) return null
                }
                val compressors =
                    intArrayOf(
                        u32be(raw, 16),
                        u32be(raw, 20),
                        u32be(raw, 24),
                        u32be(raw, 28),
                    )
                val logicalBytes = u64be(raw, 32)
                val mapOffset = u64be(raw, 40)
                val metaOffset = u64be(raw, 48)
                val hunkBytes = u32be(raw, 56)
                val unitBytes = u32be(raw, 60)
                if (hunkBytes <= 0 || hunkBytes > MAX_HUNK_BYTES || unitBytes <= 0 || unitBytes > hunkBytes) {
                    return null
                }
                val totalHunksLong = (logicalBytes + hunkBytes - 1) / hunkBytes
                if (totalHunksLong <= 0L || totalHunksLong > MAX_TOTAL_HUNKS) return null
                ParsedChdHeader(
                    version = 5,
                    headerLength = length,
                    compressors = compressors,
                    logicalBytes = logicalBytes,
                    mapOffset = mapOffset,
                    metaOffset = metaOffset,
                    hunkBytes = hunkBytes,
                    unitBytes = unitBytes,
                    totalHunks = totalHunksLong.toInt(),
                )
            }
            else -> null
        }
    }

    private fun validateBounds(
        hunkBytes: Int,
        totalHunks: Int,
    ): Boolean = hunkBytes in 512..MAX_HUNK_BYTES && totalHunks in 1..MAX_TOTAL_HUNKS

    private fun guessLegacyUnitBytes(
        source: ChdRandomAccess,
        metaOffset: Long,
        hunkBytes: Int,
    ): Int {
        var offset = metaOffset
        val metaHdr = ByteArray(16)
        var steps = 0
        while (offset > 0L && steps++ < MAX_METADATA_NODES) {
            if (source.readAt(offset, metaHdr, 0, 16) != 16) break
            val tag = u32be(metaHdr, 0)
            if (tag == TAG_CHT2 || tag == TAG_CHTR || tag == TAG_CHGD || tag == TAG_CHCD || tag == TAG_CHGT) {
                return CD_FRAME_SIZE
            }
            offset = u64be(metaHdr, 8)
        }
        return hunkBytes
    }

    private fun parseTracks(
        source: ChdRandomAccess,
        header: ParsedChdHeader,
    ): List<TrackMetadata> {
        val tracks = mutableListOf<TrackMetadata>()
        var offset = header.metaOffset
        val metaHdr = ByteArray(16)
        var chdFrameCursor = 0
        var lbaSectorCursor = 0
        var nodes = 0

        while (offset > 0L && nodes++ < MAX_METADATA_NODES) {
            if (source.readAt(offset, metaHdr, 0, 16) != 16) break
            val tag = u32be(metaHdr, 0)
            val length = u32be(metaHdr, 4) and 0x00FFFFFF
            val next = u64be(metaHdr, 8)

            if (length in 1..4096 && (tag == TAG_CHT2 || tag == TAG_CHTR || tag == TAG_CHGD)) {
                val payload = ByteArray(length)
                if (source.readAt(offset + 16L, payload, 0, length) == length) {
                    val nullIdx = payload.indexOf(0.toByte()).let { if (it >= 0) it else payload.size }
                    val text = String(payload, 0, nullIdx, Charsets.US_ASCII)
                    parseTextTrackMetadata(
                        text = text,
                        isGdRom = (tag == TAG_CHGD),
                        chdFrameOffset = chdFrameCursor,
                        firstTrackSector = lbaSectorCursor,
                    )?.let { parsed ->
                        tracks.add(parsed)
                        chdFrameCursor += parsed.frames + parsed.extraFrames
                        lbaSectorCursor += parsed.frames
                    }
                }
            } else if (length in 4..4096 && (tag == TAG_CHCD || tag == TAG_CHGT) && tracks.isEmpty()) {
                val payload = ByteArray(length)
                if (source.readAt(offset + 16L, payload, 0, length) == length) {
                    val numTracks = u32be(payload, 0).coerceIn(0, 99)
                    for (t in 0 until numTracks) {
                        val base = 4 + t * 24
                        if (base + 24 > payload.size) break
                        val trackTypeCode = u32be(payload, base)
                        val frames = u32be(payload, base + 16)
                        if (frames <= 0) continue
                        val typeStr = legacyTrackTypeToString(trackTypeCode)
                        val extra = paddingFrames(frames)
                        tracks.add(
                            TrackMetadata(
                                trackNumber = t + 1,
                                type = typeStr,
                                frames = frames,
                                pregap = 0,
                                pgType = "",
                                extraFrames = extra,
                                chdFrameOffset = chdFrameCursor,
                                firstTrackSector = lbaSectorCursor,
                                isGdRom = (tag == TAG_CHGT),
                            ),
                        )
                        chdFrameCursor += frames + extra
                        lbaSectorCursor += frames
                    }
                }
            }

            offset = next
        }

        if (tracks.isEmpty()) {
            val totalFrames =
                (header.logicalBytes / header.unitBytes)
                    .coerceIn(1L, Int.MAX_VALUE.toLong())
                    .toInt()
            tracks.add(
                TrackMetadata(
                    trackNumber = 1,
                    type = "RAW",
                    frames = totalFrames,
                    pregap = 0,
                    pgType = "",
                    extraFrames = 0,
                    chdFrameOffset = 0,
                    firstTrackSector = 0,
                    isGdRom = false,
                ),
            )
        }
        return tracks
    }

    private fun parseTextTrackMetadata(
        text: String,
        isGdRom: Boolean,
        chdFrameOffset: Int,
        firstTrackSector: Int,
    ): TrackMetadata? {
        val fields = HashMap<String, String>(8)
        for (token in text.trim().split(Regex("\\s+"))) {
            val colon = token.indexOf(':')
            if (colon > 0 && colon + 1 < token.length) {
                fields[token.substring(0, colon).uppercase(Locale.US)] = token.substring(colon + 1)
            }
        }
        val trackNum = fields["TRACK"]?.toIntOrNull() ?: return null
        val type = fields["TYPE"] ?: return null
        val frames = fields["FRAMES"]?.toIntOrNull() ?: return null
        if (frames <= 0) return null
        val pregap = fields["PREGAP"]?.toIntOrNull() ?: 0
        val pgType = fields["PGTYPE"] ?: ""
        val extra = paddingFrames(frames)
        return TrackMetadata(
            trackNumber = trackNum,
            type = type,
            frames = frames,
            pregap = pregap,
            pgType = pgType,
            extraFrames = extra,
            chdFrameOffset = chdFrameOffset,
            firstTrackSector = firstTrackSector,
            isGdRom = isGdRom,
        )
    }

    private fun legacyTrackTypeToString(code: Int): String =
        when (code) {
            0 -> "MODE1"
            1 -> "MODE1_RAW"
            2 -> "MODE2"
            3 -> "MODE2_FORM1"
            4 -> "MODE2_FORM2"
            5 -> "MODE2_FORM_MIX"
            6 -> "MODE2_RAW"
            7 -> "AUDIO"
            else -> "MODE1_RAW"
        }

    private fun paddingFrames(frames: Int): Int = ((frames + TRACK_PAD - 1) and (TRACK_PAD - 1).inv()) - frames

    private fun decodeMap(
        source: ChdRandomAccess,
        header: ParsedChdHeader,
        limitHunks: Int,
    ): HunkMap? {
        val targetCount = limitHunks.coerceIn(1, header.totalHunks)
        return if (header.version < 5) {
            decodeLegacyMap(source, header, targetCount)
        } else {
            decodeV5Map(source, header, targetCount)
        }
    }

    private fun decodeLegacyMap(
        source: ChdRandomAccess,
        header: ParsedChdHeader,
        targetCount: Int,
    ): HunkMap? {
        val rawBytes = ByteArray(targetCount * 16)
        if (source.readAt(header.mapOffset, rawBytes, 0, rawBytes.size) != rawBytes.size) {
            return null
        }
        val compTypes = ByteArray(targetCount)
        val lengths = IntArray(targetCount)
        val offsets = LongArray(targetCount)

        for (i in 0 until targetCount) {
            val base = i * 16
            offsets[i] = u64be(rawBytes, base)
            lengths[i] = u16be(rawBytes, base + 12) or ((rawBytes[base + 14].toInt() and 0xFF) shl 16)
            compTypes[i] = (rawBytes[base + 15].toInt() and 0x0F).toByte()
        }
        return HunkMap(compTypes, lengths, offsets)
    }

    private fun decodeV5Map(
        source: ChdRandomAccess,
        header: ParsedChdHeader,
        targetCount: Int,
    ): HunkMap? {
        if (header.mapOffset <= 0L) return null

        // Uncompressed v5 CHD
        if (header.compressors[0] == 0) {
            val rawMap = ByteArray(targetCount * 4)
            if (source.readAt(header.mapOffset, rawMap, 0, rawMap.size) != rawMap.size) {
                return null
            }
            val compTypes = ByteArray(targetCount) { V5_COMP_NONE.toByte() }
            val lengths = IntArray(targetCount) { header.hunkBytes }
            val offsets = LongArray(targetCount)
            for (i in 0 until targetCount) {
                val blockIndex = u32be(rawMap, i * 4).toLong() and 0xFFFFFFFFL
                offsets[i] = blockIndex * header.hunkBytes.toLong()
            }
            return HunkMap(compTypes, lengths, offsets)
        }

        val mapHdr = ByteArray(16)
        if (source.readAt(header.mapOffset, mapHdr, 0, 16) != 16) return null

        val mapBytes = u32be(mapHdr, 0)
        if (mapBytes <= 0 || mapBytes > MAX_MAP_BYTES) return null
        val firstOffs = u48be(mapHdr, 4)
        val lengthBits = mapHdr[12].toInt() and 0xFF
        val selfBits = mapHdr[13].toInt() and 0xFF
        val parentBits = mapHdr[14].toInt() and 0xFF
        if (lengthBits > 31 || selfBits > 31 || parentBits > 31) return null

        val compressed = ByteArray(mapBytes)
        if (source.readAt(header.mapOffset + 16L, compressed, 0, mapBytes) != mapBytes) return null

        val bitBuf = ChdBitStream(compressed)
        val huffman = HuffmanDecoder(numCodes = 16, maxBits = 8)
        if (!huffman.importTreeRle(bitBuf)) return null

        // Loop 1 must decode all totalHunks compression codes to advance bitBuf to the exact bit
        // where length/CRC/self fields start.
        val allCompTypes = ByteArray(header.totalHunks)
        var lastComp = 0
        var repCount = 0
        for (hunkNum in 0 until header.totalHunks) {
            if (repCount > 0) {
                allCompTypes[hunkNum] = lastComp.toByte()
                repCount--
            } else {
                val value = huffman.decodeOne(bitBuf)
                when (value) {
                    V5_COMP_RLE_SMALL -> {
                        allCompTypes[hunkNum] = lastComp.toByte()
                        repCount = 2 + huffman.decodeOne(bitBuf)
                    }
                    V5_COMP_RLE_LARGE -> {
                        allCompTypes[hunkNum] = lastComp.toByte()
                        val hi = huffman.decodeOne(bitBuf)
                        val lo = huffman.decodeOne(bitBuf)
                        repCount = 2 + 16 + (hi shl 4) + lo
                    }
                    else -> {
                        lastComp = value
                        allCompTypes[hunkNum] = value.toByte()
                    }
                }
            }
        }
        if (bitBuf.overflow()) return null

        val compTypes = ByteArray(targetCount)
        val lengths = IntArray(targetCount)
        val offsets = LongArray(targetCount)

        var curOffset = firstOffs
        var lastSelf = 0L
        var lastParent = 0L
        val unitsPerHunk = (header.hunkBytes / header.unitBytes).toLong()

        for (hunkNum in 0 until targetCount) {
            var comp = allCompTypes[hunkNum].toInt() and 0xFF
            var offset = curOffset
            var length = 0

            when (comp) {
                V5_COMP_TYPE_0, V5_COMP_TYPE_1, V5_COMP_TYPE_2, V5_COMP_TYPE_3 -> {
                    length = bitBuf.read(lengthBits)
                    curOffset += length.toLong()
                    bitBuf.read(16) // block crc16
                }
                V5_COMP_NONE -> {
                    length = header.hunkBytes
                    curOffset += length.toLong()
                    bitBuf.read(16) // block crc16
                }
                V5_COMP_SELF -> {
                    offset = bitBuf.read(selfBits).toLong()
                    lastSelf = offset
                }
                V5_COMP_PARENT -> {
                    offset = bitBuf.read(parentBits).toLong()
                    lastParent = offset
                }
                V5_COMP_SELF_1 -> {
                    lastSelf++
                    comp = V5_COMP_SELF
                    offset = lastSelf
                }
                V5_COMP_SELF_0 -> {
                    comp = V5_COMP_SELF
                    offset = lastSelf
                }
                V5_COMP_PARENT_SELF -> {
                    comp = V5_COMP_PARENT
                    offset = (hunkNum.toLong() * header.hunkBytes.toLong()) / header.unitBytes.toLong()
                    lastParent = offset
                }
                V5_COMP_PARENT_1 -> {
                    lastParent += unitsPerHunk
                    comp = V5_COMP_PARENT
                    offset = lastParent
                }
                V5_COMP_PARENT_0 -> {
                    comp = V5_COMP_PARENT
                    offset = lastParent
                }
                else -> return null
            }

            compTypes[hunkNum] = comp.toByte()
            lengths[hunkNum] = length
            offsets[hunkNum] = offset
        }

        if (bitBuf.overflow()) return null
        return HunkMap(compTypes, lengths, offsets)
    }

    private fun decompressLzmaRaw(
        src: ByteArray,
        srcOffset: Int,
        compLen: Int,
        dest: ByteArray,
        destLen: Int,
    ): Boolean {
        val dictSize = maxOf(65536, Integer.highestOneBit(destLen.coerceAtLeast(4096)) shl 1)
        return runCatching {
            LZMAInputStream(
                ByteArrayInputStream(src, srcOffset, compLen),
                destLen.toLong(),
                LZMA_PROPS_BYTE,
                dictSize,
            ).use { lzma ->
                var total = 0
                while (total < destLen) {
                    val n = lzma.read(dest, total, destLen - total)
                    if (n <= 0) break
                    total += n
                }
                total == destLen
            }
        }.getOrDefault(false)
    }

    private fun decompressZlibRaw(
        src: ByteArray,
        srcOffset: Int,
        compLen: Int,
        dest: ByteArray,
        destOffset: Int,
        destLen: Int,
    ): Boolean {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(src, srcOffset, compLen)
            var total = 0
            while (total < destLen && !inflater.finished()) {
                val n = inflater.inflate(dest, destOffset + total, destLen - total)
                if (n <= 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    total += n
                }
            }
            total == destLen
        } catch (_: Exception) {
            false
        } finally {
            inflater.end()
        }
    }

    private fun matchesSyncHeader(
        buf: ByteArray,
        offset: Int,
    ): Boolean {
        for (i in CD_SYNC_HEADER.indices) {
            if (buf[offset + i] != CD_SYNC_HEADER[i]) return false
        }
        return true
    }

    private fun u16be(
        b: ByteArray,
        off: Int,
    ): Int = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun u32be(
        b: ByteArray,
        off: Int,
    ): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)

    private fun u48be(
        b: ByteArray,
        off: Int,
    ): Long =
        ((b[off].toLong() and 0xFFL) shl 40) or
            ((b[off + 1].toLong() and 0xFFL) shl 32) or
            ((b[off + 2].toLong() and 0xFFL) shl 24) or
            ((b[off + 3].toLong() and 0xFFL) shl 16) or
            ((b[off + 4].toLong() and 0xFFL) shl 8) or
            (b[off + 5].toLong() and 0xFFL)

    private fun u64be(
        b: ByteArray,
        off: Int,
    ): Long =
        ((b[off].toLong() and 0xFFL) shl 56) or
            ((b[off + 1].toLong() and 0xFFL) shl 48) or
            ((b[off + 2].toLong() and 0xFFL) shl 40) or
            ((b[off + 3].toLong() and 0xFFL) shl 32) or
            ((b[off + 4].toLong() and 0xFFL) shl 24) or
            ((b[off + 5].toLong() and 0xFFL) shl 16) or
            ((b[off + 6].toLong() and 0xFFL) shl 8) or
            (b[off + 7].toLong() and 0xFFL)

    private class ChdBitStream(private val src: ByteArray) {
        private var buffer: Int = 0
        private var bits: Int = 0
        private var doffset: Int = 0

        fun peek(numBits: Int): Int {
            if (numBits == 0) return 0
            if (numBits > bits) {
                while (bits <= 24) {
                    if (doffset < src.size) {
                        buffer = buffer or ((src[doffset].toInt() and 0xFF) shl (24 - bits))
                    }
                    doffset++
                    bits += 8
                }
            }
            return buffer ushr (32 - numBits)
        }

        fun remove(numBits: Int) {
            if (numBits <= 0) return
            buffer = if (numBits >= 32) 0 else buffer shl numBits
            bits -= numBits
        }

        fun read(numBits: Int): Int {
            if (numBits == 0) return 0
            val res = peek(numBits)
            remove(numBits)
            return res
        }

        fun overflow(): Boolean = (doffset - bits / 8) > src.size
    }

    private class HuffmanDecoder(
        private val numCodes: Int,
        private val maxBits: Int,
    ) {
        private val nodeNumBits = IntArray(numCodes)
        private val nodeBits = IntArray(numCodes)
        private val lookup = IntArray(1 shl maxBits)

        fun decodeOne(bitBuf: ChdBitStream): Int {
            val peeked = bitBuf.peek(maxBits)
            val entry = lookup[peeked]
            val bitLen = entry and 0x1F
            if (bitLen == 0) {
                bitBuf.remove(1)
                return 0
            }
            bitBuf.remove(bitLen)
            return entry ushr 5
        }

        fun importTreeRle(bitBuf: ChdBitStream): Boolean {
            val numBits =
                when {
                    maxBits >= 16 -> 5
                    maxBits >= 8 -> 4
                    else -> 3
                }
            var curNode = 0
            while (curNode < numCodes) {
                var nb = bitBuf.read(numBits)
                if (nb != 1) {
                    nodeNumBits[curNode++] = nb
                } else {
                    nb = bitBuf.read(numBits)
                    if (nb == 1) {
                        nodeNumBits[curNode++] = 1
                    } else {
                        val repCount = bitBuf.read(numBits) + 3
                        if (curNode + repCount > numCodes) return false
                        repeat(repCount) {
                            nodeNumBits[curNode++] = nb
                        }
                    }
                }
            }
            if (curNode != numCodes) return false
            if (!assignCanonicalCodes()) return false
            buildLookupTable()
            return !bitBuf.overflow()
        }

        private fun assignCanonicalCodes(): Boolean {
            val bitHisto = IntArray(33)
            for (i in 0 until numCodes) {
                val nb = nodeNumBits[i]
                if (nb < 0 || nb > maxBits) return false
                if (nb <= 32) bitHisto[nb]++
            }
            var curStart = 0
            for (codeLen in 32 downTo 1) {
                val nextStart = (curStart + bitHisto[codeLen]) ushr 1
                if (codeLen != 1 && nextStart * 2 != (curStart + bitHisto[codeLen])) {
                    return false
                }
                bitHisto[codeLen] = curStart
                curStart = nextStart
            }
            for (i in 0 until numCodes) {
                val nb = nodeNumBits[i]
                if (nb > 0) {
                    nodeBits[i] = bitHisto[nb]++
                }
            }
            return true
        }

        private fun buildLookupTable() {
            for (i in 0 until numCodes) {
                val nb = nodeNumBits[i]
                if (nb > 0) {
                    val value = (i shl 5) or (nb and 0x1F)
                    val shift = maxBits - nb
                    val start = nodeBits[i] shl shift
                    val end = ((nodeBits[i] + 1) shl shift) - 1
                    for (idx in start..end) {
                        lookup[idx] = value
                    }
                }
            }
        }
    }
}
