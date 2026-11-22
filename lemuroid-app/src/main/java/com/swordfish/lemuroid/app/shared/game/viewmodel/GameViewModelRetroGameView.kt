package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.achievements.HardcorePolicy
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsSessionHolder
import com.swordfish.lemuroid.app.shared.game.ShaderChooser
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.HDModeQuality
import com.swordfish.lemuroid.common.coroutines.MutableStateProperty
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.view.disableTouchEvents
import com.swordfish.lemuroid.lib.core.CoreVariable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.game.GameLoaderError
import com.swordfish.lemuroid.lib.game.GameLoaderException
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.RomFiles
import com.swordfish.libretrodroid.AchievementsConfig
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ImmersiveMode
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.ViewportAlignment
import com.swordfish.libretrodroid.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class GameViewModelRetroGameView(
    private val appContext: Context,
    private val system: GameSystem,
    private val systemCoreConfig: SystemCoreConfig,
    private val settingsManager: SettingsManager,
    private val coreVariablesManager: CoreVariablesManager,
    private val sideEffects: GameViewModelSideEffects,
    private val rumbleManager: RumbleManager,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    sealed interface GameState {
        data object Uninitialized : GameState

        data class Loading(val message: String) : GameState

        data class Loaded(
            val gameData: GameLoader.GameData,
            val retroViewData: GLRetroViewData,
        ) : GameState

        data object Ready : GameState
    }

    private val gameState: MutableStateFlow<GameState> = MutableStateFlow(GameState.Uninitialized)

    // The running game and its screen filter, so options changed in the game menu can be applied.
    private var currentGame: Game? = null
    private var currentScreenFilter: String? = null

    private val retroGameViewFlow = MutableStateFlow<GLRetroView?>(null)
    var retroGameView: GLRetroView? by MutableStateProperty(retroGameViewFlow)

    fun getGameState(): Flow<GameState> {
        // A loading message waits a moment (a fast load never flashes it); the game itself never waits.
        return gameState.debounce { state -> if (state is GameState.Loaded || state is GameState.Ready) 0L else 200L }
    }

    suspend fun initialize(
        applicationContext: Context,
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        gameLoader: GameLoader,
        requestLoadSave: Boolean,
        achievementsConfig: AchievementsConfig? = null,
    ) {
        val currentState = gameState.value
        if (currentState != GameState.Uninitialized) return

        val autoSaveEnabled = settingsManager.autoSave()
        val hardcoreDisablesAutoLoad = achievementsConfig?.enabled == true && achievementsConfig.hardcoreEnabled
        val filter = settingsManager.screenFilter(game)
        currentGame = game
        currentScreenFilter = filter
        val hdMode = settingsManager.hdMode()
        val hdModeQuality = settingsManager.hdModeQuality()
        val alignment = settingsManager.viewportAlignment()
        val lowLatencyAudio = settingsManager.lowLatencyAudio()
        val enableRumble = settingsManager.enableRumble()
        val directLoad = settingsManager.allowDirectGameLoad()
        val enableImmersiveMode = settingsManager.blurFill()
        blurFillEnabled = enableImmersiveMode

        val hasMicrophonePermission =
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

        val enableMicrophone = systemCoreConfig.supportsMicrophone && hasMicrophonePermission

        val loadingStatesFlow =
            gameLoader.load(
                applicationContext,
                game,
                requestLoadSave && autoSaveEnabled && !hardcoreDisablesAutoLoad,
                systemCoreConfig,
                directLoad,
            )

        loadingStatesFlow
            .flowOn(Dispatchers.IO)
            .catch {
                val message =
                    if (it is GameLoaderException) {
                        getErrorMessage(it.error)
                    } else {
                        ""
                    }
                sideEffects.requestFailureFinish(message)
            }
            .collect { loadingState ->
                gameState.value =
                    if (loadingState is GameLoader.LoadingState.Ready) {
                        Timber.i("Setting state to loaded")
                        val retroViewData =
                            buildRetroViewData(
                                applicationContext,
                                systemCoreConfig,
                                loadingState.gameData,
                                hdMode,
                                hdModeQuality,
                                filter,
                                alignment,
                                lowLatencyAudio,
                                enableRumble,
                                enableMicrophone,
                                enableImmersiveMode,
                                achievementsConfig,
                            )
                        GameState.Loaded(
                            gameData = loadingState.gameData,
                            retroViewData = retroViewData,
                        )
                    } else {
                        GameState.Loading(getLoadingMessage(loadingState))
                    }
            }
    }

    fun createRetroView(
        context: Context,
        lifecycle: LifecycleOwner,
    ): Pair<GameLoader.GameData, GLRetroView> {
        val currentState = gameState.value
        if (currentState !is GameState.Loaded) throw IllegalStateException("Game is not loaded.")

        val result =
            GLRetroView(context, currentState.retroViewData)
                .apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                }

        if (!system.hasTouchScreen) {
            result.disableTouchEvents()
        }

        lifecycle.lifecycle.addObserver(result)

        if (BuildConfig.DEBUG) {
            runCatching {
                printRetroVariables(result)
            }
        }

        retroGameViewFlow.value = result
        gameState.value = GameState.Ready

        return currentState.gameData to result
    }

    suspend fun retroGameViewFlow() =
        retroGameViewFlow
            .filterNotNull()
            .first()

    suspend fun waitRetroGameViewInitialized() {
        retroGameViewFlow()
    }

    suspend inline fun <reified T> waitGLEvent() {
        val retroView = retroGameViewFlow()
        retroView.getGLRetroEvents()
            .filterIsInstance<T>()
            .first()
    }

    private fun buildRetroViewData(
        appContext: Context,
        systemCoreConfig: SystemCoreConfig,
        gameData: GameLoader.GameData,
        hdMode: Boolean,
        hdModeQuality: HDModeQuality,
        screenFilter: String,
        alignment: String,
        lowLatencyAudio: Boolean,
        requestRumble: Boolean,
        requestMicrophone: Boolean,
        enableImmersiveMode: Boolean,
        achievementsConfig: AchievementsConfig?,
    ): GLRetroViewData {
        return GLRetroViewData(appContext).apply {
            this.achievementsConfig = achievementsConfig
            coreFilePath = gameData.coreLibrary

            when (val gameFiles = gameData.gameFiles) {
                is RomFiles.Standard -> {
                    gameFilePath = gameFiles.files.first().absolutePath
                }

                is RomFiles.Virtual -> {
                    gameVirtualFiles = gameFiles.files.map { VirtualFile(it.filePath, it.fd) }
                }
            }

            systemDirectory = gameData.systemDirectory.absolutePath
            savesDirectory = gameData.savesDirectory.absolutePath
            variables = gameData.coreVariables.map { Variable(it.key, it.value) }.toTypedArray()
            saveRAMState = gameData.saveRAMData
            shader =
                ShaderChooser.getShaderForSystem(
                    appContext,
                    hdMode,
                    hdModeQuality,
                    screenFilter,
                    GameSystem.findById(gameData.game.systemId),
                )
            viewportAlignment =
                when (alignment) {
                    "top" -> ViewportAlignment.TOP
                    "bottom" -> ViewportAlignment.BOTTOM
                    else -> ViewportAlignment.CENTER
                }
            preferLowLatencyAudio = lowLatencyAudio
            rumbleEventsEnabled = requestRumble
            skipDuplicateFrames = systemCoreConfig.skipDuplicateFrames
            enableMicrophone = requestMicrophone
            immersiveMode = buildImmersiveModeConfiguration(enableImmersiveMode)
        }
    }

    private fun buildImmersiveModeConfiguration(enableImmersiveMode: Boolean): ImmersiveMode? {
        return if (enableImmersiveMode) {
            ImmersiveMode(blendFactor = 0.05f)
        } else {
            null
        }
    }

    private fun getLoadingMessage(loadingState: GameLoader.LoadingState): String {
        return when (loadingState) {
            is GameLoader.LoadingState.LoadingCore -> {
                appContext.getString(com.swordfish.lemuroid.ext.R.string.game_loading_download_core)
            }

            is GameLoader.LoadingState.LoadingGame -> {
                appContext.getString(R.string.game_loading_preparing_game)
            }

            else -> ""
        }
    }

    private fun printRetroVariables(retroGameView: GLRetroView) {
        scope.launch {
            // Some cores do not immediately call SET_VARIABLES so we might need to wait a little bit
            delay(1.seconds)
            retroGameView.getVariables().forEach {
                Timber.i("Libretro variable: $it")
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        owner.launchOnState(Lifecycle.State.STARTED) {
            initializeRetroGameViewErrorsFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeCoreVariablesFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeRumbleFlow()
        }
    }

    private suspend fun initializeCoreVariablesFlow() {
        try {
            waitRetroGameViewInitialized()
            val options = coreVariablesManager.getOptionsForCore(system.id, systemCoreConfig, currentGame)
            updateCoreVariables(options)
            updateScreenFilter()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private suspend fun initializeRumbleFlow() {
        val retroGameView = retroGameViewFlow()
        val rumbleEvents = retroGameView.getRumbleEvents()
        rumbleManager.collectAndProcessRumbleEvents(systemCoreConfig, rumbleEvents)
    }

    private suspend fun initializeRetroGameViewErrorsFlow() {
        retroGameViewFlow().getGLRetroErrors()
            .catch { Timber.e(it, "Exception in GLRetroErrors. Ironic.") }
            .collect { handleRetroViewError(it) }
    }

    /** Applies a screen filter chosen in the game menu (for example one for this game only). */
    private suspend fun updateScreenFilter() {
        val game = currentGame ?: return
        val filter = settingsManager.screenFilter(game)
        if (filter == currentScreenFilter) return
        currentScreenFilter = filter

        retroGameView?.shader =
            ShaderChooser.getShaderForSystem(
                appContext,
                settingsManager.hdMode(),
                settingsManager.hdModeQuality(),
                filter,
                system,
            )
    }

    /** Whether Blur fill (Settings) is on for this game; the screen decides where it may show. */
    @Volatile
    var blurFillEnabled = false
        private set

    // Values of the restart-only options when the game started (they are passed to the core at load).
    private var restartOnlyAtStart: Map<String, String>? = null

    // The core options as chosen (before the sideways change below), so the swap button knows which
    // screen is the main one now and a rotation can send the layout again.
    private val currentValues = mutableMapOf<String, String>()

    private var landscape =
        appContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    /**
     * DS and 3DS screens stacked top and bottom are tiny on a phone held sideways (and the controls
     * then cover the touch screen), so sideways they are shown side by side instead, and stacked
     * again when the phone is turned back upright.
     */
    fun onOrientationChanged(isLandscape: Boolean) {
        if (isLandscape == landscape) return
        landscape = isLandscape
        val layouts =
            SIDEWAYS_LAYOUTS.keys.mapNotNull {
                    key ->
                currentValues[key]?.let { Variable(key, sidewaysAware(key, it)) }
            }
        if (layouts.isNotEmpty()) retroGameView?.updateVariables(*layouts.toTypedArray())
        publishScreenLayout()
    }

    /**
     * The screen settings the core draws with right now (layout after the sideways change, and
     * which screen is the main one), so the console shell cuts its windows where the screens are.
     */
    val screenLayout = MutableStateFlow<Map<String, String>>(emptyMap())

    private fun publishScreenLayout() {
        screenLayout.value =
            (SIDEWAYS_LAYOUTS.keys + CITRA_SWAP_SCREEN)
                .mapNotNull { key ->
                    val raw =
                        if (key == CITRA_SWAP_SCREEN) {
                            citraSwapOverride ?: currentValues[key]
                        } else {
                            currentValues[key]
                        }
                    raw?.let { key to effectiveDsLayout(key, it) }
                }
                .toMap()
    }

    private fun effectiveDsLayout(
        key: String,
        value: String,
    ): String {
        val (stacked, sideBySide) = SIDEWAYS_LAYOUTS[key] ?: return value
        val base = if (landscape && value == stacked) sideBySide else value
        if (!dsSwapped) return base
        return when (base) {
            "top-bottom" -> "bottom-top"
            "top/bottom" -> "bottom/top"
            "left-right" -> "right-left"
            "left/right" -> "right/left"
            else -> base
        }
    }

    private fun sidewaysAware(
        key: String,
        value: String,
    ): String {
        if (key == CITRA_SWAP_SCREEN) {
            return citraSwapOverride ?: value
        }
        return effectiveDsLayout(key, value)
    }

    // Whether Swap has flipped the DS screens for this session.
    private var dsSwapped = false

    // Session-only 3DS swap override so closing the Pause Menu does not reset in-game Swap.
    private var citraSwapOverride: String? = null
    private var lastMenuCitraSwap: String? = null

    /**
     * The swap-screens button: the other screen becomes the main one. Only for this session; the
     * game menu's "Main screen" (3DS) stays the choice the game starts with.
     */
    fun swapScreens() {
        if (MELONDS_LAYOUT in currentValues || DESMUME_LAYOUT in currentValues) {
            dsSwapped = !dsSwapped
            currentValues[MELONDS_LAYOUT]?.let {
                retroGameView?.updateVariables(Variable(MELONDS_LAYOUT, sidewaysAware(MELONDS_LAYOUT, it)))
            }
            currentValues[DESMUME_LAYOUT]?.let {
                retroGameView?.updateVariables(Variable(DESMUME_LAYOUT, sidewaysAware(DESMUME_LAYOUT, it)))
            }
            publishScreenLayout()
            return
        }
        val currentSwap = citraSwapOverride ?: currentValues[CITRA_SWAP_SCREEN] ?: "Top"
        val next = if (currentSwap == "Bottom") "Top" else "Bottom"
        citraSwapOverride = next
        retroGameView?.updateVariables(Variable(CITRA_SWAP_SCREEN, next))
        publishScreenLayout()
    }

    private fun updateCoreVariables(chosen: List<CoreVariable>) {
        // A layout the player never changed is not in the list: it is the core's stacked default.
        val exposedKeys = systemCoreConfig.exposedSettings.map { it.key }.toSet()
        val defaultLayouts =
            SIDEWAYS_LAYOUTS
                .filterKeys { key -> key in exposedKeys && chosen.none { it.key == key } }
                .map { (key, layouts) -> CoreVariable(key, layouts.first) }
        val defaultCitraSwap =
            if (CITRA_SWAP_SCREEN in exposedKeys && chosen.none { it.key == CITRA_SWAP_SCREEN }) {
                listOf(CoreVariable(CITRA_SWAP_SCREEN, "Top"))
            } else {
                emptyList()
            }
        val options = chosen + defaultLayouts + defaultCitraSwap
        val menuCitraSwap = options.firstOrNull { it.key == CITRA_SWAP_SCREEN }?.value
        if (menuCitraSwap != null) {
            if (lastMenuCitraSwap != null && lastMenuCitraSwap != menuCitraSwap) {
                // Player explicitly changed Main Screen in the Pause Menu -> adopt their new choice.
                citraSwapOverride = null
            }
            lastMenuCitraSwap = menuCitraSwap
        }
        options.forEach { currentValues[it.key] = it.value }
        val (restartOnly, live) = options.partition { it.key in RESTART_ONLY_VARIABLES }

        // Changing these while the game runs makes the PSP core crash (it rebuilds its graphics on the
        // fly and aborts), so they are not sent now: they apply the next time the game starts.
        val atStart = restartOnlyAtStart
        if (atStart == null) {
            restartOnlyAtStart = restartOnly.associate { it.key to it.value }
        } else if (restartOnly.any { atStart[it.key] != it.value }) {
            Toast.makeText(appContext, R.string.setting_restart_to_apply, Toast.LENGTH_LONG).show()
        }

        val hardcoreEnforced =
            RetroAchievementsSessionHolder.state.value.let {
                it.hardcoreActive || it.hardcoreTransitionInProgress
            }
        val updatedVariables =
            live.map { Variable(it.key, sidewaysAware(it.key, it.value)) }
                .filter { variable ->
                    !hardcoreEnforced ||
                        HardcorePolicy.isCoreOptionValueAllowed(
                            systemCoreConfig.coreID,
                            system.id,
                            variable.key.orEmpty(),
                            variable.value.orEmpty(),
                        )
                }
                .toTypedArray()

        updatedVariables.forEach {
            Timber.i("Updating core variable: ${it.key} ${it.value}")
        }

        if (updatedVariables.isNotEmpty()) retroGameView?.updateVariables(*updatedVariables)
        publishScreenLayout()
    }

    private fun handleRetroViewError(errorCode: Int) {
        Timber.e("Error in GLRetroView $errorCode")
        val gameLoaderError =
            when (errorCode) {
                GLRetroView.ERROR_GL_NOT_COMPATIBLE -> GameLoaderError.GLIncompatible
                GLRetroView.ERROR_LOAD_GAME -> GameLoaderError.LoadGame
                GLRetroView.ERROR_LOAD_LIBRARY -> GameLoaderError.LoadCore
                GLRetroView.ERROR_SERIALIZATION -> GameLoaderError.Saves
                else -> GameLoaderError.Generic
            }

        sideEffects.requestFailureFinish(getErrorMessage(gameLoaderError))
    }

    private fun getErrorMessage(gameError: GameLoaderError): String {
        val message =
            when (gameError) {
                is GameLoaderError.GLIncompatible -> {
                    appContext.getString(R.string.game_loader_error_gl_incompatible)
                }
                is GameLoaderError.Generic -> {
                    appContext.getString(R.string.game_loader_error_generic)
                }
                is GameLoaderError.LoadCore -> {
                    appContext.getString(com.swordfish.lemuroid.ext.R.string.game_loader_error_load_core)
                }
                is GameLoaderError.LoadGame -> {
                    appContext.getString(R.string.game_loader_error_load_game)
                }
                is GameLoaderError.Saves -> {
                    appContext.getString(R.string.game_loader_error_save)
                }
                is GameLoaderError.UnsupportedArchitecture -> {
                    appContext.getString(R.string.game_loader_error_unsupported_architecture)
                }
                is GameLoaderError.MissingBiosFiles -> {
                    appContext.getString(R.string.game_loader_error_missing_bios, gameError.missingFiles)
                }
            }

        return message
    }

    private companion object {
        // PSP options that rebuild the emulator's graphics or CPU engine, see updateCoreVariables.
        private const val CITRA_SWAP_SCREEN = "citra_swap_screen"

        /** The stacked layout of each two-screen core, and what it becomes when the phone is sideways. */
        private const val MELONDS_LAYOUT = "melonds_screen_layout1"
        private const val DESMUME_LAYOUT = "desmume_screens_layout"

        private val SIDEWAYS_LAYOUTS =
            mapOf(
                "citra_layout_option" to ("Default Top-Bottom Screen" to "Large Screen, Small Screen"),
                // The DS stays stacked sideways too: the console shell cuts the big main screen and the small
                // other one out of the stacked picture (ScreenSpecs), so filters and Swap work on both cores.
                "melonds_screen_layout1" to ("top-bottom" to "top-bottom"),
                "desmume_screens_layout" to ("top/bottom" to "top/bottom"),
            )

        val RESTART_ONLY_VARIABLES =
            setOf(
                "ppsspp_internal_resolution",
                "ppsspp_texture_scaling_level",
                "ppsspp_texture_scaling_type",
                "ppsspp_cpu_core",
            )
    }
}
