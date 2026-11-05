package com.swordfish.touchinput.skin

import android.view.KeyEvent
import androidx.compose.ui.geometry.Rect
import com.swordfish.touchinput.radial.settings.TouchControllerID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every skin, in portrait and landscape: each control fits inside the skin, and no control's touch
 * area (its frame plus its reach) covers another control, or a press there would go to the wrong
 * one (as the 3DS d-pad did with the menu button).
 */
class SkinLayoutsTest {
    private fun skins() =
        TouchControllerID.entries.mapNotNull { id -> ArcaduneSkins.forController(id)?.let { id to it } }

    private fun SkinRect.grow(by: Float) = SkinRect(x - by, y - by, width + 2 * by, height + 2 * by)

    /**
     * True when this touch area covers a real part of [other]. Buttons in a diamond meet only at the
     * corners of their boxes (that is how two can be pressed together), so a sliver under 5% of the
     * smaller control does not count.
     */
    private fun SkinRect.overlaps(other: SkinRect): Boolean {
        val w = minOf(x + width, other.x + other.width) - maxOf(x, other.x)
        val h = minOf(y + height, other.y + other.height) - maxOf(y, other.y)
        if (w <= 0 || h <= 0) return false
        return w * h > 0.05f * minOf(width * height, other.width * other.height)
    }

    private fun problems(
        name: String,
        layout: SkinLayout,
        frames: List<SkinRect>,
        width: Float,
    ): List<String> {
        val found = mutableListOf<String>()
        layout.controls.forEachIndexed { i, control ->
            val frame = frames[i]
            if (frame.x < -0.5f || frame.y < -0.5f || frame.x + frame.width > width + 0.5f || frame.y + frame.height > layout.height + 0.5f) {
                found += "$name: ${describe(control)} goes outside the skin"
            }
            val touch = frame.grow(control.reach)
            layout.controls.forEachIndexed { j, other ->
                if (i != j && touch.overlaps(frames[j])) {
                    found += "$name: touching ${describe(control)} can press ${describe(other)}"
                }
            }
        }
        return found
    }

    private fun describe(control: SkinControl) =
        when (control) {
            is SkinControl.Button -> "button '${control.label}'"
            is SkinControl.Dpad -> "d-pad (${control.look})"
            is SkinControl.Stick -> "stick"
            is SkinControl.Menu -> "menu"
            is SkinControl.Rewind -> "rewind"
        }

