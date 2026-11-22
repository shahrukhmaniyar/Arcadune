package com.swordfish.lemuroid.app.shared.game

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.achievements.GameViewModelAchievements
import com.swordfish.lemuroid.app.shared.achievements.HardcorePolicy
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsSessionHolder
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelCheats
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelInput
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRewind
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelSaves
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelSideEffects
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTilt
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.common.longAnimationDuration
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesCoherencyEngine
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_EXIT
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_FAST_FORWARD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_LOAD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_SAVE
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_REWIND
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_SWAP_SCREENS
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.ceil

class BaseGameScreenViewModel(
    private val appContext: Context,
    private val game: Game,
    private val settingsManager: SettingsManager,
    inputDeviceManager: InputDeviceManager,
    controllerConfigsManager: ControllerConfigsManager,
    private val system: GameSystem,
    private val systemCoreConfig: SystemCoreConfig,
    private val sharedPreferences: SharedPreferences,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
    statesPreviewManager: StatesPreviewManager,
    coreVariablesManager: CoreVariablesManager,
    rumbleManager: RumbleManager,
) : ViewModel(), DefaultLifecycleObserver {
    class Factory(
        private val appContext: Context,
        private val game: Game,
        private val settingsManager: SettingsManager,
        private val inputDeviceManager: InputDeviceManager,
        private val controllerConfigsManager: ControllerConfigsManager,
        private val system: GameSystem,
        private val systemCoreConfig: SystemCoreConfig,
        private val sharedPreferences: SharedPreferences,
        private val savesManager: SavesManager,
        private val statesManager: StatesManager,
        private val statesPreviewManager: StatesPreviewManager,
        private val coreVariablesManager: CoreVariablesManager,
        private val rumbleManager: RumbleManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BaseGameScreenViewModel(
                appContext,
                game,
                settingsManager,
                inputDeviceManager,
                controllerConfigsManager,
                system,
                systemCoreConfig,
                sharedPreferences,
                savesManager,
                statesManager,
                statesPreviewManager,
                coreVariablesManager,
                rumbleManager,
            ) as T
        }
    }

    private val sideEffects = GameViewModelSideEffects(viewModelScope)
    private val transitionFence = HardcorePolicy.TransitionFence()
    private val savesCoherencyEngine = SavesCoherencyEngine(savesManager, statesManager)

    // What the quick buttons may do for this game: fast forward, and quick save / load.
    private val fastForwardSupported = system.fastForwardSupport
    private val maxFrameSpeed = system.maxFrameSpeed
    private val statesSupported = systemCoreConfig.statesSupported
    val retroGameView =
        GameViewModelRetroGameView(
            appContext,
            system,
            systemCoreConfig,
            settingsManager,
            coreVariablesManager,
            sideEffects,
            rumbleManager,
            viewModelScope,
        )
    val achievements =
        GameViewModelAchievements(
            appContext,
            system,
            systemCoreConfig,
            retroGameView,
            viewModelScope,
            onExternalSdkResetRequested = {
                // Native LibretroDroid::step() consumes externalSdkResetRequested_ and performs the clean core restart on GLThread.
                viewModelScope.launch {
                    RetroAchievementsSessionHolder.refreshFromNative()
                }
            },
        )
    private val cheats =
        GameViewModelCheats(
            appContext = appContext,
            game = game,
            retroGameView = retroGameView,
            scope = viewModelScope,
            fence = transitionFence,
            isTransitionInProgress = { RetroAchievementsSessionHolder.state.value.hardcoreTransitionInProgress },
            isHardcoreActive = { achievements.isHardcoreEnforced },
        )
    private val rewind =
        GameViewModelRewind(
            system,
            settingsManager,
            retroGameView,
            viewModelScope,
        ) { achievements.isHardcoreEnforced }
    private val tilt = GameViewModelTilt(appContext, settingsManager)
    private val inputs =
        GameViewModelInput(
            appContext,
            system,
            systemCoreConfig,
            inputDeviceManager,
            controllerConfigsManager,
            retroGameView,
            tilt,
            sideEffects,
            viewModelScope,
        )
    private val touchControls =
        GameViewModelTouchControls(
            settingsManager,
            TouchControllerSettingsManager(sharedPreferences),
            retroGameView,
            inputs,
            tilt,
            sideEffects,
            viewModelScope,
        )
    private val saves =
        GameViewModelSaves(
            appContext = appContext,
            system = system,
            game = game,
            systemCoreConfig = systemCoreConfig,
            retroGameView = retroGameView,
            settingsManager = settingsManager,
            savesManager = savesManager,
            statesManager = statesManager,
            statesPreviewManager = statesPreviewManager,
            sideEffects = sideEffects,
            fence = transitionFence,
            isTransitionInProgress = { RetroAchievementsSessionHolder.state.value.hardcoreTransitionInProgress },
            isHardcoreActive = { achievements.isHardcoreEnforced },
        )

    val loadingState = MutableStateFlow(false)
    private val _frameSpeed = MutableStateFlow(1)
    val frameSpeed: StateFlow<Int> = _frameSpeed.asStateFlow()
    val fastForwardSteps: List<Int> =
        if (fastForwardSupported) {
            GameMenuContract.speedsUpTo(maxFrameSpeed).filter { it > 1 }
        } else {
            emptyList()
        }
    private val stateMutex = Mutex()
    private var cachedLastFrameSpeed: Int? = null
    private val touchMenuHidden = MutableStateFlow(false)
    private var fastForwardButtonPressed = false
    private var exitButtonPressed = false

    fun isFastForwardSupported(): Boolean = fastForwardSupported

    fun fastForwardDotsActive(speed: Int): Int =
        if (speed <= 1 || fastForwardSteps.isEmpty()) {
            0
        } else {
            fastForwardSteps.count { it <= speed }.coerceIn(1, fastForwardSteps.size)
        }

    fun setTouchMenuHidden(hidden: Boolean) {
        touchMenuHidden.value = hidden
    }

    fun syncFastForwardAudioMode() {
        val mode =
            sharedPreferences.getInt(
                GameMenuContract.PREF_FAST_FORWARD_AUDIO_MODE,
                GLRetroView.FAST_FORWARD_AUDIO_KEEP_PITCH,
            )
        retroGameView.retroGameView?.fastForwardAudioMode = mode
    }

    private suspend inline fun withLoading(block: () -> Unit) {
        stateMutex.withLock {
            loadingState.value = true
            try {
                block()
            } finally {
                loadingState.value = false
            }
        }
    }

    fun getGameState(): Flow<GameViewModelRetroGameView.GameState> {
        return retroGameView.getGameState()
    }

    fun getSideEffects(): Flow<GameViewModelSideEffects.UiEffect> {
        return sideEffects.getUiEffects()
    }

    fun getTiltConfiguration(): Flow<TiltConfiguration> {
        return tilt.getTiltConfiguration()
    }

    fun getSimulatedTiltEvents(): Flow<InputState> {
        return tilt.getSimulatedTiltEvents()
    }

    fun getTouchControlsSettings(
        density: Density,
        insets: WindowInsets,
    ): Flow<TouchControllerSettingsManager.Settings?> {
        return touchControls.getTouchControlsSettings(density, insets)
    }

    fun getTouchHapticFeedbackMode(): Flow<HapticFeedbackMode> {
        return touchControls.getTouchHapticFeedbackMode()
    }

    fun createRetroView(
        context: Context,
        lifecycle: LifecycleOwner,
    ): GLRetroView {
        val (gameData, result) = retroGameView.createRetroView(context, lifecycle)
        result.fastForwardAudioMode =
            sharedPreferences.getInt(
                GameMenuContract.PREF_FAST_FORWARD_AUDIO_MODE,
                GLRetroView.FAST_FORWARD_AUDIO_KEEP_PITCH,
            )
        result.frameSpeed = _frameSpeed.value
        cheats.applyWhenReady()
        viewModelScope.launch {
            gameData.quickSaveData?.let {
                saves.restoreAutoSaveAsync(it)
            }
        }
        return result
    }

    suspend fun loadGame(
        applicationContext: Context,
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        gameLoader: GameLoader,
        requestLoadSave: Boolean,
        explicitQuickResume: Boolean = false,
    ) {
        Timber.i("Calling load game: $game")
        val autoSaveEnabled =
            systemCoreConfig.statesSupported &&
                systemCoreConfig.autoSaveSupported &&
                settingsManager.autoSave()
        val hasAutoSaveSnapshot =
            if (requestLoadSave && explicitQuickResume && autoSaveEnabled) {
                withContext(Dispatchers.IO) {
                    val info = statesManager.getAutoSaveInfo(game, systemCoreConfig.coreID)
                    if (!info.exists ||
                        savesCoherencyEngine.shouldDiscardAutoSaveState(game, systemCoreConfig.coreID)
                    ) {
                        false
                    } else {
                        statesManager.getAutoSave(game, systemCoreConfig.coreID)?.metadata?.version ==
                            systemCoreConfig.statesVersion
                    }
                }
            } else {
                false
            }
        val (achievementsConfig, launchDecision) =
            achievements.buildNativeConfig(
                requestedLoadSave = requestLoadSave && autoSaveEnabled,
                explicitQuickResume = explicitQuickResume,
                hasAutoSaveSnapshot = hasAutoSaveSnapshot,
            )
        retroGameView.initialize(
            applicationContext,
            game,
            systemCoreConfig,
            gameLoader,
            launchDecision.loadQuickSaveSnapshot,
            achievementsConfig,
        )
    }

    fun canOpenPauseMenu(): Boolean {
        if (!achievements.isHardcoreEnforced) return true
        val remainingFrames = retroGameView.retroGameView?.canPauseAchievements() ?: 0
        if (!HardcorePolicy.canPause(true, remainingFrames)) {
            val remainingSeconds = ceil(remainingFrames / 60.0).toInt().coerceAtLeast(1)
            sideEffects.showToast(
                appContext.getString(R.string.ra_hardcore_pause_cooldown_toast, remainingSeconds),
            )
            return false
        }
        return true
    }

    fun idleAchievementsWhilePaused() {
        retroGameView.retroGameView?.idleAchievements()
    }

    /**
     * Enables RetroAchievements Hardcore Mode on a running session by fencing async commands,
     * purging rewind/cheats/queued inputs, preserving ordinary SRAM, performing a clean core
     * reload from unpatched boot media, and resuming Hardcore evaluation only after the clean
     * reset succeeds.
     */
    suspend fun enableHardcoreWithCleanRestart() =
        withLoading {
            RetroAchievementsPreferences.setHardcoreEnabled(appContext, true)
            if (!RetroAchievementsPreferences.isHardcoreApproved()) {
                RetroAchievementsSessionHolder.updateState { it.copy(hardcoreActive = false) }
                sideEffects.showToast(appContext.getString(R.string.ra_hardcore_registration_note))
                return@withLoading
            }
            if (!achievements.isCoreHardcoreCompliant) {
                RetroAchievementsSessionHolder.updateState { it.copy(hardcoreActive = false) }
                sideEffects.showToast(achievements.coreUnsupportedReason)
                return@withLoading
            }

            val view = retroGameView.retroGameView ?: return@withLoading
            transitionFence.bumpEpoch()
            achievements.setTransitionInProgress(true)
            try {
                rewind.clearAndDisable()
                touchControls.cancelPendingInputs()
                setFrameSpeed(1)
                cheats.clearForHardcoreNow()

                runCatching {
                    val sram = withContext(Dispatchers.IO) { view.serializeSRAM(true) }
                    if (sram.isNotEmpty()) {
                        savesManager.setSaveRAM(game, sram)
                    }
                }.onFailure { Timber.w(it, "Could not flush SRAM before Hardcore clean restart") }

                val restartedCleanly =
                    withContext(Dispatchers.IO) {
                        view.setHardcoreMode(true, true)
                    }

                syncFastForwardAudioMode()
                view.frameSpeed = _frameSpeed.value
                RetroAchievementsSessionHolder.refreshFromNative()

                if (restartedCleanly) {
                    sideEffects.showToast(appContext.getString(R.string.ra_hardcore_restart_success_toast))
                } else {
                    RetroAchievementsSessionHolder.updateState { it.copy(hardcoreActive = false) }
                    val failMsg = appContext.getString(R.string.ra_hardcore_restart_failed_toast)
                    achievements.showStatusBanner(
                        title = appContext.getString(R.string.ra_mode_casual_label),
                        subtitle = failMsg,
                        isError = true,
                    )
                    sideEffects.showToast(failMsg)
                }
            } finally {
                achievements.setTransitionInProgress(false)
            }
        }

    /** Leaves Hardcore Mode immediately without restarting the game. */
    suspend fun switchToCasualImmediate() =
        withLoading {
            RetroAchievementsPreferences.setHardcoreEnabled(appContext, false)
            transitionFence.bumpEpoch()
            val view = retroGameView.retroGameView
            if (view != null) {
                withContext(Dispatchers.IO) {
                    view.setHardcoreMode(false, true)
                }
            }
            RetroAchievementsSessionHolder.updateState {
                it.copy(hardcoreActive = false, hardcoreTransitionInProgress = false)
            }
            RetroAchievementsSessionHolder.refreshFromNative()
            cheats.apply()
            sideEffects.showToast(appContext.getString(R.string.ra_casual_switched_toast))
        }

    /** Applies the cheats the player has switched on in the game menu. */
    fun applyCheats() = cheats.apply()

    fun onGameClosed() = cheats.gameClosed()

    fun showEditControls(show: Boolean) {
        touchControls.showEditControls(show)
    }

    fun isEditControlShown(): Flow<Boolean> {
        return touchControls.isEditControlsShown()
    }

    fun updateTouchControllerSettings(touchControllerSettings: TouchControllerSettingsManager.Settings) {
        touchControls.updateTouchControllerSettings(touchControllerSettings)
    }

    fun resetTouchControls() {
        touchControls.resetTouchControls()
    }

    fun onScreenOrientationChanged(orientation: TouchControllerSettingsManager.Orientation) {
        touchControls.updateScreenOrientation(orientation)
    }

    fun isTouchControllerVisible(): Flow<Boolean> {
        return touchControls.isTouchControllerVisible()
    }

    fun getTouchControllerConfig(): Flow<ControllerConfig> {
        return touchControls.getTouchControllerConfig()
    }

    fun getAvailableTouchControllerConfigs(): List<ControllerConfig> {
        return systemCoreConfig.controllerConfigs[0]
            .orEmpty()
            .distinctBy { it.touchControllerID }
    }

    fun selectTouchControllerConfig(config: ControllerConfig) {
        val prefKey = ControllerConfigsManager.getSharedPreferencesId(system.id.dbname, systemCoreConfig.coreID, 0)
        sharedPreferences.edit().putString(prefKey, config.name).apply()
        inputs.refreshControllerConfigs()
    }

    fun changeTiltConfiguration(tiltConfig: TiltConfiguration) {
        tilt.changeTiltConfiguration(tiltConfig)
    }

    fun isMenuPressed(): Flow<Boolean> {
        return touchControls.isMenuPressed()
    }

    suspend fun saveSlot(index: Int) {
        if (loadingState.value) return
        withLoading {
            saves.saveSlot(index)
        }
    }

    suspend fun loadSlot(index: Int) {
        if (loadingState.value) return
        withLoading {
            saves.loadSlot(index)
        }
    }

    fun saveQuickSave() {
        Timber.d("Saving quick save")
        if (!statesSupported) {
            sideEffects.showToast(appContext.getString(R.string.game_toast_states_not_supported))
            return
        }
        if (loadingState.value) return
        viewModelScope.launch {
            withLoading {
                saves.saveQuickSave()
            }
        }
    }

    fun loadQuickSave() {
        Timber.d("Loading quick save")
        if (!statesSupported) {
            sideEffects.showToast(appContext.getString(R.string.game_toast_states_not_supported))
            return
        }
        if (loadingState.value) return
        viewModelScope.launch {
            withLoading {
                saves.loadQuickSave()
            }
        }
    }

    /** Sets the game speed (1 is normal) and remembers the last fast one, which the button turns back on. */
    fun setFrameSpeed(requested: Int) {
        // Never above what this system's core survives (the N64 core crashes at 8X), and never < 1x slowdown.
        val speed = HardcorePolicy.clampFrameSpeed(requested, maxFrameSpeed)
        _frameSpeed.value = speed
        retroGameView.retroGameView?.frameSpeed = speed
        if (speed > 1) {
            cachedLastFrameSpeed = speed
            sharedPreferences.edit().putInt(
                GameMenuContract.PREF_LAST_FRAME_SPEED,
                speed,
            ).apply()
        }
    }

    /** Turns fast-forward off, or on at the speed last used (2X the first time). */
    fun toggleFastForward() {
        Timber.d("Toggling fast forward")
        val current = retroGameView.retroGameView?.frameSpeed ?: _frameSpeed.value
        val last =
            cachedLastFrameSpeed ?: sharedPreferences
                .getInt(GameMenuContract.PREF_LAST_FRAME_SPEED, 2)
                .also { cachedLastFrameSpeed = it }
        setFrameSpeed(if (current > 1) 1 else last)
    }

    /** Cycles through all supported speeds for this core (1x -> 2x -> 4x -> 6x -> 8x -> 1x). */
    fun cycleFastForward() {
        if (!fastForwardSupported) return
        val current = retroGameView.retroGameView?.frameSpeed ?: _frameSpeed.value
        val next = GameMenuContract.nextFrameSpeed(current, maxFrameSpeed)
        setFrameSpeed(next)
    }

    /** Each tap on the on-screen Fast Forward button cycles to the next supported speed immediately. */
    private fun onFastForwardButton(pressed: Boolean) {
        val wasPressed = fastForwardButtonPressed
        fastForwardButtonPressed = pressed
        if (pressed && !wasPressed) {
            cycleFastForward()
        }
    }

    suspend fun reset() =
        withLoading {
            try {
                delay(appContext.longAnimationDuration().toLong())
                retroGameView.retroGameViewFlow().reset()
            } catch (e: Throwable) {
                Timber.e(e, "Error in reset")
            }
        }

    fun requestFinish() {
        if (loadingState.value) return
        viewModelScope.launch {
            withLoading {
                // Only the quick part happens here: the emulator hands over a copy of its save data.
                val snapshot = saves.captureSaveSnapshot(true)
                // Compressing and writing it to disk happens after the game window has closed. The
                // game process stays alive until it is done (see GameService), so nothing is lost.
                GameService.schedule { writeSnapshotOrWarn(snapshot) }
                sideEffects.requestSuccessfulFinish()
            }
        }
    }

    /** Opens the pause menu on system Back if the touch Menu button was hidden, otherwise exits cleanly. */
    fun onSystemBackPressed() {
        if (touchMenuHidden.value) {
            if (loadingState.value) return
            viewModelScope.launch {
                sideEffects.showMenu(tilt, inputs)
            }
        } else {
            requestFinish()
        }
    }

    private fun onExitButton(pressed: Boolean) {
        val wasPressed = exitButtonPressed
        exitButtonPressed = pressed
        if (!pressed && wasPressed) {
            requestFinish()
        }
    }

    fun requestBackgroundSave() {
        if (loadingState.value) return
        GameService.schedule {
            stateMutex.withLock {
                val snapshot = saves.captureSaveSnapshot(false)
                writeSnapshotOrWarn(snapshot)
            }
        }
    }

    /** A save that cannot be written is not silent: the player gets a notification. */
    private suspend fun writeSnapshotOrWarn(snapshot: GameViewModelSaves.SaveSnapshot?) {
        if (!saves.writeSaveSnapshot(snapshot)) {
            NotificationsManager(appContext).showSaveFailedNotification(game.displayName())
        }
    }

    fun handleVirtualInputEvent(events: List<InputEvent>) {
        // The rewind, swap-screens and quick buttons are handled here and never reach the emulator.
        val (appEvents, gameEvents) = events.partition { it is InputEvent.Button && it.id in APP_KEYS }
        appEvents.lastOrNull { (it as InputEvent.Button).id == KEYCODE_REWIND }
            ?.let { rewind.onRewindPressed((it as InputEvent.Button).pressed) }

        fun pressed(key: Int) = appEvents.any { it is InputEvent.Button && it.id == key && it.pressed }
        if (pressed(KEYCODE_SWAP_SCREENS)) retroGameView.swapScreens()
        // Process all button transitions in order so rapid 1-frame taps never miss.
        if (fastForwardSupported) {
            appEvents.forEach { ev ->
                if (ev is InputEvent.Button && ev.id == KEYCODE_FAST_FORWARD) {
                    onFastForwardButton(ev.pressed)
                }
            }
        }
        appEvents.forEach { ev ->
            if (ev is InputEvent.Button && ev.id == KEYCODE_EXIT) {
                onExitButton(ev.pressed)
            }
        }
        if (pressed(KEYCODE_QUICK_SAVE)) saveQuickSave()
        if (pressed(KEYCODE_QUICK_LOAD)) loadQuickSave()
        if (gameEvents.isNotEmpty()) touchControls.handleVirtualInputEvent(gameEvents)
    }

    fun isRewindAvailable(): Flow<Boolean> = rewind.available

    /** Two-screen consoles show their screens side by side while the phone is sideways. */
    fun onOrientationChanged(isLandscape: Boolean) = retroGameView.onOrientationChanged(isLandscape)

    /** The screen settings the core draws with right now, for the console shell. */
    fun getScreenLayout(): Flow<Map<String, String>> = retroGameView.screenLayout

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        owner.lifecycle.addObserver(tilt)
        owner.lifecycle.addObserver(inputs)
        owner.lifecycle.addObserver(retroGameView)
        owner.lifecycle.addObserver(achievements)
        owner.lifecycle.addObserver(touchControls)
        owner.lifecycle.addObserver(rewind)
    }

    fun sendKeyEvent(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        return inputs.sendKeyEvent(keyCode, event)
    }

    fun sendMotionEvent(event: MotionEvent): Boolean {
        return inputs.sendMotionEvent(event)
    }
}

/** Keys of skin buttons the app handles itself (rewind, swap screens, the quick buttons); they never reach the emulator. */
private val APP_KEYS =
    setOf(
        KEYCODE_REWIND,
        KEYCODE_SWAP_SCREENS,
        KEYCODE_FAST_FORWARD,
        KEYCODE_EXIT,
        KEYCODE_QUICK_SAVE,
        KEYCODE_QUICK_LOAD,
    )
