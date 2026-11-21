package com.swordfish.lemuroid.app.mobile.feature.settings.skins

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swordfish.lemuroid.app.mobile.feature.game.ArcaduneControllerStudio
import com.swordfish.lemuroid.app.mobile.feature.game.SkinCustomizationStore
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.skin.ArcaduneSkin
import com.swordfish.touchinput.skin.ArcaduneSkins
import com.swordfish.touchinput.skin.ConsoleShell
import com.swordfish.touchinput.skin.ScreenSpecs
import com.swordfish.touchinput.skin.SkinArt
import com.swordfish.touchinput.skin.SkinArtLoader
import com.swordfish.touchinput.skin.SkinCustomization
import com.swordfish.touchinput.skin.SkinPalettes
import com.swordfish.touchinput.skin.SkinVariant
import com.swordfish.touchinput.skin.defaultFastForwardDots
import com.swordfish.touchinput.skin.placeShell
import com.swordfish.touchinput.skin.supportsFastForwardByDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The route of [id]'s layout editor (see [SkinLayoutEditorScreen]). */
fun skinLayoutRoute(
    id: TouchControllerID,
    landscape: Boolean = false,
) = "settings/skins/layout/${id.name}?landscape=$landscape"

/**
 * Full-screen 1:1 immersive controller layout studio outside a game.
 * Hides system bars, rotates the window between Upright and Sideways so thumb scale matches real
 * gameplay pixel-for-pixel, and shares [ArcaduneControllerStudio] with in-game Edit Controls.
 */
