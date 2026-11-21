package com.swordfish.lemuroid.app.mobile.feature.settings.saves

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneRowDivider
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSmallGameImage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidLazySettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.saves.backup.SavesBackupManager
import com.swordfish.lemuroid.lib.saves.backup.SavesStorageInspector

private enum class SingleGamePickerMode {
    EXPORT,
    IMPORT,
}

@Composable
fun SavesSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SavesSettingsViewModel,
) {
    val uiState = viewModel.uiState.collectAsState().value
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var activePickerMode by remember { mutableStateOf<SingleGamePickerMode?>(null) }
    var pendingGameAction by remember { mutableStateOf<SavesSettingsViewModel.GameSaveOption?>(null) }
    var pendingGameImport by remember { mutableStateOf<Pair<SavesSettingsViewModel.GameSaveOption, Uri>?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        var skippedInitialResume = false
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (skippedInitialResume) {
                        viewModel.refresh()
                    } else {
                        skippedInitialResume = true
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exportFolderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) viewModel.exportSavesFolder(uri)
        }
    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) viewModel.backupSaves(uri)
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) pendingRestore = uri
        }
    val exportSingleSaveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(SavesBackupManager.RAW_SAVE_MIME_TYPE),
        ) { uri ->
            val target = pendingGameAction
            pendingGameAction = null
            if (uri != null && target != null) {
                viewModel.exportGameSave(target.game, uri)
            }
        }
    val importSingleSaveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val target = pendingGameAction
            pendingGameAction = null
            if (uri != null && target != null) {
                if (target.hasSave) {
                    pendingGameImport = target to uri
                } else {
                    viewModel.importGameSave(target.game, uri)
                }
            }
        }

    val exportableGames = remember(uiState.singleGameOptions) { uiState.singleGameOptions.filter { it.hasSave } }

    LemuroidLazySettingsPage(modifier = modifier.fillMaxSize()) {
        if (uiState.isBusy) {
            item(key = "busy") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item(key = "storage") {
            SaveStorageCard(
                stats = uiState.stats,
                summary = viewModel.formatStorageSummary(uiState.stats),
            )
        }

        item(key = "export_folder") {
            ExportFolderCard(
                hasExportableSaves = uiState.stats.savesCount > 0,
                enabled = !uiState.isBusy,
                onExportFolder = { exportFolderLauncher.launch(null) },
            )
        }

        item(key = "single_game") {
            SingleGameSaveCard(
                hasExportableGameSaves = exportableGames.isNotEmpty(),
                hasCompatibleGames = uiState.singleGameOptions.isNotEmpty(),
                enabled = !uiState.isBusy,
                onExportSingleSave = { activePickerMode = SingleGamePickerMode.EXPORT },
                onImportSingleSave = { activePickerMode = SingleGamePickerMode.IMPORT },
            )
        }

        item(key = "zip_backup") {
            ZipBackupCard(
                enabled = !uiState.isBusy,
                onBackup = { backupLauncher.launch(viewModel.backupFileName) },
                onRestore = {
                    restoreLauncher.launch(
                        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
                    )
                },
            )
        }
    }

    activePickerMode?.let { mode ->
        val options = if (mode == SingleGamePickerMode.EXPORT) exportableGames else uiState.singleGameOptions
        GameSavePickerDialog(
            mode = mode,
            options = options,
            onDismiss = { activePickerMode = null },
            onSelect = { option ->
                activePickerMode = null
                pendingGameAction = option
                if (mode == SingleGamePickerMode.EXPORT) {
                    exportSingleSaveLauncher.launch(viewModel.suggestedGameSaveFileName(option.game))
                } else {
                    importSingleSaveLauncher.launch(arrayOf("*/*"))
                }
            },
        )
    }

    pendingGameImport?.let { (option, uri) ->
        ConfirmOverwriteDialog(
            title = stringResource(id = R.string.import_single_save_warning_title),
            message = stringResource(id = R.string.import_single_save_warning_message, option.game.displayName()),
            onDismiss = { pendingGameImport = null },
            onConfirm = {
                viewModel.importGameSave(option.game, uri)
                pendingGameImport = null
            },
        )
    }

    pendingRestore?.let { uri ->
        ConfirmOverwriteDialog(
            title = stringResource(id = R.string.restore_saves_warning_title),
            message = stringResource(id = R.string.restore_saves_warning_message),
            onDismiss = { pendingRestore = null },
            onConfirm = {
                viewModel.restoreSaves(uri)
                pendingRestore = null
            },
        )
    }
}

