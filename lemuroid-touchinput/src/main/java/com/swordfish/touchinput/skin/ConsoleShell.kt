package com.swordfish.touchinput.skin

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.settings.TouchControllerID

/**
 * Where each screen is in the picture the emulator draws, in that picture's own pixels: one screen
 * for most consoles, two for the DS and 3DS (where they are depends on the layout in use).
 */
data class ScreenSpec(
    val width: Float,
    val height: Float,
    val screens: List<SkinRect>,
) {
    /** The screens placed as big as they fit in [area], centred. */
    fun fitIn(area: Rect): List<Rect> {
        val scale = minOf(area.width / width, area.height / height)
        val left = area.left + (area.width - width * scale) / 2
        val top = area.top + (area.height - height * scale) / 2
        return screens.map { Rect(left + it.x * scale, top + it.y * scale, left + (it.x + it.width) * scale, top + (it.y + it.height) * scale) }
    }

    companion object {
        fun single(
            width: Float,
            height: Float,
        ) = ScreenSpec(width, height, listOf(SkinRect(0f, 0f, width, height)))
    }
}

object ScreenSpecs {
    /** The DS core leaves this gap (in its pixels) between stacked screens, where the shell draws its hinge. */
    const val DS_SCREEN_GAP = 16f

    private val DS_STACKED =
        ScreenSpec(256f, 384f + DS_SCREEN_GAP, listOf(SkinRect(0f, 0f, 256f, 192f), SkinRect(0f, 192f + DS_SCREEN_GAP, 256f, 192f)))

    // Hybrid: the top screen three times as big, and the touch screen small at the bottom of a
    // column on its right (the core is set to show only the other screen there, so it is not shown twice).
    private val DS_HYBRID =
        ScreenSpec(
            768f + DS_SCREEN_GAP + 256f,
            576f,
            listOf(SkinRect(0f, 0f, 768f, 576f), SkinRect(768f + DS_SCREEN_GAP, 384f, 256f, 192f)),
        )

    private val DS_SIDE_BY_SIDE =
        ScreenSpec(512f + DS_SCREEN_GAP, 192f, listOf(SkinRect(0f, 0f, 256f, 192f), SkinRect(256f + DS_SCREEN_GAP, 0f, 256f, 192f)))

    /**
     * The screens of [id]'s console, given the screen settings the core uses now ([values]: layout
     * and main screen). Null when they are not known: the shell then opens one window over the
     * whole area and the picture is centred in it.
     */
    fun forController(
        id: TouchControllerID,
        values: Map<String, String>,
        landscape: Boolean,
    ): ScreenSpec? =
        when (id) {
            TouchControllerID.MELONDS ->
                when (values["melonds_screen_layout1"] ?: if (landscape) "hybrid-top" else "top-bottom") {
                    "top-bottom" -> DS_STACKED
                    "hybrid-top", "hybrid-bottom" -> DS_HYBRID
                    "left-right" -> DS_SIDE_BY_SIDE
                    else -> null
                }
            TouchControllerID.NINTENDO_3DS -> n3ds(values, landscape)
            TouchControllerID.GB -> ScreenSpec.single(160f, 144f)
            TouchControllerID.GG -> ScreenSpec.single(160f, 144f)
            TouchControllerID.GBA -> ScreenSpec.single(240f, 160f)
            TouchControllerID.PSP -> ScreenSpec.single(480f, 272f)
            TouchControllerID.NGP -> ScreenSpec.single(160f, 152f)
            TouchControllerID.WS_LANDSCAPE -> ScreenSpec.single(224f, 144f)
            TouchControllerID.LYNX -> ScreenSpec.single(160f, 102f)
            // Arcade games come in every shape (many stand upright), and the WonderSwan upright
            // turns its picture: one window, the picture centred in it.
            TouchControllerID.ARCADE_4, TouchControllerID.ARCADE_6, TouchControllerID.WS_PORTRAIT -> null
            else -> ScreenSpec.single(4f, 3f)
        }

