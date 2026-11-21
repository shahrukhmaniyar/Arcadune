package com.swordfish.lemuroid.app.mobile.feature.settings.coreselection

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneGlassCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconPill
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneRowDivider
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneSystemBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.arcadunePressable
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidLazySettingsPage
import com.swordfish.lemuroid.lib.library.SystemID

@Composable
fun CoresSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CoresSelectionViewModel,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var activeDialogSystemId by remember { mutableStateOf<SystemID?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.downloadErrorEvents.collect { coreName ->
            Toast.makeText(
                context,
                context.getString(R.string.cores_selection_download_failed_toast, coreName),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LemuroidLazySettingsPage(modifier = modifier.fillMaxSize()) {
        item(key = "cores_banner") {
            CoresInfoBanner(
                downloadedCount = uiState.downloadedCoreCount,
                downloadedBytesFormatted = uiState.downloadedBytesFormatted,
                unusedCachedCount = uiState.unusedCachedCount,
                onClearUnused = { viewModel.clearUnusedCachedCores() },
            )
        }

        if (uiState.rows.isNotEmpty()) {
            item(key = "cores_list") {
                LemuroidCardSettingsGroup(
                    title = {
                        Text(
                            text =
                                stringResource(
                                    R.string.cores_selection_section_consoles,
                                    uiState.rows.size,
                                ),
                        )
                    },
                ) {
                    uiState.rows.forEachIndexed { index, row ->
                        if (index > 0) {
                            ArcaduneRowDivider(startIndent = 80.dp)
                        }
                        SystemCoreEntryRow(
                            row = row,
                            onClick = { activeDialogSystemId = row.system.id },
                            onRetryClick = { viewModel.retryDownload(row.selectedOption.config.coreID) },
                        )
                    }
                }
            }
        }
    }

    val dialogRow =
        activeDialogSystemId?.let { targetId ->
            uiState.rows.firstOrNull { it.system.id == targetId }
        }
    if (dialogRow != null) {
        CorePickerDialog(
            row = dialogRow,
            onDismiss = { activeDialogSystemId = null },
            onSelectCore = { option ->
                viewModel.selectCore(dialogRow.system, option.config)
            },
            onRetryDownload = { option ->
                viewModel.retryDownload(option.config.coreID)
            },
            onDeleteCached = { option ->
                viewModel.deleteCachedCore(option.config.coreID)
            },
        )
    }
}

@Composable
private fun CoresInfoBanner(
    downloadedCount: Int,
    downloadedBytesFormatted: String,
    unusedCachedCount: Int,
    onClearUnused: () -> Unit,
) {
    ArcaduneGlassCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ArcaduneIconBadge(
                icon = Icons.Filled.Memory,
                tint = ArcaduneColors.Purple,
                containerColor = ArcaduneColors.Purple.copy(alpha = 0.16f),
                borderColor = ArcaduneColors.Purple.copy(alpha = 0.45f),
                size = 40.dp,
                iconSize = 20.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.cores_selection_banner_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ArcaduneColors.OnSurface,
                )
                Text(
                    text = stringResource(R.string.cores_selection_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
                if (downloadedCount > 0 && downloadedBytesFormatted.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ArcaduneSystemBadge(
                            text =
                                stringResource(
                                    R.string.cores_selection_cache_summary,
                                    downloadedCount,
                                    downloadedBytesFormatted,
                                ),
                        )
                        if (unusedCachedCount > 0) {
                            ArcaduneIconPill(
                                icon = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.cores_selection_delete_cached_cd),
                                onClick = onClearUnused,
                                size = 28.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemCoreEntryRow(
    row: CoresSelectionViewModel.SystemCoreRow,
    onClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    val systemTitle = stringResource(row.system.titleResId)
    val shortTitle = stringResource(row.system.shortTitleResId)
    val selectedCore = row.selectedOption.config.coreID

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .arcadunePressable(scaleDown = 0.98f, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(min = 52.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            ArcaduneSystemBadge(text = shortTitle)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = systemTitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = ArcaduneColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = selectedCore.coreDisplayName,
                style = MaterialTheme.typography.bodySmall,
                color = ArcaduneColors.OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        CoreStatusPill(
            status = row.selectedOption.status,
            isDefault = row.selectedOption.isDefault,
            onRetryClick = onRetryClick,
        )
    }
}

@Composable
private fun CoreStatusPill(
    status: CoresSelectionViewModel.CoreInstallStatus,
    isDefault: Boolean,
    onRetryClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(999.dp)

    when (status) {
        CoresSelectionViewModel.CoreInstallStatus.Bundled -> {
            val label =
                if (isDefault) {
                    stringResource(R.string.cores_selection_badge_default_builtin)
                } else {
                    stringResource(R.string.cores_selection_badge_builtin)
                }
            Box(
                modifier =
                    Modifier
                        .background(ArcaduneColors.SurfaceHigh, shape)
                        .border(1.dp, ArcaduneColors.GlassEdge, shape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
            }
        }

        is CoresSelectionViewModel.CoreInstallStatus.Downloaded -> {
            val sizeLabel = Formatter.formatShortFileSize(context, status.sizeBytes)
            Box(
                modifier =
                    Modifier
                        .background(ArcaduneColors.Purple.copy(alpha = 0.16f), shape)
                        .border(1.dp, ArcaduneColors.Purple.copy(alpha = 0.45f), shape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.cores_selection_badge_downloaded, sizeLabel),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ArcaduneColors.Purple,
                )
            }
        }

        CoresSelectionViewModel.CoreInstallStatus.Downloading -> {
            Row(
                modifier =
                    Modifier
                        .background(ArcaduneColors.Purple.copy(alpha = 0.16f), shape)
                        .border(1.dp, ArcaduneColors.Purple.copy(alpha = 0.45f), shape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.8.dp,
                    color = ArcaduneColors.Purple,
                )
                Text(
                    text = stringResource(R.string.cores_selection_badge_downloading),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ArcaduneColors.Purple,
                )
            }
        }

        is CoresSelectionViewModel.CoreInstallStatus.Failed -> {
            val errorTint = Color(0xFFFF8A80)
            Row(
                modifier =
                    Modifier
                        .then(
                            if (onRetryClick != null) {
                                Modifier.arcadunePressable(onClick = onRetryClick)
                            } else {
                                Modifier
                            },
                        )
                        .background(errorTint.copy(alpha = 0.15f), shape)
                        .border(1.dp, errorTint.copy(alpha = 0.45f), shape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = errorTint,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = stringResource(R.string.cores_selection_badge_retry),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = errorTint,
                )
            }
        }

        CoresSelectionViewModel.CoreInstallStatus.NotDownloaded -> {
            Row(
                modifier =
                    Modifier
                        .background(ArcaduneColors.SurfaceHigh, shape)
                        .border(1.dp, ArcaduneColors.GlassEdge, shape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = ArcaduneColors.OnSurfaceDim,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = stringResource(R.string.cores_selection_badge_download_on_select),
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
            }
        }
    }
}

@Composable
private fun CorePickerDialog(
    row: CoresSelectionViewModel.SystemCoreRow,
    onDismiss: () -> Unit,
    onSelectCore: (CoresSelectionViewModel.CoreOptionItem) -> Unit,
    onRetryDownload: (CoresSelectionViewModel.CoreOptionItem) -> Unit,
    onDeleteCached: (CoresSelectionViewModel.CoreOptionItem) -> Unit,
) {
    ArcaduneDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Memory,
        title = { Text(text = stringResource(row.system.titleResId)) },
        confirmButton = {
            ArcadunePillButton(
                label = stringResource(R.string.about_close),
                highlighted = true,
                onClick = onDismiss,
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.options.forEach { option ->
                CoreOptionCard(
                    option = option,
                    onSelect = {
                        if (option.isArchitectureSupported) {
                            if (option.isSelected &&
                                option.status is CoresSelectionViewModel.CoreInstallStatus.Failed
                            ) {
                                onRetryDownload(option)
                            } else {
                                onSelectCore(option)
                            }
                        }
                    },
                    onDeleteCached = { onDeleteCached(option) },
                )
            }
        }
    }
}

@Composable
private fun CoreOptionCard(
    option: CoresSelectionViewModel.CoreOptionItem,
    onSelect: () -> Unit,
    onDeleteCached: () -> Unit,
) {
    val core = option.config.coreID
    val shape = RoundedCornerShape(18.dp)
    val borderColor =
        if (option.isSelected) {
            ArcaduneColors.Purple.copy(alpha = 0.7f)
        } else {
            ArcaduneColors.GlassEdge
        }
    val containerColor =
        if (option.isSelected) {
            ArcaduneColors.Purple.copy(alpha = 0.12f)
        } else {
            ArcaduneColors.SurfaceHigh
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (option.isArchitectureSupported) Modifier else Modifier.alpha(0.48f))
                .arcadunePressable(
                    enabled = option.isArchitectureSupported,
                    scaleDown = 0.98f,
                    onClick = onSelect,
                )
                .background(containerColor, shape)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .background(
                        color = if (option.isSelected) ArcaduneColors.Purple else Color.Transparent,
                        shape = CircleShape,
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (option.isSelected) ArcaduneColors.Purple else ArcaduneColors.GlassEdge,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (option.isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ArcaduneColors.OnAccent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = core.coreDisplayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = ArcaduneColors.OnSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (core.summary.isNotBlank()) {
                Text(
                    text = core.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            if (!option.isArchitectureSupported) {
                Text(
                    text = stringResource(R.string.cores_selection_badge_unsupported_arch),
                    style = MaterialTheme.typography.labelSmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
            } else {
                CoreStatusPill(
                    status = option.status,
                    isDefault = option.isDefault,
                    onRetryClick = if (option.isSelected) onSelect else null,
                )
            }
        }

        if (option.canDeleteCached) {
            ArcaduneIconPill(
                icon = Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.cores_selection_delete_cached_cd),
                onClick = onDeleteCached,
                size = 34.dp,
            )
        }
    }
}
