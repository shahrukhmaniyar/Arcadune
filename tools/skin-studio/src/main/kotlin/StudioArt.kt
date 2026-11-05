// The skin studio's artwork: renders each console's body overlay and controls as pictures (see
// SkinArt in lemuroid-touchinput) with what live drawing on a phone cannot afford: blurred
// shadows, plastic grain, gloss, letters moulded into the plastic. Run with: gradle art.

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.skin.ART_BLEED
import com.swordfish.touchinput.skin.ArcaduneSkins
import com.swordfish.touchinput.skin.ButtonShape
import com.swordfish.touchinput.skin.DpadLook
import com.swordfish.touchinput.skin.SkinArt
import com.swordfish.touchinput.skin.SkinControl
import com.swordfish.touchinput.skin.SkinLayout
import com.swordfish.touchinput.skin.SkinStyle
import com.swordfish.touchinput.skin.artCarriesLabel
import com.swordfish.touchinput.skin.artKey
import org.jetbrains.skia.*
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Pixels per skin unit of the art: about what a phone shows them at. */
const val K = 3f

private fun c(color: androidx.compose.ui.graphics.Color) = color.toArgb()

private fun shade(argb: Int, amount: Float): Int {
    val a = (argb ushr 24) and 0xFF
    var r = (argb shr 16) and 0xFF
    var g = (argb shr 8) and 0xFF
    var b = argb and 0xFF
    val t = if (amount >= 0) 255 else 0
    val k = kotlin.math.abs(amount)
    r = (r + (t - r) * k).toInt(); g = (g + (t - g) * k).toInt(); b = (b + (t - b) * k).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun alpha(argb: Int, a: Float) = (((a * 255).toInt().coerceIn(0, 255)) shl 24) or (argb and 0xFFFFFF)

private fun lum(argb: Int): Float {
    val r = ((argb shr 16) and 0xFF) / 255f; val g = ((argb shr 8) and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * [fill], or a shade of it that stands out from [body] (the same rule as the app's code-drawn
 * controls): a dark d-pad on a dark body would all but vanish.
 */
private fun standingOut(fill: Int, body: Int): Int {
    if (kotlin.math.abs(lum(fill) - lum(body)) >= 0.05f) return fill
    return if (lum(body) < 0.3f) shade(fill, 0.2f) else shade(fill, -0.25f)
}

private fun paint(color: Int = 0xFF000000.toInt()) = Paint().apply { isAntiAlias = true; this.color = color }

private fun blurred(color: Int, sigma: Float) = paint(color).apply { maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma, true) }

private val inter: (Int) -> Typeface = { weight ->
    FontMgr.default.matchFamilyStyle("Inter", FontStyle(weight, 5, FontSlant.UPRIGHT)) ?: Typeface.makeDefault()
}

private fun noise(seed: Int): Shader {
    val size = 128
    val rnd = java.util.Random(seed.toLong())
    val bytes = ByteArray(size * size * 4)
    for (i in 0 until size * size) {
        val v = rnd.nextInt(256).toByte()
        bytes[i * 4] = v; bytes[i * 4 + 1] = v; bytes[i * 4 + 2] = v; bytes[i * 4 + 3] = 255.toByte()
    }
    val image = Image.makeRaster(ImageInfo.makeN32(size, size, ColorAlphaType.OPAQUE), bytes, size * 4)
    return image.makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT, null)
}

/** The plastic of a part: soft shadow, side, crisp edge, lit face, gloss, a little rim light. */
private fun Canvas.plastic(
    shape: Path,
    bounds: Rect,
    fill: Int,
    pressed: Boolean,
    extent: Float,
    flat: Boolean = false,
) {
    val down = if (pressed) extent * 0.022f else 0f
    // Shadows: a wide soft one and a tight contact one.
    if (!pressed) {
        save(); translate(0f, extent * 0.07f); drawPath(shape, blurred(0x55000000, extent * 0.07f)); restore()
    }
    save(); translate(0f, extent * 0.025f + down * 0.4f); drawPath(shape, blurred(0x66000000, extent * 0.018f)); restore()
    save(); translate(0f, down)
    // The side, then the face a little higher.
    save(); translate(0f, extent * (if (pressed) 0.012f else 0.035f)); drawPath(shape, paint(shade(fill, -0.45f))); restore()
    val face = if (pressed) shade(fill, -0.1f) else fill
    val faceShader =
        if (flat) {
            Shader.makeLinearGradient(0f, bounds.top, 0f, bounds.bottom,
                intArrayOf(shade(face, 0.14f), face, shade(face, -0.1f)), floatArrayOf(0f, 0.5f, 1f), GradientStyle.DEFAULT)
        } else Shader.makeRadialGradient(
            bounds.left + bounds.width * 0.36f, bounds.top + bounds.height * 0.28f, max(bounds.width, bounds.height) * 0.95f,
            intArrayOf(shade(face, 0.22f), face, shade(face, -0.22f)), floatArrayOf(0f, 0.45f, 1f), GradientStyle.DEFAULT,
        )
    drawPath(shape, paint().apply { shader = faceShader })
    save(); clipPath(shape, ClipMode.INTERSECT, true)
    // Grain, gloss on top, rim light at the bottom.
    drawPath(shape, paint().apply { shader = noise(7); alpha = 9; blendMode = BlendMode.OVERLAY })
    val glossTop = bounds.top + bounds.height * 0.06f
    if (!flat) drawOval(
        Rect.makeXYWH(bounds.left + bounds.width * 0.16f, glossTop, bounds.width * 0.68f, bounds.height * 0.42f),
        paint().apply {
            shader = Shader.makeLinearGradient(0f, glossTop, 0f, glossTop + bounds.height * 0.42f,
                intArrayOf(alpha(0xFFFFFF, if (pressed) 0.18f else 0.34f), alpha(0xFFFFFF, 0f)), null, GradientStyle.DEFAULT)
        },
    )
    save(); translate(0f, -extent * 0.02f)
    drawPath(shape, paint(alpha(0xFFFFFF, 0.12f)).apply { mode = PaintMode.STROKE; strokeWidth = extent * 0.03f })
    restore()
    restore()
    // A crisp thin edge.
    drawPath(shape, paint(alpha(shade(fill, -0.6f), 0.9f)).apply { mode = PaintMode.STROKE; strokeWidth = max(1.5f, extent * 0.012f) })
    restore()
}

/** Letters moulded into the plastic: a light edge under them, a dark one above, in [color]. */
private fun Canvas.moulded(text: String, font: Font, cx: Float, cy: Float, color: Int, fill: Int, size: Float, pressed: Boolean) {
    val line = TextLine.make(text, font)
    val x = cx - line.width / 2
    val y = cy + (line.capHeight) / 2 + (if (pressed) size * 0.022f else 0f)
    val lightLetters = lum(color) > lum(fill)
    if (lightLetters) {
        drawTextLine(line, x, y + size * 0.012f, paint(alpha(0x000000, 0.35f)))
    } else {
        drawTextLine(line, x, y + size * 0.012f, paint(alpha(0xFFFFFF, 0.28f)))
        drawTextLine(line, x, y - size * 0.008f, paint(alpha(0x000000, 0.35f)))
    }
    drawTextLine(line, x, y, paint(color))
}

private fun rrect(bounds: Rect, radius: Float) = Path().addRRect(RRect.makeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom, radius))

