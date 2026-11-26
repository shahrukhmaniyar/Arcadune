package com.swordfish.lemuroid.app.mobile.feature.library

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconPill
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.db.dao.GameSortOrder
import com.swordfish.lemuroid.lib.library.db.entity.Game
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The library, built after Delta: one page per console (plus Favorites and Recently Played) that
 * the player swipes through, the page's name on top, and a small blurred bar at the bottom with the
 * page dots and a search field. A tap plays a game, a long press opens its menu.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    indexingInProgress: Boolean,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenSettings: () -> Unit,
    notices: @Composable () -> Unit,
) {
    val pages = viewModel.pages.collectAsState().value
    // Only whether a search is on is read here, not the text: typing must not rebuild the library.
    val searching by remember(viewModel) {
        viewModel.searchQuery.map { it.isNotBlank() }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val focusManager = LocalFocusManager.current
    val hazeState = remember { HazeState() }

    // Dragging up past the end of a page rescans the ROMs folder, like Settings > Rescan. The line under
    // the title shows it is running, and a pull while a scan is already running is ignored.
    val appContext = LocalContext.current.applicationContext
    val scanRunning by rememberUpdatedState(indexingInProgress)
    val rescan: () -> Unit =
        remember(appContext) {
            { if (!scanRunning) LibraryIndexScheduler.scheduleLibrarySync(appContext) }
        }

    BackHandler(enabled = searching) {
        viewModel.searchQuery.value = ""
        focusManager.clearFocus()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ArcaduneColors.Background),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ArcaduneColors.Purple.copy(alpha = 0.09f),
                                ArcaduneColors.Purple.copy(alpha = 0.03f),
                                Color.Transparent,
                            ),
                        ),
                    ),
        )

        if (pages == null) return@Box

        // Delta opens on Recently Played; without it, on the first console.
        var startPageId by rememberSaveable { mutableStateOf<String?>(null) }
        if (startPageId == null && pages.isNotEmpty()) {
            startPageId =
                (
                    pages.firstOrNull { it is LibraryViewModel.Page.RecentlyPlayed }
                        ?: pages.getOrNull(1)
                        ?: pages.first()
                ).id
        }
        val pagerState =
            rememberPagerState(
                initialPage = pages.indexOfFirst { it.id == startPageId }.coerceAtLeast(0),
            ) { pages.size }

        // Pre-compose the immediate neighbor page on each side instead of all ~20 pages at once,
        // avoiding a multi-hundred-millisecond main-thread hitch right after opening the library.
        val keptPages = 1

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Only Android 12+ blurs; older phones skip the per-frame copy the blur needs.
                    .then(if (BLUR_SUPPORTED) Modifier.hazeSource(hazeState) else Modifier),
        ) {
            // The page number is read inside the bar (not here), so turning a page redraws only the title.
            LibraryTopBar(
                title = {
                    val page = pages.getOrNull(pagerState.currentPage)
                    when {
                        searching -> stringResource(R.string.library_search_title)
                        page != null -> pageTitle(page)
                        else -> stringResource(R.string.library_title)
                    }
                },
                onOpenSettings = onOpenSettings,
            )
            if (indexingInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }
            notices()

            Box(modifier = Modifier.weight(1f)) {
                if (pages.isEmpty()) {
                    EmptyLibrary()
                } else {
                    HorizontalPager(
                        state = pagerState,
                        key = { pages[it].id },
                        beyondViewportPageCount = keptPages,
                        // Settles quickly after the finger lifts, and a short flick is enough to turn the page.
                        flingBehavior =
                            PagerDefaults.flingBehavior(
                                state = pagerState,
                                snapPositionalThreshold = 0.3f,
                                snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 700f),
                            ),
                    ) { index ->
                        val page = pages[index]
                        val games =
                            remember(page.id) { viewModel.gamesOf(page, GameSortOrder.NAME) }
                                .collectAsLazyPagingItems()
                        GameGrid(
                            games = games,
                            emptyMessage =
                                when (page) {
                                    LibraryViewModel.Page.Favorites -> stringResource(R.string.library_favorites_empty)
                                    else -> null
                                },
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onPullToRefresh = rescan,
                            scanning = indexingInProgress,
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = searching,
                    enter = ArcaduneMotion.appear,
                    exit = ArcaduneMotion.disappear,
                ) {
                    val results = viewModel.searchResults.collectAsLazyPagingItems()
                    Box(modifier = Modifier.fillMaxSize().background(ArcaduneColors.Background)) {
                        GameGrid(
                            games = results,
                            emptyMessage = stringResource(R.string.library_search_empty),
                            emptyIcon = Icons.Filled.Search,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                        )
                    }
                }
            }
        }

        // A light shade under the bar, so names behind it never fight with the search text.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, ArcaduneColors.Background.copy(alpha = 0.7f))),
                    ),
        )

        val scope = rememberCoroutineScope()
        var scrubGlide by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        // L1 / R1 on a gamepad: the previous or next page. Quick presses add up (from the page it is
        // going to), and the ends do not wrap round. The cursor is let go of, so the d-pad starts again
        // on the new page instead of on a game of the old one.
        val pageCount by rememberUpdatedState(pages.size)
        DisposableEffect(pagerState, searching) {
            val turn: (Int) -> Unit = { direction ->
                val target = (pagerState.targetPage + direction).coerceIn(0, pageCount - 1)
                if (target != pagerState.targetPage) {
                    focusManager.clearFocus()
                    scrubGlide?.cancel()
                    scrubGlide = scope.launch { pagerState.animateScrollToPage(target) }
                }
            }
            if (!searching) LibraryPageKeys.attach(turn)
            onDispose { LibraryPageKeys.detach(turn) }
        }
        val keyboardOpen = WindowInsets.isImeVisible
        LibraryBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
            pages = pages,
            currentPage = { pagerState.currentPage },
            showPages = !searching && !keyboardOpen && pages.size > 1,
            onPageSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
            // Each new page from the scrub glides there; a newer one replaces a glide still running.
            onScrub = { page ->
                scrubGlide?.cancel()
                scrubGlide =
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page,
                            animationSpec = spring(dampingRatio = 1f, stiffness = 1200f),
                        )
                    }
            },
            query = viewModel.searchQuery,
            onQueryChange = { viewModel.searchQuery.value = it },
        )
    }
}

