package com.swordfish.touchinput.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_FAST_FORWARD
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The player's changes to a skin: global size & opacity, screen scale & position, plus optional
 * per-control scale, offset (in skin units), hidden state, and enabled optional buttons.
 */
data class SkinCustomization(
    val size: Float = 1f,
    val offsets: Map<Int, Offset> = emptyMap(),
    /** How see-through the controls are: 1 is solid. */
    val opacity: Float = 1f,
    /** Individual scale multiplier for specific controls (1f = follows global [size]). */
    val scales: Map<Int, Float> = emptyMap(),
    /** Default-visible controls hidden by the player. */
    val hidden: Set<Int> = emptySet(),
    /** Default-hidden optional controls (Fast Forward, Exit) enabled by the player. */
    val shown: Set<Int> = emptySet(),
    /** Game screen zoom multiplier (1f = default fit). */
    val screenScale: Float = 1f,
    /** Game screen translation normalized by surface width & height (0,0 = default placement). */
    val screenOffset: Offset = Offset.Zero,
    /** Per-screen zoom multipliers for multi-screen consoles (DS / 3DS), indexed by window. */
    val screenScales: Map<Int, Float> = emptyMap(),
    /** Per-screen normalized translations for multi-screen consoles (DS / 3DS), indexed by window. */
    val screenOffsets: Map<Int, Offset> = emptyMap(),
    /**
     * Portrait console background height adjustment:
     * 0f = console default, +1f = full-screen top edge, -1f = compact minimum height.
     */
    val portraitBgOffset: Float = 0f,
) {
    fun controlScale(index: Int): Float = scales[index] ?: 1f

    fun effectiveScale(index: Int): Float = (size * controlScale(index)).coerceIn(0.45f, 2.0f)

    fun screenScaleOf(index: Int): Float = screenScales[index] ?: if (index == 0) screenScale else 1f

    fun screenOffsetOf(index: Int): Offset = screenOffsets[index] ?: if (index == 0) screenOffset else Offset.Zero

    fun hasScreenChangesAt(index: Int): Boolean {
        val s = screenScaleOf(index)
        val o = screenOffsetOf(index)
        return abs(s - 1f) > 0.005f || abs(o.x) > 0.001f || abs(o.y) > 0.001f
    }

    fun withScreenTransform(
        index: Int,
        scale: Float = screenScaleOf(index),
        offset: Offset = screenOffsetOf(index),
    ): SkinCustomization {
        val clampedScale = scale.coerceIn(MIN_SCREEN_SCALE, MAX_SCREEN_SCALE)
        val nextScales =
            if (abs(clampedScale - 1f) <= 0.001f) {
                screenScales - index
            } else {
                screenScales + (index to clampedScale)
            }
        val nextOffsets =
            if (abs(offset.x) <= 0.0005f && abs(offset.y) <= 0.0005f) {
                screenOffsets - index
            } else {
                screenOffsets + (index to offset)
            }
        return if (index == 0) {
            copy(
                screenScale = clampedScale,
                screenOffset = if (abs(offset.x) <= 0.0005f && abs(offset.y) <= 0.0005f) Offset.Zero else offset,
                screenScales = nextScales - 0,
                screenOffsets = nextOffsets - 0,
            )
        } else {
            copy(
                screenScales = nextScales,
                screenOffsets = nextOffsets,
            )
        }
    }

    fun isHidden(
        index: Int,
        control: SkinControl,
    ): Boolean = if (control.defaultHidden) index !in shown else index in hidden

    fun isControlAvailable(
        control: SkinControl,
        rewindAvailable: Boolean,
        fastForwardSupported: Boolean = true,
    ): Boolean =
        when {
            control is SkinControl.Rewind -> rewindAvailable
            control is SkinControl.Button && control.keyCode == KEYCODE_FAST_FORWARD -> fastForwardSupported
            else -> true
        }

    val hasScreenChanges: Boolean
        get() =
            abs(screenScale - 1f) > 0.005f ||
                abs(screenOffset.x) > 0.001f ||
                abs(screenOffset.y) > 0.001f ||
                screenScales.any { (_, s) -> abs(s - 1f) > 0.005f } ||
                screenOffsets.any { (_, o) -> abs(o.x) > 0.001f || abs(o.y) > 0.001f }

    fun resetScreen(index: Int? = null): SkinCustomization =
        when (index) {
            null ->
                copy(
                    screenScale = 1f,
                    screenOffset = Offset.Zero,
                    screenScales = emptyMap(),
                    screenOffsets = emptyMap(),
                )
            0 ->
                copy(
                    screenScale = 1f,
                    screenOffset = Offset.Zero,
                    screenScales = screenScales - 0,
                    screenOffsets = screenOffsets - 0,
                )
            else ->
                copy(
                    screenScales = screenScales - index,
                    screenOffsets = screenOffsets - index,
                )
        }

    val hasPortraitBgChange: Boolean
        get() = abs(portraitBgOffset) > 0.005f

    fun withPortraitBgOffset(offset: Float): SkinCustomization {
        val clamped = offset.coerceIn(MIN_BG_OFFSET, MAX_BG_OFFSET)
        return copy(portraitBgOffset = if (abs(clamped) <= 0.002f) 0f else clamped)
    }

    fun resetPortraitBg(): SkinCustomization = copy(portraitBgOffset = 0f)

    fun hasControlChanges(index: Int): Boolean =
        index in offsets || index in scales || index in hidden || index in shown

    val isCustomized: Boolean
        get() =
            abs(size - 1f) > 0.005f ||
                abs(opacity - 1f) > 0.005f ||
                offsets.any { (_, off) -> abs(off.x) > 0.05f || abs(off.y) > 0.05f } ||
                scales.any { (_, s) -> abs(s - 1f) > 0.005f } ||
                hidden.isNotEmpty() ||
                shown.isNotEmpty() ||
                hasScreenChanges ||
                hasPortraitBgChange

    fun resetControl(index: Int): SkinCustomization =
        copy(
            offsets = offsets - index,
            scales = scales - index,
            hidden = hidden - index,
            shown = shown - index,
        )

    fun setControlVisible(
        index: Int,
        control: SkinControl,
        visible: Boolean,
    ): SkinCustomization =
        if (control.defaultHidden) {
            copy(shown = if (visible) shown + index else shown - index)
        } else {
            copy(hidden = if (visible) hidden - index else hidden + index)
        }

    fun frameOf(
        index: Int,
        frame: SkinRect,
    ): SkinRect {
        val offset = offsets[index] ?: Offset.Zero
        val scale = effectiveScale(index)
        val width = frame.width * scale
        val height = frame.height * scale
        return SkinRect(
            frame.centerX - width / 2 + offset.x,
            frame.centerY - height / 2 + offset.y,
            width,
            height,
        )
    }

    fun encode(): String =
        buildString {
            append(size)
            if (opacity != 1f) append(";o=$opacity")
            if (abs(screenScale - 1f) > 0.001f) append(";ss=$screenScale")
            if (abs(screenOffset.x) > 0.0005f || abs(screenOffset.y) > 0.0005f) {
                append(";so=${screenOffset.x}:${screenOffset.y}")
            }
            screenScales.toSortedMap().forEach { (idx, s) ->
                if (idx != 0 && abs(s - 1f) > 0.001f) append(";ss$idx=$s")
            }
            screenOffsets.toSortedMap().forEach { (idx, off) ->
                if (idx != 0 && (abs(off.x) > 0.0005f || abs(off.y) > 0.0005f)) {
                    append(";so$idx=${off.x}:${off.y}")
                }
            }
            if (abs(portraitBgOffset) > 0.001f) append(";bg=$portraitBgOffset")
            offsets.forEach { (index, offset) -> append(";$index:${offset.x}:${offset.y}") }
            scales.forEach { (index, s) -> if (s != 1f) append(";s$index=$s") }
            if (hidden.isNotEmpty()) append(";h=${hidden.sorted().joinToString(",")}")
            if (shown.isNotEmpty()) append(";v=${shown.sorted().joinToString(",")}")
        }

    companion object {
        const val MIN_SIZE = 0.75f
        const val MAX_SIZE = 1.35f
        const val MIN_OPACITY = 0.2f
        const val MIN_CONTROL_SCALE = 0.6f
        const val MAX_CONTROL_SCALE = 1.6f
        const val MIN_SCREEN_SCALE = 0.4f
        const val MAX_SCREEN_SCALE = 2.2f
        const val MIN_BG_OFFSET = -1f
        const val MAX_BG_OFFSET = 1f

        fun decode(text: String?): SkinCustomization {
            if (text.isNullOrBlank()) return SkinCustomization()
            return runCatching {
                val parts = text.split(';')
                var opacity = 1f
                var screenScale = 1f
                var screenOffset = Offset.Zero
                var portraitBgOffset = 0f
                val screenScales = mutableMapOf<Int, Float>()
                val screenOffsets = mutableMapOf<Int, Offset>()
                val offsets = mutableMapOf<Int, Offset>()
                val scales = mutableMapOf<Int, Float>()
                val hidden = mutableSetOf<Int>()
                val shown = mutableSetOf<Int>()
                for (part in parts.drop(1)) {
                    when {
                        part.startsWith("o=") -> {
                            opacity = part.removePrefix("o=").toFloat()
                        }
                        part.startsWith("bg=") -> {
                            val rawBg = part.removePrefix("bg=").toFloat().coerceIn(MIN_BG_OFFSET, MAX_BG_OFFSET)
                            portraitBgOffset = if (abs(rawBg) <= 0.002f) 0f else rawBg
                        }
                        part.startsWith("ss=") -> {
                            screenScale =
                                part.removePrefix("ss=").toFloat().coerceIn(MIN_SCREEN_SCALE, MAX_SCREEN_SCALE)
                        }
                        part.startsWith("so=") -> {
                            val (sx, sy) = part.removePrefix("so=").split(':', limit = 2)
                            screenOffset =
                                Offset(
                                    sx.toFloat().coerceIn(-1.5f, 1.5f),
                                    sy.toFloat().coerceIn(-1.5f, 1.5f),
                                )
                        }
                        part.startsWith("ss") && '=' in part -> {
                            val (idxStr, raw) = part.removePrefix("ss").split('=', limit = 2)
                            val idx = idxStr.toInt()
                            val parsed = raw.toFloat().coerceIn(MIN_SCREEN_SCALE, MAX_SCREEN_SCALE)
                            if (idx == 0) screenScale = parsed else screenScales[idx] = parsed
                        }
                        part.startsWith("so") && '=' in part -> {
                            val (idxStr, raw) = part.removePrefix("so").split('=', limit = 2)
                            val idx = idxStr.toInt()
                            val (sx, sy) = raw.split(':', limit = 2)
                            val parsed =
                                Offset(
                                    sx.toFloat().coerceIn(-1.5f, 1.5f),
                                    sy.toFloat().coerceIn(-1.5f, 1.5f),
                                )
                            if (idx == 0) screenOffset = parsed else screenOffsets[idx] = parsed
                        }
                        part.startsWith("h=") -> {
                            part.removePrefix("h=").split(',')
                                .filter { it.isNotBlank() }
                                .forEach { hidden += it.toInt() }
                        }
                        part.startsWith("v=") -> {
                            part.removePrefix("v=").split(',')
                                .filter { it.isNotBlank() }
                                .forEach { shown += it.toInt() }
                        }
                        part.startsWith("s") && '=' in part -> {
                            val (idx, raw) = part.removePrefix("s").split('=', limit = 2)
                            scales[idx.toInt()] = raw.toFloat().coerceIn(MIN_CONTROL_SCALE, MAX_CONTROL_SCALE)
                        }
                        ':' in part -> {
                            val (index, x, y) = part.split(':')
                            offsets[index.toInt()] = Offset(x.toFloat(), y.toFloat())
                        }
                    }
                }
                SkinCustomization(
                    size = parts.first().toFloat().coerceIn(MIN_SIZE, MAX_SIZE),
                    offsets = offsets,
                    opacity = opacity.coerceIn(MIN_OPACITY, 1f),
                    scales = scales,
                    hidden = hidden,
                    shown = shown,
                    screenScale = screenScale,
                    screenOffset = screenOffset,
                    screenScales = screenScales,
                    screenOffsets = screenOffsets,
                    portraitBgOffset = portraitBgOffset,
                )
            }.getOrDefault(SkinCustomization())
        }
    }
}

/** Human-readable label for a control in the button inspector. */
fun SkinControl.displayName(index: Int): String =
    when (this) {
        is SkinControl.Dpad ->
            when (look) {
                DpadLook.C_BUTTONS -> "C Buttons"
                DpadLook.BALL_TOP -> "Joystick"
                else -> "D-Pad"
            }
        is SkinControl.Stick ->
            when (source) {
                ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK -> "Left Stick"
                ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK -> "Right Stick"
                else -> "Stick"
            }
        is SkinControl.Menu -> "Menu"
        is SkinControl.Rewind -> "Rewind"
        is SkinControl.Button ->
            when {
                icon == SkinIcon.FAST_FORWARD -> "Fast Forward"
                icon == SkinIcon.EXIT -> "Exit"
                icon == SkinIcon.SWAP -> "Swap"
                icon == SkinIcon.QUICK_SAVE -> "Quick Save"
                icon == SkinIcon.QUICK_LOAD -> "Quick Load"
                label.isNotBlank() -> label
                else -> "Button ${index + 1}"
            }
    }

/**
 * Plays the game: draws [layout] and tells [input] where each control is, so it can turn the
 * fingers into presses (see [SkinInput]). Each control shows what [input] says about it.
 */
