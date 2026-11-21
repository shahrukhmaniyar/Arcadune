package com.swordfish.lemuroid.app.shared.game

import android.content.Context
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.common.kotlin.readTextAtomic
import com.swordfish.lemuroid.common.kotlin.writeTextAtomic
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object PlayTimeTracker {
    private const val JOURNAL_DIR_NAME = "playtime_journal"
    private const val JOURNAL_SUFFIX = ".session"
    private const val MS_PER_SECOND = 1_000L
    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_HOUR = 3_600_000L

    private val journalWriteLock = Any()
    private val dbFlushMutex = Mutex()

    data class PendingSessionRecord(
        val gameId: Int,
        val playedAtMs: Long,
        val playCountDelta: Int,
        val playTimeDeltaMs: Long,
    )

    fun appendPendingJournal(
        context: Context,
        sessionId: String,
        gameId: Int,
        playedAtMs: Long,
        playCountDelta: Int,
        playTimeDeltaMs: Long,
    ) {
        if (gameId <= 0 || (playCountDelta <= 0 && playTimeDeltaMs <= 0L)) {
            return
        }
        synchronized(journalWriteLock) {
            runCatching {
                val dir = File(context.filesDir, JOURNAL_DIR_NAME).apply { mkdirs() }
                val safeId = sessionId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                val journalFile = File(dir, "$safeId$JOURNAL_SUFFIX")
                val existing =
                    if (journalFile.isFile) {
                        runCatching { parseJournalText(journalFile.readTextAtomic()) }.getOrNull()
                    } else {
                        null
                    }
                val mergedPlayedAt = maxOf(existing?.playedAtMs ?: 0L, playedAtMs)
                val mergedCount = (existing?.playCountDelta ?: 0) + playCountDelta.coerceAtLeast(0)
                val mergedTimeMs = (existing?.playTimeDeltaMs ?: 0L) + playTimeDeltaMs.coerceAtLeast(0L)
                val serialized = serializeJournalRecord(gameId, mergedPlayedAt, mergedCount, mergedTimeMs)
                journalFile.writeTextAtomic(serialized)
            }.onFailure { error ->
                Timber.e(error, "Failed to persist playtime journal for gameId=$gameId")
            }
        }
    }

    suspend fun flushPendingToDatabase(
        context: Context,
        retrogradeDb: RetrogradeDatabase,
    ): Set<Int> {
        return dbFlushMutex.withLock {
            withContext(Dispatchers.IO) {
                val flushedGameIds = mutableSetOf<Int>()
                val dir = File(context.filesDir, JOURNAL_DIR_NAME)
                val files =
                    dir.listFiles { file ->
                        file.isFile && file.name.endsWith(JOURNAL_SUFFIX)
                    } ?: return@withContext emptySet()

                for (journalFile in files) {
                    runCatching {
                        val record = parseJournalText(journalFile.readTextAtomic())
                        if (record != null && record.gameId > 0) {
                            retrogradeDb.gameDao().recordPlaySession(
                                id = record.gameId,
                                playedAt = record.playedAtMs,
                                playCountDelta = record.playCountDelta.coerceAtLeast(0),
                                playTimeDeltaMs = record.playTimeDeltaMs.coerceAtLeast(0L),
                            )
                            flushedGameIds.add(record.gameId)
                        }
                        journalFile.delete()
                    }.onFailure { error ->
                        Timber.e(error, "Failed to flush playtime journal ${journalFile.name}")
                    }
                }
                flushedGameIds
            }
        }
    }

    internal fun serializeJournalRecord(
        gameId: Int,
        playedAtMs: Long,
        playCountDelta: Int,
        playTimeDeltaMs: Long,
    ): String = "$gameId|$playedAtMs|$playCountDelta|$playTimeDeltaMs"

    internal fun parseJournalText(raw: String): PendingSessionRecord? {
        val parts = raw.trim().split('|')
        if (parts.size != 4) return null
        val gameId = parts[0].toIntOrNull() ?: return null
        val playedAtMs = parts[1].toLongOrNull() ?: return null
        val playCountDelta = parts[2].toIntOrNull() ?: return null
        val playTimeDeltaMs = parts[3].toLongOrNull() ?: return null
        return PendingSessionRecord(
            gameId = gameId,
            playedAtMs = playedAtMs,
            playCountDelta = playCountDelta,
            playTimeDeltaMs = playTimeDeltaMs,
        )
    }

    fun formatForGame(
        context: Context,
        game: Game,
    ): String {
        val playTimeMs = game.playTimeMs
        if (playTimeMs <= 0L) {
            return if (game.lastPlayedAt != null || game.playCount > 0) {
                context.getString(R.string.game_playtime_under_minute)
            } else {
                context.getString(R.string.game_playtime_never)
            }
        }
        return context.getString(R.string.game_playtime_played_format, formatDurationShort(playTimeMs))
    }

    internal fun formatDurationShort(playTimeMs: Long): String {
        val clampedMs = playTimeMs.coerceAtLeast(0L)
        if (clampedMs < MS_PER_MINUTE) {
            val seconds = maxOf(1L, clampedMs / MS_PER_SECOND)
            return "${seconds}s"
        }
        if (clampedMs < MS_PER_HOUR) {
            val minutes = clampedMs / MS_PER_MINUTE
            return "${minutes}m"
        }
        val hours = clampedMs / MS_PER_HOUR
        val minutes = (clampedMs % MS_PER_HOUR) / MS_PER_MINUTE
        return if (minutes == 0L) {
            "${hours}h"
        } else {
            "${hours}h ${minutes}m"
        }
    }
}