@Composable
private fun pageTitle(page: LibraryViewModel.Page): String =
    when (page) {
        LibraryViewModel.Page.Favorites -> stringResource(R.string.favorites)
        LibraryViewModel.Page.RecentlyPlayed -> stringResource(R.string.library_recently_played)
        is LibraryViewModel.Page.System -> stringResource(page.info.metaSystem.titleResId)
    }

@Composable
private fun LibraryTopBar(
    title: @Composable () -> String,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(
                    WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        ArcaduneIconPill(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.settings),
            size = 38.dp,
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        AnimatedContent(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
            targetState = title(),
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "title",
        ) { text ->
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                color = ArcaduneColors.OnSurface,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * How far (in dp) the finger has to travel down from the top of the list to arm the rescan. The list only
 * follows the finger by [PULL_RESISTANCE] of that, so it takes a deliberate pull and a casual drag is not enough.
 */
private const val PULL_TO_RESCAN_DP = 170

/** The list follows the finger with this share of its travel (it feels heavier the more you pull). */
private const val PULL_RESISTANCE = 0.4f

/** The most (in dp) the list moves down while pulling, and where it rests while the scan runs. */
private const val PULL_MAX_OFFSET_DP = 84
private const val PULL_REST_OFFSET_DP = 60

/**
 * Pull to refresh, like in other apps: with the list at its very top, dragging down moves the whole list
 * down with the finger; past [thresholdPx] it is armed (a buzz), and the rescan of the ROMs folder starts
 * when the finger is lifted while armed. Pulling back up before letting go cancels it. It only counts when
 * the list was already at the top when the finger went down, so scrolling back up to the top is one drag
 * and the pull is a new one. [onDistance] follows the finger travel for the list and the indicator. It never
 * takes any of the scrolling itself.
 */
private class PullToRefreshConnection(
    private val thresholdPx: Float,
    private val atTop: () -> Boolean,
    // Only a real finger pulls. Moving the gamepad cursor also scrolls the list "as user input",
    // which used to leave the arrow half shown and the list nudged down with no finger to let go.
    private val isTouching: () -> Boolean,
    private val onDistance: (Float) -> Unit,
    private val onArmed: () -> Unit,
    private val onPull: () -> Unit,
) : NestedScrollConnection {
    private var dragging = false
    private var startedAtTop = false
    private var pulled = 0f
    private var armed = false

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput && !dragging && isTouching()) {
            dragging = true
            startedAtTop = atTop()
            pulled = 0f
            armed = false
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput || !startedAtTop || !isTouching()) return Offset.Zero
        if (consumed.y != 0f) {
            // The list itself moved (going down the list): not a pull.
            pulled = 0f
            armed = false
        } else {
            pulled = (pulled + available.y).coerceAtLeast(0f)
            if (!armed && pulled >= thresholdPx) {
                armed = true
                onArmed()
            } else if (armed && pulled < thresholdPx) {
                armed = false
            }
        }
        onDistance(pulled)
        return Offset.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        val release = armed
        dragging = false
        startedAtTop = false
        pulled = 0f
        armed = false
        if (release) onPull()
        onDistance(0f)
        return Velocity.Zero
    }
}

/** The small round indicator of the pull: it fills as the finger pulls, and spins while the scan runs. */
@Composable
private fun RescanIndicator(
    progress: Float,
    spinning: Boolean,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = progress > 0.02f || spinning,
        modifier = modifier,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.6f),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.6f),
    ) {
        val accent = ArcaduneColors.Purple
        val armed = progress >= 1f
        // A small pop when the pull is long enough, so the badge answers the buzz.
        val pop by animateFloatAsState(
            targetValue = if (armed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.4f, stiffness = 520f),
            label = "pop",
        )
        val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
        val turn by spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(1100, easing = androidx.compose.animation.core.LinearEasing),
                ),
            label = "turn",
        )
        val scale = (0.7f + 0.3f * progress.coerceIn(0f, 1f)) * (1f + 0.18f * pop)
        // Just the circular arrow: it turns as the finger pulls, goes accent-coloured when the pull
        // counts, and keeps turning while the scan runs.
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            tint =
                if (armed || spinning) {
                    accent
                } else {
                    ArcaduneColors.OnSurface.copy(alpha = 0.5f + 0.45f * progress.coerceIn(0f, 1f))
                },
            modifier =
                Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = if (spinning) turn else 300f * progress.coerceIn(0f, 1f)
                    },
        )
    }
}