    private fun n3ds(
        values: Map<String, String>,
        landscape: Boolean,
    ): ScreenSpec? {
        val swapped = values["citra_swap_screen"] == "Bottom"
        val top = 400f
        val bottom = 320f
        return when (values["citra_layout_option"] ?: if (landscape) "Large Screen, Small Screen" else "Default Top-Bottom Screen") {
            "Default Top-Bottom Screen" ->
                if (!swapped) {
                    ScreenSpec(top, 480f, listOf(SkinRect(0f, 0f, top, 240f), SkinRect(40f, 240f, bottom, 240f)))
                } else {
                    ScreenSpec(top, 480f, listOf(SkinRect(40f, 0f, bottom, 240f), SkinRect(0f, 240f, top, 240f)))
                }
            // The main screen big, the other one a quarter of its size in the lower right corner.
            "Large Screen, Small Screen" ->
                if (!swapped) {
                    ScreenSpec(top + bottom / 4, 240f, listOf(SkinRect(0f, 0f, top, 240f), SkinRect(top, 180f, bottom / 4, 60f)))
                } else {
                    ScreenSpec(bottom + top / 4, 240f, listOf(SkinRect(0f, 0f, bottom, 240f), SkinRect(bottom, 180f, top / 4, 60f)))
                }
            "Side by Side" ->
                if (!swapped) {
                    ScreenSpec(top + bottom, 240f, listOf(SkinRect(0f, 0f, top, 240f), SkinRect(top, 0f, bottom, 240f)))
                } else {
                    ScreenSpec(top + bottom, 240f, listOf(SkinRect(0f, 0f, bottom, 240f), SkinRect(bottom, 0f, top, 240f)))
                }
            "Single Screen Only" -> if (!swapped) ScreenSpec.single(top, 240f) else ScreenSpec.single(bottom, 240f)
            else -> null
        }
    }
}

/**
 * The console's body over the whole screen, with a window cut out for each game screen: a dark
 * bezel around each window, a soft shadow inside its edge, and a hinge between two stacked screens.
 * The game shows through the windows; touches go through too (the body takes none).
 */
@Composable
fun ConsoleShell(
    modifier: Modifier = Modifier,
    palette: SkinPalette,
    screens: List<Rect>,
    /** Upright: where the body starts; the screens sit in black above it (see [ShellPlacement.bodyTop]). */
    bodyTop: Float? = null,
    /** The recessed panel the screens sit in (the DS, see [ShellPlacement.screenPanel]). */
    screenPanel: Rect? = null,
) {
    if (bodyTop != null) {
        UprightBody(modifier, palette, bodyTop)
        return
    }
    if (screenPanel != null) {
        PanelBody(modifier, palette, screens, screenPanel)
        return
    }
    Canvas(modifier) {
        val bezelWidth = SCREEN_BEZEL_DP.dp.toPx()
        val windowCorner = 4.dp.toPx()
        val bezelCorner = 16.dp.toPx()
        val darkBody = palette.body.luminance() < 0.3f
        val bezel = if (darkBody) Color(0xFF0D0E11) else Color(0xFF1C1D22)

        fun rounded(
            rect: Rect,
            corner: Float,
        ) = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner))) }

        val windows = screens.fold(Path()) { path, screen -> Path.combine(PathOperation.Union, path, rounded(screen, windowCorner)) }
        val bezels =
            screens.fold(Path()) { path, screen ->
                Path.combine(PathOperation.Union, path, rounded(screen.inflate(bezelWidth), bezelCorner))
            }

        // The body: everything but the bezels.
        val body = Path.combine(PathOperation.Difference, Path().apply { addRect(Rect(Offset.Zero, size)) }, bezels)
        drawPath(body, Brush.verticalGradient(listOf(palette.body, palette.bodyShade)))

        // The bezels around the windows, then a soft shadow inside each window's edge.
        drawPath(Path.combine(PathOperation.Difference, bezels, windows), bezel)
        drawPath(bezels, Color.White.copy(alpha = if (darkBody) 0.05f else 0.35f), style = Stroke(1.dp.toPx()))
        for (screen in screens) {
            drawRoundRect(Color.Black.copy(alpha = 0.55f), screen.topLeft, screen.size, CornerRadius(windowCorner), style = Stroke(2.dp.toPx()))
        }

        // A hinge between two screens stacked one above the other (the DS and 3DS held upright),
        // only in the gap between the windows so it never covers the game.
        hingeBetween(screens)?.let { (y, gap) ->
            val height = (gap - 4.dp.toPx()).coerceAtMost(18.dp.toPx())
            if (height <= 0f) return@let
            val band = palette.body.shade(-0.08f)
            drawRect(band, Offset(0f, y - height / 2), Size(size.width, height))
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, y + height / 2 - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
            val knuckle = Size(size.width * 0.14f, height)
            for (x in listOf(size.width * 0.05f, size.width * 0.81f)) {
                drawRoundRect(palette.body.shade(-0.14f), Offset(x, y - height / 2), knuckle, CornerRadius(height / 2))
                drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(x + height * 0.3f, y - height * 0.3f), Size(knuckle.width - height * 0.6f, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            }
        }
    }
}

