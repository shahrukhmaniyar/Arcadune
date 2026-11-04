package com.swordfish.touchinput.skin

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.content.ContextCompat
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputevents.InputEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The touch handling of Arcadune's skins, for every finger at once.
 *
 * Why not PadKit's own controls: PadKit gives a finger to the first control whose box contains it and
 * then drops the finger when it is outside that box's inscribed ellipse, so the corners of wide
 * buttons (L, R, Select, Start) did nothing, and a finger where two boxes met could be thrown away
 * instead of pressing the button under it. Here:
 *  - a finger presses the button it is on; where touch areas meet, the nearest button wins;
 *  - a finger can slide from one button to the next (like on Delta), without lifting;
 *  - a finger that starts on the d-pad or a stick keeps steering it wherever it moves;
 *  - on consoles with a touch screen, a finger that starts in a screen window touches the game's
 *    screen, even while other fingers hold buttons (the game view alone only follows one finger).
 */
class SkinInput(
    /** A short buzz for a press, as the haptic setting asks (see [vibrator]). */
    private val vibrate: () -> Unit,
    private val onEvents: (List<InputEvent>) -> Unit,
    /** A finger on the touch screen, in the game view's -1..1 coordinates, or null when it lifts. */
    private val onTouchScreen: (Offset?) -> Unit,
) {
    /** A control as laid out on screen: its touch area (with its reach) in root pixels. */
    class Target(
        val index: Int,
        val control: SkinControl,
        val area: Rect,
        val round: Boolean,
    )

    var hapticFeedback: HapticFeedbackType = HapticFeedbackType.PRESS

    /** The controls on screen; set by the skin view whenever its layout changes. */
    var targets: List<Target> = emptyList()

    /**
     * The game screen windows that take touches (DS / 3DS), in root pixels, and the size of the game
     * view they are measured in (its whole area, as the core expects). Empty when nothing takes touches.
     */
    var touchWindows: List<Rect> = emptyList()
    var gameViewSize: Rect = Rect.Zero

    /** Whether each control is pressed, by index, and where each d-pad / stick points; drawn by the skin. */
    val pressed = mutableStateMapOf<Int, Boolean>()
    val directions = mutableStateMapOf<Int, Offset>()

    private sealed interface Owner {
        /** Presses whatever button is under it, again at every move. */
        data object Buttons : Owner

        /** Steers the d-pad or stick it started on. */
        data class Steer(val index: Int) : Owner

        /** Touches the game's touch screen. */
        data object Screen : Owner
    }

    private val owners = mutableMapOf<Long, Owner>()
    private val positions = mutableMapOf<Long, Offset>()
    private var screenPointer: Long? = null

    private var pressedKeys = setOf<Int>()
    private var sentDirections = mapOf<Int, Offset>()

    fun onDown(
        id: Long,
        position: Offset,
    ) {
        positions[id] = position
        val target = targetAt(position)
        owners[id] =
            when {
                target != null && (target.control is SkinControl.Dpad || target.control is SkinControl.Stick) -> Owner.Steer(target.index)
                target != null -> Owner.Buttons
                screenPointer == null && touchWindows.any { it.contains(position) } -> {
                    screenPointer = id
                    Owner.Screen
                }
                // On the body: it presses a button if it slides onto one.
                else -> Owner.Buttons
            }
        update()
    }

    fun onMove(
        id: Long,
        position: Offset,
    ) {
        if (id !in owners) return
        positions[id] = position
        update()
    }

    fun onUp(id: Long) {
        owners.remove(id)
        positions.remove(id)
        if (screenPointer == id) {
            screenPointer = null
            onTouchScreen(null)
        }
        update()
    }

    /** Lets go of everything (the skin left the screen, the game paused). */
    fun releaseAll() {
        owners.keys.toList().forEach { onUp(it) }
    }

    /** The control under [position]: of those whose touch area holds it, the one whose centre is nearest. */
    private fun targetAt(position: Offset): Target? =
        targets
            .filter { it.contains(position) }
            .minByOrNull { it.normalizedDistance(position) }

    private fun Target.contains(position: Offset): Boolean = if (round) normalizedDistance(position) <= 1f else area.contains(position)

    /** Distance from the centre in "radii": 1 on the edge of the (elliptic) touch area. */
    private fun Target.normalizedDistance(position: Offset): Float {
        val dx = (position.x - area.center.x) / (area.width / 2)
        val dy = (position.y - area.center.y) / (area.height / 2)
        return hypot(dx, dy)
    }

    private fun update() {
        val keys = mutableSetOf<Int>()
        val pressedIndices = mutableSetOf<Int>()
        val steering = mutableMapOf<Int, Offset>()

        for ((id, owner) in owners) {
            val position = positions[id] ?: continue
            when (owner) {
                Owner.Buttons -> {
                    val target = targetAt(position) ?: continue
                    // A finger that wanders onto the d-pad presses nothing there: the d-pad wants its own finger.
                    if (target.control is SkinControl.Dpad || target.control is SkinControl.Stick) continue
                    keys += target.control.key()
                    pressedIndices += target.index
                }
                is Owner.Steer -> {
                    val target = targets.firstOrNull { it.index == owner.index } ?: continue
                    steering[owner.index] = steer(target, position)
                }
                Owner.Screen -> {
                    val size = gameViewSize
                    if (size.width > 0 && size.height > 0) {
                        val x = (2 * (position.x - size.left) / size.width - 1).coerceIn(-1f, 1f)
                        val y = (2 * (position.y - size.top) / size.height - 1).coerceIn(-1f, 1f)
                        onTouchScreen(Offset(x, y))
                    }
                }
            }
        }

        val events = mutableListOf<InputEvent>()
        val newlyPressed = keys - pressedKeys
        val released = pressedKeys - keys
        released.forEach { events += InputEvent.Button(it, false) }
        newlyPressed.forEach { events += InputEvent.Button(it, true) }
        pressedKeys = keys

        // Every d-pad and stick on screen, centred when no finger steers it.
        var directionPressed = false
        var directionReleased = false
        for (target in targets) {
            val control = target.control
            val source =
                when (control) {
                    is SkinControl.Dpad -> control.source
                    is SkinControl.Stick -> control.source
                    else -> continue
                }
            val now = steering[target.index] ?: Offset.Zero
            val before = sentDirections[target.index] ?: Offset.Zero
            if (now == before) continue
            if (control is SkinControl.Dpad) {
                events += InputEvent.DiscreteDirection(source, now)
                if (now != Offset.Zero) directionPressed = true else directionReleased = true
            } else {
                events += InputEvent.ContinuousDirection(source, now)
            }
        }
        sentDirections = targets.associate { it.index to (steering[it.index] ?: Offset.Zero) }.filterValues { it != Offset.Zero }

        // What the skin draws.
        targets.forEach { target ->
            val isPressed = target.index in pressedIndices
            if ((pressed[target.index] ?: false) != isPressed) pressed[target.index] = isPressed
            val direction = steering[target.index] ?: Offset.Zero
            if ((directions[target.index] ?: Offset.Zero) != direction) directions[target.index] = direction
        }

        if (events.isEmpty()) return
        onEvents(events)
        when (hapticFeedback) {
            HapticFeedbackType.PRESS -> if (newlyPressed.isNotEmpty() || directionPressed) vibrate()
            HapticFeedbackType.PRESS_RELEASE ->
                if (newlyPressed.isNotEmpty() || released.isNotEmpty() || directionPressed || directionReleased) vibrate()
            else -> Unit
        }
    }

    /**
     * Where a finger at [position] points a d-pad (8 ways, or 4 for the N64 C buttons) or a stick
     * (any amount, up to 1), with up as +y like the rest of the input. The middle of a d-pad is a
     * small dead zone, so resting the thumb there presses nothing.
     */
    private fun steer(
        target: Target,
        position: Offset,
    ): Offset {
        val radius = minOf(target.area.width, target.area.height) / 2
        val dx = (position.x - target.area.center.x) / radius
        val dy = -(position.y - target.area.center.y) / radius
        val control = target.control
        if (control is SkinControl.Stick) {
            val length = hypot(dx, dy)
            return if (length <= 1f) Offset(dx, dy) else Offset(dx / length, dy / length)
        }
        if (hypot(dx, dy) < DPAD_DEAD_ZONE) return Offset.Zero
        val fourWays = control is SkinControl.Dpad && control.look == DpadLook.C_BUTTONS
        if (fourWays) {
            return if (abs(dx) > abs(dy)) Offset(if (dx > 0) 1f else -1f, 0f) else Offset(0f, if (dy > 0) 1f else -1f)
        }
        // Eight equal slices, so diagonals are as easy to hit as the four directions.
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        val slice = ((angle + 360 + 22.5) % 360 / 45).toInt()
        return when (slice) {
            0 -> Offset(1f, 0f)
            1 -> Offset(1f, 1f)
            2 -> Offset(0f, 1f)
            3 -> Offset(-1f, 1f)
            4 -> Offset(-1f, 0f)
            5 -> Offset(-1f, -1f)
            6 -> Offset(0f, -1f)
            else -> Offset(1f, -1f)
        }
    }

    private fun SkinControl.key(): Int =
        when (this) {
            is SkinControl.Button -> keyCode
            is SkinControl.Menu -> KeyEvent.KEYCODE_BUTTON_MODE
            is SkinControl.Rewind -> ArcaduneSkins.rewindKey
            is SkinControl.Dpad, is SkinControl.Stick -> 0
        }

    companion object {
        private const val DPAD_DEAD_ZONE = 0.22f

        /** A short click on the phone's vibrator, the same as the game buttons have always used. */
        fun vibrator(context: Context): () -> Unit {
            val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
            return { vibrator?.let { click(it) } }
        }

        private fun click(vibrator: Vibrator) {
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12L)
            }
        }
    }
}

/**
 * Feeds every finger on this element (in root pixels) to [input]. It only watches, so whatever is
 * under it (the game view) still gets the touches too.
 */
fun androidx.compose.ui.Modifier.trackFingers(input: SkinInput): androidx.compose.ui.Modifier =
    composed {
        val origin = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
        androidx.compose.ui.Modifier
            .onGloballyPositioned { origin.value = it.boundsInRoot().topLeft }
            .pointerInput(input) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        for (change in event.changes) {
                            val id = change.id.value
                            when {
                                change.pressed && !change.previousPressed -> input.onDown(id, change.position + origin.value)
                                !change.pressed && change.previousPressed -> input.onUp(id)
                                change.pressed -> input.onMove(id, change.position + origin.value)
                            }
                        }
                    }
                }
            }
    }
