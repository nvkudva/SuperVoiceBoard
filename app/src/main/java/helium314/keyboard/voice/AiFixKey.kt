// SPDX-License-Identifier: GPL-3.0-only
//
// SuperVoiceBoard. New file: mounts :voice's AiFixController on HeliBoard's
// toolbar-key mechanism (W5.1) and shows what the model changed (W5.2).
package helium314.keyboard.voice

import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.ImageButton
import com.vboard.app.correct.AiFixController
import com.vboard.app.correct.FixSurface
import com.vboard.app.voice.VoiceRuntime
import com.vboard.core.correct.FixButtonState
import com.vboard.core.correct.FixEdit
import com.vboard.core.text.FieldKind
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.ToolbarKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    fun destroy() = controller.destroy()

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
        buttonState = state
        forEachKeyView { button ->
            button.contentDescription = contentDescription
            button.isEnabled = state != FixButtonState.DISABLED
            button.alpha = if (state == FixButtonState.DISABLED) DISABLED_ALPHA else 1f
        }
    }

    override fun showFixMessage(text: String) {
        // HeliBoard already has a place for transient keyboard-level messages,
        // and it is the one users of this keyboard are used to.
        KeyboardSwitcher.getInstance().showToast(text, true)
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
