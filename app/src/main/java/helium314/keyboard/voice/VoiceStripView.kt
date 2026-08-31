// SPDX-License-Identifier: GPL-3.0-only
//
// SuperVoiceBoard. New file: voice is the fourth mode of the existing strip row
// (PLAN.md §2), so this is a sibling of SuggestionStripView, emoji_tab_strip and
// clipboard_strip inside strip_container — not a bar of its own.
package helium314.keyboard.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.vboard.app.voice.VoiceErrorAction
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import kotlin.math.max
import kotlin.math.min

/**
 * The strip while dictation is running.
 *
 * Layout follows what Gboard actually does, recorded in
 * docs/reference/gboard-strip-listening.png (W3.1): back at the left, status
 * text in the middle, mic at the right, and the keyboard left fully visible and
 * live underneath. The two additions are a level meter drawn behind the status
 * text — silence is otherwise indistinguishable from a broken mic — and an
 * explicit "done" so the session can be ended without waiting for endpointing.
 */
class VoiceStripView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    interface Listener {
        /** Back: abandon the session, keep whatever was already committed. */
        fun onVoiceCancel()
        /** Done: end the session now and commit the final pass. */
        fun onVoiceDone()
        /** Minimize: hide the keyboard but keep listening. */
        fun onVoiceMinimizeKeyboard()
        /** The action offered by the current error, if any. */
        fun onVoiceErrorAction(action: VoiceErrorAction)
    }

    var listener: Listener? = null

    private val backKey: ImageButton
    private val statusText: TextView
    private val minimizeKey: ImageButton
    private val doneKey: ImageButton

    /** Smoothed 0..1 input level, drawn as a bar behind the status text. */
    private var level = 0f
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var errorAction: VoiceErrorAction? = null

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.voice_strip, this, true)
        backKey = findViewById(R.id.voice_strip_back)
        statusText = findViewById(R.id.voice_strip_status)
        minimizeKey = findViewById(R.id.voice_strip_minimize)
        doneKey = findViewById(R.id.voice_strip_done)

        val colors: Colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        for (key in listOf(backKey, minimizeKey, doneKey)) {
            colors.setColor(key, ColorType.TOOL_BAR_KEY)
            colors.setBackground(key, ColorType.STRIP_BACKGROUND)
        }
        statusText.setTextColor(colors.get(ColorType.KEY_TEXT))
        levelPaint.color = colors.get(ColorType.TOOL_BAR_KEY)

        backKey.setOnClickListener {
            // An error with an offered action turns back into "do the thing"
            // (grant the permission, open the download) rather than a dead end.
            val action = errorAction
            if (action != null && action != VoiceErrorAction.DISMISS) listener?.onVoiceErrorAction(action)
            else listener?.onVoiceCancel()
        }
        minimizeKey.setOnClickListener { listener?.onVoiceMinimizeKeyboard() }
        doneKey.setOnClickListener { listener?.onVoiceDone() }

        setWillNotDraw(false)
    }

    // -------------------------------------------------------------- states

    fun showPreparing() = setState(context.getString(R.string.voice_preparing), showDone = false)

    fun showListening() = setState(context.getString(R.string.voice_listening), showDone = true)

    fun showFinalizing() = setState(context.getString(R.string.voice_finalizing), showDone = false)

    fun showRefining() = setState(context.getString(R.string.voice_cleaning), showDone = false)

    /** Partial transcript, shown in place of the status while words are arriving. */
    fun showPartial(text: String) {
        if (text.isBlank()) return
        errorAction = null
        statusText.text = text
        // Deliberately not announced: partials arrive several times a second and
        // TalkBack would restart the whole transcript on each one. The committed
        // text is announced by the editor itself, which is the right place.
        statusText.contentDescription = text
    }

    fun showError(message: String, action: VoiceErrorAction) {
        errorAction = action
        setState(message, showDone = false)
        // The error is the whole point of the row right now, so it is announced
        // rather than left for the user to notice.
        announceForAccessibility(message)
    }

    private fun setState(status: String, showDone: Boolean) {
        statusText.text = status
        doneKey.isVisible = showDone
        minimizeKey.isVisible = showDone
        statusText.contentDescription = status
        announceForAccessibility(status)
    }

    /** Called on the audio callback's cadence; smoothed here rather than there. */
    fun onAmplitude(rms: Float) {
        val target = min(1f, max(0f, rms))
        level += (target - level) * SMOOTHING
        invalidate()
    }

    /** TalkBack: the row changing mode is a state change worth announcing. */
    fun announceSessionStarted() =
        announceForAccessibility(context.getString(R.string.voice_started))

    fun announceSessionEnded() =
        announceForAccessibility(context.getString(R.string.voice_stopped))

    fun reset() {
        errorAction = null
        level = 0f
        statusText.text = ""
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (level <= 0.01f) return
        // A centred bar under the text: wide enough to read as a level, short
        // enough not to compete with the words it sits behind.
        val full = (statusText.right - statusText.left).toFloat()
        val width = full * level
        val cx = (statusText.left + statusText.right) / 2f
        val y = height - BAR_INSET_PX * resources.displayMetrics.density
        levelPaint.alpha = (60 + 120 * level).toInt().coerceAtMost(255)
        canvas.drawRoundRect(
            cx - width / 2f, y - 2f * resources.displayMetrics.density,
            cx + width / 2f, y,
            4f, 4f, levelPaint,
        )
    }

    companion object {
        private const val SMOOTHING = 0.35f
        private const val BAR_INSET_PX = 4f
    }
}
