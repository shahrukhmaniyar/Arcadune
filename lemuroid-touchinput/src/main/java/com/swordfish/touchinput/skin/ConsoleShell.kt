package com.swordfish.touchinput.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.settings.TouchControllerID
import kotlin.math.roundToInt

/**
 * Where each screen is in the picture the emulator draws, in that picture's own pixels: one screen
 * for most consoles, two for the DS and 3DS (where they are depends on the layout in use).
 */
data class ScreenSpec(
    val width: Float,
    val height: Float,
    val screens: List<SkinRect>,
    val srcWidth: Float = width,
    val srcHeight: Float = height,
    val srcScreens: List<SkinRect> = screens,
) {
    /** The screens placed as big as they fit in [area], centred. */
    fun fitIn(area: Rect): List<Rect> {
        val scale = minOf(area.width / width, area.height / height)
        val left = area.left + (area.width - width * scale) / 2
        val top = area.top + (area.height - height * scale) / 2
        return screens.map {
            Rect(
                left + it.x * scale,
                top + it.y * scale,
                left + (it.x + it.width) * scale,
                top + (it.y + it.height) * scale,
            )
        }
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
    const val N3DS_SCREEN_GAP = 16f

    private val DS_STACKED =
        ScreenSpec(
            256f,
            384f + DS_SCREEN_GAP,
            listOf(SkinRect(0f, 0f, 256f, 192f), SkinRect(0f, 192f + DS_SCREEN_GAP, 256f, 192f)),
        )

    // Hybrid: the top screen three times as big, and the touch screen small at the bottom of a
    // column on its right (the core is set to show only the other screen there, so it is not shown twice).
    private val DS_HYBRID =
        ScreenSpec(
            768f + DS_SCREEN_GAP + 256f,
            576f,
            listOf(SkinRect(0f, 0f, 768f, 576f), SkinRect(768f + DS_SCREEN_GAP, 384f, 256f, 192f)),
        )

    // DeSmuME only applies desmume_screens_gap vertically (top/bottom), leaving 0px horizontal gap
    // in hybrid/* (1024x576) and left/right (512x192). Keep the visual 16-unit shell separation
    // while sampling exact 0px-gap source UVs.
    private val DS_DESMUME_HYBRID =
        ScreenSpec(
            width = 768f + DS_SCREEN_GAP + 256f,
            height = 576f,
            screens = listOf(SkinRect(0f, 0f, 768f, 576f), SkinRect(768f + DS_SCREEN_GAP, 384f, 256f, 192f)),
            srcWidth = 1024f,
            srcHeight = 576f,
            srcScreens = listOf(SkinRect(0f, 0f, 768f, 576f), SkinRect(768f, 384f, 256f, 192f)),
        )

    private val DS_SIDE_BY_SIDE =
        ScreenSpec(
            512f + DS_SCREEN_GAP,
            192f,
            listOf(SkinRect(0f, 0f, 256f, 192f), SkinRect(256f + DS_SCREEN_GAP, 0f, 256f, 192f)),
        )

    private val DS_DESMUME_SIDE_BY_SIDE =
        ScreenSpec(
            width = 512f + DS_SCREEN_GAP,
            height = 192f,
            screens = listOf(SkinRect(0f, 0f, 256f, 192f), SkinRect(256f + DS_SCREEN_GAP, 0f, 256f, 192f)),
            srcWidth = 512f,
            srcHeight = 192f,
            srcScreens = listOf(SkinRect(0f, 0f, 256f, 192f), SkinRect(256f, 0f, 256f, 192f)),
        )

    /**
     * Returns which window indices in [forController]'s screen list represent an interactive touch
     * screen (DS / 3DS bottom screen), so resting a finger on the non-touch screen never steals
     * the touch pointer.
     */
    fun touchWindowIndices(
        id: TouchControllerID,
        values: Map<String, String>,
        landscape: Boolean,
    ): Set<Int> =
        when (id) {
            TouchControllerID.MELONDS -> {
                val dsLayout =
                    values["melonds_screen_layout1"]
                        ?: values["desmume_screens_layout"]
                        ?: if (landscape) "hybrid-top" else "top-bottom"
                when (dsLayout) {
                    "bottom-top", "bottom/top", "right-left", "right/left", "hybrid-bottom", "hybrid/bottom" ->
                        setOf(0)
                    else -> setOf(1)
                }
            }
            TouchControllerID.NINTENDO_3DS -> {
                val swapped = values["citra_swap_screen"] == "Bottom"
                val defaultOption = if (landscape) "Large Screen, Small Screen" else "Default Top-Bottom Screen"
                val layoutOption = values["citra_layout_option"] ?: defaultOption
                when {
                    layoutOption == "Single Screen Only" -> if (swapped) setOf(0) else emptySet()
                    swapped -> setOf(0)
                    else -> setOf(1)
                }
            }
            else -> emptySet()
        }

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
            TouchControllerID.MELONDS -> {
                val isDesmume =
                    "desmume_screens_layout" in values && "melonds_screen_layout1" !in values
                val dsLayout =
                    values["melonds_screen_layout1"]
                        ?: values["desmume_screens_layout"]
                        ?: if (landscape) "hybrid-top" else "top-bottom"
                when (dsLayout) {
                    "top-bottom", "top/bottom", "bottom-top", "bottom/top" -> DS_STACKED
                    "hybrid-top", "hybrid-bottom", "hybrid/top", "hybrid/bottom" ->
                        if (isDesmume) DS_DESMUME_HYBRID else DS_HYBRID
                    "left-right", "left/right", "right-left", "right/left" ->
                        if (isDesmume) DS_DESMUME_SIDE_BY_SIDE else DS_SIDE_BY_SIDE
                    else -> null
                }
            }
            TouchControllerID.NINTENDO_3DS -> n3ds(values, landscape)
            TouchControllerID.GB -> ScreenSpec.single(160f, 144f)
            TouchControllerID.GG -> ScreenSpec.single(4f, 3f)
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
        val defaultOption = if (landscape) "Large Screen, Small Screen" else "Default Top-Bottom Screen"
        return when (values["citra_layout_option"] ?: defaultOption) {
            "Default Top-Bottom Screen" ->
                if (!swapped) {
                    ScreenSpec(
                        width = top,
                        height = 480f + N3DS_SCREEN_GAP,
                        screens =
                            listOf(
                                SkinRect(0f, 0f, top, 240f),
                                SkinRect(40f, 240f + N3DS_SCREEN_GAP, bottom, 240f),
                            ),
                        srcWidth = top,
                        srcHeight = 480f,
                        srcScreens =
                            listOf(
                                SkinRect(0f, 0f, top, 240f),
                                SkinRect(40f, 240f, bottom, 240f),
                            ),
                    )
                } else {
                    ScreenSpec(
                        width = top,
                        height = 480f + N3DS_SCREEN_GAP,
                        screens =
                            listOf(
                                SkinRect(40f, 0f, bottom, 240f),
                                SkinRect(0f, 240f + N3DS_SCREEN_GAP, top, 240f),
                            ),
                        srcWidth = top,
                        srcHeight = 480f,
                        srcScreens =
                            listOf(
                                SkinRect(40f, 0f, bottom, 240f),
                                SkinRect(0f, 240f, top, 240f),
                            ),
                    )
                }
            // The main screen big, the other one a quarter of its size in the lower right corner.
            "Large Screen, Small Screen" ->
                if (!swapped) {
                    ScreenSpec(
                        top + bottom / 4,
                        240f,
                        listOf(SkinRect(0f, 0f, top, 240f), SkinRect(top, 180f, bottom / 4, 60f)),
                    )
                } else {
                    ScreenSpec(
                        bottom + top / 4,
                        240f,
                        listOf(SkinRect(0f, 0f, bottom, 240f), SkinRect(bottom, 180f, top / 4, 60f)),
                    )
                }
            "Side by Side" ->
                if (!swapped) {
                    ScreenSpec(
                        top + bottom,
                        240f,
                        listOf(SkinRect(0f, 0f, top, 240f), SkinRect(top, 0f, bottom, 240f)),
                    )
                } else {
                    ScreenSpec(
                        top + bottom,
                        240f,
                        listOf(SkinRect(0f, 0f, bottom, 240f), SkinRect(bottom, 0f, top, 240f)),
                    )
                }
            "Single Screen Only" -> if (!swapped) ScreenSpec.single(top, 240f) else ScreenSpec.single(bottom, 240f)
            else -> null
        }
    }
}

