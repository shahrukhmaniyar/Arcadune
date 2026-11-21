package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.touchinput.skin.ArcaduneSkin
import com.swordfish.touchinput.skin.ArcaduneSkinEditor
import com.swordfish.touchinput.skin.SkinArt
import com.swordfish.touchinput.skin.SkinControl
import com.swordfish.touchinput.skin.SkinCustomization
import com.swordfish.touchinput.skin.SkinLayout
import com.swordfish.touchinput.skin.SkinVariant
import com.swordfish.touchinput.skin.clampControlOffset
import com.swordfish.touchinput.skin.displayName
import com.swordfish.touchinput.skin.signature
import kotlin.math.abs
import kotlin.math.roundToInt

/** Saves the player's skin changes, one set per controller and orientation. */
class SkinCustomizationStore(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    /** The saved changes for [key]; [default] (how the layout starts) until the player has made some. */
    fun load(
        key: String,
        default: SkinCustomization = SkinCustomization(),
    ): SkinCustomization {
        val saved = SkinCustomization.decode(preferences.getString(prefKey(key), null))
        return if (saved == SkinCustomization()) default else saved
    }

    fun save(
        key: String,
        customization: SkinCustomization,
    ) = preferences.edit { putString(prefKey(key), customization.encode()) }

    /**
     * Which of its two skins a controller uses ([controller] is its name). Players who had chosen
     * black and white before there were two skins get the Minimal one.
     */
    fun variant(controller: String): SkinVariant =
        preferences.getString(variantKey(controller), null)
            ?.let { name -> SkinVariant.values().firstOrNull { it.name == name } }
            ?: if (preferences.getBoolean(monoKey(controller), false)) SkinVariant.MINIMAL else SkinVariant.CONSOLE

    fun setVariant(
        controller: String,
        variant: SkinVariant,
    ) = preferences.edit {
        putString(variantKey(controller), variant.name)
        remove(monoKey(controller))
    }

    private fun prefKey(key: String) = "arcadune_skin_$key"

    private fun variantKey(controller: String) = "arcadune_skin_variant_$controller"

    private fun monoKey(controller: String) = "arcadune_skin_mono_$controller"

    companion object {
        /**
         * Where the changes to [layout] (the [landscape] or upright layout of [controller]) are
         * kept: one set per controller, orientation and version of the layout, so a redesigned
         * layout never gets moves made for the old one. The game and Settings share it.
         */
        fun keyOf(
            controller: String,
            landscape: Boolean,
            layout: SkinLayout,
        ) = "${controller}_${if (landscape) "landscape" else "portrait"}_${layout.signature()}"
    }
}

private val StudioGlass = Color(0xEE141220)

/**
 * Edits the skin on top of the paused game using the unified [ArcaduneControllerStudio].
 */
@Composable
fun SkinEditorOverlay(
    isLandscape: Boolean,
    skin: ArcaduneSkin,
    rewindAvailable: Boolean,
    customizationState: MutableState<SkinCustomization>,
    /** Where the game shows the upright controller panel (in pixels); the editor shows it there too. */
    panel: Rect?,
    /** Which of its two skins the controller uses (the Console one, or Minimal). */
    variant: SkinVariant,
    onVariantChange: (SkinVariant) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    /** The skin's designed pictures, when it has them, so the editor shows the controls as the game does. */
    art: SkinArt? = null,
    fastForwardSupported: Boolean = true,
    fastForwardDotsTotal: Int = 4,
    fastForwardDotsActive: Int = 0,
    screenWindows: List<Rect> = emptyList(),
    defaultScreenWindows: List<Rect> = emptyList(),
    editableBodyTop: Float? = null,
    defaultBodyTop: Float? = null,
    minBodyTop: Float = 0f,
    maxBodyTop: Float = 0f,
    controllerOptions: List<ControllerConfig> = emptyList(),
    selectedController: ControllerConfig? = null,
    onSelectController: ((ControllerConfig) -> Unit)? = null,
) {
    val layout = if (isLandscape) skin.landscape else skin.portrait
    val hasShell = !isLandscape || layout.screenArea != null
    ArcaduneControllerStudio(
        modifier =
            Modifier
                .fillMaxSize()
                // Blocks stray touches from reaching the paused game underneath without cancelling HUD taps.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        title = null,
        isLandscape = isLandscape,
        onLandscapeChange = null,
        skin = skin,
        rewindAvailable = rewindAvailable,
        fastForwardSupported = fastForwardSupported,
        fastForwardDotsTotal = fastForwardDotsTotal,
        fastForwardDotsActive = fastForwardDotsActive,
        customizationState = customizationState,
        panel = panel,
        screenWindows = screenWindows,
        defaultScreenWindows = defaultScreenWindows,
        editableBodyTop = editableBodyTop,
        defaultBodyTop = defaultBodyTop,
        minBodyTop = minBodyTop,
        maxBodyTop = maxBodyTop,
        shell = hasShell,
        variant = variant,
        onVariantChange = onVariantChange,
        onResetAll = onReset,
        onDone = onDone,
        art = art,
        controllerOptions = controllerOptions,
        selectedController = selectedController,
        onSelectController = onSelectController,
    )
}

/**
 * Minimal, zero-latency 1:1 direct-manipulation controller layout studio shared by in-game
 * Edit Controls and Settings -> Controllers -> Customize layout.
 */
