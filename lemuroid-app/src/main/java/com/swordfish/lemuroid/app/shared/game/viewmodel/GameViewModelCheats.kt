package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.content.Context
import android.widget.Toast
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.cheats.CheatApplier
import com.swordfish.lemuroid.lib.cheats.CheatTarget
import com.swordfish.lemuroid.lib.cheats.CheatsStore
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Keeps the cheats of the running game in line with the list the player keeps in the game menu.
 *
 * Every apply clears the emulator's cheats and adds only the switched-on ones (see [CheatApplier]).
 *
 * The emulator calls can wait for the emulation thread, so they never run on the screen's main
 * thread: a wait there would freeze the whole app. Applies are also run one at a time.
 *
 * Two protections keep a bad code from locking the player out of a game: codes a core cannot take
 * are never sent, and if the game died while cheats were being applied the cheats of that game are
 * switched off the next time it starts.
 */
class GameViewModelCheats(
    private val appContext: Context,
    private val game: Game,
    private val retroGameView: GameViewModelRetroGameView,
    private val scope: CoroutineScope,
) {
    private val store = CheatsStore(File(appContext.filesDir, "cheats"))
    private val key = CheatsStore.keyFor(game.systemId, game.fileName)
    private val applyLock = Any()

    /** Applies the cheats once the game is running and drawing frames. */
    fun applyWhenReady() {
        store.switchOffAfterCrash()?.let {
            Timber.w("Cheats of $it were switched off: the game stopped while they were on")
            Toast.makeText(appContext, R.string.cheats_switched_off_after_crash, Toast.LENGTH_LONG).show()
        }

        scope.launch(Dispatchers.Default) {
            runCatching { retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>() }
            applyNow()
        }
    }

    /** Call when the game is closed normally, so its cheats are not switched off at the next start. */
    fun gameClosed() {
        runCatching { store.endSession() }
    }

    /** Applies the cheats in the background. */
    fun apply() {
        scope.launch(Dispatchers.Default) { applyNow() }
    }

    private fun applyNow() =
        synchronized(applyLock) {
            val view = retroGameView.retroGameView ?: return@synchronized
            val cheats = store.load(key)
            val previouslyApplied = store.loadApplied(key)
            val applier = CheatApplier(target(view), game.systemId)

            // Nothing here may throw: this runs in a background coroutine, where an exception ends the app.
            val active =
                runCatching {
                    store.startApplying(key)
                    try {
                        applier.apply(cheats, previouslyApplied)
                    } finally {
                        store.finishApplying()
                    }
                }.onFailure { Timber.e(it, "Could not apply the cheats") }.getOrNull()

            if (active != null) runCatching { store.saveApplied(key, applier.activeCheats(cheats)) }

            // While cheats are on, a game that dies without being closed normally gets them switched off.
            runCatching { if (active != null && active > 0) store.startSession(key) else store.endSession() }

            if (active == null) {
                Timber.w("The emulator's cheats could not be cleared, no cheat was changed")
            } else {
                Timber.i("Cheats applied: $active of ${cheats.size} are on")
            }
        }

    private fun target(view: GLRetroView) =
        object : CheatTarget {
            override fun clear(): Boolean =
                runCatching {
                    // The library exposes this only as a native method, and it is safe to call at any
                    // time: the native side takes the same lock as the emulation loop.
                    Class.forName("com.swordfish.libretrodroid.LibretroDroid")
                        .getMethod("resetCheat")
                        .invoke(null)
                }.onFailure { Timber.e(it, "Could not clear the cheats") }.isSuccess

            override fun enable(
                index: Int,
                code: String,
            ) = view.setCheat(index, true, code, false)
        }
}
