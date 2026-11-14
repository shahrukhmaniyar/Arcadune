package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.swordfish.lemuroid.R

fun NavGraphBuilder.composable(
    route: GameMenuRoute,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    this.composable(route = route.route, content = content)
}

fun NavController.navigateToRoute(route: GameMenuRoute) {
    this.navigate(route.route)
}

enum class GameMenuRoute(
    val route: String,
    val titleId: Int,
    val parent: GameMenuRoute?,
) {
    HOME(
        route = "home",
        titleId = R.string.game_menu_title,
        parent = null,
    ),
    SAVE(
        route = "save",
        titleId = R.string.game_menu_save,
        parent = HOME,
    ),
    LOAD(
        route = "load",
        titleId = R.string.game_menu_load,
        parent = HOME,
    ),
    OPTIONS(
        route = "options",
        titleId = R.string.game_menu_settings,
        parent = HOME,
    ),
    FAST_FORWARD(
        route = "fast_forward",
        titleId = R.string.game_menu_fast_forward,
        parent = HOME,
    ),
    TILT(
        route = "tilt",
        titleId = R.string.game_menu_tilt_sensor,
        parent = HOME,
    ),
    DISKS(
        route = "disks",
        titleId = R.string.game_menu_change_disk_button,
        parent = HOME,
    ),
    CHEATS(
        route = "cheats",
        titleId = R.string.game_menu_cheats,
        parent = HOME,
    ),
    ACHIEVEMENTS(
        route = "achievements",
        titleId = R.string.game_menu_achievements,
        parent = HOME,
    ),
    ;

    fun canGoBack(): Boolean {
        return parent != null
    }

    companion object {
        fun findByRoute(route: String): GameMenuRoute {
            return values().first { it.route == route }
        }
    }
}
