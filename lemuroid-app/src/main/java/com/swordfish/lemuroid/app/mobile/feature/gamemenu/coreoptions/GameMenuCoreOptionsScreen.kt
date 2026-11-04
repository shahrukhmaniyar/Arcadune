package com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.core.content.edit
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

@Composable
fun GameMenuCoreOptionsScreen(
    viewModel: GameMenuCoreOptionsViewModel,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
) {
    val context = LocalContext.current

    val connectedGamePads by viewModel.connectedGamePads.collectAsState(0)

    val allOptions =
        remember(gameMenuRequest) {
            gameMenuRequest.coreOptions + gameMenuRequest.advancedCoreOptions
        }

    val game = gameMenuRequest.game
    val gameOnly =
        booleanPreferenceState(
            CoreVariablesManager.computeGameSettingsEnabledKey(game.systemId, game.fileName),
            false,
        )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        LemuroidSettingsSwitch(
            state = gameOnly,
            title = { Text(text = stringResource(R.string.game_settings_only_this_game)) },
            subtitle = { Text(text = stringResource(R.string.game_settings_only_this_game_description)) },
            onCheckedChange = { enabled ->
                if (enabled) copySystemSettingsToGame(context, game, allOptions)
            },
        )

        // The settings below are stored under other keys when the game has its own settings, so
        // their states are rebuilt when the switch changes.
        key(gameOnly.value) {
            val keyOf = { option: String ->
                if (gameOnly.value) {
                    CoreVariablesManager.computeGameSharedPreferenceKey(option, game.systemId, game.fileName)
                } else {
                    CoreVariablesManager.computeSharedPreferenceKey(option, game.systemId)
                }
            }
            ScreenFilterOption(context, if (gameOnly.value) gameScreenFilterKey(game) else globalScreenFilterKey(context))
            CoreOptions(allOptions, context, keyOf)
        }
        ControllersOptions(gameMenuRequest, maxOf(1, connectedGamePads), context)
    }
}

private fun globalScreenFilterKey(context: Context) = context.getString(R.string.pref_key_shader_filter)

private fun gameScreenFilterKey(game: Game) = CoreVariablesManager.computeGameScreenFilterKey(game.systemId, game.fileName)

/** Starts the game's own settings from the ones the system has now, so nothing changes until the player edits them. */
private fun copySystemSettingsToGame(
    context: Context,
    game: Game,
    options: List<LemuroidCoreOption>,
) {
    val preferences = SharedPreferencesHelper.getSharedPreferences(context)
    val filterValues = context.resources.getStringArray(R.array.pref_key_shader_filter_values)
    preferences.edit {
        putString(
            gameScreenFilterKey(game),
            preferences.getString(globalScreenFilterKey(context), null) ?: filterValues.first(),
        )
        for (option in options) {
            val gameKey = CoreVariablesManager.computeGameSharedPreferenceKey(option.getKey(), game.systemId, game.fileName)
            if (option.getEntriesValues().toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
                putBoolean(gameKey, option.getCurrentValue() == "enabled")
            } else {
                putString(gameKey, option.getCurrentValue())
            }
        }
    }
}

@Composable
private fun ScreenFilterOption(
    context: Context,
    preferenceKey: String,
) {
    val values = context.resources.getStringArray(R.array.pref_key_shader_filter_values).toList()
    LemuroidSettingsList(
        title = { Text(text = stringResource(R.string.display_filter)) },
        items = context.resources.getStringArray(R.array.pref_key_shader_filter_display_names).toList(),
        state = indexPreferenceState(preferenceKey, values.first(), values),
    )
}

@Composable
private fun CoreOptions(
    coreOptions: List<LemuroidCoreOption>,
    context: Context,
    keyOf: (String) -> String,
) {
    for (coreOption in coreOptions) {
        if (coreOption.getEntriesValues().toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
            LemuroidSettingsSwitch(
                state =
                    booleanPreferenceState(
                        keyOf(coreOption.getKey()),
                        coreOption.getCurrentValue() == "enabled",
                    ),
                title = { Text(text = coreOption.getDisplayName(context)) },
            )
        } else {
            LemuroidSettingsList(
                title = { Text(text = coreOption.getDisplayName(context)) },
                items = coreOption.getEntries(context),
                state =
                    indexPreferenceState(
                        keyOf(coreOption.getKey()),
                        coreOption.getEntriesValues().first(),
                        coreOption.getEntriesValues(),
                    ),
            )
        }
    }
}

@Composable
private fun ControllersOptions(
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    connectedGamePads: Int,
    context: Context,
) {
    val controllers = gameMenuRequest.coreConfig.controllerConfigs

    val visibleControllers =
        (0 until connectedGamePads)
            .map { it to controllers[it] }
            .filter { (_, controllers) -> controllers != null && controllers.size >= 2 }

    if (visibleControllers.isEmpty()) {
        return
    }

    LemuroidSettingsGroup(
        title = { Text(text = stringResource(R.string.core_settings_category_controllers)) },
    ) {
        visibleControllers.forEach { (port, controllerConfigs) ->
            LemuroidSettingsList(
                title = { Text(text = context.getString(R.string.core_settings_controller, (port + 1).toString())) },
                items = controllerConfigs!!.map { stringResource(id = it.displayName) },
                state =
                    indexPreferenceState(
                        ControllerConfigsManager.getSharedPreferencesId(
                            gameMenuRequest.game.systemId,
                            gameMenuRequest.coreConfig.coreID,
                            port,
                        ),
                        controllerConfigs.map { it.name }.first(),
                        controllerConfigs.map { it.name },
                    ),
            )
        }
    }
}
