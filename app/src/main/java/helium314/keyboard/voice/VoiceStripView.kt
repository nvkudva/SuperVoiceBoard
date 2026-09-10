// SPDX-License-Identifier: GPL-3.0-only
//
// WaveKey. New file: voice is the fourth mode of the existing strip row
// (PLAN.md §2), so this is a sibling of SuggestionStripView, emoji_tab_strip and
// clipboard_strip inside strip_container — not a bar of its own.
package helium314.keyboard.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import helium314.keyboard.latin.suggestions.SuggestionStripView
import android.graphics.Shader
import android.graphics.LinearGradient
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
import helium314.keyboard.latin.utils.prefs
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
    private var shaderWidth = 0

    private var errorAction: VoiceErrorAction? = null

    init {
        // WaveKey: clear the spectrum meter at the top edge, matching the
        // suggestion strip so nothing shifts when the two swap.
        setPadding(
            paddingLeft,
            resources.getDimensionPixelSize(helium314.keyboard.latin.R.dimen.config_toolbar_rail_gap),
            paddingRight,
            paddingBottom,
        )
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.voice_strip, this, true)
        backKey = findViewById(R.id.voice_strip_back)
        statusText = findViewById(R.id.voice_strip_status)
        minimizeKey = findViewById(R.id.voice_strip_minimize)
        doneKey = findViewById(R.id.voice_strip_done)

        val colors: Colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        for (key in listOf(backKey, minimizeKey)) {
            colors.setColor(key, ColorType.TOOL_BAR_KEY)
            // WaveKey: the same rounded square every other strip key wears.
            key.setBackgroundResource(R.drawable.toolbar_key_background)
            colors.setBackground(key, ColorType.FUNCTIONAL_KEY_BACKGROUND)
        }
        // The stop control sits where the mic was and wears the same spectrum
        // tile: one target starts dictation and ends it, and it never moves
        // under the finger that just pressed it.
        doneKey.setBackgroundResource(R.drawable.spectrum_tile)
        doneKey.setImageResource(R.drawable.ic_close_rounded)
        doneKey.setColorFilter(SuggestionStripView.SPECTRUM_GLYPH)
        doneKey.contentDescription = context.getString(R.string.voice_done)
        statusText.setTextColor(colors.get(ColorType.KEY_TEXT))

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
        // WaveKey: the minimize key hides the keyboard and keeps listening. It
        // is off by default — it is not a thing most people want mid-sentence,
        // and it could not be removed before because it is not a toolbar key.
        minimizeKey.isVisible = showDone && context.prefs()
            .getBoolean(SHOW_MINIMIZE_KEY, DEFAULT_SHOW_MINIMIZE_KEY)
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
        if (width == 0) return
        // The same 2dp spectrum hairline the suggestion strip carries, and the
        // same place on screen — only its width moves with the voice. A meter
        // that also changed height would read as a progress bar.
        if (shaderWidth != width) {
            levelPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                SuggestionStripView.SPECTRUM, null, Shader.TileMode.CLAMP,
            )
            shaderWidth = width
        }
        val h = SuggestionStripView.RAIL_DP * resources.displayMetrics.density
        val half = width / 2f
        // Never fully collapsed: silence should read as a quiet line, not as a
        // keyboard that stopped listening.
        val span = half * (0.34f + 0.66f * level)
        levelPaint.alpha = 255
        canvas.drawRect(half - span, 0f, half + span, h, levelPaint)
    }

    companion object {
        /** Settings key for the minimize-while-listening control. */
        const val SHOW_MINIMIZE_KEY = "voice_show_minimize_key"
        const val DEFAULT_SHOW_MINIMIZE_KEY = false

        private const val SMOOTHING = 0.35f
        private const val BAR_INSET_PX = 4f
    }
}