    @Test
    fun portraitControlsDoNotOverlap() {
        val found =
            skins().flatMap { (id, skin) ->
                val layout = skin.portrait
                problems("$id portrait", layout, layout.controls.map { it.frame }, layout.width)
            }
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /** Rect version of [overlaps], for controls placed on a screen (in dp). */
    private fun Rect.coversPartOf(other: Rect): Boolean {
        val w = minOf(right, other.right) - maxOf(left, other.left)
        val h = minOf(bottom, other.bottom) - maxOf(top, other.top)
        if (w <= 0 || h <= 0) return false
        return w * h > 0.05f * minOf(width * height, other.width * other.height)
    }

    // Sideways screens in dp: wide, 16:9 and small 4:3 phones, a 16:10 and a 4:3 tablet.
    private val sideways = listOf(800f to 360f, 640f to 360f, 915f to 412f, 600f to 450f, 1280f to 800f, 1024f to 768f)

    @Test
    fun landscapeControlsDoNotOverlapOnPhonesAndTablets() {
        // Placed as ArcaduneSkinView places them, inside its 4 dp padding.
        val found =
            skins().flatMap { (id, skin) ->
                val layout = skin.landscape
                sideways.flatMap { (w, h) ->
                    val geometry = SkinGeometry(layout, pinToEdges = true, width = w - 8f, height = h - 8f)
                    val frames = geometry.frameRects(SkinCustomization(), rewindAvailable = true).toMap()
                    val touches = geometry.touchRects(SkinCustomization(), rewindAvailable = true).toMap()
                    val name = "$id landscape ${w.toInt()}x${h.toInt()}"
                    frames.flatMap { (i, frame) ->
                        val offScreen =
                            frame.left < -0.5f || frame.top < -0.5f || frame.right > w - 7.5f || frame.bottom > h - 7.5f
                        val outside =
                            if (offScreen) {
                                listOf("$name: ${describe(layout.controls[i])} goes off screen")
                            } else {
                                emptyList()
                            }
                        outside +
                            frames.filter { (j, other) -> i != j && touches.getValue(i).coversPartOf(other) }
                                .map { (j, _) ->
                                    val pressed = describe(layout.controls[j])
                                    "$name: touching ${describe(layout.controls[i])} can press $pressed"
                                }
                    }
                }
            }.distinct()
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /**
     * Every Console skin's artwork (made by tools/skin-studio) matches its layouts: a picture for
     * every control the studio draws, so a changed layout cannot ship without its pictures. Run
     * `gradle art` in tools/skin-studio after changing a layout.
     */
    @Test
    fun everyConsoleSkinHasItsArtwork() {
        val assets =
            generateSequence(java.io.File("").absoluteFile) { it.parentFile }
                .map { it.resolve("lemuroid-touchinput/src/main/assets") }
                .firstOrNull { it.isDirectory }
                ?: java.io.File("src/main/assets")
        val missing =
            skins().flatMap { (id, skin) ->
                listOf(false to skin.portrait, true to skin.landscape).flatMap { (landscape, layout) ->
                    if (layout.style != SkinStyle.CONSOLE || layout.palette == null) return@flatMap emptyList()
                    val folder = assets.resolve(SkinArt.folder(id.name, landscape))
                    val drawn =
                        layout.controls.filter { control ->
                            when (control) {
                                is SkinControl.Stick -> false
                                is SkinControl.Dpad -> control.look == DpadLook.CROSS
                                else -> true
                            }
                        }
                    val names = drawn.map { artKey(it) } + listOfNotNull(SkinArt.BODY.takeIf { !landscape })
                    names.distinct().filterNot { folder.resolve("$it.webp").isFile }.map { "$folder/$it.webp" }
                }
            }
        val message = "Missing artwork (run gradle art in tools/skin-studio):\n" + missing.joinToString("\n")
        assertTrue(message, missing.isEmpty())
    }

    /**
     * Full-screen landscape skins keep thumb-sized controls: on a 16:9 phone the d-pad is as big as
     * on a tall one (it does not shrink to fit the width), and on a tablet it stops growing.
     */
    @Test
    fun fullScreenControlsKeepTheirSizeOnEveryScreen() {
        for ((id, skin) in skins()) {
            val layout = skin.landscape
            if (layout.screenArea != null) continue
            val pad = layout.controls.indexOfFirst { it is SkinControl.Dpad }
            if (pad < 0) continue

            fun padSize(
                w: Float,
                h: Float,
            ) = SkinGeometry(layout, pinToEdges = true, width = w, height = h)
                .frameRects(SkinCustomization(), rewindAvailable = true).toMap().getValue(pad).width
            val tall = padSize(792f, 352f)
            assertEquals("$id: d-pad on a 16:9 phone", tall, padSize(632f, 352f), 0.5f)
            assertTrue("$id: d-pad on a tablet ${padSize(1272f, 792f)} dp", padSize(1272f, 792f) <= tall * 1.3f)
        }
    }

    /**
     * The DS sends what melonDS DS reads: the microphone is L3, the lid is L2 with Y. And it has
     * no rewind, which the app cannot do for the DS.
     */
    @Test
    fun dsControlsMatchTheCore() {
        val skin = ArcaduneSkins.forController(TouchControllerID.MELONDS)!!
        for (layout in listOf(skin.portrait, skin.landscape)) {
            val buttons = layout.controls.filterIsInstance<SkinControl.Button>()
            assertFalse(layout.controls.any { it is SkinControl.Rewind })
            val mic = buttons.single { it.label == "MIC" }
            assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBL, mic.keyCode)
            assertEquals(null, mic.alsoKey)
            val lid = buttons.single { it.label == "LID" }
            assertEquals(KeyEvent.KEYCODE_BUTTON_L2, lid.keyCode)
            assertEquals(KeyEvent.KEYCODE_BUTTON_Y, lid.alsoKey)
        }
    }

    /**
     * Consoles the app cannot rewind (their save states are too big to take twice a second; see
     * GameViewModelRewind.SUPPORTED_SYSTEMS) have no rewind button, not even a hidden one.
     */
    @Test
    fun consolesWithoutRewindHaveNoRewindButton() {
        val noRewind =
            listOf(
                TouchControllerID.N64,
                TouchControllerID.PSX,
                TouchControllerID.PSX_DUALSHOCK,
                TouchControllerID.PSP,
                TouchControllerID.MELONDS,
                TouchControllerID.NINTENDO_3DS,
                TouchControllerID.ARCADE_4,
                TouchControllerID.ARCADE_6,
            )
        for (id in noRewind) {
            for (variant in SkinVariant.entries) {
                val skin = ArcaduneSkins.forController(id, variant)!!
                for (layout in listOf(skin.portrait, skin.landscape)) {
                    assertFalse("$id $variant", layout.controls.any { it is SkinControl.Rewind })
                }
            }
        }
    }
}