@Composable
private fun GameGrid(
    games: LazyPagingItems<Game>,
    emptyMessage: String?,
    emptyIcon: ImageVector = Icons.Outlined.StarOutline,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onPullToRefresh: (() -> Unit)? = null,
    scanning: Boolean = false,
) {
    if (games.itemCount == 0) {
        if (emptyMessage != null) CenteredMessage(emptyMessage, emptyIcon)
        return
    }

    // Room for the bottom bar, and for the keyboard while searching, so no result hides behind them.
    // In landscape the bar is a single row, so it needs less.
    val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val landscape = isLandscape()
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val pullThresholdPx = with(density) { PULL_TO_RESCAN_DP.dp.toPx() }
    val pullMaxOffsetPx = with(density) { PULL_MAX_OFFSET_DP.dp.toPx() }
    val pullRestOffsetPx = with(density) { PULL_REST_OFFSET_DP.dp.toPx() }
    val indicatorHalfPx = with(density) { 21.dp.toPx() }
    val currentPull by rememberUpdatedState(onPullToRefresh)
    var pullPx by remember { mutableFloatStateOf(0f) }
    var spinning by remember { mutableStateOf(false) }
    var sawScan by remember { mutableStateOf(false) }
    val scanningNow by rememberUpdatedState(scanning)
    // The spinner stays until the scan that the pull started has finished (or for a moment if none began).
    LaunchedEffect(scanning) {
        if (scanning) {
            sawScan = true
        } else if (sawScan) {
            spinning = false
            sawScan = false
        }
    }
    LaunchedEffect(spinning) {
        if (spinning) {
            delay(3000)
            if (!scanningNow) spinning = false
        }
    }
    // How many fingers are on the list right now (watched, never used up, so taps and scrolling work as before).
    val fingersDown = remember { intArrayOf(0) }
    val pullConnection =
        remember(pullThresholdPx) {
            PullToRefreshConnection(
                thresholdPx = pullThresholdPx,
                atTop = { !gridState.canScrollBackward },
                isTouching = { fingersDown[0] > 0 },
                onDistance = { pullPx = it },
                onArmed = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            ) {
                currentPull?.let {
                    spinning = true
                    it()
                }
            }
        }
    // The whole list follows the finger (heavier the further it goes), rests lower while the scan runs, and
    // springs back when let go.
    val listOffsetTarget =
        when {
            spinning -> pullRestOffsetPx
            pullPx > 0f -> (pullPx * PULL_RESISTANCE).coerceAtMost(pullMaxOffsetPx)
            else -> 0f
        }
    val listOffsetPx by animateFloatAsState(
        targetValue = listOffsetTarget,
        animationSpec =
            if (pullPx > 0f && !spinning) {
                snap()
            } else {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            },
        label = "listOffset",
    )
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                // Clear of the camera cutout and the side navigation bar in landscape.
                .windowInsetsPadding(
                    WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
                ),
    ) {
        LazyVerticalGrid(
            state = gridState,
            // Its own drawing layer: while pages slide, the phone moves the finished picture of the page
            // instead of drawing every cover again on every frame, and skips pages that are off screen.
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (onPullToRefresh != null) {
                            Modifier
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            fingersDown[0] = event.changes.count { it.pressed }
                                        }
                                    }
                                }
                                .nestedScroll(pullConnection)
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer { translationY = listOffsetPx },
            columns = GridCells.Fixed(libraryColumns(maxWidth)),
            contentPadding =
                PaddingValues(
                    start = 26.dp,
                    end = 26.dp,
                    top = 6.dp,
                    bottom = (if (landscape) 84.dp else 120.dp) + keyboard,
                ),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(games.itemCount, key = { games.peek(it)?.id ?: -it - 1 }, contentType = { "game" }) { index ->
                val game = games[index] ?: return@items
                GameTile(game = game, onClick = { onGameClick(game) }, onLongClick = { onGameLongClick(game) })
            }
        }
        if (onPullToRefresh != null) {
            // Like in other apps: it sits in the gap the list leaves at the top, fills as the pull gets
            // closer to counting, and spins there while the scan runs.
            RescanIndicator(
                progress = (pullPx / pullThresholdPx).coerceIn(0f, 1f),
                spinning = spinning,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer { translationY = listOffsetPx / 2f - indicatorHalfPx },
            )
        }
    }
}

