package com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.alorma.compose.settings.storage.base.SettingValueState
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuChoiceScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.LocalTileSize
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.MenuTile
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.TILE_GAP
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.TileSize
import com.swordfish.lemuroid.app.shared.achievements.HardcorePolicy
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsSessionHolder
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.util.Locale

internal sealed interface GameSettingSelection {
    data object Scope : GameSettingSelection

    data object DisplayFilter : GameSettingSelection

    data class CoreOption(val index: Int) : GameSettingSelection

    data class Controller(val port: Int) : GameSettingSelection

    companion object {
        val Saver: Saver<GameSettingSelection?, String> =
            Saver(
                save = { selection ->
                    when (selection) {
                        null -> null
                        Scope -> "scope"
                        DisplayFilter -> "filter"
                        is CoreOption -> "core:${selection.index}"
                        is Controller -> "ctrl:${selection.port}"
                    }
                },
                restore = { raw ->
                    when {
                        raw == "scope" -> Scope
                        raw == "filter" -> DisplayFilter
                        raw.startsWith("core:") -> raw.removePrefix("core:").toIntOrNull()?.let(::CoreOption)
                        raw.startsWith("ctrl:") -> raw.removePrefix("ctrl:").toIntOrNull()?.let(::Controller)
                        else -> null
                    }
                },
            )
    }
}

