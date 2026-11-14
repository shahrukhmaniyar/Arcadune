package com.swordfish.libretrodroid

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

    @Volatile
    var appVersion: String = "1.0.9"

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

    @JvmStatic
    fun dispatchHttpRequest(
        requestId: Long,
        url: String,
        postData: String?,
        contentType: String?,
        userAgentClause: String,
    ) {
        val job =
            scope.launch {
                val version = appVersion.ifBlank { "1.0.9" }
                val userAgent =
                    if (userAgentClause.isNotBlank()) {
                        "Arcadune/$version $userAgentClause"
                    } else {
                        "Arcadune/$version"
                    }

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
                        LibretroDroid.completeAchievementHttpRequest(
                            requestId,
                            response.code,
                            bytes,
                        )
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "RetroAchievements HTTP error for request $requestId: ${e.message}")
                    LibretroDroid.completeAchievementHttpRequest(
                        requestId,
                        CLIENT_ERROR_RETRYABLE,
                        null,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected RetroAchievements HTTP failure", e)
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
    fun cancelAllRequests() {
        val jobs = inFlightJobs.values.toList()
        inFlightJobs.clear()
        jobs.forEach { it.cancel() }
        runCatching { httpClient.dispatcher.cancelAll() }
    }
}
