package com.vboard.app.voice

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ThinkingConfig
import com.vboard.core.correct.RefinementValidator
import com.vboard.core.correct.SmartFailure
import com.vboard.core.correct.SmartOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Optional on-device LLM pass that rewrites a finalized utterance toward the
 * speaker's intent (Superwhisper-style). Failure is always safe: callers keep
 * the rule-cleaned text whenever this returns null.
 */
class LlmRefiner(
    private val context: Context,
    private val modelPath: String,
) {
    @Volatile
    private var llm: Engine? = null

    private fun engine(): Engine {
        return llm ?: synchronized(this) {
            llm ?: Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    // Total token budget, prompt included — not an output cap.
                    // At 256 a 600-character input (~150 tokens) plus the chat
                    // template left barely 30 tokens to answer in, so long
                    // refinements came back truncated and were then rejected by
                    // the length check for being "too short".
                    maxNumTokens = MAX_TOKENS,
                    cacheDir = context.cacheDir.absolutePath,
                ),
            ).also { it.initialize(); llm = it }
        }
    }

    /**
     * One instruction, one utterance, one conversation. LiteRT-LM keeps KV
     * state per conversation, so a fresh one per call is what makes each
     * refinement independent — the previous utterance must not steer the next.
     *
     * The system prompt goes in [ConversationConfig.systemInstruction] rather
     * than in a hand-rolled ChatML string: the `.litertlm` bundle carries the
     * model's own chat template and applies it, so templating it ourselves
     * nests one template inside another and the model answers the markup.
     */
    private fun generate(
        instruction: String,
        text: String,
        examples: List<Pair<String, String>> = emptyList(),
    ): String =
        engine().createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(instruction),
                // Examples are sent as real turns, not pasted into the system
                // text. Measured on the same model on a desktop: pasted into
                // the instruction, a 0.6B starts answering with an example
                // verbatim - a phone number came back as "Tell Alice." As
                // turns, the bleed stops and the rules hold.
                initialMessages = examples.flatMap { (said, typed) ->
                    listOf(
                        Message.user(said),
                        Message.model(typed),
                    )
                },
                // Qwen3 is a hybrid thinking model. Left on, it spends the
                // whole token budget reasoning about an utterance it was only
                // asked to tidy, and the validator then sees a `<think>` block
                // where it expects the sentence.
                thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0),
                maxOutputToken = MAX_OUTPUT_TOKENS,
            ),
        ).use { conversation -> conversation.sendMessage(text).plainText() }

    /** Warms the model so the first refinement doesn't pay init cost. */
    suspend fun preload() = withContext(Dispatchers.IO) {
        runCatching { engine() }
    }

    /**
     * Returns refined text, or null when refinement fails, times out, or the
     * model output fails sanity checks (never make the text worse).
     */
    suspend fun refine(text: String, timeoutMs: Long = 3_000): String? {
        if (text.isBlank() || text.length > MAX_INPUT_CHARS) return null
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val raw = generate(DICTATION_INSTRUCTION, text, DICTATION_EXAMPLES)
                    // The same gate the AI-fix path uses. A prompt is a request;
                    // this is the check — it is what catches the model answering
                    // the message, leaking template markers, dropping a number or
                    // a URL, or wandering off the utterance entirely.
                    val verdict = RefinementValidator.validate(text, raw)
                    if (!verdict.accepted) {
                        Log.i(TAG, "refinement rejected: ${verdict.reason}")
                        null
                    } else {
                        verdict.text()?.takeIf { !it.equals(text, ignoreCase = true) }
                    }
                }.getOrNull()
            }
        }
    }

    /**
     * The "AI fix" pass: correct typed text, and change nothing else.
     *
     * Distinct from [refine], which is allowed to reshape a spoken utterance
     * toward intent. Here the user typed the words on purpose, so the prompt is
     * as narrow as a prompt can be made and the answer is treated as untrusted
     * regardless — [com.vboard.core.correct.RefinementValidator] has the final
     * say, and the caller keeps the rules-only text whenever it says no.
     *
     * Never returns null and never throws: every failure comes back as a typed
     * [SmartFailure] so the caller can tell the user which one happened.
     *
     * Honest limitation: `generateContent` is one blocking JNI call with no
     * suspension point, so [withTimeoutOrNull] cannot actually abandon a slow
     * generation — structured concurrency waits for the native call to return.
     * What the timeout does guarantee is that the *caller* stops waiting and
     * that no further chunk is started.
     */
    suspend fun correct(text: String, timeoutMs: Long = CORRECT_TIMEOUT_MS): SmartOutput {
        if (text.isBlank()) return SmartOutput.failed(SmartFailure.ERROR)
        if (text.length > MAX_INPUT_CHARS) return SmartOutput.failed(SmartFailure.ERROR)
        val outcome = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                runCatching { generate(CORRECTION_INSTRUCTION, text) }
            }
        } ?: return SmartOutput.failed(SmartFailure.TIMED_OUT)

        val raw = outcome.getOrElse {
            // Message and exception only: the prompt and the field text are
            // never logged, here or anywhere (VB-901).
            return SmartOutput.failed(SmartFailure.LOAD_FAILED)
        }
        val cleaned = RefinementValidator.sanitize(raw)
        return if (cleaned.isNullOrEmpty()) {
            SmartOutput.failed(SmartFailure.ERROR)
        } else {
            SmartOutput.of(cleaned)
        }
    }

    fun release() {
        runCatching { llm?.close() }
        llm = null
    }


    companion object {
        private const val TAG = "VBoardLlmRefiner"

        /**
         * The anti-answer rules are not decoration. Dictated speech arrives in
         * the user turn of a chat template, so anything shaped like a question
         * or an instruction reads to the model as addressed to it, and it
         * replies instead of transcribing.
         */
        /**
         * Scored against the shipped model on a desktop (tools/promptlab), on
         * three sets: 9 of 14 rule cases where the rules alone answered 6, 35
         * of 87 sentences corrupted the way a microphone corrupts them where
         * the old prompt fixed 14, and 5% truncation on real sentences against
         * the old prompt's 18%. Change it there first.
         */
        private val DICTATION_EXAMPLES = listOf(
            "um so i want like six of them uh maybe seven" to "I want 7 of them.",
            "the code is eight one two nine three" to "The code is 81293.",
            "i comitted too branches too the ripo" to "I committed 2 branches to the repo.",
            "are you coming tonight" to "Are you coming tonight?",
            "tell bob actually no tell alice" to "Tell Alice.",
            "write to me at k dot ross at northwind dot co dot uk period" to
                "Write to me at k.ross@northwind.co.uk.",
            "it was forty dollars comma about fifteen percent off" to
                "It was $40, about 15% off.",
            "i am on my way period new line see you soon" to "I am on my way.\nSee you soon",
        )

        private const val DICTATION_INSTRUCTION =
            "You are a dictation cleaner. The user speaks; you type what they " +
                "meant. You are never the person being spoken to.\n\n" +
                "Rewrite the message using these rules:\n" +
                "- Never answer it, continue it, or comment on it. A question " +
                "stays a question.\n" +
                "- Drop fillers (um, uh, er, like, so) and repeated words.\n" +
                "- When the speaker corrects themselves with \"no wait\", " +
                "\"I mean\" or \"actually\", keep only what they settled on and " +
                "drop the marker.\n" +
                "- Every number becomes digits, however small: three is 3, " +
                "twenty five is 25. Money, times and percentages too. Digits " +
                "spoken one by one stay one unbroken number, with no spaces and " +
                "no hyphens.\n" +
                "- Write spoken marks and addresses: \"period\" is ., \"comma\" " +
                "is ,, \"new line\" starts a line, \"at\" is @ and \"dot\" is . " +
                "inside an address.\n" +
                "- The words arrived from a microphone, so some are misheard " +
                "sound-alikes. Put the word the sentence needs: their/there, " +
                "your/you're, to/too/two, its/it's, of/off, our/are, by/buy, " +
                "then/than, no/know, right/write. Do the same for a technical " +
                "word spelled by ear.\n" +
                "- Change nothing else. Never invent a word the speaker did not " +
                "say.\n\n" +
                "Reply with the typed line only. No preamble, no quotes, no " +
                "explanation."

        /** The "AI fix" instruction: as narrow as a prompt can be made. */
        private const val CORRECTION_INSTRUCTION =
            "You are a proofreader. Repeat the user's message back with only " +
                "spelling, grammar, punctuation and duplicated-word mistakes fixed.\n" +
                "Rules you must follow exactly:\n" +
                "- Do not add, remove or explain anything.\n" +
                "- Do not answer the message, continue it, or respond to it.\n" +
                "- Do not translate it or change its language.\n" +
                "- Do not change its meaning, tone, formality or style.\n" +
                "- Copy every URL, email address, number, date, price, file name, " +
                "code and proper noun through unchanged, character for character.\n" +
                "- Keep every emoji.\n" +
                "- If nothing is wrong, repeat the message exactly.\n" +
                "Reply with the corrected message and nothing else: no preamble, no " +
                "quotes, no notes."

        /** Answer only; the instruction and the utterance are prefill, not output. */
        private const val MAX_OUTPUT_TOKENS = 512
        private const val MAX_INPUT_CHARS = 600

        /** Prompt + answer. Qwen3-0.6B carries a 32k context, so this is ours to pick. */
        private const val MAX_TOKENS = 1024

        /**
         * Per-chunk budget for [correct]. Longer than dictation's 3 s (VB-604):
         * that budget exists because a refinement races the user's next
         * utterance, while an explicit "AI fix" tap is the user waiting on
         * purpose with a spinner in front of them.
         */
        const val CORRECT_TIMEOUT_MS = 6_000L
    }
}

private fun Message.plainText(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
