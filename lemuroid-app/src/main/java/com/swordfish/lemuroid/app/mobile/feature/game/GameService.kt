package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import dagger.android.DaggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class GameService : DaggerService() {
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        ensureTaskRunnerStarted(applicationContext)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ensureTaskRunnerStarted(applicationContext)
        if (intent != null) {
            displayNotification(intent)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        // Android 15 enforces a 6-hour rolling quota on dataSync foreground services.
        // Drop foreground status cleanly while keeping processScope alive so SRAM + AutoSave
        // tasks scheduled on game exit still run to completion before process termination.
        Timber.w("GameService foreground timeout reached (fgsType=%d), stopping foreground", fgsType)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        hideNotification()
        stopSelf(startId)
    }

    private fun displayNotification(intent: Intent) {
        val gameIntent =
            intent.getParcelableExtra<Intent>(EXTRA_GAME_ACTIVITY_INTENT)
                ?: return
        val notification = NotificationsManager(applicationContext).gameRunningNotification(gameIntent)
        runCatching {
            ServiceCompat.startForeground(
                this,
                NotificationsManager.GAME_RUNNING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }.onFailure {
            Timber.w(it, "Unable to promote GameService to foreground")
        }
    }

    private fun hideNotification() {
        NotificationManagerCompat.from(this).cancel(NotificationsManager.GAME_RUNNING_NOTIFICATION_ID)
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        hideNotification()
    }

    companion object {
        private const val EXTRA_GAME_ACTIVITY_INTENT = "EXTRA_GAME_ACTIVITY_INTENT"

        private data class GameProcessTask(
            val task: suspend () -> Unit = {},
            val terminate: Boolean = false,
        )

        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val runnerStarted = AtomicBoolean(false)
        private val tasks = Channel<GameProcessTask>(capacity = Channel.UNLIMITED)

        @Volatile
        private var activeService: GameService? = null

        @Volatile
        private var retainedAppContext: Context? = null

        private fun ensureTaskRunnerStarted(context: Context? = null) {
            if (context != null && retainedAppContext == null) {
                retainedAppContext = context.applicationContext
            }
            if (!runnerStarted.compareAndSet(false, true)) return
            processScope.launch {
                awaitTermination()
                withContext(Dispatchers.Main) {
                    activeService?.let { service ->
                        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        service.stopSelf()
                    }
                    retainedAppContext?.let { ctx ->
                        NotificationManagerCompat.from(ctx).cancel(NotificationsManager.GAME_RUNNING_NOTIFICATION_ID)
                    }
                    exitProcess(0)
                }
            }
        }

        fun startService(
            context: Context,
            gameActivityIntent: Intent,
        ) {
            ensureTaskRunnerStarted(context)
            runCatching {
                context.startService(
                    Intent(context, GameService::class.java).apply {
                        putExtra(EXTRA_GAME_ACTIVITY_INTENT, gameActivityIntent)
                    },
                )
            }.onFailure {
                Timber.w(it, "Unable to start GameService; process task runner remains active")
            }
        }

        fun schedule(task: suspend () -> Unit) {
            ensureTaskRunnerStarted()
            val result = tasks.trySend(GameProcessTask(task = task))
            Timber.i("GameService.schedule sent=%s", result.isSuccess)
        }

        fun requestTermination() {
            ensureTaskRunnerStarted()
            val result = tasks.trySend(GameProcessTask(terminate = true))
            Timber.i("GameService.requestTermination sent=%s", result.isSuccess)
        }

        private suspend fun awaitTermination() {
            for (task in tasks) {
                runCatching { task.task() }
                    .onFailure { Timber.e(it, "GameService task failed") }

                if (task.terminate) {
                    return
                }
            }
        }
    }
}
