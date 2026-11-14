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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.touchinput.skin.ArcaduneSkin
import com.swordfish.touchinput.skin.ArcaduneSkinEditor
import com.swordfish.touchinput.skin.SkinArt
import com.swordfish.touchinput.skin.SkinControl
import com.swordfish.touchinput.skin.SkinCustomization
import com.swordfish.touchinput.skin.SkinLayout
import com.swordfish.touchinput.skin.SkinVariant
import com.swordfish.touchinput.skin.clampControlOffset
import com.swordfish.touchinput.skin.signature
import com.swordfish.touchinput.skin.unitsAbove
import kotlin.math.abs
import kotlin.math.roundToInt

/** Saves the player's skin changes, one set per controller and orientation. */
class SkinCustomizationStore(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    fun load(key: String): SkinCustomization =
        SkinCustomization.decode(preferences.getString(prefKey(key), null)).copy(hidden = emptySet())

    fun save(
        key: String,
        customization: SkinCustomization,
    ) = preferences.edit { putString(prefKey(key), customization.copy(hidden = emptySet()).encode()) }

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
        customizationState = customizationState,
        panel = panel,
        shell = hasShell,
        variant = variant,
        onVariantChange = onVariantChange,
        onResetAll = onReset,
        onDone = onDone,
        art = art,
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
) {
    BackHandler(onBack = onDone)
    val layout = if (isLandscape) skin.landscape else skin.portrait

    var selectedIndex by rememberSaveable(isLandscape) { mutableStateOf<Int?>(null) }
    var globalOpen by rememberSaveable(isLandscape) { mutableStateOf(false) }
    var canvasBusy by remember { mutableStateOf(false) }
    var editorSurfaceDp by remember(layout, isLandscape) {
        mutableStateOf(Offset(layout.width, layout.height))
    }

    val editableCount = layout.controls.size
    LaunchedEffect(layout, rewindAvailable, selectedIndex) {
        val idx = selectedIndex ?: return@LaunchedEffect
        val ctrl = layout.controls.getOrNull(idx)
        if (idx !in 0 until editableCount || (ctrl is SkinControl.Rewind && !rewindAvailable)) {
            selectedIndex = null
        }
    }

    val canReset by remember(customizationState) {
        derivedStateOf { customizationState.value.isCustomized }
    }
    val hasGlobalChanges by remember(customizationState) {
        derivedStateOf {
            val c = customizationState.value
            abs(c.size - 1f) > 0.005f || abs(c.opacity - 1f) > 0.005f
        }
    }

    val hudAlpha by animateFloatAsState(
        targetValue = if (canvasBusy) 0.18f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "studioHudAlpha",
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenW = maxWidth
        val buttonHeight = if (screenW >= 400.dp) 44.dp else 42.dp
        val approxScale =
            remember(layout, maxWidth, maxHeight) {
                minOf(maxWidth.value / layout.width, maxHeight.value / layout.height)
            }
        val hudTopPadding =
            remember(isLandscape, layout, approxScale, rewindAvailable) {
                if (!isLandscape) {
                    8.dp
                } else {
                    val midLeft = layout.width * 0.25f
                    val midRight = layout.width * 0.75f
                    val topClearUnits =
                        layout.controls
                            .filter { rewindAvailable || it !is SkinControl.Rewind }
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
            layout = layout,
            rewindAvailable = rewindAvailable,
            customizationState = customizationState,
            panel = panel,
            shell = shell,
            art = art,
            selectedIndex = selectedIndex,
            onSelect = { idx ->
                selectedIndex = idx
                if (idx != null) globalOpen = false
            },
            onSurfaceMeasured = { editorSurfaceDp = it },
            onCanvasBusyChange = { canvasBusy = it },
        )

        val outerHorizontalPad = if (screenW < 380.dp) 8.dp else 12.dp
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        start = outerHorizontalPad,
                        end = outerHorizontalPad,
                        top = hudTopPadding,
                        bottom = 8.dp,
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
                            globalOpen = false
                            callback(nextLandscape)
                        }
                    }
                }

            val performResetAll = {
                selectedIndex = null
                globalOpen = false
                if (customizationState.value.isCustomized) {
                    onResetAll()
                }
            }

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
                        globalOpen = true
                    }
                },
                onDone = onDone,
            )

            val activeIdx = selectedIndex
            val activeControl = activeIdx?.let { layout.controls.getOrNull(it) }
            if (globalOpen) {
                GlobalControllerOptionsCard(
                    customizationState = customizationState,
                    variant = variant,
                    isLandscape = isLandscape,
                    onClose = { globalOpen = false },
                    onVariantChange = { nextVariant ->
                        if (nextVariant != variant) {
                            onVariantChange(nextVariant)
                        }
                    },
                )
            } else if (activeIdx != null && activeControl != null) {
                SelectedButtonScaleBar(
                    index = activeIdx,
                    isLandscape = isLandscape,
                    layout = layout,
                    editorSurfaceDp = editorSurfaceDp,
                    customizationState = customizationState,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.StudioCanvasLayer(
    isLandscape: Boolean,
    layout: SkinLayout,
    rewindAvailable: Boolean,
    customizationState: MutableState<SkinCustomization>,
    panel: Rect?,
    shell: Boolean,
    art: SkinArt?,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onSurfaceMeasured: (Offset) -> Unit,
    onCanvasBusyChange: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val customization = customizationState.value

    if (isLandscape) {
        ArcaduneSkinEditor(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(4.dp)
                    .onSizeChanged { size ->
                        with(density) {
                            onSurfaceMeasured(Offset(size.width.toDp().value, size.height.toDp().value))
                        }
                    },
            layout = layout,
            pinToEdges = true,
            rewindAvailable = rewindAvailable,
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
        )
    } else {
        val above = if (panel != null) layout.unitsAbove() * panel.width / layout.width else 0f
        val aboveDp = with(density) { above.toDp() }
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
                editorModifier.onSizeChanged { size ->
                    with(density) {
                        onSurfaceMeasured(
                            Offset(
                                size.width.toDp().value,
                                (size.height.toDp() - aboveDp).value.coerceAtLeast(1f),
                            ),
                        )
                    }
                },
            layout = layout,
            pinToEdges = false,
            rewindAvailable = rewindAvailable,
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
        )
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
 * Ultra-minimal single-row scale capsule for a selected button:
 * `( - )  ━━━━━━━●━━━━━━━  ( + )  [ 100% ]`
 */
@Composable
private fun SelectedButtonScaleBar(
    index: Int,
    isLandscape: Boolean,
    layout: SkinLayout,
    editorSurfaceDp: Offset,
    customizationState: MutableState<SkinCustomization>,
) {
    val scaleVal by remember(customizationState, index) {
        derivedStateOf { customizationState.value.controlScale(index) }
    }
    val scalePercent = (scaleVal * 100).roundToInt()
    val scaleChanged = abs(scaleVal - 1f) > 0.01f
    val capsuleShape = RoundedCornerShape(999.dp)

    fun applyScale(nextScale: Float) {
        val latest = customizationState.value
        val scaled = latest.copy(scales = latest.scales + (index to nextScale))
        val curOff = scaled.offsets[index] ?: Offset.Zero
        val clamped =
            layout.clampControlOffset(
                pinToEdges = isLandscape,
                surfaceWidthDp = editorSurfaceDp.x,
                surfaceHeightDp = editorSurfaceDp.y,
                index = index,
                customization = scaled,
                candidate = curOff,
                snapToDefault = false,
            )
        customizationState.value = scaled.copy(offsets = scaled.offsets + (index to clamped))
    }

    Surface(
        modifier =
            Modifier
                .widthIn(max = if (isLandscape) 420.dp else 380.dp)
                .fillMaxWidth()
                .border(1.dp, ArcaduneColors.GlassEdge, capsuleShape),
        shape = capsuleShape,
        color = StudioGlass,
        contentColor = ArcaduneColors.OnSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArcadunePrecisionScrubber(
                value = scaleVal,
                valueRange = SkinCustomization.MIN_CONTROL_SCALE..SkinCustomization.MAX_CONTROL_SCALE,
                defaultValue = 1f,
                buttonSize = 38.dp,
                modifier = Modifier.weight(1f),
                onValueChange = ::applyScale,
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
                    if (index in latest.scales) {
                        customizationState.value = latest.copy(scales = latest.scales - index)
                    }
                },
            )
        }
    }
}

@Composable
private fun GlobalControllerOptionsCard(
    customizationState: MutableState<SkinCustomization>,
    variant: SkinVariant,
    isLandscape: Boolean,
    onClose: () -> Unit,
    onVariantChange: (SkinVariant) -> Unit,
) {
    val sizeVal by remember(customizationState) { derivedStateOf { customizationState.value.size } }
    val opacityVal by remember(customizationState) { derivedStateOf { customizationState.value.opacity } }
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
        }
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
    onDragStart: (() -> Unit)? = null,
    onStepMinus: (() -> Unit)? = null,
    onStepPlus: (() -> Unit)? = null,
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
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
                    .pointerInput(valueRange, defaultValue) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
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
