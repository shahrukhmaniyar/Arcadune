@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats.GameMenuCheatsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats.GameMenuCheatsViewModel
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsViewModel
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.common.kotlin.serializable
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import java.security.InvalidParameterException
import javax.inject.Inject

class GameMenuActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    data class GameMenuRequest(
        val coreOptions: List<LemuroidCoreOption>,
        val advancedCoreOptions: List<LemuroidCoreOption>,
        val game: Game,
        val coreConfig: SystemCoreConfig,
        val audioEnabled: Boolean,
        val fastForwardSupported: Boolean,
        val fastForwardEnabled: Boolean,
        val numDisks: Int,
        val currentDisk: Int,
        val currentTiltConfiguration: TiltConfiguration,
        val allTiltConfigurations: List<TiltConfiguration>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // Keep the game full screen while the menu is open: without this Android brings its
        // navigation bar back and the game behind the menu jumps up to make room for it.
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }

        // Blur the paused game behind the menu where the system can (Android 12 and newer).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 48 }
        }

        val extras = intent.extras

        val gameMenuRequest =
            GameMenuRequest(
                coreOptions =
                    intent.serializable<Array<LemuroidCoreOption>>(GameMenuContract.EXTRA_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_CORE_OPTIONS"),
                advancedCoreOptions =
                    intent.serializable<Array<LemuroidCoreOption>>(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_ADVANCED_CORE_OPTIONS"),
                game =
                    intent.serializable<Game>(GameMenuContract.EXTRA_GAME)
                        ?: throw InvalidParameterException("Missing EXTRA_GAME"),
                coreConfig =
                    intent.serializable<SystemCoreConfig>(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG)
                        ?: throw InvalidParameterException("Missing EXTRA_SYSTEM_CORE_CONFIG"),
                audioEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_AUDIO_ENABLED, false) ?: false,
                fastForwardSupported =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, false) ?: false,
                fastForwardEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD, false) ?: false,
                numDisks =
                    extras?.getInt(GameMenuContract.EXTRA_DISKS, 0) ?: 0,
                currentDisk =
                    extras?.getInt(GameMenuContract.EXTRA_CURRENT_DISK, 0) ?: 0,
                currentTiltConfiguration =
                    intent.serializable<TiltConfiguration>(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG)
                        ?: TiltConfiguration.Disabled,
                allTiltConfigurations =
                    intent.serializable<Array<TiltConfiguration>>(GameMenuContract.EXTRA_TILT_ALL_CONFIGS)
                        ?.toList()
                        ?: emptyList(),
            )

        setContent {
            GameMenuScreen(gameMenuRequest)
        }
    }

    @Composable
    private fun GameMenuScreen(gameMenuRequest: GameMenuRequest) {
        AppTheme {
            val navController = rememberNavController()

            // Like Delta's pause menu: every page fills the screen over the paused game, and the
            // pages it opens (save states, cheats, settings) slide in from the right.
            NavHost(
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                startDestination = GameMenuRoute.HOME.route,
                enterTransition = { ArcaduneMotion.pushEnter },
                exitTransition = { ArcaduneMotion.pushExit },
                popEnterTransition = { ArcaduneMotion.popEnter },
                popExitTransition = { ArcaduneMotion.popExit },
            ) {
                composable(GameMenuRoute.HOME) {
                    GameMenuHomeScreen(navController, gameMenuRequest, ::onResult)
                }
                composable(GameMenuRoute.CHEATS) {
                    SubMenu(GameMenuRoute.CHEATS, navController) {
                        GameMenuCheatsScreen(
                            viewModel(
                                factory = GameMenuCheatsViewModel.Factory(application, gameMenuRequest.game),
                            ),
                        )
                    }
                }
                composable(GameMenuRoute.SAVE) {
                    SubMenu(GameMenuRoute.SAVE, navController) {
                        GameMenuStatesScreen(
                            viewModel(
                                factory =
                                    GameMenuStatesViewModel.Factory(
                                        application,
                                        gameMenuRequest,
                                        statesManager,
                                        false,
                                        statesPreviewManager,
                                    ),
                            ),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_SAVE, it) }
                            },
                        )
                    }
                }
                composable(GameMenuRoute.LOAD) {
                    SubMenu(GameMenuRoute.LOAD, navController) {
                        GameMenuStatesScreen(
                            viewModel(
                                factory =
                                    GameMenuStatesViewModel.Factory(
                                        application,
                                        gameMenuRequest,
                                        statesManager,
                                        true,
                                        statesPreviewManager,
                                    ),
                            ),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_LOAD, it) }
                            },
                        )
                    }
                }
                composable(GameMenuRoute.OPTIONS) {
                    SubMenu(GameMenuRoute.OPTIONS, navController) {
                        GameMenuCoreOptionsScreen(
                            viewModel(
                                factory = GameMenuCoreOptionsViewModel.Factory(inputDeviceManager),
                            ),
                            gameMenuRequest,
                        )
                    }
                }
            }
        }
    }

    /**
     * A page opened from the pause menu, like Delta's: it takes the whole screen over the paused
     * game, with a round back button on the left and the title in the middle.
     */
    @Composable
    private fun SubMenu(
        route: GameMenuRoute,
        navController: NavController,
        content: @Composable () -> Unit,
    ) = androidx.compose.runtime.CompositionLocalProvider(
        // Without a Surface around it, text and icons would default to black on this dark page.
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Nearly solid, so lists stay readable over a busy game.
                    .background(ArcaduneColors.Background.copy(alpha = 0.94f))
                    .safeDrawingPadding(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f))
                            .border(1.dp, ArcaduneColors.GlassEdge, CircleShape)
                            .clickable { navController.popBackStack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(route.titleId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // Without a Surface around it, text would default to black on this dark page.
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            ) {
                Box(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
    }

    private fun onResult(block: Intent.() -> Unit) {
        val resultIntent = Intent()
        resultIntent.block()
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    @dagger.Module
    abstract class Module
}
