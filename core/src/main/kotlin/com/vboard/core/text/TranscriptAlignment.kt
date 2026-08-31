package com.vboard.core.text

/**
 * Normalization and alignment of two transcripts of the same speech (W7.1).
 *
 * The product idea behind this is two-model confidence: the streaming model and
 * the final model both hear the same audio, and where they *disagree* is where a
 * word is most likely wrong. That idea is unproven — the plan gates it on
 * measured disagreement precision against a hand-labeled corpus, and that corpus
 * does not exist — so nothing here is wired to the UI. What is here is the
 * foundation the measurement would need: a normalizer that makes two transcripts
 * comparable, and an alignment that says which words correspond.
 *
 * Deliberately independent of any recognizer: it takes two strings.
 */
object TranscriptAlignment {

    /** How one token of the reference relates to the hypothesis. */
    enum class Op { MATCH, SUBSTITUTION, INSERTION, DELETION }

    /**
     * One aligned position.
     *
     * [reference] is null for an insertion (the hypothesis has a word the
     * reference does not) and [hypothesis] is null for a deletion.
     */
    data class Pair(val op: Op, val reference: String?, val hypothesis: String?)

    /**
     * A word plus how much the two transcripts agree about it.
     *
     * [confident] is false exactly where the models disagree — that is the whole
     * signal, and it is deliberately a boolean rather than a score, because a
     * score would imply a calibration nobody has measured.
     */
    data class ScoredWord(val word: String, val confident: Boolean)

    /**
     * Casefolded, punctuation-stripped, whitespace-collapsed form of [text].
     *
     * Comparison-only: never show this to the user, and never commit it. Two
     * recognizers disagreeing about a comma is not disagreement about the words,
     * and treating it as such would make every sentence look uncertain.
     */
    fun normalize(text: String): String {
        val nfc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        val out = StringBuilder(nfc.length)
        var lastWasSpace = true
        var i = 0
        while (i < nfc.length) {
            val cp = nfc.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.isLetterOrDigit(cp) -> {
                    out.appendCodePoint(Character.toLowerCase(cp))
                    lastWasSpace = false
                }
                // An apostrophe is part of the word: "dont" and "don't" are the
                // same disagreement-free word, but "were" and "we're" are not.
                cp == '\''.code || cp == '’'.code -> {
                    out.append('\'')
                    lastWasSpace = false
                }
                else -> if (!lastWasSpace) {
                    out.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return out.toString().trim()
    }

    /** [normalize]d words of [text], in order. */
    fun words(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }

    /**
     * Aligns [reference] against [hypothesis] word by word.
     *
     * Standard Levenshtein backtrace with equal costs, which is what word error
     * rate uses; the point is not the distance but the correspondence it implies.
     */
    fun align(reference: String, hypothesis: String): List<Pair> {
        val ref = words(reference)
        val hyp = words(hypothesis)
        if (ref.isEmpty() && hyp.isEmpty()) return emptyList()

        // costs[i][j] = distance between the first i reference and j hypothesis words.
        val costs = Array(ref.size + 1) { IntArray(hyp.size + 1) }
        for (i in 0..ref.size) costs[i][0] = i
        for (j in 0..hyp.size) costs[0][j] = j
        for (i in 1..ref.size) {
            for (j in 1..hyp.size) {
                val substitution = costs[i - 1][j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1
                val deletion = costs[i - 1][j] + 1
                val insertion = costs[i][j - 1] + 1
                costs[i][j] = minOf(substitution, deletion, insertion)
            }
        }

        val out = ArrayDeque<Pair>()
        var i = ref.size
        var j = hyp.size
        while (i > 0 || j > 0) {
            val here = costs[i][j]
            when {
                i > 0 && j > 0 && here == costs[i - 1][j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1 -> {
                    val op = if (ref[i - 1] == hyp[j - 1]) Op.MATCH else Op.SUBSTITUTION
                    out.addFirst(Pair(op, ref[i - 1], hyp[j - 1]))
                    i--; j--
                }
                i > 0 && here == costs[i - 1][j] + 1 -> {
                    out.addFirst(Pair(Op.DELETION, ref[i - 1], null))
                    i--
                }
                else -> {
                    out.addFirst(Pair(Op.INSERTION, null, hyp[j - 1]))
                    j--
                }
            }
        }
        return out.toList()
    }

    /**
     * The [hypothesis]'s words, each marked confident where the two transcripts
     * agree about it.
     *
     * The hypothesis is the transcript that will be committed — the final pass —
     * so the output is always exactly its words, in its order. Disagreement
     * marks a word; it never changes one.
     */
    fun score(reference: String, hypothesis: String): List<ScoredWord> =
        align(reference, hypothesis).mapNotNull { pair ->
            when (pair.op) {
                Op.MATCH -> ScoredWord(pair.hypothesis!!, confident = true)
                Op.SUBSTITUTION, Op.INSERTION -> ScoredWord(pair.hypothesis!!, confident = false)
                // A word only the reference had is not in the committed text, so
                // there is nothing to mark.
                Op.DELETION -> null
            }
        }

    /** Fraction of committed words the two transcripts disagree about, 0..1. */
    fun disagreementRate(reference: String, hypothesis: String): Double {
        val scored = score(reference, hypothesis)
        if (scored.isEmpty()) return 0.0
        return scored.count { !it.confident }.toDouble() / scored.size
    }
}
