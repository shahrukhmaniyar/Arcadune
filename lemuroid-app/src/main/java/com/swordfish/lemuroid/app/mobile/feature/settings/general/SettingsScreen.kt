package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.mobile.feature.settings.achievements.RaGoldColor
import com.swordfish.lemuroid.app.mobile.feature.settings.skins.prewarmControllerGallery
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneAccent
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneRowDivider
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.arcadunePressable
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.update.AppUpdater
import com.swordfish.lemuroid.app.shared.update.UpdateSettingsContent
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidLazySettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsSettingsState

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        prewarmControllerGallery(appContext)
    }

    val state = viewModel.uiState.collectAsState().value

    val scanInProgress =
        viewModel.directoryScanInProgress
            .collectAsState(false)
            .value

    val indexingInProgress =
        viewModel.indexingInProgress
            .collectAsState(false)
            .value

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var skippedInitialResume = false
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (skippedInitialResume) {
                        viewModel.refreshSavesSummary()
                    } else {
                        skippedInitialResume = true
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LemuroidLazySettingsPage(modifier = modifier) {
        item(key = "roms") {
            RomsSettings(
                state = state,
                onChangeFolder = { viewModel.changeLocalStorageFolder() },
                indexingInProgress = indexingInProgress,
                scanInProgress = scanInProgress,
            )
        }
        item(key = "saves") {
            SavesSettings(
                savesSummary = state.savesSummary,
                isSaveSyncSupported = state.isSaveSyncSupported,
                navController = navController,
            )
        }
        item(key = "achievements") {
            RetroAchievementsSettings(
                achievementsState = state.achievementsState,
                navController = navController,
            )
        }
        item(key = "appearance") {
            AppearanceSettings()
        }
        if (AppUpdater.isSupported) {
            item(key = "updates") {
                UpdateSettings()
            }
        }
        item(key = "general") {
            GeneralSettings(navController = navController)
        }
        item(key = "input") {
            InputSettings(navController = navController)
        }
        item(key = "misc") {
            MiscSettings(
                indexingInProgress = indexingInProgress,
                navController = navController,
            )
        }
    }
}

@Composable
private fun SavesSettings(
    savesSummary: String,
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_saves)) },
    ) {
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Save) },
            title = { Text(text = stringResource(id = R.string.settings_title_saves)) },
            subtitle = { Text(text = savesSummary) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVES) },
        )
        if (isSaveSyncSupported) {
            ArcaduneRowDivider(startIndent = 68.dp)
            LemuroidSettingsMenuLink(
                icon = { ArcaduneIconBadge(icon = Icons.Outlined.CloudSync) },
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = {
                    Text(text = stringResource(id = R.string.settings_description_save_sync))
                },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
            )
        }
    }
}

@Composable
private fun RetroAchievementsSettings(
    achievementsState: RetroAchievementsSettingsState,
    navController: NavController,
) {
    val account = achievementsState.account
    val subtitle =
        when {
            account == null -> stringResource(R.string.settings_ra_signed_out_subtitle)
            !achievementsState.enabled ->
                stringResource(R.string.settings_ra_signed_in_disabled_subtitle, account.displayName)
            !RetroAchievementsPreferences.isHardcoreApproved() ->
                stringResource(
                    R.string.settings_ra_signed_in_active_subtitle,
                    account.displayName,
                    account.totalPoints,
                )
            achievementsState.hardcoreEnabled ->
                stringResource(R.string.settings_ra_signed_in_hardcore_subtitle, account.displayName, account.score)
            else ->
                stringResource(
                    R.string.settings_ra_signed_in_softcore_subtitle,
                    account.displayName,
                    account.softcoreScore.takeIf { it > 0 } ?: account.score,
                )
        }

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_achievements)) },
    ) {
        LemuroidSettingsMenuLink(
            icon = {
                ArcaduneIconBadge(
                    icon = Icons.Filled.EmojiEvents,
                    tint = if (achievementsState.isSignedIn) RaGoldColor else ArcaduneColors.OnSurface,
                    containerColor =
                        if (achievementsState.isSignedIn) {
                            RaGoldColor.copy(alpha = 0.16f)
                        } else {
                            ArcaduneColors.SurfaceHigh
                        },
                    borderColor =
                        if (achievementsState.isSignedIn) {
                            RaGoldColor.copy(alpha = 0.45f)
                        } else {
                            ArcaduneColors.GlassEdge
                        },
                )
            },
            title = { Text(text = stringResource(id = R.string.settings_title_retro_achievements)) },
            subtitle = { Text(text = subtitle) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_RETRO_ACHIEVEMENTS) },
        )
    }
}

