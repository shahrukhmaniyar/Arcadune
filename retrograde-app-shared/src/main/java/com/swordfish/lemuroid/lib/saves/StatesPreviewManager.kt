package com.swordfish.lemuroid.lib.saves

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class StatesPreviewManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getPreviewForSlot(
        game: Game,
        coreID: CoreID,
        index: Int,
        size: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val screenshotName = getSlotScreenshotName(game, index)
            val file = getPreviewFile(screenshotName, coreID.coreName)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            val cropped = cropBlackBorders(bitmap)
            val scaled = scalePreview(cropped, size)
            if (scaled !== cropped) {
                cropped.recycle()
            }
            if (cropped !== bitmap) {
                bitmap.recycle()
            }
            scaled
        }

    suspend fun setPreviewForSlot(
        game: Game,
        bitmap: Bitmap,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        val screenshotName = getSlotScreenshotName(game, index)
        val file = getPreviewFile(screenshotName, coreID.coreName)
        val cropped = cropBlackBorders(bitmap)
        FileOutputStream(file).use {
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        if (cropped !== bitmap) {
            cropped.recycle()
        }
    }

    suspend fun deletePreviewForSlot(
        game: Game,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        val screenshotName = getSlotScreenshotName(game, index)
        val file = getPreviewFile(screenshotName, coreID.coreName)
        if (file.exists()) {
            file.delete()
        }
    }

    /** Removes only centered solid black padding added around the rendered game by the emulator surface. */
    private fun cropBlackBorders(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) return bitmap
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, pixel ->
            val red = pixel shr 16 and 0xff
            val green = pixel shr 8 and 0xff
            val blue = pixel and 0xff
            if (red > BLACK_THRESHOLD || green > BLACK_THRESHOLD || blue > BLACK_THRESHOLD) {
                val x = index % width
                val y = index / width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }

        if (right < left || bottom < top) return bitmap
        val croppedWidth = right - left + 1
        val croppedHeight = bottom - top + 1
        if (croppedWidth == width && croppedHeight == height) return bitmap
        // A mostly-black game frame can contain a few bright pixels. Do not zoom into those.
        if (croppedWidth <= 0 || croppedHeight <= 0 || croppedWidth * croppedHeight < width * height / 8) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, left, top, croppedWidth, croppedHeight)
    }

    private fun scalePreview(
        bitmap: Bitmap,
        size: Int,
    ): Bitmap {
        val scale = minOf(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height, 1f)
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun getPreviewFile(
        fileName: String,
        coreName: String,
    ): File {
        val statesDirectories = File(directoriesManager.getStatesPreviewDirectory(), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getSlotScreenshotName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}.jpg"

    companion object {
        val PREVIEW_SIZE_DP = 256f
        private const val BLACK_THRESHOLD = 12
    }
}