/**
 * The upright body, as Delta draws it: the console's plastic from [bodyTop] down, its top corners
 * curving up into the sides like the bottom of a handheld's screen bezel. Nothing is drawn above
 * it: the game view, black around its picture, shows there.
 */
@Composable
private fun UprightBody(
    modifier: Modifier,
    palette: SkinPalette,
    bodyTop: Float,
) {
    Canvas(modifier) {
        val corner = UPRIGHT_BODY_CORNER.dp.toPx()
        val below = Path().apply { addRect(Rect(0f, bodyTop - corner, size.width, size.height)) }

        // The black above, with rounded bottom corners, cut out of the body ([lift] lower edge).
        fun above(lift: Float): Path {
            val black = RoundRect(Rect(0f, -corner, size.width, bodyTop + lift), CornerRadius(corner))
            return Path().apply { addRoundRect(black) }
        }

        drawPath(
            Path.combine(PathOperation.Difference, below, above(0f)),
            Brush.verticalGradient(listOf(palette.body, palette.bodyShade), bodyTop - corner, size.height),
        )
        // A soft light along the body's top edge.
        val edge = Path.combine(PathOperation.Difference, above(1.5f.dp.toPx()), above(0f))
        drawPath(edge, Color.White.copy(alpha = if (palette.body.luminance() < 0.3f) 0.1f else 0.45f))
    }
}

/**
 * The body with its screens in one recessed panel, the way Delta frames its DS: the body all
 * over, a slightly sunken rounded panel around the screens (and the L / R tabs under them), and
 * a thin dark outline round each screen. The game shows through the screens.
 */
@Composable
private fun PanelBody(
    modifier: Modifier,
    palette: SkinPalette,
    screens: List<Rect>,
    panel: Rect,
) {
    Canvas(modifier) {
        val outline = SCREEN_OUTLINE_DP.dp.toPx()
        val windows =
            screens.fold(Path()) { path, screen ->
                Path.combine(PathOperation.Union, path, Path().apply { addRect(screen) })
            }
        val everything = Path().apply { addRect(Rect(Offset.Zero, size)) }
        drawPath(
            Path.combine(PathOperation.Difference, everything, windows),
            Brush.verticalGradient(listOf(palette.body.shade(0.06f), palette.bodyShade)),
        )
        // The sunken panel: a little darker, shaded on its upper edge, lit on its lower one.
        val corner = CornerRadius(18.dp.toPx())
        val sunken = Path().apply { addRoundRect(RoundRect(panel, corner)) }
        drawPath(Path.combine(PathOperation.Difference, sunken, windows), palette.body.shade(-0.04f))
        val edge = Stroke(1.5f.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.16f), panel.topLeft, panel.size, corner, style = edge)
        drawRoundRect(
            Color.White.copy(alpha = if (palette.body.luminance() < 0.3f) 0.06f else 0.35f),
            panel.topLeft + Offset(0f, 1.5f.dp.toPx()),
            panel.size,
            corner,
            style = Stroke(1.dp.toPx()),
        )
        for (screen in screens) {
            val ring = screen.inflate(outline / 2)
            drawRect(palette.body.shade(-0.7f), ring.topLeft, ring.size, style = Stroke(outline))
        }
    }
}

/** How round the bottom corners of the black above the upright body are, in dp. */
private const val UPRIGHT_BODY_CORNER = 26f

/** Middle and height of the gap between two screens stacked one above the other, if there are such screens. */
private fun hingeBetween(screens: List<Rect>): Pair<Float, Float>? {
    if (screens.size != 2) return null
    val (upper, lower) = screens.sortedBy { it.top }
    if (lower.top < upper.bottom - 1f) return null // side by side, not stacked
    return (upper.bottom + lower.top) / 2 to (lower.top - upper.bottom)
}

private fun Color.shade(amount: Float): Color = if (amount >= 0) lerp(this, Color.White, amount) else lerp(this, Color.Black, -amount)

/** Two-screen consoles always open their windows, whatever the colour choice. */
fun TouchControllerID.hasTwoScreens() = this == TouchControllerID.MELONDS || this == TouchControllerID.NINTENDO_3DS