@Composable
fun ArcaduneSkinView(
    modifier: Modifier = Modifier,
    layout: SkinLayout,
    pinToEdges: Boolean,
    rewindAvailable: Boolean,
    customization: SkinCustomization,
    input: SkinInput,
    /** True when a [ConsoleShell] is drawn behind: the panel then has no background of its own. */
    shell: Boolean = false,
    /** The skin's designed pictures, when it has them (see [SkinArt]). */
    art: SkinArt? = null,
    fastForwardSupported: Boolean = true,
    /** Extra vertical room above the panel so portrait controls moved upward are never clipped. */
    topExtra: androidx.compose.ui.unit.Dp = 0.dp,
    /** Number of speed steps (>1x) available on this core, drawn as subtle LED dots on Fast Forward. */
    fastForwardDotsTotal: Int = 0,
    /** Number of currently lit speed dots (0 at 1x, 1 at 2x, 2 at 4x, 3 at 6x, 4 at 8x). */
    fastForwardDotsActive: Int = 0,
) {
    val origin = remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.onGloballyPositioned { origin.value = it.boundsInRoot().topLeft }) {
        val density = LocalDensity.current.density
        val surfaceHeightDp = (maxHeight - topExtra).value.coerceAtLeast(1f)
        val geometry =
            remember(layout, pinToEdges, maxWidth, surfaceHeightDp) {
                SkinGeometry(layout, pinToEdges, maxWidth.value, surfaceHeightDp)
            }
        val topLeft = origin.value
        // Where each control takes touches, in root pixels, for the touch tracker.
        val targets =
            remember(geometry, customization, rewindAvailable, fastForwardSupported, topLeft, density, topExtra) {
                geometry.touchRects(
                    customization,
                    rewindAvailable,
                    fastForwardSupported = fastForwardSupported,
                ).map { (index, rect) ->
                    val control = layout.controls[index]
                    SkinInput.Target(
                        index = index,
                        control = control,
                        area =
                            androidx.compose.ui.geometry.Rect(
                                topLeft.x + rect.left * density,
                                topLeft.y + (topExtra.value + rect.top) * density,
                                topLeft.x + rect.right * density,
                                topLeft.y + (topExtra.value + rect.bottom) * density,
                            ),
                        round = control.takesRoundTouches(),
                    )
                }
            }
        SideEffect { input.targets = targets }
        DisposableEffect(input) {
            onDispose {
                input.releaseAll()
                input.targets = emptyList()
            }
        }

        SkinFrame(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = customization.opacity }
                .padding(top = topExtra),
            layout,
            pinToEdges,
            rewindAvailable,
            customization,
            drawBody = !shell,
            art = art,
            fastForwardSupported = fastForwardSupported,
        ) { index, control, frame, placement, scale ->
            val inset = (control.reach * scale).dp
            Box(modifier = placement) {
                when (control) {
                    is SkinControl.Dpad -> {
                        val art = LocalSkinArt.current?.part(control, pressed = false)
                        val partScale = if (control.frame.width > 0f) frame.width / control.frame.width else 1f
                        val pad = ((control.reach + ART_BLEED) * partScale * scale).dp
                        Canvas(Modifier.fillMaxSize().padding(inset)) {
                            val direction = input.directions[index] ?: Offset.Zero
                            if (art != null) {
                                drawArtDpad(art, pad.toPx(), direction)
                            } else {
                                drawAnyDpad(layout, control.look, direction)
                            }
                        }
                    }

                    is SkinControl.Stick ->
                        Canvas(Modifier.fillMaxSize().padding(inset)) {
                            drawStickBase(layout)
                            // The cap follows the finger, up to a little under half the well.
                            val direction = input.directions[index] ?: Offset.Zero
                            val knob = size.minDimension * 0.62f
                            val travel = size.minDimension * 0.22f
                            translate(
                                (size.width - knob) / 2 + direction.x * travel,
                                (size.height - knob) / 2 - direction.y * travel,
                            ) {
                                drawStickKnob(layout, direction != Offset.Zero, knob)
                            }
                        }

                    else -> {
                        val pressed = remember(index) { derivedStateOf { input.pressed[index] == true } }
                        ControlGlyph(
                            layout = layout,
                            control = control,
                            frame = frame,
                            pressed = pressed,
                            inset = inset,
                            scale = scale,
                            pinned = pinToEdges,
                            fastForwardDotsTotal = fastForwardDotsTotal,
                            fastForwardDotsActive = fastForwardDotsActive,
                        )
                    }
                }
            }
        }
    }
}

/** Round controls take touches in a circle (ellipse) around them; pills and shoulders in their box. */
private fun SkinControl.takesRoundTouches(): Boolean =
    when (this) {
        is SkinControl.Button -> shape == ButtonShape.ROUND
        is SkinControl.Dpad, is SkinControl.Stick -> true
        is SkinControl.Menu, is SkinControl.Rewind -> false
    }

/** Result of snapping a dragged control to default origin, screen centerlines, or sibling axes. */
data class ControlSnapResult(
    val offset: Offset,
    val verticalGuideDp: Float? = null,
    val horizontalGuideDp: Float? = null,
    val snappedToDefault: Boolean = false,
    val alignedPeerCentersDp: List<Offset> = emptyList(),
)

private fun SkinControl.selectionCornerFraction(): Float =
    when (this) {
        is SkinControl.Button -> if (shape == ButtonShape.SHOULDER) 0.30f else 0.50f
        is SkinControl.Dpad ->
            if (look == DpadLook.CROSS || look == DpadLook.SEPARATE) 0.24f else 0.50f
        is SkinControl.Stick, is SkinControl.Menu, is SkinControl.Rewind -> 0.50f
    }

/**
 * Direct-manipulation controller layout studio canvas:
 * - Renders artwork cleanly at rest (no permanent wireframe boxes cluttering unselected controls).
 * - Tap any control to select it (showing a dual-contrast shape-matched halo).
 * - Drag any control with one finger to move it (works cleanly even on small buttons), or pinch
 *   with two fingers anywhere on the canvas to resize the selected control (or all controls).
 */
