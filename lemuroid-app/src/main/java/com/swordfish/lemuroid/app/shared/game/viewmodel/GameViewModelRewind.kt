package com.swordfish.lemuroid.app.shared.game.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Rewind: while the game runs, a save state is taken every [SNAPSHOT_INTERVAL_MS] and kept in
 * memory (up to [MAX_SNAPSHOTS] or [MAX_BYTES]). Holding the rewind button loads them back, newest
 * first, one every [REWIND_STEP_MS], so the game runs backwards at a few times its speed.
 *
 * The emulator library has no rewind of its own, so this is built on save states. It is only
 * offered on consoles whose states are small enough to take often without slowing the game.
 */
class GameViewModelRewind(
    private val system: GameSystem,
    private val settingsManager: SettingsManager,
    private val retroGameView: GameViewModelRetroGameView,
    private val scope: CoroutineScope,
    private val isHardcoreActive: () -> Boolean = { false },
) : DefaultLifecycleObserver {
    private val snapshots = ArrayDeque<ByteArray>()
    private var snapshotsBytes = 0L
    private val lock = Any()

    private val mutableAvailable = MutableStateFlow(false)

    /** True when the rewind button should be shown. */
    val available: StateFlow<Boolean> = mutableAvailable

    @Volatile
    private var rewinding = false
    private var rewindJob: Job? = null

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        if (system.id !in SUPPORTED_SYSTEMS) return

        // Only while the game is in front: no snapshots while the game menu is open.
        owner.launchOnState(Lifecycle.State.RESUMED) {
            if (isHardcoreActive() || !settingsManager.rewindEnabled()) {
                mutableAvailable.value = false
                return@launchOnState
            }
            retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
            if (isHardcoreActive()) {
                mutableAvailable.value = false
                return@launchOnState
            }
            mutableAvailable.value = true
            takeSnapshots()
        }
    }

    private suspend fun takeSnapshots() {
        while (currentCoroutineContextIsActive()) {
            delay(SNAPSHOT_INTERVAL_MS)
            if (isHardcoreActive()) {
                clearAndDisable()
                return
            }
            if (rewinding) continue
            val view = retroGameView.retroGameView ?: continue
            val state =
                runCatching { withContext(Dispatchers.IO) { view.serializeState() } }
                    .onFailure { Timber.e(it, "Rewind snapshot failed") }
                    .getOrNull() ?: continue
            if (isHardcoreActive()) {
                clearAndDisable()
                return
            }
            push(state)
        }
    }

    private suspend fun currentCoroutineContextIsActive() = kotlin.coroutines.coroutineContext.isActive

    private fun push(state: ByteArray) {
        synchronized(lock) {
            snapshots.addLast(state)
            snapshotsBytes += state.size
            while (snapshots.size > MAX_SNAPSHOTS || (snapshotsBytes > MAX_BYTES && snapshots.size > 1)) {
                snapshotsBytes -= snapshots.removeFirst().size
            }
        }
    }

    private fun pop(): ByteArray? =
        synchronized(lock) {
            snapshots.removeLastOrNull()?.also { snapshotsBytes -= it.size }
        }

    /** Called when the rewind button is pressed (true) or released (false). */
    fun onRewindPressed(pressed: Boolean) {
        if (isHardcoreActive()) {
            clearAndDisable()
            return
        }
        if (!mutableAvailable.value) return
        if (pressed) start() else stop()
    }

    /** Immediately halts any active rewind and purges the entire rewind state history. */
    fun clearAndDisable() {
        stop()
        mutableAvailable.value = false
        synchronized(lock) {
            snapshots.clear()
            snapshotsBytes = 0L
        }
    }

    private fun start() {
        if (rewinding || isHardcoreActive()) return
        rewinding = true
        rewindJob =
            scope.launch(Dispatchers.IO) {
                while (rewinding && isActive && !isHardcoreActive()) {
                    val state = pop() ?: break
                    val view = retroGameView.retroGameView ?: break
                    runCatching { view.unserializeState(state) }.onFailure { Timber.e(it, "Rewind step failed") }
                    // Keep the oldest state: holding the button at the end of the buffer stays there.
                    if (synchronized(lock) { snapshots.isEmpty() }) push(state)
                    delay(REWIND_STEP_MS)
                }
            }
    }

    private fun stop() {
        rewinding = false
        rewindJob?.cancel()
        rewindJob = null
    }

    companion object {
        private const val SNAPSHOT_INTERVAL_MS = 500L
        private const val REWIND_STEP_MS = 120L
        private const val MAX_SNAPSHOTS = 60
        private const val MAX_BYTES = 48L * 1024 * 1024

        // Consoles with small save states (well under 1 MB each).
        private val SUPPORTED_SYSTEMS =
            setOf(
                SystemID.NES,
                SystemID.SNES,
                SystemID.GB,
                SystemID.GBC,
                SystemID.GBA,
                SystemID.GENESIS,
                SystemID.SMS,
                SystemID.GG,
                SystemID.PC_ENGINE,
                SystemID.ATARI2600,
                SystemID.ATARI7800,
                SystemID.LYNX,
                SystemID.NGP,
                SystemID.NGC,
                SystemID.WS,
                SystemID.WSC,
                SystemID.SEGA32X,
                SystemID.ATARI5200,
                SystemID.COLECOVISION,
                SystemID.MSX,
            )
    }
}
