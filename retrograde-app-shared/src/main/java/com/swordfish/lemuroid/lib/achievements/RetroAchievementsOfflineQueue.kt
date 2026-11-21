package com.swordfish.lemuroid.lib.achievements

import android.content.Context
import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Serializable
enum class OfflineSubmissionKind {
    @SerialName("AWARD_ACHIEVEMENT")
    AWARD_ACHIEVEMENT,

    @SerialName("SUBMIT_LEADERBOARD")
    SUBMIT_LEADERBOARD,
}

@Serializable
data class PendingOfflineSubmission(
    @SerialName("kind") val kind: OfflineSubmissionKind,
    @SerialName("username") val username: String,
    @SerialName("gameHash") val gameHash: String,
    @SerialName("targetId") val targetId: Long,
    @SerialName("hardcore") val hardcore: Boolean,
    @SerialName("scoreValue") val scoreValue: Int = 0,
    @SerialName("createdAtEpochMs") val createdAtEpochMs: Long,
    @SerialName("attempts") val attempts: Int = 0,
    @SerialName("userAgent") val userAgent: String = "",
) {
    fun dedupeKey(): String =
        "${kind.name}|${username.lowercase()}|$targetId|$hardcore|$scoreValue|${gameHash.lowercase()}"
}

data class OfflineReplaySummary(
    val attempted: Int,
    val delivered: Int,
    val remainingForAccount: Int,
    val preservedOtherAccounts: Int,
)

object RetroAchievementsOfflineQueue {
    internal const val KEY_OFFLINE_QUEUE = "ra_offline_queue_v1"
    private const val MAX_QUEUE_ENTRIES = 256
    private const val MAX_PERMANENT_ATTEMPTS = 25

    private val mutex = Mutex()

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    var clockEpochMs: () -> Long = { System.currentTimeMillis() }

