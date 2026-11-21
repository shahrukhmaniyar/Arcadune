package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.swordfish.lemuroid.R

fun NavGraphBuilder.composable(
    route: MainRoute,
    navController: NavHostController? = null,
    onHelpPressed: (() -> Unit)? = null,
    mainUIState: MainViewModel.UiState? = null,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    this.composable(route = route.route, arguments = route.arguments) { entry ->
        val showInPageTopBar =
            route != MainRoute.HOME &&
                route != MainRoute.SETTINGS_SKIN_LAYOUT &&
                navController != null &&
                onHelpPressed != null &&
                mainUIState != null

        // A 100% opaque surface per destination so underlying screens never bleed through.
        if (showInPageTopBar && navController != null && onHelpPressed != null && mainUIState != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                MainTopBar(
                    route = route,
                    navController = navController,
                    onHelpPressed = onHelpPressed,
                    mainUIState = mainUIState,
                )
                Box(Modifier.weight(1f).fillMaxSize()) {
                    this@composable.content(entry)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                content(entry)
            }
        }
    }
}

fun NavController.navigateToRoute(route: MainRoute) {
    val currentLifecycle = currentBackStackEntry?.lifecycle?.currentState
    if (currentLifecycle != null && !currentLifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
        return
    }
    this.navigate(route.route) {
        launchSingleTop = true
    }
}

fun NavController.popBackStackIfResumed() {
    val currentLifecycle = currentBackStackEntry?.lifecycle?.currentState
    if (currentLifecycle != null && !currentLifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
        return
    }
    if (previousBackStackEntry != null) {
        popBackStack()
    }
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
        parent = HOME,
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
    SETTINGS_CORES_SELECTION(
        route = "settings/cores",
        titleId = R.string.settings_title_open_cores_selection,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_SKINS(
        route = "settings/skins",
        titleId = R.string.skin_gallery_title,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),

    /** One console's layout editor; [skinLayoutRoute] builds the route. */
    SETTINGS_SKIN_LAYOUT(
        route = "settings/skins/layout/{controller}?landscape={landscape}",
        titleId = R.string.skin_layout_title,
        parent = SETTINGS_SKINS,
        arguments =
            listOf(
                navArgument("controller") { type = NavType.StringType },
                navArgument("landscape") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
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
    SETTINGS_SAVES(
        route = "settings/saves",
        titleId = R.string.settings_title_saves,
        parent = SETTINGS,
        showBottomNavigation = false,
    ),
    SETTINGS_RETRO_ACHIEVEMENTS(
        route = "settings/retroachievements",
        titleId = R.string.settings_title_retro_achievements,
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
        fun findByRoute(route: String): MainRoute? {
            return entries.firstOrNull { it.route == route }
        }
    }
}
