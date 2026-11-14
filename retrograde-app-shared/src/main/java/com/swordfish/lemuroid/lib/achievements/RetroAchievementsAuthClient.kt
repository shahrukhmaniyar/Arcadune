package com.swordfish.lemuroid.lib.achievements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class RetroAchievementsAuthException(message: String) : Exception(message)

object RetroAchievementsAuthClient {
    private const val DOREQUEST_URL = "https://retroachievements.org/dorequest.php"
    private const val USER_AGENT = "Arcadune/1.0.9 (Android)"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Serializable
    data class ConnectLoginResponse(
        @SerialName("Success") val success: Boolean = false,
        @SerialName("User") val user: String? = null,
        @SerialName("Token") val token: String? = null,
        @SerialName("Score") val score: Int = 0,
        @SerialName("SoftcoreScore") val softcoreScore: Int = 0,
        @SerialName("Messages") val messages: Int = 0,
        @SerialName("Error") val error: String? = null,
    )

    suspend fun loginWithPassword(
        username: String,
        password: String,
    ): Result<RetroAchievementsAccount> =
        withContext(Dispatchers.IO) {
            val trimmedUser = username.trim()
            if (trimmedUser.isEmpty() || password.isEmpty()) {
                return@withContext Result.failure(
                    RetroAchievementsAuthException("Enter both your username and password."),
                )
            }

            val body =
                FormBody.Builder()
                    .add("r", "login2")
                    .add("u", trimmedUser)
                    .add("p", password)
                    .build()

            executeLoginRequest(trimmedUser, body)
        }

    suspend fun verifyAndRefreshAccount(
        username: String,
        token: String,
    ): Result<RetroAchievementsAccount> =
        withContext(Dispatchers.IO) {
            val trimmedUser = username.trim()
            if (trimmedUser.isEmpty() || token.isBlank()) {
                return@withContext Result.failure(
                    RetroAchievementsAuthException("Missing account session token."),
                )
            }

            val body =
                FormBody.Builder()
                    .add("r", "login2")
                    .add("u", trimmedUser)
                    .add("t", token)
                    .build()

            executeLoginRequest(trimmedUser, body)
        }

    internal fun parseLoginPayload(
        fallbackUsername: String,
        rawJson: String,
    ): Result<RetroAchievementsAccount> {
        val parsed =
            runCatching { json.decodeFromString(ConnectLoginResponse.serializer(), rawJson) }
                .getOrElse {
                    return Result.failure(
                        RetroAchievementsAuthException("Unexpected response from RetroAchievements."),
                    )
                }

        if (!parsed.success || parsed.token.isNullOrBlank()) {
            val reason =
                parsed.error?.takeIf { it.isNotBlank() }
                    ?: "Invalid username or password."
            return Result.failure(RetroAchievementsAuthException(reason))
        }

        val resolvedUser = parsed.user?.takeIf { it.isNotBlank() } ?: fallbackUsername
        return Result.success(
            RetroAchievementsAccount(
                username = resolvedUser,
                displayName = resolvedUser,
                token = parsed.token,
                avatarUrl = RetroAchievementsPreferences.defaultAvatarUrl(resolvedUser),
                score = parsed.score,
                softcoreScore = parsed.softcoreScore,
            ),
        )
    }

    private fun executeLoginRequest(
        fallbackUsername: String,
        formBody: FormBody,
    ): Result<RetroAchievementsAccount> {
        val request =
            Request.Builder()
                .url(DOREQUEST_URL)
                .header("User-Agent", USER_AGENT)
                .post(formBody)
                .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (payload.isNotBlank()) {
                    parseLoginPayload(fallbackUsername, payload)
                } else if (!response.isSuccessful) {
                    Result.failure(
                        RetroAchievementsAuthException(
                            "RetroAchievements server returned HTTP ${response.code}.",
                        ),
                    )
                } else {
                    Result.failure(
                        RetroAchievementsAuthException("Empty response from RetroAchievements."),
                    )
                }
            }
        } catch (e: IOException) {
            Result.failure(
                RetroAchievementsAuthException("Check your internet connection and try again."),
            )
        } catch (e: Exception) {
            Result.failure(
                RetroAchievementsAuthException(e.message ?: "Unable to sign in right now."),
            )
        }
    }
}
