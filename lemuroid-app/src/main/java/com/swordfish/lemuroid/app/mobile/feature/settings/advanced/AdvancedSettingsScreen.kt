package com.swordfish.lemuroid.app.mobile.feature.settings.advanced

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneRowDivider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidLazySettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState

@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: AdvancedSettingsViewModel,
    navController: NavHostController,
) {
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value

    LemuroidLazySettingsPage(
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "input") {
            InputSettings()
        }
        item(key = "general") {
            GeneralSettings(uiState.cache, viewModel, navController)
        }
    }
}

@Composable
private fun InputSettings() {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_input)) },
    ) {
        val rumbleEnabled = booleanPreferenceState(R.string.pref_key_enable_rumble, false)
        LemuroidSettingsSwitch(
            state = rumbleEnabled,
            title = { Text(text = stringResource(id = R.string.settings_title_enable_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_rumble)) },
        )
        ArcaduneRowDivider()
        LemuroidSettingsSwitch(
            enabled = rumbleEnabled.value,
            state = booleanPreferenceState(R.string.pref_key_enable_device_rumble, false),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_device_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_device_rumble)) },
        )
        ArcaduneRowDivider()
        LemuroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_tilt_sensitivity_index),
                    default = 6,
                ),
            steps = 10,
            valueRange = 0f..10f,
            enabled = true,
            title = { Text(text = stringResource(id = R.string.settings_title_tilt_sensitivity)) },
        )
    }
}

@Composable
private fun GeneralSettings(
    cacheState: AdvancedSettingsViewModel.CacheState,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val factoryResetDialogState = remember { mutableStateOf(false) }

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_general)) },
    ) {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_low_latency_audio, false),
            title = { Text(text = stringResource(id = R.string.settings_title_low_latency_audio)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_low_latency_audio)) },
        )
        ArcaduneRowDivider()
        LemuroidSettingsList(
            title = { Text(text = stringResource(R.string.settings_title_maximum_cache_usage)) },
            items = cacheState.displayNames,
            state =
                indexPreferenceState(
                    R.string.pref_key_max_cache_size,
                    cacheState.default,
                    cacheState.values,
                ),
        )
        ArcaduneRowDivider()
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_allow_direct_game_load, true),
            title = { Text(text = stringResource(id = R.string.settings_title_direct_game_load)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_direct_game_load)) },
        )
        ArcaduneRowDivider()
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_reset_settings)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_reset_settings)) },
            onClick = { factoryResetDialogState.value = true },
        )
    }

    if (factoryResetDialogState.value) {
        FactoryResetDialog(factoryResetDialogState, viewModel, navController)
    }
}

@Composable
private fun FactoryResetDialog(
    factoryResetDialogState: MutableState<Boolean>,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val onDismiss = {
        factoryResetDialogState.value = false
    }
    ArcaduneDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Warning,
        title = { Text(stringResource(id = R.string.reset_settings_warning_message_title)) },
        dismissButton = {
            ArcadunePillButton(
                label = stringResource(id = R.string.cancel),
                onClick = onDismiss,
            )
        },
        confirmButton = {
            ArcadunePillButton(
                label = stringResource(id = R.string.ok),
                highlighted = true,
                onClick = {
                    viewModel.resetAllSettings()
                    navController.popBackStack(MainRoute.SETTINGS.route, false)
                },
            )
        },
    ) {
        Text(
            text = stringResource(id = R.string.reset_settings_warning_message_description),
            style = MaterialTheme.typography.bodyMedium,
            color = ArcaduneColors.OnSurfaceDim,
        )
    }
}
