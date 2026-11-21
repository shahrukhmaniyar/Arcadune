package com.swordfish.touchinput.skin

import com.swordfish.touchinput.radial.settings.TouchControllerID
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Buttons of one size standing in a line (four or more, upright, extras switched on) have the same
 * gutter at both ends of the panel as between them.
 */
class RowGutterTest {
    @Test
    fun rowsOfFourHaveEqualGutters() {
        val found = mutableListOf<String>()
        for (id in TouchControllerID.entries) {
            val skin = ArcaduneSkins.forController(id) ?: continue
            val layout = skin.portrait
            val shown = layout.controls.indices.filter { layout.controls[it].defaultHidden }.toSet()
            val c = layout.defaultCustomization.copy(shown = shown)
            val frames =
                layout.controls.indices
                    .filter { !c.isHidden(it, layout.controls[it]) }
                    .filter { layout.controls[it] is SkinControl.Menu || layout.controls[it] is SkinControl.Rewind || (layout.controls[it] as? SkinControl.Button)?.shape == ButtonShape.PILL }
                    .map { it to c.frameOf(it, layout.controls[it].frame) }
            val lines = frames.groupBy { (_, f) -> Math.round(f.y * 2) to Math.round(f.width * 2) }
            for ((_, row) in lines) {
                if (row.size < 4) continue
                val sorted = row.map { it.second }.sortedBy { it.x }
                val gaps = mutableListOf(sorted.first().x)
                for (k in 1 until sorted.size) gaps += sorted[k].x - (sorted[k - 1].x + sorted[k - 1].width)
                gaps += layout.width - (sorted.last().x + sorted.last().width)
                val inner = gaps.subList(1, gaps.size - 1)
                if (inner.max() - inner.min() > 1.5f || kotlin.math.abs(gaps.first() - gaps.last()) > 1.5f) found += "$id upright: a row of ${row.size} has uneven gutters ${gaps.map { Math.round(it) }}"
            }
        }
        assertTrue(found.joinToString(System.lineSeparator()), found.isEmpty())
    }
}
