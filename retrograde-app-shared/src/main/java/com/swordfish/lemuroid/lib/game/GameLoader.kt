/*
 * GameLoader.kt
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

package com.swordfish.lemuroid.lib.game

import android.content.Context
import android.os.Build
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoreLibraryLocator
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.CoreVariable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.core.assetsmanager.PPSSPPAssetsManager
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.LemuroidLibrary
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.migration.DesmumeMigrationHandler
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.SavesCoherencyEngine
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.RomFiles
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File

class GameLoader(
    private val lemuroidLibrary: LemuroidLibrary,
    private val statesManager: StatesManager,
    private val savesManager: SavesManager,
    private val coreVariablesManager: CoreVariablesManager,
    private val retrogradeDatabase: RetrogradeDatabase,
    private val savesCoherencyEngine: SavesCoherencyEngine,
    private val directoriesManager: DirectoriesManager,
    private val biosManager: BiosManager,
    private val desmumeMigrationHandler: DesmumeMigrationHandler,
    private val coreUpdater: CoreUpdater? = null,
) {
    sealed class LoadingState {
        object LoadingCore : LoadingState()

        object LoadingGame : LoadingState()

        class Ready(val gameData: GameData) : LoadingState()
    }

    fun load(
        appContext: Context,
        game: Game,
        loadSave: Boolean,
        systemCoreConfig: SystemCoreConfig,
        directLoad: Boolean,
    ): Flow<LoadingState> =
        flow {
            try {
                emit(LoadingState.LoadingCore)

                val system = GameSystem.findById(game.systemId)

                if (!isArchitectureSupported(systemCoreConfig)) {
                    throw GameLoaderException(GameLoaderError.UnsupportedArchitecture)
                }

                val existingLibrary = findLibrary(appContext, systemCoreConfig.coreID)
                val needsPpssppAssets =
                    systemCoreConfig.coreID == CoreID.PPSSPP &&
                        !File(
                            directoriesManager.getSystemDirectory(),
                            PPSSPPAssetsManager.PPSSPP_ASSETS_FOLDER_NAME,
                        ).exists()
                if (existingLibrary == null || needsPpssppAssets) {
                    runCatching {
                        coreUpdater?.downloadCores(appContext, listOf(systemCoreConfig.coreID))
                    }.onFailure { error ->
                        if (existingLibrary == null) {
                            Timber.e(error, "Failed to download on-demand core ${systemCoreConfig.coreID}")
                            throw GameLoaderException(GameLoaderError.LoadCore)
                        }
                    }
                }

                val coreLibrary =
                    runCatching {
                        val libraryFile = findLibrary(appContext, systemCoreConfig.coreID)!!
                        if (libraryFile.absolutePath.startsWith(appContext.filesDir.absolutePath)) {
                            CoreLibraryLocator.ensureReadOnlyForDynamicLoad(libraryFile)
                        }
                        libraryFile.absolutePath
                    }.getOrElse { throw GameLoaderException(GameLoaderError.LoadCore) }

                emit(LoadingState.LoadingGame)

                val missingBiosFiles = biosManager.getMissingBiosFiles(systemCoreConfig, game)
                if (missingBiosFiles.isNotEmpty()) {
                    throw GameLoaderException(GameLoaderError.MissingBiosFiles(missingBiosFiles))
                }

                // Everything below is independent of the rest, so it is prepared at the same time
                // instead of one after the other (the load time is the slowest of them, not the sum).
                val prepared =
                    coroutineScope {
                        val gameFilesJob =
                            async {
                                runCatching {
                                    val useVFS = systemCoreConfig.supportsLibretroVFS && directLoad
                                    val dataFiles = retrogradeDatabase.dataFileDao().selectDataFilesForGame(game.id)
                                    lemuroidLibrary.getGameFiles(game, dataFiles, useVFS)
                                }.getOrElse { throw it }
                            }

                        val saveRamJob =
                            async {
                                runCatching {
                                    val data = savesManager.getSaveRAM(game, systemCoreConfig)
                                    desmumeMigrationHandler.resolveSaveData(game, systemCoreConfig.coreID, data)
                                }.getOrElse { throw GameLoaderException(GameLoaderError.Saves) }
                            }

                        // The automatic save is read right away; whether it is still valid (not older
                        // than the battery save) is decided once the battery save is known.
                        val autoSaveJob =
                            async {
                                if (systemCoreConfig.statesSupported && systemCoreConfig.autoSaveSupported && loadSave) {
                                    runCatching {
                                        statesManager.getAutoSave(game, systemCoreConfig.coreID)
                                            ?.takeIf { it.metadata.version == systemCoreConfig.statesVersion }
                                    }.getOrElse { throw GameLoaderException(GameLoaderError.Saves) }
                                } else {
                                    null
                                }
                            }

                        val coreVariablesJob =
                            async {
                                coreVariablesManager.getOptionsForCore(system.id, systemCoreConfig, game)
                                    .toTypedArray()
                            }

                        val saveRAM = saveRamJob.await()
                        val keepAutoSave =
                            runCatching {
                                !savesCoherencyEngine.shouldDiscardAutoSaveState(
                                    game,
                                    systemCoreConfig.coreID,
                                    saveRAM.timestampOverride,
                                )
                            }.getOrElse { throw GameLoaderException(GameLoaderError.Saves) }
                        val quickSaveData =
                            if (keepAutoSave) {
                                autoSaveJob.await()
                            } else {
                                autoSaveJob.cancel()
                                null
                            }

                        PreparedGame(gameFilesJob.await(), saveRAM.data, quickSaveData, coreVariablesJob.await())
                    }
                val gameFiles = prepared.gameFiles
                val saveRAMData = prepared.saveRAMData
                val quickSaveData = prepared.quickSaveData
                val coreVariables = prepared.coreVariables

                val systemDirectory = directoriesManager.getSystemDirectory()
                val savesDirectory = directoriesManager.getSavesDirectory()

                emit(
                    LoadingState.Ready(
                        GameData(
                            game,
                            coreLibrary,
                            gameFiles,
                            quickSaveData,
                            saveRAMData,
                            coreVariables,
                            systemDirectory,
                            savesDirectory,
                        ),
                    ),
                )
            } catch (e: GameLoaderException) {
                Timber.e(e, "Error while preparing game")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error while preparing game")
                throw GameLoaderException(GameLoaderError.Generic)
            }
        }

    private fun isArchitectureSupported(systemCoreConfig: SystemCoreConfig): Boolean {
        if (!CoreLibraryLocator.isDeviceAbiHosted()) {
            return false
        }
        val supportedOnlyArchitectures = systemCoreConfig.supportedOnlyArchitectures ?: return true
        return Build.SUPPORTED_ABIS.toSet().intersect(supportedOnlyArchitectures).isNotEmpty()
    }

    private fun findLibrary(
        context: Context,
        coreID: CoreID,
    ): File? {
        return CoreLibraryLocator.findAvailableLibrary(context, directoriesManager, coreID)
    }

    @Suppress("ArrayInDataClass")
    private data class PreparedGame(
        val gameFiles: RomFiles,
        val saveRAMData: ByteArray?,
        val quickSaveData: SaveState?,
        val coreVariables: Array<CoreVariable>,
    )

    @Suppress("ArrayInDataClass")
    data class GameData(
        val game: Game,
        val coreLibrary: String,
        val gameFiles: RomFiles,
        val quickSaveData: SaveState?,
        val saveRAMData: ByteArray?,
        val coreVariables: Array<CoreVariable>,
        val systemDirectory: File,
        val savesDirectory: File,
    )
}
