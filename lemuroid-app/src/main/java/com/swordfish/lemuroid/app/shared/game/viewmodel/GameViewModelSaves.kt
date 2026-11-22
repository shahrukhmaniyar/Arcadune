package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.content.Context
import android.graphics.Bitmap
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.achievements.HardcorePolicy
import com.swordfish.lemuroid.common.graphics.GraphicsUtils
import com.swordfish.lemuroid.common.graphics.takeScreenshot
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.IncompatibleStateException
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.roundToInt

class GameViewModelSaves(
    private val appContext: Context,
    private val system: GameSystem,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val retroGameView: GameViewModelRetroGameView,
    private val settingsManager: SettingsManager,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
    private val statesPreviewManager: StatesPreviewManager,
    private val sideEffects: GameViewModelSideEffects,
    private val fence: HardcorePolicy.TransitionFence = HardcorePolicy.TransitionFence(),
    private val isTransitionInProgress: () -> Boolean = { false },
    private val isHardcoreActive: () -> Boolean = { false },
) {
    private var currentQuickSave: SaveState? = null

    data class SaveSnapshot(
        val sram: ByteArray,
        val autoSave: SaveState?,
    )

    suspend fun saveSlot(index: Int) {
        if (!HardcorePolicy.canSaveState(isTransitionInProgress())) return
        getCurrentSaveState()?.let {
            statesManager.setSlotSave(game, it, systemCoreConfig.coreID, index)
            runCatching {
                takeScreenshotPreview(index)
            }
        }
    }

    private fun canLoadStateForEpoch(commandEpoch: Long): Boolean =
        HardcorePolicy.canLoadSaveState(
            hardcoreActive = isHardcoreActive(),
            transitionInProgress = isTransitionInProgress(),
            commandEpoch = commandEpoch,
            currentEpoch = fence.currentEpoch(),
        )

    suspend fun loadSlot(index: Int) {
        val commandEpoch = fence.currentEpoch()
        if (!canLoadStateForEpoch(commandEpoch)) {
            sideEffects.showToast(appContext.getString(R.string.ra_hardcore_load_disabled_toast))
            return
        }
        try {
            statesManager.getSlotSave(game, systemCoreConfig.coreID, index)?.let {
                val loaded =
                    withContext(Dispatchers.IO) {
                        loadSaveState(it, commandEpoch)
                    }

                if (!loaded) {
                    val toastRes =
                        if (isHardcoreActive() || !fence.isEpochValid(commandEpoch)) {
                            R.string.ra_hardcore_load_disabled_toast
                        } else {
                            R.string.game_toast_load_state_failed
                        }
                    sideEffects.showToast(appContext.getString(toastRes))
                }
            }
        } catch (e: Throwable) {
            val errorMessageId =
                when (e) {
                    is IncompatibleStateException -> R.string.error_message_incompatible_state
                    else -> R.string.game_toast_load_state_failed
                }
            sideEffects.showToast(appContext.getString(errorMessageId))
        }
    }

    suspend fun captureSaveSnapshot(useEmulationThread: Boolean): SaveSnapshot? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val sramState = retroGameView.serializeSRAM(useEmulationThread)
        val autoSaveState = if (isAutoSaveEnabled()) getCurrentSaveState(useEmulationThread) else null
        return SaveSnapshot(sramState, autoSaveState)
    }

    /** Writes the snapshot to disk. Returns false when the battery save could not be written. */
    suspend fun writeSaveSnapshot(snapshot: SaveSnapshot?): Boolean {
        if (snapshot == null) return true
        Timber.i(
            "GameViewModelSaves.write game=%s core=%s writingSram=%s writingAutoSave=%s",
            game.id,
            systemCoreConfig.coreID,
            true,
            snapshot.autoSave != null,
        )
        val sramWritten = savesManager.setSaveRAM(game, snapshot.sram)
        snapshot.autoSave?.let {
            runCatching { statesManager.setAutoSave(game, systemCoreConfig.coreID, it) }
                .onFailure { e -> Timber.e(e, "Could not write the automatic save state") }
        }
        return sramWritten
    }

    // On some cores unserialize fails with no reason. So we need to try multiple times.
    suspend fun restoreAutoSaveAsync(saveState: SaveState) {
        // PPSSPP and Mupen64 initialize some state while rendering the first frame, so we have to wait before restoring
        // the autosave. Do not change thread here. Stick to the GL one to avoid issues with PPSSPP.
        val commandEpoch = fence.currentEpoch()
        if (!canLoadStateForEpoch(commandEpoch) ||
            !isAutoSaveEnabled()
        ) {
            return
        }

        try {
            retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
            if (!canLoadStateForEpoch(commandEpoch)) {
                return
            }
            restoreQuickSave(saveState, commandEpoch)
        } catch (e: Throwable) {
            Timber.e(e, "Error while loading auto-save")
        }
    }

    private fun getCurrentSaveState(useEmulationThread: Boolean = true): SaveState? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val currentDisk =
            if (system.hasMultiDiskSupport) {
                retroGameView.getCurrentDisk(useEmulationThread)
            } else {
                0
            }
        return SaveState(
            retroGameView.serializeState(useEmulationThread),
            SaveState.Metadata(currentDisk, systemCoreConfig.statesVersion),
        )
    }

    private suspend fun isAutoSaveEnabled(): Boolean {
        return systemCoreConfig.statesSupported &&
            systemCoreConfig.autoSaveSupported &&
            settingsManager.autoSave()
    }

    private suspend fun takeScreenshotPreview(index: Int) {
        val sizeInDp = StatesPreviewManager.PREVIEW_SIZE_DP
        val previewSize = GraphicsUtils.convertDpToPixel(sizeInDp, appContext).roundToInt().coerceAtLeast(1)
        val gameView = retroGameView.retroGameView ?: return
        // Ensure a fresh frame has been drawn onto the GL surface after returning from the pause menu.
        withTimeoutOrNull(400L) {
            retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
        }
        val viewport = gameView.viewport
        val vpLeft = if (viewport.left.isFinite()) viewport.left.coerceIn(0f, 1f) else 0f
        val vpTop = if (viewport.top.isFinite()) viewport.top.coerceIn(0f, 1f) else 0f
        val vpRight = if (viewport.right.isFinite()) viewport.right.coerceIn(vpLeft, 1f) else 1f
        val vpBottom = if (viewport.bottom.isFinite()) viewport.bottom.coerceIn(vpTop, 1f) else 1f
        val vpWidth = (vpRight - vpLeft).takeIf { it > 0.01f } ?: 1f
        val vpHeight = (vpBottom - vpTop).takeIf { it > 0.01f } ?: 1f
        val captureMaxResolution =
            (previewSize / maxOf(vpWidth, vpHeight).coerceAtLeast(0.25f)).roundToInt().coerceAtMost(
                2048,
            )

        val fullPreview = gameView.takeScreenshot(captureMaxResolution, 3) ?: return
        val left = (fullPreview.width * vpLeft).roundToInt().coerceIn(0, fullPreview.width - 1)
        val top = (fullPreview.height * vpTop).roundToInt().coerceIn(0, fullPreview.height - 1)
        val right = (fullPreview.width * vpRight).roundToInt().coerceIn(left + 1, fullPreview.width)
        val bottom = (fullPreview.height * vpBottom).roundToInt().coerceIn(top + 1, fullPreview.height)
        val preview = Bitmap.createBitmap(fullPreview, left, top, right - left, bottom - top)
        try {
            statesPreviewManager.setPreviewForSlot(game, preview, systemCoreConfig.coreID, index)
        } finally {
            if (preview !== fullPreview) {
                fullPreview.recycle()
            }
            preview.recycle()
        }
    }

    // Now that we wait for the first rendered frame this is probably no longer needed, but we'll keep it just to be sure
    private suspend fun restoreQuickSave(
        saveState: SaveState,
        commandEpoch: Long,
    ) {
        var times = 10

        while (
            canLoadStateForEpoch(commandEpoch) &&
            !loadSaveState(saveState, commandEpoch) &&
            times > 0
        ) {
            delay(200)
            times--
        }
    }

    private fun loadSaveState(
        saveState: SaveState,
        commandEpoch: Long,
    ): Boolean {
        if (!canLoadStateForEpoch(commandEpoch)) {
            return false
        }
        val retroGameView = retroGameView.retroGameView ?: return false

        if (systemCoreConfig.statesVersion != saveState.metadata.version) {
            throw IncompatibleStateException()
        }

        if (system.hasMultiDiskSupport &&
            retroGameView.getAvailableDisks() > 1 &&
            retroGameView.getCurrentDisk() != saveState.metadata.diskIndex
        ) {
            retroGameView.changeDisk(saveState.metadata.diskIndex)
        }

        if (!canLoadStateForEpoch(commandEpoch)) {
            return false
        }
        return retroGameView.unserializeState(saveState.state)
    }

    /** Saves the quick save on disk too (like Delta's), so it is still there the next time the game starts. */
    suspend fun saveQuickSave() {
        if (!HardcorePolicy.canSaveState(isTransitionInProgress())) return
        val state =
            runCatching { getCurrentSaveState() }
                .onFailure { Timber.e(it, "Error while capturing the quick save") }
                .getOrNull()
        if (state == null) {
            sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_failed))
            return
        }
        // Kept in memory first, so Quick Load works for the rest of this session whatever happens to the file.
        currentQuickSave = state
        val onDisk =
            runCatching { statesManager.setQuickSave(game, systemCoreConfig.coreID, state) }
                .onFailure { Timber.e(it, "Error while writing the quick save") }
                .getOrDefault(false)
        // Only say "Quick saved" once it is true: a failed write must not look like a success.
        val message = if (onDisk) R.string.game_toast_quick_save_saved else R.string.game_toast_quick_save_not_stored
        sideEffects.showToast(appContext.getString(message))
    }

    /** Loads the quick save (from disk when this session has none), the way a slot is loaded. */
    suspend fun loadQuickSave() {
        val commandEpoch = fence.currentEpoch()
        if (!canLoadStateForEpoch(commandEpoch)) {
            sideEffects.showToast(appContext.getString(R.string.ra_hardcore_load_disabled_toast))
            return
        }
        try {
            val state = currentQuickSave ?: statesManager.getQuickSave(game, systemCoreConfig.coreID)
            if (state == null) {
                // Pressing Quick Load before any Quick Save used to do nothing at all.
                sideEffects.showToast(appContext.getString(R.string.game_toast_no_quick_save))
                return
            }
            val loaded = withContext(Dispatchers.IO) { loadSaveState(state, commandEpoch) }
            val message =
                when {
                    loaded -> R.string.game_toast_quick_save_loaded
                    isHardcoreActive() || !fence.isEpochValid(commandEpoch) -> R.string.ra_hardcore_load_disabled_toast
                    else -> R.string.game_toast_load_state_failed
                }
            sideEffects.showToast(appContext.getString(message))
        } catch (e: Throwable) {
            val errorMessageId =
                when (e) {
                    is IncompatibleStateException -> R.string.error_message_incompatible_state
                    else -> R.string.game_toast_load_state_failed
                }
            sideEffects.showToast(appContext.getString(errorMessageId))
        }
    }
}
