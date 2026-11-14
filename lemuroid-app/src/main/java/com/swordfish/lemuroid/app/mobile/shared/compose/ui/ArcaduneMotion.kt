package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

/**
 * Shared motion specs across Arcadune. Interactive presses use responsive springs, while
 * full-screen page navigation uses 100%-opaque horizontal slides (no alpha fade) to eliminate
 * translucent ghosting and avoid full-screen SkiaGL SaveLayer FBO allocations on Android GPUs.
 */
object ArcaduneMotion {
    /** Buttons and tiles sinking under the finger and popping back. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = 900f)

    /** Colours and small state changes. */
    fun <T> quick(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    private val slideEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    private val pushSlide = tween<IntOffset>(durationMillis = 220, easing = slideEasing)
    private val popSlide = tween<IntOffset>(durationMillis = 200, easing = slideEasing)

    val pushEnter: EnterTransition = slideInHorizontally(pushSlide) { it }

    val pushExit: ExitTransition = slideOutHorizontally(pushSlide) { -it / 4 }

    val popEnter: EnterTransition = slideInHorizontally(popSlide) { -it / 4 }

    val popExit: ExitTransition = slideOutHorizontally(popSlide) { it }

    /**
     * Pause-menu pages are translucent. Sliding two of them over one another makes the outgoing
     * tiles remain visible as a ghost, so this fade is deliberately sequential: the old page is
     * gone before the new one appears.
     */
    val menuEnter: EnterTransition = fadeIn(tween(durationMillis = 140, delayMillis = 80))

    val menuExit: ExitTransition = fadeOut(tween(durationMillis = 80))

    /** Something appearing in place, like the pause menu or search results. */
    val appear: EnterTransition =
        fadeIn(spring(dampingRatio = 1f, stiffness = 600f)) +
            scaleIn(spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.94f)

    val disappear: ExitTransition = fadeOut(spring(dampingRatio = 1f, stiffness = 900f))
}