/**
 * Layer 1 (Bottom — BG): The console's body, surface grain, NES inner deck face, and panel body art.
 * In gameplay ([cutoutScreens] = true), every screen window in [screens] is clipped out so Layer 2
 * (`GLRetroView`) is always 100% unobstructed on top of BG, while Layer 3 (Buttons) renders above both.
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
    layout: SkinLayout? = null,
    panel: Rect? = null,
    art: SkinArt? = null,
    cutoutScreens: Boolean = true,
) {
    val grain = art?.grain
    val grainTile =
        remember(grain) {
            grain?.let {
                ShaderBrush(
                    ImageShader(
                        it,
                        TileMode.Repeated,
                        TileMode.Repeated,
                    ),
                )
            }
        }
    val bodyArt = art?.body

    Canvas(modifier = modifier) {
        val windowCorner = if (bodyTop == null && screenPanel != null) 0f else 4.dp.toPx()
        val screenUnionPath =
            if (screens.isEmpty()) {
                null
            } else {
                screens.fold(Path()) { path, screen ->
                    val next =
                        Path().apply {
                            if (windowCorner > 0f) {
                                addRoundRect(RoundRect(screen, CornerRadius(windowCorner)))
                            } else {
                                addRect(screen)
                            }
                        }
                    Path.combine(PathOperation.Union, path, next)
                }
            }

        fun DrawScope.drawDeckOverlays(
            bodyClipPath: Path?,
            bgArtTop: Float = 0f,
        ) {
            val drawContent: DrawScope.() -> Unit = {
                if (grainTile != null) {
                    drawRect(grainTile, alpha = 0.06f, blendMode = BlendMode.Overlay)
                }
                if (panel != null && panel.width > 0f && panel.height > 0f) {
                    if (layout != null && layout.style == SkinStyle.NES_CLASSIC && layout.width > 0f) {
                        val extraTop = (panel.top - bgArtTop).coerceAtLeast(0f)
                        translate(left = panel.left, top = panel.top) {
                            drawNesDeckFace(panel.width / layout.width, extraTopPx = extraTop)
                        }
                    }
                    if (bodyArt != null) {
                        val imgW = bodyArt.width
                        val imgH = bodyArt.height
                        val dstLeft = panel.left.roundToInt()
                        val dstWidth = panel.width.roundToInt()
                        val dstTop = bgArtTop.coerceAtLeast(0f).roundToInt()
                        val dstBottom = size.height.roundToInt().coerceAtLeast(panel.bottom.roundToInt())
                        val totalDstH = dstBottom - dstTop
                        if (imgW > 0 && imgH > 1 && dstWidth > 0 && totalDstH > 1) {
                            val srcSplitY = (imgH * 0.45f).roundToInt().coerceIn(1, imgH - 1)
                            val bottomSrcH = imgH - srcSplitY
                            val naturalBottomH =
                                (panel.height * (bottomSrcH.toFloat() / imgH.toFloat()))
                                    .roundToInt()
                                    .coerceAtLeast(1)
                            if (totalDstH >= naturalBottomH + 2) {
                                val splitDstY = dstBottom - naturalBottomH
                                drawImage(
                                    image = bodyArt,
                                    srcOffset = IntOffset(0, 0),
                                    srcSize = IntSize(imgW, srcSplitY),
                                    dstOffset = IntOffset(dstLeft, dstTop),
                                    dstSize = IntSize(dstWidth, splitDstY - dstTop),
                                    filterQuality = FilterQuality.Medium,
                                )
                                drawImage(
                                    image = bodyArt,
                                    srcOffset = IntOffset(0, srcSplitY),
                                    srcSize = IntSize(imgW, bottomSrcH),
                                    dstOffset = IntOffset(dstLeft, splitDstY),
                                    dstSize = IntSize(dstWidth, dstBottom - splitDstY),
                                    filterQuality = FilterQuality.Medium,
                                )
                            } else {
                                val topH = (totalDstH * 0.35f).roundToInt().coerceIn(1, totalDstH - 1)
                                val splitDstY = dstTop + topH
                                val remH = dstBottom - splitDstY
                                val visFrac = (remH.toFloat() / naturalBottomH.toFloat()).coerceIn(0f, 1f)
                                val croppedBottomSrcH =
                                    (bottomSrcH * visFrac).roundToInt().coerceIn(1, bottomSrcH)
                                drawImage(
                                    image = bodyArt,
                                    srcOffset = IntOffset(0, 0),
                                    srcSize = IntSize(imgW, srcSplitY),
                                    dstOffset = IntOffset(dstLeft, dstTop),
                                    dstSize = IntSize(dstWidth, topH),
                                    filterQuality = FilterQuality.Medium,
                                )
                                drawImage(
                                    image = bodyArt,
                                    srcOffset = IntOffset(0, imgH - croppedBottomSrcH),
                                    srcSize = IntSize(imgW, croppedBottomSrcH),
                                    dstOffset = IntOffset(dstLeft, splitDstY),
                                    dstSize = IntSize(dstWidth, remH),
                                    filterQuality = FilterQuality.Medium,
                                )
                            }
                        }
                    }
                }
            }
            if (bodyClipPath != null) {
                clipPath(bodyClipPath, block = drawContent)
            } else {
                drawContent()
            }
        }

        val renderShellLayer: DrawScope.() -> Unit = {
            if (bodyTop != null) {
                val fullCorner = UPRIGHT_BODY_CORNER.dp.toPx()
                val clampedTop = bodyTop.coerceAtLeast(0f)
                val effectiveCorner = fullCorner.coerceAtMost(clampedTop)
                val bodyShape =
                    if (clampedTop <= 0.5f) {
                        Path().apply { addRect(Rect(Offset.Zero, size)) }
                    } else {
                        val below =
                            Path().apply {
                                addRect(
                                    Rect(
                                        0f,
                                        (clampedTop - effectiveCorner).coerceAtLeast(0f),
                                        size.width,
                                        size.height,
                                    ),
                                )
                            }
                        val blackAbove =
                            Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        Rect(0f, -effectiveCorner - 2f, size.width, clampedTop),
                                        CornerRadius(effectiveCorner),
                                    ),
                                )
                            }
                        Path.combine(PathOperation.Difference, below, blackAbove)
                    }
                val gradStart = (clampedTop - effectiveCorner).coerceAtLeast(0f)
                val bodyBrush =
                    Brush.verticalGradient(
                        listOf(palette.body, palette.bodyShade),
                        gradStart,
                        size.height.coerceAtLeast(gradStart + 1f),
                    )
                drawPath(bodyShape, bodyBrush)
                drawDeckOverlays(bodyShape, bgArtTop = gradStart)

                if (clampedTop > 1f) {
                    val aboveLifted =
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    Rect(0f, -effectiveCorner - 2f, size.width, clampedTop + 1.5f.dp.toPx()),
                                    CornerRadius(effectiveCorner),
                                ),
                            )
                        }
                    val aboveBase =
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    Rect(0f, -effectiveCorner - 2f, size.width, clampedTop),
                                    CornerRadius(effectiveCorner),
                                ),
                            )
                        }
                    val topRim = Path.combine(PathOperation.Difference, aboveLifted, aboveBase)
                    val edgeColor =
                        Color.White.copy(alpha = if (palette.body.luminance() < 0.3f) 0.1f else 0.45f)
                    drawPath(topRim, edgeColor)
                }

                // Subtle dark rim around any screen portion sitting inside the upright body.
                if (screenUnionPath != null && screens.any { it.bottom > clampedTop - effectiveCorner }) {
                    clipPath(bodyShape) {
                        drawPath(
                            path = screenUnionPath,
                            color = Color.Black.copy(alpha = 0.55f),
                            style = Stroke(3.dp.toPx()),
                        )
                    }
                }
            } else if (screenPanel != null) {
                val outline = SCREEN_OUTLINE_DP.dp.toPx()
                val bodyBrush = Brush.verticalGradient(listOf(palette.body.shade(0.06f), palette.bodyShade))
                drawRect(bodyBrush)
                val corner = CornerRadius(18.dp.toPx())
                drawRoundRect(palette.body.shade(-0.04f), screenPanel.topLeft, screenPanel.size, corner)
                drawRoundRect(
                    Color.Black.copy(alpha = 0.16f),
                    screenPanel.topLeft,
                    screenPanel.size,
                    corner,
                    style = Stroke(1.5f.dp.toPx()),
                )
                drawRoundRect(
                    Color.White.copy(alpha = if (palette.body.luminance() < 0.3f) 0.06f else 0.35f),
                    screenPanel.topLeft + Offset(0f, 1.5f.dp.toPx()),
                    screenPanel.size,
                    corner,
                    style = Stroke(1.dp.toPx()),
                )
                drawDeckOverlays(null)
                if (screenUnionPath != null) {
                    drawPath(
                        path = screenUnionPath,
                        color = palette.body.shade(-0.7f),
                        style = Stroke(outline * 2f),
                    )
                }
            } else {
                val bezelWidth = SCREEN_BEZEL_DP.dp.toPx()
                val bezelCorner = 16.dp.toPx()
                val darkBody = palette.body.luminance() < 0.3f
                val bezelColor = if (darkBody) Color(0xFF0D0E11) else Color(0xFF1C1D22)
                val bezels =
                    screens.fold(Path()) { path, screen ->
                        val next =
                            Path().apply {
                                addRoundRect(RoundRect(screen.inflate(bezelWidth), CornerRadius(bezelCorner)))
                            }
                        Path.combine(PathOperation.Union, path, next)
                    }
                val bodyBrush = Brush.verticalGradient(listOf(palette.body, palette.bodyShade))
                drawRect(bodyBrush)
                drawDeckOverlays(null)

                drawPath(bezels, bezelColor)
                val bezelHighlight = Color.White.copy(alpha = if (darkBody) 0.05f else 0.35f)
                drawPath(bezels, bezelHighlight, style = Stroke(1.dp.toPx()))
                if (screenUnionPath != null) {
                    drawPath(
                        path = screenUnionPath,
                        color = Color.Black.copy(alpha = 0.55f),
                        style = Stroke(3.dp.toPx()),
                    )
                }

                hingeBetween(screens)?.let { (y, gap) ->
                    val height = (gap - 4.dp.toPx()).coerceAtMost(18.dp.toPx())
                    if (height > 0f) {
                        val band = palette.body.shade(-0.08f)
                        drawRect(band, Offset(0f, y - height / 2), Size(size.width, height))
                        drawRect(
                            Color.Black.copy(alpha = 0.12f),
                            Offset(0f, y + height / 2 - 1.dp.toPx()),
                            Size(size.width, 1.dp.toPx()),
                        )
                        val knuckle = Size(size.width * 0.14f, height)
                        for (x in listOf(size.width * 0.05f, size.width * 0.81f)) {
                            drawRoundRect(
                                palette.body.shade(-0.14f),
                                Offset(x, y - height / 2),
                                knuckle,
                                CornerRadius(height / 2),
                            )
                            drawRoundRect(
                                Color.White.copy(alpha = 0.18f),
                                Offset(x + height * 0.3f, y - height * 0.3f),
                                Size(knuckle.width - height * 0.6f, 2.dp.toPx()),
                                CornerRadius(1.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }

        if (cutoutScreens && screenUnionPath != null) {
            clipPath(screenUnionPath, clipOp = ClipOp.Difference, block = renderShellLayer)
        } else {
            renderShellLayer()
        }
    }
}

/** How round the bottom corners of the black above the upright body are, in dp. */
private const val UPRIGHT_BODY_CORNER = 26f