/** In-game settings use a tile index, then a separate tile-choice page for each setting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameMenuCoreOptionsScreen(
    viewModel: GameMenuCoreOptionsViewModel,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    selectedSetting: GameSettingSelection?,
    onOpenSetting: (GameSettingSelection) -> Unit,
    onSettingApplied: () -> Unit,
) {
    val context = LocalContext.current
    val connectedGamePads by viewModel.connectedGamePads.collectAsState()
    val allOptions = remember(gameMenuRequest) { gameMenuRequest.coreOptions + gameMenuRequest.advancedCoreOptions }
    val coreDefaults =
        remember(gameMenuRequest) {
            gameMenuRequest.coreConfig.defaultSettings.associate { it.key to it.value }
        }
    val game = gameMenuRequest.game
    val scrollState = rememberScrollState()
    val gameOnly =
        booleanPreferenceState(
            CoreVariablesManager.computeGameSettingsEnabledKey(game.systemId, game.fileName),
            false,
        )

    if (selectedSetting != null) {
        val activePads = maxOf(1, connectedGamePads)
        if (selectedSetting is GameSettingSelection.Controller && selectedSetting.port >= activePads) {
            LaunchedEffect(selectedSetting, activePads) { onSettingApplied() }
            return
        }
        key(gameOnly.value) {
            SettingChoicePage(
                selection = selectedSetting,
                context = context,
                gameMenuRequest = gameMenuRequest,
                allOptions = allOptions,
                coreDefaults = coreDefaults,
                gameOnly = gameOnly,
                onSettingApplied = onSettingApplied,
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = if (maxWidth > maxHeight) 4 else 2
        val horizontalPadding = 20.dp
        val width =
            ((maxWidth - horizontalPadding * 2 - TILE_GAP * (columns - 1)) / columns - 1.dp)
                .coerceIn(100.dp, 180.dp)
        CompositionLocalProvider(LocalTileSize provides TileSize(width, 112.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = horizontalPadding, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.game_menu_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TILE_GAP, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(TILE_GAP),
                        maxItemsInEachRow = columns,
                    ) {
                        MenuTile(
                            text = stringResource(R.string.game_settings_only_this_game),
                            icon = rememberVectorPainter(Icons.Filled.SportsEsports),
                            selected = gameOnly.value,
                            supportingText =
                                stringResource(
                                    if (gameOnly.value) {
                                        R.string.game_settings_scope_game
                                    } else {
                                        R.string.game_settings_scope_console
                                    },
                                ),
                            onClick = { onOpenSetting(GameSettingSelection.Scope) },
                        )

                        key(gameOnly.value) {
                            val keyOf = { option: String ->
                                if (gameOnly.value) {
                                    CoreVariablesManager.computeGameSharedPreferenceKey(
                                        option,
                                        game.systemId,
                                        game.fileName,
                                    )
                                } else {
                                    CoreVariablesManager.computeSharedPreferenceKey(option, game.systemId)
                                }
                            }
                            ScreenFilterTile(
                                context,
                                if (gameOnly.value) gameScreenFilterKey(game) else globalScreenFilterKey(context),
                            ) { onOpenSetting(GameSettingSelection.DisplayFilter) }
                            CoreOptionTiles(allOptions, coreDefaults, gameOnly.value, context, keyOf, onOpenSetting)
                        }
                        ControllerTiles(
                            gameMenuRequest,
                            maxOf(1, connectedGamePads),
                            context,
                            onOpenSetting,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingChoicePage(
    selection: GameSettingSelection,
    context: Context,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    allOptions: List<LemuroidCoreOption>,
    coreDefaults: Map<String, String>,
    gameOnly: SettingValueState<Boolean>,
    onSettingApplied: () -> Unit,
) {
    val game = gameMenuRequest.game
    val keyOf = { option: String ->
        if (gameOnly.value) {
            CoreVariablesManager.computeGameSharedPreferenceKey(option, game.systemId, game.fileName)
        } else {
            CoreVariablesManager.computeSharedPreferenceKey(option, game.systemId)
        }
    }
    when (selection) {
        GameSettingSelection.Scope -> {
            val choices =
                listOf(
                    stringResource(R.string.game_settings_scope_console),
                    stringResource(R.string.game_settings_scope_game),
                )
            GameMenuChoiceScreen(
                title = stringResource(R.string.game_settings_only_this_game),
                choices = choices,
                icons = listOf(Icons.Filled.VideogameAsset, Icons.Filled.SportsEsports),
                selected = if (gameOnly.value) 1 else 0,
            ) { index ->
                val enabled = index == 1
                if (enabled && !gameOnly.value) copySystemSettingsToGame(context, game, allOptions, coreDefaults)
                gameOnly.value = enabled
                onSettingApplied()
            }
        }

        GameSettingSelection.DisplayFilter -> {
            val values =
                remember(context) { context.resources.getStringArray(R.array.pref_key_shader_filter_values).toList() }
            val choices =
                remember(
                    context,
                ) { context.resources.getStringArray(R.array.pref_key_shader_filter_display_names).toList() }
            if (values.isEmpty()) return
            val preferenceKey = if (gameOnly.value) gameScreenFilterKey(game) else globalScreenFilterKey(context)
            val state = indexPreferenceState(preferenceKey, values.first(), values)
            val icons = remember(values) { values.map(::screenFilterChoiceIcon) }
            GameMenuChoiceScreen(
                title = stringResource(R.string.display_filter),
                choices = choices,
                icons = icons,
                selected = state.value,
            ) { index ->
                state.value = index
                onSettingApplied()
            }
        }

        is GameSettingSelection.CoreOption -> {
            val option = allOptions.getOrNull(selection.index) ?: return
            val values = remember(option) { option.getEntriesValues() }
            if (values.isEmpty()) return
            val title = remember(option, context) { option.getDisplayName(context) }
            val isBoolean = remember(values) { values.toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET }
            val rawDefault = optionDefaultValue(option, coreDefaults, gameOnly.value, values)
            val achState by RetroAchievementsSessionHolder.state.collectAsState()
            val systemIdEnum = remember(game.systemId) { GameSystem.findById(game.systemId).id }
            if (isBoolean) {
                val state = booleanPreferenceState(keyOf(option.getKey()), rawDefault == "enabled")
                GameMenuChoiceScreen(
                    title = title,
                    choices =
                        listOf(
                            stringResource(R.string.game_settings_value_off),
                            stringResource(R.string.game_settings_value_on),
                        ),
                    icons = listOf(Icons.Filled.ToggleOff, Icons.Filled.ToggleOn),
                    selected = if (state.value) 1 else 0,
                ) { index ->
                    val candidateValue = if (index == 1) "enabled" else "disabled"
                    if (achState.hardcoreActive &&
                        !HardcorePolicy.isCoreOptionValueAllowed(
                            gameMenuRequest.coreConfig.coreID,
                            systemIdEnum,
                            option.getKey(),
                            candidateValue,
                        )
                    ) {
                        Toast.makeText(
                            context,
                            R.string.ra_hardcore_core_option_locked_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@GameMenuChoiceScreen
                    }
                    state.value = index == 1
                    onSettingApplied()
                }
            } else {
                val entries = remember(option, context) { buildCoreOptionEntries(option, context) }
                val state = indexPreferenceState(keyOf(option.getKey()), rawDefault, values)
                val hasHiddenEntries = remember(entries) { entries.any { !it.isPrimary } }
                val selectedEntry = entries.firstOrNull { it.rawIndex == state.value }
                val initiallyExpanded = !hasHiddenEntries || (selectedEntry != null && !selectedEntry.isPrimary)
                var showAllEntries by rememberSaveable(option.getKey()) { mutableStateOf(initiallyExpanded) }
                BackHandler(enabled = hasHiddenEntries && showAllEntries && !initiallyExpanded) {
                    showAllEntries = false
                }
                val visibleEntries =
                    remember(entries, hasHiddenEntries, showAllEntries) {
                        if (!hasHiddenEntries || showAllEntries) entries else entries.filter { it.isPrimary }
                    }
                val showMoreTile = hasHiddenEntries && !showAllEntries
                val moreLabel = stringResource(R.string.core_option_more)
                val displayChoices =
                    remember(visibleEntries, showMoreTile, moreLabel) {
                        val base = visibleEntries.map { it.label }
                        if (showMoreTile) base + moreLabel else base
                    }
                val displayIcons =
                    remember(option, context, visibleEntries, showMoreTile) {
                        val base =
                            visibleEntries.map { entry ->
                                coreOptionChoiceIcon(option, context, entry.rawValue, entry.label)
                            }
                        if (showMoreTile) base + Icons.Filled.MoreHoriz else base
                    }
                val displaySelected = visibleEntries.indexOfFirst { it.rawIndex == state.value }
                GameMenuChoiceScreen(
                    title = title,
                    choices = displayChoices,
                    icons = displayIcons,
                    selected = displaySelected,
                ) { displayIndex ->
                    if (showMoreTile && displayIndex == visibleEntries.size) {
                        showAllEntries = true
                        return@GameMenuChoiceScreen
                    }
                    val chosen = visibleEntries.getOrNull(displayIndex) ?: return@GameMenuChoiceScreen
                    val candidateValue = chosen.rawValue
                    if (achState.hardcoreActive &&
                        !HardcorePolicy.isCoreOptionValueAllowed(
                            gameMenuRequest.coreConfig.coreID,
                            systemIdEnum,
                            option.getKey(),
                            candidateValue,
                        )
                    ) {
                        Toast.makeText(
                            context,
                            R.string.ra_hardcore_core_option_locked_toast,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@GameMenuChoiceScreen
                    }
                    state.value = chosen.rawIndex
                    onSettingApplied()
                }
            }
        }

        is GameSettingSelection.Controller -> {
            val configs = gameMenuRequest.coreConfig.controllerConfigs[selection.port] ?: return
            if (configs.isEmpty()) return
            val configNames = remember(configs) { configs.map { it.name } }
            val state =
                indexPreferenceState(
                    ControllerConfigsManager.getSharedPreferencesId(
                        game.systemId,
                        gameMenuRequest.coreConfig.coreID,
                        selection.port,
                    ),
                    configNames.first(),
                    configNames,
                )
            GameMenuChoiceScreen(
                title = context.getString(R.string.core_settings_controller, (selection.port + 1).toString()),
                choices = configs.map { stringResource(it.displayName) },
                icons = configs.map(::controllerChoiceIcon),
                selected = state.value,
            ) { index ->
                state.value = index
                onSettingApplied()
            }
        }
    }
}

private data class CoreOptionEntry(
    val rawIndex: Int,
    val rawValue: String,
    val label: String,
    val isPrimary: Boolean,
)

private fun buildCoreOptionEntries(
    option: LemuroidCoreOption,
    context: Context,
): List<CoreOptionEntry> {
    val rawValues = option.getEntriesValues()
    val rawLabels = option.getEntries(context)
    val indexed =
        rawValues.mapIndexed { idx, rawVal ->
            Triple(idx, rawVal, rawLabels.getOrElse(idx) { rawVal })
        }
    return when (option.getKey()) {
        "applewin_machine" -> {
            val priorityOrder =
                listOf(
                    "Apple //e (original)",
                    "Apple //e (enhanced)",
                    "Apple II Plus",
                    "Apple II (original)",
                )
            val primarySet = setOf("Apple //e (original)", "Apple //e (enhanced)", "Apple II Plus")
            val byRaw = indexed.associateBy { it.second }
            val ordered = mutableListOf<Triple<Int, String, String>>()
            for (raw in priorityOrder) {
                byRaw[raw]?.let(ordered::add)
            }
            for (item in indexed) {
                if (item.second !in priorityOrder) ordered.add(item)
            }
            ordered.map { (idx, raw, label) ->
                val friendlyLabel =
                    when (raw) {
                        "Apple //e (original)" -> context.getString(R.string.apple2_model_recommended)
                        "Apple //e (enhanced)" -> context.getString(R.string.apple2_model_enhanced)
                        "Apple II (original)" -> context.getString(R.string.apple2_model_original)
                        else -> label
                    }
                CoreOptionEntry(idx, raw, friendlyLabel, raw in primarySet)
            }
        }

        "applewin_joypad_start", "applewin_joypad_select" -> {
            val primaryOrder = listOf("Enter", "Space", "1", "2", "ESC", "<default>")
            val primarySet = primaryOrder.toSet()
            val byRaw = indexed.associateBy { it.second }
            val ordered = mutableListOf<Triple<Int, String, String>>()
            for (raw in primaryOrder) {
                byRaw[raw]?.let(ordered::add)
            }
            for (item in indexed) {
                if (item.second !in primarySet) ordered.add(item)
            }
            ordered.map { (idx, raw, label) ->
                val friendlyLabel =
                    if (raw == "<default>") context.getString(R.string.core_option_key_none) else label
                CoreOptionEntry(idx, raw, friendlyLabel, raw in primarySet)
            }
        }

        else ->
            indexed.map { (idx, raw, label) ->
                CoreOptionEntry(idx, raw, label, isPrimary = true)
            }
    }
}

private fun optionDefaultValue(
    option: LemuroidCoreOption,
    coreDefaults: Map<String, String>,
    isGameOnly: Boolean,
    values: List<String>,
): String {
    val candidate =
        if (isGameOnly) {
            option.getCurrentValue()
        } else {
            coreDefaults[option.getKey()] ?: option.getCurrentValue()
        }
    return candidate.takeIf { it in values } ?: values.first()
}

private fun globalScreenFilterKey(context: Context) = context.getString(R.string.pref_key_shader_filter)

private fun gameScreenFilterKey(game: Game) =
    CoreVariablesManager.computeGameScreenFilterKey(
        game.systemId,
        game.fileName,
    )

private fun copySystemSettingsToGame(
    context: Context,
    game: Game,
    options: List<LemuroidCoreOption>,
    coreDefaults: Map<String, String>,
) {
    val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)
    val filterValues = context.resources.getStringArray(R.array.pref_key_shader_filter_values)
    preferences.edit {
        putString(
            gameScreenFilterKey(game),
            preferences.getString(globalScreenFilterKey(context), null) ?: filterValues.firstOrNull().orEmpty(),
        )
        for (option in options) {
            val values = option.getEntriesValues()
            if (values.isEmpty()) continue
            val consoleKey = CoreVariablesManager.computeSharedPreferenceKey(option.getKey(), game.systemId)
            val gameKey =
                CoreVariablesManager.computeGameSharedPreferenceKey(
                    option.getKey(),
                    game.systemId,
                    game.fileName,
                )
            val fallback =
                (coreDefaults[option.getKey()] ?: option.getCurrentValue()).takeIf { it in values } ?: values.first()
            if (values.toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
                val currentBool =
                    if (preferences.contains(consoleKey)) {
                        runCatching { preferences.getBoolean(consoleKey, fallback == "enabled") }
                            .getOrDefault(fallback == "enabled")
                    } else {
                        fallback == "enabled"
                    }
                putBoolean(gameKey, currentBool)
            } else {
                val currentVal =
                    runCatching { preferences.getString(consoleKey, null) }.getOrNull() ?: fallback
                putString(gameKey, currentVal)
            }
        }
    }
}

@Composable
private fun ScreenFilterTile(
    context: Context,
    preferenceKey: String,
    onClick: () -> Unit,
) {
    val values = remember(context) { context.resources.getStringArray(R.array.pref_key_shader_filter_values).toList() }
    val items =
        remember(context) { context.resources.getStringArray(R.array.pref_key_shader_filter_display_names).toList() }
    if (values.isEmpty()) return
    ListSettingTile(
        title = stringResource(R.string.display_filter),
        items = items,
        state = indexPreferenceState(preferenceKey, values.first(), values),
        icon = Icons.Filled.ColorLens,
        onClick = onClick,
    )
}

@Composable
private fun CoreOptionTiles(
    coreOptions: List<LemuroidCoreOption>,
    coreDefaults: Map<String, String>,
    isGameOnly: Boolean,
    context: Context,
    keyOf: (String) -> String,
    onOpenSetting: (GameSettingSelection) -> Unit,
) {
    coreOptions.forEachIndexed { index, option ->
        val values = remember(option) { option.getEntriesValues() }
        if (values.isEmpty()) return@forEachIndexed
        val icon = remember(option, context) { coreOptionIcon(option, context) }
        val title = remember(option, context) { option.getDisplayName(context) }
        val isBoolean = remember(values) { values.toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET }
        val rawDefault = optionDefaultValue(option, coreDefaults, isGameOnly, values)
        if (isBoolean) {
            BooleanSettingTile(
                title = title,
                state = booleanPreferenceState(keyOf(option.getKey()), rawDefault == "enabled"),
                icon = icon,
            ) { onOpenSetting(GameSettingSelection.CoreOption(index)) }
        } else {
            val mappedEntries = remember(option, context) { buildCoreOptionEntries(option, context) }
            val labelsByRawIndex =
                remember(values, mappedEntries) {
                    val map = mappedEntries.associateBy({ it.rawIndex }, { it.label })
                    values.indices.map { idx -> map[idx] ?: values[idx] }
                }
            ListSettingTile(
                title = title,
                items = labelsByRawIndex,
                state = indexPreferenceState(keyOf(option.getKey()), rawDefault, values),
                icon = icon,
            ) { onOpenSetting(GameSettingSelection.CoreOption(index)) }
        }
    }
}

@Composable
private fun ControllerTiles(
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    connectedGamePads: Int,
    context: Context,
    onOpenSetting: (GameSettingSelection) -> Unit,
) {
    val controllers = gameMenuRequest.coreConfig.controllerConfigs
    (0 until connectedGamePads)
        .map { it to controllers[it] }
        .filter { (_, configs) -> configs != null && configs.size >= 2 }
        .forEach { (port, configs) ->
            val safeConfigs = configs!!
            ListSettingTile(
                title = context.getString(R.string.core_settings_controller, (port + 1).toString()),
                items = safeConfigs.map { stringResource(it.displayName) },
                state =
                    indexPreferenceState(
                        ControllerConfigsManager.getSharedPreferencesId(
                            gameMenuRequest.game.systemId,
                            gameMenuRequest.coreConfig.coreID,
                            port,
                        ),
                        safeConfigs.first().name,
                        safeConfigs.map { it.name },
                    ),
                icon = if (port == 0) Icons.Filled.Gamepad else Icons.Filled.VideogameAsset,
            ) { onOpenSetting(GameSettingSelection.Controller(port)) }
        }
}

@Composable
private fun BooleanSettingTile(
    title: String,
    state: SettingValueState<Boolean>,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    MenuTile(
        text = title,
        icon = rememberVectorPainter(icon),
        selected = state.value,
        supportingText =
            stringResource(
                if (state.value) R.string.game_settings_value_on else R.string.game_settings_value_off,
            ),
        onClick = onClick,
    )
}

@Composable
private fun ListSettingTile(
    title: String,
    items: List<String>,
    state: SettingValueState<Int>,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val safeIndex = state.value.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    MenuTile(
        text = title,
        icon = rememberVectorPainter(icon),
        supportingText = items.getOrNull(safeIndex),
        onClick = onClick,
    )
}

private fun coreOptionIcon(
    option: LemuroidCoreOption,
    context: Context,
): ImageVector {
    val key = option.getKey().lowercase(Locale.ROOT)
    val identity = "$key ${option.getDisplayName(context).lowercase(Locale.ROOT)}"
    return when {
        // Citra (3DS) — every tile on the 3DS menu has a distinct silhouette.
        key.contains("citra_layout_option") -> Icons.Filled.Dashboard
        key.contains("citra_swap_screen") -> Icons.Filled.SwapVert
        key.contains("citra_analog_function") -> Icons.Filled.Gamepad
        key.contains("citra_deadzone") -> Icons.Filled.RadioButtonChecked
        key.contains("citra_resolution_factor") -> Icons.Filled.HighQuality
        key.contains("citra_texture_filter") -> Icons.Filled.Texture
        key.contains("citra_use_hw_shader_cache") -> Icons.Filled.Bolt
        key.contains("citra_is_new_3ds") -> Icons.Filled.Devices
        key.contains("citra_region_value") -> Icons.Filled.Public
        key.contains("citra_language") -> Icons.Filled.Language
        key.contains("citra_use_acc_mul") -> Icons.Filled.Functions
        key.contains("citra_use_acc_geo_shaders") -> Icons.Filled.ChangeHistory
        // Apple II (AppleWin)
        key.contains("applewin_machine") -> Icons.Filled.Computer
        key.contains("applewin_video_mode") -> Icons.Filled.Tv
        key.contains("applewin_joypad_start") -> Icons.Filled.PlayArrow
        key.contains("applewin_joypad_select") -> Icons.Filled.Keyboard
        // Home computers & classic consoles (C64, Atari 800, Atari 5200, MSX, ColecoVision)
        key.contains("vice_joyport") -> Icons.Filled.SettingsInputComponent
        key.contains("vice_autoloadwarp") -> Icons.Filled.FastForward
        key.contains("vice_c64_model") || key.contains("atari800_system") || key.contains("fmsx_mode") ->
            Icons.Filled.Computer
        key.contains("vice_drive_true_emulation") -> Icons.Filled.Storage
        key.contains("atari800_internalbasic") -> Icons.Filled.Code
        key.contains("atari800_sioaccel") -> Icons.Filled.Speed
        key.contains("artifacting_mode") -> Icons.Filled.Tv
        key.contains("fmsx_video_mode") -> Icons.Filled.Tv
        key.contains("fmsx_allsprites") -> Icons.Filled.Layers
        key.contains("fmsx_mapper_type_mode") -> Icons.Filled.Extension
        // 3DO (Opera), Saturn (YabaSanshiro), Dreamcast (Flycast)
        key.contains("opera_active_devices") -> Icons.Filled.Devices
        key.contains("opera_dsp_threaded") -> Icons.Filled.VolumeUp
        key.contains("opera_swi_hle") -> Icons.Filled.DeveloperBoard
        key.contains("opera_nvram_storage") -> Icons.Filled.Storage
        key.contains("opera_cpu_overclock") || key.contains("overclk") -> Icons.Filled.Speed
        key.contains("yabasanshiro_polygon_mode") -> Icons.Filled.ChangeHistory
        key.contains("yabasanshiro_addon_cart") -> Icons.Filled.Extension
        key.contains("reicast_alpha_sorting") -> Icons.Filled.Layers
        key.contains("reicast_threaded_rendering") || key.contains("armgba_threaded_video") ||
            key.contains("melonds_threaded_renderer") -> Icons.Filled.Bolt
        key.contains("widescreen_hack") -> Icons.Filled.AspectRatio
        // DS, PSP, N64, GBA, GB/GBC, Sega, NES, Atari
        key.contains("screen_layout") || key.contains("screens_layout") -> Icons.Filled.Dashboard
        key.contains("ppsspp_auto_frameskip") -> Icons.Filled.Bolt
        key.contains("armgba_skip_bios") -> Icons.Filled.SkipNext
        key.contains("armgba_gb_screen") -> Icons.Filled.Palette
        key.contains("color_correction") -> Icons.Filled.Contrast
        key.contains("mupen64plus-pak1") -> Icons.Filled.Extension
        key.contains("mupen64plus-pak2") -> Icons.Filled.Vibration
        key.contains("43screensize") || key.contains("parallel-n64-screensize") -> Icons.Filled.AspectRatio
        key.contains("parallel-n64-gfxplugin") -> Icons.Filled.HighQuality
        key.contains("stella_filter") || key.contains("ntsc_filter") -> Icons.Filled.Tv
        key.contains("overscan_h") || key.contains("hoverscan") -> Icons.Filled.SwapHoriz
        key.contains("overscan_v") -> Icons.Filled.SwapVert
        key.contains("overscan") -> Icons.Filled.Crop
        key.contains("nospritelimit") || key.contains("no_sprite_limit") || key.contains("sprlim") ->
            Icons.Filled.Layers
        key.contains("mix_frames") || key.contains("interframe") || key.contains("lcd") -> Icons.Filled.BlurOn
        key.contains("dark_filter") -> Icons.Filled.BrightnessMedium
        key.contains("gb_colorization") -> Icons.Filled.FormatColorFill
        key.contains("palette") || identity.contains("color") || identity.contains("colour") -> Icons.Filled.Palette
        key.contains("bilinearmode") || key.contains("texture") -> Icons.Filled.Texture
        key.contains("handy_rot") || key.contains("rotate_display") -> Icons.Filled.ScreenRotation
        key.contains("drc") || key.contains("jit") || key.contains("shader_cache") -> Icons.Filled.Speed
        key.contains("picodrive_region") -> Icons.Filled.Public
        identity.contains("solar") || identity.contains("light") -> Icons.Filled.LightMode
        identity.contains("ghost") -> Icons.Filled.BlurOn
        identity.contains("frame") || identity.contains("skip") -> Icons.Filled.FastForward
        identity.contains("audio") || identity.contains("sound") -> Icons.Filled.VolumeUp
        identity.contains("video") || identity.contains("render") || identity.contains("graphic") ||
            identity.contains("resolution") -> Icons.Filled.HighQuality
        identity.contains("cpu") || identity.contains("recompiler") || identity.contains("performance") ||
            identity.contains("speed") -> Icons.Filled.Speed
        identity.contains("region") || identity.contains("language") -> Icons.Filled.Language
        identity.contains("memory") || identity.contains("bios") || identity.contains("boot") -> Icons.Filled.Memory
        identity.contains("input") || identity.contains("controller") -> Icons.Filled.Gamepad
        else -> Icons.Filled.Tune
    }
}

/** Keeps value tiles recognisable at a glance instead of repeating the parent-setting icon. */
private fun coreOptionChoiceIcon(
    option: LemuroidCoreOption,
    context: Context,
    rawValue: String,
    choice: String,
): ImageVector {
    val key = option.getKey().lowercase(Locale.ROOT)
    val raw = rawValue.lowercase(Locale.ROOT)
    if (key == "applewin_machine") {
        return when (rawValue) {
            "Apple //e (original)" -> Icons.Filled.Verified
            "Apple //e (enhanced)" -> Icons.Filled.Bolt
            "Apple II Plus" -> Icons.Filled.History
            "Apple II (original)" -> Icons.Filled.Schedule
            "Apple II J-Plus" -> Icons.Filled.Translate
            "Pravets 82" -> Icons.Filled.Public
            "Pravets 8M" -> Icons.Filled.Dns
            "Pravets 8A" -> Icons.Filled.DeveloperBoard
            "Base64A" -> Icons.Filled.Language
            "TK3000 //e" -> Icons.Filled.Keyboard
            else -> Icons.Filled.Computer
        }
    }
    if (key == "applewin_video_mode") {
        return when {
            raw.contains("rgb") -> Icons.Filled.Palette
            raw.contains("idealized") -> Icons.Filled.AutoFixHigh
            raw.contains("composite") -> Icons.Filled.SettingsInputComponent
            raw.contains("color tv") -> Icons.Filled.Tv
            raw.contains("b&w") -> Icons.Filled.Contrast
            raw.contains("amber") -> Icons.Filled.LightMode
            raw.contains("green") -> Icons.Filled.FilterVintage
            raw.contains("white") -> Icons.Filled.BrightnessHigh
            else -> Icons.Filled.Tv
        }
    }
    if (key == "applewin_joypad_start" || key == "applewin_joypad_select") {
        return when (rawValue) {
            "<default>" -> Icons.Filled.Block
            "Enter" -> Icons.AutoMirrored.Filled.KeyboardReturn
            "Space" -> Icons.Filled.SpaceBar
            "1" -> Icons.Filled.LooksOne
            "2" -> Icons.Filled.LooksTwo
            "ESC" -> Icons.Filled.SkipNext
            "Left" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
            "Right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
            "Up" -> Icons.Filled.KeyboardArrowUp
            "Down" -> Icons.Filled.KeyboardArrowDown
            else -> Icons.Filled.Keyboard
        }
    }
    return when {
        !key.contains("handy_rot") &&
            (
                raw in DISABLED_RAW_VALUES ||
                    choice.hasToken("off") ||
                    choice.hasToken("disabled") ||
                    choice.hasToken("none")
            )
        -> Icons.Filled.Block
        raw == "auto" || choice.hasToken("auto") -> Icons.Filled.AutoFixHigh
        raw == "memory" -> Icons.Filled.Memory
        raw == "rumble" -> Icons.Filled.Vibration
        choice.hasToken("top") && choice.hasToken("bottom") -> Icons.Filled.ViewAgenda
        choice.hasToken("left") && choice.hasToken("right") -> Icons.Filled.ViewColumn
        choice.hasToken("side") -> Icons.Filled.ViewColumn
        choice.hasToken("landscape") -> Icons.Filled.ScreenLockLandscape
        choice.hasToken("portrait") -> Icons.Filled.ScreenLockPortrait
        choice.hasToken("jit") || choice.hasToken("dynamic") -> Icons.Filled.Bolt
        choice.hasToken("cached") -> Icons.Filled.Speed
        choice.hasToken("interpreter") -> Icons.Filled.Memory
        choice.hasToken("rgb") -> Icons.Filled.Palette
        choice.hasToken("svideo") || choice.contains("s-video", ignoreCase = true) -> Icons.Filled.HighQuality
        choice.hasToken("composite") -> Icons.Filled.SettingsInputComponent
        choice.hasToken("monochrome") -> Icons.Filled.Contrast
        choice.hasToken("sharp") -> Icons.Filled.CenterFocusStrong
        choice.hasToken("smooth") || choice.hasToken("simple") -> Icons.Filled.BlurOn
        choice.hasToken("fast") -> Icons.Filled.FastForward
        choice.hasToken("accurate") -> Icons.Filled.Verified
        choice.hasToken("crt") -> Icons.Filled.Tv
        choice.hasToken("lcd") -> Icons.Filled.GridOn
        else -> coreOptionIcon(option, context)
    }
}

