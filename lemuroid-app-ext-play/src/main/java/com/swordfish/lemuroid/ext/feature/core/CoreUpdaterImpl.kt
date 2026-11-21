package com.swordfish.lemuroid.ext.feature.core

import android.content.Context
import android.os.Process
import android.util.Log
import com.google.android.play.core.ktx.hasTerminalStatus
import com.google.android.play.core.ktx.requestCancelInstall
import com.google.android.play.core.ktx.requestInstall
import com.google.android.play.core.ktx.requestProgressFlow
import com.google.android.play.core.ktx.requestSessionStates
import com.google.android.play.core.ktx.sessionId
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.swordfish.lemuroid.common.coroutines.retry
import com.swordfish.lemuroid.common.files.safeDelete
import com.swordfish.lemuroid.common.kotlin.writeToFile
import com.swordfish.lemuroid.lib.core.CoreLibraryLocator
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class CoreUpdaterImpl(
    private val directoriesManager: DirectoriesManager,
    retrofit: Retrofit,
) : CoreUpdater {
    private val api = retrofit.create(CoreUpdater.CoreManagerApi::class.java)
    private val downloadMutex = Mutex()

    override suspend fun downloadCores(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        val (bundledOrSplitCores, githubAltCores) = coreIDs.partition { it.isBundledByDefault }
        val unbundledSplitCores =
            withContext(Dispatchers.IO) {
                bundledOrSplitCores.filter { CoreLibraryLocator.findBundledLibrary(context, it) == null }
            }

        if (unbundledSplitCores.isNotEmpty()) {
            val installManager = SplitInstallManagerFactory.create(context)
            val installSession = installCores(installManager, unbundledSplitCores, context)

            try {
                cancelPendingInstalls(installManager, installSession)
            } catch (e: Throwable) {
                log("Error while canceling pending installs: ${e.message}")
            }
        }
        val bundledHavingAssets = bundledOrSplitCores - unbundledSplitCores.toSet()
        if (bundledHavingAssets.isNotEmpty()) {
            runCatching { installAssets(context, bundledHavingAssets) }
        }

        if (githubAltCores.isNotEmpty()) {
            installAssets(context, githubAltCores)
            githubAltCores.forEach { coreID ->
                if (CoreLibraryLocator.findBundledLibrary(context, coreID) == null) {
                    downloadAlternativeCoreFromGithub(coreID)
                }
            }
        }

        log("downloadCores has terminated")
    }

    private suspend fun downloadAlternativeCoreFromGithub(coreID: CoreID) =
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                val coresDirectory =
                    CoreLibraryLocator.getVersionedCoresDirectory(directoriesManager).apply { mkdirs() }
                val destFile = CoreLibraryLocator.getDownloadedCoreFile(directoriesManager, coreID)
                if (CoreLibraryLocator.isValidElfSharedLibrary(destFile)) {
                    CoreLibraryLocator.ensureReadOnlyForDynamicLoad(destFile)
                    return@withContext
                }
                val abi = CoreLibraryLocator.selectSupportedAbi()
                val uri = CoreLibraryLocator.buildDownloadUri(abi, coreID)
                val tempFile =
                    File(
                        coresDirectory,
                        "${coreID.libretroFileName}.${Process.myPid()}.${System.nanoTime()}.tmp",
                    )
                try {
                    val response = api.downloadFile(uri.toString())
                    if (!response.isSuccessful) {
                        throw IOException("Failed to download core (HTTP ${response.code()})")
                    }
                    val body = response.body() ?: throw IOException("Empty core download response body")
                    body.writeToFile(tempFile)
                    if (!CoreLibraryLocator.isValidElfSharedLibrary(tempFile)) {
                        throw IOException("Downloaded core ${coreID.coreName} is not a valid Android ELF binary")
                    }
                    CoreLibraryLocator.withCrossProcessCoreLock(directoriesManager) {
                        runCatching {
                            CoreLibraryLocator.deleteOutdatedCores(directoriesManager)
                        }
                        if (CoreLibraryLocator.isValidElfSharedLibrary(destFile)) {
                            tempFile.safeDelete()
                            CoreLibraryLocator.ensureReadOnlyForDynamicLoad(destFile)
                        } else {
                            if (destFile.exists()) {
                                runCatching { destFile.setWritable(true, true) }
                                destFile.safeDelete()
                            }
                            if (!tempFile.renameTo(destFile)) {
                                tempFile.copyTo(destFile, overwrite = true)
                                tempFile.safeDelete()
                            }
                            CoreLibraryLocator.ensureReadOnlyForDynamicLoad(destFile)
                        }
                    }
                } catch (e: Throwable) {
                    tempFile.safeDelete()
                    throw e
                }
            }
        }

    private suspend fun installCores(
        installManager: SplitInstallManager,
        coreIDs: List<CoreID>,
        context: Context,
    ): Int? {
        val installSession = requestCoresInstall(installManager, coreIDs) ?: return null

        try {
            waitForCompletion(installSession, installManager)
        } catch (e: Throwable) {
            log("Error while waiting for core install: ${e.message}")
        }

        try {
            installAssets(context, coreIDs)
        } catch (e: Throwable) {
            log("Error while installing assets: ${e.message}")
        }

        return installSession
    }

    private fun computePlayModuleName(it: CoreID) = "lemuroid_core_${it.coreName}"

    private suspend fun waitForCompletion(
        sessionId: Int,
        installManager: SplitInstallManager,
    ) {
        installManager.requestProgressFlow()
            .filter { it.sessionId() == sessionId }
            .onEach { log("Session status for id $sessionId updated to $it") }
            .takeWhile { !it.hasTerminalStatus }
            .catch { log("Error while waitingForCompletion $it") }
            .onCompletion { log("Terminating waitingForCompletion for session: $sessionId") }
            .collect()
    }

    private suspend fun requestCoresInstall(
        installManager: SplitInstallManager,
        coreIDs: List<CoreID>,
    ): Int? {
        val modulesToInstall =
            coreIDs
                .map { computePlayModuleName(it) }
                .filter { it !in installManager.installedModules }

        if (modulesToInstall.isEmpty()) {
            return null
        }

        log("Starting request install for the following modules: $modulesToInstall")

        val result =
            retry(RETRY_ATTEMPTS, RETRY_DELAY) {
                installManager.requestInstall(modulesToInstall)
            }

        return result.getOrNull()
    }

    private suspend fun installAssets(
        context: Context,
        coreIDs: List<CoreID>,
    ) {
        val sharedPreferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
        coreIDs.asFlow()
            .map { CoreID.getAssetManager(it) }
            .onEach { it.retrieveAssetsIfNeeded(api, directoriesManager, sharedPreferences) }
            .collect()
    }

    private suspend fun cancelPendingInstalls(
        installManager: SplitInstallManager,
        currentSession: Int?,
    ) {
        log("Starting cancelPendingInstalls")

        val pending =
            retry(RETRY_ATTEMPTS, RETRY_DELAY) { installManager.requestSessionStates() }
                .getOrNull() ?: return

        pending
            .asFlow()
            .filter { !it.hasTerminalStatus && it.sessionId != currentSession }
            .onEach { cancelPendingInstall(installManager, it.sessionId) }
            .catch { log("Failed to cancel pending installs: ${it.message}") }
            .collect()

        log("Terminating cancelPendingInstalls")
    }

    private suspend fun cancelPendingInstall(
        installManager: SplitInstallManager,
        sessionId: Int,
    ) {
        try {
            installManager.requestCancelInstall(sessionId)
        } catch (e: Throwable) {
            log("Failed to cancel pending install for session: $sessionId")
        }
        log("Terminated cancel for session: $sessionId")
    }

    private fun log(message: String) {
        if (VERBOSE) {
            Log.i(TAG_LOG, message)
        }
    }

    companion object {
        // Sadly dynamic features need to be tested directly on GooglePlay. Let's leave logging on.
        private const val TAG_LOG = "CoreUpdaterImpl"
        private const val VERBOSE = true
        private const val RETRY_ATTEMPTS = 5
        private val RETRY_DELAY = 2.seconds
    }
}