@Composable
fun ArcaduneSkinEditor(
    modifier: Modifier = Modifier,
    layout: SkinLayout,
    pinToEdges: Boolean,
    rewindAvailable: Boolean,
    customization: SkinCustomization,
    onMove: ((Int, Offset) -> Unit)? = null,
    onCustomizationChange: ((SkinCustomization) -> Unit)? = null,
    onGestureStart: (() -> Unit)? = null,
    snapEnabled: Boolean = true,
    /** True when a [ConsoleShell] is drawn behind: the panel then has no background of its own. */
    shell: Boolean = false,
    art: SkinArt? = null,
    /**
     * Room above the controls, inside this editor's own area, for the controls that sit above the
     * panel (the DS L and R tabs): a touch outside an element's bounds never reaches it, so without
     * this they could not be dragged. The caller makes the area this much taller and higher.
     */
    topExtra: androidx.compose.ui.unit.Dp = 0.dp,
    selectedIndex: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    accentColor: Color = Color(0xFFBB9CFF),
    onAccentColor: Color = Color(0xFF1E0F3D),
    onCanvasBusyChange: ((Boolean) -> Unit)? = null,
    fastForwardSupported: Boolean = true,
    fastForwardDotsTotal: Int = 4,
    fastForwardDotsActive: Int = 0,
    /** When non-null and true for a local touch point outside all controls, lets underlying screen manipulation handle it. */
    shouldPassthroughBackgroundTouch: ((Offset) -> Boolean)? = null,
    /** Fine-grained layer router: given local touch point + exact/halo control hit flags, true yields to Screen/BG layers. */
    shouldYieldToLowerLayer: ((Offset, Boolean, Boolean) -> Boolean)? = null,
    /** Invoked when a single tap lands on a passthrough background area while a control was selected. */
    onBackgroundTap: ((Offset) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val currentHaptics by rememberUpdatedState(haptics)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnCustomizationChange by rememberUpdatedState(onCustomizationChange)
    val currentOnGestureStart by rememberUpdatedState(onGestureStart)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnBusyChange by rememberUpdatedState(onCanvasBusyChange)
    val currentCustomization by rememberUpdatedState(customization)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentSnapEnabled by rememberUpdatedState(snapEnabled)
    val currentPassthrough by rememberUpdatedState(shouldPassthroughBackgroundTouch)
    val currentShouldYield by rememberUpdatedState(shouldYieldToLowerLayer)
    val currentOnBackgroundTap by rememberUpdatedState(onBackgroundTap)

    val notPressed = remember { mutableStateOf(false) }
    val activeDragIndex = remember { mutableStateOf<Int?>(null) }
    val activeSnap = remember { mutableStateOf<ControlSnapResult?>(null) }
    val liveBadge = remember { mutableStateOf<Pair<Int?, String>?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val totalHeightDp = maxHeight.value
        val surfaceWidthDp = maxWidth.value
        val surfaceHeightDp = (maxHeight - topExtra).value.coerceAtLeast(1f)
        val geometry =
            remember(layout, pinToEdges, surfaceWidthDp, surfaceHeightDp) {
                SkinGeometry(layout, pinToEdges, surfaceWidthDp, surfaceHeightDp)
            }
        val currentGeometry by rememberUpdatedState(geometry)
        val scale by rememberUpdatedState(geometry.scale)
        val effectiveUnitsAbove =
            remember(layout, topExtra, geometry.scale) {
                maxOf(layout.unitsAbove(), if (geometry.scale > 0f) topExtra.value / geometry.scale else 0f)
            }
        val currentUnitsAbove by rememberUpdatedState(effectiveUnitsAbove)

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(layout, pinToEdges, rewindAvailable, fastForwardSupported, topExtra) {
                        val dragSlopPx = 6.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                            val initialSelectedIndex = currentSelectedIndex
                            val initialCustomization = currentCustomization
                            val startDp =
                                Offset(
                                    down.position.x / density,
                                    down.position.y / density - topExtra.value,
                                )
                            val exactHitIndex =
                                currentGeometry.hitTestControl(
                                    pointDp = startDp,
                                    customization = initialCustomization,
                                    rewindAvailable = rewindAvailable,
                                    fastForwardSupported = fastForwardSupported,
                                    exactOnly = true,
                                )
                            val hitIndex =
                                exactHitIndex
                                    ?: currentGeometry.hitTestControl(
                                        pointDp = startDp,
                                        customization = initialCustomization,
                                        rewindAvailable = rewindAvailable,
                                        fastForwardSupported = fastForwardSupported,
                                        exactOnly = false,
                                    )
                            val yieldDecision =
                                currentShouldYield?.invoke(
                                    down.position,
                                    exactHitIndex != null,
                                    hitIndex != null,
                                )

                            if (yieldDecision == true ||
                                (
                                    yieldDecision == null &&
                                        hitIndex == null &&
                                        initialSelectedIndex == null &&
                                        currentPassthrough?.invoke(down.position) == true
                                )
                            ) {
                                return@awaitEachGesture
                            }

                            down.consume()

                            try {
                                var gestureNotified = false
                                var pinching = false
                                var pinchPair: Pair<Long, Long>? = null
                                var pinchTargetIdx: Int? = null
                                var pinchInitialSpan = 1f
                                var pinchInitialScale = 1f

                                var draggingIdx: Int? = null
                                var dragOriginPx = down.position
                                var dragStartOffset = Offset.Zero
                                var prevVGuide: Float? = null
                                var prevHGuide: Float? = null

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val pressed = event.changes.filter { it.pressed }
                                    event.changes.forEach { if (!it.isConsumed) it.consume() }
                                    if (pressed.isEmpty()) break

                                    if (pressed.size >= 2 && currentOnCustomizationChange != null) {
                                        val p0 = pressed[0]
                                        val p1 = pressed[1]
                                        val pair = p0.id.value to p1.id.value
                                        val span =
                                            (p0.position - p1.position)
                                                .getDistance()
                                                .coerceAtLeast(16f)
                                        if (!pinching) {
                                            if (draggingIdx != null) {
                                                currentOnCustomizationChange?.invoke(initialCustomization)
                                            }
                                            val p0Dp =
                                                Offset(
                                                    p0.position.x / density,
                                                    p0.position.y / density - topExtra.value,
                                                )
                                            val p1Dp =
                                                Offset(
                                                    p1.position.x / density,
                                                    p1.position.y / density - topExtra.value,
                                                )
                                            pinchTargetIdx =
                                                currentGeometry.resolvePinchTarget(
                                                    p0Dp = p0Dp,
                                                    p1Dp = p1Dp,
                                                    selectedIndex = initialSelectedIndex,
                                                    customization = initialCustomization,
                                                    rewindAvailable = rewindAvailable,
                                                    fastForwardSupported = fastForwardSupported,
                                                )
                                            if (pinchTargetIdx != null && !gestureNotified) {
                                                currentOnGestureStart?.invoke()
                                                gestureNotified = true
                                                currentOnBusyChange?.invoke(true)
                                            }
                                            pinching = true
                                            pinchPair = pair
                                            draggingIdx = null
                                            activeDragIndex.value = null
                                            activeSnap.value = null
                                            val tIdx = pinchTargetIdx
                                            if (tIdx != null) {
                                                if (tIdx != currentSelectedIndex) {
                                                    currentOnSelect?.invoke(tIdx)
                                                    currentHaptics.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove,
                                                    )
                                                }
                                                pinchInitialSpan = span
                                                pinchInitialScale = initialCustomization.controlScale(tIdx)
                                            }
                                        } else if (pinchPair != pair) {
                                            pinchPair = pair
                                            val tIdx = pinchTargetIdx
                                            if (tIdx != null) {
                                                pinchInitialSpan = span
                                                pinchInitialScale = currentCustomization.controlScale(tIdx)
                                            }
                                        } else {
                                            val tIdx = pinchTargetIdx
                                            if (tIdx != null) {
                                                val ratio = span / pinchInitialSpan
                                                val latest = currentCustomization
                                                val nextScale =
                                                    (pinchInitialScale * ratio).coerceIn(
                                                        SkinCustomization.MIN_CONTROL_SCALE,
                                                        SkinCustomization.MAX_CONTROL_SCALE,
                                                    )
                                                val scaled =
                                                    latest.copy(scales = latest.scales + (tIdx to nextScale))
                                                val curOff = scaled.offsets[tIdx] ?: Offset.Zero
                                                val clamped =
                                                    currentGeometry.clampOffset(
                                                        tIdx,
                                                        scaled,
                                                        curOff,
                                                        currentUnitsAbove,
                                                        snapToDefault = false,
                                                    )
                                                val nextOffsets =
                                                    if (tIdx in scaled.offsets ||
                                                        abs(clamped.x) > 0.01f ||
                                                        abs(clamped.y) > 0.01f
                                                    ) {
                                                        scaled.offsets + (tIdx to clamped)
                                                    } else {
                                                        scaled.offsets
                                                    }
                                                currentOnCustomizationChange?.invoke(
                                                    scaled.copy(offsets = nextOffsets),
                                                )
                                                liveBadge.value = tIdx to "${(nextScale * 100).roundToInt()}%"
                                            }
                                        }
                                    } else if (!pinching && hitIndex != null) {
                                        val primary = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
                                        val movedDist = (primary.position - down.position).getDistance()
                                        if (draggingIdx == null && movedDist > dragSlopPx) {
                                            if (!gestureNotified) {
                                                currentOnGestureStart?.invoke()
                                                gestureNotified = true
                                                currentOnBusyChange?.invoke(true)
                                            }
                                            if (hitIndex != currentSelectedIndex) {
                                                currentOnSelect?.invoke(hitIndex)
                                                currentHaptics.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                            }
                                            draggingIdx = hitIndex
                                            activeDragIndex.value = hitIndex
                                            dragOriginPx = primary.position
                                            dragStartOffset = currentCustomization.offsets[hitIndex] ?: Offset.Zero
                                        }
                                        val activeIdx = draggingIdx
                                        if (activeIdx != null && scale > 0f) {
                                            val rawDelta =
                                                Offset(
                                                    (primary.position.x - dragOriginPx.x) / density / scale,
                                                    (primary.position.y - dragOriginPx.y) / density / scale,
                                                )
                                            val updater = currentOnCustomizationChange
                                            if (updater != null) {
                                                val latest = currentCustomization
                                                val snap =
                                                    currentGeometry.snapOffset(
                                                        index = activeIdx,
                                                        customization = latest,
                                                        candidate = dragStartOffset + rawDelta,
                                                        rewindAvailable = rewindAvailable,
                                                        unitsAbove = currentUnitsAbove,
                                                        snapEnabled = currentSnapEnabled,
                                                    )
                                                val vLocked =
                                                    snap.verticalGuideDp != null &&
                                                        snap.verticalGuideDp != prevVGuide
                                                val hLocked =
                                                    snap.horizontalGuideDp != null &&
                                                        snap.horizontalGuideDp != prevHGuide
                                                if (vLocked || hLocked) {
                                                    currentHaptics.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove,
                                                    )
                                                }
                                                prevVGuide = snap.verticalGuideDp
                                                prevHGuide = snap.horizontalGuideDp
                                                activeSnap.value = snap
                                                val updatedOffsets = latest.offsets + (activeIdx to snap.offset)
                                                updater(latest.copy(offsets = updatedOffsets))
                                            } else {
                                                val frameDelta = primary.position - primary.previousPosition
                                                if (frameDelta != Offset.Zero) {
                                                    currentOnMove?.invoke(
                                                        activeIdx,
                                                        Offset(
                                                            frameDelta.x / density / scale,
                                                            frameDelta.y / density / scale,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!pinching && draggingIdx == null) {
                                    if (hitIndex != null) {
                                        val changed = hitIndex != currentSelectedIndex
                                        currentOnSelect?.invoke(hitIndex)
                                        if (changed) {
                                            currentHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    } else if (currentOnBackgroundTap != null) {
                                        currentOnBackgroundTap?.invoke(down.position)
                                    } else {
                                        currentOnSelect?.invoke(null)
                                    }
                                }
                            } finally {
                                activeDragIndex.value = null
                                activeSnap.value = null
                                liveBadge.value = null
                                currentOnBusyChange?.invoke(false)
                            }
                        }
                    },
        ) {
            StaticSkinFrame(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = customization.opacity }
                        .padding(top = topExtra),
                layout = layout,
                pinToEdges = pinToEdges,
                rewindAvailable = rewindAvailable,
                customization = customization,
                drawBody = !shell,
                art = art,
                notPressed = notPressed,
                fastForwardSupported = fastForwardSupported,
                fastForwardDotsTotal = fastForwardDotsTotal,
                fastForwardDotsActive = fastForwardDotsActive,
            )

            // Precision studio overlay: dynamic drag snap lasers, selection halo & resize handle.
            val currentDragIdx = activeDragIndex.value
            val currentSnap = activeSnap.value
            Canvas(Modifier.fillMaxSize()) {
                val topOffsetPx = topExtra.toPx()

                // 1. Subtle dot matrix & magnetic alignment laser guides ONLY while actively dragging.
                if (currentDragIdx != null && snapEnabled) {
                    val step = 24.dp.toPx()
                    val dotRadius = 1.dp.toPx()
                    val dotColor = Color.White.copy(alpha = 0.13f)
                    var gx = step
                    while (gx < size.width) {
                        var gy = step
                        while (gy < size.height) {
                            drawCircle(dotColor, radius = dotRadius, center = Offset(gx, gy))
                            gy += step
                        }
                        gx += step
                    }
                }

                if (currentDragIdx != null && currentSnap != null) {
                    val guideShadow = Color.Black.copy(alpha = 0.42f)
                    val guideDash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    currentSnap.verticalGuideDp?.let { vxDp ->
                        val vx = vxDp.dp.toPx()
                        drawLine(
                            guideShadow,
                            Offset(vx, 0f),
                            Offset(vx, size.height),
                            strokeWidth = 2.6.dp.toPx(),
                        )
                        drawLine(
                            accentColor,
                            Offset(vx, 0f),
                            Offset(vx, size.height),
                            strokeWidth = 1.3.dp.toPx(),
                            pathEffect = guideDash,
                        )
                    }
                    currentSnap.horizontalGuideDp?.let { hyDp ->
                        val hy = topOffsetPx + hyDp.dp.toPx()
                        drawLine(
                            guideShadow,
                            Offset(0f, hy),
                            Offset(size.width, hy),
                            strokeWidth = 2.6.dp.toPx(),
                        )
                        drawLine(
                            accentColor,
                            Offset(0f, hy),
                            Offset(size.width, hy),
                            strokeWidth = 1.3.dp.toPx(),
                            pathEffect = guideDash,
                        )
                    }
                    for (peer in currentSnap.alignedPeerCentersDp) {
                        val centerPx = Offset(peer.x.dp.toPx(), topOffsetPx + peer.y.dp.toPx())
                        drawCircle(Color.Black.copy(alpha = 0.55f), radius = 4.5.dp.toPx(), center = centerPx)
                        drawCircle(accentColor, radius = 3.dp.toPx(), center = centerPx)
                    }
                }

                // 2. Dual-contrast precision selection halo on the selected control.
                val activeSel = selectedIndex
                val activeCtrl = activeSel?.let { layout.controls.getOrNull(it) }
                if (activeSel != null && activeCtrl != null &&
                    !customization.isHidden(activeSel, activeCtrl) &&
                    customization.isControlAvailable(activeCtrl, rewindAvailable, fastForwardSupported)
                ) {
                    val placed = geometry.placedFrame(activeSel, activeCtrl.frame, customization)
                    val haloDp = placed.inflate(4f)
                    val haloPx =
                        Rect(
                            haloDp.left.dp.toPx(),
                            topOffsetPx + haloDp.top.dp.toPx(),
                            haloDp.right.dp.toPx(),
                            topOffsetPx + haloDp.bottom.dp.toPx(),
                        )
                    val cornerFrac = activeCtrl.selectionCornerFraction()
                    val cornerPx = CornerRadius(haloPx.minDimension * cornerFrac)
                    drawRoundRect(
                        accentColor.copy(alpha = 0.14f),
                        haloPx.topLeft,
                        haloPx.size,
                        cornerPx,
                    )
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.62f),
                        haloPx.topLeft,
                        haloPx.size,
                        cornerPx,
                        style = Stroke(width = 4.6.dp.toPx()),
                    )
                    drawRoundRect(
                        accentColor,
                        haloPx.topLeft,
                        haloPx.size,
                        cornerPx,
                        style = Stroke(width = 2.6.dp.toPx()),
                    )
                }
            }

            // 4. Live floating readout pill right above/below the active control while moving or resizing.
            val badge = liveBadge.value
            if (badge != null) {
                val (badgeIdx, badgeText) = badge
                val badgeCtrl = badgeIdx?.let { layout.controls.getOrNull(it) }
                val badgeShape = RoundedCornerShape(999.dp)
                if (badgeIdx != null && badgeCtrl != null) {
                    val placed = geometry.placedFrame(badgeIdx, badgeCtrl.frame, customization)
                    val rawTopDp = topExtra.value + placed.top - 40f
                    val finalTopDp =
                        if (rawTopDp < 8f) {
                            (topExtra.value + placed.bottom + 12f).coerceAtMost(totalHeightDp - 36f)
                        } else {
                            rawTopDp
                        }
                    val approxHalfWidthDp = 68f
                    val centerXDp =
                        placed.center.x.coerceIn(
                            approxHalfWidthDp,
                            (surfaceWidthDp - approxHalfWidthDp).coerceAtLeast(approxHalfWidthDp),
                        )
                    Box(
                        modifier =
                            Modifier
                                .absoluteOffset {
                                    IntOffset(
                                        (centerXDp - approxHalfWidthDp).dp.roundToPx(),
                                        finalTopDp.dp.roundToPx(),
                                    )
                                }
                                .size(width = (approxHalfWidthDp * 2).dp, height = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = badgeText,
                            color = Color(0xFFF5F2FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .clip(badgeShape)
                                    .background(Color(0xEB12101C))
                                    .border(1.2.dp, accentColor.copy(alpha = 0.80f), badgeShape)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = badgeText,
                            color = Color(0xFFF5F2FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .clip(badgeShape)
                                    .background(Color(0xEB12101C))
                                    .border(1.2.dp, accentColor.copy(alpha = 0.80f), badgeShape)
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Draws [layout] as a picture with nothing to press, for the controller gallery in Settings. */
@Composable
fun ArcaduneSkinPreview(
    modifier: Modifier = Modifier,
    layout: SkinLayout,
    pinToEdges: Boolean = false,
    shell: Boolean = false,
    customization: SkinCustomization = SkinCustomization(),
    art: SkinArt? = null,
    topExtra: androidx.compose.ui.unit.Dp = 0.dp,
    fastForwardDotsTotal: Int = 4,
) {
    val notPressed = remember { mutableStateOf(false) }
    val frameModifier =
        if (customization.opacity < 0.999f) {
            modifier
                .graphicsLayer { alpha = customization.opacity }
                .padding(top = topExtra)
        } else {
            modifier.padding(top = topExtra)
        }
    StaticSkinFrame(
        modifier = frameModifier,
        layout = layout,
        pinToEdges = pinToEdges,
        rewindAvailable = true,
        customization = customization,
        drawBody = !shell,
        art = art,
        notPressed = notPressed,
        fastForwardDotsTotal = fastForwardDotsTotal,
    )
}

/** A control drawn at rest, as in the editor and the gallery. */
@Composable
private fun StaticControl(
    layout: SkinLayout,
    control: SkinControl,
    frame: SkinRect,
    notPressed: State<Boolean>,
    scale: Float,
    pinned: Boolean,
    fastForwardDotsTotal: Int = 0,
    fastForwardDotsActive: Int = 0,
) {
    when (control) {
        is SkinControl.Dpad -> {
            val art = LocalSkinArt.current?.part(control, pressed = false)
            val partScale = if (control.frame.width > 0f) frame.width / control.frame.width else 1f
            val pad = ((control.reach + ART_BLEED) * partScale * scale).dp
            Canvas(Modifier.fillMaxSize()) {
                if (art != null) drawArtPart(art, pad.toPx()) else drawAnyDpad(layout, control.look, Offset.Zero)
            }
        }
        is SkinControl.Stick ->
            Canvas(Modifier.fillMaxSize()) {
                drawStickBase(layout)
                val knob = size.minDimension * 0.62f
                translate((size.width - knob) / 2, (size.height - knob) / 2) {
                    drawStickKnob(layout, false, knob)
                }
            }
        else ->
            ControlGlyph(
                layout = layout,
                control = control,
                frame = frame,
                pressed = notPressed,
                inset = 0.dp,
                scale = scale,
                pinned = pinned,
                fastForwardDotsTotal = fastForwardDotsTotal,
                fastForwardDotsActive = fastForwardDotsActive,
            )
    }
}

/**
 * The narrowest a full-screen landscape skin is laid out, in skin units: room for the two corner
 * clusters and the small buttons between them. Narrower screens shrink the controls to fit.
 */
internal const val FULL_SCREEN_MIN_WIDTH = 600f

/** The most dp per skin unit a full-screen landscape skin grows to (on tablets). */
internal const val FULL_SCREEN_MAX_SCALE = 1.2f

/** Clamps a control's candidate offset (in skin units) to the actual surface of [surfaceWidthDp] x [surfaceHeightDp]. */
fun SkinLayout.clampControlOffset(
    pinToEdges: Boolean,
    surfaceWidthDp: Float,
    surfaceHeightDp: Float,
    index: Int,
    customization: SkinCustomization,
    candidate: Offset,
    snapToDefault: Boolean = false,
    extraUnitsAbove: Float = 0f,
): Offset =
    SkinGeometry(this, pinToEdges, surfaceWidthDp, surfaceHeightDp)
        .clampOffset(index, customization, candidate, maxOf(unitsAbove(), extraUnitsAbove), snapToDefault)

/**
 * Clamps and optionally magnet-snaps [candidate] (in skin units) to the control's default origin,
 * surface centerlines, or sibling visible controls' centers, returning active guide lines in dp.
 */
fun SkinLayout.snapControlOffset(
    pinToEdges: Boolean,
    surfaceWidthDp: Float,
    surfaceHeightDp: Float,
    index: Int,
    customization: SkinCustomization,
    candidate: Offset,
    rewindAvailable: Boolean = true,
    snapEnabled: Boolean = true,
    snapThresholdDp: Float = 5.5f,
    extraUnitsAbove: Float = 0f,
): ControlSnapResult =
    SkinGeometry(this, pinToEdges, surfaceWidthDp, surfaceHeightDp)
        .snapOffset(
            index = index,
            customization = customization,
            candidate = candidate,
            rewindAvailable = rewindAvailable,
            unitsAbove = maxOf(unitsAbove(), extraUnitsAbove),
            snapEnabled = snapEnabled,
            snapThresholdDp = snapThresholdDp,
        )

/** Where the controls of [layout] land on a surface of [width] x [height] dp. */
internal class SkinGeometry(
    private val layout: SkinLayout,
    private val pinToEdges: Boolean,
    private val width: Float,
    private val height: Float,
) {
    // Landscape fills the height, unless the screen is narrower than the skin (4:3 tablets): then
    // it fits the width, so the left and right controls never run into each other.
    // Full screen (no screen area: the controls float in the corners) needs less width, so the
    // controls keep their size on 16:9 phones; and thumbs do not grow with the screen, so on
    // tablets they stop at a size a thumb still covers.
    val scale =
        when {
            !pinToEdges -> width / layout.width
            layout.screenArea == null ->
                minOf(height / layout.height, width / FULL_SCREEN_MIN_WIDTH, FULL_SCREEN_MAX_SCALE)
            else -> minOf(height / layout.height, width / layout.width)
        }

    /**
     * Top of [baseFrame] before user customization: in landscape, top-bar shoulders/menu stay at
     * the top edge, bottom-bar pills stay at the bottom edge, and entire thumb/wing clusters move
     * together as one cohesive unit so diamonds and multi-row button grids never tear apart on
     * tall tablets or foldables.
     */
    fun placeBaseY(baseFrame: SkinRect): Float {
        if (!pinToEdges) return baseFrame.y * scale
        val cornerClusterLayout = layout.screenArea == null || layout.screenArea.x >= 195f
        if (cornerClusterLayout) {
            val atTop = baseFrame.centerY < layout.height * 0.18f
            return if (atTop) baseFrame.y * scale else height - (layout.height - baseFrame.y) * scale
        }
        return when {
            baseFrame.centerY < layout.height * 0.16f -> baseFrame.y * scale
            baseFrame.centerY > layout.height * 0.86f -> height - (layout.height - baseFrame.y) * scale
            else -> height / 2 + (baseFrame.y - layout.height / 2) * scale
        }
    }

    fun placeBaseX(baseFrame: SkinRect): Float {
        if (!pinToEdges) return baseFrame.x * scale
        return when {
            baseFrame.centerX < layout.width * 0.37f -> baseFrame.x * scale
            baseFrame.centerX > layout.width * 0.63f -> width - (layout.width - baseFrame.x) * scale
            else -> width / 2 + (baseFrame.x - layout.width / 2) * scale
        }
    }

    /**
     * Screen-space bounds (in dp) of control [index] after anchoring [baseFrame] and applying
     * per-control/global scale and drag offset, so dragging across screen zones never teleports.
     */
    fun placedFrame(
        index: Int,
        baseFrame: SkinRect,
        customization: SkinCustomization,
    ): androidx.compose.ui.geometry.Rect {
        val controlScale = customization.effectiveScale(index)
        val offset = customization.offsets[index] ?: Offset.Zero
        val baseCenterX = placeBaseX(baseFrame) + baseFrame.width * scale / 2f
        val baseCenterY = placeBaseY(baseFrame) + baseFrame.height * scale / 2f
        val w = baseFrame.width * controlScale * scale
        val h = baseFrame.height * controlScale * scale
        val cx = baseCenterX + offset.x * scale
        val cy = baseCenterY + offset.y * scale
        return androidx.compose.ui.geometry.Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    /** Clamps a candidate offset (in skin units) to the actual screen/panel bounds of this geometry. */
    fun clampOffset(
        index: Int,
        customization: SkinCustomization,
        candidate: Offset,
        unitsAbove: Float = 0f,
        snapToDefault: Boolean = false,
    ): Offset {
        val baseFrame = layout.controls.getOrNull(index)?.frame ?: return candidate
        if (scale <= 0f) return candidate
        val controlScale = customization.effectiveScale(index)
        val baseCenterX = placeBaseX(baseFrame) + baseFrame.width * scale / 2f
        val baseCenterY = placeBaseY(baseFrame) + baseFrame.height * scale / 2f
        val marginX = baseFrame.width * controlScale * scale * 0.25f
        val marginY = baseFrame.height * controlScale * scale * 0.25f
        val minCx = minOf(marginX, width - marginX)
        val maxCx = maxOf(marginX, width - marginX)
        val topBound = -unitsAbove * scale + marginY
        val bottomBound = height - marginY
        val minCy = minOf(topBound, bottomBound)
        val maxCy = maxOf(topBound, bottomBound)
        val clampedCx = (baseCenterX + candidate.x * scale).coerceIn(minCx, maxCx)
        val clampedCy = (baseCenterY + candidate.y * scale).coerceIn(minCy, maxCy)
        val rawX = (clampedCx - baseCenterX) / scale
        val rawY = (clampedCy - baseCenterY) / scale
        if (!snapToDefault) return Offset(rawX, rawY)
        return Offset(
            if (abs(rawX) < 1.2f) 0f else rawX,
            if (abs(rawY) < 1.2f) 0f else rawY,
        )
    }

    /** Magnetic smart-snap to default origin, surface centerlines, and sibling visible controls' axes. */
    fun snapOffset(
        index: Int,
        customization: SkinCustomization,
        candidate: Offset,
        rewindAvailable: Boolean = true,
        unitsAbove: Float = 0f,
        snapEnabled: Boolean = true,
        snapThresholdDp: Float = 5.5f,
    ): ControlSnapResult {
        val clamped = clampOffset(index, customization, candidate, unitsAbove, snapToDefault = false)
        val baseFrame = layout.controls.getOrNull(index)?.frame ?: return ControlSnapResult(clamped)
        if (!snapEnabled || scale <= 0f) return ControlSnapResult(clamped)

        val baseCenterX = placeBaseX(baseFrame) + baseFrame.width * scale / 2f
        val baseCenterY = placeBaseY(baseFrame) + baseFrame.height * scale / 2f
        val candCx = baseCenterX + clamped.x * scale
        val candCy = baseCenterY + clamped.y * scale

        if (abs(candCx - baseCenterX) <= snapThresholdDp && abs(candCy - baseCenterY) <= snapThresholdDp) {
            return ControlSnapResult(
                offset = Offset.Zero,
                verticalGuideDp = baseCenterX,
                horizontalGuideDp = baseCenterY,
                snappedToDefault = true,
            )
        }

        val peers =
            layout.controls.mapIndexedNotNull { peerIdx, peerCtrl ->
                if (peerIdx == index) return@mapIndexedNotNull null
                if (peerCtrl is SkinControl.Rewind && !rewindAvailable) return@mapIndexedNotNull null
                if (customization.isHidden(peerIdx, peerCtrl)) return@mapIndexedNotNull null
                placedFrame(peerIdx, peerCtrl.frame, customization).center
            }

        val xCandidates =
            buildList {
                add(width / 2f)
                add(baseCenterX)
                peers.forEach { add(it.x) }
            }
        val yCandidates =
            buildList {
                add(height / 2f)
                add(baseCenterY)
                peers.forEach { add(it.y) }
            }

        val bestX = xCandidates.minByOrNull { abs(it - candCx) }?.takeIf { abs(it - candCx) <= snapThresholdDp }
        val bestY = yCandidates.minByOrNull { abs(it - candCy) }?.takeIf { abs(it - candCy) <= snapThresholdDp }

        val targetUnits =
            Offset(
                if (bestX != null) (bestX - baseCenterX) / scale else clamped.x,
                if (bestY != null) (bestY - baseCenterY) / scale else clamped.y,
            )
        val finalOffset = clampOffset(index, customization, targetUnits, unitsAbove, snapToDefault = false)
        val finalCx = baseCenterX + finalOffset.x * scale
        val finalCy = baseCenterY + finalOffset.y * scale
        val vGuide = bestX?.takeIf { abs(finalCx - it) <= 0.5f }
        val hGuide = bestY?.takeIf { abs(finalCy - it) <= 0.5f }
        val alignedPeers =
            peers.filter { peer ->
                (vGuide != null && abs(peer.x - vGuide) <= 0.6f) ||
                    (hGuide != null && abs(peer.y - hGuide) <= 0.6f)
            }
        return ControlSnapResult(
            offset = finalOffset,
            verticalGuideDp = vGuide,
            horizontalGuideDp = hGuide,
            snappedToDefault = abs(finalOffset.x) < 0.05f && abs(finalOffset.y) < 0.05f,
            alignedPeerCentersDp = alignedPeers,
        )
    }

    /** Each control's touch area (with its reach), in dp, by index. */
    fun touchRects(
        customization: SkinCustomization,
        rewindAvailable: Boolean,
        includeHidden: Boolean = false,
        fastForwardSupported: Boolean = true,
    ): List<Pair<Int, androidx.compose.ui.geometry.Rect>> =
        rects(
            customization,
            rewindAvailable,
            withReach = true,
            includeHidden = includeHidden,
            fastForwardSupported = fastForwardSupported,
        )

    /** Each control as drawn (without its reach), in dp, by index. */
    fun frameRects(
        customization: SkinCustomization,
        rewindAvailable: Boolean,
        includeHidden: Boolean = false,
        fastForwardSupported: Boolean = true,
    ): List<Pair<Int, androidx.compose.ui.geometry.Rect>> =
        rects(
            customization,
            rewindAvailable,
            withReach = false,
            includeHidden = includeHidden,
            fastForwardSupported = fastForwardSupported,
        )

    /**
     * Shape-aware editor hit-test for [pointDp] (in surface dp):
     * 1) Exact visual shape check (ellipse for round controls/D-pads/sticks, box for pills/shoulders)
     *    so rectangular reach halos of nearby small buttons never steal taps from A/B/X/Y.
     *    When a small control sits inside/over a much larger one (<0.6x area), the smaller wins.
     * 2) Forgiving minimum 42dp halo (+ portrait caption area below pills) picking closest edge.
     */
    fun hitTestControl(
        pointDp: Offset,
        customization: SkinCustomization,
        rewindAvailable: Boolean,
        fastForwardSupported: Boolean = true,
        exactOnly: Boolean = false,
    ): Int? {
        data class ExactCandidate(
            val index: Int,
            val area: Float,
            val placed: Rect,
            val centerDist: Float,
            val normDist: Float,
        )
        val exactHits = mutableListOf<ExactCandidate>()

        data class HaloCandidate(
            val index: Int,
            val edgeDist: Float,
            val centerDist: Float,
        )
        val haloHits = mutableListOf<HaloCandidate>()

        layout.controls.forEachIndexed { index, item ->
            if (!customization.isControlAvailable(item, rewindAvailable, fastForwardSupported)) {
                return@forEachIndexed
            }
            if (customization.isHidden(index, item)) return@forEachIndexed

            val placed = placedFrame(index, item.frame, customization)
            val area = (placed.width * placed.height).coerceAtLeast(1f)
            val isRound = item.takesRoundTouches()
            val centerDist = hypot(pointDp.x - placed.center.x, pointDp.y - placed.center.y)

            val normDist =
                if (isRound) {
                    val rx = (placed.width / 2f).coerceAtLeast(1f)
                    val ry = (placed.height / 2f).coerceAtLeast(1f)
                    hypot((pointDp.x - placed.center.x) / rx, (pointDp.y - placed.center.y) / ry)
                } else {
                    val minDim = minOf(placed.width, placed.height)
                    val exactPad = if (minDim < 26f) 3.5f else 1.5f
                    if (placed.inflate(exactPad).contains(pointDp)) {
                        val hw = (placed.width / 2f + exactPad).coerceAtLeast(1f)
                        val hh = (placed.height / 2f + exactPad).coerceAtLeast(1f)
                        maxOf(abs(pointDp.x - placed.center.x) / hw, abs(pointDp.y - placed.center.y) / hh)
                    } else {
                        Float.POSITIVE_INFINITY
                    }
                }

            if (normDist <= 1.02f) {
                exactHits += ExactCandidate(index, area, placed, centerDist, normDist)
            } else if (!exactOnly) {
                val reachDp = item.reach * scale
                val padX = maxOf(reachDp, ((42f - placed.width) / 2f).coerceAtLeast(6f))
                val padTop = maxOf(reachDp, ((42f - placed.height) / 2f).coerceAtLeast(6f))
                val captionExtra =
                    if (!pinToEdges && item is SkinControl.Button && item.labelBelow && item.icon == null) {
                        11f * scale
                    } else {
                        0f
                    }
                val expanded =
                    Rect(
                        placed.left - padX,
                        placed.top - padTop,
                        placed.right + padX,
                        placed.bottom + padTop + captionExtra,
                    )
                if (expanded.contains(pointDp)) {
                    val edgeDist =
                        if (isRound) {
                            (centerDist - minOf(placed.width, placed.height) / 2f).coerceAtLeast(0f)
                        } else {
                            val dxOut = maxOf(placed.left - pointDp.x, 0f, pointDp.x - placed.right)
                            val dyOut = maxOf(placed.top - pointDp.y, 0f, pointDp.y - placed.bottom)
                            hypot(dxOut, dyOut)
                        }
                    haloHits += HaloCandidate(index, edgeDist, centerDist)
                }
            }
        }

        if (exactHits.isNotEmpty()) {
            val nestedSmall =
                exactHits.filter { cand ->
                    exactHits.any { other ->
                        other.index != cand.index &&
                            cand.area < other.area * 0.35f &&
                            other.placed.contains(cand.placed.center)
                    }
                }
            val pool = nestedSmall.ifEmpty { exactHits }
            return pool.minByOrNull { it.centerDist }?.index
        }
        if (exactOnly) return null

        return haloHits
            .minWithOrNull(compareBy<HaloCandidate> { it.edgeDist }.thenBy { it.centerDist })
            ?.index
    }

    /**
     * Resolves which single control a 2-finger pinch ([p0Dp], [p1Dp]) should resize:
     * 1) If the player has already selected a visible control, ALWAYS resize that selected control
     *    (never let neighboring buttons or edge constraints hijack the selection!).
     * 2) Otherwise, resize the control under the pinch midpoint (or under both fingers if identical,
     *    or nearest visible control center within 48dp of the midpoint).
     * Never falls back to global size and never mutates any other control.
     */
    fun resolvePinchTarget(
        p0Dp: Offset,
        p1Dp: Offset,
        selectedIndex: Int?,
        customization: SkinCustomization,
        rewindAvailable: Boolean,
        fastForwardSupported: Boolean = true,
    ): Int? {
        if (selectedIndex != null) {
            val selCtrl = layout.controls.getOrNull(selectedIndex)
            if (selCtrl != null &&
                !customization.isHidden(selectedIndex, selCtrl) &&
                customization.isControlAvailable(selCtrl, rewindAvailable, fastForwardSupported)
            ) {
                return selectedIndex
            }
        }

        val midDp = Offset((p0Dp.x + p1Dp.x) / 2f, (p0Dp.y + p1Dp.y) / 2f)
        hitTestControl(midDp, customization, rewindAvailable, fastForwardSupported)?.let { return it }

        val hit0 = hitTestControl(p0Dp, customization, rewindAvailable, fastForwardSupported)
        val hit1 = hitTestControl(p1Dp, customization, rewindAvailable, fastForwardSupported)
        if (hit0 != null && hit0 == hit1) return hit0

        var bestIdx: Int? = null
        var bestDist = 48f
        layout.controls.forEachIndexed { index, item ->
            if (!customization.isControlAvailable(item, rewindAvailable, fastForwardSupported)) {
                return@forEachIndexed
            }
            if (customization.isHidden(index, item)) return@forEachIndexed
            val center = placedFrame(index, item.frame, customization).center
            val dist = (center - midDp).getDistance()
            if (dist <= bestDist) {
                bestDist = dist
                bestIdx = index
            }
        }
        return bestIdx
    }

    private fun rects(
        customization: SkinCustomization,
        rewindAvailable: Boolean,
        withReach: Boolean,
        includeHidden: Boolean,
        fastForwardSupported: Boolean = true,
    ): List<Pair<Int, androidx.compose.ui.geometry.Rect>> =
        layout.controls.mapIndexedNotNull { index, item ->
            if (!customization.isControlAvailable(item, rewindAvailable, fastForwardSupported)) {
                return@mapIndexedNotNull null
            }
            if (!includeHidden && customization.isHidden(index, item)) return@mapIndexedNotNull null
            val placed = placedFrame(index, item.frame, customization)
            val reach = if (withReach) item.reach * scale else 0f
            index to
                androidx.compose.ui.geometry.Rect(
                    placed.left - reach,
                    placed.top - reach,
                    placed.right + reach,
                    placed.bottom + reach,
                )
        }
}

/** Places the background and every static control (with per-control recomposition skipping). */
@Composable
private fun StaticSkinFrame(
    modifier: Modifier,
    layout: SkinLayout,
    pinToEdges: Boolean,
    rewindAvailable: Boolean,
    customization: SkinCustomization,
    drawBody: Boolean,
    art: SkinArt?,
    notPressed: State<Boolean>,
    fastForwardSupported: Boolean = true,
    fastForwardDotsTotal: Int = 0,
    fastForwardDotsActive: Int = 0,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSkinArt provides art) {
        BoxWithConstraints(modifier = modifier) {
            val geometry =
                remember(layout, pinToEdges, maxWidth, maxHeight) {
                    SkinGeometry(layout, pinToEdges, maxWidth.value, maxHeight.value)
                }
            val scale = geometry.scale

            SkinBodyBackdrop(
                layout = layout,
                pinToEdges = pinToEdges,
                drawBody = drawBody,
                scale = scale,
                art = art,
            )

            layout.controls.forEachIndexed { index, item ->
                if (!customization.isControlAvailable(item, rewindAvailable, fastForwardSupported)) {
                    return@forEachIndexed
                }
                if (customization.isHidden(index, item)) return@forEachIndexed
                androidx.compose.runtime.key(index) {
                    val frame = customization.frameOf(index, item.frame)
                    val placed = geometry.placedFrame(index, item.frame, customization)
                    StaticSkinControlItem(
                        layout = layout,
                        control = item,
                        frameX = frame.x,
                        frameY = frame.y,
                        frameWidth = frame.width,
                        frameHeight = frame.height,
                        placedLeft = placed.left,
                        placedTop = placed.top,
                        placedWidth = placed.width,
                        placedHeight = placed.height,
                        scale = scale,
                        pinToEdges = pinToEdges,
                        notPressed = notPressed,
                        fastForwardDotsTotal = fastForwardDotsTotal,
                        fastForwardDotsActive = fastForwardDotsActive,
                    )
                }
            }
        }
    }
}

@Composable
private fun StaticSkinControlItem(
    layout: SkinLayout,
    control: SkinControl,
    frameX: Float,
    frameY: Float,
    frameWidth: Float,
    frameHeight: Float,
    placedLeft: Float,
    placedTop: Float,
    placedWidth: Float,
    placedHeight: Float,
    scale: Float,
    pinToEdges: Boolean,
    notPressed: State<Boolean>,
    fastForwardDotsTotal: Int = 0,
    fastForwardDotsActive: Int = 0,
) {
    val frame =
        remember(frameX, frameY, frameWidth, frameHeight) {
            SkinRect(frameX, frameY, frameWidth, frameHeight)
        }
    val reach = control.reach
    val x = placedLeft - reach * scale
    val y = placedTop - reach * scale
    val boxW = placedWidth + 2f * reach * scale
    val boxH = placedHeight + 2f * reach * scale
    Box(
        modifier =
            Modifier
                .absoluteOffset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }
                .size(boxW.dp, boxH.dp)
                .padding((reach * scale).dp),
    ) {
        StaticControl(
            layout = layout,
            control = control,
            frame = frame,
            notPressed = notPressed,
            scale = scale,
            pinned = pinToEdges,
            fastForwardDotsTotal = fastForwardDotsTotal,
            fastForwardDotsActive = fastForwardDotsActive,
        )
    }
    if (!pinToEdges || control.isExtraButton()) {
        Caption(layout, control, frame, placedLeft, placedTop, scale)
    }
}

@Composable
private fun SkinBodyBackdrop(
    layout: SkinLayout,
    pinToEdges: Boolean,
    drawBody: Boolean,
    scale: Float,
    art: SkinArt?,
) {
    // When a ConsoleShell (Layer 1 BG) sits behind the screens, ConsoleShell renders the console
    // body, NES deck face, grain, and art.body underneath Layer 2 (Screen), keeping Layer 3 strictly buttons.
    if (!drawBody || pinToEdges) return
    Canvas(Modifier.fillMaxSize()) { drawSkinBody(layout, scale * density) }
    val bodyArt = art?.body
    if (bodyArt != null) {
        val grain = art.grain
        val grainTile =
            remember(grain) {
                grain?.let {
                    androidx.compose.ui.graphics.ShaderBrush(
                        androidx.compose.ui.graphics.ImageShader(
                            it,
                            androidx.compose.ui.graphics.TileMode.Repeated,
                            androidx.compose.ui.graphics.TileMode.Repeated,
                        ),
                    )
                }
            }
        Canvas(Modifier.fillMaxSize()) {
            if (grainTile != null) {
                drawRect(grainTile, alpha = 0.06f, blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
            }
            drawArtPart(bodyArt, 0f)
        }
    }
}

/** Places the background and every control (and the names under them) of [layout]. */
@Composable
private fun SkinFrame(
    modifier: Modifier,
    layout: SkinLayout,
    pinToEdges: Boolean,
    rewindAvailable: Boolean,
    customization: SkinCustomization,
    drawBody: Boolean = true,
    art: SkinArt? = null,
    fastForwardSupported: Boolean = true,
    control: @Composable BoxWithConstraintsScope.(
        index: Int,
        control: SkinControl,
        frame: SkinRect,
        placement: Modifier,
        scale: Float,
    ) -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSkinArt provides art) {
        BoxWithConstraints(modifier = modifier) {
            val geometry =
                remember(layout, pinToEdges, maxWidth, maxHeight) {
                    SkinGeometry(layout, pinToEdges, maxWidth.value, maxHeight.value)
                }
            val scale = geometry.scale

            SkinBodyBackdrop(
                layout = layout,
                pinToEdges = pinToEdges,
                drawBody = drawBody,
                scale = scale,
                art = art,
            )

            layout.controls.forEachIndexed { index, item ->
                if (!customization.isControlAvailable(item, rewindAvailable, fastForwardSupported)) {
                    return@forEachIndexed
                }
                if (customization.isHidden(index, item)) return@forEachIndexed
                // Each control keeps its identity by index, so a control that comes or goes (rewind)
                // never makes the ones after it start over.
                androidx.compose.runtime.key(index) {
                    val frame = customization.frameOf(index, item.frame)
                    val placed = geometry.placedFrame(index, item.frame, customization)
                    val reach = item.reach
                    val x = placed.left - reach * scale
                    val y = placed.top - reach * scale
                    val placement =
                        Modifier
                            .absoluteOffset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }
                            .size((placed.width + 2 * reach * scale).dp, (placed.height + 2 * reach * scale).dp)
                    control(index, item, frame, placement, scale)
                    // Sideways there is no room under the controls: names go on them instead (the four extra buttons keep theirs underneath, as upright).
                    if (!pinToEdges || item.isExtraButton()) {
                        Caption(layout, item, frame, placed.left, placed.top, scale)
                    }
                }
            }
        }
    }
}

/** The artwork of the skin being drawn, for the controls inside [SkinFrame]. */
private val LocalSkinArt = androidx.compose.runtime.staticCompositionLocalOf<SkinArt?> { null }

/**
 * Draws a designed picture over this canvas grown by [pad] pixels on every side (the control's
 * reach and the picture's bleed, where its shadow goes).
 */
private fun DrawScope.drawArtPart(
    image: androidx.compose.ui.graphics.ImageBitmap,
    pad: Float,
) {
    drawImage(
        image,
        dstOffset = IntOffset(-pad.toInt(), -pad.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize((size.width + 2 * pad).toInt(), (size.height + 2 * pad).toInt()),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
    )
}

/** A designed d-pad: its picture, tipped a little towards [direction], the pressed arm darker. */
private fun DrawScope.drawArtDpad(
    image: androidx.compose.ui.graphics.ImageBitmap,
    pad: Float,
    padDirection: Offset,
) {
    // Up is +y for the input; the screen counts y downwards.
    val direction = Offset(padDirection.x, -padDirection.y)
    val arm = size.minDimension / 3f
    translate(direction.x * arm * 0.04f, direction.y * arm * 0.04f) { drawArtPart(image, pad) }
    val left = (size.width - 3 * arm) / 2
    val top = (size.height - 3 * arm) / 2
    val dim = Color.Black.copy(alpha = 0.2f)
    val corner = CornerRadius(arm * 0.16f)
    if (direction.y < -0.3f) drawRoundRect(dim, Offset(left + arm, top), Size(arm, arm), corner)
    if (direction.y > 0.3f) drawRoundRect(dim, Offset(left + arm, top + 2 * arm), Size(arm, arm), corner)
    if (direction.x < -0.3f) drawRoundRect(dim, Offset(left, top + arm), Size(arm, arm), corner)
    if (direction.x > 0.3f) drawRoundRect(dim, Offset(left + 2 * arm, top + arm), Size(arm, arm), corner)
}

private object Mono {
    val BodyTop = Color(0xFFF0F0F2)
    val BodyBottom = Color(0xFFD3D3D7)
    val ButtonTop = Color(0xFF2E2E31)
    val ButtonBottom = Color(0xFF111113)
    val ButtonPressedTop = Color(0xFF161618)
    val Ring = Color(0xFF050506)
    val Emboss = Color(0xFF4A4A4F)
    val Caption = Color(0xFF151517)
    val Shadow = Color(0x33000000)
    val Glyph = Color(0xFFD8D8DC)
}

private object Nes {
    val Frame = Color(0xFFDADADC)
    val Pad = Color(0xFF131314)
    val Stripe = Color(0xFF2A2A2C)
    val WhiteBox = Color(0xFFEDEDEF)
    val CaptionStrip = Color(0xFF6A6A6D)
    val Red = Color(0xFFD71920)
    val RedDark = Color(0xFF9E0E12)
    val Label = Color(0xFFC3161C)
}

/** The Minimal skin: every control a white outline, filled in a little while pressed. */
private object Outline {
    val Line = Color(0xF2F2F2F4)
    val Pressed = Color(0x47FFFFFF)

    fun DrawScope.stroke() = Stroke(2.dp.toPx())
}

/**
 * The full-screen sideways controls: solid enough to find at a glance over any game (like Delta's
 * default skins), dark grey or in the console's colours, with a light rim.
 */
private object Overlay {
    val Fill = Color(0xE02C2C30)
    val FillPressed = Color(0xF0505056)
    val Edge = Color(0x8CFFFFFF)
    val Glyph = Color(0xF2FFFFFF)
    val PressedArm = Color(0x38FFFFFF)

    /** A console colour as the solid fill of a sideways control. */
    fun tinted(
        color: Color,
        pressed: Boolean,
    ) = if (pressed) lerp(color, Color.Black, 0.25f).copy(alpha = 0.96f) else color.copy(alpha = 0.9f)
}

/** The console body behind the controls (portrait only). [unit] is pixels per skin unit. */
private fun DrawScope.drawSkinBody(
    layout: SkinLayout,
    unit: Float,
) {
    when (layout.style) {
        SkinStyle.CONSOLE, SkinStyle.OUTLINE -> {
            val palette = layout.palette ?: SkinPalettes.MONO
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(palette.body, palette.bodyShade)),
                cornerRadius = CornerRadius(18 * unit),
            )
            // A soft highlight along the top edge, like light on plastic.
            drawRoundRect(
                Color.White.copy(alpha = 0.08f),
                Offset(6 * unit, 2 * unit),
                Size(size.width - 12 * unit, 3 * unit),
                CornerRadius(2 * unit),
            )
        }

        SkinStyle.MONO -> {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Mono.BodyTop, Mono.BodyBottom)),
                cornerRadius = CornerRadius(18 * unit),
            )
        }

        SkinStyle.NES_CLASSIC -> {
            drawRoundRect(Nes.Frame, cornerRadius = CornerRadius(16 * unit))
            drawNesDeckFace(unit)
        }

        SkinStyle.OVERLAY -> Unit
    }
}

internal fun DrawScope.drawNesDeckFace(
    unit: Float,
    extraTopPx: Float = 0f,
) {
    val extraUnits = if (unit > 0f) extraTopPx / unit else 0f

    fun rect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Color,
        radius: Float,
    ) = drawRoundRect(color, Offset(x * unit, y * unit), Size(w * unit, h * unit), CornerRadius(radius * unit))
    rect(11f, 33f - extraUnits, 298f, 230f + extraUnits, Nes.Pad, 12f)
    for ((top, bottom) in listOf(33f to 56f, 67f to 97f, 109f to 140f, 151f to 181f)) {
        rect(93f, top, 134f, bottom - top, Nes.Stripe, 6f)
    }
    rect(93f, 192f, 134f, 36f, Nes.WhiteBox, 4f)
    rect(93f, 239f, 134f, 24f, Nes.CaptionStrip, 6f)
}

/**
 * Polished plastic, the way Delta draws its controls: a soft shadow under the part, its side (so
 * it has some thickness), a crisp thin edge, a face lit from above with a little light along its
 * top. [shape] gives the part's outline grown by some pixels (its face is `shape(0f)`); [extent]
 * is its size, which the shadow and side scale with.
 */
private fun DrawScope.drawRaised(
    shape: (grow: Float) -> Path,
    fill: Color,
    pressed: Boolean,
    extent: Float,
    faceTop: Float,
    faceBottom: Float,
) {
    val edge = 1.1f.dp.toPx()
    // A soft shadow: faint copies growing outwards, a little lower. Smaller while pressed.
    val spread = extent * if (pressed) 0.03f else 0.08f
    val drop = extent * if (pressed) 0.015f else 0.05f
    val layers = 6
    for (i in layers downTo 1) {
        translate(top = drop) { drawPath(shape(spread * i / layers), Color.Black.copy(alpha = 0.32f / layers)) }
    }
    // The side, showing under the face; then the crisp edge all round.
    val depth = extent * if (pressed) 0.012f else 0.035f
    translate(top = depth) { drawPath(shape(0f), fill.shade(-0.42f)) }
    drawPath(shape(edge), fill.shade(-0.58f))
    // The face, lit from above, a little darker while pressed.
    val face = if (pressed) fill.shade(-0.12f) else fill
    val lit = Brush.verticalGradient(listOf(face.shade(0.16f), face, face.shade(-0.05f)), faceTop, faceBottom)
    drawPath(shape(0f), lit)
    // Light along the top inside edge, like a glossy rim.
    val light = Color.White.copy(alpha = if (fill.luminance() < 0.2f) 0.16f else 0.42f)
    clipPath(shape(0f)) { translate(top = edge * 1.4f) { drawPath(shape(0f), light, style = Stroke(edge * 2.2f)) } }
}

/** A rounded rectangle of [size] at [topLeft] with corners [radius], grown by [grow] pixels. */
private fun roundedBox(
    topLeft: Offset,
    size: Size,
    radius: Float,
    grow: Float,
): Path =
    Path().apply {
        addRoundRect(
            RoundRect(
                Rect(topLeft.x - grow, topLeft.y - grow, topLeft.x + size.width + grow, topLeft.y + size.height + grow),
                CornerRadius((radius + grow).coerceAtLeast(0f)),
            ),
        )
    }

/**
 * This colour, or a shade of it that stands out from [body]: without the shadow-and-ring wells
 * of before, a dark d-pad on a dark body (Genesis, Game Gear) would all but vanish.
 */
private fun Color.standingOutFrom(body: Color): Color {
    val gap = kotlin.math.abs(luminance() - body.luminance())
    if (gap >= 0.05f) return this
    return if (body.luminance() < 0.3f) lerp(this, Color.White, 0.2f) else lerp(this, Color.Black, 0.25f)
}

/** Lighter ([amount] > 0) or darker ([amount] < 0) shade of a colour. */
private fun Color.shade(amount: Float): Color =
    if (amount >= 0) lerp(this, Color.White, amount) else lerp(this, Color.Black, -amount)

private fun SkinLayout.paletteOrMono() = palette ?: SkinPalettes.MONO

/** Any d-pad of any skin: the cross, four separate arrows, or the N64's C buttons. */
private fun DrawScope.drawAnyDpad(
    layout: SkinLayout,
    look: DpadLook,
    padDirection: Offset,
) {
    // PadKit reports up as +y (like a game stick); the screen counts y downwards.
    val direction = Offset(padDirection.x, -padDirection.y)
    when {
        look == DpadLook.BALL_TOP -> drawBallTop(layout, direction)
        layout.style == SkinStyle.OUTLINE && look == DpadLook.CROSS -> drawDpad(SkinStyle.OUTLINE, direction)
        layout.style == SkinStyle.OUTLINE -> drawFourPads(look, direction, outline = true)
        layout.style == SkinStyle.OVERLAY && look == DpadLook.CROSS ->
            drawDpad(SkinStyle.OVERLAY, direction, layout.palette?.dpad)
        layout.style == SkinStyle.OVERLAY -> drawFourPads(look, direction, overlayTint = layout.palette)
        look == DpadLook.CROSS && layout.style == SkinStyle.CONSOLE -> {
            val palette = layout.paletteOrMono()
            drawConsoleCross(palette.copy(dpad = palette.dpad.standingOutFrom(palette.body)), direction)
        }
        look == DpadLook.CROSS -> drawDpad(layout.style, direction)
        else -> drawFourPads(look, direction, palette = layout.paletteOrMono())
    }
}

/** A ball-top joystick seen from above: the round gate, and the ball leaning the way it is pushed. */
private fun DrawScope.drawBallTop(
    layout: SkinLayout,
    direction: Offset,
) {
    val r = size.minDimension / 2
    val c = Offset(size.width / 2, size.height / 2)
    val lean = c + direction * (r * 0.32f)
    if (layout.style == SkinStyle.OUTLINE) {
        with(Outline) {
            drawCircle(Outline.Line, r - 1.dp.toPx(), c, style = stroke())
            if (direction != Offset.Zero) drawCircle(Outline.Pressed, r * 0.36f, lean)
            drawCircle(Outline.Line, r * 0.36f, lean, style = stroke())
        }
        return
    }
    if (layout.style == SkinStyle.OVERLAY) {
        drawCircle(Overlay.Fill, r, c)
        drawCircle(Overlay.Edge, r, c, style = Stroke(1.5.dp.toPx()))
        val knob = layout.palette?.stickKnob ?: Color(0xFF8A8A90)
        drawCircle(Overlay.tinted(knob, direction != Offset.Zero), r * 0.36f, lean)
        drawCircle(Overlay.Edge, r * 0.36f, lean, style = Stroke(1.5.dp.toPx()))
        return
    }
    val palette = layout.paletteOrMono()
    // The dust washer and the gate around the shaft.
    drawCircle(palette.stickBase.shade(-0.3f), r, c)
    drawCircle(
        Brush.verticalGradient(listOf(palette.stickBase.shade(0.1f), palette.stickBase), c.y - r, c.y + r),
        r * 0.9f,
        c,
    )
    drawCircle(palette.dpadMark, r * 0.9f, c, style = Stroke(r * 0.03f))
    // The shaft, then the ball with a highlight, like light on a glossy ball.
    drawLine(Color(0xFF6A6A70), c, lean, r * 0.14f, androidx.compose.ui.graphics.StrokeCap.Round)
    val ball = palette.stickKnob
    drawCircle(Color(0x55000000), r * 0.4f, lean + Offset(0f, r * 0.06f))
    drawCircle(
        Brush.radialGradient(
            listOf(ball.shade(0.35f), ball, ball.shade(-0.35f)),
            lean - Offset(r * 0.12f, r * 0.14f),
            r * 0.5f,
        ),
        r * 0.38f,
        lean,
    )
}

/** The cross in a console's colours: raised plastic, the pressed arm a little darker. */
private fun DrawScope.drawConsoleCross(
    palette: SkinPalette,
    direction: Offset,
) {
    val full = size.minDimension * 0.94f
    val left = (size.width - full) / 2
    val top = (size.height - full) / 2 - size.minDimension * 0.02f
    val arm = full / 3f
    // Rounded outside corners and rounded inside ones (where the arms meet), like a real cross.
    val fillet = arm * 0.2f
    val radius = CornerRadius(arm * 0.06f)

    fun cross(grow: Float): Path {
        val g = grow - fillet
        val bars =
            Path().apply {
                addRoundRect(RoundRect(left + arm - g, top - g, left + 2 * arm + g, top + full + g, radius))
                addRoundRect(RoundRect(left - g, top + arm - g, left + full + g, top + 2 * arm + g, radius))
            }
        // Drawn filled and stroked with round joins, the fillet wide, every corner comes out rounded.
        return bars
    }

    val pressedTilt = Offset(direction.x, direction.y) * (arm * 0.04f)
    translate(pressedTilt.x, pressedTilt.y) {
        drawRoundedRaised(
            { grow -> cross(grow) },
            fillet,
            palette.dpad,
            direction != Offset.Zero,
            full,
            top,
            top + full,
        )
    }
    // The pressed arm sinks a little darker.
    val dim = Color.Black.copy(alpha = 0.16f)
    if (direction.y < -0.3f) drawRect(dim, Offset(left + arm, top), Size(arm, arm))
    if (direction.y > 0.3f) drawRect(dim, Offset(left + arm, top + 2 * arm), Size(arm, arm))
    if (direction.x < -0.3f) drawRect(dim, Offset(left, top + arm), Size(arm, arm))
    if (direction.x > 0.3f) drawRect(dim, Offset(left + 2 * arm, top + arm), Size(arm, arm))
    // Marks pressed into the plastic: a light edge under each, the mark itself in the cross's own tone.
    val mark = if (palette.dpad.luminance() < 0.2f) palette.dpad.shade(0.22f) else palette.dpad.shade(-0.3f)
    translate(top = 1.dp.toPx()) { drawArrows(left, top, arm, Color.White.copy(alpha = 0.14f)) }
    drawArrows(left, top, arm, mark, direction)
    val middle = Offset(left + full / 2, top + full / 2)
    drawCircle(mark, arm * 0.3f, middle, style = Stroke(arm * 0.05f))
}

/**
 * [drawRaised] for shapes whose corners are rounded by stroking them with round joins ([fillet]
 * wide): every layer is filled and stroked the same way.
 */
private fun DrawScope.drawRoundedRaised(
    shape: (grow: Float) -> Path,
    fillet: Float,
    fill: Color,
    pressed: Boolean,
    extent: Float,
    faceTop: Float,
    faceBottom: Float,
) {
    val join = androidx.compose.ui.graphics.StrokeJoin.Round

    fun DrawScope.solid(
        path: Path,
        color: Color,
    ) {
        drawPath(path, color)
        drawPath(path, color, style = Stroke(fillet * 2, join = join))
    }
    val edge = 1.1f.dp.toPx()
    val spread = extent * if (pressed) 0.03f else 0.07f
    val drop = extent * if (pressed) 0.015f else 0.045f
    val layers = 6
    for (i in layers downTo 1) {
        translate(top = drop) { solid(shape(spread * i / layers), Color.Black.copy(alpha = 0.3f / layers)) }
    }
    val depth = extent * if (pressed) 0.012f else 0.03f
    translate(top = depth) { solid(shape(0f), fill.shade(-0.42f)) }
    solid(shape(edge), fill.shade(-0.58f))
    val face = if (pressed) fill.shade(-0.1f) else fill
    val brush = Brush.verticalGradient(listOf(face.shade(0.16f), face, face.shade(-0.05f)), faceTop, faceBottom)
    drawPath(shape(0f), brush)
    drawPath(shape(0f), brush, style = Stroke(fillet * 2, join = join))
}

/**
 * Four pads around a centre: the PlayStation's separate arrows ([DpadLook.SEPARATE]) or the N64's
 * yellow C buttons ([DpadLook.C_BUTTONS]). In landscape ([overlayTint]) they are see-through.
 */
private fun DrawScope.drawFourPads(
    look: DpadLook,
    direction: Offset,
    palette: SkinPalette? = null,
    overlayTint: SkinPalette? = null,
    /** The Minimal skin: outlines only. */
    outline: Boolean = false,
) {
    val full = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val away = full * 0.32f
    if (outline) {
        for ((dir, isPressed) in fourWays(direction)) {
            val centre = c + dir * away
            val stroke = Stroke(2.dp.toPx())
            if (look == DpadLook.C_BUTTONS) {
                val r = full * 0.165f
                if (isPressed) drawCircle(Outline.Pressed, r, centre)
                drawCircle(Outline.Line, r, centre, style = stroke)
                drawTriangleMark(centre, dir, r * 0.42f, Outline.Line)
            } else {
                val half = full * 0.17f
                val corner = CornerRadius(half * 0.45f)
                val box = Size(half * 2, half * 2)
                if (isPressed) drawRoundRect(Outline.Pressed, centre - Offset(half, half), box, corner)
                drawRoundRect(Outline.Line, centre - Offset(half, half), box, corner, style = stroke)
                drawTriangleMark(centre, dir, half * 0.5f, Outline.Line)
            }
        }
        return
    }
    val overlay = palette == null
    val cColor = (overlayTint ?: palette)?.buttons?.get(SkinPalettes.C_BUTTONS_KEY)
    val ups =
        listOf(
            Offset(0f, -1f) to (direction.y < -0.3f),
            Offset(0f, 1f) to (direction.y > 0.3f),
            Offset(-1f, 0f) to (direction.x < -0.3f),
            Offset(1f, 0f) to (direction.x > 0.3f),
        )
    for ((dir, isPressed) in ups) {
        val centre = c + dir * away
        val mark: Color
        if (look == DpadLook.C_BUTTONS) {
            val r = full * 0.165f
            if (overlay) {
                val fill = cColor?.first?.let { Overlay.tinted(it, isPressed) } ?: if (isPressed) Overlay.FillPressed else Overlay.Fill
                drawCircle(fill, r, centre)
                drawCircle(Overlay.Edge, r, centre, style = Stroke(1.5.dp.toPx()))
                mark = Overlay.Glyph
            } else {
                val fill = cColor?.first ?: palette!!.button
                val face = r * 0.92f
                drawRaised(
                    { grow -> Path().apply { addOval(Rect(centre, face + grow)) } },
                    fill,
                    isPressed,
                    r * 2,
                    centre.y - face,
                    centre.y + face,
                )
                mark = cColor?.second ?: palette.buttonLabel
            }
            drawTriangleMark(centre, dir, r * 0.42f, if (isPressed) PressedMark else mark)
        } else {
            val half = full * 0.17f
            val topLeft = centre - Offset(half, half)
            val box = Size(half * 2, half * 2)
            val corner = CornerRadius(half * 0.45f)
            if (overlay) {
                drawRoundRect(if (isPressed) Overlay.FillPressed else Overlay.Fill, topLeft, box, corner)
                drawRoundRect(Overlay.Edge, topLeft, box, corner, style = Stroke(1.5.dp.toPx()))
                mark = Overlay.Glyph
            } else {
                val fill = palette!!.dpad
                drawRaised(
                    { grow -> roundedBox(topLeft, box, corner.x, grow) },
                    fill,
                    isPressed,
                    box.width,
                    topLeft.y,
                    topLeft.y + box.height,
                )
                mark = if (fill.luminance() < 0.2f) fill.shade(0.3f) else palette.dpadMark
            }
            drawTriangleMark(centre, dir, half * 0.5f, if (isPressed) PressedMark else mark)
        }
    }
}

/** The four directions of a d-pad and whether [direction] (screen y down) presses each. */
private fun fourWays(direction: Offset) =
    listOf(
        Offset(0f, -1f) to (direction.y < -0.3f),
        Offset(0f, 1f) to (direction.y > 0.3f),
        Offset(-1f, 0f) to (direction.x < -0.3f),
        Offset(1f, 0f) to (direction.x > 0.3f),
    )

/** A small triangle at [centre] pointing to [dir]. */
private fun DrawScope.drawTriangleMark(
    centre: Offset,
    dir: Offset,
    size: Float,
    color: Color,
) {
    val side = Offset(-dir.y, dir.x)
    val tip = centre + dir * size
    val back = centre - dir * (size * 0.6f)
    val a = back + side * (size * 0.85f)
    val b = back - side * (size * 0.85f)
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(a.x, a.y)
            lineTo(b.x, b.y)
            close()
        },
        color,
    )
}

