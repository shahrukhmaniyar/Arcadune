package com.swordfish.touchinput.skin

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlButton
import gg.padkit.controls.ControlCross
import gg.padkit.ids.Id

/**
 * The player's changes to a skin: one size for every control, and how far each control was
 * dragged from its place (in skin units, by the control's position in the layout).
 */
data class SkinCustomization(
    val size: Float = 1f,
    val offsets: Map<Int, Offset> = emptyMap(),
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
            offsets.forEach { (index, offset) -> append(";$index:${offset.x}:${offset.y}") }
        }

    companion object {
        const val MIN_SIZE = 0.75f
        const val MAX_SIZE = 1.35f

        fun decode(text: String?): SkinCustomization {
            if (text.isNullOrBlank()) return SkinCustomization()
            return runCatching {
                val parts = text.split(';')
                val offsets =
                    parts.drop(1).associate { part ->
                        val (index, x, y) = part.split(':')
                        index.toInt() to Offset(x.toFloat(), y.toFloat())
                    }
                SkinCustomization(parts.first().toFloat().coerceIn(MIN_SIZE, MAX_SIZE), offsets)
            }.getOrDefault(SkinCustomization())
        }
    }
}

/** Plays the game: draws [layout] and turns touches into button presses. */
@Composable
fun PadKitScope.ArcaduneSkinView(
    modifier: Modifier = Modifier,
    layout: SkinLayout,
    pinToEdges: Boolean,
    rewindAvailable: Boolean,
    customization: SkinCustomization,
) {
    SkinFrame(modifier, layout, pinToEdges, rewindAvailable, customization) { _, control, frame, placement, scale ->
        val inset = (control.reach * scale).dp
        when (control) {
            is SkinControl.Dpad ->
                ControlCross(
                    modifier = placement,
                    id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD),
                    allowDiagonals = true,
                    background = {},
                    foreground = { direction ->
                        Canvas(Modifier.fillMaxSize().padding(inset)) { drawDpad(layout.style, direction.value) }
                    },
                )

            else ->
                ControlButton(
                    modifier = placement,
                    id = Id.Key(control.keyCode()),
                    background = {},
                    foreground = { pressed -> ControlGlyph(layout.style, control, frame, pressed, inset, scale) },
                )
        }
    }
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
            SkinFrame(Modifier.fillMaxSize(), layout, pinToEdges, rewindAvailable, customization) { index, control, frame, placement, scale ->
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
                    if (control is SkinControl.Dpad) {
                        Canvas(Modifier.fillMaxSize()) { drawDpad(layout.style, Offset.Zero) }
                    } else {
                        ControlGlyph(layout.style, control, frame, notPressed, 0.dp, scale)
                    }
                }
            }
        }
    }
}

/** Where the controls of [layout] land on a surface of [width] x [height] dp. */
private class SkinGeometry(
    private val layout: SkinLayout,
    private val pinToEdges: Boolean,
    private val width: Float,
    private val height: Float,
) {
    val scale = if (pinToEdges) height / layout.height else width / layout.width

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
    ): List<Pair<Int, androidx.compose.ui.geometry.Rect>> =
        layout.controls.mapIndexedNotNull { index, item ->
            if (item is SkinControl.Rewind && !rewindAvailable) return@mapIndexedNotNull null
            val frame = customization.frameOf(index, item.frame)
            val left = placeX(frame) - item.reach * scale
            val top = frame.y * scale - item.reach * scale
            index to
                androidx.compose.ui.geometry.Rect(
                    left,
                    top,
                    left + (frame.width + 2 * item.reach) * scale,
                    top + (frame.height + 2 * item.reach) * scale,
                )
        }
}

private fun SkinControl.keyCode(): Int =
    when (this) {
        is SkinControl.Button -> keyCode
        is SkinControl.Menu -> KeyEvent.KEYCODE_BUTTON_MODE
        is SkinControl.Rewind -> ArcaduneSkins.rewindKey
        is SkinControl.Dpad -> 0
    }

