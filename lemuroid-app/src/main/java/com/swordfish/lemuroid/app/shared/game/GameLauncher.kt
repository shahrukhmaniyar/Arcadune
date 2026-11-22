package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import android.content.Context
import android.os.SystemClock
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
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
) {
    /**
     * Starts [game]. Returns false when the request is ignored because another launch is still
     * starting: two game screens in one game process would run two emulators at once, which the
     * emulator cores do not support (crash or damaged save).
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
        explicitQuickResume: Boolean = false,
    ): Boolean {
        if (!tryStartLaunch(SystemClock.elapsedRealtime())) {
            Timber.i("Ignoring launch of ${game.id}: another game is still starting")
            return false
        }

        GlobalScope.launch {
            try {
                // A game that was just closed may still be writing its save in the background (the
                // game window closes first). Give it a moment instead of refusing straight away.
                if (!waitForPreviousGameToFinish(activity.applicationContext)) {
                    onLaunchEnded()
                    withContext(Dispatchers.Main) {
                        activity.displayToast(R.string.game_process_another_game_running)
                    }
                    return@launch
                }

                val system = GameSystem.findById(game.systemId)
                val coreConfig = coresSelection.getCoreConfigForSystem(system)
                gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
                withContext(Dispatchers.Main) {
                    if (activity.isFinishing || activity.isDestroyed) {
                        onLaunchEnded()
                        return@withContext
                    }
                    BaseGameActivity.launchGame(
                        activity = activity,
                        systemCoreConfig = coreConfig,
                        game = game,
                        loadSave = loadSave,
                        useLeanback = leanback,
                        explicitQuickResume = explicitQuickResume,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Could not start ${game.id}")
                onLaunchEnded()
            }
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

    companion object {
        private const val WAIT_STEP_MS = 50L
        private const val MAX_WAIT_FOR_PREVIOUS_GAME_MS = 3000L

        /** How long a launch blocks the next one if the game never reports back (process killed...). */
        const val LAUNCH_GUARD_MS = 10_000L

        private val launchStartedAt = AtomicLong(0L)

        /** Claims the single launch slot at [nowMs]. False while another launch holds it. */
        internal fun tryStartLaunch(nowMs: Long): Boolean {
            while (true) {
                val since = launchStartedAt.get()
                if (since != 0L && nowMs - since < LAUNCH_GUARD_MS) return false
                if (launchStartedAt.compareAndSet(since, nowMs)) return true
            }
        }

        /** Frees the launch slot: the game screen closed, or the launch failed before it opened. */
        fun onLaunchEnded() {
            launchStartedAt.set(0L)
        }
    }
}