/** Middle and height of the gap between two screens stacked one above the other, if there are such screens. */
private fun hingeBetween(screens: List<Rect>): Pair<Float, Float>? {
    if (screens.size != 2) return null
    val (upper, lower) = screens.sortedBy { it.top }
    val gap = lower.top - upper.bottom
    if (gap < -1f || gap > 96f) return null // side by side or pulled far apart
    val overlapX = minOf(upper.right, lower.right) - maxOf(upper.left, lower.left)
    if (overlapX < minOf(upper.width, lower.width) * 0.6f) return null
    return (upper.bottom + lower.top) / 2 to gap.coerceAtLeast(0f)
}

private fun Color.shade(amount: Float): Color =
    if (amount >= 0) lerp(this, Color.White, amount) else lerp(this, Color.Black, -amount)

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
    /** Unmodified screen window positions before [SkinCustomization] screen scale/offsets. */
    val defaultWindows: List<Rect> = emptyList(),
    /** Upright BG top Y in pixels when Portrait BG height is editable (including DS upright at 0f). */
    val editableBodyTop: Float? = null,
    /** Default upright BG top Y in pixels (before [SkinCustomization.portraitBgOffset]). */
    val defaultBodyTop: Float? = null,
    /** Highest upright BG position in pixels (0f = 100% full screen height). */
    val minBodyTop: Float = 0f,
    /** Lowest upright BG position in pixels. */
    val maxBodyTop: Float = 0f,
) {
    /** Visual screen windows for both shelled layouts and full-screen sideways overlay layouts. */
    fun gameWindows(
        shell: Boolean,
        spec: ScreenSpec?,
    ): List<Rect> =
        if (shell) {
            windows
        } else {
            (spec ?: ScreenSpec.single(4f, 3f)).fitIn(picture)
        }

    /** Unmodified default visual screen windows before [SkinCustomization] screen scale/offsets. */
    fun defaultGameWindows(
        shell: Boolean,
        spec: ScreenSpec?,
    ): List<Rect> =
        if (shell) {
            defaultWindows
        } else {
            val baseArea = defaultWindows.firstOrNull()
            if (baseArea != null) {
                (spec ?: ScreenSpec.single(4f, 3f)).fitIn(baseArea)
            } else {
                defaultWindows
            }
        }
}

