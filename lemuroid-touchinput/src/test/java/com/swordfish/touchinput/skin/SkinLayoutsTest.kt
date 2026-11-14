package com.swordfish.touchinput.skin

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
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
            // Above the controller only beside inset screens (the DS L / R tabs): inside the margin
            // the screens leave, low enough to sit next to the touch screen.
            val side = layout.uprightScreenSide?.let { it * layout.width } ?: 0f
            val besideScreens =
                frame.y < -0.5f && side > 0f && frame.y >= -100f && frame.y + frame.height <= 0f &&
                    (frame.x + frame.width + control.reach <= side || frame.x - control.reach >= layout.width - side)
            if (frame.x < -0.5f || (frame.y < -0.5f && !besideScreens) || frame.x + frame.width > width + 0.5f || frame.y + frame.height > layout.height + 0.5f) {
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

    // Sideways screens in dp: wide, ultra-wide, 16:9, foldable, 16:10 and 4:3 tablets.
    private val sideways =
        listOf(
            800f to 360f,
            960f to 380f,
            640f to 360f,
            915f to 412f,
            840f to 700f,
            600f to 450f,
            1280f to 800f,
            1024f to 768f,
        )

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
     * The DS sends what melonDS DS reads: the lid is L2 with Y. No microphone button (the core
     * listens all the time, as Delta does) and no rewind, which the app cannot do for the DS.
     */
    @Test
    fun dsControlsMatchTheCore() {
        val skin = ArcaduneSkins.forController(TouchControllerID.MELONDS)!!
        for (layout in listOf(skin.portrait, skin.landscape)) {
            val buttons = layout.controls.filterIsInstance<SkinControl.Button>()
            assertFalse(layout.controls.any { it is SkinControl.Rewind })
            assertFalse(buttons.any { it.label == "MIC" || it.keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL })
            assertFalse(buttons.any { it.label == "LID" })
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

    /**
     * Face-button diamonds stay symmetric on tall tablets and foldables instead of tearing apart
     * across horizontal screen-zone cutoffs.
     */
    @Test
    fun landscapeFaceDiamondsStayCohesiveAcrossScreenSizes() {
        val diamondControllers =
            listOf(
                TouchControllerID.PSX to listOf("△", "□", "○", "✕"),
                TouchControllerID.PSX_DUALSHOCK to listOf("△", "□", "○", "✕"),
                TouchControllerID.PSP to listOf("△", "□", "○", "✕"),
                TouchControllerID.MELONDS to listOf("X", "Y", "A", "B"),
                TouchControllerID.NINTENDO_3DS to listOf("X", "Y", "A", "B"),
                TouchControllerID.SNES to listOf("X", "Y", "A", "B"),
                TouchControllerID.WS_PORTRAIT to listOf("X1", "X4", "X2", "X3"),
            )
        for ((id, labels) in diamondControllers) {
            val layout = ArcaduneSkins.forController(id)!!.landscape
            val idx =
                labels.map { label ->
                    layout.controls.indexOfFirst { it is SkinControl.Button && it.label == label }
                }
            for ((w, h) in sideways) {
                val frames =
                    SkinGeometry(layout, pinToEdges = true, width = w - 8f, height = h - 8f)
                        .frameRects(SkinCustomization(), rewindAvailable = true)
                        .toMap()
                val topCy = frames.getValue(idx[0]).center.y
                val leftCy = frames.getValue(idx[1]).center.y
                val rightCy = frames.getValue(idx[2]).center.y
                val bottomCy = frames.getValue(idx[3]).center.y
                assertEquals("$id ${w}x$h left/right row", leftCy, rightCy, 0.5f)
                assertEquals("$id ${w}x$h diamond vertical symmetry", leftCy - topCy, bottomCy - leftCy, 0.5f)
            }
        }
    }

    @Test
    fun draggingControlAcrossScreenZonesIsContinuousAndClamped() {
        val psx = ArcaduneSkins.forController(TouchControllerID.PSX)!!.landscape
        val geometry = SkinGeometry(psx, pinToEdges = true, width = 1016f, height = 760f)
        val triangleIdx = psx.controls.indexOfFirst { it is SkinControl.Button && it.label == "△" }
        val base = psx.controls[triangleIdx].frame
        val atZero = geometry.placedFrame(triangleIdx, base, SkinCustomization())
        // Dragging down 80 skin units across the old 37%/63% boundary moves smoothly with no jump.
        val moved =
            geometry.placedFrame(
                triangleIdx,
                base,
                SkinCustomization(offsets = mapOf(triangleIdx to Offset(0f, 80f))),
            )
        assertEquals(80f * geometry.scale, moved.center.y - atZero.center.y, 0.05f)

        // Clamping on a 21:9 ultra-wide phone keeps the dragged control inside the real surface.
        val ultraWide = psx.clampControlOffset(true, 952f, 372f, triangleIdx, SkinCustomization(), Offset(600f, 600f))
        val clampedRect =
            SkinGeometry(psx, pinToEdges = true, width = 952f, height = 372f)
                .placedFrame(triangleIdx, base, SkinCustomization(offsets = mapOf(triangleIdx to ultraWide)))
        assertTrue("clamped inside right edge: $clampedRect", clampedRect.center.x <= 952f)
        assertTrue("clamped inside bottom edge: $clampedRect", clampedRect.center.y <= 372f)
    }

    @Test
    fun smartSnapAlignsToOriginCenterlinesAndSiblingControls() {
        val snes = ArcaduneSkins.forController(TouchControllerID.SNES)!!.portrait
        val bIdx = snes.controls.indexOfFirst { it is SkinControl.Button && it.label == "B" }
        val aIdx = snes.controls.indexOfFirst { it is SkinControl.Button && it.label == "A" }
        val surfaceW = 360f
        val surfaceH = 308f
        val geometry = SkinGeometry(snes, pinToEdges = false, width = surfaceW, height = surfaceH)

        // 1. Near default (0, 0) snaps cleanly back to exact origin.
        val originSnap =
            snes.snapControlOffset(
                pinToEdges = false,
                surfaceWidthDp = surfaceW,
                surfaceHeightDp = surfaceH,
                index = bIdx,
                customization = SkinCustomization(),
                candidate = Offset(2.2f, -1.8f),
                snapEnabled = true,
            )
        assertTrue(originSnap.snappedToDefault)
        assertEquals(0f, originSnap.offset.x, 0.001f)
        assertEquals(0f, originSnap.offset.y, 0.001f)

        // 2. Moving B vertically near A's row snaps B's centerY to A's centerY and emits horizontal guide.
        val aCenterY = geometry.placedFrame(aIdx, snes.controls[aIdx].frame, SkinCustomization()).center.y
        val bBaseCenterY = geometry.placedFrame(bIdx, snes.controls[bIdx].frame, SkinCustomization()).center.y
        val deltaUnitsToA = (aCenterY - bBaseCenterY) / geometry.scale
        val rowSnap =
            snes.snapControlOffset(
                pinToEdges = false,
                surfaceWidthDp = surfaceW,
                surfaceHeightDp = surfaceH,
                index = bIdx,
                customization = SkinCustomization(),
                candidate = Offset(18f, deltaUnitsToA + 2.5f),
                snapEnabled = true,
            )
        val snappedBCenterY =
            geometry.placedFrame(
                bIdx,
                snes.controls[bIdx].frame,
                SkinCustomization(offsets = mapOf(bIdx to rowSnap.offset)),
            ).center.y
        assertEquals(aCenterY, snappedBCenterY, 0.2f)
        assertEquals(aCenterY, rowSnap.horizontalGuideDp ?: -1f, 0.2f)
        assertTrue(rowSnap.alignedPeerCentersDp.isNotEmpty())

        // 3. Disabling snap leaves the raw candidate offset untouched (within bounds).
        val unsnapped =
            snes.snapControlOffset(
                pinToEdges = false,
                surfaceWidthDp = surfaceW,
                surfaceHeightDp = surfaceH,
                index = bIdx,
                customization = SkinCustomization(),
                candidate = Offset(2.2f, -1.8f),
                snapEnabled = false,
            )
        assertFalse(unsnapped.snappedToDefault)
        assertEquals(2.2f, unsnapped.offset.x, 0.05f)
        assertEquals(-1.8f, unsnapped.offset.y, 0.05f)
    }

    @Test
    fun customizationTracksEditedStateAndRoundTripsCleanly() {
        val clean = SkinCustomization()
        assertFalse(clean.isCustomized)

        val edited =
            SkinCustomization(
                size = 1.1f,
                opacity = 0.85f,
                offsets = mapOf(1 to Offset(12f, -6f)),
                scales = mapOf(2 to 1.25f),
                hidden = setOf(3),
            )
        assertTrue(edited.isCustomized)
        val decoded = SkinCustomization.decode(edited.encode())
        assertTrue(decoded.isCustomized)
        assertEquals(1.25f, decoded.controlScale(2), 0.001f)
        assertTrue(3 in decoded.hidden)
        assertFalse(decoded.resetControl(2).hasControlChanges(2))
    }
}