/** Places the background and every control (and the names under them) of [layout]. */
@Composable
private fun SkinFrame(
    modifier: Modifier,
    layout: SkinLayout,
    pinToEdges: Boolean,
    rewindAvailable: Boolean,
    customization: SkinCustomization,
    control: @Composable BoxWithConstraintsScope.(index: Int, control: SkinControl, frame: SkinRect, placement: Modifier, scale: Float) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val geometry = SkinGeometry(layout, pinToEdges, maxWidth.value, maxHeight.value)
        val scale = geometry.scale

        fun placeX(frame: SkinRect): Float = geometry.placeX(frame)

        if (!pinToEdges) {
            Canvas(Modifier.fillMaxSize()) { drawSkinBody(layout.style, scale * density) }
        }

        layout.controls.forEachIndexed { index, item ->
            if (item is SkinControl.Rewind && !rewindAvailable) return@forEachIndexed
            val frame = customization.frameOf(index, item.frame)
            val reach = item.reach
            val x = placeX(frame) - reach * scale
            val y = frame.y * scale - reach * scale
            val placement =
                Modifier
                    .absoluteOffset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }
                    .size(((frame.width + 2 * reach) * scale).dp, ((frame.height + 2 * reach) * scale).dp)
            control(index, item, frame, placement, scale)
            Caption(layout.style, item, frame, placeX(frame), scale)
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
    style: SkinStyle,
    unit: Float,
) {
    when (style) {
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
            drawArrows(left, top, arm, Overlay.Glyph)
            return
        }

        SkinStyle.NES_CLASSIC -> drawPath(cross(arm * 0.12f), Color(0xFFCFCFD2))
        SkinStyle.MONO -> translate(top = arm * 0.06f) { drawPath(cross(0f), Mono.Shadow) }
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

    drawArrows(left, top, arm, Mono.Emboss)
    drawCircle(Color(0xFF0B0B0C), arm * 0.3f, Offset(left + full / 2, top + full / 2))
    drawCircle(Mono.Emboss.copy(alpha = 0.6f), arm * 0.3f, Offset(left + full / 2, top + full / 2), style = Stroke(arm * 0.04f))
}

private fun DrawScope.drawArrows(
    left: Float,
    top: Float,
    arm: Float,
    color: Color,
) {
    val c = Offset(left + 1.5f * arm, top + 1.5f * arm)
    val far = arm * 1.15f
    val near = arm * 0.78f
    val half = arm * 0.22f
    fun triangle(
        tip: Offset,
        a: Offset,
        b: Offset,
    ) = drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }, color)
    triangle(Offset(c.x, c.y - far), Offset(c.x - half, c.y - near), Offset(c.x + half, c.y - near))
    triangle(Offset(c.x, c.y + far), Offset(c.x - half, c.y + near), Offset(c.x + half, c.y + near))
    triangle(Offset(c.x - far, c.y), Offset(c.x - near, c.y - half), Offset(c.x - near, c.y + half))
    triangle(Offset(c.x + far, c.y), Offset(c.x + near, c.y - half), Offset(c.x + near, c.y + half))
}

