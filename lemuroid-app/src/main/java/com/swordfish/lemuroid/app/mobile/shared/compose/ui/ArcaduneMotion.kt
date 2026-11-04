package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

/**
 * One set of motions for the whole app, in the spirit of iOS: springs instead of fixed-time
 * curves, so things settle naturally and can be interrupted without a jump.
 */
object ArcaduneMotion {
    /** Buttons and tiles sinking under the finger and popping back. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = 900f)

    /** Colours and small state changes. */
    fun <T> quick(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    private val slide = spring<IntOffset>(dampingRatio = 1f, stiffness = 1500f)
    private val fade = spring<Float>(dampingRatio = 1f, stiffness = 1800f)

    /** A screen pushed on top slides in from the right... */
    val pushEnter: EnterTransition = slideInHorizontally(slide) { it } + fadeIn(fade, initialAlpha = 0.6f)

    /** ...while the one below drifts a little to the left and dims. */
    val pushExit: ExitTransition = slideOutHorizontally(slide) { -it / 4 } + fadeOut(fade, targetAlpha = 0.4f)

    /** Going back: the screen below comes back from the left... */
    val popEnter: EnterTransition = slideInHorizontally(slide) { -it / 4 } + fadeIn(fade, initialAlpha = 0.4f)

    /** ...and the top one slides away to the right. */
    val popExit: ExitTransition = slideOutHorizontally(slide) { it } + fadeOut(fade, targetAlpha = 0.6f)

    /** Something appearing in place, like the pause menu or search results. */
    val appear: EnterTransition =
        fadeIn(spring(dampingRatio = 1f, stiffness = 600f)) +
            scaleIn(spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.94f)

    val disappear: ExitTransition = fadeOut(spring(dampingRatio = 1f, stiffness = 900f))
}
