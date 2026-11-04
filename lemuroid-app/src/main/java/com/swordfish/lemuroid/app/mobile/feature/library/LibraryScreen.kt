package com.swordfish.lemuroid.app.mobile.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.transform.RoundedCornersTransformation
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.db.dao.GameSortOrder
import com.swordfish.lemuroid.lib.library.db.entity.Game
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
    val query by viewModel.searchQuery.collectAsState()
    val searching = query.isNotBlank()
    val focusManager = LocalFocusManager.current
    val hazeState = remember { HazeState() }

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
        if (pages == null) return@Box

        // Delta opens on Recently Played; without it, on the first console.
        var startPageId by rememberSaveable { mutableStateOf<String?>(null) }
        if (startPageId == null && pages.isNotEmpty()) {
            startPageId =
                (pages.firstOrNull { it is LibraryViewModel.Page.RecentlyPlayed } ?: pages.getOrNull(1) ?: pages.first()).id
        }
        val pagerState =
            rememberPagerState(initialPage = pages.indexOfFirst { it.id == startPageId }.coerceAtLeast(0)) { pages.size }

        // Every page is built once, a moment after the library opens, and then kept: swiping only
        // moves pages that already exist instead of building the next one in the middle of a swipe.
        var keptPages by remember { mutableStateOf(1) }
        LaunchedEffect(pages.size) {
            kotlinx.coroutines.delay(700)
            keptPages = pages.size.coerceAtLeast(1)
        }

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
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = searching, enter = ArcaduneMotion.appear, exit = ArcaduneMotion.disappear) {
                    val results = viewModel.searchResults.collectAsLazyPagingItems()
                    Box(modifier = Modifier.fillMaxSize().background(ArcaduneColors.Background)) {
                        GameGrid(
                            games = results,
                            emptyMessage = stringResource(R.string.library_search_empty),
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
                    .background(Brush.verticalGradient(listOf(Color.Transparent, ArcaduneColors.Background.copy(alpha = 0.7f)))),
        )

        val scope = rememberCoroutineScope()
        var scrubGlide by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
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
                scrubGlide = scope.launch {
                    pagerState.animateScrollToPage(page, animationSpec = spring(dampingRatio = 1f, stiffness = 1200f))
                }
            },
            query = query,
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
        }
        AnimatedContent(
            modifier = Modifier.weight(1f),
            targetState = title(),
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "title",
        ) { text ->
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        }
    }
}

@Composable
private fun GameGrid(
    games: LazyPagingItems<Game>,
    emptyMessage: String?,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    if (games.itemCount == 0) {
        if (emptyMessage != null) CenteredMessage(emptyMessage)
        return
    }

    // Room for the bottom bar, and for the keyboard while searching, so no result hides behind them.
    val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        // Its own drawing layer: while pages slide, the phone moves the finished picture of the page
        // instead of drawing every cover again on every frame, and skips pages that are off screen.
        modifier = Modifier.fillMaxSize().graphicsLayer(),
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 120.dp + keyboard),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(games.itemCount, key = { games.peek(it)?.id ?: -it - 1 }, contentType = { "game" }) { index ->
            val game = games[index] ?: return@items
            GameTile(game = game, onClick = { onGameClick(game) }, onLongClick = { onGameLongClick(game) })
        }
    }
}

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
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, ArcaduneMotion.press(), label = "press")

    val name = remember(game) { game.displayName() }
    val fallback = rememberDrawablePainter(remember(game) { CoverUtils.getFallbackDrawable(game) })
    val request =
        remember(game) {
            ImageRequest.Builder(context)
                .data(game.displayCoverUrl())
                .transformations(RoundedCornersTransformation(with(density) { 8.dp.toPx() }))
                .crossfade(100)
                .build()
        }

    Column(
        modifier =
            Modifier
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
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter,
            placeholder = null,
            fallback = fallback,
            error = fallback,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
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
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        AnimatedVisibility(visible = showPages, enter = ArcaduneMotion.appear, exit = ArcaduneMotion.disappear) {
            PageIndicator(hazeState, pages, currentPage, onPageSelected, onScrub)
        }
        SearchField(hazeState, query, onQueryChange)
    }
}

