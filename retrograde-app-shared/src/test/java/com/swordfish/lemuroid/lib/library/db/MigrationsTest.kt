package com.swordfish.lemuroid.lib.library.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.swordfish.lemuroid.lib.library.db.dao.Migrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a real database from every older version to the current one with the app's migrations,
 * and checks that the result matches the current schema and keeps the player's data. The app no
 * longer wipes the library when an upgrade path is missing, so this test is what guards it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationsTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RetrogradeDatabase::class.java,
        )

    @Test
    fun everyVersionFrom8UpgradesToTheCurrentOneKeepingGames() {
        for (startVersion in 8 until CURRENT_VERSION) {
            val name = "migration-from-$startVersion.db"
            ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(name)

            helper.createDatabase(name, startVersion).apply {
                // Real databases also have the search table, created outside Room's schema.
                execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS fts_games USING FTS4(" +
                        "tokenize=unicode61 \"remove_diacritics=1\", content=\"games\", title)",
                )
                execSQL(
                    "INSERT INTO games (id, fileName, fileUri, title, systemId, developer, coverFrontUrl, " +
                        "lastIndexedAt, lastPlayedAt, isFavorite) VALUES " +
                        "(1, 'Sonic.md', 'content://roms/Sonic.md', 'Sonic', 'md', NULL, NULL, 10, 20, 1)",
                )
                close()
            }

            // validateDroppedTables = false: the search table lives outside Room's schema on purpose.
            val migrated = helper.runMigrationsAndValidate(name, CURRENT_VERSION, false, *Migrations.ALL)
            migrated.query("SELECT fileName, isFavorite, lastPlayedAt FROM games WHERE id = 1").use { cursor ->
                assertEquals("from version $startVersion", true, cursor.moveToFirst())
                assertEquals("Sonic.md", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(20L, cursor.getLong(2))
            }
            migrated.close()
        }
    }

    private companion object {
        const val CURRENT_VERSION = 13
    }
}