/**
 * Lays out [skin] on a surface of [width] x [height] pixels ([dp] pixels per dp; [safe] is the part
 * clear of the camera cutout).
 *
 * Upright, the way Delta does it: the controller as wide as the phone (up to a tablet's limit), at
 * its own proportions, sitting on the bottom edge where the thumbs are; the console's body behind
 * it (and adjustable upward behind the screens via [SkinCustomization.portraitBgOffset]); and the
 * game in Layer 2 above Layer 1 BG, with Layer 3 controls on top of everything.
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
    customization: SkinCustomization = SkinCustomization(),
): ShellPlacement {
    val layout = if (isLandscape) skin.landscape else skin.portrait
    val desiredWidth = minOf(width, MAX_PANEL_WIDTH * dp)
    val desiredHeight = desiredWidth * skin.portrait.height / skin.portrait.width
    // On square-ish unfolded foldables or split-screen windows, cap controller height to 56% of safe
    // height while shrinking width proportionally so controller aspect ratio stays exact.
    val maxPanelHeight = (safe.height * MAX_PORTRAIT_PANEL_HEIGHT_SHARE).coerceAtLeast(1f)
    val naturalHeight = minOf(desiredHeight, maxPanelHeight)
    val panelWidth =
        if (desiredHeight > maxPanelHeight) {
            naturalHeight * skin.portrait.width / skin.portrait.height
        } else {
            desiredWidth
        }
    val panelTop = safe.bottom - naturalHeight - PANEL_BOTTOM * dp
    val panel = Rect((width - panelWidth) / 2, panelTop, (width + panelWidth) / 2, panelTop + naturalHeight)
    val controllerBodyTop = panelTop - BODY_ABOVE_PANEL * dp

    // The DS upright: the body behind everything, the screens in it, in from the sides, sitting
    // right on the L / R tabs at the top of the controller.
    val side = skin.portrait.uprightScreenSide
    val minBodyTop = 0f
    val maxBodyTop = (height - 56f * dp).coerceAtLeast(minBodyTop + 1f)
    val naturalBodyTop = if (side != null) 0f else controllerBodyTop.coerceIn(minBodyTop, maxBodyTop)
    val bgOffset =
        customization.portraitBgOffset.coerceIn(
            SkinCustomization.MIN_BG_OFFSET,
            SkinCustomization.MAX_BG_OFFSET,
        )
    val effectiveBodyTop =
        if (bgOffset >= 0f) {
            naturalBodyTop + (minBodyTop - naturalBodyTop) * bgOffset
        } else {
            naturalBodyTop + (maxBodyTop - naturalBodyTop) * (-bgOffset)
        }

    // Screens in a panel have a thin outline instead of the thick bezel.
    val frame = if (layout.screenPanelBelow != null) SCREEN_OUTLINE_DP else SCREEN_BEZEL_DP
    val sidewaysScale =
        minOf((height - 8 * dp) / skin.landscape.height, (safe.right - safe.left - 8 * dp) / skin.landscape.width)
    val rawArea =
        when {
            !shell -> safe
            !isLandscape && side != null ->
                Rect(
                    safe.left + side * width,
                    safe.top + SCREEN_TOP_IN_BODY * dp,
                    safe.right - side * width,
                    panelTop - frame * dp,
                )
            !isLandscape -> Rect(safe.left, safe.top, safe.right, controllerBodyTop - SCREEN_TO_BODY * dp)
            else -> {
                val screens = layout.screenArea ?: SkinRect(0f, 0f, layout.width, layout.height)
                val usable = Rect(safe.left + 4 * dp, safe.top + 4 * dp, safe.right - 4 * dp, height - 4 * dp)
                val scale = minOf(usable.height / layout.height, usable.width / layout.width)
                // The bezel is drawn outside the window: keep it, and a little air, off the wings.
                val sidePad = (frame + SCREEN_TO_WING) * dp
                Rect(
                    usable.left + screens.x * scale + sidePad,
                    usable.top + screens.y * scale,
                    usable.right - (layout.width - screens.x - screens.width) * scale - sidePad,
                    usable.bottom - (layout.height - screens.y - screens.height) * scale,
                )
            }
        }
    val area =
        Rect(
            rawArea.left,
            rawArea.top,
            maxOf(rawArea.left + 1f, rawArea.right),
            maxOf(rawArea.top + 1f, rawArea.bottom),
        )
    val fitted = (if (shell) spec?.fitIn(area) else null) ?: listOf(area)
    // The DS upright sits its screens on the L / R tabs (the space left over goes above them).
    val drop = if (shell && !isLandscape && side != null) area.bottom - fitted.maxOf { it.bottom } else 0f
    val defaultWindows = fitted.map { it.translate(0f, drop) }
    val defaultPicture =
        defaultWindows.reduce { a, b ->
            Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }
    val (windows, picture) =
        if (!customization.hasScreenChanges) {
            defaultWindows to defaultPicture
        } else {
            // The screens as the layout's own defaults left them (the DS and 3DS ones are tuned bigger or
            // smaller, and moved): on a phone of another shape they can land on a button, so they shrink
            // until they clear every control (never below 60%). Once the player changes a screen, it is theirs.
            val d = layout.defaultCustomization
            val adaptive =
                shell && customization.screenScale == d.screenScale && customization.screenOffset == d.screenOffset &&
                    customization.screenScales == d.screenScales && customization.screenOffsets == d.screenOffsets
            val avoid: List<Pair<Rect, Float>> by lazy(LazyThreadSafetyMode.NONE) {
                if (!adaptive) {
                    emptyList()
                } else {
                    val bezelPx = frame * dp
                    val rects: List<Pair<Int, Rect>> =
                        if (isLandscape) {
                            SkinGeometry(layout, true, width / dp - 8f, height / dp - 8f)
                                .frameRects(customization, rewindAvailable = true)
                                .map { (i, r) -> i to Rect((r.left + 4f) * dp, (r.top + 4f) * dp, (r.right + 4f) * dp, (r.bottom + 4f) * dp) }
                        } else {
                            val unit = panel.width / layout.width
                            layout.controls.indices
                                .filter { customization.isControlAvailable(layout.controls[it], true, true) && !customization.isHidden(it, layout.controls[it]) }
                                .map { i ->
                                    val f = customization.frameOf(i, layout.controls[i].frame)
                                    i to Rect(panel.left + f.x * unit, panel.top + f.y * unit, panel.left + (f.x + f.width) * unit, panel.top + (f.y + f.height) * unit)
                                }
                        }
                    rects.map { (i, r) ->
                        val c = layout.controls[i]
                        r to if (c is SkinControl.Menu || (c is SkinControl.Button && c.shape == ButtonShape.PILL)) bezelPx else 0f
                    }
                }
            }

            fun windowAt(
                idx: Int,
                defaultWin: Rect,
                s: Float,
            ): Rect {
                val center = defaultWin.center
                val scaledWin =
                    Rect(
                        center.x + (defaultWin.left - center.x) * s,
                        center.y + (defaultWin.top - center.y) * s,
                        center.x + (defaultWin.right - center.x) * s,
                        center.y + (defaultWin.bottom - center.y) * s,
                    )
                val keepX = minOf(scaledWin.width * 0.55f, 88f * dp)
                val keepY = minOf(scaledWin.height * 0.55f, 88f * dp)
                val minDx = minOf(keepX - scaledWin.right, (width - keepX) - scaledWin.left)
                val maxDx = maxOf(keepX - scaledWin.right, (width - keepX) - scaledWin.left)
                val minDy = minOf(keepY - scaledWin.bottom, (height - keepY) - scaledWin.top)
                val maxDy = maxOf(keepY - scaledWin.bottom, (height - keepY) - scaledWin.top)
                val rawOffset = customization.screenOffsetOf(idx)
                val dx = (rawOffset.x * width).coerceIn(minDx, maxDx)
                val dy = (rawOffset.y * height).coerceIn(minDy, maxDy)
                return scaledWin.translate(dx, dy)
            }

            fun clear(win: Rect): Boolean =
                win.left >= -1f && win.top >= -1f && win.right <= width + 1f && win.bottom <= height + 1f &&
                    avoid.none { (r, grow) ->
                        val w = minOf(win.right + grow, r.right) - maxOf(win.left - grow, r.left)
                        val h = minOf(win.bottom + grow, r.bottom) - maxOf(win.top - grow, r.top)
                        w > 0.5f && h > 0.5f
                    }

            val transformedWindows =
                defaultWindows.mapIndexed { idx, defaultWin ->
                    if (!customization.hasScreenChangesAt(idx)) return@mapIndexed defaultWin
                    val s =
                        customization.screenScaleOf(idx).coerceIn(
                            SkinCustomization.MIN_SCREEN_SCALE,
                            SkinCustomization.MAX_SCREEN_SCALE,
                        )
                    val first = windowAt(idx, defaultWin, s)
                    if (!adaptive || clear(first)) return@mapIndexed first
                    var k = s - 0.02f
                    val floor = maxOf(SkinCustomization.MIN_SCREEN_SCALE, s * 0.6f)
                    while (k > floor) {
                        val tried = windowAt(idx, defaultWin, k)
                        if (clear(tried)) return@mapIndexed tried
                        k -= 0.02f
                    }
                    // Still on something: slide it a little (sideways first, then up or down), at its size or a bit smaller.
                    val steps = (0..20).flatMap { n -> if (n == 0) listOf(0f) else listOf(n * 6f * dp, -n * 6f * dp) }
                    for (size in listOf(s, s * 0.9f, s * 0.8f, floor)) {
                        for (dy in listOf(0f, 12f * dp, -12f * dp, 24f * dp, -24f * dp, 36f * dp, -36f * dp)) {
                            for (dx in steps) {
                                val tried = windowAt(idx, defaultWin, size.coerceAtLeast(floor)).translate(dx, dy)
                                if (clear(tried)) return@mapIndexed tried
                            }
                        }
                    }
                    // Nothing tuned fits this phone: the layout's own plain place for the screen.
                    if (clear(defaultWin)) defaultWin else windowAt(idx, defaultWin, floor)
                }
            val transformedPicture =
                transformedWindows.reduce { a, b ->
                    Rect(
                        minOf(a.left, b.left),
                        minOf(a.top, b.top),
                        maxOf(a.right, b.right),
                        maxOf(a.bottom, b.bottom),
                    )
                }
            transformedWindows to transformedPicture
        }
    val dsBodyLowered = !isLandscape && side != null && effectiveBodyTop > 0.5f
    val screenPanel =
        layout.screenPanelBelow
            ?.takeIf { shell && !dsBodyLowered && !(defaultWindows.size > 1 && customization.hasScreenChanges) }
            ?.let { below ->
                val unit = if (isLandscape) sidewaysScale else panelWidth / skin.portrait.width
                val pad = SCREEN_PANEL_PAD * dp
                val extraBelow = if (customization.hasScreenChanges) 0f else below * unit
                Rect(picture.left - pad, picture.top - pad, picture.right + pad, picture.bottom + extraBelow + pad)
            }
    val uprightBodyTop =
        when {
            !shell || isLandscape -> null
            side != null -> effectiveBodyTop.takeIf { it > 0.5f || customization.hasScreenChanges }
            else -> effectiveBodyTop
        }
    return ShellPlacement(
        windows = windows,
        picture = picture,
        panel = panel,
        bodyTop = uprightBodyTop,
        screenPanel = screenPanel,
        defaultWindows = defaultWindows,
        editableBodyTop = effectiveBodyTop.takeIf { shell && !isLandscape },
        defaultBodyTop = naturalBodyTop.takeIf { shell && !isLandscape },
        minBodyTop = minBodyTop,
        maxBodyTop = maxBodyTop,
    )
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
private const val MAX_PORTRAIT_PANEL_HEIGHT_SHARE = 0.56f
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
    selectedIndex: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    accentColor: Color = Color(0xFFB57BFF),
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
            val placement =
                placeShell(
                    skin,
                    isLandscape,
                    shell,
                    spec,
                    width,
                    height,
                    Rect(0f, 0f, width, height),
                    density.density,
                    customization,
                )

            // Strict 3-layer stack: 1) Bottom BG -> 2) Middle Screen -> 3) Top Buttons.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black)
            }
            if (shell) {
                ConsoleShell(
                    modifier = Modifier.fillMaxSize(),
                    palette = layout.palette ?: SkinPalettes.MONO,
                    screens = placement.windows,
                    bodyTop = placement.bodyTop,
                    screenPanel = placement.screenPanel,
                    layout = layout.takeIf { !isLandscape },
                    panel = placement.panel.takeIf { !isLandscape },
                    art = art.takeIf { !isLandscape },
                    cutoutScreens = false,
                )
            }
            val games = placement.gameWindows(shell, spec)
            Canvas(Modifier.fillMaxSize()) {
                for (window in games) {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color(0xFF3A4A70), Color(0xFF1C2338)),
                            window.topLeft,
                            window.bottomRight,
                        ),
                        window.topLeft,
                        window.size,
                    )
                }
            }
            val above = if (isLandscape) 0f else placement.panel.top.coerceAtLeast(0f)
            val controls =
                if (isLandscape) {
                    Modifier.fillMaxSize().padding(4.dp)
                } else {
                    with(density) {
                        Modifier
                            .absoluteOffset {
                                androidx.compose.ui.unit.IntOffset(
                                    placement.panel.left.toInt(),
                                    (placement.panel.top - above).toInt(),
                                )
                            }
                            .size(placement.panel.width.toDp(), (placement.panel.height + above).toDp())
                    }
                }
            val topExtraDp = with(density) { above.toDp() }
            val ffSupported = id.supportsFastForwardByDefault()
            val ffDotsTotal = id.defaultFastForwardDots()
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
                    topExtra = topExtraDp,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    accentColor = accentColor,
                    fastForwardSupported = ffSupported,
                    fastForwardDotsTotal = ffDotsTotal,
                )
            } else {
                ArcaduneSkinPreview(
                    controls,
                    layout,
                    isLandscape,
                    shell,
                    customization,
                    art,
                    topExtra = topExtraDp,
                    fastForwardDotsTotal = ffDotsTotal,
                )
            }
        }
    }
}

fun TouchControllerID.supportsFastForwardByDefault(): Boolean =
    this != TouchControllerID.DOS && this != TouchControllerID.NINTENDO_3DS

fun TouchControllerID.defaultFastForwardDots(): Int =
    when (this) {
        TouchControllerID.DOS, TouchControllerID.NINTENDO_3DS -> 0
        TouchControllerID.PSP,
        TouchControllerID.DREAMCAST,
        TouchControllerID.SATURN,
        TouchControllerID.SATURN_3D,
        -> 2
        TouchControllerID.N64 -> 3
        else -> 4
    }
