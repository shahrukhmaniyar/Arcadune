package com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.cheats.Cheat
import com.swordfish.lemuroid.lib.cheats.CheatPolicy
import com.swordfish.lemuroid.lib.cheats.CheatsDownloader
import com.swordfish.lemuroid.lib.cheats.CheatsStore
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.io.IOException

class GameMenuCheatsViewModel(
    private val appContext: Context,
    private val game: Game,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val game: Game,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GameMenuCheatsViewModel(appContext, game) as T
    }

    data class UiState(
        val cheats: List<Cheat> = emptyList(),
        val downloading: Boolean = false,
        val message: String? = null,
    )

    private val store = CheatsStore(File(appContext.filesDir, "cheats"))
    private val downloader = CheatsDownloader(OkHttpClient(), appContext.cacheDir)
    private val key = CheatsStore.keyFor(game.systemId, game.fileName)

    private val mutableState = MutableStateFlow(UiState(cheats = store.load(key)))
    val state: StateFlow<UiState> = mutableState

    fun setEnabled(
        index: Int,
        enabled: Boolean,
    ) = changeCheats { cheats ->
        cheats.mapIndexed { i, cheat ->
            if (i == index && (!enabled || isSupported(cheat))) cheat.copy(enabled = enabled) else cheat
        }
    }

    /** False for cheats that are never sent to this game's emulator, see [unsupportedReason]. */
    fun isSupported(cheat: Cheat): Boolean = unsupportedReason(cheat) == null

    fun unsupportedReason(cheat: Cheat): Int? =
        when {
            CheatPolicy.systemIgnoresCheats(game.systemId) -> R.string.cheats_no_support
            CheatPolicy.isNotNeeded(cheat.description, game.systemId) -> R.string.cheats_not_needed
            !CheatPolicy.isSupported(cheat.code, game.systemId) -> R.string.cheats_too_long
            else -> null
        }

    fun addCheat(
        description: String,
        code: String,
    ) {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return
        if (!CheatPolicy.isSupported(cleanCode, game.systemId)) {
            val message =
                if (CheatPolicy.systemIgnoresCheats(game.systemId)) {
                    R.string.cheats_no_support
                } else {
                    R.string.cheats_code_too_long
                }
            mutableState.update { it.copy(message = appContext.getString(message)) }
            return
        }
        val cleanDescription = description.trim().ifEmpty { cleanCode }
        changeCheats { it + Cheat(cleanDescription, cleanCode, enabled = true) }
    }

    fun deleteCheat(index: Int) = changeCheats { cheats -> cheats.filterIndexed { i, _ -> i != index } }

    fun findCheatsOnline() {
        if (mutableState.value.downloading) return
        if (CheatPolicy.systemIgnoresCheats(game.systemId)) {
            mutableState.update { it.copy(message = appContext.getString(R.string.cheats_no_support)) }
            return
        }
        mutableState.update { it.copy(downloading = true, message = null) }

        viewModelScope.launch {
            val message =
                try {
                    val titles = listOf(game.title, game.displayName()).distinct()
                    val result = downloader.download(systemFolder(), titles)
                    val added = addMissing(result.cheats)
                    appContext.getString(R.string.cheats_found, added)
                } catch (e: CheatsDownloader.NotFoundException) {
                    appContext.getString(R.string.cheats_not_found)
                } catch (e: IOException) {
                    Timber.e(e, "Cheat download failed")
                    appContext.getString(R.string.cheats_download_failed, e.message ?: e.javaClass.simpleName)
                } catch (e: Exception) {
                    Timber.e(e, "Cheat download failed")
                    appContext.getString(R.string.cheats_download_failed, e.message ?: e.javaClass.simpleName)
                }
            mutableState.update { it.copy(downloading = false, message = message) }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /** Adds downloaded cheats that the game does not have yet, keeping the user's own and switched-on ones. */
    private fun addMissing(downloaded: List<Cheat>): Int {
        var added = 0
        changeCheats { current ->
            val known = current.map { it.code }.toSet()
            val fresh = downloaded.filter { it.code !in known }
            added = fresh.size
            current + fresh
        }
        return added
    }

    private fun changeCheats(transform: (List<Cheat>) -> List<Cheat>) {
        val updated = transform(mutableState.value.cheats)
        mutableState.update { it.copy(cheats = updated) }
        // Saved right away (it is a few KB): the game reads this file as soon as the menu closes, and a
        // background save could be lost if the screen is left first.
        runCatching { store.save(key, updated) }.onFailure { Timber.e(it, "Could not save the cheats") }
    }

    private fun systemFolder(): String {
        return GameSystem.findById(game.systemId).libretroFullName
    }
}
