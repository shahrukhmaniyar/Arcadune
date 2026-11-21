package com.swordfish.lemuroid.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.startup.AppInitializer
import androidx.work.ListenableWorker
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.android.material.color.DynamicColors
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.shared.startup.GameProcessInitializer
import com.swordfish.lemuroid.app.shared.startup.MainProcessInitializer
import com.swordfish.lemuroid.app.utils.android.isMainProcess
import com.swordfish.lemuroid.common.files.ChdReader
import com.swordfish.lemuroid.common.files.FileChdAccess
import com.swordfish.lemuroid.common.files.ParcelFdChdAccess
import com.swordfish.lemuroid.ext.feature.context.ContextHandler
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsAuthClient
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsOfflineQueue
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsPreferences
import com.swordfish.lemuroid.lib.injection.HasWorkerInjector
import com.swordfish.libretrodroid.AchievementsBridge
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.DaggerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class LemuroidApplication : DaggerApplication(), HasWorkerInjector, ImageLoaderFactory {
    @Inject
    lateinit var workerInjector: DispatchingAndroidInjector<ListenableWorker>

    private val achievementsObserverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("CheckResult")
    override fun onCreate() {
        super.onCreate()

        RetroAchievementsPreferences.buildHardcoreApprovedFlag = BuildConfig.RA_HARDCORE_APPROVED
        RetroAchievementsAuthClient.appVersion = BuildConfig.VERSION_NAME
        if (BuildConfig.RA_SERVER_HOST.isNotBlank()) {
            RetroAchievementsAuthClient.endpointUrl =
                "${BuildConfig.RA_SERVER_HOST.trimEnd('/')}/dorequest.php"
        }
        AchievementsBridge.appVersion = BuildConfig.VERSION_NAME
        AchievementsBridge.httpTransferObserver =
            object : AchievementsBridge.HttpTransferObserver {
                override fun onRetryableFailure(
                    url: String,
                    postData: String?,
                    gameHash: String,
                    userAgent: String,
                ) {
                    achievementsObserverScope.launch {
                        RetroAchievementsOfflineQueue.enqueueFromPostData(
                            context = applicationContext,
                            postData = postData,
                            fallbackGameHash = gameHash,
                            userAgent = userAgent,
                        )
                    }
                }

                override fun onCompletedResponse(
                    url: String,
                    postData: String?,
                    httpStatusCode: Int,
                    responseBodyUtf8: String?,
                    gameHash: String,
                    userAgent: String,
                ) {
                    achievementsObserverScope.launch {
                        RetroAchievementsOfflineQueue.acknowledgeCompletedPostData(
                            context = applicationContext,
                            postData = postData,
                            httpStatusCode = httpStatusCode,
                            responseBodyUtf8 = responseBodyUtf8,
                            fallbackGameHash = gameHash,
                        )
                    }
                }
            }

        AchievementsBridge.chdSectorReader =
            object : AchievementsBridge.ChdSectorReader {
                private val nextHandle = AtomicLong(1L)
                private val openHandles = ConcurrentHashMap<Long, ChdReader.TrackHandle>()

                override fun openTrack(
                    path: String,
                    adoptedFd: Int,
                    track: Int,
                ): Long {
                    val access =
                        if (adoptedFd >= 0) {
                            ParcelFdChdAccess(ParcelFileDescriptor.adoptFd(adoptedFd), closeDescriptor = true)
                        } else {
                            val file = File(path)
                            if (!file.isFile) return 0L
                            FileChdAccess(file)
                        }
                    val handle = ChdReader.openTrack(access, trackSelector = track, ownSource = true) ?: return 0L
                    val id = nextHandle.getAndIncrement()
                    openHandles[id] = handle
                    return id
                }

                override fun readSector(
                    handle: Long,
                    sector: Int,
                    requestedBytes: Int,
                ): ByteArray? = openHandles[handle]?.readCookedSectors(sector, requestedBytes)

                override fun firstTrackSector(handle: Long): Int = openHandles[handle]?.firstTrackSector ?: 0

                override fun closeTrack(handle: Long) {
                    openHandles.remove(handle)?.close()
                }

                override fun closeAll() {
                    val snapshot = openHandles.keys().toList()
                    for (key in snapshot) {
                        openHandles.remove(key)?.close()
                    }
                }
            }

        val initializeComponent =
            if (isMainProcess()) {
                MainProcessInitializer::class.java
            } else {
                GameProcessInitializer::class.java
            }

        AppInitializer.getInstance(this).initializeComponent(initializeComponent)

        // Android's automatic startup is switched off for this app (see the manifest), which also
        // switched off the profile installer. It copies the baseline profile (src/main/baseline-prof.txt)
        // to Android after the first launch, so the app's screens get compiled ahead of time.
        if (isMainProcess()) {
            AppInitializer.getInstance(this)
                .initializeComponent(androidx.profileinstaller.ProfileInstallerInitializer::class.java)
        }

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        ContextHandler.attachBaseContext(base)
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerLemuroidApplicationComponent.builder().create(this)
    }

    override fun workerInjector(): AndroidInjector<ListenableWorker> = workerInjector

    override fun newImageLoader(): ImageLoader {
        return CoverUtils.buildImageLoader(applicationContext)
    }
}
