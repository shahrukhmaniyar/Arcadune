package com.swordfish.touchinput.skin

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Reads a skin's artwork (see [SkinArt]) from the app's assets. */
object SkinArtLoader {
    // Holds all controllers across both orientations so the gallery and game transitions never thrash.
    private val cache = android.util.LruCache<String, Optional>(96)

    private val loadLock = Any()

    private val decodePool: ExecutorService by lazy {
        Executors.newFixedThreadPool(DECODE_THREADS) { task ->
            Thread(task, "skin-art-decode").apply { isDaemon = true }
        }
    }

    private const val DECODE_THREADS = 4

    @Volatile
    private var cachedGrain: ImageBitmap? = null

    @Volatile
    private var grainLoaded: Boolean = false

    private class Optional(val art: SkinArt?)

    /** True when [controller] in [landscape] has already been loaded (even if it has no art files). */
    fun isCached(
        controller: String,
        landscape: Boolean,
    ): Boolean = cache.get(SkinArt.folder(controller, landscape)) != null

    /** The artwork already read for [controller] in one orientation, if any (a quick look, for a first frame). */
    fun cached(
        controller: String,
        landscape: Boolean,
    ): SkinArt? = cache.get(SkinArt.folder(controller, landscape))?.art

    /** The artwork of [controller] for one orientation, or null when it has none. Reads files: call it off the main thread. */
    fun load(
        context: Context,
        controller: String,
        landscape: Boolean,
    ): SkinArt? {
        val folder = SkinArt.folder(controller, landscape)
        cache.get(folder)?.let { return it.art }
        // One reader at a time: when the game starts reading a skin early and the screen asks for it
        // a moment later, the second one waits and then simply takes the finished result.
        synchronized(loadLock) {
            cache.get(folder)?.let { return it.art }
            val art = read(context, folder)
            cache.put(folder, Optional(art))
            return art
        }
    }

    private fun loadGrain(context: Context): ImageBitmap? {
        if (grainLoaded) return cachedGrain
        synchronized(this) {
            if (grainLoaded) return cachedGrain
            cachedGrain = decodeAssetBitmap(context, "skin-art/${SkinArt.GRAIN}.webp")
            grainLoaded = true
            return cachedGrain
        }
    }

    private fun decodeAssetBitmap(
        context: Context,
        assetPath: String,
    ): ImageBitmap? =
        runCatching {
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)?.also { it.prepareToDraw() }?.asImageBitmap()
            }
        }.getOrNull()

    private fun read(
        context: Context,
        folder: String,
    ): SkinArt? {
        val files = runCatching { context.assets.list(folder) }.getOrNull()?.toList().orEmpty()
        if (files.isEmpty()) return null
        // The pictures are decoded side by side (a console has about thirty), not one after another.
        val grainJob = decodePool.submit<ImageBitmap?> { loadGrain(context) }
        val jobs =
            files.map { file ->
                decodePool.submit<Pair<String, ImageBitmap>?> {
                    val name = file.substringBeforeLast('.')
                    val bitmap = decodeAssetBitmap(context, "$folder/$file")
                    if (bitmap == null) {
                        Timber.w("Could not read the skin art $folder/$file")
                        null
                    } else {
                        name to bitmap
                    }
                }
            }
        val images = jobs.mapNotNull { it.get() }.toMap()
        val grain = grainJob.get()
        return SkinArt(images[SkinArt.BODY], images - SkinArt.BODY, grain)
    }
}
