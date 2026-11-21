package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.layouts.shared.LocalRewindAvailable
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.skin.ArcaduneSkin
import com.swordfish.touchinput.skin.ArcaduneSkinView
import com.swordfish.touchinput.skin.ArcaduneSkins
import com.swordfish.touchinput.skin.ConsoleShell
import com.swordfish.touchinput.skin.ScreenSpecs
import com.swordfish.touchinput.skin.ShellPlacement
import com.swordfish.touchinput.skin.SkinArt
import com.swordfish.touchinput.skin.SkinArtLoader
import com.swordfish.touchinput.skin.SkinControl
import com.swordfish.touchinput.skin.SkinCustomization
import com.swordfish.touchinput.skin.SkinInput
import com.swordfish.touchinput.skin.SkinPalettes
import com.swordfish.touchinput.skin.SkinVariant
import com.swordfish.touchinput.skin.hasTwoScreens
import com.swordfish.touchinput.skin.placeShell
import com.swordfish.touchinput.skin.trackFingers
import gg.padkit.PadKit
import gg.padkit.PadKitScope
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight
        LaunchedEffect(isLandscape) { viewModel.onOrientationChanged(isLandscape) }

        LaunchedEffect(isLandscape) {
            val orientation =
                if (isLandscape) {
                    TouchControllerSettingsManager.Orientation.LANDSCAPE
                } else {
                    TouchControllerSettingsManager.Orientation.PORTRAIT
                }
            viewModel.onScreenOrientationChanged(orientation)
        }

        val density = LocalDensity.current
        val cutoutInsets = WindowInsets.displayCutout
        val controllerConfigState =
            remember(viewModel) { viewModel.getTouchControllerConfig() }.collectAsState(null)
        val touchControlsVisibleState =
            remember(viewModel) { viewModel.isTouchControllerVisible() }.collectAsState(false)
        val touchControllerSettingsState =
            remember(viewModel, density, cutoutInsets) {
                viewModel.getTouchControlsSettings(density, cutoutInsets)
            }.collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val tiltConfiguration =
            remember(viewModel) { viewModel.getTiltConfiguration() }.collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates =
            remember(viewModel) { viewModel.getSimulatedTiltEvents() }.collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val hapticFeedbackMode =
            remember(viewModel) { viewModel.getTouchHapticFeedbackMode() }
                .collectAsState(HapticFeedbackMode.NONE)

        val padHapticFeedback =
            when (hapticFeedbackMode.value) {
                HapticFeedbackMode.NONE -> HapticFeedbackType.NONE
                HapticFeedbackMode.PRESS -> HapticFeedbackType.PRESS
                HapticFeedbackMode.PRESS_RELEASE -> HapticFeedbackType.PRESS_RELEASE
            }

        // Consoles with an Arcadune skin use it; the others keep the original round pads.
        val screenContext = LocalContext.current
        val skinStore = remember { SkinCustomizationStore(screenContext) }
        val controllerName = currentControllerConfig?.touchControllerID?.name
        // Which of its two skins (Console or Minimal) this controller uses, as chosen in Settings or here.
        val skinVariant =
            remember(controllerName) { mutableStateOf(controllerName?.let(skinStore::variant) ?: SkinVariant.CONSOLE) }
        val controllerId = currentControllerConfig?.touchControllerID
        // Built once per controller and skin, not at every redraw, so the controls keep the same
        // layout objects for the whole game.
        val skin =
            remember(controllerId, skinVariant.value) {
                controllerId?.let { ArcaduneSkins.forController(it, skinVariant.value) }
            }
        // Every finger on a skin goes through this tracker (buttons, d-pad, sticks, and the DS / 3DS
        // touch screen), see SkinInput.
        val skinInput =
            remember {
                SkinInput(
                    vibrate = SkinInput.vibrator(screenContext),
                    onEvents = { viewModel.handleVirtualInputEvent(it) },
                    onTouchScreen = {
                            position ->
                        LibretroDroid.onTouchEvent(position?.x ?: -10f, position?.y ?: -10f)
                    },
                )
            }
        skinInput.hapticFeedback = padHapticFeedback
        val controlsVisible = touchControllerSettings != null && touchControlsVisibleState.value
        val editingControls = remember(viewModel) { viewModel.isEditControlShown() }.collectAsState(false)
        val renderSkin = controlsVisible || editingControls.value
        val skinTakesScreenTouches = skin != null && controlsVisible && controllerId?.hasTwoScreens() == true
        // One saved set of changes per controller, orientation and layout version, shared with Settings.
        val skinKey =
            skin?.let {
                val layout = if (isLandscape) it.landscape else it.portrait
                SkinCustomizationStore.keyOf(controllerId!!.name, isLandscape, layout)
            }
                ?: "${controllerId?.name}_${if (isLandscape) "landscape" else "portrait"}_none"
        val skinDefault = skin?.let { (if (isLandscape) it.landscape else it.portrait).defaultCustomization } ?: SkinCustomization()
        val skinCustomization = remember(skinKey) { mutableStateOf(skinStore.load(skinKey, skinDefault)) }
        DisposableEffect(skinKey) {
            onDispose {
                if (editingControls.value) {
                    skinStore.save(skinKey, skinCustomization.value)
                }
            }
        }
        // The Console skin's designed pictures for this orientation (see SkinArt), read off the main thread.
        var skinArt by
            remember(controllerName, skinVariant.value, isLandscape) {
                mutableStateOf(
                    controllerName
                        ?.takeIf { skinVariant.value == SkinVariant.CONSOLE }
                        ?.let { SkinArtLoader.cached(it, isLandscape) },
                )
            }
        LaunchedEffect(controllerName, skinVariant.value, isLandscape) {
            skinArt =
                if (controllerName != null && skinVariant.value == SkinVariant.CONSOLE) {
                    withContext(Dispatchers.IO) { SkinArtLoader.load(screenContext, controllerName, isLandscape) }
                } else {
                    null
                }
        }
        val screenValues = remember(viewModel) { viewModel.getScreenLayout() }.collectAsState(emptyMap())
        val rewindAvailable = remember(viewModel) { viewModel.isRewindAvailable() }.collectAsState(false)
        val fastForwardSupported = remember(viewModel) { viewModel.isFastForwardSupported() }
        val currentFrameSpeed by viewModel.frameSpeed.collectAsState()
        val fastForwardDotsTotal = viewModel.fastForwardSteps.size
        val fastForwardDotsActive = viewModel.fastForwardDotsActive(currentFrameSpeed)
        // Where the upright controller panel and game screen windows are, so the editor matches 1:1.
        val skinPanel = remember { mutableStateOf<Rect?>(null) }
        val skinWindows = remember { mutableStateOf<List<Rect>>(emptyList()) }
        val skinDefaultWindows = remember { mutableStateOf<List<Rect>>(emptyList()) }
        val skinPlacement = remember { mutableStateOf<ShellPlacement?>(null) }
        val screenRegionsState = remember { mutableStateOf(FloatArray(0)) }

        val activeSkinLayout = skin?.let { if (isLandscape) it.landscape else it.portrait }
        val isMenuHiddenOnSkin =
            activeSkinLayout != null &&
                controlsVisible &&
                activeSkinLayout.controls.withIndex().none { (idx, ctrl) ->
                    ctrl is SkinControl.Menu && !skinCustomization.value.isHidden(idx, ctrl)
                }
        LaunchedEffect(isMenuHiddenOnSkin) {
            viewModel.setTouchMenuHidden(isMenuHiddenOnSkin)
        }

        PadKit(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (skin != null && controlsVisible) Modifier.trackFingers(skinInput) else Modifier),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
            val viewportPosition = remember { mutableStateOf<Rect?>(null) }

            // The skin is on screen from the first frame; the game view itself only exists once the
            // game has loaded. Until then an empty stand-in gives the skin the same screen size.
            val gameLoaded =
                remember(viewModel) { viewModel.getGameState() }
                    .collectAsState(GameViewModelRetroGameView.GameState.Uninitialized)
                    .value
                    .let {
                        it is GameViewModelRetroGameView.GameState.Loaded ||
                            it is GameViewModelRetroGameView.GameState.Ready
                    }
            if (!gameLoaded) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                )
            }

            if (gameLoaded) AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle)
                },
                // With a DS / 3DS skin the tracker feeds the touch screen (every finger, not only the
                // first, and only fingers that start in a screen window): the view must not do it too.
                update = {
                        view ->
                    view.setOnTouchListener(
                        if (skinTakesScreenTouches) {
                            View.OnTouchListener {
                                    _,
                                    _,
                                ->
                                true
                            }
                        } else {
                            null
                        },
                    )
                },
            )

            val fullPos = fullScreenPosition.value
            val viewPos = viewportPosition.value
            val targetViewport =
                if (fullPos != null && viewPos != null && fullPos.width > 0f && fullPos.height > 0f) {
                    RectF(
                        (viewPos.left - fullPos.left) / fullPos.width,
                        (viewPos.top - fullPos.top) / fullPos.height,
                        (viewPos.right - fullPos.left) / fullPos.width,
                        (viewPos.bottom - fullPos.top) / fullPos.height,
                    )
                } else {
                    null
                }

            val targetRegions = if (skin != null) screenRegionsState.value else FloatArray(0)

            SideEffect {
                val gameView = viewModel.retroGameView.retroGameView
                if (gameView != null) {
                    if (targetViewport != null && gameView.viewport != targetViewport) {
                        gameView.viewport = targetViewport
                    }
                    if (!gameView.screenRegions.contentEquals(targetRegions)) {
                        gameView.screenRegions = targetRegions
                    }
                }
            }

            LaunchedEffect(targetViewport, targetRegions) {
                val gameView = viewModel.retroGameView.retroGameViewFlow()
                if (targetViewport != null && gameView.viewport != targetViewport) {
                    gameView.viewport = targetViewport
                }
                if (!gameView.screenRegions.contentEquals(targetRegions)) {
                    gameView.screenRegions = targetRegions
                }
            }

            if (skin != null) {
                SkinnedGameLayout(
                    isLandscape = isLandscape,
                    skin = skin,
                    controllerId = controllerId!!,
                    screenValues = screenValues.value,
                    controlsVisible = renderSkin,
                    editingControls = editingControls.value,
                    rewindAvailable = rewindAvailable.value,
                    fastForwardSupported = fastForwardSupported,
                    fastForwardDotsTotal = fastForwardDotsTotal,
                    fastForwardDotsActive = fastForwardDotsActive,
                    customization = skinCustomization.value,
                    onViewportPositioned = { viewportPosition.value = it },
                    onPanelPositioned = { skinPanel.value = it },
                    onWindowsPositioned = { skinWindows.value = it },
                    onDefaultWindowsPositioned = { skinDefaultWindows.value = it },
                    onPlacementChanged = { skinPlacement.value = it },
                    onRegionsPositioned = { nextRegions ->
                        if (!screenRegionsState.value.contentEquals(nextRegions)) {
                            screenRegionsState.value = nextRegions
                        }
                    },
                    input = skinInput,
                    gameView = fullPos,
                    takesScreenTouches = skinTakesScreenTouches,
                    art = skinArt,
                )
            } else {
                ConstraintLayout(
                    modifier = Modifier.fillMaxSize(),
                    constraintSet =
                        GameScreenLayout.buildConstraintSet(
                            isLandscape,
                            currentControllerConfig?.allowTouchOverlay ?: true,
                        ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                                .onGloballyPositioned { viewportPosition.value = it.boundsInRoot() },
                    )

                    val isVisible =
                        touchControllerSettings != null &&
                            currentControllerConfig != null &&
                            (touchControlsVisibleState.value || editingControls.value)

                    if (isVisible) {
                        CompositionLocalProvider(
                            LocalLemuroidPadTheme provides LemuroidPadTheme(),
                            LocalRewindAvailable provides rewindAvailable.value,
                        ) {
                            if (!isLandscape) {
                                PadContainer(
                                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                                )
                            } else if (!currentControllerConfig.allowTouchOverlay) {
                                PadContainer(
                                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                                )
                                PadContainer(
                                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                                )
                            }

                            leftGamePad?.invoke(
                                this,
                                Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD),
                                touchControllerSettings,
                            )
                            rightGamePad?.invoke(
                                this,
                                Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD),
                                touchControllerSettings,
                            )
                        }
                    }
                }
            }
        }

        val showRadialEditor =
            skin == null && editingControls.value && touchControllerSettings != null && currentControllerConfig != null
        if (showRadialEditor && touchControllerSettings != null && currentControllerConfig != null) {
            MenuEditTouchControls(
                viewModel = viewModel,
                controllerConfig = currentControllerConfig,
                touchControllerSettings = touchControllerSettings,
            )
        }

        val availableControllers = remember(viewModel) { viewModel.getAvailableTouchControllerConfigs() }
        if (skin != null && editingControls.value) {
            SkinEditorOverlay(
                isLandscape = isLandscape,
                skin = skin,
                rewindAvailable = rewindAvailable.value,
                fastForwardSupported = fastForwardSupported,
                fastForwardDotsTotal = fastForwardDotsTotal,
                fastForwardDotsActive = fastForwardDotsActive,
                // Kept in memory while dragging (saving on every move made dragging stutter), saved on Done.
                customizationState = skinCustomization,
                panel = skinPanel.value,
                screenWindows = skinWindows.value,
                defaultScreenWindows = skinDefaultWindows.value,
                editableBodyTop = skinPlacement.value?.editableBodyTop,
                defaultBodyTop = skinPlacement.value?.defaultBodyTop,
                minBodyTop = skinPlacement.value?.minBodyTop ?: 0f,
                maxBodyTop = skinPlacement.value?.maxBodyTop ?: 0f,
                art = skinArt,
                variant = skinVariant.value,
                onVariantChange = { variant ->
                    skinVariant.value = variant
                    controllerName?.let { skinStore.setVariant(it, variant) }
                },
                // Back to the console's default button positions, scale, opacity, and visibility.
                onReset = {
                    skinCustomization.value = skinDefault
                    skinStore.save(skinKey, skinDefault)
                },
                onDone = {
                    skinStore.save(skinKey, skinCustomization.value)
                    viewModel.showEditControls(false)
                },
                controllerOptions = availableControllers,
                selectedController = currentControllerConfig,
                onSelectController = { next ->
                    skinStore.save(skinKey, skinCustomization.value)
                    viewModel.selectTouchControllerConfig(next)
                },
            )
        }

        val isLoading =
            viewModel.loadingState
                .collectAsState(false)
                .value

        // No loading ring: saving and loading are quick, and the controls stay in view.
        if (!isLoading && !editingControls.value) {
            RetroAchievementsOverlay()
        }
    }
}

