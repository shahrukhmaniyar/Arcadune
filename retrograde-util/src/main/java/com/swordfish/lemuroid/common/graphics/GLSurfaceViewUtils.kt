package com.swordfish.lemuroid.common.graphics

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.PixelCopy
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

suspend fun GLSurfaceView.takeScreenshot(
    maxResolution: Int,
    retries: Int = 1,
): Bitmap? =
    withContext(Dispatchers.Main) {
        runCatchingWithRetry(retries) {
            takeScreenshot(maxResolution)
        }.getOrNull()
    }

private suspend fun GLSurfaceView.takeScreenshot(maxResolution: Int): Bitmap? =
    suspendCoroutine { cont ->
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            cont.resume(null)
            return@suspendCoroutine
        }

        queueEvent {
            try {
                if (width <= 0 || height <= 0 || maxResolution <= 0) {
                    cont.resume(null)
                    return@queueEvent
                }
                val outputScaling = maxResolution / maxOf(width, height).toFloat()
                val inputScaling = outputScaling * 2

                val inputBitmap =
                    Bitmap.createBitmap(
                        (width * inputScaling).roundToInt().coerceAtLeast(1),
                        (height * inputScaling).roundToInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )

                val onCompleted = { result: Int ->
                    if (result == PixelCopy.SUCCESS) {
                        // This rescaling limits the artifacts introduced by shaders.
                        val outputBitmap =
                            Bitmap.createScaledBitmap(
                                inputBitmap,
                                (width * outputScaling).roundToInt().coerceAtLeast(1),
                                (height * outputScaling).roundToInt().coerceAtLeast(1),
                                true,
                            )
                        if (outputBitmap !== inputBitmap) {
                            inputBitmap.recycle()
                        }

                        cont.resume(outputBitmap)
                    } else {
                        inputBitmap.recycle()
                        cont.resumeWithException(RuntimeException("Cannot take screenshot. Error code: $result"))
                    }
                }
                PixelCopy.request(this, inputBitmap, onCompleted, handler)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }
