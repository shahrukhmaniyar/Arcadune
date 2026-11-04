package com.swordfish.lemuroid.app.shared.update

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Updates Arcadune from its GitHub releases: finds the latest release, downloads the same kind of
 * APK as the one installed (the small one, or the full one with every engine), checks it against
 * the fingerprint GitHub publishes, and hands it to Android's installer, which asks the player.
 *
 * Only the "free" builds update themselves: the Play builds are updated by the Play Store.
 */
object AppUpdater {
    sealed interface State {
        data object Idle : State

        data object Checking : State

        data object UpToDate : State

        data class Available(val release: Release) : State

        data class Downloading(val release: Release, val progress: Float) : State

        data class ReadyToInstall(val release: Release, val file: File) : State

        data class Installing(val release: Release) : State

        data class Failed(val reason: Reason, val release: Release? = null) : State
    }

    enum class Reason { NO_CONNECTION, DOWNLOAD_BROKEN, INSTALL_FAILED }

    data class Release(
        val version: String,
        val title: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
        val sha256: String?,
    )

    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/shahrukhmaniyar/Arcadune/releases/latest"
    private const val PREF_LAST_CHECK = "arcadune_update_last_check"
    private const val AUTOMATIC_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    val isSupported: Boolean = BuildConfig.FLAVOR_opensource == "free"

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** The APK of the release that matches this build. */
    private val apkName = if (BuildConfig.FLAVOR_cores == "bundle") "Arcadune-full.apk" else "Arcadune.apk"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Checks at most once a day, quietly: a failure leaves the state as it was. */
    fun checkAutomatically(context: Context) {
        if (!isSupported || state.value != State.Idle) return
        val preferences = SharedPreferencesHelper.getSharedPreferences(context)
        val now = System.currentTimeMillis()
        if (now - preferences.getLong(PREF_LAST_CHECK, 0) < AUTOMATIC_CHECK_INTERVAL_MS) return
        preferences.edit().putLong(PREF_LAST_CHECK, now).apply()
        launch {
            cleanUpDownloads(context)
            val release = runCatching { fetchLatestRelease() }.getOrNull() ?: return@launch
            if (isNewer(release.version, currentVersion)) mutableState.value = State.Available(release)
        }
    }

    fun check(context: Context) {
        if (!isSupported) return
        when (state.value) {
            State.Checking, is State.Downloading, is State.Installing -> return
            else -> Unit
        }
        mutableState.value = State.Checking
        SharedPreferencesHelper.getSharedPreferences(context).edit()
            .putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
        launch {
            cleanUpDownloads(context)
            mutableState.value =
                try {
                    val release = fetchLatestRelease()
                    if (isNewer(release.version, currentVersion)) State.Available(release) else State.UpToDate
                } catch (e: Exception) {
                    Timber.w(e, "Could not check for updates")
                    State.Failed(Reason.NO_CONNECTION)
                }
        }
    }

    fun download(
        context: Context,
        release: Release,
    ) {
        if (state.value is State.Downloading) return
        mutableState.value = State.Downloading(release, 0f)
        launch {
            val file = File(updatesFolder(context), "Arcadune-${release.version}.apk")
            mutableState.value =
                try {
                    downloadTo(release, file)
                    State.ReadyToInstall(release, file)
                } catch (e: BrokenDownloadException) {
                    Timber.w(e, "The downloaded update is broken")
                    file.delete()
                    State.Failed(Reason.DOWNLOAD_BROKEN, release)
                } catch (e: Exception) {
                    Timber.w(e, "Could not download the update")
                    file.delete()
                    if (coroutineContext.isActive) State.Failed(Reason.NO_CONNECTION, release) else State.Available(release)
                }
        }
    }

    fun cancelDownload() {
        val downloading = state.value as? State.Downloading ?: return
        job?.cancel()
        mutableState.value = State.Available(downloading.release)
    }

    /**
     * Asks Android to install the downloaded update. The first time, Android needs the player to
     * allow Arcadune to install apps: this opens that page, and the player taps Install again after.
     */
    fun install(
        context: Context,
        ready: State.ReadyToInstall,
    ) {
        if (!canInstall(context)) {
            waitingForPermission = ready
            val intent =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            // From a screen, on top of Arcadune, so Back returns to it.
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        waitingForPermission = null
        lastReady = ready
        mutableState.value = State.Installing(ready.release)
        val appContext = context.applicationContext
        launch {
            try {
                writeToInstaller(appContext, ready.file)
            } catch (e: Exception) {
                Timber.e(e, "Could not start the update install")
                mutableState.value = State.Failed(Reason.INSTALL_FAILED, ready.release)
            }
        }
    }

    private var waitingForPermission: State.ReadyToInstall? = null

    private fun canInstall(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Back from Android's "Install unknown apps" page: goes on with the install if it was allowed. */
    fun onResume(context: Context) {
        val ready = waitingForPermission ?: return
        if (state.value != ready) {
            waitingForPermission = null
            return
        }
        if (canInstall(context)) install(context, ready)
    }

    /** Called by [UpdateInstallReceiver] when the install did not happen. */
    internal fun onInstallNotDone(cancelledByPlayer: Boolean) {
        val ready = lastReady
        mutableState.value =
            when {
                ready == null || !ready.file.exists() -> State.Idle
                // The player closed the installer: they can tap Install again.
                cancelledByPlayer -> ready
                else -> State.Failed(Reason.INSTALL_FAILED, ready.release)
            }
    }

    private var lastReady: State.ReadyToInstall? = null

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        job = scope.launch(block = block)
    }

    private fun fetchLatestRelease(): Release {
        val request =
            Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub answered ${response.code}")
            val json = JSONObject(response.body!!.string())
            val assets = json.getJSONArray("assets")
            val asset =
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name") == apkName }
                    ?: error("The latest release has no $apkName")
            return Release(
                version = json.getString("tag_name").removePrefix("v"),
                title = json.optString("name").ifBlank { json.getString("tag_name") },
                notes = json.optString("body"),
                apkUrl = asset.getString("browser_download_url"),
                apkSize = asset.optLong("size"),
                sha256 = asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
            )
        }
    }

    private suspend fun downloadTo(
        release: Release,
        file: File,
    ) {
        val request = Request.Builder().url(release.apkUrl).build()
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download answered ${response.code}")
            val body = response.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            var done = 0L
            var shownProgress = -1
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (!coroutineContext.isActive) throw InterruptedException("Download cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        val progress = if (total > 0) (done * 100 / total).toInt() else 0
                        if (progress != shownProgress) {
                            shownProgress = progress
                            mutableState.value = State.Downloading(release, progress / 100f)
                        }
                    }
                }
            }
            if (release.apkSize > 0 && done != release.apkSize) {
                throw BrokenDownloadException("Got $done bytes of ${release.apkSize}")
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && !hash.equals(release.sha256, ignoreCase = true)) {
            throw BrokenDownloadException("Fingerprint does not match")
        }
    }

    private fun writeToInstaller(
        context: Context,
        file: File,
    ) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(file.length())
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    input.copyTo(output, 64 * 1024)
                    session.fsync(output)
                }
            }
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback =
                PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, UpdateInstallReceiver::class.java),
                    flags,
                )
            session.commit(callback.intentSender)
        }
    }

    /** Removes downloaded updates once they are installed (or out of date). */
    private fun cleanUpDownloads(context: Context) {
        val pending = (state.value as? State.ReadyToInstall)?.file
        updatesFolder(context).listFiles()?.filter { it != pending }?.forEach { it.delete() }
    }

    private fun updatesFolder(context: Context) = File(context.cacheDir, "updates").apply { mkdirs() }

    /** True when [candidate] (like "1.0.10") is a later version than [current] (like "1.0.9"). */
    fun isNewer(
        candidate: String,
        current: String,
    ): Boolean {
        fun parts(version: String) = version.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private class BrokenDownloadException(message: String) : Exception(message)
}
