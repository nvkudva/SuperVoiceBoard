// SPDX-License-Identifier: GPL-3.0-only
//
// SuperVoiceBoard. New file: everything that binds the IME-agnostic :voice
// module to this particular keyboard lives here, so LatinIME's own edits stay
// down to lifecycle calls (PLAN.md §3.2).
package helium314.keyboard.voice

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import com.vboard.app.settings.SettingsRepository
import com.vboard.app.voice.VoiceEngines
import com.vboard.app.voice.VoiceErrorAction
import com.vboard.app.voice.VoiceRuntime
import com.vboard.app.voice.VoiceSessionController
import com.vboard.core.session.VoiceMetrics
import com.vboard.core.text.CommitPlanner
import com.vboard.core.text.FieldKind
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.PrivacyBreakingSettings

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
) : VoiceSessionController.Host, VoiceStripView.Listener, GoogleVoiceSession.Host {

    private val session = VoiceSessionController(ime, runtime, this)

    /**
     * The opt-in Google backend. Created lazily so a user who never turns the
     * setting on never has a SpeechRecognizer in their keyboard process.
     */
    private val googleSession by lazy { GoogleVoiceSession(ime, this) }

    /** Which backend owns the running session; decided at start() and kept. */
    private var googleForSession = false

    private fun googleBackendEnabled() =
        PrivacyBreakingSettings.googleVoiceEnabled(ime.prefs())

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

    /** The app being dictated into, captured when the session starts (W4.3). */
    private var sessionPackage: String? = null

    /** True while a press-and-hold owns the session; release ends it (W6.3). */
    private var holdScoped = false

    /** Session-scoped raw dictation: no cleanup, no refinement (W6.4). */
    private var rawForSession = false

    /**
     * W7.3: opt-in, content-free measurement. Held in memory for this IME
     * instance only — there is no endpoint to send it to and no file to write
     * it into; the user's own settings screen is the only reader (PLAN.md R24).
     */
    private val metrics = VoiceMetrics()

    /** When the session that is currently committing started, for the mean. */
    private var sessionStartedAt = 0L

    /**
     * The last dictated commit, awaiting its verdict: it counts as send-ready
     * only once the user has moved on without editing it. Held as a duration,
     * not as text.
     */
    private var pendingElapsedMs: Long? = null

    /** Aggregates for the settings screen; empty when telemetry is off. */
    fun metricsSnapshot(): VoiceMetrics.Snapshot = metrics.snapshot()

    /**
     * An utterance whose input connection died before the final pass returned.
     * In memory only, replayed into the next editor of the same app and nowhere
     * else — see [PendingDictation].
     */
    private var pending: PendingDictation? = null

    /**
     * Committed utterances of this session, by index, so a later refinement can
     * replace exactly what it refined rather than guessing at the cursor.
     */
    private val commits = HashMap<Int, String>()

    // --------------------------------------------------------------- IME hooks

    fun onStartInputView(editorInfo: EditorInfo?) {
        // Moving to another field settles whatever was awaiting a verdict.
        settleTelemetry()
        fieldKind = fieldKindOf(editorInfo)
        // A field that must never be dictated into ends any session that was
        // running when focus moved into it.
        if (!fieldKind.allowsVoice && isActive) cancel()
        replayPending(editorInfo?.packageName)
    }

    /**
     * W4.3 draft rescue: hand a held utterance to the editor that just opened,
     * if it is plausibly the same piece of work the user was speaking into.
     *
     * The rule is deliberately narrow — same app, within the TTL, field still
     * accepts voice — because replaying speech into the *next* app's field would
     * be a worse bug than the data loss it fixes.
     */
    private fun replayPending(editorPackage: String?) {
        val held = pending ?: return
        val verdict = held.verdictFor(editorPackage, fieldKind.allowsVoice, SystemClock.elapsedRealtime())
        if (verdict != ReplayVerdict.REPLAY) {
            // Never logs the text, its length, or anything derived from it.
            Log.i(TAG, "held utterance not replayed: $verdict")
            if (verdict == ReplayVerdict.EXPIRED) pending = null
            return
        }
        val ic = ime.currentInputConnection ?: return
        pending = null
        ic.commitText(CommitPlanner.joinForInsertion(precedingText(), held.text), 1)
        Log.i(TAG, "held utterance replayed into the reopened editor")
    }

    /** The editor is going away: finalize rather than discard what was said. */
    fun onFinishInputView() {
        if (isActive) session.finishSession()
    }

    fun onDestroy() {
        session.destroy()
        strip = null
    }

    /**
     * W4.4: a touch outside the keyboard ends the utterance rather than leaving
     * the mic open. The user has moved on — to another field, a send button, the
     * app's own UI — and anything already said should land, not keep recording.
     */
    fun onTouchOutsideKeyboard() {
        if (isActive) session.stopAndFinalize()
    }

    /** The mic key, and the VOICE toolbar key, both land here. */
    /**
     * Starts loading the models without starting a session.
     *
     * Called on the mic's touch-down and when the keyboard opens with models
     * installed: model load is seconds on a cold press, and doing it during the
     * gesture is time the user was going to spend anyway.
     */
    fun warmUp() {
        if (!fieldKind.allowsVoice) return
        // The Google backend has no local engines to warm.
        if (googleBackendEnabled()) return
        VoiceEngines.warmUp(runtime)
    }

    /** The keyboard is visible: do not release engines out from under it. */
    fun onKeyboardShown() {
        VoiceEngines.cancelIdleRelease()
    }

    /** The keyboard is gone: the engines may be reclaimed after the idle delay. */
    fun onKeyboardHidden() {
        VoiceEngines.scheduleIdleRelease()
    }

    fun toggle() {
        if (isActive) stopAndFinalize() else start()
    }

    /** End the utterance on whichever backend is running it. */
    private fun stopAndFinalize() {
        if (googleForSession) googleSession.stopAndFinalize() else session.stopAndFinalize()
    }

    /**
     * W6.3: press-and-hold started. Release ends the utterance and sends it, so
     * the session is marked as hold-scoped; a tap-started session is not.
     *
     * W6.4: [raw] is the deeper hold — this session bypasses cleanup and
     * refinement entirely and types what was heard, verbatim. It is scoped to
     * this session only; the setting is not touched.
     */
    fun startHold(raw: Boolean) {
        if (raw) {
            // The hold escalated mid-session: keep listening, drop the cleanup.
            rawForSession = true
            if (isActive) return
        }
        holdScoped = true
        // W6.5: the finger is the endpoint while it is down.
        session.setEndpointingEnabled(false)
        start()
    }

    /** W6.3: the finger lifted — finalize and send what was said. */
    fun endHold() {
        if (!holdScoped) return
        holdScoped = false
        session.setEndpointingEnabled(true)
        if (isActive) stopAndFinalize()
    }

    fun start() {
        if (!fieldKind.allowsVoice) return
        if (!holdScoped) session.setEndpointingEnabled(true)
        isActive = true
        sessionPackage = ime.currentInputEditorInfo?.packageName
        settleTelemetry()
        sessionStartedAt = SystemClock.elapsedRealtime()
        commits.clear()
        strip?.reset()
        onSessionUiStarted?.invoke()
        strip?.announceSessionStarted()
        googleForSession = googleBackendEnabled()
        if (googleForSession) {
            googleSession.start()
            return
        }
        val settings = runtime.settings.snapshot().let {
            // W6.4: a raw hold overrides the settings for this session only.
            if (rawForSession) it.copy(rawTranscriptMode = true, llmRefineEnabled = false) else it
        }
        session.startSession(fieldKind, settings)
    }

    fun cancel() {
        if (!isActive) return
        if (googleForSession) googleSession.cancel() else session.cancelSession()
    }

    // ------------------------------------------------- VoiceStripView.Listener

    override fun onVoiceCancel() = cancel()

    override fun onVoiceDone() {
        if (isActive) stopAndFinalize()
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
            // W4.3: the editor went away before the final pass returned. The
            // speech is held rather than dropped; the next editor of the same
            // app gets it.
            pending = PendingDictation.hold(
                pending, text, sessionPackage, SystemClock.elapsedRealtime(),
            )
            Log.w(TAG, "no input connection; dictated utterance $index held for replay")
            return
        }
        val joined = CommitPlanner.joinForInsertion(precedingText(), text)
        ic.commitText(joined, 1)
        commits[index] = joined
        // W7.3: the verdict is not known yet — the user may still edit this.
        settleTelemetry()
        pendingElapsedMs = SystemClock.elapsedRealtime() - sessionStartedAt
    }

    /**
     * W7.3: the user typed or deleted after a dictated commit, so that utterance
     * was not send-ready. Only the verdict and the duration are recorded.
     */
    fun onUserEditedDictation() {
        val elapsed = pendingElapsedMs ?: return
        pendingElapsedMs = null
        if (runtime.settings.snapshot().telemetryEnabled) {
            metrics.record(edited = true, elapsedMs = elapsed)
        }
    }

    /** The pending utterance survived unedited: count it and forget it. */
    private fun settleTelemetry() {
        val elapsed = pendingElapsedMs ?: return
        pendingElapsedMs = null
        if (runtime.settings.snapshot().telemetryEnabled) {
            metrics.record(edited = false, elapsedMs = elapsed)
        }
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
        googleForSession = false
        holdScoped = false
        rawForSession = false
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

    // ------------------------------------------- GoogleVoiceSession.Host

    // The Google backend returns finished text, so it joins the same commit and
    // strip paths as the on-device one and skips only the parts that describe
    // the local pipeline: no cleanup settings, no refinement, no utterance
    // indexing beyond the single result Google gives back.

    override fun onGooglePartial(text: String) = updatePartial(text)

    override fun onGoogleFinal(text: String) = commitUtterance(0, text)

    override fun onGoogleAmplitude(rms: Float) = onAmplitude(rms)

    override fun onGoogleListening() = showListening()

    override fun onGooglePreparing() = showPreparing()

    override fun onGoogleFinalizing() = showFinalizing()

    override fun onGoogleError(message: String) = showError(message, VoiceErrorAction.DISMISS)

    override fun onGoogleEnded() = onSessionEnded()

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
