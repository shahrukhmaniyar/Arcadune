package com.swordfish.lemuroid.app.mobile.feature.covermanager

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the games whose cover does not load. Every cover is loaded once through the app's image
 * loader (the same way the library loads it, so covers already seen come from its cache), and the
 * list follows the library: a game whose cover is fixed leaves it.
 */
class CoverManagerViewModel(
    private val appContext: Context,
    private val retrogradeDb: RetrogradeDatabase,
) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val retrogradeDb: RetrogradeDatabase,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CoverManagerViewModel(appContext, retrogradeDb) as T
        }
    }

    data class UiState(
        val checked: Int = 0,
        val total: Int = 0,
        val missing: List<Game> = emptyList(),
    ) {
        val done: Boolean get() = total > 0 && checked >= total
    }

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState

    // Cover address to "it loads", so a cover is checked only once while the screen is open.
    private val results = ConcurrentHashMap<String, Boolean>()
    private val permits = Semaphore(4)

    init {
        viewModelScope.launch {
            retrogradeDb.gameDao().selectAllFlow().collectLatest { games -> check(games) }
        }
    }

    private suspend fun check(games: List<Game>) {
        mutableState.update { it.copy(total = games.size, checked = games.count { isKnown(it) }) }
        publish(games)

        val unknown = games.filterNot { isKnown(it) }
        unknown.chunked(CHUNK).forEach { chunk ->
            chunk.map { game ->
                viewModelScope.async(Dispatchers.IO) {
                    permits.withPermit {
                        val url = game.displayCoverUrl()
                        if (url != null) results[url] = loads(url)
                    }
                }
            }.awaitAll()
            mutableState.update { it.copy(checked = games.count { game -> isKnown(game) }) }
            publish(games)
        }
    }

    private fun isKnown(game: Game): Boolean = game.displayCoverUrl()?.let { results.containsKey(it) } ?: true

    private fun publish(games: List<Game>) {
        val missing = games.filter { game -> game.displayCoverUrl()?.let { results[it] == false } ?: true }
        mutableState.update { it.copy(missing = missing) }
    }

    private suspend fun loads(url: String): Boolean {
        val request =
            ImageRequest.Builder(appContext)
                .data(url)
                .size(COVER_CHECK_SIZE)
                .build()
        return appContext.imageLoader.execute(request) is SuccessResult
    }

    companion object {
        private const val CHUNK = 12
        private const val COVER_CHECK_SIZE = 96
    }
}