@Composable
private fun MiscSettings(
    indexingInProgress: Boolean,
    navController: NavController,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_misc)) },
    ) {
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Memory) },
            title = { Text(text = stringResource(id = R.string.settings_title_display_bios_info)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_display_bios_info))
            },
            enabled = !indexingInProgress,
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_BIOS) },
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Tune) },
            title = { Text(text = stringResource(id = R.string.settings_title_advanced_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_advanced_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ADVANCED) },
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Image) },
            title = { Text(text = stringResource(id = R.string.cover_manager_title)) },
            subtitle = { Text(text = stringResource(id = R.string.cover_manager_description)) },
            enabled = !indexingInProgress,
            onClick = { navController.navigateToRoute(MainRoute.COVER_MANAGER) },
        )
    }
}

@Composable
private fun InputSettings(navController: NavController) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_input)) },
    ) {
        LemuroidSettingsList(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Vibration) },
            state =
                indexPreferenceState(
                    R.string.pref_key_haptic_feedback_mode,
                    "press",
                    stringListResource(R.array.pref_key_haptic_feedback_mode_values),
                ),
            title = {
                Text(text = stringResource(id = R.string.settings_title_enable_touch_feedback))
            },
            items = stringListResource(R.array.pref_key_haptic_feedback_mode_display_names),
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Palette) },
            title = { Text(text = stringResource(id = R.string.skin_gallery_title)) },
            subtitle = { Text(text = stringResource(id = R.string.skin_gallery_description)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SKINS) },
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.SportsEsports) },
            title = { Text(text = stringResource(id = R.string.settings_title_gamepad_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_gamepad_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_INPUT_DEVICES) },
        )
    }
}

@Composable
private fun GeneralSettings(navController: NavController) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_general)) },
    ) {
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.DeveloperBoard) },
            title = { Text(text = stringResource(id = R.string.settings_title_open_cores_selection)) },
            subtitle = { Text(text = stringResource(id = R.string.cores_selection_subtitle)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsSwitch(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Autorenew) },
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_autosave)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_autosave)) },
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsSwitch(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.History) },
            state = booleanPreferenceState(R.string.pref_key_rewind, true),
            title = { Text(text = stringResource(id = R.string.settings_title_rewind)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_rewind)) },
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsList(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Tv) },
            state =
                indexPreferenceState(
                    R.string.pref_key_shader_filter,
                    "auto",
                    stringListResource(R.array.pref_key_shader_filter_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.display_filter)) },
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsList(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.AspectRatio) },
            state =
                indexPreferenceState(
                    R.string.pref_key_viewport_alignment,
                    "center",
                    stringListResource(R.array.pref_key_viewport_alignment_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_viewport_alignment)) },
            items = stringListResource(R.array.pref_key_viewport_alignment_display_names),
        )
    }
}

@Composable
private fun RomsSettings(
    state: SettingsViewModel.State,
    onChangeFolder: () -> Unit,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
) {
    val context = LocalContext.current
    val currentDirectoryName = state.currentDirectoryName.ifBlank { stringResource(R.string.none) }

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.roms)) }) {
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Outlined.FolderOpen) },
            title = { Text(text = stringResource(id = R.string.directory)) },
            subtitle = { Text(text = currentDirectoryName) },
            onClick = { onChangeFolder() },
            enabled = !indexingInProgress,
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        if (scanInProgress) {
            LemuroidSettingsMenuLink(
                icon = { ArcaduneIconBadge(icon = Icons.Filled.Clear) },
                title = { Text(text = stringResource(id = R.string.stop)) },
                onClick = { LibraryIndexScheduler.cancelLibrarySync(context) },
            )
        } else {
            LemuroidSettingsMenuLink(
                icon = { ArcaduneIconBadge(icon = Icons.Filled.Refresh) },
                title = { Text(text = stringResource(id = R.string.rescan)) },
                onClick = { LibraryIndexScheduler.scheduleLibrarySync(context) },
                enabled = !indexingInProgress,
            )
        }
    }
}

/** Checking for, downloading and installing new versions of Arcadune from GitHub. */
@Composable
private fun UpdateSettings() {
    if (!AppUpdater.isSupported) return
    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.update_title)) }) {
        UpdateSettingsContent()
    }
}

/** The accent colour of the whole app: five soft colours, shown as dots to tap. */
@Composable
private fun AppearanceSettings() {
    val context = LocalContext.current
    val current = ArcaduneAccent.current.value
    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.theme_color_title)) }) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (accent in ArcaduneAccent.entries) {
                val selected = accent == current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier.arcadunePressable(
                            scaleDown = 0.90f,
                            onClick = { ArcaduneAccent.choose(context, accent) },
                        ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(46.dp)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) accent.color else ArcaduneColors.GlassEdge,
                                    shape = CircleShape,
                                )
                                .padding(5.dp)
                                .background(accent.color, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = accent.onColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(accent.titleRes),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        color = if (selected) ArcaduneColors.OnSurface else ArcaduneColors.OnSurfaceDim,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
