package com.swordfish.touchinput.skin

import androidx.compose.ui.geometry.Rect
import com.swordfish.touchinput.radial.settings.TouchControllerID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The DS and 3DS, tuned by hand and then dressed with fixed small buttons, on every phone and
 * tablet shape, upright and sideways, with all four extra buttons switched on: nothing touches
 * anything else, nothing leaves the screen, no button sits on a game screen, and the small buttons
 * are all one size.
 */
class DsLayoutTest {
    private val ids = listOf(TouchControllerID.MELONDS, TouchControllerID.NINTENDO_3DS)

    // Sideways (width x height in dp): 16:9 up to 21:9 phones, a foldable, and 4:3 and 16:10 tablets.
    private val sideways =
        listOf(
            640f to 360f, 720f to 360f, 780f to 360f, 800f to 360f, 851f to 393f, 891f to 411f,
            915f to 412f, 960f to 380f, 840f to 700f, 600f to 450f, 1024f to 768f, 1280f to 800f,
        )

    // Upright: 16:9, 18:9, 19.5:9, 20:9, 21:9, a foldable and tablets.
    private val upright =
        listOf(
            360f to 640f, 360f to 720f, 393f to 851f, 411f to 891f, 412f to 915f, 360f to 840f,
            600f to 960f, 768f to 1024f, 800f to 1067f,
        )

    private fun name(c: SkinControl) =
        when (c) {
            is SkinControl.Button -> c.label
            is SkinControl.Dpad -> "d-pad"
            is SkinControl.Stick -> "stick"
            is SkinControl.Menu -> "menu"
            is SkinControl.Rewind -> "rewind"
        }

    private fun small(c: SkinControl) =
        c is SkinControl.Menu || (c is SkinControl.Button && (c.shape == ButtonShape.PILL))

    private fun Rect.cuts(other: Rect): Boolean {
        val w = minOf(right, other.right) - maxOf(left, other.left)
        val h = minOf(bottom, other.bottom) - maxOf(top, other.top)
        return w > 0f && h > 0f && w * h > 0.05f * minOf(width * height, other.width * other.height)
    }

    /** Every problem found, one line each. */
    private fun problems(): List<String> {
        val found = mutableListOf<String>()
        for (id in ids) {
            for (variant in SkinVariant.entries) {
                val skin = ArcaduneSkins.forController(id, variant)!!
                for (landscape in listOf(true, false)) {
                    val layout = if (landscape) skin.landscape else skin.portrait
                    val extras = layout.controls.indices.filter { layout.controls[it].isExtraButton() }.toSet()
                    val c = layout.defaultCustomization.copy(shown = extras)
                    val smalls = layout.controls.filter { small(it) }
                    val sizes =
                        smalls.map {
                            c.frameOf(
                                layout.controls.indexOf(it),
                                it.frame,
                            )
                        }.map { it.width to it.height }.distinct()
                    if (variant == SkinVariant.CONSOLE && sizes.size > 1) {
                        found += "$id ${if (landscape) "landscape" else "upright"}: small buttons differ in size $sizes"
                    }
                    val spec = ScreenSpecs.forController(id, emptyMap(), landscape)
                    for ((w, h) in if (landscape) sideways else upright) {
                        val tag = "$id $variant ${if (landscape) "landscape" else "upright"} ${w.toInt()}x${h.toInt()}"
                        val dp = 3f
                        val p =
                            placeShell(skin, landscape, true, spec, w * dp, h * dp, Rect(0f, 0f, w * dp, h * dp), dp, c)
                        val windows = p.windows.map { Rect(it.left / dp, it.top / dp, it.right / dp, it.bottom / dp) }
                        val bezel = if (layout.screenPanelBelow != null) SCREEN_OUTLINE_DP else SCREEN_BEZEL_DP
                        val frames: Map<Int, Rect>
                        val touches: Map<Int, Rect>
                        if (landscape) {
                            val g = SkinGeometry(layout, true, w - 8f, h - 8f)
                            frames =
                                g.frameRects(c, rewindAvailable = false).toMap()
                                    .mapValues { it.value.translate(4f, 4f) }
                            touches =
                                g.touchRects(c, rewindAvailable = false).toMap()
                                    .mapValues { it.value.translate(4f, 4f) }
                        } else {
                            val unit = p.panel.width / layout.width / dp
                            val left = p.panel.left / dp
                            val top = p.panel.top / dp

                            fun rect(
                                f: SkinRect,
                                reach: Float,
                            ) = Rect(
                                left + (f.x - reach) * unit,
                                top + (f.y - reach) * unit,
                                left + (f.x + f.width + reach) * unit,
                                top + (f.y + f.height + reach) * unit,
                            )
                            frames =
                                layout.controls.indices.filter { !layout.controls[it].defaultHidden || it in extras }
                                    .associateWith { rect(c.frameOf(it, layout.controls[it].frame), 0f) }
                            touches =
                                layout.controls.indices.filter { !layout.controls[it].defaultHidden || it in extras }
                                    .associateWith {
                                        rect(c.frameOf(it, layout.controls[it].frame), layout.controls[it].reach)
                                    }
                        }
                        // The screens must at least fit the phone; if they do not, nothing keeps clear of them.
                        val screensFit =
                            windows.all { it.left >= -1f && it.top >= -1f && it.right <= w + 1f && it.bottom <= h + 1f }
                        for ((i, f) in frames) {
                            val label = name(layout.controls[i])
                            if (f.left < -0.5f || f.top < -0.5f || f.right > w + 0.5f || f.bottom > h + 0.5f) {
                                // The upright L and R tabs sit above the panel on purpose, inside the screen.
                                found += "$tag: $label goes off screen"
                            }
                            for ((j, other) in frames) {
                                if (i < j && touches.getValue(i).cuts(other) && touches.getValue(j).cuts(f)) {
                                    found += "$tag: touch areas of $label and ${name(layout.controls[j])} overlap"
                                } else if (i != j && touches.getValue(i).cuts(other)) {
                                    found += "$tag: touching $label can press ${name(layout.controls[j])}"
                                }
                            }
                            if (screensFit) {
                                val grow = if (small(layout.controls[i])) bezel else 0f
                                if (windows.any {
                                        it.inflate(
                                            grow,
                                        ).cuts(f)
                                    }
                                ) {
                                    found += "$tag: $label sits on a game screen"
                                }
                            }
                        }
                        if (!screensFit) found += "$tag: the screens do not fit the phone"
                    }
                }
            }
        }
        return found.distinct()
    }

