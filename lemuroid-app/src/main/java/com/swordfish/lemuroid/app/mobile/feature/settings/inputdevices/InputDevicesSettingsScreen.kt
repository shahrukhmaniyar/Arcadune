package com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Crop169
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.Filter2
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.input.GamePadBindingActivity
import com.swordfish.lemuroid.app.mobile.feature.input.GamePadShortcutBindingActivity
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconBadge
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneRowDivider
import com.swordfish.lemuroid.app.shared.input.InputBindingUpdater
import com.swordfish.lemuroid.app.shared.input.InputKey
import com.swordfish.lemuroid.app.shared.input.ShortcutBindingUpdater
import com.swordfish.lemuroid.app.shared.input.lemuroiddevice.getLemuroidInputDevice
import com.swordfish.lemuroid.app.shared.settings.GameShortcut
import com.swordfish.lemuroid.app.shared.settings.GameShortcutType
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState

@Composable
fun InputDevicesSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: InputDevicesSettingsViewModel,
) {
    val state =
        viewModel.uiState
            .collectAsState(InputDevicesSettingsViewModel.State())
            .value

    LemuroidSettingsPage(modifier = modifier.fillMaxSize()) {
        if (state.devices.isNotEmpty()) {
            EnabledDeviceCategory(state)
        }
        state.bindings.forEach { (device, bindings) ->
            DeviceBindingCategory(device, bindings)
        }
        GeneralOptionsCategory(viewModel)
    }
}

@Composable
private fun DeviceBindingCategory(
    device: InputDevice,
    bindings: InputDevicesSettingsViewModel.BindingsView,
) {
    val context = LocalContext.current
    val customizableKeys = device.getLemuroidInputDevice().getCustomizableKeys()

    LemuroidCardSettingsGroup(title = { Text(text = device.name) }) {
        customizableKeys.forEachIndexed { index, retroKey ->
            if (index > 0) {
                ArcaduneRowDivider(startIndent = 68.dp)
            }
            val inputKey = bindings.keys[retroKey] ?: InputKey(KeyEvent.KEYCODE_UNKNOWN)

            LemuroidSettingsMenuLink(
                icon = { ArcaduneIconBadge(icon = retroKeyIcon(retroKey.keyCode)) },
                title = { Text(text = retroKey.displayName(LocalContext.current)) },
                subtitle = { Text(text = inputKey.displayName()) },
                onClick = {
                    val intent =
                        Intent(context, GamePadBindingActivity::class.java).apply {
                            putExtra(InputBindingUpdater.REQUEST_DEVICE, device)
                            putExtra(InputBindingUpdater.REQUEST_RETRO_KEY, retroKey.keyCode)
                        }
                    context.startActivity(intent)
                },
            )
        }

        bindings.shortcuts.forEachIndexed { index, shortcut ->
            if (customizableKeys.isNotEmpty() || index > 0) {
                ArcaduneRowDivider(startIndent = 68.dp)
            }
            DeviceShortcutBinding(context, device, shortcut)
        }
    }
}

@Composable
private fun DeviceShortcutBinding(
    context: Context,
    device: InputDevice,
    shortcut: GameShortcut,
) {
    LemuroidSettingsMenuLink(
        icon = { ArcaduneIconBadge(icon = shortcutTypeIcon(shortcut.type)) },
        title = { Text(text = shortcut.type.displayName()) },
        subtitle = {
            Text(
                text = if (shortcut.keys.isEmpty()) stringResource(R.string.shortcut_not_set) else shortcut.name,
            )
        },
        onClick = {
            val intent =
                Intent(context, GamePadShortcutBindingActivity::class.java).apply {
                    putExtra(ShortcutBindingUpdater.REQUEST_DEVICE, device)
                    putExtra(ShortcutBindingUpdater.REQUEST_SHORTCUT_TYPE, shortcut.type.name)
                }
            context.startActivity(intent)
        },
    )
}

private fun retroKeyIcon(keyCode: Int): ImageVector =
    when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> Icons.Filled.RadioButtonChecked
        KeyEvent.KEYCODE_BUTTON_B -> Icons.Filled.Circle
        KeyEvent.KEYCODE_BUTTON_X -> Icons.Filled.CheckBoxOutlineBlank
        KeyEvent.KEYCODE_BUTTON_Y -> Icons.Filled.ChangeHistory
        KeyEvent.KEYCODE_BUTTON_START -> Icons.Filled.PlayArrow
        KeyEvent.KEYCODE_BUTTON_SELECT -> Icons.Filled.MoreHoriz
        KeyEvent.KEYCODE_BUTTON_MODE -> Icons.Filled.Menu
        KeyEvent.KEYCODE_BUTTON_L1 -> Icons.Filled.Crop169
        KeyEvent.KEYCODE_BUTTON_R1 -> Icons.Filled.SmartButton
        KeyEvent.KEYCODE_BUTTON_L2 -> Icons.Filled.Filter1
        KeyEvent.KEYCODE_BUTTON_R2 -> Icons.Filled.Filter2
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Icons.Filled.Adjust
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Icons.Filled.ControlCamera
        KeyEvent.KEYCODE_DPAD_UP -> Icons.Filled.KeyboardArrowUp
        KeyEvent.KEYCODE_DPAD_DOWN -> Icons.Filled.KeyboardArrowDown
        KeyEvent.KEYCODE_DPAD_LEFT -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        KeyEvent.KEYCODE_DPAD_RIGHT -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        else -> Icons.Filled.Gamepad
    }

private fun shortcutTypeIcon(type: GameShortcutType): ImageVector =
    when (type) {
        GameShortcutType.MENU -> Icons.Filled.Menu
        GameShortcutType.QUICK_SAVE -> Icons.Filled.Save
        GameShortcutType.QUICK_LOAD -> Icons.Filled.Restore
        GameShortcutType.TOGGLE_FAST_FORWARD -> Icons.Filled.FastForward
    }

@Composable
private fun EnabledDeviceCategory(state: InputDevicesSettingsViewModel.State) {
    LemuroidCardSettingsGroup(title = { Text(text = stringResource(R.string.settings_gamepad_category_enabled)) }) {
        state.devices.forEachIndexed { index, device ->
            if (index > 0) {
                ArcaduneRowDivider(startIndent = 68.dp)
            }
            LemuroidSettingsSwitch(
                icon = { ArcaduneIconBadge(icon = Icons.Filled.SportsEsports) },
                state = booleanPreferenceState(key = device.key, default = device.enabledByDefault),
                title = { Text(text = device.name) },
            )
        }
    }
}

@Composable
private fun GeneralOptionsCategory(viewModel: InputDevicesSettingsViewModel) {
    LemuroidCardSettingsGroup(title = { Text(text = stringResource(R.string.settings_gamepad_category_general)) }) {
        LemuroidSettingsMenuLink(
            icon = { ArcaduneIconBadge(icon = Icons.Filled.Refresh) },
            title = { Text(text = stringResource(R.string.settings_gamepad_title_reset_bindings)) },
            onClick = { viewModel.resetAllBindings() },
        )
    }
}