/**
 * Where a shell's windows, the whole picture and the upright controller panel go, in pixels.
 * [bodyTop] is where the console's body starts, upright (it only covers the controller there, like
 * Delta: the screen sits in black above it); null sideways, where the body surrounds the screens.
 */
data class ShellPlacement(
    val windows: List<Rect>,
    val picture: Rect,
    val panel: Rect,
    val bodyTop: Float? = null,
    /** The recessed panel the screens sit in, for layouts that have one (see [SkinLayout.screenPanelBelow]). */
    val screenPanel: Rect? = null,
)

/**
 * Lays out [skin] on a surface of [width] x [height] pixels ([dp] pixels per dp; [safe] is the part
 * clear of the camera cutout).
 *
 * Upright, the way Delta does it: the controller as wide as the phone (up to a tablet's limit), at
 * its own proportions, sitting on the bottom edge where the thumbs are; the console's body only
 * behind it; and the game in the black above, as big as fits (edge to edge on a phone) and centred
 * there, with a black band between it and the body. Sideways the screens go in the skin's screen
 * area, between the wings. With no [shell] (controls hidden) the picture simply fills the safe area.
 */
fun placeShell(
    skin: ArcaduneSkin,
    isLandscape: Boolean,
    shell: Boolean,
    spec: ScreenSpec?,
    width: Float,
    height: Float,
    safe: Rect,
    dp: Float,
): ShellPlacement {
    val layout = if (isLandscape) skin.landscape else skin.portrait
    val panelWidth = minOf(width, MAX_PANEL_WIDTH * dp)
    val naturalHeight = panelWidth * skin.portrait.height / skin.portrait.width
    val panelTop = safe.bottom - naturalHeight - PANEL_BOTTOM * dp
    val panel = Rect((width - panelWidth) / 2, panelTop, (width + panelWidth) / 2, panelTop + naturalHeight)
    val bodyTop = panelTop - BODY_ABOVE_PANEL * dp

    // The DS upright: the body behind everything, the screens in it, in from the sides, sitting
    // right on the L / R tabs at the top of the controller.
    val side = skin.portrait.uprightScreenSide
    // Screens in a panel have a thin outline instead of the thick bezel.
    val frame = if (layout.screenPanelBelow != null) SCREEN_OUTLINE_DP else SCREEN_BEZEL_DP
    val sidewaysScale =
        minOf((height - 8 * dp) / skin.landscape.height, (safe.right - safe.left - 8 * dp) / skin.landscape.width)
    val area =
        when {
            !shell -> safe
            !isLandscape && side != null ->
                Rect(
                    safe.left + side * width,
                    safe.top + SCREEN_TOP_IN_BODY * dp,
                    safe.right - side * width,
                    panelTop - frame * dp,
                )
            !isLandscape -> Rect(safe.left, safe.top, safe.right, bodyTop - SCREEN_TO_BODY * dp)
            else -> {
                val screens = layout.screenArea ?: SkinRect(0f, 0f, layout.width, layout.height)
                val usable = Rect(safe.left + 4 * dp, 4 * dp, safe.right - 4 * dp, height - 4 * dp)
                val scale = minOf(usable.height / layout.height, usable.width / layout.width)
                // The bezel is drawn outside the window: keep it, and a little air, off the wings.
                val side = (frame + SCREEN_TO_WING) * dp
                Rect(
                    usable.left + screens.x * scale + side,
                    usable.top + screens.y * scale,
                    usable.right - (layout.width - screens.x - screens.width) * scale - side,
                    usable.bottom - (layout.height - screens.y - screens.height) * scale,
                )
            }
        }
    val fitted = (if (shell) spec?.fitIn(area) else null) ?: listOf(area)
    // The DS upright sits its screens on the L / R tabs (the space left over goes above them).
    val drop = if (shell && !isLandscape && side != null) area.bottom - fitted.maxOf { it.bottom } else 0f
    val windows = fitted.map { it.translate(0f, drop) }
    val picture = windows.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
    val screenPanel =
        layout.screenPanelBelow?.takeIf { shell }?.let { below ->
            val unit = if (isLandscape) sidewaysScale else panelWidth / skin.portrait.width
            val pad = SCREEN_PANEL_PAD * dp
            Rect(picture.left - pad, picture.top - pad, picture.right + pad, picture.bottom + below * unit + pad)
        }
    val uprightBodyTop = bodyTop.takeIf { shell && !isLandscape && side == null }
    return ShellPlacement(windows, picture, panel, uprightBodyTop, screenPanel)
}

