package com.swordfish.touchinput.skin

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import gg.padkit.inputevents.InputEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The touch tracker of the skins: every finger, sliding, the d-pad, and the DS touch screen. */
class SkinInputTest {
    private val events = mutableListOf<InputEvent>()
    private val screenTouches = mutableListOf<Offset?>()
    private var buzzes = 0
    private lateinit var input: SkinInput

    private val a = KeyEvent.KEYCODE_BUTTON_A
    private val b = KeyEvent.KEYCODE_BUTTON_B
    private val l = KeyEvent.KEYCODE_BUTTON_L1
    private val x = KeyEvent.KEYCODE_BUTTON_X
    private val y = KeyEvent.KEYCODE_BUTTON_Y

    private fun button(
        key: Int,
        shape: ButtonShape = ButtonShape.ROUND,
    ) = SkinControl.Button(key, "", shape, SkinRect(0f, 0f, 1f, 1f))

    @Before
    fun setUp() {
        input = SkinInput(vibrate = { buzzes++ }, onEvents = { events += it }, onTouchScreen = { screenTouches += it })
        input.targets =
            listOf(
                // Two round buttons side by side whose touch areas overlap (100..200 and 170..270).
                SkinInput.Target(0, button(b), Rect(100f, 100f, 200f, 200f), round = true),
                SkinInput.Target(1, button(a), Rect(170f, 100f, 270f, 200f), round = true),
                // A wide shoulder button, touched in its box.
                SkinInput.Target(2, button(l, ButtonShape.SHOULDER), Rect(0f, 0f, 300f, 60f), round = false),
                // A d-pad.
                SkinInput.Target(3, SkinControl.Dpad(SkinRect(0f, 0f, 1f, 1f)), Rect(400f, 100f, 600f, 300f), round = true),
                // Two round buttons whose touch areas do not meet: a 20 px strip between them (200..220).
                SkinInput.Target(4, button(y), Rect(100f, 600f, 200f, 700f), round = true),
                SkinInput.Target(5, button(x), Rect(220f, 600f, 320f, 700f), round = true),
            )
        input.touchWindows = listOf(Rect(700f, 0f, 1000f, 300f))
        input.gameViewSize = Rect(0f, 0f, 1000f, 1000f)
    }

    private fun buttonEvents() = events.filterIsInstance<InputEvent.Button>().map { it.id to it.pressed }

    @Test
    fun aTapPressesAndReleases() {
        input.onDown(1, Offset(150f, 150f))
        input.onUp(1)
        assertEquals(listOf(b to true, b to false), buttonEvents())
        assertEquals(1, buzzes)
    }

    @Test
    fun twoFingersPressTwoButtonsTogether() {
        input.onDown(1, Offset(130f, 150f))
        input.onDown(2, Offset(250f, 150f))
        assertEquals(listOf(b to true, a to true), buttonEvents())
        assertTrue(input.pressed[0] == true && input.pressed[1] == true)
        input.onUp(1)
        assertEquals(b to false, buttonEvents().last())
        assertTrue(input.pressed[1] == true)
    }

    @Test
    fun aFingerSlidesFromOneButtonToTheNext() {
        input.onDown(1, Offset(130f, 150f))
        input.onMove(1, Offset(250f, 150f))
        assertEquals(listOf(b to true, b to false, a to true), buttonEvents())
    }

    @Test
    fun whereTouchAreasMeetTheNearestButtonWins() {
        // 197 is inside both areas, clearly nearer A's centre (220) than B's (150).
        input.onDown(1, Offset(197f, 150f))
        assertEquals(listOf(a to true), buttonEvents())
    }

    @Test
    fun aThumbRightBetweenTwoFaceButtonsPressesBoth() {
        // 185 is as far from B's centre as from A's: B + A with one thumb, as on Delta.
        input.onDown(1, Offset(185f, 150f))
        assertEquals(setOf(b to true, a to true), buttonEvents().toSet())
        assertTrue(input.pressed[0] == true && input.pressed[1] == true)
        input.onUp(1)
        assertEquals(setOf(b to false, a to false), buttonEvents().drop(2).toSet())
    }

    @Test
    fun aFingerKeepsItsButtonUntilItReallyLeavesIt() {
        input.onDown(1, Offset(160f, 150f))
        // Nearer A now, but still on B: no chatter between the two.
        input.onMove(1, Offset(197f, 150f))
        input.onMove(1, Offset(196f, 151f))
        assertEquals(listOf(b to true), buttonEvents())
        // Off B: A takes over.
        input.onMove(1, Offset(205f, 150f))
        assertEquals(listOf(b to true, b to false, a to true), buttonEvents())
    }

    /** Where a finger [radius] (in the d-pad's radii) from its middle, at [degrees] (0 is right, 90 up), is. */
    private fun onDpad(
        degrees: Double,
        radius: Float = 0.6f,
    ): Offset {
        val angle = Math.toRadians(degrees)
        val reach = 100f * radius
        return Offset(500f + reach * kotlin.math.cos(angle).toFloat(), 200f - reach * kotlin.math.sin(angle).toFloat())
    }

    private fun lastDirection() = events.filterIsInstance<InputEvent.DiscreteDirection>().last().direction

