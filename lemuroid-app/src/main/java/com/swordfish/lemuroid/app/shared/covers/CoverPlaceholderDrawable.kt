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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.swordfish.lemuroid.R

/**
 * The cover of a game without artwork, as Delta does it: the app's icon under a dark layer, the
 * same for every game, so a shelf with missing covers still looks calm rather than a patchwork of
 * random colours. [dim] false shows the icon as it is (for home-screen shortcuts).
 */
class CoverPlaceholderDrawable(
    context: Context,
    private val dim: Boolean = true,
) : Drawable() {
    private val icon = iconOf(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val box = RectF()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        box.set(bounds)
        val radius = minOf(bounds.width(), bounds.height()) * CORNER
        path.reset()
        path.addRoundRect(box, radius, radius, Path.Direction.CW)

        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(Color.BLACK)
        if (icon != null) {
            // Square, centred: the icon's art fills the cover.
            val side = minOf(bounds.width(), bounds.height())
            val left = bounds.left + (bounds.width() - side) / 2
            val top = bounds.top + (bounds.height() - side) / 2
            canvas.drawBitmap(icon, ART, Rect(left, top, left + side, top + side), paint)
        }
        if (dim) canvas.drawColor(Color.argb(DIM_ALPHA, 0, 0, 0))
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = -1

    override fun getIntrinsicHeight(): Int = -1

    companion object {
        /** Where the art sits in the launcher foreground (540 px, with a margin for the launcher's mask). */
        private val ART = Rect(60, 60, 480, 480)

        /** The corners of the icon's own art, so none of its transparent corner shows. */
        private const val CORNER = 0.08f

        /** About 60% black over the icon: still there, but quiet. */
        private const val DIM_ALPHA = 150

        @Volatile
        private var cached: Bitmap? = null

        /** Decoded once and shared by every cover. */
        private fun iconOf(context: Context): Bitmap? =
            cached ?: synchronized(this) {
                cached ?: runCatching {
                    val resources = context.applicationContext.resources
                    BitmapFactory.decodeResource(resources, R.drawable.lemuroid_launcher_foreground)
                }.getOrNull().also { cached = it }
            }
    }
}