@Composable
fun ArcaduneControllerStudio(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER")
    title: String? = null,
    isLandscape: Boolean,
    onLandscapeChange: ((Boolean) -> Unit)? = null,
    skin: ArcaduneSkin,
    rewindAvailable: Boolean,
    customizationState: MutableState<SkinCustomization>,
    panel: Rect?,
    shell: Boolean,
    variant: SkinVariant,
    onVariantChange: (SkinVariant) -> Unit,
    onResetAll: () -> Unit,
    onDone: () -> Unit,
    art: SkinArt? = null,
    fastForwardSupported: Boolean = true,
    fastForwardDotsTotal: Int = 4,
    fastForwardDotsActive: Int = 0,
    screenWindows: List<Rect> = emptyList(),
    defaultScreenWindows: List<Rect> = emptyList(),
    editableBodyTop: Float? = null,
    defaultBodyTop: Float? = null,
    minBodyTop: Float = 0f,
    maxBodyTop: Float = 0f,
    controllerOptions: List<ControllerConfig> = emptyList(),
    selectedController: ControllerConfig? = null,
    onSelectController: ((ControllerConfig) -> Unit)? = null,
) {
    BackHandler(onBack = onDone)
    val layout = if (isLandscape) skin.landscape else skin.portrait

    var selectedIndex by rememberSaveable(isLandscape) { mutableStateOf<Int?>(null) }
    var selectedScreenIndex by rememberSaveable(isLandscape) { mutableStateOf<Int?>(null) }
    var bgSelected by rememberSaveable(isLandscape) { mutableStateOf(false) }
    var globalOpen by rememberSaveable(isLandscape) { mutableStateOf(false) }
    var canvasBusy by remember { mutableStateOf(false) }
    var scrubberBusy by remember { mutableStateOf(false) }
    var dockedBottomLatched by remember { mutableStateOf(false) }
    var editorSurfaceDp by remember(isLandscape) {
        mutableStateOf(Offset(layout.width, layout.height))
    }
    var extraUnitsAbove by remember(isLandscape) {
        mutableStateOf(0f)
    }

    LaunchedEffect(layout, rewindAvailable, fastForwardSupported, selectedIndex, customizationState.value) {
        val idx = selectedIndex ?: return@LaunchedEffect
        val ctrl = layout.controls.getOrNull(idx)
        val c = customizationState.value
        if (
            ctrl == null ||
            !c.isControlAvailable(ctrl, rewindAvailable, fastForwardSupported) ||
            c.isHidden(idx, ctrl)
        ) {
            selectedIndex = null
        }
    }
    LaunchedEffect(screenWindows.size, selectedScreenIndex) {
        val idx = selectedScreenIndex
        if (idx != null && idx !in screenWindows.indices) {
            selectedScreenIndex = null
        }
    }
    LaunchedEffect(isLandscape, editableBodyTop) {
        if (isLandscape || editableBodyTop == null) {
            bgSelected = false
        }
    }

    val canReset by remember(customizationState) {
        derivedStateOf { customizationState.value.isCustomized }
    }
    val hasGlobalChanges by remember(customizationState, isLandscape) {
        derivedStateOf {
            val c = customizationState.value
            abs(c.size - 1f) > 0.005f ||
                abs(c.opacity - 1f) > 0.005f ||
                c.hidden.isNotEmpty() ||
                c.shown.isNotEmpty() ||
                (!isLandscape && c.hasPortraitBgChange)
        }
    }

    val hudAlpha by animateFloatAsState(
        targetValue = if (canvasBusy) 0.18f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "studioHudAlpha",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenW = maxWidth
        val stageHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val safeTopPx = WindowInsets.displayCutout.getTop(density).toFloat()
        val buttonHeight = if (screenW >= 400.dp) 44.dp else 42.dp
        val approxScale =
            remember(layout, maxWidth, maxHeight) {
                minOf(maxWidth.value / layout.width, maxHeight.value / layout.height)
            }
        val currentCustomization = customizationState.value
        val selectedCtrlOffsetY = selectedIndex?.let { currentCustomization.offsets[it]?.y } ?: 0f

        val topHudClearancePx = safeTopPx + with(density) { 68.dp.toPx() }
        val computedDockBottom =
            remember(
                globalOpen,
                isLandscape,
                selectedIndex,
                selectedScreenIndex,
                bgSelected,
                editableBodyTop,
                screenWindows,
                stageHeightPx,
                topHudClearancePx,
                layout,
                selectedCtrlOffsetY,
            ) {
                if (globalOpen) {
                    false
                } else {
                    val activeScreenWin = selectedScreenIndex?.let { screenWindows.getOrNull(it) }
                    val activeCtrl = selectedIndex?.let { layout.controls.getOrNull(it) }
                    when {
                        // Keep the Portrait `=` BG pill 100% uncovered when BG sits near the top (e.g. DS or raised shells).
                        !isLandscape &&
                            selectedIndex == null &&
                            selectedScreenIndex == null &&
                            editableBodyTop != null &&
                            editableBodyTop < topHudClearancePx -> true
                        bgSelected && !isLandscape && editableBodyTop != null ->
                            editableBodyTop < stageHeightPx * 0.34f
                        activeScreenWin != null ->
                            if (isLandscape) {
                                activeScreenWin.center.y < stageHeightPx * 0.44f
                            } else {
                                activeScreenWin.center.y < stageHeightPx * 0.42f
                            }
                        activeCtrl != null && selectedIndex != null -> {
                            val ctrlCenterY = activeCtrl.frame.centerY + selectedCtrlOffsetY
                            if (isLandscape) {
                                ctrlCenterY < layout.height * 0.36f
                            } else {
                                ctrlCenterY < -layout.height * 0.35f
                            }
                        }
                        else -> false
                    }
                }
            }
        if (!scrubberBusy && !canvasBusy && dockedBottomLatched != computedDockBottom) {
            dockedBottomLatched = computedDockBottom
        }
        val dockHudAtBottom = if (scrubberBusy || canvasBusy) dockedBottomLatched else computedDockBottom

        val hudTopPadding =
            remember(
                dockHudAtBottom,
                isLandscape,
                layout,
                approxScale,
                rewindAvailable,
                fastForwardSupported,
                currentCustomization.hidden,
                currentCustomization.shown,
            ) {
                if (dockHudAtBottom || !isLandscape) {
                    8.dp
                } else {
                    val midLeft = layout.width * 0.25f
                    val midRight = layout.width * 0.75f
                    val topClearUnits =
                        layout.controls
                            .withIndex()
                            .filter { (idx, ctrl) ->
                                currentCustomization.isControlAvailable(ctrl, rewindAvailable, fastForwardSupported) &&
                                    !currentCustomization.isHidden(idx, ctrl)
                            }
                            .map { it.value }
                            .filter {
                                it.frame.y < layout.height * 0.22f &&
                                    it.frame.x + it.frame.width > midLeft &&
                                    it.frame.x < midRight
                            }
                            .maxOfOrNull { it.frame.y + it.frame.height } ?: 0f
                    if (topClearUnits > 0f) {
                        ((topClearUnits * approxScale) + 12f).dp
                    } else {
                        8.dp
                    }
                }
            }

        StudioCanvasLayer(
            isLandscape = isLandscape,
            dockHudAtBottom = dockHudAtBottom,
            layout = layout,
            rewindAvailable = rewindAvailable,
            fastForwardSupported = fastForwardSupported,
            fastForwardDotsTotal = fastForwardDotsTotal,
            fastForwardDotsActive = fastForwardDotsActive,
            customizationState = customizationState,
            panel = panel,
            screenWindows = screenWindows,
            defaultScreenWindows = defaultScreenWindows,
            editableBodyTop = editableBodyTop,
            defaultBodyTop = defaultBodyTop,
            minBodyTop = minBodyTop,
            maxBodyTop = maxBodyTop,
            safeTopPx = safeTopPx,
            stageHeightPx = stageHeightPx,
            shell = shell,
            art = art,
            selectedIndex = selectedIndex,
            selectedScreenIndex = selectedScreenIndex,
            bgSelected = bgSelected,
            onSelect = { idx ->
                selectedIndex = idx
                if (idx != null) {
                    selectedScreenIndex = null
                    bgSelected = false
                }
                globalOpen = false
            },
            onSelectScreen = { screenIdx ->
                selectedScreenIndex = screenIdx
                if (screenIdx != null) {
                    selectedIndex = null
                    bgSelected = false
                }
                globalOpen = false
            },
            onSelectBg = { selected ->
                bgSelected = selected
                if (selected) {
                    selectedIndex = null
                    selectedScreenIndex = null
                }
                globalOpen = false
            },
            onSurfaceMeasured = { surfDp, extraUnits ->
                editorSurfaceDp = surfDp
                extraUnitsAbove = extraUnits
            },
            onCanvasBusyChange = { canvasBusy = it },
        )

        val outerHorizontalPad = if (screenW < 380.dp) 8.dp else 12.dp
        Column(
            modifier =
                Modifier
                    .align(if (dockHudAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        start = outerHorizontalPad,
                        end = outerHorizontalPad,
                        top = hudTopPadding,
                        bottom = if (dockHudAtBottom) 12.dp else 8.dp,
                    )
                    .graphicsLayer { alpha = hudAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val safeOnLandscapeChange: ((Boolean) -> Unit)? =
                onLandscapeChange?.let { callback ->
                    { nextLandscape ->
                        if (nextLandscape != isLandscape) {
                            selectedIndex = null
                            selectedScreenIndex = null
                            bgSelected = false
                            globalOpen = false
                            callback(nextLandscape)
                        }
                    }
                }

            val performResetAll = {
                selectedIndex = null
                selectedScreenIndex = null
                bgSelected = false
                globalOpen = false
                if (customizationState.value.isCustomized) {
                    onResetAll()
                }
            }

            if (!dockHudAtBottom) {
                StudioCommandPill(
                    screenWidth = screenW,
                    isLandscape = isLandscape,
                    buttonHeight = buttonHeight,
                    onLandscapeChange = safeOnLandscapeChange,
                    canReset = canReset,
                    globalOpen = globalOpen,
                    hasGlobalChanges = hasGlobalChanges,
                    onResetAll = performResetAll,
                    onToggleGlobal = {
                        if (globalOpen) {
                            globalOpen = false
                        } else {
                            selectedIndex = null
                            selectedScreenIndex = null
                            bgSelected = false
                            globalOpen = true
                        }
                    },
                    onDone = onDone,
                )
            }

            val activeIdx = selectedIndex
            val activeControl = activeIdx?.let { layout.controls.getOrNull(it) }
            val activeScreenIdx = selectedScreenIndex
            if (globalOpen) {
                GlobalControllerOptionsCard(
                    layout = layout,
                    rewindAvailable = rewindAvailable,
                    fastForwardSupported = fastForwardSupported,
                    customizationState = customizationState,
                    variant = variant,
                    isLandscape = isLandscape,
                    editableBodyTop = editableBodyTop,
                    defaultBodyTop = defaultBodyTop,
                    minBodyTop = minBodyTop,
                    maxBodyTop = maxBodyTop,
                    stageHeightPx = stageHeightPx,
                    onScrubberBusyChange = { scrubberBusy = it },
                    onClose = { globalOpen = false },
                    onVariantChange = { nextVariant ->
                        if (nextVariant != variant) {
                            onVariantChange(nextVariant)
                        }
                    },
                    controllerOptions = controllerOptions,
                    selectedController = selectedController,
                    onSelectController = onSelectController,
                )
            } else if (activeIdx != null && activeControl != null) {
                key(activeIdx) {
                    SelectedButtonScaleBar(
                        index = activeIdx,
                        control = activeControl,
                        isLandscape = isLandscape,
                        layout = layout,
                        editorSurfaceDp = editorSurfaceDp,
                        extraUnitsAbove = extraUnitsAbove,
                        customizationState = customizationState,
                        onScrubberBusyChange = { scrubberBusy = it },
                        onHide = {
                            customizationState.value =
                                customizationState.value.setControlVisible(
                                    activeIdx,
                                    activeControl,
                                    visible = false,
                                )
                            selectedIndex = null
                        },
                    )
                }
            } else if (activeScreenIdx != null && activeScreenIdx in screenWindows.indices) {
                key(activeScreenIdx) {
                    SelectedScreenScaleBar(
                        isLandscape = isLandscape,
                        screenIndex = activeScreenIdx,
                        screenCount = screenWindows.size,
                        onSelectScreenIndex = { nextIdx ->
                            if (nextIdx in screenWindows.indices) {
                                selectedScreenIndex = nextIdx
                            }
                        },
                        customizationState = customizationState,
                        onScrubberBusyChange = { scrubberBusy = it },
                    )
                }
            } else if (bgSelected && !isLandscape && editableBodyTop != null && defaultBodyTop != null) {
                SelectedBgHeightBar(
                    bodyTop = editableBodyTop,
                    defaultBodyTop = defaultBodyTop,
                    minBodyTop = minBodyTop,
                    maxBodyTop = maxBodyTop,
                    stageHeightPx = stageHeightPx,
                    customizationState = customizationState,
                    onScrubberBusyChange = { scrubberBusy = it },
                )
            }

            if (dockHudAtBottom) {
                StudioCommandPill(
                    screenWidth = screenW,
                    isLandscape = isLandscape,
                    buttonHeight = buttonHeight,
                    onLandscapeChange = safeOnLandscapeChange,
                    canReset = canReset,
                    globalOpen = globalOpen,
                    hasGlobalChanges = hasGlobalChanges,
                    onResetAll = performResetAll,
                    onToggleGlobal = {
                        if (globalOpen) {
                            globalOpen = false
                        } else {
                            selectedIndex = null
                            selectedScreenIndex = null
                            bgSelected = false
                            globalOpen = true
                        }
                    },
                    onDone = onDone,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.StudioCanvasLayer(
    isLandscape: Boolean,
    dockHudAtBottom: Boolean,
    layout: SkinLayout,
    rewindAvailable: Boolean,
    fastForwardSupported: Boolean,
    fastForwardDotsTotal: Int,
    fastForwardDotsActive: Int,
    customizationState: MutableState<SkinCustomization>,
    panel: Rect?,
    screenWindows: List<Rect>,
    defaultScreenWindows: List<Rect>,
    editableBodyTop: Float?,
    defaultBodyTop: Float?,
    minBodyTop: Float,
    maxBodyTop: Float,
    safeTopPx: Float,
    stageHeightPx: Float,
    shell: Boolean,
    art: SkinArt?,
    selectedIndex: Int?,
    selectedScreenIndex: Int?,
    bgSelected: Boolean,
    onSelect: (Int?) -> Unit,
    onSelectScreen: (Int?) -> Unit,
    onSelectBg: (Boolean) -> Unit,
    onSurfaceMeasured: (Offset, Float) -> Unit,
    onCanvasBusyChange: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val customization = customizationState.value
    var stageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var editorCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var stageWidthPx by remember { mutableStateOf(1f) }
    var editorSizePx by remember(isLandscape) { mutableStateOf(IntSize.Zero) }
    var screenSnapV by remember { mutableStateOf<Float?>(null) }
    var screenSnapH by remember { mutableStateOf<Float?>(null) }
    var screenBadge by remember { mutableStateOf<String?>(null) }
    var bgDragging by remember { mutableStateOf(false) }
    var bgBadge by remember { mutableStateOf<String?>(null) }

    val above = if (!isLandscape && panel != null) panel.top.coerceAtLeast(0f) else 0f
    val aboveDp = with(density) { above.toDp() }

    LaunchedEffect(editorSizePx, isLandscape, layout.width, aboveDp, density) {
        if (editorSizePx.width > 0 && editorSizePx.height > 0) {
            with(density) {
                if (isLandscape) {
                    onSurfaceMeasured(
                        Offset(editorSizePx.width.toDp().value, editorSizePx.height.toDp().value),
                        0f,
                    )
                } else {
                    val surfW = editorSizePx.width.toDp().value.coerceAtLeast(1f)
                    val surfH = (editorSizePx.height.toDp() - aboveDp).value.coerceAtLeast(1f)
                    val unitScale = surfW / layout.width
                    val extraUnits = if (unitScale > 0f) aboveDp.value / unitScale else 0f
                    onSurfaceMeasured(Offset(surfW, surfH), extraUnits)
                }
            }
        }
    }

    val hitInflatePx = with(density) { 8.dp.toPx() }
    val handleHalfW = with(density) { 38.dp.toPx() }
    val handleHalfH = with(density) { 22.dp.toPx() }
    val minHandleTop =
        safeTopPx +
            with(density) {
                (if (dockHudAtBottom || bgDragging) 16.dp else 60.dp).toPx()
            }
    val maxHandleBottom = (stageHeightPx - with(density) { 24.dp.toPx() }).coerceAtLeast(minHandleTop)

    val currentHaptics by rememberUpdatedState(haptics)
    val currentWindows by rememberUpdatedState(screenWindows)
    val currentDefaultWindows by rememberUpdatedState(defaultScreenWindows)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentSelectedScreenIdx by rememberUpdatedState(selectedScreenIndex)
    val currentBgSelected by rememberUpdatedState(bgSelected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelectScreen by rememberUpdatedState(onSelectScreen)
    val currentSelectBg by rememberUpdatedState(onSelectBg)
    val currentBusyChange by rememberUpdatedState(onCanvasBusyChange)
    val currentEditableBodyTop by rememberUpdatedState(editableBodyTop)
    val currentDefaultBodyTop by rememberUpdatedState(defaultBodyTop)
    val currentMinBodyTop by rememberUpdatedState(minBodyTop)
    val currentMaxBodyTop by rememberUpdatedState(maxBodyTop)
    val currentMinHandleTop by rememberUpdatedState(minHandleTop)
    val currentMaxHandleBottom by rememberUpdatedState(maxHandleBottom)
    val currentStageWidthPx by rememberUpdatedState(stageWidthPx)
    val currentStageCoords by rememberUpdatedState(stageCoords)
    val currentEditorCoords by rememberUpdatedState(editorCoords)

    fun editorToStage(localPt: Offset): Offset {
        val sc = currentStageCoords
        val ec = currentEditorCoords
        return if (sc != null && ec != null && sc.isAttached && ec.isAttached) {
            sc.localPositionOf(ec, localPt)
        } else {
            localPt
        }
    }

    fun isOnBgHandle(canvasPt: Offset): Boolean {
        if (isLandscape) return false
        val bt = currentEditableBodyTop ?: return false
        val handleY = bt.coerceIn(currentMinHandleTop, currentMaxHandleBottom)
        val cx = currentStageWidthPx / 2f
        return abs(canvasPt.x - cx) <= handleHalfW && abs(canvasPt.y - handleY) <= handleHalfH
    }

    val shouldYieldToLowerLayer: (Offset, Boolean, Boolean) -> Boolean = { localPt, exactHit, haloHit ->
        val canvasPt = editorToStage(localPt)
        when {
            // The visible `=` BG pill handle in Portrait Edit Mode is always directly grabbable up & down,
            // whether the BG sits at default height, raised behind screens, or lowered over controls.
            isOnBgHandle(canvasPt) -> true
            // Layer 3 (Buttons) strictly wins on any exact control shape hit.
            exactHit -> false
            // Layer 2 (Screen) strictly wins over button halos and over any previously selected button!
            resolveScreenWindowAt(currentWindows, canvasPt, hitInflatePx) != null -> true
            // Forgiving 42dp halo around buttons on the controller deck outside screens.
            haloHit -> false
            // Open space: yield to lower layer when no button is selected (for Screen pinch or BG drag/tap).
            else -> currentSelectedIndex == null
        }
    }

    val backgroundTapHandler: (Offset) -> Unit = { localPt ->
        val canvasPt = editorToStage(localPt)
        val hitScreen = resolveScreenWindowAt(currentWindows, canvasPt, hitInflatePx)
        if (hitScreen != null) {
            currentSelectScreen(hitScreen)
        } else {
            currentOnSelect(null)
        }
    }

    // IMPORTANT: In Jetpack Compose, sibling LayoutNodes do NOT share pointerInput hit-testing
    // (PointerInputModifierNode.sharePointerInputWithSiblings == false). Placing the lower-layer
    // Screen + Portrait BG gesture router on this PARENT Box puts both ArcaduneSkinEditor (Child)
    // and this router (Parent) on the exact same hit path: in PointerEventPass.Main, Child runs
    // first (handling Layer 3 buttons or yielding unconsumed) and Parent runs second immediately!
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    stageCoords = coords
                    stageWidthPx = coords.size.width.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(isLandscape) {
                    val dragSlopPx = 3.5.dp.toPx()
                    val screenHitPadPx = 8.dp.toPx()
                    val pinchScreenPadPx = 28.dp.toPx()
                    val bgEdgePadPx = 14.dp.toPx()
                    val snapPx = 6.dp.toPx()
                    val keepMaxPx = 88.dp.toPx()
                    val minPinchSpanPx = 24.dp.toPx()

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                        val pt = down.position
                        val stageW = size.width.toFloat().coerceAtLeast(1f)
                        val stageH = size.height.toFloat().coerceAtLeast(1f)
                        val startWindows = currentWindows
                        val directScreenIdx = resolveScreenWindowAt(startWindows, pt, screenHitPadPx)
                        val onBgPill = isOnBgHandle(pt)
                        val startBodyTop = currentEditableBodyTop
                        val defaultTop = currentDefaultBodyTop

                        down.consume()

                        fun defaultWindowFor(idx: Int): Rect =
                            currentDefaultWindows.getOrNull(idx) ?: run {
                                val curWin =
                                    currentWindows.getOrElse(idx) { Rect(0f, 0f, stageW, stageH) }
                                val cust = customizationState.value
                                val curOff = cust.screenOffsetOf(idx)
                                val curScale = cust.screenScaleOf(idx).coerceAtLeast(0.1f)
                                val zeroLeft = curWin.left - curOff.x * stageW
                                val zeroTop = curWin.top - curOff.y * stageH
                                val zeroWin =
                                    Rect(
                                        zeroLeft,
                                        zeroTop,
                                        zeroLeft + curWin.width,
                                        zeroTop + curWin.height,
                                    )
                                val zc = zeroWin.center
                                val dw = zeroWin.width / curScale
                                val dh = zeroWin.height / curScale
                                Rect(zc.x - dw / 2f, zc.y - dh / 2f, zc.x + dw / 2f, zc.y + dh / 2f)
                            }

                        fun clampScreenOffsetFor(
                            idx: Int,
                            rawOffset: Offset,
                            scale: Float,
                        ): Offset {
                            val defWin = defaultWindowFor(idx)
                            val w = defWin.width * scale
                            val h = defWin.height * scale
                            val sLeft = defWin.center.x - w / 2f
                            val sRight = defWin.center.x + w / 2f
                            val sTop = defWin.center.y - h / 2f
                            val sBottom = defWin.center.y + h / 2f
                            val keepX = minOf(w * 0.55f, keepMaxPx)
                            val keepY = minOf(h * 0.55f, keepMaxPx)
                            val minDx = minOf(keepX - sRight, (stageW - keepX) - sLeft)
                            val maxDx = maxOf(keepX - sRight, (stageW - keepX) - sLeft)
                            val minDy = minOf(keepY - sBottom, (stageH - keepY) - sTop)
                            val maxDy = maxOf(keepY - sBottom, (stageH - keepY) - sTop)
                            val dx = (rawOffset.x * stageW).coerceIn(minDx, maxDx)
                            val dy = (rawOffset.y * stageH).coerceIn(minDy, maxDy)
                            return Offset(dx / stageW, dy / stageH)
                        }

                        // Mode 1: Direct grab on Portrait BG pill handle — moves BG skin layer up & down 1:1.
                        if (onBgPill && startBodyTop != null && defaultTop != null) {
                            if (!currentBgSelected) {
                                currentHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentSelectBg(true)
                            val startHandleY =
                                startBodyTop.coerceIn(currentMinHandleTop, currentMaxHandleBottom)
                            val grabDeltaY = down.position.y - startHandleY
                            var pillDragging = false
                            var wasSnappedDefault = false
                            var wasSnappedFull = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    event.changes.forEach { it.consume() }
                                    val primary = pressed.firstOrNull { it.id == down.id } ?: continue
                                    val dy = primary.position.y - down.position.y
                                    if (!pillDragging && abs(dy) > dragSlopPx) {
                                        pillDragging = true
                                        bgDragging = true
                                        currentBusyChange(true)
                                    }
                                    if (pillDragging) {
                                        val rawTop =
                                            if (startBodyTop < startHandleY - 2f && dy > 0f) {
                                                primary.position.y - grabDeltaY
                                            } else {
                                                startBodyTop + dy
                                            }
                                        var targetTop =
                                            rawTop.coerceIn(
                                                currentMinBodyTop,
                                                currentMaxBodyTop,
                                            )
                                        val nearDefault = abs(targetTop - defaultTop) <= snapPx
                                        val nearFull = targetTop <= currentMinBodyTop + snapPx
                                        if (nearDefault) {
                                            targetTop = defaultTop
                                            if (!wasSnappedDefault) {
                                                currentHaptics.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                            }
                                        } else if (nearFull) {
                                            targetTop = currentMinBodyTop
                                            if (!wasSnappedFull) {
                                                currentHaptics.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                            }
                                        }
                                        wasSnappedDefault = nearDefault
                                        wasSnappedFull = nearFull
                                        val nextOffset =
                                            bodyTopToPortraitBgOffset(
                                                targetTop,
                                                defaultTop,
                                                currentMinBodyTop,
                                                currentMaxBodyTop,
                                            )
                                        customizationState.value =
                                            customizationState.value.withPortraitBgOffset(nextOffset)
                                        val pct =
                                            (((stageH - targetTop) / stageH) * 100f)
                                                .roundToInt()
                                                .coerceIn(1, 100)
                                        bgBadge = "$pct%"
                                    }
                                }
                            } finally {
                                bgBadge = null
                                bgDragging = false
                                currentBusyChange(false)
                            }
                            return@awaitEachGesture
                        }

                        // Mode 2 & 3: Screen manipulation (direct touch or 2-finger pinch) + open Portrait BG drag/tap.
                        var activeScreenIdx = directScreenIdx
                        val allowOneFingerScreenDrag = directScreenIdx != null
                        if (directScreenIdx != null) {
                            if (directScreenIdx != currentSelectedScreenIdx) {
                                currentHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentSelectScreen(directScreenIdx)
                        }

                        val startScreenOffset =
                            directScreenIdx?.let { customizationState.value.screenOffsetOf(it) }
                                ?: Offset.Zero
                        val onBgSurface =
                            !isLandscape &&
                                startBodyTop != null &&
                                defaultTop != null &&
                                pt.y >= startBodyTop - bgEdgePadPx

                        var screenDragging = false
                        var screenPinching = false
                        var bgSurfaceDragging = false
                        var pinchPair: Pair<Long, Long>? = null
                        var pinchStartSpan = 1f
                        var pinchStartCentroid = Offset.Zero
                        var pinchBaseScale = 1f
                        var pinchBaseOffset = Offset.Zero
                        var prevVGuide: Float? = null
                        var prevHGuide: Float? = null
                        var wasSnappedScale = false
                        var wasBgSnappedDefault = false
                        var wasBgSnappedFull = false

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                event.changes.forEach { it.consume() }

                                if (pressed.size >= 2 && !bgSurfaceDragging) {
                                    val p0 = pressed[0]
                                    val p1 = pressed[1]
                                    val centroid = (p0.position + p1.position) / 2f
                                    if (activeScreenIdx == null) {
                                        val candidateIdx =
                                            currentSelectedScreenIdx
                                                ?: resolveScreenWindowAt(
                                                    currentWindows,
                                                    centroid,
                                                    pinchScreenPadPx,
                                                )
                                                ?: resolveScreenWindowAt(
                                                    currentWindows,
                                                    p1.position,
                                                    pinchScreenPadPx,
                                                )
                                                ?: if (currentWindows.size == 1) 0 else null
                                        if (candidateIdx != null) {
                                            activeScreenIdx = candidateIdx
                                            if (candidateIdx != currentSelectedScreenIdx) {
                                                currentHaptics.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                                currentSelectScreen(candidateIdx)
                                            }
                                        }
                                    }
                                    val targetIdx = activeScreenIdx
                                    if (targetIdx != null) {
                                        val pair = p0.id.value to p1.id.value
                                        val span = (p1.position - p0.position).getDistance()
                                        if (!screenPinching || pinchPair != pair) {
                                            screenPinching = true
                                            pinchPair = pair
                                            pinchStartSpan = span.coerceAtLeast(minPinchSpanPx)
                                            pinchStartCentroid = centroid
                                            pinchBaseScale =
                                                customizationState.value.screenScaleOf(targetIdx)
                                            pinchBaseOffset =
                                                customizationState.value.screenOffsetOf(targetIdx)
                                            screenSnapV = null
                                            screenSnapH = null
                                            currentBusyChange(true)
                                        } else {
                                            var nextScale =
                                                (pinchBaseScale * (span / pinchStartSpan)).coerceIn(
                                                    SkinCustomization.MIN_SCREEN_SCALE,
                                                    SkinCustomization.MAX_SCREEN_SCALE,
                                                )
                                            val nearDefault = abs(nextScale - 1f) <= 0.02f
                                            if (nearDefault) {
                                                nextScale = 1f
                                                if (!wasSnappedScale) {
                                                    currentHaptics.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove,
                                                    )
                                                }
                                            }
                                            wasSnappedScale = nearDefault
                                            val pan =
                                                if (allowOneFingerScreenDrag) {
                                                    centroid - pinchStartCentroid
                                                } else {
                                                    Offset.Zero
                                                }
                                            val rawOffset =
                                                pinchBaseOffset +
                                                    Offset(pan.x / stageW, pan.y / stageH)
                                            val clamped =
                                                clampScreenOffsetFor(targetIdx, rawOffset, nextScale)
                                            customizationState.value =
                                                customizationState.value.withScreenTransform(
                                                    index = targetIdx,
                                                    scale = nextScale,
                                                    offset = clamped,
                                                )
                                            screenBadge = "${(nextScale * 100).roundToInt()}%"
                                        }
                                    }
                                } else if (!screenPinching && allowOneFingerScreenDrag) {
                                    val targetIdx = activeScreenIdx ?: continue
                                    val change = pressed.firstOrNull { it.id == down.id } ?: continue
                                    val delta = change.position - down.position
                                    if (!screenDragging && delta.getDistance() > dragSlopPx) {
                                        screenDragging = true
                                        currentBusyChange(true)
                                    }
                                    if (screenDragging) {
                                        val defCenter = defaultWindowFor(targetIdx).center
                                        var rawX = startScreenOffset.x + delta.x / stageW
                                        var rawY = startScreenOffset.y + delta.y / stageH
                                        val candCx = defCenter.x + rawX * stageW
                                        val candCy = defCenter.y + rawY * stageH
                                        val siblingCenters =
                                            currentWindows
                                                .withIndex()
                                                .filter { it.index != targetIdx }
                                                .map { it.value.center }
                                        val xTargets =
                                            buildList {
                                                add(defCenter.x)
                                                add(stageW / 2f)
                                                siblingCenters.forEach { add(it.x) }
                                            }
                                        val yTargets =
                                            buildList {
                                                add(defCenter.y)
                                                siblingCenters.forEach { add(it.y) }
                                            }
                                        val snapTargetX =
                                            xTargets
                                                .minByOrNull { abs(it - candCx) }
                                                ?.takeIf { abs(it - candCx) <= snapPx }
                                        val snapTargetY =
                                            yTargets
                                                .minByOrNull { abs(it - candCy) }
                                                ?.takeIf { abs(it - candCy) <= snapPx }
                                        if (snapTargetX != null) {
                                            rawX = (snapTargetX - defCenter.x) / stageW
                                        }
                                        if (snapTargetY != null) {
                                            rawY = (snapTargetY - defCenter.y) / stageH
                                        }
                                        val curScale =
                                            customizationState.value.screenScaleOf(targetIdx)
                                        val clamped =
                                            clampScreenOffsetFor(
                                                targetIdx,
                                                Offset(rawX, rawY),
                                                curScale,
                                            )
                                        val finalCx = defCenter.x + clamped.x * stageW
                                        val finalCy = defCenter.y + clamped.y * stageH
                                        val vGuide = snapTargetX?.takeIf { abs(finalCx - it) <= 1f }
                                        val hGuide = snapTargetY?.takeIf { abs(finalCy - it) <= 1f }
                                        if ((vGuide != null && vGuide != prevVGuide) ||
                                            (hGuide != null && hGuide != prevHGuide)
                                        ) {
                                            currentHaptics.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove,
                                            )
                                        }
                                        prevVGuide = vGuide
                                        prevHGuide = hGuide
                                        customizationState.value =
                                            customizationState.value.withScreenTransform(
                                                index = targetIdx,
                                                offset = clamped,
                                            )
                                        screenSnapV = vGuide
                                        screenSnapH = hGuide
                                    }
                                } else if (!screenPinching && !allowOneFingerScreenDrag && onBgSurface &&
                                    startBodyTop != null && defaultTop != null
                                ) {
                                    val change = pressed.firstOrNull { it.id == down.id } ?: continue
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    if (!bgSurfaceDragging &&
                                        abs(dy) > dragSlopPx &&
                                        abs(dy) >= abs(dx) * 0.75f
                                    ) {
                                        bgSurfaceDragging = true
                                        bgDragging = true
                                        if (!currentBgSelected) {
                                            currentHaptics.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove,
                                            )
                                        }
                                        currentSelectBg(true)
                                        currentBusyChange(true)
                                    }
                                    if (bgSurfaceDragging) {
                                        var targetTop =
                                            (startBodyTop + dy).coerceIn(
                                                currentMinBodyTop,
                                                currentMaxBodyTop,
                                            )
                                        val nearDefault = abs(targetTop - defaultTop) <= snapPx
                                        val nearFull = targetTop <= currentMinBodyTop + snapPx
                                        if (nearDefault) {
                                            targetTop = defaultTop
                                            if (!wasBgSnappedDefault) {
                                                currentHaptics.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                            }
                                        } else if (nearFull) {
                                            targetTop = currentMinBodyTop
                                            if (!wasBgSnappedFull) {
                                                currentHaptics.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                            }
                                        }
                                        wasBgSnappedDefault = nearDefault
                                        wasBgSnappedFull = nearFull
                                        val nextOffset =
                                            bodyTopToPortraitBgOffset(
                                                targetTop,
                                                defaultTop,
                                                currentMinBodyTop,
                                                currentMaxBodyTop,
                                            )
                                        customizationState.value =
                                            customizationState.value.withPortraitBgOffset(nextOffset)
                                        val pct =
                                            (((stageH - targetTop) / stageH) * 100f)
                                                .roundToInt()
                                                .coerceIn(1, 100)
                                        bgBadge = "$pct%"
                                    }
                                }
                            }

                            if (!screenPinching && !screenDragging && !bgSurfaceDragging &&
                                !allowOneFingerScreenDrag
                            ) {
                                val hadSelection =
                                    currentSelectedScreenIdx != null || currentBgSelected
                                if (!hadSelection && onBgSurface) {
                                    currentHaptics.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove,
                                    )
                                    currentSelectBg(true)
                                } else {
                                    currentSelectScreen(null)
                                    currentSelectBg(false)
                                }
                            }
                        } finally {
                            screenSnapV = null
                            screenSnapH = null
                            screenBadge = null
                            bgBadge = null
                            bgDragging = false
                            currentBusyChange(false)
                        }
                    }
                },
    ) {
        // Layer 3 Manipulator & Renderer (Top — Controller Buttons)
        if (isLandscape) {
            ArcaduneSkinEditor(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(4.dp)
                        .onGloballyPositioned { editorCoords = it }
                        .onSizeChanged { editorSizePx = it },
                layout = layout,
                pinToEdges = true,
                rewindAvailable = rewindAvailable,
                fastForwardSupported = fastForwardSupported,
                fastForwardDotsTotal = fastForwardDotsTotal,
                fastForwardDotsActive = fastForwardDotsActive,
                customization = customization,
                onCustomizationChange = { customizationState.value = it },
                snapEnabled = true,
                shell = shell,
                art = art,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                accentColor = ArcaduneColors.Purple,
                onAccentColor = ArcaduneColors.OnAccent,
                onCanvasBusyChange = onCanvasBusyChange,
                shouldYieldToLowerLayer = shouldYieldToLowerLayer,
                onBackgroundTap = backgroundTapHandler,
            )
        } else {
            val editorModifier =
                if (panel != null) {
                    with(density) {
                        Modifier
                            .absoluteOffset {
                                IntOffset(
                                    panel.left.toInt(),
                                    (panel.top - above).toInt(),
                                )
                            }
                            .size(panel.width.toDp(), (panel.height + above).toDp())
                    }
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .aspectRatio(layout.width / layout.height)
                }
            ArcaduneSkinEditor(
                modifier =
                    editorModifier
                        .onGloballyPositioned { editorCoords = it }
                        .onSizeChanged { editorSizePx = it },
                layout = layout,
                pinToEdges = false,
                rewindAvailable = rewindAvailable,
                fastForwardSupported = fastForwardSupported,
                fastForwardDotsTotal = fastForwardDotsTotal,
                fastForwardDotsActive = fastForwardDotsActive,
                customization = customization,
                onCustomizationChange = { customizationState.value = it },
                snapEnabled = true,
                shell = shell,
                art = art,
                topExtra = aboveDp,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                accentColor = ArcaduneColors.Purple,
                onAccentColor = ArcaduneColors.OnAccent,
                onCanvasBusyChange = onCanvasBusyChange,
                shouldYieldToLowerLayer = shouldYieldToLowerLayer,
                onBackgroundTap = backgroundTapHandler,
            )
        }

        if (!isLandscape && editableBodyTop != null) {
            PortraitBgSelectionOverlay(
                editableBodyTop = editableBodyTop,
                minHandleTop = minHandleTop,
                maxHandleBottom = maxHandleBottom,
                bgSelected = bgSelected,
                bgDragging = bgDragging,
                badgeText = bgBadge,
            )
        }

        if (screenWindows.isNotEmpty()) {
            ScreenSelectionOverlay(
                screenWindows = screenWindows,
                selectedScreenIndex = selectedScreenIndex,
                snapVerticalPx = screenSnapV,
                snapHorizontalPx = screenSnapH,
                badgeText = screenBadge,
            )
        }
    }
}