    private fun prefs(context: Context): SharedPreferences =
        SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    private inline fun <T> withCrossProcessQueueLock(
        lockFile: File?,
        block: () -> T,
    ): T {
        if (lockFile == null) {
            return block()
        }
        lockFile.parentFile?.mkdirs()
        return RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use {
                block()
            }
        }
    }

    private fun queueLockFile(context: Context): File =
        File(context.applicationContext.filesDir, ".ra_offline_queue.lock")

    fun parseSubmissionPostData(
        postData: String?,
        fallbackGameHash: String = "",
        createdAtEpochMs: Long = clockEpochMs(),
        userAgent: String = "",
    ): PendingOfflineSubmission? {
        if (postData.isNullOrBlank()) return null
        val params = parseFormParams(postData)
        val requestType = params["r"] ?: return null
        val username = params["u"]?.trim().orEmpty()
        if (username.isEmpty()) return null
        val hash = params["m"]?.takeIf { it.isNotBlank() } ?: fallbackGameHash
        val cleanUa = userAgent.replace("\r", "").replace("\n", "").trim()

        return when (requestType) {
            "awardachievement" -> {
                val achId = params["a"]?.toLongOrNull() ?: return null
                if (achId <= 0L) return null
                val hardcore = params["h"] == "1"
                PendingOfflineSubmission(
                    kind = OfflineSubmissionKind.AWARD_ACHIEVEMENT,
                    username = username,
                    gameHash = hash,
                    targetId = achId,
                    hardcore = hardcore,
                    scoreValue = 0,
                    createdAtEpochMs = createdAtEpochMs,
                    userAgent = cleanUa,
                )
            }

            "submitlbentry" -> {
                val lbId = params["i"]?.toLongOrNull() ?: return null
                val score = params["s"]?.toIntOrNull() ?: return null
                if (lbId <= 0L) return null
                PendingOfflineSubmission(
                    kind = OfflineSubmissionKind.SUBMIT_LEADERBOARD,
                    username = username,
                    gameHash = hash,
                    targetId = lbId,
                    hardcore = true,
                    scoreValue = score,
                    createdAtEpochMs = createdAtEpochMs,
                    userAgent = cleanUa,
                )
            }

            else -> null
        }
    }

    fun computeAwardAchievementSignature(
        achievementId: Long,
        username: String,
        hardcore: Boolean,
        secondsSinceUnlock: Long,
    ): String {
        val sb = StringBuilder()
        sb.append(achievementId)
        sb.append(username)
        sb.append(if (hardcore) 1 else 0)
        if (secondsSinceUnlock > 0L) {
            sb.append(achievementId)
            sb.append(secondsSinceUnlock)
        }
        return md5Hex(sb.toString())
    }

    fun computeSubmitLeaderboardSignature(
        leaderboardId: Long,
        username: String,
        score: Int,
        secondsSinceCompletion: Long,
    ): String {
        val sb = StringBuilder()
        sb.append(leaderboardId)
        sb.append(username)
        sb.append(score)
        if (secondsSinceCompletion > 0L) {
            sb.append(secondsSinceCompletion)
        }
        return md5Hex(sb.toString())
    }

    suspend fun enqueueFromPostData(
        context: Context,
        postData: String?,
        fallbackGameHash: String,
        userAgent: String = "",
    ) = enqueueFromPostData(
        sp = prefs(context),
        postData = postData,
        fallbackGameHash = fallbackGameHash,
        userAgent = userAgent,
        lockFile = queueLockFile(context),
    )

    suspend fun enqueueFromPostData(
        sp: SharedPreferences,
        postData: String?,
        fallbackGameHash: String,
        userAgent: String = "",
        lockFile: File? = null,
    ) {
        val parsed = parseSubmissionPostData(postData, fallbackGameHash, userAgent = userAgent) ?: return
        enqueue(sp, parsed, lockFile)
    }

    suspend fun enqueue(
        sp: SharedPreferences,
        entry: PendingOfflineSubmission,
        lockFile: File? = null,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            withCrossProcessQueueLock(lockFile) {
                val existing = loadEntriesLocked(sp).toMutableList()
                val key = entry.dedupeKey()
                if (existing.any { it.dedupeKey() == key }) {
                    return@withCrossProcessQueueLock
                }
                if (existing.size >= MAX_QUEUE_ENTRIES) {
                    existing.removeAt(0)
                }
                existing.add(entry)
                saveEntriesLocked(sp, existing)
            }
        }
    }

    suspend fun acknowledgeCompletedPostData(
        context: Context,
        postData: String?,
        httpStatusCode: Int,
        responseBodyUtf8: String?,
        fallbackGameHash: String,
    ) = acknowledgeCompletedPostData(
        prefs(context),
        postData,
        httpStatusCode,
        responseBodyUtf8,
        fallbackGameHash,
        queueLockFile(context),
    )

    suspend fun acknowledgeCompletedPostData(
        sp: SharedPreferences,
        postData: String?,
        httpStatusCode: Int,
        responseBodyUtf8: String?,
        fallbackGameHash: String,
        lockFile: File? = null,
    ) {
        if (httpStatusCode !in 200..299 || responseBodyUtf8.isNullOrBlank()) return
        val parsed = parseSubmissionPostData(postData, fallbackGameHash) ?: return
        if (!isServerSuccessOrAlreadyAwarded(responseBodyUtf8)) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                withCrossProcessQueueLock(lockFile) {
                    val existing = loadEntriesLocked(sp)
                    if (existing.isEmpty()) return@withCrossProcessQueueLock
                    val filtered =
                        existing.filterNot { item ->
                            item.kind == parsed.kind &&
                                item.username.equals(parsed.username, ignoreCase = true) &&
                                item.targetId == parsed.targetId &&
                                item.hardcore == parsed.hardcore &&
                                (
                                    parsed.kind != OfflineSubmissionKind.SUBMIT_LEADERBOARD ||
                                        item.scoreValue == parsed.scoreValue
                                )
                        }
                    if (filtered.size != existing.size) {
                        saveEntriesLocked(sp, filtered)
                    }
                }
            }
        }
    }

    suspend fun listPending(sp: SharedPreferences): List<PendingOfflineSubmission> =
        mutex.withLock { loadEntriesLocked(sp) }

    suspend fun pendingCountForAccount(
        context: Context,
        username: String?,
    ): Int {
        if (username.isNullOrBlank()) return 0
        return mutex.withLock {
            loadEntriesLocked(prefs(context)).count { it.username.equals(username, ignoreCase = true) }
        }
    }

    suspend fun flushPending(
        context: Context,
        account: RetroAchievementsAccount?,
        endpointUrl: String = RetroAchievementsAuthClient.endpointUrl,
        appVersion: String = RetroAchievementsAuthClient.appVersion,
        osRelease: String? = null,
    ): OfflineReplaySummary =
        flushPending(
            sp = prefs(context),
            account = account,
            endpointUrl = endpointUrl,
            appVersion = appVersion,
            osRelease = osRelease,
            lockFile = queueLockFile(context),
        )

    suspend fun flushPending(
        sp: SharedPreferences,
        account: RetroAchievementsAccount?,
        endpointUrl: String = RetroAchievementsAuthClient.endpointUrl,
        appVersion: String = RetroAchievementsAuthClient.appVersion,
        osRelease: String? = null,
        lockFile: File? = null,
    ): OfflineReplaySummary =
        withContext(Dispatchers.IO) {
            if (account == null || account.username.isBlank() || account.token.isBlank()) {
                val all = mutex.withLock { withCrossProcessQueueLock(lockFile) { loadEntriesLocked(sp) } }
                return@withContext OfflineReplaySummary(
                    attempted = 0,
                    delivered = 0,
                    remainingForAccount = 0,
                    preservedOtherAccounts = all.size,
                )
            }

            val snapshot = mutex.withLock { withCrossProcessQueueLock(lockFile) { loadEntriesLocked(sp) } }
            val matching = snapshot.filter { it.username.equals(account.username, ignoreCase = true) }
            val otherAccountsCount = snapshot.size - matching.size
            if (matching.isEmpty()) {
                return@withContext OfflineReplaySummary(
                    attempted = 0,
                    delivered = 0,
                    remainingForAccount = 0,
                    preservedOtherAccounts = otherAccountsCount,
                )
            }

            val fallbackUserAgent =
                if (osRelease != null) {
                    RetroAchievementsUserAgent.build(
                        appVersion = appVersion,
                        osRelease = osRelease,
                        rcheevosClause = RetroAchievementsUserAgent.DEFAULT_RCHEEVOS_CLAUSE,
                    )
                } else {
                    RetroAchievementsUserAgent.build(
                        appVersion = appVersion,
                        rcheevosClause = RetroAchievementsUserAgent.DEFAULT_RCHEEVOS_CLAUSE,
                    )
                }

            val keysToRemove = HashSet<String>()
            val retryBumps = HashMap<String, Int>()
            var delivered = 0

            for (item in matching) {
                val key = item.dedupeKey()
                val nowMs = clockEpochMs()
                val elapsedSec = max(1L, (nowMs - item.createdAtEpochMs) / 1000L)
                val formBody = buildReplayFormBody(item, account.token, elapsedSec)
                val effectiveUa =
                    item.userAgent.takeIf { it.startsWith("Arcadune/") } ?: fallbackUserAgent
                val request =
                    Request.Builder()
                        .url(endpointUrl)
                        .header("User-Agent", effectiveUa)
                        .post(formBody)
                        .build()

                val outcome = executeReplayCall(request)
                when (outcome) {
                    ReplayCallOutcome.DELIVERED -> {
                        keysToRemove.add(key)
                        delivered++
                    }

                    ReplayCallOutcome.RETRYABLE_ERROR -> {
                        retryBumps[key] = item.attempts + 1
                    }

                    ReplayCallOutcome.AUTH_ERROR -> {
                        // Preserve entry untouched until the user refreshes their session token.
                        break
                    }

                    ReplayCallOutcome.PERMANENT_REJECTION -> {
                        if (item.attempts + 1 >= MAX_PERMANENT_ATTEMPTS) {
                            keysToRemove.add(key)
                        } else {
                            retryBumps[key] = item.attempts + 1
                        }
                    }
                }
            }

            var remainingMine = 0
            var remainingOthers = 0
            mutex.withLock {
                withCrossProcessQueueLock(lockFile) {
                    val current = loadEntriesLocked(sp)
                    val updated =
                        current.mapNotNull { entry ->
                            val key = entry.dedupeKey()
                            if (key in keysToRemove) {
                                null
                            } else {
                                val bumped = retryBumps[key]
                                if (bumped != null) entry.copy(attempts = bumped) else entry
                            }
                        }
                    saveEntriesLocked(sp, updated)
                    for (entry in updated) {
                        if (entry.username.equals(account.username, ignoreCase = true)) {
                            remainingMine++
                        } else {
                            remainingOthers++
                        }
                    }
                }
            }

            OfflineReplaySummary(
                attempted = matching.size,
                delivered = delivered,
                remainingForAccount = remainingMine,
                preservedOtherAccounts = remainingOthers,
            )
        }

    private enum class ReplayCallOutcome {
        DELIVERED,
        RETRYABLE_ERROR,
        AUTH_ERROR,
        PERMANENT_REJECTION,
    }

    private fun buildReplayFormBody(
        item: PendingOfflineSubmission,
        token: String,
        elapsedSeconds: Long,
    ): FormBody {
        val builder = FormBody.Builder()
        when (item.kind) {
            OfflineSubmissionKind.AWARD_ACHIEVEMENT -> {
                val sig =
                    computeAwardAchievementSignature(
                        achievementId = item.targetId,
                        username = item.username,
                        hardcore = item.hardcore,
                        secondsSinceUnlock = elapsedSeconds,
                    )
                builder
                    .add("r", "awardachievement")
                    .add("u", item.username)
                    .add("t", token)
                    .add("a", item.targetId.toString())
                    .add("h", if (item.hardcore) "1" else "0")
                if (item.gameHash.isNotBlank()) {
                    builder.add("m", item.gameHash)
                }
                if (elapsedSeconds > 0L) {
                    builder.add("o", elapsedSeconds.toString())
                }
                builder.add("v", sig)
            }

            OfflineSubmissionKind.SUBMIT_LEADERBOARD -> {
                val sig =
                    computeSubmitLeaderboardSignature(
                        leaderboardId = item.targetId,
                        username = item.username,
                        score = item.scoreValue,
                        secondsSinceCompletion = elapsedSeconds,
                    )
                builder
                    .add("r", "submitlbentry")
                    .add("u", item.username)
                    .add("t", token)
                    .add("i", item.targetId.toString())
                    .add("s", item.scoreValue.toString())
                if (item.gameHash.isNotBlank()) {
                    builder.add("m", item.gameHash)
                }
                if (elapsedSeconds > 0L) {
                    builder.add("o", elapsedSeconds.toString())
                }
                builder.add("v", sig)
            }
        }
        return builder.build()
    }

    private fun executeReplayCall(request: Request): ReplayCallOutcome {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                if (code >= 500 || code == 429 || code == 408 || code == 404) {
                    return ReplayCallOutcome.RETRYABLE_ERROR
                }
                if (code == 401 || code == 403) {
                    return ReplayCallOutcome.AUTH_ERROR
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return ReplayCallOutcome.RETRYABLE_ERROR
                }
                if (isServerSuccessOrAlreadyAwarded(body)) {
                    return ReplayCallOutcome.DELIVERED
                }
                val parsedError = extractServerErrorOrNull(body) ?: return ReplayCallOutcome.RETRYABLE_ERROR
                val errorLower = parsedError.lowercase()
                if (errorLower.contains("credential") || errorLower.contains("token") || errorLower.contains("login")) {
                    ReplayCallOutcome.AUTH_ERROR
                } else {
                    ReplayCallOutcome.PERMANENT_REJECTION
                }
            }
        } catch (_: IOException) {
            ReplayCallOutcome.RETRYABLE_ERROR
        } catch (_: Exception) {
            ReplayCallOutcome.RETRYABLE_ERROR
        }
    }

    private fun isServerSuccessOrAlreadyAwarded(rawBody: String): Boolean {
        return runCatching {
            val obj = json.parseToJsonElement(rawBody).jsonObject
            val success = obj["Success"]?.jsonPrimitive?.booleanOrNull == true
            if (success) return@runCatching true
            val error = obj["Error"]?.jsonPrimitive?.content.orEmpty()
            error.startsWith("User already has", ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun extractServerErrorOrNull(rawBody: String): String? {
        return runCatching {
            val obj = json.parseToJsonElement(rawBody).jsonObject
            obj["Error"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun loadEntriesLocked(sp: SharedPreferences): List<PendingOfflineSubmission> {
        val raw = sp.getString(KEY_OFFLINE_QUEUE, null)
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PendingOfflineSubmission.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun saveEntriesLocked(
        sp: SharedPreferences,
        entries: List<PendingOfflineSubmission>,
    ) {
        if (entries.isEmpty()) {
            sp.edit().remove(KEY_OFFLINE_QUEUE).apply()
        } else {
            val serialized =
                json.encodeToString(ListSerializer(PendingOfflineSubmission.serializer()), entries)
            sp.edit().putString(KEY_OFFLINE_QUEUE, serialized).apply()
        }
    }

    private fun parseFormParams(formBody: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (pair in formBody.split('&')) {
            if (pair.isEmpty()) continue
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) continue
            val k = decodeFormComponent(pair.substring(0, eqIdx))
            val v = decodeFormComponent(pair.substring(eqIdx + 1))
            result[k] = v
        }
        return result
    }

    private fun decodeFormComponent(value: String): String {
        return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            val unsigned = b.toInt() and 0xFF
            hex.append(Character.forDigit(unsigned ushr 4, 16))
            hex.append(Character.forDigit(unsigned and 0x0F, 16))
        }
        return hex.toString()
    }
}