/**
 * Fades an end of the page pill while more pages are hidden that way, so it reads as a row that
 * goes on (drag it to see them). The bar itself stays sharp: only the icons fade.
 */
@Composable
private fun Modifier.fadingEdges(listState: LazyListState): Modifier {
    val start by animateFloatAsState(if (listState.canScrollBackward) 1f else 0f, ArcaduneMotion.quick(), label = "fadeStart")
    val end by animateFloatAsState(if (listState.canScrollForward) 1f else 0f, ArcaduneMotion.quick(), label = "fadeEnd")
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            // Over the outer 52dp: clear at the rim, about a third at the first icon, full after.
            val edge = (52.dp.toPx() / size.width).coerceAtMost(0.5f)
            fun faded(strength: Float, alpha: Float) = Color.Black.copy(alpha = 1f - strength * (1f - alpha))
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
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem((selectedIndex - 4).coerceAtLeast(0)) }

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
                .widthIn(max = 280.dp)
                .height(36.dp)
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
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
            Box(
                modifier =
                    Modifier
                        .size(width = 28.dp, height = 36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
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
                                .size(7.dp)
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
                                .size(22.dp)
                                .graphicsLayer {
                                    // Star and clock rest at 16 of 22; console icons grow out of the dot.
                                    val rest = if (isSystem) 0.3f else 0.73f
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

/** Arcadune's own icons, drawn in the style of Delta's: a star, a clock, and a silhouette per console. */
/**
 * Short vibrations for the page pill, played on the vibrator like the game buttons do: some phones
 * (Samsung) silently drop View.performHapticFeedback. Follows the phone's touch vibration setting.
 */
private class PillTicks(private val context: Context) {
    private val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)

    fun tick() = play(strong = false)

    fun click() = play(strong = true)

    private fun play(strong: Boolean) {
        val vibrator = vibrator ?: return
        val enabled = Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        if (!enabled || !vibrator.hasVibrator()) return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vibrator.vibrate(
                    VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK),
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(VibrationEffect.createOneShot(if (strong) 12L else 6L, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> @Suppress("DEPRECATION") vibrator.vibrate(if (strong) 12L else 6L)
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
                MetaSystemID.SNES -> R.drawable.ic_console_snes
                MetaSystemID.N64 -> R.drawable.ic_console_n64
                MetaSystemID.GB, MetaSystemID.GBC -> R.drawable.ic_console_gb
                MetaSystemID.GBA, MetaSystemID.GG, MetaSystemID.NGP, MetaSystemID.WS -> R.drawable.ic_console_gba
                MetaSystemID.NDS, MetaSystemID.NINTENDO_3DS -> R.drawable.ic_console_ds
                MetaSystemID.GENESIS -> R.drawable.ic_console_genesis
                MetaSystemID.PSX -> R.drawable.ic_console_psx
                MetaSystemID.PSP, MetaSystemID.LYNX -> R.drawable.ic_console_psp
                MetaSystemID.ARCADE, MetaSystemID.ATARI2600, MetaSystemID.ATARI7800 -> R.drawable.ic_console_joystick
                MetaSystemID.DOS -> R.drawable.ic_console_dos
            }
    }

@Composable
private fun SearchField(
    hazeState: HazeState,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .glass(hazeState, RoundedCornerShape(50))
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = ArcaduneColors.OnSurfaceDim, modifier = Modifier.size(18.dp))
        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            val textStyle = MaterialTheme.typography.bodyMedium
            if (query.isEmpty()) {
                Text(stringResource(R.string.library_search_hint), color = ArcaduneColors.OnSurfaceDim, style = textStyle)
            }
            BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = textStyle.copy(color = ArcaduneColors.OnSurface),
                cursorBrush = SolidColor(ArcaduneColors.Purple),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.library_search_clear),
                tint = ArcaduneColors.OnSurfaceDim,
                modifier =
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable {
                            onQueryChange("")
                            focusManager.clearFocus()
                        }
                        .padding(4.dp),
            )
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
private fun CenteredMessage(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.StarOutline,
            contentDescription = null,
            tint = ArcaduneColors.OnSurfaceDim,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = message, color = ArcaduneColors.OnSurfaceDim, textAlign = TextAlign.Center)
    }
}

private val BLUR_SUPPORTED = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