    /** Keeping clear of the buttons never costs a screen more than 40% of its tuned size. */
    @Test
    fun screensKeepMostOfTheirSize() {
        val found = mutableListOf<String>()
        for (id in ids) {
            val skin = ArcaduneSkins.forController(id)!!
            for (landscape in listOf(true, false)) {
                val layout = if (landscape) skin.landscape else skin.portrait
                val spec = ScreenSpecs.forController(id, emptyMap(), landscape)
                val c = layout.defaultCustomization
                for ((w, h) in if (landscape) sideways else upright) {
                    val dp = 3f
                    val p = placeShell(skin, landscape, true, spec, w * dp, h * dp, Rect(0f, 0f, w * dp, h * dp), dp, c)
                    p.windows.forEachIndexed { i, win ->
                        if (win.width < 0.59f * p.defaultWindows[i].width * c.screenScaleOf(i)) {
                            val percent = (100 * win.width / p.defaultWindows[i].width).toInt()
                            found += "$id ${w.toInt()}x${h.toInt()} landscape=$landscape: " +
                                "screen ${i + 1} shrank to $percent% of its plain size"
                        }
                    }
                }
            }
        }
        assertTrue(found.joinToString(System.lineSeparator()), found.isEmpty())
    }

    /** A screen the player changes is theirs: no clearing away from buttons. */
    @Test
    fun screensThePlayerChangedAreNotAdapted() {
        val skin = ArcaduneSkins.forController(TouchControllerID.MELONDS)!!
        val layout = skin.landscape
        val spec = ScreenSpecs.forController(TouchControllerID.MELONDS, emptyMap(), true)
        val dp = 3f
        val d = layout.defaultCustomization
        val mine = d.copy(screenScale = 1.5f)
        val a = placeShell(skin, true, true, spec, 1024f * dp, 768f * dp, Rect(0f, 0f, 1024f * dp, 768f * dp), dp, d)
        val b = placeShell(skin, true, true, spec, 1024f * dp, 768f * dp, Rect(0f, 0f, 1024f * dp, 768f * dp), dp, mine)
        assertTrue("a bigger screen is bigger", b.windows[0].width > a.windows[0].width)
        val plain = a.defaultWindows[0].width
        assertEquals("the player's 150% is exactly 150% of the plain size", 1.5f * plain, b.windows[0].width, 1f)
    }

    @Test
    fun dsAndN3dsAreClearOnEveryPhoneShape() {
        val found = problems()
        File("build/ds-problems.txt").also { it.parentFile.mkdirs() }.writeText(found.joinToString("\n"))
        assertTrue("${found.size} problems:\n" + found.take(60).joinToString("\n"), found.isEmpty())
    }
}
