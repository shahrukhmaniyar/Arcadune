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
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.achievements.GameViewModelAchievements
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
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_FAST_FORWARD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_LOAD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_SAVE
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_REWIND
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_SWAP_SCREENS
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class BaseGameScreenViewModel(
    private val appContext: Context,
    private val game: Game,
    settingsManager: SettingsManager,
    inputDeviceManager: InputDeviceManager,
    controllerConfigsManager: ControllerConfigsManager,
    system: GameSystem,
    systemCoreConfig: SystemCoreConfig,
    private val sharedPreferences: SharedPreferences,
    savesManager: SavesManager,
    statesManager: StatesManager,
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
            retroGameView,
            viewModelScope,
        )
    private val cheats =
        GameViewModelCheats(
            appContext,
            game,
            retroGameView,
            viewModelScope,
        ) { achievements.isHardcoreEnforced }
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
            appContext,
            system,
            game,
            systemCoreConfig,
            retroGameView,
            settingsManager,
            savesManager,
            statesManager,
            statesPreviewManager,
            sideEffects,
        ) { achievements.isHardcoreEnforced }

    val loadingState = MutableStateFlow(false)
    private val stateMutex = Mutex()
    private var cachedLastFrameSpeed: Int? = null

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
    ) {
        Timber.i("Calling load game: $game")
        val achievementsConfig = achievements.buildNativeConfig()
        retroGameView.initialize(
            applicationContext,
            game,
            systemCoreConfig,
            gameLoader,
            requestLoadSave,
            achievementsConfig,
        )
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
        if (loadingState.value || !statesSupported) return
        viewModelScope.launch {
            withLoading {
                saves.saveQuickSave()
            }
        }
    }

    fun loadQuickSave() {
        Timber.d("Loading quick save")
        if (loadingState.value || !statesSupported) return
        viewModelScope.launch {
            withLoading {
                saves.loadQuickSave()
            }
        }
    }

    /** Sets the game speed (1 is normal) and remembers the last fast one, which the button turns back on. */
    fun setFrameSpeed(requested: Int) {
        // Never above what this system's core survives (the N64 core crashes at 8X).
        val speed = requested.coerceIn(1, maxFrameSpeed)
        retroGameView.retroGameView?.frameSpeed = speed
        if (speed > 1) {
            cachedLastFrameSpeed = speed
            sharedPreferences.edit().putInt(
                com.swordfish.lemuroid.app.shared.GameMenuContract.PREF_LAST_FRAME_SPEED,
                speed,
            ).apply()
        }
    }

    /** Turns fast-forward off, or on at the speed last used (2X the first time). */
    fun toggleFastForward() {
        Timber.d("Toggling fast forward")
        val current = retroGameView.retroGameView?.frameSpeed ?: return
        val last =
            cachedLastFrameSpeed ?: sharedPreferences
                .getInt(com.swordfish.lemuroid.app.shared.GameMenuContract.PREF_LAST_FRAME_SPEED, 2)
                .also { cachedLastFrameSpeed = it }
        setFrameSpeed(if (current > 1) 1 else last)
    }

    private var fastForwardHold: kotlinx.coroutines.Job? = null
    private var fastForwardHeldLong = false

    /** A tap on the fast-forward button toggles it; holding it opens the speed picker. */
    private fun onFastForwardButton(pressed: Boolean) {
        if (pressed) {
            if (fastForwardHold?.isActive == true || fastForwardHeldLong) return
            fastForwardHold =
                viewModelScope.launch {
                    delay(FAST_FORWARD_HOLD_MS)
                    fastForwardHeldLong = true
                    sideEffects.showMenu(
                        tilt,
                        inputs,
                        com.swordfish.lemuroid.app.shared.GameMenuContract.START_ROUTE_FAST_FORWARD,
                    )
                }
        } else {
            fastForwardHold?.cancel()
            if (!fastForwardHeldLong) toggleFastForward()
            fastForwardHeldLong = false
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
                val snapshot = saves.captureSaveSnapshot(true)
                saves.writeSaveSnapshot(snapshot)
                sideEffects.requestSuccessfulFinish()
            }
        }
    }

    fun requestBackgroundSave() {
        if (loadingState.value) return
        GameService.schedule {
            stateMutex.withLock {
                val snapshot = saves.captureSaveSnapshot(false)
                saves.writeSaveSnapshot(snapshot)
            }
        }
    }

    fun handleVirtualInputEvent(events: List<InputEvent>) {
        // The rewind, swap-screens and quick buttons are handled here and never reach the emulator.
        val (appEvents, gameEvents) = events.partition { it is InputEvent.Button && it.id in APP_KEYS }
        appEvents.lastOrNull { (it as InputEvent.Button).id == KEYCODE_REWIND }
            ?.let { rewind.onRewindPressed((it as InputEvent.Button).pressed) }

        fun pressed(key: Int) = appEvents.any { it is InputEvent.Button && it.id == key && it.pressed }
        if (pressed(KEYCODE_SWAP_SCREENS)) retroGameView.swapScreens()
        // The quick buttons act when pressed, like Delta's.
        if (fastForwardSupported) {
            appEvents.lastOrNull { it is InputEvent.Button && it.id == KEYCODE_FAST_FORWARD }
                ?.let { onFastForwardButton((it as InputEvent.Button).pressed) }
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
    setOf(KEYCODE_REWIND, KEYCODE_SWAP_SCREENS, KEYCODE_FAST_FORWARD, KEYCODE_QUICK_SAVE, KEYCODE_QUICK_LOAD)
private const val FAST_FORWARD_HOLD_MS = 450L
