package com.swordfish.lemuroid.app.mobile.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo
import com.swordfish.lemuroid.common.paging.buildFlowPaging
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.dao.GameListQuery
import com.swordfish.lemuroid.lib.library.db.dao.GameSortOrder
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metaSystemID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

/**
 * The game library as one row of pages the player swipes through, like Delta: Favorites, Recently
 * Played, then one page per console. Search covers the whole library.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    private val retrogradeDb: RetrogradeDatabase,
    private val appContext: Context,
) : ViewModel() {
    class Factory(
        private val retrogradeDb: RetrogradeDatabase,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(retrogradeDb, appContext) as T
        }
    }

    sealed interface Page {
        val id: String

        data object Favorites : Page {
            override val id = "favorites"
        }

        data object RecentlyPlayed : Page {
            override val id = "recent"
        }

        data class System(val info: MetaSystemInfo) : Page {
            override val id = "system_${info.metaSystem.name}"
        }
    }

    private val systems: Flow<List<MetaSystemInfo>> =
        retrogradeDb.gameDao()
            .selectSystemsWithCount()
            .map { systemCounts ->
                systemCounts
                    .filter { it.count > 0 }
                    .map { GameSystem.findById(it.systemId).metaSystemID() to it.count }
                    .groupBy({ it.first }, { it.second })
                    .map { (metaSystem, counts) -> MetaSystemInfo(metaSystem, counts.sum()) }
                    .sortedBy { it.getName(appContext) }
            }

    /** Null until the library has been read once. */
    val pages: StateFlow<List<Page>?> =
        combine(
            systems,
            retrogradeDb.gameDao().countRecentlyPlayed(),
        ) { systems, recentCount ->
            buildList {
                if (systems.isNotEmpty()) add(Page.Favorites)
                if (recentCount > 0) add(Page.RecentlyPlayed)
                systems.forEach { add(Page.System(it)) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pagedGames = mutableMapOf<Pair<String, GameSortOrder>, Flow<PagingData<Game>>>()

    /** The games of a page. Console pages follow [order]; Favorites and Recently Played have their own. */
    fun gamesOf(
        page: Page,
        order: GameSortOrder,
    ): Flow<PagingData<Game>> {
        val effectiveOrder = if (page is Page.System) order else GameSortOrder.NAME
        return pagedGames.getOrPut(page.id to effectiveOrder) {
            when (page) {
                Page.Favorites -> buildFlowPaging(PAGE_SIZE, viewModelScope) { retrogradeDb.gameDao().selectFavorites() }
                Page.RecentlyPlayed ->
                    buildFlowPaging(PAGE_SIZE, viewModelScope) { retrogradeDb.gameDao().selectRecentlyPlayed() }
                is Page.System -> {
                    val query = GameListQuery.bySystems(page.info.metaSystem.systemIDs.map { it.dbname }, effectiveOrder)
                    buildFlowPaging(PAGE_SIZE, viewModelScope) { retrogradeDb.gameDao().selectPaged(query) }
                }
            }
        }
    }

    val searchQuery = MutableStateFlow("")

    val searchResults: Flow<PagingData<Game>> =
        searchQuery
            .debounce(250.milliseconds)
            .flatMapLatest { query ->
                buildFlowPaging(PAGE_SIZE, viewModelScope) { retrogradeDb.gameSearchDao().search(query.trim()) }
            }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
