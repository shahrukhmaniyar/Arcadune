package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.swordfish.lemuroid.R
import kotlin.math.roundToInt

/**
 * The cover of a game without artwork: a clean monochrome card holding just Arcadune's pixel "A"
 * mark in greyscale, so a shelf with missing covers stays quiet and cohesive. [dim] false renders
 * a brighter white-on-charcoal mark for pinned home-screen shortcuts.
 */
class CoverPlaceholderDrawable(
    context: Context,
    private val dim: Boolean = true,
) : Drawable() {
    private val mark = markOf(context)
    private val defaultMarkFilter =
        PorterDuffColorFilter(
            if (dim) MARK_COLOR_DIM else MARK_COLOR_BRIGHT,
            PorterDuff.Mode.SRC_IN,
        )
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = defaultMarkFilter
        }
    private val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = BORDER_COLOR
        }
    private val path = Path()
    private val box = RectF()
    private val dst = Rect()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        box.set(bounds)
        val side = minOf(bounds.width(), bounds.height())
        val radius = side * CORNER
        path.reset()
        path.addRoundRect(box, radius, radius, Path.Direction.CW)

        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(if (dim) CARD_COLOR_DIM else CARD_COLOR_BRIGHT)
        if (mark != null) {
            val targetWidth = side * MARK_WIDTH_RATIO
            val targetHeight = targetWidth * (ART.height().toFloat() / ART.width().toFloat())
            val left = (bounds.exactCenterX() - targetWidth / 2f).roundToInt()
            val top = (bounds.exactCenterY() - targetHeight / 2f).roundToInt()
            dst.set(left, top, left + targetWidth.roundToInt(), top + targetHeight.roundToInt())
            canvas.drawBitmap(mark, ART, dst, paint)
        }
        if (dim) {
            borderPaint.strokeWidth = maxOf(1f, side * BORDER_WIDTH_RATIO)
            canvas.drawRoundRect(box, radius, radius, borderPaint)
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        borderPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter ?: defaultMarkFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = -1

    override fun getIntrinsicHeight(): Int = -1

    companion object {
        /** Tight bounds of the "A" glyph inside the 540x540 monochrome drawable. */
        private val ART = Rect(154, 202, 387, 362)

        private const val CORNER = 0.08f
        private const val MARK_WIDTH_RATIO = 0.56f
        private const val BORDER_WIDTH_RATIO = 0.01f

        private val CARD_COLOR_DIM = Color.rgb(24, 24, 29)
        private val CARD_COLOR_BRIGHT = Color.rgb(18, 18, 22)
        private val MARK_COLOR_DIM = Color.rgb(138, 138, 148)
        private val MARK_COLOR_BRIGHT = Color.rgb(242, 242, 245)
        private val BORDER_COLOR = Color.rgb(46, 46, 54)

        @Volatile
        private var cached: Bitmap? = null

        /** Decoded once and shared by every placeholder cover. */
        private fun markOf(context: Context): Bitmap? =
            cached ?: synchronized(this) {
                cached ?: runCatching {
                    val resources = context.applicationContext.resources
                    BitmapFactory.decodeResource(resources, R.drawable.lemuroid_launcher_monochrome)
                }.getOrNull().also { cached = it }
            }
    }
}
