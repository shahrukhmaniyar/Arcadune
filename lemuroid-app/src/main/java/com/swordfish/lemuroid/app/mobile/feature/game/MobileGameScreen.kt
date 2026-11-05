package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect
import com.swordfish.touchinput.skin.hasTwoScreens
import com.swordfish.touchinput.skin.trackFingers
import com.swordfish.touchinput.skin.SkinInput
import com.swordfish.libretrodroid.LibretroDroid
import android.view.View
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.skin.SkinPalettes
import com.swordfish.touchinput.skin.ScreenSpecs
import com.swordfish.touchinput.skin.placeShell
import com.swordfish.touchinput.skin.ConsoleShell
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.layouts.shared.LocalRewindAvailable
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.radial.ui.LemuroidButtonPressFeedback
import com.swordfish.touchinput.skin.ArcaduneSkin
import com.swordfish.touchinput.skin.ArcaduneSkinView
import com.swordfish.touchinput.skin.ArcaduneSkins
import com.swordfish.touchinput.skin.SkinVariant
import com.swordfish.touchinput.skin.SkinArt
import com.swordfish.touchinput.skin.SkinArtLoader
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.swordfish.touchinput.skin.SkinCustomization
import gg.padkit.PadKit
import gg.padkit.PadKitScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.graphics.Color
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState

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

        val controllerConfigState = viewModel.getTouchControllerConfig().collectAsState(null)
        val touchControlsVisibleState = viewModel.isTouchControllerVisible().collectAsState(false)
        val touchControllerSettingsState =
            viewModel
                .getTouchControlsSettings(LocalDensity.current, WindowInsets.displayCutout)
                .collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val tiltConfiguration = viewModel.getTiltConfiguration().collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates = viewModel.getSimulatedTiltEvents().collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val hapticFeedbackMode =
            viewModel
                .getTouchHapticFeedbackMode()
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
                    onTouchScreen = { position -> LibretroDroid.onTouchEvent(position?.x ?: -10f, position?.y ?: 10f) },
                )
            }
        skinInput.hapticFeedback = padHapticFeedback
        val skinTakesScreenTouches = skin != null && controllerId?.hasTwoScreens() == true
        // One saved set of changes per controller, orientation and layout version, shared with Settings.
        val skinKey =
            skin?.let {
                val layout = if (isLandscape) it.landscape else it.portrait
                SkinCustomizationStore.keyOf(controllerId!!.name, isLandscape, layout)
            }
                ?: "${controllerId?.name}_${if (isLandscape) "landscape" else "portrait"}_none"
        val skinCustomization = remember(skinKey) { mutableStateOf(skinStore.load(skinKey)) }
        // The Console skin's designed pictures for this orientation (see SkinArt), read off the main thread.
        val skinArt by
            produceState(
                initialValue =
                    controllerName
                        ?.takeIf { skinVariant.value == SkinVariant.CONSOLE }
                        ?.let { SkinArtLoader.cached(it, isLandscape) },
                controllerName,
                skinVariant.value,
                isLandscape,
            ) {
                value =
                    if (controllerName != null && skinVariant.value == SkinVariant.CONSOLE) {
                        withContext(Dispatchers.IO) { SkinArtLoader.load(screenContext, controllerName, isLandscape) }
                    } else {
                        null
                    }
            }
        val screenValues = viewModel.getScreenLayout().collectAsState(emptyMap())
        val rewindAvailable = viewModel.isRewindAvailable().collectAsState(false)
        val editingControls = viewModel.isEditControlShown().collectAsState(false)
        // Where the upright controller panel is, so the editor shows it at the same place.
        val skinPanel = remember { mutableStateOf<Rect?>(null) }

        PadKit(
            modifier = Modifier.fillMaxSize().then(if (skin != null) Modifier.trackFingers(skinInput) else Modifier),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
            val viewportPosition = remember { mutableStateOf<Rect?>(null) }

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle)
                },
                // With a DS / 3DS skin the tracker feeds the touch screen (every finger, not only the
                // first, and only fingers that start in a screen window): the view must not do it too.
                update = { view -> view.setOnTouchListener(if (skinTakesScreenTouches) View.OnTouchListener { _, _ -> true } else null) },
            )

            val fullPos = fullScreenPosition.value
            val viewPos = viewportPosition.value

            LaunchedEffect(fullPos, viewPos) {
                val gameView = viewModel.retroGameView.retroGameViewFlow()
                if (fullPos == null || viewPos == null) return@LaunchedEffect
                val viewport =
                    RectF(
                        (viewPos.left - fullPos.left) / fullPos.width,
                        (viewPos.top - fullPos.top) / fullPos.height,
                        (viewPos.right - fullPos.left) / fullPos.width,
                        (viewPos.bottom - fullPos.top) / fullPos.height,
                    )
                gameView.viewport = viewport
            }

            if (skin != null) {
                val controlsVisible =
                    touchControllerSettings != null && touchControlsVisibleState.value
                SkinnedGameLayout(
                    isLandscape = isLandscape,
                    skin = skin,
                    controllerId = controllerId!!,
                    screenValues = screenValues.value,
                    controlsVisible = controlsVisible,
                    rewindAvailable = rewindAvailable.value,
                    customization = skinCustomization.value,
                    onViewportPositioned = { viewportPosition.value = it },
                    onPanelPositioned = { skinPanel.value = it },
                    input = skinInput,
                    gameView = fullPos,
                    takesScreenTouches = skinTakesScreenTouches,
                    art = skinArt,
                )
            } else ConstraintLayout(
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
                        touchControlsVisibleState.value

                val rewindAvailable = viewModel.isRewindAvailable().collectAsState(false)

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

                        GameScreenRunningCentralMenu(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_GAME_CONTAINER),
                            controllerConfig = currentControllerConfig,
                            touchControllerSettings = touchControllerSettings,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }

        if (skin != null && editingControls.value) {
            SkinEditorOverlay(
                isLandscape = isLandscape,
                skin = skin,
                rewindAvailable = rewindAvailable.value,
                // Kept in memory while dragging (saving on every move made dragging stutter), saved on Done.
                customizationState = skinCustomization,
                panel = skinPanel.value,
                art = skinArt,
                variant = skinVariant.value,
                onVariantChange = { variant ->
                    skinVariant.value = variant
                    controllerName?.let { skinStore.setVariant(it, variant) }
                },
                // Back to the console's own layout, size, opacity and skin, saved at once.
                onReset = {
                    skinCustomization.value = SkinCustomization()
                    skinStore.save(skinKey, SkinCustomization())
                    skinVariant.value = SkinVariant.CONSOLE
                    controllerName?.let { skinStore.setVariant(it, SkinVariant.CONSOLE) }
                },
                onDone = {
                    skinStore.save(skinKey, skinCustomization.value)
                    viewModel.showEditControls(false)
                },
            )
        }

        val isLoading =
            viewModel.loadingState
                .collectAsState(true)
                .value

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
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
    rewindAvailable: Boolean,
    customization: SkinCustomization,
    onViewportPositioned: (Rect) -> Unit,
    onPanelPositioned: (Rect) -> Unit,
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

        val spec = if (shell) ScreenSpecs.forController(controllerId, screenValues, isLandscape) else null
        val placement = placeShell(skin, isLandscape, shell, spec, width, height, Rect(left, top, right, height), px(1.dp))
        val windows = placement.windows
        val picture = placement.picture
        val panel = placement.panel
        LaunchedEffect(panel, origin) { onPanelPositioned(panel.translate(origin)) }

        LaunchedEffect(picture, origin) { onViewportPositioned(picture.translate(origin)) }
        // The DS / 3DS touch screen: fingers that start in a screen window touch the game there.
        SideEffect {
            input.touchWindows = if (takesScreenTouches) windows.map { it.translate(origin) } else emptyList()
            input.gameViewSize = gameView ?: Rect.Zero
        }

        if (shell) {
            ConsoleShell(
                modifier = Modifier.fillMaxSize(),
                palette = layout.palette ?: SkinPalettes.MONO,
                screens = windows,
                bodyTop = placement.bodyTop,
                screenPanel = placement.screenPanel,
            )
        }

        if (controlsVisible && isLandscape) {
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
                input = input,
                shell = shell,
                art = art,
            )
        } else if (controlsVisible) {
            with(density) {
                ArcaduneSkinView(
                    modifier =
                        Modifier
                            .absoluteOffset { IntOffset(panel.left.toInt(), panel.top.toInt()) }
                            .size(panel.width.toDp(), panel.height.toDp()),
                    layout = layout,
                    pinToEdges = false,
                    customization = customization,
                    rewindAvailable = rewindAvailable,
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
private fun GameScreenRunningCentralMenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    controllerConfig: ControllerConfig,
) {
    val menuPressed = viewModel.isMenuPressed().collectAsState(false)
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        // The menu opens on a tap now, so there is no "keep holding" ring over the game any more.
        MenuEditTouchControls(viewModel, controllerConfig, touchControllerSettings)
    }
}

@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
) {
    val showEditControls = viewModel.isEditControlShown().collectAsState(false)
    if (!showEditControls.value) return

    Dialog(onDismissRequest = { viewModel.showEditControls(false) }) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MenuEditTouchControlRow(Icons.Default.OpenInFull, "Scale", 0f) {
                    Slider(
                        value = touchControllerSettings.scale,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(scale = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(Icons.Default.Height, "Horizontal Margin", 90f) {
                    Slider(
                        value = touchControllerSettings.marginX,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginX = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(Icons.Default.Height, "Vertical Margin", 0f) {
                    Slider(
                        value = touchControllerSettings.marginY,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginY = it),
                            )
                        },
                    )
                }
                if (controllerConfig.allowTouchRotation) {
                    MenuEditTouchControlRow(Icons.Default.RotateLeft, "Rotate", 0f) {
                        Slider(
                            value = touchControllerSettings.rotation,
                            onValueChange = {
                                viewModel.updateTouchControllerSettings(
                                    touchControllerSettings.copy(rotation = it),
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { viewModel.resetTouchControls() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_reset))
                    }
                    TextButton(
                        onClick = { viewModel.showEditControls(false) },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEditTouchControlRow(
    icon: ImageVector,
    label: String,
    rotation: Float,
    slider: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            modifier = Modifier.rotate(rotation),
            imageVector = icon,
            contentDescription = label,
        )
        slider()
    }
}