private fun resolveScreenWindowAt(
    windows: List<Rect>,
    pt: Offset,
    hitInflatePx: Float,
): Int? {
    val exactMatches = windows.withIndex().filter { (_, win) -> win.contains(pt) }
    return when {
        exactMatches.size == 1 -> exactMatches.first().index
        exactMatches.size > 1 -> {
            val maxArea = exactMatches.maxOf { (it.value.width * it.value.height).coerceAtLeast(1f) }
            val smaller =
                exactMatches.filter {
                    (it.value.width * it.value.height) < maxArea * 0.65f
                }
            val pool = smaller.ifEmpty { exactMatches }
            pool.minByOrNull { (it.value.center - pt).getDistance() }?.index
        }
        else ->
            windows
                .withIndex()
                .filter { (_, win) -> win.inflate(hitInflatePx).contains(pt) }
                .minByOrNull { (_, win) ->
                    val dx = maxOf(win.left - pt.x, 0f, pt.x - win.right)
                    val dy = maxOf(win.top - pt.y, 0f, pt.y - win.bottom)
                    kotlin.math.hypot(dx, dy)
                }
                ?.index
    }
}

private fun bgHeightSliderFraction(
    bodyTop: Float,
    minBodyTop: Float,
    maxBodyTop: Float,
): Float {
    val range = (maxBodyTop - minBodyTop).coerceAtLeast(1f)
    return ((maxBodyTop - bodyTop) / range).coerceIn(0f, 1f)
}

