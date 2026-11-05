package com.swordfish.touchinput.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * The player's changes to a skin: one size for every control, and how far each control was
 * dragged from its place (in skin units, by the control's position in the layout).
 */
data class SkinCustomization(
    val size: Float = 1f,
    val offsets: Map<Int, Offset> = emptyMap(),
    /** How see-through the controls are: 1 is solid. */
    val opacity: Float = 1f,
) {
    fun frameOf(
        index: Int,
        frame: SkinRect,
    ): SkinRect {
        val offset = offsets[index] ?: Offset.Zero
        val width = frame.width * size
        val height = frame.height * size
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
            offsets.forEach { (index, offset) -> append(";$index:${offset.x}:${offset.y}") }
        }

    companion object {
        const val MIN_SIZE = 0.75f
        const val MAX_SIZE = 1.35f
        const val MIN_OPACITY = 0.2f

        fun decode(text: String?): SkinCustomization {
            if (text.isNullOrBlank()) return SkinCustomization()
            return runCatching {
                val parts = text.split(';')
                val (settings, moves) = parts.drop(1).partition { it.startsWith("o=") }
                val offsets =
                    moves.associate { part ->
                        val (index, x, y) = part.split(':')
                        index.toInt() to Offset(x.toFloat(), y.toFloat())
                    }
                val opacity = settings.firstOrNull()?.removePrefix("o=")?.toFloat() ?: 1f
                SkinCustomization(
                    parts.first().toFloat().coerceIn(MIN_SIZE, MAX_SIZE),
                    offsets,
                    opacity.coerceIn(MIN_OPACITY, 1f),
                )
            }.getOrDefault(SkinCustomization())
        }
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
) {
    val origin = remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.onGloballyPositioned { origin.value = it.boundsInRoot().topLeft }) {
        val density = LocalDensity.current.density
        val geometry = SkinGeometry(layout, pinToEdges, maxWidth.value, maxHeight.value)
        val rects = geometry.touchRects(customization, rewindAvailable)
        val topLeft = origin.value
        // Where each control takes touches, in root pixels, for the touch tracker.
        val targets =
            rects.map { (index, rect) ->
                val control = layout.controls[index]
                SkinInput.Target(
                    index = index,
                    control = control,
                    area =
                        androidx.compose.ui.geometry.Rect(
                            topLeft.x + rect.left * density,
                            topLeft.y + rect.top * density,
                            topLeft.x + rect.right * density,
                            topLeft.y + rect.bottom * density,
                        ),
                    round = control.takesRoundTouches(),
                )
            }
        SideEffect { input.targets = targets }
        DisposableEffect(input) {
            onDispose {
                input.releaseAll()
                input.targets = emptyList()
            }
        }

        SkinFrame(
            Modifier.fillMaxSize().graphicsLayer { alpha = customization.opacity },
            layout,
            pinToEdges,
            rewindAvailable,
            customization,
            drawBody = !shell,
            art = art,
        ) { index, control, frame, placement, scale ->
            val inset = (control.reach * scale).dp
            Box(modifier = placement) {
                when (control) {
                    is SkinControl.Dpad -> {
                        val art = LocalSkinArt.current?.part(control, pressed = false)
                        val pad = ((control.reach + ART_BLEED) * scale).dp
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
                            translate((size.width - knob) / 2 + direction.x * travel, (size.height - knob) / 2 - direction.y * travel) {
                                drawStickKnob(layout, direction != Offset.Zero, knob)
                            }
                        }

                    else -> {
                        val pressed = remember(index) { derivedStateOf { input.pressed[index] == true } }
                        ControlGlyph(layout, control, frame, pressed, inset, scale, pinToEdges)
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

/**
 * Edits the layout: every control can be dragged, nothing is sent to the game. [onMove] gets the
 * control's index and how far it moved, in skin units.
 *
 * The drag is followed by the whole editor area, which never moves: it finds the control under
 * the finger when the drag starts and keeps moving that one. (A control listening to its own drag
 * loses the finger as soon as it moves under it.)
 */
@Composable
fun ArcaduneSkinEditor(
    modifier: Modifier = Modifier,
    layout: SkinLayout,
    pinToEdges: Boolean,
    rewindAvailable: Boolean,
    customization: SkinCustomization,
    onMove: (Int, Offset) -> Unit,
    /** True when a [ConsoleShell] is drawn behind: the panel then has no background of its own. */
    shell: Boolean = false,
    art: SkinArt? = null,
    /**
     * Room above the controls, inside this editor's own area, for the controls that sit above the
     * panel (the DS L and R tabs): a touch outside an element's bounds never reaches it, so without
     * this they could not be dragged. The caller makes the area this much taller and higher.
     */
    topExtra: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val currentOnMove by rememberUpdatedState(onMove)
    val notPressed = remember { mutableStateOf(false) }
    val dragging = remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val geometry = SkinGeometry(layout, pinToEdges, maxWidth.value, (maxHeight - topExtra).value)
        val rects by rememberUpdatedState(geometry.touchRects(customization, rewindAvailable))
        val scale by rememberUpdatedState(geometry.scale)

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(layout, pinToEdges) {
                        // Handled by hand and before anything else (Initial pass), so no other
                        // part of the game screen can take the touch first.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val start = Offset(down.position.x / density, down.position.y / density - topExtra.value)
                            // The smallest control under the finger wins (a button inside the d-pad's reach).
                            val index =
                                rects
                                    .filter { (_, rect) -> rect.contains(start) }
                                    .minByOrNull { (_, rect) -> rect.width * rect.height }
                                    ?.first
                                    ?: return@awaitEachGesture
                            down.consume()
                            dragging.value = index
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.position - change.previousPosition
                                change.consume()
                                if (delta != Offset.Zero) {
                                    currentOnMove(index, Offset(delta.x / density / scale, delta.y / density / scale))
                                }
                            }
                            dragging.value = null
                        }
                    },
        ) {
            SkinFrame(
                Modifier.fillMaxSize().padding(top = topExtra).graphicsLayer { alpha = customization.opacity },
                layout,
                pinToEdges,
                rewindAvailable,
                customization,
                drawBody = !shell,
                art = art,
            ) { index, control, frame, placement, scale ->
                val inset = (control.reach * scale).dp
                val active = dragging.value == index
                Box(
                    modifier =
                        placement
                            .padding(inset / 2)
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) Color(0xFFB57BFF) else Color(0x99FFFFFF),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(inset / 2),
                ) {
                    StaticControl(layout, control, frame, notPressed, scale, pinToEdges)
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
) {
    val notPressed = remember { mutableStateOf(false) }
    val frameModifier = modifier.graphicsLayer { alpha = customization.opacity }
    SkinFrame(
        frameModifier,
        layout,
        pinToEdges,
        rewindAvailable = true,
        customization = customization,
        drawBody = !shell,
        art = art,
    ) { _, control, frame, placement, scale ->
        Box(modifier = placement.padding((control.reach * scale).dp)) {
            StaticControl(layout, control, frame, notPressed, scale, pinToEdges)
        }
    }
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
) {
    when (control) {
        is SkinControl.Dpad -> {
            val art = LocalSkinArt.current?.part(control, pressed = false)
            val pad = ((control.reach + ART_BLEED) * scale).dp
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
        else -> ControlGlyph(layout, control, frame, notPressed, 0.dp, scale, pinned)
    }
}

/**
 * The narrowest a full-screen landscape skin is laid out, in skin units: room for the two corner
 * clusters and the small buttons between them. Narrower screens shrink the controls to fit.
 */
internal const val FULL_SCREEN_MIN_WIDTH = 600f

/** The most dp per skin unit a full-screen landscape skin grows to (on tablets). */
internal const val FULL_SCREEN_MAX_SCALE = 1.2f

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

    /** Top of [frame]: in landscape the top and bottom controls stay at the screen's top and bottom edges. */
    fun placeY(frame: SkinRect): Float {
        // Upright the panel always has the skin's own proportions: the controls keep their designed spacing.
        if (!pinToEdges) return frame.y * scale
        // Full screen: the corner clusters (a whole diamond, two rows of buttons) stay in one piece.
        if (layout.screenArea == null) {
            val atTop = frame.centerY < layout.height / 2
            return if (atTop) frame.y * scale else height - (layout.height - frame.y) * scale
        }
        return when {
            frame.centerY < layout.height * 0.37f -> frame.y * scale
            frame.centerY > layout.height * 0.63f -> height - (layout.height - frame.y) * scale
            else -> height / 2 + (frame.y - layout.height / 2) * scale
        }
    }

    fun placeX(frame: SkinRect): Float {
        if (!pinToEdges) return frame.x * scale
        return when {
            frame.centerX < layout.width * 0.37f -> frame.x * scale
            frame.centerX > layout.width * 0.63f -> width - (layout.width - frame.x) * scale
            else -> width / 2 + (frame.x - layout.width / 2) * scale
        }
    }

    /** Each control's touch area (with its reach), in dp, by index. */
    fun touchRects(
        customization: SkinCustomization,
        rewindAvailable: Boolean,
    ): List<Pair<Int, androidx.compose.ui.geometry.Rect>> = rects(customization, rewindAvailable, withReach = true)

    /** Each control as drawn (without its reach), in dp, by index. */
    fun frameRects(
        customization: SkinCustomization,
        rewindAvailable: Boolean,
    ): List<Pair<Int, androidx.compose.ui.geometry.Rect>> = rects(customization, rewindAvailable, withReach = false)

    private fun rects(
        customization: SkinCustomization,
        rewindAvailable: Boolean,
        withReach: Boolean,
    ): List<Pair<Int, androidx.compose.ui.geometry.Rect>> =
        layout.controls.mapIndexedNotNull { index, item ->
            if (item is SkinControl.Rewind && !rewindAvailable) return@mapIndexedNotNull null
            val frame = customization.frameOf(index, item.frame)
            val reach = if (withReach) item.reach else 0f
            val left = placeX(frame) - reach * scale
            val top = placeY(frame) - reach * scale
            index to
                androidx.compose.ui.geometry.Rect(
                    left,
                    top,
                    left + (frame.width + 2 * reach) * scale,
                    top + (frame.height + 2 * reach) * scale,
                )
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
    control: @Composable BoxWithConstraintsScope.(index: Int, control: SkinControl, frame: SkinRect, placement: Modifier, scale: Float) -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSkinArt provides art) {
        BoxWithConstraints(modifier = modifier) {
            val geometry = SkinGeometry(layout, pinToEdges, maxWidth.value, maxHeight.value)
            val scale = geometry.scale

            fun placeX(frame: SkinRect): Float = geometry.placeX(frame)

            // The NES pad keeps its own black face even inside a shell.
            if (!pinToEdges && (drawBody || layout.style == SkinStyle.NES_CLASSIC)) {
                Canvas(Modifier.fillMaxSize()) { drawSkinBody(layout, scale * density) }
            }
            // The body's designed overlay over the panel: the plastic's grain, then sheen, wordmark, grille.
            val bodyArt = art?.body
            if (bodyArt != null && !pinToEdges) {
                val grain = art.grain
                Canvas(Modifier.fillMaxSize()) {
                    if (grain != null) {
                        val tile =
                            androidx.compose.ui.graphics.ShaderBrush(
                                androidx.compose.ui.graphics.ImageShader(
                                    grain,
                                    androidx.compose.ui.graphics.TileMode.Repeated,
                                    androidx.compose.ui.graphics.TileMode.Repeated,
                                ),
                            )
                        drawRect(tile, alpha = 0.06f, blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
                    }
                    drawArtPart(bodyArt, 0f)
                }
            }

            layout.controls.forEachIndexed { index, item ->
                if (item is SkinControl.Rewind && !rewindAvailable) return@forEachIndexed
                // Each control keeps its identity by index, so a control that comes or goes (rewind)
                // never makes the ones after it start over.
                androidx.compose.runtime.key(index) {
                    val frame = customization.frameOf(index, item.frame)
                    val reach = item.reach
                    val x = placeX(frame) - reach * scale
                    val y = geometry.placeY(frame) - reach * scale
                    val placement =
                        Modifier
                            .absoluteOffset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }
                            .size(((frame.width + 2 * reach) * scale).dp, ((frame.height + 2 * reach) * scale).dp)
                    control(index, item, frame, placement, scale)
                    // Sideways there is no room under the controls: names go on them instead.
                    if (!pinToEdges) Caption(layout, item, frame, placeX(frame), geometry.placeY(frame), scale)
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
            fun rect(
                x: Float,
                y: Float,
                w: Float,
                h: Float,
                color: Color,
                radius: Float,
            ) = drawRoundRect(color, Offset(x * unit, y * unit), Size(w * unit, h * unit), CornerRadius(radius * unit))
            rect(11f, 33f, 298f, 230f, Nes.Pad, 12f)
            for ((top, bottom) in listOf(33f to 56f, 67f to 97f, 109f to 140f, 151f to 181f)) {
                rect(93f, top, 134f, bottom - top, Nes.Stripe, 6f)
            }
            rect(93f, 192f, 134f, 36f, Nes.WhiteBox, 4f)
            rect(93f, 239f, 134f, 24f, Nes.CaptionStrip, 6f)
        }

        SkinStyle.OVERLAY -> Unit
    }
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
    drawCircle(Brush.verticalGradient(listOf(palette.stickBase.shade(0.1f), palette.stickBase), c.y - r, c.y + r), r * 0.9f, c)
    drawCircle(palette.dpadMark, r * 0.9f, c, style = Stroke(r * 0.03f))
    // The shaft, then the ball with a highlight, like light on a glossy ball.
    drawLine(Color(0xFF6A6A70), c, lean, r * 0.14f, androidx.compose.ui.graphics.StrokeCap.Round)
    val ball = palette.stickKnob
    drawCircle(Color(0x55000000), r * 0.4f, lean + Offset(0f, r * 0.06f))
    drawCircle(
        Brush.radialGradient(listOf(ball.shade(0.35f), ball, ball.shade(-0.35f)), lean - Offset(r * 0.12f, r * 0.14f), r * 0.5f),
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
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }, color)
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
    if (direction.y < -0.3f) drawRect(pressed, Offset(left + arm + inner, top + inner), Size(arm - 2 * inner, arm - inner))
    if (direction.y > 0.3f) drawRect(pressed, Offset(left + arm + inner, top + 2 * arm), Size(arm - 2 * inner, arm - inner))
    if (direction.x < -0.3f) drawRect(pressed, Offset(left + inner, top + arm + inner), Size(arm - inner, arm - 2 * inner))
    if (direction.x > 0.3f) drawRect(pressed, Offset(left + 2 * arm, top + arm + inner), Size(arm - inner, arm - 2 * inner))

    drawArrows(left, top, arm, Mono.Emboss, direction)
    drawCircle(Color(0xFF0B0B0C), arm * 0.3f, Offset(left + full / 2, top + full / 2))
    drawCircle(Mono.Emboss.copy(alpha = 0.6f), arm * 0.3f, Offset(left + full / 2, top + full / 2), style = Stroke(arm * 0.04f))
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
    ) = drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }, if (lit) PressedMark else color)
    triangle(direction.y < -0.3f, Offset(c.x, c.y - far), Offset(c.x - half, c.y - near), Offset(c.x + half, c.y - near))
    triangle(direction.y > 0.3f, Offset(c.x, c.y + far), Offset(c.x - half, c.y + near), Offset(c.x + half, c.y + near))
    triangle(direction.x < -0.3f, Offset(c.x - far, c.y), Offset(c.x - near, c.y - half), Offset(c.x - near, c.y + half))
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
        val stroke = Stroke(width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
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
        modifier = Modifier.fillMaxSize().padding(inset).graphicsLayer { scaleX = sink; scaleY = sink },
        contentAlignment = Alignment.Center,
    ) {
        val shape =
            when (control) {
                is SkinControl.Button -> control.shape
                is SkinControl.Menu, is SkinControl.Rewind ->
                    if (style == SkinStyle.NES_CLASSIC || frame.width < frame.height * 1.3f) ButtonShape.ROUND else ButtonShape.PILL
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
            val reachPad = (control.reach + ART_BLEED) * scale
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
                    style == SkinStyle.NES_CLASSIC && (control is SkinControl.Menu || control is SkinControl.Rewind) ->
                        drawNesSmallButton(isPressed)
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
        if (glyph != null && style != SkinStyle.NES_CLASSIC) {
            val coloured = style == SkinStyle.CONSOLE || style == SkinStyle.OVERLAY || style == SkinStyle.OUTLINE
            val glyphColor = if (coloured) labelColor else Mono.Glyph
            Canvas(Modifier.fillMaxSize(if (shape == ButtonShape.PILL) 0.7f else 0.56f)) { drawGlyph(glyph, glyphColor) }
        }
        if (onButton != null && onButton in PlayStationSymbols) {
            val overlaySymbol = key?.let { palette?.labelOf(it) } ?: Overlay.Glyph
            val symbolColor = if (style == SkinStyle.OVERLAY) overlaySymbol else labelColor
            Canvas(Modifier.fillMaxSize(0.46f)) { drawPlayStationSymbol(onButton, symbolColor) }
        } else if (onButton != null && !(art != null && artCarriesLabel(control))) {
            val unitDp = scale
            val fontSize = with(LocalDensity.current) { (minOf(frame.width, frame.height) * unitDp * if (control is SkinControl.Button && control.shape == ButtonShape.PILL) 0.42f else 0.46f).dp.toSp() }
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
private enum class Glyph { MENU, REWIND, MIC, LID, SWAP, FAST_FORWARD, QUICK_SAVE, QUICK_LOAD }

/** Draws [glyph] centred in this canvas, in strokes like the rest of Arcadune's icons. */
private fun DrawScope.drawGlyph(
    glyph: Glyph,
    color: Color,
) {
    val s = minOf(size.width, size.height)
    val c = Offset(size.width / 2, size.height / 2)
    val w = s * 0.11f
    val stroke = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    fun p(
        x: Float,
        y: Float,
    ) = Offset(c.x + x * s, c.y + y * s)
    when (glyph) {
        Glyph.MENU ->
            for (y in listOf(-0.22f, 0f, 0.22f)) drawLine(color, p(-0.3f, y), p(0.3f, y), w, androidx.compose.ui.graphics.StrokeCap.Round)
        Glyph.REWIND -> {
            for (dx in listOf(-0.3f, 0.02f)) {
                drawPath(Path().apply { moveTo(p(dx + 0.28f, -0.24f).x, p(0f, -0.24f).y); lineTo(p(dx + 0.28f, 0.24f).x, p(0f, 0.24f).y); lineTo(p(dx, 0f).x, c.y); close() }, color)
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
            drawPath(Path().apply { moveTo(p(-0.36f, 0.3f).x, p(0f, 0.3f).y); lineTo(p(-0.24f, -0.3f).x, p(0f, -0.3f).y); lineTo(p(0.24f, -0.3f).x, p(0f, -0.3f).y); lineTo(p(0.36f, 0.3f).x, p(0f, 0.3f).y) }, color, style = stroke)
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
                if (pressed) listOf(Mono.ButtonPressedTop, Mono.ButtonBottom) else listOf(Mono.ButtonTop, Mono.ButtonBottom),
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
    drawRaised({ grow -> roundedBox(topLeft, face, corner, grow) }, fill, pressed, size.minDimension, topLeft.y, faceBottom)
}

/** Triangle, circle, cross or square, as outlines like on the real buttons. */
private fun DrawScope.drawPlayStationSymbol(
    symbol: String,
    color: Color,
) {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val stroke = Stroke(width = s * 0.13f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
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
            drawLine(color, c + Offset(-d, -d), c + Offset(d, d), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, c + Offset(d, -d), c + Offset(-d, d), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
        }
        "□" -> drawRect(color, c - Offset(s * 0.38f, s * 0.38f), Size(s * 0.76f, s * 0.76f), style = stroke)
    }
}

private fun DrawScope.drawNesFaceButton(pressed: Boolean) {
    drawRoundRect(Color(0xFFE4E4E6), cornerRadius = CornerRadius(size.minDimension * 0.16f))
    drawRoundRect(Color(0x22000000), cornerRadius = CornerRadius(size.minDimension * 0.16f), style = Stroke(size.minDimension * 0.02f))
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
    val style = layout.style
    if (style == SkinStyle.OVERLAY) return
    val text =
        when (control) {
            is SkinControl.Button -> control.label.takeIf { control.labelBelow }
            is SkinControl.Menu -> "MENU"
            is SkinControl.Rewind -> "REWIND"
            is SkinControl.Dpad, is SkinControl.Stick -> null
        } ?: return

    val nes = style == SkinStyle.NES_CLASSIC
    val isFace = control is SkinControl.Button && control.shape == ButtonShape.ROUND
    val fontUnits = if (nes && isFace) 13f else 8.5f
    val width = maxOf(frame.width, 60f)

    // Where the name goes: under the control, or (NES menu / rewind) beside it.
    val (left, top, align) =
        when {
            nes && control is SkinControl.Menu -> Triple(x - (width + 4) * scale, y + (frame.height / 2 - fontUnits * 0.7f) * scale, TextAlign.End)
            nes && control is SkinControl.Rewind -> Triple(x + (frame.width + 4) * scale, y + (frame.height / 2 - fontUnits * 0.7f) * scale, TextAlign.Start)
            nes && !isFace -> Triple(x + (frame.width - width) / 2 * scale, y + (244f - frame.y) * scale, TextAlign.Center)
            nes -> Triple(x + (frame.width - width) / 2 * scale, y + (frame.height + 3) * scale, TextAlign.Center)
            else -> Triple(x + (frame.width - width) / 2 * scale, y + (frame.height + 4) * scale, TextAlign.Center)
        }

    val color =
        when {
            nes && (control is SkinControl.Menu || control is SkinControl.Rewind) -> Color(0xFFEDEDEF)
            nes -> Nes.Label
            style == SkinStyle.CONSOLE || style == SkinStyle.OUTLINE -> layout.paletteOrMono().caption
            else -> Mono.Caption
        }
    val fontSize = with(LocalDensity.current) { (fontUnits * scale).dp.toSp() }
    Box(
        modifier =
            Modifier
                .absoluteOffset { IntOffset(left.dp.roundToPx(), top.dp.roundToPx()) }
                .size((width * scale).dp, (fontUnits * 1.6f * scale).dp),
    ) {
        Text(
            modifier = Modifier.fillMaxSize(),
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = if (style == SkinStyle.CONSOLE) FontWeight.Bold else FontWeight.Black,
            letterSpacing = with(LocalDensity.current) { (0.6f * scale).dp.toSp() },
            textAlign = align,
            maxLines = 1,
            softWrap = false,
        )
    }
}
