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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
        ) { index, control, frame, placement, scale ->
            val inset = (control.reach * scale).dp
            Box(modifier = placement) {
                when (control) {
                    is SkinControl.Dpad ->
                        Canvas(Modifier.fillMaxSize().padding(inset)) {
                            drawAnyDpad(layout, control.look, input.directions[index] ?: Offset.Zero)
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
) {
    val currentOnMove by rememberUpdatedState(onMove)
    val notPressed = remember { mutableStateOf(false) }
    val dragging = remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val geometry = SkinGeometry(layout, pinToEdges, maxWidth.value, maxHeight.value)
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
                            val start = Offset(down.position.x / density, down.position.y / density)
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
                Modifier.fillMaxSize().graphicsLayer { alpha = customization.opacity },
                layout,
                pinToEdges,
                rewindAvailable,
                customization,
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
                    StaticControl(layout, control, frame, notPressed, scale)
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
) {
    val notPressed = remember { mutableStateOf(false) }
    SkinFrame(modifier, layout, pinToEdges, rewindAvailable = true, customization = SkinCustomization(), drawBody = !shell) { _, control, frame, placement, scale ->
        Box(modifier = placement.padding((control.reach * scale).dp)) {
            StaticControl(layout, control, frame, notPressed, scale)
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
) {
    when (control) {
        is SkinControl.Dpad -> Canvas(Modifier.fillMaxSize()) { drawAnyDpad(layout, control.look, Offset.Zero) }
        is SkinControl.Stick ->
            Canvas(Modifier.fillMaxSize()) {
                drawStickBase(layout)
                val knob = size.minDimension * 0.62f
                translate((size.width - knob) / 2, (size.height - knob) / 2) {
                    drawStickKnob(layout, false, knob)
                }
            }
        else -> ControlGlyph(layout, control, frame, notPressed, 0.dp, scale, pinned = false)
    }
}

/** Where the controls of [layout] land on a surface of [width] x [height] dp. */
internal class SkinGeometry(
    private val layout: SkinLayout,
    private val pinToEdges: Boolean,
    private val width: Float,
    private val height: Float,
) {
    // Landscape fills the height, unless the screen is narrower than the skin (4:3 tablets): then
    // it fits the width, so the left and right controls never run into each other.
    val scale =
        if (pinToEdges) minOf(height / layout.height, width / layout.width) else width / layout.width

    /** Top of [frame]: in landscape the top and bottom controls stay at the screen's top and bottom edges. */
    fun placeY(frame: SkinRect): Float {
        // Upright the panel always has the skin's own proportions: the controls keep their designed spacing.
        if (!pinToEdges) return frame.y * scale
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
    control: @Composable BoxWithConstraintsScope.(index: Int, control: SkinControl, frame: SkinRect, placement: Modifier, scale: Float) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val geometry = SkinGeometry(layout, pinToEdges, maxWidth.value, maxHeight.value)
        val scale = geometry.scale

        fun placeX(frame: SkinRect): Float = geometry.placeX(frame)

        // The NES pad keeps its own black face even inside a shell.
        if (!pinToEdges && (drawBody || layout.style == SkinStyle.NES_CLASSIC)) {
            Canvas(Modifier.fillMaxSize()) { drawSkinBody(layout, scale * density) }
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

private object Overlay {
    val Fill = Color(0x26FFFFFF)
    val FillPressed = Color(0x66FFFFFF)
    val Edge = Color(0x80FFFFFF)
    val Glyph = Color(0xE6FFFFFF)
}

/** The console body behind the controls (portrait only). [unit] is pixels per skin unit. */
private fun DrawScope.drawSkinBody(
    layout: SkinLayout,
    unit: Float,
) {
    when (layout.style) {
        SkinStyle.CONSOLE -> {
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
        layout.style == SkinStyle.OVERLAY && look == DpadLook.CROSS -> drawDpad(SkinStyle.OVERLAY, direction)
        layout.style == SkinStyle.OVERLAY -> drawFourPads(look, direction, overlayTint = layout.palette)
        look == DpadLook.CROSS && layout.style == SkinStyle.CONSOLE -> drawConsoleCross(layout.paletteOrMono(), direction)
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
    if (layout.style == SkinStyle.OVERLAY) {
        drawCircle(Overlay.Fill, r, c)
        drawCircle(Overlay.Edge, r, c, style = Stroke(1.5.dp.toPx()))
        val tint = layout.palette?.stickKnob ?: Overlay.Glyph
        drawCircle(tint.copy(alpha = if (direction != Offset.Zero) 0.75f else 0.5f), r * 0.36f, lean)
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

    drawWell(palette, full * 0.6f)
    translate(top = arm * 0.06f) { drawPath(cross(0f), Color(0x33000000)) }
    drawPath(cross(0f), palette.dpad.shade(-0.45f))
    val inner = arm * 0.07f
    drawPath(cross(-inner), Brush.verticalGradient(listOf(palette.dpad.shade(0.12f), palette.dpad), top, top + full))
    val pressed = palette.dpad.shade(-0.6f)
    if (direction.y < -0.3f) drawRect(pressed, Offset(left + arm + inner, top + inner), Size(arm - 2 * inner, arm - inner))
    if (direction.y > 0.3f) drawRect(pressed, Offset(left + arm + inner, top + 2 * arm), Size(arm - 2 * inner, arm - inner))
    if (direction.x < -0.3f) drawRect(pressed, Offset(left + inner, top + arm + inner), Size(arm - inner, arm - 2 * inner))
    if (direction.x > 0.3f) drawRect(pressed, Offset(left + 2 * arm, top + arm + inner), Size(arm - inner, arm - 2 * inner))
    drawArrows(left, top, arm, palette.dpadMark, direction)
    drawCircle(palette.dpad.shade(-0.2f), arm * 0.28f, Offset(left + full / 2, top + full / 2))
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
) {
    val full = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val away = full * 0.32f
    val overlay = palette == null
    val cColor = (overlayTint ?: palette)?.buttons?.get(SkinPalettes.C_BUTTONS_KEY)
    if (palette != null && look == DpadLook.SEPARATE) drawWell(palette, full * 0.62f)
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
                val tint = cColor?.first ?: Overlay.Glyph
                drawCircle(tint.copy(alpha = if (isPressed) 0.6f else 0.28f), r, centre)
                drawCircle(Overlay.Edge, r, centre, style = Stroke(1.5.dp.toPx()))
                mark = Overlay.Glyph
            } else {
                val fill = cColor?.first ?: palette!!.button
                if (!isPressed) drawCircle(Color(0x33000000), r, centre + Offset(0f, r * 0.12f))
                drawCircle(fill.shade(-0.4f), r, centre)
                drawCircle(
                    Brush.verticalGradient(
                        if (isPressed) listOf(fill.shade(-0.2f), fill.shade(-0.2f)) else listOf(fill.shade(0.15f), fill),
                        centre.y - r,
                        centre.y + r,
                    ),
                    r * 0.9f,
                    centre,
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
                if (!isPressed) drawRoundRect(Color(0x33000000), topLeft + Offset(0f, half * 0.14f), box, corner)
                drawRoundRect(fill.shade(-0.4f), topLeft, box, corner)
                val ring = half * 0.1f
                drawRoundRect(
                    Brush.verticalGradient(
                        if (isPressed) listOf(fill.shade(-0.6f), fill.shade(-0.6f)) else listOf(fill.shade(0.14f), fill),
                        topLeft.y,
                        topLeft.y + box.height,
                    ),
                    topLeft + Offset(ring, ring),
                    Size(box.width - 2 * ring, box.height - 2 * ring),
                    CornerRadius(corner.x - ring),
                )
                mark = palette.dpadMark
            }
            drawTriangleMark(centre, dir, half * 0.5f, if (isPressed) PressedMark else mark)
        }
    }
}

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
    if (layout.style == SkinStyle.OVERLAY) {
        drawCircle(Overlay.Fill, r)
        drawCircle(Overlay.Edge, r, style = Stroke(1.5.dp.toPx()))
        return
    }
    val palette = layout.paletteOrMono()
    drawWell(palette, r * 1.14f)
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
    if (layout.style == SkinStyle.OVERLAY) {
        drawCircle(if (pressed) Overlay.FillPressed else Overlay.Glyph.copy(alpha = 0.35f), r * 0.86f, c)
        drawCircle(Overlay.Edge, r * 0.86f, c, style = Stroke(1.5.dp.toPx()))
        return
    }
    val palette = layout.paletteOrMono()
    val cap = palette.stickKnob
    drawCircle(Color(0x44000000), r * 0.9f, c + Offset(0f, r * 0.1f))
    drawCircle(cap.shade(-0.4f), r * 0.9f, c)
    drawCircle(Brush.verticalGradient(listOf(cap.shade(0.18f), cap), c.y - r, c.y + r), r * 0.82f, c)
    // The dish of the cap, like the grippy top of a real stick.
    drawCircle(cap.shade(if (pressed) -0.2f else -0.08f), r * 0.55f, c)
}

private fun DrawScope.drawDpad(
    style: SkinStyle,
    direction: Offset,
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
        SkinStyle.OVERLAY -> {
            drawPath(cross(0f), if (direction != Offset.Zero) Overlay.FillPressed else Overlay.Fill)
            drawPath(cross(0f), Overlay.Edge, style = Stroke(1.5.dp.toPx()))
            drawArrows(left, top, arm, Overlay.Glyph, direction)
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
        val labelColor =
            when {
                style == SkinStyle.OVERLAY -> Overlay.Glyph
                style != SkinStyle.CONSOLE -> Mono.Emboss
                shape == ButtonShape.SHOULDER -> consolePalette.shoulderLabel
                key != null -> consolePalette.labelOf(key)
                else -> consolePalette.buttonLabel
            }
        // In landscape a coloured button keeps a tint of its colour (the symbol's, on dark PlayStation buttons).
        val overlayTint =
            key?.let { k ->
                palette?.buttons?.get(k)?.let { (buttonFill, label) -> if (buttonFill.luminance() < 0.15f) label else buttonFill }
            }

        Canvas(Modifier.fillMaxSize()) {
            when {
                style == SkinStyle.OVERLAY -> drawOverlayButton(shape, isPressed, overlayTint)
                style == SkinStyle.CONSOLE -> drawConsoleButton(shape, fill, isPressed)
                style == SkinStyle.NES_CLASSIC && control is SkinControl.Button && shape == ButtonShape.ROUND ->
                    drawNesFaceButton(isPressed)
                style == SkinStyle.NES_CLASSIC && (control is SkinControl.Menu || control is SkinControl.Rewind) ->
                    drawNesSmallButton(isPressed)
                else -> drawMonoButton(shape, isPressed)
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
            val glyphColor = if (style == SkinStyle.CONSOLE) labelColor else if (style == SkinStyle.OVERLAY) Overlay.Glyph else Color(0xFFD8D8DC)
            Canvas(Modifier.fillMaxSize(if (shape == ButtonShape.PILL) 0.7f else 0.56f)) { drawGlyph(glyph, glyphColor) }
        }
        if (onButton != null && onButton in PlayStationSymbols) {
            val symbolColor = if (style == SkinStyle.OVERLAY) overlayTint ?: Overlay.Glyph else labelColor
            Canvas(Modifier.fillMaxSize(0.46f)) { drawPlayStationSymbol(onButton, symbolColor) }
        } else if (onButton != null) {
            val unitDp = scale
            val fontSize = with(LocalDensity.current) { (minOf(frame.width, frame.height) * unitDp * if (control is SkinControl.Button && control.shape == ButtonShape.PILL) 0.42f else 0.46f).dp.toSp() }
            Text(
                text = onButton,
                color = labelColor,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                maxLines = 1,
            )
        }
    }
}

/** The pictures drawn on buttons. */
private enum class Glyph { MENU, REWIND, MIC, LID, SWAP }

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
    val ring = size.minDimension * if (shape == ButtonShape.ROUND) 0.06f else 0.12f
    if (!pressed) drawRoundRect(Color(0x33000000), Offset(0f, size.minDimension * 0.06f), size, radius)
    drawRoundRect(fill.shade(-0.42f), cornerRadius = radius)
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                if (pressed) listOf(fill.shade(-0.22f), fill.shade(-0.22f)) else listOf(fill.shade(0.16f), fill),
            ),
        topLeft = Offset(ring, ring),
        size = Size(size.width - 2 * ring, size.height - 2 * ring),
        cornerRadius = CornerRadius((radius.x - ring).coerceAtLeast(0f)),
    )
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
            tint != null -> tint.copy(alpha = if (pressed) 0.62f else 0.3f)
            pressed -> Overlay.FillPressed
            else -> Overlay.Fill
        }
    drawRoundRect(fill, cornerRadius = radius)
    drawRoundRect(tint?.copy(alpha = 0.85f) ?: Overlay.Edge, cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
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
            style == SkinStyle.CONSOLE -> layout.paletteOrMono().caption
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
            fontWeight = FontWeight.Black,
            textAlign = align,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The round hollow in the body that a d-pad or stick sits in, centred in this canvas. */
private fun DrawScope.drawWell(
    palette: SkinPalette,
    radius: Float,
) {
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(palette.body.shade(-0.07f), radius, c)
    // Shade on the upper edge, light on the lower one, like a dip in the plastic.
    drawArc(Color.Black.copy(alpha = 0.16f), 180f, 180f, false, c - Offset(radius, radius), Size(radius * 2, radius * 2), style = Stroke(radius * 0.05f))
    drawArc(Color.White.copy(alpha = 0.22f), 0f, 180f, false, c - Offset(radius, radius), Size(radius * 2, radius * 2), style = Stroke(radius * 0.04f))
}
