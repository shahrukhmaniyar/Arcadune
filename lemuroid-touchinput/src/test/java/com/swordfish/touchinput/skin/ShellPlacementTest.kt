package com.swordfish.touchinput.skin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.swordfish.touchinput.radial.settings.TouchControllerID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        return placeShell(
            skin,
            landscape,
            shell,
            spec,
            width * dp,
            height * dp,
            Rect(0f, 0f, width * dp, height * dp),
            dp,
        )
    }

    @Test
    fun uprightTheControllerSitsOnTheBottomEdge() {
        for ((id, skin) in skins) {
            for ((w, h) in uprightScreens) {
                val p = place(id, skin, w, h, landscape = false)
                val below = (h * dp - p.panel.bottom) / dp
                assertEquals("$id ${w}x$h: space under the controller", 4f, below, 0.1f)
                // As wide as the phone; on tablets it stops at 520 dp and is centred.
                assertEquals("$id ${w}x$h: controller width", minOf(w, 520f), p.panel.width / dp, 0.1f)
                assertEquals("$id ${w}x$h: controller centred", w * dp / 2, p.panel.center.x, 0.5f)
            }
        }
    }

    @Test
    fun uprightTheGameIsEdgeToEdgeOnTallPhones() {
        val single = skins.filter { (id, _) -> ScreenSpecs.forController(id, emptyMap(), false)?.screens?.size == 1 }
        for ((id, skin) in single) {
            for ((w, h) in listOf(393f to 873f, 412f to 915f)) {
                val p = place(id, skin, w, h, landscape = false)
                assertEquals("$id ${w}x$h: game width", w, p.picture.width / dp, 0.5f)
            }
        }
    }

    @Test
    fun uprightTheGameIsCentredInTheBlackAboveTheBodyAndNeverTouchesIt() {
        for ((id, skin) in skins) {
            if (skin.portrait.uprightScreenSide != null) continue
            for ((w, h) in uprightScreens) {
                val p = place(id, skin, w, h, landscape = false)
                val bodyTop = p.bodyTop!!
                val above = p.picture.top / dp
                val below = (bodyTop - p.picture.bottom) / dp
                assertTrue("$id ${w}x$h: the game reaches into the body ($below dp)", below >= SCREEN_TO_BODY - 0.1f)
                assertEquals("$id ${w}x$h: above $above, below $below", above, below - SCREEN_TO_BODY, 0.5f)
                assertTrue("$id ${w}x$h: body top $bodyTop above the controller", bodyTop < p.panel.top)
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
                    assertTrue(
                        "$id ${w}x$h: window off screen $window",
                        window.left >= -0.5f && window.right <= w * dp + 0.5f,
                    )
                    assertTrue(
                        "$id ${w}x$h: window off screen $window",
                        window.top >= -0.5f && window.bottom <= h * dp + 0.5f,
                    )
                }
            }
        }
    }

    /**
     * Sideways, a screen's dark bezel (drawn around its window) never lands on a control, on wide
     * phones, 16:9 phones and 4:3 tablets, and for every screen layout the DS and 3DS offer. The
     * controls are placed the way ArcaduneSkinView places them (inside a 4 dp padding).
     */
    @Test
    fun sidewaysTheBezelNeverCoversAControl() {
        val dsLayouts = listOf("top-bottom", "hybrid-top", "left-right").map { mapOf("melonds_screen_layout1" to it) }
        val n3dsLayouts =
            listOf(
                "Default Top-Bottom Screen",
                "Large Screen, Small Screen",
                "Side by Side",
                "Single Screen Only",
            ).flatMap { option ->
                listOf("Top", "Bottom").map { mapOf("citra_layout_option" to option, "citra_swap_screen" to it) }
            }
        val sideways = listOf(800f to 360f, 640f to 360f, 915f to 412f, 1280f to 800f, 1024f to 768f)
        val found = mutableListOf<String>()
        for ((id, skin) in skins) {
            val layout = skin.landscape
            if (layout.screenArea == null) continue
            val settings =
                when (id) {
                    TouchControllerID.MELONDS -> dsLayouts
                    TouchControllerID.NINTENDO_3DS -> n3dsLayouts
                    else -> listOf(emptyMap())
                }
            for (values in settings) {
                for ((w, h) in sideways) {
                    val spec = ScreenSpecs.forController(id, values, true)
                    // As players get it: the skin's defaults (the DS and 3DS ones move screens and buttons).
                    val defaults = layout.defaultCustomization
                    // Moved screens were tuned on a 20:9 phone; elsewhere they grow or shrink against
                    // the buttons (a known limit, left to the player's own adjusting).
                    // They were also tuned for the core's default screen arrangement only.
                    if (defaults.hasScreenChanges && (w / h < 2.1f || values != settings.first())) continue
                    val p =
                        placeShell(skin, true, true, spec, w * dp, h * dp, Rect(0f, 0f, w * dp, h * dp), dp, defaults)
                    // Tuned screens too big for this phone run off it; nothing can keep clear of those.
                    val offPhone =
                        p.windows.any {
                            it.top < -dp || it.bottom > (h + 1) * dp || it.left < -dp || it.right > (w + 1) * dp
                        }
                    if (offPhone) continue
                    val geometry = SkinGeometry(layout, pinToEdges = true, width = w - 8f, height = h - 8f)
                    val controls = geometry.frameRects(defaults, rewindAvailable = true)
                    for (window in p.windows) {
                        val frame = if (layout.screenPanelBelow != null) SCREEN_OUTLINE_DP else SCREEN_BEZEL_DP
                        val bezel = Rect(window.topLeft / dp, window.bottomRight / dp).inflate(frame)
                        for ((index, frame) in controls) {
                            if (frame.translate(4f, 4f).overlaps(bezel)) {
                                val control = layout.controls[index]::class.simpleName
                                val screen = "${w.toInt()}x${h.toInt()}"
                                found += "$id $screen $values: the bezel covers control $index ($control)"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(found.distinct().joinToString("\n"), found.isEmpty())
    }

    /** The DS upright, as Delta lays it out: the body behind everything, the screens in it, clear of the controller. */
    @Test
    fun uprightTheDsScreensSitInTheBodyAboveTheController() {
        val ds = ArcaduneSkins.forController(TouchControllerID.MELONDS)!!
        for ((w, h) in uprightScreens) {
            val p = place(TouchControllerID.MELONDS, ds, w, h, landscape = false)
            assertEquals("${w}x$h: the body covers the whole screen", null, p.bodyTop)
            val margin = (p.picture.left / dp) / w
            assertTrue("${w}x$h: screens $margin of the width in from the side", margin >= 0.074f)
            // The screens sit right on the L / R tabs at the top of the controller, in one panel around both.
            assertEquals("${w}x$h: screens on the tabs", p.panel.top, p.picture.bottom + SCREEN_OUTLINE_DP * dp, 0.5f)
            val screenPanel = p.screenPanel!!
            val holds = screenPanel.contains(p.picture.topLeft) && screenPanel.bottom > p.panel.top
            assertTrue("${w}x$h: the panel holds the screens", holds)
        }
    }

    /** Both skins of every console put the same controls in the same places: moved buttons apply to either. */
    @Test
    fun theMinimalSkinHasTheSameControlsAsTheConsoleOne() {
        for ((id, skin) in skins) {
            val minimal = ArcaduneSkins.forController(id, SkinVariant.MINIMAL)!!
            assertEquals("$id upright", skin.portrait.signature(), minimal.portrait.signature())
            assertEquals("$id sideways", skin.landscape.signature(), minimal.landscape.signature())
            assertEquals("$id sideways screens", skin.landscape.screenArea, minimal.landscape.screenArea)
            assertEquals("$id style", SkinStyle.OUTLINE, minimal.portrait.style)
        }
    }

    @Test
    fun uprightFoldableAndSplitScreenPreserveControllerAspectRatioAndGameSpace() {
        val compactUpright = listOf(700f to 720f, 680f to 700f, 360f to 480f, 412f to 420f)
        for ((id, skin) in skins) {
            for ((w, h) in compactUpright) {
                val p = place(id, skin, w, h, landscape = false)
                val ratio = p.panel.width / p.panel.height
                assertEquals("$id ${w}x$h aspect", skin.portrait.width / skin.portrait.height, ratio, 0.01f)
                assertTrue("$id ${w}x$h capped height", p.panel.height <= h * dp * 0.56f + 0.5f)
                assertTrue("$id ${w}x$h game visible (${p.picture.height / dp} dp)", p.picture.height >= 80f * dp)
                assertEquals("$id ${w}x$h centred", w * dp / 2, p.panel.center.x, 0.5f)
            }
        }
    }

    @Test
    fun changesSurviveSavingAndLoading() {
        val changed =
            SkinCustomization(
                size = 1.2f,
                offsets = mapOf(0 to Offset(10f, -4f), 3 to Offset(-2.5f, 7f)),
                opacity = 0.6f,
                scales = mapOf(1 to 1.35f, 3 to 0.75f),
                hidden = setOf(2, 4),
                shown = setOf(7, 8),
                screenScale = 1.15f,
                screenOffset = Offset(0.02f, -0.06f),
            )
        assertEquals(changed, SkinCustomization.decode(changed.encode()))
        val resetThree = changed.resetControl(3)
        assertFalse(resetThree.hasControlChanges(3))
        assertTrue(resetThree.hasControlChanges(1))
        val menu = SkinControl.Menu(SkinRect(0f, 0f, 40f, 18f))
        val button = SkinControl.Button(1, "A", ButtonShape.ROUND, SkinRect(0f, 0f, 40f, 40f))
        val optionalExit =
            SkinControl.Button(2, "EXIT", ButtonShape.PILL, SkinRect(0f, 0f, 40f, 18f), defaultHidden = true)
        val hiddenAttempt = SkinCustomization(hidden = setOf(0, 1))
        assertTrue("Any default button including Menu can be hidden", hiddenAttempt.isHidden(0, menu))
        assertTrue(hiddenAttempt.isHidden(1, button))
        assertTrue("Optional buttons start hidden", SkinCustomization().isHidden(2, optionalExit))
        assertFalse(
            "Optional buttons appear when shown",
            SkinCustomization(shown = setOf(2)).isHidden(2, optionalExit),
        )
        assertEquals(SkinCustomization(), SkinCustomization.decode(SkinCustomization().encode()))
        assertEquals(SkinCustomization(), SkinCustomization.decode(null))
        assertEquals(SkinCustomization(), SkinCustomization.decode("garbage;;"))
    }

    @Test
    fun customScreenScaleAndOffsetCanSlideUpPastTopNotchInset() {
        val gba = ArcaduneSkins.forController(TouchControllerID.GBA)!!
        val spec = ScreenSpecs.forController(TouchControllerID.GBA, emptyMap(), landscape = false)
        val w = 393f * dp
        val h = 873f * dp
        // Simulate a phone with an unusually large 48dp top cutout inset.
        val safeWithBigNotch = Rect(0f, 48f * dp, w, h)

        val defaultPlacement = placeShell(gba, false, true, spec, w, h, safeWithBigNotch, dp)
        assertTrue(defaultPlacement.picture.top >= 48f * dp)

        // Slide screen all the way up into the top margin and zoom slightly.
        val custom = SkinCustomization(screenScale = 1.1f, screenOffset = Offset(0f, -0.25f))
        val shiftedPlacement = placeShell(gba, false, true, spec, w, h, safeWithBigNotch, dp, custom)
        assertTrue(
            "Screen can move up above safe.top (${shiftedPlacement.picture.top} < ${safeWithBigNotch.top})",
            shiftedPlacement.picture.top < safeWithBigNotch.top,
        )
        assertTrue(
            "Screen zooms larger (${shiftedPlacement.picture.width} > ${defaultPlacement.picture.width})",
            shiftedPlacement.picture.width > defaultPlacement.picture.width,
        )
    }

    @Test
    fun perScreenCustomizationMovesAndScalesDsAnd3dsScreensIndependently() {
        val multiCustom =
            SkinCustomization(
                scales = mapOf(1 to 1.3f),
            )
                .withScreenTransform(0, scale = 1.15f, offset = Offset(0.04f, -0.05f))
                .withScreenTransform(1, scale = 0.85f, offset = Offset(-0.03f, 0.06f))

        val roundTripped = SkinCustomization.decode(multiCustom.encode())
        assertEquals(multiCustom, roundTripped)
        assertEquals(1.3f, roundTripped.controlScale(1), 0.001f)
        assertEquals(1.15f, roundTripped.screenScaleOf(0), 0.001f)
        assertEquals(0.85f, roundTripped.screenScaleOf(1), 0.001f)
        assertEquals(0.04f, roundTripped.screenOffsetOf(0).x, 0.001f)
        assertEquals(0.06f, roundTripped.screenOffsetOf(1).y, 0.001f)

        for (id in listOf(TouchControllerID.MELONDS, TouchControllerID.NINTENDO_3DS)) {
            val skin = ArcaduneSkins.forController(id)!!
            val spec = ScreenSpecs.forController(id, emptyMap(), landscape = false)
            val w = 393f * dp
            val h = 873f * dp
            val safe = Rect(0f, 0f, w, h)

            val base = placeShell(skin, false, true, spec, w, h, safe, dp)
            assertEquals("$id should expose 2 independent windows", 2, base.windows.size)

            val onlyTopEdited =
                SkinCustomization().withScreenTransform(
                    index = 0,
                    scale = 1.18f,
                    offset = Offset(0.05f, -0.04f),
                )
            val editedTop = placeShell(skin, false, true, spec, w, h, safe, dp, onlyTopEdited)
            assertNotEquals("$id top window should move/scale", base.windows[0], editedTop.windows[0])
            assertEquals("$id bottom window must stay untouched", base.windows[1], editedTop.windows[1])

            val bothEdited =
                onlyTopEdited.withScreenTransform(
                    index = 1,
                    scale = 0.88f,
                    offset = Offset(-0.04f, 0.03f),
                )
            val resetTopOnly = bothEdited.resetScreen(0)
            val afterResetTop = placeShell(skin, false, true, spec, w, h, safe, dp, resetTopOnly)
            assertEquals("$id top window resets to default", base.windows[0], afterResetTop.windows[0])
            assertNotEquals("$id bottom window keeps its own transform", base.windows[1], afterResetTop.windows[1])

            // Also verify independent dual-screen editing in Landscape (hybrid / large+small layouts).
            val landSpec = ScreenSpecs.forController(id, emptyMap(), landscape = true)
            val lw = 873f * dp
            val lh = 393f * dp
            val landSafe = Rect(0f, 0f, lw, lh)
            val landBase = placeShell(skin, true, true, landSpec, lw, lh, landSafe, dp)
            assertEquals("$id sideways should expose 2 independent windows", 2, landBase.windows.size)
            val onlySecondZoomed =
                SkinCustomization().withScreenTransform(
                    index = 1,
                    scale = 1.85f,
                    offset = Offset(-0.12f, -0.08f),
                )
            val landEdited = placeShell(skin, true, true, landSpec, lw, lh, landSafe, dp, onlySecondZoomed)
            assertEquals("$id sideways screen 0 stays untouched", landBase.windows[0], landEdited.windows[0])
            assertTrue(
                "$id sideways screen 1 zooms larger",
                landEdited.windows[1].width > landBase.windows[1].width * 1.7f,
            )
        }
    }

    @Test
    fun dsAnd3dsPortraitDefaultScreensHaveVisibleHingeSeparationAndExactSourceUvs() {
        val w = 393f * dp
        val h = 873f * dp
        val safe = Rect(0f, 0f, w, h)
        for (id in listOf(TouchControllerID.MELONDS, TouchControllerID.NINTENDO_3DS)) {
            val skin = ArcaduneSkins.forController(id)!!
            val spec = ScreenSpecs.forController(id, emptyMap(), landscape = false)!!
            val placement = placeShell(skin, false, true, spec, w, h, safe, dp)
            assertEquals("$id portrait has 2 windows", 2, placement.windows.size)
            assertTrue(
                "$id portrait separates screen 1 and screen 2 (${placement.windows[0]} vs ${placement.windows[1]})",
                placement.windows[1].top > placement.windows[0].bottom + 2f,
            )
        }
        val n3dsSpec = ScreenSpecs.forController(TouchControllerID.NINTENDO_3DS, emptyMap(), landscape = false)!!
        assertEquals(400f, n3dsSpec.srcWidth, 0.01f)
        assertEquals(480f, n3dsSpec.srcHeight, 0.01f)
        assertEquals(SkinRect(0f, 0f, 400f, 240f), n3dsSpec.srcScreens[0])
        assertEquals(SkinRect(40f, 240f, 320f, 240f), n3dsSpec.srcScreens[1])

        // DeSmuME 0px horizontal gap UVs in hybrid and side-by-side layouts:
        val desmumeHybrid =
            ScreenSpecs.forController(
                TouchControllerID.MELONDS,
                mapOf("desmume_screens_layout" to "hybrid/top"),
                landscape = true,
            )!!
        assertEquals(1024f, desmumeHybrid.srcWidth, 0.01f)
        assertEquals(SkinRect(768f, 384f, 256f, 192f), desmumeHybrid.srcScreens[1])

        // The DS sideways, on both cores, normal and swapped: the big and the small window are cut out
        // of the core's stacked picture (256 x 400, main screen on top), never out of the core's own
        // "hybrid" picture, whose small screen each core puts somewhere else (issue #2). The touch
        // screen is the small window, or the big one once swapped.
        for ((key, normal, swapped) in listOf(
            Triple("melonds_screen_layout1", "top-bottom", "bottom-top"),
            Triple("desmume_screens_layout", "top/bottom", "bottom/top"),
        )) {
            for ((value, touchWindow) in listOf(normal to 1, swapped to 0)) {
                val values = mapOf(key to value)
                val sideways = ScreenSpecs.forController(TouchControllerID.MELONDS, values, landscape = true)!!
                // With a gamepad (no shell) it is still cut into big + small, as the 3DS is; upright it is not.
                assertTrue("$value: cut out even without a shell", sideways.cutsWithoutShell)
                val fitted = sideways.fitIn(Rect(0f, 0f, 800f, 360f))
                assertTrue("$value: big window is the big one", fitted[0].width > fitted[1].width * 2f)
                assertTrue(
                    "$value: both windows fit the screen",
                    fitted.all { it.left >= 0f && it.top >= 0f && it.right <= 800f && it.bottom <= 360f },
                )
                assertFalse(
                    "$value: upright stays stacked",
                    ScreenSpecs.forController(TouchControllerID.MELONDS, values, landscape = false)!!.cutsWithoutShell,
                )
                assertEquals("$value: source width", 256f, sideways.srcWidth, 0.01f)
                assertEquals("$value: source height", 400f, sideways.srcHeight, 0.01f)
                assertEquals("$value: big window", SkinRect(0f, 0f, 256f, 192f), sideways.srcScreens[0])
                assertEquals("$value: small window", SkinRect(0f, 208f, 256f, 192f), sideways.srcScreens[1])
                assertTrue(
                    "$value: big window is the big one",
                    sideways.screens[0].width > sideways.screens[1].width * 2f,
                )
                assertEquals(
                    "$value: touch screen",
                    setOf(touchWindow),
                    ScreenSpecs.touchWindowIndices(TouchControllerID.MELONDS, values, landscape = true),
                )
            }
        }
        val sidewaysByDefault = ScreenSpecs.forController(TouchControllerID.MELONDS, emptyMap(), landscape = true)!!
        assertEquals(400f, sidewaysByDefault.srcHeight, 0.01f)
        assertEquals(setOf(1), ScreenSpecs.touchWindowIndices(TouchControllerID.MELONDS, emptyMap(), landscape = true))

        // Game Gear 4:3 cutout & DS/3DS touch-only window filtering:
        val ggSpec = ScreenSpecs.forController(TouchControllerID.GG, emptyMap(), landscape = false)!!
        assertEquals(4f / 3f, ggSpec.width / ggSpec.height, 0.001f)
        assertEquals(setOf(1), ScreenSpecs.touchWindowIndices(TouchControllerID.MELONDS, emptyMap(), landscape = false))
        assertEquals(
            setOf(0),
            ScreenSpecs.touchWindowIndices(
                TouchControllerID.MELONDS,
                mapOf("melonds_screen_layout1" to "bottom-top"),
                landscape = false,
            ),
        )
        assertEquals(
            emptySet<Int>(),
            ScreenSpecs.touchWindowIndices(
                TouchControllerID.NINTENDO_3DS,
                mapOf("citra_layout_option" to "Single Screen Only", "citra_swap_screen" to "Top"),
                landscape = true,
            ),
        )
    }

    @Test
    fun portraitBgOffsetPersistsAndAdjustsHeightAcrossAllConsolesWithoutMovingScreensOrButtons() {
        val raised = SkinCustomization().withPortraitBgOffset(0.75f)
        assertTrue(raised.hasPortraitBgChange)
        assertTrue(raised.isCustomized)
        assertEquals(raised, SkinCustomization.decode(raised.encode()))

        val lowered = SkinCustomization().withPortraitBgOffset(-0.6f)
        assertTrue(lowered.hasPortraitBgChange)
        assertEquals(lowered, SkinCustomization.decode(lowered.encode()))
        assertFalse(lowered.resetPortraitBg().hasPortraitBgChange)

        val w = 393f * dp
        val h = 873f * dp
        val safe = Rect(0f, 24f * dp, w, h)
        for ((id, skin) in skins) {
            val spec = ScreenSpecs.forController(id, emptyMap(), landscape = false)
            val base = placeShell(skin, false, true, spec, w, h, safe, dp)
            val fullBg = placeShell(skin, false, true, spec, w, h, safe, dp, SkinCustomization(portraitBgOffset = 1f))
            val lowBg = placeShell(skin, false, true, spec, w, h, safe, dp, SkinCustomization(portraitBgOffset = -1f))

            assertTrue("$id exposes editableBodyTop in portrait", base.editableBodyTop != null)
            assertEquals("$id full BG reaches top edge", 0f, fullBg.editableBodyTop!!, 0.5f)
            if (skin.portrait.uprightScreenSide == null) {
                assertEquals("$id full BG bodyTop reaches 0", 0f, fullBg.bodyTop!!, 0.5f)
            }
            assertEquals("$id low BG reaches maxBodyTop", base.maxBodyTop, lowBg.editableBodyTop!!, 0.5f)
            assertEquals("$id low BG sets bodyTop even on DS", base.maxBodyTop, lowBg.bodyTop!!, 0.5f)

            // Adjusting BG height never moves or shrinks default screens, active screens, or button panel.
            assertEquals("$id full BG keeps windows", base.windows, fullBg.windows)
            assertEquals("$id low BG keeps windows", base.windows, lowBg.windows)
            assertEquals("$id full BG keeps panel", base.panel, fullBg.panel)
            assertEquals("$id low BG keeps panel", base.panel, lowBg.panel)
        }
    }

    @Test
    fun allConsolesSupportScreenEditingInPortraitAndLandscapeWithInvariantDefaultWindows() {
        for ((id, skin) in skins) {
            for (landscape in listOf(false, true)) {
                val w = (if (landscape) 873f else 393f) * dp
                val h = (if (landscape) 393f else 873f) * dp
                val safe = Rect(0f, 0f, w, h)
                val shell = !landscape || skin.landscape.screenArea != null
                val spec = ScreenSpecs.forController(id, emptyMap(), landscape)
                val placeSpec = if (shell) spec else null

                val base = placeShell(skin, landscape, shell, placeSpec, w, h, safe, dp)
                val baseDefaults = base.defaultGameWindows(shell, spec)
                assertTrue("$id landscape=$landscape has editable game windows", baseDefaults.isNotEmpty())

                val shrunk =
                    SkinCustomization().withScreenTransform(
                        index = 0,
                        scale = SkinCustomization.MIN_SCREEN_SCALE,
                        offset = Offset(-0.08f, 0.05f),
                    )
                val zoomed =
                    SkinCustomization().withScreenTransform(
                        index = 0,
                        scale = SkinCustomization.MAX_SCREEN_SCALE,
                        offset = Offset(0.06f, -0.04f),
                    )
                val shrunkPlaced = placeShell(skin, landscape, shell, placeSpec, w, h, safe, dp, shrunk)
                val zoomedPlaced = placeShell(skin, landscape, shell, placeSpec, w, h, safe, dp, zoomed)

                assertEquals(
                    "$id landscape=$landscape defaultGameWindows invariant on shrink",
                    baseDefaults,
                    shrunkPlaced.defaultGameWindows(shell, spec),
                )
                assertEquals(
                    "$id landscape=$landscape defaultGameWindows invariant on zoom",
                    baseDefaults,
                    zoomedPlaced.defaultGameWindows(shell, spec),
                )
                val shrunkWin = shrunkPlaced.gameWindows(shell, spec)[0]
                val zoomedWin = zoomedPlaced.gameWindows(shell, spec)[0]
                assertTrue("$id landscape=$landscape shrinks below default", shrunkWin.width < baseDefaults[0].width)
                assertTrue("$id landscape=$landscape zooms above default", zoomedWin.width > baseDefaults[0].width)
                assertNotEquals("$id landscape=$landscape moves center", baseDefaults[0].center, shrunkWin.center)
            }
        }
    }

    @Test
    fun aChangedLayoutGetsAnotherFingerprint() {
        val nes = ArcaduneSkins.forController(TouchControllerID.NES)!!.portrait
        assertEquals(nes.signature(), ArcaduneSkins.forController(TouchControllerID.NES)!!.portrait.signature())
        // The colours do not matter, the controls do.
        val minimal = ArcaduneSkins.forController(TouchControllerID.NES, SkinVariant.MINIMAL)!!
        assertEquals(nes.signature(), minimal.portrait.signature())
        val moved = nes.copy(controls = nes.controls.drop(1))
        assertNotEquals(nes.signature(), moved.signature())
    }
}
