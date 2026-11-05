package com.swordfish.touchinput.skin

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import timber.log.Timber

/** Reads a skin's artwork (see [SkinArt]) from the app's assets. */
object SkinArtLoader {
    // The last few skins read, so turning the phone or coming back to a game shows them at once.
    private val cache = android.util.LruCache<String, Optional>(6)

    private class Optional(val art: SkinArt?)

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
        val art = read(context, folder)
        cache.put(folder, Optional(art))
        return art
    }

    private fun read(
        context: Context,
        folder: String,
    ): SkinArt? {
        val files = runCatching { context.assets.list(folder) }.getOrNull()?.toList().orEmpty()
        if (files.isEmpty()) return null
        val images =
            files.mapNotNull { file ->
                val name = file.substringBeforeLast('.')
                runCatching {
                    context.assets.open("$folder/$file").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.onFailure { Timber.e(it, "Could not read the skin art $folder/$file") }
                    .getOrNull()
                    ?.let { name to it }
            }.toMap()
        val grain =
            runCatching {
                val tile = "skin-art/${SkinArt.GRAIN}.webp"
                context.assets.open(tile).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        return SkinArt(images[SkinArt.BODY], images - SkinArt.BODY, grain)
    }
}