/** A polygon through [points] with every corner rounded, by [radius] (outside) or [fillet] (inside). */
private fun roundedPolygon(points: List<Pair<Float, Float>>, radius: (Int) -> Float): Path {
    val path = Path()
    val n = points.size
    fun towards(from: Pair<Float, Float>, to: Pair<Float, Float>, d: Float): Pair<Float, Float> {
        val dx = to.first - from.first; val dy = to.second - from.second
        val len = kotlin.math.hypot(dx, dy)
        return Pair(from.first + dx / len * d, from.second + dy / len * d)
    }
    for (i in 0 until n) {
        val prev = points[(i - 1 + n) % n]; val at = points[i]; val next = points[(i + 1) % n]
        val r = radius(i)
        val a = towards(at, prev, r); val b = towards(at, next, r)
        if (i == 0) path.moveTo(a.first, a.second) else path.lineTo(a.first, a.second)
        // A quarter circle approximated by a conic through the corner.
        path.conicTo(at.first, at.second, b.first, b.second, 0.7071f)
    }
    path.closePath()
    return path
}

/** A d-pad cross with rounded outside corners and rounded inside ones, where the arms meet. */
private fun cross(bounds: Rect): Path {
    val full = min(bounds.width, bounds.height)
    val l = bounds.left + (bounds.width - full) / 2
    val t = bounds.top + (bounds.height - full) / 2
    val arm = full / 3f
    val x0 = l; val x1 = l + arm; val x2 = l + 2 * arm; val x3 = l + full
    val y0 = t; val y1 = t + arm; val y2 = t + 2 * arm; val y3 = t + full
    // Clockwise from the top of the up arm; the inside corners are every third point.
    val points =
        listOf(
            x1 to y0, x2 to y0, x2 to y1, x3 to y1, x3 to y2, x2 to y2,
            x2 to y3, x1 to y3, x1 to y2, x0 to y2, x0 to y1, x1 to y1,
        )
    val inside = setOf(2, 5, 8, 11)
    return roundedPolygon(points) { i -> if (i in inside) arm * 0.2f else arm * 0.16f }
}

