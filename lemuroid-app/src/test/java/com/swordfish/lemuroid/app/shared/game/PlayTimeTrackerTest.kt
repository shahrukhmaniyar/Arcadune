package com.swordfish.lemuroid.app.shared.game

import com.swordfish.lemuroid.lib.library.db.dao.GameListQuery
import com.swordfish.lemuroid.lib.library.db.dao.GameSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayTimeTrackerTest {
    @Test
    fun `formatDurationShort formats seconds minutes and hours cleanly`() {
        assertEquals("1s", PlayTimeTracker.formatDurationShort(100L))
        assertEquals("1s", PlayTimeTracker.formatDurationShort(1_000L))
        assertEquals("42s", PlayTimeTracker.formatDurationShort(42_900L))
        assertEquals("1m", PlayTimeTracker.formatDurationShort(60_000L))
        assertEquals("59m", PlayTimeTracker.formatDurationShort(59 * 60_000L + 59_000L))
        assertEquals("1h", PlayTimeTracker.formatDurationShort(3_600_000L))
        assertEquals("2h 15m", PlayTimeTracker.formatDurationShort(2 * 3_600_000L + 15 * 60_000L + 20_000L))
        assertEquals("104h 5m", PlayTimeTracker.formatDurationShort(104 * 3_600_000L + 5 * 60_000L))
    }

    @Test
    fun `journal serialization roundtrips session record and rejects malformed text`() {
        val raw =
            PlayTimeTracker.serializeJournalRecord(
                gameId = 17,
                playedAtMs = 1_700_000_000_000L,
                playCountDelta = 1,
                playTimeDeltaMs = 95_400L,
            )
        val parsed = PlayTimeTracker.parseJournalText(raw)
        assertNotNull(parsed)
        assertEquals(17, parsed!!.gameId)
        assertEquals(1_700_000_000_000L, parsed.playedAtMs)
        assertEquals(1, parsed.playCountDelta)
        assertEquals(95_400L, parsed.playTimeDeltaMs)

        assertNull(PlayTimeTracker.parseJournalText(""))
        assertNull(PlayTimeTracker.parseJournalText("17|abc|1|500"))
        assertNull(PlayTimeTracker.parseJournalText("17|1000|1"))
    }

    @Test
    fun `MOST_PLAYED sort order prioritizes playTimeMs before playCount`() {
        val sql = GameListQuery.bySystems(listOf("gba"), GameSortOrder.MOST_PLAYED).sql
        val playTimeIdx = sql.indexOf("games.playTimeMs DESC")
        val playCountIdx = sql.indexOf("games.playCount DESC")
        assertTrue("Expected games.playTimeMs DESC in query: $sql", playTimeIdx >= 0)
        assertTrue("Expected playTimeMs before playCount in query: $sql", playTimeIdx < playCountIdx)
    }
}
