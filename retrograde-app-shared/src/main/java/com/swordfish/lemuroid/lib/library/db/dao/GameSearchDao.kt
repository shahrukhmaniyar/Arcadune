package com.swordfish.lemuroid.lib.library.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.util.Locale

class GameSearchDao(private val internalDao: Internal) {
    object CALLBACK : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            MIGRATION.migrate(db)
        }
    }

    object MIGRATION : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE VIRTUAL TABLE fts_games USING FTS4(
                  tokenize=unicode61 "remove_diacritics=1",
                  content="games",
                  title);
                """,
            )
            database.execSQL(
                """
                CREATE TRIGGER games_bu BEFORE UPDATE ON games BEGIN
                  DELETE FROM fts_games WHERE docid=old.id;
                END;
                """,
            )
            database.execSQL(
                """
                CREATE TRIGGER games_bd BEFORE DELETE ON games BEGIN
                  DELETE FROM fts_games WHERE docid=old.id;
                END;
                """,
            )
            database.execSQL(
                """
                CREATE TRIGGER games_au AFTER UPDATE ON games BEGIN
                  INSERT INTO fts_games(docid, title) VALUES(new.id, COALESCE(new.customName, new.title));
                END;
                """,
            )
            database.execSQL(
                """
                CREATE TRIGGER games_ai AFTER INSERT ON games BEGIN
                  INSERT INTO fts_games(docid, title) VALUES(new.id, COALESCE(new.customName, new.title));
                END;
                """,
            )
            database.execSQL(
                """
                INSERT INTO fts_games(docid, title) SELECT id, COALESCE(customName, title) FROM games;
                """,
            )
        }
    }

    companion object {
        internal fun migrateFtsForVersion10(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TRIGGER IF EXISTS games_bu")
            database.execSQL("DROP TRIGGER IF EXISTS games_bd")
            database.execSQL("DROP TRIGGER IF EXISTS games_au")
            database.execSQL("DROP TRIGGER IF EXISTS games_ai")

            database.execSQL(
                """
                CREATE TRIGGER games_bu BEFORE UPDATE ON games BEGIN
                  DELETE FROM fts_games WHERE docid = old.id;
                END;
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TRIGGER games_bd BEFORE DELETE ON games BEGIN
                  DELETE FROM fts_games WHERE docid = old.id;
                END;
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TRIGGER games_au AFTER UPDATE ON games BEGIN
                  INSERT INTO fts_games(docid, title)
                  VALUES(new.id, COALESCE(new.customName, new.title));
                END;
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TRIGGER games_ai AFTER INSERT ON games BEGIN
                  INSERT INTO fts_games(docid, title)
                  VALUES(new.id, COALESCE(new.customName, new.title));
                END;
                """.trimIndent(),
            )

            database.execSQL("DELETE FROM fts_games")
            database.execSQL(
                """
                INSERT INTO fts_games(docid, title)
                SELECT id, COALESCE(customName, title) FROM games
                """.trimIndent(),
            )
        }

        private val FTS_TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")

        /**
         * Sanitizes arbitrary user input into safe FTS4 prefix tokens (`token*`).
         * Splitting on non-alphanumeric characters strips quotes, parentheses, hyphens, and colons,
         * and lowercasing prevents uppercase FTS4 boolean keywords (AND/OR/NOT/NEAR) from being
         * parsed as operators while preserving FTS4 prefix wildcard matching (`pok*`).
         */
        fun sanitizeFtsQuery(rawQuery: String): String =
            rawQuery
                .trim()
                .split(FTS_TOKEN_SPLIT)
                .filter { it.isNotEmpty() }
                .joinToString(" ") { "${it.lowercase(Locale.ROOT)}*" }
    }

    fun search(query: String): PagingSource<Int, Game> {
        val sanitized = sanitizeFtsQuery(query)
        if (sanitized.isEmpty()) {
            return internalDao.rawSearch(
                SimpleSQLiteQuery("SELECT * FROM games WHERE 0"),
            )
        }
        return internalDao.rawSearch(
            SimpleSQLiteQuery(
                """
                SELECT games.*
                    FROM fts_games
                    JOIN games ON games.id = fts_games.docid
                    WHERE fts_games MATCH ?
                    ORDER BY COALESCE(games.customName, games.title) COLLATE NOCASE ASC, games.id DESC
                """,
                arrayOf(sanitized),
            ),
        )
    }

    @Dao
    interface Internal {
        @RawQuery(observedEntities = [(Game::class)])
        fun rawSearch(query: SupportSQLiteQuery): PagingSource<Int, Game>
    }
}
