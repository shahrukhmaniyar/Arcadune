package com.swordfish.touchinput.skin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.swordfish.touchinput.radial.settings.TouchControllerID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the screen and the controller go, for every console with a skin, on phones and tablets of
 * several shapes, held upright and sideways; and the saving of the player's changes.
 */
class ShellPlacementTest {
    private val skins = TouchControllerID.entries.mapNotNull { id -> ArcaduneSkins.forController(id)?.let { id to it } }

    // Upright screens in dp (width x height) at 3 px per dp: a 16:9 phone, a tall 20:9 phone, a 4:3 tablet.
    private val uprightScreens = listOf(360f to 640f, 393f to 873f, 412f to 915f, 800f to 1067f)
    private val dp = 3f

    private fun place(
        id: TouchControllerID,
        skin: ArcaduneSkin,
        width: Float,
        height: Float,
        landscape: Boolean,
    ): ShellPlacement {
        val shell = !landscape || skin.landscape.screenArea != null
        val spec = ScreenSpecs.forController(id, emptyMap(), landscape)
        return placeShell(skin, landscape, shell, spec, width * dp, height * dp, Rect(0f, 0f, width * dp, height * dp), dp)
    }

    @Test
    fun uprightTheScreenSitsRightAboveTheControllerWithTheSameSmallGap() {
        for ((id, skin) in skins) {
            for ((w, h) in uprightScreens) {
                val p = place(id, skin, w, h, landscape = false)
                val gap = (p.panel.top - p.picture.bottom) / dp
                assertTrue("$id ${w}x$h: gap $gap dp", gap >= 19.9f && gap <= 20.1f)
            }
        }
    }

    @Test
    fun uprightTheBlockIsCentredNotStuckToTheTop() {
        for ((id, skin) in skins) {
            for ((w, h) in uprightScreens) {
                val p = place(id, skin, w, h, landscape = false)
                val above = p.picture.top / dp
                val below = (h * dp - p.panel.bottom) / dp
                // The space above the screen and under the controller differ only by the fixed margins.
                assertTrue("$id ${w}x$h: above $above, below $below", kotlin.math.abs((above - 14f) - (below - 10f)) < 1f)
            }
        }
    }

    @Test
    fun uprightTheControllerKeepsItsOwnProportions() {
        for ((id, skin) in skins) {
            for ((w, h) in uprightScreens) {
                val p = place(id, skin, w, h, landscape = false)
                val ratio = p.panel.width / p.panel.height
                assertEquals("$id ${w}x$h", skin.portrait.width / skin.portrait.height, ratio, 0.01f)
                assertTrue("$id ${w}x$h: controller off screen", p.panel.bottom <= h * dp + 0.5f)
            }
        }
    }

    @Test
    fun sidewaysTheScreensStayBetweenTheWingsAndOnScreen() {
        for ((id, skin) in skins) {
            for ((h, w) in uprightScreens) {
                val p = place(id, skin, w, h, landscape = true)
                for (window in p.windows) {
                    assertTrue("$id ${w}x$h: window off screen $window", window.left >= -0.5f && window.right <= w * dp + 0.5f)
                    assertTrue("$id ${w}x$h: window off screen $window", window.top >= -0.5f && window.bottom <= h * dp + 0.5f)
                }
            }
        }
    }

    @Test
    fun changesSurviveSavingAndLoading() {
        val changed = SkinCustomization(size = 1.2f, offsets = mapOf(0 to Offset(10f, -4f), 3 to Offset(-2.5f, 7f)), opacity = 0.6f)
        assertEquals(changed, SkinCustomization.decode(changed.encode()))
        assertEquals(SkinCustomization(), SkinCustomization.decode(SkinCustomization().encode()))
        assertEquals(SkinCustomization(), SkinCustomization.decode(null))
        assertEquals(SkinCustomization(), SkinCustomization.decode("garbage;;"))
    }

    @Test
    fun aChangedLayoutGetsAnotherFingerprint() {
        val nes = ArcaduneSkins.forController(TouchControllerID.NES)!!.portrait
        assertEquals(nes.signature(), ArcaduneSkins.forController(TouchControllerID.NES)!!.portrait.signature())
        // The colours do not matter, the controls do.
        assertEquals(nes.signature(), ArcaduneSkins.forController(TouchControllerID.NES, monochrome = true)!!.portrait.signature())
        val moved = nes.copy(controls = nes.controls.drop(1))
        assertNotEquals(nes.signature(), moved.signature())
    }
}
