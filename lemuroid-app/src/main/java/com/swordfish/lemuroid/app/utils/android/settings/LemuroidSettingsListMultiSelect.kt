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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun LemuroidSettingsListMultiSelect(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: SettingValueState<Set<String>>,
    title: @Composable () -> Unit,
    entryValues: List<String>,
    entries: List<String>,
    icon: @Composable (() -> Unit)? = null,
    confirmButton: String,
    subtitle: @Composable (() -> Unit)? = null,
    onItemsSelected: ((List<String>) -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    if (entryValues.size != entries.size) {
        throw IllegalArgumentException("entries and entryValues need to have the same size")
    }

    var showDialog by remember { mutableStateOf(false) }

    LemuroidSettingsMenuLink(
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = subtitle,
        action = action,
        onClick = { showDialog = true },
    )

    if (!showDialog) return

    val onAdd: (Int) -> Unit = { selectedIndex ->
        val mutable = state.value.toMutableSet()
        mutable.add(entryValues[selectedIndex])
        state.value = mutable
    }
    val onRemove: (Int) -> Unit = { selectedIndex ->
        val mutable = state.value.toMutableSet()
        mutable.remove(entryValues[selectedIndex])
        state.value = mutable
    }

    ArcaduneDialog(
        modifier = modifier,
        onDismissRequest = { showDialog = false },
        title = title,
        dismissButton = {
            ArcadunePillButton(
                label = stringResource(id = R.string.cancel),
                onClick = { showDialog = false },
            )
        },
        confirmButton = {
            ArcadunePillButton(
                label = confirmButton,
                highlighted = true,
                onClick = {
                    showDialog = false
                    onItemsSelected?.invoke(entryValues.filter { state.value.contains(it) })
                },
            )
        },
    ) {
        Column(
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

            entryValues.forEachIndexed { index, item ->
                val isSelected by rememberUpdatedState(newValue = state.value.contains(item))
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
                            .toggleable(
                                role = Role.Checkbox,
                                value = isSelected,
                                onValueChange = {
                                    if (isSelected) {
                                        onRemove(index)
                                    } else {
                                        onAdd(index)
                                    }
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entries[index],
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArcaduneColors.OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                    )
                }
            }
        }
    }
}
