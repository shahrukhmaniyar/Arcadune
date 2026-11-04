package com.swordfish.touchinput.skin

import com.swordfish.touchinput.radial.settings.TouchControllerID
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

    /** Where landscape controls land on a screen [width] units wide, as ArcaduneSkinView pins them. */
    private fun SkinLayout.pinned(
        frame: SkinRect,
        width: Float,
    ): SkinRect {
        val x =
            when {
                frame.centerX < this.width * 0.37f -> frame.x
                frame.centerX > this.width * 0.63f -> width - (this.width - frame.x)
                else -> width / 2 + (frame.x - this.width / 2)
            }
        return frame.copy(x = x)
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

    @Test
    fun landscapeControlsDoNotOverlapOnPhonesAndTablets() {
        // A wide phone (19.5:9), and the narrowest a skin is ever laid out: on a screen narrower than
        // the skin (4:3 tablets) the controls shrink to the width, so the sides never close in further.
        val found =
            skins().flatMap { (id, skin) ->
                val layout = skin.landscape
                listOf(layout.height * 19.5f / 9f, layout.width).flatMap { width ->
                    val frames = layout.controls.map { layout.pinned(it.frame, width) }
                    problems("$id landscape ${"%.0f".format(width)}", layout, frames, width)
                }
            }.distinct()
        assertTrue(found.joinToString("\n"), found.isEmpty())
    }
}