/**
 * The game with an Arcadune skin, like Delta: the console's body fills the screen, the game shows
 * through a window cut in it for each screen, and the controls sit on the body. Upright the
 * windows are above the controller panel; sideways (for skins with a shell) they sit between the
 * two wings of controls. Skins without a sideways shell keep their controls over the game.
 * With the controls hidden (a gamepad in use) the game simply fills the screen.
 */
@Composable
private fun PadKitScope.SkinnedGameLayout(
    isLandscape: Boolean,
    skin: ArcaduneSkin,
    controllerId: TouchControllerID,
    screenValues: Map<String, String>,
    controlsVisible: Boolean,
    editingControls: Boolean,
    rewindAvailable: Boolean,
    fastForwardSupported: Boolean,
    fastForwardDotsTotal: Int,
    fastForwardDotsActive: Int,
    customization: SkinCustomization,
    onViewportPositioned: (Rect) -> Unit,
    onPanelPositioned: (Rect) -> Unit,
    onWindowsPositioned: (List<Rect>) -> Unit,
    onDefaultWindowsPositioned: (List<Rect>) -> Unit,
    onPlacementChanged: (ShellPlacement) -> Unit,
    onRegionsPositioned: (FloatArray) -> Unit,
    input: SkinInput,
    gameView: Rect?,
    takesScreenTouches: Boolean,
    art: SkinArt?,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val layout = if (isLandscape) skin.landscape else skin.portrait
    val shell = controlsVisible && (!isLandscape || layout.screenArea != null)
    var origin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft },
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        fun px(value: Dp) = with(density) { value.toPx() }
        val left = cutout.getLeft(density, layoutDirection).toFloat()
        val top = cutout.getTop(density).toFloat()
        val right = width - cutout.getRight(density, layoutDirection)
        // Upright, `top` already clears punch-hole/notch cameras, so keep horizontal bounds full-width
        // and symmetric over the centered controller panel even on corner-cutout phones.
        val safeRect = if (isLandscape) Rect(left, top, right, height) else Rect(0f, top, width, height)

        val effectiveCustomization = if (controlsVisible) customization else SkinCustomization()
        val spec = ScreenSpecs.forController(controllerId, screenValues, isLandscape)
        val placement =
            placeShell(
                skin = skin,
                isLandscape = isLandscape,
                shell = shell,
                spec = if (shell) spec else null,
                width = width,
                height = height,
                safe = safeRect,
                dp = px(1.dp),
                customization = effectiveCustomization,
            )
        val windows = placement.windows
        val picture = placement.picture
        val panel = placement.panel
        val editorWindows = if (controlsVisible) placement.gameWindows(shell, spec) else windows
        val defaultEditorWindows =
            if (controlsVisible) placement.defaultGameWindows(shell, spec) else placement.defaultWindows

        val regions =
            if (shell &&
                spec != null &&
                spec.srcScreens.size > 1 &&
                windows.size == spec.srcScreens.size &&
                gameView != null &&
                gameView.width > 0f &&
                gameView.height > 0f &&
                spec.srcWidth > 0f &&
                spec.srcHeight > 0f
            ) {
                FloatArray(spec.srcScreens.size * 8).also { arr ->
                    spec.srcScreens.forEachIndexed { idx, src ->
                        val dst = windows[idx].translate(origin)
                        val base = idx * 8
                        arr[base] = src.x / spec.srcWidth
                        arr[base + 1] = src.y / spec.srcHeight
                        arr[base + 2] = src.width / spec.srcWidth
                        arr[base + 3] = src.height / spec.srcHeight
                        arr[base + 4] = (dst.left - gameView.left) / gameView.width
                        arr[base + 5] = (dst.top - gameView.top) / gameView.height
                        arr[base + 6] = dst.width / gameView.width
                        arr[base + 7] = dst.height / gameView.height
                    }
                }
            } else {
                FloatArray(0)
            }

        val touchIndices =
            if (takesScreenTouches) {
                ScreenSpecs.touchWindowIndices(controllerId, screenValues, isLandscape)
            } else {
                emptySet()
            }

        // Update viewport, panel, screen windows, multi-region quads, and DS/3DS touch windows synchronously each frame.
        SideEffect {
            onPanelPositioned(panel)
            onViewportPositioned(picture.translate(origin))
            onWindowsPositioned(editorWindows)
            onDefaultWindowsPositioned(defaultEditorWindows)
            onPlacementChanged(placement)
            onRegionsPositioned(regions)
            input.touchWindows =
                if (takesScreenTouches) {
                    windows.mapIndexedNotNull { idx, win ->
                        if (idx in touchIndices) win.translate(origin) else null
                    }
                } else {
                    emptyList()
                }
            input.gameViewSize = gameView ?: Rect.Zero
        }

        if (shell) {
            ConsoleShell(
                modifier = Modifier.fillMaxSize(),
                palette = layout.palette ?: SkinPalettes.MONO,
                screens = windows,
                bodyTop = placement.bodyTop,
                screenPanel = placement.screenPanel,
                layout = layout.takeIf { !isLandscape },
                panel = panel.takeIf { !isLandscape },
                art = art.takeIf { !isLandscape },
                cutoutScreens = true,
            )
        }

        if (controlsVisible && !editingControls && isLandscape) {
            ArcaduneSkinView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(4.dp),
                layout = layout,
                pinToEdges = true,
                customization = customization,
                rewindAvailable = rewindAvailable,
                fastForwardSupported = fastForwardSupported,
                fastForwardDotsTotal = fastForwardDotsTotal,
                fastForwardDotsActive = fastForwardDotsActive,
                input = input,
                shell = shell,
                art = art,
            )
        } else if (controlsVisible && !editingControls) {
            val above = panel.top.coerceAtLeast(0f)
            with(density) {
                ArcaduneSkinView(
                    modifier =
                        Modifier
                            .absoluteOffset { IntOffset(panel.left.toInt(), (panel.top - above).toInt()) }
                            .size(panel.width.toDp(), (panel.height + above).toDp()),
                    layout = layout,
                    pinToEdges = false,
                    customization = customization,
                    rewindAvailable = rewindAvailable,
                    fastForwardSupported = fastForwardSupported,
                    fastForwardDotsTotal = fastForwardDotsTotal,
                    fastForwardDotsActive = fastForwardDotsActive,
                    topExtra = above.toDp(),
                    input = input,
                    shell = true,
                    art = art,
                )
            }
        }
    }
}

