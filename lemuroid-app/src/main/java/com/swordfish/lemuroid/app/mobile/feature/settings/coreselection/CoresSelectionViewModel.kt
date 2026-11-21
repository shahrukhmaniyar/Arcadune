package com.swordfish.lemuroid.app.mobile.feature.settings.coreselection

import android.content.Context
import android.os.Build
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.lib.core.CoreLibraryLocator
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class CoresSelectionViewModel(
    private val appContext: Context,
    private val coresSelection: CoresSelection,
    private val coreUpdater: CoreUpdater,
    private val directoriesManager: DirectoriesManager,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val coresSelection: CoresSelection,
        private val coreUpdater: CoreUpdater,
        private val directoriesManager: DirectoriesManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CoresSelectionViewModel(
                appContext = appContext,
                coresSelection = coresSelection,
                coreUpdater = coreUpdater,
                directoriesManager = directoriesManager,
            ) as T
        }
    }

    sealed interface CoreInstallStatus {
        data object Bundled : CoreInstallStatus

        data class Downloaded(val sizeBytes: Long) : CoreInstallStatus

        data object NotDownloaded : CoreInstallStatus

        data object Downloading : CoreInstallStatus

        data class Failed(val message: String) : CoreInstallStatus
    }

    data class CoreOptionItem(
        val config: SystemCoreConfig,
        val isDefault: Boolean,
        val isSelected: Boolean,
        val isSelectedAnywhere: Boolean,
        val isArchitectureSupported: Boolean,
        val status: CoreInstallStatus,
    ) {
        val canDeleteCached: Boolean
            get() =
                !config.coreID.isBundledByDefault &&
                    !isSelectedAnywhere &&
                    status is CoreInstallStatus.Downloaded
    }

    data class SystemCoreRow(
        val system: GameSystem,
        val selectedOption: CoreOptionItem,
        val options: List<CoreOptionItem>,
    )

    data class UiState(
        val rows: List<SystemCoreRow> = emptyList(),
        val downloadedCoreCount: Int = 0,
        val downloadedBytesFormatted: String = "",
        val unusedCachedCount: Int = 0,
    )

    private val downloadingCores = MutableStateFlow<Set<CoreID>>(emptySet())
    private val failedCores = MutableStateFlow<Map<CoreID, String>>(emptyMap())
    private val diskRefreshTick = MutableStateFlow(0L)

    private val _downloadErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val downloadErrorEvents: SharedFlow<String> = _downloadErrorEvents.asSharedFlow()

    val uiState: StateFlow<UiState> =
        combine(
            coresSelection.getSelectedCores(),
            downloadingCores,
            failedCores,
            diskRefreshTick,
        ) { selectedCores, downloading, failed, _ ->
            buildUiState(selectedCores, downloading, failed)
        }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun buildUiState(
        selectedCores: List<CoresSelection.SelectedCore>,
        downloading: Set<CoreID>,
        failed: Map<CoreID, String>,
    ): UiState {
        val deviceAbis = Build.SUPPORTED_ABIS.toSet()
        val hostedAbiAvailable = CoreLibraryLocator.isDeviceAbiHosted()
        val downloadedAlternateSizes = mutableMapOf<CoreID, Long>()
        val activeSelectedCoreIds = selectedCores.map { it.coreConfig.coreID }.toSet()

        val rows =
            selectedCores
                .map { entry ->
                    val options =
                        entry.system.systemCoreConfigs.mapIndexed { index, config ->
                            val coreId = config.coreID
                            val archSupported =
                                hostedAbiAvailable &&
                                    (
                                        config.supportedOnlyArchitectures?.let {
                                            deviceAbis.intersect(it).isNotEmpty()
                                        } ?: true
                                    )
                            val bundledFile = CoreLibraryLocator.findBundledLibrary(appContext, coreId)
                            val downloadedFile = CoreLibraryLocator.findDownloadedLibrary(directoriesManager, coreId)
                            if (!coreId.isBundledByDefault && bundledFile == null && downloadedFile != null) {
                                downloadedAlternateSizes[coreId] = downloadedFile.length()
                            }

                            val status =
                                when {
                                    coreId in downloading -> CoreInstallStatus.Downloading
                                    bundledFile != null -> CoreInstallStatus.Bundled
                                    downloadedFile != null -> CoreInstallStatus.Downloaded(downloadedFile.length())
                                    coreId in failed -> CoreInstallStatus.Failed(failed.getValue(coreId))
                                    else -> CoreInstallStatus.NotDownloaded
                                }

                            CoreOptionItem(
                                config = config,
                                isDefault = index == 0,
                                isSelected = config.coreID == entry.coreConfig.coreID,
                                isSelectedAnywhere = coreId in activeSelectedCoreIds,
                                isArchitectureSupported = archSupported,
                                status = status,
                            )
                        }
                    val selectedOption = options.firstOrNull { it.isSelected } ?: options.first()
                    SystemCoreRow(
                        system = entry.system,
                        selectedOption = selectedOption,
                        options = options,
                    )
                }
                .sortedBy { appContext.getString(it.system.titleResId).lowercase() }

        val totalBytes = downloadedAlternateSizes.values.sum()
        val unusedCount = downloadedAlternateSizes.keys.count { it !in activeSelectedCoreIds }
        return UiState(
            rows = rows,
            downloadedCoreCount = downloadedAlternateSizes.size,
            downloadedBytesFormatted =
                if (totalBytes > 0L) {
                    Formatter.formatShortFileSize(appContext, totalBytes)
                } else {
                    ""
                },
            unusedCachedCount = unusedCount,
        )
    }

    fun selectCore(
        system: GameSystem,
        config: SystemCoreConfig,
    ) {
        val coreId = config.coreID
        viewModelScope.launch {
            coresSelection.updateCoreConfigForSystem(system, coreId)
            val available =
                withContext(Dispatchers.IO) {
                    CoreLibraryLocator.findAvailableLibrary(appContext, directoriesManager, coreId)
                }
            if (available == null) {
                startCoreDownload(coreId)
            } else {
                failedCores.update { it - coreId }
                diskRefreshTick.update { it + 1L }
            }
        }
    }

    fun retryDownload(coreId: CoreID) {
        viewModelScope.launch {
            startCoreDownload(coreId)
        }
    }

    fun deleteCachedCore(coreId: CoreID) {
        if (coreId.isBundledByDefault || coreId in downloadingCores.value) return
        viewModelScope.launch(Dispatchers.IO) {
            if (GameProcessLock.isHeldByAnotherProcess(appContext)) {
                return@launch
            }
            val stillSelected =
                coresSelection.getSelectedCores().first().any { it.coreConfig.coreID == coreId }
            if (stillSelected) {
                return@launch
            }
            CoreLibraryLocator.deleteDownloadedCore(directoriesManager, coreId)
            failedCores.update { it - coreId }
            diskRefreshTick.update { it + 1L }
        }
    }

    fun clearUnusedCachedCores() {
        viewModelScope.launch(Dispatchers.IO) {
            if (GameProcessLock.isHeldByAnotherProcess(appContext)) {
                return@launch
            }
            val activeIds =
                coresSelection.getSelectedCores().first().map { it.coreConfig.coreID }.toSet()
            CoreID.entries
                .filter { !it.isBundledByDefault && it !in activeIds && it !in downloadingCores.value }
                .forEach { coreId ->
                    CoreLibraryLocator.deleteDownloadedCore(directoriesManager, coreId)
                }
            diskRefreshTick.update { it + 1L }
        }
    }

    private suspend fun startCoreDownload(coreId: CoreID) {
        if (coreId in downloadingCores.value) return
        failedCores.update { it - coreId }
        downloadingCores.update { it + coreId }
        try {
            withContext(Dispatchers.IO) {
                coreUpdater.downloadCores(appContext, listOf(coreId))
            }
            failedCores.update { it - coreId }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to download alternate core ${coreId.coreName}")
            failedCores.update { it + (coreId to (e.message ?: "Download failed")) }
            _downloadErrorEvents.tryEmit(coreId.coreDisplayName)
        } finally {
            downloadingCores.update { it - coreId }
            diskRefreshTick.update { it + 1L }
        }
    }
}
