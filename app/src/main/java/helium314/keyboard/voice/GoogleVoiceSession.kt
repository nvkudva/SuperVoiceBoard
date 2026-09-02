// SPDX-License-Identifier: GPL-3.0-only
//
// SuperVoiceBoard. New file: the opt-in Google dictation backend.
//
// This is the one path in the fork where speech leaves the device, so it is
// kept separate from the on-device session rather than hidden behind a flag
// inside it: nothing here runs unless PrivacyBreakingSettings.PREF_GOOGLE_VOICE
// is on. The audio never passes through this process — SpeechRecognizer records
// in Google's own process and hands back text.
package helium314.keyboard.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log

class GoogleVoiceSession(
    private val context: Context,
    private val host: Host,
) {
    /** What the controller has to be able to do for a session to be usable. */
    interface Host {
        fun onGooglePartial(text: String)
        fun onGoogleFinal(text: String)
        fun onGoogleAmplitude(rms: Float)
        fun onGoogleListening()
        fun onGooglePreparing()
        fun onGoogleFinalizing()
        fun onGoogleError(message: String)
        fun onGoogleEnded()
    }

    private var recognizer: SpeechRecognizer? = null

    /** True between start() and the session ending, whichever way it ends. */
    var isRunning = false
        private set

    /** Set while stopping, so a late error callback is not shown to the user. */
    private var stopping = false

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (isRunning) return
        if (!isAvailable()) {
            host.onGoogleError(context.getString(R.string.voice_google_unavailable))
            host.onGoogleEnded()
            return
        }
        isRunning = true
        stopping = false
        host.onGooglePreparing()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { this.recognizer = it }
        recognizer.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        runCatching { recognizer.startListening(intent) }
            .onFailure {
                Log.w(TAG, "Google recognizer refused to start", it)
                host.onGoogleError(context.getString(R.string.voice_google_unavailable))
                release()
            }
    }

    /** End the utterance and take whatever Google has: the "done" control. */
    fun stopAndFinalize() {
        if (!isRunning) return
        stopping = true
        host.onGoogleFinalizing()
        runCatching { recognizer?.stopListening() }
    }

    /** Drop the session and whatever it heard. */
    fun cancel() {
        if (!isRunning) return
        stopping = true
        runCatching { recognizer?.cancel() }
        release()
    }

    private fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        if (isRunning) {
            isRunning = false
            host.onGoogleEnded()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = host.onGoogleListening()
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            // SpeechRecognizer reports roughly -2..10 dB; the strip wants 0..1.
            host.onGoogleAmplitude(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = host.onGoogleFinalizing()

        override fun onError(error: Int) {
            if (!stopping || error != SpeechRecognizer.ERROR_NO_MATCH) {
                host.onGoogleError(context.getString(messageFor(error)))
            }
            release()
        }

        override fun onResults(results: Bundle?) {
            firstResult(results)?.let { host.onGoogleFinal(it) }
            release()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { host.onGooglePartial(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun firstResult(bundle: Bundle?): String? = bundle
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }

    private fun messageFor(error: Int) = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> R.string.voice_google_error_audio
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_google_error_permission
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_google_error_network
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_google_error_no_match
        else -> R.string.voice_google_error_generic
    }

    companion object {
        private const val TAG = "SVBGoogleVoice"
    }
}
