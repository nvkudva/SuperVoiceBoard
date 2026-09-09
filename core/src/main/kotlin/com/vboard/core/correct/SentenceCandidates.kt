package com.vboard.core.correct

/**
 * One committed word and the alternatives the decoder had offered for it.
 *
 * [alternatives] never contains [committed] and is ordered as the decoder ranked
 * it, best first. A slot with no alternatives is a word nothing could be said
 * about; only slots with at least one alternative are worth a second opinion.
 */
data class WordSlot(
    val committed: String,
    val alternatives: List<String>,
) {
    val isAmbiguous: Boolean get() = alternatives.isNotEmpty()
}

/**
 * Remembers what the n-gram decoder *nearly* typed, for the length of one
 * sentence.
 *
 * The decoder ranks several candidates per word and then throws all but the
 * winner away at commit time. Rescoring needs the losers: the whole point is to
 * let a second opinion choose among words the decoder already considered, so it
 * can never introduce a word the user's own typing did not imply.
 *
 * Deliberately free of any model, any Android type and any I/O. It holds words
 * the user typed, so it holds them in memory, hands them to exactly one caller
 * on sentence end, and forgets them — nothing here logs, persists or counts
 * anything derived from the text itself.
 */
class SentenceCandidates(
    private val maxWords: Int = DEFAULT_MAX_WORDS,
    private val maxAlternatives: Int = DEFAULT_MAX_ALTERNATIVES,
) {

    private val slots = ArrayList<WordSlot>()

    /**
     * Set when a sentence ran past [maxWords]. Such a sentence is abandoned
     * rather than truncated: half a sentence is worse context than none, and a
     * long one is exactly where the latency budget does not fit.
     */
    private var overflowed = false

    private var sentencesSeen = 0
    private var sentencesWorthScoring = 0
    private var ambiguousSlots = 0

    val size: Int get() = slots.size

    /**
     * Records a committed word and returns the finished sentence when
     * [separator] ended one, or null while it is still being typed.
     *
     * The returned list is the caller's; this object keeps nothing.
     */
    fun record(committed: String, alternatives: List<String>, separator: String): List<WordSlot>? {
        if (committed.isNotEmpty()) {
            if (slots.size >= maxWords) {
                overflowed = true
            } else {
                slots.add(
                    WordSlot(
                        committed = committed,
                        alternatives = alternatives
                            .asSequence()
                            .filter { it.isNotEmpty() && it != committed }
                            .distinct()
                            .take(maxAlternatives)
                            .toList(),
                    )
                )
            }
        }
        return if (endsSentence(separator)) finish() else null
    }

    /**
     * Abandons the sentence in progress. Called whenever the text stopped being
     * a sentence this object can reason about: the cursor moved, the field
     * changed, the user deleted into what was already committed.
     */
    fun reset() {
        slots.clear()
        overflowed = false
    }

    fun stats(): Stats = Stats(
        sentencesSeen = sentencesSeen,
        sentencesWorthScoring = sentencesWorthScoring,
        ambiguousSlots = ambiguousSlots,
    )

    private fun finish(): List<WordSlot>? {
        val finished = slots.toList()
        val abandoned = overflowed
        reset()
        if (finished.isEmpty()) return null
        sentencesSeen++
        if (abandoned) return null
        val ambiguous = finished.count { it.isAmbiguous }
        if (ambiguous == 0) return null
        sentencesWorthScoring++
        ambiguousSlots += ambiguous
        return finished
    }

    /**
     * How often a second opinion would have had anything to choose between.
     *
     * Counts only. It exists to answer "is rescoring worth its latency on this
     * user's typing?" before any model is wired up, and it can answer that
     * without holding a single word.
     */
    data class Stats(
        val sentencesSeen: Int,
        val sentencesWorthScoring: Int,
        val ambiguousSlots: Int,
    ) {
        /** Share of sentences where rescoring would have had a choice to make. */
        val worthScoringRate: Double
            get() = if (sentencesSeen == 0) 0.0 else sentencesWorthScoring.toDouble() / sentencesSeen
    }

    companion object {
        private const val DEFAULT_MAX_WORDS = 30
        private const val DEFAULT_MAX_ALTERNATIVES = 4

        /**
         * Terminators across the scripts this keyboard ships layouts for, not
         * just the Latin three: a Hindi or Japanese sentence ends too.
         */
        private val TERMINATORS = setOf('.', '!', '?', '\n', '…', '।', '。', '！', '？')

        fun endsSentence(separator: String): Boolean = separator.any { it in TERMINATORS }
    }
}
