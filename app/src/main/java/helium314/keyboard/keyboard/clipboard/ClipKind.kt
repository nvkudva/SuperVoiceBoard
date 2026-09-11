// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.clipboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.R
import helium314.keyboard.latin.suggestions.SuggestionStripView
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WaveKey: what a clip is, so the card can say so.
 *
 * A grid of identical text tiles makes the reader parse every clip to find the
 * one they want. A glyph and a line of metadata — where a link points, how big
 * an image is, how long a text is, how old it is — is what turns the panel into
 * something you scan instead of read.
 */
enum class ClipKind(val icon: Int, private val label: Int) {
    LINK(R.drawable.ic_clip_link, R.string.wk_clip_kind_link),
    IMAGE(R.drawable.ic_clip_image, R.string.wk_clip_kind_image),
    PHONE(R.drawable.ic_clip_phone, R.string.wk_clip_kind_phone),
    TEXT(R.drawable.ic_clip_text, R.string.wk_clip_kind_text);

    fun meta(context: Context, entry: ClipboardHistoryEntry): String {
        val parts = mutableListOf(context.getString(label))
        when (this) {
            LINK -> hostOf(entry.text)?.let { parts.add(it) }
            IMAGE -> entry.filename?.let { name ->
                val bytes = File(name).length()
                if (bytes > 0) parts.add(formatSize(bytes))
            }
            TEXT -> entry.text?.length?.takeIf { it > 80 }
                ?.let { parts.add(context.getString(R.string.wk_clip_chars, it)) }
            PHONE -> Unit
        }
        if (entry.isPinned) parts.add(context.getString(R.string.wk_clip_kept))
        else parts.add(age(entry.timeStamp))
        return parts.joinToString(" · ")
    }

    private fun hostOf(text: String?): String? = text?.trim()
        ?.substringAfter("://", "")?.substringBefore('/')?.removePrefix("www.")
        ?.takeIf { it.isNotEmpty() && it.length <= 30 }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
        else -> "${bytes / 1024} KB"
    }

    private fun age(timeStamp: Long): String {
        val ms = (System.currentTimeMillis() - timeStamp).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        if (minutes < 1) return "${TimeUnit.MILLISECONDS.toSeconds(ms)}s"
        val hours = TimeUnit.MINUTES.toHours(minutes)
        if (hours < 1) return "${minutes}m"
        return "${hours}h"
    }

    companion object {
        private val URL = Regex("""^\s*(https?://|www\.)\S+\s*$""", RegexOption.IGNORE_CASE)
        private val PHONE_NUMBER = Regex("""^\s*\+?[\d][\d\s\-()]{6,20}\s*$""")

        fun of(entry: ClipboardHistoryEntry): ClipKind {
            if (entry.filename != null) return IMAGE
            val text = entry.text ?: return TEXT
            return when {
                URL.matches(text) -> LINK
                PHONE_NUMBER.matches(text) -> PHONE
                else -> TEXT
            }
        }
    }
}

/**
 * The time a clip has left, drawn as the spectrum draining left to right. It is
 * the same spectrum the voice rail uses, dimmed: this is information, not a
 * thing to look at.
 */
class MeterDrawable(private val fraction: Float, private val fallback: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || fraction <= 0f) return
        val w = b.width() * fraction
        if (paint.shader == null) {
            paint.shader = LinearGradient(
                0f, 0f, b.width().toFloat(), 0f,
                SuggestionStripView.SPECTRUM, null, Shader.TileMode.CLAMP,
            )
        }
        paint.alpha = 120
        val r = b.height() / 2f
        canvas.drawRoundRect(RectF(b.left.toFloat(), b.top.toFloat(), b.left + w, b.bottom.toFloat()), r, r, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("deprecated in Drawable, but still abstract")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
