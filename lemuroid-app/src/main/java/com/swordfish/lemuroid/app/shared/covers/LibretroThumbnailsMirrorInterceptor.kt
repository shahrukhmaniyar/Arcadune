package com.swordfish.lemuroid.app.shared.covers

import android.util.JsonReader
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * thumbnails.libretro.com is sometimes unreachable. The same images are published in the
 * libretro-thumbnails GitHub organization, so for covers we try that mirror first and fall back
 * to the original server if the mirror doesn't have the image.
 *
 * When the mirror doesn't have the exact image (for example because the game was matched to a
 * variant like "(PlayChoice-10)" that has no box art, or because the user renamed the game), we
 * also try the same title under the most common region tags and finally look for the closest name
 * in the repository's list of covers.
 *
 * Cover urls may carry a [FALLBACK_PARAM] query parameter: the url the game had before it was
 * renamed. It is only used if nothing matches the new name.
 */
class LibretroThumbnailsMirrorInterceptor(cacheDir: File) : Interceptor {
    private val indexDirectory = File(cacheDir, "thumbnail_index").apply { mkdirs() }
    private val indexes = ConcurrentHashMap<String, List<IndexedName>>()
    private val pendingIndexes = ConcurrentHashMap<String, Future<List<String>?>>()
    private val indexFailedAt = ConcurrentHashMap<String, Long>()