@Composable
private fun ControlGlyph(
    style: SkinStyle,
    control: SkinControl,
    frame: SkinRect,
    pressed: State<Boolean>,
    inset: androidx.compose.ui.unit.Dp,
    scale: Float,
) {
    val isPressed = pressed.value
    Box(modifier = Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.Center) {
        val shape =
            when (control) {
                is SkinControl.Button -> control.shape
                is SkinControl.Menu, is SkinControl.Rewind ->
                    if (style == SkinStyle.NES_CLASSIC || frame.width < frame.height * 1.3f) ButtonShape.ROUND else ButtonShape.PILL
                is SkinControl.Dpad -> ButtonShape.ROUND
            }

        Canvas(Modifier.fillMaxSize()) {
            when {
                style == SkinStyle.OVERLAY -> drawOverlayButton(shape, isPressed)
                style == SkinStyle.NES_CLASSIC && control is SkinControl.Button && shape == ButtonShape.ROUND ->
                    drawNesFaceButton(isPressed)
                style == SkinStyle.NES_CLASSIC && (control is SkinControl.Menu || control is SkinControl.Rewind) ->
                    drawNesSmallButton(isPressed)
                else -> drawMonoButton(shape, isPressed)
            }
        }

        // What is written on the button itself.
        val onButton =
            when {
                style == SkinStyle.OVERLAY && control is SkinControl.Menu -> null
                style == SkinStyle.OVERLAY && control is SkinControl.Rewind -> null
                control is SkinControl.Button && (!control.labelBelow || style == SkinStyle.OVERLAY) -> control.label
                else -> null
            }
        if (onButton != null) {
            val unitDp = scale
            val fontSize = with(LocalDensity.current) { (minOf(frame.width, frame.height) * unitDp * if (control is SkinControl.Button && control.shape == ButtonShape.PILL) 0.42f else 0.46f).dp.toSp() }
            Text(
                text = onButton,
                color = if (style == SkinStyle.OVERLAY) Overlay.Glyph else Mono.Emboss,
                fontWeight = FontWeight.Black,
                fontSize = fontSize,
                maxLines = 1,
            )
        }
        if (style == SkinStyle.OVERLAY && (control is SkinControl.Menu || control is SkinControl.Rewind)) {
            Image(
                painter = painterResource(if (control is SkinControl.Menu) R.drawable.button_menu else R.drawable.button_rewind),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.6f),
                colorFilter = ColorFilter.tint(Overlay.Glyph),
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
                if (pressed) listOf(Mono.ButtonPressedTop, Mono.ButtonBottom) else listOf(Mono.ButtonTop, Mono.ButtonBottom),
            ),
        topLeft = Offset(ring, ring),
        size = Size(size.width - 2 * ring, size.height - 2 * ring),
        cornerRadius = CornerRadius((radius.x - ring).coerceAtLeast(0f)),
    )
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
) {
    val radius =
        when (shape) {
            ButtonShape.ROUND, ButtonShape.PILL -> CornerRadius(size.minDimension / 2)
            ButtonShape.SHOULDER -> CornerRadius(size.height * 0.45f)
        }
    drawRoundRect(if (pressed) Overlay.FillPressed else Overlay.Fill, cornerRadius = radius)
    drawRoundRect(Overlay.Edge, cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
}

/** The name written under a control, like MENU / SELECT / START under the pills. */
@Composable
private fun Caption(
    style: SkinStyle,
    control: SkinControl,
    frame: SkinRect,
    x: Float,
    scale: Float,
) {
    if (style == SkinStyle.OVERLAY) return
    val text =
        when (control) {
            is SkinControl.Button -> control.label.takeIf { control.labelBelow }
            is SkinControl.Menu -> "MENU"
            is SkinControl.Rewind -> "REWIND"
            is SkinControl.Dpad -> null
        } ?: return

    val nes = style == SkinStyle.NES_CLASSIC
    val isFace = control is SkinControl.Button && control.shape == ButtonShape.ROUND
    val fontUnits = if (nes && isFace) 13f else 8.5f
    val width = maxOf(frame.width, 60f)

    // Where the name goes: under the control, or (NES menu / rewind) beside it.
    val (left, top, align) =
        when {
            nes && control is SkinControl.Menu -> Triple(x - (width + 4) * scale, (frame.centerY - fontUnits * 0.7f) * scale, TextAlign.End)
            nes && control is SkinControl.Rewind -> Triple(x + (frame.width + 4) * scale, (frame.centerY - fontUnits * 0.7f) * scale, TextAlign.Start)
            nes && !isFace -> Triple(x + (frame.width - width) / 2 * scale, 244f * scale, TextAlign.Center)
            nes -> Triple(x + (frame.width - width) / 2 * scale, (frame.y + frame.height + 3) * scale, TextAlign.Center)
            else -> Triple(x + (frame.width - width) / 2 * scale, (frame.y + frame.height + 4) * scale, TextAlign.Center)
        }

    val color =
        when {
            nes && (control is SkinControl.Menu || control is SkinControl.Rewind) -> Color(0xFFEDEDEF)
            nes -> Nes.Label
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
