package com.swordfish.lemuroid.lib.library.db.dao

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val VERSION_8_9: Migration =
        object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `datafiles`(
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gameId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `lastIndexedAt` INTEGER NOT NULL,
                        `path` TEXT, FOREIGN KEY(`gameId`
                    ) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_datafiles_id` ON `datafiles` (`id`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_fileUri` ON `datafiles` (`fileUri`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_gameId` ON `datafiles` (`gameId`)
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_datafiles_lastIndexedAt` ON `datafiles` (`lastIndexedAt`)
                    """.trimIndent(),
                )
            }
        }

    val VERSION_9_10: Migration =
        object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                migrateGamesTableToVersion10(database)
                GameSearchDao.migrateFtsForVersion10(database)
            }
        }

    /** Removes the collections that version 11 added: the feature was dropped before any release. */
    val VERSION_11_12: Migration =
        object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `collection_games`")
                database.execSQL("DROP TABLE IF EXISTS `collections`")
            }
        }

    /** Play counts (for sorting by most played) and the player's own collections of games. */
    val VERSION_10_11: Migration =
        object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE games ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection_games` (`collectionId` INTEGER NOT NULL, " +
                        "`gameId` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `gameId`), " +
                        "FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_games_gameId` ON `collection_games` (`gameId`)",
                )
            }
        }

    private fun migrateGamesTableToVersion10(
        database: SupportSQLiteDatabase,
    ) {
        database.execSQL(
            "ALTER TABLE games ADD COLUMN customName TEXT DEFAULT NULL",
        )
        database.execSQL(
            "ALTER TABLE games ADD COLUMN customCoverUri TEXT DEFAULT NULL",
        )
    }
}