/** The well an analog stick sits in. */
private fun DrawScope.drawStickBase(layout: SkinLayout) {
    val r = size.minDimension / 2
    if (layout.style == SkinStyle.OUTLINE) {
        with(Outline) { drawCircle(Outline.Line, r - 1.dp.toPx(), style = stroke()) }
        return
    }
    if (layout.style == SkinStyle.OVERLAY) {
        drawCircle(Overlay.Fill, r)
        drawCircle(Overlay.Edge, r, style = Stroke(1.5.dp.toPx()))
        return
    }
    val palette = layout.paletteOrMono()
    // The gate the stick sits in, sunk into the body: shaded on its upper edge.
    drawCircle(palette.stickBase.shade(-0.35f), r)
    drawCircle(
        Brush.verticalGradient(listOf(palette.stickBase.shade(-0.15f), palette.stickBase.shade(0.08f))),
        r * 0.92f,
    )
}

/** The stick's cap, drawn on a square of [knob] (the whole canvas when not given). */
private fun DrawScope.drawStickKnob(
    layout: SkinLayout,
    pressed: Boolean,
    knob: Float = size.minDimension,
) {
    val r = knob / 2
    val c = Offset(knob / 2, knob / 2)
    if (layout.style == SkinStyle.OUTLINE) {
        with(Outline) {
            drawCircle(if (pressed) Outline.Pressed else Color(0xFF2C2C30), r * 0.86f, c)
            drawCircle(Outline.Line, r * 0.86f, c, style = stroke())
        }
        return
    }
    if (layout.style == SkinStyle.OVERLAY) {
        drawCircle(Overlay.tinted(layout.palette?.stickKnob ?: Color(0xFF6A6A70), pressed), r * 0.86f, c)
        drawCircle(Overlay.Edge, r * 0.86f, c, style = Stroke(1.5.dp.toPx()))
        return
    }
    val palette = layout.paletteOrMono()
    val cap = palette.stickKnob
    val capRadius = r * 0.86f
    drawRaised(
        { grow -> Path().apply { addOval(Rect(c, capRadius + grow)) } },
        cap,
        pressed,
        knob,
        c.y - capRadius,
        c.y + capRadius,
    )
    // The dish of the cap, like the grippy top of a real stick.
    drawCircle(cap.shade(if (pressed) -0.2f else -0.08f), r * 0.55f, c)
}