private fun controllerChoiceIcon(config: ControllerConfig): ImageVector {
    val name = config.name.lowercase(Locale.ROOT)
    return when {
        name.contains("auto") -> Icons.Filled.AutoFixHigh
        name.contains("mouse") -> Icons.Filled.Mouse
        name.contains("landscape") -> Icons.Filled.ScreenLockLandscape
        name.contains("portrait") -> Icons.Filled.ScreenLockPortrait
        name.contains("dualshock") || name.contains("3d") || name.contains("6") -> Icons.Filled.SportsEsports
        else -> Icons.Filled.Gamepad
    }
}

private fun screenFilterChoiceIcon(rawValue: String): ImageVector =
    when (rawValue.lowercase(Locale.ROOT)) {
        "auto" -> Icons.Filled.AutoFixHigh
        "sharp" -> Icons.Filled.CenterFocusStrong
        "smooth" -> Icons.Filled.BlurOn
        "crt" -> Icons.Filled.Tv
        "lcd" -> Icons.Filled.GridOn
        else -> Icons.Filled.ColorLens
    }

private val DISABLED_RAW_VALUES = setOf("off", "disabled", "none", "false", "0", "<default>")

private fun String.hasToken(word: String): Boolean {
    val tokens = lowercase(Locale.ROOT).split(NON_WORD_REGEX)
    return word in tokens
}

private val NON_WORD_REGEX = Regex("[^\\p{L}\\p{N}]+")
