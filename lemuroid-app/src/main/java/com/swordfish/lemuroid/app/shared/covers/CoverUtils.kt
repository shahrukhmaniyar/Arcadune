package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.achievements.RetroAchievementsUserAgent
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

object CoverUtils {
    private const val MAX_DISK_CACHE_BYTES = 256L * 1024 * 1024
    private val cacheCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun loadCover(
        game: Game,
        imageView: ImageView?,
    ) {
        if (imageView == null) return

        imageView.load(game.displayCoverUrl(), imageView.context.imageLoader) {
            val fallbackDrawable = getFallbackDrawable(imageView.context)
            fallback(fallbackDrawable)
            error(fallbackDrawable)
        }
    }

    fun buildImageLoader(applicationContext: Context): ImageLoader {
        cacheCleanupScope.launch { clearOldLargeCoversOnce(applicationContext) }
        return ImageLoader.Builder(applicationContext)
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(MAX_DISK_CACHE_BYTES)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request()
                        val host = req.url.host.lowercase()
                        if (host.endsWith("retroachievements.org")) {
                            val ua =
                                RetroAchievementsUserAgent.build(
                                    appVersion = BuildConfig.VERSION_NAME,
                                    rcheevosClause = RetroAchievementsUserAgent.DEFAULT_RCHEEVOS_CLAUSE,
                                )
                            chain.proceed(req.newBuilder().header("User-Agent", ua).build())
                        } else {
                            chain.proceed(req)
                        }
                    }
                    .addInterceptor(LibretroThumbnailsMirrorInterceptor(applicationContext.cacheDir))
                    .addNetworkInterceptor(ThrottleFailedThumbnailsInterceptor)
                    .addNetworkInterceptor(CoverCompressionInterceptor())
                    .build()
            }
            .crossfade(true)
            .allowRgb565(true)
            .interceptorDispatcher(Dispatchers.IO)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
    }

    /**
     * Covers cached before covers were shrunk on download are full-size PNGs. They are dropped once,
     * so each cover is fetched again (small) the next time it is shown.
     */
    private fun clearOldLargeCoversOnce(context: Context) {
        val marker = context.cacheDir.resolve("image_cache_small_v1")
        if (marker.exists()) return
        runCatching {
            context.cacheDir.resolve("image_cache").deleteRecursively()
            marker.createNewFile()
        }
    }

    /** The cover of a game without artwork: the app's icon under a dark layer, as Delta does it. */
    fun getFallbackDrawable(
        context: Context,
        dim: Boolean = true,
    ): Drawable = CoverPlaceholderDrawable(context, dim)

    fun getFallbackLocalResourceUri(context: Context): String {
        return "android.resource://${context.packageName}/${R.mipmap.lemuroid_launcher}"
    }
}
