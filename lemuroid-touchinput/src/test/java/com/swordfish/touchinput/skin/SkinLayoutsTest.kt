package com.swordfish.touchinput.skin

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_EXIT
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_FAST_FORWARD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_LOAD
import com.swordfish.touchinput.radial.layouts.shared.KEYCODE_QUICK_SAVE
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
            if (
                frame.x < -0.5f ||
                (frame.y < -0.5f && !besideScreens) ||
                frame.x + frame.width > width + 0.5f ||
                frame.y + frame.height > layout.height + 0.5f
            ) {
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
            SkinVariant.entries.flatMap { variant ->
                TouchControllerID.entries.mapNotNull { id ->
                    ArcaduneSkins.forController(id, variant)?.let { Triple(id, variant, it) }
                }.flatMap { (id, v, skin) ->
                    val layout = skin.portrait.copy(controls = skin.portrait.controls.filterNot { it.defaultHidden })
                    problems("$id $v portrait", layout, layout.controls.map { it.frame }, layout.width)
                }
            }
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /**
     * The four extra buttons (fast-forward, exit, save, load) sit where nothing else does, whatever
     * the skin: upright with all of them switched on, no control (or touch area) meets another.
     */
    @Test
    fun extraButtonsNeverTouchAnotherControlUpright() {
        val found =
            SkinVariant.entries.flatMap { variant ->
                TouchControllerID.entries.mapNotNull { id ->
                    ArcaduneSkins.forController(id, variant)?.let { Triple(id, variant, it) }
                }.flatMap { (id, v, skin) ->
                    val layout = skin.portrait
                    val extras = layout.controls.indices.filter { layout.controls[it].isExtraButton() }.toSet()
                    val frames = layout.controls.mapIndexed { i, c -> layout.defaultCustomization.frameOf(i, c.frame) }

                    // The name written under a control (a 4-unit gap, then a line of text about 13 high).
                    fun named(c: SkinControl) =
                        c is SkinControl.Menu || c is SkinControl.Rewind || (c is SkinControl.Button && c.labelBelow)

                    fun scaleOf(c: SkinControl) = (c as? SkinControl.Button)?.captionScale ?: 1f

                    fun nameOf(
                        f: SkinRect,
                        c: SkinControl,
                    ) = SkinRect(
                        f.centerX - 20f * scaleOf(c),
                        f.y + f.height + 4f * scaleOf(c),
                        40f * scaleOf(c),
                        13f * scaleOf(c),
                    )
                    val names =
                        extras.filter { (layout.controls[it] as SkinControl.Button).labelBelow }.flatMap { i ->
                            val extra = describe(layout.controls[i])
                            layout.controls.indices.filter { it != i }.flatMap { j ->
                                val own = nameOf(frames[i], layout.controls[i])
                                val otherName = nameOf(frames[j], layout.controls[j])
                                val clash =
                                    own.overlaps(frames[j]) || (named(layout.controls[j]) && own.overlaps(otherName))
                                if (clash) {
                                    val other = describe(layout.controls[j])
                                    listOf("$id $v portrait: the name under extra $extra meets $other")
                                } else {
                                    emptyList()
                                }
                            } +
                                layout.controls.indices
                                    .filter { it !in extras && named(layout.controls[it]) }
                                    .mapNotNull { j ->
                                        if (nameOf(frames[j], layout.controls[j]).overlaps(frames[i])) {
                                            val other = describe(layout.controls[j])
                                            "$id $v portrait: the name under $other meets extra $extra"
                                        } else {
                                            null
                                        }
                                    }
                        }
                    problems("$id $v portrait", layout, frames, layout.width)
                        .filter {
                            listOf("FAST", "EXIT", "SAVE", "LOAD").any { name -> it.contains("button '$name'") }
                        }
                        .also { assertEquals("$id has four extra buttons", 4, extras.size) } + names
                }
            }
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /** The same sideways, on every phone and tablet shape, with the four extra buttons switched on. */
    @Test
    fun extraButtonsNeverTouchAnotherControlSideways() {
        val found =
            SkinVariant.entries.flatMap { variant ->
                TouchControllerID.entries.mapNotNull { id ->
                    ArcaduneSkins.forController(id, variant)?.let { Triple(id, variant, it) }
                }.flatMap { (id, v, skin) ->
                    val layout = skin.landscape
                    val extras = layout.controls.indices.filter { layout.controls[it].isExtraButton() }.toSet()
                    val all = layout.defaultCustomization.copy(shown = extras)
                    sideways.flatMap { (w, h) ->
                        val geometry = SkinGeometry(layout, pinToEdges = true, width = w - 8f, height = h - 8f)
                        val frames = geometry.frameRects(all, rewindAvailable = true).toMap()
                        val touches = geometry.touchRects(all, rewindAvailable = true).toMap()
                        val name = "$id $v landscape ${w.toInt()}x${h.toInt()}"
                        extras.filter { it in frames }.flatMap { i ->
                            frames.filter { (j, other) ->
                                i != j &&
                                    (
                                        touches.getValue(i).coversPartOf(other) ||
                                            (touches[j]?.coversPartOf(frames.getValue(i)) ?: false)
                                    )
                            }.map { (j, _) ->
                                "$name: extra ${describe(layout.controls[i])} meets ${describe(layout.controls[j])}"
                            } +
                                // The name under an extra button (sideways only those carry one).
                                frames.getValue(i).takeIf {
                                    (layout.controls[i] as SkinControl.Button).labelBelow
                                }.let { f ->
                                    if (f == null) return@let emptyList<String>()
                                    val sc = geometry.scale
                                    val cs = (layout.controls[i] as? SkinControl.Button)?.captionScale ?: 1f

                                    fun nameOf(r: Rect) =
                                        Rect(
                                            r.center.x - 20f * sc * cs,
                                            r.bottom + 4f * sc * cs,
                                            r.center.x + 20f * sc * cs,
                                            r.bottom + 17f * sc * cs,
                                        )
                                    val own = nameOf(f)
                                    frames.filter { (j, other) ->
                                        val otherNamed =
                                            j in extras && (layout.controls[j] as SkinControl.Button).labelBelow
                                        i != j &&
                                            (own.coversPartOf(other) || (otherNamed && own.coversPartOf(nameOf(other))))
                                    }.map { (j, _) ->
                                        val extra = describe(layout.controls[i])
                                        "$name: the name under extra $extra meets ${describe(layout.controls[j])}"
                                    }
                                }
                        }
                    }
                }
            }.distinct()
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun extraButtonsStayOffTheScreens() {
        // Where the game screens really are, as the game screen places them with each skin's
        // defaults (the DS and 3DS move theirs): no extra button, or the name under it, on one.
        val found =
            skins().flatMap { (id, skin) ->
                listOf(true, false).flatMap { isLandscape ->
                    val layout = if (isLandscape) skin.landscape else skin.portrait
                    val shell = !isLandscape || layout.screenArea != null
                    if (!shell) return@flatMap emptyList<String>()
                    val extras = layout.controls.indices.filter { layout.controls[it].isExtraButton() }.toSet()
                    val all = layout.defaultCustomization.copy(shown = extras)
                    val spec = ScreenSpecs.forController(id, emptyMap(), isLandscape)
                    val sizes =
                        if (isLandscape) {
                            listOf(
                                891f to 411f,
                                800f to 360f,
                                915f to 412f,
                            )
                        } else {
                            listOf(411f to 891f, 360f to 800f, 412f to 915f)
                        }
                    sizes.flatMap { (w, h) ->
                        val placement = placeShell(skin, isLandscape, shell, spec, w, h, Rect(0f, 0f, w, h), 1f, all)
                        // Screens that run off the phone (a tuned size too big for it): nothing keeps clear of those.
                        val offPhone =
                            placement.windows.any {
                                it.top < -1f || it.bottom > h + 1f || it.left < -1f || it.right > w + 1f
                            }
                        if (offPhone) return@flatMap emptyList<String>()
                        val name = "$id ${if (isLandscape) "landscape" else "portrait"} ${w.toInt()}x${h.toInt()}"
                        val placed: Map<Int, Rect> =
                            if (isLandscape) {
                                SkinGeometry(layout, pinToEdges = true, width = w - 8f, height = h - 8f)
                                    .frameRects(all, rewindAvailable = true).toMap()
                                    .mapValues { (_, r) -> r.translate(4f, 4f) }
                            } else {
                                val unit = placement.panel.width / layout.width
                                extras.associateWith { i ->
                                    val f = all.frameOf(i, layout.controls[i].frame)
                                    Rect(
                                        placement.panel.left + f.x * unit,
                                        placement.panel.top + f.y * unit,
                                        placement.panel.left + (f.x + f.width) * unit,
                                        placement.panel.top + (f.y + f.height + 18f) * unit,
                                    )
                                }
                            }
                        extras.filter { it in placed }.flatMap { i ->
                            placement.windows.filter {
                                it.inflate(
                                    if (isLandscape && all.hasScreenChanges) {
                                        0f
                                    } else if (layout.screenPanelBelow != null) {
                                        SCREEN_OUTLINE_DP
                                    } else {
                                        SCREEN_BEZEL_DP
                                    },
                                ).overlaps(placed.getValue(i))
                            }
                                .map { "$name: extra ${describe(layout.controls[i])} is on a game screen" }
                        }
                    }
                }
            }.distinct()
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
            SkinVariant.entries.flatMap { variant ->
                TouchControllerID.entries.mapNotNull { id ->
                    ArcaduneSkins.forController(id, variant)?.let { Triple(id, variant, it) }
                }.flatMap { (id, v, skin) ->
                    val layout = skin.landscape
                    sideways.flatMap { (w, h) ->
                        val geometry = SkinGeometry(layout, pinToEdges = true, width = w - 8f, height = h - 8f)
                        val frames = geometry.frameRects(SkinCustomization(), rewindAvailable = true).toMap()
                        val touches = geometry.touchRects(SkinCustomization(), rewindAvailable = true).toMap()
                        val name = "$id $v landscape ${w.toInt()}x${h.toInt()}"
                        frames.flatMap { (i, frame) ->
                            val offScreen =
                                frame.left < -0.5f ||
                                    frame.top < -0.5f ||
                                    frame.right > w - 7.5f ||
                                    frame.bottom > h - 7.5f
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
                }
            }.distinct()
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }

    /**
     * Every Console skin's artwork (made by tools/skin-studio) matches its layouts: a picture for
     * every control the studio draws (including optional Fast-Forward and Exit), so a changed
     * layout cannot ship without its pictures. Run `gradle art` in tools/skin-studio after changing a layout.
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
                    val names =
                        drawn.flatMap { ctrl ->
                            val key = artKey(ctrl)
                            if (ctrl is SkinControl.Dpad) listOf(key) else listOf(key, "${key}_pressed")
                        } + listOfNotNull(SkinArt.BODY.takeIf { !landscape })
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
                TouchControllerID.DREAMCAST,
                TouchControllerID.SATURN,
                TouchControllerID.SATURN_3D,
                TouchControllerID.THREEDO,
                TouchControllerID.ATARI800,
                TouchControllerID.C64,
                TouchControllerID.APPLEII,
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
                TouchControllerID.DREAMCAST to listOf("Y", "X", "B", "A"),
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
        // Not 308: there the middle line of the surface falls within a dot of A's row, and snaps first.
        val surfaceH = 340f
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
                shown = setOf(8),
                screenScale = 1.2f,
                screenOffset = Offset(0f, -0.08f),
            )
        assertTrue(edited.isCustomized)
        assertTrue(edited.hasScreenChanges)
        val decoded = SkinCustomization.decode(edited.encode())
        assertTrue(decoded.isCustomized)
        assertEquals(1.25f, decoded.controlScale(2), 0.001f)
        assertEquals(1.2f, decoded.screenScale, 0.001f)
        assertEquals(-0.08f, decoded.screenOffset.y, 0.001f)
        assertTrue(3 in decoded.hidden)
        assertTrue(8 in decoded.shown)
        assertFalse(decoded.resetControl(2).hasControlChanges(2))
        assertFalse(decoded.resetScreen().hasScreenChanges)
    }

    @Test
    fun everySkinOffersOptionalFastForwardAndExitOnByDefault() {
        for ((id, skin) in skins()) {
            for ((orientation, layout) in listOf("portrait" to skin.portrait, "landscape" to skin.landscape)) {
                val ffIdx =
                    layout.controls.indexOfFirst {
                        it is SkinControl.Button && it.keyCode == KEYCODE_FAST_FORWARD && it.defaultHidden
                    }
                val exitIdx =
                    layout.controls.indexOfFirst {
                        it is SkinControl.Button && it.keyCode == KEYCODE_EXIT && !it.defaultHidden
                    }
                assertTrue("$id $orientation missing optional Fast-Forward", ffIdx >= 0)
                assertTrue("$id $orientation missing Exit", exitIdx >= 0)

                val defaultState = SkinCustomization()
                assertTrue(defaultState.isHidden(ffIdx, layout.controls[ffIdx]))
                assertFalse(
                    "$id $orientation: Exit is on by default",
                    defaultState.isHidden(exitIdx, layout.controls[exitIdx]),
                )

                val enabled = defaultState.setControlVisible(ffIdx, layout.controls[ffIdx], visible = true)
                assertFalse(enabled.isHidden(ffIdx, layout.controls[ffIdx]))

                val withoutOptional = layout.copy(controls = layout.controls.filterNot { it.defaultHidden })
                assertEquals(withoutOptional.signature(), layout.signature())
            }
        }
    }

    @Test
    fun everySkinOffersOptionalQuickSaveAndQuickLoadOffByDefault() {
        for ((id, skin) in skins()) {
            for ((orientation, layout) in listOf("portrait" to skin.portrait, "landscape" to skin.landscape)) {
                for ((name, key) in listOf("Quick Save" to KEYCODE_QUICK_SAVE, "Quick Load" to KEYCODE_QUICK_LOAD)) {
                    val index =
                        layout.controls.indexOfFirst {
                            it is SkinControl.Button && it.keyCode == key && it.defaultHidden
                        }
                    assertTrue("$id $orientation is missing the optional $name button", index >= 0)
                    assertTrue(SkinCustomization().isHidden(index, layout.controls[index]))
                    val frame = layout.controls[index].frame
                    assertTrue(
                        "$id $orientation $name starts outside the skin",
                        frame.x >= 0f && frame.x + frame.width <= layout.width,
                    )
                }
            }
        }
    }

    @Test
    fun hitTestAndPinchTargetSelectSingleButtonCleanlyEvenNearFastForward() {
        val gba = ArcaduneSkins.forController(TouchControllerID.GBA)!!.portrait
        val aIdx = gba.controls.indexOfFirst { it is SkinControl.Button && it.label == "A" }
        val ffIdx = gba.controls.indexOfFirst { it is SkinControl.Button && it.keyCode == KEYCODE_FAST_FORWARD }
        val geometry = SkinGeometry(gba, pinToEdges = false, width = 393f, height = 340f)

        val aDefault = geometry.placedFrame(aIdx, gba.controls[aIdx].frame, SkinCustomization())
        val ffBase = geometry.placedFrame(ffIdx, gba.controls[ffIdx].frame, SkinCustomization())
        // Move shrunk Fast-Forward right next to A's upper-left shoulder.
        val ffNearAOffset =
            Offset(
                (aDefault.left - 14f - ffBase.center.x) / geometry.scale,
                (aDefault.top - 10f - ffBase.center.y) / geometry.scale,
            )
        val custom =
            SkinCustomization(
                shown = setOf(ffIdx),
                scales = mapOf(ffIdx to 0.55f),
                offsets = mapOf(ffIdx to ffNearAOffset),
            )

        val aFrame = geometry.placedFrame(aIdx, gba.controls[aIdx].frame, custom)
        val ffFrame = geometry.placedFrame(ffIdx, gba.controls[ffIdx].frame, custom)

        // Tapping A's center or upper-left rim selects A cleanly (never stolen by Fast-Forward).
        assertEquals(aIdx, geometry.hitTestControl(aFrame.center, custom, rewindAvailable = true))
        val aRimPoint = Offset(aFrame.center.x - aFrame.width * 0.30f, aFrame.center.y - aFrame.height * 0.30f)
        assertEquals(aIdx, geometry.hitTestControl(aRimPoint, custom, rewindAvailable = true))

        // Tapping the shrunk Fast-Forward button (or within its forgiving 42dp halo) selects Fast-Forward.
        assertEquals(ffIdx, geometry.hitTestControl(ffFrame.center, custom, rewindAvailable = true))
        assertEquals(
            ffIdx,
            geometry.hitTestControl(Offset(ffFrame.center.x - 12f, ffFrame.center.y), custom, rewindAvailable = true),
        )

        // When a button is already selected, two-finger pinch anywhere ALWAYS resizes that selected button.
        val p0 = Offset(aFrame.left - 12f, aFrame.center.y)
        val p1 = Offset(aFrame.right + 12f, aFrame.center.y)
        assertEquals(
            ffIdx,
            geometry.resolvePinchTarget(p0, p1, selectedIndex = ffIdx, customization = custom, rewindAvailable = true),
        )
        // When nothing is selected, a two-finger pinch straddling A resolves cleanly to A.
        assertEquals(
            aIdx,
            geometry.resolvePinchTarget(p0, p1, selectedIndex = null, customization = custom, rewindAvailable = true),
        )
    }

    @Test
    fun scalingSingleButtonNeverChangesOtherButtonsAcrossAllConsoles() {
        for ((id, skin) in skins()) {
            val orientations =
                listOf(
                    Triple("portrait", skin.portrait, false to Offset(393f, 340f)),
                    Triple("landscape", skin.landscape, true to Offset(852f, 393f)),
                )
            for ((orientation, layout, surfaceSpec) in orientations) {
                val (pinToEdges, surface) = surfaceSpec
                val geometry = SkinGeometry(layout, pinToEdges = pinToEdges, width = surface.x, height = surface.y)
                val baseCustom = SkinCustomization()
                val baseFrames =
                    layout.controls.mapIndexed { idx, ctrl ->
                        geometry.placedFrame(idx, ctrl.frame, baseCustom)
                    }

                for (targetIdx in layout.controls.indices) {
                    for (targetScale in listOf(0.65f, 1.55f)) {
                        val scaledOnly = baseCustom.copy(scales = mapOf(targetIdx to targetScale))
                        val clamped =
                            layout.clampControlOffset(
                                pinToEdges = pinToEdges,
                                surfaceWidthDp = surface.x,
                                surfaceHeightDp = surface.y,
                                index = targetIdx,
                                customization = scaledOnly,
                                candidate = Offset.Zero,
                                snapToDefault = false,
                            )
                        val nextOffsets =
                            if (kotlin.math.abs(clamped.x) > 0.01f || kotlin.math.abs(clamped.y) > 0.01f) {
                                mapOf(targetIdx to clamped)
                            } else {
                                emptyMap()
                            }
                        val custom = scaledOnly.copy(offsets = nextOffsets)

                        assertEquals(setOf(targetIdx), custom.scales.keys)
                        val targetFrame = geometry.placedFrame(targetIdx, layout.controls[targetIdx].frame, custom)
                        assertEquals(
                            "$id $orientation idx=$targetIdx width",
                            baseFrames[targetIdx].width * targetScale,
                            targetFrame.width,
                            0.02f,
                        )
                        assertEquals(
                            "$id $orientation idx=$targetIdx height",
                            baseFrames[targetIdx].height * targetScale,
                            targetFrame.height,
                            0.02f,
                        )

                        for (otherIdx in layout.controls.indices) {
                            if (otherIdx == targetIdx) continue
                            val otherFrame = geometry.placedFrame(otherIdx, layout.controls[otherIdx].frame, custom)
                            assertEquals(
                                "$id $orientation target=$targetIdx mutated other=$otherIdx left",
                                baseFrames[otherIdx].left,
                                otherFrame.left,
                                0.001f,
                            )
                            assertEquals(
                                "$id $orientation target=$targetIdx mutated other=$otherIdx top",
                                baseFrames[otherIdx].top,
                                otherFrame.top,
                                0.001f,
                            )
                            assertEquals(
                                "$id $orientation target=$targetIdx mutated other=$otherIdx width",
                                baseFrames[otherIdx].width,
                                otherFrame.width,
                                0.001f,
                            )
                            assertEquals(
                                "$id $orientation target=$targetIdx mutated other=$otherIdx height",
                                baseFrames[otherIdx].height,
                                otherFrame.height,
                                0.001f,
                            )
                        }
                    }
                }

                val visibleButtons =
                    layout.controls.indices.filter { idx ->
                        val ctrl = layout.controls[idx]
                        !baseCustom.isHidden(idx, ctrl) &&
                            baseCustom.isControlAvailable(ctrl, rewindAvailable = true, fastForwardSupported = true)
                    }
                if (visibleButtons.size >= 2) {
                    val enlargedIdx = visibleButtons[0]
                    val neighborIdx = visibleButtons[1]
                    val enlargedCustom = baseCustom.copy(scales = mapOf(enlargedIdx to 1.55f))
                    assertEquals(
                        "$id $orientation neighbor center hit stolen by enlarged control",
                        neighborIdx,
                        geometry.hitTestControl(
                            baseFrames[neighborIdx].center,
                            enlargedCustom,
                            rewindAvailable = true,
                        ),
                    )
                    val p0 = baseFrames[enlargedIdx].topLeft
                    val p1 = baseFrames[enlargedIdx].bottomRight
                    assertEquals(
                        "$id $orientation pinch target stolen from selected button",
                        neighborIdx,
                        geometry.resolvePinchTarget(
                            p0,
                            p1,
                            selectedIndex = neighborIdx,
                            customization = enlargedCustom,
                            rewindAvailable = true,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun defaultFastForwardDotsMatchControllerSpeedCaps() {
        assertEquals(4, TouchControllerID.GBA.defaultFastForwardDots())
        assertEquals(4, TouchControllerID.SNES.defaultFastForwardDots())
        assertEquals(4, TouchControllerID.ATARI5200.defaultFastForwardDots())
        assertEquals(4, TouchControllerID.COLECOVISION.defaultFastForwardDots())
        assertEquals(4, TouchControllerID.MSX.defaultFastForwardDots())
        assertEquals(3, TouchControllerID.N64.defaultFastForwardDots())
        assertEquals(2, TouchControllerID.PSP.defaultFastForwardDots())
        assertEquals(2, TouchControllerID.DREAMCAST.defaultFastForwardDots())
        assertEquals(2, TouchControllerID.SATURN.defaultFastForwardDots())
        assertEquals(2, TouchControllerID.SATURN_3D.defaultFastForwardDots())
        assertEquals(0, TouchControllerID.NINTENDO_3DS.defaultFastForwardDots())
        assertEquals(0, TouchControllerID.DOS.defaultFastForwardDots())
    }
}
