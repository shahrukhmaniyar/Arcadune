package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.app.mobile.feature.game.GameActivity
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOption
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelSideEffects
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.tv.game.TVGameActivity
import com.swordfish.lemuroid.common.animationDuration
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.common.dump
import com.swordfish.lemuroid.common.kotlin.serializable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.library.ExposedSetting
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.skin.SkinArtLoader
import dagger.Lazy
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
abstract class BaseGameActivity : ImmersiveActivity() {
    protected lateinit var game: Game
    private lateinit var system: GameSystem
    protected lateinit var systemCoreConfig: SystemCoreConfig

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var savesManager: SavesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    @Inject
    lateinit var coreVariablesManager: CoreVariablesManager

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var gameLoader: GameLoader

    @Inject
    lateinit var controllerConfigsManager: ControllerConfigsManager

    @Inject
    lateinit var rumbleManager: RumbleManager

    @Inject
    lateinit var sharedPreferences: Lazy<SharedPreferences>

    private lateinit var baseGameScreenViewModel: BaseGameScreenViewModel

    private lateinit var playSessionId: String
    private var isActivityResumed = false
    private var isGameLoaded = false
    private var activeResumeElapsedMs: Long? = null
    private var unflushedPlayTimeMs: Long = 0L
    private var totalSessionPlayTimeMs: Long = 0L
    private var sessionPlayCountRecorded = false
    private var finishTriggered = false
    private var isOpeningMenu = false
    private var pauseIdleJob: Job? = null

    @Volatile
    private var handlingUncaughtException = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playSessionId =
            savedInstanceState?.getString(STATE_PLAY_SESSION_ID)
                ?: UUID.randomUUID().toString()
        sessionPlayCountRecorded =
            savedInstanceState?.getBoolean(STATE_PLAY_COUNT_RECORDED, false)
                ?: false
        totalSessionPlayTimeMs =
            savedInstanceState?.getLong(STATE_TOTAL_PLAY_TIME_MS, 0L)
                ?: 0L
        setUpExceptionsHandler()
        GameService.startService(applicationContext, intent)
        game = intent.getSerializableExtra(EXTRA_GAME) as Game
        systemCoreConfig = intent.getSerializableExtra(EXTRA_SYSTEM_CORE_CONFIG) as SystemCoreConfig
        system = GameSystem.findById(game.systemId)
        preloadSkinArt()

        val viewModel by viewModels<BaseGameScreenViewModel> {
            BaseGameScreenViewModel.Factory(
                applicationContext,
                game,
                settingsManager,
                inputDeviceManager,
                controllerConfigsManager,
                system,
                systemCoreConfig,
                sharedPreferences.get(),
                savesManager,
                statesManager,
                statesPreviewManager,
                coreVariablesManager,
                rumbleManager,
            )
        }

        baseGameScreenViewModel = viewModel

        lifecycle.addObserver(baseGameScreenViewModel)

        setContent {
            AppTheme {
                BaseGameScreen(viewModel = baseGameScreenViewModel, showScreenWhileLoading = showsScreenWhileLoading) {
                    GameScreen(viewModel)
                }
            }
        }

