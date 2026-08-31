// SPDX-License-Identifier: GPL-3.0-only
//
// SuperVoiceBoard. New file: everything that binds the IME-agnostic :voice
// module to this particular keyboard lives here, so LatinIME's own edits stay
// down to lifecycle calls (PLAN.md §3.2).
package helium314.keyboard.voice

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import com.vboard.app.settings.SettingsRepository
import com.vboard.app.voice.VoiceErrorAction
import com.vboard.app.voice.VoiceRuntime
import com.vboard.app.voice.VoiceSessionController
import com.vboard.core.text.CommitPlanner
import com.vboard.core.text.FieldKind
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.Log

/**
 * Owns the dictation session for one IME instance.
 *
 * The session controller is deliberately ignorant of views and of HeliBoard; it
 * calls back into [VoiceSessionController.Host], and this class is the only
 * place that turns those calls into input-connection edits and strip state.
 */
class VoiceController(
    private val ime: LatinIME,
    private val runtime: VoiceRuntime,
) : VoiceSessionController.Host, VoiceStripView.Listener {

    private val session = VoiceSessionController(ime, runtime, this)

    /** Set by the IME when the strip is inflated; null before the view exists. */
    var strip: VoiceStripView? = null
        set(value) {
            field = value
            value?.listener = this
        }

    /** What the IME shows instead of the voice strip when dictation ends. */
    var onSessionUiEnded: (() -> Unit)? = null
    var onSessionUiStarted: (() -> Unit)? = null

    @Volatile
    var isActive = false
        private set

    private var fieldKind: FieldKind = FieldKind.TEXT

    /**
     * Committed utterances of this session, by index, so a later refinement can
     * replace exactly what it refined rather than guessing at the cursor.
     */
    private val commits = HashMap<Int, String>()

    // --------------------------------------------------------------- IME hooks

    fun onStartInputView(editorInfo: EditorInfo?) {
        fieldKind = fieldKindOf(editorInfo)
        // A field that must never be dictated into ends any session that was
        // running when focus moved into it.
        if (!fieldKind.allowsVoice && isActive) cancel()
    }

    /** The editor is going away: finalize rather than discard what was said. */
    fun onFinishInputView() {
        if (isActive) session.finishSession()
    }

    fun onDestroy() {
        session.destroy()
        strip = null
    }

    /** The mic key, and the VOICE toolbar key, both land here. */
    fun toggle() {
        if (isActive) session.stopAndFinalize() else start()
    }

    fun start() {
        if (!fieldKind.allowsVoice) return
        isActive = true
        commits.clear()
        strip?.reset()
        onSessionUiStarted?.invoke()
        strip?.announceSessionStarted()
        session.startSession(fieldKind, runtime.settings.snapshot())
    }

    fun cancel() {
        if (!isActive) return
        session.cancelSession()
    }

    // ------------------------------------------------- VoiceStripView.Listener

    override fun onVoiceCancel() = cancel()

    override fun onVoiceDone() {
        if (isActive) session.stopAndFinalize()
    }

    override fun onVoiceMinimizeKeyboard() {
        // The session keeps running; only the keyboard window goes away. That is
        // the point of the control — dictating into a field you need to see.
        ime.requestHideSelf(0)
    }

    override fun onVoiceErrorAction(action: VoiceErrorAction) {
        when (action) {
            VoiceErrorAction.OPEN_PERMISSION -> openAppSettings()
            VoiceErrorAction.OPEN_DOWNLOAD -> openVoiceSettings()
            VoiceErrorAction.DISMISS -> cancel()
        }
    }

    // ------------------------------------------ VoiceSessionController.Host

    override fun precedingText(): String =
        ime.currentInputConnection?.getTextBeforeCursor(PRECEDING_CHARS, 0)?.toString() ?: ""

    override fun fieldKind(): FieldKind = fieldKind

    override fun updatePartial(text: String) {
        strip?.showPartial(text)
    }

    override fun commitUtterance(index: Int, text: String) {
        // The field may have changed under an asynchronous final pass — an app
        // toggling password visibility mid-dictation does exactly that.
        if (!fieldKind.allowsVoice) return
        val ic = ime.currentInputConnection ?: run {
            Log.w(TAG, "input connection gone before the final pass; utterance not committed")
            return
        }
        val joined = CommitPlanner.joinForInsertion(precedingText(), text)
        ic.commitText(joined, 1)
        commits[index] = joined
    }

    override fun replaceUtterance(index: Int, newText: String) {
        val previous = commits[index] ?: return
        val ic = ime.currentInputConnection ?: return
        val joined = CommitPlanner.joinForInsertion(
            ic.getTextBeforeCursor(PRECEDING_CHARS + previous.length, 0)
                ?.toString()?.dropLast(previous.length) ?: "",
            newText,
        )
        ic.beginBatchEdit()
        ic.deleteSurroundingText(previous.length, 0)
        ic.commitText(joined, 1)
        ic.endBatchEdit()
        commits[index] = joined
    }

    override fun deleteLastUtterance() {
        val index = commits.keys.maxOrNull() ?: return
        val text = commits.remove(index) ?: return
        ime.currentInputConnection?.deleteSurroundingText(text.length, 0)
    }

    override fun onSessionEnded() {
        isActive = false
        strip?.announceSessionEnded()
        strip?.reset()
        onSessionUiEnded?.invoke()
    }

    override fun showError(message: String, action: VoiceErrorAction) {
        strip?.showError(message, action)
    }

    override fun showPreparing() {
        strip?.showPreparing()
    }

    override fun showListening() {
        strip?.showListening()
    }

    override fun showFinalizing() {
        strip?.showFinalizing()
    }

    override fun showRefining() {
        strip?.showRefining()
    }

    override fun onAmplitude(rms: Float) {
        strip?.onAmplitude(rms)
    }

    // ------------------------------------------------------------------ misc

    private fun openAppSettings() {
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", ime.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ime.startActivity(intent) }
            .onFailure { Log.w(TAG, "could not open app settings", it) }
        cancel()
    }

    private fun openVoiceSettings() {
        val intent = ime.packageManager.getLaunchIntentForPackage(ime.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) runCatching { ime.startActivity(intent) }
        cancel()
    }

    private fun fieldKindOf(editorInfo: EditorInfo?): FieldKind {
        val inputType = editorInfo?.inputType ?: return FieldKind.TEXT
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME ->
                FieldKind.NUMBER
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                -> FieldKind.PASSWORD
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                -> FieldKind.EMAIL
                InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URI
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> FieldKind.TEXT
                else -> if (editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEARCH)
                    FieldKind.SEARCH else FieldKind.TEXT
            }
            else -> FieldKind.TEXT
        }
    }

    companion object {
        private const val TAG = "SVBVoice"
        /** Enough context for spacing and capitalization decisions, no more. */
        private const val PRECEDING_CHARS = 16
    }
}