@Composable
private fun SaveStorageCard(
    stats: SavesStorageInspector.SavesStorageStats,
    summary: String,
) {
    val context = LocalContext.current
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.saves_storage_title)) },
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                ArcaduneIconBadge(
                    icon = Icons.Outlined.FolderOpen,
                    tint = ArcaduneColors.Purple,
                    containerColor = ArcaduneColors.Purple.copy(alpha = 0.16f),
                    borderColor = ArcaduneColors.Purple.copy(alpha = 0.45f),
                )
            },
            headlineContent = { Text(text = stringResource(id = R.string.saves_storage_on_phone)) },
            supportingContent = { Text(text = summary) },
        )

        ArcaduneRowDivider(startIndent = 68.dp)

        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.FolderSpecial) },
            title = { Text(text = stringResource(id = R.string.saves_storage_folder_title)) },
            subtitle = { Text(text = stringResource(id = R.string.saves_storage_folder_hint, stats.displayPath)) },
            action = {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Arcadune saves path", stats.absolutePath))
                Toast.makeText(context, R.string.saves_storage_path_copied, Toast.LENGTH_SHORT).show()
            },
        )

        Text(
            text = stringResource(id = R.string.saves_storage_android_note, context.packageName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun ExportFolderCard(
    hasExportableSaves: Boolean,
    enabled: Boolean,
    onExportFolder: () -> Unit,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.saves_export_category)) },
    ) {
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.CreateNewFolder) },
            enabled = enabled && hasExportableSaves,
            title = { Text(text = stringResource(id = R.string.saves_export_folder_title)) },
            subtitle = {
                Text(
                    text =
                        stringResource(
                            id =
                                if (hasExportableSaves) {
                                    R.string.saves_export_folder_description
                                } else {
                                    R.string.saves_export_folder_empty
                                },
                        ),
                )
            },
            onClick = onExportFolder,
        )
    }
}

@Composable
private fun SingleGameSaveCard(
    hasExportableGameSaves: Boolean,
    hasCompatibleGames: Boolean,
    enabled: Boolean,
    onExportSingleSave: () -> Unit,
    onImportSingleSave: () -> Unit,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.saves_single_game_category)) },
    ) {
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.UploadFile) },
            enabled = enabled && hasExportableGameSaves,
            title = { Text(text = stringResource(id = R.string.saves_single_game_export_title)) },
            subtitle = {
                Text(
                    text =
                        stringResource(
                            id =
                                if (hasExportableGameSaves) {
                                    R.string.saves_single_game_export_description
                                } else {
                                    R.string.saves_single_game_export_empty
                                },
                        ),
                )
            },
            onClick = onExportSingleSave,
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Download) },
            enabled = enabled && hasCompatibleGames,
            title = { Text(text = stringResource(id = R.string.saves_single_game_import_title)) },
            subtitle = {
                Text(
                    text =
                        stringResource(
                            id =
                                if (hasCompatibleGames) {
                                    R.string.saves_single_game_import_description
                                } else {
                                    R.string.saves_single_game_import_empty
                                },
                        ),
                )
            },
            onClick = onImportSingleSave,
        )
    }
}

@Composable
private fun ZipBackupCard(
    enabled: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_backup)) },
    ) {
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Archive) },
            enabled = enabled,
            title = { Text(text = stringResource(id = R.string.settings_title_backup_saves)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_backup_saves)) },
            onClick = onBackup,
        )
        ArcaduneRowDivider(startIndent = 68.dp)
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Restore) },
            enabled = enabled,
            title = { Text(text = stringResource(id = R.string.settings_title_restore_saves)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_restore_saves)) },
            onClick = onRestore,
        )
    }
}

@Composable
private fun GameSavePickerDialog(
    mode: SingleGamePickerMode,
    options: List<SavesSettingsViewModel.GameSaveOption>,
    onDismiss: () -> Unit,
    onSelect: (SavesSettingsViewModel.GameSaveOption) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val filteredOptions =
        remember(options, query) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                options
            } else {
                options.filter { it.game.displayName().contains(trimmed, ignoreCase = true) }
            }
        }

    ArcaduneDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Save,
        scrollable = false,
        title = {
            Text(
                text =
                    stringResource(
                        id =
                            if (mode == SingleGamePickerMode.EXPORT) {
                                R.string.saves_single_game_picker_export_title
                            } else {
                                R.string.saves_single_game_picker_import_title
                            },
                    ),
            )
        },
        dismissButton = {
            ArcadunePillButton(
                label = stringResource(id = R.string.cancel),
                onClick = onDismiss,
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text(text = stringResource(id = R.string.saves_single_game_picker_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (filteredOptions.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.saves_single_game_picker_no_matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArcaduneColors.OnSurfaceDim,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    items(filteredOptions, key = { it.game.id }) { option ->
                        val systemShortTitle =
                            stringResource(id = GameSystem.findById(option.game.systemId).shortTitleResId)
                        val subtitle =
                            if (option.hasSave) {
                                stringResource(
                                    id = R.string.saves_single_game_has_save_label,
                                    systemShortTitle,
                                    Formatter.formatShortFileSize(context, option.saveSizeBytes),
                                )
                            } else {
                                systemShortTitle
                            }
                        ListItem(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelect(option) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                LemuroidSmallGameImage(
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                                    game = option.game,
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = option.game.displayName(),
                                    color = ArcaduneColors.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = subtitle,
                                    color = ArcaduneColors.OnSurfaceDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmOverwriteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ArcaduneDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Warning,
        title = { Text(text = title) },
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
                onClick = onConfirm,
            )
        },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ArcaduneColors.OnSurfaceDim,
        )
    }
}
