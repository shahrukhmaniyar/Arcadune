package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LemuroidSettingsList(
    enabled: Boolean = true,
    state: SettingValueState<Int>,
    title: @Composable () -> Unit,
    items: List<String>,
    icon: (@Composable () -> Unit)? = null,
    useSelectedValueAsSubtitle: Boolean = true,
    subtitle: (@Composable () -> Unit)? = null,
    closeDialogDelay: Long = 200,
    action: (@Composable () -> Unit)? = null,
    onItemSelected: ((Int, String) -> Unit)? = null,
) {
    if (state.value >= items.size) {
        throw IndexOutOfBoundsException("Current value for $title list setting cannot be grater than items size")
    }

    var showDialog by remember { mutableStateOf(false) }

    val safeSubtitle =
        if (state.value >= 0 && useSelectedValueAsSubtitle) {
            { Text(text = items[state.value]) }
        } else {
            subtitle
        }

    LemuroidSettingsMenuLink(
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = safeSubtitle,
        action = action,
        onClick = { showDialog = true },
    )

    if (!showDialog) return

    val coroutineScope = rememberCoroutineScope()
    val onSelected: (Int, Boolean) -> Unit = { selectedIndex, updateState ->
        coroutineScope.launch {
            if (updateState) state.value = selectedIndex
            delay(closeDialogDelay)
            showDialog = false
        }
    }

    ArcaduneDialog(
        onDismissRequest = { showDialog = false },
        title = title,
        dismissButton = {
            ArcadunePillButton(
                label = stringResource(id = R.string.cancel),
                onClick = { showDialog = false },
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (subtitle != null) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodySmall.copy(color = ArcaduneColors.OnSurfaceDim),
                ) {
                    subtitle()
                }
                Spacer(modifier = Modifier.size(4.dp))
            }

            items.forEachIndexed { index, item ->
                val isSelected by rememberUpdatedState(newValue = state.value == index)
                val rowShape = RoundedCornerShape(14.dp)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .clip(rowShape)
                            .background(
                                if (isSelected) {
                                    ArcaduneColors.Purple.copy(alpha = 0.16f)
                                } else {
                                    ArcaduneColors.SurfaceHigh.copy(alpha = 0.65f)
                                },
                            )
                            .border(
                                width = 1.dp,
                                color =
                                    if (isSelected) {
                                        ArcaduneColors.Purple.copy(alpha = 0.55f)
                                    } else {
                                        ArcaduneColors.GlassEdge
                                    },
                                shape = rowShape,
                            )
                            .selectable(
                                role = Role.RadioButton,
                                selected = isSelected,
                                onClick = {
                                    onSelected(index, !isSelected)
                                    onItemSelected?.invoke(index, items[index])
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ArcaduneColors.OnSurface,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}