private fun DrawScope.drawDpad(
    style: SkinStyle,
    direction: Offset,
    /** Sideways: the console's d-pad colour, if it has one. */
    tint: Color? = null,
) {
    val full = size.minDimension
    val left = (size.width - full) / 2
    val top = (size.height - full) / 2
    val arm = full / 3f
    val radius = CornerRadius(arm * 0.24f)

    fun cross(grow: Float): Path =
        Path().apply {
            addRoundRect(RoundRect(left + arm - grow, top - grow, left + 2 * arm + grow, top + full + grow, radius))
            addRoundRect(RoundRect(left - grow, top + arm - grow, left + full + grow, top + 2 * arm + grow, radius))
        }

    when (style) {
        SkinStyle.OUTLINE -> {
            val middle = Offset(left + 1.5f * arm, top + 1.5f * arm)
            if (direction.y < -0.3f) drawRect(Outline.Pressed, Offset(left + arm, top), Size(arm, arm))
            if (direction.y > 0.3f) drawRect(Outline.Pressed, Offset(left + arm, top + 2 * arm), Size(arm, arm))
            if (direction.x < -0.3f) drawRect(Outline.Pressed, Offset(left, top + arm), Size(arm, arm))
            if (direction.x > 0.3f) drawRect(Outline.Pressed, Offset(left + 2 * arm, top + arm), Size(arm, arm))
            val line = 2.dp.toPx()
            drawPath(cross(-line / 2), Outline.Line, style = Stroke(line))
            drawChevrons(middle, arm, Outline.Line)
            return
        }
        SkinStyle.OVERLAY -> {
            drawPath(cross(0f), tint?.let { lerp(it, Color.White, 0.14f).copy(alpha = 0.9f) } ?: Overlay.Fill)
            // Only the pressed arm (or two, on a diagonal) lights up.
            val lit = Overlay.PressedArm
            if (direction.y < -0.3f) drawRect(lit, Offset(left + arm, top), Size(arm, arm))
            if (direction.y > 0.3f) drawRect(lit, Offset(left + arm, top + 2 * arm), Size(arm, arm))
            if (direction.x < -0.3f) drawRect(lit, Offset(left, top + arm), Size(arm, arm))
            if (direction.x > 0.3f) drawRect(lit, Offset(left + 2 * arm, top + arm), Size(arm, arm))
            drawPath(cross(0f), Overlay.Edge, style = Stroke(1.5.dp.toPx()))
            val middle = Offset(left + 1.5f * arm, top + 1.5f * arm)
            drawChevrons(middle, arm, Overlay.Glyph)
            drawCircle(Overlay.Edge, arm * 0.3f, middle, style = Stroke(1.5.dp.toPx()))
            return
        }

        SkinStyle.NES_CLASSIC -> drawPath(cross(arm * 0.12f), Color(0xFFCFCFD2))
        SkinStyle.MONO, SkinStyle.CONSOLE -> translate(top = arm * 0.06f) { drawPath(cross(0f), Mono.Shadow) }
    }

    drawPath(cross(0f), Mono.Ring)
    val inner = arm * 0.07f
    drawPath(
        cross(-inner),
        Brush.verticalGradient(listOf(Mono.ButtonTop, Mono.ButtonBottom), top, top + full),
    )
    // The pressed arm (or two, on a diagonal) darkens like a pressed rubber pad.
    val pressed = Mono.ButtonPressedTop
    if (direction.y < -0.3f) {
        drawRect(
            pressed,
            Offset(left + arm + inner, top + inner),
            Size(arm - 2 * inner, arm - inner),
        )
    }
    if (direction.y > 0.3f) {
        drawRect(
            pressed,
            Offset(left + arm + inner, top + 2 * arm),
            Size(arm - 2 * inner, arm - inner),
        )
    }
    if (direction.x < -0.3f) {
        drawRect(
            pressed,
            Offset(left + inner, top + arm + inner),
            Size(arm - inner, arm - 2 * inner),
        )
    }
    if (direction.x > 0.3f) {
        drawRect(
            pressed,
            Offset(left + 2 * arm, top + arm + inner),
            Size(arm - inner, arm - 2 * inner),
        )
    }

    drawArrows(left, top, arm, Mono.Emboss, direction)
    drawCircle(Color(0xFF0B0B0C), arm * 0.3f, Offset(left + full / 2, top + full / 2))
    drawCircle(
        Mono.Emboss.copy(alpha = 0.6f),
        arm * 0.3f,
        Offset(left + full / 2, top + full / 2),
        style = Stroke(arm * 0.04f),
    )
}