/**
 * How many covers fit in a row of [width]: three on a phone held upright (the size chosen for it),
 * then one more for every 150dp: five on a phone held sideways, five to eight on tablets.
 */
private fun libraryColumns(width: Dp): Int {
    val usable = width - 52.dp + 24.dp
    return maxOf(3, (usable / 150.dp).toInt())
}

@Composable
private fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

/**
 * A cover at its own shape (portrait, landscape or square) with rounded corners, and the name below.
 * The tile shrinks a little while pressed, so every touch is felt right away.
 *
 * Built for smooth swiping: the tile has its final size from the first frame (the cover is fitted
 * inside a square, so nothing is laid out again when the image arrives), and the rounded corners
 * are cut into the image once, off the main thread, instead of being clipped on every frame.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameTile(
    game: Game,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    // Where a gamepad or keyboard is: the tile comes forward a little and its cover gets a ring.
    val focused by interactions.collectIsFocusedAsState()
    val focusRing = ArcaduneColors.Purple
    val scale by animateFloatAsState(
        if (pressed) {
            0.93f
        } else if (focused) {
            1.05f
        } else {
            1f
        },
        ArcaduneMotion.press(),
        label = "press",
    )
    val ringPx = with(density) { 3.dp.toPx() }

    val name = remember(game) { game.displayName() }
    val fallback = rememberDrawablePainter(remember { CoverUtils.getFallbackDrawable(context) })
    val cornerPx = with(density) { 10.dp.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }
    var coverSize by remember(game) { mutableStateOf(Size.Unspecified) }
    val request =
        remember(game, cornerPx) {
            ImageRequest.Builder(context)
                .data(game.displayCoverUrl())
                .transformations(RoundedCornersTransformation(cornerPx))
                .crossfade(100)
                .build()
        }

    // When the focus arrives (a gamepad moves it), the list scrolls to show the whole tile with some
    // room around it, clear of the title above and of the floating bar with the pages and the search below.
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var tileHeightPx by remember { mutableStateOf(0) }
    val marginTopPx = with(density) { 44.dp.toPx() }
    val marginBottomPx = with(density) { 160.dp.toPx() }

    Column(
        modifier =
            Modifier
                .onSizeChanged { tileHeightPx = it.height }
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        scope.launch {
                            bringIntoView.bringIntoView(
                                androidx.compose.ui.geometry.Rect(
                                    0f,
                                    -marginTopPx,
                                    1f,
                                    tileHeightPx + marginBottomPx,
                                ),
                            )
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .combinedClickable(
                    interactionSource = interactions,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            placeholder = null,
            fallback = fallback,
            error = fallback,
            onSuccess = { state -> coverSize = state.painter.intrinsicSize },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .drawWithContent {
                        drawContent()
                        val srcW = coverSize.width
                        val srcH = coverSize.height
                        if (srcW > 0f && srcH > 0f && size.width > 0f && size.height > 0f) {
                            val fitScale = minOf(size.width / srcW, size.height / srcH)
                            val w = srcW * fitScale
                            val h = srcH * fitScale
                            val halfStroke = strokePx / 2f
                            val left = (size.width - w) / 2f + halfStroke
                            val top = size.height - h + halfStroke
                            drawRoundRect(
                                color = Color(0x2EFFFFFF),
                                topLeft = Offset(left, top),
                                size = Size((w - strokePx).coerceAtLeast(0f), (h - strokePx).coerceAtLeast(0f)),
                                cornerRadius = CornerRadius(cornerPx, cornerPx),
                                style = Stroke(width = strokePx),
                            )
                            if (focused) {
                                drawRoundRect(
                                    color = focusRing,
                                    topLeft = Offset(left - ringPx / 2f, top - ringPx / 2f),
                                    size = Size(w - strokePx + ringPx, h - strokePx + ringPx),
                                    cornerRadius = CornerRadius(cornerPx + ringPx / 2f, cornerPx + ringPx / 2f),
                                    style = Stroke(width = ringPx),
                                )
                            }
                        }
                    },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = if (focused) androidx.compose.ui.text.font.FontWeight.Bold else null,
                ),
            color = ArcaduneColors.OnSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryBottomBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    pages: List<LibraryViewModel.Page>,
    currentPage: () -> Int,
    showPages: Boolean,
    onPageSelected: (Int) -> Unit,
    onScrub: (Int) -> Unit,
    query: StateFlow<String>,
    onQueryChange: (String) -> Unit,
) {
    val barModifier =
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).union(WindowInsets.displayCutout))
            .padding(horizontal = 28.dp, vertical = 8.dp)
    if (isLandscape()) {
        // Sideways the screen is short: the pages and the search sit side by side in one row,
        // so the covers keep most of the height.
        Row(
            modifier = barModifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = showPages, enter = ArcaduneMotion.appear, exit = ArcaduneMotion.disappear) {
                PageIndicator(hazeState, pages, currentPage, onPageSelected, onScrub, height = 48.dp)
            }
            Box(modifier = Modifier.widthIn(max = 360.dp)) {
                SearchField(hazeState, query, onQueryChange)
            }
        }
        return
    }
    Column(
        modifier = barModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        AnimatedVisibility(visible = showPages, enter = ArcaduneMotion.appear, exit = ArcaduneMotion.disappear) {
            PageIndicator(hazeState, pages, currentPage, onPageSelected, onScrub, maxWidth = 196.dp)
        }
        // On a tablet the search stays a comfortable width instead of stretching across the screen.
        Box(modifier = Modifier.widthIn(max = 560.dp)) {
            SearchField(hazeState, query, onQueryChange)
        }
    }
}

/**
 * Fades an end of the page pill while more pages are hidden that way, so it reads as a row that
 * goes on (drag it to see them). The bar itself stays sharp: only the icons fade.
 */
