package com.swordfish.lemuroid.app.shared.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.PlayTimeTracker
import com.swordfish.lemuroid.app.shared.gamecrash.GameCrashActivity
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork
import com.swordfish.lemuroid.app.shared.storage.cache.CacheCleanerWork
import com.swordfish.lemuroid.ext.feature.review.ReviewManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.delay

class GameLaunchTaskHandler(
    private val reviewManager: ReviewManager,
    private val retrogradeDb: RetrogradeDatabase,
) {
    fun handleGameStart(context: Context) {
        cancelBackgroundWork(context)
    }

    suspend fun handleGameFinish(
        enableRatingFlow: Boolean,
        activity: Activity,
        resultCode: Int,
        data: Intent?,
    ) {
        val flushedGameIds = PlayTimeTracker.flushPendingToDatabase(activity.applicationContext, retrogradeDb)
        rescheduleBackgroundWork(activity.applicationContext)
        when (resultCode) {
            Activity.RESULT_OK -> handleSuccessfulGameFinish(activity, enableRatingFlow, data, flushedGameIds)
            BaseGameActivity.RESULT_ERROR ->
                handleUnsuccessfulGameFinish(
                    activity,
                    data?.getStringExtra(BaseGameActivity.PLAY_GAME_RESULT_ERROR)
                        ?: activity.getString(R.string.lemuroid_crash_disclamer),
                    null,
                )
            BaseGameActivity.RESULT_UNEXPECTED_ERROR ->
                handleUnsuccessfulGameFinish(
                    activity,
                    activity.getString(R.string.lemuroid_crash_disclamer),
                    data?.getStringExtra(BaseGameActivity.PLAY_GAME_RESULT_ERROR),
                )
        }
    }

    private fun cancelBackgroundWork(context: Context) {
        SaveSyncWork.cancelAutoWork(context)
        SaveSyncWork.cancelManualWork(context)
        CacheCleanerWork.cancelCleanCacheLRU(context)
    }

    private fun rescheduleBackgroundWork(context: Context) {
        // Let's slightly delay the sync. Maybe the user wants to play another game.
        SaveSyncWork.enqueueAutoWork(context, 5)
        CacheCleanerWork.enqueueCleanCacheLRU(context)
    }

    private fun handleUnsuccessfulGameFinish(
        activity: Activity,
        message: String,
        messageDetail: String?,
    ) {
        GameCrashActivity.launch(activity, message, messageDetail)
    }

    private suspend fun handleSuccessfulGameFinish(
        activity: Activity,
        enableRatingFlow: Boolean,
        data: Intent?,
        flushedGameIds: Set<Int>,
    ) {
        val duration =
            data?.extras?.getLong(BaseGameActivity.PLAY_GAME_RESULT_SESSION_DURATION)
                ?: 0L

        @Suppress("DEPRECATION")
        val game =
            (data?.extras?.getSerializable(BaseGameActivity.PLAY_GAME_RESULT_GAME) as? Game)
                ?: return

        if (game.id !in flushedGameIds) {
            retrogradeDb.gameDao().recordPlaySession(
                id = game.id,
                playedAt = System.currentTimeMillis(),
                playCountDelta = 1,
                playTimeDeltaMs = duration.coerceAtLeast(0L),
            )
        }
        if (enableRatingFlow) {
            displayReviewRequest(activity, duration)
        }
    }

    private suspend fun displayReviewRequest(
        activity: Activity,
        durationMillis: Long,
    ) {
        delay(500)
        reviewManager.launchReviewFlow(activity, durationMillis)
    }
}
