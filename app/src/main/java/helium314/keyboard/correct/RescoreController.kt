// SPDX-License-Identifier: GPL-3.0-only
//
// SuperVoiceBoard. Part of the removable correction package: this class, the
// ghost view beside it, and the two calls into them are the whole feature.
package helium314.keyboard.correct

import com.vboard.app.voice.VoiceEngines
import com.vboard.app.voice.VoiceRuntime
import com.vboard.core.correct.Replacement
import com.vboard.core.correct.RescoreDecision
import com.vboard.core.correct.WordSlot
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gives a finished sentence a second opinion, and applies it only where that
 * opinion agrees with a word the decoder already ranked.
 *
 * The model is asked for a corrected sentence — the call the refiner already
 * exposes — and [RescoreDecision] then throws away everything in the reply that
 * was not on the decoder's own shortlist for that position. So the pass can
 * reorder the decoder's judgement and can never introduce a word the user's
 * keystrokes did not imply.
 *
 * Every failure path is "leave the text alone": no connection, no model, a
 * timeout, a reply that does not line up, or a field that changed underneath us
 * all end the same way.
 */
class RescoreController(
    private val ime: LatinIME,
    private val runtime: VoiceRuntime,
    private val scope: CoroutineScope,
) {

    @Volatile
    private var running = false

    /**
     * A sentence just ended. Runs in the background; the caller is on the input
     * path and must not wait for a model.
     */
    fun onSentenceComplete(slots: List<WordSlot>) {
        if (running || slots.isEmpty()) return
        running = true
        scope.launch {
            try {
                rescore(slots)
            } finally {
                running = false
            }
        }
    }

    private suspend fun rescore(slots: List<WordSlot>) {
        val typed = slots.joinToString(" ") { it.committed }
        // The tail as it stood when we asked. If it has changed by the time the
        // answer arrives, the user has moved on and the answer is stale.
        val tailAtRequest = tail(typed.length) ?: return

        val rescored = withContext(Dispatchers.Default) {
            val refiner = runCatching { VoiceEngines.loadRefiner(ime, runtime) }
                .onFailure { Log.w(TAG, "refiner unavailable for rescoring", it) }
                .getOrNull() ?: return@withContext null
            VoiceEngines.beginUse()
            try {
                withTimeoutOrNull(TIMEOUT_MS) { refiner.correct(typed).text() }
            } finally {
                VoiceEngines.endUse()
            }
        } ?: return

        val accepted = RescoreDecision.accept(slots, rescored)
        if (accepted.isEmpty()) return
        apply(slots, accepted, tailAtRequest)
    }

    /**
     * Rewrites the shortest run of text that contains every accepted swap: from
     * the first replaced word to the end of the sentence.
     *
     * Deliberately one delete and one commit rather than a span edit per word.
     * An IME cannot address text by position in someone else's field without
     * absolute offsets it has no reliable way to hold across an async gap, and
     * two round trips are cheaper than four.
     */
    private suspend fun apply(
        slots: List<WordSlot>,
        accepted: List<Replacement>,
        tailAtRequest: String,
    ) = withContext(Dispatchers.Main.immediate) {
        val ic = ime.currentInputConnection ?: return@withContext
        val words = slots.map { it.committed }.toMutableList()
        val firstChanged = accepted.first().index

        // The run we are about to replace, exactly as we believe it stands.
        val existing = words.subList(firstChanged, words.size).joinToString(" ")
        val current = ic.getTextBeforeCursor(tailAtRequest.length + TRAILING_SLACK, 0)?.toString()
        // Someone typed, deleted or moved while the model was thinking, so the
        // answer describes text that is no longer there.
        if (current != tailAtRequest) return@withContext
        val start = current.lastIndexOf(existing)
        if (start < 0) return@withContext
        // Whatever sits between those words and the cursor — the terminator, and
        // the space after it — is put back untouched.
        val trailing = current.substring(start + existing.length)

        for (replacement in accepted) words[replacement.index] = replacement.to
        val rebuilt = words.subList(firstChanged, words.size).joinToString(" ")

        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(existing.length + trailing.length, 0)
            ic.commitText(rebuilt + trailing, 1)
        } finally {
            ic.endBatchEdit()
        }

        // One ghost, for the first swap: two at once is a glitch, not a report.
        accepted.firstOrNull()?.let { ime.showCorrectionGhost(it.from, it.to) }
    }

    private suspend fun tail(length: Int): String? = withContext(Dispatchers.Main.immediate) {
        ime.currentInputConnection
            ?.getTextBeforeCursor(length + TRAILING_SLACK, 0)
            ?.toString()
    }

    companion object {
        /**
         * Off by default: the pass spends a model call per sentence, and nothing
         * about typing should cost that without being asked for.
         */
        const val PREF_RESCORE_SENTENCES = "pref_rescore_sentences"

        private const val TAG = "SVBRescore"

        /** Shorter than the AI fix budget: this runs unasked, so it must not be felt. */
        private const val TIMEOUT_MS = 1_200L

        /** Room for the terminator and the space that follows it. */
        private const val TRAILING_SLACK = 4
    }
}