@Composable
private fun Modifier.fadingEdges(listState: LazyListState): Modifier {
    val start by animateFloatAsState(
        if (listState.canScrollBackward) 1f else 0f,
        ArcaduneMotion.quick(),
        label = "fadeStart",
    )
    val end by animateFloatAsState(
        if (listState.canScrollForward) 1f else 0f,
        ArcaduneMotion.quick(),
        label = "fadeEnd",
    )
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            // Over the outer 40dp: clear at the rim, about a third at the first icon, full after.
            val edge = (40.dp.toPx() / size.width).coerceAtMost(0.5f)

            fun faded(
                strength: Float,
                alpha: Float,
            ) = Color.Black.copy(alpha = 1f - strength * (1f - alpha))
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        0f to faded(start, 0f),
                        edge * 0.5f to faded(start, 0.3f),
                        edge to Color.Black,
                        1f - edge to Color.Black,
                        1f - edge * 0.5f to faded(end, 0.3f),
                        1f to faded(end, 0f),
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
}

/**
 * Frosted glass. On Android 12 and newer the content behind is really blurred; older versions get
 * a denser tint instead, because their blur (RenderScript) crashed the app during screen slides.
 */
private fun Modifier.glass(
    hazeState: HazeState,
    shape: RoundedCornerShape,
) = this
    .clip(shape)
    .then(
        if (BLUR_SUPPORTED) {
            Modifier.hazeEffect(hazeState) {
                blurRadius = 14.dp
                backgroundColor = ArcaduneColors.Background
                tints = listOf(HazeTint(ArcaduneColors.Glass.copy(alpha = 0.55f)))
                noiseFactor = 0f
            }
        } else {
            // Android 11 and older: a solid 90% bar, no blur work at all.
            Modifier.background(Color(0xFF1C1A27).copy(alpha = 0.9f))
        },
    )
    .border(1.dp, ArcaduneColors.GlassEdge, shape)

