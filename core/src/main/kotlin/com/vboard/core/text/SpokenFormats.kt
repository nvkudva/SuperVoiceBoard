package com.vboard.core.text

/**
 * Turns spoken formats into their written form, before anything else touches
 * the transcript (W7.2).
 *
 * A recognizer hears "five dollars fifty" and writes exactly that. Nobody types
 * it that way, and no later stage can fix it: [ContentGuard] shields written
 * forms, and the tokenizer is prose-only, so by the time either of them runs the
 * words are already the wrong shape. This runs first, on the raw transcript.
 *
 * Every rule here is deliberately conservative, because the failure modes are
 * asymmetric: leaving "five dollars fifty" alone is a small annoyance, and
 * turning "the third of us" into "the 3rd of us" is the keyboard putting words
 * in the user's mouth. So each conversion needs an unambiguous trigger — a
 * currency word, a clock-shaped pair, an explicit "at"/"dot" chain — and
 * anything short of that is left exactly as spoken.
 *
 * The one thing this must never do is lose a word. Every rule either rewrites a
 * span into a strictly equivalent written form or leaves it untouched.
 */
object SpokenFormats {

    /** Rewrites [text] in place of the recognizer's spoken forms. */
    fun apply(text: String): String {
        if (text.isBlank()) return text
        var out = text
        // Order matters: email/URL chains first, so "dot" and "at" inside an
        // address are consumed before the money and time rules can see them.
        out = spokenAddresses(out)
        out = money(out)
        out = clockTimes(out)
        return out
    }

    // ------------------------------------------------------------- addresses

    /**
     * "john dot smith at gmail dot com" -> "john.smith@gmail.com".
     *
     * Requires the full shape: at least one "at" with a "dot" chain after it,
     * because "dot" alone is a word ("connect the dots") and "at" alone is a
     * preposition. Both sides must be single words, which is what an address
     * dictated word-by-word looks like.
     */
    private fun spokenAddresses(text: String): String {
        val pattern = Regex(
            """\b([A-Za-z0-9]+(?:\s+dot\s+[A-Za-z0-9]+)*)\s+at\s+([A-Za-z0-9]+(?:\s+dot\s+[A-Za-z0-9]+)+)\b""",
            RegexOption.IGNORE_CASE,
        )
        return pattern.replace(text) { match ->
            val local = match.groupValues[1].replace(Regex("""\s+dot\s+""", RegexOption.IGNORE_CASE), ".")
            val domain = match.groupValues[2].replace(Regex("""\s+dot\s+""", RegexOption.IGNORE_CASE), ".")
            // A domain has to end in something that could be a TLD; "meet me at
            // building dot two" is not an address.
            if (!domain.substringAfterLast('.').all { it.isLetter() }) match.value
            else "$local@$domain"
        }
    }

    // ----------------------------------------------------------------- money

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19,
    )

    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    private val CURRENCIES = mapOf(
        "dollars" to "$", "dollar" to "$",
        "euros" to "€", "euro" to "€",
        "pounds" to "£", "pound" to "£",
    )

    /**
     * "five dollars fifty" -> "$5.50", "twenty dollars" -> "$20",
     * "five dollars and ninety nine cents" -> "$5.99".
     *
     * The currency word is the trigger, and it must be preceded by a number, so
     * "dollars are down" and "pound of flour" are untouched. Amounts above 999
     * are left alone: "a hundred thousand dollars" is prose as often as it is a
     * figure, and getting it wrong is worse than leaving it.
     */
    private fun money(text: String): String {
        val pattern = Regex(
            """\b($NUMBER)\s+(dollars?|euros?|pounds?)(?:\s+(?:and\s+)?($NUMBER)(?:\s+cents?|\s+pence)?)?\b""",
            RegexOption.IGNORE_CASE,
        )
        return pattern.replace(text) { match ->
            val whole = parseNumber(match.groupValues[1]) ?: return@replace match.value
            val symbol = CURRENCIES[match.groupValues[2].lowercase()] ?: return@replace match.value
            val fractionWords = match.groupValues[3]
            if (fractionWords.isEmpty()) return@replace "$symbol$whole"
            val fraction = parseNumber(fractionWords) ?: return@replace "$symbol$whole"
            if (fraction !in 0..99) return@replace match.value
            "$symbol$whole.${fraction.toString().padStart(2, '0')}"
        }
    }

    /**
     * A number, spoken or written, as a regex fragment.
     *
     * Built from the vocabulary rather than written as `[a-z]+`, because a
     * generic word class made every rule greedy in the wrong direction: "it
     * costs twenty dollars" matched "costs twenty" as the amount, and "five
     * dollars and fifty cents" swallowed "fifty cents" as the fraction.
     */
    private val NUMBER: String = run {
        val words = (TENS.keys.flatMap { tens -> UNITS.keys.filter { UNITS[it] != 0 }.map { "$tens[- ]$it" } } +
            TENS.keys + UNITS.keys).sortedByDescending { it.length }
        "(?:\\d{1,3}|" + words.joinToString("|") + ")"
    }

    /** A spoken or written number 0..999, or null when it is not one. */
    private fun parseNumber(raw: String): Int? {
        val text = raw.trim().lowercase()
        text.toIntOrNull()?.let { return if (it in 0..999) it else null }
        val words = text.split(' ', '-').filter { it.isNotEmpty() }
        return when (words.size) {
            1 -> UNITS[words[0]] ?: TENS[words[0]]
            2 -> {
                val tens = TENS[words[0]] ?: return null
                val unit = UNITS[words[1]] ?: return null
                if (unit == 0) null else tens + unit
            }
            else -> null
        }
    }

    // ----------------------------------------------------------------- times

    /**
     * "three thirty p m" -> "3:30 PM", "seven a m" -> "7 AM".
     *
     * The meridiem is the trigger. Without it, "three thirty" stays as it is:
     * a bare pair of numbers is a time only in context this cannot see.
     */
    private fun clockTimes(text: String): String {
        val minuteWord = """(?:$NUMBER|o'?clock|oh\s+$NUMBER)"""
        val pattern = Regex(
            """\b($NUMBER)(?:\s+($minuteWord))?\s+([ap])[.\s]*m\b\.?""",
            RegexOption.IGNORE_CASE,
        )
        return pattern.replace(text) { match ->
            val hour = parseNumber(match.groupValues[1]) ?: return@replace match.value
            if (hour !in 1..12) return@replace match.value
            val meridiem = match.groupValues[3].uppercase() + "M"
            val minuteRaw = match.groupValues[2]
            if (minuteRaw.isEmpty()) return@replace "$hour $meridiem"
            // "o'clock" and "oh five" are the two spoken zero-minute forms.
            val minute = when (minuteRaw.lowercase().replace("-", " ")) {
                "o'clock", "oclock" -> 0
                else -> parseNumber(minuteRaw.replace(Regex("^oh\\s+", RegexOption.IGNORE_CASE), ""))
            } ?: return@replace match.value
            if (minute !in 0..59) return@replace match.value
            "$hour:${minute.toString().padStart(2, '0')} $meridiem"
        }
    }
}
