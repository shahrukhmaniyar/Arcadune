package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.view.KeyEvent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_ANALOG_LEFT
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_ANALOG_RIGHT
import com.swordfish.libretrodroid.GLRetroView.Companion.MOTION_SOURCE_DPAD
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTouchControls(
    private val settingsManager: SettingsManager,
    private val touchControllerSettingsManager: TouchControllerSettingsManager,
    private val retroGameView: GameViewModelRetroGameView,
    private val inputs: GameViewModelInput,
    private val tilt: GameViewModelTilt,
    private val sideEffects: GameViewModelSideEffects,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val touchControlId = MutableStateFlow(TouchControllerID.GB)
    private val screenOrientation = MutableStateFlow(TouchControllerSettingsManager.Orientation.PORTRAIT)
    private val menuPressed = MutableStateFlow(false)
    private val showEditControls = MutableStateFlow(false)
    private val hapticFeedbackMode = MutableStateFlow(HapticFeedbackMode.NONE)

    private var loadingMenuJob: Job? = null

    override fun onCreate(owner: LifecycleOwner) {
        owner.launchOnState(Lifecycle.State.CREATED) {
            getTouchControllerConfig().safeCollect {
                touchControlId.value = it.touchControllerID
            }
        }
        owner.launchOnState(Lifecycle.State.CREATED) {
            withContext(Dispatchers.IO) {
                hapticFeedbackMode.value = HapticFeedbackMode.parse(settingsManager.hapticFeedbackMode())
            }
        }
    }

    fun getTouchControlsSettings(
        density: Density,
        insets: WindowInsets,
    ): Flow<TouchControllerSettingsManager.Settings?> {
        return combine(
            touchControlId,
            screenOrientation,
        ) { touchControlId, orientation -> touchControlId to orientation }
            .flatMapLatest { (touchControlId, orientation) ->
                touchControllerSettingsManager.observeSettings(touchControlId, orientation, density, insets)
            }
    }

    fun getTouchHapticFeedbackMode(): Flow<HapticFeedbackMode> {
        return hapticFeedbackMode
    }

    fun updateTouchControllerSettings(touchControllerSettings: TouchControllerSettingsManager.Settings) {
        scope.launch {
            touchControllerSettingsManager.storeSettings(
                touchControlId.value,
                screenOrientation.value,
                touchControllerSettings,
            )
        }
    }

    fun resetTouchControls() {
        scope.launch {
            touchControllerSettingsManager.resetSettings(
                touchControlId.value,
                screenOrientation.value,
            )
        }
    }

    fun updateScreenOrientation(orientation: TouchControllerSettingsManager.Orientation) {
        screenOrientation.value = orientation
    }

    fun isTouchControllerVisible(): Flow<Boolean> {
        return inputs.getEnabledInputDevices()
            .map { it.isEmpty() }
    }

    fun getTouchControllerConfig(): Flow<ControllerConfig> {
        return inputs.getControllerConfigState()
            .map { it[0] }
            .filterNotNull()
            .distinctUntilChanged()
    }

    fun handleVirtualInputEvent(events: List<InputEvent>) {
        val menuEvent = events.firstOrNull { it is InputEvent.Button && it.id == KeyEvent.KEYCODE_BUTTON_MODE }
        if (menuEvent != null) {
            onMenuPressed((menuEvent as InputEvent.Button).pressed)
        }

        events.forEach { event ->
            when (event) {
                is InputEvent.Button -> {
                    handleVirtualInputButton(event)
                }

                is InputEvent.DiscreteDirection -> {
                    handleVirtualInputDirection(event.id, event.direction.x, -event.direction.y)
                }

                is InputEvent.ContinuousDirection -> {
                    handleVirtualInputDirection(event.id, event.direction.x, -event.direction.y)
                }
            }
        }
    }

    /** Like Delta, a tap on the menu button opens the menu: it opens when the finger lifts. */
    private fun onMenuPressed(pressed: Boolean) {
        val wasPressed = menuPressed.value
        menuPressed.value = pressed

        if (!pressed && wasPressed) {
            loadingMenuJob?.cancel()
            loadingMenuJob = scope.launch { sideEffects.showMenu(tilt, inputs) }
        }
    }

    fun isMenuPressed(): Flow<Boolean> {
        return menuPressed
    }

    fun isEditControlsShown(): Flow<Boolean> {
        return showEditControls
    }

    fun showEditControls(show: Boolean) {
        showEditControls.value = show
    }

    // The emulator reads the controls once a frame: a tap whose press and release both land between
    // two reads never reaches the game. Releases that come sooner than MIN_PRESS_MS after the press
    // wait until then, so every tap counts (a new press of the same key cancels the waiting release).
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pressedAt = mutableMapOf<Int, Long>()
    private val pendingReleases = mutableMapOf<Int, Runnable>()

    private fun handleVirtualInputButton(event: InputEvent.Button) {
        val view = retroGameView.retroGameView ?: return
        val key = event.id
        pendingReleases.remove(key)?.let { mainHandler.removeCallbacks(it) }
        if (event.pressed) {
            pressedAt[key] = android.os.SystemClock.uptimeMillis()
            view.sendKeyEvent(KeyEvent.ACTION_DOWN, key)
            return
        }
        val held = android.os.SystemClock.uptimeMillis() - (pressedAt[key] ?: 0L)
        if (held >= MIN_PRESS_MS) {
            view.sendKeyEvent(KeyEvent.ACTION_UP, key)
        } else {
            val release = Runnable {
                pendingReleases.remove(key)
                retroGameView.retroGameView?.sendKeyEvent(KeyEvent.ACTION_UP, key)
            }
            pendingReleases[key] = release
            mainHandler.postDelayed(release, MIN_PRESS_MS - held)
        }
    }

    // The same for the d-pad: a direction let go of too soon is held until it has been seen.
    private var dpadSince = 0L
    private var dpadNonZero = false
    private var pendingDpadRelease: Runnable? = null

    private fun sendDpad(
        xAxis: Float,
        yAxis: Float,
    ) {
        pendingDpadRelease?.let { mainHandler.removeCallbacks(it) }
        pendingDpadRelease = null
        val now = android.os.SystemClock.uptimeMillis()
        val centred = xAxis == 0f && yAxis == 0f
        if (centred && dpadNonZero && now - dpadSince < MIN_PRESS_MS) {
            val release = Runnable {
                pendingDpadRelease = null
                dpadNonZero = false
                retroGameView.retroGameView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f)
            }
            pendingDpadRelease = release
            mainHandler.postDelayed(release, MIN_PRESS_MS - (now - dpadSince))
            return
        }
        if (!centred && !dpadNonZero) dpadSince = now
        dpadNonZero = !centred
        retroGameView.retroGameView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, xAxis, yAxis)
    }

    private fun handleVirtualInputDirection(
        id: Int,
        xAxis: Float,
        yAxis: Float,
    ) {
        when (id) {
            ComposeTouchLayouts.MOTION_SOURCE_DPAD -> sendDpad(xAxis, yAxis)

            ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_LEFT,
                    xAxis,
                    yAxis,
                )
            }

            ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_RIGHT,
                    xAxis,
                    yAxis,
                )
            }

            ComposeTouchLayouts.MOTION_SOURCE_DPAD_AND_LEFT_STICK -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_LEFT,
                    xAxis,
                    yAxis,
                )
                retroGameView.retroGameView?.sendMotionEvent(MOTION_SOURCE_DPAD, xAxis, yAxis)
            }

            ComposeTouchLayouts.MOTION_SOURCE_RIGHT_DPAD -> {
                retroGameView.retroGameView?.sendMotionEvent(
                    MOTION_SOURCE_ANALOG_RIGHT,
                    xAxis,
                    yAxis,
                )
            }
        }
    }

    companion object {
        const val MENU_LOADING_ANIMATION_MILLIS = 500

        /** Shortest press the game sees: 3 frames at 60 fps. */
        private const val MIN_PRESS_MS = 50L
    }
}