/**
 * The page control, like Delta's. Favorites and Recently Played always show their icons, the page
 * on screen shows its console in a bigger slot, and every other console is a dot.
 *
 * Sliding a finger along it scrubs through the pages, like the page dots on iOS: the selection
 * follows the finger right away, each new page gives a light tap you can feel, and the pages
 * glide to it.
 */
@Composable
private fun PageIndicator(
    hazeState: HazeState,
    pages: List<LibraryViewModel.Page>,
    currentPage: () -> Int,
    onPageSelected: (Int) -> Unit,
    onScrub: (Int) -> Unit,
    // 36 under the search; 48 beside it (landscape), the same height as the search.
    height: Dp = 36.dp,
    // 196 (30% narrower) under the search upright; 280 beside it sideways.
    maxWidth: Dp = 280.dp,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val ticks = remember { PillTicks(context.applicationContext) }
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentPageNow by rememberUpdatedState(currentPage)

    // While a finger is on the pill, the selection follows the finger, not the pages (which lag a
    // few frames behind as they glide).
    var scrubbing by remember { mutableStateOf<Int?>(null) }
    val selectedIndex = scrubbing ?: currentPage()
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0)) }

    val lift by animateFloatAsState(if (scrubbing != null) 1.06f else 1f, ArcaduneMotion.press(), label = "lift")

    LazyRow(
        state = listState,
        userScrollEnabled = false,
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                }
                .widthIn(max = maxWidth)
                .height(height)
                .glass(hazeState, RoundedCornerShape(50))
                .fadingEdges(listState)
                .pointerInput(Unit) {
                    // The slot under the finger (not the nearest centre: the chosen slot grows and
                    // pushes its neighbours, which would make the choice flick at the edges).
                    fun pick(x: Float) {
                        val info = listState.layoutInfo
                        val items = info.visibleItemsInfo
                        if (items.isEmpty()) return
                        val item =
                            items.firstOrNull { item ->
                                val start = item.offset + info.beforeContentPadding
                                x >= start && x < start + item.size
                            } ?: when {
                                x < items.first().offset + info.beforeContentPadding -> items.first()
                                x >= items.last().offset + info.beforeContentPadding + items.last().size -> items.last()
                                else -> return
                            }
                        if (item.index != scrubbing) {
                            scrubbing = item.index
                            ticks.tick()
                            currentOnScrub(item.index)
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            ticks.refreshHapticSetting()
                            scrubbing = currentPageNow()
                            pick(start.x)
                        },
                        onDragEnd = { scrubbing = null },
                        onDragCancel = { scrubbing = null },
                    ) { change, _ ->
                        change.consume()
                        pick(change.position.x)
                    }
                },
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
            val selected = index == selectedIndex
            val color by animateColorAsState(
                ArcaduneColors.OnSurface.copy(alpha = if (selected) 1f else 0.38f),
                ArcaduneMotion.quick(),
                label = "pageColor",
            )
            // 0 for a page that is not shown, 1 for the one shown. Every slot keeps the same width and
            // the morph only scales and fades, so the dot turns into the icon from its centre and the
            // other slots never move.
            val emphasis by animateFloatAsState(if (selected) 1f else 0f, ArcaduneMotion.press(), label = "emphasis")
            val isSystem = page is LibraryViewModel.Page.System
            val pageInteractions = remember { MutableInteractionSource() }
            val pageFocused by pageInteractions.collectIsFocusedAsState()
            Box(
                modifier =
                    Modifier
                        .size(width = 24.dp, height = height)
                        .then(
                            if (pageFocused) {
                                Modifier.border(2.dp, ArcaduneColors.Purple, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = pageInteractions,
                            indication = null,
                        ) {
                            ticks.click()
                            onPageSelected(index)
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (isSystem && emphasis < 1f) {
                    Box(
                        modifier =
                            Modifier
                                .size(6.dp)
                                .graphicsLayer {
                                    val shrink = (1f - emphasis).coerceIn(0f, 1f)
                                    scaleX = shrink
                                    scaleY = shrink
                                    alpha = shrink
                                }
                                .clip(CircleShape)
                                .background(color),
                    )
                }
                if (!isSystem || emphasis > 0f) {
                    Image(
                        painter = painterResource(pageIcon(page)),
                        contentDescription = pageTitle(page),
                        modifier =
                            Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    // Star and clock rest at 16 of 21; console icons grow out of the dot.
                                    val rest = if (isSystem) 0.3f else 0.76f
                                    val scale = rest + (1f - rest) * emphasis
                                    scaleX = scale
                                    scaleY = scale
                                    if (isSystem) alpha = emphasis.coerceIn(0f, 1f)
                                },
                        colorFilter = ColorFilter.tint(color),
                    )
                }
            }
        }
    }
}

