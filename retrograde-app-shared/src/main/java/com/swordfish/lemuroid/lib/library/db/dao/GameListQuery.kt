package com.swordfish.lemuroid.lib.library.db.dao

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/** The orders the player can pick for a list of games. */
enum class GameSortOrder {
    NAME,
    RECENTLY_PLAYED,
    MOST_PLAYED,
    FAVORITES_FIRST,
    ;

    /** SQL ORDER BY clause. Ties always fall back to the name, so the order is stable. */
    internal fun orderBy(): String {
        val byName = "COALESCE(games.customName, games.title) COLLATE NOCASE ASC, games.id DESC"
        return when (this) {
            NAME -> byName
            RECENTLY_PLAYED -> "games.lastPlayedAt IS NULL, games.lastPlayedAt DESC, $byName"
            MOST_PLAYED -> "games.playCount DESC, games.lastPlayedAt DESC, $byName"
            FAVORITES_FIRST -> "games.isFavorite DESC, $byName"
        }
    }

    companion object {
        fun parse(value: String?): GameSortOrder = entries.firstOrNull { it.name == value } ?: NAME
    }
}

/** Builds the queries for [GameDao.selectPaged]. Only fixed SQL and bound arguments are used. */
object GameListQuery {
    fun bySystems(
        systemIds: List<String>,
        order: GameSortOrder,
    ): SupportSQLiteQuery {
        val placeholders = systemIds.joinToString(",") { "?" }
        return SimpleSQLiteQuery(
            "SELECT games.* FROM games WHERE games.systemId IN ($placeholders) ORDER BY ${order.orderBy()}",
            systemIds.toTypedArray(),
        )
    }
}