    private val indexExecutor =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "thumbnail-index").apply { isDaemon = true }
        }

    private val indexClient =
        OkHttpClient.Builder()
            .readTimeout(INDEX_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requested = parseThumbnailUrl(request.url) ?: return chain.proceed(request)
        val fallback =
            request.url.queryParameter(FALLBACK_PARAM)
                ?.toHttpUrlOrNull()
                ?.let { parseThumbnailUrl(it) }

        try {
            for (parts in listOfNotNull(requested, fallback)) {
                findOnMirror(chain, request, parts)?.let { return it }
            }
        } catch (e: IOException) {
            // Mirror unreachable: fall through to the original server.
        }

        return chain.proceed(request.newBuilder().url(requested.originalUrl()).build())
    }

    private fun findOnMirror(
        chain: Interceptor.Chain,
        request: Request,
        parts: ThumbnailParts,
    ): Response? {
        for (fileName in candidateFileNames(parts.fileName)) {
            tryMirror(chain, request, parts, fileName)?.let { return it }
        }

        val closest = closestFileName(parts) ?: return null
        return tryMirror(chain, request, parts, closest)
    }

    private fun tryMirror(
        chain: Interceptor.Chain,
        request: Request,
        parts: ThumbnailParts,
        fileName: String,
    ): Response? {
        val response =
            try {
                chain.proceed(request.newBuilder().url(parts.mirrorUrl(fileName)).build())
            } catch (e: PreviouslyFailedThumbnailException) {
                // This exact name is already known to be missing: just try the next one.
                return null
            }
        if (response.isSuccessful) return response
        response.close()
        return null
    }

    /** The exact file name first, then the same title with other common region tags. */
    private fun candidateFileNames(fileName: String): List<String> {
        val baseTitle = baseTitle(fileName.removeSuffix(".png"))
        if (baseTitle.isEmpty()) return listOf(fileName)

        val variants =
            FALLBACK_REGIONS
                .map { "$baseTitle $it.png" }
                .filter { it != fileName }

        return listOf(fileName) + variants
    }

    /** Looks the title up in the repository's list of covers, ignoring tags, case and numerals. */
    private fun closestFileName(parts: ThumbnailParts): String? {
        val wanted = normalize(parts.fileName.removeSuffix(".png"))
        if (wanted.isEmpty()) return null

        return loadIndex(parts.repository)
            .filter { it.normalized == wanted }
            .minWithOrNull(compareBy({ it.isBadDump }, { it.regionRank }, { it.name.length }))
            ?.name
            ?.plus(".png")
    }

    private fun loadIndex(repository: String): List<IndexedName> {
        indexes[repository]?.let { return it }

        val file = File(indexDirectory, "$repository.txt")
        val names =
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < INDEX_MAX_AGE_MS) {
                file.readLines()
            } else {
                downloadIndexInBackground(repository)?.also { fresh ->
                    runCatching { file.writeText(fresh.joinToString("\n")) }
                } ?: return emptyList()
            }

        return names.map { IndexedName(it) }.also { indexes[repository] = it }
    }

    /**
     * The list is downloaded on its own thread, so scrolling away from a cover (which cancels its
     * request) doesn't abort the download. Requests for the same system share one download.
     */
    private fun downloadIndexInBackground(repository: String): List<String>? {
        val lastFailure = indexFailedAt[repository]
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < INDEX_RETRY_DELAY_MS) {
            return null
        }

        val download =
            // Not computeIfAbsent: it does not exist on Android 6.
            synchronized(pendingIndexes) {
                pendingIndexes.getOrPut(repository) {
                    indexExecutor.submit(Callable { downloadIndex(repository) })
                }
            }

        val names =
            try {
                download.get(INDEX_WAIT_SECONDS, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted while loading the cover index")
            } catch (e: ExecutionException) {
                Timber.w(e, "Could not load cover index for $repository")
                null
            } catch (e: TimeoutException) {
                return null
            }

        if (download.isDone) pendingIndexes.remove(repository, download)
        if (names == null) indexFailedAt[repository] = System.currentTimeMillis()
        return names
    }

    private fun downloadIndex(repository: String): List<String>? {
        val request =
            Request.Builder()
                .url("https://api.github.com/repos/libretro-thumbnails/$repository/git/trees/master:Named_Boxarts")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Arcadune")
                .build()

        return indexClient.newCall(request).execute().use {
            if (!it.isSuccessful) {
                Timber.w("Could not load cover index for $repository: ${it.code}")
                return@use null
            }
            parseTreeNames(JsonReader(it.body!!.charStream()))
        }
    }

    /** Reads the "path" of every entry in a GitHub tree response, without the .png extension. */
    private fun parseTreeNames(reader: JsonReader): List<String> {
        val names = mutableListOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "tree") {
                reader.skipValue()
                continue
            }
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "path") {
                        val path = reader.nextString()
                        if (path.endsWith(".png")) names.add(path.removeSuffix(".png"))
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
            }
            reader.endArray()
        }
        reader.endObject()
        return names
    }

    private class IndexedName(val name: String) {
        val normalized: String = normalize(name)
        val isBadDump: Boolean = name.contains('[')
        val regionRank: Int = regionRank(name)
    }

    private data class ThumbnailParts(
        val system: String,
        val imageType: String,
        val fileName: String,
    ) {
        val repository: String = system.replace(' ', '_').replace('&', '_')

        fun mirrorUrl(fileName: String): HttpUrl =
            "$MIRROR_BASE/".toHttpUrl().newBuilder()
                .addPathSegment(repository)
                .addPathSegment("master")
                .addPathSegment(imageType)
                .addPathSegment(fileName)
                .build()

        fun originalUrl(): HttpUrl =
            "http://$THUMBNAILS_HOST/".toHttpUrl().newBuilder()
                .addPathSegment(system)
                .addPathSegment(imageType)
                .addPathSegment(fileName)
                .build()
    }

    /**
     * Original: http(s)://thumbnails.libretro.com/<System>/<ImageType>/<Name>.png
     * Mirror:   https://raw.githubusercontent.com/libretro-thumbnails/<System_with_underscores>/master/<ImageType>/<Name>.png
     */
    private fun parseThumbnailUrl(url: HttpUrl): ThumbnailParts? {
        if (url.host != THUMBNAILS_HOST) return null

        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size != 3) return null

        val (system, imageType, fileName) = segments
        return ThumbnailParts(system, imageType, fileName)
    }

    companion object {
        const val FALLBACK_PARAM = "fallback"

        private const val THUMBNAILS_HOST = "thumbnails.libretro.com"
        private const val MIRROR_BASE = "https://raw.githubusercontent.com/libretro-thumbnails"
        private const val INDEX_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val INDEX_RETRY_DELAY_MS = 5L * 60 * 1000
        private const val INDEX_WAIT_SECONDS = 45L
        private const val INDEX_READ_TIMEOUT_SECONDS = 60L

        private val FALLBACK_REGIONS =
            listOf("(World)", "(USA)", "(USA, Europe)", "(Europe)", "(Japan)", "(Japan, USA)")

        private val PARENTHESES = Regex("\\s*\\([^)]*\\)")
        private val BRACKETS = Regex("\\s*\\[[^\\]]*\\]")
        private val REVISION = Regex("\\s+Rev\\s+\\S+")
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

        private val ROMAN_NUMERALS =
            mapOf(
                "i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5",
                "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10",
            )

        /** The title without region, publisher, revision and dump tags. */
        private fun baseTitle(title: String): String =
            title
                .replace(PARENTHESES, "")
                .replace(BRACKETS, "")
                .replace(REVISION, "")
                .trim()

        /** Lower case words with roman numerals turned into digits, so "IV" matches "4". */
        private fun normalize(title: String): String =
            baseTitle(title)
                .lowercase()
                .split(NON_ALPHANUMERIC)
                .filter { it.isNotEmpty() }
                .joinToString(" ") { ROMAN_NUMERALS[it] ?: it }

        private fun regionRank(name: String): Int {
            val lower = name.lowercase()
            return when {
                "(usa" in lower || "(us)" in lower -> 0
                "(world" in lower -> 1
                "(europe" in lower || "(eu)" in lower -> 2
                else -> 3
            }
        }
    }
}