    @Test
    fun theDpadDoesNotFlickerOnTheBorderOfADiagonal() {
        input.onDown(1, onDpad(20.0))
        assertEquals(Offset(1f, 0f), lastDirection())
        // Just past the border between right and up-right: still right.
        input.onMove(1, onDpad(26.0))
        input.onMove(1, onDpad(19.0))
        input.onMove(1, onDpad(27.0))
        assertEquals(1, events.filterIsInstance<InputEvent.DiscreteDirection>().size)
        // Clearly in the diagonal: up-right, and it holds there coming back to the border.
        input.onMove(1, onDpad(35.0))
        assertEquals(Offset(1f, 1f), lastDirection())
        input.onMove(1, onDpad(18.0))
        assertEquals(Offset(1f, 1f), lastDirection())
        input.onMove(1, onDpad(10.0))
        assertEquals(Offset(1f, 0f), lastDirection())
    }

    @Test
    fun theDpadLetsGoOnlyWellInsideItsMiddle() {
        input.onDown(1, onDpad(90.0, radius = 0.3f))
        assertEquals(Offset(0f, 1f), lastDirection())
        // Back inside the 0.22 dead zone, but not by much: still up.
        input.onMove(1, onDpad(90.0, radius = 0.18f))
        assertEquals(Offset(0f, 1f), lastDirection())
        input.onMove(1, onDpad(90.0, radius = 0.1f))
        assertEquals(Offset.Zero, lastDirection())
    }

    @Test
    fun theCornersOfAWideButtonWork() {
        // The far corner of the shoulder: outside its inscribed ellipse, where PadKit dropped the finger.
        input.onDown(1, Offset(5f, 5f))
        assertEquals(listOf(l to true), buttonEvents())
    }

    @Test
    fun theDpadKeepsItsFingerAndPointsUpAsPlusY() {
        input.onDown(1, Offset(500f, 120f))
        val up = events.filterIsInstance<InputEvent.DiscreteDirection>().last()
        assertEquals(ComposeTouchLayouts.MOTION_SOURCE_DPAD, up.id)
        assertEquals(Offset(0f, 1f), up.direction)
        // The finger leaves the d-pad to the right: it still steers it.
        input.onMove(1, Offset(800f, 200f))
        assertEquals(Offset(1f, 0f), events.filterIsInstance<InputEvent.DiscreteDirection>().last().direction)
        input.onUp(1)
        assertEquals(Offset.Zero, events.filterIsInstance<InputEvent.DiscreteDirection>().last().direction)
    }

    @Test
    fun theMiddleOfTheDpadPressesNothing() {
        input.onDown(1, Offset(505f, 198f))
        assertTrue(events.filterIsInstance<InputEvent.DiscreteDirection>().isEmpty())
    }

    @Test
    fun diagonalsAreEightEqualSlices() {
        input.onDown(1, Offset(570f, 130f))
        assertEquals(Offset(1f, 1f), events.filterIsInstance<InputEvent.DiscreteDirection>().last().direction)
    }

    @Test
    fun theTouchScreenWorksWhileAButtonIsHeld() {
        input.onDown(1, Offset(150f, 150f))
        input.onDown(2, Offset(850f, 250f))
        assertEquals(listOf(b to true), buttonEvents())
        // 850 x 250 in a 1000 x 1000 view: 0.7, -0.5 in the core's -1..1 coordinates.
        val touch = screenTouches.last()!!
        assertEquals(0.7f, touch.x, 0.001f)
        assertEquals(-0.5f, touch.y, 0.001f)
        input.onUp(2)
        assertEquals(null, screenTouches.last())
        assertTrue(input.pressed[0] == true)
    }

    @Test
    fun aFingerOnTheBodyPressesNothingUntilItSlidesOntoAButton() {
        input.onDown(1, Offset(350f, 400f))
        assertTrue(buttonEvents().isEmpty())
        input.onMove(1, Offset(150f, 150f))
        assertEquals(listOf(b to true), buttonEvents())
    }

    @Test
    fun aTapInTheStripBetweenTwoButtonsIsNotLost() {
        // Right in the middle of the strip: both, as with a thumb on the two.
        input.onDown(1, Offset(210f, 650f))
        assertEquals(setOf(y to true, x to true), buttonEvents().toSet())
        input.onUp(1)
        events.clear()
        // Nearer Y: only Y.
        input.onDown(2, Offset(204f, 650f))
        assertEquals(listOf(y to true), buttonEvents())
    }

    @Test
    fun aFingerJustPastTheDpadSteersIt() {
        // A quarter of a radius above the d-pad's touch area.
        input.onDown(1, Offset(500f, 75f))
        assertEquals(Offset(0f, 1f), lastDirection())
    }

    @Test
    fun theStripsAreNotWideEnoughToPressAButtonFromFarAway() {
        input.onDown(1, Offset(150f, 535f))
        assertTrue(buttonEvents().isEmpty())
    }

    @Test
    fun theAbButtonPressesBothKeys() {
        val ab = SkinControl.Button(a, "AB", ButtonShape.PILL, SkinRect(0f, 0f, 1f, 1f), alsoKey = b)
        input.targets += SkinInput.Target(6, ab, Rect(800f, 600f, 900f, 650f), round = false)
        input.onDown(1, Offset(850f, 625f))
        assertEquals(setOf(a to true, b to true), buttonEvents().toSet())
        input.onUp(1)
        assertEquals(setOf(a to false, b to false), buttonEvents().drop(2).toSet())
    }

    @Test
    fun releaseAllLetsGoOfEverything() {
        input.onDown(1, Offset(150f, 150f))
        input.onDown(2, Offset(500f, 120f))
        input.releaseAll()
        assertEquals(b to false, buttonEvents().last())
        assertEquals(Offset.Zero, events.filterIsInstance<InputEvent.DiscreteDirection>().last().direction)
    }
}
