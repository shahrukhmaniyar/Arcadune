package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class HomeViewModel(
    appContext: Context,
    retrogradeDb: RetrogradeDatabase,
    private val coresSelection: CoresSelection,
) : ViewModel() {
    companion object {
        const val CAROUSEL_MAX_ITEMS = 10
        const val DEBOUNCE_TIME = 100L
    }

    class Factory(
        val appContext: Context,
        val retrogradeDb: RetrogradeDatabase,
        val coresSelection: CoresSelection,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(appContext, retrogradeDb, coresSelection) as T
        }
    }

    data class UIState(
        val indexInProgress: Boolean = true,
        val showNoNotificationPermissionCard: Boolean = false,
        val showNoGamesCard: Boolean = false,
        val showRomsAccessLostCard: Boolean = false,
    )

    private val romsFolderReadableState = MutableStateFlow(true)
    private val notificationsPermissionEnabledState = MutableStateFlow(true)
    private val uiStates = MutableStateFlow(UIState())

    fun getViewStates(): Flow<UIState> {
        return uiStates
    }

    fun changeLocalStorageFolder(context: Context) {
        StorageFrameworkPickerLauncher.pickFolder(context)
    }

    fun updatePermissions(context: Context) {
        notificationsPermissionEnabledState.value = isNotificationsPermissionGranted(context)
        romsFolderReadableState.value = isRomsFolderReadable(context)
    }

    /**
     * False when a ROMs folder was chosen but Android no longer lets Arcadune read it. Games then
     * fail to start, so the library asks the player to choose the folder again.
     */
    private fun isRomsFolderReadable(context: Context): Boolean {
        val folder =
            SharedPreferencesHelper.getLegacySharedPreferences(context)
                .getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder), null)
                ?: return true
        return runCatching {
            context.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri.toString() == folder }
        }.getOrDefault(true)
    }

    private fun isNotificationsPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val permissionResult =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            )

        return permissionResult == PackageManager.PERMISSION_GRANTED
    }

    private fun buildViewState(
        totalGamesCount: Int,
        indexInProgress: Boolean,
        notificationsPermissionEnabled: Boolean,
        romsFolderReadable: Boolean,
    ): UIState {
        return UIState(
            indexInProgress = indexInProgress,
            showNoNotificationPermissionCard = !notificationsPermissionEnabled,
            showNoGamesCard = totalGamesCount == 0 && romsFolderReadable,
            showRomsAccessLostCard = !romsFolderReadable,
        )
    }

    init {
        viewModelScope.launch {
            val uiStatesFlow =
                combine(
                    retrogradeDb.gameDao().countGames().distinctUntilChanged(),
                    indexingInProgress(appContext),
                    notificationsPermissionEnabledState,
                    romsFolderReadableState,
                    ::buildViewState,
                )

            uiStatesFlow
                .debounce(DEBOUNCE_TIME)
                .flowOn(Dispatchers.IO)
                .collect { uiStates.value = it }
        }
    }

    private fun indexingInProgress(appContext: Context) =
        PendingOperationsMonitor(appContext).anyLibraryOperationInProgress()
}