/** How wide the dark bezel around each screen window is, in dp. It is drawn outside the window. */
internal const val SCREEN_BEZEL_DP = 10f

/** The thin outline of a screen that sits in a panel (the DS), in dp, outside the window. */
internal const val SCREEN_OUTLINE_DP = 2f

/** How far the DS screen panel reaches around its screens, in dp. */
private const val SCREEN_PANEL_PAD = 12f

// Upright, in dp: the widest the controller gets (tablets), the space under it, the body showing
// above its first row of controls, and the least black between the screen and the body.
private const val MAX_PANEL_WIDTH = 520f
private const val PANEL_BOTTOM = 4f
private const val BODY_ABOVE_PANEL = 8f
internal const val SCREEN_TO_BODY = 18f

/** Upright, when the body covers the whole screen (the DS): the body showing above the screens. */
private const val SCREEN_TOP_IN_BODY = 22f

/** Sideways, the space between a screen's bezel and the wings of controls, in dp. */
private const val SCREEN_TO_WING = 4f

/**
 * A picture of [skin] as it looks in a game, held upright or sideways: the shell, a placeholder in
 * each screen window, and the controls. Laid out on a 360 x 780 dp phone and scaled down to fit,
 * so the proportions are the real ones.
 */
@Composable
fun ArcaduneShellPreview(
    modifier: Modifier = Modifier,
    id: TouchControllerID,
    skin: ArcaduneSkin,
    isLandscape: Boolean,
    /** The phone it is laid out on (held as [isLandscape] says), before it is scaled down to fit. */
    phoneWidth: androidx.compose.ui.unit.Dp = if (isLandscape) 780.dp else 360.dp,
    phoneHeight: androidx.compose.ui.unit.Dp = if (isLandscape) 360.dp else 780.dp,
    /** The player's moved buttons, size and opacity. */
    customization: SkinCustomization = SkinCustomization(),
    /** Given, the controls can be dragged (see [ArcaduneSkinEditor]): the layout editor in Settings. */
    onMove: ((Int, Offset) -> Unit)? = null,
    /** The skin's designed pictures for this orientation, when it has them (see [SkinArt]). */
    art: SkinArt? = null,
) {
    val corners = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clip(corners)) {
        val fit = minOf(maxWidth / phoneWidth, maxHeight / phoneHeight)
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .requiredSize(phoneWidth, phoneHeight)
                    .graphicsLayer {
                        scaleX = fit
                        scaleY = fit
                    },
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val width = with(density) { phoneWidth.toPx() }
            val height = with(density) { phoneHeight.toPx() }
            val layout = if (isLandscape) skin.landscape else skin.portrait
            val shell = !isLandscape || layout.screenArea != null
            val spec = ScreenSpecs.forController(id, emptyMap(), isLandscape)
            val placement = placeShell(skin, isLandscape, shell, spec, width, height, Rect(0f, 0f, width, height), density.density)

            // Without a shell (full-screen sideways skins) the game is letterboxed over the whole screen.
            val games = if (shell) placement.windows else spec?.fitIn(placement.picture) ?: placement.windows
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black)
                for (window in games) {
                    drawRect(Brush.linearGradient(listOf(Color(0xFF3A4A70), Color(0xFF1C2338)), window.topLeft, window.bottomRight), window.topLeft, window.size)
                }
            }
            if (shell) {
                ConsoleShell(
                    Modifier.fillMaxSize(),
                    layout.palette ?: SkinPalettes.MONO,
                    placement.windows,
                    placement.bodyTop,
                    placement.screenPanel,
                )
            }
            val controls =
                if (isLandscape) {
                    Modifier.fillMaxSize().padding(4.dp)
                } else {
                    with(density) {
                        Modifier
                            .absoluteOffset { androidx.compose.ui.unit.IntOffset(placement.panel.left.toInt(), placement.panel.top.toInt()) }
                            .size(placement.panel.width.toDp(), placement.panel.height.toDp())
                    }
                }
            if (onMove != null) {
                ArcaduneSkinEditor(
                    controls,
                    layout,
                    pinToEdges = isLandscape,
                    rewindAvailable = true,
                    customization = customization,
                    onMove = onMove,
                    shell = shell,
                    art = art,
                )
            } else {
                ArcaduneSkinPreview(controls, layout, isLandscape, shell, customization, art)
            }
        }
    }
}