private fun Canvas.dpad(bounds: Rect, fill: Int, mark: Int, pressed: Boolean) {
    val shape = cross(bounds)
    val extent = min(bounds.width, bounds.height)
    plastic(shape, bounds, fill, pressed, extent, flat = true)
    val full = extent
    val l = bounds.left + (bounds.width - full) / 2
    val t = bounds.top + (bounds.height - full) / 2
    val arm = full / 3f
    val cx = l + full / 2; val cy = t + full / 2
    // Arrows pressed into the arms.
    fun tri(tx: Float, ty: Float, ax: Float, ay: Float, bx: Float, by: Float) = Path().apply { moveTo(tx, ty); lineTo(ax, ay); lineTo(bx, by); closePath() }
    val far = arm * 1.18f; val near = arm * 0.80f; val half = arm * 0.2f
    val arrows = listOf(
        tri(cx, cy - far, cx - half, cy - near, cx + half, cy - near),
        tri(cx, cy + far, cx - half, cy + near, cx + half, cy + near),
        tri(cx - far, cy, cx - near, cy - half, cx - near, cy + half),
        tri(cx + far, cy, cx + near, cy - half, cx + near, cy + half),
    )
    for (p in arrows) {
        save(); translate(0f, extent * 0.006f); drawPath(p, paint(alpha(0xFFFFFF, 0.14f))); restore()
        drawPath(p, paint(mark))
    }
    // A shallow dish in the middle: darker at its top edge, lighter at its bottom one.
    val r = arm * 0.3f
    drawCircle(cx, cy, r, paint().apply {
        shader = Shader.makeLinearGradient(0f, cy - r, 0f, cy + r, intArrayOf(shade(fill, -0.3f), shade(fill, 0.08f)), null, GradientStyle.DEFAULT)
    })
    drawCircle(cx, cy, r, paint(alpha(shade(fill, -0.6f), 0.5f)).apply { mode = PaintMode.STROKE; strokeWidth = extent * 0.008f })
}

/**
 * Renders [control]'s picture: its frame plus its reach and [ART_BLEED] on every side (for the
 * shadow), at [K] pixels per unit. Null when the app draws the control itself.
 */
fun renderPart(layout: SkinLayout, control: SkinControl, pressed: Boolean): Image? {
    val palette = layout.palette ?: return null
    val reach = control.reach + ART_BLEED
    val w = ((control.frame.width + 2 * reach) * K).toInt()
    val h = ((control.frame.height + 2 * reach) * K).toInt()
    val surface = Surface.makeRasterN32Premul(w, h)
    val canvas = surface.canvas
    val bounds = Rect.makeXYWH(reach * K, reach * K, control.frame.width * K, control.frame.height * K)
    val extent = min(bounds.width, bounds.height)
    when (control) {
        is SkinControl.Dpad -> {
            if (control.look != DpadLook.CROSS) return null
            val fill = standingOut(c(palette.dpad), c(palette.body))
            val mark = if (lum(fill) < 0.2f) shade(fill, 0.22f) else shade(fill, -0.32f)
            canvas.dpad(bounds, fill, mark, pressed)
        }
        is SkinControl.Stick -> return null
        else -> {
            val key = (control as? SkinControl.Button)?.keyCode
            val shape =
                when (control) {
                    is SkinControl.Button -> control.shape
                    else -> if (control.frame.width < control.frame.height * 1.3f) ButtonShape.ROUND else ButtonShape.PILL
                }
            val fill =
                when {
                    key != null && shape == ButtonShape.SHOULDER -> c(palette.shoulder)
                    key != null && shape == ButtonShape.ROUND -> c(palette.fillOf(key))
                    else -> c(palette.small)
                }
            // Room under the face for the side and the shadow.
            val inset = extent * 0.04f
            val face = Rect.makeLTRB(bounds.left + inset, bounds.top + inset * 0.5f, bounds.right - inset, bounds.bottom - inset * 1.5f)
            val path =
                when (shape) {
                    ButtonShape.ROUND -> Path().addOval(face)
                    ButtonShape.PILL -> rrect(face, face.height / 2)
                    ButtonShape.SHOULDER -> rrect(face, face.height * 0.45f)
                }
            canvas.plastic(path, face, standingOut(fill, c(palette.body)), pressed, extent)
            if (control is SkinControl.Button && artCarriesLabel(control)) {
                val label =
                    when (shape) {
                        ButtonShape.SHOULDER -> c(palette.shoulderLabel)
                        else -> c(palette.labelOf(control.keyCode))
                    }
                val size = if (shape == ButtonShape.ROUND) face.height * 0.44f else face.height * 0.52f
                val font = Font(inter(if (shape == ButtonShape.ROUND) 600 else 700), size)
                canvas.moulded(control.label, font, face.left + face.width / 2, face.top + face.height / 2, label, fill, extent, pressed)
            }
        }
    }
    return surface.makeImageSnapshot()
}