private fun DrawScope.drawArrows(
    left: Float,
    top: Float,
    arm: Float,
    color: Color,
    direction: Offset = Offset.Zero,
) {
    val c = Offset(left + 1.5f * arm, top + 1.5f * arm)
    val far = arm * 1.15f
    val near = arm * 0.78f
    val half = arm * 0.22f

    // The arrow of a pressed arm lights up, so the press shows even on a black d-pad.
    fun triangle(
        lit: Boolean,
        tip: Offset,
        a: Offset,
        b: Offset,
    ) = drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(a.x, a.y)
            lineTo(b.x, b.y)
            close()
        },
        if (lit) PressedMark else color,
    )
    triangle(
        direction.y < -0.3f,
        Offset(c.x, c.y - far),
        Offset(c.x - half, c.y - near),
        Offset(c.x + half, c.y - near),
    )
    triangle(direction.y > 0.3f, Offset(c.x, c.y + far), Offset(c.x - half, c.y + near), Offset(c.x + half, c.y + near))
    triangle(
        direction.x < -0.3f,
        Offset(c.x - far, c.y),
        Offset(c.x - near, c.y - half),
        Offset(c.x - near, c.y + half),
    )
    triangle(direction.x > 0.3f, Offset(c.x + far, c.y), Offset(c.x + near, c.y - half), Offset(c.x + near, c.y + half))
}