@Composable
fun SkinLayoutEditorScreen(
    modifier: Modifier = Modifier,
    controller: String,
    initialLandscape: Boolean = false,
    onDone: () -> Unit,
) {
    val id = TouchControllerID.values().firstOrNull { it.name == controller } ?: return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val store = remember { SkinCustomizationStore(context) }

    var variant by remember { mutableStateOf(store.variant(id.name)) }
    var landscape by rememberSaveable { mutableStateOf(initialLandscape) }
    val skin = remember(id, variant) { ArcaduneSkins.forController(id, variant) } ?: return
    val layout = if (landscape) skin.landscape else skin.portrait
    val key = SkinCustomizationStore.keyOf(id.name, landscape, layout)
    val customization = remember(key) { mutableStateOf(store.load(key, layout.defaultCustomization)) }
    val art = rememberSkinArt(id, variant, landscape)
    val consoleName = Controllers.firstOrNull { it.first == id }?.second ?: id.name

    // Pre-warm both orientations' artwork in memory so flipping Upright <-> Sideways renders in 0ms.
    LaunchedEffect(id, variant) {
        if (variant == SkinVariant.CONSOLE) {
            withContext(Dispatchers.IO) {
                SkinArtLoader.load(context, id.name, false)
                SkinArtLoader.load(context, id.name, true)
            }
        }
    }

    val entryWasLandscape =
        remember {
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
    val previousOrientation =
        remember(activity) {
            activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    val previousCutoutMode =
        remember(activity) {
            val win = activity?.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && win != null) {
                win.attributes.layoutInDisplayCutoutMode
            } else {
                0
            }
        }
    val windowRestored = remember(activity) { booleanArrayOf(false) }

    val restoreSystemWindow =
        remember(activity, previousOrientation, previousCutoutMode, entryWasLandscape) {
            {
                if (!windowRestored[0]) {
                    windowRestored[0] = true
                    val window = activity?.window
                    val insetsController =
                        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                    insetsController?.show(WindowInsetsCompat.Type.systemBars())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
                        val attrs = window.attributes
                        attrs.layoutInDisplayCutoutMode = previousCutoutMode
                        window.attributes = attrs
                    }
                    if (activity != null) {
                        if (previousOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                            activity.requestedOrientation = previousOrientation
                        } else {
                            val snapBack =
                                if (entryWasLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            activity.requestedOrientation = snapBack
                            window?.decorView?.postDelayed(
                                {
                                    if (activity.requestedOrientation == snapBack) {
                                        activity.requestedOrientation =
                                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                },
                                350L,
                            )
                        }
                    }
                }
            }
        }

    DisposableEffect(activity) {
        windowRestored[0] = false
        val window = activity?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        applyEditorOrientation(activity, landscape)

        onDispose { restoreSystemWindow() }
    }

    LaunchedEffect(activity, landscape) {
        if (!windowRestored[0]) {
            applyEditorOrientation(activity, landscape)
        }
    }

    DisposableEffect(key) {
        onDispose { store.save(key, customization.value) }
    }

    val saveCurrentAndFinish = {
        store.save(key, customization.value)
        restoreSystemWindow()
        onDone()
    }
    BackHandler { saveCurrentAndFinish() }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val windowMatchesTarget = (constraints.maxWidth > constraints.maxHeight) == landscape
        val stageModifier: Modifier
        val widthPx: Float
        val heightPx: Float
        val safeRect: Rect

        if (windowMatchesTarget) {
            widthPx = constraints.maxWidth.toFloat()
            heightPx = constraints.maxHeight.toFloat()
            val left = cutout.getLeft(density, layoutDirection).toFloat()
            val top = cutout.getTop(density).toFloat()
            val right = widthPx - cutout.getRight(density, layoutDirection)
            safeRect =
                if (landscape) {
                    Rect(left, top, right, heightPx)
                } else {
                    Rect(0f, top, widthPx, heightPx)
                }
            stageModifier = Modifier.fillMaxSize()
        } else {
            val (winW, winH) =
                remember(configuration, density.density) {
                    fullWindowSizeDp(
                        context,
                        density.density,
                        configuration.screenWidthDp,
                        configuration.screenHeightDp,
                    )
                }
            val shortSide = minOf(winW, winH)
            val longSide = maxOf(winW, winH)
            val targetW = if (landscape) longSide else shortSide
            val targetH = if (landscape) shortSide else longSide
            val fit = minOf(maxWidth / targetW, maxHeight / targetH)
            widthPx = with(density) { targetW.toPx() }
            heightPx = with(density) { targetH.toPx() }
            safeRect = Rect(0f, 0f, widthPx, heightPx)
            stageModifier =
                Modifier
                    .requiredSize(targetW, targetH)
                    .graphicsLayer {
                        scaleX = fit
                        scaleY = fit
                    }
        }

        StudioShellStage(
            modifier = stageModifier,
            id = id,
            title = consoleName,
            skin = skin,
            isLandscape = landscape,
            widthPx = widthPx,
            heightPx = heightPx,
            safeRect = safeRect,
            customizationState = customization,
            variant = variant,
            art = art,
            onLandscapeChange = { nextLandscape ->
                if (nextLandscape != landscape) {
                    store.save(key, customization.value)
                    applyEditorOrientation(activity, nextLandscape)
                    landscape = nextLandscape
                }
            },
            onVariantChange = { nextVariant ->
                store.save(key, customization.value)
                variant = nextVariant
                store.setVariant(id.name, nextVariant)
            },
            onResetAll = {
                customization.value = layout.defaultCustomization
                store.save(key, layout.defaultCustomization)
            },
            onDone = saveCurrentAndFinish,
        )
    }
}

private fun applyEditorOrientation(
    activity: Activity?,
    landscape: Boolean,
) {
    activity ?: return
    val desired =
        if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    if (activity.requestedOrientation != desired) {
        activity.requestedOrientation = desired
    }
    activity.window?.let { win ->
        WindowCompat.getInsetsController(win, win.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun StudioShellStage(
    modifier: Modifier,
    id: TouchControllerID,
    title: String,
    skin: ArcaduneSkin,
    isLandscape: Boolean,
    widthPx: Float,
    heightPx: Float,
    safeRect: Rect,
    customizationState: MutableState<SkinCustomization>,
    variant: SkinVariant,
    art: SkinArt?,
    onLandscapeChange: (Boolean) -> Unit,
    onVariantChange: (SkinVariant) -> Unit,
    onResetAll: () -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val dpPx = with(density) { 1.dp.toPx() }
    val layout = if (isLandscape) skin.landscape else skin.portrait
    val shell = !isLandscape || layout.screenArea != null
    val customization = customizationState.value
    val spec = remember(id, isLandscape) { ScreenSpecs.forController(id, emptyMap(), isLandscape) }
    val placement =
        remember(id, skin, isLandscape, shell, spec, widthPx, heightPx, safeRect, dpPx, customization) {
            placeShell(
                skin = skin,
                isLandscape = isLandscape,
                shell = shell,
                spec = spec,
                width = widthPx,
                height = heightPx,
                safe = safeRect,
                dp = dpPx,
                customization = customization,
            )
        }
    val gameWindows =
        remember(shell, spec, placement) {
            placement.gameWindows(shell, spec)
        }
    val defaultGameWindows =
        remember(shell, spec, placement) {
            placement.defaultGameWindows(shell, spec)
        }

    // Strict 3-layer stack: 1) Bottom BG -> 2) Middle Screen -> 3) Top Buttons + Studio HUD.
    Box(modifier = modifier.background(Color.Black)) {
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanStep = 3.dp.toPx()
            val scanColor = Color.Black.copy(alpha = 0.14f)
            val corner = CornerRadius(4.dp.toPx())
            val borderStroke = Stroke(width = 1.5.dp.toPx())
            for (win in gameWindows) {
                val brush =
                    Brush.linearGradient(
                        listOf(Color(0xFF283452), Color(0xFF13192B)),
                        win.topLeft,
                        win.bottomRight,
                    )
                drawRoundRect(
                    brush = brush,
                    topLeft = win.topLeft,
                    size = win.size,
                    cornerRadius = corner,
                )
                var sy = win.top + scanStep
                while (sy < win.bottom) {
                    drawLine(
                        color = scanColor,
                        start = Offset(win.left, sy),
                        end = Offset(win.right, sy),
                        strokeWidth = 1f,
                    )
                    sy += scanStep
                }
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.62f),
                    topLeft = win.topLeft,
                    size = win.size,
                    cornerRadius = corner,
                    style = borderStroke,
                )
            }
        }

        ArcaduneControllerStudio(
            modifier = Modifier.fillMaxSize(),
            title = title,
            isLandscape = isLandscape,
            onLandscapeChange = onLandscapeChange,
            skin = skin,
            rewindAvailable = true,
            fastForwardSupported = id.supportsFastForwardByDefault(),
            fastForwardDotsTotal = id.defaultFastForwardDots(),
            customizationState = customizationState,
            panel = placement.panel,
            screenWindows = gameWindows,
            defaultScreenWindows = defaultGameWindows,
            editableBodyTop = placement.editableBodyTop,
            defaultBodyTop = placement.defaultBodyTop,
            minBodyTop = placement.minBodyTop,
            maxBodyTop = placement.maxBodyTop,
            shell = shell,
            variant = variant,
            onVariantChange = onVariantChange,
            onResetAll = onResetAll,
            onDone = onDone,
            art = art,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun fullWindowSizeDp(
    context: Context,
    density: Float,
    fallbackWidthDp: Int,
    fallbackHeightDp: Int,
): Pair<Dp, Dp> {
    val wm =
        context.getSystemService(WindowManager::class.java)
            ?: return fallbackWidthDp.dp to fallbackHeightDp.dp
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        (bounds.width() / density).dp to (bounds.height() / density).dp
    } else {
        val dm = context.resources.displayMetrics
        (dm.widthPixels / density).dp to (dm.heightPixels / density).dp
    }
}
