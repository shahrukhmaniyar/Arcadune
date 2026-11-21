package com.swordfish.lemuroid.lib.library

import android.net.Uri
import com.swordfish.lemuroid.common.files.readLines
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.GroupedStorageFiles
import com.swordfish.lemuroid.lib.storage.StorageProvider
import java.util.Locale

object StorageFilesMerger {
    private val CUE_QUOTED_FILE_REGEX = Regex("(?i)^\\s*FILE\\s+\"([^\"]+)\"")
    private val CUE_SINGLE_QUOTED_FILE_REGEX = Regex("(?i)^\\s*FILE\\s+'([^']+)'")
    private val CUE_UNQUOTED_FILE_REGEX = Regex("(?i)^\\s*FILE\\s+(\\S+)")

    /** Merge files which belong to the same game. This includes cue/ccd/mds/gdi sets and m3u playlists. */
    fun mergeDataFiles(
        storageProvider: StorageProvider,
        files: List<BaseStorageFile>,
    ): List<GroupedStorageFiles> {
        val allFiles =
            files
                .associateWith { listOf<BaseStorageFile>() }
                .toMutableMap()

        mergeBinCueFiles(allFiles, storageProvider)
        removeInvalidBinCuePairs(allFiles, storageProvider)
        mergeCcdFiles(allFiles)
        removeInvalidCcdSets(allFiles)
        mergeMdsFiles(allFiles)
        removeInvalidMdsSets(allFiles)
        mergeGdiFiles(allFiles, storageProvider)
        removeInvalidGdiPairs(allFiles, storageProvider)
        mergeM3UPlaylists(allFiles, storageProvider)
        removeInvalidM3UPlaylists(allFiles, storageProvider)

        return allFiles.map { GroupedStorageFiles(it.key, it.value) }
    }

    private fun normalizeFileName(rawName: String): String {
        return rawName
            .trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .lowercase(Locale.US)
    }

    internal fun parseM3uEntries(lines: List<String>): List<String> {
        return lines
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { normalizeFileName(it) }
    }

    internal fun parseCueFileReferences(lines: List<String>): List<String> {
        return lines.mapNotNull { line ->
            CUE_QUOTED_FILE_REGEX.find(line)?.groupValues?.get(1)
                ?: CUE_SINGLE_QUOTED_FILE_REGEX.find(line)?.groupValues?.get(1)
                ?: CUE_UNQUOTED_FILE_REGEX.find(line)?.groupValues?.get(1)
        }
    }

    private fun extractM3UEntries(
        storageProvider: StorageProvider,
        uri: Uri,
    ): List<String> {
        return runCatching {
            storageProvider.getInputStream(uri)?.readLines()
                ?.let { parseM3uEntries(it) }
                ?: listOf()
        }.getOrDefault(listOf())
    }

