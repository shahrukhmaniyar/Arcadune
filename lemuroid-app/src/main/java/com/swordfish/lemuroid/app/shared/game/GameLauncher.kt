package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import android.content.Context
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
        explicitQuickResume: Boolean = false,
    ): Boolean {
        GlobalScope.launch {
            // A game that was just closed may still be writing its save in the background (the
            // game window closes first). Give it a moment instead of refusing straight away.
            if (!waitForPreviousGameToFinish(activity.applicationContext)) {
                withContext(Dispatchers.Main) {
                    activity.displayToast(R.string.game_process_another_game_running)
                }
                return@launch
            }

            val system = GameSystem.findById(game.systemId)
            val coreConfig = coresSelection.getCoreConfigForSystem(system)
            gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
            BaseGameActivity.launchGame(
                activity = activity,
                systemCoreConfig = coreConfig,
                game = game,
                loadSave = loadSave,
                useLeanback = leanback,
                explicitQuickResume = explicitQuickResume,
            )
        }

        return true
    }

    /** True as soon as no other game process is running; false if one is still there after a few seconds. */
    private suspend fun waitForPreviousGameToFinish(context: Context): Boolean {
        var waitedMs = 0L
        while (GameProcessLock.isHeldByAnotherProcess(context)) {
            if (waitedMs >= MAX_WAIT_FOR_PREVIOUS_GAME_MS) return false
            delay(WAIT_STEP_MS)
            waitedMs += WAIT_STEP_MS
        }
        return true
    }

    private companion object {
        const val WAIT_STEP_MS = 50L
        const val MAX_WAIT_FOR_PREVIOUS_GAME_MS = 3000L
    }
}