/** Four open arrowheads around [centre], one in each arm of a cross whose arms are [arm] wide. */
private fun DrawScope.drawChevrons(
    centre: Offset,
    arm: Float,
    color: Color,
) {
    val reach = arm * 1.02f
    val half = arm * 0.24f
    val depth = arm * 0.2f
    val width = arm * 0.075f
    for (dir in listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))) {
        val side = Offset(-dir.y, dir.x)
        val tip = centre + dir * reach
        val back = centre + dir * (reach - depth)
        val path =
            Path().apply {
                moveTo((back + side * half).x, (back + side * half).y)
                lineTo(tip.x, tip.y)
                lineTo((back - side * half).x, (back - side * half).y)
            }
        val stroke =
            Stroke(
                width,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        drawPath(path, color, style = stroke)
    }
}

/** The arrow of a pressed d-pad arm. */
private val PressedMark = Color(0xF2FFFFFF)

/** The PlayStation symbols, drawn as shapes so they look the same on every phone's font. */
private val PlayStationSymbols = setOf("△", "○", "✕", "□")

@Composable
private fun ControlGlyph(
    layout: SkinLayout,
    control: SkinControl,
    frame: SkinRect,
    pressed: State<Boolean>,
    inset: androidx.compose.ui.unit.Dp,
    scale: Float,
    pinned: Boolean,
    fastForwardDotsTotal: Int = 0,
    fastForwardDotsActive: Int = 0,
) {
    val style = layout.style
    val palette = layout.palette
    val isPressed = pressed.value
    // A pressed button sinks a little, like real plastic under a thumb.
    val sink by androidx.compose.animation.core.animateFloatAsState(
        if (isPressed) 0.93f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 1400f),
        label = "sink",
    )
    Box(
        modifier =
            Modifier.fillMaxSize().padding(inset).graphicsLayer {
                scaleX = sink
                scaleY = sink
            },
        contentAlignment = Alignment.Center,
    ) {
        val shape =
            when (control) {
                is SkinControl.Button -> control.shape
                is SkinControl.Menu, is SkinControl.Rewind ->
                    if (frame.width < frame.height * 1.3f) ButtonShape.ROUND else ButtonShape.PILL
                is SkinControl.Dpad, is SkinControl.Stick -> ButtonShape.ROUND
            }
        val key = (control as? SkinControl.Button)?.keyCode

        // The button's own colour and the colour of what is written on it.
        val consolePalette = layout.paletteOrMono()
        val fill =
            when {
                key != null && shape == ButtonShape.SHOULDER -> consolePalette.shoulder
                key != null && shape == ButtonShape.ROUND -> consolePalette.fillOf(key)
                else -> consolePalette.small
            }
        // Sideways every control is solid: in the console's colour for that control, if it has colours.
        val overlayTint =
            palette?.let { p ->
                when {
                    shape == ButtonShape.SHOULDER -> p.shoulder
                    key != null && shape == ButtonShape.ROUND -> p.fillOf(key)
                    else -> p.small
                }
            }
        val labelColor =
            when {
                // Dark text on a light button (GBA's grey A / B), white on anything darker.
                style == SkinStyle.OVERLAY && overlayTint != null && overlayTint.luminance() > 0.45f ->
                    when {
                        shape == ButtonShape.SHOULDER -> consolePalette.shoulderLabel
                        key != null -> consolePalette.labelOf(key)
                        else -> consolePalette.buttonLabel
                    }
                style == SkinStyle.OVERLAY -> Overlay.Glyph
                style == SkinStyle.OUTLINE -> Outline.Line
                style != SkinStyle.CONSOLE -> Mono.Emboss
                // Names on pills and shoulders (always on them sideways) must read on their fill.
                shape == ButtonShape.SHOULDER -> readableOn(fill, consolePalette.shoulderLabel)
                shape == ButtonShape.PILL -> readableOn(fill, consolePalette.buttonLabel)
                key != null -> consolePalette.labelOf(key)
                // Menu and rewind drawn round: their picture must read on them too.
                else -> readableOn(fill, consolePalette.buttonLabel)
            }

        // The designed picture of the control, when the skin has one (see SkinArt).
        val art = LocalSkinArt.current?.part(control, isPressed)
        if (art != null) {
            val partScale = if (control.frame.width > 0f) frame.width / control.frame.width else 1f
            val reachPad = (control.reach + ART_BLEED) * partScale * scale
            Canvas(Modifier.fillMaxSize()) { drawArtPart(art, reachPad.dp.toPx()) }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                when {
                    style == SkinStyle.OVERLAY -> drawOverlayButton(shape, isPressed, overlayTint)
                    style == SkinStyle.OUTLINE -> drawOutlineButton(shape, isPressed)
                    style == SkinStyle.CONSOLE ->
                        drawConsoleButton(shape, fill.standingOutFrom(consolePalette.body), isPressed)
                    style == SkinStyle.NES_CLASSIC && control is SkinControl.Button && shape == ButtonShape.ROUND ->
                        drawNesFaceButton(isPressed)
                    style == SkinStyle.NES_CLASSIC && (control is SkinControl.Menu || control is SkinControl.Rewind) && frame.width < frame.height * 1.3f ->
                        drawNesSmallButton(isPressed)
                    style == SkinStyle.NES_CLASSIC && (control is SkinControl.Menu || control is SkinControl.Rewind || (control is SkinControl.Button && control.isExtraButton())) ->
                        drawNesSmallPill(isPressed)
                    else -> drawMonoButton(shape, isPressed)
                }
            }
        }

        // A picture on the button (menu, rewind, microphone, lid, swap, home) instead of a name.
        val glyph =
            when (control) {
                is SkinControl.Menu -> Glyph.MENU
                is SkinControl.Rewind -> Glyph.REWIND
                is SkinControl.Button -> control.icon?.let { Glyph.valueOf(it.name) }
                else -> null
            }
        // What is written on the button itself: its name, unless it goes under it.
        val onButton =
            when {
                glyph != null -> null
                control is SkinControl.Button && (!control.labelBelow || style == SkinStyle.OVERLAY || pinned) -> control.label
                else -> null
            }
        if (glyph != null && (style != SkinStyle.NES_CLASSIC || control is SkinControl.Button || frame.width >= frame.height * 1.3f)) {
            val coloured = style == SkinStyle.CONSOLE || style == SkinStyle.OVERLAY || style == SkinStyle.OUTLINE
            val glyphColor = if (coloured) labelColor else Mono.Glyph
            val mouldedShadow = style == SkinStyle.CONSOLE
            val pressedShift = isPressed && art != null
            if (glyph == Glyph.FAST_FORWARD && fastForwardDotsTotal > 0) {
                val activeCount = fastForwardDotsActive.coerceIn(0, fastForwardDotsTotal)
                Canvas(Modifier.fillMaxSize()) {
                    drawFastForwardWithDots(
                        baseColor = glyphColor,
                        totalDots = fastForwardDotsTotal,
                        activeDots = activeCount,
                        mouldedShadow = mouldedShadow,
                        pressedShift = pressedShift,
                    )
                }
            } else {
                Canvas(Modifier.fillMaxSize(if (shape == ButtonShape.PILL) 0.7f else 0.56f)) {
                    val pressDy = if (pressedShift) size.minDimension * 0.032f else 0f
                    translate(top = pressDy) {
                        if (mouldedShadow) {
                            translate(top = (size.minDimension * 0.055f).coerceAtLeast(1f)) {
                                drawGlyph(glyph, Color.Black.copy(alpha = 0.26f))
                            }
                        }
                        drawGlyph(glyph, glyphColor)
                    }
                }
            }
        }
        if (onButton != null && onButton in PlayStationSymbols) {
            val overlaySymbol = key?.let { palette?.labelOf(it) } ?: Overlay.Glyph
            val symbolColor = if (style == SkinStyle.OVERLAY) overlaySymbol else labelColor
            Canvas(Modifier.fillMaxSize(0.46f)) { drawPlayStationSymbol(onButton, symbolColor) }
        } else if (onButton != null && !(art != null && artCarriesLabel(control))) {
            val unitDp = scale
            val fontSize =
                with(LocalDensity.current) {
                    val natural = minOf(frame.width, frame.height) * unitDp * if (control is SkinControl.Button && control.shape == ButtonShape.PILL) 0.42f else 0.46f
                    // A long name (SELECT/X) shrinks to fit inside the pill.
                    val fits = frame.width * unitDp * 0.84f / (onButton.length * 0.62f)
                    minOf(natural, fits).dp.toSp()
                }
            // Medium weight with a faint emboss, like lettering moulded into the plastic.
            val emboss =
                if (style == SkinStyle.CONSOLE) {
                    androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1.5f), 2f)
                } else {
                    null
                }
            Text(
                text = onButton,
                color = labelColor,
                fontWeight = if (style == SkinStyle.CONSOLE) FontWeight.Medium else FontWeight.Black,
                fontSize = fontSize,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(shadow = emboss),
            )
        }
    }
}

/** [preferred] if it stands out on [fill], else near-black on light fills and near-white on dark ones. */
private fun readableOn(
    fill: Color,
    preferred: Color,
): Color =
    when {
        kotlin.math.abs(fill.luminance() - preferred.luminance()) >= 0.3f -> preferred
        fill.luminance() > 0.4f -> Color(0xFF1E1E22)
        else -> Color(0xFFF2F2F4)
    }

/** The pictures drawn on buttons. */
private enum class Glyph { MENU, REWIND, MIC, LID, SWAP, FAST_FORWARD, QUICK_SAVE, QUICK_LOAD, EXIT }

/**
 * Draws the Fast Forward double-chevron with the same weight and moulded finish as [Glyph.REWIND],
 * above a minimalist row of [totalDots] progressive micro-LED speed pips (lighting [activeDots]
 * from left to right in the skin's native glyph colour).
 */
private fun DrawScope.drawFastForwardWithDots(
    baseColor: Color,
    totalDots: Int,
    activeDots: Int,
    mouldedShadow: Boolean,
    pressedShift: Boolean,
) {
    val w = size.width
    val h = size.height
    val s = minOf(w, h)
    val pressDy = if (pressedShift) s * 0.022f else 0f
    val cx = w * 0.5f
    val cy = h * 0.40f + pressDy
    val triHalfH = s * 0.165f
    val triW = s * 0.196f
    val shadowDy = (s * 0.038f).coerceAtLeast(1f)

    for (startX in listOf(cx - triW * 1.04f, cx + triW * 0.04f)) {
        val tipX = startX + triW
        if (mouldedShadow) {
            drawPath(
                Path().apply {
                    moveTo(startX, cy - triHalfH + shadowDy)
                    lineTo(tipX, cy + shadowDy)
                    lineTo(startX, cy + triHalfH + shadowDy)
                    close()
                },
                Color.Black.copy(alpha = 0.26f),
            )
        }
        drawPath(
            Path().apply {
                moveTo(startX, cy - triHalfH)
                lineTo(tipX, cy)
                lineTo(startX, cy + triHalfH)
                close()
            },
            baseColor,
        )
    }

    val count = totalDots.coerceIn(1, 6)
    val dotY = h * 0.735f + pressDy
    val maxSpan = w * 0.56f
    val rawRadius = (s * 0.054f).coerceAtLeast(1.25f)
    val step = if (count > 1) minOf(s * 0.185f, maxSpan / (count - 1).toFloat()) else 0f
    val dotRadius = if (count > 1) minOf(rawRadius, step * 0.34f) else rawRadius
    val firstDotX = cx - step * (count - 1) / 2f

    for (i in 0 until count) {
        val center = Offset(firstDotX + i * step, dotY)
        if (i < activeDots) {
            drawCircle(baseColor.copy(alpha = 0.26f), radius = dotRadius * 1.48f, center = center)
            if (mouldedShadow) {
                drawCircle(
                    Color.Black.copy(alpha = 0.26f),
                    radius = dotRadius,
                    center = Offset(center.x, center.y + dotRadius * 0.35f),
                )
            }
            drawCircle(baseColor, radius = dotRadius, center = center)
        } else {
            if (mouldedShadow) {
                drawCircle(Color.Black.copy(alpha = 0.20f), radius = dotRadius * 0.88f, center = center)
            }
            drawCircle(baseColor.copy(alpha = 0.26f), radius = dotRadius * 0.85f, center = center)
        }
    }
}