    private fun removeInvalidM3UPlaylists(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "m3u" }
            .forEach { m3uFile ->
                val m3uFiles = extractM3UEntries(storageProvider, m3uFile.uri).toSet()
                val filesNames =
                    allFiles[m3uFile]
                        ?.map { normalizeFileName(it.name) }
                        ?.toSet() ?: setOf()

                if (m3uFiles.isEmpty() || !filesNames.containsAll(m3uFiles)) {
                    toBeRemoved.add(m3uFile)
                }
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun mergeM3UPlaylists(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "m3u" }
            .forEach { m3uFile ->
                val m3uFiles = extractM3UEntries(storageProvider, m3uFile.uri).toSet()
                val dataFiles =
                    allFiles.filter { entry ->
                        entry.key != m3uFile && normalizeFileName(entry.key.name) in m3uFiles
                    }

                allFiles[m3uFile] = allFiles[m3uFile]!! +
                    dataFiles.flatMap {
                        listOf(it.key) + it.value
                    }
                toBeRemoved.addAll(dataFiles.keys)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun removeInvalidBinCuePairs(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "cue" }
            .forEach { cueFile ->
                val requestedFileNames =
                    extractBinFiles(storageProvider, cueFile.uri)
                        .map { normalizeFileName(it) }
                        .toSet()
                val givenFileNames =
                    allFiles[cueFile]
                        ?.map { normalizeFileName(it.name) }
                        ?.toSet() ?: setOf()

                if (requestedFileNames.isEmpty() || !givenFileNames.containsAll(requestedFileNames)) {
                    toBeRemoved.add(cueFile)
                }
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun mergeBinCueFiles(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "cue" }
            .forEach { cueFile ->
                val requestedBinNames =
                    extractBinFiles(storageProvider, cueFile.uri)
                        .map { normalizeFileName(it) }
                        .toSet()

                val binFiles =
                    allFiles.filter { entry ->
                        entry.key != cueFile && normalizeFileName(entry.key.name) in requestedBinNames
                    }

                val candidateSubStems =
                    (
                        listOf(cueFile.extensionlessName.lowercase(Locale.US)) +
                            binFiles.keys.map { it.extensionlessName.lowercase(Locale.US) }
                    ).toSet()

                val optionalSubFiles =
                    if (binFiles.isNotEmpty()) {
                        allFiles.filter { entry ->
                            entry.key != cueFile &&
                                entry.key.extension == "sub" &&
                                entry.key.extensionlessName.lowercase(Locale.US) in candidateSubStems
                        }
                    } else {
                        emptyMap()
                    }

                val mergedCompanions = binFiles + optionalSubFiles
                allFiles[cueFile] = (allFiles[cueFile] ?: listOf()) +
                    mergedCompanions.flatMap {
                        listOf(it.key) + it.value
                    }
                toBeRemoved.addAll(mergedCompanions.keys)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun extractBinFiles(
        storageProvider: StorageProvider,
        uri: Uri,
    ): List<String> {
        return runCatching {
            storageProvider.getInputStream(uri)?.readLines()
                ?.let { parseCueFileReferences(it) }
                ?: listOf()
        }.getOrDefault(listOf())
    }

    private fun mergeCcdFiles(allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "ccd" }
            .forEach { ccdFile ->
                val stem = ccdFile.extensionlessName.lowercase(Locale.US)
                val imgEntries =
                    allFiles.filter { entry ->
                        entry.key != ccdFile &&
                            entry.key.extension == "img" &&
                            entry.key.extensionlessName.lowercase(Locale.US) == stem
                    }
                if (imgEntries.isNotEmpty()) {
                    val subEntries =
                        allFiles.filter { entry ->
                            entry.key != ccdFile &&
                                entry.key.extension == "sub" &&
                                entry.key.extensionlessName.lowercase(Locale.US) == stem
                        }
                    val companions = imgEntries + subEntries
                    allFiles[ccdFile] = (allFiles[ccdFile] ?: listOf()) +
                        companions.flatMap { listOf(it.key) + it.value }
                    toBeRemoved.addAll(companions.keys)
                }
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun removeInvalidCcdSets(allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>) {
        val invalid =
            allFiles.keys
                .filter { it.extension == "ccd" }
                .filter { ccdFile ->
                    allFiles[ccdFile]?.any { it.extension == "img" } != true
                }
        invalid.forEach { allFiles.remove(it) }
    }

    private fun mergeMdsFiles(allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "mds" }
            .forEach { mdsFile ->
                val stem = mdsFile.extensionlessName.lowercase(Locale.US)
                val mdfEntries =
                    allFiles.filter { entry ->
                        entry.key != mdsFile &&
                            entry.key.extension == "mdf" &&
                            entry.key.extensionlessName.lowercase(Locale.US) == stem
                    }
                if (mdfEntries.isNotEmpty()) {
                    allFiles[mdsFile] = (allFiles[mdsFile] ?: listOf()) +
                        mdfEntries.flatMap { listOf(it.key) + it.value }
                    toBeRemoved.addAll(mdfEntries.keys)
                }
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun removeInvalidMdsSets(allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>) {
        val invalid =
            allFiles.keys
                .filter { it.extension == "mds" }
                .filter { mdsFile ->
                    allFiles[mdsFile]?.any { it.extension == "mdf" } != true
                }
        invalid.forEach { allFiles.remove(it) }
    }

    private fun removeInvalidGdiPairs(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "gdi" }
            .forEach { gdiFile ->
                val requestedFileNames =
                    extractGdiTrackFiles(storageProvider, gdiFile.uri)
                        .map { normalizeFileName(it) }
                        .toSet()
                val givenFileNames =
                    allFiles[gdiFile]
                        ?.map { normalizeFileName(it.name) }
                        ?.toSet() ?: setOf()

                if (requestedFileNames.isEmpty() || !givenFileNames.containsAll(requestedFileNames)) {
                    toBeRemoved.add(gdiFile)
                }
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun mergeGdiFiles(
        allFiles: MutableMap<BaseStorageFile, List<BaseStorageFile>>,
        storageProvider: StorageProvider,
    ) {
        val toBeRemoved = mutableListOf<BaseStorageFile>()

        allFiles.keys
            .asSequence()
            .filter { it.extension == "gdi" }
            .forEach { gdiFile ->
                val requestedTrackNames =
                    extractGdiTrackFiles(storageProvider, gdiFile.uri)
                        .map { normalizeFileName(it) }
                        .toSet()

                val trackFiles =
                    allFiles.filter { entry ->
                        entry.key != gdiFile && normalizeFileName(entry.key.name) in requestedTrackNames
                    }

                allFiles[gdiFile] = (allFiles[gdiFile] ?: listOf()) +
                    trackFiles.flatMap {
                        listOf(it.key) + it.value
                    }
                toBeRemoved.addAll(trackFiles.keys)
            }

        toBeRemoved.forEach { allFiles.remove(it) }
    }

    private fun extractGdiTrackFiles(
        storageProvider: StorageProvider,
        uri: Uri,
    ): List<String> {
        return runCatching {
            val lines = storageProvider.getInputStream(uri)?.readLines() ?: return@runCatching listOf()
            lines.drop(1).mapNotNull { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@mapNotNull null
                val quoted = Regex("\"(.*)\"").find(line)?.groupValues?.get(1)
                if (quoted != null) {
                    quoted
                } else {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 5) parts[4] else null
                }
            }
        }.getOrDefault(listOf())
    }
}
