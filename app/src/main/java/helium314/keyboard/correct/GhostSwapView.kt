// SPDX-License-Identifier: GPL-3.0-only
//
// WaveKey. Self-contained on purpose: this whole package plus the one
// call in LatinIME is the entire feature. Delete both and nothing else changes.
package helium314.keyboard.correct

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * Shows that a word was swapped: the old one lifts away and fades, the new one
 * settles in its place.
 *
 * It plays here, in the keyboard's own row, rather than over the corrected word
 * itself. An IME does not own the pixels of the app it types into — the only
 * thing that reaches that text is a span, one commit at a time — so animating
 * there would mean a text-change callback in someone else's app per frame.
 *
 * The view is transparent, never takes touches, and is invisible whenever it is
 * not mid-animation, so the row underneath behaves exactly as it did before.
 */
class GhostSwapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val oldWord = TextView(context)
    private val newWord = TextView(context)

    init {
        isClickable = false
        isFocusable = false
        // The row below is the interactive one; this only ever draws.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE

        for (view in listOf(oldWord, newWord)) {
            view.textSize = TEXT_SIZE_SP
            view.maxLines = 1
            addView(
                view,
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
        oldWord.setTypeface(Typeface.DEFAULT)
        newWord.setTypeface(Typeface.DEFAULT_BOLD)
    }

    /** Touches belong to whatever is underneath, always. */
    override fun onTouchEvent(event: MotionEvent) = false

    /**
     * Plays the swap. Safe to call again while one is running: the second call
     * replaces the first rather than stacking, because two ghosts on top of each
     * other read as a glitch.
     */
    fun show(from: String, to: String) {
        if (from.isEmpty() || to.isEmpty() || from == to) return
        cancelAnimations()

        val colors = Settings.getValues().mColors
        oldWord.text = from
        newWord.text = to
        oldWord.setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        newWord.setTextColor(colors.get(ColorType.SUGGESTION_AUTO_CORRECT))

        visibility = VISIBLE
        oldWord.alpha = 1f
        oldWord.translationY = 0f
        newWord.alpha = 0f
        newWord.scaleX = SETTLE_FROM_SCALE
        newWord.scaleY = SETTLE_FROM_SCALE

        val rise = oldWord.animate()
            .alpha(0f)
            .translationY(-height / RISE_DIVISOR)
            .setDuration(RISE_MS)
        val settle = newWord.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(SETTLE_DELAY_MS)
            .setDuration(SETTLE_MS)

        rise.start()
        settle.withEndAction { fadeOut() }.start()
    }

    /** Ends any animation and hides the view. Called when the row changes mode. */
    fun clear() {
        cancelAnimations()
        visibility = GONE
    }

    private fun cancelAnimations() {
        oldWord.animate().cancel()
        newWord.animate().setListener(null).cancel()
    }

    private fun fadeOut() {
        newWord.animate()
            .alpha(0f)
            .setStartDelay(HOLD_MS)
            .setDuration(FADE_MS)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    newWord.animate().setListener(null)
                }
            })
            .start()
    }

    companion object {
        private const val TEXT_SIZE_SP = 15f

        /** How far up the old word travels, as a fraction of the row's height. */
        private const val RISE_DIVISOR = 2.5f
        private const val SETTLE_FROM_SCALE = 0.92f

        private const val RISE_MS = 380L
        private const val SETTLE_DELAY_MS = 90L
        private const val SETTLE_MS = 260L

        /** Long enough to read a short word, short enough not to be in the way. */
        private const val HOLD_MS = 520L
        private const val FADE_MS = 220L
    }
}