        lifecycleScope.launch {
            baseGameScreenViewModel.loadGame(
                applicationContext,
                game,
                systemCoreConfig,
                gameLoader,
                intent.getBooleanExtra(EXTRA_LOAD_SAVE, false),
                intent.getBooleanExtra(EXTRA_EXPLICIT_QUICK_RESUME, false),
            )
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    baseGameScreenViewModel.onSystemBackPressed()
                }
            },
        )

        initialiseFlows()
    }

    @Composable
    abstract fun GameScreen(viewModel: BaseGameScreenViewModel)

    /** True when the screen (the skin) should show while the game is still loading, with a small indicator over it. */
    protected open val showsScreenWhileLoading: Boolean = false

    private fun initialiseFlows() {
        launchOnState(Lifecycle.State.CREATED) {
            initializeViewModelsEffectsFlow()
        }
        launchOnState(Lifecycle.State.CREATED) {
            baseGameScreenViewModel.loadingState.collect { loading ->
                if (!loading && !isGameLoaded) {
                    isGameLoaded = true
                    syncPlayTimeClock()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PLAY_SESSION_ID, playSessionId)
        outState.putBoolean(STATE_PLAY_COUNT_RECORDED, sessionPlayCountRecorded)
        outState.putLong(STATE_TOTAL_PLAY_TIME_MS, totalSessionPlayTimeMs)
    }

    private fun syncPlayTimeClock() {
        if (isActivityResumed && isGameLoaded) {
            if (activeResumeElapsedMs == null) {
                activeResumeElapsedMs = SystemClock.elapsedRealtime()
            }
        } else {
            pausePlayTimeClock()
        }
    }

    private fun pausePlayTimeClock() {
        val startedAt = activeResumeElapsedMs ?: return
        activeResumeElapsedMs = null
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        unflushedPlayTimeMs += elapsed
        totalSessionPlayTimeMs += elapsed
    }

    private fun flushPlayTimeToJournal() {
        if (!::game.isInitialized || !::playSessionId.isInitialized) {
            return
        }
        if (isActivityResumed && isGameLoaded) {
            val now = SystemClock.elapsedRealtime()
            val startedAt = activeResumeElapsedMs
            if (startedAt != null) {
                val elapsed = (now - startedAt).coerceAtLeast(0L)
                unflushedPlayTimeMs += elapsed
                totalSessionPlayTimeMs += elapsed
            }
            activeResumeElapsedMs = now
        }
        if (!isGameLoaded && unflushedPlayTimeMs <= 0L) {
            return
        }
        val deltaMs = unflushedPlayTimeMs
        val playCountDelta = if (!sessionPlayCountRecorded) 1 else 0
        if (deltaMs <= 0L && playCountDelta == 0) {
            return
        }
        unflushedPlayTimeMs = 0L
        sessionPlayCountRecorded = true
        PlayTimeTracker.appendPendingJournal(
            context = applicationContext,
            sessionId = playSessionId,
            gameId = game.id,
            playedAtMs = System.currentTimeMillis(),
            playCountDelta = playCountDelta,
            playTimeDeltaMs = deltaMs,
        )
    }

    private fun setUpExceptionsHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            if (handlingUncaughtException) {
                previousHandler?.uncaughtException(thread, exception)
                return@setDefaultUncaughtExceptionHandler
            }
            handlingUncaughtException = true
            runOnUiThread {
                runCatching { performUnexpectedErrorFinish(exception) }
                    .onFailure { previousHandler?.uncaughtException(thread, exception) }
            }
        }
    }

    private fun transformExposedSetting(
        exposedSetting: ExposedSetting,
        coreOptions: List<CoreOption>,
    ): LemuroidCoreOption? {
        return coreOptions
            .firstOrNull { it.variable.key == exposedSetting.key }
            ?.let { LemuroidCoreOption(exposedSetting, it) }
    }

    private fun displayOptionsDialog(
        currentTiltConfiguration: TiltConfiguration,
        tiltConfigurations: List<TiltConfiguration>,
        startRoute: String? = null,
    ) {
        if (baseGameScreenViewModel.loadingState.value) {
            return
        }
        if (!baseGameScreenViewModel.canOpenPauseMenu()) {
            return
        }

        val coreOptions = getCoreOptions()

        val options =
            systemCoreConfig.exposedSettings
                .mapNotNull { transformExposedSetting(it, coreOptions) }

        val advancedOptions =
            systemCoreConfig.exposedAdvancedSettings
                .mapNotNull { transformExposedSetting(it, coreOptions) }

        val currentDisk =
            if (system.hasMultiDiskSupport) {
                baseGameScreenViewModel.retroGameView.retroGameView?.getCurrentDisk() ?: 0
            } else {
                0
            }
        val availableDisks =
            if (system.hasMultiDiskSupport) {
                baseGameScreenViewModel.retroGameView.retroGameView?.getAvailableDisks() ?: 0
            } else {
                0
            }

        val intent =
            Intent(this, getDialogClass()).apply {
                this.putExtra(GameMenuContract.EXTRA_CORE_OPTIONS, options.toTypedArray())
                this.putExtra(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS, advancedOptions.toTypedArray())
                this.putExtra(GameMenuContract.EXTRA_CURRENT_DISK, currentDisk)
                this.putExtra(GameMenuContract.EXTRA_DISKS, availableDisks)
                this.putExtra(GameMenuContract.EXTRA_GAME, game)
                this.putExtra(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                this.putExtra(
                    GameMenuContract.EXTRA_AUDIO_ENABLED,
                    baseGameScreenViewModel.retroGameView.retroGameView?.audioEnabled,
                )
                this.putExtra(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, system.fastForwardSupport)
                this.putExtra(GameMenuContract.EXTRA_MAX_FRAME_SPEED, system.maxFrameSpeed)
                this.putExtra(
                    GameMenuContract.EXTRA_FAST_FORWARD,
                    (baseGameScreenViewModel.retroGameView.retroGameView?.frameSpeed ?: 1) > 1,
                )
                this.putExtra(
                    GameMenuContract.EXTRA_FRAME_SPEED,
                    baseGameScreenViewModel.retroGameView.retroGameView?.frameSpeed ?: 1,
                )
                this.putExtra(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG, currentTiltConfiguration)
                startRoute?.let { this.putExtra(GameMenuContract.EXTRA_START_ROUTE, it) }
                // TODO PADS... Make sure to avoid passing this if a physical pad is connected.
                this.putExtra(GameMenuContract.EXTRA_TILT_ALL_CONFIGS, tiltConfigurations.toTypedArray())
            }
        isOpeningMenu = true
        pauseIdleJob?.cancel()
        pauseIdleJob =
            lifecycleScope.launch(Dispatchers.IO) {
                while (isOpeningMenu && isActive) {
                    delay(1000L)
                    if (isOpeningMenu) {
                        baseGameScreenViewModel.idleAchievementsWhilePaused()
                    }
                }
            }
        startActivityForResult(intent, DIALOG_REQUEST)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    protected abstract fun getDialogClass(): Class<out Activity>

    private fun getCoreOptions(): List<CoreOption> {
        return baseGameScreenViewModel.retroGameView.retroGameView?.getVariables()
            ?.mapNotNull {
                val coreOptionResult =
                    runCatching {
                        CoreOption.fromLibretroDroidVariable(it)
                    }
                coreOptionResult.getOrNull()
            } ?: listOf()
    }

    private suspend fun initializeViewModelsEffectsFlow() {
        baseGameScreenViewModel.getSideEffects()
            .collect {
                when (it) {
                    is GameViewModelSideEffects.UiEffect.ShowMenu ->
                        displayOptionsDialog(
                            it.currentTiltConfiguration,
                            it.tiltConfigurations,
                            it.startRoute,
                        )
                    is GameViewModelSideEffects.UiEffect.ShowToast -> displayToast(it.message)
                    is GameViewModelSideEffects.UiEffect.SuccessfulFinish -> performSuccessfulActivityFinish()
                    is GameViewModelSideEffects.UiEffect.FailureFinish -> performErrorFinish(it.message)
                    is GameViewModelSideEffects.UiEffect.SaveQuickSave -> performSaveQuickSave()
                    is GameViewModelSideEffects.UiEffect.LoadQuickSave -> performLoadQuickSave()
                    is GameViewModelSideEffects.UiEffect.ToggleFastForward -> performToggleFastForward()
                }
            }
    }

    private fun performSaveQuickSave() {
        baseGameScreenViewModel.saveQuickSave()
    }

    private fun performLoadQuickSave() {
        baseGameScreenViewModel.loadQuickSave()
    }

    private fun performToggleFastForward() {
        baseGameScreenViewModel.toggleFastForward()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = baseGameScreenViewModel.sendMotionEvent(event)
        if (handled) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val handled = baseGameScreenViewModel.sendKeyEvent(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val handled = baseGameScreenViewModel.sendKeyEvent(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun performSuccessfulActivityFinish() {
        pausePlayTimeClock()
        flushPlayTimeToJournal()
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_SESSION_DURATION, totalSessionPlayTimeMs)
                putExtra(PLAY_GAME_RESULT_GAME, intent.getSerializableExtra(EXTRA_GAME))
                putExtra(PLAY_GAME_RESULT_LEANBACK, intent.getBooleanExtra(EXTRA_LEANBACK, false))
            }

        setResult(RESULT_OK, resultIntent)
        finishAndExitProcess()
    }

    private fun performUnexpectedErrorFinish(exception: Throwable) {
        Timber.e(exception, "Handling java exception in BaseGameActivity")
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_ERROR, exception.message)
            }

        setResult(RESULT_UNEXPECTED_ERROR, resultIntent)
        finishAndExitProcess()
    }

    private fun performErrorFinish(message: String) {
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_ERROR, message)
            }

        setResult(RESULT_ERROR, resultIntent)
        finishAndExitProcess()
    }

    private fun finishAndExitProcess() {
        onFinishTriggered()
        GlobalScope.launch(Dispatchers.IO) {
            delay(animationDuration().toLong())
            baseGameScreenViewModel.retroGameView.retroGameView?.destroySynchronously()
            GameService.requestTermination()
        }
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /**
     * Starts reading this console's skin pictures right away, in the background, so the skin is
     * ready when the game screen is first drawn instead of appearing after the game has loaded.
     */
    private fun preloadSkinArt() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val controllers =
            systemCoreConfig.controllerConfigs.values
                .flatten()
                .map { it.touchControllerID }
                .distinct()
        if (controllers.isEmpty()) return
        val context = applicationContext
        Thread(
            {
                // The layout first (placing the extra buttons takes a moment), then its pictures, both
                // cached, so the game screen finds them ready instead of working them out itself.
                controllers.forEach { id -> runCatching { com.swordfish.touchinput.skin.ArcaduneSkins.forController(id) } }
                controllers.forEach { id -> runCatching { SkinArtLoader.load(context, id.name, landscape) } }
            },
            "skin-art-preload",
        ).start()
    }

    open fun onFinishTriggered() {
        finishTriggered = true
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        isOpeningMenu = false
        pauseIdleJob?.cancel()
        pauseIdleJob = null
        syncPlayTimeClock()
        baseGameScreenViewModel.syncFastForwardAudioMode()
    }

    override fun onPause() {
        isActivityResumed = false
        pausePlayTimeClock()
        super.onPause()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            flushPlayTimeToJournal()
        }
        if (!finishTriggered && !isFinishing && !isChangingConfigurations && !isOpeningMenu) {
            baseGameScreenViewModel.requestBackgroundSave()
        }
        super.onStop()
    }

    override fun onDestroy() {
        val terminating = !isChangingConfigurations
        if (terminating) {
            pausePlayTimeClock()
            flushPlayTimeToJournal()
            baseGameScreenViewModel.onGameClosed()
            baseGameScreenViewModel.retroGameView.retroGameView?.destroySynchronously()
        }
        super.onDestroy()
        if (terminating) {
            GameService.requestTermination()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_REQUEST) {
            isOpeningMenu = false
            pauseIdleJob?.cancel()
            pauseIdleJob = null
            baseGameScreenViewModel.syncFastForwardAudioMode()
            Timber.i("Game menu dialog response: ${data?.extras.dump()}")
            val requestedHardcoreChange =
                if (data?.hasExtra(GameMenuContract.RESULT_SET_HARDCORE) == true) {
                    data.getBooleanExtra(GameMenuContract.RESULT_SET_HARDCORE, false)
                } else {
                    null
                }
            if (requestedHardcoreChange != null) {
                lifecycleScope.launch {
                    if (requestedHardcoreChange) {
                        baseGameScreenViewModel.enableHardcoreWithCleanRestart()
                    } else {
                        baseGameScreenViewModel.switchToCasualImmediate()
                    }
                }
            } else if (data?.getBooleanExtra(GameMenuContract.RESULT_QUIT, false) != true) {
                // Cheats are applied in the background, and not at all when the player is quitting.
                baseGameScreenViewModel.applyCheats()
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_RESET, false) == true) {
                lifecycleScope.launch {
                    baseGameScreenViewModel.reset()
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_SAVE) == true) {
                val saveIndex = data.getIntExtra(GameMenuContract.RESULT_SAVE, 0)
                lifecycleScope.launch {
                    baseGameScreenViewModel.saveSlot(saveIndex)
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_LOAD) == true) {
                val loadIndex = data.getIntExtra(GameMenuContract.RESULT_LOAD, 0)
                lifecycleScope.launch {
                    baseGameScreenViewModel.loadSlot(loadIndex)
                }
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_QUIT, false) == true) {
                baseGameScreenViewModel.requestFinish()
            }
            if (data?.hasExtra(GameMenuContract.RESULT_CHANGE_DISK) == true) {
                val index = data.getIntExtra(GameMenuContract.RESULT_CHANGE_DISK, 0)
                baseGameScreenViewModel.retroGameView.retroGameView?.changeDisk(index)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_ENABLE_AUDIO) == true) {
                baseGameScreenViewModel.retroGameView.retroGameView?.apply {
                    this.audioEnabled =
                        data.getBooleanExtra(
                            GameMenuContract.RESULT_ENABLE_AUDIO,
                            true,
                        )
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD) == true) {
                val fastForwardEnabled =
                    data.getBooleanExtra(
                        GameMenuContract.RESULT_ENABLE_FAST_FORWARD,
                        false,
                    )
                baseGameScreenViewModel.setFrameSpeed(if (fastForwardEnabled) 2 else 1)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_FRAME_SPEED) == true) {
                baseGameScreenViewModel.setFrameSpeed(data.getIntExtra(GameMenuContract.RESULT_FRAME_SPEED, 1))
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, false) == true) {
                baseGameScreenViewModel.showEditControls(true)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_CHANGE_TILT_CONFIG) == true) {
                val tiltConfig = data.serializable<TiltConfiguration>(GameMenuContract.RESULT_CHANGE_TILT_CONFIG)
                if (tiltConfig != null) {
                    baseGameScreenViewModel.changeTiltConfiguration(tiltConfig)
                }
            }
        }
    }

    companion object {
        const val DIALOG_REQUEST = 100

        private const val EXTRA_GAME = "GAME"
        private const val EXTRA_LOAD_SAVE = "LOAD_SAVE"
        private const val EXTRA_EXPLICIT_QUICK_RESUME = "EXPLICIT_QUICK_RESUME"
        private const val EXTRA_LEANBACK = "LEANBACK"
        private const val EXTRA_SYSTEM_CORE_CONFIG = "EXTRA_SYSTEM_CORE_CONFIG"
        private const val STATE_PLAY_SESSION_ID = "STATE_PLAY_SESSION_ID"
        private const val STATE_PLAY_COUNT_RECORDED = "STATE_PLAY_COUNT_RECORDED"
        private const val STATE_TOTAL_PLAY_TIME_MS = "STATE_TOTAL_PLAY_TIME_MS"

        const val REQUEST_PLAY_GAME = 1001
        const val PLAY_GAME_RESULT_SESSION_DURATION = "PLAY_GAME_RESULT_SESSION_DURATION"
        const val PLAY_GAME_RESULT_GAME = "PLAY_GAME_RESULT_GAME"
        const val PLAY_GAME_RESULT_LEANBACK = "PLAY_GAME_RESULT_LEANBACK"
        const val PLAY_GAME_RESULT_ERROR = "PLAY_GAME_RESULT_ERROR"

        const val RESULT_ERROR = Activity.RESULT_FIRST_USER + 2
        const val RESULT_UNEXPECTED_ERROR = Activity.RESULT_FIRST_USER + 3

        fun launchGame(
            activity: Activity,
            systemCoreConfig: SystemCoreConfig,
            game: Game,
            loadSave: Boolean,
            useLeanback: Boolean,
            explicitQuickResume: Boolean = false,
        ) {
            val gameActivity =
                if (useLeanback) {
                    TVGameActivity::class.java
                } else {
                    GameActivity::class.java
                }
            activity.startActivityForResult(
                Intent(activity, gameActivity).apply {
                    putExtra(EXTRA_GAME, game)
                    putExtra(EXTRA_LOAD_SAVE, loadSave)
                    putExtra(EXTRA_EXPLICIT_QUICK_RESUME, explicitQuickResume)
                    putExtra(EXTRA_LEANBACK, useLeanback)
                    putExtra(EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                },
                REQUEST_PLAY_GAME,
            )
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
