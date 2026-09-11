// SPDX-License-Identifier: GPL-3.0-only
//
// WaveKey. New file: mounts the AI fix controller on HeliBoard's
// toolbar-key mechanism (W5.1) and shows what the model changed (W5.2).
package helium314.keyboard.voice

import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.ImageButton
import helium314.keyboard.correct.AiFixController
import helium314.keyboard.correct.FixSurface
import com.vboard.app.voice.VoiceRuntime
import com.vboard.core.correct.FixButtonState
import com.vboard.core.correct.FixEdit
import com.vboard.core.text.FieldKind
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The AI fix key: one toolbar key that fixes the field, then offers an undo.
 *
 * VBoard drew its own toolbar and owned the button. Here the button is one of
 * HeliBoard's toolbar keys, wherever the user has put it — expanded row, pinned
 * to the strip, both — so this class finds every view tagged [ToolbarKey.AI_FIX]
 * and keeps them all in the same state.
 */
class AiFixKey(
    private val ime: LatinIME,
    runtime: VoiceRuntime,
    private val fieldKindProvider: () -> FieldKind,
) : FixSurface, AiFixController.Host {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val controller = AiFixController(
        context = ime,
        app = runtime,
        scope = scope,
        host = this,
    ).also { it.attach(this) }

    /** Where the key views live; re-read on every input view because it changes. */
    private var stripRoot: View? = null

    private var buttonState: FixButtonState = FixButtonState.IDLE

    fun setStripRoot(root: View?) {
        stripRoot = root
        controller.refresh()
    }

    fun onFixKeyPressed() = controller.onFixKeyPressed()

    fun onStartInput() = controller.onStartInput()

    fun onUserEdit() = controller.onUserEdit()

    fun onFinishInputView() = controller.onFinishInputView()

    fun destroy() {
        controller.destroy()
        scope.cancel()
    }

    // ------------------------------------------------- AiFixController.Host

    override fun inputConnection(): InputConnection? = ime.currentInputConnection

    override fun fieldKind(): FieldKind = fieldKindProvider()

    override fun onBeforeFieldRewrite() {
        // The field is about to be replaced wholesale, so the IME must stop
        // believing it is composing part of it — otherwise the next keystroke
        // reinstates a word that no longer exists.
        ime.currentInputConnection?.finishComposingText()
    }

    // ------------------------------------------------------------ FixSurface

    override fun updateFixButton(state: FixButtonState, contentDescription: String) {
        // WaveKey: a fix has just landed. Show the swap for a single-word
        // change; a wholesale reword has nothing a two-word ghost could say, and
        // the long-press attribution already accounts for those.
        if (state == FixButtonState.UNDO && buttonState == FixButtonState.RUNNING) {
            controller.editorialEdits()
                .firstOrNull { it.beforeText().isOneWord() && it.afterText().isOneWord() }
                ?.let { ime.showCorrectionGhost(it.beforeText(), it.afterText()) }
        }
        buttonState = state
        forEachKeyView { button ->
            button.contentDescription = contentDescription
            button.isEnabled = state != FixButtonState.DISABLED
            button.alpha = if (state == FixButtonState.DISABLED) DISABLED_ALPHA else 1f
            // Once a fix has landed the key is the way back out of it, so it
            // says undo rather than offering to fix the same text again.
            button.setImageResource(
                if (state == FixButtonState.UNDO) R.drawable.ic_ai_fix_undo
                else R.drawable.ic_ai_fix
            )
            // The same spectrum tile the mic wears: the two keys the fork adds
            // to the strip are the two that are not the keyboard's own, and they
            // say so together. State is left to the glyph and the alpha.
            button.setBackgroundResource(R.drawable.spectrum_tile)
            button.setColorFilter(SuggestionStripView.SPECTRUM_GLYPH)
            // ...and while the fix is being written, a light travels round the
            // edge. A fill that is already saturated cannot get brighter, so the
            // working state has to happen at the border.
            if (state == FixButtonState.RUNNING) {
                val res = button.resources
                val border = RunningBorderDrawable(
                    cornerRadius = 10f * res.displayMetrics.density,
                    strokeWidth = 2f * res.displayMetrics.density,
                    inset = res.getDimensionPixelSize(R.dimen.config_toolbar_key_inset).toFloat(),
                    colors = SuggestionStripView.SPECTRUM,
                )
                button.background = android.graphics.drawable.LayerDrawable(
                    arrayOf(button.background, border),
                ).apply {
                    // Nested padding would inset the ring by the tile's own
                    // inset a second time, shrinking it down onto the glyph.
                    paddingMode = android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK
                }
                border.start()
            }
        }
    }

    override fun showFixMessage(text: String) {
        // WaveKey: the key says what it is doing — lit, spinning, or showing the
        // undo glyph — and a toast over the keyboard on top of that is noise.
        // The message still reaches screen readers through the description.
        forEachKeyView { it.announceForAccessibility(text) }
    }

    override fun clearFixMessage() {
        // The toast expires on its own; nothing to cancel.
    }

    // ---------------------------------------------------------- attribution

    /**
     * W5.2: what the model actually changed, in the user's own words.
     *
     * Mechanical edits — casing, spacing, a doubled word — are not listed: they
     * are the kind of change a user can see at a glance and does not need
     * attributed. Editorial ones are the model substituting or rewording, and
     * those are exactly what a user is owed an account of.
     */
    fun attributionLines(): List<String> = controller.editorialEdits().map(::describe)

    private fun String.isOneWord(): Boolean =
        isNotBlank() && none { it.isWhitespace() }

    private fun describe(edit: FixEdit): String =
        ime.getString(R_ATTRIBUTION_FORMAT, edit.beforeText(), edit.afterText())

    private inline fun forEachKeyView(action: (ImageButton) -> Unit) {
        val root = stripRoot ?: return
        for (view in root.findViewsWithTag(ToolbarKey.AI_FIX)) {
            (view as? ImageButton)?.let(action)
        }
    }

    private fun View.findViewsWithTag(tag: Any): List<View> {
        val out = mutableListOf<View>()
        fun walk(view: View) {
            if (view.tag == tag) out.add(view)
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(this)
        return out
    }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
        val R_ATTRIBUTION_FORMAT = helium314.keyboard.latin.R.string.ai_fix_attribution_line
    }
}
