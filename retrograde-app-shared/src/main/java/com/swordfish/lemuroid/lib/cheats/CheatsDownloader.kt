package com.swordfish.lemuroid.lib.cheats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads the cheats of a game from libretro's public cheat database
 * (https://github.com/libretro/libretro-database, license CC BY-SA 4.0).
 *
 * The database names files like the games ("Contra (USA).cht"), so the game's title is matched
 * against the list of files of its system with [LibretroTitleMatcher].
 */
class CheatsDownloader(
    private val client: OkHttpClient,
    cacheDirectory: File,
) {
    class NotFoundException : IOException("No cheats were found for this game")

    data class Result(val fileName: String, val cheats: List<Cheat>)

    private val indexDirectory = File(cacheDirectory, "cheat_index")

    /**
     * Looks for cheats of a game in the folder of its system. [titles] are tried in order, so pass
     * the title first and a name the user chose after it.
     */
    suspend fun download(
        systemFolder: String,
        titles: List<String>,
    ): Result =
        withContext(Dispatchers.IO) {
            val names = listFileNames(systemFolder)
            val best =
                titles.asSequence()
                    .mapNotNull { LibretroTitleMatcher.findBest(names, it) }
                    .firstOrNull()
                    ?: throw NotFoundException()

            val cheats = ChtParser.parse(fetch(rawUrl(systemFolder, best)))
            if (cheats.isEmpty()) throw NotFoundException()
            Result(best, cheats)
        }

    private fun listFileNames(systemFolder: String): List<String> {
        val cacheFile = File(indexDirectory, systemFolder.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".txt")
        if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < INDEX_MAX_AGE_MS) {
            return cacheFile.readLines()
        }

        val url =
            "https://api.github.com/repos/libretro/libretro-database/git/trees/".toHttpUrl().newBuilder()
                .addPathSegments("master:cht/$systemFolder")
                .build()
        val names = parseTreeNames(fetch(url.toString(), "application/vnd.github+json"))

        if (names.isNotEmpty()) {
            runCatching {
                indexDirectory.mkdirs()
                cacheFile.writeText(names.joinToString("\n"))
            }
        }
        return names
    }

    private fun rawUrl(
        systemFolder: String,
        fileName: String,
    ): String =
        "https://raw.githubusercontent.com/libretro/libretro-database/master/cht/".toHttpUrl().newBuilder()
            .addPathSegment(systemFolder)
            .addPathSegment("$fileName.cht")
            .build()
            .toString()

    private fun fetch(
        url: String,
        accept: String = "*/*",
    ): String {
        val request =
            Request.Builder()
                .url(url)
                .header("Accept", accept)
                .header("User-Agent", "Arcadune")
                .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw NotFoundException()
            if (!response.isSuccessful) throw IOException("The cheat server answered ${response.code}")
            return response.body?.string() ?: throw IOException("Empty answer from the cheat server")
        }
    }

    companion object {
        private const val INDEX_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** Reads the ".cht" file names (without the extension) from a GitHub "git tree" answer. */
        fun parseTreeNames(json: String): List<String> =
            Json.parseToJsonElement(json).jsonObject["tree"]
                ?.jsonArray
                ?.mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.content }
                ?.filter { it.endsWith(".cht") }
                ?.map { it.removeSuffix(".cht") }
                ?: emptyList()
    }
}
