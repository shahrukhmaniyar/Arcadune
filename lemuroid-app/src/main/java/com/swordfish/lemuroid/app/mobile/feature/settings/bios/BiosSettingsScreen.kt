package com.swordfish.lemuroid.app.mobile.feature.settings.bios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneRowDivider
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneSystemBadge
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidLazySettingsPage
import com.swordfish.lemuroid.lib.bios.Bios
import com.swordfish.lemuroid.lib.library.GameSystem

@Composable
fun BiosScreen(
    modifier: Modifier = Modifier,
    viewModel: BiosSettingsViewModel,
) {
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value

    LemuroidLazySettingsPage(modifier = modifier.fillMaxSize()) {
        if (uiState.detected.isNotEmpty()) {
            item(key = "detected") {
                DetectedEntries(uiState.detected)
            }
        }
        if (uiState.notDetected.isNotEmpty()) {
            item(key = "not_detected") {
                SupportedEntries(uiState.notDetected)
            }
        }
    }
}

@Composable
private fun DetectedEntries(detected: List<Bios>) {
    LemuroidCardSettingsGroup(
        title = {
            Text(
                text = "${stringResource(id = R.string.settings_bios_category_detected)} (${detected.size})",
            )
        },
    ) {
        detected.forEachIndexed { index, bios ->
            if (index > 0) {
                ArcaduneRowDivider(startIndent = 68.dp)
            }
            BiosEntry(bios, true)
        }
    }
}

@Composable
private fun SupportedEntries(supported: List<Bios>) {
    LemuroidCardSettingsGroup(
        title = {
            Text(
                text = "${stringResource(id = R.string.settings_bios_category_not_detected)} (${supported.size})",
            )
        },
    ) {
        supported.forEachIndexed { index, bios ->
            if (index > 0) {
                ArcaduneRowDivider(startIndent = 68.dp)
            }
            BiosEntry(bios, false)
        }
    }
}

@Composable
fun BiosEntry(
    bios: Bios,
    detected: Boolean,
) {
    val systemTitle = stringResource(id = GameSystem.findById(bios.systemID.dbname).shortTitleResId)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcaduneIconBadge(
            icon = if (detected) Icons.Filled.CheckCircle else Icons.Outlined.Info,
            tint = if (detected) ArcaduneColors.Purple else ArcaduneColors.OnSurfaceDim,
            containerColor =
                if (detected) {
                    ArcaduneColors.Purple.copy(alpha = 0.16f)
                } else {
                    ArcaduneColors.SurfaceHigh
                },
            borderColor =
                if (detected) {
                    ArcaduneColors.Purple.copy(alpha = 0.45f)
                } else {
                    ArcaduneColors.GlassEdge
                },
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = bios.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = ArcaduneColors.OnSurface,
            )
            Text(
                text = bios.displayName(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = ArcaduneColors.OnSurfaceDim,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        ArcaduneSystemBadge(text = systemTitle)
    }
}
