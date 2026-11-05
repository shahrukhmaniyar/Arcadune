import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.skin.*
import java.io.File
import org.jetbrains.skia.EncodedImageFormat

// The skin studio's previews: every skin as it looks in a game, on several phone and tablet
// shapes, laid out the way MobileGameScreen lays it out. Run with: gradle preview.

/** Renders every skin into [args][0]; [args][1] is the artwork folder (the assets' skin-art). */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "out").apply { mkdirs() }
    val artRoot = args.getOrNull(1)?.let(::File)
    val only = System.getenv("ONLY")?.split(",")
    // name, width dp, height dp
    val screens = listOf(
        Triple("phone-tall", 360, 800), Triple("phone-16x9", 360, 640), Triple("phone-big", 412, 915), Triple("tablet", 800, 1280),
        Triple("phone-tall-land", 800, 360), Triple("phone-16x9-land", 640, 360), Triple("tablet-land", 1280, 800), Triple("tablet43-land", 1024, 768),
    )
    val scale = 2f
    for (id in TouchControllerID.entries) {
        if (only != null && id.name !in only) continue
        val variant = if (System.getenv("VARIANT") == "minimal") SkinVariant.MINIMAL else SkinVariant.CONSOLE
        val skin = ArcaduneSkins.forController(id, variant) ?: continue
        for ((name, w, h) in screens) {
            val landscape = w > h
            val art = if (variant == SkinVariant.CONSOLE && artRoot != null) loadStudioArt(artRoot, id.name, landscape) else null
            val pw = (w * scale).toInt(); val ph = (h * scale).toInt()
            ImageComposeScene(pw, ph, Density(scale)) {
                val density = LocalDensity.current
                val width = pw.toFloat(); val height = ph.toFloat()
                val layout = if (landscape) skin.landscape else skin.portrait
                val shell = !landscape || layout.screenArea != null
                val spec = ScreenSpecs.forController(id, emptyMap(), landscape)
                val placement = placeShell(skin, landscape, shell, spec, width, height, Rect(0f, 0f, width, height), density.density)
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.Black)
                        // Without a shell the game view letterboxes the picture over the whole screen.
                        val games = if (shell) placement.windows else (spec?.fitIn(placement.picture) ?: placement.windows)
                        for (window in games) {
                            drawRect(Brush.linearGradient(listOf(Color(0xFF5C94FC), Color(0xFF9CD8FC)), window.topLeft, window.bottomLeft), window.topLeft, window.size)
                            // Ground, blocks and a hill, so see-through controls are judged over a busy picture.
                            val g = window.height * 0.78f
                            drawRect(Color(0xFFC84C0C), androidx.compose.ui.geometry.Offset(window.left, window.top + g), androidx.compose.ui.geometry.Size(window.width, window.height - g))
                            drawCircle(Color(0xFF00A800), window.height * 0.22f, androidx.compose.ui.geometry.Offset(window.left + window.width * 0.2f, window.top + g))
                            for (i in 0 until 4) drawRect(Color(0xFFFC9838), androidx.compose.ui.geometry.Offset(window.left + window.width * (0.45f + i * 0.07f), window.top + window.height * 0.5f), androidx.compose.ui.geometry.Size(window.width * 0.06f, window.width * 0.06f))
                        }
                    }
                    if (shell) ConsoleShell(Modifier.fillMaxSize(), layout.palette ?: SkinPalettes.MONO, placement.windows, placement.bodyTop, placement.screenPanel)
                    if (landscape) {
                        ArcaduneSkinPreview(Modifier.fillMaxSize().padding(4.dp), layout, pinToEdges = true, shell = shell, art = art)
                    } else with(density) {
                        ArcaduneSkinPreview(
                            Modifier.absoluteOffset { IntOffset(placement.panel.left.toInt(), placement.panel.top.toInt()) }
                                .size(placement.panel.width.toDp(), placement.panel.height.toDp()),
                            layout, shell = true, art = art,
                        )
                    }
                }
            }.use { scene ->
                val img = scene.render()
                File(out, "${id.name}_$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
