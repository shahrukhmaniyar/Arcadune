package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.swordfish.lemuroid.R

fun NavGraphBuilder.composable(
    route: MainRoute,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    this.composable(route = route.route, arguments = route.arguments) { entry ->
        // A solid background, so the screen sliding away never shows between the cards of this one.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content(entry)

            // While two screens cross-fade, the one fading out is still underneath and a tap on an
            // empty part of the new screen would reach it (for example opening a game of the home
            // screen). Nothing can be touched until the transition is over.
            if (transition.currentState != transition.targetState) {
                Box(Modifier.fillMaxSize().consumeAllTouches())
            }
        }
    }
}

private fun Modifier.consumeAllTouches(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }

fun NavController.navigateToRoute(route: MainRoute) {
    this.navigate(route.route)
}

enum class MainRoute(
    val route: String,
    @StringRes val titleId: Int,
    val parent: MainRoute? = null,
    val arguments: List<NamedNavArgument> = emptyList(),
    val showBottomNavigation: Boolean = true,
) {
    HOME(
        route = "home",
        titleId = R.string.title_home,
    ),
    SETTINGS(
        route = "settings/home",
        titleId = R.string.title_settings,
        showBottomNavigation = false,
    ),
    SETTINGS_ADVANCED(
        route = "settings/advanced",
        titleId = R.string.settings_title_advanced_settings,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_BIOS(
        route = "settings/bios",
        titleId = R.string.settings_title_display_bios_info,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_INPUT_DEVICES(
        route = "settings/inputdevices",
        titleId = R.string.settings_title_gamepad_settings,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_SAVE_SYNC(
        route = "settings/savesync",
        titleId = R.string.settings_title_save_sync,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    COVER_MANAGER(
        route = "covers/manager",
        titleId = R.string.cover_manager_title,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    ;

    val root = root()

    private fun root(): MainRoute {
        return parent?.root() ?: this
    }

    companion object {
        fun findByRoute(route: String): MainRoute {
            return values().first { it.route == route }
        }
    }
}

