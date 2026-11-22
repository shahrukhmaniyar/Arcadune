package com.swordfish.touchinput.skin

import com.swordfish.touchinput.radial.settings.TouchControllerID
import org.junit.Test
import java.io.File

/**
 * Not a check: writes every skin's layout (each control's box) to build/layout-sheets/layouts.txt,
 * which a script draws into contact sheets so placements can be reviewed side by side.
 */
class LayoutContactSheetTest {
    private fun SkinControl.kind(): String =
        when (this) {
            is SkinControl.Button -> "B"
            is SkinControl.Dpad -> "D"
            is SkinControl.Stick -> "S"
            is SkinControl.Menu -> "M"
            is SkinControl.Rewind -> "R"
        }

    private fun SkinControl.name(): String =
        when (this) {
            is SkinControl.Button -> label.replace(",", " ")
            is SkinControl.Dpad -> "DPAD"
            is SkinControl.Stick -> "STK"
            is SkinControl.Menu -> "MENU"
            is SkinControl.Rewind -> "REW"
        }

    @Test
    fun writeLayouts() {
        val out = File("build/layout-sheets/layouts.txt")
        out.parentFile.mkdirs()
        val lines = mutableListOf<String>()
        TouchControllerID.entries.forEach { id ->
            val skin = ArcaduneSkins.forController(id) ?: return@forEach
            // Upright: the panel in its own units (it is only ever scaled as a whole).
            val p = skin.portrait
            lines += "L,$id,portrait,${p.width},${p.height},-1,-1,-1,-1"
            p.controls.forEachIndexed { i, c ->
                val f = p.defaultCustomization.frameOf(i, c.frame)
                val hidden = if (c.defaultHidden) 1 else 0
                lines += "C,${c.kind()},${c.name()},${f.x},${f.y},${f.width},${f.height},$hidden"
            }
            // Sideways: placed the way the app places them on a 20:9 phone (891 x 411 dp, 4 dp padding).
            val l = skin.landscape
            val w = 883f
            val h = 403f
            val geometry = SkinGeometry(l, pinToEdges = true, width = w, height = h)
            val extras = l.controls.indices.filter { l.controls[it].isExtraButton() }.toSet()
            val all = l.defaultCustomization.copy(shown = extras)
            val screen =
                l.screenArea?.let { geometry.placedFrame(-1, it, SkinCustomization()) }
                    ?: androidx.compose.ui.geometry.Rect(w / 2 - h * 2 / 3, 0f, w / 2 + h * 2 / 3, h)
            lines += "L,$id,landscape,$w,$h,${screen.left},${screen.top},${screen.width},${screen.height}"
            val frames = geometry.frameRects(all, rewindAvailable = true, includeHidden = true).toMap()
            l.controls.forEachIndexed { i, c ->
                val f = frames[i] ?: return@forEachIndexed
                val hidden = if (c.defaultHidden) 1 else 0
                lines += "C,${c.kind()},${c.name()},${f.left},${f.top},${f.width},${f.height},$hidden"
            }
        }
        out.writeText(lines.joinToString("\n"))
    }
}
