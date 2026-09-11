// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.voice

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * WaveKey: the border that runs while a fix is being written.
 *
 * The key already fills with the spectrum, so the working state cannot be a
 * brighter fill — it is a light travelling around the edge instead. One turn a
 * second, which reads as working rather than as flashing.
 */
class RunningBorderDrawable(
    private val cornerRadius: Float,
    private val strokeWidth: Float,
    private val inset: Float,
    colors: IntArray,
) : Drawable(), Animatable {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = this@RunningBorderDrawable.strokeWidth
    }
    // The sweep has to close on itself or the seam shows as a hard edge once a
    // turn, so the first colour is repeated at the end.
    private val sweep = colors + colors.first()
    private val matrix = Matrix()
    private val box = RectF()
    private var shader: SweepGradient? = null
    private var angle = 0f

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = TURN_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedValue as Float
            invalidateSelf()
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val half = strokeWidth / 2f
        // The ring rides just outside the tile's edge rather than inside it —
        // the fill is already saturated, so a stroke drawn on top of it reads as
        // a smudge. Radius grows with the offset so the ring stays parallel to
        // the tile's corners instead of cutting across them.
        val out = (inset - half).coerceAtLeast(0f)
        box.set(
            b.left + out, b.top + out,
            b.right - out, b.bottom - out,
        )
        if (box.width() <= 0f || box.height() <= 0f) return
        val r = (cornerRadius + (inset - out)).coerceAtMost(minOf(box.width(), box.height()) / 2f)
        val s = shader ?: SweepGradient(box.centerX(), box.centerY(), sweep, null).also { shader = it }
        matrix.setRotate(angle, box.centerX(), box.centerY())
        s.setLocalMatrix(matrix)
        paint.shader = s
        canvas.drawRoundRect(box, r, r, paint)
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        shader = null
    }

    override fun start() {
        if (!animator.isRunning) animator.start()
    }

    override fun stop() {
        animator.cancel()
    }

    override fun isRunning() = animator.isRunning

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("deprecated in Drawable, but still abstract")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private const val TURN_MS = 1000L
    }
}
