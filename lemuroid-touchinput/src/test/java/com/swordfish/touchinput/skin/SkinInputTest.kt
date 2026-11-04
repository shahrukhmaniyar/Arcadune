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

    private fun button(
        key: Int,
        shape: ButtonShape = ButtonShape.ROUND,
    ) = SkinControl.Button(key, "", shape, SkinRect(0f, 0f, 1f, 1f))

    @Before
    fun setUp() {
        input = SkinInput(vibrate = { buzzes++ }, onEvents = { events += it }, onTouchScreen = { screenTouches += it })
        input.targets =
            listOf(
                // Two round buttons side by side whose touch boxes overlap a little (100..200 and 190..290).
                SkinInput.Target(0, button(b), Rect(100f, 100f, 200f, 200f), round = true),
                SkinInput.Target(1, button(a), Rect(190f, 100f, 290f, 200f), round = true),
                // A wide shoulder button, touched in its box.
                SkinInput.Target(2, button(l, ButtonShape.SHOULDER), Rect(0f, 0f, 300f, 60f), round = false),
                // A d-pad.
                SkinInput.Target(3, SkinControl.Dpad(SkinRect(0f, 0f, 1f, 1f)), Rect(400f, 100f, 600f, 300f), round = true),
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
        input.onDown(2, Offset(260f, 150f))
        assertEquals(listOf(b to true, a to true), buttonEvents())
        assertTrue(input.pressed[0] == true && input.pressed[1] == true)
        input.onUp(1)
        assertEquals(b to false, buttonEvents().last())
        assertTrue(input.pressed[1] == true)
    }

    @Test
    fun aFingerSlidesFromOneButtonToTheNext() {
        input.onDown(1, Offset(130f, 150f))
        input.onMove(1, Offset(260f, 150f))
        assertEquals(listOf(b to true, b to false, a to true), buttonEvents())
    }

    @Test
    fun whereTouchAreasMeetTheNearestButtonWins() {
        // 195 is inside both boxes, nearer A's centre (240) than B's (150).
        input.onDown(1, Offset(196f, 150f))
        assertEquals(listOf(a to true), buttonEvents())
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
    fun releaseAllLetsGoOfEverything() {
        input.onDown(1, Offset(150f, 150f))
        input.onDown(2, Offset(500f, 120f))
        input.releaseAll()
        assertEquals(b to false, buttonEvents().last())
        assertEquals(Offset.Zero, events.filterIsInstance<InputEvent.DiscreteDirection>().last().direction)
    }
}