/**
 * The body's overlay, drawn over the body colour the app already paints: a soft sheen, a
 * wordmark moulded into the plastic and (handhelds) a speaker grille. Transparent elsewhere, so it
 * sits on any shade of the body without a seam. The grain is one small tile the app repeats
 * over the body ([renderGrain]).
 */
fun renderBody(layout: SkinLayout, grille: Boolean): Image {
    val palette = layout.palette!!
    val w = (layout.width * K).toInt(); val h = (layout.height * K).toInt()
    val surface = Surface.makeRasterN32Premul(w, h)
    val canvas = surface.canvas
    val body = c(palette.body)
    val dark = lum(body) < 0.3f
    // A broad soft sheen from the top left, like light on moulded plastic.
    canvas.drawRect(Rect.makeWH(w.toFloat(), h.toFloat()), paint().apply {
        shader = Shader.makeRadialGradient(w * 0.2f, -h * 0.1f, w * 0.9f,
            intArrayOf(alpha(0xFFFFFF, if (dark) 0.07f else 0.16f), alpha(0xFFFFFF, 0f)), null, GradientStyle.DEFAULT)
    })
    // The body darkens a touch towards the bottom edge.
    canvas.drawRect(Rect.makeWH(w.toFloat(), h.toFloat()), paint().apply {
        shader = Shader.makeLinearGradient(0f, h * 0.7f, 0f, h.toFloat(), intArrayOf(alpha(0x000000, 0f), alpha(0x000000, 0.08f)), null, GradientStyle.DEFAULT)
    })
    if (grille) {
        // Six slanted slots, low on the right, like a handheld's speaker.
        val slot = paint().apply { mode = PaintMode.STROKE; strokeWidth = 3.2f * K; strokeCap = PaintStrokeCap.ROUND }
        for (i in 0 until 6) {
            val x = (layout.width - 66 + i * 9f) * K
            val y0 = (layout.height - 84) * K; val y1 = (layout.height - 108) * K
            slot.color = alpha(0xFFFFFF, if (dark) 0.08f else 0.4f)
            canvas.drawLine(x - 6 * K, y0 + 1.2f * K, x + 6 * K, y1 + 1.2f * K, slot)
            slot.color = alpha(shade(body, -0.5f), 0.55f)
            canvas.drawLine(x - 6 * K, y0, x + 6 * K, y1, slot)
        }
    }
    // The wordmark, moulded into the plastic at the bottom, where no control or name is.
    val font = Font(inter(700), 7.5f * K)
    val line = TextLine.make("ARCADUNE", font)
    val x = (layout.width / 2) * K - line.width / 2
    val y = (layout.height - 8) * K
    val mark = Rect.makeLTRB(x / K, y / K - 9f, (x + line.width) / K, y / K + 2f)
    val clear =
        layout.controls.none { control ->
            val f = control.frame
            // A name goes up to 16 units under small buttons and buttons named underneath.
            val captioned = control is SkinControl.Menu || control is SkinControl.Rewind || (control as? SkinControl.Button)?.labelBelow == true
            val bottom = f.y + f.height + if (captioned) 16f else 0f
            // And some room beside it, so it never squeezes between two names.
            f.x < mark.right + 8f && f.x + f.width > mark.left - 8f && f.y < mark.bottom && bottom > mark.top
        }
    if (clear) {
        canvas.drawTextLine(line, x, y + 0.5f * K, paint(alpha(0xFFFFFF, if (dark) 0.06f else 0.35f)))
        canvas.drawTextLine(line, x, y, paint(alpha(shade(body, -0.4f), 0.7f)))
    }
    return surface.makeImageSnapshot()
}