@Composable
private fun PadContainer(modifier: Modifier = Modifier) {
    val theme = LocalLemuroidPadTheme.current
    GlassSurface(
        modifier = modifier,
        cornerRadius = theme.level0CornerRadius,
        fillColor = theme.level0Fill,
        shadowColor = theme.level0Shadow,
        shadowWidth = theme.level0ShadowWidth,
    )
}

@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
) {
    BackHandler { viewModel.showEditControls(false) }
    val panelShape = RoundedCornerShape(26.dp)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.showEditControls(false) },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .border(1.dp, ArcaduneColors.GlassEdge, panelShape),
            shape = panelShape,
            color = ArcaduneColors.Glass,
            contentColor = ArcaduneColors.OnSurface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.touch_customize_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = ArcaduneColors.OnSurfaceDim,
                )
                MenuEditTouchControlRow(
                    icon = Icons.Default.OpenInFull,
                    label = stringResource(R.string.touch_customize_scale),
                    valueText = "${(touchControllerSettings.scale * 100).roundToInt()}%",
                    rotation = 0f,
                ) {
                    ArcadunePrecisionScrubber(
                        value = touchControllerSettings.scale,
                        valueRange = 0f..1f,
                        defaultValue = 0.5f,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(scale = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(
                    icon = Icons.Default.Height,
                    label = stringResource(R.string.touch_customize_horizontal_margin),
                    valueText = "${(touchControllerSettings.marginX * 100).roundToInt()}%",
                    rotation = 90f,
                ) {
                    ArcadunePrecisionScrubber(
                        value = touchControllerSettings.marginX,
                        valueRange = 0f..1f,
                        defaultValue = 0f,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginX = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(
                    icon = Icons.Default.Height,
                    label = stringResource(R.string.touch_customize_vertical_margin),
                    valueText = "${(touchControllerSettings.marginY * 100).roundToInt()}%",
                    rotation = 0f,
                ) {
                    ArcadunePrecisionScrubber(
                        value = touchControllerSettings.marginY,
                        valueRange = 0f..1f,
                        defaultValue = 0f,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginY = it),
                            )
                        },
                    )
                }
                if (controllerConfig.allowTouchRotation) {
                    MenuEditTouchControlRow(
                        icon = Icons.Default.RotateLeft,
                        label = stringResource(R.string.touch_customize_rotate),
                        valueText = "${(touchControllerSettings.rotation * 100).roundToInt()}%",
                        rotation = 0f,
                    ) {
                        ArcadunePrecisionScrubber(
                            value = touchControllerSettings.rotation,
                            valueRange = 0f..1f,
                            defaultValue = 0f,
                            onValueChange = {
                                viewModel.updateTouchControllerSettings(
                                    touchControllerSettings.copy(rotation = it),
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    ArcadunePillButton(
                        label = stringResource(R.string.touch_customize_button_reset),
                        onClick = { viewModel.resetTouchControls() },
                    )
                    ArcadunePillButton(
                        label = stringResource(R.string.touch_customize_button_done),
                        highlighted = true,
                        onClick = { viewModel.showEditControls(false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuEditTouchControlRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    rotation: Float,
    slider: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                modifier = Modifier.size(18.dp).rotate(rotation),
                imageVector = icon,
                contentDescription = null,
                tint = ArcaduneColors.Purple,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = ArcaduneColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = ArcaduneColors.OnSurfaceDim,
            )
        }
        slider()
    }
}
