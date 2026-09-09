package com.vboard.core.correct

/**
 * One word the rescoring pass wants to swap.
 *
 * @property index position in the sentence, as [SentenceCandidates] recorded it.
 */
data class Replacement(
    val index: Int,
    val from: String,
    val to: String,
)

/**
 * Turns what the model said back into the only thing we are willing to act on:
 * a short list of words the decoder had already ranked.
 *
 * The model is handed a whole sentence and hands one back, because that is the
 * call the refiner already exposes. Nothing here trusts that reply. A word is
 * swapped only when the model's version of it appears in the alternatives the
 * decoder itself produced for that position, so the pass can reorder the
 * decoder's own judgement and can never introduce a word the user's keystrokes
 * did not imply. Everything else in the reply is discarded.
 */
object RescoreDecision {

    /**
     * More than this many changes in one sentence is a rewrite, not a rescore,
     * and a rewrite is what the AI fix key is for.
     */
    const val MAX_REPLACEMENTS = 3

    /**
     * @param slots the sentence as it was typed, with the alternatives each word beat.
     * @param rescored the model's version of the same sentence.
     * @return the swaps worth making, in sentence order; empty when the reply is
     *   unusable, which is the safe answer and the common one.
     */
    fun accept(slots: List<WordSlot>, rescored: String): List<Replacement> {
        if (slots.isEmpty() || rescored.isBlank()) return emptyList()

        val words = rescored.trim().split(WHITESPACE)
        // A reply that lost or gained a word is not aligned with what we recorded,
        // and a misaligned swap would put the right word in the wrong place.
        if (words.size != slots.size) return emptyList()

        val accepted = ArrayList<Replacement>()
        for ((index, slot) in slots.withIndex()) {
            val candidate = words[index].trim(*TRIMMED)
            if (candidate.isEmpty() || candidate == slot.committed) continue
            // The whole guarantee lives on this line.
            if (candidate !in slot.alternatives) continue
            accepted.add(Replacement(index, slot.committed, candidate))
            if (accepted.size > MAX_REPLACEMENTS) return emptyList()
        }
        return accepted
    }

    private val WHITESPACE = Regex("\\s+")

    /**
     * Punctuation the model may have attached to a word that the decoder never
     * saw as part of it. Stripped for comparison only.
     */
    private val TRIMMED = charArrayOf(
        '.', ',', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '…', '।', '。', '！', '？',
    )
}
