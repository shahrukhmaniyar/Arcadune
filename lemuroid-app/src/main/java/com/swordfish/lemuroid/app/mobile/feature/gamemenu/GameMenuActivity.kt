@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Looks3
import androidx.compose.material.icons.filled.Looks4
import androidx.compose.material.icons.filled.Looks5
import androidx.compose.material.icons.filled.Looks6
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements.GameMenuAchievementsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats.GameMenuCheatsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats.GameMenuCheatsViewModel
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsViewModel
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameSettingSelection
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesViewModel
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.tilt.TiltConfigurationMenuEntry
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
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
        val frameSpeed: Int,
        val maxFrameSpeed: Int,
        val startRoute: String?,
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
        hideSystemBars()

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
                frameSpeed =
                    extras?.getInt(GameMenuContract.EXTRA_FRAME_SPEED, 1) ?: 1,
                maxFrameSpeed =
                    extras?.getInt(GameMenuContract.EXTRA_MAX_FRAME_SPEED, 8) ?: 8,
                startRoute =
                    extras?.getString(GameMenuContract.EXTRA_START_ROUTE),
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

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun GameMenuScreen(gameMenuRequest: GameMenuRequest) {
        val imeVisible = WindowInsets.isImeVisible
        LaunchedEffect(imeVisible) {
            if (!imeVisible) hideSystemBars()
        }
        AppTheme {
            val navController = rememberNavController()
            val startDestination =
                if (gameMenuRequest.startRoute == GameMenuContract.START_ROUTE_FAST_FORWARD) {
                    GameMenuRoute.FAST_FORWARD.route
                } else {
                    GameMenuRoute.HOME.route
                }

            // Like Delta's pause menu: every page fills the screen over the paused game, and the
            // pages it opens (save states, cheats, settings) fade sequentially without ghosting.
            NavHost(
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                startDestination = startDestination,
                enterTransition = { ArcaduneMotion.menuEnter },
                exitTransition = { ArcaduneMotion.menuExit },
                popEnterTransition = { ArcaduneMotion.menuEnter },
                popExitTransition = { ArcaduneMotion.menuExit },
            ) {
                composable(GameMenuRoute.HOME) {
                    GameMenuHomeScreen(navController, gameMenuRequest, ::onResult)
                }
                composable(GameMenuRoute.FAST_FORWARD) {
                    SubMenu(GameMenuRoute.FAST_FORWARD, navController, menuLook = true) {
                        GameMenuFastForwardScreen(gameMenuRequest.frameSpeed, gameMenuRequest.maxFrameSpeed) { speed ->
                            onResult { putExtra(GameMenuContract.RESULT_FRAME_SPEED, speed) }
                        }
                    }
                }
                composable(GameMenuRoute.TILT) {
                    val entries =
                        gameMenuRequest.allTiltConfigurations.map {
                            TiltConfigurationMenuEntry.fromTiltConfiguration(it, gameMenuRequest.allTiltConfigurations)
                        }
                    SubMenu(GameMenuRoute.TILT, navController, menuLook = true) {
                        GameMenuChoiceScreen(
                            title = stringResource(R.string.game_menu_tilt_sensor),
                            choices = entries.map { stringResource(it.descriptionId) },
                            icons = entries.map(::tiltChoiceIcon),
                            selected =
                                gameMenuRequest.allTiltConfigurations.indexOf(
                                    gameMenuRequest.currentTiltConfiguration,
                                ),
                            onChoice = { index ->
                                onResult {
                                    putExtra(
                                        GameMenuContract.RESULT_CHANGE_TILT_CONFIG,
                                        entries[index].configuration,
                                    )
                                }
                            },
                        )
                    }
                }
                composable(GameMenuRoute.DISKS) {
                    SubMenu(GameMenuRoute.DISKS, navController, menuLook = true) {
                        GameMenuChoiceScreen(
                            title = stringResource(R.string.game_menu_change_disk_button),
                            choices =
                                (1..gameMenuRequest.numDisks).map {
                                    stringResource(R.string.game_menu_change_disk_disk, it)
                                },
                            icons =
                                (1..gameMenuRequest.numDisks).map { diskNum ->
                                    when (diskNum) {
                                        1 -> Icons.Filled.LooksOne
                                        2 -> Icons.Filled.LooksTwo
                                        3 -> Icons.Filled.Looks3
                                        4 -> Icons.Filled.Looks4
                                        5 -> Icons.Filled.Looks5
                                        6 -> Icons.Filled.Looks6
                                        else -> Icons.Filled.Album
                                    }
                                },
                            selected = gameMenuRequest.currentDisk,
                            onChoice = { index ->
                                onResult { putExtra(GameMenuContract.RESULT_CHANGE_DISK, index) }
                            },
                        )
                    }
                }
                composable(GameMenuRoute.CHEATS) {
                    var addingCheat by rememberSaveable { mutableStateOf(false) }
                    val focusManager = LocalFocusManager.current
                    val goBack: () -> Unit = {
                        if (addingCheat) {
                            focusManager.clearFocus()
                            addingCheat = false
                        } else if (!navController.popBackStack()) {
                            finish()
                        }
                    }
                    SubMenu(
                        route = GameMenuRoute.CHEATS,
                        navController = navController,
                        titleOverride = if (addingCheat) stringResource(R.string.cheats_add_title) else null,
                        onBack = goBack,
                    ) {
                        GameMenuCheatsScreen(
                            viewModel =
                                viewModel(
                                    factory = GameMenuCheatsViewModel.Factory(application, gameMenuRequest.game),
                                ),
                            addingCheat = addingCheat,
                            onOpenAddCheat = { addingCheat = true },
                            onCloseAddCheat = {
                                focusManager.clearFocus()
                                addingCheat = false
                            },
                        )
                    }
                }
                composable(GameMenuRoute.SAVE) {
                    SubMenu(GameMenuRoute.SAVE, navController, menuLook = true) {
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
                            title = stringResource(GameMenuRoute.SAVE.titleId),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_SAVE, it) }
                            },
                        )
                    }
                }
                composable(GameMenuRoute.LOAD) {
                    val loadViewModel: GameMenuStatesViewModel =
                        viewModel(
                            factory =
                                GameMenuStatesViewModel.Factory(
                                    application,
                                    gameMenuRequest,
                                    statesManager,
                                    true,
                                    statesPreviewManager,
                                ),
                        )
                    SubMenu(GameMenuRoute.LOAD, navController, menuLook = true) {
                        GameMenuStatesScreen(
                            loadViewModel,
                            title = stringResource(GameMenuRoute.LOAD.titleId),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_LOAD, it) }
                            },
                            onDeleteState = loadViewModel::deleteState,
                        )
                    }
                }
                composable(GameMenuRoute.ACHIEVEMENTS) {
                    var selectedAchievementId by rememberSaveable { mutableStateOf<Int?>(null) }
                    val goBack: () -> Unit = {
                        if (selectedAchievementId != null) {
                            selectedAchievementId = null
                        } else if (!navController.popBackStack()) {
                            finish()
                        }
                    }
                    SubMenu(
                        route = GameMenuRoute.ACHIEVEMENTS,
                        navController = navController,
                        titleOverride =
                            if (selectedAchievementId != null) {
                                stringResource(R.string.ra_detail_title)
                            } else {
                                null
                            },
                        onBack = goBack,
                    ) {
                        GameMenuAchievementsScreen(
                            selectedAchievementId = selectedAchievementId,
                            onSelectAchievement = { selectedAchievementId = it },
                            onSwitchHardcoreMode = { enable ->
                                onResult { putExtra(GameMenuContract.RESULT_SET_HARDCORE, enable) }
                            },
                        )
                    }
                }
                composable(GameMenuRoute.OPTIONS) {
                    var selectedSetting by
                        rememberSaveable(stateSaver = GameSettingSelection.Saver) {
                            mutableStateOf<GameSettingSelection?>(null)
                        }
                    val goBack: () -> Unit = {
                        if (selectedSetting != null) {
                            selectedSetting = null
                        } else if (!navController.popBackStack()) {
                            finish()
                        }
                    }
                    SubMenu(GameMenuRoute.OPTIONS, navController, menuLook = true, onBack = goBack) {
                        GameMenuCoreOptionsScreen(
                            viewModel(
                                factory = GameMenuCoreOptionsViewModel.Factory(inputDeviceManager),
                            ),
                            gameMenuRequest,
                            selectedSetting = selectedSetting,
                            onOpenSetting = { selectedSetting = it },
                            onSettingApplied = { selectedSetting = null },
                        )
                    }
                }
            }
        }
    }

    /**
     * A page opened from the pause menu, like Delta's: it takes the whole screen over the paused
     * game, with a Back pill at the bottom. Compact pages place their title beside their content.
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun SubMenu(
        route: GameMenuRoute,
        navController: NavController,
        // Back as a pill at the bottom, where the pause menu keeps Main Menu and Resume.
        // The speed page looks like the pause menu itself: no title bar, and the menu's own lighter background.
        menuLook: Boolean = false,
        showTitleBar: Boolean = !menuLook,
        titleOverride: String? = null,
        onBack: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) = androidx.compose.runtime.CompositionLocalProvider(
        // Without a Surface around it, text and icons would default to black on this dark page.
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        val goBack: () -> Unit =
            onBack ?: {
                if (!navController.popBackStack()) finish()
            }
        BackHandler(onBack = goBack)
        val compactForKeyboard =
            WindowInsets.isImeVisible && LocalConfiguration.current.screenHeightDp < 480
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Nearly solid, so lists stay readable over a busy game; a page that looks like the
                    // pause menu itself (menuLook) keeps the menu's own, lighter look.
                    .then(
                        if (menuLook) {
                            Modifier
                        } else {
                            Modifier.background(ArcaduneColors.Background.copy(alpha = 0.94f))
                        },
                    )
                    .safeDrawingPadding(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = if (compactForKeyboard) 8.dp else 76.dp),
            ) {
                if (showTitleBar && !compactForKeyboard) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = titleOverride ?: stringResource(route.titleId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            }
            if (!compactForKeyboard) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PillButton(
                        text = stringResource(R.string.back),
                        icon =
                            androidx.compose.ui.graphics.vector.rememberVectorPainter(
                                Icons.AutoMirrored.Filled.ArrowBack,
                            ),
                        highlighted = false,
                        onClick = goBack,
                    )
                }
            }
        }
    }

    private fun onResult(block: Intent.() -> Unit) {
        val resultIntent = Intent()
        resultIntent.block()
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    @dagger.Module
    abstract class Module
}