/** The plastic's grain: a small tile of fine noise, repeated over the body by the app. */
fun renderGrain(): Image {
    val size = 128
    val surface = Surface.makeRasterN32Premul(size, size)
    surface.canvas.drawRect(Rect.makeWH(size.toFloat(), size.toFloat()), paint().apply { shader = noise(3) })
    return surface.makeImageSnapshot()
}

/** The body colour the app paints under the overlay (UprightBody), for previews. */
fun renderComposite(layout: SkinLayout, grille: Boolean, pressedIndex: Int = -1): Image {
    val palette = layout.palette!!
    val w = (layout.width * K).toInt(); val h = (layout.height * K).toInt()
    val surface = Surface.makeRasterN32Premul(w, h)
    val canvas = surface.canvas
    canvas.drawRect(Rect.makeWH(w.toFloat(), h.toFloat()), paint().apply {
        shader = Shader.makeLinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(c(palette.body), c(palette.bodyShade)), null, GradientStyle.DEFAULT)
    })
    canvas.drawImage(renderBody(layout, grille), 0f, 0f)
    layout.controls.forEachIndexed { i, control ->
        val part = renderPart(layout, control, i == pressedIndex) ?: return@forEachIndexed
        canvas.drawImage(part, (control.frame.x - control.reach - ART_BLEED) * K, (control.frame.y - control.reach - ART_BLEED) * K)
    }
    return surface.makeImageSnapshot()
}

/** The handhelds whose upright body has a speaker grille. */
private val Grilles = setOf(TouchControllerID.GB, TouchControllerID.GBA, TouchControllerID.GG, TouchControllerID.NGP, TouchControllerID.LYNX)

private fun Image.webp(): ByteArray = encodeToData(EncodedImageFormat.WEBP, 90)!!.bytes

/** Writes every console's artwork (the Console skins) into [args][0], the assets' skin-art folder. */
fun main(args: Array<String>) {
    val root = File(args.first())
    root.deleteRecursively()
    var files = 0
    var bytes = 0L
    for (id in TouchControllerID.entries) {
        val skin = ArcaduneSkins.forController(id) ?: continue
        for ((landscape, layout) in listOf(false to skin.portrait, true to skin.landscape)) {
            // Only the console-coloured layouts get pictures; the see-through sideways controls
            // and the NES pad stay drawn by the app.
            if (layout.style != SkinStyle.CONSOLE || layout.palette == null) continue
            val folder = File(root, SkinArt.folder(id.name, landscape).removePrefix("skin-art/")).apply { mkdirs() }
            fun write(name: String, image: Image) {
                val data = image.webp()
                File(folder, "$name.webp").writeBytes(data)
                files++
                bytes += data.size
            }
            if (!landscape) write(SkinArt.BODY, renderBody(layout, grille = id in Grilles))
            val done = mutableSetOf<String>()
            for (control in layout.controls) {
                val key = artKey(control)
                if (!done.add(key)) continue
                renderPart(layout, control, pressed = false)?.let { write(key, it) }
                if (control !is SkinControl.Dpad) renderPart(layout, control, pressed = true)?.let { write("${key}_pressed", it) }
            }
        }
    }
    File(root, "${SkinArt.GRAIN}.webp").writeBytes(renderGrain().webp())
    println("Wrote $files pictures, ${bytes / 1024} KB, to $root")
}

/** Reads the artwork the studio wrote, for previews (the app reads it with SkinArtLoader). */
fun loadStudioArt(root: File, controller: String, landscape: Boolean): SkinArt? {
    val folder = File(root, SkinArt.folder(controller, landscape).removePrefix("skin-art/"))
    val files = folder.listFiles()?.filter { it.extension == "webp" }.orEmpty()
    if (files.isEmpty()) return null
    val images = files.associate { it.nameWithoutExtension to Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
    val grainFile = File(root, "${SkinArt.GRAIN}.webp")
    val grain = if (grainFile.exists()) Image.makeFromEncoded(grainFile.readBytes()).toComposeImageBitmap() else null
    return SkinArt(images[SkinArt.BODY], images - SkinArt.BODY, grain)
}
