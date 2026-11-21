package com.swordfish.lemuroid.app.mobile.feature.settings.savesync

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneRowDivider
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsListMultiSelect
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.stringsSetPreferenceState

@Composable
fun SaveSyncSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SaveSyncSettingsViewModel,
) {
    val context = LocalContext.current

    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.refresh()
        }
    }

    val saveSyncState =
        viewModel.uiState
            .collectAsState()
            .value

    val isSyncInProgress =
        viewModel.saveSyncInProgress
            .collectAsState(true)
            .value

    val settingsActivity = saveSyncState.settingsActivity

    LemuroidSettingsPage(modifier = modifier.fillMaxSize()) {
        LemuroidCardSettingsGroup {
            LemuroidSettingsMenuLink(
                icon = { ArcaduneIconBadge(icon = Icons.Filled.Cloud) },
                title = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_save_sync_configure,
                                saveSyncState.provider,
                            ),
                    )
                },
                subtitle = { Text(text = saveSyncState.configInfo) },
                enabled = !isSyncInProgress && settingsActivity != null,
                onClick = {
                    if (settingsActivity != null) {
                        context.startActivity(Intent(context, settingsActivity))
                    }
                },
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            LemuroidSettingsSwitch(
                icon = { ArcaduneIconBadge(icon = Icons.Filled.Save) },
                state = booleanPreferenceState(R.string.pref_key_save_sync_enable, default = true),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_include_saves)) },
                subtitle = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_save_sync_include_saves_description,
                                saveSyncState.savesSpace,
                            ),
                    )
                },
                enabled = saveSyncState.isConfigured && !isSyncInProgress,
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            LemuroidSettingsListMultiSelect(
                icon = { ArcaduneIconBadge(icon = Icons.Filled.Layers) },
                state =
                    stringsSetPreferenceState(
                        stringResource(R.string.pref_key_save_sync_cores),
                        emptySet(),
                    ),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_include_states)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_save_sync_include_states_description)) },
                entryValues = saveSyncState.coreNames,
                entries = saveSyncState.coreVisibleNames,
                enabled = saveSyncState.isConfigured && !isSyncInProgress,
                confirmButton = stringResource(id = R.string.ok),
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            LemuroidSettingsSwitch(
                icon = { ArcaduneIconBadge(icon = Icons.Outlined.CloudSync) },
                state = booleanPreferenceState(R.string.pref_key_save_sync_auto, default = false),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_enable_auto)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_save_sync_enable_auto_description)) },
                enabled = saveSyncState.isConfigured && !isSyncInProgress,
            )
            ArcaduneRowDivider(startIndent = 68.dp)
            LemuroidSettingsMenuLink(
                icon = { ArcaduneIconBadge(icon = Icons.Filled.Sync) },
                title = { Text(text = stringResource(id = R.string.settings_save_sync_refresh)) },
                subtitle = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_save_sync_refresh_description,
                                saveSyncState.lastSyncInfo,
                            ),
                    )
                },
                enabled = saveSyncState.isConfigured && !isSyncInProgress,
                onClick = { SaveSyncWork.enqueueManualWork(context) },
            )
        }
    }
}