/** Draws [glyph] centred in this canvas, in strokes like the rest of Arcadune's icons. */
private fun DrawScope.drawGlyph(
    glyph: Glyph,
    color: Color,
) {
    val s = minOf(size.width, size.height)
    val c = Offset(size.width / 2, size.height / 2)
    val w = s * 0.11f
    val stroke =
        Stroke(
            width = w,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )

    fun p(
        x: Float,
        y: Float,
    ) = Offset(c.x + x * s, c.y + y * s)
    when (glyph) {
        Glyph.MENU ->
            for (y in listOf(
                -0.22f,
                0f,
                0.22f,
            )) drawLine(color, p(-0.3f, y), p(0.3f, y), w, androidx.compose.ui.graphics.StrokeCap.Round)
        Glyph.REWIND -> {
            for (dx in listOf(-0.3f, 0.02f)) {
                drawPath(
                    Path().apply {
                        moveTo(p(dx + 0.28f, -0.24f).x, p(0f, -0.24f).y)
                        lineTo(p(dx + 0.28f, 0.24f).x, p(0f, 0.24f).y)
                        lineTo(p(dx, 0f).x, c.y)
                        close()
                    },
                    color,
                )
            }
        }
        Glyph.MIC -> {
            drawRoundRect(color, p(-0.13f, -0.42f), Size(s * 0.26f, s * 0.48f), CornerRadius(s * 0.13f))
            drawArc(color, 0f, 180f, false, p(-0.28f, -0.18f), Size(s * 0.56f, s * 0.42f), style = stroke)
            drawLine(color, p(0f, 0.24f), p(0f, 0.42f), w, androidx.compose.ui.graphics.StrokeCap.Round)
        }
        Glyph.LID -> {
            // A clamshell closing: the base, and the lid coming down onto it.
            drawLine(color, p(-0.4f, 0.3f), p(0.4f, 0.3f), w, androidx.compose.ui.graphics.StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(p(-0.36f, 0.3f).x, p(0f, 0.3f).y)
                    lineTo(p(-0.24f, -0.3f).x, p(0f, -0.3f).y)
                    lineTo(p(0.24f, -0.3f).x, p(0f, -0.3f).y)
                    lineTo(p(0.36f, 0.3f).x, p(0f, 0.3f).y)
                },
                color,
                style = stroke,
            )
            drawLine(color, p(-0.1f, 0f), p(0.1f, 0f), w, androidx.compose.ui.graphics.StrokeCap.Round)
        }
        Glyph.FAST_FORWARD -> {
            // Two triangles pointing right.
            for (dx in listOf(-0.3f, 0.02f)) {
                val tip = p(dx + 0.28f, 0f)
                val path =
                    Path().apply {
                        moveTo(p(dx, -0.24f).x, p(0f, -0.24f).y)
                        lineTo(tip.x, tip.y)
                        lineTo(p(dx, 0.24f).x, p(0f, 0.24f).y)
                        close()
                    }
                drawPath(path, color)
            }
        }
        Glyph.QUICK_SAVE, Glyph.QUICK_LOAD -> {
            // A tray, and an arrow going down into it (save) or up out of it (load).
            drawLine(color, p(-0.34f, 0.18f), p(-0.34f, 0.36f), w, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, p(-0.34f, 0.36f), p(0.34f, 0.36f), w, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, p(0.34f, 0.36f), p(0.34f, 0.18f), w, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, p(0f, -0.38f), p(0f, 0.16f), w, androidx.compose.ui.graphics.StrokeCap.Round)
            val (tipY, backY) = if (glyph == Glyph.QUICK_SAVE) 0.16f to -0.04f else -0.38f to -0.18f
            drawPath(
                Path().apply {
                    moveTo(p(-0.18f, backY).x, p(0f, backY).y)
                    lineTo(p(0f, tipY).x, p(0f, tipY).y)
                    lineTo(p(0.18f, backY).x, p(0f, backY).y)
                },
                color,
                style = stroke,
            )
        }
        Glyph.SWAP -> {
            // Two screens trading places, with curved arrows between them.
            drawRoundRect(color, p(-0.42f, -0.42f), Size(s * 0.4f, s * 0.3f), CornerRadius(s * 0.06f), style = stroke)
            drawRoundRect(color, p(0.02f, 0.12f), Size(s * 0.4f, s * 0.3f), CornerRadius(s * 0.06f), style = stroke)
            drawArc(color, -90f, 90f, false, p(-0.04f, -0.36f), Size(s * 0.4f, s * 0.36f), style = stroke)
            drawArc(color, 90f, 90f, false, p(-0.36f, 0f), Size(s * 0.4f, s * 0.36f), style = stroke)
        }
        Glyph.EXIT -> {
            // Rounded doorway bracket on the left + crisp outward arrow on the right, proportioned to MENU / REWIND.
            val r = 0.08f
            drawPath(
                Path().apply {
                    moveTo(p(-0.05f, -0.26f).x, p(0f, -0.26f).y)
                    lineTo(p(-0.29f + r, -0.26f).x, p(0f, -0.26f).y)
                    quadraticBezierTo(
                        p(-0.29f, -0.26f).x,
                        p(0f, -0.26f).y,
                        p(-0.29f, -0.26f + r).x,
                        p(0f, -0.26f + r).y,
                    )
                    lineTo(p(-0.29f, 0.26f - r).x, p(0f, 0.26f - r).y)
                    quadraticBezierTo(
                        p(-0.29f, 0.26f).x,
                        p(0f, 0.26f).y,
                        p(-0.29f + r, 0.26f).x,
                        p(0f, 0.26f).y,
                    )
                    lineTo(p(-0.05f, 0.26f).x, p(0f, 0.26f).y)
                },
                color,
                style = stroke,
            )
            drawLine(color, p(-0.08f, 0f), p(0.29f, 0f), w, androidx.compose.ui.graphics.StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(p(0.12f, -0.17f).x, p(0f, -0.17f).y)
                    lineTo(p(0.29f, 0f).x, p(0f, 0f).y)
                    lineTo(p(0.12f, 0.17f).x, p(0f, 0.17f).y)
                },
                color,
                style = stroke,
            )
        }
    }
}

private fun DrawScope.drawMonoButton(
    shape: ButtonShape,
    pressed: Boolean,
) {
    val radius =
        when (shape) {
            ButtonShape.ROUND, ButtonShape.PILL -> CornerRadius(size.minDimension / 2)
            ButtonShape.SHOULDER -> CornerRadius(size.height * 0.45f)
        }
    val ring = size.minDimension * if (shape == ButtonShape.ROUND) 0.06f else 0.12f
    if (!pressed) {
        drawRoundRect(Mono.Shadow, Offset(0f, size.minDimension * 0.06f), size, radius)
    }
    drawRoundRect(Mono.Ring, cornerRadius = radius)
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                if (pressed) {
                    listOf(
                        Mono.ButtonPressedTop,
                        Mono.ButtonBottom,
                    )
                } else {
                    listOf(Mono.ButtonTop, Mono.ButtonBottom)
                },
            ),
        topLeft = Offset(ring, ring),
        size = Size(size.width - 2 * ring, size.height - 2 * ring),
        cornerRadius = CornerRadius((radius.x - ring).coerceAtLeast(0f)),
    )
}

/** A button in a console's colours: raised plastic with a darker rim, darker while pressed. */
private fun DrawScope.drawConsoleButton(
    shape: ButtonShape,
    fill: Color,
    pressed: Boolean,
) {
    val radius =
        when (shape) {
            ButtonShape.ROUND, ButtonShape.PILL -> CornerRadius(size.minDimension / 2)
            ButtonShape.SHOULDER -> CornerRadius(size.height * 0.45f)
        }
    // The face leaves room under it for the button's side, and sinks onto it while pressed.
    val depth = size.minDimension * 0.05f
    val sink = if (pressed) depth * 0.7f else 0f
    val face = Size(size.width - 2 * depth, size.height - 2 * depth)
    val topLeft = Offset(depth, depth * 0.5f + sink)
    val corner = minOf(radius.x, face.minDimension / 2)
    val faceBottom = topLeft.y + face.height
    drawRaised(
        { grow -> roundedBox(topLeft, face, corner, grow) },
        fill,
        pressed,
        size.minDimension,
        topLeft.y,
        faceBottom,
    )
}

/** Triangle, circle, cross or square, as outlines like on the real buttons. */
private fun DrawScope.drawPlayStationSymbol(
    symbol: String,
    color: Color,
) {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val stroke =
        Stroke(
            width = s * 0.13f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
    when (symbol) {
        "△" ->
            drawPath(
                Path().apply {
                    moveTo(c.x, c.y - s * 0.42f)
                    lineTo(c.x + s * 0.46f, c.y + s * 0.36f)
                    lineTo(c.x - s * 0.46f, c.y + s * 0.36f)
                    close()
                },
                color,
                style = stroke,
            )
        "○" -> drawCircle(color, s * 0.42f, c, style = stroke)
        "✕" -> {
            val d = s * 0.38f
            drawLine(
                color,
                c + Offset(-d, -d),
                c + Offset(d, d),
                stroke.width,
                androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawLine(
                color,
                c + Offset(d, -d),
                c + Offset(-d, d),
                stroke.width,
                androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        "□" -> drawRect(color, c - Offset(s * 0.38f, s * 0.38f), Size(s * 0.76f, s * 0.76f), style = stroke)
    }
}

private fun DrawScope.drawNesFaceButton(pressed: Boolean) {
    drawRoundRect(Color(0xFFE4E4E6), cornerRadius = CornerRadius(size.minDimension * 0.16f))
    drawRoundRect(
        Color(0x22000000),
        cornerRadius = CornerRadius(size.minDimension * 0.16f),
        style = Stroke(size.minDimension * 0.02f),
    )
    val r = size.minDimension * 0.37f
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(Color(0x55000000), r, c + Offset(r * 0.08f, r * 0.12f))
    drawCircle(Color(0xFF2A0000), r, c)
    drawCircle(
        Brush.verticalGradient(
            if (pressed) listOf(Nes.RedDark, Nes.RedDark) else listOf(Nes.Red, Nes.RedDark),
            c.y - r,
            c.y + r,
        ),
        r * 0.94f,
        c,
    )
}

private fun DrawScope.drawNesSmallButton(pressed: Boolean) {
    val r = size.minDimension / 2
    drawCircle(Color(0xFF9A9A9D), r)
    drawCircle(if (pressed) Color(0xFFBDBDBF) else Color(0xFFF2F2F4), r * 0.86f)
}

private fun DrawScope.drawNesSmallPill(pressed: Boolean) {
    val radius = CornerRadius(size.minDimension / 2)
    val rim = (size.minDimension * 0.10f).coerceAtLeast(1.2f)
    if (!pressed) {
        drawRoundRect(Mono.Shadow, Offset(0f, size.minDimension * 0.06f), size, radius)
    }
    // Crisp NES silver-grey bezel ring so the pill stands out cleanly even when the dark NES deck is raised.
    drawRoundRect(Color(0xFFB8B8BE), cornerRadius = radius)
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                if (pressed) {
                    listOf(Mono.ButtonPressedTop, Mono.ButtonBottom)
                } else {
                    listOf(Mono.ButtonTop, Mono.ButtonBottom)
                },
            ),
        topLeft = Offset(rim, rim),
        size = Size((size.width - 2 * rim).coerceAtLeast(1f), (size.height - 2 * rim).coerceAtLeast(1f)),
        cornerRadius = CornerRadius((radius.x - rim).coerceAtLeast(0f)),
    )
}

private fun DrawScope.drawOutlineButton(
    shape: ButtonShape,
    pressed: Boolean,
) {
    val line = 2.dp.toPx()
    val radius =
        when (shape) {
            ButtonShape.ROUND, ButtonShape.PILL -> CornerRadius(size.minDimension / 2)
            ButtonShape.SHOULDER -> CornerRadius(size.height * 0.45f)
        }
    if (pressed) drawRoundRect(Outline.Pressed, cornerRadius = radius)
    drawRoundRect(
        Outline.Line,
        Offset(line / 2, line / 2),
        Size(size.width - line, size.height - line),
        CornerRadius((radius.x - line / 2).coerceAtLeast(0f)),
        style = Stroke(line),
    )
}

private fun DrawScope.drawOverlayButton(
    shape: ButtonShape,
    pressed: Boolean,
    tint: Color? = null,
) {
    val radius =
        when (shape) {
            ButtonShape.ROUND, ButtonShape.PILL -> CornerRadius(size.minDimension / 2)
            ButtonShape.SHOULDER -> CornerRadius(size.height * 0.45f)
        }
    val fill =
        when {
            tint != null -> Overlay.tinted(tint, pressed)
            pressed -> Overlay.FillPressed
            else -> Overlay.Fill
        }
    drawRoundRect(fill, cornerRadius = radius)
    drawRoundRect(Overlay.Edge, cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
}

/** The name written under a control, like MENU / SELECT / START under the pills. */
@Composable
private fun Caption(
    layout: SkinLayout,
    control: SkinControl,
    frame: SkinRect,
    x: Float,
    y: Float,
    scale: Float,
) {
    if (layout.captionless && (control is SkinControl.Menu || control is SkinControl.Rewind)) return
    val style = layout.style
    // Sideways over the game the controls carry no names, except the four extra buttons: they read like upright.
    val extraButton = control.isExtraButton()
    if (style == SkinStyle.OVERLAY && !extraButton) return
    val text =
        when (control) {
            is SkinControl.Button -> control.label.takeIf { control.labelBelow }
            is SkinControl.Menu -> "MENU"
            is SkinControl.Rewind -> "REWIND"
            is SkinControl.Dpad, is SkinControl.Stick -> null
        } ?: return

    val nes = style == SkinStyle.NES_CLASSIC
    val isFace = control is SkinControl.Button && control.shape == ButtonShape.ROUND
    val fontUnits = (if (nes && isFace) 13f else 8.5f) * ((control as? SkinControl.Button)?.captionScale ?: 1f)
    val width = maxOf(frame.width, 60f)

    // Where the name goes: under the control (on the NES deck, small buttons share one baseline).
    val (left, top, align) =
        when {
            nes && !isFace && !control.isExtraButton() && control !is SkinControl.Menu && control !is SkinControl.Rewind ->
                Triple(
                    x + (frame.width - width) / 2 * scale,
                    y + (244f - frame.y) * scale,
                    TextAlign.Center,
                )
            nes -> Triple(x + (frame.width - width) / 2 * scale, y + (frame.height + 3) * scale, TextAlign.Center)
            else -> Triple(x + (frame.width - width) / 2 * scale, y + (frame.height + 4) * scale, TextAlign.Center)
        }

    val color =
        when {
            nes && (control.isExtraButton() || control is SkinControl.Menu || control is SkinControl.Rewind) -> Color.White
            style == SkinStyle.OVERLAY -> Color.White
            nes -> Nes.Label
            style == SkinStyle.CONSOLE || style == SkinStyle.OUTLINE -> layout.paletteOrMono().caption
            else -> Mono.Caption
        }
    val captionShadow =
        if ((nes && (control.isExtraButton() || control is SkinControl.Menu || control is SkinControl.Rewind)) || style == SkinStyle.OVERLAY) {
            androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1f), 2f)
        } else {
            null
        }
    val fontSize = with(LocalDensity.current) { (fontUnits * scale).dp.toSp() }
    Box(
        modifier =
            Modifier
                .absoluteOffset { IntOffset(left.dp.roundToPx(), top.dp.roundToPx()) }
                .size((width * scale).dp, (fontUnits * 1.6f * scale).dp),
    ) {
        Text(
            // Wide but not height-bound: a large system font makes the line taller, and a fixed box clipped it.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true),
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = if (style == SkinStyle.CONSOLE) FontWeight.Bold else FontWeight.Black,
            letterSpacing = with(LocalDensity.current) { (0.6f * scale).dp.toSp() },
            textAlign = align,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(lineHeight = fontSize * 1.2f, shadow = captionShadow),
        )
    }
}
