/*
 * CoreManager.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.ext.feature.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Process
import com.swordfish.lemuroid.common.files.safeDelete
import com.swordfish.lemuroid.common.kotlin.writeToFile
import com.swordfish.lemuroid.lib.core.CoreLibraryLocator
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import timber.log.Timber
import java.io.File
import java.io.IOException

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
        val sharedPreferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
        coreIDs.asFlow()
            .onEach { retrieveAssets(it, sharedPreferences) }
            .onEach { retrieveFile(context, it) }
            .collect()
    }

    private suspend fun retrieveFile(
        context: Context,
        coreID: CoreID,
    ) {
        withContext(Dispatchers.IO) {
            CoreLibraryLocator.findBundledLibrary(context, coreID)
        } ?: downloadCoreFromGithub(coreID)
    }

    private suspend fun retrieveAssets(
        coreID: CoreID,
        sharedPreferences: SharedPreferences,
    ) {
        CoreID.getAssetManager(coreID)
            .retrieveAssetsIfNeeded(api, directoriesManager, sharedPreferences)
    }

    private suspend fun downloadCoreFromGithub(coreID: CoreID): File =
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                val coresDirectory =
                    CoreLibraryLocator.getVersionedCoresDirectory(directoriesManager).apply {
                        mkdirs()
                    }
                val destFile = CoreLibraryLocator.getDownloadedCoreFile(directoriesManager, coreID)
                if (CoreLibraryLocator.isValidElfSharedLibrary(destFile)) {
                    return@withContext CoreLibraryLocator.ensureReadOnlyForDynamicLoad(destFile)
                }

                Timber.i("Downloading core $coreID from Arcadune GitHub releases")
                val abi = CoreLibraryLocator.selectSupportedAbi()
                val uri = CoreLibraryLocator.buildDownloadUri(abi, coreID)
                val tempFile =
                    File(
                        coresDirectory,
                        "${coreID.libretroFileName}.${Process.myPid()}.${System.nanoTime()}.tmp",
                    )

                try {
                    downloadFile(uri, tempFile)
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

    private suspend fun downloadFile(
        uri: Uri,
        destFile: File,
    ) {
        val response = api.downloadFile(uri.toString())

        if (!response.isSuccessful) {
            Timber.e("Download core response was unsuccessful (${response.code()}) for $uri")
            throw IOException("Failed to download core (HTTP ${response.code()})")
        }

        val body = response.body() ?: throw IOException("Empty core download response body")
        body.writeToFile(destFile)
    }
}
