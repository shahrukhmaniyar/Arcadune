package com.swordfish.libretrodroid

import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object AchievementsBridge {
    private const val TAG = "AchievementsBridge"
    private const val CLIENT_ERROR_RETRYABLE = -2

    interface HttpTransferObserver {
        fun onRetryableFailure(
            url: String,
            postData: String?,
            gameHash: String,
            userAgent: String,
        )

        fun onCompletedResponse(
            url: String,
            postData: String?,
            httpStatusCode: Int,
            responseBodyUtf8: String?,
            gameHash: String,
            userAgent: String,
        )
    }

    interface ChdSectorReader {
        fun openTrack(
            path: String,
            adoptedFd: Int,
            track: Int,
        ): Long

        fun readSector(
            handle: Long,
            sector: Int,
            requestedBytes: Int,
        ): ByteArray?

        fun firstTrackSector(handle: Long): Int

        fun closeTrack(handle: Long)

        fun closeAll()
    }

    @Volatile
    var appVersion: String = "1.0.9"

    @Volatile
    var httpTransferObserver: HttpTransferObserver? = null

    @Volatile
    var chdSectorReader: ChdSectorReader? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightJobs = ConcurrentHashMap<Long, Job>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val eventsFlow =
        MutableSharedFlow<AchievementEvent>(
            replay = 0,
            extraBufferCapacity = 128,
        )

    fun events(): Flow<AchievementEvent> = eventsFlow.asSharedFlow()

    fun redactLogText(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        return raw
            .replace(Regex("(?<=^|[?&\\s])t=[^&\\s\"]+"), "t=[REDACTED]")
            .replace(Regex("(?<=^|[?&\\s])p=[^&\\s\"]+"), "p=[REDACTED]")
            .replace(Regex("\"Token\"\\s*:\\s*\"[^\"]+\""), "\"Token\":\"[REDACTED]\"")
    }

    fun resolveUserAgent(
        fullOrClauseUserAgent: String,
        fallbackAppVersion: String = appVersion,
    ): String {
        val trimmed = fullOrClauseUserAgent.trim()
        if (trimmed.startsWith("Arcadune/")) {
            return trimmed
        }
        val version = fallbackAppVersion.ifBlank { "1.0.9" }
        val osRel = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() }?.let { "Android $it" } ?: "Android"
        return if (trimmed.isNotBlank()) {
            "Arcadune/$version ($osRel) $trimmed"
        } else {
            "Arcadune/$version ($osRel)"
        }
    }

    @JvmStatic
    fun dispatchHttpRequest(
        requestId: Long,
        url: String,
        postData: String?,
        contentType: String?,
        fullUserAgent: String,
        gameHash: String,
    ) {
        val job =
            scope.launch {
                val userAgent = resolveUserAgent(fullUserAgent)

                val builder =
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)

                if (postData != null) {
                    val mediaType =
                        (contentType?.takeIf { it.isNotBlank() } ?: "application/x-www-form-urlencoded")
                            .toMediaTypeOrNull()
                    builder.post(postData.toRequestBody(mediaType))
                } else {
                    builder.get()
                }

                val call = httpClient.newCall(builder.build())
                try {
                    call.execute().use { response ->
                        val bytes = response.body?.bytes()
                        val code = response.code
                        if (code >= 500 || code == 429) {
                            runCatching {
                                httpTransferObserver?.onRetryableFailure(url, postData, gameHash, userAgent)
                            }
                            LibretroDroid.completeAchievementHttpRequest(
                                requestId,
                                CLIENT_ERROR_RETRYABLE,
                                null,
                            )
                        } else {
                            runCatching {
                                httpTransferObserver?.onCompletedResponse(
                                    url = url,
                                    postData = postData,
                                    httpStatusCode = code,
                                    responseBodyUtf8 = bytes?.toString(Charsets.UTF_8),
                                    gameHash = gameHash,
                                    userAgent = userAgent,
                                )
                            }
                            LibretroDroid.completeAchievementHttpRequest(
                                requestId,
                                code,
                                bytes,
                            )
                        }
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "RetroAchievements HTTP error for request $requestId: ${redactLogText(e.message)}")
                    runCatching {
                        httpTransferObserver?.onRetryableFailure(url, postData, gameHash, userAgent)
                    }
                    LibretroDroid.completeAchievementHttpRequest(
                        requestId,
                        CLIENT_ERROR_RETRYABLE,
                        null,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected RetroAchievements HTTP failure: ${redactLogText(e.message)}")
                    runCatching {
                        httpTransferObserver?.onRetryableFailure(url, postData, gameHash, userAgent)
                    }
                    LibretroDroid.completeAchievementHttpRequest(
                        requestId,
                        CLIENT_ERROR_RETRYABLE,
                        null,
                    )
                } finally {
                    inFlightJobs.remove(requestId)
                }
            }
        inFlightJobs[requestId] = job
    }

    @JvmStatic
    fun onNativeEventsAvailable() {
        val drained = runCatching { LibretroDroid.pollAchievementEvents() }.getOrNull() ?: return
        for (event in drained) {
            eventsFlow.tryEmit(event)
        }
    }

    @JvmStatic
    fun openChdTrack(
        path: String,
        adoptedFd: Int,
        track: Int,
    ): Long {
        val reader = chdSectorReader
        if (reader == null) {
            if (adoptedFd >= 0) {
                runCatching { ParcelFileDescriptor.adoptFd(adoptedFd).close() }
            }
            return 0L
        }
        return try {
            reader.openTrack(path, adoptedFd, track)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to open CHD track ($track): ${t.message}")
            0L
        }
    }

    @JvmStatic
    fun readChdSector(
        handle: Long,
        sector: Int,
        requestedBytes: Int,
    ): ByteArray? {
        return try {
            chdSectorReader?.readSector(handle, sector, requestedBytes)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read CHD sector $sector: ${t.message}")
            null
        }
    }

    @JvmStatic
    fun firstChdTrackSector(handle: Long): Int {
        return try {
            chdSectorReader?.firstTrackSector(handle) ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    @JvmStatic
    fun closeChdTrack(handle: Long) {
        try {
            chdSectorReader?.closeTrack(handle)
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun cancelAllRequests() {
        val jobs = inFlightJobs.values.toList()
        inFlightJobs.clear()
        jobs.forEach { it.cancel() }
        runCatching { httpClient.dispatcher.cancelAll() }
        runCatching { chdSectorReader?.closeAll() }
    }
}