private fun sliderFractionToPortraitBgOffset(
    fraction: Float,
    defaultBodyTop: Float,
    minBodyTop: Float,
    maxBodyTop: Float,
): Float {
    val targetBodyTop = maxBodyTop - fraction.coerceIn(0f, 1f) * (maxBodyTop - minBodyTop)
    return bodyTopToPortraitBgOffset(targetBodyTop, defaultBodyTop, minBodyTop, maxBodyTop)
}

private fun bodyTopToPortraitBgOffset(
    targetBodyTop: Float,
    defaultBodyTop: Float,
    minBodyTop: Float,
    maxBodyTop: Float,
): Float {
    val clamped = targetBodyTop.coerceIn(minBodyTop, maxBodyTop)
    return if (clamped <= defaultBodyTop) {
        val upSpan = defaultBodyTop - minBodyTop
        if (upSpan > 0.5f) ((defaultBodyTop - clamped) / upSpan).coerceIn(0f, 1f) else 0f
    } else {
        val downSpan = maxBodyTop - defaultBodyTop
        if (downSpan > 0.5f) -((clamped - defaultBodyTop) / downSpan).coerceIn(0f, 1f) else 0f
    }
}

@Composable
private fun BoxScope.PortraitBgSelectionOverlay(
    editableBodyTop: Float,
    minHandleTop: Float,
    maxHandleBottom: Float,
    bgSelected: Boolean,
    bgDragging: Boolean,
    badgeText: String?,
) {
    val accent = ArcaduneColors.Purple
    val highlighted = bgSelected || bgDragging
    val handleY = editableBodyTop.coerceIn(minHandleTop, maxHandleBottom)

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (highlighted) {
            val edgeY = editableBodyTop.coerceIn(1.5.dp.toPx(), size.height - 1.5.dp.toPx())
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            drawLine(
                color = Color.Black.copy(alpha = 0.50f),
                start = Offset(0f, edgeY),
                end = Offset(size.width, edgeY),
                strokeWidth = 3.dp.toPx(),
            )
            drawLine(
                color = accent.copy(alpha = 0.90f),
                start = Offset(0f, edgeY),
                end = Offset(size.width, edgeY),
                strokeWidth = 1.6.dp.toPx(),
                pathEffect = dash,
            )
        }

        val cx = size.width / 2f
        val pillW = 52.dp.toPx()
        val pillH = 20.dp.toPx()
        val pillCorner = CornerRadius(pillH / 2f)
        val topLeft = Offset(cx - pillW / 2f, handleY - pillH / 2f)
        drawRoundRect(
            color = StudioGlass,
            topLeft = topLeft,
            size = Size(pillW, pillH),
            cornerRadius = pillCorner,
        )
        drawRoundRect(
            color = if (highlighted) accent else Color.White.copy(alpha = 0.34f),
            topLeft = topLeft,
            size = Size(pillW, pillH),
            cornerRadius = pillCorner,
            style = Stroke(width = if (highlighted) 1.7.dp.toPx() else 1.1.dp.toPx()),
        )
        val barHalfW = 9.dp.toPx()
        val gapY = 2.3.dp.toPx()
        val barColor = if (highlighted) accent else Color.White.copy(alpha = 0.72f)
        drawLine(
            color = barColor,
            start = Offset(cx - barHalfW, handleY - gapY),
            end = Offset(cx + barHalfW, handleY - gapY),
            strokeWidth = 1.7.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = barColor,
            start = Offset(cx - barHalfW, handleY + gapY),
            end = Offset(cx + barHalfW, handleY + gapY),
            strokeWidth = 1.7.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    if (badgeText != null) {
        val density = LocalDensity.current
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .absoluteOffset {
                        val offsetY =
                            (handleY - with(density) { 44.dp.toPx() })
                                .coerceAtLeast(minHandleTop)
                                .roundToInt()
                        IntOffset(0, offsetY)
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(StudioGlass)
                    .border(1.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoxScope.ScreenSelectionOverlay(
    screenWindows: List<Rect>,
    selectedScreenIndex: Int?,
    snapVerticalPx: Float?,
    snapHorizontalPx: Float?,
    badgeText: String?,
) {
    val accent = ArcaduneColors.Purple
    val density = LocalDensity.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        if (snapVerticalPx != null) {
            drawLine(
                color = accent.copy(alpha = 0.72f),
                start = Offset(snapVerticalPx, 0f),
                end = Offset(snapVerticalPx, size.height),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
        }
        if (snapHorizontalPx != null) {
            drawLine(
                color = accent.copy(alpha = 0.72f),
                start = Offset(0f, snapHorizontalPx),
                end = Offset(size.width, snapHorizontalPx),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
        }
        val pad = 1.5.dp.toPx()
        val edgeInset = 2.5.dp.toPx()
        val corner = CornerRadius(7.dp.toPx())
        val outerStroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        val innerStroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        val unselectedStroke = Stroke(width = 1.3.dp.toPx(), pathEffect = dash)
        screenWindows.forEachIndexed { idx, win ->
            val raw = win.inflate(pad)
            val left = raw.left.coerceIn(edgeInset, (size.width - edgeInset).coerceAtLeast(edgeInset))
            val top = raw.top.coerceIn(edgeInset, (size.height - edgeInset).coerceAtLeast(edgeInset))
            val right = raw.right.coerceIn(left + 1f, (size.width - edgeInset).coerceAtLeast(left + 1f))
            val bottom = raw.bottom.coerceIn(top + 1f, (size.height - edgeInset).coerceAtLeast(top + 1f))
            val rect = Rect(left, top, right, bottom)
            if (idx == selectedScreenIndex) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = corner,
                    style = outerStroke,
                )
                drawRoundRect(
                    color = accent,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = corner,
                    style = innerStroke,
                )
            } else {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.26f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = corner,
                    style = unselectedStroke,
                )
            }
        }
    }

    // Subtle 1 / 2 corner index badges on dual-screen consoles (DS & 3DS) so each screen is unmistakable.
    if (screenWindows.size > 1) {
        val tagMarginPx = with(density) { 6.dp.toPx() }
        screenWindows.forEachIndexed { idx, win ->
            val selected = idx == selectedScreenIndex
            Box(
                modifier =
                    Modifier
                        .absoluteOffset {
                            IntOffset(
                                (win.left + tagMarginPx).coerceAtLeast(tagMarginPx).roundToInt(),
                                (win.top + tagMarginPx).coerceAtLeast(tagMarginPx).roundToInt(),
                            )
                        }
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else StudioGlass)
                        .border(
                            1.dp,
                            if (selected) accent else Color.White.copy(alpha = 0.30f),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${idx + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurface,
                    maxLines = 1,
                )
            }
        }
    }

    val activeWin = selectedScreenIndex?.let { screenWindows.getOrNull(it) } ?: screenWindows.firstOrNull()
    if (badgeText != null && activeWin != null) {
        Box(
            modifier =
                Modifier
                    .absoluteOffset {
                        IntOffset(
                            (activeWin.center.x - with(density) { 36.dp.toPx() }).roundToInt(),
                            (activeWin.center.y - with(density) { 16.dp.toPx() }).roundToInt(),
                        )
                    }
                    .clip(RoundedCornerShape(999.dp))
                    .background(StudioGlass)
                    .border(1.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StudioCommandPill(
    screenWidth: Dp,
    isLandscape: Boolean,
    buttonHeight: Dp,
    onLandscapeChange: ((Boolean) -> Unit)?,
    canReset: Boolean,
    globalOpen: Boolean,
    hasGlobalChanges: Boolean,
    onResetAll: () -> Unit,
    onToggleGlobal: () -> Unit,
    onDone: () -> Unit,
) {
    val pillShape = RoundedCornerShape(999.dp)
    val fontScale = LocalConfiguration.current.fontScale.coerceAtLeast(1f)
    val scaledWidth = screenWidth / fontScale
    val roomy = scaledWidth >= 460.dp || onLandscapeChange == null
    val itemSpacing = if (roomy) 6.dp else 5.dp
    Surface(
        modifier = Modifier.border(1.dp, ArcaduneColors.GlassEdge, pillShape),
        shape = pillShape,
        color = StudioGlass,
        contentColor = ArcaduneColors.OnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (roomy) 6.dp else 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (onLandscapeChange != null) {
                StudioOrientationSwitch(
                    isLandscape = isLandscape,
                    scaledWidth = scaledWidth,
                    height = buttonHeight,
                    onChange = onLandscapeChange,
                )
            }
            StudioIconButton(
                icon = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.skin_editor_global_options),
                active = globalOpen,
                showBadgeDot = hasGlobalChanges && !globalOpen,
                onClick = onToggleGlobal,
                size = buttonHeight,
                iconSize = if (buttonHeight >= 44.dp) 21.dp else 20.dp,
            )
            StudioActionPill(
                label = stringResource(R.string.skin_editor_reset_short),
                icon = if (roomy) Icons.Filled.RestartAlt else null,
                highlighted = false,
                enabled = canReset,
                height = buttonHeight,
                horizontalPadding = if (roomy) 13.dp else 11.dp,
                onClick = onResetAll,
            )
            StudioActionPill(
                label = stringResource(R.string.skin_editor_done),
                icon = null,
                highlighted = true,
                enabled = true,
                height = buttonHeight,
                horizontalPadding = if (roomy) 18.dp else 14.dp,
                onClick = onDone,
            )
        }
    }
}

@Composable
private fun StudioOrientationSwitch(
    isLandscape: Boolean,
    scaledWidth: Dp,
    height: Dp,
    onChange: (Boolean) -> Unit,
) {
    val trackShape = RoundedCornerShape(999.dp)
    val showIcons = scaledWidth >= 460.dp || scaledWidth < 330.dp
    val showLabels = scaledWidth >= 330.dp
    Row(
        modifier =
            Modifier
                .height(height)
                .clip(trackShape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(1.dp, ArcaduneColors.GlassEdge, trackShape)
                .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val items =
            listOf(
                Triple(false, Icons.Filled.StayCurrentPortrait, R.string.skin_layout_upright),
                Triple(true, Icons.Filled.StayCurrentLandscape, R.string.skin_layout_sideways),
            )
        items.forEach { (land, icon, labelRes) ->
            val selected = land == isLandscape
            val fg = if (selected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim
            val horizPad =
                when {
                    showIcons && showLabels -> 12.dp
                    showLabels -> 10.dp
                    else -> 10.dp
                }
            Row(
                modifier =
                    Modifier
                        .height(height - 6.dp)
                        .studioTap(onClick = { onChange(land) })
                        .clip(trackShape)
                        .background(if (selected) ArcaduneColors.Purple else Color.Transparent)
                        .padding(horizontal = horizPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (showIcons) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(labelRes),
                        tint = fg,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (showLabels) {
                    Text(
                        text = stringResource(labelRes),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                        color = fg,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Ultra-minimal single-row scale & hide capsule for a selected button:
 * `[ EyeOff ]  ( - )  ━━━━━━━●━━━━━━━  ( + )  [ 100% ]`
 */
@Composable
private fun SelectedButtonScaleBar(
    index: Int,
    @Suppress("UNUSED_PARAMETER")
    control: SkinControl,
    isLandscape: Boolean,
    layout: SkinLayout,
    editorSurfaceDp: Offset,
    extraUnitsAbove: Float,
    customizationState: MutableState<SkinCustomization>,
    onScrubberBusyChange: (Boolean) -> Unit = {},
    onHide: () -> Unit,
) {
    val targetIndex = index
    val scaleVal by remember(customizationState, targetIndex) {
        derivedStateOf { customizationState.value.controlScale(targetIndex) }
    }
    val scalePercent = (scaleVal * 100).roundToInt()
    val scaleChanged = abs(scaleVal - 1f) > 0.01f
    val capsuleShape = RoundedCornerShape(999.dp)

    fun applyScale(nextScale: Float) {
        val latest = customizationState.value
        val scaled = latest.copy(scales = latest.scales + (targetIndex to nextScale))
        val curOff = scaled.offsets[targetIndex] ?: Offset.Zero
        val clamped =
            layout.clampControlOffset(
                pinToEdges = isLandscape,
                surfaceWidthDp = editorSurfaceDp.x,
                surfaceHeightDp = editorSurfaceDp.y,
                index = targetIndex,
                customization = scaled,
                candidate = curOff,
                snapToDefault = false,
                extraUnitsAbove = extraUnitsAbove,
            )
        val nextOffsets =
            if (targetIndex in scaled.offsets || abs(clamped.x) > 0.01f || abs(clamped.y) > 0.01f) {
                scaled.offsets + (targetIndex to clamped)
            } else {
                scaled.offsets
            }
        customizationState.value = scaled.copy(offsets = nextOffsets)
    }

    Surface(
        modifier =
            Modifier
                .widthIn(max = if (isLandscape) 400.dp else 380.dp)
                .fillMaxWidth()
                .border(1.dp, ArcaduneColors.GlassEdge, capsuleShape),
        shape = capsuleShape,
        color = StudioGlass,
        contentColor = ArcaduneColors.OnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StudioIconButton(
                icon = Icons.Filled.VisibilityOff,
                contentDescription = stringResource(R.string.skin_editor_hide_button),
                onClick = onHide,
                size = 38.dp,
                iconSize = 19.dp,
            )
            ArcadunePrecisionScrubber(
                value = scaleVal,
                valueRange = SkinCustomization.MIN_CONTROL_SCALE..SkinCustomization.MAX_CONTROL_SCALE,
                defaultValue = 1f,
                buttonSize = 38.dp,
                gestureKey = targetIndex,
                modifier = Modifier.weight(1f),
                onDragStart = { onScrubberBusyChange(true) },
                onDragEnd = { onScrubberBusyChange(false) },
                onValueChange = { nextScale -> applyScale(nextScale) },
                onStepMinus = {
                    val stepped =
                        (((scaleVal - 0.05f) * 20f).roundToInt() / 20f).coerceIn(
                            SkinCustomization.MIN_CONTROL_SCALE,
                            SkinCustomization.MAX_CONTROL_SCALE,
                        )
                    applyScale(stepped)
                },
                onStepPlus = {
                    val stepped =
                        (((scaleVal + 0.05f) * 20f).roundToInt() / 20f).coerceIn(
                            SkinCustomization.MIN_CONTROL_SCALE,
                            SkinCustomization.MAX_CONTROL_SCALE,
                        )
                    applyScale(stepped)
                },
            )
            PercentageBadgePill(
                percent = scalePercent,
                changed = scaleChanged,
                onReset = {
                    val latest = customizationState.value
                    if (targetIndex in latest.scales) {
                        val nextScales = latest.scales - targetIndex
                        val curOff = latest.offsets[targetIndex]
                        val nextOffsets =
                            if (curOff != null && abs(curOff.x) <= 0.05f && abs(curOff.y) <= 0.05f) {
                                latest.offsets - targetIndex
                            } else {
                                latest.offsets
                            }
                        customizationState.value = latest.copy(scales = nextScales, offsets = nextOffsets)
                    }
                },
            )
        }
    }
}

/**
 * Minimal single-row zoom & re-center capsule when a game screen is selected:
 * `[ 1 | 2 ]?  [ Center ]  ( - )  ━━━━━━━●━━━━━━━  ( + )  [ 100% ]`
 */
@Composable
private fun SelectedScreenScaleBar(
    isLandscape: Boolean,
    screenIndex: Int,
    screenCount: Int,
    onSelectScreenIndex: (Int) -> Unit,
    customizationState: MutableState<SkinCustomization>,
    onScrubberBusyChange: (Boolean) -> Unit = {},
) {
    val targetScreenIndex = screenIndex
    val scaleVal by remember(customizationState, targetScreenIndex) {
        derivedStateOf { customizationState.value.screenScaleOf(targetScreenIndex) }
    }
    val offsetMoved by remember(customizationState, targetScreenIndex) {
        derivedStateOf {
            val o = customizationState.value.screenOffsetOf(targetScreenIndex)
            abs(o.x) > 0.001f || abs(o.y) > 0.001f
        }
    }
    val screenChanged by remember(customizationState, targetScreenIndex) {
        derivedStateOf { customizationState.value.hasScreenChangesAt(targetScreenIndex) }
    }
    val scalePercent = (scaleVal * 100).roundToInt()
    val capsuleShape = RoundedCornerShape(999.dp)

    fun applyScreenScale(nextScale: Float) {
        customizationState.value =
            customizationState.value.withScreenTransform(
                index = targetScreenIndex,
                scale = nextScale,
            )
    }

    Surface(
        modifier =
            Modifier
                .widthIn(max = if (isLandscape) 430.dp else 390.dp)
                .fillMaxWidth()
                .border(1.dp, ArcaduneColors.GlassEdge, capsuleShape),
        shape = capsuleShape,
        color = StudioGlass,
        contentColor = ArcaduneColors.OnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (screenCount > 1) {
                Row(
                    modifier =
                        Modifier
                            .height(38.dp)
                            .clip(capsuleShape)
                            .background(ArcaduneColors.SurfaceHigh)
                            .border(1.dp, ArcaduneColors.GlassEdge, capsuleShape)
                            .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (idx in 0 until screenCount) {
                        val active = idx == targetScreenIndex
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 28.dp, height = 32.dp)
                                    .studioTap(onClick = { onSelectScreenIndex(idx) })
                                    .clip(capsuleShape)
                                    .background(if (active) ArcaduneColors.Purple else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                    ),
                                color = if (active) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            StudioIconButton(
                icon = Icons.Filled.CenterFocusStrong,
                contentDescription = stringResource(R.string.skin_editor_reset_screen_pos),
                enabled = offsetMoved,
                onClick = {
                    customizationState.value =
                        customizationState.value.withScreenTransform(
                            index = targetScreenIndex,
                            offset = Offset.Zero,
                        )
                },
                size = 38.dp,
                iconSize = 19.dp,
            )
            ArcadunePrecisionScrubber(
                value = scaleVal,
                valueRange = SkinCustomization.MIN_SCREEN_SCALE..SkinCustomization.MAX_SCREEN_SCALE,
                defaultValue = 1f,
                buttonSize = 38.dp,
                gestureKey = targetScreenIndex,
                modifier = Modifier.weight(1f),
                onDragStart = { onScrubberBusyChange(true) },
                onDragEnd = { onScrubberBusyChange(false) },
                onValueChange = { nextScale -> applyScreenScale(nextScale) },
                onStepMinus = {
                    val stepped =
                        (((scaleVal - 0.05f) * 20f).roundToInt() / 20f).coerceIn(
                            SkinCustomization.MIN_SCREEN_SCALE,
                            SkinCustomization.MAX_SCREEN_SCALE,
                        )
                    applyScreenScale(stepped)
                },
                onStepPlus = {
                    val stepped =
                        (((scaleVal + 0.05f) * 20f).roundToInt() / 20f).coerceIn(
                            SkinCustomization.MIN_SCREEN_SCALE,
                            SkinCustomization.MAX_SCREEN_SCALE,
                        )
                    applyScreenScale(stepped)
                },
            )
            PercentageBadgePill(
                percent = scalePercent,
                changed = screenChanged,
                onReset = {
                    customizationState.value = customizationState.value.resetScreen(targetScreenIndex)
                },
            )
        }
    }
}

/**
 * Minimal single-row Portrait BG height capsule when the Portrait console shell is selected:
 * `[ HeightIcon ]  ( - )  ━━━━━━━●━━━━━━━  ( + )  [ 48% ]`
 */
@Composable
private fun SelectedBgHeightBar(
    bodyTop: Float,
    defaultBodyTop: Float,
    minBodyTop: Float,
    maxBodyTop: Float,
    stageHeightPx: Float,
    customizationState: MutableState<SkinCustomization>,
    onScrubberBusyChange: (Boolean) -> Unit = {},
) {
    val sliderVal = bgHeightSliderFraction(bodyTop, minBodyTop, maxBodyTop)
    val defaultSliderVal = bgHeightSliderFraction(defaultBodyTop, minBodyTop, maxBodyTop)
    val bgChanged by remember(customizationState) {
        derivedStateOf { customizationState.value.hasPortraitBgChange }
    }
    val heightPercent =
        (((stageHeightPx - bodyTop) / stageHeightPx.coerceAtLeast(1f)) * 100f)
            .roundToInt()
            .coerceIn(1, 100)
    val capsuleShape = RoundedCornerShape(999.dp)

    fun applyFraction(nextFraction: Float) {
        val nextOffset =
            sliderFractionToPortraitBgOffset(
                fraction = nextFraction,
                defaultBodyTop = defaultBodyTop,
                minBodyTop = minBodyTop,
                maxBodyTop = maxBodyTop,
            )
        customizationState.value = customizationState.value.withPortraitBgOffset(nextOffset)
    }

    Surface(
        modifier =
            Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .border(1.dp, ArcaduneColors.GlassEdge, capsuleShape),
        shape = capsuleShape,
        color = StudioGlass,
        contentColor = ArcaduneColors.OnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StudioIconButton(
                icon = Icons.Filled.Height,
                contentDescription = stringResource(R.string.skin_editor_bg_height),
                enabled = bgChanged,
                onClick = { customizationState.value = customizationState.value.resetPortraitBg() },
                size = 38.dp,
                iconSize = 19.dp,
            )
            ArcadunePrecisionScrubber(
                value = sliderVal,
                valueRange = 0f..1f,
                defaultValue = defaultSliderVal,
                buttonSize = 38.dp,
                gestureKey = "portrait_bg_bar",
                modifier = Modifier.weight(1f),
                onDragStart = { onScrubberBusyChange(true) },
                onDragEnd = { onScrubberBusyChange(false) },
                onValueChange = { nextFrac -> applyFraction(nextFrac) },
                onStepMinus = { applyFraction((sliderVal - 0.06f).coerceIn(0f, 1f)) },
                onStepPlus = { applyFraction((sliderVal + 0.06f).coerceIn(0f, 1f)) },
            )
            PercentageBadgePill(
                percent = heightPercent,
                changed = bgChanged,
                onReset = { customizationState.value = customizationState.value.resetPortraitBg() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlobalControllerOptionsCard(
    layout: SkinLayout,
    rewindAvailable: Boolean,
    fastForwardSupported: Boolean,
    customizationState: MutableState<SkinCustomization>,
    variant: SkinVariant,
    isLandscape: Boolean,
    editableBodyTop: Float? = null,
    defaultBodyTop: Float? = null,
    minBodyTop: Float = 0f,
    maxBodyTop: Float = 0f,
    stageHeightPx: Float = 1f,
    onScrubberBusyChange: (Boolean) -> Unit = {},
    onClose: () -> Unit,
    onVariantChange: (SkinVariant) -> Unit,
    controllerOptions: List<ControllerConfig> = emptyList(),
    selectedController: ControllerConfig? = null,
    onSelectController: ((ControllerConfig) -> Unit)? = null,
) {
    val sizeVal by remember(customizationState) { derivedStateOf { customizationState.value.size } }
    val opacityVal by remember(customizationState) { derivedStateOf { customizationState.value.opacity } }
    val customization = customizationState.value
    val toggleableButtons =
        remember(layout, rewindAvailable, fastForwardSupported, customization.hidden, customization.shown) {
            layout.controls.withIndex().filter { (idx, ctrl) ->
                customization.isControlAvailable(ctrl, rewindAvailable, fastForwardSupported) &&
                    (ctrl.defaultHidden || customization.isHidden(idx, ctrl))
            }
        }
    val cardShape = RoundedCornerShape(24.dp)

    Surface(
        modifier =
            Modifier
                .widthIn(max = if (isLandscape) 430.dp else 390.dp)
                .fillMaxWidth()
                .border(1.dp, ArcaduneColors.GlassEdge, cardShape),
        shape = cardShape,
        color = StudioGlass,
        contentColor = ArcaduneColors.OnSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .heightIn(max = if (isLandscape) 290.dp else 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkinVariantSegmentedBar(
                    variant = variant,
                    onVariantChange = onVariantChange,
                )
                Spacer(modifier = Modifier.weight(1f))
                StudioIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.skin_editor_deselect),
                    onClick = onClose,
                    size = 38.dp,
                    iconSize = 19.dp,
                )
            }

            if (controllerOptions.size > 1 && selectedController != null && onSelectController != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.skin_editor_controller_type),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ArcaduneColors.OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    ControllerTypeSegmentedBar(
                        options = controllerOptions,
                        selected = selectedController,
                        onSelect = { next ->
                            if (next.name != selectedController.name) {
                                onSelectController(next)
                            }
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val sizeChanged = abs(sizeVal - 1f) > 0.005f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.skin_editor_size),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ArcaduneColors.OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    PercentageBadgePill(
                        percent = (sizeVal * 100).roundToInt(),
                        changed = sizeChanged,
                        onReset = {
                            customizationState.value = customizationState.value.copy(size = 1f)
                        },
                    )
                }
                ArcadunePrecisionScrubber(
                    value = sizeVal,
                    valueRange = SkinCustomization.MIN_SIZE..SkinCustomization.MAX_SIZE,
                    defaultValue = 1f,
                    buttonSize = 38.dp,
                    gestureKey = "global_size",
                    onDragStart = { onScrubberBusyChange(true) },
                    onDragEnd = { onScrubberBusyChange(false) },
                    onValueChange = { nextSize ->
                        customizationState.value = customizationState.value.copy(size = nextSize)
                    },
                    onStepMinus = {
                        val stepped =
                            (((sizeVal - 0.05f) * 20f).roundToInt() / 20f).coerceIn(
                                SkinCustomization.MIN_SIZE,
                                SkinCustomization.MAX_SIZE,
                            )
                        customizationState.value = customizationState.value.copy(size = stepped)
                    },
                    onStepPlus = {
                        val stepped =
                            (((sizeVal + 0.05f) * 20f).roundToInt() / 20f).coerceIn(
                                SkinCustomization.MIN_SIZE,
                                SkinCustomization.MAX_SIZE,
                            )
                        customizationState.value = customizationState.value.copy(size = stepped)
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val opacityChanged = abs(opacityVal - 1f) > 0.005f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.skin_editor_opacity),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ArcaduneColors.OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    PercentageBadgePill(
                        percent = (opacityVal * 100).roundToInt(),
                        changed = opacityChanged,
                        onReset = {
                            customizationState.value = customizationState.value.copy(opacity = 1f)
                        },
                    )
                }
                ArcadunePrecisionScrubber(
                    value = opacityVal,
                    valueRange = SkinCustomization.MIN_OPACITY..1f,
                    defaultValue = 1f,
                    buttonSize = 38.dp,
                    gestureKey = "global_opacity",
                    onDragStart = { onScrubberBusyChange(true) },
                    onDragEnd = { onScrubberBusyChange(false) },
                    onValueChange = { nextOpacity ->
                        customizationState.value = customizationState.value.copy(opacity = nextOpacity)
                    },
                    onStepMinus = {
                        val stepped =
                            (((opacityVal - 0.05f) * 20f).roundToInt() / 20f).coerceIn(
                                SkinCustomization.MIN_OPACITY,
                                1f,
                            )
                        customizationState.value = customizationState.value.copy(opacity = stepped)
                    },
                    onStepPlus = {
                        val stepped =
                            (((opacityVal + 0.05f) * 20f).roundToInt() / 20f).coerceIn(
                                SkinCustomization.MIN_OPACITY,
                                1f,
                            )
                        customizationState.value = customizationState.value.copy(opacity = stepped)
                    },
                )
            }

            if (!isLandscape && editableBodyTop != null && defaultBodyTop != null) {
                val bgSliderVal = bgHeightSliderFraction(editableBodyTop, minBodyTop, maxBodyTop)
                val bgDefaultVal = bgHeightSliderFraction(defaultBodyTop, minBodyTop, maxBodyTop)
                val bgChanged = customization.hasPortraitBgChange
                val bgPercent =
                    (((stageHeightPx - editableBodyTop) / stageHeightPx.coerceAtLeast(1f)) * 100f)
                        .roundToInt()
                        .coerceIn(1, 100)

                fun applyBgFraction(nextFrac: Float) {
                    val nextOff =
                        sliderFractionToPortraitBgOffset(
                            fraction = nextFrac,
                            defaultBodyTop = defaultBodyTop,
                            minBodyTop = minBodyTop,
                            maxBodyTop = maxBodyTop,
                        )
                    customizationState.value = customizationState.value.withPortraitBgOffset(nextOff)
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.skin_editor_bg_height),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = ArcaduneColors.OnSurface,
                            modifier = Modifier.weight(1f),
                        )
                        PercentageBadgePill(
                            percent = bgPercent,
                            changed = bgChanged,
                            onReset = {
                                customizationState.value = customizationState.value.resetPortraitBg()
                            },
                        )
                    }
                    ArcadunePrecisionScrubber(
                        value = bgSliderVal,
                        valueRange = 0f..1f,
                        defaultValue = bgDefaultVal,
                        buttonSize = 38.dp,
                        gestureKey = "global_portrait_bg",
                        onDragStart = { onScrubberBusyChange(true) },
                        onDragEnd = { onScrubberBusyChange(false) },
                        onValueChange = { nextFrac -> applyBgFraction(nextFrac) },
                        onStepMinus = { applyBgFraction((bgSliderVal - 0.06f).coerceIn(0f, 1f)) },
                        onStepPlus = { applyBgFraction((bgSliderVal + 0.06f).coerceIn(0f, 1f)) },
                    )
                }
            }

            if (toggleableButtons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.skin_editor_buttons),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = ArcaduneColors.OnSurface,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        toggleableButtons.forEach { (idx, ctrl) ->
                            val isVisible = !customization.isHidden(idx, ctrl)
                            ButtonVisibilityChip(
                                label = ctrl.displayName(idx),
                                visible = isVisible,
                                onToggle = {
                                    val latest = customizationState.value
                                    val nextVisible = latest.isHidden(idx, ctrl)
                                    customizationState.value = latest.setControlVisible(idx, ctrl, nextVisible)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ButtonVisibilityChip(
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
) {
    val chipShape = RoundedCornerShape(999.dp)
    val bg = if (visible) ArcaduneColors.Purple.copy(alpha = 0.24f) else ArcaduneColors.SurfaceHigh
    val border = if (visible) ArcaduneColors.Purple.copy(alpha = 0.78f) else ArcaduneColors.GlassEdge
    val fg = if (visible) ArcaduneColors.Purple else ArcaduneColors.OnSurfaceDim
    Row(
        modifier =
            Modifier
                .height(34.dp)
                .studioTap(onClick = onToggle)
                .clip(chipShape)
                .background(bg)
                .border(1.dp, border, chipShape)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!visible) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (visible) FontWeight.SemiBold else FontWeight.Medium,
                ),
            color = fg,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun PercentageBadgePill(
    percent: Int,
    changed: Boolean,
    onReset: () -> Unit,
) {
    val badgeShape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            Modifier
                .widthIn(min = 58.dp)
                .height(36.dp)
                .studioTap(enabled = changed, onClick = onReset)
                .clip(badgeShape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(
                    1.dp,
                    if (changed) ArcaduneColors.Purple.copy(alpha = 0.60f) else ArcaduneColors.GlassEdge,
                    badgeShape,
                )
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (changed) ArcaduneColors.Purple else ArcaduneColors.OnSurfaceDim,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun StudioIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    showBadgeDot: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 21.dp,
) {
    val bg = if (active) ArcaduneColors.Purple.copy(alpha = 0.26f) else ArcaduneColors.SurfaceHigh
    val border = if (active) ArcaduneColors.Purple.copy(alpha = 0.78f) else ArcaduneColors.GlassEdge
    val tint = if (active) ArcaduneColors.Purple else ArcaduneColors.OnSurface
    Box(
        modifier =
            Modifier
                .size(size)
                .alpha(if (enabled) 1f else 0.36f)
                .studioTap(enabled = enabled, scaleDown = 0.91f, onClick = onClick)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        if (showBadgeDot) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(ArcaduneColors.Purple),
            )
        }
    }
}

@Composable
private fun StudioActionPill(
    label: String,
    icon: ImageVector?,
    highlighted: Boolean,
    enabled: Boolean,
    height: Dp,
    horizontalPadding: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    val fg = if (highlighted) ArcaduneColors.OnAccent else ArcaduneColors.OnSurface
    Row(
        modifier =
            Modifier
                .height(height)
                .alpha(if (enabled) 1f else 0.38f)
                .studioTap(enabled = enabled, onClick = onClick)
                .clip(shape)
                .background(if (highlighted) ArcaduneColors.Purple else ArcaduneColors.SurfaceHigh)
                .then(
                    if (highlighted) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, ArcaduneColors.GlassEdge, shape)
                    },
                )
                .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Large, thumb-friendly precision scrubber: 10dp capsule track, 22dp glowing circular thumb,
 * optional reference tick at [defaultValue], and instant -/+ steppers.
 */
@Composable
internal fun ArcadunePrecisionScrubber(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    defaultValue: Float? = null,
    buttonSize: Dp = 40.dp,
    gestureKey: Any? = Unit,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onStepMinus: (() -> Unit)? = null,
    onStepPlus: (() -> Unit)? = null,
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 1600f),
        label = "scrubberThumb",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onStepMinus != null) {
            StudioIconButton(
                icon = Icons.Filled.Remove,
                contentDescription = null,
                enabled = value > valueRange.start + 0.005f,
                onClick = onStepMinus,
                size = buttonSize,
                iconSize = 20.dp,
            )
        }

        val accent = ArcaduneColors.Purple
        val onSurface = ArcaduneColors.OnSurface
        Canvas(
            modifier =
                Modifier
                    .weight(1f)
                    .height(buttonSize)
                    .pointerInput(valueRange, defaultValue, gestureKey) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                            down.consume()
                            currentOnDragStart?.invoke()
                            dragging = true
                            val padPx = 12.dp.toPx()
                            val usable = (size.width - padPx * 2f).coerceAtLeast(1f)

                            fun applyX(x: Float) {
                                val f = ((x - padPx) / usable).coerceIn(0f, 1f)
                                var raw = valueRange.start + f * span
                                if (defaultValue != null && abs(raw - defaultValue) <= span * 0.022f) {
                                    raw = defaultValue
                                }
                                currentOnValueChange(raw.coerceIn(valueRange.start, valueRange.endInclusive))
                            }

                            try {
                                applyX(down.position.x)
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    applyX(change.position.x)
                                }
                            } finally {
                                dragging = false
                                currentOnDragEnd?.invoke()
                            }
                        }
                    },
        ) {
            val padPx = 12.dp.toPx()
            val usable = (size.width - padPx * 2f).coerceAtLeast(1f)
            val trackH = 9.dp.toPx()
            val cy = size.height / 2f
            val corner = CornerRadius(trackH / 2f)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.16f),
                topLeft = Offset(padPx, cy - trackH / 2f),
                size = Size(usable, trackH),
                cornerRadius = corner,
            )

            if (defaultValue != null && defaultValue in valueRange) {
                val defFrac = ((defaultValue - valueRange.start) / span).coerceIn(0f, 1f)
                val tickX = padPx + usable * defFrac
                drawLine(
                    color = Color.White.copy(alpha = 0.50f),
                    start = Offset(tickX, cy - 8.dp.toPx()),
                    end = Offset(tickX, cy + 8.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            val thumbX = padPx + usable * fraction
            if (thumbX > padPx) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(padPx, cy - trackH / 2f),
                    size = Size(thumbX - padPx, trackH),
                    cornerRadius = corner,
                )
            }

            val outerRadius = 11.dp.toPx() * thumbScale
            drawCircle(
                color = Color.Black.copy(alpha = 0.50f),
                radius = outerRadius + 2.dp.toPx(),
                center = Offset(thumbX, cy),
            )
            drawCircle(
                color = accent,
                radius = outerRadius,
                center = Offset(thumbX, cy),
            )
            drawCircle(
                color = onSurface,
                radius = outerRadius * 0.40f,
                center = Offset(thumbX, cy),
            )
        }

        if (onStepPlus != null) {
            StudioIconButton(
                icon = Icons.Filled.Add,
                contentDescription = null,
                enabled = value < valueRange.endInclusive - 0.005f,
                onClick = onStepPlus,
                size = buttonSize,
                iconSize = 20.dp,
            )
        }
    }
}

@Composable
private fun SkinVariantSegmentedBar(
    variant: SkinVariant,
    onVariantChange: (SkinVariant) -> Unit,
) {
    val trackShape = RoundedCornerShape(999.dp)
    Row(
        modifier =
            Modifier
                .height(38.dp)
                .clip(trackShape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(1.dp, ArcaduneColors.GlassEdge, trackShape)
                .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val options =
            listOf(
                SkinVariant.CONSOLE to R.string.skin_variant_console,
                SkinVariant.MINIMAL to R.string.skin_variant_minimal,
            )
        options.forEach { (option, label) ->
            val selected = option == variant
            Box(
                modifier =
                    Modifier
                        .height(32.dp)
                        .studioTap(onClick = { onVariantChange(option) })
                        .clip(trackShape)
                        .background(if (selected) ArcaduneColors.Purple else Color.Transparent)
                        .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(label),
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = if (selected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim,
                )
            }
        }
    }
}

@Composable
private fun ControllerTypeSegmentedBar(
    options: List<ControllerConfig>,
    selected: ControllerConfig,
    onSelect: (ControllerConfig) -> Unit,
) {
    val trackShape = RoundedCornerShape(999.dp)
    Row(
        modifier =
            Modifier
                .height(38.dp)
                .clip(trackShape)
                .background(ArcaduneColors.SurfaceHigh)
                .border(1.dp, ArcaduneColors.GlassEdge, trackShape)
                .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option.name == selected.name
            Box(
                modifier =
                    Modifier
                        .height(32.dp)
                        .studioTap(onClick = { onSelect(option) })
                        .clip(trackShape)
                        .background(if (isSelected) ArcaduneColors.Purple else Color.Transparent)
                        .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(option.displayName),
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = if (isSelected) ArcaduneColors.OnAccent else ArcaduneColors.OnSurfaceDim,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Direct bounds-aware 1-touch button handler for Studio HUD controls:
 * - Zero gesture-disambiguation delay (`down` consumed immediately).
 * - Forgives natural thumb roll/pivot up to 16dp outside button edges without cancelling clicks.
 * - Consumes touches even when disabled so HUD taps never fall through onto canvas buttons.
 */
@Composable
private fun Modifier.studioTap(
    enabled: Boolean = true,
    scaleDown: Float = 0.94f,
    onClick: () -> Unit,
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 1800f),
        label = "studioTapScale",
    )
    return this
        .semantics {
            role = Role.Button
            if (enabled) {
                onClick {
                    currentOnClick()
                    true
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            val slopMargin = 16.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                down.consume()
                if (!currentEnabled) {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                    }
                    return@awaitEachGesture
                }
                pressed = true
                var inside = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        val pos = change.position
                        val inX = pos.x >= -slopMargin && pos.x <= size.width + slopMargin
                        val inY = pos.y >= -slopMargin && pos.y <= size.height + slopMargin
                        inside = inX && inY
                        pressed = inside && change.pressed
                        if (!change.pressed) {
                            if (inside) {
                                currentHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnClick()
                            }
                            break
                        }
                    }
                } finally {
                    pressed = false
                }
            }
        }
}