/**
 * Short vibrations for the page pill, played on the vibrator like the game buttons do: some phones
 * (Samsung) silently drop View.performHapticFeedback. Follows the phone's touch vibration setting.
 */
private class PillTicks(private val context: Context) {
    private val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
    private val hasVibrator = vibrator?.hasVibrator() == true
    private var hapticsEnabled = queryHapticSetting()

    private fun queryHapticSetting(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    fun refreshHapticSetting() {
        hapticsEnabled = queryHapticSetting()
    }

    fun tick() = play(strong = false)

    fun click() {
        refreshHapticSetting()
        play(strong = true)
    }

    private fun play(strong: Boolean) {
        val vibrator = vibrator ?: return
        if (!hasVibrator || !hapticsEnabled) return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vibrator.vibrate(
                    VibrationEffect.createPredefined(
                        if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK,
                    ),
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(
                    VibrationEffect.createOneShot(if (strong) 12L else 6L, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            else ->
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (strong) 12L else 6L)
        }
    }
}

private fun pageIcon(page: LibraryViewModel.Page): Int =
    when (page) {
        LibraryViewModel.Page.Favorites -> R.drawable.ic_page_favorites
        LibraryViewModel.Page.RecentlyPlayed -> R.drawable.ic_page_recent
        is LibraryViewModel.Page.System ->
            when (page.info.metaSystem) {
                MetaSystemID.NES, MetaSystemID.SMS, MetaSystemID.PC_ENGINE -> R.drawable.ic_console_pad
                MetaSystemID.SNES, MetaSystemID.THREEDO -> R.drawable.ic_console_snes
                MetaSystemID.N64 -> R.drawable.ic_console_n64
                MetaSystemID.GB, MetaSystemID.GBC -> R.drawable.ic_console_gb
                MetaSystemID.GBA, MetaSystemID.GG, MetaSystemID.NGP, MetaSystemID.WS -> R.drawable.ic_console_gba
                MetaSystemID.NDS, MetaSystemID.NINTENDO_3DS -> R.drawable.ic_console_ds
                MetaSystemID.GENESIS, MetaSystemID.SATURN, MetaSystemID.SEGA32X -> R.drawable.ic_console_genesis
                MetaSystemID.PSX, MetaSystemID.DREAMCAST -> R.drawable.ic_console_psx
                MetaSystemID.PSP, MetaSystemID.LYNX -> R.drawable.ic_console_psp
                MetaSystemID.ARCADE,
                MetaSystemID.ATARI2600,
                MetaSystemID.ATARI5200,
                MetaSystemID.ATARI7800,
                MetaSystemID.COLECOVISION,
                -> R.drawable.ic_console_joystick
                MetaSystemID.DOS,
                MetaSystemID.ATARI800,
                MetaSystemID.C64,
                MetaSystemID.APPLEII,
                MetaSystemID.MSX,
                -> R.drawable.ic_console_dos
            }
    }

@Composable
private fun SearchField(
    hazeState: HazeState,
    queryFlow: StateFlow<String>,
    onQueryChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    // The field keeps the text it shows itself, so a key press appears at once and the cursor never
    // jumps back (the library's own copy of the query arrives a frame later). Only this field reads
    // the query, so typing does not rebuild the whole library screen.
    var field by remember { mutableStateOf(TextFieldValue(queryFlow.value)) }
    val query = field.text
    val outside by queryFlow.collectAsState()
    LaunchedEffect(outside) { if (outside != field.text) field = TextFieldValue(outside) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .glass(hazeState, RoundedCornerShape(50))
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = ArcaduneColors.OnSurfaceDim,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            val textStyle = MaterialTheme.typography.bodyMedium
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.library_search_hint),
                    color = ArcaduneColors.OnSurfaceDim,
                    style = textStyle,
                )
            }
            BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = field,
                onValueChange = { new ->
                    val changed = new.text != field.text
                    field = new
                    if (changed) onQueryChange(new.text)
                },
                singleLine = true,
                textStyle = textStyle.copy(color = ArcaduneColors.OnSurface),
                cursorBrush = SolidColor(ArcaduneColors.Purple),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            field = TextFieldValue("")
                            onQueryChange("")
                            focusManager.clearFocus()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.library_search_clear),
                    tint = ArcaduneColors.OnSurfaceDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.VideogameAsset,
            contentDescription = null,
            tint = ArcaduneColors.OnSurfaceDim,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.library_empty),
            color = ArcaduneColors.OnSurfaceDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    icon: ImageVector = Icons.Outlined.StarOutline,
) {
    val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomPadding = (if (isLandscape()) 84.dp else 120.dp) + keyboard
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ArcaduneColors.OnSurfaceDim,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = message, color = ArcaduneColors.OnSurfaceDim, textAlign = TextAlign.Center)
    }
}

private val BLUR_SUPPORTED = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
