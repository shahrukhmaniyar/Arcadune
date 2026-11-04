package com.swordfish.lemuroid.app.shared.covers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Covers are stored small: a downloaded cover is scaled down to at most [MAX_SIDE] pixels and
 * saved as WebP. Box art from the cover database is often a large PNG of several hundred KB; the
 * library shows it at a few hundred pixels, so most of that was wasted space and decoding time.
 */
object CoverCompression {
    const val MAX_SIDE = 600
    private const val QUALITY = 82
    private const val MIN_BYTES_WORTH_COMPRESSING = 40 * 1024

    /** Returns [bytes] as a small WebP, or null when they are already small or not an image. */
    fun compress(bytes: ByteArray): ByteArray? {
        if (bytes.size < MIN_BYTES_WORTH_COMPRESSING) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null

        val largest = maxOf(decoded.width, decoded.height)
        val bitmap =
            if (largest > MAX_SIDE) {
                val factor = MAX_SIDE.toFloat() / largest
                Bitmap.createScaledBitmap(decoded, (decoded.width * factor).toInt(), (decoded.height * factor).toInt(), true)
                    .also { if (it != decoded) decoded.recycle() }
            } else {
                decoded
            }

        val output = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        bitmap.compress(format, QUALITY, output)
        bitmap.recycle()
        return output.toByteArray().takeIf { it.size < bytes.size }
    }

    /** Shrinks a cover file in place (used for covers the player picks). */
    fun compressFile(file: File) {
        runCatching {
            val smaller = compress(file.readBytes()) ?: return
            file.writeBytes(smaller)
        }.onFailure { Timber.w(it, "Could not shrink cover ${file.name}") }
    }
}

/** Shrinks every downloaded image before it reaches the image cache. */
class CoverCompressionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val type = body.contentType()
        val isImage = type?.type == "image" || chain.request().url.encodedPath.endsWith(".png", ignoreCase = true)
        if (!response.isSuccessful || !isImage) return response

        val bytes = body.bytes()
        val smaller = runCatching { CoverCompression.compress(bytes) }.getOrNull()
        val newBody =
            if (smaller != null) {
                smaller.toResponseBody("image/webp".toMediaType())
            } else {
                bytes.toResponseBody(type)
            }
        val builder =
            response.newBuilder()
                .removeHeader("Content-Length")
                .removeHeader("Content-Encoding")
                .body(newBody)
        newBody.contentType()?.let { builder.header("Content-Type", it.toString()) }
        return builder.build()
    }
}
