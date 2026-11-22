package com.swordfish.lemuroid.lib.core.assetsmanager

import android.content.SharedPreferences
import android.net.Uri
import com.swordfish.lemuroid.lib.core.CoreFingerprints
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.DownloadVerifier
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

class PPSSPPAssetsManager : CoreID.AssetsManager {
    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {
        getAssetsDirectory(directoriesManager).deleteRecursively()
    }

    override suspend fun retrieveAssetsIfNeeded(
        coreUpdaterApi: CoreUpdater.CoreManagerApi,
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ) {
        if (!updatedRequested(directoriesManager, sharedPreferences)) {
            return
        }

        // The zip is saved first and checked against its published fingerprint, then unpacked.
        val download = File(directoriesManager.getSystemDirectory(), "ppsspp-download.zip.part")
        try {
            val response = coreUpdaterApi.downloadFile(PPSSPP_ASSETS_URL.toString())
            if (!response.isSuccessful) throw java.io.IOException("PSP files download answered ${response.code()}")
            withContext(Dispatchers.IO) {
                val body = response.body() ?: throw java.io.IOException("Empty PSP files download")
                body.use { input -> download.outputStream().use { input.copyTo(it) } }
                DownloadVerifier.verify(download, CoreFingerprints.PPSSPP_ASSETS_SHA256, "PSP files")
            }
            handleSuccess(directoriesManager, download, sharedPreferences)
        } catch (e: Throwable) {
            Timber.e(e, "Could not get the PSP files")
            getAssetsDirectory(directoriesManager).deleteRecursively()
        } finally {
            download.delete()
        }
    }

    private suspend fun handleSuccess(
        directoriesManager: DirectoriesManager,
        zipFile: File,
        sharedPreferences: SharedPreferences,
    ) = withContext(Dispatchers.IO) {
        val coreAssetsDirectory = getAssetsDirectory(directoriesManager)
        coreAssetsDirectory.deleteRecursively()
        coreAssetsDirectory.mkdirs()

        val canonicalRoot = coreAssetsDirectory.canonicalPath + File.separator
        ZipInputStream(zipFile.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                Timber.d("Writing file: ${entry.name}")
                val destFile =
                    File(
                        coreAssetsDirectory,
                        entry.name,
                    )
                val canonicalDest = destFile.canonicalPath
                if (canonicalDest != coreAssetsDirectory.canonicalPath &&
                    !canonicalDest.startsWith(canonicalRoot)
                ) {
                    throw SecurityException("Blocked zip-slip entry in PPSSPP assets: ${entry.name}")
                }
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out ->
                        zipInputStream.copyTo(out)
                    }
                }
            }
        }

        sharedPreferences.edit()
            .putString(PPSSPP_ASSETS_VERSION_KEY, PPSSPP_ASSETS_VERSION)
            .commit()
    }

    private suspend fun updatedRequested(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val directoryExists = getAssetsDirectory(directoriesManager).exists()

            val currentVersion = sharedPreferences.getString(PPSSPP_ASSETS_VERSION_KEY, "none")
            val hasCurrentVersion = currentVersion == PPSSPP_ASSETS_VERSION

            !directoryExists || !hasCurrentVersion
        }

    private suspend fun getAssetsDirectory(directoriesManager: DirectoriesManager): File {
        return withContext(Dispatchers.IO) {
            File(directoriesManager.getSystemDirectory(), PPSSPP_ASSETS_FOLDER_NAME)
        }
    }

    companion object {
        // Version of Arcadune's hosted PSP files (the "psp-files-<version>" release on GitHub).
        const val PPSSPP_ASSETS_VERSION = "1"

        // Hosted on Arcadune's own GitHub releases. The file is the original PPSSPP assets bundle.
        val PPSSPP_ASSETS_URL: Uri =
            Uri.parse("https://github.com/shahrukhmaniyar/Arcadune/")
                .buildUpon()
                .appendEncodedPath("releases/download/psp-files-$PPSSPP_ASSETS_VERSION/ppsspp.zip")
                .build()

        const val PPSSPP_ASSETS_VERSION_KEY = "ppsspp_assets_version_key"

        const val PPSSPP_ASSETS_FOLDER_NAME = "PPSSPP"
    }
}
