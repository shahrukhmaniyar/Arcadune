package com.swordfish.lemuroid.app.shared

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.shared.covers.CoverCompression
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class GameInteractor(
    private val activity: BusyActivity,
    private val retrogradeDb: RetrogradeDatabase,
    private val useLeanback: Boolean,
    private val shortcutsGenerator: ShortcutsGenerator,
    private val gameLauncher: GameLauncher,
    private val customCoverManager: CustomCoverManager,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onGamePlay(game: Game) {
        if (!ensureNotBusy()) {
            return
        }
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        gameLauncher.launchGameAsync(activity.activity(), game, true, useLeanback)
    }

    fun onGameRestart(game: Game) {
        if (!ensureNotBusy()) {
            return
        }
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        gameLauncher.launchGameAsync(activity.activity(), game, false, useLeanback)
    }

    fun onFavoriteToggle(
        game: Game,
        isFavorite: Boolean,
    ) {
        ioScope.launch {
            runCatching {
                retrogradeDb.gameDao().updateFavorite(game.id, isFavorite)
            }.onFailure { Timber.e(it, "Failed to update favorite state for game ${game.id}") }
        }
    }

    fun onCreateShortcut(game: Game) {
        ioScope.launch {
            runCatching {
                shortcutsGenerator.pinShortcutForGame(game)
            }.onFailure { Timber.e(it, "Failed to create shortcut for game ${game.id}") }
        }
    }

    fun supportShortcuts(): Boolean {
        return shortcutsGenerator.supportShortcuts()
    }

    fun onSetCustomName(
        game: Game,
        customName: String,
    ) {
        ioScope.launch {
            runCatching {
                retrogradeDb.gameDao().updateCustomName(game.id, customName)
            }.onFailure { Timber.e(it, "Failed to set custom name for game ${game.id}") }
        }
    }

    fun onClearCustomName(game: Game) {
        ioScope.launch {
            runCatching {
                retrogradeDb.gameDao().updateCustomName(game.id, null)
            }.onFailure { Timber.e(it, "Failed to clear custom name for game ${game.id}") }
        }
    }

    /**
     * Copies the image at [sourceUri] into app-private storage, then stores
     * the local file URI in the database.  This ensures the cover survives
     * app restarts regardless of where the user picked the file from.
     */
    fun onSetCustomCoverUri(
        game: Game,
        sourceUri: String,
    ) {
        ioScope.launch {
            runCatching {
                val localUri = customCoverManager.importCover(game.id, Uri.parse(sourceUri))
                // A photo or a downloaded artwork can be several MB; keep a small copy, like other covers.
                Uri.parse(localUri).path?.let { CoverCompression.compressFile(File(it)) }
                retrogradeDb.gameDao().updateCustomCoverUri(game.id, localUri)
            }.onFailure { Timber.e(it, "Failed to set custom cover for game ${game.id}") }
        }
    }

    /**
     * Removes the custom cover: deletes the file from private storage and
     * clears the database field.
     */
    fun onClearCustomCoverUri(game: Game) {
        ioScope.launch {
            runCatching {
                customCoverManager.deleteCover(game.id)
                retrogradeDb.gameDao().updateCustomCoverUri(game.id, null)
            }.onFailure { Timber.e(it, "Failed to clear custom cover for game ${game.id}") }
        }
    }

    private fun ensureNotificationsPermissionAvailable(): Boolean {
        if (useLeanback || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val permissionResult =
            ContextCompat.checkSelfPermission(
                activity.activity(),
                Manifest.permission.POST_NOTIFICATIONS,
            )

        if (permissionResult == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        activity.activity().displayToast(R.string.game_interactor_notification_permission_required)
        return false
    }

    private fun ensureNotBusy(): Boolean {
        if (activity.isBusy()) {
            activity.activity().displayToast(R.string.game_interactory_busy)
            return false
        }
        return true
    }
}
