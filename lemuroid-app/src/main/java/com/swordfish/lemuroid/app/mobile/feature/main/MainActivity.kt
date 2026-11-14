package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.app.mobile.feature.covermanager.CoverManagerScreen
import com.swordfish.lemuroid.app.mobile.feature.covermanager.CoverManagerViewModel
import com.swordfish.lemuroid.app.mobile.feature.home.HomeNotices
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.library.LibraryScreen
import com.swordfish.lemuroid.app.mobile.feature.library.LibraryViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.achievements.RetroAchievementsSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.achievements.RetroAchievementsSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.saves.SavesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.saves.SavesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneMotion
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.ext.feature.review.ReviewManager
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Provides
import javax.inject.Inject

class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var directoriesManager: DirectoriesManager

    @Inject
    lateinit var savesManager: SavesManager

    private val reviewManager = ReviewManager()

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        lifecycleScope.safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        setContent {
            val navController = rememberNavController()
            MainScreen(navController)
        }
    }

    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val currentRoute =
                currentDestination?.route
                    ?.let { MainRoute.findByRoute(it) }
                    ?: MainRoute.HOME

            val infoDialogDisplayed =
                remember {
                    mutableStateOf(false)
                }

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }

            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val onGameClick = { game: Game ->
                gameInteractor.onGamePlay(game)
            }

            val onHelpPressed = {
                infoDialogDisplayed.value = true
            }

            val mainUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value

            val legacyFlowPreferences =
                remember {
                    FlowSharedPreferences(
                        SharedPreferencesHelper.getLegacySharedPreferences(applicationContext),
                    )
                }

            // Each Settings / utility route hosts its own MainTopBar inside its composable sheet,
            // so headers and content glide together without top-bar jumps or padding mutations.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    startDestination = MainRoute.HOME.route,
                    enterTransition = { ArcaduneMotion.pushEnter },
                    exitTransition = { ArcaduneMotion.pushExit },
                    popEnterTransition = { ArcaduneMotion.popEnter },
                    popExitTransition = { ArcaduneMotion.popExit },
                ) {
                    composable(MainRoute.HOME) {
                        val homeViewModel: HomeViewModel =
                            viewModel(factory = HomeViewModel.Factory(applicationContext, retrogradeDb, coresSelection))
                        LibraryScreen(
                            viewModel = viewModel(factory = LibraryViewModel.Factory(retrogradeDb, applicationContext)),
                            indexingInProgress = mainUIState.operationInProgress,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onOpenSettings = { navController.navigateToRoute(MainRoute.SETTINGS) },
                            notices = {
                                HomeNotices(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    viewModel = homeViewModel,
                                )
                            },
                        )
                    }
                    composable(
                        route = MainRoute.COVER_MANAGER,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        CoverManagerScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = viewModel(factory = CoverManagerViewModel.Factory(application, retrogradeDb)),
                            onGameClick = onGameLongClick,
                        )
                    }
                    composable(
                        route = MainRoute.SETTINGS,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel =
                                viewModel(
                                    factory =
                                        SettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                            saveSyncManager,
                                            legacyFlowPreferences,
                                            biosManager,
                                            directoriesManager,
                                            savesManager,
                                            retrogradeDb,
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(
                        route = MainRoute.SETTINGS_ADVANCED,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        AdvancedSettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel =
                                viewModel(
                                    factory =
                                        AdvancedSettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(
                        route = MainRoute.SETTINGS_SKINS,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        com.swordfish.lemuroid.app.mobile.feature.settings.skins.SkinGalleryScreen(
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_SKIN_LAYOUT) { entry ->
                        com.swordfish.lemuroid.app.mobile.feature.settings.skins.SkinLayoutEditorScreen(
                            modifier = Modifier.fillMaxSize(),
                            controller = entry.arguments?.getString("controller") ?: "",
                            initialLandscape = entry.arguments?.getBoolean("landscape") ?: false,
                            onDone = {
                                if (navController.previousBackStackEntry != null) {
                                    navController.popBackStack()
                                }
                            },
                        )
                    }
                    composable(
                        route = MainRoute.SETTINGS_BIOS,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        BiosScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel =
                                viewModel(
                                    factory = BiosSettingsViewModel.Factory(biosManager),
                                ),
                        )
                    }
                    composable(
                        route = MainRoute.SETTINGS_INPUT_DEVICES,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        InputDevicesSettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel =
                                viewModel(
                                    factory =
                                        InputDevicesSettingsViewModel.Factory(
                                            applicationContext,
                                            inputDeviceManager,
                                        ),
                                ),
                        )
                    }
                    composable(
                        route = MainRoute.SETTINGS_SAVE_SYNC,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        SaveSyncSettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel =
                                viewModel(
                                    factory =
                                        SaveSyncSettingsViewModel.Factory(
                                            application,
                                            saveSyncManager,
                                        ),
                                ),
                        )
                    }
                    composable(
                        route = MainRoute.SETTINGS_SAVES,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        SavesSettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel =
                                viewModel(
                                    factory =
                                        SavesSettingsViewModel.Factory(
                                            applicationContext,
                                            directoriesManager,
                                            savesManager,
                                            retrogradeDb,
                                        ),
                                ),
                        )
                    }
                    composable(
                        route = MainRoute.SETTINGS_RETRO_ACHIEVEMENTS,
                        navController = navController,
                        onHelpPressed = onHelpPressed,
                        mainUIState = mainUIState,
                    ) {
                        RetroAchievementsSettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel =
                                viewModel(
                                    factory = RetroAchievementsSettingsViewModel.Factory(applicationContext),
                                ),
                        )
                    }
                }
            }

            MainGameContextActions(
                selectedGameState = selectedGameState,
                shortcutSupported = gameInteractor.supportShortcuts(),
                onGamePlay = { gameInteractor.onGamePlay(it) },
                onGameRestart = { gameInteractor.onGameRestart(it) },
                onFavoriteToggle = { game: Game, isFavorite: Boolean ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
                onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
                onSetCustomName = { game, name -> gameInteractor.onSetCustomName(game, name) },
                onClearCustomName = { game -> gameInteractor.onClearCustomName(game) },
                onSetCustomCoverUri = { game, uri -> gameInteractor.onSetCustomCoverUri(game, uri) },
                onClearCustomCoverUri = { game -> gameInteractor.onClearCustomCoverUri(game) },
            )

            if (infoDialogDisplayed.value) {
                AboutDialog(onDismiss = { infoDialogDisplayed.value = false })
            }
        }
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                lifecycleScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
                        true,
                        this@MainActivity,
                        resultCode,
                        data,
                    )
                }
            }
        }
    }

    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                customCoverManager: CustomCoverManager,
            ) = GameInteractor(activity, retrogradeDb, false, shortcutsGenerator, gameLauncher, customCoverManager)
        }
    }
}
